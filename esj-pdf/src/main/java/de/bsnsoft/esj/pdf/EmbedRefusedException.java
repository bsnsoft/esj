package de.bsnsoft.esj.pdf;

/**
 * Signals that a PDF this module reads perfectly well is not one it will write an invoice
 * into.
 *
 * <p>Embedding turns a rendering into a hybrid invoice, and a hybrid invoice is a PDF/A-3
 * file that carries exactly one electronic invoice and says in its metadata which one. A
 * file that cannot come out of that operation as such a document is refused rather than
 * changed into something that claims more than it is:
 *
 * <ul>
 *   <li>the file declares a part of ISO 19005 that is not 3, or declares none at all.
 *       Part 3 is the part that allows a file of any type to be embedded, and nothing here
 *       converts a document from one part to another — a PDF/A-1 or PDF/A-2 file would
 *       have to lose or gain features this module does not touch;</li>
 *   <li>the file already carries an attachment that could be the electronic invoice, so
 *       embedding a second one would produce a container a reader has to choose from;</li>
 *   <li>the embedded files name tree has children, or lists two files under one name:
 *       the tree is written back as one node that maps a name to one file, so a file it
 *       carried could be lost;</li>
 *   <li>the XMP packet is not one the Factur-X extension schema can be merged into;</li>
 *   <li>the invoice does not carry the specification identifier of the profile the caller
 *       asked for, so the container and the invoice would say two different things.</li>
 * </ul>
 *
 * <p>Every message names which of these it is and what the file says instead.
 */
public final class EmbedRefusedException extends PdfException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public EmbedRefusedException(String message) {
        super(message);
    }
}
