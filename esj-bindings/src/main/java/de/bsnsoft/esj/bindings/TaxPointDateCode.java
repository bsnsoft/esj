package de.bsnsoft.esj.bindings;

import java.util.Map;

/**
 * The two spellings of the value added tax point date code, BT-8, and the translation
 * between them.
 *
 * <p>EN 16931-1 gives BT-8 a restriction of UNTDID 2005 and the cross industry invoice
 * writes it in {@code ram:DueDateTypeCode}, whose code list in that syntax is UNTDID 2475.
 * The two lists name the same three events with different numbers, and the validation
 * artefacts of the profile say which each syntax admits: {@code 3}, {@code 35} and
 * {@code 432} in a UBL document, {@code 5}, {@code 29} and {@code 72} in a cross industry
 * invoice. A semantic document holds the code of the standard, so the reader translates on
 * the way in and the writer on the way out, and BT-8 means the same thing whichever syntax
 * the invoice arrived in.
 *
 * <p>The binding table marks the element this applies to with the flag
 * {@code code-list-2475}; nothing else in the tables carries a code list of its own.
 * {@code TaxPointDateCodeTest} reads the two admitted sets out of the validation artefacts
 * of the pack and holds this table to them.
 *
 * <p>A code that is in neither list is left as it stands. Translating it would invent a
 * value, and leaving it is what lets the rule packs fault it by name.
 */
final class TaxPointDateCode {

    /** The code of the standard for each code of the syntax. */
    private static final Map<String, String> TO_STANDARD = Map.of(
            "5", "3",
            "29", "35",
            "72", "432");

    /** The code of the syntax for each code of the standard. */
    private static final Map<String, String> TO_SYNTAX = Map.of(
            "3", "5",
            "35", "29",
            "432", "72");

    private TaxPointDateCode() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the UNTDID 2005 code of an event a cross industry invoice states in
     * UNTDID 2475, or the content itself where it is neither.
     *
     * @param content the content of the element
     * @return the code of the standard
     */
    static String toStandard(String content) {
        return TO_STANDARD.getOrDefault(content.strip(), content);
    }

    /**
     * Returns the UNTDID 2475 code a cross industry invoice states an event in, or the
     * content itself where it is no code of the standard.
     *
     * @param content the value of the business term
     * @return the code of the syntax
     */
    static String toSyntax(String content) {
        return TO_SYNTAX.getOrDefault(content.strip(), content);
    }

    /** Returns the codes of the standard this table translates, for a test to check. */
    static Map<String, String> ofStandard() {
        return TO_SYNTAX;
    }
}
