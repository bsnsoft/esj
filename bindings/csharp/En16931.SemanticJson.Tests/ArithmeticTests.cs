using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using En16931.SemanticJson.Json;
using En16931.SemanticJson.Rules;
using Xunit;

namespace En16931.SemanticJson.Tests;

/// <summary>
/// The arithmetic section of the manifest: a pack of the rule language whose rules pin its
/// division as <c>rules/README.md</c> states it — exact where the quotient terminates, 34
/// fraction digits half up where it does not, absent where the divisor is zero — with the rule
/// identifiers it reports over one document.
/// </summary>
/// <remarks>
/// Every rule of the pack is a test of its own, so that a failure names the quotient that is
/// wrong; the note of that rule in the pack says what it pins.
/// </remarks>
public class ArithmeticTests
{
    private static readonly Lazy<Section> Arithmetic = new(ReadSection);

    /// <summary>The rules of the pack, one row each.</summary>
    /// <returns>the identifiers</returns>
    public static IEnumerable<object[]> Rules() =>
        Manifest.Rows(Arithmetic.Value.Pack.Rules.Select(rule => rule.Id));

    /// <summary>
    /// A rule of the pack is reported exactly where the manifest says it is: a quotient this
    /// binding computes as the language says makes its rule report, and one it computes
    /// differently, or not at all, leaves the rule silent.
    /// </summary>
    /// <param name="id">the rule</param>
    [Theory]
    [MemberData(nameof(Rules))]
    public void RuleReportsWhatTheManifestRecords(string id)
    {
        Section section = Arithmetic.Value;
        Assert.Equal(section.Expected.Contains(id), section.Reported.Contains(id));
    }

    /// <summary>No finding of the pack decides no verdict, and the manifest says the same.</summary>
    [Fact]
    public void ItsWarningsAreTheOnesTheManifestRecords() =>
        Assert.Equal(Arithmetic.Value.ExpectedWarnings, Arithmetic.Value.Warnings);

    /// <summary>A pack read from one stream carries its rules in it, and one that names rule files is refused.</summary>
    [Fact]
    public void APackThatNamesRuleFilesIsNotReadFromOneStream()
    {
        using MemoryStream input = new(System.Text.Encoding.UTF8.GetBytes(
            "{\"id\": \"p\", \"version\": \"1\", \"verifiedAgainst\": \"nothing\","
            + " \"description\": \"a pack\", \"files\": [\"rules/a.json\"]}"));

        RulePackException refused = Assert.Throws<RulePackException>(() => RulePacks.Read(input, "p"));

        Assert.Contains("rule files", refused.Message, StringComparison.Ordinal);
    }

    private static Section ReadSection()
    {
        JsonElement section = Fixtures.Manifests
            .Select(manifest => manifest.TryGetProperty("arithmetic", out JsonElement found) ? found : default)
            .First(found => found.ValueKind == JsonValueKind.Object);
        string file = section.GetProperty("pack").GetString()!;
        RulePack pack;
        using (FileStream input = File.OpenRead(Path.Combine(Fixtures.Repository, file)))
        {
            pack = RulePacks.Read(input, file);
        }

        SemanticDocument document = EsjReader.Strict().Read(Fixtures.Bytes(section.GetProperty("base").GetString()!));
        IReadOnlyList<RuleFinding> findings = RuleEngine.Compile(pack, Registries.Core2017).Evaluate(document);
        JsonElement expect = section.GetProperty("expect");
        return new Section(
            pack,
            findings.Select(finding => finding.Code).ToHashSet(StringComparer.Ordinal),
            findings.Where(finding => finding.Severity == RuleSeverity.Warning)
                .Select(finding => finding.Code).Distinct(StringComparer.Ordinal).ToList(),
            expect.GetProperty("rules").EnumerateArray().Select(rule => rule.GetString()!)
                .ToHashSet(StringComparer.Ordinal),
            expect.GetProperty("warnings").EnumerateArray().Select(rule => rule.GetString()!).ToList());
    }

    private sealed record Section(
        RulePack Pack,
        HashSet<string> Reported,
        List<string> Warnings,
        HashSet<string> Expected,
        List<string> ExpectedWarnings);
}
