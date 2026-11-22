# PDF output

*Part of [EN16931 Semantic JSON](../README.md).*

A hybrid invoice is a PDF a person reads, with the electronic invoice attached to it. This page
is the writing side; [`pdf-input.md`](pdf-input.md) reads, [`rendering.md`](rendering.md) draws.

| Command | What it does |
|---|---|
| `esj render <invoice> --embed cii --out <pdf>` | draws the pages and attaches the invoice to them |
| `esj embed <pdf> <invoice> --out <pdf>` | attaches the invoice to a PDF/A-3 file from somewhere else |

Both write a PDF/A-3 file that carries one embedded file, the cross industry invoice of
[`CiiWriter`](bindings.md), as an associated file, as Factur-X asks. Neither converts a page and
neither draws one over another: the file stays what it is.

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
        EmbedOptions.defaults().withProfile(FacturXProfile.EN_16931));
```

`EmbedOptions` carries the profile, the `HybridFlavour`, the `PdfLimits` the input is opened
under, an optional `PdfaCheck` you lend, and the extension registries the writer of the
attachment gets (`withExtensions(…)`). The call writes:

| What | Written as |
|---|---|
| the invoice | the cross industry invoice of `CiiWriter`, as an embedded file stream with the media type `text/xml`, the size it decodes to and `/Params /ModDate` from BT-2, the issue date of the invoice, at midnight UTC — absent where the document states no BT-2 |
| the associated file | the file specification is named by the catalog's `/AF` array and declares `/AFRelationship /Alternative`, which is what Factur-X asks for of a profile that is an EN 16931 invoice |
| the name tree | the same specification, under the attachment name, in `/Names /EmbeddedFiles` |
| the metadata | the Factur-X extension schema and the four properties `DocumentType`, `DocumentFileName`, `Version` and `ConformanceLevel`, merged into the XMP packet the input already carries |

`embedWithReport` returns the file and, beside it, the report of the writer. A term the cross
industry invoice has no place for is a term the archived record does not carry. The command
line prints that report, and lists the terms those registries keep out of every syntax on one
`info:` line, as `convert` does. An ESJ document of the invoice is one `esj convert` over the
file away. [`examples/java/HybridInvoice.java`](../examples/java/HybridInvoice.java) is a whole program around this call.

## Flavour and profile

You choose the flavour once, for all three columns: a file that mixes one specification's
attachment name with another's extension schema is one no specification defines.

| `HybridFlavour` | Attachment | Extension schema | Prefix, version |
|---|---|---|---|
| `FACTUR_X_1_0` (ZUGFeRD 2.1 and later) | `factur-x.xml` | `urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#` | `fx`, `1.0` |
| `ZUGFERD_2_0` | `zugferd-invoice.xml` | `urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#` | `zf`, `2p0` |

ESJ does not write `xrechnung.xml`: a conventional name with no container specification behind
it has no declaration to put beside it. An XRechnung goes in as the profile XRECHNUNG of either
flavour. [`pdf-input.md`](pdf-input.md#profiles) lists the profiles and which are EN 16931 invoices.

## What is refused

**Nothing is converted.** The input has to be a PDF/A-3 file, which is what a rendering of
`esj-render` is: part 3 is the part of ISO 19005 that allows a file of any type to be embedded.
By default ESJ reads that from the input's own packet. If you want more than a declaration,
pass a validator through `EmbedOptions.withCheck(…)`; `--verapdf` does that on the command
line. `EmbedRefusedException` says which of these it was:

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

veraPDF 1.30.2, flavour `3b`, runs in test scope on every build. It validates every rendering of
the conformance corpus and the examples, in both languages and on both papers, and every corpus
instance again after embedding: zero failed rules for all of them.
`esj validate --verapdf <installation>` gives you that answer about a file of your own;
`esj embed --verapdf` asks the same of the input.

## Determinism

The same document gives the same file on every run and every machine, for rendering and
embedding alike. Producer and creator are fixed strings. File and XMP packet carry no date of
any kind, and the font subsets are a function of the text. The only date the embedding writes
is the invoice's own BT-2. The file identifier follows ISO 32000-1, 14.4: `/ID[0]` stays the
input's, because the pages are the pages that came in, and `/ID[1]` is a digest of the result.
`PdfInvoiceImporter` over the result gives back the document that went in.

## Attribution

The container facts come from the public Factur-X and ZUGFeRD documents: attachment names, the
XMP extension schema and its properties, the `AFRelationship` values. Their sources are in
[`pdf-input.md`](pdf-input.md#facts-this-module-relies-on-and-where-they-come-from) and their
terms in [`sources.md`](sources.md). No prose is reproduced and no code was copied.

The PDF library is Apache PDFBox 3.x under the Apache License, Version 2.0. The sRGB profile of
the output intent is the ICC's, copied unaltered under the terms of its profile library. The
fonts are Liberation Sans under the SIL Open Font License, Version 1.1. ZXing Core encodes the
modules of the payment code, under the Apache License, Version 2.0, and the renderer draws them
as rectangles of the page, not as an image ([`letter-layout.md`](letter-layout.md#the-payment-code)).
veraPDF is a test-scope dependency under GPLv3+ / MPLv2+ and is in no artefact this project
publishes. The `NOTICE` of this repository names each of them.
