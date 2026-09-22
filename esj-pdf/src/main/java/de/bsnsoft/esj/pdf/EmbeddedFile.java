package de.bsnsoft.esj.pdf;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

/**
 * One file attached to a PDF: what the container says about it, and what its bytes are.
 *
 * <p>Everything the container says is a claim by whoever wrote the file and is reported
 * as such. The name may not match the content, the media type may be wrong, the declared
 * size may be anything at all, and the relationship the associated files array records
 * may be missing. None of the three is trusted for a decision; what decides is the first
 * bytes of the attachment itself (see {@link InvoiceAttachments}), and a claim that
 * disagrees with them is a finding rather than an instruction.
 *
 * <p>The content is decoded on demand and never beyond the bound this container runs. A
 * caller that only wants to know what an attachment is asks {@link #head(int)}, which
 * decodes a window and no more.
 */
public final class EmbeddedFile {

    private final String name;
    private final String declaredMediaType;
    private final long declaredSize;
    private final String associatedRelationship;
    private final boolean associated;
    private final PDEmbeddedFile stream;
    private final PdfContainer container;

    private AttachmentContent content;

    EmbeddedFile(String name,
                 String declaredMediaType,
                 long declaredSize,
                 String associatedRelationship,
                 boolean associated,
                 PDEmbeddedFile stream,
                 PdfContainer container) {
        this.name = Objects.requireNonNull(name, "name");
        this.declaredMediaType = declaredMediaType;
        this.declaredSize = declaredSize;
        this.associatedRelationship = associatedRelationship;
        this.associated = associated;
        this.stream = stream;
        this.container = Objects.requireNonNull(container, "container");
    }

    /**
     * Returns the name the container gives this attachment.
     *
     * <p>It is the file specification's unicode name where it has one, its byte-string
     * name otherwise, and the key of the name tree entry where the file specification
     * names nothing at all. It is attacker-controlled: it may carry path separators,
     * {@code ..}, control characters and a misleading double extension, and it MUST NOT
     * be used as a file system path (specification, section 12.5).
     *
     * @return the name, possibly empty
     */
    public String name() {
        return name;
    }

    /**
     * Returns the media type the embedded file stream declares in its {@code /Subtype}.
     *
     * @return the declared media type, or an empty optional where the stream declares
     *         none
     */
    public Optional<String> declaredMediaType() {
        return Optional.ofNullable(declaredMediaType);
    }

    /**
     * Returns the size the embedded file stream declares in its {@code /Params /Size}.
     *
     * @return the declared size in bytes, or an empty value where the stream declares
     *         none
     */
    public OptionalLong declaredSize() {
        return declaredSize < 0 ? OptionalLong.empty() : OptionalLong.of(declaredSize);
    }

    /**
     * Returns the {@code /AFRelationship} of the file specification: what the container
     * says this attachment is to the document it is attached to.
     *
     * @return the relationship as written, or an empty optional where there is none
     */
    public Optional<String> associatedRelationship() {
        return Optional.ofNullable(associatedRelationship);
    }

    /**
     * Tells whether the catalog's {@code /AF} array refers to this attachment.
     *
     * <p>A hybrid invoice has to say that the XML belongs to the document as a whole, and
     * that is what the array is for. An attachment that is only in the embedded files
     * name tree is a file that travels with the document; one that is in the array is a
     * file the document is about.
     *
     * @return {@code true} if the catalog refers to it
     */
    public boolean associated() {
        return associated;
    }

    /**
     * Tells whether the file specification carries an embedded file stream at all.
     *
     * <p>A file specification may name a file without embedding it, which is a reference
     * to something outside the document. Nothing outside the document is ever fetched.
     *
     * @return {@code true} if there are bytes to decode
     */
    public boolean hasContent() {
        return stream != null;
    }

    /**
     * Returns the first bytes of the attachment, decoding no more than asked for.
     *
     * <p>This is what the classification of an attachment is decided on, and it is
     * deliberately a window rather than the whole file: deciding what an attachment is
     * must not cost what reading it costs, or a container full of large attachments would
     * be expensive to refuse.
     *
     * @param maxBytes how many bytes to decode at most
     * @return the first bytes, shorter than asked for where the attachment is shorter,
     *         and empty where there is no embedded stream
     * @throws IllegalArgumentException if {@code maxBytes} is negative
     * @throws PdfFormatException       if the stream cannot be decoded
     */
    public byte[] head(int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("a window is a non-negative number of bytes");
        }
        if (stream == null) {
            return new byte[0];
        }
        if (content != null) {
            byte[] decoded = content.bytes();
            int length = Math.min(maxBytes, decoded.length);
            byte[] window = new byte[length];
            System.arraycopy(decoded, 0, window, 0, length);
            return window;
        }
        return read(maxBytes).bytes();
    }

    /**
     * Returns the decoded content of the attachment.
     *
     * <p>The bytes are decoded once and kept, and the decoding stops at the bound this
     * container runs; see {@link AttachmentContent}. Where the attachment has no embedded
     * stream the content is empty and not truncated.
     *
     * @return the content
     * @throws PdfLimitException  if the container has already produced as much decoded
     *                            content as {@link PdfLimits#maxTotalAttachmentBytes()}
     *                            allows
     * @throws PdfFormatException if the stream cannot be decoded
     */
    public AttachmentContent content() {
        if (content == null) {
            if (stream == null) {
                content = new AttachmentContent(new byte[0], false);
            } else {
                long reserved = container.claim(name);
                long actual = 0;
                try {
                    AttachmentContent read = read(reserved);
                    actual = read.length();
                    content = read;
                } finally {
                    // A decode that failed produced nothing, and the bound on all the
                    // attachments together counts bytes that were decoded: a reservation
                    // left standing would make the next attachment pay for this one.
                    container.settle(reserved, actual);
                }
            }
        }
        return content;
    }

    /**
     * Returns the content of this attachment if it has already been decoded, without
     * decoding it.
     *
     * <p>A check that needs the actual length of an attachment can ask this and stay
     * silent where the answer would cost a decode; see {@link ContainerChecks}.
     *
     * @return the content, or an empty optional where nothing has decoded it yet
     */
    public Optional<AttachmentContent> decoded() {
        return Optional.ofNullable(content);
    }

    /**
     * Reads at most {@code cap} bytes out of the stream, and finds out whether there were
     * more.
     *
     * <p>The filter chain is run by {@link BoundedStream} rather than by the library's own
     * decoding stream, because that one decodes the whole of a stream before it returns:
     * a bound applied to what it hands back would be applied to bytes that are already on
     * the heap, which is no bound at all against a stream written to inflate without end.
     */
    private AttachmentContent read(long cap) {
        return BoundedStream.decode(stream, cap, container.limits().maxAttachmentBytes(),
                "the attachment " + Messages.quoted(name), container.budget());
    }

    /**
     * Returns the attachment as one line: its name, its declared media type and its
     * declared size.
     *
     * @return a one-line description, with the content of the document escaped
     */
    @Override
    public String toString() {
        return Messages.quoted(name)
                + (declaredMediaType == null ? "" : " (" + Messages.escape(declaredMediaType) + ")")
                + (declaredSize < 0 ? "" : ", " + declaredSize + " bytes declared");
    }
}
