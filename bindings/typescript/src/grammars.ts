/**
 * The grammars of the specification, section 6, and the two bounds the specification fixes
 * itself: 64 characters for a decimal form (sections 6.4 and 7.6) and 128 for an owner
 * token (section 4.6). Neither is a configurable limit; each belongs to the grammar it is
 * stated with and is reported with that grammar's code.
 */

/** The longest a decimal form is, counting the sign and the decimal point (section 6.4). */
export const MAX_DECIMAL_LENGTH = 64;

/** The longest an owner token is (section 4.6). */
export const MAX_OWNER_TOKEN_LENGTH = 128;

/** The longest fragment of document content a message reproduces (section 12.6). */
const MESSAGE_EXCERPT = 80;

function isDigit(c: string): boolean {
  return c >= '0' && c <= '9';
}

/**
 * Tells whether a string is the canonical decimal form of section 6.4: an optional minus,
 * an integer part with no leading zero, an optional fraction with no trailing zero, no
 * exponent, no signed zero, and at most {@link MAX_DECIMAL_LENGTH} characters.
 *
 * The bound belongs to the grammar and not to the limits of section 12.2, so every caller
 * that reads content as a number applies it: a value past it is outside the format, and a
 * rule engine that computed with it would decide a business rule over a value the
 * specification does not admit.
 */
export function isDecimal(value: string): boolean {
  let i = 0;
  const length = value.length;
  if (length > MAX_DECIMAL_LENGTH) {
    return false;
  }
  const negative = i < length && value.charAt(i) === '-';
  if (negative) {
    i++;
  }
  const intStart = i;
  if (i >= length || !isDigit(value.charAt(i))) {
    return false;
  }
  if (value.charAt(i) === '0') {
    i++;
  } else {
    while (i < length && isDigit(value.charAt(i))) {
      i++;
    }
  }
  const intIsZero = i - intStart === 1 && value.charAt(intStart) === '0';
  let fractionIsZero = true;
  if (i < length) {
    if (value.charAt(i) !== '.') {
      return false;
    }
    i++;
    const fractionStart = i;
    while (i < length && isDigit(value.charAt(i))) {
      if (value.charAt(i) !== '0') {
        fractionIsZero = false;
      }
      i++;
    }
    if (i !== length || i === fractionStart || value.charAt(length - 1) === '0') {
      return false;
    }
  }
  return !(negative && intIsZero && fractionIsZero);
}

/**
 * Returns why a content is not a decimal, or `null` where it is one.
 *
 * The length is measured first, because a decimal longer than the bound of section 6.4 is
 * refused whatever else is right about it.
 */
export function decimalViolation(value: string): string | null {
  if (value.length > MAX_DECIMAL_LENGTH) {
    return `is ${value.length} characters long; a decimal has at most ${MAX_DECIMAL_LENGTH}`;
  }
  if (isDecimal(value)) {
    return null;
  }
  if (value.length === 0) {
    return 'is empty; a decimal carries at least one digit';
  }
  if (value.includes('e') || value.includes('E')) {
    return 'is written with an exponent, which a decimal never carries';
  }
  if (value.charAt(0) === '+') {
    return 'has a leading plus sign, which a decimal never carries';
  }
  if (value.endsWith('.')) {
    return 'ends in a decimal point, and a fraction part carries at least one digit';
  }
  if (isSignedZero(value)) {
    return 'signs a zero, which is written 0';
  }
  if (hasLeadingZero(value)) {
    return 'has a leading zero in its integer part';
  }
  if (value.includes('.') && value.endsWith('0')) {
    return 'has trailing zeros in its fraction, which carry no numeric information';
  }
  return 'is not written in the canonical decimal form of a decimal';
}

function isSignedZero(value: string): boolean {
  if (value.charAt(0) !== '-') {
    return false;
  }
  for (let i = 1; i < value.length; i++) {
    const c = value.charAt(i);
    if (c !== '0' && c !== '.') {
      return false;
    }
  }
  return value.length > 1;
}

function hasLeadingZero(value: string): boolean {
  const i = value.charAt(0) === '-' ? 1 : 0;
  return i + 1 < value.length && value.charAt(i) === '0' && isDigit(value.charAt(i + 1));
}

function hasDateShape(value: string): boolean {
  if (value.length !== 10 || value.charAt(4) !== '-' || value.charAt(7) !== '-') {
    return false;
  }
  for (let i = 0; i < 10; i++) {
    if (i === 4 || i === 7) {
      continue;
    }
    if (!isDigit(value.charAt(i))) {
      return false;
    }
  }
  return true;
}

/**
 * Tells whether a content is a date of section 6.5: `YYYY-MM-DD`, a year from 1000 to 9999,
 * and a day that exists in the proleptic Gregorian calendar.
 */
export function isDate(value: string): boolean {
  if (!hasDateShape(value) || value.charAt(0) === '0') {
    return false;
  }
  const year = Number(value.slice(0, 4));
  const month = Number(value.slice(5, 7));
  const day = Number(value.slice(8, 10));
  if (month < 1 || month > 12 || day < 1) {
    return false;
  }
  return day <= daysInMonth(year, month);
}

function daysInMonth(year: number, month: number): number {
  const lengths = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (month === 2 && isLeapYear(year)) {
    return 29;
  }
  return lengths[month - 1];
}

function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

/** Returns why a content is not a date, or `null` where it is one. */
export function dateViolation(value: string): string | null {
  if (isDate(value)) {
    return null;
  }
  if (!hasDateShape(value)) {
    return 'is not a date written YYYY-MM-DD';
  }
  if (value.charAt(0) === '0') {
    return 'has a year below 1000; a date carries a year from 1000 to 9999';
  }
  return 'names a day that does not exist in the calendar';
}


const CLOCK_LENGTH = 8;
const MAX_OFFSET_HOURS = 14;

function field(value: string, at: number): number {
  return Number(value.slice(at, at + 2));
}

function hasClockShape(value: string): boolean {
  if (value.length < CLOCK_LENGTH || value.charAt(2) !== ':' || value.charAt(5) !== ':') {
    return false;
  }
  for (let i = 0; i < CLOCK_LENGTH; i++) {
    if (i !== 2 && i !== 5 && !isDigit(value.charAt(i))) {
      return false;
    }
  }
  return true;
}

function hasOffsetShape(value: string): boolean {
  if (
    value.length !== CLOCK_LENGTH + 6 ||
    (value.charAt(CLOCK_LENGTH) !== '+' && value.charAt(CLOCK_LENGTH) !== '-') ||
    value.charAt(CLOCK_LENGTH + 3) !== ':'
  ) {
    return false;
  }
  for (let i = CLOCK_LENGTH + 1; i < value.length; i++) {
    if (i !== CLOCK_LENGTH + 3 && !isDigit(value.charAt(i))) {
      return false;
    }
  }
  return true;
}

function isZeroOffset(value: string): boolean {
  return hasOffsetShape(value) && field(value, CLOCK_LENGTH + 1) === 0 && field(value, CLOCK_LENGTH + 4) === 0;
}

function isCivilOffset(value: string): boolean {
  const hours = field(value, CLOCK_LENGTH + 1);
  const minutes = field(value, CLOCK_LENGTH + 4);
  return hours < MAX_OFFSET_HOURS ? minutes <= 59 : hours === MAX_OFFSET_HOURS && minutes === 0;
}

/**
 * Tells whether a content is a time of section 6.5: `hh:mm:ss` with a required offset,
 * UTC written `Z`, no fractional seconds, no `24:00:00` and no leap second.
 */
export function isTime(value: string): boolean {
  if (!hasClockShape(value) || field(value, 0) > 23 || field(value, 3) > 59 || field(value, 6) > 59) {
    return false;
  }
  if (value.length === CLOCK_LENGTH + 1 && value.charAt(CLOCK_LENGTH) === 'Z') {
    return true;
  }
  return hasOffsetShape(value) && !isZeroOffset(value) && isCivilOffset(value);
}

/** Returns why a content is not a time, or `null` where it is one. */
export function timeViolation(value: string): string | null {
  if (isTime(value)) {
    return null;
  }
  if (!hasClockShape(value)) {
    return 'is not a time of day written hh:mm:ss';
  }
  if (field(value, 0) > 23) {
    return `names the hour ${value.slice(0, 2)}; the last time of a day is 23:59:59`;
  }
  if (field(value, 3) > 59) {
    return `names the minute ${value.slice(3, 5)}`;
  }
  if (field(value, 6) === 60) {
    return 'names a leap second, which a time never carries';
  }
  if (field(value, 6) > 59) {
    return `names the second ${value.slice(6, 8)}`;
  }
  if (value.length === CLOCK_LENGTH) {
    return 'carries no offset; a time is written Z or with an offset such as +02:00';
  }
  if (value.charAt(CLOCK_LENGTH) === '.' || value.charAt(CLOCK_LENGTH) === ',') {
    return 'carries fractional seconds, which a time never does';
  }
  if (hasOffsetShape(value)) {
    if (isZeroOffset(value)) {
      return 'writes UTC as an offset of zero, which is written Z';
    }
    return `has the offset ${value.slice(CLOCK_LENGTH)}, and an offset lies between -${MAX_OFFSET_HOURS}:00 and +${MAX_OFFSET_HOURS}:00`;
  }
  return 'carries no offset written Z, +hh:mm or -hh:mm';
}

const BASE64_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

function base64Value(c: string): number {
  return BASE64_ALPHABET.indexOf(c);
}

/**
 * Tells whether a content is the canonical padded base64 of section 6.7: the standard
 * alphabet of RFC 4648 section 4, required padding, no whitespace, and pad bits at zero.
 */
export function isBase64(value: string): boolean {
  const length = value.length;
  if (length === 0 || length % 4 !== 0) {
    return false;
  }
  let end = length;
  let padding = 0;
  while (end > 0 && value.charAt(end - 1) === '=') {
    end--;
    padding++;
  }
  if (padding > 2) {
    return false;
  }
  for (let i = 0; i < end; i++) {
    if (base64Value(value.charAt(i)) < 0) {
      return false;
    }
  }
  if (padding === 0) {
    return true;
  }
  const tail = base64Value(value.charAt(end - 1));
  const padBits = padding === 1 ? 2 : 4;
  return (tail & ((1 << padBits) - 1)) === 0;
}

/** Returns why a content is not canonical base64, or `null` where it is. */
export function base64Violation(value: string): string | null {
  const length = value.length;
  if (length === 0) {
    return 'is empty';
  }
  if (length % 4 !== 0) {
    return `is ${length} characters long, which is not a multiple of four: padded base64 is written in quanta of four characters`;
  }
  let end = length;
  let padding = 0;
  while (end > 0 && value.charAt(end - 1) === '=') {
    end--;
    padding++;
  }
  if (padding > 2) {
    return `ends with ${padding} padding characters, and base64 padding is one or two`;
  }
  for (let i = 0; i < end; i++) {
    const c = value.charAt(i);
    if (base64Value(c) < 0) {
      return /\s/.test(c)
        ? 'carries whitespace, and base64 is written without line breaks and without whitespace'
        : 'carries a character that is not in the standard base64 alphabet of RFC 4648, section 4';
    }
  }
  if (padding > 0) {
    const tail = base64Value(value.charAt(end - 1));
    const padBits = padding === 1 ? 2 : 4;
    if ((tail & ((1 << padBits) - 1)) !== 0) {
      return 'sets pad bits in its last quantum, which a canonical encoding leaves at zero';
    }
  }
  return null;
}

/** The owner token grammar of section 4.6, without the length bound. */
const OWNER_TOKEN = /^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?$/;

/** Tells whether a member name of `extensions` is an owner token (section 4.6). */
export function isOwnerToken(value: string): boolean {
  if (value.length === 0 || value.length > MAX_OWNER_TOKEN_LENGTH) {
    return false;
  }
  if (value.startsWith('BT-') || value.startsWith('BG-')) {
    return false;
  }
  return OWNER_TOKEN.test(value);
}

/** The edition grammar of section 4.4. */
const EDITION =
  /^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*:[0-9]{4}(?:\/AC[0-9]*:[0-9]{4})?(?:\+A[0-9]+:[0-9]{4}(?:\/AC[0-9]*:[0-9]{4})?)*$/;

/** Tells whether a `semanticModel` satisfies the edition grammar of section 4.4. */
export function isEdition(value: string): boolean {
  return EDITION.test(value);
}

/** The path grammar of section 5.1, as `schema/esj.schema.json` writes it. */
const PATH =
  /^(?:\/BG-(?:[1-9][0-9]*|[A-Z][A-Z0-9]*-[0-9]+)(?:\/(?:0|[1-9][0-9]*))?)*\/BT-(?:[1-9][0-9]*|[A-Z][A-Z0-9]*-[0-9]+)(?:\/(?:0|[1-9][0-9]*))?$/;

/** Tells whether a member name of `values` satisfies the path grammar of section 5.1. */
export function isPath(value: string): boolean {
  return PATH.test(value);
}

/** The `sha256` member of `source`: 64 lowercase hexadecimal digits (section 4.7). */
const SHA256 = /^[0-9a-f]{64}$/;

/** Tells whether a string is a `source.sha256` (section 4.7). */
export function isSha256(value: string): boolean {
  return SHA256.test(value);
}

/**
 * Tells whether a string carries a lone surrogate.
 *
 * A string that carries one has no UTF-8 encoding at all, so a reader and a canonicalizer
 * reject the document (section 6.8) and the code outranks every code whose check reads the
 * content of the same string (section 9.6).
 */
export function hasLoneSurrogate(value: string): boolean {
  for (let i = 0; i < value.length; i++) {
    const unit = value.charCodeAt(i);
    if (unit >= 0xd800 && unit <= 0xdbff) {
      const next = i + 1 < value.length ? value.charCodeAt(i + 1) : 0;
      if (next < 0xdc00 || next > 0xdfff) {
        return true;
      }
      i++;
    } else if (unit >= 0xdc00 && unit <= 0xdfff) {
      return true;
    }
  }
  return false;
}

/**
 * Normalizes line endings as section 6.8 requires: CR LF and a lone CR become LF. It is the
 * one transformation ESJ applies to string content.
 */
export function normalizeLineEndings(value: string): string {
  if (!value.includes('\r')) {
    return value;
  }
  return value.replace(/\r\n?/g, '\n');
}

const UTF8 = new TextEncoder();

/** The length of a string in the bytes of its UTF-8 encoding, which is how a limit counts. */
export function utf8Length(value: string): number {
  return UTF8.encode(value).length;
}

/** How many Unicode code points a string has, which is what `len` counts in a rule. */
export function codePointCount(value: string): number {
  let count = 0;
  for (const _ of value) {
    count++;
  }
  return count;
}

/**
 * Returns a fragment of a document in the form a message may carry it: escaped as
 * section 9.5 requires and cut to the excerpt length of section 12.6.
 */
export function forMessage(value: string, limit: number = MESSAGE_EXCERPT): string {
  const cut = [...value].slice(0, limit).join('');
  const escaped = escapeForMessage(cut);
  return cut.length < value.length ? `${escaped}…` : escaped;
}

/**
 * Escapes a fragment quoted inside a message: a backslash, a quotation mark, the three
 * whitespace controls, every other C0 control, the delete character and the bidirectional
 * formatting characters (specification, section 9.5).
 */
export function escapeForMessage(value: string): string {
  let out = '';
  for (const c of value) {
    const code = c.codePointAt(0)!;
    if (c === '\\') {
      out += '\\\\';
    } else if (c === '"') {
      out += '\\"';
    } else if (c === '\n') {
      out += '\\n';
    } else if (c === '\r') {
      out += '\\r';
    } else if (c === '\t') {
      out += '\\t';
    } else if (code < 0x20 || code === 0x7f || (code >= 0x202a && code <= 0x202e) || (code >= 0x2066 && code <= 0x2069)) {
      out += `\\u${code.toString(16).toUpperCase().padStart(4, '0')}`;
    } else {
      out += c;
    }
  }
  return out;
}
