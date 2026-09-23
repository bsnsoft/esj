package de.bsnsoft.esj.bindings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 * <p>One kind of value is neither written nor dropped: a value of a term whose registry
 * declares that its terms belong to no transport syntax. It stays in the semantic document by
 * design, a {@link WriteNote.Kind#TERM_BY_DESIGN} note names it with that registry, and
 * {@link #byDesign()} gathers those notes by registry.
 *
 * @param syntax  the syntax the document was written in
 * @param written how many values of the document reached it
 * @param dropped how many did not and should have, leaving out the values that stay in the
 *                semantic document by design
 * @param notes   one note per value that did not, and per part of the document that has no
 *                place in the syntax, in the canonical path order of the values
 */
public record WriteReport(BindingSyntax syntax, int written, int dropped, List<WriteNote> notes) {

    /**
     * Copies the notes and checks the counts.
     *
     * @param syntax  the syntax the document was written in
     * @param written how many values of the document reached it
     * @param dropped how many did not and should have, leaving out the values that stay in
     *                the semantic document by design
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
     * that syntax accepts. Nor does a note of {@link WriteNote.Kind#TERM_BY_DESIGN}, whose
     * value was never meant to reach a syntax.
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
     * document does not state, which is a property of the syntax and not a loss,
     * {@link WriteNote.Kind#VALUE_NOT_CONVERTED} leaves the value in the document, and
     * {@link WriteNote.Kind#TERM_BY_DESIGN} names a value its registry keeps out of every
     * syntax.
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
     * Returns the terms whose values stayed in the semantic document by design, by the
     * registry that declares them untransported.
     *
     * <p>It is the {@link WriteNote.Kind#TERM_BY_DESIGN} notes gathered: a document that
     * uses one term of an extension at every invoice line has one note per line and one term
     * here.
     *
     * @return the term identifiers, without repetition and in the canonical path order of
     *         the values they stood at, by the edition of the declaring registry in the order
     *         the registries were first met; empty where no value was left behind by design
     */
    public Map<String, List<String>> byDesign() {
        Map<String, Set<String>> gathered = new LinkedHashMap<>();
        for (WriteNote note : notes(WriteNote.Kind.TERM_BY_DESIGN)) {
            gathered.computeIfAbsent(note.registry(), registry -> new LinkedHashSet<>())
                    .add(term(note.path()));
        }
        Map<String, List<String>> terms = new LinkedHashMap<>();
        gathered.forEach((registry, ids) -> terms.put(registry, List.copyOf(ids)));
        return Collections.unmodifiableMap(terms);
    }

    /**
     * Returns the term a value path names: its last step that is not an occurrence index.
     *
     * <p>A repeatable term carries its index as the last step ({@code /BG-4/BT-29/0}), and a
     * term identifier is never a number, so the steps that consist of digits alone are
     * passed over.
     */
    private static String term(String path) {
        int end = path.length();
        while (end > 0) {
            int start = path.lastIndexOf('/', end - 1) + 1;
            String step = path.substring(start, end);
            if (!step.isEmpty() && !step.chars().allMatch(c -> c >= '0' && c <= '9')) {
                return step;
            }
            end = start - 1;
        }
        return path;
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
