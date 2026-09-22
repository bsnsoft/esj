import { isPath } from './grammars.ts';

/**
 * A semantic path: the absolute address of one business term occurrence (specification,
 * section 5), and the order the canonical form puts two of them in (section 7.4).
 *
 * A path is held as its segments rather than as a string, because every question about it —
 * which term it ends at, which group instance it lies in, which of two comes first — is a
 * question about the segments. Two paths are equal exactly when their strings are, code
 * point by code point (section 5.5).
 */

/** One segment of a path: a term identifier, or an occurrence index. */
export type Segment = TermSegment | IndexSegment;

/** A term segment: `BT-131`, `BG-25`, `BT-DEX-001`. */
export interface TermSegment {
  readonly kind: 'BT' | 'BG';
  readonly id: string;
  /** The extension namespace, or the empty string for a core term. */
  readonly namespace: string;
  /** The number as the path spells it, leading zeros included. */
  readonly digits: string;
}

/** An index segment: the zero-based ordinal of one occurrence. */
export interface IndexSegment {
  readonly kind: 'index';
  readonly digits: string;
}

const TERM = /^(BT|BG)-(?:([A-Z][A-Z0-9]*)-)?([0-9]+)$/;

function termSegment(token: string): TermSegment | null {
  const match = TERM.exec(token);
  if (match === null) {
    return null;
  }
  return { kind: match[1] as 'BT' | 'BG', id: token, namespace: match[2] ?? '', digits: match[3] };
}

/**
 * Splits a member name of `values` into its segments.
 *
 * The caller has already decided that the name satisfies the path grammar; this does the
 * same work again rather than trusting it, so that a path built by hand cannot enter the
 * model in a shape the canonical order is not total over.
 */
export function parsePath(text: string): Segment[] {
  if (!isPath(text)) {
    throw new Error(`${text} is not a semantic path of the specification, section 5.1`);
  }
  return splitSegments(text);
}

/**
 * Splits a path or a group path into its segments without asking the value grammar about
 * it, which is what a group path needs: a group path ends at a business group and the
 * grammar of section 5.1 ends at a business term.
 */
export function splitSegments(text: string): Segment[] {
  const segments: Segment[] = [];
  for (const token of text.slice(1).split('/')) {
    const term = termSegment(token);
    segments.push(term ?? { kind: 'index', digits: token });
  }
  return segments;
}

/** Returns the term identifiers of a path, outermost first. */
export function termIds(segments: readonly Segment[]): string[] {
  const ids: string[] = [];
  for (const segment of segments) {
    if (segment.kind !== 'index') {
      ids.push(segment.id);
    }
  }
  return ids;
}

/** Returns the identifier of the business term a path ends at. */
export function lastTermId(segments: readonly Segment[]): string {
  const ids = termIds(segments);
  return ids[ids.length - 1];
}

/** Writes segments back out as a path. */
export function pathText(segments: readonly Segment[]): string {
  let text = '';
  for (const segment of segments) {
    text += '/' + (segment.kind === 'index' ? segment.digits : segment.id);
  }
  return text;
}

/**
 * Returns the group instance paths a path lies in, outermost first: every prefix that ends
 * at a business group, with that group's index where it carries one.
 */
export function groupPaths(segments: readonly Segment[]): string[] {
  const groups: string[] = [];
  let text = '';
  for (let i = 0; i < segments.length; i++) {
    const segment = segments[i];
    text += '/' + (segment.kind === 'index' ? segment.digits : segment.id);
    if (segment.kind === 'BG') {
      const next = segments[i + 1];
      if (next !== undefined && next.kind === 'index') {
        text += '/' + next.digits;
        i++;
      }
      groups.push(text);
    }
  }
  return groups;
}

/**
 * Compares two digit strings numerically, exactly and without arithmetic (section 7.4).
 *
 * Converting a digit string to a machine number before comparing is not conformant: the
 * grammar puts no bound on the number of digits, and two numbers that exceeded the
 * receiving type would compare equal.
 */
export function compareDigits(left: string, right: string): number {
  const a = stripLeadingZeros(left);
  const b = stripLeadingZeros(right);
  if (a.length !== b.length) {
    return a.length < b.length ? -1 : 1;
  }
  return a < b ? -1 : a > b ? 1 : 0;
}

function stripLeadingZeros(digits: string): string {
  let i = 0;
  while (i < digits.length - 1 && digits.charAt(i) === '0') {
    i++;
  }
  return digits.slice(i);
}

function compareCodePoints(left: string, right: string): number {
  return left < right ? -1 : left > right ? 1 : 0;
}

function compareSegments(left: Segment, right: Segment): number {
  if (left.kind === 'index' && right.kind === 'index') {
    return compareDigits(left.digits, right.digits);
  }
  if (left.kind === 'index') {
    return 1;
  }
  if (right.kind === 'index') {
    return -1;
  }
  if (left.kind !== right.kind) {
    return left.kind === 'BT' ? -1 : 1;
  }
  if (left.namespace !== right.namespace) {
    if (left.namespace === '') {
      return -1;
    }
    if (right.namespace === '') {
      return 1;
    }
    return compareCodePoints(left.namespace, right.namespace);
  }
  const byNumber = compareDigits(left.digits, right.digits);
  if (byNumber !== 0) {
    return byNumber;
  }
  return compareCodePoints(left.digits, right.digits);
}

/**
 * The canonical path order of section 7.4: segment by segment from the left, the shorter
 * path first where one is a prefix of the other. It is total over every well-formed
 * document and needs no registry.
 */
export function comparePaths(left: readonly Segment[], right: readonly Segment[]): number {
  const shared = Math.min(left.length, right.length);
  for (let i = 0; i < shared; i++) {
    const order = compareSegments(left[i], right[i]);
    if (order !== 0) {
      return order;
    }
  }
  return left.length - right.length;
}

/** Compares two paths written as text, in canonical path order. */
export function comparePathText(left: string, right: string): number {
  return comparePaths(splitSegments(left), splitSegments(right));
}
