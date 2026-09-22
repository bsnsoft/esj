package de.bsnsoft.esj.render;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a report has to say about the invoice it is about.
 *
 * <p>A report is a statement about one document, and the document is in it. Five things
 * can be true of that, and a report says which: the invoice is here; it is here and part
 * of the document did not reach the rendering; it is not here because nothing could render
 * it; it is not here because the document is larger than this form of the report renders;
 * it is not here because its rendering turned out larger than this form carries. The one
 * answer that may never be given is the sixth — showing a rendering that is not the whole
 * document and saying nothing — because a reader of a proof would take the page for the
 * document it names a digest of.
 *
 * <p>The last two are the same bound measured at two moments. The document is measured
 * first, because a rendering that does not fit in a report does not fit in the heap
 * either; the rendering is measured afterwards as well, because the first measurement is
 * an estimate and an estimate that came out too low must not be the last word.
 *
 * <p>Instances are immutable and safe to share between threads.
 *
 * @param rendering the rendered invoice, where there is one to show
 * @param refusal   why nothing could be rendered, in the words of whatever refused it
 * @param oversize  how large the rendering is, where it was made and is larger than this
 *                  form carries
 * @param oversizeValues how many values the document carries, where it was not rendered
 *                  because the estimate of its rendering is past that bound
 * @param notes     what did not reach the rendering, one line each as the exporter wrote
 *                  them
 */
record ReportInvoice(Optional<String> rendering,
                     Optional<String> refusal,
                     Optional<Integer> oversize,
                     Optional<Integer> oversizeValues,
                     List<String> notes) {

    /** Copies the notes and refuses a missing member. */
    ReportInvoice {
        Objects.requireNonNull(rendering, "rendering");
        Objects.requireNonNull(refusal, "refusal");
        Objects.requireNonNull(oversize, "oversize");
        Objects.requireNonNull(oversizeValues, "oversizeValues");
        notes = List.copyOf(notes);
    }

    /**
     * Returns the invoice of a report that carries none: a run without a document, or a
     * caller who asked for the report alone.
     *
     * @return the invoice
     */
    static ReportInvoice none() {
        return new ReportInvoice(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of());
    }

    /**
     * Returns an invoice that was rendered.
     *
     * @param rendering the rendering
     * @param notes     what did not reach it
     * @return the invoice
     */
    static ReportInvoice of(String rendering, List<String> notes) {
        return new ReportInvoice(Optional.of(rendering), Optional.empty(),
                Optional.empty(), Optional.empty(), notes);
    }

    /**
     * Returns an invoice nothing could render.
     *
     * @param reason why, in the words of whatever refused it
     * @return the invoice
     */
    static ReportInvoice refused(String reason) {
        return new ReportInvoice(Optional.empty(), Optional.of(reason), Optional.empty(),
                Optional.empty(), List.of());
    }

    /**
     * Returns an invoice whose rendering is larger than this form of the report carries.
     *
     * @param characters how large the rendering is
     * @return the invoice
     */
    static ReportInvoice tooLarge(int characters) {
        return new ReportInvoice(Optional.empty(), Optional.empty(),
                Optional.of(characters), Optional.empty(), List.of());
    }

    /**
     * Returns an invoice that was not rendered at all, because the document it would be
     * rendered from is larger than this form of the report carries.
     *
     * <p>It is the same bound as {@link #tooLarge(int)} and the other side of it: that one
     * is a rendering that was made and measured, this one a rendering that was never made.
     * The rendering is what does not fit, so a report that made it in order to find that
     * out would spend the heap it was trying to save.
     *
     * @param values how many values the document carries
     * @return the invoice
     */
    static ReportInvoice tooLargeDocument(int values) {
        return new ReportInvoice(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(values), List.of());
    }

    /**
     * Tells whether this report has a section about the invoice at all.
     *
     * @return {@code true} where it has something to show or something to say
     */
    boolean present() {
        return rendering().isPresent() || refusal().isPresent() || oversize().isPresent()
                || oversizeValues().isPresent();
    }
}
