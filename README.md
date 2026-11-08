# EN16931 Semantic JSON

[![CI](https://github.com/bsnsoft/esj/actions/workflows/ci.yml/badge.svg)](https://github.com/bsnsoft/esj/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/bsnsoft/esj?include_prereleases&label=release&color=blue)](https://github.com/bsnsoft/esj/releases)
[![Maven Central](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fde%2Fbsnsoft%2Fesj%2Fesj-bom%2Fmaven-metadata.xml&label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/de.bsnsoft.esj/esj-bom)
[![Homebrew](https://img.shields.io/badge/homebrew-bsnsoft%2Ftap%2Fesj-blue)](https://github.com/bsnsoft/homebrew-tap)
[![License](https://img.shields.io/github/license/bsnsoft/esj)](LICENSE)

For software that receives, stores, checks or sends e-invoices under EN 16931:
XRechnung, ZUGFeRD, Factur-X, Peppol BIS.

**Your invoices arrive as UBL, as CII or inside a PDF. ESJ gives your software one invoice.**

ESJ is a flat JSON document keyed by the business terms of the standard: `/BT-1` is the invoice
number, `/BG-25/0/BT-131` the net amount of the first line. A UBL invoice, its CII twin and the
hybrid PDF that carries it read to the same document. You store it in PostgreSQL, index the
invoice number and compare two files without parsing XML in your own code. When an invoice has
to leave, ESJ writes CII or UBL again and checks it with the official XSD and Schematron.

Try it on one of your own invoices: `brew install bsnsoft/tap/esj`, then `esj validate invoice.xml`.

```json
{
  "format": "EN16931-Semantic-JSON",
  "version": "1.0",
  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
  "values": {
    "/BT-1": "RE-2026-0042",
    "/BG-4/BT-27": "Example GmbH",
    "/BG-4/BT-29/0": { "value": "0088123456785", "scheme": "0088" },
    "/BG-25/0/BT-131": "250"
  }
}
```

EN 16931 defines the invoice once. Most applications still build a model of their own behind two
parsers and a mapping. ESJ is that model, specified in [`SPEC.md`](SPEC.md) and implemented in
Java, TypeScript and C#. UBL and CII stay what goes over the wire.

```text
                ┌── UBL
                ├── CII
EN 16931 ─> ESJ ┤
                ├── PostgreSQL
                ├── API
                ├── ERP
                └── analytics
```

A value is the string its business term carries. It is an object only where the term has
components, such as the scheme of an identifier. One registry per edition records which terms
exist, how often and of which type ([`model/README.md`](model/README.md)).

[Demo video, two minutes](https://www.youtube.com/watch?v=Hys6tKensF4)
([German](https://www.youtube.com/watch?v=OBbLrAIi03w)): everything on screen is the output of
`esj` 0.9.1, a PostgreSQL included. [One-minute short](https://www.youtube.com/shorts/3yCHqg5ENno)
(German, animated): what ESJ is for, without a terminal.

## What you can do with it

- **Read** UBL 2.1, CII D16B and hybrid PDFs (Factur-X, ZUGFeRD 2.x) into one document. ESJ takes
  the embedded invoice and reads nothing off the page; a PDF without one is refused
  ([`docs/pdf-input.md`](docs/pdf-input.md)).
- **Store and query** invoices in PostgreSQL, with an index on `/BT-1` ([Persistence](#persistence)).
- **Check** an invoice with the official XSD and Schematron of its profile and the 217 business
  rules of EN 16931; `VALID` comes only when all of them ran ([Validation](#validation)).
- **Compare** two files without a parser: canonical bytes and two digests say whether they carry
  the same invoice.
- **Build** an invoice in Java with enums, profile defaults and derived totals, and write it as
  CII or UBL from the binding tables the reader uses ([`docs/bindings.md`](docs/bindings.md)).
- **Print** it as an HTML page or as a PDF/A-3b file with the XML embedded as Factur-X / ZUGFeRD
  2.x. By default the PDF is a letter, on your letterhead if a template brings one: the address
  field where DIN 5008 puts it, a reference line across the text area and an EPC QR code in the
  payment block ([`docs/pdf-output.md`](docs/pdf-output.md),
  [`docs/letter-layout.md`](docs/letter-layout.md)).
- **Look at** an invoice: `esj view invoice.pdf` opens one page with the PDF as it came, the
  letter drawn from its data, the KoSIT view and, with `--report`, the validation report;
  `--text` prints it in the terminal. The Linux packages and the macOS archive offer it under
  *Open with* for XML and PDF files ([`docs/view.md`](docs/view.md)).
- **Keep the gross figures** a consumer was shown beside the net ones of the standard
  ([`docs/b2c.md`](docs/b2c.md)).
- **Run it without Java**: the native command line tool validates a 7 kB invoice in 0.12 s
  ([`docs/install.md`](docs/install.md)) and is a process boundary for untrusted input
  ([`docs/deployment.md`](docs/deployment.md)). `esj serve` offers its tools over REST, OpenAPI
  and MCP, `esj mcp` to an AI agent on the same machine, every call a process of its own
  ([`docs/serve.md`](docs/serve.md), a preview).
- **Use the format in TypeScript or C#**: both implement [`SPEC.md`](SPEC.md) and pass the
  fixture manifest of [`conformance/fixtures/`](conformance/fixtures/README.md), the cases every
  implementation has to pass, written in no programming language
  ([`docs/bindings-ts.md`](docs/bindings-ts.md), [`docs/bindings-csharp.md`](docs/bindings-csharp.md)).

## Measured against the official validators

The test corpus is the XRechnung test suite of KoSIT: 86 files, unmodified, 40 of them one
business case written as UBL and as CII. Of those 40 pairs, 18 read to the same semantic digest.
The other 22 differ only where the two KoSIT files differ, none through the import path and none
through a mapper defect. Written through ESJ into the other syntax, the pairs reach the same 18
and 22. The two readers agree to the byte on 74 of the 86 files. Of the 86 written back out, 85
are accepted as a cross industry invoice and 80 read back as the document they were written
from; 85 are accepted as a UBL invoice and all 86 read back
([`docs/conformance.md`](docs/conformance.md)).

Both engines run against the official ones on the same bytes. On 149 documents the syntax engine
reports the same rule identifiers as the KoSIT validator, and the same verdict on 146. Over 455
mutations the rule pack answers like the EN 16931 Schematron 1.3.17 on every case for 137 of its
217 rules. It agrees on some cases and not on others for 53, differs for 25 and cannot be
compared for 2. That leaves 0 unaccounted for, and
[`docs/validation.md`](docs/validation.md#the-oracle-measured-not-asserted) names each
difference.

ESJ was checked against the BMF letter of 15 October 2026 on mandatory e-invoicing in Germany,
and nothing had to change ([`docs/legal-de.md`](docs/legal-de.md)). ESJ does not replace a
transport syntax and is not a CEN or KoSIT deliverable: your customer's portal still receives
UBL or CII, and ESJ writes it. Format 1.0, release candidate of version 1.0.0; documents of
format 0.1 keep their digests.

## Quick start

Install with `brew install bsnsoft/tap/esj` on macOS and Linux, with `apt-get install esj` or
`dnf install esj` from the [package repositories](docs/install.md#debian-and-ubuntu-apt) (from
0.9.8), with `docker pull ghcr.io/bsnsoft/esj` (from 0.9.2), or from an archive of the
[release](https://github.com/bsnsoft/esj/releases): a native executable that needs no Java, a
Java 25 runtime image, or the jar ([`docs/install.md`](docs/install.md)). Three lines build the
same executable from source:

```sh
mvn -B verify                                 # the self-contained jar, on JDK 17, 21 or 25
dist/package.sh native                        # or runtime-image, docker, zip
dist/out/esj-<version>-native-<os>-<arch>/esj validate invoice.xml
```

`bin/esj` runs the jar without packaging it; the transcripts here and under `docs/` write `esj`
for that script with `bin/` on the `PATH`. The libraries are on Maven Central from 0.9.0: import
`de.bsnsoft.esj:esj-bom:1.0.0-rc.6` once, then add a module by name
([`docs/java-api.md`](docs/java-api.md)). `mvn -B install` builds `de.bsnsoft.esj:esj-core` and
its siblings from source.

```console
$ esj validate conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
...
VALID
$ esj convert examples/standard-invoice.esj.json --to ubl --out invoice.ubl.xml
$ esj get examples/standard-invoice.esj.json /BG-22/BT-112
2915.5
$ esj render examples/standard-invoice.esj.json \
             --template examples/templates/letter.json --embed cii --out invoice.pdf
$ esj validate invoice.pdf --report proof.pdf
...
Container:        OK
Invoice:          VALID
```

The last two commands are the lead use case. They turn invoice data into a branded PDF/A-3b that
carries the invoice as Factur-X, then check it and keep `proof.pdf` as the record. Java does both
directions:

```java
SemanticDocument invoice = new StreamingReader().read(Files.readAllBytes(xmlFile)).document();
SemanticDocument fromPdf = PdfInvoiceImporter.importPdf(Files.readAllBytes(pdfFile)).document();
String invoiceNumber = invoice.value(SemanticPath.of("/BT-1"))
        .map(SemanticValue::asString).orElseThrow();

byte[] pages = new PdfRenderer().render(invoice,
        RenderOptions.defaults().withLanguage(RenderLanguage.ENGLISH));
EmbedResult hybrid = FacturX.embedWithReport(pages, invoice,
        EmbedOptions.defaults().withProfile(FacturXProfile.EN_16931));
hybrid.report().notes().forEach(note -> System.out.println("write note: " + note));
Files.write(out.resolve("invoice.pdf"), hybrid.pdf());
Files.write(out.resolve("invoice.esj.json"), EsjWriter.pretty().toBytes(invoice));
```

[`examples/java/HybridInvoice.java`](examples/java/HybridInvoice.java) is the whole program: it
builds the invoice with the domain API, reads it back from the file and checks the business rules
in the three places a sender can check them. [`docs/getting-started.md`](docs/getting-started.md)
takes you through the first hour.

## Persistence

Use the semantic paths as stable keys for indexes and projections.
[`docs/storage.md`](docs/storage.md) shows the PostgreSQL side: a `jsonb` column, views,
expression indexes, materialized views and a key/value table. A test runs each statement and puts
the UBL and the CII file of one invoice into one row.

## Validation

`esj validate` runs three checks over one input:

1. The official validation artefacts of the document's profile: the UBL 2.1 or CII D16B XML
   Schema, the EN 16931 Schematron of CEN/TC 434 and the XRechnung Schematron. They are carried
   under [`packs/`](packs/README.md) as data and run in the pack `xrechnung/3.0.2/2026-10-12`
   (CEN release 1.3.17; `--pack` selects the release before it).
2. The structural layers L1 to L3 against the registry.
3. The business rules of EN 16931-1, clause 6.4, as the pack
   [`rules/en16931/1.3.17`](rules/README.md): 217 rules and 17 dated code list snapshots, over
   the business terms.

An ESJ document is written out through a binding table in memory, so the artefacts read it too
and every input gets all three checks. Every finding carries a code, not an XPath; a native
finding is a layer of its own, never ESJ conformance. The Peppol BIS Billing 3.0 artefacts may not be redistributed, so `esj packs fetch`
builds their pack on your machine from a pinned recipe
([`docs/validation.md`](docs/validation.md#packs-made-on-this-machine)).

| Verdict | Exit code | When |
|---|---|---|
| `VALID` | 0 | the complete check for this kind of input ran and found nothing fatal |
| `INVALID` | 1 | anything that ran found something fatal |
| `INDETERMINATE` | 9 | a component did not run; the output names it and the reason |
| none | 7 | a limit was reached |

A container is judged beside the invoice. Its PDF/A conformance is what the file declares, unless
`--verapdf` names a veraPDF installation of your own. `--report <file.html|file.pdf>` writes the
run as one file; [`docs/validation.md`](docs/validation.md) has the table per input kind.

## Java

| Layer | What it looks like | What it is for | Module |
|---|---|---|---|
| Paths on the document | `document.value(SemanticPath.of("/BT-1"))` | storage, queries, mapping, tooling: every term through one accessor | `esj-core` |
| Typed view and editor | `En16931.view(document).seller().name()` | reading and writing by name instead of by identifier | `esj-typed` |
| Constrained builder | `InvoiceBuilder.create(Profile.EN16931)` | the structure of the model as step interfaces, generated from the registry | `esj-typed` |
| Domain API | `Invoice.create(Profile.XRECHNUNG_3_0)` | invoice objects with enums, profile defaults and derived totals | `esj-invoice` |
| B2C overlay | `Gross.on(invoice)` | the gross figures a consumer was shown, and the policies that derive the net invoice | `esj-b2c` |
| Command line | `esj validate invoice.pdf` | every runtime that is not Java, and a process boundary | `esj-cli` |

## Status

Works today: the format, its schema and the registry of 196 terms; reading, writing,
canonicalizing, hashing and the structural layers L1 to L3; import from UBL 2.1, CII D16B and
hybrid PDFs; both validation engines; the CII and UBL writers, as far as the round trips above
prove them; the HTML and PDF/A-3b renderings, as a business letter or in the generic layout; the
constrained builder, the domain API, the B2C extension and the validation report; the command
line tool as a native executable, a Java 25 runtime image, a release zip and a container image
([`docs/install.md`](docs/install.md)); a TypeScript and a C# implementation of the format,
measured by the shared fixture manifest.

EN 16931-1:2026 stands beside the default 2017 edition as a preview, where a build carries its
registry. Its registry, typed view, structural validation, `esj upgrade` and a rule pack exist; a
UBL or CII binding of its new terms does not, so a 2026 document validates `INDETERMINATE` at
best ([`docs/editions.md`](docs/editions.md)).

Changes are listed in [`CHANGELOG.md`](CHANGELOG.md); report a vulnerability as
[`SECURITY.md`](SECURITY.md) describes.

## Documentation

- [`SPEC.md`](SPEC.md) — the format, normative
- [`docs/getting-started.md`](docs/getting-started.md) — the first hour, complete
- [`docs/faq.md`](docs/faq.md) — one question, one command or one snippet
- [`docs/faq-de.md`](docs/faq-de.md) — the same questions in German
- [`docs/storage.md`](docs/storage.md) — invoices in a database, PostgreSQL first
- [`docs/java-api.md`](docs/java-api.md) — the reference implementation, every snippet a test
- [`docs/compatibility.md`](docs/compatibility.md) — what a release may change, and what it keeps
- [`docs/cli.md`](docs/cli.md) — the command line reference and the exit codes
- [`docs/validation.md`](docs/validation.md) — what each engine checks, what a verdict commits to
- [`docs/b2c.md`](docs/b2c.md) — the gross figures a consumer was shown, and the policies over them
- [`docs/editions.md`](docs/editions.md) — the editions of EN 16931-1, and `esj upgrade`
- [`docs/pdf-input.md`](docs/pdf-input.md) — hybrid PDFs: what is read, what is refused
- [`docs/pdf-output.md`](docs/pdf-output.md) — the PDF/A-3b file and the invoice inside it
- [`docs/rendering.md`](docs/rendering.md) — the HTML page and the PDF, and what neither does
- [`docs/view.md`](docs/view.md) — `esj view`: one page about an invoice file, and *Open with*
- [`docs/letter-layout.md`](docs/letter-layout.md) — the invoice as the letter a business sends
- [`docs/templates.md`](docs/templates.md) — the render template: letterhead, logo, colours
- [`docs/bindings.md`](docs/bindings.md) — the binding tables, the readers and the two writers
- [`docs/bindings-ts.md`](docs/bindings-ts.md) — the TypeScript implementation of the format
- [`docs/bindings-csharp.md`](docs/bindings-csharp.md) — the C# implementation of the format
- [`docs/install.md`](docs/install.md) — the artefacts, what each needs, measured start-up
- [`docs/deployment.md`](docs/deployment.md) — running `esj` from a service
- [`docs/serve.md`](docs/serve.md) — `esj serve` and `esj mcp`: REST, OpenAPI and MCP
- [`docs/conformance.md`](docs/conformance.md) — the corpus, the litmus test and the ledgers
- [`docs/why.md`](docs/why.md) — why this exists
- [`docs/design-decisions.md`](docs/design-decisions.md) — the trade-offs, and the non-goals
- [`docs/related-work.md`](docs/related-work.md) — the neighbours, verified
- [`docs/legal-de.md`](docs/legal-de.md) — § 14 UStG, the interoperability wording and the BMF letters
- [`docs/sources.md`](docs/sources.md) — every source, its licence and its digest
- [`model/README.md`](model/README.md) — how each column of every registry was derived
- [`docs/releasing.md`](docs/releasing.md) — how a version is cut, and where it goes
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — what a useful contribution looks like

## License

This project's own work — code, specification text, schema, registry, examples and the rule packs
under [`rules/`](rules/README.md) — is under the Apache License, Version 2.0
([`LICENSE`](LICENSE)). Third-party material under [`packs/`](packs/README.md), `conformance/`, the
code list snapshots, the display-name tables derived from them and the vendored directories of
`esj-xr` and `esj-render` keeps its own licence, unmodified, with its digests recorded. This
implements EN 16931-1:2017+A1:2019/AC:2020 and reproduces none of its normative prose; the full
statements: [`NOTICE`](NOTICE), [`docs/sources.md`](docs/sources.md).

Author: Christian Bürckert. Publisher: BSNSoft Solutions GmbH.
