package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.xr.XrEncodingMode;
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
 * <p>Instances are immutable and safe to share between threads.
 */
public final class ReaderOptions {

    /**
     * The largest input a reader accepts by default, in bytes: sixty-four mebibytes.
     *
     * <p>This module exists because the cost of reading a document should grow with the
     * document rather than faster than it, and it does: the reader holds one element and
     * the values it has produced, not three trees. The default is therefore an order of
     * magnitude above the bound of the XSLT path and still a bound — the values of a
     * document are held in memory, so bytes that arrive do turn into heap, just at a rate
     * a caller can compute from {@code docs/deployment-measurements.md}.
     */
    public static final long DEFAULT_MAX_INPUT_BYTES = 64L * 1024L * 1024L;

    /**
     * The deepest element nesting a reader walks by default.
     *
     * <p>Neither syntax nests more than a dozen elements deep, and the sub invoice line of
     * the XRechnung extension is the only element that nests at all without bound. The
     * bound is on elements rather than on semantic paths, because elements are what the
     * reader's own stack counts; the bound on the path is a second one and comes from the
     * limits the reader writes within.
     */
    public static final int DEFAULT_MAX_ELEMENT_DEPTH = 64;

    /**
     * The characters a reader holds in memory to decide a predicate over, counted over the
     * element it holds and everything below it: thirty-three mebibytes.
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
     */
    public static final long DEFAULT_MAX_BUFFERED_BYTES = 33L * 1024L * 1024L;

    /**
     * The elements a reader holds in memory to decide a predicate over, counted over the
     * element it holds and everything below it: one hundred thousand.
     *
     * <p>The bound on characters says nothing about empty elements, and an element a
     * predicate stands on may carry as many children as the syntax allows — three million
     * of them cost a gibibyte of heap and not one byte of character content. This is the
     * bound that answers such a document, and it is separate from the byte bound because
     * the two read differently to a caller: one says the content held is too large, the
     * other says the structure is.
     */
    public static final int DEFAULT_MAX_BUFFERED_ELEMENTS = 100_000;

    private final Registry registry;
    private final Limits limits;
    private final ReaderMode mode;
    private final XrEncodingMode encodingMode;
    private final long maxInputBytes;
    private final int maxElementDepth;
    private final long maxBufferedBytes;
    private final int maxBufferedElements;

    private ReaderOptions(Builder builder) {
        this.registry = builder.registry;
        this.limits = builder.limits;
        this.mode = builder.mode;
        this.encodingMode = builder.encodingMode;
        this.maxInputBytes = builder.maxInputBytes;
        this.maxElementDepth = builder.maxElementDepth;
        this.maxBufferedBytes = builder.maxBufferedBytes;
        this.maxBufferedElements = builder.maxBufferedElements;
    }

    /**
     * Returns the options a reader uses unless it is given others.
     *
     * @return the defaults
     */
    public static ReaderOptions defaults() {
        return Defaults.INSTANCE;
    }

    /**
     * Returns a builder that starts from the defaults.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder that starts from these options.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        return new Builder().registry(registry).limits(limits).mode(mode)
                .encodingMode(encodingMode)
                .maxInputBytes(maxInputBytes).maxElementDepth(maxElementDepth)
                .maxBufferedBytes(maxBufferedBytes)
                .maxBufferedElements(maxBufferedElements);
    }

    /**
     * Returns the registry that decides the structure of the documents the reader builds.
     *
     * @return the registry
     */
    public Registry registry() {
        return registry;
    }

    /**
     * Returns the limits of the reader the documents are written for.
     *
     * @return the limits
     */
    public Limits limits() {
        return limits;
    }

    /**
     * Returns what the reader does where the document and the semantic model disagree.
     *
     * @return the mode
     */
    public ReaderMode mode() {
        return mode;
    }

    /**
     * Returns what the reader does with bytes that are not written in the encoding the
     * document declares.
     *
     * @return the encoding mode
     */
    public XrEncodingMode encodingMode() {
        return encodingMode;
    }

    /**
     * Returns the largest input the reader accepts, in bytes.
     *
     * @return the bound
     */
    public long maxInputBytes() {
        return maxInputBytes;
    }

    /**
     * Returns the deepest element nesting the reader walks.
     *
     * @return the bound, in elements
     */
    public int maxElementDepth() {
        return maxElementDepth;
    }

    /**
     * Returns the characters the reader holds in memory to decide a predicate over,
     * counted over the element it holds and everything below it.
     *
     * @return the bound
     */
    public long maxBufferedBytes() {
        return maxBufferedBytes;
    }

    /**
     * Returns the elements the reader holds in memory to decide a predicate over, counted
     * over the element it holds and everything below it.
     *
     * @return the bound, in elements
     */
    public int maxBufferedElements() {
        return maxBufferedElements;
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

    /** Holds the default options, which are built once and shared. */
    private static final class Defaults {

        private static final ReaderOptions INSTANCE = builder().build();

        private Defaults() {
            throw new AssertionError("no instances");
        }
    }

    /** Collects the options of a reader. */
    public static final class Builder {

        private Registry registry = DefaultRegistry.COMBINED;
        private Limits limits = Limits.defaults();
        private ReaderMode mode = ReaderMode.REPAIR;
        private XrEncodingMode encodingMode = XrEncodingMode.REPAIR;
        private long maxInputBytes = DEFAULT_MAX_INPUT_BYTES;
        private int maxElementDepth = DEFAULT_MAX_ELEMENT_DEPTH;
        private long maxBufferedBytes = DEFAULT_MAX_BUFFERED_BYTES;
        private int maxBufferedElements = DEFAULT_MAX_BUFFERED_ELEMENTS;

        private Builder() {
        }

        /**
         * Sets the registry that decides the structure.
         *
         * <p>The default is the core registry of EN 16931 with the XRechnung extension.
         * The command line starts from the core registry alone; the class documentation
         * says why the two differ.
         *
         * @param value the registry
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder registry(Registry value) {
            this.registry = Objects.requireNonNull(value, "registry");
            return this;
        }

        /**
         * Sets the limits of the reader the documents are written for.
         *
         * @param value the limits
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder limits(Limits value) {
            this.limits = Objects.requireNonNull(value, "limits");
            return this;
        }

        /**
         * Sets what the reader does where the document and the semantic model disagree.
         *
         * @param value the mode
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder mode(ReaderMode value) {
            this.mode = Objects.requireNonNull(value, "mode");
            return this;
        }

        /**
         * Sets what the reader does with bytes that are not written in the encoding the
         * document declares.
         *
         * <p>The default is {@link XrEncodingMode#REPAIR}, which recodes them and says so
         * in the report; {@link XrEncodingMode#STRICT} refuses them instead.
         *
         * @param value the encoding mode
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder encodingMode(XrEncodingMode value) {
            this.encodingMode = Objects.requireNonNull(value, "encodingMode");
            return this;
        }

        /**
         * Sets the largest input the reader accepts.
         *
         * @param value the bound in bytes
         * @return this builder
         * @throws IllegalArgumentException if the bound is not positive
         */
        public Builder maxInputBytes(long value) {
            this.maxInputBytes = positive(value, "an input bound is a positive number of bytes");
            return this;
        }

        /**
         * Sets the deepest element nesting the reader walks.
         *
         * @param value the bound in elements
         * @return this builder
         * @throws IllegalArgumentException if the bound is not positive
         */
        public Builder maxElementDepth(int value) {
            this.maxElementDepth =
                    (int) positive(value, "a nesting bound is a positive number of elements");
            return this;
        }

        /**
         * Sets the characters the reader holds in memory to decide a predicate over.
         *
         * @param value the bound in characters, over the held element and everything
         *              below it
         * @return this builder
         * @throws IllegalArgumentException if the bound is not positive
         */
        public Builder maxBufferedBytes(long value) {
            this.maxBufferedBytes =
                    positive(value, "a buffer bound is a positive number of bytes");
            return this;
        }

        /**
         * Sets the elements the reader holds in memory to decide a predicate over.
         *
         * @param value the bound in elements, over the held element and everything below
         *              it
         * @return this builder
         * @throws IllegalArgumentException if the bound is not positive
         */
        public Builder maxBufferedElements(int value) {
            this.maxBufferedElements = (int) positive(value,
                    "a buffer bound is a positive number of elements");
            return this;
        }

        /**
         * Returns the options.
         *
         * @return the options
         */
        public ReaderOptions build() {
            return new ReaderOptions(this);
        }

        private static long positive(long value, String message) {
            if (value <= 0) {
                throw new IllegalArgumentException(message);
            }
            return value;
        }
    }

    /** Holds the combined registry, which is built once and shared. */
    private static final class DefaultRegistry {

        private static final Registry COMBINED =
                Registry.en16931().withExtension(Registry.xrechnungExtension());

        private DefaultRegistry() {
            throw new AssertionError("no instances");
        }
    }
}
