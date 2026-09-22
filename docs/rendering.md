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

The layout, the five tabs, the labels and the two languages are the HTML visualization of the
KoSIT XRechnung project, vendored and run unmodified. The one rule this project overrides is
the one a document from a stranger could use to decide where a link goes, under *What a
document from a stranger can do to its own HTML rendering* below.

`HtmlRenderer.render(document)` returns the whole page as a string. The only option it reads is
the language; a branded template belongs to the PDF renderer and is ignored here.

## Every rendering is net, and derives nothing

EN 16931-1 core terms keep their net semantics, and so do both PDF layouts and the HTML page.
Each shows the net amount and the net unit price of every invoice line, the VAT breakdown per
category (BG-23) and the totals of BG-22 up to the amount due, gross among them (BT-112,
BT-115). No core business term is invented for a gross line or unit value, no derived figure is
written into a core term, BT-114 is the invoice rounding amount of the standard rather than a
balancing field, and no sentence combines two values into a statement. Consumer-oriented gross
figures belong to a model extension and to a renderer that was asked for them
([`templates.md`](templates.md)); they are never inferred back from the core
([`design-decisions.md`](design-decisions.md)).

The `extensions` subtree — data belonging to no business term — has no place in the XR
representation and therefore none in the HTML rendering;
`HtmlRenderer.renderWithReport` names it, and everything else that did not reach the
stylesheet, in an `ExportReport`. A PDF names the owner of every extension subtree at the end
and says the data below it is not printed.

## What the HTML rendering shows

**Every business term of the registry**, in both languages, together with the supplementary
components: the scheme and the scheme version of an identifier, the media type and the file
name of an attachment. Asserted on a document carrying a value at each of the 203 value paths
of the core model and the XRechnung extension, on every instance of the conformance corpus and
on every example of the repository. (203 value paths, 208 terms: a term standing in two groups
has a path in each; the number is pinned in `PdfCoverageTest`.)

- **Values are formatted for a reader**, through the stylesheet's number picture — `1.234,56` in
  German, `1,234.56` in English, at least two decimal places — and its date picture, `31.12.2026`
  and `2026-12-31`. The canonical spelling of the document is not what stands in the page.
- **The values are not in canonical path order.** The stylesheet arranges an invoice over five
  tabs of its own and shows the buyer before the seller.
- **Attachments are carried whole.** BT-125 goes into the page in a hidden element a download
  link reads out, so a rendering is larger than the attachments of the invoice: a corpus
  instance with a 200 kB attachment renders to more than that, against about 100 kB without one.

## What a document from a stranger can do to its own HTML rendering

The text of an invoice is written into the page as text. The serializer escapes it, so a
description containing `<script>` reaches the reader as those characters; the scripts in a
rendering are the two the stylesheet inlines and no others, whatever the invoice says. That is
asserted on a fixture built to break it.

Two places of the vendored stylesheet write a value somewhere other than into text, and this
module overrides the one template rule that writes them. The override lives in
`xsl/esj-html.xsl`, which imports the vendored stylesheet; the vendored bytes, their digests
and their attribution are untouched, because import precedence is what `xsl:import` is for.

- **BT-124, the external document location, becomes the target of a link.** It is a link where
  its scheme is `http`, `https` or `mailto`, and the text it is where it is anything else — a
  `javascript:` URI is something a reader sees rather than something a reader can follow. The
  value is on the page either way: a rendering that dropped it would hide what the invoice says.
- **The download link of an attachment calls a script with values out of the document.** BT-122,
  the media type and the file name stand in the arguments of a JavaScript call inside single
  quotes, and an HTML serializer does not escape an apostrophe inside an attribute delimited by
  double quotes. The three values are escaped for the string literal they stand in, so an
  apostrophe in a file name is an apostrophe in a file name.

**Characters that direct the reading order** — the right-to-left override and its family, U+200E,
U+200F, U+202A–U+202E, U+2066–U+2069 and U+FFF9–U+FFFB — are replaced by a space on the way in,
as they are in the PDF rendering. They are invisible, and what they do is make a line read
differently from the text the document stores.

`UntrustedContentTest` asserts all of this, so that a newer tag of the stylesheet that changed
something is a failing build rather than a surprise.

**What a caller should do.** A rendering is a document built from one somebody else wrote.
Serve it from an origin of its own, in a sandboxed frame, under a content security policy that
forbids inline script where the download link is not needed, and never paste it into a page
that holds a session. The renderer is a display function, not a sanitizer of arbitrary HTML.

## Determinism of the HTML rendering

The same document rendered twice gives the same string, character for character: no timestamp,
no producer string, no identifier that counts up, no iteration order a hash decides. A
rendering is a function of the document, the language and the version of the stylesheet this
module ships. Asserted for every instance of the corpus.

## What the HTML renderer may reach

The stylesheets reach outside their own files in three ways, and `esj-render` answers exactly
those three out of its own classpath — the entry point being `xsl/esj-html.xsl`, which imports
the vendored one:

| Reference | What the stylesheet asks for | What is answered |
|---|---|---|
| `xsl:import` | `xrechnung-html.xsl`, `common-xr.xsl`, transitively `functions.xsl` | those three files |
| `doc()` | `l10n/de.xml`, `l10n/en.xml` | those two files |
| `unparsed-text()` | `xrechnung-viewer.css`, `xrechnung-viewer.js`, `FileSaver-v2.0.5.js` | those three files |

Every other reference is refused. The processor underneath is the one of `XmlFrontDoor`, shared
with the importer: no extension function, no document type declaration, no XInclude, no
`xsl:evaluate`, no protocol it may dereference and no `xsl:result-document`. The resolvers are
set on the compiler and on the transformer, not on that shared processor, so nothing here widens
what any other module may reach.

The base URI the resolvers hand the stylesheet has a scheme nothing can dereference, so a
relative reference inside it resolves in the ordinary way and lands back at the resolvers rather
than at a file.

## The PDF rendering is this project's own layout

```text
  SemanticDocument
        │  PdfLayout            sections, tables, labels; the registry decides the types
        ▼
  Sheet (PDFBox)                pages, cursor, wrapping, page breaks, footers
        │  Liberation Sans      vendored, SIL OFL 1.1, subset and embedded
        ▼
  one PDF, byte for byte the same on every run
```

`PdfRenderer.render(document)` returns the whole file as bytes.
`render(document, options)` takes the language, the paper — `PageSize.A4` or `PageSize.LETTER`
— and the layout. PDFBox draws text and lines; no FOP, no HTML-to-PDF library, no browser.

### Two layouts

`RenderOptions.layout(Layout)`, template member `"layout"`, `esj render --layout`. Default
`generic`; a layout the caller names wins over one the template names.

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
2. the identification of the document — issue date, type code, currency, due date, the
   references BT-10 to BT-19, the process control of BG-2 and the preceding invoices of BG-3;
3. the notes of BG-1, the delivery information of BG-13 with the invoicing period inside it;
4. the invoice lines of BG-25 as a table: line identifier, item, quantity with its unit, the
   **net** unit price with its base quantity, the VAT category and rate, the **net** line
   amount. Under each row hang its note, its object and order references, its item
   identifiers, its item attributes, its line period, its allowances and charges, and the sub
   invoice lines of the XRechnung extension where the document carries them and the registry
   knows them — numbered, and a sub line of a sub line numbered through both;
5. the document allowances of BG-20 and the charges of BG-21, each as a table with the reason,
   the base amount, the percentage, the VAT category and the amount;
6. the VAT breakdown of BG-23 per category, with the exemption reason under the row;
7. the totals of BG-22, in the order of the standard: the net sums first, the gross ones last,
   BT-114 under the label of the rounding amount and BT-115 in bold;
8. the payment terms and the payment instructions of BG-16, with the credit transfers, the
   card information and the direct debit inside it; then the payee of BG-10 and the tax
   representative of BG-11;
9. the supporting documents of BG-24, each with its reference, its description, its external
   location and — for an attachment — its file name, its media type and its size;
10. **Other terms**: every value the sections above have no place of its own for, printed with
    its label and its semantic path.

### Every value is on a page

A test renders every instance of the conformance corpus, every example of the repository and a
document carrying a value at each of the 203 value paths of the registry, extracts the text
with PDFBox and asks for every value and every supplementary component. It runs for both
layouts. The one value not printed is the content of an attachment (BT-125); its file name, its
media type and its size are, so a PDF rendering is *smaller* than the attachment of the invoice
where the HTML rendering is larger.

- **Values are formatted for a reader.** An amount, a unit price and a percentage are grouped in
  threes with at least two decimal places — `1.234,56`, `1,234.56` — a quantity carries exactly
  the decimals the document writes, a date is `31.12.2026` or `2026-12-31`. Content that is not
  of the shape its semantic data type asks for is printed as it stands, and so is a decimal
  whose exponent asks for more than two thousand digits.
- **The currency is shown once per table**, in the header of the money column, out of BT-5;
  the totals block of the letter layout has no header and carries it per row. BT-111 is the VAT
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
BG-25 and BG-30, the eight `BG-DEX-*` groups of the extension, and BT-128, BT-160 and BT-161.
Those show their English registry name in a German rendering. Where two entries name the same
term — BG-4 three times, BG-7 twice — the first in document order wins.

A value inside a repeatable group is labelled with the name and the number of every such group
between its section and itself: `Credit transfer 1 · Payment account identifier`. A registry
name used inside a label is written as a word rather than in capitals; an abbreviation that is
a word of its own keeps its capitals.

BT-109, BT-112 and BT-116 are all `Gesamtsumme` in the localization. Under one another they
carry the localization's own words for a figure without VAT and with it, `_net` and `_gross`,
giving `Gesamtsumme netto` and `Gesamtsumme brutto`; where that does not separate them, the
identifier is appended — `Kennung (BT-122)`.

### A value of an extension is a value, not a layout

The values of an extension are printed like every other value: under the name that extension's
own registry gives the term, in the place its group gives it, never in the place of a core
term. Sub invoice lines of the XRechnung extension hang under their invoice line; a term of an
extension the renderer's registry does not know is printed under its path in the closing
section.

**And it is marked**: the label carries the namespace of the identifier behind the name —
`Gross unit price (B2C)`, `Third party payment amount (DEX)` — and so does the name of an
extension group inside a label. The namespace is part of every extension identifier
(specification, section 5.6), so the mark does not depend on the renderer having the
extension's registry. The HTML rendering is the KoSIT visualization's layout, and this project
marks only what its own stylesheet controls.

- an extension value never changes how a core term is shown,
- nothing is summed or derived from one,
- none of them is presented as a core figure or placed in a row of the core totals,
- no value is dropped for being an extension's.

A layout that gives extension terms a *place* is a branded template
([`templates.md`](templates.md)).

### Fonts

Liberation Sans, regular and bold, vendored unmodified; `fonts/README.md` beside them has the
release, the licence and the digests. A subset of each face — the glyphs the document uses — is
embedded in every PDF, and no standard-14 font takes part. A code point neither face has a
glyph for is printed as a question mark.

### The file is PDF/A-3b

Every rendering is an archival file — `ISO 19005-3`, conformance level B — and veraPDF says so
on every build: [`pdf-output.md`](pdf-output.md#the-file-is-pdfa-3b) is what the profile asks
for, where each of it comes from, and how a caller checks a file of their own.

### Determinism of the PDF rendering

The same document, language, page size and template give the same bytes. The producer and the
creator are fixed strings, the file and its XMP packet carry no date of any kind, its `/ID` is
a digest of the file itself rather than a number from the clock — which is the job ISO 32000-1,
14.4 gives it, and what lets an archive tell two renderings of one invoice number apart — and
the embedded font subsets are a function of the text. Two renderings of the two examples are
checked into
`esj-render/src/test/resources/golden/` and compared byte for byte on every build, which is
what catches a clock, a locale or an iteration order that a later change might let in. The
default locale of the machine is checked explicitly rather than left to the build: the two
golden files are rendered again under `ar-EG`, `ne-NP` and `tr-TR` and have to come out the
same, because a build whose own locale happens to be the right one would never notice a number
written with the digits of another script.

### What a document from a stranger can do to its own PDF

Less than to its HTML. The text is drawn as text and nothing of the document becomes an
instruction of the file: the renderer writes no script, no action, no link, no form and no
embedded file, whatever the invoice says, and BT-124 is printed as the text it is. A character
the embedded faces cannot show becomes a question mark, a control character a space, and a
character that directs the reading order a space as well.

### What it costs

Rendering is linear in the number of values: an invoice of three hundred lines, grown by the
generator of `conformance/scale/`, renders in well under a second, and that is a test. It is
*not* linear in the size of one value — a megabyte in a narrow column is close to three hundred
pages, an invoice number of half a megabyte is two hundred, because BT-1 stands in the footer of
every one. Both are inside every bound of `--limits default`, which are bounds on the input;
`esj render` arms `--max-runtime` at five minutes and bounds the pages
([`cli.md`](cli.md#limits), [measured](deployment-measurements.md#rendering)).

Never broken across a page: a section heading and the beginning of what it heads; a table
heading, its column header and its first row; **a row together with everything hanging under
it**; one hanging line, however many lines it was wrapped to. A block taller than **half** an
empty page body keeps its row whole and lets the lines under it flow, because one that took
most of a page would empty the page before it; a row taller than a page is cut at a line
boundary, at the same line in every column. Either way the next page repeats the identifier of
the row, greyed, as `L000000011 (continued)`.

## From the command line

`esj render` is both renderers without writing Java, over any input the other commands read:

```text
esj render invoice.xml --out invoice.pdf
esj render invoice.esj.json --html --lang en --out invoice.html
esj render invoice.xml --template letterhead.json --embed cii --out hybrid.pdf
```

`--embed cii` attaches the same invoice as a cross industry invoice and, beside it, as an ESJ
document; `esj embed` is that step over a PDF from elsewhere ([`pdf-output.md`](pdf-output.md)).

The destination is `--out` and has no default — `-` is the standard output, for a pipeline —
`--lang de|en` is the language of this page, `--page A4|LETTER` the paper of the PDF and
`--layout generic|letter` its layout, neither of which the HTML page has. [`cli.md`](cli.md#render) is the reference: the options,
what the command writes about values that did not reach an HTML page, and the exit codes.

`esj validate --report` puts either rendering inside the file it writes, with what the rendering
left behind printed beside it ([`cli.md`](cli.md#the-report)).

## Attribution

Under `esj-render/src/main/resources/de/bsnsoft/esj/render/`, each directory of
vendored material has a `README.md` with the origin, the release and the SHA-256 of every file
in it; tests recompute those digests on every build, and the NOTICE names each item.

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

`--template` puts a letterhead under the page, a logo on it, a colour scheme over it, the
caller's fonts in it and the caller's margins around it, and gives terms of a model extension
the places the template declares for them. A template is a JSON file of the shape
`schema/render-template.schema.json`, and it decides what the page looks like, not what it
says: the same sections in the same order, every value of the document on a page, the figures
of EN 16931-1 still the net ones, the file still PDF/A-3b and still the same bytes twice.

**The gross layout is one of those places, and only the document opens it.** A column beside a
net figure or a row among the totals appears where the document carries the extension term the
template placed there, and nowhere else; every figure so placed is marked as one that was
displayed; no gross figure is derived from a core term, and BT-114 stays the invoice rounding
amount. [`templates.md`](templates.md) is the file format, the four positions a template may
place a term at, and what a template cannot do.

