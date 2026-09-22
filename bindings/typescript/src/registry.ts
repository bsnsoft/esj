/**
 * The registry: the machine-readable structure of one edition of the semantic model
 * (specification, section 10).
 *
 * Layers L2 and L3 are defined against it, and so is the content grammar of every value. It
 * is data, not code: the files under `model/` are copied into this package at build time and
 * are never edited here, so that both implementations measure a document against the same
 * facts.
 */

/** The role a supplementary component plays in a value (sections 6.6 and 6.7). */
export type ComponentRole = 'scheme' | 'schemeVersion' | 'mimeCode' | 'filename';

/** A supplementary component a business term carries. */
export interface RegistryComponent {
  /** The informative label of the component. */
  readonly id?: string;
  /** Which member of a value object the component is written as. */
  readonly role: ComponentRole;
  /** The name of the component. */
  readonly name?: string;
  /** The code list the component is drawn from, where the model fixes one. */
  readonly schemeList?: string;
  /** The minimum cardinality: 1 where the registry declares the component mandatory. */
  readonly min: number;
  /** The maximum cardinality. */
  readonly max: number;
}

/** How often a term or a group may occur; `n` is the registry's spelling of unbounded. */
export type MaxCardinality = number | 'n';

/** One business term or business group of a registry. */
export interface RegistryTerm {
  /** The identifier the standard gives it. */
  readonly id: string;
  /** Whether it is a business term or a business group. */
  readonly kind: 'BT' | 'BG';
  /** The name the standard gives it. */
  readonly name: string;
  /** The ESJ name stem a generator forms a member name from (section 10). */
  readonly slug: string;
  /** The group it sits in, or `null` at the root of the document. */
  readonly parent: string | null;
  /** The chain of identifiers that reaches it, this term included. */
  readonly path: readonly string[];
  /** The minimum cardinality. */
  readonly min: number;
  /** The maximum cardinality. */
  readonly max: MaxCardinality;
  /** The semantic data type of a business term, or `null` for a group (section 6.2). */
  readonly datatype: string | null;
  /** The supplementary components the term carries. */
  readonly components: readonly RegistryComponent[];
  /** The core identifiers an extension group carries directly (section 5.6). */
  readonly reusesTerms?: readonly string[];
  /** The code list the value is drawn from, for documentation only. */
  readonly codeList?: string;
  /** The fraction-digit bound of the term, where the edition fixes a number. */
  readonly maxDecimals?: number;
  /** The rule that gives the bound, where the edition names one instead. */
  readonly maxDecimalsRule?: string;
  /** The position of the term in the term table of its edition. */
  readonly order?: number;
  /** The author's description of the term. */
  readonly description?: string;
}

/** What an extension registry builds on (section 10). */
export interface RegistryImport {
  /** The model it imports. */
  readonly model: string;
  /** The edition of that model, in the spelling the standards body uses. */
  readonly edition: string;
}

/** A registry file as it is written. */
export interface RegistryFile {
  readonly format: string;
  readonly version: string;
  readonly model: string;
  readonly edition: string;
  readonly imports?: readonly RegistryImport[];
  /** `none` where the extension declares its terms bound by no transport syntax (section 10). */
  readonly transport?: 'none';
  readonly transportNote?: string;
  readonly terms: readonly RegistryTerm[];
}

/** Tells whether a term or a group may occur more than once, which decides its path shape. */
export function isRepeatable(term: RegistryTerm): boolean {
  return term.max === 'n' || (typeof term.max === 'number' && term.max > 1);
}

/**
 * Returns the edition string a document writes in `semanticModel` for the edition a registry
 * names: the registry's spelling with every space removed (section 10).
 */
export function semanticModelOf(edition: string): string {
  return edition.split(' ').join('');
}

/** One registry file, read. */
export class Registry {
  /** The model the registry describes, for example `EN16931-1`. */
  readonly model: string;

  /** The edition, in the spelling the standards body uses. */
  readonly edition: string;

  /** The edition as a document writes it in `semanticModel` (section 4.4). */
  readonly semanticModel: string;

  /** What this registry builds on, where it is an extension (section 10). */
  readonly imports: readonly RegistryImport[];

  private readonly byId = new Map<string, RegistryTerm>();

  constructor(file: RegistryFile) {
    this.model = file.model;
    this.edition = file.edition;
    this.semanticModel = semanticModelOf(file.edition);
    this.imports = file.imports ?? [];
    for (const term of file.terms) {
      this.byId.set(term.id, term);
    }
  }

  /** Returns the term with that identifier, or `undefined`. */
  term(id: string): RegistryTerm | undefined {
    return this.byId.get(id);
  }

  /** Returns every term and group of this registry, in the order the file writes them. */
  terms(): RegistryTerm[] {
    return [...this.byId.values()];
  }

  /** Tells whether this registry is an extension, which is one that imports another. */
  isExtension(): boolean {
    return this.imports.length > 0;
  }
}

/** Reads a registry from the parsed JSON of a registry file. */
export function registryOf(file: RegistryFile): Registry {
  return new Registry(file);
}
