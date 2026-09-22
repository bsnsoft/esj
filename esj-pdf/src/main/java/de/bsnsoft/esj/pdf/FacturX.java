package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.BindingException;
import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.bindings.WriteResult;
import de.bsnsoft.esj.bindings.WriterOptions;
import de.bsnsoft.esj.json.Canonicalizer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.TreeMap;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

/**
 * Writes the invoice into the PDF that shows it.
 *
 * <p>This is the one operation of this module that produces a file rather than reading
 * one, and it is the second half of the lead use case: a rendering of the document goes
 * in, and what comes out is a hybrid invoice — the page a person reads and, attached to
 * it, the same invoice as a cross industry invoice, with the file saying in its metadata
 * that it carries one and which attachment it is. {@code PdfInvoiceImporter} reads such a
 * file back, and a document that went in comes out of it again.
 *
 * <p>What is written:
 *
 * <ul>
 *   <li>the <b>attachment</b>: the cross industry invoice of {@code CiiWriter}, as an
 *       embedded file stream with the media type {@code text/xml}, the size it decodes to
 *       and, where the document states one, the issue date of the invoice as the
 *       modification date the container specification asks the stream to carry;</li>
 *   <li>the <b>associated file</b>: the file specification is named by the catalog's
 *       {@code /AF} array and declares the relationship {@code Alternative}, which is
 *       what Factur-X asks for of an invoice that is the document rather than an
 *       enclosure of it;</li>
 *   <li>the <b>name tree</b>: the specification is also an entry of
 *       {@code /Names /EmbeddedFiles}, under the attachment name, which is where a reader
 *       that goes by names looks;</li>
 *   <li>the <b>metadata</b>: the Factur-X extension schema and its four properties are
 *       merged into the XMP packet the file already has (see {@code FacturXXmp});</li>
 *   <li>the <b>ESJ document</b>: the canonical bytes of the same invoice, as a second
 *       embedded file beside the first — {@link EsjAttachment} says what it is called and
 *       what it is declared as, and {@link EmbedOptions#withEsj(boolean)} turns it off.
 *       It is written only where the invoice XML that has just been written and the
 *       document are two accounts of one invoice, which is checked by reading that XML
 *       back; see below.</li>
 * </ul>
 *
 * <h2>The ESJ document beside the invoice</h2>
 *
 * <p>The XML stays the invoice, and the second attachment is an enclosure: it is declared
 * with the relationship of one and is never read as an invoice by anything in this
 * project. What it is for is the terms the syntax has no place for — a model extension
 * the XML cannot carry — and a consumer that would rather read the invoice than map it.
 *
 * <p>It is written only where it is true. The cross industry invoice that has just been
 * written is read back with the streaming reader and checked against the document with
 * {@link EsjAgreement}: the document has to hold together under the semantic model it
 * names, every value the XML states has to stand in it unchanged, and what it states
 * beyond that has to belong to terms the binding table does not bind. Where the writer had
 * to leave a core value out, that does not hold, and then no ESJ document is attached and
 * {@link EmbedResult#esjOmitted()} names the paths. The XML is embedded either way.
 *
 * <h2>What is refused</h2>
 *
 * <p>The input has to be a PDF/A-3 file, because part 3 of ISO 19005 is the part that
 * allows a file of any type to be embedded, and because the result is supposed to be one:
 * a rendering of {@code esj-render} is one, and by default that is read from what the
 * input declares about itself in its XMP packet. A caller that wants the stronger
 * statement lends a validator through {@link EmbedOptions#checkedWith(PdfaCheck)}.
 *
 * <p><strong>Nothing is converted.</strong> A PDF/A-1 or PDF/A-2 input is refused and the
 * message says so: moving a document between parts of ISO 19005 means changing what its
 * pages contain, which is not something a program that has not drawn those pages gets to
 * do quietly. The other refusals are in {@link EmbedRefusedException}.
 *
 * <p>What the writer of the attachment had no place for is not swallowed: it is the
 * report of {@link #embedWithReport}, and {@link #embed} is the short form for a caller
 * that has already seen it. A term the cross industry invoice cannot carry is a term the
 * archived record does not carry either, and the caller is the one who gets to decide
 * what that means.
 *
 * <p>Embedding the same document into the same file twice gives the same bytes. Nothing
 * of the machine, the moment or the run takes part: the only date written is BT-2, the
 * issue date of the invoice itself; the attachment is a function of the document; the
 * packet is fixed text; and the second element of the file identifier is a digest of the
 * file it identifies. That identifier is why the file is serialized twice — the digest of
 * a file cannot be inside it before the file exists — and the first element is the
 * input's own, because the pages are still the pages that came in.
 */
public final class FacturX {

    /** The semantic path of BT-24, the specification identifier of the invoice. */
    private static final SemanticPath SPECIFICATION_IDENTIFIER = SemanticPath.of("/BG-2/BT-24");

    /** The semantic path of BT-2, the issue date of the invoice. */
    private static final SemanticPath ISSUE_DATE = SemanticPath.of("/BT-2");

    /** The part of ISO 19005 that allows an arbitrary file to be embedded. */
    private static final int PDFA_PART = 3;

    /** The media type of the attachment, which is what an XML invoice is. */
    private static final String MEDIA_TYPE = "text/xml";

    /**
     * The relationship the attachment is declared with. Factur-X asks for
     * {@code Alternative} — the attached file is another form of the document itself —
     * for every profile that is an EN 16931 invoice, and {@link EmbedOptions} admits no
     * other profile.
     */
    private static final COSName ALTERNATIVE = COSName.getPDFName("Alternative");

    /**
     * The relationship the ESJ document is declared with: the value PDF 32000-2 defines
     * for a file that travels with the document rather than being it.
     */
    private static final COSName SUPPLEMENT =
            COSName.getPDFName(EsjAttachment.RELATIONSHIP);

    /** The key of the associated files array on the catalog. */
    private static final COSName AF = COSName.getPDFName("AF");

    /** The key of the relationship on a file specification. */
    private static final COSName AF_RELATIONSHIP = COSName.getPDFName("AFRelationship");

    /**
     * What the file specification says the attachment is. It is written because a reader
     * that lists the attachments of a PDF shows it to a person, and "the invoice" is a
     * better answer there than the file name repeated.
     */
    private static final String DESCRIPTION = "the electronic invoice of this document";

    /** What the result says where the caller turned the ESJ attachment off. */
    private static final String TURNED_OFF =
            "the ESJ document was not attached, because this run turned it off";

    /**
     * The number the draft identifier is derived from where the input carries none, as in
     * {@code PdfRenderer}: a clock would make every run a different file. The identifier
     * the result carries is a digest and not this number; see {@link #saved}.
     */
    private static final long FIXED_DOCUMENT_ID = 0L;

    /** How many bytes of the digest the file identifier is, which is what PDF writes. */
    private static final int IDENTIFIER_BYTES = 16;

    private FacturX() {
        throw new AssertionError("no instances");
    }

    /**
     * Embeds a document into a PDF as the profile EN 16931, under the name
     * {@code factur-x.xml}.
     *
     * @param pdf      the rendering to embed into, which has to be a PDF/A-3 file
     * @param document the invoice
     * @return the hybrid invoice
     * @throws EmbedRefusedException if the input or the document is not one this module
     *                               embeds; see {@link EmbedRefusedException}
     * @throws PdfAccessException    if the input is encrypted
     * @throws PdfFormatException    if the input is no PDF this module reads
     * @throws PdfLimitException     if a bound of the default limits was reached
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static byte[] embed(byte[] pdf, SemanticDocument document) {
        return embed(pdf, document, EmbedOptions.defaults());
    }

    /**
     * Embeds a document into a PDF.
     *
     * @param pdf      the rendering to embed into, which has to be a PDF/A-3 file
     * @param document the invoice
     * @param options  the profile, the attachment name, the bounds and an optional
     *                 PDF/A validator for the input
     * @return the hybrid invoice
     * @throws EmbedRefusedException if the input or the document is not one this module
     *                               embeds; see {@link EmbedRefusedException}
     * @throws PdfAccessException    if the input is encrypted
     * @throws PdfFormatException    if the input is no PDF this module reads
     * @throws PdfLimitException     if a bound of {@link EmbedOptions#limits()} was
     *                               reached, which is never a verdict on the invoice
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static byte[] embed(byte[] pdf, SemanticDocument document, EmbedOptions options) {
        return embedWithReport(pdf, document, options).pdf();
    }

    /**
     * Embeds a document and reports what the cross industry invoice had no place for.
     *
     * @param pdf      the rendering to embed into, which has to be a PDF/A-3 file
     * @param document the invoice
     * @param options  the profile, the flavour, the bounds and an optional PDF/A
     *                 validator for the input
     * @return the hybrid invoice and the report of the writer
     * @throws EmbedRefusedException if the input or the document is not one this module
     *                               embeds; see {@link EmbedRefusedException}
     * @throws PdfAccessException    if the input is encrypted
     * @throws PdfFormatException    if the input is no PDF this module reads
     * @throws PdfLimitException     if a bound of {@link EmbedOptions#limits()} was
     *                               reached, which is never a verdict on the invoice
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static EmbedResult embedWithReport(byte[] pdf,
                                              SemanticDocument document,
                                              EmbedOptions options) {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(options, "options");
        requireProfile(document, options);
        WriteResult invoice = CiiWriter.writeWithReport(document,
                WriterOptions.defaults());
        Optional<String> unproven = options.esj()
                ? disagreement(document, invoice.xml())
                : Optional.empty();
        EmbedResult.EsjOutcome outcome = outcome(options.esj(), unproven);
        Optional<String> omitted = options.esj() ? unproven : Optional.of(TURNED_OFF);
        try (PdfContainer container = PdfContainer.open(pdf, options.limits())) {
            requirePdfa3(container, pdf, options);
            requireNoInvoiceYet(container);
            byte[] packet = container.xmpPacket().orElseThrow(() -> new EmbedRefusedException(
                    "this file carries no XMP packet, and a hybrid invoice says in its"
                            + " metadata that it carries an invoice; a PDF/A file carries"
                            + " a packet, so this one does not declare what it is"));
            byte[] merged = FacturXXmp.merged(packet, options.flavour(), options.profile());
            PDDocument open = container.document();
            Optional<Calendar> modified = modificationDate(document);
            attach(open, invoice.xml(), options.flavour().attachmentName(), MEDIA_TYPE,
                    ALTERNATIVE, DESCRIPTION, modified);
            if (outcome == EmbedResult.EsjOutcome.ATTACHED) {
                attach(open, Canonicalizer.canonicalBytes(document), EsjAttachment.NAME,
                        EsjAttachment.MEDIA_TYPE, SUPPLEMENT, EsjAttachment.DESCRIPTION,
                        modified);
            }
            metadata(open, merged);
            return new EmbedResult(saved(open), invoice.report(), outcome, omitted);
        }
    }

    /**
     * Returns what became of the ESJ document, which is three answers and not two.
     *
     * <p>A caller who turned the attachment off got what was asked for; a file that comes
     * back without it although it was asked for is the answer somebody has to read. The
     * two travel apart so that the command line can say each of them in its own voice.
     */
    private static EmbedResult.EsjOutcome outcome(boolean wanted, Optional<String> unproven) {
        if (!wanted) {
            return EmbedResult.EsjOutcome.TURNED_OFF;
        }
        return unproven.isEmpty() ? EmbedResult.EsjOutcome.ATTACHED
                : EmbedResult.EsjOutcome.UNPROVEN;
    }

    /**
     * Returns why the document must not be attached beside this invoice XML, where it
     * must not.
     *
     * <p>The check is made on the XML that has just been written and not on the document
     * alone, because what is being asserted is that the two files in this container say
     * the same thing. Reading it back costs what reading an invoice costs and is the only
     * way to assert it: the writer's own report says what it left out, not what a reader
     * makes of what it wrote.
     */
    private static Optional<String> disagreement(SemanticDocument document, byte[] xml) {
        SemanticDocument written;
        try {
            written = new StreamingReader().read(xml).document();
        } catch (BindingException e) {
            // The invoice is written and goes in; what could not be done is the proof that
            // the two say the same thing, and an attachment nobody proved is one this
            // module does not write.
            return Optional.of("the cross industry invoice of this container could not be"
                    + " read back, so the document beside it was not checked against it: "
                    + e.getMessage());
        }
        // A document that does not hold together under its own model carries its own
        // sentence: what is wrong with it is not a statement about the pair of files.
        return EsjAgreement.disagreement(document, written, BindingSyntax.CII)
                .map(reason -> reason.ground() == EsjAgreement.Ground.UNSOUND
                        ? reason.message()
                        : "the cross industry invoice of this container and the document"
                                + " are not two accounts of one invoice: "
                                + reason.message());
    }

    /**
     * Returns the date the embedded file stream carries: BT-2, the issue date of the
     * invoice, at midnight UTC.
     *
     * <p>The container specification lists a modification date on the stream, and a clock
     * would be the end of "the same invoice gives the same bytes". The issue date is a
     * date of the document rather than of the run, so it is both. A document that states
     * none, or states something that is no date, gets no key: an absent optional entry is
     * a smaller untruth than an invented date.
     */
    private static Optional<Calendar> modificationDate(SemanticDocument document) {
        Optional<String> written = document.value(ISSUE_DATE).map(SemanticValue::content);
        if (written.isEmpty()) {
            return Optional.empty();
        }
        try {
            LocalDate date = LocalDate.parse(written.get());
            Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            calendar.clear();
            calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
            return Optional.of(calendar);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks that the invoice names the profile the caller asked to write.
     *
     * <p>The profile is written twice in a hybrid file — once in the packet for a consumer
     * that only reads the container, once as BT-24 for one that reads the invoice — and a
     * file whose two disagree sends the two consumers to two different answers.
     * {@code ContainerChecks} reports that as an error when it reads such a file; this is
     * the same fact, kept from being written in the first place.
     */
    private static void requireProfile(SemanticDocument document, EmbedOptions options) {
        Optional<String> identifier = document.value(SPECIFICATION_IDENTIFIER)
                .map(SemanticValue::content);
        if (identifier.isEmpty()) {
            throw new EmbedRefusedException("this document carries no specification"
                    + " identifier in BT-24, so nothing in it says that it is an invoice of"
                    + " the profile " + Messages.quoted(options.profile().conformanceLevel())
                    + " the container would declare");
        }
        Optional<FacturXProfile> written =
                FacturXProfile.ofSpecificationIdentifier(identifier.get());
        if (written.isEmpty()) {
            throw new EmbedRefusedException("the specification identifier "
                    + Messages.quoted(identifier.get()) + " of this document names no"
                    + " profile this module knows, and the container would declare "
                    + Messages.quoted(options.profile().conformanceLevel()));
        }
        if (written.get() != options.profile()) {
            throw new EmbedRefusedException("this document is written in the profile "
                    + Messages.quoted(written.get().conformanceLevel()) + " and the"
                    + " container would declare "
                    + Messages.quoted(options.profile().conformanceLevel())
                    + ", so a consumer that reads the container and one that reads the"
                    + " invoice would be told two different things");
        }
    }

    /**
     * Checks that the input is a PDF/A-3 file: by its own declaration, and by a validator
     * where the caller lent one.
     */
    private static void requirePdfa3(PdfContainer container, byte[] pdf, EmbedOptions options) {
        Optional<PdfaIdentification> declared = container.pdfaIdentification();
        if (declared.isEmpty()) {
            throw new EmbedRefusedException("this file declares no PDF/A conformance, and"
                    + " a hybrid invoice is a PDF/A-3 file; nothing here converts a"
                    + " document into one, because that would mean changing pages this"
                    + " module has not drawn");
        }
        if (declared.get().part() != PDFA_PART) {
            throw new EmbedRefusedException("this file declares " + declared.get().describe()
                    + " and an invoice is embedded into PDF/A-" + PDFA_PART + ", which is"
                    + " the part of ISO 19005 that allows a file of any type to be"
                    + " embedded; nothing here converts between the parts, because that"
                    + " would mean changing pages this module has not drawn");
        }
        options.check().flatMap(check -> check.notPdfa3(pdf)).ifPresent(reason -> {
            throw new EmbedRefusedException("this file declares "
                    + declared.get().describe() + " and the validator the caller passed"
                    + " does not agree: " + reason);
        });
    }

    /**
     * Checks that the input does not already carry an invoice.
     *
     * <p>A container declares one electronic invoice. A file that offered two would leave
     * the choice to whoever reads it, which is the choice this project refuses to make
     * silently anywhere else, and writing the second one is where that file would come
     * from.
     */
    private static void requireNoInvoiceYet(PdfContainer container) {
        List<LocatedAttachment> candidates = InvoiceAttachments.locate(container).candidates();
        if (!candidates.isEmpty()) {
            throw new EmbedRefusedException("this file already carries " + candidates.size()
                    + " attachment(s) that could be the electronic invoice, the first of"
                    + " them " + candidates.get(0) + "; a container declares one invoice,"
                    + " and embedding a second would leave the choice to whoever reads the"
                    + " file");
        }
    }

    /**
     * Writes the attachment, the name tree entry and the associated files entry.
     */
    private static void attach(PDDocument pdf,
                               byte[] content,
                               String name,
                               String mediaType,
                               COSName relationship,
                               String description,
                               Optional<Calendar> modified) {
        PDDocumentCatalog catalog = pdf.getDocumentCatalog();
        PDComplexFileSpecification specification = specification(pdf, content, name,
                mediaType, relationship, description, modified);
        names(catalog, name, specification);
        associate(catalog, specification);
    }

    private static PDComplexFileSpecification specification(PDDocument pdf,
                                                            byte[] content,
                                                            String name,
                                                            String mediaType,
                                                            COSName relationship,
                                                            String description,
                                                            Optional<Calendar> modified) {
        PDEmbeddedFile embedded;
        try {
            embedded = new PDEmbeddedFile(pdf, new ByteArrayInputStream(content),
                    COSName.FLATE_DECODE);
        } catch (IOException e) {
            throw new PdfFormatException("the attachment " + Messages.quoted(name)
                    + " could not be written into the file", e);
        }
        embedded.setSubtype(mediaType);
        embedded.setSize(content.length);
        modified.ifPresent(embedded::setModDate);
        PDComplexFileSpecification specification = new PDComplexFileSpecification();
        specification.setFile(name);
        specification.setFileUnicode(name);
        specification.setEmbeddedFile(embedded);
        specification.setEmbeddedFileUnicode(embedded);
        specification.setFileDescription(description);
        specification.getCOSObject().setItem(AF_RELATIONSHIP, relationship);
        return specification;
    }

    /**
     * Puts the specification into the embedded files name tree, beside whatever the file
     * already carries.
     *
     * <p>The entries are rewritten as one flat node in the order of their names, which is
     * what makes the result a function of its inputs. A file whose tree has children is
     * refused rather than flattened: the children may hold entries this module never saw,
     * and a rewrite that dropped one would lose an attachment silently.
     */
    private static void names(PDDocumentCatalog catalog,
                              String name,
                              PDComplexFileSpecification specification) {
        PDDocumentNameDictionary names = catalog.getNames();
        if (names == null) {
            names = new PDDocumentNameDictionary(catalog);
        }
        PDEmbeddedFilesNameTreeNode tree = names.getEmbeddedFiles();
        Map<String, PDComplexFileSpecification> entries = new TreeMap<>();
        if (tree != null) {
            if (tree.getKids() != null) {
                throw new EmbedRefusedException("the embedded files name tree of this file"
                        + " has children, and this module writes the tree as one node;"
                        + " rewriting a tree it has not walked whole could lose an"
                        + " attachment");
            }
            try {
                Map<String, PDComplexFileSpecification> existing = tree.getNames();
                if (existing != null) {
                    entries.putAll(existing);
                }
            } catch (IOException e) {
                throw new PdfFormatException("the embedded files name tree could not be"
                        + " read", e);
            }
        }
        if (entries.containsKey(name)) {
            throw new EmbedRefusedException("this file already carries an attachment under"
                    + " the name " + Messages.quoted(name) + ", and this module overwrites"
                    + " nothing");
        }
        entries.put(name, specification);
        PDEmbeddedFilesNameTreeNode written = new PDEmbeddedFilesNameTreeNode();
        written.setNames(entries);
        names.setEmbeddedFiles(written);
        catalog.setNames(names);
    }

    /** Adds the specification to the catalog's associated files array. */
    private static void associate(PDDocumentCatalog catalog,
                                  PDComplexFileSpecification specification) {
        COSArray associated = catalog.getCOSObject().getCOSArray(AF);
        if (associated == null) {
            associated = new COSArray();
            catalog.getCOSObject().setItem(AF, associated);
        }
        associated.add(specification.getCOSObject());
    }

    /**
     * Replaces the metadata stream with the merged packet. The stream carries no filter,
     * as PDF/A asks, so that a reader which knows nothing about PDF finds the metadata in
     * the file with nothing to undo first.
     */
    private static void metadata(PDDocument pdf, byte[] packet) {
        PDMetadata metadata = new PDMetadata(pdf);
        try (OutputStream out = metadata.createOutputStream()) {
            out.write(packet);
        } catch (IOException e) {
            throw new PdfFormatException("the metadata of the file could not be written", e);
        }
        pdf.getDocumentCatalog().setMetadata(metadata);
    }

    /**
     * Writes the file, and gives it a second identifier of its own.
     *
     * <p>ISO 32000-1, 14.4 gives the two halves of {@code /ID} two jobs: the first
     * identifies the content and stays with it, the second changes whenever the file
     * changes. This file is the input's pages with an invoice added, so the first half is
     * the input's own where it had one, and the second is a digest of these bytes — which
     * means writing them once to have something to hash and once more to carry the
     * answer. Both halves are a function of the inputs, so the result is still the same
     * bytes twice.
     */
    private static byte[] saved(PDDocument pdf) {
        if (pdf.getDocumentId() == null) {
            pdf.setDocumentId(FIXED_DOCUMENT_ID);
        }
        byte[] draft = write(pdf);
        COSArray identifier = new COSArray();
        identifier.add(first(pdf).orElseGet(() -> new COSString(digest(draft))));
        identifier.add(new COSString(digest(draft)));
        pdf.getDocument().getTrailer().setItem(COSName.ID, identifier);
        return write(pdf);
    }

    /** Returns the first half of the identifier the input carried, where it carried one. */
    private static Optional<COSString> first(PDDocument pdf) {
        COSArray identifier = pdf.getDocument().getTrailer().getCOSArray(COSName.ID);
        if (identifier == null || identifier.size() != 2
                || !(identifier.get(0) instanceof COSString written)) {
            return Optional.empty();
        }
        return Optional.of(written);
    }

    /** Returns as many bytes of the SHA-256 of these bytes as a file identifier holds. */
    private static byte[] digest(byte[] bytes) {
        try {
            return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(bytes),
                    IDENTIFIER_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
        }
    }

    private static byte[] write(PDDocument pdf) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            pdf.save(bytes);
        } catch (IOException e) {
            throw new PdfFormatException("the hybrid invoice could not be written", e);
        }
        return bytes.toByteArray();
    }
}
