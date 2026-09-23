package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the container says about the invoice it carries, checked against the invoice.
 *
 * <p>These are structural checks and their scope is stated rather than implied. What is
 * checked is the object structure of the file, the catalog's associated files array, the
 * embedded file dictionary, the names and places the file refers to its embedded files
 * from, the XMP packet and its Factur-X extension schema, and whether what those say about
 * the attachment agrees with the attachment. What is <em>not</em>
 * checked is PDF/A conformance: a file that declares PDF/A-3B is reported as declaring it,
 * and validating that declaration means checking fonts, colour spaces and transparency,
 * which is a different tool's work and is not done here.
 *
 * <p>Nothing here is a finding of the specification and nothing here decides whether the
 * invoice is valid. The container verdict and the invoice verdict stay two answers, and a
 * caller shows both.
 *
 * <p>The facts these checks are written against — the Factur-X XMP extension schema, the
 * properties it defines, the relationship an associated file is declared with and the
 * conventional attachment names — come from the public Factur-X and ZUGFeRD
 * specifications; {@code docs/pdf-input.md} records where each of them was taken from.
 */
public final class ContainerChecks {

    /** The semantic path of BT-24, the specification identifier of the invoice. */
    private static final SemanticPath SPECIFICATION_IDENTIFIER = SemanticPath.of("/BG-2/BT-24");

    /** The relationship a hybrid invoice declares its XML with. */
    private static final String ALTERNATIVE = "Alternative";

    /**
     * The relationships that say the attached file is the document rather than something
     * that travels with it. Factur-X asks for {@code Alternative}; ZUGFeRD used
     * {@code Data} for the profiles that are not invoices in the sense of EN 16931, and
     * files in the field carry {@code Source} as well.
     */
    static final Set<String> DOCUMENT_RELATIONSHIPS =
            Set.of(ALTERNATIVE, "Data", "Source");

    /**
     * The relationships that say an attached file travels with the document rather than
     * being it: the two PDF 32000-2 defines for an ordinary enclosure — terms of delivery,
     * a timesheet, a picture. They are the only two that let an attachment this reader
     * could not classify off the list of the ones that could be the invoice; see
     * {@link InvoiceAttachments#candidates()}.
     */
    static final Set<String> SUPPLEMENTARY_RELATIONSHIPS = Set.of("Supplement", "Unspecified");

    /** The relationships PDF 32000-2 defines, beyond the three above. */
    private static final Set<String> OTHER_RELATIONSHIPS =
            Set.of("Supplement", "EncryptedPayload", "FormData", "Schema", "Unspecified");

    /**
     * The attachment names the Factur-X and ZUGFeRD specifications give the cross industry
     * invoice of a hybrid document.
     *
     * <p>Only names those two documents define belong here, because the check this set
     * drives says that a file carries the name a cross industry invoice has and holds
     * something else. {@code xrechnung.xml} is a conventional name too and is deliberately
     * absent: an XRechnung attachment is legitimately UBL, so the same check would report
     * a correct file. The name of the Spanish Facturae hybrid PDF is absent for the same
     * reason and because neither of the two documents defines it.
     */
    private static final Set<String> CROSS_INDUSTRY_NAMES =
            Set.of("factur-x.xml", "zugferd-invoice.xml");

    /** What the XMP property {@code DocumentType} carries in a hybrid invoice. */
    private static final String INVOICE = "INVOICE";

    private ContainerChecks() {
        throw new AssertionError("no instances");
    }

    /**
     * Runs every check that needs the container alone.
     *
     * <p>The one check that is not here is the one against the invoice: whether the
     * profile the XMP packet declares is the profile the invoice writes in BT-24 can only
     * be asked once the invoice has been read, and
     * {@link #profileAgainstInvoice(Optional, SemanticDocument)} asks it.
     *
     * @param container the container
     * @param located   its attachments, classified
     * @return the findings, in the order the checks were made, possibly none
     * @throws PdfLimitException    if reading the XMP packet meets a bound
     * @throws NullPointerException if an argument is {@code null}
     */
    public static List<ContainerFinding> run(PdfContainer container,
                                             InvoiceAttachments located) {
        return run(container, located, null);
    }

    /**
     * Runs every check that needs the container alone, with the truncation of one
     * attachment left to the caller to report.
     *
     * <p>A bound of the run is not a defect of the file. Where the caller has decided that
     * a bound met inside one attachment must not decide what it says about the container —
     * {@code esj inspect} over an attachment somebody appended to somebody else's hybrid
     * invoice — the attachment is still decoded, so that every check that is about decoded
     * content is made over it, and the one finding that only says this reader stopped is
     * left out here and reported by the caller on the row that attachment has of its own.
     *
     * @param container         the container
     * @param located           its attachments, classified
     * @param boundNotReported  the attachment whose truncation the caller reports itself,
     *                          or {@code null} where every attachment is reported here
     * @return the findings, in the order the checks were made, possibly none
     * @throws PdfLimitException    if reading the XMP packet meets a bound
     * @throws NullPointerException if {@code container} or {@code located} is {@code null}
     */
    public static List<ContainerFinding> run(PdfContainer container,
                                             InvoiceAttachments located,
                                             LocatedAttachment boundNotReported) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(located, "located");
        List<ContainerFinding> findings = new ArrayList<>(container.structureFindings());
        container.pdfaIdentification().ifPresent(pdfa -> findings.add(
                finding(ContainerFinding.Category.PDF_STRUCTURE, "PDF-STRUCTURE-PDFA",
                        ContainerFinding.Severity.INFO,
                        "the file declares " + pdfa.describe()
                                + "; this is the declaration as the file writes it, and"
                                + " conformance to it was not validated")));
        Optional<LocatedAttachment> invoice = located.invoices().size() == 1
                ? Optional.of(located.invoices().get(0))
                : Optional.empty();
        invoice.ifPresent(attachment -> associatedFile(attachment, findings));
        xmp(container, invoice, findings);
        duplicateNames(located, findings);
        outsideTheTree(located, findings);
        several(located, findings);
        severalEsjDocuments(located, findings);
        labelledButNotAnEsjDocument(located, findings);
        for (LocatedAttachment attachment : located.all()) {
            embedded(attachment, findings, attachment == boundNotReported);
        }
        return List.copyOf(findings);
    }

    /**
     * Says that the embedded files name tree lists two different files under one name.
     *
     * <p>A name tree maps a key to one file, and a reader looks a file up by its key. A
     * tree that lists two files under one key is read differently by different readers: a
     * reader that loads it into a map keeps one entry and loses the other without a
     * trace, and a reader that walks it takes the first. A consumer that validates with
     * one reader and books with another may therefore book a file nobody validated. That
     * is a defect of the container whichever of the files is the invoice and whether or
     * not a caller named one, so it is an error: {@link InvoiceAttachments#single()} has
     * already refused to choose where one of the files could be the invoice, and where a
     * caller chose by position this finding keeps the container from passing.
     *
     * <p>Every entry is named with its position, which is what {@code --attachment-index}
     * takes, the object number of its stream and the size it declares, because the name
     * is the one thing that does not tell them apart.
     */
    private static void duplicateNames(InvoiceAttachments located,
                                       List<ContainerFinding> findings) {
        for (InvoiceAttachments.DuplicateName duplicate : located.duplicateNames()) {
            List<LocatedAttachment> sharing = duplicate.attachments();
            StringBuilder text = new StringBuilder();
            if (duplicate.source() == InvoiceAttachments.DuplicateName.Source.NAME_TREE_KEY) {
                text.append("the embedded files name tree lists ").append(sharing.size())
                        .append(" different files under the name ")
                        .append(Messages.quoted(duplicate.name()))
                        .append(", so a reader that looks the name up is handed one of them"
                                + " and which one depends on the reader:");
            } else {
                text.append("the file specification ")
                        .append(Messages.quoted(duplicate.name()))
                        .append(duplicate.objectNumber().isPresent()
                                ? " (object " + duplicate.objectNumber().getAsLong() + ")"
                                : "")
                        .append(" holds ").append(sharing.size())
                        .append(" different files under the entries ")
                        .append(String.join(", ", duplicate.entries().stream()
                                .map(entry -> "/" + entry).toList()))
                        .append(" of its embedded file dictionary, so a reader that follows"
                                + " it is handed one of them and which one depends on the"
                                + " reader:");
            }
            for (LocatedAttachment attachment : sharing) {
                text.append(' ').append(located.all().indexOf(attachment) + 1).append(' ')
                        .append(identified(attachment));
            }
            findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED,
                    "PDF-EMBEDDED-DUPLICATE-NAME", ContainerFinding.Severity.ERROR,
                    text.toString()));
        }
    }

    /**
     * Says that an attachment that could be the invoice is not listed in the embedded
     * files name tree.
     *
     * <p>The tree is where a reader of a hybrid invoice looks the invoice up, and a hybrid
     * invoice lists its invoice there. An attachment a page refers to, or only the
     * associated files array, is still an attachment of the file — a viewer shows it and a
     * reader that collects attachments collects it — and it is counted and read like any
     * other; this finding is what keeps its place from going unsaid. It is a warning: a
     * reader that looks in the tree and finds nothing has not been told something false,
     * and a second invoice outside the tree is already a second candidate that
     * {@link InvoiceAttachments#single()} refuses to choose from.
     */
    private static void outsideTheTree(InvoiceAttachments located,
                                       List<ContainerFinding> findings) {
        for (LocatedAttachment attachment : located.candidates()) {
            EmbeddedFile file = attachment.file();
            if (file.inNameTree()) {
                continue;
            }
            findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED,
                    "PDF-EMBEDDED-NOT-IN-TREE", ContainerFinding.Severity.WARNING,
                    "the attachment " + (located.all().indexOf(attachment) + 1) + " "
                            + identified(attachment) + " could be the invoice and the"
                            + " embedded files name tree does not list it; it is referred to"
                            + " from " + places(file) + " only, so a reader that looks for"
                            + " the invoice in the tree does not see it"));
        }
    }

    /**
     * Returns the places that refer to an attachment in words, at most three of them and
     * the count of the rest.
     *
     * @param file the attachment
     * @return the places, such as {@code a file attachment annotation on page 1}
     */
    static String places(EmbeddedFile file) {
        List<String> described = file.references().stream()
                .map(EmbeddedFile.Reference::describe)
                .distinct()
                .toList();
        int shown = Math.min(3, described.size());
        String text = String.join(", ", described.subList(0, shown));
        return described.size() > shown
                ? text + " and " + (described.size() - shown) + " more"
                : text;
    }

    /**
     * Returns an attachment as a finding names it where its name does not tell it apart:
     * its name, what its bytes are, the object number of its stream and the size it
     * declares.
     */
    private static String identified(LocatedAttachment attachment) {
        EmbeddedFile file = attachment.file();
        return Messages.quoted(file.name()) + " (" + attachment.kind().describe()
                + (file.objectNumber().isPresent()
                        ? ", object " + file.objectNumber().getAsLong()
                        : ", no embedded file stream")
                + (file.declaredSize().isPresent()
                        ? ", " + file.declaredSize().getAsLong() + " bytes declared"
                        : ", no size declared")
                + ")";
    }

    /**
     * Says that the container carries more than one ESJ document.
     *
     * <p>None of them is the invoice, so this decides nothing about the invoice — but a
     * file that offers two accounts of itself and says nothing about which one it means is
     * a file whose producer left the choice to the reader, which is the choice this project
     * refuses to make silently. Nothing is checked against the invoice while it holds; the
     * finding is the answer.
     */
    private static void severalEsjDocuments(InvoiceAttachments located,
                                            List<ContainerFinding> findings) {
        List<LocatedAttachment> documents = located.all().stream()
                .filter(attachment -> attachment.kind() == AttachmentKind.ESJ_DOCUMENT)
                .toList();
        if (documents.size() < 2) {
            return;
        }
        findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED,
                "PDF-EMBEDDED-SEVERAL-ESJ", ContainerFinding.Severity.ERROR,
                "this file carries " + documents.size() + " ESJ documents beside its"
                        + " invoice, and nothing says which of them is this invoice, so"
                        + " none of them was checked against it"));
    }

    /**
     * Says that an attachment wears the label of this project's ESJ document and is not
     * one.
     *
     * <p>Classification is by content, so such an attachment is simply not an ESJ document
     * and every row about one is silent about it. That silence is the wrong answer to give
     * about a file that calls itself {@value EsjAttachment#NAME} and declares the
     * relationship of an enclosure: a consumer that picks the attachment by its name or by
     * its relationship, as another vendor's reader may, gets those bytes and makes of them
     * what it can. So the label is reported, with what the content turned out to be.
     *
     * <p>It is a warning and not an error. The invoice of the container is its XML and is
     * untouched by anything an enclosure says, and a file that carries a wrongly labelled
     * enclosure beside a sound invoice is not a file whose invoice is in doubt. It stands
     * on the row of the embedded files rather than on the row of the ESJ document,
     * because there is no ESJ document: that row is printed for a container that carries
     * one, and this is the answer for a container that does not.
     */
    private static void labelledButNotAnEsjDocument(InvoiceAttachments located,
                                                    List<ContainerFinding> findings) {
        for (LocatedAttachment attachment : located.all()) {
            if (attachment.kind() == AttachmentKind.ESJ_DOCUMENT) {
                continue;
            }
            EmbeddedFile file = attachment.file();
            boolean named = EsjAttachment.NAME.equals(file.name());
            boolean enclosure = file.associatedRelationship()
                    .map(EsjAttachment.RELATIONSHIP::equals).orElse(Boolean.FALSE);
            if (!named) {
                continue;
            }
            findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED,
                    "PDF-EMBEDDED-ESJ-LABEL", ContainerFinding.Severity.WARNING,
                    "the attachment " + Messages.quoted(file.name()) + " carries the name"
                            + " this project gives the ESJ document beside an invoice"
                            + (enclosure ? " and the relationship of an enclosure" : "")
                            + ", and its content is " + content(attachment.kind())
                            + "; it was not checked against the invoice"));
        }
    }

    /**
     * Returns what the content of such an attachment turned out to be, in words that fit
     * an attachment the name promises is JSON.
     *
     * <p>{@link AttachmentKind#describe()} answers the question the invoice attachment
     * asks — which syntax is this? — and one of its answers, <i>not XML</i>, is no ground
     * at all for the conclusion drawn here: an ESJ document is JSON, and a file that is
     * not XML has said nothing about whether it is one. What rules it out is the
     * classification, so that kind says so in its own words and the others, which do rule
     * it out by being something else, keep theirs.
     *
     * @param kind what the content was classified as, which is never the ESJ document
     * @return the phrase the finding states after "its content is"
     */
    private static String content(AttachmentKind kind) {
        return kind == AttachmentKind.NOT_XML
                ? "neither an ESJ document this reader reads nor XML"
                : kind.describe() + ", so it is no ESJ document";
    }

    /**
     * Says that the container carries more than one attachment that could be the invoice.
     *
     * <p>It is a defect of the container whether or not the caller resolved it. A
     * container specification expects one attachment to be the electronic invoice, and a
     * file that offers two or three of them is a file whose producer left the choice to
     * the reader — which is precisely the choice a tool that prints one verdict must not
     * make silently. Where nobody named one the run is refused before this finding is
     * ever built; where somebody did, the finding is what keeps the others from
     * disappearing out of the report.
     */
    private static void several(InvoiceAttachments located, List<ContainerFinding> findings) {
        List<LocatedAttachment> candidates = located.candidates();
        if (candidates.size() < 2) {
            return;
        }
        StringBuilder text = new StringBuilder("this PDF carries ")
                .append(candidates.size())
                .append(" attachments that could be the electronic invoice, and a"
                        + " container declares one:");
        for (LocatedAttachment candidate : candidates) {
            text.append(' ').append(located.all().indexOf(candidate) + 1).append(' ')
                    .append(candidate);
        }
        findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED, "PDF-EMBEDDED-SEVERAL",
                ContainerFinding.Severity.WARNING, text.toString()));
    }

    /**
     * Checks the profile the XMP packet declares against the specification identifier the
     * invoice carries.
     *
     * <p>The two are the same fact written twice, once for a consumer that only reads the
     * container and once for one that reads the invoice, and a file whose two disagree
     * sends the two consumers to two different answers. Where either is missing or names
     * a profile this module does not know, nothing is decided and nothing is reported:
     * that the packet says nothing is already a finding of {@link #run}.
     *
     * @param metadata the Factur-X properties of the XMP packet, possibly absent
     * @param document the invoice that was read out of the container
     * @return the finding, or an empty optional where the two agree or one of them is
     *         missing
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Optional<ContainerFinding> profileAgainstInvoice(
            Optional<FacturXMetadata> metadata, SemanticDocument document) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(document, "document");
        Optional<FacturXProfile> declared = metadata.flatMap(FacturXMetadata::profile);
        Optional<FacturXProfile> written = document.value(SPECIFICATION_IDENTIFIER)
                .map(SemanticValue::content)
                .flatMap(FacturXProfile::ofSpecificationIdentifier);
        if (declared.isEmpty() || written.isEmpty() || declared.get() == written.get()) {
            return Optional.empty();
        }
        return Optional.of(finding(ContainerFinding.Category.PDF_XMP,
                "PDF-XMP-CONFORMANCE-MISMATCH", ContainerFinding.Severity.ERROR,
                "the XMP packet declares the profile "
                        + Messages.quoted(declared.get().conformanceLevel())
                        + " and the invoice writes a specification identifier of the"
                        + " profile " + Messages.quoted(written.get().conformanceLevel())
                        + ", so a consumer that reads the container and one that reads"
                        + " the invoice are told two different things"));
    }

    private static void associatedFile(LocatedAttachment attachment,
                                       List<ContainerFinding> findings) {
        EmbeddedFile file = attachment.file();
        if (!file.associated()) {
            findings.add(finding(ContainerFinding.Category.PDF_AF, "PDF-AF-ABSENT",
                    ContainerFinding.Severity.ERROR,
                    "the invoice attachment " + Messages.quoted(file.name()) + " is not"
                            + " referred to by the catalog's associated files array, so"
                            + " nothing in the file says that this attachment is what the"
                            + " document is about"));
            return;
        }
        Optional<String> relationship = file.associatedRelationship();
        if (relationship.isEmpty()) {
            findings.add(finding(ContainerFinding.Category.PDF_AF, "PDF-AF-RELATIONSHIP",
                    ContainerFinding.Severity.WARNING,
                    "the invoice attachment " + Messages.quoted(file.name()) + " is an"
                            + " associated file with no relationship, and a hybrid invoice"
                            + " declares " + ALTERNATIVE));
            return;
        }
        String value = relationship.get();
        if (ALTERNATIVE.equals(value)) {
            return;
        }
        boolean known = DOCUMENT_RELATIONSHIPS.contains(value)
                || OTHER_RELATIONSHIPS.contains(value);
        findings.add(finding(ContainerFinding.Category.PDF_AF, "PDF-AF-RELATIONSHIP",
                ContainerFinding.Severity.WARNING,
                "the invoice attachment " + Messages.quoted(file.name()) + " is an"
                        + " associated file with the relationship "
                        + Messages.quoted(value)
                        + (known ? ", and a hybrid invoice declares " + ALTERNATIVE
                                 : ", which PDF 32000-2 does not define")));
    }

    private static void xmp(PdfContainer container,
                            Optional<LocatedAttachment> invoice,
                            List<ContainerFinding> findings) {
        Optional<FacturXMetadata> metadata = container.facturX();
        if (metadata.isEmpty()) {
            boolean packet = container.xmpPacket().isPresent();
            findings.add(finding(ContainerFinding.Category.PDF_XMP,
                    packet ? "PDF-XMP-SCHEMA" : "PDF-XMP-ABSENT",
                    ContainerFinding.Severity.WARNING,
                    packet
                            ? "the XMP packet of this file carries none of the properties"
                                    + " of the Factur-X extension schema, so the container"
                                    + " says nothing about the invoice attached to it"
                            : "this file carries no XMP packet, so the container says"
                                    + " nothing about the invoice attached to it"));
            return;
        }
        FacturXMetadata properties = metadata.get();
        properties.documentType()
                .filter(type -> !INVOICE.equals(type.toUpperCase(Locale.ROOT)))
                .ifPresent(type -> findings.add(finding(ContainerFinding.Category.PDF_XMP,
                        "PDF-XMP-DOCUMENT-TYPE", ContainerFinding.Severity.WARNING,
                        "the XMP packet declares the document type "
                                + Messages.quoted(type) + " and a hybrid invoice declares "
                                + INVOICE)));
        if (properties.version().isEmpty()) {
            findings.add(finding(ContainerFinding.Category.PDF_XMP, "PDF-XMP-VERSION",
                    ContainerFinding.Severity.WARNING,
                    "the XMP packet names no version of the container specification"));
        }
        fileName(properties, invoice, findings);
        conformance(properties, findings);
    }

    private static void fileName(FacturXMetadata properties,
                                 Optional<LocatedAttachment> invoice,
                                 List<ContainerFinding> findings) {
        Optional<String> declared = properties.documentFileName();
        if (declared.isEmpty()) {
            findings.add(finding(ContainerFinding.Category.PDF_XMP, "PDF-XMP-FILENAME",
                    ContainerFinding.Severity.WARNING,
                    "the XMP packet names no attachment, so a consumer that reads the"
                            + " container has to guess which attachment is the invoice"));
            return;
        }
        if (invoice.isEmpty() || declared.get().equals(invoice.get().name())) {
            return;
        }
        findings.add(finding(ContainerFinding.Category.PDF_XMP, "PDF-XMP-FILENAME",
                ContainerFinding.Severity.ERROR,
                "the XMP packet names the attachment " + Messages.quoted(declared.get())
                        + " and the invoice is the attachment "
                        + Messages.quoted(invoice.get().name()) + ", so a consumer that"
                        + " believes the packet reads the wrong file or none"));
    }

    private static void conformance(FacturXMetadata properties,
                                    List<ContainerFinding> findings) {
        Optional<String> level = properties.conformanceLevel();
        if (level.isEmpty()) {
            findings.add(finding(ContainerFinding.Category.PDF_XMP, "PDF-XMP-CONFORMANCE",
                    ContainerFinding.Severity.WARNING,
                    "the XMP packet names no conformance level, so the container does not"
                            + " say which profile the invoice is written in"));
            return;
        }
        if (properties.profile().isEmpty()) {
            findings.add(finding(ContainerFinding.Category.PDF_XMP, "PDF-XMP-CONFORMANCE",
                    ContainerFinding.Severity.WARNING,
                    "the XMP packet names the conformance level "
                            + Messages.quoted(level.get())
                            + ", which is no profile this reader knows"));
        }
    }

    private static void embedded(LocatedAttachment attachment,
                                 List<ContainerFinding> findings,
                                 boolean boundNotReported) {
        EmbeddedFile file = attachment.file();
        String name = file.name();
        mediaType(attachment, findings);
        if (attachment.kind() == AttachmentKind.UNREADABLE) {
            findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED,
                    "PDF-EMBEDDED-UNREADABLE", ContainerFinding.Severity.WARNING,
                    "the attachment " + Messages.quoted(name) + " is filtered with"
                            + " something this reader does not decode, so not one byte of"
                            + " it was looked at and what it holds is unknown here"));
        }
        if (attachment.kind() == AttachmentKind.UNDETERMINED) {
            findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED,
                    "PDF-EMBEDDED-UNDETERMINED", ContainerFinding.Severity.WARNING,
                    "the attachment " + Messages.quoted(name) + " begins an XML document"
                            + " whose root element this reader did not reach inside the"
                            + " bytes it classifies an attachment by, so what it holds was"
                            + " not established and it is counted among the attachments"
                            + " that could be the invoice"));
        }
        boolean conventional = CROSS_INDUSTRY_NAMES.contains(name.toLowerCase(Locale.ROOT));
        if (attachment.kind().isInvoice() && !name.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED, "PDF-EMBEDDED-NAME",
                    ContainerFinding.Severity.WARNING,
                    "the attachment " + Messages.quoted(name) + " carries an invoice in "
                            + attachment.kind().describe() + " and is not named as XML"));
        } else if (conventional && attachment.kind() != AttachmentKind.CII_INVOICE) {
            findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED, "PDF-EMBEDDED-NAME",
                    ContainerFinding.Severity.WARNING,
                    "the attachment " + Messages.quoted(name) + " carries the name a cross"
                            + " industry invoice has and its content is "
                            + attachment.kind().describe()));
        }
        file.decoded().ifPresent(content -> {
            if (content.truncated() && !boundNotReported) {
                findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED,
                        "PDF-EMBEDDED-TRUNCATED", ContainerFinding.Severity.ERROR,
                        "the attachment " + Messages.quoted(name) + " decodes to more than"
                                + " the bytes this reader holds, so it was cut off and"
                                + " nothing was read from it"));
            }
            file.declaredSize().ifPresent(declared -> {
                if (!content.truncated() && declared != content.length()) {
                    findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED,
                            "PDF-EMBEDDED-SIZE", ContainerFinding.Severity.WARNING,
                            "the attachment " + Messages.quoted(name) + " declares a size"
                                    + " of " + declared + " bytes and decodes to "
                                    + content.length()));
                }
            });
        });
    }

    private static void mediaType(LocatedAttachment attachment,
                                  List<ContainerFinding> findings) {
        if (attachment.kind() == AttachmentKind.NOT_XML
                || attachment.kind() == AttachmentKind.UNREADABLE
                || attachment.kind() == AttachmentKind.ESJ_DOCUMENT) {
            // The ESJ document is not XML and was classified partly by the media type it
            // declares, so a check that the declaration fits the content has nothing left
            // to find; EsjAgreement checks what it holds against the invoice instead.
            return;
        }
        EmbeddedFile file = attachment.file();
        Optional<String> declared = file.declaredMediaType();
        if (declared.isEmpty()) {
            findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED, "PDF-EMBEDDED-MIME",
                    ContainerFinding.Severity.WARNING,
                    "the attachment " + Messages.quoted(file.name()) + " carries XML and"
                            + " its embedded file stream declares no media type"));
            return;
        }
        if (!isXmlMediaType(declared.get())) {
            findings.add(finding(ContainerFinding.Category.PDF_EMBEDDED, "PDF-EMBEDDED-MIME",
                    ContainerFinding.Severity.WARNING,
                    "the attachment " + Messages.quoted(file.name()) + " carries XML and"
                            + " its embedded file stream declares the media type "
                            + Messages.quoted(declared.get())));
        }
    }

    private static boolean isXmlMediaType(String declared) {
        String type = declared.toLowerCase(Locale.ROOT);
        int parameters = type.indexOf(';');
        if (parameters >= 0) {
            type = type.substring(0, parameters);
        }
        type = type.trim();
        return type.equals("text/xml") || type.equals("application/xml") || type.endsWith("+xml");
    }

    private static ContainerFinding finding(ContainerFinding.Category category,
                                            String code,
                                            ContainerFinding.Severity severity,
                                            String message) {
        return new ContainerFinding(category, code, severity, message);
    }
}
