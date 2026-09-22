package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

/**
 * Writes a semantic document as an XR document.
 *
 * <p>The walk is the walk of {@link XrMapper} read backwards. It follows the shape of the
 * XR representation — {@link XrElements}, the element names and the element order of the
 * KoSIT schema — and asks the registry, at every element, which semantic path that
 * position addresses ({@link XrPlacement}). Following the XR shape rather than the semantic
 * one is what puts a group the XR representation flattens where that representation wants
 * it: the invoicing period BG-14 lies inside the delivery information BG-13 in the
 * standard and beside it here, and the path of its values is the same either way.
 *
 * <h2>What the emitter leaves out</h2>
 *
 * <p>A group element is written only where something was written inside it, which is what
 * the stylesheets of the other direction do as well: an empty group carries no
 * information, and the importer would read nothing from it. Occurrences are written in
 * index order and stop at the first index the document has no values under, so a document
 * whose indices have a gap loses the occurrences behind that gap; the specification,
 * section 5.4 forbids such a gap, and the report names what was left out either way.
 *
 * <p>Every value the walk did not write is named in the report, and nothing is written
 * that the walk did not place: the set of values that reached the XR document and the set
 * of notes together account for every value of the document.
 */
final class XrEmitter {

    /** The name of the schema type whose elements carry a scheme and a scheme version. */
    private static final String IDENTIFIER_TYPE = "identifier";

    /** The name of the schema type whose elements carry a media type and a file name. */
    private static final String BINARY_TYPE = "binary_object";

    /** The attribute an identifier carries its scheme in. */
    private static final String SCHEME = "scheme_identifier";

    /** The attribute an identifier carries the version of its scheme in. */
    private static final String SCHEME_VERSION = "scheme_version_identifier";

    /** The attribute a binary object carries its media type in. */
    private static final String MIME_CODE = "mime_code";

    /** The attribute a binary object carries its file name in. */
    private static final String FILENAME = "filename";

    /** The prefix this emitter binds the namespace of the XR representation to. */
    private static final String PREFIX = "xr";

    /** What one level of nesting is indented by, which the KoSIT stylesheets also use. */
    private static final String INDENT = "   ";

    private final Registry registry;
    private final XrElements table;
    private final SortedMap<SemanticPath, SemanticValue> values;
    private final List<ExportNote> notes = new ArrayList<>();
    private final Set<SemanticPath> handled = new HashSet<>();

    private XrEmitter(SemanticDocument document, Registry registry) {
        this.registry = registry;
        this.table = XrElements.table();
        this.values = document.values();
    }

    /**
     * Writes one document.
     *
     * @param document the document to write
     * @param registry the registry that decides the structure
     * @return the XR document and the report
     */
    static ExportResult emit(SemanticDocument document, Registry registry) {
        XrEmitter emitter = new XrEmitter(document, registry);
        Node root = new Node(XrElements.ROOT, null);
        emitter.children(root, XrElements.ROOT, SemanticPath.root(), List.of());
        emitter.account(document);
        return new ExportResult(serialize(root),
                emitter.notes.isEmpty() ? ExportReport.empty() : new ExportReport(emitter.notes));
    }

    /**
     * Writes the elements one group of the XR representation carries, in the order of the
     * schema.
     *
     * @param parent       the element they are written into
     * @param containerId  the business group that element stands for, or the root
     * @param instancePath the semantic path of the group instance
     * @param chain        the term identifiers that path spends, from the root down
     */
    private void children(Node parent,
                          String containerId,
                          SemanticPath instancePath,
                          List<String> chain) {
        for (XrElements.Element element : table.children(containerId)) {
            Optional<Term> known = registry.term(element.id());
            Optional<List<String>> continuation =
                    XrPlacement.continuation(registry, chain, element.id());
            if (known.isEmpty() || continuation.isEmpty()) {
                // A registry without the XRechnung extension knows no BG-DEX-01, and the
                // values below such an element are named by account() rather than here,
                // where one note per position would fire on every document.
                continue;
            }
            Term term = known.get();
            SemanticPath position =
                    XrPlacement.instance(registry, instancePath, continuation.get(), chain.size());
            if (term.isGroup()) {
                groups(parent, element, term, position, XrPlacement.resolved(continuation.get()));
            } else {
                terms(parent, element, term, position);
            }
        }
    }

    /** Writes the occurrences of one group, as long as the document holds values in them. */
    private void groups(Node parent,
                        XrElements.Element element,
                        Term term,
                        SemanticPath position,
                        List<String> chain) {
        for (int index = 0; ; index++) {
            SemanticPath path = XrPlacement.extend(position, term, term.isRepeatable() ? index : -1);
            if (!holdsValues(path)) {
                return;
            }
            Node node = new Node(element.name(), element.id());
            children(node, element.id(), path, chain);
            if (node.hasChildren()) {
                parent.children.add(node);
            }
            if (!term.isRepeatable()) {
                return;
            }
        }
    }

    /** Writes the occurrences of one business term, as long as the document holds them. */
    private void terms(Node parent, XrElements.Element element, Term term, SemanticPath position) {
        for (int index = 0; ; index++) {
            SemanticPath path = XrPlacement.extend(position, term, term.isRepeatable() ? index : -1);
            SemanticValue value = values.get(path);
            if (value == null) {
                return;
            }
            value(parent, element, term, path, value);
            if (!term.isRepeatable()) {
                return;
            }
        }
    }

    /** Writes one value, or notes why it stayed behind. */
    private void value(Node parent,
                       XrElements.Element element,
                       Term term,
                       SemanticPath path,
                       SemanticValue value) {
        handled.add(path);
        String unwritable = unwritable(value);
        if (unwritable != null) {
            notes.add(new ExportNote(ExportNote.Kind.NOT_REPRESENTABLE, path.toString(),
                    "the " + unwritable + " of " + term.id() + " carries a character XML 1.0"
                            + " cannot hold, so the value was left out"));
            return;
        }
        Node node = new Node(element.name(), element.id());
        node.text = value.canonicalContent();
        component(node, element, term, path, SCHEME, value.scheme(), IDENTIFIER_TYPE);
        component(node, element, term, path, SCHEME_VERSION, value.schemeVersion(), IDENTIFIER_TYPE);
        component(node, element, term, path, MIME_CODE, value.mimeCode(), BINARY_TYPE);
        component(node, element, term, path, FILENAME, value.filename(), BINARY_TYPE);
        parent.children.add(node);
    }

    /**
     * Writes one supplementary component as the attribute the schema defines for it, or
     * notes that the element of this term has no such attribute. The schema gives a
     * scheme and a scheme version to every identifier and a media type and a file name to
     * every binary object, and nothing to any other type, so a component at a term of
     * another type is one the XR representation cannot carry.
     */
    private void component(Node node,
                           XrElements.Element element,
                           Term term,
                           SemanticPath path,
                           String attribute,
                           String content,
                           String type) {
        if (content == null) {
            return;
        }
        if (!element.type().equals(type)) {
            notes.add(new ExportNote(ExportNote.Kind.COMPONENT_DROPPED, path.toString(),
                    "the element of " + term.id() + " is of the schema type " + element.type()
                            + ", which carries no " + attribute + ", so that component of the"
                            + " value was dropped"));
            return;
        }
        node.attributes.put(attribute, content);
    }

    /**
     * Names every value that did not reach the XR document, and the extension subtrees
     * that could not reach it at all.
     */
    private void account(SemanticDocument document) {
        Set<String> recorded = recorded();
        for (SemanticPath path : values.keySet()) {
            if (handled.contains(path)) {
                continue;
            }
            notes.add(new ExportNote(ExportNote.Kind.NO_ELEMENT, path.toString(),
                    reason(path, recorded)));
        }
        for (String owner : document.extensions().keySet()) {
            notes.add(new ExportNote(ExportNote.Kind.EXTENSIONS_DROPPED, owner,
                    "the XR representation carries business terms and nothing else, so the"
                            + " extension subtree of this owner was left out"));
        }
    }

    /**
     * Says why one value found no element. Every term of the path is asked about, not only
     * the last one: a value below a group this exporter cannot place is left out because
     * of that group, and a note that named the term at the end of the path would send its
     * reader looking in the wrong place.
     */
    private String reason(SemanticPath path, Set<String> recorded) {
        for (String id : path.termIds()) {
            if (registry.term(id).isEmpty()) {
                return "no registry this exporter was given knows " + id + ", so the value"
                        + " was left out";
            }
            if (!recorded.contains(id)) {
                return "the XR representation has no element for " + id + ", so the value"
                        + " was left out";
            }
        }
        return "no element of the XR representation lies at that path: the position is not"
                + " one the registry records for " + path.term() + ", or an occurrence index"
                + " in the path has no predecessor, which the XR representation cannot write";
    }

    /** Returns the terms the XR representation carries anywhere. */
    private Set<String> recorded() {
        Set<String> recorded = new HashSet<>();
        for (String container : table.containers()) {
            for (XrElements.Element element : table.children(container)) {
                recorded.add(element.id());
            }
        }
        return recorded;
    }

    /** Tells whether the document holds a value at or below a path. */
    private boolean holdsValues(SemanticPath path) {
        SortedMap<SemanticPath, SemanticValue> tail = values.tailMap(path);
        return !tail.isEmpty() && tail.firstKey().startsWith(path);
    }

    /**
     * Returns the name of the part of a value that XML 1.0 cannot carry, or {@code null}
     * where every part of it can be written.
     */
    private static String unwritable(SemanticValue value) {
        if (!writable(value.canonicalContent())) {
            return "content";
        }
        if (!writable(value.scheme()) || !writable(value.schemeVersion())) {
            return "scheme";
        }
        if (!writable(value.mimeCode())) {
            return "media type";
        }
        if (!writable(value.filename())) {
            return "file name";
        }
        return null;
    }

    /**
     * Tells whether every character of a string is one XML 1.0 can carry. The control
     * characters below the space, the tabulator, the line feed and the carriage return
     * excepted, have no representation in XML at all — neither literally nor as a
     * character reference — and neither has an unpaired surrogate.
     */
    private static boolean writable(String content) {
        if (content == null) {
            return true;
        }
        for (int i = 0; i < content.length(); i++) {
            char character = content.charAt(i);
            if (character == '\t' || character == '\n' || character == '\r') {
                continue;
            }
            if (character < 0x20 || character == 0xFFFE || character == 0xFFFF) {
                return false;
            }
            if (Character.isHighSurrogate(character)) {
                if (i + 1 >= content.length()
                        || !Character.isLowSurrogate(content.charAt(i + 1))) {
                    return false;
                }
                i++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Writes the tree. The result is decided by the document alone: no timestamp, no
     * identifier of the producer and no hash map ordering takes part in it, so the same
     * document written twice gives the same bytes.
     */
    private static byte[] serialize(Node root) {
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        write(out, root, 0);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void write(StringBuilder out, Node node, int depth) {
        out.append(INDENT.repeat(depth)).append('<').append(PREFIX).append(':').append(node.name);
        if (depth == 0) {
            out.append(" xmlns:").append(PREFIX).append("=\"")
                    .append(XrTransformer.XR_NAMESPACE).append('"');
        }
        if (node.id != null) {
            out.append(' ').append(PREFIX).append(":id=\"").append(node.id).append('"');
        }
        node.attributes.forEach((name, content) ->
                out.append(' ').append(name).append("=\"").append(attribute(content)).append('"'));
        if (node.text == null && node.children.isEmpty()) {
            out.append("/>\n");
            return;
        }
        out.append('>');
        if (node.text != null) {
            // The content of a value is written without any whitespace of its own around
            // it: the text of a business term carries its line breaks and its spaces, and
            // an indented value would read back as another value.
            out.append(text(node.text));
        } else {
            out.append('\n');
            for (Node child : node.children) {
                write(out, child, depth + 1);
            }
            out.append(INDENT.repeat(depth));
        }
        out.append("</").append(PREFIX).append(':').append(node.name).append(">\n");
    }

    /** Escapes the content of an element. */
    private static String text(String content) {
        StringBuilder escaped = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            char character = content.charAt(i);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                // A carriage return survives a parser only as a character reference; every
                // other one is normalized to a line feed before a reader sees it.
                case '\r' -> escaped.append("&#13;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    /** Escapes the value of an attribute, where whitespace is normalized as well. */
    private static String attribute(String content) {
        StringBuilder escaped = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            char character = content.charAt(i);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\t' -> escaped.append("&#9;");
                case '\n' -> escaped.append("&#10;");
                case '\r' -> escaped.append("&#13;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    /** One element of the tree that is about to be written. */
    private static final class Node {

        private final String name;
        private final String id;
        private final List<Node> children = new ArrayList<>();
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private String text;

        private Node(String name, String id) {
            this.name = name;
            this.id = id;
        }

        private boolean hasChildren() {
            return !children.isEmpty();
        }
    }
}
