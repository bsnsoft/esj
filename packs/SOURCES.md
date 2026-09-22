# Sources of the vendored validation packs

Everything under `packs/` is third-party material, copied **unmodified** from a published
release of its owner. This file records, for every component, where it was published, which
release it is, under which licence it is distributed, the SHA-256 of the upstream archive and
of each vendored file, and the day the bytes were fetched.

Two files in each pack are written by this project rather than copied: `pack.json`, the
manifest, and the `NOTICE` beside each schema set, whose body is the notice comment at the head
of the schema files themselves, quoted verbatim. Nothing else here is ours; the manifest itself
carries one body of third-party material, the levels each profile gives rules, recorded below
with the rest.

The digests below are checked on every build. `PackContentsTest` in `esj-syntax` recomputes
each one over the bytes as they are packaged and fails on any difference, on a missing file and
on a file the inventory of `pack.json` does not list.

---

## Pack `xrechnung/3.0.2/2026-08-31`

Retrieved **2026-09-19**. 28 files, 2,814,010 bytes (2.68 MiB).

### Upstream archives

| Archive | URL | sha256 |
|---|---|---|
| `en16931-ubl-1.3.16.zip` | <https://github.com/ConnectingEurope/eInvoicing-EN16931/releases/download/validation-1.3.16/en16931-ubl-1.3.16.zip> | `bafada015efbc5248bf5e05ad2191e1d9833ef96e9dd5f4bce420a747342da85` |
| `en16931-cii-1.3.16.zip` | <https://github.com/ConnectingEurope/eInvoicing-EN16931/releases/download/validation-1.3.16/en16931-cii-1.3.16.zip> | `1cd53cb8a84d38aedc82c0caede217da983a7934dd663f793a092fd66443c561` |
| `LICENSE.txt` of the same tag | <https://raw.githubusercontent.com/ConnectingEurope/eInvoicing-EN16931/validation-1.3.16/LICENSE.txt> | `fc22ec1dcd8bee4636a395fb332e2308cde870fb3fdc71a2e260b919877cdef5` |
| `xrechnung-3.0.2-validator-configuration-2026-08-31.zip` | <https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/download/v2026-08-31/xrechnung-3.0.2-validator-configuration-2026-08-31.zip> | `2530cd107c414511c5d0462ec10f886910395abfca820db82e83d70bf01221a8` |
| `LICENSE` of the same tag | <https://raw.githubusercontent.com/itplr-kosit/validator-configuration-xrechnung/v2026-08-31/LICENSE> | `73f2898ad26e1fcb9ec4649cdf8466ea0bda47644b7c12efc10a703bdfb94a9f` |

The two licence files are fetched from the source tree at the same tag, because neither release
archive carries one.

### Component: EN 16931 Schematron, compiled (`cen/1.3.16/`)

| Item | Value |
|---|---|
| Publisher | CEN/TC 434 via the Connecting Europe eInvoicing project |
| Project | <https://github.com/ConnectingEurope/eInvoicing-EN16931> |
| Release | `validation-1.3.16`, "EN16931 Validation artefacts v1.3.16" |
| Taken from | `xslt/` of the two release archives above |
| Licence | European Union Public Licence (EUPL) v1.2, SPDX `EUPL-1.2` |
| Licence file | `cen/1.3.16/LICENSE` |
| Modified | no |

The licence file opens with the sentence

> Licensed under European Union Public Licence (EUPL) version 1.2.

and the release notes of 1.3.16 repeat it. The stylesheets are the publisher's own compilation
of the Schematron rules of that release; they are executed as data and nothing in this
repository is derived from the XPath expressions inside them.

| File | sha256 |
|---|---|
| `cen/1.3.16/EN16931-UBL-validation.xslt` | `39f9d282867f1a49e7708d9e29a53da89643e1ee56f10cec1ebcf1277595fcbd` |
| `cen/1.3.16/EN16931-CII-validation.xslt` | `0b234dea2bbfee739b7761e607a992c17fab88773014ef56355b6158cfb1cc53` |
| `cen/1.3.16/LICENSE` | `fc22ec1dcd8bee4636a395fb332e2308cde870fb3fdc71a2e260b919877cdef5` |

The XRechnung validator configuration of the same date ships its own compilation of the same
1.3.16 rules. This pack takes the CEN artefacts from the CEN release instead, so that the
EN 16931 rules in a report are the ones their own publisher released; the two compilations
differ in bytes because they were produced by different Schematron compilers.

### Component: XRechnung Schematron, compiled (`xrechnung-schematron/2.6.0/`)

| Item | Value |
|---|---|
| Publisher | Koordinierungsstelle für IT-Standards (KoSIT) / XStandards Einkauf |
| Rules project | <https://projekte.kosit.org/xrechnung/xrechnung-schematron> |
| Rules version | 2.6.0, as named in the component table of the XRechnung 3.0.2 bundle of 2026-08-31 |
| Distributed in | <https://github.com/itplr-kosit/validator-configuration-xrechnung>, release `v2026-08-31` |
| Taken from | `resources/xrechnung/3.0.2/xsl/` of that archive |
| Licence | Apache License, Version 2.0, SPDX `Apache-2.0`, with an addendum about the standard text |
| Licence file | `xrechnung-schematron/2.6.0/LICENSE` |
| Modified | no |

The licence file is the Apache License 2.0 followed by an addendum in German. The Apache text
grants use under the sentence

> Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
> except in compliance with the License.

The addendum concerns the standard behind the rules rather than the rules themselves: it states
that XRechnung falls under the licence agreement between the European Commission and CEN, that
the copyright in EN 16931 rests with CEN, that copies of the standard are distributed by the
national standards bodies — in Germany DIN — and that reproduction of XRechnung as an
implementation of that data model is permitted by CEN and DIN, who give no warranty for such
derivatives. The same addendum is in the licence file of every KoSIT XRechnung component.

| File | sha256 |
|---|---|
| `xrechnung-schematron/2.6.0/XRechnung-UBL-validation.xsl` | `e32d831668facbbd5136cc0119bf1dd6f92ac20738aa3b3f021586f5a305c738` |
| `xrechnung-schematron/2.6.0/XRechnung-CII-validation.xsl` | `ec54f662803a87f527ec606d1320bc404f1657a848af6a76f37f32905e4aac40` |
| `xrechnung-schematron/2.6.0/LICENSE` | `73f2898ad26e1fcb9ec4649cdf8466ea0bda47644b7c12efc10a703bdfb94a9f` |

### Component: UBL 2.1 schema modules (`xsd/ubl-2.1/`)

| Item | Value |
|---|---|
| Publisher | OASIS Open |
| Published at | <http://docs.oasis-open.org/ubl/os-UBL-2.1/> |
| Version | Universal Business Language 2.1 OS, release date 04 November 2013 |
| Obtained from | `resources/ubl/2.1/xsd/` of the XRechnung validator configuration archive |
| Licence | OASIS material. The permission is the Notices section of the UBL 2.1 OS specification, under the OASIS IPR Policy; SPDX `LicenseRef-OASIS-UBL-2.1` resolves to it |
| Notice file | `xsd/ubl-2.1/NOTICE` |
| Modified | no |

Every file carries its notice in a comment at its head, and that comment grants nothing: it is
an identification block ending in

> Copyright (c) OASIS Open 2013. All Rights Reserved.

The permission to copy and redistribute is elsewhere — in the Notices section of the UBL 2.1 OS
specification (<http://docs.oasis-open.org/ubl/os-UBL-2.1/UBL-2.1.html>, retrieved 2026-09-19),
which permits copying and distribution, in whole or in part, **provided that the copyright
notice and that section accompany the copies**. So both have to travel with these files, and
both are in `xsd/ubl-2.1/NOTICE`: the file header verbatim, and the Notices section verbatim
after it. The component next door needs no such addition only because UN/CEFACT put its
permission paragraph inside its own files.

There is no SPDX identifier for the terms OASIS publishes its standards under, so the manifest
uses the `LicenseRef-OASIS-UBL-2.1` name, and what it resolves to is that Notices section as
carried in `xsd/ubl-2.1/NOTICE`.

The fifteen files are the transitive `schemaLocation` closure of `UBL-Invoice-2.1.xsd` and
`UBL-CreditNote-2.1.xsd` — the two document schemas the syntax engine validates against — and
not the whole UBL schema library. The closure is computed from the files rather than listed by
hand.

| File | sha256 |
|---|---|
| `xsd/ubl-2.1/maindoc/UBL-Invoice-2.1.xsd` | `3a5aacd823f0e5b8f25ae7b5191c2002d5333ba351d87de9371ed62dca2b2b0c` |
| `xsd/ubl-2.1/maindoc/UBL-CreditNote-2.1.xsd` | `1e117b6c1ab713604b29b0d685442a81a0c78a82575445ee8c43e6050cda7454` |
| `xsd/ubl-2.1/common/CCTS_CCT_SchemaModule-2.1.xsd` | `dd546e4809df86b6445589f69f0d6c9df162840ae386574ddfc1da7638103e15` |
| `xsd/ubl-2.1/common/UBL-CommonAggregateComponents-2.1.xsd` | `580b5af6f68f7f556bd15945ba0e819cbf561442c81694f9b9468036ebedec4d` |
| `xsd/ubl-2.1/common/UBL-CommonBasicComponents-2.1.xsd` | `a3b349bf92e5cff26e303d2f05b7e00c884dcafcfd9e12755fb80289abf5e22e` |
| `xsd/ubl-2.1/common/UBL-CommonExtensionComponents-2.1.xsd` | `ad7a4e490978adfbcfc5ec0bb20941cf11ac960ccf0c4de8791a7c731a8dbe87` |
| `xsd/ubl-2.1/common/UBL-CommonSignatureComponents-2.1.xsd` | `4fa9e2370100040fe14c43e135ef77e2eb66b21cb8dbfc2ffb8d82ae991fe92e` |
| `xsd/ubl-2.1/common/UBL-ExtensionContentDataType-2.1.xsd` | `fcee77a11870208e6377ea6311b9f2a050bca24bdad8606ea02d71e9f9e72f8d` |
| `xsd/ubl-2.1/common/UBL-QualifiedDataTypes-2.1.xsd` | `7dcb156e610239c97ae70940cf4653b88e48c3595bf5f56a2204a32e2893e6cf` |
| `xsd/ubl-2.1/common/UBL-SignatureAggregateComponents-2.1.xsd` | `9234c2ca48dbfa9a22a786112bb075c5922a305170920eaab1e3c04fa0b7344b` |
| `xsd/ubl-2.1/common/UBL-SignatureBasicComponents-2.1.xsd` | `0fbe2d7afff0c1e11164b8ec83e13f18801021c3c87e390a9d76f9cf862f6a64` |
| `xsd/ubl-2.1/common/UBL-UnqualifiedDataTypes-2.1.xsd` | `09052d406b4293e2a5f9c2bfee6df10ad4d8d5f0b36e24a6349d7f7936d89eb6` |
| `xsd/ubl-2.1/common/UBL-XAdESv132-2.1.xsd` | `a4f726bcf8cc3f7d9ffa4dab99e005535a8e8b60dced1e5d94578d2e05afa96e` |
| `xsd/ubl-2.1/common/UBL-XAdESv141-2.1.xsd` | `1fa4625e9cefcb7a9abb5ac1b64315547450031eece8a55bd584e4ba4b79dbc1` |
| `xsd/ubl-2.1/common/UBL-xmldsig-core-schema-2.1.xsd` | `101909c9f06456d61ddcc4fb982f1d40dc357b439f393b1a2eb46e42acd60809` |
| `xsd/ubl-2.1/NOTICE` | `8e844cbf09aea02acc352b5403129d066c6a4f165e88fbf1ba6abd79e3587e39` |

### Component: UN/CEFACT CII D16B schema modules (`xsd/cii-d16b/`)

| Item | Value |
|---|---|
| Publisher | UN/CEFACT |
| Published at | <https://unece.org/trade/uncefact/xml-schemas> |
| Version | Cross Industry Invoice, schema version 100.D16B, schema date 10 October 2016 |
| Obtained from | `resources/cii/16b/xsd/` of the XRechnung validator configuration archive |
| Licence | UN/CEFACT copyright notice in the files, SPDX `LicenseRef-UN-CEFACT-D16B` |
| Notice file | `xsd/cii-d16b/NOTICE` |
| Modified | no |

The notice in the files permits copying and redistribution provided the copyright notice and
the permission paragraph travel with the copies, and forbids modifying the documents. Both
conditions are met here: the files are byte for byte the published ones, the notice is inside
each of them, and `xsd/cii-d16b/NOTICE` quotes it verbatim beside them. Its copyright line
reads

> Copyright (C) UN/CEFACT (2016). All Rights Reserved.

The four files are the transitive import closure of `CrossIndustryInvoice_100pD16B.xsd`.

| File | sha256 |
|---|---|
| `xsd/cii-d16b/CrossIndustryInvoice_100pD16B.xsd` | `3baf143b90f289d62c5dfb086aa51fa183490a2d0b38a40c6b31dcae0760f2ba` |
| `xsd/cii-d16b/CrossIndustryInvoice_QualifiedDataType_100pD16B.xsd` | `6649215914728652e883662bec1d3eab008c215e86a59f3bb417db9723ee8c5c` |
| `xsd/cii-d16b/CrossIndustryInvoice_ReusableAggregateBusinessInformationEntity_100pD16B.xsd` | `1c42244298a863bcc0b5196983ff0a5d084fcabe58aea77ef82b242e7cd0ddfd` |
| `xsd/cii-d16b/CrossIndustryInvoice_UnqualifiedDataType_100pD16B.xsd` | `04fce6f6f28521886731294be18a4e6bb5cb3003456457d99ad5c4d3e652f497` |
| `xsd/cii-d16b/NOTICE` | `78e16375c69f86b807b3db5bfbac8a1be61c22951d61224d9faa3a9b2c62f2c2` |

### Levels: what the profiles say about rules of those artefacts (`levels` of `pack.json`)

| Item | Value |
|---|---|
| Publisher | Koordinierungsstelle für IT-Standards (KoSIT) / XStandards Einkauf |
| Distributed in | <https://github.com/itplr-kosit/validator-configuration-xrechnung>, release `v2026-08-31` |
| Taken from | `scenarios.xml` of that archive, whose SHA-256 is in the table of archives above |
| Licence | Apache License, Version 2.0, as the packaging of that release |
| Form here | transcribed into the `levels` member of `pack.json`; the file itself is not vendored |

A compiled Schematron artefact flags each of its rules, and that flag is what the artefact says
about the rule in general. The specification behind a profile may say something else about a
rule for documents of its own profile, and it is the body entitled to say it: it decides what
makes a document of its profile unacceptable. XRechnung states these levels in the
`customLevel` elements of the scenarios of its validator configuration, and the official
validator applies them when it decides whether to accept a document, while reporting the
artefact's own flag beside each finding. The syntax engine does the same, which is why a
finding here carries both levels.

This is the one place a fact is taken out of `scenarios.xml` rather than the file being
vendored. The rest of that file configures the KoSIT validator engine — which artefact runs for
which document, and in which order — and this project decides that for itself from the syntax
and the customization identifier. The levels are not engine configuration; they are what the
specification says about its own rules, and a report that ignored them would describe rules
nobody published in that form.

56 entries in eight tables, transcribed verbatim, with the level names of the
configuration mapped to those of a report: `error` is `fatal`, `warning` is `warning`,
`information` is `information`.

| Table | Syntax | Profile | Levels |
|---|---|---|---|
| `xrechnung-ubl-invoice` | UBL Invoice | XRechnung 3.0 | `BR-CL-21` warning, `BR-CL-23` warning, `UBL-CR-646` fatal |
| `xrechnung-extension-ubl-invoice` | UBL Invoice | XRechnung 3.0 Extension | `BR-CL-10` information, `BR-CL-11` information, `BR-CL-21` information, `BR-CL-23` warning, `BR-CL-24` information, `BR-CL-25` information, `BR-CL-26` information, `BR-CO-16` information, `UBL-CR-470` information, `UBL-CR-646` information |
| `xrechnung-cvd-ubl-invoice` | UBL Invoice | XRechnung 3.0 CVD | `BR-CL-13` information, `BR-CL-21` warning, `BR-CL-23` warning, `UBL-CR-646` fatal |
| `xrechnung-ubl-creditnote` | UBL CreditNote | XRechnung 3.0 | `BR-CL-21` warning, `BR-CL-23` warning |
| `xrechnung-cvd-ubl-creditnote` | UBL CreditNote | XRechnung 3.0 CVD | `BR-CL-13` information, `BR-CL-21` warning, `BR-CL-23` warning, `UBL-CR-646` fatal |
| `xrechnung-cii` | CII | XRechnung 3.0 | `BR-CL-21` warning, `BR-CL-23` warning, `CII-SR-452` fatal, `CII-SR-453` fatal, `CII-SR-454` fatal, `CII-SR-465` fatal, `CII-SR-466` fatal, `CII-SR-475` information, `CII-SR-476` information |
| `xrechnung-extension-cii` | CII | XRechnung 3.0 Extension | `BR-CL-10` information, `BR-CL-11` information, `BR-CL-21` information, `BR-CL-23` warning, `BR-CL-24` information, `BR-CL-25` information, `BR-CL-26` information, `CII-SR-452` fatal, `CII-SR-453` fatal, `CII-SR-454` fatal, `CII-SR-465` fatal, `CII-SR-466` fatal, `CII-SR-475` information, `CII-SR-476` information |
| `xrechnung-cvd-cii` | CII | XRechnung 3.0 CVD | `BR-CL-13` information, `BR-CL-21` warning, `BR-CL-23` warning, `CII-SR-452` fatal, `CII-SR-453` fatal, `CII-SR-454` fatal, `CII-SR-465` fatal, `CII-SR-466` fatal, `CII-SR-475` information, `CII-SR-476` information |

The configuration has no scenario for a UBL CreditNote of the extension profile, so this pack
has no table for that combination either, and the flags of the artefacts stand for it.

`conformance/syntax/ledger.md` records what these tables come to over the conformance corpus
and the mutation set, and what the verdicts would be without them.

### What was left out

The XRechnung validator configuration archive holds 113 files, 5,696,701 bytes unpacked. Taken
from it are the two XRechnung stylesheets and the nineteen schema modules above. Not taken as
files: `scenarios.xml` and the scenario schema, which configure the KoSIT validator engine —
this project selects the artefacts itself from the syntax and the customization identifier of
the document, and takes from `scenarios.xml` only the levels recorded above; the report stylesheets and the report schema, which produce that engine's report
format; the archive's own copy of the compiled CEN stylesheets, for the reason given above; the
`CHANGELOG.md`, `README.md` and `docs/`; and the UBL and CII schema modules no invoice document
imports. The KoSIT validator engine itself is not part of the archive and is not used here.

Not taken from the CEN release archives: the Schematron sources under `schematron/`, including
the preprocessed and abstract rule files, and the example instances under `examples/`.

## Why the artefacts are vendored rather than fetched

A build resolves nothing over the network for these files, and neither does a run. `git clone`
followed by `mvn verify` works offline, works behind a proxy that does not know
`github.com`, and works in five years when a release asset has moved. A digest in git is also
the only form of "this is the artefact that ran" that survives: a report names a pack and a
release, and `SOURCES.md` says exactly which bytes that was.

The obligations are the same either way. The self-contained jar of `esj-cli` distributes these
files whether the build downloaded them or found them here, so vendoring adds no licence
condition that redistribution did not already impose — it only makes the conditions visible in
the tree. The licences permit it: EUPL-1.2 and Apache-2.0 for the two Schematron sets, and the
notices of OASIS and UN/CEFACT for the schemas, each of which asks that the notice travel with
the copy, which is why the notices are beside the files and in the `NOTICE` of this repository.

Running a stylesheet is not deriving from it. The Java in this repository executes these files
as data; no rule, expression or message from them is translated into code. That is the line the
project holds, and it is why the EUPL of the CEN artefacts and the Apache-2.0 of this project
sit side by side without either reaching into the other.
