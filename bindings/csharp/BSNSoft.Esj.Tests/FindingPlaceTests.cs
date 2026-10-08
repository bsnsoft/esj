using System;
using System.Linq;
using System.Text;
using BSNSoft.Esj.Json;
using BSNSoft.Esj.Validation;
using Xunit;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// What layer L1 reports and where it says it found it: the code, the path and the subject of
/// every finding the reader makes, over the probes of the specification audit of 8 October 2026
/// and the decisions taken on them. A subject is a member access from the root of the document,
/// with the names the specification defines after a dot and every name the document chose in
/// brackets, escaped and whole (specification, section 9.5).
/// </summary>
public class FindingPlaceTests
{
    private const string Head = "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
        + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",";

    private static string Values(string members) => Head + "\"values\":{" + members + "}}";

    private static string WithExtensions(string members) =>
        Head + "\"values\":{\"/BT-1\":\"X\"},\"extensions\":{" + members + "}}";

    private static string WithSource(string members) =>
        Head + "\"values\":{\"/BT-1\":\"X\"},\"source\":{" + members + "}}";

    private static (string Path, string Code, string Subject)[] Read(string json, Limits? limits = null) =>
        Findings(EsjReader.WithLimits(limits ?? Limits.Defaults).ReadWithFindings(Encoding.UTF8.GetBytes(json)));

    private static (string Path, string Code, string Subject)[] Findings(ReadResult read) =>
        read.Findings.Select(finding => (finding.Path.Text, finding.Code.Code, finding.Subject)).ToArray();

    private static Limits Strings(long bytes) => Limits.Defaults.ToBuilder().MaxStringBytes(bytes).Build();

    // ------------------------------------------------------------ D2: line endings

    /// <summary>
    /// A value built through the API is normalized the way a value read from a document is, so the
    /// two are one value with one canonical form and one digest (can-01).
    /// </summary>
    [Fact]
    public void AValueBuiltThroughTheApiNormalizesItsLineEndings()
    {
        SemanticValue built = new("a\r\nb\rc", "s\r\nx");
        Assert.Equal("a\nb\nc", built.Content);
        Assert.Equal("s\nx", built.Scheme);

        SemanticDocument read = EsjReader.Strict().Read(Encoding.UTF8.GetBytes(Values("\"/BT-22/0\":\"a\\r\\nb\\rc\"")));
        SemanticDocument made = EsjReader.Strict().Read(Encoding.UTF8.GetBytes(Values("\"/BT-22/0\":\"z\"")))
            .With(SemanticPath.Parse("/BT-22/0"), new SemanticValue("a\r\nb\rc"));

        Assert.Equal(Canonicalizer.DocumentDigest(read), Canonicalizer.DocumentDigest(made));
        Assert.Contains("\"a\\nb\\nc\"", EsjWriter.Canonical().ToText(made), StringComparison.Ordinal);
    }

    /// <summary>
    /// A string of <c>source</c> or of <c>extensions</c> is never normalized, and its bound is
    /// measured on the string as it stands (env-16).
    /// </summary>
    [Fact]
    public void SourceAndExtensionStringsKeepTheirLineEndingsAndAreMeasuredRaw()
    {
        Assert.Empty(Read(WithSource("\"syntax\":\"ab\\r\\n\""), Strings(64)));
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", "source.syntax") },
            Read(Head + "\"values\":{\"/BT-1\":\"X\"},\"source\":{\"syntax\":\"" + new string('a', 62) + "\\r\\n\"}}", Strings(63)));

        SemanticDocument document = EsjReader.Strict().Read(Encoding.UTF8.GetBytes(
            WithExtensions("\"de.example\":\"a\\r\\nb\"").TrimEnd('}') + "},\"source\":{\"syntax\":\"U\\r\\nBL\"}}"));
        string canonical = EsjWriter.Canonical().ToText(document);
        Assert.Contains("\"a\\r\\nb\"", canonical, StringComparison.Ordinal);
        Assert.Contains("\"U\\r\\nBL\"", canonical, StringComparison.Ordinal);
    }

    /// <summary>
    /// A value of <c>values</c> is held to the string bound after normalization: CR LF counts as
    /// one byte, so a value that fits once normalized is not refused for its raw length.
    /// </summary>
    [Fact]
    public void AValueIsMeasuredAfterNormalization()
    {
        Limits limits = Strings(64);
        string fits = new string('a', 62) + "\\r\\n\\r\\n";
        Assert.Empty(Read(Values("\"/BT-22/0\":\"" + fits + "\""), limits));
        Assert.Equal(new[] { ("/BT-22/0", "ESJ-L1-LIMIT", "values[\"/BT-22/0\"]") },
            Read(Values("\"/BT-22/0\":\"" + new string('a', 63) + "\\r\\n\\r\\n\""), limits));
    }

    // ------------------------------------------------------------ D6, D7, D8, D9: limits

    /// <summary>
    /// Every string of the envelope is held to the string bound, whatever it carries, and the
    /// finding names the member (env-06, reg-04).
    /// </summary>
    /// <param name="member">the envelope member</param>
    [Theory]
    [InlineData("format")]
    [InlineData("version")]
    [InlineData("semanticModel")]
    public void AnEnvelopeStringIsHeldToTheStringBound(string member)
    {
        string json = Values("\"/BT-1\":\"X\"").Replace(
            "\"" + member + "\":\"", "\"" + member + "\":\"" + new string('x', 70), StringComparison.Ordinal);
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", member) }, Read(json, Strings(64)));
    }

    /// <summary>
    /// A string written where the envelope wants another JSON type is read to its end and refused
    /// for its type, however long it is.
    /// </summary>
    [Fact]
    public void AnEnvelopeStringOfTheWrongTypeIsRefusedForItsType()
    {
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-VALUE", "values") }, Read(Head + "\"values\":\"" + new string('x', 65) + "\"}", Strings(64)));
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-VALUE", "values") }, Read(Head + "\"values\":\"" + new string('x', 64) + "\"}", Strings(64)));
    }

    /// <summary>A digest of <c>source</c> past the string bound is a limit, not a malformed digest.</summary>
    [Fact]
    public void ASourceDigestPastTheBoundIsALimit()
    {
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", "source.sha256") },
            Read(WithSource("\"sha256\":\"" + new string('a', 65) + "\""), Strings(64)));
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-VALUE", "source.sha256") },
            Read(WithSource("\"sha256\":\"" + new string('a', 63) + "\""), Strings(64)));
    }

    /// <summary>
    /// A member name is held to the string bound in the bytes of its UTF-8 encoding wherever it
    /// stands: 524 289 times <c>ä</c> is 1 048 578 bytes and past the default bound, though it is
    /// fewer UTF-16 code units than that (env-04, env-05, reg-03).
    /// </summary>
    [Fact]
    public void AMemberNameIsMeasuredInUtf8Bytes()
    {
        string name = new('ä', 524_289);
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", "[\"" + name + "\"]") },
            Read(Head + "\"" + name + "\":\"x\",\"values\":{\"/BT-1\":\"X\"}}"));
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", "extensions[\"" + name + "\"]") },
            Read(WithExtensions("\"" + name + "\":1")));
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", "values[\"" + name + "\"]") },
            Read(Values("\"" + name + "\":\"X\"")));
    }

    /// <summary>
    /// Inside the string bound the grammar bounds of a name apply with their own codes: an owner
    /// token of 129 characters is not an owner token, and a path past the path bound is a limit
    /// (section 12.2).
    /// </summary>
    [Fact]
    public void GrammarBoundsApplyInsideTheNameBound()
    {
        string token = "a" + new string('b', 128);
        Assert.Equal(new[] { ("", "ESJ-L1-OWNER-TOKEN", "extensions[\"" + token + "\"]") },
            Read(WithExtensions("\"" + token + "\":1")));
        string path = "/BT-1" + new string('0', 252);
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", "values[\"" + path + "\"]") }, Read(Values("\"" + path + "\":\"X\"")));
    }

    /// <summary>
    /// A number token longer than the string bound is a limit wherever it stands, before it is
    /// built: as a member of <c>values</c>, in a value object, inside <c>extensions</c> and as an
    /// envelope member of the wrong type (reg-01, reg-02, vld-18).
    /// </summary>
    [Fact]
    public void ANumberTokenPastTheStringBoundIsALimitEverywhere()
    {
        Limits limits = Strings(32);
        string fraction = "1." + new string('0', 31);
        string integer = new string('1', 33);
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-LIMIT", "values[\"/BT-1\"]") }, Read(Values("\"/BT-1\":" + fraction), limits));
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-LIMIT", "values[\"/BT-1\"]") }, Read(Values("\"/BT-1\":" + integer), limits));
        Assert.Equal(new[] { ("/BG-4/BT-29/0", "ESJ-L1-LIMIT", "values[\"/BG-4/BT-29/0\"].value") },
            Read(Values("\"/BG-4/BT-29/0\":{\"value\":" + integer + ",\"scheme\":\"0088\"}"), limits));
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", "extensions[\"de.example\"][\"o\"]") },
            Read(WithExtensions("\"de.example\":{\"o\":" + integer + "}"), limits));
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", "extensions[\"de.example\"][1]") },
            Read(WithExtensions("\"de.example\":[1," + fraction + "]"), limits));
        Assert.Equal(new[] { ("", "ESJ-L1-LIMIT", "format") },
            Read(Values("\"/BT-1\":\"X\"").Replace("\"format\":\"EN16931-Semantic-JSON\"", "\"format\":" + integer, StringComparison.Ordinal), limits));
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-JSON-TYPE", "values[\"/BT-1\"]") },
            Read(Values("\"/BT-1\":" + new string('1', 32)), limits));
    }

    /// <summary>
    /// A limit met inside a member of <c>values</c> carries that member's path, and a limit met in
    /// a subtree the reader walks past names the member walked past: the member of <c>values</c>,
    /// or the member of its value object (vld-18).
    /// </summary>
    [Fact]
    public void ALimitInsideAValueCarriesThePathAndTheMember()
    {
        string nested = new string('[', 33) + new string(']', 33);
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-LIMIT", "values[\"/BT-1\"]") }, Read(Values("\"/BT-1\":" + nested)));
        string inObject = new string('[', 32) + new string(']', 32);
        Assert.Equal(new[] { ("/BG-4/BT-29/0", "ESJ-L1-LIMIT", "values[\"/BG-4/BT-29/0\"].value") },
            Read(Values("\"/BG-4/BT-29/0\":{\"value\":" + inObject + ",\"scheme\":\"0088\"}")));
        Assert.Equal(new[] { ("/BG-4/BT-29/0", "ESJ-L1-JSON-TYPE", "values[\"/BG-4/BT-29/0\"].value") },
            Read(Values("\"/BG-4/BT-29/0\":{\"value\":" + inObject.Substring(1, 62) + ",\"scheme\":\"0088\"}")));
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-LIMIT", "values[\"/BT-1\"]") },
            Read(Values("\"/BT-1\":[{\"" + new string('a', 65) + "\":1}]"), Strings(64)));
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-LIMIT", "values[\"/BT-1\"]") },
            Read(Values("\"/BT-1\":\"" + new string('x', 65) + "\""), Strings(64)));
    }

    /// <summary>
    /// A string in a subtree the reader walks past is held to well-formedness and to no string
    /// bound: the member it stands in has its code already.
    /// </summary>
    [Fact]
    public void AStringWalkedPastIsNotMeasured() =>
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-JSON-TYPE", "values[\"/BT-1\"]") },
            Read(Values("\"/BT-1\":[\"" + new string('x', 200) + "\"]"), Strings(64)));

    /// <summary>
    /// The checks of one string are ordered: a lone surrogate before the bound before the value
    /// in the envelope, a lone surrogate before the bound inside <c>values</c> wherever the guard
    /// of twice the bound let the reader read the string whole.
    /// </summary>
    [Fact]
    public void ASurrogateIsJudgedBeforeTheBoundOfItsString()
    {
        Limits limits = Strings(64);
        Assert.Equal(new[] { ("", "ESJ-L1-SURROGATE", "version") },
            Read(Values("\"/BT-1\":\"X\"").Replace("\"version\":\"0.1\"", "\"version\":\"\\ud800" + new string('x', 70) + "\"", StringComparison.Ordinal), limits));
        Assert.Equal(new[] { ("", "ESJ-L1-SURROGATE", "source.syntax") },
            Read(WithSource("\"syntax\":\"\\ud800" + new string('x', 70) + "\""), limits));
        Assert.Equal(new[] { ("/BT-22/0", "ESJ-L1-SURROGATE", "values[\"/BT-22/0\"]") },
            Read(Values("\"/BT-22/0\":\"\\ud800" + new string('x', 70) + "\""), limits));
        Assert.Equal(new[] { ("/BT-22/0", "ESJ-L1-LIMIT", "values[\"/BT-22/0\"]") },
            Read(Values("\"/BT-22/0\":\"\\ud800" + new string('x', 130) + "\""), limits));
        Assert.Equal(new[] { ("", "ESJ-L1-SURROGATE", "extensions[\"de.example\"]") },
            Read(WithExtensions("\"de.example\":\"\\ud800" + new string('x', 70) + "\""), limits));
    }

    // ------------------------------------------------------------ D10: offsets

    /// <summary>
    /// A message names the byte, counted from zero, at which the token it is about begins, in
    /// bytes of the encoded document and not in characters (vld-17).
    /// </summary>
    [Fact]
    public void AMessageNamesTheByteTheTokenBeginsAt()
    {
        string json = Values("\"/BT-22/0\":\"" + new string('ä', 200) + "\",\"/BT-1\":tru");
        int at = Encoding.UTF8.GetBytes(json).Length - "tru}}".Length;
        Finding finding = Assert.Single(EsjReader.Strict().ReadWithFindings(Encoding.UTF8.GetBytes(json)).Findings);
        Assert.Equal("ESJ-L1-JSON", finding.Code.Code);
        Assert.Contains("(at byte " + at + ")", finding.Message, StringComparison.Ordinal);

        string name = new('a', 70);
        string limited = Values("\"/BT-22/0\":\"" + new string('ä', 200) + "\",\"" + name + "\":\"x\"");
        int start = Encoding.UTF8.GetBytes(limited).Length - ("\"" + name + "\":\"x\"}}").Length;
        Finding limit = Assert.Single(EsjReader.WithLimits(Limits.Defaults.ToBuilder().MaxStringBytes(450).Build())
            .ReadWithFindings(Encoding.UTF8.GetBytes(limited.Replace(name, new string('a', 451), StringComparison.Ordinal))).Findings);
        Assert.Equal("ESJ-L1-LIMIT", limit.Code.Code);
        Assert.Contains("byte " + start + ")", limit.Message, StringComparison.Ordinal);
    }

    // ------------------------------------------------------------ D11, D12

    /// <summary>
    /// The decoded length of base64 content is three bytes a group of four, less the padding at
    /// the end counted at most twice, and never less than nothing (reg-06).
    /// </summary>
    /// <param name="content">the content</param>
    /// <param name="decoded">the bytes it stands for</param>
    [Theory]
    [InlineData("", 0)]
    [InlineData("=", 0)]
    [InlineData("==", 0)]
    [InlineData("====", 1)]
    [InlineData("========", 4)]
    [InlineData("Q===", 1)]
    [InlineData("QQ==", 1)]
    [InlineData("QUI=", 2)]
    [InlineData("QUJD", 3)]
    public void TheDecodedLengthCountsAtMostTwoPaddingCharacters(string content, long decoded) =>
        Assert.Equal(decoded, Texts.DecodedBase64Length(content));

    /// <summary>
    /// Content that is all padding counts towards the total binary bound like any other, so a
    /// lowered bound is reached and reported as a limit (reg-06).
    /// </summary>
    [Fact]
    public void PaddingDoesNotSubtractFromTheBinaryTotal()
    {
        Limits limits = Limits.Defaults.ToBuilder().MaxTotalBinaryBytes(5).Build();
        string json = Values("\"/BG-24/0/BT-122\":\"A\",\"/BG-24/0/BT-125\":{\"value\":\"========\",\"mimeCode\":\"text/plain\",\"filename\":\"a\"},"
            + "\"/BG-24/1/BT-122\":\"B\",\"/BG-24/1/BT-125\":{\"value\":\"QUJD\",\"mimeCode\":\"text/plain\",\"filename\":\"b\"}");
        Assert.Equal(new[] { ("/BG-24/1/BT-125", "ESJ-L1-LIMIT", "values[\"/BG-24/1/BT-125\"]") }, Read(json, limits));
    }

    /// <summary>
    /// A bound the reader would have to enlarge past what an integer holds is refused when it is
    /// given, with its name, and the largest bound accepted reads a document (reg-13).
    /// </summary>
    [Fact]
    public void ABoundThatWouldOverflowIsRefusedWhenItIsGiven()
    {
        ArgumentOutOfRangeException depth = Assert.Throws<ArgumentOutOfRangeException>(
            () => Limits.Defaults.ToBuilder().MaxExtensionDepth(int.MaxValue).Build());
        Assert.Contains("maxExtensionDepth", depth.Message, StringComparison.Ordinal);
        Assert.Throws<ArgumentOutOfRangeException>(
            () => Limits.Defaults.ToBuilder().MaxDocumentBytes(long.MaxValue).Build());

        Limits largest = Limits.Defaults.ToBuilder()
            .MaxExtensionDepth(Limits.MaxExtensionDepthBound)
            .MaxDocumentBytes(Limits.MaxDocumentBytesBound)
            .Build();
        Assert.Empty(Read(Values("\"/BT-2\":\"2026-01-01\""), largest));
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-JSON-TYPE", "values[\"/BT-1\"]") }, Read(Values("\"/BT-1\":[[[[1]]]]"), largest));
    }

    // ------------------------------------------------------------ D13, D14, D15, D16

    /// <summary>
    /// A name the document chose is written in brackets, a name the specification defines after a
    /// dot, so a member of the envelope called <c>source.foo</c> and a member <c>foo</c> of
    /// <c>source</c> are two places (env-13, vld-20).
    /// </summary>
    [Fact]
    public void ADocumentChosenNameIsWrittenInBrackets()
    {
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-MEMBER", "[\"source.foo\"]") },
            Read(Head + "\"source.foo\":1,\"values\":{\"/BT-1\":\"X\"}}"));
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-MEMBER", "source[\"foo\"]") }, Read(WithSource("\"foo\":1")));
        Assert.Equal(new[] { ("/BG-4/BT-29/0", "ESJ-L1-VALUE-MEMBER", "values[\"/BG-4/BT-29/0\"][\"foo\"]") },
            Read(Values("\"/BG-4/BT-29/0\":{\"value\":\"X\",\"scheme\":\"0088\",\"foo\":\"y\"}")));
        Assert.Equal(new[] { ("/BG-4/BT-29/0", "ESJ-L1-EMPTY-STRING", "values[\"/BG-4/BT-29/0\"].scheme") },
            Read(Values("\"/BG-4/BT-29/0\":{\"value\":\"X\",\"scheme\":\"\"}")));
    }

    /// <summary>
    /// A duplicate names the member whose name occurs twice, at its second occurrence, in every
    /// object of the document (vld-01, vld-02, env-07, env-08).
    /// </summary>
    [Fact]
    public void ADuplicateNamesTheMemberThatOccursTwice()
    {
        Assert.Equal(new[] { ("", "ESJ-L1-DUPLICATE-MEMBER", "values[\"/BT-1\"]") },
            Read(Values("\"/BT-1\":\"X\",\"/BT-1\":\"Y\"")));
        Assert.Equal(new[] { ("", "ESJ-L1-DUPLICATE-MEMBER", "format") },
            Read(Head.Replace("\"version\"", "\"format\":\"EN16931-Semantic-JSON\",\"version\"", StringComparison.Ordinal)
                + "\"values\":{\"/BT-1\":\"X\"}}"));
        Assert.Equal(new[] { ("", "ESJ-L1-DUPLICATE-MEMBER", "source.syntax") },
            Read(WithSource("\"syntax\":\"UBL\",\"syntax\":\"CII\"")));
        Assert.Equal(new[] { ("", "ESJ-L1-DUPLICATE-MEMBER", "extensions[\"a.b\"]") },
            Read(WithExtensions("\"a.b\":1,\"a.b\":2")));
        Assert.Equal(new[] { ("", "ESJ-L1-DUPLICATE-MEMBER", "extensions[\"a.b\"][\"x\"]") },
            Read(WithExtensions("\"a.b\":{\"x\":1,\"x\":2}")));
        Assert.Equal(new[] { ("/BG-4/BT-29/0", "ESJ-L1-DUPLICATE-MEMBER", "values[\"/BG-4/BT-29/0\"]") },
            Read(Values("\"/BG-4/BT-29/0\":{\"value\":\"X\",\"scheme\":\"0088\",\"scheme\":\"0060\"}")));
    }

    /// <summary>
    /// Every required envelope member a document lacks is one finding with its name as the subject,
    /// in a fixed order (vld-04, env-09).
    /// </summary>
    [Fact]
    public void EveryMissingEnvelopeMemberIsOneFinding()
    {
        Assert.Equal(
            new[]
            {
                ("", "ESJ-L1-ENVELOPE-MEMBER", "format"),
                ("", "ESJ-L1-ENVELOPE-MEMBER", "version"),
                ("", "ESJ-L1-ENVELOPE-MEMBER", "semanticModel"),
            },
            Read("{\"values\":{\"/BT-1\":\"X\"}}"));
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-MEMBER", "values") }, Read(Head.TrimEnd(',') + "}"));
        Assert.Equal(4, Read("{}").Length);

        EsjFormatException first = Assert.Throws<EsjFormatException>(() => EsjReader.Strict().Read(Encoding.UTF8.GetBytes("{}")));
        Assert.Equal("format", first.Subject);
    }

    /// <summary>
    /// The subject of an undefined envelope member carries the name whole, however long; only the
    /// message holds it to an excerpt (vld-03, env-10, reg-05).
    /// </summary>
    [Fact]
    public void ASubjectIsNeverShortened()
    {
        string name = "profile-" + new string('x', 120);
        (string Path, string Code, string Subject)[] found = Read(Head + "\"" + name + "\":1,\"values\":{\"/BT-1\":\"X\"}}");
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-MEMBER", "[\"" + name + "\"]") }, found);
    }

    // ------------------------------------------------------------ D17: surrogates

    /// <summary>
    /// A lone surrogate is escaped as <c>\u</c> and four lowercase hexadecimal digits, and a pair is
    /// left alone (vld-05).
    /// </summary>
    [Fact]
    public void ALoneSurrogateIsEscaped()
    {
        Assert.Equal("a\\ud800b", Esj.ForSubject("a\ud800b"));
        Assert.Equal("a\\udfffb", Esj.ForMessage("a\udfffb"));
        Assert.Equal("\\udc00\\ud800", Esj.ForSubject("\udc00\ud800"));
        Assert.Equal("a\U0001F600b", Esj.ForSubject("a\U0001F600b"));
    }

    /// <summary>
    /// A member name with a lone surrogate names its member with the surrogate escaped, in every
    /// object, and inside a value object as a duplicate there does (vld-05, vld-06, env-11, K2).
    /// </summary>
    [Fact]
    public void ANameWithALoneSurrogateIsNamedEscaped()
    {
        Assert.Equal(new[] { ("", "ESJ-L1-SURROGATE", "[\"\\ud800\"]") },
            Read(Head + "\"\\ud800\":\"y\",\"values\":{\"/BT-1\":\"X\"}}"));
        Assert.Equal(new[] { ("", "ESJ-L1-SURROGATE", "values[\"/BT-9\\ud800\"]") },
            Read(Values("\"/BT-9\\ud800\":\"X\"")));
        Assert.Equal(new[] { ("", "ESJ-L1-SURROGATE", "source[\"\\ud800\"]") }, Read(WithSource("\"\\ud800\":\"UBL\"")));
        Assert.Equal(new[] { ("", "ESJ-L1-SURROGATE", "extensions[\"\\ud800\"]") }, Read(WithExtensions("\"\\ud800\":1")));
        Assert.Equal(new[] { ("", "ESJ-L1-SURROGATE", "extensions[\"o\"][\"\\udc00\"]") },
            Read(WithExtensions("\"o\":{\"\\udc00\":1}")));
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-SURROGATE", "values[\"/BT-1\"]") },
            Read(Values("\"/BT-1\":{\"value\":\"A\",\"\\ud800\":\"y\"}")));
    }

    // ------------------------------------------------------------ D18, D19, D20

    /// <summary>
    /// A token that is no complete JSON value is <c>ESJ-L1-JSON</c> wherever it stands, also as
    /// the value of an envelope member, and a finding about the document: no path, no subject
    /// (vld-07, env-02, env-12).
    /// </summary>
    /// <param name="json">the document</param>
    [Theory]
    [InlineData("{\"format\":tru,\"version\":\"0.1\"}")]
    [InlineData("{\"format\":01}")]
    [InlineData("{\"format\":1.}")]
    [InlineData("{\"format\":-}")]
    [InlineData(Head + "\"values\":tru}")]
    [InlineData(Head + "\"values\":{\"/BT-1\":01}}")]
    [InlineData(Head + "\"values\":{\"/BT-1\":\"X\" \"/BT-2\":\"Y\"}}")]
    [InlineData(Head + "\"values\":{\"/BT-1\":\"X\",}}")]
    [InlineData(Head + "\"values\":{\"/BT-1\":\"X\"},\"extensions\":{\"o\":[1,]}}")]
    [InlineData(Head + "\"values\":{\"/BT-1\":\"X\"},\"source\":{\"syntax\":tru}}")]
    [InlineData(Head + "\"values\":{\"/BT-1\":\"X\"")]
    [InlineData(Head + "\"values\":{\"/BT-1\":truex}}")]
    [InlineData(Head + "\"values\":{\"/BT-1\":nul_}}")]
    [InlineData("{\"format\":false$}")]
    public void ATokenThatIsNoJsonValueIsAFindingAboutTheDocument(string json) =>
        Assert.Equal(new[] { ("", "ESJ-L1-JSON", "") }, Read(json));

    /// <summary>
    /// A complete token of the wrong JSON type is still the type finding, read whole before it is
    /// judged.
    /// </summary>
    [Fact]
    public void ACompleteTokenOfTheWrongTypeIsTheTypeFinding()
    {
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-VALUE", "format") }, Read("{\"format\":true}"));
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-VALUE", "values") }, Read(Head + "\"values\":1.5}"));
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-JSON-TYPE", "values[\"/BT-1\"]") }, Read(Values("\"/BT-1\":1")));
    }

    /// <summary>
    /// A defect of a member name is decided before anything about the value written under it:
    /// the text reaches the name first (env-03, K4).
    /// </summary>
    [Fact]
    public void TheNameIsJudgedBeforeItsValue()
    {
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-MEMBER", "[\"foo\"]") }, Read("{\"foo\":tru}"));
        Assert.Equal(new[] { ("", "ESJ-L1-DUPLICATE-MEMBER", "format") },
            Read("{\"format\":\"EN16931-Semantic-JSON\",\"format\":tru}"));
        Assert.Equal(new[] { ("", "ESJ-L1-DUPLICATE-MEMBER", "values[\"/BT-1\"]") },
            Read(Values("\"/BT-1\":\"X\",\"/BT-1\":tru")));
        Assert.Equal(new[] { ("", "ESJ-L1-OWNER-TOKEN", "extensions[\"a b\"]") }, Read(WithExtensions("\"a b\":tru")));
    }

    /// <summary>
    /// A subtree the reader walks past is held to well-formedness and to the bounds, and to nothing
    /// else: a lone surrogate or a repeated name in it draws nothing, and the reader reads on
    /// (vld-08, vld-09, env-24).
    /// </summary>
    [Fact]
    public void AWalkedPastSubtreeIsHeldToWellFormednessAlone()
    {
        Assert.Equal(new[] { ("/BT-1", "ESJ-L1-JSON-TYPE", "values[\"/BT-1\"]"), ("", "ESJ-L1-JSON", "") },
            Read(Values("\"/BT-1\":[\"\\ud800\"],\"/BT-2\":01")));
        Assert.Equal(
            new[] { ("/BT-1", "ESJ-L1-JSON-TYPE", "values[\"/BT-1\"]"), ("/BT-2", "ESJ-L1-EMPTY-STRING", "values[\"/BT-2\"]") },
            Read(Values("\"/BT-1\":[{\"a\":\"\\ud800\",\"a\":2}],\"/BT-2\":\"\"")));
        Assert.Equal(new[] { ("", "ESJ-L1-JSON", "") }, Read(Values("\"/BT-1\":[\"\\ud800\\x\"]")));
        Assert.Equal(new[] { ("", "ESJ-L1-JSON", "") }, Read(Values("\"/BT-1\":\"\\ud800\\x\"")));
        Assert.Equal(new[] { ("", "ESJ-L1-JSON", "") }, Read(Values("\"/BT-1\":{\"value\":tru,\"scheme\":\"x\"}")));
        Assert.Equal(new[] { ("", "ESJ-L1-ENVELOPE-MEMBER", "[\"profile\"]") },
            Read(Head + "\"profile\":\"\\ud800\",\"values\":{\"/BT-1\":\"X\"}}"));
    }

    // ------------------------------------------------------------ D23: exceptions

    /// <summary>
    /// An exception carries the code, the path and the subject the finding of the same defect
    /// carries (vld-19).
    /// </summary>
    [Fact]
    public void AnExceptionCarriesCodePathAndSubject()
    {
        byte[] surrogate = Encoding.UTF8.GetBytes(Values("\"/BG-4/BT-27\":\"A\\ud800\""));
        EsjFormatException refused = Assert.Throws<EsjFormatException>(() => EsjReader.Strict().Read(surrogate));
        Finding reported = Assert.Single(EsjReader.Strict().ReadWithFindings(surrogate).Findings);
        Assert.Equal("ESJ-L1-SURROGATE", refused.Code);
        Assert.Equal("/BG-4/BT-27", refused.Path.Text);
        Assert.Equal("values[\"/BG-4/BT-27\"]", refused.Subject);
        Assert.Equal(reported.Code.Code, refused.Code);
        Assert.Equal(reported.Path, refused.Path);
        Assert.Equal(reported.Subject, refused.Subject);

        byte[] deep = Encoding.UTF8.GetBytes(Values("\"/BT-1\":" + new string('[', 33) + new string(']', 33)));
        EsjLimitException limit = Assert.Throws<EsjLimitException>(() => EsjReader.Strict().Read(deep));
        Assert.Equal("ESJ-L1-LIMIT", limit.Code);
        Assert.Equal("/BT-1", limit.Path.Text);
        Assert.Equal("values[\"/BT-1\"]", limit.Subject);

        EsjFormatException json = Assert.Throws<EsjFormatException>(
            () => EsjReader.Strict().Read(Encoding.UTF8.GetBytes(Values("\"/BT-1\":01"))));
        Assert.Equal("ESJ-L1-JSON", json.Code);
        Assert.True(json.Path.IsRoot);
        Assert.Equal(string.Empty, json.Subject);
    }

    /// <summary>
    /// UTF-8 is the one encoding read: a document in UTF-16 without a byte order mark is valid
    /// UTF-8 of no JSON text, so it is <c>ESJ-L1-JSON</c>; with a mark it is the encoding (env-01).
    /// </summary>
    [Fact]
    public void NoEncodingIsGuessed()
    {
        byte[] utf16 = Encoding.Unicode.GetBytes(Values("\"/BT-1\":\"X\""));
        Assert.Equal(new[] { ("", "ESJ-L1-JSON", "") }, Findings(EsjReader.Strict().ReadWithFindings(utf16)));
        byte[] marked = new byte[] { 0xEF, 0xBB, 0xBF }.Concat(Encoding.UTF8.GetBytes(Values("\"/BT-1\":\"X\""))).ToArray();
        Assert.Equal(new[] { ("", "ESJ-L1-ENCODING", "") }, Findings(EsjReader.Strict().ReadWithFindings(marked)));
    }
}
