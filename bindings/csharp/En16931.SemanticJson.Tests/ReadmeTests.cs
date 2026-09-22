using System;
using System.Collections.Generic;
using System.IO;
using System.Text.RegularExpressions;
using En16931.SemanticJson.Json;
using En16931.SemanticJson.Model;
using En16931.SemanticJson.Rules;
using En16931.SemanticJson.Rules.En16931;
using En16931.SemanticJson.Typed.V2017;
using En16931.SemanticJson.Validation;
using Xunit;

namespace En16931.SemanticJson.Tests;

/// <summary>
/// The two code blocks of <c>bindings/csharp/README.md</c>, run as they are written there, and
/// the page under <c>docs/</c> held to them, so that a page of this repository never shows a
/// snippet nobody executes.
/// </summary>
public class ReadmeTests
{
    /// <summary>Reading, digesting, validating and reading through the generated view.</summary>
    [Fact]
    public void ReadingDigestingValidating()
    {
        string path = Path.Combine(Fixtures.Repository, "examples/standard-invoice.esj.json");

        SemanticDocument invoice = EsjReader.Strict().Read(File.ReadAllBytes(path));

        string digest = Canonicalizer.SemanticDigest(invoice);
        byte[] canonical = Canonicalizer.CanonicalBytes(invoice);

        ValidationResult result = Validator.Validate(invoice);
        foreach (Finding finding in result.Findings)
        {
            Assert.NotEmpty(finding.Message);
        }

        Invoice view = new(invoice);
        BigDecimal total = view.DocumentTotals.AmountDueForPayment!.AsDecimal();

        Assert.Equal(64, digest.Length);
        Assert.NotEmpty(canonical);
        Assert.Empty(result.Findings);
        Assert.True(total.Sign > 0);
    }

    /// <summary>Running the business rules of the pack this build carries.</summary>
    [Fact]
    public void RunningTheRulePack()
    {
        SemanticDocument invoice = EsjReader.Strict()
            .Read(Fixtures.Bytes("examples/standard-invoice.esj.json"));

        RuleEngine pack = En16931Pack.Engine(
            Registry.En16931().WithExtension(Registry.XRechnungExtension()));
        IReadOnlyList<RuleFinding> findings = pack.Evaluate(invoice);

        Assert.Empty(findings);
        Assert.Equal(217, pack.RuleIds().Count);
    }

    /// <summary>The page under <c>docs/</c> shows only code this README already runs.</summary>
    [Fact]
    public void TheDocumentationPageShowsOnlyCodeThisReadmeRuns()
    {
        string readme = File.ReadAllText(Path.Combine(Fixtures.Repository, "bindings/csharp/README.md"));
        string page = File.ReadAllText(Path.Combine(Fixtures.Repository, "docs/bindings-csharp.md"));
        MatchCollection blocks = Regex.Matches(page, "```csharp\n(.*?)```", RegexOptions.Singleline);

        Assert.Equal(2, blocks.Count);
        foreach (Match block in blocks)
        {
            Assert.Contains(block.Groups[1].Value, readme, StringComparison.Ordinal);
        }

        Assert.True(page.Split('\n').Length <= 150, "the page stays inside its line budget");
    }
}
