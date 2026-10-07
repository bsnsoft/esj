package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The escaping of a fragment of a container — an attachment name, a media type, an XMP
 * property — before a finding quotes it: the characters of the specification, section 9.5,
 * which are the ones {@code esj-core} names, and nothing else.
 */
class MessagesTest {

    @Test
    void theWhitespaceControlsTheBackslashAndTheQuotationMarkAreWrittenByName() {
        assertEquals("\"a\\tb\\nc\\rd\\\\e\\\"f\"", Messages.quoted("a\tb\nc\rd\\e\"f"));
    }

    @Test
    void everyOtherCharacterThatSteersATerminalIsWrittenByCodePoint() {
        String name = "x" + (char) 0x1b + "[2K" + (char) 0x7f + (char) 0x9b + (char) 0x85
                + (char) 0x2028 + (char) 0x2029 + (char) 0x061c + (char) 0x202e
                + (char) 0x2066 + "lmx.lmth.xml";

        assertEquals("x\\u001b[2K\\u007f\\u009b\\u0085\\u2028\\u2029\\u061c\\u202e\\u2066"
                + "lmx.lmth.xml", Messages.escape(name));
    }

    @Test
    void aNameInAnotherScriptIsLeftAsItIs() {
        String name = "請求書-" + (char) 0x00fc + (char) 0x20ac + ".xml";

        assertEquals(name, Messages.escape(name));
    }
}
