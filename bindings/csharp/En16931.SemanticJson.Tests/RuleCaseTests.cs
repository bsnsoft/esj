using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using En16931.SemanticJson.Json;
using En16931.SemanticJson.Rules;
using En16931.SemanticJson.Rules.En16931;
using Xunit;

namespace En16931.SemanticJson.Tests;

/// <summary>
/// The rule cases of the manifest: every mutation of the conformance corpus, as a base
/// document and the changes that break it, with the rule identifiers the pack reports over it
/// and the subset of them whose findings decide no verdict.
/// </summary>
public class RuleCaseTests
{
    private static readonly Lazy<RuleEngine> Pack = new(() => En16931Pack.Engine(Registries.Core2017));
    private static readonly ConcurrentDictionary<string, SemanticDocument> Bases = new(StringComparer.Ordinal);
    private static readonly ConcurrentDictionary<string, RuleEngine> Engines = new(StringComparer.Ordinal);

    /// <summary>The rule cases of the manifest.</summary>
    public static IEnumerable<object[]> Cases() => Manifest.Rows(Manifest.RuleCases.Keys);

    /// <summary>The pack carries the rules the manifest counts, with no identifier twice.</summary>
    [Fact]
    public void PackCarriesItsRules()
    {
        IReadOnlyList<string> ids = Pack.Value.RuleIds();
        Assert.Equal(ids.Count, ids.Distinct(StringComparer.Ordinal).Count());
        Assert.Equal(En16931Pack.Version, Pack.Value.Pack.Version);
        Assert.Contains("BR-CO-10", ids);
        Assert.Contains("BR-S-08", ids);
        Assert.Equal(
            Pack.Value.Pack.Rules.Count + Pack.Value.Pack.NativeRules.Count,
            ids.Count);
    }

    /// <summary>
    /// A conformant document of the corpus, changed the way the case says, draws exactly the
    /// rule identifiers the manifest records, and the manifest's warnings are the findings of
    /// this run that decide no verdict.
    /// </summary>
    /// <param name="id">the case</param>
    [Theory]
    [MemberData(nameof(Cases))]
    public void CaseReportsTheRulesTheManifestRecords(string id)
    {
        RuleCase expected = Manifest.RuleCases[id];
        SemanticDocument start = expected.Carried is JsonElement carried
            ? EsjReader.Strict().Read(Encoding.UTF8.GetBytes(carried.GetRawText()))
            : Base(expected.Base);
        SemanticDocument document = Apply(start, expected.Changes);
        IReadOnlyList<RuleFinding> findings = EngineFor(document.SemanticModel).Evaluate(document);

        Assert.Equal(expected.Rules, Reported(findings, all: true));
        Assert.Equal(expected.Warnings, Reported(findings, all: false));
    }

    private static List<string> Reported(IReadOnlyList<RuleFinding> findings, bool all) =>
        findings
            .Where(finding => all || finding.Severity == RuleSeverity.Warning)
            .Select(finding => finding.Code)
            .Distinct(StringComparer.Ordinal)
            .OrderBy(code => code, StringComparer.Ordinal)
            .ToList();

    /// <summary>
    /// Returns the pack this build carries for the edition a document names: the pack of the
    /// default edition over the registry the manifest measures that edition with, or the pack
    /// of another edition over the registry of it.
    /// </summary>
    private static RuleEngine EngineFor(string semanticModel)
    {
        if (Pack.Value.Registry.Describes(semanticModel))
        {
            return Pack.Value;
        }

        return Engines.GetOrAdd(semanticModel, edition =>
        {
            Model.Registry registry = Registries.Measuring(edition);
            IRulePackSource source = RulePackSources.ForEdition(registry.Edition)
                ?? throw new InvalidOperationException("no rule pack of this build states rules about " + edition);
            return source.Engine(registry);
        });
    }

    private static SemanticDocument Base(string name) =>
        Bases.GetOrAdd(name, file => EsjReader.Strict().Read(Fixtures.Bytes(file)));

    private static SemanticDocument Apply(SemanticDocument document, IReadOnlyList<JsonElement> changes)
    {
        SemanticDocument changed = document;
        foreach (JsonElement change in changes)
        {
            SemanticPath path = SemanticPath.Parse(change.GetProperty("path").GetString()!);
            changed = change.TryGetProperty("remove", out JsonElement _)
                ? changed.With(path, null)
                : changed.With(path, Values.Read(change.GetProperty("value")));
        }

        return changed;
    }
}
