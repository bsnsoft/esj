# Code list snapshots of the pack `en16931/1.3.16`

*Part of [EN16931 Semantic JSON](../../../../README.md). See [`../../../README.md`](../../../README.md)
for the rule language and [`docs/validation.md`](../../../../docs/validation.md) for what a
finding of this engine means.*

A rule that asks whether a code is on a list is asking about a list that has a date. A
currency is withdrawn, a country is added, a reason code is retired; if the engine read the
list of the day it runs, the same invoice would be valid in one year and invalid in the next
without anyone having changed anything, and a report from the archive could not be
reproduced. So every list is a **snapshot**: one file, named by the day it was taken, frozen
for as long as this pack version exists. A newer list is a newer pack, never an edit here.

Two rules decide where a snapshot may come from.

**From the publisher, not from another implementation.** A copy of a list inside somebody
else's validator is a copy of a copy: it has a licence of its own, an error history of its
own and a release cycle of its own. In particular the code list files distributed with the
CEN validation artefacts are EUPL-1.2 material and are never a source for a file in this
directory, and no private material is either.

**With its provenance written down.** Each row below names the publisher, the page the
snapshot was taken from, the day it was taken and what the publisher says about reuse. Where
what the publisher says is unclear, the snapshot is taken anyway and the uncertainty is
recorded in its row, so that the decision is visible rather than implied.

## Layout

```text
rules/en16931/1.3.16/codelists/<listId>/<YYYY-MM-DD>.json
```

A snapshot file:

```json
{
  "listId": "iso-4217",
  "name": "ISO 4217 alpha-3 currency codes",
  "publisher": "…",
  "source": "…",
  "retrieved": "2026-09-19",
  "terms": "what the publisher says about reuse",
  "entries": [ { "value": "EUR", "name": "Euro" } ]
}
```

`name` on an entry is the description the publisher gives the code, and is left out where the
publisher gives none. The pack manifest names which day of which list this pack decides
against; a list with no snapshot named there cannot be asked about, and a rule that names one
anyway does not compile — a membership test against a list nobody loaded would pass every
code, and a rule that silently passes everything is worse than a pack that will not start.

## Where these snapshots came from

Every snapshot in this directory was taken on **2026-09-19**. Eight files were downloaded,
and every list below was extracted mechanically from one of them; nothing was typed by hand
and nothing was taken from an implementation.

| File | Publisher | URL | SHA-256 of the bytes downloaded |
|---|---|---|---|
| `EN16931 code lists values v17b - used from 2026-05-15.xlsx` | European Commission | `https://ec.europa.eu/digital-building-blocks/sites/download/attachments/467108974/EN16931%20code%20lists%20values%20v17b%20-%20used%20from%202026-05-15.xlsx` | `a56f85ff5b30287cabdbb68dc2690be6bc0c628041e29bdcb4cd5eac1f6f5296` |
| `Electronic Address Scheme Code list - version 16 - published Mar2026.xlsx` | European Commission | `https://ec.europa.eu/digital-building-blocks/sites/download/attachments/467108974/Electronic%20Address%20Scheme%20Code%20list%20-%20version%2016%20-%20published%20Mar2026.xlsx` | `4f67d28433a675a5eaaa2202429ad4b4640fc785d66dc494f33fd3181b661cb3` |
| `VAT Exemption Reason Code list VATEX - version 8.xlsx` | European Commission | `https://ec.europa.eu/digital-building-blocks/sites/download/attachments/467108974/VAT%20Exemption%20Reason%20Code%20list%20VATEX%20-%20version%208.xlsx` | `40c1cc807edff744bdb6b5adaf56046ad46b04df1a766dc1abdf81f869efa5f2` |
| `list-one.xml` (ISO 4217) | SIX Group, ISO 4217 maintenance agency | `https://www.six-group.com/dam/download/financial-information/data-center/iso-currrency/lists/list-one.xml` | `33139b438657d1cee116ba737807ea71d19d6de4b90f799a09c56f0cc6a1b0ff` |
| `iso639-1.json` | Library of Congress, ISO 639-2 registration authority | `https://id.loc.gov/vocabulary/iso639-1.json` | `384c3c47bf785011b56d601e8794081797955de8848bbfb3983dd4e6ee856e47` |
| `application.csv` (media types) | IANA | `https://www.iana.org/assignments/media-types/application.csv` | `195d9d1a759e234a0321ef38a5b44a8acdaecb5ea84cd8321b2ef7bcb723b310` |
| `image.csv` (media types) | IANA | `https://www.iana.org/assignments/media-types/image.csv` | `946acb46321e250c658d1e135363496d7940ee83f23df9d2baf3760f9197c8bc` |
| `text.csv` (media types) | IANA | `https://www.iana.org/assignments/media-types/text.csv` | `8b81eb8746d62d742808221f093f3ea32c3d059f6352dd60b17526464218e8cf` |

All eight are spreadsheets, XML, JSON or CSV; the codes were read out of the cells, the
elements, the members and the rows, so each snapshot is a mechanical extraction and not a
transcription.

### The one thing a reader has to know about these sources

Thirteen of the eighteen lists below did not come from the body that originates them. **The sites
of UNECE and of the Library of Congress' `www.loc.gov` refuse automated retrieval** — a
request to `service.unece.org` (the UNTDID data element pages), to `unece.org` (the
Recommendation 20 and 21 spreadsheets) and to `www.loc.gov` answers with a bot check rather
than with the file, and this project does not work around a bot check. **ISO publishes no
free machine-readable form of the ISO/IEC 6523 ICD list at all.** What was taken instead
is the European Commission's own publication for the standard this pack is about: the workbook
*EN 16931 code lists values*, which states which codes of each list the European eInvoicing
standard admits, published on the Commission's registry of supporting artefacts for EN 16931.
It is not the CEN validation artefacts and carries none of their files; it is a separate
publication, by a different body, of the code list values rather than of the rules.

Two consequences, both of which belong in the ledger rather than in a footnote.

The Commission's workbook is in places a **restriction** of the list it names, which is what
the rules of the standard ask about: `untdid-2005` carries the three codes the standard
admits and not the hundreds UNTDID has, and `untdid-5305` carries the nine VAT category
codes. Where the rule is written as a membership test in a restricted list, this is the right
list to ask; where a rule wanted the full list, the note on the rule says so.

`iso-4217` and `iso-639`, by contrast, come from the maintenance agency and the registration
authority themselves, and are therefore a **different** list from the one the artefacts carry —
different in both directions, not merely wider. Measured on 2026-09-20 against the currency
list `BR-CL-03/04/05` carry in the 1.3.16 artefacts (178 codes) and against the snapshot here
(178 codes):

- the artefacts accept `CNH` and `STD`, which the snapshot does not carry;
- the snapshot accepts `STN` and `XAD`, which the artefacts do not carry.

So an invoice in `CNH` or `STD` passes the artefacts and is reported fatal by this pack, and an
invoice in `STN` or `XAD` does the reverse. Neither direction is the safe one, and the
divergence is a real one of four codes rather than a theoretical lag: it is recorded here, in
the note on `BR-CL-04`, and in `conformance/rules/ledger.md`.

### Licences and terms, and what is uncertain

**None of the four publishers states a licence for the data on the page it is offered from**,
across the eight files listed above. That is the uncertainty, it applies to all of them, and it
is recorded here for the author to decide rather than resolved by this stage:

- the Commission's pages offer the workbooks for implementers of EN 16931 and state no terms;
  reuse of Commission documents is generally governed by Commission Decision 2011/833/EU, but
  the pages do not cite it, so this is inference and not a statement;
- SIX Group publishes the ISO 4217 tables as the maintenance agency and states no terms on
  the download;
- the Library of Congress publishes the ISO 639 vocabularies at `id.loc.gov` as the
  registration authority and states no terms for the JSON representation;
- IANA publishes the media type registry as the registry operator and states no terms for the
  CSV representations the three media type files were read from.

What each snapshot carries is a list of codes and the short names their publisher gives them.
No prose of any publisher is reproduced, and the `terms` member of every snapshot file says
in one sentence what was found on the page it came from.

## The lists

| `listId` | List | Source file | Codes | Status |
|---|---|---|---|---|
| `unece-rec20` | UN/ECE Recommendation 20, units of measure | Commission workbook, sheet `Unit`, rows sourced `rec20` | 1756 | taken |
| `unece-rec21` | UN/ECE Recommendation 21, types of cargo and packaging | Commission workbook, sheet `Unit`, rows sourced `rec21` | 406 | taken |
| `untdid-1001` | Document name code | Commission workbook, sheet `1001` | 62 | taken |
| `untdid-2005` | Date or time or period function code qualifier | Commission workbook, sheet `Time` | 3 | taken, a restriction |
| `untdid-4451` | Text subject code qualifier | Commission workbook, sheet `Text` | 401 | taken |
| `untdid-4461` | Payment means code | Commission workbook, sheet `Payment` | 84 | taken |
| `untdid-5305` | Duty or tax or fee category code | Commission workbook, sheet `5305` | 9 | taken, a restriction |
| `untdid-5189` | Allowance or charge identification code | Commission workbook, sheet `Allowance` | 19 | taken, a restriction |
| `untdid-7143` | Item number type code | Commission workbook, sheet `Item` | 185 | taken |
| `untdid-7161` | Special services code | Commission workbook, sheet `Charge` | 178 | taken |
| `untdid-1153` | Reference code qualifier | Commission workbook, sheet `1153` | 818 | taken |
| `iso-6523-icd` | ISO/IEC 6523 international code designators | Commission workbook, sheet `ICD` | 243 | taken |
| `iso-4217` | Currency codes, alpha-3 | SIX Group `list-one.xml` | 178 | taken; diverges from the artefacts by four codes |
| `iso-3166-1` | Country codes, alpha-2 | Commission workbook, sheet `Country` | 251 | taken as it stands; the Commission's sheet carries `1A` and `XI` beyond ISO 3166-1 |
| `iso-639` | Language codes, alpha-2 | Library of Congress `iso639-1.json` | 183 | taken; no rule asks about it yet |
| `eas` | Electronic address scheme identifiers | Commission EAS code list, version 16 | 102 | taken, codes not marked deprecated |
| `vatex` | VAT exemption reason codes | Commission VATEX code list, version 8 | 88 | taken, codes not marked deprecated |
| `mime-code` | Media types of an attached document | IANA media types registry | 6 | taken, the restriction the norm states |

One of the eighteen is carried without a rule that asks about it, and is therefore not named in
the pack manifest either: `iso-639` is not the code list of any business term of the 2017
edition. It was taken because the domain API of a later phase needs the enumeration, and because
a list taken now is a list whose provenance was recorded while the page it came from still said
what it says. The other seventeen are named in the manifest and are what the rules decide
against.

### The two lists that are read differently from the others

`iso-6523-icd` and `mime-code` are the two snapshots that deserve a sentence of their own.

The **ICD list** is the one this directory said for a while it could not have: ISO publishes
no free machine-readable form of it, and the only copies in easy reach are the code list files
of the CEN validation artefacts, which this directory does not take from. The European
Commission's workbook has a sheet of it, and that sheet is the source here — the same
publication nine other snapshots come from, taken the same way. Four rules decide against it:
`BR-CL-10`, `BR-CL-11`, `BR-CL-21` and `BR-CL-26`.

The **media types** are the other way round. The registry is IANA's and is published openly,
but the list a rule may ask about is not the registry: EN 16931-1 admits exactly six media
types for an attachment (clause 6.5.11, and the usage note of BT-125), and an invoice that
carries a seventh is one the standard does not admit however well registered that type is. So
the snapshot is the six, and what the IANA download is for is that each of the six is looked
up in the registry it belongs to and carried only if it is there. A snapshot taken when one of
them had been deregistered would not be written at all.
