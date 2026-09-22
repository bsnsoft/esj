# Editions

*Part of [EN16931 Semantic JSON](../README.md).*

EN 16931-1 has more than one edition, and a semantic path is an address relative to one:
`/BT-20` is the payment terms at the root of a 2017 document and no path at all in a 2026 one,
where that term sits in a group. Every document names its edition in `semanticModel`, every
registry describes exactly one, and no tool ever reads a document by the registry of another
(`SPEC.md`, sections 4.4 and 10).

Which editions a build carries is a property of that build:

```text
esj --version
```

prints `semantic model registries 2017, 2026` for a build that carries both. The default — the
edition the tool writes when nobody asks for another — is 2017, because the official validation
artefacts, XRechnung 3.0.2 and Peppol BIS 3 are written for it.

## What each command does with a document of a non-default edition

| Command | A registry of that edition is there | No registry of that edition |
|---|---|---|
| `convert --to esj`, `canonicalize`, `list`, `diff` | works; these are edition-blind | works |
| `get` | works; a path that addresses nothing is explained against that edition | works, without the explanation |
| `inspect` | works; the `Semantic model` line names the edition | works, and the line says no registry is carried |
| `validate` | L1 to L3 run against that registry; the business rules run where a pack is written for that edition | model layers not evaluated, verdict `INDETERMINATE` |
| `render` | works; the PDF layout is driven by that registry | refuses, exit 4 |
| `render --html` | refuses, exit 4 | refuses, exit 4 |
| `convert --to cii` | refuses, exit 4 | refuses, exit 4 |
| `upgrade` | writes the document as another edition | refuses, exit 4 |

`diff` compares two documents whichever editions they name, and notes on the standard error
where the two editions differ, because a path they place differently reads as a difference of
the invoice. A refusal names both editions, and the way out where this build has one. Nothing is ever
dropped to make a document fit: a binding table maps the business terms of one edition onto
one syntax version, and writing a document of another edition through it would place the
terms the two editions share and lose the rest.

Three components are written for one edition and say so rather than being extended quietly:

| Component | Written for | Why |
|---|---|---|
| The binding tables and the CII writer | 2017 | no public authoritative mapping of the terms a later edition adds; [`bindings.md`](bindings.md) |
| The vendored XRechnung visualization (`--html`) | 2017 | the stylesheets have nowhere to put a term of a later edition |
| The rule pack `en16931/1.3.16` | 2017 | the rules of the standard are renumbered, added to and withdrawn between editions |

The PDF rendering is this project's own layout and is driven by whatever registry the document's
edition brings: designed sections for the groups it knows, and a final heading under which every
other value stands with its label and its semantic path. That every value of a document of every
carried edition reaches the page is a test of `esj-render`.

## upgrade

```text
esj upgrade invoice.esj.json --to 2026 --out invoice-2026.esj.json
```

What changes between two editions is data — `model/en16931/upgrade-2017-2026.json` — and the
engine reads it; nothing about a pair of editions is written in Java. The mapping moves the
paths that moved and leaves every other value byte for byte.

| Option | What it does |
|---|---|
| `--to <edition>` | the edition to write, for example `2026`. Required |
| `--out <file>` | write the document to this file instead of to the standard output |
| `--output <text\|json>` | how the run reports what it did; `json` writes the report to the standard output and needs `--out` |
| `--specification <identifier>` | write this identifier at BT-24, which is otherwise left as it stands and reported |
| `--drop <path>` | allow the run to drop the values at this path and under it; repeatable |
| `--refuse-open-points` | refuse where the run would leave an open point instead of reporting it |
| `--partial` | write a result that does not satisfy the target edition for a reason the mapping does not explain |
| `--canonical`, `--pretty` | the serialization of the result, as for `convert` |

Exit code 0 where the document was written, 1 where the input made the run refuse, 2 where the
input is not an ESJ document, and 4 where this build carries no registry of that edition or no
mapping between the two.

Nothing is repaired, rounded or invented. A component the target edition requires and the
document has not, a value whose decimals exceed what the target edition allows, a specification
identifier in BT-24 that names a specification of the other edition: each is reported as an open
point and none is silently changed. Downwards, a value the target edition has no address for
makes the run refuse until the caller names its path with `--drop`. A path whose occurrence
index the document's own edition does not give it makes the run refuse as well: writing the
address the target edition wants would repair an invalid document on the way, and `validate`
answers that question first. `conformance/editions/upgrade.md` is the measurement over the
whole corpus.

An upgrade changes the semantic digest by design: the digest covers `semanticModel`, and the
two documents are two statements about two different models.

## Separability

The registry of the 2026 edition carries the facts an implementation needs, in this project's
words and without the standard's text; no official validation artefact and no public syntax
binding exist for that edition yet, and whether the licence agreement between the European
Commission and CEN extends to it has not been published (`NOTICE`). The files that carry facts
of that edition are therefore separable from the rest, for a build that wants none of them. `model/en16931/2026.paths` lists
every one of them — the registry, the generated schema, the generated typed view, the upgrade
mapping, the examples, the fixtures, the manifest part of `conformance/fixtures/` and the
generated view of the C# binding — and the Maven profile leaves them out:

```text
mvn -B -P without-edition-2026 verify
```

```text
bin/without-edition-2026.sh
```

The script copies the tree without the listed files and runs `mvn -B -P without-edition-2026
clean verify` over the copy, which is what keeps the claim checked rather than asserted for the
Java build. It runs no binding of its own: the TypeScript and C# test commands are not part of
it. The machinery is not in the list and is not separable: the reader, the registry loader,
the generator, the upgrade engine and the command line work with whatever registries are
present, and an edition is data they load rather than code they contain.

## What works for a 2026 document today

Per layer, for a build that carries the registry of that edition:

| Layer | What it does | Word |
|---|---|---|
| Format and specification | the edition identifier, the `Time` grammar and the finding code `ESJ-L2-TIME` are in `SPEC.md` | complete |
| Registry | the 255 terms of the edition, 216 BT and 39 BG, the decimal bound of a term among the facts it records | complete |
| Reader, canonicalizer, digests | nothing was added: these layers read no registry | complete |
| Structural validation L1 to L3 | measured against the registry of the edition the document names | complete |
| Typed view, editing | `…typed.v2026`, generated from that registry | complete |
| Constrained builder, `derive()` | `…typed.build` is generated for the default edition alone, and no profile overlay is written for another | not in this version |
| Domain API (`esj-invoice`) | built on that builder, with enums and profile defaults that are facts of the default edition | not in this version |
| `upgrade`, both directions | the mapping as data, with the open points reported | complete |
| Rendering to PDF | driven by the registry: designed sections for the groups it knows, a generic one for the rest | partial by design |
| Business rules | no pack is written for the edition, so `validate` reports `no-pack-for-edition` and reaches no verdict | not in this version |
| UBL, CII, the HTML page | refused, exit 4: no public authoritative mapping of the terms the edition adds | not available |

Where a build carries that registry, ESJ can represent, read, canonicalize,
hash, structurally validate, type-safely edit and upgrade a document of EN 16931-1:2026. It
cannot convert one to UBL or to CII, and no official validation artefact exists for the rules of
that edition — so no implementation of them can be measured against one, this project's
included.
