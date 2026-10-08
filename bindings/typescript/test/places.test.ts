import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FindingCode } from '../src/codes.ts';
import { EsjError } from '../src/errors.ts';
import type { Limits } from '../src/limits.ts';
import { readDocument, readDocumentOrThrow } from '../src/reader.ts';
import { escapeForMessage } from '../src/grammars.ts';

/**
 * Where a finding of layer L1 points (specification, section 9.5), what the reader holds to
 * the bounds of section 12.2, and how far it reads (section 9.6).
 *
 * A finding names the member it is about by its member access: a name the specification
 * defines in dot form, a name the document chose in bracket form, an array element by its
 * index. Every probe here is written as the text a reader receives, so that what is compared is
 * what the bytes give.
 */

const HEAD = '{"format":"EN16931-Semantic-JSON","version":"0.1",'
  + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020",';

type Place = [string, string, string];

/** The findings of a read, as path, code and subject, in the order the reader reported them. */
function places(text: string, limits?: Partial<Limits>): Place[] {
  return readDocument(text, limits === undefined ? undefined : { limits }).findings
    .map((entry): Place => [entry.path, entry.code, entry.subject]);
}

/** The message of the one finding a read reports. */
function messageOf(text: string, limits?: Partial<Limits>): string {
  const findings = readDocument(text, limits === undefined ? undefined : { limits }).findings;
  assert.equal(findings.length, 1, JSON.stringify(findings));
  return findings[0].message;
}

/** The offset in UTF-8 bytes of the first occurrence of a fragment in a text. */
function bytesBefore(text: string, fragment: string): number {
  const at = text.indexOf(fragment);
  assert.ok(at >= 0, fragment);
  return Buffer.byteLength(text.slice(0, at), 'utf8');
}

test('a repeated name is named by the member access of the name that repeats', () => {
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":"a","/BT-1":"b"}}'),
    [['', FindingCode.L1_DUPLICATE_MEMBER, 'values["/BT-1"]']]);
  assert.deepEqual(places('{"format":"EN16931-Semantic-JSON","format":"x"}'),
    [['', FindingCode.L1_DUPLICATE_MEMBER, 'format']]);
  assert.deepEqual(places(HEAD + '"values":{},"source":{"syntax":"UBL","syntax":"CII"}}'),
    [['', FindingCode.L1_DUPLICATE_MEMBER, 'source.syntax']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"a.b":1,"a.b":2}}'),
    [['', FindingCode.L1_DUPLICATE_MEMBER, 'extensions["a.b"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"a.b":{"x":1,"x":2}}}'),
    [['', FindingCode.L1_DUPLICATE_MEMBER, 'extensions["a.b"]["x"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"a.b":[0,{"x":1,"x":2}]}}'),
    [['', FindingCode.L1_DUPLICATE_MEMBER, 'extensions["a.b"][1]["x"]']]);
  // A value object is one member of values: the finding carries its path and names the object.
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":{"value":"a","scheme":"b","scheme":"c"}}}'),
    [['/BT-1', FindingCode.L1_DUPLICATE_MEMBER, 'values["/BT-1"]']]);
});

test('a name the specification defines is written in dot form, any other in bracket form', () => {
  assert.deepEqual(places(HEAD + '"profile":"x","values":{}}'),
    [['', FindingCode.L1_ENVELOPE_MEMBER, '["profile"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"source":{"foo":"x"}}'),
    [['', FindingCode.L1_ENVELOPE_MEMBER, 'source["foo"]']]);
  assert.deepEqual(places(HEAD
    + '"values":{"/BG-4/BT-29/0":{"value":"X","scheme":"0088","foo":"a"}}}'),
    [['/BG-4/BT-29/0', FindingCode.L1_VALUE_MEMBER, 'values["/BG-4/BT-29/0"]["foo"]']]);
  assert.deepEqual(places(HEAD + '"values":{"/BG-4/BT-29/0":{"value":"X","schemeVersion":"1"}}}'),
    [['/BG-4/BT-29/0', FindingCode.L1_VALUE_MEMBER, 'values["/BG-4/BT-29/0"].schemeVersion']]);
  assert.deepEqual(places(HEAD + '"values":{"/BG-4/BT-29/0":{"value":"X","scheme":""}}}'),
    [['/BG-4/BT-29/0', FindingCode.L1_EMPTY_STRING, 'values["/BG-4/BT-29/0"].scheme']]);
  // A member named like a member of another object is still a name the document chose here.
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":{"value":"X","scheme":"a","syntax":"b"}}}'),
    [['/BT-1', FindingCode.L1_VALUE_MEMBER, 'values["/BT-1"]["syntax"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"source":{"value":"x"}}'),
    [['', FindingCode.L1_ENVELOPE_MEMBER, 'source["value"]']]);
});

test('every required envelope member that is missing is a finding of its own, in order', () => {
  const missing = (...names: string[]): Place[] =>
    names.map((name): Place => ['', FindingCode.L1_ENVELOPE_MEMBER, name]);
  assert.deepEqual(places('{}'), missing('format', 'version', 'semanticModel', 'values'));
  assert.deepEqual(places('{"format":"EN16931-Semantic-JSON"}'),
    missing('version', 'semanticModel', 'values'));
  assert.deepEqual(places('{"values":{},"version":"0.1"}'), missing('format', 'semanticModel'));
});

test('a subject is never cut, only a message is', () => {
  const name = 'profile-' + 'p'.repeat(200);
  const [found] = readDocument(HEAD + '"' + name + '":1,"values":{}}').findings;
  assert.equal(found.code, FindingCode.L1_ENVELOPE_MEMBER);
  assert.equal(found.subject, '["' + name + '"]');
  assert.ok(found.message.includes('...'), found.message);
});

test('a lone surrogate in a name is written \\udxxx in the subject, wherever it stands', () => {
  assert.equal(escapeForMessage('a\uD800b\uDFFF'), 'a\\ud800b\\udfff');
  assert.equal(escapeForMessage('\u{1F600}'), '\u{1F600}', 'a surrogate pair is one character');
  assert.deepEqual(places('{"\\uD800":"y"}'), [['', FindingCode.L1_SURROGATE, '["\\ud800"]']]);
  assert.deepEqual(places(HEAD + '"values":{"\\uDBFFx":"y"}}'),
    [['', FindingCode.L1_SURROGATE, 'values["\\udbffx"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"source":{"\\uD800":"UBL"}}'),
    [['', FindingCode.L1_SURROGATE, 'source["\\ud800"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"\\uDFFF":1}}'),
    [['', FindingCode.L1_SURROGATE, 'extensions["\\udfff"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"o":{"\\uD800":1}}}'),
    [['', FindingCode.L1_SURROGATE, 'extensions["o"]["\\ud800"]']]);
  // Inside a value object the finding names the object, as a repeated name there does.
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":{"value":"A","\\uD800":"y"}}}'),
    [['/BT-1', FindingCode.L1_SURROGATE, 'values["/BT-1"]']]);
  // The message of such a finding is safe to write anywhere a string is written.
  for (const entry of readDocument('{"\\uD800":"y"}').findings) {
    assert.equal(entry.message, Buffer.from(entry.message, 'utf8').toString('utf8'));
  }
});

test('a token that is not a whole JSON value is not JSON, wherever it stands', () => {
  const json: Place = ['', FindingCode.L1_JSON, ''];
  for (const token of ['tru', 'truex', 'nul', '01', '1.', '-', '1e', '"abc']) {
    assert.deepEqual(places('{"format":' + token + '}'), [json], 'format ' + token);
    assert.deepEqual(places(HEAD + '"values":' + token + '}'), [json], 'values ' + token);
    assert.deepEqual(places(HEAD + '"values":{"/BT-1":' + token + '}}'), [json],
      '/BT-1 ' + token);
    assert.deepEqual(places(HEAD + '"values":{},"source":{"syntax":' + token + '}}'), [json],
      'source.syntax ' + token);
  }
  // A token of the wrong type that is whole is refused for its type, and an object or array
  // where a string belongs is refused at its bracket.
  assert.deepEqual(places('{"format":true}'),
    [['', FindingCode.L1_ENVELOPE_VALUE, 'format']]);
  assert.deepEqual(places('{"format":[1,'), [['', FindingCode.L1_ENVELOPE_VALUE, 'format']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":1}}'),
    [['/BT-1', FindingCode.L1_JSON_TYPE, 'values["/BT-1"]']]);
});

test('a broken JSON text is a finding about the document, at the byte offset of its token', () => {
  const values = HEAD + '"values":{"/BT-1":"' + 'ä'.repeat(200) + '","/BT-2":01}}';
  assert.deepEqual(places(values), [['', FindingCode.L1_JSON, '']]);
  assert.ok(messageOf(values).endsWith('at offset ' + bytesBefore(values, '01}')),
    messageOf(values));
  const comma = HEAD + '"values":{"/BT-1":"ä" "/BT-2":"y"}}';
  assert.deepEqual(places(comma), [['', FindingCode.L1_JSON, '']]);
  assert.ok(messageOf(comma).endsWith('at offset ' + bytesBefore(comma, '"/BT-2"')),
    messageOf(comma));
});

test('a defect of a name is found before any defect of the value written under it', () => {
  assert.deepEqual(places(HEAD + '"foo":tru,"values":{}}'),
    [['', FindingCode.L1_ENVELOPE_MEMBER, '["foo"]']]);
  assert.deepEqual(places('{"format":"EN16931-Semantic-JSON","format":tru}'),
    [['', FindingCode.L1_DUPLICATE_MEMBER, 'format']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":"x","/BT-1":01}}'),
    [['', FindingCode.L1_DUPLICATE_MEMBER, 'values["/BT-1"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"not a token":tru}}'),
    [['', FindingCode.L1_OWNER_TOKEN, 'extensions["not a token"]']]);
  assert.deepEqual(places(HEAD + '"values":{"BT 1":"x","/BT-2":"y"}}'),
    [['', FindingCode.L1_PATH_SYNTAX, 'values["BT 1"]']]);
});

test('a name is judged before the colon after it, and the colon before the value', () => {
  assert.deepEqual(places('{"a" 1}'), [['', FindingCode.L1_ENVELOPE_MEMBER, '["a"]']]);
  assert.deepEqual(places('{"profile"'), [['', FindingCode.L1_ENVELOPE_MEMBER, '["profile"]']]);
  assert.deepEqual(places('{"format":"EN16931-Semantic-JSON","format" 1}'),
    [['', FindingCode.L1_DUPLICATE_MEMBER, 'format']]);
  assert.deepEqual(places(HEAD + '"values":{},"source":{"foo" "x"}}'),
    [['', FindingCode.L1_ENVELOPE_MEMBER, 'source["foo"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"urn:x/2" {}}}'),
    [['', FindingCode.L1_OWNER_TOKEN, 'extensions["urn:x/2"]']]);
  // A name that is no path is confined to its member: the reader reads on to the colon.
  assert.deepEqual(places(HEAD + '"values":{"/BT-1x" "a"}}'), [
    ['', FindingCode.L1_PATH_SYNTAX, 'values["/BT-1x"]'],
    ['', FindingCode.L1_JSON, ''],
  ]);
  const format = '{"format" 1}';
  assert.deepEqual(places(format), [['', FindingCode.L1_JSON, '']]);
  assert.ok(messageOf(format).endsWith('at offset ' + bytesBefore(format, '1}')),
    messageOf(format));
});

test('a string that does not close is placed at the quotation mark that opens it', () => {
  for (const text of [
    '{"format":"EN16931-Sem',
    HEAD + '"values":{"/BT-1":"RE-1',
    HEAD + '"values":{"/BT-1":"RE\\',
    '{"form',
  ]) {
    const opening = text.lastIndexOf('"');
    assert.deepEqual(places(text), [['', FindingCode.L1_JSON, '']], text);
    assert.ok(messageOf(text).endsWith('at offset ' + Buffer.byteLength(text.slice(0, opening))),
      messageOf(text));
  }
  assert.ok(messageOf('[]').endsWith('at offset 0'), messageOf('[]'));
});

test('a lone surrogate is found before the bound, however far past it a string runs', () => {
  const small = { maxStringBytes: 64, maxBinaryValueBytes: 64 };
  const long = 'x'.repeat(200);
  const surrogate = (subject: string, path = ''): Place[] =>
    [[path, FindingCode.L1_SURROGATE, subject]];
  assert.deepEqual(places('{"format":"' + long + '\\ud800"}', small), surrogate('format'));
  assert.deepEqual(places(HEAD + '"values":{},"source":{"syntax":"' + long + '\\ud800"}}', small),
    surrogate('source.syntax'));
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"de.example":"' + long
    + '\\ud800"}}', small), surrogate('extensions["de.example"]'));
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":"' + long + '\\ud800"}}', small),
    surrogate('values["/BT-1"]', '/BT-1'));
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":"\\ud800' + long + '"}}', small),
    surrogate('values["/BT-1"]', '/BT-1'));
  // Inside a value object the surrogate stands beside the code of the object, and a string that
  // carries one is held to no bound.
  assert.deepEqual(places(HEAD + '"values":{"/BG-4/BT-29/0":{"value":"' + long
    + '\\ud800","scheme":"0088"}}}', small),
  surrogate('values["/BG-4/BT-29/0"].value', '/BG-4/BT-29/0'));
  assert.deepEqual(places(HEAD + '"values":{"/BG-4/BT-29/0":{"value":"' + long
    + '\\ud800","scheme":"0088"}}}', { maxStringBytes: 64 }),
  surrogate('values["/BG-4/BT-29/0"].value', '/BG-4/BT-29/0'));
  assert.deepEqual(places(HEAD + '"values":{"/BG-4/BT-29/0":{"value":"' + long
    + '\\ud800","scheme":"0088","foo":"z"}}}', small), [
    ['/BG-4/BT-29/0', FindingCode.L1_SURROGATE, 'values["/BG-4/BT-29/0"].value'],
    ['/BG-4/BT-29/0', FindingCode.L1_VALUE_MEMBER, 'values["/BG-4/BT-29/0"]["foo"]'],
  ]);
});

test('a member of a value object past every bound that could apply stops the reader', () => {
  const small = { maxStringBytes: 64, maxBinaryValueBytes: 64 };
  const limit: Place[] =
    [['/BG-4/BT-29/0', FindingCode.L1_LIMIT, 'values["/BG-4/BT-29/0"].value']];
  const mid = 'y'.repeat(100);
  assert.deepEqual(places(HEAD + '"values":{"/BG-4/BT-29/0":{"value":"' + mid
    + '","scheme":"0088","foo":"z"}}}', small), limit);
  assert.deepEqual(places(HEAD + '"values":{"/BG-4/BT-29/0":{"foo":"z","scheme":"0088",'
    + '"value":"' + mid + '"}}}', small), limit);
  assert.deepEqual(places(HEAD + '"values":{"/BG-4/BT-29/0":{"value":"' + mid
    + '","scheme":{"a":"b"}}}}', small), limit);
  // A supplementary component is held to the string bound while it is read, so a lone surrogate
  // in a member read before it is never judged.
  assert.deepEqual(places(HEAD + '"values":{"/BG-4/BT-29/0":{"value":"a\\ud800","scheme":"'
    + 'x'.repeat(200) + '"}}}', { maxStringBytes: 64 }),
  [['/BG-4/BT-29/0', FindingCode.L1_LIMIT, 'values["/BG-4/BT-29/0"].scheme']]);
});

test('below a value the reader walks past, only the JSON text and the bounds are judged', () => {
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":["\\uD800"],"/BT-2":01}}'), [
    ['/BT-1', FindingCode.L1_JSON_TYPE, 'values["/BT-1"]'],
    ['', FindingCode.L1_JSON, ''],
  ]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":[{"a":1,"a":2}],"/BT-2":"x"}}'),
    [['/BT-1', FindingCode.L1_JSON_TYPE, 'values["/BT-1"]']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":[{"\\uD800":1}],"/BT-2":"x"}}'),
    [['/BT-1', FindingCode.L1_JSON_TYPE, 'values["/BT-1"]']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":{"value":"x","scheme":{"\\uD800":1,'
    + '"\\uD800":2}},"/BT-2":"x"}}'),
  [['/BT-1', FindingCode.L1_VALUE_SHAPE, 'values["/BT-1"].scheme']]);
  // An undefined envelope member ends the read and nothing below it is read.
  assert.deepEqual(places(HEAD + '"profile":{"a":"\\uD800"},"values":{}}'),
    [['', FindingCode.L1_ENVELOPE_MEMBER, '["profile"]']]);
});

test('every string of the envelope is held to the string bound and names its member', () => {
  const limit = (subject: string): Place[] => [['', FindingCode.L1_LIMIT, subject]];
  assert.deepEqual(places(HEAD + '"values":{}}', { maxStringBytes: 16 }), limit('format'));
  assert.deepEqual(places('{"format":"EN16931-Semantic-JSON","version":"' + '0'.repeat(30)
    + '"}', { maxStringBytes: 24 }), limit('version'));
  assert.deepEqual(places('{"format":"EN16931-Semantic-JSON","version":"0.1",'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020","values":{}}', { maxStringBytes: 24 }),
  limit('semanticModel'));
  assert.deepEqual(places(HEAD + '"values":{},"source":{"syntax":"' + 'U'.repeat(40) + '"}}',
    { maxStringBytes: 32 }), limit('source.syntax'));
  assert.deepEqual(places(HEAD + '"values":{},"source":{"sha256":"' + 'a'.repeat(65) + '"}}',
    { maxStringBytes: 64 }), limit('source.sha256'));
  // The bound is the one a string has as it is written: source is never normalized.
  assert.deepEqual(places(HEAD + '"values":{},"source":{"syntax":"' + 'U'.repeat(62)
    + '\\r\\n"}}', { maxStringBytes: 63 }), limit('source.syntax'));
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":"' + 'U'.repeat(62) + '\\r\\n"}}',
    { maxStringBytes: 63 }), [], 'a string of values is measured normalized');
  assert.deepEqual(places('{"format":"' + 'x'.repeat(40) + '"}', { maxStringBytes: 16 }),
    limit('format'), 'the bound outranks the fixed value');
});

test('every member name is held to the string bound in UTF-8 bytes, wherever it stands', () => {
  // A name past the bound is no part of the finding: the subject names the object it stands in.
  const name = 'ä'.repeat(40);
  const bound = { maxStringBytes: 64 };
  assert.deepEqual(places('{"' + name + '":"x"}', bound),
    [['', FindingCode.L1_LIMIT, '']]);
  assert.deepEqual(places(HEAD + '"values":{"' + name + '":"x"}}', bound),
    [['', FindingCode.L1_LIMIT, 'values']]);
  assert.deepEqual(places(HEAD + '"values":{},"source":{"' + name + '":"x"}}', bound),
    [['', FindingCode.L1_LIMIT, 'source']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"' + name + '":1}}', bound),
    [['', FindingCode.L1_LIMIT, 'extensions']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"o":{"' + name + '":1}}}', bound),
    [['', FindingCode.L1_LIMIT, 'extensions["o"]']]);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"o":[{"' + name + '":1}]}}', bound),
    [['', FindingCode.L1_LIMIT, 'extensions["o"][0]']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":{"value":"x","' + name + '":"y"}}}', bound),
    [['/BT-1', FindingCode.L1_LIMIT, 'values["/BT-1"]']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":[{"' + name + '":1}]}}', bound),
    [['/BT-1', FindingCode.L1_LIMIT, 'values["/BT-1"]']]);
  // Forty characters are 80 bytes: counted in code units the name would fit, in bytes it does
  // not. One character fewer than the bound is judged by the grammar of its place.
  const fits = 'ä'.repeat(32);
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"' + fits + '":1}}', bound),
    [['', FindingCode.L1_OWNER_TOKEN, 'extensions["' + fits + '"]']]);
  assert.deepEqual(places(HEAD + '"' + fits + '":1,"values":{}}', bound),
    [['', FindingCode.L1_ENVELOPE_MEMBER, '["' + fits + '"]']]);
});

test('a name of the default bound counts its bytes, not its code units', () => {
  const name = 'ä'.repeat(512 * 1024 + 1);
  const text = HEAD + '"values":{},"extensions":{"' + name + '":1}}';
  const [found] = readDocument(text).findings;
  assert.equal(found.code, FindingCode.L1_LIMIT);
  assert.equal(found.subject, 'extensions');
  assert.ok(found.message.endsWith('at offset ' + bytesBefore(text, '"ää') + '.'), found.message);
});

test('every number token is held to the string bound before it is built', () => {
  const bound = { maxStringBytes: 64 };
  const long = '1'.repeat(70);
  const fraction = '1.' + '1'.repeat(70);
  for (const token of [long, fraction, '-' + long, '1e' + '1'.repeat(70)]) {
    assert.deepEqual(places(HEAD + '"values":{"/BT-1":' + token + '}}', bound),
      [['/BT-1', FindingCode.L1_LIMIT, 'values["/BT-1"]']], token);
    assert.deepEqual(places(HEAD + '"values":{"/BT-1":{"value":' + token + ',"scheme":"x"}}}',
      bound), [['/BT-1', FindingCode.L1_LIMIT, 'values["/BT-1"].value']], token);
    assert.deepEqual(places(HEAD + '"values":{},"extensions":{"o":{"n":' + token + '}}}', bound),
      [['', FindingCode.L1_LIMIT, 'extensions["o"]["n"]']], token);
    assert.deepEqual(places(HEAD + '"values":{},"extensions":{"o":[1,' + token + ']}}', bound),
      [['', FindingCode.L1_LIMIT, 'extensions["o"][1]']], token);
    assert.deepEqual(places('{"format":' + token + '}', bound),
      [['', FindingCode.L1_LIMIT, 'format']], token);
  }
  const text = HEAD + '"values":{"/BT-1":"' + 'ä'.repeat(10) + '","/BT-2":' + long + '}}';
  assert.ok(messageOf(text, bound).endsWith('at offset ' + bytesBefore(text, long)),
    messageOf(text, bound));
});

test('a bound met inside a member names that member and carries its path', () => {
  const deep = (levels: number) => '['.repeat(levels) + ']'.repeat(levels);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":' + deep(33) + '}}'),
    [['/BT-1', FindingCode.L1_LIMIT, 'values["/BT-1"]']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":{"value":' + deep(32) + ',"scheme":"x"}}}'),
    [['/BT-1', FindingCode.L1_LIMIT, 'values["/BT-1"].value']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":"' + 'x'.repeat(65) + '"}}',
    { maxStringBytes: 64 }), [['/BT-1', FindingCode.L1_LIMIT, 'values["/BT-1"]']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":{"value":"x","scheme":"' + 'x'.repeat(65)
    + '"}}}', { maxStringBytes: 64 }),
  [['/BT-1', FindingCode.L1_LIMIT, 'values["/BT-1"].scheme']]);
  assert.deepEqual(places(HEAD + '"values":{"/BT-1":"a","/BT-2":"b","/BT-3":"c"}}',
    { maxValues: 2 }), [['', FindingCode.L1_LIMIT, 'values["/BT-3"]']]);
  // Where no member of values holds the place, the path is empty.
  assert.deepEqual(places(HEAD + '"values":{},"extensions":{"o":["' + 'x'.repeat(65) + '"]}}',
    { maxStringBytes: 64 }), [['', FindingCode.L1_LIMIT, 'extensions["o"][0]']]);
});

test('the exception a reader ends with carries the code, the path and the subject', () => {
  const thrown = (text: string): EsjError => {
    try {
      readDocumentOrThrow(text);
    } catch (failure) {
      assert.ok(failure instanceof EsjError);
      return failure;
    }
    return assert.fail('the document was read');
  };
  const syntax = thrown(HEAD + '"values":{"/BT-1x":"X"}}');
  assert.deepEqual([syntax.code, syntax.path, syntax.subject],
    [FindingCode.L1_PATH_SYNTAX, '', 'values["/BT-1x"]']);
  const shape = thrown(HEAD + '"values":{"/BT-1":{"value":"X"}}}');
  assert.deepEqual([shape.code, shape.path, shape.subject],
    [FindingCode.L1_VALUE_SHAPE, '/BT-1', 'values["/BT-1"]']);
  const json = thrown('{"format":tru}');
  assert.deepEqual([json.code, json.path, json.subject], [FindingCode.L1_JSON, '', '']);
});
