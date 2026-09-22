# The rules this pack does not carry, and why

*Part of [EN16931 Semantic JSON](../../README.md). [`coverage.md`](coverage.md) is the whole
table; this page is the eight rows of it that are not implemented, with the reason for each,
and below them the rules that are implemented and that an XML input cannot reach.*

A pack that quietly left a rule out would claim more than it checks, so every rule of the
official validation artefacts that this pack does not carry is named here. There are three
reasons, and they are not the same kind of thing.

## Not applicable: the rule is about the XML and there is nothing here to ask

| Rule | Why |
|---|---|
| `BR-CL-03` | The rule asks whether the currency attribute of an amount is an ISO 4217 code. An amount of the semantic model carries no currency at all (EN 16931-1, 6.5.2): the invoice states one currency in the invoice currency code (BT-5), and the attribute is the XML binding repeating it on every amount it writes. Nothing of that attribute survives the import into an ESJ document, and nothing of it belongs in one. `BR-CL-04`, which asks the same question of BT-5 itself, is implemented. |

This is the only rule of the release that is invisible here. It is worth saying why there is
only one: the artefacts have a large family of rules about the binding — the `UBL-CR-*` and
`CII-SR-*` families and the syntax Schematron — and none of those are in this table, because they
are not rules of EN 16931. They belong to the syntax engine, which runs the released artefacts as
data, and [`docs/validation.md`](../../docs/validation.md) says how the two engines divide the
work. The model rules of EN 16931 are statements about business terms, and a statement about
business terms is what this engine was built to decide.

## Not decidable: the statement is not a question an engine can answer

| Rule | Why |
|---|---|
| `BR-CO-05` | The statement is that a document level allowance reason code (BT-98) and a document level allowance reason (BT-97) indicate the same kind of allowance. One is a code and the other is free text in whatever language the seller writes; whether the two mean the same thing is not a question a rule engine answers, and the official artefacts assert nothing for it either. |
| `BR-CO-06` | As `BR-CO-05`, for a document level charge (BT-104 and BT-105). |
| `BR-CO-07` | As `BR-CO-05`, for an invoice line allowance (BT-139 and BT-140). |
| `BR-CO-08` | As `BR-CO-05`, for an invoice line charge (BT-144 and BT-145). |

These four are deferred rather than abandoned. A pack that carried a rule which always passes
would report agreement it had not earned; if a later edition of the standard makes the statement
decidable, or a profile pins the reason texts to a list, the rule belongs here.

## Deferred: the rule is decidable and something it needs is missing

| Rule | What is missing |
|---|---|
| `BR-B-01` | The rule is about the split payment VAT category of one member state. The norm text of EN 16931-1:2017+A1:2019/AC:2020 states no such rule, and the VAT category code it asks about is not in the UNTDID 5305 subset this standard admits; the pack of a later edition is where it belongs. |
| `BR-B-02` | As `BR-B-01`. |

### The value added tax point date code, which was deferred and is not any more

One further row said that BT-8 reached this engine from a cross industry invoice in UNTDID 2475,
the list that syntax binds the term to, rather than in the restriction of UNTDID 2005 the
standard names, so that a membership test would fault an invoice the artefacts accept. The
binding tables translate between the two lists since this release, and `BR-CL-06` decides the
code of the standard.

### The seven that were deferred for a code list and are not any more

Seven rows of an earlier version of this page said that a rule was decidable and waited for a
snapshot of the list it asks about: `BR-CL-07` (UNTDID 1153), `BR-CL-13` (UNTDID 7143),
`BR-CL-24` (the media types of an attachment) and the four that ask for the ISO/IEC 6523 ICD
list, `BR-CL-10`, `BR-CL-11`, `BR-CL-21` and `BR-CL-26`. The lists have since been taken from
their publishers — the first three from the European Commission's listing of the code lists
this standard uses, the media types from the IANA registry, each recorded in
[`SOURCES.md`](../../rules/en16931/1.3.16/codelists/SOURCES.md) — and the seven rules are in
the pack.

One thing that was said of them then is still true and now costs something. Three instances of
the corpus carry a scheme identifier that is not on the ICD list, and the profile those
documents name levels the official finding down to information rather than accepting the code.
This pack reports on those three documents at the severity its rules declare, and
[`corpus.md`](corpus.md) records what it says about each; what a profile makes of a finding is
a decision of whoever runs the pack.

## Implemented, and out of reach of an XML input

These rules are in the pack and a document built through the writing API decides them. What
they cannot be asked about is an invoice that arrived as XML, because the syntax binding of
this module does not carry the thing the rule is about into the semantic document. The rule is
not weaker for it; the *comparison* with the official artefacts over an XML input is, and
[`ledger.md`](ledger.md) counts these rules accordingly.

| Rule | What the binding does not carry |
|---|---|
| `BR-49`, `BR-51` | Payment instructions state a payment means type code (BT-81). The XSLT path groups the payment means of the source by that code, and a payment means that states none joins no group: the whole business group BG-16 is absent from the document it builds, with BT-81, BT-82, BT-83 and the groups BG-17, BG-18 and BG-19 below it, and the two rules have nothing left to fire on. The streaming reader — the default of `esj validate` — reads the element from the binding table whatever it states, so both rules are decided there and [`ledger.md`](ledger.md) measures `BR-49` as agreeing. `BR-51` is recorded as partly agreeing for a different reason, which that page gives. |
| `BR-CO-14`, `BR-CO-15`, `BR-DEC-13`, `BR-53` | The invoice total VAT amount (BT-110) is the tax total the source states in the currency of the invoice (BT-5), and the total in the accounting currency (BT-111) the one it states in BT-6. Both bindings select by that value, so an invoice that states no currency code — which the schema of either syntax admits — loses both totals. The four rules that compute with them then decide against an operand that is absent only because of the import, and the artefacts, reading the XML, report the missing currency alone. The importer reports the loss; [`ledger.md`](ledger.md) measures it as `nocur-cii` and `nocur-ubl`. |
| `BR-CL-24` | The media type of an attached document (BT-125) is on the list the standard admits. A binary object of this format is its bytes together with its media type *and* its file name (specification, section 6.7), so an attachment that arrives with neither a file name nor an admitted media type is not written at all and the rule sees no value. With the file name present, both engines report the rule. |

The rows are the same shape: a source element that a binding selects by a value in or on it,
and that states no such value, is selected by nothing and reaches nothing. Document level
allowances and charges (BG-20, BG-21) and their line level twins (BG-27, BG-28) are selected by
the indicator that says which of the two an allowance charge is; a CII tax registration is the
VAT identifier BT-31 or the fiscal registration identifier BT-32 by the scheme on it; a CII date
is a date by its format qualifier. None of those costs a rule — the artefacts' contexts carry
the same condition and both engines stay silent — and each costs business terms. **Both readers
write one `UNPLACEABLE` note for every such element**, which is what
[`docs/validation.md`](../../docs/validation.md) means by saying that a syntax element the
binding cannot classify is reported rather than dropped.

Where the semantic model has a place for what arrived, the value is kept instead of being
reported away: an identifier without the scheme its term requires is kept, the model layer
reports `ESJ-L2-COMPONENT-MISSING`, and `BR-62` to `BR-65` are reported under the identifiers
the artefacts use. A CII party global identifier that states no scheme is the party identifier,
which is how the CEN validation artefact reads it, so `BR-CO-26` is decided on the document the
streaming reader builds; [`../readers.md`](../readers.md) records that the XSLT path loses it.
