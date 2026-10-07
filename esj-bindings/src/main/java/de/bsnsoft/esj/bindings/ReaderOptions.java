package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.xml.EncodingMode;
import java.util.Objects;

/**
 * What a {@link StreamingReader} is allowed to do and what it writes within.
 *
 * <p>Eight things are settable and each of them answers a different question. The
 * {@linkplain #registry() registry} decides what the reader can represent: a registry
 * without the XRechnung extension turns every extension element into a note rather than
 * into a value, which is the right behaviour for a reader that wants core terms and
 * nothing else. The {@linkplain #limits() limits} are the limits of the reader the
 * documents are written for, and the reader holds to all of them while it writes, so that
 * a reader running them reads back what this one produced. The {@linkplain #mode() mode}
 * says what to do where the document and the semantic model disagree. The
 * {@linkplain #encodingMode() encoding mode} says what to do where the bytes are not
 * written in the encoding the document declares. And the four
 * bounds — {@linkplain #maxInputBytes() input}, {@linkplain #maxElementDepth() nesting}
 * and the two on the buffer, {@linkplain #maxBufferedBytes() characters} and
 * {@linkplain #maxBufferedElements() elements} — are what a document from a stranger is
 * refused on.
 *
 * <p>The defaults are written for input from strangers, in the same spirit as the default
 * limits of the specification, section 12.2: a caller who reads documents of the size
 * this module was built for raises them deliberately, names the profile in the
 * measurement, and knows what it is buying.
 *
 * <p>The registry is the one default that differs between this door and the command line,
 * and the difference is deliberate. Here it is the core registry with the XRechnung
 * extension, because a caller who reaches for this module reads whatever arrives; the
 * tool starts from the core registry alone and takes {@code --extension xrechnung},
 * because it is the process boundary and the smaller model is the conservative default
 * there. Over the conformance corpus that is 34 observations against 44. The two are
 * named side by side in {@code docs/bindings.md} and {@code conformance/readers.md}.
 *
 * <p>Instances are immutable and safe to share between threads; every {@code with}
 * method returns new options.
 */
public final class ReaderOptions {

    /** One mebibyte. */
    private static final long MIB = 1024L * 1024L;

    private final Registry registry;
    private final Limits limits;
    private final ReaderMode mode;
    private final EncodingMode encodingMode;
    private final long maxInputBytes;
    private final int maxElementDepth;
    private final long maxBufferedBytes;
    private final int maxBufferedElements;

    private ReaderOptions(Registry registry, Limits limits, ReaderMode mode,
                          EncodingMode encodingMode, long maxInputBytes, int maxElementDepth,
                          long maxBufferedBytes, int maxBufferedElements) {
        this.registry = registry;
        this.limits = limits;
        this.mode = mode;
        this.encodingMode = encodingMode;
        this.maxInputBytes = maxInputBytes;
        this.maxElementDepth = maxElementDepth;
        this.maxBufferedBytes = maxBufferedBytes;
        this.maxBufferedElements = maxBufferedElements;
    }

    /**
     * Returns the options a reader uses unless it is given others: the core registry with
     * the XRechnung extension, the default limits, {@link ReaderMode#REPAIR},
     * {@link EncodingMode#REPAIR}, and the four bounds each accessor names.
     *
     * @return the defaults
     */
    public static ReaderOptions defaults() {
        return Defaults.INSTANCE;
    }

    /**
     * Returns the registry that decides the structure of the documents the reader builds.
     *
     * <p>The default is the core registry of EN 16931 with the XRechnung extension. The
     * command line starts from the core registry alone; the class documentation says why
     * the two differ.
     *
     * @return the registry
     */
    public Registry registry() {
        return registry;
    }

    /**
     * Returns the limits of the reader the documents are written for.
     *
     * @return the limits, {@link Limits#defaults()} by default
     */
    public Limits limits() {
        return limits;
    }

    /**
     * Returns what the reader does where the document and the semantic model disagree.
     *
     * @return the mode, {@link ReaderMode#REPAIR} by default
     */
    public ReaderMode mode() {
        return mode;
    }

    /**
     * Returns what the reader does with bytes that are not written in the encoding the
     * document declares.
     *
     * <p>The default is {@link EncodingMode#REPAIR}, which recodes them and says so in the
     * report; {@link EncodingMode#STRICT} refuses them instead.
     *
     * @return the encoding mode
     */
    public EncodingMode encodingMode() {
        return encodingMode;
    }

    /**
     * Returns the largest input the reader accepts, in bytes; sixty-four mebibytes by
     * default.
     *
     * <p>This module exists because the cost of reading a document should grow with the
     * document rather than faster than it, and it does: the reader holds one element and
     * the values it has produced, not three trees. The default is therefore an order of
     * magnitude above the bound of the XSLT path and still a bound — the values of a
     * document are held in memory, so bytes that arrive do turn into heap, just at a rate
     * a caller can compute from {@code docs/deployment-measurements.md}.
     *
     * @return the bound
     */
    public long maxInputBytes() {
        return maxInputBytes;
    }

    /**
     * Returns the deepest element nesting the reader walks; 64 by default.
     *
     * <p>Neither syntax nests more than a dozen elements deep, and the sub invoice line of
     * the XRechnung extension is the only element that nests at all without bound. The
     * bound is on elements rather than on semantic paths, because elements are what the
     * reader's own stack counts; the bound on the path is a second one and comes from the
     * limits the reader writes within.
     *
     * @return the bound, in elements
     */
    public int maxElementDepth() {
        return maxElementDepth;
    }

    /**
     * Returns the characters the reader holds in memory to decide a predicate over,
     * counted over the element it holds and everything below it; thirty-three mebibytes
     * by default.
     *
     * <p>This is one of the two bounds that make the memory claim of this module true. A
     * predicate that asks about a child element can be decided only once that element has
     * been read, so the element the predicate stands on is held whole — a document
     * reference, an allowance, a tax subtotal, each a few hundred bytes in practice. No
     * element on the way to an invoice line carries such a predicate in either syntax,
     * which {@code StreamingReaderTest} asserts from the compiled tables rather than from
     * a reading of them, so the buffer never holds a line, let alone a document.
     *
     * <p>The default is nevertheless large, and for one reason: the embedded attachment
     * BT-125 is written inside a document reference, which is exactly such an element, so
     * a bound below the thirty-two mebibytes the default limits admit for one binary value
     * would refuse documents the rest of this project accepts. What keeps a single value
     * from costing the heap is {@link Limits#maxBinaryValueBytes()} and
     * {@link Limits#maxStringBytes()}, which the reader applies while it reads rather than
     * after: an element whose content passes them is dropped with a note about that one
     * term, and neither the buffer nor a frame grows past them.
     *
     * @return the bound
     */
    public long maxBufferedBytes() {
        return maxBufferedBytes;
    }

    /**
     * Returns the elements the reader holds in memory to decide a predicate over, counted
     * over the element it holds and everything below it; one hundred thousand by default.
     *
     * <p>The bound on characters says nothing about empty elements, and an element a
     * predicate stands on may carry as many children as the syntax allows — three million
     * of them cost a gibibyte of heap and not one byte of character content. This is the
     * bound that answers such a document, and it is separate from the byte bound because
     * the two read differently to a caller: one says the content held is too large, the
     * other says the structure is.
     *
     * @return the bound, in elements
     */
    public int maxBufferedElements() {
        return maxBufferedElements;
    }

    /**
     * Returns these options with the registry that decides the structure.
     *
     * @param value the registry
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public ReaderOptions withRegistry(Registry value) {
        return new ReaderOptions(Objects.requireNonNull(value, "registry"), limits, mode,
                encodingMode, maxInputBytes, maxElementDepth, maxBufferedBytes,
                maxBufferedElements);
    }

    /**
     * Returns these options with the limits of the reader the documents are written for.
     *
     * @param value the limits
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public ReaderOptions withLimits(Limits value) {
        return new ReaderOptions(registry, Objects.requireNonNull(value, "limits"), mode,
                encodingMode, maxInputBytes, maxElementDepth, maxBufferedBytes,
                maxBufferedElements);
    }

    /**
     * Returns these options with what the reader does where the document and the semantic
     * model disagree.
     *
     * @param value the mode
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public ReaderOptions withMode(ReaderMode value) {
        return new ReaderOptions(registry, limits, Objects.requireNonNull(value, "mode"),
                encodingMode, maxInputBytes, maxElementDepth, maxBufferedBytes,
                maxBufferedElements);
    }

    /**
     * Returns these options with what the reader does with bytes that are not written in
     * the encoding the document declares.
     *
     * @param value the encoding mode
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public ReaderOptions withEncodingMode(EncodingMode value) {
        return new ReaderOptions(registry, limits, mode,
                Objects.requireNonNull(value, "encodingMode"), maxInputBytes, maxElementDepth,
                maxBufferedBytes, maxBufferedElements);
    }

    /**
     * Returns these options with another bound on the input.
     *
     * @param value the bound in bytes
     * @return the options
     * @throws IllegalArgumentException if the bound is not positive
     */
    public ReaderOptions withMaxInputBytes(long value) {
        return new ReaderOptions(registry, limits, mode, encodingMode,
                positive(value, "an input bound is a positive number of bytes"),
                maxElementDepth, maxBufferedBytes, maxBufferedElements);
    }

    /**
     * Returns these options with another bound on the element nesting.
     *
     * @param value the bound in elements
     * @return the options
     * @throws IllegalArgumentException if the bound is not positive
     */
    public ReaderOptions withMaxElementDepth(int value) {
        return new ReaderOptions(registry, limits, mode, encodingMode, maxInputBytes,
                (int) positive(value, "a nesting bound is a positive number of elements"),
                maxBufferedBytes, maxBufferedElements);
    }

    /**
     * Returns these options with another bound on the characters held to decide a
     * predicate over.
     *
     * @param value the bound in characters, over the held element and everything below it
     * @return the options
     * @throws IllegalArgumentException if the bound is not positive
     */
    public ReaderOptions withMaxBufferedBytes(long value) {
        return new ReaderOptions(registry, limits, mode, encodingMode, maxInputBytes,
                maxElementDepth, positive(value, "a buffer bound is a positive number of bytes"),
                maxBufferedElements);
    }

    /**
     * Returns these options with another bound on the elements held to decide a predicate
     * over.
     *
     * @param value the bound in elements, over the held element and everything below it
     * @return the options
     * @throws IllegalArgumentException if the bound is not positive
     */
    public ReaderOptions withMaxBufferedElements(int value) {
        return new ReaderOptions(registry, limits, mode, encodingMode, maxInputBytes,
                maxElementDepth, maxBufferedBytes,
                (int) positive(value, "a buffer bound is a positive number of elements"));
    }

    /**
     * Tells whether another object is options with the same members.
     *
     * @param other the object to compare with
     * @return {@code true} if every member is equal
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ReaderOptions that
                && registry.equals(that.registry)
                && limits.equals(that.limits)
                && mode == that.mode
                && encodingMode == that.encodingMode
                && maxInputBytes == that.maxInputBytes
                && maxElementDepth == that.maxElementDepth
                && maxBufferedBytes == that.maxBufferedBytes
                && maxBufferedElements == that.maxBufferedElements;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(registry, limits, mode, encodingMode, maxInputBytes,
                maxElementDepth, maxBufferedBytes, maxBufferedElements);
    }

    /**
     * Returns the options as one line, for a message about them.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return "ReaderOptions[mode=" + mode + ", encodingMode=" + encodingMode
                + ", maxInputBytes=" + maxInputBytes
                + ", maxElementDepth=" + maxElementDepth + ", maxBufferedBytes="
                + maxBufferedBytes + ", maxBufferedElements=" + maxBufferedElements
                + "]";
    }

    private static long positive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** Holds the default options, which are built once and shared. */
    private static final class Defaults {

        private static final ReaderOptions INSTANCE = new ReaderOptions(
                Registry.en16931WithXrechnung(), Limits.defaults(), ReaderMode.REPAIR,
                EncodingMode.REPAIR, 64L * MIB, 64, 33L * MIB, 100_000);

        private Defaults() {
            throw new AssertionError("no instances");
        }
    }
}
