using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using BSNSoft.Esj.Model;

namespace BSNSoft.Esj.Validation;

/// <summary>
/// The structural validator: layers L2 and L3 of the specification, section 9, checked
/// against a registry.
/// </summary>
/// <remarks>
/// <para>Layer L2 looks at one path at a time: the terms exist, the group chain is one the
/// registry records, the content of the value satisfies the grammar the registry datatype of
/// its term requires, the supplementary components are allowed and present, and the index
/// rule of section 5.3 is obeyed in both directions. Layer L3 looks at the document as a
/// whole: the occurrence indices are dense and zero-based, every mandatory term and group is
/// present in every instance of its parent and at the root, and nothing occurs more often
/// than its maximum allows.</para>
/// <para>A path that layer L2 could not place takes no part in layer L3, so the two layers do
/// not report one defect twice. A path that leads through an extension segment the registry
/// does not know is reported as <c>ESJ-L2-NOT-CHECKED</c>, with the severity <c>info</c>: not
/// knowing is not a defect of the document. A core segment the registry does not know is
/// <c>ESJ-L2-UNKNOWN-TERM</c> in every path, since no extension supplies a core identifier.</para>
/// <para>A finding of layer L2 names what it is about in its subject: the identifier of the
/// segment for <c>ESJ-L2-UNKNOWN-TERM</c> and the two index codes, the term of the path for
/// <c>ESJ-L2-PARENT-CHAIN</c>, and the component for the two component codes (specification,
/// section 9.5).</para>
/// <para>What comes back is a <see cref="ValidationResult"/> and not a list of findings. This
/// validator implements two of the three layers, so its result always names L1 as not
/// evaluated and is never <c>VALID</c> on its own; <see cref="Validator.Validate(byte[], IEnumerable{Registry}?, Json.Limits?)"/>
/// runs the reader over the bytes first and composes the three layers into one verdict.</para>
/// </remarks>
public static class StructuralValidator
{
    private const int MessageExcerpt = 80;

    /// <summary>Validates a document against a registry at both layers.</summary>
    /// <param name="document">the document to check</param>
    /// <param name="registry">the registry to use where it describes the edition the document names</param>
    /// <returns>the result of layers L2 and L3, naming L1 as not evaluated</returns>
    public static ValidationResult Validate(SemanticDocument document, Registry registry) =>
        Validate(document, new[] { registry }, new[] { ValidationLayer.L2, ValidationLayer.L3 });

    /// <summary>
    /// Validates a document against the one registry of a set whose edition it names
    /// (specification, sections 4.4 and 10).
    /// </summary>
    /// <param name="document">the document to check</param>
    /// <param name="registries">the registries available, one per edition</param>
    /// <param name="layers">the layers to report, a non-empty subset of L2 and L3</param>
    /// <returns>the result of the requested layers</returns>
    /// <exception cref="ArgumentException">
    /// if no layer is asked for, or if layer L1 is, which needs the bytes of the document and
    /// belongs to the reader
    /// </exception>
    public static ValidationResult Validate(
        SemanticDocument document,
        IEnumerable<Registry> registries,
        IEnumerable<ValidationLayer> layers)
    {
        ArgumentNullException.ThrowIfNull(document);
        ArgumentNullException.ThrowIfNull(registries);
        ArgumentNullException.ThrowIfNull(layers);
        HashSet<ValidationLayer> asked = new(layers);
        CheckLayers(asked);
        Registry? registry = registries.FirstOrDefault(candidate => candidate.Describes(document.SemanticModel));
        if (registry is null)
        {
            return EditionUnknown(document, asked);
        }

        return ValidationResult.Of(
            Findings(document, registry, asked),
            NotEvaluated(asked, null),
            new[] { registry.Edition });
    }

    private static void CheckLayers(HashSet<ValidationLayer> layers)
    {
        if (layers.Count == 0)
        {
            throw new ArgumentException("a validation asks for at least one layer", nameof(layers));
        }

        if (layers.Contains(ValidationLayer.L1))
        {
            throw new ArgumentException(
                "layer L1 is decided by the bytes of a document, so it is the reader's layer",
                nameof(layers));
        }
    }

    /// <summary>
    /// Returns the result of a run that had no registry for the edition the document names.
    /// It reports the one document-level finding and nothing about any individual path,
    /// because without the registry there is nothing to measure one against.
    /// </summary>
    private static ValidationResult EditionUnknown(SemanticDocument document, HashSet<ValidationLayer> layers)
    {
        Finding finding = Finding.OfDocument(FindingCode.EditionUnknown,
            "no registry for the edition " + Esj.ForMessage(document.SemanticModel, MessageExcerpt)
            + " was available, so the model layers were not checked");
        return ValidationResult.Of(
            new[] { finding },
            NotEvaluated(layers, NotEvaluatedReason.EditionUnknown));
    }

    private static Dictionary<ValidationLayer, NotEvaluatedReason> NotEvaluated(
        HashSet<ValidationLayer> layers, NotEvaluatedReason? whenRequested)
    {
        Dictionary<ValidationLayer, NotEvaluatedReason> reasons = new()
        {
            [ValidationLayer.L1] = NotEvaluatedReason.NotRequested,
        };
        foreach (ValidationLayer layer in new[] { ValidationLayer.L2, ValidationLayer.L3 })
        {
            if (!layers.Contains(layer))
            {
                reasons[layer] = NotEvaluatedReason.NotRequested;
            }
            else if (whenRequested is not null)
            {
                reasons[layer] = whenRequested.Value;
            }
        }

        return reasons;
    }

    private static List<Finding> Findings(
        SemanticDocument document, Registry registry, HashSet<ValidationLayer> layers)
    {
        List<Finding> model = new();
        List<SemanticPath> placed = new();
        foreach (KeyValuePair<SemanticPath, SemanticValue> entry in document.Entries)
        {
            if (CheckValue(entry.Key, entry.Value, registry, model))
            {
                placed.Add(entry.Key);
            }
        }

        List<Finding> findings = new();
        if (layers.Contains(ValidationLayer.L2))
        {
            findings.AddRange(model);
        }

        if (layers.Contains(ValidationLayer.L3))
        {
            CheckCardinality(placed, registry, findings);
        }

        return findings;
    }

    /// <summary>
    /// Checks one path at layer L2 (specification, sections 5 and 9.2). Every segment is asked
    /// whether a registry knows it: a core segment the registry does not contain is
    /// <c>ESJ-L2-UNKNOWN-TERM</c> whatever else the path carries, because no extension can supply
    /// an identifier of the core model, and an extension segment the registry does not contain is
    /// <c>ESJ-L2-NOT-CHECKED</c>, whether its namespace is not loaded or is loaded and does not
    /// know it, since a namespace may grow (section 5.6); the second is reported once a path. An
    /// unknown segment ends the checks below it; the segments above it are still held to the index
    /// rule, and the findings of a path stand in the order of its segments. Where every segment is
    /// known, the index rule, the parent chain, the content grammar and the components are four
    /// checks that run independently of each other, so a path with four problems draws four
    /// findings. A finding about one segment names its identifier as the subject, a finding
    /// about a component names the component.
    /// </summary>
    /// <returns>whether the path is placed in the structure, which is what layer L3 counts</returns>
    private static bool CheckValue(
        SemanticPath path, SemanticValue value, Registry registry, List<Finding> findings)
    {
        IReadOnlyList<PathSegment> segments = path.Segments;
        bool known = true;
        bool notChecked = false;
        bool shapeIsRight = true;
        for (int at = 0; at < segments.Count; at++)
        {
            PathSegment segment = segments[at];
            if (segment.IsIndex)
            {
                continue;
            }

            if (registry.TermOf(segment.Text) is null)
            {
                known = false;
                if (!segment.IsExtension)
                {
                    findings.Add(Finding.About(path, segment.Text, FindingCode.UnknownTerm,
                        "the registry of " + registry.Edition + " does not contain " + segment.Text
                        + ", and no extension can supply an identifier of the core model"));
                }
                else if (!notChecked)
                {
                    notChecked = true;
                    findings.Add(Finding.Of(path, FindingCode.NotChecked, NotCheckedMessage(registry, segment.Text)));
                }

                continue;
            }

            if (known)
            {
                shapeIsRight = CheckIndexAt(path, at, registry, findings) && shapeIsRight;
            }
        }

        if (!known)
        {
            return false;
        }

        bool chainIsRight = RecordsChain(registry, path);
        if (!chainIsRight)
        {
            findings.Add(Finding.About(path, path.Term, FindingCode.ParentChain,
                "the group chain of this path is none the registry records for " + path.Term
                + "; the registry records " + Written(registry.Chains(path.Term))));
        }

        CheckContent(path, value, registry, findings);
        return shapeIsRight && chainIsRight;
    }

    /// <summary>
    /// Says why an extension segment was not checked: the namespace it names is not loaded at
    /// all, or it is loaded and does not know the identifier, which a later registry of the
    /// namespace may add (specification, section 5.6).
    /// </summary>
    private static string NotCheckedMessage(Registry registry, string id)
    {
        string space = Namespace(id);
        bool loaded = registry.Terms.Any(term => string.Equals(Namespace(term.Id), space, StringComparison.Ordinal));
        return loaded
            ? "the loaded registry of the namespace " + space + " does not define " + id
                + ", so this path was not checked; a later registry of the namespace may"
            : "the registry that defines " + id + " is not loaded, so this path was not checked against it";
    }

    /// <summary>Returns the namespace an extension identifier carries, or the empty string for a core one.</summary>
    private static string Namespace(string id)
    {
        int first = id.IndexOf('-', StringComparison.Ordinal);
        int last = id.LastIndexOf('-');
        return first < 0 || last <= first ? string.Empty : id.Substring(first + 1, last - first - 1);
    }

    private static string Written(IReadOnlyList<IReadOnlyList<string>> chains) =>
        "[" + string.Join(", ", chains.Select(chain => "[" + string.Join(", ", chain) + "]")) + "]";

    /// <summary>
    /// Tells whether the group chain of a path is one the registry records for its term. A
    /// group the registry records among its own children may nest, and a registry file cannot
    /// enumerate one chain per nesting level, so a path that nests such a group is measured
    /// against the chain that writes the group once (specification, section 5.6).
    /// </summary>
    private static bool RecordsChain(Registry registry, SemanticPath path)
    {
        IReadOnlyList<IReadOnlyList<string>> recorded = registry.Chains(path.Term);
        IReadOnlyList<string> ids = path.TermIds();
        return Contains(recorded, ids) || Contains(recorded, WithoutRepeatedNesting(registry, ids));
    }

    private static bool Contains(IReadOnlyList<IReadOnlyList<string>> chains, IReadOnlyList<string> ids) =>
        chains.Any(chain => chain.SequenceEqual(ids, StringComparer.Ordinal));

    private static List<string> WithoutRepeatedNesting(Registry registry, IReadOnlyList<string> ids)
    {
        List<string> collapsed = new(ids.Count);
        foreach (string id in ids)
        {
            bool repeatsNesting = collapsed.Count > 0
                && string.Equals(collapsed[collapsed.Count - 1], id, StringComparison.Ordinal)
                && registry.NestsInItself(id);
            if (!repeatsNesting)
            {
                collapsed.Add(id);
            }
        }

        return collapsed;
    }

    /// <summary>
    /// Holds one segment to the index rule of section 5.3, in both directions. The finding names
    /// the segment it is about.
    /// </summary>
    private static bool CheckIndexAt(SemanticPath path, int at, Registry registry, List<Finding> findings)
    {
        IReadOnlyList<PathSegment> segments = path.Segments;
        string id = segments[at].Text;
        bool carriesIndex = at + 1 < segments.Count && segments[at + 1].IsIndex;
        bool repeatable = registry.IsRepeatable(id);
        if (repeatable && !carriesIndex)
        {
            findings.Add(Finding.About(path, id, FindingCode.IndexRequired,
                id + " is declared " + registry.CardinalityOf(id)
                + ", so its segment is followed by an occurrence index"));
            return false;
        }

        if (!repeatable && carriesIndex)
        {
            findings.Add(Finding.About(path, id, FindingCode.IndexForbidden,
                id + " is declared " + registry.CardinalityOf(id)
                + ", so its segment is not followed by an occurrence index"));
            return false;
        }

        return true;
    }

    /// <summary>
    /// Checks the content of a value against the grammar the registry datatype of its term
    /// requires, and then its supplementary components (specification, section 6.2). The
    /// grammar is applied by the typed accessor of the value, so that this layer and a caller
    /// that reads the same value ask one question and get one answer.
    /// </summary>
    private static void CheckContent(
        SemanticPath path, SemanticValue value, Registry registry, List<Finding> findings)
    {
        Term term = registry.TermOf(path.Term)!;
        if (term.Datatype is not SemanticType datatype)
        {
            return;
        }

        try
        {
            if (datatype.IsDecimal())
            {
                value.AsDecimal();
            }
            else if (datatype == SemanticType.Date)
            {
                value.AsDate();
            }
            else if (datatype == SemanticType.Time)
            {
                value.AsTime();
            }
            else if (datatype == SemanticType.BinaryObject)
            {
                value.AsBytes();
            }
        }
        catch (EsjFormatException violation)
        {
            findings.Add(Finding.Of(path, FindingCode.Of(violation.Code!),
                term.Id + " carries the semantic data type " + datatype.RegistryDatatype()
                + ", and its content " + violation.Message));
        }

        CheckComponents(path, value, term, findings);
    }

    private static void CheckComponents(
        SemanticPath path, SemanticValue value, Term term, List<Finding> findings)
    {
        HashSet<ComponentRole> present = PresentComponents(value);
        foreach (ComponentRole role in Enum.GetValues<ComponentRole>())
        {
            if (present.Contains(role) && term.ComponentOf(role) is null)
            {
                findings.Add(Finding.About(path, Member(role), FindingCode.ComponentNotAllowed,
                    "the registry lists no " + Member(role) + " component for " + term.Id));
            }
        }

        foreach (Component component in term.Components)
        {
            if (component.IsMandatory && !present.Contains(component.Role))
            {
                findings.Add(Finding.About(path, component.JsonMember, FindingCode.ComponentMissing,
                    "the registry declares the " + component.JsonMember + " component of "
                    + term.Id + " as mandatory"));
            }
        }
    }

    private static string Member(ComponentRole role) => role switch
    {
        ComponentRole.Scheme => "scheme",
        ComponentRole.SchemeVersion => "schemeVersion",
        ComponentRole.MimeCode => "mimeCode",
        _ => "filename",
    };

    private static HashSet<ComponentRole> PresentComponents(SemanticValue value)
    {
        HashSet<ComponentRole> present = new();
        if (value.Scheme is not null)
        {
            present.Add(ComponentRole.Scheme);
        }

        if (value.SchemeVersion is not null)
        {
            present.Add(ComponentRole.SchemeVersion);
        }

        if (value.MimeCode is not null)
        {
            present.Add(ComponentRole.MimeCode);
        }

        if (value.Filename is not null)
        {
            present.Add(ComponentRole.Filename);
        }

        return present;
    }

    private static void CheckCardinality(
        List<SemanticPath> placed, Registry registry, List<Finding> findings)
    {
        SortedDictionary<SemanticPath, Instance> instances = new(SemanticPath.CanonicalOrder)
        {
            [SemanticPath.Root()] = new Instance(null),
        };

        foreach (SemanticPath path in placed)
        {
            SemanticPath enclosing = SemanticPath.Root();
            IReadOnlyList<PathSegment> segments = path.Segments;
            for (int at = 0; at < segments.Count; at++)
            {
                if (segments[at].IsIndex)
                {
                    continue;
                }

                string id = segments[at].Text;
                string index = string.Empty;
                int end = at + 1;
                if (end < segments.Count && segments[end].IsIndex)
                {
                    index = segments[end].Text;
                    end++;
                }

                instances[enclosing].Record(id, index);
                if (!segments[at].IsIndex && segments[at].Kind == TermKind.Bg)
                {
                    SemanticPath instancePath = path.Prefix(end);
                    if (!instances.ContainsKey(instancePath))
                    {
                        instances[instancePath] = new Instance(id);
                    }

                    enclosing = instancePath;
                }
            }
        }

        foreach (KeyValuePair<SemanticPath, Instance> entry in instances)
        {
            CheckInstance(entry.Key, entry.Value, registry, findings);
        }
    }

    /// <summary>
    /// Reports the cardinality findings of one group instance, or of the root of the
    /// document. Every one of them carries the path of the instance and names the term or
    /// group it is about as the finding's subject, which is where the specification,
    /// section 9.5 puts them.
    /// </summary>
    private static void CheckInstance(
        SemanticPath instancePath, Instance instance, Registry registry, List<Finding> findings)
    {
        string where = instance.TermId is null
            ? "at the root of the document"
            : "in this instance of " + instance.TermId;

        foreach (string childId in instance.Order)
        {
            SortedSet<string> indices = instance.Occurrences[childId];
            if (indices.Contains(string.Empty))
            {
                continue;
            }

            int expected = 0;
            foreach (string index in indices)
            {
                if (!string.Equals(index, expected.ToString(CultureInfo.InvariantCulture), StringComparison.Ordinal))
                {
                    findings.Add(Finding.About(instancePath, childId, FindingCode.IndexGap,
                        "the occurrences of " + childId + " " + where
                        + " are not dense and zero-based: " + index + " occurs but " + expected + " does not"));
                    break;
                }

                expected++;
            }
        }

        foreach (Term child in registry.ChildrenOf(instance.TermId))
        {
            int count = instance.Occurrences.TryGetValue(child.Id, out SortedSet<string>? indices)
                ? indices.Count
                : 0;
            if (count == 0)
            {
                if (child.IsMandatory)
                {
                    FindingCode code = child.IsGroup ? FindingCode.MissingGroup : FindingCode.MissingTerm;
                    findings.Add(Finding.About(instancePath, child.Id, code,
                        child.Id + " is declared " + child.Cardinality + " and is missing " + where));
                }
            }
            else if (count > child.Cardinality.Max)
            {
                findings.Add(Finding.About(instancePath, child.Id, FindingCode.MaxCardinality,
                    child.Id + " is declared " + child.Cardinality + " but occurs "
                    + count.ToString(CultureInfo.InvariantCulture) + " times " + where));
            }
        }
    }

    /// <summary>One instance of a business group, or the root of the document.</summary>
    private sealed class Instance
    {
        private static readonly IComparer<string> NumericDigits =
            Comparer<string>.Create(SemanticPath.CompareDigits);

        internal Instance(string? termId)
        {
            TermId = termId;
        }

        internal string? TermId { get; }

        internal Dictionary<string, SortedSet<string>> Occurrences { get; } = new(StringComparer.Ordinal);

        /// <summary>The children in the order they were first met, which orders the findings.</summary>
        internal List<string> Order { get; } = new();

        /// <summary>
        /// Records one occurrence of a child. The index stays a digit string, because the
        /// path grammar puts no bound on its length and converting it to a machine integer
        /// would fail on a document the reader accepted.
        /// </summary>
        internal void Record(string childId, string index)
        {
            if (!Occurrences.TryGetValue(childId, out SortedSet<string>? indices))
            {
                indices = new SortedSet<string>(NumericDigits);
                Occurrences[childId] = indices;
                Order.Add(childId);
            }

            indices.Add(index);
        }
    }
}
