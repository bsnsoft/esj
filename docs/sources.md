# Sources

Look up where the material behind this project comes from, what it costs, and under which
licence you may reuse it. A retrieval date is the day someone last checked the link by hand.
Nothing here reproduces text from the documents listed; the registry and the specification
cite clauses.

## EN 16931-1 — the semantic model itself

EN 16931-1:2017+A1:2019 with its corrigendum AC:2020 is the normative source of the 2017
registry and of the specification. EN 16931-1:2026 supersedes it. It is the source of the 2026
registry and the last entry of this section.

**Free of charge, English text, no registration (retrieved 2026-09-19).** The Slovak
standards body ÚNMS SR publishes the Slovak adoptions of the EN on its page "Bezodplatné
poskytovanie noriem". Each contains the English version:

- Overview page: <https://www.normoff.gov.sk/stranka/159/bezodplatne-poskytovanie-noriem/>
- EN 16931-1:2017+A1:2019 (STN EN 16931-1+A1:2020, 158 pp.):
  <https://www.normoff.gov.sk/files/docs/e-fakturacia-stn-en-16931-1-a1-614d692fbcaa2.pdf>
- EN 16931-1:2017+A1:2019/AC:2020 (corrigendum, 5 pp.):
  <https://www.normoff.gov.sk/files/docs/e-fakturacia-stn-en-16931-1-a1-ac-614d63df0ec7a.pdf>
- CEN/TS 16931-2:2017, the list of compliant syntaxes (12 pp.):
  <https://www.normoff.gov.sk/files/docs/e-fakturacia-stn-p-cen-ts-16931-2-614d642e56b09.pdf>

The free access rests on the licence agreement between the European Commission and CEN of
18 December 2018, for parts 1 and 2 and the duration of the current version. CEN sells parts
3-1 to 3-4 (syntax bindings) and the technical reports.

**Condition attached to that access.** A derivative application displays two statements.
(1) It implements the semantic data model and the two mandatory syntaxes with the permission of
CEN and the relevant member national standardisation bodies as copyright owners. (2) Those bear
no liability, and the official authoritative content governs. `NOTICE` carries both.

**Germany.** DIN Media lists DIN EN 16931-1 at no charge behind its registration and basket
flow (<https://www.dinmedia.de/>). Other national bodies price it in the low hundreds of euros.

**EN 16931-1:2026, bought (retrieved 2026-09-20).** The 2026 edition is not free of charge. The
project bought a single copy from a national standards body, redistributes nothing of it and
reproduces no prose of it. `model/en16931/2026.json` and the artefacts generated from it carry
identifiers, names, cardinalities, semantic data types, supplementary components, decimal bounds
and requirement identifiers, and nothing else. The licence agreement above is documented for the
2017 version and is to be renegotiated on revision, so the project claims no permission for this
edition. `NOTICE` states the position; `model/en16931/2026.paths` lists the files a distribution
leaves out.

## CEN validation artefacts

The official Schematron rules of CEN/TC 434 for UBL 2.1 and CII D16B, plus the ISO 6523 ICD list.
They serve three purposes: a cross-check of code lists, the oracle of the native rule pack, and
what `esj validate` executes over an XML input.

- Repository: <https://github.com/ConnectingEurope/eInvoicing-EN16931>
- Version used for the cross-check: tag `validation-1.3.17` (released 2026-10-12, in effect from
  the end of 2026-11-15), retrieved 2026-10-18. Licence: European Union Public Licence (EUPL)
  v1.2. The 1.3.17 artefacts still target EN 16931-1:2017+A1:2019/AC:2020. The release before
  it, tag `validation-1.3.16` (released 2026-04-13, retrieved 2026-09-19), stays in the bundled
  pack `xrechnung/3.0.2/2026-08-31`. The rule pack `en16931/1.3.16` was measured against it.
- Releases: the European Commission's
  [registry of supporting artefacts to implement EN 16931](https://ec.europa.eu/digital-building-blocks/sites/spaces/DIGITAL/pages/467108974/Registry+of+supporting+artefacts+to+implement+EN16931)
  lists every release of the validation artefacts and of the code lists, read 2026-10-18. Both
  follow a fixed half-yearly schedule. Each round has a code freeze, a publication date and an
  effective date. The next validation release is due for publication on 2027-04-09.
- This repository redistributes the compiled stylesheets of this release inside a validation
  pack that `esj validate` executes. Files, digests and licence are in
  [`packs/SOURCES.md`](../packs/SOURCES.md); [`packs/README.md`](../packs/README.md) says what
  a pack is.

## XRechnung (the German CIUS) and KoSIT

XRechnung is the German core invoice usage specification of EN 16931. Its extension supplies the
`BG-DEX-*` and `BT-DEX-*` terms of the extension registry here. Their identifiers, names and
cardinalities come from table 1.1 of the syntax-binding extension document.

- Specification and syntax bindings, free download, no registration:
  <https://xeinkauf.de/dokumente/>, <https://xeinkauf.de/xrechnung/versionen-und-bundles/>.
  In force when this project started: XRechnung 3.0.2 of 2024-06-20. The 4.0.0 pre-release of
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
  `ubl-invoice-xr.xsl`. Beside them: the upstream `LICENSE`, a `README.md` that records the tag
  and the SHA-256 of each file, and an entry in `NOTICE`.
- XRechnung Schematron, compiled, **vendored** in a validation pack:
  <https://github.com/itplr-kosit/validator-configuration-xrechnung>, release `v2026-08-31`,
  retrieved 2026-09-19. It distributes the rules of `xrechnung-schematron` 2.6.0 as XSLT.
  Apache License, Version 2.0, with KoSIT's addendum about the standard text, beside the
  files. `esj validate` executes `XRechnung-UBL-validation.xsl` and
  `XRechnung-CII-validation.xsl` as data; digests in [`packs/SOURCES.md`](../packs/SOURCES.md).
- The level each XRechnung profile gives a rule of those artefacts is transcribed from
  `scenarios.xml` of the same release. These 56 statements are data of the pack manifest, not
  a shipped file, and `packs/SOURCES.md` lists them.
- Test suite instances, **copied** into the test resources of `esj-xr`:
  <https://github.com/itplr-kosit/xrechnung-testsuite>, directory `src/test/`, tag
  `v2026-08-31`, retrieved 2026-09-19. Apache License, Version 2.0. They are synthetic
  invoices, the corpus the readers run over.

## Schema modules of the two syntaxes

`esj validate` runs the XML Schema of UBL 2.1 and of the UN/CEFACT Cross Industry Invoice over
an XML input; both are **vendored** in the validation pack. They come from the XRechnung
validator configuration archive above, which redistributes them. OASIS and UN/CEFACT publish
them. Files, digests and the full licence statements are in
[`packs/SOURCES.md`](../packs/SOURCES.md).

- OASIS Universal Business Language 2.1 OS, release date 04 November 2013:
  <http://docs.oasis-open.org/ubl/os-UBL-2.1/>, retrieved 2026-09-19. Copyright (c) OASIS Open
  2013. 15 modules, the transitive import closure of the Invoice and CreditNote document
  schemas. The `NOTICE` beside the files carries the copyright notice and the Notices section
  that permits the copies, verbatim.
- UN/CEFACT Cross Industry Invoice, schema version 100.D16B of 10 October 2016:
  <https://unece.org/trade/uncefact/xml-schemas>, retrieved 2026-09-19. Copyright (C)
  UN/CEFACT (2016). 4 modules, the import closure of `CrossIndustryInvoice_100pD16B.xsd`, byte
  for byte the published files. Their notice permits unmodified copies that carry it; the
  `NOTICE` beside them quotes it verbatim.

## Code lists behind the rule pack

The EN 16931 rule pack decides its membership tests (`BR-CL-*` and the Java rules beside them)
against dated snapshots under `rules/en16931/1.3.17/codelists/`. Each snapshot is a mechanical
extraction of codes and their publisher's short names. No prose is reproduced, and no snapshot
comes from the code list files of the CEN validation artefacts.

- URLs, SHA-256 digests, row counts, per-list notes:
  [`rules/en16931/1.3.17/codelists/SOURCES.md`](../rules/en16931/1.3.17/codelists/SOURCES.md).
- Publishers, retrieved 2026-10-18: the European Commission (the workbook *EN 16931 code lists
  values* version 18, the Electronic Address Scheme list version 17, the VAT Exemption Reason
  list version 9), SIX Group for ISO 4217, the Library of Congress for ISO 639, IANA for the
  media types.
- **Licence: unknown.** No publisher states terms on the page the data is offered from.
  `SOURCES.md` and `NOTICE` record that as an open question. Apache-2.0 covers neither the
  snapshots nor the display-name tables derived from them, and the README carves out both.

## Peppol BIS Billing 3.0

The European CIUS operated by OpenPeppol, the practical reference for how the semantic model
binds to UBL.

- Documentation: <https://docs.peppol.eu/poacc/billing/3.0/>
- Repository with the machine-readable structure files:
  <https://github.com/OpenPEPPOL/peppol-bis-invoice-3>. Retrieved 2026-09-19.
- Licence: none in the open-source sense. OpenPeppol AISBL holds the copyright and forbids
  modification, redistribution, sale and repackaging without prior consent. **No Peppol
  material is included in this repository**, and none of its text informs the wording here.
  Checked again 2026-09-30: the repository carries no licence file (GitHub API:
  `license: null`), and its `README.adoc` states none. The Peppol Schematron files name only
  CEN's permission. The statement of copyright in the BIS document at
  <https://docs.peppol.eu/poacc/billing/3.0/bis/> is unchanged.
- Validation artefacts: you fetch them, ESJ never ships them. Two recipes each name the 4
  Schematron files of `rules/sch/` of one release, with their SHA-256, and `esj packs fetch`
  downloads and compiles them on your machine ([`validation.md`](validation.md#packs-made-on-this-machine)).
  - [`../packs/recipes/peppol-bis-billing-3.0.21.json`](../packs/recipes/peppol-bis-billing-3.0.21.json):
    release 3.0.21 ("2026 May release", published 2026-05-20, mandatory from 2026-08-17
    according to the release notes at <https://docs.peppol.eu/poacc/billing/3.0/release-notes/>,
    read 2026-10-06). OpenPeppol did not tag it. The recipe fetches the files below commit
    `806866bd2bd91d7e9623b68f08164e8fbe9e67a0` of the branch `2026-Q2-QA2` (2026-06-10). On
    2026-10-06 its two UBL files were byte-identical to the ones published under
    <https://docs.peppol.eu/poacc/billing/3.0/files/>; the CII files are published only in the
    repository.
  - [`../packs/recipes/peppol-bis-billing-3.0.20.json`](../packs/recipes/peppol-bis-billing-3.0.20.json):
    tag `v3.0.20` (commit `261c458474e27d58a25be629cccac28883171c92`, "BIS version 3.0.20: 2025
    November Release", published 2026-03-16).
  - The two CEN files of 3.0.21 state version 1.3.16 of 2026-04-10, those of 3.0.20 version
    1.3.15 of 2025-10-16 (its `rules/sch/README.md` still names 1.3.14.1). They are under the
    EUPL 1.2. The recipe fetches them with the rest, because a pack is one release of one
    publisher. The evidence fetches the examples and unit tests it runs at test time, listed
    with their digests in [`../conformance/peppol/README.md`](../conformance/peppol/README.md).
- Two facts come from it and are named where they are used: the value `NA` that document
  states at `cac:OrderReference/cbc:ID` and at `cac:CardAccount/cbc:NetworkID` where the
  invoice has nothing to put there. They are conventions of the UBL binding tables, each with
  this source recorded beside it ([`bindings.md`](bindings.md)).

## Factur-X / ZUGFeRD and the PDF specifications

`esj-pdf` reads and writes these container formats. The facts taken from them (identifiers,
property names, allowed values, conventional names) are in [`pdf-input.md`](pdf-input.md) and
[`pdf-output.md`](pdf-output.md). No text of them is reproduced anywhere here.

- **Factur-X 1.0**: the French–German hybrid invoice specification, published by FNFE-MPE
  (Forum National de la Facture Électronique et des Marchés Publics Électroniques) together
  with FeRD: <https://fnfe-mpe.org/factur-x/>
- **ZUGFeRD 2.x**: the same format under its German name, published by FeRD (Forum
  elektronische Rechnung Deutschland) at AWV e. V.: <https://www.ferd-net.de/>. Retrieved
  2026-09-19.
- Licence for both: free of charge, but not open. You get the documents from the publishers'
  own pages, from FeRD after registration. They allow neither redistribution nor
  republication. **No text of either document is in this repository**, and no sample file of
  either is vendored. The hybrid PDF of the corpus is generated from a KoSIT instance
  (`conformance/pdf/README.md`).
- **ZUGFeRD 1.0**: the predecessor. This project recognises its root element
  `CrossIndustryDocument` in order to refuse it. Same publisher and same terms.
- **A further attachment beside the invoice XML.** Both specifications allow one, and PDF/A-3
  (ISO 19005-3) admits an embedded file of any type. Neither states one `AFRelationship` for
  such a file. The published guidance is `Supplement`, the value ISO 32000-2 gives a file that
  travels with the document. Sample files of ZUGFeRD 2.1 use `Data`, those of Factur-X 1.0.05
  `Unspecified`. Read 2026-09-21 in the PDFlib knowledge base article *The ZUGFeRD and Factur-X
  Formats for electronic Invoices*, <https://www.pdflib.com/pdf-knowledge-base/zugferd-and-factur-x/>.
  The specification documents are behind e-mail-gated downloads.
- **ISO 32000-1:2008** (PDF 1.7): the file structure, the cross-reference table, the embedded
  file streams, the name trees and the file identifier of clause 14.4. Adobe publishes it free
  of charge: <https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf>
- **ISO 32000-2** (PDF 2.0): the source of the `AFRelationship` values. ISO sells it; the PDF
  Association offers a no-charge copy at <https://pdfa.org/sponsored-standards/>. Retrieved
  2026-09-19. The ISO text is ISO's; the Adobe and PDF Association copies are published under
  their own no-charge terms. Neither is redistributed here.
- **Mustangproject**: an Apache-2.0 Java implementation of ZUGFeRD and Factur-X, the second
  source for the container facts and nothing else: <https://github.com/ZUGFeRD/mustangproject>.
  Retrieved 2026-09-19. **No code was copied** (`NOTICE`).
- **PDF/A (ISO 19005)**: cited for the identification schema `pdfaid`, whose two properties
  this project reads out of the XMP packet, and for the extension-schema container with which a
  packet declares an unpredefined schema. Also for part 3, which allows an embedded file of any
  type, and for the level-B requirements the PDF renderer meets. ISO and the national bodies
  sell it; it is not redistributed here. veraPDF checks this project's output: it validates
  every rendering and every hybrid file of a build in test scope.

## Vendored assets

Third-party files copied in unmodified. Beside each set sits a `README.md` with the upstream
terms and the SHA-256 of every file, and a test recomputes the digest. The first 4 sets live
under `esj-render/src/main/resources/de/bsnsoft/esj/render/`, the fifth under
`esj-syntax/src/main/resources/de/bsnsoft/esj/syntax/`. The sections above record the XR
stylesheets of `esj-xr` and the artefacts under `packs/`.

- **Liberation Sans**, regular and bold, in `fonts/`: SIL Open Font License 1.1, whose text is
  beside the files. Subset-embedded in every PDF.
- **sRGB v2 ICC profile** `sRGB2014.icc`, in `icc/`: International Color Consortium,
  <https://registry.color.org/rgb-registry/profiles/sRGB2014.icc>, byte-identical (SHA-256) on
  2026-10-07. The ICC issues no licence file; that `README.md` and `NOTICE` quote its terms.
- **HTML visualization**, in `kosit/`: `xrechnung-html.xsl`, `common-xr.xsl`, `functions.xsl`,
  `xrechnung-viewer.css`, `xrechnung-viewer.js`, `l10n/de.xml` and `l10n/en.xml` from
  <https://github.com/itplr-kosit/xrechnung-visualization>, directory `src/xsl/`, tag
  `v2026-08-31`, retrieved 2026-09-19. Licence: Apache License, Version 2.0, beside the files.
- **FileSaver.js** `FileSaver-v2.0.5.js`, in `kosit/`: third-party material inside that
  release, copyright Eli Grey, MIT licence, <https://github.com/eligrey/FileSaver.js>. Its
  notice ships beside the file as `FileSaver-LICENSE.txt` and in `NOTICE`.
- **ISO Schematron XSLT 2 skeleton**, 2010 release, in `schematron/`: `iso_dsdl_include.xsl`,
  `iso_abstract_expand.xsl`, `iso_svrl_for_xslt2.xsl` and `iso_schematron_skeleton_for_saxon.xsl`
  by Rick Jelliffe with the Academia Sinica Computing Centre, Taiwan, under the MIT licence
  stated at the head of each file. Their version notes record the move from an earlier
  permissive notice to MIT on 2010-07-10.
  - Copied byte for byte from `external/schematron/20100710-xslt2/` of the Maven Central
    artefact `com.helger.schematron:ph-schematron-xslt:8.0.6` (Apache-2.0, Philip Helger; jar
    SHA-256 `eac96bfab38187cfc22bb98b227f4e515beffb8ebf24a8bbcb38b392c41d169b`), retrieved
    2026-09-30. That copy carries 7 changes marked `[ph]`, among them the stylesheet version
    raised to 2.0.
  - SHA-256 of the files: `e59976a934bcffba9998607f4fce5009130d69f9530ebee9a858aa289ac03412`
    (`iso_dsdl_include.xsl`), `16e8e7da796100de38446e64c4b2845c318e316a35c66098381b6846f6c89aac`
    (`iso_abstract_expand.xsl`), `a3a497c2e792700575111bce2bc646c435edc5296334c16a29fc7a114c2fa951`
    (`iso_svrl_for_xslt2.xsl`), `3fa57d52a633d2f73214e85f22005a40db3a6c9f7d7917c73e3ed5a6973c0787`
    (`iso_schematron_skeleton_for_saxon.xsl`). `esj packs fetch` compiles the Schematron of a
    recipe with them; no bundled pack is compiled with them.

## Display names of codes

`esj-render/src/main/resources/de/bsnsoft/esj/render/names/` holds what the letter layout
writes where the document carries a code: 5 tables, checked in as data. Nothing is read from
the locale data of the machine.

| Table | English names | German names |
|---|---|---|
| `invoice-type`, `unit`, `payment-means`, `vat-category` | the dated snapshots under `rules/en16931/1.3.17/codelists/`, above, except the document type 380, which reads *Invoice* as the title of a page, and the unit XPP, which reads *Packaging piece* because Rec 21 calls it what Rec 20 calls H87; `unit` adds a name for more than one | written for this project |
| `country` | the Unicode CLDR, <https://cldr.unicode.org/>, read once out of the locale data of a Java runtime by the generator `CountryNames` of `esj-render` and checked in; the header records the runtime. Unicode License v3, <https://www.unicode.org/license.txt>, copyright 1991–present Unicode, Inc., quoted in `NOTICE` | the same |

`DisplayNamesTest` compares the English half against the snapshots, code for code.
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
