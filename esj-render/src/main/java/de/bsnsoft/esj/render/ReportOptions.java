package de.bsnsoft.esj.render;

import java.util.Objects;
import java.util.Optional;

/**
 * What a caller gets to decide about a validation report.
 *
 * <p>Four things, and the third is the only one that can make two runs differ. The
 * language picks the labels of the report and the language the invoice below it is
 * rendered in. {@code includeInvoice} says whether the invoice is in the file at all: a
 * report is a proof about one document and normally carries it, and a caller that keeps
 * the invoice elsewhere, or that is writing thousands of these, says so.
 *
 * <p>{@code pageSize} is the paper the PDF form is laid out on, the report and the invoice
 * pages after it alike; the HTML form has no pages and ignores it. A report is a document
 * somebody files and prints, and the paper it is filed on is not the same everywhere the
 * tool runs, which is the whole of the reason it is a choice.
 *
 * <p>{@code time} is a string and not a clock. A report carries no timestamp of its own,
 * because the same input and the same options must give the same bytes — a report that
 * dated itself could not be compared, diffed or checked in. A caller who needs the moment
 * on the page passes it, in whatever form it writes moments in, and the report prints it
 * exactly as given without parsing it, reformatting it or adding a time zone to it.
 *
 * @param language       the language the labels of the report and of the rendered invoice
 *                       are written in
 * @param includeInvoice whether the rendered invoice is part of the report
 * @param time           what the report prints as the moment of the run, exactly as
 *                       given; empty for a report that names no moment
 * @param pageSize       the paper the PDF form is laid out on; the HTML form has no pages
 */
public record ReportOptions(RenderLanguage language, boolean includeInvoice,
                            Optional<String> time, PageSize pageSize) {

    /**
     * Checks every member.
     *
     * @param language       the language the labels of the report and of the rendered
     *                       invoice are written in
     * @param includeInvoice whether the rendered invoice is part of the report
     * @param time           what the report prints as the moment of the run, exactly as
     *                       given; empty for a report that names no moment
     * @param pageSize       the paper the PDF form is laid out on
     * @throws NullPointerException if an argument is {@code null}
     */
    public ReportOptions {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(pageSize, "pageSize");
    }

    /**
     * Returns the options of a report in German that carries the invoice and names no
     * moment.
     *
     * @return the default options
     */
    public static ReportOptions defaults() {
        return new ReportOptions(RenderLanguage.GERMAN, true, Optional.empty(),
                PageSize.A4);
    }

    /**
     * Returns the options of a report in a language, otherwise the defaults.
     *
     * @param language the language
     * @return the options
     * @throws NullPointerException if {@code language} is {@code null}
     */
    public static ReportOptions in(RenderLanguage language) {
        return new ReportOptions(language, true, Optional.empty(), PageSize.A4);
    }

    /**
     * Returns these options with or without the rendered invoice.
     *
     * @param include whether the invoice is part of the report
     * @return the options
     */
    public ReportOptions withInvoice(boolean include) {
        return new ReportOptions(language, include, time, pageSize);
    }

    /**
     * Returns these options with a moment for the report to print.
     *
     * @param moment the moment, as the caller writes it; it is printed unchanged
     * @return the options
     * @throws NullPointerException if {@code moment} is {@code null}
     */
    public ReportOptions at(String moment) {
        return new ReportOptions(language, includeInvoice,
                Optional.of(Objects.requireNonNull(moment, "moment")), pageSize);
    }

    /**
     * Returns these options on another paper.
     *
     * @param size the paper the PDF form is laid out on
     * @return the options
     * @throws NullPointerException if {@code size} is {@code null}
     */
    public ReportOptions on(PageSize size) {
        return new ReportOptions(language, includeInvoice, time,
                Objects.requireNonNull(size, "size"));
    }

    /**
     * Returns the options a rendering of the invoice inside this report is made with.
     *
     * @return the render options
     */
    RenderOptions rendering() {
        return RenderOptions.in(language).on(pageSize);
    }
}
