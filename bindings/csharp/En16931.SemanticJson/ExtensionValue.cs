using System;
using System.Collections.Generic;

namespace En16931.SemanticJson;

/// <summary>What kind of JSON value an <see cref="ExtensionValue"/> is.</summary>
public enum ExtensionKind
{
    /// <summary>A JSON string.</summary>
    String,

    /// <summary>A JSON number, kept as the document spells it.</summary>
    Number,

    /// <summary>A JSON boolean.</summary>
    Boolean,

    /// <summary>The JSON literal <c>null</c>.</summary>
    Null,

    /// <summary>A JSON array.</summary>
    Array,

    /// <summary>A JSON object.</summary>
    Object,
}

/// <summary>
/// A value inside <c>extensions</c>, where the JSON is free (specification, section 4.6).
/// </summary>
/// <remarks>
/// A number keeps the spelling the document gave it. The canonical form of section 7.6
/// derives its own spelling from those digits by string processing, so no number of a
/// document ever passes through a binary floating point value on its way through this
/// implementation.
/// </remarks>
public sealed class ExtensionValue
{
    private static readonly ExtensionValue NullValue = new(ExtensionKind.Null, null, null, null);

    private readonly string? _text;
    private readonly List<ExtensionValue>? _elements;
    private readonly List<KeyValuePair<string, ExtensionValue>>? _members;

    private ExtensionValue(
        ExtensionKind kind,
        string? text,
        List<ExtensionValue>? elements,
        List<KeyValuePair<string, ExtensionValue>>? members)
    {
        Kind = kind;
        _text = text;
        _elements = elements;
        _members = members;
    }

    /// <summary>Returns what kind of JSON value this is.</summary>
    public ExtensionKind Kind { get; }

    /// <summary>Returns a string value.</summary>
    /// <param name="text">the string</param>
    /// <returns>the value</returns>
    public static ExtensionValue OfString(string text) => new(ExtensionKind.String, text, null, null);

    /// <summary>Returns a number, as the document spells it.</summary>
    /// <param name="lexical">the number token</param>
    /// <returns>the value</returns>
    public static ExtensionValue OfNumber(string lexical) => new(ExtensionKind.Number, lexical, null, null);

    /// <summary>Returns a boolean value.</summary>
    /// <param name="value">the truth value</param>
    /// <returns>the value</returns>
    public static ExtensionValue OfBoolean(bool value) =>
        new(ExtensionKind.Boolean, value ? "true" : "false", null, null);

    /// <summary>Returns the JSON literal <c>null</c>.</summary>
    /// <returns>the value</returns>
    public static ExtensionValue OfNull() => NullValue;

    /// <summary>Returns an array.</summary>
    /// <param name="elements">the elements, in the order the document writes them</param>
    /// <returns>the value</returns>
    public static ExtensionValue OfArray(IEnumerable<ExtensionValue> elements)
    {
        ArgumentNullException.ThrowIfNull(elements);
        return new ExtensionValue(ExtensionKind.Array, null, new List<ExtensionValue>(elements), null);
    }

    /// <summary>
    /// Returns an object. The members are sorted by Unicode code point, which is the order
    /// the canonical form asks for at every level inside <c>extensions</c> (specification,
    /// section 7.6) and therefore the order this model keeps them in.
    /// </summary>
    /// <param name="members">the members, in any order</param>
    /// <returns>the value</returns>
    public static ExtensionValue OfObject(IEnumerable<KeyValuePair<string, ExtensionValue>> members)
    {
        ArgumentNullException.ThrowIfNull(members);
        List<KeyValuePair<string, ExtensionValue>> sorted = new(members);
        sorted.Sort((left, right) => Json.Texts.CompareByCodePoint(left.Key, right.Key));
        return new ExtensionValue(ExtensionKind.Object, null, null, sorted);
    }

    /// <summary>Returns the string, the number token or the boolean word.</summary>
    /// <exception cref="InvalidOperationException">if the value is an array, an object or null</exception>
    public string Text => _text ?? throw new InvalidOperationException("this value carries no text");

    /// <summary>Returns the elements of an array.</summary>
    public IReadOnlyList<ExtensionValue> Elements =>
        _elements ?? (IReadOnlyList<ExtensionValue>)Array.Empty<ExtensionValue>();

    /// <summary>Returns the members of an object, in the order the document writes them.</summary>
    public IReadOnlyList<KeyValuePair<string, ExtensionValue>> Members =>
        _members ?? (IReadOnlyList<KeyValuePair<string, ExtensionValue>>)Array.Empty<KeyValuePair<string, ExtensionValue>>();
}
