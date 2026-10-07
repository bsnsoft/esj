package de.bsnsoft.esj;

import java.util.Objects;

/**
 * The fixed envelope constants of the EN16931 Semantic JSON format, the one
 * transformation the format applies to string content, and the two string rules an
 * implementation needs before it has a registry: the edition grammar of the
 * specification, section 4.4 and the characters the message escaping of section 9.5
 * escapes.
 *
 * <p>Every ESJ document carries {@link #FORMAT} and {@link #VERSION}. The third fixed
 * string, {@link #SEMANTIC_MODEL}, is the edition this implementation ships a registry
 * for and therefore the edition it reads and writes by default; it is not the only value
 * the format defines for that member.
 */
public final class Esj {

    /** Value of the {@code format} member of every ESJ document. */
    public static final String FORMAT = "EN16931-Semantic-JSON";

    /** Value of the {@code version} member produced by this implementation. */
    public static final String VERSION = "0.1";

    /**
     * The default edition of the semantic model: the one the bundled registry describes
     * (specification, sections 4.4 and 10).
     *
     * <p>A document names an edition in its {@code semanticModel} member, and which
     * editions an implementation holds a registry for is a property of the implementation
     * rather than of the format. This one ships one registry, so this is the edition it
     * writes and the one it can validate against. It is not the only edition it reads: a
     * reader asks of {@code semanticModel} the grammar of section 4.4 and nothing more,
     * and a validator with no registry for the edition a document names reports that the
     * model layers were not checked (section 9.2).
     */
    public static final String SEMANTIC_MODEL = "EN16931-1:2017+A1:2019/AC:2020";

    /** The provisional, unregistered media type of an ESJ document. */
    public static final String MEDIA_TYPE = "application/vnd.en16931-semantic+json";

    /** The customary file name extension of an ESJ document. */
    public static final String FILE_EXTENSION = ".esj.json";

    private Esj() {
        throw new AssertionError("no instances");
    }

    /**
     * Normalizes CR LF and a lone CR to a single LF, which is the only transformation ESJ
     * applies to string content (specification, section 6.8). Nothing else is touched: no
     * trimming, no collapsing of whitespace, no Unicode normalization and no case folding.
     *
     * <p>This is the single implementation of that rule. A reader applies it to every
     * string inside {@code values} before it measures the string against the limits of
     * section 12.2, and {@link SemanticValue} applies it to the strings it is built from,
     * so that a value built by hand and the same value read from a document are equal.
     * Strings inside {@code extensions} are not normalized.
     *
     * @param value the string to normalize
     * @return the normalized string, or the argument itself when it carries no CR
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static String normalizeLineEndings(String value) {
        if (value.indexOf('\r') < 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r') {
                builder.append('\n');
                if (i + 1 < value.length() && value.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /**
     * Tells whether a string is an edition of the semantic model, as the grammar of the
     * specification, section 4.4 writes it: a model token and a year, optionally followed
     * by amendments and corrigenda, with no spaces. {@link #SEMANTIC_MODEL} is one such
     * string, and so is {@code EN16931-1:2026}.
     *
     * <p>The grammar is checked by hand rather than by a regular expression, so that a
     * long string a stranger wrote costs a single pass and never a backtracking search.
     * Whether an implementation has a registry for the edition is a different question
     * and is answered by {@code de.bsnsoft.esj.model.Registry}.
     *
     * @param value the string to test
     * @return {@code true} if the string matches the edition grammar
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static boolean isEdition(String value) {
        Objects.requireNonNull(value, "value");
        int at = modelToken(value, 0);
        if (at < 0) {
            return false;
        }
        if (at < value.length() && value.charAt(at) == '+') {
            at = amendment(value, at + 1);
            if (at < 0) {
                return false;
            }
        }
        if (at < value.length() && value.charAt(at) == '/') {
            at = corrigendum(value, at + 1);
            if (at < 0) {
                return false;
            }
        }
        while (at < value.length()) {
            if (value.charAt(at) != '+') {
                return false;
            }
            at = amendment(value, at + 1);
            if (at < 0) {
                return false;
            }
            if (at < value.length() && value.charAt(at) == '/') {
                at = corrigendum(value, at + 1);
                if (at < 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Tells whether a character steers a terminal rather than saying something, which makes
     * it a character that no text of this implementation writes out as it stands: a C0
     * control, the delete character, a C1 control (U+0080 to U+009F, among them U+0085 NEXT
     * LINE), the line separator U+2028, the paragraph separator U+2029, or one of the
     * bidirectional formatting characters of {@link #isBidiControl(int)}.
     *
     * <p>This is the one definition of that set. The message escaping of this
     * implementation writes each of these characters as an escape (specification, section
     * 9.5), and every other text this
     * implementation writes a fragment of a document into — the messages of the rule engine
     * and of the PDF container checks, the lines of the command line tool — asks the same
     * question here, so that no output lets through what another one escapes. A C0 or C1
     * control rewrites the line a terminal shows; the two separators and U+0085 start a new
     * line where a reader of line-oriented output expects none; a bidirectional control
     * reverses the reading order of the text around it, so that a number or an identifier
     * reads differently from what the document carries.
     *
     * <p>The line feed, the carriage return and the tab belong to the set as well. An
     * escaper writes those three by name ({@code \n}, {@code \r}, {@code \t}) rather than
     * by code point, which is a matter of spelling and not of membership.
     *
     * @param codePoint a Unicode code point, or a UTF-16 code unit
     * @return {@code true} if the character is written as an escape
     */
    public static boolean steersATerminal(int codePoint) {
        return codePoint < 0x20
                || (codePoint >= 0x7F && codePoint <= 0x9F)
                || codePoint == 0x2028
                || codePoint == 0x2029
                || isBidiControl(codePoint);
    }

    /**
     * Tells whether a character is one of the bidirectional formatting characters: U+061C
     * ARABIC LETTER MARK, U+200E LEFT-TO-RIGHT MARK, U+200F RIGHT-TO-LEFT MARK, the
     * embeddings and overrides U+202A to U+202E, and the isolates U+2066 to U+2069. They are
     * the characters of the Unicode property {@code Bidi_Control}: invisible, and each of
     * them changes the order in which the characters around it are displayed.
     *
     * @param codePoint a Unicode code point, or a UTF-16 code unit
     * @return {@code true} if the character is a bidirectional formatting character
     */
    public static boolean isBidiControl(int codePoint) {
        return codePoint == 0x061C
                || codePoint == 0x200E
                || codePoint == 0x200F
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    /**
     * Reads {@code model-token}: one or more alphanumeric groups joined by hyphens, a
     * colon and a four-digit year.
     *
     * @return the position after the year, or -1 if the string does not start with one
     */
    private static int modelToken(String value, int from) {
        int at = alphanumeric(value, from);
        if (at < 0) {
            return -1;
        }
        while (at < value.length() && value.charAt(at) == '-') {
            at = alphanumeric(value, at + 1);
            if (at < 0) {
                return -1;
            }
        }
        if (at >= value.length() || value.charAt(at) != ':') {
            return -1;
        }
        return year(value, at + 1);
    }

    /**
     * Reads {@code amendment}: the letter A, at least one digit, a colon and a year.
     *
     * @return the position after the year, or -1
     */
    private static int amendment(String value, int from) {
        if (from >= value.length() || value.charAt(from) != 'A') {
            return -1;
        }
        int at = digits(value, from + 1, 1);
        if (at < 0 || at >= value.length() || value.charAt(at) != ':') {
            return -1;
        }
        return year(value, at + 1);
    }

    /**
     * Reads {@code corrigendum}: the letters AC, any number of digits, a colon and a
     * year.
     *
     * @return the position after the year, or -1
     */
    private static int corrigendum(String value, int from) {
        if (from + 1 >= value.length() || value.charAt(from) != 'A' || value.charAt(from + 1) != 'C') {
            return -1;
        }
        int at = digits(value, from + 2, 0);
        if (at >= value.length() || value.charAt(at) != ':') {
            return -1;
        }
        return year(value, at + 1);
    }

    /** Reads exactly four digits. */
    private static int year(String value, int from) {
        return digits(value, from, 4) == from + 4 ? from + 4 : -1;
    }

    /** Reads at least {@code least} digits and then as many more as there are. */
    private static int digits(String value, int from, int least) {
        int at = from;
        while (at < value.length() && value.charAt(at) >= '0' && value.charAt(at) <= '9') {
            at++;
        }
        return at - from < least ? -1 : at;
    }

    /** Reads at least one ASCII letter or digit. */
    private static int alphanumeric(String value, int from) {
        int at = from;
        while (at < value.length() && isAlphanumeric(value.charAt(at))) {
            at++;
        }
        return at == from ? -1 : at;
    }

    private static boolean isAlphanumeric(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
