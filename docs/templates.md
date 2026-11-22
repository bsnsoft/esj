# Branded render templates

*Part of [EN16931 Semantic JSON](../README.md).*

```java
RenderTemplate template = RenderTemplate.read(templateFile);
byte[] pdf = new PdfRenderer().render(document, RenderOptions.defaults().withTemplate(template));
```

A template is a JSON file and the files it names beside it. It decides what the page looks
like and which of the two layouts draws it. It never decides the order of the blocks or the
content: every business term of the document reaches a page either way, and the figures of
EN 16931-1 stay the net ones ([`rendering.md`](rendering.md)). On the command line, use
`esj render --template` ([`cli.md`](cli.md#render)).

## The file

`schema/render-template.schema.json` gives its shape; the examples are under
[`examples/templates/`](../examples/templates/README.md).

| Member | What it does |
|---|---|
| `template` | `"esj-render-template/0.1"`, the marker this version reads |
| `name` | what the template calls itself; it appears nowhere on the page |
| `layout` | `"generic"` or `"letter"`, the page layout ([`letter-layout.md`](letter-layout.md)). Default `letter`; a layout the caller names wins |
| `letter` | what the letter layout leaves to the sender, [below](#the-letter); the generic layout ignores it |
| `letterhead.first`, `letterhead.following` | `{"file": …, "page": n}`: one page of a PDF, or a PNG or JPEG stretched over the page. Only `first` given: the same sheet on every page |
| `logo` | `{"file": …, "x": …, "y": …, "width": …, "height": …, "pages": "first"⎮"all"}`, in points, `x` from the left edge and `y` below the top edge |
| `colors` | `text`, `muted`, `rule`, `heading`, `tableHeaderFill`, `tableHeaderText`, each `#rrggbb` in sRGB; one left out keeps the grey of a rendering without a template |
| `fonts` | `{"regular": …, "bold": …}`, TrueType; both weights or neither |
| `margins.first`, `margins.following` | `top`, `bottom`, `left`, `right` in points, put over the margins of the layout; `bottom` is at least 25, which is what holds the page footer |
| `extensionTerms` | the places the layout gives to terms of a model extension |

Measurements are PostScript points, 1/72 inch: A4 is 595 × 842.

**A reference is a file beside the template.** The renderer refuses a name that is absolute,
that leaves the template's directory or that is not a plain relative path, and does not follow
it. It also refuses a name that reaches a file outside that directory through a symbolic link,
and anything that is not a regular file. It refuses a file by its size before reading it, past
32 MiB, and an image by the size its header states before decoding it, past 36 million pixels
(a page at 600 dots per inch). A template is the renderer's own configuration, and these bounds
do not make it anything else. They turn a link that points elsewhere, or a scan at the wrong
resolution, into an error that names the file. If you keep templates elsewhere, use
`RenderTemplate.of(byte[], Files)`; the bound on an image holds for what it answers too.

**The left and the right margin are the same on both page kinds.** A table lays its columns out
once and keeps them across every page it runs onto. The renderer refuses a template that writes
two different ones and says why. In the letter layout they hold for every block but the address
field ([`letter-layout.md`](letter-layout.md#geometry)). So if your letterhead prints along an
edge, `right` or `left` alone keeps the text clear of it.

**The page footer sits inside the bottom margin, 22 points below its top**, not at a fixed
distance from the edge of the paper. Its descenders reach 3 points further, so the renderer
refuses a `bottom` under 25 points. That is how a letterhead hands over its printed foot: for
one *h* points tall, write `margins.*.bottom` = *h* + 22 + air, the air covering those 3 points
and a little more. A printed foot of 20 mm (57 points) with 12 points of air asks for 91. Where
the letter layout writes the seller's details in the foot of the first page, they stand above
the page footer, and the whole band is above the `bottom` the template wrote. From the edge of
the paper upwards, the order is: the printed foot, the page footer, the details, the text.
Where a page cannot give that band, the details stand under the closing heading instead
([`letter-layout.md`](letter-layout.md#geometry)).

## The letter

The letter layout is the default, and `"layout": "letter"` names it explicitly. `"letter"` holds
what that layout leaves to the sender:

- the address field, and the fold and punch marks;
- whether the head data stands in the reference line (the default) or in the block beside the
  address field, and how far down the printed head of the letterhead reaches;
- whether the seller's details stand along the foot, and whether the payment block carries the
  EPC QR code of a credit transfer.

[`letter-layout.md`](letter-layout.md#what-the-template-decides) is the reference;
`examples/templates/letter.json` is the example.

## The letterhead

The renderer imports a letterhead PDF **as a form object with its own resources** (its fonts, its
images, its colour spaces) once per rendering, and every page references that one object. So a
100-page invoice on a letterhead is the size of a 1-page invoice on it. A letterhead of another
paper is scaled onto the page. The margins keep the text out of the printed matter. The compact head
of a following page stands under `margins.following.top`, and the text of that page under the head,
so a second sheet printed across its own head needs that one distance and nothing else. The example
letterhead of this repository shows what that asks for. It keeps to the head of the sheet (a name, a
hairline, a mark) and puts nothing behind the text and nothing at the foot, where the letter layout
writes the sender's own details.

A rendering stays PDF/A-3b, and **what the letterhead brought is part of that file**. A letterhead
whose fonts are not embedded, or whose colours are device-dependent without an output intent, makes
a rendering fail a check that the same layout passes without it. `esj render` does not validate the
letterhead. Check a template of your own against veraPDF once, as the build checks the examples of
this repository every time. Every file a template renders embeds the fonts it brings, so their
licence has to permit that. The vendored faces are Liberation Sans under the SIL Open Font License;
SIL OFL and Apache-2.0 faces are the safe ones.

## Terms of a model extension

EN 16931 invoices are net invoices. A model extension records the gross figures a consumer was
shown, not a core business term. A template says which of those terms it has a place for:

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
- **A place is filled only where the document fills it.** A column appears when an invoice line
  carries the term, the row when the document does, so one template renders both kinds.
- **Every placed figure is marked** as a figure that was displayed, in the column header or the
  row label. One line under the block says what that means: figures out of a model extension,
  beside the net amounts, the VAT breakdown and the totals of the standard.
- **`label` and `type` travel with the template**, so a build whose registry has never heard of
  the extension still shows the figure under a name and writes it down as an amount. Where the
  registry knows the term, the registry decides the type. The terms
  `examples/templates/gross.json` places are those of the B2C extension ([b2c.md](b2c.md)),
  which `esj render --extension b2c` loads.
- BT-114 is the invoice rounding amount of EN 16931-1 in a branded rendering as in an unbranded
  one. Nothing of the template writes into a core term, and nothing derives a gross figure the
  document does not carry.

## Determinism

The same document, options and template give the same bytes: no clock, no locale, no iteration
order, a letterhead imported once, and a template read twice that is the same template. The
build checks `examples/templates/` against the code that draws it every time.

## What a template cannot do

Move a section, rename a business term, hide a value, compute a figure, or change what the
rendering says. Those belong to the layout, and the layout is one.
