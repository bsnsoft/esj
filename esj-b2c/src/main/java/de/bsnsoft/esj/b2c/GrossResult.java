package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.invoice.InvoiceResult;
import de.bsnsoft.esj.typed.build.BuildReport;
import java.util.Objects;

/**
 * What {@link GrossInvoice#build(GrossAuthoring)} returns: what the domain API returns, and
 * beside it what the gross authoring policy did to get there.
 *
 * @param invoice   the invoice and what the check of it found, as
 *                  {@code Draft.buildReport()} returns them
 * @param authoring what the policy wrote, where it cut a price and what it set BT-114 to
 */
public record GrossResult(InvoiceResult invoice, AuthoringReport authoring) {

    /**
     * Checks that both parts are present.
     *
     * @param invoice   the invoice and what the check of it found, as
     *                  {@code Draft.buildReport()} returns them
     * @param authoring what the policy wrote, where it cut a price and what it set BT-114 to
     * @throws NullPointerException if a part is {@code null}
     */
    public GrossResult {
        Objects.requireNonNull(invoice, "invoice");
        Objects.requireNonNull(authoring, "authoring");
    }

    /**
     * Returns the invoice.
     *
     * @return an immutable document in canonical path order
     */
    public SemanticDocument document() {
        return invoice.document();
    }

    /**
     * Returns what the check of the invoice found.
     *
     * @return the structural findings, the missing members and what the business rules had to
     *         say
     */
    public BuildReport report() {
        return invoice.report();
    }

    /**
     * Tells whether the check found nothing that refuses the invoice.
     *
     * @return whether the invoice is ready
     */
    public boolean ok() {
        return invoice.ok();
    }
}
