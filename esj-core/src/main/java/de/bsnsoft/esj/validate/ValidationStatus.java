package de.bsnsoft.esj.validate;

/**
 * The three states a validation result can be in (specification, section 9.5).
 *
 * <p>A check that does not end in a yes ends in one of two ways — <em>the document is
 * wrong</em> and <em>I could not tell</em> — and a result that folds the two together is
 * read as the wrong one of them by whoever reads it next. The three states keep them
 * apart, and {@link ValidationResult} decides which one a result carries.
 */
public enum ValidationStatus {

    /**
     * All three layers L1, L2 and L3 were evaluated, no error was found, and no finding
     * records something that could not be evaluated
     * ({@link FindingCode#recordsSomethingNotEvaluated()}): the document is a conformant
     * ESJ document in the sense of the specification, section 3.1. A result that says of
     * one path that it could not be checked has not checked the document, whatever
     * severity that finding carries.
     *
     * <p>This says nothing about the business rules of EN 16931-1, which are a separate
     * layer and are not checked in this version (specification, sections 9.4 and 9.5).
     */
    VALID,

    /**
     * An error was found. A defect found is a defect whatever else was not reached, so
     * this state outranks {@link #INDETERMINATE}.
     */
    INVALID,

    /**
     * Nothing was found to be wrong, and something was not evaluated: a layer was not
     * evaluated, a limit stopped the run, or a registry the document needed was not
     * available. The result names what and why.
     */
    INDETERMINATE
}
