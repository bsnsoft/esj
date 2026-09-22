# Sources

Where the material behind this project comes from, what it costs, and under which licence it
may be reused. Retrieval dates are the days the links were last checked by hand. Nothing here
reproduces text from the documents listed; the registry and the specification cite clauses.

## EN 16931-1 — the semantic model itself

EN 16931-1:2017+A1:2019 with its corrigendum AC:2020 is the normative source of the 2017
registry and of the specification; EN 16931-1:2026, the edition that supersedes it, is the
source of the 2026 registry and is the last entry of this section.

**Free of charge, English text, no registration (retrieved 2026-09-19).** The Slovak
standards body ÚNMS SR publishes the Slovak adoptions of the EN, each containing the English
version, on its page "Bezodplatné poskytovanie noriem":

- Overview page: <https://www.normoff.gov.sk/stranka/159/bezodplatne-poskytovanie-noriem/>
- EN 16931-1:2017+A1:2019 (STN EN 16931-1+A1:2020, 158 pp.):
  <https://www.normoff.gov.sk/files/docs/e-fakturacia-stn-en-16931-1-a1-614d692fbcaa2.pdf>
- EN 16931-1:2017+A1:2019/AC:2020 (corrigendum, 5 pp.):
  <https://www.normoff.gov.sk/files/docs/e-fakturacia-stn-en-16931-1-a1-ac-614d63df0ec7a.pdf>
- CEN/TS 16931-2:2017, the list of compliant syntaxes (12 pp.):
  <https://www.normoff.gov.sk/files/docs/e-fakturacia-stn-p-cen-ts-16931-2-614d642e56b09.pdf>

The free access rests on the licence agreement between the European Commission and CEN of
18 December 2018, covering parts 1 and 2 for the duration of the current version. Parts 3-1 to
3-4 (syntax bindings) and the technical reports are sold instead.

**Condition attached to that access.** A derivative application displays two statements: that it
implements the semantic data model and the two mandatory syntaxes with the permission of CEN and
the relevant member national standardisation bodies as copyright owners, and that those bear no
liability and that the official authoritative content governs; `NOTICE` carries both.

**Germany.** DIN Media lists DIN EN 16931-1 at no charge behind its registration and basket
flow (<https://www.dinmedia.de/>); other national bodies price it in the low hundreds of euros.

**EN 16931-1:2026, bought (retrieved 2026-09-20).** The 2026 edition is not free of charge. A
single copy was bought from a national standards body; it is not redistributed and no prose of it
is reproduced. `model/en16931/2026.json` and the artefacts generated from it carry identifiers,
names, cardinalities, semantic data types, supplementary components, decimal bounds and
requirement identifiers, and nothing else. The licence agreement above is documented for the 2017
version and is to be renegotiated on revision, so no permission is claimed for this edition;
`NOTICE` states the position, `model/en16931/2026.paths` the files a distribution leaves out.

## CEN validation artefacts

The official Schematron rules of CEN/TC 434 for UBL 2.1 and CII D16B, plus the ISO 6523 ICD list:
a cross-check of code lists, the oracle of the native rule pack, and what `esj validate` executes
over an XML input.

- Repository: <https://github.com/ConnectingEurope/eInvoicing-EN16931>
- Version used for the cross-check: tag `validation-1.3.16` (released 2026-04-13), retrieved
  2026-09-19; licence European Union Public Licence (EUPL) v1.2. The 1.3.16 artefacts still
  target EN 16931-1:2017+A1:2019/AC:2020.
- The compiled stylesheets of this release are redistributed here as part of a validation pack
  that `esj validate` executes: files, digests and licence in
  [`packs/SOURCES.md`](../packs/SOURCES.md), what a pack is in
  [`packs/README.md`](../packs/README.md).

## XRechnung (the German CIUS) and KoSIT

XRechnung is the German core invoice usage specification of EN 16931. Its extension supplies the
`BG-DEX-*` and `BT-DEX-*` terms of the extension registry here; their identifiers, names and
cardinalities come from table 1.1 of the syntax-binding extension document.

- Specification and syntax bindings, free download, no registration:
  <https://xeinkauf.de/dokumente/>, <https://xeinkauf.de/xrechnung/versionen-und-bundles/>.
  In force when this project started: XRechnung 3.0.2 of 2024-06-20; the 4.0.0 pre-release of
  2026-09-15 is a look-ahead. Retrieved 2026-09-19.
- Licence: the CEN/DIN licence regime of EN 16931, not open source. Link, do not redistribute.
- Semantic model schema, the machine-readable **cross-check** of the extension structure:
  <https://github.com/itplr-kosit/xrechnung-visualization>, `src/xsd/xrechnung-semantic-model.xsd`,
  tag `v2026-08-31`, retrieved 2026-09-19. Apache License, Version 2.0, attributed in `NOTICE`.
- Validator and rule set: <https://github.com/itplr-kosit/validator>,
  `.../xrechnung-schematron`, `.../xrechnung-testsuite`, Apache License, Version 2.0.
- Visualization stylesheets, **vendored** in `esj-xr`: the same repository, directory
  `src/xsl/`, tag `v2026-08-31`, retrieved 2026-09-19, Apache License, Version 2.0. Unmodified
  copies of `cii-xr.xsl`, `common-xr.xsl`, `functions.xsl`, `ubl-creditnote-xr.xsl` and
  `ubl-invoice-xr.xsl`, with the upstream `LICENSE`, a `README.md` recording the tag and the
  SHA-256 of each file, and an entry in `NOTICE`.
- XRechnung Schematron, compiled, **vendored** in a validation pack:
  <https://github.com/itplr-kosit/validator-configuration-xrechnung>, release `v2026-08-31`,
  which distributes the rules of `xrechnung-schematron` 2.6.0 as XSLT; retrieved 2026-09-19.
  Apache License, Version 2.0, with KoSIT's addendum about the standard text, beside the
  files. `XRechnung-UBL-validation.xsl` and `XRechnung-CII-validation.xsl` are executed as
  data; digests in [`packs/SOURCES.md`](../packs/SOURCES.md).
- The level each XRechnung profile gives a rule of those artefacts is transcribed from
  `scenarios.xml` of the same release: 56 statements, data of the pack manifest rather than a
  shipped file, listed in `packs/SOURCES.md`.
- Test suite instances, **copied** into the test resources of `esj-xr`:
  <https://github.com/itplr-kosit/xrechnung-testsuite>, directory `src/test/`, tag
  `v2026-08-31`, retrieved 2026-09-19. Apache License, Version 2.0. Synthetic invoices, the
  corpus the readers are run over.

## Schema modules of the two syntaxes

The XML Schema of UBL 2.1 and of the UN/CEFACT Cross Industry Invoice, **vendored** in the
validation pack and run by `esj validate` over an XML input. Both come from the XRechnung
validator configuration archive above, which redistributes them; they are published by OASIS
and by UN/CEFACT. Files, digests and the full licence statements are in
[`packs/SOURCES.md`](../packs/SOURCES.md).

- OASIS Universal Business Language 2.1 OS, release date 04 November 2013:
  <http://docs.oasis-open.org/ubl/os-UBL-2.1/>, retrieved 2026-09-19. Copyright (c) OASIS Open
  2013. Fifteen modules, the transitive import closure of the Invoice and CreditNote document
  schemas; the copyright notice and the Notices section that permits the copies are verbatim
  in the `NOTICE` beside the files.
- UN/CEFACT Cross Industry Invoice, schema version 100.D16B of 10 October 2016:
  <https://unece.org/trade/uncefact/xml-schemas>, retrieved 2026-09-19. Copyright (C)
  UN/CEFACT (2016). Four modules, the import closure of `CrossIndustryInvoice_100pD16B.xsd`,
  byte for byte the published files; the notice inside them permits unmodified copies that
  carry it, and the `NOTICE` beside them quotes it verbatim.

## Code lists behind the rule pack

The membership tests of the EN 16931 rule pack (`BR-CL-*` and the Java rules beside them) are
decided against dated snapshots under `rules/en16931/1.3.16/codelists/`, each a mechanical
extraction of codes and their publisher's short names. No prose is reproduced, and no snapshot
comes from the code list files of the CEN validation artefacts.

- URLs, SHA-256 digests, row counts, per-list notes:
  [`rules/en16931/1.3.16/codelists/SOURCES.md`](../rules/en16931/1.3.16/codelists/SOURCES.md).
- Publishers, retrieved 2026-09-19: the European Commission (the workbook *EN 16931 code lists
  values*, the Electronic Address Scheme list, the VAT Exemption Reason list), SIX Group for
  ISO 4217, the Library of Congress for ISO 639, IANA for the media types.
- **Licence: unknown.** No publisher states terms on the page the data is offered from;
  `SOURCES.md` and `NOTICE` record that as an open question. Apache-2.0 covers neither the
  snapshots nor the display-name tables derived from them, and the README carves out both.

## Peppol BIS Billing 3.0

The European CIUS operated by OpenPeppol, the practical reference for how the semantic model
is bound to UBL.

- Documentation: <https://docs.peppol.eu/poacc/billing/3.0/>
- Repository with the machine-readable structure files:
  <https://github.com/OpenPEPPOL/peppol-bis-invoice-3>. Retrieved 2026-09-19.
- Licence: none in the open-source sense. OpenPeppol AISBL holds the copyright and forbids
  modification, redistribution, sale and repackaging without prior consent. **No Peppol
  material is included in this repository**, and none of its text informs the wording here.
- Two facts are taken from it and named where they are used: the value `NA` that document
  states at `cac:OrderReference/cbc:ID` and at `cac:CardAccount/cbc:NetworkID` where the
  invoice has nothing to put there. They are conventions of the UBL binding tables with this
  source recorded beside each of them ([`bindings.md`](bindings.md)).

## Factur-X / ZUGFeRD and the PDF specifications

The container formats `esj-pdf` reads and writes; the facts taken from them — identifiers,
property names, allowed values, conventional names — are in [`pdf-input.md`](pdf-input.md) and
[`pdf-output.md`](pdf-output.md). No text of them is reproduced anywhere here.

- **Factur-X 1.0** — the French–German hybrid invoice specification, published by FNFE-MPE
  (Forum National de la Facture Électronique et des Marchés Publics Électroniques) together
  with FeRD: <https://fnfe-mpe.org/factur-x/>
- **ZUGFeRD 2.x** — the same format under its German name, published by FeRD (Forum
  elektronische Rechnung Deutschland) at AWV e. V.: <https://www.ferd-net.de/>. Retrieved
  2026-09-19.
- Licence for both: free of charge, but not open. The documents come from the publishers' own
  pages, in FeRD's case after registration, and allow neither redistribution nor republication.
  **No text of either document is in this repository**, and no sample file of either is vendored:
  the hybrid PDF of the corpus is generated from a KoSIT instance (`conformance/pdf/README.md`).
- **ZUGFeRD 1.0** — the predecessor, whose root element `CrossIndustryDocument` this project
  recognises in order to refuse it. Same publisher and same terms.
- **A further attachment beside the invoice XML.** Both specifications allow one, and PDF/A-3
  (ISO 19005-3) admits an embedded file of any type. Neither states one `AFRelationship` for such
  a file: the published guidance is `Supplement`, the value ISO 32000-2 gives a file that travels
  with the document, while sample files of ZUGFeRD 2.1 use `Data` and of Factur-X 1.0.05
  `Unspecified`. Read 2026-09-21 in the PDFlib knowledge base article *The ZUGFeRD and Factur-X
  Formats for electronic Invoices*, <https://www.pdflib.com/pdf-knowledge-base/zugferd-and-factur-x/>;
  the specification documents are behind e-mail-gated downloads. This project writes `Supplement`.

- **ISO 32000-1:2008** (PDF 1.7) — the file structure, the cross-reference table, the embedded
  file streams, the name trees and the file identifier of clause 14.4. Published free of charge
  by Adobe: <https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf>
- **ISO 32000-2** (PDF 2.0) — the source of the `AFRelationship` values. Sold by ISO; the PDF
  Association offers a no-charge copy at <https://pdfa.org/sponsored-standards/>. Retrieved
  2026-09-19. The ISO text is ISO's; the Adobe and PDF Association copies are published under
  their own no-charge terms. Neither is redistributed here.

- **Mustangproject** — an Apache-2.0 Java implementation of ZUGFeRD and Factur-X, the second
  source for the container facts and nothing else: <https://github.com/ZUGFeRD/mustangproject>.
  Retrieved 2026-09-19. **No code was copied** (`NOTICE`).

- **PDF/A (ISO 19005)** — cited for the identification schema `pdfaid`, whose two properties this
  project reads out of the XMP packet; for the extension-schema container a packet declares an
  unpredefined schema with; for part 3 as the part that allows an embedded file of any type; and
  for the level-B requirements the PDF renderer meets. Sold by ISO and the national bodies, not
  redistributed here. Conformance of this project's output is asserted against veraPDF, which
  validates every rendering and every hybrid file of a build in test scope.

## Vendored assets

Third-party files copied in unmodified, each with the upstream terms and the SHA-256 of every
file in a `README.md` beside it and a test that recomputes the digest. These four live under
`esj-render/src/main/resources/de/bsnsoft/esj/render/`; the XR stylesheets of
`esj-xr` and the artefacts under `packs/` are recorded above.

- **Liberation Sans**, regular and bold, in `fonts/` — SIL Open Font License 1.1, whose text is
  beside the files. Subset-embedded in every PDF.
- **sRGB v2 ICC profile** `sRGB2014.icc`, in `icc/` — International Color Consortium,
  <https://registry.color.org/profile-library/>, retrieved 2026-09-20. The ICC issues no
  licence file; its profile-library terms are quoted in that `README.md` and in `NOTICE`.
- **HTML visualization**, in `kosit/` — `xrechnung-html.xsl`, `common-xr.xsl`, `functions.xsl`,
  `xrechnung-viewer.css`, `xrechnung-viewer.js`, `l10n/de.xml` and `l10n/en.xml` from
  <https://github.com/itplr-kosit/xrechnung-visualization>, directory `src/xsl/`, tag
  `v2026-08-31`, retrieved 2026-09-19. Licence: Apache License, Version 2.0, beside the files.
- **FileSaver.js** `FileSaver-v2.0.5.js`, in `kosit/` — third-party material inside that
  release: copyright Eli Grey, MIT licence, <https://github.com/eligrey/FileSaver.js>. Its
  notice is shipped beside the file as `FileSaver-LICENSE.txt` and in `NOTICE`.

## Display names of codes

`esj-render/src/main/resources/de/bsnsoft/esj/render/names/` — what the letter
layout writes where the document carries a code. Five tables, checked in as data; nothing is
read from the locale data of the machine.

| Table | English names | German names |
|---|---|---|
| `invoice-type`, `unit`, `payment-means`, `vat-category` | the dated snapshots under `rules/en16931/1.3.16/codelists/`, above, except the document type 380, which reads *Invoice* as the title of a page, and the unit XPP, which reads *Packaging piece* because Rec 21 calls it what Rec 20 calls H87; `unit` adds a name for more than one | written for this project |
| `country` | the Unicode CLDR, <https://cldr.unicode.org/>, read once out of the locale data of a Java runtime by the generator `CountryNames` of `esj-render` and checked in; the header records the runtime. Unicode License v3, <https://www.unicode.org/license.txt>, copyright 1991–present Unicode, Inc., quoted in `NOTICE` | the same |

`DisplayNamesTest` compares the English half against the snapshots, code for code;
`CountryNamesTest` regenerates the country table on the runtime its header names.

## Legal texts cited in the README

- § 14 UStG, in the version in force since 1 January 2025:
  <https://www.gesetze-im-internet.de/ustg_1980/__14.html>
- BMF letter of 15 October 2025, GZ III C 2 - S 7287-a/00019/007/243, which amends section
  14.1 of the Umsatzsteuer-Anwendungserlass (UStAE), in particular paragraph 15. Published on
  <https://www.bundesfinanzministerium.de/>. Retrieved 2026-09-19.

## Other referenced specifications

- RFC 8259 (JSON), RFC 8785 (JSON Canonicalization Scheme), RFC 4648 (Base16, Base32,
  Base64): <https://www.rfc-editor.org/>
- JSON Schema 2020-12: <https://json-schema.org/specification>
- ISO 8601 date format: cited through EN 16931-1, which fixes the calendar date form.
- **EPC069-12**, the QR code guideline of the European Payments Council,
  <https://www.europeanpaymentscouncil.eu/>: the elements of the payload, their order and
  lengths, the error correction level and the bound on the payload of the EPC QR code
  ([`letter-layout.md`](letter-layout.md#the-payment-code)). No text of it is reproduced here.
- ISO 13616 (IBAN): the check digits the payment code tests an account identifier against.
