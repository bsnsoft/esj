package de.bsnsoft.esj.render;

import java.util.Objects;
import java.util.Optional;

/**
 * What a caller gets to decide about a rendering.
 *
 * <p>There is deliberately little to decide. The language picks the labels, the number
 * picture and the date picture, and both renderers of this module honour it. The page size
 * is the paper the PDF rendering is laid out for; the HTML rendering has no pages and
 * ignores it. The page bound is what a rendering may cost.
 *
 * <p>The {@link Layout} is the one switch that moves values on the page, and it has two
 * settings, both of which show every term occurrence of the document: the registry-driven
 * generic layout, which is the default, and the letter layout. It is left empty here where
 * the caller did not choose, so that a {@link RenderTemplate} may choose instead and an
 * explicit choice of the caller still wins over the template. The HTML rendering is the
 * layout of the KoSIT XRechnung visualization and this module is the user of it, not its
 * author, so the HTML rendering has neither this switch nor a template.
 *
 * <p>The payment code is the second: the letter layout draws the EPC QR code of a credit
 * transfer the document states, and a caller that wants none says so here. It is left empty
 * where the caller did not decide, and a template may then decide instead. The generic
 * layout draws no code whatever any of them says.
 *
 * <p>A {@link RenderTemplate} brings a letterhead, a logo, colours, fonts, margins, the
 * places a model extension's terms get and the few decisions the letter layout leaves to a
 * sender. The HTML rendering ignores it.
 *
 * @param language the language the labels, the dates and the decimals are written in
 * @param pageSize the paper a PDF rendering is laid out for
 * @param template the branded template, empty for the generic rendering
 * @param maxPages how many pages a PDF rendering may have before it is refused
 * @param layout   the page layout the caller chose, empty to leave the choice to the
 *                 template and, where that is silent, to {@link Layout#GENERIC}
 * @param paymentCode whether the letter layout draws the EPC QR code of a credit transfer
 *                    the document states, empty to leave the choice to the template and,
 *                    where that is silent, to drawing it
 */
public record RenderOptions(RenderLanguage language, PageSize pageSize,
                            Optional<RenderTemplate> template, int maxPages,
                            Optional<Layout> layout, Optional<Boolean> paymentCode) {

    /**
     * How many pages a rendering may have where the caller named no number.
     *
     * <p>It is generous: an invoice of three hundred lines is about eight pages, so this
     * is room for tens of thousands of lines and for every document the reference
     * configuration of a reader accepts. It is there because a rendering costs pages and
     * the bounds of a reader are about the input: one value of a megabyte in a narrow
     * column is hundreds of pages of a document that is well inside every one of them.
     * A caller that renders invoices of the size {@code --limits large} is for raises it.
     */
    public static final int DEFAULT_MAX_PAGES = 2_000;

    /**
     * Checks the members.
     *
     * @param language the language the labels, the dates and the decimals are written in
     * @param pageSize the paper a PDF rendering is laid out for
     * @param template the branded template, empty for the generic rendering
     * @param maxPages how many pages a PDF rendering may have before it is refused
     * @param layout   the page layout the caller chose, empty to leave the choice to the
     *                 template
     * @param paymentCode whether the letter layout draws the EPC QR code, empty to leave
     *                    the choice to the template
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code maxPages} is less than one
     */
    public RenderOptions {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(pageSize, "pageSize");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(paymentCode, "paymentCode");
        if (maxPages < 1) {
            throw new IllegalArgumentException(
                    "a rendering has at least one page, and maxPages is " + maxPages);
        }
    }

    /**
     * Returns the options of a rendering in German on A4 — the language the localization
     * of the visualization is authored in, and the paper an invoice is printed on where
     * that localization is read.
     *
     * @return the default options
     */
    public static RenderOptions defaults() {
        return new RenderOptions(RenderLanguage.GERMAN, PageSize.A4, Optional.empty(),
                DEFAULT_MAX_PAGES, Optional.empty(), Optional.empty());
    }

    /**
     * Returns the options of a rendering in a language, on A4.
     *
     * @param language the language
     * @return the options
     * @throws NullPointerException if {@code language} is {@code null}
     */
    public static RenderOptions in(RenderLanguage language) {
        return defaults().withLanguage(language);
    }

    /**
     * Returns these options in another language.
     *
     * @param value the language
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public RenderOptions withLanguage(RenderLanguage value) {
        return new RenderOptions(value, pageSize, template, maxPages, layout, paymentCode);
    }

    /**
     * Returns these options with another page size.
     *
     * @param size the paper a PDF rendering is laid out for
     * @return the options
     * @throws NullPointerException if {@code size} is {@code null}
     */
    public RenderOptions on(PageSize size) {
        return new RenderOptions(language, size, template, maxPages, layout, paymentCode);
    }

    /**
     * Returns these options with a branded template.
     *
     * @param value the template
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public RenderOptions with(RenderTemplate value) {
        return new RenderOptions(language, pageSize,
                Optional.of(Objects.requireNonNull(value, "value")), maxPages, layout,
                paymentCode);
    }

    /**
     * Returns these options with another bound on the number of pages.
     *
     * @param pages how many pages a rendering may have
     * @return the options
     * @throws IllegalArgumentException if {@code pages} is less than one
     */
    public RenderOptions withMaxPages(int pages) {
        return new RenderOptions(language, pageSize, template, pages, layout, paymentCode);
    }

    /**
     * Returns these options with a page layout, which wins over the one a template names.
     *
     * @param value the layout
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public RenderOptions layout(Layout value) {
        return new RenderOptions(language, pageSize, template, maxPages,
                Optional.of(Objects.requireNonNull(value, "value")), paymentCode);
    }

    /**
     * Returns these options with the EPC QR code of a credit transfer drawn or left out,
     * which wins over what a template says.
     *
     * <p>It is a decision of the letter layout. The generic layout is the shape of the
     * semantic model and draws no code whatever this says.
     *
     * @param value whether the payment block carries the code
     * @return the options
     */
    public RenderOptions withPaymentCode(boolean value) {
        return new RenderOptions(language, pageSize, template, maxPages, layout,
                Optional.of(value));
    }
}
