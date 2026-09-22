using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text.Json;

namespace En16931.SemanticJson.Model;

/// <summary>
/// The machine-readable structure of one edition of the semantic model: the identifiers, the
/// parent relation, the cardinalities, the semantic data types and the supplementary
/// components (specification, section 10).
/// </summary>
/// <remarks>
/// A registry file describes exactly one edition and a validator uses the one whose edition
/// matches the <c>semanticModel</c> of the document and no other, because a path is an
/// address relative to an edition and a term may be renumbered between two of them. A
/// registry of an extension is loaded like any other and combined with a core registry by
/// <see cref="WithExtension"/>; the combined registry also knows the additional positions an
/// extension gives a core term through its <c>reusesTerms</c> member (section 5.6).
/// </remarks>
public sealed class Registry
{
    /// <summary>The edition key everything writes unless a caller asks for another.</summary>
    public const string DefaultEdition = "2017";

    private const string XRechnungResource = "model/xrechnung/3.0.2.json";

    private const string B2cResource = "model/b2c/0.1.json";

    private static readonly string[] CoreEditionKeys = { "2017", "2026" };
    private static readonly Dictionary<string, Registry> Loaded = new(StringComparer.Ordinal);

    private readonly List<Term> _terms;
    private readonly Dictionary<string, Term> _byId;
    private readonly Dictionary<string, List<Term>> _childrenByParent;
    private readonly Dictionary<string, List<IReadOnlyList<string>>> _chainsById;
    private readonly List<RegistryImport> _imports;

    private Registry(string model, string edition, string? version, List<RegistryImport> imports, List<Term> terms)
    {
        Model = model;
        Edition = edition;
        Version = version;
        SemanticModel = Esj.SemanticModelOf(edition);
        _imports = imports;
        _terms = terms;

        _byId = new Dictionary<string, Term>(StringComparer.Ordinal);
        foreach (Term term in terms)
        {
            if (!_byId.TryAdd(term.Id, term))
            {
                throw new EsjFormatException(null, "the registry lists " + term.Id + " twice");
            }
        }

        _childrenByParent = new Dictionary<string, List<Term>>(StringComparer.Ordinal);
        foreach (Term term in terms)
        {
            Children(term.Parent ?? string.Empty).Add(term);
        }

        _chainsById = new Dictionary<string, List<IReadOnlyList<string>>>(StringComparer.Ordinal);
        foreach (Term term in terms)
        {
            ChainsOf(term.Id).Add(term.Path);
        }

        foreach (Term group in terms)
        {
            foreach (string reused in group.ReusesTerms)
            {
                if (!_byId.TryGetValue(reused, out Term? term))
                {
                    continue;
                }

                Children(group.Id).Add(term);
                List<string> chain = new(group.Path) { reused };
                ChainsOf(reused).Add(chain);
            }
        }
    }

    /// <summary>Returns the name of the model this registry describes.</summary>
    public string Model { get; }

    /// <summary>Returns the edition, in the spelling the standards body uses.</summary>
    public string Edition { get; }

    /// <summary>Returns the version of the registry file, where it carries one.</summary>
    public string? Version { get; }

    /// <summary>Returns the edition as a document writes it in <c>semanticModel</c>.</summary>
    public string SemanticModel { get; }

    /// <summary>Returns every term of this registry, in the order of the model's table.</summary>
    public IReadOnlyList<Term> Terms => _terms;

    /// <summary>Returns what an extension registry declares it builds on.</summary>
    public IReadOnlyList<RegistryImport> Imports => _imports;

    /// <summary>Returns the registry of the 2017 edition of the core model.</summary>
    /// <returns>the registry</returns>
    public static Registry En16931() => ForEdition(DefaultEdition);

    /// <summary>Returns the editions this build carries a registry for.</summary>
    /// <returns>the edition keys, in the order of the files</returns>
    public static IReadOnlyList<string> Editions()
    {
        HashSet<string> carried = new(
            typeof(Registry).GetTypeInfo().Assembly.GetManifestResourceNames(), StringComparer.Ordinal);
        return CoreEditionKeys.Where(key => carried.Contains("model/en16931/" + key + ".json")).ToList();
    }

    /// <summary>Returns the registry of one edition of the core model.</summary>
    /// <param name="edition">the edition key, for example <c>2017</c></param>
    /// <returns>the registry</returns>
    /// <exception cref="EsjFormatException">if this build carries no such edition</exception>
    public static Registry ForEdition(string edition)
    {
        ArgumentNullException.ThrowIfNull(edition);
        return Shared("model/en16931/" + edition + ".json");
    }

    /// <summary>Returns the registry a document is measured against, or <c>null</c>.</summary>
    /// <param name="semanticModel">the <c>semanticModel</c> member of a document</param>
    /// <returns>the registry of that edition, or <c>null</c> where this build carries none</returns>
    public static Registry? ForSemanticModel(string semanticModel)
    {
        ArgumentNullException.ThrowIfNull(semanticModel);
        foreach (string edition in Editions())
        {
            Registry registry = ForEdition(edition);
            if (registry.Describes(semanticModel))
            {
                return registry;
            }
        }

        return null;
    }

    /// <summary>
    /// Returns the registry of the XRechnung extension, which describes extension terms only
    /// and is meant to be combined with a core registry.
    /// </summary>
    /// <returns>the extension registry</returns>
    public static Registry XRechnungExtension() => Shared(XRechnungResource);

    /// <summary>
    /// Returns the registry of the B2C extension, which describes extension terms only and is
    /// meant to be combined with a core registry.
    /// </summary>
    /// <returns>the extension registry</returns>
    public static Registry B2cExtension() => Shared(B2cResource);

    /// <summary>Reads a registry from a stream.</summary>
    /// <param name="input">the registry file</param>
    /// <returns>the registry</returns>
    /// <exception cref="EsjFormatException">if the file is not a registry</exception>
    public static Registry Load(Stream input)
    {
        ArgumentNullException.ThrowIfNull(input);
        using JsonDocument json = JsonDocument.Parse(input);
        return Read(json.RootElement);
    }

    /// <summary>
    /// Returns this registry combined with an extension registry, which is how the terms of
    /// an extension and the positions it gives core terms become visible to a validator.
    /// </summary>
    /// <param name="extension">the registry of the extension</param>
    /// <returns>the combined registry, which keeps the model and edition of this one</returns>
    /// <exception cref="EsjFormatException">
    /// if the extension redefines a term of this registry, or imports this model with a
    /// different edition
    /// </exception>
    public Registry WithExtension(Registry extension)
    {
        ArgumentNullException.ThrowIfNull(extension);
        foreach (RegistryImport imported in extension._imports)
        {
            if (string.Equals(imported.Model, Model, StringComparison.Ordinal)
                && !string.Equals(imported.Edition, Edition, StringComparison.Ordinal))
            {
                throw new EsjFormatException(null, "the extension was written against "
                    + imported.Model + " " + imported.Edition + " and this registry describes " + Edition);
            }
        }

        List<Term> combined = new(_terms);
        foreach (Term term in extension._terms)
        {
            if (_byId.ContainsKey(term.Id))
            {
                throw new EsjFormatException(null,
                    "an extension does not redefine the core term " + term.Id);
            }

            combined.Add(term);
        }

        return new Registry(Model, Edition, Version, _imports, combined);
    }

    /// <summary>Tells whether this registry describes the edition a document names.</summary>
    /// <param name="semanticModel">the <c>semanticModel</c> member of a document</param>
    /// <returns>whether it is this edition</returns>
    public bool Describes(string semanticModel) =>
        string.Equals(SemanticModel, semanticModel, StringComparison.Ordinal);

    /// <summary>Returns the term with an identifier, or <c>null</c>.</summary>
    /// <param name="id">the identifier</param>
    /// <returns>the term, or <c>null</c> where this registry does not contain it</returns>
    public Term? TermOf(string id)
    {
        ArgumentNullException.ThrowIfNull(id);
        return _byId.TryGetValue(id, out Term? term) ? term : null;
    }

    /// <summary>Returns the children of a group, or of the root of the document.</summary>
    /// <param name="parentId">the group, or <c>null</c> for the root</param>
    /// <returns>the children, empty where the group has none</returns>
    public IReadOnlyList<Term> ChildrenOf(string? parentId) =>
        _childrenByParent.TryGetValue(parentId ?? string.Empty, out List<Term>? children)
            ? children
            : Array.Empty<Term>();

    /// <summary>Returns the terms and groups the registry places at the root.</summary>
    /// <returns>the children of the root</returns>
    public IReadOnlyList<Term> RootTerms() => ChildrenOf(null);

    /// <summary>
    /// Returns the parent chains a path may follow to reach a term: the chain the term
    /// itself records, and every chain an extension gives it through <c>reusesTerms</c>
    /// (specification, section 5.6).
    /// </summary>
    /// <param name="id">the identifier of the term</param>
    /// <returns>the chains, empty where this registry does not know the term</returns>
    public IReadOnlyList<IReadOnlyList<string>> Chains(string id)
    {
        ArgumentNullException.ThrowIfNull(id);
        return _chainsById.TryGetValue(id, out List<IReadOnlyList<string>>? chains)
            ? chains
            : Array.Empty<IReadOnlyList<string>>();
    }

    /// <summary>Tells whether a term or a group may occur more than once.</summary>
    /// <param name="id">the identifier</param>
    /// <returns>whether the registry declares it repeatable</returns>
    public bool IsRepeatable(string id) => Required(id).IsRepeatable;

    /// <summary>Returns how often a term or a group may occur under its parent.</summary>
    /// <param name="id">the identifier</param>
    /// <returns>the cardinality</returns>
    public Cardinality CardinalityOf(string id) => Required(id).Cardinality;

    /// <summary>Returns the semantic data type of a term, or <c>null</c> for a group.</summary>
    /// <param name="id">the identifier</param>
    /// <returns>the semantic data type, or <c>null</c></returns>
    public SemanticType? Datatype(string id) => TermOf(id)?.Datatype;

    /// <summary>
    /// Tells whether the registry records a group among its own children, which is what makes
    /// it recursive (specification, section 5.6).
    /// </summary>
    /// <param name="id">the identifier of the group</param>
    /// <returns>whether it may carry further instances of itself</returns>
    public bool NestsInItself(string id)
    {
        foreach (Term child in ChildrenOf(id))
        {
            if (string.Equals(child.Id, id, StringComparison.Ordinal))
            {
                return true;
            }
        }

        return false;
    }

    /// <inheritdoc />
    public override string ToString() => Edition + " with " + _terms.Count + " terms";

    private Term Required(string id) => TermOf(id)
        ?? throw new EsjFormatException(null, "the registry of " + Edition + " does not contain " + id);

    private List<Term> Children(string parentId)
    {
        if (!_childrenByParent.TryGetValue(parentId, out List<Term>? children))
        {
            children = new List<Term>();
            _childrenByParent[parentId] = children;
        }

        return children;
    }

    private List<IReadOnlyList<string>> ChainsOf(string id)
    {
        if (!_chainsById.TryGetValue(id, out List<IReadOnlyList<string>>? chains))
        {
            chains = new List<IReadOnlyList<string>>();
            _chainsById[id] = chains;
        }

        return chains;
    }

    private static Registry Shared(string resource)
    {
        lock (Loaded)
        {
            if (Loaded.TryGetValue(resource, out Registry? registry))
            {
                return registry;
            }

            using Stream stream = Resource(resource)
                ?? throw new EsjFormatException(null, "this build carries no " + resource);
            registry = Load(stream);
            Loaded[resource] = registry;
            return registry;
        }
    }

    private static Stream? Resource(string name) =>
        typeof(Registry).GetTypeInfo().Assembly.GetManifestResourceStream(name);

    private static Registry Read(JsonElement root)
    {
        string model = Text(root, "model");
        string edition = Text(root, "edition");
        string? version = root.TryGetProperty("version", out JsonElement written) ? written.GetString() : null;
        List<RegistryImport> imports = new();
        if (root.TryGetProperty("imports", out JsonElement declared))
        {
            foreach (JsonElement imported in declared.EnumerateArray())
            {
                imports.Add(new RegistryImport(Text(imported, "model"), Text(imported, "edition")));
            }
        }

        List<Term> terms = new();
        foreach (JsonElement term in root.GetProperty("terms").EnumerateArray())
        {
            terms.Add(ReadTerm(term));
        }

        return new Registry(model, edition, version, imports, terms);
    }

    private static Term ReadTerm(JsonElement json)
    {
        string id = Text(json, "id");
        bool isGroup = string.Equals(Text(json, "kind"), "BG", StringComparison.Ordinal);
        string? parent = json.GetProperty("parent").ValueKind == JsonValueKind.Null
            ? null
            : json.GetProperty("parent").GetString();
        List<string> path = json.GetProperty("path").EnumerateArray()
            .Select(step => step.GetString()!).ToList();
        int min = json.GetProperty("min").GetInt32();
        JsonElement max = json.GetProperty("max");
        int maximum = max.ValueKind == JsonValueKind.String ? Cardinality.Unbounded : max.GetInt32();
        JsonElement datatype = json.GetProperty("datatype");
        SemanticType? type = datatype.ValueKind == JsonValueKind.Null
            ? null
            : SemanticTypes.Parse(datatype.GetString()!);

        List<Component> components = new();
        if (json.TryGetProperty("components", out JsonElement listed))
        {
            foreach (JsonElement component in listed.EnumerateArray())
            {
                components.Add(new Component(
                    Component.RoleOf(Text(component, "role")),
                    Text(component, "name"),
                    component.GetProperty("min").GetInt32(),
                    component.TryGetProperty("schemeList", out JsonElement schemeList)
                        ? schemeList.GetString()
                        : null));
            }
        }

        List<string> reuses = new();
        if (json.TryGetProperty("reusesTerms", out JsonElement reused))
        {
            reuses.AddRange(reused.EnumerateArray().Select(term => term.GetString()!));
        }

        return new Term(
            id,
            isGroup,
            Text(json, "name"),
            Text(json, "slug"),
            parent,
            path,
            new Cardinality(min, maximum),
            type,
            components,
            reuses,
            json.TryGetProperty("codeList", out JsonElement codeList) ? codeList.GetString() : null);
    }

    private static string Text(JsonElement json, string member) =>
        json.TryGetProperty(member, out JsonElement value) && value.ValueKind == JsonValueKind.String
            ? value.GetString()!
            : throw new EsjFormatException(null, "a registry carries the string member " + member);
}

/// <summary>What an extension registry declares it builds on (specification, section 10).</summary>
/// <param name="Model">the name of the model</param>
/// <param name="Edition">that registry's own edition string</param>
public sealed record RegistryImport(string Model, string Edition);
