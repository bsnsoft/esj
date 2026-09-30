package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.bindings.WriteReport;
import java.util.Objects;

/**
 * The outcome of writing an invoice into a PDF: the hybrid file, and the report of what
 * the cross industry invoice inside it had no place for.
 *
 * <p>The two belong together for the reason {@code WriteResult} says: a caller that keeps
 * the bytes and drops the report has an archival file that may be missing something nobody
 * will ever notice again. A hybrid invoice is the one file where that matters most,
 * because the page a person reads and the invoice a machine reads are then two accounts of
 * the same document and only one of them was checked.
 *
 * <p>This is a class rather than a record because it carries bytes: a record would compare
 * two results by the identity of their arrays.
 */
public final class EmbedResult {

    private final byte[] pdf;
    private final WriteReport report;

    /**
     * Creates a result.
     *
     * @param pdf    the hybrid invoice
     * @param report what became of the document on the way into the attachment
     * @throws NullPointerException if an argument is {@code null}
     */
    public EmbedResult(byte[] pdf, WriteReport report) {
        this.pdf = Objects.requireNonNull(pdf, "pdf").clone();
        this.report = Objects.requireNonNull(report, "report");
    }

    /**
     * Returns the hybrid invoice.
     *
     * @return a copy of the bytes
     */
    public byte[] pdf() {
        return pdf.clone();
    }

    /**
     * Returns what did not reach the embedded cross industry invoice.
     *
     * @return the report of the writer
     */
    public WriteReport report() {
        return report;
    }

    /**
     * Returns the length and the report, which is what a message about a result needs.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return pdf.length + " bytes, " + report;
    }
}
