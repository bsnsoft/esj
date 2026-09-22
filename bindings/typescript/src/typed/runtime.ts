import { Decimal } from '../decimal.ts';
import type { SemanticDocument, SemanticValue } from '../document.ts';
import { EsjError } from '../errors.ts';
import { FindingCode } from '../codes.ts';
import { isBase64, isDate, isDecimal, isTime } from '../grammars.ts';
import { comparePathText } from '../paths.ts';

/**
 * What the generated typed view is built from.
 *
 * The view is names instead of identifiers: `invoice.lines()[0].netAmount()` rather than
 * `document.values.get('/BG-25/0/BT-131')`. Everything that is the same for every business
 * term lives here and is written once; the generator emits one line per term, and the
 * registry decides which line that is.
 */

/** An identifier, with the identification scheme it was issued under (section 6.6). */
export interface Identifier {
  /** The identifier itself. */
  readonly value: string;
  /** The identification scheme, where the value carries one. */
  readonly scheme?: string;
  /** The version of that scheme, where the value carries one. */
  readonly schemeVersion?: string;
}

/** A binary object: the attachment, its media type and its file name (section 6.7). */
export interface BinaryObject {
  /** The content, as canonical base64. */
  readonly value: string;
  /** The media type. */
  readonly mimeCode: string;
  /** The file name. */
  readonly filename: string;
}

/**
 * A document with its paths indexed, so that a view answers a question in the time the
 * answer takes rather than in the size of the invoice.
 */
export class TypedIndex {
  /** The document the view reads. */
  readonly document: SemanticDocument;

  private readonly paths: string[];

  constructor(document: SemanticDocument) {
    this.document = document;
    this.paths = [...document.values.keys()];
  }

  /** Returns the value at a path, or `undefined`. */
  value(path: string): SemanticValue | undefined {
    return this.document.values.get(path);
  }

  /** Tells whether the document carries anything at or below a path. */
  has(prefix: string): boolean {
    let low = 0;
    let high = this.paths.length;
    while (low < high) {
      const middle = (low + high) >> 1;
      if (comparePathText(this.paths[middle], prefix) < 0) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    if (low >= this.paths.length) {
      return false;
    }
    const path = this.paths[low];
    return path === prefix || (path.startsWith(prefix) && path.charAt(prefix.length) === '/');
  }
}

/** How a value of one semantic data type is read. */
export type Read<T> = (value: SemanticValue, path: string) => T;

/** Returns the value a mandatory business term carries. */
export function required<T>(index: TypedIndex, path: string, read: Read<T>): T {
  const value = index.value(path);
  if (value === undefined) {
    throw new EsjError('the document carries no value at ' + path,
      FindingCode.L3_MISSING_TERM, path);
  }
  return read(value, path);
}

/** Returns the value an optional business term carries, or `undefined`. */
export function optional<T>(index: TypedIndex, path: string, read: Read<T>): T | undefined {
  const value = index.value(path);
  return value === undefined ? undefined : read(value, path);
}

/**
 * Returns the values a repeatable business term carries.
 *
 * The occurrences are dense and zero-based (section 5.4), so reading stops at the first
 * index the document does not carry.
 */
export function repeated<T>(index: TypedIndex, base: string, read: Read<T>): T[] {
  const found: T[] = [];
  for (let i = 0; ; i++) {
    const path = base + '/' + i;
    const value = index.value(path);
    if (value === undefined) {
      return found;
    }
    found.push(read(value, path));
  }
}

/** Returns the one instance of a business group that occurs at most once, or `undefined`. */
export function group<T>(
  index: TypedIndex, path: string, make: (index: TypedIndex, path: string) => T,
): T | undefined {
  return index.has(path) ? make(index, path) : undefined;
}

/** Returns the one instance of a mandatory business group. */
export function requiredGroup<T>(
  index: TypedIndex, path: string, make: (index: TypedIndex, path: string) => T,
): T {
  if (!index.has(path)) {
    throw new EsjError('the document carries no business group instance at ' + path,
      FindingCode.L3_MISSING_GROUP, path);
  }
  return make(index, path);
}

/** Returns the instances of a repeatable business group, in the order they are written. */
export function groups<T>(
  index: TypedIndex, base: string, make: (index: TypedIndex, path: string) => T,
): T[] {
  const found: T[] = [];
  for (let i = 0; ; i++) {
    const path = base + '/' + i;
    if (!index.has(path)) {
      return found;
    }
    found.push(make(index, path));
  }
}

/** Reads a value whose semantic data type carries no grammar: text, a code, a reference. */
export function textOf(value: SemanticValue): string {
  return value.value;
}

/** Reads a decimal, refusing content that is not the canonical form of section 6.4. */
export function decimalOf(value: SemanticValue, path: string): Decimal {
  if (!isDecimal(value.value)) {
    throw new EsjError('the value at ' + path + ' is not a decimal of section 6.4',
      FindingCode.L2_DECIMAL, path);
  }
  return Decimal.of(value.value);
}

/** Reads a calendar date, as the `YYYY-MM-DD` the specification fixes (section 6.5). */
export function dateOf(value: SemanticValue, path: string): string {
  if (!isDate(value.value)) {
    throw new EsjError('the value at ' + path + ' is not a date of section 6.5',
      FindingCode.L2_DATE, path);
  }
  return value.value;
}

/** Reads a time of day with its offset from UTC (section 6.5). */
export function timeOf(value: SemanticValue, path: string): string {
  if (!isTime(value.value)) {
    throw new EsjError('the value at ' + path + ' is not a time of section 6.5',
      FindingCode.L2_TIME, path);
  }
  return value.value;
}

/** Reads an identifier with the components it carries. */
export function identifierOf(value: SemanticValue): Identifier {
  return {
    value: value.value,
    ...(value.scheme === undefined ? {} : { scheme: value.scheme }),
    ...(value.schemeVersion === undefined ? {} : { schemeVersion: value.schemeVersion }),
  };
}

/** Reads a binary object, whose two components the standard makes mandatory. */
export function binaryOf(value: SemanticValue, path: string): BinaryObject {
  if (!isBase64(value.value)) {
    throw new EsjError('the value at ' + path + ' is not canonical base64 of section 6.7',
      FindingCode.L2_BASE64, path);
  }
  if (value.mimeCode === undefined || value.filename === undefined) {
    throw new EsjError('the binary object at ' + path + ' carries no media type or no file name',
      FindingCode.L2_COMPONENT_MISSING, path);
  }
  return { value: value.value, mimeCode: value.mimeCode, filename: value.filename };
}
