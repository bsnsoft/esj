package de.bsnsoft.esj.cli;

import java.util.List;
import java.util.Optional;

/**
 * One resource bound a run can be configured with, and the switch that raises it.
 *
 * <p>A refusal by a limit is of no use to its reader unless it says which bound was met
 * and how to raise it, so both belong to the same message. The libraries report the bound
 * in a sentence of their own — they know nothing about a command line — and this is where
 * that sentence is matched back to the switch it belongs to.
 *
 * <p>The match is on the wording the library writes, which is a coupling between two
 * modules that no compiler checks. It is kept honest in two ways: a sentence that matches
 * nothing here is still reported in full and still leaves with {@link ExitCode#LIMIT},
 * with the reader sent to the table of the reference instead of to one switch, and the
 * tests of this module run every bound through the tool and read the switch back out of
 * the message.
 *
 * <p>Whether {@code --limits large} moves a bound is recorded here as well, because a
 * refusal that offers a profile which leaves that bound where it is sends its reader to
 * run the same document a second time for the same answer. Only the bounds the large
 * profile actually raises say so.
 */
enum Bound {

    /**
     * The largest XML input an importer reads, in bytes.
     *
     * <p>It bounds the attachment of a PDF as well, because an attachment is the input of
     * the same importer: a container whose invoice is larger than this reads no further
     * than a file of the same size would.
     */
    INPUT_BYTES("--max-input-bytes", true, "this importer reads at most",
            "this reader reads at most", "and this run was given",
            "so it was cut off and no invoice was read from it", "is past that bound"),

    /**
     * The largest PDF a run opens, in bytes of the file.
     *
     * <p>Three bounds of the container follow it and have no switch of their own: what one
     * container may decode in total, how many objects its object streams may declare
     * together, and how many objects the reader walks on its pages to find the files they
     * refer to, which is the same number. All three are raised by raising this one, so all
     * three are answered here.
     */
    PDF_BYTES("--max-pdf-bytes", true, "reader opens a PDF of at most",
            "bytes this reader decodes while it opens a PDF",
            "objects together for this reader",
            "objects this reader walks on the pages of a PDF"),

    /** How many attachments of one PDF are enumerated at all. */
    ATTACHMENTS("--max-attachments", false, "attachments this reader enumerates"),

    /**
     * How many pages one rendering may have.
     *
     * <p>It is the one bound of this table that is about what a run <em>writes</em> rather
     * than about what it reads. A rendering costs pages, and a document well inside every
     * bound on its input can still be hundreds of them: one value of a megabyte in a
     * narrow column, or an invoice number that stands in the footer of every page.
     */
    RENDER_PAGES("--max-pages", true, "pages this run allows"),

    /**
     * The largest XML output a writer produces, in bytes.
     *
     * <p>It is a bound of its own and not the input one read backwards. A cross industry
     * invoice is about three times the size of the UBL invoice it was converted from, so a
     * conversion held to the bound on its input would refuse documents that are well
     * inside every bound this run was given.
     */
    OUTPUT_BYTES("--max-output-bytes", true,
            "bytes this run was given, so it was not written"),

    /** The largest ESJ document a reader reads, in bytes. */
    DOCUMENT_BYTES("--max-document-bytes", true, "the document exceeds the bound of",
            "the canonical form of the document reached"),

    /** The largest number of members of {@code values}. */
    VALUES("--max-values", true, "values carries more than",
            "values this importer writes within"),

    /** The largest string value, in bytes of its UTF-8 encoding. */
    STRING_BYTES("--max-string-bytes", false, "a string value is longer than",
            "a string inside extensions is longer than", "source.syntax is longer than",
            "a number inside extensions is spelled in more than",
            "the string value of", "a supplementary component of"),

    /** The largest binary value, in bytes of its base64 encoding. */
    BINARY_BYTES("--max-binary-bytes", false, "a binary value is longer than",
            "the decoded binary content of the document", "the binary value of",
            "the binary content of the document would exceed"),

    /** The largest number of segments of one semantic path. */
    PATH_SEGMENTS("--max-path-segments", false, "a semantic path has more than",
            "the XR representation nests elements more than"),

    /** The largest number of nodes inside {@code extensions}. */
    EXTENSION_NODES("--max-extension-nodes", true, "extensions carries more than"),

    /**
     * The characters the streaming reader holds of the one element it has to read whole
     * before it can decide a condition over it.
     */
    BUFFERED_BYTES("--max-buffered-bytes", false, "characters it holds"),

    /** The elements the streaming reader holds of that same element. */
    BUFFERED_ELEMENTS("--max-buffered-elements", false, "elements it holds");

    /**
     * The wordings of the bounds no switch and no profile of this version moves: the
     * length of a semantic path in bytes, the members of one value object, and the depth
     * of the nesting inside {@code extensions}, including the one the JSON parser itself
     * refuses at.
     */
    private static final List<String> FIXED = List.of(
            "a semantic path is longer than", "a value object carries more than",
            "extensions is nested deeper than", "nests deeper than this reader accepts",
            "the XMP packet of this file is larger than",
            "levels deep, which this reader",
            "declares a predictor row of",
            "is filtered with a chain this reader does not decode");

    private final String option;
    private final boolean largeProfile;
    private final List<String> phrases;

    Bound(String option, boolean largeProfile, String... phrases) {
        this.option = option;
        this.largeProfile = largeProfile;
        this.phrases = List.of(phrases);
    }

    /** Returns the switch that raises this bound, as it is written on a command line. */
    String option() {
        return option;
    }

    /**
     * Tells whether the large profile of {@link Bounds} raises this bound.
     *
     * @return {@code true} where {@code --limits large} moves it, {@code false} where
     *         only the switch of this bound does
     */
    boolean raisedByLargeProfile() {
        return largeProfile;
    }

    /**
     * Tells whether a message of one of the libraries is about a bound that no
     * configuration of this version raises.
     *
     * <p>It is asked only where {@link #of(String)} found nothing, and it is what turns
     * the silence there into an answer: a caller at such a bound is deciding between
     * running the document again and passing it on, and the honest advice is that running
     * it again with this version will meet the same bound.
     *
     * @param message the message, as the library wrote it
     * @return {@code true} if the bound it names is one this version holds fixed
     */
    static boolean isFixed(String message) {
        if (message == null) {
            return false;
        }
        return FIXED.stream().anyMatch(message::contains);
    }

    /**
     * Returns the bound a message of one of the libraries is about.
     *
     * @param message the message, as the library wrote it
     * @return the bound, or empty where the message is about a bound with no switch of
     *         its own — the length of a path in bytes, the members of one value object,
     *         the nesting inside {@code extensions} — or about none this enum knows
     */
    static Optional<Bound> of(String message) {
        if (message == null) {
            return Optional.empty();
        }
        for (Bound bound : values()) {
            for (String phrase : bound.phrases) {
                if (message.contains(phrase)) {
                    return Optional.of(bound);
                }
            }
        }
        return Optional.empty();
    }
}
