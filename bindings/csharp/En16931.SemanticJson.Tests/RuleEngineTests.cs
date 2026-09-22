using System;
using System.Collections.Generic;
using System.Linq;
using En16931.SemanticJson.Json;
using En16931.SemanticJson.Model;
using En16931.SemanticJson.Rules;
using En16931.SemanticJson.Rules.En16931;
using Xunit;

namespace En16931.SemanticJson.Tests;

/// <summary>
/// What the rule engine does beside the cases of the manifest: how it answers a value that
/// does not spell its semantic data type, and what it refuses to compile.
/// </summary>
public class RuleEngineTests
{
    private static readonly Lazy<RuleEngine> Pack = new(() => En16931Pack.Engine(Registries.Core2017));

    /// <summary>
    /// A rule that reads a value which is not the number its term requires is not decided: it
    /// reports one finding at <c>info</c> and no verdict, because the defect belongs to the
    /// value and the structural validator has already named it at layer L2.
    /// </summary>
    [Fact]
    public void ARuleThatCannotReadAValueIsNotDecided()
    {
        SemanticDocument invoice = EsjReader.Strict()
            .Read(Fixtures.Bytes("examples/minimal.esj.json"))
            .With(SemanticPath.Parse("/BG-22/BT-106"), new SemanticValue("1.000,00"));

        IReadOnlyList<RuleFinding> findings = Pack.Value.Evaluate(invoice);

        Assert.Contains(findings, finding => finding.Severity == RuleSeverity.Info);
        Assert.All(
            findings.Where(finding => finding.Severity == RuleSeverity.Info),
            finding => Assert.StartsWith("not decided:", finding.Message, StringComparison.Ordinal));
        Assert.Contains(findings, finding => finding.Code == "BR-CO-10");
    }

    /// <summary>
    /// A pack that names rules the language cannot express does not compile without them: one
    /// missing would be a pack that silently checks less than it claims.
    /// </summary>
    [Fact]
    public void APackDoesNotCompileWithoutTheRulesItNames()
    {
        RulePack pack = En16931Pack.Pack();

        RulePackException refused = Assert.Throws<RulePackException>(
            () => RuleEngine.Compile(pack, Registries.Core2017, CodeLists.Bundled(pack), NativeRules.None()));

        Assert.Contains("missing", refused.Message, StringComparison.Ordinal);
    }

    /// <summary>
    /// A finding carries the pack that reported it and the paths its rule read, which is what
    /// lets a report name both.
    /// </summary>
    [Fact]
    public void AFindingNamesItsPackAndWhatItRead()
    {
        SemanticDocument invoice = EsjReader.Strict()
            .Read(Fixtures.Bytes("examples/invalid/arithmetic-mismatch.esj.json"));

        RuleFinding finding = Pack.Value.Evaluate(invoice)[0];

        Assert.Equal(En16931Pack.PackId, finding.PackId);
        Assert.Equal(En16931Pack.Version, finding.PackVersion);
        Assert.Equal(RuleFinding.NativeEngine, finding.Engine);
        Assert.NotEmpty(finding.Paths);
        Assert.NotEmpty(finding.Message);
    }

    /// <summary>The pack states what it was measured against and which snapshots it decides by.</summary>
    [Fact]
    public void ThePackNamesWhatItWasMeasuredAgainst()
    {
        RuleEngine pack = Pack.Value;

        Assert.Equal("en16931/1.3.16", pack.Pack.Name);
        Assert.Contains("1.3.16", pack.Pack.VerifiedAgainst, StringComparison.Ordinal);
        Assert.Equal(pack.Pack.CodeLists.Keys.OrderBy(id => id, StringComparer.Ordinal),
            pack.CodeLists.ListIds.OrderBy(id => id, StringComparer.Ordinal));
        Assert.NotNull(pack.TermsOf("BR-CO-10"));
        Assert.Contains("BT-131", pack.TermsOf("BR-CO-10")!);
    }
}
