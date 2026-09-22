# The UBL writer, measured

*Part of [EN16931 Semantic JSON](../../README.md).*

Two measurements, for the two ways a writer can be wrong, taken the way
[`cii-roundtrip.md`](cii-roundtrip.md) takes them for the other syntax.

**Does the syntax accept what it wrote?** The official validation artefacts of the profile —
the XML Schema modules of OASIS UBL 2.1, the EN 16931 Schematron of CEN/TC 434 and the
XRechnung Schematron of KoSIT — run over every document the writer produces, through the same
engine `esj validate` uses and the same pack, `xrechnung/3.0.2/2026-08-31`.

**Does it still say what it was given?** Every result is read back with the streaming reader of
the same module and compared value by value.

Both figures are recomputed on every build. `ubl-roundtrip.json` beside this page carries them
as data and `UblWriterCorpusTest` in `esj-bindings` fails where a number here has moved in
either direction.

## What was written

| | |
|---|---|
| Corpus | the 86 instances of `conformance/kosit/`, 41 of them cross industry invoices and 45 of them UBL |
| Examples | the ten documents of `examples/` |
| Credit note | `../creditnote/credit-note_ubl.xml`, the one credit note of the repository |
| Read by | `StreamingReader`, the table-driven reader of this module |
| Written by | `UblWriter`, from `model/bindings/ubl-invoice.json` and `ubl-creditnote.json` and the element order of the UBL 2.1 schema modules |
| Validated by | `SyntaxValidator` of `esj-syntax` with the pack `xrechnung/3.0.2/2026-08-31` |

## The corpus

| Measure | Instances |
|---|---:|
| `accepted` — the validation artefacts report nothing fatal | 85 |
| `refused` — at least one fatal finding | 1 |
| `identical` — reading the result back gives the semantic document it was written from | 86 |
| `differing` — at least one semantic path differs | 0 |
| `total` | 86 |

**Every value of every instance survives the conversion.** Nothing the semantic model holds is
without a place in this syntax, the extension included: the XRechnung extension binds its own
terms and the core terms its sub invoice lines carry for UBL Invoice, and the writer writes the
descendant binding of a sub invoice line out to the depth the semantic path states.

The same measurement over the documents the XSLT path of `esj-xr` builds from the same corpus
gives **84 accepted** and **86 identical**. The two it refuses are the one below and
`01.02_comprehensive_test_uncefact.xml`, whose BT-8 that path takes from the cross industry
invoice in UNTDID 2475 and the UBL profile reads in UNTDID 2005 (`BR-CL-06`); the streaming
reader translates the code, which `../readers.md` records as `tax-point-date-code-list`.

## What the writer writes where the model has no term

UBL requires three elements that EN 16931-1 either has no term for or does not require, and
`model/bindings/ubl-invoice.json` and `ubl-creditnote.json` say in their `conventions` member
what is written at each of them: the element, the condition, the value, what asks for the
element and where the value comes from. The value carries no business statement, the writer
names every one it applies as `CONVENTION_APPLIED`, and the document stays complete, so
`esj validate --via ubl` runs the official artefacts over it.

| Element | Value | Instances |
|---|---|---:|
| `cac:TaxScheme/cbc:ID` | `FC` | 36 |
| `cac:OrderReference/cbc:ID` | `NA` | 7 |
| `cac:CardAccount/cbc:NetworkID` | `NA` | 6 |

`FC` is the UNTDID 1153 code for a fiscal number and the scheme identifier the cross industry
invoice binding fixes for BT-32, so a document converted from that syntax says the same thing
in both; `WriterOptions.taxRegistrationScheme` names another code, and `VAT` is refused,
because an element that carries it is BT-31. `NA` is the value
Peppol BIS Billing 3.0 records at those two elements. The purchase order reference is the one
of the three a reader has to know: `cac:OrderReference/cbc:ID` that carries `NA` beside a
`cbc:SalesOrderID` is not read as BT-13, and a document whose BT-13 really is those two letters
beside a BT-14 gets a `VALUE_READS_AS_CONVENTION` note from the writer.

## What the syntax refused, and why

| Rule | Instances | Cause |
|---|---:|---|
| `cvc-complex-type.2.4.a` | 1 | `term-the-document-does-not-state` |

One instance, `01.05_minimal_test_uncefact.xml`. UBL requires `cbc:TaxAmount` of every
`cac:TaxTotal` and the document states no BT-110; the business rules of the standard fault it
as well. The writer names it as `TERM_NOT_STATED` while it writes, so a caller hears about it
without validating, and it derives no amount: a total nobody stated is not a total.

## The examples

The ten documents of `examples/` this syntax holds whole are synthetic and structurally
complete under validation layers L1 to L3, and `examples/README.md` states for each what the
business rules of EN 16931-1, clause 6.4 make of it.

| Measure | Documents |
|---|---:|
| `structural` — the parser and the schema modules report nothing fatal | 6 |
| `ruleClean` — the Schematron artefacts report nothing fatal either | 6 |
| `identical` — reading the result back gives the document it was written from | 10 |

Every example survives the round trip. Four are refused by the schema modules for the element
above: `smallest-valid`, `minimal`, `extended` and `extension-depth` state a VAT breakdown and
no BT-110.

| Rule | Findings |
|---|---:|
| `cvc-complex-type.2.4.a` | 4 |

## The credit note

`credit-note.esj.json` of `examples/` and `../creditnote/credit-note_ubl.xml` are the two
documents that exercise `model/bindings/ubl-creditnote.json` through the writer, because no
instance of the corpus is a credit note: every one of them states the invoice type code `380`.
Both are written as a UBL Credit Note, both are accepted by the validation artefacts with no
finding at all, and both come back unchanged.

Which of the two UBL documents a semantic document becomes is a fact of BT-3 and the writer
takes it from there. The validation artefact of `packs/` admits one set of UNTDID 1001 codes on
`cbc:InvoiceTypeCode` and another on `cbc:CreditNoteTypeCode`; the codes it admits on a credit
note and not on an invoice are what `UblWriter.DocumentType.AUTO` writes a credit note for, and
`UblWriterTest` reads both sets out of that artefact and holds the list to their difference.

## Determinism

The writer produces the same bytes for the same document. Element order comes from the schema,
siblings of one name stand in the canonical order of the semantic paths that produced them, and
nothing in the output depends on a map iteration order or on the locale of the process.
`UblWriterTest` writes each corpus instance twice and compares the bytes.

Author: Christian Bürckert.
