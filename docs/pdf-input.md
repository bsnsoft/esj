# Hybrid PDF

`esj-pdf` reads the electronic invoice out of a hybrid PDF; [`pdf-output.md`](pdf-output.md) is
the other direction. The PDF is a container and the invoice is the XML attached to it; from the
moment the attachment is in hand, nothing is different from reading the same XML on its own.

## The boundary

**Only an embedded electronic invoice is read. Nothing is extracted from the page.**

There is no optical character recognition here, no layout analysis and no heuristic or
model-based extraction, and none is planned. A PDF whose invoice exists only as printed
text carries no instance of the semantic model, and the answer is that there is none.

So a PDF falls into one of three cases:

| Case | What happens |
|---|---|
| it carries one attachment whose bytes are an electronic invoice | the invoice is read and the container is reported beside it |
| it carries several, or one invoice and one attachment that was not classified, and nothing says which | nothing is chosen: the candidates are listed and the caller is asked |
| it carries none | that is the answer |

## What decides what an attachment is

The **bytes**, and nothing else. Every attachment is decoded far enough to read the root
element of the XML it begins — 8 KiB, which is an order of magnitude more than a root
element with its namespace declarations takes — and the qualified name of that element
decides. That window is an argument about a cooperative producer, though, and this module
does not assume one: the prolog of an XML document has no length limit, so whoever writes
the file decides whether the root element is inside it. An attachment that begins XML and
whose root element was not reached is therefore *undetermined* — not "XML of some other
kind" — and undetermined is counted among the attachments that could be the invoice, so
that a second invoice cannot hide behind a long comment:

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
| a JSON object named `invoice.esj.json`, declared `application/json` | ESJ document, and never a candidate |

The encoding is the document's business: a byte order mark, a UTF-16 attachment with or without
one and the `encoding` pseudo-attribute are all read before the root element is.

The name of the attachment and the media type its embedded file stream declares take no
part in this; both are strings somebody chose. Where the name and the content disagree,
that is a finding — `PDF-EMBEDDED-NAME` — and not a reason to believe the name. The one
exception is the last row: the two labels can only make an attachment that is not XML into
*this project's own document, and still not the invoice*, and what it holds is read solely in
order to be checked against the invoice ([below](#the-esj-document-beside-the-invoice)).

ZUGFeRD 1.0 is refused rather than read: its root element belongs to CII D14B and is no binding
of EN 16931. Saying so is a different answer from "no invoice here", and has its own exit code.

## Several invoices are never chosen between

A container with two invoice attachments is a container whose invoice nobody has named:
one of the two may be valid and the other not, and a tool that prints one verdict has to
know which document the verdict is about. `InvoiceAttachments.single()` raises
`AmbiguousInvoiceAttachmentException` carrying the candidates. `candidates()` is what it
refuses to choose between: the attachments whose bytes spell an invoice, and the ones that
were not classified at all.

An attachment is unclassified for two different reasons, and the reasons are weighed
differently.

An attachment whose root element lies **beyond the window** is a candidate unless the
container itself calls it a supplement — `/AFRelationship /Supplement` or `/Unspecified` —
and exactly one other attachment was read as an invoice. The window is a bound of the
reading party, so an ordinary enclosure with a long comment in front of its root element
must not make a container that says exactly what it carries unreadable. A hybrid invoice
whose XML declares no relationship at all is read and reported on, and an attachment that
declares none is a candidate; a second invoice behind a long prolog is still caught, because
an attachment meant to be read as the document carries one of the relationships that say so.
`PDF-EMBEDDED-UNDETERMINED` is raised either way.

An attachment **whose stream this reader does not decode** is a candidate where the
container declares it the document (`/Alternative`, `/Data` or `/Source`) or declares
nothing, and is not one where the container calls it a supplement or leaves it unspecified.
A picture beside the invoice is the second case, and refusing the container for it would let
anyone who can attach a logo deny the recipient a verdict. Where the container points at an
attachment as the document and that attachment cannot be decoded, reading the *other* one
would turn an ambiguity refusal into an answer about the attachment nobody singled out.
`PDF-EMBEDDED-UNREADABLE` is raised either way.

Where the container carries several candidates and a caller named one, what it carries
beside the one that was read is `PDF-EMBEDDED-SEVERAL`, at warning level, with the position
of each: the caller has taken responsibility for the choice, and a container specification
still declares one invoice attachment.

Where **no** attachment was read as an invoice and one of them was not classified, the
answer is that nothing was established rather than that the file carries no invoice.
`NoInvoiceAttachmentException.undetermined()` carries them, and a caller that names such an
attachment with a selector reads it on its own responsibility.

Nothing in a PDF makes an attachment name unique, so `InvoiceAttachments.named(String)`
raises the same exception where the name matches more than one. `at(int)` is the selector that always works: the
position in the order `all()` returns, counted from one, which is this module's own
numbering and not the file's.

## What is checked about the container

The container verdict and the invoice verdict stay two answers. A caller shows both and
says which is which; none of the codes below is a finding of the ESJ specification, and
none of them begins with `ESJ-`, which that specification reserves for itself
(SPEC section 9.6).

The container answer has two words where the invoice answer has five. Its checks either ran
over the file or the file was never opened: a bound met inside the container — the file
itself, one attachment, a structural stream over the pre-flight bound — stops the command
with exit code 7 and no report at all. The PDF/A conformance of the file takes no part in
either answer unless `--verapdf` names a validator; without one it is declared, not
validated, and every report says so. The `Invoice:` line follows the rule the report's last
line follows, for its own scope — a claim about coverage as well as about findings:

| `Invoice:` | When |
|---|---|
| `VALID` | the complete check for the embedded invoice ran and nothing fatal was found |
| `INVALID` | something fatal was found: a layer error, a fatal finding of an official artefact, or bytes that never became a document |
| `INDETERMINATE (missing from the check: …)` | nothing fatal was found and a component of that check did not run or did not complete, named with its cause |
| `NO VERDICT (a limit of this run was reached)` | a bound of this run stopped the reader, so nothing was judged; the command leaves with exit code 7 |
| `NOT CHECKED (profile MINIMUM)` | the profile of the container carries no invoice line, so EN 16931 is not the question to ask of this document |

The gaps are named in parentheses rather than after a dash, because the line is a row of the
report and not its last word; the cause tokens are the ones
[`validation.md`](validation.md#verdict-and-exit-code) lists.

| Category | Codes | What it is about |
|---|---|---|
| `PDF-STRUCTURE` | `PDF-STRUCTURE-XREF`, `PDF-STRUCTURE-EOF`, `PDF-STRUCTURE-PDFA` | the object structure of the file, and what the file declares itself to be |
| `PDF-AF` | `PDF-AF-ABSENT`, `PDF-AF-RELATIONSHIP` | whether the catalog's `/AF` array says that the invoice attachment is what the document is about, and in what relationship |
| `PDF-XMP` | `PDF-XMP-ABSENT`, `PDF-XMP-SCHEMA`, `PDF-XMP-DOCUMENT-TYPE`, `PDF-XMP-VERSION`, `PDF-XMP-FILENAME`, `PDF-XMP-CONFORMANCE`, `PDF-XMP-CONFORMANCE-MISMATCH` | the Factur-X extension schema of the XMP packet, and whether what it says about the attachment and the profile agrees with the attachment and the invoice |
| `PDF-EMBEDDED` | `PDF-EMBEDDED-MIME`, `PDF-EMBEDDED-SIZE`, `PDF-EMBEDDED-NAME`, `PDF-EMBEDDED-TRUNCATED`, `PDF-EMBEDDED-UNDETERMINED`, `PDF-EMBEDDED-UNREADABLE`, `PDF-EMBEDDED-SEVERAL`, `PDF-EMBEDDED-SEVERAL-ESJ`, `PDF-EMBEDDED-ESJ-LABEL` | the embedded file dictionary: the declared media type, the declared size, the name against the content, an attachment whose root element lay beyond the window, one whose stream this reader does not decode, a container that carries more than one attachment that could be the invoice, one that carries more than one ESJ document, and one carrying an attachment that wears the name of the ESJ document without being one |
| `PDF-ESJ` | `PDF-ESJ-DISAGREES`, `PDF-ESJ-UNSOUND`, `PDF-ESJ-UNREADABLE`, `PDF-ESJ-UNCHECKED` | the ESJ document beside the invoice: whether it can be read (layer L1), whether it is a document of the semantic model it names (layer L2), whether it and the invoice are two accounts of one invoice, and the cases where nothing was compared — the invoice this run read is in no syntax this version has a binding table for, or `esj inspect` met a bound inside the attachment |

**PDF/A conformance is validated only where [`--verapdf`](cli.md#validating-the-pdfa-claim)
names a validator.** Without one, a file that declares PDF/A-3B is reported as declaring it,
with the word *declared* in the message; validating it is a PDF/A validator's work.

A message may quote the file — an attachment name, a media type, an XMP property — and
every such fragment is escaped the way SPEC section 9.5 asks a validator to escape a
fragment it quotes, so that a finding about a hostile name is not the way that name
reaches a terminal.

## Security posture

A PDF is a larger attack surface than an XML document, and the module is written for that.

* PDFBox is used for the object structure, the catalog name trees, the `/AF` array, the
  embedded file streams and the XMP packet, and for nothing else. **No page is rendered,
  no font is loaded, no form or annotation action is processed, and no JavaScript is
  executed** — PDFBox executes none.
* The stream cache is memory only. Nothing is written to a temporary directory: a library
  does not get to put somebody's invoice on a disk.
* Nothing that leaves the file is fetched. A file specification that names a file without
  embedding it is reported as carrying no content.
* **This reader never decrypts.** It takes no password, builds no security handler and
  deciphers no byte, not even of a file that would open with the empty user password.
  ISO 19005 allows no `/Encrypt` key in the trailer, so a hybrid invoice carries none. A
  file whose `/Encrypt` entry the parse sees is refused with exit code 2, wherever it meets
  it — the trailer, an object of the body, or an object an object stream yielded — and
  through the references it may be written as, because `/V 13 0 R` declares what `/V 4`
  does. Where a damaged file hides the entry from all three, the file is read as an
  ordinary container and its streams stay ciphertext: an attachment of ciphertext decodes
  to nothing and is reported as one nothing was read from.
* The XMP packet and every attachment window are parsed with a stream parser that refuses
  document type definitions and external entities, as `esj-xr` does one module further on.
* Filenames and media types are reported and never trusted. A name is never used as a file
  system path (SPEC section 12.5).

### Limits

Configurable, and a policy of the reader rather than a property of the document: a file
that outgrows one is refused because processing it would cost more than its recipient
agreed to spend, which is not the same as calling it invalid (SPEC section 3.1).
`PdfLimitException` is never a verdict on the invoice.

| Bound | Default | Measured in |
|---|---|---|
| the PDF itself | 64 MiB | bytes of the file, checked before anything is parsed |
| attachments enumerated | 64 | entries of the name tree and the `/AF` array |
| one decoded attachment | 4 MiB | bytes after the stream filters have run |
| all decoded attachments together | 16 MiB | the same |
| the XMP packet | 1 MiB | bytes |
| everything one container decodes | 64 MiB | bytes after the stream filters have run, counting the streams the library decodes to find the objects of the file and the streams this reader decodes out of it |
| objects the object streams declare | 250 000 | the `/N` entry of each object stream, added up over the container |
| one predicted row | 1 MiB | the row length a `/Predictor` stage declares, which is fixed and raised by no switch; a stage declaring values this format does not define is malformed rather than large (exit code 2) |

The last two have no switch of their own. The bound on objects follows `--max-pdf-bytes`,
because a file can only declare the objects it has the bytes to declare; the bound on a
predicted row is fixed, and a refusal by it says so.

The bound on one attachment is the bound the XML readers already read within. Decoding
stops **at** the bound rather than after it: a small Flate stream that inflates without end
is cut off there and reported, never buffered whole, and a truncated invoice attachment is
refused instead of imported. This module therefore runs the filter chain itself — the
library's own decoding stream decodes the whole of a stream before it returns a reader over
it, so a bound applied to what that reader hands back is applied to bytes already on the
heap — into an output stream that stops accepting bytes at the bound. The same holds for the
XMP packet and for the window an attachment is classified by.

**A filter is run only when every allocation it makes is a function of what it has already
written.** A cap bounds what a decoder writes and bounds nothing for one that allocates
first: an image decoder builds the whole raster from the width and the height in the file.
So the filters that are run are `FlateDecode`, `LZWDecode`, `ASCIIHexDecode`,
`ASCII85Decode`, `RunLengthDecode` and `Crypt`, which PDFBox runs as the identity filter,
and every other filter is one this reader does not decode. An electronic invoice is not a
JPEG, a fax image or a JBIG2 image, and neither is an XMP packet. An attachment filtered
that way is classified as *unreadable* rather than refusing the container; a structural
stream filtered that way ends the run with exit code 7, because measuring it is not
possible and decoding it is whatever the chain expands to.

A filter on the list can still be handed parameters that break the rule. `FlateDecode` and
`LZWDecode` take a predictor (PDF 32000-1, 7.4.4.4) and the decoder allocates two buffers
of one predicted row — `/Columns × /Colors × /BitsPerComponent`, rounded up to bytes —
before either writes anything. The stage is therefore closed to the values the format
defines (table 10): `/Predictor` 1, 2 or 10 to 15, `/Colors` and `/Columns` at least one,
`/BitsPerComponent` 1, 2, 4, 8 or 16. Anything else is a malformed stream, refused before a
multiplication happens (exit code 2, a statement about the file). The row is then computed
from the accepted values in the same order and the same 32-bit width the library uses, with
an overflow treated as a row too wide (exit code 7), so that a row negative in wider
arithmetic — `/Columns -17` of `/Colors 8` of `/BitsPerComponent 15790321` — cannot be
waved through by a guard that counts in 64 bits.

Two streams are never this module's to decode: the **object streams** (`/Type /ObjStm`)
that hold a file's objects and the **cross-reference streams** (`/Type /XRef`) that find
them, both decoded by the library while it opens the file. Bounding them needs each
stream's extent and the filters it declares, and reading either out of the file with a scan
of this reader's own would hold only as far as the scan agrees with the library's lexer — a
comment between a name and its value, a name spelled with `#xx` escapes, a length written
as a reference are all places where two readings part. **The library hands both over.**
Every stream is built in one place, whichever way the parser found it, and that place has
the dictionary and the extent together; this reader supplies the view the stream will be
read through, and the first time anything reads it — the moment the library begins to
decode it, not earlier — the view runs it through the same bounded decoder, counts what
comes out and charges it to one budget for the whole container. The budget is
`--max-pdf-bytes` by default, and is raised where a run's attachment bounds are larger.
Beside it stands a second number, the objects an object stream declares in `/N`: bytes
bound bytes, and three million empty strings cost a few bytes each in the stream and a few
dozen each once the library has built them.

The budget is **suspended for one stream** while this module decodes that stream itself,
and for no other. The suspension names the stream rather than the container, so every other
stream the library touches meanwhile is still measured; and the filters, `/DecodeParms` and
`/Length` are resolved *before* it begins, so nothing the file controls sends the library
off to read another object inside the window.

What is **not** bounded is the library's own object model: a file inside the byte bound
whose objects cost more to hold than its bytes cost to decode. The figures under *A profile
needs the heap its bounds imply* in [`deployment.md`](deployment.md) are measured on
documents and not on adversaries, and the memory ceiling of the process — a limit given
from outside, exit 3, no verdict — is the answer to the rest.

### Nothing is written to the error stream

A library does not get to write to the error stream of the process, and the obvious way to
break that rule is to hand arbitrary bytes to an XML parser, which prints its own complaint
about an encoding before any caller can catch the exception. So every window and every XMP
packet is **decoded into characters first** — in the charset the document names, anything
that does not decode becoming a replacement character — and the parser is given a character
stream. A test asserts that classifying a set of hostile attachments produces not one byte
of output.

PDFBox is the exception: it writes its own diagnostics through Apache Commons Logging. This module
neither reads them back nor configures them, and makes its own observations about the structure
from the bytes. `esj` is not a library and does configure them: it installs a handler on the two
package loggers before it opens anything, so the records stay off the error stream and
`--verbose` shows them, escaped, as `info: pdf library: …`.

## Profiles

The Factur-X and ZUGFeRD 2.x family is a set of profiles of one container format, and only
some of them are EN 16931 invoices.

| Profile | `ConformanceLevel` | An EN 16931 invoice? |
|---|---|---|
| MINIMUM | `MINIMUM` | no — little more than the totals |
| BASIC WL | `BASIC WL` | no — no invoice line at all |
| BASIC | `BASIC` | yes |
| EN 16931 | `EN 16931` | yes |
| EXTENDED | `EXTENDED` | yes |
| XRECHNUNG | `XRECHNUNG` | yes |

Running the rules of the standard against a MINIMUM or a BASIC WL document produces a list
of missing mandatory elements that says nothing about the document — it was never claiming
to be one. So the profile is read, reported, and `PdfImportResult.en16931Invoice()` says
that the question does not apply, rather than answering it wrongly. Where the profile is
not known at all the flag is `true`: nothing said that the document is not an EN 16931
invoice, and assuming the opposite would refuse to check a perfectly ordinary one.

The profile is written twice in a hybrid file — in the XMP packet as `ConformanceLevel`,
and in the invoice as BT-24, the specification identifier. The invoice is asked first,
because the invoice is the document; the packet is a claim the container makes about it.
Where the two disagree, `PDF-XMP-CONFORMANCE-MISMATCH` says so, because a consumer that
reads the container and one that reads the invoice are then told two different things.

## The ESJ document beside the invoice

A file this project writes carries the same invoice a second time, as `invoice.esj.json` declared
`/Supplement`; [`pdf-output.md`](pdf-output.md#the-esj-document-beside-the-invoice) is the rule. On the way back in:

- it is **never the invoice**. Every command reads the invoice XML, and this attachment is no
  candidate whatever the file declares about it.
- `esj inspect` and `esj extract --list` show it with its role, `esj extract --attachment
  invoice.esj.json` hands out its bytes, and a PDF that carries it and *no* invoice XML is a PDF
  with no structured invoice — exit code 2, with a line saying an ESJ attachment was seen and is
  not read as the invoice in this version.
- `esj validate` and `esj inspect` read it, through the reader bounds of the run, measure it on
  layers L1 and L2 and compare it with the invoice
  ([`validation.md`](validation.md#the-container)); no other command opens it. A bound met inside
  it ends `esj validate` at exit 7 and no verdict; `esj inspect` reports it on the row.

## Embedding the invoice

`FacturX.embed` writes an invoice into a PDF/A-3 file, and `esj embed` and `esj render --embed
cii` are that step from the command line: [`pdf-output.md`](pdf-output.md) is what is written, which flavour writes which name, and what is refused rather than converted.

## Facts this module relies on, and where they come from

Identifiers, property names, allowed values and conventional names from the public Factur-X and
ZUGFeRD specification documents, cross-checked against
[Mustangproject](https://www.mustangproject.org/), an Apache-2.0 reference implementation of the
same formats. No specification prose is reproduced here and no code was copied.
Publisher, edition, retrieval date and terms of each cited document are in
[`docs/sources.md`](sources.md); a citation names the part of a document a fact comes from
rather than a clause number.

`factura-e.xml` (the Spanish Facturae hybrid PDF) and `xrechnung.xml` (whose body is
legitimately UBL) are deliberately absent from the cross industry invoice names this module
knows: a check on either name would report a correct file.

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
under the Apache License, Version 2.0. It is a dependency of `esj-pdf` and of `esj-render`,
which writes the PDF rendering with it; `esj-core` still depends on jackson-core alone and
Saxon stays in `esj-xr`. See `NOTICE`.

These facts about how that library reads a file are load-bearing for the bounds described
under *Limits* and for the refusal of an encrypted file, and they were read out of its
published sources rather than assumed:

| Fact | Where it is |
|---|---|
| every stream of a parsed file is built in one place, which is handed the dictionary the stream was parsed from and asks the parser for the view of the stream's raw bytes | `COSDocument.createCOSStream(COSDictionary, long, long)` |
| the parsers of an object stream and of a cross-reference stream decode through a view that runs the whole filter chain into a buffer of the library's own, so nothing outside can bound it after the fact | `PDFObjectStreamParser`, `PDFXrefStreamParser`, `COSStream.createView()`, `Filter.decode(…)` |
| a Flate or LZW stage with `/Predictor` greater than one allocates two buffers of one row, computed from the parameters, before the decoder writes a byte; the only guard is against a negative length, and the row is `(columns × (colors × bitsPerComponent) + 7) / 8` in 32-bit arithmetic, with `/Colors` clamped to 32 and nothing clamped from below | `Predictor.wrapPredictor(…)`, `Predictor.calculateRowLength(…)`, `PredictorOutputStream` |
| the encryption dictionary is asked for at one point, and the security handler that decrypts a stream is built there and nowhere else, so refusing there refuses before any plaintext exists | `COSParser.prepareDecryption()`, `COSDocument.getEncryptionDictionary()` |
| that question is answered from the trailer, and a trailer rebuilt after an unusable `startxref` carries `/Encrypt` only when a catalog and an info dictionary were both found; the fallback drops it, and it dereferences the objects of the file body only — an object compressed into an object stream that nothing refers to is registered and never parsed | `BruteForceParser.rebuildTrailer(…)`, `bfSearchForTrailer(…)`, `searchForTrailerItems(…)`, `bfSearchForObjStreams(…)` |
| every indirect reference of a parse is resolved through one method of the parser, so an object can be examined there before anything else sees it | `COSParser.dereferenceCOSObject(COSObject)` |
| an object that lives inside an object stream is handed out through one other method, so the objects that route does not see can be examined there | `COSParser.parseObjectStreamObject(long, COSObjectKey)` |

They belong to one release line and a new one is a reason to read them again; the version is
pinned in the parent `pom.xml`.

## Encoding repair, the other half of the same phase

Both readers look at the bytes before the parser does, because a document whose bytes are in one
charset and whose declaration names another is the commonest defect in the field.
`XmlBytes.inspect` reports the byte order mark, the declared encoding and the charset the bytes
are; `XmlBytes.repair` recodes into UTF-8 and rewrites the declaration. Four charsets are recoded
and no others: UTF-8, UTF-16 in either byte order, ISO-8859-1 and Windows-1252. A sequence that
is not valid UTF-8 where UTF-8 was claimed is read as Windows-1252 when it carries a byte between
`0x80` and `0x9F` that Windows-1252 defines, and as ISO-8859-1 otherwise — the two differ exactly
in that range, so the report says which was assumed. Any other declared charset is handed to the
parser untouched.

**Repair is not validation**, and nothing is silent. `REPAIR`, the default, records the import
note `ENCODING_REPAIRED` with what was declared and what was read; `STRICT` refuses with
`XrEncodingException` carrying the same two facts. The digest in the provenance is over the bytes
that were handed over, so the note is also what tells a caller that the two differ, and a document
that was already valid UTF-8 is returned as the same array.

## What the command line makes of both

`esj-cli` composes the two modules and adds nothing of its own. A file whose first bytes are the
PDF header goes to `esj-pdf` and the attachment to the reader of the run, `esj-bindings` by
default; the bounds of a run reach `esj-pdf` as `PdfLimits`, with `--max-pdf-bytes` and
`--max-attachments` as its own switches and the bound on an attachment following
`--max-input-bytes`, because an attachment is the input of the XML reader. `esj validate` is
`STRICT` and every other command is `REPAIR` with one warning line; `--strict` makes them all
strict. The switches, the transcripts and the exit codes are
[`cli.md`](cli.md#hybrid-invoices-pdf).
