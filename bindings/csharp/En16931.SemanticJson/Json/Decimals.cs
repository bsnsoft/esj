using System;
using System.Text;

namespace En16931.SemanticJson.Json;

/// <summary>
/// The canonical spelling of a JSON number inside <c>extensions</c> (specification,
/// section 7.6, rule 2).
/// </summary>
/// <remarks>
/// This is pure string processing. The number is taken as the document writes it — sign,
/// integer digits, fraction digits and exponent — the decimal point is shifted by the
/// exponent, the leading zeros of the integer part and the trailing zeros of the fraction
/// part are removed, and the result is written in plain notation. No numeric type takes
/// part in it, nothing is rounded and no digit is lost, which is what keeps the canonical
/// form of a document independent of any binary floating point conversion.
/// </remarks>
public static class Decimals
{
    /// <summary>The longest a canonical decimal form may be (specification, section 6.4).</summary>
    public const int MaxLength = 64;

    private const long ExponentSaturation = long.MaxValue / 8;
    private const long ExponentLastSafe = (ExponentSaturation - 9) / 10;

    /// <summary>
    /// Returns the canonical decimal form of a JSON number token, or <c>null</c> where that
    /// form would be longer than 64 characters.
    /// </summary>
    /// <param name="lexical">the number as the document spells it</param>
    /// <returns>the canonical form, or <c>null</c></returns>
    /// <exception cref="FormatException">if the token is not a number</exception>
    public static string? Canonicalize(string lexical)
    {
        ArgumentNullException.ThrowIfNull(lexical);
        int length = lexical.Length;
        int at = 0;
        bool negative = at < length && lexical[at] == '-';
        if (negative || (at < length && lexical[at] == '+'))
        {
            at++;
        }

        StringBuilder digits = new(length);
        int pointAfter = -1;
        while (at < length)
        {
            char character = lexical[at];
            if (char.IsAsciiDigit(character))
            {
                digits.Append(character);
                at++;
            }
            else if (character == '.' && pointAfter < 0)
            {
                pointAfter = digits.Length;
                at++;
            }
            else
            {
                break;
            }
        }

        if (digits.Length == 0)
        {
            throw new FormatException("not a number: " + lexical);
        }

        long exponent = 0;
        if (at < length)
        {
            char character = lexical[at];
            if (character != 'e' && character != 'E')
            {
                throw new FormatException("not a number: " + lexical);
            }

            exponent = ParseExponent(lexical, at + 1);
        }

        if (pointAfter < 0)
        {
            pointAfter = digits.Length;
        }

        return Plain(digits, pointAfter + exponent, negative);
    }

    private static long ParseExponent(string lexical, int from)
    {
        int length = lexical.Length;
        int at = from;
        bool negative = at < length && lexical[at] == '-';
        if (negative || (at < length && lexical[at] == '+'))
        {
            at++;
        }

        if (at >= length)
        {
            throw new FormatException("not a number: " + lexical);
        }

        long value = 0;
        while (at < length)
        {
            char character = lexical[at];
            if (!char.IsAsciiDigit(character))
            {
                throw new FormatException("not a number: " + lexical);
            }

            value = value <= ExponentLastSafe ? (value * 10) + (character - '0') : ExponentSaturation;
            at++;
        }

        return negative ? -value : value;
    }

    private static string? Plain(StringBuilder digits, long pointAfter, bool negative)
    {
        int first = 0;
        while (first < digits.Length && digits[first] == '0')
        {
            first++;
        }

        int last = digits.Length;
        while (last > first && digits[last - 1] == '0')
        {
            last--;
        }

        if (first == last)
        {
            return "0";
        }

        long point = pointAfter - first;
        int significant = last - first;
        long fractionDigits = significant - point;
        long size;
        if (fractionDigits <= 0)
        {
            size = point;
        }
        else if (fractionDigits < significant)
        {
            size = significant + 1L;
        }
        else
        {
            size = fractionDigits + 2L;
        }

        if (negative)
        {
            size++;
        }

        if (size > MaxLength)
        {
            return null;
        }

        StringBuilder plain = new((int)size);
        if (negative)
        {
            plain.Append('-');
        }

        if (fractionDigits <= 0)
        {
            plain.Append(digits, first, last - first);
            for (long zeros = 0; zeros < -fractionDigits; zeros++)
            {
                plain.Append('0');
            }
        }
        else if (fractionDigits < significant)
        {
            int split = first + (int)point;
            plain.Append(digits, first, split - first).Append('.').Append(digits, split, last - split);
        }
        else
        {
            plain.Append("0.");
            for (long zeros = 0; zeros < fractionDigits - significant; zeros++)
            {
                plain.Append('0');
            }

            plain.Append(digits, first, last - first);
        }

        return plain.ToString();
    }
}
