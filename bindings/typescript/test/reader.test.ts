import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FindingCode } from '../src/codes.ts';
import { EsjError } from '../src/errors.ts';
import { readDocument, readDocumentOrThrow } from '../src/reader.ts';
import { validate } from '../src/validate.ts';
import { registries } from '../src/node/data.ts';

/**
 * Layer L1, which is the layer decided by the bytes alone, and the three states a result has.
 */

const REGISTRIES = registries();

function envelope(values: Record<string, unknown>, rest: Record<string, unknown> = {}): string {
  return JSON.stringify({
    format: 'EN16931-Semantic-JSON',
    version: '0.1',
    semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
    values,
    ...rest,
  });
}

function codesOf(input: Uint8Array | string): string[] {
  return readDocument(input).findings.map((finding) => finding.code);
}

test('a byte order mark is not whitespace and is not part of a JSON text', () => {
  const bytes = new Uint8Array([0xef, 0xbb, 0xbf, ...new TextEncoder().encode(envelope({}))]);
  assert.deepEqual(codesOf(bytes), [FindingCode.L1_ENCODING]);
});

test('a byte sequence that is not UTF-8 is refused', () => {
  assert.deepEqual(codesOf(new Uint8Array([0x7b, 0xff, 0x7d])), [FindingCode.L1_ENCODING]);
});

test('a member name that occurs twice is kept and reported, never collapsed', () => {
  const text = '{"format":"EN16931-Semantic-JSON","version":"0.1",'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020",'
    + '"values":{"/BT-1":"a","/BT-1":"b"}}';
  assert.ok(codesOf(text).includes(FindingCode.L1_DUPLICATE_MEMBER));
});

test('the five checks of one value object answer the object and not its spelling', () => {
  const cases: Array<[Record<string, unknown>, string]> = [
    [{ value: 'RE-2026-0001' }, FindingCode.L1_VALUE_SHAPE],
    [{ value: { x: 'y' }, scheme: '0088' }, FindingCode.L1_VALUE_SHAPE],
    [{ foo: 'a', scheme: null, value: 'X' }, FindingCode.L1_JSON_TYPE],
    [{ value: ['a'], scheme: '0088' }, FindingCode.L1_JSON_TYPE],
    [{ scheme: '' }, FindingCode.L1_VALUE_MEMBER],
    [{ value: 'X', scheme: '0088', foo: '' }, FindingCode.L1_VALUE_MEMBER],
    [{ value: 'X', schemeVersion: '1' }, FindingCode.L1_VALUE_MEMBER],
    [{ value: '0088123456785', scheme: '' }, FindingCode.L1_EMPTY_STRING],
  ];
  for (const [value, code] of cases) {
    assert.deepEqual(codesOf(envelope({ '/BG-4/BT-29/0': value })), [code],
      JSON.stringify(value));
  }
});

test('the order the members of a value object are written in changes nothing', () => {
  const written = envelope({
    '/BG-4/BT-29/0': { scheme: '0088', value: 'X', foo: 'a' },
  });
  const other = envelope({
    '/BG-4/BT-29/0': { foo: 'a', value: 'X', scheme: '0088' },
  });
  assert.deepEqual(codesOf(written), codesOf(other));
});

test('a lone surrogate stands beside the code the member set draws', () => {
  const text = '{"format":"EN16931-Semantic-JSON","version":"0.1",'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020",'
    + '"values":{"/BG-4/BT-29/0":{"value":"X","scheme":"0088","foo":"\\uD800"}}}';
  const codes = codesOf(text);
  assert.ok(codes.includes(FindingCode.L1_SURROGATE));
  assert.ok(codes.includes(FindingCode.L1_VALUE_MEMBER));
});

test('a limit outranks the code the member it stopped at would have drawn', () => {
  const deep = { a: {} as Record<string, unknown> };
  let leaf = deep.a;
  for (let i = 0; i < 40; i++) {
    const next: Record<string, unknown> = {};
    leaf.a = next;
    leaf = next;
  }
  const text = envelope({ '/BT-1': 'X' }, { extensions: { 'de.example.vendor': deep } });
  assert.deepEqual(codesOf(text), [FindingCode.L1_LIMIT]);
});

test('a bound a caller could never enforce is refused when it is given', () => {
  assert.throws(() => readDocument(envelope({}), { limits: { maxValues: 0 } }), RangeError);
});

test('a reader may end with an exception, and it carries the same code', () => {
  try {
    readDocumentOrThrow(envelope({ '/BT-1x': 'X' }));
    assert.fail('the path is not a path of section 5.1');
  } catch (failure) {
    assert.ok(failure instanceof EsjError);
    assert.equal(failure.code, FindingCode.L1_PATH_SYNTAX);
  }
});

test('an empty finding list is not conformance: the status says what was evaluated', () => {
  const unknown = JSON.stringify({
    format: 'EN16931-Semantic-JSON',
    version: '0.1',
    semanticModel: 'EN16931-1:2099',
    values: { '/BT-1': 'RE-2026-0001' },
  });
  const result = validate(unknown, { registries: REGISTRIES });
  assert.equal(result.status, 'INDETERMINATE');
  assert.deepEqual(result.notEvaluated,
    [{ layer: 'L2', reason: 'EDITION-UNKNOWN' }, { layer: 'L3', reason: 'EDITION-UNKNOWN' }]);
  assert.deepEqual(result.findings.map((finding) => finding.code),
    [FindingCode.L2_EDITION_UNKNOWN]);
});

test('an edition with no registry is read, canonicalized and digested all the same', () => {
  const document = readDocumentOrThrow(JSON.stringify({
    format: 'EN16931-Semantic-JSON',
    version: '0.1',
    semanticModel: 'EN16931-1:2099',
    values: { '/BT-1': 'RE-2026-0001' },
  }));
  assert.equal(document.values.size, 1);
});

test('where L1 failed the model layers are named as not evaluated', () => {
  const result = validate(envelope({ '/BT-1x': 'X' }), { registries: REGISTRIES });
  assert.equal(result.status, 'INVALID');
  assert.deepEqual(result.notEvaluated, [
    { layer: 'L2', reason: 'PRECEDING-LAYER-FAILED' },
    { layer: 'L3', reason: 'PRECEDING-LAYER-FAILED' },
  ]);
});

test('a limit leaves the result indeterminate and never invalid', () => {
  const values: Record<string, unknown> = {};
  for (let i = 1; i <= 5; i++) {
    values['/BT-' + i] = 'X';
  }
  const result = validate(envelope(values), {
    registries: REGISTRIES, limits: { maxValues: 2 },
  });
  assert.equal(result.status, 'INDETERMINATE');
  assert.deepEqual(result.notEvaluated,
    [{ layer: 'L2', reason: 'LIMIT' }, { layer: 'L3', reason: 'LIMIT' }]);
});
