package de.bsnsoft.esj.bindings;

/**
 * The flags of a binding table that change what a reader or a writer does with a value.
 *
 * <p>Most flags of {@code model/bindings} describe the binding and are documentation:
 * that the syntax lets an element repeat where the semantic model allows the term once,
 * that a term is reached through an element standing for something else. Four of them
 * decide behaviour, and those four are named here so that a reader or a writer asks for
 * a constant rather than for a string it spelled itself.
 * {@code model/bindings/README.md} carries the meaning of every flag, these four
 * included.
 */
final class Flags {

    /**
     * The bound element is a CII date string whose {@code format} attribute is 102, so
     * its content is eight digits rather than an ISO date.
     */
    static final String DATE_FORMAT_102 = "date-format-102";

    /**
     * The value of this term is written into the value of another term at the same
     * element, as a prefix between two number signs. The UBL note subject code is the one
     * case; see {@link ReaderMode}.
     */
    static final String SUBJECT_CODE_PREFIX = "subject-code-prefix";

    /**
     * The bound element states the code in UNTDID 2475 while the term's code list is
     * UNTDID 2005. {@link TaxPointDateCode} translates between the two.
     */
    static final String CODE_LIST_2475 = "code-list-2475";

    /**
     * The source model states that this syntax has no representation for the term at all.
     * On a business group it means the group has no element of its own while the terms
     * inside it are bound where the standard puts them.
     */
    static final String NOT_REPRESENTED = "not-represented";

    private Flags() {
        throw new AssertionError("no instances");
    }
}
