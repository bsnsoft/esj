import type { SemanticDocument, SemanticValue } from './document.ts';
import { VALUE_MEMBERS, hasComponent } from './document.ts';
import { FindingCode } from './codes.ts';
import { EsjError } from './errors.ts';
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
  return writeNode(node, false, 0);
}

/** One container of an extension subtree the writer has opened and not yet closed. */
interface Frame {
  /** The members, sorted, or the elements, each with no name. */
  readonly entries: ReadonlyArray<readonly [string | undefined, JsonNode]>;
  readonly close: string;
  readonly level: number;
  next: number;
}

/**
 * Writes an extension subtree, in the canonical or in the pretty form.
 *
 * The walk is a loop with the containers it has opened held on a stack, not in call frames:
 * a document a caller assembled may nest deeper than a reader would have accepted, and one a
 * reader accepted under a raised nesting bound may nest as deep as that bound, so a recursive
 * writer would answer either with a stack overflow rather than with a serialization.
 *
 * @param root the subtree
 * @param pretty whether to write the pretty form of section 7.7
 * @param level the indentation level of the subtree in the pretty form
 * @return the serialization
 */
function writeNode(root: JsonNode, pretty: boolean, level: number): string {
  const out: string[] = [];
  const open: Frame[] = [];
  let pending: JsonNode | undefined = root;
  let pendingLevel = level;
  for (;;) {
    if (pending !== undefined) {
      const frame = writeScalarOrOpen(pending, pretty, pendingLevel, out);
      if (frame !== undefined) {
        open.push(frame);
      }
      pending = undefined;
    }
    const top = open[open.length - 1];
    if (top === undefined) {
      return out.join('');
    }
    if (top.next < top.entries.length) {
      const [name, child] = top.entries[top.next];
      if (top.next > 0) {
        out.push(',');
      }
      if (pretty) {
        out.push('\n' + indent(top.level + 1));
      }
      if (name !== undefined) {
        out.push(string(name) + (pretty ? ': ' : ':'));
      }
      top.next++;
      pending = child;
      pendingLevel = top.level + 1;
    } else {
      if (pretty) {
        out.push('\n' + indent(top.level));
      }
      out.push(top.close);
      open.pop();
    }
  }
}

/**
 * Writes a scalar, or an empty container, and returns the frame of a container that has
 * members or elements to write.
 *
 * A number keeps its spelling in the pretty form, which section 7.7 allows; in the canonical
 * form it is written in the canonical decimal form of section 7.6, rule 2.
 */
function writeScalarOrOpen(
  node: JsonNode, pretty: boolean, level: number, out: string[],
): Frame | undefined {
  switch (node.t) {
    case 'object': {
      if (node.members.length === 0) {
        out.push('{}');
        return undefined;
      }
      const members = [...node.members].sort(
        (left, right) => compareCodePoints(left.name, right.name));
      out.push('{');
      return {
        entries: members.map((entry) => [entry.name, entry.value] as const),
        close: '}', level, next: 0,
      };
    }
    case 'array':
      if (node.items.length === 0) {
        out.push('[]');
        return undefined;
      }
      out.push('[');
      return {
        entries: node.items.map((item) => [undefined, item] as const),
        close: ']', level, next: 0,
      };
    case 'string':
      out.push(string(node.value));
      return undefined;
    case 'number':
      out.push(pretty ? node.raw : canonicalNumber(node.raw));
      return undefined;
    case 'boolean':
      out.push(node.value ? 'true' : 'false');
      return undefined;
    case 'null':
      out.push('null');
      return undefined;
  }
}

/**
 * Compares two strings by Unicode code point, which is the order the canonical form sorts
 * the member names inside `extensions` by (section 7.6).
 *
 * It is not the order the language's own comparison gives: `<` on a string compares UTF-16
 * code units, and those place every supplementary code point below U+E000 to U+FFFF, which
 * is the one order that does not agree with the bytes the canonical form is made of. The two
 * strings are compared unit by unit and the first unit that differs is moved to where its
 * code point sorts: a surrogate above every other unit, U+E000 to U+FFFF just below it. That
 * is the code point order without decoding either string.
 */
export function compareCodePoints(left: string, right: string): number {
  const shared = Math.min(left.length, right.length);
  for (let i = 0; i < shared; i++) {
    const x = left.charCodeAt(i);
    const y = right.charCodeAt(i);
    if (x !== y) {
      return inCodePointOrder(x) < inCodePointOrder(y) ? -1 : 1;
    }
  }
  return left.length - right.length;
}

function inCodePointOrder(unit: number): number {
  if (unit >= 0xd800 && unit <= 0xdfff) {
    return unit + 0x2000;
  }
  return unit >= 0xe000 ? unit - 0x800 : unit;
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
 *
 * A string that carries a lone surrogate has no UTF-8 encoding, so it has no canonical form
 * either: it is refused with `ESJ-L1-SURROGATE` rather than written with a replacement
 * character, which would give two different strings one canonical form and one digest
 * (sections 6.8 and 3.4). A reader never hands such a string over; a document assembled by
 * hand may carry one.
 *
 * @throws EsjError carrying `ESJ-L1-SURROGATE` where the string carries a lone surrogate
 */
export function string(value: string): string {
  let out = '"';
  let plain = 0;
  for (let i = 0; i < value.length; i++) {
    const code = value.charCodeAt(i);
    if (code >= 0xd800 && code <= 0xdfff) {
      const next = i + 1 < value.length ? value.charCodeAt(i + 1) : 0;
      if (code > 0xdbff || next < 0xdc00 || next > 0xdfff) {
        throw new EsjError('a string carries a lone surrogate and has no UTF-8 encoding, so it'
          + ' has no canonical form.', FindingCode.L1_SURROGATE);
      }
      i++;
      continue;
    }
    if (code >= 0x20 && code !== 0x22 && code !== 0x5c) {
      continue;
    }
    out += value.slice(plain, i);
    const c = value.charAt(i);
    const short = SHORT_ESCAPES[c];
    out += short !== undefined ? short : '\\u' + code.toString(16).padStart(4, '0');
    plain = i + 1;
  }
  return out + value.slice(plain) + '"';
}

/**
 * The magnitude an exponent is saturated at while it is accumulated. It is exact in a double
 * together with every other term of the arithmetic below, each of which is a length in a
 * document and therefore below 2^31; and it is far above the number of digits any token can
 * carry, so a saturated exponent pushes the canonical form past the bound whatever the
 * mantissa is.
 */
const EXPONENT_SATURATION = 2 ** 50;

/** The largest accumulated exponent another digit may still be appended to. */
const EXPONENT_LAST_SAFE = Math.floor((EXPONENT_SATURATION - 9) / 10);

function isDigitAt(text: string, at: number): boolean {
  const c = text.charCodeAt(at);
  return c >= 0x30 && c <= 0x39;
}

/**
 * Returns the canonical decimal form of a JSON number, or `undefined` where that form is
 * longer than the 64 characters of sections 6.4 and 7.6.
 *
 * The length of the form is computed from the position of the decimal point and the number of
 * significant digits before a character of it is written, so a spelling such as `1e999999999`
 * or `1e-999999999` is answered at once and costs nothing: no digit string is ever longer than
 * the bound.
 *
 * @param raw the number as the document spells it
 * @return its canonical decimal form, or `undefined`
 * @throws Error if the string is not the spelling of a JSON number
 */
export function canonicalDecimalForm(raw: string): string | undefined {
  const length = raw.length;
  let at = 0;
  const negative = raw.charCodeAt(0) === 0x2d;
  if (negative) {
    at++;
  }
  const integerStart = at;
  while (at < length && isDigitAt(raw, at)) {
    at++;
  }
  const integerEnd = at;
  let fractionStart = at;
  let fractionEnd = at;
  if (at < length && raw.charCodeAt(at) === 0x2e) {
    at++;
    fractionStart = at;
    while (at < length && isDigitAt(raw, at)) {
      at++;
    }
    fractionEnd = at;
  }
  let exponent = 0;
  let exponentDigits = 1;
  if (at < length && (raw.charCodeAt(at) | 0x20) === 0x65) {
    at++;
    const exponentNegative = raw.charCodeAt(at) === 0x2d;
    if (exponentNegative || raw.charCodeAt(at) === 0x2b) {
      at++;
    }
    const exponentStart = at;
    while (at < length && isDigitAt(raw, at)) {
      const digit = raw.charCodeAt(at) - 0x30;
      exponent = exponent <= EXPONENT_LAST_SAFE ? exponent * 10 + digit : EXPONENT_SATURATION;
      at++;
    }
    exponentDigits = at - exponentStart;
    if (exponentNegative) {
      exponent = -exponent;
    }
  }
  if (at !== length || integerEnd === integerStart || exponentDigits === 0
    || (fractionStart !== integerEnd && fractionEnd === fractionStart)) {
    throw new Error('not the spelling of a JSON number: ' + forNumberMessage(raw));
  }
  const digits = raw.slice(integerStart, integerEnd) + raw.slice(fractionStart, fractionEnd);
  let first = 0;
  while (first < digits.length && digits.charCodeAt(first) === 0x30) {
    first++;
  }
  let last = digits.length;
  while (last > first && digits.charCodeAt(last - 1) === 0x30) {
    last--;
  }
  if (first === last) {
    return '0';
  }
  const point = integerEnd - integerStart + exponent - first;
  const significant = last - first;
  const fractionDigits = significant - point;
  let size: number;
  if (fractionDigits <= 0) {
    size = point;
  } else if (fractionDigits < significant) {
    size = significant + 1;
  } else {
    size = fractionDigits + 2;
  }
  if (negative) {
    size++;
  }
  if (size > MAX_DECIMAL_LENGTH) {
    return undefined;
  }
  const sign = negative ? '-' : '';
  if (fractionDigits <= 0) {
    return sign + digits.slice(first, last) + '0'.repeat(-fractionDigits);
  }
  if (fractionDigits < significant) {
    const split = first + point;
    return sign + digits.slice(first, split) + '.' + digits.slice(split, last);
  }
  return sign + '0.' + '0'.repeat(fractionDigits - significant) + digits.slice(first, last);
}

/** The start of a spelling that is no number, for a message: never the whole of it. */
function forNumberMessage(raw: string): string {
  return JSON.stringify(raw.length > 80 ? raw.slice(0, 80) + '…' : raw);
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
 * @throws EsjError carrying `ESJ-L1-EXT-NUMBER` where that form is longer than 64 characters
 * @throws Error if the string is not the spelling of a JSON number
 */
export function canonicalNumber(raw: string): string {
  const canonical = canonicalDecimalForm(raw);
  if (canonical === undefined) {
    throw new EsjError('the canonical decimal form of the number ' + forNumberMessage(raw)
      + ' is longer than ' + MAX_DECIMAL_LENGTH + ' characters.', FindingCode.L1_EXT_NUMBER);
  }
  return canonical;
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
    parts.push(prettyMember('extensions', writeNode(document.extensions, true, 1), 1));
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
