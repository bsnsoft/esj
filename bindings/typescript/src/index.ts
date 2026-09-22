/**
 * EN16931 Semantic JSON — the TypeScript implementation.
 *
 * The public surface is four things: read a document, write it in the canonical or the pretty
 * form, take its digests, and validate it against the layers of the specification and against
 * a rule pack. Everything here is free of any environment — a document, a registry and a rule
 * pack are values a caller passes in — and `./node.js` is the entry point that reads the
 * registries and the pack this package carries from the files beside it.
 */

export { FindingCode, NOT_EVALUATED_CODES, layerOf, severityOf } from './codes.ts';
export type { Layer, NotEvaluatedReason, Severity, ValidationStatus } from './codes.ts';
export { EsjError } from './errors.ts';
export { DEFAULT_LIMITS, limitsOf } from './limits.ts';
export type { Limits } from './limits.ts';

export {
  EDITION_2017, FORMAT, VALUE_MEMBERS, VERSION, documentOf, hasComponent, isBinary,
} from './document.ts';
export type { SemanticDocument, SemanticValue, Source, ValueMember } from './document.ts';

export { readDocument, readDocumentOrThrow } from './reader.ts';
export type { ReadOptions, ReadResult } from './reader.ts';

export {
  canonicalBytes, canonicalNumber, canonicalSemanticContent, canonicalize, compareCodePoints,
  pretty,
} from './canonical.ts';
export { digestOfBytes, documentDigest, semanticDigest } from './digest.ts';

export { finding, findingsOf, statusOf } from './finding.ts';
export type { Finding, NotEvaluated, ValidationResult } from './finding.ts';

export { Registry, isRepeatable, registryOf, semanticModelOf } from './registry.ts';
export type {
  ComponentRole, MaxCardinality, RegistryComponent, RegistryFile, RegistryImport, RegistryTerm,
} from './registry.ts';
export { Structure } from './structure.ts';
export type { Child } from './structure.ts';

export { validate, validateSemanticDocument } from './validate.ts';
export type { ValidateOptions } from './validate.ts';

export { Decimal, DIVISION_SCALE } from './decimal.ts';

export {
  comparePathText, comparePaths, groupPaths, lastTermId, parsePath, pathText, splitSegments,
  termIds,
} from './paths.ts';
export type { IndexSegment, Segment, TermSegment } from './paths.ts';

export {
  MAX_DECIMAL_LENGTH, MAX_OWNER_TOKEN_LENGTH, base64Violation, codePointCount, dateViolation,
  decimalViolation, escapeForMessage, forMessage, hasLoneSurrogate, isBase64, isDate, isDecimal,
  isEdition, isOwnerToken, isPath, isSha256, isTime, normalizeLineEndings, timeViolation,
  utf8Length,
} from './grammars.ts';

export { RuleContext, RuleEngine, Undecided, compile } from './rules/engine.ts';
export type { NativeRule, RuleFinding, RuleValue } from './rules/engine.ts';
export { CodeList, RulePackError, ruleIdOfDeclared } from './rules/pack.ts';
export type {
  CodeListEntry, CodeListFile, Expression, RuleDefinition, RuleFile, RulePackFile, RuleSeverity,
  RuleWarning,
} from './rules/pack.ts';
export { en16931NativeRules } from './rules/native.ts';

export {
  TypedIndex, binaryOf, dateOf, decimalOf, group, groups, identifierOf, optional, repeated,
  required, requiredGroup, textOf, timeOf,
} from './typed/runtime.ts';
export type { BinaryObject, Identifier, Read } from './typed/runtime.ts';
