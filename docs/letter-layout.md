# The letter layout

The default of the two PDF layouts ([`rendering.md`](rendering.md#two-layouts)): the invoice as
the letter a business sends. `esj render` and `PdfRenderer.render(document)` draw it where
neither the caller nor a template names a layout; `--layout generic`, `Layout.GENERIC` or the
template member `"layout": "generic"` ask for the other one ([`cli.md`](cli.md#render)), and a
layout the caller names wins over the one a template names.

## What it places where

| Block | What stands in it |
|---|---|
| address field | the buyer of BG-7 with the address of BG-8, under a sender line taken from BG-4 |
| reference line, under it | BT-1, BT-2, the delivery date BT-72 or the invoicing period of BG-14, BT-9, the references BT-10 to BT-14 and BT-46 the document carries, and the seller's VAT identifier BT-31 and tax registration identifier BT-32 — small labels over their values, in equal columns across the text width. `"information": "block"` puts the same fields in a block beside the address field instead |
| title | the name of the document type behind BT-3, with the invoice number ([below](#document-types)); under it, for every type, the preceding invoices of BG-3 with their dates where the document names one to three: *zur Rechnung RE-2026-0042 vom 03.02.2026*, *to invoices A of …, B of …*. Four or more stay under *Further details* |
| notes | the notes of BG-1, as paragraphs |
| lines | a table: position, item with what hangs under it, quantity with the name of its unit, net unit price, VAT rate, net amount. A hairline closes every row. The sum of the lines BT-106 closes the table, after its last row and on that page only: its figure in the net amount column, its label to the left of it |
| allowances, charges | the document ones of BG-20 and BG-21, each a table |
| totals | a narrow block against the right edge: the sums of BG-22 that follow BT-106, with the VAT breakdown of BG-23 inside them, the amount due last and in bold |
| payment | the terms of BT-20, the means of BG-16 by name, then each account whole (IBAN in groups of four, BIC, holder), the mandate and the card data, and at the right of them the payment code ([below](#the-payment-code)) |
| foot of the first page | the seller's business details in columns, in the band that page reserves for them before it is filled, written compactly — the value first, an identifier followed by the code of its scheme or, where the document states none, by a short word (`HRB 12345 (Registernummer)`), and no label in front of a value that says what it is; what the reference line already carries is not repeated there, and a template may send them to the closing heading instead |
| **Further details** | everything the letter has no place of its own for, with its label and its semantic path, and one line per code written under a name |
| every page after the first | a compact head with the title and the invoice number, under the top margin and over the text; whatever opens the page stands as far under the rule of the head as the first section of page one under the rule of the title; the footer counts the pages, so the number stands once; the table repeats its column header |

## Geometry

The distances a window envelope and a two-hole punch are built to, which DIN 5008 gives for a
business letter. Millimetres from the top left corner of the paper, on A4 and on US Letter alike
— the envelope is what they are for, not the paper. The renderer holds them as points, 1 mm =
72/25.4 pt.

| Block | Where |
|---|---|
| address field, form B (default) | 85 × 45, at 20 from the left and 45 from the top |
| sender line | the first 5 of that field |
| address field, form A | the same field at 27 from the top |
| address field, `none` | no field; the recipient stands at the head of the letter in the flow |
| reference line (default) | in the flow between the margins, under the address zone and over the title |
| information block | from 125 to the right margin, at 45 from the top or under `printedHead`, whichever is lower |
| text | from 20 to 200 |
| white below the text | 25, which the page footer sits in |
| foot of the first page | what the seller's details need, measured before the page is filled and added to the bottom margin: the text of the letter stops above the band, the page footer stays under it, and a sender with more details gets a shorter first page rather than a line of the letter over its own foot. Details taller than half of what that page has for text are not a foot at all and stand under *Further details* instead |
| fold marks, punch mark | at 105 and 210 from the top, and 148.5, in the left margin |

A section a page could hold whole begins on the page it fits on, or on a fresh page — the payment
block and *Further details* are never two rows at a foot and the rest overleaf. One taller than a
page breaks, and the payment block then repeats its heading and keeps every account whole.

A `margins` member of the template is put over these, which a letterhead with a tall band needs
([`templates.md`](templates.md#the-letterhead)). **The left and the right margin hold for every
block of the letter except the address field**, whose place a window envelope fixes. A letterhead
that prints along an edge keeps the text clear of it by stating a margin, and says nothing else.

## Codes under their names

Where a reader does not read the code, the letter writes the name — `H87` becomes `piece` — and
lists the code once under *Further details*, as `Unit H87 = piece`. So the page never hides what
the document says. A code no table names is printed as the code; the generic layout uses none.

Beside a quantity the unit is written the way a quantity is read: the name of more than one
where the quantity is not one (`3 days`, `3 Tage`), and without the qualifier a code list puts
behind a name to tell it from another one (`30 minutes`, not `30 minute [unit of time]`). The
line under *Further details* carries the name the table gives the code, which is the name of
the code list except where the table below says otherwise.

| Table | Codes | English names | German names |
|---|---|---|---|
| `invoice-type` | UNTDID 1001, BT-3 | the dated code list snapshots under `rules/en16931/1.3.16/codelists/`, except the document type 380, which reads *Invoice* as the title of a page | written for this project |
| `unit` | UN/ECE Rec 20 with the Rec 21 extension, BT-130, BT-150 | the same, plus a name for more than one in the case of the name for one; XPP reads *Packaging piece*, because Rec 21 calls it *Piece* and that is what Rec 20 calls H87 | the same |
| `payment-means` | UNTDID 4461, BT-81 | the same | the same |
| `vat-category` | UNTDID 5305 | the same | the same |
| `country` | ISO 3166-1 alpha-2, BT-40, BT-55 and the other addresses | the Unicode CLDR, read once out of the locale data of a Java runtime and checked in | the same |

The tables are checked-in data under `esj-render/src/main/resources/de/bsnsoft/esj/render/names/`,
with their sources in [`sources.md`](sources.md#display-names-of-codes) and the licences in
`NOTICE`. Nothing is read from the locale data of the machine while rendering, which is what keeps
a rendering the same bytes everywhere ([`rendering.md`](rendering.md#determinism-of-the-pdf-rendering)).

## The payment code

Where the invoice states a credit transfer, the payment block carries the QR code a payer's
banking application reads to fill the transfer in: the EPC QR code of the guideline EPC069-12 of
the European Payments Council, known in Germany as the *GiroCode*, drawn as squares rather than
placed as a picture ([`design-decisions.md`](design-decisions.md#one-printed-code-and-it-is-drawn)).
It is on in the letter layout and never drawn in the generic one; `"paymentCode": false` leaves it
out, and `esj render --no-payment-code` wins over the template ([`cli.md`](cli.md#render)).

It carries values the block prints beside it and computes nothing: the account identifier BT-84
and the BIC BT-86 of the first credit transfer account, the beneficiary (the account name BT-85,
else the payee BT-59, else the seller BT-27), the amount due BT-115, and the remittance
information BT-83, else the invoice number BT-1. Every condition of the guideline holds or no
code is drawn and nothing is said: BT-81 is 30 or 58, BT-5 is `EUR`, BT-84 is a well-formed IBAN
by the check digits of ISO 13616, BT-115 is between 0.01 and 999999999.99, and the payload fits
its bound. Three elements the payload can do without, and *Further details* says which gave way:
a beneficiary over 70 characters is written to that length, a remittance information over 140
gives way to the invoice number, a BT-86 that is not a BIC by ISO 9362 is left out. Where the
document states more than one account or instruction, the caption names the one it pays into.
A credit note or a self-billed invoice gets none ([below](#document-types)).

## Document types

BT-3 gives the letter its title. For the types the table of names calls a credit note — 81, 83,
261, 262, 296, 308, 381, 396, 420, 502, 503, 532 — it gives three words as well: the reference
line calls BT-1 and BT-2 the number and the date of a credit note, and the totals close with the
*amount credited* (BT-115); BT-9 keeps its name. The payment code asks its reader to pay, so a
letter whose reader does not pay gets none, whatever the template asks: a credit note, where the
seller pays the buyer, and a self-billed invoice — 389, 471, 473, 500, 501, 527 — which the buyer
issues and the seller reads; it keeps the title and the words of an invoice. The payment block
stands as the document states it. Every other type — a corrected invoice (384), a partial or a
construction invoice (326, 386, 875–877) — is the letter of an invoice under the title that
names it.

## The content rule

**Every term occurrence of the document reaches a page**, in this layout as in the generic one,
and that is a test over the conformance corpus, both languages and both papers
([`rendering.md`](rendering.md#every-value-is-on-a-page)). A value the layout has a place for
stands in that place; every other one stands under *Further details* with its label and its
path. A value that did not fit where it was meant to go — a recipient longer than the address
field — goes there too rather than nowhere.

## What it never does

- derive: no carried-forward sum, no computed gross figure, no sentence that combines two
  values into a statement. What is done to a value is formatting: the number picture and the
  date picture of the language, a percentage with the decimal places the document wrote and no
  others — `19 %`, `10,7 %`, nothing rounded — and an account identifier that is an IBAN in
  groups of four;
- hide: nothing is dropped for being unexpected, and a code written under a name is listed;
- reorder: a template decides the look, never the order of the blocks and never the content;
- invent a core term for a gross figure. The letter is net, BT-114 is the rounding amount of
  the standard, and the displayed figures of a model extension reach the page through the
  places the template gives them ([`templates.md`](templates.md)).

## What the template decides

```json
"layout": "letter",
"letter": { "addressWindow": "din5008-b", "information": "line", "foldMarks": true,
            "holeMark": true, "sellerDetails": "footer", "paymentCode": true }
```

| Member | Values |
|---|---|
| `addressWindow` | `din5008-b` (default), `din5008-a`, `none` |
| `information` | `line` (default), the reference line in the flow; or `block`, the block beside the address field, for paper whose top right is free |
| `printedHead` | with `block`: how far below the top edge the printed head of the letterhead reaches, in points, 0 to 400. The block starts under it |
| `foldMarks`, `holeMark` | `true`, `false` (default); a letterhead carrying its own wants neither |
| `sellerDetails` | `footer` (default), or `details` for a letterhead that prints them already — they then stand under *Further details*, where details too tall for a foot go as well |
| `paymentCode` | `true` (default), `false` to leave the code of a credit transfer out |

The reference line takes the width the margins leave it, in as many columns as fit — five on A4
with the full text width, fewer where a margin keeps the text off a printed edge — and runs on
into further rows. A value wider than its column takes two columns or is wrapped inside its own
cell, never over the cell beside it. Why it is the default, and why the foot is a distance the
template hands over, is in [`design-decisions.md`](design-decisions.md#two-layouts-one-content-rule).

Everything else a template says — letterhead, logo, colours, fonts, margins, the places for the
terms of a model extension — works as it does in the generic layout, and [`templates.md`](templates.md)
is the reference. `examples/templates/letter.json` is the example the README renders.
