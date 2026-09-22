import { FindingCode } from '../codes.ts';
import type { Limits } from '../limits.ts';
import { utf8Length } from '../grammars.ts';
import type { JsonArray, JsonMember, JsonNode, JsonObject } from './tree.ts';

/**
 * The JSON parser an ESJ reader needs.
 *
 * A stock parser cannot be used for three reasons, and each of them is a requirement of the
 * specification rather than a preference. A repeated member name has to survive the parse,
 * because a reader MUST reject such a document and MUST NOT silently keep one of the two
 * (section 4.2, rule 3). A number has to keep the spelling the document gave it, because the
 * canonical form of a number inside `extensions` is computed from its lexical form and never
 * from a binary floating point value (section 7.6). And the limits of section 12.2 have to
 * fire while the input is read rather than after it is held, which is what they are for.
 */

/** What the parser raises where it cannot go on. */
export class ParseFailure extends Error {
  /** The finding code this failure carries. */
  readonly code: FindingCode;

  /** The offset in the text, counted from zero, at which the parser stopped. */
  readonly offset: number;

  /**
   * The member of `values` the parser was inside, or the empty string where a bound was met
   * outside one. A limit met while a member is read is a finding about that member, and
   * section 9.5 asks such a finding to carry the member's path.
   */
  readonly path: string;

  constructor(message: string, code: FindingCode, offset: number, path = '') {
    super(message);
    this.name = 'ParseFailure';
    this.code = code;
    this.offset = offset;
    this.path = path;
  }
}

/**
 * Where in the envelope the parser is, which is what decides the limits that apply to the
 * container it is about to read.
 */
type Place = 'document' | 'values' | 'value' | 'extensions' | 'extension' | 'other';

const WHITESPACE = new Set([' ', '\t', '\n', '\r']);
const DIGITS = '0123456789';

class Parser {
  private readonly text: string;
  private readonly limits: Limits;
  private at = 0;
  private depth = 0;
  private extensionNodes = 0;
  private member = '';

  constructor(text: string, limits: Limits) {
    this.text = text;
    this.limits = limits;
  }

  /** Reads the one JSON value the text holds, and checks that nothing follows it. */
  parse(): JsonObject {
    this.skipWhitespace();
    const value = this.readValue('document');
    this.skipWhitespace();
    if (this.at < this.text.length) {
      throw this.malformed('the document carries more than one JSON value');
    }
    if (value.t !== 'object') {
      throw this.malformed('the top level of an ESJ document is a JSON object');
    }
    return value;
  }

  private readValue(place: Place): JsonNode {
    if (place === 'extension') {
      this.countExtensionNode();
    }
    const c = this.peek();
    switch (c) {
      case '{':
        return this.readObject(place);
      case '[':
        return this.readArray(place);
      case '"':
        return { t: 'string', value: this.readString() };
      case 't':
        this.expect('true');
        return { t: 'boolean', value: true };
      case 'f':
        this.expect('false');
        return { t: 'boolean', value: false };
      case 'n':
        this.expect('null');
        return { t: 'null' };
      default:
        if (c === '-' || DIGITS.includes(c)) {
          return { t: 'number', raw: this.readNumber() };
        }
        throw this.malformed('a JSON value does not begin with ' + describe(c));
    }
  }

  private readObject(place: Place): JsonObject {
    this.open();
    this.at++;
    const members: JsonMember[] = [];
    this.skipWhitespace();
    if (this.peek() === '}') {
      this.at++;
      this.close();
      return { t: 'object', members };
    }
    for (;;) {
      this.skipWhitespace();
      if (this.peek() !== '"') {
        throw this.malformed('a member name is a JSON string');
      }
      const name = this.readString();
      this.skipWhitespace();
      if (this.peek() !== ':') {
        throw this.malformed('a member name is followed by a colon');
      }
      this.at++;
      this.skipWhitespace();
      const outer = this.member;
      if (place === 'values') {
        this.member = name;
      }
      const value = this.readValue(this.within(place, name));
      this.member = outer;
      members.push({ name, value });
      this.countMembers(place, members.length);
      this.skipWhitespace();
      const c = this.peek();
      this.at++;
      if (c === '}') {
        this.close();
        return { t: 'object', members };
      }
      if (c !== ',') {
        throw this.malformed('a member is followed by a comma or by the end of the object');
      }
    }
  }

  private readArray(place: Place): JsonArray {
    this.open();
    this.at++;
    const items: JsonNode[] = [];
    this.skipWhitespace();
    if (this.peek() === ']') {
      this.at++;
      this.close();
      return { t: 'array', items };
    }
    for (;;) {
      this.skipWhitespace();
      items.push(this.readValue(place === 'extension' ? 'extension' : 'other'));
      this.skipWhitespace();
      const c = this.peek();
      this.at++;
      if (c === ']') {
        this.close();
        return { t: 'array', items };
      }
      if (c !== ',') {
        throw this.malformed('an element is followed by a comma or by the end of the array');
      }
    }
  }

  /**
   * Returns where the value of a member lies, which is what decides the limits over it.
   *
   * The two places that matter are named by the envelope: the members of `values` and the
   * members of a value object are bounded in number, and everything below an owner token of
   * `extensions` is bounded in depth and in the number of nodes.
   */
  private within(place: Place, name: string): Place {
    if (place === 'document') {
      return name === 'values' ? 'values' : name === 'extensions' ? 'extensions' : 'other';
    }
    if (place === 'values') {
      return 'value';
    }
    if (place === 'extensions' || place === 'extension') {
      return 'extension';
    }
    return 'other';
  }

  private countMembers(place: Place, members: number): void {
    if (place === 'values' && members > this.limits.maxValues) {
      throw this.limit('values carries more than ' + this.limits.maxValues + ' members');
    }
    if (place === 'value' && members > this.limits.maxValueMembers) {
      throw this.limit(
        'a value object carries more than ' + this.limits.maxValueMembers + ' members');
    }
  }

  private countExtensionNode(): void {
    this.extensionNodes++;
    if (this.extensionNodes > this.limits.maxExtensionNodes) {
      throw this.limit(
        'extensions carries more than ' + this.limits.maxExtensionNodes + ' nodes');
    }
  }

  /**
   * Counts one level of nesting.
   *
   * The bound is the one of section 12.2 on the depth inside `extensions`, where the value
   * of an owner-token member is level 1. The document object and the `extensions` object
   * itself are not levels, so two containers are open before the first one that counts, and
   * the same bound is what a reader walks a container inside `values` against.
   */
  private open(): void {
    this.depth++;
    if (this.depth > this.limits.maxExtensionDepth + 2) {
      throw this.limit(
        'the document nests deeper than ' + this.limits.maxExtensionDepth + ' levels');
    }
  }

  private close(): void {
    this.depth--;
  }

  private readString(): string {
    this.at++;
    let value = '';
    let plain = this.at;
    for (;;) {
      if (this.at >= this.text.length) {
        throw this.malformed('the document ends inside a string');
      }
      const c = this.text.charAt(this.at);
      if (c === '"') {
        value += this.text.slice(plain, this.at);
        this.at++;
        this.boundString(value);
        return value;
      }
      if (c === '\\') {
        value += this.text.slice(plain, this.at);
        this.at++;
        value += this.readEscape();
        plain = this.at;
        continue;
      }
      if (c < ' ') {
        throw this.malformed('a control character is escaped inside a string');
      }
      this.at++;
    }
  }

  private readEscape(): string {
    const c = this.text.charAt(this.at);
    this.at++;
    switch (c) {
      case '"':
        return '"';
      case '\\':
        return '\\';
      case '/':
        return '/';
      case 'b':
        return '\b';
      case 'f':
        return '\f';
      case 'n':
        return '\n';
      case 'r':
        return '\r';
      case 't':
        return '\t';
      case 'u': {
        const digits = this.text.slice(this.at, this.at + 4);
        if (digits.length < 4 || !/^[0-9a-fA-F]{4}$/.test(digits)) {
          throw this.malformed('a \\u escape carries four hexadecimal digits');
        }
        this.at += 4;
        return String.fromCharCode(parseInt(digits, 16));
      }
      default:
        throw this.malformed('a backslash inside a string begins an escape');
    }
  }

  /**
   * Refuses a string longer than a reader is willing to hold.
   *
   * The bound applied here is the larger of the two of section 12.2, because the parser does
   * not yet know whether the string it has read is the content of a binary object; the
   * reader applies the smaller one where it knows that it is not.
   */
  private boundString(value: string): void {
    if (utf8Length(value) > this.limits.maxBinaryValueBytes) {
      throw this.limit(
        'a string is longer than ' + this.limits.maxBinaryValueBytes + ' bytes');
    }
  }

  private readNumber(): string {
    const start = this.at;
    if (this.peek() === '-') {
      this.at++;
    }
    this.readDigits();
    if (this.peek() === '.') {
      this.at++;
      this.readDigits();
    }
    const exponent = this.peek();
    if (exponent === 'e' || exponent === 'E') {
      this.at++;
      const sign = this.peek();
      if (sign === '+' || sign === '-') {
        this.at++;
      }
      this.readDigits();
    }
    const raw = this.text.slice(start, this.at);
    if (raw.length > this.limits.maxStringBytes) {
      throw this.limit('a number is spelled with more than '
        + this.limits.maxStringBytes + ' characters');
    }
    if (!/^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?$/.test(raw)) {
      throw this.malformed('a JSON number does not read ' + describe(raw));
    }
    return raw;
  }

  private readDigits(): void {
    const start = this.at;
    while (this.at < this.text.length && DIGITS.includes(this.text.charAt(this.at))) {
      this.at++;
    }
    if (this.at === start) {
      throw this.malformed('a JSON number carries at least one digit here');
    }
  }

  private skipWhitespace(): void {
    while (this.at < this.text.length && WHITESPACE.has(this.text.charAt(this.at))) {
      this.at++;
    }
  }

  private peek(): string {
    return this.at < this.text.length ? this.text.charAt(this.at) : '';
  }

  private expect(word: string): void {
    if (this.text.slice(this.at, this.at + word.length) !== word) {
      throw this.malformed('a JSON value does not read ' + describe(this.peek()));
    }
    this.at += word.length;
  }

  private malformed(message: string): ParseFailure {
    return new ParseFailure(
      message + ', at offset ' + this.at, FindingCode.L1_JSON, this.at);
  }

  private limit(message: string): ParseFailure {
    return new ParseFailure(
      message + ', at offset ' + this.at, FindingCode.L1_LIMIT, this.at, this.member);
  }
}

function describe(fragment: string): string {
  return fragment === '' ? 'the end of the document' : JSON.stringify(fragment);
}

/**
 * Parses a JSON text into the tree of `tree.ts`, enforcing the limits that are decided while
 * the input is read.
 *
 * @param text the document, already decoded from UTF-8
 * @param limits the limits this reader runs with
 * @return the document object
 * @throws ParseFailure if the text is not a JSON object, or a limit stopped the parse
 */
export function parseJson(text: string, limits: Limits): JsonObject {
  return new Parser(text, limits).parse();
}
