import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import type { Registry, RegistryFile } from '../registry.ts';
import { registryOf } from '../registry.ts';
import type { RulePackFile, RuleFile, CodeListFile } from '../rules/pack.ts';
import { compile, type RuleEngine } from '../rules/engine.ts';
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
 * report the terms it describes as not checked rather than measuring them.
 */
export function registries(): Registry[] {
  const files: RegistryFile[] = [
    read<RegistryFile>('model', 'en16931', '2017.json'),
    read<RegistryFile>('model', 'en16931', '2026.json'),
    read<RegistryFile>('model', 'xrechnung', '3.0.2.json'),
    read<RegistryFile>('model', 'b2c', '0.1.json'),
  ];
  return files.map(registryOf);
}

/** The identifier of the rule pack this build carries. */
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
 * Reads the rule pack of `rules/en16931/1.3.16`: its manifest, the rule files it names and
 * the code list snapshots it decides membership against.
 */
export function rulePack(): RulePackFile {
  const directory = ['rules', PACK_ID, PACK_VERSION];
  const manifest = read<RulePackFile>(...directory, 'pack.json');
  const rules = [...manifest.rules ?? []];
  for (const file of manifest.files ?? []) {
    rules.push(...read<RuleFile>(...directory, ...file.split('/')));
  }
  const codeLists: Record<string, CodeListFile> = {};
  for (const [listId, day] of Object.entries(manifest.codeLists ?? {})) {
    codeLists[listId] = read<CodeListFile>(...directory, 'codelists', listId, day + '.json');
  }
  return { ...manifest, rules, codeLists: manifest.codeLists, lists: codeLists };
}

/**
 * Compiles the rule pack of this build against a structure.
 *
 * @param structure the terms of the edition the documents name
 * @return the engine, ready to run over a document
 * @throws Error where the structure is not the edition the pack states rules about
 */
export function ruleEngine(structure: Structure): RuleEngine {
  if (structure.core.semanticModel !== PACK_SEMANTIC_MODEL) {
    throw new Error('the pack ' + PACK_ID + '/' + PACK_VERSION + ' states rules about '
      + PACK_SEMANTIC_MODEL + ' and the structure is ' + structure.core.semanticModel);
  }
  return compile(rulePack(), structure, en16931NativeRules());
}

/** The code list snapshots the pack names, for a caller that wants to inspect them. */
export function codeListDays(): Record<string, string> {
  const manifest = read<RulePackFile>('rules', PACK_ID, PACK_VERSION, 'pack.json');
  return { ...manifest.codeLists };
}

/** The days a code list has a snapshot for in this build, newest last. */
export function snapshotsOf(listId: string): string[] {
  const directory = path.join(DATA, 'rules', PACK_ID, PACK_VERSION, 'codelists', listId);
  return readdirSync(directory)
    .filter((name) => name.endsWith('.json'))
    .map((name) => name.slice(0, name.length - '.json'.length))
    .sort();
}
