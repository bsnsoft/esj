package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code esj render} through the command line: what it writes, where it writes it and which
 * code it leaves with.
 *
 * <p>What the two renderings show is the business of {@code esj-render} and is tested there,
 * over the whole conformance corpus. What is tested here is the wiring: that the command
 * reads every input the other commands read, that the options reach the renderer, that the
 * bytes land where {@code --out} says, and that a document the tool could not read is an
 * answer about the document rather than a stack trace.
 */
class RenderCommandTest {

    /** The first bytes of a PDF file, which is what {@code %PDF-} is for. */
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    /** A UBL invoice of the conformance corpus, which the command has to read as well. */
    private static final String UBL =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml";

    @TempDir
    private Path directory;

    @Test
    void writesAPdfToTheFileItWasGiven() {
        Path target = directory.resolve("invoice.pdf");

        Cli.Run run = Cli.run("render", example("minimal"), "--out", target.toString());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("", run.text(), "the file was the destination, so the stream stays empty");
        assertTrue(Files.exists(target), "the file was written");
        assertPdf(read(target));
    }

    @Test
    void writesThePdfToTheStandardOutputWhereTheDestinationIsADash() {
        Cli.Run run = Cli.run("render", example("minimal"), "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertPdf(run.out());
    }

    /**
     * The bytes of a rendering are a function of the document, the language and the paper,
     * and the command line adds nothing of its own to them. The file compared against here
     * is the same rendering {@code esj-render} checks in, the generic layout named as it is
     * there, so a difference between the two is either a change to the renderer, which that
     * module's own golden test sees first, or something this command did to the document on
     * the way.
     */
    @Test
    void thePdfIsTheOneCheckedIn() {
        Cli.Run first = Cli.run("render", example("minimal"), "--layout", "generic",
                "--out", "-");
        Cli.Run second = Cli.run("render", example("minimal"), "--layout", "generic",
                "--out", "-");

        assertArrayEquals(Fixtures.bytes("golden/render-minimal-de-a4.pdf"), first.out(),
                "the rendering is the one checked in, byte for byte; if the change was"
                        + " intended, write the file again");
        assertArrayEquals(first.out(), second.out(), "two runs write the same bytes");
    }

    @Test
    void writesOneSelfContainedHtmlPageWithHtml() {
        Cli.Run run = Cli.run("render", example("standard-invoice"), "--html", "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = run.text();
        assertTrue(page.startsWith("<!DOCTYPE HTML>"), "an HTML document: " + head(page));
        assertTrue(page.contains("<html lang=\"de\">"), "German is the default language");
        assertFalse(page.contains("<link"), "self-contained: nothing is loaded from outside");
    }

    @Test
    void theLanguageReachesTheRendering() {
        Cli.Run run = Cli.run("render", example("standard-invoice"), "--html", "--lang", "en",
                "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("<html lang=\"en\">"), "--lang en reached the page");
    }

    @Test
    void thePaperReachesTheRendering() {
        Cli.Run a4 = Cli.run("render", example("standard-invoice"), "--out", "-");
        Cli.Run letter = Cli.run("render", example("standard-invoice"), "--page", "LETTER",
                "--out", "-");

        assertEquals(ExitCode.SUCCESS, letter.exitCode(), letter.err());
        assertFalse(Arrays.equals(a4.out(), letter.out()),
                "the two papers are two layouts");
    }

    @Test
    void thePaperIsWrittenInAnyCase() {
        Cli.Run upper = Cli.run("render", example("minimal"), "--page", "LETTER", "--out", "-");
        Cli.Run lower = Cli.run("render", example("minimal"), "--page", "letter", "--out", "-");

        assertEquals(ExitCode.SUCCESS, lower.exitCode(), lower.err());
        assertArrayEquals(upper.out(), lower.out(), "the case of the token does not matter");
    }

    /**
     * A page size and an HTML page is not a mistake worth refusing — the page a browser lays
     * out has no paper — but it is worth saying, because a caller who wrote it expected
     * something of it.
     */
    @Test
    void saysThatThePaperDidNothingForAnHtmlPage() {
        Cli.Run run = Cli.run("render", example("minimal"), "--html", "--page", "LETTER",
                "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("warning: --page is the paper of the PDF"),
                "the run is told the size took no effect: " + run.err());
    }

    @Test
    void saysNothingAboutThePaperWhereNobodyNamedOne() {
        Cli.Run run = Cli.run("render", example("minimal"), "--html", "--out", "-");

        assertFalse(run.err().contains("--page"), "an unmentioned option is not discussed");
    }

    @Test
    void readsAUblInvoiceAsWell() {
        Cli.Run run = Cli.run("render", Fixtures.file(directory, UBL), "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertPdf(run.out());
    }

    @Test
    void readsTheStandardInput() {
        Cli.Run run = Cli.run(Fixtures.bytes("examples/minimal.esj.json"),
                "render", "-", "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertPdf(run.out());
    }

    @Test
    void refusesAnInputItCannotRecognizeWithTheInputCode() {
        Cli.Run run = Cli.run("not text".getBytes(StandardCharsets.UTF_8),
                "render", "-", "--out", "-");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().startsWith("error: "), run.err());
        assertEquals(0, run.out().length, "nothing was written where nothing was rendered");
    }

    @Test
    void refusesALanguageItDoesNotKnow() {
        Cli.Run run = Cli.run("render", example("minimal"), "--lang", "fr", "--out", "-");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--lang takes de or en, not 'fr'"), run.err());
    }

    @Test
    void refusesAPaperItDoesNotKnow() {
        Cli.Run run = Cli.run("render", example("minimal"), "--page", "A3", "--out", "-");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--page takes A4 or LETTER, not 'A3'"), run.err());
    }

    /** A command line without a destination is a usage error, which is exit code 2 as well. */
    @Test
    void refusesACommandLineWithoutADestination() {
        Cli.Run run = Cli.run("render", example("minimal"));

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--out"), run.err());
    }

    /**
     * A bound of this run is a policy of the run and no statement about the document, so it
     * is code 7 here as everywhere else — and nothing of a rendering is written, because half
     * a rendering is worse than none.
     */
    @Test
    void refusesADocumentThatOutgrewABoundOfThisRunWithTheLimitCode() {
        Path target = directory.resolve("invoice.pdf");

        Cli.Run run = Cli.run("render", example("minimal"), "--max-document-bytes", "100",
                "--out", target.toString());

        assertEquals(ExitCode.LIMIT, run.exitCode());
        assertFalse(Files.exists(target), "no file where there is no rendering");
    }

    /** A destination that cannot be written is the output code, not a verdict of any kind. */
    @Test
    void reportsADestinationItCannotWrite() {
        Path target = directory.resolve("no-such-directory").resolve("invoice.pdf");

        Cli.Run run = Cli.run("render", example("minimal"), "--out", target.toString());

        assertEquals(ExitCode.OUTPUT, run.exitCode());
        assertTrue(run.err().contains("cannot write "), run.err());
    }

    /**
     * {@code --template} puts the letterhead of the caller under the page. The example
     * template of the repository is copied whole into a directory of its own, because a
     * template names the files it needs beside it and is read from there.
     */
    @Test
    void rendersOnTheTemplateItWasGiven() throws IOException {
        String template = templates();

        Cli.Run run = Cli.run("render", example("standard-invoice"),
                "--template", template, "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertPdf(run.out());
        assertFalse(Arrays.equals(run.out(),
                        Fixtures.bytes("golden/render-minimal-de-a4.pdf")),
                "a branded rendering is not the generic one");
    }

    /**
     * {@code --layout} reaches the renderer: the two layouts show the same document and are
     * two different files.
     */
    @Test
    void theLayoutReachesTheRendering() {
        Cli.Run generic = Cli.run("render", example("standard-invoice"), "--layout", "generic",
                "--out", "-");
        Cli.Run letter = Cli.run("render", example("standard-invoice"), "--layout", "letter",
                "--out", "-");

        assertEquals(ExitCode.SUCCESS, generic.exitCode(), generic.err());
        assertEquals(ExitCode.SUCCESS, letter.exitCode(), letter.err());
        assertFalse(Arrays.equals(generic.out(), letter.out()),
                "the two layouts are two renderings");
    }

    /**
     * Without {@code --layout} the PDF is the letter, the layout the library draws where
     * nobody names one: the same bytes as {@code --layout letter}, on either paper and in
     * either language.
     */
    @Test
    void theLetterIsTheLayoutWithoutTheOption() {
        for (String[] options : new String[][] {{}, {"--page", "LETTER", "--lang", "en"}}) {
            Cli.Run unnamed = Cli.run(render(example("standard-invoice"), options));
            Cli.Run letter = Cli.run(render(example("standard-invoice"), options,
                    "--layout", "letter"));

            assertEquals(ExitCode.SUCCESS, unnamed.exitCode(), unnamed.err());
            assertArrayEquals(letter.out(), unnamed.out(),
                    "no layout named is the letter layout: " + Arrays.toString(options));
        }
    }

    @Test
    void refusesALayoutItDoesNotKnow() {
        Cli.Run run = Cli.run("render", example("minimal"), "--layout", "poster", "--out", "-");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--layout takes generic or letter"), run.err());
    }

    /**
     * {@code --no-payment-code} leaves the code of a credit transfer out of the letter. It
     * is a switch of the letter layout, so the generic rendering is the same file with it
     * and without it.
     */
    @Test
    void thePaymentCodeIsLeftOutWhereTheCommandLineSaysSo() {
        Cli.Run letter = Cli.run("render", example("standard-invoice"), "--out", "-");
        Cli.Run without = Cli.run("render", example("standard-invoice"),
                "--no-payment-code", "--out", "-");
        Cli.Run generic = Cli.run("render", example("standard-invoice"), "--layout", "generic",
                "--out", "-");
        Cli.Run genericWithout = Cli.run("render", example("standard-invoice"),
                "--layout", "generic", "--no-payment-code", "--out", "-");

        assertEquals(ExitCode.SUCCESS, without.exitCode(), without.err());
        assertFalse(Arrays.equals(letter.out(), without.out()),
                "the letter with the code is not the letter without it");
        assertArrayEquals(generic.out(), genericWithout.out(),
                "and the generic layout draws none either way");
    }

    /** The HTML page draws no code, and a run that asks for none is told that it did not. */
    @Test
    void saysThatThePaymentCodeDoesNotReachTheHtmlPage() {
        Cli.Run run = Cli.run("render", example("minimal"), "--html", "--no-payment-code",
                "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("--no-payment-code is about the payment block"),
                run.err());
    }

    /** The HTML page has no layout of this tool's, and a run that names one is told so. */
    @Test
    void saysThatALayoutDoesNotReachTheHtmlPage() {
        Cli.Run run = Cli.run("render", example("minimal"), "--html", "--layout", "letter",
                "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("--layout is the page layout of the PDF"), run.err());
    }

    /** A template that is not one is a fault of the command line, and the message says which. */
    @Test
    void reportsATemplateItCannotRead() {
        Path broken = directory.resolve("broken.json");
        write(broken, "{\"template\": \"esj-render-template/9.9\"}");

        Cli.Run run = Cli.run("render", example("minimal"), "--template", broken.toString(),
                "--out", "-");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("esj-render-template/0.1"), run.err());
    }

    /** The HTML page is somebody else's layout, and a template does not change it. */
    @Test
    void saysThatATemplateDoesNotReachTheHtmlPage() throws IOException {
        Cli.Run run = Cli.run("render", example("minimal"), "--html",
                "--template", templates(), "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("--template is a layout of the PDF"), run.err());
    }

    /**
     * The page bound is a bound of this run: reaching it is exit code 7, nothing is
     * written, and the message names the switch that raises it.
     */
    @Test
    void aRenderingPastThePageBoundIsALimitAndNotAVerdict() {
        Path target = directory.resolve("invoice.pdf");

        Cli.Run run = Cli.run("render", Fixtures.file(directory, UBL), "--max-pages", "1",
                "--out", target.toString());

        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        assertTrue(run.err().contains("--max-pages"), run.err());
        assertFalse(Files.exists(target), "no file where there is no rendering");
    }

    /**
     * The HTML page is held to {@code --max-output-bytes} as every other document this tool
     * writes is: a page past it is exit code 7, nothing is written, and the message names
     * the switch. The page is stopped where it reached the bound rather than finished.
     */
    @Test
    void anHtmlPagePastTheOutputBoundIsALimitAndNotAVerdict() {
        Path target = directory.resolve("invoice.html");

        Cli.Run run = Cli.run("render", example("standard-invoice"), "--html",
                "--max-output-bytes", "10k", "--out", target.toString());

        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        assertTrue(run.err().contains("--max-output-bytes"), run.err());
        assertFalse(Files.exists(target), "no file where there is no rendering");
    }

    /** The bound is the size of the page and not less: a page of that size is written. */
    @Test
    void anHtmlPageAtTheOutputBoundIsWritten() {
        Cli.Run whole = Cli.run(render(example("standard-invoice"), new String[] {"--html"}));
        assertEquals(ExitCode.SUCCESS, whole.exitCode(), whole.err());
        String bound = Integer.toString(whole.out().length);

        Cli.Run at = Cli.run(render(example("standard-invoice"),
                new String[] {"--html", "--max-output-bytes", bound}));
        Cli.Run under = Cli.run(render(example("standard-invoice"),
                new String[] {"--html", "--max-output-bytes",
                    Integer.toString(whole.out().length - 1)}));

        assertEquals(ExitCode.SUCCESS, at.exitCode(), at.err());
        assertArrayEquals(whole.out(), at.out(), "the same page");
        assertEquals(ExitCode.LIMIT, under.exitCode(), under.err());
        assertEquals(0, under.out().length, "and nothing of it one byte under that");
    }

    /**
     * A line feed is legal in every text of a document, the invoice number among them,
     * which stands in the footer of every page, and the buyer reference, which stands in
     * the reference line of the letter. Both layouts draw such a document, the HTML page
     * shows it, and the validation report renders it: it is no internal error.
     */
    @Test
    void anInvoiceNumberOnTwoLinesIsRenderedInEveryForm() {
        Path file = directory.resolve("two-lines.esj.json");
        String document = new String(Fixtures.bytes("examples/smallest-valid.esj.json"),
                StandardCharsets.UTF_8)
                .replace("\"/BT-1\": \"RE-2026-0002\"", "\"/BT-1\": \"INV\\n2026-1\"")
                .replace("\"/BT-9\"", "\"/BT-10\": \"REF\\r\\n0815\",\n    \"/BT-9\"");
        write(file, document);
        assertTrue(document.contains("INV\\n2026-1") && document.contains("REF\\r\\n0815"),
                document);

        for (String[] options : List.of(new String[0], new String[] {"--layout", "generic"},
                new String[] {"--layout", "letter"}, new String[] {"--html"})) {
            Cli.Run run = Cli.run(render(file.toString(), options));
            assertEquals(ExitCode.SUCCESS, run.exitCode(),
                    String.join(" ", options) + ": " + run.err());
        }
        for (String format : List.of("pdf", "html")) {
            Cli.Run run = Cli.run("validate", file.toString(), "--report", "-",
                    "--report-format", format);
            assertEquals(ExitCode.SUCCESS, run.exitCode(), format + ": " + run.err());
        }
        Path cii = directory.resolve("two-lines.xml");
        Cli.Run converted = Cli.run("convert", file.toString(), "--to", "cii",
                "--out", cii.toString());
        assertEquals(ExitCode.SUCCESS, converted.exitCode(), converted.err());
        Cli.Run fromCii = Cli.run(render(cii.toString(), new String[0]));
        assertEquals(ExitCode.SUCCESS, fromCii.exitCode(),
                "the same invoice number read from a cross industry invoice: " + fromCii.err());
    }

    /**
     * A template refers to files beside it, and a symbolic link beside it that points out
     * of its directory is not followed: the refusal names the file, and it is the code of
     * a template that is not one.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aTemplateThatLinksOutOfItsDirectoryIsRefused() throws IOException {
        String template = templates();
        Path letterhead = Path.of(template).resolveSibling("letterhead.pdf");
        Path outside = Files.write(directory.resolve("elsewhere.pdf"),
                Files.readAllBytes(letterhead));
        Files.delete(letterhead);
        Files.createSymbolicLink(letterhead, outside);

        Cli.Run run = Cli.run("render", example("minimal"), "--template", template,
                "--out", directory.resolve("out.pdf").toString());

        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("letterhead.pdf") && run.err().contains("link"),
                run.err());
    }

    /** Copies the example templates into a directory of their own and names the file. */
    private String templates() throws IOException {
        Path into = Files.createDirectory(directory.resolve("templates"));
        for (String file : new String[] {"letterhead.json", "letterhead.pdf"}) {
            Files.write(into.resolve(file), Fixtures.bytes("examples/templates/" + file));
        }
        return into.resolve("letterhead.json").toString();
    }

    private static void write(Path target, String content) {
        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the arguments of a render run to the standard output: a file, then options. */
    private static String[] render(String file, String[] options, String... more) {
        List<String> arguments = new ArrayList<>(List.of("render", file));
        arguments.addAll(List.of(options));
        arguments.addAll(List.of(more));
        arguments.addAll(List.of("--out", "-"));
        return arguments.toArray(String[]::new);
    }

    /** Writes an example of the repository into the temporary directory and names it. */
    private String example(String name) {
        return Fixtures.file(directory, "examples/" + name + ".esj.json");
    }

    private static void assertPdf(byte[] content) {
        assertTrue(content.length > PDF_MAGIC.length, "something was written");
        byte[] magic = Arrays.copyOf(content, PDF_MAGIC.length);
        assertArrayEquals(PDF_MAGIC, magic, "the bytes begin with the PDF signature");
    }

    private static String head(String page) {
        return page.substring(0, Math.min(page.length(), 80));
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
