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

/** The second assertion of a rule, weighed only where the first one holds. */
export interface RuleWarning {
  /** What is noted where it does not hold. */
  readonly assert: Expression;
  /** What the warning says. */
  readonly message: string;
}

/** One rule written in the rule language. */
export interface RuleDefinition {
  /** The identifier the standard gives the rule; it is the code of every finding. */
  readonly id: string;
  /** `fatal` or `warning`. */
  readonly severity: 'fatal' | 'warning';
  /** What the rule is a statement about: `/`, or a business group pattern. */
  readonly context: string;
  /** The business terms and groups the rule reads; documentation. */
  readonly terms?: readonly string[];
  /** What must hold; a finding is produced where it is false. */
  readonly assert: Expression;
  /** A second assertion whose failure is a warning and decides no verdict. */
  readonly warn?: RuleWarning;
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

/** A rule pack manifest, with the rules of every file it names read into it. */
export interface RulePackFile {
  /** The pack, which appears in every finding it produces. */
  readonly id: string;
  /** The version, which is never edited once released. */
  readonly version: string;
  /** The release of the official artefacts this pack's behaviour was measured against. */
  readonly verifiedAgainst?: string;
  /** What the pack is. */
  readonly description?: string;
  /** Which day's snapshot of each code list the pack decides membership against. */
  readonly codeLists?: Record<string, string>;
  /**
   * The rules the language cannot express, named by the class of the reference
   * implementation.
   *
   * Naming is not loading: a pack may arrive from a directory a caller was handed, so an
   * implementation passes its own rules in and the compiler checks that exactly the declared
   * identifiers arrived.
   */
  readonly javaRules?: readonly string[];
  /** The rule files the pack is made of, each a path relative to the manifest. */
  readonly files?: readonly string[];
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

  constructor(file: CodeListFile) {
    this.id = file.listId;
    this.codes = new Set(file.entries.map((entry) => entry.value));
  }

  /** Tells whether the snapshot carries that code. */
  contains(code: string): boolean {
    return this.codes.has(code);
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
