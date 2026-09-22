package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.typed.En16931;
import de.bsnsoft.esj.xr.ExportNote;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the renderer promises its caller.
 */
class HtmlRendererTest {

    @Test
    void aRenderingIsOneSelfContainedHtmlDocument() {
        String html = new HtmlRenderer().render(Corpus.example("standard-invoice"));

        assertTrue(html.startsWith("<!DOCTYPE HTML>"), "a rendering is an HTML document");
        assertTrue(html.contains("</html>"), "and it is complete");
        assertFalse(html.contains("<link "), "it fetches no style sheet");
        assertFalse(html.contains("<script src"), "and no script");
        assertFalse(html.contains("<img "), "and no image");
        assertEquals(2, Html.count(html, "<script"),
                "the two scripts the stylesheet inlines are the whole of its active content");
        assertTrue(html.contains("box-sizing"), "the style sheet of the visualization is inlined");
    }

    @Test
    void theLanguageDecidesTheLabelsAndThePictures() {
        SemanticDocument document = Corpus.example("standard-invoice");

        String german = new HtmlRenderer().render(document,
                RenderOptions.in(RenderLanguage.GERMAN));
        String english = new HtmlRenderer().render(document,
                RenderOptions.in(RenderLanguage.ENGLISH));

        assertTrue(german.contains("lang=\"de\""), "the document element names the language");
        assertTrue(english.contains("lang=\"en\""), "in either language");
        assertNotEquals(german, english, "the two renderings are not the same document");
        assertTrue(Html.text(german).contains("Rechnungsnummer"),
                "the German labels come from the German localization");
        assertTrue(Html.text(english).contains("Invoice number"),
                "and the English ones from the English localization");
    }

    @Test
    void germanIsTheDefault() {
        SemanticDocument document = Corpus.example("standard-invoice");

        assertEquals(new HtmlRenderer().render(document, RenderOptions.in(RenderLanguage.GERMAN)),
                new HtmlRenderer().render(document),
                "a rendering without options is a German one");
        assertEquals(RenderLanguage.GERMAN, RenderOptions.defaults().language(),
                "and the options say so");
    }

    @Test
    void whatTheTypedViewReadsIsWhatTheRenderingShows() {
        SemanticDocument document = Corpus.example("standard-invoice");

        String text = Html.text(new HtmlRenderer().render(document));

        assertTrue(text.contains(En16931.view(document).seller().name()),
                "the seller of the read view is the seller of the rendering");
        assertTrue(text.contains(En16931.view(document).invoiceNumber().value()),
                "and so is the invoice number");
    }

    @Test
    void anExtensionSubtreeIsNamedInTheReportBecauseNoRenderingCanShowIt() {
        RenderResult result = new HtmlRenderer()
                .renderWithReport(Corpus.example("extended"), RenderOptions.defaults());

        assertFalse(result.report().isEmpty(), "the extension subtree did not reach the rendering");
        assertEquals(result.report().notes(ExportNote.Kind.EXTENSIONS_DROPPED),
                result.report().notes(),
                "and nothing else of that example stayed behind");
    }

    /**
     * The baseline rendering is net and shows no extension. {@code examples/b2c-gross.esj.json}
     * carries the four terms of {@code model/b2c/0.1.json}; the XR representation the
     * stylesheet reads has no element for one of them, and the report names every path it
     * left behind rather than losing it quietly.
     */
    @Test
    void theBaselineRenderingShowsNoTermOfAnExtensionRegistry() {
        RenderResult result = new HtmlRenderer()
                .renderWithReport(Corpus.example("b2c-gross"), RenderOptions.defaults());

        assertEquals(List.of("/BT-B2C-010", "/BG-25/0/BT-B2C-001", "/BG-25/0/BT-B2C-002",
                        "/BG-25/0/BT-B2C-003", "/BG-25/1/BT-B2C-001", "/BG-25/1/BT-B2C-002",
                        "/BG-25/1/BT-B2C-003", "/BG-25/2/BT-B2C-001", "/BG-25/2/BT-B2C-002",
                        "/BG-25/2/BT-B2C-003"),
                result.report().notes(ExportNote.Kind.NO_ELEMENT).stream()
                        .map(ExportNote::location).toList());
        assertEquals(result.report().notes(ExportNote.Kind.NO_ELEMENT), result.report().notes(),
                "and nothing else of that example stayed behind");
        assertFalse(result.html().contains("99.99"),
                "no displayed gross figure of the extension is on the page");
    }

    @Test
    void aDocumentOfEveryExampleReachesTheStylesheetWhole() {
        for (String example : Corpus.EXAMPLES) {
            RenderResult result = new HtmlRenderer()
                    .renderWithReport(Corpus.example(example), RenderOptions.defaults());
            List<ExportNote> unexpected = result.report().notes().stream()
                    .filter(note -> note.kind() != ExportNote.Kind.EXTENSIONS_DROPPED)
                    .toList();
            assertEquals(List.of(), unexpected,
                    example + " reached the stylesheet apart from its extension subtree");
        }
    }

    @Test
    void aDocumentOfAnEditionTheRegistryDoesNotDescribeIsRefused() {
        SemanticDocument document = Corpus.example("minimal").toBuilder()
                .semanticModel("EN16931-1:2999")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> new HtmlRenderer().render(document),
                "a semantic path is an address relative to an edition");
    }

    @Test
    void aRegistryOfTheCallersChoosingDecidesWhatCanBeWritten() {
        SemanticDocument document = Corpus.example("standard-invoice");

        String html = new HtmlRenderer(Registry.en16931()).render(document);

        assertTrue(Html.text(html).contains(En16931.view(document).seller().name()),
                "a renderer of the core model renders the core terms");
    }

    @Test
    void theArgumentsAreChecked() {
        HtmlRenderer renderer = new HtmlRenderer();
        SemanticDocument document = Corpus.example("minimal");

        assertThrows(NullPointerException.class, () -> renderer.render(null));
        assertThrows(NullPointerException.class, () -> renderer.render(document, null));
        assertThrows(NullPointerException.class, () -> new HtmlRenderer((Registry) null));
        assertThrows(NullPointerException.class, () -> RenderOptions.in(null));
    }
}
