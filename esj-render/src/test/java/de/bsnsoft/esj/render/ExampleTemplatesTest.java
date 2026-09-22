package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The example templates of the repository, and the artwork they stand on.
 *
 * <p>There is no third-party picture in this project. The letterhead, the image
 * letterhead and the mark under {@code examples/templates/} are drawn by {@link Artwork}
 * out of a name, a hairline and three squares, in the font this module vendors, and this
 * is what says that the files checked in are the files that code draws — a letterhead
 * that was replaced by hand, or a change to the drawing that nobody carried into the
 * examples, fails here rather than in somebody's copy of the template.
 *
 * <p>The PDF is compared byte for byte, as the golden renderings of this module are. The
 * images are compared as pixels: a PNG encoder is part of the platform rather than of this
 * project, and what this project draws is the picture and not the encoding of it.
 */
class ExampleTemplatesTest {

    /** The page whose template reference the members of a template are read from. */
    private static final String TEMPLATES_PAGE = "/docs/templates.md";

    /** The page that carries the letter layout, and the snippet of its members. */
    private static final String LETTER_PAGE = "/docs/letter-layout.md";

    /** Every file the examples directory carries, and nothing else. */
    private static final List<String> FILES = List.of(
            "README.md", "band-first.png", "band-following.png", "gross.json", "image.json",
            "letter.json", "letterhead.json", "letterhead.pdf", "mark.png");

    @Test
    void theLetterheadIsTheOneThisProjectDraws() {
        assertArrayEquals(Artwork.letterhead(), example("letterhead.pdf"),
                "examples/templates/letterhead.pdf is what Artwork.letterhead() draws");
    }

    @Test
    void theBandsAndTheMarkAreTheOnesThisProjectDraws() {
        assertSamePixels(Artwork.band(true), example("band-first.png"), "band-first.png");
        assertSamePixels(Artwork.band(false), example("band-following.png"),
                "band-following.png");
        assertSamePixels(Artwork.mark(), example("mark.png"), "mark.png");
    }

    @Test
    void theDirectoryCarriesExactlyTheseFiles() {
        List<String> found = new java.util.ArrayList<>();
        try (var files = java.nio.file.Files.list(Templates.directory())) {
            files.forEach(file -> found.add(file.getFileName().toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        found.sort(String::compareTo);

        assertEquals(FILES, found, "examples/templates/ carries what this test knows about");
    }

    @Test
    void theReadmeNamesEveryFileBesideIt() {
        String readme = new String(example("README.md"), java.nio.charset.StandardCharsets.UTF_8);

        for (String file : FILES) {
            if (!"README.md".equals(file)) {
                assertTrue(readme.contains(file),
                        "the README of the examples names " + file);
            }
        }
    }

    /**
     * The placements the documentation prints are the placements of the example it took
     * them from. The two files are formatted differently and say the same thing, so the
     * comparison is of the JSON with its formatting taken out.
     */
    @Test
    void theSnippetOfTheDocumentationIsFromTheExample() {
        String gross = compact(new String(example("gross.json"),
                java.nio.charset.StandardCharsets.UTF_8));

        for (String entry : placements()) {
            assertTrue(gross.contains(compact(entry)),
                    "examples/templates/gross.json carries what docs/templates.md prints: "
                            + entry);
        }
    }

    /**
     * And so are the members of the letter the page prints: the example letter template
     * carries every one of them, so a reader who copies the snippet copies a template this
     * module reads.
     */
    @Test
    void theLetterSnippetOfTheDocumentationIsFromTheExample() {
        String letter = compact(new String(example("letter.json"),
                java.nio.charset.StandardCharsets.UTF_8));
        String block = compact(snippet(LETTER_PAGE, "\"layout\": \"letter\""));

        assertTrue(block.contains("\"layout\":\"letter\""), "the page shows the layout");
        for (String member : List.of("\"addressWindow\":\"din5008-b\"",
                "\"information\":\"line\"", "\"foldMarks\":true",
                "\"holeMark\":true", "\"sellerDetails\":\"footer\"",
                "\"paymentCode\":true")) {
            assertTrue(block.contains(member), LETTER_PAGE + " shows " + member);
            assertTrue(letter.contains(member),
                    "examples/templates/letter.json carries " + member);
        }
    }

    /** Returns the placement objects of the block of {@code docs/templates.md} that has them. */
    private static List<String> placements() {
        String block = snippet(TEMPLATES_PAGE, "extensionTerms");
        List<String> entries = new java.util.ArrayList<>();
        int at = block.indexOf('{');
        while (at >= 0) {
            int end = block.indexOf('}', block.indexOf('}', at) + 1);
            entries.add(block.substring(at, end + 1));
            at = block.indexOf('{', end);
        }
        assertEquals(2, entries.size(), "the block shows two placements");
        return entries;
    }

    /**
     * Returns the JSON block of a page that carries a marker.
     *
     * @param path   the page on the test classpath
     * @param marker the text the block carries
     * @return the block
     */
    private static String snippet(String path, String marker) {
        String page = Corpus.text(path);
        int start = page.indexOf("```json");
        while (start > 0) {
            String block = page.substring(page.indexOf('\n', start) + 1,
                    page.indexOf("```", start + 7));
            if (block.contains(marker)) {
                return block;
            }
            start = page.indexOf("```json", start + 7);
        }
        throw new AssertionError(path + " carries a JSON block with " + marker);
    }

    /** Returns JSON with the formatting taken out and the strings left alone. */
    private static String compact(String json) {
        return json.replaceAll("\\s*([{}\\[\\]:,])\\s*", "$1");
    }

    private static byte[] example(String name) {
        return Corpus.bytes("/examples/templates/" + name);
    }

    private static void assertSamePixels(byte[] drawn, byte[] checkedIn, String what) {
        BufferedImage left = read(drawn);
        BufferedImage right = read(checkedIn);
        assertEquals(left.getWidth(), right.getWidth(), what + ": the width");
        assertEquals(left.getHeight(), right.getHeight(), what + ": the height");
        for (int y = 0; y < left.getHeight(); y++) {
            for (int x = 0; x < left.getWidth(); x++) {
                if (left.getRGB(x, y) != right.getRGB(x, y)) {
                    assertEquals(left.getRGB(x, y), right.getRGB(x, y),
                            what + ": the pixel at " + x + ", " + y);
                }
            }
        }
    }

    private static BufferedImage read(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertNotNull(image, "the bytes are a picture");
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
