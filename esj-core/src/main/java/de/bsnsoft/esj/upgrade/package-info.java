/**
 * Moving a document from one edition of the semantic model to another.
 *
 * <p>The knowledge of what changes between two editions is data:
 * {@code model/en16931/upgrade-2017-2026.json} names the paths that move, the terms an
 * edition adds or removes and the points an upgrade cannot decide on its own.
 * {@link de.bsnsoft.esj.upgrade.UpgradeMapping} reads it and
 * {@link de.bsnsoft.esj.upgrade.EditionUpgrade} applies it. Nothing about the
 * two editions is written in this package, so a later edition arrives as one more file.
 *
 * <p>Two rules hold whichever way the upgrade runs. Nothing is repaired: a value that the
 * target edition bounds differently is reported and never rounded, and a component the
 * target edition now requires is reported and never invented. Nothing is lost in silence:
 * where the target edition has no address for a value, the run refuses and names every
 * such path, and a caller who accepts the loss names the paths that may be dropped and
 * finds each of them in the report.
 */
package de.bsnsoft.esj.upgrade;
