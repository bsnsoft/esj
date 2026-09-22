package de.bsnsoft.esj.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdmodel.common.PDStream;

/**
 * Decodes a PDF stream under a bound that holds while it is being decoded.
 *
 * <p>This exists because a bound applied after a decode is no bound at all. A PDF stream
 * is compressed, and the ratio is chosen by whoever wrote the file: a few kilobytes of
 * Flate expand to gigabytes, and the expansion is the cheapest denial of service a
 * container carries. The library's own {@code createInputStream} runs the whole filter
 * chain into a buffer before it returns, so a caller that reads a window out of that
 * stream has already paid for the whole of it. The stream is therefore taken raw here and
 * the filters are run one at a time into an output stream that stops accepting bytes at
 * the bound: the decoded bytes never exceed it, whatever the file claims and whatever it
 * holds.
 *
 * <p>Reaching the bound on the last filter of the chain is not a failure — it is what a
 * window is for, and the content is returned with {@link AttachmentContent#truncated()}
 * set. Reaching it on an earlier filter is a {@link PdfLimitException}: the output of that
 * stage is the input of the next one, so a stage that was cut off cannot be decoded
 * further and this run has no answer about the stream rather than a partial one.
 *
 * <p>A filter this module cannot run — an unregistered name, one outside the list below,
 * or one whose parameters the library refuses — is a {@link PdfFormatException} and never
 * a reason to fall back to the unbounded path.
 *
 * <p>The list is the second half of the bound, and it is what makes the first half hold.
 * A capped output stream bounds what a filter <em>writes</em>, so the rule a filter has to
 * meet before it is run here is that every allocation it makes is a function of what it
 * has already written. An image decoder does not meet it: it allocates the whole raster
 * from the numbers in the file before it writes a byte, so a stream of a few hundred bytes
 * declaring a raster of forty thousand by forty thousand pixels ends the process no matter
 * what cap it was given. Only the filters that meet the rule are run here, and an
 * electronic invoice is none of the others: it is not a JPEG, not a JPEG 2000, not a fax
 * image and not a JBIG2 image, and neither is an XMP packet.
 *
 * <p>Being on the list is not the whole of the rule, because a filter on it can be handed
 * parameters that break it. Flate and LZW take a predictor, and the decoder allocates two
 * buffers of one predicted row — computed from {@code /Columns}, {@code /Colors} and
 * {@code /BitsPerComponent} — before either of them writes a byte, so the row is checked
 * against {@link #MAX_PREDICTOR_ROW} before the chain is run at all. Reading those numbers
 * the way the library reads them is not enough: they have to be combined the way the
 * library combines them, or in arithmetic that cannot silently differ from it, because a
 * product that is negative in one width is large and positive in another. So the stage is
 * first closed to the values this format defines and the row is then computed exactly as
 * {@code Predictor.calculateRowLength} computes it; with those value sets and a ceiling of
 * one mebibyte the library's {@code int} cannot reach a different number than this guard
 * did. The other three
 * filters of the list were read against the same rule and meet it as they stand: ASCIIHex
 * and ASCII85 write per input group and hold a handful of bytes of state, RunLength writes
 * what the length byte it has already read announced and holds none, and the library runs
 * Crypt as the identity filter. A new name on the list is a reason to read its decoder
 * against the rule again.
 */
final class BoundedStream {

    /** What {@link #measure} answers where this reader does not run the filter chain. */
    static final long NOT_MEASURABLE = -1L;

    /** What {@link #measure} answers where the stream was still producing at the bound. */
    static final long PAST_THE_BOUND = -2L;

    /** The largest array this decoder will build, whatever bound it is given. */
    private static final int MAX_BYTES = Integer.MAX_VALUE - 8;

    /** The first allocation of a bounded buffer, so that a small stream costs little. */
    private static final int INITIAL = 8192;

    /**
     * The widest predicted row this reader decodes: one mebibyte.
     *
     * <p>A predictor is declared in the parameters of a Flate or LZW stage and the decoder
     * allocates two buffers of one row from it before it writes anything, so the row is a
     * cost the file names rather than one it earns, and it is the one allocation of this
     * chain that the bound on the output cannot reach.
     *
     * <p>The number is far above everything a container of an electronic invoice needs and
     * far below what a file can ask for. A cross-reference stream is the only structural
     * stream that carries a predictor at all, and its row is one tag byte plus the three
     * widths of {@code /W}, which is a few dozen bytes; an object stream carries none; an
     * XMP packet and an XML attachment are text and carry none. The widest predicted row a
     * raster could legitimately need — sixty-five thousand samples of four components of
     * sixteen bits — is half of this. A row past it is refused, and the refusal is a
     * statement about this reader and not a verdict on the file.
     */
    private static final long MAX_PREDICTOR_ROW = 1024L * 1024L;

    /**
     * The filters this reader runs: the ones whose decoders write what they have decoded
     * as they decode it, so that the output stream they write into is a bound on what
     * they cost. Both spellings of each name are here, because a stream may use either
     * (PDF 32000-1, 7.4.1). {@code Crypt} belongs here because the library runs it as the
     * identity filter and refuses every other crypt filter by itself.
     */
    private static final Set<COSName> STREAMING_FILTERS = Set.of(
            COSName.FLATE_DECODE, COSName.FLATE_DECODE_ABBREVIATION,
            COSName.LZW_DECODE, COSName.LZW_DECODE_ABBREVIATION,
            COSName.ASCII_HEX_DECODE, COSName.ASCII_HEX_DECODE_ABBREVIATION,
            COSName.ASCII85_DECODE, COSName.ASCII85_DECODE_ABBREVIATION,
            COSName.RUN_LENGTH_DECODE, COSName.RUN_LENGTH_DECODE_ABBREVIATION,
            COSName.CRYPT);

    /**
     * The filters of the list whose parameters may declare a predictor (PDF 32000-1,
     * 7.4.4.4): the two the library wraps in a predicting output stream.
     */
    private static final Set<COSName> PREDICTED_FILTERS = Set.of(
            COSName.FLATE_DECODE, COSName.FLATE_DECODE_ABBREVIATION,
            COSName.LZW_DECODE, COSName.LZW_DECODE_ABBREVIATION);

    /**
     * The predictor algorithms this format defines (PDF 32000-1, table 10): none, the TIFF
     * predictor, and the six PNG ones. A stage declaring any other number is malformed.
     */
    private static final Set<Integer> PREDICTOR_ALGORITHMS = Set.of(1, 2, 10, 11, 12, 13, 14, 15);

    /** The sample widths this format defines for a predicted stage (PDF 32000-1, table 10). */
    private static final Set<Integer> BITS_PER_COMPONENT = Set.of(1, 2, 4, 8, 16);

    private BoundedStream() {
        throw new AssertionError("no instances");
    }

    /**
     * Decodes at most {@code cap} bytes out of a stream.
     *
     * @param stream       the stream to decode
     * @param cap          how many decoded bytes to keep at most
     * @param intermediate how many bytes an earlier filter of a chain may produce; it is
     *                     a bound of this reader and not of the window, because the
     *                     output of one stage is the input of the next
     * @param what         what to call the stream in a message
     * @return the decoded bytes, and whether the stream carried more than the bound
     * @throws PdfLimitException  if a filter that is not the last one reached its bound
     * @throws PdfFormatException if the stream cannot be decoded
     */
    static AttachmentContent decode(PDStream stream,
                                    long cap,
                                    long intermediate,
                                    String what,
                                    DecodeBudget budget) {
        COSStream cos = stream.getCOSObject();
        // The entries the decode reads are resolved before the suspension begins: /Filter
        // and /F, /DecodeParms and /DP — the library reads /F before /Filter and /DP
        // before /DecodeParms, so both spellings of both keys are pulled in — and
        // /Length. Each may be written as an indirect reference, and resolving one inside
        // the window sends the library off to parse another object, possibly decoding the
        // object stream that holds it. This is a narrowing of that window and not the
        // guarantee: the guarantee is that the suspension names this one stream, so every
        // other stream the library touches meanwhile is measured as usual. Whatever had
        // to be read to resolve these was measured like anything else.
        List<COSName> filters = stream.getFilters();
        cos.getDictionaryObject(COSName.F, COSName.FILTER);
        cos.getDictionaryObject(COSName.DECODE_PARMS, COSName.DP);
        cos.getDictionaryObject(COSName.DP, COSName.DECODE_PARMS);
        cos.getDictionaryObject(COSName.LENGTH);
        budget.suspend(cos);
        try {
            AttachmentContent content = run(cos, filters, cap, intermediate, what);
            budget.spend(content.length(), what);
            return content;
        } finally {
            budget.resume(cos);
        }
    }

    /**
     * Decodes the stream, with the budget of the container out of the way: what this
     * decode costs is bounded by {@code cap} and by the bounds of {@link PdfLimits} that
     * belong to the stream, and charging the budget as the bytes are made would bound the
     * same decode twice and refuse the second time.
     */
    private static AttachmentContent run(COSStream cos,
                                         List<COSName> filters,
                                         long cap,
                                         long intermediate,
                                         String what) {
        int bounded = (int) Math.min(Math.max(cap, 0), MAX_BYTES);
        int between = (int) Math.min(Math.max(Math.max(intermediate, cap), 0), MAX_BYTES);
        try (InputStream raw = cos.createRawInputStream()) {
            if (filters == null || filters.isEmpty()) {
                return read(raw, bounded);
            }
            InputStream stage = raw;
            Cap out = null;
            for (int i = 0; i < filters.size(); i++) {
                boolean last = i == filters.size() - 1;
                out = new Cap(last ? bounded : between);
                guardPredictor(filters.get(i), cos, i, what);
                run(filter(filters.get(i), what), stage, out, cos, i, what);
                if (!last && out.full()) {
                    throw new PdfLimitException(what + " decodes to more than"
                            + " the " + between + " bytes this reader holds after its "
                            + ordinal(i) + " filter, and the stages after it were not run");
                }
                if (!last) {
                    stage = new ByteArrayInputStream(out.bytes());
                }
            }
            return new AttachmentContent(out.bytes(), out.full());
        } catch (IOException e) {
            throw new PdfFormatException(what + " could not be decoded", e);
        }
    }

    /**
     * Measures how many bytes a raw stream decodes to, without keeping what it produced.
     *
     * <p>This is the question the parser of this module asks of the streams the library
     * decodes for itself, and the whole of the bound in front of it. Only the number is
     * wanted, so the last stage of the filter chain writes into a counter rather than
     * into a buffer: a stream that would fill the heap is measured for the price of the
     * time it takes to inflate the first {@code cap} bytes of it and not a byte more.
     *
     * <p>The filter parameters are the stream's own dictionary, which is the dictionary
     * the library passes to the same decoders, so a stream whose {@code /DecodeParms}
     * change what a decoder does is measured under the rules it will be decoded under.
     *
     * @param raw        the bytes of the stream as they stand in the file
     * @param parameters the stream dictionary, for {@code /DecodeParms}
     * @param filters    the filters the stream declares, in the order they are to be run
     * @param cap        how many decoded bytes are left
     * @return the number of bytes the stream decodes to, or {@link #PAST_THE_BOUND} where
     *         it was still producing them at {@code cap}, or {@link #NOT_MEASURABLE}
     *         where this reader does not run the chain the stream declares
     * @throws PdfLimitException if a stage declares a predictor row wider than
     *                           {@link #MAX_PREDICTOR_ROW}, which is an allocation made
     *                           before the decoding begins and therefore refused before it
     */
    static long measure(InputStream raw,
                        COSDictionary parameters,
                        List<COSName> filters,
                        long cap) {
        int bounded = (int) Math.min(Math.max(cap, 0), MAX_BYTES);
        String what = "a stream of this file";
        List<Filter> decoders = new ArrayList<>(filters.size());
        for (int i = 0; i < filters.size(); i++) {
            COSName name = filters.get(i);
            if (name == null || !STREAMING_FILTERS.contains(name)) {
                return NOT_MEASURABLE;
            }
            // Before a byte is read: the one allocation of this chain that the counter
            // below cannot bound is made from the parameters, so it is refused from them.
            guardPredictor(name, parameters, i, what);
            try {
                decoders.add(FilterFactory.INSTANCE.getFilter(name));
            } catch (IOException e) {
                // The library has no decoder of that name, so neither has this reader.
                return NOT_MEASURABLE;
            }
        }
        InputStream stage = raw;
        for (int i = 0; i < decoders.size(); i++) {
            boolean last = i == decoders.size() - 1;
            Bounded out = last ? new Counter(bounded) : new Cap(bounded);
            try {
                run(decoders.get(i), stage, out, parameters, i, what);
            } catch (IOException | PdfException e) {
                // A stage that failed produced what it produced, and the library's own
                // decoder fails on the same bytes: what the last stage wrote is the
                // measurement. A failure earlier in a chain is different, because the
                // stages after it were never run here and would be run there, on bytes
                // this reader has not accounted for; that is not a measurement.
                return last ? ((Counter) out).written() : NOT_MEASURABLE;
            }
            if (out.full()) {
                return PAST_THE_BOUND;
            }
            if (last) {
                return ((Counter) out).written();
            }
            stage = new ByteArrayInputStream(((Cap) out).bytes());
        }
        return 0;
    }

    /**
     * Runs one filter, and treats the bound of the output as the end of the input rather
     * than as a failure: an output stream that stops accepting bytes is how the decoding
     * is stopped, and the exception it raises to do so is this class's own signal.
     */
    private static void run(Filter filter,
                            InputStream in,
                            Bounded out,
                            COSDictionary parameters,
                            int index,
                            String what) throws IOException {
        try {
            filter.decode(in, out, parameters, index);
        } catch (CapReached signal) {
            // The bound was met; what fits is in the output stream.
        } catch (IOException e) {
            if (out.full()) {
                // A filter that swallowed the signal and failed on the next write.
                return;
            }
            throw new PdfFormatException(what + " could not be decoded", e);
        } catch (RuntimeException e) {
            throw new PdfFormatException(what + " could not be decoded", e);
        }
    }

    /**
     * Refuses a filter stage whose predictor declares a row this reader will not allocate.
     *
     * <p>This is the one place where a bound is read out of the file rather than measured
     * while it is spent, and it is here because the allocation it bounds happens before
     * the decoder writes anything: the library computes the row length from the parameters
     * and allocates two buffers of it in the constructor of its predicting output stream,
     * guarded only against a negative result.
     *
     * <p>The stage is first closed to the values this format defines: {@code /Predictor}
     * is 1 or 2 or one of 10 to 15, {@code /Colors} and {@code /Columns} are at least one,
     * and {@code /BitsPerComponent} is 1, 2, 4, 8 or 16 (PDF 32000-1, table 10). Anything
     * else is a malformed stream and is refused before a multiplication happens, because
     * the library's behaviour there is undefined rather than safe. The row is then
     * computed from the accepted values with {@link Math#multiplyExact} in the order and
     * the width {@code Predictor.calculateRowLength} uses — {@code /Colors} by
     * {@code /BitsPerComponent}, that by {@code /Columns}, with the same ceiling of
     * thirty-two on {@code /Colors} — and an {@link ArithmeticException} is a refusal by
     * the ceiling, because a row that does not fit the arithmetic is wider than the
     * ceiling whatever the ceiling is.
     *
     * <p>Those two steps are what makes the guard and the library agree. With all three
     * numbers positive and inside their sets, the widest row this method accepts is the
     * one-mebibyte ceiling, which is far below {@code 2^31}, so the product the library
     * computes in {@code int} is the product computed here and cannot wrap into a
     * different number; every other combination is refused here before the library sees
     * it. Reading the file's numbers the way the library reads them is not enough on its
     * own — they have to be combined the way the library combines them, or in arithmetic
     * that cannot silently differ from it.
     *
     * @param filter     the name of the stage
     * @param parameters the stream dictionary the stage's parameters are read out of
     * @param index      the position of the stage in the chain
     * @param what       what to call the stream in a message
     * @throws PdfLimitException  if the row is wider than {@link #MAX_PREDICTOR_ROW}
     * @throws PdfFormatException if the stage declares values this format does not define
     */
    private static void guardPredictor(COSName filter,
                                       COSDictionary parameters,
                                       int index,
                                       String what) {
        if (filter == null || !PREDICTED_FILTERS.contains(filter)) {
            return;
        }
        COSDictionary parms = decodeParameters(parameters, index);
        if (parms == null) {
            return;
        }
        int predictor = parms.getInt(COSName.PREDICTOR);
        if (predictor <= 1) {
            // No predictor is declared, so the decoder wraps nothing and allocates
            // nothing: an absent entry reads as -1 and /Predictor 1 means "none".
            return;
        }
        int colors = parms.getInt(COSName.COLORS, 1);
        int bits = parms.getInt(COSName.BITS_PER_COMPONENT, 8);
        int columns = parms.getInt(COSName.COLUMNS, 1);
        if (!PREDICTOR_ALGORITHMS.contains(predictor) || colors < 1 || columns < 1
                || !BITS_PER_COMPONENT.contains(bits)) {
            throw new PdfFormatException(what + " declares /Predictor " + predictor
                    + ", /Colors " + colors + ", /BitsPerComponent " + bits
                    + " and /Columns " + columns + ", which is no predictor stage this"
                    + " format defines (PDF 32000-1, table 10)");
        }
        int row;
        try {
            // The same numbers, combined in the same order and the same width as
            // Predictor.calculateRowLength combines them, so that a product this reader
            // accepts is the product the library computes.
            row = Math.addExact(Math.multiplyExact(columns,
                    Math.multiplyExact(Math.min(colors, 32), bits)), 7) / 8;
        } catch (ArithmeticException e) {
            // A row that does not fit the arithmetic is wider than any ceiling, and the
            // library would compute a negative length out of the same numbers and refuse
            // the stream itself. It is a bound of this reader and not a defect.
            throw new PdfLimitException(what + " declares a predictor row of /Columns "
                    + columns + " by /Colors " + colors + " by /BitsPerComponent " + bits
                    + ", which is wider than this format can express, and this reader"
                    + " decodes a predicted stream whose rows are at most "
                    + MAX_PREDICTOR_ROW + " bytes wide");
        }
        if (row > MAX_PREDICTOR_ROW) {
            throw new PdfLimitException(what + " declares a predictor row of " + row
                    + " bytes, and this reader decodes a predicted stream whose rows are at"
                    + " most " + MAX_PREDICTOR_ROW + " bytes wide");
        }
    }

    /**
     * Returns the parameters of one stage of a filter chain, read the way the library
     * reads them (PDF 32000-1, 7.4.1): one dictionary belongs to a single filter name, and
     * an array of dictionaries is indexed alongside the array of filter names.
     */
    private static COSDictionary decodeParameters(COSDictionary dictionary, int index) {
        COSBase filter = dictionary.getDictionaryObject(COSName.F, COSName.FILTER);
        COSBase parms = dictionary.getDictionaryObject(COSName.DP, COSName.DECODE_PARMS);
        if (filter instanceof COSName && parms instanceof COSDictionary single) {
            return single;
        }
        if (filter instanceof COSArray && parms instanceof COSArray array
                && index < array.size()
                && array.getObject(index) instanceof COSDictionary stage) {
            return stage;
        }
        return null;
    }

    /**
     * Returns the filter of a name, or says that this reader does not run it.
     *
     * <p>A name outside {@link #STREAMING_FILTERS} is refused here rather than run,
     * whether or not the library has a decoder for it; see the note on the class.
     */
    private static Filter filter(COSName name, String what) {
        if (name == null || !STREAMING_FILTERS.contains(name)) {
            throw new PdfFormatException(what + " is filtered with "
                    + Messages.quoted(name == null ? "" : name.getName()) + ", which this"
                    + " reader does not decode");
        }
        try {
            return FilterFactory.INSTANCE.getFilter(name);
        } catch (IOException e) {
            throw new PdfFormatException(what + " is filtered with "
                    + Messages.quoted(name == null ? "" : name.getName()) + ", which this"
                    + " reader does not decode", e);
        }
    }

    /** Reads a bound number of bytes, and one more to find out whether there were more. */
    private static AttachmentContent read(InputStream in, int cap) throws IOException {
        byte[] bytes = in.readNBytes(cap);
        return new AttachmentContent(bytes, bytes.length == cap && in.read() >= 0);
    }

    private static String ordinal(int index) {
        return switch (index) {
            case 0 -> "first";
            case 1 -> "second";
            case 2 -> "third";
            default -> (index + 1) + "th";
        };
    }

    /** An output stream that stops the writer at a bound and says that it did. */
    private abstract static class Bounded extends OutputStream {

        /** Tells whether a byte past the bound arrived. */
        abstract boolean full();
    }

    /**
     * An output stream that holds a bound number of bytes and stops the writer at the
     * first byte past it.
     */
    private static final class Cap extends Bounded {

        private final int cap;
        private final ByteArrayOutputStream collected;
        private boolean full;

        Cap(int cap) {
            this.cap = cap;
            this.collected = new ByteArrayOutputStream(Math.min(cap, INITIAL));
        }

        @Override
        public void write(int b) throws IOException {
            room(1);
            collected.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int count) throws IOException {
            int room = room(count);
            collected.write(bytes, offset, room);
            if (room < count) {
                throw new CapReached();
            }
        }

        /**
         * Returns how many of the bytes offered still fit, and stops the writer where
         * none of them do.
         */
        private int room(int count) throws IOException {
            if (full) {
                throw new CapReached();
            }
            int room = cap - collected.size();
            if (count > room) {
                full = true;
                if (room == 0) {
                    throw new CapReached();
                }
            }
            return Math.min(count, room);
        }

        @Override
        boolean full() {
            return full;
        }

        /** Returns what fitted. */
        byte[] bytes() {
            return collected.toByteArray();
        }
    }

    /**
     * An output stream that counts what is written into it and stops the writer at the
     * bound, keeping nothing.
     *
     * <p>It answers the one question {@link #measure} asks, and it answers it for the
     * price of the decoding alone: a stream that would fill the heap is measured without a
     * byte of it being kept.
     */
    private static final class Counter extends Bounded {

        private final int cap;
        private long written;
        private boolean full;

        Counter(int cap) {
            this.cap = cap;
        }

        @Override
        public void write(int b) throws IOException {
            room(1);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int count) throws IOException {
            int room = room(count);
            written += room;
            if (room < count) {
                throw new CapReached();
            }
        }

        private int room(int count) throws IOException {
            if (full) {
                throw new CapReached();
            }
            long room = cap - written;
            if (count > room) {
                full = true;
                if (room == 0) {
                    throw new CapReached();
                }
            }
            return (int) Math.min(count, room);
        }

        @Override
        boolean full() {
            return full;
        }

        /** Returns how many bytes were written, which is at most the bound. */
        long written() {
            return written;
        }
    }

    /** The signal a bounded output stream raises to stop the filter that writes into it. */
    private static final class CapReached extends IOException {

        private static final long serialVersionUID = 1L;

        CapReached() {
            super("the bound of this reader was reached");
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            // It is a control signal caught a few frames up and never reported.
            return this;
        }
    }
}
