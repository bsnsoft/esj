package de.bsnsoft.esj.json;

/**
 * The string rules of the JSON layer: the length measured in UTF-8 bytes that the limits
 * of the specification, section 12.2 are counted in, the lone surrogate that makes a
 * document impossible to represent as bytes, and the two grammars that belong to the
 * envelope rather than to a value — the owner token of section 4.6 and the digest of
 * section 4.7.
 *
 * <p>The line ending normalization of section 6.8 and the message escaping of section 9.5
 * live in {@link de.bsnsoft.esj.Esj}, and the grammars a registry datatype
 * requires of the content of a value live with the value, because layer L2 checks them.
 */
final class Texts {

    private Texts() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the number of bytes the UTF-8 encoding of a string takes, without encoding
     * it. A lone surrogate counts as the three bytes its replacement would take; a string
     * that carries one is rejected before the count is used.
     *
     * @param value the string to measure
     * @return the length in UTF-8 bytes
     */
    static long utf8Length(String value) {
        long length = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x80) {
                length += 1;
            } else if (c < 0x800) {
                length += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                length += 4;
                i++;
            } else {
                length += 3;
            }
        }
        return length;
    }

    /**
     * Tells whether a string contains a surrogate that is not part of a pair. Such a
     * string has no UTF-8 encoding, so a document containing one has no stable byte
     * representation and would break the digests (specification, section 6.8).
     *
     * @param value the string to test
     * @return {@code true} if the string carries an unpaired surrogate
     */
    static boolean hasLoneSurrogate(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    return true;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a string carries an escaped surrogate that is not part of a pair,
     * asking the bytes of the document rather than a parser. A parser that decodes member
     * names to strings refuses such a name while it reads it, so the reader cannot see the
     * name itself; the specification, section 9.6 nevertheless names that defect
     * {@code ESJ-L1-SURROGATE}, and this is how the reader recognizes it.
     *
     * <p>Only escapes are examined. A raw surrogate has no UTF-8 encoding and is caught by
     * {@link #isValidUtf8(byte[], int)} before a parser is started.
     *
     * @param bytes  the bytes of the document
     * @param length the number of bytes of {@code bytes} that are the document
     * @return {@code true} if an unpaired {@code \\uXXXX} surrogate escape occurs
     */
    static boolean hasLoneSurrogateEscape(byte[] bytes, int length) {
        int i = 0;
        boolean highPending = false;
        while (i < length) {
            if (bytes[i] != '\\') {
                if (highPending) {
                    return true;
                }
                i++;
                continue;
            }
            int start = i;
            while (i < length && bytes[i] == '\\') {
                i++;
            }
            boolean escape = ((i - start) % 2) == 1;
            if (!escape || i >= length || bytes[i] != 'u') {
                if (highPending) {
                    return true;
                }
                continue;
            }
            int code = hex4(bytes, i + 1, length);
            i += 5;
            boolean high = code >= 0xD800 && code <= 0xDBFF;
            boolean low = code >= 0xDC00 && code <= 0xDFFF;
            if (highPending != low) {
                return true;
            }
            highPending = high;
        }
        return highPending;
    }

    private static int hex4(byte[] bytes, int from, int length) {
        if (from + 4 > length) {
            return -1;
        }
        int value = 0;
        for (int i = from; i < from + 4; i++) {
            int digit = Character.digit((char) (bytes[i] & 0xFF), 16);
            if (digit < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    /**
     * Returns the number of bytes a base64 string decodes to, without decoding it, so
     * that the bound of the specification, section 12.5 can be applied first. Whether the
     * string is canonical base64 at all is a layer L2 question and is not asked here: the
     * result is the size a reader has to be willing to hold either way.
     *
     * @param value the encoded string
     * @return the number of decoded bytes
     */
    static long decodedBase64Length(String value) {
        int padding = 0;
        for (int i = value.length(); i > 0 && value.charAt(i - 1) == '='; i--) {
            padding++;
        }
        return value.length() / 4L * 3L - padding;
    }

    /**
     * Tells whether a byte sequence is valid UTF-8, rejecting overlong encodings, the
     * surrogate range and everything above U+10FFFF.
     *
     * @param bytes  the byte sequence to test
     * @param length the number of bytes of {@code bytes} to test
     * @return {@code true} if the bytes are a valid UTF-8 encoding
     */
    static boolean isValidUtf8(byte[] bytes, int length) {
        int i = 0;
        while (i < length) {
            int b = bytes[i] & 0xFF;
            int following;
            int codePoint;
            if (b < 0x80) {
                i++;
                continue;
            } else if (b >= 0xC2 && b <= 0xDF) {
                following = 1;
                codePoint = b & 0x1F;
            } else if (b >= 0xE0 && b <= 0xEF) {
                following = 2;
                codePoint = b & 0x0F;
            } else if (b >= 0xF0 && b <= 0xF4) {
                following = 3;
                codePoint = b & 0x07;
            } else {
                return false;
            }
            if (i + following >= length) {
                return false;
            }
            for (int k = 1; k <= following; k++) {
                int next = bytes[i + k] & 0xFF;
                if (next < 0x80 || next > 0xBF) {
                    return false;
                }
                codePoint = (codePoint << 6) | (next & 0x3F);
            }
            if (following == 2 && (codePoint < 0x800 || (codePoint >= 0xD800 && codePoint <= 0xDFFF))) {
                return false;
            }
            if (following == 3 && (codePoint < 0x10000 || codePoint > 0x10FFFF)) {
                return false;
            }
            i += following + 1;
        }
        return true;
    }

    /**
     * Tells whether a string is an owner token of the specification, section 4.6: ASCII,
     * 1 to 128 characters, starting and ending with a letter or a digit, otherwise
     * carrying only letters, digits, {@code .}, {@code _} and {@code -}, and never
     * beginning with {@code BT-} or {@code BG-}.
     *
     * @param value the string to test
     * @return {@code true} if the string is an owner token
     */
    static boolean isOwnerToken(String value) {
        int length = value.length();
        if (length == 0 || length > 128) {
            return false;
        }
        if (value.startsWith("BT-") || value.startsWith("BG-")) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            boolean alnum = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            boolean edge = i == 0 || i == length - 1;
            if (edge ? !alnum : !(alnum || c == '.' || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells whether a string is 64 lowercase hexadecimal digits, the form the
     * specification, section 4.7 fixes for {@code source.sha256}.
     *
     * @param value the string to test
     * @return {@code true} if the string is a lowercase SHA-256 digest
     */
    static boolean isLowercaseSha256(String value) {
        if (value.length() != 64) {
            return false;
        }
        for (int i = 0; i < 64; i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Names the one clause of the digest grammar a string breaks.
     *
     * <p>The phrase is written to follow the word "sha256", so that the message says what
     * the document carries rather than restating the rule it broke.
     *
     * @param value a string that {@link #isLowercaseSha256(String)} rejects
     * @return the phrase, without a leading or trailing space
     */
    static String sha256Violation(String value) {
        if (value.length() != 64) {
            return "is " + value.length() + " characters long; a digest is 64 hexadecimal"
                    + " digits";
        }
        for (int i = 0; i < 64; i++) {
            char c = value.charAt(i);
            if ((c >= 'A' && c <= 'F')) {
                return "carries the uppercase digit " + printable(c) + "; write it in lowercase";
            }
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return "carries " + printable(c) + ", which is not a hexadecimal digit";
            }
        }
        return "is not a digest written as 64 lowercase hexadecimal digits";
    }

    /**
     * Names the one clause of the owner token grammar a string breaks.
     *
     * <p>The phrase is written to follow the offending token.
     *
     * @param value a string that {@link #isOwnerToken(String)} rejects
     * @return the phrase, or {@code null} if the string is an owner token after all
     */
    static String ownerTokenViolation(String value) {
        int length = value.length();
        if (length == 0) {
            return "is empty";
        }
        if (length > 128) {
            return "is " + length + " characters long; an owner token has at most 128";
        }
        if (value.startsWith("BT-") || value.startsWith("BG-")) {
            return "begins with BT- or BG-, which is reserved for business terms";
        }
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            boolean alnum = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9');
            if (alnum) {
                continue;
            }
            if (i == 0 || i == length - 1) {
                return "starts or ends with " + printable(c) + "; an owner token starts and"
                        + " ends with a letter or a digit";
            }
            if (c != '.' && c != '_' && c != '-') {
                return "carries " + printable(c) + "; an owner token carries letters, digits,"
                        + " '.', '_' and '-'";
            }
        }
        return null;
    }

    /**
     * Writes one character of document content for a message: quoted where it is a
     * printable ASCII character, and as {@code U+XXXX} otherwise, so that a control
     * character or a bidirectional override cannot travel into a terminal or a log line
     * through a diagnostic (specification, section 12.6).
     */
    private static String printable(char c) {
        if (c >= 0x20 && c < 0x7F) {
            return "'" + c + "'";
        }
        return String.format("U+%04X", (int) c);
    }
}
