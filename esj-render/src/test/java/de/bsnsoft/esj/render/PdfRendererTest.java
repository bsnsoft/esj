package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The surface of the PDF renderer: what it takes, what it gives back and what it refuses.
 */
class PdfRendererTest {

    @Test
    void writesAPdf() {
        byte[] pdf = new PdfRenderer().render(Corpus.example("standard-invoice"));

        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1),
                "the result is a PDF");
        assertTrue(Pdf.pages(pdf) >= 1, "it has a page");
    }

    @Test
    void laysTheDocumentOutForThePaperItWasGiven() {
        SemanticDocument document = Corpus.example("standard-invoice");

        float[] a4 = Pdf.pageSize(new PdfRenderer().render(document), 1);
        float[] letter = Pdf.pageSize(
                new PdfRenderer().render(document, RenderOptions.defaults().on(PageSize.LETTER)), 1);

        assertEquals(PageSize.A4.width(), a4[0], 0.01f, "the width of A4");
        assertEquals(PageSize.A4.height(), a4[1], 0.01f, "the height of A4");
        assertEquals(PageSize.LETTER.width(), letter[0], 0.01f, "the width of Letter");
        assertEquals(PageSize.LETTER.height(), letter[1], 0.01f, "the height of Letter");
    }

    @Test
    void writesTheLabelsInTheLanguageItWasGiven() {
        SemanticDocument document = Corpus.example("standard-invoice");

        String german = Pdf.flat(new PdfRenderer().render(document, RenderOptions.defaults()));
        String english = Pdf.flat(
                new PdfRenderer().render(document, RenderOptions.in(RenderLanguage.ENGLISH)));

        assertTrue(german.contains(Word.LINES.in(RenderLanguage.GERMAN)),
                "the German rendering carries the German heading");
        assertTrue(english.contains(Word.LINES.in(RenderLanguage.ENGLISH)),
                "the English rendering carries the English heading");
        assertTrue(german.contains("03.02.2026"), "a date in the German picture");
        assertTrue(english.contains("2026-02-03"), "a date in the English picture");
        assertTrue(german.contains("2.915,50"), "an amount in the German picture");
        assertTrue(english.contains("2,915.50"), "an amount in the English picture");
    }

    @Test
    void defaultsToGermanOnA4() {
        SemanticDocument document = Corpus.example("standard-invoice");

        assertArrayEquals(new PdfRenderer().render(document),
                new PdfRenderer().render(document,
                        RenderOptions.in(RenderLanguage.GERMAN).on(PageSize.A4)),
                "the default is German on A4");
    }

    @Test
    void refusesADocumentOfAnEditionItDoesNotKnow() {
        PdfRenderer renderer = new PdfRenderer(Registry.en16931());
        SemanticDocument document = SemanticDocument.builder()
                .semanticModel("EN16931-1:2099")
                .put("/BT-1", "RE-1")
                .build();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> renderer.render(document));

        assertTrue(refused.getMessage().contains("EN16931-1:2099"),
                "the message names the edition the document asked for");
    }

    @Test
    void refusesNothingInsteadOfADocument() {
        assertThrows(NullPointerException.class, () -> new PdfRenderer().render(null));
        assertThrows(NullPointerException.class,
                () -> new PdfRenderer().render(Corpus.example("minimal"), null));
        assertThrows(NullPointerException.class, () -> new PdfRenderer(null));
    }

    @Test
    void namesTheRegistryItGoesBy() {
        Registry registry = Registry.en16931();

        assertEquals(registry, new PdfRenderer(registry).registry(),
                "the renderer hands back the registry it was given");
    }

    /**
     * A registry without the XRechnung extension still renders a document that uses it.
     * The renderer is not a validator: it knows no semantic data type for an extension term
     * it was not told about, so it prints the value as text under its path, and nothing is
     * lost.
     */
    @Test
    void rendersAnExtensionTermWithoutItsRegistry() {
        SemanticDocument document = Documents.withSubLines();

        String text = Pdf.flat(new PdfRenderer(Registry.en16931()).render(document));

        assertTrue(text.contains("Bracket"), "the value of an unknown term is still printed");
        assertTrue(text.contains("BG-DEX-01"),
                "and it is printed under the path that addresses it");
    }

    @Test
    void rendersADocumentWithoutAnInvoiceNumber() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-2", "2026-03-01")
                .build();

        String text = Pdf.flat(new PdfRenderer().render(document));

        assertTrue(text.contains("01.03.2026"), "what the document does carry is printed");
        assertNotEquals("", text, "and the rendering is not empty");
    }
}
