using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace En16931.SemanticJson;

/// <summary>Whether a term identifier names a business term or a business group.</summary>
public enum TermKind
{
    /// <summary>A business term, which carries a value.</summary>
    Bt,

    /// <summary>A business group, which carries terms and further groups.</summary>
    Bg,
}

/// <summary>
/// One step of a semantic path: a term identifier, or an occurrence index following one.
/// </summary>
/// <param name="Text">the segment as the path writes it</param>
/// <param name="IsIndex">whether the segment is an occurrence index</param>
public readonly record struct PathSegment(string Text, bool IsIndex)
{
    /// <summary>Returns the kind of term the segment names.</summary>
    /// <exception cref="InvalidOperationException">if the segment is an index</exception>
    public TermKind Kind => IsIndex
        ? throw new InvalidOperationException("an index segment names no term")
        : Text.StartsWith("BT-", StringComparison.Ordinal) ? TermKind.Bt : TermKind.Bg;

    /// <summary>
    /// Tells whether the segment names an extension term, which carries a namespace between
    /// the kind and the number (specification, section 5.6).
    /// </summary>
    public bool IsExtension
    {
        get
        {
            if (IsIndex)
            {
                return false;
            }

            string rest = Text.Substring(3);
            return rest.Length > 0 && char.IsAsciiLetterUpper(rest[0]);
        }
    }
}

/// <summary>
/// A semantic path: the address of a value, or of a business group instance, as the
/// specification, section 5.1 writes it.
/// </summary>
public sealed class SemanticPath : IComparable<SemanticPath>, IEquatable<SemanticPath>
{
    private static readonly SemanticPath RootPath = new(string.Empty, Array.Empty<PathSegment>());

    private readonly PathSegment[] _segments;

    private SemanticPath(string text, PathSegment[] segments)
    {
        Text = text;
        _segments = segments;
    }

    /// <summary>Returns the path of the document itself, which has no segment.</summary>
    /// <returns>the root</returns>
    public static SemanticPath Root() => RootPath;

    /// <summary>Returns the path as the document writes it.</summary>
    public string Text { get; }

    /// <summary>Returns the segments of the path, outermost first.</summary>
    public IReadOnlyList<PathSegment> Segments => _segments;

    /// <summary>Tells whether this is the document itself.</summary>
    public bool IsRoot => _segments.Length == 0;

    /// <summary>Returns the identifier of the term the path ends at.</summary>
    /// <exception cref="InvalidOperationException">if this is the root</exception>
    public string Term
    {
        get
        {
            for (int at = _segments.Length - 1; at >= 0; at--)
            {
                if (!_segments[at].IsIndex)
                {
                    return _segments[at].Text;
                }
            }

            throw new InvalidOperationException("the root of a document names no term");
        }
    }

    /// <summary>Returns the term identifiers of the path, outermost first.</summary>
    /// <returns>the chain of identifiers, without the occurrence indices</returns>
    public IReadOnlyList<string> TermIds()
    {
        List<string> ids = new(_segments.Length);
        foreach (PathSegment segment in _segments)
        {
            if (!segment.IsIndex)
            {
                ids.Add(segment.Text);
            }
        }

        return ids;
    }

    /// <summary>
    /// Reads a path and checks it against the grammar of the specification, section 5.1: a
    /// chain of business group steps ending at a business term, each step optionally followed
    /// by one occurrence index.
    /// </summary>
    /// <param name="text">the path</param>
    /// <param name="path">the path, where the text is one</param>
    /// <returns>whether the text satisfies the grammar</returns>
    public static bool TryParse(string text, out SemanticPath path)
    {
        if (!TryParseAny(text, out path) || path.IsRoot)
        {
            path = RootPath;
            return false;
        }

        IReadOnlyList<PathSegment> segments = path.Segments;
        for (int at = 0; at < segments.Count; at++)
        {
            if (segments[at].IsIndex)
            {
                continue;
            }

            bool last = at == segments.Count - 1
                || (at == segments.Count - 2 && segments[at + 1].IsIndex);
            if (last != (segments[at].Kind == TermKind.Bt))
            {
                path = RootPath;
                return false;
            }
        }

        return true;
    }

    /// <summary>Reads a path, or a business group instance path, without checking a kind.</summary>
    /// <param name="text">the path</param>
    /// <returns>the path</returns>
    /// <exception cref="EsjFormatException">if the text is not a path</exception>
    public static SemanticPath Parse(string text)
    {
        if (text == "/" || text.Length == 0)
        {
            return RootPath;
        }

        if (!TryParseAny(text, out SemanticPath path))
        {
            throw new EsjFormatException("ESJ-L1-PATH-SYNTAX", text + " is not a semantic path");
        }

        return path;
    }

    /// <summary>
    /// Reads a path that may end at a business group, which is what a group instance is.
    /// </summary>
    /// <param name="text">the path</param>
    /// <param name="path">the path, where the text is one</param>
    /// <returns>whether the text is a chain of segments</returns>
    public static bool TryParseAny(string text, out SemanticPath path)
    {
        path = RootPath;
        if (text.Length == 0 || text == "/")
        {
            return true;
        }

        if (text[0] != '/')
        {
            return false;
        }

        string[] tokens = text.Substring(1).Split('/');
        List<PathSegment> segments = new(tokens.Length);
        bool previousWasIndex = true;
        foreach (string token in tokens)
        {
            if (token.Length == 0)
            {
                return false;
            }

            if (char.IsAsciiDigit(token[0]))
            {
                if (previousWasIndex || !IsIndexToken(token))
                {
                    return false;
                }

                segments.Add(new PathSegment(token, true));
                previousWasIndex = true;
                continue;
            }

            if (!IsTermToken(token))
            {
                return false;
            }

            segments.Add(new PathSegment(token, false));
            previousWasIndex = false;
        }

        path = new SemanticPath(text, segments.ToArray());
        return true;
    }

    private static bool IsIndexToken(string token) =>
        token == "0" || (token[0] != '0' && AllDigits(token));

    private static bool AllDigits(string token)
    {
        foreach (char character in token)
        {
            if (!char.IsAsciiDigit(character))
            {
                return false;
            }
        }

        return true;
    }

    private static bool IsTermToken(string token)
    {
        if (!token.StartsWith("BT-", StringComparison.Ordinal)
            && !token.StartsWith("BG-", StringComparison.Ordinal))
        {
            return false;
        }

        string rest = token.Substring(3);
        if (rest.Length == 0)
        {
            return false;
        }

        if (char.IsAsciiDigit(rest[0]))
        {
            return rest[0] != '0' && AllDigits(rest);
        }

        int dash = rest.IndexOf('-', StringComparison.Ordinal);
        if (dash <= 0 || dash == rest.Length - 1)
        {
            return false;
        }

        string space = rest.Substring(0, dash);
        if (!char.IsAsciiLetterUpper(space[0]))
        {
            return false;
        }

        foreach (char character in space)
        {
            if (!char.IsAsciiLetterUpper(character) && !char.IsAsciiDigit(character))
            {
                return false;
            }
        }

        return AllDigits(rest.Substring(dash + 1));
    }

    /// <summary>
    /// Tells whether this path lies at or below another one, which is what makes the subtree
    /// of a business group instance a range of the canonical order.
    /// </summary>
    /// <param name="prefix">the path the subtree hangs under</param>
    /// <returns>whether every segment of the prefix opens this path</returns>
    public bool StartsWith(SemanticPath prefix)
    {
        ArgumentNullException.ThrowIfNull(prefix);
        if (prefix._segments.Length > _segments.Length)
        {
            return false;
        }

        for (int at = 0; at < prefix._segments.Length; at++)
        {
            if (!string.Equals(prefix._segments[at].Text, _segments[at].Text, StringComparison.Ordinal)
                || prefix._segments[at].IsIndex != _segments[at].IsIndex)
            {
                return false;
            }
        }

        return true;
    }

    /// <summary>Returns the path made of the first segments of this one.</summary>
    /// <param name="count">how many segments to keep</param>
    /// <returns>the prefix</returns>
    public SemanticPath Prefix(int count)
    {
        if (count >= _segments.Length)
        {
            return this;
        }

        PathSegment[] segments = new PathSegment[count];
        Array.Copy(_segments, segments, count);
        StringBuilder text = new();
        foreach (PathSegment segment in segments)
        {
            text.Append('/').Append(segment.Text);
        }

        return new SemanticPath(text.ToString(), segments);
    }

    /// <summary>
    /// Compares two paths in the canonical path order of the specification, section 7.4.
    /// </summary>
    /// <param name="other">the path to compare with</param>
    /// <returns>a negative number, zero or a positive number</returns>
    public int CompareTo(SemanticPath? other)
    {
        if (other is null)
        {
            return 1;
        }

        int common = Math.Min(_segments.Length, other._segments.Length);
        for (int at = 0; at < common; at++)
        {
            int order = Compare(_segments[at], other._segments[at]);
            if (order != 0)
            {
                return order;
            }
        }

        return _segments.Length.CompareTo(other._segments.Length);
    }

    private static int Compare(PathSegment left, PathSegment right)
    {
        if (left.IsIndex != right.IsIndex)
        {
            return left.IsIndex ? 1 : -1;
        }

        if (left.IsIndex)
        {
            return CompareDigits(left.Text, right.Text);
        }

        int kinds = left.Kind.CompareTo(right.Kind);
        if (kinds != 0)
        {
            return kinds;
        }

        string leftSpace = NamespaceOf(left.Text);
        string rightSpace = NamespaceOf(right.Text);
        int spaces = string.CompareOrdinal(leftSpace, rightSpace);
        if (spaces != 0)
        {
            return leftSpace.Length == 0 ? -1 : rightSpace.Length == 0 ? 1 : spaces;
        }

        string leftNumber = NumberOf(left.Text);
        string rightNumber = NumberOf(right.Text);
        int numbers = CompareDigits(leftNumber, rightNumber);
        return numbers != 0 ? numbers : string.CompareOrdinal(left.Text, right.Text);
    }

    private static string NamespaceOf(string term)
    {
        string rest = term.Substring(3);
        int dash = rest.IndexOf('-', StringComparison.Ordinal);
        return dash < 0 ? string.Empty : rest.Substring(0, dash);
    }

    private static string NumberOf(string term)
    {
        string rest = term.Substring(3);
        int dash = rest.IndexOf('-', StringComparison.Ordinal);
        return dash < 0 ? rest : rest.Substring(dash + 1);
    }

    /// <summary>
    /// Compares two digit strings numerically, without converting either to a machine
    /// integer (specification, section 7.4).
    /// </summary>
    /// <param name="left">the first digit string</param>
    /// <param name="right">the second digit string</param>
    /// <returns>a negative number, zero or a positive number</returns>
    public static int CompareDigits(string left, string right)
    {
        string first = TrimZeros(left);
        string second = TrimZeros(right);
        return first.Length != second.Length
            ? first.Length.CompareTo(second.Length)
            : string.CompareOrdinal(first, second);
    }

    private static string TrimZeros(string digits)
    {
        int at = 0;
        while (at < digits.Length - 1 && digits[at] == '0')
        {
            at++;
        }

        return digits.Substring(at);
    }

    /// <inheritdoc />
    public bool Equals(SemanticPath? other) =>
        other is not null && string.Equals(Text, other.Text, StringComparison.Ordinal);

    /// <inheritdoc />
    public override bool Equals(object? obj) => Equals(obj as SemanticPath);

    /// <inheritdoc />
    public override int GetHashCode() => Text.GetHashCode(StringComparison.Ordinal);

    /// <inheritdoc />
    public override string ToString() => Text;

    /// <summary>The canonical path order of the specification, section 7.4, as a comparer.</summary>
    public static IComparer<SemanticPath> CanonicalOrder { get; } = Comparer<SemanticPath>.Create(
        (left, right) => left.CompareTo(right));
}
