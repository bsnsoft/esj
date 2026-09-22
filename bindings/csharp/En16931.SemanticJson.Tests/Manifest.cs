using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;

namespace En16931.SemanticJson.Tests;

/// <summary>One conformant document of the manifest, with what it has to produce.</summary>
internal sealed record DocumentCase(
    string File,
    string? Canonical,
    string SemanticModel,
    IReadOnlyList<string> Registries,
    int Values,
    int CanonicalBytes,
    string SemanticDigest,
    string DocumentDigest,
    IReadOnlyList<(string Path, string Code)> Findings);

/// <summary>One document of the manifest that has to be rejected.</summary>
/// <remarks>
/// A document may be wrong in two ways at layer L1 and draw a row for each code, so the rows
/// of one file are held together: SPEC.md section 9.6 fixes how far a reader reads, and the
/// rows of a document layer L1 refused are its whole answer.
/// </remarks>
internal sealed record InvalidCase(
    string File, string Layer, IReadOnlyList<(string Path, string Code)> Rows);

/// <summary>One document whose members are in the wrong order, with its canonical bytes.</summary>
internal sealed record CanonicalOrderCase(string Scrambled, string Canonical, int Values, string DocumentDigest);

/// <summary>One value grammar of the specification, as an accept and a reject table.</summary>
internal sealed record GrammarCase(
    string Datatype,
    string Code,
    string Base,
    string Path,
    IReadOnlyList<JsonElement> Accept,
    IReadOnlyList<JsonElement> Reject);

/// <summary>One registry the manifest asks an implementation to load.</summary>
internal sealed record RegistryCase(
    string File,
    string SemanticModel,
    int Terms,
    int BusinessTerms,
    int BusinessGroups,
    IReadOnlyList<JsonElement> SamplePaths);

/// <summary>One rule case: a conformant document, the changes, and what the pack reports.</summary>
internal sealed record RuleCase(
    string Id,
    string Rule,
    string Base,
    IReadOnlyList<JsonElement> Changes,
    IReadOnlyList<string> Rules,
    IReadOnlyList<string> Warnings);

/// <summary>The manifest as typed cases, keyed so that a test row names the case it runs.</summary>
internal static class Manifest
{
    private static readonly Lazy<IReadOnlyDictionary<string, DocumentCase>> DocumentCases = new(ReadDocuments);
    private static readonly Lazy<IReadOnlyDictionary<string, InvalidCase>> InvalidCases = new(ReadInvalid);
    private static readonly Lazy<IReadOnlyDictionary<string, CanonicalOrderCase>> OrderCases = new(ReadOrder);
    private static readonly Lazy<IReadOnlyDictionary<string, GrammarCase>> GrammarCases = new(ReadGrammars);
    private static readonly Lazy<IReadOnlyDictionary<string, RegistryCase>> RegistryCases = new(ReadRegistries);
    private static readonly Lazy<IReadOnlyDictionary<string, RuleCase>> Rules = new(ReadRules);

    internal static IReadOnlyDictionary<string, DocumentCase> Documents => DocumentCases.Value;

    internal static IReadOnlyDictionary<string, InvalidCase> Invalid => InvalidCases.Value;

    internal static IReadOnlyDictionary<string, CanonicalOrderCase> CanonicalOrder => OrderCases.Value;

    internal static IReadOnlyDictionary<string, GrammarCase> Grammars => GrammarCases.Value;

    internal static IReadOnlyDictionary<string, RegistryCase> Registries => RegistryCases.Value;

    internal static IReadOnlyDictionary<string, RuleCase> RuleCases => Rules.Value;

    /// <summary>Returns the names of a set of cases as xunit theory rows.</summary>
    /// <param name="names">the keys of the cases</param>
    /// <returns>one row per case</returns>
    internal static IEnumerable<object[]> Rows(IEnumerable<string> names) =>
        names.Select(name => new object[] { name });

    private static Dictionary<string, DocumentCase> ReadDocuments()
    {
        Dictionary<string, DocumentCase> cases = new(StringComparer.Ordinal);
        foreach ((JsonElement _, JsonElement entry) in Fixtures.Section("documents"))
        {
            List<(string, string)> findings = new();
            if (entry.TryGetProperty("findings", out JsonElement reported))
            {
                findings.AddRange(reported.EnumerateArray().Select(finding =>
                    (finding.GetProperty("path").GetString()!, finding.GetProperty("code").GetString()!)));
            }

            string file = entry.GetProperty("file").GetString()!;
            cases[file] = new DocumentCase(
                file,
                Fixtures.Optional(entry, "canonical"),
                entry.GetProperty("semanticModel").GetString()!,
                entry.GetProperty("registries").EnumerateArray()
                    .Select(registry => registry.GetString()!).ToList(),
                entry.GetProperty("values").GetInt32(),
                entry.GetProperty("canonicalBytes").GetInt32(),
                entry.GetProperty("semanticDigest").GetString()!,
                entry.GetProperty("documentDigest").GetString()!,
                findings);
        }

        return cases;
    }

    private static Dictionary<string, InvalidCase> ReadInvalid()
    {
        Dictionary<string, InvalidCase> cases = new(StringComparer.Ordinal);
        foreach ((JsonElement _, JsonElement entry) in Fixtures.Section("invalid"))
        {
            string file = entry.GetProperty("file").GetString()!;
            string layer = entry.GetProperty("layer").GetString()!;
            List<(string, string)> rows = cases.TryGetValue(file, out InvalidCase? known)
                ? new List<(string, string)>(known.Rows)
                : new List<(string, string)>();
            string? code = Fixtures.Optional(entry, "code");
            if (code is not null)
            {
                rows.Add((Fixtures.Optional(entry, "path") ?? string.Empty, code));
            }

            cases[file] = new InvalidCase(file, layer, rows);
        }

        return cases;
    }

    private static Dictionary<string, CanonicalOrderCase> ReadOrder()
    {
        Dictionary<string, CanonicalOrderCase> cases = new(StringComparer.Ordinal);
        foreach ((JsonElement _, JsonElement entry) in Fixtures.Section("canonicalOrder"))
        {
            string scrambled = entry.GetProperty("scrambled").GetString()!;
            cases[scrambled] = new CanonicalOrderCase(
                scrambled,
                entry.GetProperty("canonical").GetString()!,
                entry.GetProperty("values").GetInt32(),
                entry.GetProperty("documentDigest").GetString()!);
        }

        return cases;
    }

    private static Dictionary<string, GrammarCase> ReadGrammars()
    {
        Dictionary<string, GrammarCase> cases = new(StringComparer.Ordinal);
        foreach ((JsonElement _, JsonElement entry) in Fixtures.Section("grammars"))
        {
            string datatype = entry.GetProperty("datatype").GetString()!;
            cases[datatype] = new GrammarCase(
                datatype,
                entry.GetProperty("code").GetString()!,
                entry.GetProperty("base").GetString()!,
                entry.GetProperty("path").GetString()!,
                entry.GetProperty("accept").EnumerateArray().ToList(),
                entry.GetProperty("reject").EnumerateArray().ToList());
        }

        return cases;
    }

    private static Dictionary<string, RegistryCase> ReadRegistries()
    {
        Dictionary<string, RegistryCase> cases = new(StringComparer.Ordinal);
        foreach ((JsonElement _, JsonElement entry) in Fixtures.Section("registries"))
        {
            string file = entry.GetProperty("file").GetString()!;
            cases[file] = new RegistryCase(
                file,
                entry.GetProperty("semanticModel").GetString()!,
                entry.GetProperty("terms").GetInt32(),
                entry.TryGetProperty("businessTerms", out JsonElement terms) ? terms.GetInt32() : 0,
                entry.TryGetProperty("businessGroups", out JsonElement groups) ? groups.GetInt32() : 0,
                entry.GetProperty("samplePaths").EnumerateArray().ToList());
        }

        return cases;
    }

    private static Dictionary<string, RuleCase> ReadRules()
    {
        Dictionary<string, RuleCase> cases = new(StringComparer.Ordinal);
        foreach (JsonElement entry in Fixtures.Cases())
        {
            string id = entry.GetProperty("id").GetString()!;
            JsonElement expect = entry.GetProperty("expect");
            cases[id] = new RuleCase(
                id,
                entry.GetProperty("rule").GetString()!,
                entry.GetProperty("base").GetString()!,
                entry.GetProperty("changes").EnumerateArray().ToList(),
                expect.GetProperty("rules").EnumerateArray().Select(rule => rule.GetString()!).ToList(),
                expect.GetProperty("warnings").EnumerateArray().Select(rule => rule.GetString()!).ToList());
        }

        return cases;
    }
}
