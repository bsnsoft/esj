# The official validator as the oracle

The syntax engine of this project runs the official validation artefacts of a document's
profile. This ledger is the check on that sentence: the same documents were put through
the validator the Koordinierungsstelle für IT-Standards publishes for XRechnung, with the
validator configuration of the same release the pack carries, and the two answers were
compared finding by finding.

It is the only honest way to make the claim. Nothing in this repository could otherwise
tell a working validator from one that runs a stylesheet and reports what happens to come
out of it, and a test suite written by the same hands as the code under it cannot answer
the question either.

## What was run

| | |
|---|---|
| Documents | 149 — 86 instances of the conformance corpus and 63 mutations |
| Oracle | KoSIT Validator 1.6.3, with the XRechnung validator configuration of the release this pack is taken from |
| Under test | the syntax engine of this repository with the pack `xrechnung/3.0.2/2026-08-31` |

The mutations are the second half of the question. A corpus of valid documents can only
show that neither side complains; a document broken on purpose shows that both sides
notice, notice the same thing, and say so at the same level. They live as data in
[`mutations/mutations.json`](mutations/mutations.json) — an instance, a location in it and
one change to make there — so that a reader can see beside each one which rule it is aimed
at, and so that no broken invoice has to be checked in.

| Mutations by what they are aimed at | |
|---|---|
| `CII-BINDING` | 2 |
| `CII-XSD` | 3 |
| `EN-BR` | 19 |
| `EN-CL` | 12 |
| `EN-DEC` | 4 |
| `PROFILE` | 4 |
| `UBL-BINDING` | 5 |
| `UBL-XSD` | 6 |
| `XR-BR` | 8 |

The oracle itself is not part of the build. Running it needs a download of the official
validator and of its configuration, which a build must not do; what the build keeps is
the answer, below and in [`ledger.json`](ledger.json), and a test that re-runs the engine
over the mutation set and pins every rule identifier it is expected to report.

## The figures

| Both runs agree on | |
|---|---|
| The rule identifiers reported | 149 of 149 |
| Those identifiers with the flag of the artefact | 149 of 149 |
| The node each finding names | 149 of 149 |
| The location expression, character for character | 94 of 149 |
| The verdict | 146 of 149 |

A schema failure is compared as a yes or a no rather than by identifier: the two report
one with different codes and different wording, and neither pretends to report the
other's.

Two things are worth saying plainly about the two rows that are not complete.

## The location expression: the same node, written two ways

At least one location expression differs in form on 55 documents. Reduced to
the node the expression names, the two runs agree on every finding of every document, which
is the row above that reads 149 of 149.

The cause is that the two runs execute two compilations of the same EN 16931 rules of
release 1.3.16. This pack carries the compilation CEN published; the validator
configuration ships one of its own, produced by a different Schematron compiler. One
writes a location as `/Q{namespace}name[1]`, the other as
`/*:name[namespace-uri()='namespace'][1]`. The rules, the identifiers and the flags are
the same, and only findings of the EN 16931 components are affected — the XRechnung
artefact is the same file in both runs, and its locations are identical character for
character.

## The verdict: a profile the official validator has no scenario for

The verdicts differ on 3 documents — `reject` there, `INDETERMINATE` here — all of one kind:

- `profile-unknown-cius-cii`
- `profile-unknown-cius-ubl`
- `profile-unknown-entirely-ubl`

Each of them names a core invoice usage specification that no scenario of the official
configuration matches. That validator picks its artefacts with a table of scenarios, each
matching one exact customization identifier, and rejects a document that matches none of
them without running anything over it. It is a rejection for want of a scenario rather than
a finding about the invoice.

The syntax engine picks per artefact instead. A document whose profile no rule set of the
pack recognizes is still checked against the schema of its syntax, and against the
EN 16931 rules where it says it follows EN 16931 — and then this engine reaches no verdict on
it: the answer is `INDETERMINATE`, the cause is `no-rules-for-profile`, the report names the
rule sets that were left out, and the command leaves with exit code 9. So neither run calls
such a document valid. They decline differently: the official one before looking, this one
after checking everything that applies and saying what it could not reach. It says it to a
program too, and not only to a reader: the JSON report carries the `reasons` array beside
`syntax.profileRulesSkipped` and the token `profile-rules` in `notChecked`. Neither behaviour
is a defect of the other. They are two answers to a question the standard does not settle,
and this is the one place the ledger records a difference of intent rather than of accident.

## The levels a profile gives a rule

A compiled artefact flags each of its rules, and that flag is what the artefact says about
the rule in general. A core invoice usage specification may say something else about a rule
for documents of its own profile, and it is entitled to: it decides what makes a document
of its profile unacceptable. The XRechnung specification does this in both directions —
`BR-CL-13` down for the CVD profile, whose item classification scheme the EN 16931 code
list does not carry, and `UBL-CR-646` up for the standard profile, where it wants a
document refused rather than noted. The official validator applies those levels when it
decides whether to accept a document, and reports the artefact's own flag beside each
finding.

So a finding here carries both. `flag` is the artefact's, untouched, and is what the rows
above compare. `severity` is the level the profile asks for, and is what the verdict is
made on. The levels are data of the pack, transcribed from the configuration release and
recorded in [`packs/SOURCES.md`](../../packs/SOURCES.md); nothing in the code levels
anything.

Of the 149 documents, 19 carry a finding whose two levels differ, and on
7 of those the verdict would be the other one without the tables. That is not
a detail. Without them this engine would refuse every CVD invoice, and four instances of the
conformance corpus would be invalid here and acceptable to the validator their publisher
ships.

| Document | Rule | Verdict | Verdict on the flags alone |
|---|---|---|---|
| `business-cases/extension/04.01a-INVOICE_ubl.xml` | `UBL-CR-646` warning -> information | valid | valid |
| `business-cases/extension/04.02a-INVOICE_ubl.xml` | `UBL-CR-646` warning -> information | valid | valid |
| `business-cases/extension/04.03a-INVOICE_ubl.xml` | `UBL-CR-646` warning -> information | valid | valid |
| `business-cases/extension/04.04a-INVOICE_ubl.xml` | `UBL-CR-646` warning -> information | valid | valid |
| `business-cases/extension/04.05a-INVOICE_uncefact.xml` | `BR-CL-10` fatal -> information, `BR-CL-21` fatal -> information | valid | invalid |
| `business-cases/extension/05.01a-INVOICE_ubl.xml` | `BR-CO-16` fatal -> information, `UBL-CR-470` warning -> information | valid | invalid |
| `business-cases/standard/03.03a-INVOICE_uncefact.xml` | `CII-SR-475` warning -> information | valid | valid |
| `business-cases/standard/03.07a-INVOICE_uncefact.xml` | `CII-SR-475` warning -> information, `CII-SR-476` warning -> information | valid | valid |
| `en-br-co-10-cii-comprehensive` | `CII-SR-475` warning -> information, `CII-SR-476` warning -> information | invalid | invalid |
| `en-br-co-16-ubl-extension` | `BR-CO-16` fatal -> information, `UBL-CR-646` warning -> information | invalid | invalid |
| `en-cl-endpoint-scheme-ubl-extension` | `BR-CL-25` fatal -> information, `UBL-CR-646` warning -> information | valid | invalid |
| `en-cl-item-classification-ubl-cvd` | `BR-CL-13` fatal -> information | invalid | invalid |
| `en-cl-unit-code-cii` | `BR-CL-23` fatal -> warning | valid | invalid |
| `en-cl-unit-code-ubl` | `BR-CL-23` fatal -> warning | valid | invalid |
| `technical-cases/cius/01.01_comprehensive_test_uncefact.xml` | `CII-SR-475` warning -> information, `CII-SR-476` warning -> information | valid | valid |
| `technical-cases/cvd/02.01a-cvd_INVOICE_ubl.xml` | `BR-CL-13` fatal -> information | valid | invalid |
| `technical-cases/cvd/02.01a-cvd_INVOICE_uncefact.xml` | `BR-CL-13` fatal -> information | valid | invalid |
| `ubl-binding-copy-indicator-extension` | `UBL-CR-646` warning -> information | valid | valid |
| `xr-br-seller-contact-cii-extension` | `BR-CL-10` fatal -> information, `BR-CL-21` fatal -> information | invalid | invalid |

## How the ledger was taken down

The official validator and its configuration were downloaded from their release pages and
run over the corpus instances and over the mutated documents, producing one report per
document in the validator's own report format. The engine of this repository was run over
the same bytes. For every document the two answers were reduced to the rule identifiers,
the flags and the locations, and to the verdict — accepted or rejected there, valid, invalid
or indeterminate here — and compared. [`ledger.json`](ledger.json) holds the outcome per document
beside the answer the engine gives for every instance of the corpus, which the build checks
on every run.
