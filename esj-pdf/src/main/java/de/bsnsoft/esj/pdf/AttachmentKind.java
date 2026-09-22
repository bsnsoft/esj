package de.bsnsoft.esj.pdf;

import java.util.Optional;
import de.bsnsoft.esj.xr.XrSyntax;

/**
 * What an attachment of a PDF turned out to be, decided by its first bytes.
 *
 * <p>The decision is made on the content and on nothing else. A name is a string somebody
 * chose and a media type is a string somebody chose; either may be wrong by accident and
 * either may be wrong on purpose, and a validator that read {@code factur-x.xml} and
 * concluded "this is a CII invoice" would be told what to conclude by the file it is
 * examining.
 */
public enum AttachmentKind {

    /** A UN/CEFACT cross industry invoice, CII D16B: the usual content of a Factur-X file. */
    CII_INVOICE(true, true),

    /** A UBL 2.1 invoice. */
    UBL_INVOICE(true, true),

    /** A UBL 2.1 credit note. */
    UBL_CREDIT_NOTE(true, true),

    /**
     * A ZUGFeRD 1.0 invoice, whose root element is {@code CrossIndustryDocument}.
     *
     * <p>It is an electronic invoice and it is not a binding of EN 16931 — it predates
     * the standard. This project refuses it rather than inventing a semantic model for
     * it; see {@link UnsupportedInvoiceException}.
     */
    ZUGFERD_1(true, false),

    /** An XML document of some other kind: an order, a signature, a producer's own data. */
    OTHER_XML(false, false),

    /**
     * An EN16931 Semantic JSON document attached beside the invoice, as
     * {@link EsjAttachment} describes it.
     *
     * <p>It is not an invoice of this container: the electronic invoice of a hybrid file
     * is its XML, and this attachment is an enclosure the container declares as one. What
     * it holds is read only in order to be checked against the invoice
     * ({@link EsjAgreement}), never instead of it.
     */
    ESJ_DOCUMENT(false, false),

    /**
     * XML whose root element this reader did not reach inside the window it classifies an
     * attachment by, so what the attachment is was never established.
     *
     * <p>The prolog of an XML document has no length limit, and whoever writes the file
     * decides how long it is. An attachment with a long enough comment in front of its
     * root element would otherwise be filed as "XML of some other kind" — that is, as
     * something a container may carry beside its invoice — and a second invoice hidden
     * that way would slip past the rule that a container with two of them is never chosen
     * from silently. Saying that the question was not answered is the only answer that
     * keeps that rule.
     */
    UNDETERMINED(false, false),

    /** Not XML at all: an image, a spreadsheet, a second PDF, anything. */
    NOT_XML(false, false),

    /**
     * An attachment whose stream this reader does not decode, so not one byte of it was
     * looked at.
     *
     * <p>An attachment is filtered with whatever its writer chose, and the decoders of
     * some filters allocate from the numbers in the file before they produce anything;
     * those are not run here (see {@code BoundedStream}). An attachment that is filtered
     * that way is not read, and it is not thereby a reason to refuse the container: the
     * invoice beside it is still the document, and an attachment nobody can decode is an
     * attachment nobody mistook for an invoice either. Naming it with a selector says so
     * in the reader's own words.
     */
    UNREADABLE(false, false);

    private final boolean invoice;
    private final boolean supported;

    AttachmentKind(boolean invoice, boolean supported) {
        this.invoice = invoice;
        this.supported = supported;
    }

    /**
     * Tells whether this kind is an electronic invoice, whether or not this project reads
     * it.
     *
     * <p>The two questions are separate on purpose. A file that carries one ZUGFeRD 1.0
     * attachment and nothing else carries an invoice this project will not read, and
     * saying so is a different answer from saying that the file carries no invoice.
     *
     * @return {@code true} for the four invoice kinds
     */
    public boolean isInvoice() {
        return invoice;
    }

    /**
     * Tells whether this project reads an invoice of this kind.
     *
     * @return {@code true} for everything but ZUGFeRD 1.0
     */
    public boolean isSupported() {
        return supported;
    }

    /**
     * Returns the syntax the importer of {@code esj-xr} reads this kind as.
     *
     * @return the syntax, or an empty optional for a kind that importer does not read
     */
    public Optional<XrSyntax> syntax() {
        return switch (this) {
            case CII_INVOICE -> Optional.of(XrSyntax.CII);
            case UBL_INVOICE -> Optional.of(XrSyntax.UBL_INVOICE);
            case UBL_CREDIT_NOTE -> Optional.of(XrSyntax.UBL_CREDIT_NOTE);
            default -> Optional.empty();
        };
    }

    /**
     * Returns a short name for a message.
     *
     * @return the name, such as {@code CII} or {@code UBL credit note}
     */
    public String describe() {
        return switch (this) {
            case CII_INVOICE -> "CII";
            case UBL_INVOICE -> "UBL invoice";
            case UBL_CREDIT_NOTE -> "UBL credit note";
            case ZUGFERD_1 -> "ZUGFeRD 1.0";
            case OTHER_XML -> "XML";
            case ESJ_DOCUMENT -> "ESJ document, not the invoice";
            case UNDETERMINED -> "XML, root element beyond the window";
            case NOT_XML -> "not XML";
            case UNREADABLE -> "filtered with something this reader does not decode";
        };
    }
}
