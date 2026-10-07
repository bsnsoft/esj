package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.pdf.AmbiguousInvoiceAttachmentException;
import de.bsnsoft.esj.pdf.AttachmentContent;
import de.bsnsoft.esj.pdf.AttachmentKind;
import de.bsnsoft.esj.pdf.ContainerChecks;
import de.bsnsoft.esj.pdf.ContainerFinding;
import de.bsnsoft.esj.pdf.EmbeddedFile;
import de.bsnsoft.esj.pdf.FacturXMetadata;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.pdf.InvoiceAttachments;
import de.bsnsoft.esj.pdf.LocatedAttachment;
import de.bsnsoft.esj.pdf.NoInvoiceAttachmentException;
import de.bsnsoft.esj.pdf.PdfAccessException;
import de.bsnsoft.esj.pdf.PdfContainer;
import de.bsnsoft.esj.pdf.PdfException;
import de.bsnsoft.esj.pdf.PdfLimits;
import de.bsnsoft.esj.pdf.PdfaIdentification;
import de.bsnsoft.esj.pdf.UnsupportedInvoiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A PDF as this tool reads it: a container, and the electronic invoice inside it.
 *
 * <p>Everything a command does with a PDF happens here and nowhere else. The container is
 * opened, every attachment is classified by its bytes, one of them is chosen, and from
 * that point on the run is the run it would have been if the same XML had been handed over
 * as a file. What the container had to say travels beside the invoice as findings of its
 * own, because a container that is wrong about the invoice it carries and an invoice that
 * is wrong are two different answers.
 *
 * <p><strong>Nothing is read off the page.</strong> A PDF whose invoice exists only as
 * printed text carries no structured invoice, and that is what is reported, with exit code
 * {@link ExitCode#INPUT}. There is no optical character recognition here and no heuristic
 * extraction of any kind.
 *
 * <p>The class also owns the one translation the command line needs: a failure of the
 * reader becomes an exit code. A bound of this run is {@link ExitCode#LIMIT} and never
 * {@link ExitCode#VALIDATION}, because a reader that stopped has said nothing about the
 * invoice it stopped reading; a container this project does not read is
 * {@link ExitCode#UNSUPPORTED}; everything else about the bytes is {@link ExitCode#INPUT}.
 */
final class Container {

    /** The semantic path of BT-24, the specification identifier of the invoice. */
    private static final SemanticPath SPECIFICATION_IDENTIFIER = SemanticPath.of("/BG-2/BT-24");

    /** What this tool calls a PDF it opened, in the {@code Detected} line of a report. */
    static final String LABEL = "PDF (hybrid invoice container)";

    private final List<LocatedAttachment> attachments;
    private final List<InvoiceAttachments.DuplicateName> duplicates;
    private final List<ContainerFinding> findings;
    private final Optional<FacturXMetadata> facturX;
    private final Optional<PdfaIdentification> pdfa;
    private final Invoice invoice;

    private Container(List<LocatedAttachment> attachments,
                      List<InvoiceAttachments.DuplicateName> duplicates,
                      List<ContainerFinding> findings,
                      Optional<FacturXMetadata> facturX,
                      Optional<PdfaIdentification> pdfa,
                      Invoice invoice) {
        this.attachments = List.copyOf(attachments);
        this.duplicates = List.copyOf(duplicates);
        this.findings = List.copyOf(findings);
        this.facturX = facturX;
        this.pdfa = pdfa;
        this.invoice = invoice;
    }

    /**
     * The attachment a command reads and the bytes it decoded to.
     *
     * <p>The position travels with it because the name does not identify anything: a PDF
     * permits two attachments of one name, and a report that named only the name could
     * not be tied back to the document it judged.
     *
     * @param attachment the attachment, with what its bytes turned out to be
     * @param position   where it stands in the enumeration of the container, counted
     *                   from one
     * @param bytes      its decoded content
     */
    record Invoice(LocatedAttachment attachment, int position, byte[] bytes) {

        /** Returns the name the container gives the attachment. */
        String name() {
            return attachment.name();
        }
    }

    /**
     * Opens a PDF and decodes the one attachment this run reads.
     *
     * @param input   the bytes of the file and the name to report them under
     * @param console the console, for the bounds of this run and {@code --attachment}
     * @return the container, with the chosen attachment decoded
     * @throws CliException if the file cannot be opened, carries no invoice, carries
     *                      several without one being named, or reaches a bound
     */
    static Container read(Input input, Console console) {
        return open(input, console, true);
    }

    /**
     * Opens a PDF and classifies its attachments without decoding any of them.
     *
     * @param input   the bytes of the file and the name to report them under
     * @param console the console, for the bounds of this run
     * @return the container, with no attachment chosen
     * @throws CliException if the file cannot be opened or reaches a bound
     */
    static Container list(Input input, Console console) {
        return open(input, console, false);
    }

    private static Container open(Input input, Console console, boolean decode) {
        Bounds bounds = console.options().bounds();
        PdfLimits limits = bounds.pdfLimits();
        return LimitRefusal.during(limit -> bounds.refusal(input.name(), limit.getMessage()),
                () -> {
                    try (PdfContainer container = PdfContainer.open(input.bytes(), limits)) {
                        InvoiceAttachments located = InvoiceAttachments.locate(container);
                        Invoice invoice = decode ? decode(located, input, console, limits)
                                : null;
                        return new Container(located.all(), located.duplicateNames(),
                                ContainerChecks.run(container, located), container.facturX(),
                                container.pdfaIdentification(), invoice);
                    } catch (PdfException e) {
                        throw refuse(e, input);
                    }
                });
    }

    /**
     * Chooses the attachment this run reads and decodes it.
     *
     * <p>The checks of the container are run afterwards, by the caller, because some of
     * them are about the attachment that was decoded — whether it was cut off at a bound,
     * and whether its declared size is its size.
     */
    private static Invoice decode(InvoiceAttachments located,
                                  Input input,
                                  Console console,
                                  PdfLimits limits) {
        boolean picked = console.options().attachment() != null
                || console.options().attachmentIndex() != null;
        LocatedAttachment chosen = choose(located, input, console);
        int position = positionOf(located, chosen);
        console.verbose(input.name() + ": reading the attachment at position " + position
                + ", " + describe(chosen)
                + (picked ? "" : " (the one electronic invoice it carries)"));
        if (chosen.kind() == AttachmentKind.ZUGFERD_1) {
            throw CliException.unsupported(input.name() + " carries " + describe(chosen)
                    + ": ZUGFeRD 1.0 predates EN 16931 and is no binding of it, so this"
                    + " project does not read it");
        }
        AttachmentContent content = chosen.file().content();
        if (content.truncated()) {
            throw CliException.limit(console.options().bounds().refusal(input.name(),
                    "the attachment " + ValueText.quoted(chosen.name()) + " decodes to more"
                            + " than the " + limits.maxAttachmentBytes() + " bytes this"
                            + " reader holds, so it was cut off and no invoice was read"
                            + " from it"));
        }
        return new Invoice(chosen, position, content.bytes());
    }

    /**
     * Returns where an attachment stands in the enumeration of its container.
     *
     * <p>The list is searched by identity rather than by equality, because two
     * attachments of one container may say the same thing about themselves — one name,
     * one media type, one declared size — and the answer has to be the one that was
     * chosen rather than the first that looks like it.
     */
    private static int positionOf(InvoiceAttachments located, LocatedAttachment chosen) {
        List<LocatedAttachment> all = located.all();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) == chosen) {
                return i + 1;
            }
        }
        throw new IllegalStateException("the chosen attachment is not one of the"
                + " container's");
    }

    /**
     * Chooses the attachment to read: the one the position names, the one the name names,
     * or the one the container carries.
     *
     * <p>Neither switch may resolve silently. A PDF permits two attachments of one name,
     * so a name that matches more than one is refused here exactly as a container with two
     * invoices is refused: the tool prints one verdict, and it has to be about a document
     * the caller picked rather than about whichever of two a name tree happened to list
     * first. The position is the answer for that case, and it is the one selector the file
     * cannot influence.
     *
     * <p>The two switches together have to agree. Given both, the position selects and
     * the name is checked against it: a run that names one attachment and indexes another
     * is a run whose caller believes something about the file that is not so, and
     * answering it about either of the two would be answering a question nobody asked.
     * The refusal names both, because the ambiguity message that sends a caller here
     * offers both switches on one line and passing both is the expected mistake.
     */
    private static LocatedAttachment choose(InvoiceAttachments located,
                                            Input input,
                                            Console console) {
        Integer position = console.options().attachmentIndex();
        String named = console.options().attachment();
        try {
            if (position != null) {
                LocatedAttachment at = located.at(position)
                        .orElseThrow(() -> CliException.input(input.name()
                                + " has no attachment at position " + position
                                + ", and carries " + located.all().size()
                                + (located.all().size() == 1 ? " attachment:" : " attachments:")
                                + numbered(located.all())));
                if (named != null && !named.equals(at.name())) {
                    throw CliException.input(input.name() + ": --attachment "
                            + ValueText.quoted(named) + " and --attachment-index "
                            + position + " name two different attachments — position "
                            + position + " is " + describe(at) + ". Give one of the two,"
                            + " or the position of an attachment that carries that name:"
                            + numbered(located.all()));
                }
                return at;
            }
            if (named != null) {
                return located.named(named).orElseThrow(() -> CliException.input(input.name()
                        + " carries no attachment named " + ValueText.quoted(named)
                        + numbered(located.all())));
            }
            return located.single();
        } catch (AmbiguousInvoiceAttachmentException e) {
            throw CliException.input(ambiguity(input.name(), located, e.candidates(), named));
        }
    }

    /**
     * Says that the container did not name the document and lists what a caller may pick
     * from, each with the position it has in the container.
     *
     * <p>The position is printed because the name is not always enough: two attachments
     * may carry one name, and then {@code --attachment} cannot separate them and saying
     * so is part of the answer.
     */
    private static String ambiguity(String input,
                                    InvoiceAttachments located,
                                    List<LocatedAttachment> candidates,
                                    String named) {
        StringBuilder text = new StringBuilder(input);
        if (named == null) {
            text.append(" carries ").append(candidates.size())
                    .append(" attachments that could be the electronic invoice, and nothing"
                            + " says which of them the document is:");
        } else {
            text.append(" carries ").append(candidates.size())
                    .append(" attachments named ").append(ValueText.quoted(named))
                    .append(", and the name does not say which of them is meant:");
        }
        for (LocatedAttachment candidate : candidates) {
            text.append("\n  ").append(located.all().indexOf(candidate) + 1)
                    .append("  ").append(describe(candidate));
        }
        for (InvoiceAttachments.DuplicateName duplicate : located.duplicateNames()) {
            text.append("\n  ").append(sharedName(duplicate))
                    .append(positions(located.all(), duplicate.attachments()))
                    .append(", so that name cannot say which of them is meant");
        }
        text.append("\n  use --attachment <name>, or --attachment-index <position> where"
                + " two attachments carry one name");
        return text.toString();
    }

    /**
     * Returns what gives several attachments one name, as the beginning of a sentence
     * that goes on with their positions.
     */
    private static String sharedName(InvoiceAttachments.DuplicateName duplicate) {
        return duplicate.source() == InvoiceAttachments.DuplicateName.Source.NAME_TREE_KEY
                ? "the embedded files name tree lists under one name, "
                        + ValueText.quoted(duplicate.name()) + ", the attachments at positions "
                : "one file specification, " + ValueText.quoted(duplicate.name())
                        + ", holds the attachments at positions ";
    }

    /** Returns the positions of some attachments in the enumeration, as "1 and 2". */
    private static String positions(List<LocatedAttachment> all,
                                    List<LocatedAttachment> some) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < some.size(); i++) {
            if (i > 0) {
                text.append(i == some.size() - 1 ? " and " : ", ");
            }
            text.append(all.indexOf(some.get(i)) + 1);
        }
        return text.toString();
    }

    /**
     * Returns every attachment of the container, in the order it was enumerated.
     *
     * @return the attachments, possibly none
     */
    List<LocatedAttachment> attachments() {
        return attachments;
    }

    /**
     * Returns what the checks had to say about the container.
     *
     * @return the findings, in the order they were made
     */
    List<ContainerFinding> findings() {
        return findings;
    }

    /**
     * Returns the findings about one part of the container.
     *
     * @param category the part
     * @return its findings, in the order they were made
     */
    List<ContainerFinding> findings(ContainerFinding.Category category) {
        return findings.stream().filter(finding -> finding.category() == category).toList();
    }

    /**
     * Returns the Factur-X properties of the XMP packet, where the file carries them.
     *
     * @return the properties, or an empty optional
     */
    Optional<FacturXMetadata> facturX() {
        return facturX;
    }

    /**
     * Returns the PDF/A conformance the file declares for itself, which was not validated.
     *
     * @return the declaration, or an empty optional
     */
    Optional<PdfaIdentification> pdfa() {
        return pdfa;
    }

    /**
     * Returns the attachment this run read, where one was chosen.
     *
     * @return the invoice attachment and its bytes, or an empty optional
     */
    Optional<Invoice> invoice() {
        return Optional.ofNullable(invoice);
    }

    /**
     * Returns the attachment this run read.
     *
     * @return the invoice attachment and its bytes
     * @throws IllegalStateException if this container was opened without choosing one
     */
    Invoice requireInvoice() {
        if (invoice == null) {
            throw new IllegalStateException("this container was opened without reading an"
                    + " attachment");
        }
        return invoice;
    }

    /**
     * Tells whether the container is wrong about the document it carries.
     *
     * @return {@code true} if a finding is an error
     */
    boolean hasErrors() {
        return findings.stream()
                .anyMatch(finding -> finding.severity() == ContainerFinding.Severity.ERROR);
    }

    /**
     * Returns this container with one more finding, for a check that needs the invoice.
     *
     * @param finding the finding to add
     * @return a container carrying it
     */
    Container with(ContainerFinding finding) {
        List<ContainerFinding> all = new ArrayList<>(findings);
        all.add(finding);
        return new Container(attachments, duplicates, all, facturX, pdfa, invoice);
    }

    /**
     * Returns this container with the finding a disagreement between the packet and the
     * invoice draws, where the two disagree.
     *
     * @param document the invoice that was read out of it
     * @return this container, or one finding richer
     */
    Container against(SemanticDocument document) {
        return ContainerChecks.profileAgainstInvoice(facturX, document)
                .map(this::with)
                .orElse(this);
    }

    /**
     * Returns the profile the document is written in.
     *
     * <p>The invoice is asked first, because the invoice is the document: BT-24 is written
     * by whoever wrote the invoice, and the XMP packet is a claim the container makes about
     * it. Where the two disagree, that disagreement is a finding of {@link ContainerChecks}
     * rather than a reason to believe the container.
     *
     * @param document the invoice that was read out of the container
     * @return the profile, or an empty optional where neither names one this tool knows
     */
    Optional<FacturXProfile> profile(SemanticDocument document) {
        return document.value(SPECIFICATION_IDENTIFIER)
                .map(SemanticValue::content)
                .flatMap(FacturXProfile::ofSpecificationIdentifier)
                .or(() -> facturX.flatMap(FacturXMetadata::profile));
    }

    /**
     * Returns the profile the XMP packet declares, without asking the invoice.
     *
     * @return the profile, or an empty optional
     */
    Optional<FacturXProfile> declaredProfile() {
        return facturX.flatMap(FacturXMetadata::profile);
    }

    /**
     * Returns one attachment as one line: its name, what its bytes are and how large the
     * container says it is.
     *
     * @param attachment the attachment
     * @return the line, with everything the document chose escaped
     */
    static String describe(LocatedAttachment attachment) {
        return named(attachment) + outsideTheTree(attachment.file());
    }

    /** Returns an attachment's name, what its bytes are and the size it declares. */
    private static String named(LocatedAttachment attachment) {
        return ValueText.quoted(attachment.name()) + " (" + attachment.kind().describe()
                + ", " + size(attachment.file()) + ")";
    }

    /**
     * Says where an attachment the embedded files name tree does not list is referred to
     * from, and says nothing for one it lists.
     *
     * <p>A hybrid invoice lists its invoice in the tree, and so does every file this tool
     * writes, so the ordinary line is unchanged; an attachment a page refers to, or only an
     * associated files array, is not where a reader of the tree looks, and a line that did
     * not say so would describe it as if it were.
     *
     * @param file the attachment
     * @return the suffix, empty for an attachment the tree lists
     */
    static String outsideTheTree(EmbeddedFile file) {
        if (file.inNameTree()) {
            return "";
        }
        List<String> places = file.references().stream()
                .map(EmbeddedFile.Reference::describe)
                .distinct()
                .toList();
        int shown = Math.min(3, places.size());
        return ", not in the name tree: " + String.join(", ", places.subList(0, shown))
                + (places.size() > shown ? " and " + (places.size() - shown) + " more" : "");
    }

    /**
     * Returns the attachment a run read, with the position that identifies it.
     *
     * <p>The name does not identify it. A container may carry two attachments of one
     * name, and it writes their sizes itself, so a report that showed the name and the
     * declared size could describe either of them; the position is this reader's own
     * numbering and is the one thing the file cannot repeat.
     *
     * @param invoice the attachment this run read
     * @return the line, with everything the document chose escaped
     */
    static String describe(Invoice invoice) {
        return describe(invoice.attachment()) + ", position " + invoice.position();
    }

    /**
     * Returns one attachment as one line with everything the container says about it: what
     * its bytes are, what the file calls it, what media type it declares and what the
     * document says it is to it.
     *
     * @param attachment the attachment
     * @return the line, with everything the document chose escaped
     */
    static String detail(LocatedAttachment attachment) {
        EmbeddedFile file = attachment.file();
        return named(attachment)
                + ", " + file.declaredMediaType()
                        .map(type -> "media type " + ValueText.quoted(type))
                        .orElse("no media type declared")
                + ", " + file.associatedRelationship()
                        .map(value -> "AFRelationship " + ValueText.quoted(value))
                        .orElse("no AFRelationship")
                + (file.associated() ? ", in /AF" : ", not in /AF")
                + outsideTheTree(file);
    }

    /**
     * Returns every attachment of the container as one numbered line with everything the
     * container says about it, as {@code esj extract --list} and {@code esj inspect} print
     * them.
     *
     * <p>An attachment the embedded files name tree lists under a key it lists another
     * attachment under says so on its line: the key is the name a reader looks it up by,
     * and a listing that showed two lines of one name without saying that the tree gives
     * that name to both would leave its reader to find out which one a reader is handed.
     *
     * @return the lines, indented, one per attachment
     */
    List<String> listing() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < attachments.size(); i++) {
            LocatedAttachment attachment = attachments.get(i);
            StringBuilder line = new StringBuilder("  ").append(i + 1).append("  ")
                    .append(detail(attachment));
            for (InvoiceAttachments.DuplicateName duplicate : duplicates) {
                if (!duplicate.attachments().contains(attachment)) {
                    continue;
                }
                line.append(", one of ").append(duplicate.attachments().size())
                        .append(duplicate.source()
                                == InvoiceAttachments.DuplicateName.Source.NAME_TREE_KEY
                                ? " files the name tree lists under "
                                : " files the file specification holds that is named ")
                        .append(ValueText.quoted(duplicate.name()));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Returns the size an attachment declares, which is a claim of the file and not a
     * measurement: reading the bytes to measure them is what this tool declines to do for
     * an attachment it is only listing.
     *
     * @param file the attachment
     * @return the declared size in bytes, or that none is declared
     */
    static String size(EmbeddedFile file) {
        OptionalLong declared = file.declaredSize();
        return declared.isPresent()
                ? declared.getAsLong() + " bytes declared"
                : "no size declared";
    }

    /** Returns the attachments as a numbered list, one per line, indented under a message. */
    private static String numbered(List<LocatedAttachment> attachments) {
        StringBuilder text = new StringBuilder();
        int ordinal = 1;
        for (LocatedAttachment attachment : attachments) {
            text.append("\n  ").append(ordinal++).append("  ").append(describe(attachment));
        }
        return text.toString();
    }

    /**
     * Turns a failure of the reader into the exit code it means.
     *
     * <p>They are kept apart because a caller acts differently on each of them: a container
     * this project does not read is answered with another tool, and a PDF without an
     * invoice with a request for the invoice. A bound of this run is answered with more
     * resources and is no {@link PdfException}; {@link LimitRefusal} refuses it. The
     * container that carries several is refused in {@link #choose}, where the positions of
     * the candidates are still known.
     */
    private static CliException refuse(PdfException e, Input input) {
        String name = input.name();
        if (e instanceof UnsupportedInvoiceException unsupported) {
            return CliException.unsupported(name + " carries "
                    + describe(unsupported.attachment()) + ", which is no binding of"
                    + " EN 16931 and is not read by this project");
        }
        if (e instanceof NoInvoiceAttachmentException none) {
            return CliException.input(noInvoice(name, none));
        }
        if (e instanceof PdfAccessException) {
            return CliException.input("cannot open " + name + ": " + e.getMessage(), e);
        }
        // What is left is PdfFormatException: the bytes are no PDF this reader opens, or
        // its object structure is damaged past recovery. EmbedRefusedException is the one
        // other member of the hierarchy and cannot arrive here, because it is raised by
        // writing a hybrid invoice, which is Embedding and not this class.
        return CliException.input("cannot read " + name + " as a PDF: " + e.getMessage(), e);
    }

    /**
     * Says that no attachment of a PDF was read as an invoice, in the words that fit the
     * case.
     *
     * <p>There are two cases and they are two statements. Where every attachment was
     * classified, the file carries no structured invoice and the tool says so, with the
     * sentence that this is the end of the matter because nothing is read off the page.
     * Where an attachment begins an XML document whose root element lies beyond the bytes
     * this reader classifies by, nothing of the sort was established: the attachment may
     * well be an invoice, the reader simply did not look far enough, and the caller is
     * given the switch that reads it anyway. Printing the first sentence for the second
     * case would be asserting something nobody checked.
     */
    private static String noInvoice(String name, NoInvoiceAttachmentException none) {
        if (none.unestablished().isEmpty()) {
            return name + ": the PDF contains no structured invoice representation"
                    + (none.attachments().isEmpty()
                            ? " and carries no attachment at all"
                            : "; none of its attachments is an electronic invoice:"
                                    + numbered(none.attachments()))
                    + "\n  nothing is read off the page: this tool validates structured"
                    + " data or says that there is none";
        }
        boolean beyondWindow = none.unestablished().stream()
                .anyMatch(attachment -> attachment.kind() == AttachmentKind.UNDETERMINED);
        boolean undecoded = none.unestablished().stream()
                .anyMatch(attachment -> attachment.kind() == AttachmentKind.UNREADABLE);
        return name + ": no attachment of this PDF was established to be an electronic"
                + " invoice:"
                + numbered(none.attachments())
                + (beyondWindow
                        ? "\n  an attachment begins an XML document whose root element lies"
                                + " beyond the bytes this reader classifies an attachment"
                                + " by, so what it holds was not established; use"
                                + " --attachment <name> or --attachment-index <position> to"
                                + " read it anyway, and the report says that it was read"
                                + " without having been classified"
                        : "")
                + (undecoded
                        ? "\n  an attachment is filtered with something this reader does not"
                                + " decode, so not one byte of it was looked at"
                        : "");
    }
}
