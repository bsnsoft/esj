import type { FindingCode } from './codes.ts';

/**
 * What a reader raises where it cannot construct a document at all.
 *
 * A reader and a validator answer differently and both are conformant (specification,
 * section 9.5): a validator reports findings, a reader may end with an exception carrying
 * the finding it stopped at. This binding does both — {@link readDocument} returns the
 * findings, {@link readDocumentOrThrow} raises this — and the code is the same either way,
 * so a caller that must handle both catches this and inspects {@link EsjError.code}.
 */
export class EsjError extends Error {
  /** The finding code the reader stopped at, where one applies. */
  readonly code?: FindingCode;

  /** The semantic path the finding is about, where the finding names one. */
  readonly path?: string;

  constructor(message: string, code?: FindingCode, path?: string) {
    super(message);
    this.name = 'EsjError';
    this.code = code;
    this.path = path;
  }
}
