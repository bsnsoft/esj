package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.imports.ImportNote;

/**
 * What an importer does with a document whose bytes are not written in the encoding the
 * document declares.
 *
 * <p>The two answers are both right, for two different questions. A command that converts
 * or inspects a document wants the content, and a defect that a recode repairs costs
 * nothing to repair as long as it is on the record. A command that gives a verdict about
 * the bytes it was handed cannot repair them first: a document is valid or it is not, and
 * a validator that silently reads a different byte sequence than the one it was given is
 * answering a question nobody asked.
 *
 * <p>Neither mode is silent. The repairing one records
 * {@link ImportNote.Kind#ENCODING_REPAIRED} with what was declared and what was read; the
 * refusing one throws {@link XrEncodingException} carrying the same two facts.
 */
public enum XrEncodingMode {

    /**
     * Recode the document and record the note. This is what an importer does unless it is
     * told otherwise.
     */
    REPAIR,

    /**
     * Refuse the document with an {@link XrEncodingException}. The verdict is about the
     * bytes that were handed over, so nothing is read in a charset other than the one the
     * document names.
     */
    STRICT
}
