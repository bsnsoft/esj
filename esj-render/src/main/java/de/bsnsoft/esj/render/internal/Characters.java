package de.bsnsoft.esj.render.internal;

import de.bsnsoft.esj.Esj;
import java.nio.charset.StandardCharsets;

/**
 * The characters a rendering does not pass on as they stand.
 *
 * <p>Both renderings of this module, and the report that carries one of them, write the
 * text of a document from a stranger. Three kinds of character in it are not text to be
 * shown as it stands, and this class is the one place that says which they are: the HTML
 * rendering, the PDF rendering in both of its layouts, the title of the PDF and the report
 * all ask it, so the forms of one document say the same thing.
 *
 * <p>The first kind only directs the reading order, and becomes a space. It is invisible,
 * it changes what a line appears to say without changing what the document stores, and the
 * classic use of it is an identifier or a file name that reads one way on the page and is
 * another in the data. A rendering that carried it would show the reader something other
 * than what the invoice says, which is the one thing a rendering must not do.
 *
 * <p>The second kind is what a terminal, a file format or a pipeline reads as a command
 * rather than as text, and becomes a space as well: the C0 and C1 controls that do not end
 * a line, and U+007F. A NUL makes a report a binary blob to a version control system, a
 * search and a mail gateway, and is a parse error in HTML, where the file on disk and the
 * page a browser shows then disagree; an escape sequence repaints the terminal a file is
 * written to. The PDF rendering has always replaced those characters on its way to a page,
 * because no font has a glyph for them; doing it here means the forms of one report say
 * the same thing and the console does too.
 *
 * <p>The third kind ends a line, and a text may say that in seven ways: the line feed, the
 * carriage return and the two together, the vertical tabulator, the form feed, U+0085, and
 * the line and paragraph separators U+2028 and U+2029. A rendering knows one of them, the
 * line feed, and every other one becomes a line feed — the pair of a carriage return and a
 * line feed one line feed, not two. A layout breaks a block at a line feed and writes a
 * text that has to stand on one line, the page footer for one, with a space in its place;
 * nothing that ends a line ever reaches a font, which has no glyph for one.
 *
 * <p>Nothing else is removed: markup, quotation marks, the tabulator and every printable
 * character of every script reach the page as the document wrote them, escaped where the
 * format needs escaping.
 */
public final class Characters {

    private Characters() {
        throw new AssertionError("no instances");
    }

    /**
     * Tells whether a code point directs the reading order rather than showing a
     * character: a bidirectional formatting character of {@link Esj#isBidiControl(int)} —
     * the Arabic letter mark U+061C, the marks U+200E and U+200F, the embeddings and
     * overrides U+202A-U+202E and the isolates U+2066-U+2069 — or one of the interlinear
     * annotation characters U+FFF9-U+FFFB.
     *
     * <p>The first set is the one every text output of this implementation escapes, and is
     * asked of {@code esj-core} so that a rendering and a message never disagree about it.
     * The annotation characters are a rendering's own addition: they mark a run of text as
     * an annotation of another and change what a line on a page appears to say, which is
     * this class's concern, but they steer no terminal and reorder nothing, so a message
     * carries them as they stand and the specification, section 9.5 does not list them.
     *
     * @param codePoint the code point
     * @return {@code true} if it is one of those
     */
    static boolean directional(int codePoint) {
        return Esj.isBidiControl(codePoint)
                || (codePoint >= 0xfff9 && codePoint <= 0xfffb);
    }

    /**
     * Tells whether a code point ends a line: the line feed, the carriage return, the
     * vertical tabulator, the form feed, U+0085, and the line and paragraph separators
     * U+2028 and U+2029.
     *
     * @param codePoint the code point
     * @return {@code true} if it is one of those
     */
    static boolean lineEnd(int codePoint) {
        return codePoint == '\n' || codePoint == '\r' || codePoint == 0x0b
                || codePoint == 0x0c || codePoint == 0x85
                || codePoint == 0x2028 || codePoint == 0x2029;
    }

    /**
     * Tells whether a code point is a command rather than text: a C0 control other than
     * the tabulator and the ones that {@linkplain #lineEnd(int) end a line}, U+007F, or a
     * C1 control other than U+0085, which ends a line.
     *
     * @param codePoint the code point
     * @return {@code true} if it is one of those
     */
    static boolean commanding(int codePoint) {
        if (lineEnd(codePoint)) {
            return false;
        }
        return (codePoint < 0x20 && codePoint != '\t')
                || (codePoint >= 0x7f && codePoint <= 0x9f);
    }

    /**
     * Returns a text as a rendering writes it: every way of ending a line as one line
     * feed, and every {@linkplain #directional(int) directional control} and every
     * {@linkplain #commanding(int) commanding control} as a space.
     *
     * @param text the text
     * @return the text, or the same string where there was nothing to change
     */
    static String plain(String text) {
        return written(text, false);
    }

    /**
     * Returns a text as a rendering writes it on one line: as {@link #plain(String)} does,
     * with a space where a line ends.
     *
     * @param text the text
     * @return the text, or the same string where there was nothing to change
     */
    static String oneLine(String text) {
        return written(text, true);
    }

    /** Writes a text with the replacements of this class, ending a line or not. */
    private static String written(String text, boolean oneLine) {
        int first = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (changed(c, oneLine)) {
                first = i;
                break;
            }
        }
        if (first < 0) {
            return text;
        }
        StringBuilder written = new StringBuilder(text.length());
        written.append(text, 0, first);
        for (int i = first; i < text.length(); i++) {
            char c = text.charAt(i);
            if (lineEnd(c)) {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                written.append(oneLine ? ' ' : '\n');
            } else if (directional(c) || commanding(c)) {
                written.append(' ');
            } else {
                written.append(c);
            }
        }
        return written.toString();
    }

    /** Tells whether a character of a text is written as something else. */
    private static boolean changed(char c, boolean oneLine) {
        if (c == '\n') {
            return oneLine;
        }
        return lineEnd(c) || directional(c) || commanding(c);
    }

    /**
     * Returns the bytes of an XML document with every directional and every commanding
     * control replaced by a space, and every line end that is not markup by a line feed.
     *
     * <p>The C0 characters cannot be there, but for the three the markup itself is written
     * with: XML 1.0 admits no other, so a document that reached a parser carries none of
     * them, and the tabulator, the line feed and the carriage return stay where they are.
     * What XML 1.0 does admit is U+007F, the C1 controls, U+0085 and the two Unicode
     * separators, and the directional characters. All of them are content of the document
     * — none of them may stand in a name of the XR representation, whose element names come
     * from a checked-in table — and replacing one here is the same replacement the PDF
     * rendering makes on its way to a page. The bytes are searched before anything is
     * decoded, and a document that carries none of them is handed back unchanged, which is
     * what all but a crafted document is.
     *
     * @param xml the document, encoded in UTF-8
     * @return the same array where there was nothing to replace, a new one otherwise
     */
    public static byte[] plain(byte[] xml) {
        if (!mayCarryReplaced(xml)) {
            return xml;
        }
        String text = new String(xml, StandardCharsets.UTF_8);
        StringBuilder plain = new StringBuilder(text);
        boolean replaced = false;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (lineEnd(c)) {
                plain.setCharAt(i, '\n');
                replaced = true;
            } else if (directional(c) || commanding(c)) {
                plain.setCharAt(i, ' ');
                replaced = true;
            }
        }
        return replaced ? plain.toString().getBytes(StandardCharsets.UTF_8) : xml;
    }

    /**
     * Tells whether the bytes may carry one of the characters {@link #plain(byte[])}
     * replaces, by the UTF-8 forms they are written in: U+007F is one byte, the C1
     * controls and U+0085 two that begin {@code C2 80} to {@code C2 9F}, U+061C the two
     * bytes {@code D8 9C}, and every other one three that begin {@code E2 80},
     * {@code E2 81} or {@code EF BF}.
     */
    private static boolean mayCarryReplaced(byte[] xml) {
        for (int i = 0; i < xml.length; i++) {
            int first = xml[i] & 0xff;
            if (first == 0x7f) {
                return true;
            }
            if (i + 1 >= xml.length) {
                break;
            }
            int second = xml[i + 1] & 0xff;
            if (first == 0xc2 && second >= 0x80 && second <= 0x9f
                    || first == 0xd8 && second == 0x9c
                    || first == 0xe2 && (second == 0x80 || second == 0x81)
                    || first == 0xef && second == 0xbf) {
                return true;
            }
        }
        return false;
    }
}
