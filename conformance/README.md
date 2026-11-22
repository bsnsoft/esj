# Conformance corpus

Here you find real invoice documents, the ESJ documents this project builds from them, and a
ledger of what could not be built. All of it is checked in, and `mvn verify` checks all of it.
The corpus is not sample material beside the code. It is the part of the test suite that no
unit test can replace.

## Why a corpus at all

A unit test asks whether a rule of the specification holds on a case written to exercise it. It
does not ask whether the whole thing survives an invoice somebody else wrote. The XRechnung test
suite of the Koordinierungsstelle für IT-Standards (KoSIT) is such a body of documents: 86
instances covering the business cases of German electronic invoicing, in both syntaxes the
standard binds, written by people who were not thinking about ESJ.

It is not complete. Those 86 instances are 41 cross industry invoices and 45 UBL **Invoices**,
and not one UBL Credit Note. Of the 3 binding tables of `model/bindings/` the corpus therefore
exercises 2. `creditnote/` holds the 2 documents that measure the third: one written by hand, one
a credit note copy of an extension instance. Its README says what that gap cost before it was
filled.

## Layout

```
conformance/
  kosit/                     the instances, unmodified, plus the KoSIT LICENSE and a
                             README.md with repository, tag, license and a SHA-256 per file
    business-cases/          standard/ and extension/
    technical-cases/         cius/ and cvd/
  esj/                       one <instance path>.esj.json per instance, pretty form, as
                             the streaming reader of esj-bindings writes it
  fixtures/                  the fixture manifest: every case an implementation of the
                             format has to pass, in no programming language, with its
                             schema and a runner beside it; see fixtures/README.md
  syntax/
    README.md                what the official validation artefacts say about the corpus
    ledger.json              their answer per instance, checked on every build, and the
                             record of the comparison against the official validator
    ledger.md                that comparison in words: figures and every difference
    mutations/               instances broken on purpose, as data: a location, a change,
                             and the rules it is expected to make fire
  ledger/
    l3-findings.md           the cardinality findings the corpus produces, per instance
    pairs.md                 the litmus test: the same invoice in both syntaxes
    pairs.json               the machine-readable form of that ledger: every differing
                             path with its cause and the kind of that cause
  rules/
    coverage.md              which rule of the EN 16931 validation artefacts the semantic
                             rule pack carries, rule by rule, with the figures
    not-applicable.md        the rules it does not carry, each with its reason
    corpus.json              what the pack says about all 86 instances and the ten
                             examples, checked on every build
    corpus.md                that in words, with every finding explained
    ledger.json              the pack measured against the official artefacts, rule by
                             rule, with the totals and the timings
    ledger.md                that comparison in words: the figures, every difference with
                             its cause, and what each engine costs on a large document
    mutations/               instances broken on purpose, as data: a location, the changes,
                             and what each engine is expected to report about the result
    mutate.py                writes those documents out, for a reader who wants one
  rules-2026/
    coverage.md              which rule of the 2026 edition its pack carries, and why not
    ledger.md, ledger.json   what stands behind each oracle, checked on every build
    cases/cases.json         the hand-computed cases of the rules whose oracle is cases
  scale/                     the generator for synthetic instances of tens of megabytes and
                             hundreds of thousands of lines, which the corpus has none of;
                             see scale/README.md. What it writes is not checked in.
  creditnote/
    README.md                why a hand-written document stands beside a corpus, what is
                             in it and where the two readers part on it
    credit-note_ubl.xml      one UBL 2.1 Credit Note, written for this repository, which
                             is the only document here that exercises ubl-creditnote.json
    credit-note_ubl.esj.json what the streaming reader builds from it, pretty form
    04.01a-CREDITNOTE_ubl.xml
                             the extension instance 04.01a of kosit/ written as a UBL
                             2.1 Credit Note, with sub credit note lines
    04.01a-CREDITNOTE_ubl.esj.json
                             what the streaming reader builds from it, pretty form
  bindings/
    crosscheck.md            what the binding tables of model/bindings/ look like beside
                             the KoSIT visualization stylesheets, the CEN validation
                             artefacts and the older SeMoX model: coverage, agreement and
                             every difference with the reason for it
  readers.md                 the two readers over the same corpus: where the XSLT path of
                             esj-xr and the streaming reader of esj-bindings agree, where
                             they differ and why
  readers.json               that comparison as data: every family of differences with its
                             cause, the pattern its paths match and the counts
  writers/
    cii-roundtrip.md         the CII writer over the same corpus and over examples/: what
    ubl-roundtrip.md         the official artefacts say about every document each one
                             produces, and what survives reading the result back
    cii-roundtrip.json       those measurements as data: the counts, the rule every refusal
    ubl-roundtrip.json       fires, and what each syntax has no place for
    matrix.md                the two writers against each other: every business case the
                             corpus carries in both syntaxes, converted into the other one
                             and compared with the file that was already there
    matrix.json              those four verdicts per business case, and the totals
```

`creditnote/` is the one place here that holds documents nobody else wrote, or wrote as an
invoice. A table no test reaches is a table nobody has measured.

`readers.md` and `readers.json` are about the corpus. Every instance is read both ways, and every
differing semantic path is traced to a cause. `ReaderCorpusTest` in `esj-bindings` recomputes
the whole comparison on every build: both readers run, and neither is compared with a checked-in
file. It fails where the report does not explain a difference, where a count has moved, or where
an instance the two readers agreed on stops agreeing.

The report under `bindings/` measures a table of the repository against other descriptions of
the two syntaxes. A test of `esj-core` fails when it leaves a difference unexplained.

An ESJ file records in its `source` member the syntax it came from and the SHA-256 of the bytes
it was read from. Every file under `esj/` therefore names the instance it belongs to.

## What the tests assert

`ReaderCorpusTest` in `esj-bindings`, over the 86 instances:

- the streaming reader builds a document from each of them;
- the pretty form of that document is byte for byte the file under `esj/`;
- the XSLT path of `esj-xr` reads every instance a second time. Where the two agree at every
  path, the two canonical byte sequences are equal. Every path at which they disagree matches
  exactly one cause of `readers.json`, with the recorded counts;
- layer L2 accepts every document that reader builds, and the observations it reports over the
  corpus are the recorded ones.

`CreditNoteReaderTest` in `esj-bindings`, over `creditnote/credit-note_ubl.xml`: the 3 business
terms the credit note table used to lose carry the values the document states. The pretty form
of what the streaming reader builds is the checked-in file, and layer L2 accepts it. The paths at
which the two readers differ are exactly the 5 that `creditnote/README.md` explains.

`CreditNoteExtensionTest` in `esj-bindings`, over `creditnote/04.01a-CREDITNOTE_ubl.xml`: the
reader reads the sub credit note lines at both depths, with the values of the invoice instance
04.01a apart from BT-3. The writer writes them back as `cac:SubCreditNoteLine` elements that the
pack accepts. The XSLT path differs inside the sub lines only.

`ConformanceCorpusTest` in `esj-xr`, for every instance:

1. The SHA-256 of the instance is the one `kosit/README.md` records. The corpus is the one that
   was investigated, not a later version of it.
2. The importer builds a document from it, and the document carries the provenance of the
   syntax that was read.
3. Validation layer L2 of the specification, section 9.2, accepts that document, with no
   exception anywhere in the corpus. That says every path is one the registry records and every
   value has the type its term declares. It says nothing about whether a value is the right
   one. Until the note subject code was split out of the UBL note, 39 UBL text values carried a
   syntax artefact that L2 could not see. `ledger/pairs.md` records the rule that now removes it
   and the one limitation that remains.
4. The freshly imported document agrees with the file checked in under `esj/` at every semantic
   path except those `readers.json` records this path as losing: 6 values of 6 instances, all
   the identification scheme of one business term. Where there is no such difference, the two
   canonical byte sequences are equal. Every one of those files is therefore a golden file for
   the whole path from XML to canonical bytes.
5. The file under `esj/` reads back into an equal document, and canonicalizing it twice gives
   the same bytes.
6. The import report of that path carries no unplaceable element, no unknown term, no malformed
   value, no empty element, no duplicate path, no path too long to write, no value or document
   that reached a limit of the default reader profile, and no missing mandatory component. The
   ESJ file thus holds everything the stylesheets read out of the instance, apart from 89
   supplementary components the standard does not give their terms. The report names them as
   `COMPONENT_DROPPED` (39 at BT-31, 18 at BT-32, 16 at BT-48, 11 at BT-63 and 5 at BT-90), and
   they are the only notes the whole corpus produces.
7. The cardinality findings of layer L3 are exactly the ones `ledger/l3-findings.md` records.

`CiiWriterCorpusTest` and `UblWriterCorpusTest` in `esj-bindings`, over the same 86 instances
and the 10 documents of `examples/`: each test writes every document in its syntax and runs the
result through the official validation artefacts of its profile. Every fatal finding must be one
that `writers/<syntax>-roundtrip.json` names, with the rule it fires and the number of documents
that fire it. The streaming reader then reads every result back. What comes back must be what was
written, or a loss the report classifies and the writer's own report names. Both tests also
measure the documents the XSLT path builds from the same corpus, because the two readers do not
hand the writer the same documents.

`WriterMatrixTest`, over the 40 business cases the corpus carries in both syntaxes: it reads each
one in one syntax, writes it in the other, validates it, reads it back and compares it with the
file that was already there, in both directions. Every differing path must be one
`ledger/pairs.json` records for that pair. A difference between the two files survives a
conversion; anything else is a writer that lost something. `writers/matrix.json` must carry the
verdicts and the totals the run produces.

`ConformancePairsTest` runs the litmus test of `ledger/pairs.md` over the two documents of a pair
as they are checked in under `esj/`. It asserts 3 things. The set of semantic paths at which they
disagree is exactly the recorded one. The recorded flag for a pair whose two syntaxes agree is
the one the digests give. And the two count tables of the prose are the counts of
`ledger/pairs.json`. It compares the checked-in documents instead of running a reader itself: the
litmus test is a claim about the corpus and the format, and the two tests above cover which
reader wrote those files.

## Which reader wrote the documents under `esj/`, and what changed with it

The streaming reader of `esj-bindings` writes the files under `esj/`. The command line tool
reads with it by default. Before, they were the output of the XSLT path of `esj-xr`, then the
only reader of this project. The condition for the change was set in advance: the streaming
reader had to match or beat the XSLT path over the whole corpus, with every remaining difference
classified as a limitation of the XSLT path that it had overcome.

| Measure | Before | After |
|---|---:|---:|
| instances the two readers agree on, byte for byte | 72 | 80 |
| instances with at least one differing path | 14 | 6 |
| differing semantic paths | 707 | 6 |
| causes of those differences | 3 | 1 |
| of those causes, ones still open rather than overcome | 2 | 0 |

The 2 causes that are gone were both facts missing from the binding tables, and the generator
that writes the tables fixed both. The core business terms that the sub invoice lines of the
XRechnung extension carry are now in the tables. The seller and payee identifiers of a UBL
document no longer take the SEPA creditor identifier that belongs to BT-90. What is left is 1
cause in 6 instances, and it goes the other way: the streaming reader carries an identification
scheme in CII that the stylesheets drop. `readers.md` records all of it, with 2 further
differences that only a broken document reaches.

6 files under `esj/` changed as a result, each by one value gaining a `scheme` member.
`ledger/pairs.md` lost the limitation those 6 values accounted for: 93 differing paths over the
40 pairs became 87. Nothing else in this directory moved.

## The round trip through the XR representation

`XrExporter` writes an ESJ document back as an XR document. On every build, for all 86 instances
of the corpus and all 9 examples of `examples/`, `XrRoundTripTest` asserts that

* the export report is empty, so nothing of the document stayed behind. The one exception is the
  `extensions` subtree of the 2 examples that carry one, which the XR representation has no
  place for;
* importing the written XR document gives the same values again: the canonical form of the
  semantic identity object `{"semanticModel", "values"}` of section 8.2 of the specification
  is byte for byte the one of the document that went in;
* the same document written twice gives the same bytes.

The test compares the semantic identity, not the whole canonical document, because `source`
records the bytes a document was read from, and the second read was of the XR document.
Everything that is a statement about the invoice lies inside what is compared.

### What the round trip does not carry

No business term of either model is lost on the way. The element table the exporter writes from
covers every term of EN 16931-1 and of the XRechnung extension, all 208 of them, and
`XrElementsTest` asserts that against the registry. The round trip cannot carry 4 things. The
export report names each, or no document that came out of a reader can hold it:

1. **`extensions`.** The XR representation carries business terms and nothing else. The exporter
   leaves out a subtree that belongs to no term and names it as `EXTENSIONS_DROPPED`. 2 examples
   carry one. No instance of the corpus does, because reading UBL or CII cannot produce such a
   subtree.
2. **`source`.** Provenance, not content, and not part of the semantic identity (specification,
   section 4.7). The importer records a new one, naming the XR document, when it reads the
   result back.
3. **Content XML 1.0 cannot hold.** A control character other than tabulator, line feed and
   carriage return, and an unpaired surrogate, have no representation in XML at all, not even as
   a character reference. The exporter leaves such a value out and names it as
   `NOT_REPRESENTABLE`. No document read from an XML invoice can contain one, so the corpus has
   none; a document written by hand can.
4. **Whitespace around a value that is not text.** The importer strips the whitespace around the
   content of every semantic data type except `Text`, where the specification, section 6.8,
   preserves it. A hand-written value like `" 380 "` at a `Code` term therefore comes back as
   `"380"`. Nothing that came out of the importer is affected, because it was stripped on the way
   in.

A gap in the occurrence indices is a fifth case, of a different kind. The XR representation
writes occurrences one after another and numbers them by counting, so it cannot express
`/BG-25/2` without `/BG-25/1`. Such a document is invalid to begin with: the specification,
section 5.4, requires dense indices, and layer L3 reports a gap. The exporter names the values
behind the gap as `NO_ELEMENT` and does not renumber them.

Everything above concerns a document that is valid at layer L2. One case outside that loses
something on the way back in, not on the way out. The XR representation gives a scheme attribute
to every identifier element, while the registry gives a scheme component only to the terms whose
semantic data type has one. A value that carries a scheme at a term without one is an
`ESJ-L2-COMPONENT-NOT-ALLOWED` error, which no reader produces. It is written out with its scheme
and read back without it.

The note subject code needs no reverse of `XrNormalization.UBL_NOTE_SUBJECT_CODE`. That
normalization exists because the UBL syntax binding writes BT-21 as a `#AAC#` prefix inside the
note BT-22. The XR representation has an element of its own for BT-21, and the importer splits
nothing when it reads an XR document. The two terms go out as two elements and come back as two
values.

## Regenerating

The files under `esj/` and the 3 files under `ledger/` are outputs of a reader, and the tests
keep them honest. A change in a reader, in the registry or in the canonical form makes them fail
with the difference in hand. Write them again from the run that produced the new behaviour, and
review the change to the ledger with the change to the code. The default reader writes them, and
`ReaderCorpusTest` fails if they stop being its output.

## License and attribution

The instances under `kosit/` are third-party material, copied unmodified under the Apache
License, Version 2.0. `kosit/LICENSE` and `kosit/README.md` carry the license and the
attribution, and the `NOTICE` file of the repository repeats it. Everything else in this
directory is part of this project and carries its license.
