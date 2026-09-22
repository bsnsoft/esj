using System;
using System.Globalization;
using System.Text;

namespace En16931.SemanticJson;

/// <summary>The constants of the format, and the text helpers every message uses.</summary>
public static class Esj
{
    /// <summary>The value of the <c>format</c> member of every document.</summary>
    public const string Format = "EN16931-Semantic-JSON";

    /// <summary>The version of the specification this implementation carries.</summary>
    public const string Version = "0.1";

    /// <summary>The edition a document names unless it is asked for another.</summary>
    public const string DefaultSemanticModel = "EN16931-1:2017+A1:2019/AC:2020";

    /// <summary>The file extension of a document.</summary>
    public const string FileExtension = ".esj.json";

    /// <summary>The provisional media type, which is not registered with IANA.</summary>
    public const string MediaType = "application/vnd.en16931-semantic+json";

    /// <summary>The longest fragment of document content a message reproduces.</summary>
    public const int MessageExcerpt = 80;

    /// <summary>
    /// Returns a fragment of content fit for a message: escaped as the specification,
    /// section 9.5 requires, and cut so that a hostile document cannot decide how long a
    /// log line is (section 12.6).
    /// </summary>
    /// <param name="text">the content</param>
    /// <returns>the fragment</returns>
    public static string ForMessage(string text) => ForMessage(text, MessageExcerpt);

    /// <summary>Returns a fragment of content fit for a message, cut to a stated length.</summary>
    /// <param name="text">the content</param>
    /// <param name="excerpt">the longest fragment to reproduce</param>
    /// <returns>the fragment</returns>
    public static string ForMessage(string text, int excerpt)
    {
        ArgumentNullException.ThrowIfNull(text);
        if (excerpt < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(excerpt), excerpt,
                "a message excerpt is at least one character long");
        }

        if (text.Length <= excerpt)
        {
            return Escape(text);
        }

        int end = excerpt;
        if (char.IsHighSurrogate(text[end - 1]))
        {
            end--;
        }

        return Escape(text.Substring(0, end)) + "...";
    }

    /// <summary>
    /// Returns a fragment of content in the form a finding's subject carries it: escaped
    /// the same way and whole, because a subject is a structured field a program reads and
    /// is what tells two findings apart (specification, section 9.5).
    /// </summary>
    /// <param name="text">the content</param>
    /// <returns>the fragment, escaped and whole</returns>
    public static string ForSubject(string text)
    {
        ArgumentNullException.ThrowIfNull(text);
        return Escape(text);
    }

    /// <summary>
    /// Holds an already escaped fragment to a length, cutting on the boundary of an escape
    /// sequence and never inside one.
    /// </summary>
    /// <param name="escaped">the fragment, already escaped</param>
    /// <param name="maxLength">the longest fragment to keep</param>
    /// <returns>the fragment, cut where it was longer</returns>
    public static string Abbreviated(string escaped, int maxLength)
    {
        ArgumentNullException.ThrowIfNull(escaped);
        if (maxLength < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(maxLength), maxLength,
                "a message excerpt is at least one character long");
        }

        if (escaped.Length <= maxLength)
        {
            return escaped;
        }

        int at = 0;
        int cut = 0;
        while (at < escaped.Length)
        {
            int unit = 1;
            if (escaped[at] == '\\' && at + 1 < escaped.Length)
            {
                unit = escaped[at + 1] == 'u' ? 6 : 2;
            }

            if (at + unit > maxLength)
            {
                break;
            }

            at += unit;
            cut = at;
        }

        if (cut > 0 && char.IsHighSurrogate(escaped[cut - 1]))
        {
            cut--;
        }

        return escaped.Substring(0, cut) + "...";
    }

    /// <summary>
    /// Tells whether a string satisfies the edition grammar of the specification,
    /// section 4.4, which is the whole of what layer L1 asks of <c>semanticModel</c>.
    /// </summary>
    /// <param name="value">the value of the member</param>
    /// <returns>whether it is an edition string</returns>
    public static bool IsEdition(string value)
    {
        ArgumentNullException.ThrowIfNull(value);
        int at = ModelToken(value, 0);
        if (at < 0)
        {
            return false;
        }

        if (at < value.Length && value[at] == '+')
        {
            at = Amendment(value, at + 1);
            if (at < 0)
            {
                return false;
            }
        }

        if (at < value.Length && value[at] == '/')
        {
            at = Corrigendum(value, at + 1);
            if (at < 0)
            {
                return false;
            }
        }

        while (at < value.Length)
        {
            if (value[at] != '+')
            {
                return false;
            }

            at = Amendment(value, at + 1);
            if (at < 0)
            {
                return false;
            }

            if (at < value.Length && value[at] == '/')
            {
                at = Corrigendum(value, at + 1);
                if (at < 0)
                {
                    return false;
                }
            }
        }

        return true;
    }

    /// <summary>
    /// Returns the <c>semanticModel</c> spelling of an edition a registry writes with
    /// spaces (specification, section 10).
    /// </summary>
    /// <param name="edition">the edition as a registry spells it</param>
    /// <returns>the same edition with every space removed</returns>
    public static string SemanticModelOf(string edition)
    {
        ArgumentNullException.ThrowIfNull(edition);
        return edition.Replace(" ", string.Empty, StringComparison.Ordinal);
    }

    private static string Escape(string value)
    {
        StringBuilder text = new(value.Length);
        foreach (char character in value)
        {
            switch (character)
            {
                case '\\':
                    text.Append("\\\\");
                    break;
                case '"':
                    text.Append("\\\"");
                    break;
                case '\n':
                    text.Append("\\n");
                    break;
                case '\r':
                    text.Append("\\r");
                    break;
                case '\t':
                    text.Append("\\t");
                    break;
                default:
                    if (SteersATerminal(character))
                    {
                        text.Append("\\u").Append(((int)character).ToString("x4", CultureInfo.InvariantCulture));
                    }
                    else
                    {
                        text.Append(character);
                    }

                    break;
            }
        }

        return text.ToString();
    }

    /// <summary>
    /// Tells whether a character would steer a terminal rather than appear in it: a C0
    /// control, the delete character, or one of the bidirectional formatting characters.
    /// </summary>
    private static bool SteersATerminal(char character) =>
        character < 0x20
        || character == 0x7F
        || character == '‎' || character == '‏'
        || (character >= '‪' && character <= '‮')
        || (character >= '⁦' && character <= '⁩');

    /// <summary>Reads one or more alphanumeric groups joined by hyphens, a colon and a year.</summary>
    private static int ModelToken(string value, int from)
    {
        int at = Alphanumeric(value, from);
        if (at < 0)
        {
            return -1;
        }

        while (at < value.Length && value[at] == '-')
        {
            at = Alphanumeric(value, at + 1);
            if (at < 0)
            {
                return -1;
            }
        }

        return at >= value.Length || value[at] != ':' ? -1 : Year(value, at + 1);
    }

    private static int Amendment(string value, int from)
    {
        if (from >= value.Length || value[from] != 'A')
        {
            return -1;
        }

        int at = Digits(value, from + 1, 1);
        return at < 0 || at >= value.Length || value[at] != ':' ? -1 : Year(value, at + 1);
    }

    private static int Corrigendum(string value, int from)
    {
        if (from + 1 >= value.Length || value[from] != 'A' || value[from + 1] != 'C')
        {
            return -1;
        }

        int at = Digits(value, from + 2, 0);
        return at >= value.Length || value[at] != ':' ? -1 : Year(value, at + 1);
    }

    private static int Year(string value, int from) => Digits(value, from, 4) == from + 4 ? from + 4 : -1;

    private static int Digits(string value, int from, int least)
    {
        int at = from;
        while (at < value.Length && char.IsAsciiDigit(value[at]))
        {
            at++;
        }

        return at - from < least ? -1 : at;
    }

    private static int Alphanumeric(string value, int from)
    {
        int at = from;
        while (at < value.Length && char.IsAsciiLetterOrDigit(value[at]))
        {
            at++;
        }

        return at == from ? -1 : at;
    }
}
