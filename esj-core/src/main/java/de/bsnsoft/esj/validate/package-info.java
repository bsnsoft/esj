/**
 * Structural validation against the registry: the layers L2 (model) and L3 (cardinality)
 * of the specification, section 9, the findings they report, and the result that carries
 * the verdict. Invalid content is reported as findings, never as exceptions, so that a
 * document with fifty problems produces fifty findings in one pass.
 *
 * <p>The verdict is a {@link de.bsnsoft.esj.validate.ValidationResult} and has
 * three states, because a check can end in three ways: the document satisfies the layers,
 * it fails one of them, or something could not be evaluated. An empty finding list is not
 * conformance — a component that evaluated nothing also reports nothing — so the result
 * names the layers it evaluated and, for each one it did not, the reason. A caller reads
 * {@link de.bsnsoft.esj.validate.ValidationResult#status()} and never the
 * findings alone.
 */
package de.bsnsoft.esj.validate;
