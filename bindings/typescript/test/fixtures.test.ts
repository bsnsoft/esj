import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { canonicalize } from '../src/canonical.ts';
import { documentDigest, semanticDigest } from '../src/digest.ts';
import { readDocumentOrThrow } from '../src/reader.ts';
import { validate } from '../src/validate.ts';
import { Structure } from '../src/structure.ts';
import type { SemanticDocument } from '../src/document.ts';
import type { Limits } from '../src/limits.ts';
import type { Registry, RegistryFile } from '../src/registry.ts';
import { RegistryError, registryOf } from '../src/registry.ts';
import { compile, type RuleEngine } from '../src/rules/engine.ts';
import type { RulePackFile } from '../src/rules/pack.ts';
import { registries, ruleEngine } from '../src/node/data.ts';

/**
 * The fixture manifest of `conformance/fixtures/`, run against this implementation.
 *
 * The manifest is written in no programming language: documents with the digests they have to
 * produce and the whole answer a validator gives about them, documents that have to be rejected
 * and that answer, documents read under bounds other than the defaults, registries a loader has
 * to take or refuse, the value grammars and the grammars of the envelope as accept and reject
 * tables, every mutation of the conformance corpus as a base document and the changes that
 * break it, and a pack of the rule language that pins its division. It is generated from the reference implementation
 * and compared with what is checked in on every build of it, so it cannot record an
 * expectation that implementation does not meet — which is what makes it a contract rather
 * than a second opinion.
 */

const ROOT = path.resolve(fileURLToPath(new URL('../../..', import.meta.url)));
const FIXTURES = path.join(ROOT, 'conformance', 'fixtures');

function read<T>(file: string): T {
  return JSON.parse(readFileSync(file, 'utf8')) as T;
}

/** A finding as the manifest records it, SPEC.md section 9.5. */
interface Finding {
  path: string;
  code: string;
  subject: string;
  severity: string;
}

/** The whole answer of a validation, as the manifest records it. */
interface Outcome {
  status: string;
  notEvaluated: Array<{ layer: string; reason: string }>;
  findings: Finding[];
}

interface Digests {
  values: number;
  canonicalBytes: number;
  semanticDigest: string;
  documentDigest: string;
}

interface Manifest {
  part: string;
  parts?: string[];
  semanticModel?: string;
  registries?: Array<{ file: string; semanticModel: string; terms: number }>;
  documents?: Array<Digests & {
    file: string;
    canonical?: string;
    semanticModel: string;
    registries: string[];
    outcome: Outcome;
  }>;
  invalid?: Array<Partial<Digests> & {
    file: string; layer: string; registries?: string[]; outcome: Outcome;
  }>;
  bounds?: Array<{ file: string; limits: Partial<Limits>; outcome: Outcome }>;
  registryChecks?: Array<{ files: string[]; accepted: boolean }>;
  canonicalOrder?: Array<{
    scrambled: string; canonical: string; values: number; documentDigest: string;
  }>;
  grammars?: Array<{
    datatype?: string; grammar?: string; code: string; base: string; path?: string;
    member?: string; accept: unknown[]; reject: unknown[];
  }>;
  rules?: { pack: string; version: string; casesFile: string; cases: number };
  arithmetic?: { pack: string; base: string; expect: { rules: string[]; warnings: string[] } };
}

interface Case {
  id: string;
  rule: string;
  base?: string;
  baseDocument?: string;
  changes: Array<{ path: string; value?: string; remove?: boolean }>;
  expect: { rules: string[]; warnings: string[] };
}

const MANIFESTS: Manifest[] = (() => {
  const root = read<Manifest>(path.join(FIXTURES, 'manifest.json'));
  const all = [root];
  for (const name of root.parts ?? []) {
    const part = path.join(FIXTURES, name);
    if (existsSync(part)) {
      all.push(read<Manifest>(part));
    }
  }
  return all;
})();

const REGISTRIES: Registry[] = registries();
const CARRIED = new Set(REGISTRIES
  .filter((registry) => !registry.isExtension())
  .map((registry) => registry.semanticModel));
const ENGINES = new Map<string, RuleEngine>();

function structureFor(semanticModel: string): Structure | undefined {
  const core = REGISTRIES.find(
    (registry) => !registry.isExtension() && registry.semanticModel === semanticModel);
  if (core === undefined) {
    return undefined;
  }
  return new Structure(core, REGISTRIES.filter((registry) => registry.isExtension()
    && registry.imports.some((imported) => imported.edition === core.edition)));
}

function engineFor(semanticModel: string): RuleEngine {
  const known = ENGINES.get(semanticModel);
  if (known !== undefined) {
    return known;
  }
  const engine = ruleEngine(structureFor(semanticModel)!);
  ENGINES.set(semanticModel, engine);
  return engine;
}

function documentOf(file: string): SemanticDocument {
  return readDocumentOrThrow(readFileSync(path.join(ROOT, file)));
}

/** Reads registry files of the repository, the first as the core and the others as its extensions. */
function registriesFrom(files: readonly string[]): Registry[] {
  return files.map((file) => registryOf(read<RegistryFile>(path.join(ROOT, file))));
}

/** Validates bytes or a text as a validate request of the runner asks for it. */
function outcomeOf(input: Uint8Array | string,
  options: { limits?: Partial<Limits>; registries?: readonly string[] } = {}): Outcome {
  const result = validate(input, {
    registries: options.registries === undefined ? REGISTRIES : registriesFrom(options.registries),
    ...(options.limits === undefined ? {} : { limits: options.limits }),
  });
  return {
    status: result.status,
    notEvaluated: result.notEvaluated.map((entry) => ({ layer: entry.layer, reason: entry.reason })),
    findings: result.findings.map((entry) => ({
      path: entry.path, code: entry.code, subject: entry.subject, severity: entry.severity,
    })),
  };
}

type Row = [string, string, string, string];

function compareRows(left: readonly string[], right: readonly string[]): number {
  for (let at = 0; at < Math.min(left.length, right.length); at++) {
    if (left[at] !== right[at]) {
      return left[at] < right[at] ? -1 : 1;
    }
  }
  return left.length - right.length;
}

/**
 * An outcome in the form the runner compares two outcomes in: the findings of the reader as
 * a list (SPEC.md section 9.6 fixes how far a reader reads and in which order), the findings
 * of one path at layer L2 as a list (section 9.2 fixes the order of the checks of a path), and
 * everything else as a set.
 */
function normalized(outcome: Outcome): unknown {
  const reader: Row[] = [];
  const model = new Map<string, Row[]>();
  const rest: Row[] = [];
  for (const finding of outcome.findings) {
    const row: Row = [finding.path, finding.code, finding.subject, finding.severity];
    if (finding.code.startsWith('ESJ-L1-')) {
      reader.push(row);
    } else if (finding.code.startsWith('ESJ-L2-')) {
      const rows = model.get(finding.path) ?? [];
      rows.push(row);
      model.set(finding.path, rows);
    } else {
      rest.push(row);
    }
  }
  return {
    status: outcome.status,
    notEvaluated: outcome.notEvaluated.map((entry) => entry.layer + ':' + entry.reason).sort(),
    reader,
    model: [...model.entries()].sort(([left], [right]) => compareRows([left], [right])),
    others: rest.sort(compareRows),
  };
}

/** The codes of the errors a validation reports about one path. */
function errorsAt(outcome: Outcome, at: string): string[] {
  return outcome.findings
    .filter((entry) => entry.severity === 'error' && entry.path === at)
    .map((entry) => entry.code);
}

/** The codes of the errors of layer L1 a validation reports. */
function layerOneErrors(outcome: Outcome): string[] {
  return outcome.findings
    .filter((entry) => entry.severity === 'error' && entry.code.startsWith('ESJ-L1-'))
    .map((entry) => entry.code);
}

/**
 * Whether the cases of a manifest file are validated here: a part carries one edition, and
 * its outcomes were recorded with that edition's registry.
 */
function evaluated(manifest: Manifest): boolean {
  return manifest.semanticModel === undefined || CARRIED.has(manifest.semanticModel);
}

async function digestsOf(file: string): Promise<Digests> {
  const document = documentOf(file);
  return {
    values: document.values.size,
    canonicalBytes: new TextEncoder().encode(canonicalize(document)).length,
    semanticDigest: await semanticDigest(document),
    documentDigest: await documentDigest(document),
  };
}

test('every file the manifest names is in the repository', () => {
  for (const manifest of MANIFESTS) {
    for (const registry of manifest.registries ?? []) {
      assert.ok(existsSync(path.join(ROOT, registry.file)), registry.file);
    }
    for (const document of manifest.documents ?? []) {
      assert.ok(existsSync(path.join(ROOT, document.file)), document.file);
      assert.ok(document.registries.length > 0, document.file + ' names its registries');
      for (const registry of document.registries) {
        assert.ok(existsSync(path.join(ROOT, registry)), registry);
      }
    }
  }
});

/** The registry files the manifests name, and the edition each of them describes. */
const REGISTRY_FILES = new Map<string, string>(MANIFESTS
  .flatMap((manifest) => manifest.registries ?? [])
  .map((entry) => [entry.file, entry.semanticModel]));

test('this build carries every registry a document was measured with', () => {
  const carried = new Set(REGISTRIES.map((registry) => registry.semanticModel));
  for (const manifest of MANIFESTS) {
    for (const document of manifest.documents ?? []) {
      for (const file of document.registries) {
        const semanticModel = REGISTRY_FILES.get(file);
        assert.ok(semanticModel !== undefined, file + ' is named by no registries section');
        assert.ok(carried.has(semanticModel),
          document.file + ' was measured with ' + file + ', which this build does not carry');
      }
    }
  }
});

test('the registries carry the terms the manifest counts', () => {
  for (const manifest of MANIFESTS) {
    for (const entry of manifest.registries ?? []) {
      const registry = REGISTRIES.find(
        (candidate) => candidate.semanticModel === entry.semanticModel);
      assert.ok(registry !== undefined, entry.semanticModel);
      assert.equal(registry.terms().length, entry.terms, entry.file);
    }
  }
});

test('a conformant document has the digests and the canonical form the manifest pins',
  async () => {
    for (const manifest of MANIFESTS) {
      for (const entry of manifest.documents ?? []) {
        const document = documentOf(entry.file);
        const canonical = canonicalize(document);
        assert.deepEqual({
          semanticDigest: await semanticDigest(document),
          documentDigest: await documentDigest(document),
          canonicalBytes: new TextEncoder().encode(canonical).length,
          values: document.values.size,
        }, {
          semanticDigest: entry.semanticDigest,
          documentDigest: entry.documentDigest,
          canonicalBytes: entry.canonicalBytes,
          values: entry.values,
        }, entry.file);
        if (entry.canonical !== undefined) {
          assert.equal(canonical,
            readFileSync(path.join(ROOT, entry.canonical), 'utf8'), entry.canonical);
        }
      }
    }
  });

/**
 * Compares every case of a loop before it fails, so that a run names each case this
 * implementation answers differently and not only the first.
 */
function mismatches(): { check(name: string, actual: unknown, expected: unknown): void;
  assertNone(): void; } {
  const found: string[] = [];
  return {
    check(name, actual, expected) {
      try {
        assert.deepEqual(actual, expected);
      } catch {
        found.push(name + ': expected ' + JSON.stringify(expected) + ', answered '
          + JSON.stringify(actual));
      }
    },
    assertNone() {
      assert.deepEqual(found, []);
    },
  };
}

test('a document draws exactly the outcome the manifest records', () => {
  const report = mismatches();
  for (const manifest of MANIFESTS) {
    if (!evaluated(manifest)) {
      continue;
    }
    for (const entry of manifest.documents ?? []) {
      report.check(entry.file,
        normalized(outcomeOf(readFileSync(path.join(ROOT, entry.file)))),
        normalized(entry.outcome));
    }
  }
  report.assertNone();
});

test('a document the manifest calls invalid draws the whole outcome it records', async () => {
  const report = mismatches();
  for (const manifest of MANIFESTS) {
    for (const entry of manifest.invalid ?? []) {
      if (entry.values !== undefined) {
        // Content a model layer refuses passes the reader and the canonicalizer unchanged.
        report.check(entry.file + ' digests', await digestsOf(entry.file), {
          values: entry.values, canonicalBytes: entry.canonicalBytes,
          semanticDigest: entry.semanticDigest, documentDigest: entry.documentDigest,
        });
      }
      if (!evaluated(manifest)) {
        continue;
      }
      report.check(entry.file,
        normalized(outcomeOf(readFileSync(path.join(ROOT, entry.file)),
          entry.registries === undefined ? {} : { registries: entry.registries })),
        normalized(entry.outcome));
    }
  }
  report.assertNone();
});

test('a document read under the bounds of a case draws the outcome the manifest records', () => {
  const report = mismatches();
  let run = 0;
  for (const manifest of MANIFESTS) {
    for (const entry of manifest.bounds ?? []) {
      report.check(entry.file + ' under ' + JSON.stringify(entry.limits),
        normalized(outcomeOf(readFileSync(path.join(ROOT, entry.file)), { limits: entry.limits })),
        normalized(entry.outcome));
      run++;
    }
  }
  report.assertNone();
  assert.ok(run > 0, 'the bounds were run');
});

test('a set of registries is taken or refused as the manifest records', () => {
  const report = mismatches();
  for (const manifest of MANIFESTS) {
    for (const entry of manifest.registryChecks ?? []) {
      let accepted: boolean;
      try {
        const read = registriesFrom(entry.files);
        new Structure(read[0], read.slice(1));
        accepted = true;
      } catch (failure) {
        if (!(failure instanceof RegistryError)) {
          throw failure;
        }
        accepted = false;
      }
      report.check(entry.files.join(' + '), accepted, entry.accepted);
    }
  }
  report.assertNone();
});

test('a document not in canonical form canonicalizes to the pinned bytes', async () => {
  for (const manifest of MANIFESTS) {
    for (const entry of manifest.canonicalOrder ?? []) {
      const document = documentOf(entry.scrambled);
      assert.equal(canonicalize(document),
        readFileSync(path.join(ROOT, entry.canonical), 'utf8'), entry.scrambled);
      assert.deepEqual({ values: document.values.size, documentDigest: await documentDigest(document) },
        { values: entry.values, documentDigest: entry.documentDigest }, entry.scrambled);
    }
  }
});

/** Builds the document of a case: the base document with the changes the manifest gives. */
function applied(
  base: Record<string, unknown>, changes: ReadonlyArray<{ path: string; value?: unknown; remove?: boolean }>,
): string {
  const document = JSON.parse(JSON.stringify(base)) as { values: Record<string, unknown> };
  for (const change of changes) {
    if (change.remove === true) {
      delete document.values[change.path];
    } else {
      document.values[change.path] = change.value;
    }
  }
  return JSON.stringify(document);
}

/** The base document with a candidate written into one member of the envelope. */
function envelopeDocument(base: Record<string, unknown>, member: string, candidate: string): string {
  const document = JSON.parse(JSON.stringify(base)) as Record<string, unknown>;
  if (member === 'semanticModel') {
    document.semanticModel = candidate;
  } else {
    document.extensions = { [candidate]: 'x' };
  }
  return JSON.stringify(document);
}

test('a value is accepted or rejected as the grammar table says', () => {
  for (const manifest of MANIFESTS) {
    for (const grammar of manifest.grammars ?? []) {
      const base = read<Record<string, unknown>>(path.join(ROOT, grammar.base));
      if (!CARRIED.has(base.semanticModel as string)) {
        continue;
      }
      if (grammar.member !== undefined) {
        for (const candidate of grammar.accept as string[]) {
          assert.deepEqual(layerOneErrors(outcomeOf(envelopeDocument(base, grammar.member, candidate))),
            [], grammar.grammar + ' accepts ' + JSON.stringify(candidate));
        }
        for (const candidate of grammar.reject as string[]) {
          assert.deepEqual(layerOneErrors(outcomeOf(envelopeDocument(base, grammar.member, candidate))),
            [grammar.code], grammar.grammar + ' rejects ' + JSON.stringify(candidate));
        }
        continue;
      }
      const at = grammar.path as string;
      for (const candidate of grammar.accept) {
        assert.deepEqual(errorsAt(outcomeOf(applied(base, [{ path: at, value: candidate }])), at),
          [], grammar.datatype + ' accepts ' + JSON.stringify(candidate));
      }
      for (const candidate of grammar.reject) {
        assert.deepEqual(errorsAt(outcomeOf(applied(base, [{ path: at, value: candidate }])), at),
          [grammar.code], grammar.datatype + ' rejects ' + JSON.stringify(candidate));
      }
    }
  }
});

test('the rule pack reports on every mutation what the manifest expects', () => {
  const bases = new Map<string, Record<string, unknown>>();
  let run = 0;
  for (const manifest of MANIFESTS) {
    if (manifest.rules === undefined) {
      continue;
    }
    const file = read<{ cases: Case[]; bases?: Record<string, Record<string, unknown>> }>(
      path.join(FIXTURES, manifest.rules.casesFile));
    const cases = file.cases;
    assert.equal(cases.length, manifest.rules.cases);
    for (const entry of cases) {
      let base = entry.baseDocument !== undefined
        ? file.bases![entry.baseDocument]
        : bases.get(entry.base!);
      if (base === undefined) {
        base = read<Record<string, unknown>>(path.join(ROOT, entry.base!));
        bases.set(entry.base!, base);
      }
      if (!CARRIED.has(base.semanticModel as string)) {
        continue;
      }
      const document = readDocumentOrThrow(applied(base, entry.changes));
      const findings = engineFor(document.semanticModel).evaluate(document);
      const rules = new Set<string>();
      const warnings = new Set<string>();
      for (const finding of findings) {
        rules.add(finding.rule);
        if (finding.severity === 'warning') {
          warnings.add(finding.rule);
        }
      }
      assert.deepEqual({ rules: [...rules].sort(), warnings: [...warnings].sort() },
        entry.expect, entry.id);
      run++;
    }
  }
  assert.ok(run > 400, 'the mutations of the corpus were run: ' + run);
  if (MANIFESTS.some((manifest) => manifest.part !== 'core' && manifest.rules !== undefined
    && CARRIED.has('EN16931-1:2026'))) {
    assert.ok(run > 448, 'the cases of the pack of the later edition were run: ' + run);
  }
});

/**
 * The arithmetic of the rule language: a pack whose rules pin division as `rules/README.md`
 * states it — exact where the quotient terminates, 34 fraction digits half up where it does
 * not, absent where the divisor is zero. Each of its rules is a case of its own here, so that a
 * failure names the quotient that is wrong, and the note of that rule in the pack says what it
 * pins.
 */
test('the arithmetic pack reports what the manifest expects, rule by rule', () => {
  let run = 0;
  for (const manifest of MANIFESTS) {
    const section = manifest.arithmetic;
    if (section === undefined) {
      continue;
    }
    const pack = read<RulePackFile>(path.join(ROOT, section.pack));
    const document = documentOf(section.base);
    const findings = compile(pack, structureFor(document.semanticModel)!).evaluate(document);
    const reported = new Set(findings.map((finding) => finding.rule));
    for (const rule of pack.rules ?? []) {
      assert.equal(reported.has(rule.id), section.expect.rules.includes(rule.id),
        rule.id + ': ' + (rule.note ?? rule.message));
      run++;
    }
    assert.deepEqual(findings.filter((finding) => finding.severity === 'warning')
      .map((finding) => finding.rule), section.expect.warnings);
  }
  assert.ok(run >= 10, 'the rules of the arithmetic pack were run: ' + run);
});
