package de.bsnsoft.esj.upgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What one upgrade did and what it leaves to the caller.
 *
 * <p>The notes are in the order the run made them: the addresses it rewrote, then the
 * points it cannot decide, then the reasons it refused. {@link #statements()} carries, for
 * every point a note names, the sentence the mapping file states the general case in, so
 * that a report shows the rule once and the paths under it.
 */
public final class UpgradeReport {

    private final String from;
    private final String to;
    private final List<UpgradeNote> notes;
    private final Map<String, String> statements;

    UpgradeReport(String from, String to, List<UpgradeNote> notes,
                  Map<String, String> statements) {
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.notes = List.copyOf(notes);
        this.statements = Collections.unmodifiableMap(new LinkedHashMap<>(statements));
    }

    /**
     * Returns the edition the document was read as.
     *
     * @return the edition string of the source
     */
    public String from() {
        return from;
    }

    /**
     * Returns the edition the document was written as, or would have been.
     *
     * @return the edition string of the target
     */
    public String to() {
        return to;
    }

    /**
     * Returns everything the run has to say, in the order it said it.
     *
     * @return the notes
     */
    public List<UpgradeNote> notes() {
        return notes;
    }

    /**
     * Returns the notes of one severity.
     *
     * @param severity the severity to select
     * @return the notes of that severity, in the order of {@link #notes()}
     */
    public List<UpgradeNote> notes(UpgradeNote.Severity severity) {
        List<UpgradeNote> selected = new ArrayList<>();
        for (UpgradeNote note : notes) {
            if (note.severity() == severity) {
                selected.add(note);
            }
        }
        return List.copyOf(selected);
    }

    /**
     * Returns the points the run left to the caller.
     *
     * @return the notes of severity {@code OPEN_POINT}
     */
    public List<UpgradeNote> openPoints() {
        return notes(UpgradeNote.Severity.OPEN_POINT);
    }

    /**
     * Returns the reasons the run wrote nothing.
     *
     * @return the notes of severity {@code REFUSAL}, empty where the run wrote a document
     */
    public List<UpgradeNote> refusals() {
        return notes(UpgradeNote.Severity.REFUSAL);
    }

    /**
     * Returns the sentence the mapping file states each point in, by identifier of the
     * point, for the points the notes name.
     *
     * @return the statements, in the order the points were first met
     */
    public Map<String, String> statements() {
        return statements;
    }

    /**
     * Tells whether the run rewrote addresses and nothing else: no open point, no loss,
     * no refusal.
     *
     * @return {@code true} where every note is information
     */
    public boolean isClean() {
        for (UpgradeNote note : notes) {
            if (note.severity() != UpgradeNote.Severity.INFORMATION) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns how many values moved to another address.
     *
     * @return the number of rewritten paths
     */
    public int rewritten() {
        return count(UpgradeNote.Kind.PATH_REWRITTEN);
    }

    /**
     * Returns how many values were dropped because the caller named their paths.
     *
     * @return the number of dropped values
     */
    public int dropped() {
        return count(UpgradeNote.Kind.VALUE_DROPPED);
    }

    private int count(UpgradeNote.Kind kind) {
        int found = 0;
        for (UpgradeNote note : notes) {
            if (note.kind() == kind) {
                found++;
            }
        }
        return found;
    }

    @Override
    public String toString() {
        return "UpgradeReport[" + from + " -> " + to + ", " + notes.size() + " notes]";
    }
}
