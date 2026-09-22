package de.bsnsoft.esj.upgrade;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.model.Registry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * How the paths and the structural facts of one edition of a semantic model relate to
 * those of another, read from a mapping file such as
 * {@code model/en16931/upgrade-2017-2026.json}.
 *
 * <p>The file is data and this class is a reader of it. It carries every path of one
 * edition that is not a path of the other, the terms only one of them has, what an upgrade
 * cannot decide on its own and what a downgrade refuses, each with the sentence the file
 * states it in, so that a report says what the mapping says and not what a method name
 * suggests. {@link EditionUpgrade} consults nothing else about the two editions.
 *
 * <p>The mapping files this build carries are found on the classpath beside the
 * registries; {@link #available()} lists them. An edition pair with no file has no
 * mapping, and a build made without an edition carries neither its registry nor the
 * mappings that name it.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class UpgradeMapping {

    /** The fixed value of the {@code format} member of a mapping file. */
    public static final String FORMAT = "EN16931-Semantic-JSON-upgrade";

    /**
     * The mapping files this project ships, as classpath names relative to the package of
     * {@link Registry}. A file that is not on the classpath is not an error: the Maven
     * profile {@code without-edition-2026} builds a distribution without the files of that
     * edition, and a mapping that names it goes with them.
     */
    private static final List<String> RESOURCES = List.of("en16931/upgrade-2017-2026.json");

    /** The largest mapping file this reader accepts, in bytes. */
    private static final long MAX_MAPPING_BYTES = 8L * 1024L * 1024L;

    /** The deepest nesting a mapping file may have. */
    private static final int MAX_MAPPING_NESTING = 16;

    /** The longest string a mapping file may carry. */
    private static final int MAX_MAPPING_STRING = 64 * 1024;

    private final String model;
    private final Edition from;
    private final Edition to;
    private final List<PathRewrite> pathRewrites;
    private final Map<String, AddedTerm> addedTerms;
    private final Set<String> removedTerms;
    private final Map<String, Point> openPoints;
    private final Map<String, Point> refusals;

    private UpgradeMapping(String model,
                           Edition from,
                           Edition to,
                           List<PathRewrite> pathRewrites,
                           Map<String, AddedTerm> addedTerms,
                           Set<String> removedTerms,
                           Map<String, Point> openPoints,
                           Map<String, Point> refusals) {
        this.model = model;
        this.from = from;
        this.to = to;
        this.pathRewrites = List.copyOf(pathRewrites);
        this.addedTerms = Collections.unmodifiableMap(new LinkedHashMap<>(addedTerms));
        this.removedTerms = Collections.unmodifiableSet(new LinkedHashSet<>(removedTerms));
        this.openPoints = Collections.unmodifiableMap(new LinkedHashMap<>(openPoints));
        this.refusals = Collections.unmodifiableMap(new LinkedHashMap<>(refusals));
    }

    /**
     * Returns the mappings this build carries, in the order their files are listed.
     *
     * @return the mappings, empty where this build carries none
     */
    public static List<UpgradeMapping> available() {
        return Holder.LOADED;
    }

    /**
     * Returns the mapping between two editions, whichever way round they are named.
     *
     * <p>The mapping of a pair is one file and it is read in both directions: the
     * downgrade is the upgrade run backwards, with the refusals the file states.
     *
     * @param oneEdition     the edition string of one side, as a registry spells it
     * @param anotherEdition the edition string of the other side
     * @return the mapping, or empty where this build carries none for that pair
     */
    public static Optional<UpgradeMapping> between(String oneEdition, String anotherEdition) {
        Objects.requireNonNull(oneEdition, "oneEdition");
        Objects.requireNonNull(anotherEdition, "anotherEdition");
        for (UpgradeMapping mapping : available()) {
            if (mapping.from.edition().equals(oneEdition)
                    && mapping.to.edition().equals(anotherEdition)) {
                return Optional.of(mapping);
            }
            if (mapping.to.edition().equals(oneEdition)
                    && mapping.from.edition().equals(anotherEdition)) {
                return Optional.of(mapping);
            }
        }
        return Optional.empty();
    }

    /**
     * Reads a mapping from a stream. The stream is not closed.
     *
     * @param in the bytes of a mapping file, UTF-8
     * @return the mapping
     * @throws EsjFormatException   if the bytes are not a mapping file
     * @throws UncheckedIOException if the stream fails
     * @throws NullPointerException if {@code in} is {@code null}
     */
    public static UpgradeMapping load(InputStream in) {
        Objects.requireNonNull(in, "in");
        try (JsonParser parser = factory().createParser(in)) {
            return read(parser);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the short token of the model both editions belong to.
     *
     * @return the {@code model} member, for example {@code EN16931-1}
     */
    public String model() {
        return model;
    }

    /**
     * Returns the older of the two editions.
     *
     * @return the edition the mapping leads away from
     */
    public Edition from() {
        return from;
    }

    /**
     * Returns the newer of the two editions.
     *
     * @return the edition the mapping leads to
     */
    public Edition to() {
        return to;
    }

    /**
     * Returns every path of one edition that is not a path of the other.
     *
     * @return the rewrites, in the order of the file
     */
    public List<PathRewrite> pathRewrites() {
        return pathRewrites;
    }

    /**
     * Returns the terms only the newer edition has, by identifier.
     *
     * @return the added terms, in the order of the file
     */
    public Map<String, AddedTerm> addedTerms() {
        return addedTerms;
    }

    /**
     * Returns the identifiers of the terms only the older edition has.
     *
     * @return the removed terms, empty where the newer edition removed none
     */
    public Set<String> removedTerms() {
        return removedTerms;
    }

    /**
     * Returns what an upgrade cannot decide on its own, by identifier of the point.
     *
     * @return the open points, in the order of the file
     */
    public Map<String, Point> openPoints() {
        return openPoints;
    }

    /**
     * Returns what a downgrade refuses, by identifier of the refusal.
     *
     * @return the refusals, in the order of the file
     */
    public Map<String, Point> refusals() {
        return refusals;
    }

    /**
     * Returns the sentence the file states a point or a refusal in.
     *
     * @param id the identifier of an open point or of a refusal
     * @return the statement, or empty where the file names no such point
     */
    public Optional<String> statement(String id) {
        Point point = openPoints.get(id);
        if (point == null) {
            point = refusals.get(id);
        }
        return Optional.ofNullable(point).map(Point::statement);
    }

    /**
     * Returns the refusal that names a term, for a run that has to say which of them it
     * met.
     *
     * @param term the identifier of the term the refusal is about
     * @return the refusal naming that term, or empty where none does
     */
    public Optional<Point> refusalFor(String term) {
        for (Point refusal : refusals.values()) {
            if (refusal.terms().contains(term)) {
                return Optional.of(refusal);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "UpgradeMapping[" + from.edition() + " -> " + to.edition() + "]";
    }

    /**
     * One side of a mapping: an edition and the string a document of it writes.
     *
     * @param edition       the edition string, as a registry of it spells it
     * @param semanticModel the {@code semanticModel} member a document of that edition
     *                      carries
     */
    public record Edition(String edition, String semanticModel) {

        /**
         * Checks that both strings are there.
         *
         * @param edition       the edition string, as a registry of it spells it
         * @param semanticModel the {@code semanticModel} member a document of that edition
         *                      carries
         * @throws NullPointerException if a string is {@code null}
         */
        public Edition {
            Objects.requireNonNull(edition, "edition");
            Objects.requireNonNull(semanticModel, "semanticModel");
        }
    }

    /** What a path rewrite does to the address of a term. */
    public enum Change {

        /** The term hangs under another parent, so its chain of identifiers differs. */
        MOVED,

        /**
         * The chain of identifiers is the same and the path still differs, because the
         * term became repeatable and its segment now carries an occurrence index
         * (specification, section 5.3).
         */
        INDEX_ADDED;

        static Change ofToken(String token) {
            return switch (token) {
                case "moved" -> MOVED;
                case "index-added" -> INDEX_ADDED;
                default -> throw new EsjFormatException(
                        "not a change a path rewrite makes: " + token);
            };
        }
    }

    /**
     * One path of an edition that is not a path of the other.
     *
     * @param term   the identifier of the term whose address changed
     * @param change what changed
     * @param from   the chain of identifiers in the older edition
     * @param to     the chain of identifiers in the newer edition
     * @param reason why the address changed, in the words of the file
     */
    public record PathRewrite(String term, Change change, List<String> from,
                              List<String> to, String reason) {

        /**
         * Copies the chains and checks that every part is there.
         *
         * @param term   the identifier of the term whose address changed
         * @param change what changed
         * @param from   the chain of identifiers in the older edition
         * @param to     the chain of identifiers in the newer edition
         * @param reason why the address changed, in the words of the file
         * @throws NullPointerException if a part is {@code null}
         */
        public PathRewrite {
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(change, "change");
            Objects.requireNonNull(reason, "reason");
            from = List.copyOf(from);
            to = List.copyOf(to);
        }
    }

    /**
     * One term the newer edition has and the older one has not.
     *
     * @param term        the identifier
     * @param group       whether it is a business group
     * @param path        the chain of identifiers in the newer edition
     * @param cardinality the cardinality in the newer edition, as {@code 0..1} or
     *                    {@code 0..n}
     */
    public record AddedTerm(String term, boolean group, List<String> path, String cardinality) {

        /**
         * Copies the chain and checks that every part is there.
         *
         * @param term        the identifier
         * @param group       whether it is a business group
         * @param path        the chain of identifiers in the newer edition
         * @param cardinality the cardinality in the newer edition, as {@code 0..1} or
         *                    {@code 0..n}
         * @throws NullPointerException if a part is {@code null}
         */
        public AddedTerm {
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(cardinality, "cardinality");
            path = List.copyOf(path);
        }

        /**
         * Tells whether the term may occur more than once, which decides whether its
         * segment carries an occurrence index (specification, section 5.3).
         *
         * @return {@code true} where the declared maximum is unbounded
         */
        public boolean repeatable() {
            return cardinality.endsWith("n");
        }
    }

    /**
     * One open point of an upgrade, or one refusal of a downgrade, as the file states it.
     *
     * @param id        the identifier of the point
     * @param terms     the terms it is about, empty where it is about any term the
     *                  statement fits
     * @param statement the sentence the file states it in
     */
    public record Point(String id, List<String> terms, String statement) {

        /**
         * Copies the terms and checks that every part is there.
         *
         * @param id        the identifier of the point
         * @param terms     the terms it is about, empty where it is about any term the
         *                  statement fits
         * @param statement the sentence the file states it in
         * @throws NullPointerException if a part is {@code null}
         */
        public Point {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(statement, "statement");
            terms = List.copyOf(terms);
        }
    }

    private static JsonFactory factory() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxDocumentLength(MAX_MAPPING_BYTES)
                .maxNestingDepth(MAX_MAPPING_NESTING)
                .maxStringLength(MAX_MAPPING_STRING)
                .build();
        return new JsonFactoryBuilder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .streamReadConstraints(constraints)
                .build();
    }

    private static UpgradeMapping read(JsonParser parser) throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new EsjFormatException("a mapping is a JSON object");
        }
        String format = null;
        String model = null;
        Edition from = null;
        Edition to = null;
        List<PathRewrite> rewrites = new ArrayList<>();
        Map<String, AddedTerm> added = new LinkedHashMap<>();
        Set<String> removed = new LinkedHashSet<>();
        Map<String, Point> points = new LinkedHashMap<>();
        Map<String, Point> refusals = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "format" -> format = text(parser, field);
                case "model" -> model = text(parser, field);
                case "from" -> from = readEdition(parser);
                case "to" -> to = readEdition(parser);
                case "pathRewrites" -> rewrites = readRewrites(parser);
                case "addedTerms" -> added = readAddedTerms(parser);
                case "removedTerms" -> removed = readTermIds(parser);
                case "openPoints" -> points = readPoints(parser);
                case "reverse" -> refusals = readReverse(parser);
                default -> parser.skipChildren();
            }
        }
        if (!FORMAT.equals(format)) {
            throw new EsjFormatException("a mapping carries format " + FORMAT);
        }
        if (model == null || from == null || to == null) {
            throw new EsjFormatException("a mapping carries model, from and to");
        }
        return new UpgradeMapping(model, from, to, rewrites, added, removed, points, refusals);
    }

    private static Edition readEdition(JsonParser parser) throws IOException {
        expectObject(parser, "an end of a mapping");
        String edition = null;
        String semanticModel = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "edition" -> edition = text(parser, field);
                case "semanticModel" -> semanticModel = text(parser, field);
                default -> parser.skipChildren();
            }
        }
        if (edition == null || semanticModel == null) {
            throw new EsjFormatException("an end of a mapping carries edition and semanticModel");
        }
        return new Edition(edition, semanticModel);
    }

    private static List<PathRewrite> readRewrites(JsonParser parser) throws IOException {
        expectArray(parser, "pathRewrites");
        List<PathRewrite> rewrites = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            expectObject(parser, "a path rewrite");
            String term = null;
            String change = null;
            List<String> from = null;
            List<String> to = null;
            String reason = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "term" -> term = text(parser, field);
                    case "change" -> change = text(parser, field);
                    case "from" -> from = strings(parser, field);
                    case "to" -> to = strings(parser, field);
                    case "reason" -> reason = text(parser, field);
                    default -> parser.skipChildren();
                }
            }
            if (term == null || change == null || from == null || to == null || reason == null) {
                throw new EsjFormatException(
                        "a path rewrite carries term, change, from, to and reason");
            }
            rewrites.add(new PathRewrite(term, Change.ofToken(change), from, to, reason));
        }
        return rewrites;
    }

    private static Map<String, AddedTerm> readAddedTerms(JsonParser parser) throws IOException {
        expectArray(parser, "addedTerms");
        Map<String, AddedTerm> added = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            expectObject(parser, "an added term");
            String term = null;
            String kind = null;
            List<String> path = null;
            String cardinality = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "term" -> term = text(parser, field);
                    case "kind" -> kind = text(parser, field);
                    case "path" -> path = strings(parser, field);
                    case "cardinality" -> cardinality = text(parser, field);
                    default -> parser.skipChildren();
                }
            }
            if (term == null || kind == null || path == null || cardinality == null) {
                throw new EsjFormatException(
                        "an added term carries term, kind, path and cardinality");
            }
            added.put(term, new AddedTerm(term, "BG".equals(kind), path, cardinality));
        }
        return added;
    }

    private static Set<String> readTermIds(JsonParser parser) throws IOException {
        expectArray(parser, "removedTerms");
        Set<String> ids = new LinkedHashSet<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            expectObject(parser, "a removed term");
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                if ("term".equals(field)) {
                    ids.add(text(parser, field));
                } else {
                    parser.skipChildren();
                }
            }
        }
        return ids;
    }

    private static Map<String, Point> readPoints(JsonParser parser) throws IOException {
        expectArray(parser, "openPoints");
        Map<String, Point> points = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            Point point = readPoint(parser);
            points.put(point.id(), point);
        }
        return points;
    }

    private static Map<String, Point> readReverse(JsonParser parser) throws IOException {
        expectObject(parser, "reverse");
        Map<String, Point> refusals = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            if ("refusals".equals(field)) {
                expectArray(parser, "refusals");
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    Point refusal = readPoint(parser);
                    refusals.put(refusal.id(), refusal);
                }
            } else {
                parser.skipChildren();
            }
        }
        return refusals;
    }

    private static Point readPoint(JsonParser parser) throws IOException {
        expectObject(parser, "an open point");
        String id = null;
        List<String> terms = List.of();
        String statement = null;
        String behaviour = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "id" -> id = text(parser, field);
                case "terms" -> terms = strings(parser, field);
                case "statement" -> statement = text(parser, field);
                case "behaviour" -> behaviour = text(parser, field);
                default -> parser.skipChildren();
            }
        }
        if (id == null || statement == null) {
            throw new EsjFormatException("an open point carries id and statement");
        }
        if (behaviour != null && !"report".equals(behaviour)) {
            throw new EsjFormatException("an open point is reported and never repaired,"
                    + " and " + id + " asks for " + behaviour);
        }
        return new Point(id, terms, statement);
    }

    private static void expectObject(JsonParser parser, String what) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new EsjFormatException(what + " is a JSON object");
        }
    }

    private static void expectArray(JsonParser parser, String what) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new EsjFormatException(what + " is a JSON array");
        }
    }

    private static List<String> strings(JsonParser parser, String field) throws IOException {
        expectArray(parser, field);
        List<String> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                throw new EsjFormatException("every element of " + field + " is a string");
            }
            values.add(parser.getText());
        }
        return values;
    }

    private static String text(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw new EsjFormatException(field + " is a string");
        }
        return parser.getText();
    }

    /**
     * The mapping files of this build, read once. Which of them are there is a question
     * about the classpath, and it is answered by looking.
     */
    private static final class Holder {

        static final List<UpgradeMapping> LOADED;

        static {
            List<UpgradeMapping> mappings = new ArrayList<>();
            for (String resource : RESOURCES) {
                InputStream in = Registry.class.getResourceAsStream(resource);
                if (in == null) {
                    continue;
                }
                try (InputStream stream = in) {
                    mappings.add(load(stream));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            LOADED = List.copyOf(mappings);
        }

        private Holder() {
        }
    }
}
