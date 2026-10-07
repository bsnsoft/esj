package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * What a rendering may cost.
 *
 * <p>A reader's bounds are about the input, and a rendering is not: a document well inside
 * every one of them can be hundreds of pages, because one long value in a narrow column
 * is, and because an invoice number stands in the footer of every page. So a rendering has
 * a bound of its own — counted in pages for the PDF, and in bytes for the HTML page, which
 * has no pages and grows with the document — and reaching it is a statement about this run
 * and not about the invoice: the same document renders where more is allowed.
 */
class RenderBoundTest {

    @Test
    void theDefaultIsGenerousEnoughForAnOrdinaryInvoice() {
        assertEquals(2_000, RenderOptions.DEFAULT_MAX_PAGES, "the default bound");

        byte[] pdf = new PdfRenderer().render(Documents.withDetailedLines(300));

        assertTrue(Pdf.pages(pdf) > 5 && Pdf.pages(pdf) < RenderOptions.DEFAULT_MAX_PAGES,
                "three hundred lines are " + Pdf.pages(pdf) + " pages, well inside it");
    }

    @Test
    void aRenderingThatRunsPastTheBoundIsRefusedAndNamesIt() {
        SemanticDocument document = Documents.withDetailedLines(300);

        RenderLimitException refused = assertThrows(RenderLimitException.class,
                () -> new PdfRenderer().render(document,
                        RenderOptions.defaults().withMaxPages(3)));

        assertTrue(refused.getMessage().contains("3 pages"),
                "the refusal names the bound: " + refused.getMessage());
    }

    /**
     * A single value can be a rendering of many pages without the document being large.
     * That is the case the bound is for, and it is the one a reader's limits do not see.
     */
    @Test
    void oneEnormousValueIsWhatTheBoundIsFor() {
        SemanticDocument document = Documents.withALineNamedAtLength(4_000);

        assertThrows(RenderLimitException.class, () -> new PdfRenderer().render(document,
                RenderOptions.defaults().withMaxPages(2)));
        assertTrue(Pdf.pages(new PdfRenderer().render(document)) > 2,
                "and the same document renders where the bound allows it");
    }

    @Test
    void aBoundOfLessThanOnePageIsNotABound() {
        assertThrows(IllegalArgumentException.class,
                () -> RenderOptions.defaults().withMaxPages(0));
    }

    @Test
    void aDocumentInsideTheBoundIsRenderedWhole() {
        byte[] pdf = new PdfRenderer().render(Corpus.example("minimal"),
                RenderOptions.defaults().withMaxPages(1));

        assertEquals(1, Pdf.pages(pdf), "one page, and the bound was one page");
    }

    // ---------------------------------------------------------------- the HTML page

    /**
     * The HTML page has no pages to count, and grows with the document instead: it is
     * bounded by its size in bytes of UTF-8, measured while it is written. A page of
     * exactly the bound is the page; one byte less and there is none.
     */
    @Test
    void anHtmlPageIsBoundedByItsSizeToTheByte() {
        SemanticDocument document = Corpus.example("standard-invoice");
        String whole = new HtmlRenderer().render(document);
        int bytes = whole.getBytes(StandardCharsets.UTF_8).length;

        assertEquals(whole, new HtmlRenderer().render(document,
                        RenderOptions.defaults().withMaxHtmlBytes(bytes)),
                "a page of the size of the bound is the same page");
        RenderLimitException refused = assertThrows(RenderLimitException.class,
                () -> new HtmlRenderer().render(document,
                        RenderOptions.defaults().withMaxHtmlBytes(bytes - 1)));
        assertTrue(refused.getMessage().contains((bytes - 1) + " bytes"),
                "the refusal names the bound: " + refused.getMessage());
    }

    /**
     * The bytes are counted as UTF-8 writes them, so a document of umlauts and of
     * characters outside the basic plane is measured as what it weighs on disk.
     */
    @Test
    void theSizeOfAnHtmlPageIsItsSizeInUtf8() {
        SemanticDocument document = Corpus.example("standard-invoice").toBuilder()
                .set(SemanticPath.of("/BG-4/BT-27"),
                        SemanticValue.of("Bäckerei Groß \u20ac \ud83d\ude00 ".repeat(50)))
                .build();
        int bytes = new HtmlRenderer().render(document)
                .getBytes(StandardCharsets.UTF_8).length;

        new HtmlRenderer().render(document, RenderOptions.defaults().withMaxHtmlBytes(bytes));
        assertThrows(RenderLimitException.class, () -> new HtmlRenderer().render(document,
                RenderOptions.defaults().withMaxHtmlBytes(bytes - 1)));
    }

    /** The PDF is bounded by its pages and does not ask the bound on an HTML page. */
    @Test
    void thePdfIsNotHeldToTheBoundOfAnHtmlPage() {
        assertTrue(Pdf.pages(new PdfRenderer().render(Corpus.example("standard-invoice"),
                RenderOptions.defaults().withMaxHtmlBytes(1))) > 0, "the PDF is drawn");
    }

    @Test
    void theDefaultBoundOfAnHtmlPageIsAGibibyte() {
        assertEquals(1L << 30, RenderOptions.DEFAULT_MAX_HTML_BYTES);
        assertEquals(RenderOptions.DEFAULT_MAX_HTML_BYTES,
                RenderOptions.defaults().maxHtmlBytes());
        assertThrows(IllegalArgumentException.class,
                () -> RenderOptions.defaults().withMaxHtmlBytes(0));
    }
}
