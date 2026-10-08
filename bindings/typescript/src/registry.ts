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

/**
 * What a registry is refused with when it is read or combined: a registry no document can be
 * measured against is a defect of the registry and not of any invoice, so it is refused where
 * it is given rather than where a document arrives (section 10).
 */
export class RegistryError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'RegistryError';
  }
}

/** The components a value of each semantic data type may carry (sections 6.6 and 6.7). */
const COMPONENT_ROLES: Readonly<Record<string, readonly ComponentRole[]>> = {
  Identifier: ['scheme', 'schemeVersion'],
  BinaryObject: ['mimeCode', 'filename'],
};

/**
 * Checks the components a term declares against the rules of section 10, which follow from
 * section 6 and are checked when a registry is read, because a term no value can satisfy is a
 * defect of the registry: a component appears only where the semantic data type has it and
 * only once; a `BinaryObject` carries exactly `mimeCode` and `filename`, both mandatory;
 * `schemeVersion` is declared only beside `scheme`, and mandatory only beside a `scheme` that
 * is mandatory too.
 */
function checkComponents(term: RegistryTerm): void {
  const components = term.components;
  const roles = new Set<ComponentRole>();
  for (const component of components) {
    if (roles.has(component.role)) {
      throw new RegistryError(term.id + ' lists the component ' + component.role + ' twice');
    }
    roles.add(component.role);
  }
  const mandatory = (role: ComponentRole) =>
    components.some((component) => component.role === role && component.min >= 1);
  if (term.datatype === 'BinaryObject') {
    if (roles.size !== 2 || !roles.has('mimeCode') || !roles.has('filename')) {
      throw new RegistryError('the Binary Object ' + term.id
        + ' carries the components mimeCode and filename');
    }
    for (const component of components) {
      if (component.min < 1) {
        throw new RegistryError('the component ' + component.role + ' of the Binary Object '
          + term.id + ' is mandatory');
      }
    }
    return;
  }
  if (components.length === 0) {
    return;
  }
  if (term.datatype === null || term.datatype === undefined) {
    throw new RegistryError(term.id + ' carries supplementary components but no semantic data'
      + ' type');
  }
  const allowed = COMPONENT_ROLES[term.datatype] ?? [];
  for (const role of roles) {
    if (!allowed.includes(role)) {
      throw new RegistryError('the semantic data type ' + term.datatype + ' of ' + term.id
        + ' has no component ' + role);
    }
  }
  if (roles.has('schemeVersion') && !roles.has('scheme')) {
    throw new RegistryError(term.id + ' carries a scheme version without a scheme');
  }
  if (mandatory('schemeVersion') && !mandatory('scheme')) {
    throw new RegistryError(term.id + ' declares its scheme version mandatory and its scheme'
      + ' optional, and no value can satisfy both');
  }
}

/**
 * Checks that a registry file has the members a registry is read by, so that a file of another
 * shape is refused as one rather than failing somewhere later.
 */
function checkShape(file: RegistryFile): void {
  const text = (value: unknown) => typeof value === 'string' && value !== '';
  if (typeof file !== 'object' || file === null || !text(file.model) || !text(file.edition)
    || !Array.isArray(file.terms)) {
    throw new RegistryError('a registry carries model, edition and terms');
  }
  if (file.imports !== undefined && (!Array.isArray(file.imports) || file.imports.some(
    (entry) => typeof entry !== 'object' || entry === null || !text(entry.model)
      || !text(entry.edition)))) {
    throw new RegistryError('imports is a list of imports, each with model and edition');
  }
  for (const term of file.terms) {
    if (typeof term !== 'object' || term === null || !text(term.id)
      || (term.kind !== 'BT' && term.kind !== 'BG') || !Array.isArray(term.path)
      || term.path[term.path.length - 1] !== term.id
      || (term.parent !== null && typeof term.parent !== 'string')
      || typeof term.min !== 'number' || (typeof term.max !== 'number' && term.max !== 'n')
      || !Array.isArray(term.components)) {
      throw new RegistryError('the term ' + (typeof term?.id === 'string' ? term.id : 'of index '
        + file.terms.indexOf(term)) + ' is not written as section 10 writes a term');
    }
  }
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
  private readonly foreign: readonly string[];

  /**
   * Reads a registry file and checks what section 10 has checked when a registry is read: no
   * identifier is listed twice, every term declares components a value can satisfy, and a
   * registry that names a parent or a reused term it does not define itself — an extension —
   * says in `imports` what it builds on.
   *
   * @throws RegistryError where the file breaks one of those rules
   */
  constructor(file: RegistryFile) {
    checkShape(file);
    this.model = file.model;
    this.edition = file.edition;
    this.semanticModel = semanticModelOf(file.edition);
    this.imports = file.imports ?? [];
    for (const term of file.terms) {
      if (this.byId.has(term.id)) {
        throw new RegistryError('the registry ' + file.edition + ' lists ' + term.id + ' twice');
      }
      checkComponents(term);
      this.byId.set(term.id, term);
    }
    const foreign = new Set<string>();
    for (const term of file.terms) {
      const named = [...term.path.slice(0, term.path.length - 1), ...(term.reusesTerms ?? [])];
      if (term.parent !== null) {
        named.push(term.parent);
      }
      for (const id of named) {
        if (!this.byId.has(id)) {
          foreign.add(id);
        }
      }
    }
    this.foreign = [...foreign];
    if (this.foreign.length > 0 && this.imports.length === 0) {
      throw new RegistryError('the registry ' + file.edition + ' names ' + this.foreign[0]
        + ', which it does not define, and imports no registry that does');
    }
  }

  /**
   * Returns the identifiers this registry names as a parent or a reused term without defining
   * them: the terms of the registry an extension builds on, and none for a core registry.
   */
  foreignIds(): readonly string[] {
    return this.foreign;
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
