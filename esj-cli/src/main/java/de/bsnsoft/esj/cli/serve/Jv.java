package de.bsnsoft.esj.cli.serve;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import de.bsnsoft.esj.Esj;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A JSON value, read and written with the streaming parser and generator of jackson-core.
 *
 * <p>The messages of the protocols and the reports of the child processes are small trees,
 * and this is the smallest model of them: an object keeps the order of its members, a number
 * keeps the text it was written with — a JSON-RPC identifier comes back exactly as it was
 * sent, and no amount passes through a floating point type — and nothing is bound to a class
 * by reflection, which is what lets the native executable run it without configuration.
 */
sealed interface Jv {

    /** The JSON {@code null}. */
    Jv NULL = new Null();

    /** A string. */
    record Str(String value) implements Jv {
    }

    /** A number, as the text it was written with. */
    record Num(String text) implements Jv {
    }

    /** {@code true} or {@code false}. */
    record Bool(boolean value) implements Jv {
    }

    /** The JSON {@code null}; {@link #NULL} is its one instance. */
    record Null() implements Jv {
    }

    /**
     * A string member of a message that was not held but written to a file as it was read:
     * the {@code content_base64} of an upload, whose length is the upload's and not the
     * message's.
     *
     * @param file       the file, the characters of the string as UTF-8
     * @param characters the length of the string, in characters
     * @param complete   whether every character is in the file; not, where the string was
     *                   longer than the bound it was read with
     */
    record Blob(java.nio.file.Path file, long characters, boolean complete) implements Jv {
    }

    /**
     * A value that was written already, compactly, and is kept in a spool rather than as a
     * tree: the result of a call, written while the call held its child's place
     * ({@link Capacity}). Writing a message that carries it copies its bytes; the transport
     * that sends the message gives the spool back ({@link #release(Jv)}).
     *
     * @param spool the bytes of the value, closed
     */
    record Raw(Spool spool) implements Jv {
    }

    /** An array. */
    record Arr(List<Jv> items) implements Jv {
        public Arr {
            items = List.copyOf(items);
        }
    }

    /** An object; its members keep the order they were read or put in. */
    record Obj(Map<String, Jv> members) implements Jv {
        public Obj {
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }

        /** Returns a member, if there is one. */
        Optional<Jv> get(String name) {
            return Optional.ofNullable(members.get(name));
        }

        /** Returns a member that is a string, if there is one. */
        Optional<String> string(String name) {
            return get(name).filter(Str.class::isInstance).map(v -> ((Str) v).value());
        }
    }

    /** The greatest nesting a message may have. */
    int MAX_DEPTH = 64;

    /** Builds a string. */
    static Jv of(String value) {
        return value == null ? NULL : new Str(value);
    }

    /** Builds a number. */
    static Jv of(long value) {
        return new Num(Long.toString(value));
    }

    /** Builds a boolean. */
    static Jv of(boolean value) {
        return new Bool(value);
    }

    /** Builds an array. */
    static Jv array(List<? extends Jv> items) {
        return new Arr(new ArrayList<>(items));
    }

    /** Starts an object. */
    static Builder object() {
        return new Builder();
    }

    /** Builds an object member by member, in order. */
    final class Builder {
        private final Map<String, Jv> members = new LinkedHashMap<>();

        /** Puts a member; a {@code null} value is written as JSON {@code null}. */
        Builder put(String name, Jv value) {
            members.put(name, value == null ? NULL : value);
            return this;
        }

        Builder put(String name, String value) {
            return put(name, Jv.of(value));
        }

        Builder put(String name, long value) {
            return put(name, Jv.of(value));
        }

        Builder put(String name, boolean value) {
            return put(name, Jv.of(value));
        }

        /** Puts a member only where the value is present. */
        Builder putIfPresent(String name, Optional<? extends Jv> value) {
            value.ifPresent(v -> members.put(name, v));
            return this;
        }

        Obj build() {
            return new Obj(members);
        }
    }

    /**
     * Reads one JSON value, refusing anything after it.
     *
     * @param bytes     the UTF-8 text
     * @param maxString the longest string, in characters
     * @return the value
     * @throws JsonException if the bytes are not one JSON value within the bounds
     */
    static Jv parse(byte[] bytes, int maxString) {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(MAX_DEPTH)
                        .maxStringLength(maxString)
                        .maxNumberLength(64)
                        .maxNameLength(4096)
                        .build())
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .build();
        try (JsonParser parser = factory.createParser(bytes)) {
            if (parser.nextToken() == null) {
                throw new JsonException("the message is empty");
            }
            Jv value = read(parser);
            if (parser.nextToken() != null) {
                throw new JsonException("the message carries more than one JSON value");
            }
            return value;
        } catch (IOException e) {
            throw new JsonException("the message is not JSON: " + reason(e));
        }
    }

    private static Jv read(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        switch (token) {
            case VALUE_STRING:
                return new Str(parser.getText());
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return new Num(parser.getText());
            case VALUE_TRUE:
                return new Bool(true);
            case VALUE_FALSE:
                return new Bool(false);
            case VALUE_NULL:
                return NULL;
            case START_ARRAY: {
                List<Jv> items = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    items.add(read(parser));
                }
                return new Arr(items);
            }
            case START_OBJECT: {
                Map<String, Jv> members = new LinkedHashMap<>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String name = parser.currentName();
                    parser.nextToken();
                    if (members.put(name, read(parser)) != null) {
                        throw new JsonException("the member " + name + " appears twice");
                    }
                }
                return new Obj(members);
            }
            default:
                throw new JsonException("unexpected token " + token);
        }
    }

    private static String reason(IOException failure) {
        String message = String.valueOf(failure.getMessage());
        int source = message.indexOf("\n at [Source");
        String text = source < 0 ? message : message.substring(0, source);
        int line = text.indexOf('\n');
        return (line < 0 ? text : text.substring(0, line)).strip();
    }

    /** Writes this value compactly, on one line. */
    default byte[] toBytes() {
        return write(this, false);
    }

    /**
     * Writes this value to a stream as it is generated, without a copy of it in the heap.
     *
     * @param out    the stream, which is not closed
     * @param pretty with two spaces of indentation and a final line feed, as
     *               {@link #toPrettyBytes()} writes it; compactly, as {@link #toBytes()},
     *               otherwise
     * @throws IOException where the stream fails
     */
    default void writeTo(java.io.OutputStream out, boolean pretty) throws IOException {
        try (JsonGenerator generator = Writing.FACTORY.createGenerator(out, JsonEncoding.UTF8)) {
            if (pretty) {
                generator.setPrettyPrinter(Writing.printer());
            }
            write(this, generator);
        }
        if (pretty) {
            out.write('\n');
        }
    }

    /**
     * Gives back the spools of every {@link Raw} value in a message, once it is sent or will
     * not be.
     *
     * @param value the message
     */
    static void release(Jv value) {
        if (value instanceof Raw raw) {
            raw.spool().release();
        } else if (value instanceof Arr arr) {
            arr.items().forEach(Jv::release);
        } else if (value instanceof Obj obj) {
            obj.members().values().forEach(Jv::release);
        }
    }

    /**
     * Tells whether a message carries a value written already ({@link Raw}).
     *
     * @param value the message
     * @return whether it does
     */
    static boolean carriesRaw(Jv value) {
        if (value instanceof Raw) {
            return true;
        }
        if (value instanceof Arr arr) {
            return arr.items().stream().anyMatch(Jv::carriesRaw);
        }
        if (value instanceof Obj obj) {
            return obj.members().values().stream().anyMatch(Jv::carriesRaw);
        }
        return false;
    }

    /** Writes this value with two spaces of indentation and a final line feed. */
    default byte[] toPrettyBytes() {
        byte[] body = write(this, true);
        byte[] withNewline = java.util.Arrays.copyOf(body, body.length + 1);
        withNewline[body.length] = '\n';
        return withNewline;
    }

    private static byte[] write(Jv value, boolean pretty) {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        try (JsonGenerator generator = Writing.FACTORY.createGenerator(collected,
                JsonEncoding.UTF8)) {
            if (pretty) {
                generator.setPrettyPrinter(Writing.printer());
            }
            write(value, generator);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return collected.toByteArray();
    }

    private static void write(Jv value, JsonGenerator generator) throws IOException {
        if (value instanceof Str str) {
            generator.writeString(str.value());
        } else if (value instanceof Blob blob) {
            generator.writeString("(" + blob.characters() + " characters, not held)");
        } else if (value instanceof Raw raw) {
            // The separator before the value, then the value itself, after what the generator
            // holds: the bytes go to the generator's own stream.
            if (!(generator.getOutputTarget() instanceof java.io.OutputStream target)) {
                throw new IllegalStateException("a written value goes to a stream of bytes");
            }
            generator.writeRawValue("");
            generator.flush();
            raw.spool().copyTo(target);
        } else if (value instanceof Num num) {
            generator.writeNumber(num.text());
        } else if (value instanceof Bool bool) {
            generator.writeBoolean(bool.value());
        } else if (value instanceof Null) {
            generator.writeNull();
        } else if (value instanceof Arr arr) {
            generator.writeStartArray();
            for (Jv item : arr.items()) {
                write(item, generator);
            }
            generator.writeEndArray();
        } else if (value instanceof Obj obj) {
            generator.writeStartObject();
            for (Map.Entry<String, Jv> member : obj.members().entrySet()) {
                generator.writeFieldName(member.getKey());
                write(member.getValue(), generator);
            }
            generator.writeEndObject();
        }
    }

    /** A JSON text that could not be read. */
    final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        JsonException(String message) {
            super(message);
        }
    }

    /** The generator settings: the escapes of the command line's own reports. */
    final class Writing {
        static final JsonFactory FACTORY = new JsonFactory()
                .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
                .setCharacterEscapes(new TerminalEscapes());

        private Writing() {
        }

        static DefaultPrettyPrinter printer() {
            return new DefaultPrettyPrinter()
                    .withSeparators(Separators.createDefaultInstance()
                            .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                            .withObjectEmptySeparator("")
                            .withArrayEmptySeparator(""))
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        }
    }

    /**
     * Every character {@link Visible#hidden(int)} names is written as an escape — those that
     * {@link Esj#steersATerminal(int)} names as the reports of the command line write them —
     * so that a message carries no raw line separator (a line of the stdio transport is one
     * message), nothing that steers a terminal and nothing a reader cannot see. The
     * generator writes every character outside the basic multilingual plane as the escapes of
     * its two units anyway, a tag character among them.
     */
    final class TerminalEscapes extends CharacterEscapes {
        private static final long serialVersionUID = 1L;

        private final int[] ascii;

        TerminalEscapes() {
            int[] codes = CharacterEscapes.standardAsciiEscapesForJSON();
            codes[0x7F] = CharacterEscapes.ESCAPE_STANDARD;
            this.ascii = codes;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return ascii.clone();
        }

        @Override
        public SerializableString getEscapeSequence(int ch) {
            return Visible.hidden(ch) && !Character.isSurrogate((char) ch)
                    ? new SerializedString(String.format(Locale.ROOT, "\\u%04X", ch))
                    : null;
        }
    }
}
