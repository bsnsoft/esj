package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Two runs over the same bytes with the same pack produce the same report.
 *
 * <p>It is worth a test of its own because nothing in the parts guarantees it. The
 * findings arrive from three sources; a schema validator and an XSLT processor each
 * report in their own order; and the order the components run in is a loop over a
 * manifest. The report sorts them by content and by nothing that varies between runs, and
 * that is what makes a golden file possible and a difference between two reports worth
 * reading.
 */
class SyntaxDeterminismTest {

    /** An instance the artefacts have several things to say about, in both severities. */
    private static final String MANY = "business-cases/extension/04.05a-INVOICE_uncefact.xml";

    @Test
    void repeatsItself() {
        byte[] document = Corpus.instance(MANY);

        List<String> first = describe(SyntaxValidator.validate(document));
        List<String> second = describe(SyntaxValidator.validate(document));
        List<String> third = describe(SyntaxValidator.validate(document));

        assertTrue(first.size() > 1, "the document produces more than one finding");
        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    void ordersTheFindingsByTheirContent() {
        SyntaxReport report = SyntaxValidator.validate(Corpus.instance(MANY));

        List<SyntaxFinding> sorted = new ArrayList<>(report.findings());
        sorted.sort(SyntaxFinding.ORDER);

        assertEquals(sorted, report.findings(), "the report is in the order it declares");
    }

    /**
     * The verdict does not depend on the order either: the same document validated with
     * the pack named explicitly is the same document validated with the pack chosen from
     * the profile.
     */
    @Test
    void answersTheSameWhicheverWayThePackIsChosen() {
        byte[] document = Corpus.instance(MANY);
        SyntaxOptions named = SyntaxOptions.defaults()
                .withPack(Packs.bundled("xrechnung/3.0.2/2026-08-31"));

        assertEquals(describe(SyntaxValidator.validate(document)),
                describe(SyntaxValidator.validate(document, named)));
    }

    /**
     * The rule identifier of a schema finding is the same on every machine.
     *
     * <p>It is read off the front of a sentence the platform's validator writes, and that
     * sentence is localized: a French virtual machine writes {@code cvc-… : } with a space
     * before the colon where an English one writes {@code cvc-…: }. A report whose code
     * fell back to {@code XSD-INVALID} on one machine and named the constraint on another
     * would be a report that cannot be compared, and the mutation set pins exactly these
     * identifiers.
     */
    @Test
    void namesTheSchemaConstraintWhateverTheLocaleOfTheMachineIs() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            SyntaxReport report = SyntaxValidator.validate(
                    Mutations.apply(Mutations.of("xsd-ubl-missing-invoice-number")));

            assertEquals(List.of("cvc-complex-type.2.4.a"),
                    report.findings(Engine.XSD).stream().map(SyntaxFinding::code).toList(),
                    "the schema finding names the constraint of the recommendation");
        } finally {
            Locale.setDefault(before);
        }
    }

    private static List<String> describe(SyntaxReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(report.verdict() + " " + report.pack().orElseThrow().directory());
        for (SyntaxFinding finding : report.findings()) {
            lines.add(finding.engine() + " " + finding.component() + " "
                    + finding.category() + " " + finding.severity() + " " + finding.code()
                    + " " + finding.location() + " " + finding.message());
        }
        for (SkippedComponent skipped : report.skipped()) {
            lines.add("skipped " + skipped.component() + ": " + skipped.message());
        }
        return lines;
    }
}
