package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.bindings.WriteReport;
import java.util.Objects;
import java.util.Optional;

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
 * <p>It also says what became of the ESJ document that goes in beside the invoice, and it
 * says it as {@link EsjOutcome} rather than as a sentence: a file that comes back without
 * the attachment because the caller asked for none is a run that did what it was told, and
 * one that comes back without it because the two could not be shown to say the same thing
 * is a run its caller has to hear about. {@link #esjOmitted()} carries the words for
 * either, because an attachment that is quietly absent is the one a sender believes is
 * there.
 *
 * <p>This is a class rather than a record because it carries bytes: a record would compare
 * two results by the identity of their arrays.
 */
public final class EmbedResult {

    /** What became of the ESJ document that goes in beside the invoice. */
    public enum EsjOutcome {

        /** The file carries it. */
        ATTACHED,

        /** The caller turned it off, and the file was written as asked. */
        TURNED_OFF,

        /**
         * It was left out because it could not be shown to be the invoice beside it:
         * either the two are not two accounts of one invoice ({@link EsjAgreement}), or
         * the invoice that had just been written could not be read back to find out.
         */
        UNPROVEN
    }

    private final byte[] pdf;
    private final WriteReport report;
    private final EsjOutcome esj;
    private final String esjOmitted;

    /**
     * Creates a result.
     *
     * @param pdf        the hybrid invoice
     * @param report     what became of the document on the way into the attachment
     * @param esj        what became of the ESJ document beside the invoice
     * @param esjOmitted why the file carries none, which every outcome but
     *                   {@link EsjOutcome#ATTACHED} carries and that one does not
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the outcome and the reason do not go together
     */
    public EmbedResult(byte[] pdf, WriteReport report, EsjOutcome esj,
                       Optional<String> esjOmitted) {
        this.pdf = Objects.requireNonNull(pdf, "pdf").clone();
        this.report = Objects.requireNonNull(report, "report");
        this.esj = Objects.requireNonNull(esj, "esj");
        Objects.requireNonNull(esjOmitted, "esjOmitted");
        if (esjOmitted.isPresent() != (esj != EsjOutcome.ATTACHED)) {
            throw new IllegalArgumentException("a file that carries no ESJ document says"
                    + " why, and one that carries it has nothing to say: " + esj);
        }
        this.esjOmitted = esjOmitted.orElse(null);
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
     * Returns what became of the ESJ document beside the invoice.
     *
     * @return the outcome
     */
    public EsjOutcome esj() {
        return esj;
    }

    /**
     * Tells whether the ESJ document of the invoice was attached beside the XML.
     *
     * @return {@code true} if the file carries it
     */
    public boolean esjAttached() {
        return esj == EsjOutcome.ATTACHED;
    }

    /**
     * Returns why the file carries no ESJ document beside the invoice.
     *
     * @return the reason, or an empty optional where it carries one
     */
    public Optional<String> esjOmitted() {
        return Optional.ofNullable(esjOmitted);
    }

    /**
     * Returns the length and the report, which is what a message about a result needs.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return pdf.length + " bytes, " + report
                + (esjOmitted == null ? ", with the ESJ document"
                        : ", without the ESJ document: " + esjOmitted);
    }
}
