# What stands behind the rules of the EN 16931-1:2026 pack

*Part of [EN16931 Semantic JSON](../../README.md). [`coverage.md`](coverage.md) is which rules
the pack carries, [`rules/README.md`](../../rules/README.md) is the language they are written
in, and this page is the evidence for each of them. The figures beside it in
[`ledger.json`](ledger.json) are recomputed on every build and the page is held to them.*

**No official validation artefact is published for this edition.** The artefact releases of
CEN/TC 434 end at 1.3.16 and validate EN 16931-1:2017+A1:2019/AC:2020. Nothing here is
corroborated by an official artefact, and no figure on this page is a conformance claim.

Each of the 252 rules says what stands behind it, and the two answers are measured differently:

| Oracle | Rules | Measured by |
|---|---:|---|
| `downgrade` | 108 | the official Schematron of release 1.3.16, over the document written back to the 2017 edition |
| `cases` | 144 | hand-computed cases of this project, and nothing else |

## The unchanged rules, measured by the downgrade

A rule the edition leaves unchanged is the rule of `en16931/1.3.16`, taken over by identifier —
except `BR-CO-10`, `-11`, `-12` and `-15`, written out only to round to the minor unit of the
currency. What has to be shown is that it decides a document of this edition the way the
official artefacts decide the same document of the earlier one.

| | |
|---|---|
| Input | the 448 mutations of the default pack, [`conformance/rules/mutations`](../rules/mutations/mutations.json) |
| Route | each mutated instance read into ESJ, written up with `esj upgrade --to 2026`, and written back |
| Under test | the 108 rules of this pack whose oracle is `downgrade`, over the upgraded document |
| Oracle | the identifiers of category `EN-BR`, `EN-DEC` and `EN-CL` the official Schematron reports on the same bytes |

The round trip is what lets the artefact's verdict carry over: 447 of the 448 mutations write
up to this edition and back to canonical bytes identical to the ones they started from. The
one that does not is `br-46-cii`, which removes the VAT category tax amount: this edition
admits a VAT breakdown without it and the 2017 edition does not, so the document cannot be
written back and is left out of the comparison.

| Rules whose oracle is the downgrade | |
|---|---:|
| Named on both sides wherever either side names them | **100** |
| Named on one side and not on the other | **7** |
| Named on neither side by any mutation | **1** |
| Open — a difference nobody has accounted for | **0** |

436 of the 447 mutations are ones where every one of the 108 rules is named on both sides or
on neither.

**Nothing drifted.** Over all 447 mutations and all 108 rules, this pack over the upgraded
document names exactly what the pack of the default edition names over the document it was made
from — `findingsThatDifferFromTheDefaultPack` is 0. The seven differences against the official
artefacts are therefore differences that pack already has: `BR-01`, `BR-27`, `BR-51`,
`BR-CL-01`, `BR-CL-04`, `BR-CO-10` and `BR-CO-15` each differ only on mutations whose `cause`
in the default set says why, and [`../rules/ledger.md`](../rules/ledger.md) explains each
cause. `BR-CO-19` is the rule no mutation exercises, there as here.

What this measurement cannot show is that the two texts were read the same way. It shows that
a rule carried over decides what it decided, not that the edition left it unchanged; that is
this project's reading of Annex A. It is evidence for these 108 rules and for no other rule of
the pack.

## The rules written out, measured by cases

A rule this pack writes out rather than takes over has no oracle at all: every rule this
edition changed or adds, the fraction digit rules, and, of the rules it leaves unchanged, those
that cannot be taken over as written — `BR-30` and `BR-CO-20`, whose invoice line period moved
into the invoice line delivery group (BG-37), `BR-IC-11`, which Annex A does not list but whose
statement now counts the line level delivery date and period, and those of the `BR-IG-*` and
`BR-IP-*` families, which carry this edition's identifiers. Each is weighed by
[`cases/cases.json`](cases/cases.json): a document of the conformance corpus written up to
this edition that the rule is silent on, and the same document with one thing changed that it
speaks on — more than one pair where a reading of the rule needs showing, with the identifiers the pack reports about each recorded beside them.
`ledger.json` names the outcome per rule.

| Rules whose oracle is `cases` | |
|---|---:|
| With a case | **143** |
| Cases | **157** |
| That no document of this format can make speak | **1** |

The one is `BR-CO-20`, which asks that an invoice line period state a start date or an end
date: a business group of an ESJ document exists exactly when it carries a value, and that
group carries no business term other than those two dates, so a period with neither of them is
no group at all. The file says so beside the rule.

`BR-DEC-15` bounds the total VAT amount in the accounting currency (BT-111) by the minor unit
of that currency (BT-6), so an invoice that states BT-111 and names no BT-6 is bounded by no
rule: `BR-53` asks for the total where the currency is named, and nothing asks the reverse.

A case shows that the engine decides what the rule was written to say. It never shows that
what was written is what the standard means.

## The corpus

The conformance corpus is 86 invoices the official validator accepts and eleven documents of
the format, all of them written for the 2017 edition. All 97 write up to this edition, and what
this pack then says about them is recorded finding by finding in `ledger.json`.

| | |
|---|---:|
| Documents | 97 |
| Written up to this edition | 97 |
| Without a finding | 11 |
| Findings | 206 |

No rule the edition leaves unchanged speaks about an upgraded document unless the default pack
speaks about the document it was made from. The five that do — `BR-48`, `BR-CL-13`,
`BR-CO-26`, `BR-Z-02` and `BR-Z-05` — are named on both sides of every one of the 97.

The rules the edition changed or adds are the rest, and every identifier of them is here:

| Rule | Documents | What the edition changed |
|---|---:|---|
| `BR-71` | 55 | The buyer identifier (BT-46) carries its identification scheme; in the 2017 edition the scheme was optional. |
| `BR-70` | 37 | The same for the seller legal registration identifier (BT-30). |
| `BR-73` | 20 | The same for the payee identifier (BT-60). |
| `BR-75` | 20 | The same for the deliver to location identifier (BT-71). |
| `BR-69` | 16 | The same for the seller identifier (BT-29). |
| `BR-72` | 2 | The same for the buyer legal registration identifier (BT-47). |
| `BR-CO-40` | 31 | Delivery information stands at header level or at line level and not at both, and this edition puts the invoice line period inside the new invoice line delivery group (BG-37). An invoice that stated delivery information in the header and a period on a line satisfied the 2017 edition and states both levels here. |
| `BR-DEC-44` | 5 | The item net price (BT-146) carries at most as many fraction digits as the minor unit of the invoice currency and two more. The 2017 edition bounded no unit price; the fifth document is `examples/b2c-gross.esj.json`, whose net prices are cut from gross ones at six fraction digits. |
| `BR-CO-25` | 3 | Nothing: the edition moved the payment term text into the new payment terms group (BG-33) and the rule with it. The default pack reports it on the same three documents. |
| `BR-CO-16` | 1 | Nothing that these documents reach: the edition adds the charge amount collected on behalf of a third party (BT-179) to the sum. The default pack reports it on the same document. |

The first six are the migration point this edition's own upgrade report names: an
identification scheme is not derivable from anything a document carries, so `esj upgrade`
reports each one and invents none. They speak on exactly the documents and terms that report
names, which a test holds. The seventh rule of the kind, `BR-74` on the payee legal
registration identifier (BT-61), finds nothing: every document that carries one states its
scheme. `BR-CO-40` and `BR-DEC-44` are the two a reader of the edition should know about before
writing an invoice against it; the last two say what the default pack says about the same
documents and are here because the edition rewrote the rule.

Two rules of the default pack have no counterpart here and are silent where it speaks:
`BR-CL-10` on one instance and `BR-CL-21` on one. Both decide an identification scheme against
a code list this edition names differently; [`coverage.md`](coverage.md) says which.
