using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using BSNSoft.Esj.Fixtures;
using Xunit;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// The request protocol of <c>conformance/fixtures/run.py</c>, answered by this binding: the
/// runner of the manifest speaks it, and these tests hold the answers to the manifest without
/// a runner having to be started.
/// </summary>
public class ProtocolTests
{
    private static readonly Protocol Binding = new(Fixtures.Repository);

    /// <summary>The binding names the editions it carries.</summary>
    [Fact]
    public void ItNamesTheEditionsItCarries()
    {
        JsonElement answer = Ask("{\"op\": \"editions\"}");
        List<string> editions = answer.GetProperty("semanticModels")
            .EnumerateArray().Select(edition => edition.GetString()!).ToList();
        Assert.Contains(Esj.DefaultSemanticModel, editions);
        Assert.Equal(
            BSNSoft.Esj.Validation.Validator.Registries()
                .Select(registry => registry.SemanticModel).ToList(),
            editions);
    }

    /// <summary>A digest request answers what the manifest records for that document.</summary>
    [Fact]
    public void ItAnswersTheDigestsOfADocument()
    {
        DocumentCase expected = Manifest.Documents["examples/minimal.esj.json"];
        JsonElement answer = Ask("{\"op\": \"digest\", \"file\": \"examples/minimal.esj.json\"}");
        Assert.Equal(expected.SemanticDigest, answer.GetProperty("semanticDigest").GetString());
        Assert.Equal(expected.DocumentDigest, answer.GetProperty("documentDigest").GetString());
        Assert.Equal(expected.CanonicalBytes, answer.GetProperty("canonicalBytes").GetInt32());
        Assert.Equal(expected.Values, answer.GetProperty("values").GetInt32());
    }

    /// <summary>A canonicalize request answers the canonical bytes beside the document.</summary>
    [Fact]
    public void ItAnswersTheCanonicalForm()
    {
        JsonElement answer = Ask("{\"op\": \"canonicalize\", \"file\": \"examples/extended.esj.json\"}");
        Assert.Equal(
            Fixtures.Text("examples/extended.canonical.esj.json"),
            answer.GetProperty("canonical").GetString());
    }

    /// <summary>A validate request reports the finding a document draws, with its path.</summary>
    [Fact]
    public void ItReportsWhatAValidationFound()
    {
        JsonElement answer = Ask(
            "{\"op\": \"validate\", \"file\": \"examples/invalid/calendar-impossible-date.esj.json\"}");
        List<(string Path, string Code)> findings = answer.GetProperty("findings").EnumerateArray()
            .Select(finding => (finding.GetProperty("path").GetString()!, finding.GetProperty("code").GetString()!))
            .ToList();
        Assert.Contains(("/BT-2", "ESJ-L2-DATE"), findings);
        Assert.Equal("INVALID", answer.GetProperty("status").GetString());
    }

    /// <summary>A validate request takes a document inline as well as by name.</summary>
    [Fact]
    public void ItTakesADocumentInline()
    {
        string document = Fixtures.Text("examples/minimal.esj.json");
        JsonElement answer = Ask("{\"op\": \"validate\", \"document\": " + document + "}");
        Assert.Empty(answer.GetProperty("findings").EnumerateArray());
        Assert.Equal("VALID", answer.GetProperty("status").GetString());
    }

    /// <summary>A rules request reports the identifiers the pack reports over a document.</summary>
    [Fact]
    public void ItReportsTheRulesThePackReports()
    {
        string document = Fixtures.Text("examples/invalid/arithmetic-mismatch.esj.json");
        JsonElement answer = Ask("{\"op\": \"rules\", \"document\": " + document + "}");
        List<string> rules = answer.GetProperty("rules")
            .EnumerateArray().Select(rule => rule.GetString()!).ToList();
        Assert.NotEmpty(rules);
        Assert.Equal(rules.OrderBy(rule => rule, StringComparer.Ordinal), rules);
    }

    /// <summary>
    /// A rules request that names a pack of the rule language is answered with that pack: the
    /// arithmetic pack of the manifest reports what the manifest records.
    /// </summary>
    [Fact]
    public void ItAnswersARulesRequestWithThePackItNames()
    {
        JsonElement section = Fixtures.Manifests[0].GetProperty("arithmetic");
        JsonElement answer = Ask("{\"op\": \"rules\", \"pack\": \"" + section.GetProperty("pack").GetString()
            + "\", \"file\": \"" + section.GetProperty("base").GetString() + "\"}");
        Assert.Equal(
            section.GetProperty("expect").GetProperty("rules").EnumerateArray().Select(rule => rule.GetString()!),
            answer.GetProperty("rules").EnumerateArray().Select(rule => rule.GetString()!));
    }

    /// <summary>
    /// A validate answer carries every finding with its severity, the status, and every layer that
    /// was not evaluated with its reason, as the specification, section 9.5 spells them.
    /// </summary>
    [Fact]
    public void AValidateAnswerCarriesSeverityStatusAndCoverage()
    {
        JsonElement refused = Ask("{\"op\": \"validate\", \"file\": \"examples/invalid/lone-surrogate.esj.json\"}");
        JsonElement finding = Assert.Single(refused.GetProperty("findings").EnumerateArray());
        Assert.Equal("error", finding.GetProperty("severity").GetString());
        Assert.Equal("INVALID", refused.GetProperty("status").GetString());
        Assert.Equal(
            new[] { ("L2", "PRECEDING-LAYER-FAILED"), ("L3", "PRECEDING-LAYER-FAILED") },
            Coverage(refused));

        string unknown = Fixtures.Text("examples/minimal.esj.json")
            .Replace("EN16931-1:2017+A1:2019/AC:2020", "EN16931-1:2099", StringComparison.Ordinal);
        JsonElement indeterminate = Ask("{\"op\": \"validate\", \"document\": " + unknown + "}");
        JsonElement edition = Assert.Single(indeterminate.GetProperty("findings").EnumerateArray());
        Assert.Equal("ESJ-L2-EDITION-UNKNOWN", edition.GetProperty("code").GetString());
        Assert.Equal("info", edition.GetProperty("severity").GetString());
        Assert.Equal("INDETERMINATE", indeterminate.GetProperty("status").GetString());
        Assert.Equal(new[] { ("L2", "EDITION-UNKNOWN"), ("L3", "EDITION-UNKNOWN") }, Coverage(indeterminate));

        JsonElement valid = Ask("{\"op\": \"validate\", \"file\": \"examples/minimal.esj.json\"}");
        Assert.Empty(Coverage(valid));
    }

    /// <summary>
    /// A validate request may name bounds of section 12.2 under the names <c>Limits</c> gives them,
    /// so a small document pins a bound; a name the binding does not know is an error of the
    /// request, as is a bound <c>Limits</c> refuses.
    /// </summary>
    [Fact]
    public void AValidateRequestMayCarryLimits()
    {
        JsonElement limited = Ask("{\"op\": \"validate\", \"file\": \"examples/minimal.esj.json\", \"limits\": {\"maxValues\": 3}}");
        JsonElement finding = Assert.Single(limited.GetProperty("findings").EnumerateArray());
        Assert.Equal("ESJ-L1-LIMIT", finding.GetProperty("code").GetString());
        Assert.Equal("values[\"/BT-5\"]", finding.GetProperty("subject").GetString());
        Assert.Equal("INDETERMINATE", limited.GetProperty("status").GetString());
        Assert.Equal(new[] { ("L2", "LIMIT"), ("L3", "LIMIT") }, Coverage(limited));

        Assert.True(Answer("{\"op\": \"validate\", \"file\": \"examples/minimal.esj.json\", \"limits\": {\"maxWhatever\": 3}}")
            .TryGetProperty("error", out _));
        Assert.True(Answer("{\"op\": \"validate\", \"file\": \"examples/minimal.esj.json\", \"limits\": {\"maxExtensionDepth\": 2147483647}}")
            .TryGetProperty("error", out _));
    }

    /// <summary>
    /// A subject that names a member whose name carries a lone surrogate travels escaped, as the
    /// reader wrote it, and never as a replacement character.
    /// </summary>
    [Fact]
    public void ASubjectWithALoneSurrogateTravelsEscaped()
    {
        JsonElement answer = Ask("{\"op\": \"validate\", \"file\": \"examples/invalid/surrogate-in-path.esj.json\"}");
        JsonElement finding = Assert.Single(answer.GetProperty("findings").EnumerateArray());
        Assert.Equal("values[\"/BT-9\\ud800\"]", finding.GetProperty("subject").GetString());
    }

    /// <summary>
    /// A registry request reads the first file and combines every further one with it, and answers
    /// whether the binding accepts them: the core registry is no extension of itself, and the
    /// XRechnung extension imports the 2017 edition and is refused beside the 2026 one, where this
    /// build carries that edition.
    /// </summary>
    [Fact]
    public void ARegistryRequestSaysWhetherTheRegistriesAreAccepted()
    {
        JsonElement accepted = Ask("{\"op\": \"registry\", \"files\": [\"model/en16931/2017.json\", \"model/xrechnung/3.0.2.json\", \"model/b2c/0.1.json\"]}");
        Assert.True(accepted.GetProperty("accepted").GetBoolean());
        Assert.False(accepted.TryGetProperty("error", out _));

        JsonElement twice = Ask("{\"op\": \"registry\", \"files\": [\"model/en16931/2017.json\", \"model/en16931/2017.json\"]}");
        Assert.False(twice.GetProperty("accepted").GetBoolean());
        Assert.Contains("own namespace only", twice.GetProperty("error").GetString(), StringComparison.Ordinal);

        if (File.Exists(Path.Combine(Fixtures.Repository, "model/en16931/2026.json")))
        {
            JsonElement refused = Ask("{\"op\": \"registry\", \"files\": [\"model/en16931/2026.json\", \"model/xrechnung/3.0.2.json\"]}");
            Assert.False(refused.GetProperty("accepted").GetBoolean());
            Assert.False(string.IsNullOrEmpty(refused.GetProperty("error").GetString()));
        }
    }

    /// <summary>
    /// A registry no value can satisfy is refused by a registry request, and a file that is not
    /// there is an error of the request rather than a refusal.
    /// </summary>
    [Fact]
    public void ARegistryRequestRefusesARegistryNoValueCanSatisfy()
    {
        DirectoryInfo root = Directory.CreateTempSubdirectory("esj-registry-");
        string file = Path.Combine(root.FullName, "binary-without-components.json");
        try
        {
            File.WriteAllText(file, "{\"model\":\"T\",\"edition\":\"T 1\",\"terms\":[{\"id\":\"BT-1\",\"kind\":\"BT\","
                + "\"name\":\"n\",\"slug\":\"s\",\"parent\":null,\"path\":[\"BT-1\"],\"min\":0,\"max\":1,"
                + "\"datatype\":\"BinaryObject\",\"components\":[]}]}");
            Protocol local = new(root.FullName);
            using JsonDocument refused = JsonDocument.Parse(local.Answer(
                "{\"op\": \"registry\", \"files\": [\"binary-without-components.json\"]}"));
            Assert.False(refused.RootElement.GetProperty("accepted").GetBoolean());

            StringWriter answers = new();
            local.Run(new StringReader("{\"op\": \"registry\", \"files\": [\"absent.json\"]}"), answers);
            using JsonDocument missing = JsonDocument.Parse(answers.ToString());
            Assert.True(missing.RootElement.TryGetProperty("error", out _));
            Assert.False(missing.RootElement.TryGetProperty("accepted", out _));
        }
        finally
        {
            File.Delete(file);
            root.Delete();
        }
    }

    private static (string Layer, string Reason)[] Coverage(JsonElement answer) =>
        answer.GetProperty("notEvaluated").EnumerateArray()
            .Select(layer => (layer.GetProperty("layer").GetString()!, layer.GetProperty("reason").GetString()!))
            .ToArray();

    private static JsonElement Answer(string request)
    {
        StringWriter answers = new();
        Binding.Run(new StringReader(request), answers);
        return JsonDocument.Parse(answers.ToString()).RootElement;
    }

    /// <summary>The harness answers a stream of requests, one line each.</summary>
    [Fact]
    public void ItAnswersOneLinePerRequest()
    {
        StringReader requests = new(string.Join("\n", new[]
        {
            "{\"op\": \"editions\"}",
            "{\"op\": \"digest\", \"file\": \"examples/minimal.esj.json\"}",
        }));
        StringWriter answers = new();
        Binding.Run(requests, answers);

        string[] lines = answers.ToString()
            .Split('\n', StringSplitOptions.RemoveEmptyEntries)
            .Select(line => line.TrimEnd('\r'))
            .ToArray();
        Assert.Equal(2, lines.Length);
        foreach (string line in lines)
        {
            using JsonDocument parsed = JsonDocument.Parse(line);
            Assert.Equal(JsonValueKind.Object, parsed.RootElement.ValueKind);
        }
    }

    private static JsonElement Ask(string request) =>
        JsonDocument.Parse(Binding.Answer(request)).RootElement;
}
