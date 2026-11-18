package de.bsnsoft.esj.internal;

import java.util.Base64;

/**
 * Decodes the base64 of an attachment a document carried, with every quantum complete
 * before the runtime's decoder sees it.
 *
 * <p>{@link Base64.Decoder#decode(String)} is the method a Java runtime replaces by a
 * vectorised routine of its own once it is hot, and on x86-64 processors with AVX-512 VBMI
 * the routine of the October 2026 releases and earlier writes past the end of its array
 * when the text ends in an incomplete group of one to three characters (CVE-2026-114938).
 * The text of an attachment is something a stranger wrote, so it is checked here first,
 * by the rules of the decoder itself: the base64 alphabet, at most two padding characters
 * and only at the end, a padded text whose length is a multiple of four, and an unpadded
 * text whose last group has two or three characters. That is exactly the set of texts the
 * runtime's decoder accepts, so nothing it decodes today is refused here and nothing it
 * refuses is decoded. An unpadded text is padded before it is handed on, which yields the
 * bytes the decoder yields for it unpadded; every text the decoder sees is then a whole
 * number of four-character groups, which the vectorised routine decodes correctly.
 */
public final class Base64Text {

    private Base64Text() {
        throw new AssertionError("no instances");
    }

    /**
     * Decodes base64 text without line breaks or other white space, as {@link Base64#getDecoder()}
     * decodes it.
     *
     * @param text the base64 text
     * @return the decoded bytes
     * @throws IllegalArgumentException if the decoder of the runtime would refuse the text
     */
    public static byte[] decode(String text) {
        String whole = whole(text);
        if (whole == null) {
            throw new IllegalArgumentException("not a base64 text: " + reason(text));
        }
        return Base64.getDecoder().decode(whole);
    }

    /**
     * Returns the text as a whole number of four-character groups, padded where the last
     * group was short, or nothing where the decoder of the runtime would refuse it.
     *
     * @param text the base64 text
     * @return the padded text, or {@code null}
     */
    static String whole(String text) {
        int length = text.length();
        int end = length;
        while (end > 0 && text.charAt(end - 1) == '=') {
            end--;
        }
        int padding = length - end;
        if (padding > 2) {
            return null;
        }
        for (int i = 0; i < end; i++) {
            if (!alphabet(text.charAt(i))) {
                return null;
            }
        }
        int tail = end % 4;
        if (padding > 0) {
            // The two forms of a padded last group: two characters and two padding
            // characters, three characters and one.
            return tail + padding == 4 && tail >= 2 ? text : null;
        }
        return switch (tail) {
            case 0 -> text;
            case 2 -> text + "==";
            case 3 -> text + "=";
            default -> null;
        };
    }

    private static boolean alphabet(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '+' || c == '/';
    }

    private static String reason(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '=') {
            end--;
        }
        if (text.length() - end > 2) {
            return "more than two padding characters";
        }
        for (int i = 0; i < end; i++) {
            if (!alphabet(text.charAt(i))) {
                return "a character outside the base64 alphabet at offset " + i;
            }
        }
        return "the last group is incomplete";
    }
}
