package de.bsnsoft.esj.render;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a template file into a tree of maps, lists, strings and numbers, and asks it
 * questions.
 *
 * <p>A template is a small configuration file written by the party that renders, not a
 * document from a stranger, and it is read once per template. A tree is therefore the
 * right shape for it: the reader below is a few lines, the questions are asked where the
 * meaning of a member is known, and a member that is not what it should be is refused
 * with the name of the member in the sentence rather than with an offset into the file.
 */
final class TemplateJson {

    /** The largest template file this reader accepts, in bytes. */
    static final int MAX_TEMPLATE_BYTES = 1024 * 1024;

    /** How deep a template may nest, which is three levels more than the schema needs. */
    private static final int MAX_NESTING = 8;

    /** The longest string a template may carry. */
    private static final int MAX_STRING = 64 * 1024;

    private TemplateJson() {
        throw new AssertionError("no instances");
    }

    /**
     * Reads a template file.
     *
     * @param bytes the file
     * @return the tree
     * @throws TemplateException if the bytes are not a JSON object
     */
    static Object read(byte[] bytes) {
        if (bytes.length > MAX_TEMPLATE_BYTES) {
            throw new TemplateException("a render template is at most " + MAX_TEMPLATE_BYTES
                    + " bytes and this one is " + bytes.length);
        }
        try (JsonParser parser = factory().createParser(bytes)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new TemplateException("a render template is a JSON object");
            }
            Object tree = value(parser);
            if (parser.nextToken() != null) {
                throw new TemplateException("a render template is one JSON object and"
                        + " nothing after it");
            }
            return tree;
        } catch (IOException e) {
            throw new TemplateException("the render template could not be read: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Returns a member of an object.
     *
     * @param value the object
     * @param name  the member
     * @return the member, or {@code null} where it is absent
     */
    static Object member(Object value, String name) {
        return value instanceof Map<?, ?> members ? members.get(name) : null;
    }

    /**
     * Returns a member as a string.
     *
     * @param value the object
     * @param name  the member
     * @param where what the object is, for the message of a refusal
     * @return the member, or {@code null} where it is absent
     * @throws TemplateException if the member is present and is not a string
     */
    static String text(Object value, String name, String where) {
        Object member = member(value, name);
        if (member == null) {
            return null;
        }
        if (!(member instanceof String string)) {
            throw new TemplateException(where + ": " + name + " is a string");
        }
        return string;
    }

    /**
     * Returns a member as a number of points.
     *
     * @param value the object
     * @param name  the member
     * @param where what the object is, for the message of a refusal
     * @return the member, or {@code null} where it is absent
     * @throws TemplateException if the member is present and is not a finite number
     */
    static Float number(Object value, String name, String where) {
        Object member = member(value, name);
        if (member == null) {
            return null;
        }
        if (!(member instanceof String written)) {
            throw new TemplateException(where + ": " + name + " is a number");
        }
        try {
            float number = Float.parseFloat(written);
            if (!Float.isFinite(number)) {
                throw new TemplateException(where + ": " + name + " is a finite number");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new TemplateException(where + ": " + name + " is a number, not '"
                    + written + "'", e);
        }
    }

    /**
     * Returns a member as a list.
     *
     * @param value the object
     * @param name  the member
     * @param where what the object is, for the message of a refusal
     * @return the member, empty where it is absent
     * @throws TemplateException if the member is present and is not an array
     */
    static List<?> list(Object value, String name, String where) {
        Object member = member(value, name);
        if (member == null) {
            return List.of();
        }
        if (!(member instanceof List<?> items)) {
            throw new TemplateException(where + ": " + name + " is an array");
        }
        return items;
    }

    /** Returns the value the parser stands on; a number comes back as the text of it. */
    private static Object value(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            Map<String, Object> members = new LinkedHashMap<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                parser.nextToken();
                members.put(name, value(parser));
            }
            return members;
        }
        if (token == JsonToken.START_ARRAY) {
            List<Object> items = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                items.add(value(parser));
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

    private static JsonFactory factory() {
        return new JsonFactoryBuilder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(MAX_NESTING)
                        .maxStringLength(MAX_STRING)
                        .maxNumberLength(MAX_STRING)
                        .build())
                .build();
    }
}
