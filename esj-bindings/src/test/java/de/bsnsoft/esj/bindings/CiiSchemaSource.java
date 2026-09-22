package de.bsnsoft.esj.bindings;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Writes the CII schema table {@link CiiSchema} reads, from the schema modules of the
 * validation pack.
 *
 * <p>This is the generator the checked-in resource comes from, and it lives with the tests
 * rather than with the code that reads the resource: the modules it reads are material of
 * the repository and are on the class path of the tests, and the writer must not go looking
 * for them at run time. {@link CiiSchemaTest} regenerates the table on every build and
 * compares it with the resource character for character, so a schema module that is
 * replaced shows up as a failing test rather than as a writer that quietly puts an element
 * in the wrong place.
 *
 * <p>The modules of this syntax are written in one shape — a complex type is a flat
 * sequence or choice of element declarations, or simple content with attributes — and this
 * class reads exactly that shape and refuses everything else by name, for the same reason
 * {@link Xpaths} refuses an expression it does not understand.
 */
final class CiiSchemaSource {

    /** The XML Schema namespace. */
    private static final String XSD = "http://www.w3.org/2001/XMLSchema";

    /** Where the schema modules of the pack sit on the test class path. */
    private static final String PACK =
            "/de/bsnsoft/esj/syntax/packs/xrechnung/3.0.2/2026-08-31/xsd/cii-d16b/";

    /** The pack the modules were taken from, as the table names it. */
    static final String PACK_ID = "xrechnung/3.0.2/2026-08-31";

    /** The modules, in the order the table names them. */
    static final List<String> MODULES = List.of(
            "CrossIndustryInvoice_100pD16B.xsd",
            "CrossIndustryInvoice_ReusableAggregateBusinessInformationEntity_100pD16B.xsd",
            "CrossIndustryInvoice_QualifiedDataType_100pD16B.xsd",
            "CrossIndustryInvoice_UnqualifiedDataType_100pD16B.xsd");

    /** The elements of the XML Schema language these modules are written with. */
    private static final Set<String> KNOWN = Set.of("attribute", "choice", "complexType",
            "element", "extension", "import", "maxLength", "minLength", "restriction",
            "schema", "sequence", "simpleContent", "simpleType");

    private final Map<String, String> prefixes = new LinkedHashMap<>();
    private final Map<String, RawType> types = new TreeMap<>();
    private String rootElement;
    private String rootType;

    private CiiSchemaSource(Map<String, String> namespaces) {
        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
            prefixes.put(entry.getValue(), entry.getKey());
        }
    }

    /**
     * Returns the table as the resource carries it.
     *
     * @param namespaces prefix to namespace URI, as the binding table of the syntax
     *                   declares them
     * @return the table, as the UTF-8 text of a JSON document
     */
    static String generate(Map<String, String> namespaces) {
        CiiSchemaSource source = new CiiSchemaSource(namespaces);
        for (String module : MODULES) {
            source.read(module);
        }
        return source.write(namespaces);
    }

    private void read(String module) {
        try (InputStream in = CiiSchemaSource.class.getResourceAsStream(PACK + module)) {
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
        List<String> open = new ArrayList<>();
        RawType current = null;
        RawType inline = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                String closed = open.remove(open.size() - 1);
                if ("complexType".equals(closed) && open.size() == 1) {
                    current = null;
                }
                continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            String local = reader.getLocalName();
            if (!XSD.equals(reader.getNamespaceURI()) || !KNOWN.contains(local)) {
                throw new IllegalStateException("the schema module " + module + " uses "
                        + reader.getName() + ", which this generator does not read");
            }
            int depth = open.size();
            open.add(local);
            switch (local) {
                case "schema" -> target = reader.getAttributeValue(null, "targetNamespace");
                case "complexType" -> {
                    if (depth == 1) {
                        current = type(qualify(target,
                                reader.getAttributeValue(null, "name")));
                        inline = null;
                    }
                }
                case "sequence", "choice" -> {
                    if (depth == 2 && current != null) {
                        current.model = local;
                    }
                }
                case "simpleContent" -> {
                    if (depth == 2 && current != null) {
                        current.model = "simple";
                    }
                }
                case "element" -> {
                    if (depth == 1) {
                        rootElement = qualify(target, reader.getAttributeValue(null, "name"));
                        rootType = reference(reader.getAttributeValue(null, "type"), target);
                    } else if (depth == 3 && current != null) {
                        inline = declare(reader, target, current);
                    }
                }
                case "attribute" -> {
                    RawType owner = open.contains("element") ? inline : current;
                    if (owner != null) {
                        owner.attributes.put(reader.getAttributeValue(null, "name"),
                                "required".equals(reader.getAttributeValue(null, "use")));
                    }
                }
                default -> { }
            }
        }
    }

    /**
     * Records one element declaration on the type under examination and returns the type
     * the schema declares inline for it, or {@code null} where it names one.
     */
    private RawType declare(XMLStreamReader reader, String target, RawType owner) {
        String name = qualify(target, reader.getAttributeValue(null, "name"));
        String type = reference(reader.getAttributeValue(null, "type"), target);
        RawType inline = null;
        if (type == null) {
            // The schema declares the type of this element inside the element itself. The
            // table gives it a name of its own so that its attributes have somewhere to
            // live and the writer can ask about them the way it asks about any other type.
            type = owner.name + "." + name.substring(name.indexOf(':') + 1);
            inline = type(type);
            inline.model = "simple";
        }
        boolean required = !"0".equals(reader.getAttributeValue(null, "minOccurs"));
        String max = reader.getAttributeValue(null, "maxOccurs");
        owner.children.add(new RawChild(name, type, required, max != null
                && !"1".equals(max)));
        return inline;
    }

    private RawType type(String name) {
        return types.computeIfAbsent(name, RawType::new);
    }

    /** Returns the prefixed form of a name declared in a target namespace. */
    private String qualify(String namespace, String local) {
        String prefix = prefixes.get(namespace);
        if (prefix == null) {
            throw new IllegalStateException("no prefix of the binding table stands for "
                    + namespace);
        }
        return prefix + ":" + local;
    }

    /** Returns a type reference in its prefixed form, or {@code null} where there is none. */
    private String reference(String type, String target) {
        if (type == null) {
            return null;
        }
        return type.indexOf(':') < 0 ? qualify(target, type) : type;
    }

    private String write(Map<String, String> namespaces) {
        int elements = 0;
        for (RawType type : types.values()) {
            elements += type.children.size();
        }
        StringBuilder out = new StringBuilder(128 * 1024);
        out.append("{\n");
        member(out, "format", "EN16931-Semantic-JSON-cii-schema");
        member(out, "version", "0.1");
        member(out, "syntax", "CII");
        member(out, "syntaxName", "UN/CEFACT Cross Industry Invoice D16B");
        member(out, "license", "Apache-2.0");
        member(out, "notice", "The element names, their order, their occurrence bounds and"
                + " the attribute names and uses in this file are facts read from the XML"
                + " Schema"
                + " modules of UN/CEFACT Cross Industry Invoice D16B, schema version"
                + " 100.D16B, as the validation pack of this repository carries them. No"
                + " documentation, annotation or other prose of those modules is reproduced"
                + " here, and nothing here is a statement of the standard beyond what an"
                + " element name and a position are. This file itself is Apache-2.0.");
        out.append("  \"source\": {\n");
        out.append("    \"name\": ")
                .append(quote("UN/CEFACT Cross Industry Invoice XML Schema")).append(",\n");
        out.append("    \"schemaVersion\": ").append(quote("100.D16B")).append(",\n");
        out.append("    \"pack\": ").append(quote(PACK_ID)).append(",\n");
        out.append("    \"modules\": [\n");
        for (int i = 0; i < MODULES.size(); i++) {
            out.append("      ").append(quote(MODULES.get(i)))
                    .append(i + 1 < MODULES.size() ? ",\n" : "\n");
        }
        out.append("    ]\n  },\n");
        member(out, "generator", "esj-bindings/src/test/java/de/bsnsoft/esj/"
                + "bindings/CiiSchemaSource.java");
        out.append("  \"namespaces\": {\n");
        Map<String, String> sorted = new TreeMap<>(namespaces);
        int written = 0;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            written++;
            out.append("    ").append(quote(entry.getKey())).append(": ")
                    .append(quote(entry.getValue()))
                    .append(written < sorted.size() ? ",\n" : "\n");
        }
        out.append("  },\n");
        member(out, "rootElement", rootElement);
        member(out, "rootType", rootType);
        out.append("  \"counts\": {\n    \"types\": ").append(types.size())
                .append(",\n    \"elements\": ").append(elements).append("\n  },\n");
        out.append("  \"types\": {\n");
        int index = 0;
        for (RawType type : types.values()) {
            index++;
            out.append("    ").append(quote(type.name)).append(": {\n");
            out.append("      \"model\": ").append(quote(type.model)).append(",\n");
            out.append("      \"elements\": [");
            for (int i = 0; i < type.children.size(); i++) {
                RawChild child = type.children.get(i);
                out.append(i == 0 ? "\n" : ",\n").append("        {\"name\": ")
                        .append(quote(child.name())).append(", \"type\": ")
                        .append(quote(child.type())).append(", \"required\": ")
                        .append(child.required()).append(", \"repeatable\": ")
                        .append(child.repeatable()).append('}');
            }
            out.append(type.children.isEmpty() ? "]," : "\n      ],").append('\n');
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

        private RawType(String name) {
            this.name = name;
        }
    }

    /** One element declaration inside a complex type. */
    private record RawChild(String name, String type, boolean required, boolean repeatable) { }
}
