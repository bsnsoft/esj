package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.EsjException;
import de.bsnsoft.esj.imports.ImportReport;

/**
 * Base class of the unchecked exceptions this module throws for a document it cannot read.
 *
 * <p>A document that cannot be read at all raises an exception: its root element belongs
 * to no syntax this module knows ({@link XrSyntaxException}), or it is not well-formed XML
 * or the transformation into the XR representation failed ({@link XrFormatException}). Two
 * more refusals are not of this type, because both readers of an XML invoice raise them
 * alike: a document larger than the importer accepts or nesting deeper than it walks
 * ends in an {@link de.bsnsoft.esj.EsjLimitException}, and one whose bytes are not written
 * in the encoding it declares, where the importer was asked not to repair that, in a
 * {@link de.bsnsoft.esj.xml.XmlEncodingException}. Everything a document contains that
 * the importer could not place is not an exception but a note in the {@link ImportReport}.
 */
public abstract sealed class XrException extends EsjException
        permits XrFormatException, XrSyntaxException {

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
