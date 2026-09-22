package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

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
