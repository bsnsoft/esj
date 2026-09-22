package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.imports.ImportResult;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the electronic invoice out of a hybrid PDF.
 *
 * <p>Four steps, in this order: open the container, classify every attachment by its
 * bytes, take the one that is an invoice, and hand it to the importer of {@code esj-xr}.
 * From the fourth step on nothing is different from reading the same XML on its own, and
 * that is the point: the PDF is a container and the invoice inside it is the document.
 *
 * <p>Three of the steps can end without a document, and each of them ends differently,
 * because the three answers are not interchangeable for the caller that has to act on
 * them: the file carries no invoice at all, it carries more than one and nothing said
 * which, or it carries one in a format this project does not read. See
 * {@link PdfException} for the whole list and what each one means.
 *
 * <p><strong>Nothing is read off the page.</strong> A PDF whose invoice exists only as
 * printed text carries no structured invoice, and that is what is reported. There is no
 * optical character recognition here and no heuristic extraction of any kind.
 */
public final class PdfInvoiceImporter {

    /** The semantic path of BT-24, the specification identifier of the invoice. */
    private static final SemanticPath SPECIFICATION_IDENTIFIER = SemanticPath.of("/BG-2/BT-24");

    private PdfInvoiceImporter() {
        throw new AssertionError("no instances");
    }

    /**
     * Reads the invoice out of a PDF.
     *
     * @param pdf      the bytes of the file
     * @param limits   what this reader is willing to spend on it
     * @param importer the importer the attachment is handed to, which decides the
     *                 registry, the reader limits and what happens to a document whose
     *                 bytes are not written in the encoding it declares
     * @return the invoice, the attachment it came from and what the container had to say
     * @throws PdfLimitException                   if a bound of {@code limits} was
     *                                             reached, which is never a verdict on
     *                                             the invoice
     * @throws PdfAccessException                  if the file is encrypted, which is
     *                                             refused before anything is decrypted
     * @throws PdfFormatException                  if the bytes are no PDF this reader
     *                                             opens
     * @throws NoInvoiceAttachmentException        if no attachment spells an invoice
     * @throws AmbiguousInvoiceAttachmentException if more than one does
     * @throws UnsupportedInvoiceException         if the one that does is ZUGFeRD 1.0
     * @throws NullPointerException                if an argument is {@code null}
     */
    public static PdfImportResult importPdf(byte[] pdf, PdfLimits limits, XrImporter importer) {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(importer, "importer");
        try (PdfContainer container = PdfContainer.open(pdf, limits)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);
            LocatedAttachment attachment = located.single();
            if (!attachment.kind().isSupported()) {
                throw new UnsupportedInvoiceException("the attachment " + attachment
                        + " is a " + attachment.kind().describe() + " invoice, which is no"
                        + " binding of EN 16931 and is not read by this project",
                        attachment);
            }
            AttachmentContent content = attachment.file().content();
            if (content.truncated()) {
                throw new PdfLimitException("the attachment " + attachment + " decodes to"
                        + " more than the " + limits.maxAttachmentBytes() + " bytes this"
                        + " reader holds, so it was cut off and no invoice was read from"
                        + " it");
            }
            ImportResult invoice = importer.importXmlWithReport(content.bytes());
            return new PdfImportResult(invoice,
                    report(container, located, invoice.document()),
                    attachment,
                    profile(container, invoice.document())
                            .map(FacturXProfile::isEn16931Invoice)
                            .orElse(Boolean.TRUE));
        }
    }

    /**
     * Reads the invoice out of a PDF with an importer of the caller's choosing and the
     * default limits.
     *
     * @param pdf      the bytes of the file
     * @param importer the importer the attachment is handed to
     * @return the invoice, the attachment it came from and what the container had to say
     * @throws NullPointerException if an argument is {@code null}
     */
    public static PdfImportResult importPdf(byte[] pdf, XrImporter importer) {
        return importPdf(pdf, PdfLimits.defaults(), importer);
    }

    /**
     * Reads the invoice out of a PDF with the default limits and a default importer.
     *
     * @param pdf the bytes of the file
     * @return the invoice, the attachment it came from and what the container had to say
     * @throws NullPointerException if {@code pdf} is {@code null}
     */
    public static PdfImportResult importPdf(byte[] pdf) {
        return importPdf(pdf, PdfLimits.defaults(), new XrImporter());
    }

    private static PdfReport report(PdfContainer container,
                                    InvoiceAttachments located,
                                    SemanticDocument document) {
        List<ContainerFinding> findings =
                new ArrayList<>(ContainerChecks.run(container, located));
        ContainerChecks.profileAgainstInvoice(container.facturX(), document)
                .ifPresent(findings::add);
        return new PdfReport(located.all(), findings, container.facturX(),
                container.pdfaIdentification(), profile(container, document));
    }

    /**
     * Returns the profile of the document: the one the invoice writes in BT-24 where it
     * writes one, and the one the XMP packet declares otherwise.
     *
     * <p>The invoice is asked first because the invoice is the document. The packet is a
     * claim the container makes about it, and where the two disagree that disagreement is
     * a finding rather than a reason to believe the container.
     */
    private static Optional<FacturXProfile> profile(PdfContainer container,
                                                    SemanticDocument document) {
        Optional<FacturXProfile> written = document.value(SPECIFICATION_IDENTIFIER)
                .map(SemanticValue::content)
                .flatMap(FacturXProfile::ofSpecificationIdentifier);
        return written.isPresent()
                ? written
                : container.facturX().flatMap(FacturXMetadata::profile);
    }
}
