import { FindingCode } from './codes.ts';
import { EsjError } from './errors.ts';
import {
  FORMAT, VERSION, VALUE_MEMBERS, type SemanticDocument, type SemanticValue, type Source,
  type ValueMember, documentOf,
} from './document.ts';
import type { Finding } from './finding.ts';
import { extensionsSubject, finding, valuesSubject } from './finding.ts';
import {
  escapeForMessage, forMessage, hasLoneSurrogate, isEdition, isOwnerToken, isPath, isSha256,
  normalizeLineEndings, utf8Length,
} from './grammars.ts';
import { canonicalDecimalForm } from './canonical.ts';
import type { Limits } from './limits.ts';
import { limitsOf } from './limits.ts';
import type { JsonMember, JsonNode, JsonObject } from './json/tree.ts';
import { Scanner, describe, type JsonKind } from './json/scanner.ts';
import { splitSegments } from './paths.ts';

/**
 * The reader: bytes or text in, a document and the findings of layer L1 out (specification,
 * sections 3.2 and 9.1).
 *
 * L1 needs no registry, and this module holds no registry: every check here is decided by
 * the bytes alone. Whether a value's content fits the business term it sits at is an L2
 * question and lives in `validate.ts`.
 *
 * The reader streams. It walks the document once, in the order the text is written, asks
 * the scanner for the token the envelope says belongs next, and judges each member as it
 * reaches it; what it refuses it has the scanner walk past without building it. So the
 * findings are the ones the text reaches first (section 9.6), a limit fires before what it
 * bounds is held (section 12.2), and what a document costs to refuse is the walk over it.
 *
 * A reader and a validator answer differently and both are conformant (section 9.5).
 * {@link readDocument} reports the findings and hands back the document where it could build
 * one; {@link readDocumentOrThrow} raises {@link EsjError} carrying the first finding. The
 * code is the same either way.
 */

/** The members of a value object that make it one (sections 6.1, 6.6 and 6.7). */
const COMPONENTS = ['scheme', 'schemeVersion', 'mimeCode', 'filename'];

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

/**
 * A place inside `extensions`, kept as a chain of steps and spelled out only where a finding
 * names it: an owner may write a hundred thousand nodes, and none of them needs its member
 * access as text unless something is wrong with it.
 */
class Where {
  readonly parent?: Where;
  readonly step: string | number;

  constructor(step: string | number, parent?: Where) {
    this.step = step;
    this.parent = parent;
  }

  /** The member access, from `extensions` down to this place. */
  text(): string {
    const steps: Array<string | number> = [];
    let at: Where | undefined = this;
    while (at !== undefined) {
      steps.push(at.step);
      at = at.parent;
    }
    let out = '';
    for (let i = steps.length - 1; i >= 0; i--) {
      const step = steps[i];
      out += i === steps.length - 1
        ? extensionsSubject(step as string)
        : typeof step === 'number' ? '[' + step + ']' : '["' + escapeForMessage(step) + '"]';
    }
    return out;
  }
}

/** One member of a value object, as the scanner handed it over. */
interface Member {
  readonly name: string;
  readonly kind: JsonKind;
  readonly text?: string;
}

/** One container of an extension subtree that the reader has opened and not yet closed. */
interface Container {
  readonly object: boolean;
  readonly where: Where;
  readonly depth: number;
  readonly members: JsonMember[];
  readonly items: JsonNode[];
  readonly names: Set<string>;
  pending: string;
  any: boolean;
}

/** One run of the reader over one document. */
class Parse {
  readonly findings: Finding[] = [];
  private readonly limits: Limits;
  private scanner!: Scanner;
  private readonly values = new Map<string, SemanticValue>();
  private semanticModel?: string;
  private extensions?: JsonObject;
  private source?: Source;
  private valueCount = 0;
  private binaryBytes = 0;
  private extensionNodes = 0;
  /**
   * The member of `values` the reader is inside, where its name is a path. A finding met
   * there is a finding about that member, and section 9.5 asks it to carry the member's
   * path: a bound met inside the value, a JSON text that breaks off inside it, a member of
   * its value object.
   */
  private currentPath?: string;

  constructor(limits: Limits) {
    this.limits = limits;
  }

  run(text: string): SemanticDocument {
    this.scanner = new Scanner(text, this.limits,
      (code, message) => code === FindingCode.L1_LIMIT
        ? this.limit(message, '')
        : this.fatal(code, message, ''));
    this.readEnvelope();
    // The loop of readEnvelope ended the read where one of the four required members was
    // missing or was not what section 4.1 asks for, so each of them is set here.
    return documentOf({
      format: FORMAT,
      version: VERSION,
      semanticModel: this.semanticModel as string,
      values: this.values,
      ...(this.extensions === undefined ? {} : { extensions: this.extensions }),
      ...(this.source === undefined ? {} : { source: this.source }),
    });
  }

  report(code: FindingCode, message: string, subject: string): void {
    this.findings.push(finding(code, message, { path: this.currentPath ?? '', subject }));
  }

  /**
   * Reports a defect the reader cannot read past and ends the read (section 9.6). Every
   * defect of layer L1 but the ones confined to one member of `values` comes through here.
   */
  private fatal(code: FindingCode, message: string, subject: string): never {
    this.report(code, message, subject);
    throw new Stop();
  }

  /** Reports a bound of section 12.2 and ends the read: a limit is no verdict (section 9.6). */
  private limit(message: string, subject: string): never {
    return this.fatal(FindingCode.L1_LIMIT, message, subject);
  }

  // ------------------------------------------------------------------ the envelope

  /**
   * Reads the envelope in the order the document writes it, so that the first finding
   * reported is the first defect in the text (section 9.6).
   */
  private readEnvelope(): void {
    const scanner = this.scanner;
    if (scanner.peekKind() !== 'object') {
      this.fatal(FindingCode.L1_JSON, 'the top level of an ESJ document is a JSON object.', '');
    }
    scanner.open();
    const seen = new Set<string>();
    let any = false;
    while (scanner.nextMember(any)) {
      any = true;
      const name = scanner.readName();
      const subject = forMessage(name);
      this.nameTheReaderCannotTake(name, subject, seen, subject);
      switch (name) {
        case 'format':
          this.fixed('format', FORMAT);
          break;
        case 'version':
          this.fixed('version', VERSION);
          break;
        case 'semanticModel':
          this.semanticModel = this.edition();
          break;
        case 'values':
          this.readValues();
          break;
        case 'extensions':
          this.extensions = this.readExtensions();
          break;
        case 'source':
          this.source = this.readSource();
          break;
        default:
          this.fatal(FindingCode.L1_ENVELOPE_MEMBER,
            'the envelope carries the member ' + subject
            + ', which this specification does not define.', subject);
      }
    }
    for (const name of ['format', 'version', 'semanticModel', 'values']) {
      if (!seen.has(name)) {
        this.fatal(FindingCode.L1_ENVELOPE_MEMBER,
          'the envelope member ' + name + ' is missing.', name);
      }
    }
    scanner.end();
  }

  /**
   * Holds one member name to the two defects section 9.6 holds against the JSON text rather
   * than against the value written under it: a name carrying a lone surrogate, which names
   * nothing, and a name that has already occurred in this object, which leaves no one object
   * to judge. Either ends the read with that one code, and the object is judged no further.
   *
   * The surrogate is asked first because the two are ranked by the place the text reaches
   * first and a repeated name is met at its second occurrence, so the earlier of the two is
   * always the one reported. The rule reaches every object of a document alike: a value
   * object, `values`, the envelope, `source`, and every object below an owner token of
   * `extensions`.
   */
  private nameTheReaderCannotTake(
    name: string, subject: string, seen: Set<string>, duplicateSubject: string,
  ): void {
    if (hasLoneSurrogate(name)) {
      this.fatal(FindingCode.L1_SURROGATE,
        'a member name carries a lone surrogate and has no UTF-8 encoding.', subject);
    }
    if (seen.has(name)) {
      this.fatal(FindingCode.L1_DUPLICATE_MEMBER,
        'the member ' + forMessage(name) + ' occurs twice in one object.', duplicateSubject);
    }
    seen.add(name);
  }

  /** Reads an envelope member whose one value this specification fixes (section 4.1). */
  private fixed(name: string, expected: string): void {
    const value = this.envelopeString(name);
    if (value !== expected) {
      this.fatal(FindingCode.L1_ENVELOPE_VALUE,
        name + ' carries ' + forMessage(value) + ' where ' + expected + ' belongs.', name);
    }
  }

  /**
   * Reads `semanticModel` and holds it to the edition grammar of section 4.4 and to nothing
   * else. Whether a registry for that edition exists is an L2 question (section 9.2).
   */
  private edition(): string {
    const value = this.envelopeString('semanticModel');
    if (!isEdition(value)) {
      this.fatal(FindingCode.L1_ENVELOPE_VALUE,
        'semanticModel carries ' + forMessage(value)
        + ', which is not an edition of section 4.4.', 'semanticModel');
    }
    return value;
  }

  /**
   * Reads one envelope member that is a JSON string, screening it for a lone surrogate before
   * any check that reads its content: a string with no UTF-8 encoding spells no fixed value and
   * no edition, so a grammar cannot be the first thing said about it (sections 6.8 and 9.6).
   */
  private envelopeString(name: string): string {
    const kind = this.scanner.peekKind();
    if (kind !== 'string') {
      this.fatal(FindingCode.L1_ENVELOPE_VALUE,
        name + ' is ' + describe(kind) + ', not a JSON string.', name);
    }
    const value = this.scanner.readString();
    if (hasLoneSurrogate(value)) {
      this.fatal(FindingCode.L1_SURROGATE,
        'a string carries a lone surrogate and has no UTF-8 encoding.', name);
    }
    return value;
  }

  // ------------------------------------------------------------------ values

  private readValues(): void {
    const scanner = this.scanner;
    const kind = scanner.peekKind();
    if (kind !== 'object') {
      this.fatal(FindingCode.L1_ENVELOPE_VALUE,
        'values is ' + describe(kind) + ', not a JSON object.', 'values');
    }
    scanner.open();
    const seen = new Set<string>();
    let any = false;
    while (scanner.nextMember(any)) {
      any = true;
      const name = scanner.readName();
      const subject = valuesSubject(name);
      this.nameTheReaderCannotTake(name, subject, seen, subject);
      if (++this.valueCount > this.limits.maxValues) {
        this.limit('values carries more than ' + this.limits.maxValues + ' members.', subject);
      }
      const path = this.checkPath(name, subject);
      this.currentPath = path;
      const value = this.readValue(subject);
      if (path !== undefined && value !== undefined) {
        this.values.set(path, value);
      }
      this.currentPath = undefined;
    }
  }

  /**
   * Checks a member name of `values` against the bounds on a path and the path grammar
   * (section 5.1), and returns it where it is a path. A name that is no path is confined to
   * its member: it is reported, and the value written under it is judged all the same.
   */
  private checkPath(name: string, subject: string): string | undefined {
    if (utf8Length(name) > this.limits.maxPathBytes) {
      this.limit('a path is longer than ' + this.limits.maxPathBytes + ' bytes.', subject);
    }
    if (!isPath(name)) {
      this.report(FindingCode.L1_PATH_SYNTAX,
        'the member name ' + forMessage(name) + ' is not a semantic path of section 5.1.',
        subject);
      return undefined;
    }
    if (splitSegments(name).length > this.limits.maxPathSegments) {
      this.limit('a path carries more than ' + this.limits.maxPathSegments + ' segments.',
        subject);
    }
    return name;
  }

  /**
   * Reads one member of `values` against the shape rules of section 6.1, in the order section
   * 9.6 fixes. A defect here is confined to this one member, so it is reported and the members
   * after it are read (section 9.6).
   */
  private readValue(subject: string): SemanticValue | undefined {
    const scanner = this.scanner;
    const kind = scanner.peekKind();
    if (kind === 'string') {
      const raw = scanner.readString();
      if (hasLoneSurrogate(raw)) {
        this.report(FindingCode.L1_SURROGATE,
          'a string carries a lone surrogate and has no UTF-8 encoding.', subject);
        return undefined;
      }
      const content = this.content(raw, subject, 'the value', false);
      return content === undefined ? undefined : { value: content };
    }
    if (kind !== 'object') {
      scanner.skipValue(1);
      this.report(FindingCode.L1_JSON_TYPE,
        'the value is ' + describe(kind) + '; inside values every leaf is a JSON string.',
        subject);
      return undefined;
    }
    return this.readValueObject(subject);
  }

  /**
   * Reads a value written as a JSON object and applies the five checks of one value object in
   * the order of section 9.6: the shape before the member set, and the member set before the
   * content of a member, so that the code a caller branches on is a function of the object and
   * not of the order its members were written in.
   *
   * The members are collected before any of them is judged, and the collection is bounded
   * while it happens (section 12.2): a member that is not a string is walked past and only its
   * kind is kept. A member name that occurs twice, or carries a lone surrogate, ends the read
   * where the text reaches it: section 9.6 holds those two against the JSON text rather than
   * against the value, and they leave no object for this order to judge. A lone surrogate in a
   * member **value** is the other case: it is reported beside the code the order gives,
   * because the two checks read two different strings, and it is reported first for that
   * reason.
   */
  private readValueObject(subject: string): SemanticValue | undefined {
    const scanner = this.scanner;
    scanner.open();
    const members: Member[] = [];
    const seen = new Set<string>();
    let component = false;
    let any = false;
    while (scanner.nextMember(any)) {
      any = true;
      const name = scanner.readName();
      this.nameTheReaderCannotTake(name, subject, seen, subject);
      if (members.length >= this.limits.maxValueMembers) {
        this.limit('a value object carries more than ' + this.limits.maxValueMembers
          + ' members.', subject);
      }
      component ||= COMPONENTS.includes(name);
      const kind = scanner.peekKind();
      if (kind === 'string') {
        members.push({ name, kind, text: scanner.readString() });
      } else {
        scanner.skipValue(2);
        members.push({ name, kind });
      }
    }
    let surrogate = false;
    for (const entry of members) {
      if (entry.text !== undefined && hasLoneSurrogate(entry.text)) {
        this.report(FindingCode.L1_SURROGATE,
          'a string carries a lone surrogate and has no UTF-8 encoding.',
          memberSubject(subject, entry.name));
        surrogate = true;
      }
    }
    if (!component) {
      this.report(FindingCode.L1_VALUE_SHAPE,
        'the value is an object that carries no supplementary component; a value with no'
        + ' component is written as a JSON string.', subject);
      return undefined;
    }
    for (const entry of members) {
      if (entry.kind === 'object') {
        this.report(FindingCode.L1_VALUE_SHAPE,
          'the member ' + forMessage(entry.name) + ' of the value is a JSON object.',
          memberSubject(subject, entry.name));
        return undefined;
      }
    }
    for (const entry of members) {
      if (entry.kind !== 'string') {
        this.report(FindingCode.L1_JSON_TYPE,
          'the member ' + forMessage(entry.name) + ' of the value is ' + describe(entry.kind)
          + '; every member of a value object is a JSON string.',
          memberSubject(subject, entry.name));
        return undefined;
      }
    }
    const named = new Map<ValueMember, string>();
    for (const entry of members) {
      if (!(VALUE_MEMBERS as readonly string[]).includes(entry.name)) {
        this.report(FindingCode.L1_VALUE_MEMBER,
          'the value carries the member ' + forMessage(entry.name)
          + ', which is not one of the five of section 6.1.',
          memberSubject(subject, entry.name));
        return undefined;
      }
      named.set(entry.name as ValueMember, entry.text as string);
    }
    if (!named.has('value')) {
      this.report(FindingCode.L1_VALUE_MEMBER,
        'the value object carries no value member.', subject);
      return undefined;
    }
    if (named.has('schemeVersion') && !named.has('scheme')) {
      this.report(FindingCode.L1_VALUE_MEMBER,
        'the value carries schemeVersion without scheme; a scheme version is the version of a'
        + ' scheme.', memberSubject(subject, 'schemeVersion'));
      return undefined;
    }
    return this.build(named, subject, surrogate);
  }

  /**
   * Checks the strings of a value object, in the order the document wrote them, and builds
   * the value. The content of a value that carries a binary component is held to the larger
   * of the two string bounds of section 12.2: a reader has no registry and cannot know the
   * semantic data type of the term, and the presence of such a component is what it can see.
   */
  private build(
    named: Map<ValueMember, string>, subject: string, surrogate: boolean,
  ): SemanticValue | undefined {
    const binary = named.has('mimeCode') || named.has('filename');
    const checked = new Map<ValueMember, string>();
    for (const [name, raw] of named) {
      const content = this.content(raw, memberSubject(subject, name), 'the member ' + name,
        binary && name === 'value');
      if (content === undefined) {
        return undefined;
      }
      checked.set(name, content);
    }
    if (surrogate) {
      return undefined;
    }
    if (binary) {
      this.countBinary(checked.get('value') as string, subject);
    }
    const value = {} as { -readonly [K in ValueMember]?: string };
    for (const name of VALUE_MEMBERS) {
      const content = checked.get(name);
      if (content !== undefined) {
        value[name] = content;
      }
    }
    return value as SemanticValue;
  }

  /**
   * Checks one string inside `values` whose surrogates have already been looked at: not empty,
   * and within the bound of section 12.2, measured on the normalized value.
   */
  private content(
    raw: string, subject: string, what: string, binary: boolean,
  ): string | undefined {
    const value = normalizeLineEndings(raw);
    if (value === '') {
      this.report(FindingCode.L1_EMPTY_STRING,
        what + ' is the empty string; a business term that is present carries content.',
        subject);
      return undefined;
    }
    const bound = binary ? this.limits.maxBinaryValueBytes : this.limits.maxStringBytes;
    if (utf8Length(value) > bound) {
      this.limit((binary ? 'a binary value is longer than ' : 'a string value is longer than ')
        + bound + ' bytes.', subject);
    }
    return value;
  }

  /** Counts the decoded bytes of a binary value against the bound on the whole document. */
  private countBinary(encoded: string, subject: string): void {
    const decoded = decodedLength(encoded);
    if (this.binaryBytes + decoded > this.limits.maxTotalBinaryBytes) {
      this.limit('the binary content of the document decodes to more than '
        + this.limits.maxTotalBinaryBytes + ' bytes.', subject);
    }
    this.binaryBytes += decoded;
  }

  // ------------------------------------------------------------------ extensions

  private readExtensions(): JsonObject {
    const scanner = this.scanner;
    const kind = scanner.peekKind();
    if (kind !== 'object') {
      this.fatal(FindingCode.L1_ENVELOPE_VALUE,
        'extensions is ' + describe(kind) + ', not a JSON object.', 'extensions');
    }
    scanner.open();
    const members: JsonMember[] = [];
    const seen = new Set<string>();
    let any = false;
    while (scanner.nextMember(any)) {
      any = true;
      const owner = scanner.readName();
      const subject = extensionsSubject(owner);
      this.nameTheReaderCannotTake(owner, subject, seen, 'extensions');
      if (!isOwnerToken(owner)) {
        this.fatal(FindingCode.L1_OWNER_TOKEN,
          'the member name ' + forMessage(owner)
          + ' of extensions is not an owner token of section 4.6.', subject);
      }
      members.push({ name: owner, value: this.readExtensionValue(new Where(owner)) });
    }
    if (seen.size === 0) {
      this.fatal(FindingCode.L1_ENVELOPE_VALUE,
        'extensions is present and empty; it is absent when it would be empty.', 'extensions');
    }
    return { t: 'object', members };
  }

  /**
   * Reads what one owner wrote, in the order the document writes it: a member name the reader
   * cannot take (section 9.6), a string with no UTF-8 encoding (section 6.8), the bound on the
   * canonical form of a number (section 7.6, rule 2), and the bounds of section 12.2 on the
   * depth, the number of nodes and the length of a string. Nothing below an owner token is a
   * member of `values`, so every one of them ends the read.
   *
   * The walk is a loop with the containers it has opened held on a stack, not in call frames:
   * the nesting bound is configurable, and a recursive reader would answer a bound a caller
   * raised with a stack overflow instead of with a document or a finding.
   */
  private readExtensionValue(root: Where): JsonNode {
    const scanner = this.scanner;
    const open: Container[] = [];
    let where = root;
    let depth = 1;
    let finished: JsonNode | undefined;
    for (;;) {
      if (finished === undefined) {
        this.countExtensionNode(where);
        const kind = scanner.peekKind();
        if (kind === 'object' || kind === 'array') {
          if (depth > this.limits.maxExtensionDepth) {
            this.limit('extensions is nested deeper than ' + this.limits.maxExtensionDepth
              + ' levels.', where.text());
          }
          scanner.open();
          open.push({
            object: kind === 'object', where, depth, members: [], items: [], names: new Set(),
            pending: '', any: false,
          });
        } else {
          finished = this.scalar(kind, where);
        }
      }
      if (finished !== undefined) {
        const parent = open[open.length - 1];
        if (parent === undefined) {
          return finished;
        }
        if (parent.object) {
          parent.members.push({ name: parent.pending, value: finished });
        } else {
          parent.items.push(finished);
        }
        finished = undefined;
      }
      const container = open[open.length - 1];
      if (container.object) {
        if (!scanner.nextMember(container.any)) {
          finished = { t: 'object', members: container.members };
          open.pop();
          continue;
        }
        const name = scanner.readName();
        where = new Where(name, container.where);
        this.checkExtensionString(name, where);
        if (container.names.has(name)) {
          this.fatal(FindingCode.L1_DUPLICATE_MEMBER,
            'the member ' + forMessage(name) + ' occurs twice in one object.',
            container.where.text());
        }
        container.names.add(name);
        container.pending = name;
      } else {
        if (!scanner.nextElement(container.any)) {
          finished = { t: 'array', items: container.items };
          open.pop();
          continue;
        }
        where = new Where(container.items.length, container.where);
      }
      container.any = true;
      depth = container.depth + 1;
    }
  }

  /** Counts one node of `extensions` against the bound of section 12.2, before it is read. */
  private countExtensionNode(where: Where): void {
    if (++this.extensionNodes > this.limits.maxExtensionNodes) {
      this.limit('extensions carries more than ' + this.limits.maxExtensionNodes + ' nodes.',
        where.text());
    }
  }

  private scalar(kind: JsonKind, where: Where): JsonNode {
    const scanner = this.scanner;
    switch (kind) {
      case 'string': {
        const value = scanner.readString();
        this.checkExtensionString(value, where);
        return { t: 'string', value };
      }
      case 'number': {
        const raw = scanner.readNumber();
        if (canonicalDecimalForm(raw) === undefined) {
          this.fatal(FindingCode.L1_EXT_NUMBER,
            'the canonical decimal form of the number ' + forMessage(raw)
            + ' inside extensions is longer than 64 characters.', where.text());
        }
        return { t: 'number', raw };
      }
      case 'true':
      case 'false':
        scanner.literal(kind);
        return { t: 'boolean', value: kind === 'true' };
      default:
        scanner.literal('null');
        return { t: 'null' };
    }
  }

  /** Holds a string of `extensions`, a member name included, to sections 6.8 and 12.2. */
  private checkExtensionString(value: string, where: Where): void {
    if (hasLoneSurrogate(value)) {
      this.fatal(FindingCode.L1_SURROGATE,
        'a string carries a lone surrogate and has no UTF-8 encoding.', where.text());
    }
    if (utf8Length(value) > this.limits.maxStringBytes) {
      this.limit('a string inside extensions is longer than ' + this.limits.maxStringBytes
        + ' bytes.', where.text());
    }
  }

  // ------------------------------------------------------------------ source

  private readSource(): Source {
    const scanner = this.scanner;
    const kind = scanner.peekKind();
    if (kind !== 'object') {
      this.fatal(FindingCode.L1_ENVELOPE_VALUE,
        'source is ' + describe(kind) + ', not a JSON object.', 'source');
    }
    scanner.open();
    const seen = new Set<string>();
    const source: { syntax?: string; sha256?: string } = {};
    let any = false;
    while (scanner.nextMember(any)) {
      any = true;
      const name = scanner.readName();
      const subject = 'source["' + escapeForMessage(name) + '"]';
      this.nameTheReaderCannotTake(name, subject, seen, 'source');
      if (name !== 'syntax' && name !== 'sha256') {
        this.fatal(FindingCode.L1_ENVELOPE_MEMBER,
          'source carries the member ' + forMessage(name)
          + ', which section 4.7 does not define.', subject);
      }
      const member = scanner.peekKind();
      if (member !== 'string') {
        this.fatal(FindingCode.L1_ENVELOPE_VALUE,
          'the member ' + name + ' of source is ' + describe(member) + ', not a JSON string.',
          subject);
      }
      const text = scanner.readString();
      if (hasLoneSurrogate(text)) {
        this.fatal(FindingCode.L1_SURROGATE,
          'a string carries a lone surrogate and has no UTF-8 encoding.', subject);
      }
      if (text === '') {
        this.fatal(FindingCode.L1_ENVELOPE_VALUE, 'source.' + name + ' is the empty string.',
          subject);
      }
      if (name === 'syntax') {
        if (utf8Length(text) > this.limits.maxStringBytes) {
          this.limit('source.syntax is longer than ' + this.limits.maxStringBytes + ' bytes.',
            subject);
        }
        source.syntax = text;
        continue;
      }
      if (!isSha256(text)) {
        this.fatal(FindingCode.L1_ENVELOPE_VALUE,
          'source.sha256 carries ' + forMessage(text)
          + ', which is not 64 lowercase hexadecimal digits.', subject);
      }
      source.sha256 = text;
    }
    if (seen.size === 0) {
      this.fatal(FindingCode.L1_ENVELOPE_VALUE,
        'source is present and empty; it is absent when it would be empty.', 'source');
    }
    return source;
  }
}

/** The member access of one member of a value object, for the subject of a finding. */
function memberSubject(subject: string, name: string): string {
  return subject + '.' + escapeForMessage(name);
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
  const parse = new Parse(limits);
  let text: string;
  try {
    text = decode(input, limits);
  } catch (failure) {
    if (!(failure instanceof EsjError)) {
      throw failure;
    }
    parse.report(failure.code ?? FindingCode.L1_ENCODING, failure.message, '');
    return { findings: parse.findings };
  }
  try {
    const document = parse.run(text);
    return { document, findings: parse.findings };
  } catch (failure) {
    if (!(failure instanceof Stop)) {
      throw failure;
    }
    return { findings: parse.findings };
  }
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
  } catch (failure) {
    // A decoder told to be fatal raises a TypeError for a byte sequence that is not UTF-8,
    // and that is the one failure this finding is about.
    if (!(failure instanceof TypeError)) {
      throw failure;
    }
    throw new EsjError('the byte sequence is not UTF-8.', FindingCode.L1_ENCODING);
  }
}

/** Returns how many bytes a base64 string decodes to, without decoding it. */
function decodedLength(encoded: string): number {
  const padding = encoded.endsWith('==') ? 2 : encoded.endsWith('=') ? 1 : 0;
  return Math.max(0, Math.floor(encoded.length / 4) * 3 - padding);
}
