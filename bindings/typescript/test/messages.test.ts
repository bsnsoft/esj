import { test } from 'node:test';
import assert from 'node:assert/strict';
import { escapeForMessage, forMessage, isBidiControl, steersATerminal } from '../src/grammars.ts';

/**
 * The one set of characters no message and no subject of this implementation carries as it
 * stands (specification, section 9.5), class by class, and the escaping over each of them. The
 * classes and the code points are those of the Java implementation's test of the same set, so
 * that the three implementations are held to one list.
 */

function hex(codePoint: number): string {
  return 'U+' + codePoint.toString(16).toUpperCase().padStart(4, '0');
}

function assertSteersAndIsEscaped(codePoint: number): void {
  assert.ok(steersATerminal(codePoint), hex(codePoint));
  const fragment = 'RE-' + String.fromCodePoint(codePoint) + '1';
  const escaped = 'RE-\\u' + codePoint.toString(16).padStart(4, '0') + '1';
  assert.equal(escapeForMessage(fragment), escaped, hex(codePoint));
  assert.equal(forMessage(fragment), escaped, hex(codePoint));
}

const CLASSES: Array<[string, number[]]> = [
  ['C0 control', [0x00, 0x07, 0x08, 0x0b, 0x0c, 0x1b, 0x1f]],
  ['delete', [0x7f]],
  ['C1 control', [0x80, 0x85, 0x8d, 0x90, 0x9b, 0x9c, 0x9d, 0x9f]],
  ['line or paragraph separator', [0x2028, 0x2029]],
  ['bidirectional control', [0x061c, 0x200e, 0x200f, 0x202a, 0x202b, 0x202c, 0x202d, 0x202e,
    0x2066, 0x2067, 0x2068, 0x2069]],
];

for (const [name, codePoints] of CLASSES) {
  test('a ' + name + ' steers a terminal and is escaped as \\u and four lowercase digits', () => {
    for (const codePoint of codePoints) {
      assertSteersAndIsEscaped(codePoint);
    }
  });
}

test('the bidirectional controls are the characters of the Unicode property Bidi_Control', () => {
  for (const codePoint of CLASSES[4][1]) {
    assert.ok(isBidiControl(codePoint), hex(codePoint));
  }
});

test('a character that says something is left alone', () => {
  // The neighbours of every range, and characters that look like candidates and are not.
  for (const codePoint of [0x20, 0x41, 0x7e, 0xa0, 0xa9, 0xfc, 0x061b, 0x061d, 0x0628, 0x200b,
    0x200c, 0x200d, 0x2027, 0x202f, 0x2065, 0x206a, 0x20ac, 0xfeff, 0x1f600]) {
    assert.ok(!steersATerminal(codePoint), hex(codePoint));
    assert.ok(!isBidiControl(codePoint), hex(codePoint));
    const text = 'a' + String.fromCodePoint(codePoint) + 'b';
    assert.equal(escapeForMessage(text), text, hex(codePoint));
    assert.equal(forMessage(text), text, hex(codePoint));
  }
});

test('the three whitespace controls belong to the set and are written by name', () => {
  assert.equal(escapeForMessage('a\tb'), 'a\\tb');
  assert.equal(escapeForMessage('a\nb'), 'a\\nb');
  assert.equal(escapeForMessage('a\rb'), 'a\\rb');
  assert.equal(escapeForMessage('a"b\\c'), 'a\\"b\\\\c');
});

test('an excerpt is cut in code units, ends in three dots, and keeps a surrogate pair whole', () => {
  assert.equal(forMessage('x'.repeat(81)), 'x'.repeat(80) + '...');
  assert.equal(forMessage('x'.repeat(80)), 'x'.repeat(80));
  assert.equal(forMessage('x'.repeat(79) + '\u{1F600}y'), 'x'.repeat(79) + '...');
});
