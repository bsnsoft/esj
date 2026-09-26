# Code list snapshots of the pack `en16931-2026/0.1`

*Part of [EN16931 Semantic JSON](../../../../README.md). See
[`../../../README.md`](../../../README.md) for the rule language,
[`en16931/1.3.16/codelists/SOURCES.md`](../../../en16931/1.3.16/codelists/SOURCES.md) for the
two rules that decide where a snapshot may come from, and
[`conformance/rules-2026/coverage.md`](../../../../conformance/rules-2026/coverage.md) for
what this pack covers.*

This pack carries two snapshots of its own and reads the other fifteen from the pack of the
2017 edition. Where the edition names the same published list for the same term, a second
copy of the file would be a second thing to keep equal by hand, so the manifest writes
`{"day": …, "from": "en16931/1.3.16"}` and both packs decide membership against the same
bytes. The two files here are the two lists this edition asks a different question of.

| List | Day | Where it comes from | What the publisher says about reuse |
|---|---|---|---|
| `iso-4217` | 2026-09-21 | SIX Group, the ISO 4217 maintenance agency, [`list-one.xml`](https://www.six-group.com/dam/download/financial-information/data-center/iso-currrency/lists/list-one.xml) | The page offers the list for download and states no licence. **Uncertain**, and recorded as such: the snapshot is taken and the question is left open rather than answered by silence. |
| `untdid-5305` | 2026-09-21 | The nine codes and their descriptions are the European Commission's listing of the subset EN 16931 admits, [workbook v17b](https://ec.europa.eu/digital-building-blocks/sites/download/attachments/467108974/EN16931%20code%20lists%20values%20v17b%20-%20used%20from%202026-05-15.xlsx), sheet `5305`; the tenth is this edition's own table | The Commission page states no licence and the workbook says it lists codes rather than replacing each publisher's own publication. Reuse of Commission documents is generally governed by Decision 2011/833/EU, which the page does not cite. **Uncertain**, recorded as such. |

## Why these two are not the ones of the other pack

**The currency list carries a second column here.** The 2017 edition bounds every amount at
two fraction digits; this one bounds it at the minor unit of the currency the invoice is
written in (clause 6.5.13, Table 28), and the minor unit is a fact the publisher of the
currency list states beside each code. The snapshot therefore carries `minorUnit` on the 165
codes the publisher gives one — a fund or a precious metal has none, and a rule that asked
about one of those is not decided rather than guessing a number. The list of codes itself is
the same as in the snapshot of the other pack, taken from the same page.

**The VAT category list is one code longer.** Table 5 of this edition admits a tenth
category, `N`, for national VAT schemes and margin schemes. The workbook of the European
Commission of 2026-05-15 was published for the earlier edition and does not carry the code,
so the nine it does carry keep the descriptions it gives them and the tenth carries the one
this edition's own table gives it. The row of the snapshot says so, which is the point: a
reader can see which of the ten came from which publication.

## The five codes that are not a list

Four rules of this pack — the one that excepts margin scheme categories from the tax amount
and the three that oblige a seller tax identifier under a national or a margin scheme — ask
whether a category code is one of `D`, `F`, `I`, `J` or `N`. Those five are written into the
rules themselves and not into a snapshot. They are a set this edition states in clause
6.4.3.4.13 rather than a list a body publishes, so a file with a publisher and a retrieval day
would be a file whose header was untrue.
