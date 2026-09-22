using System;
using System.Globalization;
using System.Numerics;

namespace En16931.SemanticJson;

/// <summary>
/// An exact decimal number: an arbitrary precision unscaled value and a scale, so that
/// <c>100</c> and <c>100.00</c> are the same number with two different scales.
/// </summary>
/// <remarks>
/// The specification forbids binary floating point anywhere, in <c>values</c> and in
/// <c>extensions</c> alike, and the decimal grammar admits sixty-four characters, which is
/// more than the twenty-eight digits of the runtime's own <c>decimal</c> holds. The type is
/// therefore built on <see cref="BigInteger"/>, and it keeps the scale arithmetic of the
/// reference implementation so that a rule comparing two figures compares them the same way
/// in both: a sum has the larger of the two scales, a product the sum of them, and a division
/// whose quotient does not terminate is computed to a stated working precision rather than
/// refused.
/// </remarks>
public readonly struct BigDecimal : IEquatable<BigDecimal>, IComparable<BigDecimal>
{
    /// <summary>The number zero, with the scale zero.</summary>
    public static readonly BigDecimal Zero = new(BigInteger.Zero, 0);

    /// <summary>The number one, with the scale zero.</summary>
    public static readonly BigDecimal One = new(BigInteger.One, 0);

    private readonly BigInteger _unscaled;
    private readonly int _scale;

    /// <summary>Creates a number from its unscaled value and its scale.</summary>
    /// <param name="unscaled">the digits, as an integer</param>
    /// <param name="scale">how many of them are fraction digits</param>
    public BigDecimal(BigInteger unscaled, int scale)
    {
        _unscaled = unscaled;
        _scale = scale;
    }

    /// <summary>Returns the digits of the number, as an integer.</summary>
    public BigInteger Unscaled => _unscaled;

    /// <summary>Returns how many digits of the unscaled value are fraction digits.</summary>
    public int Scale => _scale;

    /// <summary>Returns the sign of the number: -1, 0 or 1.</summary>
    public int Sign => _unscaled.Sign;

    /// <summary>
    /// Reads a number written in plain notation, with an optional sign and an optional
    /// fraction part and no exponent.
    /// </summary>
    /// <param name="text">the number</param>
    /// <returns>the number</returns>
    /// <exception cref="FormatException">if the text is not a plain decimal</exception>
    public static BigDecimal Parse(string text)
    {
        if (!TryParse(text, out BigDecimal value))
        {
            throw new FormatException(text + " is not a decimal number");
        }

        return value;
    }

    /// <summary>
    /// Reads a number written in plain notation, without raising on a text that is not one.
    /// </summary>
    /// <param name="text">the number</param>
    /// <param name="value">the number, where the text is one</param>
    /// <returns>whether the text is a plain decimal</returns>
    public static bool TryParse(string text, out BigDecimal value)
    {
        value = Zero;
        if (string.IsNullOrEmpty(text))
        {
            return false;
        }

        int at = 0;
        bool negative = false;
        if (text[at] == '-' || text[at] == '+')
        {
            negative = text[at] == '-';
            at++;
        }

        int start = at;
        while (at < text.Length && char.IsAsciiDigit(text[at]))
        {
            at++;
        }

        int integerDigits = at - start;
        int fractionDigits = 0;
        if (at < text.Length && text[at] == '.')
        {
            at++;
            int fractionStart = at;
            while (at < text.Length && char.IsAsciiDigit(text[at]))
            {
                at++;
            }

            fractionDigits = at - fractionStart;
        }

        if (at != text.Length || integerDigits + fractionDigits == 0)
        {
            return false;
        }

        string digits = text.Substring(start).Replace(".", string.Empty, StringComparison.Ordinal);
        BigInteger unscaled = digits.Length == 0 ? BigInteger.Zero : BigInteger.Parse(digits, CultureInfo.InvariantCulture);
        value = new BigDecimal(negative ? -unscaled : unscaled, fractionDigits);
        return true;
    }

    /// <summary>Returns a whole number.</summary>
    /// <param name="value">the number</param>
    /// <returns>the number, with the scale zero</returns>
    public static BigDecimal FromInt64(long value) => new(new BigInteger(value), 0);

    /// <summary>Returns the sum of two numbers, with the larger of the two scales.</summary>
    /// <param name="other">the second summand</param>
    /// <returns>the sum</returns>
    public BigDecimal Add(BigDecimal other)
    {
        int scale = Math.Max(_scale, other._scale);
        return new BigDecimal(Rescale(_unscaled, _scale, scale) + Rescale(other._unscaled, other._scale, scale), scale);
    }

    /// <summary>Returns the difference of two numbers, with the larger of the two scales.</summary>
    /// <param name="other">the subtrahend</param>
    /// <returns>the difference</returns>
    public BigDecimal Subtract(BigDecimal other) => Add(other.Negate());

    /// <summary>Returns the product of two numbers, with the sum of the two scales.</summary>
    /// <param name="other">the second factor</param>
    /// <returns>the product</returns>
    public BigDecimal Multiply(BigDecimal other) =>
        new(_unscaled * other._unscaled, _scale + other._scale);

    /// <summary>Returns the number with the opposite sign.</summary>
    /// <returns>the negated number</returns>
    public BigDecimal Negate() => new(-_unscaled, _scale);

    /// <summary>Returns the number without its sign.</summary>
    /// <returns>the absolute value</returns>
    public BigDecimal Abs() => _unscaled.Sign < 0 ? Negate() : this;

    /// <summary>
    /// Divides by another number exactly where the quotient terminates, and to the working
    /// precision named by the rule language where it does not.
    /// </summary>
    /// <param name="divisor">the divisor</param>
    /// <param name="workingScale">the fraction digits a non-terminating quotient is computed to</param>
    /// <param name="quotient">the quotient, where the divisor is not zero</param>
    /// <returns>whether the division could be made, which it cannot be by zero</returns>
    public bool TryDivide(BigDecimal divisor, int workingScale, out BigDecimal quotient)
    {
        quotient = Zero;
        if (divisor._unscaled.IsZero)
        {
            return false;
        }

        BigInteger numerator = _unscaled;
        BigInteger denominator = divisor._unscaled;
        if (denominator.Sign < 0)
        {
            numerator = -numerator;
            denominator = -denominator;
        }

        BigInteger common = BigInteger.GreatestCommonDivisor(BigInteger.Abs(numerator), denominator);
        if (!common.IsZero && !common.IsOne)
        {
            numerator /= common;
            denominator /= common;
        }

        int twos = 0;
        while (denominator % 2 == 0)
        {
            denominator /= 2;
            twos++;
        }

        int fives = 0;
        while (denominator % 5 == 0)
        {
            denominator /= 5;
            fives++;
        }

        int preferred = _scale - divisor._scale;
        if (!denominator.IsOne)
        {
            quotient = DivideRounded(_unscaled, divisor._unscaled, _scale, divisor._scale, workingScale);
            return true;
        }

        int digits = Math.Max(twos, fives);
        BigInteger scaled = numerator * BigInteger.Pow(10, digits) / (BigInteger.Pow(2, twos) * BigInteger.Pow(5, fives));
        BigDecimal exact = new(scaled, digits + preferred);
        quotient = exact._scale < preferred ? exact.SetScale(preferred) : exact;
        return true;
    }

    private static BigDecimal DivideRounded(
        BigInteger numerator, BigInteger denominator, int numeratorScale, int denominatorScale, int scale)
    {
        int shift = scale + denominatorScale - numeratorScale;
        BigInteger left = numerator;
        BigInteger right = denominator;
        if (shift >= 0)
        {
            left *= BigInteger.Pow(10, shift);
        }
        else
        {
            right *= BigInteger.Pow(10, -shift);
        }

        return new BigDecimal(RoundHalfUp(left, right), scale);
    }

    private static BigInteger RoundHalfUp(BigInteger numerator, BigInteger denominator)
    {
        int sign = numerator.Sign * denominator.Sign;
        BigInteger left = BigInteger.Abs(numerator);
        BigInteger right = BigInteger.Abs(denominator);
        BigInteger whole = BigInteger.DivRem(left, right, out BigInteger rest);
        if (rest * 2 >= right)
        {
            whole += BigInteger.One;
        }

        return sign < 0 ? -whole : whole;
    }

    /// <summary>
    /// Returns the number written with exactly that many fraction digits, rounding half up
    /// where digits are dropped.
    /// </summary>
    /// <param name="scale">the fraction digits</param>
    /// <returns>the number at that scale</returns>
    public BigDecimal SetScale(int scale)
    {
        if (scale == _scale)
        {
            return this;
        }

        if (scale > _scale)
        {
            return new BigDecimal(_unscaled * BigInteger.Pow(10, scale - _scale), scale);
        }

        BigInteger divisor = BigInteger.Pow(10, _scale - scale);
        return new BigDecimal(RoundHalfUp(_unscaled, divisor), scale);
    }

    /// <summary>
    /// Returns the number without the trailing zeros of its fraction part, which is the
    /// spelling the canonical decimal form of the specification, section 6.4 asks for.
    /// </summary>
    /// <returns>the number at its smallest exact scale, never below zero</returns>
    public BigDecimal StripTrailingZeros()
    {
        if (_unscaled.IsZero)
        {
            return Zero;
        }

        BigInteger unscaled = _unscaled;
        int scale = _scale;
        while (scale > 0 && BigInteger.Remainder(unscaled, 10).IsZero)
        {
            unscaled /= 10;
            scale--;
        }

        return new BigDecimal(unscaled, scale);
    }

    /// <summary>Compares two numbers by value, ignoring their scales.</summary>
    /// <param name="other">the number to compare with</param>
    /// <returns>a negative number, zero or a positive number</returns>
    public int CompareTo(BigDecimal other)
    {
        int scale = Math.Max(_scale, other._scale);
        return BigInteger.Compare(Rescale(_unscaled, _scale, scale), Rescale(other._unscaled, other._scale, scale));
    }

    /// <summary>Tells whether two numbers are the same number, whatever their scales.</summary>
    /// <param name="other">the number to compare with</param>
    /// <returns>whether they are numerically equal</returns>
    public bool Equals(BigDecimal other) => CompareTo(other) == 0;

    /// <inheritdoc />
    public override bool Equals(object? obj) => obj is BigDecimal other && Equals(other);

    /// <inheritdoc />
    public override int GetHashCode() => StripTrailingZeros().ToPlainString().GetHashCode(StringComparison.Ordinal);

    /// <summary>Returns the number in plain notation, with the scale it carries.</summary>
    /// <returns>the number as text</returns>
    public string ToPlainString()
    {
        if (_scale == 0)
        {
            return _unscaled.ToString(CultureInfo.InvariantCulture);
        }

        bool negative = _unscaled.Sign < 0;
        string digits = BigInteger.Abs(_unscaled).ToString(CultureInfo.InvariantCulture);
        string text;
        if (_scale < 0)
        {
            text = digits + new string('0', -_scale);
        }
        else if (digits.Length > _scale)
        {
            text = digits.Substring(0, digits.Length - _scale) + "." + digits.Substring(digits.Length - _scale);
        }
        else
        {
            text = "0." + new string('0', _scale - digits.Length) + digits;
        }

        return negative ? "-" + text : text;
    }

    /// <summary>
    /// Returns the canonical decimal form of the specification, section 6.4: no exponent, no
    /// leading zeros, no trailing fraction zeros and no signed zero.
    /// </summary>
    /// <returns>the canonical spelling</returns>
    public string ToCanonicalString()
    {
        BigDecimal stripped = StripTrailingZeros();
        if (stripped._scale < 0)
        {
            stripped = stripped.SetScale(0);
        }

        return stripped._unscaled.IsZero ? "0" : stripped.ToPlainString();
    }

    /// <inheritdoc />
    public override string ToString() => ToPlainString();

    private static BigInteger Rescale(BigInteger unscaled, int from, int to) =>
        from == to ? unscaled : unscaled * BigInteger.Pow(10, to - from);
}
