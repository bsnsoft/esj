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
| `manifest.json` | the manifest: registries, documents, negative fixtures, canonical order, value grammars |
| `manifest.schema.json` | the schema of a manifest file, parts included |
| `manifest-en16931-2026.json` | the part of the later edition, absent from a distribution that does not ship it |
| `cases-en16931-1.3.16.json` | the rule cases: 448 mutations of the corpus as base document plus changes |
| `canonical-order/` | documents whose members are written in the wrong order, with their canonical bytes |
| `run.py` | the runner, and the request protocol a binding answers |

## Sections

| Section | The case | What is compared |
|---|---|---|
| `registries` | the registries to load before anything else | term counts, and a sample of paths with datatype, cardinality and components |
| `documents` | a conformant document | `semanticDigest`, `documentDigest`, the length of the canonical bytes, the number of values, the registry files it was measured with, and the errors layers L2 and L3 report |
| `invalid` | a document that has to be rejected | the layer, the finding code of SPEC.md section 9.6 and the path that finding names, one row per code where a document is wrong in two ways at L1; for a document rejected at layer L1, that those rows are the whole answer and nothing stands beside them |
| `canonicalOrder` | a document whose members are in the wrong order | the canonical bytes, byte for byte |
| `grammars` | a value substituted into a base document at one path | the finding code reported about that path, or none |
| `rules` | a base document plus changes | the rule identifiers the pack reports, and which of them decide no verdict |

A `documents` entry names the registries it was measured with, core registry first. A binding
that carries fewer of them measures other terms than the manifest records: an extension term
whose registry is not loaded is reported as not checked and its content is not decided at all
(SPEC.md section 5.6), so a document with such a term would pass on digests while nothing about
those terms was measured.

The rows of one `invalid` document that names layer `L1` or `limit` are its whole answer, not a
sample of it: SPEC.md section 9.6 fixes how far a reader reads, so a second implementation that
reports one finding more or one fewer differs from the reference and the runner says so.

A `documents` entry names `canonical` where the repository carries the canonical bytes beside
the document. Where it does not, `documentDigest` is what pins them; `esj canonicalize` writes
them out.

## Editions

`parts` names further manifest files. A part carries the fixtures of one edition and is left
out of a distribution that does not ship that edition, so a part that is not there is not an
error. An implementation that does not carry the edition a part names still answers its
digests and its canonical bytes — those need no registry — and reports
`ESJ-L2-EDITION-UNKNOWN` instead of evaluating L2 and L3 (SPEC.md sections 4.4 and 9.2).

## Scope

The manifest covers reading, the canonical form, the digests, layers L1 to L3 and the JSON
rule language of `rules/README.md`. It does not cover `esj upgrade`, the writers, PDF or
rendering.

## How it is kept true

`FixtureManifestTest` in `esj-cli` builds these files from the reference implementation on
every build and compares them with what is checked in, byte for byte. A manifest cannot
therefore record an expectation the implementation does not meet. After the corpus, a
registry or a finding code has changed, regenerate them:

```sh
mvn -B -pl esj-cli -am test -Desj.fixtures.rewrite=true
```
