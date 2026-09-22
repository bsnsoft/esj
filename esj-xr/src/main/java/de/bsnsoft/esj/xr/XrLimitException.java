package de.bsnsoft.esj.xr;

/**
 * Signals that an input document asks for more than the importer grants it: it is larger
 * than the accepted number of bytes, or its XR representation nests elements deeper than
 * the mapper walks.
 *
 * <p>An importer reads whatever it is handed into memory and lets a stylesheet build a
 * second tree from it, so the size of the input is bounded before any of that begins;
 * see {@link XrImporter#maxInputBytes()}. The bound on bytes is not a bound on work, and
 * that method says how the two differ. The bound on nesting is the second one, and it is
 * there because the mapper walks the XR tree recursively: without it a document that
 * nests without end would end the call in a {@link StackOverflowError} rather than in an
 * exception of this module.
 */
public final class XrLimitException extends XrException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public XrLimitException(String message) {
        super(message);
    }
}
