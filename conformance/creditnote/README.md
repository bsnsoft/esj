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
