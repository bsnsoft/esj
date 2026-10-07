using System;
using System.Collections.Generic;
using System.Linq;
using BSNSoft.Esj.Json;
using BSNSoft.Esj.Model;
using BSNSoft.Esj.Rules;
using BSNSoft.Esj.Rules.En16931;
using Xunit;

namespace BSNSoft.Esj.Tests;

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
    /// A rule about a line that divides by its quantity and names the case it has no answer for:
    /// where the quantity is zero it is not decided and says why, and nothing else of it is
    /// weighed; where the case does not hold, or cannot be decided, the assertion decides.
    /// </summary>
    [Fact]
    public void ARuleThatNamesTheCaseItHasNoAnswerForIsNotDecidedThere()
    {
        const string pack = "{\"id\": \"test\", \"version\": \"1\", \"edition\": \"EN 16931-1:2017+A1:2019/AC:2020\","
            + " \"description\": \"A pack the tests write.\", \"rules\": [{\"id\": \"BR-TEST\","
            + " \"severity\": \"fatal\", \"oracle\": \"cases\", \"context\": \"/BG-25/*\", \"terms\": [\"BT-129\"],"
            + " \"assert\": {\"eq\": [{\"value\": \"/BG-29/BT-146\"}, {\"div\": [{\"value\": \"/BT-131\"},"
            + " {\"value\": \"/BT-129\"}]}]},"
            + " \"undecided\": {\"when\": {\"eq\": [{\"value\": \"/BT-129\"}, {\"const\": \"0\"}]},"
            + " \"message\": \"the quantity at {@/BT-129} is {/BT-129}, and a price per unit of no units is no number\"},"
            + " \"message\": \"BR-TEST does not hold.\", \"source\": \"EN 16931-1, 6.4\"}]}";
        using System.IO.MemoryStream input = new(System.Text.Encoding.UTF8.GetBytes(pack));
        RuleEngine engine = RuleEngine.Compile(RulePacks.Read(input, "the test pack"), Registries.Core2017);
        SemanticDocument minimal = EsjReader.Strict().Read(Fixtures.Bytes("examples/minimal.esj.json"));
        SemanticPath quantity = SemanticPath.Parse("/BG-25/0/BT-129");

        RuleFinding undecided = Assert.Single(engine.Evaluate(minimal.With(quantity, new SemanticValue("0"))));
        IReadOnlyList<RuleFinding> decided = engine.Evaluate(minimal);
        IReadOnlyList<RuleFinding> failed = engine.Evaluate(minimal.With(quantity, new SemanticValue("2")));
        IReadOnlyList<RuleFinding> unknown = engine.Evaluate(minimal.With(quantity, null));

        Assert.Equal(RuleSeverity.Info, undecided.Severity);
        Assert.Equal("BR-TEST", undecided.Code);
        Assert.Equal(
            "not decided: the quantity at /BG-25/0/BT-129 is 0, and a price per unit of no units is no number",
            undecided.Message);
        Assert.Empty(decided);
        Assert.Equal(RuleSeverity.Fatal, Assert.Single(failed).Severity);
        Assert.Empty(unknown);
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
