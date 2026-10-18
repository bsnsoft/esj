package de.bsnsoft.esj.render.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.render.RenderLanguage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * The page of {@code esj view} as this module writes it: inert whatever the names it is
 * given, every reference to a file of its directory and nothing else, the report's style
 * sheet first and unchanged, the views as tabs, and the words a reader reads. What a run
 * puts on it, state by state, is held by the goldens of {@code esj-cli}.
 */
class ViewPageTest {

    private static final String HOSTILE = "<script>alert(1)</script>‮.pdf";

    @Test
    void aNameOfAStrangerIsTextAndNeverMarkup() {
        String page = ViewPage.html(new ViewPage.Content(RenderLanguage.ENGLISH, HOSTILE,
                "/tmp/view", "esj 0.0.0")
                .received(HOSTILE)
                .deadEnd(ViewPage.DeadEnd.several(List.of(new ViewPage.Candidate(1, HOSTILE,
                        ViewPage.Candidate.Kind.OTHER, "<b>bold</b>",
                        OptionalLong.of(12), Optional.of("<i>"),
                        "esj view --report --attachment-index 1 x.pdf")))));

        assertFalse(page.contains("<script"), page);
        assertFalse(page.contains("<b>bold</b>"), page);
        assertFalse(page.contains("<i>"), page);
        assertFalse(page.contains("‮"), "the reading order is not the stranger's");
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), page);
        assertTrue(page.contains("src=\"received/%3Cscript%3Ealert%281%29%3C/script%3E%E2%80%AE.pdf\""),
                page);
    }

    @Test
    void theStyleSheetOfTheReportComesFirstAndUnchanged() {
        String page = ViewPage.html(content().received("x.pdf"));
        assertTrue(page.contains("<style>\n" + ReportHtml.STYLE), page);
        assertTrue(page.contains(":root { color-scheme: light; }"), page);
    }

    @Test
    void everyReferenceIsAFileOfTheDirectory() {
        String page = ViewPage.html(content().received("a b.pdf")
                .document(Corpus.example("standard-invoice"), Registry.en16931())
                .letter(ViewPage.Letter.written("a b.letter.pdf"))
                .kosit("a b.kosit.html", List.of("one", "two", "three", "four")));
        java.util.regex.Matcher references =
                java.util.regex.Pattern.compile("(?:src|href)=\"([^\"]*)\"").matcher(page);
        int found = 0;
        while (references.find()) {
            String reference = references.group(1);
            found++;
            assertTrue(!reference.contains(":") && !reference.startsWith("/")
                    && !reference.contains(".."), reference);
        }
        assertEquals(3, found, "three frames, and no link: " + page);
        assertTrue(page.contains("<p class=\"caption\">This view does not show every detail"
                + " of the invoice.</p>"), "what did not reach it is said once: " + page);
    }

    /** Every view is a tab, in one order; a tab is there only where its content is. */
    @Test
    void theViewsAreTabsAndTheFirstOneIsSelected() {
        String page = ViewPage.html(content().received("x.pdf")
                .document(Corpus.example("standard-invoice"), Registry.en16931())
                .letter(ViewPage.Letter.written("x.letter.pdf"))
                .kosit("x.kosit.html", List.of()));
        int pdf = page.indexOf("<input type=\"radio\" name=\"tab\" id=\"tab-pdf\" checked>"
                + "<label for=\"tab-pdf\">PDF</label>");
        int invoice = page.indexOf("<input type=\"radio\" name=\"tab\" id=\"tab-invoice\">"
                + "<label for=\"tab-invoice\">Invoice</label>");
        int kosit = page.indexOf("<label for=\"tab-kosit\">KoSIT</label>");
        assertTrue(pdf > 0 && invoice > pdf && kosit > invoice, page);
        assertFalse(page.contains("id=\"tab-report\""), "no report, no tab for it: " + page);
        assertTrue(page.contains("<iframe sandbox=\"allow-scripts\" src=\"x.kosit.html\""),
                "the KoSIT view runs its script in a frame of its own: " + page);
        assertTrue(page.contains("<iframe src=\"x.letter.pdf\""), page);

        String xml = ViewPage.html(content()
                .document(Corpus.example("standard-invoice"), Registry.en16931())
                .letter(ViewPage.Letter.written("x.letter.pdf")));
        assertTrue(xml.contains("id=\"tab-invoice\" checked>"),
                "without a PDF the invoice is the first tab, and selected: " + xml);
        assertFalse(xml.contains("id=\"tab-pdf\""), xml);
    }

    @Test
    void theFactsAreWrittenAsTheLetterWritesThem() {
        String page = ViewPage.html(content()
                .document(Corpus.example("standard-invoice"), Registry.en16931())
                .letter(ViewPage.Letter.refused()));
        assertTrue(page.contains("<p class=\"parties\">Example GmbH → Muster AG</p>"), page);
        assertTrue(page.contains("<div><dt>Invoice number</dt><dd>RE-2026-0042</dd></div>"),
                page);
        assertTrue(page.contains("<div><dt>Invoice date</dt><dd>2026-02-03</dd></div>"), page);
        assertTrue(page.contains("<div class=\"due\"><dt>Amount due</dt><dd>2,915.50 EUR</dd>"),
                page);
        assertFalse(page.contains("class=\"kind\""), "an invoice is not called one: " + page);
        assertTrue(page.contains("<p>This invoice cannot be shown here.</p>"), page);
    }

    @Test
    void aCreditNoteNamesItsTypeAndItsAmountCredited() {
        String page = ViewPage.html(new ViewPage.Content(RenderLanguage.GERMAN, "x.xml",
                "/tmp/view", "esj 0.0.0")
                .document(Corpus.example("credit-note"), Registry.en16931())
                .letter(ViewPage.Letter.written("x.letter.pdf")));
        assertTrue(page.contains("<p class=\"kind\">Gutschrift</p>"), page);
        assertTrue(page.contains("<dt>Gutschriftsnummer</dt>"), page);
        assertTrue(page.contains("<dt>Gutschriftsbetrag</dt><dd>355,81 EUR</dd>"), page);
        assertTrue(page.contains("<label for=\"tab-invoice\">Rechnung</label>"),
                "a credit note is an invoice too: " + page);
    }

    @Test
    void withoutAReportTheVerdictIsNotChecked() {
        String page = ViewPage.html(new ViewPage.Content(RenderLanguage.GERMAN, "x.xml",
                "/tmp/view", "esj 0.0.0")
                .document(Corpus.example("standard-invoice"), Registry.en16931())
                .letter(ViewPage.Letter.written("x.letter.pdf")));
        assertTrue(page.contains("<p class=\"verdict verdict-NONE\"><b>NICHT GEPRÜFT</b></p>"),
                page);
        assertFalse(page.contains("class=\"counts\""), page);
        assertTrue(page.contains("<footer>\n<p>Ablage: <span>/tmp/view</span> · esj 0.0.0</p>"),
                page);
    }

    /** The page writes the verdict in the reader's language; any other word as it stands. */
    @Test
    void theVerdictIsWrittenInTheWordsOfTheReader() {
        assertEquals("GÜLTIG", ViewPage.word("VALID", RenderLanguage.GERMAN));
        assertEquals("UNGÜLTIG", ViewPage.word("INVALID", RenderLanguage.GERMAN));
        assertEquals("NICHT ENTSCHIEDEN", ViewPage.word("INDETERMINATE", RenderLanguage.GERMAN));
        assertEquals("NICHT GEPRÜFT", ViewPage.word("NOT CHECKED", RenderLanguage.GERMAN));
        assertEquals("UNDECIDED", ViewPage.word("INDETERMINATE", RenderLanguage.ENGLISH));
        assertEquals("VALID", ViewPage.word("VALID", RenderLanguage.ENGLISH));
        assertEquals("OK", ViewPage.word("OK", RenderLanguage.GERMAN));
    }

    /** A dead end says what the file is and what to ask for, and a limit names its option. */
    @Test
    void aDeadEndSaysWhatToAskTheSenderFor() {
        assertEquals("Dieses PDF enthält keine E-Rechnung. Bitten Sie den Absender um die"
                        + " E-Rechnung.",
                ViewPage.state(ViewPage.DeadEnd.of(ViewPage.DeadEnd.Kind.NO_INVOICE),
                        RenderLanguage.GERMAN, false));
        ViewPage.DeadEnd limit = ViewPage.DeadEnd.limit(new ViewPage.Limit(
                ViewPage.Limit.Kind.SIZE, "--max-input-bytes"));
        assertEquals("Die Datei ist größer, als diese Prüfung zulässt.",
                ViewPage.state(limit, RenderLanguage.GERMAN, true));
        String page = ViewPage.html(content().deadEnd(limit));
        assertTrue(page.contains("<p class=\"state-head\">The file is larger than this view"
                + " allows.</p>\n<p><code>--max-input-bytes</code></p>"), page);
        assertFalse(page.contains("<main"), "nothing to show, no tabs: " + page);
    }

    @Test
    void aPageWithAnInvoiceShowsItsLetterOrSaysWhyNot() {
        assertThrows(IllegalStateException.class, () -> ViewPage.html(content()
                .document(Corpus.example("standard-invoice"), Registry.en16931())));
    }

    @Test
    void aReferenceIsPercentEncodedByteForByte() {
        assertEquals("received/Rechnung%20%234711%20100%25%20%C3%BC.pdf",
                ViewPage.href("received/Rechnung #4711 100% ü.pdf"));
        assertEquals("a-b_c.d~e/f", ViewPage.href("a-b_c.d~e/f"));
    }

    private static ViewPage.Content content() {
        return new ViewPage.Content(RenderLanguage.ENGLISH, "x.xml", "/tmp/view", "esj 0.0.0");
    }
}
