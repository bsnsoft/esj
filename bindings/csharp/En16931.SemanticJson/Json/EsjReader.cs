using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using En16931.SemanticJson.Validation;

namespace En16931.SemanticJson.Json;

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
/// transformation it applies is the line ending normalization of section 6.8.</para>
/// <para>It needs no registry and makes no check that would need one. Whether the content of
/// a value is a canonical decimal, a date of the calendar or canonical base64 is decided by
/// the semantic data type the registry records for the term, so those checks belong to layer
/// L2. The content passes through this reader exactly as the document spells it,
/// <c>100.00</c> included: nothing here repairs a spelling.</para>
/// <para><see cref="Read"/> rejects and <see cref="ReadWithFindings"/> reports; both are
/// conformant and both carry the same finding code (specification, section 9.5).</para>
/// </remarks>
public sealed class EsjReader
{
    private static readonly string[] ValueMembers = { "value", "scheme", "schemeVersion", "mimeCode", "filename" };
    private static readonly string[] ComponentMembers = { "scheme", "schemeVersion", "mimeCode", "filename" };

    /// <summary>
    /// The envelope objects a nesting level is not counted from: the document object and the
    /// <c>values</c> or <c>extensions</c> object inside it. The bound of the specification,
    /// section 12.2 is counted below them, so the value of a member of <c>values</c> is
    /// level 1, exactly as the value of an owner-token member is.
    /// </summary>
    private const int EnvelopeNesting = 2;

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
                throw Fatal(FindingCode.JsonCode, string.Empty, "the top level of a document is a JSON object");
            }

            _at++;
            HashSet<string> seen = new(StringComparer.Ordinal);
            foreach (string name in Members(string.Empty))
            {
                NameTheReaderCannotTake(name, Excerpt(name), seen, string.Empty);

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
                        throw Fatal(FindingCode.EnvelopeMember, Excerpt(name),
                            "the envelope has no member named " + Excerpt(name));
                }
            }

            foreach (string required in new[] { "format", "version", "semanticModel", "values" })
            {
                if (!seen.Contains(required))
                {
                    throw Fatal(FindingCode.EnvelopeMember, string.Empty,
                        "the envelope member " + required + " is required");
                }
            }

            SkipWhitespace();
            if (_at != _text.Length)
            {
                throw Fatal(FindingCode.JsonCode, string.Empty,
                    "the document carries content after the object that closes it");
            }
        }

        private string Edition()
        {
            JsonKind kind = PeekKind();
            if (kind != JsonKind.String)
            {
                string type = Describe(kind);
                SkipValue(1);
                throw Fatal(FindingCode.EnvelopeValue, "semanticModel",
                    "the envelope member semanticModel is " + type + ", not a string");
            }

            string value = ReadString();
            RequireUnicode(value, "semanticModel");
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
            JsonKind kind = PeekKind();
            if (kind != JsonKind.String)
            {
                string type = Describe(kind);
                SkipValue(1);
                throw Fatal(FindingCode.EnvelopeValue, member,
                    "the envelope member " + member + " is " + type + ", not a string");
            }

            string value = ReadString();
            RequireUnicode(value, member);
            if (!string.Equals(expected, value, StringComparison.Ordinal))
            {
                throw Fatal(FindingCode.EnvelopeValue, member,
                    "the envelope member " + member + " carries " + Excerpt(value)
                    + "; its one value is " + expected);
            }
        }

        private void ReadSource()
        {
            if (PeekKind() != JsonKind.Object)
            {
                string type = Describe(PeekKind());
                SkipValue(1);
                throw Fatal(FindingCode.EnvelopeValue, "source",
                    "source is " + type + ", not a JSON object");
            }

            _at++;
            HashSet<string> seen = new(StringComparer.Ordinal);
            string? syntax = null;
            string? sha256 = null;
            foreach (string name in Members("source"))
            {
                string where = "source." + Esj.ForSubject(name);
                NameTheReaderCannotTake(name, where, seen, "source");

                if (name != "syntax" && name != "sha256")
                {
                    throw Fatal(FindingCode.EnvelopeMember, where,
                        "source has no member named " + Excerpt(name));
                }

                if (PeekKind() != JsonKind.String)
                {
                    string type = Describe(PeekKind());
                    SkipValue(2);
                    throw Fatal(FindingCode.EnvelopeValue, where,
                        name + " is " + type + ", not a string");
                }

                string value = ReadString();
                RequireUnicode(value, where);
                if (value.Length == 0)
                {
                    throw Fatal(FindingCode.EnvelopeValue, where, name + " is the empty string");
                }

                if (name == "syntax")
                {
                    if (Texts.Utf8Length(value) > _limits.MaxStringBytes)
                    {
                        throw LimitAt("source.syntax is longer than " + _limits.MaxStringBytes + " bytes", where);
                    }

                    syntax = value;
                }
                else
                {
                    if (!Texts.IsLowercaseSha256(value))
                    {
                        throw Fatal(FindingCode.EnvelopeValue, where, "sha256 " + Texts.Sha256Violation(value));
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
            if (PeekKind() != JsonKind.Object)
            {
                string type = Describe(PeekKind());
                SkipValue(1);
                throw Fatal(FindingCode.EnvelopeValue, "values",
                    "values is " + type + ", not a JSON object");
            }

            _at++;
            HashSet<string> seen = new(StringComparer.Ordinal);
            foreach (string name in Members("values"))
            {
                string where = ValuesWhere(name);
                NameTheReaderCannotTake(name, where, seen, "values");

                if (++_valueCount > _limits.MaxValues)
                {
                    throw LimitAt("values carries more than " + _limits.MaxValues + " members", where);
                }

                if (Texts.Utf8Length(name) > _limits.MaxPathBytes)
                {
                    throw LimitAt("a semantic path is longer than " + _limits.MaxPathBytes + " bytes", where);
                }

                SemanticPath? path = ReadPath(name, where);
                _currentPath = path;
                SemanticValue? value = ReadValue(where);
                if (path is not null && value is not null)
                {
                    _values[path] = value;
                }

                _currentPath = null;
            }
        }

        private SemanticPath? ReadPath(string name, string where)
        {
            if (!SemanticPath.TryParse(name, out SemanticPath path))
            {
                Report(FindingCode.PathSyntax, where, Excerpt(name) + " is not a semantic path");
                return null;
            }

            if (path.Segments.Count > _limits.MaxPathSegments)
            {
                throw LimitAt("a semantic path has more than " + _limits.MaxPathSegments + " segments", where);
            }

            return path;
        }

        private SemanticValue? ReadValue(string where)
        {
            JsonKind kind = PeekKind();
            if (kind == JsonKind.String)
            {
                string? content = String(ReadString(), where, "this value", false);
                return content is null ? null : new SemanticValue(content);
            }

            if (kind != JsonKind.Object)
            {
                string type = Describe(kind);
                SkipValue(2);
                Report(FindingCode.JsonType, where,
                    "this member of values is " + type + ", not a string and not a value object");
                return null;
            }

            return ReadValueObject(where);
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
            foreach (string name in Members(where))
            {
                NameTheReaderCannotTake(name, where, seen, where);

                if (members.Count >= _limits.MaxValueMembers)
                {
                    throw LimitAt("a value object carries more than " + _limits.MaxValueMembers + " members", where);
                }

                component |= Array.IndexOf(ComponentMembers, name) >= 0;
                JsonKind kind = PeekKind();
                string type = Describe(kind);
                string? text = kind == JsonKind.String ? ReadString() : null;
                if (text is null)
                {
                    SkipValue(3);
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
                    Report(FindingCode.ValueShape, MemberWhere(where, member.Name),
                        Excerpt(member.Name) + " is an object, not a string");
                    return null;
                }
            }

            foreach (Member member in members)
            {
                if (member.Kind != JsonKind.String)
                {
                    Report(FindingCode.JsonType, MemberWhere(where, member.Name),
                        Excerpt(member.Name) + " is " + member.JsonType + ", not a string");
                    return null;
                }
            }

            Dictionary<string, string> byName = new(StringComparer.Ordinal);
            foreach (Member member in members)
            {
                if (Array.IndexOf(ValueMembers, member.Name) < 0)
                {
                    Report(FindingCode.ValueMember, MemberWhere(where, member.Name),
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
                    Report(FindingCode.Surrogate, MemberWhere(where, member.Name),
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
                string? content = Content(member.Value, MemberWhere(where, member.Key), member.Key,
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

        // ------------------------------------------------------------ extensions

        private void ReadExtensions()
        {
            if (PeekKind() != JsonKind.Object)
            {
                string type = Describe(PeekKind());
                SkipValue(1);
                throw Fatal(FindingCode.EnvelopeValue, "extensions",
                    "extensions is " + type + ", not a JSON object");
            }

            _at++;
            HashSet<string> seen = new(StringComparer.Ordinal);
            foreach (string owner in Members("extensions"))
            {
                NameTheReaderCannotTake(owner, OwnerWhere(owner), seen, "extensions");

                if (!Texts.IsOwnerToken(owner))
                {
                    throw Fatal(FindingCode.OwnerToken, OwnerWhere(owner),
                        "the owner token " + Excerpt(owner) + " " + Texts.OwnerTokenViolation(owner));
                }

                _extensions.Add(new KeyValuePair<string, ExtensionValue>(
                    owner, ReadExtensionValue(OwnerWhere(owner))));
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
        /// </summary>
        private ExtensionValue ReadExtensionValue(string root)
        {
            Stack<Container> open = new();
            string where = root;
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
                                + _limits.MaxExtensionDepth + " levels", where);
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
                    if (!NextMember(container.Any, container.Where))
                    {
                        finished = container.Build();
                        open.Pop();
                        continue;
                    }

                    string name = ReadString();
                    string memberWhere = container.Where + "[\"" + Esj.ForSubject(name) + "\"]";
                    CheckExtensionString(name, memberWhere);
                    if (container.Has(name))
                    {
                        throw Duplicate(name, container.Where);
                    }

                    container.Expect(name);
                    Colon();
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

                    where = container.Where + "[" + container.Size.ToString(CultureInfo.InvariantCulture) + "]";
                }

                container.Any = true;
                depth = container.Depth + 1;
            }
        }

        private void CountExtensionNode(string where)
        {
            if (++_extensionNodes > _limits.MaxExtensionNodes)
            {
                throw LimitAt("extensions carries more than " + _limits.MaxExtensionNodes + " nodes", where);
            }
        }

        private ExtensionValue Scalar(JsonKind kind, string where)
        {
            switch (kind)
            {
                case JsonKind.String:
                    string value = ReadString();
                    CheckExtensionString(value, where);
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

        private void CheckExtensionString(string value, string where)
        {
            if (Texts.HasLoneSurrogate(value))
            {
                throw Fatal(FindingCode.Surrogate, where, "a string carries an unpaired surrogate");
            }

            if (Texts.Utf8Length(value) > _limits.MaxStringBytes)
            {
                throw LimitAt("a string inside extensions is longer than "
                    + _limits.MaxStringBytes + " bytes", where);
            }
        }

        /// <summary>
        /// Reads one number of <c>extensions</c> and returns its canonical decimal form. The
        /// spelling is measured against the string bound exactly, because two readers running
        /// the defaults have to refuse the same tokens (specification, section 12.2).
        /// </summary>
        private string Number(string where)
        {
            string lexical = ReadNumberToken();
            if (lexical.Length > _limits.MaxStringBytes)
            {
                throw LimitAt("a number inside extensions is spelled in more than "
                    + _limits.MaxStringBytes + " bytes", where);
            }

            string? canonical = Decimals.Canonicalize(lexical);
            if (canonical is null)
            {
                throw Fatal(FindingCode.ExtensionNumber, where,
                    "a number inside extensions has a canonical decimal form of more than "
                    + Decimals.MaxLength + " characters");
            }

            return canonical;
        }

        /// <summary>One container of an extension subtree that the reader has opened.</summary>
        private sealed class Container
        {
            private readonly List<KeyValuePair<string, ExtensionValue>>? _members;
            private readonly List<ExtensionValue>? _elements;
            private readonly HashSet<string>? _names;
            private string? _pending;

            internal Container(bool isObject, string where, int depth)
            {
                IsObject = isObject;
                Where = where;
                Depth = depth;
                _members = isObject ? new List<KeyValuePair<string, ExtensionValue>>() : null;
                _names = isObject ? new HashSet<string>(StringComparer.Ordinal) : null;
                _elements = isObject ? null : new List<ExtensionValue>();
            }

            internal bool IsObject { get; }

            internal string Where { get; }

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
        /// leaving the parser on the value that follows it.
        /// </summary>
        private IEnumerable<string> Members(string where)
        {
            bool any = false;
            while (true)
            {
                SkipWhitespace();
                if (_at >= _text.Length)
                {
                    throw Malformed("an object is not closed", where);
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
                        throw Malformed("a comma separates two members of an object", where);
                    }

                    _at++;
                    SkipWhitespace();
                }

                if (_at >= _text.Length || _text[_at] != '"')
                {
                    throw Malformed("a member name is a JSON string", where);
                }

                string name = ReadString();
                Colon();
                any = true;
                yield return name;
            }
        }

        /// <summary>
        /// Stands on the next member name of an open object, or on the brace that closes it.
        /// </summary>
        private bool NextMember(bool any, string where)
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
                    throw Malformed("a comma separates two members of an object", where);
                }

                _at++;
                SkipWhitespace();
            }

            if (_at >= _text.Length || _text[_at] != '"')
            {
                throw Malformed("a member name is a JSON string", where);
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
                    throw Malformed("a comma separates two elements of an array", string.Empty);
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
                throw Malformed("a colon separates a member name from its value", string.Empty);
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

        /// <summary>Returns what kind of JSON value the parser stands on, without consuming it.</summary>
        private JsonKind PeekKind()
        {
            SkipWhitespace();
            if (_at >= _text.Length)
            {
                throw Malformed("the document ends where a value belongs", string.Empty);
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
                    : throw Malformed("a JSON value does not begin with " + Excerpt(character.ToString()), string.Empty),
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

        private string ReadString()
        {
            SkipWhitespace();
            if (_at >= _text.Length || _text[_at] != '"')
            {
                throw Malformed("a string begins with a quotation mark", string.Empty);
            }

            _at++;
            StringBuilder value = new();
            while (true)
            {
                if (_at >= _text.Length)
                {
                    throw Malformed("a string is not closed", string.Empty);
                }

                char character = _text[_at++];
                if (character == '"')
                {
                    return value.ToString();
                }

                if (character < 0x20)
                {
                    throw Malformed("a control character inside a string is written as an escape", string.Empty);
                }

                if (character != '\\')
                {
                    value.Append(character);
                    continue;
                }

                if (_at >= _text.Length)
                {
                    throw Malformed("a string is not closed", string.Empty);
                }

                char escape = _text[_at++];
                switch (escape)
                {
                    case '"':
                        value.Append('"');
                        break;
                    case '\\':
                        value.Append('\\');
                        break;
                    case '/':
                        value.Append('/');
                        break;
                    case 'b':
                        value.Append('\b');
                        break;
                    case 'f':
                        value.Append('\f');
                        break;
                    case 'n':
                        value.Append('\n');
                        break;
                    case 'r':
                        value.Append('\r');
                        break;
                    case 't':
                        value.Append('\t');
                        break;
                    case 'u':
                        value.Append(Hex4());
                        break;
                    default:
                        throw Malformed("a string carries an escape this format does not define", string.Empty);
                }
            }
        }

        private char Hex4()
        {
            if (_at + 4 > _text.Length)
            {
                throw Malformed("a unicode escape carries four hexadecimal digits", string.Empty);
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
                    _ => throw Malformed("a unicode escape carries four hexadecimal digits", string.Empty),
                };
                value = (value * 16) + number;
            }

            return (char)value;
        }

        private string ReadNumberToken()
        {
            SkipWhitespace();
            int start = _at;
            if (_at < _text.Length && _text[_at] == '-')
            {
                _at++;
            }

            int digits = Digits();
            if (digits == 0)
            {
                throw Malformed("a number carries at least one digit", string.Empty);
            }

            if (digits > 1 && _text[start + (_text[start] == '-' ? 1 : 0)] == '0')
            {
                throw Malformed("a number carries no leading zero", string.Empty);
            }

            if (_at < _text.Length && _text[_at] == '.')
            {
                _at++;
                if (Digits() == 0)
                {
                    throw Malformed("a fraction carries at least one digit", string.Empty);
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
                    throw Malformed("an exponent carries at least one digit", string.Empty);
                }
            }

            return _text.Substring(start, _at - start);
        }

        private int Digits()
        {
            int start = _at;
            while (_at < _text.Length && char.IsAsciiDigit(_text[_at]))
            {
                _at++;
            }

            return _at - start;
        }

        /// <summary>Walks past a scalar the reader neither keeps nor judges.</summary>
        private void ConsumeScalar(JsonKind kind)
        {
            switch (kind)
            {
                case JsonKind.String:
                    ReadString();
                    break;
                case JsonKind.Number:
                    ReadNumberToken();
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

        private void Literal(string word)
        {
            if (_at + word.Length > _text.Length
                || string.CompareOrdinal(_text, _at, word, 0, word.Length) != 0)
            {
                throw Malformed("a JSON literal is true, false or null", string.Empty);
            }

            _at += word.Length;
        }

        /// <summary>
        /// Walks past a value the reader does not keep. The walk is bounded, because a
        /// reader that has to get past a structure nested deeper than the bound allows
        /// reports the limit rather than following it to any depth (specification,
        /// section 12.2).
        /// </summary>
        private void SkipValue(int depth)
        {
            int bound = _limits.MaxExtensionDepth + EnvelopeNesting;
            int level = depth;
            Stack<char> open = new();
            bool opened = false;
            while (true)
            {
                JsonKind kind = PeekKind();
                if (kind is JsonKind.Object or JsonKind.Array)
                {
                    if (++level > bound)
                    {
                        throw LimitAt("a place in the document nests deeper than "
                            + _limits.MaxExtensionDepth + " levels", string.Empty);
                    }

                    open.Push(kind == JsonKind.Object ? '}' : ']');
                    _at++;
                    opened = true;
                }
                else
                {
                    ConsumeScalar(kind);
                    opened = false;
                }

                while (open.Count > 0)
                {
                    SkipWhitespace();
                    if (_at >= _text.Length)
                    {
                        throw Malformed("a container is not closed", string.Empty);
                    }

                    if (_text[_at] == open.Peek())
                    {
                        _at++;
                        open.Pop();
                        level--;
                        opened = false;
                        continue;
                    }

                    if (!opened)
                    {
                        if (_text[_at] != ',')
                        {
                            throw Malformed("a comma separates two members of a container", string.Empty);
                        }

                        _at++;
                        SkipWhitespace();
                    }

                    if (open.Peek() == '}')
                    {
                        ReadString();
                        Colon();
                    }

                    opened = false;
                    break;
                }

                if (open.Count == 0)
                {
                    return;
                }
            }
        }

        // ------------------------------------------------------------ findings

        private string ValuesWhere(string name) => "values[\"" + Esj.ForSubject(name) + "\"]";

        private static string MemberWhere(string where, string name) => where + "." + Esj.ForSubject(name);

        private static string OwnerWhere(string owner) => "extensions[\"" + Esj.ForSubject(owner) + "\"]";

        private static string Excerpt(string value) => Esj.ForMessage(value, Esj.MessageExcerpt);

        /// <summary>
        /// Holds one member name to the two defects the specification, section 9.6 holds against
        /// the JSON text rather than against the value written under the name: a name carrying a
        /// lone surrogate, which names nothing, and a name that has already occurred in this
        /// object, which leaves no one object to judge. Either ends the read with that one code
        /// and the object is judged no further. The surrogate is asked first because the two are
        /// ranked by the place the text reaches first and a repeated name is met at its second
        /// occurrence, so the earlier of the two is always the one reported.
        /// </summary>
        /// <param name="name">the member name, as the document spells it</param>
        /// <param name="where">the member access a finding about this name names</param>
        /// <param name="seen">the names this object has already carried</param>
        /// <param name="duplicateWhere">the member access a duplicate in this object names</param>
        private void NameTheReaderCannotTake(
            string name, string where, HashSet<string> seen, string duplicateWhere)
        {
            if (Texts.HasLoneSurrogate(name))
            {
                throw Fatal(FindingCode.Surrogate, where, "a member name carries an unpaired surrogate");
            }

            if (!seen.Add(name))
            {
                throw Duplicate(name, duplicateWhere);
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

        private Exception Malformed(string what, string where) =>
            Fatal(FindingCode.JsonCode, where,
                "the document is not a JSON text: " + what + " (at character "
                + _at.ToString(CultureInfo.InvariantCulture) + ")");

        private EsjLimitException LimitAt(string message, string where)
        {
            _collector?.Add(Finding(FindingCode.Limit, where, message));
            return new EsjLimitException(message, where);
        }

        /// <summary>Reports a problem that ends the parse, and returns what to throw for it.</summary>
        private Exception Fatal(FindingCode code, string where, string message)
        {
            if (_collector is not null)
            {
                _collector.Add(Finding(code, where, message));
                return new Stop();
            }

            return new EsjFormatException(code.Code, message, where);
        }

        /// <summary>Reports a problem that is local to one member of <c>values</c>.</summary>
        private void Report(FindingCode code, string where, string message)
        {
            if (_collector is not null)
            {
                _collector.Add(Finding(code, where, message));
                return;
            }

            throw new EsjFormatException(code.Code, message, where);
        }

        private Finding Finding(FindingCode code, string where, string message)
        {
            string text = where.Length == 0
                ? message
                : message + " (at " + Esj.Abbreviated(where, LocationExcerpt) + ")";
            return Validation.Finding.About(_currentPath ?? SemanticPath.Root(), where, code, text);
        }
    }
}
