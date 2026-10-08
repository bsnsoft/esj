using System;
using System.Collections.Generic;
using System.Globalization;

namespace BSNSoft.Esj.Model;

/// <summary>Which member of an ESJ value a supplementary component feeds.</summary>
public enum ComponentRole
{
    /// <summary>The identification scheme of an identifier.</summary>
    Scheme,

    /// <summary>The version of that identification scheme.</summary>
    SchemeVersion,

    /// <summary>The media type of an attachment.</summary>
    MimeCode,

    /// <summary>The file name of an attachment.</summary>
    Filename,
}

/// <summary>
/// A supplementary component of a semantic data type, as a registry records it for one term
/// (specification, section 6.1).
/// </summary>
/// <param name="Role">which value member the component feeds</param>
/// <param name="Name">the name the model gives the component</param>
/// <param name="Min">the minimum cardinality: 1 where the registry declares it mandatory</param>
/// <param name="SchemeList">the code list the scheme comes from, where the model fixes one</param>
public sealed record Component(ComponentRole Role, string Name, int Min, string? SchemeList)
{
    /// <summary>Returns the member of an ESJ value this component feeds.</summary>
    public string JsonMember => Role switch
    {
        ComponentRole.Scheme => "scheme",
        ComponentRole.SchemeVersion => "schemeVersion",
        ComponentRole.MimeCode => "mimeCode",
        _ => "filename",
    };

    /// <summary>Tells whether the registry declares this component mandatory.</summary>
    public bool IsMandatory => Min >= 1;

    /// <summary>Returns the role a registry writes under that name.</summary>
    /// <param name="role">the <c>role</c> member of a component</param>
    /// <returns>the role</returns>
    /// <exception cref="EsjFormatException">if no role carries that name</exception>
    public static ComponentRole RoleOf(string role) => role switch
    {
        "scheme" => ComponentRole.Scheme,
        "schemeVersion" => ComponentRole.SchemeVersion,
        "mimeCode" => ComponentRole.MimeCode,
        "filename" => ComponentRole.Filename,
        _ => throw new EsjFormatException(null,
            "the registry names the component role " + role + ", which no value member carries"),
    };
}

/// <summary>How often a term or a group may occur under its parent.</summary>
/// <param name="Min">the minimum, 0 or 1</param>
/// <param name="Max">the maximum, 1 or <see cref="Unbounded"/> where the registry writes <c>n</c></param>
public sealed record Cardinality(int Min, int Max)
{
    /// <summary>The maximum a registry writes as <c>n</c>.</summary>
    public const int Unbounded = int.MaxValue;

    /// <summary>Tells whether more than one occurrence is admitted.</summary>
    public bool IsRepeatable => Max > 1;

    /// <summary>Tells whether at least one occurrence is required.</summary>
    public bool IsMandatory => Min >= 1;

    /// <inheritdoc />
    public override string ToString() =>
        Min.ToString(CultureInfo.InvariantCulture) + ".." + (Max == Unbounded ? "n" : Max.ToString(CultureInfo.InvariantCulture));
}

/// <summary>
/// One business term or business group of the semantic model, as a registry records it
/// (specification, section 10).
/// </summary>
public sealed class Term
{
    private readonly List<string> _path;
    private readonly List<Component> _components;
    private readonly List<string> _reusesTerms;

    /// <summary>Creates a term.</summary>
    /// <param name="id">the identifier of the model, used verbatim in a semantic path</param>
    /// <param name="isGroup">whether the term is a business group</param>
    /// <param name="name">the name the model gives it</param>
    /// <param name="slug">the language-neutral name stem for code generation</param>
    /// <param name="parent">the enclosing group, or <c>null</c> at the root</param>
    /// <param name="path">the identifiers from the root down to this term</param>
    /// <param name="cardinality">how often it may occur under its parent</param>
    /// <param name="datatype">the semantic data type, or <c>null</c> for a group</param>
    /// <param name="components">the supplementary components the registry lists</param>
    /// <param name="reusesTerms">terms an extension group carries one level deeper</param>
    /// <param name="codeList">the code list a code term draws from, without a version</param>
    public Term(
        string id,
        bool isGroup,
        string name,
        string slug,
        string? parent,
        IEnumerable<string> path,
        Cardinality cardinality,
        SemanticType? datatype,
        IEnumerable<Component> components,
        IEnumerable<string> reusesTerms,
        string? codeList)
    {
        Id = id ?? throw new ArgumentNullException(nameof(id));
        List<Component> listed = new(components ?? throw new ArgumentNullException(nameof(components)));
        CheckComponents(id, datatype, listed);
        IsGroup = isGroup;
        Name = name;
        Slug = slug;
        Parent = parent;
        _path = new List<string>(path);
        Cardinality = cardinality;
        Datatype = datatype;
        _components = listed;
        _reusesTerms = new List<string>(reusesTerms);
        CodeList = codeList;
    }

    /// <summary>Returns the identifier of the model.</summary>
    public string Id { get; }

    /// <summary>Tells whether this is a business group.</summary>
    public bool IsGroup { get; }

    /// <summary>Returns the name the model gives the term.</summary>
    public string Name { get; }

    /// <summary>Returns the language-neutral name stem for code generation.</summary>
    public string Slug { get; }

    /// <summary>Returns the enclosing group, or <c>null</c> at the root of the document.</summary>
    public string? Parent { get; }

    /// <summary>Returns the identifiers from the root down to and including this term.</summary>
    public IReadOnlyList<string> Path => _path;

    /// <summary>Returns how often the term may occur under its parent.</summary>
    public Cardinality Cardinality { get; }

    /// <summary>Returns the semantic data type, or <c>null</c> for a group.</summary>
    public SemanticType? Datatype { get; }

    /// <summary>Returns the supplementary components the registry lists for the term.</summary>
    public IReadOnlyList<Component> Components => _components;

    /// <summary>Returns the terms an extension group carries one level deeper.</summary>
    public IReadOnlyList<string> ReusesTerms => _reusesTerms;

    /// <summary>Returns the code list the term draws from, or <c>null</c>.</summary>
    public string? CodeList { get; }

    /// <summary>Tells whether the term may occur more than once.</summary>
    public bool IsRepeatable => Cardinality.IsRepeatable;

    /// <summary>Tells whether at least one occurrence is required.</summary>
    public bool IsMandatory => Cardinality.IsMandatory;

    /// <summary>Returns the component of a role, or <c>null</c> where the registry lists none.</summary>
    /// <param name="role">the role</param>
    /// <returns>the component, or <c>null</c></returns>
    public Component? ComponentOf(ComponentRole role)
    {
        foreach (Component component in _components)
        {
            if (component.Role == role)
            {
                return component;
            }
        }

        return null;
    }

    /// <inheritdoc />
    public override string ToString() => Id + " " + Cardinality;

    /// <summary>
    /// Holds the components of a term to the three rules of the specification, section 10,
    /// which a registry is checked against when it is read rather than left to the documents
    /// measured with it: a component is listed once and only where the semantic data type has
    /// it; a <c>BinaryObject</c> carries exactly <c>mimeCode</c> and <c>filename</c>, both
    /// mandatory; <c>schemeVersion</c> is declared only beside <c>scheme</c>, and mandatory only
    /// beside a <c>scheme</c> that is mandatory too, since no value could satisfy the other way
    /// round. A term no value can satisfy is a defect of the registry.
    /// </summary>
    /// <exception cref="EsjFormatException">if the components break one of the rules</exception>
    private static void CheckComponents(string id, SemanticType? datatype, List<Component> components)
    {
        HashSet<ComponentRole> roles = new();
        foreach (Component component in components)
        {
            if (!roles.Add(component.Role))
            {
                throw new EsjFormatException(null, id + " lists the component " + component.JsonMember + " twice");
            }
        }

        if (datatype == SemanticType.BinaryObject)
        {
            if (roles.Count != 2 || !roles.Contains(ComponentRole.MimeCode) || !roles.Contains(ComponentRole.Filename))
            {
                throw new EsjFormatException(null, "the Binary Object " + id + " carries the components mimeCode and filename");
            }

            foreach (Component component in components)
            {
                if (!component.IsMandatory)
                {
                    throw new EsjFormatException(null,
                        "the component " + component.JsonMember + " of the Binary Object " + id + " is mandatory");
                }
            }

            return;
        }

        if (components.Count == 0)
        {
            return;
        }

        if (datatype is null)
        {
            throw new EsjFormatException(null, id + " carries supplementary components but no semantic data type");
        }

        foreach (ComponentRole role in roles)
        {
            bool allowed = datatype == SemanticType.Identifier
                && role is ComponentRole.Scheme or ComponentRole.SchemeVersion;
            if (!allowed)
            {
                throw new EsjFormatException(null, "the semantic data type " + datatype.Value.RegistryDatatype()
                    + " of " + id + " has no component " + MemberOf(role));
            }
        }

        if (roles.Contains(ComponentRole.SchemeVersion) && !roles.Contains(ComponentRole.Scheme))
        {
            throw new EsjFormatException(null, id + " carries a scheme version without a scheme");
        }

        if (Mandatory(components, ComponentRole.SchemeVersion) && !Mandatory(components, ComponentRole.Scheme))
        {
            throw new EsjFormatException(null, id + " declares its scheme version mandatory and its scheme"
                + " optional, and no value can satisfy both");
        }
    }

    private static string MemberOf(ComponentRole role) => role switch
    {
        ComponentRole.Scheme => "scheme",
        ComponentRole.SchemeVersion => "schemeVersion",
        ComponentRole.MimeCode => "mimeCode",
        _ => "filename",
    };

    private static bool Mandatory(List<Component> components, ComponentRole role) =>
        components.Exists(component => component.Role == role && component.IsMandatory);
}
