package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import org.junit.jupiter.api.Test;

/**
 * What a rendering may cost.
 *
 * <p>A reader's bounds are about the input, and a rendering is not: a document well inside
 * every one of them can be hundreds of pages, because one long value in a narrow column
 * is, and because an invoice number stands in the footer of every page. So a rendering has
 * a bound of its own, counted in pages, and reaching it is a statement about this run and
 * not about the invoice — the same document renders where more pages are allowed.
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
}
