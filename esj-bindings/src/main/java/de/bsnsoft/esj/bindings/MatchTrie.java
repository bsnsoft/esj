package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.model.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The binding table of one syntax, compiled into a tree of element steps.
 *
 * <p>A table names every term by an absolute XPath, and a document arrives one element at
 * a time. Matching each element against 196 expressions would cost the length of the
 * table per element; matching it against the children of the nodes its parent matched
 * costs the fan-out of one node. The tree is built once per syntax and shared, so the
 * cost is paid once for a process rather than once for a document.
 *
 * <p>Two XPaths that differ only in a predicate keep separate nodes, because the
 * predicate is what tells them apart: {@code cac:AllowanceCharge[cbc:ChargeIndicator =
 * 'false']} is a document level allowance and {@code cac:AllowanceCharge[cbc:ChargeIndicator
 * = 'true']} a document level charge, and an element can match either node, both, or
 * neither.
 *
 * <p>A node is marked as <em>buffered</em> where the reader cannot decide at the start tag
 * whether it matches: a predicate that asks about a child element, and a supplementary
 * component that is written as a sibling of the value rather than as an attribute of it,
 * both need the element to have been read whole. The reader holds exactly those elements
 * in memory, and {@link #bufferedPaths()} names them so that a test can state which ones
 * they are and a change to the tables that put an invoice line among them is caught.
 */
final class MatchTrie {

    private static final Map<BindingSyntax, MatchTrie> COMPILED = new HashMap<>();

    private final BindingSyntax syntax;
    private final Node root;
    private final List<String> bufferedPaths;
    private final List<List<Name>> references;

    private MatchTrie(BindingSyntax syntax,
                      Node root,
                      List<String> bufferedPaths,
                      List<List<Name>> references) {
        this.syntax = syntax;
        this.root = root;
        this.bufferedPaths = List.copyOf(bufferedPaths);
        this.references = List.copyOf(references);
    }

    /**
     * Returns the compiled tree of one syntax, building it on first use.
     *
     * @param syntax the syntax
     * @return its compiled binding table
     */
    static synchronized MatchTrie of(BindingSyntax syntax) {
        return COMPILED.computeIfAbsent(syntax, key -> compile(BindingTable.of(key)));
    }

    /**
     * Compiles one table.
     *
     * @param table the table
     * @return the tree
     */
    static MatchTrie compile(BindingTable table) {
        Node root = new Node(null, List.of());
        for (BindingTable.Entry entry : table.entries()) {
            for (BindingTable.Path path : entry.paths()) {
                add(root, entry, path);
            }
        }
        Map<String, String> prefixes = new LinkedHashMap<>();
        for (Map.Entry<String, String> namespace : table.namespaces().entrySet()) {
            prefixes.putIfAbsent(namespace.getValue(), namespace.getKey());
        }
        Set<String> buffered = new TreeSet<>();
        List<List<Name>> references = new ArrayList<>();
        finish(root, "", prefixes, buffered, references, readBack(table));
        return new MatchTrie(table.syntax(), root, List.copyOf(buffered), references);
    }

    /**
     * Returns the conventions of a table that a reader has to know, by the path of the
     * element each one is written at, with the sibling it is recognized beside resolved to
     * an expanded name.
     *
     * <p>A convention a reader has to know is one whose value would otherwise be read back
     * as a business term: the purchase order reference of a UBL document that states a
     * sales order reference and no purchase order is the one of this release. The others
     * are written at elements no business term is bound to, so no reader reads them.
     */
    private static Map<String, ReadBack> readBack(BindingTable table) {
        Map<String, ReadBack> conventions = new LinkedHashMap<>();
        for (BindingTable.Convention convention : table.conventions()) {
            if (convention.notReadBeside() == null) {
                continue;
            }
            conventions.put(convention.element(),
                    new ReadBack(convention, expanded(convention.notReadBeside(), table)));
        }
        return conventions;
    }

    /** Returns the expanded name of a prefixed one, with the namespaces of a table. */
    private static Name expanded(String prefixed, BindingTable table) {
        int colon = prefixed.indexOf(':');
        String prefix = colon < 0 ? "" : prefixed.substring(0, colon);
        String namespace = table.namespaces().get(prefix);
        if (namespace == null) {
            throw new BindingFormatException("a convention of the binding table of "
                    + table.syntax() + " names the element " + prefixed + ", whose prefix"
                    + " the table does not declare");
        }
        return new Name(namespace, prefixed.substring(colon + 1));
    }

    /** Returns the root of the tree, whose children are the root elements of the syntax. */
    Node root() {
        return root;
    }

    /** Returns the syntax this tree belongs to. */
    BindingSyntax syntax() {
        return syntax;
    }

    /**
     * Returns the XPath of every element the reader holds in memory to decide a predicate
     * or to read a component from a sibling, shortest first.
     */
    List<String> bufferedPaths() {
        return bufferedPaths;
    }

    /**
     * Returns the absolute paths of the elements a predicate compares an attribute with,
     * so that the reader can remember their content as it goes past them.
     */
    List<List<Name>> references() {
        return references;
    }

    private static void add(Node root, BindingTable.Entry entry, BindingTable.Path path) {
        Node node = root;
        for (BindingTable.Step step : path.steps()) {
            node = node.child(step);
        }
        if (path.attribute() != null) {
            node.attributeTargets.add(new AttributeTarget(entry.id(), path.attribute(),
                    entry.paths().size() > 1));
            return;
        }
        if (entry.isGroup()) {
            node.groups.add(entry.id());
            return;
        }
        node.valueTargets.add(new ValueTarget(entry.id(), path.components(), path.flags(),
                entry.paths().size() > 1));
    }

    /**
     * Works out, once the whole table is in the tree, which nodes have to be held in
     * memory and which terms are carried inside the value of another term.
     */
    private static void finish(Node node,
                               String path,
                               Map<String, String> prefixes,
                               Set<String> buffered,
                               List<List<Name>> references,
                               Map<String, ReadBack> conventions) {
        node.subjectCodeTerm = splitSubjectCode(node);
        node.valueTargets = List.copyOf(node.valueTargets);
        node.attributeTargets = List.copyOf(node.attributeTargets);
        node.groups = List.copyOf(node.groups);
        node.needsAttributes = !node.attributeTargets.isEmpty()
                || node.valueTargets.stream().anyMatch(ValueTarget::hasAttributeComponent);
        node.needsText = !node.valueTargets.isEmpty();
        for (Predicate predicate : node.predicates) {
            if (predicate.needsWholeElement()) {
                node.buffered = true;
            }
            if (predicate.kind() == Predicate.Kind.ATTRIBUTE_REFERENCE) {
                references.add(predicate.path());
            }
        }
        for (Map<String, List<Node>> axis : List.of(node.children, node.descendants)) {
            for (Map.Entry<String, List<Node>> children : axis.entrySet()) {
                for (Node child : children.getValue()) {
                    String step = path + (axis == node.descendants ? "//" : "/")
                            + qualified(child.name, prefixes);
                    if (child.valueTargets.stream()
                            .anyMatch(ValueTarget::hasParentComponent)) {
                        node.buffered = true;
                    }
                    child.convention = conventions.get(step);
                    if (child.convention != null) {
                        // The sibling the convention is recognized beside may be written
                        // after this element, so the element above is held whole.
                        node.buffered = true;
                    }
                    finish(child, step, prefixes, buffered, references, conventions);
                }
            }
        }
        if (node.buffered && node.name != null) {
            buffered.add(path);
        }
        node.key = node.name == null ? "" : node.name.key();
        node.xpath = path;
        node.selector = selectorOf(node.predicates, prefixes);
        node.absent = absentOf(node);
        List<Node> reachable = new ArrayList<>();
        for (List<Node> named : node.descendants.values()) {
            reachable.addAll(named);
        }
        node.reachable = List.copyOf(reachable);
        node.children = Collections.unmodifiableMap(node.children);
        node.descendants = Collections.unmodifiableMap(node.descendants);
    }

    /**
     * Returns what the conditions of one node read, in the spelling of the table, as one
     * list, or {@code null} where the node carries no condition at all.
     *
     * <p>A node may be selected by more than one condition — a UBL document reference is
     * told apart from the other references it shares an element with by two document type
     * codes — and a note about an element the binding did not select names every one of
     * them, because the element met none.
     */
    private static String selectorOf(List<Predicate> predicates, Map<String, String> prefixes) {
        List<String> selectors = new ArrayList<>();
        for (Predicate predicate : predicates) {
            String selector = selectorOf(predicate, prefixes);
            if (selector != null && !selectors.contains(selector)) {
                selectors.add(selector);
            }
        }
        return selectors.isEmpty() ? null : String.join(" and ", selectors);
    }

    /**
     * Returns what one condition reads, in the spelling of the table: an attribute with
     * its number sign, or the chain of child element names a condition about the content
     * below the element follows.
     */
    private static String selectorOf(Predicate predicate, Map<String, String> prefixes) {
        if (predicate == null) {
            return null;
        }
        if (predicate.attribute() != null) {
            return "@" + predicate.attribute();
        }
        StringBuilder chain = new StringBuilder();
        for (Name step : predicate.path()) {
            chain.append(chain.length() == 0 ? "" : "/").append(qualified(step, prefixes));
        }
        return chain.toString();
    }

    /**
     * Returns the business groups and terms an element this node matches would carry.
     *
     * <p>A node that opens groups is named by those groups, because the terms below one
     * are its content rather than a second loss; a node that carries values is named by
     * the terms of those values. A node that is neither — a step a condition sits on,
     * such as the party tax scheme of the UBL syntaxes — is named by the terms of the
     * subtree below it, which is where its values are written.
     */
    private static String absentOf(Node node) {
        List<String> named = new ArrayList<>(node.groups);
        if (named.isEmpty()) {
            collectTerms(node, named);
        }
        return String.join(", ", named);
    }

    private static void collectTerms(Node node, List<String> into) {
        for (ValueTarget target : node.valueTargets) {
            if (!into.contains(target.termId())) {
                into.add(target.termId());
            }
        }
        for (AttributeTarget target : node.attributeTargets) {
            if (!into.contains(target.termId())) {
                into.add(target.termId());
            }
        }
        for (Map<String, List<Node>> axis : List.of(node.children, node.descendants)) {
            for (List<Node> children : axis.values()) {
                for (Node child : children) {
                    if (child.groups.isEmpty()) {
                        collectTerms(child, into);
                    } else if (!into.containsAll(child.groups)) {
                        for (String group : child.groups) {
                            if (!into.contains(group)) {
                                into.add(group);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Returns a name as the binding table writes it, with the prefix of its namespace. */
    private static String qualified(Name name, Map<String, String> prefixes) {
        String prefix = prefixes.getOrDefault(name.namespace(), "");
        return prefix.isEmpty() ? name.localName() : prefix + ":" + name.localName();
    }

    /**
     * Returns the term whose value is written into the value of another term at the same
     * element, and takes it out of the targets of that element.
     *
     * <p>The one case of this release is the note subject code of the UBL syntax binding:
     * BT-21 and BT-22 are bound to the same {@code cbc:Note} element, and BT-21 carries
     * the flag {@code subject-code-prefix}, which says that its value stands in front of
     * the note between two number signs. Emitting it as a target of its own would store
     * the whole note as the subject code. The reader splits it out of the note instead,
     * which is a named correction rather than a binding; see {@link ReaderMode}.
     */
    private static String splitSubjectCode(Node node) {
        String prefixed = null;
        List<ValueTarget> kept = new ArrayList<>(node.valueTargets.size());
        for (ValueTarget target : node.valueTargets) {
            if (target.flags().contains(Flags.SUBJECT_CODE_PREFIX)) {
                prefixed = target.termId();
            } else {
                kept.add(target);
            }
        }
        if (prefixed == null || kept.isEmpty()) {
            return null;
        }
        node.valueTargets = kept;
        return prefixed;
    }

    /**
     * One convention of a binding table that a reader has to know, compiled.
     *
     * @param convention the convention as the table states it
     * @param beside     the expanded name of the sibling it is recognized beside
     */
    record ReadBack(BindingTable.Convention convention, Name beside) { }

    /** One node of the tree: one element step of one or more binding XPaths. */
    static final class Node {

        private final Name name;
        private final List<Predicate> predicates;
        private Map<String, List<Node>> children = new LinkedHashMap<>();
        private Map<String, List<Node>> descendants = new LinkedHashMap<>();
        private List<ValueTarget> valueTargets = new ArrayList<>();
        private List<AttributeTarget> attributeTargets = new ArrayList<>();
        private List<String> groups = new ArrayList<>();
        private String subjectCodeTerm;
        private ReadBack convention;
        private String key;
        private String xpath;
        private String selector;
        private String absent;
        private List<Node> reachable = List.of();
        private boolean buffered;
        private boolean needsAttributes;
        private boolean needsText;

        private Node(Name name, List<Predicate> predicates) {
            this.name = name;
            this.predicates = predicates;
        }

        private Node child(BindingTable.Step step) {
            Name childName = new Name(step.namespace(), step.localName());
            Map<String, List<Node>> axis = step.descendant() ? descendants : children;
            List<Node> siblings =
                    axis.computeIfAbsent(childName.key(), key -> new ArrayList<>());
            for (Node sibling : siblings) {
                if (Objects.equals(sibling.predicates, step.predicates())) {
                    return sibling;
                }
            }
            Node created = new Node(childName, step.predicates());
            siblings.add(created);
            return created;
        }

        /** Returns the children of this node that carry one expanded name, never null. */
        List<Node> childrenNamed(String key) {
            return children.getOrDefault(key, List.of());
        }

        /**
         * Returns the nodes this one reaches over the descendant axis, which stay in scope
         * for every element below this one however deeply it is written.
         */
        List<Node> reachable() {
            return reachable;
        }

        /** Returns the expanded name this node matches, as the key of the compiled maps. */
        String key() {
            return key;
        }

        /** Returns the conditions an element has to meet to match this node, all of them. */
        List<Predicate> predicates() {
            return predicates;
        }

        /**
         * Returns the convention of the binding table written at this element, or
         * {@code null} where the table states none there.
         */
        ReadBack convention() {
            return convention;
        }

        /** Returns the XPath of this element step, without the condition on it. */
        String xpath() {
            return xpath;
        }

        /**
         * Returns what the binding reads to decide about this element — an attribute
         * written with its number sign, or the chain of child element names of a
         * condition that asks about the content below it — or {@code null} where this
         * node carries no condition at all.
         */
        String selector() {
            return selector;
        }

        /**
         * Returns the business groups and terms that an element of this shape would have
         * carried, as one English list, so that a note about an element the binding did
         * not select can say what is missing from the document because of it.
         */
        String absent() {
            return absent;
        }

        /** Tells whether the reader has to hold this element whole to decide about it. */
        boolean isBuffered() {
            return buffered;
        }

        /** Tells whether the reader needs the attributes of this element. */
        boolean needsAttributes() {
            return needsAttributes;
        }

        /** Tells whether the reader needs the character content of this element. */
        boolean needsText() {
            return needsText;
        }

        /** Returns the terms whose value is the content of this element. */
        List<ValueTarget> valueTargets() {
            return valueTargets;
        }

        /** Returns the terms whose value is an attribute of this element. */
        List<AttributeTarget> attributeTargets() {
            return attributeTargets;
        }

        /** Returns the business groups one instance of which this element is. */
        List<String> groups() {
            return groups;
        }

        /**
         * Returns the term whose value is written as a prefix of the value of this
         * element, or {@code null} where no term is.
         */
        String subjectCodeTerm() {
            return subjectCodeTerm;
        }
    }

    /**
     * A term whose value is the character content of an element.
     *
     * @param termId       the term the element carries
     * @param components   where its supplementary components are written
     * @param flags        the flags of the XPath this target was built from
     * @param alternatives whether the table binds the term to more than one XPath, so
     *                     that a second element carrying it is an alternative spelling
     *                     rather than a second value
     */
    record ValueTarget(String termId,
                       List<BindingTable.ComponentBinding> components,
                       Set<String> flags,
                       boolean alternatives) {

        /** Tells whether a component of this term is an attribute of the value element. */
        boolean hasAttributeComponent() {
            return components.stream().anyMatch(component ->
                    component.xpath().startsWith("@"));
        }

        /** Tells whether a component of this term is a sibling of the value element. */
        boolean hasParentComponent() {
            return components.stream().anyMatch(component ->
                    component.xpath().startsWith("../"));
        }

        /**
         * Tells whether this term carries binary content, which a reader measures against
         * a bound of its own. The syntaxes write the media type and the file name of an
         * embedded document beside its base64, and no other term carries either.
         */
        boolean isBinary() {
            return components.stream().anyMatch(component ->
                    component.role() == Component.Role.MIME_CODE
                            || component.role() == Component.Role.FILENAME);
        }

        /** Tells whether the content of this element is stated in UNTDID 2475. */
        boolean isCodeList2475() {
            return flags.contains(Flags.CODE_LIST_2475);
        }

        /** Tells whether the content of this element is a CII eight-digit calendar date. */
        boolean isDateFormat102() {
            return flags.contains(Flags.DATE_FORMAT_102);
        }
    }

    /**
     * A term whose value is an attribute of an element.
     *
     * @param termId       the term the attribute carries
     * @param attribute    the local name of the attribute
     * @param alternatives whether the table binds the term to more than one XPath, so
     *                     that a second attribute carrying it is an alternative spelling
     *                     rather than a second value
     */
    record AttributeTarget(String termId, String attribute, boolean alternatives) { }
}
