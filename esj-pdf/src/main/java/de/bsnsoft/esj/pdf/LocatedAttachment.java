package de.bsnsoft.esj.pdf;

import java.util.Objects;
import java.util.Optional;

/**
 * One attachment of a PDF together with what its bytes turned out to be.
 *
 * @param file the attachment, and what the container says about it
 * @param kind what its first bytes spell
 * @param root the qualified name of its root element, where it has one
 */
public record LocatedAttachment(EmbeddedFile file,
                                AttachmentKind kind,
                                Optional<String> root) {

    /**
     * Checks that no member is {@code null}.
     *
     * @param file the attachment, and what the container says about it
     * @param kind what its first bytes spell
     * @param root the qualified name of its root element, where it has one
     * @throws NullPointerException if a member is {@code null}
     */
    public LocatedAttachment {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(root, "root");
    }

    /**
     * Returns the name of the attachment, which is the name the container gives it and
     * is not to be trusted; see {@link EmbeddedFile#name()}.
     *
     * @return the name
     */
    public String name() {
        return file.name();
    }

    /**
     * Returns the attachment as one line: its name and what it is.
     *
     * @return a one-line description, with the content of the document escaped
     */
    @Override
    public String toString() {
        return Messages.quoted(file.name()) + " (" + kind.describe() + ")";
    }
}
