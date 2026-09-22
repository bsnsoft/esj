# What the EN 16931 pack covers

*Part of [EN16931 Semantic JSON](../../README.md). [`rules/README.md`](../../rules/README.md) is
the language these rules are written in, [`docs/validation.md`](../../docs/validation.md) is what a
finding of the semantic engine means, and
[`not-applicable.md`](not-applicable.md) says why a rule that is not implemented is not.*

A rule pack that says "the business rules of EN 16931" and does not say which ones is a claim
nobody can check. This table is the answer, rule by rule, and a test of `esj-rules` reads it: every
identifier of the official validation artefacts, release 1.3.16, is either implemented here or
named in `not-applicable.md`, and no identifier appears twice.

## The figures

| | Rules of the release | Implemented | Not applicable | Deferred |
|---|---|---|---|---|
| Model rules (`EN16931-model.sch`) | 201 | 195 | 0 | 6 |
| Code list rules (`EN16931-UBL-codes.sch`) | 22 | 21 | 1 | 0 |
| **Together** | **223** | **216** | **1** | **6** |

Of the 216, one hundred and eighty-eight are written in the rule language and twenty-eight in Java.
One further rule is implemented that the artefacts of this release do not carry: `BR-CO-25`, which
the standard states in clause 6.4.2 — an invoice with an amount due for payment states a payment
due date or payment terms — and which no identifier of the release covers. The pack therefore
carries 217 rules in all.

Two of the 217 are implemented and cannot fail here. `BR-CO-19` and `BR-CO-20` ask that an
invoicing period, where one is there, state a start date or an end date; the groups they are about
carry no other business term, and a business group of an ESJ document exists exactly when it
carries a value, so a period with neither date is not an empty period but no period at all. The
XML binding can write the empty element the rules were written for, and the rules therefore keep
their place in a pack that also serves documents imported from XML. Their `note` says so, and the
rule case table of `esj-rules` names them as the two rules for which it holds no negative case.

## Where the identifiers come from

The identifiers are the artefacts' and the statements are the norm's, and the two do not always
agree. Three differences are worth naming before the table:

- The standard numbers its integrity constraints `BR-1`, `BR-2`, `BR-3`; the artefacts write
  `BR-01`, `BR-02`, `BR-03`. The artefacts' spelling is used, so that a report of this engine can
  be compared line for line with a report of theirs.
- The standard numbers the Canary Islands family `BR-IG-*` and the Ceuta and Melilla family
  `BR-IP-*`; the artefacts number them `BR-AF-*` and `BR-AG-*`. The artefacts' identifiers are
  used and the `source` of every such rule names the clause and the standard's own number.
- The decimal restrictions of clause 6.5.12 and the code list restrictions of clause 6.3 carry no
  rule identifier in the standard at all: it states them as a table of terms. `BR-DEC-*` and
  `BR-CL-*` are the artefacts' numbering of those tables, and the `source` of each rule names the
  business term instead.

## Model rules

| Rule | Flag | How | Where |
|---|---|---|---|
| `BR-01` | fatal | native-json | `rules/br.json` |
| `BR-02` | fatal | native-json | `rules/br.json` |
| `BR-03` | fatal | native-json | `rules/br.json` |
| `BR-04` | fatal | native-json | `rules/br.json` |
| `BR-05` | fatal | native-json | `rules/br.json` |
| `BR-06` | fatal | native-json | `rules/br.json` |
| `BR-07` | fatal | native-json | `rules/br.json` |
| `BR-08` | fatal | native-json | `rules/br.json` |
| `BR-09` | fatal | native-json | `rules/br.json` |
| `BR-10` | fatal | native-json | `rules/br.json` |
| `BR-11` | fatal | native-json | `rules/br.json` |
| `BR-12` | fatal | native-json | `rules/br.json` |
| `BR-13` | fatal | native-json | `rules/br.json` |
| `BR-14` | fatal | native-json | `rules/br.json` |
| `BR-15` | fatal | native-json | `rules/br.json` |
| `BR-16` | fatal | native-json | `rules/br.json` |
| `BR-17` | fatal | native-json | `rules/br.json` |
| `BR-18` | fatal | native-json | `rules/br.json` |
| `BR-19` | fatal | native-json | `rules/br.json` |
| `BR-20` | fatal | native-json | `rules/br.json` |
| `BR-21` | fatal | native-json | `rules/br.json` |
| `BR-22` | fatal | native-json | `rules/br.json` |
| `BR-23` | fatal | native-json | `rules/br.json` |
| `BR-24` | fatal | native-json | `rules/br.json` |
| `BR-25` | fatal | native-json | `rules/br.json` |
| `BR-26` | fatal | native-json | `rules/br.json` |
| `BR-27` | fatal | native-json | `rules/br.json` |
| `BR-28` | fatal | native-json | `rules/br.json` |
| `BR-29` | fatal | native-json | `rules/br.json` |
| `BR-30` | fatal | native-json | `rules/br.json` |
| `BR-31` | fatal | native-json | `rules/br.json` |
| `BR-32` | fatal | native-json | `rules/br.json` |
| `BR-33` | fatal | native-json | `rules/br.json` |
| `BR-36` | fatal | native-json | `rules/br.json` |
| `BR-37` | fatal | native-json | `rules/br.json` |
| `BR-38` | fatal | native-json | `rules/br.json` |
| `BR-41` | fatal | native-json | `rules/br.json` |
| `BR-42` | fatal | native-json | `rules/br.json` |
| `BR-43` | fatal | native-json | `rules/br.json` |
| `BR-44` | fatal | native-json | `rules/br.json` |
| `BR-45` | fatal | native-json | `rules/br.json` |
| `BR-46` | fatal | native-json | `rules/br.json` |
| `BR-47` | fatal | native-json | `rules/br.json` |
| `BR-48` | fatal | native-json | `rules/br.json` |
| `BR-49` | fatal | native-json | `rules/br.json` |
| `BR-50` | fatal | native-json | `rules/br.json` |
| `BR-51` | warning | native-json | `rules/br.json` |
| `BR-52` | fatal | native-json | `rules/br.json` |
| `BR-53` | fatal | native-json | `rules/br.json` |
| `BR-54` | fatal | native-json | `rules/br.json` |
| `BR-55` | fatal | native-json | `rules/br.json` |
| `BR-56` | fatal | native-json | `rules/br.json` |
| `BR-57` | fatal | native-json | `rules/br.json` |
| `BR-61` | fatal | native-json | `rules/br.json` |
| `BR-62` | fatal | native-java | `Br62.java` |
| `BR-63` | fatal | native-java | `Br63.java` |
| `BR-64` | fatal | native-java | `Br64.java` |
| `BR-65` | fatal | native-java | `Br65.java` |
| `BR-AE-01` | fatal | native-java | `BrAe01.java` |
| `BR-AE-02` | fatal | native-json | `rules/vat-ae.json` |
| `BR-AE-03` | fatal | native-json | `rules/vat-ae.json` |
| `BR-AE-04` | fatal | native-json | `rules/vat-ae.json` |
| `BR-AE-05` | fatal | native-json | `rules/vat-ae.json` |
| `BR-AE-06` | fatal | native-json | `rules/vat-ae.json` |
| `BR-AE-07` | fatal | native-json | `rules/vat-ae.json` |
| `BR-AE-08` | fatal | native-java | `BrAe08.java` |
| `BR-AE-09` | fatal | native-json | `rules/vat-ae.json` |
| `BR-AE-10` | fatal | native-json | `rules/vat-ae.json` |
| `BR-AF-01` | fatal | native-json | `rules/vat-ig.json` |
| `BR-AF-02` | fatal | native-json | `rules/vat-ig.json` |
| `BR-AF-03` | fatal | native-json | `rules/vat-ig.json` |
| `BR-AF-04` | fatal | native-json | `rules/vat-ig.json` |
| `BR-AF-05` | fatal | native-json | `rules/vat-ig.json` |
| `BR-AF-06` | fatal | native-json | `rules/vat-ig.json` |
| `BR-AF-07` | fatal | native-json | `rules/vat-ig.json` |
| `BR-AF-08` | fatal | native-java | `BrAf08.java` |
| `BR-AF-09` | fatal | native-json | `rules/vat-ig.json` |
| `BR-AF-10` | fatal | native-json | `rules/vat-ig.json` |
| `BR-AG-01` | fatal | native-json | `rules/vat-ip.json` |
| `BR-AG-02` | fatal | native-json | `rules/vat-ip.json` |
| `BR-AG-03` | fatal | native-json | `rules/vat-ip.json` |
| `BR-AG-04` | fatal | native-json | `rules/vat-ip.json` |
| `BR-AG-05` | fatal | native-json | `rules/vat-ip.json` |
| `BR-AG-06` | fatal | native-json | `rules/vat-ip.json` |
| `BR-AG-07` | fatal | native-json | `rules/vat-ip.json` |
| `BR-AG-08` | fatal | native-java | `BrAg08.java` |
| `BR-AG-09` | fatal | native-json | `rules/vat-ip.json` |
| `BR-AG-10` | fatal | native-json | `rules/vat-ip.json` |
| `BR-B-01` | fatal | deferred | — |
| `BR-B-02` | fatal | deferred | — |
| `BR-CL-08` | fatal | native-json | `rules/br-cl.json` |
| `BR-CO-03` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-04` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-05` | fatal | deferred | — |
| `BR-CO-06` | fatal | deferred | — |
| `BR-CO-07` | fatal | deferred | — |
| `BR-CO-08` | fatal | deferred | — |
| `BR-CO-09` | fatal | native-java | `BrCo09.java` |
| `BR-CO-10` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-11` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-12` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-13` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-14` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-15` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-16` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-17` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-18` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-19` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-20` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-21` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-22` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-23` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-24` | fatal | native-json | `rules/br-co.json` |
| `BR-CO-26` | fatal | native-json | `rules/br-co.json` |
| `BR-DEC-01` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-02` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-05` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-06` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-09` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-10` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-11` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-12` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-13` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-14` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-15` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-16` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-17` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-18` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-19` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-20` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-23` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-24` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-25` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-27` | fatal | native-json | `rules/br-dec.json` |
| `BR-DEC-28` | fatal | native-json | `rules/br-dec.json` |
| `BR-E-01` | fatal | native-java | `BrE01.java` |
| `BR-E-02` | fatal | native-json | `rules/vat-e.json` |
| `BR-E-03` | fatal | native-json | `rules/vat-e.json` |
| `BR-E-04` | fatal | native-json | `rules/vat-e.json` |
| `BR-E-05` | fatal | native-json | `rules/vat-e.json` |
| `BR-E-06` | fatal | native-json | `rules/vat-e.json` |
| `BR-E-07` | fatal | native-json | `rules/vat-e.json` |
| `BR-E-08` | fatal | native-java | `BrE08.java` |
| `BR-E-09` | fatal | native-json | `rules/vat-e.json` |
| `BR-E-10` | fatal | native-json | `rules/vat-e.json` |
| `BR-G-01` | fatal | native-java | `BrG01.java` |
| `BR-G-02` | fatal | native-json | `rules/vat-g.json` |
| `BR-G-03` | fatal | native-json | `rules/vat-g.json` |
| `BR-G-04` | fatal | native-json | `rules/vat-g.json` |
| `BR-G-05` | fatal | native-json | `rules/vat-g.json` |
| `BR-G-06` | fatal | native-json | `rules/vat-g.json` |
| `BR-G-07` | fatal | native-json | `rules/vat-g.json` |
| `BR-G-08` | fatal | native-java | `BrG08.java` |
| `BR-G-09` | fatal | native-json | `rules/vat-g.json` |
| `BR-G-10` | fatal | native-json | `rules/vat-g.json` |
| `BR-IC-01` | fatal | native-java | `BrIc01.java` |
| `BR-IC-02` | fatal | native-json | `rules/vat-ic.json` |
| `BR-IC-03` | fatal | native-json | `rules/vat-ic.json` |
| `BR-IC-04` | fatal | native-json | `rules/vat-ic.json` |
| `BR-IC-05` | fatal | native-json | `rules/vat-ic.json` |
| `BR-IC-06` | fatal | native-json | `rules/vat-ic.json` |
| `BR-IC-07` | fatal | native-json | `rules/vat-ic.json` |
| `BR-IC-08` | fatal | native-java | `BrIc08.java` |
| `BR-IC-09` | fatal | native-json | `rules/vat-ic.json` |
| `BR-IC-10` | fatal | native-json | `rules/vat-ic.json` |
| `BR-IC-11` | fatal | native-json | `rules/vat-ic.json` |
| `BR-IC-12` | fatal | native-json | `rules/vat-ic.json` |
| `BR-O-01` | fatal | native-java | `BrO01.java` |
| `BR-O-02` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-03` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-04` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-05` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-06` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-07` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-08` | fatal | native-java | `BrO08.java` |
| `BR-O-09` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-10` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-11` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-12` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-13` | fatal | native-json | `rules/vat-o.json` |
| `BR-O-14` | fatal | native-json | `rules/vat-o.json` |
| `BR-S-01` | fatal | native-json | `rules/vat-s.json` |
| `BR-S-02` | fatal | native-json | `rules/vat-s.json` |
| `BR-S-03` | fatal | native-json | `rules/vat-s.json` |
| `BR-S-04` | fatal | native-json | `rules/vat-s.json` |
| `BR-S-05` | fatal | native-json | `rules/vat-s.json` |
| `BR-S-06` | fatal | native-json | `rules/vat-s.json` |
| `BR-S-07` | fatal | native-json | `rules/vat-s.json` |
| `BR-S-08` | fatal | native-java | `BrS08.java` |
| `BR-S-09` | fatal | native-json | `rules/vat-s.json` |
| `BR-S-10` | fatal | native-json | `rules/vat-s.json` |
| `BR-Z-01` | fatal | native-java | `BrZ01.java` |
| `BR-Z-02` | fatal | native-json | `rules/vat-z.json` |
| `BR-Z-03` | fatal | native-json | `rules/vat-z.json` |
| `BR-Z-04` | fatal | native-json | `rules/vat-z.json` |
| `BR-Z-05` | fatal | native-json | `rules/vat-z.json` |
| `BR-Z-06` | fatal | native-json | `rules/vat-z.json` |
| `BR-Z-07` | fatal | native-json | `rules/vat-z.json` |
| `BR-Z-08` | fatal | native-java | `BrZ08.java` |
| `BR-Z-09` | fatal | native-json | `rules/vat-z.json` |
| `BR-Z-10` | fatal | native-json | `rules/vat-z.json` |

## Code list rules

The code list rules live in a second Schematron of the release rather than in the model one,
because the artefacts check them against the XML. Here they are statements about business terms
like any other, and the list a term is decided against is the snapshot named in
[`rules/en16931/1.3.16/codelists/SOURCES.md`](../../rules/en16931/1.3.16/codelists/SOURCES.md).

| Rule | How | Where |
|---|---|---|
| `BR-CL-01` | native-json | `rules/br-cl.json` |
| `BR-CL-03` | not applicable | — |
| `BR-CL-04` | native-json | `rules/br-cl.json` |
| `BR-CL-05` | native-json | `rules/br-cl.json` |
| `BR-CL-06` | native-json | `rules/br-cl.json` |
| `BR-CL-07` | native-java | `BrCl07.java` |
| `BR-CL-10` | native-java | `BrCl10.java` |
| `BR-CL-11` | native-java | `BrCl11.java` |
| `BR-CL-13` | native-java | `BrCl13.java` |
| `BR-CL-14` | native-json | `rules/br-cl.json` |
| `BR-CL-15` | native-json | `rules/br-cl.json` |
| `BR-CL-16` | native-json | `rules/br-cl.json` |
| `BR-CL-17` | native-json | `rules/br-cl.json` |
| `BR-CL-18` | native-json | `rules/br-cl.json` |
| `BR-CL-19` | native-json | `rules/br-cl.json` |
| `BR-CL-20` | native-json | `rules/br-cl.json` |
| `BR-CL-21` | native-java | `BrCl21.java` |
| `BR-CL-22` | native-json | `rules/br-cl.json` |
| `BR-CL-23` | native-json | `rules/br-cl.json` |
| `BR-CL-24` | native-java | `BrCl24.java` |
| `BR-CL-25` | native-java | `BrCl25.java` |
| `BR-CL-26` | native-java | `BrCl26.java` |

## What this table does not say

It says which rules exist, not that they agree with the artefacts. That is measured separately
and is [`ledger.md`](ledger.md): both engines over the corpus and over 448 mutations of it, rule
identifier against rule identifier. Of the 217 rules the pack carries, the two report the same
identifiers on every shape measured for 166; on 33 they agree on one shape of the defect and
differ on another; on 16 they differ outright, for reasons the ledger names one by one; and on
2 the artefacts cannot be asked at all. [`corpus.md`](corpus.md) is the narrower statement that comes first: what this pack says
about the 86 instances and the eleven examples, which is nothing at all on 82 of the instances.
