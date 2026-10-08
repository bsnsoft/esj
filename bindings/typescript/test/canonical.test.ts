import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
  canonicalNumber, canonicalize, compareCodePoints, pretty,
} from '../src/canonical.ts';
import { FindingCode } from '../src/codes.ts';
import { semanticDigest } from '../src/digest.ts';
import { documentOf } from '../src/document.ts';
import { EsjError } from '../src/errors.ts';
import type { JsonObject } from '../src/json/tree.ts';
import { comparePathText } from '../src/paths.ts';
import { readDocumentOrThrow } from '../src/reader.ts';

/** The canonical form of section 7, where it differs from what a JCS library would write. */

const ROOT = path.resolve(fileURLToPath(new URL('../../..', import.meta.url)));

function example(name: string): string {
  return readFileSync(path.join(ROOT, 'examples', name), 'utf8');
}

test('a number inside extensions is canonicalized from its spelling, never through a double',
  () => {
    assert.equal(canonicalNumber('1e21'), '1000000000000000000000');
    assert.equal(canonicalNumber('1e-6'), '0.000001');
    assert.equal(canonicalNumber('1e-7'), '0.0000001');
    assert.equal(canonicalNumber('-0'), '0');
    assert.equal(canonicalNumber('-0.0'), '0');
    assert.equal(canonicalNumber('12345678901234567890'), '12345678901234567890');
    assert.equal(canonicalNumber('1.0000000000000001'), '1.0000000000000001');
    assert.equal(canonicalNumber('100.00'), '100');
  });

test('member names sort by Unicode code point and not by UTF-16 code unit', () => {
  assert.ok(compareCodePoints('', '\u{10000}') < 0);
  assert.ok('' > '\u{10000}'.charAt(0), 'the language compares code units');
});

test('the canonical path order is numeric and puts a term before a group', () => {
  assert.ok(comparePathText('/BG-4/BT-27', '/BG-25/0/BT-129') < 0);
  assert.ok(comparePathText('/BT-1', '/BG-4/BT-27') < 0);
  assert.ok(comparePathText('/BG-4/BT-27', '/BG-4/BG-5/BT-40') < 0);
  assert.ok(comparePathText('/BG-25/1/BT-129', '/BG-25/10/BT-129') < 0);
});

test('a string is escaped as section 7.5 requires', () => {
  const document = readDocumentOrThrow(JSON.stringify({
    format: 'EN16931-Semantic-JSON',
    version: '0.1',
    semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
    values: { '/BG-4/BT-27': 'a"b\\c\nd\tef€' },
  }));
  assert.ok(canonicalize(document).includes('"a\\"b\\\\c\\nd\\te\\u0001f€"'));
});

test('canonicalizing a pretty document and a scrambled one gives the same bytes', () => {
  const canonical = example('extended.canonical.esj.json');
  assert.equal(canonicalize(readDocumentOrThrow(example('extended.esj.json'))), canonical);
  const scrambled = readFileSync(
    path.join(ROOT, 'conformance/fixtures/canonical-order/scrambled.esj.json'), 'utf8');
  assert.equal(canonicalize(readDocumentOrThrow(scrambled)), canonical);
});

test('the pretty form is the layout the examples of the repository are stored in', () => {
  const stored = readdirSync(path.join(ROOT, 'examples')).filter((name) =>
    name.endsWith('.esj.json') && !name.endsWith('.canonical.esj.json')
    && name !== 'extended.esj.json');
  assert.ok(stored.length >= 10, stored.join(', '));
  for (const name of stored) {
    const text = example(name);
    assert.equal(pretty(readDocumentOrThrow(text)), text, name);
    assert.ok(text.endsWith('}\n'), name + ' ends with one line feed');
  }
});

test('the pretty form writes a number of extensions in its canonical form and ends with LF', () => {
  // extended.esj.json keeps the spellings it was written with, so that reading it exercises
  // the canonical form of a number; the pretty form is written from the content alone.
  const written = pretty(readDocumentOrThrow(example('extended.esj.json')));
  assert.ok(written.includes('"exponentNegativeSeven": 0.0000001'), written);
  assert.ok(!written.includes('1e-7'));
  assert.ok(written.endsWith('}\n') && !written.endsWith('\n\n'));
  const again = readDocumentOrThrow(written);
  assert.equal(pretty(again), written, 'the pretty form is a fixed point');
  assert.equal(canonicalize(again), example('extended.canonical.esj.json'));
});

test('a line ending is normalized when the document is read, and once', () => {
  const document = readDocumentOrThrow(JSON.stringify({
    format: 'EN16931-Semantic-JSON',
    version: '0.1',
    semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
    values: { '/BT-1': 'a\r\nb\rc' },
  }));
  assert.equal(document.values.get('/BT-1')!.value, 'a\nb\nc');
  assert.ok(canonicalize(document).includes('"a\\nb\\nc"'));
});

test('the canonical form of a number is sized before it is written', { timeout: 20_000 }, () => {
  assert.equal(canonicalNumber('0e999999999'), '0');
  assert.equal(canonicalNumber('-0.0e-5'), '0');
  assert.equal(canonicalNumber('0.001e3'), '1');
  assert.equal(canonicalNumber('1' + '0'.repeat(1000) + 'e-1000'), '1');
  assert.equal(canonicalNumber('0.' + '0'.repeat(1000) + '1e1001'), '1');
  assert.equal(canonicalNumber('1' + '0'.repeat(63)), '1' + '0'.repeat(63));
  assert.equal(canonicalNumber('0.' + '0'.repeat(61) + '1'), '0.' + '0'.repeat(61) + '1');
  for (const spelling of ['1' + '0'.repeat(64), '-1' + '0'.repeat(63), '0.' + '0'.repeat(62) + '1',
    '1e500000000', '1e-500000000', '1e999999999', '1e99999999999999999999',
    '1e-99999999999999999999', '1e' + '9'.repeat(400), '0.' + '0'.repeat(1_000_000) + '1']) {
    assert.throws(() => canonicalNumber(spelling),
      (failure: unknown) => failure instanceof EsjError
        && failure.code === FindingCode.L1_EXT_NUMBER, spelling.slice(0, 40));
  }
  assert.throws(() => canonicalNumber('1e'), /not the spelling of a JSON number/);
});

test('a lone surrogate has no canonical form and is refused, never replaced', async () => {
  const lone = (value: string, extensions?: JsonObject) => documentOf({
    semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
    values: [['/BT-1', { value }]],
    ...(extensions === undefined ? {} : { extensions }),
  });
  const refused = (failure: unknown) => failure instanceof EsjError
    && failure.code === FindingCode.L1_SURROGATE;
  for (const value of ['X\uD800', 'X\uDBFF', '\uDC00X', 'X\uDC00\uD800']) {
    assert.throws(() => canonicalize(lone(value)), refused, JSON.stringify(value));
    assert.throws(() => pretty(lone(value)), refused, JSON.stringify(value));
    await assert.rejects(semanticDigest(lone(value)), refused, JSON.stringify(value));
  }
  assert.throws(() => canonicalize(lone('X', {
    t: 'object', members: [{ name: 'de.example', value: { t: 'string', value: 'a\uD800' } }],
  })), refused);
  assert.throws(() => canonicalize(lone('X', {
    t: 'object', members: [{ name: 'de.example', value: { t: 'object',
      members: [{ name: '\uDFFF', value: { t: 'null' } }] } }],
  })), refused);
  assert.equal(canonicalize(lone('X\u{1F4B6}')).includes('X\u{1F4B6}'), true,
    'a surrogate pair is one code point and is written as it stands');
});

test('a value built through the API has its line endings normalized as a read one', async () => {
  const built = documentOf({
    semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
    values: [
      ['/BG-1/0/BT-22', { value: 'a\r\nb\rc\r\r\nd' }],
      ['/BG-4/BT-29/0', { value: 'X\r\n1', scheme: '00\r88' }],
    ],
    source: { syntax: 'U\r\nBL' },
    extensions: {
      t: 'object', members: [{ name: 'de.example', value: { t: 'string', value: 'x\r\ny' } }],
    },
  });
  assert.equal(built.values.get('/BG-1/0/BT-22')!.value, 'a\nb\nc\n\nd');
  assert.deepEqual(built.values.get('/BG-4/BT-29/0'), { value: 'X\n1', scheme: '00\n88' });
  const read = readDocumentOrThrow(JSON.stringify({
    format: 'EN16931-Semantic-JSON',
    version: '0.1',
    semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
    values: {
      '/BG-1/0/BT-22': 'a\r\nb\rc\r\r\nd',
      '/BG-4/BT-29/0': { value: 'X\r\n1', scheme: '00\r88' },
    },
    extensions: { 'de.example': 'x\r\ny' },
    source: { syntax: 'U\r\nBL' },
  }));
  assert.equal(canonicalize(built), canonicalize(read));
  assert.equal(await semanticDigest(built), await semanticDigest(read));
  // Neither source nor extensions is normalized, built or read.
  assert.ok(canonicalize(read).includes('"syntax":"U\\r\\nBL"'));
  assert.ok(canonicalize(read).includes('"de.example":"x\\r\\ny"'));
  // A document assembled without documentOf is normalized where it is written.
  const literal = { ...read, values: new Map([['/BT-1', { value: 'a\r\nb' }]]) };
  assert.ok(canonicalize(literal).includes('"/BT-1":"a\\nb"'));
});

test('the canonical form is written from the content, whatever order a caller assembled', () => {
  const read = readDocumentOrThrow(example('minimal.esj.json'));
  const reversed = new Map([...read.values].reverse());
  const assembled = {
    format: 'something else',
    version: '9.9',
    semanticModel: read.semanticModel,
    values: reversed,
    extensions: { t: 'object' as const, members: [] },
    source: {},
  };
  assert.equal(canonicalize(assembled), canonicalize(read));
  assert.equal(pretty(assembled), pretty(read));
  assert.equal(canonicalize(assembled), example('minimal.canonical.esj.json'));
});

test('a string with no canonical form names the value it stands in', () => {
  const document = documentOf({
    semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
    values: [['/BG-4/BT-29/0', { value: 'X', scheme: 'a\uD800' }]],
  });
  assert.throws(() => canonicalize(document), (failure: unknown) => failure instanceof EsjError
    && failure.code === FindingCode.L1_SURROGATE && failure.path === '/BG-4/BT-29/0'
    && failure.subject === 'values["/BG-4/BT-29/0"].scheme');
});
