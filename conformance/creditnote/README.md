# A credit note, written by hand

The conformance corpus under `conformance/kosit/` holds 86 instances: 41 cross industry
invoices and 45 UBL Invoices, and not one UBL Credit Note. `model/bindings/ubl-creditnote.json`
is therefore the one binding table of this release that the corpus does not exercise at all,
and four defects of that table were found the moment a credit note existed to read:

- the project reference BT-11 was bound to every `cac:AdditionalDocumentReference`, the same
  element the supporting document reference BT-122 is bound to, so every supporting document
  of a credit note was read as a project reference the document does not state;
- the invoiced object identifier BT-18 was bound with a document type code that the code list
  of that element does not contain, so no conformant credit note could carry it;
- the buyer VAT identifier BT-48 and the tax representative's BT-63 were bound without the
  condition that tells the value added tax registration from another one, so a party stating
  both had the wrong one read;
- the supporting document group BG-24 and its four terms were bound to every
  `cac:AdditionalDocumentReference`, so the project reference and the invoiced object
  identifier each opened a supporting document group of their own and moved the occurrence
  index of the real one.

The `corrections` member of the table records all four. This directory holds the document
that shows them, so that the table stays measured.

## The document

`credit-note_ubl.xml` is written for this repository — nothing in it comes from any other
source — and is licensed Apache-2.0 like the rest of the repository. It validates against
`UBL-CreditNote-2.1.xsd` of the shipped pack. Its values are those of
`examples/credit-note.esj.json`, plus the elements that make the three defects visible:

- three `cac:AdditionalDocumentReference` elements, with the document type codes 50, 130 and
  916, so that BT-11, BT-18 and BT-122 each have to find their own;
- a buyer and a tax representative that each state a tax registration, the buyer with a
  second registration under another tax scheme written before the value added tax one;
- a tax breakdown and a line item whose tax category names the value added tax scheme.

`credit-note_ubl.esj.json` is what the streaming reader of `esj-bindings` builds from it,
in the pretty form, exactly as `conformance/esj/` holds the corpus documents.

## What is measured

`CreditNoteReaderTest` in `esj-bindings` reads the document with both readers and compares
them value by value, the way `conformance/readers.md` does for the corpus. The two readers
agree on every business term except the occurrence indices of BG-24:

| Path | Streaming reader | XSLT path |
|---|---|---|
| `/BG-24/0/BT-122` | `ATT-1` | `OBJ-42` |
| `/BG-24/0/BT-123` | `Return delivery note` | — |
| `/BG-24/1/BT-122` | — | `ATT-1` |
| `/BG-24/1/BT-123` | — | `Return delivery note` |

The table keeps BG-24 off the `cac:AdditionalDocumentReference` whose document type code is
130 or 50, because those two carry BT-18 and BT-11; the KoSIT stylesheet leaves out only the
one whose code is 50. The stylesheet therefore reads the invoiced object identifier as a
supporting document as well, and the index of the real supporting document moves by one. The
CEN validation artefact of `packs/` writes the exclusion of 130 that the table follows;
`conformance/bindings/crosscheck.md` records the difference.

## The extension: sub credit note lines

`04.01a-CREDITNOTE_ubl.xml` is a modified copy of
`../kosit/business-cases/extension/04.01a-INVOICE_ubl.xml` of the XRechnung test suite (KoSIT,
Apache License 2.0, tag v2026-08-31; `NOTICE` carries the attribution). The changes, and
nothing else:

| Invoice | Credit note |
|---|---|
| `ubl:Invoice` in the Invoice namespace | `ubl:CreditNote` in the CreditNote namespace |
| `cbc:InvoiceTypeCode` `380` | `cbc:CreditNoteTypeCode` `381` |
| `cac:InvoiceLine` | `cac:CreditNoteLine` |
| `cac:SubInvoiceLine` | `cac:SubCreditNoteLine` |
| `cbc:InvoicedQuantity` | `cbc:CreditedQuantity` |

plus a comment before the root element that says so. It validates against the UBL 2.1 XSD, the
CEN Schematron and the XRechnung Schematron of `packs/` with no finding.
`04.01a-CREDITNOTE_ubl.esj.json` is what the streaming reader builds from it.

The tailoring model of the extension binds the sub invoice line for UBL Invoice only, so until
0.9.4 `ubl-creditnote.json` left the thirteen sub lines of this document unread — 130 values,
without an observation. Two corrections of that table carry them now
(`../../model/bindings/README.md`, "Corrections").

`CreditNoteExtensionTest` in `esj-bindings` asserts:

- the streaming reader reads every sub line at both depths, quantity from `cbc:CreditedQuantity`;
- the document says what the invoice of the corpus says — the two differ at `/BT-3` and nowhere
  else;
- the pretty form of what the streaming reader builds is the checked-in file;
- written by `UblWriter`, the document is a UBL Credit Note with thirteen
  `cac:SubCreditNoteLine` elements, the pack accepts it, and reading it gives the document back.

### Where the XSLT path parts

The vendored `ubl-creditnote-xr.xsl` of KoSIT matches `cac:SubCreditNoteLine`, but reads the
terms of a sub line from absolute paths of the credit note line, so every sub line carries the
values of the first credit note line of the document; it does not descend into a nested sub
line, and it matches the sub line VAT group on `cac:SubCreditNoteLine/cac:ClassifiedTaxCategory`,
an element the schema does not have. `esj convert --importer xslt` therefore reads 32 values
inside the sub lines where the streaming reader reads 130, and 114 paths differ, every one of
them inside `BG-DEX-01`. The stylesheet stays byte-identical to the KoSIT release and is not a
source of the binding; `../bindings/crosscheck.md` records the difference for BG-DEX-06.
