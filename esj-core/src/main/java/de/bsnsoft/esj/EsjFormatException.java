package de.bsnsoft.esj;

import de.bsnsoft.esj.internal.Messages;
import de.bsnsoft.esj.validate.FindingCode;
import java.util.Optional;

/**
 * Signals content that the format does not allow: a semantic path that does not match the
 * grammar of the specification, section 5.1, a value that breaks a rule of section 6, an
 * envelope member with a value the specification does not define, or a registry file that
 * is not a registry.
 *
 * <p>This exception is thrown while a value, path or document is being built, and by a
 * reader that was asked to reject rather than to report: a document that fails layer L1
 * is not a document, so a reader cannot hand one back. A validator that inspects an
 * existing document reports findings instead (specification, section 9.5), and so does
 * {@code de.bsnsoft.esj.json.EsjReader.readWithFindings}.
 *
 * <p>An exception that a reader throws carries the three fields of the finding it stands
 * for — the finding code of the specification, section 9.6, the semantic path and the
 * subject — so that a caller can react to the kind of problem and to its place without
 * reading English text. It is the same finding in another shape: {@link #code()} is the
 * code the reader would have reported for that input, {@link #path()} the path and
 * {@link #subject()} the subject that finding carries. Reporting and throwing are both
 * conformant, and a caller that must handle both portably catches the exception and
 * branches on its code exactly as it would on a finding's (specification, sections 3.2,
 * 9.5 and 9.6).
 */
public final class EsjFormatException extends EsjException {

    private static final long serialVersionUID = 1L;

    /**
     * The longest location a detail message reproduces. A location is built out of
     * document content, so without a bound a document would decide how long a log line is
     * (specification, section 12.6). {@link #location()} is the whole of it and is what a
     * program reads.
     */
    private static final int LOCATION_IN_MESSAGE = 512;


    /**
     * The kind of problem.
     *
     * @serial the finding code of the specification, section 9.6, or {@code null}
     */
    private final FindingCode code;

    /**
     * The place in the document the problem sits in.
     *
     * @serial where the problem sits in the document as it was received, written as a
     *         member access, or {@code null}
     */
    private final String location;

    /**
     * The semantic path of the finding this exception stands for.
     *
     * @serial the text of the path, the empty string for the root path, or {@code null}
     *         where the thrower named none
     */
    private final String path;

    /**
     * Creates an exception with a message and no code or location.
     *
     * @param message the detail message, in English
     */
    public EsjFormatException(String message) {
        this(message, null, null, null);
    }

    /**
     * Creates an exception with a message and a cause, and no code or location.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    public EsjFormatException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    /**
     * Creates an exception that names the kind of problem and the place it sits in.
     *
     * @param message  the detail message, in English
     * @param code     the finding code of the specification, section 9.6, or {@code null}
     * @param location the place in the document, written as a member access, for
     *                 example {@code values["/BT-1"].value}, or {@code null}
     */
    public EsjFormatException(String message, FindingCode code, String location) {
        this(message, code, location, null);
    }

    /**
     * Creates an exception that names the kind of problem, the place it sits in and the
     * underlying failure.
     *
     * @param message  the detail message, in English
     * @param code     the finding code of the specification, section 9.6, or {@code null}
     * @param location the place in the document, written as a member access, or
     *                 {@code null}
     * @param cause    the underlying failure, or {@code null}
     */
    public EsjFormatException(String message, FindingCode code, String location, Throwable cause) {
        this(message, code, null, location, cause);
    }

    /**
     * Creates an exception that carries the three fields of a finding: the kind of
     * problem, the semantic path and the subject (specification, section 9.5).
     *
     * @param message  the detail message, in English
     * @param code     the finding code of the specification, section 9.6, or {@code null}
     * @param path     the semantic path the finding names, the root path where it names
     *                 none, or {@code null}
     * @param location the subject: the place in the document written as a member access,
     *                 for example {@code values["/BT-1"].value}, or {@code null}
     * @param cause    the underlying failure, or {@code null}
     */
    public EsjFormatException(String message,
                              FindingCode code,
                              SemanticPath path,
                              String location,
                              Throwable cause) {
        super(location == null || location.isEmpty()
                ? message
                : message + " (at " + Messages.abbreviated(location, LOCATION_IN_MESSAGE) + ")",
                cause);
        this.code = code;
        this.location = location;
        this.path = path == null ? null : path.toString();
    }

    /**
     * Returns the kind of problem, where one was named.
     *
     * @return the finding code of the specification, section 9.6, or an empty optional
     */
    public Optional<FindingCode> code() {
        return Optional.ofNullable(code);
    }

    /**
     * Returns the place in the document the problem sits in, where one was named. It is
     * written the way a program would address that place in the document as it was
     * received, so a value member reads {@code values["/BG-4/BT-27"].value}.
     *
     * @return the location, or an empty optional
     */
    public Optional<String> location() {
        return Optional.ofNullable(location);
    }

    /**
     * Returns the semantic path of the finding this exception stands for: the member of
     * {@code values} the problem sits in, and the root path where it sits in no such member
     * or the thrower named no place. It is the {@code path} a reader that reports would have
     * given the same finding (specification, section 9.5).
     *
     * @return the path, the root path where there is none
     */
    public SemanticPath path() {
        return path == null || path.isEmpty() ? SemanticPath.root() : SemanticPath.of(path);
    }

    /**
     * Returns the subject of the finding this exception stands for: the member access
     * {@link #location()} names, whole, or the empty string where it names none. It is the
     * {@code subject} a reader that reports would have given the same finding
     * (specification, section 9.5).
     *
     * @return the subject, or the empty string
     */
    public String subject() {
        return location == null ? "" : location;
    }
}
