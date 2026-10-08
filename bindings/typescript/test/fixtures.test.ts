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
import type { Registry } from '../src/registry.ts';
import { compile, type RuleEngine } from '../src/rules/engine.ts';
import type { RulePackFile } from '../src/rules/pack.ts';
import { registries, ruleEngine } from '../src/node/data.ts';

/**
 * The fixture manifest of `conformance/fixtures/`, run against this implementation.
 *
 * The manifest is written in no programming language: documents with the digests they have to
 * produce, documents that have to be rejected and the finding code for each, the value
 * grammars as accept and reject tables, every mutation of the conformance corpus as a base
 * document and the changes that break it, and a pack of the rule language that pins its
 * division. It is generated from the reference implementation
 * and compared with what is checked in on every build of it, so it cannot record an
 * expectation that implementation does not meet — which is what makes it a contract rather
 * than a second opinion.
 */

const ROOT = path.resolve(fileURLToPath(new URL('../../..', import.meta.url)));
const FIXTURES = path.join(ROOT, 'conformance', 'fixtures');

function read<T>(file: string): T {
  return JSON.parse(readFileSync(file, 'utf8')) as T;
}

interface Manifest {
  part: string;
  parts?: string[];
  registries?: Array<{ file: string; semanticModel: string; terms: number }>;
  documents?: Array<{
    file: string;
    canonical?: string;
    semanticModel: string;
    registries: string[];
    values: number;
    canonicalBytes: number;
    semanticDigest: string;
    documentDigest: string;
    findings?: Array<{ path: string; code: string; subject?: string }>;
  }>;
  invalid?: Array<Row>;
  canonicalOrder?: Array<{ scrambled: string; canonical: string }>;
  grammars?: Array<{
    datatype: string; code: string; base: string; path: string;
    accept: string[]; reject: string[];
  }>;
  rules?: { pack: string; version: string; casesFile: string; cases: number };
  arithmetic?: { pack: string; base: string; expect: { rules: string[]; warnings: string[] } };
}

/** A row of the manifest that names a finding: its file and layer where it is a negative one. */
interface Row {
  file?: string;
  layer?: string;
  code?: string;
  path?: string;
  subject?: string;
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

type Triple = [string, string, string];

/** The error findings of a validation, as the runner reads them: path, code and subject. */
function errorsOf(input: Uint8Array | string): Triple[] {
  const result = validate(input, { registries: REGISTRIES });
  return result.findings
    .filter((entry) => entry.code.startsWith('ESJ-L')
      && !entry.code.endsWith('NOT-CHECKED') && !entry.code.endsWith('EDITION-UNKNOWN'))
    .map((entry): Triple => [entry.path, entry.code, entry.subject])
    .sort(compareTriples);
}

function compareTriples(left: Triple, right: Triple): number {
  for (let at = 0; at < 3; at++) {
    if (left[at] !== right[at]) {
      return left[at] < right[at] ? -1 : 1;
    }
  }
  return 0;
}

/**
 * The places whose subject the manifest records, as path and code. The manifest records one
 * where SPEC.md section 9.5 requires it, and the runner compares it there and nowhere else.
 */
function pinned(rows: readonly Row[]): Set<string> {
  return new Set(rows.filter((row) => row.subject !== undefined)
    .map((row) => (row.path ?? '') + '\u0000' + row.code));
}

/** Findings as path, code and subject, the subject kept only at the places pinned. */
function compared(findings: readonly Triple[], pins: Set<string>): Triple[] {
  return findings
    .map(([where, code, subject]): Triple =>
      [where, code, pins.has(where + '\u0000' + code) ? subject : ''])
    .sort(compareTriples);
}

/** The rows of the manifest in the form compared() gives an answer. */
function recorded(rows: readonly Row[]): Triple[] {
  return compared(rows.map((row): Triple =>
    [row.path ?? '', row.code as string, row.subject ?? '']), pinned(rows));
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

test('a conformant document draws exactly the findings the manifest records', () => {
  const report = mismatches();
  for (const manifest of MANIFESTS) {
    for (const entry of manifest.documents ?? []) {
      if (!CARRIED.has(entry.semanticModel)) {
        continue;
      }
      const rows = entry.findings ?? [];
      report.check(entry.file,
        compared(errorsOf(readFileSync(path.join(ROOT, entry.file))), pinned(rows)),
        recorded(rows));
    }
  }
  report.assertNone();
});

/**
 * The rows of one manifest, gathered per file: a document may be wrong in two ways at layer
 * L1 and draw a row for each code.
 */
function invalidByFile(manifest: Manifest): Map<string, Row[]> {
  const byFile = new Map<string, Row[]>();
  for (const entry of manifest.invalid ?? []) {
    const rows = byFile.get(entry.file as string);
    if (rows === undefined) {
      byFile.set(entry.file as string, [entry]);
    } else {
      rows.push(entry);
    }
  }
  return byFile;
}

test('a document the manifest calls invalid is rejected at the place it names', () => {
  const report = mismatches();
  for (const manifest of MANIFESTS) {
    for (const [file, rows] of invalidByFile(manifest)) {
      const found = errorsOf(readFileSync(path.join(ROOT, file)));
      const codes = found.map(([, code]) => code);
      if (rows[0].layer === 'business-rule') {
        report.check(file + ' is structurally sound', codes, []);
        continue;
      }
      if (rows[0].layer === 'L1' || rows[0].layer === 'limit') {
        // SPEC.md section 9.6 fixes how far a reader reads, so the rows of a document layer
        // L1 refused are the whole answer and not a sample of it.
        report.check(file, compared(found, pinned(rows)), recorded(rows));
        continue;
      }
      const [first] = recorded(rows.slice(0, 1));
      report.check(file + ' draws ' + rows[0].code + ' at ' + JSON.stringify(rows[0].path ?? ''),
        compared(found, pinned(rows.slice(0, 1))).some((entry) =>
          compareTriples(entry, first) === 0) ? 'drawn' : found, 'drawn');
    }
  }
  report.assertNone();
});

test('a document whose members are in the wrong order canonicalizes to the pinned bytes', () => {
  for (const manifest of MANIFESTS) {
    for (const entry of manifest.canonicalOrder ?? []) {
      assert.equal(canonicalize(documentOf(entry.scrambled)),
        readFileSync(path.join(ROOT, entry.canonical), 'utf8'), entry.scrambled);
    }
  }
});

/** Builds the document of a case: the base document with the changes the manifest gives. */
function applied(
  base: Record<string, unknown>, changes: ReadonlyArray<{ path: string; value?: string; remove?: boolean }>,
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

test('a value is accepted or rejected as the grammar table says', () => {
  for (const manifest of MANIFESTS) {
    for (const grammar of manifest.grammars ?? []) {
      const base = read<Record<string, unknown>>(path.join(ROOT, grammar.base));
      if (!CARRIED.has(base.semanticModel as string)) {
        continue;
      }
      for (const candidate of grammar.accept) {
        const codes = errorsOf(applied(base, [{ path: grammar.path, value: candidate }]))
          .filter(([at]) => at === grammar.path).map(([, code]) => code);
        assert.deepEqual(codes, [], grammar.datatype + ' accepts ' + JSON.stringify(candidate));
      }
      for (const candidate of grammar.reject) {
        const codes = errorsOf(applied(base, [{ path: grammar.path, value: candidate }]))
          .filter(([at]) => at === grammar.path).map(([, code]) => code);
        assert.deepEqual(codes, [grammar.code],
          grammar.datatype + ' rejects ' + JSON.stringify(candidate));
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
