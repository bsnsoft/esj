/**
 * The finding vocabulary of the specification, section 9.6, and the three states a
 * validation result has (section 9.5).
 *
 * Every code ESJ defines begins with `ESJ-`. A validator that reports problems of its own
 * uses a different prefix, so that its codes can never be confused with these.
 */

/** A finding code of the specification, section 9.6. */
export const FindingCode = {
  L1_ENCODING: 'ESJ-L1-ENCODING',
  L1_JSON: 'ESJ-L1-JSON',
  L1_DUPLICATE_MEMBER: 'ESJ-L1-DUPLICATE-MEMBER',
  L1_ENVELOPE_MEMBER: 'ESJ-L1-ENVELOPE-MEMBER',
  L1_ENVELOPE_VALUE: 'ESJ-L1-ENVELOPE-VALUE',
  L1_OWNER_TOKEN: 'ESJ-L1-OWNER-TOKEN',
  L1_PATH_SYNTAX: 'ESJ-L1-PATH-SYNTAX',
  L1_JSON_TYPE: 'ESJ-L1-JSON-TYPE',
  L1_EXT_NUMBER: 'ESJ-L1-EXT-NUMBER',
  L1_VALUE_SHAPE: 'ESJ-L1-VALUE-SHAPE',
  L1_VALUE_MEMBER: 'ESJ-L1-VALUE-MEMBER',
  L1_EMPTY_STRING: 'ESJ-L1-EMPTY-STRING',
  L1_SURROGATE: 'ESJ-L1-SURROGATE',
  L1_LIMIT: 'ESJ-L1-LIMIT',
  L2_UNKNOWN_TERM: 'ESJ-L2-UNKNOWN-TERM',
  L2_PARENT_CHAIN: 'ESJ-L2-PARENT-CHAIN',
  L2_DECIMAL: 'ESJ-L2-DECIMAL',
  L2_DATE: 'ESJ-L2-DATE',
  L2_TIME: 'ESJ-L2-TIME',
  L2_BASE64: 'ESJ-L2-BASE64',
  L2_COMPONENT_NOT_ALLOWED: 'ESJ-L2-COMPONENT-NOT-ALLOWED',
  L2_COMPONENT_MISSING: 'ESJ-L2-COMPONENT-MISSING',
  L2_INDEX_REQUIRED: 'ESJ-L2-INDEX-REQUIRED',
  L2_INDEX_FORBIDDEN: 'ESJ-L2-INDEX-FORBIDDEN',
  L2_NOT_CHECKED: 'ESJ-L2-NOT-CHECKED',
  L2_EDITION_UNKNOWN: 'ESJ-L2-EDITION-UNKNOWN',
  L3_INDEX_GAP: 'ESJ-L3-INDEX-GAP',
  L3_MISSING_TERM: 'ESJ-L3-MISSING-TERM',
  L3_MISSING_GROUP: 'ESJ-L3-MISSING-GROUP',
  L3_MAX_CARDINALITY: 'ESJ-L3-MAX-CARDINALITY',
} as const;

/** One of the codes of {@link FindingCode}. */
export type FindingCode = (typeof FindingCode)[keyof typeof FindingCode];

/** How much one finding weighs (specification, section 9.5). */
export type Severity = 'error' | 'warning' | 'info';

/** One of the three validation layers of the specification, section 9. */
export type Layer = 'L1' | 'L2' | 'L3';

/** Why a layer was not evaluated; the vocabulary is closed (specification, section 9.5). */
export type NotEvaluatedReason =
  | 'LIMIT'
  | 'PRECEDING-LAYER-FAILED'
  | 'EDITION-UNKNOWN'
  | 'NOT-REQUESTED';

/** The three states of a validation result (specification, section 9.5). */
export type ValidationStatus = 'VALID' | 'INVALID' | 'INDETERMINATE';

const SEVERITY: Record<string, Severity> = {
  [FindingCode.L2_NOT_CHECKED]: 'info',
  [FindingCode.L2_EDITION_UNKNOWN]: 'info',
};

const LAYER_OF: Record<string, Layer> = {};
for (const code of Object.values(FindingCode)) {
  LAYER_OF[code] = code.slice(4, 6) as Layer;
}

/** Returns the severity the specification gives a code where it is reported at its own layer. */
export function severityOf(code: string): Severity {
  return SEVERITY[code] ?? 'error';
}

/** Returns the layer a code belongs to. */
export function layerOf(code: string): Layer {
  const layer = LAYER_OF[code];
  if (layer === undefined) {
    throw new Error(`${code} is not a finding code of this specification`);
  }
  return layer;
}

/**
 * The three codes that record something the run did not evaluate. A result that carries one
 * of them is never `VALID` (specification, section 9.5).
 */
export const NOT_EVALUATED_CODES: readonly string[] = [
  FindingCode.L1_LIMIT,
  FindingCode.L2_NOT_CHECKED,
  FindingCode.L2_EDITION_UNKNOWN,
];
