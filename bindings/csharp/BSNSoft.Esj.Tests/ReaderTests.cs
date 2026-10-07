using System;
using System.Linq;
using System.Text;
using BSNSoft.Esj.Json;
using BSNSoft.Esj.Validation;
using Xunit;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// What layer L1 decides about bytes, which the fixture manifest cannot state because it is
/// written over documents and not over byte sequences: the encoding, a byte order mark, a
/// duplicate member name, and the two shapes a reader may answer in.
/// </summary>
public class ReaderTests
{
    private const string Minimal = "examples/minimal.esj.json";

    /// <summary>A byte order mark is not whitespace and not part of a JSON text.</summary>
    [Fact]
    public void ItRefusesAByteOrderMark()
    {
        byte[] document = Fixtures.Bytes(Minimal);
        byte[] marked = new byte[] { 0xEF, 0xBB, 0xBF }.Concat(document).ToArray();

        EsjFormatException refused = Assert.Throws<EsjFormatException>(() => EsjReader.Strict().Read(marked));
        Assert.Equal("ESJ-L1-ENCODING", refused.Code);
    }

    /// <summary>A byte sequence that is not UTF-8 is refused before it is parsed.</summary>
    [Fact]
    public void ItRefusesWhatIsNotUtf8()
    {
        byte[] document = Fixtures.Bytes(Minimal);
        int at = Array.IndexOf(document, (byte)'E', 200);
        document[at] = 0xC3;

        EsjFormatException refused = Assert.Throws<EsjFormatException>(() => EsjReader.Strict().Read(document));
        Assert.Equal("ESJ-L1-ENCODING", refused.Code);
    }

    /// <summary>A member name that occurs twice is refused rather than resolved.</summary>
    [Fact]
    public void ItRefusesADuplicateMemberName()
    {
        string document = Fixtures.Text(Minimal)
            .Replace("\"/BT-3\": \"380\"", "\"/BT-3\": \"380\",\n    \"/BT-3\": \"381\"", StringComparison.Ordinal);

        EsjFormatException refused = Assert.Throws<EsjFormatException>(
            () => EsjReader.Strict().Read(Encoding.UTF8.GetBytes(document)));
        Assert.Equal("ESJ-L1-DUPLICATE-MEMBER", refused.Code);
    }

    /// <summary>Content after the object that closes a document is not a JSON text.</summary>
    [Fact]
    public void ItRefusesContentAfterTheDocument()
    {
        byte[] document = Encoding.UTF8.GetBytes(Fixtures.Text(Minimal) + "{}");

        EsjFormatException refused = Assert.Throws<EsjFormatException>(() => EsjReader.Strict().Read(document));
        Assert.Equal("ESJ-L1-JSON", refused.Code);
    }

    /// <summary>
    /// Reporting and rejecting are the same check: a reader asked to report carries the code
    /// in a finding, and one asked to reject carries it in the exception.
    /// </summary>
    [Fact]
    public void ItReportsWhatItWouldOtherwiseThrow()
    {
        byte[] document = Fixtures.Bytes("examples/invalid/empty-string-value.esj.json");

        ReadResult reported = EsjReader.Strict().ReadWithFindings(document);
        EsjFormatException thrown = Assert.Throws<EsjFormatException>(() => EsjReader.Strict().Read(document));

        Assert.Contains(reported.Findings, finding => finding.Code.Code == thrown.Code);
        Assert.False(reported.IsWellFormed);
        Assert.Equal(ValidationStatus.Invalid, reported.Validation().Status);
    }

    /// <summary>
    /// A limit is a policy of the reader and not a property of the document: the same bytes
    /// are refused by one configuration and read by another, and the refusal says so.
    /// </summary>
    [Fact]
    public void ALimitIsAPropertyOfTheReader()
    {
        byte[] document = Fixtures.Bytes(Minimal);
        Limits narrow = Limits.Defaults.ToBuilder().MaxValues(3).Build();

        EsjLimitException refused = Assert.Throws<EsjLimitException>(
            () => EsjReader.WithLimits(narrow).Read(document));
        Assert.Contains("3", refused.Message, StringComparison.Ordinal);

        ReadResult reported = EsjReader.WithLimits(narrow).ReadWithFindings(document);
        Assert.Equal(ValidationStatus.Indeterminate, reported.Validation().Status);
        Assert.Equal(
            NotEvaluatedReason.Limit,
            reported.Validation().NotEvaluated[ValidationLayer.L2]);

        Assert.NotNull(EsjReader.Strict().Read(document));
    }

    /// <summary>
    /// A document read and written in the pretty form of section 7.7 is the file it came
    /// from, which is what makes that form a serialization two implementations agree on.
    /// </summary>
    /// <param name="name">the example</param>
    [Theory]
    [InlineData("examples/minimal.esj.json")]
    [InlineData("examples/allowances.esj.json")]
    [InlineData("examples/multiple-lines.esj.json")]
    [InlineData("examples/standard-invoice.esj.json")]
    public void ItWritesThePrettyFormTheExamplesAreStoredIn(string name)
    {
        SemanticDocument document = EsjReader.Strict().Read(Fixtures.Bytes(name));
        Assert.Equal(Fixtures.Text(name), EsjWriter.Pretty().ToText(document));
    }

    /// <summary>
    /// A number inside <c>extensions</c> keeps every digit the sender wrote, because the
    /// canonical form of section 7.6 is taken from its spelling and never from a binary
    /// floating point value.
    /// </summary>
    [Theory]
    [InlineData("1e21", "1000000000000000000000")]
    [InlineData("1e-6", "0.000001")]
    [InlineData("-0.0", "0")]
    [InlineData("1.0000000000000001", "1.0000000000000001")]
    [InlineData("12345678901234567890", "12345678901234567890")]
    public void ANumberInsideExtensionsKeepsItsDigits(string written, string canonical)
    {
        string document = Fixtures.Text("examples/minimal.esj.json").TrimEnd();
        document = document.Substring(0, document.Length - 1)
            + ",\n  \"extensions\": {\"de.example.vendor\": {\"n\": " + written + "}}\n}";

        SemanticDocument read = EsjReader.Strict().Read(Encoding.UTF8.GetBytes(document));
        Assert.Contains(
            "\"n\":" + canonical,
            EsjWriter.Canonical().ToText(read),
            StringComparison.Ordinal);
    }

    private const string Head = "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
        + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",";

    private static string WithValue(string json) => Head + "\"values\":{\"/BT-1\":" + json + "}}";

    private static string WithExtension(string json) => Head + "\"values\":{\"/BT-1\":\"X\"},"
        + "\"extensions\":{\"de.example.vendor\":" + json + "}}";

    private static string[] Pairs(string document, Limits? limits = null) =>
        EsjReader.WithLimits(limits ?? Limits.Defaults).ReadWithFindings(Encoding.UTF8.GetBytes(document))
            .Findings.Where(finding => finding.IsError)
            .Select(finding => finding.Path + " " + finding.Code.Code).ToArray();

    /// <summary>
    /// The length of the canonical form of a number is computed before a digit of it is
    /// written, so a spelling whose plain form would run to millions of digits costs nothing.
    /// </summary>
    [Theory]
    [InlineData("1e999999999")]
    [InlineData("1e-999999999")]
    [InlineData("1e99999999999999999999")]
    [InlineData("1e-99999999999999999999")]
    public void ItRefusesANumberWhoseCanonicalFormIsTooLongBeforeWritingIt(string spelling)
    {
        Assert.Null(Decimals.Canonicalize(spelling));
        Assert.Equal(new[] { " ESJ-L1-EXT-NUMBER" }, Pairs(WithExtension("[" + spelling + "]")));
    }

    /// <summary>A long spelling with a short canonical form is a number like any other.</summary>
    [Fact]
    public void ItKeepsALongSpellingWithAShortCanonicalForm()
    {
        Assert.Equal("0", Decimals.Canonicalize("0e999999999"));
        Assert.Equal("1", Decimals.Canonicalize("0." + new string('0', 1000) + "1e1001"));
        Assert.Equal("1", Decimals.Canonicalize("1" + new string('0', 1000) + "e-1000"));
        Assert.Empty(Pairs(WithExtension("[0e999999999,-0.0e-5]")));
    }

    /// <summary>
    /// An envelope member of the wrong JSON type is refused at its first token: nothing after
    /// it is read, so a structure nested past the bound there is not a limit.
    /// </summary>
    [Fact]
    public void ItRefusesAnEnvelopeMemberAtItsFirstToken()
    {
        string nested = new string('[', 40) + new string(']', 40);
        Assert.Equal(new[] { " ESJ-L1-ENVELOPE-VALUE" }, Pairs(Head + "\"values\":" + nested + "}"));
        Assert.Equal(new[] { " ESJ-L1-ENVELOPE-VALUE" },
            Pairs(WithValue("\"X\"").TrimEnd('}') + "},\"source\":{\"syntax\":" + nested + "}}"));
    }

    /// <summary>
    /// A number token is held to the string bound wherever it stands, and a member name to the
    /// larger of the string bound and the path bound: the answer the reference reader gives.
    /// </summary>
    [Fact]
    public void ItBoundsANumberTokenAndAMemberNameWhereverTheyStand()
    {
        Assert.Equal(new[] { "/BT-1 ESJ-L1-LIMIT" }, Pairs(WithValue(new string('1', (1024 * 1024) + 1))));
        Assert.Equal(new[] { "/BT-1 ESJ-L1-JSON-TYPE" }, Pairs(WithValue(new string('1', 1024 * 1024))));
        Assert.Equal(new[] { "/BT-1 ESJ-L1-LIMIT" },
            Pairs(WithValue("{\"" + new string('a', (1024 * 1024) + 1) + "\":\"x\",\"scheme\":\"y\"}")));
    }

    /// <summary>
    /// A nesting bound a caller raised is answered with a document and with canonical bytes:
    /// the reader and the writer keep the containers they have opened on a stack of their own.
    /// </summary>
    [Fact]
    public void ItReadsAndWritesANestingBoundACallerRaised()
    {
        const int levels = 60_000;
        string nested = new string('[', levels) + "0" + new string(']', levels);
        Limits raised = Limits.Defaults.ToBuilder().MaxExtensionDepth(levels + 10).Build();

        SemanticDocument document = EsjReader.WithLimits(raised).Read(Encoding.UTF8.GetBytes(WithExtension(nested)));

        Assert.Contains(nested, Encoding.UTF8.GetString(Canonicalizer.CanonicalBytes(document)), StringComparison.Ordinal);
        Assert.Equal(new[] { "/BT-1 ESJ-L1-JSON-TYPE" }, Pairs(WithValue(nested), raised));
        Assert.Equal(new[] { " ESJ-L1-LIMIT" }, Pairs(WithExtension(nested)));
    }
}
