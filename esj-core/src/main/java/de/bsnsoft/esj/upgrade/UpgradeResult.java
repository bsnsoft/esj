package de.bsnsoft.esj.upgrade;

import de.bsnsoft.esj.SemanticDocument;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * What one run of {@link EditionUpgrade} produced: a document of the target edition, or
 * the reasons there is none.
 *
 * @param outcome  whether a document was written
 * @param document the document, absent where the run refused
 * @param report   what the run did and what it leaves to the caller
 */
public record UpgradeResult(Outcome outcome,
                            Optional<SemanticDocument> document,
                            UpgradeReport report) {

    /**
     * Checks that every part is there and that the outcome and the document agree.
     *
     * @param outcome  whether a document was written
     * @param document the document, absent where the run refused
     * @param report   what the run did and what it leaves to the caller
     * @throws NullPointerException     if a part is {@code null}
     * @throws IllegalArgumentException if an upgrade carries no document, or a refusal
     *                                  carries one
     */
    public UpgradeResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(report, "report");
        if (document.isPresent() != (outcome == Outcome.UPGRADED)) {
            throw new IllegalArgumentException(
                    "an upgrade carries a document and a refusal carries none");
        }
    }

    /**
     * Returns the document, for a caller that has established that there is one.
     *
     * @return the document of the target edition
     * @throws NoSuchElementException if the run refused
     */
    public SemanticDocument require() {
        return document.orElseThrow(() -> new NoSuchElementException(
                "the upgrade refused: " + report.refusals()));
    }

    /**
     * Tells whether a document was written.
     *
     * @return {@code true} where the run produced a document
     */
    public boolean isUpgraded() {
        return outcome == Outcome.UPGRADED;
    }

    /** Whether a run produced a document. */
    public enum Outcome {

        /** A document of the target edition was written. */
        UPGRADED,

        /** Nothing was written, and the report says why. */
        REFUSED
    }
}
