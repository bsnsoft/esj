import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
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
  const stored = example('minimal.esj.json');
  assert.equal(pretty(readDocumentOrThrow(stored)) + '\n', stored);
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
