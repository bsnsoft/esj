package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a fatal finding of the native rule pack costs the invoice, and what a levelled one
 * does not.
 *
 * <p>The overall verdict weighs every engine, and the {@code Invoice:} line of a container
 * report weighs the same ones: a document whose only fatal finding is a business rule of
 * EN 16931 is an invalid invoice inside a container that may be immaculate. The case the
 * two could be told apart on is the one no official artefact reports — the CII assertion of
 * {@code BR-AF-08} reads its operands from a node where they are not written and cannot
 * fire ({@code conformance/rules/ledger.md}) — so the native pack is the only engine that
 * faults the document, and the line, the {@code invoice} member and the exit code have to
 * be one answer.
 *
 * <p>The other half of the same question is the profile: where the specification a document
 * names levels that rule, the finding is information and decides nothing, and the invoice
 * is valid with the finding printed under it.
 */
class NativeRuleVerdictTest {

    /**
     * A mutation of the corpus that moves the taxable amount of a VAT breakdown one unit of
     * the invoice currency away from the sum it must equal, in the one category whose CII
     * assertion cannot report.
     */
    private static final String MUTATION = "af08-one-unit-cii";

    /** The rule that faults it, at the level EN 16931 gives it. */
    private static final String RULE = "BR-AF-08";

    /** A corpus instance whose profile levels two native findings down to information. */
    private static final String LEVELLED =
            "conformance/kosit/business-cases/extension/04.05a-INVOICE_uncefact.xml";

    /** The invoice member of a report whose only fatal finding is a native rule. */
    private static final String INVOICE_FAILED = "\"invoice\": {\n    \"checked\": true,\n"
            + "    \"ok\": false,\n    \"reason\": \"rule-finding\"\n  }";

    @TempDir
    private Path directory;

    /** A fatal native finding over an XML input is what the invoice member reports. */
    @Test
    void aFatalNativeFindingIsWhyTheInvoiceIsInvalid() {
        String xml = Fixtures.write(directory, MUTATION + ".xml", mutation());

        Cli.Run run = Cli.run("validate", xml, "--output", "json");

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"verdict\": \"INVALID\""), run.text());
        assertTrue(run.text().contains(INVOICE_FAILED), run.text());
    }

    /**
     * The same invoice inside a PDF: the container is sound and the invoice is not, and the
     * line says so.
     */
    @Test
    void aFatalNativeFindingIsAnInvoiceFailureInAHybrid() {
        String pdf = hybrid(MUTATION + ".xml", mutation());

        Cli.Run run = Cli.run("validate", pdf);

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains("Container:        OK"),
                "the container this tool wrote: " + run.text());
        assertTrue(run.text().contains("Invoice:          INVALID"),
                "the invoice inside it, faulted by " + RULE + " alone: " + run.text());
        Cli.Run json = Cli.run("validate", pdf, "--output", "json");
        assertTrue(json.text().contains(INVOICE_FAILED), json.text());
    }

    /** A finding the profile of the document levels leaves the invoice valid. */
    @Test
    void aLevelledNativeFindingLeavesTheInvoiceValid() {
        String pdf = hybrid("04.05a.xml", Fixtures.bytes(LEVELLED));

        Cli.Run run = Cli.run("validate", pdf);

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains("levelled by the profile "),
                "the finding is printed with both levels: " + run.text());
        assertTrue(run.text().contains("Invoice:          VALID"),
                "and decides nothing: " + run.text());
    }

    /**
     * The report file follows the same two answers: the invoice line of a hybrid whose only
     * fatal finding is a native rule reads {@code INVALID}, and a finding the profile
     * levelled is printed there with both levels, as the printed lines print it.
     */
    @Test
    void theReportFileSaysWhatTheRunSaidAboutANativeFinding() {
        Path faulted = directory.resolve("faulted.html");
        Path levelled = directory.resolve("levelled.html");

        Cli.run("validate", hybrid(MUTATION + ".xml", mutation()), "--report",
                faulted.toString(), "--report-lang", "en");
        Cli.run("validate", hybrid("04.05a.xml", Fixtures.bytes(LEVELLED)), "--report",
                levelled.toString(), "--report-lang", "en");

        assertTrue(read(faulted).contains("<span>Container:</span> OK"), "the container");
        assertTrue(read(faulted).contains("<span>Invoice:</span> INVALID"),
                "the invoice " + RULE + " alone faulted");
        assertTrue(read(levelled).contains("by the standard, levelled by the profile "),
                "a levelled finding carries both levels in the report");
        assertTrue(read(levelled).contains("<span>Invoice:</span> VALID"),
                "and decides nothing there either");
    }

    /** Returns the text of a report this test wrote. */
    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the document the mutation makes. */
    private static byte[] mutation() {
        return Oracle.apply(Oracle.all().stream()
                .filter(candidate -> MUTATION.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "conformance/rules/mutations carries " + MUTATION)));
    }

    /** Renders an XML invoice and embeds it in its own rendering, and returns the file. */
    private String hybrid(String name, byte[] xml) {
        String source = Fixtures.write(directory, name, xml);
        String pdf = directory.resolve(name + ".pdf").toString();
        Cli.Run rendered = Cli.run("render", source, "--embed", "cii", "--out", pdf);
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());
        return pdf;
    }

}
