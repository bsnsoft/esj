package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.report.ValidationOutcome;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A report is a function of the run, the document and the options, and of nothing else.
 *
 * <p>It is the property a proof depends on. A report that carried a clock, a producer
 * string with a build number or an iteration order a hash decided could not be compared
 * against the copy somebody else was sent, could not be checked into a repository beside
 * the invoice, and could not be re-derived years later from the same bytes.
 *
 * <p>Two statements are checked. The first is that two runs in one process agree. The
 * second is that they agree with the files checked in beside this test, which were written
 * by another run of another build on another day — the only thing that catches a clock, a
 * locale or a subsetter that numbered its glyphs by chance.
 *
 * <p>A golden that fails is not by itself a defect. It says the report has changed, and
 * whoever changed it decides whether that was the intention; if it was, the files are
 * written again. What may not happen is that they change without anybody noticing.
 *
 * <p>The checked-in reports leave the invoice out. What the invoice looks like is the
 * subject of {@link HtmlDeterminismTest} and {@link PdfDeterminismTest}, which pin it for
 * the whole corpus; repeating a hundred kilobytes of it inside four report goldens would
 * pin the same thing a second time and make every one of them unreadable in a review. The
 * one golden that does carry it is the PDF, where the invoice is pages of the same file.
 */
class ReportDeterminismTest {

    /** Where the checked-in reports sit on the test classpath. */
    private static final String GOLDEN = "/golden/";

    private final ReportRenderer renderer = new ReportRenderer();

    static List<Golden> goldens() {
        return List.of(
                new Golden("report-valid-de.html", Outcomes.valid(), Outcomes.instance(),
                        ReportOptions.defaults().withInvoice(false)),
                new Golden("report-mutated-en.html", Outcomes.mutated(), Outcomes.instance(),
                        ReportOptions.in(RenderLanguage.ENGLISH).withInvoice(false)),
                new Golden("report-esj-en.html", Outcomes.esj(),
                        Corpus.example("standard-invoice"),
                        ReportOptions.in(RenderLanguage.ENGLISH).withInvoice(false)
                                .at("2026-09-21T08:15:00+02:00")),
                new Golden("report-pdf-de.html", Outcomes.pdf(), Corpus.example("minimal"),
                        ReportOptions.defaults().withInvoice(false)));
    }

    @ParameterizedTest
    @MethodSource("goldens")
    void theReportIsTheOneCheckedIn(Golden golden) {
        assertEquals(Corpus.text(GOLDEN + golden.name()),
                renderer.html(golden.outcome(), golden.document(), golden.options()),
                golden.name() + " is unchanged, character for character");
    }

    @ParameterizedTest
    @MethodSource("goldens")
    void renderingAReportTwiceGivesTheSameFile(Golden golden) {
        ValidationOutcome outcome = golden.outcome();
        SemanticDocument document = golden.document();
        ReportOptions whole = golden.options().withInvoice(true);

        assertEquals(renderer.html(outcome, document, whole),
                renderer.html(outcome, document, whole),
                golden.name() + ": the page is the same string twice");
        assertArrayEquals(renderer.pdf(outcome, document, whole),
                renderer.pdf(outcome, document, whole),
                golden.name() + ": the PDF is the same bytes twice");
    }

    /**
     * The PDF form, with the invoice after it as pages of the same file, is the one checked
     * in.
     */
    @Test
    void thePdfReportOfTheMutatedRunIsTheOneCheckedIn() {
        byte[] rendered = renderer.pdf(Outcomes.mutated(), Outcomes.instance(),
                ReportOptions.in(RenderLanguage.ENGLISH));

        byte[] golden = Corpus.bytes(GOLDEN + "report-mutated-en.pdf");
        assertEquals(Corpus.sha256(golden), Corpus.sha256(rendered),
                "report-mutated-en.pdf is unchanged");
        assertArrayEquals(golden, rendered, "report-mutated-en.pdf is unchanged, byte for byte");
    }

    /**
     * The paper is a choice, and the PDF report on the other one is checked in too. A
     * report is a document somebody files and prints, and the paper it is filed on is not
     * A4 everywhere the tool runs; the report pages and the invoice pages after them are
     * laid out on the same sheet, which is what this golden pins.
     */
    @Test
    void thePdfReportOnLetterPaperIsTheOneCheckedIn() {
        byte[] rendered = renderer.pdf(Outcomes.mutated(), Outcomes.instance(),
                ReportOptions.in(RenderLanguage.ENGLISH).on(PageSize.LETTER));

        byte[] golden = Corpus.bytes(GOLDEN + "report-mutated-en-letter.pdf");
        assertEquals(Corpus.sha256(golden), Corpus.sha256(rendered),
                "report-mutated-en-letter.pdf is unchanged");
        assertArrayEquals(golden, rendered,
                "report-mutated-en-letter.pdf is unchanged, byte for byte");
    }

    /**
     * The machine the report is written on takes no part in it. A default locale of another
     * script is the one property of a machine that reaches a number through a formatter
     * that was not told which language it is writing, and a page number is where it would
     * show.
     */
    @Test
    void theReportIsTheSameUnderAnyDefaultLocale() {
        Locale machine = Locale.getDefault();
        try {
            for (String tag : new String[] {"ar-EG", "ne-NP", "tr-TR"}) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                assertEquals(Corpus.text(GOLDEN + "report-valid-de.html"),
                        renderer.html(Outcomes.valid(), Outcomes.instance(),
                                ReportOptions.defaults().withInvoice(false)),
                        "the page does not depend on the locale of the machine");
                assertArrayEquals(Corpus.bytes(GOLDEN + "report-mutated-en.pdf"),
                        renderer.pdf(Outcomes.mutated(), Outcomes.instance(),
                                ReportOptions.in(RenderLanguage.ENGLISH)),
                        "and neither does the PDF");
            }
        } finally {
            Locale.setDefault(machine);
        }
    }

    /** A page is written in UTF-8 and says so, because it is read from a file. */
    @Test
    void thePageDeclaresTheEncodingItIsWrittenIn() {
        String page = renderer.html(Outcomes.valid(), null, ReportOptions.defaults());

        assertEquals(page, new String(page.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8), "the page survives a round trip through UTF-8");
        assertTrue(page.contains("<meta charset=\"UTF-8\">"), page);
    }

    /**
     * One checked-in report.
     *
     * @param name     the file beside this test
     * @param outcome  the run it is about
     * @param document the document that was judged
     * @param options  the options it was written with
     */
    record Golden(String name, ValidationOutcome outcome, SemanticDocument document,
                  ReportOptions options) {

        @Override
        public String toString() {
            return name;
        }
    }
}
