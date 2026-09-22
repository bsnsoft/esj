# What this pack says about the corpus

*Part of [EN16931 Semantic JSON](../../README.md). [`coverage.md`](coverage.md) is which rules
the pack carries; this page is what they say about documents that are known to be good.*

[`corpus.json`](corpus.json) is a golden file. It records every finding the EN 16931 rule pack
makes about the 86 instances of the conformance corpus and the eleven 2017 documents of
[`examples/`](../../examples/README.md), and a test of `esj-rules` runs the pack and compares. A
finding that appears, disappears or changes its paths is a change in what this pack says about a
document nobody changed, and the build stops until somebody has looked at it.

| | |
|---|---|
| Documents | 97 — the 86 corpus instances and the eleven examples |
| Documents with no finding at all | 90 |
| Findings in total | 20 |
| Of those, on a corpus instance | 5 |

## The five findings on the corpus

The 86 corpus instances are invoices the official validator accepts. This pack reports five
things about four of them, and not one of the five is a defect of a rule:

| Document | Rule | What it says |
|---|---|---|
| `business-cases/extension/05.01a-INVOICE_ubl.xml` | `BR-CO-16` | The amount due for payment (BT-115) is 366.86, and the invoice total amount with VAT (BT-112) is 336.90 with no paid amount and no rounding amount to explain the difference. |
| `business-cases/extension/04.05a-INVOICE_uncefact.xml` | `BR-CL-10` | The seller, buyer and payee identifiers name the identification scheme `XR03`, which is not on the ISO/IEC 6523 ICD list. |
| `business-cases/extension/04.05a-INVOICE_uncefact.xml` | `BR-CL-21` | The item standard identifier names the identification scheme `XR01`, which is not on that list either. |
| `technical-cases/cvd/02.01a-cvd_INVOICE_ubl.xml` | `BR-CL-13` | An item classification identifier names the scheme `CVD`, which is not a UNTDID 7143 entry. |
| `technical-cases/cvd/02.01a-cvd_INVOICE_uncefact.xml` | `BR-CL-13` | The same, in the other syntax. |

**The official artefacts say the same thing about the same documents.** The EN 16931 Schematron
of release 1.3.16 raises exactly these rules on exactly these instances, each with the flag
`fatal`; the core invoice usage specification each document names levels those findings down to
information for documents of its profile, which is why the validator accepts them.
[`../syntax/ledger.json`](../syntax/ledger.json) records that, instance by instance — down to the
three separate `BR-CL-10` locations in `04.05a`, where this pack stops at the first and says
which value it stopped at. So the two engines agree about what is wrong and about what it costs.
A finding of this pack carries its severity as the rule declares it; the verdict of `esj
validate` then applies the level the profile of the document gives that rule, to a finding of
this pack as to one of an artefact, and all 86 instances are `VALID`
([`../../docs/validation.md`](../../docs/validation.md)).

The three documents are worth a second sentence, because the scheme identifiers they carry are
not mistakes: `XR01` and `XR03` are identifiers a German specification defines for its own use,
and `CVD` is the classification scheme of a domain-specific extension. The standard says an
identification scheme is chosen from the ICD list and from UNTDID 7143, the artefacts assert
that, and both of them fault these documents; the profile that defined the codes is the one that
decides they are acceptable anyway. That is the division this repository keeps everywhere: a
statement of the standard is what the pack decides, and a profile is a layer above it.

One further corpus instance would have been reported until this release, and the rule was
withdrawn instead of the report. `technical-cases/cius/01.02_comprehensive_test_uncefact.xml`
carries a value added tax point date code (BT-8) of `5`, which is the UNTDID 2475 code a cross
industry invoice writes; the standard binds the term to a restriction of UNTDID 2005, in which
the same meaning is `3`. The binding table now marks that element with the flag
`code-list-2475` and the streaming reader translates it, so the value this pack sees is the code
of the standard whichever syntax the invoice arrived in, and `BR-CL-06` is a rule of the pack
like any other.

## The findings on the examples

Fifteen findings fall on three of the eleven examples, and all fifteen were expected before they
were measured: [`examples/README.md`](../../examples/README.md) calls those three documents about
the structure of the format rather than invoices anybody would send, each carrying the mandatory
terms of the model and nothing else. Now the sentence has figures behind it.

| Document | Rules |
|---|---|
| `minimal.esj.json` | `BR-48`, `BR-CO-25`, `BR-CO-26`, `BR-Z-02`, `BR-Z-05` |
| `extended.esj.json` | the same five: the document is the values of `minimal.esj.json` with extensions and a source |
| `extension-depth.esj.json` | the same five, for the same reason |

Every term those rules ask for is optional in the model, so adding one would make the minimal
document no longer minimal, which is the whole of what it demonstrates.
`smallest-valid.esj.json` is where the trade is made instead: the same values plus the four terms
these rules ask for, and this pack is silent about it.

One of the fifteen is a finding no other tool of this release reports. `BR-CO-25` is a rule the
standard states in clause 6.4.2 and the validation artefacts of release 1.3.16 do not carry, so a
document that states an amount due for payment without a payment due date or payment terms passes
them and is faulted here.

The eight remaining examples — `smallest-valid`, `standard-invoice`, `multiple-lines`,
`allowances`, `charges`, `self-billed`, `credit-note` and `b2c-gross` — carry no finding of any
severity. `b2c-gross` carries the four terms of the B2C extension; this pack is about EN 16931 and
measures the core terms of that document like those of any other.

## What this page does not claim

That the pack agrees with the official artefacts in general. This page is about documents that
are good, and a pack that reports nothing would pass it. Agreement is measured by running both
engines over the same bytes, including bytes broken on purpose, and counting; that measurement is
[`ledger.md`](ledger.md), and no claim about agreement is made anywhere in this repository
without the figures of that ledger beside it. What this page establishes is narrower and worth
having on its own: on 82 of the 86 instances the pack is silent, and on the other four it says
what the artefacts say.
