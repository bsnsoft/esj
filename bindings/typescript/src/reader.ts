import { FindingCode } from './codes.ts';
import { EsjError } from './errors.ts';
import {
  FORMAT, VERSION, VALUE_MEMBERS, type SemanticDocument, type SemanticValue, type Source,
  documentOf, isBinary,
} from './document.ts';
import type { Finding } from './finding.ts';
import { extensionsSubject, finding, valuesSubject } from './finding.ts';
import {
  forMessage, hasLoneSurrogate, isEdition, isOwnerToken, isPath, isSha256,
  normalizeLineEndings, utf8Length,
} from './grammars.ts';
import { isNumberWithinBound } from './canonical.ts';
import type { Limits } from './limits.ts';
import { limitsOf } from './limits.ts';
import type { JsonNode, JsonObject } from './json/tree.ts';
import { ParseFailure, parseJson } from './json/parse.ts';
import { splitSegments } from './paths.ts';

/**
 * The reader: bytes or text in, a document and the findings of layer L1 out (specification,
 * sections 3.2 and 9.1).
 *
 * L1 needs no registry, and this module holds no registry: every check here is decided by
 * the bytes alone. Whether a value's content fits the business term it sits at is an L2
 * question and lives in `validate.ts`.
 *
 * A reader and a validator answer differently and both are conformant (section 9.5).
 * {@link readDocument} reports the findings and hands back the document where it could build
 * one; {@link readDocumentOrThrow} raises {@link EsjError} carrying the first finding. The
 * code is the same either way.
 */

/** The members of `source` (section 4.7). */
const SOURCE_MEMBERS = ['syntax', 'sha256'];

/** How a reader was configured. */
export interface ReadOptions {
  /** The limits of section 12.2, where they are not the defaults. */
  readonly limits?: Partial<Limits>;
}

/** What a read produced. */
export interface ReadResult {
  /**
   * The document, where the reader could build one.
   *
   * A document is handed back even where findings were reported, so long as the envelope
   * could be read: a caller that wants to canonicalize a document with one bad value has it,
   * and a caller that wants a verdict reads {@link ReadResult.findings}.
   */
  readonly document?: SemanticDocument;
  /** The findings of layer L1, in the order the reader produced them. */
  readonly findings: readonly Finding[];
}

/**
 * What a reader raises where it cannot read past a defect.
 *
 * Section 9.6 fixes how far a reader reads: it reads past a defect confined to one member of
 * `values` and stops at every other one, so that the findings of a document are a function of
 * its bytes and two conformant readers answer one byte sequence with one list. This is that
 * stop. It carries nothing, the finding having been reported before it was raised, and it
 * never leaves this module.
 */
class Stop extends Error {
  constructor() {
    super('the reader stopped at a defect it cannot read past');
    this.name = 'Stop';
  }
}

/** Where a finding sits: the member of `values` it is about, and the member access. */
interface Place {
  readonly path?: string;
  readonly subject?: string;
}

class Reading {
  readonly findings: Finding[] = [];
  readonly limits: Limits;
  private binaryBytes = 0;

  constructor(limits: Limits) {
    this.limits = limits;
  }

  report(code: FindingCode, message: string, place?: Place): void {
    this.findings.push(finding(code, message, place));
  }

  /**
   * Reports a defect the reader cannot read past and ends the read (section 9.6). Every
   * defect of layer L1 but the ones confined to one member of `values` comes through here.
   */
  fatal(code: FindingCode, message: string, place?: Place): never {
    this.report(code, message, place);
    throw new Stop();
  }

  /** Counts the decoded bytes of a binary value against the bound on the whole document. */
  countBinary(encoded: string, path: string, subject: string): void {
    this.binaryBytes += decodedLength(encoded);
    if (this.binaryBytes > this.limits.maxTotalBinaryBytes) {
      this.fatal(FindingCode.L1_LIMIT,
        'the binary content of the document decodes to more than '
        + this.limits.maxTotalBinaryBytes + ' bytes.', { path, subject });
    }
  }
}

/**
 * Reads a document.
 *
 * @param input the document, as the bytes of its UTF-8 encoding or as decoded text
 * @param options how the reader is configured
 * @return the document, where one could be built, and the findings of layer L1
 */
export function readDocument(input: Uint8Array | string, options?: ReadOptions): ReadResult {
  const limits = limitsOf(options?.limits);
  const reading = new Reading(limits);
  let text: string;
  try {
    text = decode(input, limits);
  } catch (failure) {
    const error = failure as EsjError;
    reading.report(error.code ?? FindingCode.L1_ENCODING, error.message);
    return { findings: reading.findings };
  }
  let root: JsonObject;
  try {
    root = parseJson(text, limits);
  } catch (failure) {
    const error = failure as ParseFailure;
    reading.report(error.code, error.message, error.path === ''
      ? undefined
      : { path: error.path, subject: valuesSubject(error.path) });
    return { findings: reading.findings };
  }
  let document: SemanticDocument | undefined;
  try {
    document = readEnvelope(reading, root);
  } catch (failure) {
    if (!(failure instanceof Stop)) {
      throw failure;
    }
  }
  return document === undefined
    ? { findings: reading.findings }
    : { document, findings: reading.findings };
}

/**
 * Reads a document and raises where it cannot hand one back.
 *
 * @param input the document, as bytes or as decoded text
 * @param options how the reader is configured
 * @return the document
 * @throws EsjError carrying the first finding, where the document is not well formed
 */
export function readDocumentOrThrow(
  input: Uint8Array | string, options?: ReadOptions,
): SemanticDocument {
  const result = readDocument(input, options);
  const first = result.findings.find((entry) => entry.severity === 'error');
  if (first !== undefined) {
    throw new EsjError(first.message, first.code, first.path === '' ? undefined : first.path);
  }
  if (result.document === undefined) {
    throw new EsjError('the document could not be read', FindingCode.L1_JSON);
  }
  return result.document;
}

/**
 * Decodes the input, refusing anything that is not UTF-8 without a byte order mark
 * (section 4.2, rule 2) and anything past the document bound (section 12.2).
 */
function decode(input: Uint8Array | string, limits: Limits): string {
  if (typeof input === 'string') {
    if (utf8Length(input) > limits.maxDocumentBytes) {
      throw new EsjError('the document is longer than ' + limits.maxDocumentBytes + ' bytes.',
        FindingCode.L1_LIMIT);
    }
    if (input.startsWith('﻿')) {
      throw new EsjError('the document begins with a byte order mark, which is not whitespace'
        + ' and is not part of a JSON text.', FindingCode.L1_ENCODING);
    }
    return input;
  }
  if (input.length > limits.maxDocumentBytes) {
    throw new EsjError('the document is longer than ' + limits.maxDocumentBytes + ' bytes.',
      FindingCode.L1_LIMIT);
  }
  if (input.length >= 3 && input[0] === 0xef && input[1] === 0xbb && input[2] === 0xbf) {
    throw new EsjError('the document begins with a byte order mark, which is not whitespace'
      + ' and is not part of a JSON text.', FindingCode.L1_ENCODING);
  }
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(input);
  } catch {
    throw new EsjError('the byte sequence is not UTF-8.', FindingCode.L1_ENCODING);
  }
}

/**
 * Holds one member name to the two defects section 9.6 holds against the JSON text rather
 * than against the value written under it: a name carrying a lone surrogate, which names
 * nothing, and a name that has already occurred in this object, which leaves no one object to
 * judge. Either ends the read with that one code, and the object is judged no further.
 *
 * The surrogate is asked first because the two are ranked by the place the text reaches first
 * and a repeated name is met at its second occurrence, so the earlier of the two is always the
 * one reported. The rule reaches every object of a document alike: a value object, `values`,
 * the envelope, `source`, and every object below an owner token of `extensions`.
 *
 * @param reading the run
 * @param name the member name, as the document spells it
 * @param place where a finding about this name sits
 * @param seen the names this object has already carried, added to here
 * @param duplicatePlace where a finding about a repeated name in this object sits
 */
function nameTheReaderCannotTake(
  reading: Reading, name: string, place: Place, seen: Set<string>, duplicatePlace: Place,
): void {
  if (hasLoneSurrogate(name)) {
    reading.fatal(FindingCode.L1_SURROGATE,
      'a member name carries a lone surrogate and has no UTF-8 encoding.', place);
  }
  if (seen.has(name)) {
    reading.fatal(FindingCode.L1_DUPLICATE_MEMBER,
      'the member ' + forMessage(name) + ' occurs twice in one object.', duplicatePlace);
  }
  seen.add(name);
}

/**
 * Reads the envelope in the order the document writes it, so that the first finding reported
 * is the first defect in the text (section 9.6).
 */
function readEnvelope(reading: Reading, root: JsonObject): SemanticDocument {
  const seen = new Set<string>();
  let format: string | undefined;
  let version: string | undefined;
  let semanticModel: string | undefined;
  let values: Map<string, SemanticValue> | undefined;
  let extensions: JsonObject | undefined;
  let source: Source | undefined;
  for (const entry of root.members) {
    nameTheReaderCannotTake(reading, entry.name, { subject: forMessage(entry.name) },
      seen, { subject: forMessage(entry.name) });
    switch (entry.name) {
      case 'format':
        format = envelopeFixed(reading, entry.value, 'format', FORMAT);
        break;
      case 'version':
        version = envelopeFixed(reading, entry.value, 'version', VERSION);
        break;
      case 'semanticModel':
        semanticModel = edition(reading, entry.value);
        break;
      case 'values':
        values = readValues(reading, entry.value);
        break;
      case 'extensions':
        extensions = readExtensions(reading, entry.value);
        break;
      case 'source':
        source = readSource(reading, entry.value);
        break;
      default:
        reading.fatal(FindingCode.L1_ENVELOPE_MEMBER,
          'the envelope carries the member ' + forMessage(entry.name)
          + ', which this specification does not define.',
          { subject: forMessage(entry.name) });
    }
  }
  for (const name of ['format', 'version', 'semanticModel', 'values']) {
    if (!seen.has(name)) {
      reading.fatal(FindingCode.L1_ENVELOPE_MEMBER,
        'the envelope member ' + name + ' is missing.', { subject: name });
    }
  }
  // The loop above ended the read where one of the four required members was missing or was
  // not what section 4.1 asks for, so each of them is set here.
  return documentOf({
    format: format as string,
    version: version as string,
    semanticModel: semanticModel as string,
    values: values as Map<string, SemanticValue>,
    ...(extensions === undefined ? {} : { extensions }),
    ...(source === undefined ? {} : { source }),
  });
}

/** Reads an envelope member whose one value this specification fixes (section 4.1). */
function envelopeFixed(
  reading: Reading, node: JsonNode, name: string, expected: string,
): string {
  const value = envelopeString(reading, node, name);
  if (value !== expected) {
    reading.fatal(FindingCode.L1_ENVELOPE_VALUE,
      name + ' carries ' + forMessage(value) + ' where ' + expected + ' belongs.',
      { subject: name });
  }
  return value;
}

/**
 * Reads `semanticModel` and holds it to the edition grammar of section 4.4 and to nothing
 * else. Whether a registry for that edition exists is an L2 question (section 9.2).
 */
function edition(reading: Reading, node: JsonNode): string {
  const value = envelopeString(reading, node, 'semanticModel');
  if (!isEdition(value)) {
    reading.fatal(FindingCode.L1_ENVELOPE_VALUE,
      'semanticModel carries ' + forMessage(value)
      + ', which is not an edition of section 4.4.', { subject: 'semanticModel' });
  }
  return value;
}

/**
 * Reads one envelope member that is a JSON string, screening it for a lone surrogate before
 * any check that reads its content: a string with no UTF-8 encoding spells no fixed value and
 * no edition, so a grammar cannot be the first thing said about it (sections 6.8 and 9.6).
 */
function envelopeString(reading: Reading, node: JsonNode, name: string): string {
  if (node.t !== 'string') {
    reading.fatal(FindingCode.L1_ENVELOPE_VALUE, name + ' is a JSON string.',
      { subject: name });
  }
  if (hasLoneSurrogate(node.value)) {
    reading.fatal(FindingCode.L1_SURROGATE,
      'a string carries a lone surrogate and has no UTF-8 encoding.', { subject: name });
  }
  return node.value;
}

function readValues(reading: Reading, node: JsonNode): Map<string, SemanticValue> {
  if (node.t !== 'object') {
    reading.fatal(FindingCode.L1_ENVELOPE_VALUE, 'values is a JSON object.',
      { subject: 'values' });
  }
  const seen = new Set<string>();
  const values = new Map<string, SemanticValue>();
  for (const entry of node.members) {
    nameTheReaderCannotTake(reading, entry.name, { subject: valuesSubject(entry.name) },
      seen, { subject: valuesSubject(entry.name) });
    const value = readValue(reading, entry.name, entry.value);
    if (value !== undefined) {
      values.set(entry.name, value);
    }
  }
  return values;
}

/**
 * Reads one member of `values`: its name against the path grammar and the bounds a path is
 * held to, and its value against the shape rules of section 6.1 in the order section 9.6
 * fixes. A defect here is confined to this one member, so it is reported and the members
 * after it are read (section 9.6).
 */
function readValue(
  reading: Reading, name: string, node: JsonNode,
): SemanticValue | undefined {
  const subject = valuesSubject(name);
  if (!checkPath(reading, name, subject)) {
    return undefined;
  }
  if (node.t === 'string') {
    if (hasLoneSurrogate(node.value)) {
      reading.report(FindingCode.L1_SURROGATE,
        'a string carries a lone surrogate and has no UTF-8 encoding.', { path: name });
      return undefined;
    }
    if (node.value === '') {
      reading.report(FindingCode.L1_EMPTY_STRING,
        'the value is the empty string; a business term that is present carries content.',
        { path: name });
      return undefined;
    }
    const value: SemanticValue = { value: normalizeLineEndings(node.value) };
    return boundStrings(reading, name, value) ? value : undefined;
  }
  if (node.t !== 'object') {
    reading.report(FindingCode.L1_JSON_TYPE,
      'the value is a JSON ' + jsonType(node) + '; inside values every leaf is a JSON string.',
      { path: name });
    return undefined;
  }
  return readValueObject(reading, name, node);
}

/**
 * Applies the five checks of one value object in the order of section 9.6: the shape before
 * the member set, and the member set before the content of a member, so that the code a
 * caller branches on is a function of the object and not of the order its members were
 * written in.
 *
 * A member name that occurs twice, or carries a lone surrogate, ends the read before any of
 * them: section 9.6 holds those two against the JSON text rather than against the value, and
 * they leave no object for this order to judge. A lone surrogate in a member **value** is the
 * other case: it is reported beside the code the order gives, because the two checks read two
 * different strings, and it is reported first for that reason.
 */
function readValueObject(
  reading: Reading, name: string, node: JsonObject,
): SemanticValue | undefined {
  const seen = new Set<string>();
  for (const entry of node.members) {
    nameTheReaderCannotTake(reading, entry.name,
      { path: name, subject: valuesSubject(name) }, seen,
      { path: name, subject: valuesSubject(name) });
  }
  let surrogate = false;
  for (const entry of node.members) {
    if (entry.value.t === 'string' && hasLoneSurrogate(entry.value.value)) {
      reading.report(FindingCode.L1_SURROGATE,
        'a string carries a lone surrogate and has no UTF-8 encoding.', { path: name });
      surrogate = true;
    }
  }
  const named = new Map<string, JsonNode>();
  for (const entry of node.members) {
    named.set(entry.name, entry.value);
  }
  const components = ['scheme', 'schemeVersion', 'mimeCode', 'filename'];
  if (!components.some((component) => named.has(component))) {
    reading.report(FindingCode.L1_VALUE_SHAPE,
      'the value is an object that carries no supplementary component; a value with no'
      + ' component is written as a JSON string.', { path: name });
    return undefined;
  }
  for (const entry of node.members) {
    if (entry.value.t === 'object') {
      reading.report(FindingCode.L1_VALUE_SHAPE,
        'the member ' + forMessage(entry.name) + ' of the value is a JSON object.',
        { path: name });
      return undefined;
    }
  }
  for (const entry of node.members) {
    if (entry.value.t !== 'string') {
      reading.report(FindingCode.L1_JSON_TYPE,
        'the member ' + forMessage(entry.name) + ' of the value is a JSON '
        + jsonType(entry.value) + '; every member of a value object is a JSON string.',
        { path: name });
      return undefined;
    }
  }
  for (const entry of node.members) {
    if (!(VALUE_MEMBERS as readonly string[]).includes(entry.name)) {
      reading.report(FindingCode.L1_VALUE_MEMBER,
        'the value carries the member ' + forMessage(entry.name)
        + ', which is not one of the five of section 6.1.', { path: name });
      return undefined;
    }
  }
  if (!named.has('value')) {
    reading.report(FindingCode.L1_VALUE_MEMBER,
      'the value object carries no value member.', { path: name });
    return undefined;
  }
  if (named.has('schemeVersion') && !named.has('scheme')) {
    reading.report(FindingCode.L1_VALUE_MEMBER,
      'the value carries schemeVersion without scheme; a scheme version is the version of a'
      + ' scheme.', { path: name });
    return undefined;
  }
  for (const entry of node.members) {
    if (entry.value.t === 'string' && entry.value.value === '') {
      reading.report(FindingCode.L1_EMPTY_STRING,
        'the member ' + entry.name + ' of the value is the empty string.', { path: name });
      return undefined;
    }
  }
  if (surrogate) {
    return undefined;
  }
  const value: SemanticValue = {} as SemanticValue;
  const writable = value as { -readonly [K in keyof SemanticValue]: string };
  for (const component of VALUE_MEMBERS) {
    const node1 = named.get(component);
    if (node1 !== undefined && node1.t === 'string') {
      writable[component] = normalizeLineEndings(node1.value);
    }
  }
  return boundStrings(reading, name, value) ? value : undefined;
}

/**
 * Applies the two string bounds of section 12.2 to one value: the larger one to the content
 * of a binary object, and the smaller one to every other string, the supplementary
 * components included.
 */
function boundStrings(reading: Reading, path: string, value: SemanticValue): boolean {
  const binary = isBinary(value);
  const bound = binary ? reading.limits.maxBinaryValueBytes : reading.limits.maxStringBytes;
  if (utf8Length(value.value) > bound) {
    reading.fatal(FindingCode.L1_LIMIT,
      'the value is longer than ' + bound + ' bytes.', { path });
  }
  for (const component of VALUE_MEMBERS) {
    if (component === 'value') {
      continue;
    }
    const text = value[component];
    if (text !== undefined && utf8Length(text) > reading.limits.maxStringBytes) {
      reading.fatal(FindingCode.L1_LIMIT,
        'the member ' + component + ' of the value is longer than '
        + reading.limits.maxStringBytes + ' bytes.', { path });
    }
  }
  if (binary) {
    reading.countBinary(value.value, path, valuesSubject(path));
  }
  return true;
}

/** Checks a member name of `values` against the path grammar and the bounds on a path. */
function checkPath(reading: Reading, name: string, subject: string): boolean {
  if (utf8Length(name) > reading.limits.maxPathBytes) {
    reading.fatal(FindingCode.L1_LIMIT,
      'a path is longer than ' + reading.limits.maxPathBytes + ' bytes.', { subject });
  }
  if (!isPath(name)) {
    reading.report(FindingCode.L1_PATH_SYNTAX,
      'the member name ' + forMessage(name) + ' is not a semantic path of section 5.1.',
      { subject });
    return false;
  }
  if (splitSegments(name).length > reading.limits.maxPathSegments) {
    reading.fatal(FindingCode.L1_LIMIT,
      'a path carries more than ' + reading.limits.maxPathSegments + ' segments.', { subject });
  }
  return true;
}

function readExtensions(reading: Reading, node: JsonNode): JsonObject {
  if (node.t !== 'object') {
    reading.fatal(FindingCode.L1_ENVELOPE_VALUE, 'extensions is a JSON object.',
      { subject: 'extensions' });
  }
  if (node.members.length === 0) {
    reading.fatal(FindingCode.L1_ENVELOPE_VALUE,
      'extensions is present and empty; it is absent when it would be empty.',
      { subject: 'extensions' });
  }
  const seen = new Set<string>();
  for (const entry of node.members) {
    const subject = extensionsSubject(entry.name);
    nameTheReaderCannotTake(reading, entry.name, { subject }, seen, { subject: 'extensions' });
    if (!isOwnerToken(entry.name)) {
      reading.fatal(FindingCode.L1_OWNER_TOKEN,
        'the member name ' + forMessage(entry.name)
        + ' of extensions is not an owner token of section 4.6.', { subject });
    }
    checkExtensionContent(reading, entry.value, subject);
  }
  return node;
}

/**
 * Checks what an owner wrote, in the order the document writes it: a member name the reader
 * cannot take (section 9.6), a string with no UTF-8 encoding (section 6.8), the bound on the
 * canonical form of a number (section 7.6, rule 2) and the bound on the length of a string
 * (section 12.2). Nothing below an owner token is a member of `values`, so every one of them
 * ends the read.
 */
function checkExtensionContent(reading: Reading, node: JsonNode, subject: string): void {
  if (node.t === 'object') {
    const seen = new Set<string>();
    for (const entry of node.members) {
      nameTheReaderCannotTake(reading, entry.name, { subject }, seen, { subject });
      checkExtensionContent(reading, entry.value, subject);
    }
  } else if (node.t === 'array') {
    for (const item of node.items) {
      checkExtensionContent(reading, item, subject);
    }
  } else if (node.t === 'number' && !isNumberWithinBound(node.raw)) {
    reading.fatal(FindingCode.L1_EXT_NUMBER,
      'the canonical decimal form of the number ' + forMessage(node.raw)
      + ' inside extensions is longer than 64 characters.', { subject });
  } else if (node.t === 'string') {
    if (hasLoneSurrogate(node.value)) {
      reading.fatal(FindingCode.L1_SURROGATE,
        'a string carries a lone surrogate and has no UTF-8 encoding.', { subject });
    }
    if (utf8Length(node.value) > reading.limits.maxStringBytes) {
      reading.fatal(FindingCode.L1_LIMIT,
        'a string inside extensions is longer than ' + reading.limits.maxStringBytes
        + ' bytes.', { subject });
    }
  }
}

function readSource(reading: Reading, node: JsonNode): Source {
  if (node.t !== 'object') {
    reading.fatal(FindingCode.L1_ENVELOPE_VALUE, 'source is a JSON object.',
      { subject: 'source' });
  }
  if (node.members.length === 0) {
    reading.fatal(FindingCode.L1_ENVELOPE_VALUE,
      'source is present and empty; it is absent when it would be empty.',
      { subject: 'source' });
  }
  const seen = new Set<string>();
  const source: { syntax?: string; sha256?: string } = {};
  for (const entry of node.members) {
    const subject = 'source["' + forMessage(entry.name) + '"]';
    nameTheReaderCannotTake(reading, entry.name, { subject }, seen, { subject: 'source' });
    if (!SOURCE_MEMBERS.includes(entry.name)) {
      reading.fatal(FindingCode.L1_ENVELOPE_MEMBER,
        'source carries the member ' + forMessage(entry.name)
        + ', which section 4.7 does not define.', { subject });
    }
    if (entry.value.t !== 'string') {
      reading.fatal(FindingCode.L1_ENVELOPE_VALUE,
        'the member ' + entry.name + ' of source is a JSON string.', { subject });
    }
    const text = entry.value.value;
    if (hasLoneSurrogate(text)) {
      reading.fatal(FindingCode.L1_SURROGATE,
        'a string carries a lone surrogate and has no UTF-8 encoding.', { subject });
    }
    if (text === '') {
      reading.fatal(FindingCode.L1_ENVELOPE_VALUE,
        'source.' + entry.name + ' is the empty string.', { subject });
    }
    if (entry.name === 'syntax') {
      if (utf8Length(text) > reading.limits.maxStringBytes) {
        reading.fatal(FindingCode.L1_LIMIT,
          'source.syntax is longer than ' + reading.limits.maxStringBytes + ' bytes.',
          { subject });
      }
      source.syntax = text;
      continue;
    }
    if (!isSha256(text)) {
      reading.fatal(FindingCode.L1_ENVELOPE_VALUE,
        'source.sha256 carries ' + forMessage(text)
        + ', which is not 64 lowercase hexadecimal digits.', { subject });
    }
    source.sha256 = text;
  }
  return source;
}

function jsonType(node: JsonNode): string {
  switch (node.t) {
    case 'object':
      return 'object';
    case 'array':
      return 'array';
    case 'string':
      return 'string';
    case 'number':
      return 'number';
    case 'boolean':
      return node.value ? 'true' : 'false';
    case 'null':
      return 'null';
  }
}

/** Returns how many bytes a base64 string decodes to, without decoding it. */
function decodedLength(encoded: string): number {
  const padding = encoded.endsWith('==') ? 2 : encoded.endsWith('=') ? 1 : 0;
  return Math.max(0, Math.floor(encoded.length / 4) * 3 - padding);
}
