using System.Globalization;
using Xunit;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// The one set of characters no message and no subject of this implementation carries as it
/// stands (specification, section 9.5), class by class, and the escaping over each of them. The
/// classes and the code points are those of the Java implementation's test of the same set, so
/// that the three implementations are held to one list.
/// </summary>
public class TerminalCharactersTests
{
    /// <summary>A C0 control steers a terminal and is escaped.</summary>
    /// <param name="codePoint">the character</param>
    [Theory]
    [InlineData(0x00)]
    [InlineData(0x07)]
    [InlineData(0x08)]
    [InlineData(0x0B)]
    [InlineData(0x0C)]
    [InlineData(0x1B)]
    [InlineData(0x1F)]
    public void AC0ControlSteersATerminal(int codePoint) => AssertSteersAndIsEscaped(codePoint);

    /// <summary>The delete character steers a terminal and is escaped.</summary>
    [Fact]
    public void TheDeleteCharacterSteersATerminal() => AssertSteersAndIsEscaped(0x7F);

    /// <summary>A C1 control steers a terminal and is escaped.</summary>
    /// <param name="codePoint">the character</param>
    [Theory]
    [InlineData(0x80)]
    [InlineData(0x85)]
    [InlineData(0x8D)]
    [InlineData(0x90)]
    [InlineData(0x9B)]
    [InlineData(0x9C)]
    [InlineData(0x9D)]
    [InlineData(0x9F)]
    public void AC1ControlSteersATerminal(int codePoint) => AssertSteersAndIsEscaped(codePoint);

    /// <summary>The line and the paragraph separator steer a terminal and are escaped.</summary>
    /// <param name="codePoint">the character</param>
    [Theory]
    [InlineData(0x2028)]
    [InlineData(0x2029)]
    public void ALineOrParagraphSeparatorSteersATerminal(int codePoint) => AssertSteersAndIsEscaped(codePoint);

    /// <summary>A bidirectional formatting character steers a terminal and is escaped.</summary>
    /// <param name="codePoint">the character</param>
    [Theory]
    [InlineData(0x061C)]
    [InlineData(0x200E)]
    [InlineData(0x200F)]
    [InlineData(0x202A)]
    [InlineData(0x202B)]
    [InlineData(0x202C)]
    [InlineData(0x202D)]
    [InlineData(0x202E)]
    [InlineData(0x2066)]
    [InlineData(0x2067)]
    [InlineData(0x2068)]
    [InlineData(0x2069)]
    public void ABidirectionalControlSteersATerminal(int codePoint)
    {
        Assert.True(Esj.IsBidiControl(codePoint), Hex(codePoint));
        AssertSteersAndIsEscaped(codePoint);
    }

    /// <summary>
    /// The neighbours of every range, and characters that look like candidates and are not, say
    /// something and are left alone.
    /// </summary>
    /// <param name="codePoint">the character</param>
    [Theory]
    [InlineData(0x20)]
    [InlineData(0x41)]
    [InlineData(0x7E)]
    [InlineData(0xA0)]
    [InlineData(0xA9)]
    [InlineData(0xFC)]
    [InlineData(0x061B)]
    [InlineData(0x061D)]
    [InlineData(0x0628)]
    [InlineData(0x200B)]
    [InlineData(0x200C)]
    [InlineData(0x200D)]
    [InlineData(0x2027)]
    [InlineData(0x202F)]
    [InlineData(0x2065)]
    [InlineData(0x206A)]
    [InlineData(0x20AC)]
    [InlineData(0xFEFF)]
    [InlineData(0x1F600)]
    public void ACharacterThatSaysSomethingIsLeftAlone(int codePoint)
    {
        Assert.False(Esj.SteersATerminal(codePoint), Hex(codePoint));
        Assert.False(Esj.IsBidiControl(codePoint), Hex(codePoint));
        string text = "a" + char.ConvertFromUtf32(codePoint) + "b";
        Assert.Equal(text, Esj.ForMessage(text));
        Assert.Equal(text, Esj.ForSubject(text));
    }

    /// <summary>The three whitespace controls belong to the set and are written by name.</summary>
    [Fact]
    public void TheThreeWhitespaceControlsAreWrittenByName()
    {
        Assert.True(Esj.SteersATerminal('\t'));
        Assert.Equal("a\\tb", Esj.ForSubject("a\tb"));
        Assert.Equal("a\\nb", Esj.ForSubject("a\nb"));
        Assert.Equal("a\\rb", Esj.ForSubject("a\rb"));
        Assert.Equal("a\\\"b\\\\c", Esj.ForSubject("a\"b\\c"));
    }

    private static void AssertSteersAndIsEscaped(int codePoint)
    {
        Assert.True(Esj.SteersATerminal(codePoint), Hex(codePoint));
        string fragment = "RE-" + char.ConvertFromUtf32(codePoint) + "1";
        string escaped = "RE-\\u" + codePoint.ToString("x4", CultureInfo.InvariantCulture) + "1";
        Assert.Equal(escaped, Esj.ForMessage(fragment));
        Assert.Equal(escaped, Esj.ForSubject(fragment));
    }

    private static string Hex(int codePoint) =>
        "U+" + codePoint.ToString("X4", CultureInfo.InvariantCulture);
}
