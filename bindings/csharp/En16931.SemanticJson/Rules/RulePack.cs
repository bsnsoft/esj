using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text.Json;

namespace En16931.SemanticJson.Rules;

/// <summary>A second assertion a rule carries, whose failure decides no verdict.</summary>
/// <param name="Assertion">what is weighed where the first assertion holds</param>
/// <param name="Message">what the warning says</param>
public sealed record RuleWarning(JsonElement Assertion, string Message);

/// <summary>
/// One rule as a rule file writes it (<c>rules/README.md</c>): what it is called, how much it
/// weighs, where it is evaluated, what must hold, and what the finding says.
/// </summary>
public sealed class RuleDefinition
{
    private readonly List<string> _terms;
    private readonly Dictionary<string, JsonElement> _bindings;

    internal RuleDefinition(
        string id,
        RuleSeverity severity,
        string context,
        IEnumerable<string> terms,
        JsonElement assertion,
        IReadOnlyDictionary<string, JsonElement> bindings,
        string message,
        string source,
        string? note,
        RuleWarning? warning)
    {
        Id = id;
        Severity = severity;
        Context = context;
        _terms = new List<string>(terms);
        Assertion = assertion;
        _bindings = new Dictionary<string, JsonElement>(bindings, StringComparer.Ordinal);
        Message = message;
        Source = source;
        Note = note;
        Warning = warning;
    }

    /// <summary>Returns the identifier the standard gives the rule.</summary>
    public string Id { get; }

    /// <summary>Returns how much a finding of this rule weighs.</summary>
    public RuleSeverity Severity { get; }

    /// <summary>Returns what the rule is a statement about.</summary>
    public string Context { get; }

    /// <summary>Returns the business terms and groups the rule declares that it reads.</summary>
    public IReadOnlyList<string> Terms => _terms;

    /// <summary>Returns what must hold.</summary>
    public JsonElement Assertion { get; }

    /// <summary>Returns the expressions the message may show under a name.</summary>
    public IReadOnlyDictionary<string, JsonElement> Bindings => _bindings;

    /// <summary>Returns what the finding says.</summary>
    public string Message { get; }

    /// <summary>Returns the clause of the standard the rule states.</summary>
    public string Source { get; }

    /// <summary>Returns why the rule reads the way it does, where the pack says so.</summary>
    public string? Note { get; }

    /// <summary>Returns the second assertion, where the rule carries one.</summary>
    public RuleWarning? Warning { get; }

    /// <inheritdoc />
    public override string ToString() => Id + " at " + Context;
}

/// <summary>
/// A pack of business rules: the rules written in the rule language, the rules the language
/// cannot express, and the code list snapshots membership is decided against.
/// </summary>
public sealed class RulePack
{
    private readonly List<string> _nativeRules;
    private readonly List<RuleDefinition> _rules;
    private readonly Dictionary<string, string> _codeLists;

    internal RulePack(
        string id,
        string version,
        string verifiedAgainst,
        string description,
        IReadOnlyDictionary<string, string> codeLists,
        IEnumerable<string> nativeRules,
        IEnumerable<RuleDefinition> rules)
    {
        Id = id;
        Version = version;
        VerifiedAgainst = verifiedAgainst;
        Description = description;
        _codeLists = new Dictionary<string, string>(codeLists, StringComparer.Ordinal);
        _nativeRules = new List<string>(nativeRules);
        _rules = new List<RuleDefinition>(rules);
    }

    /// <summary>Returns the identifier of the pack.</summary>
    public string Id { get; }

    /// <summary>Returns the version of the pack, which a released version never changes.</summary>
    public string Version { get; }

    /// <summary>Returns the release of the official artefacts this pack was measured against.</summary>
    public string VerifiedAgainst { get; }

    /// <summary>Returns what the pack says about itself.</summary>
    public string Description { get; }

    /// <summary>Returns which day's snapshot of each code list the pack decides against.</summary>
    public IReadOnlyDictionary<string, string> CodeLists => _codeLists;

    /// <summary>
    /// Returns the rules the language cannot express, by the name the manifest gives them.
    /// </summary>
    /// <remarks>
    /// The manifest names them; it does not load them. A file that could name a class the
    /// engine then instantiates would be a file that decides what code runs, and a pack may
    /// arrive from a directory a caller was handed. The caller passes the instances to
    /// <see cref="RuleEngine.Compile(RulePack, Model.Registry, CodeLists, NativeRules)"/>, which checks that exactly the named rules arrived.
    /// A binding in another language carries its own implementation of each of them and
    /// declares it under the name the manifest writes.
    /// </remarks>
    public IReadOnlyList<string> NativeRules => _nativeRules;

    /// <summary>Returns the rules written in the rule language.</summary>
    public IReadOnlyList<RuleDefinition> Rules => _rules;

    /// <summary>Returns the pack as a report names it.</summary>
    public string Name => Id + "/" + Version;

    /// <inheritdoc />
    public override string ToString() =>
        Name + " (" + _rules.Count + " rules in the rule language, " + _nativeRules.Count + " written in code)";
}

/// <summary>Reads a rule pack: the manifest, the rule files it names, and nothing else.</summary>
public static class RulePacks
{
    /// <summary>Returns the directory a bundled pack is read from.</summary>
    /// <param name="id">the pack identifier</param>
    /// <param name="version">the version</param>
    /// <returns>the resource directory, ending in a solidus</returns>
    public static string ResourceDirectory(string id, string version) => "rules/" + id + "/" + version + "/";

    /// <summary>Returns a pack this build carries.</summary>
    /// <param name="id">the pack identifier</param>
    /// <param name="version">the version</param>
    /// <returns>the pack</returns>
    /// <exception cref="RulePackException">if this build carries no such pack</exception>
    public static RulePack Bundled(string id, string version)
    {
        ArgumentNullException.ThrowIfNull(id);
        ArgumentNullException.ThrowIfNull(version);
        string directory = ResourceDirectory(id, version);
        JsonElement manifest = Read(directory + "pack.json");
        List<RuleDefinition> rules = new();
        if (manifest.TryGetProperty("files", out JsonElement files))
        {
            foreach (JsonElement file in files.EnumerateArray())
            {
                rules.AddRange(Read(directory + file.GetString()!).EnumerateArray().Select(ReadRule));
            }
        }

        return Pack(manifest, rules);
    }

    /// <summary>Reads a pack whose rules all stand in one file, such as a pack a caller wrote.</summary>
    /// <remarks>
    /// It is the counterpart of <see cref="Bundled"/> for a pack that does not travel in this
    /// build, and it reads what the rule language says a pack is. A pack that names rule files
    /// beside its manifest is refused: this stream is all there is to read, and a pack
    /// compiled without the rules of those files would check less than it claims.
    /// </remarks>
    /// <param name="input">the pack file, UTF-8; the caller closes it</param>
    /// <param name="what">what is being read, for the message of a failure</param>
    /// <returns>the pack</returns>
    /// <exception cref="RulePackException">if the bytes are not a pack of the rule language, or
    /// the pack names rule files</exception>
    public static RulePack Read(Stream input, string what)
    {
        ArgumentNullException.ThrowIfNull(input);
        ArgumentNullException.ThrowIfNull(what);
        JsonElement manifest;
        try
        {
            using JsonDocument json = JsonDocument.Parse(input);
            manifest = json.RootElement.Clone();
        }
        catch (JsonException malformed)
        {
            throw new RulePackException(what + " is not a JSON document: " + malformed.Message, malformed);
        }

        if (manifest.ValueKind != JsonValueKind.Object)
        {
            throw new RulePackException(what + " is not a rule pack: a pack is a JSON object");
        }

        if (manifest.TryGetProperty("files", out JsonElement _))
        {
            throw new RulePackException(what + " names rule files beside it, and a pack read from"
                + " one stream carries its rules in that stream");
        }

        return Pack(manifest, Enumerable.Empty<RuleDefinition>());
    }

    /// <summary>Builds a pack from its manifest and the rules of the files it names.</summary>
    private static RulePack Pack(JsonElement manifest, IEnumerable<RuleDefinition> fromFiles)
    {
        List<RuleDefinition> rules = new();
        if (manifest.TryGetProperty("rules", out JsonElement written))
        {
            rules.AddRange(written.EnumerateArray().Select(ReadRule));
        }

        rules.AddRange(fromFiles);
        Dictionary<string, string> codeLists = new(StringComparer.Ordinal);
        if (manifest.TryGetProperty("codeLists", out JsonElement named))
        {
            foreach (JsonProperty list in named.EnumerateObject())
            {
                codeLists[list.Name] = list.Value.GetString()!;
            }
        }

        List<string> nativeRules = new();
        if (manifest.TryGetProperty("javaRules", out JsonElement declared))
        {
            nativeRules.AddRange(declared.EnumerateArray().Select(name => name.GetString()!));
        }

        return new RulePack(
            Text(manifest, "id"),
            Text(manifest, "version"),
            Text(manifest, "verifiedAgainst"),
            Text(manifest, "description"),
            codeLists,
            nativeRules,
            rules);
    }

    /// <summary>Returns the bytes of a file of a bundled pack.</summary>
    /// <param name="resource">the path of the file inside the build</param>
    /// <returns>the stream, which the caller closes</returns>
    /// <exception cref="RulePackException">if this build carries no such file</exception>
    internal static Stream Open(string resource) =>
        typeof(RulePacks).GetTypeInfo().Assembly.GetManifestResourceStream(resource)
        ?? throw new RulePackException("this build carries no " + resource);

    private static JsonElement Read(string resource)
    {
        using Stream stream = Open(resource);
        using JsonDocument json = JsonDocument.Parse(stream);
        return json.RootElement.Clone();
    }

    private static RuleDefinition ReadRule(JsonElement json)
    {
        string id = Text(json, "id");
        string token = Text(json, "severity");
        RuleSeverity severity = token switch
        {
            "fatal" => RuleSeverity.Fatal,
            "warning" => RuleSeverity.Warning,
            _ => throw new RulePackException("the rule " + id + " declares the severity " + token
                + ", which is not one a rule file may declare"),
        };

        Dictionary<string, JsonElement> bindings = new(StringComparer.Ordinal);
        if (json.TryGetProperty("bind", out JsonElement bound))
        {
            foreach (JsonProperty binding in bound.EnumerateObject())
            {
                bindings[binding.Name] = binding.Value;
            }
        }

        RuleWarning? warning = null;
        if (json.TryGetProperty("warn", out JsonElement second))
        {
            warning = new RuleWarning(second.GetProperty("assert"), Text(second, "message"));
        }

        return new RuleDefinition(
            id,
            severity,
            Text(json, "context"),
            json.TryGetProperty("terms", out JsonElement terms)
                ? terms.EnumerateArray().Select(term => term.GetString()!).ToList()
                : new List<string>(),
            json.GetProperty("assert"),
            bindings,
            Text(json, "message"),
            Text(json, "source"),
            json.TryGetProperty("note", out JsonElement note) ? note.GetString() : null,
            warning);
    }

    private static string Text(JsonElement json, string member) =>
        json.TryGetProperty(member, out JsonElement value) && value.ValueKind == JsonValueKind.String
            ? value.GetString()!
            : throw new RulePackException("a rule pack carries the string member " + member);
}
