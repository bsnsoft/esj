# EN16931 Semantic JSON — TypeScript

*Part of [EN16931 Semantic JSON](../../README.md). The format is [`SPEC.md`](../../SPEC.md); this
package is one implementation of it. Format version 1.0. The package is
`@bsnsoft/esj`, versioned with the project; nothing is published on a registry yet.*

Read, canonicalize, digest and validate (L1–L3) ESJ documents and run the semantic rule engine
in TypeScript, with no runtime dependency. The package implements the specification instead of
wrapping the Java library. It reads the same registries and the same rule pack as data, and the
shared fixture manifest measures both.

```ts
import { readDocumentOrThrow, canonicalize, semanticDigest, validate } from '@bsnsoft/esj';
import { registries } from '@bsnsoft/esj/node';

const document = readDocumentOrThrow(await readFile('invoice.esj.json'));
document.values.get('/BG-25/0/BT-131');           // { value: '1080' }
canonicalize(document);                            // the one byte sequence of this content
await semanticDigest(document);                    // 64 lowercase hexadecimal digits

const result = validate(await readFile('invoice.esj.json'), { registries: registries() });
result.status;                                     // VALID | INVALID | INDETERMINATE
result.findings;                                   // path, subject, code, severity, message
```

A document built here is of format version 1.0. A document of 0.1, the version written before
1.0.0-rc.1, is read by the same rules and keeps its version. Its canonical bytes and both digests
are the ones it always had. Any other `version` is `ESJ-L1-ENVELOPE-VALUE`.

## The typed view

Read an invoice by the names of the model, from a view generated with the registry's name stems:

```ts
import { invoiceOf } from '@bsnsoft/esj/generated/en16931-2017/view';

const invoice = invoiceOf(document);
invoice.seller().name();                           // string
invoice.invoiceLines()[0].netAmount();             // Decimal 1080, exact
invoice.documentTotals().amountDueForPayment();    // Decimal, exact
```

You get one view per edition, `en16931-2017` and `en16931-2026`, because a path is an address
relative to the edition the document names.

## Business rules

The pack of the repository runs here unchanged. The rules of EN 16931 are a layer of their own
and never ESJ conformance ([`SPEC.md`](../../SPEC.md) section 9.4).

```ts
import { Structure } from '@bsnsoft/esj';
import { registries, ruleEngine } from '@bsnsoft/esj/node';

const carried = registries();
const core = carried.find((registry) => registry.semanticModel === document.semanticModel)!;
const engine = ruleEngine(new Structure(core, carried.filter((r) => r.isExtension())));
engine.evaluate(document);                         // rule, severity, context, message, pack
```

217 rules: 189 read from `rules/en16931/1.3.17/`, and the 28 the rule language cannot express
written in `src/rules/native.ts` under the same identifiers. The pack names them and does not
load them. `compile` refuses a pack whose declared set differs from the rules handed to it.
`evaluate` refuses a document of an edition other than the one the pack was compiled against.
`ruleEngine` compiles the pack written for the edition of the structure. For the later edition
that is `rules/en16931-2026/0.1/`; its rules the language cannot express are
`src/rules/native-2026.ts`, left out with that edition.

## What is here

| Module | What it does |
|---|---|
| `src/json/scanner.ts` | a JSON scanner that keeps duplicate members and the spelling of a number, walks past what the reader refuses without building it, and enforces the limits while it reads |
| `src/reader.ts` | layer L1: the envelope, the paths, the shape of every value; every finding names its member as section 9.5 writes a member access |
| `src/canonical.ts` | the canonical form, the pretty form, the lexical canonical form of a number |
| `src/digest.ts` | the semantic digest and the document digest, over canonical bytes |
| `src/registry.ts`, `src/structure.ts` | the registries, checked as section 10 checks one read or combined, their parent chains and the children of a group |
| `src/validate.ts` | layers L2 and L3, and the three states a result has |
| `src/rules/` | the JSON rule language, its engine, and the rules written here |
| `src/typed/runtime.ts` | what the generated view is built from |
| `src/decimal.ts` | exact decimals |

## Decimals

No binary floating point takes part anywhere: not in `values`, not inside `extensions`, not in
the canonical form, not in the digests, not in a rule. `Decimal` is an unscaled `bigint` with a
scale, so `0.1 + 0.2` is `0.3` and `1.0000000000000001` keeps its digits. Division is exact where
the quotient terminates. Where it does not, it is computed to 34 fraction digits, half away from
zero. `round` is the only operator that rounds to a scale you choose, and it rounds half away
from zero too.

That is why the package has no runtime dependency: a decimal library would have been the one.
The arithmetic the specification asks for (add, subtract, multiply, one named division precision,
one rounding mode, an exact comparison) is small, fully specified and measured by the fixtures.

## Building and testing

```sh
npm install
npm test          # copies the registries and the pack, generates the view, runs node:test
npm run build     # the same, then tsc to dist/
npm run fixtures  # the manifest through the language-neutral runner
```

`npm test` runs the whole fixture manifest of [`conformance/fixtures/`](../../conformance/fixtures/README.md) in process:

- 104 documents a reader reads, with their two digests, their canonical byte length, the
  registries they were measured with and the whole outcome a validator gives about each;
- 141 documents that have to be rejected, each with that outcome;
- documents read under bounds below the defaults, and sets of registries a loader takes or refuses;
- the canonical bytes of documents not in canonical form, and the accept and reject tables of the
  value and envelope grammars;
- 448 mutations of the conformance corpus against the rule pack, 314 cases of the later edition's
  pack, and the rules of the pack that pins division.

Two documents, one rejected document and the 314 cases come from the part of the manifest that
carries the later edition, which a build without that edition leaves out. The language-neutral
runner answers the same manifest over a pipe.

`scripts/sync-data.mjs` copies `model/` and `rules/` into `data/` before every build and test run.
Nothing there is edited or checked in: a binding that hand-maintained a registry would measure
documents against its own idea of the model. `scripts/generate-typed.ts` writes `src/generated/`
from those copies, also not checked in.

## Licence

Apache-2.0, with the repository's [NOTICE](../../NOTICE).
