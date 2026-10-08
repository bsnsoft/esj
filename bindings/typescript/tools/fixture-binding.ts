/**
 * The binding `conformance/fixtures/run.py` talks to.
 *
 * It reads one JSON request per line from the standard input and answers one JSON object per
 * line: which editions this build carries, the digests and the canonical form of a document,
 * the result of layers L1 to L3, the rule identifiers the pack reports — the pack this build
 * carries, or one of the rule language a request names — and whether a set of registry files
 * is accepted. The manifest is the contract; this file is only the pipe.
 *
 *     python3 conformance/fixtures/run.py --binding node tools/fixture-binding.ts
 *
 * A `validate` request may carry `limits`, an object of bounds of section 12.2 under the names
 * `Limits` gives them, which replace the defaults for that one request. Its answer carries the
 * status, every layer not evaluated with its reason, and every finding with its path, code,
 * subject and severity:
 *
 *     {"op": "validate", "file": "...", "limits": {"maxStringBytes": 16}}
 *     -> {"status": "INDETERMINATE",
 *         "notEvaluated": [{"layer": "L2", "reason": "LIMIT"}, {"layer": "L3", "reason": "LIMIT"}],
 *         "findings": [{"path": "", "code": "ESJ-L1-LIMIT", "subject": "format",
 *                       "severity": "error"}]}
 *
 * It may also carry `registries`, registry files relative to the repository root: the first is
 * read as the core and every further one combined with it as an extension, and the request is
 * validated with those instead of the registries this build carries:
 *
 *     {"op": "validate", "file": "...", "registries": ["conformance/fixtures/registries/x.json"]}
 *
 * A `registry` request names registry files relative to the repository root. Each is read and
 * checked as section 10 checks a registry when it is read, and every file after the first is
 * then combined with the first as an extension of it:
 *
 *     {"op": "registry", "files": ["model/en16931/2017.json", "model/b2c/0.1.json"]}
 *     -> {"accepted": true}   or   {"accepted": false, "error": "..."}
 */

import { createInterface } from 'node:readline';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { canonicalize } from '../src/canonical.ts';
import { documentDigest, semanticDigest } from '../src/digest.ts';
import { readDocumentOrThrow } from '../src/reader.ts';
import { validate } from '../src/validate.ts';
import { Structure } from '../src/structure.ts';
import type { Limits } from '../src/limits.ts';
import type { Registry, RegistryFile } from '../src/registry.ts';
import { RegistryError, registryOf } from '../src/registry.ts';
import { compile, type RuleEngine } from '../src/rules/engine.ts';
import type { RulePackFile } from '../src/rules/pack.ts';
import { registries, ruleEngine } from '../src/node/data.ts';

const ROOT = path.resolve(fileURLToPath(new URL('../../..', import.meta.url)));
const REGISTRIES: Registry[] = registries();
const ENGINES = new Map<string, RuleEngine>();

function bytesOf(file: string): Uint8Array {
  return readFileSync(path.join(ROOT, file));
}

function documentOf(request: Record<string, unknown>): ReturnType<typeof readDocumentOrThrow> {
  if (typeof request.file === 'string') {
    return readDocumentOrThrow(bytesOf(request.file));
  }
  return readDocumentOrThrow(JSON.stringify(request.document));
}

function structureFor(semanticModel: string): Structure | undefined {
  const core = REGISTRIES.find(
    (registry) => !registry.isExtension() && registry.semanticModel === semanticModel);
  if (core === undefined) {
    return undefined;
  }
  const extensions = REGISTRIES.filter((registry) => registry.isExtension()
    && registry.imports.some((imported) => imported.edition === core.edition));
  return new Structure(core, extensions);
}

/**
 * Returns the engine a rules request is answered with: the pack this build carries, or the
 * pack of the rule language the request names by its path in the repository, compiled against
 * the edition of the document.
 */
function engineFor(semanticModel: string, pack: unknown): RuleEngine | undefined {
  const key = (typeof pack === 'string' ? pack : '') + '|' + semanticModel;
  const known = ENGINES.get(key);
  if (known !== undefined) {
    return known;
  }
  const structure = structureFor(semanticModel);
  if (structure === undefined) {
    return undefined;
  }
  const engine = typeof pack === 'string'
    ? compile(JSON.parse(readFileSync(path.join(ROOT, pack), 'utf8')) as RulePackFile, structure)
    : ruleEngine(structure);
  ENGINES.set(key, engine);
  return engine;
}

/** Reads registry files of the repository, as a registry request or a validate request names them. */
function registriesOf(files: unknown): Registry[] {
  if (!Array.isArray(files) || files.length === 0
    || files.some((file) => typeof file !== 'string')) {
    throw new Error('a request names registries as one or more files');
  }
  return (files as string[]).map((file) => registryOf(
    JSON.parse(readFileSync(path.join(ROOT, file), 'utf8')) as RegistryFile));
}

/**
 * Reads registry files and combines every one after the first with the first, the way a
 * validator is given an edition and the extensions it carries.
 */
function registry(files: unknown): unknown {
  if (!Array.isArray(files) || files.length === 0
    || files.some((file) => typeof file !== 'string')) {
    return { error: 'a registry request names one or more files' };
  }
  try {
    const read = (files as string[]).map((file) => registryOf(
      JSON.parse(readFileSync(path.join(ROOT, file), 'utf8')) as RegistryFile));
    new Structure(read[0], read.slice(1));
    return { accepted: true };
  } catch (failure) {
    if (failure instanceof RegistryError) {
      return { accepted: false, error: failure.message };
    }
    throw failure;
  }
}

async function answer(request: Record<string, unknown>): Promise<unknown> {
  switch (request.op) {
    case 'editions':
      return {
        semanticModels: REGISTRIES
          .filter((registry) => !registry.isExtension())
          .map((registry) => registry.semanticModel),
      };
    case 'digest': {
      const document = documentOf(request);
      return {
        semanticDigest: await semanticDigest(document),
        documentDigest: await documentDigest(document),
        canonicalBytes: new TextEncoder().encode(canonicalize(document)).length,
        values: document.values.size,
      };
    }
    case 'canonicalize':
      return { canonical: canonicalize(documentOf(request)) };
    case 'validate': {
      const input = typeof request.file === 'string'
        ? bytesOf(request.file)
        : JSON.stringify(request.document);
      const result = validate(input, {
        registries: request.registries === undefined ? REGISTRIES : registriesOf(request.registries),
        ...(request.limits === undefined ? {} : { limits: request.limits as Partial<Limits> }),
      });
      return {
        status: result.status,
        notEvaluated: result.notEvaluated.map((entry) => ({
          layer: entry.layer, reason: entry.reason,
        })),
        findings: result.findings.map((entry) => ({
          path: entry.path, code: entry.code, subject: entry.subject, severity: entry.severity,
        })),
      };
    }
    case 'registry':
      return registry(request.files);
    case 'rules': {
      const document = documentOf(request);
      const engine = engineFor(document.semanticModel, request.pack);
      if (engine === undefined) {
        return {
          error: 'no rule pack of this build states rules about ' + document.semanticModel,
        };
      }
      const findings = engine.evaluate(document);
      const rules = new Set<string>();
      const warnings = new Set<string>();
      for (const entry of findings) {
        rules.add(entry.rule);
        if (entry.severity === 'warning') {
          warnings.add(entry.rule);
        }
      }
      return { rules: [...rules].sort(), warnings: [...warnings].sort() };
    }
    default:
      return { error: 'unknown request ' + JSON.stringify(request.op) };
  }
}

const lines = createInterface({ input: process.stdin });
for await (const line of lines) {
  if (line.trim() === '') {
    continue;
  }
  let reply: unknown;
  try {
    reply = await answer(JSON.parse(line) as Record<string, unknown>);
  } catch (failure) {
    reply = { error: (failure as Error).message };
  }
  process.stdout.write(JSON.stringify(reply) + '\n');
}
