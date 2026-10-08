# C# binding

*Part of [EN16931 Semantic JSON](../README.md).*

`bindings/csharp/` reads, canonicalizes, digests and validates ESJ documents on .NET 8 with no
dependency beyond the base class library, and runs the rule pack of
[`rules/`](../rules/README.md) unchanged. It implements [`SPEC.md`](../SPEC.md) rather than
wrapping the Java library: the registries of `model/` and the pack of `rules/` are read as
data, and the fixture manifest of
[`conformance/fixtures/`](../conformance/fixtures/README.md) measures both implementations
against the same material. The syntax binding tables for UBL and CII are a different subject
and live in [`bindings.md`](bindings.md).

## Build and test

```console
$ dotnet test bindings/csharp/BSNSoft.Esj.sln
```

Without a .NET SDK installed, in the official image, with the checkout mounted:

```console
$ docker run --rm -v "$PWD:/repo:ro" -v "$PWD/bindings/csharp:/repo/bindings/csharp" \
    -w /repo/bindings/csharp mcr.microsoft.com/dotnet/sdk:8.0 dotnet test
```

The tests read the fixtures from the checkout the build recorded, and `ESJ_REPOSITORY` points
them at another one. [`bindings/csharp/README.md`](../bindings/csharp/README.md) is the
package's own page.

| Project | What it is |
|---|---|
| `BSNSoft.Esj` | the library and package id: net8.0, warnings as errors, nullable enabled; the version is read from `pom.xml` |
| `BSNSoft.Esj.Tests` | xunit; every case of the fixture manifest is one test |
| `BSNSoft.Esj.Fixtures` | the request protocol of `conformance/fixtures/run.py` |

## The API

```csharp
SemanticDocument invoice = EsjReader.Strict().Read(File.ReadAllBytes("invoice.esj.json"));

string digest = Canonicalizer.SemanticDigest(invoice);
byte[] canonical = Canonicalizer.CanonicalBytes(invoice);

ValidationResult result = Validator.Validate(invoice);
foreach (Finding finding in result.Findings)
{
    Console.WriteLine($"{finding.Code} {finding.Path}: {finding.Message}");
}

Invoice view = new(invoice);
BigDecimal total = view.DocumentTotals.AmountDueForPayment!.AsDecimal();
```

`Read` rejects a byte sequence that fails layer L1 and carries the finding code of `SPEC.md`
section 9.6 in an `EsjFormatException`; `ReadWithFindings` reports instead and returns what it
could build. A limit of section 12.2 is an `EsjLimitException` and a finding with the code
`ESJ-L1-LIMIT`, never a verdict on the document, and `Limits.Defaults.ToBuilder()` configures
the bounds; a bound past `Limits.MaxDocumentBytesBound` or `Limits.MaxExtensionDepthBound` is
refused when it is given. `Validator.Validate` answers `VALID`, `INVALID` or `INDETERMINATE` with
the components that did not run.

Both exceptions carry the `Code`, `Path` and `Subject` of the finding of the same defect. A subject
is the member access of `SPEC.md` section 9.5, whole: `format`, `source.syntax`,
`values["/BG-4/BT-29/0"].scheme`, `values["/BT-1"]["foo"]`, `extensions["de.example"]["a"][1]`,
`["profile"]` — a name the specification defines after a dot, a name the document chose in
brackets, escaped, a lone surrogate as `\ud800`. `ESJ-L1-JSON` and `ESJ-L1-ENCODING` carry neither
path nor subject; their message names the byte offset of the token. `SemanticValue` normalizes the
line endings of every string it is built with (section 6.8), as the reader does.

Every number is a `BigDecimal` of this binding: an arbitrary-precision unscaled value on
`System.Numerics.BigInteger` and a scale. The runtime's own `decimal` is not used anywhere,
because the decimal grammar of section 6.4 admits sixty-four characters and that type holds
twenty-eight digits; no value of a document passes through binary floating point at any point.

## Business rules

```csharp
RuleEngine pack = En16931Pack.Engine(Registry.En16931().WithExtension(Registry.XRechnungExtension()));
IReadOnlyList<RuleFinding> findings = pack.Evaluate(invoice);
```

The pack is `rules/en16931/1.3.16` as data: 189 rules in the JSON rule language of
[`rules/README.md`](../rules/README.md) and 28 the language cannot express, written in C# under
the names the manifest declares. Findings of the pack are a layer of their own and never ESJ
conformance (`SPEC.md` section 9.4). `Evaluate` refuses a document of an edition other than the
one the pack was compiled against, because a path is an address relative to an edition.
`RulePacks.Read` reads a pack of the rule language from a stream — one whose rules stand in that
one file, such as a pack a caller wrote — and `RuleEngine.Compile` compiles it the same way.

## What is covered

| Class of `SPEC.md` section 3 | Here |
|---|---|
| Reader (3.2) | `EsjReader`, with the limits of section 12.2 |
| Writer (3.3) | `EsjWriter`, the canonical and the pretty form |
| Canonicalizer (3.4) | `Canonicalizer`, both digests over the canonical bytes |
| Validator (3.5) | `Validator`, `StructuralValidator`, layers L1 to L3, tri-state result |

Every registry under `model/en16931/` this build carries is offered by `Registry.Editions()`, with
the XRechnung 3.0.2 and B2C 0.1 extension registries combined into the default one. A later
edition is separable the way [`editions.md`](editions.md) describes, and this binding builds
without its files; a document of an edition it has no registry for is read, canonicalized and
digested like any other and reports `ESJ-L2-EDITION-UNKNOWN` (`SPEC.md` sections 4.4 and 9.2).

`Typed/Invoice2017.cs` and `Typed/Invoice2026.cs` are the generated read views, checked in and
held to the registry by a test, so a stale file fails the build:

```console
$ python3 bindings/csharp/tools/generate-typed-view.py 2017 2026
```

Not here: the writers to UBL and CII, PDF, rendering, `esj upgrade` and the command line. Those
are the Java implementation ([`cli.md`](cli.md)).

## The fixture manifest

```console
$ python3 conformance/fixtures/run.py --binding dotnet run --project bindings/csharp/BSNSoft.Esj.Fixtures -- .
```

`BSNSoft.Esj.Fixtures` answers the six requests of the runner over a pipe: the
digests, the canonical bytes, the findings with severity, status and the layers not evaluated, the
rule identifiers, and whether a set of registries is accepted. A `validate` request may carry
`limits` under the names of `Limits` (`maxStringBytes`, `maxExtensionDepth`, …). The same cases run as xunit tests, one test per case, which is what `dotnet test`
reports, so a case that the reference implementation writes into the manifest fails here until
this binding answers it too. The job `bindings` of the CI runs `dotnet test` and the runner on
every push.

Author: Christian Bürckert.
