using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using En16931.SemanticJson.Fixtures;
using Xunit;

namespace En16931.SemanticJson.Tests;

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
            En16931.SemanticJson.Validation.Validator.Registries()
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
