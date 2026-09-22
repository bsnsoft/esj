# Validation

*Part of [EN16931 Semantic JSON](../README.md).*

`esj validate` runs two engines over one input and ends in one verdict. What each runs, what a
finding means, what a verdict and an exit code commit to, and what has been measured.
[`cli.md`](cli.md) is the option-by-option reference.

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

The **syntax engine** takes the bytes exactly as they arrived and runs the official artefacts of
the profile BT-24 names, in the release the pack names. The **semantic engine** runs the
structural layers of `SPEC.md` section 9 over the semantic document — L1 for an ESJ input, L2 and
L3 for both — and then the business rules of EN 16931-1, clause 6.4 over the business terms. It
knows nothing about XML.

Both run over an XML input. **Semantic validity does not prove syntax-binding validity**: an
element written where the binding forbids it, an identifier without the scheme attribute the
binding requires, a code in an attribute the importer never reads are all invisible in the
semantic document, because an import is a projection. An ESJ document that never was XML is
written out through a binding table in memory, so that the artefacts read it too.

## The packs

A **validation pack** is a directory of the official artefacts for one profile, in the form a
machine can execute, unmodified, each file under the licence it came with.
[`../packs/README.md`](../packs/README.md) describes it; [`../packs/SOURCES.md`](../packs/SOURCES.md)
records for every file where it was published, under which licence, with which SHA-256 and when.

This build carries one pack, `xrechnung/3.0.2/2026-08-31`, of four components:

| Component | What it is | Licence |
|---|---|---|
| `ubl-2.1-xsd` | the OASIS UBL 2.1 schema modules the Invoice and CreditNote documents import | OASIS UBL 2.1 notice |
| `cii-d16b-xsd` | the UN/CEFACT CII D16B schema modules CrossIndustryInvoice imports | UN/CEFACT notice |
| `en16931-ubl-schematron`, `en16931-cii-schematron` | the EN 16931 Schematron of CEN/TC 434, release 1.3.16, compiled to XSLT | EUPL-1.2 |
| `xrechnung-ubl-schematron`, `xrechnung-cii-schematron` | the XRechnung Schematron 2.6.0, compiled to XSLT | Apache-2.0 |

and eight level tables, 56 rules in all, which are what the XRechnung profiles say a rule of
those artefacts means for a document of their own profile (**Two publishers, two levels**
below). `esj --list-packs` prints all of it for a build. Three properties a report depends on:

**A release is identity.** A pack is named by profile, profile version and the release date of
the artefact bundle, and all three appear in every report, because a bugfix release adds,
removes or re-levels rules. A new release is a new directory beside the old one.

**Nothing is fetched at run time.** The packs are in the jar, so a build works offline and the
digests in `SOURCES.md`, recomputed on every build, are those of the artefacts that judged.

**A pack can be replaced, and every form says when it was.** `--pack <directory>` runs a pack
from a directory holding a `pack.json`, `--pack <id>` selects a bundled one; a manifest writes
its own identity, so the source is printed beside the name and `--output json` carries
`syntax.pack.source` with `bundled` or `supplied`. Adding a release is a directory, a manifest,
the provenance rows and a line in the index — no code:
[`../packs/README.md`](../packs/README.md#adding-a-release).

## The check table

The rows of the syntax block are the components of the chosen pack in manifest order; the rows of
the semantic block are the layers. Every row is `OK`, a count of findings, `no verdict` where the
row ran and could not measure what it was asked to — a limit, an unknown edition, a path only an
extension registry defines — or a reason it did not run. A pack carries the artefacts of every
syntax it covers, so a UBL invoice leaves the CII rows unused; `--verbose` prints them and the
JSON report always carries them.

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

A program reads `syntax.ran`, `syntax.skipped`, `layers` and `rules` in the JSON report instead,
with the components under the pack's own names (`en16931-ubl-schematron`), and `notChecked` as a
list of stable tokens — `format-l1`, `model-l2`, `cardinality-l3`, `business-rules`,
`profile-rules`, `syntax-binding`, `written-syntax`. `notChecked` is everything this run did not
look at or could not measure, including what there was nothing to look at: layer L1 over a document
that arrived as XML is in it and is no gap. The `reasons` array beside it carries the missing
components of the complete check, each with its cause, and is the list the verdict turns on; every
component of `reasons` is in `notChecked`.

The last row is the engine of [`rules/`](../rules/README.md) and of the module `esj-rules`: one
rule serves a UBL invoice, a CII invoice and a document that was never XML alike. Its findings
are a layer of their own — category `EN-BR`, `EN-DEC` or `EN-CL`, engine `native` — and are
never presented as ESJ conformance (`SPEC.md` 9.4).

Both engines check those rules and one identifier can appear in both blocks. Neither report is
merged into the other and neither is suppressed: the artefact reads the document as it arrived, the
native pack what the import carried into the model. The lines and the report file name the overlap
under the native findings; `--output json` keeps `syntax.findings` and `rules.findings` apart, with
the engine on every finding.

## The rule pack

[`../packs/`](../packs/README.md) holds other people's artefacts, [`../rules/`](../rules/README.md)
this project's own. A **rule pack** is a directory of rules written over business terms, with the
code list snapshots its membership tests are decided against and a manifest naming both. This
build carries one:

| `en16931/1.3.16` | |
|---|---|
| What it states | the business rules of EN 16931-1, clause 6.4, together with the decimal restrictions of clause 6.5.12, Table 26, and the code list restrictions of clause 6.3, which the official artefacts number `BR-DEC-*` and `BR-CL-*` |
| Rules | 217 — 189 written in the rule language, 28 in Java, indistinguishable in a report |
| Code lists | 17 dated snapshots, each with its publisher, its retrieval date and what that publisher says about reuse in [`../rules/en16931/1.3.16/codelists/SOURCES.md`](../rules/en16931/1.3.16/codelists/SOURCES.md) |
| Verified against | the CEN/TC 434 validation artefacts, release 1.3.16 |

`1.3.16` is the release of the artefacts the pack was *measured against*, not a version of the
standard and not of this project. A later release is a directory beside this one.

### The rule language, in short

A rule is a JSON object: the identifier the standard gives it, a severity, the business group
it is a statement about, the terms it reads, an assertion built from a closed set of thirty-one
operators, a message, and the clause of the standard in `source`.

Four properties of the language matter to a reader of a report. A rule addresses business terms
and never an occurrence index. A rule with a business group as its context is evaluated once per
instance of that group and reports at the instance it failed at. Arithmetic is exact
`BigDecimal`, and the operator that counts fraction digits applies to the fixed-scale amounts of
the standard alone — a unit price, a quantity and a percentage have no decimal limit and are
never rounded or cut. A rule a closed operator set cannot express is written in Java under the
same identifier, in the same pack, producing the same finding.
[`../rules/README.md`](../rules/README.md) is the whole language.

### What it covers, and what it does not

Every identifier of the official validation artefacts of release 1.3.16 is either implemented
here or named with a reason, and a test of `esj-rules` reads the table that says which:

| | Rules of the release | Implemented | Not applicable | Deferred |
|---|---|---|---|---|
| Model rules | 201 | 195 | 0 | 6 |
| Code list rules | 22 | 21 | 1 | 0 |
| **Together** | **223** | **216** | **1** | **6** |

The 217 of the pack are those 216 and `BR-CO-25`, which the standard states in clause 6.4.2 —
an invoice with an amount due for payment states a payment due date or payment terms — and which
release 1.3.16 carries no identifier for.

The seven that are not implemented are in
[`../conformance/rules/not-applicable.md`](../conformance/rules/not-applicable.md), each with
its reason: `BR-CL-03` is about an attribute of the XML binding the semantic model has no place
for, `BR-CO-05` to `BR-CO-08` ask whether a reason code and a free-text reason agree, which the
official artefacts assert nothing for either, and `BR-B-01` and `BR-B-02` wait for an edition of
the standard. The rule-by-rule table is
[`../conformance/rules/coverage.md`](../conformance/rules/coverage.md); what the pack says about
invoices known to be good is [`../conformance/rules/corpus.md`](../conformance/rules/corpus.md).

## What the import reports

The layers and the native rule pack can only decide what the import carried, so what it did
*not* carry is reported. Three rules govern it.

**Nothing is dropped in silence.** Where the model has a place for what arrived, the value is
kept: an identifier the binding does not select by its scheme reaches the document without one,
the model layer reports `ESJ-L2-COMPONENT-MISSING` at its path and `BR-62` to `BR-65` report it
— the one model error that does not stop the rule pack. Where the binding has no place for it
— in CII a tax registration under neither the value added tax scheme nor the fiscal one is
neither BT-31 nor BT-32, where UBL binds BT-32 by the negation and takes it — the element is
reported under the rule below. [`../conformance/readers.md`](../conformance/readers.md) records both, term by term.

**A syntax element the binding cannot classify is reported, not dropped.** A binding selects the
element carrying a term by a value in or on it — an allowance charge by its indicator, payment
instructions by the payment means type code, a CII date by its format qualifier — and an element
that states no such value is selected by nothing. Both readers write one `UNPLACEABLE` note per
element, naming it and the terms consequently absent; [`../conformance/rules/not-applicable.md`](../conformance/rules/not-applicable.md)
records which rules that costs.

**A notation the semantic document cannot carry is reported.** The canonical decimal form writes
no trailing zeros, so `319.860` is `319.86` in the document; the importer warns at that path.

A warning-level note means content of the source did not reach the document; an information-level
note means nothing EN 16931 has a place for was lost. The warnings are on the error stream of
every command that imports, collapsed and cut off, and `--verbose` adds the information-level
notes; in `--output json` they are the `warnings` and `information` arrays, and in the report file
([`cli.md`](cli.md#the-report)) a warning is a finding of the engine `importer` and an
information-level note stands under *Before the check*.

## Finding categories

A validation produces findings from several rule sets at once, so every finding carries a
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

A build enumerates the identifiers out of the vendored artefacts and asks the table about every
one; a family of a later release that is not in the table lands in `OTHER` with its identifier
intact. Beside the category, a finding carries the rule identifier, the artefact's message, the
node it names, the pack and engine it came from, and two levels.

### Two publishers, two levels

The artefact flags each of its rules, and the core invoice usage specification a document names
may say something else about that rule for its own profile. XRechnung does both: `BR-CL-13` down
for its CVD profile, whose item classification scheme the EN 16931 code list does not carry, and
`UBL-CR-646` up for its standard profile. A finding therefore reports both — `flag` is the
artefact's, untouched, `severity` is the level the profile asks for and the one the verdict is made
on, and where they differ every form says so, `[information, flagged fatal by the artefact]`. The
levels are data of the pack, transcribed from the configuration the specification publishes and
recorded in [`../packs/SOURCES.md`](../packs/SOURCES.md); nothing in the code levels anything. Over
the 149 documents of the ledger below, 19 carry a finding whose two levels differ, and on 7 of
those the verdict would be the other one without the tables.

## The complete check

**`VALID` and exit code 0 are given only where the complete check for that kind of input ran and
nothing fatal was found.** What that check is, this tool decides per input kind:

| Input | The complete check |
|---|---|
| **XML** (UBL, CII; also the XML taken out of a PDF) | well-formedness and the bounded parse; the XML Schema of its syntax; the EN 16931 Schematron; the CIUS Schematron where the specification identifier of the document names a CIUS the pack carries; and the structural layers L2 and L3 of the imported document |
| **ESJ** | layers L1, L2 and L3, the business rules of EN 16931 as the native pack, and the official artefacts of its profile over the XML the document is written to |
| **PDF** | the container checks of this tool, plus the rows of the embedded XML |

**Layer L1 is in the ESJ row and in no other**: L1 is decided by the bytes of an ESJ document
(`SPEC.md` section 9.1), a document that arrived as UBL, as CII or inside a PDF never was such a byte
sequence, and `SPEC.md` section 3.5 measures a result over it against L2 and L3 alone, so an XML
input reaches `VALID` with `format-l1` among the rows this run did not look at.

**A document that names EN 16931 and no CIUS needs no CIUS rules**, and the check is complete;
a document that names a specification the pack carries no rules for leaves the same rule sets
unused and *was* checked less thoroughly than it asked to be. The `baseProfiles` member of the
pack manifest tells the two apart ([`../packs/README.md`](../packs/README.md#the-manifest)).

**The native rules are a required row for an ESJ input and an additional one for XML.** For an
XML input the official artefacts of the profile define `VALID`, so the native pack adds evidence
rather than coverage and `--rules none` leaves the check complete. For an ESJ input the pack is a
row of its own and `--rules none` makes it `skipped-by-caller`, with the verdict `INDETERMINATE`,
as `--no-syntax` does for an XML input. A fatal native finding is `INVALID` for either input; a
declared CIUS the native rules do not cover stays a gap for an ESJ input until a pack covers it.

**The official artefacts reach an ESJ input through a binding table.** No artefact reads a document
that was never XML, so `esj validate` writes it out — a cross industry invoice, or a UBL document
with `--via ubl` — and runs the artefacts of its profile over the result. Nothing is written to
disk and the document under test stays the ESJ one. The row holds only while the two say the same
thing, and four causes say where it does not: `term-not-in-syntax`, `term-not-stated`,
`element-not-in-model` and `written-over-bound` (the table below); the row is then not applicable
and the verdict `INDETERMINATE`. An element the binding table states a value for is written and the
row runs; `--no-syntax` takes the row out, and a profile with no artefact leaves it
`no-rules-for-profile`.
[`../conformance/writers/cii-roundtrip.md`](../conformance/writers/cii-roundtrip.md) and
[`ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md) measure both writers over the
corpus, which is what the coverage of this row rests on.

**PDF/A conformance is outside the verdict** unless [`--verapdf`](cli.md#validating-the-pdfa-claim)
names a validator; without it the row reads what the file declares ([`pdf-input.md`](pdf-input.md)).

## Verdict and exit code

The verdict is one word over both engines, and there are three of them:

| Verdict | When | Exit |
|---|---|---|
| `VALID` | the complete check for this input ran and nothing fatal was found | 0 |
| `INVALID` | a fatal finding in anything that ran, whatever the coverage | 1 |
| `INDETERMINATE` | nothing fatal was found and a component of the complete check did not run or did not complete | 9 |

A run stopped by a **bound of this run** reaches none of the three and says none of the three
words. Exit code **7** has that one meaning: a bound was met, the document was not judged, and
the answer is to read it again against a larger bound rather than to reject it. Whether a report
exists turns on whether the semantic document had been built when the bound was met:

| Where the bound was met | What the run leaves |
|---|---|
| after the document exists: an ESJ input under a reader bound of `SPEC.md` section 12.2 | the whole answer. Last line `NO VERDICT — a limit of this run was reached …`, the rows the bound cut short `no verdict`, `"verdict": null` and the cause `limit-reached` under each component of `reasons` |
| while the document is still being built: every importer path, so every XML and every PDF input, as well as [`--max-runtime`](cli.md#limits) and the container bounds ([`pdf-input.md`](pdf-input.md)) | no answer at all. One line on the error stream, nothing on the standard output — with `--output json` as well |

`INDETERMINATE` always carries why — the last printed line is `INDETERMINATE — nothing fatal
found; missing from the check: …`, naming each component and its cause, and `--output json`
carries the same as a `reasons` array of `{component, cause}`, from a closed vocabulary:

| `cause` | What happened |
|---|---|
| `skipped-by-caller` | `--no-syntax`, or `--level l2` |
| `limit-reached` | a limit of this run stopped the reader before the component could cover the document; the run reaches no verdict at all and leaves with exit code 7 |
| `not-run-by-this-command` | `esj inspect` names the pack and runs none of it; the second pass of `--after-repair` judges bytes this run made |
| `no-rules-for-profile` | the document names a specification the pack carries no rules for |
| `not-in-this-version` | a component announced for a later version and not built yet; no required row carries it today |
| `extension-registry-missing` | a path only an extension registry defines, and none was loaded; `--extension xrechnung` or `--extension b2c` loads the registries this build carries |
| `edition-unknown` | no registry for the edition the document names, so the model layers had nothing to measure it against |
| `no-pack-for-edition` | a registry for that edition is carried and no artefact is written for it — no rule pack, and no binding table an ESJ document could be written through; [`editions.md`](editions.md) |
| `term-not-in-syntax` | the writer had no place in the target syntax for something an ESJ document states, so the XML the artefacts would have judged is not that document; `--via` chooses the other syntax. Not where every term left behind belongs to a registry that declares `"transport": "none"`: the row runs and names them ([`b2c.md`](b2c.md)) |
| `term-not-stated` | the syntax requires an element a business term of the model carries and this document does not state, so the written XML is a rendition its own schema refuses and the finding is not about the invoice; `--via` chooses the other syntax |
| `element-not-in-model` | the same where no business term names the element at all and the binding table states no value for it; no corpus instance and no example reaches it |
| `written-over-bound` | the XML an ESJ document was written to is longer than `--max-output-bytes` or `--max-input-bytes` allows this run, so the artefacts had nothing to read; the other components keep their answer. A clock that ran out is not this cause: it leaves no verdict and exit code 7 |

### The container

A PDF input adds two more ways for the verdict to be `INVALID`, both about the container rather
than the invoice: a container whose own checks failed, and a container whose profile carries no
invoice line — `INVALID` with exit 1, the reason "not an EN 16931 invoice" and an `Invoice:`
line reading `NOT CHECKED (profile MINIMUM)`, so that nobody books a conformant MINIMUM file as
a bad invoice. The report keeps the two apart, in two lines and two members;
[`pdf-input.md`](pdf-input.md) is their page. PDF/A conformance is among them only where
`--verapdf` was given: without it the row reads what the file declares, and a run that named a
validator of its own turns a refusal into `Container: INVALID`
([`cli.md`](cli.md#validating-the-pdfa-claim)).

A file carrying an ESJ document beside its invoice
([`pdf-output.md`](pdf-output.md#the-esj-document-beside-the-invoice)) gains one row:

| `ESJ document attached` | When | Verdict |
|---|---|---|
| no row, `"esj": null` | the file carries none | |
| `OK`, `"status": "one"` | it and the invoice name one semantic model and are two accounts of one invoice; where it carries paths of terms the syntax of the invoice binds nothing of — what the enclosure is for — the invoice had nothing to measure them against, and the row counts them as not checked, as `"pathsNotChecked"` does | |
| `PDF-ESJ-DISAGREES`, with the model or the first differing paths | they are not | `Container: INVALID`, exit 1 |
| `PDF-ESJ-UNSOUND`, with the first model findings | it is no document of the model it names | `Container: INVALID`, exit 1 |
| `PDF-ESJ-UNREADABLE` | it is no ESJ document this reader reads | `Container: INVALID`, exit 1 |
| `PDF-ESJ-UNCHECKED`, `"agrees": null` | nothing was compared: the invoice is in a syntax with no binding table here, or `esj inspect` met a bound inside the attachment | warning |
| no row, `"status": "several"`, `PDF-EMBEDDED-SEVERAL-ESJ` | the file carries more than one, so none was checked | `Container: INVALID`, exit 1 |
| — | a bound of the run stopped the reader before it was read | no verdict, exit 7 |

The attachment is measured on layers L1 and L2 and then compared with the invoice; L3 and the
business rules are questions about the XML. `esj inspect` gives no verdict and reports a bound
inside the attachment on that row instead of leaving at exit 7.

### Exit codes

| Code | When | What a pipeline should do |
|---|---|---|
| 0 | completely checked, nothing fatal found | proceed |
| 1 | a fatal finding, from either engine | reject the document, report the findings |
| 2 | the input could not be read, recognized or parsed, or the command line could not be parsed | the bytes or the arguments are the problem, not the invoice |
| 3 | not this tool's: the virtual machine aborted under `-XX:+ExitOnOutOfMemoryError` | treat as a crash, never as a verdict |
| 4 | something this version does not implement | |
| 5 | an internal error, a defect of the tool | report it; the document is unjudged |
| 6 | the output could not be written in full: a full disk, or a `--report` the run could not deliver | the bytes received are not the bytes produced; repeat the run, and give an undelivered report more time or another destination — the verdict printed above it stands |
| 7 | a configured resource or time limit was reached; no verdict at all | **retry or raise the limit; do not reject** |
| 8 | the conversion cannot be completed as constrained (reserved) | |
| 9 | nothing fatal, and part of the check did not run or did not complete | read `reasons`; supply what is missing, or accept the reduced check deliberately |

**7 is never invalidity.** A document the tool gave up on is not a document the tool rejected,
and a pipeline that read a 1 there would quarantine a sound invoice. The message names the step
the time ran out in. `--max-runtime` is one number over the whole command — taking the bytes in,
reading the document into the model, running the artefacts, and a validator lent by `--verapdf`
— because a bound covering only the last would be no bound for a caller who set one because the
document came from a stranger.

### When a profile brings no rules with it

A document that names a specification the pack carries no rules for is checked against the
schema of its syntax and nothing else: `INDETERMINATE`, cause `no-rules-for-profile`. A document
that names EN 16931 and no CIUS leaves the same rule sets unused and is complete. Both name the
rule sets left out, set `syntax.profileRulesSkipped` and carry `profile-rules` in `notChecked`.

## The report

`esj validate --report proof.html` (or `.pdf`) writes the same run as one file to keep: the
verdict, the rows and the exit code the command printed, never a second run ([`cli.md`](cli.md#the-report)).

| | |
|---|---|
| What is in it | the input and the document it became, with their digests; every pack with its version; the check table row by row; the findings of both engines with their categories and both levels; what the import could not carry; and the invoice as [`rendering.md`](rendering.md) draws it |
| Two forms | HTML, one page with the invoice in a sandboxed frame; PDF, the same content as pages with the invoice after it in the same file. One file either way, nothing fetched when it is opened |
| Two languages | `--report-lang de` (the default) or `en`, for the report's own words and for the invoice in it. A rule, a check or a pack someone else named keeps the name it came with |
| The same bytes | the same input and the same options give the same file: no clock, no duration, no build stamp. `--report-time` prints the moment the caller passes, exactly as written |

It claims no more than the run did. The PDF/A row is what the container declares, and says so
rather than `OK`, unless `--verapdf` ran — then it is the validator's answer, and a refusal makes
the container `INVALID` there as in the lines; the `ESJ document attached` row is there only for a
file that carries one. The syntax block over an input that was never XML is `not applicable`, never
a pass. A check that did not run says so and why, an `INDETERMINATE` verdict names each component,
and a levelled finding carries both levels. A document no renderer of this build takes is reported
all the same, with the sentence that says what refused it where the invoice would be; so is one
whose rendering would be larger than the HTML form carries ([`cli.md`](cli.md#the-report)).

## `--no-syntax` and `--rules`

`--no-syntax` leaves the official artefacts out and runs the semantic engine alone: for a document
too large to pay the Schematron for, and for a pipeline that validated the XML elsewhere
([`validation-measurements.md`](validation-measurements.md) has the numbers). What remains — the
structural layers and the native business rules — is linear in the number of invoice lines, which
is how a document of hundreds of thousands of lines is validated. It is never the default, and a
run that uses it reaches no verdict in every form: the last line reads `INDETERMINATE — nothing
fatal found; missing from the check: syntax-binding (skipped-by-caller)`, the `verdict` member
reads `INDETERMINATE`, the exit code is 9 and `notChecked` carries `syntax-binding` — or
`written-syntax` for an ESJ input, whose artefacts read the written document.

`--rules none` leaves the native business rules out, `--rules en16931` — the default — runs the pack
this build carries, and the two switches are independent. Over an ESJ input `--rules none` takes out
a required row and the verdict is `INDETERMINATE`, cause `skipped-by-caller`; over an XML input the
official artefacts carry the check and it stays complete. The two together are the cheapest run
there is, the structural layers alone, and reach no verdict on either input.

`--via cii` is the default because the cross industry invoice carries the semantic model most
completely: over the corpus the artefacts accept 85 of the 86, all but the one whose extension the
binding does not cover, where UBL requires elements EN 16931-1 states no term for. `--via ubl` is
for a caller whose documents travel as UBL and wants that syntax's artefacts to have judged them.

### A native finding is levelled by the profile, as an artefact's is

The native pack states what the standard states, at the level the rule declares. Where the level
table of the pack levels a rule identifier for the profile the document names in BT-24
(**Two publishers, two levels** above), the native finding of that identifier is levelled the
same way before it counts: the text report writes `info, fatal by the standard, levelled by the
profile <identifier>`, the JSON report carries both as `severity` and `flag`, and a fatal one
left after levelling is the `invoice.reason` `rule-finding` ([`cli.md`](cli.md#the-json-report)).

The levels are a fact of the profile, not of the syntax: this holds for XML, for the XML inside a
PDF and for an ESJ document that names a profile; where no syntax chooses a table, only what
every table of that profile agrees on applies. A document that names no profile, or one the pack
has no table for, is not levelled. Four corpus instances turn on it, three with an identification
scheme their own specification defines and the EN 16931 code list does not, one over BR-CO-16:
[`../conformance/rules/corpus.md`](../conformance/rules/corpus.md) names them.

**A scenario is a profile and a syntax**, so one invoice can be `VALID` as UBL and `INVALID` as CII:
a document is levelled by the table of the syntax it arrived in, and `esj convert` and
`esj render --embed` say so in an `info:` line where the target syntax's table is the stricter one
([`design-decisions.md`](design-decisions.md#business-rules-are-a-layer-of-their-own-and-are-checked)).

## The oracle: measured, not asserted

Each engine was put beside the published thing it could be wrong about, over the same bytes, and the answers were compared finding by finding.

### The syntax engine, beside the official validator

[`../conformance/syntax/ledger.md`](../conformance/syntax/ledger.md) is the record.

| | |
|---|---|
| Documents | 149 — the 86 instances of the conformance corpus and 63 mutations |
| Oracle | KoSIT Validator 1.6.3 with the XRechnung validator configuration of the release this pack is taken from |
| Under test | the syntax engine of this repository with `xrechnung/3.0.2/2026-08-31` |

| The two runs agree on | |
|---|---|
| the rule identifiers reported | 149 of 149 |
| those identifiers with the flag of the artefact | 149 of 149 |
| the node each finding names | 149 of 149 |
| the location expression, character for character | 94 of 149 |
| the verdict | 146 of 149 — and on the 3 that differ, neither run accepted the document |

The mutations are the second half of the question: a corpus of valid documents can only show that
neither side complains. Each lives as data — an instance, a location in it and one change to make
there — so no broken invoice is checked in.

Neither incomplete row is a disagreement about an invoice. The **location expressions** differ in
form on 55 documents because the two runs execute two compilations of the same rules by different
Schematron compilers; reduced to the node the expression names, the two agree on every finding of
every document, which is the row above. The **verdicts** differ on 3 documents, all naming a core
invoice usage specification no scenario of the official configuration matches: that validator
rejects such a document without running anything, this one checks what does apply and then reaches
no verdict either — `INDETERMINATE`, cause `no-rules-for-profile`, exit code 9.

**What the ledger licenses this project to say** is the figures above and nothing further: not a
proof of equivalence on documents nobody has run. The oracle is not part of the build — running it
needs a download of the official validator — and what the build keeps is its answer, with a test
that re-runs this engine over the mutation set and pins every identifier it must report.

### The rule pack, beside the official Schematron

The rules are written over business terms, so what nobody can check by reading them is whether
they decide an invoice the way the artefacts of CEN/TC 434 decide it. Same bytes through both
engines, identifier against identifier, every difference named in
[`../conformance/rules/ledger.md`](../conformance/rules/ledger.md).

| | |
|---|---|
| Under test | the pack `en16931/1.3.16` of this repository, over the document after import |
| Oracle | the EN 16931 Schematron of release 1.3.16, run in process by the syntax engine on the same bytes |
| Input | the 86 instances of the conformance corpus and 448 mutations of them |
| Compared | the identifiers of category `EN-BR`, `EN-DEC` and `EN-CL` that each engine reports |

Both engines are on the class path, so the comparison runs in the ordinary build: nothing is
downloaded and nothing installed, and these mutations too live as data.

| Of the 217 rules of the pack | |
|---|---|
| Both engines report the same identifiers on every shape measured | 166 |
| They agree on one shape of the defect and not on another, each named | 33 |
| They differ, each difference explained | 16 |
| The oracle cannot be asked at all | 2 |
| **Open — a difference nobody has accounted for** | **0** |

| Of the 448 mutations | |
|---|---|
| Aimed at a rule, both engines reporting the same set | 359 |
| Aimed at a rule, the two sets differing | 76 |
| Near misses, both engines silent as they should be | 13 |

| Of the 86 corpus instances | |
|---|---|
| Both engines silent | 82 |
| Both engines reporting, with the same identifiers | 4 |
| Either engine reporting something the other does not | 0 |

Agreement measured on one shape of a defect is not agreement on the rule, so a rule with an
agreeing mutation and a differing one is counted as neither and the differing shape is named. The
51 differences come down to nine causes, every one a property of the two engines rather than of an
invoice: an operand the rule needs was removed and the artefacts decide an arithmetic rule against
a node that is not there; a trailing zero the canonical decimal form does not carry, which the
decimal rules count in the XML and cannot count in the document; a business term the syntax binding
did not carry into the model; two artefacts numbering one defect differently; a rule the release
does not carry; an artefact expression that cannot fire; and nine rules where the two artefacts ask
for a different closeness of the same two figures, where the pack faults from the wider reading on
and warns inside the zone only one artefact grants. Each is in the ledger with its mutation.

**What the ledger licenses this project to say** is exactly the figures above, each of the 51
differences named with its reason. The first figure may not be quoted alone: a rule counted as
agreeing has been measured on the shapes the mutation set carries and on no others.

What may **not** be said is that this pack implements the official rule sets or may be used in
place of them. The artefacts and the core invoice usage specifications are the authority on the
business rules of EN 16931; `SPEC.md` section 9.4 keeps this engine's findings a layer of their
own, and `esj validate` runs the released artefacts over every XML input whatever this pack says.
