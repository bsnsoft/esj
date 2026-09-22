package de.bsnsoft.esj.rules;

/**
 * Escaping of a fragment of a document that goes into a message.
 *
 * <p>A rule message quotes what the invoice says: the amount that does not add up, the
 * code that is not on its list. That fragment is content a stranger wrote
 * ({@code SPEC.md} section 12.6), so it is escaped before it is put in a message
 * (section 9.5): a backslash, a quotation mark, the three whitespace controls by name, and
 * every other C0 control, the delete character and the bidirectional formatting characters
 * by code point. A message is then safe to write to a terminal or a log line as it stands:
 * an escape sequence in a value cannot rewrite the line a reader sees, and a right-to-left
 * override in one cannot reverse the rest of it.
 *
 * <p>A quoted fragment is also cut. {@code esj-core} reproduces at most eighty characters of
 * content in a message ({@code SPEC.md} section 12.6) and this module reproduces the same
 * eighty, so that the two halves of one tool quote a document the same way. Without the cut a
 * rule that quotes a business term quotes as much of it as the sender wrote: a text value may
 * be a megabyte, a document may carry two hundred of them, and a report is then the size of
 * the invoice it is a report about. What a finding owes a reader is the identifier, the path
 * and enough of the value to recognise it.
 *
 * <p>The escaping is reversible, and a caller that wants the characters the document
 * carries — or the whole of a value that was cut — reads the document at the path the
 * finding names rather than parsing the message.
 */
final class Texts {

    private Texts() {
        throw new AssertionError("no instances");
    }

    /** The longest fragment of content a message reproduces (specification, section 12.6). */
    static final int MESSAGE_EXCERPT = 80;

    /**
     * Returns a fragment of a document in the form a message may carry it: cut to
     * {@link #MESSAGE_EXCERPT} characters, then escaped.
     *
     * <p>The cut is made on the characters the document carries and not on the escaped
     * text, which is how {@code esj-core} makes it: a value of eighty characters is quoted
     * whole whatever escaping it needs, and one character more is quoted to eighty and
     * marked with an ellipsis.
     *
     * @param value the text as the document carries it
     * @return the text as a message may carry it
     */
    static String excerpt(String value) {
        if (value.length() <= MESSAGE_EXCERPT) {
            return escape(value);
        }
        int end = MESSAGE_EXCERPT;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return escape(value.substring(0, end)) + "...";
    }

    /**
     * Escapes a fragment of a document for a message, without cutting it.
     *
     * @param value the text as the document carries it
     * @return the text as a message may carry it
     */
    private static String escape(String value) {
        StringBuilder builder = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            String replacement = replacementFor(c);
            if (replacement == null) {
                if (builder != null) {
                    builder.append(c);
                }
                continue;
            }
            if (builder == null) {
                builder = new StringBuilder(value.length() + 8).append(value, 0, i);
            }
            builder.append(replacement);
        }
        return builder == null ? value : builder.toString();
    }

    private static String replacementFor(char c) {
        return switch (c) {
            case '\\' -> "\\\\";
            case '"' -> "\\\"";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> {
                if (c < 0x20 || c == 0x7f || isBidirectional(c)) {
                    yield String.format("\\u%04X", (int) c);
                }
                yield null;
            }
        };
    }

    private static boolean isBidirectional(char c) {
        return (c >= 0x202a && c <= 0x202e) || (c >= 0x2066 && c <= 0x2069)
                || c == 0x200e || c == 0x200f || c == 0x061c;
    }
}
