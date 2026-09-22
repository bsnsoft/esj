# Branded render templates

*Part of [EN16931 Semantic JSON](../README.md).*

```java
RenderTemplate template = RenderTemplate.read(templateFile);
byte[] pdf = new PdfRenderer().render(document, RenderOptions.defaults().with(template));
```

From the command line it is `esj render --template`, [`cli.md`](cli.md#render).

A template is a JSON file and the files it names beside it. It decides what the page looks
like and which of the two layouts draws it, never the order of the blocks and never the
content: every business term of the document reaches a page either way, and the figures of
EN 16931-1 stay the net ones ([`rendering.md`](rendering.md)).

## The file

`schema/render-template.schema.json` is the shape of it; the examples are under
[`examples/templates/`](../examples/templates/README.md).

| Member | What it does |
|---|---|
| `template` | `"esj-render-template/0.1"`, the marker this version reads |
| `name` | what the template calls itself; it appears nowhere on the page |
| `layout` | `"generic"` or `"letter"`, the page layout ([`letter-layout.md`](letter-layout.md)). Default `generic`; a layout the caller names wins |
| `letter` | what the letter layout leaves to the sender, [below](#the-letter); the generic layout ignores it |
| `letterhead.first`, `letterhead.following` | `{"file": …, "page": n}`: one page of a PDF, or a PNG or JPEG stretched over the page. Only `first` given: the same sheet on every page |
| `logo` | `{"file": …, "x": …, "y": …, "width": …, "height": …, "pages": "first"⎮"all"}`, in points, `x` from the left edge and `y` below the top edge |
| `colors` | `text`, `muted`, `rule`, `heading`, `tableHeaderFill`, `tableHeaderText`, each `#rrggbb` in sRGB; one left out keeps the grey of the generic layout |
| `fonts` | `{"regular": …, "bold": …}`, TrueType; both weights or neither |
| `margins.first`, `margins.following` | `top`, `bottom`, `left`, `right` in points, put over the margins of the layout; `bottom` is at least 25, which is what holds the page footer |
| `extensionTerms` | the places the layout gives to terms of a model extension |

Measurements are PostScript points, 1/72 inch: A4 is 595 × 842.

**A reference is a file beside the template.** A name that is absolute, that leaves the
template's directory or that is not a plain relative path is refused rather than followed.
`RenderTemplate.of(byte[], Files)` is the entry for a caller who keeps templates elsewhere.

**The left and the right margin are the same on both page kinds**, because a table lays its
columns out once and keeps them across every page it runs onto; a template that writes two
different ones is refused and says why. In the letter layout they hold for every block but
the address field ([`letter-layout.md`](letter-layout.md#geometry)), so a letterhead that
prints along an edge keeps the text clear of it with `right` or `left` alone.

**The page footer sits inside the bottom margin, 22 points below its top** — not at a fixed
distance from the edge of the paper — and its descenders reach 3 points further, so a
`bottom` under 25 points is refused. That is how a letterhead hands over its printed foot:
for one *h* points tall, write `margins.*.bottom` = *h* + 22 + air, the air covering those
3 points and a little more. A printed foot of 20 mm (57 points) with 12 points of air asks
for 91. Where the letter layout writes the seller's details in the foot of the first page,
they stand above the page footer and the whole band is above the `bottom` the template wrote,
so the order from the edge of the paper upwards is: the printed foot, the page footer, the
details, the text. Where a page cannot give that band, the details stand under
the closing heading instead ([`letter-layout.md`](letter-layout.md#geometry)).

## The letter

`"layout": "letter"` draws the invoice as a business letter and `"letter"` is what that layout
leaves to the sender — the address field, whether the head data stands in the reference line
(the default) or in the block beside the address field and how far down the printed head of
the letterhead reaches, the fold and punch marks, whether the seller's details stand along
the foot, and whether the payment block carries the EPC QR code of a credit transfer.
[`letter-layout.md`](letter-layout.md#what-the-template-decides) is the reference;
`examples/templates/letter.json` is the example.

## The letterhead

A letterhead PDF is imported **as a form object with its own resources** — its fonts, its
images, its colour spaces — once per rendering, and every page references that one object, so a
hundred-page invoice on a letterhead is the size of a one-page invoice on it. A letterhead of
another paper is scaled onto the page. The margins keep the text out of the printed matter: the
compact head of a following page stands under `margins.following.top` and the text of that page
under the head, so a second sheet printed across its own head needs that one distance and
nothing else. The example letterhead of this repository shows what that asks for: it keeps to the head of the sheet — a
name, a hairline, a mark — and puts nothing behind the text and nothing at the foot, which is
where the letter layout writes the sender's own details.

A rendering stays PDF/A-3b, and **what the letterhead brought is part of that file**: a
letterhead whose fonts are not embedded, or whose colours are device-dependent without an
output intent, makes a rendering fail a check the same layout passes without it. `esj render`
does not validate the letterhead; check a template of your own against veraPDF once, as the
examples of this repository are checked on every build.

Fonts a template brings are embedded in every file it renders, so their licence has to permit
that. The vendored faces are Liberation Sans under the SIL Open Font License; SIL OFL and
Apache-2.0 faces are the safe ones.

## Terms of a model extension

EN 16931 invoices are net invoices, and the gross figures a consumer was shown are recorded by
a model extension rather than by a core business term. A template says which of those terms it
has a place for:

```json
"extensionTerms": [
  { "term": "BT-B2C-001", "position": "line.unitPrice", "type": "UnitPriceAmount",
    "label": { "en": "Gross unit price", "de": "Einzelpreis brutto" } },
  { "term": "BT-B2C-010", "position": "totals", "type": "Amount",
    "label": { "en": "Gross total", "de": "Bruttogesamtbetrag" } }
]
```

| `position` | Where the value goes |
|---|---|
| `line.unitPrice` | a column beside the net unit price of an invoice line |
| `line.vat` | a column beside the VAT category and rate of a line |
| `line.amount` | a column beside the net amount of a line |
| `totals` | a row among the totals of the document |

- **Only an extension term may be placed.** A business term of EN 16931-1 has a place in the
  layout already, and a template that moved one would be a template deciding what the standard
  means.
- **A place is filled only where the document fills it**: a column appears when an invoice line
  carries the term, the row when the document does, so one template renders both kinds.
- **Every placed figure is marked** as a figure that was displayed, in the column header or the
  row label, and one line under the block says what that means: figures out of a model
  extension, beside the net amounts, the VAT breakdown and the totals of the standard.
- **`label` and `type` travel with the template**, so a build whose registry has never heard of
  the extension still shows the figure under a name and writes it down as an amount. Where the
  registry does know the term, the registry decides the type. The terms
  `examples/templates/gross.json` places are those of the B2C extension ([b2c.md](b2c.md)),
  which `esj render --extension b2c` loads.
- BT-114 is the invoice rounding amount of EN 16931-1 in a branded rendering as in a generic
  one. Nothing of the template writes into a core term, and nothing derives a gross figure the
  document does not carry.

## Determinism

The same document, options and template give the same bytes: no clock, no locale, no iteration
order, a letterhead imported once, and a template read twice that is the same template.
`examples/templates/` is checked against the code that draws it on every build.

## What a template cannot do

Move a section, rename a business term, hide a value, compute a figure, or change what the
rendering says. Those are the layout, and the layout is one.
