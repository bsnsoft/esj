package de.bsnsoft.esj.invoice;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A document that travels with the invoice: an additional supporting document (BG-24).
 *
 * <p>It states the reference of the document (BT-122), a description of it (BT-123) and
 * either the place it can be fetched from (BT-124) or the document itself, embedded with
 * its media type and its file name (BT-125, BT-125-1, BT-125-2). EN 16931-1 allows the
 * location and the embedded object side by side; a caller that wants both writes the
 * location on an attachment that already carries bytes.
 *
 * <p>The attachment is a value: every method returns a new one and the original is
 * unchanged.
 */
public final class Attachment {

    private final String reference;

    private final String description;

    private final String location;

    private final byte[] content;

    private final String mimeCode;

    private final String filename;

    private Attachment(String reference, String description, String location, byte[] content,
                       String mimeCode, String filename) {
        this.reference = reference;
        this.description = description;
        this.location = location;
        this.content = content;
        this.mimeCode = mimeCode;
        this.filename = filename;
    }

    /**
     * An attachment that is only referenced (BT-122).
     *
     * @param reference the reference of the supporting document
     * @return the attachment
     * @throws IllegalArgumentException if the reference is blank
     * @throws NullPointerException     if {@code reference} is {@code null}
     */
    public static Attachment referencing(String reference) {
        return new Attachment(Amounts.text(reference, "reference"), null, null, null, null, null);
    }

    /**
     * An attachment that travels inside the invoice (BT-125).
     *
     * @param reference the reference of the supporting document (BT-122)
     * @param content   the bytes of the document
     * @param mimeCode  the media type (BT-125-1), for example {@code application/pdf}
     * @param filename  the file name (BT-125-2)
     * @return the attachment
     * @throws IllegalArgumentException if a string is blank or the content is empty
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Attachment embedded(String reference, byte[] content, String mimeCode,
                                      String filename) {
        Objects.requireNonNull(content, "content");
        if (content.length == 0) {
            throw new IllegalArgumentException("an embedded document (BT-125) carries bytes");
        }
        return new Attachment(Amounts.text(reference, "reference"), null, null, content.clone(),
                Amounts.text(mimeCode, "mimeCode"), Amounts.text(filename, "filename"));
    }

    /**
     * Returns this attachment with a description of it (BT-123).
     *
     * @param value the description
     * @return a new attachment
     * @throws IllegalArgumentException if the description is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Attachment description(String value) {
        return new Attachment(reference, Amounts.text(value, "value"), location, content, mimeCode,
                filename);
    }

    /**
     * Returns this attachment with the place it can be fetched from (BT-124).
     *
     * @param value the external location
     * @return a new attachment
     * @throws IllegalArgumentException if the location is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Attachment location(String value) {
        return new Attachment(reference, description, Amounts.text(value, "value"), content,
                mimeCode, filename);
    }

    /**
     * Returns the reference of the supporting document (BT-122).
     *
     * @return the reference
     */
    public String reference() {
        return reference;
    }

    /**
     * Returns the description (BT-123).
     *
     * @return the description, or an empty optional
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the external location (BT-124).
     *
     * @return the location, or an empty optional
     */
    public Optional<String> location() {
        return Optional.ofNullable(location);
    }

    /**
     * Returns the embedded document (BT-125).
     *
     * @return a copy of the bytes, or an empty optional where nothing is embedded
     */
    public Optional<byte[]> content() {
        return Optional.ofNullable(content).map(byte[]::clone);
    }

    /**
     * Returns the media type of the embedded document (BT-125-1).
     *
     * @return the media type, or an empty optional
     */
    public Optional<String> mimeCode() {
        return Optional.ofNullable(mimeCode);
    }

    /**
     * Returns the file name of the embedded document (BT-125-2).
     *
     * @return the file name, or an empty optional
     */
    public Optional<String> filename() {
        return Optional.ofNullable(filename);
    }

    /**
     * Two attachments are equal where they state the same parts, the embedded bytes
     * included. An attachment read back out of a document therefore equals the one that
     * was written.
     *
     * @param other the object to compare with
     * @return whether the other object is an attachment with the same parts
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Attachment that)) {
            return false;
        }
        return Objects.equals(reference, that.reference)
                && Objects.equals(description, that.description)
                && Objects.equals(location, that.location)
                && Arrays.equals(content, that.content)
                && Objects.equals(mimeCode, that.mimeCode)
                && Objects.equals(filename, that.filename);
    }

    /**
     * The hash code of the parts, over the embedded bytes and not over their identity.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(reference, description, location, Arrays.hashCode(content), mimeCode,
                filename);
    }

    /**
     * The parts, with the size of the embedded document in place of its bytes.
     *
     * @return one line naming the reference and what else the attachment states
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("Attachment[BT-122=").append(reference);
        if (description != null) {
            text.append(", BT-123=").append(description);
        }
        if (location != null) {
            text.append(", BT-124=").append(location);
        }
        if (content != null) {
            text.append(", BT-125=").append(content.length).append(" bytes of ").append(mimeCode)
                    .append(" as ").append(filename);
        }
        return text.append(']').toString();
    }
}
