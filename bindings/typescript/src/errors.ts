import type { FindingCode } from './codes.ts';

/**
 * What a reader raises where it cannot construct a document at all.
 *
 * A reader and a validator answer differently and both are conformant (specification,
 * section 9.5): a validator reports findings, a reader may end with an exception carrying
 * the finding it stopped at. This binding does both — {@link readDocument} returns the
 * findings, {@link readDocumentOrThrow} raises this — and the code, the path and the subject
 * are the same either way, so a caller that must handle both catches this and inspects
 * {@link EsjError.code}, {@link EsjError.path} and {@link EsjError.subject} as it would a
 * finding.
 */
export class EsjError extends Error {
  /** The finding code the reader stopped at, where one applies. */
  readonly code?: FindingCode;

  /**
   * The semantic path the finding is about, or the empty string where it names none
   * (section 9.5).
   */
  readonly path: string;

  /**
   * What the finding is about as a member access, or the empty string where it names none
   * (section 9.5).
   */
  readonly subject: string;

  constructor(message: string, code?: FindingCode, path = '', subject = '') {
    super(message);
    this.name = 'EsjError';
    this.code = code;
    this.path = path;
    this.subject = subject;
  }
}
