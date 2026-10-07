# Changelog

The format is [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html). Until 1.0 the format itself may
still change; a change to it is named here under *Format*.

## [0.9.5] — unreleased

### Added

- Six negative fixtures under `examples/invalid/`, run by the fixture manifest over the Java, the
  TypeScript and the C# reader: `extension-number-exponent-overflow` and `-underflow`
  (`ESJ-L1-EXT-NUMBER`), `value-depth-32` and `value-depth-33` (`ESJ-L1-JSON-TYPE`, then
  `ESJ-L1-LIMIT`: the walk past a structure inside `values`), `path-syntax-with-array-value`
  (`ESJ-L1-PATH-SYNTAX` and `ESJ-L1-JSON-TYPE`) and `values-deep-array` (`ESJ-L1-ENVELOPE-VALUE`).

### Changed

- `esj render --html` is held to `--max-output-bytes` (64 MiB, 1 GiB under `--limits large`), as
  every XML document the tool writes is: the page is measured in bytes of UTF-8 while it is
  written, and one past the bound leaves with exit code 7 and nothing written, rather than being
  finished in memory first. In the library the bound is `RenderOptions.maxHtmlBytes()`
  (`withMaxHtmlBytes(long)`, default `DEFAULT_MAX_HTML_BYTES`, 1 GiB) and is reached with a
  `RenderLimitException`. `RenderOptions` has a seventh member; the constructor of six stays.
- `esj-render` replaces U+007F and the C1 controls with a space in every form — the HTML
  rendering and the report carried them as they stood, the PDF printed a question mark — and
  turns U+0085, U+2028 and U+2029 into line feeds in all of them; one class holds the sets for
  every form.
- `dist/package.sh smoke` compares a container image only when `dist/package.sh docker` built it
  from the jar it is compared with, and fails when the image that target built is gone or stale;
  an older image of the same tag is named and not compared. `dist/smoke.sh` takes a relative path
  in a command relative to the directory it was run from.
- `bin/without-edition-2026.sh` makes its copy under `$TMPDIR` and removes it when it ends;
  `--keep` keeps it.
- `-Dsurefire.failIfNoSpecifiedTests=false` lets `mvn -Dtest=<class> -pl <module> -am` pass the
  modules without that test; without it, a pattern that matches no test still fails.
- `esj-render`'s sRGB profile is recorded as compared byte for byte with the file the
  International Color Consortium publishes (`icc/README.md`, `docs/sources.md`).
- The bindings carry their publisher's names and the project's version: the npm package is
  `@bsnsoft/esj` (was `en16931-semantic-json`), the C# package, assembly and namespace
  `BSNSoft.Esj` (was `En16931.SemanticJson`, projects under `bindings/csharp/BSNSoft.Esj*`). The C#
  build reads the version from `pom.xml`; `npm run sync-version` writes it into `package.json` and
  `package-lock.json`, and a test fails while they differ. Nothing is published yet.

### Fixed

- `esj render` in the letter layout — the default since 0.9.2 — left with exit code 5, "internal
  error", where the invoice number (BT-1) or the buyer reference (BT-10) carried a line feed,
  which ESJ allows in every text; the generic layout left with 2 on the same invoice number. Both
  layouts draw such a document now. A line end of any kind — CR LF, CR, VT, FF, U+0085, U+2028,
  U+2029 — breaks a block as a line feed does and is a space on a line that has to stay one: the
  page footer, the head of a following page, a figure of the totals, the footer of the PDF report.
  No character the embedded faces have no glyph for reaches them.
- U+061C ARABIC LETTER MARK reached the HTML rendering and the HTML report, although the
  renderings replace the characters that direct the reading order with a space. HTML, PDF and
  report now replace one set: U+061C, U+200E, U+200F, U+202A–U+202E, U+2066–U+2069 and
  U+FFF9–U+FFFB.
- The EPC QR code (GiroCode) of the letter dropped an element only where it carried a line feed
  or a carriage return; U+2028, U+2029, U+0085, VT and FF passed into the payload, so a reader
  that splits lines where Unicode does would read the account and the amount one line too far
  down. No element carries a line end of any kind, another control character, a character that
  directs the reading order or half a surrogate pair now: where the beneficiary, the remittance
  information or the invoice number standing in for it holds one, the letter draws no code and
  says under *Further details* which element it was.
- Render templates: a reference was checked as a name, so a symbolic link beside the template
  led out of its directory, and a link to `/dev/zero` or a small PNG that declares a vast picture
  ran the heap out. A reference is now checked as the file it reaches — a link out of the
  directory and anything that is not a regular file are refused — a file is refused by its size
  before it is read (32 MiB), an image by the size its header states before it is decoded
  (36 million pixels, a page at 600 dots per inch), and the template file itself is read to
  32 MiB at most. Each refusal names the file and leaves with exit code 2, as every template error
  does.
- TypeScript: a number inside `extensions` whose canonical form is long (`1e500000000`,
  `1e-500000000`, `1e999999999`, `0.` and a million zeros) cost up to 600 MiB, minutes, or an
  uncaught `RangeError` from `readDocument`. The length of the canonical form is computed before
  any digit is written, the exponent saturated, as in Java and C#: `ESJ-L1-EXT-NUMBER` at once.
  `canonicalNumber` throws `EsjError` (`ESJ-L1-EXT-NUMBER`) for such a number.
- TypeScript: the reader streams. It judges each member where the text reaches it and walks past
  what it refuses without building it: 64 MiB of `[0,0,…]` where a string belongs cost 2.6 GiB and
  ran out of memory under a 1 GiB heap, now 0.5 s and less than 300 MiB. Where its findings
  differed from Java's they no longer do: the value under a name that is no path is judged, a
  finding confined to one member stands before a JSON error after it, an undefined envelope member
  ends the read before broken text behind it, a JSON error or a limit inside a member of `values`
  names that member's path, and a member name is held to the larger of the string and path bound.
- TypeScript: reader and canonical writer keep the containers they open on a stack. A raised
  `maxExtensionDepth` reads and canonicalizes a document 60 000 levels deep, where the parser
  overflowed the stack and `readDocument` turned that into `TypeError: value is not iterable`;
  `readDocument` now passes on any error that is not a finding unchanged.
- TypeScript: the canonical and the pretty form refuse a string with a lone surrogate with
  `EsjError` (`ESJ-L1-SURROGATE`), as Java and C# do, instead of writing U+FFFD; `semanticDigest`
  and `documentDigest` reject with it rather than throw.
- TypeScript: `npm run build` type-checks again (TypeScript 7 loads no Node types unasked).
- C#: the reader answers as the Java one where it did not. An envelope member of the wrong JSON
  type is refused at its first token (a deep array there was `ESJ-L1-LIMIT`, now
  `ESJ-L1-ENVELOPE-VALUE`); a number token longer than the string bound is `ESJ-L1-LIMIT` wherever
  it stands (was `ESJ-L1-JSON-TYPE` inside `values`), and so is a member name longer than the
  larger of the string and path bound.

### Security

- The release archives and the container image carry a build provenance attestation, signed
  through Sigstore and kept by GitHub: `gh attestation verify <archive> --repo bsnsoft/esj`, and
  `gh attestation verify oci://ghcr.io/bsnsoft/esj:<version> --repo bsnsoft/esj` for the image.
  `docs/install.md` ("Checking a download") and `SECURITY.md` name the fingerprint of the key the
  artefacts on Maven Central are signed with, `39BA1E760ADE6940558B6EEC25FD2A2F5B520EB8`, and say
  that the macOS executable is signed ad hoc and not notarised.
- The base images of `dist/Dockerfile` and `dist/Dockerfile.native` are pinned by digest beside
  their tags, and Dependabot moves the digests; the image of a release names the base it was built on.
- `docs/cli.md` names the veraPDF releases `--verapdf` should run — 1.30.2 or later, 1.31.71 or
  later on veraPDF's development line; earlier ones have advisories for untrusted PDFs — and caps
  its heap through `JAVA_OPTS`.
- The workflows pin every action to a commit and keep no token in a checkout; the release and
  publish jobs build without a Maven cache. A release tag stops the release before anything is
  built unless it reads `v1.2.3` or `v1.2.3-rc.1` and names the version of the POM; `publish.yml`
  takes its tag only in that form and checks it out as a tag. Every push to `main` submits the
  resolved Maven dependency tree, so that Dependabot alerts cover the libraries the jar carries
  through other ones (fontbox, pdfbox-io, commons-logging, xmlresolver).


## [0.9.4] — 2026-10-07

### Added

- Pack directories: `esj validate` and `esj inspect` take `--packs <directory>` (repeatable) and
  read the environment variable `ESJ_PACKS` (a path list); every `<id>/<version>/<release>/pack.json`
  found there joins the bundled packs and is chosen by the profile of the document. A pack with the
  identity of a bundled one, and a profile two packs recognize, are refused by name — except where
  those packs are releases of one pack (same id and version): then the newest release is taken,
  compared part by part (3.0.10 after 3.0.9), and `--pack` takes another. Every report
  names the origin of the pack beside its identity: `syntax.pack.source` is `directory` and the new
  `syntax.pack.location` the directory it was read from (`null` for a bundled pack), and
  `esj --list-packs` prints `origin` for every pack.
- `esj packs list` and `esj packs fetch <recipe> --into <directory> [--replace]`. `fetch` follows a
  recipe this build carries for artefacts that may be used but not redistributed: it downloads the
  files over https, refuses any whose SHA-256 is not the one the recipe pins, compiles the
  Schematron to XSLT in process with the ISO Schematron XSLT 2 skeleton (MIT, now in
  `esj-syntax`), copies the schema modules out of the bundled pack and writes a pack whose
  manifest records the source URLs, the fetch date and the SHA-256 of every file. It is the only
  command that opens a network connection.
- The recipes `peppol-bis-billing-3.0.21` and `peppol-bis-billing-3.0.20`: the Peppol BIS
  Billing 3.0 validation artefacts, as published by OpenPeppol — the Peppol rules for UBL and CII
  and the EN 16931 Schematron that release ships (1.3.16 and 1.3.15). 3.0.21 is the May 2026
  release, mandatory from 2026-08-17; OpenPeppol tagged no 3.0.21, so its recipe fetches below
  the commit `806866bd2bd91d7e9623b68f08164e8fbe9e67a0`, never by a branch name, and a recipe
  without a tag may fetch only below the commit it names. `esj packs fetch peppol-bis-billing`
  follows the newest recipe this build carries (3.0.21); `esj packs list` marks it. None of those
  files is in this repository or in any artefact; `esj validate` on a Peppol invoice without the
  pack names the command that makes it. With 3.0.21, OpenPeppol's 10 examples validate `VALID`
  and all 62 rules its own unit tests cover fire as those tests expect; with 3.0.20, 9 examples
  and 58 rules (`conformance/peppol/README.md`, run with `-Desj.network=true` and in the JDK 21
  job of the continuous integration).

### Changed

- A recipe's `source.tag` is optional, with a new optional `source.branch`; the manifest of a
  fetched pack records them as the recipe has them, and `esj packs list` names the revision a
  recipe fetches by (`at tag v3.0.20`, `at commit … (branch 2026-Q2-QA2)`).

- A PDF written by 0.9.0 to 0.9.3 still carries `invoice.esj.json`. It is read as an attachment
  that is not the invoice, as a logo would be: `esj inspect` and `esj extract --list` list it
  (`not XML`, `application/json`, `Supplement`), `esj extract --attachment invoice.esj.json`
  hands out its bytes, and `esj validate` reports nothing about it. A PDF whose one attachment it
  is carries no invoice (exit code 2).
- `docs/java-api.md` names Saxon-HE 13.0 as the minimum: with 12.x the XML Schema, Schematron
  and XSLT path still run, and every HTML page of `esj-render`, the HTML report included, fails
  with `AbstractMethodError` (`UnparsedTextURIResolver`). `docs/install.md` notes that the jar
  carries Saxon-HE 13.0 unrelocated.

### Removed

- The ESJ document inside the hybrid PDF. `esj embed` and `esj render --embed cii` write one
  embedded file, the invoice XML. Gone with the second file `invoice.esj.json`: the rule that it
  and the XML are two accounts of one invoice, the option `--no-esj` and the line saying the
  attachment went in, the container row `ESJ document attached` of `esj validate` (lines, the
  JSON member `container.esj`, the HTML and the PDF report), the findings `PDF-ESJ-DISAGREES`,
  `PDF-ESJ-UNSOUND`, `PDF-ESJ-UNREADABLE`, `PDF-ESJ-UNCHECKED`, `PDF-EMBEDDED-SEVERAL-ESJ` and
  `PDF-EMBEDDED-ESJ-LABEL`, and section 15 of `SPEC.md` (informative). A receiver of two
  representations of one invoice has to parse and compare both; the XML is the invoice, and the
  ESJ document derives from it at any time ([`docs/design-decisions.md`](docs/design-decisions.md)).
- The API of that feature, which breaks callers of it: in `esj-pdf` the classes `EsjAgreement`
  and `EsjAttachment`, `EmbedOptions.withEsj` and the `esj` component of `EmbedOptions` (its
  constructors take one argument fewer), `EmbedResult.EsjOutcome`, `EmbedResult.esj()`,
  `esjAttached()` and `esjOmitted()` (its constructor takes the file and the report),
  `AttachmentKind.ESJ_DOCUMENT`, `ContainerFinding.Category.PDF_ESJ` and the three-argument
  `ContainerChecks.run`; in `esj-core` the phrases `Phrase.ROW_PDF_ESJ`, `ESJ_ATTACHMENT` and
  `ESJ_ATTACHMENT_PARTLY`.

### Fixed

- UBL Credit Notes keep the sub lines of the XRechnung extension. The extension's source binds
  them for UBL Invoice only, so the streaming reader dropped every `cac:SubCreditNoteLine`
  without an observation, and the writer had no place for BG-DEX-01. `ubl-creditnote.json` now
  binds BG-DEX-01 to BG-DEX-08 and the 36 core terms a sub line reuses below
  `cac:CreditNoteLine//cac:SubCreditNoteLine`, quantity from `cbc:CreditedQuantity` (two
  corrections, `model/bindings/README.md`). BG-DEX-09 and BT-DEX-001 to BT-DEX-003 stay unbound:
  UBL 2.1 gives the credit note no `cac:PrepaidPayment`. Measured on
  `conformance/creditnote/04.01a-CREDITNOTE_ubl.xml`, the KoSIT extension instance 04.01a as a
  credit note: read, written, read again and accepted by the pack, with the values of the
  invoice. The XSLT path (`--importer xslt`) still gives every sub credit note line the values
  of the first credit note line, a defect of the vendored KoSIT stylesheet
  (`conformance/creditnote/README.md`).
- `esj-bom` no longer hands on the versions the build of this project manages. The deployed POM
  is flattened: no parent, no properties, and only the modules of this project in
  `dependencyManagement`. Importing 0.9.0 to 0.9.3 also imported JUnit 6.1.3, Saxon-HE, PDFBox,
  Jackson and the PostgreSQL test binaries, and so decided the JUnit version of the importing
  build.

## [0.9.3] — 2026-09-29

### Changed

- The German display names of four invoice type codes (UNTDID 1001) in the letter layout now
  read as a German document writes them: 386 `Vorauszahlungsrechnung` (was
  `Abschlagsrechnung`), 875 `Abschlagsrechnung` (was `Bauabschlagsrechnung`), 876
  `Teilschlussrechnung` (was `Teilschlussrechnung Bau`), 877 `Schlussrechnung` (was
  `Schlussrechnung Bau`). 875–877 are the terms of § 16 VOB/B and § 14 (5) UStG, written
  without the trade qualifier; 386 is the invoice before the supply, whereas a partial
  payment invoice (875) presupposes a supplied part. The English names are unchanged.

### Fixed

- The deployment to Maven Central signs again under `actions/setup-java` v5 and later, which
  import the signing key into an isolated GPG home: the workflow allows the loopback pinentry in
  that home and signs a probe with the passphrase before the deployment, so a wrong secret is
  named by the probe and not by the Portal (the first deployment of 0.9.2 ended in "signing
  failed: No pinentry" and was repeated from `main`).

## [0.9.2] — 2026-09-26

### Added

- The business rule pack of EN 16931-1:2026, `rules/en16931-2026/0.1`: 252 rules, of which 199
  are those of clause 6.4 (194 implemented, 5 not decidable, each with its reason), the fraction
  digit rules of Table 28 and the code list rules. No official validation artefact is published
  for the edition, so every rule names in `oracle` what stands behind it — `downgrade` for the
  rules the edition leaves unchanged, measured against the Schematron of release 1.3.16 over the
  document written back with `esj upgrade`, `cases` for the rules it changed or adds — and a
  document of that edition validates `INVALID` or `INDETERMINATE`, never `VALID`, with the new
  cause `no-artefacts-for-edition` ([`conformance/rules-2026/coverage.md`](conformance/rules-2026/coverage.md),
  [`ledger.md`](conformance/rules-2026/ledger.md)). The pack is separable with the edition.
- `derive()` for the typed view of EN 16931-1:2026: `…typed.v2026.Totals.of(minorUnits)` writes
  the line net amounts, one VAT breakdown per category, rate, exemption reason and goods/services
  code, and the totals, with the third party charges (BG-34) added into the amount due.
  Each amount is rounded once, half up, to the minor unit of its currency; `MinorUnits` of
  `esj-core` holds those numbers and `En16931V2026.minorUnits()` reads them from the pack's
  currency snapshot. A currency the snapshot gives no minor unit is refused, never guessed; a
  rule of the pack that asks for one reports that it was not decided. The
  pack writes out `BR-CO-10`, `-11`, `-12` and `-15`, which round to the same minor unit.
- The rule language has three more operators — `minorUnit`, `unit` and `atRoot` — and the scale
  of `round` and of `decimals` may be an expression; `decimals` applies to every numeric term.
  A rule may name in `undecided` a case a document may state and the rule has no answer for;
  there it reports that it was not decided, at `info`, instead of holding silently — the 2026
  pack does so for a price base quantity of zero in `BR-CO-32` and `BR-67`.
  A pack manifest names its `edition`, which the engine checks against the registry, may take
  rules of another pack over by identifier (`shares`) and read a code list snapshot of another
  pack (`{"day", "from"}`). The TypeScript and C# bindings read all of it and run the 2026 pack;
  the fixture manifest carries its cases in the part of that edition.
- `esj validate` runs the pack of the edition a document names. The row, the JSON report
  (`rules.pack.edition`, `rules.pack.corroborated`) and the report file name the pack and, for
  the 2026 one, say it is not corroborated by an official artefact. `esj --list-packs` lists
  the rule packs with their edition and the count of rules per oracle. `esj upgrade --to 2026`
  evaluates a decimal bound over the minor unit of the currency a value is written in — BT-5,
  BT-6 for BT-111, BT-184 in a VAT breakdown — against the currency snapshot of that pack
  (`UpgradeOptions.minorUnits`) and reports a value beyond it, unrounded.
- The container image is published with every release at `ghcr.io/bsnsoft/esj`, for linux/amd64
  and linux/arm64, tagged with the version and `latest`
  ([`docs/install.md`](docs/install.md#container-image)).
- `esj convert --to cii|ubl --fail-on-loss`: where the target syntax has no place for part of the
  document, nothing is written — neither to `--out` nor to the standard output — and the run
  leaves with exit code 8. The exit code table did not grow: 8 was reserved for exactly this
  case and is now used, and every printing of the table says so. A value written by convention
  and a term left behind by design are not losses. Without the option a loss is warned about and
  the exit code stays 0 ([`docs/cli.md`](docs/cli.md#writing-ubl-and-cii)).
- The CI runs the TypeScript and C# bindings on every push, in the job `bindings`: `npm ci` and
  `npm test`, `dotnet test`, and the fixture runner over each binding.
- The fixture manifest pins division in the rule language for every implementation: the section
  `arithmetic` runs the pack `conformance/fixtures/arithmetic/pack.json`, whose quotients are
  worked out by hand, and a `rules` request of the runner may name that pack. The C# binding
  reads a pack from a stream with `RulePacks.Read`.

### Changed

- Every rule of a pack, and every entry of `javaRules`, which is now an object `{class, oracle}`,
  names its `oracle`; the rules of `en16931/1.3.16` say `artefact`. `verifiedAgainst` is optional.
  A pack manifest written for 0.9.x — without `edition`, with a rule that names no `oracle`, or
  with a `javaRules` entry written as a string — is refused by `RulePacks.read`.
  `no-pack-for-edition` now means only that this build carries no pack for the edition.
- The writer's report tells a term left behind by design from a loss. Handed the extension
  registries of a document (`WriterOptions.builder().extensions(...)`, and `esj convert` and
  `esj validate` hand it the ones `--extension` loads), the writer notes a value of a term whose
  registry declares `"transport": "none"` — the B2C extension's — as `TERM_BY_DESIGN` with the
  registry in `WriteNote.registry()`, and counts it neither as written nor as dropped;
  `WriteReport.byDesign()` gathers those terms by registry. `esj convert --extension b2c` names
  them on one information line instead of warning that ten values were not written, and the
  JSON report carries the registry beside each such note. `esj validate` reaches the same rows
  and exit codes as before.
- The letter layout is the default of `esj render` and of `PdfRenderer`
  (`RenderOptions.DEFAULT_LAYOUT`), for a template that names none as well; `--layout generic`,
  `Layout.GENERIC` or `"layout": "generic"` ask for the generic layout, in which the PDF
  validation report still draws the invoice it carries.
- Letter layout: a page after the first begins as far under the rule of its compact head as the
  first section of page one begins under the rule of the title, whatever opens the page.
- Letter layout: the seller's details in the foot of page one are written value first — an
  identifier followed by the code of its scheme, or by a short word where the document states
  none (`HRB 12345 (Registernummer)`), and the contact point without a label.
- Letter layout: the preceding invoices of BG-3 stand under the title, up to three, with their
  dates; a credit note (the types the display names call one) labels its number, its date and
  its closing amount as a credit note's; neither a credit note nor a self-billed invoice carries
  an EPC QR code, since its reader is not the one who pays ([`docs/letter-layout.md`](docs/letter-layout.md#document-types)).
- The libraries underneath moved on: Saxon-HE 13.0 runs the validation artefacts and the
  stylesheets, where 12.10 did, the tests run on JUnit 6, and the PostgreSQL that
  [`docs/storage.md`](docs/storage.md) is executed against is release 18 on every platform the
  build runs on, pinned through the bill of materials of its binaries rather than left to the
  default of the artefact that starts it. Nothing observable changed: the ledgers, the fixture
  manifest and the checked-in renderings hold as they are.

### Fixed

- Division in the TypeScript binding is exact where the quotient terminates, as `rules/README.md`
  says: `Decimal.divide` computed every quotient to 34 fraction digits and stripped the zeros, so
  a terminating quotient that needs more digits was rounded — `1E-35 / 1` came out as `0`. The
  Java and C# implementations were measured and already divided exactly.
- A row of a table that fits on a page no longer sends the lines hanging under it to the next
  page under a carry-over line: the room for that line is asked for only where the lines go over.
- The footer of the PDF validation report no longer runs into the page count for a long input
  path: the path is shortened in its middle, keeping its beginning and its file name.
- A hybrid PDF with two embedded files under one name is refused instead of validated on the one
  a map kept: both are listed, the name is reported (`PDF-EMBEDDED-DUPLICATE-NAME`), and files a
  page refers to count as attachments too ([`docs/pdf-input.md`](docs/pdf-input.md#where-the-invoice-may-lie-and-what-counts-as-one)).
- The output intent of a rendering carries the vendored sRGB profile byte for byte on every
  runtime. PDFBox was handed the profile as a stream, read it into a `java.awt.color.ICC_Profile`
  and embedded what that object gave back, and on a runtime whose colour management
  re-serializes a profile it was handed — the Ubuntu build of OpenJDK 21, for one — the header
  of the embedded profile named that engine as the preferred CMM, so the file was no longer the
  ICC's and the rendering no longer the checked-in one. The stream is now filled from the file
  itself; the checked-in renderings are unchanged.
- The checksum beside the platform-independent archive of a release, `esj-<version>.zip.sha256`,
  is the checksum of that archive. Each of the three packaging jobs of the release workflow
  uploaded the archive and its checksum under the same two names, and the release kept whichever
  copy of each came last — for 0.9.1 the archive of one job and the checksum of another, which
  does not match it; the six platform archives match theirs. The `linux-x64` job alone delivers
  it now, its entries carry fixed times, modes and order, so that the clock, the time zone and the
  umask of a build leave no trace in its bytes, and nothing is attached before every checksum has
  been checked against the archive beside it, no name comes from two jobs with different
  contents, and a tag finds the archive of the version it names.
- `esj embed` and `esj render --embed cii` hand the writer of the attachment the extension
  registries `--extension` loads, as `esj convert` does: a term whose registry declares
  `"transport": "none"` is named on one information line instead of being warned about as a value
  that was not written. `EmbedOptions.withExtensions(...)` hands them over in Java; the hybrid
  file is the same file.
- `conformance/fixtures/manifest.schema.json` describes the rule case file as it is written: it
  required a `part` of every file, and the rule case file names its pack, the directory of that
  pack and its cases instead. `FixtureManifestTest` validates every file of the manifest against
  the schema.
- `dist/package.sh --out <directory>` takes a relative directory: `zip` left with exit code 15,
  and `linux-native` and `smoke` looked for it under the repository rather than under the
  working directory.

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
