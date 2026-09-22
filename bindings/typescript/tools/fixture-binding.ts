/**
 * The binding `conformance/fixtures/run.py` talks to.
 *
 * It reads one JSON request per line from the standard input and answers one JSON object per
 * line: which editions this build carries, the digests and the canonical form of a document,
 * the findings of layers L1 to L3, and the rule identifiers the pack reports. The manifest is
 * the contract; this file is only the pipe.
 *
 *     python3 conformance/fixtures/run.py --binding node tools/fixture-binding.ts
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
import type { Registry } from '../src/registry.ts';
import type { RuleEngine } from '../src/rules/engine.ts';
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

function engineFor(semanticModel: string): RuleEngine | undefined {
  const known = ENGINES.get(semanticModel);
  if (known !== undefined) {
    return known;
  }
  const core = REGISTRIES.find(
    (registry) => !registry.isExtension() && registry.semanticModel === semanticModel);
  if (core === undefined) {
    return undefined;
  }
  const extensions = REGISTRIES.filter((registry) => registry.isExtension()
    && registry.imports.some((imported) => imported.edition === core.edition));
  const engine = ruleEngine(new Structure(core, extensions));
  ENGINES.set(semanticModel, engine);
  return engine;
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
      const result = validate(input, { registries: REGISTRIES });
      return {
        findings: result.findings.map((entry) => ({ path: entry.path, code: entry.code })),
        status: result.status,
      };
    }
    case 'rules': {
      const document = documentOf(request);
      const engine = engineFor(document.semanticModel);
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
