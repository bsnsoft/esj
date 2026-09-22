package de.bsnsoft.esj.bindings;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the value a parser stands on into a tree of maps, lists and strings.
 *
 * <p>A binding table is read in two passes: the member that decides how an XPath is read
 * — the namespace map — may be written after the terms, so the terms are kept as a
 * neutral tree until the namespaces are known. That tree is what this class builds, and
 * it is thrown away as soon as the table is compiled.
 */
final class Json {

    private Json() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the value the parser stands on, with objects as maps, arrays as lists,
     * strings as strings and {@code true}, {@code false} and {@code null} as themselves.
     * A number is returned as the text it was written with, which is all a binding table
     * needs of one.
     */
    static Object read(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            Map<String, Object> members = new LinkedHashMap<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                parser.nextToken();
                members.put(name, read(parser));
            }
            return members;
        }
        if (token == JsonToken.START_ARRAY) {
            List<Object> items = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                items.add(read(parser));
            }
            return items;
        }
        return switch (token) {
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            default -> parser.getText();
        };
    }

    /** Returns a member of an object, or {@code null} where the value is not an object. */
    static Object member(Object value, String name) {
        return value instanceof Map<?, ?> members ? members.get(name) : null;
    }

    /** Returns a member as a string, or {@code null} where it is absent or not a string. */
    static String text(Object value, String name) {
        return member(value, name) instanceof String text ? text : null;
    }

    /** Returns a member as a list, empty where it is absent or not a list. */
    static List<?> list(Object value, String name) {
        return member(value, name) instanceof List<?> items ? items : List.of();
    }

    /** Returns a member as a flag, {@code false} where it is absent or not a boolean. */
    static boolean flag(Object value, String name) {
        return Boolean.TRUE.equals(member(value, name));
    }
}
