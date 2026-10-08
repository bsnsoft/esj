using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using BSNSoft.Esj.Validation;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// The whole answer of a validation as the manifest records it: the status, every layer not
/// evaluated with its reason, and every finding with its path, code, subject and severity.
/// </summary>
internal sealed record Outcome(
    string Status,
    IReadOnlyList<(string Layer, string Reason)> NotEvaluated,
    IReadOnlyList<(string Path, string Code, string Subject, string Severity)> Findings)
{
    /// <summary>Reads an outcome of the manifest.</summary>
    /// <param name="outcome">the <c>outcome</c> member of a case</param>
    /// <returns>the outcome</returns>
    internal static Outcome Read(JsonElement outcome) => new(
        outcome.GetProperty("status").GetString()!,
        outcome.GetProperty("notEvaluated").EnumerateArray()
            .Select(entry => (entry.GetProperty("layer").GetString()!, entry.GetProperty("reason").GetString()!))
            .ToList(),
        outcome.GetProperty("findings").EnumerateArray()
            .Select(finding => (finding.GetProperty("path").GetString()!, finding.GetProperty("code").GetString()!,
                finding.GetProperty("subject").GetString()!, finding.GetProperty("severity").GetString()!))
            .ToList());

    /// <summary>
    /// The outcome in the form two outcomes are compared in: the findings of the reader as a
    /// list, because the specification, section 9.6 fixes how far a reader reads and in which
    /// order; the findings of one path at layer L2 as a list, because section 9.2 fixes the order
    /// of the checks of a path; everything else as a set.
    /// </summary>
    /// <returns>a text that is equal for two outcomes exactly when they are</returns>
    internal string Normalized()
    {
        static string Row((string Path, string Code, string Subject, string Severity) finding) =>
            JsonSerializer.Serialize(new[] { finding.Path, finding.Code, finding.Subject, finding.Severity });

        List<string> reader = new();
        SortedDictionary<string, List<string>> model = new(StringComparer.Ordinal);
        List<string> others = new();
        foreach ((string Path, string Code, string Subject, string Severity) finding in Findings)
        {
            if (finding.Code.StartsWith("ESJ-L1-", StringComparison.Ordinal))
            {
                reader.Add(Row(finding));
            }
            else if (finding.Code.StartsWith("ESJ-L2-", StringComparison.Ordinal))
            {
                if (!model.TryGetValue(finding.Path, out List<string>? rows))
                {
                    rows = new List<string>();
                    model[finding.Path] = rows;
                }

                rows.Add(Row(finding));
            }
            else
            {
                others.Add(Row(finding));
            }
        }

        others.Sort(StringComparer.Ordinal);
        List<string> layers = NotEvaluated.Select(entry => entry.Layer + ":" + entry.Reason).ToList();
        layers.Sort(StringComparer.Ordinal);
        return "status " + Status + "\nnot evaluated " + string.Join(", ", layers)
            + "\nreader\n  " + string.Join("\n  ", reader)
            + "\nmodel\n  " + string.Join("\n  ", model.Select(path => path.Key + ": " + string.Join(" ", path.Value)))
            + "\nothers\n  " + string.Join("\n  ", others);
    }
}

/// <summary>The digests and sizes of a document the reader builds.</summary>
internal sealed record Digests(int Values, int CanonicalBytes, string SemanticDigest, string DocumentDigest);

/// <summary>One document of the manifest, with what it has to produce and what a validator says of it.</summary>
internal sealed record DocumentCase(
    string File,
    string? Canonical,
    string SemanticModel,
    IReadOnlyList<string> Registries,
    int Values,
    int CanonicalBytes,
    string SemanticDigest,
    string DocumentDigest,
    Outcome Outcome,
    bool Evaluated);

/// <summary>One document of the manifest that has to be rejected, with the whole answer of a validation.</summary>
/// <remarks>
/// A case may name the registries it is validated with instead of the ones a binding carries,
/// and carries the digests of the document where the reader builds one.
/// </remarks>
internal sealed record InvalidCase(
    string File,
    string Layer,
    IReadOnlyList<string>? Registries,
    Digests? Digests,
    Outcome Outcome,
    bool Evaluated);

/// <summary>One document read under bounds other than the defaults, with what a validator says of it.</summary>
internal sealed record BoundCase(string File, JsonElement Limits, Outcome Outcome);

/// <summary>A set of registry files to read and combine, and whether a loader takes it.</summary>
internal sealed record RegistryCheckCase(IReadOnlyList<string> Files, bool Accepted);

/// <summary>One document whose members are in the wrong order, with its canonical bytes.</summary>
internal sealed record CanonicalOrderCase(string Scrambled, string Canonical, int Values, string DocumentDigest);

/// <summary>
/// One grammar as an accept and a reject table: a value grammar of the specification, section 6,
/// measured at a path, or a grammar of the envelope, measured in the member it constrains.
/// </summary>
internal sealed record GrammarCase(
    string Name,
    string Code,
    string Base,
    string? Path,
    string? Member,
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
/// <remarks>
/// The document is a file of the repository named by <c>Base</c>, or, where the case file
/// carries the documents its cases start from, <c>Carried</c>.
/// </remarks>
internal sealed record RuleCase(
    string Id,
    string Rule,
    string Base,
    JsonElement? Carried,
    IReadOnlyList<JsonElement> Changes,
    IReadOnlyList<string> Rules,
    IReadOnlyList<string> Warnings);

/// <summary>The manifest as typed cases, keyed so that a test row names the case it runs.</summary>
internal static class Manifest
{
    private static readonly Lazy<IReadOnlyDictionary<string, DocumentCase>> DocumentCases = new(ReadDocuments);
    private static readonly Lazy<IReadOnlyDictionary<string, InvalidCase>> InvalidCases = new(ReadInvalid);
    private static readonly Lazy<IReadOnlyDictionary<string, BoundCase>> BoundCases = new(ReadBounds);
    private static readonly Lazy<IReadOnlyDictionary<string, RegistryCheckCase>> RegistryCheckCases = new(ReadRegistryChecks);
    private static readonly Lazy<IReadOnlyDictionary<string, CanonicalOrderCase>> OrderCases = new(ReadOrder);
    private static readonly Lazy<IReadOnlyDictionary<string, GrammarCase>> GrammarCases = new(ReadGrammars);
    private static readonly Lazy<IReadOnlyDictionary<string, RegistryCase>> RegistryCases = new(ReadRegistries);
    private static readonly Lazy<IReadOnlyDictionary<string, RuleCase>> Rules = new(ReadRules);

    internal static IReadOnlyDictionary<string, DocumentCase> Documents => DocumentCases.Value;

    internal static IReadOnlyDictionary<string, InvalidCase> Invalid => InvalidCases.Value;

    internal static IReadOnlyDictionary<string, BoundCase> Bounds => BoundCases.Value;

    internal static IReadOnlyDictionary<string, RegistryCheckCase> RegistryChecks => RegistryCheckCases.Value;

    internal static IReadOnlyDictionary<string, CanonicalOrderCase> CanonicalOrder => OrderCases.Value;

    internal static IReadOnlyDictionary<string, GrammarCase> Grammars => GrammarCases.Value;

    internal static IReadOnlyDictionary<string, RegistryCase> Registries => RegistryCases.Value;

    internal static IReadOnlyDictionary<string, RuleCase> RuleCases => Rules.Value;

    /// <summary>Returns the names of a set of cases as xunit theory rows.</summary>
    /// <param name="names">the keys of the cases</param>
    /// <returns>one row per case</returns>
    internal static IEnumerable<object[]> Rows(IEnumerable<string> names) =>
        names.Select(name => new object[] { name });

    /// <summary>
    /// Whether this binding validates the cases of a manifest file: a part carries one edition,
    /// and its outcomes were recorded with the registry of that edition.
    /// </summary>
    private static bool Evaluated(JsonElement manifest) =>
        !manifest.TryGetProperty("semanticModel", out JsonElement edition)
        || Validator.Registries().Any(registry => registry.SemanticModel == edition.GetString());

    private static Dictionary<string, DocumentCase> ReadDocuments()
    {
        Dictionary<string, DocumentCase> cases = new(StringComparer.Ordinal);
        foreach ((JsonElement manifest, JsonElement entry) in Fixtures.Section("documents"))
        {
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
                Outcome.Read(entry.GetProperty("outcome")),
                Evaluated(manifest));
        }

        return cases;
    }

    private static Dictionary<string, InvalidCase> ReadInvalid()
    {
        Dictionary<string, InvalidCase> cases = new(StringComparer.Ordinal);
        foreach ((JsonElement manifest, JsonElement entry) in Fixtures.Section("invalid"))
        {
            string file = entry.GetProperty("file").GetString()!;
            cases[file] = new InvalidCase(
                file,
                entry.GetProperty("layer").GetString()!,
                entry.TryGetProperty("registries", out JsonElement registries)
                    ? registries.EnumerateArray().Select(registry => registry.GetString()!).ToList()
                    : null,
                entry.TryGetProperty("values", out JsonElement values)
                    ? new Digests(
                        values.GetInt32(),
                        entry.GetProperty("canonicalBytes").GetInt32(),
                        entry.GetProperty("semanticDigest").GetString()!,
                        entry.GetProperty("documentDigest").GetString()!)
                    : null,
                Outcome.Read(entry.GetProperty("outcome")),
                Evaluated(manifest));
        }

        return cases;
    }

    private static Dictionary<string, BoundCase> ReadBounds()
    {
        Dictionary<string, BoundCase> cases = new(StringComparer.Ordinal);
        foreach ((JsonElement _, JsonElement entry) in Fixtures.Section("bounds"))
        {
            string file = entry.GetProperty("file").GetString()!;
            JsonElement limits = entry.GetProperty("limits");
            cases[file + " " + limits.GetRawText()] = new BoundCase(file, limits, Outcome.Read(entry.GetProperty("outcome")));
        }

        return cases;
    }

    private static Dictionary<string, RegistryCheckCase> ReadRegistryChecks()
    {
        Dictionary<string, RegistryCheckCase> cases = new(StringComparer.Ordinal);
        foreach ((JsonElement _, JsonElement entry) in Fixtures.Section("registryChecks"))
        {
            List<string> files = entry.GetProperty("files").EnumerateArray().Select(file => file.GetString()!).ToList();
            cases[string.Join(" + ", files)] = new RegistryCheckCase(files, entry.GetProperty("accepted").GetBoolean());
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
            string? path = Fixtures.Optional(entry, "path");
            string? member = Fixtures.Optional(entry, "member");
            string name = path is null
                ? entry.GetProperty("grammar").GetString()!
                : entry.GetProperty("datatype").GetString()! + " at " + path;
            cases[name] = new GrammarCase(
                name,
                entry.GetProperty("code").GetString()!,
                entry.GetProperty("base").GetString()!,
                path,
                member,
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
        foreach ((JsonElement file, JsonElement entry) in Fixtures.Cases())
        {
            string id = entry.GetProperty("id").GetString()!;
            JsonElement expect = entry.GetProperty("expect");
            bool carried = entry.TryGetProperty("baseDocument", out JsonElement named);
            cases[id] = new RuleCase(
                id,
                entry.GetProperty("rule").GetString()!,
                carried ? string.Empty : entry.GetProperty("base").GetString()!,
                carried ? file.GetProperty("bases").GetProperty(named.GetString()!) : null,
                entry.GetProperty("changes").EnumerateArray().ToList(),
                expect.GetProperty("rules").EnumerateArray().Select(rule => rule.GetString()!).ToList(),
                expect.GetProperty("warnings").EnumerateArray().Select(rule => rule.GetString()!).ToList());
        }

        return cases;
    }
}
