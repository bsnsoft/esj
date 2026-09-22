package de.bsnsoft.esj.validate;

import de.bsnsoft.esj.SemanticPath;
import java.util.Objects;

/**
 * One thing a validator has to say about a document (specification, section 9.5).
 *
 * <p>A caller acts on a finding by its {@link #code()} and by the place it names, so the
 * place is fixed by the specification and not by this implementation. {@link #path()} is
 * that place wherever a semantic path can be it. {@link #subject()} carries what the path
 * cannot: the member name as the document writes it where the defect is about a name that
 * is no path, and the identifier of the term or group where the defect is about one that
 * is missing from the instance the path names or occurs too often in it. Four mandatory
 * terms missing at the root of a document draw four findings with one code and one path,
 * and only the subject tells them apart without the English message being read.
 *
 * @param path     the semantic path the finding is about; the root path, whose text is
 *                 the empty string, stands for a finding about the document as a whole
 * @param subject  what the finding is about where the path cannot name it, or the empty
 *                 string where the path names it
 * @param code     the stable identifier of the kind of problem
 * @param severity how much the finding weighs
 * @param message  human-readable English text
 */
public record Finding(SemanticPath path,
                      String subject,
                      FindingCode code,
                      Severity severity,
                      String message) {

    /**
     * Checks that every part is present.
     *
     * @param path     the semantic path the finding is about; the root path, whose text is
     *                 the empty string, stands for a finding about the document as a whole
     * @param subject  what the finding is about where the path cannot name it, or the empty
     *                 string where the path names it
     * @param code     the stable identifier of the kind of problem
     * @param severity how much the finding weighs
     * @param message  human-readable English text
     * @throws NullPointerException if a part is {@code null}
     */
    public Finding {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Creates a finding whose path names the place, with the severity the specification
     * fixes for the code.
     *
     * @param path    the semantic path the finding is about
     * @param code    the kind of problem
     * @param message human-readable English text
     * @return the finding, with an empty subject
     */
    public static Finding of(SemanticPath path, FindingCode code, String message) {
        return about(path, "", code, message);
    }

    /**
     * Creates a finding that names what it is about beside the path, with the severity
     * the specification fixes for the code.
     *
     * @param path    the semantic path the finding is about
     * @param subject the member name or the term or group identifier the finding is
     *                about, as the specification, section 9.5 fixes it for the code
     * @param code    the kind of problem
     * @param message human-readable English text
     * @return the finding
     */
    public static Finding about(SemanticPath path,
                                String subject,
                                FindingCode code,
                                String message) {
        return new Finding(path, subject, code, code.defaultSeverity(), message);
    }

    /**
     * Creates a finding about the document as a whole.
     *
     * @param code    the kind of problem
     * @param message human-readable English text
     * @return the finding, carrying the root path and an empty subject
     */
    public static Finding ofDocument(FindingCode code, String message) {
        return of(SemanticPath.root(), code, message);
    }

    /**
     * Tells whether this finding makes the document fail its layer.
     *
     * @return {@code true} if the severity is {@link Severity#ERROR}
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /**
     * Returns the finding as one line: the code, the path and the message. The subject
     * is not repeated here, because the message that goes with a subject names it.
     *
     * @return a short description of the finding
     */
    @Override
    public String toString() {
        return code.code() + " [" + severity.token() + "] " + path + ": " + message;
    }
}
