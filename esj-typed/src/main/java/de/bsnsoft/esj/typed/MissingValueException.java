package de.bsnsoft.esj.typed;

import de.bsnsoft.esj.SemanticPath;
import java.util.Objects;

/**
 * Signals that a view was asked for a value the document does not carry at a path where
 * the semantic model declares the business term mandatory.
 *
 * <p>This is not a format error and it is not reported as a finding: it is what an
 * accessor of a mandatory term can do when the document it reads is incomplete. Whether
 * every mandatory term is present is decided by validation layer L3 (specification,
 * section 9.3), which the typed view does not perform.
 */
public final class MissingValueException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient SemanticPath path;

    /**
     * Creates the exception for a path.
     *
     * @param path the path the view was asked to read
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public MissingValueException(SemanticPath path) {
        super("the document carries no value at " + Objects.requireNonNull(path, "path"));
        this.path = path;
    }

    /**
     * Returns the path the view was asked to read.
     *
     * @return the semantic path of the missing value
     */
    public SemanticPath path() {
        return path;
    }
}
