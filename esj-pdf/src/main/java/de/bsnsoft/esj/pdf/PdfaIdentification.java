package de.bsnsoft.esj.pdf;

import java.util.Objects;
import java.util.Optional;

/**
 * The PDF/A conformance a file declares for itself in its XMP packet.
 *
 * <p>It is a declaration and this module treats it as nothing more. Validating PDF/A
 * conformance means checking the fonts, the colour spaces, the transparency, the
 * structure and a hundred other things that have nothing to do with an invoice, and this
 * project does none of it; a report that said "PDF/A-3B" without that qualification would
 * be claiming something it had not checked. Every report therefore writes the word
 * <em>declared</em> beside this.
 *
 * <p>A hybrid invoice is conventionally PDF/A-3, because part 3 is the part that allows
 * a file of any type to be embedded. Nothing here requires that: the invoice is read out
 * of the attachments whatever the file declares, and a file that declares nothing at all
 * is read like any other.
 *
 * @param part       the part of ISO 19005 the file declares, {@code pdfaid:part}
 * @param conformance the conformance level the file declares, {@code pdfaid:conformance},
 *                   such as {@code B} or {@code U}
 */
public record PdfaIdentification(int part, Optional<String> conformance) {

    /**
     * Checks that the conformance level is present as an optional.
     *
     * @param part       the part of ISO 19005 the file declares, {@code pdfaid:part}
     * @param conformance the conformance level the file declares, {@code pdfaid:conformance},
     *                   such as {@code B} or {@code U}
     * @throws NullPointerException if {@code conformance} is {@code null}
     */
    public PdfaIdentification {
        Objects.requireNonNull(conformance, "conformance");
    }

    /**
     * Returns the declaration as it is conventionally written, such as {@code PDF/A-3B}.
     *
     * @return the declaration in one token
     */
    public String describe() {
        return "PDF/A-" + part + conformance.orElse("");
    }
}
