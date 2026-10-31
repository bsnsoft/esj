# Changelog

The format is [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html). A change to the format itself is
named here under *Format*.

## [1.0.0] — unreleased

### Added

- The bundled validation pack `xrechnung/3.0.2/2026-10-12`: release 1.3.17 of the EN 16931
  Schematron of CEN/TC 434, published on 2026-10-12 and in effect from the end of 2026-11-15,
  with the XRechnung Schematron, the schema modules and the level tables of the KoSIT
  configuration of 2026-08-31, which no configuration release has replaced yet. It is the pack a
  document is judged by; `xrechnung/3.0.2/2026-08-31`, with release 1.3.16, stays bundled and is
  chosen with `--pack xrechnung/3.0.2/2026-08-31`
  ([`docs/validation.md`](docs/validation.md#the-packs), [`packs/SOURCES.md`](packs/SOURCES.md)).
- The rule pack `en16931/1.3.17`, measured against release 1.3.17, with code list snapshots of
  2026-10-18: the code list workbook version 18, the EAS list version 17 and the VATEX list
  version 9 of the European Commission — among them the ISO/IEC 6523 schemes `0249` to `0251`, the
  electronic address scheme `9960` and eighteen `VATEX-ES-*` codes — and ISO 4217, ISO 639 and the
  media types from their publishers. The generated enums of `esj-invoice` and the display names
  of `esj-render` follow it; the pack `en16931/1.3.16` stays in the tree
  ([`rules/en16931/1.3.17/codelists/SOURCES.md`](rules/en16931/1.3.17/codelists/SOURCES.md)).

### Changed

- The Linux x64 executable and the amd64 container image are built, and the x64 packages tested,
  on GitHub's `ubuntu-24.04` runner, as the arm64 ones already were; `ubuntu-latest` moves to
  Ubuntu 26 on 2026-10-19, and an executable built there could need a glibc above the 2.34 the
  packages declare.
- `esj validate` runs the rule pack `en16931/1.3.17`. The rules of the VAT categories `L` (IGIC)
  and `M` (IPSI) carry the identifiers the standard gives them, `BR-IG-01` to `-10` and `BR-IP-01`
  to `-10`, where release 1.3.16 and the pack `en16931/1.3.16` say `BR-AF-*` and `BR-AG-*`; a
  finding of those rules is reported under the new identifier. The pack `en16931-2026/0.1` takes
  its unchanged rules and its shared code lists from `en16931/1.3.17`.
- The ledger of the rule pack is taken against release 1.3.17, over 455 mutations: 137 rules
  agree on every shape measured, 53 on one shape and not another, 25 differ, 2 cannot be asked,
  0 are open. Three causes are new and come from the release, and the pack follows the standard
  in each: the CII artefact binds `BR-IG-*` to the category `M` and `BR-IP-*` to `L` and asks for
  a rate above zero in `L`; both artefacts dropped `EL` from the prefixes `BR-CO-09` admits, so a
  valid invoice with a Greek VAT identifier is invalid against release 1.3.17; the UBL artefact
  reports `BR-CO-09` beside `UBL-SR-53` on a VAT tax scheme that states no identifier
  ([`conformance/rules/ledger.md`](conformance/rules/ledger.md)).
- With release 1.3.17 the official artefacts answer some documents differently: `CII-SR-475` and
  `CII-SR-476` count the names and attachments of each supporting document rather than of all of
  them and no longer fire on three instances of the corpus; `UBL-SR-57` to `-59`, `CII-SR-495` to
  `-497`, `CII-DT-105` and `CII-DT-106` are new and `CII-SR-471` is withdrawn; the currency list
  accepts `STN` where it accepted `STD`. The syntax ledger was taken again with the official
  validator, its EN 16931 stylesheets replaced by those of 1.3.17
  ([`conformance/syntax/ledger.md`](conformance/syntax/ledger.md)).
- Saxon-HE 13.1 runs the validation artefacts and the stylesheets, where 13.0 did, and Jackson
  2.22.4 reads the JSON files esj reads with it; the goldens of the tests did not move.

## [1.0.0-rc.4] — 2026-10-18

### Fixed

- `ESJ Viewer.app` is built on the `macos-15` runner again: the script that turns the compiled
  AppleScript into the viewer removed a property-list key the applet template of that macOS does not
  write, and stopped. Every key it sets or removes is now handled whichever keys the template
  carries. 1.0.0-rc.3 has no release for that reason; its tag exists, nothing of it was published.

## [1.0.0-rc.3] — 2026-10-18

### Added

- `esj view <file>` shows an invoice in the browser: one page in a directory of its own, with
  the facts of the invoice and the verdict at the top and a tab each for the PDF as it came, the
  letter `esj render` draws from its data, the KoSIT rendering and, with `--report`, the
  validation report, kept beside the page as an HTML file and a PDF; every file is the bytes its
  own command writes. Every outcome is a page — a PDF without an invoice, several candidates (a
  table with the command that opens each), ZUGFeRD 1.0, an encrypted PDF, a file that is not
  read, a limit — saying what the file is and what to ask the sender for, with the exit code
  `render` leaves with, or with `--report` `validate`. The page runs no script of its own (the
  tabs are CSS), fetches nothing and says so in a content security policy. `--out <dir>`,
  `--lang` (the default from the locale), `--template`, `--no-payment-code`, `--output json`;
  `--text` prints the invoice in the terminal instead, and with `--report` the lines of
  `esj validate` under it ([`docs/view.md`](docs/view.md)).
- `esj render --text` writes the invoice as plain text for a terminal: the facts and names of the
  letter layout top to bottom, the lines as a table, UTF-8 without colour or box drawing, in
  `--width` columns (default `COLUMNS` where it is 60 or more, otherwise 100), no line longer and
  nothing cut ([`docs/cli.md`](docs/cli.md#the-invoice-as-text)).
- The deb and the rpm install a desktop entry, *ESJ Viewer*, that offers `esj view --report` under
  *Open with* for XML and PDF files, with its icon; the macOS archive carries `ESJ Viewer.app`
  beside the executable, an alternate viewer of the same two types, signed ad hoc;
  [`docs/install.md`](docs/install.md#esj-view-from-the-file-manager) gives the line that does it
  on Windows. None of them makes itself the default program of a type.

### Changed

- The macOS executable and its archives are built on GitHub's `macos-15` runner; `macos-14`, which
  built them until 1.0.0-rc.2, is being retired by GitHub. The pages and the Homebrew formula name
  the release candidate as the version to use.
- A release candidate is published like a release: the image tag `latest` follows every tag, and
  the apt and dnf repositories have one suite, `stable`, with every version, a candidate as
  `1.2.3~rc.1`, which sorts before `1.2.3`. The suite `testing` and `rpm/esj-testing.repo` are
  gone (a source line or `.repo` file added for them is to be removed); the next run of
  `dist/repositories.sh` moves the rpm packages of `testing` into `stable`. `release.yml` started
  with `publish_version` sets `latest` and regenerates the repositories for a version already
  released, and builds nothing ([`docs/releasing.md`](docs/releasing.md#publishing-a-version-again)).

### Fixed

- The native executables — the release archives, the Homebrew formula, the `.deb` and `.rpm`
  packages — ended with exit code 5 and `internal error: java.util.MissingResourceException:
  Could not load any resource bundle by com.sun.org.apache.xerces.internal.impl.msg.XMLMessages`
  on every XML document that is not well-formed — cut short, with a wrong end tag, an undeclared
  entity, a control character or markup after the root element — in every command that reads
  one, and on a hybrid PDF whose attachment or XMP packet is not well-formed; and in
  `esj validate` on every document its schema refuses (`…XMLSchemaMessages`). The jar answers
  exit code 2, `the document is not well-formed XML that this reader accepts`, or reports the
  finding with exit code 1, and so do they now. The parser of the Java runtime formats each error
  from a resource bundle even where the tool prints a sentence of its own, and no case of
  `dist/cases.sh` had fed it such a document, so the GraalVM agent never recorded the two
  bundles: every native executable released up to 1.0.0-rc.2 lacks them. The jar, the runtime
  image and the container image were not affected. The cases `input-*` now take every kind of
  input a reader refuses or reads the hard way, from [`dist/inputs/`](dist/inputs/README.md),
  through `validate`, `inspect`, `convert` and `render --html`, so the agent records what they
  reach and the smoke test compares every packaged artefact's answers with the jar's.
- The native executables refused an invoice in Windows-1252, ISO-8859-15, Shift_JIS or any other
  encoding beyond UTF-8, UTF-16, ISO-8859-1 and US-ASCII with exit code 2 (`the document is
  written in, or declares, the encoding …, which this reader does not read`), and the repair of
  a document declared UTF-8 whose bytes are Windows-1252, both of which the jar reads: an image
  carries only those charsets unless it is built with `-H:+AddAllCharsets`, which it now is. The
  runtime image refused the encodings of the module `jdk.charsets`, Shift_JIS among them, and
  now carries it. Cases of `dist/inputs/` in each of these encodings keep both answering as the
  jar does.
- The Linux packages of a pre-release are attached to the GitHub release with a dot in place of
  the `~` of their version (`esj_1.2.3.rc.1_amd64.deb`), which is the name GitHub stores them
  under anyway, and their checksum files name them so: those of 1.0.0-rc.2 name
  `esj_1.0.0~rc.2_amd64.deb`, which a download does not have, so `sha256sum -c` fails on them
  (compare the digest by hand). The packages in the apt and dnf repositories keep the `~`, and a
  release, which has none, is attached as before. The release job also publishes a pre-release
  only when GitHub lists every asset under the name it was delivered with
  ([`docs/install.md`](docs/install.md#a-package-without-the-repository)).

## [1.0.0-rc.2] — 2026-10-16

### Fixed

- `release.yml` creates a pre-release as a draft, attaches the assets and publishes it then:
  releases are immutable once published, so assets can be attached only before publication, and
  the release action publishes a pre-release before it uploads them. The tag `v1.0.0-rc.1` was
  published that way, got no assets, and is on no other channel; its release is deleted, and
  1.0.0-rc.2 is the same software with this fix ([`docs/releasing.md`](docs/releasing.md#pre-releases)).

## [1.0.0-rc.1] — 2026-10-16

### Format

- The format is 1.0, and the specification is `SPEC.md` version 1.0, a release candidate: a
  document written now carries `"version": "1.0"`. Nothing else of the format changed — the path
  grammar, the value model, the canonical form, the digests, the finding codes, the registries
  and the limits are those of 0.1 (`SPEC.md` appendix D).
- A reader of 1.0 reads documents of version `1.0` and of `0.1`, the release candidate, both by
  the rules of 1.0, and refuses every other value with `ESJ-L1-ENVELOPE-VALUE`, whose message
  names the two; in general a reader of 1.*x* reads 1.*y* for *y* ≤ *x* and 0.1 (section 4.3).
- A document keeps the version it was read in. The canonical and the pretty form write it, so a
  stored 0.1 document keeps its canonical bytes and both digests, and a document derived from one
  — edited, given derived totals, written up to another edition and back — stays 0.1. A document
  built from nothing, by the domain API or the typed builder, or imported from UBL, CII or PDF
  carries 1.0. The same values as 0.1 and as 1.0 share the semantic digest and differ in the
  document digest (sections 7.3 and 8.3); no command rewrites a 0.1 document as 1.0.
- `schema/esj.schema.json` takes `version` from `["1.0", "0.1"]`, and the schema identifiers move
  from `tag:bsnsoft.de,2026:esj/schema/0.1/…` to `…/schema/1.0/…`; the generated schemata of the
  two editions follow.
- The fixture manifest records documents of version 0.1 with the canonical bytes they had in a
  new section `earlierVersions` (`conformance/fixtures/version-0.1/`), a case of the canonical
  order whose bytes carry 0.1, and rejected documents with the versions `2.0`, `1` and `1.1`.
  Every other document of the repository — examples, corpus, fixtures, appendix B — is written in
  1.0, and its document digest moved with it; no semantic digest did.

### Added

- `Esj.acceptedFormatVersions()`, `SemanticDocument.formatVersion()` and
  `SemanticDocument.Builder.formatVersion(String)`, which accepts only a version this
  implementation reads. `Esj.formatVersion()` answers `1.0`. Two documents that differ in their
  format version alone are not `equals`, as their document digests differ.

### Changed

- The validation report says under its verdict, in German and in English, that the verdict is
  technical conformance with EN 16931 and that the German VAT classification is a separate question
  (BMF letter of 15 October 2026, Rn. 22 f.). The documentation is checked against that letter, the
  third on mandatory e-invoicing in Germany; nothing in the format, the registries, the bindings or
  the rule packs had to change. [`docs/legal-de.md`](docs/legal-de.md) maps it paragraph by paragraph
  to what ESJ offers, [`docs/validation.md`](docs/validation.md) separates the verdict from the VAT
  view, [`docs/b2c.md`](docs/b2c.md) covers prices agreed gross between businesses as well, and both
  FAQ pages answer for a final invoice after partial invoices, construction work with a bill of
  quantities and a correction.

## [0.9.8] — 2026-10-14

### Added

- Linux packages and package repositories: the native executable as `esj_<version>_<arch>.deb`
  and `esj-<version>-1.<arch>.rpm` for x86_64 and arm64, built with nfpm (`dist/packages.sh`,
  nfpm 2.47.0 pinned by SHA-256 in `dist/nfpm.sh`), attached to the release with their checksums
  and build provenance attestations, and published in an apt repository and a dnf repository at
  `https://apt.bsnsoft.de`, served by GitHub Pages from the branch `gh-pages`:
  `apt-get install esj` and `dnf install esj` after adding a keyring and a source line or a
  `.repo` file ([`docs/install.md`](docs/install.md#debian-and-ubuntu-apt)). The executable is
  installed under `/usr/lib/esj` beside its shared libraries, with `/usr/bin/esj` a link to it;
  the packages require glibc 2.34 and zlib and nothing else, measured on `debian:12-slim`,
  `ubuntu:22.04`, `ubuntu:24.04`, `fedora:latest` and `rockylinux:9` (`dist/packages-smoke.sh`).
  A release goes to the suite `stable`, a pre-release to `testing` as `1.2.3~rc.1`, which sorts
  before `1.2.3`. The indices are signed with the release key, the key the artefacts on Maven
  Central are signed with, inline and detached (`InRelease`, `Release.gpg`, `repomd.xml.asc`),
  and so is every rpm; apt reads the indices by hash. `dist/repositories.sh` regenerates both
  repositories from the packages on the branch, which is their whole state; the job *Package
  repositories* of `release.yml` runs it on a tag, and `dist/repositories-test.sh` puts both
  through apt and dnf end to end with a throwaway key and the commands of the install page, in
  the CI and before a release attaches anything.
- `dist/checks.sh` fails a native Linux executable, or a library beside it, that needs a glibc
  symbol version newer than `ESJ_GLIBC_FLOOR` (2.34), the floor the packages require; the native
  build and `dist/packages.sh` run the check.

### Fixed

- `esj serve` kept the connection of an early refusal alive — an answer sent before the request
  body was read to its end — though it closes that connection where the lingering read of the
  rest ends at its time bound or at the bound of the door; a client that had already sent its
  next request on it lost that answer (`HTTP/1.1 header parser received no bytes`: once in 420
  requests on a CI runner, in a test run some 3.5 s longer than usual, past the test's 3 s
  bounds; every time behind a chunked body past the bound). Such an answer now says
  `Connection: close`, so the next request goes on a new connection; a request whose body was
  read to its end keeps its connection.
- The native executables ended `esj render --template` with exit 5 on a JPEG letterhead or logo
  (`NoSuchMethodError: …JPEGImageReader.readInputData`) and on every PNG that is not plain RGB or
  a palette — with an ICC profile or an `sRGB` chunk, in grey, interlaced or with an alpha channel
  (`sun/java2d/Disposer`, `CMMException`): no case had used such an image, so the native image
  lacked the JNI and resource metadata of the JPEG decoder, of the rasters and colour models of
  ImageIO and of the colour management of the Java runtime. Four template cases over
  `dist/artwork/` record it, and the smoke compares their renderings with the jar's.

## [0.9.7] — 2026-10-10

### Added

- `esj serve` (preview): the tools of the command line over HTTP — a REST API under `/api`, its
  OpenAPI 3.1 description at `/openapi.json`, and an MCP server over Streamable HTTP at `/mcp` —
  and `esj mcp` (preview), the same tools as an MCP server over the standard streams. Seven tools
  from one table: `validate`, `summary`, `get`, `convert`, `render`, `extract` and `inspect`, and
  `upload` over HTTP. The server reads no document itself: every call runs `esj` as a child process
  with `--job-heap`, `--job-timeout` and an environment of its own, at most `--max-jobs` at once.
  `POST /api/validate` answers with the report of `esj validate --output json`, byte for byte.
  Loopback by default and `0.0.0.0` in both container images (`ESJ_SERVE_BIND`, port 8080
  exposed), a bearer token from `--token-file`, `Origin` checked, `path` only below
  `--allow-dir`, uploads and artefacts kept for `--ttl` in one temporary directory that `SIGTERM`
  removes. The server holds no document and no answer whole: it reads what a child wrote from
  its file, keeps what a result carries and writes the answer into a spool before the call gives
  back its child's place, so a call costs its heap at most 6 MiB whatever `--limits` lets a
  child read and a client that reads slowly costs none of it, and it starts only with the
  `--max-jobs` its heap holds (`ESJ_SERVE_HEAP` in the container image, beside `ESJ_MAX_HEAP`
  for the children); an MCP batch carries at most 16 messages and one `tools/call`. Links are
  built from `--public-url` or a `Host` the server answers to (`--allow-host`) and are paths
  otherwise; a file with a second name below `--allow-dir` is refused; the texts a model reads
  escape every invisible character; `notifications/cancelled` ends the child of its call.
  Everything the server holds on disk — request bodies and uploads as they are read, artefacts,
  answers that wait for their client, and 64 MiB for each running child from its start, the
  most it may write — is held to `--max-disk` (448M, within the tmpfs of `dist/compose.yaml`;
  `--max-stored-bytes` 256M within it): a call that does not fit is refused with 503 and
  `Retry-After`, never cut, and a call whose child the server ends as it stops is told so with
  503, not 502. A request refused before its body was read is answered first, and the rest of
  its body is read and dropped, for at most 5 s, before the connection is closed, so that the
  client reads the answer and not a TCP reset. `dist/compose.yaml` runs it as the service
  `esj-api`; `dist/serve-smoke.sh` holds every packaged artefact's server against the jar's.
  [`docs/serve.md`](docs/serve.md).

### Fixed

- The image stage of `dist/Dockerfile.native` carries the runtime libraries beside the executable,
  without which a rendering in it failed to load the AWT library.

## [0.9.6] — 2026-10-08

The Java implementation and the TypeScript and C# bindings now read, report and load registries
alike. An entry without a prefix holds for all three; a prefix names the implementation it is
about.

### Added

- The fixture manifest records the whole answer of a validation (contract version 2): the status,
  the layers not evaluated with their reasons, and every finding with its path, code, subject —
  the empty one included — and severity, information findings such as `ESJ-L2-NOT-CHECKED` and
  `ESJ-L2-EDITION-UNKNOWN` among them. `run.py` compares the findings of the reader and those of
  one path at layer L2 in order, as `SPEC.md` sections 9.6 and 9.2 fix it, and the rest as a set,
  and a rejected document by its whole list of findings rather than its first.
- Fixture manifest: a section `bounds` reads documents under bounds below the defaults, each at a
  bound and one past it; a section `registryChecks` asks a loader to read and combine registry
  files and records whether it takes them; the grammar tables cover the edition grammar of
  `semanticModel` and the owner token, and more decimal, date, time and base64 candidates; a
  rejected document the reader reads carries its digests; a canonical order case carries its
  document digest and its number of values. A `validate` request may name the registries to
  validate with (`registries`), which the fixture bindings of TypeScript and C# answer.
- Fixtures: 13 documents in `examples/invalid/` (the encoding, the JSON text, a missing envelope
  member, a wrong version, the edition grammar, an undefined member of `source`, a duplicate in
  the envelope and below an owner token, a parent chain, a missing index, a missing group) and,
  under `conformance/fixtures/`, 64 variants layer L1 refuses, 10 documents whose findings at
  layers L2 and L3 are pinned as a list, 4 documents a reader reads for what a validator says of
  them, 69 cases under bounds, 30 sets of registries, 4 documents not in canonical form, and the
  two documents of `SPEC.md` appendix B with their canonical bytes. A test holds the bytes,
  lengths and digests the appendix prints to those files and to the manifest.

### Changed

- Java: `EsjReader` reads the bytes with a scanner of its own instead of `jackson-core`: it reads
  UTF-8 and nothing else, and it judges every member name before the value written under it. It is
  faster than before and holds less while it reads.
- The `subject` of a reader finding is a member access with one grammar: a name the
  specification defines at that place is dotted (`source.syntax`, `values["/BT-1"].scheme`),
  every name the document chose is written in brackets (`["profile"]`, `source["origin"]`,
  `values["/BT-1"]["note"]`, `extensions["a.b"]["x"][0]`), and the name is never cut to an
  excerpt. The bindings wrote such a name dotted (`profile`, `source.foo`, `values["/BT-1"].foo`).
- A duplicate member name is named by its own member access (`values["/BT-1"]`, `format`,
  `source.syntax`, `extensions["a.b"]["x"]`), not by the object it occurs in; inside a value
  object, a repeated name or a name with a lone surrogate names the value (`values["/BT-1"]`).
- Every missing required envelope member is a finding of its own, with the member's name as its
  subject, in the order `format`, `version`, `semanticModel`, `values` (was the first alone).
- Every member name is held to `maxStringBytes`, counted in UTF-8 bytes (the bindings counted
  UTF-16 code units), also below a value the reader walks past, and so are `format`, `version`,
  `semanticModel` and `source.sha256` as `source.syntax` was: past the bound each is
  `ESJ-L1-LIMIT` rather than the code of its grammar (`ESJ-L1-OWNER-TOKEN`, `ESJ-L1-PATH-SYNTAX`,
  `ESJ-L1-ENVELOPE-MEMBER`), which still applies inside the bound. A bound shorter than the
  envelope's own strings refuses every document; the edition string of 2017 is 30 bytes long.
- A number token longer than `maxStringBytes` is `ESJ-L1-LIMIT` wherever it stands, measured
  before it is built; in `values` it was `ESJ-L1-JSON-TYPE` when written with a fraction.
- `ESJ-L1-LIMIT` carries the path of the member of `values` it was met in and, as its subject,
  the member whose name or value reached the bound — `values["/BT-1"]`, `values["/BT-1"].value`,
  `extensions["o"][1]`; for a member name past the string bound, the object the name stands in —
  also for a bound the parser used to apply, which named nothing. Inside a
  structure the reader walks past, that is the member walked past; names, numbers and the depth
  are bounded there, strings are not.
- A message that places a defect in the byte sequence names the byte offset at which the token
  begins, counted from zero, rather than where the parser stopped (Java) or a UTF-16 index (the
  bindings).
- `ESJ-L1-JSON` is a finding about the document: its path and subject are empty, its message
  names the byte offset. A token where a value belongs that is no complete JSON value — `tru`,
  `truex`, `01`, `1.` — is `ESJ-L1-JSON`, before anything is said about its type, also where the
  envelope expects another type (the bindings said `ESJ-L1-ENVELOPE-VALUE` there).
- An exception carries the path and the subject of the finding it stands for beside its code:
  `path()` and `subject()` of `EsjFormatException` and `EsjLimitException` in Java; `Path` and
  `Subject` in C#, where `EsjLimitException.Code` is now an instance property and
  `EsjLimitException.LimitCode` the constant; `subject` of `EsjError` in TypeScript, whose `path`
  is the empty string where the finding names none (was `undefined`).
- `esj validate --output json` writes `subject` as a string in every finding, `""` where it
  names nothing beyond the path, and never `null`.
- Layer L2 checks every path independently (`SPEC.md` 9.2): a core identifier the registry does not
  contain is `ESJ-L2-UNKNOWN-TERM` once per segment, also beside an extension segment no loaded
  registry defines, which made the whole path `ESJ-L2-NOT-CHECKED` and the result `INDETERMINATE`
  before; the segments above an undefined one are held to the index rule; an unknown identifier of
  a loaded extension namespace stays `ESJ-L2-NOT-CHECKED`, and its message no longer says that the
  registry is not loaded. Of a path whose terms are all known, the index rule, the chain, the
  content and the components are checked and every failure is reported (TypeScript reported the
  first alone), and the findings of a path stand in the order of its segments.
- The findings of layer L2 name in `subject` what the path cannot tell apart: the identifier of the
  segment for `ESJ-L2-UNKNOWN-TERM`, `-INDEX-REQUIRED` and `-INDEX-FORBIDDEN`, the term whose group
  chain is wrong for `ESJ-L2-PARENT-CHAIN`, the component for `ESJ-L2-COMPONENT-NOT-ALLOWED` and
  `-COMPONENT-MISSING`. The subject was empty in Java and C#.
- Java and C#: `ValidationResult.merge` (`Merge`) keeps `NOT-REQUESTED` for a layer the caller did
  not ask for: a `LIMIT` or `PRECEDING-LAYER-FAILED` the other result names for it no longer
  displaces it (`SPEC.md` 9.5). `EDITION-UNKNOWN` of a validator that was asked for the layer is
  still kept over the `NOT-REQUESTED` of a clean read, which `Merge` of C# answered with
  `NOT-REQUESTED`; both now compose every pair of reasons by one table. C#:
  `Validator.Validate(byte[])` composes the read and the structural run with `Merge`, so an unknown
  edition reads `EDITION-UNKNOWN`.
- Java: `Registry.load` refuses a registry that names terms it does not define — as a parent, in a
  chain or in `reusesTerms` — and imports nothing, and reports a cardinality below zero or a path
  that does not end at its term as `EsjFormatException` rather than `IllegalArgumentException`.
  `Registry.withExtension` refuses such an extension where its `imports` do not name this model
  with this edition, which it combined silently before; its refusal of a redefined term no longer
  calls a term of an earlier extension a core term. `Registry.admits(extension)` asks the edition
  question before combining.
- TypeScript: `registryOf` and `new Structure` refuse a registry the way section 10 does —
  `RegistryError` for components no value can satisfy, an identifier listed twice or defined by
  core and extension (was: the later one won), and an extension combined with an edition it does
  not import, or naming core terms with no `imports` at all.
- `esj validate --extension` for a document of an edition the extension does not import says so:
  the `ESJ-L2-NOT-CHECKED` finding names the option, the edition the registry imports and that it
  is not this one, and the cause is the new `extension-for-another-edition` rather than
  `extension-registry-missing`.
- An extension registry defines identifiers of its own namespace only: one that defines an
  identifier without a namespace — `BT-999` as well as `BT-1` — or identifiers of two namespaces
  is refused when it is read, where it imports a core, and where it is combined with one.
  `model/registry.schema.json` states the same for a registry that carries `imports`.
- C#: `Registry.Load` refuses a registry that names an identifier it does not define and imports
  nothing, and `WithExtension` asks for the imports wherever an extension names an identifier it
  does not define, as Java and TypeScript do, not only one of the core.
- TypeScript and C#: a member name is judged before the colon after it, as in Java: `{"a" 1}` is
  `ESJ-L1-ENVELOPE-MEMBER` rather than `ESJ-L1-JSON`, and a name of `values` that is no path draws
  `ESJ-L1-PATH-SYNTAX` before the missing colon ends the read.
- A string past its bound is read on to its end for a lone surrogate, which comes first:
  TypeScript and C# refused a string of the envelope, of `source` or of `extensions` past the
  larger of the two string bounds, and a string of `values` past twice the string bound, for its
  length alone. Strings of the envelope, of `source`, of `extensions` and of `values` are held to
  the string bound while they are read, in TypeScript and C# as in Java.
- Inside a value object a string with a lone surrogate is held to no bound: it drew
  `ESJ-L1-LIMIT` beside `ESJ-L1-SURROGATE`, and in Java past the larger of the two string bounds
  `ESJ-L1-LIMIT` alone. TypeScript and C#: a member of a value object past every bound that could
  apply to it stops the reader before the object is judged, as in Java, rather than after its
  shape and member set.
- TypeScript and C#: the message of an `ESJ-L1-LIMIT` about the members of `values`, the length
  or the segments of a path, the members of a value object or the depth of `extensions` names the
  byte offset of its token, as Java's does; TypeScript's `ESJ-L1-JSON` for a document that is no
  JSON object names it too.
- Java: the generator escapes a slug Java reserves, or one that would hide a member every view
  carries, with a trailing underscore (`class_()`) instead of refusing the registry (`SPEC.md` 10).
- TypeScript: the view generator refuses a repeatable group whose slug is no plural (was: the slug
  named an instance as it stood).
- TypeScript and C#: the fixture binding answers `validate` with `status`, `notEvaluated` and each
  finding's `severity`, takes the bounds of section 12.2 in `limits` under the names of `Limits`,
  and answers `{"op": "registry", "files": [...]}` with `accepted`.
- TypeScript: `limitsOf` refuses a name that is no limit of section 12.2 and a bound that is not a
  positive whole number.

### Fixed

- Java: the reader read a document written in UTF-16 or UTF-32 without a byte order mark,
  because its parser guessed the encoding; such a byte sequence is valid UTF-8 and no JSON text
  in it, and is now `ESJ-L1-JSON`.
- Java: a defective member name followed by a broken value — `"foo":tru`, a second
  `"format":tru` — was `ESJ-L1-JSON`, because the parser read the value ahead; the name is judged
  first and draws its own code.
- A lone surrogate or a repeated name inside a structure the reader walks past under a defective
  member was reported, and in Java a lone surrogate there stopped the read; neither is a finding
  now, and the reader reads on.
- A member name with a lone surrogate carries its member access as subject (it was empty in
  Java), and a subject or message escapes every unpaired surrogate as `\u` and four lowercase
  hexadecimal digits (`\ud800`); the C# fixture protocol carries it so instead of a replacement
  character.
- The subject of an undefined envelope member was cut to 80 characters; a subject is whole.
- Java and C#: the decoded size of a base64 value counts at most two padding characters and is
  never negative, so a value that is no base64 cannot lower the total the bound on binary content
  sees.
- TypeScript and C#: a value built through the API (`documentOf`, `SemanticValue`, or a document
  assembled by hand) has its line endings normalized as a read one has, in the content and in
  every component, so both have one canonical form and one digest; `source` and `extensions` stay
  as written.
- C#: `format`, `version`, `semanticModel` and both members of `source` were held to the binary
  bound; they are held to the string bound, and a string of the envelope is screened for a lone
  surrogate before it is measured.
- C#: `Limits` refuses a `maxExtensionDepth` or a `maxDocumentBytes` past the bound the Java
  implementation refuses, instead of overflowing when a document arrives.
- TypeScript: `pretty` ends with one LF and writes a number inside `extensions` in its canonical
  form, as the Java and the C# writer do.
- TypeScript: `canonicalize` puts the values in canonical path order itself, leaves an empty
  `extensions` and an empty `source` out, writes `format` and `version` as the constants, and
  names the value of a string it cannot write.
- TypeScript: `validate(bytes, {layers})` without L1 no longer measures the model layers over a
  document the reader refused a member of: they are `PRECEDING-LAYER-FAILED`, or `LIMIT`.
- C#: reading a registry applies the three component rules of `SPEC.md` section 10, and
  `WithExtension` refuses an extension that names a term of the core without importing that
  core's edition.
- `publish.yml` deploys on Maven 3.9.16, downloaded and checked against Apache's digest: the
  runner image moved to Maven 3.10.0, with which central-publishing-maven-plugin 0.11.0 put the
  repository metadata of every artifact into the bundle, and the Portal refused the 0.9.5
  deployment. The release itself was not affected.

### Format

The format version stays 0.1, and the canonical bytes and both digests of every document a
reader accepts are unchanged. `SPEC.md` now says what an implementation does where it was silent
or contradicted itself; the section numbers are the same.

- Section 4.2: a reader detects no encoding. Bytes that are not UTF-8 (RFC 3629: an overlong
  form, an encoded surrogate, a code point past U+10FFFF) or begin with a byte order mark are
  `ESJ-L1-ENCODING`; UTF-8 that decodes to no JSON text — UTF-16 or UTF-32 without a byte order
  mark among it — is `ESJ-L1-JSON`.
- Section 9.6: the document size, a byte order mark and UTF-8 are decided over the whole byte
  sequence before any JSON is read, in that order, and a document refused for one of them draws
  that one finding.
- Section 6.8: CR LF and a lone CR become LF also when a value of `values` is built through an
  interface; strings outside `values` (envelope, `source`, `extensions`) are never normalized and
  are measured as they stand; a reader may accept a string early by its length before
  normalization, and never refuse one by it.
- Section 7.2, rule 5: a canonicalizer never re-encodes a binary object; the sentence about an
  implementation that holds an attachment as bytes is gone.
- Section 7.3: `format` and `version` are written as their fixed values, and an `extensions` or
  `source` without members counts as absent.
- Section 7.7: the pretty form ends in exactly one LF and writes every number inside `extensions`
  in its canonical form, so it has one byte sequence per document content.
- Section 12.2: the string bound (`maxStringBytes`) covers every string of the envelope
  (`format`, `version`, `semanticModel`, `source.syntax`, `source.sha256`); "need no bound" is
  gone.
- Section 12.2: every member name of the document is held to the string bound in UTF-8 bytes,
  before the grammar of its object; the owner-token and decimal bounds apply inside the limits,
  and "never as `ESJ-L1-LIMIT`" holds only there.
- Section 12.2: every number token, wherever it stands, is held to the string bound as it is
  written and before it is built; past it the finding is `ESJ-L1-LIMIT`, not `ESJ-L1-JSON-TYPE`
  or `ESJ-L1-EXT-NUMBER`.
- Section 12.2: the table names every limit (`maxDocumentBytes`, `maxValues`,
  `maxValueMembers`, `maxPathSegments`, `maxPathBytes`, `maxStringBytes`, `maxBinaryValueBytes`,
  `maxTotalBinaryBytes`, `maxExtensionDepth`, `maxExtensionNodes`), and says how each is measured:
  after JSON escapes are read, a lone surrogate counting three bytes, a count reached by the first
  member or node past it.
- Section 12.2: a member name of `values` is held to the string bound and the path length before
  the path grammar, and its segments are counted once it satisfies the grammar.
- Section 12.2: the total decoded binary content adds ⌊L/4⌋·3 − min(p, 2) bytes for a `value` of
  length L ending in p `=`, never less than nothing, whether or not the value is canonical base64.
- Section 9.5: `path` and `subject` are fixed for every code in one table. At L1 the path is the
  member's path for a finding about the value of a member of `values` and empty otherwise; at L2
  `ESJ-L2-UNKNOWN-TERM` and `ESJ-L2-INDEX-*` name the segment, `ESJ-L2-PARENT-CHAIN` the term the
  path ends at, `ESJ-L2-COMPONENT-*` the component, and the content codes, `ESJ-L2-NOT-CHECKED`
  and `ESJ-L2-EDITION-UNKNOWN` nothing.
- Section 9.5: an L1 `subject` is a member access with a grammar (also in Appendix A): a name the
  specification defines in that object after `.`, every other name in brackets as a JSON string
  (`values["/BT-1"]`, `extensions["de.example"]["a"][0]`, an undefined top-level member
  `["profile"]`, also `["value"]`, `source["foo"]`), an array element as `[n]`.
- Section 9.5: `ESJ-L1-DUPLICATE-MEMBER` names the access of the name that occurs twice
  (`values["/BT-1"]`, `format`, `source.syntax`, `extensions["a.b"]["x"]`), no longer the object
  it occurs in; inside a value object it names the value object, as a lone surrogate in a name
  there does.
- Section 9.5: each missing required envelope member draws its own `ESJ-L1-ENVELOPE-MEMBER`,
  `subject` its name, in the order `format`, `version`, `semanticModel`, `values`.
- Section 9.5: a finding about a value object names the member the step of the order of
  section 9.6 that applies is about, the first in document order where several are, and the
  value object where the step is about the object as a whole.
- Section 9.5: `ESJ-L1-LIMIT` carries the member's path where the bound is reached inside the
  value of a member of `values`, and as `subject` the access of the member or element whose name
  or value reaches the bound — the surplus member for `maxValues` (path empty), the value object
  for `maxValueMembers`, the full access with indices inside `extensions`, and inside a structure
  walked past under `values` the member walked past (`values["/BT-1"]`,
  `values["/BT-1"].value`) and nothing deeper.
- Section 9.5: a `subject` is never shortened; a lone surrogate is escaped as `\u` and four
  lowercase hexadecimal digits (`\ud800`) in a message and a `subject` alike.
- Sections 9.5 and 12.2: a member name past the string bound is not held and not part of the
  finding: `ESJ-L1-LIMIT` names the object the name stands in (`values`, `source`, `extensions`,
  `extensions["o"][0]`, the value object `values["/BT-1"]`, empty in the envelope), and its
  message MUST name the byte offset at which the name begins. A reader reads such a name to its end
  for the JSON text alone, so a 60 MiB name costs a refusal no more than its bytes.
- Section 9.5: a message names a place as the byte offset of the start of the token, counted from
  zero, also for a token the text ends inside — a string that is not closed at its opening
  quotation mark — and the length of the byte sequence only where the text ends between two
  tokens; `ESJ-L1-JSON` MUST name it, `ESJ-L1-LIMIT` SHOULD.
- Section 9.5: `path` and `subject` are the empty string where empty, never absent or null.
- Section 9.5: an exception carries code, path and subject of the finding it stopped at.
- Section 9.6: `ESJ-L1-JSON` is a finding about the document (empty path and subject), and a
  token that is not a complete JSON value where a value stands (`tru`, `01`, `1.`, `truex`) is
  `ESJ-L1-JSON`, not `ESJ-L1-ENVELOPE-VALUE` or `ESJ-L1-JSON-TYPE`.
- Section 9.6: the checks of one name or string run in a fixed order — a member name: string
  bound, lone surrogate, repeated name, then its object's checks; a string of the envelope: lone
  surrogate, string bound, value; a member of `source`: lone surrogate, empty string, string
  bound, `sha256` grammar; a string of `values`: lone surrogate, empty string, string bound.
- Section 9.6: the first defect the text reaches is the one that counts, and a member name is
  judged before the colon after it and before the value written under it.
- Section 9.6: a lone surrogate is found before the string bound however far past the bound a
  string runs, and a string that carries one is held to no bound; inside a value object a reader
  holds a supplementary component to the string bound and `value` to the larger of the two
  string bounds while it reads them, and a member past that stops it before the object is
  judged.
- Sections 9.6 and 12.2: inside a structure a reader walks past only well-formedness and three
  limits are checked — the depth, and the string bound on names and number tokens; a string there
  is held to no string bound; no `ESJ-L1-SURROGATE` or `ESJ-L1-DUPLICATE-MEMBER` comes from it,
  and the reader reads on. An undefined envelope or
  `source` member draws `ESJ-L1-ENVELOPE-MEMBER` alone, whatever its value carries.
- Sections 5.6 and 9.2: L2 walks the segments of a path from the left. A core segment the core
  registry lacks is always `ESJ-L2-UNKNOWN-TERM`, one per segment, even beside an unloaded
  extension segment; an extension segment no loaded registry carries — the namespace not loaded,
  or an identifier the loaded registry lacks — draws one `ESJ-L2-NOT-CHECKED` per path, `subject`
  empty; the index rule holds at every segment before the first unknown one; of a path whose
  segments are all known, the parent chain, the content and the components are checked as well,
  every failure reported, in that order.
- Section 9.3: a validator handed bytes and asked for L2 or L3 alone names them
  `PRECEDING-LAYER-FAILED` where the reader found an L1 error.
- Section 9.5: `NOT-REQUESTED` stands outside the precedence of the other reasons, and a
  composition of two results is stated by the two reasons the results name:
  `NOT-REQUESTED` and `EDITION-UNKNOWN` give `EDITION-UNKNOWN`, `NOT-REQUESTED` and `LIMIT` or
  `PRECEDING-LAYER-FAILED` give `NOT-REQUESTED`, any other two the first in the order of
  precedence.
- Sections 9.1 and 9.5: a validator handed an already parsed document MAY check the limits.
- Section 10: loading registries refuses an identifier defined twice, an extension that defines a
  term of the core it is combined with, an extension whose parents or `reusesTerms` name a core
  identifier without `imports`, and a combination with a core whose model and edition `imports`
  does not name; the three component rules are checked by every implementation. A fifth rule:
  an extension registry defines identifiers of its own namespace only.
- Section 10: a generator refuses a repeatable group whose slug is not a plural and escapes a
  reserved word rather than refuse it; rule 3 is about groups, and the repeatable terms BT-10 and
  BT-46 of the 2026 registry keep their singular slugs.
- Grammar: the two `year` productions are `edition-year` and `date-year`; `b64tail2`,
  `b64tail4` and the owner-token rule use case-sensitive literals (`%s"c"`, `%s"BT-"`); the
  value object of Appendix A is an informative sketch of exactly the objects section 6.1 admits.
- Regular expressions are those of ECMA-262 and end in `(?![\s\S])` rather than `$`, in
  section 5.1, in `schema/esj.schema.json`, in the generated `schema/esj-en16931-*.schema.json`
  and in `model/registry.schema.json`, so that no engine accepts a string followed by LF.
- `schema/esj.schema.json`: `semanticModel` is typed `string` (5, `null`, `[]` and `{}` passed
  before) and bounded; the guard on a string of `values` is 2 097 152 code points and on the
  content of a value object 67 108 864, twice the limit, because the limit is measured after CR LF
  has become LF; `source.syntax` keeps 1 048 576.
- `model/registry.schema.json`: a registry whose namespaced terms hang under a core identifier, or
  carry one through `reusesTerms`, requires `imports`.
- Clarified, with no change to what conforms: business rules are "not part of ESJ conformance"
  (sections 1.2, 9.4, 9.5); `100.00` is refused by a validator and passed through by a reader and a
  canonicalizer (6.4); the sign rule of a decimal is a lookahead in the generated schemas (6.4);
  serializing an ESJ document again is canonicalizer or pretty form, not the writer class (3.3);
  the syntax of an extension segment is L1 (5.6, 9.2); "one defect, one code" (9.6); repeatable
  terms and canonical order per edition (5.3, 7.4); the edition mapping runs from registry to
  document only (10); code-list snapshots live in rule packs and the 2026 `schemeList` rests on
  that edition's usage notes (10); Appendix C lists all twelve examples; the `value` of a binary
  object is held to `maxBinaryValueBytes` alone, also where that is the smaller bound (12.2).

## [0.9.5] — 2026-10-08

### Added

- Six negative fixtures under `examples/invalid/`, run by the fixture manifest over the Java, the
  TypeScript and the C# reader: `extension-number-exponent-overflow` and `-underflow`
  (`ESJ-L1-EXT-NUMBER`), `value-depth-32` and `value-depth-33` (`ESJ-L1-JSON-TYPE`, then
  `ESJ-L1-LIMIT`: the walk past a structure inside `values`), `path-syntax-with-array-value`
  (`ESJ-L1-PATH-SYNTAX` and `ESJ-L1-JSON-TYPE`) and `values-deep-array` (`ESJ-L1-ENVELOPE-VALUE`).
- `de.bsnsoft.esj.Preview`, an annotation for what is published to be used and judged and may
  change in any minor release. It marks the packages `…typed.v2026`, `…rules.en16931.v2026`,
  `…upgrade` and `…b2c`, the types `MinorUnits`, `PackFetcher`, `PackRecipe`, `PackRecipes`,
  `ContainerChecks`, `InvoiceAttachments` and `En16931V2026Pack`, and the method
  `RulePackSource.currencyMinorUnits()`. The generator writes the package comment of the 2026
  view with it.
- `Registry.isPreview()`: whether a registry describes an edition this project ships as a preview.
- `de.bsnsoft.esj.imports.InvoiceReader`, `ImportResult read(byte[] xml)`: `StreamingReader` and
  `XrImporter` implement it, and `PdfInvoiceImporter.importPdf` hands the attachment to one.
- The package `de.bsnsoft.esj.xml` in `esj-core`, the front door of an XML invoice:
  `InvoiceSyntax` with `of(namespace, localName)`, `EncodingMode`, `XmlEncodingReport` with
  `of(byte[])`, and `XmlEncodingException`. Both readers, the syntax engine and the PDF container
  name a syntax and an encoding with these types.
- `Registry.en16931WithXrechnung()`: the core model with the XRechnung extension, built once; the
  readers, the renderers and `XrExporter` use it by default.
- `EsjLimitException.bound()` names the bound that was reached as an `EsjLimitException.Bound`:
  the setting (`maxInputBytes`, `maxPages`, `maxRuntime`, …), its value and its unit. Every bound
  of every module names it.
- `RenderEngineException`, the failure of the rendering machinery, which `RenderException` stood
  for itself before.
- `CiiWriter.semanticModel()`, `CiiWriter.supports(String)`, `UblWriter.semanticModel()` and
  `UblWriter.supports(String)`.
- Every jar names its module: `Automatic-Module-Name` is `de.bsnsoft.esj.` followed by the
  artifact identifier without `esj-` (`de.bsnsoft.esj.core`, …); `MavenCoordinatesTest` holds every
  published jar to it.
- The Maven profile `api-check` compares the API of every library module with the release
  `esj.api-baseline` names, with japicmp, leaving out internal packages, the preview packages and
  everything marked `@Preview`. It reports and does not fail before 1.0.0; the CI job *API compared
  with the last release* keeps the report (`docs/releasing.md`). Against 0.9.4 it names 124 classes
  with incompatible changes, all of them the moves, renames and removals of the migration table and
  the preview marks.
- The rule files of both bundled packs, as their manifests name them, are validated against
  `rules/rule.schema.json` on every build; the 2026 pack's test is part of what
  `bin/without-edition-2026.sh` removes.
- Three negative fixtures carry, in a member name, every class of character `SPEC.md` section 9.5
  escapes: `path-syntax-terminal-characters` (`ESJ-L1-PATH-SYNTAX`),
  `owner-token-terminal-characters` (`ESJ-L1-OWNER-TOKEN`) and
  `envelope-member-terminal-characters` (`ESJ-L1-ENVELOPE-MEMBER`). The fixture manifest records
  the `subject` of a finding where section 9.5 requires one — where the path is empty, and on a
  finding of layer L3 — and `run.py` and the manifest tests of both bindings compare it there; a
  binding's `validate` answer carries `subject` beside `path` and `code`.
- `docs/compatibility.md`: what a release may change from 1.0.0 — the format, the command line,
  the Java libraries, the data they ship — and the rules the Java API keeps; `docs/java-api.md`
  lists every public package as API, preview or internal.
- `Registry.editionKey()`, `Registry.defaultEditionKey()` and `Registry.editionKeys()`: an edition
  is named three ways and each has its method — the key (`2017`), `edition()` (the title) and
  `semanticModel()` (the spelling of a document).
- `de.bsnsoft.esj.render.PaymentCode` (`TEMPLATE`, `DRAW`, `OMIT`), what `RenderOptions` says
  about the EPC QR code of the letter.

### Changed

- `esj render --html` is held to `--max-output-bytes` (64 MiB, 1 GiB under `--limits large`), as
  every XML document the tool writes is: the page is measured in bytes of UTF-8 while it is
  written, and one past the bound leaves with exit code 7 and nothing written, rather than being
  finished in memory first. In the library the bound is `RenderOptions.maxHtmlBytes()`
  (`withMaxHtmlBytes(long)`, 1 GiB by default) and is reached with an `EsjLimitException`.
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
- EN 16931-1:2026 is a preview, and says so: `esj --version` prints
  `semantic model registries 2017, 2026 (preview)`, `esj inspect` and `esj validate` print
  `Semantic model: EN16931-1:2026 (preview)`, the validation report writes *(preview)* or
  *(Vorschau)* beside the edition, `esj upgrade` writes an `info:` line where the edition it reads
  or writes is one, and `esj --list-packs` marks the pack `en16931-2026/0.1`. The JSON output is
  unchanged. `README.md`, `SPEC.md` (section 1; the format stays 0.1), `docs/editions.md`,
  `docs/java-api.md` and the FAQ say so too.
- The Javadoc leaves out every package whose name contains `internal`.
- `esj-syntax` and `esj-render` declare Saxon-HE, which they call directly; `esj-cli` takes
  `esj-typed` in test scope only, so the self-contained jar no longer carries the typed view it
  never loads.
- One set of characters that steer a terminal, `Esj.steersATerminal(int)` and
  `Esj.isBidiControl(int)` in `esj-core`, is used by every text output: the messages of
  `esj-core`, `esj-rules` and `esj-pdf`, the lines of `esj list`, `esj inspect`, `esj diff`,
  `esj extract --list` and of every report, and the error stream. The JSON reports write the same
  characters as JSON escapes. Until now `list`, `inspect` and `diff` wrote C1 controls (CSI,
  NEXT LINE), U+061C, U+2028 and U+2029 as they stood, and a message of a registry quoted its
  content unescaped. `esj get` still writes a value raw. `esj-render` takes the bidirectional
  controls from there as well and adds the interlinear annotation characters U+FFF9–U+FFFB,
  which only a rendering replaces; the messages of `esj-rules` and `esj-pdf` write `\u` with
  lowercase digits, as `esj-core` does.
- Every command that reads a document holds the five-minute deadline where `--max-runtime` names
  none: `convert`, `upgrade`, `inspect`, `extract`, `get`, `list`, `diff`, `canonicalize`, and
  `embed` without `--verapdf`, as `validate` and `render` did. Exit code 7 and no verdict. Over
  `conformance/scale` (80 MB of UBL and CII, rich and dense lines, `--limits large`) each of them
  ends in 2 to 22 s; `--importer xslt` takes 2 minutes over 25 MB and needs a larger
  `--max-runtime` from about 40 MB.
- `examples/smallest-valid.esj.json` states BT-110 (0): it is `VALID` through both syntaxes, where
  `--via ubl` said `INDETERMINATE` (exit code 9) because UBL requires the tax amount beside a VAT
  breakdown. It has 28 terms, and removing any of them ends the verdict through one of the two.
- `XmlEncodingReport` has `decodes()` and `XmlEncodingException` has `repairable()`; the earlier
  constructors stay.
- One exception hierarchy: `EsjException` is the abstract root of every exception the libraries
  throw, no longer sealed. `XrException`, `BindingException`, `SyntaxException`, `PdfException`,
  `RenderException`, `PackException`, `RulePackException`, `DerivationException`,
  `MissingValueException`, `ValueTypeException`, `BuildException` and
  `PolicyPreconditionException` extend it. `RenderException` is abstract and sealed, its kinds
  final: `RenderEngineException`, `TemplateException`, `RenderContentException`.
- Every bound of every module raises `EsjLimitException`, which is no longer final and no longer
  belongs to a module's hierarchy: `catch (PdfException e)` or `catch (BindingException e)` no
  longer catches a limit. The messages are the ones of 0.9.4.
- `PdfInvoiceImporter.importPdf(byte[])` reads the attachment with the `StreamingReader`, as the
  command line does; it was the importer of `esj-xr`. The document is the one `esj convert` writes
  for the same file, and a refusal is the streaming reader's (`BindingSyntaxException`,
  `BindingFormatException`). A reader handed in is used as it is. The command line is unchanged.
- `esj-bindings` needs neither `esj-xr` nor Saxon-HE (runtime dependencies 8.7 MB → 1.1 MB), and
  `esj-pdf` no longer needs `esj-xr` (12.7 MB → 5.1 MB); both declare it for their tests only.
- `XrImporter` reads with one verb, as `StreamingReader` does: `read(byte[])`, `readUbl`,
  `readCii` and `readXr` return the `ImportResult`.
- `XmlEncodingException` is thrown by both readers and is an `EsjException`, not an
  `XrException`; a recode that does not decode, which a report made of the same bytes rules out,
  raises it rather than an `XrFormatException`.
- The command line turns a bound of any library into exit code 7 in one place. A bound met while
  the PDF validation report is drawn, or while `esj embed` writes the attachment, now leaves with
  7 where it left with 5, "internal error".
- Options are final classes with one convention: `defaults()` and one `withX(…)` per setting,
  `equals` and the accessors of before. That holds for `Limits`, `ReaderOptions`,
  `WriterOptions`, `SyntaxOptions`, `PdfLimits`, `EmbedOptions`, `RenderOptions`,
  `TotalsOptions`, `AuthoringOptions` and `UpgradeOptions`; none has a builder or a public
  constructor any more.
- A value that changes with a release is a method rather than a constant compiled into the
  caller: the format version, the default edition, the version of a bundled rule pack and the
  defaults of every options class.
- One `Severity` (`ERROR`, `WARNING`, `INFO`) and one `ValidationStatus` in
  `de.bsnsoft.esj.validate` for every check: the syntax engine, the rule engine, the container
  checks of a PDF and the structural validator. The reports, the JSON output and the ledgers keep
  the words of each source — `fatal` and `information` for an artefact, `fatal` and `info` for a
  rule pack.
- `Term` is a final class with the accessors of the record; a later release can give it a member
  without breaking a caller.
- An array a method of the API returns is a copy: `ExportResult.xr()`, `AttachmentContent.bytes()`
  and `PdfContainer.xmpPacket()` handed out the array they hold.
- The generated views, editors and step builders, `Coded` and the steps of `InvoiceSteps` are
  sealed; `EditorList`, `ValueList`, `IdentifierList` and `SchemedIdentifierList` say they are not
  for implementation.
- The PDF engine of `esj-render` and the report renderer are in `de.bsnsoft.esj.render.internal`;
  `PdfRenderer`, `HtmlRenderer`, `RenderOptions`, `RenderTemplate`, the enums and the exceptions
  stay.
- A PDF validation report that reaches its page bound says so — the 2 000 pages a report is drawn
  within, which `--max-pages` does not move — where the line named `--max-pages`. Exit code 7.

### Removed

- `esj-generator` is no longer published on Maven Central and is no longer managed by `esj-bom`.
- `XrLimitException`, `BindingLimitException`, `SyntaxLimitException`, `PdfLimitException` and
  `RenderLimitException`; `XrSyntax` and `BindingSyntax`; `XrImporter.importUbl`, `importCii`,
  `importXml`, `fromXr` and `defaultRegistry()`; `Esj.forMessage`, `forSubject` and `abbreviated`.
  The migration table below names what takes their place.

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
- A document whose bytes do not decode in the charset it declares and are none of UTF-8, UTF-16,
  ISO-8859-1 and Windows-1252 — Shift_JIS or EUC-JP over bytes that spell no character of it,
  US-ASCII over Latin-1 — was read with U+FFFD where the bytes did not decode, without the note
  `ENCODING_REPAIRED` and under `--strict` as well, by every command but `validate`. Both readers
  now refuse it in either mode: exit code 2 naming the charset, and `XML-ENCODING` under
  `esj validate`. The same holds behind a byte order mark whose charset the bytes do not spell.
  Bytes that are UTF-8 under another declaration are read as UTF-8, and a byte Windows-1252
  leaves undefined as ISO-8859-1, each with the note.
- No XML parser of the platform is handed bytes it cannot decode, so none writes a line of its own
  (`[Fatal Error] …`) to the error stream. A document declaring UTF-8 with such a byte before its
  root element was refused by every command as being of no syntax this tool reads; it is now
  recoded with the note, like any other.
- `docs/cli.md`: `esj extract --out` follows a symbolic link at the path the caller names.
  `docs/validation-measurements.md`: the official Schematron is superlinear where a line-level
  assertion fails on every line.
- TypeScript and C#: a message and a `subject` escape the characters of `SPEC.md` section 9.5 the
  Java implementation escapes. Both let the C1 controls, U+2028, U+2029 and U+061C through, and
  TypeScript U+200E and U+200F as well; TypeScript wrote `\u001B` where Java and C# write
  `\u001b`, and ended a cut excerpt in `…` where they write `...`. TypeScript exports
  `steersATerminal` and `isBidiControl`, C# has `Esj.SteersATerminal` and `Esj.IsBidiControl`.
- TypeScript: a finding about a member of `source` names it `source.syntax` (was
  `source["syntax"]`), and one about a name repeated in `values` names `values` (was the member
  access of the name), as Java and C# do.

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

### Format

- `SPEC.md` section 9.5 names every character a finding message and a `subject` escape as `\u`
  and four lowercase hexadecimal digits: besides the C0 controls and DEL, every C1 control
  (U+0080–U+009F), U+2028, U+2029 and the bidirectional formatting characters, listed as
  U+061C, U+200E, U+200F, U+202A–U+202E and U+2066–U+2069. The reference implementation let the
  C1 controls and the two separators through, and U+061C in the messages of `esj-core`; the
  section said `\uXXXX` and left the case of the digits open.

### Migration from 0.9.4

The API is cut before 1.0: what a caller is not meant to depend on moved into packages whose name
contains `internal`, and what may still change is marked `@Preview`. Neither is covered by the
compatibility promise. One row per package or type:

| 0.9.4 | 0.9.5 |
|---|---|
| `de.bsnsoft.esj.rules.en16931.En16931` | `de.bsnsoft.esj.rules.en16931.En16931Pack` |
| `de.bsnsoft.esj.rules.en16931.v2026.En16931V2026` | `de.bsnsoft.esj.rules.en16931.v2026.En16931V2026Pack`, a preview; the service declaration of `RulePackSource` names it |
| `de.bsnsoft.esj.rules.en16931.Br*`, `SchemeIdentifier` | `de.bsnsoft.esj.rules.internal.en16931`, internal |
| `de.bsnsoft.esj.rules.en16931.v2026.Br*` | `de.bsnsoft.esj.rules.internal.en16931.v2026`, internal |
| `"class"` of `javaRules` in a rule pack manifest, `…rules.en16931.Br62` | `…rules.internal.en16931.Br62`; a manifest of a pack directory that names a rule of this build names it so |
| `de.bsnsoft.esj.typed.runtime` | `de.bsnsoft.esj.typed.internal`, internal; generated views and the B2C overlay import it from there |
| `de.bsnsoft.esj.xr.XmlFrontDoor` | `de.bsnsoft.esj.xr.internal.XmlFrontDoor`, internal |
| `de.bsnsoft.esj.report` (`ValidationOutcome`, `Text`, `Phrase`) | `de.bsnsoft.esj.internal.report`, internal |
| `de.bsnsoft.esj.render.ReportOptions` | `de.bsnsoft.esj.render.internal.ReportOptions`, internal |
| `de.bsnsoft.esj.bindings.BindingTable` | no longer public; `CiiWriter.semanticModel()` and `supports(String)`, `UblWriter.semanticModel()` and `supports(String)` say which edition a writer writes |
| `esj-generator` on Maven Central and in `esj-bom` | not published; it is the build tool of this repository |
| `de.bsnsoft.esj.xr.XrSyntax`, `de.bsnsoft.esj.bindings.BindingSyntax` | `de.bsnsoft.esj.xml.InvoiceSyntax` in `esj-core`; `XmlFrontDoor.detect` is `InvoiceSyntax.of(namespace, localName)` for a name |
| `de.bsnsoft.esj.xr.XrEncodingMode` | `de.bsnsoft.esj.xml.EncodingMode` |
| `de.bsnsoft.esj.xr.XmlEncodingReport` | `de.bsnsoft.esj.xml.XmlEncodingReport` |
| `de.bsnsoft.esj.xr.XrEncodingException` | `de.bsnsoft.esj.xml.XmlEncodingException`, an `EsjException` and no `XrException` |
| `de.bsnsoft.esj.xr.XmlBytes` | `de.bsnsoft.esj.internal.XmlBytes`, internal; `XmlBytes.inspect(xml)` is `XmlEncodingReport.of(xml)` |
| `XrImporter.importXmlWithReport`, `importUblWithReport`, `importCiiWithReport`, `fromXrWithReport` | `XrImporter.read`, `readUbl`, `readCii`, `readXr` |
| `XrImporter.importXml(xml)`, `importUbl`, `importCii`, `fromXr` | `XrImporter.read(xml).document()`, `readUbl`, `readCii`, `readXr` |
| `XrImporter.defaultRegistry()` | `Registry.en16931WithXrechnung()` |
| `PdfInvoiceImporter.importPdf(pdf, XrImporter)`, `importPdf(pdf, limits, XrImporter)` | `importPdf(pdf, InvoiceReader)`, `importPdf(pdf, limits, InvoiceReader)`; an `XrImporter` is one |
| `PdfInvoiceImporter.importPdf(pdf)` read with `new XrImporter()` | reads with `new StreamingReader()`; `importPdf(pdf, new XrImporter())` reads as before |
| `XrLimitException`, `BindingLimitException`, `SyntaxLimitException`, `PdfLimitException`, `RenderLimitException` | `de.bsnsoft.esj.EsjLimitException`; `bound()` names the bound |
| `SyntaxLimitException.budget()` | `EsjLimitException.bound()`, the bound `maxRuntime` in milliseconds |
| `new RenderException(…)` | `new RenderEngineException(…)`; `RenderException` is abstract |
| `Esj.forMessage`, `Esj.forSubject`, `Esj.abbreviated` | `de.bsnsoft.esj.internal.Messages`, internal; `Esj.steersATerminal` and `Esj.isBidiControl` stay |
| `Limits`, `PdfLimits`, `EmbedOptions`, `RenderOptions`, `TotalsOptions`, `AuthoringOptions` (records), `new X(…)` | final classes: `X.defaults().withY(…)` |
| `Limits.builder()…build()`, `toBuilder()`, `Limits.Builder`; the same for `ReaderOptions`, `WriterOptions`, `UpgradeOptions` | `X.defaults().withY(…)`, setter `y(v)` → `withY(v)` |
| `UpgradeOptions.Builder.drop(path)`, `extension(registry)`, `source(bytes)` | `withDroppable(paths)`, `withExtensions(registries)`, `withSource(bytes)` |
| `RenderOptions.in(language)`, `.on(size)`, `.with(template)`, `.layout(layout)` | `RenderOptions.defaults().withLanguage(language)`, `.withPageSize(size)`, `.withTemplate(template)`, `.withLayout(layout)` |
| `RenderOptions.withPaymentCode(boolean)`, `paymentCode()` as `Optional<Boolean>` | `withPaymentCode(PaymentCode.DRAW / OMIT / TEMPLATE)`, `paymentCode()` as `PaymentCode` |
| `EmbedOptions.of(profile)`, `checkedWith(check)` | `EmbedOptions.defaults().withProfile(profile)`, `withCheck(check)` |
| `TotalsOptions.standard()`, `AuthoringOptions.standard()` | `defaults()` |
| `DEFAULT_MAX_*` of `RenderOptions`, `PdfLimits`, `ReaderOptions`, `WriterOptions`, `SyntaxOptions`; `WriterOptions.DEFAULT_TAX_REGISTRATION_SCHEME`, `AuthoringOptions.DEFAULT_NET_PRICE_SCALE` | the accessor of `defaults()`, e.g. `PdfLimits.defaults().maxPdfBytes()`; `RenderOptions.DEFAULT_LAYOUT` stays |
| `XrImporter.DEFAULT_MAX_INPUT_BYTES` | `XrImporter.defaultMaxInputBytes()` |
| `Esj.VERSION`, `Esj.SEMANTIC_MODEL` | `Esj.formatVersion()`, `Esj.defaultSemanticModel()` |
| `En16931Pack.VERSION`, `En16931Pack.EDITION`, `En16931V2026Pack.VERSION` | `En16931Pack.version()`, `En16931Pack.edition()`, `En16931V2026Pack.version()` |
| `Rules.PACK_VERSION`, `B2cConsistency.PACK_VERSION` | `Rules.packVersion()`, `B2cConsistency.packVersion()` |
| `Registry.DEFAULT_EDITION`, `Registry.editions()` | `Registry.defaultEditionKey()`, `Registry.editionKeys()` |
| `RulePackSources.forEdition(registry.edition())` | `RulePackSources.forRegistry(registry)` |
| `de.bsnsoft.esj.syntax.Severity` (`FATAL`, `WARNING`, `INFORMATION`), `Severity.ofFlag` | `de.bsnsoft.esj.validate.Severity` (`ERROR`, `WARNING`, `INFO`) |
| `de.bsnsoft.esj.rules.RuleSeverity` (`FATAL`, `WARNING`, `INFO`), `RuleSeverity.declared` | `de.bsnsoft.esj.validate.Severity`; `JavaRule.severity()` returns it |
| `ContainerFinding.Severity` | `de.bsnsoft.esj.validate.Severity` |
| `de.bsnsoft.esj.syntax.Verdict` | `de.bsnsoft.esj.validate.ValidationStatus` |
| `Term` (record), `new Term(…)` | final class, made by `Registry` only |
| `ExportResult`, `AttachmentContent` (records), `new …(…)` | final classes, made by the library only |
| `de.bsnsoft.esj.render.ReportRenderer` | `de.bsnsoft.esj.render.internal.ReportRenderer`, internal |
| `de.bsnsoft.esj.invoice.InvoiceSteps` (interface) | final class; its steps are sealed interfaces |

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
