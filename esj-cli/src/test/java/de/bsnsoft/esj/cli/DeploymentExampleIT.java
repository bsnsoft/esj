package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The caller's side of {@code docs/deployment.md}, run.
 *
 * <p>That page tells a service to read an invoice from a stranger in a process of its own,
 * with a heap ceiling on its command line and a timeout held by the caller, and it prints
 * the Java that does it. A printed snippet that nobody runs is a claim; this test is the
 * evidence. {@link #validate(Path, byte[], Path, Path)} is that snippet, character for
 * character inside its body, and {@link #thePageShowsTheCodeThatIsRunHere()} reads the page
 * back to make sure the two have not drifted apart.
 *
 * <p>What is asserted are the outcomes of the contract — a conformant document leaves with
 * 0, a document past a bound of the run leaves with the code that means no verdict — and
 * never a timing. The numbers belong to {@code docs/deployment-measurements.md}, which
 * names the machine they were taken on.
 *
 * <p>The example spawns the self-contained jar, because a heap ceiling and an exit code
 * are properties of a process and cannot be observed inside the one the test runs in. That
 * jar is written by the {@code package} phase, which is why this is an integration test and
 * runs after it: as a unit test it skipped itself on every clean build, which is every build
 * that mattered.
 */
class DeploymentExampleIT {

    /** The self-contained jar, next to the classes of this module. */
    private static final Path JAR = Path.of("target", "esj.jar").toAbsolutePath();

    /** A conformant document of the repository's own examples. */
    private static final String INVOICE = "examples/standard-invoice.esj.json";

    /** The page whose snippet this test is. */
    private static final String PAGE = "docs/deployment.md";

    @TempDir
    private Path directory;

    @Test
    void aConformantDocumentLeavesWithSuccess() throws Exception {
        requireJar();
        Path report = directory.resolve("report.json");
        Path diagnostics = directory.resolve("diagnostics.txt");
        int code = validate(JAR, Fixtures.bytes(INVOICE), report, diagnostics);
        assertEquals(ExitCode.SUCCESS, code,
                "the complete check for an ESJ input is the structural layers and the"
                        + " business rules of EN 16931, and the example runs both; "
                        + read(diagnostics));
        assertTrue(read(report).contains("\"l3\""),
                "the run wrote the JSON report the example asks for: " + read(report));
    }

    @Test
    void aDocumentPastABoundOfTheRunLeavesWithTheLimitCode() throws Exception {
        requireJar();
        Path report = directory.resolve("bounded-report.json");
        Path diagnostics = directory.resolve("bounded-diagnostics.txt");
        int code = validateWithin(JAR, Fixtures.bytes(INVOICE), report, diagnostics,
                List.of("--max-values", "5"));
        assertEquals(ExitCode.LIMIT, code,
                "a bound of this run is not a verdict about the document; "
                        + read(diagnostics));
        assertTrue(read(diagnostics).contains(Bound.VALUES.option()),
                "the refusal names the switch that raises the bound: " + read(diagnostics));
    }

    /**
     * Reads the page and asserts that the snippet it prints is the code run above.
     *
     * <p>The two live in different files and no compiler holds them together, so the
     * load-bearing lines are checked here: a page that still showed a killed process being
     * left for dead, or a run without a heap ceiling, would be a page that recommends
     * something this test does not do.
     */
    @Test
    void thePageShowsTheCodeThatIsRunHere() {
        String page = Fixtures.text(PAGE);
        for (String line : List.of(
                "\"-Xms32m\", \"-Xmx512m\", \"-XX:+ExitOnOutOfMemoryError\",",
                "\"-jar\", jar.toString(), \"validate\", \"-\", \"--output\", \"json\")",
                ".redirectOutput(report.toFile())",
                ".redirectError(diagnostics.toFile())",
                "if (!process.waitFor(30, TimeUnit.SECONDS)) {",
                "process.destroyForcibly().waitFor();")) {
            assertTrue(page.contains(line),
                    PAGE + " shows the code this test runs, and is missing: " + line);
        }
    }

    /**
     * The example of {@code docs/deployment.md}: one process, one document, one verdict.
     *
     * @param jar         the self-contained jar to run
     * @param invoice     the bytes of the document, written to the standard input
     * @param report      the file the standard output is redirected to
     * @param diagnostics the file the standard error stream is redirected to
     * @return the exit code of the process, or {@code -1} where it had to be killed
     * @throws IOException          where the process could not be started
     * @throws InterruptedException where the waiting thread was interrupted
     */
    static int validate(Path jar, byte[] invoice, Path report, Path diagnostics)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms32m", "-Xmx512m", "-XX:+ExitOnOutOfMemoryError",
                "-jar", jar.toString(), "validate", "-", "--output", "json")
                .redirectOutput(report.toFile())
                .redirectError(diagnostics.toFile())
                .start();
        try (OutputStream in = process.getOutputStream()) {
            in.write(invoice);
        } catch (IOException e) {
            // The process left before it had read everything: a bound, or a crash. Its exit
            // code says which, and it is about to be read.
        }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
            }
            return -1;   // no verdict: the caller retries with more, or gives up
        }
        return process.exitValue();
    }

    /**
     * The same run with further switches, which is how the second case reaches a bound.
     *
     * <p>It is kept apart from {@link #validate} so that the method above stays the page's
     * snippet and nothing else.
     *
     * @param jar         the self-contained jar to run
     * @param invoice     the bytes of the document
     * @param report      the file the standard output is redirected to
     * @param diagnostics the file the standard error stream is redirected to
     * @param switches    the further arguments of the run
     * @return the exit code of the process, or {@code -1} where it had to be killed
     */
    private static int validateWithin(Path jar, byte[] invoice, Path report,
            Path diagnostics, List<String> switches)
            throws IOException, InterruptedException {
        List<String> line = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms32m", "-Xmx512m", "-XX:+ExitOnOutOfMemoryError",
                "-jar", jar.toString(), "validate", "-", "--output", "json"));
        line.addAll(switches);
        Process process = new ProcessBuilder(line)
                .redirectOutput(report.toFile())
                .redirectError(diagnostics.toFile())
                .start();
        try (OutputStream in = process.getOutputStream()) {
            in.write(invoice);
        } catch (IOException e) {
            // As above: the refusal came before the last byte was taken.
        }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
            return -1;
        }
        return process.exitValue();
    }

    private void requireJar() {
        assertTrue(Files.isRegularFile(JAR), "the self-contained jar is written by the"
                + " package phase, which is before the integration-test phase this runs in");
    }

    private String read(Path file) throws IOException {
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }
}
