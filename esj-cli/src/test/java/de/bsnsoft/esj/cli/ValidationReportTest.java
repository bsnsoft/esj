package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code esj validate --report}: the file a person keeps, written through the command line.
 *
 * <p>What the report looks like is the business of {@code esj-render} and is tested there,
 * against its own goldens. What is tested here is the wiring: that the run reaches the
 * report unchanged, that the options land where they were aimed, that the lines are the
 * same with and without it, and that the file is the same bytes for the same input.
 *
 * <p>The exit code is the one thing that is not the same with and without it. A report
 * the run could not deliver — a destination it cannot write, a deadline met while the
 * file is being drawn — leaves with {@link ExitCode#OUTPUT}, unless the verdict is
 * {@link ExitCode#VALIDATION} and keeps its own code; the printed lines stand either way.
 */
class ValidationReportTest {

    /** The first bytes of a PDF file. */
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    /** The example the goldens are written from, read from the standard input. */
    private static final String EXAMPLE = "examples/standard-invoice.esj.json";

    /** A hybrid PDF: the container block, the syntax block and the provenance in one run. */
    private static final String HYBRID = "conformance/pdf/factur-x.pdf";

    /** What the tool of this build calls itself, which a golden must not be pinned to. */
    private static final String TOOL = "esj " + version();

    /** What stands in the golden in place of the version of the build that wrote it. */
    private static final String TOOL_PLACEHOLDER = "esj ${version}";

    @TempDir
    private Path directory;

    @Test
    void writesTheReportBesideTheLinesAndLeavesBothUnchanged() {
        Path target = directory.resolve("report.html");

        Cli.Run plain = Cli.run("validate", example());
        Cli.Run reported = Cli.run("validate", example(), "--report", target.toString());

        assertEquals(ExitCode.SUCCESS, reported.exitCode(), reported.err());
        assertEquals(plain.text(), reported.text(),
                "a report is a second form of one answer, not a second answer");
        assertTrue(Files.exists(target), "the file was written");
        assertTrue(read(target).startsWith("<!DOCTYPE html>"), "an HTML page");
    }

    /** The name says the form where it can, so that a caller writes one option and not two. */
    @Test
    void theNameOfTheFileDecidesTheForm() {
        Path page = directory.resolve("report.html");
        Path printed = directory.resolve("report.pdf");

        Cli.run("validate", example(), "--report", page.toString());
        Cli.run("validate", example(), "--report", printed.toString());

        assertTrue(read(page).startsWith("<!DOCTYPE html>"), "the .html name is a page");
        assertPdf(bytes(printed));
    }

    @Test
    void writesTheReportToTheStandardOutputWithTheFormatNamed() {
        Cli.Run run = Cli.run(bytes(), "validate", "-", "--report", "-",
                "--report-format", "pdf");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertPdf(run.out());
        assertTrue(run.err().contains("VALID"),
                "the lines go to the error stream where the report has the output");
    }

    @Test
    void refusesTheStandardOutputWithoutAFormat() {
        Cli.Run run = Cli.run(bytes(), "validate", "-", "--report", "-");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--report-format"), run.err());
    }

    @Test
    void refusesANameItCannotReadAFormFrom() {
        Cli.Run run = Cli.run("validate", example(), "--report",
                directory.resolve("report.txt").toString());

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--report-format"), run.err());
    }

    /**
     * A name that says a form and a format that says another are two answers to one
     * question, and the file would hold the one the reader does not expect.
     */
    @Test
    void refusesAFormatThatContradictsTheName() {
        Path target = directory.resolve("report.html");

        Cli.Run run = Cli.run("validate", example(), "--report", target.toString(),
                "--report-format", "pdf");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("the name decides what a file holds"), run.err());
        assertFalse(Files.exists(target), "and nothing was written");
    }

    /** A format that says what the name says is no contradiction. */
    @Test
    void takesAFormatThatAgreesWithTheName() {
        Path target = directory.resolve("report.pdf");

        Cli.Run run = Cli.run("validate", example(), "--report", target.toString(),
                "--report-format", "pdf");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertPdf(bytes(target));
    }

    @Test
    void refusesAFormatItDoesNotKnow() {
        Cli.Run run = Cli.run("validate", example(), "--report", "-",
                "--report-format", "docx");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--report-format takes html or pdf, not 'docx'"),
                run.err());
    }

    @Test
    void refusesALanguageItDoesNotKnow() {
        Cli.Run run = Cli.run("validate", example(), "--report",
                directory.resolve("report.html").toString(), "--report-lang", "fr");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--report-lang takes de or en, not 'fr'"), run.err());
    }

    /**
     * Two machine forms cannot share one stream. The combination is refused rather than
     * written, because a caller who asked for both and received them interleaved would have
     * neither.
     */
    @Test
    void refusesTheStandardOutputForTheReportAndForTheJson() {
        Cli.Run run = Cli.run(bytes(), "validate", "-", "--report", "-",
                "--report-format", "html", "--output", "json");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("standard output"), run.err());
    }

    /** A report beside the machine form is two files and no conflict. */
    @Test
    void theMachineFormAndAReportAreWrittenTogether() {
        Path target = directory.resolve("report.html");

        Cli.Run run = Cli.run("validate", example(), "--output", "json",
                "--report", target.toString());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().startsWith("{"), "the JSON has the standard output");
        assertTrue(Files.exists(target), "and the report has the file");
    }

    @Test
    void theLanguageReachesTheReport() {
        Path german = directory.resolve("de.html");
        Path english = directory.resolve("en.html");

        Cli.run("validate", example(), "--report", german.toString());
        Cli.run("validate", example(), "--report", english.toString(),
                "--report-lang", "en");

        assertTrue(read(german).contains("<html lang=\"de\">"), "German is the default");
        assertTrue(read(english).contains("<html lang=\"en\">"), "--report-lang en arrived");
    }

    /**
     * A report carries no clock of its own, so that the same input and the same options give
     * the same file. A caller who needs the moment on the page passes it, and it is printed
     * as it was written.
     */
    @Test
    void theMomentIsPrintedOnlyWhereTheCallerPassedOne() {
        Path without = directory.resolve("without.html");
        Path with = directory.resolve("with.html");

        Cli.run("validate", example(), "--report", without.toString());
        Cli.run("validate", example(), "--report", with.toString(),
                "--report-time", "2026-09-21T10:00:00+02:00");

        assertFalse(read(without).contains("2026-09-21"), "no clock of its own");
        assertTrue(read(with).contains("2026-09-21T10:00:00+02:00"), "printed as given");
    }

    @Test
    void leavesTheRenderedInvoiceOutOnRequest() {
        Path with = directory.resolve("with.html");
        Path without = directory.resolve("without.html");

        Cli.run("validate", example(), "--report", with.toString());
        Cli.run("validate", example(), "--report", without.toString(), "--no-invoice");

        assertTrue(read(with).contains("<iframe"), "the invoice is in the page by default");
        assertFalse(read(without).contains("<iframe"), "--no-invoice left it out");
    }

    /** An option that describes a report nobody asked for is said out loud rather than lost. */
    @Test
    void saysSoWhereTheOptionsDescribeAReportThatWasNotAskedFor() {
        Cli.Run run = Cli.run("validate", example(), "--no-invoice");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("without --report none is written"), run.err());
    }

    /**
     * The report is written from the same run the lines were written from, so a document
     * something is wrong with — the case a report is most needed in — is reported rather
     * than refused, and the exit code is the verdict and not the writing of a file.
     */
    @Test
    void reportsADocumentTheRendererWillNotTakeAndKeepsTheVerdict() {
        Path target = directory.resolve("report.pdf");

        Cli.Run run = Cli.run("validate",
                Fixtures.file(directory, "examples/invalid/unknown-term.esj.json"),
                "--report", target.toString());

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("INVALID"), run.text());
        assertPdf(bytes(target));
    }

    /**
     * The row an ESJ input owes its complete check to is in the report, in both languages.
     *
     * <p>An ESJ document never was XML, so the official artefacts read a rendition of it
     * that this run wrote, and that row is what lets such an input reach {@code VALID} at
     * all. A report that carried the verdict and not the row would be evidence with its
     * reason left out, so the row, the syntax it was written in and the components that
     * ran under it are all here — the same rows the command printed.
     */
    @Test
    void theArtefactsOverTheWrittenDocumentAreARowOfTheReport() {
        Path english = directory.resolve("en.html");
        Path german = directory.resolve("de.html");

        Cli.Run run = Cli.run("validate", example(), "--report", english.toString(),
                "--report-lang", "en", "--no-invoice");
        Cli.run("validate", example(), "--report", german.toString(), "--no-invoice");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = read(english);
        assertTrue(page.contains("Official artefacts over the written document (CII)"),
                "the row names the syntax the document was written in: " + page);
        assertTrue(page.contains("CII D16B XSD"), "and the components ran under it");
        assertTrue(run.text().contains("official artefacts over the written CII"),
                "which is the row the command printed: " + run.text());
        assertTrue(read(german)
                        .contains("Amtliche Artefakte über dem geschriebenen Dokument (CII)"),
                "a German report carries it in German");
    }

    /**
     * Where the artefacts were not asked over the written document, the row says so.
     *
     * <p>{@code --no-syntax} takes them out, and the report has to read as the lines do:
     * a component of the complete check that did not run, with the reason beside it, and
     * never a row that looks like a pass.
     */
    @Test
    void theWrittenRowSaysWhereTheArtefactsWereNotAsked() {
        Path target = directory.resolve("report.html");

        Cli.Run run = Cli.run("validate", example(), "--no-syntax", "--report",
                target.toString(), "--report-lang", "en", "--no-invoice");

        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        String page = read(target);
        assertTrue(page.contains("Official artefacts over the written document (CII)"),
                "the row is there: " + page);
        assertTrue(page.contains("skipped: --no-syntax"), "with the reason it did not run");
    }

    /** A destination that cannot be written is the output code, and never a verdict. */
    @Test
    void reportsADestinationItCannotWrite() {
        Path target = directory.resolve("no-such-directory").resolve("report.html");

        Cli.Run run = Cli.run("validate", example(), "--report", target.toString());

        assertEquals(ExitCode.OUTPUT, run.exitCode());
        assertTrue(run.err().contains("cannot write "), run.err());
    }

    /**
     * An invalid verdict outranks the missing file, as it does on the deadline.
     *
     * <p>The two ways a report does not arrive — the destination and the clock — are one
     * rule and not two: the verdict is reached and printed before either can happen, so
     * the caller that came for the verdict has it, and the code says so. A run that
     * answered the question and could not file the answer is still an answer.
     */
    @Test
    void anInvalidVerdictKeepsItsCodeWhereTheDestinationCannotBeWritten() {
        Path target = directory.resolve("no-such-directory").resolve("report.html");

        Cli.Run run = Cli.run("validate",
                Fixtures.file(directory, "examples/invalid/unknown-term.esj.json"),
                "--report", target.toString());

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("INVALID"), run.text());
        assertTrue(run.err().contains("cannot write "), run.err());
    }

    /**
     * The report of an ESJ document, as this command line writes it.
     *
     * <p>The file checked in is the report without the invoice, because the rendering of the
     * invoice is what {@code esj-render} pins and what this test is about is everything
     * around it: the identity with its digests, the packs, the rows of both blocks and the
     * verdict. The version of the build is the one thing in it that is not a property of the
     * document, so the golden carries a placeholder for it; everything else is compared byte
     * for byte. To write the file again, run the same command line over the same example and
     * replace the version of the build in the output with {@code ${version}}.
     */
    @Test
    void theReportIsTheOneCheckedIn() {
        Cli.Run run = Cli.run(bytes(), "validate", "-", "--report", "-",
                "--report-format", "html", "--report-lang", "en", "--no-invoice");
        Cli.Run again = Cli.run(bytes(), "validate", "-", "--report", "-",
                "--report-format", "html", "--report-lang", "en", "--no-invoice");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals(Fixtures.text("golden/validate-report-en.html"),
                run.text().replace(TOOL, TOOL_PLACEHOLDER),
                "the report is the one checked in; if the change was intended, write the"
                        + " file again");
        assertArrayEquals(run.out(), again.out(), "two runs write the same bytes");
    }

    /** The container, the syntax block and the provenance of a hybrid PDF all reach it. */
    @Test
    void aHybridPdfCarriesItsContainerBlockIntoTheReport() {
        Path target = directory.resolve("report.html");

        Cli.Run run = Cli.run("validate", Fixtures.file(directory, HYBRID),
                "--report", target.toString(), "--report-lang", "en");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = read(target);
        assertTrue(page.contains("PDF/A-3B — declared, not validated"),
                "what the file declares, said to be a declaration");
        assertTrue(page.contains("the invoice was taken out of attachment 1"),
                "where the invoice came from");
        assertTrue(page.contains("xrechnung/3.0.2"), "the pack that judged it, with version");
    }

    /**
     * The two verdicts of a run over a container reach the report, because the one word
     * above them cannot say that a conformant file carries no EN 16931 invoice, or that a
     * sound invoice sits inside a file that is wrong about it.
     */
    @Test
    void theTwoVerdictsOfAContainerReachTheReport() {
        Path target = directory.resolve("report.html");

        Cli.Run run = Cli.run("validate", Fixtures.file(directory, HYBRID),
                "--report", target.toString(), "--report-lang", "en");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = read(target);
        assertTrue(page.contains("<span>Container:</span> OK"), page);
        assertTrue(page.contains("<span>Invoice:</span> VALID"), page);
        assertTrue(run.text().contains("Container:        OK"),
                "and they are the lines the command printed: " + run.text());
    }

    /**
     * What the import could not carry is in the report, counted in a row of its own. A
     * report that said {@code VALID} over a document the importer shortened, and did not
     * say that it had been shortened, would be evidence for something that was never
     * checked.
     */
    @Test
    void whatTheImportDidNotCarryIsInTheReport() {
        Path target = directory.resolve("report.html");

        Cli.Run run = Cli.run("validate",
                Fixtures.file(directory, "conformance/kosit/technical-cases/cius/"
                        + "01.02_comprehensive_test_ubl.xml"),
                "--report", target.toString(), "--report-lang", "en");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = read(target);
        assertTrue(run.err().contains("did not reach the document"),
                "the error stream says it: " + run.err());
        assertTrue(page.contains("DUPLICATE_PATH [warning] — import, importer"),
                "and so does the report: " + page);
        assertTrue(page.contains("<td>Import</td>"), "with a row that counts it: " + page);
    }

    /**
     * The reader that built the document is named beside the digests it decided, because
     * {@code --importer} changes them and two reports of the same bytes would otherwise be
     * indistinguishable.
     */
    @Test
    void theReaderThatBuiltTheDocumentIsNamed() {
        Path streaming = directory.resolve("streaming.html");
        Path xslt = directory.resolve("xslt.html");
        String instance = Fixtures.file(directory,
                "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml");

        Cli.run("validate", instance, "--report", streaming.toString(),
                "--report-lang", "en");
        Cli.run("validate", instance, "--report", xslt.toString(), "--report-lang", "en",
                "--importer", "xslt");

        assertTrue(read(streaming).contains("<dt>Reader</dt><dd class=\"mono\">streaming"),
                read(streaming));
        assertTrue(read(xslt).contains("<dt>Reader</dt><dd class=\"mono\">xslt"),
                read(xslt));
    }

    /**
     * A German report is German where the words are this project's own. The reader that
     * built the document, the parenthesis that says an invoice came out of a container and
     * the reason a rule set of the pack did not run are three sentences this project
     * writes itself, and they travel from the run as a phrase rather than as English.
     */
    @Test
    void theGermanReportOfAContainerIsGerman() {
        Path target = directory.resolve("report.html");

        Cli.Run run = Cli.run("validate", Fixtures.file(directory, HYBRID),
                "--report", target.toString());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = read(target);
        assertTrue(page.contains("<dt>Leser</dt><dd class=\"mono\">streaming — der"
                + " Streaming-Leser der Bindungstabellen</dd>"), page);
        assertTrue(page.contains("CII (aus einem PDF-Anhang)"), page);
        assertFalse(page.contains("the streaming reader of the binding tables"),
                "and nothing this project wrote is left in English: " + page);
        assertFalse(page.contains("from a PDF attachment"), page);
        assertFalse(page.contains("it validates no document of the profile"), page);
    }

    /**
     * A verdict that was reached is not unreached by the file about it running out of
     * time. {@code --max-runtime} covers the report like every other step, but a bound met
     * while the report is being drawn is met after the verdict: the file is what is given
     * up, and the printed lines are what they would have been without {@code --report} at
     * all.
     *
     * <p>The exit code is the one thing that is not the same, because a caller who asked
     * for a file and received none has not had what it asked for: a run that gave the
     * report up leaves with {@link ExitCode#OUTPUT}. Neither outcome is
     * {@link ExitCode#LIMIT}, which would say the document has no verdict.
     *
     * <p>Nothing here is asserted about which of the two happened, because how fast a
     * report is drawn is a property of the machine.
     */
    @Test
    void aBoundMetWhileTheReportIsDrawnLeavesTheVerdictStanding() {
        String document = Fixtures.write(directory, "many.esj.json",
                ScaleValidationTest.invoice(2_000));
        Path target = directory.resolve("many.pdf");

        Cli.Run plain = Cli.run("validate", "--no-syntax", "--rules", "none", document);
        Cli.Run reported = Cli.run("validate", "--no-syntax", "--rules", "none", document,
                "--report", target.toString(), "--max-runtime", "1s");

        assertNotEquals(ExitCode.LIMIT, reported.exitCode(),
                "a bound met after the verdict does not unreach it: " + reported.err());
        assertEquals(plain.text(), reported.text(),
                "a bound on the report is no second answer about the document");
        assertFalse(reported.err().contains("no verdict on the document"),
                "the document was judged, and the run says so: " + reported.err());
        if (Files.exists(target)) {
            assertEquals(plain.exitCode(), reported.exitCode(),
                    "the report arrived, so the run answers as it would without it: "
                            + reported.err());
        } else {
            assertTrue(reported.err().contains("no report was written"),
                    "either the file is there or the run says why it is not: "
                            + reported.err());
            assertEquals(ExitCode.OUTPUT, reported.exitCode(),
                    "and a file the caller asked for and did not get is an output that"
                            + " was not written in full: " + reported.err());
        }
    }

    /**
     * {@code --report -} is the shape the difference matters most in: the report <em>is</em>
     * the standard output, so a caller that took a zero exit code for the answer would be
     * reading zero bytes as a report about a valid invoice. Either the report arrives and
     * the run answers as it would without it, or it does not and the exit code says that
     * something the caller asked for is missing.
     *
     * <p>Which of the two happens is a property of the machine, as above, so both are
     * admitted and the pairing of bytes and code is what is asserted.
     */
    @Test
    void aReportOnTheStandardOutputThatWasNotDeliveredIsAnExitCodeOfItsOwn() {
        String document = Fixtures.write(directory, "many-lines.esj.json",
                ScaleValidationTest.invoice(2_000));

        Cli.Run plain = Cli.run("validate", "--no-syntax", "--rules", "none", document);
        Cli.Run reported = Cli.run("validate", "--no-syntax", "--rules", "none", document,
                "--report", "-", "--report-format", "pdf", "--max-runtime", "1s");

        assertNotEquals(ExitCode.LIMIT, reported.exitCode(), reported.err());
        if (reported.out().length > 0) {
            assertEquals(plain.exitCode(), reported.exitCode(), reported.err());
        } else {
            assertTrue(reported.err().contains("no report was written"), reported.err());
            assertEquals(ExitCode.OUTPUT, reported.exitCode(),
                    "zero bytes on the standard output is not a report: " + reported.err());
        }
    }

    /** Returns the example of the repository as a file of the temporary directory. */
    private String example() {
        return Fixtures.file(directory, EXAMPLE);
    }

    /** Returns the bytes of the example, for a run that reads the standard input. */
    private static byte[] bytes() {
        return Fixtures.bytes(EXAMPLE);
    }

    private static String read(Path file) {
        return new String(bytes(file), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertPdf(byte[] content) {
        assertTrue(content.length > PDF_MAGIC.length, "something was written");
        assertArrayEquals(PDF_MAGIC, Arrays.copyOf(content, PDF_MAGIC.length),
                "the bytes begin with the PDF signature");
    }

    /** Returns the version of this build, as the tool prints it in {@code --version}. */
    private static String version() {
        for (String line : Cli.run("--version").text().split("\n")) {
            if (line.startsWith("esj ")) {
                return line.substring("esj ".length()).trim();
            }
        }
        throw new IllegalStateException("esj --version names the version of the build");
    }
}
