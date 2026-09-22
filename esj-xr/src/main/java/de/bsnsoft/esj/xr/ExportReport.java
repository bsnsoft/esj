package de.bsnsoft.esj.xr;

import java.util.List;
import java.util.Objects;

/**
 * What the exporter had to say about a document beyond the elements it wrote: the values
 * the XR representation has no element for, the ones whose content XML cannot carry, the
 * supplementary components it dropped and the extension subtree it left behind.
 *
 * <p>An empty report means that every value of the document reached the XR representation
 * and that the document reads back with the same values. It does not mean that the XR
 * document is a valid invoice; that is what the validators are for.
 *
 * @param notes the observations, in the order they were made, which is the canonical path
 *              order of the values they concern
 */
public record ExportReport(List<ExportNote> notes) {

    /** A report without observations. */
    private static final ExportReport EMPTY = new ExportReport(List.of());

    /**
     * Copies the notes.
     *
     * @param notes the observations, in the order they were made, which is the canonical path
     *              order of the values they concern
     * @throws NullPointerException if {@code notes} or one of its elements is {@code null}
     */
    public ExportReport {
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }

    /**
     * Returns the report of an export that had nothing to remark.
     *
     * @return the empty report
     */
    public static ExportReport empty() {
        return EMPTY;
    }

    /**
     * Tells whether the exporter had nothing to remark.
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
    public List<ExportNote> notes(ExportNote.Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return notes.stream().filter(note -> note.kind() == kind).toList();
    }
}
