package de.bsnsoft.esj.bindings;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Writes the UBL schema table {@link SchemaTable} reads, from the schema modules of the
 * validation pack.
 *
 * <p>This is the generator the checked-in resource comes from, and it lives with the tests
 * for the reason {@link CiiSchemaSource} gives: the modules it reads are material of the
 * repository and are on the class path of the tests, and the writer must not go looking
 * for them at run time. {@link UblSchemaTest} regenerates the table on every build and
 * compares it with the resource character for character.
 *
 * <p>UBL is written in a different shape from the cross industry invoice and this class
 * reads that shape and refuses everything else by name. An aggregate is a sequence of
 * references to globally declared elements, so the type of a child is the type of the
 * element the reference names. A value type extends or restricts another one, and the
 * attributes it carries are its own together with those of the type it derives from, up
 * the chain to the core component type that declares them, and where both declare one the
 * nearer declaration says whether the schema asks for it.
 *
 * <p>The table carries the types the two document elements reach and no others. UBL 2.1
 * declares eleven hundred types for every document of the library, and a table of all of
 * them would say nothing about an invoice that the closure does not.
 */
final class UblSchemaSource {

    /** The XML Schema namespace. */
    private static final String XSD = "http://www.w3.org/2001/XMLSchema";

    /** Where the schema modules of the pack sit on the test class path. */
    private static final String PACK =
            "/de/bsnsoft/esj/syntax/packs/xrechnung/3.0.2/2026-08-31/xsd/ubl-2.1/";

    /** The pack the modules were taken from, as the table names it. */
    static final String PACK_ID = "xrechnung/3.0.2/2026-08-31";

    /** The modules, in the order the table names them. */
    static final List<String> MODULES = List.of(
            "maindoc/UBL-Invoice-2.1.xsd",
            "maindoc/UBL-CreditNote-2.1.xsd",
            "common/UBL-CommonAggregateComponents-2.1.xsd",
            "common/UBL-CommonBasicComponents-2.1.xsd",
            "common/UBL-UnqualifiedDataTypes-2.1.xsd",
            "common/CCTS_CCT_SchemaModule-2.1.xsd");

    /** The elements of the XML Schema language these modules are written with. */
    private static final Set<String> KNOWN = Set.of("annotation", "attribute", "complexType",
            "documentation", "element", "extension", "import", "restriction", "schema",
            "sequence", "simpleContent");

    /**
     * The prefix each namespace of the table is written with. The two document namespaces
     * are not among them: each declares one element and one type, the {@code documents}
     * member names both, and the binding table of a UBL document writes that element
     * without a prefix.
     */
    private static final Map<String, String> PREFIXES = Map.of(
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2", "cac",
            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2", "cbc",
            "urn:oasis:names:specification:ubl:schema:xsd:UnqualifiedDataTypes-2", "udt",
            "urn:un:unece:uncefact:data:specification:CoreComponentTypeSchemaModule:2", "cct");

    /** The two document elements, in the order the table names them. */
    private static final List<String> DOCUMENTS = List.of("Invoice", "CreditNote");

    private final Map<String, String> elementTypes = new TreeMap<>();
    private final Map<String, RawType> types = new TreeMap<>();
    private final Map<String, String> roots = new LinkedHashMap<>();
    private final List<String> unknownElements = new ArrayList<>();

    private UblSchemaSource() {
    }

    /**
     * Returns the table as the resource carries it.
     *
     * @return the table, as the UTF-8 text of a JSON document
     */
    static String generate() {
        UblSchemaSource source = new UblSchemaSource();
        for (String module : MODULES) {
            source.read(module);
        }
        source.resolve();
        return source.write();
    }

    /**
     * Writes the table and returns the names of the elements no module it reads declares,
     * in the order the closure met them.
     *
     * @return the element names, with one entry per type that references such an element
     */
    static List<String> generateAndList() {
        UblSchemaSource source = new UblSchemaSource();
        for (String module : MODULES) {
            source.read(module);
        }
        source.resolve();
        return List.copyOf(source.unknownElements);
    }

    private void read(String module) {
        try (InputStream in = UblSchemaSource.class.getResourceAsStream(PACK + module)) {
            if (in == null) {
                throw new IllegalStateException("the schema module " + module + " is not on"
                        + " the test class path");
            }
            XMLStreamReader reader = factory().createXMLStreamReader(in);
            try {
                walk(reader, module);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (XMLStreamException e) {
            throw new IllegalStateException("the schema module " + module + " cannot be read",
                    e);
        }
    }

    private void walk(XMLStreamReader reader, String module) throws XMLStreamException {
        String target = null;
        Deque<String> open = new ArrayDeque<>();
        RawType current = null;
        int annotation = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                String closed = open.pop();
                if ("annotation".equals(closed)) {
                    annotation--;
                } else if (annotation == 0 && "complexType".equals(closed)) {
                    current = null;
                }
                continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            if (annotation > 0) {
                open.push(reader.getLocalName());
                continue;
            }
            String local = reader.getLocalName();
            if (!XSD.equals(reader.getNamespaceURI()) || !KNOWN.contains(local)) {
                throw new IllegalStateException("the schema module " + module + " uses "
                        + reader.getName() + ", which this generator does not read");
            }
            int depth = open.size();
            open.push(local);
            switch (local) {
                case "annotation" -> annotation++;
                case "schema" -> target = reader.getAttributeValue(null, "targetNamespace");
                case "complexType" -> current = type(qualify(target,
                        reader.getAttributeValue(null, "name")));
                case "sequence" -> {
                    if (current != null) {
                        current.model = "sequence";
                    }
                }
                case "simpleContent" -> {
                    if (current != null) {
                        current.model = "simple";
                    }
                }
                case "extension", "restriction" -> {
                    if (current != null && current.base == null) {
                        current.base = reference(reader,
                                reader.getAttributeValue(null, "base"), target);
                    }
                }
                case "element" -> element(reader, target, depth, current);
                case "attribute" -> {
                    if (current != null) {
                        current.attributes.put(reader.getAttributeValue(null, "name"),
                                "required".equals(reader.getAttributeValue(null, "use")));
                    }
                }
                default -> { }
            }
        }
    }

    /** Records a global element declaration, a document element, or one child of a type. */
    private void element(XMLStreamReader reader, String target, int depth, RawType owner) {
        String name = reader.getAttributeValue(null, "name");
        if (name != null) {
            String qualified = qualify(target, name);
            String type = reference(reader, reader.getAttributeValue(null, "type"), target);
            elementTypes.put(qualified, type);
            if (depth == 1 && DOCUMENTS.contains(name)) {
                roots.put(name, type);
            }
            return;
        }
        String reference = reference(reader, reader.getAttributeValue(null, "ref"), target);
        boolean required = !"0".equals(reader.getAttributeValue(null, "minOccurs"));
        String max = reader.getAttributeValue(null, "maxOccurs");
        owner.children.add(new RawChild(reference, required, max != null && !"1".equals(max)));
    }

    /**
     * Gives every type the attributes of the types it derives from, and drops the types
     * the two document elements do not reach.
     */
    private void resolve() {
        for (RawType type : types.values()) {
            for (String base = type.base; base != null; ) {
                RawType inherited = types.get(base);
                if (inherited == null) {
                    break;
                }
                for (Map.Entry<String, Boolean> attribute : inherited.attributes.entrySet()) {
                    type.attributes.putIfAbsent(attribute.getKey(), attribute.getValue());
                }
                base = inherited.base;
            }
        }
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(roots.values());
        while (!pending.isEmpty()) {
            String name = pending.pop();
            RawType type = types.get(name);
            if (name == null || type == null || !reached.add(name)) {
                continue;
            }
            if (type.base != null) {
                pending.push(type.base);
            }
            for (RawChild child : type.children) {
                String childType = elementTypes.get(child.element());
                if (childType == null) {
                    unknownElements.add(child.element());
                    continue;
                }
                pending.push(childType);
            }
        }
        types.keySet().retainAll(reached);
    }

    /** Returns the prefixed form of a name declared in a target namespace. */
    private static String qualify(String namespace, String local) {
        String prefix = PREFIXES.get(namespace);
        return prefix == null ? local : prefix + ":" + local;
    }

    /**
     * Returns a reference in the prefixed form this table writes, or {@code null} where
     * there is none and where it names a built-in type of the schema language.
     *
     * <p>The prefix a module writes a reference with is the module's own and is resolved
     * against the namespaces in scope, because two modules spell one namespace
     * differently: the unqualified data types are {@code udt} in one module and
     * {@code ccts-cct} names in another what a third calls nothing at all.
     */
    private static String reference(XMLStreamReader reader, String name, String target) {
        if (name == null) {
            return null;
        }
        int colon = name.indexOf(':');
        if (colon < 0) {
            return qualify(target, name);
        }
        String namespace = reader.getNamespaceURI(name.substring(0, colon));
        if (XSD.equals(namespace)) {
            return null;
        }
        return qualify(namespace, name.substring(colon + 1));
    }

    private String write() {
        int elements = 0;
        for (RawType type : types.values()) {
            elements += type.children.size();
        }
        StringBuilder out = new StringBuilder(256 * 1024);
        out.append("{\n");
        member(out, "format", "EN16931-Semantic-JSON-ubl-schema");
        member(out, "version", "0.1");
        member(out, "syntax", "UBL");
        member(out, "syntaxName", "OASIS UBL 2.1 Invoice and Credit Note");
        member(out, "license", "Apache-2.0");
        member(out, "notice", "The element names, their order, their occurrence bounds and"
                + " the attribute names and uses in this file are facts read from the XML Schema"
                + " modules of OASIS Universal Business Language 2.1, as the validation"
                + " pack of this repository carries them. No documentation, annotation or"
                + " other prose of those modules is reproduced here, and nothing here is a"
                + " statement of the standard beyond what an element name and a position"
                + " are. The file carries the complex types the two document elements"
                + " reach and no others, and the one element of those documents whose"
                + " declaration lies in a module this table does not read, the UBL"
                + " extension container, is left out with them. This file itself is"
                + " Apache-2.0.");
        out.append("  \"source\": {\n");
        out.append("    \"name\": ").append(quote("OASIS UBL 2.1 XML Schema")).append(",\n");
        out.append("    \"schemaVersion\": ").append(quote("2.1")).append(",\n");
        out.append("    \"pack\": ").append(quote(PACK_ID)).append(",\n");
        out.append("    \"modules\": [\n");
        for (int i = 0; i < MODULES.size(); i++) {
            out.append("      ").append(quote(MODULES.get(i)))
                    .append(i + 1 < MODULES.size() ? ",\n" : "\n");
        }
        out.append("    ]\n  },\n");
        member(out, "generator", "esj-bindings/src/test/java/de/bsnsoft/esj/"
                + "bindings/UblSchemaSource.java");
        out.append("  \"namespaces\": {\n");
        Map<String, String> sorted = new TreeMap<>();
        PREFIXES.forEach((namespace, prefix) -> sorted.put(prefix, namespace));
        int written = 0;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            written++;
            out.append("    ").append(quote(entry.getKey())).append(": ")
                    .append(quote(entry.getValue()))
                    .append(written < sorted.size() ? ",\n" : "\n");
        }
        out.append("  },\n");
        out.append("  \"documents\": {\n");
        for (int i = 0; i < DOCUMENTS.size(); i++) {
            String document = DOCUMENTS.get(i);
            out.append("    ").append(quote(document)).append(": {\"rootElement\": ")
                    .append(quote(document)).append(", \"rootType\": ")
                    .append(quote(roots.get(document))).append('}')
                    .append(i + 1 < DOCUMENTS.size() ? ",\n" : "\n");
        }
        out.append("  },\n");
        out.append("  \"counts\": {\n    \"types\": ").append(types.size())
                .append(",\n    \"elements\": ").append(elements).append("\n  },\n");
        out.append("  \"types\": {\n");
        int index = 0;
        for (RawType type : types.values()) {
            index++;
            out.append("    ").append(quote(type.name)).append(": {\n");
            out.append("      \"model\": ").append(quote(type.model)).append(",\n");
            out.append("      \"elements\": [");
            int printed = 0;
            for (RawChild child : type.children) {
                String childType = elementTypes.get(child.element());
                if (childType == null) {
                    continue;
                }
                out.append(printed == 0 ? "\n" : ",\n").append("        {\"name\": ")
                        .append(quote(child.element())).append(", \"type\": ")
                        .append(quote(childType)).append(", \"required\": ")
                        .append(child.required()).append(", \"repeatable\": ")
                        .append(child.repeatable()).append('}');
                printed++;
            }
            out.append(printed == 0 ? "]," : "\n      ],").append('\n');
            out.append("      \"attributes\": [");
            int attributes = 0;
            for (Map.Entry<String, Boolean> attribute : type.attributes.entrySet()) {
                out.append(attributes == 0 ? "\n" : ",\n").append("        {\"name\": ")
                        .append(quote(attribute.getKey())).append(", \"required\": ")
                        .append(attribute.getValue()).append('}');
                attributes++;
            }
            out.append(attributes == 0 ? "]" : "\n      ]").append("\n    }")
                    .append(index < types.size() ? ",\n" : "\n");
        }
        out.append("  }\n}\n");
        return out.toString();
    }

    private static void member(StringBuilder out, String name, String value) {
        out.append("  ").append(quote(name)).append(": ").append(quote(value)).append(",\n");
    }

    private static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private RawType type(String name) {
        return types.computeIfAbsent(name, RawType::new);
    }

    private static XMLInputFactory factory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return factory;
    }

    /** One complex type while the modules are being read. */
    private static final class RawType {

        private final String name;
        private final List<RawChild> children = new ArrayList<>();
        private final Map<String, Boolean> attributes = new LinkedHashMap<>();
        private String model = "simple";
        private String base;

        private RawType(String name) {
            this.name = name;
        }
    }

    /** One element reference inside a complex type. */
    private record RawChild(String element, boolean required, boolean repeatable) { }
}
