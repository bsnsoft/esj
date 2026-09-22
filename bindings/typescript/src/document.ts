import type { JsonObject } from './json/tree.ts';
import { comparePathText } from './paths.ts';

/**
 * The document an ESJ reader hands back: the envelope, and the values by their semantic
 * paths (specification, section 4).
 *
 * A document carries no registry and knows no business term. It is the content of the file
 * as the bytes give it, with the one transformation the specification allows a reader —
 * line ending normalization (section 6.8) — already applied, so that canonicalizing it
 * reproduces the canonical bytes whatever layout it arrived in.
 */

/** The fixed value of the `format` member (section 4.3). */
export const FORMAT = 'EN16931-Semantic-JSON';

/** The fixed value of the `version` member (section 4.3). */
export const VERSION = '0.1';

/** The edition the reference registry of this library describes (section 4.4). */
export const EDITION_2017 = 'EN16931-1:2017+A1:2019/AC:2020';

/** The five members a value object may carry, in canonical order (sections 6.1 and 7.3). */
export const VALUE_MEMBERS = ['value', 'scheme', 'schemeVersion', 'mimeCode', 'filename'] as const;

/** One of those five members. */
export type ValueMember = (typeof VALUE_MEMBERS)[number];

/**
 * One value of `values`: its content and the supplementary components it carries.
 *
 * A value written as a JSON string has content and nothing else; one written as an object
 * carries at least one component. Which shape a value has is decided by its content and not
 * by the writer (section 6.1), so a reader does not record the shape and a canonicalizer
 * writes the string form exactly where no component is present.
 */
export interface SemanticValue {
  /** The content, with line endings normalized. */
  readonly value: string;
  /** The identification scheme, where the value carries one (section 6.6). */
  readonly scheme?: string;
  /** The version of that scheme, where the value carries one (section 6.6). */
  readonly schemeVersion?: string;
  /** The media type of a binary object (section 6.7). */
  readonly mimeCode?: string;
  /** The file name of a binary object (section 6.7). */
  readonly filename?: string;
}

/** Tells whether a value carries any supplementary component, which decides its shape. */
export function hasComponent(value: SemanticValue): boolean {
  return value.scheme !== undefined || value.schemeVersion !== undefined
    || value.mimeCode !== undefined || value.filename !== undefined;
}

/** Tells whether a value carries a binary object's components (section 6.7). */
export function isBinary(value: SemanticValue): boolean {
  return value.mimeCode !== undefined || value.filename !== undefined;
}

/** Where a document was extracted from (section 4.7). */
export interface Source {
  /** The syntax the content was extracted from, for example `UBL`, `CII` or `ESJ`. */
  readonly syntax?: string;
  /** The SHA-256 digest of the source bytes, as 64 lowercase hexadecimal digits. */
  readonly sha256?: string;
}

/** An ESJ document. */
export interface SemanticDocument {
  /** The `format` member, which is always {@link FORMAT}. */
  readonly format: string;
  /** The `version` member, which is always {@link VERSION}. */
  readonly version: string;
  /** The edition the paths of this document are addresses in (section 4.4). */
  readonly semanticModel: string;
  /** The values, by path, in canonical path order (section 7.4). */
  readonly values: ReadonlyMap<string, SemanticValue>;
  /** The `extensions` subtree as the document wrote it, or `undefined` (section 4.6). */
  readonly extensions?: JsonObject;
  /** The provenance the document records, or `undefined` (section 4.7). */
  readonly source?: Source;
}

/**
 * Builds a document from its parts, putting the values in canonical path order.
 *
 * The order is not part of the meaning of a document (section 4.2, rule 6) and every
 * consumer that cares wants it: it is the order the canonical form writes, the order a
 * report reads well in, and the order that makes the values of one business group instance
 * one range of the map rather than a scan of the document.
 *
 * @param parts the envelope and the values
 * @return the document
 */
export function documentOf(parts: {
  format?: string;
  version?: string;
  semanticModel: string;
  values: Iterable<readonly [string, SemanticValue]>;
  extensions?: JsonObject;
  source?: Source;
}): SemanticDocument {
  const values = new Map<string, SemanticValue>(
    [...parts.values].sort((left, right) => comparePathText(left[0], right[0])));
  const document: SemanticDocument = {
    format: parts.format ?? FORMAT,
    version: parts.version ?? VERSION,
    semanticModel: parts.semanticModel,
    values,
    ...(parts.extensions === undefined ? {} : { extensions: parts.extensions }),
    ...(parts.source === undefined ? {} : { source: parts.source }),
  };
  return document;
}
