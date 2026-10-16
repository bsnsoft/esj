using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using BSNSoft.Esj.Json;
using BSNSoft.Esj.Validation;
using Xunit;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// The format version of a document (specification, section 4.3): a document this binding
/// builds is 1.0, a reader takes 1.0 and 0.1 and nothing else, and a document keeps the version
/// it was read with through canonicalization and every derivation, so that a document of 0.1
/// has the canonical bytes and the digests it always had.
/// </summary>
/// <remarks>
/// The documents under <c>Version01/</c> are fixtures of the repository as they stood before
/// the format became 1.0, with their canonical bytes as the reference implementation wrote
/// them and the digests both implementations produced then. They are copies kept in this
/// binding on purpose: the repository's own fixtures move to 1.0, and these hold the reading of
/// 0.1 to the bytes of that time.
/// </remarks>
public class FormatVersionTests
{
    private const string Pinned = "bindings/csharp/BSNSoft.Esj.Tests/Version01/";

    private static readonly Regex VersionMember = new("\"version\"\\s*:\\s*\"[^\"]*\"", RegexOptions.CultureInvariant);

    /// <summary>
    /// The pinned documents of version 0.1, each with the semantic and the document digest it
    /// had before the format became 1.0.
    /// </summary>
    public static IEnumerable<object[]> PinnedDocuments() => new[]
    {
        new object[]
        {
            "example",
            "27d43ab507a929218e8278be315a736467d9a5f881bf232fe1843b06bd6105b8",
            "fdc75e42190b6aec5cf5454fe07e615af71f33fc275e297cb6aeefec0525e42a",
        },
        new object[]
        {
            "edition-2026-example",
            "8f12f3ea4a038fa8877b9b8084793c0ff4d90190dfe6897b4a44b04d464f2d1c",
            "38b3abe5cfa1d8f705844b7169b56d8aecd35ffbd9ae65ec0aaa83d4c8b7b10f",
        },
        new object[]
        {
            "escaping",
            "0af0a73dedbc14e41ab39b0d92577d4d78613649b6efa13a4e614004ac8d9d68",
            "6b6bb3400996733616c7522388f3e3017bda33b3d840cbc45e912293bfc3dba3",
        },
        new object[]
        {
            "numbers",
            "f364d853b1065a3d582256d93959022cd18da8dad1f5e7edf58ae55fb66b3d88",
            "2c21bec7b7c074f973ee7d854227a7fa5d0599292dae89b9c17f2e9b6bd9891f",
        },
    };

    /// <summary>The version this binding writes is 1.0, and its reader takes 1.0 and 0.1.</summary>
    [Fact]
    public void ThisBindingWritesOneAndReadsTwo()
    {
        Assert.Equal("1.0", Esj.Version);
        Assert.Equal(new[] { "1.0", "0.1" }, Esj.AcceptedVersions);
        Assert.Equal("1.0", Esj.AcceptedVersion("1.0"));
        Assert.Equal("0.1", Esj.AcceptedVersion("0.1"));
        Assert.Null(Esj.AcceptedVersion("1"));
        Assert.Null(Esj.AcceptedVersion("1.1"));
    }

    /// <summary>
    /// A document of version 0.1 is read, keeps that version and writes the canonical bytes and
    /// both digests it had before the format became 1.0.
    /// </summary>
    /// <param name="name">the pinned document</param>
    /// <param name="semanticDigest">its semantic digest</param>
    /// <param name="documentDigest">its document digest</param>
    [Theory]
    [MemberData(nameof(PinnedDocuments))]
    public void ADocumentOfVersion01KeepsItsBytesAndDigests(string name, string semanticDigest, string documentDigest)
    {
        SemanticDocument document = EsjReader.Strict().Read(Fixtures.Bytes(Pinned + name + ".esj.json"));

        Assert.Equal("0.1", document.Version);
        Assert.Equal(Fixtures.Text(Pinned + name + ".canonical.esj.json"), EsjWriter.Canonical().ToText(document));
        Assert.Equal(semanticDigest, Canonicalizer.SemanticDigest(document));
        Assert.Equal(documentDigest, Canonicalizer.DocumentDigest(document));
    }

    /// <summary>
    /// The same document written as version 1.0 is read as 1.0 and differs from its 0.1 form in
    /// the version member alone: the semantic digest, taken over the edition and the values, is
    /// the same, and the document digest, taken over the whole canonical form, is not.
    /// </summary>
    /// <param name="name">the pinned document</param>
    /// <param name="semanticDigest">its semantic digest</param>
    /// <param name="documentDigest">its document digest as version 0.1</param>
    [Theory]
    [MemberData(nameof(PinnedDocuments))]
    public void TheSameDocumentAsVersion10DiffersInItsDocumentDigestAlone(
        string name, string semanticDigest, string documentDigest)
    {
        byte[] written = Encoding.UTF8.GetBytes(WithVersion(Fixtures.Text(Pinned + name + ".esj.json"), "\"1.0\""));
        SemanticDocument document = EsjReader.Strict().Read(written);
        string canonical = Fixtures.Text(Pinned + name + ".canonical.esj.json")
            .Replace("\"version\":\"0.1\"", "\"version\":\"1.0\"", StringComparison.Ordinal);

        Assert.Equal("1.0", document.Version);
        Assert.Equal(canonical, EsjWriter.Canonical().ToText(document));
        Assert.Equal(semanticDigest, Canonicalizer.SemanticDigest(document));
        Assert.NotEqual(documentDigest, Canonicalizer.DocumentDigest(document));
        Assert.Equal(Canonicalizer.Sha256(Encoding.UTF8.GetBytes(canonical)), Canonicalizer.DocumentDigest(document));
    }

    /// <summary>The pretty form writes the version the document carries, either of the two.</summary>
    /// <param name="version">the version, as a JSON string</param>
    [Theory]
    [InlineData("\"0.1\"")]
    [InlineData("\"1.0\"")]
    public void ThePrettyFormWritesTheVersionOfTheDocument(string version)
    {
        string text = WithVersion(Fixtures.Text(Pinned + "minimal.esj.json"), version);
        SemanticDocument document = EsjReader.Strict().Read(Encoding.UTF8.GetBytes(text));

        Assert.Equal(text, EsjWriter.Pretty().ToText(document));
    }

    /// <summary>
    /// A document of 0.1 is read by the rules of this specification: it draws the outcome its
    /// 1.0 form draws, finding for finding.
    /// </summary>
    /// <param name="name">the pinned document</param>
    [Theory]
    [InlineData("example")]
    [InlineData("minimal")]
    public void ADocumentOfVersion01IsValidatedAsItsVersion10Form(string name)
    {
        string text = Fixtures.Text(Pinned + name + ".esj.json");
        ValidationResult old = Validator.Validate(Encoding.UTF8.GetBytes(text));
        ValidationResult current = Validator.Validate(Encoding.UTF8.GetBytes(WithVersion(text, "\"1.0\"")));

        Assert.Equal(current.Status, old.Status);
        Assert.Equal(Places(current), Places(old));
        Assert.Equal(current.NotEvaluated.OrderBy(entry => entry.Key), old.NotEvaluated.OrderBy(entry => entry.Key));
    }

    /// <summary>
    /// Every other value of <c>version</c> is refused at layer L1 with
    /// <c>ESJ-L1-ENVELOPE-VALUE</c> about the member, whether the reader rejects or reports, and
    /// the message names both versions the reader accepts.
    /// </summary>
    /// <param name="version">the value, as JSON</param>
    [Theory]
    [InlineData("\"2.0\"")]
    [InlineData("\"1\"")]
    [InlineData("\"1.1\"")]
    [InlineData("\"1.00\"")]
    [InlineData("\"0.2\"")]
    [InlineData("\"\"")]
    [InlineData("\" 1.0\"")]
    [InlineData("1.0")]
    [InlineData("null")]
    public void EveryOtherVersionIsRefused(string version)
    {
        byte[] written = Encoding.UTF8.GetBytes(WithVersion(Fixtures.Text(Pinned + "minimal.esj.json"), version));

        EsjFormatException refused = Assert.Throws<EsjFormatException>(() => EsjReader.Strict().Read(written));
        Assert.Equal("ESJ-L1-ENVELOPE-VALUE", refused.Code);
        Assert.Equal("version", refused.Subject);
        Assert.Equal(string.Empty, refused.Path.Text);

        ReadResult reported = EsjReader.Strict().ReadWithFindings(written);
        Assert.Null(reported.Document);
        Finding finding = Assert.Single(reported.Findings);
        Assert.Equal(("ESJ-L1-ENVELOPE-VALUE", "version", Severity.Error), (finding.Code.Code, finding.Subject, finding.Severity));
        Assert.Equal(ValidationStatus.Invalid, reported.Validation().Status);
        if (version.StartsWith('"'))
        {
            Assert.Contains("1.0", finding.Message, StringComparison.Ordinal);
            Assert.Contains("0.1", finding.Message, StringComparison.Ordinal);
        }
    }

    /// <summary>
    /// A document derived from another keeps the version of its source, whether a value is set
    /// or removed, and is written with it.
    /// </summary>
    /// <param name="version">the version of the source</param>
    [Theory]
    [InlineData("0.1")]
    [InlineData("1.0")]
    public void ADerivedDocumentKeepsTheVersionOfItsSource(string version)
    {
        SemanticDocument source = EsjReader.Strict().Read(Encoding.UTF8.GetBytes(
            WithVersion(Fixtures.Text(Pinned + "escaping.esj.json"), "\"" + version + "\"")));
        SemanticDocument set = source.With(SemanticPath.Parse("/BT-2"), new SemanticValue("2026-01-15"));
        SemanticDocument removed = set.With(SemanticPath.Parse("/BT-1"), null);

        Assert.Equal(version, set.Version);
        Assert.Equal(version, removed.Version);
        Assert.StartsWith(
            "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"" + version + "\",",
            EsjWriter.Canonical().ToText(removed),
            StringComparison.Ordinal);
        Assert.Equal(source.Extensions.Count, removed.Extensions.Count);
        Assert.Equal(source.Source, removed.Source);
    }

    /// <summary>
    /// A document built from nothing is version 1.0, and is the document a reader builds from the
    /// bytes it writes; one built with a version a reader does not take is refused.
    /// </summary>
    [Fact]
    public void ADocumentBuiltFromNothingIsVersion10()
    {
        SemanticDocument built = new(
            Esj.DefaultSemanticModel,
            new[] { KeyValuePair.Create(SemanticPath.Parse("/BT-1"), new SemanticValue("RE-2026-0001")) });
        string canonical = EsjWriter.Canonical().ToText(built);

        Assert.Equal("1.0", built.Version);
        Assert.StartsWith("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"1.0\",", canonical, StringComparison.Ordinal);
        Assert.Equal(canonical, EsjWriter.Canonical().ToText(EsjReader.Strict().Read(Encoding.UTF8.GetBytes(canonical))));

        SemanticDocument earlier = new(Esj.DefaultSemanticModel, built.Entries, version: "0.1");
        Assert.Equal("0.1", earlier.Version);
        Assert.Equal(Canonicalizer.SemanticDigest(built), Canonicalizer.SemanticDigest(earlier));
        Assert.NotEqual(Canonicalizer.DocumentDigest(built), Canonicalizer.DocumentDigest(earlier));

        Assert.Throws<ArgumentException>(() => new SemanticDocument(Esj.DefaultSemanticModel, built.Entries, version: "2.0"));
    }

    /// <summary>Writes a value into the <c>version</c> member of a document's text.</summary>
    private static string WithVersion(string text, string json)
    {
        Assert.Single(VersionMember.Matches(text));
        return VersionMember.Replace(text, "\"version\": " + json, 1);
    }

    private static List<(string Path, string Code, string Subject, Severity Severity)> Places(ValidationResult result) =>
        result.Findings.Select(finding => (finding.Path.Text, finding.Code.Code, finding.Subject, finding.Severity)).ToList();
}
