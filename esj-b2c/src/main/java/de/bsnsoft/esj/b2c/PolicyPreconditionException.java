package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticPath;
import java.util.Objects;

/**
 * Signals that a gross authoring policy cannot run on the invoice as it stands.
 *
 * <p>A policy computes or it refuses; it never guesses. Every refusal names the precondition
 * that failed, the business term it is about and the group instance it sits in, so that the
 * caller goes to one place in the invoice and to one sentence of the policy's documentation.
 */
public final class PolicyPreconditionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String policy;

    private final transient String precondition;

    private final transient SemanticPath path;

    private final transient String term;

    /**
     * Creates the exception.
     *
     * @param policy       the name of the policy that refused
     * @param precondition the precondition that failed, as the policy's documentation names
     *                     it
     * @param message      what the policy cannot do and why, as one English sentence
     * @param path         the path of the group instance or value the refusal is about
     * @param term         the identifier of the business term or group the refusal is about
     * @throws NullPointerException if an argument is {@code null}
     */
    public PolicyPreconditionException(String policy, String precondition, String message,
                                       SemanticPath path, String term) {
        super(Objects.requireNonNull(message, "message"));
        this.policy = Objects.requireNonNull(policy, "policy");
        this.precondition = Objects.requireNonNull(precondition, "precondition");
        this.path = Objects.requireNonNull(path, "path");
        this.term = Objects.requireNonNull(term, "term");
    }

    /**
     * Returns which policy refused.
     *
     * @return the policy name, for instance {@code GROSS_UNIT_AUTHORING}
     */
    public String policy() {
        return policy;
    }

    /**
     * Returns which precondition failed.
     *
     * @return the precondition, for instance {@code no line allowance or line charge}
     */
    public String precondition() {
        return precondition;
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
     * @return the identifier, for instance {@code BT-B2C-001}
     */
    public String term() {
        return term;
    }
}
