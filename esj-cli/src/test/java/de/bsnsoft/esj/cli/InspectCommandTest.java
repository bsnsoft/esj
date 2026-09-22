package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code esj inspect}: the page a person reads.
 *
 * <p>The golden file is compared in full rather than line by line, because the value of
 * this command is that it is the same page every time: a label that moves, a field that
 * disappears or a digest that changes is something to notice. The fixture is passed on
 * the standard input so that the first line of the page does not carry the name of a
 * temporary file.
 */
class InspectCommandTest {

    private static final String STANDARD = "examples/standard-invoice.esj.json";

    private static final String CII =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml";

    private static final String UBL =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml";

    @TempDir
    private Path directory;

    @Test
    void summarizesAnExampleExactlyAsTheGoldenFileRecordsIt() {
        Cli.Run run = Cli.run(Fixtures.bytes(STANDARD), "inspect", "-");
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(),
                "this command runs no official artefact, so it never reaches a verdict: "
                        + run.err());
        assertEquals(Fixtures.text("golden/inspect-standard-invoice.txt"), run.text());
    }

    @Test
    void summarizesAnInvoiceThatWasReadFromXml() {
        Cli.Run run = Cli.run("inspect", Fixtures.file(directory,
                "conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml"));
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("Detected syntax:            CII"), run.text());
        assertTrue(run.text().contains("Type code (BT-3):           380"), run.text());
        assertTrue(run.text().contains("Semantic digest:            "), run.text());
        assertTrue(run.text().contains("Validation pack:            xrechnung/"),
                "a summary names the pack esj validate would run: " + run.text());
        assertTrue(run.text().contains(SyntaxCheck.NOT_INSPECTED),
                "and says that it did not run it: " + run.text());
        assertTrue(run.text().endsWith("INDETERMINATE — nothing fatal found;"
                + " missing from the check:"
                        + " syntax-binding (not-run-by-this-command)\n"),
                "a page that ran no artefact never prints VALID: " + run.text());
    }

    @Test
    void theReferenceShowsWhatTheCommandActuallyPrints() {
        Cli.Run run = Cli.run(Fixtures.bytes(STANDARD), "inspect", "-");
        // Everything below the first line, which names the input and is the one line that
        // differs between the page and a run that reads the standard input.
        String body = run.text().substring(run.text().indexOf('\n') + 1);
        assertTrue(Fixtures.text("docs/cli.md").contains(body),
                "docs/cli.md shows the output of esj inspect as this version prints it");
    }

    @Test
    void showsWhatAnAmbiguousContainerCarriesBeforeRefusingIt() {
        // "What did I just receive" is the question this command answers, and a container
        // that carries two attachments either of which could be the invoice is an answer
        // to it. There is no verdict to print, so the run still leaves with exit 2 — but
        // it prints what it found and how it classified each attachment, which is what
        // the caller needs in order to name one with --attachment.
        byte[] pdf = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach("xrechnung.xml", Fixtures.bytes(UBL))
                .build();

        Cli.Run run = Cli.run(pdf, "inspect", "-");

        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.text().contains("Attachments:                2"), run.text());
        assertTrue(run.text().contains("1  \"factur-x.xml\" (CII"), run.text());
        assertTrue(run.text().contains("2  \"xrechnung.xml\" (UBL invoice"), run.text());
        assertTrue(run.err().contains("could be the electronic invoice"), run.err());
    }

    @Test
    void namesTheTermsADocumentDoesNotCarry() {
        Cli.Run run = Cli.run(Fixtures.bytes("examples/invalid/missing-mandatory-term.esj.json"),
                "inspect", "-");
        assertEquals(ExitCode.VALIDATION, run.exitCode(),
                "a summary of a document that fails a layer says so in its exit code");
        assertTrue(run.text().contains("ESJ-L3-MISSING-TERM"), run.text());
    }
}
