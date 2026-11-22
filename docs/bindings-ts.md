# TypeScript binding

*Part of [EN16931 Semantic JSON](../README.md).*

With `bindings/typescript/` you read, canonicalize, digest and validate ESJ documents in
TypeScript, with no runtime dependency. It runs the rule pack of [`rules/`](../rules/README.md)
unchanged. It implements [`SPEC.md`](../SPEC.md) instead of wrapping the Java library: it reads the
registries of `model/` and the pack of `rules/` as data. The fixture manifest of
[`conformance/fixtures/`](../conformance/fixtures/README.md) measures both implementations against
the same material. The syntax binding tables for UBL and CII are another subject, on
[`bindings.md`](bindings.md).

## Build and test

```sh
npm install
npm test          # copies the registries and the pack, generates the view, runs node:test
npm run build     # the same, then tsc to dist/
npm run fixtures  # the manifest through the language-neutral runner
```

You need Node 22.18 or newer; TypeScript is the only development dependency. The package is
`@bsnsoft/esj`. Its version is the project's: `npm run sync-version` writes it from `pom.xml`, and
`npm test` checks it. The package is `private`, and nothing is published on a registry yet.
[`bindings/typescript/README.md`](../bindings/typescript/README.md) is the package's own page.

## The API

| Entry point | What it carries |
|---|---|
| `@bsnsoft/esj` | `readDocument`, `readDocumentOrThrow`, `canonicalize`, `pretty`, `semanticDigest`, `documentDigest`, `validate`, `Registry`, `Structure`, `compile`, `RuleEngine`, `Decimal`, `FindingCode`, the path helpers and the value grammars |
| `@bsnsoft/esj/node` | `registries()`, `rulePack()`, `ruleEngine()`, `codeListDays()` — the files of this repository, read from disk |
| `@bsnsoft/esj/generated/<edition>/view` | the read view generated from that edition's registry |

Nothing in the first entry point touches a file system or an environment. You pass in a document, a
registry and a rule pack as values. The second entry point is the one place that reads them from
disk.

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

`validate` answers `VALID`, `INVALID` or `INDETERMINATE`. A finding carries the path, the subject,
the code of `SPEC.md` section 9.6, its severity and a message. A limit of section 12.2 is a finding
with the code `ESJ-L1-LIMIT`; it leaves the result indeterminate, never invalid.

| Field | What it holds |
|---|---|
| `path` | the path of the member of `values` the finding is about, where its name is a path; empty for `ESJ-L1-JSON` |
| `subject` | the member access of section 9.5, never cut: `format`, `source.syntax`, `values["/BT-1"]`, `values["/BG-4/BT-29/0"].scheme`, `extensions["de.example"]["a"][1]`, `["profile"]`; a lone surrogate is written `\ud800`; at L2 and L3 the identifier or the component |
| `message` | English, quoted fragments escaped; an offset is in UTF-8 bytes from the start of the token |

`readDocumentOrThrow` raises `EsjError` with the code, the path and the subject of the first
finding. `canonicalize` and `pretty` sort and normalize the values themselves. `pretty` ends with
one LF and writes a number inside `extensions` in its canonical form.

## The typed view

```ts
import { invoiceOf } from '@bsnsoft/esj/generated/en16931-2017/view';

const invoice = invoiceOf(document);
invoice.seller().name();                           // string
invoice.invoiceLines()[0].netAmount();             // Decimal 1080, exact
invoice.documentTotals().amountDueForPayment();    // Decimal, exact
```

You get one view per edition, `en16931-2017` and `en16931-2026`, because a path is an address
relative to the edition a document names. `scripts/generate-typed.ts` writes both from the
registries, with the name stem each term carries. Neither the copies under `data/` nor the
generated sources are checked in.

## Business rules

```ts
import { Structure } from '@bsnsoft/esj';
import { registries, ruleEngine } from '@bsnsoft/esj/node';

const carried = registries();
const core = carried.find((registry) => registry.semanticModel === document.semanticModel)!;
const engine = ruleEngine(new Structure(core, carried.filter((r) => r.isExtension())));
engine.evaluate(document);                         // rule, severity, context, message, pack
```

The pack is `rules/en16931/1.3.17` as data: 189 rules in the JSON rule language of
[`rules/README.md`](../rules/README.md), and 28 the language cannot express. Those 28 are written
in `src/rules/native.ts` under the identifiers the manifest declares. Findings of the pack are a
layer of their own and never ESJ conformance (`SPEC.md` section 9.4). `compile` refuses a pack
whose declared set differs from the rules handed to it. `evaluate` refuses a document of an edition
other than the one the pack was compiled against.

## What is covered

| Class of `SPEC.md` section 3 | Here |
|---|---|
| Reader (3.2) | `src/reader.ts` on `src/json/scanner.ts`, streaming, with the limits of section 12.2 |
| Writer (3.3) | `src/canonical.ts`: the canonical and the pretty form |
| Canonicalizer (3.4) | `src/canonical.ts`, `src/digest.ts`: both digests over the canonical bytes |
| Validator (3.5) | `src/validate.ts`: layers L1 to L3 and the tri-state result |

The binding carries every registry under `model/en16931/` this build copies, with the XRechnung
3.0.2 and B2C 0.1 extension registries beside the default edition. `registryOf` and `new Structure`
make the checks of `SPEC.md` section 10 and throw `RegistryError`: components a value can satisfy,
no identifier twice, an extension that defines identifiers of its own namespace only, and an
extension combined only with the edition its `imports` names.

A document of an edition the build has no registry for is read, canonicalized and digested like any
other. It reports `ESJ-L2-EDITION-UNKNOWN` instead of a model layer (`SPEC.md` sections 4.4 and
9.2).

Not here: the writers to UBL and CII, PDF, rendering, `esj upgrade` and the command line. Those
are the Java implementation ([`cli.md`](cli.md)).

No binary floating point takes part anywhere: not in `values`, not inside `extensions`, not in the
canonical form, not in a digest, not in a rule. `Decimal` is an unscaled `bigint` with a scale.
Division is exact where the quotient terminates. Where it does not, it is computed to
`DIVISION_SCALE` = 34 fraction digits, half away from zero. `round` is the only operator that
rounds to a scale the caller names.

## The fixture manifest

```sh
npm run fixtures
```

starts `tools/fixture-binding.ts`, which answers the requests of `conformance/fixtures/run.py` over
a pipe. It answers with the digests, the canonical bytes, the status, the layers not evaluated and
every finding with its severity, under the bounds a request names in `limits` and with the
registries it names in `registries`. It gives the rule identifiers of every case of the manifest.
For `{"op": "registry", "files": [...]}` it says whether those registry files are accepted, every
file after the first combined with the first. Where the part of the later edition is present, the
runner makes 1485 checks. `npm test` runs the same manifest in process: a case that the reference
implementation writes into the manifest fails here until this binding answers it too. The job
`bindings` of the CI runs `npm ci`, `npm test` and the runner on every push.

Author: Christian Bürckert.
