# The conformance corpus and the litmus test

*Part of [EN16931 Semantic JSON](../README.md).*

The litmus test: import the UBL file and the CII file of one business case and compare their
semantic digests (`SPEC.md`, section 8.2). A pair that agrees agrees exactly; where a pair
disagrees, the disagreement is a set of semantic paths, each with the value one side has and
the other has not.

## The corpus

`conformance/` holds the 86 invoice instances of the KoSIT XRechnung test suite
([`itplr-kosit/xrechnung-testsuite`](https://github.com/itplr-kosit/xrechnung-testsuite), tag
`v2026-08-31`, Apache-2.0), unmodified under `conformance/kosit/`, the ESJ document this
project builds from each of them, and the ledgers. Everything there is checked in and checked
by `mvn verify`: the ESJ files are golden files for the whole path from XML to canonical bytes,
and each ledger is asserted line by line. Over the whole corpus neither reader leaves anything
unplaced — no unknown term, no element without a position in the semantic model, no content
that spells no value.

Forty of those business cases exist twice, once as UBL and once as UN/CEFACT CII, distinguished
only by the file name suffix, and that is what the litmus test runs on.
`conformance/ledger/pairs.md` names every path at which a pair disagrees, with both values and
a cause; `conformance/ledger/pairs.json` is the machine-readable form the tests recompute.
`conformance/README.md` says what else the tests assert, and
`conformance/ledger/l3-findings.md` records the cardinality findings.

`conformance/syntax/ledger.json` is the second ledger of the same corpus: what the official
validation artefacts say about every instance, checked on every build, and
`conformance/syntax/ledger.md` records the comparison that gives those answers their standing —
the 86 instances and 63 documents broken on purpose were put through the validator KoSIT
publishes, and the two agree on every rule identifier and every flag of all 149.

## How it is run

Two readers of this repository read the corpus. `esj-bindings` matches a document against the
binding tables of `model/bindings/` one element at a time and writes the documents under
`conformance/esj/`, which is what this project says each instance is; `esj-xr` runs the
vendored XRechnung visualization stylesheets and reads the same corpus on every build as the
second implementation the first is measured against ([`readers.md`](../conformance/readers.md)).
Neither states a binding of its own: the tables carry facts taken from the syntax bindings
KoSIT publishes as a model, the stylesheets are an existing implementation of the same
bindings, and what this repository adds is the semantic model the two arrive at.

`ConformancePairsTest` recomputes the whole comparison from the checked-in documents on every
`mvn verify` and holds it against `conformance/ledger/pairs.json`. A pair that starts to
differ fails the build, and so does a pair that starts to agree: an improvement has to be
recorded in the ledger before it counts.

## The numbers

| Pairs | Outcome |
|---:|---|
| 18 | the two syntaxes arrive at the same semantic digest |
| 22 | every difference is a difference between the two KoSIT files |
| 0 | at least one difference is a limitation of the way in |
| 0 | at least one difference is a defect of a reader |
| 40 | business cases the suite carries in both syntaxes |

For all forty the import path introduces no difference at all. Twenty-two still differ because
the two files of one business case do not always say the same thing: one side writes
`Dummywert` where the other writes nothing, one side puts the postcode in the city element, one
side spells an item description with a typo. Three limitations stood in this table in earlier
releases and all three are closed — the note subject code BT-21, which `esj-xr` now splits as
the named normalization `XrNormalization.UBL_NOTE_SUBJECT_CODE`, scoped as the CEN rule
`BR-CL-08` is scoped; the scheme of the line object identifier BT-128; and BT-122, for which
the UBL tables now carry the document type condition the CII table has.
[`readers.md`](../conformance/readers.md) records the last two from the other side.

## The causes, by size

Reproduced from `conformance/ledger/pairs.md`, which the tests assert against. A *fixture*
difference is a difference between the two KoSIT files, a *limitation* is a place where the way
in does not carry something the source document holds or carries something it should not, and a
*defect* would be a fault of a reader.

| Cause | Kind | Pairs | Differing paths |
|---|---|---:|---:|
| `zero-total` | fixture | 13 | 15 |
| `item-attribute-order` | fixture | 1 | 12 |
| `seller-identifiers` | fixture | 3 | 9 |
| `buyer-name-and-trading-name` | fixture | 4 | 8 |
| `price-base-quantity` | fixture | 4 | 8 |
| `order-reference-placeholder` | fixture | 7 | 7 |
| `payee-legal-registration` | fixture | 6 | 6 |
| `address-city-postcode-swapped` | fixture | 1 | 4 |
| `address-placeholder-wording` | fixture | 1 | 2 |
| `preceding-invoice-references` | fixture | 1 | 2 |
| `seller-contact-point` | fixture | 2 | 2 |
| `buyer-trading-name-only-ubl` | fixture | 1 | 1 |
| `item-description-typo` | fixture | 1 | 1 |
| `payment-means-text` | fixture | 1 | 1 |
| `seller-vat-identifier` | fixture | 1 | 1 |
| `vat-exemption-reason` | fixture | 1 | 1 |

No limitation remains: all 80 differing paths are differences between the two KoSIT files.

## What a difference looks like

Between the UBL and the CII rendering of `business-cases/standard/01.08a-INVOICE` — two
documents of roughly 220 lines — the entire semantic difference is this:

```diff
     "/BT-12": "V876543210",
-    "/BT-13": "Dummywert",
     "/BT-14": "A123456789",
```

UBL requires `cbc:ID` inside `cac:OrderReference`, so an instance that wants to carry only the
sales order reference BT-14 has to write something as BT-13, and the suite writes `Dummywert`.
The only other hunk of that diff is the `source` member, which records provenance and is
deliberately outside the semantic digest (`SPEC.md`, section 4.7).

## The same corpus, a second reader

[`readers.md`](../conformance/readers.md) records where the two readers agree: 74 instances to
the byte and 12 with a difference, over 13 paths and three causes — the scheme of a line object
identifier, an invoiced object identifier read as a supporting document, and a tax point date
code — each of them a limitation of the stylesheets the tables do not have.

## The same corpus, written back out

The tables that say where a business term is read say where it is written, so the same module
turns a semantic document into a cross industry invoice or a UBL document. Each writer is
measured in the two ways a writer can be wrong: whether the official validation artefacts of
the pack `xrechnung/3.0.2/2026-08-31` accept the result, and whether reading the result back
gives the semantic document it was written from.

| Over the 86 instances | CII writer | UBL writer |
|---|---:|---:|
| `accepted` — nothing fatal from the artefacts | 85 | 85 |
| `identical` — reading back gives the document written | 80 | 86 |

The two columns answer different questions and are never added together. The one instance each
writer is refused for states no BT-110, which both syntaxes require and the business rules
fault as well; where UBL requires an element EN 16931-1 has no term for, the `conventions`
member of the binding table says what is written there ([`bindings.md`](bindings.md)). A
difference on reading back is a value the target syntax has no place for: the six CII instances
are the four that carry extension sub invoice lines, the one that carries the extension terms,
and the two that state more than one preceding invoice.
[`writers/cii-roundtrip.md`](../conformance/writers/cii-roundtrip.md) and
[`ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md) name every one of them.

## The matrix

[`writers/matrix.md`](../conformance/writers/matrix.md) puts the two writers against each other
over the 40 business cases the corpus carries in both syntaxes: read one file of the pair,
write it as the other syntax, validate the result with the artefacts of the pack, read it back
and compare it with the other file of the pair as it is read.

| Measure | UBL → CII | CII → UBL |
|---|---:|---:|
| `accepted` — nothing fatal from the artefacts | 40 | 39 |
| `refused` — at least one fatal finding | 0 | 1 |
| `equal` — the result says what the other file says | 18 | 18 |
| `differing` — at least one semantic path differs | 22 | 22 |
| `paths` — semantic paths that differ, over all pairs | 78 | 80 |

How to read it: 18 is the ceiling, because 18 pairs is what the table above records as
agreeing, and converting a document cannot make two files agree that do not. Both directions
reach it. Every one of the 158 differing paths is a difference the pairs ledger already carries
with the cause `fixture`; not one is a value a writer mislaid, and `WriterMatrixTest` fails on a
path the ledger does not carry. The one refusal is the UBL profile asking for a term the
document does not state, the same cause as above.

One asymmetry is recorded rather than counted as agreement: a case whose UBL file states two
preceding invoices and whose CII file states one comes back agreeing at those two paths because
the CII schema admits one `ram:InvoiceReferencedDocument` and the second reference was dropped.
The writer reports that loss, `matrix.json` records it under `masked`, and the test holds the
recorded set to the losses the writers actually report.

All of it runs on every build.

## What this is not

**Neither reader is an ESJ syntax binding.** ESJ defines no mapping from UBL or CII elements to
business terms: that mapping belongs to the syntax bindings of CEN/TS 16931-3. `esj-bindings`
takes the XPaths of an existing published model of those bindings as facts, `esj-xr` borrows an
existing implementation of them, and neither invents one. The limitations above are therefore
inherited rather than designed, and a third way in would move the numbers of this page without
changing a line of `SPEC.md`.

The note subject code is the one place where `esj-xr` reads a value rather than only moving it:
a named, switchable normalization with its own class in the public API, following a published
CEN rule that reads the same characters at the same place, with the rule, the source and the
scope in the ledger.
