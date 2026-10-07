package de.bsnsoft.esj;

import de.bsnsoft.esj.internal.Messages;
import de.bsnsoft.esj.validate.FindingCode;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Signals that a resource bound was reached: one of the reader limits of the
 * specification, section 12.2, a number in a document that does not fit the type this
 * implementation would have to hold it in, or a bound of any other module of this
 * project — on the bytes a reader accepts or a writer produces, on what a PDF container
 * decodes, on the pages of a rendering, on the time a validation runs.
 *
 * <p>Every module of this project throws this type for a bound it reaches, and no other,
 * so that a caller who answers every limit the same way — more resources, another party,
 * no verdict — catches it once. {@link #bound()} names the bound where the thrower knows
 * it: the setting it is configured with, the value it had and what that value counts. The
 * message says the same in English.
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
 *
 * <p>The class is not final so that a module may say more about a bound of its own in a
 * subclass; none of this release does.
 */
public class EsjLimitException extends EsjException {

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
     * The bound that was reached.
     *
     * @serial the setting, its value and its unit, or {@code null} where the thrower did
     *         not name them
     */
    private final Bound bound;

    /**
     * A bound that was reached: the setting it is configured with, the value it had and
     * what that value counts.
     *
     * <p>The name is the setting as the options of the throwing module spell it —
     * {@code maxInputBytes}, {@code maxPages}, {@code maxRuntime} — so that a caller can
     * raise exactly that one. A bound no setting moves is named in the same style and
     * documented as fixed where it is thrown.
     *
     * @param name  the name of the bound, such as {@code maxInputBytes}
     * @param value the value the bound had when it was reached
     * @param unit  what the value counts, in English and in the plural, such as
     *              {@code bytes}, {@code pages}, {@code levels} or {@code milliseconds}
     */
    public record Bound(String name, long value, String unit) implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Checks that no member is {@code null} or empty.
         *
         * @param name  the name of the bound
         * @param value the value the bound had when it was reached
         * @param unit  what the value counts
         * @throws NullPointerException     if {@code name} or {@code unit} is {@code null}
         * @throws IllegalArgumentException if {@code name} or {@code unit} is empty
         */
        public Bound {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(unit, "unit");
            if (name.isEmpty() || unit.isEmpty()) {
                throw new IllegalArgumentException("a bound has a name and a unit");
            }
        }
    }

    /**
     * Creates an exception with a message and no bound or location.
     *
     * @param message the detail message, in English
     */
    public EsjLimitException(String message) {
        this(message, null, null, null);
    }

    /**
     * Creates an exception with a message and a cause, and no bound or location.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    public EsjLimitException(String message, Throwable cause) {
        this(message, null, null, cause);
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
        this(message, null, location, cause);
    }

    /**
     * Creates an exception that names the bound that was reached.
     *
     * @param message the detail message, in English
     * @param bound   the bound, or {@code null} where it is not known
     */
    public EsjLimitException(String message, Bound bound) {
        this(message, bound, null, null);
    }

    /**
     * Creates an exception that names the bound that was reached, the place it was
     * reached at and the underlying failure.
     *
     * @param message  the detail message, in English
     * @param bound    the bound, or {@code null} where it is not known
     * @param location the place in the document, written as a member access, or
     *                 {@code null}
     * @param cause    the underlying failure, or {@code null}
     */
    public EsjLimitException(String message, Bound bound, String location, Throwable cause) {
        super(location == null || location.isEmpty()
                ? message
                : message + " (at " + Messages.abbreviated(location, LOCATION_IN_MESSAGE) + ")",
                cause);
        this.bound = bound;
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
     * Returns the bound that was reached, where the thrower named it.
     *
     * @return the bound, or an empty optional
     */
    public Optional<Bound> bound() {
        return Optional.ofNullable(bound);
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
