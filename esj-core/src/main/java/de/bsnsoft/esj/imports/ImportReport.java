package de.bsnsoft.esj.imports;

import java.util.List;
import java.util.Objects;

/**
 * What the importer had to say about a document beyond the values it produced: the
 * elements it could not place, the values it could not read and the supplementary
 * components it dropped.
 *
 * <p>An empty report means that every element carrying a term identifier reached the
 * document. It does not mean that the document is valid; that is what the validator of
 * {@code esj-core} is for.
 *
 * @param notes the observations, in the order they were made, which is the document
 *              order of the source
 */
public record ImportReport(List<ImportNote> notes) {

    /** A report without observations. */
    private static final ImportReport EMPTY = new ImportReport(List.of());

    /**
     * Copies the notes.
     *
     * @param notes the observations, in the order they were made, which is the document
     *              order of the source
     * @throws NullPointerException if {@code notes} or one of its elements is
     *                              {@code null}
     */
    public ImportReport {
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }

    /**
     * Returns the report of an import that had nothing to remark.
     *
     * @return the empty report
     */
    public static ImportReport empty() {
        return EMPTY;
    }

    /**
     * Tells whether the importer had nothing to remark.
     *
     * @return {@code true} if there are no notes
     */
    public boolean isEmpty() {
        return notes.isEmpty();
    }

    /**
     * Returns the notes of one kind.
     *
     * @param kind the kind of observation
     * @return the notes of that kind, in the order they were made
     * @throws NullPointerException if {@code kind} is {@code null}
     */
    public List<ImportNote> notes(ImportNote.Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return notes.stream().filter(note -> note.kind() == kind).toList();
    }

    /**
     * Returns the notes at one level.
     *
     * <p>{@link ImportNote.Level#WARNING} is the set a caller has to show: those are the
     * notes that say content of the source did not reach the document.
     *
     * @param level how much attention the notes deserve
     * @return the notes at that level, in the order they were made
     * @throws NullPointerException if {@code level} is {@code null}
     */
    public List<ImportNote> notes(ImportNote.Level level) {
        Objects.requireNonNull(level, "level");
        return notes.stream().filter(note -> note.level() == level).toList();
    }
}
