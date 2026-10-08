using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using BSNSoft.Esj.Json;
using BSNSoft.Esj.Model;
using BSNSoft.Esj.Validation;
using Xunit;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// The fixture manifest, run against this binding: the registries, the documents with their
/// digests, their canonical bytes and the whole answer of a validation, the documents that have
/// to be rejected, the documents read under bounds other than the defaults, the sets of
/// registries a loader takes or refuses, the grammars and the canonical order.
/// </summary>
public class ManifestTests
{
    /// <summary>The editions this binding carries, as a document writes them.</summary>
    private static readonly HashSet<string> Carried =
        new(Validator.Registries().Select(registry => registry.SemanticModel), StringComparer.Ordinal);

    /// <summary>The registries the manifest names, loaded before anything else is run.</summary>
    public static IEnumerable<object[]> RegistryCases() => Manifest.Rows(Manifest.Registries.Keys);

    /// <summary>The conformant documents of the manifest.</summary>
    public static IEnumerable<object[]> DocumentCases() => Manifest.Rows(Manifest.Documents.Keys);

    /// <summary>The documents of the manifest that have to be rejected.</summary>
    public static IEnumerable<object[]> InvalidCases() => Manifest.Rows(Manifest.Invalid.Keys);

    /// <summary>The documents read under bounds other than the defaults.</summary>
    public static IEnumerable<object[]> BoundCases() => Manifest.Rows(Manifest.Bounds.Keys);

    /// <summary>The sets of registry files a loader takes or refuses.</summary>
    public static IEnumerable<object[]> RegistryCheckCases() => Manifest.Rows(Manifest.RegistryChecks.Keys);

    /// <summary>The documents whose members are written in the wrong order.</summary>
    public static IEnumerable<object[]> OrderCases() => Manifest.Rows(Manifest.CanonicalOrder.Keys);

    /// <summary>The value grammars of the specification, section 6.</summary>
    public static IEnumerable<object[]> GrammarCases() => Manifest.Rows(Manifest.Grammars.Keys);

    /// <summary>A registry carries the terms the manifest counts, with the facts it records.</summary>
    /// <param name="name">the registry file</param>
    [Theory]
    [MemberData(nameof(RegistryCases))]
    public void RegistryCarriesTheTermsTheManifestCounts(string name)
    {
        RegistryCase expected = Manifest.Registries[name];
        Registry registry = Registries.Named(name);
        Assert.Equal(expected.SemanticModel, registry.SemanticModel);
        Assert.Equal(expected.Terms, registry.Terms.Count);
        if (expected.BusinessTerms > 0)
        {
            Assert.Equal(expected.BusinessTerms, registry.Terms.Count(term => !term.IsGroup));
            Assert.Equal(expected.BusinessGroups, registry.Terms.Count(term => term.IsGroup));
        }

        Registry measured = Registries.Measuring(expected.SemanticModel);
        foreach (JsonElement sample in expected.SamplePaths)
        {
            CheckSample(measured, sample, name);
        }
    }

    /// <summary>A conformant document produces the digests and the canonical bytes it names.</summary>
    /// <param name="name">the document</param>
    [Theory]
    [MemberData(nameof(DocumentCases))]
    public void DocumentProducesItsDigests(string name)
    {
        DocumentCase expected = Manifest.Documents[name];
        SemanticDocument document = EsjReader.Strict().Read(Fixtures.Bytes(name));
        byte[] canonical = Canonicalizer.CanonicalBytes(document);

        Assert.NotEmpty(expected.Registries);
        foreach (string registry in expected.Registries)
        {
            // Throws where this build carries no such registry, which is the whole check: a
            // document measured with a registry a binding left out is measured differently.
            Registries.Named(registry);
        }

        Assert.Equal(expected.Values, document.Values.Count);
        Assert.Equal(expected.CanonicalBytes, canonical.Length);
        Assert.Equal(expected.SemanticDigest, Canonicalizer.SemanticDigest(document));
        Assert.Equal(expected.DocumentDigest, Canonicalizer.DocumentDigest(document));
        Assert.Equal(expected.SemanticModel, document.SemanticModel);
    }

    /// <summary>
    /// A document the repository carries the canonical bytes of produces exactly those bytes.
    /// </summary>
    /// <param name="name">the document</param>
    [Theory]
    [MemberData(nameof(DocumentCases))]
    public void DocumentProducesItsCanonicalForm(string name)
    {
        DocumentCase expected = Manifest.Documents[name];
        if (expected.Canonical is null)
        {
            return;
        }

        SemanticDocument document = EsjReader.Strict().Read(Fixtures.Bytes(name));
        Assert.Equal(Fixtures.Text(expected.Canonical), EsjWriter.Canonical().ToText(document));
    }

    /// <summary>A document draws exactly the outcome the manifest records.</summary>
    /// <param name="name">the document</param>
    [Theory]
    [MemberData(nameof(DocumentCases))]
    public void DocumentDrawsTheOutcomeTheManifestRecords(string name)
    {
        DocumentCase expected = Manifest.Documents[name];
        if (!expected.Evaluated)
        {
            return;
        }

        Assert.Equal(expected.Outcome.Normalized(), OutcomeOf(Validator.Validate(Fixtures.Bytes(name))).Normalized());
    }

    /// <summary>
    /// A document the manifest lists as invalid draws the whole outcome it records — the status,
    /// the layers not evaluated and every finding — validated with the registries the case names
    /// where it names any. Where the reader builds a document from it, the document has the
    /// digests the case records: content a model layer refuses passes the reader unchanged.
    /// </summary>
    /// <param name="name">the document</param>
    [Theory]
    [MemberData(nameof(InvalidCases))]
    public void InvalidDocumentDrawsItsOutcome(string name)
    {
        InvalidCase expected = Manifest.Invalid[name];
        if (expected.Digests is not null)
        {
            SemanticDocument document = EsjReader.Strict().Read(Fixtures.Bytes(name));
            Assert.Equal(
                expected.Digests,
                new Digests(
                    document.Values.Count,
                    Canonicalizer.CanonicalBytes(document).Length,
                    Canonicalizer.SemanticDigest(document),
                    Canonicalizer.DocumentDigest(document)));
        }

        if (!expected.Evaluated)
        {
            return;
        }

        ValidationResult result = Validator.Validate(
            Fixtures.Bytes(name),
            expected.Registries is null ? null : new[] { Combined(expected.Registries) });
        Assert.Equal(expected.Outcome.Normalized(), OutcomeOf(result).Normalized());
        Assert.NotEqual(expected.Layer == "business-rule" ? ValidationStatus.Invalid : ValidationStatus.Valid, result.Status);
    }

    /// <summary>A document read under the bounds of a case draws the outcome the manifest records.</summary>
    /// <param name="name">the document and its bounds</param>
    [Theory]
    [MemberData(nameof(BoundCases))]
    public void DocumentUnderBoundsDrawsItsOutcome(string name)
    {
        BoundCase expected = Manifest.Bounds[name];
        Assert.Equal(
            expected.Outcome.Normalized(),
            OutcomeOf(Validator.Validate(Fixtures.Bytes(expected.File), null, LimitsOf(expected.Limits))).Normalized());
    }

    /// <summary>
    /// A set of registry files is taken or refused as the manifest records: the first read as the
    /// core, every further one combined with it as an extension (specification, section 10).
    /// </summary>
    /// <param name="name">the files, joined</param>
    [Theory]
    [MemberData(nameof(RegistryCheckCases))]
    public void RegistriesAreTakenOrRefused(string name)
    {
        RegistryCheckCase expected = Manifest.RegistryChecks[name];
        bool accepted;
        try
        {
            Combined(expected.Files);
            accepted = true;
        }
        catch (Exception refused) when (refused is EsjException or JsonException or KeyNotFoundException
            or InvalidOperationException or FormatException)
        {
            accepted = false;
        }

        Assert.Equal(expected.Accepted, accepted);
    }

    /// <summary>
    /// A document whose members are written in the wrong order canonicalizes to the bytes the
    /// manifest carries beside it.
    /// </summary>
    /// <param name="name">the scrambled document</param>
    [Theory]
    [MemberData(nameof(OrderCases))]
    public void ScrambledDocumentCanonicalizesToItsOrder(string name)
    {
        CanonicalOrderCase expected = Manifest.CanonicalOrder[name];
        SemanticDocument document = EsjReader.Strict().Read(Fixtures.Bytes(name));
        Assert.Equal(expected.Values, document.Values.Count);
        Assert.Equal(Fixtures.Text(expected.Canonical), EsjWriter.Canonical().ToText(document));
        Assert.Equal(expected.DocumentDigest, Canonicalizer.DocumentDigest(document));
    }

    /// <summary>
    /// A value grammar accepts every value of its accept table and refuses every value of its
    /// reject table with the code the manifest names, and with no other.
    /// </summary>
    /// <param name="name">the semantic data type</param>
    [Theory]
    [MemberData(nameof(GrammarCases))]
    public void GrammarAcceptsAndRejects(string name)
    {
        GrammarCase grammar = Manifest.Grammars[name];
        SemanticDocument baseDocument = EsjReader.Strict().Read(Fixtures.Bytes(grammar.Base));
        if (!Carried.Contains(baseDocument.SemanticModel))
        {
            return;
        }

        if (grammar.Member is not null)
        {
            foreach (JsonElement candidate in grammar.Accept)
            {
                Assert.Equal(Array.Empty<string>(), LayerOneErrors(Written(grammar, candidate.GetString()!)));
            }

            foreach (JsonElement candidate in grammar.Reject)
            {
                Assert.Equal(new[] { grammar.Code }, LayerOneErrors(Written(grammar, candidate.GetString()!)));
            }

            return;
        }

        SemanticPath path = SemanticPath.Parse(grammar.Path!);
        foreach (JsonElement candidate in grammar.Accept)
        {
            Assert.Equal(
                Array.Empty<string>(),
                CodesAt(baseDocument.With(path, Values.Read(candidate)), path));
        }

        foreach (JsonElement candidate in grammar.Reject)
        {
            Assert.Equal(
                new[] { grammar.Code },
                CodesAt(baseDocument.With(path, Values.Read(candidate)), path));
        }
    }

    /// <summary>
    /// The base document of an envelope grammar with a candidate written into its member: as the
    /// value of <c>semanticModel</c>, or as the name of the one member of <c>extensions</c>, whose
    /// value is the string <c>x</c>.
    /// </summary>
    private static byte[] Written(GrammarCase grammar, string candidate)
    {
        using JsonDocument parsed = JsonDocument.Parse(Fixtures.Bytes(grammar.Base));
        using MemoryStream bytes = new();
        using (Utf8JsonWriter writer = new(bytes))
        {
            writer.WriteStartObject();
            foreach (JsonProperty member in parsed.RootElement.EnumerateObject())
            {
                if (grammar.Member == "semanticModel" && member.Name == "semanticModel")
                {
                    writer.WriteString("semanticModel", candidate);
                }
                else
                {
                    member.WriteTo(writer);
                }
            }

            if (grammar.Member == "extensions")
            {
                writer.WriteStartObject("extensions");
                writer.WriteString(candidate, "x");
                writer.WriteEndObject();
            }

            writer.WriteEndObject();
        }

        return bytes.ToArray();
    }

    private static string[] LayerOneErrors(byte[] document) =>
        Validator.Validate(document).Findings
            .Where(finding => finding.Severity == Severity.Error
                && finding.Code.Code.StartsWith("ESJ-L1-", StringComparison.Ordinal))
            .Select(finding => finding.Code.Code)
            .ToArray();

    private static void CheckSample(Registry registry, JsonElement sample, string name)
    {
        string id = sample.GetProperty("term").GetString()!;
        Term term = registry.TermOf(id) ?? throw new Xunit.Sdk.XunitException(
            name + " carries " + id + ", and this binding read no such term");
        Assert.Equal(sample.GetProperty("kind").GetString(), term.IsGroup ? "BG" : "BT");
        Assert.Equal(sample.GetProperty("min").GetInt32(), term.Cardinality.Min);
        JsonElement max = sample.GetProperty("max");
        Assert.Equal(
            max.ValueKind == JsonValueKind.String ? Cardinality.Unbounded : max.GetInt32(),
            term.Cardinality.Max);
        if (sample.TryGetProperty("datatype", out JsonElement datatype))
        {
            Assert.Equal(datatype.GetString(), term.Datatype?.RegistryDatatype());
        }

        List<(string Member, int Min)> components = new();
        if (sample.TryGetProperty("components", out JsonElement listed))
        {
            components.AddRange(listed.EnumerateArray().Select(component =>
                (component.GetProperty("member").GetString()!, component.GetProperty("min").GetInt32())));
        }

        Assert.Equal(
            components,
            term.Components.Select(component => (component.JsonMember, component.Min)).ToList());

        // The sample path is the path the index rule of section 5.3 gives the term.
        Assert.Equal(sample.GetProperty("path").GetString(), PathOf(registry, term));
    }

    private static string PathOf(Registry registry, Term term)
    {
        System.Text.StringBuilder path = new();
        foreach (string step in term.Path)
        {
            path.Append('/').Append(step);
            if (registry.IsRepeatable(step))
            {
                path.Append("/0");
            }
        }

        return path.ToString();
    }

    /// <summary>The codes of the errors a validation of a document reports about one path.</summary>
    private static string[] CodesAt(SemanticDocument document, SemanticPath path) =>
        Validator.Validate(document).Findings
            .Where(finding => finding.Severity == Severity.Error && finding.Path.Text == path.Text)
            .Select(finding => finding.Code.Code)
            .ToArray();

    /// <summary>A result in the form the manifest records it.</summary>
    private static Outcome OutcomeOf(ValidationResult result) => new(
        result.Status switch
        {
            ValidationStatus.Valid => "VALID",
            ValidationStatus.Invalid => "INVALID",
            _ => "INDETERMINATE",
        },
        result.NotEvaluated.OrderBy(entry => entry.Key)
            .Select(entry => (entry.Key.ToString(), entry.Value switch
            {
                NotEvaluatedReason.Limit => "LIMIT",
                NotEvaluatedReason.PrecedingLayerFailed => "PRECEDING-LAYER-FAILED",
                NotEvaluatedReason.EditionUnknown => "EDITION-UNKNOWN",
                _ => "NOT-REQUESTED",
            }))
            .ToList(),
        result.Findings
            .Select(finding => (finding.Path.Text, finding.Code.Code, finding.Subject, finding.Severity switch
            {
                Severity.Error => "error",
                Severity.Warning => "warning",
                _ => "info",
            }))
            .ToList());

    /// <summary>Reads registry files of the repository and combines every one after the first with it.</summary>
    private static Registry Combined(IReadOnlyList<string> files)
    {
        Registry? combined = null;
        foreach (string file in files)
        {
            using MemoryStream input = new(Fixtures.Bytes(file));
            Registry read = Registry.Load(input);
            combined = combined is null ? read : combined.WithExtension(read);
        }

        return combined!;
    }

    /// <summary>The defaults of the specification, section 12.2, with every bound a case names replaced.</summary>
    private static Limits LimitsOf(JsonElement named)
    {
        Limits.Builder limits = Limits.Defaults.ToBuilder();
        foreach (JsonProperty bound in named.EnumerateObject())
        {
            _ = bound.Name switch
            {
                "maxDocumentBytes" => limits.MaxDocumentBytes(bound.Value.GetInt64()),
                "maxValues" => limits.MaxValues(bound.Value.GetInt32()),
                "maxValueMembers" => limits.MaxValueMembers(bound.Value.GetInt32()),
                "maxPathSegments" => limits.MaxPathSegments(bound.Value.GetInt32()),
                "maxPathBytes" => limits.MaxPathBytes(bound.Value.GetInt32()),
                "maxStringBytes" => limits.MaxStringBytes(bound.Value.GetInt64()),
                "maxBinaryValueBytes" => limits.MaxBinaryValueBytes(bound.Value.GetInt64()),
                "maxTotalBinaryBytes" => limits.MaxTotalBinaryBytes(bound.Value.GetInt64()),
                "maxExtensionDepth" => limits.MaxExtensionDepth(bound.Value.GetInt32()),
                "maxExtensionNodes" => limits.MaxExtensionNodes(bound.Value.GetInt32()),
                _ => throw new ArgumentException("no bound is called " + bound.Name, nameof(named)),
            };
        }

        return limits.Build();
    }
}
