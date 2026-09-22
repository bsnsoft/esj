package de.bsnsoft.esj.syntax;

/**
 * What the syntax engine says about a document: the three states of the specification,
 * section 9.5, as this engine reaches them.
 *
 * <p>Two of them are about the document and the third is about the run. A run that could
 * not reach an answer at all — a resource or time limit was reached, or the document is of
 * a syntax no pack binds — does not return a verdict but raises an exception, so that a
 * document nobody managed to check never reads as an invalid one.
 */
public enum Verdict {

    /**
     * Every component the pack holds for this document ran and none of them made a fatal
     * finding. Warnings may still be listed.
     */
    VALID,

    /** At least one fatal finding. A defect found is a defect whatever else was not run. */
    INVALID,

    /**
     * Nothing fatal was found, and the document names a specification this pack carries no
     * rules for, so a rule set that should have judged it did not run
     * ({@link SyntaxReport#profileRulesMissing()}).
     *
     * <p>A document that names EN 16931 and no core invoice usage specification is not this
     * case: no CIUS rules apply to it, and a check that ran everything that applies is
     * complete.
     */
    INDETERMINATE
}
