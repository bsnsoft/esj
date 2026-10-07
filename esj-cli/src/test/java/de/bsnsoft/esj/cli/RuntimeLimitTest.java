package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The deadline of {@code --max-runtime}.
 *
 * <p>Nothing here waits for a document that is slow to read: a test whose outcome depends
 * on how fast the machine it runs on happens to be is a test that fails on somebody else's
 * laptop for no reason. The command is made to take longer than the deadline instead — the
 * standard input it is reading simply does not answer until the deadline has fired — so
 * the only timing the test depends on is that one second passes before ten do.
 *
 * <p>What ends the process is passed in, which is why these tests can watch a deadline
 * fire at all; in the tool a user runs it is {@link Runtime#halt(int)}, and there would be
 * no test runner left to assert anything.
 */
class RuntimeLimitTest {

    /** How long a test waits for the deadline before it gives up on it. */
    private static final long PATIENCE_SECONDS = 30;

    /** Stands for a hybrid PDF of the corpus, copied into the directory of the test. */
    private static final String PDF = "@pdf";

    /** Stands for a second ESJ document, copied into the directory of the test. */
    private static final String OTHER = "@other";

    /** Stands for a file in the directory of the test that a command writes to. */
    private static final String OUT = "@out";

    @TempDir
    private Path directory;

    @Test
    void endsARunThatTakesLongerThanItWasGiven() throws InterruptedException {
        CountDownLatch halted = new CountDownLatch(1);
        AtomicInteger code = new AtomicInteger();
        Stalled stdin = new Stalled(halted);
        Cli.Run run = Cli.run(stdin, status -> {
            code.set(status);
            halted.countDown();
        }, "convert", "--max-runtime", "1", Input.STDIN_ARGUMENT);

        assertTrue(halted.await(PATIENCE_SECONDS, TimeUnit.SECONDS), "the deadline fired");
        assertEquals(ExitCode.LIMIT, code.get(), "a deadline is no verdict on the document");
        assertTrue(run.err().contains("runtime limit of 1 s reached"), run.err());
        assertTrue(run.err().contains("no verdict"), run.err());
    }

    @Test
    void leavesARunThatFinishedInTimeAlone() throws InterruptedException {
        AtomicInteger halts = new AtomicInteger();
        Cli.Run run = Cli.run(new ByteArrayInputStream(Fixtures.bytes(
                "examples/minimal.esj.json")), status -> halts.incrementAndGet(),
                "convert", "--max-runtime", "600", Input.STDIN_ARGUMENT);

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        Thread.sleep(50);
        assertEquals(0, halts.get(), "the watchdog is cancelled with the run it watched");
    }

    @Test
    void refusesADeadlineItCannotRead() {
        Cli.Run run = Cli.run("convert", "--max-runtime", "half", "-");
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("--max-runtime"), run.err());
        assertTrue(run.err().contains("takes a duration"), run.err());
    }

    @Test
    void refusesADeadlineOfNoTimeAtAll() {
        Cli.Run run = Cli.run("convert", "--max-runtime", "0", "-");
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("positive duration"), run.err());
    }

    @Test
    void readsADeadlineInEveryUnitItOffers() {
        for (String bound : new String[] {"600000ms", "600s", "10m", "600"}) {
            Cli.Run run = Cli.run(Fixtures.bytes("examples/minimal.esj.json"),
                    "convert", "--max-runtime", bound, Input.STDIN_ARGUMENT);
            assertEquals(ExitCode.SUCCESS, run.exitCode(), bound + ": " + run.err());
        }
    }

    /**
     * Every command that reads a document holds the deadline where the caller named none,
     * not only the three that spend it step by step. The default is five minutes; it is
     * shortened to one second here, which is the only difference to the tool a user runs.
     * {@code esj validate} stops itself at the deadline and names the step; every other
     * command is ended by the watchdog. Either way the run leaves with exit code 7 and no
     * verdict.
     */
    @ParameterizedTest
    @MethodSource("documentCommands")
    void everyCommandThatReadsADocumentHoldsTheDefaultDeadline(List<String> command)
            throws InterruptedException {
        CountDownLatch halted = new CountDownLatch(1);
        AtomicInteger code = new AtomicInteger();
        Stalled stdin = new Stalled(halted);
        try {
            String[] arguments = command.stream().map(argument -> switch (argument) {
                case PDF -> Fixtures.file(directory, "conformance/pdf/factur-x.pdf");
                case OTHER -> Fixtures.file(directory, "examples/minimal.esj.json");
                case OUT -> directory.resolve("out").toString();
                default -> argument;
            }).toArray(String[]::new);
            Cli.Run run = Cli.runWithDefaultMaxRuntime(Duration.ofSeconds(1), stdin, status -> {
                code.set(status);
                halted.countDown();
            }, arguments);

            boolean watchdog = halted.getCount() == 0;
            assertTrue(watchdog || run.exitCode() == ExitCode.LIMIT,
                    command + ": exit " + run.exitCode() + ", " + run.err());
            if (watchdog) {
                assertEquals(ExitCode.LIMIT, code.get(), command.toString());
                assertTrue(run.err().contains("runtime limit of 1 s reached"),
                        command + ": " + run.err());
            }
            assertTrue(run.err().contains("no verdict"), command + ": " + run.err());
            assertFalse(run.text().contains("VALID"), command + ": " + run.text());
        } finally {
            halted.countDown();
        }
    }

    static List<List<String>> documentCommands() {
        return List.of(
                List.of("convert", "-"),
                List.of("upgrade", "--to", "2017", "-"),
                List.of("validate", "-"),
                List.of("render", "-", "--out", OUT),
                List.of("embed", PDF, "-", "--out", OUT),
                List.of("inspect", "-"),
                List.of("extract", "-", "--list"),
                List.of("get", "-", "/BT-1"),
                List.of("list", "-"),
                List.of("diff", "-", OTHER),
                List.of("canonicalize", "-"));
    }

    /**
     * A standard input that answers nothing until the deadline has fired, and reports the
     * end of the stream afterwards so that the run it stalled can finish.
     */
    private static final class Stalled extends InputStream {

        private final CountDownLatch released;

        Stalled(CountDownLatch released) {
            this.released = released;
        }

        @Override
        public int read() {
            return end();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return end();
        }

        private int end() {
            try {
                released.await(PATIENCE_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }
    }
}
