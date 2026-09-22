package de.bsnsoft.esj.cli;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The mutation set of {@code conformance/rules/mutations/}, and the four operations it is
 * written in.
 *
 * <p>A mutation is data rather than a file: an instance of the conformance corpus, a
 * location in it, one change to make there, and what each of the two engines is expected to
 * report about the result. The broken documents are never checked in, which keeps the
 * repository free of invoices that look real and are wrong, keeps every mutation readable
 * beside the rule it is aimed at, and lets the whole set survive a release of the corpus
 * that reformats an instance. {@code conformance/rules/mutate.py} writes the same documents
 * out for a reader who wants to look at one.
 *
 * <p>An expression that selects nothing is an error rather than a change that does nothing,
 * because a set whose expressions have gone stale would otherwise pass by validating the
 * corpus over and over.
 */
final class Oracle {

    /** Where the set lives on the test class path. */
    private static final String FILE = "/conformance/rules/mutations/mutations.json";

    /** Where the corpus lives on the test class path. */
    private static final String CORPUS = "/conformance/kosit/";

    private static final Map<String, String> NAMESPACES = new LinkedHashMap<>();

    private static final List<Mutation> SET = read();

    private Oracle() {
        throw new AssertionError("no instances");
    }

    /**
     * One mutation of the set.
     *
     * @param id       its identifier, unique in the set and the name a test runs under
     * @param rule     the rule it is aimed at, empty for a near miss
     * @param syntax   {@code UBL} or {@code CII}
     * @param source   the instance it is made from, relative to {@code conformance/kosit/}
     * @param note     what it does, in English
     * @param changes  the changes to make, in order
     * @param expectedNative   the rule identifiers the semantic engine is expected to fault
     * @param expectedWarnings the rule identifiers the semantic engine is expected to warn
     *                         about, which decide no verdict and are recorded so that the
     *                         zone between the two artefacts is measured rather than read
     * @param expectedOfficial the EN 16931 identifiers the official Schematron is expected to
     *                         report
     * @param outcome  {@code agrees}, {@code differs} or {@code silent}
     * @param cause    why the two differ, empty where they do not
     */
    record Mutation(String id,
                    String rule,
                    String syntax,
                    String source,
                    String note,
                    List<Change> changes,
                    List<String> expectedNative,
                    List<String> expectedWarnings,
                    List<String> expectedOfficial,
                    String outcome,
                    String cause) {

        @Override
        public String toString() {
            return id;
        }
    }

    /**
     * One change of a mutation.
     *
     * @param operation {@code set}, {@code delete}, {@code attribute}, {@code insert-before}
     *                  or {@code insert-after}
     * @param xpath     what it applies to, an expression over the instance
     * @param value     the new string value for {@code set}, the XML fragment for the two
     *                  insertions, {@code name=value} for {@code attribute}, and unused for
     *                  {@code delete}
     */
    record Change(String operation, String xpath, String value) {
    }

    /** Returns the mutations, in the order the file lists them. */
    static List<Mutation> all() {
        return SET;
    }

    /** Returns the mutations, for a parameterized test. */
    static List<Mutation> mutations() {
        return SET;
    }

    /**
     * Applies a mutation to the instance it names and returns the document it makes.
     *
     * @param mutation the mutation
     * @return the bytes of the mutated document
     * @throws IllegalStateException if one of its expressions selects nothing
     */
    static byte[] apply(Mutation mutation) {
        Document document = parse(bytes(CORPUS + mutation.source()));
        for (Change change : mutation.changes()) {
            apply(document, change, mutation.id());
        }
        return serialize(document);
    }

    /** Returns the instance a mutation is made from, unchanged. */
    static byte[] instance(Mutation mutation) {
        return bytes(CORPUS + mutation.source());
    }

    /** Returns a file of the test class path. */
    static byte[] bytes(String resource) {
        try (InputStream in = Oracle.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the class path");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(resource + " could not be read", e);
        }
    }

    /** Returns a file of the test class path as text. */
    static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }

    private static void apply(Document document, Change change, String id) {
        List<Node> nodes = select(document, change.xpath(), id);
        switch (change.operation()) {
            case "set" -> nodes.forEach(node -> node.setTextContent(change.value()));
            case "delete" -> nodes.forEach(Oracle::remove);
            case "attribute" -> {
                String[] pair = change.value().split("=", 2);
                nodes.forEach(node -> ((Element) node).setAttribute(pair[0], pair[1]));
            }
            case "insert-before" -> nodes.forEach(node ->
                    node.getParentNode().insertBefore(fragment(document, change.value()), node));
            case "insert-after" -> nodes.forEach(node ->
                    node.getParentNode().insertBefore(fragment(document, change.value()),
                            node.getNextSibling()));
            default -> throw new IllegalStateException(id + " asks for the operation "
                    + change.operation() + ", and a mutation has one of set, delete, attribute,"
                    + " insert-before and insert-after");
        }
    }

    private static void remove(Node node) {
        if (node instanceof Attr attribute) {
            attribute.getOwnerElement().removeAttributeNode(attribute);
            return;
        }
        node.getParentNode().removeChild(node);
    }

    private static Node fragment(Document document, String xml) {
        StringBuilder root = new StringBuilder("<fragment");
        NAMESPACES.forEach((prefix, namespace) ->
                root.append(" xmlns:").append(prefix).append("=\"").append(namespace).append('"'));
        root.append('>').append(xml).append("</fragment>");
        Document parsed = parse(root.toString().getBytes(StandardCharsets.UTF_8));
        Node imported = document.createDocumentFragment();
        NodeList children = parsed.getDocumentElement().getChildNodes();
        List<Node> copy = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            copy.add(children.item(i));
        }
        for (Node child : copy) {
            imported.appendChild(document.importNode(child, true));
        }
        return imported;
    }

    private static List<Node> select(Document document, String expression, String id) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new Prefixes());
        NodeList selected;
        try {
            selected = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
        } catch (Exception e) {
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
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalStateException("a document of the mutation set could not be read", e);
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
        } catch (Exception e) {
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
            return prefix == null ? List.<String>of().iterator() : List.of(prefix).iterator();
        }
    }

    private static List<Mutation> read() {
        Map<?, ?> root = (Map<?, ?>) json(bytes(FILE));
        ((Map<?, ?>) root.get("namespaces")).forEach((prefix, namespace) ->
                NAMESPACES.put((String) prefix, (String) namespace));
        List<Mutation> mutations = new ArrayList<>();
        for (Object element : (List<?>) root.get("mutations")) {
            Map<?, ?> entry = (Map<?, ?>) element;
            List<Change> changes = new ArrayList<>();
            for (Object item : (List<?>) entry.get("changes")) {
                Map<?, ?> change = (Map<?, ?>) item;
                Object value = change.get("value");
                changes.add(new Change((String) change.get("op"), (String) change.get("xpath"),
                        value == null ? "" : (String) value));
            }
            Map<?, ?> expect = (Map<?, ?>) entry.get("expect");
            mutations.add(new Mutation((String) entry.get("id"),
                    text(entry, "rule"), (String) entry.get("syntax"),
                    (String) entry.get("source"), (String) entry.get("note"),
                    List.copyOf(changes),
                    strings(expect.get("native")),
                    expect.get("warns") == null ? List.of() : strings(expect.get("warns")),
                    strings(expect.get("official")),
                    (String) entry.get("outcome"), text(entry, "cause")));
        }
        return List.copyOf(mutations);
    }

    private static String text(Map<?, ?> entry, String member) {
        Object value = entry.get(member);
        return value == null ? "" : (String) value;
    }

    private static List<String> strings(Object array) {
        List<String> values = new ArrayList<>();
        for (Object value : (List<?>) array) {
            values.add((String) value);
        }
        return List.copyOf(values);
    }

    /** Reads JSON into maps, lists and strings, which is all the set is written in. */
    static Object json(byte[] bytes) {
        try (JsonParser parser = new JsonFactory().createParser(bytes)) {
            parser.nextToken();
            return value(parser);
        } catch (IOException e) {
            throw new IllegalStateException("a file of the mutation set is not JSON", e);
        }
    }

    private static Object value(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            Map<String, Object> members = new LinkedHashMap<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.currentName();
                parser.nextToken();
                members.put(name, value(parser));
            }
            return members;
        }
        if (token == JsonToken.START_ARRAY) {
            List<Object> elements = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                elements.add(value(parser));
            }
            return elements;
        }
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return parser.getLongValue();
        }
        return parser.getValueAsString();
    }
}
