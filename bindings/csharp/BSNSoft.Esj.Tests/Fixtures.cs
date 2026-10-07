using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text.Json;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// The fixture manifest of <c>conformance/fixtures/</c>: every case an implementation of ESJ
/// has to pass, written in no programming language.
/// </summary>
/// <remarks>
/// The manifest is read from the repository rather than copied into this binding, so that a
/// case the reference implementation adds is a case this binding is measured against on the
/// next run and not on the next copy.
/// </remarks>
internal static class Fixtures
{
    private const string ManifestDirectory = "conformance/fixtures";

    private static readonly Lazy<string> Root = new(FindRepository);
    private static readonly Lazy<IReadOnlyList<JsonElement>> Files = new(ReadManifests);
    private static readonly Lazy<IReadOnlyList<JsonElement>> RuleCases = new(ReadRuleCases);

    /// <summary>Returns the root of the checkout the fixtures are read from.</summary>
    internal static string Repository => Root.Value;

    /// <summary>Returns the core manifest and every part of it this distribution carries.</summary>
    internal static IReadOnlyList<JsonElement> Manifests => Files.Value;

    /// <summary>Returns the bytes of a file named by the manifest.</summary>
    /// <param name="name">the path, relative to the repository root</param>
    /// <returns>the bytes</returns>
    internal static byte[] Bytes(string name) => File.ReadAllBytes(Path.Combine(Repository, name));

    /// <summary>Returns the text of a file named by the manifest.</summary>
    /// <param name="name">the path, relative to the repository root</param>
    /// <returns>the text, read as UTF-8</returns>
    internal static string Text(string name) => File.ReadAllText(Path.Combine(Repository, name));

    /// <summary>Returns the entries of one section of every manifest file.</summary>
    /// <param name="section">the member name of the section</param>
    /// <returns>each entry, with the manifest it came from</returns>
    internal static IEnumerable<(JsonElement Manifest, JsonElement Entry)> Section(string section)
    {
        foreach (JsonElement manifest in Manifests)
        {
            if (!manifest.TryGetProperty(section, out JsonElement entries))
            {
                continue;
            }

            foreach (JsonElement entry in entries.EnumerateArray())
            {
                yield return (manifest, entry);
            }
        }
    }

    /// <summary>
    /// Returns the rule cases of the manifest and of every part of it, each with the case file
    /// it stands in, which carries the documents a case names by <c>baseDocument</c>.
    /// </summary>
    /// <returns>the cases</returns>
    internal static IEnumerable<(JsonElement File, JsonElement Entry)> Cases()
    {
        foreach (JsonElement file in RuleCases.Value)
        {
            foreach (JsonElement entry in file.GetProperty("cases").EnumerateArray())
            {
                yield return (file, entry);
            }
        }
    }

    /// <summary>Returns a string member of a manifest entry, or <c>null</c> where it has none.</summary>
    /// <param name="entry">the entry</param>
    /// <param name="member">the member name</param>
    /// <returns>the value, or <c>null</c></returns>
    internal static string? Optional(JsonElement entry, string member) =>
        entry.TryGetProperty(member, out JsonElement value) ? value.GetString() : null;

    private static IReadOnlyList<JsonElement> ReadManifests()
    {
        JsonElement core = Read(Path.Combine(Repository, ManifestDirectory, "manifest.json"));
        List<JsonElement> files = new() { core };
        if (!core.TryGetProperty("parts", out JsonElement parts))
        {
            return files;
        }

        foreach (JsonElement part in parts.EnumerateArray())
        {
            string path = Path.Combine(Repository, ManifestDirectory, part.GetString()!);
            if (File.Exists(path))
            {
                files.Add(Read(path));
            }
        }

        return files;
    }

    private static IReadOnlyList<JsonElement> ReadRuleCases()
    {
        List<JsonElement> files = new();
        foreach (JsonElement manifest in Manifests)
        {
            if (manifest.TryGetProperty("rules", out JsonElement rules))
            {
                files.Add(Read(Path.Combine(
                    Repository, ManifestDirectory, rules.GetProperty("casesFile").GetString()!)));
            }
        }

        return files;
    }

    private static JsonElement Read(string path) =>
        JsonDocument.Parse(File.ReadAllBytes(path)).RootElement;

    /// <summary>
    /// Finds the checkout the fixtures are read from: the root the build recorded, or the one
    /// the environment names, and it has to carry the manifest.
    /// </summary>
    private static string FindRepository()
    {
        string? named = Environment.GetEnvironmentVariable("ESJ_REPOSITORY");
        if (!string.IsNullOrEmpty(named))
        {
            return Verified(named);
        }

        string? recorded = typeof(Fixtures).Assembly
            .GetCustomAttributes<AssemblyMetadataAttribute>()
            .FirstOrDefault(attribute => attribute.Key == "RepositoryRoot")?.Value;
        if (!string.IsNullOrEmpty(recorded))
        {
            return Verified(recorded);
        }

        throw new InvalidOperationException(
            "the build recorded no repository root; set ESJ_REPOSITORY to the checkout");
    }

    private static string Verified(string root)
    {
        string full = Path.GetFullPath(root);
        string manifest = Path.Combine(full, ManifestDirectory, "manifest.json");
        return File.Exists(manifest)
            ? full
            : throw new FileNotFoundException("the checkout carries no fixture manifest", manifest);
    }
}
