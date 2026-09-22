package de.bsnsoft.esj;

/**
 * Comparison of strings by Unicode code point, which is the order the specification uses
 * wherever it sorts strings (section 7.6). It is also the lexicographic order of the
 * UTF-8 encodings of those strings, and it differs from {@code String.compareTo}, which
 * compares UTF-16 code units and places every supplementary code point below the range
 * U+E000 to U+FFFF.
 */
final class CodePoints {

    private CodePoints() {
        throw new AssertionError("no instances");
    }

    /**
     * Compares two strings by Unicode code point.
     *
     * @param left  the first string
     * @param right the second string
     * @return a negative number, zero or a positive number as {@code left} sorts before,
     *         equal to or after {@code right}
     */
    static int compare(String left, String right) {
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            int a = left.codePointAt(i);
            int b = right.codePointAt(j);
            if (a != b) {
                return Integer.compare(a, b);
            }
            i += Character.charCount(a);
            j += Character.charCount(b);
        }
        return Integer.compare(left.length() - i, right.length() - j);
    }
}
