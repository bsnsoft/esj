package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticPath;

/**
 * A compiled expression of the rule language.
 *
 * <p>Compilation is where everything that can be decided without a document is decided: the
 * paths are resolved against the registry, the semantic data types are looked up, the
 * operands of an arithmetic operator are known to be numbers, a literal is turned into the
 * number or the date it will be compared with, and a code list is fetched from the pack. A
 * compiled expression therefore does no lookup, no parsing of its own text and no guessing
 * at run time; it reads values and combines them.
 *
 * <p>That is not an optimisation for its own sake. A rule of the EN 16931 pack is evaluated
 * once per invoice line, and an invoice of three hundred thousand lines is a real input for
 * this project, so anything done per evaluation is done three hundred thousand times. It is
 * also the difference between a pack that fails to start and a pack that fails on the
 * thousandth invoice: a path nobody can resolve is a defect of the pack, and the pack
 * refuses to compile rather than reporting it as a defect of a document.
 */
@FunctionalInterface
interface Expression {

    /**
     * Evaluates the expression.
     *
     * @param evaluation the run: the document, the index and what has been read so far
     * @param base       the business group instance the rule is evaluated at, or the root
     * @return the value, which may be {@link RuleValue#ABSENT}
     * @throws Undecided if a value the expression reads does not spell what its semantic
     *                   data type requires
     */
    RuleValue evaluate(Evaluation evaluation, SemanticPath base);
}
