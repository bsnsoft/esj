package de.bsnsoft.esj.xr;

/**
 * A named correction the importer applies to the XR representation before it writes the
 * semantic model.
 *
 * <p>A normalization is not part of the mapping. The mapper reads business term
 * identifiers and asks the registry what they are; a normalization repairs a place where
 * the XR representation itself does not carry what the source syntax holds, and it is
 * named, documented and switchable so that a caller can see exactly what was done to the
 * document. Which ones an importer runs is decided by
 * {@link XrImporter#XrImporter(de.bsnsoft.esj.model.Registry, long,
 * de.bsnsoft.esj.json.Limits, java.util.Set)}.
 */
public enum XrNormalization {

    /**
     * Splits the note subject code out of a document level invoice note that was read
     * from UBL.
     *
     * <p>The UBL syntax binding of EN 16931-1 carries the note subject code BT-21 and the
     * note BT-22 in one {@code cbc:Note} element, with the code written as a prefix
     * between two number signs: {@code <cbc:Note>#AAC#Invoice Note Description</cbc:Note>}.
     * The CEN validation artefacts read the code out of exactly that prefix — rule
     * {@code BR-CL-08} on the context {@code /ubl:Invoice/cbc:Note | /cn:CreditNote/cbc:Note}
     * takes the characters between the first and the second number sign and tests them
     * against UNTDID 4451. In CII the two are separate elements, {@code ram:SubjectCode}
     * and {@code ram:Content}, so only the UBL side needs the split.
     *
     * <p>This normalization applies it: where a note read from UBL matches
     * {@code ^#([A-Z]{3})#(.*)$} and the remainder is not empty, BT-21 becomes the three
     * characters and BT-22 the remainder. Every other note is stored unchanged. Without
     * it, BT-21 is never read from a UBL document and BT-22 carries the prefix as part of
     * its text, which is a value that is wrong rather than merely incomplete.
     */
    UBL_NOTE_SUBJECT_CODE
}
