using System;

namespace En16931.SemanticJson;

/// <summary>
/// The content grammars of the specification, section 6: the decimal form, the date, the
/// time and the canonical base64 of an attachment.
/// </summary>
public static class Grammars
{
    /// <summary>
    /// The longest a decimal may be written, counting the sign and the decimal point
    /// (specification, section 6.4).
    /// </summary>
    public const int MaxDecimalLength = 64;

    /// <summary>
    /// Tells whether a content is the canonical decimal form: no exponent, no leading plus,
    /// no leading zeros, no trailing fraction zeros, no empty fraction and no signed zero.
    /// </summary>
    /// <param name="content">the content of a value</param>
    /// <returns>whether it satisfies section 6.4</returns>
    public static bool IsDecimal(string content)
    {
        if (content.Length == 0 || content.Length > MaxDecimalLength)
        {
            return false;
        }

        int at = 0;
        bool negative = content[0] == '-';
        if (negative)
        {
            at++;
        }

        int integerStart = at;
        while (at < content.Length && char.IsAsciiDigit(content[at]))
        {
            at++;
        }

        int integerDigits = at - integerStart;
        if (integerDigits == 0)
        {
            return false;
        }

        if (integerDigits > 1 && content[integerStart] == '0')
        {
            return false;
        }

        bool zero = integerDigits == 1 && content[integerStart] == '0';
        if (at == content.Length)
        {
            return !(negative && zero);
        }

        if (content[at] != '.')
        {
            return false;
        }

        at++;
        int fractionStart = at;
        while (at < content.Length && char.IsAsciiDigit(content[at]))
        {
            at++;
        }

        if (at != content.Length || at == fractionStart || content[at - 1] == '0')
        {
            return false;
        }

        return !negative || !zero || content.AsSpan(fractionStart).ContainsAnyExcept('0');
    }

    /// <summary>
    /// Tells whether a content is a calendar date of the proleptic Gregorian calendar,
    /// written with four digits of year from 1000 on (specification, section 6.5).
    /// </summary>
    /// <param name="content">the content of a value</param>
    /// <returns>whether it satisfies the date grammar and names a day that exists</returns>
    public static bool IsDate(string content) => TryReadDate(content, out _);

    /// <summary>Reads a calendar date.</summary>
    /// <param name="content">the content of a value</param>
    /// <param name="date">the date, where the content is one</param>
    /// <returns>whether the content is a date</returns>
    public static bool TryReadDate(string content, out DateOnly date)
    {
        date = default;
        if (content.Length != 10 || content[4] != '-' || content[7] != '-')
        {
            return false;
        }

        for (int at = 0; at < content.Length; at++)
        {
            if (at != 4 && at != 7 && !char.IsAsciiDigit(content[at]))
            {
                return false;
            }
        }

        int year = Number(content, 0, 4);
        int month = Number(content, 5, 2);
        int day = Number(content, 8, 2);
        if (year < 1000 || month < 1 || month > 12 || day < 1)
        {
            return false;
        }

        if (day > DateTime.DaysInMonth(year, month))
        {
            return false;
        }

        date = new DateOnly(year, month, day);
        return true;
    }

    /// <summary>
    /// Tells whether a content is a time of day with the offset it is stated in, in the one
    /// spelling the specification, section 6.5 admits.
    /// </summary>
    /// <param name="content">the content of a value</param>
    /// <returns>whether it satisfies the time grammar</returns>
    public static bool IsTime(string content)
    {
        if (content.Length < 9 || content[2] != ':' || content[5] != ':')
        {
            return false;
        }

        if (!IsTwoDigits(content, 0) || !IsTwoDigits(content, 3) || !IsTwoDigits(content, 6))
        {
            return false;
        }

        int hour = Number(content, 0, 2);
        int minute = Number(content, 3, 2);
        int second = Number(content, 6, 2);
        if (hour > 23 || minute > 59 || second > 59)
        {
            return false;
        }

        string offset = content.Substring(8);
        if (offset == "Z")
        {
            return true;
        }

        if (offset.Length != 6 || (offset[0] != '+' && offset[0] != '-') || offset[3] != ':')
        {
            return false;
        }

        if (!IsTwoDigits(offset, 1) || !IsTwoDigits(offset, 4))
        {
            return false;
        }

        int offsetHour = Number(offset, 1, 2);
        int offsetMinute = Number(offset, 4, 2);
        if (offsetMinute > 59 || offsetHour > 14 || (offsetHour == 14 && offsetMinute != 0))
        {
            return false;
        }

        return offsetHour != 0 || offsetMinute != 0;
    }

    /// <summary>
    /// Tells whether a content is canonical padded base64 of the standard alphabet
    /// (specification, section 6.7): the bits that pad the last quantum are zero, the padding
    /// is there, and no whitespace and no URL-safe character appears.
    /// </summary>
    /// <param name="content">the content of a value</param>
    /// <returns>whether it satisfies the base64 grammar</returns>
    public static bool IsCanonicalBase64(string content)
    {
        if (content.Length == 0 || content.Length % 4 != 0)
        {
            return false;
        }

        int padding = 0;
        while (padding < 2 && content[content.Length - 1 - padding] == '=')
        {
            padding++;
        }

        int characters = content.Length - padding;
        for (int at = 0; at < characters; at++)
        {
            if (!IsBase64Character(content[at]))
            {
                return false;
            }
        }

        if (padding == 0)
        {
            return true;
        }

        int last = Sextet(content[characters - 1]);
        int padBits = padding == 1 ? 2 : 4;
        return (last & ((1 << padBits) - 1)) == 0;
    }

    private static bool IsBase64Character(char character) =>
        char.IsAsciiLetterOrDigit(character) || character == '+' || character == '/';

    private static int Sextet(char character)
    {
        if (char.IsAsciiLetterUpper(character))
        {
            return character - 'A';
        }

        if (char.IsAsciiLetterLower(character))
        {
            return character - 'a' + 26;
        }

        if (char.IsAsciiDigit(character))
        {
            return character - '0' + 52;
        }

        return character == '+' ? 62 : 63;
    }

    private static bool IsTwoDigits(string text, int at) =>
        at + 1 < text.Length && char.IsAsciiDigit(text[at]) && char.IsAsciiDigit(text[at + 1]);

    private static int Number(string text, int at, int length)
    {
        int value = 0;
        for (int index = at; index < at + length; index++)
        {
            value = (value * 10) + (text[index] - '0');
        }

        return value;
    }
}
