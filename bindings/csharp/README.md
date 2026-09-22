# EN16931 Semantic JSON — C# binding

*Part of [EN16931 Semantic JSON](../../README.md). The format is [`../../SPEC.md`](../../SPEC.md);
this page is the .NET implementation of it. The assembly and namespace name below is provisional
and nothing is published on a package registry yet.*

Status: reader, canonicalizer, digests, structural validation (L1–L3), the JSON rule language of
[`../../rules/README.md`](../../rules/README.md) and a generated read view, measured against the
fixture manifest of [`../../conformance/fixtures/`](../../conformance/fixtures/README.md), which is
where the counts of that material stand.

## Build and test

```console
$ dotnet test bindings/csharp/En16931.SemanticJson.sln
```

Without a .NET SDK installed, in the official image, with the checkout mounted:

```console
$ docker run --rm -v "$PWD:/repo:ro" -v "$PWD/bindings/csharp:/repo/bindings/csharp" \
    -w /repo/bindings/csharp mcr.microsoft.com/dotnet/sdk:8.0 dotnet test
```

The tests read the fixtures from the checkout the build recorded; `ESJ_REPOSITORY` points them at
another one.

| Project | What it is |
|---|---|
| `En16931.SemanticJson` | the library: net8.0, no dependency beyond the base class library |
| `En16931.SemanticJson.Tests` | xunit; every case of the fixture manifest is one test |
| `En16931.SemanticJson.Fixtures` | the request protocol of `conformance/fixtures/run.py` |

## Reading, digesting, validating

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

Every number is a `BigDecimal` of this binding: an arbitrary-precision unscaled value on
`System.Numerics.BigInteger` and a scale. The runtime's own `decimal` is not used anywhere, because
the decimal grammar of section 6.4 admits sixty-four characters and that type holds twenty-eight
digits; no value of a document passes through binary floating point at any point.

`Read` rejects a byte sequence that fails layer L1 and carries the finding code of `SPEC.md`
section 9.6 in an `EsjFormatException`; `ReadWithFindings` reports instead and returns what it
could build. A limit of section 12.2 is an `EsjLimitException` and a finding with the code
`ESJ-L1-LIMIT`, never a verdict on the document (`Limits.Defaults.ToBuilder()` configures them).

## Business rules

```csharp
RuleEngine pack = En16931Pack.Engine(Registry.En16931().WithExtension(Registry.XRechnungExtension()));
IReadOnlyList<RuleFinding> findings = pack.Evaluate(invoice);
```

The pack is `rules/en16931/1.3.16` as data: 189 rules in the JSON rule language and 28 the language
cannot express, written in C# under the names the manifest declares and matched to it by the last
segment of each name. Findings of the pack are their own layer and are never ESJ conformance
(`SPEC.md` section 9.4). `Evaluate` refuses a document of an edition other than the one the pack
was compiled against, because a path is an address relative to an edition: a caller holding a
document with no pack for its edition reports that nothing was checked.

## What the classes cover

| Class of `SPEC.md` section 3 | Here |
|---|---|
| Reader (3.2) | `EsjReader`, with the limits of section 12.2 |
| Canonicalizer (3.4) | `Canonicalizer`, `EsjWriter.Canonical()`, both digests |
| Validator (3.5) | `Validator`, `StructuralValidator`, layers L1–L3, tri-state result |
| Writer (3.3) | `EsjWriter`, canonical and pretty form, for documents this API builds |

Editions: every registry of `model/en16931/` this build carries (`Registry.Editions()`), with the
XRechnung 3.0.2 and B2C 0.1 extension registries combined into the default one; a later edition is separable the
way [`../../docs/editions.md`](../../docs/editions.md) describes, and this binding builds without its
files. A document of an edition this build has no registry for is read, canonicalized and digested
like any other, and its model layers report `ESJ-L2-EDITION-UNKNOWN` (`SPEC.md` sections 4.4
and 9.2).

Not here: the writers to UBL and CII, PDF, rendering, `esj upgrade`, and the command line. Those are
the Java implementation ([`../../docs/cli.md`](../../docs/cli.md)).

## The generated read view

`Typed/Invoice2017.cs` and `Typed/Invoice2026.cs` are generated from the term registries:

```console
$ python3 bindings/csharp/tools/generate-typed-view.py 2017 2026
```

The slug of a term is the name stem the registry carries; this generator writes it PascalCase and
names the class of a repeatable group's instance with the singular of its plural slug. The files are
checked in, and a test holds their coverage to the registry, so a stale file fails the build.

## The fixture manifest

`En16931.SemanticJson.Fixtures` answers the six requests of `conformance/fixtures/run.py`, so the
language-neutral runner measures this binding against the same material as the reference:

```console
$ python3 conformance/fixtures/run.py --binding dotnet run --project bindings/csharp/En16931.SemanticJson.Fixtures -- .
```

The same cases run as xunit tests, one test per case, which is what `dotnet test` reports.

## Licence

Apache-2.0, as the repository. See [`../../NOTICE`](../../NOTICE).
