/**
 * The code lists of EN 16931-1 as Java enums, generated from the dated snapshots of the
 * rule pack {@code en16931} by {@code esj-generator} and checked in.
 *
 * <p>One enum per list a business term draws from. A constant carries the code, the name
 * its publisher gives the code and the identifier of the list it came from; the Javadoc of
 * the enum names the snapshot and the business terms of the registry that use the list.
 * {@code of(code)} finds a constant by its exact code, {@code resolve(code)} never fails
 * and yields a {@link de.bsnsoft.esj.invoice.code.CustomCode} for a code the
 * snapshot does not carry, and {@code custom(code)} makes one on purpose.
 *
 * <p>How a constant is named is decided by {@code model/enums.json} and written out there:
 * the code or the publisher's name is decomposed to its base letters, upper cased and
 * joined with underscores, and a name two codes would share goes to the first of them in
 * list order while the later one is suffixed with its own code. Nine VAT category codes
 * are named in that file instead, because their published names are sentences.
 *
 * <p>A newer code list is a newer snapshot, a newer pack version and newly generated
 * enums, never an edit here.
 */
package de.bsnsoft.esj.invoice.code;
