package de.bsnsoft.esj.model;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a JSON document into maps, lists and strings so that a test can walk it.
 *
 * <p>The production code of this project never builds a tree of a JSON document; it
 * reads what it needs as the parser goes. A test that checks the shape of a checked-in
 * file wants the whole of it, and this is the smallest thing that gives it, on the same
 * streaming parser the rest of the project uses.
 */
public final class JsonTree {

    private static final JsonFactory FACTORY = new JsonFactory();

    private JsonTree() {}

    /** Returns the given JSON text as maps, lists, strings, numbers and booleans. */
    public static Object of(String json) {
        try (JsonParser parser = FACTORY.createParser(json)) {
            parser.nextToken();
            return value(parser);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the member of an object, or null where the object has none. */
    @SuppressWarnings("unchecked")
    public static Object get(Object object, String member) {
        return ((Map<String, Object>) object).get(member);
    }

    /** Returns a member as a map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object object, String member) {
        return (Map<String, Object>) get(object, member);
    }

    /** Returns a member as a list, or an empty list where the object has none. */
    @SuppressWarnings("unchecked")
    public static List<Object> list(Object object, String member) {
        Object value = get(object, member);
        return value == null ? List.of() : (List<Object>) value;
    }

    /** Returns a member as a string, or null where the object has none. */
    public static String text(Object object, String member) {
        return (String) get(object, member);
    }

    /** Returns a member as an int. */
    public static int number(Object object, String member) {
        return ((BigDecimal) get(object, member)).intValueExact();
    }

    private static Object value(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        switch (token) {
            case START_OBJECT:
                Map<String, Object> object = new LinkedHashMap<>();
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    String name = parser.currentName();
                    parser.nextToken();
                    object.put(name, value(parser));
                }
                return object;
            case START_ARRAY:
                List<Object> array = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    array.add(value(parser));
                }
                return array;
            case VALUE_STRING:
                return parser.getText();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return parser.getDecimalValue();
            case VALUE_TRUE:
                return Boolean.TRUE;
            case VALUE_FALSE:
                return Boolean.FALSE;
            case VALUE_NULL:
                return null;
            default:
                throw new IllegalStateException("unexpected token " + token);
        }
    }
}
