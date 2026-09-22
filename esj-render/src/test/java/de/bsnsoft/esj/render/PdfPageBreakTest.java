package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An invoice of three hundred lines, and what the layout does when the paper runs out.
 *
 * <p>The corpus has nothing of this size, so the document is grown from one of its
 * invoices by the generator of {@code conformance/scale/}, the same one the measurements
 * of this project use. Three hundred lines is the smallest size that makes the question
 * interesting and still costs nothing to render in a build.
 *
 * <p>What is checked is what a reader of page seven needs: that the table of lines
 * continues there, that its header is above the rows on that page as it was on the first,
 * that the page says which invoice it belongs to and which page of how many it is, and
 * that not one of the three hundred lines fell out between two pages.
 *
 * <p>The test is skipped where no {@code python3} is on the path; the generator has no
 * dependencies beyond it, and a build without a Python is not a broken build.
 */
class PdfPageBreakTest {

    /** The instance the lines are grown from: one VAT rate, no allowances, no charges. */
    private static final String SOURCE = "business-cases/standard/01.01a-INVOICE_ubl.xml";

    /** How many lines the generated invoice carries. */
    private static final int LINES = 300;

    /** How long the generator is given before the test gives up on it. */
    private static final int PATIENCE_SECONDS = 120;

    @TempDir
    private Path directory;

    @Test
    void anInvoiceOfThreeHundredLinesRunsOverPagesWithItsHeaderRepeated() {
        assumeTrue(python(), "python3 is on the path");
        SemanticDocument document = generated();
        assertEquals(LINES, lineCount(document), "the generated invoice has " + LINES + " lines");

        byte[] pdf = new PdfRenderer().render(document);

        int pages = Pdf.pages(pdf);
        assertTrue(pages > 5, "three hundred lines take more than five pages, not " + pages);

        String header = Word.COLUMN_ITEM.in(RenderLanguage.GERMAN);
        List<Integer> withHeader = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            String text = Pdf.flat(Pdf.textOfPage(pdf, page));
            if (text.contains(header)) {
                withHeader.add(page);
                assertTrue(text.contains(Word.COLUMN_QUANTITY.in(RenderLanguage.GERMAN))
                                && text.contains(Word.COLUMN_LINE.in(RenderLanguage.GERMAN)),
                        "page " + page + " carries the whole header of the table, not part of it");
            }
            assertTrue(text.contains("Seite " + page + " von " + pages),
                    "page " + page + " says which page of how many it is");
        }
        assertTrue(withHeader.size() > 4,
                "the header of the table is repeated on every page it runs onto, and it was "
                        + "on " + withHeader.size());
        assertTrue(withHeader.contains(2), "the second page of the table carries it too");
    }

    @Test
    void notOneOfThoseLinesIsLost() {
        assumeTrue(python(), "python3 is on the path");
        SemanticDocument document = generated();

        String text = Pdf.flat(new PdfRenderer().render(document));

        List<String> missing = new ArrayList<>();
        for (int line = 0; line < LINES; line++) {
            String identifier = document.value(
                    SemanticPath.of("/BG-25/" + line + "/BT-126")).orElseThrow().content();
            if (!Pdf.shows(text, identifier)) {
                missing.add(identifier);
            }
        }
        assertEquals(List.of(), missing, "every line identifier is on a page");
    }

    @Test
    void andItRendersToTheSameBytesTwice() {
        assumeTrue(python(), "python3 is on the path");
        SemanticDocument document = generated();

        assertArrayEquals(new PdfRenderer().render(document), new PdfRenderer().render(document),
                "an invoice of three hundred lines renders to the same bytes twice");
    }

    /** Grows the corpus invoice into one of {@link #LINES} lines and imports it. */
    private SemanticDocument generated() {
        Path source = write("source.xml", Corpus.bytes("/conformance/kosit/" + SOURCE));
        Path generator = write("generate.py", Corpus.bytes("/conformance/scale/generate.py"));
        Path grown = directory.resolve("out.xml");

        run(List.of("python3", generator.toString(), "--source", source.toString(),
                "--lines", String.valueOf(LINES), "--out", grown.toString()));

        return new XrImporter().importXml(read(grown));
    }

    private static long lineCount(SemanticDocument document) {
        return document.values().keySet().stream()
                .filter(path -> path.toString().startsWith("/BG-25/"))
                .map(path -> path.toString().split("/")[2])
                .distinct()
                .count();
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
