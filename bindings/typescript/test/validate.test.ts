import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FindingCode } from '../src/codes.ts';
import { validate, validateSemanticDocument } from '../src/validate.ts';
import { readDocumentOrThrow } from '../src/reader.ts';
import { registries } from '../src/node/data.ts';

/**
 * Layer L2 path by path (specification, section 9.2): every check of a path whose terms are
 * known runs on its own, a term the registries do not know ends the checks below it, and a
 * finding about one segment or one component names it in its subject (section 9.5).
 */

const REGISTRIES = registries();

function document(values: Record<string, unknown>): string {
  return JSON.stringify({
    format: 'EN16931-Semantic-JSON',
    version: '0.1',
    semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
    values,
  });
}

type Place = [string, string, string];

/** The findings of layer L2 over one path, as path, code and subject. */
function l2(path: string, value: unknown): { status: string; findings: Place[] } {
  const result = validate(document({ [path]: value }), {
    registries: REGISTRIES, layers: ['L1', 'L2'],
  });
  return {
    status: result.status,
    findings: result.findings.map((entry): Place => [entry.path, entry.code, entry.subject]),
  };
}

test('every check of a path whose terms are known runs on its own', () => {
  assert.deepEqual(l2('/BG-25/0/BT-2', '2026-02-30').findings, [
    ['/BG-25/0/BT-2', FindingCode.L2_PARENT_CHAIN, 'BT-2'],
    ['/BG-25/0/BT-2', FindingCode.L2_DATE, ''],
  ]);
  assert.deepEqual(l2('/BT-2/0', '2026-02-30').findings, [
    ['/BT-2/0', FindingCode.L2_INDEX_FORBIDDEN, 'BT-2'],
    ['/BT-2/0', FindingCode.L2_DATE, ''],
  ]);
  assert.deepEqual(l2('/BG-25/BT-146', '1').findings, [
    ['/BG-25/BT-146', FindingCode.L2_INDEX_REQUIRED, 'BG-25'],
    ['/BG-25/BT-146', FindingCode.L2_PARENT_CHAIN, 'BT-146'],
  ]);
  assert.deepEqual(l2('/BG-25/BG-29/BT-146', '1.0').findings, [
    ['/BG-25/BG-29/BT-146', FindingCode.L2_INDEX_REQUIRED, 'BG-25'],
    ['/BG-25/BG-29/BT-146', FindingCode.L2_DECIMAL, ''],
  ]);
});

test('a core segment the registry does not carry is an unknown term, one per segment', () => {
  const twice = l2('/BG-998/BT-999', 'x');
  assert.equal(twice.status, 'INVALID');
  assert.deepEqual(twice.findings, [
    ['/BG-998/BT-999', FindingCode.L2_UNKNOWN_TERM, 'BG-998'],
    ['/BG-998/BT-999', FindingCode.L2_UNKNOWN_TERM, 'BT-999'],
  ]);
  // An extension never supplies a core identifier, so an extension segment whose registry is
  // not loaded does not make an unknown core segment unchecked.
  const beside = l2('/BG-999/BT-ZZZ-1', 'x');
  assert.equal(beside.status, 'INVALID');
  assert.deepEqual(beside.findings, [
    ['/BG-999/BT-ZZZ-1', FindingCode.L2_UNKNOWN_TERM, 'BG-999'],
    ['/BG-999/BT-ZZZ-1', FindingCode.L2_NOT_CHECKED, ''],
  ]);
  const below = l2('/BG-ZZZ-1/BT-999', 'x');
  assert.equal(below.status, 'INVALID');
  assert.deepEqual(below.findings, [
    ['/BG-ZZZ-1/BT-999', FindingCode.L2_NOT_CHECKED, ''],
    ['/BG-ZZZ-1/BT-999', FindingCode.L2_UNKNOWN_TERM, 'BT-999'],
  ]);
});

test('an unknown term ends the checks below it and none above', () => {
  const result = l2('/BG-25/BG-ZZZ-1/BT-1', 'x');
  assert.equal(result.status, 'INVALID');
  assert.deepEqual(result.findings, [
    ['/BG-25/BG-ZZZ-1/BT-1', FindingCode.L2_INDEX_REQUIRED, 'BG-25'],
    ['/BG-25/BG-ZZZ-1/BT-1', FindingCode.L2_NOT_CHECKED, ''],
  ]);
  const unchecked = l2('/BG-25/0/BG-ZZZ-1/BT-1', 'x');
  assert.equal(unchecked.status, 'INDETERMINATE');
  assert.deepEqual(unchecked.findings,
    [['/BG-25/0/BG-ZZZ-1/BT-1', FindingCode.L2_NOT_CHECKED, '']]);
});

test('an identifier a loaded namespace does not carry is not checked, since a namespace grows',
  () => {
    const result = l2('/BG-DEX-09/0/BT-DEX-999', 'x');
    assert.equal(result.status, 'INDETERMINATE');
    assert.deepEqual(result.findings,
      [['/BG-DEX-09/0/BT-DEX-999', FindingCode.L2_NOT_CHECKED, '']]);
  });

test('a finding about a component names the component', () => {
  assert.deepEqual(l2('/BG-24/0/BT-125', 'QUJD').findings, [
    ['/BG-24/0/BT-125', FindingCode.L2_COMPONENT_MISSING, 'mimeCode'],
    ['/BG-24/0/BT-125', FindingCode.L2_COMPONENT_MISSING, 'filename'],
  ]);
  assert.deepEqual(l2('/BG-4/BT-27', { value: 'X', scheme: '0088' }).findings,
    [['/BG-4/BT-27', FindingCode.L2_COMPONENT_NOT_ALLOWED, 'scheme']]);
});

test('a document is measured as a document, whether read or handed over', () => {
  const read = readDocumentOrThrow(document({ '/BT-2/0': '2026-02-30' }));
  const handed = validateSemanticDocument(read, { registries: REGISTRIES, layers: ['L2'] });
  assert.deepEqual(handed.findings.map((entry) => [entry.path, entry.code, entry.subject]), [
    ['/BT-2/0', FindingCode.L2_INDEX_FORBIDDEN, 'BT-2'],
    ['/BT-2/0', FindingCode.L2_DATE, ''],
  ]);
});

test('where the reader refused a member, the model layers asked for are not evaluated', () => {
  const refused = validate(document({ '/BT-1x': 'x', '/BT-2': '2026-02-30' }),
    { registries: REGISTRIES, layers: ['L2', 'L3'] });
  assert.equal(refused.status, 'INDETERMINATE');
  assert.deepEqual(refused.findings, []);
  assert.deepEqual(refused.notEvaluated, [
    { layer: 'L1', reason: 'NOT-REQUESTED' },
    { layer: 'L2', reason: 'PRECEDING-LAYER-FAILED' },
    { layer: 'L3', reason: 'PRECEDING-LAYER-FAILED' },
  ]);
  const onlyL3 = validate(document({ '/BT-1x': 'x' }), { registries: REGISTRIES, layers: ['L3'] });
  assert.deepEqual(onlyL3.notEvaluated, [
    { layer: 'L1', reason: 'NOT-REQUESTED' },
    { layer: 'L2', reason: 'NOT-REQUESTED' },
    { layer: 'L3', reason: 'PRECEDING-LAYER-FAILED' },
  ]);
  const stopped = validate(document({ '/BT-1': 'a', '/BT-2': 'b', '/BT-3': 'c' }),
    { registries: REGISTRIES, layers: ['L2', 'L3'], limits: { maxValues: 2 } });
  assert.deepEqual(stopped.notEvaluated, [
    { layer: 'L1', reason: 'NOT-REQUESTED' },
    { layer: 'L2', reason: 'LIMIT' },
    { layer: 'L3', reason: 'LIMIT' },
  ]);
  const sound = validate(document({ '/BT-2': '2026-02-30' }),
    { registries: REGISTRIES, layers: ['L2'] });
  assert.equal(sound.status, 'INVALID');
  assert.deepEqual(sound.findings.map((entry) => entry.code), [FindingCode.L2_DATE]);
});
