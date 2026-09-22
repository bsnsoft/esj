package de.bsnsoft.esj.validate;

import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.PathSegment;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.TermKind;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The structural validator: layers L2 and L3 of the specification, section 9, checked
 * against a registry.
 *
 * <p>Layer L2 looks at one path at a time: the terms exist, the group chain is one the
 * registry records, the content of the value satisfies the grammar the registry datatype
 * of its term requires, the supplementary components are allowed and present, and the
 * index rule of section 5.3 is obeyed in both directions. A group the registry records
 * inside itself may nest, and the chain that writes that group once covers every depth of
 * it. Layer L3 looks at the document as a whole: the occurrence indices are dense and
 * zero-based, every mandatory term and group is present in every instance of its parent
 * and at the root, and nothing occurs more often than its maximum allows.
 *
 * <p>The content grammars are this layer's work and not the reader's, because it takes the
 * registry to know that BT-106 carries a decimal and BT-2 a date (specification,
 * section 6.2). A content that does not satisfy the one its term requires is
 * <em>reported</em> and never repaired: {@code 100.00} at a decimal term is
 * {@code ESJ-L2-DECIMAL}, and no stage of this implementation turns it into {@code 100} on
 * the way through (section 6.4).
 *
 * <p>A validator uses the registry whose edition matches the {@code semanticModel} member
 * of the document and no other, because a path is an address relative to an edition
 * (sections 4.4 and 10): the same {@code /BT-1} under two editions may be two different
 * business terms. Every entry point makes that choice itself, from the one registry or
 * from the set it is handed. Where none of them describes that edition, the validator
 * decides nothing: it reports {@link FindingCode#ESJ_L2_EDITION_UNKNOWN} once at document
 * level, evaluates neither L2 nor L3, and returns the status
 * {@link ValidationStatus#INDETERMINATE}. Which editions an implementation holds a
 * registry for is a property of the implementation, and this specification does not let it
 * decide what a document is (section 4.4).
 *
 * <p><strong>What comes back is a {@link ValidationResult} and not a list of findings.</strong>
 * This validator implements two of the three layers, so its result always names L1 as not
 * evaluated and is therefore never {@code VALID} on its own (section 3.5). A caller that
 * has also run L1 — a reader has, and
 * {@code de.bsnsoft.esj.json.ReadResult#validation()} says so — composes the
 * two with {@link ValidationResult#merge(ValidationResult)} and reads the verdict off the
 * composed result.
 *
 * <p>A path that layer L2 could not place — because a term is unknown, because the chain
 * is not one the registry records, or because the index rule is broken — takes no part in
 * layer L3. Layer L3 would otherwise report a second, derived problem for every path whose
 * position is already in doubt.
 *
 * <p>A path that leads through an extension whose registry is not loaded is reported as
 * {@link FindingCode#ESJ_L2_NOT_CHECKED}, with the severity {@code info}: not knowing is
 * not a defect of the document (specification, section 5.6).
 */
public final class StructuralValidator {

    private static final Comparator<String> NUMERIC_DIGITS = StructuralValidator::compareDigits;

    /** The longest fragment of document content a message reproduces (section 12.6). */
    private static final int MESSAGE_EXCERPT = 80;

    private StructuralValidator() {
        throw new AssertionError("no instances");
    }

    /**
     * Validates a document against a registry at both layers this validator implements.
     *
     * @param document the document to check
     * @param registry the registry to use where it describes the edition the document
     *                 names; combine the core registry with an extension registry to have
     *                 extension terms checked too
     * @return the result of layers L2 and L3, naming L1 as not evaluated
     * @throws NullPointerException if an argument is {@code null}
     */
    public static ValidationResult validate(SemanticDocument document, Registry registry) {
        return validate(document, registry, EnumSet.of(ValidationLayer.L2, ValidationLayer.L3));
    }

    /**
     * Validates a document against a registry at the layers the caller asks for, which
     * the specification, section 3.5 requires to be individually selectable.
     *
     * <p>Layer L3 can be asked for alone. It still needs to know which paths layer L2
     * could place, because a path whose position is in doubt takes no part in the
     * cardinality layer; that placement is then done silently, and no L2 finding is
     * returned. Asking for L2 alone returns the model findings and nothing else.
     *
     * @param document the document to check
     * @param registry the registry to use where it describes the edition the document
     *                 names; combine the core registry with an extension registry to have
     *                 extension terms checked too
     * @param layers   the layers to report, a non-empty subset of {@code L2} and
     *                 {@code L3}
     * @return the result: the findings in the order they were met — the model layer path
     *         by path in canonical path order, then the cardinality layer instance by
     *         instance — and the coverage of the run
     * @throws IllegalArgumentException if {@code layers} is empty or if it names layer
     *                                  L1, which needs the bytes of the document and
     *                                  belongs to the reader
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static ValidationResult validate(SemanticDocument document,
                                            Registry registry,
                                            Set<ValidationLayer> layers) {
        Objects.requireNonNull(registry, "registry");
        return validate(document, List.of(registry), layers);
    }

    /**
     * Validates a document against the one registry of a set whose edition it names
     * (specification, sections 4.4 and 10).
     *
     * <p>This is the form a program with more than one registry uses. The choice is not a
     * convenience: a validator must not measure a document against a registry of another
     * edition, because the same {@code /BT-1} under two editions may be two different
     * business terms.
     *
     * <p>Where none of them describes that edition the validator evaluates nothing: the
     * result carries {@link FindingCode#ESJ_L2_EDITION_UNKNOWN} at document level, names
     * both model layers as not evaluated for the reason
     * {@link NotEvaluatedReason#EDITION_UNKNOWN}, and has the status
     * {@link ValidationStatus#INDETERMINATE}. That is not an error of the call and not a
     * defect of the document: an implementation may meet a document of an edition
     * published after it, and it then says so instead of guessing in either direction
     * (specification, sections 3.1, 4.4 and 9.2).
     *
     * @param document   the document to check
     * @param registries the registries available, one per edition; a core registry
     *                   combined with an extension registry counts as the edition of the
     *                   core one
     * @param layers     the layers to report, a non-empty subset of {@code L2} and
     *                   {@code L3}
     * @return the result of the requested layers
     * @throws IllegalArgumentException if {@code layers} is empty or if it names layer L1
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static ValidationResult validate(SemanticDocument document,
                                            Collection<Registry> registries,
                                            Set<ValidationLayer> layers) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(layers, "layers");
        checkLayers(layers);
        Registry registry = select(registries, document.semanticModel());
        if (registry == null) {
            return editionUnknown(document, layers);
        }
        return ValidationResult.of(findings(document, registry, layers),
                notEvaluated(layers, null),
                List.of(registry.edition()));
    }

    /** Returns the registry that describes the edition, or {@code null} if there is none. */
    private static Registry select(Collection<Registry> registries, String semanticModel) {
        for (Registry registry : registries) {
            if (registry.describes(semanticModel)) {
                return registry;
            }
        }
        return null;
    }

    /**
     * Returns the result of a run that had no registry for the edition the document
     * names (specification, sections 4.4 and 9.2). It reports the one document-level
     * finding and nothing about any individual path, because without the registry there
     * is nothing to measure one against.
     */
    private static ValidationResult editionUnknown(SemanticDocument document,
                                                   Set<ValidationLayer> layers) {
        Finding finding = Finding.ofDocument(FindingCode.ESJ_L2_EDITION_UNKNOWN,
                "no registry for the edition "
                        + Esj.forMessage(document.semanticModel(), MESSAGE_EXCERPT)
                        + " was available, so the model layers were not checked");
        return ValidationResult.of(List.of(finding),
                notEvaluated(layers, NotEvaluatedReason.EDITION_UNKNOWN),
                List.of());
    }

    /**
     * Names the layers this run did not evaluate. Layer L1 is decided by the bytes of a
     * document and belongs to the reader, so this validator never evaluates it; a model
     * layer the caller left out was not requested, and one it asked for is unevaluated
     * only where {@code whenRequested} says why.
     */
    private static Map<ValidationLayer, NotEvaluatedReason> notEvaluated(
            Set<ValidationLayer> layers, NotEvaluatedReason whenRequested) {
        Map<ValidationLayer, NotEvaluatedReason> reasons = new EnumMap<>(ValidationLayer.class);
        reasons.put(ValidationLayer.L1, NotEvaluatedReason.NOT_REQUESTED);
        for (ValidationLayer layer : List.of(ValidationLayer.L2, ValidationLayer.L3)) {
            if (!layers.contains(layer)) {
                reasons.put(layer, NotEvaluatedReason.NOT_REQUESTED);
            } else if (whenRequested != null) {
                reasons.put(layer, whenRequested);
            }
        }
        return reasons;
    }

    /**
     * Validates a document against a registry known to describe the edition it names.
     *
     * @return the findings of the requested layers, in the order they were met
     */
    private static List<Finding> findings(SemanticDocument document,
                                          Registry registry,
                                          Set<ValidationLayer> layers) {
        List<Finding> model = new ArrayList<>();
        List<SemanticPath> placed = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            if (checkValue(entry.getKey(), entry.getValue(), registry, model)) {
                placed.add(entry.getKey());
            }
        }
        List<Finding> findings = new ArrayList<>();
        if (layers.contains(ValidationLayer.L2)) {
            findings.addAll(model);
        }
        if (layers.contains(ValidationLayer.L3)) {
            checkCardinality(placed, registry, findings);
        }
        return List.copyOf(findings);
    }

    private static void checkLayers(Set<ValidationLayer> layers) {
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("a validation asks for at least one layer");
        }
        if (layers.contains(ValidationLayer.L1)) {
            throw new IllegalArgumentException("layer L1 is decided by the bytes of a document,"
                    + " so it is the reader's layer and not this validator's");
        }
    }

    private static boolean checkValue(SemanticPath path,
                                      SemanticValue value,
                                      Registry registry,
                                      List<Finding> findings) {
        for (PathSegment segment : path.segments()) {
            if (segment instanceof PathSegment.Term term
                    && term.isExtension()
                    && registry.term(term.id()).isEmpty()) {
                findings.add(Finding.of(path, FindingCode.ESJ_L2_NOT_CHECKED,
                        "the registry that defines " + term.id() + " is not loaded,"
                                + " so this path was not checked against it"));
                return false;
            }
        }
        boolean known = true;
        for (PathSegment segment : path.segments()) {
            if (segment instanceof PathSegment.Term term && registry.term(term.id()).isEmpty()) {
                findings.add(Finding.of(path, FindingCode.ESJ_L2_UNKNOWN_TERM,
                        "the registry of " + registry.edition() + " does not contain " + term.id()));
                known = false;
            }
        }
        if (!known) {
            return false;
        }

        boolean shapeIsRight = checkIndexRule(path, registry, findings);
        boolean chainIsRight = recordsChain(registry, path);
        if (!chainIsRight) {
            findings.add(Finding.of(path, FindingCode.ESJ_L2_PARENT_CHAIN,
                    "the group chain of this path is none the registry records for "
                            + path.term() + "; the registry records " + registry.chains(path.term())));
        }
        checkContent(path, value, registry, findings);
        return shapeIsRight && chainIsRight;
    }

    /**
     * Tells whether the group chain of a path is one the registry records for its term.
     *
     * <p>A group the registry records among its own children may nest, and a registry
     * file cannot enumerate one chain per nesting level. A path that nests such a group
     * is therefore measured against the chain that writes the group once (specification,
     * section 5.6): the occurrences below the first carry the same children in the same
     * positions as the first, so the chain says the same thing about all of them.
     */
    private static boolean recordsChain(Registry registry, SemanticPath path) {
        List<List<String>> recorded = registry.chains(path.term());
        List<String> ids = path.termIds();
        return recorded.contains(ids) || recorded.contains(withoutRepeatedNesting(registry, ids));
    }

    /**
     * Returns a chain in which a group the registry records inside itself stands once
     * instead of once per nesting level.
     */
    private static List<String> withoutRepeatedNesting(Registry registry, List<String> ids) {
        List<String> collapsed = new ArrayList<>(ids.size());
        for (String id : ids) {
            boolean repeatsNesting = !collapsed.isEmpty()
                    && collapsed.get(collapsed.size() - 1).equals(id)
                    && nestsInItself(registry, id);
            if (!repeatsNesting) {
                collapsed.add(id);
            }
        }
        return collapsed;
    }

    /** Tells whether the registry records a group among its own children. */
    private static boolean nestsInItself(Registry registry, String id) {
        return registry.children(id).stream().anyMatch(child -> child.id().equals(id));
    }

    private static boolean checkIndexRule(SemanticPath path, Registry registry, List<Finding> findings) {
        List<PathSegment> segments = path.segments();
        boolean right = true;
        for (int i = 0; i < segments.size(); i++) {
            if (!(segments.get(i) instanceof PathSegment.Term term)) {
                continue;
            }
            boolean carriesIndex = i + 1 < segments.size() && segments.get(i + 1) instanceof PathSegment.Index;
            boolean repeatable = registry.isRepeatable(term.id());
            if (repeatable && !carriesIndex) {
                findings.add(Finding.of(path, FindingCode.ESJ_L2_INDEX_REQUIRED,
                        term.id() + " is declared " + registry.cardinality(term.id())
                                + ", so its segment is followed by an occurrence index"));
                right = false;
            } else if (!repeatable && carriesIndex) {
                findings.add(Finding.of(path, FindingCode.ESJ_L2_INDEX_FORBIDDEN,
                        term.id() + " is declared " + registry.cardinality(term.id())
                                + ", so its segment is not followed by an occurrence index"));
                right = false;
            }
        }
        return right;
    }

    /**
     * Checks the content of a value against the grammar the registry datatype of its term
     * requires, and then its supplementary components (specification, section 6.2).
     *
     * <p>The grammar is applied by the typed accessor of the value, so that this layer and
     * a caller that reads the same value ask one question and get one answer. The accessor
     * signals a violation with the finding code of section 9.6 and a message that names
     * the clause that was broken; both are taken over as they stand.
     */
    private static void checkContent(SemanticPath path,
                                     SemanticValue value,
                                     Registry registry,
                                     List<Finding> findings) {
        Term term = registry.term(path.term()).orElseThrow();
        Optional<SemanticType> declared = term.datatype();
        if (declared.isEmpty()) {
            return;
        }
        SemanticType datatype = declared.get();
        try {
            if (datatype.isDecimal()) {
                value.asDecimal();
            } else if (datatype == SemanticType.DATE) {
                value.asDate();
            } else if (datatype == SemanticType.TIME) {
                value.asTime();
            } else if (datatype == SemanticType.BINARY_OBJECT) {
                value.asBytes();
            }
        } catch (EsjFormatException violation) {
            findings.add(Finding.of(path, violation.code().orElseThrow(),
                    term.id() + " carries the semantic data type " + datatype.registryDatatype()
                            + ", and its content " + violation.getMessage()));
        }
        checkComponents(path, value, term, findings);
    }

    private static void checkComponents(SemanticPath path,
                                        SemanticValue value,
                                        Term term,
                                        List<Finding> findings) {
        Set<Component.Role> present = presentComponents(value);
        for (Component.Role role : present) {
            if (term.component(role).isEmpty()) {
                findings.add(Finding.of(path, FindingCode.ESJ_L2_COMPONENT_NOT_ALLOWED,
                        "the registry lists no " + role.jsonMember() + " component for " + term.id()));
            }
        }
        for (Component component : term.components()) {
            if (component.isMandatory() && !present.contains(component.role())) {
                findings.add(Finding.of(path, FindingCode.ESJ_L2_COMPONENT_MISSING,
                        "the registry declares the " + component.role().jsonMember()
                                + " component of " + term.id() + " as mandatory"));
            }
        }
    }

    private static Set<Component.Role> presentComponents(SemanticValue value) {
        Set<Component.Role> present = EnumSet.noneOf(Component.Role.class);
        if (value.scheme() != null) {
            present.add(Component.Role.SCHEME);
        }
        if (value.schemeVersion() != null) {
            present.add(Component.Role.SCHEME_VERSION);
        }
        if (value.mimeCode() != null) {
            present.add(Component.Role.MIME_CODE);
        }
        if (value.filename() != null) {
            present.add(Component.Role.FILENAME);
        }
        return present;
    }

    private static void checkCardinality(List<SemanticPath> placed,
                                         Registry registry,
                                         List<Finding> findings) {
        Map<SemanticPath, Instance> instances = new TreeMap<>(SemanticPath.canonicalOrder());
        instances.put(SemanticPath.root(), new Instance(null));
        for (SemanticPath path : placed) {
            SemanticPath enclosing = SemanticPath.root();
            List<PathSegment> segments = path.segments();
            for (int i = 0; i < segments.size(); i++) {
                if (!(segments.get(i) instanceof PathSegment.Term term)) {
                    continue;
                }
                String index = "";
                int end = i + 1;
                if (end < segments.size() && segments.get(end) instanceof PathSegment.Index segment) {
                    index = segment.digits();
                    end++;
                }
                instances.get(enclosing).record(term.id(), index);
                if (term.kind() == TermKind.BG) {
                    SemanticPath instancePath = path.prefix(end);
                    instances.computeIfAbsent(instancePath, key -> new Instance(term.id()));
                    enclosing = instancePath;
                }
            }
        }
        for (Map.Entry<SemanticPath, Instance> entry : instances.entrySet()) {
            checkInstance(entry.getKey(), entry.getValue(), registry, findings);
        }
    }

    /**
     * Reports the cardinality findings of one group instance, or of the root of the
     * document.
     *
     * <p>Every one of them carries the path of the instance and names the term or group
     * it is about as the finding's subject, which is where the specification,
     * section 9.5 puts them: a term that is missing has no path of its own, and the
     * instance that lacks it is what its author has to go to.
     */
    private static void checkInstance(SemanticPath instancePath,
                                      Instance instance,
                                      Registry registry,
                                      List<Finding> findings) {
        String where = instance.termId == null
                ? "at the root of the document"
                : "in this instance of " + instance.termId;
        instance.occurrences.forEach((childId, indices) -> {
            if (indices.contains("")) {
                return;
            }
            int expected = 0;
            for (String index : indices) {
                if (!index.equals(Integer.toString(expected))) {
                    findings.add(Finding.about(instancePath, childId,
                            FindingCode.ESJ_L3_INDEX_GAP,
                            "the occurrences of " + childId + " " + where
                                    + " are not dense and zero-based: " + index
                                    + " occurs but " + expected + " does not"));
                    return;
                }
                expected++;
            }
        });
        for (Term child : registry.children(instance.termId)) {
            SortedSet<String> indices = instance.occurrences.get(child.id());
            int count = indices == null ? 0 : indices.size();
            if (count == 0) {
                if (child.isMandatory()) {
                    FindingCode code = child.isGroup()
                            ? FindingCode.ESJ_L3_MISSING_GROUP
                            : FindingCode.ESJ_L3_MISSING_TERM;
                    findings.add(Finding.about(instancePath, child.id(), code,
                            child.id() + " is declared " + child.cardinality()
                                    + " and is missing " + where));
                }
            } else if (count > child.cardinality().max()) {
                findings.add(Finding.about(instancePath, child.id(),
                        FindingCode.ESJ_L3_MAX_CARDINALITY,
                        child.id() + " is declared " + child.cardinality() + " but occurs "
                                + count + " times " + where));
            }
        }
    }

    private static int compareDigits(String left, String right) {
        if (left.length() != right.length()) {
            return Integer.compare(left.length(), right.length());
        }
        return left.compareTo(right);
    }

    /** One instance of a business group, or the root of the document. */
    private static final class Instance {

        private final String termId;
        private final Map<String, SortedSet<String>> occurrences = new LinkedHashMap<>();

        Instance(String termId) {
            this.termId = termId;
        }

        /**
         * Records one occurrence of a child. The index stays a digit string, because the
         * path grammar of the specification, section 5.1 puts no bound on its length and
         * converting it to an integer would fail on a document the reader accepted.
         */
        void record(String childId, String index) {
            occurrences.computeIfAbsent(childId, key -> new TreeSet<>(NUMERIC_DIGITS)).add(index);
        }
    }
}
