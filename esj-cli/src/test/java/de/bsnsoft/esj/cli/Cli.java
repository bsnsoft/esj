package de.bsnsoft.esj.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.IntConsumer;

/**
 * Runs the tool the way a user runs it, in the process the test runs in.
 *
 * <p>No subprocess is started. {@link Main#run(String[], InputStream, java.io.OutputStream,
 * java.io.OutputStream)} is the whole tool with its three streams passed in, so a test can
 * hand it bytes on the standard input and read back the bytes it wrote, the diagnostics it
 * wrote and the code it would have left with. That is the reason the tool is built that
 * way: a command line whose behaviour can only be checked by spawning a process tends not
 * to be checked.
 */
final class Cli {

    private Cli() {
        throw new AssertionError("no instances");
    }

    /** Runs the tool with an empty standard input. */
    static Run run(String... args) {
        return run(new byte[0], args);
    }

    /** Runs the tool with the given bytes on the standard input. */
    static Run run(byte[] stdin, String... args) {
        return run(new ByteArrayInputStream(stdin), args);
    }

    /**
     * Runs the tool with a stream of the test's own on the standard input, which is how a
     * test hands it something other than bytes that are all there: one that stops
     * producing them without ending, as a pipe whose writer has gone quiet does.
     */
    static Run run(InputStream stdin, String... args) {
        return run(stdin, code -> { }, args);
    }

    /**
     * Runs the tool over a stream of the test's own, and with the test's own way of
     * ending the process.
     *
     * <p>{@code halt} is what {@code --max-runtime} calls when the deadline passes. A
     * test records the code instead of ending the process, which is the only way to watch
     * a deadline fire without taking the test runner with it.
     */
    static Run run(InputStream stdin, IntConsumer halt, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(args, stdin, out, err, halt);
        return new Run(code, out.toByteArray(), err.toString(StandardCharsets.UTF_8));
    }

    /**
     * What one run of the tool produced.
     *
     * @param exitCode the code the process would have left with
     * @param out      the bytes written to the standard output, unchanged
     * @param err      the text written to the standard error stream
     */
    record Run(int exitCode, byte[] out, String err) {

        /** Returns the standard output as text. */
        String text() {
            return new String(out, StandardCharsets.UTF_8);
        }

        /** Returns the lines of the standard output, without a trailing empty line. */
        String[] lines() {
            String text = text();
            return text.isEmpty() ? new String[0] : text.split("\n", -1);
        }
    }
}
