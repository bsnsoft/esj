/**
 * The rules of the EN 16931-1:2026 pack that the rule language cannot express.
 *
 * <p>Everything in this package is a fact of that edition, which is why it is a package of
 * its own: {@code model/en16931/2026.paths} lists it, the Maven profile
 * {@code without-edition-2026} builds without it, and nothing outside it but
 * {@code de.bsnsoft.esj.rules.en16931.v2026.En16931V2026Pack} names a class in it. The
 * package is internal: it is not part of the API and may change in any release.
 *
 * <p>No official validation artefact is published for this edition. Every rule here is
 * weighed by hand-computed cases, its statement is written from the clause of the standard
 * it names, and no text of the standard is reproduced.
 */
package de.bsnsoft.esj.rules.internal.en16931.v2026;
