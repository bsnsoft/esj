# What the official artefacts say about the corpus

This directory holds 3 things:

| | |
|---|---|
| [`ledger.json`](ledger.json) | the answer of the syntax engine for every instance of the corpus, taken down once and checked on every build, and beside it the record of the comparison against the official validator |
| [`ledger.md`](ledger.md) | that comparison in words: what was run, the agreement figures, and every difference with its cause |
| [`mutations/`](mutations/mutations.json) | the mutation set — instances of the corpus broken on purpose, one change at a time, with the rules each one is expected to make fire |

`ledger.json` is a golden file, and that is its point. For each of the 86 instances it records
the syntax, the profile the document names, the verdict, and every finding the artefacts made,
with the component that made it, its level and the location it names. A finding that appears,
disappears or changes its level is a change in what the official rules say about a document.
That is exactly what a new release of a validation pack is for. The build then stops until
somebody has looked at it and written the new answer down.

## The short version

| | |
|---|---|
| Instances | 86 |
| Valid | 86 |
| Invalid | 0 |
| Findings in total | 48 |
| Of those, fatal | 0 |

No instance fails the XML Schema of its syntax, none fails to parse, and none carries a
finding that makes it invalid for the profile it names.

## Two levels, and why nothing here is fatal

Every finding carries 2 levels, and the difference between them is the whole of the table below.

The **flag** is what the artefact set on the rule: what the rule means in general. The
**severity** is what the core invoice usage specification the document names says a rule of
that name means for a document of its own profile. That specification is the body that decides
what makes a document of its profile unacceptable, and ESJ makes the verdict on the severity.
Where a specification says nothing about a rule, which is almost everywhere, the two are the
same. The levels are data of the pack; `packs/SOURCES.md` records where each was published.

| Code | Flag | Level for the profile | Count | Component |
|---|---|---|---|---|
| `BR-DE-TMP-32` | information | information | 36 | XRechnung Schematron, both syntaxes |
| `UBL-CR-646` | warning | information | 4 | EN 16931 UBL Schematron |
| `BR-CL-10` | fatal | information | 3 | EN 16931 CII Schematron |
| `BR-CL-13` | fatal | information | 2 | EN 16931 CII and UBL Schematron |
| `BR-CL-21` | fatal | information | 1 | EN 16931 CII Schematron |
| `BR-CO-16` | fatal | information | 1 | EN 16931 UBL Schematron |
| `UBL-CR-470` | warning | information | 1 | EN 16931 UBL Schematron |

`BR-DE-TMP-32` is by far the largest group and reports no error. It is the temporary rule that
asks for a delivery date or an invoicing period, levelled as information by the artefact that
carries it. The 2 binding rules note where a document uses a part of its syntax that the
EN 16931 binding does not map. Under release 1.3.16 of the EN 16931 artefacts, 2 more stood
here, `CII-SR-475` and `CII-SR-476`, on 3 CII instances whose supporting documents each carry a
name or an attachment. That release counted those across all supporting documents of an
invoice. Release 1.3.17 counts them within each one, and neither rule fires.

The 7 findings whose flag is fatal are the interesting ones, and they are why the tables exist.
6 of the 7 are the same kind of thing. A profile introduces an identifier scheme of its own:
`XR01` and `XR03` for parties and items in the extension profile, `CVD` for item classification
in the CVD profile. The EN 16931 rule that checks scheme identifiers against a published code
list does not know it. The profile that introduced the scheme levels the rule down for its own
documents, the only sensible thing it could do. The seventh is `BR-CO-16` in an extension
instance whose amount due for payment does not follow from its total. The extension profile
levels that rule down too.

Without those levels this engine would refuse every CVD invoice. 4 instances of this corpus
would then be invalid here and acceptable to the validator their publisher ships.
[`ledger.md`](ledger.md) has the figures.

None of this depends on which compilation of the CEN rules the pack carries. The comparison in
[`ledger.md`](ledger.md) ran the official validator over the same documents. It reported the
same identifiers with the same flags on every one of them.

## What it costs

Measured once, on a laptop, with the artefacts of this pack. The build asserts that a document
of a megabyte is answered within the default time of a run, and asserts nothing about these
numbers.

| | UBL | CII |
|---|---|---|
| Compiling the schema modules | 60 ms | 60 ms |
| Compiling the EN 16931 rule set | 160–470 ms | 420 ms |
| Compiling the specification's rule set | 40–80 ms | 70 ms |
| An invoice of the corpus, all three | under 30 ms | under 70 ms |
| A UBL invoice of 1.0 MB with 940 lines | 130 ms | — |

A process pays the compilation once and then shares it, so its second document costs the bottom
2 rows and nothing above them. A command line that starts a process per invoice pays the whole
table every time. The report keeps that difference apart: `ComponentRun` reports the run and the
compilation separately, and the compilation is zero once the artefact is in hand.

## The order

The findings of an instance are in the order the engine reports them: by engine, then
category, then rule identifier, then location. 2 runs over the same bytes with the same pack
produce the same list, which is what makes a golden file possible at all.
