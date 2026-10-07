package de.bsnsoft.esj.render.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one place that says which characters a rendering does not pass on as they stand.
 *
 * <p>Three kinds, and three answers: a character that directs the reading order becomes a
 * space, a control character becomes a space, and every way of ending a line becomes a line
 * feed — or a space, on a line that has to stay one. The cases here ask the class itself;
 * {@code ControlCharactersRenderingTest} asks the renderings that use it.
 */
class CharactersTest {

    /** Every character that directs the reading order, as the class names them. */
    static IntStream directional() {
        return IntStream.of(0x061c, 0x200e, 0x200f, 0x202a, 0x202b, 0x202c, 0x202d, 0x202e,
                0x2066, 0x2067, 0x2068, 0x2069, 0xfff9, 0xfffa, 0xfffb);
    }

    /** Every way of ending a line, but the line feed and the pair of a return and a feed. */
    static IntStream lineEnds() {
        return IntStream.of('\r', 0x0b, 0x0c, 0x85, 0x2028, 0x2029);
    }

    @ParameterizedTest
    @MethodSource("directional")
    void aCharacterThatDirectsTheReadingOrderIsASpace(int codePoint) {
        String text = "2026" + Character.toString(codePoint) + "0042";

        assertTrue(Characters.directional(codePoint));
        assertEquals("2026 0042", Characters.plain(text));
        assertEquals("2026 0042", Characters.oneLine(text));
        assertArrayEquals(bytes("<a>2026 0042</a>"),
                Characters.plain(bytes("<a>" + text + "</a>")),
                "the bytes of the HTML rendering lose it as the text does");
    }

    /** The neighbours of the directional characters are text, and stay. */
    @ParameterizedTest
    @ValueSource(ints = {0x061b, 0x061d, 0x200d, 0x2010, 0x2030, 0x202f, 0x2070, 0xfff8,
        0xfffc})
    void theNeighboursOfThoseAreText(int codePoint) {
        String text = "a" + Character.toString(codePoint) + "b";

        assertFalse(Characters.directional(codePoint));
        assertSame(text, Characters.plain(text), "a text with nothing to replace is itself");
        byte[] xml = bytes(text);
        assertSame(xml, Characters.plain(xml), "and so are its bytes");
    }

    @ParameterizedTest
    @MethodSource("lineEnds")
    void everyWayOfEndingALineIsALineFeed(int codePoint) {
        String text = "first" + Character.toString(codePoint) + "second";

        assertTrue(Characters.lineEnd(codePoint));
        assertFalse(Characters.commanding(codePoint), "a line end is not a command");
        assertEquals("first\nsecond", Characters.plain(text));
        assertEquals("first second", Characters.oneLine(text),
                "and a space on a line that stays one");
    }

    @Test
    void aReturnAndAFeedAreOneLineEnd() {
        assertEquals("first\nsecond", Characters.plain("first\r\nsecond"));
        assertEquals("first second", Characters.oneLine("first\r\nsecond"));
        assertEquals("first\n\nsecond", Characters.plain("first\r\rsecond"),
                "two returns are two line ends");
    }

    @Test
    void theLineFeedStaysInABlockAndIsASpaceOnALine() {
        String text = "first\nsecond";

        assertSame(text, Characters.plain(text));
        assertEquals("first second", Characters.oneLine(text));
    }

    @Test
    void theTabulatorIsText() {
        assertSame("a\tb", Characters.plain("a\tb"));
        assertFalse(Characters.commanding('\t'));
    }

    /** The C0 controls that end no line, U+007F and the C1 controls but U+0085. */
    @ParameterizedTest
    @ValueSource(ints = {0x00, 0x01, 0x07, 0x08, 0x1b, 0x1f, 0x7f, 0x80, 0x9b, 0x9d, 0x9f})
    void aControlCharacterIsASpace(int codePoint) {
        String text = "a" + Character.toString(codePoint) + "b";

        assertTrue(Characters.commanding(codePoint));
        assertEquals("a b", Characters.plain(text));
        assertEquals("a b", Characters.oneLine(text));
    }

    /**
     * XML carries U+007F, the C1 controls and the Unicode separators, so the bytes of an
     * XR document are searched for them as well; the line feed, the return and the
     * tabulator the markup is written with stay.
     */
    @Test
    void theBytesOfADocumentLoseWhatTheTextLoses() {
        byte[] xml = bytes("<a\tb='c'>\r\n"
                + "x\u007fy\u009bz\u0085w v u؜t</a>");

        assertArrayEquals(bytes("<a\tb='c'>\r\nx y z\nw\nv\nu t</a>"), Characters.plain(xml));
    }

    /**
     * What every rendering of this module rests on: a text of any character at all, made
     * showable by a face, is something the face can measure and draw. Every code point of
     * the basic plane is tried at once, and so is half of a surrogate pair on its own.
     */
    @Test
    void everyCharacterOfTheBasicPlaneIsShowableOrReplaced() {
        StringBuilder all = new StringBuilder();
        for (int c = 0; c <= 0xffff; c++) {
            all.append((char) c);
        }
        all.append("\ud800x\udc00");
        try (PDDocument document = new PDDocument()) {
            Fonts fonts = Fonts.embeddedIn(document);
            for (Fonts.Face face : new Fonts.Face[] {fonts.regular(), fonts.bold()}) {
                String block = face.showable(all.toString());
                for (String line : block.split("\n", -1)) {
                    assertDoesNotThrow(() -> face.width(line, 10f),
                            "every line of a showable text can be measured");
                }
                String line = face.line(all.toString());
                assertFalse(line.contains("\n"), "a line has no line feed");
                assertDoesNotThrow(() -> face.width(line, 10f),
                        "and the line can be measured whole");
                for (int c : new int[] {'\r', 0x0b, 0x0c, 0x85, 0x2028, 0x2029, '\t', 0,
                    0x1b, 0x7f, 0x9b, 0x061c, 0x202e}) {
                    assertFalse(block.contains(Character.toString(c)),
                            "U+" + Integer.toHexString(c) + " does not reach the font");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
