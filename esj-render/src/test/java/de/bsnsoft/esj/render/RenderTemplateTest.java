package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
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

    // ---------------------------------------------------------------- the files it reads

    /**
     * A plain name beside the template that is a symbolic link out of its directory is
     * refused like a name that says {@code ../}: what is checked is the file the name
     * reaches, not how the name is spelt.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aLinkOutOfTheDirectoryIsNotFollowed(@TempDir Path root) throws Exception {
        Path home = Files.createDirectory(root.resolve("templates"));
        Path outside = Files.write(root.resolve("elsewhere.pdf"), Corpus.bytes(
                "/examples/templates/letterhead.pdf"));
        Files.createSymbolicLink(home.resolve("letterhead.pdf"), outside);
        Path template = letterheadTemplate(home, "letterhead.pdf");

        TemplateException refused =
                assertThrows(TemplateException.class, () -> RenderTemplate.read(template));
        assertTrue(refused.getMessage().contains("'letterhead.pdf'")
                        && refused.getMessage().contains("link"),
                "the refusal names the file and says why: " + refused.getMessage());
    }

    /** A link that stays inside the directory is a file beside the template like another. */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aLinkInsideTheDirectoryIsFollowed(@TempDir Path home) throws Exception {
        Path art = Files.createDirectory(home.resolve("art"));
        Path real = Files.write(art.resolve("paper.pdf"), Corpus.bytes(
                "/examples/templates/letterhead.pdf"));
        Files.createSymbolicLink(home.resolve("letterhead.pdf"), real);

        assertTrue(RenderTemplate.read(letterheadTemplate(home, "letterhead.pdf"))
                .letterheadFirst() != null, "the letterhead was read through the link");
    }

    /** A device the name reaches is not read, however it is reached. */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void aDeviceIsNotRead(@TempDir Path home) throws Exception {
        Files.createSymbolicLink(home.resolve("letterhead.png"), Path.of("/dev/zero"));

        assertThrows(TemplateException.class,
                () -> RenderTemplate.read(letterheadTemplate(home, "letterhead.png")),
                "a link to /dev/zero leaves the directory and is not followed");
        TemplateException itself = assertThrows(TemplateException.class,
                () -> RenderTemplate.read(Path.of("/dev/zero")),
                "and a template file that never ends is refused at the bound");
        assertTrue(itself.getMessage().contains("larger than"), itself.getMessage());
    }

    /** A directory is not a file, and is refused with a sentence that says so. */
    @Test
    void aDirectoryIsNotAFile(@TempDir Path home) throws Exception {
        Files.createDirectory(home.resolve("letterhead.pdf"));

        TemplateException refused = assertThrows(TemplateException.class,
                () -> RenderTemplate.read(letterheadTemplate(home, "letterhead.pdf")));
        assertTrue(refused.getMessage().contains("letterhead.pdf")
                        && refused.getMessage().contains("not a regular file"),
                refused.getMessage());
    }

    /**
     * A file past the bound is refused by its size, before it is read. The file of the
     * case is sparse: it has the size and none of the content, so a reader that read it
     * anyway would be found out by the time it took and not only by the heap.
     */
    @Test
    void aFilePastTheBoundIsRefusedBeforeItIsRead(@TempDir Path home) throws Exception {
        try (RandomAccessFile file =
                     new RandomAccessFile(home.resolve("letterhead.pdf").toFile(), "rw")) {
            file.setLength(RenderTemplate.MAX_REFERENCE_BYTES + 1L);
        }

        TemplateException refused = assertThrows(TemplateException.class,
                () -> RenderTemplate.read(letterheadTemplate(home, "letterhead.pdf")));
        assertTrue(refused.getMessage().contains("letterhead.pdf")
                        && refused.getMessage().contains("larger than"),
                refused.getMessage());
    }

    /**
     * An image is measured by the size its header states and refused before anything
     * decodes it: a small file can declare a picture whose pixels are gigabytes. Both
     * places an image may stand are measured, and so are both formats, whether the file
     * comes from a directory or from a caller's own {@link RenderTemplate.Files}.
     */
    @Test
    void anImageLargerThanTheBoundIsRefusedBeforeItIsDecoded() {
        for (byte[] image : List.of(png(20_000, 20_000), jpeg(20_000, 20_000))) {
            TemplateException letterhead = assertThrows(TemplateException.class,
                    () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                            + "\"letterhead\": {\"first\": {\"file\": \"vast\"}}}",
                            "vast", image));
            assertTrue(letterhead.getMessage().contains("vast")
                            && letterhead.getMessage().contains("20000 × 20000 pixels"),
                    letterhead.getMessage());
            TemplateException logo = assertThrows(TemplateException.class,
                    () -> Templates.of("{\"template\": \"esj-render-template/0.1\","
                            + "\"logo\": {\"file\": \"vast\", \"width\": 50,"
                            + " \"height\": 20}}", "vast", image));
            assertTrue(logo.getMessage().contains("pixels"), logo.getMessage());
        }
    }

    /** The bound is the size of a page at 600 dots per inch, and that size is allowed. */
    @Test
    void anImageAtTheBoundIsAllowed() {
        RenderTemplate.measured(png(6000, 6000), "at.png");
        RenderTemplate.measured(jpeg(6000, 6000), "at.jpg");
        assertThrows(TemplateException.class,
                () -> RenderTemplate.measured(png(6000, 6001), "past.png"));
        assertThrows(TemplateException.class,
                () -> RenderTemplate.measured(jpeg(6001, 6000), "past.jpg"));
    }

    /** Real images are measured as they are, the encoder's own JPEG among them. */
    @Test
    void realImagesAreMeasuredByTheirHeaders() throws Exception {
        RenderTemplate.measured(Artwork.mark(), "mark.png");
        BufferedImage picture = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(picture, "jpg", jpeg), "the platform writes a JPEG");
        RenderTemplate.measured(jpeg.toByteArray(), "logo.jpg");
        assertThrows(TemplateException.class,
                () -> RenderTemplate.measured(new byte[] {(byte) 0xff, (byte) 0xd8,
                    (byte) 0xff, (byte) 0xd9}, "empty.jpg"),
                "a JPEG that ends before it states a size is refused");
    }

    /** Writes a template into a directory that names one file as its letterhead. */
    private static Path letterheadTemplate(Path home, String reference) throws IOException {
        Path template = home.resolve("template.json");
        Files.writeString(template, "{\"template\": \"esj-render-template/0.1\","
                + "\"letterhead\": {\"first\": {\"file\": \"" + reference + "\"}}}",
                StandardCharsets.UTF_8);
        return template;
    }

    /** Returns the first bytes of a PNG that states a size: its signature and its header. */
    private static byte[] png(int width, int height) {
        ByteBuffer png = ByteBuffer.allocate(33);
        png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        png.putInt(13).put(new byte[] {'I', 'H', 'D', 'R'}).putInt(width).putInt(height);
        png.put(new byte[] {8, 6, 0, 0, 0}).putInt(0);
        return png.array();
    }

    /**
     * Returns the first bytes of a JPEG that states a size: the start of the image, an
     * application segment the frame header has to be found behind, and the frame header.
     */
    private static byte[] jpeg(int width, int height) {
        ByteBuffer jpeg = ByteBuffer.allocate(2 + 18 + 19);
        jpeg.put(new byte[] {(byte) 0xff, (byte) 0xd8});
        jpeg.put(new byte[] {(byte) 0xff, (byte) 0xe0}).putShort((short) 16)
                .put(new byte[] {'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0});
        jpeg.put(new byte[] {(byte) 0xff, (byte) 0xc0}).putShort((short) 17).put((byte) 8)
                .putShort((short) height).putShort((short) width).put((byte) 3)
                .put(new byte[] {1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1});
        return jpeg.array();
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

    /**
     * A template that says nothing about the layout names none: the choice is left to the
     * caller and then to {@link RenderOptions#DEFAULT_LAYOUT}, and the letter it is drawn as
     * is the one of the defaults.
     */
    @Test
    void aTemplateThatSaysNothingAboutTheLayoutLeavesItToTheDefault() {
        RenderTemplate template = Templates.of(MINIMAL);

        assertEquals(Optional.empty(), template.layout(), "the template names no layout");
        assertEquals(LetterOptions.defaults(), template.letter(),
                "and the letter it is drawn as is the default one");
    }

    /** The example letterhead was set for the generic layout, and says so. */
    @Test
    void theExampleLetterheadNamesTheGenericLayout() {
        assertEquals(Optional.of(Layout.GENERIC), Templates.example("letterhead.json").layout(),
                "the layout its margins were set for");
    }

    @Test
    void aTemplateChoosesTheLayoutAndTheLetterItWants() {
        RenderTemplate template = Templates.example("letter.json");

        assertEquals(Optional.of(Layout.LETTER), template.layout(),
                "the layout of the example letter");
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
