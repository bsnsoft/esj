using System;
using System.Collections.Generic;
using System.Globalization;

namespace En16931.SemanticJson.Rules;

/// <summary>
/// What an expression of the rule language evaluates to: a number, a date, text, a truth
/// value, or nothing at all.
/// </summary>
/// <remarks>
/// The absence is the interesting part. An invoice is a document in which most terms are
/// optional, so an expression that reads one asks a question that may have no answer.
/// Treating a missing amount as zero would invent a figure the invoice does not carry;
/// treating a missing value as a failure would make one omission fail every rule that mentions
/// the term, and the cardinality layer has already said so once. So absence propagates, and an
/// assertion that comes out absent is a rule that could not be decided and reports nothing.
/// </remarks>
public readonly struct RuleValue : IEquatable<RuleValue>
{
    /// <summary>The one absent value.</summary>
    public static readonly RuleValue Absent = default;

    /// <summary>The one true value.</summary>
    public static readonly RuleValue True = new(null, null, null, true);

    /// <summary>The one false value.</summary>
    public static readonly RuleValue False = new(null, null, null, false);

    private readonly BigDecimal? _decimal;
    private readonly DateOnly? _date;
    private readonly string? _text;
    private readonly bool? _truth;

    private RuleValue(BigDecimal? number, DateOnly? date, string? text, bool? truth)
    {
        _decimal = number;
        _date = date;
        _text = text;
        _truth = truth;
    }

    /// <summary>Returns a number.</summary>
    /// <param name="value">the number</param>
    /// <returns>the value</returns>
    public static RuleValue Of(BigDecimal value) => new(value, null, null, null);

    /// <summary>Returns a date.</summary>
    /// <param name="value">the date</param>
    /// <returns>the value</returns>
    public static RuleValue Of(DateOnly value) => new(null, value, null, null);

    /// <summary>Returns text.</summary>
    /// <param name="value">the text</param>
    /// <returns>the value</returns>
    public static RuleValue Of(string value) => new(null, null, value, null);

    /// <summary>Returns a truth value.</summary>
    /// <param name="value">the truth</param>
    /// <returns>the value</returns>
    public static RuleValue Of(bool value) => value ? True : False;

    /// <summary>Returns a whole number.</summary>
    /// <param name="value">the number</param>
    /// <returns>the value</returns>
    public static RuleValue Of(long value) => Of(BigDecimal.FromInt64(value));

    /// <summary>Tells whether the expression had no answer.</summary>
    public bool IsAbsent => _decimal is null && _date is null && _text is null && _truth is null;

    /// <summary>Tells whether the value is a number.</summary>
    public bool IsDecimal => _decimal is not null;

    /// <summary>Tells whether the value is a date.</summary>
    public bool IsDate => _date is not null;

    /// <summary>Tells whether the value is text.</summary>
    public bool IsText => _text is not null;

    /// <summary>Tells whether the value is a truth value.</summary>
    public bool IsBoolean => _truth is not null;

    /// <summary>Returns the number.</summary>
    public BigDecimal Decimal => _decimal!.Value;

    /// <summary>Returns the date.</summary>
    public DateOnly Date => _date!.Value;

    /// <summary>Returns the text.</summary>
    public string Text => _text!;

    /// <summary>Returns the truth, or <c>null</c> where the value is absent.</summary>
    public bool? Truth => _truth;

    /// <summary>
    /// Returns the value as it appears in a message. A decimal keeps the scale the arithmetic
    /// produced, because that is what the rule compared; an absent value reads as the two
    /// words that say so, which no business term value can be mistaken for.
    /// </summary>
    /// <returns>the text for a message</returns>
    public string Display()
    {
        if (_decimal is not null)
        {
            return _decimal.Value.ToPlainString();
        }

        if (_date is not null)
        {
            return _date.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        }

        if (_text is not null)
        {
            return Esj.ForMessage(_text);
        }

        return _truth is not null
            ? _truth.Value ? "true" : "false"
            : "(absent)";
    }

    /// <inheritdoc />
    public bool Equals(RuleValue other) =>
        Nullable.Equals(_decimal, other._decimal)
        && Nullable.Equals(_date, other._date)
        && string.Equals(_text, other._text, StringComparison.Ordinal)
        && Nullable.Equals(_truth, other._truth);

    /// <inheritdoc />
    public override bool Equals(object? obj) => obj is RuleValue other && Equals(other);

    /// <inheritdoc />
    public override int GetHashCode() => HashCode.Combine(_decimal, _date, _text, _truth);

    /// <inheritdoc />
    public override string ToString() => Display();
}

/// <summary>
/// One run of one compiled pack over one document: the document, what the pack may ask it,
/// and the answers that are worth keeping.
/// </summary>
/// <remarks>
/// The index answers every question asked from the document root out of a single pass over the
/// values, and an aggregate taken from the root is computed once for the whole run, so a pack
/// in which ten rules mention the sum of the line net amounts walks the lines once and not ten
/// times. The read list is what a rule looked at, in the order it looked, which is what a
/// finding reports; a shared answer carries the reads that made it and replays them to every
/// rule it is handed to, so that sharing an answer does not move one rule's reads into another
/// rule's finding.
/// </remarks>
public sealed class Evaluation
{
    private readonly SemanticDocument _document;
    private readonly List<KeyValuePair<SemanticPath, SemanticValue>> _sorted = new();
    private readonly Dictionary<string, List<KeyValuePair<SemanticPath, SemanticValue>>> _byValueKey =
        new(StringComparer.Ordinal);

    private readonly Dictionary<string, List<SemanticPath>> _byGroupKey = new(StringComparer.Ordinal);
    private readonly Dictionary<string, RuleValue> _aggregates = new(StringComparer.Ordinal);
    private readonly Dictionary<string, Memo> _shared = new(StringComparer.Ordinal);

    private List<string> _reads = new();
    private HashSet<string> _seen = new(StringComparer.Ordinal);

    internal Evaluation(SemanticDocument document, CodeLists codeLists)
    {
        _document = document;
        CodeLists = codeLists;
        Index();
    }

    /// <summary>Returns the snapshots this run decides membership against.</summary>
    public CodeLists CodeLists { get; }

    /// <summary>Returns the document this run is over.</summary>
    public SemanticDocument Document => _document;

    /// <summary>Starts a rule instance with a fresh read list.</summary>
    internal void Begin()
    {
        _reads = new List<string>();
        _seen = new HashSet<string>(StringComparer.Ordinal);
    }

    /// <summary>Records that a rule looked at a path or at a pattern.</summary>
    /// <param name="pathOrPattern">what was read</param>
    public void Record(string pathOrPattern)
    {
        ArgumentNullException.ThrowIfNull(pathOrPattern);
        if (_seen.Add(pathOrPattern))
        {
            _reads.Add(pathOrPattern);
        }
    }

    /// <summary>Returns what the rule looked at, in the order it looked.</summary>
    internal IReadOnlyList<string> Reads => new List<string>(_reads);

    /// <summary>Reads the one value a pattern without an asterisk addresses.</summary>
    /// <param name="pattern">the pattern</param>
    /// <param name="basePath">the instance the rule is evaluated at</param>
    /// <param name="type">the semantic data type the registry gives the term</param>
    /// <returns>the value, or absent where the document does not carry it</returns>
    public RuleValue Read(PathPattern pattern, SemanticPath basePath, SemanticType type)
    {
        ArgumentNullException.ThrowIfNull(pattern);
        SemanticPath path = pattern.Concrete(basePath);
        Record(path.Text);
        SemanticValue? value = _document.Value(path);
        return value is null ? RuleValue.Absent : Convert(value, type, path);
    }

    /// <summary>Reads the one value a pattern addresses, without converting it.</summary>
    /// <param name="pattern">the pattern</param>
    /// <param name="basePath">the instance the rule is evaluated at</param>
    /// <returns>the value, or <c>null</c> where the document does not carry it</returns>
    public SemanticValue? Raw(PathPattern pattern, SemanticPath basePath)
    {
        ArgumentNullException.ThrowIfNull(pattern);
        SemanticPath path = pattern.Concrete(basePath);
        Record(path.Text);
        return _document.Value(path);
    }

    /// <summary>Converts the content of a value to what its semantic data type says it is.</summary>
    /// <param name="value">the value</param>
    /// <param name="type">the semantic data type</param>
    /// <param name="path">the path, for the message of a failure</param>
    /// <returns>the converted value</returns>
    /// <exception cref="UndecidedException">if the content does not spell what the type requires</exception>
    public static RuleValue Convert(SemanticValue value, SemanticType type, SemanticPath path)
    {
        ArgumentNullException.ThrowIfNull(value);
        ArgumentNullException.ThrowIfNull(path);
        try
        {
            if (type.IsDecimal())
            {
                return RuleValue.Of(value.AsDecimal());
            }

            return type == SemanticType.Date
                ? RuleValue.Of(value.AsDate())
                : RuleValue.Of(value.Content);
        }
        catch (EsjFormatException violation)
        {
            throw new UndecidedException("the value at " + path + " is not a "
                + type.RegistryDatatype() + ": " + violation.Message);
        }
    }

    /// <summary>Returns the values a pattern addresses inside an instance, in canonical order.</summary>
    /// <param name="pattern">the pattern</param>
    /// <param name="basePath">the instance the rule is evaluated at</param>
    /// <returns>the matching values with their paths</returns>
    public IReadOnlyList<KeyValuePair<SemanticPath, SemanticValue>> Matches(
        PathPattern pattern, SemanticPath basePath)
    {
        ArgumentNullException.ThrowIfNull(pattern);
        ArgumentNullException.ThrowIfNull(basePath);
        Record(pattern.AbsoluteText(basePath));
        if (!pattern.HasWildcard)
        {
            SemanticPath path = pattern.Concrete(basePath);
            SemanticValue? value = _document.Value(path);
            return value is null
                ? Array.Empty<KeyValuePair<SemanticPath, SemanticValue>>()
                : new[] { new KeyValuePair<SemanticPath, SemanticValue>(path, value) };
        }

        if (basePath.IsRoot)
        {
            return _byValueKey.TryGetValue(pattern.AbsoluteText(basePath),
                out List<KeyValuePair<SemanticPath, SemanticValue>>? found)
                ? found
                : Array.Empty<KeyValuePair<SemanticPath, SemanticValue>>();
        }

        string key = PathPattern.Key(basePath) + pattern.Text;
        List<KeyValuePair<SemanticPath, SemanticValue>> matches = new();
        foreach (KeyValuePair<SemanticPath, SemanticValue> entry in Within(pattern.ConcretePrefix(basePath)))
        {
            if (string.Equals(PathPattern.Key(entry.Key), key, StringComparison.Ordinal))
            {
                matches.Add(entry);
            }
        }

        return matches;
    }

    /// <summary>
    /// Returns the business group instances a pattern addresses inside an instance, in
    /// canonical order.
    /// </summary>
    /// <param name="pattern">the pattern, which ends at a business group</param>
    /// <param name="basePath">the instance the rule is evaluated at</param>
    /// <returns>the instances</returns>
    public IReadOnlyList<SemanticPath> Instances(PathPattern pattern, SemanticPath basePath)
    {
        ArgumentNullException.ThrowIfNull(pattern);
        ArgumentNullException.ThrowIfNull(basePath);
        Record(pattern.AbsoluteText(basePath));
        if (!pattern.HasWildcard)
        {
            SemanticPath path = pattern.Concrete(basePath);
            return HasAnythingUnder(path) ? new[] { path } : Array.Empty<SemanticPath>();
        }

        if (basePath.IsRoot)
        {
            return _byGroupKey.TryGetValue(pattern.AbsoluteText(basePath), out List<SemanticPath>? found)
                ? found
                : Array.Empty<SemanticPath>();
        }

        string key = PathPattern.Key(basePath) + pattern.Text;
        int depth = basePath.Segments.Count + pattern.SegmentCount;
        List<SemanticPath> instances = new();
        foreach (KeyValuePair<SemanticPath, SemanticValue> entry in Within(pattern.ConcretePrefix(basePath)))
        {
            if (entry.Key.Segments.Count < depth)
            {
                continue;
            }

            SemanticPath instance = entry.Key.Prefix(depth);
            if (string.Equals(PathPattern.Key(instance), key, StringComparison.Ordinal)
                && (instances.Count == 0 || !instances[instances.Count - 1].Equals(instance)))
            {
                instances.Add(instance);
            }
        }

        return instances;
    }

    /// <summary>
    /// Returns a remembered answer of a rule written in code, or computes and remembers it.
    /// </summary>
    /// <typeparam name="T">what the answer is</typeparam>
    /// <param name="key">what identifies the answer, the base path included</param>
    /// <param name="remember">whether the answer is about the document as a whole</param>
    /// <param name="compute">how to compute it</param>
    /// <returns>the answer</returns>
    public T Shared<T>(string key, bool remember, Func<T> compute)
    {
        ArgumentNullException.ThrowIfNull(key);
        ArgumentNullException.ThrowIfNull(compute);
        if (!remember)
        {
            return compute();
        }

        if (_shared.TryGetValue(key, out Memo? known))
        {
            foreach (string read in known.Reads)
            {
                Record(read);
            }

            return (T)known.Answer!;
        }

        List<string> outerReads = _reads;
        HashSet<string> outerSeen = _seen;
        object? computed;
        List<string> made;
        _reads = new List<string>();
        _seen = new HashSet<string>(StringComparer.Ordinal);
        try
        {
            computed = compute();
            made = new List<string>(_reads);
        }
        finally
        {
            _reads = outerReads;
            _seen = outerSeen;
        }

        foreach (string read in made)
        {
            Record(read);
        }

        _shared[key] = new Memo(computed, made);
        return (T)computed!;
    }

    /// <summary>Returns a remembered aggregate, or computes and remembers it.</summary>
    /// <param name="key">the absolute pattern and the operator, which identify it</param>
    /// <param name="remember">whether the aggregate is taken from the document root</param>
    /// <param name="compute">how to compute it</param>
    /// <returns>the aggregate</returns>
    public RuleValue Aggregate(string key, bool remember, Func<RuleValue> compute)
    {
        ArgumentNullException.ThrowIfNull(key);
        ArgumentNullException.ThrowIfNull(compute);
        if (!remember)
        {
            return compute();
        }

        if (_aggregates.TryGetValue(key, out RuleValue known))
        {
            return known;
        }

        RuleValue computed = compute();
        _aggregates[key] = computed;
        return computed;
    }

    /// <summary>
    /// Returns the entries of one subtree of the document, in canonical order. The cost is the
    /// size of the subtree and not the size of the document: the paths of a subtree are
    /// contiguous in the canonical path order, so they are one range of the sorted values.
    /// </summary>
    /// <param name="prefix">the group instance the subtree hangs under</param>
    /// <returns>the entries at or below the prefix</returns>
    public IEnumerable<KeyValuePair<SemanticPath, SemanticValue>> Within(SemanticPath prefix)
    {
        ArgumentNullException.ThrowIfNull(prefix);
        if (prefix.IsRoot)
        {
            return _sorted;
        }

        int from = LowerBound(prefix);
        List<KeyValuePair<SemanticPath, SemanticValue>> range = new();
        for (int at = from; at < _sorted.Count && _sorted[at].Key.StartsWith(prefix); at++)
        {
            range.Add(_sorted[at]);
        }

        return range;
    }

    private bool HasAnythingUnder(SemanticPath group)
    {
        int at = LowerBound(group);
        return at < _sorted.Count && _sorted[at].Key.StartsWith(group);
    }

    private int LowerBound(SemanticPath prefix)
    {
        int low = 0;
        int high = _sorted.Count;
        while (low < high)
        {
            int middle = (low + high) / 2;
            if (SemanticPath.CanonicalOrder.Compare(_sorted[middle].Key, prefix) < 0)
            {
                low = middle + 1;
            }
            else
            {
                high = middle;
            }
        }

        return low;
    }

    /// <summary>
    /// Files every value of the document under the key of its path, and every business group
    /// instance under the key of that group, in one pass.
    /// </summary>
    private void Index()
    {
        foreach (KeyValuePair<SemanticPath, SemanticValue> entry in _document.Entries)
        {
            _sorted.Add(entry);
            string key = PathPattern.Key(entry.Key);
            if (!_byValueKey.TryGetValue(key, out List<KeyValuePair<SemanticPath, SemanticValue>>? values))
            {
                values = new List<KeyValuePair<SemanticPath, SemanticValue>>();
                _byValueKey[key] = values;
            }

            values.Add(entry);

            IReadOnlyList<PathSegment> segments = entry.Key.Segments;
            for (int at = 0; at < segments.Count; at++)
            {
                if (segments[at].IsIndex || segments[at].Kind != TermKind.Bg)
                {
                    continue;
                }

                int end = at + 1 < segments.Count && segments[at + 1].IsIndex ? at + 2 : at + 1;
                SemanticPath group = entry.Key.Prefix(end);
                string groupKey = PathPattern.Key(group);
                if (!_byGroupKey.TryGetValue(groupKey, out List<SemanticPath>? instances))
                {
                    instances = new List<SemanticPath>();
                    _byGroupKey[groupKey] = instances;
                }

                if (instances.Count == 0 || !instances[instances.Count - 1].Equals(group))
                {
                    instances.Add(group);
                }
            }
        }
    }

    /// <summary>An answer about the whole document, together with what computing it read.</summary>
    private sealed record Memo(object? Answer, IReadOnlyList<string> Reads);
}
