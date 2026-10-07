package de.bsnsoft.esj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one set of characters no text of this implementation writes out as it stands
 * ({@link Esj#steersATerminal(int)}), class by class, and the message escaping of the
 * specification, section 9.5, over each of them.
 *
 * <p>Each class is named by the code points at its edges and one inside, because the
 * defect this test was written against was a range that ended one character too early: the
 * C1 controls were let through while the C0 controls before them were escaped.
 */
class TerminalCharactersTest {

    @ParameterizedTest(name = "code point {0} (C0 control)")
    @ValueSource(ints = {0x00, 0x07, 0x08, 0x0B, 0x0C, 0x1B, 0x1F})
    void aC0ControlSteersATerminal(int codePoint) {
        assertSteersAndIsEscaped(codePoint);
    }

    @ParameterizedTest(name = "code point {0} (delete)")
    @ValueSource(ints = {0x7F})
    void theDeleteCharacterSteersATerminal(int codePoint) {
        assertSteersAndIsEscaped(codePoint);
    }

    @ParameterizedTest(name = "code point {0} (C1 control)")
    @ValueSource(ints = {0x80, 0x85, 0x8D, 0x90, 0x9B, 0x9C, 0x9D, 0x9F})
    void aC1ControlSteersATerminal(int codePoint) {
        assertSteersAndIsEscaped(codePoint);
    }

    @ParameterizedTest(name = "code point {0} (line or paragraph separator)")
    @ValueSource(ints = {0x2028, 0x2029})
    void aLineOrParagraphSeparatorSteersATerminal(int codePoint) {
        assertSteersAndIsEscaped(codePoint);
    }

    @ParameterizedTest(name = "code point {0} (bidirectional control)")
    @ValueSource(ints = {0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
                         0x2066, 0x2067, 0x2068, 0x2069})
    void aBidirectionalControlSteersATerminal(int codePoint) {
        assertTrue(Esj.isBidiControl(codePoint), hex(codePoint));
        assertSteersAndIsEscaped(codePoint);
    }

    /**
     * The neighbours of every range, and characters that look like candidates and are not:
     * a no-break space, the zero-width characters that join or separate, the byte order
     * mark, an Arabic letter beside the mark, and the characters around the separators and
     * the isolates.
     */
    @ParameterizedTest(name = "code point {0} says something")
    @ValueSource(ints = {0x20, 0x41, 0x7E, 0xA0, 0xA9, 0xFC, 0x061B, 0x061D, 0x0628, 0x200B,
                         0x200C, 0x200D, 0x2027, 0x202F, 0x2065, 0x206A, 0x20AC, 0xFEFF,
                         0x1F600})
    void aCharacterThatSaysSomethingIsLeftAlone(int codePoint) {
        assertFalse(Esj.steersATerminal(codePoint), hex(codePoint));
        assertFalse(Esj.isBidiControl(codePoint), hex(codePoint));
        String text = "a" + Character.toString(codePoint) + "b";
        assertEquals(text, Esj.forMessage(text, 80));
        assertEquals(text, Esj.forSubject(text));
    }

    @ParameterizedTest(name = "code point {0} is written by name")
    @ValueSource(ints = {0x09, 0x0A, 0x0D})
    void theThreeWhitespaceControlsBelongToTheSetAndAreWrittenByName(int codePoint) {
        assertTrue(Esj.steersATerminal(codePoint), hex(codePoint));
        String expected = switch (codePoint) {
            case 0x09 -> "a\\tb";
            case 0x0A -> "a\\nb";
            default -> "a\\rb";
        };
        assertEquals(expected, Esj.forSubject("a" + Character.toString(codePoint) + "b"));
    }

    private static void assertSteersAndIsEscaped(int codePoint) {
        assertTrue(Esj.steersATerminal(codePoint), hex(codePoint));
        String fragment = "RE-" + Character.toString(codePoint) + "1";
        String escaped = "RE-\\u" + String.format(Locale.ROOT, "%04x", codePoint) + "1";
        assertEquals(escaped, Esj.forMessage(fragment, 80));
        assertEquals(escaped, Esj.forSubject(fragment));
        assertEquals(escaped.length(), Esj.abbreviated(escaped, escaped.length()).length(),
                "a fragment that fits is not cut");
    }

    private static String hex(int codePoint) {
        return String.format(Locale.ROOT, "U+%04X", codePoint);
    }
}
