# EN16931 Semantic JSON

[![CI](https://github.com/bsnsoft/esj/actions/workflows/ci.yml/badge.svg)](https://github.com/bsnsoft/esj/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/bsnsoft/esj?label=release&color=blue)](https://github.com/bsnsoft/esj/releases)
[![Maven Central](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fde%2Fbsnsoft%2Fesj%2Fesj-bom%2Fmaven-metadata.xml&label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/de.bsnsoft.esj/esj-bom)
[![Homebrew](https://img.shields.io/badge/homebrew-bsnsoft%2Ftap%2Fesj-blue)](https://github.com/bsnsoft/homebrew-tap)
[![License](https://img.shields.io/github/license/bsnsoft/esj)](LICENSE)

**ESJ is the missing application format for EN 16931.** EN 16931 defines the invoice; ESJ makes it
directly usable by software: a flat JSON map keyed by the standard's business terms —
`"/BT-1": "RE-2026-4711"`, `"/BG-25/0/BT-131": "84.03"`. UBL and CII stay transport bindings; the
same invoice gives the same map whichever it arrived in, so an application stores, indexes, queries,
compares and hashes invoices without XML. A Java library reads, builds, validates, renders and
writes such documents back out as CII or UBL; the command line tool `esj` does the same for every
other runtime. Release candidate: not a CEN or KoSIT deliverable; format 0.1 is expected to become 1.0 unchanged.

```json
{
  "format": "EN16931-Semantic-JSON",
  "version": "0.1",
  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
  "values": {
    "/BT-1": "RE-2026-0042",
    "/BG-4/BT-27": "Example GmbH",
    "/BG-4/BT-29/0": { "value": "0088123456785", "scheme": "0088" },
    "/BG-25/0/BT-131": "250"
  }
}
```

[Demo video](https://www.youtube.com/watch?v=Hys6tKensF4) ([German version](https://www.youtube.com/watch?v=OBbLrAIi03w)) — two minutes; everything on screen is the output of `esj` 0.9.1, a PostgreSQL included.

```text
                ┌── UBL
                ├── CII
EN 16931 ─> ESJ ┤
                ├── PostgreSQL
                ├── API
                ├── ERP
                └── analytics
```

A value is the string the business term carries, and an object only where the semantic model gives
the term supplementary components; [`SPEC.md`](SPEC.md) defines the format. Which terms exist, how
often and of what type are facts of one registry per edition ([`model/README.md`](model/README.md)).

## Features

- Semantic addresses: the keys are the business terms of EN 16931, not UBL or CII element paths.
- Canonical bytes and two digests: comparison, deduplication and change detection, no parser.
- Validation by the official XSD and Schematron of the profile, run as data, and by the EN 16931
  business rules over business terms; findings by code, not XPath ([`docs/why.md`](docs/why.md)).
- Hybrid PDFs by their embedded invoice; nothing is read off the page, nothing is guessed at.
- A cross industry invoice, or a UBL invoice or credit note, written back out of the document
  from the binding tables the reader matches against ([`docs/bindings.md`](docs/bindings.md)).
- A rendering to read: one self-contained HTML page or a PDF/A-3b file, plain or branded from a
  template, with the invoice as Factur-X / ZUGFeRD 2.x and, beside it, the ESJ document of the
  same invoice, checked against the XML ([`docs/pdf-output.md`](docs/pdf-output.md)).
- That PDF as a letter, the default, on the sender's letterhead where a template brings one: the
  address field where DIN 5008 puts it, the reference line across the text area, and the EPC QR
  code in the payment block ([`docs/letter-layout.md`](docs/letter-layout.md)).
- A command line tool for every runtime that is not Java, packaged for machines without one, and
  a process boundary for untrusted input ([`docs/deployment.md`](docs/deployment.md)).
- An invoice written in the words of the domain: enums, profile defaults and derived totals,
  with the gross figures a consumer was shown kept beside the net ones of the standard
  ([`docs/b2c.md`](docs/b2c.md)).
- The format in other languages: a TypeScript and a C# implementation of [`SPEC.md`](SPEC.md),
  each measured by the fixture manifest of
  [`conformance/fixtures/`](conformance/fixtures/README.md) — every case an implementation has
  to pass, written in no programming language ([`docs/bindings-ts.md`](docs/bindings-ts.md),
  [`docs/bindings-csharp.md`](docs/bindings-csharp.md)).

## Quick start

`brew install bsnsoft/tap/esj` on macOS and Linux, `docker pull ghcr.io/bsnsoft/esj` from 0.9.2,
or an archive of the [release](https://github.com/bsnsoft/esj/releases): a native executable
needing no Java, a Java 25 runtime image, or the jar ([`docs/install.md`](docs/install.md)). From
source, three lines build the same executable:

```sh
mvn -B verify                                 # the self-contained jar, on JDK 17, 21 or 25
dist/package.sh native                        # or runtime-image, docker, zip
dist/out/esj-<version>-native-<os>-<arch>/esj validate invoice.xml
```

`bin/esj` runs the jar unpackaged; the transcripts here and under `docs/` write `esj` for that
script with `bin/` on the `PATH`, and [`docs/install.md`](docs/install.md) has what each artefact
needs. From 0.9.0 the libraries come from Maven Central: `de.bsnsoft.esj:esj-bom:0.9.1` imported once,
then a module by name ([`docs/java-api.md`](docs/java-api.md)); `mvn -B install` builds
`de.bsnsoft.esj:esj-core` and its siblings from source.

```console
$ esj validate conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
...
VALID
$ esj convert examples/standard-invoice.esj.json --to ubl --out invoice.ubl.xml
$ esj get examples/standard-invoice.esj.json /BG-22/BT-112
2915.5
$ esj render examples/standard-invoice.esj.json \
             --template examples/templates/letter.json --embed cii --out invoice.pdf
the ESJ document of this invoice is attached beside it as "invoice.esj.json"
$ esj validate invoice.pdf --report proof.pdf
...
Container:        OK
Invoice:          VALID
```

The last two commands are the lead use case: invoice data to a branded PDF/A-3b carrying the invoice
as Factur-X, then the check over it with `proof.pdf` as the record. Java does both directions:

```java
SemanticDocument invoice = new StreamingReader().read(Files.readAllBytes(xmlFile)).document();
SemanticDocument fromPdf = PdfInvoiceImporter.importPdf(Files.readAllBytes(pdfFile)).document();
String invoiceNumber = invoice.value(SemanticPath.of("/BT-1"))
        .map(SemanticValue::asString).orElseThrow();

byte[] pages = new PdfRenderer().render(invoice, RenderOptions.in(RenderLanguage.ENGLISH));
EmbedResult hybrid = FacturX.embedWithReport(pages, invoice,
        EmbedOptions.of(FacturXProfile.EN_16931));
hybrid.report().notes().forEach(note -> System.out.println("write note: " + note));
Files.write(out.resolve("invoice.pdf"), hybrid.pdf());
Files.write(out.resolve("invoice.esj.json"), EsjWriter.pretty().toBytes(invoice));
```

[`examples/java/HybridInvoice.java`](examples/java/HybridInvoice.java) is the whole program: built
with the domain API, read back from the file, the business rules checked in the three places a
sender can check them. [`docs/getting-started.md`](docs/getting-started.md) is the first hour.

## Persistence

Semantic paths are stable keys for indexes and projections. [`docs/storage.md`](docs/storage.md) is
the PostgreSQL side — `jsonb` column, views, expression indexes, materialized views, key/value table
— each statement run by a test that puts the UBL and the CII rendering of one invoice at one row.

## Validation

Three checks over one input. The official validation artefacts of the document's profile — the UBL
2.1 or CII D16B XML Schema, the EN 16931 Schematron of CEN/TC 434 and the XRechnung Schematron —
carried under [`packs/`](packs/README.md) as data and run in the pack `xrechnung/3.0.2/2026-08-31`.
The structural layers L1 to L3 against the registry. And the business rules of EN 16931-1, clause
6.4 as the pack [`rules/en16931/1.3.16`](rules/README.md) — 217 rules and seventeen dated code list
snapshots, over the business terms. An ESJ document is written out through a binding table in
memory so that the artefacts read it too, so every input gets all three, and a native finding is a
layer of its own, never ESJ conformance.

`VALID` and exit code 0 are given only where the complete check for that kind of input ran and found
nothing fatal; `INVALID` and 1 follow a fatal finding in anything that ran; `INDETERMINATE` and 9
name the components that did not run and why; a limit leaves with 7 and no verdict at all. A
container is judged beside the invoice, and its PDF/A conformance is what the file declares unless
`--verapdf` names a veraPDF of your own. `--report <file.html|file.pdf>` writes the run as one file
([`docs/validation.md`](docs/validation.md) has the table per input kind).

## Conformance

The corpus is the XRechnung test suite of KoSIT, 86 instances unmodified, 40 of them one business
case written twice. Of those 40 pairs, 18 arrive at the same semantic digest and 22 differ only
where the two KoSIT files differ — none through the import path, none through a mapper defect —
and each pair written through the semantic core into the other syntax reaches that same 18 and 22.
The two readers agree to the byte on 74 of the 86 instances. Of the 86 written back out, 85 are
accepted as a cross industry invoice and 80 read back as the document they were written from; 85
are accepted as a UBL invoice and all 86 read back ([`docs/conformance.md`](docs/conformance.md)).

Both engines are measured against the official ones on the same bytes. Against the KoSIT validator
the syntax engine agrees on every rule identifier of all 149 documents compared and on 146 of their
149 verdicts. Against the EN 16931 Schematron 1.3.16 over 448 mutations, the rule pack agrees on
every shape measured for 166 of its 217 rules, on one shape and not another for 33, differs for 16,
cannot be asked at all about 2, and leaves 0 unaccounted for; each difference is named in
[`docs/validation.md`](docs/validation.md#the-oracle-measured-not-asserted).

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

Works today: the format, the schema and the registry of 196 terms; reading, writing,
canonicalizing, hashing and the structural layers L1 to L3; import from UBL 2.1, CII D16B and
hybrid PDFs; both validation engines; the CII and UBL writers, as far as the round trips above
prove them; the HTML and PDF/A-3b renderings, the latter as a business letter or in the generic
layout; the constrained builder, the domain API, the B2C extension and the validation report; the
command line tool, packaged as a native executable, a Java 25 runtime image, a release zip and a
container image ([`docs/install.md`](docs/install.md)). Where a build carries its registry, EN
16931-1:2026 stands beside the default 2017 edition: its registry, typed view, structural
validation and `esj upgrade` between the two exist; its business rule pack and a UBL or CII
binding of its new terms do not, so a 2026 document validates `INDETERMINATE` at best and is not
written to XML ([`docs/editions.md`](docs/editions.md)). Beside the Java library, a TypeScript
and a C# implementation of the format, measured by the shared fixture manifest. Changes:
[`CHANGELOG.md`](CHANGELOG.md); a vulnerability: [`SECURITY.md`](SECURITY.md).

## Documentation

- [`SPEC.md`](SPEC.md) — the format, normative
- [`docs/getting-started.md`](docs/getting-started.md) — the first hour, complete
- [`docs/faq.md`](docs/faq.md) — one question, one command or one snippet
- [`docs/faq-de.md`](docs/faq-de.md) — the same questions in German
- [`docs/storage.md`](docs/storage.md) — invoices in a database, PostgreSQL first
- [`docs/java-api.md`](docs/java-api.md) — the reference implementation, every snippet a test
- [`docs/cli.md`](docs/cli.md) — the command line reference and the exit codes
- [`docs/validation.md`](docs/validation.md) — what each engine checks, what a verdict commits to
- [`docs/b2c.md`](docs/b2c.md) — the gross figures a consumer was shown, and the policies over them
- [`docs/editions.md`](docs/editions.md) — the editions of EN 16931-1, and `esj upgrade`
- [`docs/pdf-input.md`](docs/pdf-input.md) — hybrid PDFs: what is read, what is refused
- [`docs/pdf-output.md`](docs/pdf-output.md) — the PDF/A-3b file and the invoice inside it
- [`docs/rendering.md`](docs/rendering.md) — the HTML page and the PDF, and what neither does
- [`docs/letter-layout.md`](docs/letter-layout.md) — the invoice as the letter a business sends
- [`docs/templates.md`](docs/templates.md) — the render template: letterhead, logo, colours
- [`docs/bindings.md`](docs/bindings.md) — the binding tables, the readers and the two writers
- [`docs/bindings-ts.md`](docs/bindings-ts.md) — the TypeScript implementation of the format
- [`docs/bindings-csharp.md`](docs/bindings-csharp.md) — the C# implementation of the format
- [`docs/install.md`](docs/install.md) — the artefacts, what each needs, measured start-up
- [`docs/deployment.md`](docs/deployment.md) — running `esj` from a service
- [`docs/conformance.md`](docs/conformance.md) — the corpus, the litmus test and the ledgers
- [`docs/why.md`](docs/why.md) — why this exists
- [`docs/design-decisions.md`](docs/design-decisions.md) — the trade-offs, and the non-goals
- [`docs/related-work.md`](docs/related-work.md) — the neighbours, verified
- [`docs/legal-de.md`](docs/legal-de.md) — § 14 UStG and the interoperability wording
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
