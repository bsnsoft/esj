# Display names of codes

What the letter layout of `esj-render` writes where a document carries a code, in the two
languages the renderer knows. Data of this repository: a rendering is the same bytes on every
machine, so nothing here is read from the locale data of the runtime.

| File | Codes | English names | German names |
|---|---|---|---|
| `invoice-type.json` | UNTDID 1001 (BT-3) | the dated snapshot `rules/en16931/1.3.16/codelists/untdid-1001/2026-09-19.json`, except 380, which reads *Invoice* because it is the title of a page | written for this project |
| `unit.json` | UN/ECE Rec 20 with the Rec 21 extension (BT-130, BT-150) | the snapshots `unece-rec20/2026-09-19.json` and `unece-rec21/2026-09-19.json`, with a name for more than one under `enPlural`, except XPP, which reads *Packaging piece* because Rec 21 calls it *Piece* and that is what Rec 20 calls H87 | written for this project, with `dePlural` beside it |
| `payment-means.json` | UNTDID 4461 (BT-81) | the snapshot `untdid-4461/2026-09-19.json` | written for this project |
| `vat-category.json` | UNTDID 5305 (BT-95, BT-102, BT-118, BT-151) | the snapshot `untdid-5305/2026-09-19.json` | written for this project |
| `country.json` | ISO 3166-1 alpha-2 (BT-40, BT-55, BT-69, BT-80, BT-159) | the Unicode CLDR | the Unicode CLDR |

A code no file names is printed as the code.

A unit beside a quantity is written with the name of more than one where the quantity is not
one, and without the qualifier a code list puts behind a name to tell it from another one —
`30 minutes`, not `30 minute [unit of time]`. The line under *Further details* carries the name
of the table word for word, so nothing of the fact is lost by leaving it off the quantity. No
two codes of one table read alike in one language, in either number; `DisplayNamesTest` checks
that as well.

`country.json` was generated once by `CountryNames`, a test-scope class of this module, out of
the locale data of the Java runtime its header records, and checked in. To write it again, run
that class with this directory as its argument, on the runtime whose names the project wants to
carry. `CountryNamesTest` regenerates and compares on that runtime and only reads the file on
any other. The Unicode terms and the attribution are in the `NOTICE` of this repository and in
`docs/sources.md`.

`DisplayNamesTest` compares the English names against the snapshots above, code for code, so a
name printed here as a fact is the fact the snapshot carries.
