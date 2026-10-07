import { FindingCode } from '../codes.ts';
import type { Limits } from '../limits.ts';

/**
 * The JSON scanner the reader walks a document with, one token at a time.
 *
 * A stock parser cannot be used for three reasons, and each of them is a requirement of the
 * specification rather than a preference. A repeated member name has to survive the parse,
 * because a reader MUST reject such a document and MUST NOT silently keep one of the two
 * (section 4.2, rule 3). A number has to keep the spelling the document gave it, because the
 * canonical form of a number inside `extensions` is computed from its lexical form and never
 * from a binary floating point value (section 7.6). And the limits of section 12.2 have to
 * fire while the input is read rather than after it is held, which is what they are for.
 *
 * The scanner builds nothing. The reader asks it for the next token where the envelope says
 * what belongs there, keeps what it judges, and has the scanner walk past what it refuses
 * (`skipValue`) without holding it: an array of a million zeros written where a string
 * belongs costs the walk and no tree. Every walk is a loop with the open containers on a
 * stack of its own, never a recursion, so a nesting bound a caller raised is answered with a
 * document or a finding and never with a stack overflow.
 *
 * Three bounds are applied here, to every token of the document, because they size what a
 * token may cost before the reader knows where it stands: a number token at the string bound,
 * a member name at the larger of the string bound and the path bound, and a string the reader
 * keeps at the larger of the two string bounds. They are coarse guards counted in UTF-16 code
 * units, which are never more than the UTF-8 bytes of the same string, so they never refuse a
 * token the finer bounds of the reader, counted in bytes, would have taken.
 */

/** What kind of JSON value the scanner stands on. */
export type JsonKind = 'string' | 'number' | 'true' | 'false' | 'null' | 'array' | 'object';

/**
 * What the scanner calls where it cannot go on: the text is not JSON (`ESJ-L1-JSON`), or a
 * bound stopped it (`ESJ-L1-LIMIT`). The reader reports the finding and ends the read, so
 * the call never returns.
 */
export type Failure = (code: FindingCode, message: string) => never;

const QUOTE = 0x22;
const BACKSLASH = 0x5c;
const COMMA = 0x2c;
const COLON = 0x3a;
const CLOSE_OBJECT = 0x7d;
const CLOSE_ARRAY = 0x5d;

/** One container a walk has opened and not yet closed. */
interface Open {
  readonly object: boolean;
  any: boolean;
}

export class Scanner {
  private readonly text: string;
  private readonly limits: Limits;
  private readonly fail: Failure;
  private at = 0;

  constructor(text: string, limits: Limits, fail: Failure) {
    this.text = text;
    this.limits = limits;
    this.fail = fail;
  }

  /** The offset of the next character, counted from zero in UTF-16 code units. */
  get offset(): number {
    return this.at;
  }

  /** Steps over the four whitespace characters of RFC 8259. */
  skipWhitespace(): void {
    const text = this.text;
    let at = this.at;
    while (at < text.length) {
      const c = text.charCodeAt(at);
      if (c !== 0x20 && c !== 0x09 && c !== 0x0a && c !== 0x0d) {
        break;
      }
      at++;
    }
    this.at = at;
  }

  /** Returns what kind of value begins at the next token, without consuming it. */
  peekKind(): JsonKind {
    this.skipWhitespace();
    if (this.at >= this.text.length) {
      return this.malformed('the document ends where a value belongs');
    }
    const c = this.text.charAt(this.at);
    switch (c) {
      case '"':
        return 'string';
      case '{':
        return 'object';
      case '[':
        return 'array';
      case 't':
        return 'true';
      case 'f':
        return 'false';
      case 'n':
        return 'null';
      default:
        if (c === '-' || (c >= '0' && c <= '9')) {
          return 'number';
        }
        return this.malformed('a JSON value does not begin with ' + describeCharacter(c));
    }
  }

  /** Consumes the bracket that opens the object or array the scanner stands on. */
  open(): void {
    this.skipWhitespace();
    this.at++;
  }

  /**
   * Stands on the name of the next member of an open object, or consumes the brace that
   * closes it.
   *
   * @param any whether the object has had a member before this one
   * @return `true` where a member follows, `false` where the object closed
   */
  nextMember(any: boolean): boolean {
    this.skipWhitespace();
    const text = this.text;
    if (this.at < text.length && text.charCodeAt(this.at) === CLOSE_OBJECT) {
      this.at++;
      return false;
    }
    if (any) {
      if (this.at >= text.length || text.charCodeAt(this.at) !== COMMA) {
        return this.malformed('a member is followed by a comma or by the end of the object');
      }
      this.at++;
      this.skipWhitespace();
    }
    if (this.at >= text.length || text.charCodeAt(this.at) !== QUOTE) {
      return this.malformed('a member name is a JSON string');
    }
    return true;
  }

  /**
   * Stands on the next element of an open array, or consumes the bracket that closes it.
   *
   * @param any whether the array has had an element before this one
   * @return `true` where an element follows, `false` where the array closed
   */
  nextElement(any: boolean): boolean {
    this.skipWhitespace();
    const text = this.text;
    if (this.at < text.length && text.charCodeAt(this.at) === CLOSE_ARRAY) {
      this.at++;
      return false;
    }
    if (any) {
      if (this.at >= text.length || text.charCodeAt(this.at) !== COMMA) {
        return this.malformed('an element is followed by a comma or by the end of the array');
      }
      this.at++;
    }
    return true;
  }

  /**
   * Reads the member name the scanner stands on and the colon after it, holding the name to
   * the larger of the string bound and the path bound of section 12.2.
   */
  readName(): string {
    const name = this.string(Math.max(this.limits.maxStringBytes, this.limits.maxPathBytes),
      'a member name');
    this.skipWhitespace();
    if (this.at >= this.text.length || this.text.charCodeAt(this.at) !== COLON) {
      return this.malformed('a member name is followed by a colon');
    }
    this.at++;
    return name;
  }

  /**
   * Reads the string the scanner stands on, holding it to the larger of the two string
   * bounds of section 12.2: the scanner does not know whether it is the content of a binary
   * object, and the reader applies the finer bound where it knows.
   */
  readString(): string {
    this.skipWhitespace();
    return this.string(
      Math.max(this.limits.maxStringBytes, this.limits.maxBinaryValueBytes), 'a string');
  }

  /**
   * Reads the number token the scanner stands on and returns it as the document spells it.
   *
   * The token is held to the string bound of section 12.2, counted in its own characters,
   * which for a JSON number are ASCII and therefore its UTF-8 bytes as well: a spelling of
   * any length may canonicalize to a short number, so the bound on the canonical form cannot
   * size what the scanner reads, and this one does.
   */
  readNumber(): string {
    this.skipWhitespace();
    const start = this.at;
    return this.text.slice(start, this.number());
  }

  /** Checks the number token the scanner stands on and steps past it. */
  private number(): number {
    const text = this.text;
    const start = this.at;
    let at = start;
    if (at < text.length && text.charCodeAt(at) === 0x2d) {
      at++;
    }
    const integer = at;
    at = digits(text, at);
    if (at === integer) {
      this.at = at;
      return this.malformed('a JSON number carries at least one digit here');
    }
    if (at - integer > 1 && text.charCodeAt(integer) === 0x30) {
      this.at = integer + 1;
      return this.malformed('a JSON number carries no leading zero');
    }
    if (at < text.length && text.charCodeAt(at) === 0x2e) {
      const fraction = at + 1;
      at = digits(text, fraction);
      if (at === fraction) {
        this.at = at;
        return this.malformed('a fraction carries at least one digit');
      }
    }
    if (at < text.length && (text.charCodeAt(at) | 0x20) === 0x65) {
      at++;
      if (at < text.length && (text.charCodeAt(at) === 0x2b || text.charCodeAt(at) === 0x2d)) {
        at++;
      }
      const exponent = at;
      at = digits(text, exponent);
      if (at === exponent) {
        this.at = at;
        return this.malformed('an exponent carries at least one digit');
      }
    }
    this.at = at;
    if (at - start > this.limits.maxStringBytes) {
      return this.fail(FindingCode.L1_LIMIT, 'a number is spelled with more than '
        + this.limits.maxStringBytes + ' characters, at offset ' + start);
    }
    return at;
  }

  /** Consumes `true`, `false` or `null`. */
  literal(word: 'true' | 'false' | 'null'): void {
    this.skipWhitespace();
    if (!this.text.startsWith(word, this.at)) {
      this.malformed('a JSON literal is true, false or null');
    }
    this.at += word.length;
  }

  /**
   * Walks past the value the scanner stands on without holding any of it, and bounds the
   * walk.
   *
   * A member of `values` is a string or a value object and a member of a value object is a
   * string (section 6.1), so an object or an array in either place is an error whatever it
   * holds — but the reader has to get past it to reach the next member, and section 12.2
   * extends the nesting bound to that walk. A container is counted from the level the caller
   * names: the value of a member of `values` is level 1, the value of a member of a value
   * object level 2, the way levels are counted inside `extensions`. A container past the bound
   * is `ESJ-L1-LIMIT`, because the reader stopped rather than finished judging (section 9.6).
   *
   * Member names are read and held to the name bound, as everywhere; a string is checked to
   * be JSON and never built.
   *
   * @param level the level the value occupies where it is a container
   */
  skipValue(level: number): void {
    const open: Open[] = [];
    let depth = level;
    for (;;) {
      const kind = this.peekKind();
      if (kind === 'object' || kind === 'array') {
        if (depth > this.limits.maxExtensionDepth) {
          this.fail(FindingCode.L1_LIMIT, 'a value nests deeper than '
            + this.limits.maxExtensionDepth + ' levels, at offset ' + this.at);
        }
        this.at++;
        open.push({ object: kind === 'object', any: false });
        depth++;
      } else {
        this.skipScalar(kind);
      }
      for (;;) {
        if (open.length === 0) {
          return;
        }
        const top = open[open.length - 1];
        if (top.object ? this.nextMember(top.any) : this.nextElement(top.any)) {
          top.any = true;
          if (top.object) {
            this.readName();
          }
          break;
        }
        open.pop();
        depth--;
      }
    }
  }

  /** Requires that nothing but whitespace follows the value the document consists of. */
  end(): void {
    this.skipWhitespace();
    if (this.at < this.text.length) {
      this.malformed('the document carries content after the object that closes it');
    }
  }

  private skipScalar(kind: JsonKind): void {
    switch (kind) {
      case 'string':
        this.skipWhitespace();
        this.skipString();
        return;
      case 'number':
        this.skipWhitespace();
        this.number();
        return;
      case 'true':
      case 'false':
      case 'null':
        this.literal(kind);
        return;
      default:
        return;
    }
  }

  /**
   * Reads one JSON string, the opening quotation mark included, and refuses it where it is
   * longer than the bound, in UTF-16 code units.
   */
  private string(bound: number, what: string): string {
    const text = this.text;
    const start = this.at;
    this.at++;
    let value = '';
    let plain = this.at;
    for (;;) {
      if (this.at >= text.length) {
        return this.malformed('the document ends inside a string');
      }
      const c = text.charCodeAt(this.at);
      if (c === QUOTE) {
        value += text.slice(plain, this.at);
        this.at++;
        if (value.length > bound) {
          return this.fail(FindingCode.L1_LIMIT, what + ' is longer than this reader accepts,'
            + ' at offset ' + start);
        }
        return value;
      }
      if (c === BACKSLASH) {
        value += text.slice(plain, this.at);
        this.at++;
        value += this.escape();
        plain = this.at;
        if (value.length > bound) {
          return this.fail(FindingCode.L1_LIMIT, what + ' is longer than this reader accepts,'
            + ' at offset ' + start);
        }
        continue;
      }
      if (c < 0x20) {
        return this.malformed('a control character is escaped inside a string');
      }
      this.at++;
    }
  }

  /** Checks one JSON string to be well formed and steps past it, building nothing. */
  private skipString(): void {
    const text = this.text;
    this.at++;
    for (;;) {
      if (this.at >= text.length) {
        this.malformed('the document ends inside a string');
      }
      const c = text.charCodeAt(this.at);
      if (c === QUOTE) {
        this.at++;
        return;
      }
      if (c === BACKSLASH) {
        this.at++;
        this.escape();
        continue;
      }
      if (c < 0x20) {
        this.malformed('a control character is escaped inside a string');
      }
      this.at++;
    }
  }

  private escape(): string {
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
        let code = 0;
        for (let i = 0; i < 4; i++) {
          const digit = this.at < this.text.length ? hexValue(this.text.charCodeAt(this.at)) : -1;
          if (digit < 0) {
            return this.malformed('a \\u escape carries four hexadecimal digits');
          }
          code = code * 16 + digit;
          this.at++;
        }
        return String.fromCharCode(code);
      }
      default:
        this.at--;
        return this.malformed('a backslash inside a string begins an escape');
    }
  }

  private malformed(message: string): never {
    return this.fail(FindingCode.L1_JSON,
      'the document is not a JSON text: ' + message + ', at offset ' + this.at);
  }
}

/** Returns the offset after the run of ASCII digits that starts at an offset. */
function digits(text: string, from: number): number {
  let at = from;
  while (at < text.length) {
    const c = text.charCodeAt(at);
    if (c < 0x30 || c > 0x39) {
      break;
    }
    at++;
  }
  return at;
}

function hexValue(c: number): number {
  if (c >= 0x30 && c <= 0x39) {
    return c - 0x30;
  }
  const lower = c | 0x20;
  if (lower >= 0x61 && lower <= 0x66) {
    return lower - 0x61 + 10;
  }
  return -1;
}

function describeCharacter(c: string): string {
  return JSON.stringify(c);
}

/** Names a JSON type in a message, so that it says what the document carries. */
export function describe(kind: JsonKind): string {
  switch (kind) {
    case 'string':
      return 'a string';
    case 'number':
      return 'a number';
    case 'true':
      return 'the boolean true';
    case 'false':
      return 'the boolean false';
    case 'null':
      return 'null';
    case 'array':
      return 'an array';
    case 'object':
      return 'an object';
  }
}
