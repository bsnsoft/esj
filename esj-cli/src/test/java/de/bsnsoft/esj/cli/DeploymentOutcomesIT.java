package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The outcomes the deployment documentation states for its smallest rows, taken the way
 * the documentation takes them: a separate process, a heap ceiling on its command line,
 * and the exit code read from the process rather than from a method.
 *
 * <p>What is pinned here are the <em>outcomes</em>, never the timings. A wall clock and a
 * peak resident set belong to the machine that measured them, and a test that asserted
 * them would fail on a slower laptop or under a parallel build without anything being
 * wrong; the numbers live in {@code docs/deployment-measurements.md}, which names the
 * machine they were taken on. What must not drift is the shape of the answer: the corpus
 * of this repository is readable in a quarter of a gibibyte of heap, an input past the
 * bound of the default profile is refused with the code that means a limit of this run
 * rather than a defect of the document, an input the heap cannot hold ends with the
 * code the virtual machine chooses rather than with one of the tool's, and the same run
 * started without that switch says that a resource of this run ran out rather than that
 * the tool has a defect.
 *
 * <p>That last one is here because it is the outcome a caller is most likely to classify
 * wrongly: the documented command line carries {@code -XX:+ExitOnOutOfMemoryError}, the
 * status it aborts with is fixed by the virtual machine and not by this project, and a
 * code of {@link ExitCode} that collided with it would turn a crash into a verdict about
 * somebody's invoice.
 *
 * <p>The tests run the self-contained jar, because a heap ceiling and an exit code are
 * properties of a process and cannot be observed inside the one the test runs in. That jar
 * is written by the {@code package} phase, which is why these are integration tests and run
 * after it: as unit tests they skipped themselves on every clean build, which is every build
 * that mattered.
 */
class DeploymentOutcomesIT {

    /** The self-contained jar, next to the classes of this module. */
    private static final Path JAR = Path.of("target", "esj.jar").toAbsolutePath();

    /** The largest instance of the conformance corpus. */
    private static final String LARGEST_CORPUS =
            "conformance/kosit/business-cases/standard/03.07a-INVOICE_uncefact.xml";

    /** A small corpus instance, grown past the bound of the default profile. */
    private static final String SMALL_CORPUS =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml";

    /** The XML the default profile reads, from {@code XrImporter.DEFAULT_MAX_INPUT_BYTES}. */
    private static final long DEFAULT_INPUT_BYTES = 4L * 1024 * 1024;

    /**
     * The status the virtual machine aborts with under {@code -XX:+ExitOnOutOfMemoryError}.
     *
     * <p>It is HotSpot's and not this tool's, which is the whole point of asserting it: the
     * documented command line carries that switch, so this is the code a caller meets when a
     * document does not fit the heap it was given, and no code of {@link ExitCode} may
     * collide with it.
     */
    private static final int OUT_OF_HEAP = 3;

    /** An input large enough that a small heap cannot hold it while it is being read. */
    private static final long PAST_A_SMALL_HEAP = 32L * 1024 * 1024;

    /**
     * How long a run of the tool is given here before it is taken for hung.
     *
     * <p>Both runs finish in well under a second on any machine that can build this
     * project, so this is a hang detector and not a budget. It is kept short on purpose:
     * a test that waits minutes before saying that something is wrong has told nobody
     * anything a half-minute wait would not have told them sooner.
     */
    private static final long PATIENCE_SECONDS = 60;

    @TempDir
    private Path directory;

    @Test
    void theLargestCorpusInvoiceConvertsInAQuarterGibibyteOfHeap() throws Exception {
        String invoice = Fixtures.file(directory, LARGEST_CORPUS);
        Result result = run("-Xmx256m", "convert", "--to", "esj", invoice);
        assertEquals(0, result.exitCode(),
                "the corpus is readable at the smallest heap the documentation measures; "
                        + result.err());
    }

    @Test
    void anXmlInputPastTheDefaultBoundLeavesWithTheLimitCode() throws Exception {
        Path grown = grownPastTheDefaultBound();
        Result result = run("-Xmx256m", "convert", "--to", "esj", grown.toString());
        assertEquals(ExitCode.LIMIT, result.exitCode(),
                "a bound of this run, not a verdict about the document; " + result.err());
        assertTrue(result.err().contains(Bound.INPUT_BYTES.option()),
                "the refusal names the switch that raises the bound: " + result.err());
    }

    @Test
    void aHeapTheInputCannotFitInLeavesWithTheCodeTheVirtualMachineChooses() throws Exception {
        Path grown = grownTo("past-a-small-heap.xml", PAST_A_SMALL_HEAP);
        Result result = run("-Xmx16m", "convert", "--limits", "large", "--to", "esj",
                grown.toString());
        assertEquals(OUT_OF_HEAP, result.exitCode(),
                "a heap exhaustion is the virtual machine's answer, not one of the tool's "
                        + "codes; " + result.err());
        assertEquals("", result.err(),
                "the virtual machine writes nothing to the error stream: " + result.err());
        assertTrue(result.out().contains("OutOfMemoryError"),
                "its notice goes to the standard output, which is why the bytes of a run "
                        + "that did not leave with 0 or 1 are not a result: " + result.out());
    }

    @Test
    void aHeapExhaustionWithoutTheSwitchIsAResourceFailureAndNotADefectOfTheTool()
            throws Exception {
        Path grown = grownTo("past-a-small-heap-unflagged.xml", PAST_A_SMALL_HEAP);
        Result result = run(List.of("-Xmx16m"), "convert", "--limits", "large", "--to", "esj",
                grown.toString());
        assertEquals(ExitCode.LIMIT, result.exitCode(),
                "a ceiling chosen by the caller is a resource of this run, not a defect of "
                        + "the tool; " + result.err());
        assertTrue(result.err().contains("16 MiB"),
                "the line names the ceiling that was reached: " + result.err());
        assertTrue(result.err().contains("-XX:+ExitOnOutOfMemoryError"),
                "and the switch that would have made the answer reliable: " + result.err());
    }

    /**
     * Writes a corpus invoice followed by enough whitespace to pass the bound.
     *
     * <p>The bound is a count of bytes on the way in, and the tool reaches it before it
     * has parsed anything, so a document with a long tail of insignificant space is the
     * cheapest input that reaches it: a few megabytes written once, instead of a generated
     * instance whose invoice lines nobody here looks at.
     */
    private Path grownPastTheDefaultBound() throws IOException {
        return grownTo("past-the-default-bound.xml", DEFAULT_INPUT_BYTES);
    }

    /** Writes that invoice grown past the given number of bytes. */
    private Path grownTo(String name, long bytes) throws IOException {
        Path file = directory.resolve(name);
        byte[] invoice = Fixtures.bytes(SMALL_CORPUS);
        byte[] space = new byte[64 * 1024];
        Arrays.fill(space, (byte) ' ');
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(invoice);
            for (long written = invoice.length; written <= bytes; written += space.length) {
                out.write(space);
            }
        }
        return file;
    }

    /**
     * Runs the jar in its own process, with the switch the documentation prescribes, and
     * returns what it left behind.
     */
    private Result run(String heap, String... arguments) throws Exception {
        return run(List.of(heap, "-XX:+ExitOnOutOfMemoryError"), arguments);
    }

    /** Runs the jar in its own process with the given options and returns what it left. */
    private Result run(List<String> options, String... arguments) throws Exception {
        assertTrue(Files.isRegularFile(JAR), "the self-contained jar is written by the"
                + " package phase, which is before the integration-test phase this runs in");
        List<String> line = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString()));
        line.addAll(options);
        line.addAll(List.of("-jar", JAR.toString()));
        line.addAll(List.of(arguments));
        Path out = directory.resolve("stdout.txt");
        Path err = directory.resolve("stderr.txt");
        Process process = new ProcessBuilder(line)
                .redirectOutput(out.toFile())
                .redirectError(err.toFile())
                .start();
        if (!process.waitFor(PATIENCE_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("the tool did not finish within " + PATIENCE_SECONDS
                    + " seconds: " + String.join(" ", line));
        }
        return new Result(process.exitValue(), read(out), read(err));
    }

    /** Returns what a redirected stream collected, or nothing where it collected nothing. */
    private static String read(Path file) throws IOException {
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    /**
     * What one run of the jar produced.
     *
     * @param exitCode the code the process left with
     * @param out      what it wrote to its standard output
     * @param err      what it wrote to its error stream
     */
    private record Result(int exitCode, String out, String err) {
    }
}
