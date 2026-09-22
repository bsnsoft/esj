using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace En16931.SemanticJson.Rules;

/// <summary>
/// A dated snapshot of a code list a rule decides membership against.
/// </summary>
/// <remarks>
/// A rule asks <c>inList</c> about a list identifier and the pack manifest says which day's
/// snapshot of that list this pack decides against, so two runs of the same pack version over
/// the same document give the same verdict however far apart they are. That is the only way a
/// validation report keeps its meaning in an archive.
/// </remarks>
public sealed class CodeList
{
    private readonly Dictionary<string, string> _entries;

    internal CodeList(
        string listId, string name, string publisher, string source, string retrieved,
        Dictionary<string, string> entries)
    {
        ListId = listId;
        Name = name;
        Publisher = publisher;
        Source = source;
        Retrieved = retrieved;
        _entries = entries;
    }

    /// <summary>Returns the identifier a rule names the list by.</summary>
    public string ListId { get; }

    /// <summary>Returns the name of the list.</summary>
    public string Name { get; }

    /// <summary>Returns who publishes it.</summary>
    public string Publisher { get; }

    /// <summary>Returns where the snapshot was taken from.</summary>
    public string Source { get; }

    /// <summary>Returns the day the snapshot was taken.</summary>
    public string Retrieved { get; }

    /// <summary>Returns how many codes the snapshot holds.</summary>
    public int Size => _entries.Count;

    /// <summary>Tells whether a code is on the snapshot.</summary>
    /// <param name="code">the code</param>
    /// <returns>whether the list carries it</returns>
    public bool Contains(string code)
    {
        ArgumentNullException.ThrowIfNull(code);
        return _entries.ContainsKey(code);
    }

    /// <summary>Returns what the publisher calls a code, or <c>null</c>.</summary>
    /// <param name="code">the code</param>
    /// <returns>the name, or <c>null</c> where the list does not carry the code</returns>
    public string? Describe(string code) => _entries.TryGetValue(code, out string? name) ? name : null;

    /// <inheritdoc />
    public override string ToString() =>
        ListId + " (" + Publisher + ", " + Retrieved + ", " + _entries.Count + " codes)";
}

/// <summary>The snapshots one pack decides membership against.</summary>
public sealed class CodeLists
{
    private readonly Dictionary<string, CodeList> _byId;

    private CodeLists(Dictionary<string, CodeList> byId)
    {
        _byId = byId;
    }

    /// <summary>Returns a set of snapshots.</summary>
    /// <param name="lists">the snapshots</param>
    /// <returns>the set</returns>
    public static CodeLists Of(IEnumerable<CodeList> lists)
    {
        ArgumentNullException.ThrowIfNull(lists);
        Dictionary<string, CodeList> byId = new(StringComparer.Ordinal);
        foreach (CodeList list in lists)
        {
            if (!byId.TryAdd(list.ListId, list))
            {
                throw new RulePackException("two snapshots carry the identifier " + list.ListId);
            }
        }

        return new CodeLists(byId);
    }

    /// <summary>Returns no snapshots at all.</summary>
    /// <returns>the empty set</returns>
    public static CodeLists Empty() => new(new Dictionary<string, CodeList>(StringComparer.Ordinal));

    /// <summary>Returns the snapshots a bundled pack names, as this build carries them.</summary>
    /// <param name="pack">the pack</param>
    /// <returns>the snapshots</returns>
    /// <exception cref="RulePackException">if a snapshot the pack names is not in this build</exception>
    public static CodeLists Bundled(RulePack pack)
    {
        ArgumentNullException.ThrowIfNull(pack);
        List<CodeList> lists = new();
        foreach (KeyValuePair<string, string> named in pack.CodeLists)
        {
            string resource = RulePacks.ResourceDirectory(pack.Id, pack.Version)
                + "codelists/" + named.Key + "/" + named.Value + ".json";
            using Stream stream = RulePacks.Open(resource);
            lists.Add(Read(stream, named.Key + " of " + named.Value));
        }

        return Of(lists);
    }

    /// <summary>Reads one snapshot.</summary>
    /// <param name="input">the snapshot file</param>
    /// <param name="what">what is being read, for the message of a failure</param>
    /// <returns>the snapshot</returns>
    public static CodeList Read(Stream input, string what)
    {
        ArgumentNullException.ThrowIfNull(input);
        using JsonDocument json = JsonDocument.Parse(input);
        JsonElement root = json.RootElement;
        Dictionary<string, string> entries = new(StringComparer.Ordinal);
        foreach (JsonElement entry in root.GetProperty("entries").EnumerateArray())
        {
            string value = entry.GetProperty("value").GetString()!;
            string name = entry.TryGetProperty("name", out JsonElement written)
                ? written.GetString() ?? string.Empty
                : string.Empty;
            if (!entries.TryAdd(value, name))
            {
                throw new RulePackException(
                    "the code list snapshot " + what + " carries the code " + value + " twice");
            }
        }

        return new CodeList(
            root.GetProperty("listId").GetString()!,
            root.GetProperty("name").GetString()!,
            root.GetProperty("publisher").GetString()!,
            root.GetProperty("source").GetString()!,
            root.GetProperty("retrieved").GetString()!,
            entries);
    }

    /// <summary>Returns the identifiers of the snapshots this set holds.</summary>
    public IReadOnlyCollection<string> ListIds => _byId.Keys.ToList();

    /// <summary>Returns a snapshot, or <c>null</c> where this set holds none.</summary>
    /// <param name="listId">the identifier of the list</param>
    /// <returns>the snapshot, or <c>null</c></returns>
    public CodeList? List(string listId)
    {
        ArgumentNullException.ThrowIfNull(listId);
        return _byId.TryGetValue(listId, out CodeList? list) ? list : null;
    }

    /// <summary>
    /// Returns a snapshot, or refuses: a membership test against a list nobody loaded would
    /// pass every code, and a rule that silently passes everything is worse than a pack that
    /// will not start.
    /// </summary>
    /// <param name="listId">the identifier of the list</param>
    /// <returns>the snapshot</returns>
    /// <exception cref="RulePackException">if this set holds no such snapshot</exception>
    public CodeList Require(string listId) => List(listId)
        ?? throw new RulePackException("no snapshot of the code list " + listId + " was loaded");
}
