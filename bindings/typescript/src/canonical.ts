import type { SemanticDocument, SemanticValue } from './document.ts';
import { VALUE_MEMBERS, hasComponent } from './document.ts';
import type { JsonNode, JsonObject } from './json/tree.ts';
import { MAX_DECIMAL_LENGTH } from './grammars.ts';

/**
 * The canonical form of the specification, section 7, and the pretty form of section 7.7.
 *
 * The canonical form gives one content exactly one byte sequence, which is what both digests
 * are taken over (section 8). Three things distinguish it from [RFC8785], and each is
 * implemented here rather than borrowed: the members of `values` are in the path order of
 * section 7.4, a number inside `extensions` is canonicalized from its lexical form and never
 * through a binary floating point value, and member names are sorted by Unicode code point
 * rather than by UTF-16 code unit.
 */

/** Returns the canonical serialization of a document (section 7). */
export function canonicalize(document: SemanticDocument): string {
  const parts: string[] = [];
  parts.push(member('format', string(document.format)));
  parts.push(member('version', string(document.version)));
  parts.push(member('semanticModel', string(document.semanticModel)));
  parts.push(member('values', values(document)));
  if (document.extensions !== undefined) {
    parts.push(member('extensions', extensions(document.extensions)));
  }
  if (document.source !== undefined) {
    parts.push(member('source', source(document)));
  }
  return '{' + parts.join(',') + '}';
}

/**
 * Returns the canonical serialization the semantic digest is taken over: the edition and the
 * values, and nothing else (section 8.2).
 */
export function canonicalSemanticContent(document: SemanticDocument): string {
  return '{' + member('semanticModel', string(document.semanticModel))
    + ',' + member('values', values(document)) + '}';
}

/** Returns the canonical bytes of a document, which is what a digest is computed over. */
export function canonicalBytes(document: SemanticDocument): Uint8Array {
  return new TextEncoder().encode(canonicalize(document));
}

function member(name: string, value: string): string {
  return string(name) + ':' + value;
}

function values(document: SemanticDocument): string {
  const parts: string[] = [];
  for (const [path, value] of document.values) {
    parts.push(member(path, valueOf(value)));
  }
  return '{' + parts.join(',') + '}';
}

function valueOf(value: SemanticValue): string {
  if (!hasComponent(value)) {
    return string(value.value);
  }
  const parts: string[] = [];
  for (const name of VALUE_MEMBERS) {
    const component = value[name];
    if (component !== undefined) {
      parts.push(member(name, string(component)));
    }
  }
  return '{' + parts.join(',') + '}';
}

function source(document: SemanticDocument): string {
  const parts: string[] = [];
  if (document.source?.syntax !== undefined) {
    parts.push(member('syntax', string(document.source.syntax)));
  }
  if (document.source?.sha256 !== undefined) {
    parts.push(member('sha256', string(document.source.sha256)));
  }
  return '{' + parts.join(',') + '}';
}

function extensions(node: JsonObject): string {
  return node1(node);
}

function node1(node: JsonNode): string {
  switch (node.t) {
    case 'object': {
      const members = [...node.members].sort(
        (left, right) => compareCodePoints(left.name, right.name));
      return '{' + members.map((entry) => member(entry.name, node1(entry.value))).join(',') + '}';
    }
    case 'array':
      return '[' + node.items.map(node1).join(',') + ']';
    case 'string':
      return string(node.value);
    case 'number':
      return canonicalNumber(node.raw);
    case 'boolean':
      return node.value ? 'true' : 'false';
    case 'null':
      return 'null';
  }
}

/**
 * Compares two strings by Unicode code point, which is the order the canonical form sorts
 * the member names inside `extensions` by (section 7.6).
 *
 * It is not the order the language's own comparison gives: `<` on a string compares UTF-16
 * code units, and those place every supplementary code point below U+E000 to U+FFFF, which
 * is the one order that does not agree with the bytes the canonical form is made of.
 */
export function compareCodePoints(left: string, right: string): number {
  const a = [...left];
  const b = [...right];
  const shared = Math.min(a.length, b.length);
  for (let i = 0; i < shared; i++) {
    const x = a[i].codePointAt(0)!;
    const y = b[i].codePointAt(0)!;
    if (x !== y) {
      return x < y ? -1 : 1;
    }
  }
  return a.length - b.length;
}

const SHORT_ESCAPES: Record<string, string> = {
  '\b': '\\b',
  '\t': '\\t',
  '\n': '\\n',
  '\f': '\\f',
  '\r': '\\r',
  '"': '\\"',
  '\\': '\\\\',
};

/**
 * Escapes a string as section 7.5 requires: the five two-character escapes, `\u00xx` with
 * lowercase hexadecimal digits for the other C0 controls, and every other code point
 * literally — the solidus is not escaped and nothing above U+007F is.
 */
export function string(value: string): string {
  let out = '"';
  for (const c of value) {
    const short = SHORT_ESCAPES[c];
    if (short !== undefined) {
      out += short;
      continue;
    }
    const code = c.codePointAt(0)!;
    out += code < 0x20 ? '\\u' + code.toString(16).padStart(4, '0') : c;
  }
  return out + '"';
}

/**
 * Returns the canonical decimal form of a JSON number, computed from the spelling the
 * document gave it (section 7.6, rule 2).
 *
 * The decimal point is shifted by the exponent, the leading zeros of the integer part and
 * the trailing zeros of the fraction part are removed, and the result is written in plain
 * notation. This is string processing: no numeric type takes part in it, nothing is rounded,
 * and no digit is lost.
 *
 * @param raw the number as the document spells it
 * @return its canonical decimal form
 */
export function canonicalNumber(raw: string): string {
  const match = /^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(raw);
  if (match === null) {
    throw new Error('not the spelling of a JSON number: ' + raw);
  }
  const sign = match[1];
  let digits = match[2] + (match[3] ?? '');
  let point = match[2].length + Number(match[4] ?? '0');
  if (point <= 0) {
    digits = '0'.repeat(1 - point) + digits;
    point += 1 - point;
  }
  if (point > digits.length) {
    digits += '0'.repeat(point - digits.length);
  }
  let integer = digits.slice(0, point).replace(/^0+(?=\d)/, '');
  let fraction = digits.slice(point).replace(/0+$/, '');
  const text = fraction === '' ? integer : integer + '.' + fraction;
  return text === '0' ? '0' : sign + text;
}

/**
 * Tells whether the canonical decimal form of a JSON number stays inside the 64-character
 * bound the specification puts on a decimal form (sections 6.4 and 7.6).
 */
export function isNumberWithinBound(raw: string): boolean {
  return canonicalNumber(raw).length <= MAX_DECIMAL_LENGTH;
}

/**
 * Returns the pretty form of a document (section 7.7): the canonical content with two spaces
 * of indentation, one member per line, and no trailing newline.
 *
 * It is the form the examples of the repository are stored in. Canonicalizing it and
 * canonicalizing any other layout of the same content produce the same bytes.
 */
export function pretty(document: SemanticDocument): string {
  const lines: string[] = [];
  lines.push('{');
  const parts: string[] = [];
  parts.push(prettyMember('format', string(document.format), 1));
  parts.push(prettyMember('version', string(document.version), 1));
  parts.push(prettyMember('semanticModel', string(document.semanticModel), 1));
  parts.push(prettyMember('values', prettyValues(document, 1), 1));
  if (document.extensions !== undefined) {
    parts.push(prettyMember('extensions', prettyNode(document.extensions, 1), 1));
  }
  if (document.source !== undefined) {
    parts.push(prettyMember('source', prettySource(document, 1), 1));
  }
  lines.push(parts.join(',\n'));
  lines.push('}');
  return lines.join('\n');
}

function indent(level: number): string {
  return '  '.repeat(level);
}

function prettyMember(name: string, value: string, level: number): string {
  return indent(level) + string(name) + ': ' + value;
}

function prettyValues(document: SemanticDocument, level: number): string {
  if (document.values.size === 0) {
    return '{}';
  }
  const parts: string[] = [];
  for (const [path, value] of document.values) {
    parts.push(prettyMember(path, prettyValue(value, level + 1), level + 1));
  }
  return '{\n' + parts.join(',\n') + '\n' + indent(level) + '}';
}

function prettyValue(value: SemanticValue, level: number): string {
  if (!hasComponent(value)) {
    return string(value.value);
  }
  const parts: string[] = [];
  for (const name of VALUE_MEMBERS) {
    const component = value[name];
    if (component !== undefined) {
      parts.push(prettyMember(name, string(component), level + 1));
    }
  }
  return '{\n' + parts.join(',\n') + '\n' + indent(level) + '}';
}

function prettySource(document: SemanticDocument, level: number): string {
  const parts: string[] = [];
  if (document.source?.syntax !== undefined) {
    parts.push(prettyMember('syntax', string(document.source.syntax), level + 1));
  }
  if (document.source?.sha256 !== undefined) {
    parts.push(prettyMember('sha256', string(document.source.sha256), level + 1));
  }
  return '{\n' + parts.join(',\n') + '\n' + indent(level) + '}';
}

function prettyNode(node: JsonNode, level: number): string {
  switch (node.t) {
    case 'object': {
      if (node.members.length === 0) {
        return '{}';
      }
      const members = [...node.members].sort(
        (left, right) => compareCodePoints(left.name, right.name));
      const parts = members.map(
        (entry) => prettyMember(entry.name, prettyNode(entry.value, level + 1), level + 1));
      return '{\n' + parts.join(',\n') + '\n' + indent(level) + '}';
    }
    case 'array': {
      if (node.items.length === 0) {
        return '[]';
      }
      const parts = node.items.map(
        (item) => indent(level + 1) + prettyNode(item, level + 1));
      return '[\n' + parts.join(',\n') + '\n' + indent(level) + ']';
    }
    case 'string':
      return string(node.value);
    case 'number':
      return node.raw;
    case 'boolean':
      return node.value ? 'true' : 'false';
    case 'null':
      return 'null';
  }
}
