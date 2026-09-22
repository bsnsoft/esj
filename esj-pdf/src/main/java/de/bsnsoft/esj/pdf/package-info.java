/**
 * Reads the electronic invoice out of a hybrid PDF, and writes one into a PDF/A-3 file.
 *
 * <p>A Factur-X, ZUGFeRD 2.x or XRechnung-in-PDF file is a PDF that carries the invoice
 * twice: once as the page a person reads, and once as an XML document attached to the
 * file. Only the second of the two is data. This module opens the container, enumerates
 * what is attached to it, decides by the bytes of each attachment what it is, and hands
 * the one invoice to the importer of {@code esj-xr}. From there nothing is different
 * from reading the same XML on its own.
 *
 * <p><strong>The page is never read.</strong> There is no optical character recognition,
 * no layout analysis and no heuristic or model-based extraction here, and none is
 * planned: EN 16931 is about structured data, and a PDF that carries none is reported as
 * carrying none rather than guessed at. That is a decision of the project and not a gap.
 *
 * <p>The entry point is {@link de.bsnsoft.esj.pdf.PdfInvoiceImporter}.
 * {@link de.bsnsoft.esj.pdf.PdfContainer} is the container on its own, for a
 * caller that wants the attachments and the metadata without an import, and
 * {@link de.bsnsoft.esj.pdf.ContainerChecks} produces the structural findings
 * about the container that sit beside the findings about the invoice.
 *
 * <p>{@link de.bsnsoft.esj.pdf.FacturX} is the other direction: a rendering of
 * the document and the document itself go in, and a hybrid invoice comes out — the
 * attachment, the associated file, the name tree and the Factur-X metadata, written into a
 * file that is still the PDF/A-3 file it was. Nothing is converted; see
 * {@link de.bsnsoft.esj.pdf.EmbedRefusedException} for what that rules out.
 *
 * <p>A PDF is a larger attack surface than an XML document, so everything here is
 * bounded: see {@link de.bsnsoft.esj.pdf.PdfLimits} for what, and the class
 * documentation of {@link de.bsnsoft.esj.pdf.PdfContainer} for what is turned
 * off.
 */
package de.bsnsoft.esj.pdf;
