import type { FindingCode, Layer, NotEvaluatedReason, Severity, ValidationStatus } from './codes.ts';
import { NOT_EVALUATED_CODES, layerOf, severityOf } from './codes.ts';
import { escapeForMessage } from './grammars.ts';

/**
 * A finding, and the result that carries a set of them (specification, section 9.5).
 *
 * A caller acts on `code` and on the place the finding names, and the message is for a
 * person: it may quote the document, and a fragment quoted in it is escaped, so that a
 * message is safe to write to a log line as it stands.
 */

/** One finding. */
export interface Finding {
  /** The semantic path the finding is about, or the empty string. */
  readonly path: string;
  /** What the finding is about where `path` cannot name it, or the empty string. */
  readonly subject: string;
  /** The kind of problem (section 9.6). */
  readonly code: FindingCode;
  /** How much the finding weighs at the layer it belongs to. */
  readonly severity: Severity;
  /** English text, with any quoted fragment escaped. */
  readonly message: string;
}

/** Builds a finding, filling in the severity the specification gives the code. */
export function finding(
  code: FindingCode, message: string, place?: { path?: string; subject?: string },
): Finding {
  return {
    path: place?.path ?? '',
    subject: place?.subject ?? '',
    code,
    severity: severityOf(code),
    message,
  };
}

/** Returns the member access a finding names a member of `values` by (section 9.5). */
export function valuesSubject(name: string): string {
  return 'values["' + escapeForMessage(name) + '"]';
}

/** Returns the member access a finding names a member of `extensions` by (section 9.5). */
export function extensionsSubject(name: string): string {
  return 'extensions["' + escapeForMessage(name) + '"]';
}

/** What a run says about a layer it did not evaluate. */
export interface NotEvaluated {
  /** The layer. */
  readonly layer: Layer;
  /** Why it was not evaluated; the vocabulary is closed (section 9.5). */
  readonly reason: NotEvaluatedReason;
}

/** What a validator returns (section 9.5). */
export interface ValidationResult {
  /** The status, decided by the rules of section 9.5. */
  readonly status: ValidationStatus;
  /** The findings, in the order the validator produced them. */
  readonly findings: readonly Finding[];
  /** The layers that were evaluated. */
  readonly evaluated: readonly Layer[];
  /** The layers that were not, each with the reason. */
  readonly notEvaluated: readonly NotEvaluated[];
}

/**
 * Decides the status of a result from its findings and from what it evaluated
 * (section 9.5).
 *
 * The status is not a function of the severities alone: `ESJ-L1-LIMIT` is an error and
 * yields `INDETERMINATE`, because it says that this implementation stopped and not that the
 * document is wrong, and a result that names a layer as not evaluated is never `VALID`
 * however quiet it is.
 *
 * @param findings the findings
 * @param notEvaluated the layers that were not evaluated
 * @return the status
 */
export function statusOf(
  findings: readonly Finding[], notEvaluated: readonly NotEvaluated[],
): ValidationStatus {
  for (const entry of findings) {
    if (entry.severity === 'error' && !NOT_EVALUATED_CODES.includes(entry.code)) {
      return 'INVALID';
    }
  }
  if (notEvaluated.length > 0) {
    return 'INDETERMINATE';
  }
  for (const entry of findings) {
    if (NOT_EVALUATED_CODES.includes(entry.code)) {
      return 'INDETERMINATE';
    }
  }
  return 'VALID';
}

/** Returns the findings of one layer. */
export function findingsOf(findings: readonly Finding[], layer: Layer): Finding[] {
  return findings.filter((entry) => layerOf(entry.code) === layer);
}
