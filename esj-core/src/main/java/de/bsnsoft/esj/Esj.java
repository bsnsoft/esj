package de.bsnsoft.esj;

import java.util.Objects;

/**
 * The fixed envelope constants of the EN16931 Semantic JSON format, the one
 * transformation the format applies to string content, and the two string rules an
 * implementation needs before it has a registry: the edition grammar of the
 * specification, section 4.4 and the message escaping of section 9.5.
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
     * Returns a fragment of document content in the form a message may carry it: at most
     * {@code maxLength} characters of it, with every character that would steer a
     * terminal, break the line or close a quotation replaced by an escape.
     *
     * <p>A backslash becomes {@code \\}, a quotation mark {@code \"}, a line feed
     * {@code \n}, a carriage return {@code \r} and a tab {@code \t}; every other C0
     * control, the delete character and the bidirectional formatting characters become
     * {@code \}{@code uXXXX}. That list is the one the specification, section 9.5
     * requires of a finding message, and section 12.6 is the reason for it: a value is
     * content a stranger wrote, an escape sequence inside one rewrites the line a
     * terminal shows, and a quotation mark inside one forges the rest of a location. The
     * bound is there for the same reason: a hostile document does not get to decide how
     * long a log line is. A fragment that was cut ends in three dots, and the cut never
     * falls between the two halves of a surrogate pair.
     *
     * @param value     the fragment as the document carries it
     * @param maxLength the greatest number of characters of {@code value} to reproduce
     * @return the fragment as a message may carry it
     * @throws IllegalArgumentException if {@code maxLength} is not positive
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public static String forMessage(String value, int maxLength) {
        Objects.requireNonNull(value, "value");
        if (maxLength < 1) {
            throw new IllegalArgumentException("a message excerpt is at least one character long");
        }
        if (value.length() <= maxLength) {
            return escape(value);
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return escape(value.substring(0, end)) + "...";
    }

    /**
     * Returns a fragment of document content in the form a finding's {@code subject} may
     * carry it: escaped exactly as {@link #forMessage(String, int)} escapes it, and
     * <strong>whole</strong>.
     *
     * <p>A subject is a structured field a program reads, not a line a person reads
     * (specification, section 9.5): it is what tells two findings apart whose {@code path}
     * is empty because the member name they are about is no semantic path, and a subject
     * held to an excerpt would let two long names that share their first characters
     * produce two findings nothing can distinguish. The bound of section 12.6 is about the
     * length of a log line, and a log line is the message.
     *
     * @param value the fragment as the document carries it
     * @return the fragment escaped, in full
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static String forSubject(String value) {
        Objects.requireNonNull(value, "value");
        return escape(value);
    }

    /**
     * Holds an already escaped fragment — a subject of {@link #forSubject(String)}, or a
     * location built out of several of them — to the length a message may carry, so that
     * a hostile document does not decide how long a log line is (specification, section
     * 12.6).
     *
     * <p>The cut falls on the boundary of an escape sequence and never inside one, because
     * half of a {@code \}{@code uXXXX} is neither the character it stood for nor an escape
     * a reader of the line can undo. A fragment that was cut ends in three dots.
     *
     * @param escaped   the fragment, already escaped
     * @param maxLength the greatest number of characters to reproduce
     * @return the fragment as a message may carry it
     * @throws IllegalArgumentException if {@code maxLength} is not positive
     * @throws NullPointerException     if {@code escaped} is {@code null}
     */
    public static String abbreviated(String escaped, int maxLength) {
        Objects.requireNonNull(escaped, "escaped");
        if (maxLength < 1) {
            throw new IllegalArgumentException("a message excerpt is at least one character long");
        }
        if (escaped.length() <= maxLength) {
            return escaped;
        }
        int at = 0;
        int cut = 0;
        while (at < escaped.length()) {
            int unit = 1;
            if (escaped.charAt(at) == '\\' && at + 1 < escaped.length()) {
                unit = escaped.charAt(at + 1) == 'u' ? 6 : 2;
            }
            if (at + unit > maxLength) {
                break;
            }
            at += unit;
            cut = at;
        }
        if (cut > 0 && Character.isHighSurrogate(escaped.charAt(cut - 1))) {
            cut--;
        }
        return escaped.substring(0, cut) + "...";
    }

    private static String escape(String value) {
        StringBuilder text = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> text.append("\\\\");
                case '"' -> text.append("\\\"");
                case '\n' -> text.append("\\n");
                case '\r' -> text.append("\\r");
                case '\t' -> text.append("\\t");
                default -> {
                    if (steersATerminal(c)) {
                        text.append(String.format("\\u%04x", (int) c));
                    } else {
                        text.append(c);
                    }
                }
            }
        }
        return text.toString();
    }

    /**
     * Tells whether a character steers a terminal rather than saying something: a C0
     * control, the delete character, or one of the bidirectional formatting characters
     * that reorder the text around them.
     */
    private static boolean steersATerminal(char c) {
        return c < 0x20
                || c == 0x7F
                || c == '‎' || c == '‏'
                || (c >= '‪' && c <= '‮')
                || (c >= '⁦' && c <= '⁩');
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
