package de.bsnsoft.esj.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * One budget of decoded bytes for one container.
 *
 * <p>A PDF stream is compressed, and the ratio is chosen by whoever wrote the file. Some
 * of those streams are decoded by the library rather than by this module: the object
 * streams that hold the objects of a file, and the cross-reference streams that find
 * them. They are decoded while the file is being opened, before this module has seen an
 * attachment or anything else, and the library decodes them into a buffer of its own that
 * grows until the stream ends. A few hundred kilobytes therefore end the process, at any
 * heap, unless something counts the bytes as they are produced.
 *
 * <p>This counts them. Every stream the library is about to decode passes through a view
 * of this module first (see {@code BoundedPdfParser}), which measures the stream against
 * what is left of the budget before the library decodes it. A stream that would take the
 * budget past its bound raises {@link PdfLimitException}, which is a statement about this
 * reader's configuration and never a verdict on the file.
 *
 * <p>The budget is suspended for the one stream this module is decoding itself — an
 * attachment, the XMP packet — because that decode is bounded where it stands, by
 * {@link BoundedStream} and the bounds of {@link PdfLimits} that belong to it, and what
 * it produced is charged afterwards, so that one container has one number for everything
 * that was decoded out of it. The suspension names that stream and no other: a suspension
 * that named the container would leave every other stream of the file unmeasured for as
 * long as this module was busy, and a dictionary entry written as an indirect reference
 * is enough to reach one from inside that window.
 *
 * <p>The second number here is the objects an object stream declares. The bytes of such a
 * stream bound the bytes it decodes to and nothing else: three million empty strings are
 * a few dozen bytes each once the library has built them, and well inside a byte budget
 * that a file of thirteen megabytes can afford. The count is read from {@code /N} of the
 * stream dictionary, which is already in hand at the moment of the measurement.
 *
 * <p>Instances are not safe to share between threads, and neither is the container that
 * holds one.
 */
final class DecodeBudget {

    private final long limit;
    private final long objectLimit;
    private final List<Object> suspended = new ArrayList<>(2);
    private long spent;
    private long objects;

    /**
     * Creates a budget.
     *
     * @param limit       how many decoded bytes one container may produce
     * @param objectLimit how many objects the object streams of one container may declare
     *                    together
     */
    DecodeBudget(long limit, long objectLimit) {
        this.limit = limit;
        this.objectLimit = objectLimit;
    }

    /** Returns how many decoded bytes are still within the bound. */
    long remaining() {
        return Math.max(0L, limit - spent);
    }

    /**
     * Tells whether this module is decoding exactly this stream at the moment.
     *
     * @param stream the stream a measurement is about
     * @return {@code true} where the suspension names it
     */
    boolean suspendedFor(Object stream) {
        for (Object held : suspended) {
            if (held == stream) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stops the budget from measuring one stream, which this module is about to decode
     * itself.
     *
     * @param stream the stream, named by identity
     */
    void suspend(Object stream) {
        suspended.add(stream);
    }

    /**
     * Ends what {@link #suspend(Object)} began.
     *
     * @param stream the stream that was named
     */
    void resume(Object stream) {
        for (int i = suspended.size() - 1; i >= 0; i--) {
            if (suspended.get(i) == stream) {
                suspended.remove(i);
                return;
            }
        }
    }

    /**
     * Charges decoded bytes against the budget.
     *
     * @param bytes how many bytes were produced
     * @param what  what to call the stream in a message
     * @throws PdfLimitException if the budget is spent
     */
    void spend(long bytes, String what) {
        if (bytes <= 0) {
            return;
        }
        if (bytes > limit - spent) {
            throw past(what);
        }
        spent += bytes;
    }

    /**
     * Charges the objects an object stream declares against the budget.
     *
     * @param declared what {@code /N} of the stream dictionary says
     * @param what     what to call the stream in a message
     * @throws PdfLimitException if the container declares more objects than the bound
     */
    void spendObjects(long declared, String what) {
        if (declared <= 0) {
            return;
        }
        if (declared > objectLimit - objects) {
            throw new PdfLimitException(what + " declares " + declared + " objects, and the"
                    + " object streams of one container declare at most " + objectLimit
                    + " objects together for this reader");
        }
        objects += declared;
    }

    /**
     * Returns the refusal for a stream that decodes past what is left of the budget.
     *
     * <p>The wording names the bound and the switch that moves it, and it is the same
     * sentence whether the bound was met while the file was being opened or afterwards,
     * because the budget is one number for the whole container.
     *
     * @param what what to call the stream in the message
     * @return the exception to throw
     */
    PdfLimitException past(String what) {
        return new PdfLimitException(what + " decodes to more than the " + limit
                + " bytes this reader decodes while it opens a PDF, counting every stream"
                + " the library reads out of the file and every stream this reader"
                + " decodes itself");
    }

    /**
     * Returns the refusal for a stream the library is about to decode and this reader
     * cannot measure.
     *
     * <p>Handing it over unmeasured would be a hole the size of whatever the chain
     * expands to, so it is refused instead. Nothing is lost by that: the structural
     * streams of a container of an electronic invoice are deflated, and a chain outside
     * the list this reader runs is not one a producer of such a file writes.
     *
     * @param what what to call the stream in the message
     * @return the exception to throw
     */
    PdfLimitException unmeasurable(String what) {
        return new PdfLimitException(what + " is filtered with a chain this reader does not"
                + " decode, so what it decodes to cannot be bounded and the file was not"
                + " opened; the structural streams of a container of an electronic invoice"
                + " are deflated");
    }
}
