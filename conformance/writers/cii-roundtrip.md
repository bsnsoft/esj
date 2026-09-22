# The CII writer, measured

*Part of [EN16931 Semantic JSON](../../README.md).*

A writer that turns a semantic document into an XML syntax can be wrong in two ways, and
they have to be measured separately. There is a third way it can be less than right, which
neither measurement can see, and it has a section of its own at the end of this page.

It can write a document the syntax does not accept. That is answered by the official
validation artefacts of the profile — the schema modules of UN/CEFACT CII D16B, the EN 16931
Schematron of CEN/TC 434 and the XRechnung Schematron of KoSIT — run over every document the
writer produces, through the same engine `esj validate` uses and the same pack,
`xrechnung/3.0.2/2026-08-31`. Nothing here is this project's opinion of what a valid cross
industry invoice is.

It can also write a document the syntax accepts and that no longer says what it was given.
That is answered by reading the result back with the streaming reader of the same module and
comparing the semantic documents value by value. A conversion that survives that comparison
carried everything; one that does not says exactly which paths it lost.

Both figures are recomputed on every build. `cii-roundtrip.json` beside this page carries
them as data, `CiiWriterCorpusTest` in `esj-bindings` recomputes each of them from the corpus
and from `examples/`, and it fails where a number here has moved in either direction. A
document that starts to validate fails this test as loudly as one that stops.

## What was written

| | |
|---|---|
| Corpus | the 86 instances of `conformance/kosit/`, 41 of them cross industry invoices and 45 of them UBL |
| Examples | the ten documents of `examples/` |
| Read by | `StreamingReader`, the table-driven reader of this module |
| Written by | `CiiWriter`, from `model/bindings/cii.json` and the element order of `CiiSchema` |
| Validated by | `SyntaxValidator` of `esj-syntax` with the pack `xrechnung/3.0.2/2026-08-31` |

## The corpus

| Measure | Instances |
|---|---:|
| `accepted` — the validation artefacts report nothing fatal | 85 |
| `refused` — at least one fatal finding | 1 |
| `identical` — reading the result back gives the semantic document it was written from | 80 |
| `differing` — at least one semantic path differs | 6 |
| `total` | 86 |

706 semantic paths differ over the whole corpus, all of them in the six instances below, and
every one of them is a value the syntax has no place for rather than a value the writer
mislaid. 696 of the 706 belong to one cause in four instances.

The same measurement over the documents the XSLT path of `esj-xr` builds from the same
corpus gives **85 accepted** and **79 identical**. The two readers do not hand the writer the
same documents — `../readers.md` says where they differ — so the two columns are measured
separately; a page that gave one figure would be choosing the flattering one. The one
document that separates them is the invoice that states a value added tax point date code:
the XSLT path takes the code of the syntax as it stands, the writer has no code of the
standard to translate back and says so, and reading the result with the other reader gives
the code of the standard.

## What the syntax refused, and why

One document carries a fatal finding, and it is not a value in the wrong element: the
semantic document holds something the target profile does not admit at all, and a writer
cannot put it anywhere.

| Rule | Instances | Cause |
|---|---:|---|
| `BR-CO-16` | 1 | `extension-not-bound` |

### `extension-not-bound`

One document uses the XRechnung extension, whose three terms the source model binds for UBL
Invoice and for no other syntax. The writer reports each of them as dropped, and the
document that comes out is a document whose amounts no longer add up, because the amounts the
extension carried are part of the sum that `BR-CO-16` checks. A syntax binding that does not
bind an extension cannot carry a document that uses it, and saying so is the only honest
outcome.

## What did not survive the round trip

Six documents read back differently, at 706 semantic paths, and every cause is reported by
the writer as it writes.

| Cause | Note | Instances | Paths |
|---|---|---:|---:|
| `extension-sub-invoice-lines` | `TERM_NOT_BOUND` | 4 | 696 |
| `extension-terms` | `TERM_NOT_BOUND` | 1 | 6 |
| `preceding-invoice-references` | `GROUP_NOT_REPEATABLE` | 2 | 4 |

**`extension-sub-invoice-lines`.** The XRechnung extension lets an invoice line carry sub
invoice lines, to any depth, and a sub invoice line carries the business terms of a line.
The extension binds those groups, and the core terms inside them, for UBL Invoice and for no
other syntax, so a cross industry invoice has no element for any of them. The four documents
concerned are the extension instances of the corpus; the writer names every one of the 696
values with the term it belongs to, and the deepest of them lie four sub invoice lines down.

**`extension-terms`.** The three terms of the XRechnung extension have no XPath in this
syntax, as `model/bindings/cii.json` states and the writer's note repeats.

**`preceding-invoice-references`.** EN 16931-1 lets an invoice reference more than one
preceding invoice: BG-3 is a group the model admits many times. The schema of this syntax
declares `ram:InvoiceReferencedDocument` once inside the header trade settlement, so the
second and third reference have no element to be. The writer writes the first, reports each
further instance with the identifier of the group and the occurrence it could not place, and
the report says how many.

## The examples

The ten documents of `examples/` this syntax holds whole are synthetic and structurally
complete under validation layers L1 to L3, and `examples/README.md` states for each what the
business rules of EN 16931-1, clause 6.4 make of it. Measured against that:

| Measure | Documents |
|---|---:|
| `structural` — the parser and the schema modules report nothing fatal | 10 |
| `ruleClean` — the Schematron artefacts report nothing fatal either | 7 |
| `identical` — reading the result back gives the document it was written from | 10 |

Every example survives the round trip, and every example is a cross industry invoice the
schema accepts. Three of them make a rule fire, and each rule is one those documents were
never built to satisfy:

| Rule | Findings | What it asks for |
|---|---:|---|
| `BR-48` | 3 | a VAT category rate in every VAT breakdown |
| `BR-CO-26` | 3 | a seller identifier, legal registration identifier or VAT identifier |
| `BR-Z-02` | 3 | a seller VAT or tax registration identifier where a line is zero rated |
| `BR-Z-05` | 3 | a VAT rate of zero on a zero rated line |

The three documents are `minimal.esj.json` and the two built from its values, and
`examples/README.md` names those rules as the ones a document of only the mandatory terms
cannot satisfy. `smallest-valid.esj.json` is the same document with the four terms they ask
for, and the artefacts report nothing about it.

## Where the syntax draws a distinction the model does not

Both measurements above compare a document with itself: the artefacts say whether the syntax
accepts it, and the round trip says whether reading it back gives what was written. Neither
can see a third thing. Where the syntax offers two elements for one business term and the
semantic model has nothing that says which, the writer has to choose — and a round trip stays
identical whichever it chooses, because the reader maps both elements back to the same term.
The three places this happens are named here rather than left to be found.

**BT-84, the payment account identifier.** CII writes it as `ram:IBANID` or as
`ram:ProprietaryID`; neither carries a supplementary component, and ESJ has one term for both.
The writer chooses by the form of the value, which is what the CEN mapping intends: an
identifier shaped like an international bank account number — two letters, two digits and up
to thirty more characters — goes into `ram:IBANID`, and every other one into
`ram:ProprietaryID`. Without that rule the first element the table lists would win and every
account identifier would be written as an IBAN, which asserts of the document something it
never said. One corpus instance still comes back changed: `01.02_comprehensive_test_uncefact`
writes an IBAN-shaped value at `ram:ProprietaryID`, and the writer puts it at `ram:IBANID`.
Nothing in the semantic document distinguishes the two, so this is the rule applied and not a
value mislaid.

**BT-41 and BT-56, the contact point of a party.** CII writes the contact as
`ram:PersonName` or `ram:DepartmentName`, and the table carries both as alternatives. The
writer takes the first, so two corpus instances that state a department come back stating a
person. The value is the same.

**BT-149 and BT-150, the price base quantity.** CII carries it under the gross price or under
the net price. The writer puts it under the gross price where the schema admits it there, so
one corpus instance that states it under the net price comes back stating it under the gross
one. The value and its unit are the same, and a document that carries only a net price still
gets the net one, which `CiiWriterTest` asserts.

## Where the model admits a character the syntax has no place for

Everything measured on this page came out of an XML parser, so nothing on it can see this
one: a semantic document may hold characters that no XML 1.0 document can carry.
`SPEC.md` section 12.6 admits any Unicode scalar value in a text value, control characters
included, and the ESJ reader takes them — it refuses only an unpaired surrogate,
`ESJ-L1-SURROGATE`. XML 1.0 admits neither the C0 controls apart from tab, line feed and
carriage return, nor the two non-characters `U+FFFE` and `U+FFFF`, and it has no escape for
them: `&#x7;` is as ill-formed as the raw byte.

**The writer leaves such a character out and says so.** The rest of the value is written, the
report carries a `CHARACTER_NOT_REPRESENTABLE` note naming the semantic path and the code
points, and `esj convert` prints it as a warning. A surrogate that is not half of a pair is
the same case and takes the same answer: it is no scalar value, and it reaches the writer only
from a caller who built the document in memory, because the reader refuses it at the front
door.

Refusing the whole document was the other candidate and was not taken. A converter that
refuses an invoice because one party name carries a stray `U+0007` leaves its caller with
nothing; a note that names the term and the code point leaves them with the file and the
question, which is the same bargain the writer makes everywhere else on this page. The
decision is held by `CiiWriterTest` for a control character, for `U+FFFE`, for an unpaired
surrogate, for a supplementary component and for the three control characters XML does have.

## Two documents the writer supplies that the table does not

Two things a cross industry invoice needs are not in the binding table, because they are not
bindings of a business term, and the writer takes both from the schema rather than from an
opinion.

The **structural sections** — the document context, the trade transaction and its three
header sections — are elements the schema requires and whose types ask for nothing in turn.
The writer creates every such element, and `CiiSchema` decides which they are.

The **allowance indicator** says which of the two an allowance-or-charge element is. The
table states it wherever the standard binds a business group to that element type, and it
binds one business term to an element of that type without a group: the item price discount.
A discount is an allowance, so the writer writes the allowance form there, taking the element
and the value from the table's own statement of it.

## Determinism

The writer produces the same bytes for the same document. Element order comes from the
schema, siblings of one name stand in the canonical order of the semantic paths that produced
them, and nothing in the output depends on a map iteration order or on the locale of the
process. `CiiWriterTest` writes each corpus instance twice and compares the bytes.

Author: Christian Bürckert.
