package de.bsnsoft.esj;

import de.bsnsoft.esj.validate.FindingCode;
import java.util.Optional;

/**
 * Signals that a resource bound was reached: one of the reader limits of the
 * specification, section 12.2, or a number in a document that does not fit the type this
 * implementation would have to hold it in.
 *
 * <p>A limit violation is an ordinary finding with the code {@code ESJ-L1-LIMIT} as well;
 * an implementation that meets one while it parses may additionally abort with this
 * exception (specification, section 9.5). This implementation stops parsing either way,
 * because parsing on is exactly what the limit was there to prevent: a reader that was
 * asked to reject throws this exception, and a reader that was asked to report returns the
 * findings it has collected, the last of which is the limit.
 *
 * <p><strong>It says nothing about the document.</strong> A limit is the reading party's
 * policy and takes no part in conformance (specification, section 3.1): the same byte
 * sequence is a conformant document for a reader configured differently, and both readers
 * are right. Forwarding the document to a party with a larger bound is a sensible answer
 * to this exception, and would not be to an {@link EsjFormatException}.
 */
public final class EsjLimitException extends EsjException {

    private static final long serialVersionUID = 1L;

    /**
     * The longest location a detail message reproduces. A location is built out of
     * document content, so without a bound a document would decide how long a log line is
     * (specification, section 12.6). {@link #location()} is the whole of it and is what a
     * program reads.
     */
    private static final int LOCATION_IN_MESSAGE = 512;


    /**
     * The place in the document the limit was reached at.
     *
     * @serial where the limit was reached in the document as it was received, written
     *         as a member access, or {@code null}
     */
    private final String location;

    /**
     * Creates an exception with a message and no location.
     *
     * @param message the detail message, in English
     */
    public EsjLimitException(String message) {
        this(message, null, null);
    }

    /**
     * Creates an exception with a message and a cause, and no location.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    public EsjLimitException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * Creates an exception that names the place the limit was reached at.
     *
     * @param message  the detail message, in English
     * @param location the place in the document, written as a member access, or
     *                 {@code null}
     * @param cause    the underlying failure, or {@code null}
     */
    public EsjLimitException(String message, String location, Throwable cause) {
        super(location == null || location.isEmpty()
                ? message
                : message + " (at " + Esj.abbreviated(location, LOCATION_IN_MESSAGE) + ")", cause);
        this.location = location;
    }

    /**
     * Returns the kind of problem, which is always the limit code of the specification,
     * section 9.6.
     *
     * @return {@link FindingCode#ESJ_L1_LIMIT}
     */
    public FindingCode code() {
        return FindingCode.ESJ_L1_LIMIT;
    }

    /**
     * Returns the place in the document the limit was reached at, where one was named.
     *
     * @return the location, written as a member access, or an empty optional
     */
    public Optional<String> location() {
        return Optional.ofNullable(location);
    }
}
