# Code list snapshots of the pack `en16931/1.3.17`

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
rules/en16931/1.3.17/codelists/<listId>/<YYYY-MM-DD>.json
```

A snapshot file:

```json
{
  "listId": "iso-4217",
  "name": "ISO 4217 alpha-3 currency codes",
  "publisher": "…",
  "source": "…",
  "retrieved": "2026-10-18",
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

Every snapshot in this directory was taken on **2026-10-18**. Eight files were downloaded,
and every list below was extracted mechanically from one of them; nothing was typed by hand
and nothing was taken from an implementation. The extraction is the one of the snapshots of
`en16931/1.3.16` — the same sheets, the same columns, the same treatment of codes marked
deprecated, whitespace inside a name written as one space — and run over the sources of that
pack it gives its eighteen snapshots byte for byte.

| File | Publisher | URL | SHA-256 of the bytes downloaded |
|---|---|---|---|
| `EN16931 code lists values v18 - used from 2026-11-15.xlsx` | European Commission | `https://ec.europa.eu/digital-building-blocks/sites/download/attachments/467108974/EN16931%20code%20lists%20values%20v18%20-%20used%20from%202026-11-15.xlsx` | `40d6c139a895ca868a57b9b543db768d532fb88b28c48c7f8908f8642d9dba33` |
| `Electronic Address Scheme Code list - version 17 - published Sep2026.xlsx` | European Commission | `https://ec.europa.eu/digital-building-blocks/sites/download/attachments/467108974/Electronic%20Address%20Scheme%20Code%20list%20-%20version%2017%20-%20published%20Sep2026.xlsx` | `c12f7c2415035b4decb8dfdf68fd79e3eed2e30dbbeb290f43429765f515f3f3` |
| `VAT Exemption Reason Code list VATEX - version 9.xlsx` | European Commission | `https://ec.europa.eu/digital-building-blocks/sites/download/attachments/467108974/VAT%20Exemption%20Reason%20Code%20list%20VATEX%20-%20version%209.xlsx` | `aac5f3c747429566c160622b35ce2f8693a9c6019c522c9937f2a57cf8f32906` |
| `list-one.xml` (ISO 4217) | SIX Group, ISO 4217 maintenance agency | `https://www.six-group.com/dam/download/financial-information/data-center/iso-currrency/lists/list-one.xml` | `33139b438657d1cee116ba737807ea71d19d6de4b90f799a09c56f0cc6a1b0ff` |
| `iso639-1.json` | Library of Congress, ISO 639-2 registration authority | `https://id.loc.gov/vocabulary/iso639-1.json` | `384c3c47bf785011b56d601e8794081797955de8848bbfb3983dd4e6ee856e47` |
| `application.csv` (media types) | IANA | `https://www.iana.org/assignments/media-types/application.csv` | `15e2989e3948846d0897f564c5ec997bfd375ee75c0e10023d11ebed234e5012` |
| `image.csv` (media types) | IANA | `https://www.iana.org/assignments/media-types/image.csv` | `e4e74a3c35d37cbaf4d37fa7538e8d8c1ef83ec551a3ab5e61aba31ab238eb9f` |
| `text.csv` (media types) | IANA | `https://www.iana.org/assignments/media-types/text.csv` | `8873804114de76118bfdecc79afb6546e1a1d9e2a9f936b0d7b260f3a1e08ba6` |

The workbook names its publication date, 2026-10-09, and the date from which implementations
of EN 16931 are to support its codes, 2026-11-15. `list-one.xml` and `iso639-1.json` are
byte for byte the files of 2026-09-19; the three IANA files are not, because the registry
grew, and each of the six media types below is in it.

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
different in both directions, not merely wider. Measured on 2026-10-18 against the currency
list `BR-CL-03/04/05` carry in the 1.3.17 artefacts (178 codes) and against the snapshot here
(178 codes):

- the artefacts accept `CNH`, which the snapshot does not carry;
- the snapshot accepts `XAD`, which the artefacts do not carry.

So an invoice in `CNH` passes the artefacts and is reported fatal by this pack, and an invoice
in `XAD` does the reverse. Neither direction is the safe one, and the divergence is a real one
of two codes rather than a theoretical lag: it is recorded here, in the note on `BR-CL-04`, and
in `conformance/rules/ledger.md`. Release 1.3.16 diverged by four: it also accepted `STD` and
not `STN`, and 1.3.17 replaced the one by the other. The list of 1.3.17 is, code for code, the
currency sheet of the workbook version 18, which is not where this snapshot comes from.

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
| `iso-6523-icd` | ISO/IEC 6523 international code designators | Commission workbook, sheet `ICD` | 246 | taken |
| `iso-4217` | Currency codes, alpha-3 | SIX Group `list-one.xml` | 178 | taken; diverges from the artefacts by two codes |
| `iso-3166-1` | Country codes, alpha-2 | Commission workbook, sheet `Country` | 251 | taken as it stands; the Commission's sheet carries `1A` and `XI` beyond ISO 3166-1 |
| `iso-639` | Language codes, alpha-2 | Library of Congress `iso639-1.json` | 183 | taken; no rule asks about it yet |
| `eas` | Electronic address scheme identifiers | Commission EAS code list, version 17 | 103 | taken, codes not marked deprecated, without `XA` |
| `vatex` | VAT exemption reason codes | Commission VATEX code list, version 9 | 106 | taken, codes not marked deprecated |
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

### `XA`, the one code a source carries and this directory does not

Version 17 of the EAS code list adds two codes that are not marked deprecated: `9960`, the
Taiwan Unified Business Number, and `XA`, named *Defined by users*, with the source `NA`.
`9960` is in the snapshot. `XA` is not, for three reasons that point the same way: the sheet
`EAS` of the Commission's workbook v18 — the listing of what EN 16931 admits from 2026-11-15 —
carries `9960` and not `XA`; `BR-CL-25` of the 1.3.17 artefacts carries `9960` and not `XA`;
and a scheme that any user defines identifies no address by itself, which is what the scheme of
an electronic address (BT-34, BT-49) is for. An invoice whose electronic address names `XA` is
therefore faulted by this pack and by the artefacts alike. Should a later workbook admit it, a
later pack takes it.

## What changed against the snapshots of `en16931/1.3.16`

| `listId` | Change |
|---|---|
| `iso-6523-icd` | `0249`, `0250`, `0251` added |
| `eas` | `9960` added |
| `vatex` | eighteen codes `VATEX-ES-*` added: `VATEX-ES-N1`, `-N2`, `-IVA20`, `-IVA21`, `-IVA22`, `-IVA23/24`, `-IVA25`, `-IVA140BIS`, `-OTROS`, `-IGIC1TR`, `-IGIC11`, `-IGIC12`, `-IGIC13`, `-IGIC25`, `-IGIC47`, `-IGIC90`, `-IPSI7`, `-IPSI8` |
| the other fifteen | the same codes with the same names in the same order; only `retrieved` and, for the workbook lists, the file named in `source` differ |

Every code of the three that grew is in the corresponding list of the 1.3.17 artefacts:
`BR-CL-10`, `BR-CL-11`, `BR-CL-21` and `BR-CL-26` carry the 246 ICD codes, `BR-CL-25` the 103
EAS codes and `BR-CL-22` the 106 VATEX codes, measured on 2026-10-18.
