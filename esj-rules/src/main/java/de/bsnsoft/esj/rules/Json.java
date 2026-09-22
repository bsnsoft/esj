package de.bsnsoft.esj.rules;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The small JSON value tree a rule file is parsed into before it is compiled.
 *
 * <p>A rule expression is a tree whose shape is not known until it is read, so it is the
 * one place in this project where a streaming parser alone does not do. The tree is
 * defined here rather than taken from a data binding library, because the module carries
 * one run time dependency on purpose and because the four things a rule file may contain —
 * a string, an integer, a boolean and a container of those — are fewer than JSON has.
 *
 * <p>Two of JSON's values are missing and their absence is the point. There is no
 * {@code null}: a member that is not there is absent, and a member that is there is a
 * value, and a rule file that wrote {@code null} would mean one of the two without saying
 * which. There is no fractional number: every decimal of this project is a string in its
 * canonical decimal form ({@code SPEC.md} section 6.4), and a rule that wrote
 * {@code 84.03} as a JSON number would hand the value to a binary floating point type on
 * the way in. A number in a rule file is therefore an integer, and it is a scale, a length
 * or a count.
 */
sealed interface Json {

    /** Text. */
    record Str(String value) implements Json {
    }

    /** A whole number: a scale, a length or a count. */
    record Int(long value) implements Json {
    }

    /** A boolean literal. */
    record Bool(boolean value) implements Json {
    }

    /** An array, in document order. */
    record Arr(List<Json> items) implements Json {
    }

    /** An object, in document order, without duplicate member names. */
    record Obj(Map<String, Json> members) implements Json {
    }

    /** The largest rule file this parser reads, in bytes. */
    long MAX_BYTES = 8L * 1024L * 1024L;

    /** The deepest a rule file may nest. */
    int MAX_DEPTH = 64;

    /**
     * Reads one JSON value from a stream and closes nothing: the caller owns the stream.
     *
     * @param in    the bytes, UTF-8
     * @param what  what is being read, for the message of a failure
     * @return the value tree
     * @throws RulePackException if the bytes are not the JSON this method admits
     */
    static Json read(InputStream in, String what) {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(MAX_DEPTH)
                        .maxDocumentLength(MAX_BYTES)
                        .maxStringLength(64 * 1024)
                        .maxNumberLength(32)
                        .build())
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .build();
        try (JsonParser parser = factory.createParser(in)) {
            if (parser.nextToken() == null) {
                throw new RulePackException(what + " is empty");
            }
            Json value = readValue(parser, what);
            if (parser.nextToken() != null) {
                throw new RulePackException(what + " carries more than one JSON value");
            }
            return value;
        } catch (IOException e) {
            throw new RulePackException(what + " could not be read: " + reason(e), e);
        }
    }

    /**
     * Returns what a parser failure says, without the parser's own vocabulary.
     *
     * <p>A caller handed a broken pack is told what is wrong with the pack. The message of
     * the streaming parser carries two things that belong to this module and not to that
     * caller: the {@code [Source: …]} location, which names a stream the caller never saw
     * and which the parser writes inline as often as on a line of its own, and the names of
     * the parser's configuration members, which are the names of a library this project does
     * not put in its public vocabulary. Both are cut here, together with the clause that
     * introduced the location where cutting it leaves a parenthesis hanging; the cause keeps
     * the full message for {@code --debug}.
     *
     * @param failure what the parser threw
     * @return the first sentence of it, or a fixed sentence where there is none
     */
    private static String reason(IOException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "it is not the JSON this language admits";
        }
        int source = message.indexOf("[Source");
        String text = source < 0 ? message : message.substring(0, source);
        int open = text.lastIndexOf('(');
        if (open >= 0 && text.indexOf(')', open) < 0) {
            text = text.substring(0, open);
        }
        text = text.replaceAll("\\s*\\(from `[^`]*`\\)", "")
                .replaceAll("[\\s;,]+$", "")
                .strip();
        return text.isEmpty() ? "it is not the JSON this language admits" : text;
    }

    private static Json readValue(JsonParser parser, String what) throws IOException {
        JsonToken token = parser.currentToken();
        return switch (token) {
            case VALUE_STRING -> new Str(parser.getText());
            case VALUE_TRUE -> new Bool(true);
            case VALUE_FALSE -> new Bool(false);
            case VALUE_NUMBER_INT -> new Int(parser.getLongValue());
            case START_ARRAY -> readArray(parser, what);
            case START_OBJECT -> readObject(parser, what);
            case VALUE_NUMBER_FLOAT -> throw new RulePackException(what
                    + " carries the fractional number " + parser.getText()
                    + "; a decimal is written as a string in this language");
            case VALUE_NULL -> throw new RulePackException(what
                    + " carries null; a member that has no value is left out");
            default -> throw new RulePackException(what + " carries the unexpected token " + token);
        };
    }

    private static Json readArray(JsonParser parser, String what) throws IOException {
        List<Json> items = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            items.add(readValue(parser, what));
        }
        return new Arr(List.copyOf(items));
    }

    private static Json readObject(JsonParser parser, String what) throws IOException {
        Map<String, Json> members = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.currentName();
            parser.nextToken();
            if (members.put(name, readValue(parser, what)) != null) {
                throw new RulePackException(what + " names the member " + name + " twice");
            }
        }
        return new Obj(Collections.unmodifiableMap(members));
    }

    /**
     * Returns this value as an object.
     *
     * @param what what the value is, for the message of a failure
     * @return the members, in no particular order
     * @throws RulePackException if this value is not an object
     */
    default Map<String, Json> asObject(String what) {
        if (this instanceof Obj obj) {
            return obj.members();
        }
        throw new RulePackException(what + " is not a JSON object");
    }

    /**
     * Returns this value as an array.
     *
     * @param what what the value is, for the message of a failure
     * @return the items, in document order
     * @throws RulePackException if this value is not an array
     */
    default List<Json> asArray(String what) {
        if (this instanceof Arr arr) {
            return arr.items();
        }
        throw new RulePackException(what + " is not a JSON array");
    }

    /**
     * Returns this value as a string.
     *
     * @param what what the value is, for the message of a failure
     * @return the text
     * @throws RulePackException if this value is not a string
     */
    default String asString(String what) {
        if (this instanceof Str str) {
            return str.value();
        }
        throw new RulePackException(what + " is not a JSON string");
    }

    /**
     * Returns this value as an integer in a range.
     *
     * @param what what the value is, for the message of a failure
     * @param min  the smallest value admitted
     * @param max  the largest value admitted
     * @return the number
     * @throws RulePackException if this value is not an integer in the range
     */
    default int asInt(String what, int min, int max) {
        if (this instanceof Int number && number.value() >= min && number.value() <= max) {
            return (int) number.value();
        }
        throw new RulePackException(what + " is not a whole number between " + min + " and " + max);
    }
}
