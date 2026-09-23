using System;
using Xunit;

namespace En16931.SemanticJson.Tests;

/// <summary>
/// The arithmetic every figure of an invoice goes through: exact, with the scale rules the
/// rule language is written against, and with no binary floating point anywhere.
/// </summary>
public class BigDecimalTests
{
    /// <summary>A sum keeps every digit of both summands.</summary>
    [Theory]
    [InlineData("0.1", "0.2", "0.3")]
    [InlineData("1.0000000000000001", "0", "1.0000000000000001")]
    [InlineData("-100.00", "100", "0.00")]
    public void ASumIsExact(string left, string right, string expected) =>
        Assert.Equal(expected, BigDecimal.Parse(left).Add(BigDecimal.Parse(right)).ToPlainString());

    /// <summary>A product carries the sum of the two scales.</summary>
    [Fact]
    public void AProductCarriesBothScales()
    {
        BigDecimal product = BigDecimal.Parse("233.50").Multiply(BigDecimal.Parse("19.00"));
        Assert.Equal("4436.5000", product.ToPlainString());
    }

    /// <summary>A division that terminates is exact, and one that does not names its scale.</summary>
    [Fact]
    public void ADivisionIsExactWhereItCanBe()
    {
        Assert.True(BigDecimal.Parse("1").TryDivide(BigDecimal.Parse("8"), 34, out BigDecimal eighth));
        Assert.Equal("0.125", eighth.ToPlainString());

        Assert.True(BigDecimal.Parse("1").TryDivide(BigDecimal.Parse("3"), 34, out BigDecimal third));
        Assert.Equal(34, third.Scale);
        Assert.StartsWith("0.3333333333", third.ToPlainString(), StringComparison.Ordinal);

        Assert.False(BigDecimal.Parse("1").TryDivide(BigDecimal.Zero, 34, out _));
    }

    /// <summary>
    /// A quotient that terminates is exact however far beyond the working precision it ends,
    /// because the fraction in its lowest terms decides it, not a precision to divide at.
    /// </summary>
    [Theory]
    [InlineData("0.00000000000000000000000000000000001", "1", "0.00000000000000000000000000000000001")]
    [InlineData("0.000000000000000000000000000000001", "8", "0.000000000000000000000000000000000125")]
    [InlineData("1", "1125899906842624", "0.00000000000000088817841970012523233890533447265625")]
    [InlineData("0.3", "1.2", "0.25")]
    [InlineData("100", "0.25", "400")]
    [InlineData("-0.3", "1.2", "-0.25")]
    public void ATerminatingQuotientIsExact(string dividend, string divisor, string expected)
    {
        Assert.True(BigDecimal.Parse(dividend).TryDivide(BigDecimal.Parse(divisor), 34, out BigDecimal quotient));
        Assert.Equal(expected, quotient.ToCanonicalString());
    }

    /// <summary>
    /// A quotient that does not terminate has 34 fraction digits, counted from the point, and
    /// the last of them is rounded half away from zero.
    /// </summary>
    [Theory]
    [InlineData("2", "3", "0.6666666666666666666666666666666667")]
    [InlineData("-2", "3", "-0.6666666666666666666666666666666667")]
    [InlineData("1", "7", "0.1428571428571428571428571428571429")]
    [InlineData("10", "3", "3.3333333333333333333333333333333333")]
    [InlineData("1", "3000", "0.0003333333333333333333333333333333")]
    public void ANonTerminatingQuotientIsRoundedAtTheWorkingPrecision(string dividend, string divisor, string expected)
    {
        Assert.True(BigDecimal.Parse(dividend).TryDivide(BigDecimal.Parse(divisor), 34, out BigDecimal quotient));
        Assert.Equal(expected, quotient.ToCanonicalString());
    }

    /// <summary>Rounding is half up, away from zero, which is what the standard asks for.</summary>
    [Theory]
    [InlineData("2.345", 2, "2.35")]
    [InlineData("-2.345", 2, "-2.35")]
    [InlineData("2.344", 2, "2.34")]
    [InlineData("100", 2, "100.00")]
    public void RoundingIsHalfUp(string value, int scale, string expected) =>
        Assert.Equal(expected, BigDecimal.Parse(value).SetScale(scale).ToPlainString());

    /// <summary>
    /// The canonical decimal form of section 6.4: no trailing fraction zeros, no signed zero,
    /// and the digits a writer takes from an exact decimal.
    /// </summary>
    [Theory]
    [InlineData("100.00", "100")]
    [InlineData("42.0150", "42.015")]
    [InlineData("-0.00", "0")]
    [InlineData("0.5", "0.5")]
    public void TheCanonicalFormHasOneSpellingPerNumber(string value, string expected) =>
        Assert.Equal(expected, BigDecimal.Parse(value).ToCanonicalString());

    /// <summary>Two numbers are equal by value, whatever scale either was written at.</summary>
    [Fact]
    public void EqualityIsByValue()
    {
        Assert.Equal(BigDecimal.Parse("100"), BigDecimal.Parse("100.00"));
        Assert.Equal(0, BigDecimal.Parse("100").CompareTo(BigDecimal.Parse("100.000")));
        Assert.True(BigDecimal.Parse("9").CompareTo(BigDecimal.Parse("10")) < 0);
    }
}
