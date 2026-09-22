package de.bsnsoft.esj.rules;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One code list, as one publisher published it on one day.
 *
 * <p>A code list changes. A currency is withdrawn, a country is added, a reason code is
 * retired; and a rule that asks whether a code is on a list is asking about a list that has
 * a date. If the engine read the list of the day it runs, the same invoice would be valid
 * in one year and invalid in the next without anyone having changed anything, and a report
 * from the archive could not be reproduced. So a list is a <em>snapshot</em>: a file with a
 * date in its name, named by the pack manifest, frozen for as long as that pack version
 * exists. A newer list is a newer pack.
 *
 * <p>A snapshot records where it came from, which is the other half of the same idea. The
 * membership question has one right answer per publisher per day, and a reader who wants to
 * check the answer needs to know which publication was asked. The provenance of every
 * snapshot in the repository is written out in the {@code SOURCES.md} beside them, and no
 * snapshot is taken from another implementation's copy of a list: a copy of a copy has a
 * licence and an error history of its own.
 *
 * @param listId    the identifier a rule names the list by, for example {@code iso-4217}
 * @param name      the name of the list at its publisher
 * @param publisher who publishes the list
 * @param source    where the snapshot was taken from
 * @param retrieved the day it was taken, {@code YYYY-MM-DD}, which is also the file name
 * @param entries   the code values, each with the description the publisher gives it or
 *                  the empty string where the publisher gives none
 */
public record CodeList(String listId,
                       String name,
                       String publisher,
                       String source,
                       String retrieved,
                       Map<String, String> entries) {

    /**
     * Copies the entries and checks that every part is present.
     *
     * @param listId    the identifier a rule names the list by, for example {@code iso-4217}
     * @param name      the name of the list at its publisher
     * @param publisher who publishes the list
     * @param source    where the snapshot was taken from
     * @param retrieved the day it was taken, {@code YYYY-MM-DD}, which is also the file name
     * @param entries   the code values, each with the description the publisher gives it or
     *                  the empty string where the publisher gives none
     * @throws NullPointerException if a part is {@code null}
     */
    public CodeList {
        Objects.requireNonNull(listId, "listId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(publisher, "publisher");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(retrieved, "retrieved");
        entries = Map.copyOf(entries);
    }

    /**
     * Tells whether a code is on this list.
     *
     * <p>The comparison is exact, code point by code point. A code list is a list of
     * codes and not of their spellings: a lower case currency code is not a currency code,
     * and repairing it here would hide a defect that the party who wrote it has to fix.
     *
     * @param code the code as the document spells it
     * @return whether the list has an entry with exactly this value
     */
    public boolean contains(String code) {
        return entries.containsKey(code);
    }

    /**
     * Returns the description the publisher gives a code.
     *
     * @param code the code
     * @return the description, empty where the list has the code without one, and an empty
     *         optional where the list does not have the code
     */
    public Optional<String> describe(String code) {
        return Optional.ofNullable(entries.get(code));
    }

    /**
     * Returns how many codes the list has.
     *
     * @return the number of entries
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns the list, its publisher and the day the snapshot was taken.
     *
     * @return a short description of the snapshot
     */
    @Override
    public String toString() {
        return listId + " (" + publisher + ", " + retrieved + ", " + entries.size() + " codes)";
    }
}
