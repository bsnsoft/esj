using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using En16931.SemanticJson.Json;
using En16931.SemanticJson.Model;
using En16931.SemanticJson.Validation;
using Xunit;

namespace En16931.SemanticJson.Tests;

/// <summary>
/// The fixture manifest, run against this binding: the registries, the conformant documents
/// with their digests and their canonical bytes, the documents that have to be rejected, the
/// value grammars and the canonical order.
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

    /// <summary>
    /// A conformant document draws exactly the errors of layers L2 and L3 the manifest
    /// records, and none of its own.
    /// </summary>
    /// <param name="name">the document</param>
    [Theory]
    [MemberData(nameof(DocumentCases))]
    public void DocumentDrawsTheFindingsTheManifestRecords(string name)
    {
        DocumentCase expected = Manifest.Documents[name];
        if (!Carried.Contains(expected.SemanticModel))
        {
            return;
        }

        Assert.Equal(
            expected.Findings.OrderBy(finding => finding, TupleOrder).ToList(),
            Errors(Validator.Validate(Fixtures.Bytes(name))));
    }

    /// <summary>
    /// A document the manifest lists as invalid draws the code it names, at the path it
    /// names. A document rejected at layer L1 draws exactly the rows the manifest holds for
    /// it and no others: the model layers are not evaluated over a document the reader
    /// refused a member of (specification, section 9.5), and section 9.6 fixes how far a
    /// reader reads, so the rows are the whole answer.
    /// </summary>
    /// <param name="name">the document</param>
    [Theory]
    [MemberData(nameof(InvalidCases))]
    public void InvalidDocumentIsRejected(string name)
    {
        InvalidCase expected = Manifest.Invalid[name];
        ValidationResult result = Validator.Validate(Fixtures.Bytes(name));
        List<(string Path, string Code)> errors = Errors(result);

        if (expected.Layer == "business-rule")
        {
            Assert.Empty(errors);
            Assert.NotEqual(ValidationStatus.Invalid, result.Status);
            return;
        }

        if (expected.Layer is "L1" or "limit")
        {
            // The specification, section 9.6 fixes how far a reader reads, so the rows of a
            // document layer L1 refused are the whole answer and not a sample of it.
            Assert.Equal(expected.Rows.OrderBy(row => row, TupleOrder).ToList(), errors);
        }
        else
        {
            Assert.Contains(errors, finding => finding == expected.Rows[0]);
        }

        Assert.NotEqual(ValidationStatus.Valid, result.Status);
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

        SemanticPath path = SemanticPath.Parse(grammar.Path);
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

    private static string[] CodesAt(SemanticDocument document, SemanticPath path) =>
        Errors(Validator.Validate(document))
            .Where(finding => finding.Path == path.Text)
            .Select(finding => finding.Code)
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

    /// <summary>
    /// The error findings of a result, as the runner of the manifest reads them: the codes of
    /// this specification, without the two that record something not evaluated.
    /// </summary>
    private static List<(string Path, string Code)> Errors(ValidationResult result) =>
        result.Findings
            .Where(finding => finding.Code.Code.StartsWith("ESJ-L", StringComparison.Ordinal)
                && !finding.Code.Code.EndsWith("NOT-CHECKED", StringComparison.Ordinal)
                && !finding.Code.Code.EndsWith("EDITION-UNKNOWN", StringComparison.Ordinal))
            .Select(finding => (finding.Path.Text, finding.Code.Code))
            .OrderBy(finding => finding, TupleOrder)
            .ToList();

    private static readonly IComparer<(string Path, string Code)> TupleOrder =
        Comparer<(string Path, string Code)>.Create((left, right) =>
        {
            int paths = string.CompareOrdinal(left.Path, right.Path);
            return paths != 0 ? paths : string.CompareOrdinal(left.Code, right.Code);
        });
}
