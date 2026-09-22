# Conformance corpus

A body of real invoice documents, the ESJ documents this project builds from them, and a
ledger of what could not be built. Everything here is checked in, and everything here is
checked by `mvn verify`: the corpus is not sample material beside the code, it is the part of
the test suite that no unit test can replace.

## Why a corpus at all

A unit test asks whether a rule of the specification holds on a case written to exercise it,
not whether the whole thing survives an invoice somebody else wrote. The XRechnung test suite
of the Koordinierungsstelle für IT-Standards (KoSIT) is such a body of documents: 86 instances
covering the business cases of German electronic invoicing, in both syntaxes the standard
binds, written by people who were not thinking about ESJ.

What it is not is complete. Those 86 instances are 41 cross industry invoices and 45 UBL
**Invoices**, and not one UBL Credit Note — so of the three binding tables of
`model/bindings/` the corpus exercises two. `creditnote/` holds the one hand-written document
that measures the third, and its README says what that gap cost before it was filled.

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
  scale/                     the generator for synthetic instances of tens of megabytes and
                             hundreds of thousands of lines, which the corpus has none of;
                             see scale/README.md. What it writes is not checked in.
  creditnote/
    README.md                why a hand-written document stands beside a corpus, what is
                             in it and where the two readers part on it
    credit-note_ubl.xml      one UBL 2.1 Credit Note, written for this repository, which
                             is the only document here that exercises ubl-creditnote.json
    credit-note_ubl.esj.json what the streaming reader builds from it, pretty form
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

One page per syntax the project writes, plus one that compares the two.

`creditnote/` is the one place here that holds a document nobody else wrote: a table that no
test reaches is a table nobody has measured, and three defects of `ubl-creditnote.json` were
found within minutes of one credit note existing.

`readers.md` and `readers.json` are about the corpus: every instance is read both ways and
every differing semantic path is traced to a cause. `ReaderCorpusTest` in `esj-bindings`
recomputes the whole comparison on every build — both readers run, neither is compared with a
checked-in file — and fails where a difference is not one the report explains, where a count
has moved, or where an instance the two readers agreed on stops agreeing.

The report under `bindings/` is not about documents: it measures a table of the repository
against other descriptions of the same two syntaxes. It is checked in for the same reason the
ledgers are, and a test of `esj-core` fails when it leaves a difference unexplained.

An ESJ file records in its `source` member the syntax it came from and the SHA-256 of the bytes
it was read from, so every file under `esj/` names the instance it belongs to.

## What the tests assert

`ReaderCorpusTest` in `esj-bindings`, over the 86 instances: the streaming reader builds a
document from each of them; the pretty form of that document is byte for byte the file under
`esj/`; every instance is then read a second time with the XSLT path of `esj-xr`, and where the
two agree at every path the two canonical byte sequences are equal, while every path at which
they do not agree matches exactly one cause of `readers.json` with the recorded counts. It also
asserts that layer L2 accepts every document that reader builds and that the observations it
reports over the corpus are the recorded ones.

`CreditNoteReaderTest` in `esj-bindings`, over the one document of `creditnote/`: the three
business terms the credit note table used to lose carry the values the document states; the
pretty form of what the streaming reader builds is the checked-in file; layer L2 accepts it;
and the paths at which the two readers differ are exactly the five that `creditnote/README.md`
explains.

`ConformanceCorpusTest` in `esj-xr`, for every instance:

1. the SHA-256 of the instance is the one `kosit/README.md` records, so the corpus is the one
   that was investigated and not a later version of it;
2. the importer builds a document from it, and the document carries the provenance of the
   syntax that was read;
3. validation layer L2 of the specification, section 9.2, accepts that document, with no
   exception anywhere in the corpus — which says that every path is one the registry records
   and every value has the type its term declares, and nothing about whether a value is the
   right one: until the note subject code was split out of the UBL note, 39 UBL text values
   carried a syntax artefact that L2 could not see, and `ledger/pairs.md` records the rule
   that now removes it and the one limitation that remains;
4. the freshly imported document agrees with the file checked in under `esj/` at every
   semantic path but the ones `readers.json` records this path as losing — six values of
   six instances, all of them the identification scheme of one business term — and where
   there is no such difference the two canonical byte sequences are equal, which makes
   every one of those files a golden file for the whole path from XML to canonical bytes;
5. the file under `esj/` reads back into an equal document, and canonicalizing it twice gives
   the same bytes;
6. the import report of that path carries no unplaceable element, no unknown term, no
   malformed value, no empty element, no duplicate path, no path too long to write, no value
   or document that reached a limit of the default reader profile and no missing mandatory
   component — so the ESJ file holds everything the stylesheets read out of the instance,
   apart from 89 supplementary components the standard does not give their terms, which the
   report names as `COMPONENT_DROPPED` (39 at BT-31, 18 at BT-32, 16 at BT-48, 11 at BT-63
   and 5 at BT-90) and which are the only notes the whole corpus produces;
7. the cardinality findings of layer L3 are exactly the ones `ledger/l3-findings.md` records.

`CiiWriterCorpusTest` and `UblWriterCorpusTest` in `esj-bindings`, over the same 86 instances
and over the ten documents of `examples/`: every one of them is written in that syntax, the
result goes through the official validation artefacts of its profile, and every fatal finding
is one `writers/<syntax>-roundtrip.json` names with the rule it fires and the number of
documents that fire it. Every result is then read back with the streaming reader: what comes
back has to be what was written, or a loss the report classifies and the writer's own report
names. Both tests also measure the documents the XSLT path builds from the same corpus, because
the two readers do not hand the writer the same documents.

`WriterMatrixTest`, over the 40 business cases the corpus carries in both syntaxes: each one
is read in one syntax, written in the other, validated, read back and compared with the file
that was already there, in both directions. Every differing path has to be one
`ledger/pairs.json` records for that pair — a difference between the two files survives a
conversion, anything else is a writer that lost something — and `writers/matrix.json` has to
carry the verdicts and the totals the run produces.

`ConformancePairsTest` runs the litmus test of `ledger/pairs.md` over the two documents of a
pair as they are checked in under `esj/`, and asserts that the set of semantic paths at which
they disagree is exactly the recorded one, that the recorded flag for a pair whose two syntaxes
agree is the one the digests give, and that the two count tables of the prose are the counts of
`ledger/pairs.json`. It compares the checked-in documents rather than running a reader itself:
the litmus test is a claim about the corpus and about the format, and which reader wrote those
files is the subject of the two tests above.

## Which reader wrote the documents under `esj/`, and what changed with it

Until this release the files under `esj/` were the output of the XSLT path of `esj-xr`, the
only reader this project had. They are now written by the streaming reader of `esj-bindings`,
which is also what the command line tool reads with by default. The condition for the change
was set in advance: the streaming reader had to match or beat the XSLT path over the whole
corpus, with every remaining difference classified as a limitation of the XSLT path that it
had overcome.

| Measure | Before | After |
|---|---:|---:|
| instances the two readers agree on, byte for byte | 72 | 80 |
| instances with at least one differing path | 14 | 6 |
| differing semantic paths | 707 | 6 |
| causes of those differences | 3 | 1 |
| of those causes, ones still open rather than overcome | 2 | 0 |

The two causes that are gone were both facts missing from the binding tables, and both were
fixed in the generator that writes them: the core business terms that the sub invoice lines of
the XRechnung extension carry are now in the tables, and the seller and payee identifiers of a
UBL document no longer take the SEPA creditor identifier that belongs to BT-90. What is left is
one cause in six instances, and it goes the other way: the streaming reader carries an
identification scheme in CII that the stylesheets drop. `readers.md` records all of it, with
two further differences that only a broken document reaches.

Six files under `esj/` changed as a result, each by one value gaining a `scheme` member, and
`ledger/pairs.md` lost the limitation those six values accounted for — 93 differing paths over
the forty pairs became 87. Nothing else in this directory moved.

## The round trip through the XR representation

`XrExporter` writes an ESJ document back as an XR document, and `XrRoundTripTest` asserts on
every build, for all 86 instances of the corpus and all 9 examples of `examples/`, that

* the export report is empty — apart from the `extensions` subtree of the two examples that
  carry one, which the XR representation has no place for — so nothing of the document
  stayed behind;
* importing the written XR document gives the same values again: the canonical form of the
  semantic identity object `{"semanticModel", "values"}` of section 8.2 of the specification
  is byte for byte the one of the document that went in;
* the same document written twice gives the same bytes.

What is compared is the semantic identity and not the whole canonical document, because
`source` records the bytes a document was read from and the second read was of the XR document.
Everything that is a statement about the invoice lies inside what is compared.

### What the round trip does not carry

No business term of either model is lost on the way: the element table the exporter writes from
covers every term of EN 16931-1 and of the XRechnung extension — all 208 of them, which
`XrElementsTest` asserts against the registry. What the round trip cannot carry is one of these
four, each either named in the export report or impossible for a document that came out of a
reader:

1. **`extensions`.** The XR representation carries business terms and nothing else, so a
   subtree that belongs to no term is left out and named as `EXTENSIONS_DROPPED`. Two
   examples carry one; no instance of the corpus does, because such a subtree cannot arise
   from reading UBL or CII.
2. **`source`.** Provenance, not content, and not part of the semantic identity
   (specification, section 4.7). The importer records a new one, naming the XR document, when
   it reads the result back.
3. **Content XML 1.0 cannot hold.** A control character other than tabulator, line feed and
   carriage return, and an unpaired surrogate, have no representation in XML at all, not even
   as a character reference. Such a value is left out and named as `NOT_REPRESENTABLE`. No
   document read from an XML invoice can contain one, so the corpus has none; a document
   written by hand can.
4. **Whitespace around a value that is not text.** The importer strips the whitespace around
   the content of every semantic data type but `Text`, where the specification, section 6.8
   preserves it. A hand-written value like `" 380 "` at a `Code` term therefore comes back as
   `"380"`. Nothing that came out of the importer is affected, because it was stripped on the
   way in.

A gap in the occurrence indices is a fifth case and a different kind of thing: the XR
representation writes occurrences one after another and numbers them by counting, so it
cannot express `/BG-25/2` without `/BG-25/1`. Such a document is invalid to begin with — the
specification, section 5.4 requires dense indices and layer L3 reports a gap — and the
exporter names the values behind the gap as `NO_ELEMENT` rather than renumbering them.

Everything above concerns a document that is valid at layer L2. One case outside that loses
something on the way back in rather than on the way out: the XR representation gives a scheme
attribute to every identifier element, while the registry gives a scheme component only to the
terms whose semantic data type has one. A value that carries a scheme at a term without one —
an `ESJ-L2-COMPONENT-NOT-ALLOWED` error, which no reader produces — is written out with its
scheme and read back without it.

The note subject code needs no reverse of `XrNormalization.UBL_NOTE_SUBJECT_CODE`. That
normalization exists because the UBL syntax binding writes BT-21 as a `#AAC#` prefix inside the
note BT-22; the XR representation has an element of its own for BT-21 and the importer splits
nothing when it reads an XR document, so the two terms go out as two elements and come back as
two values.

## Regenerating

The files under `esj/` and the three files under `ledger/` are outputs of a reader, and the
tests keep them honest: a change in a reader, in the registry or in the canonical form makes
them fail with the difference in hand. They are then written again from the run that produced
the new behaviour, and the change to the ledger is reviewed with the change to the code. The
reader that writes them is the default one; `ReaderCorpusTest` fails if they stop being its
output.

## License and attribution

The instances under `kosit/` are third-party material, copied unmodified under the Apache
License, Version 2.0; `kosit/LICENSE` and `kosit/README.md` carry the license and the
attribution, and the `NOTICE` file of the repository repeats it. Everything else in this
directory is part of this project and carries its license.
