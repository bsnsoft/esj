# Validation

*Part of [EN16931 Semantic JSON](../README.md).*

`esj validate` runs two engines over one input and gives you one verdict. Below: what each engine
runs, what a finding means, what a verdict and an exit code promise, and what has been measured.
[`cli.md`](cli.md) is the reference for every option.

## Two engines

```text
                 esj validate
                      │
       ┌──────────────┴──────────────┐
  syntax engine                 semantic engine
  (XML, or ESJ written to XML)  (ESJ, or the document imported from XML)
  well-formed, bounded parse    L1  the bytes are a document
  XML Schema of the syntax      L2  every path and value is one the registry knows
  EN 16931 Schematron           L3  the cardinalities hold
  CIUS Schematron               EN 16931 business rules, over the business terms
       └──────────────┬──────────────┘
                  one verdict
```

The **syntax engine** takes the bytes exactly as they arrived. It runs the official artefacts of the
profile BT-24 names, in the release the pack names. The **semantic engine** runs the structural
layers of `SPEC.md` section 9 over the semantic document: L1 for an ESJ input, L2 and L3 for both.
Then it runs the business rules of EN 16931-1, clause 6.4, over the business terms. It knows no XML.

Both engines run over an XML input, because **semantic validity does not prove syntax-binding
validity**. An import is a projection, so the semantic document cannot show an element written where
the binding forbids it. Nor can it show an identifier without the scheme attribute the binding
requires, or a code in an attribute the importer never reads. An ESJ document that never was XML is
written out through a binding table in memory, so the artefacts read it too.

## The packs

A **validation pack** is a directory of the official artefacts for one profile. Each file is
unmodified, executable by a machine, under its own licence.
[`../packs/README.md`](../packs/README.md) describes the format.
[`../packs/SOURCES.md`](../packs/SOURCES.md) records for every file where it was published, its
licence, its SHA-256 and the date.

This build carries two releases of one pack, `xrechnung/3.0.2/2026-10-12` and
`xrechnung/3.0.2/2026-08-31`, each of four components:

| Component | What it is | Licence |
|---|---|---|
| `ubl-2.1-xsd` | the OASIS UBL 2.1 schema modules the Invoice and CreditNote documents import | OASIS UBL 2.1 notice |
| `cii-d16b-xsd` | the UN/CEFACT CII D16B schema modules CrossIndustryInvoice imports | UN/CEFACT notice |
| `en16931-ubl-schematron`, `en16931-cii-schematron` | the EN 16931 Schematron of CEN/TC 434, compiled to XSLT: release 1.3.17 in the pack of 2026-10-12, release 1.3.16 in the pack of 2026-08-31 | EUPL-1.2 |
| `xrechnung-ubl-schematron`, `xrechnung-cii-schematron` | the XRechnung Schematron 2.6.0, compiled to XSLT | Apache-2.0 |

Each release also carries eight level tables with 56 rules in all. They say what a rule of those
artefacts means for a document of the XRechnung profiles (**Two publishers, two levels** below).
`esj --list-packs` prints all of it for a build.

ESJ judges a document by the newer release. CEN/TC 434 published release 1.3.17 of its validation
artefacts on 2026-10-12, in effect from the end of 2026-11-15. No release of the XRechnung validator
configuration carries it yet. The pack `xrechnung/3.0.2/2026-10-12` is therefore release 1.3.17
together with the XRechnung Schematron, the schema modules and the level tables of the configuration
of 2026-08-31. `--pack xrechnung/3.0.2/2026-08-31` judges by the previous release, CEN 1.3.16.

**A release is identity.** Profile, profile version and the release date of the artefact bundle name
a pack, and every report shows all three. A bugfix release adds, removes or re-levels rules, so a
new release is a new directory beside the old one.

**Nothing is fetched at run time.** The packs are in the jar, so a build works offline. Every build
recomputes the digests in `SOURCES.md`, so they are those of the artefacts that judged. The one
command that goes to the network is `esj packs fetch` (below), and only when you call it.

**You can replace a pack, and every form says when you did.** `--pack <directory>` runs a pack from
a directory holding a `pack.json`. `--pack <id>` selects a bundled pack or one of a pack directory.
A manifest writes its own identity, so ESJ prints the origin beside the name. `--output json`
carries `syntax.pack.source` (`bundled`, `supplied` or `directory`) and `syntax.pack.location`, the
directory ESJ read the files from. A new release needs a directory, a manifest, the provenance rows
and a line in the index, and no code: [`../packs/README.md`](../packs/README.md#adding-a-release).

### Packs made on this machine

Some artefacts may be used but not redistributed. The Peppol BIS Billing 3.0 validation artefacts,
as OpenPeppol publishes them, carry no open-source licence. OpenPeppol permits no redistribution
without its consent, so no file of them is in this repository or in any artefact it publishes. esj
carries its own work instead: a recipe naming the files of one release with their SHA-256, and the
command that follows it.

```text
$ esj packs fetch peppol-bis-billing --into ~/esj-packs
$ esj validate --packs ~/esj-packs invoice.xml
```

This build carries two recipes: `peppol-bis-billing-3.0.21`, the May 2026 release that is mandatory
from 2026-08-17, and `peppol-bis-billing-3.0.20`. The pack identifier `peppol-bis-billing` names the
newest release this build carries; a recipe's own name names its release. OpenPeppol tagged 3.0.20
(`v3.0.20`) but not 3.0.21. The 3.0.21 recipe therefore fetches below the commit
`806866bd2bd91d7e9623b68f08164e8fbe9e67a0` of the branch `2026-Q2-QA2`, never below a branch name,
which moves. When the recipe was written, its two UBL files were byte for byte the ones OpenPeppol
publishes under `docs.peppol.eu/poacc/billing/3.0/files/`. The CII files are only in the repository.

`esj packs fetch` downloads the four Schematron files of the release from the OpenPeppol repository
over https. It refuses any file whose SHA-256 is not the one the recipe pins, and then writes
nothing. It compiles each file to XSLT in process, with the ISO Schematron skeleton this build
carries, and copies the UBL and CII schema modules out of the bundled pack. It writes the pack to
`<directory>/peppol-bis-billing/3.0/<release>`, with a `pack.json` that records the source URLs, the
fetch date and the SHA-256 of every file it wrote. A second fetch into the same directory leaves an
identical pack alone and refuses a different one unless you pass `--replace`.

A **pack directory** is a directory of packs laid out as `<id>/<version>/<release>/pack.json`. Name
pack directories with `--packs <directory>` on `validate` and `inspect` (repeatable) or with the
environment variable `ESJ_PACKS`, a path list. Every pack found there joins the bundled ones. ESJ
chooses among them the same way, by the profile the document names in BT-24. Two rules keep the
choice unambiguous. ESJ refuses by name a pack there with the identity of a bundled pack. It refuses
a document whose profile two packs recognize, one of them from a pack directory, and names both;
choose with `--pack <id>`. The one exception is the order a publisher gives. Sometimes the packs
that recognize the profile are all releases of one pack, with the same id and version, as
`peppol-bis-billing/3.0/3.0.20` and `peppol-bis-billing/3.0/3.0.21` fetched side by side are. Then
ESJ takes the newest release, compared part by part (3.0.9 before 3.0.10), and the report names it.
`--pack peppol-bis-billing/3.0/3.0.20` runs the older one. A document of another profile is judged
as without the directory. `esj --list-packs` and `esj packs list` print the origin of every pack,
`bundled` or the directory. For a rule set compiled on this machine they also print the file it came
from and the SHA-256 of what came out. They list the recipes this build carries after the packs. A
container reads a pack directory from a mount ([`install.md`](install.md#container-image)).

The pack carries the rule sets the release ships. First, the EN 16931 Schematron of CEN/TC 434 in
the version OpenPeppol took into it, as the files state: 1.3.16 of 2026-04-10 for 3.0.21, and 1.3.15
of 2025-10-16 for 3.0.20. Then the Peppol rules for UBL and for CII, each at the level its artefact
flags. The build configuration of the release runs the schema, the CEN rules and the Peppol rules of
a syntax together and levels no rule, so the pack has no level table. The Peppol rules run for
`urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0` and every identifier
that begins with it, for Invoice and CreditNote alike. No document of another profile gets this
pack.

## The check table

The rows of the syntax block are the components of the chosen pack, in manifest order. The rows of
the semantic block are the layers. Every row reads `OK`, a count of findings, a reason it did not
run, or `no verdict`. The last means the row ran and could not measure what it was asked to: a
limit, an unknown edition, a path only an extension registry defines. A pack carries the artefacts
of every syntax it covers, so a UBL invoice leaves the CII rows unused. `--verbose` prints them; the
JSON report always has them.

| Row | What it ran |
|---|---|
| `XML` | the front door: well-formedness, the declared encoding, and the bounds a parser is given before it reads a stranger's document |
| `UBL 2.1 XSD` / `CII D16B XSD` | the schema modules of the document's syntax |
| `EN 16931 UBL/CII Schematron` | the CEN/TC 434 rules for that syntax |
| `XRechnung UBL/CII Schematron` | the rules of the core invoice usage specification the document names |
| `ESJ format (L1)` | the bytes are an ESJ document (`SPEC.md` 9.1) |
| `Model (L2)` | every path is a term the registry knows and every value fits its data type (9.2) |
| `Cardinality (L3)` | the cardinalities of the registry hold (9.3) |
| `EN 16931 business rules (native, pack …)` | the business rules of clause 6.4 as rules of this project, over the business terms; the row names the pack and either what it found or the reason it did not run |
| `official artefacts over the written CII` | for an ESJ input: the schema and Schematron rows above, over the XML the document was written to |

A program reads `syntax.ran`, `syntax.skipped`, `layers` and `rules` in the JSON report instead. The
components appear there under the pack's own names (`en16931-ubl-schematron`). `notChecked` is a
list of stable tokens: `format-l1`, `model-l2`, `cardinality-l3`, `business-rules`, `profile-rules`,
`syntax-binding`, `written-syntax`. It names everything this run did not look at or could not
measure, including what there was nothing to look at: L1 over an XML input is in it and is no gap.
The `reasons` array beside it carries the missing components of the complete check, each with its
cause. The verdict turns on that list, and every component of `reasons` is also in `notChecked`.

The business-rules row is the engine of [`rules/`](../rules/README.md) and of the module
`esj-rules`. One rule serves a UBL invoice, a CII invoice and a document that was never XML alike.
Its findings are a layer of their own (category `EN-BR`, `EN-DEC` or `EN-CL`, engine `native`), and
ESJ never presents them as ESJ conformance (`SPEC.md` 9.4).

Both engines check those rules, so one identifier can appear in both blocks. ESJ neither merges one
report into the other nor suppresses either. The artefact reads the document as it arrived, the
native pack what the import carried into the model. The lines and the report file name the overlap
under the native findings. `--output json` keeps `syntax.findings` and `rules.findings` apart, the
engine on every finding.

## The rule pack

[`../packs/`](../packs/README.md) holds other people's artefacts, [`../rules/`](../rules/README.md)
this project's own. A **rule pack** is a directory of rules written over business terms. It holds
the code list snapshots its membership tests are decided against and a manifest naming both. There
is one per edition (`esj --list-packs`). The pack of the default edition:

| `en16931/1.3.17` | |
|---|---|
| What it states | the business rules of EN 16931-1, clause 6.4, together with the decimal restrictions of clause 6.5.12, Table 26, and the code list restrictions of clause 6.3, which the official artefacts number `BR-DEC-*` and `BR-CL-*` |
| Rules | 217 — 189 written in the rule language, 28 in Java, indistinguishable in a report |
| Code lists | 17 dated snapshots of 2026-10-18 — the code list workbook version 18, the EAS list version 17 and the VATEX list version 9 among their sources — each with its publisher, its retrieval date and what that publisher says about reuse in [`../rules/en16931/1.3.17/codelists/SOURCES.md`](../rules/en16931/1.3.17/codelists/SOURCES.md) |
| Verified against | the CEN/TC 434 validation artefacts, release 1.3.17 |

`1.3.17` is the release of the artefacts the pack was *measured against*. The pack `en16931/1.3.16`
stays in the tree beside it, with the identifiers `BR-AF-*` and `BR-AG-*` that release used for the
categories `L` and `M`. `esj validate` runs the newer one. The pack `en16931-2026/0.1`, a preview
like its edition, has no release behind it
([`editions.md`](editions.md#what-works-for-a-2026-document-today)).

### The rule language, in short

A rule is a JSON object with seven parts: the identifier the standard gives it, a severity, the
business group it is a statement about, the terms it reads, an assertion built from a closed set of
34 operators, a message, and the clause of the standard in `source`.
[`../rules/README.md`](../rules/README.md) is the whole language. Four properties matter when you
read a report. A rule addresses business terms, never an occurrence index. A rule whose context is a
business group runs once per instance of that group and reports at the instance it failed at.
Arithmetic is exact `BigDecimal`. The operator that counts fraction digits applies only to the
fixed-scale amounts of the standard; a unit price, a quantity and a percentage have no decimal
limit, and no rule rounds or cuts them. A rule the closed operator set cannot express is written in
Java: same identifier, same pack, same finding.

### What it covers, and what it does not

Every identifier of the official validation artefacts of release 1.3.17 is either implemented here
or named with a reason. A test of `esj-rules` reads the table that says which:

| | Rules of the release | Implemented | Not applicable | Deferred |
|---|---|---|---|---|
| Model rules | 201 | 195 | 0 | 6 |
| Code list rules | 22 | 21 | 1 | 0 |
| **Together** | **223** | **216** | **1** | **6** |

The 217 of the pack are those 216 and `BR-CO-25`. The standard states that rule in clause 6.4.2 (an
invoice with an amount due for payment states a payment due date or payment terms), and release
1.3.17 carries no identifier for it. ESJ does not implement 7 rules;
[`../conformance/rules/not-applicable.md`](../conformance/rules/not-applicable.md) gives each
reason. `BR-CL-03` is about an attribute of the XML binding the semantic model has no place for.
`BR-CO-05` to `BR-CO-08` ask whether a reason code and a free-text reason agree; the official
artefacts assert nothing for them either. `BR-B-01` and `BR-B-02` wait for an edition of the
standard. [`../conformance/rules/coverage.md`](../conformance/rules/coverage.md) is the table rule
by rule; [`../conformance/rules/corpus.md`](../conformance/rules/corpus.md) shows what the pack says
about invoices known to be good.

## What the import reports

The layers and the native rule pack can only decide what the import carried, so the import reports
what it did *not* carry. Three rules govern it.

**Nothing is dropped in silence.** Where the model has a place for what arrived, ESJ keeps the
value. An identifier the binding does not select by its scheme reaches the document without one. The
model layer then reports `ESJ-L2-COMPONENT-MISSING` at its path, and `BR-62` to `BR-65` report it
too. It is the one model error that does not stop the rule pack. Where the binding has no place for
an element, ESJ reports it under the next rule. In CII a tax registration under neither the value
added tax scheme nor the fiscal one is neither BT-31 nor BT-32, while UBL binds BT-32 by the
negation and takes it. [`readers.md`](../conformance/readers.md) records both, term by term.

**A syntax element the binding cannot classify is reported, not dropped.** A binding selects the
element that carries a term by a value in or on it: an allowance charge by its indicator, payment
instructions by the payment means type code, a CII date by its format qualifier. Nothing selects an
element that states no such value. Both readers then write one `UNPLACEABLE` note per element,
naming it and the terms that are absent as a result.
[`../conformance/rules/not-applicable.md`](../conformance/rules/not-applicable.md) records which
rules that costs.

**A notation the semantic document cannot carry is reported.** The canonical decimal form writes no
trailing zeros, so `319.860` is `319.86` in the document, and the importer warns at that path.

A warning-level note means content of the source did not reach the document. An information-level
note means ESJ lost nothing EN 16931 has a place for. Every command that imports prints the warnings
on the error stream, collapsed and cut off; `--verbose` adds the information-level notes. In
`--output json` they are the `warnings` and `information` arrays. In the report file
([`cli.md`](cli.md#the-report)) a warning is a finding of the engine `importer`, and an
information-level note stands under *Before the check*.

## Finding categories

One validation produces findings from several rule sets at once. ESJ therefore gives every finding a
category, derived from the identifier the artefact reported:

| Category | What a finding of it is about |
|---|---|
| `XML` | the document as XML: well-formedness, the declared encoding, what the parser refused |
| `UBL-XSD`, `CII-XSD` | the schema of the syntax: an element in the wrong place, a missing one, a value the schema type rejects |
| `EN-BR` | a business rule of EN 16931: what the invoice must contain and what its numbers must come to |
| `EN-DEC` | a decimal rule of EN 16931: how many fraction digits a value may spell |
| `EN-CL` | a code list rule of EN 16931: whether a code is one of the list it must come from |
| `UBL-BINDING`, `CII-BINDING` | how EN 16931 is bound to that syntax: which element carries a term, which one may not appear |
| `XR-BR` | a rule of the XRechnung core invoice usage specification |
| `XR-EXT` | a rule of the XRechnung extension |
| `OTHER` | an identifier of no family this table names; the identifier travels with the finding unchanged |

A build enumerates the identifiers of the vendored artefacts and asks the table about every one. A
family of a later release that is not in the table lands in `OTHER` with its identifier intact.
Beside the category, a finding carries the rule identifier, the artefact's message, the node it
names, the pack and engine it came from, and two levels.

### Two publishers, two levels

The artefact flags each of its rules. The core invoice usage specification a document names may say
something else about that rule for its own profile. XRechnung does both: it levels `BR-CL-13` down
for its CVD profile, whose item classification scheme the EN 16931 code list does not carry, and
`UBL-CR-646` up for its standard profile. A finding therefore reports both levels. `flag` is the
artefact's, untouched. `severity` is the level the profile asks for, and ESJ makes the verdict on
it. Where the two differ, every form says so: `[information, flagged fatal by the artefact]`.

The levels are data of the pack, transcribed from the configuration the specification publishes and
recorded in [`../packs/SOURCES.md`](../packs/SOURCES.md). Nothing in the code levels anything. Over
the 149 documents of the ledger below, 19 carry a finding whose two levels differ. On 7 of those,
the verdict would be the other one without the tables.

## The complete check

**You get `VALID` and exit code 0 only where the complete check for that kind of input ran and found
nothing fatal.** ESJ defines that check per input kind:

| Input | The complete check |
|---|---|
| **XML** (UBL, CII; also the XML taken out of a PDF) | well-formedness and the bounded parse; the XML Schema of its syntax; the EN 16931 Schematron; the CIUS Schematron where the specification identifier of the document names a CIUS the pack carries; and the structural layers L2 and L3 of the imported document |
| **ESJ** | layers L1, L2 and L3, the business rules of EN 16931 as the native pack, and the official artefacts of its profile over the XML the document is written to |
| **PDF** | the container checks of this tool, plus the rows of the embedded XML |

**Layer L1 is in the ESJ row and in no other.** The bytes of an ESJ document decide L1 (`SPEC.md`
section 9.1). A document that arrived as UBL, as CII or inside a PDF never was such a byte sequence,
and `SPEC.md` section 3.5 measures a result over it against L2 and L3 alone. An XML input therefore
reaches `VALID` with `format-l1` among the rows this run did not look at.

**A document that names EN 16931 and no CIUS needs no CIUS rules**, and its check is complete
([When a profile brings no rules with it](#when-a-profile-brings-no-rules-with-it) has the other
case). The `baseProfiles` member of the pack manifest tells the two apart
([`../packs/README.md`](../packs/README.md#the-manifest)).

**The native rules are a required row for an ESJ input and an additional one for XML.** For an XML
input the official artefacts of the profile define `VALID`. The native pack adds evidence, not
coverage, and `--rules none` leaves the check complete. For an ESJ input the pack is a row of its
own. `--rules none` makes it `skipped-by-caller`, with the verdict `INDETERMINATE`, as `--no-syntax`
does for an XML input. A fatal native finding is `INVALID` for either input. For an ESJ input, a
declared CIUS that the native rules do not cover stays a gap until a pack covers it.

**The official artefacts reach an ESJ input through a binding table.** No artefact reads a document
that was never XML. `esj validate` therefore writes it out, as a cross industry invoice or, with
`--via ubl`, as a UBL document, and runs the artefacts of its profile over the result. Nothing goes
to disk, and the document under test stays the ESJ one. The row holds only while the two say the
same thing. Four causes name where they do not: `term-not-in-syntax`, `term-not-stated`,
`element-not-in-model` and `written-over-bound` (table below). The row is then not applicable, and
the verdict is `INDETERMINATE`. ESJ writes an element the binding table states a value for, and the
row runs. `--no-syntax` takes the row out, and a profile with no artefact leaves it
`no-rules-for-profile`.
[`../conformance/writers/cii-roundtrip.md`](../conformance/writers/cii-roundtrip.md) and
[`ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md) measure both writers over the corpus,
which this row rests on.

**PDF/A conformance is outside the verdict** unless [`--verapdf`](cli.md#validating-the-pdfa-claim)
names a validator. Its refusal makes the container `INVALID`. Without it, the row reads what the
file declares ([`pdf-input.md`](pdf-input.md)).

## Verdict and exit code

The verdict is one word over both engines, and there are three of them:

| Verdict | When | Exit |
|---|---|---|
| `VALID` | the complete check for this input ran and nothing fatal was found | 0 |
| `INVALID` | a fatal finding in anything that ran, whatever the coverage | 1 |
| `INDETERMINATE` | nothing fatal was found and a component of the complete check did not run or did not complete | 9 |

**The verdict is technical conformance** with EN 16931 and the official artefacts. A fatal
business-rule finding is `INVALID`, as for the KoSIT validator. For German VAT the
[BMF letter of 15 October 2026](https://www.bundesfinanzministerium.de/Content/DE/Downloads/BMF_Schreiben/Steuerarten/Umsatzsteuer/Umsatzsteuer-Anwendungserlass/2026-10-15-obligatorische-elektronische-rechnung.html)
(Rn. 22 f.) tells format, business-rule and content errors apart. A file with a business-rule error
and no format error stays an e-invoice under § 14 UStG where its mandatory details are complete and
correct. This version does not check those mandatory details ([`legal-de.md`](legal-de.md)).

A run stopped by a **bound of this run** reaches none of the three, and leaves with exit code **7**.
A bound was met and the document was not judged: read it again against a larger bound, do not reject
it. Whether you get a report depends on whether the document had been built by then:

| Where the bound was met | What the run leaves |
|---|---|
| after the document exists: an ESJ input under a reader bound of `SPEC.md` section 12.2 | the whole answer. Last line `NO VERDICT — a limit of this run was reached …`, the rows the bound cut short `no verdict`, `"verdict": null` and the cause `limit-reached` under each component of `reasons` |
| while the document is still being built: every importer path, so every XML and every PDF input, as well as [`--max-runtime`](cli.md#limits) and the container bounds ([`pdf-input.md`](pdf-input.md)) | no answer at all. One line on the error stream, nothing on the standard output — with `--output json` as well |

`INDETERMINATE` always carries why. The last printed line is `INDETERMINATE — nothing fatal found;
missing from the check: …` and names each component with its cause. `--output json` carries the same
as a `reasons` array of `{component, cause}`, from a closed vocabulary:

| `cause` | What happened |
|---|---|
| `skipped-by-caller` | `--no-syntax`, or `--level l2` |
| `limit-reached` | a limit of this run stopped the reader before the component could cover the document; the run reaches no verdict at all and leaves with exit code 7 |
| `not-run-by-this-command` | `esj inspect` names the pack and runs none of it; the second pass of `--after-repair` judges bytes this run made |
| `no-rules-for-profile` | the document names a specification the pack carries no rules for |
| `not-in-this-version` | a component announced for a later version and not built yet |
| `extension-registry-missing` | a path only an extension registry defines, and none was loaded; `--extension xrechnung` or `--extension b2c` loads the registries this build carries |
| `extension-for-another-edition` | the same path, and `--extension` loaded the registry: it imports another edition than the document names, so the run left it out (`SPEC.md` section 10). The `ESJ-L2-NOT-CHECKED` finding names the option, the edition the registry imports and that it is not this one |
| `edition-unknown` | no registry for the edition the document names, so the model layers had nothing to measure it against |
| `no-pack-for-edition` | a registry for that edition is carried and this build holds no rule pack written against it; [`editions.md`](editions.md) |
| `no-artefacts-for-edition` | a registry and a rule pack for that edition are carried and no official validation artefact is published for it, so the component that runs those artefacts could not run; no work in this project closes it; [`editions.md`](editions.md) |
| `term-not-in-syntax` | the writer had no place in the target syntax for something an ESJ document states, so the XML the artefacts would have judged is not that document; `--via` chooses the other syntax. Not where every term left behind belongs to a registry that declares `"transport": "none"`: the row runs and names them ([`b2c.md`](b2c.md)) |
| `term-not-stated` | the syntax requires an element a business term of the model carries and this document does not state, so the written XML is a rendition its own schema refuses and the finding is not about the invoice; `--via` chooses the other syntax |
| `element-not-in-model` | the same where no business term names the element and the table states no value for it |
| `written-over-bound` | the XML an ESJ document was written to is longer than `--max-output-bytes` or `--max-input-bytes` allows this run, so the artefacts had nothing to read; the other components keep their answer. A clock that ran out is not this cause: it leaves no verdict and exit code 7 |

### The container

A PDF input adds two more ways for the verdict to be `INVALID`. Both are about the container, not
the invoice: a container whose own checks failed, and a container whose profile carries no invoice
line. The second gives `INVALID` with exit 1, the reason "not an EN 16931 invoice" and an `Invoice:`
line reading `NOT CHECKED (profile MINIMUM)`, so that nobody books a conformant MINIMUM file as a
bad invoice. The report keeps the two apart, in two lines and two members
([`pdf-input.md`](pdf-input.md)).

### Exit codes

| Code | When | What a pipeline should do |
|---|---|---|
| 0 | completely checked, nothing fatal found | proceed |
| 1 | a fatal finding, from either engine | reject the document, report the findings |
| 2 | the input could not be read, recognized or parsed, a validation pack could not be read, or the command line could not be parsed | the bytes, a pack or the arguments are the problem, not the invoice |
| 3 | not this tool's: the virtual machine aborted under `-XX:+ExitOnOutOfMemoryError` | treat as a crash, never as a verdict |
| 4 | something this version does not implement | |
| 5 | an internal error, a defect of the tool | report it; the document is unjudged |
| 6 | the output could not be written in full: a full disk, or a `--report` the run could not deliver | the bytes received are not the bytes produced; repeat the run, and give an undelivered report more time or another destination — the verdict printed above it stands |
| 7 | a configured resource or time limit was reached; no verdict at all | **retry or raise the limit; do not reject** |
| 8 | the conversion cannot be completed as constrained: `esj convert --fail-on-loss` found part of the document the target syntax has no place for | nothing was written; keep the ESJ document, or convert without the option and accept what the warnings name |
| 9 | nothing fatal, and part of the check did not run or did not complete | read `reasons`; supply what is missing, or accept the reduced check deliberately |

`--max-runtime` is one number over the whole command: taking the bytes in, reading the document into
the model, running the artefacts, and a validator lent by `--verapdf`. When it stops a run, the
message names the step the time ran out in.

### When a profile brings no rules with it

ESJ checks a document that names a specification the pack carries no rules for against the schema of
its syntax and nothing else: `INDETERMINATE`, cause `no-rules-for-profile`. Such a document *was*
checked less thoroughly than it asked to be. A document that names EN 16931 and no CIUS leaves the
same rule sets unused and is complete. Both name the rule sets left out, set
`syntax.profileRulesSkipped` and carry `profile-rules` in `notChecked`. Where a recipe of this build
brings the rules of the profile (Peppol BIS Billing 3.0), the text report adds a line with the
`esj packs fetch` command that makes their pack
([Packs made on this machine](#packs-made-on-this-machine)). The verdict stays what it is.

## The report

`esj validate --report proof.html` (or `.pdf`) writes the same run as one file you can keep: the
verdict, the rows and the exit code the command printed, never a second run
([`cli.md`](cli.md#the-report)).

| | |
|---|---|
| What is in it | the input and the document it became, with their digests; every pack with its version; the check table row by row; the findings of both engines with their categories and both levels; what the import could not carry; and the invoice as [`rendering.md`](rendering.md) draws it |
| Two forms | HTML, one page with the invoice in a sandboxed frame; PDF, the same content as pages with the invoice after it in the same file. One file either way, nothing fetched when it is opened |
| Two languages | `--report-lang de` (the default) or `en`, for the report's own words and for the invoice in it. A rule, a check or a pack someone else named keeps the name it came with |
| The same bytes | the same input and the same options give the same file: no clock, no duration, no build stamp. `--report-time` prints the moment the caller passes, exactly as written |

The report claims no more than the run did. A line under the verdict says that it is technical
conformance. The PDF/A row reads what the container declares, not `OK`, unless `--verapdf` ran. The
syntax block over an input that was never XML is `not applicable`, never a pass. A check that did
not run says so and why, an `INDETERMINATE` verdict names each component, and a levelled finding
carries both levels. ESJ reports a document that no renderer of this build takes all the same, with
the sentence that says what refused it where the invoice would be. The same holds for a document
whose rendering would be larger than the HTML form carries ([`cli.md`](cli.md#the-report)).

## `--no-syntax` and `--rules`

`--no-syntax` leaves the official artefacts out and runs the semantic engine alone. Use it for a
document too large to pay the Schematron for, or in a pipeline that validated the XML elsewhere
([`validation-measurements.md`](validation-measurements.md) has the numbers). What remains, the
structural layers and the native business rules, is linear in the number of invoice lines. That is
how ESJ validates a document of hundreds of thousands of lines. It is never the default, and a run
that uses it reaches no verdict in any form. The last line reads `INDETERMINATE — nothing fatal
found; missing from the check: syntax-binding (skipped-by-caller)`. The `verdict` member reads
`INDETERMINATE` and the exit code is 9. `notChecked` carries `syntax-binding`, or `written-syntax`
for an ESJ input, whose artefacts read the written document.

`--rules none` leaves the native business rules out. `--rules en16931`, the default, runs the pack
this build carries. The two switches are independent; what `--rules none` costs per input is under
[The complete check](#the-complete-check). Together they are the cheapest run there is, the
structural layers alone, and reach no verdict on either input.

`--via cii` is the default because the cross industry invoice carries the semantic model most
completely. Over the corpus the artefacts accept 85 of the 86, all but the one whose extension the
binding does not cover; UBL requires elements EN 16931-1 states no term for. Pass `--via ubl` if
your documents travel as UBL and you want that syntax's artefacts to judge them.

### A native finding is levelled by the profile, as an artefact's is

The native pack states what the standard states, at the level the rule declares. The level table of
the pack may level a rule identifier for the profile the document names in BT-24 (**Two publishers,
two levels** above). ESJ then levels the native finding of that identifier the same way before it
counts. The text report writes `info, fatal by the standard, levelled by the profile <identifier>`,
and the JSON report carries both as `severity` and `flag`. A fatal one left after levelling is the
`invoice.reason` `rule-finding` ([`cli.md`](cli.md#the-json-report)).

The levels are a fact of the profile, not of the syntax. This holds for XML, for the XML inside a
PDF and for an ESJ document that names a profile. Where no syntax chooses a table, only what every
table of that profile agrees on applies. ESJ does not level a document that names no profile, or one
the pack has no table for. Four corpus instances turn on it: three with an identification scheme
their own specification defines and the EN 16931 code list does not, one over BR-CO-16.
[`../conformance/rules/corpus.md`](../conformance/rules/corpus.md) names them.

**A scenario is a profile and a syntax**, so one invoice can be `VALID` as UBL and `INVALID` as CII.
ESJ levels a document by the table of the syntax it arrived in. Where the target syntax's table is
the stricter one, `esj convert` and `esj render --embed` say so in an `info:` line
([`design-decisions.md`](design-decisions.md#business-rules-are-a-layer-of-their-own-and-are-checked)).

## The oracle: measured, not asserted

Each engine ran beside the published thing it could be wrong about, over the same bytes, finding by
finding.

### The syntax engine, beside the official validator

[`../conformance/syntax/ledger.md`](../conformance/syntax/ledger.md) is the record.

| | |
|---|---|
| Documents | 149 — the 86 instances of the conformance corpus and 63 mutations |
| Oracle | KoSIT Validator 1.6.3 with the XRechnung validator configuration of 2026-08-31, its EN 16931 stylesheets replaced by those of release 1.3.17 |
| Under test | the syntax engine of this repository with `xrechnung/3.0.2/2026-10-12` |

| The two runs agree on | |
|---|---|
| the rule identifiers reported | 149 of 149 |
| those identifiers with the flag of the artefact | 149 of 149 |
| the node each finding names | 149 of 149 |
| the location expression, character for character | 149 of 149 |
| the verdict | 146 of 149 — and on the 3 that differ, neither run accepted the document |

The mutations are the second half of the question: a corpus of valid documents can only show that
neither side complains. Each mutation lives as data, an instance, a location in it and one change to
make there, so no broken invoice is checked in.

No configuration release of KoSIT carries release 1.3.17 yet. The oracle therefore ran the
configuration of 2026-08-31 with the stylesheets of this pack in place of its own. Run as published,
it differs on 5 documents, each by a change of the release, and the verdicts are the same either
way. The incomplete row is not a disagreement about an invoice. The **verdicts** differ on 3
documents. All 3 name a core invoice usage specification that no scenario of the official
configuration matches. That validator rejects such a document without running anything. This one
checks what does apply and then reaches no verdict either: `INDETERMINATE`, cause
`no-rules-for-profile`, exit code 9.

**What the ledger licenses this project to say** is the figures above and nothing further. It is no
proof of equivalence on documents nobody has run. The oracle is not part of the build, because
running it needs a download of the official validator. The build keeps its answer, with a test that
re-runs this engine over the mutation set and pins every identifier it must report.

### The Peppol pack, beside OpenPeppol's own tests

`esj packs fetch` compiles a pack on the machine that runs it. The question is whether the compiled
rule sets say what their publisher's tests say they say. Nothing of OpenPeppol is in this
repository, so the tests fetch the evidence at test time. Two tests run only with
`-Desj.network=true`, once for each release this build carries. They fetch every file from the
revision the recipe pins and weigh it against the SHA-256 that
[`../conformance/peppol/README.md`](../conformance/peppol/README.md) records. CI runs them in the
JDK 21 job. Without the property they are skipped by name, and the build stays offline.

| | 3.0.21 | 3.0.20 |
|---|---|---|
| Under test | the pack `peppol-bis-billing/3.0/3.0.21` | the pack `peppol-bis-billing/3.0/3.0.20` |
| OpenPeppol's examples, `rules/examples/` | 10 of 10 `VALID`, exit 0 | 9 of 9 `VALID`, exit 0 |
| OpenPeppol's unit tests, `rules/unit-UBL-PEPPOL/` and `rules/unit-CII-PEPPOL/` | 110 test sets (2 hold no test), 493 tests, 493 expectations over 62 rules | 102 test sets (2 hold no test), 354 tests, 354 expectations over 58 rules |
| Rules whose every expectation holds | 62 of 62 | 58 of 58 |
| Rules with an expectation that does not hold | 0 | 0 |

3.0.21 adds the warnings `PEPPOL-COMMON-R054`, `-R055`, `-R056-1`, `-R056-2` and `-R057` (Dutch
identifiers and VAT numbers), and in UBL `DE-R-T02`. It makes `PEPPOL-COMMON-R052` and `-R053`, and
in UBL `DK-R-003` and `DK-R-017`, fatal. It takes the EN 16931 rules in version 1.3.16, which
withdraws `BR-CO-25` and adds `UBL-SR-56` and `CII-SR-467` to `CII-SR-494`. The table by rule set is
in [`../conformance/peppol/README.md`](../conformance/peppol/README.md#from-3020-to-3021).

A unit test is a fragment and the rules it expects to fire, or not to fire. The test runs the rule
sets its configuration names over the fragment: the Peppol rules of the syntax, with the EN 16931
rules where the configuration adds them. It compares rule identifier by rule identifier, with the
flag and, where the test states one, the count.

Four changes to `base-example.xml`, one at a time, each `INVALID` with exit 1 under either release:

| Change | Rules that fire |
|---|---|
| no invoice number (BT-1) | `BR-02`, `PEPPOL-EN16931-R008` |
| VAT category code `X` on a line | `BR-CL-18`, `BR-S-08` |
| category `S` with the exemption reason `VATEX-EU-G` | `BR-S-10`, `PEPPOL-EN16931-P0104` |
| a business process no Peppol process names (BT-23) | `PEPPOL-EN16931-R007` |

A specification identifier (BT-24) the pack does not name is not a change the pack can judge. The
identifier is how ESJ chooses the pack, so the bundled pack judges the document, and it ends
`INDETERMINATE`, cause `no-rules-for-profile`. So it does with `--pack` naming the Peppol pack,
whose Peppol rule sets do not apply to it. `PEPPOL-EN16931-R004`, the rule that asks for that
identifier, is among the 58 of the unit tests.

Fetching and compiling the pack takes about 5 seconds with the jar and about 2 with the native
executable; about a second of it is the download. A document then takes what a bundled pack takes:
0.2 to 0.3 seconds for each of OpenPeppol's examples with the native executable.

### The rule pack, beside the official Schematron

The rules are written over business terms, so reading them cannot show whether they decide an
invoice the way the artefacts of CEN/TC 434 decide it. The same bytes go through both engines,
identifier against identifier, and
[`../conformance/rules/ledger.md`](../conformance/rules/ledger.md) names every difference.

| | |
|---|---|
| Under test | the pack `en16931/1.3.17` of this repository, over the document after import |
| Oracle | the EN 16931 Schematron of release 1.3.17, run in process by the syntax engine on the same bytes |
| Input | the 86 instances of the conformance corpus and 455 mutations of them |
| Compared | the identifiers of category `EN-BR`, `EN-DEC` and `EN-CL` that each engine reports |

Both engines are on the class path, so the comparison runs in the ordinary build. Nothing is
downloaded or installed, and these mutations too live as data.

| Of the 217 rules of the pack | |
|---|---|
| Both engines report the same identifiers on every shape measured | 137 |
| They agree on one shape of the defect and not on another, each named | 53 |
| They differ, each difference explained | 25 |
| The oracle cannot be asked at all | 2 |
| **Open — a difference nobody has accounted for** | **0** |

| Of the 455 mutations | |
|---|---|
| Aimed at a rule, both engines reporting the same set | 326 |
| Aimed at a rule, the two sets differing | 112 |
| Near misses, both engines silent as they should be | 17 |

| Of the 86 corpus instances | |
|---|---|
| Both engines silent | 82 |
| Both engines reporting, with the same identifiers | 4 |
| Either engine reporting something the other does not | 0 |

Agreement on one shape of a defect is not agreement on the rule. A rule with an agreeing mutation
and a differing one therefore counts as neither, and the ledger names the differing shape. The 80
differences come down to 12 causes. Every one is a property of the two engines, not of an invoice:

- an operand the rule needs was removed, and the artefacts decide an arithmetic rule against a node
  that is not there; a trailing zero the canonical decimal form does not carry, which the decimal
  rules count in the XML and cannot count in the document;
- a business term the syntax binding did not carry into the model; two artefacts numbering one
  defect differently; a rule the release does not carry; an artefact expression that cannot fire;
- nine rules where the two artefacts ask for a different closeness of the same two figures; the pack
  faults from the wider reading on and warns inside the zone only one artefact grants;
- and 3 that release 1.3.17 brought: the CII artefact numbers the categories `L` and `M` the other
  way round and asks for a rate above zero in `L`; both artefacts dropped the prefix `EL` from
  `BR-CO-09`; and the UBL artefact reports `BR-CO-09` on a tax scheme that states no identifier,
  which 25 mutations show.

The pack follows the standard in all 3, and the ledger has each with its mutation. Only the second
changes a verdict: a valid invoice with a Greek VAT identifier is invalid against release 1.3.17.
The third adds a finding to a document that `UBL-SR-53` already reports in both releases.

**What the ledger licenses this project to say** is exactly the figures above, each of the 80
differences named with its reason. Do not quote the first figure alone: a rule counted as agreeing
was measured on the shapes the mutation set carries and on no others.

What may **not** be said is that this pack implements the official rule sets or may replace them.
The artefacts and the core invoice usage specifications are the authority on the business rules of
EN 16931. `SPEC.md` section 9.4 keeps this engine's findings a layer of their own, and
`esj validate` runs the released artefacts over every XML input, whatever this pack says.
