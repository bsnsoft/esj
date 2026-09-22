# Hybrid invoice fixtures

*Part of [EN16931 Semantic JSON](../../README.md).*

One file, and it is this project's own.

| File | What it is |
|---|---|
| `factur-x.pdf` | a one-page PDF carrying `../kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml` as the attachment `factur-x.xml` |

It is the ordinary shape of a hybrid invoice and nothing more: one page, the invoice attached
with the media type `text/xml`, the attachment named in the catalog's `/AF` array with the
relationship `Alternative`, and an XMP packet carrying the PDF/A-3B declaration and the four
properties of the Factur-X extension schema (`DocumentType`, `DocumentFileName`, `Version`,
`ConformanceLevel`). The conformance level is `XRECHNUNG`, because that is the profile the
invoice inside it writes in BT-24.

**The PDF/A-3B declaration is a declaration.** The file was written with PDFBox and no part of
ISO 19005-3 was validated against it, here or anywhere in this repository; a consumer that
needs PDF/A conformance validates it with a validator built for that. What the file is for is
the container: the name tree, the associated files array, the embedded file stream and the XMP
packet, which is what `esj-pdf` reads.

It was produced by this project, from material this repository already carries, with
`esj-cli/src/test/java/de/bsnsoft/esj/cli/TestPdfs.java`. Nothing in it is
third-party material: the invoice is one of the KoSIT test suite instances that
`../kosit/README.md` records, under the Apache License, Version 2.0, and the container around
it was written here.

`PdfInputTest.theExampleContainerCarriesTheCorpusInvoice` runs `esj extract` over this file and
compares the result with the corpus invoice byte for byte, so the two cannot drift apart
without the build saying so. The pages that show the command line use this file, and
`ReadmeCliExamplesTest` runs what they show.

The hostile containers — an inflate bomb, a spoofed attachment name, two invoices, a truncated
file — are not here. They are built in the tests of `esj-pdf`, in the source of the test that
needs them, because a fixture that says in its own source what makes it the case it is is worth
more than a binary somebody has to be careful with.
