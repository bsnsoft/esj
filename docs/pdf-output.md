# PDF output

*Part of [EN16931 Semantic JSON](../README.md).*

A hybrid invoice is a PDF a person reads with the electronic invoice attached to it. This page is
the writing side; [`pdf-input.md`](pdf-input.md) reads, [`rendering.md`](rendering.md) draws.

| Command | What it does |
|---|---|
| `esj render <invoice> --embed cii --out <pdf>` | draws the pages and attaches the invoice to them |
| `esj embed <pdf> <invoice> --out <pdf>` | attaches the invoice to a PDF/A-3 file from somewhere else |

Both write a PDF/A-3 file carrying the cross industry invoice of [`CiiWriter`](bindings.md) as an
associated file, as Factur-X asks, and the same invoice as an ESJ document beside it. Neither
converts a page and neither draws one over another: what the file is, it stays.

## The file is PDF/A-3b

Every rendering of `esj-render` is an archival file: `ISO 19005-3`, conformance level B.

| What the profile asks for | Where it comes from |
|---|---|
| every font embedded | the two vendored faces, subset into the file |
| an output intent for the device-dependent greys | `icc/sRGB2014.icc`, the ICC's v2 sRGB profile, embedded whole |
| XMP declaring `pdfaid:part` 3 and `pdfaid:conformance` `B` | written by `Pdfa`, uncompressed stream |
| XMP and the information dictionary in agreement | the packet is written out of that dictionary; title = BT-1, creator and producer = the tool |
| PDF 1.7, no encryption, no transparency, no action, no script | the layout writes none of them |

Level B, not level A: no `MarkInfo` and no structure tree
([design-decisions.md](design-decisions.md)). A branded template brings its own letterhead, logo
and fonts into the file, so what it brought is part of the conformance ([`templates.md`](templates.md#the-letterhead)).

## Embedding the invoice

```java
byte[] rendering = new PdfRenderer().render(document);
byte[] hybrid = FacturX.embed(rendering, document,
        EmbedOptions.of(FacturXProfile.EN_16931));
```

`EmbedOptions` carries the profile, the `HybridFlavour`, the `PdfLimits` the input is opened
under, an optional `PdfaCheck` the caller lends, and the extension registries the writer of the
attachment is handed (`withExtensions(…)`). What the call writes:

| What | Written as |
|---|---|
| the invoice | the cross industry invoice of `CiiWriter`, as an embedded file stream with the media type `text/xml`, the size it decodes to and `/Params /ModDate` from BT-2, the issue date of the invoice, at midnight UTC — absent where the document states no BT-2 |
| the associated file | the file specification is named by the catalog's `/AF` array and declares `/AFRelationship /Alternative`, which is what Factur-X asks for of a profile that is an EN 16931 invoice |
| the name tree | the same specification, under the attachment name, in `/Names /EmbeddedFiles` |
| the metadata | the Factur-X extension schema and the four properties `DocumentType`, `DocumentFileName`, `Version` and `ConformanceLevel`, merged into the XMP packet the input already carries |

`embedWithReport` returns the file and the report of the writer beside it, because a term the cross
industry invoice had no place for is a term the archived record does not carry; the command line
prints it, and a term those registries keep out of every syntax on one `info:` line, as `convert` does.
[`examples/java/HybridInvoice.java`](../examples/java/HybridInvoice.java) is a whole program around this call.

## The ESJ document beside the invoice

The same invoice goes into the file a second time, as the canonical bytes of the ESJ document, and
as an enclosure rather than as the invoice: the XML stays what every reader of a hybrid file reads.

| What | Written as |
|---|---|
| name | `invoice.esj.json` |
| media type | `application/json` |
| relationship | `/AFRelationship /Supplement`, in the catalog's `/AF` array and in `/Names /EmbeddedFiles` beside the XML |
| `/Params /ModDate` | BT-2, as for the invoice |

It is written only where it is true. The cross industry invoice just written is read back with the
streaming reader, and the pair has to satisfy one rule:

1. both name the same semantic model;
2. the ESJ document holds together under the registry of that model — layer L2: every term is a
   term of that edition, every value satisfies its datatype, components and group chain;
3. every value the XML states stands in the ESJ document unchanged — same path, same content,
   same supplementary components;
4. everything the ESJ document states beyond that stands at a path at least one of whose terms
   the binding table of that syntax does not bind.

The attachment is measured on L1 and L2 and no further. Condition 2 keeps condition 1 from being a
claim about a header; condition 4 keeps the extension terms the XML has no place for, since a path
only an extension registry defines is reported on L2 as not checked. A core value the writer had to
leave out breaks the rule, and then **no ESJ document is attached**: the file carries the XML alone
and the command says why, naming the model or the paths. `EmbedOptions.withEsj(false)` and
`--no-esj` leave it out outright, which is no warning.

One function decides the rule on both sides: `esj validate` checks it over a file somebody else
wrote and reports it as a row of the container block
([`validation.md`](validation.md#the-container)); reading such a file is
[`pdf-input.md`](pdf-input.md).

## Flavour and profile

The flavour is one choice and not three: a file mixing one specification's attachment name with
another's extension schema would be one no specification defines.

| `HybridFlavour` | Attachment | Extension schema | Prefix, version |
|---|---|---|---|
| `FACTUR_X_1_0` (ZUGFeRD 2.1 and later) | `factur-x.xml` | `urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#` | `fx`, `1.0` |
| `ZUGFERD_2_0` | `zugferd-invoice.xml` | `urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#` | `zf`, `2p0` |

`xrechnung.xml` is not written: a conventional name with no container specification behind it
has no declaration to put beside it, and an XRechnung goes in as the profile XRECHNUNG of either
flavour. The profiles, and which of them are EN 16931 invoices, are in [`pdf-input.md`](pdf-input.md#profiles).

## What is refused

**Nothing is converted.** The input has to be a PDF/A-3 file, which is what a rendering of
`esj-render` is: part 3 is the part of ISO 19005 that allows a file of any type to be embedded.
By default that is read from the input's own packet; a caller who wants more than a declaration
passes a validator through `EmbedOptions.checkedWith(…)`, which is what `--verapdf` is on the
command line. `EmbedRefusedException` says which of these it was:

| Refused | Because |
|---|---|
| the input declares PDF/A-1 or PDF/A-2, or declares nothing | moving a document between parts means changing pages this module has not drawn |
| the input already carries an attachment that could be the invoice | a container declares one invoice, and a second would leave the choice to whoever reads the file |
| the input's packet already declares the Factur-X or ZUGFeRD properties, or a PDF/A extension schema of its own | a second set of the same properties gives the file two answers to every question |
| the input's embedded files name tree has children | the tree is written as one node, and rewriting a tree this module has not walked whole could lose an attachment |
| the input's embedded files name tree lists two files under one name | the tree is written as one node that maps a name to one file, so one of the two would be dropped |
| the input's packet ends its RDF with something other than the literal `</rdf:RDF>` | the properties are inserted as text, and a packet this module cannot find the end of is one it must not rewrite |
| the document's BT-24 is not the profile the options name | the container and the invoice would tell two consumers two different things |

## veraPDF is the oracle, not this project

veraPDF 1.30.2, flavour `3b`, in test scope: every rendering of the conformance corpus and of the
examples in both languages and on both papers is validated on every build, and every corpus
instance again after embedding, all with zero failed rules. `esj validate --verapdf <installation>`
gives a caller that answer about a file of their own; `esj embed --verapdf` asks it of the input.

## Determinism

The same document gives the same file on every run and every machine, rendering and embedding
alike. Producer and creator are fixed strings, file and XMP packet carry no date of any kind, the
font subsets are a function of the text, and the only date the embedding writes is the invoice's
own BT-2. The file identifier follows ISO 32000-1, 14.4: `/ID[0]` stays the input's, because the
pages are the pages that came in, and `/ID[1]` is a digest of the result. `PdfInvoiceImporter`
over the result gives the document that went in.

## Attribution

The container facts — attachment names, the XMP extension schema and its properties, the
`AFRelationship` values, what a further attachment beside the invoice may be — come from the
public Factur-X and ZUGFeRD documents, with their sources in [`pdf-input.md`](pdf-input.md#facts-this-module-relies-on-and-where-they-come-from)
and their terms in [`sources.md`](sources.md). No prose is reproduced and no code was copied.

The PDF library is Apache PDFBox 3.x under the Apache License, Version 2.0; the sRGB profile
embedded as the output intent is the ICC's, copied unaltered under the terms of its profile
library; the fonts are Liberation Sans under the SIL Open Font License, Version 1.1; the modules
of the payment code are encoded by ZXing Core under the Apache License, Version 2.0, and drawn
as rectangles of the page rather than placed as an image ([`letter-layout.md`](letter-layout.md#the-payment-code)).
veraPDF is a test-scope dependency under GPLv3+ / MPLv2+ and is in no artefact this project
publishes. The `NOTICE` of this repository names each of them.
