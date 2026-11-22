# Design decisions and trade-offs

Every heading is a decision that could have gone the other way. The paragraph under it says
what ESJ gave up for it. `SPEC.md` is the normative text; nothing here changes it.

## What the format is good for

The decisions below serve these properties, and every one of them cost something:

- **Compact**: no XML namespace and wrapper overhead.
- **Semantic**: addresses BT/BG concepts directly, not syntax elements.
- **Binding-independent**: business logic need not depend on UBL or CII.
- **Database-friendly**: stores as JSON/JSONB or as path/value rows unchanged
  ([`storage.md`](storage.md)).
- **Deterministic**: every value has one stable semantic address, and every content one
  canonical byte sequence.
- **Diff-friendly**: a semantic change shows up as a changed path or value.
- **Test-friendly**: you can compare two syntax bindings at the semantic level.
- **Streaming-friendly**: a consumer can process path plus value without building a full
  invoice object graph.
- **Implementation-friendly**: a small mapper binds semantic paths to a domain model.
- **Change-resistant**: persistence is keyed to EN 16931 identifiers, not to Java classes.
- **Reduced XML exposure**: native ESJ consumers need no XML processing. Import adapters
  that read UBL or CII still need secure XML handling. ESJ does not remove that requirement;
  it confines it to the adapter.

## A value is a string, never a JSON number

ESJ writes amounts, quantities, percentages and dates as JSON strings with a fixed grammar
(`SPEC.md` sections 6.4 and 6.5), never as JSON numbers.

**Why.** A JSON number has no spelling. `1.50`, `1.5` and `15E-1` are the same number, and a
parser that hands over a `double` has already changed the document. `0.1 + 0.2` is not `0.3`
in binary floating point, and an invoice total that does not add up is not an academic
problem. A string has exactly one canonical spelling and survives every parser unchanged. That
makes the canonical form of section 7, and with it the two digests of section 8, a property of
the content instead of the library that read it.

**What it costs.** A consumer cannot compute with a value the moment it has parsed the
document. It converts first, to whatever decimal type its language offers. Generic JSON
tooling shows `"84.03"` where a reader expects `84.03`, and a JSON Schema `maximum` cannot
apply to it. Neither cost falls on correctness, and the alternative does.

## A value carries no type token

An earlier draft wrote every value as an object with a `type` member naming its semantic
data type. It is gone. A value is a JSON string, or an object only where the semantic model
gives the term supplementary components (`SPEC.md` section 6.1).

**Why.** The registry already writes the type down, once: `model/en16931/2017.json` records
the semantic data type of every business term, and `semanticModel` in the envelope says which
registry applies. Repeating the type per value had 3 effects. Every document carried the same
fact a few hundred times. Two documents of one invoice differed when one of them spelled the
type differently. Worst, the document could *contradict* the model: a document that called
BT-131 a `Text` had no defined meaning.

**What it costs.** A reader alone can no longer say that `100.00` is a badly spelled decimal
or that `2026-02-30` is not a day. Without the registry, nothing says those two strings are a
decimal and a date. Those checks therefore moved from layer L1 to layer L2 (`SPEC.md` sections
6.2 and 9.2), and if you want them, you load a registry. In exchange, each layer is honest
about what it actually knows, and `schema/esj-en16931-2017.schema.json` gives JSON Schema
users the same typing without one.

## Occurrence indices are positions, not identities

A repeatable term or group carries a zero-based index in its path: `/BG-25/0/BT-131` is the
net amount of the first invoice line. The index is a position in a dense sequence
(`SPEC.md` sections 5.3 and 5.4), not a key.

**Why.** EN 16931 gives a line BT-126, an identifier of its own, but gives no such thing to
most repeatable groups, and a binding may not invent identities the model does not have. A
position is what the model actually provides, and UBL and CII both preserve it. Density makes
the canonical path order of section 7.4 total. So one invoice has one canonical form whichever
syntax it was read from, and the conformance corpus runs exactly that litmus test.

**What it costs.** Deleting the first of 3 lines renumbers the other 2. Every path under
`/BG-25/1` and `/BG-25/2` changes, although nothing about those lines did. A diff of the two
documents shows every value of both lines as changed. An external reference to
`/BG-25/2/BT-131` (a comment, a row in another table, an approval) now points at a different
line. The editor of `esj-typed` therefore removes an occurrence and closes the gap in one
operation, so you need not do it yourself. If your system needs a stable handle, store BT-126
instead of the index. The alternative, a synthetic key inside the path, would have made the
same invoice serialize differently in two systems. That would have ended the one property the
whole format is built on.

## Two editions, one registry file each

`semanticModel` has two defined values. One is the 2017 line with amendment A1:2019 and
corrigendum AC:2020 applied, the default everywhere. The other is EN 16931-1:2026.

**Why.** An edition is data, not code: one registry file per edition, the edition named in the
envelope and in the registry header, the paths interpreted against the registry that matches.
The 2017 edition stays the default for 2 reasons. The CEN validation artefacts, the KoSIT test
suite and the syntax bindings are written against it, and its text is freely obtainable
(`docs/sources.md`). `model/en16931/2026.paths` lists the files that carry facts of the 2026
edition, and the Maven profile `without-edition-2026` builds a distribution without them.

**What it costs.** For a document of the 2026 edition, ESJ carries the content, reads it,
canonicalizes it, hashes it and validates it structurally. It does not convert it to UBL or
CII, because no public syntax binding of the terms that edition adds exists. Its business
rules are a pack of this project, `en16931-2026/0.1`, and no official validation artefact
corroborates it. Each rule names what stands behind it instead, so a document of the edition
reaches `INDETERMINATE` at best. A build that holds no registry for the edition a document
names reports `ESJ-L2-EDITION-UNKNOWN` and reaches no verdict instead of guessing (`SPEC.md`
4.4 and 9.2).

## How many decimals a term allows is a fact of its edition

The registry records the bound: `maxDecimals` where the edition fixes a number, and
`maxDecimalsRule` where it derives the number from the currency of the document. Layers L1,
L2 and L3 check none of them.

**Why.** The two editions do not agree on the bound. In the 2017 edition an amount carries at
most 2 fraction digits, and the cap reads as a property of the semantic data type. In the 2026
edition the cap of an amount follows the minor unit of its currency, a unit price gets 2
digits more, and one amount term is bounded at 6. A bound that moves between editions is no
property of the type. A structural layer that enforced it would reach a different verdict on
the same digits depending on which registry a build happened to carry. The bound belongs with
the arithmetic it serves, and that arithmetic is a rule pack.

**What it costs.** `validate` accepts a value whose scale exceeds what the edition allows, and
only the rule pack of that edition objects. The registry states the bound, so a writer, a form
or a database projection can hold to it before an invoice goes out. `upgrade` reports every
value that exceeds the bound of the target edition instead of rounding it. A migration that
changed a figure would be a repair, and nothing in ESJ repairs.

## `upgrade` is a table, and never silent

`esj upgrade` reads what differs between two editions from
`model/en16931/upgrade-2017-2026.json` and writes the result of applying it. No pair of
editions is described in Java.

**Why.** What moved between two editions is a fact about two registries. You can check it by
reading one file, and a binding in another language that will never run this implementation
can reuse it. 6 paths move upwards: one term into a new group, one group under a new one, and
two whose occurrence index appeared because the edition widened their cardinality. Every other
value is carried byte for byte.

**What it costs.** The interesting cases are the ones the table cannot decide, and the command
reports them instead of resolving them:

- a component the target edition requires and the document does not carry is an open point;
- the specification identifier in BT-24 is a statement about a specification this tool did not
  check, so it stays as it stands unless you write another one with `--specification`;
- a value the target edition has no address for makes the run refuse until you name that path
  with `--drop`.

So the command sometimes ends without a document, and that is the point. An upgrade that
quietly dropped a business term would be the failure mode this project exists to prevent.

## One typed view per edition, in a package of its own

`de.bsnsoft.esj.typed` is the view of the 2017 edition, `…typed.v2026` the view of the 2026
one. Each is generated from that edition's registry, with an `En16931` of its own. The types
the two share (the value records, the handles, the two exceptions and the runtime a generated
accessor calls) lie in the first package and in `…typed.internal`.

**Why.** The typed view exists to make a wrong path unrepresentable. A single view over both
editions would offer `/BT-20` at the root of a 2026 document, where no such path exists. It
would have to answer "is this term in this edition?" at run time, the question the view was
built to remove. `edit` therefore refuses a document of another edition. `view` is a reader and
stays blind to it, as a reader must (`SPEC.md` 3).

**What it costs.** Twice the generated source, and if you handle both editions, you write
against both packages. The generator emits `derive` for the 2017 view alone, because the
derivation policies are written against that edition's arithmetic. A policy for the 2026
edition comes with the rule pack for it.

## The registry is the single source of structure

Which terms exist, what they are called, how often they may occur, what data type each one
has and which supplementary components it carries are facts of the registry, not of
`SPEC.md` (`SPEC.md` section 10).

**Why.** A specification that restates a term table goes out of date against it, and two
copies of one fact are one fact and one bug. With the facts in a machine-readable file, the
validator, the code generator, the typed view and the generated schema all read the same one.
A disagreement between them is impossible, not merely unlikely.

**What it costs.** The registry has to be right, and nothing outside it can catch it being
wrong. So `model/registry.schema.json` constrains it, the loader refuses combinations the
semantic data types do not have, and `model/README.md` records how each column was derived and
cross-checked. ESJ also ships a data file that a reader needs to reach layers L2 and L3 at all.
Layer L1 is deliberately usable without one.

## Business rules are a layer of their own, and are checked

The BR-*, BR-CO-*, BR-DEC-* and BR-CL-* rules of EN 16931 are not part of ESJ conformance. A
document may be structurally conformant under `SPEC.md` and violate any of them. A tool that
reported a broken sum as an ESJ format error would say something false about the format. ESJ
checks them all the same. `esj validate` runs the official Schematron of the document's
profile over an XML input and the rule pack of [`rules/`](../rules/README.md) over the semantic
document. Both report into layers of their own, with the pack named on every finding.

**Why apart.** They are a different kind of statement. The bytes and the registry decide
structure, and structure does not change. A business rule is decided against code lists and
arithmetic that move over time, and a verdict that expires does not belong in the layer that
says whether a document is well formed. Mixing them would give an archived invoice two
different structural verdicts from two releases of one tool. Kept apart, both run in one
command without either claiming the other's authority.

**Why a pack of our own, beside the artefacts.** An ESJ document that was never XML has no
artefact that will ever look at it. `examples/invalid/arithmetic-mismatch.esj.json` is the
fixture that made the gap visible, and ESJ now reports it. A rule written over business terms
serves a UBL invoice, a CII invoice and a native document alike, which is the long-term reason
the semantic model exists at all.

**What it costs.** Two engines can report one rule twice over an XML input, and the report
keeps both instead of choosing between them. A native finding carries the level its rule
declares. The verdict then applies the level the profile of the document gives that rule, to a
finding of the pack as to one of an artefact. So the pack stays profile-agnostic, and one
document gets one answer. [`validation.md`](validation.md) and
[`../conformance/rules/corpus.md`](../conformance/rules/corpus.md) document both where you meet
them, and [`../conformance/rules/ledger.md`](../conformance/rules/ledger.md) is the measurement
against the official artefacts.

**A scenario is a profile and a syntax.** The specifications publish their level tables per
scenario, so one profile may level a rule for one syntax and say nothing about it for the
other. The XRechnung extension profile levels BR-CO-16 down for a UBL invoice and leaves the
standard's level standing for a cross industry invoice. An invoice that trips it is `VALID` as
UBL and `INVALID` as CII. That is the published fact, not a decision of this tool. ESJ levels a
document by the table of the syntax it arrived in, the syntax a reader of that file will judge
it by. Where a command writes the document into the other syntax, `esj convert` and
`esj render --embed` say so in an `info:` line if the target's table is the stricter one: the
file they write will be judged by that table, not by the one the source was read under.

## A file identifier is a digest of the file

ISO 32000-1, 14.4 gives `/ID[0]` the job of identifying a file's content and `/ID[1]` the job
of changing whenever the file changes, and archives deduplicate on both. A writer that derives
the value from a clock loses determinism. A writer that derives it from a fixed number and the
information dictionary (here BT-1 and two constant producer strings) makes every rendering of
one invoice number the same file, whatever its language, paper, template or attachment. So the
renderer writes the file, hashes it, and writes it again with a digest of the first pass as
both halves. `FacturX.embed` keeps the input's `/ID[0]`, because the pages are the pages that
came in, and gives the result a `/ID[1]` of its own.

**What it costs.** Serializing each file twice. A rendering spends its time on the layout, so
the second pass is cheap beside it, and there is no other way to put the digest inside the file
it is a digest of.

## One declared flavour decides the name, the namespace and the version

Factur-X 1.0, and ZUGFeRD 2.1 and later as the same format under the other name, call the
attachment `factur-x.xml` and declare the `…:invoice:1p0#` extension schema with `Version`
`1.0`. ZUGFeRD 2.0 calls it `zugferd-invoice.xml` and declares `…:invoice:2p0#` with `2p0`. No
specification defines a file that takes the name of one and the schema of the other, so
`EmbedOptions` carries a `HybridFlavour`, not three independent strings. This project therefore
does not write the name `xrechnung.xml`. It is a convention with no container specification
behind it, so there is no declaration to put beside it. ESJ embeds an XRechnung as the profile
XRECHNUNG of either flavour.

## The hybrid PDF carries the ESJ document too, and only where it is true

PDF/A-3 admits an embedded file of any type, and the hybrid invoice specifications admit a
further attachment beside the invoice XML. So the file that already carries the pages a person
reads and the invoice a machine reads can carry the same invoice in the form this project is
about. A consumer that would rather read business terms than a syntax binding then needs no
mapper, and a model extension the syntax has no place for has somewhere to live.

**The XML stays the invoice.** Nothing changes about which file is the legally relevant
invoice. The attachment is declared `Supplement`, not `Alternative`. Every command reads the
invoice out of the XML, and the reader never offers the ESJ document as a candidate. A file
that carried only the ESJ document carries no electronic invoice, and says so.

**Two machine-readable accounts of one invoice have to agree.** Otherwise the file is wrong
about itself, and a recipient acting on one of them acts on something the other denies. The
rule is exact, because a vague one would be useless in both directions. Every value the XML
states stands in the ESJ document unchanged. What the document states beyond that belongs to
terms the binding table of that syntax does not bind. The second half is the whole point (an
extension term is not a disagreement); the first half stops a tampered attachment.

**The producer proves it instead of assuming it.** The embedding reads back the XML it has just
written and checks the rule against the document. Where the writer had to leave a core value
out, it writes no attachment, and the report names the paths. One function decides the rule,
and `esj validate` runs the same one over a file somebody else wrote. So a producer and a
consumer of one file cannot apply two rules. A failure of it is a finding about the container,
never about the invoice.

**Revoked on 2026-09-30.** From 0.9.4 the hybrid PDF carries the invoice XML alone. Two
machine-readable accounts of one invoice mean that every receiver, this project included,
parses both, compares them and decides what a difference means. That is work and a failure
mode on every file, for nothing the XML does not already carry. The XML is the invoice, and
the ESJ document derives from it at any time. A file written by 0.9.0 to 0.9.3 still carries
`invoice.esj.json`, which ESJ reads as an attachment that is not the invoice.

## The PDF rendering is PDF/A-3b, and carries no date

Every PDF `PdfRenderer` writes conforms to ISO 19005-3, conformance level B: fonts embedded,
an output intent with the ICC's sRGB profile inside the file, an XMP packet declaring part and
level. veraPDF validates every rendering of the conformance corpus on every build.

**Why level B and not level A.** Level A also requires a tagged document: a structure tree, a
reading order, a language, alternate text. A layout driven by a term registry can put every
value of a document on a page. For a document it has never seen, it cannot say which of those
values is a heading of which section and in which order a screen reader should read them. A
file that claimed the tagging and got it wrong would be worse for its reader than one that
claims nothing, because assistive software trusts the claim.

**Why an archival profile at all.** An invoice is kept for years, and its rendering is what a
person opens at the end of them. The profile makes that file reproducible on a machine that has
none of the sender's fonts and none of its colour management.

**What it costs.** 3 kilobytes of ICC profile in every file, and a third-party file embedded in
every rendering, not only vendored in the repository. And the one convention of archival files
this project does not follow: an archived document usually records when it was written, and
this one records no date at all. A rendering that changes with the clock cannot be compared
byte for byte between two runs. If you need a creation date, write it onto the file afterwards;
that costs the comparison, not the determinism. The one date a hybrid file does carry is the
invoice's own. The embedded file stream's `/Params /ModDate` is BT-2, which the container
specification asks for and which is a fact of the document, not of the run.

## PDF/A conformance is a declaration, unless a caller lends a validator

`esj validate` reports the PDF/A conformance of a container as `declared, not validated`.
That is no modesty: nothing in this build checks the claim. The reference implementation of
the PDF/A validation model is under a copyleft licence. So it is a test-scope dependency of
`esj-render`, which validates every rendering of the conformance corpus with it on every build,
and it is in no artefact this project publishes. Bundling it would change the licence of the
release. Downloading one at run time would make a validator of a stranger's bytes fetch code
from a network.

**What `--verapdf` is.** You name a veraPDF installation of your own. This tool runs it as a
process of its own inside `--max-runtime` and reads its XML report. It puts the validator's
version, the profile it ran and its verdict in the container block and in `pdfaValidator` of
the JSON report. Naming the validator and its version matters as much as the verdict. A PDF/A
result that did not say which validator produced it would not survive that validator's next
release.

**Why it changes the verdict.** Without the switch, PDF/A conformance is outside what the run
checked, and the report says so instead of answering from the declaration. With it, you asked
the question, so a file the validator rejects is a container that is wrong about itself:
`Container: INVALID`, exit code 1. That leaves the invoice inside untouched. The two verdicts
stay two lines, as they do for every other container finding.

## Two layouts, one content rule

The PDF renderer draws a document in one of two layouts ([`letter-layout.md`](letter-layout.md)).
The generic one has the shape of the semantic model: every term under its own label, every code
as the code it is. A proof wants that, and the report of a validation is built on it. The
letter has the shape of the document a business posts. It exists because the generic page is
right and unusable as an invoice: a recipient looks for the amount due, not for a term called
BT-115. So the letter is the default of `esj render` and of `PdfRenderer`, and the report names
the generic layout instead of taking whatever the default is.

Two layouts could have meant two truths, so they share one rule: **every term occurrence of the
document reaches a page in either.** What the letter has no place of its own for stands under a
closing heading with its label and its semantic path, as in the generic layout. A value that
did not fit the place it was meant for goes there, not nowhere. Neither layout derives: no
carried-forward sums, no computed gross figure, no sentence that combines two values into a
statement. A carried-forward sum in particular is a figure on no page of the document. It would
be the renderer's arithmetic printed as if the invoice had said it, and a reader cannot tell the
two apart.

**Display names are checked-in data.** A letter that printed `H87` where a reader expects
`piece` would be a data sheet again. So the letter writes the name of a code, and lists the
code itself once under the closing heading, so the page hides nothing the document says. The
names are 5 tables in this repository, not a lookup in the locale data of the machine. A
rendering has to be the same bytes on every JDK and under every default locale, and the locale
data of a runtime is neither. The country names were generated once out of a runtime's CLDR
and checked in, with the runtime recorded in the file and a test that regenerates them there
([`sources.md`](sources.md#display-names-of-codes)).

**The head data stands in a reference line, not in a block at the top right.** DIN 5008 puts
the information block into the corner of the sheet that a printed letterhead most often uses for
its own contact details. A layout standing there overprints paper it was never shown. The
reference line has labelled fields in columns across the text width, the way a business letter
has carried *your reference / our reference* for as long as it has had a form. It stands in the
flow between the margins and so keeps clear of the corner without being told about it. That is
why it is the default. The block stays for paper whose top right is free, and `printedHead` is
how it starts below a top right that is not.

**The foot is a distance the template hands over.** Only the sender knows what is printed along
the lower edge of a letterhead. So the template states it once, as the bottom margin, and the
layout stacks everything it puts at the foot of a page above that distance: first the printed
foot the template reserved, then the page footer line, then, on the first page, the seller's
own details. The alternative would be a member for each of them, three numbers to keep in step
where one does.

A template chooses between the layouts and decides what the letter leaves to a sender: the
address field, whether the head data is a line or a block and how far a printed head reaches,
the marks on the paper, where the seller's details stand, whether the payment code is drawn. It
decides no order and no content. That is the layout, and the layout is one.

## One printed code, and it is drawn

The letter carries one machine-readable code and will carry no other: the EPC QR code of a
credit transfer, known in Germany as the GiroCode
([`letter-layout.md`](letter-layout.md#the-payment-code)). A payer with a telephone in one hand
and a letter in the other is the one place where printing data pays for itself. And what the
code carries is not new: the account, the beneficiary, the amount due and the remittance
information are printed beside it in words. A code that carried a figure the letter does not
show would be a second invoice inside the first one. There is no code of the document itself,
on paper or anywhere else. A printed page is a picture of an invoice, the invoice is the
attached file, and this project reads no invoice off a page.

The symbol comes from a dependency, `com.google.zxing:core` under the Apache License. Writing a
QR encoder means writing Reed–Solomon arithmetic, a mask-penalty evaluation and a version table
out of ISO/IEC 18004: 300 lines of code whose defects show up as a code that scans in the test
and not in the bank, the worst kind of defect to ship. The library is pure Java, brings nothing
with it, and ESJ asks it for one thing: the matrix of modules.

The renderer then **draws the modules as squares of the page** instead of placing an image. A
rendering of this project is PDF/A-3b and deterministic. An image object would add a colour
space, a sample depth and an encoder to that claim for no gain, and the same picture is a few
hundred rectangles in the content stream. It is black whatever a template's palette says,
because a scanner reads contrast, not branding.

**Where a condition of the guideline fails, no code is drawn and the letter says nothing.** The
failures: a means of payment that is not a credit transfer, an invoice in another currency, an
account identifier whose check digits do not hold, an amount outside the range the guideline
allows. A code drawn anyway would be one a banking application refuses at the
counter. A sentence explaining its absence would be the renderer talking about itself on
somebody's invoice. The letter says one thing only: where the guideline's own length limit cut
an element short, because there the code carries less than the page beside it.

## Four layers over one registry, and only the top one is written by hand

`SemanticDocument` addresses values by path. `esj-typed` generates a view and an editor with one
accessor and one setter per business term. The constrained builder over that editor turns the
structure of the model into step interfaces, so the terminal step exists only once the mandatory
members have been written. `esj-invoice` puts enums, `Party`, `Line`, `Vat`, profile defaults and
one `build()` on top. You can use each layer on its own, each is expressible through the one
below it, and `Draft.edit(...)` is the way down from the top.

**Why.** The generated layers are the faithful image of EN 16931-1 and have to stay that. An
integrator mapping term by term, and the bindings for other languages, need every term and no
opinions. The words a person uses when writing an invoice are not those of the standard: nobody
knows that a piece is `H87` or that a credit note is `381`. Instead of bending the generator to
taste, the taste lives in a hand-written module the generator knows nothing about, and the
generated builder stays reproducible for any language.

**What the builder does not do.** It enforces structure and nothing else: mandatory terms and
groups, cardinalities, the group a member belongs to, the data type, at least one invoice line.
Business rules, arithmetic, code list membership, VAT logic and the rules of a profile beyond
its cardinalities belong to the validator. Pressing those into the Java type system would
rebuild EN 16931 as unreadable generics, for a check that a rule pack makes better and versions
properly.

**What it costs.** A fourth layer is a fourth place a term can be missing from. The domain API
deliberately does not cover every term of the standard; the escape hatch is how you reach the
long tail, not decoration. A profile that narrows a cardinality carries it as a step of the
generated chain: `InvoiceStepsXrechnung` asks for BT-10 before it asks for anything optional.
The domain API has one chain for every profile and reports the same member as a named finding
at `build()`, the price of one `Party` that fits every profile. And an enum is a snapshot. A
code list that moves on needs a new pack version and newly generated enums, with
`Unit.custom(...)` as the bridge until then.

## A writer never invents a value, and never writes a document its schema refuses

A syntax binding is not a bijection. Where the target syntax requires an element whose content
a business term of the semantic model carries and this document does not state, the writer
leaves it out and names it. A value nobody stated would put into the invoice a fact the sender
never made. The resulting XML is then one that schema does not accept, a property of the
document that [`../conformance/writers/ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md)
measures. The total value added tax amount BT-110 is the case of the corpus, and a business
rule of the standard faults it as well.

## A syntax convention is a fact of the binding, not an invented value

Where the target syntax requires an element that no business term of the semantic model carries
at all, leaving it out produces a document that syntax refuses, for something the invoice never
had a way to state. UBL does this 3 times: the tax scheme of a registration that is not for
value added tax, the purchase order reference of a document that states only a sales order
reference, and the network of a payment card. Over the conformance corpus that refused 46 of 86
instances and 26 of the 40 conversions from a cross industry invoice, for elements no sender
ever filled.

The values written there are now facts of the binding table, in its `conventions` member:
element, condition, value, what asks for the element, and where the value comes from. `FC` is
the UNTDID 1153 code for a fiscal number. The cross industry invoice binding of the same source
model fixes it as the scheme identifier for BT-32, so the two syntaxes say the same thing about
the same registration. `NA` is what Peppol BIS Billing 3.0 records at the other two elements.
None of them is a business statement, none derives from anything the document says, and the
writer names every one it applies in its report. The corpus now measures 85 of 86 accepted and
39 of 40 conversions accepted. Nothing about the semantic documents changed, and all 86 round
trips stay identical. A derived figure is a different thing and stays refused: BT-110 absent is
BT-110 absent, and no writer of this project computes a total the document does not state.

**What it costs.** A reader has to know the one convention written at an element a business
term is bound to. Where `cac:OrderReference/cbc:ID` carries `NA` beside a `cbc:SalesOrderID`,
the streaming reader does not read it as BT-13 and records `CONVENTION_NOT_READ`. The price: a
document whose BT-13 really is the two letters `NA`, beside a BT-14, does not survive a UBL
round trip. The writer says so with a `VALUE_READS_AS_CONVENTION` note when it meets that
value. The cross industry invoice writes the two references in two elements and carries it. The
`conventions` member is where that trade is written down, so a second convention has to be
argued at the table, not in a writer.

One case is neither: a value the syntax writes only inside the element of a business group the
document does not state, where the schema also requires content of that element that only that
group's terms carry. The payment due date of a UBL credit note is the case of this release. UBL
has no `cbc:DueDate` on a credit note, so the table binds BT-9 inside `cac:PaymentMeans`, whose
type requires the payment means code of BT-81. And BT-9 stands outside BG-16 in the semantic
model. Writing it alone would make a document the UBL schema refuses, for a term no element of
that syntax can hold without the group. The writer therefore counts it as a loss, leaves it out
and names it, and the document it produces is one the schema accepts. The other syntax carries
it: the same invoice written as a cross industry invoice keeps BT-9.

**What it costs `esj validate`.** An ESJ input reaches the official artefacts through a writer,
so each of these decides whether their verdict is about the invoice:

- a loss makes the written XML a different document (`term-not-in-syntax`), unless every term
  left behind belongs to a registry that declares it untransported;
- a term the syntax requires and the document does not state makes it a rendition its own
  schema refuses (`term-not-stated`), as does an element no term names and no convention
  covers (`element-not-in-model`).

In those cases the artefacts stand down, the run ends `INDETERMINATE`, and `--via` chooses the
other syntax ([`validation.md`](validation.md#verdict-and-exit-code)). A document that needed
only conventions is complete, and the artefacts judge it.

## An invoice is net, and what a consumer was shown is an extension

EN 16931-1 states an invoice in net terms: BT-146 is a net unit price, BT-131 a net line amount,
BG-23 the VAT breakdown per category, and BT-112 to BT-115 the totals over them. A consumer buys
at a gross price, and a gross invoice is allowed, so a document has to be able to record what
was displayed. It records it in 4 terms of an extension namespace of this project, `B2C`. The
core terms keep their EN 16931 meaning beside them, BT-114 among them: it stays the invoice
rounding amount and never becomes a balancing account ([`b2c.md`](b2c.md)).

**Why an extension.** An extension may add information and may not restate or replace a core
term (`SPEC.md` section 4.6, rule 4). "The customer was shown 99.99 including VAT" is
information the standard does not carry. A document that drops the extension is still a
conformant invoice with the same net figures. Writing a gross figure into a core term, or adding
a `V-*` field beside one, would make every reader of the core model wrong about what it says.

**Why the extension states no arithmetic.** Presence of a term means the figure was shown or
agreed, and nothing more. A gross line total does not follow from a gross unit price where the
line carries a base quantity or allowances. A gross invoice total does not follow from the lines
where the document carries allowances or charges. A merchant who rounded at the unit, at the
line or at the total tells the truth in each case. A specification that stated such a relation
would be false for the documents it is meant to describe. The derivation belongs to a policy of
the SDK, which names its preconditions, reports what it wrote and refuses instead of guessing.

**Why the terms do not travel.** A gross-priced invoice travels in UBL and in CII as the net
values of the core terms, with the difference in BT-114, so the total with VAT is the figure the
customer was shown. The 4 terms record that price statement, and no syntax has an element for
it. So `model/b2c/0.1.json` declares `"transport": "none"` (`SPEC.md` section 10). The XML
written from such a document is the whole invoice. `esj validate` runs the official artefacts
over it, counts that check instead of answering `term-not-in-syntax`, and names the terms that
stayed behind, so nobody reads the verdict as a claim that the XML carries them. An extension a
syntax does bind makes no such declaration. A document using the XRechnung extension still ends
`INDETERMINATE`, because there the missing elements are ones that were meant to be there.

**What it costs.** If you want a net invoice out of gross figures, you have to say which policy,
because the answer differs by policy and nothing in the document decides it. A policy that meets
a precondition it cannot satisfy stops with an exception instead of producing a plausible
invoice. The consistency check needs the policy as an argument for the same reason: it checks
the guarantees a policy gave, not relations the extension states.

## A document keeps the format version it was written in

**Decided on 2026-10-16, for 1.0.0-rc.1.** The format became 1.0 with no change but the value of
`version`. A reader of 1.0 reads a document of 0.1 by the rules of 1.0. The canonical form
writes the version a document carries, not the one the implementation writes. A document read
as 0.1 is canonicalized, edited, given derived totals and written up to another edition as 0.1.
Only a document created from nothing or imported from another syntax is 1.0.

Writing every document out as 1.0 would have put one version into every canonical form. It
would also have changed the document digest of every stored document the moment it was read
again. A digest a database keeps as a key, or one a recipient compares with the one it was
sent, would stop matching content nobody had changed. Persistence is one of the uses the format
is for, so the digests stay.

**What it costs.** Two documents with the same values can differ in their document digest by
their version alone, and stay that way, because no command rewrites a 0.1 document as 1.0. The
semantic digest does not cover the version, and that digest answers whether two documents carry
the same invoice.

## Non-goals

ESJ does not:

- replace EN 16931, redefine invoice semantics or redefine taxation;
- claim to be the authority on the Schematron business rules; the CEN/TC 434 artefacts and
  the CIUS owners are;
- make a document legally valid by itself;
- read an invoice off the page of a PDF: it takes the electronic invoice out of a hybrid file
  and says so when there is none, and there is no optical character recognition, no layout
  analysis and no heuristic extraction anywhere in it;
- preserve arbitrary XML syntax details, or guarantee round trips for data outside the
  EN 16931 semantic model;
- replace national profiles such as XRechnung.