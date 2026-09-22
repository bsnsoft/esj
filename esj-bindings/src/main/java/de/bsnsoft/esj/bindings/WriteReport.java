package de.bsnsoft.esj.bindings;

import java.util.List;
import java.util.Objects;

/**
 * What became of a semantic document on the way into an XML syntax: how many of its values
 * were written, how many were not, and one note for every one that was not.
 *
 * <p>The report is the writer's half of the bargain the reader's {@code ImportReport}
 * keeps on the way in. A conversion between a semantic model and a syntax binding of it is
 * not lossless in either direction by nature — the model holds terms a given syntax has no
 * element for, and a syntax admits one element where the model admits many — and the only
 * honest thing a converter can do is to say exactly what did not travel. A report with no
 * notes says that everything did.
 *
 * @param syntax  the syntax the document was written in
 * @param written how many values of the document reached it
 * @param dropped how many did not
 * @param notes   one note per value that did not, and per part of the document that has no
 *                place in the syntax, in the canonical path order of the values
 */
public record WriteReport(BindingSyntax syntax, int written, int dropped, List<WriteNote> notes) {

    /**
     * Copies the notes and checks the counts.
     *
     * @param syntax  the syntax the document was written in
     * @param written how many values of the document reached it
     * @param dropped how many did not
     * @param notes   one note per value that did not, and per part of the document that has no
     *                place in the syntax, in the canonical path order of the values
     * @throws IllegalArgumentException if a count is negative
     * @throws NullPointerException     if a part is {@code null}
     */
    public WriteReport {
        Objects.requireNonNull(syntax, "syntax");
        notes = List.copyOf(notes);
        if (written < 0 || dropped < 0) {
            throw new IllegalArgumentException("a count of values is not negative");
        }
    }

    /**
     * Tells whether everything the document held reached the syntax and the syntax asked
     * for nothing the document could not give it.
     *
     * <p>A note of {@link WriteNote.Kind#CONVENTION_APPLIED} does not make a document
     * incomplete. It says that the syntax required an element no business term of the
     * document states and that the binding table said what is written there, which is a
     * fact about the syntax: nothing of the document was lost and the result is a document
     * that syntax accepts.
     *
     * @return {@code true} if no value was dropped and nothing fell short
     */
    public boolean isComplete() {
        return dropped == 0 && notes.stream().noneMatch(note -> note.kind().isShortfall());
    }

    /**
     * Tells whether the written document says everything the semantic document says.
     *
     * <p>It is the weaker of the two questions: a report may carry notes and still be
     * faithful, because {@link WriteNote.Kind#ELEMENT_NOT_STATED} and
     * {@link WriteNote.Kind#TERM_NOT_STATED} say the syntax asks for something the
     * document does not state, which is a property of the syntax and not a loss, and
     * {@link WriteNote.Kind#VALUE_NOT_CONVERTED} leaves the value in the document.
     *
     * <p>Faithful is not the same as usable in place of the document. A caller who is
     * about to run the validation artefacts of the syntax over the result — as
     * {@code esj validate} does for an ESJ input — has to ask about those notes too: an
     * element the schema requires and nobody states makes a document that schema refuses,
     * and the refusal is about the rendition rather than about what was written from.
     *
     * @return {@code true} if nothing the document states was left behind
     */
    public boolean isFaithful() {
        return dropped == 0 && losses().isEmpty();
    }

    /**
     * Returns the notes that say something the document states did not reach the syntax.
     *
     * @return those notes, in the order they were made
     */
    public List<WriteNote> losses() {
        return notes.stream().filter(note -> note.kind().isLoss()).toList();
    }

    /**
     * Returns the notes of one kind, in the order they were made.
     *
     * @param kind the kind
     * @return the notes of that kind
     * @throws NullPointerException if {@code kind} is {@code null}
     */
    public List<WriteNote> notes(WriteNote.Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return notes.stream().filter(note -> note.kind() == kind).toList();
    }

    /**
     * Returns the report as one line: the syntax, the counts and the number of notes.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return syntax + ": " + written + " values written, " + dropped + " dropped, "
                + notes.size() + " notes";
    }
}
