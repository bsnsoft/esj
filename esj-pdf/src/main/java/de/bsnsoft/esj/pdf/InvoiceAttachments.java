package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.xr.XrSyntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The attachments of one container, each with what its bytes turned out to be, and the
 * one of them that is the invoice.
 *
 * <p>Every attachment is classified by its first bytes: the root element of the XML it
 * begins, or the fact that it begins no XML at all. Neither the name nor the declared
 * media type takes part in the decision. An attachment called {@code factur-x.xml} that
 * holds a UBL invoice is a UBL invoice, an attachment called {@code logo.png} that holds
 * a CII invoice is a CII invoice, and where the name and the content disagree that is a
 * finding of {@link ContainerChecks} rather than a reason to believe the name.
 *
 * <p>The one thing this class refuses to do is choose. A container with two invoice
 * attachments is a container whose invoice nobody has named, and
 * {@link #single()} says so rather than taking the first: one of the two may be valid and
 * the other not, and a tool that prints one verdict has to know which document the
 * verdict is about.
 */
public final class InvoiceAttachments {

    /**
     * How many bytes of an attachment are read to classify it. A root element with the
     * namespace declarations of UBL or CII on it is a few hundred bytes; this is an order
     * of magnitude above that, and it is all that an attachment that is not an invoice
     * ever costs.
     */
    static final int WINDOW = 8192;

    /** The root element of a ZUGFeRD 1.0 invoice, which this project does not read. */
    private static final String ZUGFERD_1_ROOT = "CrossIndustryDocument";

    private final List<LocatedAttachment> all;
    private final List<LocatedAttachment> invoices;
    private final List<LocatedAttachment> candidates;

    private InvoiceAttachments(List<LocatedAttachment> all,
                               List<LocatedAttachment> invoices,
                               List<LocatedAttachment> candidates) {
        this.all = List.copyOf(all);
        this.invoices = List.copyOf(invoices);
        this.candidates = List.copyOf(candidates);
    }

    /**
     * Classifies every attachment of a container.
     *
     * @param container the container
     * @return the attachments and their classification
     * @throws PdfLimitException    if classifying an attachment meets a bound of the
     *                              container that leaves this reader without an answer
     * @throws NullPointerException if {@code container} is {@code null}
     */
    public static InvoiceAttachments locate(PdfContainer container) {
        Objects.requireNonNull(container, "container");
        List<LocatedAttachment> all = new ArrayList<>();
        List<LocatedAttachment> invoices = new ArrayList<>();
        for (EmbeddedFile file : container.embeddedFiles()) {
            LocatedAttachment located = classify(file);
            all.add(located);
            if (located.kind().isInvoice()) {
                invoices.add(located);
            }
        }
        List<LocatedAttachment> candidates = new ArrayList<>();
        boolean oneInvoice = invoices.size() == 1;
        for (LocatedAttachment located : all) {
            if (candidate(located, oneInvoice)) {
                candidates.add(located);
            }
        }
        return new InvoiceAttachments(all, invoices, candidates);
    }

    /**
     * Tells whether an attachment is one this class declines to choose between; see
     * {@link #candidates()} for why each of the three answers is what it is.
     *
     * @param oneInvoice whether exactly one attachment of the container was read as an
     *                   invoice, which is the case a supplement may be set aside in
     */
    private static boolean candidate(LocatedAttachment attachment, boolean oneInvoice) {
        return switch (attachment.kind()) {
            case CII_INVOICE, UBL_INVOICE, UBL_CREDIT_NOTE, ZUGFERD_1 -> true;
            case UNDETERMINED -> !(oneInvoice && isSupplementary(attachment));
            case UNREADABLE -> couldBeTheDocument(attachment);
            case NOT_XML, OTHER_XML, ESJ_DOCUMENT -> false;
        };
    }

    /**
     * Tells whether the container leaves it open that this attachment is the document:
     * it says so with {@code /AFRelationship /Alternative}, {@code /Data} or
     * {@code /Source}, and it leaves the question open by saying nothing at all.
     */
    private static boolean couldBeTheDocument(LocatedAttachment attachment) {
        return attachment.file().associatedRelationship()
                .map(ContainerChecks.DOCUMENT_RELATIONSHIPS::contains)
                .orElse(Boolean.TRUE);
    }

    /** Tells whether the container says this attachment merely travels with the document. */
    private static boolean isSupplementary(LocatedAttachment attachment) {
        return attachment.file().associatedRelationship()
                .map(ContainerChecks.SUPPLEMENTARY_RELATIONSHIPS::contains)
                .orElse(Boolean.FALSE);
    }

    /**
     * Returns every attachment of the container, in the order it was enumerated.
     *
     * @return the attachments, possibly none
     */
    public List<LocatedAttachment> all() {
        return all;
    }

    /**
     * Returns the attachments whose bytes spell an electronic invoice, including one this
     * project does not read; see {@link AttachmentKind#isInvoice()}.
     *
     * @return the invoice attachments, possibly none
     */
    public List<LocatedAttachment> invoices() {
        return invoices;
    }

    /**
     * Returns the attachments this class declines to choose between: the ones whose bytes
     * spell an invoice, and the ones whose bytes were never established.
     *
     * <p>The second group belongs here and not in {@link #invoices()}. An attachment that
     * was not classified may be an invoice, and a container that carries one invoice and
     * one unread attachment is a container whose invoice nobody has named — which is the
     * question {@link #single()} refuses to answer on its own. An attachment is
     * unestablished for two different reasons, and the reasons are weighed differently.
     *
     * <p>{@link AttachmentKind#UNDETERMINED} — it begins an XML document whose root
     * element lies beyond the window this reader classifies by. It is a candidate unless
     * the container calls it a supplement and exactly one other attachment was read as an
     * invoice. The window is a bound of the reading party and the prolog of an XML
     * document has no length limit, so an ordinary enclosure with a long comment in front
     * of its root element would otherwise make a perfectly clear container unreadable —
     * and anyone who can add a file to a hybrid invoice could deny its recipient a
     * verdict. What the invoice beside it declares about itself does not enter into it: a
     * hybrid invoice whose XML carries no relationship at all is a file this tool reports
     * on and still reads. A second invoice hidden behind a long prolog is still caught,
     * because an attachment meant to be read as the document says so, and one that says
     * nothing is a candidate too.
     *
     * <p>{@link AttachmentKind#UNREADABLE} — its stream is filtered with something this
     * reader does not decode, so not one byte of it was looked at. It is a candidate where
     * the container declares it the document or declares nothing, and not where the
     * container calls it a supplement or leaves it unspecified. The asymmetry is
     * deliberate: a picture beside an invoice is what the second case is, while an
     * attachment the container itself points at as the document is one whose substitution
     * for the invoice must not pass as a verdict about the invoice.
     *
     * @return the candidates, possibly none
     */
    public List<LocatedAttachment> candidates() {
        return candidates;
    }

    /**
     * Returns the one invoice attachment of the container.
     *
     * @return the invoice attachment
     * @throws NoInvoiceAttachmentException        if no attachment spells an invoice
     * @throws AmbiguousInvoiceAttachmentException if more than one attachment could be it
     */
    public LocatedAttachment single() {
        if (candidates.size() > 1) {
            throw new AmbiguousInvoiceAttachmentException("this PDF carries "
                    + candidates.size() + " attachments that could be the electronic"
                    + " invoice, and nothing says which of them the document is: "
                    + candidates.stream().map(LocatedAttachment::toString)
                            .collect(Collectors.joining(", ")), candidates);
        }
        if (invoices.isEmpty()) {
            List<LocatedAttachment> unestablished = all.stream()
                    .filter(located -> located.kind() == AttachmentKind.UNDETERMINED
                            || located.kind() == AttachmentKind.UNREADABLE)
                    .toList();
            throw new NoInvoiceAttachmentException(unestablished.isEmpty()
                    ? "this PDF carries no attachment whose bytes spell an electronic"
                            + " invoice, so there is no structured invoice to read; nothing"
                            + " is extracted from the page"
                    : "no attachment of this PDF was established to be an electronic"
                            + " invoice: " + unestablished.size()
                            + (unestablished.size() == 1 ? " of them was" : " of them were")
                            + " never classified, so what "
                            + (unestablished.size() == 1 ? "it holds" : "they hold")
                            + " was not established",
                    all, unestablished);
        }
        return invoices.get(0);
    }

    /**
     * Returns the attachment of a given name.
     *
     * <p>A name is a string in a file specification and nothing in a PDF makes it unique,
     * so a container written to confuse a reader carries two attachments of one name and
     * lets the reader pick. This method does not pick: where the name matches more than
     * one attachment it refuses, exactly as {@link #single()} refuses, and a caller that
     * has to tell them apart asks {@link #at(int)} with the position it printed.
     *
     * @param name the name as the container spells it
     * @return the attachment, or an empty optional where no attachment has that name
     * @throws AmbiguousInvoiceAttachmentException if more than one attachment has it
     * @throws NullPointerException                if {@code name} is {@code null}
     */
    public Optional<LocatedAttachment> named(String name) {
        Objects.requireNonNull(name, "name");
        List<LocatedAttachment> matching = all.stream()
                .filter(located -> located.name().equals(name))
                .toList();
        if (matching.size() > 1) {
            throw new AmbiguousInvoiceAttachmentException("this PDF carries "
                    + matching.size() + " attachments named " + Messages.quoted(name)
                    + ", and the name does not say which of them is meant: "
                    + matching.stream().map(LocatedAttachment::toString)
                            .collect(Collectors.joining(", ")), matching);
        }
        return matching.isEmpty() ? Optional.empty() : Optional.of(matching.get(0));
    }

    /**
     * Returns the attachment at a position of the enumeration.
     *
     * <p>The position is this module's own numbering, counted from one in the order
     * {@link #all()} returns, and it is the one selector a container cannot influence:
     * a name can be repeated, a media type can be wrong, and neither can be used to tell
     * two attachments apart.
     *
     * @param ordinal the position, counted from one
     * @return the attachment, or an empty optional where the container has no such
     *         position
     */
    public Optional<LocatedAttachment> at(int ordinal) {
        return ordinal < 1 || ordinal > all.size()
                ? Optional.empty()
                : Optional.of(all.get(ordinal - 1));
    }

    private static LocatedAttachment classify(EmbeddedFile file) {
        if (!file.hasContent()) {
            return new LocatedAttachment(file, AttachmentKind.NOT_XML, Optional.empty());
        }
        byte[] head;
        try {
            head = file.head(WINDOW);
        } catch (PdfFormatException e) {
            // A stream this reader does not decode is not a reason to refuse the file:
            // the invoice beside it is still the document, and an attachment nobody
            // decoded is an attachment nobody read an invoice out of either.
            return new LocatedAttachment(file, AttachmentKind.UNREADABLE, Optional.empty());
        }
        if (!XmlRoot.looksLikeXml(head)) {
            // The one classification that reads the name and the declared media type. It
            // can only answer "this is not the invoice, and it carries the label of this
            // project's own document"; see EsjAttachment.
            return new LocatedAttachment(file, EsjAttachment.labelled(file, head)
                    ? AttachmentKind.ESJ_DOCUMENT : AttachmentKind.NOT_XML,
                    Optional.empty());
        }
        Optional<XmlRoot.Name> root = XmlRoot.of(head);
        if (root.isEmpty()) {
            // It begins a tag and this parser could not reach an element in the window.
            // What it is was not established, and saying so is not the same as saying
            // that it is not an invoice; see AttachmentKind.UNDETERMINED.
            return new LocatedAttachment(file, AttachmentKind.UNDETERMINED, Optional.empty());
        }
        XmlRoot.Name name = root.get();
        return new LocatedAttachment(file, kindOf(name), Optional.of(qualified(name)));
    }

    private static AttachmentKind kindOf(XmlRoot.Name name) {
        if (XrSyntax.CII.matches(name.namespace(), name.localName())) {
            return AttachmentKind.CII_INVOICE;
        }
        if (XrSyntax.UBL_INVOICE.matches(name.namespace(), name.localName())) {
            return AttachmentKind.UBL_INVOICE;
        }
        if (XrSyntax.UBL_CREDIT_NOTE.matches(name.namespace(), name.localName())) {
            return AttachmentKind.UBL_CREDIT_NOTE;
        }
        // The namespace of ZUGFeRD 1.0 is not checked: the local name belongs to the
        // CII D14B document type and to nothing this project reads, and a file that
        // carries it under another namespace is still not something to import.
        return ZUGFERD_1_ROOT.equals(name.localName())
                ? AttachmentKind.ZUGFERD_1
                : AttachmentKind.OTHER_XML;
    }

    private static String qualified(XmlRoot.Name name) {
        return name.namespace().isEmpty()
                ? name.localName()
                : "{" + name.namespace() + "}" + name.localName();
    }
}
