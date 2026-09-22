/**
 * Generates the typed read view from a registry.
 *
 * The registry records a name stem per business term (`SPEC.md` section 10), and a generator
 * applies the naming convention of its own language to it. This one writes TypeScript: the
 * stems are the member names, a repeatable group's plural stem yields the name of one of its
 * instances, and a type name is qualified by its ancestors only as far as it must be to stay
 * unique — which is the rule the Java generator of this repository follows, so that the two
 * views read alike.
 *
 * The output is not checked in. It is written before every build and every test run, from the
 * registry files of the repository, so that no view can drift from the model it claims to be.
 *
 *     node scripts/generate-typed.ts
 */

import { mkdir, writeFile } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import type { RegistryFile, RegistryTerm } from '../src/registry.ts';
import { Registry, isRepeatable } from '../src/registry.ts';

const here = path.dirname(fileURLToPath(import.meta.url));
const data = path.resolve(here, '..', 'data');
const out = path.resolve(here, '..', 'src', 'generated');

/** The editions this package generates a view for, and the directory each one goes in. */
const EDITIONS = [
  { file: 'model/en16931/2017.json', directory: 'en16931-2017' },
  { file: 'model/en16931/2026.json', directory: 'en16931-2026' },
];

/** Type names the runtime and the root of the view already use. */
const RESERVED = new Set([
  'Invoice', 'Identifier', 'BinaryObject', 'Decimal', 'SemanticDocument', 'SemanticValue',
  'TypedIndex',
]);

/** Member names the view itself uses. */
const RESERVED_MEMBERS = new Set(['path', 'document']);

function upperCamel(stem: string): string {
  return stem.charAt(0).toUpperCase() + stem.slice(1);
}

/**
 * Returns the singular of a plural stem, which the registry writes for a repeatable term or
 * group because the stem names the list of the occurrences.
 */
function singular(stem: string): string | null {
  if (stem.length > 3 && stem.endsWith('ies')) {
    return stem.slice(0, stem.length - 3) + 'y';
  }
  if (stem.length > 1 && stem.endsWith('s')) {
    return stem.slice(0, stem.length - 1);
  }
  return null;
}

function baseName(term: RegistryTerm): string {
  const stem = isRepeatable(term) ? singular(term.slug) ?? term.slug : term.slug;
  return upperCamel(stem);
}

function ancestorsOf(registry: Registry, id: string): RegistryTerm[] {
  const ancestors: RegistryTerm[] = [];
  let current = registry.term(id)!;
  while (current.parent !== null) {
    current = registry.term(current.parent)!;
    ancestors.push(current);
  }
  return ancestors;
}

/**
 * Names the interface of every business group, qualifying a name by its ancestors only where
 * two groups would otherwise be called the same.
 */
function typeNames(registry: Registry): Map<string, string> {
  const groups = registry.terms().filter((term) => term.kind === 'BG');
  const levels = new Map(groups.map((group) => [group.id, 0]));
  for (let round = 0; round <= 16; round++) {
    const candidates = new Map<string, string>();
    for (const group of groups) {
      const ancestors = ancestorsOf(registry, group.id);
      const level = Math.min(levels.get(group.id)!, ancestors.length);
      let name = '';
      for (let i = level - 1; i >= 0; i--) {
        name += baseName(ancestors[i]);
      }
      candidates.set(group.id, name + baseName(group));
    }
    const seen = new Set<string>();
    const clashing = new Set<string>();
    for (const name of candidates.values()) {
      if (seen.has(name) || RESERVED.has(name)) {
        clashing.add(name);
      }
      seen.add(name);
    }
    if (clashing.size === 0) {
      return candidates;
    }
    let raised = false;
    for (const [id, name] of candidates) {
      if (clashing.has(name) && levels.get(id)! < ancestorsOf(registry, id).length) {
        levels.set(id, levels.get(id)! + 1);
        raised = true;
      }
    }
    if (!raised) {
      throw new Error('the registry stems do not yield unique type names: ' + [...clashing]);
    }
  }
  throw new Error('the registry stems do not yield unique type names');
}

/** The TypeScript type a business term's value is read as, and the reader that reads it. */
function reader(term: RegistryTerm): { type: string; read: string } {
  switch (term.datatype) {
    case 'Amount':
    case 'UnitPriceAmount':
    case 'Quantity':
    case 'Percentage':
      return { type: 'Decimal', read: 'decimalOf' };
    case 'Date':
      return { type: 'string', read: 'dateOf' };
    case 'Time':
      return { type: 'string', read: 'timeOf' };
    case 'BinaryObject':
      return { type: 'BinaryObject', read: 'binaryOf' };
    case 'Identifier':
      return { type: 'Identifier', read: 'identifierOf' };
    default:
      return { type: 'string', read: 'textOf' };
  }
}

function comment(term: RegistryTerm): string {
  const description = term.description ?? term.name;
  return '  /** ' + term.id + ' ' + term.name + '. ' + description + ' */';
}

function childrenOf(registry: Registry, parent: string | null): RegistryTerm[] {
  return registry.terms()
    .filter((term) => term.parent === parent)
    .sort((left, right) => (left.order ?? 0) - (right.order ?? 0));
}

function declaration(registry: Registry, names: Map<string, string>, child: RegistryTerm): string {
  const repeatable = isRepeatable(child);
  if (child.kind === 'BG') {
    const type = names.get(child.id)!;
    return repeatable ? type + '[]' : child.min >= 1 ? type : type + ' | undefined';
  }
  const { type } = reader(child);
  return repeatable ? type + '[]' : child.min >= 1 ? type : type + ' | undefined';
}

function body(registry: Registry, names: Map<string, string>, child: RegistryTerm): string {
  const repeatable = isRepeatable(child);
  const step = "'/" + child.id + "'";
  if (child.kind === 'BG') {
    const make = 'make' + names.get(child.id)!;
    if (repeatable) {
      return 'groups(index, path + ' + step + ', ' + make + ')';
    }
    return child.min >= 1
      ? 'requiredGroup(index, path + ' + step + ', ' + make + ')'
      : 'group(index, path + ' + step + ', ' + make + ')';
  }
  const { read } = reader(child);
  if (repeatable) {
    return 'repeated(index, path + ' + step + ', ' + read + ')';
  }
  return child.min >= 1
    ? 'required(index, path + ' + step + ', ' + read + ')'
    : 'optional(index, path + ' + step + ', ' + read + ')';
}

function interfaceOf(
  registry: Registry, names: Map<string, string>, name: string, term: RegistryTerm | null,
): string {
  const children = childrenOf(registry, term === null ? null : term.id);
  const lines: string[] = [];
  lines.push('/**');
  lines.push(' * ' + (term === null
    ? 'The invoice, read through the names of the semantic model.'
    : term.id + ' ' + term.name + '. ' + (term.description ?? '')));
  lines.push(' */');
  lines.push('export interface ' + name + ' {');
  lines.push('  /** The path of this business group instance; the empty string at the root. */');
  lines.push('  readonly path: string;');
  for (const child of children) {
    lines.push('');
    lines.push(comment(child));
    lines.push('  ' + child.slug + '(): ' + declaration(registry, names, child) + ';');
  }
  lines.push('}');
  return lines.join('\n');
}

function factoryOf(
  registry: Registry, names: Map<string, string>, name: string, term: RegistryTerm | null,
): string {
  const children = childrenOf(registry, term === null ? null : term.id);
  const lines: string[] = [];
  lines.push('function make' + name + '(index: TypedIndex, path: string): ' + name + ' {');
  lines.push('  return {');
  lines.push('    path,');
  for (const child of children) {
    lines.push('    ' + child.slug + ': () => ' + body(registry, names, child) + ',');
  }
  lines.push('  };');
  lines.push('}');
  return lines.join('\n');
}

function checkMembers(registry: Registry): void {
  for (const term of registry.terms()) {
    if (RESERVED_MEMBERS.has(term.slug)) {
      throw new Error('the stem of ' + term.id + ' cannot be a member name: ' + term.slug);
    }
    if (!/^[a-z][A-Za-z0-9]*$/.test(term.slug)) {
      throw new Error('the stem of ' + term.id + ' is not a TypeScript name: ' + term.slug);
    }
  }
}

/** Tells whether the generated source uses a name, which decides whether it is imported. */
function used(source: string, name: string): boolean {
  return new RegExp('\\b' + name + '\\b').test(source);
}

/** The import statements the generated source needs, and no others. */
function imported(source: string, types: readonly string[]): string[] {
  const lines: string[] = [];
  if (used(source, 'Decimal')) {
    lines.push("import type { Decimal } from '../../decimal.ts';");
  }
  lines.push("import type { SemanticDocument } from '../../document.ts';");
  lines.push("import type { " + [...types, 'TypedIndex'].join(', ')
    + " } from '../../typed/runtime.ts';");
  const readers = [
    'TypedIndex as Index', 'binaryOf', 'dateOf', 'decimalOf', 'group', 'groups', 'identifierOf',
    'optional', 'repeated', 'required', 'requiredGroup', 'textOf', 'timeOf',
  ].filter((name) => used(source, name.split(' ')[0] === 'TypedIndex' ? 'Index' : name));
  lines.push('import {');
  let line = ' ';
  for (const reader of readers) {
    if (line.length + reader.length + 2 > 96) {
      lines.push(line + ',');
      line = ' ';
    }
    line += (line === ' ' ? ' ' : ', ') + reader;
  }
  lines.push(line + ',');
  lines.push("} from '../../typed/runtime.ts';");
  return lines;
}

async function generate(file: string, directory: string): Promise<number> {
  const registry = new Registry(
    JSON.parse(readFileSync(path.join(data, file), 'utf8')) as RegistryFile);
  checkMembers(registry);
  const names = typeNames(registry);
  const groups = registry.terms().filter((term) => term.kind === 'BG');
  const parts: string[] = [];
  parts.push(interfaceOf(registry, names, 'Invoice', null));
  for (const group of groups) {
    parts.push(interfaceOf(registry, names, names.get(group.id)!, group));
  }
  parts.push(factoryOf(registry, names, 'Invoice', null));
  for (const group of groups) {
    parts.push(factoryOf(registry, names, names.get(group.id)!, group));
  }
  parts.push([
    '/**',
    ' * Reads a document through the names of ' + registry.edition + '.',
    ' *',
    ' * @param document the document; it is not changed',
    ' * @return the typed view',
    ' */',
    'export function invoiceOf(document: SemanticDocument): Invoice {',
    "  return makeInvoice(new Index(document), '');",
    '}',
    '',
    '/** The edition this view reads, as a document writes it in `semanticModel`. */',
    "export const SEMANTIC_MODEL = '" + registry.semanticModel + "';",
  ].join('\n'));
  const source = parts.join('\n\n') + '\n';
  const header = [
    '// Generated from ' + file + ' by scripts/generate-typed.ts. Do not edit.',
    '// ' + registry.edition + '.',
    '',
  ];
  const types = ['BinaryObject', 'Identifier'].filter((name) => used(source, name));
  header.push(...imported(source, types));
  header.push('');
  await mkdir(path.join(out, directory), { recursive: true });
  await writeFile(path.join(out, directory, 'view.ts'),
    header.join('\n') + source, 'utf8');
  return groups.length + 1;
}

let written = 0;
for (const edition of EDITIONS) {
  written += await generate(edition.file, edition.directory);
}
await writeFile(path.join(out, 'index.ts'), [
  '// Generated by scripts/generate-typed.ts. Do not edit.',
  '',
  "export * as en16931_2017 from './en16931-2017/view.ts';",
  "export * as en16931_2026 from './en16931-2026/view.ts';",
  '',
].join('\n'), 'utf8');
console.log('generated ' + written + ' interfaces for ' + EDITIONS.length + ' editions');
