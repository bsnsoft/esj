import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { readDocumentOrThrow } from '../src/reader.ts';
import { invoiceOf, SEMANTIC_MODEL } from '../src/generated/en16931-2017/view.ts';
import { invoiceOf as invoiceOf2026 } from '../src/generated/en16931-2026/view.ts';
import { registries } from '../src/node/data.ts';

/**
 * The typed view, generated from the registry: the same name stems the Java view uses, so an
 * invoice reads alike in both.
 */

const ROOT = path.resolve(fileURLToPath(new URL('../../..', import.meta.url)));

function example(name: string) {
  return readDocumentOrThrow(readFileSync(path.join(ROOT, 'examples', name)));
}

test('the view reads an invoice by the names of the model', () => {
  const invoice = invoiceOf(example('minimal.esj.json'));
  assert.equal(invoice.invoiceNumber().value, 'RE-2026-0001');
  assert.equal(invoice.issueDate(), '2026-01-15');
  assert.equal(invoice.typeCode(), '380');
  assert.equal(invoice.currencyCode(), 'EUR');
  assert.equal(invoice.seller().name(), 'Example GmbH');
  assert.equal(invoice.seller().postalAddress().countryCode(), 'DE');
  assert.equal(invoice.invoiceLines().length, 1);
  assert.equal(invoice.invoiceLines()[0].netAmount().toString(), '100');
  assert.equal(invoice.invoiceLines()[0].item().name(), 'Consulting service');
  assert.equal(invoice.documentTotals().amountDueForPayment().toString(), '100');
});

test('an optional term the invoice does not carry reads as undefined', () => {
  const invoice = invoiceOf(example('minimal.esj.json'));
  assert.equal(invoice.paymentDueDate(), undefined);
  assert.equal(invoice.vatAccountingCurrencyCode(), undefined);
  assert.equal(invoice.invoiceLines()[0].period(), undefined);
});

test('the view and the path address the same value', () => {
  const document = example('allowances.esj.json');
  const line = invoiceOf(document).invoiceLines()[0];
  assert.equal(line.path, '/BG-25/0');
  assert.equal(line.netAmount().toString(),
    document.values.get(line.path + '/BT-131')!.value);
});

test('the edition each view reads is the edition it was generated from', () => {
  assert.equal(SEMANTIC_MODEL, 'EN16931-1:2017+A1:2019/AC:2020');
  assert.ok(registries().some((registry) => registry.semanticModel === SEMANTIC_MODEL));
});

test('the 2026 view reads a document of its own edition', () => {
  const invoice = invoiceOf2026(example('edition-2026.esj.json'));
  assert.equal(invoice.path, '');
  assert.ok(invoice.invoiceNumber().value.length > 0);
});
