package de.bsnsoft.esj.bindings;

/**
 * The characters an XML 1.0 document may carry, and what a writer does with the others.
 *
 * <p>The semantic model is wider than the syntaxes it is written into. A text value of an
 * ESJ document is any sequence of Unicode scalar values (specification, section 12.6): a
 * control character other than tab, line feed and carriage return is a value a reader
 * accepts and a document may hold. XML 1.0 has no such character and no escape for one —
 * {@code &#x7;} is as ill-formed as the raw byte — so a writer that puts the value into
 * the syntax unchanged produces a file no parser reads back.
 *
 * <p>The rule of the writers is therefore one rule, and it is the rule the writers hold to
 * everywhere else: write what the syntax can carry and say what it could not. A character
 * outside the {@code Char} production of XML 1.0 is left out of the output and the value
 * it stood in is named in the {@link WriteReport}, with the code point, as a
 * {@link WriteNote.Kind#CHARACTER_NOT_REPRESENTABLE} note. Refusing the whole document was
 * the other candidate and was not taken: a converter that refuses a document because one
 * name carries a stray {@code U+0007} gives its caller nothing to work with, while a note
 * naming the term and the code point gives the caller both the file and the question.
 *
 * <p>The characters left out are the ones the production excludes: the C0 controls apart
 * from {@code U+0009}, {@code U+000A} and {@code U+000D}; a surrogate code unit that is
 * not half of a well-formed pair, which is no scalar value at all; and the two
 * non-characters {@code U+FFFE} and {@code U+FFFF}. Everything else a Java string can hold
 * is written as it stands.
 */
final class XmlCharacters {

    private XmlCharacters() {
        throw new AssertionError("no instances");
    }

    /**
     * Tells whether a string holds a character XML 1.0 cannot carry.
     *
     * @param text the string
     * @return {@code true} where {@link #scrub(String)} would shorten it
     */
    static boolean hasUnrepresentable(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (pairAt(text, i)) {
                i++;
                continue;
            }
            if (!isChar(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the string without the characters XML 1.0 cannot carry.
     *
     * @param text the string
     * @return the string itself where it holds no such character, and a shortened copy
     *         otherwise
     */
    static String scrub(String text) {
        if (!hasUnrepresentable(text)) {
            return text;
        }
        StringBuilder kept = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (pairAt(text, i)) {
                kept.append(text.charAt(i)).append(text.charAt(i + 1));
                i++;
                continue;
            }
            char c = text.charAt(i);
            if (isChar(c)) {
                kept.append(c);
            }
        }
        return kept.toString();
    }

    /**
     * Returns the code points a string holds that XML 1.0 cannot carry, written as
     * {@code U+XXXX} and separated by commas.
     *
     * <p>It is what a note says instead of the content: a code point names the defect
     * without reproducing the value it stood in. Each one is named once, in the order it
     * first occurs.
     *
     * @param text the string
     * @return the list, which is empty where the string holds no such character
     */
    static String describe(String text) {
        StringBuilder named = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (pairAt(text, i)) {
                i++;
                continue;
            }
            char c = text.charAt(i);
            if (isChar(c)) {
                continue;
            }
            String point = String.format("U+%04X", (int) c);
            if (named.indexOf(point) < 0) {
                named.append(named.isEmpty() ? "" : ", ").append(point);
            }
        }
        return named.toString();
    }

    /** Tells whether a well-formed surrogate pair starts at an index. */
    private static boolean pairAt(String text, int index) {
        return Character.isHighSurrogate(text.charAt(index))
                && index + 1 < text.length()
                && Character.isLowSurrogate(text.charAt(index + 1));
    }

    /**
     * Tells whether one code unit, outside a well-formed surrogate pair, is a character of
     * the {@code Char} production of XML 1.0.
     */
    private static boolean isChar(char c) {
        if (c < 0x20) {
            return c == 0x09 || c == 0x0A || c == 0x0D;
        }
        return c < 0xD800 || (c >= 0xE000 && c <= 0xFFFD);
    }
}
