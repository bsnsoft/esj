package de.bsnsoft.esj.imports;

/**
 * Reads an invoice written in an XML syntax into a semantic document.
 *
 * <p>Two readers implement it and answer alike: the streaming reader of
 * {@code esj-bindings}, which the command line runs by default and which reads without
 * building a tree, and the stylesheet importer of {@code esj-xr}. A caller that is handed
 * an XML invoice — the PDF importer of {@code esj-pdf}, for the attachment it found —
 * takes a reader of this type and leaves the choice to its own caller.
 *
 * <p>The syntax is read off the root element. What the input carried that the document
 * does not is not an exception but a note of the report, so a reader returns a document
 * for every input it can read at all.
 *
 * <p>An implementation is immutable and safe to share between threads.
 */
@FunctionalInterface
public interface InvoiceReader {

    /**
     * Reads a UBL 2.1 invoice or credit note, or a CII D16B invoice, recognized by its root
     * element.
     *
     * @param xml the bytes of the document
     * @return the document and what the reader had to say about it
     * @throws de.bsnsoft.esj.EsjLimitException          if the document is larger, or nests
     *                                                    deeper, than the reader accepts
     * @throws de.bsnsoft.esj.xml.XmlEncodingException  if the bytes are not written in the
     *                                                    encoding the document declares and
     *                                                    the reader was asked not to repair
     *                                                    that, or could not
     * @throws de.bsnsoft.esj.EsjException               of the implementing module if the
     *                                                    document cannot be read at all: its
     *                                                    root element belongs to no syntax
     *                                                    the reader knows, or it is not
     *                                                    well-formed XML the reader accepts
     * @throws NullPointerException                      if {@code xml} is {@code null}
     */
    ImportResult read(byte[] xml);
}
