package de.bsnsoft.esj.render.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.internal.report.Text;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.render.RenderLanguage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * The page of {@code esj view} as this module writes it: inert whatever the names and the
 * messages it is given, every reference to a file of its directory and nothing else, and the
 * report's style sheet first and unchanged. What a run puts on it, state by state, is held
 * by the goldens of {@code esj-cli}.
 */
class ViewPageTest {

    private static final String HOSTILE = "<script>alert(1)</script>‮.pdf";

    @Test
    void aNameOrAMessageOfAStrangerIsTextAndNeverMarkup() {
        String page = ViewPage.html(new ViewPage.Content(RenderLanguage.ENGLISH, HOSTILE,
                "in/" + HOSTILE, "/tmp/view", "esj 0.0.0")
                .received(HOSTILE)
                .deadEnd(new ViewPage.DeadEnd(ViewPage.DeadEnd.Kind.SEVERAL,
                        "\"" + HOSTILE + "\" carries two",
                        List.of(new ViewPage.Candidate(1, HOSTILE,
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
        String page = ViewPage.html(content().letter(ViewPage.Letter.written("x.letter.pdf")));
        assertTrue(page.contains("<style>\n" + ReportHtml.STYLE), page);
        assertTrue(page.contains(":root { color-scheme: light; }"), page);
    }

    @Test
    void everyReferenceIsAFileOfTheDirectoryOrAnAnchorOfThePage() {
        String page = ViewPage.html(content().received("a b.pdf")
                .letter(ViewPage.Letter.written("a b.letter.pdf"))
                .kosit("a b.kosit.html", List.of("one", "two", "three", "four")));
        java.util.regex.Matcher references =
                java.util.regex.Pattern.compile("(?:src|href)=\"([^\"]*)\"").matcher(page);
        int found = 0;
        while (references.find()) {
            String reference = references.group(1);
            found++;
            assertTrue(reference.startsWith("#") || !reference.contains(":")
                    && !reference.startsWith("/") && !reference.contains(".."), reference);
        }
        assertEquals(7, found, "two anchors, two frames and three links of the views");
        assertTrue(page.contains("<details>\n<summary>4 values of the document did not reach"
                + " this rendering:</summary>"), "more than three are folded: " + page);
    }

    @Test
    void theFactsAreWrittenAsTheLetterWritesThem() {
        String page = ViewPage.html(content()
                .document(Corpus.example("standard-invoice"), Registry.en16931())
                .letter(ViewPage.Letter.refused(Text.words("no"))));
        assertTrue(page.contains("<p class=\"title\">Invoice RE-2026-0042 of 2026-02-03</p>"),
                page);
        assertTrue(page.contains("<div class=\"due\"><dt>Amount due</dt><dd>2,915.50 EUR</dd>"),
                page);
    }

    @Test
    void aPageThatIsNoDeadEndShowsTheInvoiceAsReadOrSaysWhyNot() {
        assertThrows(IllegalStateException.class, () -> ViewPage.html(content()));
    }

    @Test
    void aReferenceIsPercentEncodedByteForByte() {
        assertEquals("received/Rechnung%20%234711%20100%25%20%C3%BC.pdf",
                ViewPage.href("received/Rechnung #4711 100% ü.pdf"));
        assertEquals("a-b_c.d~e/f", ViewPage.href("a-b_c.d~e/f"));
    }

    private static ViewPage.Content content() {
        return new ViewPage.Content(RenderLanguage.ENGLISH, "x.xml", "x.xml", "/tmp/view",
                "esj 0.0.0");
    }
}
