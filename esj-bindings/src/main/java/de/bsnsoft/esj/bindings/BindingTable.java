package de.bsnsoft.esj.bindings;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamReadException;
import de.bsnsoft.esj.model.Component;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One binding table of {@code model/bindings}: where each business term of EN 16931-1
 * sits in one of the syntaxes the standard binds.
 *
 * <p>A table holds facts and nothing else — a term identifier, the XPath of its element
 * or attribute, the XPaths of its supplementary components, the element that one instance
 * of a business group is written as, and flags that name how the lexical form of the
 * syntax differs from the semantic value. {@code model/bindings/README.md} names the
 * source the facts were taken from, its release, and the meaning of every flag;
 * {@link #flagDefinitions()} carries those meanings as data.
 *
 * <p>What this class publishes is the table as it is written. The compiled form the
 * reader matches a document against is built from it and is not part of the API: a
 * caller that wants to read a document calls {@link StreamingReader}, and a caller that
 * wants to know what the project claims about a syntax reads the facts here.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class BindingTable {

    /** The largest binding table this class reads, in bytes. */
    private static final int MAX_TABLE_BYTES = 8 * 1024 * 1024;

    /** The deepest JSON nesting a binding table is written with, with room to spare. */
    private static final int MAX_TABLE_NESTING = 32;

    /** The longest string a binding table is written with: an XPath, with room to spare. */
    private static final int MAX_TABLE_STRING = 8192;

    private final BindingSyntax syntax;
    private final String edition;
    private final String release;
    private final Map<String, String> namespaces;
    private final Map<String, String> flagDefinitions;
    private final List<Convention> conventions;
    private final List<Entry> entries;
    private final Map<String, Entry> byId;

    private BindingTable(BindingSyntax syntax,
                         String edition,
                         String release,
                         Map<String, String> namespaces,
                         Map<String, String> flagDefinitions,
                         List<Convention> conventions,
                         List<Entry> entries) {
        this.syntax = syntax;
        this.edition = edition;
        this.release = release;
        this.namespaces = Collections.unmodifiableMap(new LinkedHashMap<>(namespaces));
        this.flagDefinitions =
                Collections.unmodifiableMap(new LinkedHashMap<>(flagDefinitions));
        this.conventions = List.copyOf(conventions);
        this.entries = List.copyOf(entries);
        Map<String, Entry> index = new LinkedHashMap<>();
        for (Entry entry : entries) {
            index.put(entry.id(), entry);
        }
        this.byId = Collections.unmodifiableMap(index);
    }

    /**
     * Returns the table of one syntax, read from the resources of this module.
     *
     * <p>The three tables are read once and shared.
     *
     * @param syntax the syntax
     * @return its binding table
     * @throws NullPointerException if {@code syntax} is {@code null}
     */
    public static BindingTable of(BindingSyntax syntax) {
        Objects.requireNonNull(syntax, "syntax");
        return Tables.LOADED.get(syntax);
    }

    /**
     * Reads a binding table from a stream.
     *
     * @param in     the table, encoded in UTF-8
     * @param syntax the syntax it is expected to bind
     * @return the table
     * @throws BindingFormatException if the stream does not hold the binding table of that
     *                                syntax
     * @throws UncheckedIOException   if the stream cannot be read
     * @throws NullPointerException   if an argument is {@code null}
     */
    public static BindingTable load(InputStream in, BindingSyntax syntax) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(syntax, "syntax");
        try (JsonParser parser = factory().createParser(in)) {
            return read(parser, syntax);
        } catch (StreamReadException e) {
            throw new BindingFormatException("the binding table of " + syntax
                    + " is not a JSON text this reader accepts", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the syntax this table binds.
     *
     * @return the syntax
     */
    public BindingSyntax syntax() {
        return syntax;
    }

    /**
     * Returns the edition of the semantic model the table was written against.
     *
     * @return the edition, as the table spells it
     */
    public String edition() {
        return edition;
    }

    /**
     * Returns the edition of the semantic model the table was written against in the
     * spelling a document uses in its {@code semanticModel} member: the {@link #edition()}
     * with every space removed ({@code SPEC.md}, section 10).
     *
     * @return the edition as a document writes it
     */
    public String semanticModel() {
        return edition.replace(" ", "");
    }

    /**
     * Tells whether this table binds the business terms of the edition a document names.
     *
     * <p>A writer asks before it writes anything. A path is an address relative to an
     * edition, so a table of another edition would bind some of the document's paths,
     * miss the rest and produce a syntax instance that is short of business content
     * without saying so.
     *
     * @param semanticModel the {@code semanticModel} member of a document
     * @return {@code true} if the table was written against that edition
     * @throws NullPointerException if {@code semanticModel} is {@code null}
     */
    public boolean describes(String semanticModel) {
        Objects.requireNonNull(semanticModel, "semanticModel");
        return semanticModel().equals(semanticModel);
    }

    /**
     * Returns the release of the source the facts were taken from.
     *
     * @return the release, as the table spells it
     */
    public String sourceRelease() {
        return release;
    }

    /**
     * Returns the namespace of every prefix the XPaths of this table use. The empty
     * prefix is the default namespace of the syntax.
     *
     * @return prefix to namespace URI, in the order the table writes them
     */
    public Map<String, String> namespaces() {
        return namespaces;
    }

    /**
     * Returns the meaning of every flag this table uses, in one line each.
     *
     * @return flag to meaning, in the order the table writes them
     */
    public Map<String, String> flagDefinitions() {
        return flagDefinitions;
    }

    /**
     * Returns the values this syntax is written with where it requires an element that no
     * business term of the semantic model states, in the order the table writes them.
     *
     * <p>A convention is a fact about the syntax rather than about an invoice: which
     * element, under which condition, which value, what asks for the element and where the
     * value comes from. The writer applies them while it writes and names every one it
     * applied in its report; the reader applies {@link Convention#notReadBeside()}, so
     * that a value written for a convention is not read back as a business statement.
     *
     * @return the conventions, empty where the syntax requires no such element
     */
    public List<Convention> conventions() {
        return conventions;
    }

    /**
     * Returns the identifiers of every term the table carries, core terms and extension
     * terms alike, in the order the table writes them.
     *
     * @return the term identifiers
     */
    public List<String> termIds() {
        return entries.stream().map(Entry::id).toList();
    }

    /**
     * Tells whether the table gives a term an XPath in this syntax.
     *
     * <p>A term that is not bound is one the source model records as having no
     * representation in this syntax, or one of an extension that binds for another syntax
     * only. It is not a term the table forgot.
     *
     * @param id the term identifier
     * @return {@code true} if the table gives it at least one XPath
     */
    public boolean isBound(String id) {
        Entry entry = byId.get(id);
        return entry != null && entry.bound();
    }

    /**
     * Returns the XPaths of a term: the one the table names first, then its alternatives.
     *
     * @param id the term identifier
     * @return the XPaths, empty if the term is unbound or unknown to this table
     */
    public List<String> xpaths(String id) {
        Entry entry = byId.get(id);
        return entry == null ? List.of() : entry.paths().stream().map(Path::xpath).toList();
    }

    /**
     * Returns the flags of a term, the flags of its alternatives included.
     *
     * @param id the term identifier
     * @return the flags, empty if the term carries none or is unknown to this table
     */
    public Set<String> flags(String id) {
        Entry entry = byId.get(id);
        return entry == null ? Set.of() : entry.flags();
    }

    /** Returns the entries of this table, in the order it writes them. */
    List<Entry> entries() {
        return entries;
    }

    /** Returns one entry, or {@code null} where the table has none with that identifier. */
    Entry entry(String id) {
        return byId.get(id);
    }

    /**
     * Returns the table as {@code syntax edition}, which is what a message about it needs.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return syntax + " " + edition;
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

    private static BindingTable read(JsonParser parser, BindingSyntax syntax)
            throws IOException {
        expect(parser.nextToken() == JsonToken.START_OBJECT, "the table is a JSON object");
        String edition = null;
        String release = null;
        Map<String, String> namespaces = new LinkedHashMap<>();
        Map<String, String> flags = new LinkedHashMap<>();
        List<Convention> conventions = List.of();
        List<List<Object>> deferred = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String member = parser.currentName();
            parser.nextToken();
            switch (member) {
                case "edition" -> edition = parser.getText();
                case "source" -> release = release(parser);
                case "namespaces" -> namespaces.putAll(strings(parser));
                case "flagDefinitions" -> flags.putAll(strings(parser));
                case "conventions" -> conventions = conventions(Json.read(parser), syntax);
                case "terms" -> deferred.add(List.of(raw(parser)));
                case "extension" -> deferred.add(extension(parser));
                default -> parser.skipChildren();
            }
        }
        expect(edition != null, "the table names the edition it was written against");
        expect(!namespaces.isEmpty(), "the table names the namespaces of its XPaths");
        for (List<Object> terms : deferred) {
            for (Object term : terms) {
                entries.addAll(Entries.parse(term, namespaces, syntax));
            }
        }
        expect(!entries.isEmpty(), "the table carries at least one term");
        return new BindingTable(syntax, edition, release == null ? "" : release,
                namespaces, flags, conventions, merge(entries));
    }

    /**
     * Reads the {@code conventions} array, refusing one that names no element or value and
     * one whose condition is not in the closed vocabulary of the binding schema.
     *
     * <p>The condition decides what a writer does with the row, so a token this release
     * does not know is refused here rather than applied with the meaning of the token it
     * does know. A table that grows a second condition is read by a release that
     * understands it or by none.
     */
    private static List<Convention> conventions(Object array, BindingSyntax syntax) {
        if (!(array instanceof List<?> items)) {
            return List.of();
        }
        List<Convention> conventions = new ArrayList<>(items.size());
        for (Object item : items) {
            String element = Json.text(item, "element");
            String value = Json.text(item, "value");
            String token = Json.text(item, "condition");
            expect(element != null && element.startsWith("/"),
                    "a convention names the element it is written at");
            expect(value != null && !value.isEmpty(), "a convention names its value");
            Condition condition = Condition.ofToken(token);
            expect(condition != null, "the table of " + syntax + " gives the convention at "
                    + element + " the condition '" + token + "', and a condition is one of "
                    + Condition.tokens());
            conventions.add(new Convention(element, condition, value,
                    Json.text(item, "option"), Json.text(item, "requiredBy"),
                    Json.text(item, "term"), Json.text(item, "notReadBeside"),
                    Json.text(item, "source")));
        }
        return List.copyOf(conventions);
    }

    private static String release(JsonParser parser) throws IOException {
        String release = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String member = parser.currentName();
            parser.nextToken();
            if ("release".equals(member)) {
                release = parser.getText();
            } else {
                parser.skipChildren();
            }
        }
        return release;
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

    /** Reads the {@code terms} array into the neutral tree {@link Entries} parses. */
    private static Object raw(JsonParser parser) throws IOException {
        return Json.read(parser);
    }

    /**
     * Reads the {@code extension} object and returns the arrays of terms it carries: the
     * terms the extension defines, and the terms of the base model that its groups carry
     * through their {@code reusesTerms} member.
     */
    private static List<Object> extension(JsonParser parser) throws IOException {
        Object object = Json.read(parser);
        if (!(object instanceof Map<?, ?> members)) {
            return List.of();
        }
        List<Object> arrays = new ArrayList<>();
        for (String member : List.of("terms", "reusedTerms")) {
            Object array = members.get(member);
            if (array != null) {
                arrays.add(array);
            }
        }
        return arrays;
    }

    /**
     * Folds the entries a table states more than once into one entry each.
     *
     * <p>A core term an extension group reuses is bound twice by the same table: once
     * where the standard puts it and once inside the group, which is what
     * {@code extension.reusedTerms} carries. That is one term with two places in the
     * syntax, not two terms, so the second entry's paths join the first entry's and the
     * order the table writes its terms in does not change.
     */
    private static List<Entry> merge(List<Entry> entries) {
        Map<String, Entry> folded = new LinkedHashMap<>();
        for (Entry entry : entries) {
            Entry known = folded.get(entry.id());
            if (known == null) {
                folded.put(entry.id(), entry);
                continue;
            }
            List<Path> paths = new ArrayList<>(known.paths());
            paths.addAll(entry.paths());
            Set<String> flags = new LinkedHashSet<>(known.flags());
            flags.addAll(entry.flags());
            folded.put(entry.id(), new Entry(known.id(), known.kind(),
                    known.bound() || entry.bound(), paths,
                    known.instanceElement() != null
                            ? known.instanceElement() : entry.instanceElement(),
                    flags));
        }
        return List.copyOf(folded.values());
    }

    private static void expect(boolean condition, String what) {
        if (!condition) {
            throw new BindingFormatException("a binding table is malformed: " + what);
        }
    }

    /** Holds the three tables, which are read once and shared. */
    private static final class Tables {

        private static final Map<BindingSyntax, BindingTable> LOADED = load();

        private Tables() {
            throw new AssertionError("no instances");
        }

        private static Map<BindingSyntax, BindingTable> load() {
            Map<BindingSyntax, BindingTable> tables = new EnumMap<>(BindingSyntax.class);
            for (BindingSyntax syntax : BindingSyntax.values()) {
                try (InputStream in =
                             BindingTable.class.getResourceAsStream(syntax.table())) {
                    if (in == null) {
                        throw new BindingFormatException("the binding table "
                                + syntax.table() + " is not on the classpath of this module");
                    }
                    tables.put(syntax, BindingTable.load(in, syntax));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return Collections.unmodifiableMap(tables);
        }
    }

    /** One term of a table: its identifier, its XPaths and its flags. */
    record Entry(String id,
                 String kind,
                 boolean bound,
                 List<Path> paths,
                 String instanceElement,
                 Set<String> flags) {

        Entry {
            paths = List.copyOf(paths);
            flags = Collections.unmodifiableSet(new LinkedHashSet<>(flags));
        }

        boolean isGroup() {
            return "BG".equals(kind);
        }
    }

    /** One XPath of a term: its steps, the attribute it ends in, and its components. */
    record Path(String xpath,
                List<Step> steps,
                String attribute,
                List<ComponentBinding> components,
                Set<String> flags) {

        Path {
            steps = List.copyOf(steps);
            components = List.copyOf(components);
            flags = Collections.unmodifiableSet(new LinkedHashSet<>(flags));
        }
    }

    /**
     * One element step of an XPath: its expanded name, the conditions on it, and whether
     * it follows the descendant axis.
     *
     * <p>A step carries none, one or several predicates, and several hold together. A
     * descendant step is written {@code //name} and is the way the XRechnung extension
     * binds the sub invoice line, which nests inside itself to any depth the document
     * writes. It is the one axis besides the child axis the tables use.
     */
    record Step(String namespace,
                String localName,
                List<Predicate> predicates,
                boolean descendant) {

        /** Copies the conditions. */
        Step {
            predicates = List.copyOf(predicates);
        }
    }

    /** One supplementary component: which one it is and where it is written. */
    record ComponentBinding(Component.Role role, String xpath) { }

    /**
     * When a convention of a binding table applies.
     *
     * <p>The vocabulary is closed: {@code model/bindings/binding.schema.json} declares it
     * as an enumeration, a table that spells a token outside it is refused where it is
     * read, and a writer decides what to do with a row by switching over this type. A
     * second condition therefore arrives as a constant here and as a new case in every
     * switch over it, rather than as a row silently applied with the meaning of the one
     * condition this release knows.
     */
    public enum Condition {

        /**
         * The document has the element above the one the convention names, and no
         * business term of the document states that element.
         *
         * <p>It is the condition of every convention of this release: a syntax that
         * requires an element inside a structure it is writing anyway, where the semantic
         * model either has no term for that element or does not require the term.
         */
        PARENT_WRITTEN_ELEMENT_ABSENT("parent-written-element-absent");

        private final String token;

        Condition(String token) {
            this.token = token;
        }

        /**
         * Returns the token the binding tables and the binding schema spell.
         *
         * @return the token
         */
        public String token() {
            return token;
        }

        /** Returns the condition of a token, or {@code null} where no condition has it. */
        private static Condition ofToken(String token) {
            for (Condition condition : values()) {
                if (condition.token.equals(token)) {
                    return condition;
                }
            }
            return null;
        }

        /** Returns the tokens of the vocabulary, for the message that refuses one. */
        private static List<String> tokens() {
            return Arrays.stream(values()).map(Condition::token).toList();
        }
    }

    /**
     * One value a syntax is written with where it requires an element that no business
     * term of the semantic model states.
     *
     * <p>The three of this release are the tax scheme of a tax registration that is not
     * for value added tax, the purchase order reference of a document that states only a
     * sales order reference, and the card network of a payment card. Each of them is an
     * element one of the two UBL documents asks for and EN 16931-1 either has no term for
     * or does not require, and none of them carries a business statement:
     * {@code conformance/writers/ubl-roundtrip.md} names what each one costs.
     *
     * @param element       the absolute path of the element, as the bound paths are
     *                      spelled and without predicates
     * @param condition     when the convention applies, in the closed vocabulary
     *                      {@link Condition} carries
     * @param value         the value written, which carries no business statement
     * @param option        the writer setting that overrides the value, or {@code null}
     * @param requiredBy    {@code schema} where the XML Schema of the syntax asks for the
     *                      element, otherwise the identifier of the rule that asks for it
     * @param term          the business term whose content the element otherwise carries,
     *                      or {@code null} where no term names it at all
     * @param notReadBeside the prefixed name of a sibling element; where this element
     *                      carries exactly {@code value} and that sibling is present, a
     *                      reader does not read it as {@code term}. {@code null} where
     *                      nothing reads the element back
     * @param source        where the value comes from, with attribution
     */
    public record Convention(String element,
                             Condition condition,
                             String value,
                             String option,
                             String requiredBy,
                             String term,
                             String notReadBeside,
                             String source) {

        /**
         * Checks that the element, the condition and the value are there.
         *
         * @param element       the absolute path of the element
         * @param condition     when the convention applies
         * @param value         the value written
         * @param option        the writer setting that overrides the value, or {@code null}
         * @param requiredBy    what asks for the element
         * @param term          the business term the element otherwise carries, or
         *                      {@code null}
         * @param notReadBeside the sibling this value is not read back beside, or
         *                      {@code null}
         * @param source        where the value comes from
         * @throws NullPointerException if the element, the condition or the value is
         *                              {@code null}
         */
        public Convention {
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(value, "value");
        }

        /**
         * Returns the path of the element this one is written inside.
         *
         * @return the path of the parent element
         */
        public String parent() {
            return element.substring(0, element.lastIndexOf('/'));
        }

        /**
         * Returns the prefixed name of the element itself.
         *
         * @return the last step of {@link #element()}
         */
        public String name() {
            return element.substring(element.lastIndexOf('/') + 1);
        }
    }
}
