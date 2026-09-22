package de.bsnsoft.esj.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

/**
 * A PDF opened as a container: its attachments, its XMP packet and what it declares about
 * itself.
 *
 * <p>Nothing here is about the page. The object structure is read, the embedded files
 * name tree and the catalog's associated files array are enumerated, the metadata stream
 * is read, and that is the whole of it. In particular this module never renders a page,
 * never loads a font, never processes a form or an annotation action, never executes
 * JavaScript — PDFBox executes none — and never dereferences a reference that leaves the
 * file. A file specification that names a file without embedding it is reported as
 * carrying no content and is not fetched.
 *
 * <p>Every step is bounded by the {@link PdfLimits} the container was opened with: the
 * bytes of the file before anything is parsed, the number of attachments enumerated, the
 * bytes one attachment decodes to, the bytes all of them decode to together, the size of
 * the XMP packet, everything one container decodes — the streams the library reads out of
 * the file while it opens it as well as the streams this module decodes itself — and the
 * objects the object streams of the file declare. Reaching one of them raises
 * {@link PdfLimitException}, which is a statement about this reader's configuration and
 * never a verdict on the file.
 *
 * <p><strong>This reader never decrypts.</strong> It takes no password, it builds no
 * security handler, and no byte of any file it opens is ever deciphered — not even for a
 * file that would open with the empty user password. PDF/A forbids encryption, so a
 * hybrid invoice carries none. A file whose {@code /Encrypt} entry the parse sees is
 * refused with {@link PdfAccessException} before anything else happens, wherever the
 * parse meets it: in the trailer it ended with, in an object of the file body, or in an
 * object an object stream yielded. Where a damaged file hides the entry from all three,
 * the file is read as an ordinary container and its streams stay ciphertext: an
 * attachment of ciphertext decodes to nothing and is reported as unreadable.
 *
 * <p>A container holds an open PDFBox document and has to be closed. Instances are not
 * safe to share between threads: the attachments decode on demand and keep what they
 * decoded.
 */
public final class PdfContainer implements AutoCloseable {

    /** The first bytes of a PDF file (PDF 32000-1, 7.5.2). */
    private static final byte[] HEADER = {'%', 'P', 'D', 'F', '-'};

    /** The key of the associated files array, on the catalog and on a file specification. */
    private static final COSName AF = COSName.getPDFName("AF");

    /** The key of the relationship a file specification records. */
    private static final COSName AF_RELATIONSHIP = COSName.getPDFName("AFRelationship");

    /** How deep the embedded files name tree is walked. */
    private static final int MAX_NAME_TREE_DEPTH = 32;

    private final PDDocument document;
    private final PdfLimits limits;
    private final DecodeBudget budget;
    private final List<ContainerFinding> structure;

    private List<EmbeddedFile> embeddedFiles;
    private long decoded;
    private byte[] xmp;
    private boolean xmpRead;
    private XmpProperties properties;

    private PdfContainer(PDDocument document,
                         PdfLimits limits,
                         DecodeBudget budget,
                         List<ContainerFinding> structure) {
        this.document = document;
        this.limits = limits;
        this.budget = budget;
        this.structure = List.copyOf(structure);
    }

    /**
     * Opens a PDF.
     *
     * @param pdf    the bytes of the file
     * @param limits what this reader is willing to spend on it
     * @return the container, which the caller closes
     * @throws PdfLimitException    if the file is larger than {@link PdfLimits#maxPdfBytes()}
     * @throws PdfAccessException   if the file is encrypted
     * @throws PdfFormatException   if the bytes are no PDF, or the object structure is
     *                              damaged past recovery
     * @throws NullPointerException if an argument is {@code null}
     */
    public static PdfContainer open(byte[] pdf, PdfLimits limits) {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(limits, "limits");
        if (pdf.length > limits.maxPdfBytes()) {
            throw new PdfLimitException("the file is " + pdf.length + " bytes long, and this"
                    + " reader opens a PDF of at most " + limits.maxPdfBytes());
        }
        if (!startsWithHeader(pdf)) {
            throw new PdfFormatException("the bytes do not begin with the PDF file header");
        }
        List<ContainerFinding> structure = PdfStructure.inspect(pdf);
        // The object streams and cross-reference streams of a file are decoded by the
        // library while it opens the file, into a buffer that grows until the stream
        // ends. Every one of them passes a view of this module on the way, and this is
        // the budget those views measure against. See BoundedPdfParser.
        DecodeBudget budget = new DecodeBudget(limits.maxDecodedBytes(),
                limits.maxObjectStreamObjects());
        PDDocument document;
        try {
            document = BoundedPdfParser.load(new RandomAccessReadBuffer(pdf), budget);
        } catch (PdfException e) {
            // A bound of this reader, met inside the library: it is this module's own
            // answer and never a statement that the file could not be read.
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new PdfFormatException("the file could not be read as a PDF", e);
        }
        PdfContainer container = new PdfContainer(document, limits, budget, structure);
        try {
            container.enumerate();
        } catch (RuntimeException e) {
            container.close();
            throw e;
        }
        return container;
    }

    /**
     * Opens a PDF with the default limits.
     *
     * @param pdf the bytes of the file
     * @return the container, which the caller closes
     * @throws PdfLimitException    if the file is larger than the default bound
     * @throws PdfAccessException   if the file is encrypted
     * @throws PdfFormatException   if the bytes are no PDF
     * @throws NullPointerException if {@code pdf} is {@code null}
     */
    public static PdfContainer open(byte[] pdf) {
        return open(pdf, PdfLimits.defaults());
    }

    /**
     * Returns the limits this container was opened with.
     *
     * @return the limits
     */
    public PdfLimits limits() {
        return limits;
    }

    /**
     * Returns the budget of decoded bytes this container runs under, which the
     * attachments are decoded against as well.
     */
    DecodeBudget budget() {
        return budget;
    }

    /**
     * Returns the open document, for {@link FacturX}, which is the one thing in this
     * module that writes a PDF rather than reading one.
     *
     * <p>It is package-private and it stays package-private: a caller that could reach the
     * object model of a container could do to it whatever PDFBox allows, and everything
     * this module promises about what it does to a file — that no page is rendered, no
     * form is processed and nothing is decrypted — would then be a promise about this
     * module and not about the document a caller gets back.
     */
    PDDocument document() {
        return document;
    }

    /**
     * Returns the attachments of the container, in the order they were enumerated: the
     * entries of the embedded files name tree first, then any entry of the catalog's
     * associated files array that the name tree does not hold.
     *
     * @return the attachments, possibly none
     */
    public List<EmbeddedFile> embeddedFiles() {
        return embeddedFiles;
    }

    /**
     * Returns what was observed about the object structure of the file while it was
     * opened: a cross-reference offset that does not point at a cross-reference section,
     * or a missing end-of-file marker.
     *
     * <p>These are the {@link ContainerFinding.Category#PDF_STRUCTURE} findings, and they
     * are made here rather than in {@link ContainerChecks} because they are about the
     * bytes of the file, which are gone by the time the checks run.
     *
     * @return the findings, possibly none
     */
    public List<ContainerFinding> structureFindings() {
        return structure;
    }

    /**
     * Returns the XMP packet of the document catalog, as it stands.
     *
     * <p>The packet is XML written by whoever produced the file and is returned raw, so
     * that a caller can show it or hash it without this module having decided what it
     * means. It is read once and bounded by {@link PdfLimits#maxXmpBytes()}.
     *
     * @return the packet, or an empty optional where the catalog carries no metadata
     * @throws PdfLimitException if the packet is larger than the bound
     */
    public Optional<byte[]> xmpPacket() {
        readXmp();
        return Optional.ofNullable(xmp);
    }

    /**
     * Returns the Factur-X properties of the XMP packet, where the packet carries the
     * extension schema of Factur-X or of ZUGFeRD 2.x.
     *
     * @return the properties, or an empty optional where the packet is absent or carries
     *         no such property
     * @throws PdfLimitException if the XMP packet is larger than the bound
     */
    public Optional<FacturXMetadata> facturX() {
        return xmpProperties().facturX();
    }

    /**
     * Returns the PDF/A identification the XMP packet declares.
     *
     * <p>It is a declaration and nothing more: this module does not validate PDF/A
     * conformance and does not claim to. A file that declares PDF/A-3B may be anything at
     * all; what the declaration is worth is a question for a PDF/A validator.
     *
     * @return the declaration, or an empty optional where the packet declares none
     * @throws PdfLimitException if the XMP packet is larger than the bound
     */
    public Optional<PdfaIdentification> pdfaIdentification() {
        return xmpProperties().pdfa();
    }

    /** Closes the underlying document. */
    @Override
    public void close() {
        try {
            document.close();
        } catch (IOException e) {
            // Closing a document that was read from a byte array releases memory and
            // touches nothing that can fail in a way a caller could act on.
        }
    }

    /**
     * Reserves the decoded bytes of one attachment against the bound on all of them
     * together, and returns the bound that applies to this one.
     */
    long claim(String name) {
        long remaining = limits.maxTotalAttachmentBytes() - decoded;
        if (remaining <= 0) {
            throw new PdfLimitException("the attachments of this file decode to more than the "
                    + limits.maxTotalAttachmentBytes() + " bytes this reader holds, and "
                    + Messages.quoted(name) + " is past that bound");
        }
        long cap = Math.min(limits.maxAttachmentBytes(), remaining);
        decoded += cap;
        return cap;
    }

    /**
     * Gives back what an attachment reserved and did not use, so that the bound on all
     * attachments together counts the bytes that were decoded rather than the bytes that
     * might have been.
     */
    void settle(long reserved, long actual) {
        decoded -= reserved - actual;
    }

    /**
     * Walks the embedded files name tree and the catalog's associated files array, and
     * builds one attachment per file specification.
     *
     * <p>The two are joined on the identity of the file specification dictionary rather
     * than on the name, because the name is the one thing in a PDF that may be written
     * twice with two different spellings and is attacker-controlled besides.
     */
    private void enumerate() {
        Map<COSDictionary, EmbeddedFile> found = new IdentityHashMap<>();
        List<EmbeddedFile> ordered = new ArrayList<>();
        PDDocumentCatalog catalog = catalog();
        Map<COSDictionary, String> associated = associatedFiles(catalog);
        PDDocumentNameDictionary names = catalog.getNames();
        PDEmbeddedFilesNameTreeNode tree = names == null ? null : names.getEmbeddedFiles();
        if (tree != null) {
            walk(tree, 0, found, ordered, associated);
        }
        for (Map.Entry<COSDictionary, String> entry : associated.entrySet()) {
            if (!found.containsKey(entry.getKey())) {
                bound(ordered);
                EmbeddedFile file = build("", new PDComplexFileSpecification(entry.getKey()),
                        associated);
                found.put(entry.getKey(), file);
                ordered.add(file);
            }
        }
        this.embeddedFiles = List.copyOf(ordered);
    }

    private PDDocumentCatalog catalog() {
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        if (catalog == null) {
            throw new PdfFormatException("the file has no document catalog");
        }
        return catalog;
    }

    /**
     * Returns the file specifications the catalog's {@code /AF} array refers to, each with
     * the relationship it records. An entry that is not a dictionary is skipped rather
     * than reported: the array says which files the document is about, and a malformed
     * entry names no file.
     */
    private static Map<COSDictionary, String> associatedFiles(PDDocumentCatalog catalog) {
        Map<COSDictionary, String> associated = new IdentityHashMap<>();
        COSBase base = catalog.getCOSObject().getDictionaryObject(AF);
        if (!(base instanceof COSArray array)) {
            return associated;
        }
        for (int i = 0; i < array.size(); i++) {
            if (array.getObject(i) instanceof COSDictionary specification) {
                associated.put(specification, relationship(specification));
            }
        }
        return associated;
    }

    private static String relationship(COSDictionary specification) {
        COSBase value = specification.getDictionaryObject(AF_RELATIONSHIP);
        return value instanceof COSName name ? name.getName() : null;
    }

    private void walk(PDNameTreeNode<PDComplexFileSpecification> node,
                      int depth,
                      Map<COSDictionary, EmbeddedFile> found,
                      List<EmbeddedFile> ordered,
                      Map<COSDictionary, String> associated) {
        if (depth > MAX_NAME_TREE_DEPTH) {
            throw new PdfLimitException("the embedded files name tree of this file nests more"
                    + " than " + MAX_NAME_TREE_DEPTH + " levels deep, which this reader"
                    + " does not walk");
        }
        Map<String, PDComplexFileSpecification> entries;
        try {
            entries = node.getNames();
        } catch (IOException e) {
            throw new PdfFormatException("the embedded files name tree could not be read", e);
        }
        if (entries != null) {
            for (Map.Entry<String, PDComplexFileSpecification> entry : entries.entrySet()) {
                PDComplexFileSpecification specification = entry.getValue();
                if (specification == null
                        || found.containsKey(specification.getCOSObject())) {
                    continue;
                }
                bound(ordered);
                EmbeddedFile file = build(entry.getKey(), specification, associated);
                found.put(specification.getCOSObject(), file);
                ordered.add(file);
            }
        }
        List<PDNameTreeNode<PDComplexFileSpecification>> kids = node.getKids();
        if (kids != null) {
            for (PDNameTreeNode<PDComplexFileSpecification> kid : kids) {
                walk(kid, depth + 1, found, ordered, associated);
            }
        }
    }

    private void bound(List<EmbeddedFile> ordered) {
        if (ordered.size() >= limits.maxEmbeddedFiles()) {
            throw new PdfLimitException("the file carries more than the "
                    + limits.maxEmbeddedFiles() + " attachments this reader enumerates");
        }
    }

    private EmbeddedFile build(String key,
                               PDComplexFileSpecification specification,
                               Map<COSDictionary, String> associated) {
        COSDictionary dictionary = specification.getCOSObject();
        PDEmbeddedFile stream = embeddedStream(specification);
        String name = specification.getFilename();
        if (name == null || name.isEmpty()) {
            name = key;
        }
        String relationship = associated.containsKey(dictionary)
                ? associated.get(dictionary)
                : relationship(dictionary);
        long size = -1;
        String mediaType = null;
        if (stream != null) {
            mediaType = stream.getSubtype();
            COSDictionary parameters =
                    stream.getCOSObject().getCOSDictionary(COSName.PARAMS);
            if (parameters != null && parameters.containsKey(COSName.SIZE)) {
                size = parameters.getLong(COSName.SIZE, -1L);
            }
        }
        return new EmbeddedFile(name, mediaType, size, relationship,
                associated.containsKey(dictionary), stream, this);
    }

    /**
     * Returns the embedded file stream of a file specification, trying the platform
     * specific entries after the portable one, as PDF 32000-1, 7.11.4 allows.
     */
    private static PDEmbeddedFile embeddedStream(PDComplexFileSpecification specification) {
        PDEmbeddedFile stream = specification.getEmbeddedFile();
        if (stream != null) {
            return stream;
        }
        stream = specification.getEmbeddedFileUnicode();
        if (stream != null) {
            return stream;
        }
        stream = specification.getEmbeddedFileUnix();
        if (stream != null) {
            return stream;
        }
        stream = specification.getEmbeddedFileDos();
        return stream != null ? stream : specification.getEmbeddedFileMac();
    }

    private XmpProperties xmpProperties() {
        if (properties == null) {
            readXmp();
            properties = xmp == null ? XmpProperties.none() : XmpProperties.parse(xmp);
        }
        return properties;
    }

    private void readXmp() {
        if (xmpRead) {
            return;
        }
        xmpRead = true;
        PDMetadata metadata = catalog().getMetadata();
        if (metadata == null) {
            return;
        }
        // The packet is decoded under the bound rather than measured after the fact; see
        // BoundedStream. A metadata stream that inflates without end is as cheap to write
        // as an attachment that does, and it is read on every container.
        AttachmentContent packet = BoundedStream.decode(metadata, limits.maxXmpBytes(),
                limits.maxXmpBytes(), "the XMP packet of this file", budget);
        if (packet.truncated()) {
            throw new PdfLimitException("the XMP packet of this file is larger than the "
                    + limits.maxXmpBytes() + " bytes this reader holds");
        }
        xmp = packet.bytes();
    }

    private static boolean startsWithHeader(byte[] pdf) {
        if (pdf.length < HEADER.length) {
            return false;
        }
        for (int i = 0; i < HEADER.length; i++) {
            if (pdf[i] != HEADER[i]) {
                return false;
            }
        }
        return true;
    }
}
