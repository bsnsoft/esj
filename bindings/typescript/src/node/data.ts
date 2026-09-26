import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import type { Registry, RegistryFile } from '../registry.ts';
import { registryOf } from '../registry.ts';
import type { CodeListFile, RuleDefinition, RuleFile, RulePackFile } from '../rules/pack.ts';
import { RulePackError } from '../rules/pack.ts';
import { compile, type NativeRule, type RuleEngine } from '../rules/engine.ts';
import { en16931NativeRules } from '../rules/native.ts';
import type { Structure } from '../structure.ts';

/**
 * The registries and the rule pack this package carries, read from the files beside it.
 *
 * Everything above this module is free of any environment: a document, a registry and a rule
 * pack are values a caller passes in, so the library runs wherever JavaScript does. This
 * module is the one that reads files, and it is the one a browser bundle leaves out.
 */

const DATA = fileURLToPath(new URL('../../data/', import.meta.url));

function read<T>(...parts: string[]): T {
  return JSON.parse(readFileSync(path.join(DATA, ...parts), 'utf8')) as T;
}

/**
 * The registries of `model/`: the editions this build carries, and every extension registry
 * of the repository. An extension is combined with the core registry it imports where a
 * document of that edition is validated (section 5.6), so a build that left one out would
 * report the terms it describes as not checked rather than measuring them. The registry of
 * an edition a distribution does not ship is not there, and neither is its edition here.
 */
export function registries(): Registry[] {
  const files: string[][] = [
    ['model', 'en16931', '2017.json'],
    ['model', 'en16931', '2026.json'],
    ['model', 'xrechnung', '3.0.2.json'],
    ['model', 'b2c', '0.1.json'],
  ];
  return files
    .filter((parts) => parts[2] !== '2026.json' || existsSync(path.join(DATA, ...parts)))
    .map((parts) => registryOf(read<RegistryFile>(...parts)));
}

/** The identifier of the rule pack of the default edition. */
export const PACK_ID = 'en16931';

/** The version of that pack, which is the release of the artefacts it was verified against. */
export const PACK_VERSION = '1.3.16';

/**
 * The edition that pack states rules about, as a document spells it in `semanticModel`.
 *
 * A rule addresses business terms by semantic path, and a path is an address relative to an
 * edition (section 10), so a pack belongs to the edition it was written over. Compiling it
 * against another one would silently give rules that address terms of a different table.
 */
export const PACK_SEMANTIC_MODEL = 'EN16931-1:2017+A1:2019/AC:2020';

/**
 * The rules a pack of this build declares the rule language cannot express, by pack. The
 * pack of an edition that is not shipped leaves its module out, and with it the rules; its
 * data is left out beside it, so a build without the edition finds one pack fewer.
 */
const NATIVES = new Map<string, () => NativeRule[]>([[PACK_ID, en16931NativeRules]]);

/** The module of the rules of the pack of the later edition, where this build carries it. */
const LATER = new URL('../rules/native-2026' + path.extname(fileURLToPath(import.meta.url)),
  import.meta.url);
if (existsSync(fileURLToPath(LATER))) {
  const module = await import(LATER.href) as {
    PACK_ID: string; nativeRules: () => NativeRule[];
  };
  NATIVES.set(module.PACK_ID, module.nativeRules);
}

/** One pack of this build: where it lies and the edition it is written for. */
interface PackLocation {
  readonly id: string;
  readonly version: string;
  readonly edition: string;
}

/**
 * The rule packs this build carries, one per edition, read from the directories of `rules/`
 * that hold a manifest.
 */
function packs(): PackLocation[] {
  const found: PackLocation[] = [];
  const root = path.join(DATA, 'rules');
  for (const id of readdirSync(root).sort()) {
    const directory = path.join(root, id);
    if (!statSync(directory).isDirectory()) {
      continue;
    }
    for (const version of readdirSync(directory).sort()) {
      if (!existsSync(path.join(directory, version, 'pack.json'))) {
        continue;
      }
      const manifest = read<RulePackFile>('rules', id, version, 'pack.json');
      found.push({ id, version, edition: manifest.edition });
    }
  }
  return found;
}

/**
 * Reads a rule pack of `rules/`: its manifest, the rule files it names, the rules it takes
 * over from another pack by identifier, and the code list snapshots it decides membership
 * against, its own and those it reads from another pack.
 *
 * A rule taken over carries the oracle of the share rather than the one it carries in its own
 * pack: the statement is the same and the evidence for it is not.
 *
 * @param id the identifier of the pack
 * @param version its version
 */
export function rulePackAt(id: string, version: string): RulePackFile {
  const directory = ['rules', id, version];
  const manifest = read<RulePackFile>(...directory, 'pack.json');
  const rules: RuleDefinition[] = [...manifest.rules ?? []];
  for (const share of manifest.shares ?? []) {
    const taken = new Map(read<RuleFile>('rules', share.pack, share.version,
      ...share.file.split('/')).map((rule) => [rule.id, rule]));
    for (const ruleId of share.rules) {
      const rule = taken.get(ruleId);
      if (rule === undefined) {
        throw new RulePackError('the pack ' + id + '/' + version + ' takes over ' + ruleId
          + ' from ' + share.file + ' of ' + share.pack + '/' + share.version
          + ', which carries no such rule');
      }
      rules.push({ ...rule, oracle: share.oracle });
    }
  }
  for (const file of manifest.files ?? []) {
    rules.push(...read<RuleFile>(...directory, ...file.split('/')));
  }
  const codeLists: Record<string, CodeListFile> = {};
  for (const [listId, snapshot] of Object.entries(manifest.codeLists ?? {})) {
    const [from, day] = typeof snapshot === 'string'
      ? [id + '/' + version, snapshot]
      : [snapshot.from, snapshot.day];
    codeLists[listId] = read<CodeListFile>('rules', ...from.split('/'), 'codelists', listId,
      day + '.json');
  }
  return { ...manifest, rules, lists: codeLists };
}

/**
 * Reads the rule pack of `rules/en16931/1.3.16`, the pack of the default edition.
 */
export function rulePack(): RulePackFile {
  return rulePackAt(PACK_ID, PACK_VERSION);
}

/**
 * Compiles the rule pack of this build that is written for the edition of a structure.
 *
 * @param structure the terms of the edition the documents name
 * @return the engine, ready to run over a document
 * @throws Error where this build carries no pack for that edition
 */
export function ruleEngine(structure: Structure): RuleEngine {
  const location = packs().find((pack) => pack.edition === structure.core.edition);
  if (location === undefined) {
    throw new Error('no rule pack of this build states rules about '
      + structure.core.semanticModel);
  }
  const natives = NATIVES.get(location.id);
  return compile(rulePackAt(location.id, location.version), structure,
    natives === undefined ? [] : natives());
}

/**
 * The editions this build carries a rule pack for, in the spelling of the registry that
 * describes each.
 */
export function packEditions(): string[] {
  return packs().map((pack) => pack.edition);
}

/** The code list snapshots the pack of the default edition names, for a caller that wants to inspect them. */
export function codeListDays(): Record<string, string> {
  const manifest = read<RulePackFile>('rules', PACK_ID, PACK_VERSION, 'pack.json');
  const days: Record<string, string> = {};
  for (const [listId, snapshot] of Object.entries(manifest.codeLists ?? {})) {
    days[listId] = typeof snapshot === 'string' ? snapshot : snapshot.day;
  }
  return days;
}

/** The days a code list has a snapshot for in the pack of the default edition, newest last. */
export function snapshotsOf(listId: string): string[] {
  const directory = path.join(DATA, 'rules', PACK_ID, PACK_VERSION, 'codelists', listId);
  return readdirSync(directory)
    .filter((name) => name.endsWith('.json'))
    .map((name) => name.slice(0, name.length - '.json'.length))
    .sort();
}
