package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/**
 * What the semantic-only check costs on an invoice of the size this project designs for.
 *
 * <p>Invoices with hundreds of thousands of lines are a real input, and the combination this
 * measures is the one such an invoice is validated with: {@code --no-syntax} leaves out the
 * Schematron, which is the expensive half of the tool and the half a caller who has already
 * validated the XML does not need again, and what remains is the reader, the structural layers
 * and the native business rules — all three linear in the number of lines.
 *
 * <p><strong>How large the document is, is a decision of whoever runs the build.</strong> The
 * whole document is held in memory as bytes and again as a semantic document, so the size that
 * fits depends on the heap the test runner was given. The default is
 * {@value #DEFAULT_LINES} lines, which the {@code -Xmx1g} of this module carries with room to
 * spare; the field sizes are one command away:
 *
 * <pre>{@code mvn -pl esj-cli test -Dtest=ScaleValidationTest -Desj.cli.scale.lines=100000}</pre>
 *
 * <p>What is asserted is that the document is still answered and answered correctly — the
 * synthetic invoice is arithmetically sound and satisfies the business rules, so a run over it
 * must end {@code VALID} with nothing to report. The time is printed rather than asserted,
 * because a build machine is not a benchmark; the numbers taken once are in
 * {@code docs/validation-measurements.md} beside the numbers for the syntax engine.
 */
class ScaleValidationTest {

    /** The system property that says how many invoice lines the document has. */
    static final String LINES_PROPERTY = "esj.cli.scale.lines";

    /** The number of lines measured when the property is not set. */
    private static final int DEFAULT_LINES = 20_000;

    /** What one line of the synthetic invoice comes to. */
    private static final int LINE_NET = 100;

    @TempDir
    private Path directory;

    @Test
    void answersALargeInvoiceWithoutTheOfficialArtefacts() {
        int lines = Integer.getInteger(LINES_PROPERTY, DEFAULT_LINES);
        byte[] document = invoice(lines);
        String file = Fixtures.write(directory, "scale.esj.json", document);

        long started = System.nanoTime();
        Cli.Run run = Cli.run("validate", "--no-syntax", "--limits", "large", file);
        Duration wall = Duration.ofNanos(System.nanoTime() - started);

        String measured = String.format(Locale.ROOT,
                "esj validate --no-syntax --limits large over %,d invoice lines (%,d bytes)"
                        + " took %,d ms%n", lines, document.length, wall.toMillis());
        System.out.print(measured);

        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err() + measured);
        assertTrue(run.text().contains("Cardinality (L3):          OK"), run.text());
        assertTrue(run.text().contains(RuleCheck.LABEL + ": OK"),
                "the synthetic invoice satisfies the business rules: " + run.text());
        assertTrue(run.text().contains(SyntaxCheck.BY_OPTION), run.text());
        assertTrue(run.text().contains("written-syntax (skipped-by-caller)"),
                "the official artefacts are a required row for an ESJ input, and the run"
                        + " that leaves them out says so rather than claiming a verdict: "
                        + run.text());
    }

    /**
     * Returns an ESJ document of many invoice lines that every engine accepts.
     *
     * <p>It is written as bytes rather than built through the reader, because the subject of
     * the measurement is the command reading a file a stranger handed it. Every line is the
     * same hundred, so the totals follow from the count, and the terms the business rules ask
     * for beyond the mandatory ones are present: a seller VAT identifier, a payment due date,
     * a rate on the VAT breakdown and on every line, which a zero rated category fixes at
     * zero.
     *
     * <p>It is not private, because the report tests need a document large enough that
     * drawing a report about it takes real time, and a second generator of the same thing
     * would be a second thing to keep sound.
     *
     * @param lines how many invoice lines
     * @return the document, UTF-8
     */
    static byte[] invoice(int lines) {
        long total = (long) lines * LINE_NET;
        StringBuilder json = new StringBuilder(lines * 200)
                .append("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\",")
                .append("\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{");
        value(json, "/BT-1", "RE-2026-0001", true);
        value(json, "/BT-2", "2026-01-15", false);
        value(json, "/BT-3", "380", false);
        value(json, "/BT-5", "EUR", false);
        value(json, "/BT-9", "2026-02-15", false);
        value(json, "/BG-2/BT-24", "urn:cen.eu:en16931:2017", false);
        value(json, "/BG-4/BT-27", "Example GmbH", false);
        value(json, "/BG-4/BT-31", "DE123456789", false);
        value(json, "/BG-4/BG-5/BT-40", "DE", false);
        value(json, "/BG-7/BT-44", "Muster AG", false);
        value(json, "/BG-7/BG-8/BT-55", "DE", false);
        value(json, "/BG-22/BT-106", Long.toString(total), false);
        value(json, "/BG-22/BT-109", Long.toString(total), false);
        value(json, "/BG-22/BT-110", "0", false);
        value(json, "/BG-22/BT-112", Long.toString(total), false);
        value(json, "/BG-22/BT-115", Long.toString(total), false);
        value(json, "/BG-23/0/BT-116", Long.toString(total), false);
        value(json, "/BG-23/0/BT-117", "0", false);
        value(json, "/BG-23/0/BT-118", "Z", false);
        value(json, "/BG-23/0/BT-119", "0", false);
        for (int line = 0; line < lines; line++) {
            String at = "/BG-25/" + line;
            value(json, at + "/BT-126", Integer.toString(line + 1), false);
            value(json, at + "/BT-129", "1", false);
            value(json, at + "/BT-130", "C62", false);
            value(json, at + "/BT-131", Integer.toString(LINE_NET), false);
            value(json, at + "/BG-29/BT-146", Integer.toString(LINE_NET), false);
            value(json, at + "/BG-30/BT-151", "Z", false);
            value(json, at + "/BG-30/BT-152", "0", false);
            value(json, at + "/BG-31/BT-153", "Service " + (line + 1), false);
        }
        return json.append("}}").toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Appends one member of the {@code values} object, with the separator it needs. */
    private static void value(StringBuilder json, String path, String content, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(path).append("\":\"").append(content).append('"');
    }
}
