package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The children: a child the server ends because it is stopping is a shutdown and not a
 * crash, a child that is not given its share of the disk is not started, and the standard
 * output of a child is held to that share as it arrives.
 */
@DisabledOnOs(OS.WINDOWS)
class JobsTest {

    @TempDir
    private Path temp;

    private ServeConfig config(String script) {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withJobs(2, 0, Duration.ofSeconds(1), "64m", Duration.ofMinutes(1));
        return config.withProcess(List.of("sh", "-c", script), Map.of("PATH",
                System.getenv().getOrDefault("PATH", "/usr/bin:/bin")), "default", config.log());
    }

    /**
     * A child ended by {@link Jobs#close()} leaves with the exit code of the signal, 143, and
     * usually within the wait of the loop that looks at it: before, the call took the code for
     * a crash (502); it is the server shutting down (503).
     */
    @Test
    void aChildEndedBecauseTheServerStopsIsAShutdownNotACrash() throws Exception {
        for (int round = 0; round < 5; round++) {
            ServeConfig config = config("exec sleep 60");
            Store store = new Store(config, Clock.systemUTC());
            Jobs jobs = new Jobs(config, store.disk());
            try {
                Jobs.Slot slot = jobs.acquire(Jobs.Cancel.NEVER);
                CompletableFuture<Jobs.Result> result = CompletableFuture.supplyAsync(
                        () -> jobs.run(slot, List.of("validate"), null, Jobs.Cancel.NEVER));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (jobs.running() == 0 && System.nanoTime() < deadline) {
                    Thread.sleep(5);
                }
                Thread.sleep(150);
                jobs.close();
                Jobs.Result ended = result.get(30, TimeUnit.SECONDS);
                assertEquals("shutdown", ended.killed(), "round " + round + ": exit code "
                        + ended.exitCode());
                Outcome outcome = Calls.killed(ended);
                assertEquals(Outcome.Status.BUSY, outcome.status());
                assertEquals(503, outcome.status().http());
                assertEquals("the server is shutting down", outcome.text());
                assertTrue(outcome.retryAfter().isPresent());
                slot.close();
                assertThrows(Jobs.Busy.class, () -> jobs.acquire(Jobs.Cancel.NEVER),
                        "and no child is started after");
                assertEquals(0, store.disk().used(), "the share is given back");
            } finally {
                jobs.close();
                store.close();
            }
        }
    }

    @Test
    void aChildWhoseShareDoesNotFitIsNotStarted() throws Exception {
        ServeConfig config = config("exit 0").withDisk(Jobs.MAX_WRITTEN + 1024);
        Store store = new Store(config, Clock.systemUTC());
        Jobs jobs = new Jobs(config, store.disk());
        Jobs.Slot first = jobs.acquire(Jobs.Cancel.NEVER);
        try {
            // Twice: a refused call gives its place back, or the second would find no place
            // (busy) before it found no room.
            for (int attempt = 0; attempt < 2; attempt++) {
                Disk.Full full = assertThrows(Disk.Full.class,
                        () -> jobs.acquire(Jobs.Cancel.NEVER));
                assertTrue(full.getMessage().contains("--max-disk"), full.getMessage());
            }
            assertEquals(Jobs.MAX_WRITTEN, store.disk().used());
            try (Stream<Path> left = Files.list(store.jobs())) {
                assertEquals(1, left.count(), "no directory is left behind");
            }
        } finally {
            first.close();
            jobs.close();
            store.close();
        }
    }

    @Test
    void theOutputOfAChildIsHeldToItsShareAsItArrives() throws Exception {
        // 80 MiB of zeros, more than the share of a child; the copy stops at the share.
        ServeConfig config = config("head -c 83886080 /dev/zero; exec sleep 30");
        Store store = new Store(config, Clock.systemUTC());
        Jobs jobs = new Jobs(config, store.disk());
        try (Jobs.Slot slot = jobs.acquire(Jobs.Cancel.NEVER)) {
            Jobs.Result result = jobs.run(slot, List.of(), null, Jobs.Cancel.NEVER);
            assertEquals("output", result.killed());
            assertTrue(Files.size(result.stdout()) <= Jobs.MAX_WRITTEN,
                    Files.size(result.stdout()) + " bytes kept");
            assertTrue(result.millis() < 20_000, "killed at once, not at its deadline: "
                    + result.millis() + " ms");
        } finally {
            jobs.close();
            store.close();
        }
        assertEquals(0, store.disk().used());
    }
}
