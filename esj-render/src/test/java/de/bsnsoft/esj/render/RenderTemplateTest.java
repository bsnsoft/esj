package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a template file may say, and what it may not.
 *
 * <p>A template is configuration of the party that renders rather than a document from a
 * stranger, and this is what that means for its refusals: every one of them names the
 * member it is about, so that whoever wrote the file can find it. The three example
 * templates of the repository are read here as a caller reads them — from their files,
 * with the files they name beside them — so that a broken example is a failing build.
 */
class RenderTemplateTest {

    /** The smallest template that is one. */
    private static final String MINIMAL = "{\"template\": \"esj-render-template/0.1\"}";

    @Test
    void theExamplesOfTheRepositoryAreTemplates() {
        assertEquals("Letterhead (PDF)", Templates.example("letterhead.json").name());
        assertEquals("Letterhead (image) with a mark", Templates.example("image.json").name());
        assertEquals("Letterhead (PDF) with the displayed gross figures",
                Templates.example("gross.json").name());
    }

    @Test
    void aTemplateWithoutAnythingInItIsStillATemplate() {
        RenderTemplate template = Templates.of(MINIMAL);

        assertEquals("unnamed", template.name(), "the name a file without one gets");
        assertEquals(Palette.defaults(), template.palette(), "the greys of the generic layout");
        assertEquals(Margins.defaults(), template.marginsFirst(), "and its margins");
        assertEquals(List.of(), template.placements(), "and no extension term placed");
    }

    @Test
    void theFormatMarkerDecidesWhetherThisVersionReadsTheFile() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/9.9\"}"));

        assertTrue(refused.getMessage().contains("esj-render-template/0.1"),
                "the refusal says which marker this version reads: " + refused.getMessage());
    }

    @Test
    void theFileIsAJsonObjectAndOneOfThem() {
        assertThrows(TemplateException.class, () -> Templates.of("[]"));
        assertThrows(TemplateException.class, () -> Templates.of(MINIMAL + " {}"));
    }

    @Test
    void aColourIsSixHexadecimalDigitsBehindANumberSign() {
        RenderTemplate template = Templates.of("{\"template\": \"esj-render-template/0.1\","
                + "\"colors\": {\"heading\": \"#123a63\"}}");

        assertEquals(new Ink(0x12 / 255f, 0x3a / 255f, 0x63 / 255f),
                template.palette().heading(), "the colour the file names");
        assertEquals(Palette.defaults().text(), template.palette().text(),
                "and the grey of the layout for the five it does not");

        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + "\"colors\": {\"heading\": \"navy\"}}"));
        assertTrue(refused.getMessage().contains("heading"),
                "the refusal names the colour: " + refused.getMessage());
    }

    @Test
    void theTwoPageKindsKeepTheSameLeftAndRightMargin() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + "\"margins\": {\"first\": {\"left\": 56},"
                        + " \"following\": {\"left\": 72}}}"));

        assertTrue(refused.getMessage().contains("columns"),
                "the refusal says why: " + refused.getMessage());
    }

    /**
     * The page footer — the invoice number and the page number — sits inside the bottom
     * margin. A template that asks for a narrower foot than the footer needs is told the
     * number rather than losing the footer off the paper on every page.
     */
    @Test
    void aBottomMarginTooNarrowForThePageFooterIsRefused() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + " \"margins\": {\"first\": {\"bottom\": 8}}}"));

        assertTrue(refused.getMessage().contains("bottom is 8"), refused.getMessage());
        assertTrue(refused.getMessage().contains("25"), refused.getMessage());
    }

    /** The pages after the first are held to the same rule, and named separately. */
    @Test
    void theSameGoesForThePagesAfterTheFirst() {
        assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + " \"margins\": {\"following\": {\"bottom\": 0}}}"));
    }

    @Test
    void marginsThatAreNotStatedAreTheOnesOfTheGenericLayout() {
        RenderTemplate template = Templates.of("{\"template\": \"esj-render-template/0.1\","
                + "\"margins\": {\"first\": {\"top\": 148}}}");

        assertEquals(148f, template.marginsFirst().top(), "what the file says");
        assertEquals(Margins.defaults().bottom(), template.marginsFirst().bottom(),
                "and the default for what it does not");
        assertEquals(148f, template.marginsFollowing().top(),
                "a template that names only the first page gives the same to the rest");
    }

    @Test
    void aTemplateBringsBothWeightsOfAFaceOrNeither() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + "\"fonts\": {\"regular\": \"mark.png\"}}"));

        assertTrue(refused.getMessage().contains("both"),
                "the refusal says so: " + refused.getMessage());
    }

    @Test
    void aFaceThatIsNotATrueTypeFontIsRefusedRatherThanReplaced() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + "\"fonts\": {\"regular\": \"mark.png\","
                        + " \"bold\": \"mark.png\"}}"));

        assertTrue(refused.getMessage().contains("TrueType"),
                "the refusal says what a face has to be: " + refused.getMessage());
    }

    /**
     * A file that says it is a TrueType font and is not one gets as far as the renderer,
     * which is where a font is parsed. It is still the template that is wrong, and the
     * refusal says so.
     */
    @Test
    void aFaceThatSaysItIsOneAndIsNotIsRefusedWhenItIsEmbedded() {
        byte[] header = new byte[12];
        header[1] = 1;
        RenderTemplate template = RenderTemplate.of(
                ("{\"template\": \"esj-render-template/0.1\", \"fonts\":"
                        + " {\"regular\": \"face.ttf\", \"bold\": \"face.ttf\"}}")
                        .getBytes(StandardCharsets.UTF_8),
                reference -> header);

        TemplateException refused = assertThrows(TemplateException.class,
                () -> new PdfRenderer().render(Corpus.example("minimal"),
                        RenderOptions.defaults().with(template)));
        assertTrue(refused.getMessage().contains("fonts:"),
                "the refusal is about the template: " + refused.getMessage());
    }

    @Test
    void aLetterheadIsAPdfOrAnImageAndNothingElse() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + "\"letterhead\": {\"first\": {\"file\": \"letterhead.json\"}}}"));

        assertTrue(refused.getMessage().contains("PNG"),
                "the refusal says which files are read: " + refused.getMessage());
    }

    @Test
    void aPageOfALetterheadThatIsNotInItIsRefusedWhenItIsDrawn() {
        RenderTemplate template = Templates.of("{\"template\": \"esj-render-template/0.1\","
                + "\"letterhead\": {\"first\": {\"file\": \"letterhead.pdf\", \"page\": 7}}}");

        TemplateException refused = assertThrows(TemplateException.class,
                () -> new PdfRenderer().render(Corpus.example("minimal"),
                        RenderOptions.defaults().with(template)));
        assertTrue(refused.getMessage().contains("2 pages"),
                "the refusal says how many pages there are: " + refused.getMessage());
    }

    @Test
    void aFileTheTemplateNamesAndThatIsNotThereIsSaidSo() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + "\"letterhead\": {\"first\": {\"file\": \"nowhere.pdf\"}}}"));

        assertTrue(refused.getMessage().contains("nowhere.pdf"),
                "the refusal names the file: " + refused.getMessage());
    }

    /**
     * A reference is a file beside the template, and the reader does not follow one that
     * leaves the directory. A template is the caller's own file, so this is not a defence
     * against the caller; it is what keeps a template that was copied from somewhere from
     * reading a file the copier never looked at.
     */
    @Test
    void aReferenceDoesNotLeaveTheDirectoryOfTheTemplate(@TempDir Path directory)
            throws Exception {
        Path template = directory.resolve("template.json");
        Files.writeString(template, "{\"template\": \"esj-render-template/0.1\","
                + "\"letterhead\": {\"first\": {\"file\": \"../letterhead.pdf\"}}}",
                StandardCharsets.UTF_8);

        TemplateException refused =
                assertThrows(TemplateException.class, () -> RenderTemplate.read(template));
        assertTrue(refused.getMessage().contains("leaves the directory"),
                "the refusal says so: " + refused.getMessage());
    }

    @Test
    void anAbsoluteReferenceIsRefusedTheSameWay(@TempDir Path directory) throws Exception {
        Path template = directory.resolve("template.json");
        Files.writeString(template, "{\"template\": \"esj-render-template/0.1\","
                + "\"letterhead\": {\"first\": {\"file\": \"/etc/hosts\"}}}",
                StandardCharsets.UTF_8);

        assertThrows(TemplateException.class, () -> RenderTemplate.read(template));
    }

    @Test
    void onlyATermOfAnExtensionGetsAPlace() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of(placing("BT-131", "line.amount")));

        assertTrue(refused.getMessage().contains("BT-131"),
                "the refusal names the term: " + refused.getMessage());
    }

    @Test
    void aTermIsPlacedOnce() {
        assertThrows(TemplateException.class, () -> Templates.of(
                "{\"template\": \"esj-render-template/0.1\", \"extensionTerms\": ["
                        + "{\"term\": \"BT-B2C-001\", \"position\": \"line.amount\"},"
                        + "{\"term\": \"BT-B2C-001\", \"position\": \"totals\"}]}"));
    }

    @Test
    void aPositionIsOneOfTheFour() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of(placing("BT-B2C-001", "line.middle")));

        assertTrue(refused.getMessage().contains("line.unitPrice"),
                "the refusal lists the positions: " + refused.getMessage());
    }

    @Test
    void aDataTypeIsOneOfTheTenOfTheStandard() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + "\"extensionTerms\": [{\"term\": \"BT-B2C-001\","
                        + " \"position\": \"totals\", \"type\": \"Money\"}]}"));

        assertTrue(refused.getMessage().contains("UnitPriceAmount"),
                "the refusal names the types: " + refused.getMessage());
    }

    @Test
    void thePlacementsKeepTheOrderTheTemplateWroteThemIn() {
        RenderTemplate template = Templates.example("gross.json");

        assertEquals(List.of("BT-B2C-001", "BT-B2C-003", "BT-B2C-002", "BT-B2C-010"),
                template.placements().stream().map(Placement::term).toList(),
                "the four places of the gross template, in the order of the file");
    }

    @Test
    void aPlacementCarriesTheLabelOfBothLanguages() {
        Placement price = Templates.example("gross.json").placements().get(0);

        assertEquals("Gross unit price", price.label(RenderLanguage.ENGLISH));
        assertEquals("Einzelpreis brutto", price.label(RenderLanguage.GERMAN));
    }

    @Test
    void aTemplateSaysNothingAboutTheLayoutAndMeansTheGenericOne() {
        RenderTemplate template = Templates.example("letterhead.json");

        assertEquals(Layout.GENERIC, template.layout(), "the layout it means");
        assertEquals(LetterOptions.defaults(), template.letter(),
                "and the letter it would be if a caller asked for one");
    }

    @Test
    void aTemplateChoosesTheLayoutAndTheLetterItWants() {
        RenderTemplate template = Templates.example("letter.json");

        assertEquals(Layout.LETTER, template.layout(), "the layout of the example letter");
        assertEquals(new LetterOptions(LetterOptions.Window.DIN_5008_B, true, true,
                        LetterOptions.SellerDetails.FOOTER,
                        LetterOptions.Information.LINE, 0f, true),
                template.letter(),
                "with its marks, its reference line, its foot and its payment code");
    }

    @Test
    void theHeadDataStandsInOneOfTwoPlaces() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + " \"letter\": {\"information\": \"column\"}}"));

        assertTrue(refused.getMessage().contains("line or block"),
                "the refusal names the two: " + refused.getMessage());
    }

    /**
     * The printed head of a letterhead is a distance from the top edge of the paper, and
     * one the block of information has to be able to stand under. A number that is not one
     * is refused by the name of the member rather than drawn.
     */
    @Test
    void aPrintedHeadIsADistanceTheBlockCanStandUnder() {
        for (String written : List.of("-1", "500")) {
            TemplateException refused = assertThrows(TemplateException.class,
                    () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                            + " \"letter\": {\"information\": \"block\","
                            + " \"printedHead\": " + written + "}}"),
                    "a printed head of " + written + " points is refused");
            assertTrue(refused.getMessage().contains("printedHead"),
                    "the refusal names the member: " + refused.getMessage());
        }
        assertEquals(170f, Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + " \"letter\": {\"printedHead\": 170}}").letter().printedHead(),
                "and a distance the block can stand under is kept");
    }

    @Test
    void aLayoutIsOneOfTheTwo() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + " \"layout\": \"invoice\"}"));

        assertTrue(refused.getMessage().contains("generic or letter"),
                "the refusal names the two: " + refused.getMessage());
    }

    @Test
    void anAddressWindowIsOneOfTheThreeForms() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + " \"letter\": {\"addressWindow\": \"din5008-c\"}}"));

        assertTrue(refused.getMessage().contains("din5008-b"),
                "the refusal lists the forms: " + refused.getMessage());
    }

    @Test
    void theSellerDetailsGoToOneOfTwoPlaces() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + " \"letter\": {\"sellerDetails\": \"nowhere\"}}"));

        assertTrue(refused.getMessage().contains("footer or details"),
                "the refusal names the two: " + refused.getMessage());
    }

    @Test
    void aMarkIsTrueOrFalse() {
        TemplateException refused = assertThrows(TemplateException.class,
                () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                        + " \"letter\": {\"foldMarks\": \"yes\"}}"));

        assertTrue(refused.getMessage().contains("true or false"),
                "the refusal says what a mark is: " + refused.getMessage());
    }

    private static String placing(String term, String position) {
        return "{\"template\": \"esj-render-template/0.1\", \"extensionTerms\": ["
                + "{\"term\": \"" + term + "\", \"position\": \"" + position + "\"}]}";
    }
}
