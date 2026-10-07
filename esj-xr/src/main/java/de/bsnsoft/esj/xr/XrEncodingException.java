package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.imports.ImportNote;
import java.util.Optional;

/**
 * Signals that the bytes of a document are not written in the encoding the document
 * declares, and that the importer was asked not to repair that or could not.
 *
 * <p>Where the bytes are written in one of the charsets {@link XmlBytes} recodes from, it
 * is thrown only in {@link XrEncodingMode#STRICT}: an importer in
 * {@link XrEncodingMode#REPAIR} recodes the document instead and records the same two
 * facts as the import note {@link ImportNote.Kind#ENCODING_REPAIRED}, what the document
 * declared and what its bytes are. Where they decode in none of those charsets and not in
 * the one the document names either, it is thrown in both modes, and
 * {@link #repairable()} is {@code false}: reading such bytes at all would mean replacing
 * what does not decode, which is a change to the content no note can describe.
 */
public final class XrEncodingException extends XrException {

    private static final long serialVersionUID = 1L;

    /**
     * The charset the XML declaration named, absent where the document carries none.
     *
     * @serial
     */
    private final String declared;

    /**
     * The canonical name of the charset the bytes are written in.
     *
     * @serial
     */
    private final String assumed;

    /**
     * Whether the bytes are written in a charset the importer recodes from.
     *
     * @serial
     */
    private final boolean repairable;

    /**
     * Creates an exception carrying what the document declared and what its bytes are,
     * for bytes the importer could have recoded and was asked not to.
     *
     * @param message  the detail message, in English
     * @param declared the charset the document declared, {@code null} where it declared
     *                 none
     * @param assumed  the canonical name of the charset the bytes are written in
     */
    public XrEncodingException(String message, String declared, String assumed) {
        this(message, declared, assumed, true);
    }

    /**
     * Creates an exception carrying what the document declared and what its bytes are,
     * and whether the importer could have recoded them.
     *
     * @param message    the detail message, in English
     * @param declared   the charset the document declared, {@code null} where it declared
     *                   none
     * @param assumed    the canonical name of the charset the bytes are read in; where they
     *                   are not {@code repairable}, the one the document names
     * @param repairable whether the bytes are written in a charset the importer recodes
     *                   from, so that a run that is not strict reads them
     */
    public XrEncodingException(String message, String declared, String assumed,
                               boolean repairable) {
        super(message);
        this.declared = declared;
        this.assumed = assumed;
        this.repairable = repairable;
    }

    /**
     * Returns the charset the document declared.
     *
     * @return the declared charset, or an empty optional where the document carries no
     *         declaration and no byte order mark
     */
    public Optional<String> declared() {
        return Optional.ofNullable(declared);
    }

    /**
     * Returns the charset the bytes are written in.
     *
     * @return the canonical charset name
     */
    public String assumed() {
        return assumed;
    }

    /**
     * Tells whether an importer that is not strict would have recoded these bytes and read
     * them. Where it would not, the bytes decode neither in the charset the document names
     * nor in any of the ones the importer recodes from, and no mode reads them.
     *
     * @return {@code true} if the refusal is the strict mode's, {@code false} if it is the
     *         bytes'
     */
    public boolean repairable() {
        return repairable;
    }
}
