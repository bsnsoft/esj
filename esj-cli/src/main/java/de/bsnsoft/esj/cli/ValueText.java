package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.Canonicalizer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * How a single value is written on a line and in JSON.
 *
 * <p>A canonical value is not built here. It is cut out of what the canonicalizer
 * produced for a document that holds nothing but that one value, because the escaping of
 * a string is part of the canonical form of the specification, section 7.5 and belongs to
 * {@code esj-core}: a second spelling of it in a command line tool would be a second
 * thing to keep in step with the specification, and the first one to fall behind.
 *
 * <p>The one-line form is this module's own, and it is not canonical anything. A
 * business term may carry line breaks — BT-22 is the obvious one — and a listing that
 * gives one path per line, or a diff that gives one value per line, has to stay one line
 * per value or it stops being readable by the tools that read line-oriented output.
 */
final class ValueText {

    private ValueText() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the canonical bytes of one value, as it stands inside {@code values}: the
     * content as a JSON string where the value carries no supplementary component, and
     * the object with the content and those components where it carries one
     * (specification, section 6.1).
     *
     * @param path  the path the value lies at
     * @param value the value
     * @return the canonical serialization of the value alone
     */
    static byte[] canonicalObject(SemanticPath path, SemanticValue value) {
        byte[] semantic = Canonicalizer.canonicalSemanticBytes(
                SemanticDocument.builder().put(path, value).build());
        byte[] marker = (",\"values\":{\"" + path + "\":").getBytes(StandardCharsets.UTF_8);
        int start = indexOf(semantic, marker);
        if (start < 0 || semantic.length < start + marker.length + 3
                || semantic[semantic.length - 1] != '}'
                || semantic[semantic.length - 2] != '}') {
            throw new IllegalStateException(
                    "the canonicalizer wrote a values object of an unexpected shape for " + path);
        }
        return Arrays.copyOfRange(semantic, start + marker.length, semantic.length - 2);
    }

    /** Returns where a byte sequence begins inside another, or {@code -1}. */
    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns a text in double quotes, safe to write into a terminal.
     *
     * <p>It is what a message quotes an attachment name, a media type or any other
     * fragment of the document with. The fragment is chosen by whoever wrote the file, so
     * it may carry control characters, a line feed that would forge a second line of
     * output, or a bidirectional override that would reverse the sentence around it
     * (specification, section 12.6).
     *
     * @param text the fragment
     * @return the fragment escaped and in double quotes
     */
    static String quoted(String text) {
        return "\"" + oneLine(text).replace("\"", "\\\"") + "\"";
    }

    /**
     * Returns a string with every character that would break a line, move the cursor or
     * reverse the reading order replaced by an escape, so that one value stays on one
     * line and says on a terminal what the document carries.
     *
     * <p>A backslash becomes {@code \\}, a line feed {@code \n}, a carriage return
     * {@code \r} and a tab {@code \t}. Every other C0 control, the delete character and
     * the bidirectional formatting characters become {@code &#92;uXXXX}. The escaping stays
     * reversible, because the backslash is escaped with them.
     *
     * <p>This is not decoration. A value of an invoice is content a stranger wrote, and
     * the specification, section 12.6 is normative about where it may be put unescaped:
     * an escape sequence in a seller name rewrites the line a terminal shows, a backspace
     * deletes what came before it, and a right-to-left override reverses a domain name —
     * so {@code esj list} and {@code esj inspect}, the two commands whose answer to "what
     * did I just receive" a person reads off a screen, would show something the document
     * does not say. {@code esj get} without {@code --json} stays raw on purpose: its one
     * value is meant for {@code $(...)} and not for a terminal.
     *
     * @param text the value as the document carries it
     * @return the value on one line
     */
    static String oneLine(String text) {
        StringBuilder line = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> line.append("\\\\");
                case '\n' -> line.append("\\n");
                case '\r' -> line.append("\\r");
                case '\t' -> line.append("\\t");
                default -> {
                    if (isDangerous(c)) {
                        line.append(String.format("\\u%04x", (int) c));
                    } else {
                        line.append(c);
                    }
                }
            }
        }
        return line.toString();
    }

    /**
     * Tells whether a character steers a terminal rather than saying something: a C0
     * control, the delete character, or one of the bidirectional formatting characters
     * that reorder the text around them.
     */
    private static boolean isDangerous(char c) {
        return c < 0x20
                || c == 0x7F
                || c == '\u200E' || c == '\u200F'
                || (c >= '\u202A' && c <= '\u202E')
                || (c >= '\u2066' && c <= '\u2069');
    }
}
