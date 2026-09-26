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
 * @param minorUnits the number of fraction digits the publisher gives a code, for the lists
 *                  that publish one; empty for every other list
 */
public record CodeList(String listId,
                       String name,
                       String publisher,
                       String source,
                       String retrieved,
                       Map<String, String> entries,
                       Map<String, Integer> minorUnits) {

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
     * @param minorUnits the number of fraction digits the publisher gives a code
     * @throws NullPointerException if a part is {@code null}
     */
    public CodeList {
        Objects.requireNonNull(listId, "listId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(publisher, "publisher");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(retrieved, "retrieved");
        entries = Map.copyOf(entries);
        minorUnits = Map.copyOf(minorUnits);
    }

    /**
     * Creates a snapshot of a list whose publisher gives no number of fraction digits.
     *
     * @param listId    the identifier a rule names the list by
     * @param name      the name of the list at its publisher
     * @param publisher who publishes the list
     * @param source    where the snapshot was taken from
     * @param retrieved the day it was taken, {@code YYYY-MM-DD}
     * @param entries   the code values with their descriptions
     */
    public CodeList(String listId, String name, String publisher, String source,
                    String retrieved, Map<String, String> entries) {
        this(listId, name, publisher, source, retrieved, entries, Map.of());
    }

    /**
     * Returns the number of fraction digits the publisher of this list gives a code.
     *
     * <p>One list of this repository publishes such a number: the currency list, whose
     * minor unit column says how many fraction digits an amount in that currency carries.
     * A rule that asks about it over a list that does not publish it, or over a code the
     * list does not have, gets no answer and reports nothing rather than guessing.
     *
     * @param code the code as the document spells it
     * @return the number of fraction digits, or an empty optional
     */
    public Optional<Integer> minorUnit(String code) {
        return Optional.ofNullable(minorUnits.get(code));
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
