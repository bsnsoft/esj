# Fixture manifest

Every case an implementation of ESJ has to pass, written in no programming language, so that
a binding in another language is measured against the same material as the reference one.

```sh
python3 conformance/fixtures/run.py                      # what the manifest contains
python3 conformance/fixtures/run.py --binding ./binding  # run it against an implementation
```

## Files

| File | What it holds |
|---|---|
| `manifest.json` | the manifest: registries, documents, negative fixtures, documents under bounds, registry checks, canonical order, grammars |
| `manifest.schema.json` | the schema of the manifest, of its parts and of the rule case file |
| `manifest-en16931-2026.json` | the part of the later edition, absent from a distribution that does not ship it |
| `cases-en16931-1.3.16.json` | the rule cases: 448 mutations of the corpus as base document plus changes |
| `cases-en16931-2026-0.1.json` | the rule cases of the later edition's pack: its documents, carried in the file, plus changes |
| `arithmetic/pack.json` | a pack of the rule language whose rules pin division, each quotient worked out by hand |
| [`reader/`](reader/README.md) | documents layer L1 refuses, one variant of a defect each |
| [`model/`](model/README.md) | documents layers L2 and L3 refuse, each for the whole list of findings it draws |
| [`documents/`](documents/README.md) | documents a reader reads, for what a validator says of them |
| [`bounds/`](bounds/README.md) | documents read under bounds below the defaults |
| [`registries/`](registries/README.md) | registries written for the manifest: one a validator measures with, and the ones a loader refuses |
| [`canonical-order/`](canonical-order/README.md) | documents not in canonical form, with their canonical bytes |
| [`annex-b/`](annex-b/README.md) | the two documents of `SPEC.md` appendix B, with their canonical bytes |
| `run.py` | the runner, and the request protocol a binding answers |

## Sections

| Section | The case | What is compared |
|---|---|---|
| `registries` | the registries to load before anything else | term counts, and a sample of paths with datatype, cardinality and components |
| `documents` | a document the reader reads | `semanticDigest`, `documentDigest`, the length of the canonical bytes, the number of values, the canonical bytes where a twin is checked in, and the outcome |
| `invalid` | a document that has to be rejected | the outcome, and the digests where the reader reads the document; a case that names `registries` is validated with those |
| `bounds` | a document and bounds of `SPEC.md` section 12.2 | the outcome under those bounds |
| `registryChecks` | registry files, the first the core and every further one an extension | whether a loader takes them (`SPEC.md` section 10) |
| `canonicalOrder` | a document not in canonical form | the canonical bytes, byte for byte, the document digest and the number of values |
| `grammars` | a value substituted into a base document at one path, or a candidate written into `semanticModel` or as the owner token of `extensions` | the error codes reported about that path, or of layer L1 |
| `rules` | a base document plus changes, answered by the pack of the document's edition | the rule identifiers the pack reports, and which of them decide no verdict |
| `arithmetic` | the pack `arithmetic/pack.json` over one document | the rule identifiers that pack reports, compared rule by rule |

## The outcome

An outcome is the whole answer of a validation of the bytes at layers L1 to L3: the `status`, the
layers in `notEvaluated` with their reasons, and every finding — errors, warnings and information
alike — with its `path`, `code`, `subject` and `severity` (`SPEC.md` section 9.5). The subject is
recorded for every finding, the empty one included, and compared like the rest.

The findings are compared in the order the reference reported them where `SPEC.md` fixes that
order: the findings of the reader (section 9.6, how far a reader reads and in which order), and
the findings of one path at layer L2 (section 9.2, the checks of a path). The paths of layer L2
and the findings of layer L3 are compared as sets. A document of a part is validated only by a
binding that carries the edition of that part; its digests are compared by every binding.

## Arithmetic

The `arithmetic` section pins what `rules/README.md` says about division: a quotient that
terminates is exact however many fraction digits it needs, one that does not is computed to 34
fraction digits, half up, and a division by zero is absent. Each rule of its pack asserts that a
quotient is *not* the value worked out by hand for it, so an implementation that divides as the
language says reports every one of them; a rule it leaves silent names a quotient it computed
differently or not at all, and the note of that rule says what it pins. The two rules about a
division by zero stay silent, because the quotient they read is absent. A `rules` request that
names a pack (`run.py` documents it) is how the runner hands a binding this pack instead of its
own.

A `documents` entry names the registries it was measured with, core registry first. A binding
that carries fewer of them measures other terms than the manifest records: an extension term
whose registry is not loaded is reported as not checked (`SPEC.md` section 5.6).

## Editions

`parts` names further manifest files. A part carries the fixtures of one edition and is left
out of a distribution that does not ship that edition, so a part that is not there is not an
error. An implementation that does not carry the edition a part names still answers its
digests and its canonical bytes — those need no registry — and reports
`ESJ-L2-EDITION-UNKNOWN` instead of evaluating L2 and L3 (`SPEC.md` sections 4.4 and 9.2).

## Scope

The manifest covers reading, the canonical form, the digests, layers L1 to L3, the bounds of
section 12.2, loading and combining registries, and the JSON rule language of `rules/README.md`.
It does not cover `esj upgrade`, the writers, PDF or rendering.

## How it is kept true

`FixtureManifestTest` in `esj-cli` builds these files from the reference implementation on
every build and compares them with what is checked in, byte for byte. A manifest cannot
therefore record an expectation the implementation does not meet. The candidates of the
grammar tables, the bounds of the `bounds` section and the sets of `registryChecks` are written
down in that test; what the manifest records about them is measured. The quotients of
`arithmetic/pack.json` are written by hand, and the same test holds the reference
implementation to every one of them before it records what the pack reports.
`AnnexBExampleTest` holds the bytes, lengths and digests `SPEC.md` appendix B prints to the
files of `annex-b/` and to this manifest. After the corpus, a registry or a finding code has
changed, regenerate the files:

```sh
mvn -B -pl esj-cli -am test -Desj.fixtures.rewrite=true
```
