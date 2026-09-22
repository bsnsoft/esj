# The official artefacts as the oracle of the rule pack

*Part of [EN16931 Semantic JSON](../../README.md). [`coverage.md`](coverage.md) is which rules
the pack carries, [`corpus.md`](corpus.md) is what it says about documents that are known to be
good, and this page is what happens when the same documents are broken on purpose and put
through the pack and through the official validation artefacts side by side.*

The pack is written over business terms — `/BG-22/BT-106` and not
`cac:LegalMonetaryTotal/cbc:LineExtensionAmount` — so whether it decides an invoice the way the
artefacts of CEN/TC 434 decide it is measured here: the same bytes through both engines, rule
identifier against rule identifier, with every difference named.

## What was run

| | |
|---|---|
| Under test | the EN 16931 rule pack `en16931/1.3.16` of this repository, over the document after import |
| Oracle | the EN 16931 Schematron of release 1.3.16, run in process by the syntax engine on the same bytes |
| Input | 448 mutations of the conformance corpus |
| Compared | the identifiers of category `EN-BR`, `EN-DEC` and `EN-CL` that each engine reports, and which of the pack's decide no verdict |

Both engines are on the class path of `esj-cli`, so `OracleTest` makes the comparison in the
ordinary build. Nothing is downloaded and nothing is installed: the artefacts are the ones the
syntax engine already carries as data.

The mutations are data rather than files:
[`mutations/mutations.json`](mutations/mutations.json) names an instance of the corpus, a
location in it, the changes to make there, and what each engine reported when this ledger was
taken down. No broken invoice is checked in;
[`mutate.py`](mutate.py) writes any of them out for a reader who wants to look at one:

```text
python3 conformance/rules/mutate.py --only br-co-10-ubl --print
python3 conformance/rules/mutate.py --out /tmp/mutations
```

Where the corpus carries no invoice of a VAT category — the intra-community supply, the export
outside the Union, the IGIC of the Canary Islands, the IPSI of Ceuta and Melilla — the mutation
first rewrites one into that category and only then breaks the rule. Each rewritten document is
one both engines accept, or the mutation would measure the rewrite rather than the rule.

## The figures

| Rules of the pack | |
|---|---|
| Implemented | 217 |
| Where both engines report the same identifiers on every shape measured | **166** |
| Where they agree on one shape of the defect and not on another, named below | **33** |
| Where they differ, with the difference explained below | **16** |
| Where the oracle cannot be asked at all | **2** |
| Open — a difference nobody has accounted for | **0** |

| Mutations | |
|---|---|
| Aimed at a rule, both engines reporting the same set | 359 |
| Aimed at a rule, the two sets differing | 76 |
| Near misses, both engines silent | 13 |

The third row is the one to read slowly. Agreement measured on one shape of a defect is not
agreement on the rule, so a rule with an agreeing mutation and a differing one is counted as
neither and the shape that differs is named. A rule's status is taken from the mutations aimed
at it; an identifier a mutation reports beside the one it is aimed at — `BR-AF-09`, where the
CII template of `BR-AF-08` takes the element away from it — is named in the prose of that
difference and does not move the status of `BR-AF-09`. Of the 214 rules that were exercised,
most were exercised in both syntaxes and the rest in the one syntax whose schema admits the
broken document: the Schematron of a document the schema rejects is never run, by the artefacts
or by anybody.

### The near misses

Thirteen mutations are there to fail: a value at the edge of what a rule allows, where both
engines must say nothing. It is the half of the question a set of broken documents cannot ask.

| Mutation | What is at the edge |
|---|---|
| `near-tolerance-above-ubl`, `near-tolerance-above-cii` | a category VAT amount 0.99 above the rate applied to the taxable amount, inside the tolerance of one that the artefacts allow |
| `near-tolerance-below-ubl` | the same 0.99 below |
| `near-percentage-scale-ubl`, `near-percentage-scale-cii` | a VAT rate written `19.00` and `19.0000`: a percentage has no decimal limit, and a decimal rule may not fire on one |
| `near-amount-two-decimals-ubl` | an amount written with exactly the two fraction digits the decimal rules allow |
| `near-zero-rate-written-out-ubl` | a zero rate written `0.00`, which is zero and has to compare as zero |
| `near-country-northern-ireland-ubl` | the country code `XI`, which is on the list this standard uses and not in ISO 3166-1 itself |
| `near-unit-from-recommendation-21-ubl` | a unit of measure from Recommendation 21 where Recommendation 20 is the usual list |
| `near-corrected-invoice-type-ubl` | the invoice type code `384`, a corrected invoice |
| `near-seller-identified-by-a-global-identifier-cii` | a seller identified by a global identifier that states no identification scheme, which is the whole of what `BR-CO-26` asks for |
| `near-tax-registration-without-scheme-cii`, `near-tax-registration-without-scheme-ubl` | a seller tax registration the binding of its syntax places nowhere, on an invoice that identifies its seller otherwise |

## The differences, and why each one is there

A difference is not automatically a defect, and it is never left as a shrug. Each of the 16
rules that differ outright, and each of the 33 that differ on one shape of their defect, is one
of nine causes; every cause is a property of the two engines rather than of one invoice, and
the mutation that shows it is kept in the set with the cause written on it so that the day it
changes, the build says so.

### An operand the rule needs was removed (eleven rules)

`BR-05`, `BR-12`, `BR-13`, `BR-14`, `BR-15`, `BR-26`, `BR-45`, `BR-46`, `BR-48`, `BR-53`,
`BR-CO-18`.

These rules are all of the form "an invoice shall have *X*", and the only way to break one is to
take *X* away. *X* is also what some other rule adds up. The artefacts' expression then compares
a sum against a node that is not there, which in XPath is a comparison that fails, and the
report carries the arithmetic rule beside the missing one: remove the sum of invoice line net
amounts (BT-106) and the artefacts report `BR-12`, `BR-CO-10` and `BR-CO-13`. This engine
reports `BR-12` and stops: a rule whose operand the document does not carry is not decided here,
and `SPEC.md` section 9.5 says what an undecided rule reports.

Neither reading is wrong and the difference is not silent in either direction — `BR-05` and
`BR-CO-18` are the cases where this engine reports the arithmetic rule and the artefacts do not.
What the two engines agree on, every time, is the rule the mutation was aimed at. A caller who
needs the arithmetic decided on an incomplete invoice has the missing term in the report and can
decide for itself.

A second shape inside this one reports *nothing at all* rather than reporting less. A rule whose
context is a business group — `BR-09` of the seller postal address, `BR-11` of the buyer's,
`BR-20` of an invoicing period — has no instance to be evaluated at when that group is absent,
so it produces neither a finding nor a remark: remove the seller postal address and this engine
reports `BR-08` alone where the artefacts report `BR-08` and `BR-09`. The UBL artefact loses
`BR-09` the same way, so the two artefacts do not agree with each other here either. 117 rules
carry a context deeper than the document root; these three are the ones whose context another
rule declares mandatory, which is what makes them the ones a report can lose unnoticed.

### The artefacts are chosen by the very term the rule is about (`BR-01`)

`BR-01` asks that an invoice state a specification identifier (BT-24). That identifier is how
the syntax engine — and the official validator, and every other implementation — decides which
rule sets to run. An invoice without it has no artefacts to be measured against, so the official
side of this one comparison is empty by construction. The semantic engine has no such problem:
it reads a document that names its model and reports the missing term.

### The release does not carry the rule (`BR-CO-25`)

`BR-CO-25` — an invoice with an amount due for payment states a payment due date or payment
terms — is stated in clause 6.4.2 of the standard and carries no identifier in release 1.3.16.
The pack implements it, `coverage.md` says so, and the artefacts have nothing to say. That is
the one difference where the two engines are not looking at the same list of rules at all.

### The artefact's expression cannot fire (`BR-DEC-13`, `BR-AF-08`, `BR-AG-08`)

`BR-DEC-13` restricts the invoice total VAT amount (BT-110) to two fraction digits. Give that
amount three, and this engine reports it; neither artefact does.

The UBL expression compares the currency attribute of the amount with a `DocumentCurrencyCode`
it looks for *below the amount*, where there is never one; the comparison selects nothing, the
negation of nothing is true, and the rule passes on every document ever written. The CII
expression is satisfied by any one of the amounts it selects, and an invoice that states a VAT
accounting currency has a second amount which satisfies it. Either way it reports nothing.

The ground rule of this project is to follow the artefacts where the norm admits their reading,
and the norm where they contradict it. A rule that cannot fire is not a reading of anything, so
this pack follows Table 26 of clause 6.5 and reports: in this one respect it is stricter than
the ecosystem.

*Give that amount three fraction digits and this engine reports it* holds only for a third
digit that is not a zero; written `1.230` the amount is not over-scale by the time a rule sees
it, for the reason the next section gives, which is why the set carries no trailing-zero
variant.

`BR-AF-08` and `BR-AG-08` are the same shape in the CII artefact. Their assertion reads the rate
and the taxable amount from the parent of the element the rule is written over, where neither is
written, so `every … satisfies` quantifies over an empty sequence and holds on every document.
The template also takes that element away from the lower-priority rules of the same pattern, so
`BR-AF-09`, `BR-AG-09` and `BR-CO-17` go silent on it as well — an identifier each of those
rules keeps its own status through, by the convention above. `br-af-08-cii`, `br-ag-08-cii`,
`af08-tol-cii`, `ag08-tol-cii` and `af08-one-unit-cii` measure it: this pack reports what the
invoice breaks and the CII artefact reports nothing.

### A notation the import does not carry (19 rules of the decimal family)

This is the reverse of the section above, and it is the reason the figures table has a row for
rules that agree on one shape of a defect and not on another.

An amount of EN 16931 has at most two fraction digits, and the `BR-DEC-*` family is the family
that says so. The canonical decimal form of this project writes a number without trailing zeros
(`SPEC.md` section 6.4), so an amount the source spelled `319.860` is `319.86` in the semantic
document: the same number, one notation shorter. The artefacts read the XML and see three
fraction digits; this engine reads the document and sees two, and there is nothing left for
`decimals(2)` to count. A third digit that is *not* a zero survives the import, and there the
two engines agree — which is what the twenty-one mutations of the original set measured, all of
them with the one value `1.234`.

So each of those rules now has a second mutation with the value `1.230`, and each is recorded
as a difference with the cause `notation-lost-on-import`. The nineteen are `BR-DEC-01`, `-02`,
`-05`, `-06`, `-09`, `-10`, `-11`, `-12`, `-14`, `-16`, `-17`, `-18`, `-19`, `-20`, `-23`,
`-24`, `-25`, `-27` and `-28`. Four more rules are partly agreeing for reasons of their own and
are counted in the same row of the figures: `BR-51`, `BR-CL-24`, `BR-S-01` and `BR-CL-01`, each
below.

Two members of the family are not in that list and the reason is instructive: `BR-DEC-13`
cannot fire at all, and `BR-DEC-15` is written arithmetically rather than lexically in the
artefacts — it compares the amount with itself rounded to two decimals, which `1.230` passes —
so on a trailing zero the artefacts are silent there too and the two engines agree.

The loss is not silent. The importer writes a warning at the path of every amount whose
fraction digits it could not carry, and a warning is on the error stream of every command that
imports without being asked for, so `esj convert` says what it did and a caller reading the
report can see the defect the rule engine no longer can.

### The two artefacts number the same defect differently (`BR-O-01`, `BR-O-11`)

The rules of the *not subject to VAT* category are bound to different elements in the two
syntaxes, and the two artefacts consequently report different identifiers for one and the same
invoice: an invoice whose only breakdown is not the category of its lines is `BR-O-01` to the
UBL artefact and silence to the CII one, and a second breakdown beside a `O` one is `BR-O-11`
and `BR-O-12` to the CII artefact and `BR-O-11` alone to this engine. A rule of this pack is a
statement about business terms and has one identifier; it cannot be two things at once, and
which of the two artefacts it agrees with is decided by the term rather than by the syntax the
document happened to arrive in.

`BR-S-01` is a third case of the same kind and the measurement is worth recording, because it
is not a gap in release 1.3.16: both vendored artefacts carry the rule. Take the whole header
tax breakdown out of a CII invoice and the artefacts report `BR-CO-14`, `BR-CO-18` and `BR-E-01`
while this pack reports those three and `BR-S-01`; make the same removal in a UBL invoice and
both engines report the rule. The CII expression has no context left to run in once the
breakdown is absent, and the UBL one has. The pack follows the norm and the UBL artefact, which
is the way round the ground rule of this project asks for, and the CII mutation is in the set
so that the day the artefact changes, the build says so.

The same split shows up once more, outside the differences, and it is recorded here because a
reader will meet it in a report: `BR-CL-17` and `BR-CL-18` divide the VAT category codes
between them by the *element* each syntax writes, and the two syntaxes divide them differently.
The category of a document level allowance and of a document level charge is `BR-CL-17` in both,
and the category of an invoiced item is `BR-CL-18` in both, so the pack follows them there. The
category of a VAT breakdown is `BR-CL-17` to the UBL artefact and `BR-CL-18` to the CII one; the
pack follows the CII reading, and a report compared line for line with the UBL artefact will
differ on that identifier alone.

### A business term the reader did not carry (two rules)

`BR-62` to `BR-65` stood here until this round. They ask that an electronic address, an item
standard identifier and an item classification identifier state the scheme they were issued
under, and the reader used to drop an identifier that arrived without one, so the rule had no
value to fire on while the artefacts, reading the XML, reported it. Both readers now keep the
identifier and report the component as missing, the model layer reports
`ESJ-L2-COMPONENT-MISSING` at its path, the rule fires, and the eight mutations agree.

An attachment without a file name is the first of the two that are left: a binary object of
this format is its bytes
together with its media type and its file name, so nothing is written for the element and
`BR-CL-24` sees no value where the artefacts see a media type off the list.

The invoice currency code is the third and it runs the other way. Both bindings select the
invoice total VAT amount (BT-110) by the currency the amount is stated in, against the currency
the invoice names (BT-5), and the total in the accounting currency (BT-111) the same way against
BT-6. An invoice that states no currency code at all is valid against the schema of either
syntax, and there the selection matches nothing: the artefacts, reading the XML, report `BR-05`
for the missing currency and nothing else, while this engine reports `BR-05` and then `BR-CO-15`,
because the total with VAT no longer equals the total without VAT plus a VAT amount that is not
there. `nocur-cii` and `nocur-ubl` measure it in both syntaxes. The finding is about the import
and not about the invoice, which is why it is a difference here rather than a rule to be
corrected: the invoice is wrong, and it is wrong for the reason the artefacts give.

Both are limitations of a reader and not of the rules: `esj-rules` has a case for each
that builds the document directly and shows the rule firing and holding.
[`not-applicable.md`](not-applicable.md) names them with the element that was lost, and the
importer writes a note for every source element the binding could not classify, so the loss is
in the report rather than in nobody's hands.

### The artefact reads the norm more narrowly than the norm reads itself (`BR-51`)

`BR-51` is about the payment card account number (BT-87). The standard says which digits of a
card number may be shown — the first six and the last four — and the artefacts ask instead that
the whole value be at most ten characters long. A number masked to `************6789` shows four
digits and satisfies the standard; it is sixteen characters and fails the artefacts. Measured
on the comprehensive instance of both syntaxes: the artefacts report `BR-51` and this pack does
not. A number of sixteen digits fails both readings, which is the shape the original mutation
had and the shape both engines agree on.

This pack follows the standard, so `BR-51` is recorded as agreeing on one shape and differing on the other.

### The two artefacts do not ask for the same closeness (the `*-08` family, `BR-CO-17`)

The `*-08` rule of each VAT category asks that the taxable amount of a breakdown equal the
invoice line net amounts, plus the document level charges, minus the document level allowances
of that category — and, for the categories that carry a rate, of that rate. `BR-CO-17` asks that
the VAT category tax amount be the taxable amount at the category rate. Release 1.3.16 does not
ask either question the same way in its two artefacts, and which of the two is the lenient one
changes with the rule. Read out of the artefacts by `ToleranceMatrixTest` and recorded in
[`ledger.json`](ledger.json) under `tolerances`:

| Rule | UBL artefact | CII artefact | This pack faults | and warns, naming |
|---|---|---|---|---|
| `BR-S-08` | within one unit | equality | a unit or more | below a unit, CII |
| `BR-AF-08`, `BR-AG-08` | within one unit | equality, and cannot fire | a unit or more | below a unit, the standard |
| `BR-Z-08`, `BR-E-08`, `BR-AE-08`, `BR-IC-08`, `BR-G-08` | equality | within one unit | a unit or more | below a unit, UBL |
| `BR-O-08` | equality | equality | any difference | — |
| `BR-CO-17` | within one unit, boundary excluded | within one unit, boundary included | more than a unit | exactly a unit, UBL |

A difference both artefacts fault is a fatal finding; a difference inside the zone only one of
them grants is a warning, which decides no verdict and names the syntax whose artefact faults
the figure there, so on the eight rows whose strict artefact can fire a document keeps the
verdict that artefact gives it. On `BR-AF-08` and `BR-AG-08` nothing faults a figure there,
because the CII assertion cannot report, and the warning names the standard.
Nine of the ten rows disagree; each of those rules is recorded as agreeing on one shape and
differing on another, with the cause `artefacts-disagree-on-a-tolerance` — and for `BR-AF-08`
and `BR-AG-08` `artefact-cannot-fire`.

The set carries 49 mutations aimed at these ten rules, measuring the family at four points.
Inside the zone, half a unit off the sum, in both syntaxes: `s08-tol-{cii,ubl}` and the same
pair for `z08`, `e08`, `ae08`, `ic08`, `g08`, `af08` and `ag08`. At the edge, exactly one unit
off, which both artefacts fault: `s08-one-unit-{cii,ubl}`, `z08-one-unit-{cii,ubl}`,
`af08-one-unit-{cii,ubl}`. Beyond it, 999 off: the `br-*-08-{cii,ubl}` and `br-co-17-{cii,ubl}`
mutations of the coverage set. `o08-tol-{cii,ubl}` is the row where the artefacts agree, half a
unit off and both engines faulting. For `BR-CO-17` the edge is the unit itself:
`co17-one-unit-{above,below}-{ubl,cii}` state a VAT category tax amount exactly a unit either
side of the rate applied to the taxable amount, where this pack warns, the UBL artefact faults
and the CII artefact is silent; `near-tolerance-above-ubl` and its two fellows are 0.99 out,
which nothing reports.

One shape is left that no mutation crosses. The UBL artefact puts the comparison behind a guard
and faults a breakdown at a rate nothing else states whatever its amount, so a breakdown whose
taxable amount is *zero* at such a rate holds for the standard, for the CII artefact and here,
and fails there. `s08-guard-ubl` states such a breakdown with an amount that is not zero, where
both engines report.

## Two boundaries of agreement that no mutation of the set crosses

Both belong here rather than in a footnote, because both are places where the word *agree*
above is narrower than it sounds.

**`BR-CL-01` is measured for a code the standard does not admit at all.** The rule asks that the
invoice type code (BT-3) be one of the UNTDID 1001 subset the standard names, and the mutation
that measures it puts a code outside that subset in. Put `381` there instead — a credit note
type code, which *is* in the subset — in a UBL `Invoice` document, and the artefacts report
`BR-CL-01` while this pack is silent; that mutation is in the set with the cause
`category-bound-differently`. Both are right about what they are asking: the pack reads
a semantic document, where the code is on the list, and the artefact is enforcing which of the
two UBL root elements a document with that code belongs in, which is a restriction of the
syntax binding carried under an EN identifier. The agreement recorded for `BR-CL-01` is
agreement about membership of the list and not about the root element.

**The currency list this pack decides against is not the one the artefacts carry.** `BR-CL-04`
asks whether the invoice currency code (BT-5) is an ISO 4217 code, and the snapshot here is
ISO's own list through its maintenance agency, while the artefacts carry a listing written into
their own code list Schematron. Where that listing comes from is not recorded here, and it is
not the European Commission's workbook for this standard either — that sheet carries `STN`
where the artefacts carry `STD`. What is measured is the two lists themselves: on 2026-09-20 both carry 178 codes and the 178 are
not the same — the artefacts accept `CNH` and `STD`, the snapshot `STN` and `XAD`. An invoice in
one of those four codes is judged differently by the two engines; no mutation of the set uses
one, because a mutation whose outcome depends on the day a list was taken measures the list.
[`SOURCES.md`](../../rules/en16931/1.3.16/codelists/SOURCES.md) records where each list came
from and why.

## The two rules the oracle cannot be asked about

| Rule | Why |
|---|---|
| `BR-CO-19`, `BR-CO-20` | These ask that an invoicing period state a start date or an end date. A business group of an ESJ document exists exactly when it carries a value, so a period with neither date is not an empty period but no period at all; [`coverage.md`](coverage.md) records that the two rules keep their place in the pack for documents that came from XML and cannot fail here. |

`BR-62` to `BR-65` and `BR-49` are measured in both syntaxes and the two engines agree on all
five, which is what the reader of this measurement changed.

## The corpus

The other half of the measurement is the documents nobody broke. All 86 instances of the
conformance corpus were run through both engines:

| | |
|---|---|
| Instances | 86 |
| Both engines silent | 82 |
| Both engines reporting, with the same identifiers | 4 |
| Either engine reporting something the other does not | 0 |

The four are `BR-CO-16` on one instance and the three scheme identifier rules `BR-CL-10`,
`BR-CL-13` and `BR-CL-21` on three others; [`corpus.md`](corpus.md) has the detail and
[`../syntax/ledger.json`](../syntax/ledger.json) records what the artefacts said about the same
instances. In every case the artefacts raise the same rule with the flag `fatal` and the profile
of the document levels it down to information, which is why the official validator accepts the
document and why this pack reports it at the severity its rule declares.

## What it costs

The measurement of the previous stage was that the rule engine is linear in the number of
invoice lines. Here is what that means beside the artefacts, on documents grown by
[`../scale/generate.py`](../scale/README.md) from one corpus invoice. The rule engine is timed
over the semantic document; the artefacts are timed over the XML, which is what they read.

| Invoice lines | Bytes of XML | EN 16931 rule pack | EN 16931 Schematron |
|---|---|---|---|
| 1 000 | 1.5 MB | 36 ms at `-Xmx1g` | 2.2 s at `-Xmx1g` |
| 10 000 | 15 MB | 0.36 s at `-Xmx1g` | 5.8 s at `-Xmx1g` |
| 300 000 | 450 MB | 10.4 s at `-Xmx4g`, 10.8 s at `-Xmx2g` | **exceeded** |

*Exceeded* is exact rather than polite: at one gibibyte the run ends in nineteen milliseconds,
because 450 megabytes of XML do not fit in the heap before anything has been parsed. The ten
minutes the measurement allows are never reached. The rule pack needs two gibibytes for the same
document — the semantic document is 2.4 million values — and finishes in eleven seconds, and it
is no faster with four, which is what a run that is not fighting the collector looks like.

The two engines read different things, and the artefacts do work this pack does not over a
syntax this pack never sees. What the table is for is the sentence a deployment needs: a
document of this class can be checked against the business rules of the standard on a machine
that cannot run the released artefacts over it at all.

## What may now be said, and what may not

The claim this ledger earns is this one, with the figures beside it: *the EN 16931 rule pack of
this repository was measured against the official validation artefacts of release 1.3.16 over
the conformance corpus and 448 mutations of it; of the 217 rules it implements, the two engines
report the same identifiers on every shape measured for 166, agree on one shape of the defect
and differ on another for 33, differ outright for 16, and cannot be compared at all for 2 —
each of the 51 named above with the reason for it.*

The figure that may not be quoted alone is the first one. A rule counted as agreeing has been
measured on the shapes the mutation set carries and on no others; the day a new shape is
measured the table grows rather than the sentence changing.

What may not be said is that this pack implements the official rule sets, or that it may be used
in place of them. The artefacts and the core invoice usage specifications are the authority on
the business rules of EN 16931; `SPEC.md` section 9.4 keeps the findings of this engine a layer
of their own, and `esj validate` runs the released artefacts on every XML input whatever this
pack says.
