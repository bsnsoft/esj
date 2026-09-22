package de.bsnsoft.esj.bindings;

/**
 * What the reader does with content that does not arrive the way the semantic model would
 * have it.
 *
 * <p>The difference between the two modes is not how much the reader understands. Both
 * read the same tables and apply the same bindings, the eight-digit CII date included:
 * that a date is written {@code 20260919} where the format qualifier says 102 is what the
 * syntax <em>is</em>, not a defect to be repaired. The difference is what happens where
 * the document and the model disagree.
 */
public enum ReaderMode {

    /**
     * Named corrections are applied and a value that cannot be built is a note.
     *
     * <p>A correction repairs a place where the syntax carries something the semantic
     * model holds in another shape, and every one of them is named in the binding table by
     * a flag. The one of this release is the note subject code of UBL, which the syntax
     * binding writes as a {@code #AAC#} prefix in front of the note rather than in an
     * element of its own: the reader splits it into BT-21 and BT-22 where it stands there,
     * and stores the note whole where it does not. Without the correction BT-21 is never
     * read from a UBL document and BT-22 carries a syntax artefact as part of its text,
     * which is a value that is wrong rather than merely incomplete.
     *
     * <p>A value the reader cannot build — content that spells no date, no decimal, no
     * base64 — is left out and named in the report, and the rest of the document is read.
     * This is the mode to read a document from a stranger in: what arrived is reported
     * exactly, and one bad value does not cost the other three hundred.
     */
    REPAIR,

    /**
     * No correction is applied and a value that cannot be built ends the read.
     *
     * <p>The document is taken exactly as the binding table describes it: a note keeps its
     * prefix, BT-21 stays empty where the syntax wrote no element for it, and the first
     * content that does not spell the semantic data type its term declares raises a
     * {@link BindingFormatException} naming the path and the reason instead of becoming a
     * note. This is the mode for a caller who is about to convert the document onward and
     * would rather hear about a defect than carry it.
     */
    STRICT
}
