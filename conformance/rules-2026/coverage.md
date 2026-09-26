# What the EN 16931-1:2026 pack covers

*Part of [EN16931 Semantic JSON](../../README.md). [`ledger.md`](ledger.md) is the evidence
for each rule, [`rules/README.md`](../../rules/README.md) the language they are written in,
[`docs/editions.md`](../../docs/editions.md) what this build does with the edition, and
[`docs/validation.md`](../../docs/validation.md) what a finding of the semantic engine means.*

**No official validation artefact is published for this edition.** The artefact releases of
CEN/TC 434 end at 1.3.16 and validate EN 16931-1:2017+A1:2019/AC:2020, and no syntax binding
of the terms this edition adds is published either. A document of this edition therefore never
reaches `VALID`: the business rules of this pack run and can make it `INVALID`, and the best
it reaches otherwise is `INDETERMINATE` with the cause `no-artefacts-for-edition`. Every rule
names in its `oracle` member what stands behind it — `downgrade` where this edition leaves it
unchanged, `cases` where it changed or added it — and [`ledger.md`](ledger.md) is what each of
the two was measured by.

## The figures

| | Rules of the edition | Implemented | Not applicable |
|---|---|---|---|
| Integrity constraints (clause 6.4.1) | 69 | 68 | 1 |
| Conditions (clause 6.4.2) | 42 | 38 | 4 |
| Standard and reduced rate (Table 6) | 9 | 9 | 0 |
| Zero rate (Table 7) | 9 | 9 | 0 |
| Exempt from VAT (Table 8) | 10 | 10 | 0 |
| Reverse charge (Table 9) | 10 | 10 | 0 |
| Intra-community supply (Table 10) | 12 | 12 | 0 |
| Exports (Table 11) | 10 | 10 | 0 |
| Not subject to VAT (Table 12) | 7 | 7 | 0 |
| National and margin schemes (Table 13) | 3 | 3 | 0 |
| Canary Islands tax (Table 14) | 9 | 9 | 0 |
| Ceuta and Melilla tax (Table 15) | 9 | 9 | 0 |
| **Together** | **199** | **194** | **5** |

Beside those, the pack carries the fraction digit rules of clause 6.5.13 (`BR-DEC-*`, one per
row of Table 28) and eighteen of the twenty-two code list rules of clause 6.3 (`BR-CL-*`),
taken over from the pack of the 2017 edition where this edition names the same list for the
same terms. The counts of the whole pack are in its manifest.

Four code list rules of that pack are not carried, because this edition names another list
for the terms they are about: `BR-CL-10` (BT-29, BT-46, BT-60) and `BR-CL-26` (BT-71) decide
against the ISO/IEC 6523 list, which this edition complements with an entry for an identifier
the buyer assigned and one for an identifier the seller assigned; `BR-CL-21` (BT-157) decides
against the same list where this edition names the entries of UNTDID 7143; and `BR-CL-24`
(BT-125) decides against the six media types clause 6.5.11 admitted, where this edition admits
seven. The identification scheme of those terms is therefore not decided against a list here.
That a scheme is stated at all is decided: `BR-69`, `BR-71`, `BR-73`, `BR-75`.

## Where the identifiers come from

- The standard numbers its integrity constraints `BR-1`, `BR-2`, `BR-3`; this pack writes
  `BR-01`, `BR-02`, `BR-03`, as the pack of the 2017 edition does, so that a report of the two
  compares line for line. The padding is this project's.
- The Canary Islands family is `BR-IG-*` and the Ceuta and Melilla family `BR-IP-*`, the
  standard's own numbering. Release 1.3.16 calls the same two families `BR-AF-*` and
  `BR-AG-*`; no artefact covers this edition, so there is nothing to follow. A report of the
  two packs differs in those eighteen identifiers and in no other.
- The fraction digit limits of clause 6.5.13 and the code list restrictions of clause 6.3
  carry no rule identifier in the standard: it states them as tables of terms, and `BR-DEC-*`
  and `BR-CL-*` are the numbering release 1.3.16 gave those tables. A term with a row in both
  editions keeps its number there; a row this edition adds is numbered from `BR-DEC-30` up,
  which no release used. The `source` of each such rule names the business term.

## Two readings of this pack

A VAT breakdown may state its amounts in the VAT accounting currency and name it in BT-184. The
rules that weigh a breakdown against the invoice — `BR-CO-14`, `BR-CO-18`, the `*-01` and `*-08`
rules written in Java and the `*-09` rules — count only the breakdowns in the invoice currency;
`BR-CO-49` and `BR-CO-50` weigh the others against them. The edition states `BR-S-9` for every
standard rated breakdown, and a converted one need not meet it within its tolerance, so this pack
does not ask it to. `BR-CO-33` reads as credit notes the twelve codes of UNTDID 1001 named so.

## Rules

### Integrity constraints (clause 6.4.1)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-01` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-02` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-03` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-04` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-05` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-06` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-07` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-08` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-09` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-10` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-11` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-12` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-13` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-14` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-15` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-16` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-17` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-18` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-19` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-20` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-21` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-22` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-23` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-24` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-25` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-26` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-27` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-28` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-29` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-30` | native-json | `rules/br.json` | `cases` |
| `BR-31` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-32` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-33` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-36` | native-json | `rules/br.json` | `cases` |
| `BR-37` | native-json | `rules/br.json` | `cases` |
| `BR-38` | native-json | `rules/br.json` | `cases` |
| `BR-41` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-42` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-43` | native-json | `rules/br.json` | `cases` |
| `BR-44` | native-json | `rules/br.json` | `cases` |
| `BR-45` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-46` | native-json | `rules/br.json` | `cases` |
| `BR-47` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-48` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-49` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-50` | native-json | `rules/br.json` | `cases` |
| `BR-76` | native-json | `rules/br.json` | `cases` |
| `BR-51` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-52` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-53` | native-json | `rules/br.json` | `cases` |
| `BR-54` | native-json | `rules/br.json` | `cases` |
| `BR-55` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-56` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-57` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-61` | shared | `rules/br.json` of `en16931/1.3.16` | `downgrade` |
| `BR-62` | native-java | `Br62.java` | `downgrade` |
| `BR-63` | native-java | `Br63.java` | `downgrade` |
| `BR-64` | native-java | `Br64.java` | `downgrade` |
| `BR-69` | native-java | `Br69.java` | `cases` |
| `BR-70` | native-java | `Br70.java` | `cases` |
| `BR-71` | native-java | `Br71.java` | `cases` |
| `BR-72` | native-java | `Br72.java` | `cases` |
| `BR-73` | native-java | `Br73.java` | `cases` |
| `BR-74` | native-java | `Br74.java` | `cases` |
| `BR-75` | native-java | `Br75.java` | `cases` |
| `BR-65` | native-java | `Br65.java` | `downgrade` |
| `BR-66` | not applicable | — | — |
| `BR-67` | native-json | `rules/br.json` | `cases` |
| `BR-68` | native-java | `Br68.java` | `cases` |

### Conditions (clause 6.4.2)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-CO-03` | shared | `rules/br-co.json` of `en16931/1.3.16` | `downgrade` |
| `BR-CO-40` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-46` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-45` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-47` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-04` | shared | `rules/br-co.json` of `en16931/1.3.16` | `downgrade` |
| `BR-CO-05` | not applicable | — | — |
| `BR-CO-06` | not applicable | — | — |
| `BR-CO-07` | not applicable | — | — |
| `BR-CO-38` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-08` | not applicable | — | — |
| `BR-CO-39` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-09` | native-java | `BrCo09.java` | `cases` |
| `BR-CO-10` | native-json | `rules/br-co.json` | `downgrade` |
| `BR-CO-11` | native-json | `rules/br-co.json` | `downgrade` |
| `BR-CO-12` | native-json | `rules/br-co.json` | `downgrade` |
| `BR-CO-13` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-14` | native-java | `BrCo14.java` | `cases` |
| `BR-CO-15` | native-json | `rules/br-co.json` | `downgrade` |
| `BR-CO-48` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-16` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-18` | native-java | `BrCo18.java` | `cases` |
| `BR-CO-19` | shared | `rules/br-co.json` of `en16931/1.3.16` | `downgrade` |
| `BR-CO-20` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-21` | shared | `rules/br-co.json` of `en16931/1.3.16` | `downgrade` |
| `BR-CO-22` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-23` | shared | `rules/br-co.json` of `en16931/1.3.16` | `downgrade` |
| `BR-CO-24` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-25` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-26` | shared | `rules/br-co.json` of `en16931/1.3.16` | `downgrade` |
| `BR-CO-28` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-29` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-30` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-31` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-49` | native-java | `BrCo49.java` | `cases` |
| `BR-CO-50` | native-java | `BrCo50.java` | `cases` |
| `BR-CO-32` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-33` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-34` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-35` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-36` | native-json | `rules/br-co.json` | `cases` |
| `BR-CO-51` | native-json | `rules/br-co.json` | `cases` |

### Standard and reduced rate (Table 6)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-S-01` | native-json | `rules/vat-s.json` | `cases` |
| `BR-S-02` | shared | `rules/vat-s.json` of `en16931/1.3.16` | `downgrade` |
| `BR-S-03` | shared | `rules/vat-s.json` of `en16931/1.3.16` | `downgrade` |
| `BR-S-04` | native-json | `rules/vat-s.json` | `cases` |
| `BR-S-05` | shared | `rules/vat-s.json` of `en16931/1.3.16` | `downgrade` |
| `BR-S-06` | shared | `rules/vat-s.json` of `en16931/1.3.16` | `downgrade` |
| `BR-S-07` | native-json | `rules/vat-s.json` | `cases` |
| `BR-S-08` | native-java | `BrS08.java` | `cases` |
| `BR-S-09` | native-json | `rules/vat-s.json` | `cases` |

### Zero rate (Table 7)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-Z-01` | native-json | `rules/vat-z.json` | `cases` |
| `BR-Z-02` | shared | `rules/vat-z.json` of `en16931/1.3.16` | `downgrade` |
| `BR-Z-03` | shared | `rules/vat-z.json` of `en16931/1.3.16` | `downgrade` |
| `BR-Z-04` | native-json | `rules/vat-z.json` | `cases` |
| `BR-Z-05` | shared | `rules/vat-z.json` of `en16931/1.3.16` | `downgrade` |
| `BR-Z-06` | shared | `rules/vat-z.json` of `en16931/1.3.16` | `downgrade` |
| `BR-Z-07` | native-json | `rules/vat-z.json` | `cases` |
| `BR-Z-08` | native-java | `BrZ08.java` | `cases` |
| `BR-Z-09` | shared | `rules/vat-z.json` of `en16931/1.3.16` | `downgrade` |

### Exempt from VAT (Table 8)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-E-01` | native-java | `BrE01.java` | `cases` |
| `BR-E-02` | shared | `rules/vat-e.json` of `en16931/1.3.16` | `downgrade` |
| `BR-E-03` | shared | `rules/vat-e.json` of `en16931/1.3.16` | `downgrade` |
| `BR-E-04` | native-json | `rules/vat-e.json` | `cases` |
| `BR-E-05` | shared | `rules/vat-e.json` of `en16931/1.3.16` | `downgrade` |
| `BR-E-06` | shared | `rules/vat-e.json` of `en16931/1.3.16` | `downgrade` |
| `BR-E-07` | native-json | `rules/vat-e.json` | `cases` |
| `BR-E-08` | native-java | `BrE08.java` | `cases` |
| `BR-E-09` | shared | `rules/vat-e.json` of `en16931/1.3.16` | `downgrade` |
| `BR-E-10` | native-json | `rules/vat-e.json` | `cases` |

### Reverse charge (Table 9)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-AE-01` | native-java | `BrAe01.java` | `cases` |
| `BR-AE-02` | shared | `rules/vat-ae.json` of `en16931/1.3.16` | `downgrade` |
| `BR-AE-03` | shared | `rules/vat-ae.json` of `en16931/1.3.16` | `downgrade` |
| `BR-AE-04` | native-json | `rules/vat-ae.json` | `cases` |
| `BR-AE-05` | shared | `rules/vat-ae.json` of `en16931/1.3.16` | `downgrade` |
| `BR-AE-06` | shared | `rules/vat-ae.json` of `en16931/1.3.16` | `downgrade` |
| `BR-AE-07` | native-json | `rules/vat-ae.json` | `cases` |
| `BR-AE-08` | native-java | `BrAe08.java` | `cases` |
| `BR-AE-09` | shared | `rules/vat-ae.json` of `en16931/1.3.16` | `downgrade` |
| `BR-AE-10` | native-json | `rules/vat-ae.json` | `cases` |

### Intra-community supply (Table 10)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-IC-01` | native-java | `BrIc01.java` | `cases` |
| `BR-IC-02` | shared | `rules/vat-ic.json` of `en16931/1.3.16` | `downgrade` |
| `BR-IC-03` | shared | `rules/vat-ic.json` of `en16931/1.3.16` | `downgrade` |
| `BR-IC-04` | native-json | `rules/vat-ic.json` | `cases` |
| `BR-IC-05` | shared | `rules/vat-ic.json` of `en16931/1.3.16` | `downgrade` |
| `BR-IC-06` | shared | `rules/vat-ic.json` of `en16931/1.3.16` | `downgrade` |
| `BR-IC-07` | native-json | `rules/vat-ic.json` | `cases` |
| `BR-IC-08` | native-java | `BrIc08.java` | `cases` |
| `BR-IC-09` | shared | `rules/vat-ic.json` of `en16931/1.3.16` | `downgrade` |
| `BR-IC-10` | native-json | `rules/vat-ic.json` | `cases` |
| `BR-IC-11` | native-json | `rules/vat-ic.json` | `cases` |
| `BR-IC-12` | native-json | `rules/vat-ic.json` | `cases` |

### Exports (Table 11)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-G-01` | native-java | `BrG01.java` | `cases` |
| `BR-G-02` | shared | `rules/vat-g.json` of `en16931/1.3.16` | `downgrade` |
| `BR-G-03` | shared | `rules/vat-g.json` of `en16931/1.3.16` | `downgrade` |
| `BR-G-04` | native-json | `rules/vat-g.json` | `cases` |
| `BR-G-05` | shared | `rules/vat-g.json` of `en16931/1.3.16` | `downgrade` |
| `BR-G-06` | shared | `rules/vat-g.json` of `en16931/1.3.16` | `downgrade` |
| `BR-G-07` | native-json | `rules/vat-g.json` | `cases` |
| `BR-G-08` | native-java | `BrG08.java` | `cases` |
| `BR-G-09` | shared | `rules/vat-g.json` of `en16931/1.3.16` | `downgrade` |
| `BR-G-10` | native-json | `rules/vat-g.json` | `cases` |

### Not subject to VAT (Table 12)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-O-01` | native-java | `BrO01.java` | `cases` |
| `BR-O-05` | shared | `rules/vat-o.json` of `en16931/1.3.16` | `downgrade` |
| `BR-O-06` | shared | `rules/vat-o.json` of `en16931/1.3.16` | `downgrade` |
| `BR-O-07` | native-json | `rules/vat-o.json` | `cases` |
| `BR-O-08` | native-java | `BrO08.java` | `cases` |
| `BR-O-09` | shared | `rules/vat-o.json` of `en16931/1.3.16` | `downgrade` |
| `BR-O-10` | native-json | `rules/vat-o.json` | `cases` |

### National and margin schemes (Table 13)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-N-02` | native-json | `rules/vat-n.json` | `cases` |
| `BR-N-03` | native-json | `rules/vat-n.json` | `cases` |
| `BR-N-04` | native-json | `rules/vat-n.json` | `cases` |

### Canary Islands tax (Table 14)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-IG-01` | native-json | `rules/vat-ig.json` | `cases` |
| `BR-IG-02` | native-json | `rules/vat-ig.json` | `cases` |
| `BR-IG-03` | native-json | `rules/vat-ig.json` | `cases` |
| `BR-IG-04` | native-json | `rules/vat-ig.json` | `cases` |
| `BR-IG-05` | native-json | `rules/vat-ig.json` | `cases` |
| `BR-IG-06` | native-json | `rules/vat-ig.json` | `cases` |
| `BR-IG-07` | native-json | `rules/vat-ig.json` | `cases` |
| `BR-IG-08` | native-java | `BrIg08.java` | `cases` |
| `BR-IG-09` | native-json | `rules/vat-ig.json` | `cases` |

### Ceuta and Melilla tax (Table 15)

| Rule | How | Where | Oracle |
|---|---|---|---|
| `BR-IP-01` | native-json | `rules/vat-ip.json` | `cases` |
| `BR-IP-02` | native-json | `rules/vat-ip.json` | `cases` |
| `BR-IP-03` | native-json | `rules/vat-ip.json` | `cases` |
| `BR-IP-04` | native-json | `rules/vat-ip.json` | `cases` |
| `BR-IP-05` | native-json | `rules/vat-ip.json` | `cases` |
| `BR-IP-06` | native-json | `rules/vat-ip.json` | `cases` |
| `BR-IP-07` | native-json | `rules/vat-ip.json` | `cases` |
| `BR-IP-08` | native-java | `BrIp08.java` | `cases` |
| `BR-IP-09` | native-json | `rules/vat-ip.json` | `cases` |

## Not applicable, with the reason

| Rule | Why |
|---|---|
| `BR-66` | Not decidable. The statement is that an invoice with attachments states them in BT-125, and what a document says it has attached is BT-125 itself; there is nothing beside it to compare. |
| `BR-CO-05` | Not decidable. Whether a reason code and a free reason text mean the same kind of allowance is not a question an engine answers. |
| `BR-CO-06` | Not decidable, as `BR-CO-5`, for a document level charge or tax. |
| `BR-CO-07` | Not decidable, as `BR-CO-5`, for an invoice line allowance. |
| `BR-CO-08` | Not decidable, as `BR-CO-5`, for an invoice line charge or tax. |

## Withdrawn by this edition

These twelve identifiers are rules of the 2017 edition and not of this one. Every one is a
relaxation: no document becomes invalid because they are gone.

| Rule | What it asked for |
|---|---|
| `BR-CO-17` | Superseded: the category tax amount as taxable amount times rate rounded to two decimals; the surviving per-category rules BR-S-9, BR-IG-9 and BR-IP-9 state it with a tolerance instead. |
| `BR-S-10` | Withdrawn: the ban on an exemption reason in a standard rated breakdown. |
| `BR-Z-10` | Withdrawn: the ban on an exemption reason in a zero rated breakdown. |
| `BR-O-02` | Withdrawn: the ban on seller and buyer VAT identifiers when a line is not subject to VAT. |
| `BR-O-03` | Withdrawn: the ban on seller and buyer VAT identifiers when a document level allowance is not subject to VAT. |
| `BR-O-04` | Withdrawn: the ban on seller and buyer VAT identifiers when a document level charge is not subject to VAT. |
| `BR-O-11` | Withdrawn: the rule that a not-subject-to-VAT breakdown had to stand alone. |
| `BR-O-12` | Withdrawn: the rule that a not-subject-to-VAT breakdown barred lines of any other category. |
| `BR-O-13` | Withdrawn: the rule that a not-subject-to-VAT breakdown barred document level allowances of any other category. |
| `BR-O-14` | Withdrawn: the rule that a not-subject-to-VAT breakdown barred document level charges of any other category. |
| `BR-IG-10` | Withdrawn: the ban on an exemption reason in an IGIC breakdown. |
| `BR-IP-10` | Withdrawn: the ban on an exemption reason in an IPSI breakdown. |
