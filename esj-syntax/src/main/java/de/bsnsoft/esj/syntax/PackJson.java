package de.bsnsoft.esj.syntax;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a pack manifest into plain values.
 *
 * <p>A manifest is a small file of this project's own, written beside the artefacts it
 * describes and checked by the build, so it is read with the streaming parser every other
 * JSON file of this repository is read with and turned into maps, lists and strings. The
 * result is consumed by {@link PackManifest}, which is where the shape of a manifest is
 * decided; nothing here knows what a component is.
 */
final class PackJson {

    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(32)
                    .build())
            .build();

    private PackJson() {
        throw new AssertionError("no instances");
    }

    /**
     * Reads a JSON document into maps, lists, strings and booleans.
     *
     * @param json  the bytes of the document
     * @param where the name of the file, for a message
     * @return the value at the root of the document
     * @throws PackException if the bytes are not the JSON document a manifest is
     */
    static Object read(byte[] json, String where) {
        try (JsonParser parser = FACTORY.createParser(json)) {
            if (parser.nextToken() == null) {
                throw new PackException(where + " is empty");
            }
            Object value = value(parser, where);
            if (parser.nextToken() != null) {
                throw new PackException(where + " carries more than one JSON value");
            }
            return value;
        } catch (IOException e) {
            throw new PackException(where + " is not the JSON document a pack manifest is", e);
        }
    }

    private static Object value(JsonParser parser, String where) throws IOException {
        JsonToken token = parser.currentToken();
        return switch (token) {
            case START_OBJECT -> object(parser, where);
            case START_ARRAY -> array(parser, where);
            case VALUE_STRING -> parser.getText();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getText();
            case VALUE_NULL -> null;
            default -> throw new PackException(where + " carries the unexpected token "
                    + token + " at " + parser.currentLocation());
        };
    }

    private static Map<String, Object> object(JsonParser parser, String where)
            throws IOException {
        Map<String, Object> members = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.currentName();
            parser.nextToken();
            if (members.put(name, value(parser, where)) != null) {
                throw new PackException(where + " names the member " + name + " twice");
            }
        }
        return members;
    }

    private static List<Object> array(JsonParser parser, String where) throws IOException {
        List<Object> elements = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            elements.add(value(parser, where));
        }
        return elements;
    }
}
