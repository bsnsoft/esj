# EN16931 Semantic JSON — TypeScript

*Part of [EN16931 Semantic JSON](../../README.md). The format is [`SPEC.md`](../../SPEC.md); this
package is one implementation of it. Release candidate; format version 0.1. The package name below is
provisional and nothing is published on a registry yet.*

Reader, canonical form, digests, validator (L1–L3) and the semantic rule engine, in TypeScript,
with no runtime dependency. It implements the specification rather than wrapping the Java
library: the same registries and the same rule pack are read as data, and the shared fixture
manifest measures both.

```ts
import { readDocumentOrThrow, canonicalize, semanticDigest, validate } from 'en16931-semantic-json';
import { registries } from 'en16931-semantic-json/node';

const document = readDocumentOrThrow(await readFile('invoice.esj.json'));
document.values.get('/BG-25/0/BT-131');           // { value: '1080' }
canonicalize(document);                            // the one byte sequence of this content
await semanticDigest(document);                    // 64 lowercase hexadecimal digits

const result = validate(await readFile('invoice.esj.json'), { registries: registries() });
result.status;                                     // VALID | INVALID | INDETERMINATE
result.findings;                                   // path, subject, code, severity, message
```

## The typed view

Generated from the registry, with the name stems the registry records, so an invoice reads by
the names of the model and not by identifiers:

```ts
import { invoiceOf } from 'en16931-semantic-json/generated/en16931-2017/view';

const invoice = invoiceOf(document);
invoice.seller().name();                           // string
invoice.invoiceLines()[0].netAmount();             // Decimal 1080, exact
invoice.documentTotals().amountDueForPayment();    // Decimal, exact
```

One view per edition — `en16931-2017` and `en16931-2026` — because a path is an address
relative to the edition the document names.

## Business rules

The rules of EN 16931 are a layer of their own and never ESJ conformance
([`SPEC.md`](../../SPEC.md) section 9.4). The pack of the repository runs here unchanged:

```ts
import { Structure } from 'en16931-semantic-json';
import { registries, ruleEngine } from 'en16931-semantic-json/node';

const carried = registries();
const core = carried.find((registry) => registry.semanticModel === document.semanticModel)!;
const engine = ruleEngine(new Structure(core, carried.filter((r) => r.isExtension())));
engine.evaluate(document);                         // rule, severity, context, message, pack
```

217 rules: 189 read from `rules/en16931/1.3.16/`, and the 28 the rule language cannot express
written in `src/rules/native.ts` under the same identifiers. The pack names them and does not
load them; `compile` refuses a pack whose declared set and the rules handed to it differ, and
`evaluate` refuses a document of an edition other than the one the pack was compiled against,
because a path is an address relative to an edition.

## What is here

| Module | What it does |
|---|---|
| `src/json/parse.ts` | a JSON parser that keeps duplicate members and the spelling of a number, and enforces the limits while it reads |
| `src/reader.ts` | layer L1: the envelope, the paths, the shape of every value |
| `src/canonical.ts` | the canonical form, the pretty form, the lexical canonical form of a number |
| `src/digest.ts` | the semantic digest and the document digest, over canonical bytes |
| `src/registry.ts`, `src/structure.ts` | the registries, their parent chains and the children of a group |
| `src/validate.ts` | layers L2 and L3, and the three states a result has |
| `src/rules/` | the JSON rule language, its engine, and the rules written here |
| `src/typed/runtime.ts` | what the generated view is built from |
| `src/decimal.ts` | exact decimals |

## Decimals

No binary floating point takes part anywhere — not in `values`, not inside `extensions`, not
in the canonical form, not in the digests, not in a rule. `Decimal` is an unscaled `bigint`
with a scale, so `0.1 + 0.2` is `0.3` and `1.0000000000000001` keeps its digits. Division is
exact where the quotient terminates and is computed to 34 fraction digits, half away from zero,
where it does not; `round` is the only operator that rounds to a scale the caller chooses, and
it rounds half away from zero too.

This is why the package has no runtime dependency. A decimal library would have been the one,
and the arithmetic the specification asks for — add, subtract, multiply, one named division
precision, one rounding mode, an exact comparison — is small, fully specified, and measured by
the same fixtures as everything else.

## Building and testing

```sh
npm install
npm test          # copies the registries and the pack, generates the view, runs node:test
npm run build     # the same, then tsc to dist/
npm run fixtures  # the manifest through the language-neutral runner
```

`npm test` runs the whole fixture manifest of [`conformance/fixtures/`](../../conformance/fixtures/README.md)
in process: 98 conformant documents with their two digests, their canonical byte length, the
registries they were measured with and the 46 cardinality findings two of them draw;
45 rows for the documents that have to be rejected, each with its finding code and the path it
names; the canonical bytes of two scrambled documents; the accept and reject tables of the value
grammars; 448 mutations of the conformance corpus against the rule pack; and the rules of the
pack that pins division. One document and one rejected row of those counts come from the part of
the manifest that carries the later edition, which a build without that edition leaves out. The
language-neutral runner answers the same manifest over a pipe.

`data/` holds copies of `model/` and `rules/` taken by `scripts/sync-data.mjs` before every
build and every test run. Nothing there is edited, and it is not checked in: a binding that
hand-maintained a registry would be measuring documents against its own idea of the model.
`src/generated/` is written by `scripts/generate-typed.ts` from those copies and is not checked
in either.

## Licence

Apache-2.0, with the repository's [NOTICE](../../NOTICE).
