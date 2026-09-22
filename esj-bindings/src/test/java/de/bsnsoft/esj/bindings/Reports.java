package de.bsnsoft.esj.bindings;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/** Reads a checked-in report of the conformance directory, for a test to compare against. */
final class Reports {

    private Reports() {
        throw new AssertionError("no instances");
    }

    /** Returns a JSON text as a tree of maps, lists and strings. */
    static Map<String, Object> parse(String text) {
        try (JsonParser parser = new JsonFactory().createParser(text)) {
            parser.nextToken();
            return object(Json.read(parser));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a value as an object. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return (Map<String, Object>) value;
    }

    /** Returns a value as an array. */
    static List<?> array(Object value) {
        return (List<?>) value;
    }

    /** Returns a value as a string. */
    static String text(Object value) {
        return (String) value;
    }

    /** Returns a value as a whole number, which {@link Json} keeps as the text it was. */
    static int number(Object value) {
        return Integer.parseInt(String.valueOf(value));
    }
}
