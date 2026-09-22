package de.bsnsoft.esj.rules;

/**
 * A rule that cannot be decided on this document, because a value it reads does not spell
 * what its semantic data type requires.
 *
 * <p>An amount that reads {@code 1.000,00} is not a number this engine will guess at. It is
 * a defect of the document, the structural validator reports it at layer L2 as
 * {@code ESJ-L2-DECIMAL} with the path it is at, and a rule that reads it stops there: it
 * produces one {@link RuleSeverity#INFO} finding saying which rule was not decided and why,
 * and no verdict. Reporting the same defect a second time as a failed business rule would
 * make one problem look like two and would put the blame in the wrong place — the rule did
 * not fail, it was never given the number it is about.
 *
 * <p>The exception travels no further than the rule instance it was raised in. Every other
 * rule, and the same rule at every other business group instance, is evaluated as usual.
 */
final class Undecided extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the signal.
     *
     * @param message what could not be read, in English, with the offending text escaped
     */
    Undecided(String message) {
        super(message, null, false, false);
    }
}
