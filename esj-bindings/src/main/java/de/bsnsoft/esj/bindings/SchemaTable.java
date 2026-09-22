package de.bsnsoft.esj.bindings;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamReadException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The shape of one XML syntax, as far as writing a document in it needs it: for every
 * complex type of the syntax, the elements it admits in the order it admits them, whether
 * each of them is required and whether it may repeat, and the attributes the type
 * carries.
 *
 * <p>A binding table says where a business term lives. It does not say in which order two
 * terms' elements have to be written, and a document whose elements stand in the wrong
 * order is rejected by the schema before any rule of the standard is reached. That order
 * is a fact of the schema, so it is taken from the schema rather than guessed: a
 * generator walks the schema modules of the validation pack and writes this table, and
 * {@code CiiSchemaTest} and {@code UblSchemaTest} regenerate it from the same modules on
 * every build and fail where the two differ.
 *
 * <p>The table also answers three further questions the writer has to ask. Whether an
 * element may repeat decides which element of a path carries the occurrence index of a
 * business group. Whether an element or an attribute exists at all decides which of the
 * XPaths a term is bound to is the one to write, because the source model binds two terms
 * of this release to a name the schema of this release does not have. And whether a
 * required element's type is one that can stand empty decides the structural skeleton
 * every document gets.
 *
 * <p>One file describes one syntax and may describe more than one document of it: the two
 * UBL document types are the same types with two document elements, so they share a table
 * and {@code documents} names the element and the type of each. A file that describes one
 * document names them at its top level instead.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
final class SchemaTable {

    /** The resource of this module the table of the UBL syntax is read from. */
    private static final String UBL_RESOURCE = "ubl-schema.json";

    /** The resource of this module the table of the CII syntax is read from. */
    private static final String CII_RESOURCE = "cii-schema.json";

    /** The largest schema table this class reads, in bytes. */
    private static final int MAX_TABLE_BYTES = 4 * 1024 * 1024;

    /** The deepest JSON nesting the table is written with, with room to spare. */
    private static final int MAX_TABLE_NESTING = 16;

    /** The longest string the table is written with, with room to spare. */
    private static final int MAX_TABLE_STRING = 4096;

    private final Map<String, String> namespaces;
    private final String rootElement;
    private final String rootType;
    private final Map<String, Type> types;
    private final Map<String, Boolean> skeletal;

    private SchemaTable(Map<String, String> namespaces,
                        String rootElement,
                        String rootType,
                        Map<String, Type> types,
                        Map<String, Boolean> skeletal) {
        this.namespaces = namespaces;
        this.rootElement = rootElement;
        this.rootType = rootType;
        this.types = types;
        this.skeletal = skeletal;
    }

    /**
     * Returns the table of one syntax, with the document element of that syntax as its
     * root. The files are read once and shared.
     *
     * @param syntax the syntax
     * @return its schema table
     */
    static SchemaTable of(BindingSyntax syntax) {
        return Loaded.LOADED.get(syntax);
    }

    /**
     * Reads a table from a stream of UTF-8 bytes.
     *
     * @param in        the table, encoded in UTF-8
     * @param document  the local name of the document element to take as the root, or
     *                  {@code null} to take the one the file names at its top level
     * @return the table
     */
    static SchemaTable load(InputStream in, String document) {
        Objects.requireNonNull(in, "in");
        try (JsonParser parser = factory().createParser(in)) {
            return read(parser, document);
        } catch (StreamReadException e) {
            throw new BindingFormatException("a schema table is not a JSON text this"
                    + " reader accepts", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the namespace of every prefix the names of this table use. */
    Map<String, String> namespaces() {
        return namespaces;
    }

    /** Returns the document element, as a prefixed name. */
    String rootElement() {
        return rootElement;
    }

    /** Returns the type of the document element. */
    String rootType() {
        return rootType;
    }

    /** Returns one type, or {@code null} where the schema has none of that name. */
    Type type(String name) {
        return types.get(name);
    }

    /** Returns the names of every type this table carries, in the order it writes them. */
    Set<String> typeNames() {
        return types.keySet();
    }

    /**
     * Tells whether a type can stand as an empty element and still satisfy the schema,
     * once the elements it requires are written along with it.
     *
     * <p>A type is skeletal when its content model is a sequence and every element it
     * requires is itself of a skeletal type. The three header sections of a cross industry
     * invoice are: they are required, they hold nothing that is required in turn, and a
     * document that leaves one of them out is rejected however complete the rest of it is.
     * The exchanged document is not, because it requires an identifier, and an identifier
     * is content rather than structure: a writer that invented one would be writing an
     * invoice number nobody gave it. A value type is never skeletal, because its content
     * model is simple content and an empty one says something the document did not.
     *
     * @param typeName the type
     * @return {@code true} if an element of this type may be created for structure alone
     */
    boolean isSkeletal(String typeName) {
        return Boolean.TRUE.equals(skeletal.get(typeName));
    }

    /**
     * Answers {@link #isSkeletal} for every type of a table, once, while the table is
     * being read.
     *
     * <p>It is worked out here rather than when a writer first asks because the tables are
     * read once and shared between every writer of the process, and a memo filled from the
     * writing path would be shared mutable state: two writers could lose an entry between
     * them, and — worse — one could read the provisional answer the other writes while it
     * walks a chain of required elements and create an element the schema does not admit.
     * A table computed at load time and never written to again cannot do either.
     *
     * @param types the types of the table
     * @return the answer for each of them, which the table then holds unmodifiable
     */
    private static Map<String, Boolean> skeletal(Map<String, Type> types) {
        Map<String, Boolean> answers = new HashMap<>();
        for (String name : types.keySet()) {
            skeletal(types, name, answers, new LinkedHashSet<>());
        }
        return Collections.unmodifiableMap(answers);
    }

    /**
     * Answers it for one type, with the chain of types above it, so that a type that
     * reaches itself through required elements is decided by the rest of its chain rather
     * than by the order the chain is walked in.
     */
    private static boolean skeletal(Map<String, Type> types,
                                    String name,
                                    Map<String, Boolean> answers,
                                    Set<String> walking) {
        Boolean known = answers.get(name);
        if (known != null) {
            return known;
        }
        if (!walking.add(name)) {
            return true;
        }
        Type type = types.get(name);
        boolean answer = type != null && type.model() == Model.SEQUENCE;
        if (answer) {
            for (Child child : type.children()) {
                if (child.required() && (child.type() == null
                        || !skeletal(types, child.type(), answers, walking))) {
                    answer = false;
                    break;
                }
            }
        }
        walking.remove(name);
        answers.put(name, answer);
        return answer;
    }

    /** The content model of a complex type. */
    enum Model {

        /** A sequence of elements, which is what every aggregate of this syntax is. */
        SEQUENCE,

        /** A choice of elements: the date and indicator types of the syntax. */
        CHOICE,

        /** Simple content with attributes: every type that carries a value. */
        SIMPLE;

        static Model of(String token) {
            for (Model model : values()) {
                if (model.name().toLowerCase(Locale.ROOT).equals(token)) {
                    return model;
                }
            }
            throw new BindingFormatException("a schema table names the content model "
                    + token + ", and a complex type of this syntax is a sequence, a choice"
                    + " or simple content");
        }
    }

    /** One complex type: its content model, the elements it admits and its attributes. */
    static final class Type {

        private final String name;
        private final Model model;
        private final List<Child> children;
        private final Map<String, Child> byName;
        private final Map<String, Boolean> attributes;
        private final List<String> required;

        Type(String name, Model model, List<Child> children, Map<String, Boolean> attributes) {
            this.name = name;
            this.model = model;
            this.children = List.copyOf(children);
            this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            List<String> asked = new ArrayList<>(1);
            for (Map.Entry<String, Boolean> attribute : this.attributes.entrySet()) {
                if (Boolean.TRUE.equals(attribute.getValue())) {
                    asked.add(attribute.getKey());
                }
            }
            this.required = List.copyOf(asked);
            Map<String, Child> index = new LinkedHashMap<>();
            for (Child child : this.children) {
                index.put(child.name(), child);
            }
            this.byName = Collections.unmodifiableMap(index);
        }

        /** Returns the name of this type, prefixed. */
        String name() {
            return name;
        }

        /** Returns the content model. */
        Model model() {
            return model;
        }

        /** Returns the elements this type admits, in the order the schema admits them. */
        List<Child> children() {
            return children;
        }

        /** Returns one element by its prefixed name, or {@code null} where it has none. */
        Child child(String prefixedName) {
            return byName.get(prefixedName);
        }

        /** Tells whether this type carries an attribute of that local name. */
        boolean hasAttribute(String localName) {
            return attributes.containsKey(localName);
        }

        /**
         * Returns the attributes the schema asks every element of this type for, in the
         * order the table writes them.
         *
         * <p>An attribute is as much a part of what the syntax requires as an element is,
         * and the semantic model does not always fill one: UBL asks every amount for the
         * currency it is stated in, and EN 16931-1 states the currency once, as BT-5. A
         * document without BT-5 therefore leaves every amount without the attribute, and
         * the writer names each of them rather than inventing a currency.
         *
         * @return the local names of the required attributes
         */
        List<String> requiredAttributes() {
            return required;
        }
    }

    /**
     * One element a complex type admits.
     *
     * @param name      the prefixed name of the element
     * @param type      the name of its type, or {@code null} where the schema declares the
     *                  type inline and the element carries no element of its own
     * @param required  whether the schema asks for at least one of it
     * @param repeatable whether the schema admits more than one of it
     * @param order     its position in the content model, which is the order a document
     *                  writes its siblings in
     */
    record Child(String name, String type, boolean required, boolean repeatable, int order) { }

    /** Holds the tables, which are read once and shared. */
    private static final class Loaded {

        private static final Map<BindingSyntax, SchemaTable> LOADED = read();

        private Loaded() {
            throw new AssertionError("no instances");
        }

        private static Map<BindingSyntax, SchemaTable> read() {
            Map<BindingSyntax, SchemaTable> tables = new EnumMap<>(BindingSyntax.class);
            for (BindingSyntax syntax : BindingSyntax.values()) {
                String resource = syntax == BindingSyntax.CII ? CII_RESOURCE : UBL_RESOURCE;
                try (InputStream in = SchemaTable.class.getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new BindingFormatException("the schema table " + resource
                                + " is not on the classpath of this module");
                    }
                    tables.put(syntax, load(in, syntax.localName()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return Collections.unmodifiableMap(tables);
        }
    }

    private static JsonFactory factory() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxDocumentLength(MAX_TABLE_BYTES)
                .maxNestingDepth(MAX_TABLE_NESTING)
                .maxStringLength(MAX_TABLE_STRING)
                .maxNameLength(MAX_TABLE_STRING)
                .maxNumberLength(MAX_TABLE_STRING)
                .build();
        return new JsonFactoryBuilder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .streamReadConstraints(constraints)
                .build();
    }

    private static SchemaTable read(JsonParser parser, String document) throws IOException {
        expect(parser.nextToken() == JsonToken.START_OBJECT, "the table is a JSON object");
        Map<String, String> namespaces = new LinkedHashMap<>();
        String rootElement = null;
        String rootType = null;
        Map<String, Map<String, String>> documents = new LinkedHashMap<>();
        Map<String, Type> types = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String member = parser.currentName();
            parser.nextToken();
            switch (member) {
                case "namespaces" -> namespaces.putAll(strings(parser));
                case "rootElement" -> rootElement = parser.getText();
                case "rootType" -> rootType = parser.getText();
                case "documents" -> documents.putAll(documents(parser));
                case "types" -> types.putAll(types(parser));
                default -> parser.skipChildren();
            }
        }
        if (!documents.isEmpty()) {
            Map<String, String> root = documents.get(document);
            expect(root != null, "the table describes the document element " + document);
            rootElement = root.get("rootElement");
            rootType = root.get("rootType");
        }
        expect(rootElement != null && rootType != null,
                "the table names the document element and its type");
        expect(!namespaces.isEmpty(), "the table names the namespaces of its element names");
        expect(types.containsKey(rootType), "the table carries the type of the document"
                + " element");
        Map<String, Type> declared = Collections.unmodifiableMap(types);
        return new SchemaTable(Collections.unmodifiableMap(namespaces), rootElement, rootType,
                declared, skeletal(declared));
    }

    /** Reads the {@code documents} member: one document element and type per entry. */
    private static Map<String, Map<String, String>> documents(JsonParser parser)
            throws IOException {
        Map<String, Map<String, String>> documents = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String name = parser.currentName();
            parser.nextToken();
            documents.put(name, strings(parser));
        }
        return documents;
    }

    private static Map<String, String> strings(JsonParser parser) throws IOException {
        Map<String, String> members = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String member = parser.currentName();
            parser.nextToken();
            members.put(member, parser.getText());
        }
        return members;
    }

    private static Map<String, Type> types(JsonParser parser) throws IOException {
        Map<String, Type> types = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String name = parser.currentName();
            parser.nextToken();
            types.put(name, type(parser, name));
        }
        return types;
    }

    private static Type type(JsonParser parser, String name) throws IOException {
        Model model = null;
        List<Child> children = new ArrayList<>();
        Map<String, Boolean> attributes = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String member = parser.currentName();
            parser.nextToken();
            switch (member) {
                case "model" -> model = Model.of(parser.getText());
                case "elements" -> children.addAll(children(parser));
                case "attributes" -> attributes.putAll(attributes(parser));
                default -> parser.skipChildren();
            }
        }
        expect(model != null, "the type " + name + " names its content model");
        return new Type(name, model, children, attributes);
    }

    private static List<Child> children(JsonParser parser) throws IOException {
        List<Child> children = new ArrayList<>();
        while (parser.nextToken() == JsonToken.START_OBJECT) {
            String name = null;
            String type = null;
            boolean required = false;
            boolean repeatable = false;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String member = parser.currentName();
                JsonToken token = parser.nextToken();
                switch (member) {
                    case "name" -> name = parser.getText();
                    case "type" -> type = token == JsonToken.VALUE_NULL ? null
                            : parser.getText();
                    case "required" -> required = token == JsonToken.VALUE_TRUE;
                    case "repeatable" -> repeatable = token == JsonToken.VALUE_TRUE;
                    default -> parser.skipChildren();
                }
            }
            expect(name != null, "every element of the table carries a name");
            children.add(new Child(name, type, required, repeatable, children.size()));
        }
        return children;
    }

    private static Map<String, Boolean> attributes(JsonParser parser) throws IOException {
        Map<String, Boolean> attributes = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.START_OBJECT) {
            String name = null;
            boolean required = false;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String member = parser.currentName();
                JsonToken token = parser.nextToken();
                switch (member) {
                    case "name" -> name = parser.getText();
                    case "required" -> required = token == JsonToken.VALUE_TRUE;
                    default -> parser.skipChildren();
                }
            }
            expect(name != null, "every attribute of the table carries a name");
            attributes.put(name, required);
        }
        return attributes;
    }

    private static void expect(boolean condition, String what) {
        if (!condition) {
            throw new BindingFormatException("a schema table is malformed: " + what);
        }
    }
}
