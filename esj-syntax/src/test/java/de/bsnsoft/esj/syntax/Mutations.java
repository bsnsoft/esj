package de.bsnsoft.esj.syntax;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The mutation set of {@code conformance/syntax/mutations/}, and the four operations it
 * is written in.
 *
 * <p>A mutation is data, not a file: an instance of the corpus, a location in it, and one
 * change to make there. The broken documents are never checked in, which keeps the
 * repository free of invoices that look real and are wrong, keeps every mutation readable
 * beside the rule it is aimed at, and makes the whole set survive a new release of the
 * corpus that reformats an instance.
 *
 * <p>The four operations are all a mutation needs and are deliberately not more: set the
 * string value of what an expression selects, remove it, or put a fragment beside it.
 * Anything an expression cannot reach is not a mutation this set can express, and that is
 * a limit worth keeping — a mutation nobody can read is a test nobody can maintain.
 *
 * <p>An expression that selects nothing is an error rather than a mutation that changes
 * nothing, because a mutation set whose expressions have gone stale would otherwise pass
 * by validating the corpus over and over.
 */
final class Mutations {

    /** Where the set lives on the test class path. */
    private static final String FILE = "/conformance/syntax/mutations/mutations.json";

    private static final Map<String, String> NAMESPACES = new LinkedHashMap<>();

    private static final List<Mutation> SET = read();

    private Mutations() {
        throw new AssertionError("no instances");
    }

    /**
     * One mutation of the set.
     *
     * @param id       its identifier, unique in the set and the name a test runs under
     * @param category the finding category it is aimed at, for the coverage the set
     *                 claims
     * @param source   the instance of the corpus it is made from, relative to
     *                 {@code conformance/kosit/}
     * @param note     what it does, in English
     * @param changes  the changes to make, in order
     * @param verdict  the verdict the engine is expected to reach
     * @param expected the findings the engine is expected to make, one line each, in the
     *                 order of the report
     * @param profileNote the note the engine is expected to leave about the rule sets it
     *                    did not run for the profile, empty where it leaves none
     */
    record Mutation(String id,
                    String category,
                    String source,
                    String note,
                    List<Change> changes,
                    String verdict,
                    List<String> expected,
                    String profileNote) {
    }

    /**
     * One change of a mutation.
     *
     * @param operation {@code set}, {@code delete}, {@code insert-before} or
     *                  {@code insert-after}
     * @param xpath     what it applies to, an expression over the instance
     * @param value     the new string value for {@code set}, the XML fragment for the two
     *                  insertions, and unused for {@code delete}
     */
    record Change(String operation, String xpath, String value) {
    }

    /**
     * Returns one expected finding as one line, which is the form a failure prints.
     *
     * @param code     the rule identifier
     * @param severity the level the profile of the document gives that rule
     * @param flag     the level the artefact itself set on it
     * @param category what kind of thing the finding is about
     * @return the line
     */
    static String line(String code, String severity, String flag, String category) {
        return code + " " + severity + " (flag " + flag + ") " + category;
    }

    /** Returns the mutations, in the order the file lists them. */
    static List<Mutation> all() {
        return SET;
    }

    /** Returns the identifiers of the mutations, for a parameterized test. */
    static List<String> ids() {
        return SET.stream().map(Mutation::id).toList();
    }

    /**
     * Returns the mutation of an identifier.
     *
     * @param id the identifier
     * @return the mutation
     * @throws IllegalArgumentException if the set has no mutation of that identifier
     */
    static Mutation of(String id) {
        return SET.stream().filter(mutation -> mutation.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no mutation " + id));
    }

    /**
     * Applies a mutation to the instance it names and returns the document it makes.
     *
     * @param mutation the mutation
     * @return the bytes of the mutated document
     * @throws IllegalStateException if one of its expressions selects nothing
     */
    static byte[] apply(Mutation mutation) {
        Document document = parse(Corpus.instance(mutation.source()));
        for (Change change : mutation.changes()) {
            apply(document, change, mutation.id());
        }
        return serialize(document);
    }

    private static void apply(Document document, Change change, String id) {
        List<Node> nodes = select(document, change.xpath(), id);
        switch (change.operation()) {
            case "set" -> nodes.forEach(node -> node.setTextContent(change.value()));
            case "delete" -> nodes.forEach(Mutations::remove);
            case "insert-before" -> nodes.forEach(node ->
                    node.getParentNode().insertBefore(
                            fragment(document, change.value()), node));
            case "insert-after" -> nodes.forEach(node ->
                    node.getParentNode().insertBefore(
                            fragment(document, change.value()), node.getNextSibling()));
            default -> throw new IllegalStateException(id + " asks for the operation "
                    + change.operation() + ", and a mutation has one of set, delete,"
                    + " insert-before and insert-after");
        }
    }

    /** Removes a node, whether it hangs off a parent or off an element as an attribute. */
    private static void remove(Node node) {
        if (node instanceof Attr attribute) {
            attribute.getOwnerElement().removeAttributeNode(attribute);
            return;
        }
        node.getParentNode().removeChild(node);
    }

    /**
     * Parses an XML fragment of a mutation into a node the document can hold.
     *
     * <p>The fragment is parsed under a root that declares every prefix the set uses, so
     * a mutation writes {@code <cbc:UBLVersionID>2.1</cbc:UBLVersionID>} rather than
     * repeating a namespace declaration it did not choose.
     */
    private static Node fragment(Document document, String xml) {
        StringBuilder root = new StringBuilder("<fragment");
        NAMESPACES.forEach((prefix, namespace) ->
                root.append(" xmlns:").append(prefix).append("=\"").append(namespace)
                        .append('"'));
        root.append('>').append(xml).append("</fragment>");
        Document parsed = parse(root.toString().getBytes(StandardCharsets.UTF_8));
        Node imported = document.createDocumentFragment();
        NodeList children = parsed.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            imported.appendChild(document.importNode(children.item(i), true));
        }
        return imported;
    }

    private static List<Node> select(Document document, String expression, String id) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new Prefixes());
        NodeList selected;
        try {
            selected = (NodeList) xpath.evaluate(expression, document,
                    XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(id + " carries the expression " + expression
                    + ", which is not one this set can evaluate", e);
        }
        if (selected.getLength() == 0) {
            throw new IllegalStateException(id + " selects nothing with " + expression
                    + ", so it would validate the instance rather than a mutation of it");
        }
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < selected.getLength(); i++) {
            nodes.add(selected.item(i));
        }
        return nodes;
    }

    private static Document parse(byte[] xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("a document of the mutation set could not be"
                    + " read", e);
        }
    }

    private static byte[] serialize(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(bytes));
            return bytes.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("a mutated document could not be written", e);
        }
    }

    /** The prefixes the expressions of the set are written with. */
    private static final class Prefixes implements NamespaceContext {

        @Override
        public String getNamespaceURI(String prefix) {
            return NAMESPACES.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
        }

        @Override
        public String getPrefix(String namespaceUri) {
            return NAMESPACES.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(namespaceUri))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceUri) {
            String prefix = getPrefix(namespaceUri);
            return prefix == null ? List.<String>of().iterator()
                    : List.of(prefix).iterator();
        }
    }

    private static List<Mutation> read() {
        Map<?, ?> root = (Map<?, ?>) PackJson.read(Corpus.bytes(FILE), FILE);
        ((Map<?, ?>) root.get("namespaces")).forEach((prefix, namespace) ->
                NAMESPACES.put((String) prefix, (String) namespace));
        List<Mutation> mutations = new ArrayList<>();
        for (Object element : (List<?>) root.get("mutations")) {
            Map<?, ?> entry = (Map<?, ?>) element;
            List<Change> changes = new ArrayList<>();
            for (Object item : (List<?>) entry.get("changes")) {
                Map<?, ?> change = (Map<?, ?>) item;
                Object value = change.get("value");
                changes.add(new Change((String) change.get("op"),
                        (String) change.get("xpath"),
                        value == null ? "" : (String) value));
            }
            Map<?, ?> expect = (Map<?, ?>) entry.get("expect");
            List<String> expected = new ArrayList<>();
            for (Object finding : (List<?>) expect.get("findings")) {
                Map<?, ?> found = (Map<?, ?>) finding;
                expected.add(line((String) found.get("code"),
                        (String) found.get("severity"), (String) found.get("flag"),
                        (String) found.get("category")));
            }
            Object profileNote = expect.get("profileNote");
            mutations.add(new Mutation((String) entry.get("id"),
                    (String) entry.get("category"),
                    (String) entry.get("source"),
                    (String) entry.get("note"),
                    changes,
                    (String) expect.get("verdict"),
                    expected,
                    profileNote == null ? "" : (String) profileNote));
        }
        return List.copyOf(mutations);
    }
}
