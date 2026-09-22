import { FindingCode, type Layer } from './codes.ts';
import type { SemanticDocument, SemanticValue } from './document.ts';
import { VALUE_MEMBERS } from './document.ts';
import type { Finding, NotEvaluated, ValidationResult } from './finding.ts';
import { finding, statusOf } from './finding.ts';
import {
  base64Violation, dateViolation, decimalViolation, forMessage, hasLoneSurrogate,
  timeViolation,
} from './grammars.ts';
import type { ReadOptions } from './reader.ts';
import { readDocument } from './reader.ts';
import type { Registry, RegistryTerm } from './registry.ts';
import type { Child } from './structure.ts';
import { Structure } from './structure.ts';
import { splitSegments } from './paths.ts';

/**
 * The validator: layers L1, L2 and L3 of the specification, section 9.
 *
 * L1 is the reader's (`reader.ts`); this module adds the two layers that need a registry.
 * L2 measures one path and one value against the term it sits at, and L3 looks at the
 * document as a whole and counts occurrences per parent instance. A path that failed L2 is
 * not placed in the structure, so the two layers never report one defect twice.
 */

/** The three layers, in the order they are evaluated. */
const ALL_LAYERS: readonly Layer[] = ['L1', 'L2', 'L3'];

/** The datatypes whose content is a decimal (section 6.2). */
const DECIMAL_TYPES = ['Amount', 'UnitPriceAmount', 'Quantity', 'Percentage'];

/** How a validator was configured. */
export interface ValidateOptions extends ReadOptions {
  /**
   * The registries this build carries. The validator uses the one whose edition matches the
   * document's `semanticModel` and every extension registry that imports it; where it holds
   * none for that edition it reports `ESJ-L2-EDITION-UNKNOWN` and evaluates neither model
   * layer (section 9.2).
   */
  readonly registries?: readonly Registry[];
  /** The layers the caller asked for; the default is all three. */
  readonly layers?: readonly Layer[];
}

/** One step of a path: a term or group, with the occurrence index it carries. */
interface Step {
  readonly id: string;
  readonly kind: 'BT' | 'BG';
  readonly index?: number;
  /** The path up to and including this step, with its index. */
  readonly at: string;
}

/**
 * Reads and validates a document.
 *
 * @param input the document, as the bytes of its UTF-8 encoding or as decoded text
 * @param options the registries, the limits and the layers asked for
 * @return the result, with its status, its findings and what it evaluated
 */
export function validate(
  input: Uint8Array | string, options: ValidateOptions = {},
): ValidationResult {
  const layers = options.layers ?? ALL_LAYERS;
  if (!layers.includes('L1')) {
    const read = readDocument(input, options);
    if (read.document !== undefined) {
      return validateModel(read.document, options, layers, [], []);
    }
    // A layer the caller did not ask for keeps NOT-REQUESTED whatever else happened
    // (section 9.5); the two that were asked for never ran, because there is no document.
    return result([], [], [
      notEvaluated('L1', 'NOT-REQUESTED'),
      ...(['L2', 'L3'] as const).map((layer) => notEvaluated(layer,
        layers.includes(layer) ? 'PRECEDING-LAYER-FAILED' : 'NOT-REQUESTED')),
    ]);
  }
  const read = readDocument(input, options);
  const l1 = [...read.findings];
  if (read.document === undefined || l1.some((entry) => entry.severity === 'error')) {
    const reason = l1.some((entry) => entry.code === FindingCode.L1_LIMIT)
      ? 'LIMIT' : 'PRECEDING-LAYER-FAILED';
    const skipped: NotEvaluated[] = [];
    for (const layer of ['L2', 'L3'] as const) {
      skipped.push(layers.includes(layer)
        ? notEvaluated(layer, reason)
        : notEvaluated(layer, 'NOT-REQUESTED'));
    }
    return result(l1, ['L1'], skipped);
  }
  return validateModel(read.document, options, layers, l1, ['L1']);
}

/**
 * Validates a document a caller already holds, which is a document that was never read from
 * bytes here.
 *
 * L1 cannot be evaluated of such an input, so the result names it as not evaluated and is
 * never `VALID`; only a tool that composes a verdict over such an input may answer `VALID`
 * on the strength of the two model layers (section 9.5).
 *
 * @param document the document
 * @param options the registries and the layers asked for
 * @return the result
 */
export function validateSemanticDocument(
  document: SemanticDocument, options: ValidateOptions = {},
): ValidationResult {
  const layers = (options.layers ?? ALL_LAYERS).filter((layer) => layer !== 'L1');
  return validateModel(document, options, layers, [], []);
}

function validateModel(
  document: SemanticDocument,
  options: ValidateOptions,
  layers: readonly Layer[],
  before: Finding[],
  evaluated: Layer[],
): ValidationResult {
  const findings = [...before];
  const skipped: NotEvaluated[] = [];
  if (!layers.includes('L1') && !evaluated.includes('L1')) {
    skipped.push(notEvaluated('L1', 'NOT-REQUESTED'));
  }
  const structure = structureFor(document, options.registries ?? []);
  if (structure === undefined) {
    findings.push(finding(FindingCode.L2_EDITION_UNKNOWN,
      'no registry for the edition ' + forMessage(document.semanticModel)
      + ' is available, so neither the model layer nor the cardinality layer was evaluated.'));
    for (const layer of ['L2', 'L3'] as const) {
      skipped.push(notEvaluated(layer,
        layers.includes(layer) ? 'EDITION-UNKNOWN' : 'NOT-REQUESTED'));
    }
    return result(findings, evaluated, skipped);
  }
  const placed: Array<{ path: string; steps: Step[] }> = [];
  for (const [path, value] of document.values) {
    const steps = stepsOf(path);
    if (checkPath(findings, structure, path, steps) && checkValue(findings, structure, path, value)) {
      placed.push({ path, steps });
    }
  }
  const model: Layer[] = [...evaluated];
  if (layers.includes('L2')) {
    model.push('L2');
  } else {
    skipped.push(notEvaluated('L2', 'NOT-REQUESTED'));
  }
  if (layers.includes('L3')) {
    checkCardinality(findings, structure, placed);
    model.push('L3');
  } else {
    skipped.push(notEvaluated('L3', 'NOT-REQUESTED'));
  }
  const reported = layers.includes('L2')
    ? findings
    : findings.filter((entry) => !entry.code.startsWith('ESJ-L2-'));
  return result(reported, model, skipped);
}

/**
 * Returns the structure a document is measured against: the registry whose edition matches
 * its `semanticModel`, and every extension registry that imports that edition.
 */
function structureFor(
  document: SemanticDocument, registries: readonly Registry[],
): Structure | undefined {
  const core = registries.find(
    (registry) => !registry.isExtension() && registry.semanticModel === document.semanticModel);
  if (core === undefined) {
    return undefined;
  }
  const extensions = registries.filter((registry) => registry.isExtension()
    && registry.imports.some((imported) => imported.edition === core.edition));
  return new Structure(core, extensions);
}

/** Splits a path into its steps, each with the occurrence index it carries. */
function stepsOf(path: string): Step[] {
  const steps: Step[] = [];
  let at = '';
  const segments = splitSegments(path);
  for (let i = 0; i < segments.length; i++) {
    const segment = segments[i];
    if (segment.kind === 'index') {
      continue;
    }
    at += '/' + segment.id;
    const next = segments[i + 1];
    if (next !== undefined && next.kind === 'index') {
      at += '/' + next.digits;
      steps.push({ id: segment.id, kind: segment.kind, index: Number(next.digits), at });
    } else {
      steps.push({ id: segment.id, kind: segment.kind, at });
    }
  }
  return steps;
}

/** Tells whether an identifier carries an extension namespace (section 5.6). */
function isExtensionId(id: string): boolean {
  return /^(?:BT|BG)-[A-Z][A-Z0-9]*-[0-9]+$/.test(id);
}

/**
 * Checks a path against the registry: that its terms exist, that its index segments stand
 * where the declared cardinalities put them, and that its group chain is one the registries
 * record (sections 5.2, 5.3 and 5.6).
 *
 * @return whether the path is placed in the structure, which is what L3 counts
 */
function checkPath(
  findings: Finding[], structure: Structure, path: string, steps: Step[],
): boolean {
  for (const step of steps) {
    if (structure.term(step.id) !== undefined) {
      continue;
    }
    if (isExtensionId(step.id)) {
      findings.push(finding(FindingCode.L2_NOT_CHECKED,
        step.id + ' belongs to an extension whose registry is not loaded, so nothing about'
        + ' this path was checked.', { path }));
      return false;
    }
    findings.push(finding(FindingCode.L2_UNKNOWN_TERM,
      step.id + ' is not a term of the registry of ' + structure.core.edition + '.',
      { path, subject: step.id }));
    return false;
  }
  let sound = true;
  for (const step of steps) {
    const repeatable = structure.repeatable(step.id);
    if (repeatable && step.index === undefined) {
      findings.push(finding(FindingCode.L2_INDEX_REQUIRED,
        step.id + ' is declared repeatable, so its segment carries an occurrence index.',
        { path, subject: step.id }));
      sound = false;
    } else if (!repeatable && step.index !== undefined) {
      findings.push(finding(FindingCode.L2_INDEX_FORBIDDEN,
        step.id + ' occurs at most once, so its segment carries no occurrence index.',
        { path, subject: step.id }));
      sound = false;
    }
  }
  if (!sound) {
    return false;
  }
  const last = steps[steps.length - 1];
  const groups = steps.slice(0, steps.length - 1).map((step) => step.id);
  if (!structure.isChain(last.id, groups)) {
    findings.push(finding(FindingCode.L2_PARENT_CHAIN,
      'the group segments of this path are not a chain the loaded registries record for '
      + last.id + '.', { path, subject: last.id }));
    return false;
  }
  return true;
}

/**
 * Checks the content of a value against the semantic data type of its term, and the
 * supplementary components against what the registry lists for it (sections 6.2, 6.6 and
 * 6.7).
 *
 * A string carrying a lone surrogate is not measured against a grammar: it has no UTF-8
 * encoding, so it has no content a grammar could read, and L1 has already reported it
 * (section 9.6).
 */
function checkValue(
  findings: Finding[], structure: Structure, path: string, value: SemanticValue,
): boolean {
  const steps = stepsOf(path);
  const term = structure.term(steps[steps.length - 1].id);
  if (term === undefined) {
    return true;
  }
  checkContent(findings, term, path, value);
  checkComponents(findings, term, path, value);
  return true;
}

function checkContent(
  findings: Finding[], term: RegistryTerm, path: string, value: SemanticValue,
): void {
  if (hasLoneSurrogate(value.value)) {
    return;
  }
  const content = value.value;
  const violation = violationOf(term.datatype, content);
  if (violation === undefined) {
    return;
  }
  findings.push(finding(violation.code,
    term.id + ' carries ' + forMessage(content) + ', which ' + violation.why + '.', { path }));
}

/**
 * Returns what is wrong with the content of a value at a term of that semantic data type,
 * and the code the specification gives it, or `undefined` where nothing is.
 *
 * The grammar says what it refused rather than only that it refused: a decimal with trailing
 * fraction zeros, a date that names a day the calendar does not have and a time without its
 * offset are three different mistakes, and a sender told which one it made can mend it.
 */
function violationOf(
  datatype: string | null, content: string,
): { code: FindingCode; why: string } | undefined {
  if (datatype === null) {
    return undefined;
  }
  if (DECIMAL_TYPES.includes(datatype)) {
    const why = decimalViolation(content);
    return why === null ? undefined : { code: FindingCode.L2_DECIMAL, why };
  }
  if (datatype === 'Date') {
    const why = dateViolation(content);
    return why === null ? undefined : { code: FindingCode.L2_DATE, why };
  }
  if (datatype === 'Time') {
    const why = timeViolation(content);
    return why === null ? undefined : { code: FindingCode.L2_TIME, why };
  }
  if (datatype === 'BinaryObject') {
    const why = base64Violation(content);
    return why === null ? undefined : { code: FindingCode.L2_BASE64, why };
  }
  return undefined;
}

function checkComponents(
  findings: Finding[], term: RegistryTerm, path: string, value: SemanticValue,
): void {
  for (const member of VALUE_MEMBERS) {
    if (member === 'value' || value[member] === undefined) {
      continue;
    }
    if (!term.components.some((component) => component.role === member)) {
      findings.push(finding(FindingCode.L2_COMPONENT_NOT_ALLOWED,
        'the value carries ' + member + ', and the registry lists no such component for '
        + term.id + '.', { path, subject: member }));
    }
  }
  for (const component of term.components) {
    if (component.min >= 1 && value[component.role] === undefined) {
      findings.push(finding(FindingCode.L2_COMPONENT_MISSING,
        'the registry declares ' + component.role + ' mandatory for ' + term.id
        + ', and the value carries none.', { path, subject: component.role }));
    }
  }
}

/**
 * Layer L3: the document as a whole (section 9.3).
 *
 * Every placed path is walked once, and each of its steps is recorded as an occurrence of
 * that child inside the group instance it lies in. A group instance exists exactly where a
 * value lies under it (section 4.5), so the instances are what the paths give and never what
 * a registry declares.
 */
function checkCardinality(
  findings: Finding[], structure: Structure, placed: Array<{ path: string; steps: Step[] }>,
): void {
  const groupOf = new Map<string, string>([['', '']]);
  const occurrences = new Map<string, Map<string, Set<number>>>();
  for (const value of placed) {
    let instance = '';
    for (const step of value.steps) {
      const children = occurrences.get(instance) ?? new Map<string, Set<number>>();
      occurrences.set(instance, children);
      const indices = children.get(step.id) ?? new Set<number>();
      children.set(step.id, indices);
      indices.add(step.index ?? -1);
      if (step.kind === 'BG') {
        groupOf.set(step.at, step.id);
        instance = step.at;
      }
    }
  }
  const instances = [...groupOf.keys()].sort();
  for (const instance of instances) {
    const group = groupOf.get(instance)!;
    const children = group === '' ? structure.rootChildren() : structure.childrenOf(group);
    const seen = occurrences.get(instance) ?? new Map<string, Set<number>>();
    for (const child of children) {
      report(findings, instance, child, seen.get(child.id));
    }
  }
}

function report(
  findings: Finding[], instance: string, child: Child, indices: Set<number> | undefined,
): void {
  const count = indices === undefined ? 0 : indices.size;
  if (count === 0) {
    if (child.min >= 1) {
      findings.push(finding(
        child.kind === 'BG' ? FindingCode.L3_MISSING_GROUP : FindingCode.L3_MISSING_TERM,
        child.id + ' has a minimum cardinality of ' + child.min + ' and is absent from '
        + where(instance) + '.', { path: instance, subject: child.id }));
    }
    return;
  }
  if (child.max !== 'n' && count > child.max) {
    findings.push(finding(FindingCode.L3_MAX_CARDINALITY,
      child.id + ' occurs ' + count + ' times in ' + where(instance)
      + ', and its maximum cardinality is ' + child.max + '.',
      { path: instance, subject: child.id }));
  }
  const ordinals = [...indices!].filter((index) => index >= 0).sort((a, b) => a - b);
  if (ordinals.length !== count) {
    return;
  }
  for (let i = 0; i < ordinals.length; i++) {
    if (ordinals[i] !== i) {
      findings.push(finding(FindingCode.L3_INDEX_GAP,
        'the occurrences of ' + child.id + ' in ' + where(instance)
        + ' are not dense and zero-based.', { path: instance, subject: child.id }));
      return;
    }
  }
}

function where(instance: string): string {
  return instance === '' ? 'the root of the document' : instance;
}

function notEvaluated(layer: Layer, reason: NotEvaluated['reason']): NotEvaluated {
  return { layer, reason };
}

function result(
  findings: readonly Finding[], evaluated: readonly Layer[], skipped: readonly NotEvaluated[],
): ValidationResult {
  const ordered = [...skipped].sort(
    (left, right) => ALL_LAYERS.indexOf(left.layer) - ALL_LAYERS.indexOf(right.layer));
  return {
    status: statusOf(findings, ordered),
    findings,
    evaluated: [...evaluated],
    notEvaluated: ordered,
  };
}
