package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.imports.ImportNote;
import java.util.Optional;

/**
 * Signals that the bytes of a document are not written in the encoding the document
 * declares, and that the importer was asked not to repair that.
 *
 * <p>It is thrown only in {@link XrEncodingMode#STRICT}. An importer in
 * {@link XrEncodingMode#REPAIR} recodes the document instead and records the same two
 * facts as the import note {@link ImportNote.Kind#ENCODING_REPAIRED}: what the document
 * declared, and what its bytes are.
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
     * Creates an exception carrying what the document declared and what its bytes are.
     *
     * @param message  the detail message, in English
     * @param declared the charset the document declared, {@code null} where it declared
     *                 none
     * @param assumed  the canonical name of the charset the bytes are written in
     */
    public XrEncodingException(String message, String declared, String assumed) {
        super(message);
        this.declared = declared;
        this.assumed = assumed;
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
}
