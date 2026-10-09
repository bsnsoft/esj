package de.bsnsoft.esj.cli.serve;

import de.bsnsoft.esj.Esj;
import java.util.Locale;

/**
 * The characters a reader of a text cannot see, and their escapes.
 *
 * <p>A value of an invoice comes back from every tool exactly as the document states it: a
 * reading tool changes no data. What a model reads, though, is the text of a result, and a
 * text in which a bidirectional override reorders what follows, a zero-width character
 * splits a word or a run of tag characters spells an instruction nobody sees is no longer
 * the text it appears to be. Every such character is therefore written as the escape
 * {@code \}{@code uXXXX} of its UTF-16 units wherever this server writes text for a reader —
 * the text of an MCP result, the message of a protocol error — and in every JSON it writes,
 * where the escape is the character itself to a JSON reader.
 *
 * <p>The characters are those that {@link Esj#steersATerminal(int)} names — the controls,
 * the line and paragraph separators and the bidirectional formatting characters — and
 * beyond them every format character of Unicode (general category Cf: soft hyphen,
 * zero-width space and joiners, word joiner, the invisible operators, the byte order mark,
 * the tag characters), a surrogate that is not one half of a pair, the variation
 * selectors, the combining grapheme joiner, the Mongolian free variation selectors and the
 * Hangul fillers, which are letters that draw nothing.
 */
final class Visible {

    private Visible() {
    }

    /**
     * Tells whether a reader of a text cannot see a character, or cannot see what it does.
     *
     * @param codePoint a code point, or a UTF-16 unit
     * @return {@code true} if the character is written as an escape
     */
    static boolean hidden(int codePoint) {
        if (Esj.steersATerminal(codePoint)) {
            return true;
        }
        if (codePoint < 0xA0) {
            return false;
        }
        int type = Character.getType(codePoint);
        return type == Character.FORMAT
                || type == Character.SURROGATE
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                || (codePoint >= 0xE0100 && codePoint <= 0xE01EF)
                || (codePoint >= 0x180B && codePoint <= 0x180F)
                || codePoint == 0x034F
                || codePoint == 0x115F || codePoint == 0x1160 || codePoint == 0x3164
                || codePoint == 0xFFA0;
    }

    /**
     * Returns a text with every hidden character but the line feed written as the escapes of
     * its UTF-16 units, the way JSON writes them.
     *
     * @param text the text
     * @return the text a reader sees as it is
     */
    static String text(String text) {
        int length = text.length();
        int i = 0;
        while (i < length) {
            int codePoint = text.codePointAt(i);
            if (codePoint != '\n' && hidden(codePoint)) {
                break;
            }
            i += Character.charCount(codePoint);
        }
        if (i == length) {
            return text;
        }
        StringBuilder visible = new StringBuilder(length + 16);
        visible.append(text, 0, i);
        while (i < length) {
            int codePoint = text.codePointAt(i);
            int units = Character.charCount(codePoint);
            if (codePoint != '\n' && hidden(codePoint)) {
                for (int unit = i; unit < i + units; unit++) {
                    escape(visible, text.charAt(unit));
                }
            } else {
                visible.appendCodePoint(codePoint);
            }
            i += units;
        }
        return visible.toString();
    }

    private static void escape(StringBuilder out, char unit) {
        out.append(String.format(Locale.ROOT, "\\u%04X", (int) unit));
    }
}
