package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * What the tool does when writing goes wrong, and what it does when nothing went wrong
 * but the reader left.
 *
 * <p>These are interface promises a script is written against, so they are asserted here
 * rather than left to be noticed: an output that was truncated must not leave with a
 * successful code, a consumer that closed the pipe must not be reported as a failure, a
 * lost diagnostic must not be reported as a lost output, and nothing a command can raise
 * may reach the default handler of the virtual machine.
 *
 * <p>{@link Main#run(String[], java.io.InputStream, OutputStream, OutputStream)} takes its
 * three streams, so each case is an ordinary unit test over a stream that misbehaves on
 * purpose. The real process meets the same shapes through the file descriptors that
 * {@link Main#main(String[])} passes.
 *
 * <p>Whether a refused write is a reader that left or a write that failed is not read out
 * of the exception — the platform writes that message in its own language — but out of
 * what the stream is attached to, which {@link OutputSink} carries. A test says which of
 * the two it is playing by wrapping its stream in one; a plain stream is the other case,
 * where a refused write is a refused write.
 */
class StreamsTest {

    private static final String MINIMAL = "examples/minimal.esj.json";

    /** An example every engine accepts, for the runs whose subject is the streams. */
    private static final String STANDARD = "examples/standard-invoice.esj.json";

    /** A stream that refuses to be written to, in the words a platform would use. */
    private static final class Failing extends OutputStream {

        private final String message;
        private final Error error;

        private Failing(String message, Error error) {
            this.message = message;
            this.error = error;
        }

        static Failing saying(String message) {
            return new Failing(message, null);
        }

        static Failing throwing(Error error) {
            return new Failing(null, error);
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (error != null) {
                throw error;
            }
            throw new IOException(message);
        }
    }

    @Test
    void leavesWithTheOutputCodeWhenAWriteReallyFailed() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"list", "-"}, stdin(Fixtures.bytes(MINIMAL)),
                Failing.saying("No space left on device"), err);
        assertEquals(ExitCode.OUTPUT, code);
        assertTrue(text(err).contains("writing the output failed"), text(err));
    }

    @Test
    void keepsTheVerdictOfTheCommandWhenAConsumerClosedThePipe() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"list", "-"}, stdin(Fixtures.bytes(MINIMAL)),
                pipe(Failing.saying("Broken pipe")), err);
        assertEquals(ExitCode.SUCCESS, code, text(err));
        assertFalse(text(err).contains("error:"),
                "a reader that stopped reading is not an error: " + text(err));
    }

    @Test
    void readsAClosedPipeFromTheStreamAndNotFromTheWordsOfTheFailure() {
        for (String spelling : new String[] {
                "Broken pipe", "Une erreur de tube", "Borulama hatas\u0131", null}) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = Main.run(new String[] {"list", "-"}, stdin(Fixtures.bytes(MINIMAL)),
                    pipe(Failing.saying(spelling)), err);
            assertEquals(ExitCode.SUCCESS, code, text(err));
            assertEquals("", text(err), "nothing is said about a reader that left");
        }
    }

    @Test
    void keepsTheVerdictOfAFailedValidationWhenAConsumerClosedThePipe() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"validate", "-"},
                stdin(Fixtures.bytes("examples/invalid/unknown-term.esj.json")),
                pipe(Failing.saying("Broken pipe")), err);
        assertEquals(ExitCode.VALIDATION, code, text(err));
    }

    @Test
    void reportsAFailedWriteOnce() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"list", "-"}, stdin(Fixtures.bytes(MINIMAL)),
                Failing.saying("Bad file descriptor"), err);
        assertEquals(ExitCode.OUTPUT, code);
        assertEquals(1, text(err).lines().filter(line -> line.startsWith("error:")).count(),
                "one event, one sentence: " + text(err));
        assertFalse(text(err).contains("java.io.IOException"),
                "the exception is for --debug: " + text(err));
    }

    @Test
    void carriesTheExceptionOfAFailedWriteUnderDebug() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"--debug", "list", "-"}, stdin(Fixtures.bytes(MINIMAL)),
                Failing.saying("Bad file descriptor"), err);
        assertEquals(ExitCode.OUTPUT, code);
        assertTrue(text(err).contains("Bad file descriptor"), text(err));
    }

    @Test
    void writesADiagnosticBeforeTheCommandEnds() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Console console = new Console(stdin(new byte[0]), new ByteArrayOutputStream(),
                new BufferedOutputStream(err, 8192), new GlobalOptions());
        console.error("said while the command is still running");
        assertTrue(text(err).contains("said while the command is still running"),
                "a diagnostic reaches the stream as it is written, not when the command"
                        + " ends: " + text(err));
    }

    /** Wraps a stream as one whose far end is a reader that can leave. */
    private static OutputSink pipe(OutputStream stream) {
        return new OutputSink(stream, true);
    }

    @Test
    void doesNotTurnALostDiagnosticIntoALostOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"--verbose", "validate", "-"},
                stdin(Fixtures.bytes(STANDARD)), out, Failing.saying("Broken pipe"));
        assertEquals(ExitCode.SUCCESS, code);
        assertTrue(text(out).contains("Cardinality (L3):          OK"),
                "the output was written in full: " + text(out));
    }

    @Test
    void leavesWithTheInternalCodeForAThrowableNoCommandTurnedIntoAnAnswer() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"list", "-"}, stdin(Fixtures.bytes(MINIMAL)),
                Failing.throwing(new InternalError("a defect")), err);
        assertEquals(ExitCode.INTERNAL, code);
        assertTrue(text(err).contains("internal error"), text(err));
        assertFalse(text(err).contains("\tat "), "no stack trace without --debug: " + text(err));
    }

    @Test
    void leavesWithTheLimitCodeWhenAResourceOfTheRunRanOut() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"list", "-"}, stdin(Fixtures.bytes(MINIMAL)),
                Failing.throwing(new StackOverflowError("too deep")), err);
        assertEquals(ExitCode.LIMIT, code,
                "a stack that ran out is a resource of this run, not a defect of the tool: "
                        + text(err));
        assertTrue(text(err).contains("out of stack"), text(err));
        assertTrue(text(err).contains("no verdict on the document"), text(err));
    }

    @Test
    void namesTheHeapCeilingWhenTheHeapRanOut() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[] {"list", "-"}, stdin(Fixtures.bytes(MINIMAL)),
                Failing.throwing(new OutOfMemoryError("Java heap space")), err);
        assertEquals(ExitCode.LIMIT, code, text(err));
        assertTrue(text(err).contains("heap ceiling of "), text(err));
        assertTrue(text(err).contains("-XX:+ExitOnOutOfMemoryError"),
                "and the switch that makes the answer reliable: " + text(err));
    }

    private static ByteArrayInputStream stdin(byte[] content) {
        return new ByteArrayInputStream(content);
    }

    private static String text(ByteArrayOutputStream stream) {
        return stream.toString(StandardCharsets.UTF_8);
    }
}
