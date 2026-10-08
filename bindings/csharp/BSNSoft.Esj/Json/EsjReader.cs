using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using BSNSoft.Esj.Validation;

namespace BSNSoft.Esj.Json;

/// <summary>What a reader that was asked to report rather than to reject answers.</summary>
public sealed class ReadResult
{
    private readonly List<Finding> _findings;

    internal ReadResult(SemanticDocument? document, List<Finding> findings)
    {
        Document = document;
        _findings = findings;
    }

    /// <summary>Returns the document, or <c>null</c> where none could be built.</summary>
    public SemanticDocument? Document { get; }

    /// <summary>Returns every finding the reader met, in the order it met them.</summary>
    public IReadOnlyList<Finding> Findings => _findings;

    /// <summary>Tells whether the byte sequence satisfies layer L1.</summary>
    public bool IsWellFormed => Document is not null && !_findings.Any(finding => finding.IsError);

    /// <summary>
    /// Returns the reading as a validation result: the findings of layer L1, with the model
    /// layers named as not evaluated and the reason the specification, section 9.5 gives
    /// them.
    /// </summary>
    /// <returns>the result of layer L1</returns>
    public ValidationResult Validation()
    {
        NotEvaluatedReason reason;
        if (_findings.Any(finding => ReferenceEquals(finding.Code, FindingCode.Limit)))
        {
            reason = NotEvaluatedReason.Limit;
        }
        else if (!IsWellFormed)
        {
            reason = NotEvaluatedReason.PrecedingLayerFailed;
        }
        else
        {
            reason = NotEvaluatedReason.NotRequested;
        }

        return ValidationResult.Of(_findings, new Dictionary<ValidationLayer, NotEvaluatedReason>
        {
            [ValidationLayer.L2] = reason,
            [ValidationLayer.L3] = reason,
        });
    }

    /// <summary>Returns the document, or raises where none could be built.</summary>
    /// <returns>the document</returns>
    /// <exception cref="InvalidOperationException">if the reader built no document</exception>
    public SemanticDocument OrElseThrow() =>
        Document ?? throw new InvalidOperationException("the reader built no document: "
            + string.Join("; ", _findings.Select(finding => finding.ToString())));
}


/// <summary>
/// Reads an ESJ document from bytes and enforces validation layer L1 while it does
/// (specification, sections 3.2 and 9.1).
/// </summary>
/// <remarks>
/// <para>The reader is strict on purpose. It rejects a byte order mark, anything that is not
/// UTF-8, a duplicate member name at any depth, a member the specification does not define,
/// a <c>semanticModel</c> that is not an edition string, a JSON number, boolean, <c>null</c>
/// or array anywhere inside <c>values</c>, a value whose shape is neither a JSON string nor
/// a value object with a supplementary component, a lone surrogate and every limit of
/// section 12.2. It never trims, collapses, reorders or normalizes a value; the one
/// transformation it applies is the line ending normalization of section 6.8, and only to the
/// strings of <c>values</c>.</para>
/// <para>It needs no registry and makes no check that would need one. Whether the content of
/// a value is a canonical decimal, a date of the calendar or canonical base64 is decided by
/// the semantic data type the registry records for the term, so those checks belong to layer
/// L2. The content passes through this reader exactly as the document spells it,
/// <c>100.00</c> included: nothing here repairs a spelling.</para>
/// <para>A finding names its place the way section 9.5 writes it: the path of the member of
/// <c>values</c> it is about, or the empty path, and a subject that is a member access from the
/// root of the document — <c>source.syntax</c>, <c>values["/BG-4/BT-29/0"].scheme</c>,
/// <c>extensions["de.example"]["a"][1]</c>, <c>["profile"]</c> — with the names the
/// specification defines written after a dot and every name the document chose written in
/// brackets, escaped and whole. <c>ESJ-L1-JSON</c> and <c>ESJ-L1-ENCODING</c> are findings about
/// the document and carry neither; their message names the byte, counted from zero, at which the
/// token that failed begins.</para>
/// <para><see cref="Read"/> rejects and <see cref="ReadWithFindings"/> reports; both are
/// conformant and both carry the same finding code, path and subject (specification,
/// section 9.5).</para>
/// </remarks>
public sealed class EsjReader
{
    private static readonly string[] EnvelopeMembers = { "format", "version", "semanticModel", "values", "extensions", "source" };
    private static readonly string[] RequiredMembers = { "format", "version", "semanticModel", "values" };
    private static readonly string[] ValueMembers = { "value", "scheme", "schemeVersion", "mimeCode", "filename" };
    private static readonly string[] ComponentMembers = { "scheme", "schemeVersion", "mimeCode", "filename" };

    /// <summary>The longest location a message carries.</summary>
    private const int LocationExcerpt = 512;

    private readonly Limits _limits;

    private EsjReader(Limits limits)
    {
        _limits = limits;
    }

    /// <summary>Returns a reader with the limits of the specification, section 12.2.</summary>
    /// <returns>a reader with the reference configuration</returns>
    public static EsjReader Strict() => new(Limits.Defaults);

    /// <summary>Returns a reader with the given limits.</summary>
    /// <param name="limits">the resource bounds to enforce</param>
    /// <returns>a reader</returns>
    public static EsjReader WithLimits(Limits limits)
    {
        ArgumentNullException.ThrowIfNull(limits);
        return new EsjReader(limits);
    }

    /// <summary>Returns the limits this reader enforces.</summary>
    public Limits Limits => _limits;

    /// <summary>Reads a document and rejects a byte sequence that fails layer L1.</summary>
    /// <param name="bytes">the document</param>
    /// <returns>the document</returns>
    /// <exception cref="EsjFormatException">if the byte sequence fails layer L1</exception>
    /// <exception cref="EsjLimitException">if a limit of section 12.2 is exceeded</exception>
    public SemanticDocument Read(byte[] bytes)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        return new Parse(bytes, _limits, null).Run()!;
    }

    /// <summary>Reads a document and reports what it met as findings instead of rejecting.</summary>
    /// <param name="bytes">the document</param>
    /// <returns>the document, where one could be built, and the findings</returns>
    public ReadResult ReadWithFindings(byte[] bytes)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        List<Finding> findings = new();
        SemanticDocument? document;
        try
        {
            document = new Parse(bytes, _limits, findings).Run();
        }
        catch (Stop)
        {
            document = null;
        }
        catch (EsjLimitException)
        {
            document = null;
        }

        return new ReadResult(document, findings);
    }

    /// <summary>Signals that parsing cannot continue. Never leaves the reader.</summary>
    private sealed class Stop : Exception
    {
    }

    /// <summary>One member of a value object, as the parser handed it over.</summary>
    private readonly struct Member
    {
        internal Member(string name, JsonKind kind, string? text, string jsonType)
        {
            Name = name;
            Kind = kind;
            Text = text;
            JsonType = jsonType;
        }

        internal string Name { get; }

        internal JsonKind Kind { get; }

        internal string? Text { get; }

        internal string JsonType { get; }
    }

    /// <summary>What kind of JSON value the parser stands on.</summary>
    private enum JsonKind
    {
        String,
        Number,
        True,
        False,
        Null,
        Array,
        Object,
    }

    /// <summary>One run over one byte sequence.</summary>
    private sealed class Parse
    {
        private readonly byte[] _bytes;
        private readonly Limits _limits;
        private readonly List<Finding>? _collector;
        private readonly SortedDictionary<SemanticPath, SemanticValue> _values = new(SemanticPath.CanonicalOrder);
        private readonly List<KeyValuePair<string, ExtensionValue>> _extensions = new();

        private string _text = string.Empty;
        private int _at;
        private string? _semanticModel;
        private DocumentSource? _source;
        private int _valueCount;
        private long _binaryBytes;
        private int _extensionNodes;
        private SemanticPath? _currentPath;
        private bool _colonPending;
        private int _nameStart;

        internal Parse(byte[] bytes, Limits limits, List<Finding>? collector)
        {
            _bytes = bytes;
            _limits = limits;
            _collector = collector;
        }

        internal SemanticDocument? Run()
        {
            CheckEncoding();
            ReadEnvelope();
            return new SemanticDocument(_semanticModel!, _values, _extensions, _source);
        }

        // ------------------------------------------------------------ the bytes

        /// <summary>
        /// Decides the encoding and the size over the whole byte sequence before a token is read,
        /// so that either finding stands alone. The bytes are decoded as UTF-8 and as nothing
        /// else: no encoding is guessed, so a byte sequence that is UTF-8 but no JSON text in
        /// that decoding — UTF-16 or UTF-32 without a byte order mark among them — is
        /// <c>ESJ-L1-JSON</c> when the parse reaches it (specification, section 4.2).
        /// </summary>
        private void CheckEncoding()
        {
            if (_bytes.LongLength > _limits.MaxDocumentBytes)
            {
                throw LimitAt("the document exceeds the bound of " + _limits.MaxDocumentBytes + " bytes", string.Empty);
            }

            if (_bytes.Length >= 3 && _bytes[0] == 0xEF && _bytes[1] == 0xBB && _bytes[2] == 0xBF)
            {
                throw Fatal(FindingCode.EncodingCode, string.Empty,
                    "the document starts with a byte order mark, which is not whitespace and not"
                    + " part of a JSON text");
            }

            try
            {
                _text = new UTF8Encoding(false, true).GetString(_bytes);
            }
            catch (DecoderFallbackException)
            {
                throw Fatal(FindingCode.EncodingCode, string.Empty, "the document is not encoded in UTF-8");
            }
        }

        // ------------------------------------------------------------ the envelope

        private void ReadEnvelope()
        {
            SkipWhitespace();
            if (_at >= _text.Length || _text[_at] != '{')
            {
                throw Malformed("the top level of a document is a JSON object", _at);
            }

            _at++;
            HashSet<string> seen = new(StringComparer.Ordinal);
            foreach (string name in Members(EnvelopeAccess))
            {
                string access = EnvelopeAccess(name);
                NameTheReaderCannotTake(name, access, seen);

                switch (name)
                {
                    case "format":
                        Fixed(name, Esj.Format);
                        break;
                    case "version":
                        Fixed(name, Esj.Version);
                        break;
                    case "semanticModel":
                        _semanticModel = Edition();
                        break;
                    case "values":
                        ReadValues();
                        break;
                    case "extensions":
                        ReadExtensions();
                        break;
                    case "source":
                        ReadSource();
                        break;
                    default:
                        throw Fatal(FindingCode.EnvelopeMember, access,
                            "the envelope has no member named " + Excerpt(name));
                }
            }

            ReportMissingMembers(seen);

            SkipWhitespace();
            if (_at != _text.Length)
            {
                throw Malformed("the document carries content after the object that closes it", _at);
            }
        }

        /// <summary>
        /// Reports every required envelope member the document lacks, one finding each with the
        /// member's name as its subject, in the order <c>format</c>, <c>version</c>,
        /// <c>semanticModel</c>, <c>values</c>, and ends the read there. A reader asked to reject
        /// raises the first of them.
        /// </summary>
        private void ReportMissingMembers(HashSet<string> seen)
        {
            List<string> absent = RequiredMembers.Where(required => !seen.Contains(required)).ToList();
            if (absent.Count == 0)
            {
                return;
            }

            if (_collector is null)
            {
                throw new EsjFormatException(FindingCode.EnvelopeMember.Code,
                    "the envelope member " + absent[0] + " is required", absent[0], SemanticPath.Root());
            }

            foreach (string member in absent)
            {
                _collector.Add(Finding(FindingCode.EnvelopeMember, member, "the envelope member " + member + " is required"));
            }

            throw new Stop();
        }

        private string Edition()
        {
            string value = EnvelopeString("semanticModel", "semanticModel");
            if (!Esj.IsEdition(value))
            {
                throw Fatal(FindingCode.EnvelopeValue, "semanticModel",
                    "the envelope member semanticModel carries " + Excerpt(value)
                    + ", which is not an edition of the semantic model; one is written "
                    + Esj.DefaultSemanticModel);
            }

            return value;
        }

        private void Fixed(string member, string expected)
        {
            string value = EnvelopeString(member, member);
            if (!string.Equals(expected, value, StringComparison.Ordinal))
            {
                throw Fatal(FindingCode.EnvelopeValue, member,
                    "the envelope member " + member + " carries " + Excerpt(value)
                    + "; its one value is " + expected);
            }
        }

        /// <summary>
        /// Reads a string of the envelope or of <c>source</c>: one JSON token, screened for a lone
        /// surrogate before any check reads it, since a string with no UTF-8 encoding spells no
        /// value, and then held to the string bound of section 12.2 as it stands — these strings are
        /// never normalized.
        /// </summary>
        private string EnvelopeString(string name, string access)
        {
            JsonKind kind = PeekKind();
            if (kind != JsonKind.String)
            {
                throw RefuseType(kind, access, name + " is " + Describe(kind) + ", not a string");
            }

            string value = BoundString(_limits.MaxStringBytes, access, false);
            RequireUnicode(value, access);
            return value;
        }

        /// <summary>
        /// Reads a string under a bound, in UTF-8 bytes, as it stands or
        /// <paramref name="normalized"/>, and stops at <c>ESJ-L1-LIMIT</c> naming
        /// <paramref name="access"/> where the content passes it and carries no lone surrogate.
        /// </summary>
        private string BoundString(long bound, string access, bool normalized)
        {
            int start = _at;
            return ReadString(bound, normalized)
                ?? throw LimitAt("a string is longer than " + bound + " bytes" + TokenAt(start), access);
        }

        /// <summary>
        /// Refuses a member of the envelope or of <c>source</c> whose JSON type is wrong. A scalar
        /// is read to its end first, because a token that is no complete JSON value is
        /// <c>ESJ-L1-JSON</c> and not a value of the wrong type (specification, section 9.6); an
        /// object or an array is refused at the bracket that opens it, which is a token of its own.
        /// </summary>
        private Exception RefuseType(JsonKind kind, string access, string message)
        {
            if (kind is not (JsonKind.Object or JsonKind.Array))
            {
                ConsumeScalar(kind, access);
            }

            return Fatal(FindingCode.EnvelopeValue, access, message);
        }

        private void ReadSource()
        {
            JsonKind kind = PeekKind();
            if (kind != JsonKind.Object)
            {
                throw RefuseType(kind, "source", "source is " + Describe(kind) + ", not a JSON object");
            }

            _at++;
            HashSet<string> seen = new(StringComparer.Ordinal);
            string? syntax = null;
            string? sha256 = null;
            foreach (string name in Members(SourceAccess))
            {
                string access = SourceAccess(name);
                NameTheReaderCannotTake(name, access, seen);

                if (name != "syntax" && name != "sha256")
                {
                    throw Fatal(FindingCode.EnvelopeMember, access, "source has no member named " + Excerpt(name));
                }

                string value = EnvelopeString(name, access);
                if (value.Length == 0)
                {
                    throw Fatal(FindingCode.EnvelopeValue, access, name + " is the empty string");
                }

                if (name == "syntax")
                {
                    syntax = value;
                }
                else
                {
                    if (!Texts.IsLowercaseSha256(value))
                    {
                        throw Fatal(FindingCode.EnvelopeValue, access, "sha256 " + Texts.Sha256Violation(value));
                    }

                    sha256 = value;
                }
            }

            if (syntax is null && sha256 is null)
            {
                throw Fatal(FindingCode.EnvelopeValue, "source",
                    "source carries neither syntax nor sha256; a source object that would be empty"
                    + " is absent instead");
            }

            _source = new DocumentSource(syntax, sha256);
        }

        // ------------------------------------------------------------ values

        private void ReadValues()
        {
            JsonKind kind = PeekKind();
            if (kind != JsonKind.Object)
            {
                throw RefuseType(kind, "values", "values is " + Describe(kind) + ", not a JSON object");
            }

            _at++;
            HashSet<string> seen = new(StringComparer.Ordinal);
            foreach (string name in Members(ValuesAccess))
            {
                string access = ValuesAccess(name);
                NameTheReaderCannotTake(name, access, seen);

                if (++_valueCount > _limits.MaxValues)
                {
                    throw LimitAt("values carries more than " + _limits.MaxValues + " members" + TokenAt(_nameStart), access);
                }

                if (Texts.Utf8Length(name) > _limits.MaxPathBytes)
                {
                    throw LimitAt("a semantic path is longer than " + _limits.MaxPathBytes + " bytes" + TokenAt(_nameStart), access);
                }

                SemanticPath? path = ReadPath(name, access);
                _currentPath = path;
                SemanticValue? value = ReadValue(access);
                if (path is not null && value is not null)
                {
                    _values[path] = value;
                }

                _currentPath = null;
            }
        }

        private SemanticPath? ReadPath(string name, string access)
        {
            if (!SemanticPath.TryParse(name, out SemanticPath path))
            {
                Report(FindingCode.PathSyntax, access, Excerpt(name) + " is not a semantic path");
                return null;
            }

            if (path.Segments.Count > _limits.MaxPathSegments)
            {
                throw LimitAt("a semantic path has more than " + _limits.MaxPathSegments + " segments" + TokenAt(_nameStart), access);
            }

            return path;
        }

        private SemanticValue? ReadValue(string access)
        {
            JsonKind kind = PeekKind();
            if (kind == JsonKind.String)
            {
                // The bound is measured on the normalized content (section 6.8), and after the
                // string has been found free of a lone surrogate (section 9.6).
                string raw = BoundString(_limits.MaxStringBytes, access, true);
                string? content = String(raw, access, "this value", false);
                return content is null ? null : new SemanticValue(content);
            }

            if (kind == JsonKind.Object)
            {
                return ReadValueObject(access);
            }

            string type = Describe(kind);
            WalkPast(kind, access, 1);
            Report(FindingCode.JsonType, access,
                "this member of values is " + type + ", not a string and not a value object");
            return null;
        }

        /// <summary>
        /// Reads a value written as a JSON object. The members are collected before any of
        /// them is judged, because the specification, section 9.6 decides the code from the
        /// object and not from the member a reader happens to meet first. A member name that
        /// occurs twice or carries a lone surrogate ends the reading there: section 9.6 holds
        /// both against the JSON text, and there is then no object to judge. A lone surrogate
        /// in a member value is reported beside the code the object draws, so it is reported
        /// before that code is decided.
        /// </summary>
        private SemanticValue? ReadValueObject(string where)
        {
            _at++;
            List<Member> members = new();
            HashSet<string> seen = new(StringComparer.Ordinal);
            bool component = false;
            foreach (string name in Members(member => ValueMemberAccess(where, member)))
            {
                string access = ValueMemberAccess(where, name);
                NameTheReaderCannotTake(name, where, seen);

                if (members.Count >= _limits.MaxValueMembers)
                {
                    throw LimitAt("a value object carries more than " + _limits.MaxValueMembers + " members" + TokenAt(_nameStart), where);
                }

                component |= Array.IndexOf(ComponentMembers, name) >= 0;
                JsonKind kind = PeekKind();
                string type = Describe(kind);
                string? text = null;
                if (kind == JsonKind.String)
                {
                    // A supplementary component is held to the string bound as it is read; the
                    // value member to the larger of the two bounds, because whether it is the
                    // content of a binary object is decided by members that may follow, and to its
                    // own bound once the object is judged (section 12.2). A string with a lone
                    // surrogate is held to neither (section 9.6).
                    long bound = name == "value"
                        ? Math.Max(_limits.MaxStringBytes, _limits.MaxBinaryValueBytes)
                        : _limits.MaxStringBytes;
                    text = BoundString(bound, access, true);
                }
                else
                {
                    WalkPast(kind, access, 2);
                }

                members.Add(new Member(name, kind, text, type));
            }

            bool surrogate = ReportSurrogates(members, where);
            if (!component)
            {
                Report(FindingCode.ValueShape, where,
                    "this value is an object and carries no supplementary component; a value that"
                    + " is nothing but content is written as a JSON string");
                return null;
            }

            foreach (Member member in members)
            {
                if (member.Kind == JsonKind.Object)
                {
                    Report(FindingCode.ValueShape, ValueMemberAccess(where, member.Name),
                        Excerpt(member.Name) + " is an object, not a string");
                    return null;
                }
            }

            foreach (Member member in members)
            {
                if (member.Kind != JsonKind.String)
                {
                    Report(FindingCode.JsonType, ValueMemberAccess(where, member.Name),
                        Excerpt(member.Name) + " is " + member.JsonType + ", not a string");
                    return null;
                }
            }

            Dictionary<string, string> byName = new(StringComparer.Ordinal);
            foreach (Member member in members)
            {
                if (Array.IndexOf(ValueMembers, member.Name) < 0)
                {
                    Report(FindingCode.ValueMember, ValueMemberAccess(where, member.Name),
                        "a value object has no member named " + Excerpt(member.Name));
                    return null;
                }

                byName[member.Name] = member.Text!;
            }

            if (!byName.ContainsKey("value"))
            {
                Report(FindingCode.ValueMember, where, "this value object carries no value member");
                return null;
            }

            if (byName.ContainsKey("schemeVersion") && !byName.ContainsKey("scheme"))
            {
                Report(FindingCode.ValueMember, where + ".schemeVersion",
                    "schemeVersion is present only beside scheme");
                return null;
            }

            return Build(byName, where, surrogate);
        }

        /// <summary>
        /// Reports every member string of a value object that carries a lone surrogate. The
        /// specification, section 9.6 gives that code precedence over every check that reads
        /// the content of the same string, and leaves a check that reads another string
        /// untouched by it, so a value object may draw this code beside the code of its shape.
        /// </summary>
        private bool ReportSurrogates(List<Member> members, string where)
        {
            bool found = false;
            foreach (Member member in members)
            {
                if (member.Kind == JsonKind.String && Texts.HasLoneSurrogate(member.Text!))
                {
                    Report(FindingCode.Surrogate, ValueMemberAccess(where, member.Name),
                        "a string carries an unpaired surrogate");
                    found = true;
                }
            }

            return found;
        }

        private SemanticValue? Build(Dictionary<string, string> members, string where, bool surrogate)
        {
            bool binary = members.ContainsKey("mimeCode") || members.ContainsKey("filename");
            Dictionary<string, string> checkedMembers = new(StringComparer.Ordinal);
            foreach (KeyValuePair<string, string> member in members)
            {
                if (Texts.HasLoneSurrogate(member.Value))
                {
                    // Reported already, and the first check of the string that fails (section 9.6).
                    continue;
                }

                string? content = Content(member.Value, ValueMemberAccess(where, member.Key), member.Key,
                    binary && member.Key == "value");
                if (content is null)
                {
                    return null;
                }

                checkedMembers[member.Key] = content;
            }

            if (surrogate)
            {
                return null;
            }

            if (binary)
            {
                CountBinary(checkedMembers["value"], where);
            }

            return new SemanticValue(
                checkedMembers["value"],
                Optional(checkedMembers, "scheme"),
                Optional(checkedMembers, "schemeVersion"),
                Optional(checkedMembers, "mimeCode"),
                Optional(checkedMembers, "filename"));
        }

        private static string? Optional(Dictionary<string, string> members, string name) =>
            members.TryGetValue(name, out string? value) ? value : null;

        private string? String(string raw, string where, string what, bool binary)
        {
            if (Texts.HasLoneSurrogate(raw))
            {
                Report(FindingCode.Surrogate, where, "a string carries an unpaired surrogate");
                return null;
            }

            return Content(raw, where, what, binary);
        }

        private string? Content(string raw, string where, string what, bool binary)
        {
            string value = Texts.NormalizeLineEndings(raw);
            if (value.Length == 0)
            {
                Report(FindingCode.EmptyString, where, what + " is the empty string");
                return null;
            }

            long bound = binary ? _limits.MaxBinaryValueBytes : _limits.MaxStringBytes;
            if (Texts.Utf8Length(value) > bound)
            {
                throw LimitAt(
                    (binary ? "a binary value is longer than " : "a string value is longer than ")
                    + bound + " bytes", where);
            }

            return value;
        }

        private void CountBinary(string content, string where)
        {
            long decoded = Texts.DecodedBase64Length(content);
            if (_binaryBytes + decoded > _limits.MaxTotalBinaryBytes)
            {
                throw LimitAt("the decoded binary content of the document exceeds the bound of "
                    + _limits.MaxTotalBinaryBytes + " bytes", where);
            }

            _binaryBytes += decoded;
        }

        /// <summary>
        /// Walks past a value the specification allows no such JSON type for inside
        /// <c>values</c>, checking that it is well formed and that it keeps inside the bounds of
        /// section 12.2 that bind there — the depth, the length of a member name and of a number
        /// token — and nothing else: no surrogate, no repeated name, no string length and no shape
        /// below it is reported, because the member it stands in has drawn its code already and
        /// the subtree is not judged (specification, section 9.6). A bound met there names the
        /// member walked past. The depth is counted the way it is counted inside
        /// <c>extensions</c> — the value of a member of <c>values</c> is level 1, the value of a
        /// member of a value object level 2 — and the walk keeps the containers it has opened on a
        /// stack of its own, so a bound a caller raised is not answered with a stack overflow.
        /// </summary>
        private void WalkPast(JsonKind kind, string access, int level)
        {
            if (kind is not (JsonKind.Object or JsonKind.Array))
            {
                ConsumeScalar(kind, access);
                return;
            }

            Stack<Walked> open = new();
            int depth = level;
            JsonKind current = kind;
            while (true)
            {
                if (current is JsonKind.Object or JsonKind.Array)
                {
                    if (depth > _limits.MaxExtensionDepth)
                    {
                        throw LimitAt("a value nests deeper than " + _limits.MaxExtensionDepth + " levels"
                            + TokenAt(_at), access);
                    }

                    _at++;
                    open.Push(new Walked(current == JsonKind.Object, depth));
                }
                else
                {
                    ConsumeScalar(current, access);
                }

                while (true)
                {
                    if (open.Count == 0)
                    {
                        return;
                    }

                    Walked container = open.Peek();
                    SkipWhitespace();
                    if (container.IsObject)
                    {
                        if (!NextMember(container.Any))
                        {
                            open.Pop();
                            continue;
                        }

                        int start = _at;
                        string name = ReadName();
                        CheckNameBound(name, start, () => access);
                        Colon();
                    }
                    else if (!NextElement(container.Any))
                    {
                        open.Pop();
                        continue;
                    }

                    container.Any = true;
                    depth = container.Depth + 1;
                    current = PeekKind();
                    break;
                }
            }
        }

        /// <summary>One container of a walked-past value that the reader has opened.</summary>
        private sealed class Walked
        {
            internal Walked(bool isObject, int depth)
            {
                IsObject = isObject;
                Depth = depth;
            }

            internal bool IsObject { get; }

            internal int Depth { get; }

            internal bool Any { get; set; }
        }

        // ------------------------------------------------------------ extensions

        private void ReadExtensions()
        {
            JsonKind kind = PeekKind();
            if (kind != JsonKind.Object)
            {
                throw RefuseType(kind, "extensions", "extensions is " + Describe(kind) + ", not a JSON object");
            }

            _at++;
            HashSet<string> seen = new(StringComparer.Ordinal);
            foreach (string owner in Members(OwnerAccess))
            {
                string access = OwnerAccess(owner);
                NameTheReaderCannotTake(owner, access, seen);

                if (!Texts.IsOwnerToken(owner))
                {
                    throw Fatal(FindingCode.OwnerToken, access,
                        "the owner token " + Excerpt(owner) + " " + Texts.OwnerTokenViolation(owner));
                }

                _extensions.Add(new KeyValuePair<string, ExtensionValue>(
                    owner, ReadExtensionValue(new Where(access))));
            }

            if (seen.Count == 0)
            {
                throw Fatal(FindingCode.EnvelopeValue, "extensions",
                    "extensions carries no owner; an extensions object that would be empty is absent instead");
            }
        }

        /// <summary>
        /// Reads one extension subtree. The walk is iterative, with the containers it has
        /// opened held on a stack rather than in call frames: the nesting bound of the
        /// specification, section 12.2 is configurable, so a recursive reader would answer a
        /// bound a caller raised with a stack overflow instead of with a document or a finding.
        /// The place of a node is kept as a link to its parent and spelled out only where a
        /// finding names it, so the walk costs as much as the depth and not its square.
        /// </summary>
        private ExtensionValue ReadExtensionValue(Where root)
        {
            Stack<Container> open = new();
            Where where = root;
            int depth = 1;
            ExtensionValue? finished = null;
            while (true)
            {
                if (finished is null)
                {
                    CountExtensionNode(where);
                    JsonKind kind = PeekKind();
                    if (kind is JsonKind.Object or JsonKind.Array)
                    {
                        if (depth > _limits.MaxExtensionDepth)
                        {
                            throw LimitAt("extensions is nested deeper than "
                                + _limits.MaxExtensionDepth + " levels" + TokenAt(_at), where.Text());
                        }

                        _at++;
                        open.Push(new Container(kind == JsonKind.Object, where, depth));
                    }
                    else
                    {
                        finished = Scalar(kind, where);
                    }
                }

                if (finished is not null)
                {
                    if (open.Count == 0)
                    {
                        return finished;
                    }

                    open.Peek().Add(finished);
                    finished = null;
                }

                Container container = open.Peek();
                SkipWhitespace();
                if (container.IsObject)
                {
                    if (!NextMember(container.Any))
                    {
                        finished = container.Build();
                        open.Pop();
                        continue;
                    }

                    int start = _at;
                    string name = ReadName();
                    Where memberWhere = new(container.Where, name);
                    CheckNameBound(name, start, memberWhere.Text);
                    if (Texts.HasLoneSurrogate(name))
                    {
                        throw Fatal(FindingCode.Surrogate, memberWhere.Text(), "a member name carries an unpaired surrogate");
                    }

                    if (container.Has(name))
                    {
                        throw Duplicate(name, memberWhere.Text());
                    }

                    Colon();
                    container.Expect(name);
                    where = memberWhere;
                }
                else
                {
                    if (!NextElement(container.Any))
                    {
                        finished = container.Build();
                        open.Pop();
                        continue;
                    }

                    where = new Where(container.Where, container.Size);
                }

                container.Any = true;
                depth = container.Depth + 1;
            }
        }

        private void CountExtensionNode(Where where)
        {
            if (++_extensionNodes > _limits.MaxExtensionNodes)
            {
                throw LimitAt("extensions carries more than " + _limits.MaxExtensionNodes + " nodes", where.Text());
            }
        }

        private ExtensionValue Scalar(JsonKind kind, Where where)
        {
            switch (kind)
            {
                case JsonKind.String:
                    int start = _at;
                    string value = ReadString(_limits.MaxStringBytes)
                        ?? throw LimitAt("a string is longer than " + _limits.MaxStringBytes + " bytes"
                            + TokenAt(start), where.Text());
                    if (Texts.HasLoneSurrogate(value))
                    {
                        throw Fatal(FindingCode.Surrogate, where.Text(), "a string carries an unpaired surrogate");
                    }

                    return ExtensionValue.OfString(value);
                case JsonKind.Number:
                    return ExtensionValue.OfNumber(Number(where));
                case JsonKind.True:
                    Literal("true");
                    return ExtensionValue.OfBoolean(true);
                case JsonKind.False:
                    Literal("false");
                    return ExtensionValue.OfBoolean(false);
                default:
                    Literal("null");
                    return ExtensionValue.OfNull();
            }
        }

        /// <summary>
        /// Reads one number of <c>extensions</c> and returns its canonical decimal form. The
        /// spelling is measured against the string bound exactly, because two readers running
        /// the defaults have to refuse the same tokens (specification, section 12.2).
        /// </summary>
        private string Number(Where where)
        {
            string lexical = ReadNumberToken(where.Text, true);
            string? canonical = Decimals.Canonicalize(lexical);
            if (canonical is null)
            {
                throw Fatal(FindingCode.ExtensionNumber, where.Text(),
                    "a number inside extensions has a canonical decimal form of more than "
                    + Decimals.MaxLength + " characters");
            }

            return canonical;
        }

        /// <summary>
        /// A place in the document below a member access the reader knows: the root, or a member
        /// name or an element index below a parent place. It is spelled out only where a finding
        /// names it, so a walk pays for the place of a node only when it reports one.
        /// </summary>
        private sealed class Where
        {
            private readonly Where? _parent;
            private readonly string? _name;
            private readonly int _index;

            internal Where(string root)
            {
                _name = root;
            }

            internal Where(Where parent, string name)
            {
                _parent = parent;
                _name = name;
            }

            internal Where(Where parent, int index)
            {
                _parent = parent;
                _index = index;
            }

            /// <summary>Returns the member access, from the root of the document down to this place.</summary>
            internal string Text()
            {
                List<Where> steps = new();
                for (Where? at = this; at is not null; at = at._parent)
                {
                    steps.Add(at);
                }

                StringBuilder text = new();
                for (int i = steps.Count - 1; i >= 0; i--)
                {
                    Where step = steps[i];
                    if (step._parent is null)
                    {
                        text.Append(step._name);
                    }
                    else if (step._name is not null)
                    {
                        text.Append(Bracketed(step._name));
                    }
                    else
                    {
                        text.Append('[').Append(step._index.ToString(CultureInfo.InvariantCulture)).Append(']');
                    }
                }

                return text.ToString();
            }
        }

        /// <summary>One container of an extension subtree that the reader has opened.</summary>
        private sealed class Container
        {
            private readonly List<KeyValuePair<string, ExtensionValue>>? _members;
            private readonly List<ExtensionValue>? _elements;
            private readonly HashSet<string>? _names;
            private string? _pending;

            internal Container(bool isObject, Where where, int depth)
            {
                IsObject = isObject;
                Where = where;
                Depth = depth;
                _members = isObject ? new List<KeyValuePair<string, ExtensionValue>>() : null;
                _names = isObject ? new HashSet<string>(StringComparer.Ordinal) : null;
                _elements = isObject ? null : new List<ExtensionValue>();
            }

            internal bool IsObject { get; }

            internal Where Where { get; }

            internal int Depth { get; }

            internal bool Any { get; set; }

            internal int Size => _elements!.Count;

            internal bool Has(string name) => _names!.Contains(name);

            internal void Expect(string name)
            {
                _pending = name;
                _names!.Add(name);
            }

            internal void Add(ExtensionValue value)
            {
                if (IsObject)
                {
                    _members!.Add(new KeyValuePair<string, ExtensionValue>(_pending!, value));
                    _pending = null;
                }
                else
                {
                    _elements!.Add(value);
                }
            }

            internal ExtensionValue Build() =>
                IsObject ? ExtensionValue.OfObject(_members!) : ExtensionValue.OfArray(_elements!);
        }

        // ------------------------------------------------------------ the scanner

        /// <summary>
        /// Walks the members of an object the reader has opened, handing each name over and
        /// leaving the parser on the value that follows it. Each name is held to the string
        /// bound of section 12.2 before anything else is asked of it, and a name past it is
        /// reported under the member access <paramref name="access"/> gives it.
        /// </summary>
        private IEnumerable<string> Members(Func<string, string> access)
        {
            bool any = false;
            while (true)
            {
                SkipWhitespace();
                if (_at >= _text.Length)
                {
                    throw Malformed("an object is not closed", _at);
                }

                if (_text[_at] == '}')
                {
                    _at++;
                    yield break;
                }

                if (any)
                {
                    if (_text[_at] != ',')
                    {
                        throw Malformed("a comma separates two members of an object", _at);
                    }

                    _at++;
                    SkipWhitespace();
                }

                if (_at >= _text.Length || _text[_at] != '"')
                {
                    throw Malformed("a member name is a JSON string", _at);
                }

                int start = _at;
                _nameStart = start;
                string name = ReadName();
                CheckNameBound(name, start, () => access(name));
                // The colon is read where the value is (PeekKind), so that the caller judges the
                // name before the text after it (specification, section 9.6).
                _colonPending = true;
                any = true;
                yield return name;
            }
        }

        /// <summary>
        /// Holds a member name to the string bound of the specification, section 12.2, counted in
        /// the bytes of its UTF-8 encoding. The bounds of the grammars a name is measured against
        /// afterwards — the path, the owner token — apply inside this one.
        /// </summary>
        private void CheckNameBound(string name, int start, Func<string> access)
        {
            if (Texts.Utf8Length(name) > _limits.MaxStringBytes)
            {
                throw LimitAt("a member name is longer than " + _limits.MaxStringBytes + " bytes"
                    + TokenAt(start), access());
            }
        }

        /// <summary>
        /// Stands on the next member name of an open object, or on the brace that closes it.
        /// </summary>
        private bool NextMember(bool any)
        {
            if (_at < _text.Length && _text[_at] == '}')
            {
                _at++;
                return false;
            }

            if (any)
            {
                if (_at >= _text.Length || _text[_at] != ',')
                {
                    throw Malformed("a comma separates two members of an object", _at);
                }

                _at++;
                SkipWhitespace();
            }

            if (_at >= _text.Length || _text[_at] != '"')
            {
                throw Malformed("a member name is a JSON string", _at);
            }

            return true;
        }

        private bool NextElement(bool any)
        {
            if (_at < _text.Length && _text[_at] == ']')
            {
                _at++;
                return false;
            }

            if (any)
            {
                if (_at >= _text.Length || _text[_at] != ',')
                {
                    throw Malformed("a comma separates two elements of an array", _at);
                }

                _at++;
                SkipWhitespace();
            }

            return true;
        }

        private void Colon()
        {
            SkipWhitespace();
            if (_at >= _text.Length || _text[_at] != ':')
            {
                throw Malformed("a colon separates a member name from its value", _at);
            }

            _at++;
            SkipWhitespace();
        }

        private void SkipWhitespace()
        {
            while (_at < _text.Length)
            {
                char character = _text[_at];
                if (character != ' ' && character != '\t' && character != '\n' && character != '\r')
                {
                    return;
                }

                _at++;
            }
        }

        /// <summary>
        /// Returns what kind of JSON value the parser stands on, without consuming it. What the
        /// first character promises is not yet a judgement: a scalar is judged only once it is
        /// read whole, so that a token that is no JSON value is <c>ESJ-L1-JSON</c>.
        /// </summary>
        private JsonKind PeekKind()
        {
            if (_colonPending)
            {
                _colonPending = false;
                Colon();
            }

            SkipWhitespace();
            if (_at >= _text.Length)
            {
                throw Malformed("the document ends where a value belongs", _at);
            }

            char character = _text[_at];
            return character switch
            {
                '"' => JsonKind.String,
                '{' => JsonKind.Object,
                '[' => JsonKind.Array,
                't' => JsonKind.True,
                'f' => JsonKind.False,
                'n' => JsonKind.Null,
                '-' => JsonKind.Number,
                _ => char.IsAsciiDigit(character)
                    ? JsonKind.Number
                    : throw Malformed("a JSON value does not begin with " + Excerpt(character.ToString()), _at),
            };
        }

        private static string Describe(JsonKind kind) => kind switch
        {
            JsonKind.String => "a string",
            JsonKind.Number => "a number",
            JsonKind.True => "the boolean true",
            JsonKind.False => "the boolean false",
            JsonKind.Null => "null",
            JsonKind.Array => "an array",
            _ => "an object",
        };

        /// <summary>
        /// Reads a member name whole. The name is held to its bound once it is read, because a
        /// finding about a name names it whole (specification, section 9.5): the document is
        /// already held, so reading the name costs nothing a bound would have saved.
        /// </summary>
        private string ReadName() => ReadString(long.MaxValue)!;

        /// <summary>
        /// What <see cref="ReadString"/> returns for a string past its bound that carries a lone
        /// surrogate: the surrogate alone, which every caller refuses for the surrogate.
        /// </summary>
        private static readonly string LoneSurrogate = new('\uD800', 1);

        /// <summary>
        /// Reads one JSON string and returns what it decodes to, measuring its content in UTF-8
        /// bytes against <paramref name="bound"/>. A lone surrogate counts the three bytes of its
        /// generalized encoding, and measured <paramref name="normalized"/>, a LF straight after a
        /// CR counts nothing, because the two become one LF (specification, section 6.8).
        /// </summary>
        /// <remarks>
        /// A string past the bound is read to its end without being kept, because the checks of
        /// one string run in a fixed order and a lone surrogate comes before the bound
        /// (specification, section 9.6): one that carries a lone surrogate is returned as
        /// <see cref="LoneSurrogate"/>, which every caller refuses for the surrogate before it asks
        /// anything else, and any other one as <c>null</c>, which the caller refuses with
        /// <c>ESJ-L1-LIMIT</c>.
        /// </remarks>
        private string? ReadString(long bound, bool normalized = false, bool keep = true)
        {
            SkipWhitespace();
            if (_at >= _text.Length || _text[_at] != '"')
            {
                throw Malformed("a string begins with a quotation mark", _at);
            }

            int start = _at;
            _at++;
            StringBuilder? value = keep ? new() : null;
            bool over = false;
            long bytes = 0;
            bool afterHigh = false;
            bool lone = false;
            bool afterCr = false;
            while (true)
            {
                if (_at >= _text.Length)
                {
                    throw Malformed("a string is not closed", start);
                }

                char character = _text[_at++];
                if (character == '"')
                {
                    lone |= afterHigh;
                    if (!over)
                    {
                        return value is null ? string.Empty : value.ToString();
                    }

                    return lone ? LoneSurrogate : null;
                }

                if (character < 0x20)
                {
                    throw Malformed("a control character inside a string is written as an escape", start);
                }

                if (character == '\\')
                {
                    character = Escape(start);
                }

                if (char.IsLowSurrogate(character) && afterHigh)
                {
                    bytes += 1;
                    afterHigh = false;
                }
                else
                {
                    lone |= afterHigh || char.IsLowSurrogate(character);
                    if (!(normalized && afterCr && character == '\n'))
                    {
                        bytes += character < 0x80 ? 1 : character < 0x800 ? 2 : 3;
                    }

                    afterHigh = char.IsHighSurrogate(character);
                }

                afterCr = character == '\r';
                if (!over && bytes > bound)
                {
                    over = true;
                    value = null;
                }

                value?.Append(character);
            }
        }

        private char Escape(int start)
        {
            if (_at >= _text.Length)
            {
                throw Malformed("a string is not closed", start);
            }

            char escape = _text[_at++];
            return escape switch
            {
                '"' => '"',
                '\\' => '\\',
                '/' => '/',
                'b' => '\b',
                'f' => '\f',
                'n' => '\n',
                'r' => '\r',
                't' => '\t',
                'u' => Hex4(start),
                _ => throw Malformed("a string carries an escape this format does not define", start),
            };
        }

        private char Hex4(int start)
        {
            if (_at + 4 > _text.Length)
            {
                throw Malformed("a unicode escape carries four hexadecimal digits", start);
            }

            int value = 0;
            for (int digit = 0; digit < 4; digit++)
            {
                char character = _text[_at++];
                int number = character switch
                {
                    >= '0' and <= '9' => character - '0',
                    >= 'a' and <= 'f' => character - 'a' + 10,
                    >= 'A' and <= 'F' => character - 'A' + 10,
                    _ => throw Malformed("a unicode escape carries four hexadecimal digits", start),
                };
                value = (value * 16) + number;
            }

            return (char)value;
        }

        /// <summary>
        /// Reads a number token as the document spells it. The spelling is held to the string
        /// bound of the specification, section 12.2 wherever the token stands, once the token is
        /// read and before it is built: a spelling of any length may canonicalize to a short
        /// number, so this is the bound that sizes what the reader holds. A JSON number is ASCII,
        /// so its characters are its bytes.
        /// </summary>
        private string ReadNumberToken(Func<string> access, bool keep)
        {
            SkipWhitespace();
            int start = _at;
            if (_at < _text.Length && _text[_at] == '-')
            {
                _at++;
            }

            int integer = _at;
            if (Digits() == 0)
            {
                throw Malformed("a number carries at least one digit", start);
            }

            if (_at - integer > 1 && _text[integer] == '0')
            {
                throw Malformed("a number carries no leading zero", start);
            }

            if (_at < _text.Length && _text[_at] == '.')
            {
                _at++;
                if (Digits() == 0)
                {
                    throw Malformed("a fraction carries at least one digit", start);
                }
            }

            if (_at < _text.Length && (_text[_at] == 'e' || _text[_at] == 'E'))
            {
                _at++;
                if (_at < _text.Length && (_text[_at] == '+' || _text[_at] == '-'))
                {
                    _at++;
                }

                if (Digits() == 0)
                {
                    throw Malformed("an exponent carries at least one digit", start);
                }
            }

            if (_at - start > _limits.MaxStringBytes)
            {
                throw LimitAt("a number is spelled in more than " + _limits.MaxStringBytes + " bytes"
                    + TokenAt(start), access());
            }

            return keep ? _text.Substring(start, _at - start) : string.Empty;
        }

        private int Digits()
        {
            int first = _at;
            while (_at < _text.Length && char.IsAsciiDigit(_text[_at]))
            {
                _at++;
            }

            return _at - first;
        }

        /// <summary>
        /// Reads past a scalar the reader neither keeps nor judges, to its end: a string is checked
        /// to be JSON and not measured, a number is held to its bound, which names
        /// <paramref name="access"/>.
        /// </summary>
        private void ConsumeScalar(JsonKind kind, string access)
        {
            switch (kind)
            {
                case JsonKind.String:
                    ReadString(long.MaxValue, keep: false);
                    break;
                case JsonKind.Number:
                    ReadNumberToken(() => access, false);
                    break;
                case JsonKind.True:
                    Literal("true");
                    break;
                case JsonKind.False:
                    Literal("false");
                    break;
                default:
                    Literal("null");
                    break;
            }
        }

        /// <summary>
        /// Reads <c>true</c>, <c>false</c> or <c>null</c>. An ASCII letter or digit, <c>_</c> or
        /// <c>$</c> straight after the word makes the token another word, which is no JSON value.
        /// </summary>
        private void Literal(string word)
        {
            SkipWhitespace();
            int end = _at + word.Length;
            if (end > _text.Length
                || string.CompareOrdinal(_text, _at, word, 0, word.Length) != 0
                || (end < _text.Length && IsWordCharacter(_text[end])))
            {
                throw Malformed("a JSON literal is true, false or null", _at);
            }

            _at = end;
        }

        private static bool IsWordCharacter(char character) =>
            char.IsAsciiLetterOrDigit(character) || character == '_' || character == '$';

        // ------------------------------------------------------------ findings

        private static string Bracketed(string name) => "[\"" + Esj.ForSubject(name) + "\"]";

        /// <summary>
        /// Returns the member access of a member of the envelope: the name after nothing where
        /// the specification defines it, in brackets where the document chose it.
        /// </summary>
        private static string EnvelopeAccess(string name) =>
            Array.IndexOf(EnvelopeMembers, name) >= 0 ? name : Bracketed(name);

        private static string SourceAccess(string name) =>
            name is "syntax" or "sha256" ? "source." + name : "source" + Bracketed(name);

        private static string ValuesAccess(string name) => "values" + Bracketed(name);

        private static string ValueMemberAccess(string where, string name) =>
            Array.IndexOf(ValueMembers, name) >= 0 ? where + "." + name : where + Bracketed(name);

        private static string OwnerAccess(string owner) => "extensions" + Bracketed(owner);

        private static string Excerpt(string value) => Esj.ForMessage(value, Esj.MessageExcerpt);

        /// <summary>
        /// Holds one member name to the two defects the specification, section 9.6 holds against
        /// the JSON text rather than against the value written under the name: a name carrying a
        /// lone surrogate, which names nothing, and a name that has already occurred in this
        /// object, which leaves no one object to judge. Either ends the read with that one code
        /// and the object is judged no further; the finding names the member the name stands
        /// for, as a member access with the name escaped — inside a value object the object, whose
        /// findings name it (section 9.5). The surrogate is asked first because
        /// the two are ranked by the place the text reaches first and a repeated name is met at
        /// its second occurrence, so the earlier of the two is always the one reported.
        /// </summary>
        /// <param name="name">the member name, as the document spells it</param>
        /// <param name="access">the member access the finding names</param>
        /// <param name="seen">the names this object has already carried</param>
        private void NameTheReaderCannotTake(string name, string access, HashSet<string> seen)
        {
            if (Texts.HasLoneSurrogate(name))
            {
                throw Fatal(FindingCode.Surrogate, access, "a member name carries an unpaired surrogate");
            }

            if (!seen.Add(name))
            {
                throw Duplicate(name, access);
            }
        }

        private void RequireUnicode(string value, string where)
        {
            if (Texts.HasLoneSurrogate(value))
            {
                throw Fatal(FindingCode.Surrogate, where, "a string carries an unpaired surrogate");
            }
        }

        private Exception Duplicate(string name, string where) =>
            Fatal(FindingCode.DuplicateMember, where,
                "the member name " + Excerpt(name) + " occurs twice in one object");

        /// <summary>
        /// Returns the finding of a byte sequence that is not a JSON text. It is a finding about
        /// the document, so it names no path and no subject; its message names the byte, counted
        /// from zero, at which the token the reader could not read begins (specification,
        /// section 9.5).
        /// </summary>
        private Exception Malformed(string what, int at) =>
            Fatal(FindingCode.JsonCode, string.Empty,
                "the document is not a JSON text: " + what + " (at byte " + ByteOffset(at) + ")");

        private string TokenAt(int at) => " (the token begins at byte " + ByteOffset(at) + ")";

        private string ByteOffset(int at) =>
            Texts.Utf8Length(_text, Math.Min(at, _text.Length)).ToString(CultureInfo.InvariantCulture);

        private EsjLimitException LimitAt(string message, string where)
        {
            Finding finding = Finding(FindingCode.Limit, where, message);
            _collector?.Add(finding);
            return new EsjLimitException(message, where, finding.Path);
        }

        /// <summary>Reports a problem that ends the parse, and returns what to throw for it.</summary>
        private Exception Fatal(FindingCode code, string where, string message)
        {
            Finding finding = Finding(code, where, message);
            if (_collector is not null)
            {
                _collector.Add(finding);
                return new Stop();
            }

            return new EsjFormatException(code.Code, message, where, finding.Path);
        }

        /// <summary>Reports a problem that is local to one member of <c>values</c>.</summary>
        private void Report(FindingCode code, string where, string message)
        {
            Finding finding = Finding(code, where, message);
            if (_collector is not null)
            {
                _collector.Add(finding);
                return;
            }

            throw new EsjFormatException(code.Code, message, where, finding.Path);
        }

        /// <summary>
        /// Returns the finding for a problem met at <paramref name="where"/>: the path of the
        /// member of <c>values</c> the reader was reading, or the root of the document where it
        /// was reading none or where the problem is one of the document itself, and the member
        /// access as the subject, whole. The copy of that place in the message is held to
        /// <see cref="LocationExcerpt"/>, because the message is a log line (section 12.6).
        /// </summary>
        private Finding Finding(FindingCode code, string where, string message)
        {
            string text = where.Length == 0
                ? message
                : message + " (at " + Esj.Abbreviated(where, LocationExcerpt) + ")";
            bool documentLevel = ReferenceEquals(code, FindingCode.JsonCode)
                || ReferenceEquals(code, FindingCode.EncodingCode);
            SemanticPath path = documentLevel ? SemanticPath.Root() : _currentPath ?? SemanticPath.Root();
            return Validation.Finding.About(path, documentLevel ? string.Empty : where, code, text);
        }
    }
}
