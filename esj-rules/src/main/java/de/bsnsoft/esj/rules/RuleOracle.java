package de.bsnsoft.esj.rules;

import java.util.Objects;
import java.util.Optional;

/**
 * What stands behind a rule: the evidence that the rule says what the edition it is written
 * for says.
 *
 * <p>A rule pack of this project is not an authority on the business rules of a standard,
 * and the strength of the evidence for a rule is not the same for every rule of every pack.
 * For the edition the official validation artefacts of CEN/TC 434 cover, a rule is measured
 * against those artefacts over the same input. For an edition no artefact release covers,
 * that measurement is not available, and a pack that said nothing about the difference would
 * be claiming the stronger of the two. So every rule declares which of the three it is, the
 * coverage page of the pack counts them, and a report can say what a finding rests on.
 *
 * <p>None of the three is a conformance claim. The wording the documentation of this project
 * uses for the two weaker kinds is that the rule is <em>not corroborated by an official
 * artefact</em>, never that it is validated and never that it is official.
 */
public enum RuleOracle {

    /**
     * The rule was measured against the official validation artefacts of a release, over the
     * same document, and the ledger of the pack carries the figures.
     */
    ARTEFACT("artefact"),

    /**
     * The rule is unchanged against the edition an artefact release does cover, and was
     * measured against those artefacts over the document written down to that edition with
     * {@code esj upgrade}. The evidence is the artefact's, one edition removed; what it
     * cannot show is that the edition this pack is written for left the rule alone, which is
     * a reading of the two texts and is recorded in the coverage page.
     */
    DOWNGRADE("downgrade"),

    /**
     * The rule is weighed by hand-computed cases alone. Every rule of the pack carries at
     * least one document that passes it and one that fails it, with the arithmetic written
     * out where it is arithmetic; what such a case shows is that the engine decides what the
     * rule was written to say, never that what was written is what the standard means.
     */
    CASES("cases");

    private final String token;

    RuleOracle(String token) {
        this.token = token;
    }

    /**
     * Returns the token a pack file writes this oracle with.
     *
     * @return the token, for example {@code downgrade}
     */
    public String token() {
        return token;
    }

    /**
     * Returns the oracle a pack file names.
     *
     * @param token the token as the file writes it
     * @return the oracle, or an empty optional if no oracle is written that way
     * @throws NullPointerException if {@code token} is {@code null}
     */
    public static Optional<RuleOracle> declared(String token) {
        Objects.requireNonNull(token, "token");
        for (RuleOracle oracle : values()) {
            if (oracle.token.equals(token)) {
                return Optional.of(oracle);
            }
        }
        return Optional.empty();
    }
}
