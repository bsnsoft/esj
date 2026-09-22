package de.bsnsoft.esj.cli;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the reports this tool produces in JSON.
 *
 * <p>These are reports about documents and not documents: a validation report, a listing
 * of paths. They are written with the streaming generator rather than with the ESJ writer
 * because they are not ESJ, and hand-rolling the escaping of a JSON string in a command
 * line tool is how a tool ends up with two spellings of the same character.
 *
 * <p>The indentation is fixed to two spaces and the line ending to a line feed, so that
 * the output of the tool does not depend on the platform it runs on: a report that a test
 * compares, or that a pipeline diffs, has to be the same on every machine.
 */
final class Json {

    private static final JsonFactory FACTORY = new JsonFactory()
            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

    private Json() {
        throw new AssertionError("no instances");
    }

    /**
     * Writes a JSON document built by the given body, followed by one line feed.
     *
     * @param console the streams of the process
     * @param body    what to write into the generator
     */
    static void write(Console console, Body body) {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                        .withObjectEmptySeparator("")
                        .withArrayEmptySeparator(""))
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        try (JsonGenerator generator = FACTORY.createGenerator(collected, JsonEncoding.UTF8)) {
            generator.setPrettyPrinter(printer);
            body.write(generator);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        console.print(collected.toString(StandardCharsets.UTF_8) + "\n");
    }

    /** What a caller writes into the generator. */
    @FunctionalInterface
    interface Body {

        /**
         * Writes the document.
         *
         * @param generator the generator to write to
         * @throws IOException if the generator fails
         */
        void write(JsonGenerator generator) throws IOException;
    }
}
