# Hybrid PDF

`esj-pdf` reads the electronic invoice out of a hybrid PDF; [`pdf-output.md`](pdf-output.md)
writes one. The PDF is the container and the invoice is the XML attached to it. Once ESJ holds
the attachment, it reads it exactly as it reads the same XML on its own.

## The boundary

**Only an embedded electronic invoice is read. Nothing is extracted from the page.**

ESJ does no optical character recognition, no layout analysis and no heuristic or model-based
extraction, and none is planned. A PDF whose invoice exists only as printed text carries no
instance of the semantic model, so ESJ answers that there is none.

A PDF falls into one of three cases:

| Case | What happens |
|---|---|
| it carries one attachment whose bytes are an electronic invoice | the invoice is read and the container is reported beside it |
| it carries several, one invoice and one attachment that was not classified, or two files under one name, and nothing says which | nothing is chosen: the candidates are listed and the caller is asked |
| it carries none | that is the answer |

## Where the invoice may lie, and what counts as one

A reader of a hybrid invoice may take its invoice from five places, so ESJ enumerates all five:

1. the embedded files name tree, walked entry by entry through `/Names` and `/Kids` rather
   than loaded into a map;
2. the associated files array of the document;
3. page by page, in the order of the page tree, the associated files array of the page;
4. the file specification of every file attachment annotation;
5. the associated files array of every annotation.

ESJ reads every stream under `/F`, `/UF`, `/DOS`, `/Mac` and `/Unix` of a file specification,
not only the first. **One attachment is one embedded file stream.** A stream that two places
refer to is one attachment and one candidate, however many places and file specifications name
it. A stream that one place refers to is an attachment all the same, listed with its place.
Where the name tree does not list a stream that could be the invoice, ESJ says that it lies
outside the tree (`PDF-EMBEDDED-NOT-IN-TREE`).

**A name that leads to two files makes the container unsound** (`PDF-EMBEDDED-DUPLICATE-NAME`).
That is a key the name tree lists twice, in one node or across two, or a file specification
whose entries hold two different streams. The error names every entry by position, name,
object number and size. Readers part on such a name: one that loads the tree into a map keeps
one entry, one that walks it takes the first, most take `/F` and some `/UF`. So the name does
not say which file the container means. ESJ counts every attachment that shares such a name
with a possible invoice among the candidates. It chooses nothing without you, and
`--attachment` refuses the name.

ESJ does not look at the associated files of a form XObject or a structure element, rich media
assets, related files arrays (`/RF`) or the targets of embedded go-to actions. No hybrid
invoice specification puts the invoice there.

## What decides what an attachment is

The **bytes**, and nothing else. ESJ decodes every attachment far enough to read the root
element of the XML it begins: 8 KiB, an order of magnitude more than a root element with its
namespace declarations takes. The qualified name of that element decides. The window assumes
a cooperative producer, and this module does not. The prolog of an XML document has no length
limit, so whoever writes the file decides whether the root element lies inside the window. An
attachment that begins XML and whose root element ESJ did not reach is therefore
*undetermined*, not "XML of some other kind". ESJ counts it among the attachments that could be
the invoice, so a second invoice cannot hide behind a long comment:

| Root element | Classification |
|---|---|
| `{urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100}CrossIndustryInvoice` | CII invoice |
| `{urn:oasis:names:specification:ubl:schema:xsd:Invoice-2}Invoice` | UBL invoice |
| `{urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2}CreditNote` | UBL credit note |
| `CrossIndustryDocument`, in any namespace | ZUGFeRD 1.0 — recognised and refused |
| any other element | other XML |
| XML whose root element is beyond the window | undetermined, and a candidate |
| no element at all | not XML |
| a stream this reader does not decode | unreadable, and no candidate |

The encoding is the document's business. ESJ reads a byte order mark, a UTF-16 attachment with
or without one and the `encoding` pseudo-attribute before the root element.

The name of the attachment and the media type its embedded file stream declares take no part in
this; somebody chose both strings. Where name and content disagree, ESJ reports
`PDF-EMBEDDED-NAME` and does not believe the name.

ESJ refuses ZUGFeRD 1.0 rather than reading it: its root element belongs to CII D14B, which is
no binding of EN 16931. That answer differs from "no invoice here" and has its own exit code.

## Several invoices are never chosen between

In a container with two invoice attachments, nobody has named the invoice. One may be valid and
the other not, and a tool that prints one verdict has to know which document it judges.
`InvoiceAttachments.single()` raises `AmbiguousInvoiceAttachmentException` with the
candidates. `candidates()` returns what it refuses to choose between: the attachments whose
bytes spell an invoice, and the ones not classified at all.

An attachment stays unclassified for two reasons, and ESJ weighs them differently.

An attachment whose root element lies **beyond the window** is a candidate. It is none only
where the container calls it a supplement (`/AFRelationship /Supplement` or `/Unspecified`) and
exactly one other attachment was read as an invoice. The window is a bound of the reading
party. An ordinary enclosure with a long comment before its root element must not make a
container that says exactly what it carries unreadable. ESJ reads and reports on a hybrid
invoice whose XML declares no relationship at all, and an attachment that declares none is a
candidate. A second invoice behind a long prolog is still caught, because an attachment meant
to be read as the document carries one of the relationships that say so. ESJ raises
`PDF-EMBEDDED-UNDETERMINED` either way.

An attachment **whose stream this reader does not decode** is a candidate where the container
declares it the document (`/Alternative`, `/Data` or `/Source`) or declares nothing. It is none
where the container calls it a supplement or leaves it unspecified. A picture beside the
invoice is that second case: refusing the container for it would let anyone who can attach a
logo deny the recipient a verdict. Where the container points at an undecodable attachment as
the document, reading the *other* one would turn an ambiguity refusal into an answer about the
attachment nobody singled out. ESJ raises `PDF-EMBEDDED-UNREADABLE` either way.

Where the container carries several candidates and you name one, ESJ reports the others as
`PDF-EMBEDDED-SEVERAL`, at warning level, with the position of each. You took responsibility
for the choice, and a container specification still declares one invoice attachment.

Where ESJ read **no** attachment as an invoice and one of them was not classified, the answer
is that nothing was established, not that the file carries no invoice.
`NoInvoiceAttachmentException.undetermined()` carries those attachments. If you name one with a
selector, you read it on your own responsibility.

Nothing in a PDF makes an attachment name unique. `InvoiceAttachments.named(String)` therefore
raises the same exception where the name matches more than one attachment, by the name a file
specification gives or by a key the name tree lists. It raises it too where its one match
shares a name with another (`duplicateNames()`). `at(int)` always works: the position in the
order `all()` returns, counted from one. That numbering is this module's own, not the file's.

## What is checked about the container

You get two answers, one about the container and one about the invoice. Show both and say which
is which. None of the codes below is a finding of the ESJ specification, and none begins with
`ESJ-`, which that specification reserves for itself (SPEC section 9.6).

The container answer has two words, the invoice answer five. The container checks either ran
over the file, or the file was never opened. A bound met inside the container stops the command
with exit code 7 and no report at all: the file itself, one attachment, or a structural stream
over the pre-flight bound. The PDF/A conformance of the file takes part in neither answer
unless `--verapdf` names a validator. Without one it is declared, not validated, and every
report says so. The `Invoice:` line follows the rule of the report's last line, for its own
scope. It is a claim about coverage as well as about findings:

| `Invoice:` | When |
|---|---|
| `VALID` | the complete check for the embedded invoice ran and nothing fatal was found |
| `INVALID` | something fatal was found: a layer error, a fatal finding of an official artefact, or bytes that never became a document |
| `INDETERMINATE (missing from the check: …)` | nothing fatal was found and a component of that check did not run or did not complete, named with its cause |
| `NO VERDICT (a limit of this run was reached)` | a bound of this run stopped the reader, so nothing was judged; the command leaves with exit code 7 |
| `NOT CHECKED (profile MINIMUM)` | the profile of the container carries no invoice line, so EN 16931 is not the question to ask of this document |

The line names the gaps in parentheses, not after a dash, because it is a row of the report and
not its last word. The cause tokens are the ones
[`validation.md`](validation.md#verdict-and-exit-code) lists.

| Category | Codes | What it is about |
|---|---|---|
| `PDF-STRUCTURE` | `PDF-STRUCTURE-XREF`, `PDF-STRUCTURE-EOF`, `PDF-STRUCTURE-PDFA` | the object structure of the file, and what the file declares itself to be |
| `PDF-AF` | `PDF-AF-ABSENT`, `PDF-AF-RELATIONSHIP` | whether the catalog's `/AF` array says that the invoice attachment is what the document is about, and in what relationship |
| `PDF-XMP` | `PDF-XMP-ABSENT`, `PDF-XMP-SCHEMA`, `PDF-XMP-DOCUMENT-TYPE`, `PDF-XMP-VERSION`, `PDF-XMP-FILENAME`, `PDF-XMP-CONFORMANCE`, `PDF-XMP-CONFORMANCE-MISMATCH` | the Factur-X extension schema of the XMP packet, and whether what it says about the attachment and the profile agrees with the attachment and the invoice |
| `PDF-EMBEDDED` | `PDF-EMBEDDED-MIME`, `PDF-EMBEDDED-SIZE`, `PDF-EMBEDDED-NAME`, `PDF-EMBEDDED-TRUNCATED`, `PDF-EMBEDDED-UNDETERMINED`, `PDF-EMBEDDED-UNREADABLE`, `PDF-EMBEDDED-SEVERAL`, `PDF-EMBEDDED-DUPLICATE-NAME`, `PDF-EMBEDDED-NOT-IN-TREE` | the embedded file dictionary: the declared media type, the declared size, the name against the content, an attachment whose root element lay beyond the window, one whose stream this reader does not decode, a container that carries more than one attachment that could be the invoice, a name that leads to two files (an error), and an attachment that could be the invoice and lies outside the name tree |

**ESJ validates PDF/A conformance only where [`--verapdf`](cli.md#validating-the-pdfa-claim)
names a validator.** Without one, ESJ reports a file that declares PDF/A-3B as declaring it,
with the word *declared* in the message. Validating it is a PDF/A validator's work.

A message may quote the file: an attachment name, a media type, an XMP property. ESJ escapes
every such fragment the way SPEC section 9.5 asks a validator to escape a fragment it quotes. A
finding about a hostile name does not carry that name to a terminal.

## Security posture

A PDF is a larger attack surface than an XML document, and the module is written for that.

* ESJ uses PDFBox for the object structure, the catalog name trees, the `/AF` arrays, the page
  tree and the annotation dictionaries as far as they refer to embedded files, the embedded
  file streams and the XMP packet. It uses it for nothing else. **ESJ renders no page, reads no
  content stream, loads no font, processes no form or annotation action and executes no
  JavaScript.** PDFBox executes none.
* The stream cache lives in memory only. Nothing goes to a temporary directory: a library does
  not get to put somebody's invoice on a disk.
* ESJ fetches nothing that lies outside the file. It reports a file specification that names a
  file without embedding it as carrying no content.
* **This reader never decrypts.** It takes no password, builds no security handler and
  deciphers no byte, not even of a file that would open with the empty user password. ISO
  19005 allows no `/Encrypt` key in the trailer, so a hybrid invoice carries none. ESJ refuses
  a file with exit code 2 wherever the parse meets its `/Encrypt` entry: in the trailer, in an
  object of the body, or in an object an object stream yielded. That holds through the
  references the entry may be written as, because `/V 13 0 R` declares what `/V 4` does. Where
  a damaged file hides the entry from all three, ESJ reads it as an ordinary container and its
  streams stay ciphertext. An attachment of ciphertext decodes to nothing and is reported as
  one nothing was read from.
* A stream parser reads the XMP packet and every attachment window. It refuses document type
  definitions and external entities, as the reader of the invoice does next.
* ESJ reports filenames and media types and never trusts them. It never uses a name as a file
  system path (SPEC section 12.5).

### Limits

The limits are configurable. They are a policy of the reader, not a property of the document.
ESJ refuses a file that outgrows one because processing it would cost more than its recipient
agreed to spend. That does not call the file invalid (SPEC section 3.1). An `EsjLimitException`
is never a verdict on the invoice; its `bound()` names the `PdfLimits` setting.

| Bound | Default | Measured in |
|---|---|---|
| the PDF itself | 64 MiB | bytes of the file, checked before anything is parsed |
| attachments enumerated | 64 | embedded file streams, from every place above; the entries of the name tree, its nodes that list no file and the entries of the document's `/AF` array are held to the same number, and the tree to 32 levels |
| one decoded attachment | 4 MiB | bytes after the stream filters have run |
| all decoded attachments together | 16 MiB | the same |
| the XMP packet | 1 MiB | bytes |
| everything one container decodes | 64 MiB | bytes after the stream filters have run, counting the streams the library decodes to find the objects of the file and the streams this reader decodes out of it |
| objects the object streams declare | 250 000 | the `/N` entry of each object stream, added up over the container |
| objects walked on the pages | 250 000 | page-tree nodes, pages, annotations and the entries of their `/AF` arrays, while the attachments are enumerated |
| one predicted row | 1 MiB | the row length a `/Predictor` stage declares, which is fixed and raised by no switch; a stage declaring values this format does not define is malformed rather than large (exit code 2) |

The last three have no switch of their own. The two bounds on objects follow `--max-pdf-bytes`:
a file can only declare the objects it has the bytes to declare, and the walk of the pages
visits objects of the file. The bound on a predicted row is fixed, and a refusal by it says so.

The bound on one attachment is the bound the XML readers already read within. Decoding stops
**at** the bound, not after it. ESJ cuts off a small Flate stream that inflates without end at
the bound and reports it, and never buffers it whole. It refuses a truncated invoice attachment
instead of importing it. This module therefore runs the filter chain itself, into an output
stream that stops accepting bytes at the bound. The library's own decoding stream decodes the
whole of a stream before it returns a reader over it, so a bound on what that reader hands back
would apply to bytes already on the heap. The same holds for the XMP packet and for the window
that classifies an attachment.

**ESJ runs a filter only when every allocation it makes is a function of what it has already
written.** A cap bounds what a decoder writes. It bounds nothing for a decoder that allocates
first: an image decoder builds the whole raster from the width and the height in the file. So
ESJ runs `FlateDecode`, `LZWDecode`, `ASCIIHexDecode`, `ASCII85Decode`, `RunLengthDecode` and
`Crypt`, which PDFBox runs as the identity filter. Every other filter is one this reader does
not decode. An electronic invoice is not a JPEG, a fax image or a JBIG2 image, and neither is
an XMP packet. ESJ classifies an attachment filtered that way as *unreadable* and does not
refuse the container for it. A structural stream filtered that way ends the run with exit code
7: ESJ cannot measure it, and decoding it costs whatever the chain expands to.

A filter on the list can still receive parameters that break the rule. `FlateDecode` and
`LZWDecode` take a predictor (PDF 32000-1, 7.4.4.4). The decoder allocates two buffers of one
predicted row, `/Columns × /Colors × /BitsPerComponent` rounded up to bytes, before either
writes anything. ESJ therefore closes the stage to the values the format defines (table 10):
`/Predictor` 1, 2 or 10 to 15, `/Colors` and `/Columns` at least one, `/BitsPerComponent` 1,
2, 4, 8 or 16. Anything else is a malformed stream, refused before a multiplication happens
(exit code 2, a statement about the file). ESJ then computes the row from the accepted values
in the order and the 32-bit width the library uses, and treats an overflow as a row too wide
(exit code 7). A row that is negative in wider arithmetic, such as `/Columns -17` of
`/Colors 8` of `/BitsPerComponent 15790321`, cannot slip past a guard that counts in 64 bits.

Two streams are never this module's to decode: the **object streams** (`/Type /ObjStm`) that
hold a file's objects and the **cross-reference streams** (`/Type /XRef`) that find them. The
library decodes both while it opens the file. Bounding them needs each stream's extent and the
filters it declares. A scan of this reader's own would hold only as far as it agrees with the
library's lexer: a comment between a name and its value, a name spelled with `#xx` escapes, a
length written as a reference are all places where two readings part. **The library hands both
over.** It builds every stream in one place, whichever way the parser found it, and that place
has the dictionary and the extent together. This reader supplies the view the stream is read
through. The first time anything reads it (when the library begins to decode it, not earlier),
the view runs it through the same bounded decoder, counts the output and charges it to one
budget for the whole container. The budget is `--max-pdf-bytes` by default and grows where a
run's attachment bounds are larger. A second number stands beside it, the objects an object
stream declares in `/N`. Bytes bound bytes, and 3 million empty strings cost a few bytes each
in the stream and a few dozen each once the library has built them.

The budget is **suspended for one stream** while this module decodes that stream itself, and
for no other. The suspension names the stream, not the container, so ESJ still measures every
other stream the library touches meanwhile. ESJ resolves the filters, `/DecodeParms` and
`/Length` *before* the suspension begins, so nothing the file controls sends the library off to
read another object inside the window.

ESJ does **not** bound the library's own object model: a file inside the byte bound whose
objects cost more to hold than its bytes cost to decode. The figures under *A profile needs the
heap its bounds imply* in [`deployment.md`](deployment.md) are measured on documents, not on
adversaries. The memory ceiling of the process answers the rest: a limit given from outside,
exit 3, no verdict.

### Nothing is written to the error stream

A library does not get to write to the error stream of the process. The obvious way to break
that rule is to hand arbitrary bytes to an XML parser, which prints its own complaint about an
encoding before any caller can catch the exception. So ESJ **decodes every window and every XMP
packet into characters first**, in the charset the document names, and anything that does not
decode becomes a replacement character. The parser gets a character stream. A test asserts
that classifying a set of hostile attachments produces not one byte of output.

PDFBox is the exception: it writes its own diagnostics through Apache Commons Logging. This
module neither reads them back nor configures them, and makes its own observations about the
structure from the bytes. `esj` is not a library and does configure them. It installs a handler
on the two package loggers before it opens anything, so the records stay off the error stream
and `--verbose` shows them, escaped, as `info: pdf library: …`.

## Profiles

The Factur-X and ZUGFeRD 2.x family is a set of profiles of one container format. Only some of
them are EN 16931 invoices.

| Profile | `ConformanceLevel` | An EN 16931 invoice? |
|---|---|---|
| MINIMUM | `MINIMUM` | no — little more than the totals |
| BASIC WL | `BASIC WL` | no — no invoice line at all |
| BASIC | `BASIC` | yes |
| EN 16931 | `EN 16931` | yes |
| EXTENDED | `EXTENDED` | yes |
| XRECHNUNG | `XRECHNUNG` | yes |

The rules of the standard, run against a MINIMUM or a BASIC WL document, produce a list of
missing mandatory elements that says nothing about the document: it never claimed to be one.
So ESJ reads and reports the profile, and `PdfImportResult.en16931Invoice()` says that the
question does not apply instead of answering it wrongly. Where the profile is not known at all,
the flag is `true`. Nothing said that the document is no EN 16931 invoice, and assuming so
would refuse to check an ordinary one.

A hybrid file writes the profile twice: in the XMP packet as `ConformanceLevel`, and in the
invoice as BT-24, the specification identifier. ESJ asks the invoice first, because the invoice
is the document; the packet is a claim the container makes about it. Where the two disagree,
`PDF-XMP-CONFORMANCE-MISMATCH` says so. A consumer that reads the container and one that reads
the invoice are then told two different things.

## Embedding the invoice

`FacturX.embed` writes an invoice into a PDF/A-3 file. `esj embed` and `esj render --embed cii`
are that step on the command line. [`pdf-output.md`](pdf-output.md) lists what is written,
which flavour writes which name, and what is refused rather than converted.

## Facts this module relies on, and where they come from

Identifiers, property names, allowed values and conventional names come from the public
Factur-X and ZUGFeRD specification documents, cross-checked against
[Mustangproject](https://www.mustangproject.org/), an Apache-2.0 reference implementation of
the same formats. No specification prose is reproduced here and no code was copied.
[`docs/sources.md`](sources.md) has the publisher, edition, retrieval date and terms of each
cited document. A citation names the part of a document a fact comes from, not a clause number.

The cross industry invoice names this module knows leave out `factura-e.xml` (the Spanish
Facturae hybrid PDF) and `xrechnung.xml` (whose body is legitimately UBL) on purpose: a check
on either name would report a correct file.

| Fact | Value | Source |
|---|---|---|
| XMP extension schema namespace (Factur-X 1.0, ZUGFeRD 2.1 and later) | `urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#` | Factur-X 1.0 specification (FNFE-MPE), the XMP extension schema it defines; Mustangproject |
| XMP extension schema namespace (ZUGFeRD 2.0) | `urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#` | ZUGFeRD 2.0 specification (FeRD), the XMP extension schema it defines; Mustangproject |
| the four properties of that schema | `DocumentType`, `DocumentFileName`, `Version`, `ConformanceLevel` | as above |
| the prefix each namespace is bound to, as `pdfaSchema:prefix` and as the element prefix of the four properties | `fx` for the Factur-X namespace, `zf` for the ZUGFeRD 2.0 one | as above |
| the `Version` each of them writes | `1.0` for the Factur-X namespace, `2p0` for the ZUGFeRD 2.0 one | as above |
| the modification date of the embedded file stream | `/Params /ModDate` | Factur-X 1.0 and ZUGFeRD 2.x specifications, the attached file; Mustangproject |
| how a packet declares a schema PDF/A does not predefine: a `pdfaExtension:schemas` bag of one `pdfaSchema` description with its `namespaceURI`, `prefix` and `property` sequence, each property with `name`, `valueType`, `category` and `description` | namespaces `http://www.aiim.org/pdfa/ns/extension/`, `…/schema#`, `…/property#`; the four properties are `Text` of the category `external` | ISO 19005 / the PDF/A extension schema container, which XMP carries; Factur-X 1.0 specification, its XMP extension schema; Mustangproject |
| `DocumentType` of a hybrid invoice | `INVOICE` | as above |
| PDF/A identification schema | `http://www.aiim.org/pdfa/ns/id/`, properties `part` and `conformance` | ISO 19005 / the PDF/A identification schema, which XMP carries |
| the relationship a hybrid invoice declares its XML with | `Alternative` | Factur-X 1.0 specification (FNFE-MPE), the attached file; ZUGFeRD used `Data` for the profiles that are not EN 16931 invoices |
| the relationship values PDF defines | `Source`, `Data`, `Alternative`, `Supplement`, `EncryptedPayload`, `FormData`, `Schema`, `Unspecified` | ISO 32000-2 (PDF 2.0), 7.11.4 |
| that a hybrid file may carry further attachments beside the one invoice XML, and that an explanatory enclosure takes `Supplement` | one invoice XML; further files of any type, `Supplement` | PDF/A-3 (ISO 19005-3) for the embedding of a file of any type; Factur-X 1.0 and ZUGFeRD 2.x for the further attachment; the published guidance recorded in [`sources.md`](sources.md#factur-x--zugferd-and-the-pdf-specifications) for `Supplement` |
| the attachment name of the cross industry invoice | `factur-x.xml` (Factur-X 1.0, ZUGFeRD 2.1 and later); `zugferd-invoice.xml` (ZUGFeRD 2.0); `ZUGFeRD-invoice.xml` (ZUGFeRD 1.0) | Factur-X 1.0 and ZUGFeRD 2.x specifications, the attached file; ZUGFeRD 1.0 specification for the third |
| a conventional attachment name that is **not** a cross industry invoice name | `xrechnung.xml`, used for an XRechnung inside a PDF, whose body may be UBL | KoSIT XRechnung 3.0.2; Mustangproject |
| the profiles | MINIMUM, BASIC WL, BASIC, EN 16931, EXTENDED, XRECHNUNG | Factur-X 1.0 and ZUGFeRD 2.x specifications, the profiles they define |
| the specification identifiers those profiles write in BT-24 | `urn:factur-x.eu:1p0:minimum`, `…:basicwl`, `urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic`, `urn:cen.eu:en16931:2017`, `urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended`, and the XRechnung identifier | as above |
| the root element of a ZUGFeRD 1.0 invoice | `CrossIndustryDocument` | ZUGFeRD 1.0 specification, its message structure |
| the PDF file header and the trailer keyword | `%PDF-`, `startxref`, `%%EOF` | ISO 32000-1, 7.5.2 and 7.5.5 |

The dependency itself: **Apache PDFBox 3.x**, published by the Apache Software Foundation
under the Apache License, Version 2.0. `esj-pdf` depends on it, and so does `esj-render`, which
writes the PDF rendering with it. `esj-core` still depends on jackson-core alone, and `esj-pdf`
needs neither `esj-xr` nor Saxon. See `NOTICE`.

The bounds under *Limits* and the refusal of an encrypted file rest on these facts about how
that library reads a file. They were read out of its published sources, not assumed:

| Fact | Where it is |
|---|---|
| every stream of a parsed file is built in one place, which is handed the dictionary the stream was parsed from and asks the parser for the view of the stream's raw bytes | `COSDocument.createCOSStream(COSDictionary, long, long)` |
| the parsers of an object stream and of a cross-reference stream decode through a view that runs the whole filter chain into a buffer of the library's own, so nothing outside can bound it after the fact | `PDFObjectStreamParser`, `PDFXrefStreamParser`, `COSStream.createView()`, `Filter.decode(…)` |
| a Flate or LZW stage with `/Predictor` greater than one allocates two buffers of one row, computed from the parameters, before the decoder writes a byte; the only guard is against a negative length, and the row is `(columns × (colors × bitsPerComponent) + 7) / 8` in 32-bit arithmetic, with `/Colors` clamped to 32 and nothing clamped from below | `Predictor.wrapPredictor(…)`, `Predictor.calculateRowLength(…)`, `PredictorOutputStream` |
| the encryption dictionary is asked for at one point, and the security handler that decrypts a stream is built there and nowhere else, so refusing there refuses before any plaintext exists | `COSParser.prepareDecryption()`, `COSDocument.getEncryptionDictionary()` |
| that question is answered from the trailer, and a trailer rebuilt after an unusable `startxref` carries `/Encrypt` only when a catalog and an info dictionary were both found; the fallback drops it, and it dereferences the objects of the file body only — an object compressed into an object stream that nothing refers to is registered and never parsed | `BruteForceParser.rebuildTrailer(…)`, `bfSearchForTrailer(…)`, `searchForTrailerItems(…)`, `bfSearchForObjStreams(…)` |
| every indirect reference of a parse is resolved through one method of the parser, so an object can be examined there before anything else sees it | `COSParser.dereferenceCOSObject(COSObject)` |
| an object that lives inside an object stream is handed out through one other method, so the objects that route does not see can be examined there | `COSParser.parseObjectStreamObject(long, COSObjectKey)` |
| a node of a name tree is loaded into a map, so a key the node lists twice keeps its last entry and loses the first without a trace; this reader therefore walks the `/Names` and `/Kids` arrays itself | `PDNameTreeNode.getNames()` |
| the page iterator enqueues the whole tree by recursion before it hands out the first page; this reader walks the tree itself, iteratively and within its bound | `PDPageTree.PageIterator` |

They belong to one release line, and a new line is a reason to read them again. The parent
`pom.xml` pins the version.

## Encoding repair, the other half of the same phase

Both readers look at the bytes before the parser does: a document whose bytes are in one
charset and whose declaration names another is the commonest defect in the field.
`XmlEncodingReport.of` reports the byte order mark, the declared encoding and the charset the
bytes are in. A reader in `REPAIR` mode recodes into UTF-8 and rewrites the declaration. It
recodes four charsets and no others: UTF-8, UTF-16 in either byte order, ISO-8859-1 and
Windows-1252. Where UTF-8 was claimed, a sequence that is not valid UTF-8 is read as
Windows-1252 when it carries a byte between `0x80` and `0x9F` that Windows-1252 defines, and as
ISO-8859-1 otherwise; the report says which. ESJ hands any other declared charset to the parser
where the bytes decode in it. Where they do not, both modes refuse (`XmlEncodingException`, not
`repairable()`).

**Repair is not validation**, and nothing is silent. `REPAIR`, the default, records the import
note `ENCODING_REPAIRED` with what was declared and what was read. `STRICT` refuses with
`XmlEncodingException` carrying the same two facts. The digest in the provenance covers the
bytes you handed over, so the note is also what tells you that the two differ. A document that
was already valid UTF-8 comes back as the same array.

## What the command line makes of both

`esj-cli` composes the two modules and adds nothing of its own. A file whose first bytes are
the PDF header goes to `esj-pdf`, and the attachment goes to the reader of the run,
`esj-bindings` by default. The bounds of a run reach `esj-pdf` as `PdfLimits`.
`--max-pdf-bytes` and `--max-attachments` are its own switches. The bound on an attachment
follows `--max-input-bytes`, because an attachment is the input of the XML reader.
`esj validate` is `STRICT`; every other command is `REPAIR` with one warning line, and
`--strict` makes them all strict. [`cli.md`](cli.md#hybrid-invoices-pdf) has the switches, the
transcripts and the exit codes.
