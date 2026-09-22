package de.bsnsoft.esj.model;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.TermKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The term registry: the structural facts of a semantic model, loaded from a registry
 * file (specification, section 10). It answers which terms exist, where they sit, how
 * often they may occur, which semantic data type they carry and which supplementary
 * components they allow.
 *
 * <p>The registry files are read with the streaming parser of {@code jackson-core}; no
 * databind, no reflection and no class named in the input take part in it. Instances are
 * immutable and safe to share.
 *
 * <p>A registry of an extension is loaded like any other and combined with the core
 * registry by {@link #withExtension(Registry)}. The combined registry also knows the
 * additional positions an extension group gives to core terms through its
 * {@code reusesTerms} member (specification, section 5.6).
 */
public final class Registry {

    /** The edition key of the core model a build ships unless it ships none. */
    public static final String DEFAULT_EDITION = "2017";

    /**
     * The edition keys of the core model this reader looks for, newest last. A key is the
     * stem of the registry file name under {@code model/en16931/}; which of them a build
     * actually carries is a question about the classpath and is answered by
     * {@link #editions()}, because a registry is data a build may leave out.
     */
    private static final List<String> CORE_EDITION_KEYS = List.of("2017", "2026");

    private static final String XRECHNUNG_RESOURCE = "xrechnung/3.0.2.json";
    private static final String B2C_RESOURCE = "b2c/0.1.json";

    /**
     * The largest registry file this class reads, in bytes. A registry travels between
     * parties like a document does (specification, sections 5.6 and 10.4), so it is read
     * with a bound rather than with the unbounded default of the parser.
     */
    private static final long MAX_REGISTRY_BYTES = 16L * 1024L * 1024L;

    private static final int MAX_REGISTRY_NESTING = 16;

    /**
     * The longest string and the longest member name a registry file may carry. A
     * registry holds identifiers, names and short notes; the parser's own default would
     * let one string fill the whole file, and that string reaches a message.
     */
    private static final int MAX_REGISTRY_STRING = 64 * 1024;

    /** The longest fragment of a registry file a message reproduces. */
    private static final int MESSAGE_EXCERPT = 80;

    /** The one value the {@code transport} member of a registry file takes today. */
    private static final String TRANSPORT_NONE = "none";

    private final String model;
    private final String edition;
    private final String semanticModel;
    private final String version;
    private final String transport;
    private final List<Import> imports;
    private final List<Term> terms;
    private final Map<String, Term> byId;
    private final Map<String, List<Term>> childrenByParent;
    private final Map<String, List<List<String>>> chainsById;

    private Registry(
            String model,
            String edition,
            String version,
            String transport,
            List<Import> imports,
            List<Term> terms) {
        this.model = model;
        this.edition = edition;
        this.semanticModel = edition.replace(" ", "");
        this.version = version;
        this.transport = transport;
        this.imports = List.copyOf(imports);
        this.terms = List.copyOf(terms);

        Map<String, Term> ids = new LinkedHashMap<>();
        for (Term term : this.terms) {
            if (ids.putIfAbsent(term.id(), term) != null) {
                throw new EsjFormatException(
                        "the registry lists " + excerpt(term.id()) + " twice");
            }
        }
        this.byId = Collections.unmodifiableMap(ids);

        Map<String, List<Term>> children = new LinkedHashMap<>();
        for (Term term : this.terms) {
            children.computeIfAbsent(term.parent().orElse(""), key -> new ArrayList<>()).add(term);
        }
        Map<String, List<List<String>>> chains = new LinkedHashMap<>();
        for (Term term : this.terms) {
            chains.computeIfAbsent(term.id(), key -> new ArrayList<>()).add(term.path());
        }
        for (Term group : this.terms) {
            for (String reused : group.reusesTerms()) {
                Term term = ids.get(reused);
                if (term == null) {
                    continue;
                }
                children.computeIfAbsent(group.id(), key -> new ArrayList<>()).add(term);
                List<String> chain = new ArrayList<>(group.path());
                chain.add(reused);
                chains.computeIfAbsent(reused, key -> new ArrayList<>()).add(List.copyOf(chain));
            }
        }
        this.childrenByParent = unmodifiableLists(children);
        this.chainsById = unmodifiableChains(chains);
    }

    /**
     * Returns the registry of the 2017 edition of the core model, read once from the
     * classpath and shared. It is the edition ESJ writes unless a caller asks for
     * another one; {@link #forEdition(String)} reaches the others.
     *
     * @return the registry of EN 16931-1:2017+A1:2019/AC:2020
     * @throws EsjFormatException if the registry file is missing or malformed
     */
    public static Registry en16931() {
        return forEdition(DEFAULT_EDITION);
    }

    /**
     * Returns the registry of one edition of the core model, read once from the classpath
     * and shared.
     *
     * <p>An edition key is the stem of the registry file name under
     * {@code model/en16931/}, for example {@code 2017} or {@code 2026}. Which keys a
     * build carries is a property of the build and not of this class: a registry is data,
     * a distribution may leave one out, and {@link #editions()} says which ones are
     * there.
     *
     * @param edition the edition key
     * @return the registry of that edition
     * @throws EsjFormatException   if this build carries no registry for that edition, or
     *                              if the registry file is malformed
     * @throws NullPointerException if {@code edition} is {@code null}
     */
    public static Registry forEdition(String edition) {
        Objects.requireNonNull(edition, "edition");
        Loaded loaded = CoreHolder.BY_EDITION.get(edition);
        if (loaded == null) {
            throw new EsjFormatException("this build carries no registry of the edition "
                    + excerpt(edition) + "; it carries " + String.join(", ", editions()));
        }
        return loaded.get();
    }

    /**
     * Returns the edition keys of the core model this build carries, in the order the
     * editions were published.
     *
     * @return the edition keys, possibly empty
     */
    public static List<String> editions() {
        return CoreHolder.KEYS;
    }

    /**
     * Returns the registry a document naming this semantic model is measured against: the
     * one whose {@code edition} is that string with the spaces put back (specification,
     * section 10). A validator uses that registry and no other, because a path is an
     * address relative to an edition.
     *
     * @param semanticModel the {@code semanticModel} member of a document
     * @return the registry of that edition, or an empty optional if this build carries
     *         none for it
     * @throws NullPointerException if {@code semanticModel} is {@code null}
     */
    public static Optional<Registry> forSemanticModel(String semanticModel) {
        Objects.requireNonNull(semanticModel, "semanticModel");
        for (String edition : editions()) {
            Registry registry = forEdition(edition);
            if (registry.describes(semanticModel)) {
                return Optional.of(registry);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the registry of the XRechnung extension, read once from the classpath and
     * shared. It describes extension terms only and is meant to be combined with
     * {@link #en16931()} through {@link #withExtension(Registry)}.
     *
     * @return the registry of the extension
     * @throws EsjFormatException if the registry file is missing or malformed
     */
    public static Registry xrechnungExtension() {
        return ExtensionHolder.LOADED.get();
    }

    /**
     * Returns the registry of the B2C extension, read once from the classpath and shared.
     * It carries the four terms that record the gross figures a consumer was shown or
     * agreed to, and is meant to be combined with {@link #en16931()} through
     * {@link #withExtension(Registry)}.
     *
     * @return the registry of the extension
     * @throws EsjFormatException if the registry file is missing or malformed
     */
    public static Registry b2cExtension() {
        return B2cHolder.LOADED.get();
    }

    /**
     * Reads a registry from a stream. The caller closes the stream.
     *
     * <p>The stream is read as strictly as a document is: a member name that occurs twice
     * in one object is refused rather than silently resolved in favour of one of its two
     * values, and the file is bounded in size, in nesting depth and in the length of one
     * string or member name. An extension registry decides which paths a validator
     * considers well formed, so an ambiguity in it is the same kind of defect as an
     * ambiguity in a document (specification, section 4.2, rule 3). A fragment of the
     * file that reaches a message is truncated for the same reason a document's is
     * (specification, section 12.6).
     *
     * @param in the registry file, encoded in UTF-8
     * @return the registry
     * @throws EsjFormatException   if the stream does not hold a registry
     * @throws UncheckedIOException if the stream cannot be read
     * @throws NullPointerException if {@code in} is {@code null}
     */
    public static Registry load(InputStream in) {
        Objects.requireNonNull(in, "in");
        try (JsonParser parser = factory().createParser(in)) {
            return read(parser);
        } catch (StreamConstraintsException e) {
            throw new EsjFormatException("the registry exceeds what this reader accepts: "
                    + excerpt(String.valueOf(e.getOriginalMessage())), e);
        } catch (StreamReadException e) {
            throw new EsjFormatException("the registry is not a JSON text, or a member name"
                    + " occurs twice in one of its objects", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns a fragment of a registry file short enough to put into a message. A
     * registry travels between parties like a document does, so its content does not get
     * to decide how long a log line is (specification, section 12.6).
     */
    private static String excerpt(String value) {
        if (value.length() <= MESSAGE_EXCERPT) {
            return value;
        }
        int end = MESSAGE_EXCERPT;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + "...";
    }

    private static JsonFactory factory() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxDocumentLength(MAX_REGISTRY_BYTES)
                .maxNestingDepth(MAX_REGISTRY_NESTING)
                .maxStringLength(MAX_REGISTRY_STRING)
                .maxNameLength(MAX_REGISTRY_STRING)
                .maxNumberLength(MAX_REGISTRY_STRING)
                .build();
        return new JsonFactoryBuilder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .streamReadConstraints(constraints)
                .build();
    }

    /**
     * Returns a registry that knows the terms of this registry and of an extension.
     *
     * <p>An extension registry declares in its {@code imports} member which model it was
     * written against, and with which edition of it. An import that names this registry's
     * model with another edition is refused rather than combined: the extension's parents,
     * its reused terms and its cardinalities were checked against a different list of
     * terms, and an edition may renumber them (specification, sections 4.4 and 10).
     *
     * @param extension the registry of the extension
     * @return the combined registry, which keeps the model and edition of this one
     * @throws EsjFormatException   if the extension redefines a term of this registry,
     *                              which the specification, section 11.1 forbids, or if it
     *                              imports this model with a different edition
     * @throws NullPointerException if {@code extension} is {@code null}
     */
    public Registry withExtension(Registry extension) {
        Objects.requireNonNull(extension, "extension");
        for (Import imported : extension.imports) {
            if (imported.model().equals(model) && !imported.edition().equals(edition)) {
                throw new EsjFormatException(
                        "the extension was written against "
                                + excerpt(imported.model() + " " + imported.edition())
                                + " and this registry describes "
                                + excerpt(edition));
            }
        }
        List<Term> combined = new ArrayList<>(terms);
        for (Term term : extension.terms) {
            if (byId.containsKey(term.id())) {
                throw new EsjFormatException(
                        "an extension does not redefine the core term " + excerpt(term.id()));
            }
            combined.add(term);
        }
        // The combination is a registry of this model and this edition, and it declares
        // nothing about transport of its own: the declaration belongs to the file that
        // defines the terms, and a caller asks the extension registry that made it.
        return new Registry(model, edition, version, null, imports, combined);
    }

    /**
     * Tells whether this registry declares that the terms it defines have no binding in
     * any transport syntax, by design rather than for want of a table.
     *
     * <p>It is the {@code transport} member of the registry file, whose one value today is
     * {@code none} (specification, section 10). An extension whose terms record something
     * about the document rather than something a syntax carries says so here, and a caller
     * who writes such a document into UBL or CII knows that the terms left behind were
     * never meant to travel: what the writer produced is the whole invoice and not a lossy
     * copy of it. A registry that says nothing carries terms that travel like any other.
     *
     * <p>A combined registry answers {@code false}: the declaration is a fact of the file
     * that defines the terms, so a caller asks the extension registry itself.
     *
     * @return whether the file declares {@code "transport": "none"}
     */
    public boolean withoutTransport() {
        return TRANSPORT_NONE.equals(transport);
    }

    /**
     * Returns the models this registry builds on.
     *
     * @return the {@code imports} member of the registry file, empty for a registry that
     *         describes a model of its own
     */
    public List<Import> imports() {
        return imports;
    }

    /**
     * One entry of the {@code imports} member of a registry file: the model an extension
     * registry builds on, and the exact edition string of the registry it was written
     * against (specification, section 10).
     *
     * @param model   short token of the imported model, for example {@code EN16931-1}
     * @param edition edition string of the imported registry, character for character
     */
    public record Import(String model, String edition) {

        /**
         * Creates an import.
         *
         * @param model   short token of the imported model
         * @param edition edition string of the imported registry
         * @throws NullPointerException if an argument is {@code null}
         */
        public Import {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(edition, "edition");
        }
    }

    /**
     * Returns the version of the registry file itself, which is the version of the
     * registry format rather than of the described model.
     *
     * @return the {@code version} member of the registry file, or an empty optional if
     *         the file carries none
     */
    public Optional<String> version() {
        return Optional.ofNullable(version);
    }

    /**
     * Returns the name of the model this registry describes.
     *
     * @return the {@code model} member of the registry file, for example {@code EN16931-1}
     */
    public String model() {
        return model;
    }

    /**
     * Returns the edition of the model this registry describes, in the spelling the
     * standards body uses: {@code EN 16931-1:2017+A1:2019/AC:2020}.
     *
     * @return the {@code edition} member of the registry file
     */
    public String edition() {
        return edition;
    }

    /**
     * Returns the same edition in the spelling a document uses in its
     * {@code semanticModel} member: the {@link #edition()} with every space removed
     * (specification, section 10). The mapping is mechanical and exact in both
     * directions, because an edition string carries no other whitespace and no space in
     * it is significant.
     *
     * @return the edition as a document writes it, for example
     *         {@code EN16931-1:2017+A1:2019/AC:2020}
     */
    public String semanticModel() {
        return semanticModel;
    }

    /**
     * Tells whether this registry is the one a document naming that edition must be
     * validated against (specification, sections 4.4 and 10). A validator selects the
     * registry this way and uses no other: a path is an address relative to an edition,
     * and a term may be renumbered between two of them.
     *
     * @param semanticModel the {@code semanticModel} member of a document
     * @return {@code true} if this registry describes that edition
     * @throws NullPointerException if {@code semanticModel} is {@code null}
     */
    public boolean describes(String semanticModel) {
        Objects.requireNonNull(semanticModel, "semanticModel");
        return this.semanticModel.equals(semanticModel);
    }

    /**
     * Returns every term of this registry in the order of the table of the described
     * model. In a combined registry the terms of the base model come first.
     *
     * @return an unmodifiable list of terms
     */
    public List<Term> terms() {
        return terms;
    }

    /**
     * Looks up a term by its identifier.
     *
     * @param id the term identifier, for example {@code BT-131}
     * @return the term, or an empty optional if this registry does not know it
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public Optional<Term> term(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns the terms and groups that sit directly inside a group, followed by the
     * terms an extension group carries through its {@code reusesTerms} member.
     *
     * @param parentId the identifier of the enclosing group, or {@code null} for the terms
     *                 and groups at the root of the document
     * @return an unmodifiable list of children, empty if the group has none
     */
    public List<Term> children(String parentId) {
        return childrenByParent.getOrDefault(parentId == null ? "" : parentId, List.of());
    }

    /**
     * Returns the terms and groups that sit at the root of the document.
     *
     * @return an unmodifiable list of children of the root
     */
    public List<Term> rootTerms() {
        return children(null);
    }

    /**
     * Returns the identifier of the group a term sits in.
     *
     * @param id the term identifier
     * @return the identifier of the enclosing group, or an empty optional for a term at
     *         the root
     * @throws IllegalArgumentException if this registry does not know the term
     * @throws NullPointerException     if {@code id} is {@code null}
     */
    public Optional<String> parentOf(String id) {
        return required(id).parent();
    }

    /**
     * Tells whether a term may occur more than once inside one instance of its parent and
     * therefore carries an occurrence index in every path.
     *
     * @param id the term identifier
     * @return {@code true} if the declared maximum cardinality is greater than 1
     * @throws IllegalArgumentException if this registry does not know the term
     * @throws NullPointerException     if {@code id} is {@code null}
     */
    public boolean isRepeatable(String id) {
        return required(id).isRepeatable();
    }

    /**
     * Returns the cardinality a term is declared with.
     *
     * @param id the term identifier
     * @return the declared cardinality
     * @throws IllegalArgumentException if this registry does not know the term
     * @throws NullPointerException     if {@code id} is {@code null}
     */
    public Cardinality cardinality(String id) {
        return required(id).cardinality();
    }

    /**
     * Returns the semantic data type of a term.
     *
     * @param id the term identifier
     * @return the semantic data type, or an empty optional for a business group
     * @throws IllegalArgumentException if this registry does not know the term
     * @throws NullPointerException     if {@code id} is {@code null}
     */
    public Optional<SemanticType> datatype(String id) {
        return required(id).datatype();
    }

    /**
     * Returns the supplementary components of the semantic data type of a term.
     *
     * @param id the term identifier
     * @return an unmodifiable list of components, empty where the type has none
     * @throws IllegalArgumentException if this registry does not know the term
     * @throws NullPointerException     if {@code id} is {@code null}
     */
    public List<Component> components(String id) {
        return required(id).components();
    }

    /**
     * Returns the parent chains a path may follow to reach a term: the chain the term
     * itself records, and one chain for every extension group that carries the term
     * through its {@code reusesTerms} member (specification, section 5.6). Each chain
     * lists the identifiers from the root down to and including the term.
     *
     * @param id the term identifier
     * @return an unmodifiable list of chains, empty if this registry does not know the term
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public List<List<String>> chains(String id) {
        Objects.requireNonNull(id, "id");
        return chainsById.getOrDefault(id, List.of());
    }

    private Term required(String id) {
        Objects.requireNonNull(id, "id");
        Term term = byId.get(id);
        if (term == null) {
            throw new IllegalArgumentException("the registry does not know the term " + id);
        }
        return term;
    }

    private static Map<String, List<Term>> unmodifiableLists(Map<String, List<Term>> source) {
        Map<String, List<Term>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, List<List<String>>> unmodifiableChains(Map<String, List<List<String>>> source) {
        Map<String, List<List<String>>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static boolean hasResource(String name) {
        try (InputStream in = Registry.class.getResourceAsStream(name)) {
            return in != null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Registry loadResource(String name) {
        InputStream in = Registry.class.getResourceAsStream(name);
        if (in == null) {
            throw new EsjFormatException("the registry " + name + " is not on the classpath");
        }
        try (InputStream stream = in) {
            return load(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Registry read(JsonParser parser) throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new EsjFormatException("a registry is a JSON object");
        }
        String model = null;
        String edition = null;
        String version = null;
        String transport = null;
        List<Import> imports = List.of();
        List<Term> terms = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "model" -> model = text(parser, field);
                case "edition" -> edition = text(parser, field);
                case "version" -> version = text(parser, field);
                case "transport" -> transport = text(parser, field);
                case "imports" -> imports = readImports(parser);
                case "terms" -> terms = readTerms(parser);
                default -> parser.skipChildren();
            }
        }
        if (model == null || edition == null || terms == null) {
            throw new EsjFormatException("a registry carries model, edition and terms");
        }
        if (transport != null && !TRANSPORT_NONE.equals(transport)) {
            throw new EsjFormatException("the transport member of a registry is "
                    + TRANSPORT_NONE + ", not " + excerpt(transport));
        }
        List<Term> ordered = new ArrayList<>(terms);
        ordered.sort((left, right) -> Integer.compare(left.order(), right.order()));
        return new Registry(model, edition, version, transport, imports, ordered);
    }

    private static List<Import> readImports(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new EsjFormatException("imports is a JSON array");
        }
        List<Import> imports = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw new EsjFormatException("every element of imports is a JSON object");
            }
            String model = null;
            String edition = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "model" -> model = text(parser, field);
                    case "edition" -> edition = text(parser, field);
                    default -> parser.skipChildren();
                }
            }
            if (model == null || edition == null) {
                throw new EsjFormatException("an import carries model and edition");
            }
            imports.add(new Import(model, edition));
        }
        return imports;
    }

    private static List<Term> readTerms(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new EsjFormatException("terms is a JSON array");
        }
        List<Term> terms = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw new EsjFormatException("every element of terms is a JSON object");
            }
            terms.add(readTerm(parser));
        }
        return terms;
    }

    private static Term readTerm(JsonParser parser) throws IOException {
        String id = null;
        String kind = null;
        String name = null;
        String slug = null;
        String parent = null;
        List<String> path = List.of();
        Integer depth = null;
        Integer min = null;
        Integer max = null;
        SemanticType datatype = null;
        OptionalInt maxDecimals = OptionalInt.empty();
        String maxDecimalsRule = null;
        String codeList = null;
        List<Component> components = List.of();
        List<String> reusesTerms = List.of();
        Integer order = null;
        List<String> reqIds = List.of();
        String description = null;
        List<String> notes = List.of();

        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "id" -> id = text(parser, field);
                case "kind" -> kind = text(parser, field);
                case "name" -> name = text(parser, field);
                case "slug" -> slug = text(parser, field);
                case "parent" -> parent = nullableText(parser, field);
                case "path" -> path = readStrings(parser, field);
                case "depth" -> depth = number(parser, field);
                case "min" -> min = number(parser, field);
                case "max" -> max = readMax(parser);
                case "datatype" -> {
                    String value = nullableText(parser, field);
                    datatype = value == null ? null : SemanticType.fromRegistryDatatype(value);
                }
                case "maxDecimals" -> maxDecimals = OptionalInt.of(number(parser, field));
                case "maxDecimalsRule" -> maxDecimalsRule = text(parser, field);
                case "codeList" -> codeList = text(parser, field);
                case "components" -> components = readComponents(parser);
                case "reusesTerms" -> reusesTerms = readStrings(parser, field);
                case "order" -> order = number(parser, field);
                case "reqIds" -> reqIds = readStrings(parser, field);
                case "description" -> description = text(parser, field);
                case "notes" -> notes = readStrings(parser, field);
                default -> parser.skipChildren();
            }
        }
        if (id == null || kind == null || name == null || slug == null
                || min == null || max == null || order == null || description == null) {
            throw new EsjFormatException("a registry term carries id, kind, name, slug, min, max,"
                    + " order and description");
        }
        Cardinality cardinality = max == Cardinality.UNBOUNDED
                ? Cardinality.unbounded(min)
                : Cardinality.of(min, max);
        return new Term(id,
                TermKind.fromPrefix(kind),
                name,
                slug,
                Optional.ofNullable(parent),
                path,
                depth == null ? Math.max(path.size() - 1, 0) : depth,
                cardinality,
                Optional.ofNullable(datatype),
                maxDecimals,
                Optional.ofNullable(maxDecimalsRule),
                Optional.ofNullable(codeList),
                components,
                reusesTerms,
                order,
                reqIds,
                description,
                notes);
    }

    private static List<Component> readComponents(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new EsjFormatException("components is a JSON array");
        }
        List<Component> components = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw new EsjFormatException("every element of components is a JSON object");
            }
            String id = null;
            String role = null;
            String name = null;
            String schemeList = null;
            Integer min = null;
            Integer max = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "id" -> id = text(parser, field);
                    case "role" -> role = text(parser, field);
                    case "name" -> name = text(parser, field);
                    case "schemeList" -> schemeList = text(parser, field);
                    case "min" -> min = number(parser, field);
                    case "max" -> max = readMax(parser);
                    default -> parser.skipChildren();
                }
            }
            if (id == null || role == null || name == null || min == null || max == null) {
                throw new EsjFormatException("a component carries id, role, name, min and max");
            }
            String roleName = role;
            Component.Role parsedRole = Component.Role.findByMember(roleName)
                    .orElseThrow(() -> new EsjFormatException(
                            "not a component role: " + excerpt(roleName)));
            Cardinality cardinality = max == Cardinality.UNBOUNDED
                    ? Cardinality.unbounded(min)
                    : Cardinality.of(min, max);
            components.add(new Component(id, parsedRole, name, cardinality,
                    Optional.ofNullable(schemeList)));
        }
        return components;
    }

    private static List<String> readStrings(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new EsjFormatException(excerpt(field) + " is a JSON array");
        }
        List<String> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(text(parser, field));
        }
        return values;
    }

    private static int readMax(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return parser.getIntValue();
        }
        if (parser.currentToken() == JsonToken.VALUE_STRING && "n".equals(parser.getText())) {
            return Cardinality.UNBOUNDED;
        }
        throw new EsjFormatException("a maximum cardinality is a number or the string n");
    }

    private static String text(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw new EsjFormatException(excerpt(field) + " is a JSON string");
        }
        return parser.getText();
    }

    private static String nullableText(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        return text(parser, field);
    }

    private static int number(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw new EsjFormatException(excerpt(field) + " is a JSON integer");
        }
        return parser.getIntValue();
    }

    /**
     * One registry read from the classpath, or the failure that reading it produced. The
     * failure is kept rather than thrown out of a class initializer, so that a caller of
     * {@link #en16931()} sees the {@link EsjFormatException} its contract names instead of
     * an {@code ExceptionInInitializerError} on the first call and a
     * {@code NoClassDefFoundError} on every later one.
     */
    private static final class Loaded {

        private final String resource;
        private final Registry registry;
        private final RuntimeException failure;

        private Loaded(String resource) {
            Registry read = null;
            RuntimeException problem = null;
            try {
                read = loadResource(resource);
            } catch (RuntimeException e) {
                problem = e;
            }
            this.resource = resource;
            this.registry = read;
            this.failure = problem;
        }

        Registry get() {
            if (failure != null) {
                throw new EsjFormatException(
                        "the registry " + resource + " could not be read", failure);
            }
            return registry;
        }
    }

    /**
     * The core registries this build carries. Which editions those are is decided once,
     * by looking on the classpath: a build that leaves the registry of an edition out is
     * a build that does not know that edition, and it says so instead of failing.
     */
    private static final class CoreHolder {

        static final List<String> KEYS;
        static final Map<String, Loaded> BY_EDITION;

        static {
            List<String> keys = new ArrayList<>();
            Map<String, Loaded> loaded = new LinkedHashMap<>();
            for (String key : CORE_EDITION_KEYS) {
                String resource = "en16931/" + key + ".json";
                if (hasResource(resource)) {
                    keys.add(key);
                    loaded.put(key, new Loaded(resource));
                }
            }
            KEYS = List.copyOf(keys);
            BY_EDITION = Map.copyOf(loaded);
        }

        private CoreHolder() {
        }
    }

    private static final class ExtensionHolder {
        static final Loaded LOADED = new Loaded(XRECHNUNG_RESOURCE);

        private ExtensionHolder() {
        }
    }

    private static final class B2cHolder {
        static final Loaded LOADED = new Loaded(B2C_RESOURCE);

        private B2cHolder() {
        }
    }
}
