package de.bsnsoft.esj.generator;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The configuration the code list enums are generated from, {@code model/enums.json}, read
 * as data.
 *
 * <p>It says which enum is made of which snapshots of which rule pack, which business term
 * list of the registry the enum belongs to, whether a constant is named after the code or
 * after the name its publisher gives the code, and — for the one list whose published names
 * are sentences — how a constant is named instead. The file also carries the naming rule in
 * words, beside the data it applies to.
 */
final class EnumFacts {

    private EnumFacts() {
    }

    /**
     * Reads the configuration.
     *
     * @param file the configuration file
     * @return what the file says
     * @throws IOException           if the file cannot be read
     * @throws IllegalStateException if a member the generator needs is missing
     */
    static Configuration read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             JsonParser parser = new JsonFactory().createParser(in)) {
            String packageName = null;
            String packId = null;
            String packVersion = null;
            List<Spec> enums = new ArrayList<>();
            expect(parser.nextToken(), JsonToken.START_OBJECT, file);
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "package" -> packageName = parser.getText();
                    case "pack" -> {
                        expect(parser.currentToken(), JsonToken.START_OBJECT, file);
                        while (parser.nextToken() == JsonToken.FIELD_NAME) {
                            String member = parser.currentName();
                            parser.nextToken();
                            if ("id".equals(member)) {
                                packId = parser.getText();
                            } else if ("version".equals(member)) {
                                packVersion = parser.getText();
                            } else {
                                parser.skipChildren();
                            }
                        }
                    }
                    case "enums" -> {
                        expect(parser.currentToken(), JsonToken.START_ARRAY, file);
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            enums.add(spec(parser, file));
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
            required(packageName, "package", file);
            required(packId, "pack.id", file);
            required(packVersion, "pack.version", file);
            if (enums.isEmpty()) {
                throw new IllegalStateException(file + " names no enum");
            }
            return new Configuration(packageName, packId, packVersion, List.copyOf(enums));
        }
    }

    private static Spec spec(JsonParser parser, Path file) throws IOException {
        expect(parser.currentToken(), JsonToken.START_OBJECT, file);
        String type = null;
        String summary = null;
        String registryList = null;
        String source = null;
        List<String> lists = new ArrayList<>();
        Map<String, String> constants = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "type" -> type = parser.getText();
                case "summary" -> summary = parser.getText();
                case "registryList" -> registryList = parser.getText();
                case "source" -> source = parser.getText();
                case "lists" -> {
                    expect(parser.currentToken(), JsonToken.START_ARRAY, file);
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        lists.add(parser.getText());
                    }
                }
                case "constants" -> {
                    expect(parser.currentToken(), JsonToken.START_OBJECT, file);
                    while (parser.nextToken() == JsonToken.FIELD_NAME) {
                        String code = parser.currentName();
                        parser.nextToken();
                        constants.put(code, parser.getText());
                    }
                }
                default -> parser.skipChildren();
            }
        }
        required(type, "type", file);
        required(summary, "summary", file);
        required(registryList, "registryList", file);
        required(source, "source", file);
        if (lists.isEmpty()) {
            throw new IllegalStateException(file + " names no code list for the enum " + type);
        }
        if (!"code".equals(source) && !"name".equals(source)) {
            throw new IllegalStateException(file + " names the constant source " + source
                    + " for the enum " + type + ", which is neither code nor name");
        }
        return new Spec(type, summary, registryList, "code".equals(source), List.copyOf(lists),
                Map.copyOf(constants));
    }

    /**
     * Reads which snapshot of which code list a rule pack decides against.
     *
     * @param manifest the {@code pack.json} of the pack
     * @return the day of the snapshot of each list, by list identifier
     * @throws IOException           if the manifest cannot be read
     * @throws IllegalStateException if it carries no {@code codeLists} object
     */
    static Map<String, String> snapshotDates(Path manifest) throws IOException {
        try (InputStream in = Files.newInputStream(manifest);
             JsonParser parser = new JsonFactory().createParser(in)) {
            Map<String, String> dates = new LinkedHashMap<>();
            boolean seen = false;
            expect(parser.nextToken(), JsonToken.START_OBJECT, manifest);
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                if ("codeLists".equals(field)) {
                    seen = true;
                    expect(parser.currentToken(), JsonToken.START_OBJECT, manifest);
                    while (parser.nextToken() == JsonToken.FIELD_NAME) {
                        String listId = parser.currentName();
                        parser.nextToken();
                        dates.put(listId, parser.getText());
                    }
                } else {
                    parser.skipChildren();
                }
            }
            if (!seen) {
                throw new IllegalStateException(manifest + " carries no codeLists object");
            }
            return dates;
        }
    }

    /**
     * Reads one code list snapshot, keeping the order its publisher put the codes in.
     *
     * <p>The rule engine reads the same files through its own reader and does not keep
     * that order, because a membership test does not need it. A generated enum does: the
     * order of the constants is the order of the list, and a name two codes would share
     * goes to the first of them.
     *
     * @param file the snapshot file
     * @return the snapshot
     * @throws IOException           if the file cannot be read
     * @throws IllegalStateException if it is not a snapshot or carries a code twice
     */
    static Snapshot snapshot(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             JsonParser parser = new JsonFactory().createParser(in)) {
            String listId = null;
            String name = null;
            String publisher = null;
            String retrieved = null;
            List<Entry> entries = new ArrayList<>();
            expect(parser.nextToken(), JsonToken.START_OBJECT, file);
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "listId" -> listId = parser.getText();
                    case "name" -> name = parser.getText();
                    case "publisher" -> publisher = parser.getText();
                    case "retrieved" -> retrieved = parser.getText();
                    case "entries" -> {
                        expect(parser.currentToken(), JsonToken.START_ARRAY, file);
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            entries.add(entry(parser, file));
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
            required(listId, "listId", file);
            required(name, "name", file);
            required(publisher, "publisher", file);
            required(retrieved, "retrieved", file);
            return new Snapshot(listId, name, publisher, retrieved, List.copyOf(entries));
        }
    }

    private static Entry entry(JsonParser parser, Path file) throws IOException {
        expect(parser.currentToken(), JsonToken.START_OBJECT, file);
        String value = null;
        String name = "";
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            if ("value".equals(field)) {
                value = parser.getText();
            } else if ("name".equals(field)) {
                name = parser.getText();
            } else {
                parser.skipChildren();
            }
        }
        required(value, "value of an entry", file);
        return new Entry(value, name);
    }

    private static void required(String value, String member, Path file) {
        if (value == null) {
            throw new IllegalStateException(file + " carries no " + member);
        }
    }

    private static void expect(JsonToken actual, JsonToken expected, Path file) {
        if (actual != expected) {
            throw new IllegalStateException(file + " is not of the shape this generator reads");
        }
    }

    /**
     * What {@code model/enums.json} says.
     *
     * @param packageName the package the enums are emitted into
     * @param packId      the rule pack whose snapshots they are generated from
     * @param packVersion the version of that pack
     * @param enums       the enums to emit, in the order of the file
     */
    record Configuration(String packageName, String packId, String packVersion, List<Spec> enums) {

        /**
         * Copies the list and checks that every part is present.
         *
         * @throws NullPointerException if a part is {@code null}
         */
        Configuration {
            Objects.requireNonNull(packageName, "packageName");
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(packVersion, "packVersion");
            enums = List.copyOf(enums);
        }
    }

    /**
     * One code list as one publisher published it on one day, in the order of that
     * publication.
     *
     * @param listId    the identifier a rule and this configuration name the list by
     * @param name      the name of the list at its publisher
     * @param publisher who publishes the list
     * @param retrieved the day the snapshot was taken, {@code YYYY-MM-DD}
     * @param entries   the codes, in the order of the list
     */
    record Snapshot(String listId,
                    String name,
                    String publisher,
                    String retrieved,
                    List<Entry> entries) {

        /**
         * Copies the entries and checks that every part is present.
         *
         * @throws NullPointerException if a part is {@code null}
         */
        Snapshot {
            Objects.requireNonNull(listId, "listId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(publisher, "publisher");
            Objects.requireNonNull(retrieved, "retrieved");
            entries = List.copyOf(entries);
        }
    }

    /**
     * One code of a snapshot.
     *
     * @param value the code
     * @param name  the name its publisher gives it, empty where the publisher gives none
     */
    record Entry(String value, String name) {

        /**
         * Checks that both parts are present.
         *
         * @throws NullPointerException if a part is {@code null}
         */
        Entry {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * One enum.
     *
     * @param type         the name of the Java type
     * @param summary      the first sentence of its Javadoc
     * @param registryList the code list as the registry spells it, which is how the terms
     *                     that use the enum are found
     * @param fromCode     whether a constant is named after the code rather than after the
     *                     name its publisher gives the code
     * @param lists        the snapshots the constants come from, in the order they are
     *                     emitted in
     * @param constants    the constant names this file decides rather than derives, by code
     */
    record Spec(String type,
                String summary,
                String registryList,
                boolean fromCode,
                List<String> lists,
                Map<String, String> constants) {

        /**
         * Copies the collections and checks that every part is present.
         *
         * @throws NullPointerException if a part is {@code null}
         */
        Spec {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(registryList, "registryList");
            lists = List.copyOf(lists);
            constants = Map.copyOf(constants);
        }
    }
}
