package de.bsnsoft.esj.imports;

import de.bsnsoft.esj.SemanticDocument;
import java.util.Objects;

/**
 * The outcome of an import: the document that was built and the report of what did not
 * reach it.
 *
 * @param document the semantic document
 * @param report   the observations the importer made
 */
public record ImportResult(SemanticDocument document, ImportReport report) {

    /**
     * Checks that neither member is {@code null}.
     *
     * @param document the semantic document
     * @param report   the observations the importer made
     * @throws NullPointerException if a member is {@code null}
     */
    public ImportResult {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(report, "report");
    }
}
