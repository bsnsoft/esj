# Changelog

The format is [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html). Until 1.0 the format itself may
still change; a change to it is named here under *Format*.

## [0.9.1] — 2026-09-22

### Fixed

- The macOS native executable of 0.9.0 could not render: its archive shipped without the shared
  libraries of the Java runtime that a rendering loads (`Can't load library: awt`). The packaging
  now copies them from the GraalVM that built the image where `native-image` leaves them out and
  fails the build where any is missing; a smoke test whose cases differ from the jar fails the
  build again instead of being lost in a pipe. The GraalVM build the executables are made with is
  now pinned by release and SHA-256 (`dist/graalvm.sh`): the 25.0.x line of the community builds,
  which the release runner had picked, produces a macOS image that cannot load `libawt` even with
  the libraries beside it; the 25.3 line does.

## [0.9.0] — 2026-09-22

First version. What it carries is the "Works today" list of the README.

### Added

- The format: a path-based JSON binding of the semantic model, with a machine-readable term
  registry of 196 terms, a JSON Schema, examples and the specification
  (EN 16931-1:2017+A1:2019/AC:2020).
- A second edition beside the default one, where a build carries its registry: EN 16931-1:2026,
  with its own registry of 255 terms, its own JSON Schema, its own typed view and editor, the
  semantic data type `Time` and the finding code `ESJ-L2-TIME`. Every command knows the edition
  a document names and measures it against that registry alone; the writers, the HTML
  visualization and the rule pack are written for one edition and refuse a document of another
  with exit code 4 rather than losing values ([`docs/editions.md`](docs/editions.md)). The files
  of that edition are separable: `model/en16931/2026.paths` lists them and the Maven profile
  `without-edition-2026` builds without them.
- Java library: paths on the document, a typed view and editor, canonical bytes and the two
  digests, and the structural layers L1 to L3 against the registry.
- A constrained builder generated from the registry — the structure of the model as step
  interfaces — and a domain API over it (`esj-invoice`): invoice objects with enums generated
  from the code list snapshots, profile defaults, derived totals, and a missing term refused by
  name.
- Import: UBL 2.1, CII D16B and hybrid PDFs by their embedded invoice; nothing is read off the
  page of a PDF.
- The B2C extension (`model/b2c/0.1.json`, module `esj-b2c`): four terms for the gross figures a
  consumer was shown, beside the net ones of the standard, with an overlay view and editor
  generated from that registry, three policies that derive the net invoice from them with exact
  arithmetic, and a consistency check over the two sets of figures. `--extension b2c` loads the
  registry on the command line, and `--extension xrechnung,b2c` loads both
  ([`docs/b2c.md`](docs/b2c.md)).
- A registry member `transport`, whose one value is `none`: an extension registry declares with it
  that the terms it defines are bound by no transport syntax by design. `esj validate` then runs
  the official artefacts over the XML an ESJ document is written to, counts that check and names
  the terms that stayed in the ESJ document, instead of leaving the run `INDETERMINATE` with the
  cause `term-not-in-syntax`. `model/b2c/0.1.json` declares it: a gross-priced invoice travels as
  the net values of the core terms with the difference in BT-114, so the written XML is the whole
  invoice. `examples/b2c-gross.esj.json` is `VALID` with `--extension b2c`; a document carrying
  terms of a registry without the declaration is unchanged.
- Export: the cross industry invoice and the two UBL 2.1 documents, Invoice and Credit Note,
  written by one engine from the same binding tables the readers match against, with the
  document type taken from the invoice type code BT-3. Where a syntax requires an element the
  semantic model has no term for, the value is a documented convention of the binding table
  and a note of the write report; nothing is invented silently.
- The writer matrix: the 40 business cases the conformance corpus carries in both syntaxes,
  each written through the semantic core into the other one and compared with the file that
  was already there ([`conformance/writers/matrix.md`](conformance/writers/matrix.md)).
- Validation: the official XML Schema and Schematron of a document's profile executed as data
  (pack `xrechnung/3.0.2/2026-08-31`, CEN artefacts 1.3.16), and the EN 16931 business rules as
  a native pack over the business terms (`rules/en16931/1.3.16`), with the verdicts `VALID`,
  `INVALID` and `INDETERMINATE` and an exit code per outcome. An ESJ document that never was
  XML is written through a binding table in memory — `--via cii`, the default, or `--via ubl` —
  so that the official artefacts judge it too; that row is required for such an input, and
  where the two syntaxes cannot say the same thing the row is not applicable and the reason is
  named.
- Rendering: one self-contained HTML page, or a PDF/A-3b file, German or English, deterministic;
  plain, or branded from a render template with letterhead, logo, colours, fonts and margins.
- A second page layout for the PDF, `--layout letter`: the invoice as the letter a business
  posts — the address field where DIN 5008 puts it, a reference line across the text area, a
  line table whose rows stay whole, a narrow block of totals, and the payment block with the
  EPC QR code of a credit transfer, known in Germany as the GiroCode. Codes a reader does not
  read are written under their names, from checked-in tables, and listed once under the closing
  heading. Both layouts keep one rule: every term occurrence of the document reaches a page
  ([`docs/letter-layout.md`](docs/letter-layout.md)).
- Hybrid PDF output: `esj render --embed cii` draws the pages and attaches the invoice as
  Factur-X, `esj embed` writes an invoice into a PDF/A-3 file from somewhere else, and
  `--verapdf <installation>` checks the PDF/A claim with a veraPDF of your own instead of
  believing what a file declares about itself.
- The same invoice as an ESJ document beside the embedded XML: `invoice.esj.json`,
  `application/json`, relationship `Supplement`, written by default wherever this tool embeds an
  invoice, and only where the two name one semantic model, the document holds together under that
  model and the two are two accounts of one invoice. `--no-esj` leaves it out, `esj validate`
  checks the pair and reports the row `ESJ document attached`, and a container whose two
  machine-readable accounts disagree is `Container: INVALID`
  ([`docs/pdf-output.md`](docs/pdf-output.md), `SPEC.md` section 15).
- The validation report: `esj validate --report <file.html|file.pdf>` writes one run as one
  self-contained file — verdict, digests, packs, the check table, the findings of both engines
  and the rendered invoice — in German or English, deterministic, on A4 or the paper
  `--report-page` names, and changing none of the printed lines. One a run could not deliver —
  an unwritable destination, or the deadline met while it is drawn — leaves with exit code 6
  unless the verdict is invalid and keeps code 1; the verdict is printed either way.
- `esj upgrade --to <edition>`: writes an ESJ document as a document of another edition, in
  both directions, from the mapping `model/en16931/upgrade-2017-2026.json` as data. Nothing is
  repaired, rounded or invented; every open point is reported and a value the target edition
  has no address for makes the run refuse until its path is named.
- The `esj` command line — `convert`, `upgrade`, `validate`, `render`, `embed`, `inspect`,
  `extract`, `get`, `list`, `diff`, `canonicalize` — with a documented contract of streams,
  exit codes and resource limits for callers that spawn it as a process.
- Verdicts follow the profile the document names: the level table of its profile is applied to a
  native finding as it is to one of the official artefacts, so a document the official validator
  accepts is not called invalid here.
- Packaging: a native executable, a Java 25 runtime image, a self-contained jar, a container
  image, and the release archives with their checksums.
- The libraries on Maven Central under `de.bsnsoft.esj`, each with its source and Javadoc jar and
  a signature, and `esj-bom` so that a build names a module without a version. The command line
  tool stays a release asset ([`docs/releasing.md`](docs/releasing.md)).
- The fixture manifest ([`conformance/fixtures/`](conformance/fixtures/README.md)): every case an
  implementation of the format has to pass, written in no programming language, generated from the
  reference implementation and held to it on every build, with a runner that drives an
  implementation over a pipe. Two implementations are measured by it beside the Java library, each
  reading the registries and the rule pack of this repository as data rather than carrying its own
  copy of the model: TypeScript ([`docs/bindings-ts.md`](docs/bindings-ts.md)) and C#
  ([`docs/bindings-csharp.md`](docs/bindings-csharp.md)).
- [`examples/java/HybridInvoice.java`](examples/java/HybridInvoice.java): one runnable program
  from invoice data to a hybrid PDF and back, compiled and run by a test of the build.
- [`docs/faq.md`](docs/faq.md) and [`docs/faq-de.md`](docs/faq-de.md): 48 questions in six
  sections, each answered with one command line or one snippet. A test runs every `esj` line of
  both pages but the one that needs an installed veraPDF, holds it to the exit code the answer
  states, and compares every quoted block with the page it is taken from.

### Format

- Nothing yet: version 0.1 is the first.
