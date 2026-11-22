# Rendering

*Part of [EN16931 Semantic JSON](../README.md).*

`esj-render` writes an ESJ document for a human reader: HTML through somebody else's
stylesheet, PDF through a layout of this project's own.

| | `HtmlRenderer` | `PdfRenderer` |
|---|---|---|
| Layout | the KoSIT XRechnung visualization, vendored | this project's, driven by the registry |
| Result | one self-contained HTML page | a PDF/A-3b file, fonts embedded |
| Options | language | language, page size, template, page bound |
| Attachments | carried into the page whole | named, measured, not printed |
| Determinism | same document, same string | same document, same bytes |

## The HTML rendering is not this project's design

```text
  SemanticDocument
        │  XrExporter (esj-xr)
        ▼
  XR representation                urn:ce.eu:en16931:2017:xoev-de:kosit:standard:xrechnung-1
        │  xsl/esj-html.xsl        this project's, imports the next line, overrides one rule
        │  xrechnung-html.xsl      vendored unmodified, Apache-2.0, tag v2026-08-31
        │  + l10n/de.xml | en.xml  labels, date picture, decimal picture
        │  + viewer.css, viewer.js, FileSaver  inlined by the stylesheet itself
        ▼
  one self-contained HTML file
```

The layout, the five tabs, the labels and the two languages come from the HTML visualization
of the KoSIT XRechnung project, vendored and run unmodified. This project overrides one rule:
the one a document from a stranger could use to decide where a link goes (*What a document
from a stranger can do to its own HTML rendering*, below).

`HtmlRenderer.render(document)` returns the whole page as a string. The only option it reads is
the language. A branded template belongs to the PDF renderer, and the HTML renderer ignores it.

## Every rendering is net, and derives nothing

EN 16931-1 core terms keep their net semantics in both PDF layouts and in the HTML page. Each
shows the net amount and the net unit price of every invoice line, the VAT breakdown per
category (BG-23) and the totals of BG-22 up to the amount due, gross among them (BT-112,
BT-115). The renderers invent no core business term for a gross line or unit value and write
no derived figure into a core term. BT-114 is the invoice rounding amount of the standard, not
a balancing field, and no sentence combines two values into a statement. Gross figures for
consumers belong to a model extension and to a renderer you asked for them
([`templates.md`](templates.md)). They are never inferred back from the core
([`design-decisions.md`](design-decisions.md)).

The `extensions` subtree holds data that belongs to no business term. It has no place in the
XR representation and therefore none in the HTML rendering. `HtmlRenderer.renderWithReport`
names it in an `ExportReport`, with everything else that did not reach the stylesheet. A PDF
names the owner of every extension subtree at the end and says that it does not print the data
below it.

## What the HTML rendering shows

**Every business term of the registry**, in both languages, together with the supplementary
components: the scheme and the scheme version of an identifier, the media type and the file
name of an attachment. Tests assert this on a document that carries a value at each of the 203
value paths of the core model and the XRechnung extension, on every instance of the
conformance corpus and on every example of the repository. (203 value paths, 208 terms: a term
that stands in two groups has a path in each; `PdfCoverageTest` pins the number.)

- **Values are formatted for a reader**, through the stylesheet's number picture (`1.234,56` in
  German, `1,234.56` in English, at least two decimal places) and its date picture, `31.12.2026`
  and `2026-12-31`. The page does not show the canonical spelling of the document.
- **The values are not in canonical path order.** The stylesheet arranges an invoice over five
  tabs of its own and shows the buyer before the seller.
- **Attachments are carried whole.** BT-125 goes into the page in a hidden element that a
  download link reads out, so a rendering is larger than the attachments of the invoice. A
  corpus instance with a 200 kB attachment renders to more than that, against about 100 kB
  without one.

## What a document from a stranger can do to its own HTML rendering

The renderer writes the text of an invoice into the page as text. The serializer escapes it, so
a description containing `<script>` reaches the reader as those characters. The only scripts in
a rendering are the two the stylesheet inlines, whatever the invoice says. A fixture built to
break this asserts it.

The vendored stylesheet writes a value somewhere other than into text in two places, and this
module overrides the one template rule that writes them. The override lives in
`xsl/esj-html.xsl`, which imports the vendored stylesheet. The vendored bytes, their digests
and their attribution stay untouched, because import precedence is what `xsl:import` is for.

- **BT-124, the external document location, becomes the target of a link.** It is a link where
  its scheme is `http`, `https` or `mailto`, and plain text where it is anything else. A reader
  sees a `javascript:` URI and cannot follow it. The value is on the page either way: a
  rendering that dropped it would hide what the invoice says.
- **The download link of an attachment calls a script with values out of the document.** BT-122,
  the media type and the file name stand in the arguments of a JavaScript call inside single
  quotes. An HTML serializer does not escape an apostrophe inside an attribute delimited by
  double quotes. So the override escapes the three values for the string literal they stand
  in, and an apostrophe in a file name stays an apostrophe in a file name.

**Characters that direct the reading order** become a space on the way in, as in the PDF
rendering and the report: the right-to-left override and its family, U+061C, U+200E, U+200F,
U+202A–U+202E, U+2066–U+2069 and U+FFF9–U+FFFB. They are invisible and make a line read
differently from the text the document stores. U+007F and the C1 controls become a space too,
and U+0085, U+2028 and U+2029 become line feeds. One class of `esj-render` holds these sets
for every form.

`UntrustedContentTest` asserts all of this. A newer tag of the stylesheet that changed
something fails the build instead of surprising you.

**What you should do.** A rendering is a document built from one somebody else wrote. Serve it
from an origin of its own, in a sandboxed frame, under a content security policy that forbids
inline script where you do not need the download link. Never paste it into a page that holds a
session. The renderer is a display function, not a sanitizer of arbitrary HTML.

## Determinism of the HTML rendering

The same document rendered twice gives the same string, character for character: no timestamp,
no producer string, no identifier that counts up, no iteration order a hash decides. A
rendering is a function of the document, the language and the version of the stylesheet this
module ships. Tests assert this for every instance of the corpus.

## What the HTML renderer may reach

The stylesheets reach outside their own files in three ways, and `esj-render` answers exactly
those three out of its own classpath. The entry point is `xsl/esj-html.xsl`, which imports the
vendored one:

| Reference | What the stylesheet asks for | What is answered |
|---|---|---|
| `xsl:import` | `xrechnung-html.xsl`, `common-xr.xsl`, transitively `functions.xsl` | those three files |
| `doc()` | `l10n/de.xml`, `l10n/en.xml` | those two files |
| `unparsed-text()` | `xrechnung-viewer.css`, `xrechnung-viewer.js`, `FileSaver-v2.0.5.js` | those three files |

The renderer refuses every other reference. The processor underneath is the one of
`XmlFrontDoor`, shared with the importer: no extension function, no document type declaration,
no XInclude, no `xsl:evaluate`, no protocol it may dereference and no `xsl:result-document`.
The resolvers sit on the compiler and on the transformer, not on that shared processor, so
nothing here widens what any other module may reach.

The base URI the resolvers hand the stylesheet has a scheme nothing can dereference. A relative
reference inside it resolves in the ordinary way and lands back at the resolvers, not at a
file.

## The PDF rendering is this project's own layout

```text
  SemanticDocument
        │  LetterLayout         the letter, by default; PdfLayout for the sections of the model
        ▼
  Sheet (PDFBox)                pages, cursor, wrapping, page breaks, footers
        │  Liberation Sans      vendored, SIL OFL 1.1, subset and embedded
        ▼
  one PDF, byte for byte the same on every run
```

`PdfRenderer.render(document)` returns the whole file as bytes.
`render(document, options)` takes the language, the paper (`PageSize.A4` or `PageSize.LETTER`)
and the layout. PDFBox draws text and lines; no FOP, no HTML-to-PDF library, no browser.

### Two layouts

Choose the layout with `RenderOptions.withLayout(Layout)`, the template member `"layout"` or
`esj render --layout`. The default is `letter` (`RenderOptions.DEFAULT_LAYOUT`). A layout you
name wins over one the template names. The validation report draws the invoice it carries in
the generic layout.

| | `Layout.GENERIC` | `Layout.LETTER` |
|---|---|---|
| Shape | the semantic model, section by section | a business letter |
| Codes | as the document writes them | under their names, each code listed once at the end |
| Recipient | in the block of parties | in the address field of a window envelope |
| Totals | a table | a narrow block against the right edge |
| Seller | in the block of parties | along the foot of the letter, except BT-31 and BT-32, which stand in the reference line |
| Every term occurrence on a page | yes | yes |

[`letter-layout.md`](letter-layout.md) is the letter: its blocks, its geometry, the display
names, the payment code and what a template decides. The rest of this page holds for both.

### What the generic layout puts on the page, in which order

1. the invoice number, and under it the seller and the buyer side by side, each with its
   address, its identifiers and its contact;
2. the identification of the document: issue date, type code, currency, due date, the
   references BT-10 to BT-19, the process control of BG-2 and the preceding invoices of BG-3;
3. the notes of BG-1, the delivery information of BG-13 with the invoicing period inside it;
4. the invoice lines of BG-25 as a table: line identifier, item, quantity with its unit, the
   **net** unit price with its base quantity, the VAT category and rate, the **net** line
   amount. Under each row hang its note, its object and order references, its item
   identifiers, its item attributes, its line period, its allowances and charges, and the sub
   invoice lines of the XRechnung extension where the document carries them and the registry
   knows them. Sub lines are numbered, and a sub line of a sub line through both;
5. the document allowances of BG-20 and the charges of BG-21, each as a table with the reason,
   the base amount, the percentage, the VAT category and the amount;
6. the VAT breakdown of BG-23 per category, with the exemption reason under the row;
7. the totals of BG-22, in the order of the standard: the net sums first, the gross ones last,
   BT-114 under the label of the rounding amount and BT-115 in bold;
8. the payment terms and the payment instructions of BG-16, with the credit transfers, the
   card information and the direct debit inside it; then the payee of BG-10 and the tax
   representative of BG-11;
9. the supporting documents of BG-24, each with its reference, its description, its external
   location and, for an attachment, its file name, its media type and its size;
10. **Other terms**: every value the sections above have no place of their own for, printed
    with its label and its semantic path.

### Every value is on a page

A test renders every instance of the conformance corpus, every example of the repository and a
document with a value at each of the 203 value paths of the registry. It extracts the text with
PDFBox and asks for every value and every supplementary component, in both layouts. The one
value the PDF does not print is the content of an attachment (BT-125). It prints its file name,
its media type and its size, so a PDF rendering is *smaller* than the attachment of the invoice
where the HTML rendering is larger.

- **Values are formatted for a reader.** An amount, a unit price and a percentage are grouped in
  threes with at least two decimal places: `1.234,56`, `1,234.56`. A quantity carries exactly
  the decimals the document writes, and a date is `31.12.2026` or `2026-12-31`. Content that
  does not have the shape its semantic data type asks for is printed as it stands, and so is a
  decimal whose exponent asks for more than 2000 digits.
- **The currency is shown once per table**, out of BT-5, in the header of the money column. The
  totals block of the letter layout has no header and carries it per row. BT-111 is the VAT
  total in the accounting currency BT-6 and carries that code: beside the figure in the letter,
  beside the label in the generic layout, whose column header speaks for the rest of the table.

### Where the labels come from

| Language | Source |
|---|---|
| English | the term registry: every business term, business group and supplementary component has a name there |
| German | the localization of the vendored KoSIT visualization, `kosit/l10n/de.xml`, through the `id` attribute of an entry — no name matching |
| section headings, column headers | written by the renderer in both languages |

Of the 208 terms of the core registry and the XRechnung extension, **185 are named by an entry
and 23 are not**: the groups BG-2, BG-5, BG-6, BG-8, BG-9, BG-12, BG-14, BG-15, BG-16, BG-24,
BG-25 and BG-30, the 8 `BG-DEX-*` groups of the extension, and BT-128, BT-160 and BT-161.
A German rendering shows their English registry name. Where two entries name the same term
(BG-4 three times, BG-7 twice), the first in document order wins.

The label of a value inside a repeatable group names every such group between its section and
itself, with its number: `Credit transfer 1 · Payment account identifier`. A registry name
inside a label is written as a word, not in capitals; an abbreviation that is a word of its own
keeps its capitals.

The localization calls BT-109, BT-112 and BT-116 all `Gesamtsumme`. Under one another they carry
the localization's own words for a figure without VAT and with it, `_net` and `_gross`, which
gives `Gesamtsumme netto` and `Gesamtsumme brutto`. Where that does not separate two labels,
the renderer appends the identifier: `Kennung (BT-122)`.

### A value of an extension is a value, not a layout

The renderer prints the values of an extension like every other value: under the name that
extension's own registry gives the term, in the place its group gives it, never in the place of
a core term. Sub invoice lines of the XRechnung extension hang under their invoice line. A term
of an extension the renderer's registry does not know is printed under its path in the closing
section.

**And it is marked.** The label carries the namespace of the identifier behind the name, as in
`Gross unit price (B2C)` and `Third party payment amount (DEX)`, and so does the name of an
extension group inside a label. The namespace is part of every extension identifier
(specification, section 5.6), so the mark works without the extension's registry. The HTML
rendering is the KoSIT visualization's layout, and this project marks only what its own
stylesheet controls.

- an extension value never changes how a core term is shown,
- nothing is summed or derived from one,
- none of them is presented as a core figure or placed in a row of the core totals,
- no value is dropped for being an extension's.

A branded template gives extension terms a *place* ([`templates.md`](templates.md)).

### Fonts

Liberation Sans, regular and bold, vendored unmodified; `fonts/README.md` beside them has the
release, the licence and the digests. Every PDF embeds a subset of each face, the glyphs the
document uses, and no standard-14 font takes part. A code point neither face has a glyph for
is printed as a question mark.

### The file is PDF/A-3b

Every rendering is an archival file (`ISO 19005-3`, conformance level B), and veraPDF says so
on every build. [`pdf-output.md`](pdf-output.md#the-file-is-pdfa-3b) lists what the profile asks
for, where each part of it comes from, and how you check a file of your own.

### Determinism of the PDF rendering

The same document, language, page size and template give the same bytes. The producer and the
creator are fixed strings, and the file and its XMP packet carry no date of any kind. Its `/ID` is a
digest of the file itself, not a number from the clock. That is the job ISO 32000-1, 14.4 gives it,
and it lets an archive tell two renderings of one invoice number apart. The embedded font subsets
are a function of the text. Four renderings of the two examples, two in each layout, are checked
into `esj-render/src/test/resources/golden/`. The build compares them byte for byte every time, and
so catches a clock, a locale or an iteration order that a later change might let in. The build
checks the default locale of the machine explicitly: it renders the four golden files again under
`ar-EG`, `ne-NP` and `tr-TR`, and they have to come out the same. A build whose own locale happens
to be the right one would never notice a number written with the digits of another script.

### What a document from a stranger can do to its own PDF

Less than to its HTML. The renderer draws the text as text, and nothing of the document becomes
an instruction of the file. It writes no script, no action, no link, no form and no embedded
file, whatever the invoice says, and prints BT-124 as the text it is. A character the embedded
faces cannot show becomes a question mark, a control character a space, and a character that
directs the reading order a space as well. A line end of any kind (CR LF, CR, VT, FF, U+0085,
U+2028, U+2029) breaks a block as a line feed does. On a line that has to stay one, it is a
space: the page footer, the head of a following page, a figure of the totals.

### What it costs

Rendering is linear in the number of values. An invoice of 300 lines, grown by the generator of
`conformance/scale/`, renders in well under a second, and a test holds that. It is *not* linear
in the size of one value. A megabyte in a narrow column is close to 300 pages, and an invoice
number of half a megabyte is 200, because BT-1 stands in the footer of every one. Both are
inside every bound of `--limits default`, which are bounds on the input. `esj render` arms
`--max-runtime` at 5 minutes and bounds the pages, and it bounds the HTML page by
`--max-output-bytes` while it writes it ([`cli.md`](cli.md#limits),
[measured](deployment-measurements.md#rendering)).

The renderer never breaks these across a page: a section heading and the beginning of what it
heads; a table heading, its column header and its first row; **a row together with everything
hanging under it**; one hanging line, however many lines it was wrapped to. A block taller than
**half** an empty page body keeps its row whole and lets the lines under it flow, because a
block that took most of a page would empty the page before it. A row taller than a page is cut
at a line boundary, at the same line in every column. Either way, the next page repeats the
identifier of the row, greyed, as `L000000011 (continued)`.

**A column header breaks between words and never inside one**, in both layouts, since they
share the table. Where one word of a header is wider than its column, the columns with width to
spare give it up. Where the widest words of all headers side by side are wider than the table,
the header row alone is set smaller, down to 6.6 points. Only a table with more columns than the
paper holds at that size breaks a word.

## From the command line

`esj render` gives you both renderers without writing Java, over any input the other commands
read:

```text
esj render invoice.xml --out invoice.pdf
esj render invoice.esj.json --html --lang en --out invoice.html
esj render invoice.xml --template letterhead.json --embed cii --out hybrid.pdf
```

`--embed cii` attaches the same invoice as a cross industry invoice; `esj embed` is that step
over a PDF from elsewhere ([`pdf-output.md`](pdf-output.md)).

The destination is `--out` and has no default; `-` is the standard output, for a pipeline.
`--lang de|en` sets the language of the page, `--page A4|LETTER` the paper of the PDF and
`--layout generic|letter` its layout, the letter where neither the command line nor the template
names one. The HTML page has neither. [`cli.md`](cli.md#render) is the reference: the options,
what the command writes about values that did not reach an HTML page, and the exit codes.

`esj validate --report` puts either rendering inside the file it writes, with what the rendering
left behind printed beside it ([`cli.md`](cli.md#the-report)). The PDF report uses the generic
layout, because a report is a proof and not a letter. Every page of the PDF report names the
input in its footer. A path too long for the room left of the page count is shortened in its
middle: the beginning and the file name stay, and an ellipsis stands for the rest. The report
names it whole among the identity of the run.

## Attribution

Each directory of vendored material under `esj-render/src/main/resources/de/bsnsoft/esj/render/`
has a `README.md` with the origin, the release and the SHA-256 of every file in it. Tests
recompute those digests on every build, and the NOTICE names each item.

| Directory | Material | Licence |
|---|---|---|
| `kosit/` | the XRechnung visualization stylesheets, tag `v2026-08-31`, nine files | Apache-2.0; `FileSaver-v2.0.5.js` inside it is MIT |
| `fonts/` | Liberation Sans, regular and bold, embedded as subsets | SIL OFL 1.1, which permits the embedding |
| `icc/` | `sRGB2014.icc`, the ICC's v2 sRGB profile, embedded whole in every file | the ICC's profile-library terms: copy, distribute, embed, unaltered |

`common-xr.xsl` and `functions.xsl` are vendored a second time here, beside the copies in
`esj-xr`, so that each module ships the closure of what it runs; a test compares the two copies.
veraPDF, the PDF/A oracle above, is a test-scope dependency under GPLv3+ / MPLv2+ and is in no
artefact this project publishes.

## Branded templates

`--template` puts a letterhead under the page, a logo on it, a colour scheme over it, your fonts
in it and your margins around it. It also gives terms of a model extension the places the
template declares for them. A template is a JSON file of the shape
`schema/render-template.schema.json`. It decides what the page looks like, not what it says:
the same sections in the same order, every value of the document on a page, the figures of
EN 16931-1 still the net ones, the file still PDF/A-3b and still the same bytes twice.

**The gross layout is one of those places, and only the document opens it.** A column beside a
net figure or a row among the totals appears where the document carries the extension term the
template placed there, and nowhere else. Every figure placed so is marked as one that was
displayed. No gross figure is derived from a core term, and BT-114 stays the invoice rounding
amount. [`templates.md`](templates.md) is the file format, the 4 positions a template may place
a term at, and what a template cannot do.
