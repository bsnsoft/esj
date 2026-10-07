package de.bsnsoft.esj;

/**
 * Base class of the unchecked exceptions the libraries of this project throw.
 *
 * <p>Every module throws exceptions of its own, and every one of them is an
 * {@code EsjException}, so that a caller who handles them all in one place catches this
 * type and a caller who cares about one kind catches that kind. Invalid user data is
 * reported as findings, not as exceptions (specification, section 9.5); an exception says
 * that something could not be done at all. Two kinds are defined here because every
 * module meets them:
 *
 * <ul>
 *   <li>{@link EsjFormatException}: a value or path that this implementation was asked to
 *       construct although it cannot exist in a conformant document;</li>
 *   <li>{@link EsjLimitException}: a resource bound that was reached, in any module. A
 *       limit is the policy of the party that reads, writes or renders, and never a
 *       statement about the document, so it has one type wherever it is met.</li>
 * </ul>
 *
 * <p>The other kinds belong to the module that throws them: a document in no syntax a
 * reader knows, a PDF without an invoice, a template that is not a template, a rule pack
 * that does not load. {@link de.bsnsoft.esj.xml.XmlEncodingException} is the one of
 * those this module defines, because both readers of an XML invoice throw it.
 * Input/output failures keep their own exception types.
 */
public abstract class EsjException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    protected EsjException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    protected EsjException(String message, Throwable cause) {
        super(message, cause);
    }
}
