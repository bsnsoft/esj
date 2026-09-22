package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.typed.build.BuildReport;
import java.util.Objects;

/**
 * What {@link Draft#buildReport()} returns: the invoice as far as it got, and what the
 * check of it found.
 *
 * <p>It is the answer for a caller that would rather look than be interrupted. Where
 * {@link #ok()} holds, the document is the same one {@link Draft#build()} would have
 * returned and the report may still carry what the rules said without refusing it; where
 * it does not, the document is what had been written when the check stopped, and it is not
 * a conformant invoice.
 *
 * @param document the invoice
 * @param report   the structural findings, the missing members and what the business rules
 *                 had to say
 */
public record InvoiceResult(SemanticDocument document, BuildReport report) {

    /**
     * Checks that both parts are present.
     *
     * @param document the invoice
     * @param report   the structural findings, the missing members and what the business rules
     *                 had to say
     * @throws NullPointerException if a part is {@code null}
     */
    public InvoiceResult {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(report, "report");
    }

    /**
     * Tells whether the check found nothing that refuses the invoice.
     *
     * @return whether the invoice is ready
     */
    public boolean ok() {
        return report.ok();
    }
}
