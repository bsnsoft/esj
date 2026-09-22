using System;
using System.Text;

namespace En16931.SemanticJson.Json;

/// <summary>
/// The text questions a reader and a canonicalizer ask of a string: how many bytes its
/// UTF-8 encoding takes, whether it carries a lone surrogate, whether a token is an owner
/// token or a digest, and in which order two strings stand by Unicode code point.
/// </summary>
public static class Texts
{
    /// <summary>The longest an owner token may be (specification, section 4.6).</summary>
    public const int MaxOwnerTokenLength = 128;

    /// <summary>Returns how many bytes the UTF-8 encoding of a string takes.</summary>
    /// <param name="text">the string</param>
    /// <returns>the number of bytes</returns>
    public static long Utf8Length(string text)
    {
        ArgumentNullException.ThrowIfNull(text);
        long bytes = 0;
        for (int at = 0; at < text.Length; at++)
        {
            char character = text[at];
            if (character < 0x80)
            {
                bytes += 1;
            }
            else if (character < 0x800)
            {
                bytes += 2;
            }
            else if (char.IsHighSurrogate(character)
                && at + 1 < text.Length && char.IsLowSurrogate(text[at + 1]))
            {
                bytes += 4;
                at++;
            }
            else
            {
                bytes += 3;
            }
        }

        return bytes;
    }

    /// <summary>
    /// Tells whether a string carries a surrogate that is not part of a pair, which leaves
    /// it with no UTF-8 encoding (specification, section 6.8).
    /// </summary>
    /// <param name="text">the string</param>
    /// <returns>whether a lone surrogate is in it</returns>
    public static bool HasLoneSurrogate(string text)
    {
        ArgumentNullException.ThrowIfNull(text);
        for (int at = 0; at < text.Length; at++)
        {
            char character = text[at];
            if (char.IsHighSurrogate(character))
            {
                if (at + 1 >= text.Length || !char.IsLowSurrogate(text[at + 1]))
                {
                    return true;
                }

                at++;
            }
            else if (char.IsLowSurrogate(character))
            {
                return true;
            }
        }

        return false;
    }

    /// <summary>
    /// Normalizes line endings: CR LF and a lone CR become LF (specification, section 6.8).
    /// </summary>
    /// <param name="text">the string</param>
    /// <returns>the normalized string</returns>
    public static string NormalizeLineEndings(string text)
    {
        ArgumentNullException.ThrowIfNull(text);
        if (text.IndexOf('\r') < 0)
        {
            return text;
        }

        StringBuilder normalized = new(text.Length);
        for (int at = 0; at < text.Length; at++)
        {
            char character = text[at];
            if (character != '\r')
            {
                normalized.Append(character);
                continue;
            }

            normalized.Append('\n');
            if (at + 1 < text.Length && text[at + 1] == '\n')
            {
                at++;
            }
        }

        return normalized.ToString();
    }

    /// <summary>
    /// Tells whether a member name of <c>extensions</c> is an owner token: ASCII, starting
    /// and ending with a letter or a digit, at most 128 characters, and no <c>BT-</c> or
    /// <c>BG-</c> at its front (specification, section 4.6).
    /// </summary>
    /// <param name="token">the member name</param>
    /// <returns>whether it is an owner token</returns>
    public static bool IsOwnerToken(string token)
    {
        ArgumentNullException.ThrowIfNull(token);
        return OwnerTokenViolation(token) is null;
    }

    /// <summary>
    /// Returns what is wrong with a member name of <c>extensions</c>, or <c>null</c> where
    /// it is an owner token.
    /// </summary>
    /// <param name="token">the member name</param>
    /// <returns>the reason, in English, or <c>null</c></returns>
    public static string? OwnerTokenViolation(string token)
    {
        ArgumentNullException.ThrowIfNull(token);
        if (token.Length == 0)
        {
            return "is empty";
        }

        if (token.Length > MaxOwnerTokenLength)
        {
            return "is longer than " + MaxOwnerTokenLength + " characters";
        }

        if (token.StartsWith("BT-", StringComparison.Ordinal)
            || token.StartsWith("BG-", StringComparison.Ordinal))
        {
            return "begins with a business term prefix, which an owner token does not";
        }

        if (!IsOwnerAlphanumeric(token[0]) || !IsOwnerAlphanumeric(token[token.Length - 1]))
        {
            return "starts or ends with something other than a letter or a digit";
        }

        foreach (char character in token)
        {
            if (!IsOwnerAlphanumeric(character)
                && character != '.' && character != '_' && character != '-')
            {
                return "carries a character an owner token does not admit";
            }
        }

        return null;
    }

    /// <summary>
    /// Tells whether a string is 64 lowercase hexadecimal digits, which is what
    /// <c>source.sha256</c> carries (specification, section 4.7).
    /// </summary>
    /// <param name="text">the string</param>
    /// <returns>whether it is a digest</returns>
    public static bool IsLowercaseSha256(string text)
    {
        ArgumentNullException.ThrowIfNull(text);
        if (text.Length != 64)
        {
            return false;
        }

        foreach (char character in text)
        {
            if (!char.IsAsciiDigit(character) && (character < 'a' || character > 'f'))
            {
                return false;
            }
        }

        return true;
    }

    /// <summary>Returns what is wrong with a <c>source.sha256</c>, in English.</summary>
    /// <param name="text">the string</param>
    /// <returns>the reason</returns>
    public static string Sha256Violation(string text)
    {
        ArgumentNullException.ThrowIfNull(text);
        return text.Length != 64
            ? "is " + text.Length + " characters long, and a SHA-256 digest is 64"
            : "carries something other than a lowercase hexadecimal digit";
    }

    /// <summary>
    /// Returns how many bytes a base64 string decodes to, without decoding it
    /// (specification, section 12.5).
    /// </summary>
    /// <param name="content">the encoded content</param>
    /// <returns>the number of bytes the content stands for</returns>
    public static long DecodedBase64Length(string content)
    {
        ArgumentNullException.ThrowIfNull(content);
        long padding = 0;
        for (int at = content.Length - 1; at >= 0 && content[at] == '='; at--)
        {
            padding++;
        }

        return (content.Length / 4L * 3L) - padding;
    }

    /// <summary>
    /// Compares two strings by Unicode code point, which is the order of their UTF-8
    /// encodings and the order the canonical form inside <c>extensions</c> asks for
    /// (specification, section 7.6).
    /// </summary>
    /// <param name="left">the first string</param>
    /// <param name="right">the second string</param>
    /// <returns>a negative number, zero or a positive number</returns>
    public static int CompareByCodePoint(string left, string right)
    {
        ArgumentNullException.ThrowIfNull(left);
        ArgumentNullException.ThrowIfNull(right);
        int leftAt = 0;
        int rightAt = 0;
        while (leftAt < left.Length && rightAt < right.Length)
        {
            int leftPoint = CodePointAt(left, ref leftAt);
            int rightPoint = CodePointAt(right, ref rightAt);
            if (leftPoint != rightPoint)
            {
                return leftPoint.CompareTo(rightPoint);
            }
        }

        return (left.Length - leftAt).CompareTo(right.Length - rightAt);
    }

    /// <summary>
    /// Returns the code point at a position and advances past it. A surrogate with no
    /// partner stands for itself, so that the order is total over every string a caller
    /// may hold rather than only over those that have a UTF-8 encoding.
    /// </summary>
    private static int CodePointAt(string text, ref int at)
    {
        char character = text[at];
        if (char.IsHighSurrogate(character) && at + 1 < text.Length && char.IsLowSurrogate(text[at + 1]))
        {
            at += 2;
            return char.ConvertToUtf32(character, text[at - 1]);
        }

        at++;
        return character;
    }

    private static bool IsOwnerAlphanumeric(char character) => char.IsAsciiLetterOrDigit(character);
}
