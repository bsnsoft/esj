package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * What a document of a megabyte costs.
 *
 * <p>An EN 16931 invoice of ordinary size is validated in milliseconds, and that number
 * says nothing about an invoice with thousands of lines. This builds one — a corpus
 * instance whose invoice line is repeated until the document passes a megabyte — and
 * measures the three blocks of the engine on it. What is asserted is that the document is
 * still answered, and answered inside the time a run is given by default; the figures
 * themselves are not, because a build machine is not a benchmark. They travel in the
 * message of the assertion, so a failure says what it took, and a measurement taken once
 * is written down in the README beside the ledger of the corpus.
 *
 * <p>The repeated lines make the totals of the document wrong, so the arithmetic rules of
 * EN 16931 report on it. That is the point: this measures the cost of a document the rule
 * sets have something to say about, not of one they walk past.
 */
class SyntaxScaleTest {

    private static final String BASE = "business-cases/standard/01.01a-INVOICE_ubl.xml";

    private static final int MEGABYTE = 1024 * 1024;

    @Test
    void answersADocumentOfAMegabyte() {
        byte[] document = repeated(MEGABYTE);
        assertTrue(document.length >= MEGABYTE, "the document is a megabyte or more");

        long start = System.nanoTime();
        SyntaxReport report = SyntaxValidator.validate(document,
                SyntaxOptions.defaults().withMaxInputBytes(8L * MEGABYTE));
        Duration wall = Duration.ofNanos(System.nanoTime() - start);

        String measured = "a UBL invoice of " + document.length + " bytes with "
                + lines(document) + " invoice lines took " + wall.toMillis() + " ms, of it "
                + report.ran().stream().map(run -> run.component() + " "
                        + run.duration().toMillis() + " ms").toList();

        assertEquals(3, report.ran().size(), "the schema and both rule sets ran over it");
        assertTrue(report.ran().stream().allMatch(run -> !run.duration().isNegative()),
                "every component reports what it cost: " + measured);
        assertEquals(Verdict.INVALID, report.verdict(),
                "the repeated lines make the totals of the document wrong");
        assertTrue(report.findings().stream()
                        .anyMatch(finding -> finding.code().equals("BR-CO-10")),
                "the sum of the line net amounts is reported");
        assertTrue(wall.compareTo(SyntaxOptions.DEFAULT_MAX_RUNTIME) < 0,
                "a document of this size is answered inside the time a run is given by"
                        + " default: " + measured);
    }

    /** The base instance with its invoice line repeated until the document is this large. */
    private static byte[] repeated(int atLeast) {
        String document = new String(Corpus.instance(BASE), StandardCharsets.UTF_8);
        int from = document.indexOf("<cac:InvoiceLine>");
        int to = document.lastIndexOf("</cac:InvoiceLine>") + "</cac:InvoiceLine>".length();
        String line = document.substring(from, to);
        int rest = document.length() - line.length();
        int copies = Math.max(1, (atLeast - rest) / line.length() + 1);
        return (document.substring(0, from) + line.repeat(copies) + document.substring(to))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static int lines(byte[] document) {
        return new String(document, StandardCharsets.UTF_8).split("<cac:InvoiceLine>",
                -1).length - 1;
    }
}
