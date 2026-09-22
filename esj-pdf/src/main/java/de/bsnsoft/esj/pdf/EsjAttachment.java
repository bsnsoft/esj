package de.bsnsoft.esj.pdf;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The ESJ document a hybrid invoice carries beside the invoice XML.
 *
 * <p>A hybrid invoice declares one electronic invoice, and that is the XML. PDF/A-3 is the
 * part of ISO 19005 that admits an embedded file of any type, and the hybrid invoice
 * specifications admit further attachments beside the invoice for a reader that has a use
 * for them; {@code docs/sources.md} records where those two facts were taken from. This
 * class is the one place that says what this project's further attachment is called, what
 * media type it declares and what relationship it is declared under.
 *
 * <p><strong>It is never the invoice.</strong> The relationship is the one an enclosure
 * takes, the name is not one any container specification gives an invoice, and the
 * classification below puts it in a kind {@link InvoiceAttachments} never offers as a
 * candidate. A file that carries this attachment and no invoice XML carries no electronic
 * invoice.
 *
 * <p>The name and the media type take part in the classification, which nothing else about
 * an attachment does (see {@link AttachmentKind}). They may do so here because the answer
 * they can produce is "this is not the invoice, and it is labelled as this project's own
 * document": a container that lies about either of them loses a label and gains nothing,
 * and what the attachment actually holds is read and checked by {@link EsjAgreement}
 * before a single value of it is believed.
 */
public final class EsjAttachment {

    /** What the attachment is called. */
    public static final String NAME = "invoice.esj.json";

    /** The media type its embedded file stream declares. */
    public static final String MEDIA_TYPE = "application/json";

    /**
     * The relationship it is declared with: the value PDF 32000-2 defines for a file that
     * travels with the document rather than being it, which is what a further attachment
     * beside the invoice is.
     */
    public static final String RELATIONSHIP = "Supplement";

    /** What the file specification says the attachment is, for a reader that lists it. */
    static final String DESCRIPTION = "the same invoice as an EN16931 Semantic JSON document";

    private EsjAttachment() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the attachment of a container that is this project's ESJ document.
     *
     * @param located the attachments of the container, classified
     * @return the one attachment classified as {@link AttachmentKind#ESJ_DOCUMENT}, or an
     *         empty optional where the container carries none or carries more than one
     * @throws NullPointerException if {@code located} is {@code null}
     */
    public static Optional<LocatedAttachment> in(InvoiceAttachments located) {
        List<LocatedAttachment> found = located.all().stream()
                .filter(attachment -> attachment.kind() == AttachmentKind.ESJ_DOCUMENT)
                .toList();
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

    /**
     * Tells whether an attachment is labelled as this project's ESJ document: the name,
     * the declared media type, and a window of content that begins a JSON object.
     */
    static boolean labelled(EmbeddedFile file, byte[] head) {
        return NAME.equals(file.name())
                && file.declaredMediaType().map(EsjAttachment::isJson).orElse(Boolean.FALSE)
                && beginsAnObject(head);
    }

    /** Tells whether a declared media type is JSON, parameters and case aside. */
    private static boolean isJson(String declared) {
        String type = declared.toLowerCase(Locale.ROOT);
        int parameters = type.indexOf(';');
        if (parameters >= 0) {
            type = type.substring(0, parameters);
        }
        return MEDIA_TYPE.equals(type.trim());
    }

    /**
     * Tells whether the first bytes of an attachment begin a JSON object, a byte order
     * mark and insignificant whitespace aside.
     */
    private static boolean beginsAnObject(byte[] head) {
        for (int i = 0; i < head.length; i++) {
            int b = head[i] & 0xFF;
            if (b == 0xEF || b == 0xBB || b == 0xBF
                    || b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                continue;
            }
            return b == '{';
        }
        return false;
    }
}
