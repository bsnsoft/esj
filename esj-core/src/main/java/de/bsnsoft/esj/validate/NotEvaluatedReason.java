package de.bsnsoft.esj.validate;

/**
 * Why a validation layer was not evaluated (specification, section 9.5). The vocabulary
 * is closed, so that a caller can branch on it without reading English.
 *
 * <p>The constants are declared in the order of their precedence: where two results about
 * the same document both leave a layer unevaluated for different reasons, the one
 * declared first is the one {@link ValidationResult#merge(ValidationResult)} keeps,
 * because it says more about why the answer is missing.
 *
 * <p>The order ranks the reasons a run established, not the ones that would have held had
 * it gone further. {@link #PRECEDING_LAYER_FAILED} therefore comes before
 * {@link #EDITION_UNKNOWN}: a run whose layer L1 failed never reached the edition
 * question, which is an L2 question, so an unknown edition was not established at all
 * (specification, sections 9.3 and 9.5).
 *
 * <p>An implementation that adds components of its own — a syntax validator, a rule pack
 * — extends this vocabulary with reasons of its own and documents them. It must not reuse
 * one of these four for a different situation.
 */
public enum NotEvaluatedReason {

    /**
     * A limit of the specification, section 12.2 stopped the run before this layer was
     * complete. It is a statement about the configuration of this implementation and not
     * about the document.
     */
    LIMIT("LIMIT"),

    /**
     * The layer could not run because an earlier one did not produce what it needs: a
     * document that could not be read has no paths for the model layers to look at. It
     * outranks {@link #EDITION_UNKNOWN}, because a run that stopped at layer L1 never
     * asked whether a registry describes the edition.
     */
    PRECEDING_LAYER_FAILED("PRECEDING-LAYER-FAILED"),

    /**
     * No registry is available for the edition the document names (specification,
     * sections 4.4 and 9.2), so there is nothing to measure the model layers against.
     */
    EDITION_UNKNOWN("EDITION-UNKNOWN"),

    /**
     * The caller asked for a subset of the layers and this one is outside it
     * (specification, section 3.5).
     */
    NOT_REQUESTED("NOT-REQUESTED");

    private final String token;

    NotEvaluatedReason(String token) {
        this.token = token;
    }

    /**
     * Returns the reason as the specification writes it.
     *
     * @return the token, for example {@code EDITION-UNKNOWN}
     */
    public String token() {
        return token;
    }

    /**
     * Returns the reason as the specification writes it.
     *
     * @return the token
     */
    @Override
    public String toString() {
        return token;
    }
}
