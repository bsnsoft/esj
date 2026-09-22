# Design decisions and trade-offs

Every heading is a decision that could have gone the other way, and the paragraph under it
says what was given up for it. `SPEC.md` is the normative text; nothing here changes it.

## What the format is good for

The properties the decisions below were made for; every one of them cost something:

- **Compact** — no XML namespace and wrapper overhead.
- **Semantic** — addresses BT/BG concepts directly, not syntax elements.
- **Binding-independent** — business logic need not depend on UBL or CII.
- **Database-friendly** — stores as JSON/JSONB or as path/value rows unchanged
  ([`storage.md`](storage.md)).
- **Deterministic** — every value has one stable semantic address, and every content one
  canonical byte sequence.
- **Diff-friendly** — a semantic change shows up as a changed path or value.
- **Test-friendly** — two syntax bindings can be compared at the semantic level.
- **Streaming-friendly** — a consumer can process path plus value without building a full
  invoice object graph.
- **Implementation-friendly** — a small mapper binds semantic paths to a domain model.
- **Change-resistant** — persistence is keyed to EN 16931 identifiers, not to Java classes.
- **Reduced XML exposure** — native ESJ consumers need no XML processing. Import adapters
  that read UBL or CII still need secure XML handling; ESJ does not remove that requirement,
  it confines it to the adapter.

## A value is a string, never a JSON number

Amounts, quantities, percentages and dates are written as JSON strings with a fixed
grammar (`SPEC.md` sections 6.4 and 6.5), never as JSON numbers.

**Why.** A JSON number has no spelling. `1.50`, `1.5` and `15E-1` are the same number, and a
parser that hands over a `double` has already changed the document: `0.1 + 0.2` is not `0.3`
in binary floating point, and an invoice total that does not add up is not an academic
problem. A string has exactly one canonical spelling, survives every parser unchanged, and
makes the canonical form of section 7 — and with it the two digests of section 8 — a property
of the content rather than of the library that read it.

**What it costs.** A consumer cannot compute with a value the moment it has parsed the
document; it converts first, to whatever decimal type its language offers. Generic JSON
tooling shows `"84.03"` where a reader expects `84.03`, and a JSON Schema `maximum` cannot be
applied to it. Neither cost falls on correctness, and the alternative does.

## A value carries no type token

An earlier draft wrote every value as an object with a `type` member naming its semantic
data type. It is gone: a value is a JSON string, or an object only where the semantic model
gives the term supplementary components (`SPEC.md` section 6.1).

**Why.** The type is already written down, once, in the registry: `model/en16931/2017.json`
records the semantic data type of every business term, and `semanticModel` in the envelope
says which registry applies. Repeating it per value made every document carry the same fact a
few hundred times, made two documents of one invoice differ when one of them spelled the type
differently, and — worst — made the document able to *contradict* the model. A document that
called BT-131 a `Text` was a document with no defined meaning.

**What it costs.** A reader alone can no longer say that `100.00` is a badly spelled decimal
or that `2026-02-30` is not a day: without the registry there is nothing that says those two
strings are a decimal and a date. Those checks therefore moved from layer L1 to layer L2
(`SPEC.md` sections 6.2 and 9.2), and a party that wants them has to load a registry. In
exchange the layers became honest about what each of them actually knows, and
`schema/esj-en16931-2017.schema.json` gives JSON Schema users the same typing without one.

## Occurrence indices are positions, not identities

A repeatable term or group carries a zero-based index in its path: `/BG-25/0/BT-131` is the
net amount of the first invoice line. The index is a position in a dense sequence
(`SPEC.md` sections 5.3 and 5.4), not a key.

**Why.** EN 16931 gives a line BT-126, an identifier of its own, but it gives no such thing
to most repeatable groups, and a binding may not invent identities the model does not have. A
position is what the model actually provides, it is what UBL and CII both preserve, and
density makes the canonical path order of section 7.4 total — so one invoice has one canonical
form whichever syntax it was read from, which is the litmus test the conformance corpus runs.

**What it costs.** Deleting the first of three lines renumbers the other two: every path under
`/BG-25/1` and `/BG-25/2` changes, although nothing about those lines did. A diff of the two
documents shows every value of both lines as changed, and an external reference to
`/BG-25/2/BT-131` — a comment, a row in another table, an approval — now points at a different
line. The editor of `esj-typed` therefore removes an occurrence and closes the gap in one
operation rather than leaving the caller to do it, and a system that needs a stable handle
stores BT-126 rather than the index. The alternative, a synthetic key inside the path, would
have made the same invoice serialize differently in two systems and would have ended the one
property the whole format is built on.

## Two editions, one registry file each

`semanticModel` has two defined values: the 2017 line with amendment A1:2019 and corrigendum
AC:2020 applied, which is the default everywhere, and EN 16931-1:2026.

**Why.** An edition is data and not code: one registry file per edition, the edition named in
the envelope and in the registry header, the paths interpreted against the registry that
matches. The 2017 edition stays the default because the CEN validation artefacts, the KoSIT
test suite and the syntax bindings are written against it, and because its text is freely
obtainable (`docs/sources.md`). The files that carry facts of the 2026 edition are listed in
`model/en16931/2026.paths`, and the Maven profile `without-edition-2026` builds a distribution
without them.

**What it costs.** For a document of the 2026 edition ESJ carries the content, reads it,
canonicalizes it, hashes it and validates it structurally. There is no conversion to UBL or
CII, because no public syntax binding of the terms that edition adds exists, and no rule pack,
because no official validation artefact for its rules exists. A build that holds no registry
for the edition a document names reports `ESJ-L2-EDITION-UNKNOWN` and reaches no verdict
rather than guessing (`SPEC.md` 4.4 and 9.2).

## How many decimals a term allows is a fact of its edition

The registry records the bound — `maxDecimals` where the edition fixes a number, and
`maxDecimalsRule` where it derives the number from the currency of the document — and layers
L1, L2 and L3 check none of them.

**Why.** The two editions do not agree on the bound. In the 2017 edition an amount carries at
most two fraction digits and the cap reads as a property of the semantic data type; in the 2026
edition the cap of an amount follows the minor unit of its currency, a unit price is allowed two
digits more, and one amount term is bounded at six. A bound that moves between editions is not a
property of the type, and a structural layer that enforced it would reach a different verdict on
the same digits depending on which registry a build happened to carry. The bound belongs with
the arithmetic it serves, and that arithmetic is a rule pack.

**What it costs.** `validate` accepts a value whose scale exceeds what the edition allows, and
only the rule pack of that edition objects. The registry states the bound so that a writer, a
form or a database projection can hold to it before an invoice goes out, and `upgrade` reports
every value that exceeds the bound of the target edition rather than rounding it: a migration
that changed a figure would be a repair, and nothing in ESJ repairs.

## `upgrade` is a table, and never silent

`esj upgrade` reads what differs between two editions from `model/en16931/upgrade-2017-2026.json`
and writes the result of applying it; no pair of editions is described in Java.

**Why.** What moved between two editions is a fact about two registries, checkable by reading
one file, and reusable by a binding in another language that will never run this implementation.
Six paths move upwards — one term into a new group, one group under a new one, and two whose
occurrence index appeared because the edition widened their cardinality — and every other value
is carried byte for byte.

**What it costs.** The interesting cases are the ones the table cannot decide, and the command
reports rather than resolving them. A component the target edition requires and the document
does not carry is an open point; the specification identifier in BT-24 is a statement about a
specification this tool did not check, so it stays as it stands unless the caller writes another
one with `--specification`; a value the target edition has no address for makes the run refuse
until the caller names that path with `--drop`. The result is a command that sometimes ends
without a document, which is the point: an upgrade that quietly dropped a business term would be
the failure mode this project exists to prevent.

## One typed view per edition, in a package of its own

`de.bsnsoft.esj.typed` is the view of the 2017 edition, `…typed.v2026` the view of
the 2026 one, each generated from that edition's registry with an `En16931` of its own. The
types the two share — the value records, the handles, the two exceptions and the runtime a
generated accessor calls — lie in the first package and in `…typed.runtime`.

**Why.** The typed view exists to make a wrong path unrepresentable. A single view over both
editions would offer `/BT-20` at the root of a 2026 document, where no such path exists, and
would have to answer "is this term in this edition?" at run time — the question the view was
built to remove. `edit` therefore refuses a document of another edition, while `view`, being a
reader, stays blind to it as a reader must (`SPEC.md` 3).

**What it costs.** Twice the generated source, and a caller who handles both editions writes
against both packages. `derive` is emitted for the 2017 view alone, because the derivation
policies are written against that edition's arithmetic; a policy for the 2026 edition comes with
the rule pack for it.

## The registry is the single source of structure

Which terms exist, what they are called, how often they may occur, what data type each one
has and which supplementary components it carries are facts of the registry, not of
`SPEC.md` (`SPEC.md` section 10).

**Why.** A specification that restates a term table goes out of date against it, and two
copies of one fact are one fact and one bug. With the facts in a machine-readable file the
validator, the code generator, the typed view and the generated schema all read the same one,
and a disagreement between them is impossible rather than merely unlikely.

**What it costs.** The registry has to be right, and nothing outside it can catch it being
wrong. That is why `model/registry.schema.json` constrains it, why the loader refuses
combinations the semantic data types do not have, and why `model/README.md` records how each
column was derived and cross-checked. It also means ESJ ships a data file that a reader must
have to reach layers L2 and L3 at all; layer L1 is deliberately usable without one.

## Business rules are a layer of their own, and are checked

The BR-*, BR-CO-*, BR-DEC-* and BR-CL-* rules of EN 16931 are not part of ESJ conformance. A
document may be structurally conformant under `SPEC.md` and violate any of them, and a tool
that reported a broken sum as an ESJ format error would be saying something false about the
format. They are checked all the same: `esj validate` runs the official Schematron of the
document's profile over an XML input and the rule pack of [`rules/`](../rules/README.md) over
the semantic document, and both report into layers of their own with the pack named on every
finding.

**Why apart.** They are a different kind of statement. Structure is decided by the bytes and
the registry and does not change; a business rule is decided against code lists and arithmetic
that move over time, and a verdict that expires does not belong in the layer that says whether
a document is well formed. Mixing them would mean an archived invoice getting two different
structural verdicts from two releases of one tool. Keeping them apart is what lets both run in
one command without either claiming the other's authority.

**Why a pack of our own, beside the artefacts.** An ESJ document that was never XML has no
artefact that will ever look at it;
`examples/invalid/arithmetic-mismatch.esj.json` is the fixture that made the gap visible, and
it is now reported. A rule written over business terms serves a UBL invoice, a CII invoice and
a native document alike, which is the long-term reason the semantic model exists at all.

**What it costs.** Two engines can report one rule twice over an XML input, and the report
keeps both rather than choosing between them. A native finding carries the level its rule
declares, and the verdict then applies the level the profile of the document gives that rule,
to a finding of the pack as to one of an artefact: the pack stays profile-agnostic and one
document gets one answer. Both are documented where a reader meets them, in
[`validation.md`](validation.md) and in
[`../conformance/rules/corpus.md`](../conformance/rules/corpus.md), and
[`../conformance/rules/ledger.md`](../conformance/rules/ledger.md) is the measurement against
the official artefacts.

**A scenario is a profile and a syntax.** The level tables the specifications publish are written
per scenario, so one profile may level a rule for one syntax and say nothing about it for the
other: the XRechnung extension profile levels BR-CO-16 down for a UBL invoice and leaves the
standard's level standing for a cross industry invoice, so an invoice that trips it is `VALID` as
UBL and `INVALID` as CII. That is the published fact and not a decision of this tool, which levels
a document by the table of the syntax it arrived in — the syntax a reader of that file will judge
it by. Where a command writes the document into the other syntax, `esj convert` and
`esj render --embed` say so in an `info:` line if the target's table is the stricter one, because
the file they write will be judged by that table and not by the one the source was read under.

## A file identifier is a digest of the file

ISO 32000-1, 14.4 gives `/ID[0]` the job of identifying a file's content and `/ID[1]` the job
of changing whenever the file changes; archives deduplicate on both. A writer that derives the
value from a clock loses determinism, and one that derives it from a fixed number and the
information dictionary — which here is BT-1 and two constant producer strings — makes every
rendering of one invoice number the same file whatever its language, paper, template or
attachment. So the renderer writes the file, hashes it, and writes it again with a digest of
the first pass as both halves; `FacturX.embed` keeps the input's `/ID[0]`, because the pages
are the pages that came in, and gives the result a `/ID[1]` of its own.

**What it costs.** Serializing each file twice. The layout is what a rendering spends its time
on, so the second pass is cheap beside it, and the digest cannot be inside the file it is a
digest of any other way.

## One declared flavour decides the name, the namespace and the version

Factur-X 1.0 — and ZUGFeRD 2.1 and later, the same format under the other name — calls the
attachment `factur-x.xml` and declares the `…:invoice:1p0#` extension schema with `Version`
`1.0`; ZUGFeRD 2.0 calls it `zugferd-invoice.xml` and declares `…:invoice:2p0#` with `2p0`. No
specification defines a file that takes the name of one and the schema of the other, so
`EmbedOptions` carries a `HybridFlavour` and not three independent strings. `xrechnung.xml` is
therefore not a name this project writes: it is a convention with no container specification
behind it, so there is no declaration to put beside it, and an XRechnung is embedded as the
profile XRECHNUNG of either flavour.

## The hybrid PDF carries the ESJ document too, and only where it is true

PDF/A-3 admits an embedded file of any type and the hybrid invoice specifications admit a
further attachment beside the invoice XML, so the file that already carries the pages a person
reads and the invoice a machine reads can carry the same invoice in the form this project is
about. A consumer that would rather read business terms than a syntax binding then needs no
mapper, and a model extension the syntax has no place for has somewhere to live.

**The XML stays the invoice.** Nothing about which file is the legally relevant invoice
changes: the attachment is declared `Supplement` rather than `Alternative`, every command reads
the invoice out of the XML, and the reader never offers the ESJ document as a candidate. A file
that carried only the ESJ document carries no electronic invoice, and says so.

**Two machine-readable accounts of one invoice have to agree**, or the file is wrong about
itself and a recipient acting on one of them acts on something the other denies. The rule is
exact because a vague one would be useless in both directions: every value the XML states
stands in the ESJ document unchanged, and what the document states beyond that belongs to terms
the binding table of that syntax does not bind. The second half is the whole point — an
extension term is not a disagreement — and the first half is what stops a tampered attachment.

**The producer proves it rather than assuming it.** The embedding reads back the XML it has
just written and checks the rule against the document; where the writer had to leave a core
value out, no attachment is written and the report names the paths. One function decides the
rule, and `esj validate` runs the same one over a file somebody else wrote, so a producer and a
consumer of one file cannot be applying two rules. A failure of it is a finding about the
container and never about the invoice.

## The PDF rendering is PDF/A-3b, and carries no date

Every PDF `PdfRenderer` writes conforms to ISO 19005-3, conformance level B: fonts embedded,
an output intent with the ICC's sRGB profile inside the file, an XMP packet declaring part and
level. veraPDF validates every rendering of the conformance corpus on every build.

**Why level B and not level A.** Level A additionally requires a tagged document — a structure
tree, a reading order, a language, alternate text. A layout driven by a term registry can put
every value of a document on a page; it cannot say, for a document it has never seen, which of
those values is a heading of which section and in which order a screen reader should read them.
A file that claimed the tagging and got it wrong would be worse for its reader than one that
claims nothing, because the claim is what assistive software trusts.

**Why an archival profile at all.** An invoice is kept for years and its rendering is what a
person will open at the end of them. The profile is what makes that file reproducible on a
machine that has none of the sender's fonts and none of its colour management.

**What it costs.** Three kilobytes of ICC profile in every file, and a third-party file
embedded in every rendering rather than only vendored in the repository. And the one convention
of archival files this project does not follow: an archived document usually records when it
was written, and this one records no date at all, because a rendering that changes with the
clock cannot be compared byte for byte between two runs. A caller who needs a creation date
writes it onto the file afterwards, which costs the comparison rather than the determinism.
The one date a hybrid file does carry is the invoice's own: the embedded file stream's
`/Params /ModDate` is BT-2, which the container specification asks for and which is a fact of
the document rather than of the run.

## PDF/A conformance is a declaration, unless a caller lends a validator

`esj validate` reports the PDF/A conformance of a container as `declared, not validated`, and
that is not modesty: nothing in this build checks the claim. The reference implementation of
the PDF/A validation model is under a copyleft licence, so it is a test-scope dependency of
`esj-render` — where every rendering of the conformance corpus is validated with it on every
build — and it is in no artefact this project publishes. Bundling it would change the licence
of the release; downloading one at run time would make a validator of a stranger's bytes fetch
code from a network.

**What `--verapdf` is.** The caller names a veraPDF installation of their own, this tool runs
it as a process of its own inside `--max-runtime`, reads its XML report, and puts its version,
the profile it ran and its verdict in the container block and in `pdfaValidator` of the JSON
report. Naming the validator and its version matters as much as the verdict: a PDF/A result
that did not say which validator produced it would not survive that validator's next release.

**Why it changes the verdict.** Without the switch, PDF/A conformance is outside what the run
checked, and the report says so rather than answering from the declaration. With it, the caller
asked the question, so a file the validator rejects is a container that is wrong about itself:
`Container: INVALID`, exit code 1. The invoice inside it is untouched by that — the two
verdicts stay two lines, as they do for every other container finding.

## Two layouts, one content rule

The PDF renderer draws a document in one of two layouts ([`letter-layout.md`](letter-layout.md)).
The generic one is the shape of the semantic model — every term under its own label, every code
as the code it is — which is what a proof wants and what the report of a validation is built
on. The letter is the shape of the document a business posts, and the reason it exists is that
the generic page is right and unusable as an invoice: a recipient is looking for the amount due,
not for a term called BT-115.

Two layouts could have meant two truths, so they share one rule: **every term occurrence of the
document reaches a page in either.** What the letter has no place of its own for stands under a
closing heading with its label and its semantic path, as it does in the generic layout, and a
value that did not fit the place it was meant for goes there rather than nowhere. Neither
layout derives: no carried-forward sums, no computed gross figure, no sentence that combines two
values into a statement. Carried-forward sums in particular are a figure that is on no page of
the document — it would be the renderer's arithmetic printed as if the invoice had said it, and
a reader cannot tell the two apart.

**Display names are checked-in data.** A letter that printed `H87` where a reader expects
`piece` would be a data sheet again, so the letter writes the name of a code — and lists the
code itself once under the closing heading, so the page hides nothing the document says. The
names are five tables in this repository, not a lookup in the locale data of the machine: a
rendering has to be the same bytes on every JDK and under every default locale, and the locale
data of a runtime is neither of those. The country names were generated once out of a runtime's
CLDR and checked in, with the runtime recorded in the file and a test that regenerates them
there ([`sources.md`](sources.md#display-names-of-codes)).

**The head data stands in a reference line, not in a block at the top right.** DIN 5008 puts the
information block into the corner of the sheet that a printed letterhead most often uses for its
own contact details, and a layout standing there is a layout that overprints paper it was never
shown. The reference line — labelled fields in columns across the text width, the way a business
letter has carried *your reference / our reference* for as long as it has had a form — stands in
the flow between the margins and so keeps clear of the corner without being told about it. That
is why it is the default; the block stays for paper whose top right is free, and `printedHead`
is how it starts below one that is not.

**The foot is a distance the template hands over.** What is printed along the lower edge of a
letterhead is known to the sender and to nobody else, so the template states it once as the
bottom margin, and everything the layout puts at the foot of a page is stacked above that
distance: first the printed foot the template reserved, then the page footer line, then, on the
first page, the seller's own details. The alternative would be a member for each of them, three
numbers to keep in step where one does.

A template chooses between the layouts and decides what the letter leaves to a sender — the
address field, whether the head data is a line or a block and how far a printed head reaches,
the marks on the paper, where the seller's details stand, whether the payment code is drawn. It
decides no order and no content: that is the layout, and the layout is one.

## One printed code, and it is drawn

The letter carries one machine-readable code and will carry no other: the EPC QR code of a
credit transfer, known in Germany as the GiroCode
([`letter-layout.md`](letter-layout.md#the-payment-code)). It is there because a payer with a
telephone in one hand and a letter in the other is the one place where printing data pays for
itself, and because what it carries is not new — the account, the beneficiary, the amount due
and the remittance information are printed beside it in words. A code that carried a figure the
letter does not show would be a second invoice inside the first one. There is no code of the
document itself, on paper or anywhere else: a printed page is a picture of an invoice, the
invoice is the attached file, and this project reads no invoice off a page.

The symbol comes from a dependency, `com.google.zxing:core` under the Apache License. Writing a
QR encoder is writing Reed–Solomon arithmetic, a mask-penalty evaluation and a version table out
of ISO/IEC 18004 — three hundred lines of code whose defects show up as a code that scans in the
test and not in the bank, which is the worst kind of defect to ship. The library is pure Java,
brings nothing with it, and is asked for one thing: the matrix of modules.

The modules are then **drawn as squares of the page**, not placed as an image. A rendering of
this project is PDF/A-3b and deterministic; an image object would add a colour space, a sample
depth and an encoder to that claim for no gain, and the same picture is a few hundred rectangles
in the content stream. It is black whatever a template's palette says, because a scanner reads
contrast rather than branding.

**Where a condition of the guideline fails, no code is drawn and the letter says nothing.** A
means of payment that is not a credit transfer, an invoice in another currency, an account
identifier whose check digits do not hold, an amount outside the range the guideline allows: a
code drawn anyway would be one a banking application refuses at the counter, and a sentence
explaining its absence would be the renderer talking about itself on somebody's invoice. The
one thing the letter does say is where the guideline's own length limit cut an element short,
because there the code carries less than the page beside it.

## Four layers over one registry, and only the top one is written by hand

`SemanticDocument` addresses values by path; `esj-typed` generates a view and an editor with one
accessor and one setter per business term; the constrained builder over that editor turns the
structure of the model into step interfaces, so that the terminal step exists only once the
mandatory members have been written; `esj-invoice` puts enums, `Party`, `Line`, `Vat`, profile
defaults and one `build()` on top. Each layer is usable on its own, each is expressible through
the one below it, and `Draft.edit(...)` is the way down from the top.

**Why.** The generated layers are the faithful image of EN 16931-1 and have to stay that — an
integrator mapping term by term, and the bindings generated for other languages in a later
release, need every term and no opinions. The words a person uses when writing an invoice are
not those of the standard: nobody knows that a piece is `H87` or that a credit note is `381`.
Rather than bend the generator to taste, the taste lives in a hand-written module that the
generator knows nothing about, and the generated builder stays reproducible for any language.

**What the builder does not do.** It enforces structure and nothing else: mandatory terms and
groups, cardinalities, the group a member belongs to, the data type, at least one invoice line.
Business rules, arithmetic, code list membership, VAT logic and the rules of a profile beyond
its cardinalities are the validator's. Pressing those into the Java type system would rebuild
EN 16931 as unreadable generics for a check that a rule pack makes better and versions properly.

**What it costs.** A fourth layer is a fourth place a term can be missing from, and the domain
API deliberately does not cover every term of the standard — the escape hatch is not decoration,
it is how the long tail is reached. A profile that narrows a cardinality carries it as a step of
the generated chain — `InvoiceStepsXrechnung` asks for BT-10 before it asks for anything optional
— while the domain API has one chain for every profile and reports the same member as a named
finding at `build()`, which is the price of one `Party` that fits every profile. And an enum is a
snapshot: a code list that moves on needs a new pack version and newly generated enums, with
`Unit.custom(...)` as the bridge until then.

## A writer never invents a value, and never writes a document its schema refuses

A syntax binding is not a bijection. Where the target syntax requires an element whose content a
business term of the semantic model carries and this document does not state, the writer leaves
it out and names it: supplying a value nobody stated would put into the invoice a fact the
sender never made. The resulting XML is then one that schema does not accept, which is a
property of the document and is measured in
[`../conformance/writers/ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md). The total
value added tax amount BT-110 is the case of the corpus, and a business rule of the standard
faults it as well.

## A syntax convention is a fact of the binding, not an invented value

Where the target syntax requires an element that no business term of the semantic model carries
at all, leaving it out produces a document that syntax refuses for something the invoice never
had a way to state. UBL does this three times: the tax scheme of a registration that is not for
value added tax, the purchase order reference of a document that states only a sales order
reference, and the network of a payment card. Over the conformance corpus that was 46 of 86
instances refused, and 26 of the 40 conversions from a cross industry invoice — for elements
no sender ever filled.

The values written there are now facts of the binding table, in its `conventions` member:
element, condition, value, what asks for the element, and where the value comes from. `FC` is
the UNTDID 1153 code for a fiscal number and the scheme identifier the cross industry invoice
binding of the same source model fixes for BT-32, so the two syntaxes say the same thing about
the same registration; `NA` is what Peppol BIS Billing 3.0 records at the other two elements.
None of them is a business statement, none is derived from anything the document says, and the
writer names every one it applies in its report. The corpus now measures 85 of 86 accepted and
39 of 40 conversions accepted; nothing about the semantic documents changed, and all 86 round
trips stay identical.

A derived figure is a different thing and stays refused: BT-110 absent is BT-110 absent, and no
writer of this project computes a total the document does not state.

**What it costs.** A reader has to know the one convention written at an element a business
term is bound to. The streaming reader does not read `cac:OrderReference/cbc:ID` as BT-13 where
it carries `NA` beside a `cbc:SalesOrderID`, and records `CONVENTION_NOT_READ`. The price is
that a document whose BT-13 really is the two letters `NA`, beside a BT-14, does not survive a
UBL round trip: the writer says so with a `VALUE_READS_AS_CONVENTION` note when it meets that
value, and the cross industry invoice, which writes the two references in two elements, carries
it. The `conventions` member is where that trade is written down, so a second convention has to
be argued at the table rather than in a writer.

One case is neither: a value the syntax writes only inside the element of a business group the
document does not state, where the schema also requires content of that element that only that
group's terms carry. The payment due date of a UBL credit note is the case of this release —
UBL has no `cbc:DueDate` on a credit note, so the table binds BT-9 inside `cac:PaymentMeans`,
whose type requires the payment means code of BT-81, and BT-9 stands outside BG-16 in the
semantic model. Writing it alone would make a document the UBL schema refuses, for a term no
element of that syntax can hold without the group. It is therefore counted as a loss, left out
and named, and the document the writer produces is one the schema accepts. The other syntax
carries it: the same invoice written as a cross industry invoice keeps BT-9.

**What it costs `esj validate`.** An ESJ input reaches the official artefacts through a writer, so
each of these decides whether their verdict is about the invoice. A loss makes the written XML a
different document (`term-not-in-syntax`), unless every term left behind belongs to a registry that
declares it untransported; a term the syntax requires and the document does not state makes it a
rendition its own schema refuses (`term-not-stated`), as does an element no term names and no
convention covers (`element-not-in-model`). In those cases the artefacts stand down, the run ends
`INDETERMINATE`, and `--via` chooses the other syntax
([`validation.md`](validation.md#verdict-and-exit-code)). A document that needed only conventions
is complete, and the artefacts judge it.

## An invoice is net, and what a consumer was shown is an extension

EN 16931-1 states an invoice in net terms: BT-146 is a net unit price, BT-131 a net line amount,
BG-23 the VAT breakdown per category, and BT-112 to BT-115 the totals over them. A consumer buys
at a gross price, and a gross invoice is allowed, so a document has to be able to record what was
displayed. It records it in four terms of an extension namespace of this project, `B2C`, and the
core terms keep their EN 16931 meaning beside them — BT-114 among them, which stays the invoice
rounding amount and never becomes a balancing account ([`b2c.md`](b2c.md)).

**Why an extension.** An extension may add information and may not restate or replace a core term
(`SPEC.md` section 4.6, rule 4). "The customer was shown 99.99 including VAT" is information the
standard does not carry, and a document that drops the extension is still a conformant invoice
with the same net figures. Writing a gross figure into a core term, or adding a `V-*` field beside
one, would make every reader of the core model wrong about what it says.

**Why the extension states no arithmetic.** Presence of a term means the figure was shown or
agreed, and nothing more. A gross line total does not follow from a gross unit price where the
line carries a base quantity or allowances, a gross invoice total does not follow from the lines
where the document carries allowances or charges, and a merchant who rounded at the unit, at the
line or at the total is telling the truth in each case. The specification that stated such a
relation would be false for the documents it is meant to describe; the derivation belongs to a
policy of the SDK, which names its preconditions, reports what it wrote and refuses rather than
guesses.

**Why the terms do not travel.** A gross-priced invoice travels in UBL and in CII as the net values
of the core terms, with the difference in BT-114, so that the total with VAT is the figure the
customer was shown. The four terms record that price statement, which no syntax has an element for,
so `model/b2c/0.1.json` declares `"transport": "none"` (`SPEC.md` section 10). The XML written from
such a document is the whole invoice: `esj validate` runs the official artefacts over it, counts
that check instead of answering `term-not-in-syntax`, and names the terms that stayed behind, so
that the verdict is not read as a claim that the XML carries them. An extension a syntax does bind
makes no such declaration, and a document using the XRechnung extension still ends
`INDETERMINATE`, because there the missing elements are ones that were meant to be there.

**What it costs.** A caller who wants a net invoice out of gross figures has to say which policy,
because the answer differs by policy and nothing in the document decides it. A policy that meets a
precondition it cannot satisfy stops with an exception instead of producing a plausible invoice,
and the consistency check needs the policy as an argument for the same reason: what it checks are
the guarantees a policy gave, not relations the extension states.

## Non-goals

ESJ does not:

- replace EN 16931, redefine invoice semantics or redefine taxation;
- claim to be the authority on the Schematron business rules — the CEN/TC 434 artefacts and
  the CIUS owners are;
- make a document legally valid by itself;
- read an invoice off the page of a PDF: it takes the electronic invoice out of a hybrid file
  and says so when there is none, and there is no optical character recognition, no layout
  analysis and no heuristic extraction anywhere in it;
- preserve arbitrary XML syntax details, or guarantee round trips for data outside the
  EN 16931 semantic model;
- replace national profiles such as XRechnung.