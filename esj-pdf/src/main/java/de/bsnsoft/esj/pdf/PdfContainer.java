package de.bsnsoft.esj.pdf;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

/**
 * A PDF opened as a container: its attachments, its XMP packet and what it declares about
 * itself.
 *
 * <p>Nothing here is about what a page shows. The object structure is read; every place a
 * file can be embedded from is enumerated — the embedded files name tree, the catalog's
 * associated files array, and on every page its associated files array, its file
 * attachment annotations and their associated files arrays; the metadata stream is read,
 * and that is the whole of it. In particular this module never renders a page, never reads
 * its content stream, never loads a font, never processes a form or an annotation action,
 * never executes JavaScript — PDFBox executes none — and never dereferences a reference
 * that leaves the file. A file specification that names a file without embedding it is
 * reported as carrying no content and is not fetched.
 *
 * <p>Every step is bounded by the {@link PdfLimits} the container was opened with: the
 * bytes of the file before anything is parsed, the number of attachments enumerated and of
 * the entries that name them, the bytes one attachment decodes to, the bytes all of them
 * decode to together, the size of the XMP packet, everything one container decodes — the
 * streams the library reads out of the file while it opens it as well as the streams this
 * module decodes itself — the objects the object streams of the file declare, and as many
 * objects again walked on its pages. Reaching one of them raises
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

    /** The subtype of an annotation that carries a file (PDF 32000-1, 12.5.6.15). */
    private static final COSName FILE_ATTACHMENT = COSName.getPDFName("FileAttachment");

    private final PDDocument document;
    private final PdfLimits limits;
    private final DecodeBudget budget;
    private final List<ContainerFinding> structure;

    private List<EmbeddedFile> embeddedFiles;
    private List<SeveralFiles> severalFiles;
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
     * entries of the embedded files name tree first, in the order a walk of the tree meets
     * them, then any entry of the catalog's associated files array that the name tree does
     * not hold, in the order of the array.
     *
     * <p>One attachment is one embedded file stream. Two entries that name the same stream
     * are one attachment, whether they are two entries of the tree, an entry of the tree
     * and an entry of the array, or two file specifications that embed the same stream;
     * two entries that name two streams are two attachments, whatever their keys and
     * names say.
     *
     * @return the attachments, possibly none
     */
    public List<EmbeddedFile> embeddedFiles() {
        return embeddedFiles;
    }

    /**
     * A file specification whose embedded file dictionary holds more than one stream.
     *
     * <p>A reader that follows the file specification takes one of its entries — most take
     * {@code /F}, some prefer {@code /UF} — so two streams under one specification are two
     * files behind one name, and which of them a reader is handed depends on the reader.
     *
     * @param name         the name the file specification gives
     * @param objectNumber the object number of the file specification, or {@code -1} where
     *                     it is written inline
     * @param files        the attachments, one per stream, in the order of the entries
     * @param entries      the entry each of them stands under first, in the same order
     */
    record SeveralFiles(String name, long objectNumber, List<EmbeddedFile> files,
                        List<String> entries) {
    }

    /**
     * Returns the file specifications that hold more than one stream, in the order they
     * were met.
     */
    List<SeveralFiles> severalFiles() {
        return severalFiles;
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
     * Walks every place of the file that can refer to an embedded file — the embedded
     * files name tree, the catalog's associated files array, and the pages — and builds
     * one attachment per embedded file stream.
     *
     * <p>The tree is walked here, entry by entry, and not through the map the library
     * builds of it. A map has one value per key, and a tree written to confuse its reader
     * carries one key twice: loaded into a map, the second entry replaces the first and
     * the first is gone without a trace, while a reader that walks the array takes the
     * first. Walking the entries keeps both, and {@link ContainerChecks} reports the key.
     *
     * <p>The places are joined on the identity of the embedded file stream rather than on
     * a name, because the name is the one thing in a PDF that may be written twice with
     * two different spellings and is attacker-controlled besides. A file specification
     * that embeds no stream is its own attachment and is joined on its own identity.
     */
    private void enumerate() {
        PDDocumentCatalog catalog = catalog();
        List<COSDictionary> associated = associatedFiles(catalog);
        Set<COSDictionary> inArray = Collections.newSetFromMap(new IdentityHashMap<>());
        inArray.addAll(associated);
        Enumeration enumeration = new Enumeration(inArray);
        COSDictionary tree = embeddedFilesTree(catalog);
        if (tree != null) {
            walk(tree, 0, new TreeWalk(), enumeration);
        }
        for (COSDictionary specification : associated) {
            enumeration.add(specification, EmbeddedFile.Place.DOCUMENT_ASSOCIATED_FILES, 0,
                    null);
        }
        pages(catalog, enumeration);
        enumeration.build();
    }

    /**
     * Walks the page tree and enumerates, page by page, what refers to an embedded file:
     * the associated files array of the page, the file specification of every file
     * attachment annotation, and the associated files array of every annotation.
     *
     * <p>A file a page refers to is a file of the document as much as one the name tree
     * lists: a viewer shows it on the page, and a reader that collects the attachments of a
     * file collects it. So it is an attachment here, with the page it belongs to; where it
     * is also listed in the tree it is the same attachment, joined on its stream.
     *
     * <p>The walk is iterative and takes every node once, so a page tree that loops or
     * shares a node costs no more than one that does not. It is bounded by the objects it
     * visits — the nodes of the tree, the pages, the entries of the arrays it reads and
     * the annotations — which may be no more than the objects the object streams of one
     * container may declare: {@link PdfLimits#maxObjectStreamObjects()}, the number this
     * reader already holds the objects of a file to.
     */
    private void pages(PDDocumentCatalog catalog, Enumeration enumeration) {
        COSDictionary root = catalog.getCOSObject().getCOSDictionary(COSName.PAGES);
        if (root == null) {
            return;
        }
        PageWalk walk = new PageWalk();
        Deque<COSDictionary> pending = new ArrayDeque<>();
        Set<COSDictionary> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.push(root);
        int page = 0;
        while (!pending.isEmpty()) {
            COSDictionary node = pending.pop();
            if (!seen.add(node)) {
                continue;
            }
            walk.visit();
            if (COSName.PAGES.equals(node.getCOSName(COSName.TYPE))
                    || node.containsKey(COSName.KIDS)) {
                COSArray kids = node.getCOSArray(COSName.KIDS);
                if (kids != null) {
                    // Pushed last to first, so that the first child is taken first and the
                    // pages are numbered in the order of the tree.
                    for (int i = kids.size() - 1; i >= 0; i--) {
                        walk.visit();
                        if (kids.getObject(i) instanceof COSDictionary kid) {
                            pending.push(kid);
                        }
                    }
                }
                continue;
            }
            page++;
            associatedFiles(node, EmbeddedFile.Place.PAGE_ASSOCIATED_FILES, page, walk,
                    enumeration);
            COSArray annotations = node.getCOSArray(COSName.ANNOTS);
            if (annotations == null) {
                continue;
            }
            for (int i = 0; i < annotations.size(); i++) {
                walk.visit();
                if (!(annotations.getObject(i) instanceof COSDictionary annotation)) {
                    continue;
                }
                if (FILE_ATTACHMENT.equals(annotation.getCOSName(COSName.SUBTYPE))
                        && annotation.getDictionaryObject(COSName.FS)
                                instanceof COSDictionary specification) {
                    enumeration.add(specification,
                            EmbeddedFile.Place.FILE_ATTACHMENT_ANNOTATION, page, null);
                }
                associatedFiles(annotation, EmbeddedFile.Place.ANNOTATION_ASSOCIATED_FILES,
                        page, walk, enumeration);
            }
        }
    }

    /** Enumerates the file specifications of the associated files array of a page object. */
    private static void associatedFiles(COSDictionary owner,
                                        EmbeddedFile.Place place,
                                        int page,
                                        PageWalk walk,
                                        Enumeration enumeration) {
        if (!(owner.getDictionaryObject(AF) instanceof COSArray array)) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            walk.visit();
            if (array.getObject(i) instanceof COSDictionary specification) {
                enumeration.add(specification, place, page, null);
            }
        }
    }

    /** What one walk of the page tree has visited so far. */
    private final class PageWalk {

        private long visited;

        void visit() {
            visited++;
            if (visited > limits.maxObjectStreamObjects()) {
                throw new PdfLimitException("the pages of this file and what they refer to"
                        + " come to more than " + limits.maxObjectStreamObjects() + " objects,"
                        + " the number of objects this reader walks on the pages of a PDF");
            }
        }
    }

    private PDDocumentCatalog catalog() {
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        if (catalog == null) {
            throw new PdfFormatException("the file has no document catalog");
        }
        return catalog;
    }

    /** Returns the root of the embedded files name tree, where the catalog has one. */
    private static COSDictionary embeddedFilesTree(PDDocumentCatalog catalog) {
        COSDictionary names = catalog.getCOSObject().getCOSDictionary(COSName.NAMES);
        return names == null ? null : names.getCOSDictionary(COSName.EMBEDDED_FILES);
    }

    /**
     * Returns the file specifications the catalog's {@code /AF} array refers to, in the
     * order of the array. An entry that is not a dictionary is skipped rather than
     * reported: the array says which files the document is about, and a malformed entry
     * names no file.
     *
     * <p>The array is bounded like the tree: it names attachments, and an array longer
     * than the number of attachments this reader enumerates is one it does not read.
     */
    private List<COSDictionary> associatedFiles(PDDocumentCatalog catalog) {
        List<COSDictionary> associated = new ArrayList<>();
        COSBase base = catalog.getCOSObject().getDictionaryObject(AF);
        if (!(base instanceof COSArray array)) {
            return associated;
        }
        if (array.size() > limits.maxEmbeddedFiles()) {
            throw new PdfLimitException("the associated files array of this file has "
                    + array.size() + " entries, more than the " + limits.maxEmbeddedFiles()
                    + " attachments this reader enumerates");
        }
        for (int i = 0; i < array.size(); i++) {
            if (array.getObject(i) instanceof COSDictionary specification) {
                associated.add(specification);
            }
        }
        return associated;
    }

    private static String relationship(COSDictionary specification) {
        COSBase value = specification.getDictionaryObject(AF_RELATIONSHIP);
        return value instanceof COSName name ? name.getName() : null;
    }

    /**
     * Walks one node of the embedded files name tree and its children, in the order the
     * file writes them: the entries of the node, then its children one after the other.
     *
     * <p>Three bounds hold the walk. The depth, because a tree is walked by recursion; the
     * entries, because every entry names an attachment and a tree with more entries than
     * this reader enumerates attachments is one it does not read; and the nodes that carry
     * no entry of their own, which a tree needs fewer of than it has entries and a hostile
     * one could otherwise multiply without end. A node met a second time — a cycle, or a
     * node two parents share — is walked once: it names the same entries both times.
     */
    private void walk(COSDictionary node,
                      int depth,
                      TreeWalk walked,
                      Enumeration enumeration) {
        if (depth > MAX_NAME_TREE_DEPTH) {
            throw new PdfLimitException("the embedded files name tree of this file nests more"
                    + " than " + MAX_NAME_TREE_DEPTH + " levels deep, which this reader"
                    + " does not walk");
        }
        if (!walked.nodes.add(node)) {
            return;
        }
        COSArray names = node.getCOSArray(COSName.NAMES);
        if (names == null || names.size() < 2) {
            walked.withoutEntries++;
            if (walked.withoutEntries > limits.maxEmbeddedFiles()) {
                throw new PdfLimitException("the embedded files name tree of this file has"
                        + " more than " + limits.maxEmbeddedFiles() + " nodes that list no"
                        + " file, more than a tree of the " + limits.maxEmbeddedFiles()
                        + " attachments this reader enumerates needs");
            }
        }
        if (names != null) {
            for (int i = 0; i + 1 < names.size(); i += 2) {
                if (!(names.getObject(i) instanceof COSString key)) {
                    throw new PdfFormatException("the embedded files name tree could not be"
                            + " read: the entry at index " + i + " of a node has no string"
                            + " for its key");
                }
                COSBase value = names.getObject(i + 1);
                if (value != null && !(value instanceof COSDictionary)) {
                    throw new PdfFormatException("the embedded files name tree could not be"
                            + " read: the entry " + Messages.quoted(key.getString())
                            + " names no file specification");
                }
                walked.entries++;
                if (walked.entries > limits.maxEmbeddedFiles()) {
                    throw new PdfLimitException("the embedded files name tree of this file"
                            + " lists more entries than the " + limits.maxEmbeddedFiles()
                            + " attachments this reader enumerates");
                }
                // An entry whose value is missing names no file. It is kept as an
                // attachment with no content, as a file specification that embeds
                // nothing is, so that the listing says the tree carries it.
                enumeration.add(value == null ? new COSDictionary() : (COSDictionary) value,
                        EmbeddedFile.Place.NAME_TREE, 0, key.getString());
            }
        }
        COSArray kids = node.getCOSArray(COSName.KIDS);
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                if (kids.getObject(i) instanceof COSDictionary kid) {
                    walk(kid, depth + 1, walked, enumeration);
                }
            }
        }
    }

    /** What one walk of the name tree has met so far. */
    private static final class TreeWalk {

        private final Set<COSDictionary> nodes =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private int entries;
        private int withoutEntries;
    }

    /**
     * The attachments of the container as the places that refer to them are met, joined
     * on the identity of the embedded file stream.
     */
    private final class Enumeration {

        private final Set<COSDictionary> inArray;

        /**
         * The attachments of the streams met so far, and apart from them those of the
         * file specifications that embed no stream: a stream the file also offers as a
         * file specification in its own right must not be taken for one that embeds
         * nothing.
         */
        private final Map<COSStream, Found> byStream = new IdentityHashMap<>();
        private final Map<COSDictionary, Found> withoutStream = new IdentityHashMap<>();
        private final List<Found> ordered = new ArrayList<>();
        private final Map<COSDictionary, Several> severalBySpecification =
                new IdentityHashMap<>();

        Enumeration(Set<COSDictionary> inArray) {
            this.inArray = inArray;
        }

        /**
         * Records that a place refers to a file specification.
         *
         * @param specification the file specification
         * @param place         what refers to it
         * @param page          the page the place belongs to, or {@code 0}
         * @param key           the key of the name tree entry, or {@code null} for a place
         *                      that is no entry of the tree
         */
        void add(COSDictionary specification, EmbeddedFile.Place place, int page, String key) {
            List<COSStream> streams = new ArrayList<>();
            List<String> entries = new ArrayList<>();
            streams(specification, streams, entries);
            if (streams.isEmpty()) {
                Found attachment = withoutStream.get(specification);
                if (attachment == null) {
                    attachment = found(specification, null);
                    withoutStream.put(specification, attachment);
                }
                record(attachment, specification, place, page, key, "");
                return;
            }
            List<Found> several = new ArrayList<>(streams.size());
            for (int i = 0; i < streams.size(); i++) {
                Found attachment = byStream.get(streams.get(i));
                if (attachment == null) {
                    attachment = found(specification, new PDEmbeddedFile(streams.get(i)));
                    byStream.put(streams.get(i), attachment);
                }
                record(attachment, specification, place, page, key, entries.get(i));
                several.add(attachment);
            }
            if (several.size() > 1 && !severalBySpecification.containsKey(specification)) {
                severalBySpecification.put(specification, new Several(specification,
                        several, List.copyOf(entries)));
            }
        }

        /** Returns a new attachment, within the bound on how many there are. */
        private Found found(COSDictionary specification, PDEmbeddedFile stream) {
            if (ordered.size() >= limits.maxEmbeddedFiles()) {
                throw new PdfLimitException("the file carries more than the "
                        + limits.maxEmbeddedFiles() + " attachments this reader"
                        + " enumerates");
            }
            Found attachment = new Found(specification, stream);
            ordered.add(attachment);
            return attachment;
        }

        private void record(Found attachment, COSDictionary specification,
                            EmbeddedFile.Place place, int page, String key, String entry) {
            if (key != null && !attachment.keys.contains(key)) {
                attachment.keys.add(key);
            }
            EmbeddedFile.Reference reference = new EmbeddedFile.Reference(place, page, entry);
            if (!attachment.references.contains(reference)) {
                attachment.references.add(reference);
            }
            if (attachment.associated == null && inArray.contains(specification)) {
                attachment.associated = specification;
            }
        }

        /** Builds the attachments and the specifications that hold several of them. */
        void build() {
            Map<Found, EmbeddedFile> built = new IdentityHashMap<>();
            List<EmbeddedFile> files = new ArrayList<>(ordered.size());
            for (Found attachment : ordered) {
                EmbeddedFile file = attachment.build();
                built.put(attachment, file);
                files.add(file);
            }
            List<SeveralFiles> several = new ArrayList<>();
            for (Several specification : severalBySpecification.values()) {
                COSObjectKey key = specification.specification().getKey();
                several.add(new SeveralFiles(
                        nameOf(specification.specification(), ""),
                        key == null ? -1 : key.getNumber(),
                        specification.found().stream().map(built::get).toList(),
                        specification.entries()));
            }
            embeddedFiles = List.copyOf(files);
            severalFiles = List.copyOf(several);
        }
    }

    /** A file specification that holds several streams, while the enumeration runs. */
    private record Several(COSDictionary specification, List<Found> found,
                           List<String> entries) {
    }

    /**
     * Collects the streams of the embedded file dictionary of a file specification, each
     * once, with the first entry it stands under, in the order {@code F}, {@code UF},
     * {@code DOS}, {@code Mac}, {@code Unix}.
     *
     * <p>Every entry is read and not only the first. The entries are meant to name one
     * file for several platforms, and a specification that puts two different streams
     * under them is one whose readers do not agree on what it holds: most take
     * {@code /F}, PDF 2.0 prefers {@code /UF}.
     */
    private static void streams(COSDictionary specification,
                                List<COSStream> streams,
                                List<String> entries) {
        COSDictionary files = specification.getCOSDictionary(COSName.EF);
        if (files == null) {
            return;
        }
        for (String entry : ENTRIES) {
            if (files.getDictionaryObject(COSName.getPDFName(entry)) instanceof COSStream stream
                    && streams.stream().noneMatch(seen -> seen == stream)) {
                streams.add(stream);
                entries.add(entry);
            }
        }
    }

    /**
     * Returns the name a file specification gives, and the fallback where it gives none.
     */
    private static String nameOf(COSDictionary specification, String fallback) {
        String name = new PDComplexFileSpecification(specification).getFilename();
        return name == null || name.isEmpty() ? fallback : name;
    }

    /** One attachment while the places that refer to it are still being met. */
    private final class Found {

        /** The first file specification that referred to it. */
        private final COSDictionary specification;
        private final PDEmbeddedFile stream;
        private final List<String> keys = new ArrayList<>();
        private final List<EmbeddedFile.Reference> references = new ArrayList<>();

        /** The first of its file specifications the catalog's array lists, if any. */
        private COSDictionary associated;

        Found(COSDictionary specification, PDEmbeddedFile stream) {
            this.specification = specification;
            this.stream = stream;
        }

        EmbeddedFile build() {
            String name = nameOf(specification, keys.isEmpty() ? "" : keys.get(0));
            // What the document says the file is to it is what its associated files array
            // says, where the array lists it; otherwise what the file specification that
            // referred to it first says.
            String relationship = relationship(associated != null ? associated : specification);
            long size = -1;
            String mediaType = null;
            long number = -1;
            if (stream != null) {
                mediaType = stream.getSubtype();
                COSDictionary parameters =
                        stream.getCOSObject().getCOSDictionary(COSName.PARAMS);
                if (parameters != null && parameters.containsKey(COSName.SIZE)) {
                    size = parameters.getLong(COSName.SIZE, -1L);
                }
                COSObjectKey key = stream.getCOSObject().getKey();
                number = key == null ? -1 : key.getNumber();
            }
            return new EmbeddedFile(name, mediaType, size, relationship, associated != null,
                    keys, references, number, stream, PdfContainer.this);
        }
    }

    /**
     * The entries of an embedded file dictionary, in the order a stream is taken from
     * (PDF 32000-1, 7.11.4).
     */
    private static final List<String> ENTRIES = List.of("F", "UF", "DOS", "Mac", "Unix");

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
