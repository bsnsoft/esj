package de.bsnsoft.esj.typed;

import de.bsnsoft.esj.SemanticPath;
import java.util.Objects;

/**
 * Signals that the document totals cannot be derived from the invoice as it stands.
 *
 * <p>A derivation policy computes or it refuses; it never guesses. Every refusal names the
 * business term that is missing or contradictory and the group instance it is in, so that the
 * caller can go to that one place in the invoice.
 */
public final class DerivationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient SemanticPath path;

    private final transient String term;

    /**
     * Creates the exception.
     *
     * @param message what cannot be derived and why, as one English sentence
     * @param path    the path of the group instance or value the refusal is about
     * @param term    the identifier of the business term or group the refusal is about
     * @throws NullPointerException if an argument is {@code null}
     */
    public DerivationException(String message, SemanticPath path, String term) {
        super(Objects.requireNonNull(message, "message"));
        this.path = Objects.requireNonNull(path, "path");
        this.term = Objects.requireNonNull(term, "term");
    }

    /**
     * Returns where in the invoice the refusal is.
     *
     * @return the path of the group instance or value the refusal is about
     */
    public SemanticPath path() {
        return path;
    }

    /**
     * Returns which business term or business group the refusal is about.
     *
     * @return the identifier, for instance {@code BT-151}
     */
    public String term() {
        return term;
    }
}
