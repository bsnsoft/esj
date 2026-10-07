package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.internal.report.ValidationOutcome;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.render.internal.ReportOptions;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What a line break, a control character or a character that directs the reading order
 * in a value of a document does to its renderings: nothing that stops them, and nothing
 * that reaches the page as it stands.
 *
 * <p>A line feed is legal in every text of an ESJ document, and so is every other
 * character JSON can carry. The cases put each such character into every value of a
 * document that is text — the identifiers, the codes and the references as well, and the
 * components beside them — and render it in both layouts of the PDF, as HTML, and as both
 * forms of the validation report. Every rendering has to come out: a document that is legal
 * and cannot be drawn is a defect of the renderer, not of the document. And none of these
 * characters may reach a page: a PDF shows a line end as a new line or a space and the rest
 * as a space, and no character the embedded faces have no glyph for is handed to them.
 */
class ControlCharactersRenderingTest {

    private static final Registry REGISTRY = XrImporter.defaultRegistry();

    /** The types whose values are written as they stand rather than formatted. */
    private static final Set<SemanticType> TEXTS = Set.of(SemanticType.TEXT,
            SemanticType.IDENTIFIER, SemanticType.CODE, SemanticType.DOCUMENT_REFERENCE);

    /**
     * The characters the cases put into the values one at a time: the seven ways of
     * ending a line, the tabulator, controls of C0 and C1, U+007F, a character that
     * directs the reading order, and half of a surrogate pair.
     */
    static Stream<String> hostile() {
        return Stream.of("\n", "\r\n", "\r", "\u000b", "\u000c", "\u0085", " ",
                " ", "\t", "\u0000", "\u001b[2K", "\u007f", "\u009b", "؜",
                "‮", "\ud800");
    }

    /** All of them in one text, which is what the slow cases put into every value. */
    private static final String ALL = "A\nB\r\nC\rD\u000bE\u000cF\u0085G H I\tJ"
            + "\u0000K\u001b[2KL\u007fM\u009bN؜O‮P\ud800Q";

    /**
     * The characters that may not stand in the text of a PDF or of an HTML page, which is
     * every one of {@link #ALL} but the line feed and the tabulator, which an HTML page
     * carries as white space.
     */
    private static final int[] NEVER_ON_A_PAGE = {'\r', 0x0b, 0x0c, 0x85, 0x2028, 0x2029, 0x00,
        0x1b, 0x7f, 0x9b, 0x061c, 0x202e};

    /**
     * The lead example with one character in every value that is text, in both layouts and
     * as HTML. The example states a credit transfer, so the letter has a payment block and
     * a reference line to put those values into.
     */
    @ParameterizedTest
    @MethodSource("hostile")
    void everyTextOfTheLeadExampleCarriesIt(String character) {
        SemanticDocument document = carrying(Corpus.example("standard-invoice"), character);

        for (Layout layout : Layout.values()) {
            byte[] pdf = assertDoesNotThrow(() -> new PdfRenderer().render(document,
                    RenderOptions.defaults().layout(layout)), layout + " layout");
            assertNotOnThePage(Pdf.text(pdf), layout + " layout");
        }
        String html = assertDoesNotThrow(() -> new HtmlRenderer().render(document));
        assertNotInTheHtml(html, "HTML");
    }

    /** Every term of the registry at once, carrying all of them at once, in every form. */
    @Test
    void everyTermOfTheRegistryCarriesAllOfThem() {
        SemanticDocument document = carrying(Documents.everyTerm(), ALL);

        for (RenderLanguage language : RenderLanguage.values()) {
            for (Layout layout : Layout.values()) {
                byte[] pdf = assertDoesNotThrow(() -> new PdfRenderer().render(document,
                        RenderOptions.in(language).layout(layout)), layout + " layout");
                assertNotOnThePage(Pdf.text(pdf), layout + " layout in " + language);
            }
            String html = assertDoesNotThrow(() -> new HtmlRenderer().render(document,
                    RenderOptions.in(language)));
            assertNotInTheHtml(html, "HTML in " + language);
        }
    }

    /** The validation report draws the invoice in the generic layout, and frames the HTML. */
    @Test
    void theReportOfADocumentThatCarriesThemComesOutInBothForms() {
        ReportRenderer renderer = new ReportRenderer();
        for (SemanticDocument document : List.of(carrying(Documents.everyTerm(), ALL),
                carrying(Corpus.example("standard-invoice"), ALL))) {
            byte[] pdf = assertDoesNotThrow(() ->
                    renderer.pdf(Outcomes.valid(), document, ReportOptions.defaults()));
            assertNotOnThePage(Pdf.text(pdf), "the PDF report");
            String page = assertDoesNotThrow(() ->
                    renderer.html(Outcomes.valid(), document, ReportOptions.defaults()));
            assertNotInTheHtml(page, "the HTML report");
        }
    }

    /**
     * Every character that directs the reading order, one at a time, in every text of the
     * lead example and in the name of the input a report is about: one set for every form,
     * and none of the forms carries one — the HTML page, both layouts of the PDF, and both
     * forms of the report, whose own lines and footer quote that name.
     *
     * @param codePoint the character
     */
    @ParameterizedTest
    @MethodSource("de.bsnsoft.esj.render.CharactersTest#directional")
    void aCharacterThatDirectsTheReadingOrderReachesNoForm(int codePoint) {
        String character = Character.toString(codePoint);
        SemanticDocument document = carrying(Corpus.example("standard-invoice"), character);
        ValidationOutcome valid = Outcomes.valid();
        ValidationOutcome.Identity identity = valid.identity();
        ValidationOutcome outcome = new ValidationOutcome(new ValidationOutcome.Identity(
                "invoices/RE" + character + "-2026-0042.xml", identity.syntax(),
                identity.reader(), identity.semanticModel(), identity.profile(),
                identity.inputSha256(), identity.semanticDigest(), identity.documentDigest(),
                identity.sourceSha256(), identity.packs(), identity.tool()),
                valid.blocks(), valid.verdict(), valid.detail(), valid.subjects(),
                valid.provenance());
        String what = String.format("U+%04X", codePoint);

        assertAbsent(codePoint, new HtmlRenderer().render(document), what + ", HTML");
        for (Layout layout : Layout.values()) {
            assertAbsent(codePoint, Pdf.text(new PdfRenderer().render(document,
                    RenderOptions.defaults().layout(layout))), what + ", " + layout);
        }
        ReportRenderer renderer = new ReportRenderer();
        assertAbsent(codePoint, renderer.html(outcome, document, ReportOptions.defaults()),
                what + ", the HTML report");
        assertAbsent(codePoint, Pdf.text(renderer.pdf(outcome, document,
                ReportOptions.defaults())), what + ", the PDF report");
    }

    /** Asserts that a form carries a character neither as itself nor as a reference. */
    private static void assertAbsent(int codePoint, String form, String what) {
        assertFalse(form.indexOf(codePoint) >= 0, what + " carries it");
        assertFalse(form.toLowerCase(java.util.Locale.ROOT)
                .contains(String.format("&#x%x;", codePoint)), what + " refers to it");
        assertFalse(form.contains("&#" + codePoint + ";"), what + " refers to it");
    }

    /**
     * A line break in the invoice number, which stands in the footer of every page and in
     * the head of every page after the first, and in the buyer reference, which stands in
     * a cell of the reference line that is measured before it is drawn: the two values the
     * letter used to stop at. The value keeps its two parts on the page.
     */
    @Test
    void anInvoiceNumberAndABuyerReferenceOnTwoLinesAreDrawn() {
        SemanticDocument document = Documents.withDetailedLines(120).toBuilder()
                .set(SemanticPath.of("/BT-1"), SemanticValue.of("INV\n2026-1"))
                .set(SemanticPath.of("/BT-10"), SemanticValue.of("REF\r\n0815"))
                .build();

        for (Layout layout : Layout.values()) {
            byte[] pdf = new PdfRenderer().render(document,
                    RenderOptions.defaults().layout(layout));
            assertTrue(Pdf.pages(pdf) > 1, "the letter runs over more than one page");
            String flat = Pdf.flat(pdf);
            assertTrue(flat.contains("INV") && flat.contains("2026-1"),
                    layout + ": the invoice number is on the page: " + flat);
            String lastPage = Pdf.flat(Pdf.textOfPage(pdf, Pdf.pages(pdf)));
            assertTrue(lastPage.contains("INV 2026-1"),
                    layout + ": the footer writes it on one line: " + lastPage);
        }
    }

    /** Returns a document with a character in every value that is text, and its parts. */
    private static SemanticDocument carrying(SemanticDocument document, String character) {
        SemanticDocument.Builder builder = document.toBuilder();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            SemanticPath path = entry.getKey();
            SemanticType type = REGISTRY.datatype(path.term()).orElse(SemanticType.TEXT);
            if (!TEXTS.contains(type)) {
                continue;
            }
            SemanticValue value = entry.getValue();
            builder.set(path, new SemanticValue(around(value.content(), character),
                    around(value.scheme(), character),
                    around(value.schemeVersion(), character),
                    value.mimeCode(),
                    around(value.filename(), character)));
        }
        return builder.build();
    }

    /** Puts a character into the middle of a text, and leaves an absent one absent. */
    private static String around(String text, String character) {
        if (text == null) {
            return null;
        }
        int middle = text.length() / 2;
        return text.substring(0, middle) + character + text.substring(middle);
    }

    private static void assertNotOnThePage(String text, String what) {
        List<String> found = new ArrayList<>();
        for (int c : NEVER_ON_A_PAGE) {
            if (text.indexOf(c) >= 0) {
                found.add(String.format("U+%04X", c));
            }
        }
        IntStream.range(0, text.length())
                .filter(i -> Character.isSurrogate(text.charAt(i))
                        && !(Character.isHighSurrogate(text.charAt(i)) && i + 1 < text.length()
                                && Character.isLowSurrogate(text.charAt(i + 1)))
                        && !(Character.isLowSurrogate(text.charAt(i)) && i > 0
                                && Character.isHighSurrogate(text.charAt(i - 1))))
                .findFirst()
                .ifPresent(i -> found.add("half of a surrogate pair"));
        assertEquals(List.of(), found, what + ": none of them is in the text of the PDF");
    }

    private static void assertNotInTheHtml(String html, String what) {
        List<String> found = new ArrayList<>();
        for (int c : NEVER_ON_A_PAGE) {
            if (html.indexOf(c) >= 0
                    || html.toLowerCase(java.util.Locale.ROOT)
                            .contains(String.format("&#x%x;", c))
                    || html.contains("&#" + c + ";")) {
                found.add(String.format("U+%04X", c));
            }
        }
        assertFalse(html.isEmpty(), what + " has a page");
        assertEquals(List.of(), found, what + ": none of them is in the page");
    }
}
