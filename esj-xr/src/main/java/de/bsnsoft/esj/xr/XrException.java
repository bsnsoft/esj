package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.imports.ImportReport;

/**
 * Base class of the unchecked exceptions this module throws.
 *
 * <p>A document that cannot be read at all raises an exception: its root element belongs
 * to no syntax this module knows ({@link XrSyntaxException}), it is not well-formed XML
 * or the transformation into the XR representation failed ({@link XrFormatException}),
 * or it is larger than the importer accepts or nests deeper than the importer walks
 * ({@link XrLimitException}), or its bytes are not written in the encoding it declares
 * and the importer was asked not to repair that ({@link XrEncodingException}). Everything a
 * document contains that the importer could not place is not an exception but a note in
 * the {@link ImportReport}.
 */
public abstract sealed class XrException extends RuntimeException
        permits XrEncodingException, XrFormatException, XrLimitException, XrSyntaxException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    protected XrException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    protected XrException(String message, Throwable cause) {
        super(message, cause);
    }
}
