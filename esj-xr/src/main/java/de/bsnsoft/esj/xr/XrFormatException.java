package de.bsnsoft.esj.xr;

/**
 * Signals that a document could not be parsed as XML, or that transforming it into the
 * XR representation failed.
 *
 * <p>Two causes are worth naming, because neither reads as a defect of the document to
 * the person who sent it. A document type declaration is refused: it is legal XML, and
 * the importer takes it as the one construct it will not read, because a declaration is
 * what entity expansion and external entity resolution arrive in. And a date whose
 * digits pass the range checks of the stylesheets but name no day of the calendar — the
 * 30th of February — makes the stylesheet fail while it builds the date, which costs the
 * whole document rather than the one value.
 *
 * <p>The message describes what went wrong; it does not reproduce the document, because
 * the content of a document does not get to decide how long a log line is.
 */
public final class XrFormatException extends XrException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public XrFormatException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    public XrFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
