/**
 * The reader limits of the specification, section 12.2.
 *
 * They are a policy of the reader and not a property of the document: exceeding one is
 * reported as `ESJ-L1-LIMIT` and says that this reader, as configured, declines to process
 * the document. The defaults are the ones the specification gives, so that two
 * implementations that adopt them refuse the same documents.
 *
 * Every length is counted in UTF-8 bytes, never in characters or UTF-16 code units.
 */
export interface Limits {
  /** Bytes of the encoded document. */
  maxDocumentBytes: number;
  /** Members of `values`. */
  maxValues: number;
  /** Members of one value object. */
  maxValueMembers: number;
  /** Segments per path, index segments counted. */
  maxPathSegments: number;
  /** Bytes of the UTF-8 encoding of a member name of `values`. */
  maxPathBytes: number;
  /** Bytes of a string inside `values` that carries no binary component. */
  maxStringBytes: number;
  /** Bytes of the content of a value object that carries `mimeCode` or `filename`. */
  maxBinaryValueBytes: number;
  /** Bytes of the whole binary content of a document, after base64 decoding. */
  maxTotalBinaryBytes: number;
  /** Levels of object or array inside `extensions`, counted from an owner token's value. */
  maxExtensionDepth: number;
  /** Nodes inside `extensions`, counting every scalar and every container. */
  maxExtensionNodes: number;
}

const MIB = 1024 * 1024;

/** The defaults of the specification, section 12.2. */
export const DEFAULT_LIMITS: Readonly<Limits> = Object.freeze({
  maxDocumentBytes: 64 * MIB,
  maxValues: 100_000,
  maxValueMembers: 16,
  maxPathSegments: 16,
  maxPathBytes: 256,
  maxStringBytes: MIB,
  maxBinaryValueBytes: 32 * MIB,
  maxTotalBinaryBytes: 48 * MIB,
  maxExtensionDepth: 32,
  maxExtensionNodes: 100_000,
});

/**
 * Returns the limits a reader runs with: the defaults, with whatever the caller overrode.
 *
 * A bound that is not a positive number is a defect in the call and not in any document, so
 * it is refused when it is given rather than when a document arrives (specification,
 * section 12.2).
 */
export function limitsOf(overrides?: Partial<Limits>): Limits {
  const limits: Limits = { ...DEFAULT_LIMITS, ...(overrides ?? {}) };
  for (const [name, value] of Object.entries(limits)) {
    if (!Number.isFinite(value) || value <= 0) {
      throw new RangeError(`${name} is a positive number, not ${value}`);
    }
  }
  return limits;
}
