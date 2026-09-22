package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.imports.ImportReport;
import de.bsnsoft.esj.imports.ImportResult;
import java.util.Objects;

/**
 * The outcome of reading a PDF: the invoice that was read out of it, which attachment it
 * came from, and what the container had to say.
 *
 * @param invoice        the document and the notes of the XML import, exactly as reading
 *                       the same XML on its own would have produced them
 * @param pdf            what the container had to say
 * @param attachment     the attachment the invoice was read from
 * @param en16931Invoice whether the profile of this document is one the rules of
 *                       EN 16931 apply to
 */
public record PdfImportResult(ImportResult invoice,
                              PdfReport pdf,
                              LocatedAttachment attachment,
                              boolean en16931Invoice) {

    /**
     * Checks that no member is {@code null}.
     *
     * @param invoice        the document and the notes of the XML import, exactly as reading
     *                       the same XML on its own would have produced them
     * @param pdf            what the container had to say
     * @param attachment     the attachment the invoice was read from
     * @param en16931Invoice whether the profile of this document is one the rules of
     *                       EN 16931 apply to
     * @throws NullPointerException if a member is {@code null}
     */
    public PdfImportResult {
        Objects.requireNonNull(invoice, "invoice");
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(attachment, "attachment");
    }

    /**
     * Returns the semantic document.
     *
     * @return the document
     */
    public SemanticDocument document() {
        return invoice.document();
    }

    /**
     * Returns what the XML importer had to say about the attachment.
     *
     * @return the import report
     */
    public ImportReport importReport() {
        return invoice.report();
    }

    /**
     * Tells whether the rules of EN 16931 are the right question to ask of this document.
     *
     * <p>They are not for the two smallest profiles of the Factur-X family: MINIMUM
     * carries little more than the totals and BASIC WL carries no invoice line, so
     * running the rules against either produces a list of missing mandatory elements that
     * says nothing about the document. A caller says that instead of answering wrongly;
     * see {@link FacturXProfile}.
     *
     * <p>Where the profile is not known at all this is {@code true}: nothing said that
     * the document is not an EN 16931 invoice, and assuming the opposite would refuse to
     * check a perfectly ordinary one.
     *
     * @return {@code false} for MINIMUM and BASIC WL, {@code true} otherwise
     */
    @Override
    public boolean en16931Invoice() {
        return en16931Invoice;
    }
}
