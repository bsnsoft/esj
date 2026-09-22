package de.bsnsoft.esj.bindings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One element of the document the writer builds, with the children it has gathered so far.
 *
 * <p>The tree exists because the order a document is written in is not the order the values
 * arrive in. Values arrive in the canonical path order of the semantic model; a document of
 * this syntax is written in the order the schema declares its elements, and the two are
 * different orders over different things. So the writer places every value where it belongs
 * and the tree is put into the schema's order once, when it is serialized.
 *
 * <p>Two numbers decide where a child stands among its siblings: the position the schema
 * gives its name, and the order it was created in. The first is the order the schema asks
 * for; the second decides between two children of the same name — two tax breakdowns, two
 * invoice lines — and it follows the canonical order of the semantic paths that produced
 * them, so two runs over the same document write the same bytes.
 *
 * <p>Children of the same name are kept together under the occurrence they belong to, and
 * the writer asks for the ones of an occurrence rather than for one child: where a syntax
 * writes two elements of a name that the model keeps apart — a document reference of one
 * type code and one of another — it is the condition on the element and not its name that
 * tells them apart, and that is a question the caller answers.
 */
final class XmlElement {

    /** Siblings stand in the schema's order, and equal names in the order they were made. */
    private static final Comparator<XmlElement> ORDER =
            Comparator.comparingInt((XmlElement e) -> e.order).thenComparingInt(e -> e.created);

    private final String name;
    private final String type;
    private final int order;
    private final int created;
    private XmlElement parent;
    private String owner;
    private Map<String, String> attributes;
    private Map<String, String> references;
    private Map<String, Set<String>> excluded;
    private Map<String, List<XmlElement>> occurrences;
    private List<XmlElement> children;
    private String text;
    private String subjectCode;

    /**
     * Creates an element.
     *
     * @param name    the prefixed name the document writes
     * @param type    the name of its schema type, or {@code null} where the schema gives it
     *                none this table carries
     * @param order   its position in the content model of the type it stands in
     * @param created how many elements the writer had made when it made this one
     */
    XmlElement(String name, String type, int order, int created) {
        this.name = name;
        this.type = type;
        this.order = order;
        this.created = created;
    }

    /** Returns the prefixed name this element is written with. */
    String name() {
        return name;
    }

    /** Returns the name of the schema type of this element, or {@code null}. */
    String type() {
        return type;
    }

    /**
     * Returns the element this one hangs off, or {@code null} for the document element.
     *
     * <p>A supplementary component is written relative to the value it belongs to, and this
     * syntax puts one of them on a sibling of that value rather than on the value itself,
     * so the walk has to be able to step back out.
     */
    XmlElement parent() {
        return parent;
    }

    /**
     * Returns the children of one name that belong to one occurrence, in the order they
     * were made.
     *
     * @param name     the prefixed name
     * @param instance the occurrence, or {@code -1} where the name stands for one element
     * @return the children, empty where there are none
     */
    List<XmlElement> occurrence(String name, int instance) {
        if (occurrences == null) {
            return List.of();
        }
        List<XmlElement> found = occurrences.get(name + "|" + instance);
        return found == null ? List.of() : found;
    }

    /** Tells whether this element already has a child of that name, whatever its occurrence. */
    boolean hasChildNamed(String name) {
        if (occurrences == null) {
            return false;
        }
        for (Map.Entry<String, List<XmlElement>> entry : occurrences.entrySet()) {
            if (entry.getKey().startsWith(name + "|") && !entry.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a child under an occurrence.
     *
     * @param instance the occurrence, or {@code -1} where the name stands for one element
     * @param child    the child
     * @return the child
     */
    XmlElement add(int instance, XmlElement child) {
        if (occurrences == null) {
            occurrences = new LinkedHashMap<>();
            children = new ArrayList<>();
        }
        occurrences.computeIfAbsent(child.name + "|" + instance, key -> new ArrayList<>())
                .add(child);
        children.add(child);
        child.parent = this;
        return child;
    }

    /**
     * Takes a child back out, which the writer does where a walk it had begun turns out to
     * need a further instance of an element above it and is started again.
     *
     * @param instance the occurrence it was added under
     * @param child    the child
     */
    void remove(int instance, XmlElement child) {
        if (occurrences == null) {
            return;
        }
        List<XmlElement> found = occurrences.get(child.name + "|" + instance);
        if (found != null) {
            found.remove(child);
        }
        children.remove(child);
        child.parent = null;
    }

    /**
     * Returns the instance of the business group this element was made for, or {@code null}
     * where it was made for no group in particular.
     *
     * <p>Two business groups can be bound to elements of the same name under the same
     * parent, and so can a business group and a business term that stands outside it. What
     * tells the elements apart is then not their name and not always a condition on them
     * but the group instance they belong to, and this is where that is remembered.
     *
     * @return the semantic path of the group instance, or {@code null}
     */
    String owner() {
        return owner;
    }

    /** Records the instance of the business group this element belongs to. */
    void owner(String instancePath) {
        this.owner = instancePath;
    }

    /**
     * Returns the code that is to be written in front of the content of this element, or
     * {@code null} where there is none.
     *
     * <p>A syntax may have no element for a business term and write it in front of the
     * content of another one instead. The code is kept beside the content until everything
     * is written, because the two terms arrive separately and the one that carries the
     * content arrives second.
     *
     * @return the code, without the number signs that will surround it
     */
    String subjectCode() {
        return subjectCode;
    }

    /** Sets, or with {@code null} clears, the code written in front of the content. */
    void subjectCode(String code) {
        this.subjectCode = code;
    }

    /** Returns the content of this element, or {@code null} where it carries none. */
    String text() {
        return text;
    }

    /** Sets the content of this element. */
    void text(String content) {
        this.text = content;
    }

    /** Returns the value of an attribute, or {@code null} where the element has none. */
    String attribute(String localName) {
        return attributes == null ? null : attributes.get(localName);
    }

    /** Sets an attribute. */
    void attribute(String localName, String value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(localName, value);
    }

    /**
     * Returns the element whose content an attribute of this one is to be copied from, or
     * {@code null} where no attribute of this one is.
     *
     * <p>A binding of this syntax conditions an amount on the currency it is stated in by
     * comparing an attribute with an element elsewhere in the document. The element may be
     * written after the amount, so the copy is made at the end; until then the target is
     * remembered here, and it is also what tells two amounts of different currencies apart.
     *
     * @param localName the attribute
     * @return the target, as the text of the path that names it
     */
    String referenceTarget(String localName) {
        return references == null ? null : references.get(localName);
    }

    /** Remembers the element an attribute of this one is to be copied from. */
    void referenceTarget(String localName, String target) {
        if (references == null) {
            references = new LinkedHashMap<>();
        }
        references.put(localName, target);
    }

    /**
     * Records that a child of this element must never come to carry a content, because the
     * element was written for a condition that says so.
     *
     * <p>A syntax can write several business terms into elements of one name and tell them
     * apart by the content of a child or by an attribute: the additional document reference
     * of UBL is the supporting document group where its document type code is neither the
     * one of the invoiced object identifier nor the one of the project reference, and a
     * party identifier is the bank assigned creditor identifier where its scheme is SEPA
     * and the party's own identifier where it is not. An element written for one of those
     * cannot become the element of the other, and this is where that is remembered, because
     * nothing in the element itself says it.
     *
     * @param child   the path of the child, as prefixed names separated by solidi, or an
     *                attribute name with a commercial at in front of it
     * @param content the content that element or attribute may not have
     */
    void exclude(String child, String content) {
        if (excluded == null) {
            excluded = new LinkedHashMap<>();
        }
        excluded.computeIfAbsent(child, key -> new LinkedHashSet<>()).add(content);
    }

    /**
     * Tells whether this element was written for a condition that forbids a content.
     *
     * @param child   the path of the child, or an attribute name with a commercial at in
     *                front of it
     * @param content the content
     * @return {@code true} if the element may not carry it
     */
    boolean excludes(String child, String content) {
        Set<String> contents = excluded == null ? null : excluded.get(child);
        return contents != null && contents.contains(content);
    }

    /** Returns the children of this element, in the order they were made. */
    List<XmlElement> children() {
        return children == null ? List.of() : children;
    }

    /**
     * Tells whether this element holds neither content nor a child.
     *
     * <p>A content of no characters is no content. The writer can arrive at one where every
     * character of a value is a character XML 1.0 cannot carry, and an element written with
     * it says nothing that an element written without it does not: the validation artefacts
     * of both syntaxes refuse an empty element either way, so the two forms are one case and
     * are reported as one.
     */
    boolean isEmpty() {
        return (text == null || text.isEmpty())
                && (children == null || children.isEmpty());
    }

    /**
     * Tells whether this element carries nothing at all: no content, no child, no attribute
     * and nothing that is still to be written into it.
     *
     * <p>It is what the writer asks before it takes an element back out of the tree. An
     * element it made for a value it then declined to write holds nothing, and an element
     * that holds anything was made for something else as well and stays.
     *
     * @return {@code true} if nothing was ever written into this element
     */
    boolean bare() {
        return isEmpty() && subjectCode == null
                && (attributes == null || attributes.isEmpty())
                && (references == null || references.isEmpty());
    }

    /**
     * Takes this element out of the tree it stands in.
     *
     * <p>The writer creates the elements of a path before it knows whether the value at the
     * end of it can be written, and a value that cannot be written must not leave an empty
     * element behind: the validation artefacts of this syntax refuse one, and the finding
     * would be about an element the writer invented rather than about the invoice.
     */
    void detach() {
        if (parent == null) {
            return;
        }
        if (parent.occurrences != null) {
            for (List<XmlElement> found : parent.occurrences.values()) {
                if (found.remove(this)) {
                    break;
                }
            }
        }
        if (parent.children != null) {
            parent.children.remove(this);
        }
        parent = null;
    }

    /**
     * Writes this element and everything below it.
     *
     * @param out    where the text goes
     * @param depth  how deep this element stands, for the indentation
     * @param indent whether the output is indented and broken into lines
     */
    void write(Utf8Sink out, int depth, boolean indent) {
        if (indent) {
            out.repeat("  ", depth);
        }
        out.append('<').append(name);
        if (attributes != null) {
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                out.append(' ').append(attribute.getKey()).append("=\"");
                escapeAttribute(out, attribute.getValue());
                out.append('"');
            }
        }
        if (isEmpty()) {
            out.append("/>");
            if (indent) {
                out.append('\n');
            }
            return;
        }
        out.append('>');
        if (children != null && !children.isEmpty()) {
            children.sort(ORDER);
            if (indent) {
                out.append('\n');
            }
            for (XmlElement child : children) {
                child.write(out, depth + 1, indent);
            }
            if (indent) {
                out.repeat("  ", depth);
            }
        } else {
            escapeText(out, text);
        }
        out.append("</").append(name).append('>');
        if (indent) {
            out.append('\n');
        }
    }

    /** Appends content with the three characters that may not stand in it replaced. */
    private static void escapeText(Utf8Sink out, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> i += out.appendAt(text, i);
            }
        }
    }

    /**
     * Appends an attribute value. Besides the three characters of content and the quotation
     * mark, the three characters an XML parser would replace with a space are written as
     * references, so that what is read back is what was written.
     */
    private static void escapeAttribute(Utf8Sink out, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\t' -> out.append("&#x9;");
                case '\n' -> out.append("&#xA;");
                case '\r' -> out.append("&#xD;");
                default -> i += out.appendAt(value, i);
            }
        }
    }
}
