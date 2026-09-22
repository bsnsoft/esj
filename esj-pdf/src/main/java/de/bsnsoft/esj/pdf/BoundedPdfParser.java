package de.bsnsoft.esj.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessInputStream;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadView;
import org.apache.pdfbox.io.RandomAccessStreamCache.StreamCacheCreateFunction;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * The parser this module opens a PDF with: the library's own, with a budget under the
 * streams it decodes for itself.
 *
 * <p>The library finds the objects of a file through object streams and cross-reference
 * streams, and it decodes those while the file is being opened — before this module has
 * seen an attachment, and through a path no caller can reach from the outside. It decodes
 * them into a buffer that grows until the stream ends, so a container of a few hundred
 * kilobytes ends the process at any heap unless the bytes are counted as they are made.
 *
 * <p>Counting them where they are made needs the extent of each stream and the filters it
 * declares, and reading either of those out of the bytes of the file with a scan of this
 * module's own is a bound that holds only where the scan agrees with the library's lexer.
 * It does not have to: the library hands both over. Every stream of a file, however the
 * parser found it — through the cross-reference table, through a cross-reference stream,
 * or through the brute-force search that runs when those are broken — is built in one
 * place, where the dictionary it was parsed from meets the extent of its bytes, and the
 * view it will be read through is made there. The view returned here is therefore a view
 * that knows its own dictionary, and the first time anything reads it — which is the
 * moment the library begins to decode the stream, and no earlier, so a stream that is
 * never decoded costs nothing — it measures what the stream decodes to and charges it to
 * the budget of the container.
 *
 * <p>A stream whose filter chain this reader does not run is refused rather than handed
 * over unmeasured. Nothing here ever decrypts: no password is taken and no security
 * handler is built, so a file that declares encryption is refused wherever the parse
 * meets the declaration — in {@link MeasuringDocument} where the trailer carries
 * {@code /Encrypt}, in {@link #dereferenceCOSObject} where the parse reads an encryption
 * dictionary whose trailer entry a rebuild lost, and in
 * {@link #parseObjectStreamObject} where an object stream yields one. Where a damaged
 * file hides the declaration from all three, the streams of that file are never
 * decrypted either: they stay ciphertext, and an attachment of ciphertext decodes to
 * nothing and is reported as unreadable.
 *
 * <p>What is left outside the budget is the library's object
 * model: a file inside the byte bound whose objects cost more to hold than its bytes cost
 * to decode. The memory ceiling of the process is the answer to that, and
 * {@code docs/deployment.md} says so.
 */
final class BoundedPdfParser extends PDFParser {

    private final RandomAccessRead source;
    private final DecodeBudget budget;
    private final MeasuringDocument measuring;

    private BoundedPdfParser(RandomAccessRead source,
                             DecodeBudget budget,
                             StreamCacheCreateFunction cache) throws IOException {
        super(source, "", null, null, cache);
        this.source = source;
        this.budget = budget;
        this.measuring = new MeasuringDocument(cache, this);
        this.document = measuring;
    }

    /**
     * Opens a PDF with the library, under a budget of decoded bytes.
     *
     * <p>The stream cache the library is given is memory only: a library does not get to
     * write the content of somebody's invoice into a temporary directory.
     *
     * @param source the bytes of the file
     * @param budget the budget of the container
     * @return the document, which the caller closes
     * @throws IOException        if the file cannot be read as a PDF
     * @throws PdfLimitException  if the budget is spent while the file is opened
     * @throws PdfAccessException if the file is encrypted
     */
    static PDDocument load(RandomAccessRead source, DecodeBudget budget) throws IOException {
        return new BoundedPdfParser(source, budget, IOUtils.createMemoryOnlyStreamCache())
                .parse();
    }

    /**
     * Returns the object an indirect reference names, and refuses the file where that
     * object is an encryption dictionary.
     *
     * <p>This is the second half of the refusal of an encrypted file, and it is here
     * because the first half depends on a trailer. {@code COSDocument} answers the
     * question from {@code trailer.getCOSDictionary(/Encrypt)}, and where
     * {@code startxref} is unusable the trailer is rebuilt: the brute-force parser copies
     * {@code /Encrypt} only where it found both a catalog and an info dictionary, and its
     * fallback drops the entry altogether. A file that declares encryption, has a broken
     * {@code startxref} and carries no {@code /Info} would therefore reach the reader with
     * an empty trailer and be read as an ordinary container.
     *
     * <p>Every object of such a file that lies in the body of the file is nevertheless
     * parsed — the fallback dereferences all of them looking for the catalog — and every
     * dereference of this parse passes here. What is refused is exactly this: an object
     * that is a dictionary and not a stream, whose {@code /Filter} is a name, and which
     * carries {@code /V} or {@code /R} as a number, which is the shape of the encryption
     * dictionary of PDF 32000-1, 7.6.2 and of no other object of a file. The three
     * entries are read through the references they may be written as, because a value
     * behind a reference is the same value: a file that writes {@code /V 13 0 R} declares
     * the same version as one that writes {@code /V 4}.
     *
     * <p>An object that lies inside an object stream reaches this parse through
     * {@link #parseObjectStreamObject} instead, and is tested there.
     */
    @Override
    public COSBase dereferenceCOSObject(COSObject object) throws IOException {
        COSBase dereferenced = super.dereferenceCOSObject(object);
        if (isEncryptionDictionary(dereferenced)) {
            throw encrypted();
        }
        return dereferenced;
    }

    /**
     * Returns an object an object stream carries, and refuses the file where that object
     * is an encryption dictionary.
     *
     * <p>An object inside an object stream is not dereferenced the way an object of the
     * file body is: the stream is decompressed once and every object it held is handed
     * out from here, so this is the one place all of them pass. It is the same test as in
     * {@link #dereferenceCOSObject}, for the same reason, on the objects that route does
     * not see.
     */
    @Override
    protected COSBase parseObjectStreamObject(long objectStreamNumber, COSObjectKey key)
            throws IOException {
        COSBase yielded = super.parseObjectStreamObject(objectStreamNumber, key);
        if (isEncryptionDictionary(yielded)) {
            throw encrypted();
        }
        return yielded;
    }

    /**
     * Tells whether an object presents itself as an encryption dictionary (PDF 32000-1,
     * 7.6.2): a dictionary, not a stream, with a handler name in {@code /Filter} and the
     * version or the revision of a security handler beside it.
     *
     * <p>Each of the three entries is resolved through an indirect reference where the
     * file writes one, so that the shape is recognized however the file spells it.
     */
    private static boolean isEncryptionDictionary(COSBase object) {
        if (!(object instanceof COSDictionary dictionary) || object instanceof COSStream) {
            return false;
        }
        if (!(dictionary.getDictionaryObject(COSName.FILTER) instanceof COSName)) {
            return false;
        }
        return dictionary.getDictionaryObject(COSName.V) instanceof COSNumber
                || dictionary.getDictionaryObject(COSName.R) instanceof COSNumber;
    }

    /** The one refusal of an encrypted file, wherever this module notices it. */
    private static PdfAccessException encrypted() {
        return new PdfAccessException("the file is encrypted, and an encrypted file"
                + " is not a container of an electronic invoice: PDF/A forbids"
                + " encryption, so a hybrid invoice carries none. This reader takes"
                + " no password and decrypts nothing, including a file that would"
                + " open with the empty user password");
    }

    /**
     * Returns the view the library reads a stream's raw bytes through, measured against
     * the budget of this container.
     */
    @Override
    public RandomAccessReadView createRandomAccessReadView(long startPosition, long streamLength)
            throws IOException {
        COSDictionary dictionary = measuring == null ? null : measuring.pending;
        if (dictionary == null || budget == null) {
            return super.createRandomAccessReadView(startPosition, streamLength);
        }
        MeasuredView view = new MeasuredView(source, startPosition, streamLength, dictionary,
                budget);
        measuring.pendingView = view;
        return view;
    }

    /**
     * The document of this parse, which is where every stream of the file is built.
     *
     * <p>The library builds a stream in one place whichever way it found it, and that
     * place is the only one that has both the dictionary the stream was parsed from and
     * the extent of its bytes. The dictionary is held here for the moment in between, in
     * which the view of those bytes is made, and the stream that comes out of it is handed
     * back to the view, so that the view can be named later by the object the rest of this
     * module holds it by.
     *
     * <p>This is also where an encrypted file the library itself noticed is refused. The
     * library asks the document for the encryption dictionary at one point, in
     * {@code COSParser.prepareDecryption}, and that is the point before which nothing has
     * been decrypted: the security handler that decrypts a stream is built there and
     * nowhere else. Refusing here therefore refuses before the first byte of plaintext
     * exists, whichever of the two parsers of the library asked — they share this
     * document. It answers the question from the trailer, though, and a rebuilt trailer
     * can lose the entry; the encryption dictionary as an object is refused in
     * {@link BoundedPdfParser#dereferenceCOSObject}.
     */
    private static final class MeasuringDocument extends COSDocument {

        private COSDictionary pending;
        private MeasuredView pendingView;

        MeasuringDocument(StreamCacheCreateFunction cache, BoundedPdfParser parser) {
            super(cache, parser);
        }

        @Override
        public COSStream createCOSStream(COSDictionary dictionary,
                                         long startPosition,
                                         long streamLength) throws IOException {
            COSDictionary outer = pending;
            MeasuredView outerView = pendingView;
            pending = dictionary;
            pendingView = null;
            try {
                COSStream stream = super.createCOSStream(dictionary, startPosition,
                        streamLength);
                if (pendingView != null) {
                    pendingView.owner(stream);
                }
                return stream;
            } finally {
                pending = outer;
                pendingView = outerView;
            }
        }

        @Override
        public COSDictionary getEncryptionDictionary() {
            if (super.getEncryptionDictionary() != null) {
                throw encrypted();
            }
            return null;
        }
    }

    /**
     * A view of one stream of the file that measures what the stream decodes to before it
     * lets anything read it.
     */
    private static final class MeasuredView extends RandomAccessReadView {

        private final COSDictionary dictionary;
        private final DecodeBudget budget;
        private COSStream owner;
        private boolean measured;
        private boolean measuring;

        MeasuredView(RandomAccessRead source,
                     long startPosition,
                     long streamLength,
                     COSDictionary dictionary,
                     DecodeBudget budget) throws IOException {
            super(source, startPosition, streamLength);
            this.dictionary = dictionary;
            this.budget = budget;
        }

        /** Records the stream this view belongs to, which is how a suspension names it. */
        void owner(COSStream stream) {
            this.owner = stream;
        }

        @Override
        public int read() throws IOException {
            measure();
            return super.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int count) throws IOException {
            measure();
            return super.read(bytes, offset, count);
        }

        @Override
        public void seek(long position) throws IOException {
            measure();
            super.seek(position);
        }

        /**
         * Measures the stream once, the first time anything outside this class reads it.
         *
         * <p>The measurement reads the stream itself, so it runs with the flag that keeps
         * it from measuring again; and it is skipped for the one stream this module is
         * decoding under a bound of its own, which charges what it produced afterwards.
         * Every other stream is measured even while that decode is running, because the
         * entries of a stream dictionary may be indirect references and resolving one
         * sends the library off to read whatever object they name.
         */
        private void measure() throws IOException {
            if (budget == null || measured || measuring || budget.suspendedFor(owner)) {
                return;
            }
            measuring = true;
            long position = getPosition();
            try {
                List<COSName> filters = filters(dictionary);
                long produced = filters == null
                        ? BoundedStream.NOT_MEASURABLE
                        : decodedLength(filters);
                measured = true;
                if (produced == BoundedStream.PAST_THE_BOUND) {
                    throw budget.past(name());
                }
                if (produced == BoundedStream.NOT_MEASURABLE) {
                    throw budget.unmeasurable(name());
                }
                budget.spend(produced, name());
                objects();
            } finally {
                super.seek(position);
                measuring = false;
            }
        }

        /**
         * Charges the objects an object stream declares, which the dictionary states in
         * {@code /N} and the measurement therefore has at no cost.
         */
        private void objects() {
            if (COSName.OBJ_STM.equals(dictionary.getDictionaryObject(COSName.TYPE))) {
                budget.spendObjects(dictionary.getLong(COSName.N, 0L), name());
            }
        }

        private long decodedLength(List<COSName> filters) throws IOException {
            super.seek(0);
            if (filters.isEmpty()) {
                return length();
            }
            return BoundedStream.measure(new RandomAccessInputStream(this), dictionary,
                    filters, budget.remaining());
        }

        /** Names the stream as the file itself declares it, for a message. */
        private String name() {
            COSBase type = dictionary.getDictionaryObject(COSName.TYPE);
            if (COSName.OBJ_STM.equals(type)) {
                return "an object stream of this file";
            }
            if (COSName.XREF.equals(type)) {
                return "a cross-reference stream of this file";
            }
            return "a stream of this file";
        }

        /**
         * Returns the filters of a stream dictionary in the order they are to be run, or
         * {@code null} where the entry is not a filter chain this module can read.
         */
        private static List<COSName> filters(COSDictionary dictionary) {
            COSBase base = dictionary.getDictionaryObject(COSName.FILTER);
            if (base == null) {
                return List.of();
            }
            if (base instanceof COSName name) {
                return List.of(name);
            }
            if (!(base instanceof COSArray array)) {
                return null;
            }
            List<COSName> filters = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                if (!(array.getObject(i) instanceof COSName name)) {
                    return null;
                }
                filters.add(name);
            }
            return filters;
        }
    }
}
