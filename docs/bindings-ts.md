# TypeScript binding

*Part of [EN16931 Semantic JSON](../README.md).*

`bindings/typescript/` reads, canonicalizes, digests and validates ESJ documents in TypeScript
with no runtime dependency, and runs the rule pack of [`rules/`](../rules/README.md) unchanged.
It implements [`SPEC.md`](../SPEC.md) rather than wrapping the Java library: the registries of
`model/` and the pack of `rules/` are read as data, and the fixture manifest of
[`conformance/fixtures/`](../conformance/fixtures/README.md) measures both implementations
against the same material. The syntax binding tables for UBL and CII are a different subject
and live in [`bindings.md`](bindings.md).

## Build and test

```sh
npm install
npm test          # copies the registries and the pack, generates the view, runs node:test
npm run build     # the same, then tsc to dist/
npm run fixtures  # the manifest through the language-neutral runner
```

Node 22.18 or newer, TypeScript as the only development dependency. The package is `private`
and nothing is published on a registry yet;
[`bindings/typescript/README.md`](../bindings/typescript/README.md) is the package's own page.

## The API

| Entry point | What it carries |
|---|---|
| `en16931-semantic-json` | `readDocument`, `readDocumentOrThrow`, `canonicalize`, `pretty`, `semanticDigest`, `documentDigest`, `validate`, `Registry`, `Structure`, `compile`, `RuleEngine`, `Decimal`, `FindingCode`, the path helpers and the value grammars |
| `en16931-semantic-json/node` | `registries()`, `rulePack()`, `ruleEngine()`, `codeListDays()` — the files of this repository, read from disk |
| `en16931-semantic-json/generated/<edition>/view` | the read view generated from that edition's registry |

Nothing in the first entry point touches a file system or an environment: a document, a
registry and a rule pack are values the caller passes in, and the second entry point is the
one place that reads them from disk.

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

`validate` answers `VALID`, `INVALID` or `INDETERMINATE`, and a finding carries the path, the
subject, the code of `SPEC.md` section 9.6, its severity and a message. A limit of section 12.2
is a finding with the code `ESJ-L1-LIMIT` and leaves the result indeterminate, never invalid.

## The typed view

```ts
import { invoiceOf } from 'en16931-semantic-json/generated/en16931-2017/view';

const invoice = invoiceOf(document);
invoice.seller().name();                           // string
invoice.invoiceLines()[0].netAmount();             // Decimal 1080, exact
invoice.documentTotals().amountDueForPayment();    // Decimal, exact
```

One view per edition — `en16931-2017` and `en16931-2026` — because a path is an address
relative to the edition a document names. `scripts/generate-typed.ts` writes both from the
registries, with the name stem each term carries, and neither the copies under `data/` nor the
generated sources are checked in.

## Business rules

```ts
import { Structure } from 'en16931-semantic-json';
import { registries, ruleEngine } from 'en16931-semantic-json/node';

const carried = registries();
const core = carried.find((registry) => registry.semanticModel === document.semanticModel)!;
const engine = ruleEngine(new Structure(core, carried.filter((r) => r.isExtension())));
engine.evaluate(document);                         // rule, severity, context, message, pack
```

The pack is `rules/en16931/1.3.16` as data: 189 rules in the JSON rule language of
[`rules/README.md`](../rules/README.md) and 28 the language cannot express, written in
`src/rules/native.ts` under the identifiers the manifest declares. Findings of the pack are a
layer of their own and never ESJ conformance (`SPEC.md` section 9.4). `compile` refuses a pack
whose declared set and the rules handed to it differ, and `evaluate` refuses a document of an
edition other than the one the pack was compiled against.

## What is covered

| Class of `SPEC.md` section 3 | Here |
|---|---|
| Reader (3.2) | `src/json/parse.ts`, `src/reader.ts`, with the limits of section 12.2 |
| Writer (3.3) | `src/canonical.ts`: the canonical and the pretty form |
| Canonicalizer (3.4) | `src/canonical.ts`, `src/digest.ts`: both digests over the canonical bytes |
| Validator (3.5) | `src/validate.ts`: layers L1 to L3 and the tri-state result |

Every registry under `model/en16931/` this build copies is carried, with the XRechnung 3.0.2 and
B2C 0.1 extension registries beside the default edition. A document of an edition the build has no
registry for is read, canonicalized and digested like any other and reports
`ESJ-L2-EDITION-UNKNOWN` instead of a model layer (`SPEC.md` sections 4.4 and 9.2).

Not here: the writers to UBL and CII, PDF, rendering, `esj upgrade` and the command line. Those
are the Java implementation ([`cli.md`](cli.md)).

No binary floating point takes part anywhere — not in `values`, not inside `extensions`, not in
the canonical form, not in a digest, not in a rule. `Decimal` is an unscaled `bigint` with a
scale; division is exact where the quotient terminates and is computed to `DIVISION_SCALE` = 34
fraction digits, half away from zero, where it does not, and `round` is the only operator that
rounds to a scale the caller names.

## The fixture manifest

```sh
npm run fixtures
```

starts `tools/fixture-binding.ts` and answers the six requests of
`conformance/fixtures/run.py` over a pipe: the digests, the canonical bytes, the findings and
the rule identifiers of every case of the manifest, 761 of them where the part of the later
edition is present. `npm test` runs the same manifest in process, so a case that the reference
implementation writes into the manifest fails here until this binding answers it too.

Author: Christian Bürckert.
