package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.Limits;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The dense instances of {@code conformance/scale/} at the smallest size worth generating.
 *
 * <p>{@code --dense} replicates the smallest invoice line EN 16931 admits rather than the
 * rich line a corpus invoice carries, so that the document class this module was built for
 * — tens of megabytes with several hundred thousand lines — can be produced at all: a
 * corpus line costs between 1.5 and 2.6 kB and a dense one a few hundred bytes. The
 * measurements taken at that size are in {@code docs/deployment-measurements.md}; what is
 * checked here is that the generator writes a document both syntaxes agree on, at a size a
 * build can afford.
 *
 * <p>The test is skipped where no {@code python3} is on the path.
 */
class DenseScaleTest {

    /** The generator, as the corpus directory of the repository carries it. */
    private static final String GENERATOR = Corpus.ROOT + "scale/generate.py";

    /** How many lines the generated instances carry. */
    private static final int LINES = 200;

    /** The values a dense document of {@link #LINES} lines carries. */
    private static final int VALUES = 1641;

    /** The sum of the line net amounts of those lines: 200 x 1.00. */
    private static final BigDecimal BT_106 = new BigDecimal("200.00");

    /** How long the generator is given before the test gives up on it. */
    private static final int PATIENCE_SECONDS = 120;

    @TempDir
    private Path directory;

    @Test
    void bothSyntaxesGrowIntoTheSameDenseDocument() {
        assumeTrue(python(), "python3 is on the path");
        SemanticDocument ubl = read("business-cases/standard/01.01a-INVOICE_ubl.xml");
        SemanticDocument cii = read("business-cases/standard/01.01a-INVOICE_uncefact.xml");

        assertEquals(VALUES, ubl.values().size(), "the values of " + LINES + " dense lines");
        assertEquals(ubl.values(), cii.values(),
                "the same invoice in both syntaxes is the same semantic document");
        assertEquals(SemanticValue.ofDecimal(BT_106), value(ubl, "/BG-22/BT-106"),
                "the sum of the line net amounts follows the lines");
        assertEquals(LINES, lines(ubl), "one line group per generated line");
        assertTrue(Canonicalizer.canonicalBytes(ubl).length > 0);
    }

    /** Generates a dense instance from one corpus invoice and reads it from a stream. */
    private SemanticDocument read(String instance) {
        Path source = write(instance.substring(instance.lastIndexOf('/') + 1),
                Corpus.instance(instance));
        Path generator = write("generate.py", Corpus.bytes(GENERATOR));
        Path generated = directory.resolve(source.getFileName() + ".dense.xml");
        run(List.of("python3", generator.toString(), "--source", source.toString(),
                "--dense", "--lines", String.valueOf(LINES), "--out", generated.toString()));
        StreamingReader reader = new StreamingReader(ReaderOptions.builder()
                .limits(Limits.builder().maxValues(100_000).build()).build());
        try (InputStream in = Files.newInputStream(generated)) {
            return reader.read(in).document();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns how many invoice line groups the document carries. */
    private static long lines(SemanticDocument document) {
        return document.values().keySet().stream()
                .map(SemanticPath::toString)
                .filter(path -> path.startsWith("/BG-25/"))
                .map(path -> path.split("/")[2])
                .distinct()
                .count();
    }

    private static SemanticValue value(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path))
                .orElseThrow(() -> new AssertionError("the document has no value at " + path));
    }

    private Path write(String name, byte[] content) {
        Path target = directory.resolve(name);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target;
    }

    /** Runs a command and fails the test with what it said where it did not succeed. */
    private static void run(List<String> command) {
        try {
            Process process = new ProcessBuilder(new ArrayList<>(command))
                    .redirectErrorStream(true).start();
            String said = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(PATIENCE_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError(command.get(1) + " did not finish: " + said);
            }
            if (process.exitValue() != 0) {
                throw new AssertionError(command.get(1) + " failed: " + said);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /** Tells whether a python3 that runs is on the path. */
    private static boolean python() {
        try {
            Process process = new ProcessBuilder("python3", "--version")
                    .redirectErrorStream(true).start();
            return process.waitFor(PATIENCE_SECONDS, TimeUnit.SECONDS)
                    && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
