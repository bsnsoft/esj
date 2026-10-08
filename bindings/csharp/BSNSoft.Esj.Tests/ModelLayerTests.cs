using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using BSNSoft.Esj.Model;
using BSNSoft.Esj.Validation;
using Xunit;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// What layer L2 reports about one path, what a composed result names as not evaluated, and what
/// a registry is held to when it is read and combined: the probes of the specification audit of
/// 8 October 2026 and the decisions taken on them.
/// </summary>
public class ModelLayerTests
{
    /// <summary>Returns the minimal example with one member added to <c>values</c>.</summary>
    private static byte[] MinimalWith(string member)
    {
        string text = Fixtures.Text("examples/minimal.esj.json");
        int at = text.IndexOf("\"/BT-1\":", StringComparison.Ordinal);
        return Encoding.UTF8.GetBytes(text.Insert(at, member + ",\n    "));
    }

    /// <summary>The findings at one path, as code and subject, in the order they were produced.</summary>
    private static (string Code, string Subject)[] At(ValidationResult result, string path) =>
        result.Findings.Where(finding => finding.Path.Text == path)
            .Select(finding => (finding.Code.Code, finding.Subject)).ToArray();

    private static (string Code, string Subject)[] FindingsAt(string member, string path) =>
        At(Validator.Validate(MinimalWith(member)), path);

    // ------------------------------------------------------------ the model layer, path by path

    /// <summary>
    /// Where every segment of a path is known, the index rule, the parent chain, the content and
    /// the components are checked apart, so each problem draws its own finding.
    /// </summary>
    [Fact]
    public void EveryCheckOfAKnownPathRunsOnItsOwn()
    {
        Assert.Equal(new[] { ("ESJ-L2-PARENT-CHAIN", "BT-2"), ("ESJ-L2-DATE", "") },
            FindingsAt("\"/BG-25/0/BT-2\": \"2026-02-30\"", "/BG-25/0/BT-2"));
        Assert.Equal(new[] { ("ESJ-L2-INDEX-FORBIDDEN", "BT-2"), ("ESJ-L2-DATE", "") },
            FindingsAt("\"/BT-2/0\": \"2026-02-30\"", "/BT-2/0"));
        Assert.Equal(new[] { ("ESJ-L2-INDEX-REQUIRED", "BG-25"), ("ESJ-L2-PARENT-CHAIN", "BT-146") },
            FindingsAt("\"/BG-25/BT-146\": \"1\"", "/BG-25/BT-146"));
    }

    /// <summary>
    /// A core segment the registry does not know is <c>ESJ-L2-UNKNOWN-TERM</c> even beside an
    /// extension segment no loaded registry describes, which is <c>ESJ-L2-NOT-CHECKED</c>: no
    /// extension supplies a core identifier, so the document is wrong whatever registry is loaded
    ///.
    /// </summary>
    [Fact]
    public void AnUnknownCoreSegmentIsUnknownBesideAnUncheckedExtension()
    {
        ValidationResult result = Validator.Validate(MinimalWith("\"/BG-999/BT-ZZZ-1\": \"x\""));
        Assert.Equal(new[] { ("ESJ-L2-UNKNOWN-TERM", "BG-999"), ("ESJ-L2-NOT-CHECKED", "") }, At(result, "/BG-999/BT-ZZZ-1"));
        Assert.Equal(ValidationStatus.Invalid, result.Status);

        Assert.Equal(new[] { ("ESJ-L2-NOT-CHECKED", ""), ("ESJ-L2-UNKNOWN-TERM", "BT-999") },
            FindingsAt("\"/BG-ZZZ-1/BT-999\": \"x\"", "/BG-ZZZ-1/BT-999"));
    }

    /// <summary>One <c>ESJ-L2-UNKNOWN-TERM</c> for each unknown segment, each naming its segment.</summary>
    [Fact]
    public void EachUnknownSegmentIsOneFinding() =>
        Assert.Equal(new[] { ("ESJ-L2-UNKNOWN-TERM", "BG-998"), ("ESJ-L2-UNKNOWN-TERM", "BT-999") },
            FindingsAt("\"/BG-998/BT-999\": \"x\"", "/BG-998/BT-999"));

    /// <summary>
    /// An identifier a loaded namespace does not define is not checked rather than unknown, since
    /// a namespace may grow, and the message says it is the loaded registry that lacks it.
    /// </summary>
    [Fact]
    public void AnUnknownIdentifierOfALoadedNamespaceIsNotChecked()
    {
        ValidationResult result = Validator.Validate(MinimalWith("\"/BG-DEX-09/0/BT-DEX-999\": \"x\""));
        Finding finding = Assert.Single(result.Findings, found => found.Path.Text == "/BG-DEX-09/0/BT-DEX-999");
        Assert.Equal("ESJ-L2-NOT-CHECKED", finding.Code.Code);
        Assert.Contains("loaded registry of the namespace DEX", finding.Message, StringComparison.Ordinal);
        Assert.Equal(ValidationStatus.Indeterminate, result.Status);
    }

    /// <summary>
    /// An unknown segment ends the checks below it, and the known segments above it are still held
    /// to the index rule; the findings stand in the order of the segments.
    /// </summary>
    [Fact]
    public void TheSegmentsAboveAnUnknownOneAreStillHeldToTheIndexRule() =>
        Assert.Equal(new[] { ("ESJ-L2-INDEX-REQUIRED", "BG-25"), ("ESJ-L2-NOT-CHECKED", "") },
            FindingsAt("\"/BG-25/BG-ZZZ-1/BT-1\": \"x\"", "/BG-25/BG-ZZZ-1/BT-1"));

    /// <summary>
    /// A component finding names the component, so two of them about one value are told apart
    /// without the message being read.
    /// </summary>
    [Fact]
    public void AComponentFindingNamesTheComponent()
    {
        Assert.Equal(new[] { ("ESJ-L2-COMPONENT-MISSING", "mimeCode"), ("ESJ-L2-COMPONENT-MISSING", "filename") },
            FindingsAt("\"/BG-24/0/BT-122\": \"A\", \"/BG-24/0/BT-125\": \"QUJD\"", "/BG-24/0/BT-125"));
        Assert.Equal(new[] { ("ESJ-L2-COMPONENT-NOT-ALLOWED", "scheme") },
            At(Validator.Validate(Fixtures.Bytes("examples/invalid/scheme-on-text-value.esj.json")), "/BG-4/BT-27"));
        Assert.Equal(new[] { ("ESJ-L2-UNKNOWN-TERM", "BT-999") },
            At(Validator.Validate(Fixtures.Bytes("examples/invalid/unknown-term.esj.json")), "/BT-999"));
        Assert.Equal(new[] { ("ESJ-L2-INDEX-FORBIDDEN", "BT-1") },
            At(Validator.Validate(Fixtures.Bytes("examples/invalid/index-on-bt-1.esj.json")), "/BT-1/0"));
    }

    // ------------------------------------------------------------ composing two results

    /// <summary>
    /// A layer the caller did not ask for keeps <c>NOT-REQUESTED</c> in a composed result, unless
    /// the other run was asked for it and established <c>EDITION-UNKNOWN</c>; the other three
    /// reasons are ranked. The table is the one the Java implementation composes by.
    /// </summary>
    [Theory]
    [InlineData("LIMIT", "LIMIT", "LIMIT")]
    [InlineData("LIMIT", "PRECEDING-LAYER-FAILED", "LIMIT")]
    [InlineData("LIMIT", "EDITION-UNKNOWN", "LIMIT")]
    [InlineData("LIMIT", "NOT-REQUESTED", "NOT-REQUESTED")]
    [InlineData("PRECEDING-LAYER-FAILED", "LIMIT", "LIMIT")]
    [InlineData("PRECEDING-LAYER-FAILED", "PRECEDING-LAYER-FAILED", "PRECEDING-LAYER-FAILED")]
    [InlineData("PRECEDING-LAYER-FAILED", "EDITION-UNKNOWN", "PRECEDING-LAYER-FAILED")]
    [InlineData("PRECEDING-LAYER-FAILED", "NOT-REQUESTED", "NOT-REQUESTED")]
    [InlineData("EDITION-UNKNOWN", "LIMIT", "LIMIT")]
    [InlineData("EDITION-UNKNOWN", "PRECEDING-LAYER-FAILED", "PRECEDING-LAYER-FAILED")]
    [InlineData("EDITION-UNKNOWN", "EDITION-UNKNOWN", "EDITION-UNKNOWN")]
    [InlineData("EDITION-UNKNOWN", "NOT-REQUESTED", "EDITION-UNKNOWN")]
    [InlineData("NOT-REQUESTED", "LIMIT", "NOT-REQUESTED")]
    [InlineData("NOT-REQUESTED", "PRECEDING-LAYER-FAILED", "NOT-REQUESTED")]
    [InlineData("NOT-REQUESTED", "EDITION-UNKNOWN", "EDITION-UNKNOWN")]
    [InlineData("NOT-REQUESTED", "NOT-REQUESTED", "NOT-REQUESTED")]
    public void TheCompositionOfTwoReasonsFollowsOneTable(string mine, string theirs, string kept)
    {
        Assert.Equal(Reason(kept), ValidationResult.Surviving(Reason(mine), Reason(theirs)));
        ValidationResult left = ValidationResult.Of(Array.Empty<Finding>(),
            new Dictionary<ValidationLayer, NotEvaluatedReason> { [ValidationLayer.L2] = Reason(mine) });
        ValidationResult right = ValidationResult.Of(Array.Empty<Finding>(),
            new Dictionary<ValidationLayer, NotEvaluatedReason> { [ValidationLayer.L2] = Reason(theirs) });
        Assert.Equal(Reason(kept), left.Merge(right).NotEvaluated[ValidationLayer.L2]);
    }

    private static NotEvaluatedReason Reason(string token) => token switch
    {
        "LIMIT" => NotEvaluatedReason.Limit,
        "PRECEDING-LAYER-FAILED" => NotEvaluatedReason.PrecedingLayerFailed,
        "EDITION-UNKNOWN" => NotEvaluatedReason.EditionUnknown,
        _ => NotEvaluatedReason.NotRequested,
    };

    /// <summary>
    /// A composition with a run that asked for fewer layers keeps the layer neither run evaluated
    /// as not requested.
    /// </summary>
    [Fact]
    public void NotRequestedSurvivesAComposition()
    {
        ValidationResult asked = ValidationResult.Of(Array.Empty<Finding>(),
            new Dictionary<ValidationLayer, NotEvaluatedReason> { [ValidationLayer.L3] = NotEvaluatedReason.NotRequested });
        ValidationResult limited = ValidationResult.Of(Array.Empty<Finding>(),
            new Dictionary<ValidationLayer, NotEvaluatedReason>
            {
                [ValidationLayer.L2] = NotEvaluatedReason.Limit,
                [ValidationLayer.L3] = NotEvaluatedReason.Limit,
            });
        ValidationResult merged = asked.Merge(limited);
        Assert.Equal(NotEvaluatedReason.NotRequested, merged.NotEvaluated[ValidationLayer.L3]);
        Assert.False(merged.NotEvaluated.ContainsKey(ValidationLayer.L2));
    }

    /// <summary>
    /// The verdict over bytes takes the reason of the model layers from the run that was asked for
    /// them: a document of an edition no registry describes is <c>EDITION-UNKNOWN</c> at L2 and
    /// L3, and L1 is evaluated.
    /// </summary>
    [Fact]
    public void AnUnknownEditionIsTheReasonOfTheModelLayers()
    {
        byte[] document = Encoding.UTF8.GetBytes(Fixtures.Text("examples/minimal.esj.json")
            .Replace("EN16931-1:2017+A1:2019/AC:2020", "EN16931-1:2099", StringComparison.Ordinal));
        ValidationResult result = Validator.Validate(document);
        Assert.Equal(ValidationStatus.Indeterminate, result.Status);
        Assert.Equal(
            new[] { (ValidationLayer.L2, NotEvaluatedReason.EditionUnknown), (ValidationLayer.L3, NotEvaluatedReason.EditionUnknown) },
            result.NotEvaluated.OrderBy(entry => entry.Key).Select(entry => (entry.Key, entry.Value)).ToArray());
        Assert.Equal("ESJ-L2-EDITION-UNKNOWN", Assert.Single(result.Findings).Code.Code);
    }

    // ------------------------------------------------------------ loading registries

    private static string Term(string id, string datatype, string components, string parent = "null", string path = "") =>
        "{\"id\":\"" + id + "\",\"kind\":\"" + id.Substring(0, 2) + "\",\"name\":\"n\",\"slug\":\"s\",\"parent\":" + parent
        + ",\"path\":[" + (path.Length == 0 ? "\"" + id + "\"" : path) + "],\"min\":0,\"max\":1,\"datatype\":"
        + (datatype == "null" ? "null" : "\"" + datatype + "\"") + ",\"components\":[" + components + "]}";

    private static string Component(string role, int min) =>
        "{\"role\":\"" + role + "\",\"name\":\"" + role + "\",\"min\":" + min + "}";

    private static string RegistryText(string terms, string imports = "", string model = "Test", string edition = "Test 1") =>
        "{\"model\":\"" + model + "\",\"edition\":\"" + edition + "\""
        + (imports.Length == 0 ? string.Empty : ",\"imports\":[" + imports + "]")
        + ",\"terms\":[" + terms + "]}";

    private static Registry Load(string text)
    {
        using MemoryStream input = new(Encoding.UTF8.GetBytes(text));
        return Registry.Load(input);
    }

    /// <summary>
    /// A term no value can satisfy is a defect of the registry, refused when the registry is read
    /// (specification, section 10).
    /// </summary>
    /// <param name="term">the term the registry carries</param>
    [Theory]
    [InlineData("{0}BinaryObject{1}")]
    [InlineData("{0}BinaryObject{1}mimeCode:1")]
    [InlineData("{0}BinaryObject{1}mimeCode:1,filename:0")]
    [InlineData("{0}Text{1}scheme:0")]
    [InlineData("{0}Identifier{1}schemeVersion:0")]
    [InlineData("{0}Identifier{1}scheme:0,schemeVersion:1")]
    [InlineData("{0}Identifier{1}scheme:0,scheme:0")]
    [InlineData("{0}Identifier{1}mimeCode:0")]
    [InlineData("{0}null{1}scheme:0")]
    public void ARegistryWhoseComponentsNoValueCanSatisfyIsRefused(string term)
    {
        string[] parts = term.Replace("{0}", string.Empty, StringComparison.Ordinal).Split("{1}");
        string components = string.Join(",", parts[1].Split(',', StringSplitOptions.RemoveEmptyEntries)
            .Select(component => Component(component.Split(':')[0], int.Parse(component.Split(':')[1], System.Globalization.CultureInfo.InvariantCulture))));
        Assert.Throws<EsjFormatException>(() => Load(RegistryText(Term("BT-1", parts[0], components))));
    }

    /// <summary>The component sets the specification gives the two types that have any are read.</summary>
    [Fact]
    public void TheComponentsOfAnIdentifierAndOfABinaryObjectAreRead()
    {
        Registry registry = Load(RegistryText(
            Term("BT-1", "Identifier", Component("scheme", 1) + "," + Component("schemeVersion", 0)) + ","
            + Term("BT-2", "BinaryObject", Component("mimeCode", 1) + "," + Component("filename", 1)) + ","
            + Term("BT-3", "Identifier", Component("scheme", 1) + "," + Component("schemeVersion", 1))));
        Assert.Equal(3, registry.Terms.Count);
    }

    /// <summary>An identifier a registry lists twice is refused rather than overwritten.</summary>
    [Fact]
    public void ARegistryThatListsAnIdentifierTwiceIsRefused() =>
        Assert.Throws<EsjFormatException>(() => Load(RegistryText(Term("BT-1", "Text", string.Empty) + "," + Term("BT-1", "Text", string.Empty))));

    /// <summary>
    /// An extension defines identifiers of its own namespace only (specification, sections 5.6 and
    /// 10). An identifier without a namespace belongs to a core model, so an extension that defines
    /// one is refused — when it is read, where it imports a core, and where it is combined, whether
    /// or not the core defines that identifier — and so is one whose identifiers carry two
    /// namespaces.
    /// </summary>
    [Fact]
    public void AnExtensionDefinesIdentifiersOfItsOwnNamespaceOnly()
    {
        string imports = Import("2017+A1:2019/AC:2020");
        EsjFormatException core = Assert.Throws<EsjFormatException>(() =>
            Load(RegistryText(Term("BT-1", "Text", string.Empty), imports, "Ext", "Ext 1")));
        Assert.Contains("own namespace only, and BT-1 carries none", core.Message, StringComparison.Ordinal);
        EsjFormatException unknown = Assert.Throws<EsjFormatException>(() => Load(RegistryText(
            Term("BT-999", "Text", string.Empty, "\"BG-25\"", "\"BG-25\",\"BT-999\""), imports, "Ext", "Ext 1")));
        Assert.Contains("own namespace only, and BT-999 carries none", unknown.Message, StringComparison.Ordinal);

        Registry standalone = Load(RegistryText(Term("BT-999", "Text", string.Empty), string.Empty, "Ext", "Ext 1"));
        EsjFormatException combined = Assert.Throws<EsjFormatException>(() =>
            Registry.ForEdition("2017").WithExtension(standalone));
        Assert.Contains("BT-999 carries none", combined.Message, StringComparison.Ordinal);

        EsjFormatException two = Assert.Throws<EsjFormatException>(() => Load(RegistryText(
            Term("BT-ZZZ-1", "Text", string.Empty, "\"BG-25\"", "\"BG-25\",\"BT-ZZZ-1\"") + ","
            + Term("BT-YYY-1", "Text", string.Empty, "\"BG-25\"", "\"BG-25\",\"BT-YYY-1\""), imports, "Ext", "Ext 1")));
        Assert.Contains("BT-YYY-1 carries YYY where its other identifiers carry ZZZ", two.Message, StringComparison.Ordinal);
    }

    /// <summary>
    /// An extension whose terms name an identifier neither it nor the core defines still builds on
    /// the core it places them under, so it imports that core: every identifier an extension does
    /// not define itself counts, not only those of the core (specification, section 10).
    /// </summary>
    [Fact]
    public void EveryIdentifierAnExtensionDoesNotDefineAsksForItsImports()
    {
        string below999 = Term("BT-X-1", "Text", string.Empty, "\"BG-999\"", "\"BG-999\",\"BT-X-1\"");
        EsjFormatException read = Assert.Throws<EsjFormatException>(() =>
            Load(RegistryText(below999, string.Empty, "Ext", "Ext 1")));
        Assert.Contains("names BG-999, which it does not define", read.Message, StringComparison.Ordinal);
        Registry other = Load(RegistryText(below999, "{\"model\":\"Other\",\"edition\":\"Other 1\"}", "Ext", "Ext 1"));
        EsjFormatException combined = Assert.Throws<EsjFormatException>(() =>
            Registry.ForEdition("2017").WithExtension(other));
        Assert.Contains("places its terms under BG-999", combined.Message, StringComparison.Ordinal);
    }

    private static string Import(string edition) =>
        "{\"model\":\"EN16931-1\",\"edition\":\"EN 16931-1:" + edition + "\"}";

    private static Registry BelowLines(string imports) => Load(RegistryText(
        Term("BT-X-1", "Text", string.Empty, "\"BG-25\"", "\"BG-25\",\"BT-X-1\""), imports, "Ext", "Ext 1"));

    /// <summary>
    /// An extension whose terms hang below a core group is combined only with the edition it
    /// imports: one that imports nothing, another model, or another edition is refused.
    /// </summary>
    [Fact]
    public void AnExtensionThatNamesACoreTermImportsThatCore()
    {
        Registry core2017 = Registry.ForEdition("2017");
        Assert.Equal("BT-X-1", core2017.WithExtension(BelowLines(Import("2017+A1:2019/AC:2020"))).TermOf("BT-X-1")!.Id);

        EsjFormatException nothing = Assert.Throws<EsjFormatException>(() => core2017.WithExtension(BelowLines(string.Empty)));
        Assert.Contains("BG-25", nothing.Message, StringComparison.Ordinal);
        Assert.Throws<EsjFormatException>(() => core2017.WithExtension(
            BelowLines("{\"model\":\"Other\",\"edition\":\"Other 1\"}")));
        Assert.Throws<EsjFormatException>(() => core2017.WithExtension(BelowLines(Import("2026"))));
        Assert.Throws<EsjFormatException>(() => Registry.ForEdition("2026").WithExtension(Registry.XRechnungExtension()));

        EsjFormatException reusing = Assert.Throws<EsjFormatException>(() => Load(RegistryText(
            Term("BG-X-1", "null", string.Empty).Replace("\"max\":1", "\"max\":\"n\"", StringComparison.Ordinal)
                .Replace("\"components\":[]", "\"components\":[],\"reusesTerms\":[\"BT-1\"]", StringComparison.Ordinal),
            string.Empty, "Ext", "Ext 1")));
        Assert.Contains("names BT-1, which it does not define, and imports no registry that does", reusing.Message,
            StringComparison.Ordinal);

        Registry standalone = Load(RegistryText(Term("BT-X-2", "Text", string.Empty), string.Empty, "Ext", "Ext 1"));
        Assert.NotNull(core2017.WithExtension(standalone).TermOf("BT-X-2"));
    }
}
