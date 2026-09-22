import type { Registry, RegistryTerm } from './registry.ts';
import { isRepeatable } from './registry.ts';

/**
 * The structure a path is measured against: which terms exist, in which chains of groups
 * they may stand, and which children a group instance has (specification, sections 5.2, 5.6
 * and 9.3).
 *
 * It is one registry of one edition together with the extension registries loaded beside it.
 * An extension adds terms of its own, and it may record a further position for a core term
 * through `reusesTerms`; the chain the core registry records stays well formed, so a term has
 * a set of chains and not one.
 */

/** A child a group instance, or the root of the document, may carry. */
export interface Child {
  /** The identifier of the child term or group. */
  readonly id: string;
  /** Whether the child is a business term or a business group. */
  readonly kind: 'BT' | 'BG';
  /** The minimum cardinality in this position. */
  readonly min: number;
  /** The maximum cardinality in this position, `n` where it is unbounded. */
  readonly max: number | 'n';
}

/** The terms of one edition and of the extensions loaded beside it. */
export class Structure {
  /** The registry of the edition the documents name. */
  readonly core: Registry;

  /** The extension registries loaded beside it. */
  readonly extensions: readonly Registry[];

  private readonly byId = new Map<string, RegistryTerm>();
  private readonly chains = new Map<string, string[][]>();
  private readonly children = new Map<string, Child[]>();
  private readonly recursive = new Set<string>();

  /** The key under which the children of the root of a document are held. */
  private static readonly ROOT = '';

  constructor(core: Registry, extensions: readonly Registry[] = []) {
    this.core = core;
    this.extensions = extensions;
    for (const registry of [core, ...extensions]) {
      for (const term of registry.terms()) {
        this.byId.set(term.id, term);
      }
    }
    for (const registry of [core, ...extensions]) {
      for (const term of registry.terms()) {
        this.record(term);
      }
    }
  }

  private record(term: RegistryTerm): void {
    this.addChain(term.id, term.path.slice(0, term.path.length - 1));
    this.addChild(term.parent ?? Structure.ROOT, {
      id: term.id, kind: term.kind, min: term.min, max: term.max,
    });
    for (const reused of term.reusesTerms ?? []) {
      if (reused === term.id) {
        this.recursive.add(term.id);
      }
      const child = this.byId.get(reused);
      if (child === undefined) {
        continue;
      }
      this.addChain(reused, [...term.path]);
      this.addChild(term.id, {
        id: reused, kind: child.kind, min: child.min, max: child.max,
      });
    }
  }

  private addChain(id: string, chain: string[]): void {
    const chains = this.chains.get(id);
    if (chains === undefined) {
      this.chains.set(id, [chain]);
      return;
    }
    if (!chains.some((known) => same(known, chain))) {
      chains.push(chain);
    }
  }

  private addChild(parent: string, child: Child): void {
    const children = this.children.get(parent);
    if (children === undefined) {
      this.children.set(parent, [child]);
      return;
    }
    if (!children.some((known) => known.id === child.id)) {
      children.push(child);
    }
  }

  /** Returns the term or group with that identifier, or `undefined`. */
  term(id: string): RegistryTerm | undefined {
    return this.byId.get(id);
  }

  /** Tells whether a term or group may occur more than once, which decides its path shape. */
  repeatable(id: string): boolean {
    const term = this.byId.get(id);
    return term !== undefined && isRepeatable(term);
  }

  /** Returns the children a group instance may carry, or those of the root for the empty id. */
  childrenOf(groupId: string): readonly Child[] {
    return this.children.get(groupId) ?? [];
  }

  /** Returns the children the root of a document may carry. */
  rootChildren(): readonly Child[] {
    return this.childrenOf(Structure.ROOT);
  }

  /** Tells whether a group carries further instances of itself, to any depth (section 5.6). */
  isRecursive(groupId: string): boolean {
    return this.recursive.has(groupId);
  }

  /**
   * Tells whether a chain of group identifiers is one the registries record for a term.
   *
   * A registry enumerates chains and no enumeration reaches every depth a recursive group
   * may be written to, so consecutive repetitions of a recursive group are written as one
   * before the chain is looked up (section 5.6).
   *
   * @param id the identifier of the term or group the path ends at
   * @param groups the group identifiers of the path, outermost first
   * @return whether the path stands in a position the registries record
   */
  isChain(id: string, groups: readonly string[]): boolean {
    const chains = this.chains.get(id);
    if (chains === undefined) {
      return false;
    }
    const collapsed = this.collapse(groups);
    return chains.some((known) => same(known, collapsed) || same(known, [...groups]));
  }

  /** Writes consecutive repetitions of a recursive group as one. */
  private collapse(groups: readonly string[]): string[] {
    const out: string[] = [];
    for (const group of groups) {
      if (out.length > 0 && out[out.length - 1] === group && this.recursive.has(group)) {
        continue;
      }
      out.push(group);
    }
    return out;
  }
}

function same(left: readonly string[], right: readonly string[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}
