using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace En16931.SemanticJson.Json;

/// <summary>
/// Writes a document as JSON, in the canonical form of the specification, section 7 or in
/// the pretty form of section 7.7.
/// </summary>
/// <remarks>
/// Every byte is decided here rather than by a JSON library, because a library's defaults —
/// an escaped solidus, an escaped non-ASCII character, a space after a colon — would each
/// change the canonical form and with it both digests. The content of a value is written as
/// it stands: this writer serializes a document and never rewrites what a value holds. A
/// JSON number inside <c>extensions</c> is written in the canonical decimal form of
/// section 7.6, rule 2, which is string processing over the digits the document carried.
/// </remarks>
public sealed class EsjWriter
{
    private readonly bool _pretty;

    private EsjWriter(bool pretty)
    {
        _pretty = pretty;
    }

    /// <summary>Returns a writer for the canonical form of the specification, section 7.</summary>
    /// <returns>the canonical writer</returns>
    public static EsjWriter Canonical() => new(false);

    /// <summary>Returns a writer for the pretty form of the specification, section 7.7.</summary>
    /// <returns>the pretty writer</returns>
    public static EsjWriter Pretty() => new(true);

    /// <summary>Writes a document and returns its bytes.</summary>
    /// <param name="document">the document</param>
    /// <returns>the bytes, encoded in UTF-8 without a byte order mark</returns>
    public byte[] ToBytes(SemanticDocument document)
    {
        ArgumentNullException.ThrowIfNull(document);
        Bytes bytes = new();
        WriteDocument(document, bytes);
        return bytes.ToArray();
    }

    /// <summary>Writes a document and returns it as text.</summary>
    /// <param name="document">the document</param>
    /// <returns>the JSON text</returns>
    public string ToText(SemanticDocument document) =>
        new UTF8Encoding(false).GetString(ToBytes(document));

    /// <summary>Writes a document to a stream.</summary>
    /// <param name="document">the document</param>
    /// <param name="output">the stream, which is written to and not closed</param>
    public void Write(SemanticDocument document, Stream output)
    {
        ArgumentNullException.ThrowIfNull(output);
        byte[] bytes = ToBytes(document);
        output.Write(bytes, 0, bytes.Length);
    }

    /// <summary>
    /// Writes the object the semantic digest is taken over: the edition and the values, and
    /// nothing else (specification, section 8.2).
    /// </summary>
    /// <param name="document">the document</param>
    /// <returns>the canonical bytes of that object</returns>
    public byte[] SemanticToBytes(SemanticDocument document)
    {
        ArgumentNullException.ThrowIfNull(document);
        Bytes bytes = new();
        bytes.Ascii('{');
        WriteMember(bytes, 1, true, "semanticModel");
        bytes.String(document.SemanticModel);
        WriteMember(bytes, 1, false, "values");
        WriteValues(document, bytes, 1);
        Close(bytes, 0, '}');
        return bytes.ToArray();
    }

    private void WriteDocument(SemanticDocument document, Bytes bytes)
    {
        bytes.Ascii('{');
        WriteMember(bytes, 1, true, "format");
        bytes.String(Esj.Format);
        WriteMember(bytes, 1, false, "version");
        bytes.String(Esj.Version);
        WriteMember(bytes, 1, false, "semanticModel");
        bytes.String(document.SemanticModel);
        WriteMember(bytes, 1, false, "values");
        WriteValues(document, bytes, 1);
        if (document.Extensions.Count > 0)
        {
            WriteMember(bytes, 1, false, "extensions");
            WriteExtensions(document.Extensions, bytes, 1);
        }

        if (document.Source is not null)
        {
            WriteMember(bytes, 1, false, "source");
            WriteSource(document.Source, bytes, 1);
        }

        Close(bytes, 0, '}');
        if (_pretty)
        {
            bytes.Ascii('\n');
        }
    }

    private void WriteValues(SemanticDocument document, Bytes bytes, int depth)
    {
        if (document.Values.Count == 0)
        {
            bytes.Ascii("{}");
            return;
        }

        bytes.Ascii('{');
        bool first = true;
        foreach (KeyValuePair<SemanticPath, SemanticValue> entry in document.Entries)
        {
            WriteMember(bytes, depth + 1, first, entry.Key.Text);
            WriteValue(entry.Value, bytes, depth + 1);
            first = false;
        }

        Close(bytes, depth, '}');
    }

    private void WriteValue(SemanticValue value, Bytes bytes, int depth)
    {
        if (!value.HasComponent)
        {
            bytes.String(value.Content);
            return;
        }

        bytes.Ascii('{');
        WriteMember(bytes, depth + 1, true, "value");
        bytes.String(value.Content);
        Component(bytes, depth, "scheme", value.Scheme);
        Component(bytes, depth, "schemeVersion", value.SchemeVersion);
        Component(bytes, depth, "mimeCode", value.MimeCode);
        Component(bytes, depth, "filename", value.Filename);
        Close(bytes, depth, '}');
    }

    private void Component(Bytes bytes, int depth, string name, string? value)
    {
        if (value is not null)
        {
            WriteMember(bytes, depth + 1, false, name);
            bytes.String(value);
        }
    }

    private void WriteSource(DocumentSource source, Bytes bytes, int depth)
    {
        bytes.Ascii('{');
        bool first = true;
        if (source.Syntax is not null)
        {
            WriteMember(bytes, depth + 1, true, "syntax");
            bytes.String(source.Syntax);
            first = false;
        }

        if (source.Sha256 is not null)
        {
            WriteMember(bytes, depth + 1, first, "sha256");
            bytes.String(source.Sha256);
        }

        Close(bytes, depth, '}');
    }

    private void WriteExtensions(
        IReadOnlyList<KeyValuePair<string, ExtensionValue>> extensions, Bytes bytes, int depth)
    {
        bytes.Ascii('{');
        bool first = true;
        foreach (KeyValuePair<string, ExtensionValue> entry in extensions)
        {
            WriteMember(bytes, depth + 1, first, entry.Key);
            WriteExtensionValue(entry.Value, bytes, depth + 1);
            first = false;
        }

        Close(bytes, depth, '}');
    }

    /// <summary>
    /// Writes one extension subtree. The walk is iterative, because a document assembled
    /// through this API may nest deeper than a document the reader would have accepted, and a
    /// recursive writer would answer that with a stack overflow rather than a serialization.
    /// </summary>
    private void WriteExtensionValue(ExtensionValue value, Bytes bytes, int depth)
    {
        Stack<Frame> open = new();
        ExtensionValue? pending = value;
        int pendingDepth = depth;
        while (true)
        {
            Frame? frame = WriteNode(pending!, bytes, pendingDepth);
            if (frame is not null)
            {
                open.Push(frame);
            }

            pending = null;
            while (pending is null)
            {
                if (open.Count == 0)
                {
                    return;
                }

                Frame top = open.Peek();
                if (!top.HasNext)
                {
                    Close(bytes, top.Depth, top.Bracket);
                    open.Pop();
                    continue;
                }

                pendingDepth = top.Depth + 1;
                pending = top.Next(this, bytes);
            }
        }
    }

    private Frame? WriteNode(ExtensionValue value, Bytes bytes, int depth)
    {
        switch (value.Kind)
        {
            case ExtensionKind.Object:
                if (value.Members.Count == 0)
                {
                    bytes.Ascii("{}");
                    return null;
                }

                bytes.Ascii('{');
                return Frame.OfObject(value.Members, depth);
            case ExtensionKind.Array:
                if (value.Elements.Count == 0)
                {
                    bytes.Ascii("[]");
                    return null;
                }

                bytes.Ascii('[');
                return Frame.OfArray(value.Elements, depth);
            case ExtensionKind.String:
                bytes.String(value.Text);
                return null;
            case ExtensionKind.Number:
                bytes.Ascii(CanonicalNumber(value.Text));
                return null;
            case ExtensionKind.Boolean:
                bytes.Ascii(value.Text);
                return null;
            default:
                bytes.Ascii("null");
                return null;
        }
    }

    private static string CanonicalNumber(string lexical)
    {
        string? canonical = Decimals.Canonicalize(lexical);
        return canonical ?? throw new EsjFormatException("ESJ-L1-EXT-NUMBER",
            "a number inside extensions has a canonical decimal form of more than "
            + Decimals.MaxLength + " characters");
    }

    private void WriteMember(Bytes bytes, int depth, bool first, string name)
    {
        if (!first)
        {
            bytes.Ascii(',');
        }

        Indent(bytes, depth);
        bytes.String(name);
        bytes.Ascii(':');
        if (_pretty)
        {
            bytes.Ascii(' ');
        }
    }

    private void Element(Bytes bytes, int depth, bool first)
    {
        if (!first)
        {
            bytes.Ascii(',');
        }

        Indent(bytes, depth);
    }

    private void Close(Bytes bytes, int depth, char bracket)
    {
        Indent(bytes, depth);
        bytes.Ascii(bracket);
    }

    private void Indent(Bytes bytes, int depth)
    {
        if (!_pretty)
        {
            return;
        }

        bytes.Ascii('\n');
        for (int level = 0; level < depth; level++)
        {
            bytes.Ascii("  ");
        }
    }

    /// <summary>One container the writer has opened and not yet closed.</summary>
    private sealed class Frame
    {
        private readonly IReadOnlyList<KeyValuePair<string, ExtensionValue>>? _members;
        private readonly IReadOnlyList<ExtensionValue>? _elements;
        private int _at;

        private Frame(
            IReadOnlyList<KeyValuePair<string, ExtensionValue>>? members,
            IReadOnlyList<ExtensionValue>? elements,
            char bracket,
            int depth)
        {
            _members = members;
            _elements = elements;
            Bracket = bracket;
            Depth = depth;
        }

        internal char Bracket { get; }

        internal int Depth { get; }

        internal bool HasNext => _at < (_members?.Count ?? _elements!.Count);

        internal static Frame OfObject(IReadOnlyList<KeyValuePair<string, ExtensionValue>> members, int depth) =>
            new(members, null, '}', depth);

        internal static Frame OfArray(IReadOnlyList<ExtensionValue> elements, int depth) =>
            new(null, elements, ']', depth);

        internal ExtensionValue Next(EsjWriter writer, Bytes bytes)
        {
            bool first = _at == 0;
            if (_members is not null)
            {
                KeyValuePair<string, ExtensionValue> member = _members[_at++];
                writer.WriteMember(bytes, Depth + 1, first, member.Key);
                return member.Value;
            }

            ExtensionValue element = _elements![_at++];
            writer.Element(bytes, Depth + 1, first);
            return element;
        }
    }

    /// <summary>
    /// The bytes of a JSON text, written one at a time: the escaping of the specification,
    /// section 7.5 and the UTF-8 encoding are this class and no library's.
    /// </summary>
    private sealed class Bytes
    {
        private const string Hex = "0123456789abcdef";

        private readonly MemoryStream _out = new(4096);

        internal void Ascii(char character) => _out.WriteByte((byte)character);

        internal void Ascii(string literal)
        {
            foreach (char character in literal)
            {
                _out.WriteByte((byte)character);
            }
        }

        internal void String(string value)
        {
            _out.WriteByte((byte)'"');
            for (int at = 0; at < value.Length; at++)
            {
                char character = value[at];
                switch (character)
                {
                    case '"':
                        Ascii("\\\"");
                        break;
                    case '\\':
                        Ascii("\\\\");
                        break;
                    case '\b':
                        Ascii("\\b");
                        break;
                    case '\t':
                        Ascii("\\t");
                        break;
                    case '\n':
                        Ascii("\\n");
                        break;
                    case '\f':
                        Ascii("\\f");
                        break;
                    case '\r':
                        Ascii("\\r");
                        break;
                    default:
                        WriteCharacter(value, character, ref at);
                        break;
                }
            }

            _out.WriteByte((byte)'"');
        }

        internal byte[] ToArray() => _out.ToArray();

        private void WriteCharacter(string value, char character, ref int at)
        {
            if (character < 0x20)
            {
                Ascii("\\u00");
                _out.WriteByte((byte)Hex[(character >> 4) & 0xF]);
                _out.WriteByte((byte)Hex[character & 0xF]);
                return;
            }

            if (character < 0x80)
            {
                _out.WriteByte((byte)character);
                return;
            }

            if (character < 0x800)
            {
                _out.WriteByte((byte)(0xC0 | (character >> 6)));
                _out.WriteByte((byte)(0x80 | (character & 0x3F)));
                return;
            }

            if (char.IsHighSurrogate(character))
            {
                if (at + 1 >= value.Length || !char.IsLowSurrogate(value[at + 1]))
                {
                    throw LoneSurrogate();
                }

                int codePoint = char.ConvertToUtf32(character, value[++at]);
                _out.WriteByte((byte)(0xF0 | (codePoint >> 18)));
                _out.WriteByte((byte)(0x80 | ((codePoint >> 12) & 0x3F)));
                _out.WriteByte((byte)(0x80 | ((codePoint >> 6) & 0x3F)));
                _out.WriteByte((byte)(0x80 | (codePoint & 0x3F)));
                return;
            }

            if (char.IsLowSurrogate(character))
            {
                throw LoneSurrogate();
            }

            _out.WriteByte((byte)(0xE0 | (character >> 12)));
            _out.WriteByte((byte)(0x80 | ((character >> 6) & 0x3F)));
            _out.WriteByte((byte)(0x80 | (character & 0x3F)));
        }

        private static EsjFormatException LoneSurrogate() => new("ESJ-L1-SURROGATE",
            "a string carries an unpaired surrogate and therefore has no UTF-8 encoding");
    }
}
