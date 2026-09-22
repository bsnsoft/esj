package de.bsnsoft.esj.bindings;

/**
 * Signals that the root element of a document belongs to no syntax this module reads.
 *
 * <p>The reader recognizes a UBL 2.1 {@code Invoice}, a UBL 2.1 {@code CreditNote} and a
 * UN/CEFACT {@code CrossIndustryInvoice}, each by the namespace and the local name of its
 * root element. Anything else is refused rather than guessed at.
 */
public final class BindingSyntaxException extends BindingException {

    private static final long serialVersionUID = 1L;

    /**
     * The namespace of the root element that was found.
     *
     * @serial
     */
    private final String namespace;

    /**
     * The local name of the root element that was found.
     *
     * @serial
     */
    private final String localName;

    /**
     * Creates an exception that names the root element that was found.
     *
     * @param message   the detail message, in English
     * @param namespace the namespace of the root element, empty if it has none
     * @param localName the local name of the root element
     */
    public BindingSyntaxException(String message, String namespace, String localName) {
        super(message);
        this.namespace = namespace;
        this.localName = localName;
    }

    /**
     * Returns the namespace of the root element that was found.
     *
     * @return the namespace, empty if the root element has none
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Returns the local name of the root element that was found.
     *
     * @return the local name
     */
    public String localName() {
        return localName;
    }
}
