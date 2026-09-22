package de.bsnsoft.esj.pdf;

import java.util.Optional;

/**
 * A PDF/A validator the caller lends to {@link FacturX#embed}.
 *
 * <p>Embedding requires a PDF/A-3 input, and the cheap way to establish that is to read
 * what the file declares about itself in its XMP packet — which is what this module does
 * by default and what {@link PdfaIdentification} is careful to call a declaration. A
 * declaration is not a validation, and a caller that wants the stronger statement passes
 * one of these: the bytes go to a real validator before anything is written into them,
 * and a file that declares PDF/A-3 without being it is refused.
 *
 * <p>The validator is the caller's because this project publishes no artefact that
 * carries one. The reference implementation of the PDF/A validation model is under a
 * copyleft licence, so it appears in this project as a test-scope dependency and in
 * nothing that is released; {@code docs/pdf-output.md} records that. An application that
 * is willing to take that dependency implements this interface over it in three lines.
 */
@FunctionalInterface
public interface PdfaCheck {

    /**
     * Returns why these bytes are not a PDF/A-3 file, or nothing where they are one.
     *
     * @param pdf the bytes of the file, before anything has been embedded into them
     * @return the reason, in English, for a caller's message, or an empty optional where
     *         the file passed
     */
    Optional<String> notPdfa3(byte[] pdf);
}
