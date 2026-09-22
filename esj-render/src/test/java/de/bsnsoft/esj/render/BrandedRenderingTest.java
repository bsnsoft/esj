package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A branded rendering is the same rendering with a letterhead under it.
 *
 * <p>That is the claim of the template mode, and it is the claim this class checks. Every
 * instance of the conformance corpus is rendered on both example templates, in both
 * languages, and every value of the document has to be in the printed text — the same
 * assertion {@code PdfCoverageTest} makes about the generic layout, because a template
 * that lost a business term would be a template deciding what an invoice says.
 *
 * <p>Beside that: the letterhead is really in the file, the text stays inside the margins
 * the template asks for, a rendering is still the same bytes twice, and a template brings
 * its own faces where it names them.
 */
class BrandedRenderingTest {

    private static final Registry REGISTRY = XrImporter.defaultRegistry();

    /** The two example templates that bring a letterhead and no extension terms. */
    private static final List<String> LETTERHEADS = List.of("letterhead.json", "image.json");

    /** Every example template, the one that places extension terms included. */
    private static final List<String> TEMPLATES =
            List.of("letterhead.json", "image.json", "gross.json");

    static List<String> instances() {
        return Corpus.instances();
    }

    static List<String> examples() {
        return Corpus.EXAMPLES;
    }

    @ParameterizedTest
    @MethodSource("instances")
    void everyValueOfAnInstanceIsOnTheLetterheadToo(String instance) {
        SemanticDocument document = Corpus.instance(instance);
        for (String file : LETTERHEADS) {
            RenderTemplate template = Templates.example(file);
            for (RenderLanguage language : RenderLanguage.values()) {
                assertEverythingIsShown(document,
                        RenderOptions.in(language).with(template), instance + " on " + file);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("examples")
    void everyValueOfAnExampleIsOnTheLetterheadToo(String example) {
        SemanticDocument document = Corpus.example(example);
        for (String file : TEMPLATES) {
            RenderTemplate template = Templates.example(file);
            for (RenderLanguage language : RenderLanguage.values()) {
                assertEverythingIsShown(document,
                        RenderOptions.in(language).with(template),
                        "examples/" + example + " on " + file);
            }
        }
    }

    /**
     * The consumer invoice on every example template, including the one that places its
     * four extension terms. A template that gave a term a place and then lost it, or that
     * lost a core figure to make room for one, would be a template deciding what an
     * invoice says.
     */
    @Test
    void everyValueOfTheConsumerInvoiceIsShownOnEveryExampleTemplate() {
        SemanticDocument document = Documents.grossB2cInvoice();
        Registry registry = Templates.withB2c();
        for (String file : TEMPLATES) {
            RenderTemplate template = Templates.example(file);
            for (RenderLanguage language : RenderLanguage.values()) {
                assertEverythingIsShown(registry, document,
                        RenderOptions.in(language).with(template),
                        "the consumer invoice on " + file);
            }
        }
    }

    /**
     * The letterhead is imported as a form with its own resources: the name the sender
     * printed on the sheet comes back out of the rendering, on the first page and on the
     * pages after it, because the form carries the font it was set in.
     *
     * <p>It is asked for in the head of the sheet, where the letterhead writes, rather
     * than anywhere on the page. The name of the sender is the seller of the invoice as
     * well, and a test that looked for it in the whole text would pass on a page where
     * the rendering wrote it and the letterhead drew nothing.
     */
    @Test
    void theLetterheadIsOnEveryPageWithItsOwnText() {
        byte[] pdf = new PdfRenderer().render(Documents.withDetailedLines(80),
                RenderOptions.defaults().with(Templates.example("letterhead.json")));

        int pages = Pdf.pages(pdf);
        assertTrue(pages > 2, "eighty lines take more than two pages, not " + pages);
        for (int page = 1; page <= pages; page++) {
            assertTrue(head(pdf, page, 0).contains(Artwork.SENDER),
                    "page " + page + " carries the name the letterhead prints");
        }
        assertTrue(head(pdf, 1, Artwork.FOLLOWING_RULE_BELOW_TOP).contains(Artwork.SENDER),
                "on the first sheet the name stands lower, and larger");
        assertFalse(head(pdf, 2, Artwork.FOLLOWING_RULE_BELOW_TOP).contains(Artwork.SENDER),
                "and on the sheets after it, up in the corner");
    }

    /**
     * Returns the text of the head of a page, from a distance below the top edge down to
     * the accent rule of the first sheet — the strip the letterhead writes in, and above
     * anything a margin of these templates lets the rendering write.
     *
     * @param pdf      the rendering
     * @param page     the page, counted from one
     * @param belowTop where the strip begins, in points below the top edge
     * @return the text inside it
     */
    private static String head(byte[] pdf, int page, float belowTop) {
        return Pdf.textInArea(pdf, page, 0, belowTop, 600,
                Artwork.RULE_BELOW_TOP - belowTop);
    }

    /**
     * A template that names a letterhead for the pages after the first and none for the
     * first gets exactly that: the first sheet is bare and the following ones carry the
     * band. The member is optional in the schema, so a file that names only one of the
     * two is a file a template author writes, and dropping the letterhead from every page
     * without a word is what this asks against.
     */
    @Test
    void aLetterheadNamedOnlyForTheFollowingPagesIsOnThosePages() throws IOException {
        RenderTemplate template = Templates.of("{\"template\": \"esj-render-template/0.1\","
                + " \"letterhead\": {\"following\": {\"file\": \"letterhead.pdf\","
                + " \"page\": 2}}}");

        byte[] pdf = new PdfRenderer().render(Documents.withDetailedLines(40),
                RenderOptions.defaults().with(template));

        assertTrue(Pdf.pages(pdf) > 1, "forty detailed lines take more than one page");
        assertTrue(head(pdf, 2, 0).contains(Artwork.SENDER),
                "the pages after the first carry the letterhead the template names,"
                        + " with the name the sender printed on it");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(0, count(document.getPage(0)),
                    "the first page carries no letterhead, because the template names none");
            assertEquals(1, count(document.getPage(1)),
                    "and every page after it carries the form of the one it names");
        }
    }

    /**
     * An image letterhead is drawn as an image and a logo over it. Neither carries text,
     * so what is checked is that the objects are in the file and that the page is the
     * paper it was asked for.
     */
    @Test
    void anImageLetterheadAndALogoAreInTheFile() throws IOException {
        byte[] pdf = new PdfRenderer().render(Corpus.example("standard-invoice"),
                RenderOptions.defaults().with(Templates.example("image.json")));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(2, count(document.getPage(0)),
                    "the band and the mark are on the first page");
            assertEquals(1, count(document.getPage(1)),
                    "and the band alone on the second, because the mark is a first-page mark");
        }
    }

    /**
     * The margins of the template are the margins of the page. The text of the invoice
     * begins below the band of the letterhead and ends above its foot, and that is what
     * keeps a rendering from being printed over the printed matter.
     *
     * <p>The image template is the one to ask, because its letterhead draws no glyph: what
     * a text stripper finds on those pages is the text of the invoice and nothing else.
     */
    @Test
    void theTextStaysInsideTheMarginsTheTemplateAsksFor() {
        byte[] pdf = new PdfRenderer().render(Documents.withDetailedLines(80),
                RenderOptions.defaults().with(Templates.example("image.json")));

        // The first page begins 142 points down, the ones after it 80; both keep 82 below.
        assertTrue(Pdf.pages(pdf) > 3, "the document runs over pages");
        assertInside(pdf, 1, 142f, 82f);
        assertInside(pdf, 2, 80f, 82f);
        assertInside(pdf, 4, 80f, 82f);
    }

    /**
     * The other pair of numbers an example template asks for — 148 down on the first
     * page, 84 on the ones after it, 82 below, which is what {@code letterhead.json}
     * states. They are given to a template without artwork, because the letterhead of
     * that file draws glyphs of its own inside those margins by design and a text
     * stripper cannot tell them from the invoice.
     */
    @Test
    void theTextStaysInsideTheMarginsOfThePdfLetterheadToo() {
        RenderTemplate template = Templates.of("{\"template\": \"esj-render-template/0.1\","
                + " \"margins\": {\"first\": {\"top\": 148, \"bottom\": 82,"
                + " \"left\": 56, \"right\": 56},"
                + " \"following\": {\"top\": 84, \"bottom\": 82}}}");

        byte[] pdf = new PdfRenderer().render(Documents.withDetailedLines(80),
                RenderOptions.defaults().with(template));

        assertTrue(Pdf.pages(pdf) > 3, "the document runs over pages");
        assertInside(pdf, 1, 148f, 82f);
        assertInside(pdf, 2, 84f, 82f);
        assertInside(pdf, 4, 84f, 82f);
    }

    @Test
    void aBrandedRenderingIsTheSameBytesTwice() {
        SemanticDocument document = Corpus.example("standard-invoice");
        for (String file : LETTERHEADS) {
            RenderOptions options = RenderOptions.defaults().with(Templates.example(file));
            assertArrayEquals(new PdfRenderer().render(document, options),
                    new PdfRenderer().render(document, options),
                    file + " renders to the same bytes twice");
        }
    }

    /**
     * A template that reads its own template file again is the same template: nothing of
     * the machine, the moment or the path takes part in reading one.
     */
    @Test
    void readingATemplateTwiceRendersTheSameBytes() {
        SemanticDocument document = Corpus.example("minimal");

        assertArrayEquals(
                new PdfRenderer().render(document,
                        RenderOptions.defaults().with(Templates.example("letterhead.json"))),
                new PdfRenderer().render(document,
                        RenderOptions.defaults().with(Templates.example("letterhead.json"))),
                "two readings of one template give one rendering");
    }

    /**
     * A template brings its own faces where it names them, and those are the faces the
     * file carries. The two the template brings here are the vendored files under a name
     * of the template's own, so the rendering has to come out byte for byte the rendering
     * without a template: what is checked is that the bytes of the template reached the
     * document, not that another font looks different.
     */
    @Test
    void aTemplateCanBringItsOwnFaces() throws IOException {
        RenderTemplate template = RenderTemplate.of(
                ("{\"template\": \"esj-render-template/0.1\", \"fonts\": "
                        + "{\"regular\": \"LiberationSans-Regular.ttf\","
                        + " \"bold\": \"LiberationSans-Bold.ttf\"}}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                reference -> Corpus.bytes(
                        "/de/bsnsoft/esj/render/fonts/" + reference));
        SemanticDocument document = Corpus.example("standard-invoice");

        assertArrayEquals(new PdfRenderer().render(document),
                new PdfRenderer().render(document, RenderOptions.defaults().with(template)),
                "the faces a template brings are the faces that are embedded");

        try (PDDocument pdf = Loader.loadPDF(new PdfRenderer().render(document,
                RenderOptions.defaults().with(template)))) {
            List<String> faces = new ArrayList<>();
            PDResources resources = pdf.getPage(0).getResources();
            for (var name : resources.getFontNames()) {
                PDFont font = resources.getFont(name);
                faces.add(font.getName());
            }
            assertTrue(faces.stream().anyMatch(name ->
                            name.toLowerCase(Locale.ROOT).contains("liberationsans")),
                    "and they are embedded, subset and named: " + faces);
        }
    }

    /** The paper is still the caller's, and a letterhead of another size is scaled onto it. */
    @Test
    void aLetterheadOfAnotherPaperIsScaledOntoThePage() {
        byte[] pdf = new PdfRenderer().render(Corpus.example("standard-invoice"),
                RenderOptions.defaults().on(PageSize.LETTER)
                        .with(Templates.example("letterhead.json")));

        float[] size = Pdf.pageSize(pdf, 1);
        assertEquals(PageSize.LETTER.width(), size[0], 0.01f, "the page is the paper asked for");
        assertEquals(PageSize.LETTER.height(), size[1], 0.01f, "in both directions");
        assertTrue(Pdf.flat(pdf).contains(Artwork.SENDER),
                "and the A4 letterhead is on it");
    }

    private static int count(PDPage page) {
        int objects = 0;
        for (var name : page.getResources().getXObjectNames()) {
            objects++;
        }
        return objects;
    }

    /**
     * Asserts that no glyph of a page is drawn above the top margin or below the bottom
     * one. The letterhead itself is a form and an image and draws no glyph, so what a text
     * stripper reports the position of is the text of the invoice.
     */
    private static void assertInside(byte[] pdf, int page, float top, float bottom) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            float height = document.getPage(page - 1).getMediaBox().getHeight();
            Positions positions = new Positions();
            positions.setStartPage(page);
            positions.setEndPage(page);
            positions.getText(document);
            assertTrue(positions.highest() >= top - 1f,
                    "page " + page + ": the text begins " + positions.highest()
                            + " points down, and the margin is " + top);
            assertTrue(positions.lowest() <= height - bottom + Margins.FOOTER_INSET + 1f,
                    "page " + page + ": the text ends " + (height - positions.lowest())
                            + " points above the bottom edge, and the margin is " + bottom);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertEverythingIsShown(SemanticDocument document, RenderOptions options,
                                         String what) {
        assertEverythingIsShown(REGISTRY, document, options, what);
    }

    private void assertEverythingIsShown(Registry registry, SemanticDocument document,
                                         RenderOptions options, String what) {
        String text = Pdf.text(new PdfRenderer(registry).render(document, options));
        List<String> missing = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            SemanticPath path = entry.getKey();
            SemanticValue value = entry.getValue();
            SemanticType type = registry.datatype(path.term()).orElse(SemanticType.TEXT);
            if (type != SemanticType.BINARY_OBJECT && !Pdf.shows(text,
                    Formats.value(type, value.content(), options.language()))) {
                missing.add(path + " = " + value.content());
            }
        }
        assertEquals(List.of(), missing,
                what + " in " + options.language() + ": every value is in the rendering");
    }

    /** A text stripper that remembers how far down the page its glyphs were drawn. */
    private static final class Positions extends org.apache.pdfbox.text.PDFTextStripper {

        private float highest = Float.MAX_VALUE;
        private float lowest;

        private Positions() throws IOException {
            super();
        }

        float highest() {
            return highest;
        }

        float lowest() {
            return lowest;
        }

        @Override
        protected void writeString(String text,
                                   List<org.apache.pdfbox.text.TextPosition> positions) {
            for (org.apache.pdfbox.text.TextPosition position : positions) {
                if (position.getUnicode().isBlank()) {
                    continue;
                }
                highest = Math.min(highest, position.getY() - position.getHeight());
                lowest = Math.max(lowest, position.getY());
            }
        }
    }
}
