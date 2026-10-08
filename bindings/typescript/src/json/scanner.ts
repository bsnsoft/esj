import { FindingCode } from '../codes.ts';
import { utf8Length } from '../grammars.ts';
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
 * Two bounds are applied here, to every token of the document, because they size what a token
 * may cost before the reader knows where it stands: a number token at the string bound of
 * section 12.2, and a string the reader keeps at the bound the reader names for the place it
 * stands in, both counted in UTF-8 bytes while the token is read. A member name is read whole
 * and handed to the reader, which holds it to the string bound itself, because a finding about
 * a name names the member it stands for; a name below a value the reader walks past is held
 * to that bound here.
 *
 * Every offset a message names is the offset in UTF-8 bytes, counted from zero, of the
 * beginning of the token the scanner stopped at (section 9.5).
 */

/** What kind of JSON value the scanner stands on. */
export type JsonKind = 'string' | 'number' | 'true' | 'false' | 'null' | 'array' | 'object';

/**
 * What the scanner calls where it cannot go on: the text is not JSON (`ESJ-L1-JSON`), or a
 * bound stopped it (`ESJ-L1-LIMIT`). The reader reports the finding and ends the read, so
 * the call never returns.
 */
export type Failure = (code: FindingCode, message: string) => never;

/**
 * What {@link Scanner.readString} returns for a string past its bound that carries a lone
 * surrogate: the surrogate alone, which every caller refuses for the surrogate.
 */
export const LONE_SURROGATE = '\ud800';

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
  private nameAt = 0;
  /** Whether a member name was read and the colon after it not yet. */
  private colonPending = false;

  constructor(text: string, limits: Limits, fail: Failure) {
    this.text = text;
    this.limits = limits;
    this.fail = fail;
  }

  /** The offset of the next character, counted from zero in UTF-16 code units. */
  get offset(): number {
    return this.at;
  }

  /**
   * The offset in UTF-8 bytes, counted from zero, of the member name {@link readName} read
   * last: the place a finding about that name points at where a message names one.
   */
  get nameOffset(): number {
    return this.bytesBefore(this.nameAt);
  }

  /** Returns the offset in UTF-8 bytes of a place given in UTF-16 code units. */
  bytesBefore(index: number): number {
    return utf8Length(this.text.slice(0, index));
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
    this.colon();
    this.skipWhitespace();
    if (this.at >= this.text.length) {
      return this.malformed('the document ends where a value belongs', this.at);
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
        return this.malformed('a JSON value does not begin with ' + describeCharacter(c), this.at);
    }
  }

  /**
   * Reads the scalar the scanner stands on to its end and checks that it is one JSON value,
   * without keeping it; a container is left where it stands.
   *
   * A reader that refuses a value for its JSON type asks this first: a token that is not a
   * whole JSON value — `tru`, `01`, `1.` — is not a JSON text at all, and that is the first
   * thing to say about it (section 9.6). An object or an array is told by its first character
   * and is refused there.
   */
  checkToken(kind: JsonKind): void {
    if (kind !== 'object' && kind !== 'array') {
      this.skipScalar(kind);
    }
  }

  /** Consumes the bracket that opens the object or array the scanner stands on. */
  open(): void {
    this.colon();
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
        return this.malformed('a member is followed by a comma or by the end of the object',
          this.at);
      }
      this.at++;
      this.skipWhitespace();
    }
    if (this.at >= text.length || text.charCodeAt(this.at) !== QUOTE) {
      return this.malformed('a member name is a JSON string', this.at);
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
        return this.malformed('an element is followed by a comma or by the end of the array',
          this.at);
      }
      this.at++;
    }
    return true;
  }

  /**
   * Reads the member name the scanner stands on.
   *
   * The name is read whole: the reader holds every member name to the string bound of
   * section 12.2 and names the member in the finding, which takes the name. The document bound
   * has already sized it. The colon after the name is read where the value is, so that a defect
   * of the name is judged before the text after it (section 9.6): `"foo" 1` in the envelope is an
   * undefined member before it is a missing colon.
   */
  readName(): string {
    this.nameAt = this.at;
    const name = this.string(Number.POSITIVE_INFINITY, false);
    this.colonPending = true;
    return name;
  }

  /** Reads the colon after the member name read last, where it has not been read yet. */
  private colon(): void {
    if (!this.colonPending) {
      return;
    }
    this.colonPending = false;
    this.skipWhitespace();
    if (this.at >= this.text.length || this.text.charCodeAt(this.at) !== COLON) {
      this.malformed('a member name is followed by a colon', this.at);
    }
    this.at++;
  }

  /**
   * Reads the string the scanner stands on and returns what it decodes to, measuring its content
   * in UTF-8 bytes against the bound the reader sets for the place it stands in.
   *
   * A string past the bound is read to its end without being kept, because the checks of one
   * string run in a fixed order and a lone surrogate comes before the bound (section 9.6): one
   * that carries a lone surrogate is returned as that surrogate alone, `'\ud800'`, which every
   * caller refuses for the surrogate before it asks anything else; any other one is refused with
   * `ESJ-L1-LIMIT`.
   *
   * @param bound the most UTF-8 bytes the content may take here
   * @param normalized whether the content is measured after CR LF has become LF, as a string
   *   inside `values` is (section 6.8), or as it stands
   */
  readString(bound: number, normalized: boolean): string {
    this.colon();
    this.skipWhitespace();
    return this.string(bound, normalized);
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
    this.colon();
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
      return this.malformed('a JSON number carries at least one digit here', start);
    }
    if (at - integer > 1 && text.charCodeAt(integer) === 0x30) {
      this.at = integer + 1;
      return this.malformed('a JSON number carries no leading zero', start);
    }
    if (at < text.length && text.charCodeAt(at) === 0x2e) {
      const fraction = at + 1;
      at = digits(text, fraction);
      if (at === fraction) {
        this.at = at;
        return this.malformed('a fraction carries at least one digit', start);
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
        return this.malformed('an exponent carries at least one digit', start);
      }
    }
    this.at = at;
    if (at - start > this.limits.maxStringBytes) {
      return this.fail(FindingCode.L1_LIMIT, 'a number is spelled with more than '
        + this.limits.maxStringBytes + ' characters, at offset ' + this.bytesBefore(start));
    }
    return at;
  }

  /**
   * Consumes `true`, `false` or `null`. A letter, a digit, `_` or `$` straight after the word
   * makes the token another word, which is not a JSON value.
   */
  literal(word: 'true' | 'false' | 'null'): void {
    this.colon();
    this.skipWhitespace();
    const start = this.at;
    if (!this.text.startsWith(word, start) || isWordCharacter(
      this.text.charCodeAt(start + word.length))) {
      this.malformed('a JSON literal is true, false or null', start);
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
   * extends its bounds to that walk. A container is counted from the level the caller names:
   * the value of a member of `values` is level 1, the value of a member of a value object
   * level 2, the way levels are counted inside `extensions`. A container past the bound is
   * `ESJ-L1-LIMIT`, because the reader stopped rather than finished judging (section 9.6).
   *
   * Nothing below the value is judged but the JSON text and the bounds: a member name is held
   * to the string bound and a number token to the same bound, as everywhere, and neither a
   * repeated name nor a lone surrogate is looked for (section 9.6). A string is checked to be
   * JSON and never built.
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
            + this.limits.maxExtensionDepth + ' levels, at offset ' + this.bytesBefore(this.at));
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
            const name = this.readName();
            if (utf8Length(name) > this.limits.maxStringBytes) {
              this.fail(FindingCode.L1_LIMIT, 'a member name is longer than '
                + this.limits.maxStringBytes + ' bytes, at offset ' + this.nameOffset);
            }
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
      this.malformed('the document carries content after the object that closes it', this.at);
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
   * Reads one JSON string, the opening quotation mark included. The bytes are counted as the
   * string is read: a code unit below U+0080 is one, below U+0800 two, any other three, and a
   * low surrogate that completes a pair one more, which makes the pair four. A lone surrogate
   * counts three, the bytes of the replacement character an encoder would write; the reader
   * refuses such a string for the surrogate wherever it keeps one (section 6.8). Measured
   * normalized, a LF straight after a CR counts nothing, because the two become one LF.
   *
   * Past the bound the rest of the string is read without being kept, looking for a lone
   * surrogate only: see {@link readString}.
   */
  private string(bound: number, normalized: boolean): string {
    const text = this.text;
    const start = this.at;
    this.at++;
    let value = '';
    let plain = this.at;
    let bytes = 0;
    let high = false;
    let lone = false;
    let cr = false;
    let kept = true;
    for (;;) {
      if (this.at >= text.length) {
        return this.malformed('the document ends inside a string', start);
      }
      const c = text.charCodeAt(this.at);
      if (c === QUOTE) {
        this.at++;
        if (high) {
          lone = true;
        }
        if (kept) {
          return value + text.slice(plain, this.at - 1);
        }
        if (lone) {
          return LONE_SURROGATE;
        }
        return this.fail(FindingCode.L1_LIMIT, 'a string is longer than ' + bound
          + ' bytes, at offset ' + this.bytesBefore(start));
      }
      let unit = c;
      if (c === BACKSLASH) {
        if (kept) {
          value += text.slice(plain, this.at);
        }
        this.at++;
        const decoded = this.escape(start);
        if (kept) {
          value += decoded;
        }
        plain = this.at;
        unit = decoded.charCodeAt(0);
      } else if (c < 0x20) {
        return this.malformed('a control character is escaped inside a string', start);
      } else {
        this.at++;
      }
      if (high && unit >= 0xdc00 && unit <= 0xdfff) {
        bytes += 1;
        high = false;
      } else {
        if (high || (unit >= 0xdc00 && unit <= 0xdfff)) {
          lone = true;
        }
        if (!(normalized && cr && unit === 0x0a)) {
          bytes += unit < 0x80 ? 1 : unit < 0x800 ? 2 : 3;
        }
        high = unit >= 0xd800 && unit <= 0xdbff;
      }
      cr = unit === 0x0d;
      if (kept && bytes > bound) {
        kept = false;
        value = '';
      }
    }
  }

  /** Checks one JSON string to be well formed and steps past it, building nothing. */
  private skipString(): void {
    const text = this.text;
    const start = this.at;
    this.at++;
    for (;;) {
      if (this.at >= text.length) {
        this.malformed('the document ends inside a string', start);
      }
      const c = text.charCodeAt(this.at);
      if (c === QUOTE) {
        this.at++;
        return;
      }
      if (c === BACKSLASH) {
        this.at++;
        this.escape(start);
        continue;
      }
      if (c < 0x20) {
        this.malformed('a control character is escaped inside a string', start);
      }
      this.at++;
    }
  }

  /**
   * Reads one escape, the backslash already consumed, and returns the code unit it stands for.
   *
   * @param start where the string the escape stands in begins, which a message names
   */
  private escape(start: number): string {
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
            return this.malformed('a \\u escape carries four hexadecimal digits', start);
          }
          code = code * 16 + digit;
          this.at++;
        }
        return String.fromCharCode(code);
      }
      default:
        this.at--;
        return this.malformed('a backslash inside a string begins an escape', start);
    }
  }

  /**
   * Ends the scan with `ESJ-L1-JSON`, naming the offset in UTF-8 bytes of the token the scanner
   * stopped at, or of the character where no token could begin.
   */
  private malformed(message: string, at: number): never {
    return this.fail(FindingCode.L1_JSON,
      'the document is not a JSON text: ' + message + ', at offset ' + this.bytesBefore(at));
  }
}

/**
 * Tells whether a character continues a word, which makes `truex` one token and no JSON
 * literal: an ASCII letter or digit, `_` or `$`.
 */
function isWordCharacter(c: number): boolean {
  return (c >= 0x30 && c <= 0x39) || ((c | 0x20) >= 0x61 && (c | 0x20) <= 0x7a)
    || c === 0x5f || c === 0x24;
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
