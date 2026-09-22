# The matrix: both syntaxes through the semantic core

*Part of [EN16931 Semantic JSON](../../README.md).*

[`pairs.md`](../ledger/pairs.md) asks whether the two files of a business case arrive at the
same semantic content when they are read. This page asks the question the writers make
possible: whether one of them, taken through the semantic core and written out as the other
syntax, arrives where the other one started.

That is four measurements per business case, and they are separate questions. A conversion can
produce a document the target syntax refuses, and a conversion the target syntax accepts can
still say something other than what the other file says. Both directions are measured, because
a converter can be right in one and wrong in the other.

| Step | UBL → CII | CII → UBL |
|---|---|---|
| read | the `_ubl` file of the pair | the `_uncefact` file of the pair |
| write | `CiiWriter` | `UblWriter` |
| validate | the artefacts of the pack, over the result | the artefacts of the pack, over the result |
| read back | `StreamingReader` | `StreamingReader` |
| compare with | the `_uncefact` file of the pair, read | the `_ubl` file of the pair, read |

`matrix.json` beside this page carries every verdict as data, and `WriterMatrixTest` in
`esj-bindings` recomputes all four of them for all 40 pairs on every build and fails where a
number here has moved in either direction.

## What was measured

| | |
|---|---|
| Pairs | the 40 business cases the corpus carries in both syntaxes, the ones [`pairs.md`](../ledger/pairs.md) lists |
| Read by | `StreamingReader`, so that the way in is the same on both sides of every comparison |
| Written by | `CiiWriter` and `UblWriter`, from the tables of `model/bindings/` |
| Validated by | `SyntaxValidator` of `esj-syntax` with the pack `xrechnung/3.0.2/2026-08-31` |
| Compared by | semantic path and value, against the other file of the pair as it is read |

## The four verdicts

| Measure | UBL → CII | CII → UBL |
|---|---:|---:|
| `accepted` — the validation artefacts report nothing fatal on the result | 40 | 39 |
| `refused` — at least one fatal finding | 0 | 1 |
| `equal` — the result says what the other file of the pair says | 18 | 18 |
| `differing` — at least one semantic path differs | 22 | 22 |
| `paths` — semantic paths that differ, over all pairs | 78 | 80 |

**Every one of those 158 differing paths is a difference the litmus ledger already records**,
and every one of them is of the kind `fixture`: a place where the two files of the suite do not
say the same thing. Not one is a value a writer mislaid. A converter that lost something would
show it here as a path the ledger does not carry, and `WriterMatrixTest` fails on exactly that.

The two columns of `equal` are the 18 pairs [`pairs.md`](../ledger/pairs.md) records as
identical. Converting a document cannot make two files agree that do not agree, so 18 is the
ceiling this measurement has, and both directions reach it.

## What the target syntax refused

The cross industry invoice accepted every document written for it. The UBL profile refused one
of the 40, for the reason [`ubl-roundtrip.md`](ubl-roundtrip.md) sets out over the whole
corpus — nothing here is new to this page, and nothing here is about the source syntax.

| Rule | Pairs | Cause |
|---|---:|---|
| `cvc-complex-type.2.4.a` | 1 | `term-the-document-does-not-state` |

A refusal is a statement about what the target profile asks for beyond EN 16931-1, not about
what the conversion carried, and the two measurements are reported side by side and never
added together. Where UBL asks for an element the semantic model has no term for, the
`conventions` member of the binding table says what is written there; seven of these pairs
carry the conventional purchase order reference, which is why their round trip still differs
at `/BT-13` — the UBL file of the suite carries a placeholder of its own
(`order-reference-placeholder` below).

## The causes, by size

Every differing path with the cause `pairs.json` gives it. The two columns count the same
business cases from the two ends, which is why they agree except in one place.

| Cause | Kind | UBL → CII | CII → UBL |
|---|---|---:|---:|
| `zero-total` | fixture | 15 | 15 |
| `item-attribute-order` | fixture | 12 | 12 |
| `seller-identifiers` | fixture | 9 | 9 |
| `buyer-name-and-trading-name` | fixture | 8 | 8 |
| `price-base-quantity` | fixture | 8 | 8 |
| `order-reference-placeholder` | fixture | 7 | 7 |
| `payee-legal-registration` | fixture | 6 | 6 |
| `address-city-postcode-swapped` | fixture | 4 | 4 |
| `address-placeholder-wording` | fixture | 2 | 2 |
| `seller-contact-point` | fixture | 2 | 2 |
| `preceding-invoice-references` | fixture | 0 | 2 |
| `buyer-trading-name-only-ubl` | fixture | 1 | 1 |
| `item-description-typo` | fixture | 1 | 1 |
| `payment-means-text` | fixture | 1 | 1 |
| `seller-vat-identifier` | fixture | 1 | 1 |
| `vat-exemption-reason` | fixture | 1 | 1 |

## Where a loss and a difference cancel

`preceding-invoice-references` is the one asymmetric row, and it is the one place on this page
where a number is better than the thing it measures.

`technical-cases/cius/01.01_comprehensive_test` states two preceding invoices in its UBL file
and one in its cross industry invoice, which is the fixture difference the ledger records at
`/BG-3/1/BT-25` and `/BG-3/1/BT-26`. The schema of the cross industry invoice admits one
`ram:InvoiceReferencedDocument`, so writing that document as one drops the second reference —
the loss [`cii-roundtrip.md`](cii-roundtrip.md) records as `GROUP_NOT_REPEATABLE` — and what
comes back agrees with the other file at those two paths for a reason that has nothing to do
with agreement.

The comparison alone cannot see that, so it is not left to the comparison. The writer reports
every such loss as it writes, `matrix.json` records this one under `masked` with the two paths
and the note that caused it, and the test asserts that the recorded set is the set the writers
actually report. A second case of this kind would have to be written down before the build
went green again.

## What this measures, and what it does not

It measures the two writers and the three binding tables against each other over documents
nobody wrote for them. It does not measure the corpus: the fixture differences above are
differences between two hand-written files and are recorded, not repaired.

It is also not a claim that a conversion is lossless in general. The corpus holds no UBL credit
note, and `../creditnote/` says what that gap costs; the matrix runs over 40 business cases and
not over the whole 86-instance corpus, because a pair is what makes the comparison possible at
all; and what each syntax cannot carry from the semantic model is measured per syntax in
[`cii-roundtrip.md`](cii-roundtrip.md) and [`ubl-roundtrip.md`](ubl-roundtrip.md) rather than
here. This page adds one thing to those two: over these 40 business cases, the path through the
semantic core loses nothing that the two files did not already disagree about.

Author: Christian Bürckert.
