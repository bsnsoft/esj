package de.bsnsoft.esj.bindings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One element of the source document, held whole.
 *
 * <p>The reader holds an element only where it cannot decide at the start tag whether the
 * element matches: a predicate that asks about a child element, or a supplementary
 * component written as a sibling of the value rather than as an attribute of it. Those
 * elements are small and there are few of them — a document reference, an allowance, a
 * tax subtotal — and the compiled table names every one of them, so the claim that an
 * invoice line is never among them is checked rather than asserted.
 *
 * <p>The character content of an element is kept as one string and its child elements as
 * a list, which means a replay puts all of an element's own text before all of its
 * children. For a value element, which is a leaf, that is the same document; for an
 * element with children, the text is whitespace between tags. Neither syntax writes mixed
 * content, and neither binds a term to it.
 */
final class Buffered {

    private final String namespace;
    private final String localName;
    private final Map<String, String> attributes;
    private final List<Buffered> children = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private Buffered parent;
    private boolean overlong;

    Buffered(String namespace, String localName, Map<String, String> attributes) {
        this.namespace = namespace;
        this.localName = localName;
        this.attributes = attributes == null ? Map.of() : new LinkedHashMap<>(attributes);
    }

    /** Returns the namespace of this element, empty where it has none. */
    String namespace() {
        return namespace;
    }

    /** Returns the local name of this element. */
    String localName() {
        return localName;
    }

    /** Returns the attributes of this element by local name. */
    Map<String, String> attributes() {
        return attributes;
    }

    /** Returns the child elements of this element, in document order. */
    List<Buffered> children() {
        return children;
    }

    /** Returns the character content of this element, its child elements excluded. */
    String text() {
        return text.toString();
    }

    /** Returns the characters held of this element, its child elements excluded. */
    int textLength() {
        return text.length();
    }

    /**
     * Tells whether the character content of this element passed the bound on a single
     * value, so that nothing of it is held and the term it carries is reported instead.
     */
    boolean isOverlong() {
        return overlong;
    }

    /** Drops what is held of the character content and marks the element as too long. */
    void markOverlong() {
        overlong = true;
        text.setLength(0);
    }

    /** Returns the element this one is written inside, or {@code null} for the outermost. */
    Buffered parent() {
        return parent;
    }

    /** Adds a child element. */
    void add(Buffered child) {
        child.parent = this;
        children.add(child);
    }

    /** Adds character content. */
    void append(String characters) {
        text.append(characters);
    }

    /**
     * Returns the character content of the first descendant a path of names leads to, or
     * {@code null} where the path leads nowhere.
     */
    String contentAt(List<Name> path) {
        Buffered node = this;
        for (Name step : path) {
            node = node.child(step);
            if (node == null) {
                return null;
            }
        }
        return node.text();
    }

    /** Returns the first child with an expanded name, or {@code null} where there is none. */
    Buffered child(Name name) {
        for (Buffered child : children) {
            if (name.matches(child.namespace, child.localName)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Tells whether this element meets a predicate that asks about a child element.
     *
     * <p>A comparison holds where the path leads to an element whose content, with the
     * whitespace around it removed, is the string the predicate names. A negated
     * comparison holds where it does not — including where the path leads nowhere, which
     * is what {@code !=} means over an empty node set in the tables this reads.
     */
    boolean matches(Predicate predicate) {
        String content = contentAt(predicate.path());
        boolean equal = content != null && content.strip().equals(predicate.literal());
        return predicate.kind() == Predicate.Kind.CHILD_EQUALS ? equal : !equal;
    }
}
