# Hybrid invoice fixtures

*Part of [EN16931 Semantic JSON](../../README.md).*

One file, this project's own.

| File | What it is |
|---|---|
| `factur-x.pdf` | a one-page PDF carrying `../kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml` as the attachment `factur-x.xml` |

It is the ordinary shape of a hybrid invoice and nothing more. It has one page, and the invoice
attached with the media type `text/xml`. The catalog's `/AF` array names the attachment with the
relationship `Alternative`. An XMP packet carries the PDF/A-3B declaration and the 4 properties
of the Factur-X extension schema (`DocumentType`, `DocumentFileName`, `Version`,
`ConformanceLevel`). The conformance level is `XRECHNUNG`, the profile the invoice inside it
writes in BT-24.

**The PDF/A-3B declaration is a declaration.** PDFBox wrote the file, and nobody validated any
part of ISO 19005-3 against it, here or anywhere in this repository. If you need PDF/A
conformance, validate it with a validator built for that. The file is for the container: the
name tree, the associated files array, the embedded file stream and the XMP packet, which is
what `esj-pdf` reads.

This project produced it with `esj-cli/src/test/java/de/bsnsoft/esj/cli/TestPdfs.java`, from
material this repository already carries. Nothing in it is third-party material. The invoice is
one of the KoSIT test suite instances that `../kosit/README.md` records, under the Apache
License, Version 2.0, and the container around it was written here.

`PdfInputTest.theExampleContainerCarriesTheCorpusInvoice` runs `esj extract` over this file and
compares the result with the corpus invoice byte for byte. The two cannot drift apart without
the build saying so. The pages that show the command line use this file, and
`ReadmeCliExamplesTest` runs what they show.

The hostile containers are not here: an inflate bomb, a spoofed attachment name, two invoices, a
truncated file. The tests of `esj-pdf` build them in the source of the test that needs them. A
fixture whose own source says what makes it the case it is is worth more than a binary somebody
has to be careful with.
