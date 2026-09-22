using System;
using System.Collections.Generic;

namespace En16931.SemanticJson;

/// <summary>Where a document came from (specification, section 4.7).</summary>
/// <param name="Syntax">the syntax the content was extracted from, or <c>null</c></param>
/// <param name="Sha256">the digest of the source bytes, or <c>null</c></param>
public sealed record DocumentSource(string? Syntax, string? Sha256);

/// <summary>
/// An ESJ document: the edition it addresses, its values in canonical path order, and what
/// the envelope says around them.
/// </summary>
/// <remarks>
/// The values are held in the canonical path order of the specification, section 7.4 and the
/// extension subtrees under owner tokens sorted by Unicode code point, so that a document
/// assembled through this API serializes to the same bytes as the same document read from a
/// file, whatever order either was built in.
/// </remarks>
public sealed class SemanticDocument
{
    private readonly SortedDictionary<SemanticPath, SemanticValue> _values;
    private readonly List<KeyValuePair<string, ExtensionValue>> _extensions;

    /// <summary>Creates a document.</summary>
    /// <param name="semanticModel">the edition the paths address</param>
    /// <param name="values">the values, keyed by path</param>
    /// <param name="extensions">the extension subtrees, keyed by owner token</param>
    /// <param name="source">where the document came from, or <c>null</c></param>
    public SemanticDocument(
        string semanticModel,
        IEnumerable<KeyValuePair<SemanticPath, SemanticValue>> values,
        IEnumerable<KeyValuePair<string, ExtensionValue>>? extensions = null,
        DocumentSource? source = null)
    {
        ArgumentNullException.ThrowIfNull(semanticModel);
        ArgumentNullException.ThrowIfNull(values);
        SemanticModel = semanticModel;
        _values = new SortedDictionary<SemanticPath, SemanticValue>(SemanticPath.CanonicalOrder);
        foreach (KeyValuePair<SemanticPath, SemanticValue> value in values)
        {
            _values[value.Key] = value.Value;
        }

        _extensions = extensions is null
            ? new List<KeyValuePair<string, ExtensionValue>>()
            : new List<KeyValuePair<string, ExtensionValue>>(extensions);
        _extensions.Sort((left, right) => Json.Texts.CompareByCodePoint(left.Key, right.Key));
        Source = source;
    }

    /// <summary>Returns the edition the paths of this document address.</summary>
    public string SemanticModel { get; }

    /// <summary>Returns the values, in canonical path order.</summary>
    public IReadOnlyDictionary<SemanticPath, SemanticValue> Values => _values;

    /// <summary>Returns the paths and values in canonical path order.</summary>
    public IEnumerable<KeyValuePair<SemanticPath, SemanticValue>> Entries => _values;

    /// <summary>Returns the extension subtrees, under owner tokens in code point order.</summary>
    public IReadOnlyList<KeyValuePair<string, ExtensionValue>> Extensions => _extensions;

    /// <summary>Returns where the document came from, or <c>null</c>.</summary>
    public DocumentSource? Source { get; }

    /// <summary>Returns the value at a path, or <c>null</c> where the document has none.</summary>
    /// <param name="path">the path</param>
    /// <returns>the value, or <c>null</c></returns>
    public SemanticValue? Value(SemanticPath path)
    {
        ArgumentNullException.ThrowIfNull(path);
        return _values.TryGetValue(path, out SemanticValue? value) ? value : null;
    }

    /// <summary>Returns the value at a path, or <c>null</c> where the document has none.</summary>
    /// <param name="path">the path, as a document writes it</param>
    /// <returns>the value, or <c>null</c></returns>
    public SemanticValue? Value(string path) => Value(SemanticPath.Parse(path));

    /// <summary>
    /// Returns a copy of this document with one value set, or removed where the value is
    /// <c>null</c>.
    /// </summary>
    /// <param name="path">the path</param>
    /// <param name="value">the value, or <c>null</c> to remove it</param>
    /// <returns>the new document</returns>
    public SemanticDocument With(SemanticPath path, SemanticValue? value)
    {
        ArgumentNullException.ThrowIfNull(path);
        SortedDictionary<SemanticPath, SemanticValue> values = new(_values, SemanticPath.CanonicalOrder);
        if (value is null)
        {
            values.Remove(path);
        }
        else
        {
            values[path] = value;
        }

        return new SemanticDocument(SemanticModel, values, _extensions, Source);
    }

    /// <inheritdoc />
    public override string ToString() =>
        "SemanticDocument[" + SemanticModel + ", values=" + _values.Count
        + ", extensions=" + _extensions.Count + ", source=" + (Source is null ? "absent" : "present") + "]";
}
