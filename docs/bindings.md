# Syntax bindings: the tables, the readers and the writer

*Part of [EN16931 Semantic JSON](../README.md).*

ESJ addresses the semantic model of EN 16931 and binds no syntax of its own. Everything that
turns a UBL Invoice, a UBL Credit Note or a cross industry invoice into a semantic document —
and a semantic document back into one of them — sits in `esj-bindings` and in the three binding
tables of `model/bindings/`. This page is the short way in; each section names the page that
holds the detail, because those pages are generated beside the thing they measure and stay
right by being regenerated rather than by being remembered.

## The tables

`model/bindings/{ubl-invoice,ubl-creditnote,cii}.json` say, for every business term and every
business group of the registry, the XPath the syntax writes it at, the XPaths of its
supplementary components, and machine flags for the quirks of that binding. They are generated
from the SeMoX model of the XRechnung CIUS published by KoSIT, and only facts travel: no
description, note text, rule or example of that model is reproduced. Where the source says
something about a syntax that the syntax does not do, the generator corrects it and records the
correction with its reason in the `corrections` member of the table.

- [`model/bindings/README.md`](../model/bindings/README.md) — the format, the flags, the
  corrections, the attribution, and how to regenerate the tables from a model file.
- [`conformance/bindings/crosscheck.md`](../conformance/bindings/crosscheck.md) — the tables
  measured against the KoSIT visualization stylesheets, the CEN validation artefacts and the
  older MIT-licensed model, with every difference named and explained.

## The two readers

Two ways into the semantic model, sharing no code. The **streaming reader** of `esj-bindings`
matches a document against the tables one element at a time and is what `esj` reads with by
default (`--importer streaming`). The **XSLT path** of `esj-xr` runs the vendored KoSIT
stylesheets and stays in the product as the oracle (`--importer xslt`); it builds three trees of
the document, so it costs minutes where the other costs seconds.

- [`conformance/readers.md`](../conformance/readers.md) — the two over the whole corpus: every
  differing path, every cause, what each reader reports that the other does not, and the one
  class of document where the streaming reader gives way.
- [`conformance/creditnote/README.md`](../conformance/creditnote/README.md) — the corpus holds
  no UBL Credit Note, so the credit note table is measured by one document written for it.
- [`cli.md`](cli.md), *Which reader* — the switch and what it changes.

**Which registry each door starts from.** A reader can only represent the terms a registry
knows, and the two doors do not start from the same one. The library — `ReaderOptions` — starts
from the core registry **with** the XRechnung extension. The command line starts from the
**core** registry alone and takes `--extension xrechnung` to add it. The difference shows on a
document that uses the extension: with the extension the sub invoice lines become values, and
without it they become `UNKNOWN_TERM` observations and are dropped. Over the conformance corpus
the streaming reader reports 34 observations with the extension and 44 without it — the same 34
and ten more. The counts on `conformance/readers.md`, and the documents checked in under
`conformance/esj/`, are the registry with the extension, so

```sh
esj convert --extension xrechnung --to esj <instance>
```

is the command that reproduces them.

## The two writers

`CiiWriter` and `UblWriter` turn a semantic document back into a UN/CEFACT CII D16B invoice and
into an OASIS UBL 2.1 Invoice or Credit Note, from the same tables and with the element order
taken from the schema modules of the validation pack. One engine serves both; what differs is
what the tables and the two schemas say. Where the semantic model is wider than the syntax, or
the syntax asks for something the semantic model does not state, the writer writes what it can
and names the rest in its report. Three things it leaves out rather than write: a character
XML 1.0 has no place for, a value the syntax writes only in front of another term's content,
and a value the syntax nests inside the element of a business group the document does not state
and whose schema requires content of that element — the payment due date of a UBL credit note
without payment instructions is the one case of this release.

**What the UBL writer writes where the model has no term.** UBL requires three elements that
EN 16931-1 either has no term for or does not require. What is written at each of them is the
`conventions` member of `model/bindings/ubl-invoice.json` and `ubl-creditnote.json` — element,
condition, value, what asks for the element and where the value comes from — and every one the
writer applies is a note of its report. The value carries no business statement, so the
document stays complete and the official artefacts judge it.

| Element | Value | When | Source |
|---|---|---|---|
| `cac:TaxScheme/cbc:ID` | `FC` | the party states a tax registration that is not BT-31 | the UNTDID 1153 code the CII binding fixes for BT-32 |
| `cac:OrderReference/cbc:ID` | `NA` | the document states BT-14 and no BT-13 | Peppol BIS Billing 3.0 |
| `cac:CardAccount/cbc:NetworkID` | `NA` | the document states BG-18 | Peppol BIS Billing 3.0 |

`WriterOptions.taxRegistrationScheme` names another code for the first of them and refuses
`VAT`, which is the scheme of BT-31. The second is the one a reader has to know: the streaming
reader does not read that element as BT-13 where it carries `NA` beside a `cbc:SalesOrderID`,
and the writer says so where a document's own BT-13 is those two letters beside a BT-14.

**Which UBL document.** UBL is two document types where the semantic model has one, and which
one is written is a fact of the invoice type code BT-3: the default `AUTO` writes a credit note
where BT-3 is one of the codes the validation artefacts of the pack admit on a credit note and
not on an invoice, an invoice otherwise, and a document with no BT-3 is an invoice.
`INVOICE` and `CREDIT_NOTE` name it instead ([`java-api.md`](java-api.md#writing-xml)).

**What a writer guarantees.** Four things, each of them measured rather than asserted:

| | |
|---|---|
| Nothing is lost silently | every value the syntax has no place for is a note of the report, with its semantic path and the reason; `isComplete()` is the short answer |
| The bytes are deterministic | element order from the schema, siblings of one name in the canonical order of the semantic paths that produced them, no map iteration order and no locale |
| The result is judged by the syntax, not by this project | every document of the corpus and of `examples/` goes to the official artefacts of the pack on every build |
| The output is bounded | `maxOutputBytes`, a bound of the output and not of the input |

- [`conformance/writers/cii-roundtrip.md`](../conformance/writers/cii-roundtrip.md) and
  [`ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md) — every document each writer
  produces over the corpus and over `examples/`, put to the official validation artefacts and
  read back: what is accepted, what survives, and where each syntax draws a distinction the
  semantic model does not.
- [`matrix.md`](../conformance/writers/matrix.md) — the two writers against each other: the 40
  business cases the corpus carries in both syntaxes, each converted into the other one and
  compared with the file that was already there.
- [`java-api.md`](java-api.md), *Writing XML* — the API and its report.
- `esj convert --to cii` and `--to ubl` in [`cli.md`](cli.md). `esj validate` writes an ESJ
  document the same way so that the official artefacts can read it
  ([`validation.md`](validation.md#the-complete-check)).
- [`pdf-output.md`](pdf-output.md), *Embedding the invoice* — the cross industry invoice
  attached to a PDF/A-3 rendering as a Factur-X file.

## Limits and cost

The reader is bounded by the size of the input, the nesting of its elements and what it has to
hold to decide a condition; a writer by the size of the output it may produce,
`--max-output-bytes`, which is a bound of its own and not the input one: a cross industry
invoice runs to about three times the UBL invoice it was converted from. All of them are
switches of the command line and options of the library, and both writers build the element
tree of the whole document before they serialize it, so their cost grows with the document
rather than with one invoice line.

- [`deployment.md`](deployment.md), *Sizing* — which class of document needs which heap.
- [`deployment-measurements.md`](deployment-measurements.md) — the measurements behind it,
  including *The streaming reader at 80 MB* and *The two writers at 50 and 80 MB*.

Author: Christian Bürckert.
