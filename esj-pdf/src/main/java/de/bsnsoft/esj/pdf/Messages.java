package de.bsnsoft.esj.pdf;

/**
 * Escapes a fragment of a document before it is put into a message.
 *
 * <p>An attachment name, a media type and an XMP property are content a stranger wrote,
 * and a finding about them is written to a terminal or a log line. The specification,
 * section 9.5 asks a validator to escape such a fragment so that an escape sequence in it
 * cannot rewrite the line a reader sees and a quotation mark in it cannot forge the rest
 * of a location. The same rule is applied here, to the same characters.
 */
final class Messages {

    private Messages() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns a fragment of a document, escaped and in quotation marks, ready to be put
     * into a message.
     */
    static String quoted(String text) {
        return "\"" + escape(text) + "\"";
    }

    /** Returns a fragment of a document with the characters of section 9.5 escaped. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7F || isBidirectionalFormatting(c)) {
                        out.append(String.format("\\u%04X", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * Tells whether a character reorders the text around it. These are invisible and
     * would let a name rewrite the rest of the line it is printed on.
     */
    private static boolean isBidirectionalFormatting(char c) {
        return c == '؜'
                || c == '‎' || c == '‏'
                || (c >= '‪' && c <= '‮')
                || (c >= '⁦' && c <= '⁩');
    }
}
