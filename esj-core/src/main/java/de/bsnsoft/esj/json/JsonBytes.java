package de.bsnsoft.esj.json;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.validate.FindingCode;
import java.io.ByteArrayOutputStream;

/**
 * A byte sink for JSON, with the string escaping of the specification, section 7.5 and
 * its own UTF-8 encoding.
 *
 * <p>The escaping is written here rather than taken from a JSON library because the
 * canonical form is a byte sequence and a library's defaults are not part of it: a
 * generator that escapes the solidus, or that escapes an accented letter instead of
 * writing it out, produces a different document and therefore a different digest. The
 * rules are short enough to state exactly: the five two-character escapes, the escaped
 * quotation mark and the escaped reverse solidus, a backslash-u escape with lowercase
 * hexadecimal digits for the remaining control characters, and every other code point
 * literally, as its UTF-8 bytes.
 */
final class JsonBytes {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final ByteArrayOutputStream out;

    JsonBytes(int expectedSize) {
        this.out = new ByteArrayOutputStream(expectedSize);
    }

    /**
     * Writes one ASCII character, which is a structural character of the JSON text.
     *
     * @param c the character, below U+0080
     */
    void ascii(char c) {
        out.write(c);
    }

    /**
     * Writes an ASCII literal, which is a structural fragment of the JSON text.
     *
     * @param literal the literal, for example {@code true} or {@code null}
     */
    void ascii(String literal) {
        for (int i = 0; i < literal.length(); i++) {
            out.write(literal.charAt(i));
        }
    }

    /**
     * Writes a string as a JSON string, with the quotation marks and the escaping of the
     * specification, section 7.5.
     *
     * @param value the string content
     * @throws EsjFormatException if the string carries an unpaired surrogate, which has no
     *                            UTF-8 encoding
     */
    void string(String value) {
        out.write('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> ascii("\\\"");
                case '\\' -> ascii("\\\\");
                case '\b' -> ascii("\\b");
                case '\t' -> ascii("\\t");
                case '\n' -> ascii("\\n");
                case '\f' -> ascii("\\f");
                case '\r' -> ascii("\\r");
                default -> {
                    if (c < 0x20) {
                        ascii("\\u00");
                        out.write(HEX[(c >> 4) & 0xF]);
                        out.write(HEX[c & 0xF]);
                    } else if (c < 0x80) {
                        out.write(c);
                    } else if (c < 0x800) {
                        out.write(0xC0 | (c >> 6));
                        out.write(0x80 | (c & 0x3F));
                    } else if (Character.isHighSurrogate(c)) {
                        if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                            throw loneSurrogate();
                        }
                        int codePoint = Character.toCodePoint(c, value.charAt(++i));
                        out.write(0xF0 | (codePoint >> 18));
                        out.write(0x80 | ((codePoint >> 12) & 0x3F));
                        out.write(0x80 | ((codePoint >> 6) & 0x3F));
                        out.write(0x80 | (codePoint & 0x3F));
                    } else if (Character.isLowSurrogate(c)) {
                        throw loneSurrogate();
                    } else {
                        out.write(0xE0 | (c >> 12));
                        out.write(0x80 | ((c >> 6) & 0x3F));
                        out.write(0x80 | (c & 0x3F));
                    }
                }
            }
        }
        out.write('"');
    }

    /**
     * Returns everything written so far.
     *
     * @return the bytes of the JSON text
     */
    byte[] toByteArray() {
        return out.toByteArray();
    }

    private static EsjFormatException loneSurrogate() {
        return new EsjFormatException(
                "a string carries an unpaired surrogate and therefore has no UTF-8 encoding",
                FindingCode.ESJ_L1_SURROGATE, null);
    }
}
