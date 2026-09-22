# Contributing

Review and criticism of the format are more useful to this project right now than large pull
requests. What follows is what a change has to satisfy to be mergeable, and what is declined.

## Build and checks

```text
mvn -B verify
```

is the whole check: it compiles the eleven modules with a JDK 17, 21 or 25 at release 17, runs
every test, builds the source and Javadoc jars, and writes the self-contained
`esj-cli/target/esj.jar` that `bin/esj` runs. Warnings are errors — the compiler runs with
`-Xlint:all` and `failOnWarning`, Javadoc with `doclint` on every group and `failOnWarnings`.

After `mvn -B verify`, regenerate:

```text
mvn -B -Pgenerate -pl esj-generator -am process-classes
```

and check that the working tree is unchanged. `.github/workflows/ci.yml` runs both and fails
when a generated file differs.

## What is generated

`esj-typed/src/main/java/**`, the code list enums under
`esj-invoice/src/main/java/de/bsnsoft/esj/invoice/code/` and the model schema of each
edition under `schema/` are emitted by `esj-generator` from the term registries,
`model/enums.json` and the profile overlays, are checked in, and are what the CI up-to-date check
covers. They are never edited by hand: a typed accessor is changed by changing the registry (its
`slug`, its cardinality, its datatype) or the generator, and then regenerating. A pull request
that edits a generated file directly fails CI on the regeneration step even when it is correct.

`schema/esj.schema.json` is written by hand and is the machine-readable form of most of
layer L1. `SPEC.md` section 9.1 lists the L1 checks it deliberately does not perform; a change
that adds one of those to the schema has to remove it from that list, or explain why the list
was wrong.

## Changing the registry

`model/en16931/2017.json` is the normative source of structure for this specification
(`SPEC.md` section 10). A change to a term's identifier, parent, cardinality, datatype or
supplementary components changes what documents are conformant, so it needs evidence: name the
clause or table of EN 16931-1 that states the fact. No normative prose of the standard goes into
the file — identifiers, names, numbers and the author's own descriptions only.

Both registries validate against `model/registry.schema.json`, which `RegistrySchemaTest`
checks. `slug`, `order`, `codeList`, `maxDecimals`, `description` and `notes` are
documentation and code generation aids, and so is the `schemeList` of a `scheme` component:
they must not affect layer L1, L2 or L3, or the canonical form. A `schemeList` names the code
list an identification scheme is taken from, and the term's `notes` say which rule of the CEN
validation artefacts fixes it; membership of a code list is a business rule and belongs to a
rule pack. A `slug` is a language-neutral name stem, not a Java name; a generator applies
its own language's convention and escapes its own keywords (`SPEC.md` section 10). A `slug`
change is a source-compatibility break in `esj-typed` and needs a reason beyond taste.

A registry file describes one edition and lives at `model/<model>/<edition key>.json`. A new
edition is a new file beside the existing one, never a rewrite of it: documents that name the
old edition must still validate against exactly the terms it had.

## Changing the specification

`SPEC.md` is normative, and a change to it is a change to the format. Such a change comes with

* the wording, with the RFC 2119 keyword it needs and the section it belongs in,
* a document under `examples/` or `examples/invalid/` that exercises it, with its row in the
  matching `README.md`,
* a test that fails before the change and passes after it.

A new finding code goes into the table of section 9.6 with its layer and severity, into
`FindingCode` and into the fixture table of `examples/invalid/README.md`; a released code keeps
its meaning.

Two properties are worth more than any individual rule, and a change that costs either of them
will be declined: two conformant canonicalizers produce identical bytes for the same content
in any language, and no number anywhere in the format passes through binary floating point.

## Adding an example

An example is a synthetic document in pretty form (`SPEC.md` section 7.7) with fictitious
parties — `Example GmbH`, `Muster AG` — fictitious VAT identifiers, and arithmetic that adds
up wherever the terms a rule relates are present. It needs

* a `*.canonical.esj.json` twin holding exactly the bytes the canonicalizer produces from it,
* a row in `examples/README.md` saying what it shows,
* its name in the `Examples.NAMES` list of both test modules, which is what drives the
  parameterized golden-file tests.

A negative fixture carries exactly one defect, is rejected, and gets a row in
`examples/invalid/README.md` naming the layer, the finding code and whether
`schema/esj.schema.json` alone rejects it. `InvalidFixturesTest` and `SchemaTest` read those
same facts from their own tables, so a fixture without a row fails the build rather than
sitting unchecked.

## Changing the conformance corpus

`conformance/` is checked in whole: the 86 instances of the KoSIT test suite, the ESJ document
built from each of them, and three ledger files. It is also asserted whole, so a change to the
importer, to the registry or to the canonical form is expected to break it, and that break is
the review material.

The corpus is enumerated from the checksum block of `conformance/kosit/README.md`. An instance
that is not listed there with its digest is not part of the corpus, and one whose bytes no
longer match its digest fails before anything is imported; adding or replacing an instance
means changing that block in the same commit.

`ConformanceCorpusTest` and `ConformancePairsTest` compare what the importer produces with what
is checked in and print the difference. The outputs are then written again from the run that
produced the new behaviour:

* `conformance/esj/<instance>.esj.json` — the pretty form of the imported document,
* `conformance/ledger/l3-findings.md` — the cardinality findings, four fields per line,
* `conformance/ledger/pairs.json` — the paths at which the two syntaxes of a pair disagree,
* `conformance/ledger/pairs.md` — the same, with both values and the cause of each difference.

A difference that moves in `pairs.json` needs its section in `pairs.md` rewritten, not only its
number: every difference there is classified as a difference between the two test files, as a
named asymmetry of the vendored stylesheets, or as a defect — and a defect is fixed, not recorded.

## Changing a binding table

`model/bindings/{ubl-invoice,ubl-creditnote,cii}.json` are generated, not maintained: an edit
belongs in `model/bindings/tools/semox_to_bindings.py`, never in the JSON. *Regenerating* in
[`model/bindings/README.md`](model/bindings/README.md) is the command and its `--check` form; a
place where the source model says something the syntax does not do goes into the generator's
correction table with its reason, and `conformance/bindings/crosscheck.md` is regenerated with
it.

A table says where a term is read and where it is written, so a change to one is verified from
both ends in the same commit:

* `mvn -B verify` — `esj-core` holds each table against `binding.schema.json`, against the
  registry and against the flag table of its README, and fails on a cross-check difference
  nobody has explained;
* the documents under `conformance/esj/` and the ledgers move with it, as **Changing the
  conformance corpus** above describes;
* `CiiWriterCorpusTest`, `UblWriterCorpusTest` and `WriterMatrixTest` recompute
  `conformance/writers/*.json` from the corpus and fail where a figure has moved in either
  direction — a document that starts to validate as loudly as one that stops;
* the three pages beside those files — `cii-roundtrip.md`, `ubl-roundtrip.md` and `matrix.md` —
  are rewritten from the run that produced the new figures, and so are the tables of
  `docs/conformance.md` and the **Conformance** section of `README.md`.

A figure that moves with no account of why is not a ledger entry; it is a defect to find first.

## Adding or changing a validation pack

`packs/` carries the official validation artefacts that `esj validate` executes: the UBL 2.1
and CII D16B schema modules, the EN 16931 Schematron of CEN/TC 434 and the Schematron of a core
invoice usage specification, each compiled to XSLT by its own publisher.
[`packs/README.md`](packs/README.md) describes the layout and the manifest,
[`packs/SOURCES.md`](packs/SOURCES.md) the provenance, and
[`docs/validation.md`](docs/validation.md) what the engine does with them.

**A release is never overwritten and never edited.** A newer bundle is a new directory beside
the old one, `<pack id>/<version>/<release>/`, because the release date is part of the identity
a report prints and the old digests have to stay valid for every report that ever named them.
Adding one is:

1. build the directory from the upstream archives, every file copied byte for byte — only what
   the engine executes, which is the schema modules and the compiled Schematron: no `.sch`
   sources, no scenario configuration, no documentation, no example instances, no validator;
2. write `pack.json`: the components, the level tables, and the inventory of every file with
   its SHA-256;
3. put the licence text or notice of each component beside the files it belongs to;
4. add the rows to `packs/SOURCES.md` — the release URL, the SHA-256 of the upstream archive
   and of every file, the licence and the day the bytes were fetched — the entries to `NOTICE`
   and the entry to `docs/sources.md`;
5. name the directory in
   `esj-syntax/src/main/resources/de/bsnsoft/esj/syntax/bundled-packs.json`, since
   a class path inside a jar is not a directory that can be listed;
6. run `mvn -B verify`, which recomputes every digest, checks the inventory against the
   packaged bytes in both directions, and checks that the index and the packed packs are the
   same list.

A statement about a licence is a fact with a source: name the file or the release page it comes
from in `SOURCES.md`, and copy a notice that has to travel with its files verbatim rather than
summarising it. A pack whose component carries a licence this repository cannot satisfy is not
added.

**Nothing may be copied out of an artefact into Java, JSON or documentation.** An XPath
expression transcribed into a method, a rule's message text pasted into a resource bundle or a
rule's logic restated on a documentation page are derivations of somebody else's licensed work.
What may be taken is what a report needs in order to be read — a rule identifier, the flag the
artefact set, the message the artefact emits at run time — and what a table maps to a finding
category. A pull request that reimplements a rule will be declined; see **Not part of 0.1**.

A level table in `pack.json` is transcription of statements, not of text: one line per rule
saying that a named profile treats it as fatal, a warning or information, its source in
`SOURCES.md` with the rest. Nothing in the code levels anything.

An existing pack directory is not edited. The digests are checked, so an edit to a vendored file
fails the build and a file no inventory lists fails it too: an artefact that differs from its
published bytes is no longer the artefact the report claims ran.

## Adding or changing a semantic rule

`rules/` is this project's own rules, written over business terms rather than executed as data.
[`rules/README.md`](rules/README.md) is the language, `rules/rule.schema.json` constrains every
rule file, and the pack this build carries is `rules/en16931/1.3.16/`.

**The statement comes from the norm text and the behaviour from the artefacts — never the other
way round.** Read EN 16931-1, clause 6.4, for the identifier, the terms and the condition, and
write the rule in this project's own words; the artefacts' XPath is the oracle the rule is
measured against, not a source to copy from. Where the standard admits more than one reading — a
tolerance, a rounding — follow the artefacts and say so in the rule's `note`; where they
demonstrably contradict the norm, follow the norm and record it in the ledger.

Four steps, and none of them is optional:

1. **Write it.** Give the rule the identifier the artefacts use, so a report can be compared
   line for line with theirs; the clause in `source`; the business terms it reads in `terms`;
   and a message that names the terms and the values seen rather than repeating the identifier.
   A rule a closed operator set cannot express is a `JavaRule` under the same identifier in
   `esj-rules`, named in the pack manifest and passed to `RuleEngine.compile` by the caller.
   Arithmetic is exact `BigDecimal`; the `decimals` operator is for the fixed-scale amounts of
   the standard and never for a unit price, a quantity or a percentage.
2. **Give it cases.** A rule with no negative case is a rule nobody has seen fail. Every rule of
   the pack has a positive case and at least one negative one in `esj-rules`, and a test asserts
   that the table of cases and the pack are the same list of identifiers.
3. **Give it a mutation.** A mutation of a corpus instance shows the rule works on an invoice
   that arrived as XML. Mutations are data in `conformance/rules/mutations/mutations.json` — an
   instance, a location and the change to make there — so that no broken invoice is checked in;
   `conformance/rules/mutate.py` writes one out to look at. Where one shape of the defect
   behaves differently from another, both shapes get a mutation: that is what the ledger's
   *partly* row is for.
4. **Measure it and record it.** `OracleTest` runs the pack and the official Schematron over the
   same bytes and compares the identifiers of both. An outcome that differs is recorded in
   `conformance/rules/ledger.md` with a named cause, and `conformance/rules/ledger.json` carries
   the verdict per rule. A difference nobody has accounted for is not a ledger entry; it is a
   defect to fix. Only the ledger licenses a claim, so a change that moves a figure moves
   `conformance/rules/ledger.md`, `conformance/rules/coverage.md`, `docs/validation.md` and
   `rules/README.md` in the same step.

A rule that is *not* implemented is named in `conformance/rules/not-applicable.md` with its
reason; a test holds the coverage table, that page and the pack to the same list of
identifiers, with none appearing twice.

**A code list snapshot is taken from the body that publishes the list**, with its source URL,
the day it was fetched and what the publisher says about reuse recorded in
`rules/en16931/1.3.16/codelists/SOURCES.md` — never from the code list files of the CEN
artefacts, which are under their own licence, and never from another project's copy; an unclear
term is written down rather than resolved silently. A pack that names a list it has no snapshot
for does not compile.

## A binding in another language

`conformance/fixtures/manifest.json` is the contract: every case an implementation of ESJ has to
pass, written in no programming language — the registries to load, conformant documents with their
two digests, their canonical byte length and what their model layers report, documents that have
to be rejected with the finding code and the path of each, documents in the wrong member order
with their canonical bytes, the accept and reject tables of the value grammars, and every rule
case as a base document plus the changes that break it.
[`conformance/fixtures/README.md`](conformance/fixtures/README.md) is its format and the six
requests a binding answers over a pipe.

```text
python3 conformance/fixtures/run.py                      # what the manifest contains
python3 conformance/fixtures/run.py --binding ./binding  # run it against an implementation
```

`FixtureManifestTest` in `esj-cli` builds those files from the reference implementation on every
build and compares them with what is checked in, so the manifest cannot record an expectation the
reference does not meet. A change to the corpus, to a registry or to a finding code regenerates them
with `mvn -B -pl esj-cli -am test -Desj.fixtures.rewrite=true` in the same commit.

A binding implements `SPEC.md`, not the Java code: it reads the registries of `model/` and the
rule pack of `rules/` as data rather than carrying its own copy of the model, no number anywhere
passes through binary floating point, and it is measured by the manifest rather than against this
implementation's output. Two exist, `bindings/typescript` and `bindings/csharp`
([`docs/bindings-ts.md`](docs/bindings-ts.md), [`docs/bindings-csharp.md`](docs/bindings-csharp.md)),
each with its own test command and its snippets held to a test like every other page's.

## Changing the README and the pages under docs/

`README.md` is an entry point of 200 lines or fewer: what ESJ is in five sentences, the features,
a quick start, persistence, validation, conformance, the layers, the status, the links and the
licence. One status sentence stands at the top and there is no second disclaimer anywhere. A
change adds at most what that frame has a slot for — a verb in the first paragraph, a bullet under
**Features**, a row in the table under **Java**, a line in **Status** — and writes everything else
into the page that carries it, which is the page the **Documentation** list of `README.md` names
for that subject. Every page has a line budget and a page over it is shortened rather than grown.

Every Java snippet of those pages is a test, between a marker comment naming the page and its
section — `// README: <section>`, `// docs/java-api.md: <section>` — and the assertions: each
module holds the tests for its own snippets in
`src/test/java/de/bsnsoft/esj/<package>/ReadmeExamplesTest.java` — `esj-core` and
`esj-typed` in `esj-typed`, the two readers in `esj-bindings` and `esj-xr`, the syntax engine in
`esj-syntax`, the rule engine in `esj-rules`, the renderers in `esj-render` — the domain API in
`esj-invoice/src/test/java/de/bsnsoft/esj/invoice/DomainInvoiceTest.java`, and the
two-line read of an XML file and a PDF in
`esj-pdf/src/test/java/de/bsnsoft/esj/pdf/GettingStartedExamplesTest.java`. A snippet
is changed there first and copied into the page, so no page can describe an API the modules do not
have.

The SQL of `docs/storage.md` is a test too:
`esj-xr/src/test/java/de/bsnsoft/esj/xr/StorageExamplesTest.java` runs every fenced
`sql` block of that page against a PostgreSQL it starts for itself — binaries from a Maven
artefact in test scope, no Docker, nothing redistributed — filled with the UBL and the CII
rendering of one corpus invoice, and asserts what the page says each query answers, the identical
`invoice_summary` row of the two syntaxes included. Where the platform cannot run the binary the
class skips itself with the reason.

Every command line example is a test too.
`esj-cli/src/test/java/de/bsnsoft/esj/cli/ReadmeCliExamplesTest.java` reads
`README.md`, `docs/getting-started.md` and `docs/cli.md` from the classpath and runs every fenced
`console` block in the process the test runs in; the grammar of a transcript is the class comment
of that test, which also asserts that the pages together show every command at work and that
`docs/cli.md` lists every exit code. Change the tool first, run the test, paste what it prints.
`ReadmeJsonExampleTest` reads the one `json` block of `README.md` likewise.

The two deployment pages are held to a rule of their own.
`esj-cli/src/test/java/de/bsnsoft/esj/cli/DeploymentExampleIT.java` compiles and runs
the `ProcessBuilder` snippet of `docs/deployment.md` against the built jar, so the snippet is
changed in the test first, and `DeploymentOutcomesIT` spawns the same jar, pins the outcomes those
pages state and asserts no timing at all. Both are integration tests and run in the
`integration-test` phase, after the jar they spawn is written. Everything on
`docs/deployment-measurements.md` and `docs/validation-measurements.md` is a measurement of one
named machine: a number there is **re-measured and replaced, never edited** to fit a change, and a
change to an exit code, to a bound or to a limit profile has to move both pages, the exit-code
table of `docs/cli.md`, and the footer of `esj --help` in the same step.

The tables of `docs/conformance.md` and the **Conformance** section of `README.md` state numbers
from the ledgers under `conformance/` — the pairs, the readers, the two round trips, the matrix
and the two oracles. No test recomputes them there, so a change to the corpus, to a reader, to a writer or to
a ledger that moves a count has to move both pages with it; `ConformancePairsTest` keeps the
ledger itself honest.

## Code

* Java 17 language level, built with a JDK 17, 21 or 25.
* The only runtime dependency of `esj-core` is `com.fasterxml.jackson.core:jackson-core`, the
  streaming API. Adding a runtime dependency to it needs a very good reason. `esj-xr` also
  depends on `net.sf.saxon:Saxon-HE`, because the vendored stylesheets are XSLT 2.0; that
  dependency stays inside `esj-xr`. `esj-pdf` depends on `org.apache.pdfbox:pdfbox` for the
  object structure, the name trees, the associated files array, the embedded file streams and
  the XMP packet of a hybrid invoice — never for rendering, fonts, JavaScript or forms — and
  that dependency stays inside `esj-pdf`. `esj-cli` depends on `info.picocli:picocli` for
  argument parsing, and that dependency stays inside `esj-cli`.
* `esj-cli` holds no domain logic. It wires streams, recognizes which of the three syntaxes an
  input is written in — or that it is a PDF carrying one of them — and formats what the
  libraries return; a rule about paths, values, canonical bytes or cardinalities that appears
  there is a rule in the wrong module. Its exit codes are an interface: a code never changes
  meaning, and a new situation gets a new code. Nothing outside `Console` writes to
  `System.out` or `System.err`.
* Third-party files are vendored only under their own licence, unmodified, with the upstream
  `LICENSE` or notice beside them, a record of repository, tag and the SHA-256 of every file
  — a `README.md` beside the files, or, under `packs/`, the inventory in `pack.json` together
  with `packs/SOURCES.md` — an entry in `NOTICE` and an entry in `docs/sources.md`. A test
  recomputes the digests, so an edit to a vendored file fails the build, and under `packs/`
  a file that no inventory lists fails it too.
* No Lombok, no reflection-based mapping, no `ObjectMapper` or other databind, no Java
  serialization, no polymorphic type handling. A reader never instantiates a class named in
  its input (`SPEC.md` section 12.3).
* Public types are immutable, and arrays that cross an API boundary are copied. Records and
  sealed interfaces are welcome.
* Javadoc on every public type and every public method, including `@param`, `@return` and the
  exceptions that are thrown; `doclint` enforces it.
* Invalid user data produces findings, not exceptions. Exceptions are for I/O failures and for
  a limit a reader chooses to abort on.
* No `System.out` or `System.err` in main code, no `TODO` or `FIXME` comments, no dead code.
* English only, in code, comments, documentation and commit messages.

## Not part of 0.1

What follows will be declined, and not because they are bad ideas:

* a hand-written mapping between syntax elements and business terms, in either direction. That
  mapping is the syntax binding of CEN/TS 16931-3; the binding tables of `model/bindings/`
  carry it as facts read from a published model, and the two writers and the streaming reader
  read those tables rather than restating them ([`docs/bindings.md`](docs/bindings.md)). A
  second importer is welcome behind the interfaces of `esj-xr`; a fourth copy of the binding
  is not;
* a business rule *transcribed* from the CEN/TC 434 validation artefacts or from a CIUS rule
  set, including XRechnung's. Over an XML input `esj validate` runs the artefacts their
  publishers released — `esj-syntax` executes them as data and translates nothing — and an XPath
  expression restated in Java would be a second source for something that has exactly one. The
  rules of EN 16931-1 clause 6.4 *are* implemented natively, in `rules/` and `esj-rules`, from
  the statements of the norm text and in this project's own words, and **Adding or changing a
  semantic rule** above is how one is added: with a pack identity on every finding, its findings
  a layer of their own and never ESJ conformance (`SPEC.md` section 9.4), a case, a mutation and
  an entry in the oracle ledger. A pull request that adds such a check inside the L1 to L3
  layers, or without that pack identity and that verification, will be declined. A rule set of a
  core invoice usage specification is a pack of its own and is not in 0.1: the syntax engine runs
  the published artefacts for it;
* transport, signing, encryption and archiving;
* a registry for a semantic model other than EN 16931-1. Its editions live beside each other
  under `model/en16931/` ([`docs/editions.md`](docs/editions.md));
* an envelope member for the profile. The profile is BT-24, and two places for one fact is one
  place too many (`SPEC.md` section 11.2);
* publishing to a package repository.

## Licence

Contributions are made under the Apache License, Version 2.0, the licence of everything in
this repository. Do not add text, schemas or code copied from EN 16931-1, from XRechnung or
from any other standard under a licence that does not allow it; facts — identifiers, names,
numbers, cardinalities — are welcome, with the source named. `NOTICE` and `docs/sources.md`
record where the underlying documents come from and under which licence.
