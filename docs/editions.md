# Editions

*Part of [EN16931 Semantic JSON](../README.md).*

EN 16931-1 has more than one edition, and a semantic path is an address relative to one.
`/BT-20` is the payment terms at the root of a 2017 document. In a 2026 one that term sits in a
group, and `/BT-20` is no path at all. Every document names its edition in `semanticModel`,
every registry describes exactly one, and no tool reads a document by the registry of another
(`SPEC.md`, sections 4.4 and 10).

Ask a build which editions it carries:

```text
esj --version
```

prints `semantic model registries 2017, 2026 (preview)` for a build that carries both. The
default, the edition the tool writes when you ask for no other, is 2017: the official validation
artefacts, XRechnung 3.0.2 and Peppol BIS 3 are written for it. **2026 is a preview**: its
registry, typed view, rule pack and `upgrade` may change in any minor release (`@Preview` in the
Java API). `validate`, `inspect`, `upgrade` and the report say so for a 2026 document.

## What each command does with a document of a non-default edition

| Command | A registry of that edition is there | No registry of that edition |
|---|---|---|
| `convert --to esj`, `canonicalize`, `list`, `diff` | works; these are edition-blind | works |
| `get` | works; a path that addresses nothing is explained against that edition | works, without the explanation |
| `inspect` | works; the `Semantic model` line names the edition | works, and the line says no registry is carried |
| `validate` | L1 to L3 run against that registry; the business rules run where a pack is written for that edition (`esj --list-packs`) | model layers not evaluated, verdict `INDETERMINATE` |
| `render` | works; the PDF layout is driven by that registry | refuses, exit 4 |
| `render --html` | refuses, exit 4 | refuses, exit 4 |
| `convert --to cii`, `convert --to ubl` | refuses, exit 4 | refuses, exit 4 |
| `upgrade` | writes the document as another edition | refuses, exit 4 |

`diff` compares two documents whichever editions they name. It notes on the standard error
where the two editions differ, because a path they place differently reads as a difference of
the invoice. A refusal names both editions, and the way out where this build has one. ESJ never
drops a value to make a document fit. A binding table maps the business terms of one edition
onto one syntax version. Writing a document of another edition through it would place the terms
the two editions share and lose the rest.

Three components are written for one edition and say so instead of growing quietly:

| Component | Written for | Why |
|---|---|---|
| The binding tables and the CII and UBL writers | 2017 | no public authoritative mapping of the terms a later edition adds; [`bindings.md`](bindings.md) |
| The vendored XRechnung visualization (`--html`) | 2017 | the stylesheets have nowhere to put a term of a later edition |
| The rule pack `en16931/1.3.17` | 2017 | the rules of the standard are renumbered, added to and withdrawn between editions; the pack `en16931-2026/0.1` is the one of the later edition |

The PDF rendering is this project's own layout, driven by whatever registry the document's
edition brings. It draws designed sections for the groups it knows, and a final heading under
which every other value stands with its label and its semantic path. A test of `esj-render`
checks that every value of a document of every carried edition reaches the page.

## upgrade

```text
esj upgrade invoice.esj.json --to 2026 --out invoice-2026.esj.json
```

What changes between two editions is data in `model/en16931/upgrade-2017-2026.json`, and the
engine reads it. Nothing about a pair of editions is written in Java. The mapping moves the
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

Exit codes: 0 when the document was written, 1 when the input made the run refuse, 2 when the
input is not an ESJ document, 4 when this build carries no registry of that edition or no
mapping between the two.

`upgrade` repairs, rounds and invents nothing. It reports each of these as an open point and
changes none silently: a component the target edition requires and the document has not, a value
whose decimals exceed what the target edition allows, and a specification identifier in BT-24
that names a specification of the other edition. A bound the edition states over the minor unit
of the currency is evaluated in the currency the value is written in: the invoice currency
(BT-5), the VAT accounting currency (BT-6) for BT-111, the currency a VAT breakdown names
(BT-184) for its amounts. The ISO 4217 snapshot of the rule pack of that edition supplies the
minor units; a build without the pack reports the bound as not evaluated.

Downwards, a value the target edition has no address for makes the run refuse until you name its
path with `--drop`. A path whose occurrence index the document's own edition does not give it
makes the run refuse as well. Writing the address the target edition wants would repair an
invalid document on the way, and `validate` answers that question first.
`conformance/editions/upgrade.md` is the measurement over the whole corpus. An upgrade changes
the semantic digest by design. The digest covers `semanticModel`, and the two documents are
statements about two different models.

## Separability

The registry of the 2026 edition carries the facts an implementation needs, in this project's
words and without the standard's text. No official validation artefact and no public syntax
binding exist for that edition yet. Nobody has published whether the licence agreement between
the European Commission and CEN extends to it (`NOTICE`). The files that carry its facts are
therefore separable: `model/en16931/2026.paths` lists every one, and the Maven profile leaves
them out. They are the registry, the generated schema, the generated typed view, the upgrade
mapping, the examples, the fixtures, the rule pack with its rules in Java, TypeScript and C# and
its measurements, the manifest part of `conformance/fixtures/` and the generated view of the
C# binding:

```text
mvn -B -P without-edition-2026 verify
bin/without-edition-2026.sh
```

The script copies the tree without the listed files and runs `mvn -B -P without-edition-2026 clean
verify` over the copy. Then it runs the tests and the fixture runner of both bindings where npm
and dotnet are on the PATH. `--bindings` runs the bindings alone, as the CI does on every push.
`--keep` keeps the copy, which is otherwise removed. The machinery is not in the list and is not
separable. The reader, the registry loader, the generator, the upgrade engine and the command
line work with whatever registries are present; an edition is data they load, not code.

## What works for a 2026 document today

Per layer, for a build that carries the registry of that edition:

| Layer | What it does | Word |
|---|---|---|
| Format and specification | the edition identifier, the `Time` grammar and the finding code `ESJ-L2-TIME` are in `SPEC.md` | complete |
| Registry | the 255 terms of the edition, 216 BT and 39 BG, the decimal bound of a term among the facts it records | complete |
| Reader, canonicalizer, digests | nothing was added: these layers read no registry | complete |
| Structural validation L1 to L3 | measured against the registry of the edition the document names | complete |
| Typed view, editing | `…typed.v2026`, generated from that registry | complete |
| Totals, `derive()` | `…typed.v2026.Totals.of(En16931V2026Pack.minorUnits())`: each amount rounded once, to the minor unit of its currency in the pack's snapshot; an unknown currency is refused | complete |
| Constrained builder | `…typed.build` is generated for the default edition alone, and no profile overlay is written for another | not in this version |
| Domain API (`esj-invoice`) | built on that builder, with enums and profile defaults that are facts of the default edition | not in this version |
| `upgrade`, both directions | the mapping as data, with the open points reported | complete |
| Rendering to PDF | driven by the registry: designed sections for the groups it knows, a generic one for the rest | partial by design |
| Business rules | the pack `en16931-2026/0.1`: 252 rules, of which 199 are those of clause 6.4, 194 of them implemented, with the fraction digit rules of Table 28 and the code list rules beside them. 108 rules the edition leaves unchanged have the oracle `downgrade`: over 454 mutations written back to 2017, 100 are named alike by the official Schematron 1.3.17, 7 differ exactly as in the default pack, 1 is exercised by none, 0 are open. 144 changed or new rules have `cases`: hand-computed cases only | implemented; not corroborated by an official artefact |
| The official artefacts over a written syntax | no artefact release is written against the edition and no syntax binding of the terms it adds is published, so `validate` reports `no-artefacts-for-edition` and reaches no verdict | not available |
| UBL, CII, the HTML page | refused, exit 4: no public authoritative mapping of the terms the edition adds | not available |

With that registry in the build, you can represent, read, canonicalize, hash, structurally
validate, type-safely edit, upgrade a document of EN 16931-1:2026, derive its totals and check
its business rules. You cannot convert one to UBL or to CII. **No official validation artefact
exists for the rules of that edition**, so no implementation of them can be measured against
one, this project's included. A document of the edition reaches `INVALID` where a rule faults
it and `INDETERMINATE` otherwise, never `VALID`. Each rule says what stands behind it.
[`conformance/rules-2026/coverage.md`](../conformance/rules-2026/coverage.md) counts the kinds,
and [`ledger.md`](../conformance/rules-2026/ledger.md) is what each kind was measured by.
