package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.EsjException;
import de.bsnsoft.esj.bindings.ReaderOptions;
import de.bsnsoft.esj.bindings.WriterOptions;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.pdf.PdfLimits;
import de.bsnsoft.esj.render.RenderOptions;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.Objects;
import java.util.Optional;

/**
 * The resource bounds one run of the tool works within: the limits of the reader, the
 * bound on an XML input, and the bound the tool holds an input to while it reads it.
 *
 * <p>They are one object because they have to agree. A reader that accepts a document of
 * sixty four mebibytes while the tool stops reading at four would refuse half of what it
 * can read, and an importer that reads more XML than the reader will take back turns a
 * successful import into a document nobody can read again. So a run has one configuration,
 * built once from {@code --limits} and the individual switches, and every step is given
 * the part of it that concerns it.
 *
 * <p>What a run may <em>write</em> is a bound of its own, {@link #maxOutputBytes()}. It is
 * not the input bound read backwards: a cross industry invoice is about three times the
 * size of the UBL invoice it was converted from, so a conversion measured against the
 * bound on its input would be refused while every bound of the run still had room.
 *
 * <p>Limits are policy, not conformance (specification, sections 3.1 and 12.2). The same
 * byte sequence is a conformant document for a reader configured differently, and both
 * readers are right; that is why a refusal by one of these bounds leaves with
 * {@link ExitCode#LIMIT} rather than with a verdict about the document, and why every
 * such refusal carries {@link #hint(Bound)}.
 *
 * <p>Two profiles ship. {@link #DEFAULT_PROFILE} is the reference configuration of the
 * specification and stays conservative, because the tool is written for input from
 * strangers. {@link #LARGE_PROFILE} is sized for the invoices that exist at the other end
 * of the scale — tens of megabytes of XML and hundreds of thousands of lines — and is safe
 * to ask for because the heap ceiling of the process, not this table, is what keeps the
 * machine standing: a run of the tool is one process, and {@code -Xmx} is where that
 * ceiling is set.
 *
 * <p>The large profile moves seven bounds and no more: the XML input, the XML output, the
 * PDF container, the document, the number of values, the nodes inside {@code extensions}
 * and the pages of a rendering. The size of one value, the length of a path and the depth of
 * {@code extensions} stay where the reference configuration puts them, because a single
 * enormous value is a different question from a large invoice. {@link Bound#raisedByLargeProfile()} is that list, and it is what keeps
 * {@link #hint(Bound)} from offering a profile that would answer the same way twice.
 */
final class Bounds {

    /** The name of the conservative profile: the reference configuration. */
    static final String DEFAULT_PROFILE = "default";

    /** The name of the profile sized for very large invoices. */
    static final String LARGE_PROFILE = "large";

    /** One mebibyte. */
    private static final long MIB = 1024L * 1024L;

    /** The XML the large profile reads: enough for the class of document it is for. */
    private static final long LARGE_INPUT_BYTES = 256L * MIB;

    /**
     * The XML the large profile writes. It is four times the input bound of that profile,
     * because the two syntaxes are not the same size: the measurements of
     * {@code docs/deployment-measurements.md} put a cross industry invoice at about three
     * times the UBL invoice it came from, and the fourth is the room a bound wants.
     */
    private static final long LARGE_OUTPUT_BYTES = 1024L * MIB;

    /** The ESJ document the large profile reads. */
    private static final long LARGE_DOCUMENT_BYTES = 512L * MIB;

    /** The values the large profile reads: a few hundred thousand lines' worth. */
    private static final int LARGE_VALUES = 8_000_000;

    /** The nodes inside {@code extensions} the large profile reads. */
    private static final int LARGE_EXTENSION_NODES = 8_000_000;

    private static final long LARGE_PDF_BYTES = 512L * MIB;

    /**
     * The pages one rendering may have under the large profile. An invoice of three
     * hundred lines is about eight pages, so this is room for several million lines —
     * far past the hundreds of thousands the profile is sized for, because a bound on
     * what a run writes is there to stop a runaway and not to size a document.
     */
    private static final int LARGE_RENDER_PAGES = 200_000;

    /**
     * How much decoded attachment content one container may produce in total, as a
     * multiple of the bound on one attachment. It is the ratio {@link PdfLimits} runs by
     * default, kept here because the bound on one attachment follows
     * {@code --max-input-bytes} and the total has to follow it.
     */
    private static final int TOTAL_ATTACHMENT_FACTOR = 4;

    /** What a refusal says where no configuration of this version raises the bound. */
    private static final String FIXED_BOUND_HINT =
            "; no profile and no switch of this version raises that bound";

    /** What a refusal says where the bound is not one this tool matched to a switch. */
    private static final String UNKNOWN_BOUND_HINT =
            "; the --limits and --max-… switches of esj --help set the bounds of this run";

    private final String profile;
    private final Limits readerLimits;
    private final long maxInputBytes;
    private final long maxOutputBytes;
    private final long maxBufferedBytes;
    private final int maxBufferedElements;
    private final long maxPdfBytes;
    private final int maxEmbeddedFiles;
    private final int maxRenderPages;

    private Bounds(String profile,
                   Limits readerLimits,
                   long maxInputBytes,
                   long maxOutputBytes,
                   long maxBufferedBytes,
                   int maxBufferedElements,
                   long maxPdfBytes,
                   int maxEmbeddedFiles,
                   int maxRenderPages) {
        this.profile = profile;
        this.readerLimits = readerLimits;
        this.maxInputBytes = maxInputBytes;
        this.maxOutputBytes = maxOutputBytes;
        this.maxBufferedBytes = maxBufferedBytes;
        this.maxBufferedElements = maxBufferedElements;
        this.maxPdfBytes = maxPdfBytes;
        this.maxEmbeddedFiles = maxEmbeddedFiles;
        this.maxRenderPages = maxRenderPages;
    }

    /**
     * Returns the bounds of a named profile.
     *
     * @param name {@link #DEFAULT_PROFILE} or {@link #LARGE_PROFILE}
     * @return the profile
     * @throws CliException if the name is neither
     */
    static Bounds profile(String name) {
        if (DEFAULT_PROFILE.equals(name)) {
            return new Bounds(DEFAULT_PROFILE, Limits.defaults(),
                    XrImporter.DEFAULT_MAX_INPUT_BYTES,
                    WriterOptions.DEFAULT_MAX_OUTPUT_BYTES,
                    ReaderOptions.DEFAULT_MAX_BUFFERED_BYTES,
                    ReaderOptions.DEFAULT_MAX_BUFFERED_ELEMENTS,
                    PdfLimits.DEFAULT_MAX_PDF_BYTES,
                    PdfLimits.DEFAULT_MAX_EMBEDDED_FILES,
                    RenderOptions.DEFAULT_MAX_PAGES);
        }
        if (LARGE_PROFILE.equals(name)) {
            return new Bounds(LARGE_PROFILE, Limits.defaults().toBuilder()
                    .maxDocumentBytes(LARGE_DOCUMENT_BYTES)
                    .maxValues(LARGE_VALUES)
                    .maxExtensionNodes(LARGE_EXTENSION_NODES)
                    .build(), LARGE_INPUT_BYTES, LARGE_OUTPUT_BYTES,
                    ReaderOptions.DEFAULT_MAX_BUFFERED_BYTES,
                    ReaderOptions.DEFAULT_MAX_BUFFERED_ELEMENTS,
                    LARGE_PDF_BYTES, PdfLimits.DEFAULT_MAX_EMBEDDED_FILES,
                    LARGE_RENDER_PAGES);
        }
        throw CliException.input("--limits takes " + DEFAULT_PROFILE + " or " + LARGE_PROFILE
                + ", not '" + name + "'");
    }

    /**
     * Returns the limits to configure a reader, an importer and a validator with.
     *
     * @return the limits of the specification, section 12.2 for this run
     */
    Limits readerLimits() {
        return readerLimits;
    }

    /** Returns the largest XML input this run reads, in bytes. */
    long maxInputBytes() {
        return maxInputBytes;
    }

    /** Returns the largest XML output this run writes, in bytes. */
    long maxOutputBytes() {
        return maxOutputBytes;
    }

    /** Returns the largest ESJ document this run reads, in bytes. */
    long maxDocumentBytes() {
        return readerLimits.maxDocumentBytes();
    }

    /** Returns the largest PDF this run opens, in bytes of the file. */
    long maxPdfBytes() {
        return maxPdfBytes;
    }

    /** Returns how many pages one rendering of this run may have. */
    int maxRenderPages() {
        return maxRenderPages;
    }

    /**
     * Returns what a reader of a PDF container may spend on one file under these bounds.
     *
     * <p>Two of the seven bounds have switches of their own and two follow
     * {@code --max-input-bytes}: an attachment is handed to the XML importer, so the
     * bound on one attachment is the bound that importer reads within, and the bound on
     * all of them together is a multiple of it. Two more follow the bound on the file:
     * what one container may decode, and how many objects its object streams may declare.
     * The XMP packet keeps the bound of the library, which no switch of this tool moves.
     *
     * @return the limits the PDF reader runs under
     */
    PdfLimits pdfLimits() {
        long attachments = total(maxInputBytes);
        return PdfLimits.defaults()
                .withMaxPdfBytes(maxPdfBytes)
                .withMaxEmbeddedFiles(maxEmbeddedFiles)
                .withMaxAttachmentBytes(maxInputBytes)
                .withMaxTotalAttachmentBytes(attachments)
                .withMaxDecodedBytes(decoded(attachments))
                .withMaxObjectStreamObjects(objects());
    }

    /**
     * Returns how many objects the object streams of one container may declare together:
     * the default, scaled with the bound on the file.
     *
     * <p>The two belong together because a file can only declare the objects it has the
     * bytes to declare, so a run configured to open eight times the file opens eight times
     * the invoice and is right to expect eight times the objects. There is no switch of
     * its own, and a refusal by this bound therefore sends its reader to
     * {@code --max-pdf-bytes}, which is the one that moves it.
     */
    private long objects() {
        long ratio = Math.max(1L, maxPdfBytes / PdfLimits.DEFAULT_MAX_PDF_BYTES);
        long room = Long.MAX_VALUE / PdfLimits.DEFAULT_MAX_OBJECT_STREAM_OBJECTS;
        return PdfLimits.DEFAULT_MAX_OBJECT_STREAM_OBJECTS * Math.min(ratio, room);
    }

    /**
     * Returns what one container may decode in total: the bound on the file itself, or
     * what the attachments of one container are allowed to decode to if that is more.
     *
     * <p>The budget counts the streams the library decodes to find the objects of a file
     * and the streams this reader decodes out of it, so a budget below the bounds that
     * govern the second group would refuse an attachment that those bounds allow, and a
     * caller would have met a bound it was never told about.
     */
    private long decoded(long attachments) {
        long withXmp = attachments > Long.MAX_VALUE - PdfLimits.DEFAULT_MAX_XMP_BYTES
                ? Long.MAX_VALUE
                : attachments + PdfLimits.DEFAULT_MAX_XMP_BYTES;
        return Math.max(maxPdfBytes, withXmp);
    }

    private static long total(long perAttachment) {
        return perAttachment > Long.MAX_VALUE / TOTAL_ATTACHMENT_FACTOR
                ? Long.MAX_VALUE
                : perAttachment * TOTAL_ATTACHMENT_FACTOR;
    }

    /**
     * Returns the characters the streaming reader holds of the one element it reads whole
     * before it can decide a condition over it.
     */
    long maxBufferedBytes() {
        return maxBufferedBytes;
    }

    /** Returns the elements it holds of that same element. */
    int maxBufferedElements() {
        return maxBufferedElements;
    }

    /**
     * Returns these bounds with one of them replaced.
     *
     * @param bound the bound to set
     * @param value the value, which every bound takes in the unit of its own switch
     * @return the new configuration
     * @throws CliException if the value is one no configuration can hold — zero or less,
     *                      a count past what an index can address, a document past what
     *                      one byte array can hold
     */
    Bounds with(Bound bound, long value) {
        Objects.requireNonNull(bound, "bound");
        try {
            return switch (bound) {
                case INPUT_BYTES -> new Bounds(profile, readerLimits, positive(bound, value),
                        maxOutputBytes, maxBufferedBytes, maxBufferedElements, maxPdfBytes,
                        maxEmbeddedFiles, maxRenderPages);
                case OUTPUT_BYTES -> new Bounds(profile, readerLimits, maxInputBytes,
                        positive(bound, value), maxBufferedBytes, maxBufferedElements,
                        maxPdfBytes, maxEmbeddedFiles, maxRenderPages);
                case BUFFERED_BYTES -> new Bounds(profile, readerLimits, maxInputBytes,
                        maxOutputBytes, positive(bound, value), maxBufferedElements,
                        maxPdfBytes, maxEmbeddedFiles, maxRenderPages);
                case BUFFERED_ELEMENTS -> new Bounds(profile, readerLimits, maxInputBytes,
                        maxOutputBytes, maxBufferedBytes, count(bound, value), maxPdfBytes,
                        maxEmbeddedFiles, maxRenderPages);
                case PDF_BYTES -> new Bounds(profile, readerLimits, maxInputBytes,
                        maxOutputBytes, maxBufferedBytes, maxBufferedElements,
                        positive(bound, value), maxEmbeddedFiles, maxRenderPages);
                case ATTACHMENTS -> new Bounds(profile, readerLimits, maxInputBytes,
                        maxOutputBytes, maxBufferedBytes, maxBufferedElements, maxPdfBytes,
                        count(bound, value), maxRenderPages);
                case RENDER_PAGES -> new Bounds(profile, readerLimits, maxInputBytes,
                        maxOutputBytes, maxBufferedBytes, maxBufferedElements, maxPdfBytes,
                        maxEmbeddedFiles, count(bound, value));
                case DOCUMENT_BYTES -> limits(readerLimits.toBuilder()
                        .maxDocumentBytes(value).build());
                case VALUES -> limits(readerLimits.toBuilder()
                        .maxValues(count(bound, value)).build());
                case STRING_BYTES -> limits(readerLimits.toBuilder()
                        .maxStringBytes(value).build());
                case BINARY_BYTES -> limits(readerLimits.toBuilder()
                        .maxBinaryValueBytes(value)
                        .maxTotalBinaryBytes(Math.max(value, readerLimits.maxTotalBinaryBytes()))
                        .build());
                case PATH_SEGMENTS -> limits(readerLimits.toBuilder()
                        .maxPathSegments(count(bound, value)).build());
                case EXTENSION_NODES -> limits(readerLimits.toBuilder()
                        .maxExtensionNodes(count(bound, value)).build());
            };
        } catch (EsjException e) {
            // The reader refuses a configuration where it is given rather than when a
            // document arrives, and its sentence says which bound and why; the switch it
            // arrived through is what this module adds to it.
            throw CliException.input(bound.option() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns the sentence that tells a reader of a refusal how to raise what it met.
     *
     * <p>It is written to be appended to a refusal, so it begins with a semicolon and
     * ends without a stop. It names {@code --limits large} only for the bounds that
     * profile actually moves: a reader who runs the same document again under a profile
     * that leaves the bound where it is has paid for the same answer twice, and a hint
     * that costs its reader a second run is worse than no hint.
     *
     * @param bound the bound that was met, or {@code null} where it is not known
     * @return the sentence
     */
    String hint(Bound bound) {
        if (bound == null) {
            return UNKNOWN_BOUND_HINT;
        }
        String text = "; " + bound.option() + " raises that bound";
        if (bound.raisedByLargeProfile() && !LARGE_PROFILE.equals(profile)) {
            text += ", and --limits " + LARGE_PROFILE
                    + " raises it with the other bounds of that profile";
        }
        return text;
    }

    /**
     * Returns the same sentence for a message of one of the libraries rather than for a
     * bound this tool recognized in it.
     *
     * <p>Three cases, and the third is the reason this is not simply {@link #hint(Bound)}
     * of {@link Bound#of(String)}: a bound with a switch, a bound this version holds fixed
     * — the length of a path, the members of a value object, the depth of
     * {@code extensions} — and a message naming neither. Only the first has a remedy, and
     * the second is told so plainly, because a caller at a fixed bound is deciding between
     * running the document again and passing it on and would otherwise retry for nothing.
     */
    private String hintFor(String detail) {
        Optional<Bound> bound = Bound.of(detail);
        if (bound.isPresent()) {
            return hint(bound.orElseThrow());
        }
        return Bound.isFixed(detail) ? FIXED_BOUND_HINT : UNKNOWN_BOUND_HINT;
    }

    /**
     * Returns a refusal by a limit, written as one line.
     *
     * <p>The three parts are always the same: what was refused, what the library said
     * about the bound it met, and how to raise it. The middle part is the library's own
     * sentence rather than a translation of it, because the library is the side that
     * knows the number.
     *
     * @param what   what was refused: the name of an input, normally
     * @param detail what the library said
     * @return the message of a {@link CliException#limit(String)}
     */
    String refusal(String what, String detail) {
        return what + " reached a limit of this run rather than a defect of the document: "
                + detail + hintFor(detail);
    }

    private Bounds limits(Limits replaced) {
        return new Bounds(profile, replaced, maxInputBytes, maxOutputBytes, maxBufferedBytes,
                maxBufferedElements, maxPdfBytes, maxEmbeddedFiles, maxRenderPages);
    }

    private static long positive(Bound bound, long value) {
        if (value <= 0) {
            throw CliException.input(bound.option() + " is a positive number of bytes, not "
                    + value);
        }
        return value;
    }

    private static int count(Bound bound, long value) {
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw CliException.input(bound.option() + " is a count between 1 and "
                    + Integer.MAX_VALUE + ", not " + value);
        }
        return (int) value;
    }
}
