/**
 * A rule pack as its files write it (`rules/README.md`).
 *
 * The business rules of EN 16931 are not part of ESJ conformance (specification,
 * section 9.4): they are a layer of their own, carried by versioned packs whose findings
 * name the pack and never the format. A pack is data — JSON rules over business terms, and
 * dated snapshots of the code lists it decides membership against — which is what lets an
 * implementation in another language run the same pack over the same document.
 */

/** An expression of the rule language: a JSON object with exactly one member. */
export type Expression = Record<string, unknown>;

/** How much a rule weighs; `info` is the engine's and says that a rule was not decided. */
export type RuleSeverity = 'fatal' | 'warning' | 'info';

/**
 * What stands behind a rule: an official validation artefact of the edition it is written for
 * (`artefact`), the artefact of an earlier edition over the document written down to that
 * edition (`downgrade`), or hand-computed cases alone (`cases`).
 */
export type RuleOracle = 'artefact' | 'downgrade' | 'cases';

/** The oracles a pack may declare, in the order `rules/README.md` names them. */
export const RULE_ORACLES: readonly RuleOracle[] = ['artefact', 'downgrade', 'cases'];

/** The second assertion of a rule, weighed only where the first one holds. */
export interface RuleWarning {
  /** What is noted where it does not hold. */
  readonly assert: Expression;
  /** What the warning says. */
  readonly message: string;
}

/**
 * The case in which a rule is not decided: a figure the document may state and the rule has no
 * answer for. Weighed before the assertion; where it is true the rule reports that it was not
 * decided, with the reason, and weighs nothing else.
 */
export interface RuleUndecided {
  /** The case, a truth value; a condition that cannot be decided is not true. */
  readonly when: Expression;
  /** Why the rule is not decided in that case. */
  readonly message: string;
}

/** One rule written in the rule language. */
export interface RuleDefinition {
  /** The identifier the standard gives the rule; it is the code of every finding. */
  readonly id: string;
  /** `fatal` or `warning`. */
  readonly severity: 'fatal' | 'warning';
  /** What stands behind the rule. */
  readonly oracle: RuleOracle;
  /** What the rule is a statement about: `/`, or a business group pattern. */
  readonly context: string;
  /** The business terms and groups the rule reads; documentation. */
  readonly terms?: readonly string[];
  /** What must hold; a finding is produced where it is false. */
  readonly assert: Expression;
  /** A second assertion whose failure is a warning and decides no verdict. */
  readonly warn?: RuleWarning;
  /** The case in which the rule is not decided. */
  readonly undecided?: RuleUndecided;
  /** Expressions the message may show under a name. */
  readonly bind?: Record<string, Expression>;
  /** What the finding says. */
  readonly message: string;
  /** The clause of the standard the rule states. */
  readonly source?: string;
  /** Why the rule reads the way it does, where the statement admits more than one reading. */
  readonly note?: string;
}

/** A rule file: an array of rules. */
export type RuleFile = RuleDefinition[];

/** One entry of a code list snapshot. */
export interface CodeListEntry {
  /** The code. */
  readonly value: string;
  /** What the code means, where the publisher gives a name. */
  readonly name?: string;
  /** The number of fraction digits the publisher gives the code, for a currency list. */
  readonly minorUnit?: number;
}

/** A dated snapshot of one code list. */
export interface CodeListFile {
  /** The identifier a rule names the list by. */
  readonly listId: string;
  /** The name of the list. */
  readonly name?: string;
  /** Who publishes it. */
  readonly publisher?: string;
  /** The day the snapshot was taken. */
  readonly retrieved?: string;
  /** The codes the list held on that day. */
  readonly entries: readonly CodeListEntry[];
}

/**
 * A code list snapshot that lies in another pack: the day, and the pack it is read from,
 * written `id/version`.
 */
export interface SnapshotReference {
  readonly day: string;
  readonly from: string;
}

/** A rule the language cannot express, named by the class of the reference implementation. */
export interface JavaRuleReference {
  /** The class, whose simple name spells the rule identifier. */
  readonly class: string;
  /** What stands behind the rule. */
  readonly oracle: RuleOracle;
}

/**
 * Named rules of another pack's rule file, taken over by identifier rather than copied. Each
 * takes the oracle of the share in place of the one it carries in its own pack.
 */
export interface RuleShare {
  readonly pack: string;
  readonly version: string;
  readonly file: string;
  readonly oracle: RuleOracle;
  readonly rules: readonly string[];
}

/** A rule pack manifest, with the rules of every file it names read into it. */
export interface RulePackFile {
  /** The pack, which appears in every finding it produces. */
  readonly id: string;
  /** The version, which is never edited once released. */
  readonly version: string;
  /** The edition of the semantic model the rules are addresses in, as its registry spells it. */
  readonly edition: string;
  /** The release of the official artefacts this pack's behaviour was measured against. */
  readonly verifiedAgainst?: string;
  /** What the pack is. */
  readonly description?: string;
  /**
   * Which day's snapshot of each code list the pack decides membership against, or where a
   * snapshot lies in another pack, that pack and the day.
   */
  readonly codeLists?: Record<string, string | SnapshotReference>;
  /**
   * The rules the language cannot express, named by the class of the reference
   * implementation.
   *
   * Naming is not loading: a pack may arrive from a directory a caller was handed, so an
   * implementation passes its own rules in and the compiler checks that exactly the declared
   * identifiers arrived.
   */
  readonly javaRules?: readonly JavaRuleReference[];
  /** The rule files the pack is made of, each a path relative to the manifest. */
  readonly files?: readonly string[];
  /** Rules of other packs this one takes over; the loader reads them into {@link rules}. */
  readonly shares?: readonly RuleShare[];
  /** The rules written in this language. */
  readonly rules?: readonly RuleDefinition[];
  /** The code list snapshots, by list identifier; filled by the loader. */
  readonly lists?: Record<string, CodeListFile>;
}

/** A code list snapshot, ready to be asked about a code. */
export class CodeList {
  /** The identifier a rule names the list by. */
  readonly id: string;

  private readonly codes: Set<string>;

  private readonly minorUnits: Map<string, number>;

  constructor(file: CodeListFile) {
    this.id = file.listId;
    this.codes = new Set(file.entries.map((entry) => entry.value));
    this.minorUnits = new Map(file.entries
      .filter((entry) => entry.minorUnit !== undefined)
      .map((entry) => [entry.value, entry.minorUnit!]));
  }

  /** Tells whether the snapshot carries that code. */
  contains(code: string): boolean {
    return this.codes.has(code);
  }

  /**
   * Returns the number of fraction digits the publisher gives a code, or `undefined` where the
   * list publishes none for it. One list of the repository publishes such a number: the
   * currency list, whose minor unit says how many fraction digits an amount carries.
   */
  minorUnit(code: string): number | undefined {
    return this.minorUnits.get(code);
  }

  /** Tells whether the list publishes a number of fraction digits for any code. */
  get publishesMinorUnits(): boolean {
    return this.minorUnits.size > 0;
  }

  /** How many codes the snapshot carries. */
  get size(): number {
    return this.codes.size;
  }
}

/** What a pack raises where it cannot be compiled. It is never a statement about an invoice. */
export class RulePackError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'RulePackError';
  }
}

/**
 * Returns the rule identifier the reference implementation's class name stands for:
 * `Br62` is `BR-62`, `BrCl07` is `BR-CL-07`, `BrAe08` is `BR-AE-08`.
 *
 * The manifest names those classes because it was written beside them, and the identifier is
 * what a pack is actually about: a rule expressed in Java there and in TypeScript here is one
 * rule, and the finding it produces carries the identifier either way. This is the mapping
 * that lets the compiler check that a binding supplies exactly the set of rules the pack
 * declares the language cannot express.
 *
 * @param className the class the manifest names, with or without its package
 * @return the rule identifier
 */
export function ruleIdOfDeclared(className: string): string {
  const simple = className.slice(className.lastIndexOf('.') + 1);
  if (!simple.startsWith('Br')) {
    throw new RulePackError('the pack declares ' + className
      + ', which is not a rule identifier this loader can read');
  }
  const parts = simple.slice(2).match(/[A-Za-z]+|[0-9]+/g) ?? [];
  return ['BR', ...parts.map((part) => part.toUpperCase())].join('-');
}
