import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { Structure, canonicalize, readDocumentOrThrow, semanticDigest, validate } from '../src/index.ts';
import { invoiceOf } from '../src/generated/en16931-2017/view.ts';
import { registries, ruleEngine } from '../src/node/data.ts';

/**
 * The snippets of this package's README, run.
 *
 * A code block nobody runs is a claim, and this repository does not make claims it has not
 * measured. Each test below is the block above it, with the file name resolved.
 */

const ROOT = path.resolve(fileURLToPath(new URL('../../..', import.meta.url)));
const INVOICE = path.join(ROOT, 'examples', 'allowances.esj.json');
const README = readFileSync(
  fileURLToPath(new URL('../README.md', import.meta.url)), 'utf8');

test('the first snippet reads, canonicalizes, digests and validates an invoice', async () => {
  const document = readDocumentOrThrow(await readFile(INVOICE));
  assert.deepEqual(document.values.get('/BG-25/0/BT-131'), { value: '1080' });
  assert.ok(canonicalize(document).startsWith('{"format":"EN16931-Semantic-JSON"'));
  assert.match(await semanticDigest(document), /^[0-9a-f]{64}$/);

  const result = validate(await readFile(INVOICE), { registries: registries() });
  assert.equal(result.status, 'VALID');
  assert.deepEqual(result.findings, []);
});

test('the typed view snippet reads the invoice by the names of the model', async () => {
  const document = readDocumentOrThrow(await readFile(INVOICE));
  const invoice = invoiceOf(document);
  assert.equal(typeof invoice.seller().name(), 'string');
  assert.equal(invoice.invoiceLines()[0].netAmount().toString(), '1080');
  assert.ok(invoice.documentTotals().amountDueForPayment().signum() !== 0);
});

test('the rule snippet compiles the pack and runs it', async () => {
  const document = readDocumentOrThrow(await readFile(INVOICE));
  const carried = registries();
  const core = carried.find(
    (registry) => registry.semanticModel === document.semanticModel)!;
  const engine = ruleEngine(new Structure(core, carried.filter((r) => r.isExtension())));
  assert.deepEqual(engine.evaluate(document), []);
});

test('the README says what the pack carries, and the pack carries it', () => {
  const pack = JSON.parse(readFileSync(
    path.join(ROOT, 'rules', 'en16931', '1.3.16', 'pack.json'), 'utf8')) as {
      javaRules: string[]; files: string[];
    };
  let declarative = 0;
  for (const file of pack.files) {
    declarative += (JSON.parse(readFileSync(
      path.join(ROOT, 'rules', 'en16931', '1.3.16', file), 'utf8')) as unknown[]).length;
  }
  assert.ok(README.includes(String(declarative + pack.javaRules.length) + ' rules: '
    + declarative + ' read from'), 'the README counts ' + declarative + ' + '
    + pack.javaRules.length);
  assert.ok(README.includes(pack.javaRules.length + ' the rule language cannot express'));
});

test('every import specifier of the README resolves through the package exports', () => {
  const exported = (JSON.parse(readFileSync(
    fileURLToPath(new URL('../package.json', import.meta.url)), 'utf8')) as {
      exports: Record<string, string | Record<string, string>>;
    }).exports;
  const specifiers = [...README.matchAll(/from '(en16931-semantic-json[^']*)'/g)]
    .map((match) => match[1]);
  assert.ok(specifiers.length >= 3, 'the README imports from the package');
  for (const specifier of specifiers) {
    const target = resolve(exported, './' + specifier.slice('en16931-semantic-json'.length + 1)
      .replace(/^$/, ''));
    assert.ok(target !== undefined, specifier + ' matches no entry of the exports map');
    const source = path.join(fileURLToPath(new URL('..', import.meta.url)),
      target!.replace(/^\.\/dist\//, 'src/').replace(/\.js$/, '.ts'));
    assert.ok(existsSync(source),
      specifier + ' resolves to ' + target + ', and this package has no ' + source);
  }
});

/**
 * The target an exports map gives a subpath, by the rule node applies: an exact key first,
 * then the one pattern whose `*` the subpath fills.
 */
function resolve(
  exported: Record<string, string | Record<string, string>>, subpath: string,
): string | undefined {
  const of = (entry: string | Record<string, string>): string =>
    typeof entry === 'string' ? entry : entry.import;
  if (subpath === './') {
    return of(exported['.']);
  }
  if (exported[subpath] !== undefined) {
    return of(exported[subpath]);
  }
  for (const [key, entry] of Object.entries(exported)) {
    const star = key.indexOf('*');
    if (star < 0) {
      continue;
    }
    const before = key.slice(0, star);
    const after = key.slice(star + 1);
    if (subpath.startsWith(before) && subpath.endsWith(after)
      && subpath.length >= before.length + after.length) {
      const filled = subpath.slice(before.length, subpath.length - after.length);
      return of(entry).replace('*', filled);
    }
  }
  return undefined;
}

test('the README counts the manifest as this build runs it', () => {
  const fixtures = path.join(ROOT, 'conformance', 'fixtures');
  const root = JSON.parse(readFileSync(path.join(fixtures, 'manifest.json'), 'utf8')) as Part;
  const parts: Part[] = [root];
  for (const name of root.parts ?? []) {
    const file = path.join(fixtures, name);
    if (existsSync(file)) {
      parts.push(JSON.parse(readFileSync(file, 'utf8')) as Part);
    }
  }
  const documents = parts.reduce((sum, part) => sum + (part.documents ?? []).length, 0);
  const invalid = parts.reduce((sum, part) => sum + (part.invalid ?? []).length, 0);
  assert.ok(README.includes(documents + ' conformant documents'),
    'the manifest this build runs holds ' + documents + ' conformant documents');
  assert.ok(README.includes(invalid + ' rows for the documents that have to be rejected'),
    'the manifest this build runs holds ' + invalid + ' rejected rows');
});

interface Part {
  parts?: string[];
  documents?: unknown[];
  invalid?: unknown[];
}

test('the README stays inside the line budget of the documentation style', () => {
  assert.ok(README.split('\n').length <= 150, 'the README is a reference page, not a manual');
});

test('the page under docs/ shows only code this README already runs', () => {
  const page = readFileSync(path.join(ROOT, 'docs', 'bindings-ts.md'), 'utf8');
  const blocks = [...page.matchAll(/```ts\n([\s\S]*?)```/g)].map((match) => match[1]);
  assert.equal(blocks.length, 3, 'the page shows the three snippets of this README');
  for (const block of blocks) {
    assert.ok(README.includes(block), 'this README does not carry\n' + block);
  }
  assert.ok(page.split('\n').length <= 150, 'the page stays inside its line budget');
});
