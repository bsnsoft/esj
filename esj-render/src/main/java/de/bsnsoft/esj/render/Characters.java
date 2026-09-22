package de.bsnsoft.esj.render;

import java.nio.charset.StandardCharsets;

/**
 * The characters a rendering does not pass on.
 *
 * <p>Both renderings of this module, and the report that carries one of them, write the
 * text of a document from a stranger. Two kinds of control character are not text and are
 * replaced by a space.
 *
 * <p>The first only directs the reading order. It is invisible, it changes what a line
 * appears to say without changing what the document stores, and the classic use of it is
 * an identifier or a file name that reads one way on the page and is another in the data.
 * A rendering that carried it would show the reader something other than what the invoice
 * says, which is the one thing a rendering must not do.
 *
 * <p>The second is what a terminal, a file format or a pipeline reads as a command rather
 * than as text: everything below U+0020 but the line feed and the tabulator, and U+007F. A
 * NUL makes a report a binary blob to a version control system, a search and a mail
 * gateway, and is a parse error in HTML, where the file on disk and the page a browser
 * shows then disagree; an escape sequence repaints the terminal a file is written to. The
 * PDF rendering has always replaced those characters on its way to a page, because no font
 * has a glyph for them; doing it here means the two forms of one report say the same thing
 * and the console does too.
 *
 * <p>Nothing else is removed: markup, quotation marks and every printable character of
 * every script reach the page as the document wrote them, escaped where the format needs
 * escaping.
 */
final class Characters {

    private Characters() {
        throw new AssertionError("no instances");
    }

    /**
     * Tells whether a code point directs the reading order rather than showing a
     * character: the marks U+200E and U+200F, the embeddings and overrides
     * U+202A-U+202E, the isolates U+2066-U+2069 and the interlinear annotation
     * characters U+FFF9-U+FFFB.
     *
     * @param codePoint the code point
     * @return {@code true} if it is one of those
     */
    static boolean directional(int codePoint) {
        return codePoint == 0x200e || codePoint == 0x200f
                || (codePoint >= 0x202a && codePoint <= 0x202e)
                || (codePoint >= 0x2066 && codePoint <= 0x2069)
                || (codePoint >= 0xfff9 && codePoint <= 0xfffb);
    }

    /**
     * Tells whether a code point is a command rather than text: a C0 control other than
     * the line feed and the tabulator, or U+007F.
     *
     * @param codePoint the code point
     * @return {@code true} if it is one of those
     */
    static boolean commanding(int codePoint) {
        return codePoint == 0x7f
                || (codePoint < 0x20 && codePoint != '\n' && codePoint != '\t');
    }

    /**
     * Returns a text with every {@linkplain #directional(int) directional control} and
     * every {@linkplain #commanding(int) commanding control} replaced by a space.
     *
     * @param text the text
     * @return the text without those characters, or the same string where it has none
     */
    static String plain(String text) {
        int found = -1;
        for (int i = 0; i < text.length(); i++) {
            if (replaced(text.charAt(i))) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            return text;
        }
        StringBuilder plain = new StringBuilder(text);
        for (int i = found; i < plain.length(); i++) {
            if (replaced(plain.charAt(i))) {
                plain.setCharAt(i, ' ');
            }
        }
        return plain.toString();
    }

    /** Tells whether a character of a text of the run is replaced by a space. */
    private static boolean replaced(char c) {
        return directional(c) || commanding(c);
    }

    /**
     * Returns the bytes of an XML document with every directional control replaced by a
     * space.
     *
     * <p>Only the directional ones: the other controls cannot be there. XML 1.0 admits no
     * C0 character but the tabulator, the line feed and the carriage return, so a document
     * that reached a parser carries none of them, and the three it may carry are what the
     * markup is written with. The directional characters are content of the document —
     * none of them may stand in a name of the XR representation, whose element names come
     * from a checked-in table — and replacing one here is the same replacement the PDF
     * rendering makes on its way to a page. The bytes are searched before anything is
     * decoded, and a document that carries none of them is handed back unchanged, which is
     * what all but a crafted document is.
     *
     * @param xml the document, encoded in UTF-8
     * @return the same array where there was nothing to replace, a new one otherwise
     */
    static byte[] plain(byte[] xml) {
        if (!mayCarryDirectional(xml)) {
            return xml;
        }
        String text = new String(xml, StandardCharsets.UTF_8);
        StringBuilder plain = new StringBuilder(text);
        boolean replaced = false;
        for (int i = 0; i < plain.length(); i++) {
            if (directional(plain.charAt(i))) {
                plain.setCharAt(i, ' ');
                replaced = true;
            }
        }
        return replaced ? plain.toString().getBytes(StandardCharsets.UTF_8) : xml;
    }

    /**
     * Tells whether the bytes carry the lead of one of the three UTF-8 ranges the
     * directional controls live in. Every one of them is three bytes long and begins
     * {@code E2 80}, {@code E2 81} or {@code EF BF}.
     */
    private static boolean mayCarryDirectional(byte[] xml) {
        for (int i = 0; i + 1 < xml.length; i++) {
            int first = xml[i] & 0xff;
            int second = xml[i + 1] & 0xff;
            if (first == 0xe2 && (second == 0x80 || second == 0x81)
                    || first == 0xef && second == 0xbf) {
                return true;
            }
        }
        return false;
    }
}
