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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The two configuration files the constrained builder is generated from, read as data:
 * {@code model/derivable-terms.json} and the profile overlays under
 * {@code model/profiles/}.
 *
 * <p>Neither file states a fact of EN 16931-1 that the registry already carries. The
 * first names the terms a derivation policy of this SDK writes, which is a policy and not
 * a property of the model; the second records, per profile, the cardinalities a profile
 * narrows and the values it fixes. Both are read here rather than hard-coded so that a
 * further profile is a file and not a change to the generator.
 */
final class BuildFacts {

    private BuildFacts() {
    }

    /**
     * Reads the identifiers of the terms and groups a derivation policy writes.
     *
     * @param file the configuration file
     * @return the identifiers, in the order the file lists them
     * @throws IOException           if the file cannot be read
     * @throws IllegalStateException if the file does not carry a {@code derivable} array
     */
    static Set<String> derivable(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             JsonParser parser = factory().createParser(in)) {
            Set<String> ids = new LinkedHashSet<>();
            boolean seen = false;
            expect(parser.nextToken(), JsonToken.START_OBJECT, file);
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                if ("derivable".equals(field)) {
                    seen = true;
                    expect(parser.currentToken(), JsonToken.START_ARRAY, file);
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        ids.add(parser.getText());
                    }
                } else {
                    parser.skipChildren();
                }
            }
            if (!seen) {
                throw new IllegalStateException(file + " carries no derivable array");
            }
            return ids;
        }
    }

    /**
     * Reads every profile overlay of a directory, in the order of the file names.
     *
     * @param directory the directory holding the overlays
     * @return the profiles, the base profile first
     * @throws IOException           if a file cannot be read
     * @throws IllegalStateException if no profile is a base profile, if more than one is,
     *                               or if two profiles share a constant or a type suffix
     */
    static List<Profile> profiles(Path directory) throws IOException {
        List<Profile> profiles = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                if (file.getFileName().toString().endsWith(".json")) {
                    profiles.add(profile(file));
                }
            }
        }
        List<Profile> ordered = new ArrayList<>();
        for (Profile profile : profiles) {
            if (profile.base()) {
                ordered.add(0, profile);
            } else {
                ordered.add(profile);
            }
        }
        if (ordered.isEmpty() || !ordered.get(0).base()) {
            throw new IllegalStateException(directory + " holds no base profile");
        }
        Set<String> constants = new LinkedHashSet<>();
        Set<String> suffixes = new LinkedHashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            Profile profile = ordered.get(i);
            if (i > 0 && profile.base()) {
                throw new IllegalStateException(directory + " holds more than one base profile");
            }
            if (!constants.add(profile.constant()) || !suffixes.add(profile.typeSuffix())) {
                throw new IllegalStateException(
                        "two profiles share a constant or a type suffix: " + profile.constant());
            }
        }
        return List.copyOf(ordered);
    }

    private static Profile profile(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             JsonParser parser = factory().createParser(in)) {
            String id = null;
            String constant = null;
            String name = null;
            String typeSuffix = null;
            boolean base = false;
            Map<String, String> fixed = new LinkedHashMap<>();
            Map<String, Narrowing> narrows = new LinkedHashMap<>();
            expect(parser.nextToken(), JsonToken.START_OBJECT, file);
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "id" -> id = parser.getText();
                    case "constant" -> constant = parser.getText();
                    case "name" -> name = parser.getText();
                    case "typeSuffix" -> typeSuffix = parser.getText();
                    case "base" -> base = parser.getBooleanValue();
                    case "fixed" -> readFixed(parser, fixed, file);
                    case "narrows" -> readNarrows(parser, narrows, file);
                    default -> parser.skipChildren();
                }
            }
            required(id, "id", file);
            required(constant, "constant", file);
            required(name, "name", file);
            if (typeSuffix == null) {
                throw new IllegalStateException(file + " carries no typeSuffix");
            }
            return new Profile(id, constant, name, typeSuffix, base,
                    Map.copyOf(fixed), Map.copyOf(narrows));
        }
    }

    private static void readFixed(JsonParser parser, Map<String, String> fixed, Path file)
            throws IOException {
        expect(parser.currentToken(), JsonToken.START_ARRAY, file);
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            expect(parser.currentToken(), JsonToken.START_OBJECT, file);
            String id = null;
            String value = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "id" -> id = parser.getText();
                    case "value" -> value = parser.getText();
                    default -> parser.skipChildren();
                }
            }
            required(id, "id of a fixed value", file);
            required(value, "value of a fixed value", file);
            fixed.put(id, value);
        }
    }

    private static void readNarrows(JsonParser parser, Map<String, Narrowing> narrows, Path file)
            throws IOException {
        expect(parser.currentToken(), JsonToken.START_ARRAY, file);
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            expect(parser.currentToken(), JsonToken.START_OBJECT, file);
            String id = null;
            int min = -1;
            int max = -1;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "id" -> id = parser.getText();
                    case "min" -> min = parser.getIntValue();
                    case "max" -> max = parser.getIntValue();
                    default -> parser.skipChildren();
                }
            }
            required(id, "id of a narrowing", file);
            if (min < 0 || max < min) {
                throw new IllegalStateException(file + " gives " + id + " no profile cardinality");
            }
            narrows.put(id, new Narrowing(id, min, max));
        }
    }

    private static void required(String value, String what, Path file) {
        if (value == null) {
            throw new IllegalStateException(file + " carries no " + what);
        }
    }

    private static void expect(JsonToken token, JsonToken expected, Path file) {
        if (token != expected) {
            throw new IllegalStateException(file + " is not shaped as a profile overlay");
        }
    }

    private static JsonFactory factory() {
        return new JsonFactory();
    }

    /**
     * One profile the builder is generated for.
     *
     * @param id         the identifier of the profile
     * @param constant   the name of the constant the generated {@code Profile} class
     *                   carries it under
     * @param name       the name a message prints
     * @param typeSuffix what the generated types of this profile are suffixed with, empty
     *                   for the base profile
     * @param base       whether this is the profile the others are variants of
     * @param fixed      the values the profile writes into a new invoice, by term
     *                   identifier
     * @param narrows    the cardinalities the profile narrows, by term identifier
     */
    record Profile(String id,
                   String constant,
                   String name,
                   String typeSuffix,
                   boolean base,
                   Map<String, String> fixed,
                   Map<String, Narrowing> narrows) {

        /**
         * Copies the maps and checks that every part is present.
         *
         * @throws NullPointerException if a part is {@code null}
         */
        Profile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(constant, "constant");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(typeSuffix, "typeSuffix");
            fixed = Map.copyOf(fixed);
            narrows = Map.copyOf(narrows);
        }

        /**
         * Tells whether this profile makes a term or group mandatory that the registry
         * leaves optional.
         *
         * @param id the identifier of the term or group
         * @return whether the profile narrows it to at least one occurrence
         */
        boolean requires(String id) {
            Narrowing narrowing = narrows.get(id);
            return narrowing != null && narrowing.min() >= 1;
        }
    }

    /**
     * One cardinality a profile narrows.
     *
     * @param id  the identifier of the term or group
     * @param min the minimum number of occurrences the profile allows
     * @param max the maximum number of occurrences the profile allows
     */
    record Narrowing(String id, int min, int max) {

        /**
         * Checks that the identifier is present.
         *
         * @throws NullPointerException if {@code id} is {@code null}
         */
        Narrowing {
            Objects.requireNonNull(id, "id");
        }
    }
}
