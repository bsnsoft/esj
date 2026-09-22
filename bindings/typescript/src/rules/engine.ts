import { Decimal } from '../decimal.ts';
import type { SemanticDocument, SemanticValue } from '../document.ts';
import { codePointCount, forMessage, isDate, isDecimal } from '../grammars.ts';
import { comparePathText } from '../paths.ts';
import type { Structure } from '../structure.ts';
import type {
  CodeListFile, Expression, RuleDefinition, RulePackFile, RuleSeverity,
} from './pack.ts';
import { CodeList, RulePackError, ruleIdOfDeclared } from './pack.ts';

/**
 * The engine of the rule language of `rules/README.md`.
 *
 * A rule is a statement about business terms, evaluated once per instance of its context.
 * Three things decide how it reads: the semantic data type of a path comes from the registry
 * and never from a declaration, absence propagates so that a rule about a term an invoice
 * does not carry decides nothing, and every number is exact.
 */

/** What a rule found. */
export interface RuleFinding {
  /** The rule identifier, which is the code of the finding. */
  readonly rule: string;
  /** How much it weighs. */
  readonly severity: RuleSeverity;
  /** The business group instance the rule was evaluated at; the empty string for the document. */
  readonly context: string;
  /** What the finding says. */
  readonly message: string;
  /** The pack that produced it. */
  readonly pack: string;
  /** The version of that pack. */
  readonly version: string;
}

/** The value an expression has: a number, a date, text, a truth value, or nothing at all. */
export type RuleValue =
  | { readonly k: 'absent' }
  | { readonly k: 'decimal'; readonly decimal: Decimal }
  | { readonly k: 'date'; readonly text: string }
  | { readonly k: 'text'; readonly text: string }
  | { readonly k: 'boolean'; readonly truth: boolean };

/**
 * The three readers of a {@link RuleValue}.
 *
 * Which of the four shapes a value has was decided when the pack was compiled: every operand
 * was coerced to one type there, and an operand that could not be is a defect of the pack
 * that never reaches an invoice. These read the value that type promises.
 */
function numberOf(value: RuleValue): Decimal {
  return (value as { decimal: Decimal }).decimal;
}

function textOf(value: RuleValue): string {
  return (value as { text: string }).text;
}

function truthOf(value: RuleValue): boolean {
  return (value as { truth: boolean }).truth;
}

const ABSENT: RuleValue = { k: 'absent' };

/** What the engine raises where a value does not spell what its semantic data type requires. */
export class Undecided extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'Undecided';
  }
}

/** The type an expression has, decided when the pack is compiled. */
type RuleType = 'decimal' | 'date' | 'text' | 'boolean' | 'any';

/** A compiled expression. */
type Evaluate = (run: Run, base: string) => RuleValue;

interface Compiled {
  readonly evaluate: Evaluate;
  readonly type: RuleType;
  /** The text of a string literal, which is what lets it take the type it stands beside. */
  readonly literal: string | null;
}

/**
 * A rule the language cannot express, written in this binding instead.
 *
 * It answers the same two questions a rule file answers and produces the same finding: which
 * side of that line a rule fell on is an implementation detail and never a fact about the
 * invoice.
 */
export interface NativeRule {
  /** The identifier the standard gives the rule. */
  readonly id: string;
  /** What the rule is a statement about. */
  readonly context: string;
  /** The business terms it reads. */
  readonly terms: readonly string[];
  /** The clause of the standard it states. */
  readonly source: string;
  /** Returns the message of a fault, or `undefined`. */
  check(context: RuleContext): string | undefined;
  /** Returns the message of a warning, weighed only where {@link check} held. */
  warn?(context: RuleContext): string | undefined;
}

/** One rule, ready to run. */
interface Rule {
  readonly id: string;
  readonly severity: 'fatal' | 'warning';
  /** The context pattern, or `null` where the rule is about the document. */
  readonly context: string | null;
  readonly decide: (run: Run, base: string) => { severity: RuleSeverity; message: string } | undefined;
}

/** The document, indexed so that a pattern costs the range it addresses. */
class Index {
  readonly paths: string[];
  readonly values: SemanticValue[];

  constructor(document: SemanticDocument) {
    this.paths = [...document.values.keys()];
    this.values = [...document.values.values()];
  }

  /**
   * Returns the half-open range of paths that lie at or below a literal prefix.
   *
   * The values of a document are held in canonical path order, in which the paths under one
   * prefix are contiguous, so a pattern evaluated inside an invoice line costs the size of
   * that line and not the size of the invoice.
   */
  range(prefix: string): [number, number] {
    if (prefix === '') {
      return [0, this.paths.length];
    }
    let low = 0;
    let high = this.paths.length;
    while (low < high) {
      const middle = (low + high) >> 1;
      if (comparePathText(this.paths[middle], prefix) < 0) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    let end = low;
    while (end < this.paths.length && isUnder(this.paths[end], prefix)) {
      end++;
    }
    return [low, end];
  }
}

function isUnder(path: string, prefix: string): boolean {
  return path === prefix || (path.startsWith(prefix) && path.charAt(prefix.length) === '/');
}

/** One run of a pack over one document. */
class Run {
  readonly document: SemanticDocument;
  readonly structure: Structure;
  readonly lists: Map<string, CodeList>;
  private readonly index: Index;
  private readonly aggregates = new Map<string, RuleValue>();
  private readonly shared = new Map<string, unknown>();
  private readonly patterns = new Map<string, CompiledPattern>();

  constructor(document: SemanticDocument, structure: Structure, lists: Map<string, CodeList>) {
    this.document = document;
    this.structure = structure;
    this.lists = lists;
    this.index = new Index(document);
  }

  /** Returns the values a pattern addresses inside an instance, in canonical order. */
  matches(pattern: CompiledPattern, base: string): Array<[string, SemanticValue]> {
    const absolute = base + pattern.text;
    if (!pattern.wildcard) {
      const value = this.document.values.get(absolute);
      return value === undefined ? [] : [[absolute, value]];
    }
    const [start, end] = this.index.range(base + pattern.literalPrefix);
    const regex = pattern.valueRegex;
    const found: Array<[string, SemanticValue]> = [];
    for (let i = start; i < end; i++) {
      if (regex.test(this.index.paths[i].slice(base.length))) {
        found.push([this.index.paths[i], this.index.values[i]]);
      }
    }
    return found;
  }

  /** Returns the business group instances a pattern addresses inside an instance. */
  instances(pattern: CompiledPattern, base: string): string[] {
    const absolute = base + pattern.text;
    if (!pattern.wildcard) {
      const [start, end] = this.index.range(absolute);
      return end > start ? [absolute] : [];
    }
    const [start, end] = this.index.range(base + pattern.literalPrefix);
    const regex = pattern.instanceRegex;
    const found: string[] = [];
    let last = '';
    for (let i = start; i < end; i++) {
      const match = regex.exec(this.index.paths[i].slice(base.length));
      if (match === null) {
        continue;
      }
      const instance = base + match[1];
      if (instance !== last) {
        found.push(instance);
        last = instance;
      }
    }
    return found;
  }

  /** Returns an aggregate, computing it once for the whole run where it spans the document. */
  aggregate(key: string, remember: boolean, compute: () => RuleValue): RuleValue {
    if (!remember) {
      return compute();
    }
    const known = this.aggregates.get(key);
    if (known !== undefined) {
      return known;
    }
    const value = compute();
    this.aggregates.set(key, value);
    return value;
  }

  /** Returns a value a native rule computes once for the whole run. */
  sharedValue<T>(key: string, compute: () => T): T {
    if (this.shared.has(key)) {
      return this.shared.get(key) as T;
    }
    const value = compute();
    this.shared.set(key, value);
    return value;
  }

  /** Returns a pattern of a rule of this binding, compiled once for the whole run. */
  pattern(written: string): CompiledPattern {
    const known = this.patterns.get(written);
    if (known !== undefined) {
      return known;
    }
    const compiled = compilePattern(this.structure, [], written, 'a rule of this binding');
    this.patterns.set(written, compiled);
    return compiled;
  }

  /** Returns the code list snapshot the pack decides membership against. */
  codeList(listId: string): CodeList {
    const list = this.lists.get(listId);
    if (list === undefined) {
      throw new RulePackError('the pack carries no snapshot of the code list ' + listId);
    }
    return list;
  }
}

/** A pattern of the rule language, compiled against the registry. */
interface CompiledPattern {
  /** The pattern as it is written, relative to the context. */
  readonly text: string;
  /** Whether it carries an asterisk. */
  readonly wildcard: boolean;
  /** The literal part before the first asterisk, which is the range it can be sought in. */
  readonly literalPrefix: string;
  /** Matches a path relative to the context instance. */
  readonly valueRegex: RegExp;
  /** Matches a path and captures the instance the pattern addresses. */
  readonly instanceRegex: RegExp;
  /** The identifier the pattern ends at. */
  readonly last: string;
  /** Whether it ends at a business group. */
  readonly endsAtGroup: boolean;
}

const INDEX = '(?:0|[1-9][0-9]*)';

function compilePattern(
  structure: Structure, context: readonly string[], written: string, where: string,
): CompiledPattern {
  if (!written.startsWith('/')) {
    throw new RulePackError(where + ': ' + written + ' is not a path');
  }
  const tokens = written.slice(1).split('/');
  const steps: Array<{ id: string; wildcard: boolean }> = [];
  for (const token of tokens) {
    if (token === '*') {
      if (steps.length === 0 || steps[steps.length - 1].wildcard) {
        throw new RulePackError(where + ': ' + written + ' writes an asterisk where no'
          + ' repeatable term or group stands');
      }
      steps[steps.length - 1] = { id: steps[steps.length - 1].id, wildcard: true };
      continue;
    }
    steps.push({ id: token, wildcard: false });
  }
  for (const step of steps) {
    const term = structure.term(step.id);
    if (term === undefined) {
      throw new RulePackError(where + ': ' + written + ' names ' + step.id
        + ', which the registry does not carry');
    }
    if (structure.repeatable(step.id) !== step.wildcard) {
      throw new RulePackError(where + ': ' + written + ' writes ' + step.id
        + (step.wildcard ? ' with an asterisk, and it occurs at most once'
          : ' without an asterisk, and it is repeatable'));
    }
  }
  const groups = [...context, ...steps.slice(0, steps.length - 1).map((step) => step.id)];
  const last = steps[steps.length - 1];
  if (!structure.isChain(last.id, groups)) {
    throw new RulePackError(where + ': ' + written
      + ' is not a position the registry records for ' + last.id);
  }
  let pattern = '';
  let literalPrefix = '';
  let literal = true;
  let instancePattern = '';
  for (let i = 0; i < steps.length; i++) {
    const step = steps[i];
    pattern += '/' + step.id + (step.wildcard ? '/' + INDEX : '');
    if (literal) {
      literalPrefix += '/' + step.id;
    }
    if (step.wildcard) {
      literal = false;
    }
    if (i === steps.length - 1) {
      instancePattern = '(' + pattern + ')';
    }
  }
  return {
    text: written,
    wildcard: steps.some((step) => step.wildcard),
    literalPrefix,
    valueRegex: new RegExp('^' + pattern + '$'),
    instanceRegex: new RegExp('^' + instancePattern + '(?:/|$)'),
    last: last.id,
    endsAtGroup: structure.term(last.id)!.kind === 'BG',
  };
}

/** Converts the content of a value to what its semantic data type says it is. */
function convert(structure: Structure, termId: string, path: string, value: SemanticValue): RuleValue {
  const datatype = structure.term(termId)?.datatype ?? null;
  if (datatype !== null && ['Amount', 'UnitPriceAmount', 'Quantity', 'Percentage'].includes(datatype)) {
    if (!isDecimal(value.value)) {
      throw new Undecided('the value at ' + path + ' is not a ' + datatype + ': '
        + forMessage(value.value));
    }
    return { k: 'decimal', decimal: Decimal.of(value.value) };
  }
  if (datatype === 'Date') {
    if (!isDate(value.value)) {
      throw new Undecided('the value at ' + path + ' is not a Date: ' + forMessage(value.value));
    }
    return { k: 'date', text: value.value };
  }
  return { k: 'text', text: value.value };
}

function ruleTypeOf(datatype: string | null): RuleType {
  if (datatype !== null && ['Amount', 'UnitPriceAmount', 'Quantity', 'Percentage'].includes(datatype)) {
    return 'decimal';
  }
  return datatype === 'Date' ? 'date' : 'text';
}

/** The compiler: one rule file in, closures out. */
class Compiler {
  readonly structure: Structure;
  readonly lists: Map<string, CodeList>;

  constructor(structure: Structure, lists: Map<string, CodeList>) {
    this.structure = structure;
    this.lists = lists;
  }

  compile(written: unknown, scope: readonly string[], where: string): Compiled {
    if (typeof written !== 'object' || written === null || Array.isArray(written)) {
      throw new RulePackError(where + ': an expression is a JSON object with one member');
    }
    const members = Object.entries(written as Record<string, unknown>);
    if (members.length !== 1) {
      throw new RulePackError(where + ': an expression is a JSON object with exactly one member');
    }
    const [operator, argument] = members[0];
    switch (operator) {
      case 'value':
        return this.value(argument, scope, where);
      case 'const':
        return this.literal(argument, where);
      case 'exists':
        return this.presence(argument, scope, where, true);
      case 'absent':
        return this.presence(argument, scope, where, false);
      case 'eq': case 'ne': case 'lt': case 'le': case 'gt': case 'ge':
        return this.comparison(operator, argument, scope, where);
      case 'add': case 'sub': case 'mul': case 'div':
        return this.arithmetic(operator, argument, scope, where);
      case 'abs':
        return this.abs(argument, scope, where);
      case 'round':
        return this.round(argument, scope, where);
      case 'sum': case 'min': case 'max':
        return this.aggregate(operator, argument, scope, where);
      case 'count':
        return this.count(argument, scope, where);
      case 'inList':
        return this.inList(argument, scope, where);
      case 'matches':
        return this.matches(argument, scope, where);
      case 'len':
        return this.len(argument, scope, where);
      case 'decimals':
        return this.decimals(argument, scope, where);
      case 'and': case 'or':
        return this.junction(operator, argument, scope, where);
      case 'not':
        return this.negation(argument, scope, where);
      case 'if':
        return this.conditional(argument, scope, where);
      case 'forEach': case 'all': case 'any':
        return this.quantifier(operator, argument, scope, where);
      default:
        throw new RulePackError(where + ': ' + operator + ' is not an operator of this language');
    }
  }

  pattern(argument: unknown, scope: readonly string[], where: string): CompiledPattern {
    if (typeof argument !== 'string') {
      throw new RulePackError(where + ': a path is written as a string');
    }
    return compilePattern(this.structure, scope, argument, where);
  }

  private value(argument: unknown, scope: readonly string[], where: string): Compiled {
    const pattern = this.pattern(argument, scope, where);
    if (pattern.wildcard || pattern.endsAtGroup) {
      throw new RulePackError(where + ': value addresses one value of a business term');
    }
    const structure = this.structure;
    const type = ruleTypeOf(structure.term(pattern.last)?.datatype ?? null);
    return {
      type,
      literal: null,
      evaluate: (run, base) => {
        const path = base + pattern.text;
        const value = run.document.values.get(path);
        return value === undefined ? ABSENT : convert(structure, pattern.last, path, value);
      },
    };
  }

  private literal(argument: unknown, where: string): Compiled {
    if (typeof argument === 'string') {
      const value: RuleValue = { k: 'text', text: argument };
      return { type: 'any', literal: argument, evaluate: () => value };
    }
    if (typeof argument === 'number' && Number.isInteger(argument)) {
      const value: RuleValue = { k: 'decimal', decimal: Decimal.ofInteger(argument) };
      return { type: 'decimal', literal: null, evaluate: () => value };
    }
    if (typeof argument === 'boolean') {
      const value: RuleValue = { k: 'boolean', truth: argument };
      return { type: 'boolean', literal: null, evaluate: () => value };
    }
    throw new RulePackError(where + ': a literal is a string, a whole number or a truth value');
  }

  private presence(
    argument: unknown, scope: readonly string[], where: string, wanted: boolean,
  ): Compiled {
    const pattern = this.pattern(argument, scope, where);
    return {
      type: 'boolean',
      literal: null,
      evaluate: (run, base) => {
        const there = pattern.endsAtGroup
          ? run.instances(pattern, base).length > 0
          : run.matches(pattern, base).length > 0;
        return { k: 'boolean', truth: there === wanted };
      },
    };
  }

  private comparison(
    operator: string, argument: unknown, scope: readonly string[], where: string,
  ): Compiled {
    const operands = this.operands(argument, scope, where, 2, 2);
    const type = unify(operands[0].type, operands[1].type, where);
    if (type === 'boolean' && operator !== 'eq' && operator !== 'ne') {
      throw new RulePackError(where + ': a truth value is compared with eq or ne only');
    }
    const left = coerce(operands[0], type, where).evaluate;
    const right = coerce(operands[1], type, where).evaluate;
    return {
      type: 'boolean',
      literal: null,
      evaluate: (run, base) => {
        const a = left(run, base);
        const b = right(run, base);
        if (a.k === 'absent' || b.k === 'absent') {
          return ABSENT;
        }
        const order = compareValues(a, b);
        const truth = operator === 'eq' ? order === 0
          : operator === 'ne' ? order !== 0
            : operator === 'lt' ? order < 0
              : operator === 'le' ? order <= 0
                : operator === 'gt' ? order > 0 : order >= 0;
        return { k: 'boolean', truth };
      },
    };
  }

  private arithmetic(
    operator: string, argument: unknown, scope: readonly string[], where: string,
  ): Compiled {
    const binary = operator === 'sub' || operator === 'div';
    const operands = this.operands(argument, scope, where, 2, binary ? 2 : Number.MAX_SAFE_INTEGER);
    const parts = operands.map((operand) => coerce(operand, 'decimal', where).evaluate);
    return {
      type: 'decimal',
      literal: null,
      evaluate: (run, base) => {
        let total: Decimal | undefined;
        for (const part of parts) {
          const next = part(run, base);
          if (next.k === 'absent') {
            return ABSENT;
          }
          const number = numberOf(next);
          if (total === undefined) {
            total = number;
            continue;
          }
          total = operator === 'add' ? total.add(number)
            : operator === 'sub' ? total.subtract(number)
              : operator === 'mul' ? total.multiply(number) : total.divide(number);
          if (total === undefined) {
            return ABSENT;
          }
        }
        return { k: 'decimal', decimal: total! };
      },
    };
  }

  private abs(argument: unknown, scope: readonly string[], where: string): Compiled {
    const value = coerce(this.compile(argument, scope, where), 'decimal', where).evaluate;
    return {
      type: 'decimal',
      literal: null,
      evaluate: (run, base) => {
        const number = value(run, base);
        return number.k === 'absent'
          ? ABSENT
          : { k: 'decimal', decimal: numberOf(number).abs() };
      },
    };
  }

  private round(argument: unknown, scope: readonly string[], where: string): Compiled {
    const parts = asArray(argument, where);
    if (parts.length !== 2) {
      throw new RulePackError(where + ': round takes an expression and a scale');
    }
    const value = coerce(this.compile(parts[0], scope, where), 'decimal', where).evaluate;
    const scale = asScale(parts[1], where);
    return {
      type: 'decimal',
      literal: null,
      evaluate: (run, base) => {
        const number = value(run, base);
        return number.k === 'absent'
          ? ABSENT
          : { k: 'decimal', decimal: numberOf(number).round(scale) };
      },
    };
  }

  private aggregate(
    operator: string, argument: unknown, scope: readonly string[], where: string,
  ): Compiled {
    if (typeof argument === 'string') {
      return this.overPattern(operator, argument, scope, where);
    }
    const operands = this.operands(argument, scope, where, 1, Number.MAX_SAFE_INTEGER);
    const parts = operands.map((operand) => coerce(operand, 'decimal', where).evaluate);
    return {
      type: 'decimal',
      literal: null,
      evaluate: (run, base) => {
        const numbers: Decimal[] = [];
        for (const part of parts) {
          const next = part(run, base);
          if (next.k === 'absent') {
            return ABSENT;
          }
          numbers.push(numberOf(next));
        }
        return fold(operator, numbers);
      },
    };
  }

  private overPattern(
    operator: string, written: string, scope: readonly string[], where: string,
  ): Compiled {
    const pattern = this.pattern(written, scope, where);
    if (pattern.endsAtGroup) {
      throw new RulePackError(where + ': ' + written
        + ' addresses business group instances, and ' + operator + ' takes values');
    }
    const structure = this.structure;
    if (ruleTypeOf(structure.term(pattern.last)?.datatype ?? null) !== 'decimal') {
      throw new RulePackError(where + ': ' + pattern.last + ' is not a numeric term, and '
        + operator + ' takes one');
    }
    const rootBased = scope.length === 0;
    return {
      type: 'decimal',
      literal: null,
      evaluate: (run, base) => run.aggregate(operator + ' ' + base + pattern.text, rootBased, () => {
        const numbers: Decimal[] = [];
        for (const [path, value] of run.matches(pattern, base)) {
          numbers.push(numberOf(convert(structure, pattern.last, path, value)));
        }
        return fold(operator, numbers);
      }),
    };
  }

  private count(argument: unknown, scope: readonly string[], where: string): Compiled {
    const pattern = this.pattern(argument, scope, where);
    const rootBased = scope.length === 0;
    return {
      type: 'decimal',
      literal: null,
      evaluate: (run, base) => run.aggregate('count ' + base + pattern.text, rootBased, () => ({
        k: 'decimal',
        decimal: Decimal.ofInteger(pattern.endsAtGroup
          ? run.instances(pattern, base).length
          : run.matches(pattern, base).length),
      })),
    };
  }

  private inList(argument: unknown, scope: readonly string[], where: string): Compiled {
    const parts = asArray(argument, where);
    if (parts.length !== 2) {
      throw new RulePackError(where + ': inList takes an expression and a list identifier');
    }
    const value = coerce(this.compile(parts[0], scope, where), 'text', where).evaluate;
    const listId = parts[1];
    if (typeof listId !== 'string' || !this.lists.has(listId)) {
      throw new RulePackError(where + ': the pack carries no snapshot of the code list '
        + String(listId));
    }
    return {
      type: 'boolean',
      literal: null,
      evaluate: (run, base) => {
        const code = value(run, base);
        return code.k === 'absent'
          ? ABSENT
          : { k: 'boolean', truth: run.codeList(listId).contains(textOf(code)) };
      },
    };
  }

  private matches(argument: unknown, scope: readonly string[], where: string): Compiled {
    const parts = asArray(argument, where);
    if (parts.length !== 2) {
      throw new RulePackError(where + ': matches takes an expression and a pattern');
    }
    const value = coerce(this.compile(parts[0], scope, where), 'text', where).evaluate;
    if (typeof parts[1] !== 'string') {
      throw new RulePackError(where + ': the pattern of matches is a string');
    }
    let regex: RegExp;
    try {
      regex = new RegExp('^(?:' + parts[1] + ')$', 'u');
    } catch (failure) {
      throw new RulePackError(where + ': ' + parts[1] + ' is not a pattern: '
        + (failure as Error).message);
    }
    return {
      type: 'boolean',
      literal: null,
      evaluate: (run, base) => {
        const text = value(run, base);
        return text.k === 'absent' ? ABSENT : { k: 'boolean', truth: regex.test(textOf(text)) };
      },
    };
  }

  private len(argument: unknown, scope: readonly string[], where: string): Compiled {
    const value = coerce(this.compile(argument, scope, where), 'text', where).evaluate;
    return {
      type: 'decimal',
      literal: null,
      evaluate: (run, base) => {
        const text = value(run, base);
        return text.k === 'absent'
          ? ABSENT
          : { k: 'decimal', decimal: Decimal.ofInteger(codePointCount(textOf(text))) };
      },
    };
  }

  /**
   * Compiles `decimals`, which asks how many fraction digits a number needs.
   *
   * It applies to Amount alone. EN 16931 leaves Unit Price Amount, Quantity and Percentage
   * unbounded on purpose, so a rule that capped the scale of one of them is a defect of the
   * pack and is refused here rather than on the first invoice that carries many digits.
   */
  private decimals(argument: unknown, scope: readonly string[], where: string): Compiled {
    const parts = asArray(argument, where);
    if (parts.length !== 2) {
      throw new RulePackError(where + ': decimals takes a path and a maximum scale');
    }
    const pattern = this.pattern(parts[0], scope, where);
    if (pattern.wildcard || pattern.endsAtGroup) {
      throw new RulePackError(where + ': decimals addresses one value of a business term');
    }
    const structure = this.structure;
    if (structure.term(pattern.last)?.datatype !== 'Amount') {
      throw new RulePackError(where + ': ' + pattern.last + ' is not an Amount, and decimals'
        + ' applies to Amount alone');
    }
    const maxScale = asScale(parts[1], where);
    return {
      type: 'boolean',
      literal: null,
      evaluate: (run, base) => {
        const path = base + pattern.text;
        const value = run.document.values.get(path);
        if (value === undefined) {
          return ABSENT;
        }
        const number = convert(structure, pattern.last, path, value);
        return { k: 'boolean', truth: numberOf(number).decimals() <= maxScale };
      },
    };
  }

  /**
   * Compiles `and` and `or`.
   *
   * An operand that is absent does not decide, and the junction is absent unless another
   * operand does decide it: `and` is false as soon as one operand is false, `or` is true as
   * soon as one is true, and an undecided operand otherwise makes the whole undecided.
   */
  private junction(
    operator: string, argument: unknown, scope: readonly string[], where: string,
  ): Compiled {
    const operands = this.operands(argument, scope, where, 2, Number.MAX_SAFE_INTEGER);
    const parts = operands.map((operand) => coerce(operand, 'boolean', where).evaluate);
    const conjunction = operator === 'and';
    return {
      type: 'boolean',
      literal: null,
      evaluate: (run, base) => {
        let undecided = false;
        for (const part of parts) {
          const next = part(run, base);
          if (next.k === 'absent') {
            undecided = true;
            continue;
          }
          if (truthOf(next) !== conjunction) {
            return { k: 'boolean', truth: !conjunction };
          }
        }
        return undecided ? ABSENT : { k: 'boolean', truth: conjunction };
      },
    };
  }

  private negation(argument: unknown, scope: readonly string[], where: string): Compiled {
    const value = coerce(this.compile(argument, scope, where), 'boolean', where).evaluate;
    return {
      type: 'boolean',
      literal: null,
      evaluate: (run, base) => {
        const truth = value(run, base);
        return truth.k === 'absent' ? ABSENT : { k: 'boolean', truth: !truthOf(truth) };
      },
    };
  }

  private conditional(argument: unknown, scope: readonly string[], where: string): Compiled {
    const members = asObject(argument, where);
    reject(Object.keys(members), ['condition', 'then', 'else'], where, 'if');
    if (members.condition === undefined || members.then === undefined) {
      throw new RulePackError(where + ': if takes a condition and a then');
    }
    const test = coerce(this.compile(members.condition, scope, where), 'boolean', where).evaluate;
    const consequent = this.compile(members.then, scope, where);
    const alternative = 'else' in members
      ? this.compile(members.else, scope, where)
      : { type: 'boolean' as RuleType, literal: null, evaluate: () => ({ k: 'boolean', truth: true } as RuleValue) };
    const type = unify(consequent.type, alternative.type, where);
    const yes = coerce(consequent, type, where).evaluate;
    const no = coerce(alternative, type, where).evaluate;
    return {
      type,
      literal: null,
      evaluate: (run, base) => {
        const truth = test(run, base);
        return truth.k === 'absent' ? ABSENT : truthOf(truth) ? yes(run, base) : no(run, base);
      },
    };
  }

  /**
   * Compiles `forEach`, `all` and `any`. Inside a quantifier the paths are relative to the
   * instance, so the scope grows by the groups of the quantified pattern.
   */
  private quantifier(
    operator: string, argument: unknown, scope: readonly string[], where: string,
  ): Compiled {
    const members = asObject(argument, where);
    reject(Object.keys(members), ['group', 'assert'], where, operator);
    if (members.group === undefined || members.assert === undefined) {
      throw new RulePackError(where + ': ' + operator + ' takes a group and an assert');
    }
    const pattern = this.pattern(members.group, scope, where);
    if (!pattern.endsAtGroup) {
      throw new RulePackError(where + ': ' + operator + ' quantifies over business group'
        + ' instances, and ' + pattern.text + ' addresses a value');
    }
    const inner = [...scope, ...pattern.text.slice(1).split('/').filter((token) => token !== '*')];
    const assertion = coerce(this.compile(members.assert, inner, where), 'boolean', where).evaluate;
    const existential = operator === 'any';
    return {
      type: 'boolean',
      literal: null,
      evaluate: (run, base) => {
        let undecided = false;
        for (const instance of run.instances(pattern, base)) {
          const next = assertion(run, instance);
          if (next.k === 'absent') {
            undecided = true;
            continue;
          }
          if (truthOf(next) === existential) {
            return { k: 'boolean', truth: existential };
          }
        }
        return undecided ? ABSENT : { k: 'boolean', truth: !existential };
      },
    };
  }

  private operands(
    argument: unknown, scope: readonly string[], where: string, least: number, most: number,
  ): Compiled[] {
    const parts = asArray(argument, where);
    if (parts.length < least || parts.length > most) {
      throw new RulePackError(where + ': this operator takes between ' + least + ' and '
        + most + ' operands');
    }
    return parts.map((part) => this.compile(part, scope, where));
  }
}

function fold(operator: string, numbers: Decimal[]): RuleValue {
  if (numbers.length === 0) {
    return operator === 'sum' ? { k: 'decimal', decimal: Decimal.ZERO } : ABSENT;
  }
  let result = numbers[0];
  for (let i = 1; i < numbers.length; i++) {
    const next = numbers[i];
    result = operator === 'sum' ? result.add(next)
      : operator === 'min' ? (result.compare(next) <= 0 ? result : next)
        : (result.compare(next) >= 0 ? result : next);
  }
  return { k: 'decimal', decimal: result };
}

function compareValues(a: RuleValue, b: RuleValue): number {
  if (a.k === 'decimal' && b.k === 'decimal') {
    return a.decimal.compare(numberOf(b));
  }
  if (a.k === 'boolean') {
    return a.truth === truthOf(b) ? 0 : a.truth ? 1 : -1;
  }
  const left = textOf(a);
  const right = textOf(b);
  const x = [...left];
  const y = [...right];
  const shared = Math.min(x.length, y.length);
  for (let i = 0; i < shared; i++) {
    const p = x[i].codePointAt(0)!;
    const q = y[i].codePointAt(0)!;
    if (p !== q) {
      return p < q ? -1 : 1;
    }
  }
  return x.length - y.length;
}

function unify(left: RuleType, right: RuleType, where: string): RuleType {
  if (left === right) {
    return left === 'any' ? 'text' : left;
  }
  if (left === 'any') {
    return right;
  }
  if (right === 'any') {
    return left;
  }
  throw new RulePackError(where + ': ' + left + ' and ' + right
    + ' are not compared or combined in this language');
}

/**
 * Gives a literal the type it stands beside: a literal takes its type from its neighbour,
 * and a literal that is not the number or the date it is compared against is a defect of the
 * pack, found once when the pack is compiled.
 */
function coerce(compiled: Compiled, target: RuleType, where: string): Compiled {
  if (compiled.type === target) {
    return compiled;
  }
  if (compiled.type !== 'any' || compiled.literal === null) {
    throw new RulePackError(where + ': ' + compiled.type + ' stands where ' + target + ' belongs');
  }
  const text = compiled.literal;
  if (target === 'decimal') {
    if (!isDecimal(text)) {
      throw new RulePackError(where + ': the literal ' + text
        + ' stands beside a decimal and is not one');
    }
    const value: RuleValue = { k: 'decimal', decimal: Decimal.of(text) };
    return { type: target, literal: null, evaluate: () => value };
  }
  if (target === 'date') {
    if (!isDate(text)) {
      throw new RulePackError(where + ': the literal ' + text
        + ' stands beside a date and is not one');
    }
    const value: RuleValue = { k: 'date', text };
    return { type: target, literal: null, evaluate: () => value };
  }
  if (target === 'text') {
    const value: RuleValue = { k: 'text', text };
    return { type: target, literal: null, evaluate: () => value };
  }
  throw new RulePackError(where + ': the literal ' + text + ' stands where ' + target + ' belongs');
}

function asArray(argument: unknown, where: string): unknown[] {
  if (!Array.isArray(argument)) {
    throw new RulePackError(where + ': this operator takes a list');
  }
  return argument;
}

function asObject(argument: unknown, where: string): Record<string, unknown> {
  if (typeof argument !== 'object' || argument === null || Array.isArray(argument)) {
    throw new RulePackError(where + ': this operator takes a JSON object');
  }
  return argument as Record<string, unknown>;
}

function asScale(argument: unknown, where: string): number {
  if (typeof argument !== 'number' || !Number.isInteger(argument) || argument < 0 || argument > 34) {
    throw new RulePackError(where + ': a scale is a whole number between 0 and 34');
  }
  return argument;
}

function reject(
  given: readonly string[], allowed: readonly string[], where: string, operator: string,
): void {
  for (const name of given) {
    if (!allowed.includes(name)) {
      throw new RulePackError(where + ': ' + operator + ' does not take a member ' + name);
    }
  }
}

/** What a rule written in this binding reads the invoice through. */
export class RuleContext {
  private readonly run: Run;
  private readonly base: string;

  constructor(run: Run, base: string) {
    this.run = run;
    this.base = base;
  }

  /** The business group instance the rule is looking at; the empty string for the document. */
  get context(): string {
    return this.base;
  }

  /** Returns the values a pattern addresses, with their paths, in canonical order. */
  values(pattern: string): Array<[string, SemanticValue]> {
    return this.run.matches(this.compiled(pattern), this.base);
  }

  /** Returns the contents a pattern addresses, as text. */
  texts(pattern: string): Array<[string, string]> {
    return this.values(pattern).map(([path, value]) => [path, value.value]);
  }

  /**
   * Returns the numbers a pattern addresses.
   *
   * @throws Undecided if a value at one of those paths does not spell a decimal
   */
  decimals(pattern: string): Array<[string, Decimal]> {
    const compiled = this.compiled(pattern);
    return this.run.matches(compiled, this.base).map(([path, value]) =>
      [path, numberOf(convert(this.run.structure, compiled.last, path, value))]);
  }

  /** Returns the business group instances a pattern addresses. */
  instances(pattern: string): string[] {
    return this.run.instances(this.compiled(pattern), this.base);
  }

  /** Returns the code list snapshot the pack decides membership against. */
  codeList(listId: string): CodeList {
    return this.run.codeList(listId);
  }

  /** Returns a value computed once for the whole run, however many rules ask for it. */
  shared<T>(key: string, compute: () => T): T {
    return this.run.sharedValue(key, compute);
  }

  /** Escapes a fragment of the document for a message (specification, section 9.5). */
  escape(text: string): string {
    return forMessage(text);
  }

  private compiled(pattern: string): CompiledPattern {
    return this.run.pattern(pattern);
  }
}

/** A pack compiled against one structure, ready to run over documents. */
export class RuleEngine {
  /** The identifier of the pack. */
  readonly id: string;

  /** The version of the pack. */
  readonly version: string;

  private readonly rules: Rule[];
  private readonly structure: Structure;
  private readonly lists: Map<string, CodeList>;

  constructor(id: string, version: string, rules: Rule[], structure: Structure,
    lists: Map<string, CodeList>) {
    this.id = id;
    this.version = version;
    this.rules = rules;
    this.structure = structure;
    this.lists = lists;
  }

  /**
   * Runs every rule of the pack over a document.
   *
   * A value that does not spell what its semantic data type requires stops the rule that
   * reads it: the defect belongs to the value, a structural layer has already named it, and
   * reporting it a second time as a failed business rule would make one problem look like
   * two. Such a rule reports one `info` finding and decides nothing.
   *
   * The document has to name the edition this engine was compiled for. A pack states rules
   * about the business terms of one edition and a path is an address relative to an edition
   * (section 10), so a pack run over a document of another one would answer with rule
   * identifiers that say nothing true about it. The engine refuses instead, and a caller
   * with no pack for the edition it holds reports that nothing was checked rather than a
   * verdict.
   *
   * @param document the document; it is not changed
   * @return the findings, ordered by rule identifier and then by context
   * @throws Error where the document names another edition than the pack was compiled for
   */
  evaluate(document: SemanticDocument): RuleFinding[] {
    if (document.semanticModel !== this.structure.core.semanticModel) {
      throw new Error('this pack states rules about ' + this.structure.core.semanticModel
        + ' and the document names ' + document.semanticModel);
    }
    const run = new Run(document, this.structure, this.lists);
    const findings: RuleFinding[] = [];
    for (const rule of this.rules) {
      for (const base of this.bases(rule, run)) {
        try {
          const outcome = rule.decide(run, base);
          if (outcome !== undefined) {
            findings.push({
              rule: rule.id,
              severity: outcome.severity,
              context: base,
              message: outcome.message,
              pack: this.id,
              version: this.version,
            });
          }
        } catch (failure) {
          if (!(failure instanceof Undecided)) {
            throw failure;
          }
          findings.push({
            rule: rule.id,
            severity: 'info',
            context: base,
            message: 'not decided: ' + failure.message,
            pack: this.id,
            version: this.version,
          });
        }
      }
    }
    findings.sort((left, right) => left.rule < right.rule ? -1 : left.rule > right.rule ? 1
      : left.context < right.context ? -1 : left.context > right.context ? 1 : 0);
    return findings;
  }

  private bases(rule: Rule, run: Run): string[] {
    if (rule.context === null) {
      return [''];
    }
    return run.instances(compilePattern(this.structure, [], rule.context, 'the context'), '');
  }
}

/**
 * Compiles a pack against a structure.
 *
 * Every path of every rule is resolved against the registry here, so that a rule naming a
 * term nobody has is a defect of the pack found once rather than a surprise on one invoice.
 * The rules the pack declares the language cannot express are passed in: a manifest that
 * could name code the engine then loads would be a file that decides what runs.
 *
 * @param pack the manifest with the rules of every file it names and its code list snapshots
 * @param structure the terms of the edition the documents name
 * @param natives the rules written in this binding, one per identifier the pack declares
 * @return the engine
 * @throws RulePackError if the pack cannot be compiled
 */
export function compile(
  pack: RulePackFile, structure: Structure, natives: readonly NativeRule[] = [],
): RuleEngine {
  const lists = new Map<string, CodeList>();
  for (const [listId, file] of Object.entries(pack.lists ?? {} as Record<string, CodeListFile>)) {
    lists.set(listId, new CodeList(file));
  }
  const compiler = new Compiler(structure, lists);
  const rules: Rule[] = [];
  const seen = new Set<string>();
  for (const definition of pack.rules ?? []) {
    if (seen.has(definition.id)) {
      throw new RulePackError('the pack ' + pack.id + ' carries the rule '
        + definition.id + ' twice');
    }
    seen.add(definition.id);
    rules.push(language(definition, compiler, structure));
  }
  const declared = (pack.javaRules ?? []).map(ruleIdOfDeclared);
  const supplied = natives.map((rule) => rule.id);
  for (const id of declared) {
    if (!supplied.includes(id)) {
      throw new RulePackError('the pack ' + pack.id + ' declares the rule ' + id
        + ' as one the rule language cannot express, and no rule of that identifier arrived');
    }
  }
  for (const rule of natives) {
    if (!declared.includes(rule.id)) {
      throw new RulePackError('a rule of the identifier ' + rule.id
        + ' arrived, and the pack ' + pack.id + ' declares no such rule');
    }
    if (seen.has(rule.id)) {
      throw new RulePackError('the pack ' + pack.id + ' carries the rule ' + rule.id + ' twice');
    }
    seen.add(rule.id);
    rules.push(native(rule, structure));
  }
  return new RuleEngine(pack.id, pack.version, rules, structure, lists);
}

function contextOf(definition: { context: string }): string | null {
  return definition.context === '/' ? null : definition.context;
}

function scopeOf(context: string | null): string[] {
  return context === null
    ? []
    : context.slice(1).split('/').filter((token) => token !== '*');
}

function language(
  definition: RuleDefinition, compiler: Compiler, structure: Structure,
): Rule {
  const where = 'the rule ' + definition.id;
  const context = contextOf(definition);
  if (context !== null) {
    const pattern = compilePattern(structure, [], context, where + ', context');
    if (!pattern.endsAtGroup) {
      throw new RulePackError(where + ': a context is the document or a business group');
    }
  }
  const scope = scopeOf(context);
  const bindings = new Map<string, Evaluate>();
  for (const [name, expression] of Object.entries(definition.bind ?? {})) {
    bindings.set(name, compiler.compile(expression, scope, where + ', bind ' + name).evaluate);
  }
  const assertion = assertionOf(compiler, definition.assert, scope, where);
  const message = template(compiler, definition.message, scope, bindings, where + ', message');
  const second = definition.warn === undefined
    ? undefined
    : assertionOf(compiler, definition.warn.assert, scope, where + ', warn');
  const secondMessage = definition.warn === undefined
    ? undefined
    : template(compiler, definition.warn.message, scope, bindings, where + ', warn message');
  return {
    id: definition.id,
    severity: definition.severity,
    context,
    decide: (run, base) => {
      if (!holds(assertion, run, base)) {
        return { severity: definition.severity, message: message(run, base) };
      }
      if (second !== undefined && !holds(second, run, base)) {
        return { severity: 'warning', message: secondMessage!(run, base) };
      }
      return undefined;
    },
  };
}

function native(rule: NativeRule, structure: Structure): Rule {
  const where = 'the rule ' + rule.id;
  const context = contextOf(rule);
  if (context !== null) {
    const pattern = compilePattern(structure, [], context, where + ', context');
    if (!pattern.endsAtGroup) {
      throw new RulePackError(where + ': a context is the document or a business group');
    }
  }
  return {
    id: rule.id,
    severity: 'fatal',
    context,
    decide: (run, base) => {
      const ruleContext = new RuleContext(run, base);
      const failed = rule.check(ruleContext);
      if (failed !== undefined) {
        return { severity: 'fatal', message: failed };
      }
      const noted = rule.warn?.(ruleContext);
      return noted === undefined ? undefined : { severity: 'warning', message: noted };
    },
  };
}

function assertionOf(
  compiler: Compiler, written: Expression, scope: readonly string[], where: string,
): Evaluate {
  const compiled = compiler.compile(written, scope, where);
  if (compiled.type !== 'boolean') {
    throw new RulePackError(where + ': an assertion is a truth value, and this one is '
      + compiled.type);
  }
  return compiled.evaluate;
}

/** Tells whether an assertion holds, which it does where it cannot be decided. */
function holds(assertion: Evaluate, run: Run, base: string): boolean {
  const truth = assertion(run, base);
  return truth.k === 'absent' || truthOf(truth);
}

/**
 * Compiles a message template.
 *
 * Four placeholders make a finding say what happened: `{path}` is the value at that path,
 * `{@path}` the path itself, `{$name}` an expression the rule bound under that name, and
 * `{.}` the business group instance the rule is looking at. Every path is resolved when the
 * pack is compiled, so a message naming a term nobody has is a defect of the pack.
 */
function template(
  compiler: Compiler, written: string, scope: readonly string[],
  bindings: Map<string, Evaluate>, where: string,
): (run: Run, base: string) => string {
  const parts: Array<string | ((run: Run, base: string) => string)> = [];
  let plain = '';
  for (let i = 0; i < written.length; i++) {
    const c = written.charAt(i);
    if (c === '{' && written.charAt(i + 1) === '{') {
      plain += '{';
      i++;
      continue;
    }
    if (c === '}' && written.charAt(i + 1) === '}') {
      plain += '}';
      i++;
      continue;
    }
    if (c !== '{') {
      plain += c;
      continue;
    }
    const end = written.indexOf('}', i);
    if (end < 0) {
      throw new RulePackError(where + ': a placeholder is not closed');
    }
    const token = written.slice(i + 1, end);
    i = end;
    if (plain !== '') {
      parts.push(plain);
      plain = '';
    }
    parts.push(placeholder(compiler, token, scope, bindings, where));
  }
  if (plain !== '') {
    parts.push(plain);
  }
  return (run, base) => parts
    .map((part) => typeof part === 'string' ? part : part(run, base))
    .join('');
}

function placeholder(
  compiler: Compiler, token: string, scope: readonly string[],
  bindings: Map<string, Evaluate>, where: string,
): (run: Run, base: string) => string {
  if (token === '.') {
    return (_run, base) => base === '' ? '/' : base;
  }
  if (token.startsWith('$')) {
    const bound = bindings.get(token.slice(1));
    if (bound === undefined) {
      throw new RulePackError(where + ': the rule binds no expression named ' + token.slice(1));
    }
    return (run, base) => show(bound(run, base));
  }
  if (token.startsWith('@')) {
    const pattern = compiler.pattern(token.slice(1), scope, where);
    return (_run, base) => base + pattern.text;
  }
  const pattern = compiler.pattern(token, scope, where);
  return (run, base) => {
    const value = run.document.values.get(base + pattern.text);
    return value === undefined ? '(absent)' : forMessage(value.value);
  };
}

function show(value: RuleValue): string {
  switch (value.k) {
    case 'absent':
      return '(absent)';
    case 'decimal':
      return value.decimal.toString();
    case 'boolean':
      return value.truth ? 'true' : 'false';
    default:
      return forMessage(textOf(value));
  }
}
