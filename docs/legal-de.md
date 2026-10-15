# Legal note (Germany)

*Part of [EN16931 Semantic JSON](../README.md).*

Since 1 January 2025 an electronic invoice under German VAT law must use a structured
electronic format. § 14 Abs. 1 Satz 6 UStG offers two routes: a format conforming to
EN 16931 and to the syntaxes of Directive 2014/55/EU, or a format agreed between issuer and
recipient that allows the correct and complete extraction of the information the UStG
requires into a format conforming to EN 16931 or interoperable with it. Section 14.1
paragraph 15 of the Umsatzsteuer-Anwendungserlass, as amended by the BMF letter of 15 October
2025, defines interoperable for that purpose: the information must be processable from the
original format without loss, and there is a loss when the content or the meaning of an item
changes or is no longer recognisable.

ESJ is built to make that extraction mechanical: values are keyed by EN 16931 business terms,
the registry records the semantic data type of each term, and the canonical form makes the
result of an extraction byte-comparable. Whether a concrete use of ESJ meets those
requirements is for the parties to assess, and an agreement between them is a precondition.
B2G invoicing and the Peppol network are outside the scope of this project.

## Checked against the BMF letter of 15 October 2026

No change to the format, the registries, the bindings or the rule packs follows from the
[BMF letter of 15 October 2026](https://www.bundesfinanzministerium.de/Content/DE/Downloads/BMF_Schreiben/Steuerarten/Umsatzsteuer/Umsatzsteuer-Anwendungserlass/2026-10-15-obligatorische-elektronische-rechnung.html) (III C 5 - S 7287-a/00019/021/017), checked
paragraph by paragraph on 2026-10-15. It applies to supplies from 1 January 2025, with a transition until
31 December 2027; an e-invoice issued until 31 December 2026 that follows the letters of
15 October 2024 and 15 October 2025 is not objected to.

| Rn. | What the letter says | What ESJ offers |
|---|---|---|
| 3 f. | a final invoice after partial invoices may, until 30 June 2030, deduct the partial amounts paid as one sum in BT-113, the details in BT-22 or in an embedded attachment (BG-24) | BT-113 is input, and `derive()` computes BT-115 from it by BR-CO-16; the domain API writes BT-22 (`note`), the partial invoices in BG-3 (`precedingInvoice`) and embedded attachments (`Attachment.embedded`) |
| 8 f. | KoSIT and FeRD map the mandatory details of §§ 14, 14a UStG to business terms and groups ([KoSIT](https://xeinkauf.de/aktuelles/xrechnung/pflichtangaben-nach-ustg-en16931), [FeRD](https://www.ferd-net.de/faqs/pflichtangaben-in-e-rechnungen)) | those mandatory details are not checked in this version |
| 10–12 | the description of the supply belongs in the structured part; further detail may go into an embedded attachment | BT-153 and BT-154 per line; further detail as an embedded attachment (BG-24, BT-125) |
| 13 f. | construction work: the trades as invoice lines, the bill of quantities as an embedded human-readable attachment, or structured through an extension | BG-24 with BT-125, or the sub-lines BG-DEX-01 of the XRechnung extension, read and written in UBL and CII (`--extension xrechnung`) |
| 17–19 | gross-price invoices | [`b2c.md`](b2c.md#deriving-the-net-invoice) |
| 20 f. | a correction is an e-invoice of a correction type | BT-3 384, a code of the credit-note family or 389, the corrected invoice in BG-3; the letter layout prints that reference under the title ([`letter-layout.md`](letter-layout.md#document-types)) |
| 22 f. | format errors, business-rule errors and content errors are told apart for VAT | the verdict is technical conformance ([`validation.md`](validation.md#verdict-and-exit-code)) |
| II | UStAE 14.1 and 14.5 now say *eingebettet*: supplementary details travel inside the e-invoice | an attachment is embedded (BT-125), not referenced by URL (BT-124); in a hybrid PDF the XML is the invoice, so the attachment belongs inside the XML |

This page states the legal texts it cites; it is not legal advice.
