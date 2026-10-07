package de.bsnsoft.esj.internal;

import de.bsnsoft.esj.Esj;
import java.util.Objects;

/**
 * Writes fragments of document content into messages: escaped as the specification,
 * section 9.5 requires of a finding message, and held to a length a log line can carry.
 *
 * <p>The characters it escapes are the set of {@link Esj#steersATerminal(int)}, which is
 * the API; this class is the one implementation of the escaping that the modules of this
 * project share.
 */
public final class Messages {

    private Messages() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns a fragment of document content in the form a message may carry it: at most
     * {@code maxLength} characters of it, with every character that would steer a
     * terminal, break the line or close a quotation replaced by an escape.
     *
     * <p>A backslash becomes {@code \\}, a quotation mark {@code \"}, a line feed
     * {@code \n}, a carriage return {@code \r} and a tab {@code \t}; every other character
     * of {@link Esj#steersATerminal(int)} — the C0 and C1 controls, the delete character, the
     * line and paragraph separators and the bidirectional formatting characters — becomes
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
                    if (Esj.steersATerminal(c)) {
                        text.append(String.format("\\u%04x", (int) c));
                    } else {
                        text.append(c);
                    }
                }
            }
        }
        return text.toString();
    }
}
