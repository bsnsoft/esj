import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { canonicalize, pretty } from '../src/canonical.ts';
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

/** A document whose `extensions` carry one owner with the given JSON text as its value. */
function withExtension(json: string): string {
  return '{"format":"EN16931-Semantic-JSON","version":"0.1",'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020","values":{"/BT-1":"X"},'
    + '"extensions":{"de.example.vendor":' + json + '}}';
}

/** A document whose `values` carry `/BT-1` written as the given JSON text. */
function withValue(json: string): string {
  return '{"format":"EN16931-Semantic-JSON","version":"0.1",'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020","values":{"/BT-1":' + json + '}}';
}

function pairsOf(input: Uint8Array | string, limits?: Parameters<typeof readDocument>[1]): string[] {
  return readDocument(input, limits).findings.map((entry) => entry.path + ' ' + entry.code);
}

test('a number whose canonical form is too long is refused before any of it is written out',
  { timeout: 20_000 }, () => {
    const spellings = [
      '1e500000000', '1e-500000000', '1e999999999', '-1e-999999999',
      '1e99999999999999999999', '1e-99999999999999999999', '1e' + '9'.repeat(400),
      '0.' + '0'.repeat(1_000_000) + '1',
    ];
    for (const spelling of spellings) {
      const started = performance.now();
      assert.deepEqual(pairsOf(withExtension('[' + spelling + ']')), [' ESJ-L1-EXT-NUMBER'],
        spelling.slice(0, 40));
      assert.ok(performance.now() - started < 2_000, spelling.slice(0, 40) + ' took too long');
    }
  });

test('a long spelling whose canonical form is short is a number like any other', () => {
  const read = readDocumentOrThrow(withExtension('[0e999999999, 0.' + '0'.repeat(1000)
    + '1e1001, 1' + '0'.repeat(1000) + 'e-1000, -0.0e-5]'));
  assert.ok(read.extensions !== undefined);
});

test('a number token past the string bound is a limit wherever it stands', () => {
  const token = '1'.repeat(1024 * 1024 + 1);
  assert.deepEqual(pairsOf(withValue(token)), ['/BT-1 ESJ-L1-LIMIT']);
  assert.deepEqual(pairsOf(withValue('1'.repeat(1024 * 1024))), ['/BT-1 ESJ-L1-JSON-TYPE']);
});

test('what the reader refuses inside values it walks past, bounded, without building it', () => {
  const nested = (levels: number): string => '['.repeat(levels) + '0' + ']'.repeat(levels);
  assert.deepEqual(pairsOf(withValue(nested(32))), ['/BT-1 ESJ-L1-JSON-TYPE']);
  assert.deepEqual(pairsOf(withValue(nested(33))), ['/BT-1 ESJ-L1-LIMIT']);
  assert.deepEqual(pairsOf(withValue('{"value":' + nested(31) + ',"scheme":"x"}')),
    ['/BT-1 ESJ-L1-JSON-TYPE']);
  assert.deepEqual(pairsOf(withValue('{"value":' + nested(32) + ',"scheme":"x"}')),
    ['/BT-1 ESJ-L1-LIMIT']);
});

test('a member whose name is no path has its value judged all the same', () => {
  const text = '{"format":"EN16931-Semantic-JSON","version":"0.1",'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020","values":{"BT 1":[1,[2]],"/BT-2":"X"}}';
  assert.deepEqual(pairsOf(text), [' ESJ-L1-PATH-SYNTAX', ' ESJ-L1-JSON-TYPE']);
});

test('the reader reports in the order the text is written and stops where it must', () => {
  const head = '{"format":"EN16931-Semantic-JSON","version":"0.1",'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020",';
  assert.deepEqual(pairsOf(head + '"profile":1,"values":{,,}'), [' ESJ-L1-ENVELOPE-MEMBER'],
    'an undefined envelope member ends the read before the broken text after it');
  assert.deepEqual(pairsOf(head + '"values":{"BT 1":"x","/BT-2" "x"}}'),
    [' ESJ-L1-PATH-SYNTAX', ' ESJ-L1-JSON'],
    'a finding confined to one member stands before the defect that ends the read');
  assert.deepEqual(pairsOf(head + '"values":' + '['.repeat(40) + ']'.repeat(40) + '}'),
    [' ESJ-L1-ENVELOPE-VALUE'], 'an envelope member of the wrong type is refused at its first token');
});

test('a wide array where a string belongs costs the walk and no tree', () => {
  // The process gets 128 MiB of heap: building the 32 million elements of this 64 MiB
  // document into a tree took about 2.6 GiB, walking past them takes the text.
  const reader = new URL('../src/reader.ts', import.meta.url).href;
  const script = `
    const { readDocument } = await import(${JSON.stringify(reader)});
    const text = '{"format":"EN16931-Semantic-JSON","version":"0.1",'
      + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020","values":{"/BT-1":['
      + '0,'.repeat(32 * 1024 * 1024 - 100) + '0]}}';
    const result = readDocument(text);
    process.stdout.write(JSON.stringify(result.findings.map((f) => f.path + ' ' + f.code)));`;
  const run = spawnSync(process.execPath, ['--max-old-space-size=128', '--input-type=module',
    '-e', script], { encoding: 'utf8', timeout: 120_000 });
  assert.equal(run.status, 0, run.stderr);
  assert.deepEqual(JSON.parse(run.stdout), ['/BT-1 ESJ-L1-JSON-TYPE']);
});

test('a nesting bound a caller raised is answered with a document, never a stack overflow',
  () => {
    const levels = 60_000;
    const nested = '['.repeat(levels) + '0' + ']'.repeat(levels);
    const limits = { limits: { maxExtensionDepth: levels + 10 } };
    const document = readDocumentOrThrow(withExtension(nested), limits);
    const canonical = canonicalize(document);
    assert.ok(canonical.includes('['.repeat(levels) + '0' + ']'.repeat(levels)));
    // The pretty form indents every level, so its length grows with the square of the depth;
    // a few thousand levels are what a string of the language holds.
    const shallower = readDocumentOrThrow(withExtension(nested.slice(levels - 4000,
      levels + 4001)), limits);
    assert.ok(pretty(shallower).includes('[\n'));
    assert.deepEqual(pairsOf(withValue(nested), limits), ['/BT-1 ESJ-L1-JSON-TYPE']);
    assert.deepEqual(pairsOf(withExtension(nested)), [' ESJ-L1-LIMIT'],
      'the default bound answers the same document with the limit');
  });
