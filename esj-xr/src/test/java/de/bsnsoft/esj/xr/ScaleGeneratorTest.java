package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The generator of {@code conformance/scale/} at the smallest size worth generating.
 *
 * <p>The instances it is written for are too large to run in a build — that is the whole
 * point of them — so what is checked here is the arithmetic rather than the scale: fifty
 * lines, imported, counted and added up. A generator whose totals drift away from its
 * lines would otherwise be found by a measurement somebody repeats twice a year, and the
 * numbers of that measurement would be measurements of a broken document.
 *
 * <p>Both syntaxes are grown from the same business case and must arrive at the same
 * semantic document — the litmus test of the project, at a size no corpus instance
 * reaches.
 *
 * <p>{@code --text-bytes} is checked too, at a size a build can afford. It is the switch
 * that makes a document large in what one value says rather than in how many values it
 * has, which is the shape the rendering measurements need, and a padding that landed in
 * the wrong element or broke the arithmetic would be found by a measurement rather than
 * by a build.
 *
 * <p>The test is skipped where no {@code python3} is on the path; the generator has no
 * dependencies beyond it, and a build without a Python is not a broken build.
 */
class ScaleGeneratorTest {

    /** The generator, as the corpus directory of the repository carries it. */
    private static final String GENERATOR = Conformance.ROOT + "scale/generate.py";

    /** How many lines the generated instance carries. */
    private static final int LINES = 50;

    /** The values a document of {@link #LINES} lines of this business case carries. */
    private static final int VALUES = 791;

    /** The sum of the line net amounts of those lines: 50 × 288.79. */
    private static final BigDecimal BT_106 = new BigDecimal("14439.50");

    /** How many bytes of filler the padded instance carries in every item name. */
    private static final int PADDING = 2048;

    /** How long the generator is given before the test gives up on it. */
    private static final int PATIENCE_SECONDS = 120;

    @TempDir
    private Path directory;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"business-cases/standard/01.01a-INVOICE_ubl.xml",
                            "business-cases/standard/01.01a-INVOICE_uncefact.xml"})
    void growsACorpusInvoiceIntoAConsistentInstanceOfManyLines(String instance) {
        assumeTrue(python(), "python3 is on the path");
        Path source = write("source.xml", Conformance.instance(instance));
        Path generator = write("generate.py", Conformance.bytes(GENERATOR));
        Path generated = directory.resolve("out.xml");

        run(List.of("python3", generator.toString(), "--source", source.toString(),
                "--lines", String.valueOf(LINES), "--out", generated.toString()));

        assertTrue(Files.exists(generated), "the generator wrote the instance");
        SemanticDocument document = new XrImporter().importXml(read(generated));
        assertEquals(VALUES, document.values().size(), "the values of " + LINES + " lines");
        assertEquals(SemanticValue.ofDecimal(BT_106), value(document, "/BG-22/BT-106"),
                "the sum of the line net amounts follows the lines");
        assertEquals(SemanticValue.ofDecimal(BT_106), value(document, "/BG-22/BT-109"),
                "and the invoice total without VAT follows that sum");
        assertEquals(LINES, lines(document), "one line group per generated line");
    }

    /**
     * The item name of every line carries the filler {@code --text-bytes} asks for, the
     * document is otherwise the same document, and the totals still follow the lines.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"business-cases/standard/01.01a-INVOICE_ubl.xml",
                            "business-cases/standard/01.01a-INVOICE_uncefact.xml"})
    void padsTheItemNameOfEveryLineWithTextBytes(String instance) {
        assumeTrue(python(), "python3 is on the path");
        Path source = write("source.xml", Conformance.instance(instance));
        Path generator = write("generate.py", Conformance.bytes(GENERATOR));
        Path generated = directory.resolve("wide.xml");

        run(List.of("python3", generator.toString(), "--source", source.toString(),
                "--lines", String.valueOf(LINES), "--text-bytes", String.valueOf(PADDING),
                "--out", generated.toString()));

        SemanticDocument document = new XrImporter().importXml(read(generated));
        assertEquals(VALUES, document.values().size(),
                "padding a value adds no value and drops none");
        assertEquals(SemanticValue.ofDecimal(BT_106), value(document, "/BG-22/BT-106"),
                "and the totals still follow the lines");
        for (int line = 0; line < LINES; line++) {
            String name = value(document, "/BG-25/" + line + "/BG-31/BT-153").content();
            assertTrue(name.length() >= PADDING,
                    "the item name of line " + line + " carries the filler, and it is "
                            + name.length() + " characters long");
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

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Runs a command and fails the test with what it said where it did not succeed. */
    private static void run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
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
