package de.bsnsoft.esj.typed;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import java.util.Objects;

/**
 * Signals that the value a document carries at a path is no value of the semantic data
 * type the registry records for the business term there: its content does not satisfy the
 * grammar that type requires, or a supplementary component the type demands is missing
 * (specification, section 6.2).
 *
 * <p>Such a document fails validation layer L2 (specification, section 9.2). The typed
 * view does not validate, so it meets the defect when an accessor is called and says so
 * rather than returning a value it had to invent.
 */
public final class ValueTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient SemanticPath path;

    private final transient SemanticType expected;

    /**
     * Creates the exception for a path, the semantic data type of the term and the
     * failure the content produced.
     *
     * @param path     the path the view was asked to read
     * @param expected the semantic data type the registry records for the term
     * @param cause    what reading the content as a value of that type failed with
     * @throws NullPointerException if an argument is {@code null}
     */
    public ValueTypeException(SemanticPath path, SemanticType expected, EsjFormatException cause) {
        super("the value at " + Objects.requireNonNull(path, "path")
                + " is no value of the semantic data type "
                + Objects.requireNonNull(expected, "expected").registryDatatype()
                + " the registry records for that term: "
                + Objects.requireNonNull(cause, "cause").getMessage(), cause);
        this.path = path;
        this.expected = expected;
    }

    /**
     * Returns the path the view was asked to read.
     *
     * @return the semantic path of the value
     */
    public SemanticPath path() {
        return path;
    }

    /**
     * Returns the semantic data type the registry records for the business term at that
     * path.
     *
     * @return the semantic data type the value was read as
     */
    public SemanticType expected() {
        return expected;
    }
}
