package de.bsnsoft.esj.render;

import de.bsnsoft.esj.EsjLimitException;
import java.util.Objects;
import java.util.Optional;

/**
 * What a caller gets to decide about a rendering.
 *
 * <p>There is deliberately little to decide. The language picks the labels, the number
 * picture and the date picture, and both renderers of this module honour it. The page size
 * is the paper the PDF rendering is laid out for; the HTML rendering has no pages and
 * ignores it. The page bound is what a PDF rendering may cost, and the byte bound what an
 * HTML rendering may: a page of HTML grows with the document rather than with a page, so it
 * is measured in what it is written as.
 *
 * <p>The {@link Layout} is the one switch that moves values on the page, and it has two
 * settings, both of which show every term occurrence of the document: the letter layout,
 * which is the default, and the registry-driven generic layout. It is left empty here where
 * the caller did not choose, so that a {@link RenderTemplate} may choose instead and an
 * explicit choice of the caller still wins over the template; where neither chooses,
 * {@link #DEFAULT_LAYOUT} does. The HTML rendering is the layout of the KoSIT XRechnung
 * visualization and this module is the user of it, not its author, so the HTML rendering
 * has neither this switch nor a template.
 *
 * <p>The {@link PaymentCode} is the second: the letter layout draws the EPC QR code of a
 * credit transfer the document states, and a caller that wants none says so here. It is
 * {@link PaymentCode#TEMPLATE} where the caller did not decide, and a template may then
 * decide instead. The generic layout draws no code whatever any of them says.
 *
 * <p>A {@link RenderTemplate} brings a letterhead, a logo, colours, fonts, margins, the
 * places a model extension's terms get and the few decisions the letter layout leaves to a
 * sender. The HTML rendering ignores it.
 *
 * <p>Instances are immutable; every {@code with} method returns new options.
 * {@link #defaults()} is what a caller with no opinion gets.
 */
public final class RenderOptions {

    /**
     * The layout a PDF rendering is drawn in where neither the caller nor the template
     * names one: the letter, which is what an invoice rendered for its recipient is. A
     * caller that wants the shape of the semantic model names {@link Layout#GENERIC}, as
     * the validation report does for the invoice it carries.
     */
    public static final Layout DEFAULT_LAYOUT = Layout.LETTER;

    /** One gibibyte, the default bound on an HTML rendering. */
    private static final long GIB = 1024L * 1024L * 1024L;

    private static final RenderOptions DEFAULTS = new RenderOptions(RenderLanguage.GERMAN,
            PageSize.A4, null, 2_000, null, PaymentCode.TEMPLATE, GIB);

    private final RenderLanguage language;
    private final PageSize pageSize;
    private final RenderTemplate template;
    private final int maxPages;
    private final Layout layout;
    private final PaymentCode paymentCode;
    private final long maxHtmlBytes;

    private RenderOptions(RenderLanguage language, PageSize pageSize, RenderTemplate template,
                          int maxPages, Layout layout, PaymentCode paymentCode,
                          long maxHtmlBytes) {
        this.language = language;
        this.pageSize = pageSize;
        this.template = template;
        this.maxPages = maxPages;
        this.layout = layout;
        this.paymentCode = paymentCode;
        this.maxHtmlBytes = maxHtmlBytes;
    }

    /**
     * Returns the options of a rendering in German on A4 — the language the localization
     * of the visualization is authored in, and the paper an invoice is printed on where
     * that localization is read. They name no template and no layout, so a PDF rendering
     * is drawn in {@link #DEFAULT_LAYOUT}, and they leave the payment code to a template.
     *
     * <p>A PDF rendering may have 2 000 pages: an invoice of three hundred lines is about
     * eight pages, so this is room for tens of thousands of lines and for every document
     * the reference configuration of a reader accepts. It is there because a rendering costs
     * pages and the bounds of a reader are about the input: one value of a megabyte in a
     * narrow column is hundreds of pages of a document that is well inside every one of
     * them. A caller that renders invoices of the size {@code --limits large} is for raises
     * it.
     *
     * <p>An HTML rendering may have one gibibyte: an invoice of ten thousand lines is about
     * 130 megabytes of HTML, because the visualization writes every value of the document
     * into the page together with the markup of its place, and an attachment is written
     * into it whole. It is there because a page of HTML grows with the document and a heap
     * does not: a rendering that reaches it is stopped where it stands rather than finished
     * in memory and then found too large. A caller that renders documents from strangers
     * sets a bound of its own, as the command line tool does.
     *
     * @return the default options
     */
    public static RenderOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the language the labels, the dates and the decimals are written in.
     *
     * @return the language, {@link RenderLanguage#GERMAN} by default
     */
    public RenderLanguage language() {
        return language;
    }

    /**
     * Returns the paper a PDF rendering is laid out for.
     *
     * @return the page size, {@link PageSize#A4} by default
     */
    public PageSize pageSize() {
        return pageSize;
    }

    /**
     * Returns the branded template.
     *
     * @return the template, empty for an unbranded rendering
     */
    public Optional<RenderTemplate> template() {
        return Optional.ofNullable(template);
    }

    /**
     * Returns how many pages a PDF rendering may have before it is refused.
     *
     * @return the bound, 2 000 by default
     */
    public int maxPages() {
        return maxPages;
    }

    /**
     * Returns the page layout the caller chose.
     *
     * @return the layout, empty to leave the choice to the template and, where that is
     *         silent, to {@link #DEFAULT_LAYOUT}
     */
    public Optional<Layout> layout() {
        return Optional.ofNullable(layout);
    }

    /**
     * Returns whether the letter layout draws the EPC QR code of a credit transfer the
     * document states.
     *
     * @return the choice, {@link PaymentCode#TEMPLATE} by default
     */
    public PaymentCode paymentCode() {
        return paymentCode;
    }

    /**
     * Returns how many bytes an HTML rendering may have, in UTF-8, before it is refused.
     *
     * @return the bound, one gibibyte by default
     */
    public long maxHtmlBytes() {
        return maxHtmlBytes;
    }

    /**
     * Returns these options in another language.
     *
     * @param value the language
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public RenderOptions withLanguage(RenderLanguage value) {
        return new RenderOptions(Objects.requireNonNull(value, "language"), pageSize,
                template, maxPages, layout, paymentCode, maxHtmlBytes);
    }

    /**
     * Returns these options with another page size.
     *
     * @param value the paper a PDF rendering is laid out for
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public RenderOptions withPageSize(PageSize value) {
        return new RenderOptions(language, Objects.requireNonNull(value, "pageSize"),
                template, maxPages, layout, paymentCode, maxHtmlBytes);
    }

    /**
     * Returns these options with a branded template.
     *
     * @param value the template
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public RenderOptions withTemplate(RenderTemplate value) {
        return new RenderOptions(language, pageSize, Objects.requireNonNull(value, "template"),
                maxPages, layout, paymentCode, maxHtmlBytes);
    }

    /**
     * Returns these options with another bound on the number of pages.
     *
     * @param value how many pages a rendering may have
     * @return the options
     * @throws IllegalArgumentException if {@code value} is less than one
     */
    public RenderOptions withMaxPages(int value) {
        if (value < 1) {
            throw new IllegalArgumentException(
                    "a rendering has at least one page, and maxPages is " + value);
        }
        return new RenderOptions(language, pageSize, template, value, layout, paymentCode,
                maxHtmlBytes);
    }

    /**
     * Returns these options with a page layout, which wins over the one a template names.
     *
     * @param value the layout
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public RenderOptions withLayout(Layout value) {
        return new RenderOptions(language, pageSize, template, maxPages,
                Objects.requireNonNull(value, "layout"), paymentCode, maxHtmlBytes);
    }

    /**
     * Returns these options with the EPC QR code of a credit transfer drawn, left out, or
     * left to the template.
     *
     * <p>It is a decision of the letter layout. The generic layout is the shape of the
     * semantic model and draws no code whatever this says.
     *
     * @param value the choice
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public RenderOptions withPaymentCode(PaymentCode value) {
        return new RenderOptions(language, pageSize, template, maxPages, layout,
                Objects.requireNonNull(value, "paymentCode"), maxHtmlBytes);
    }

    /**
     * Returns these options with another bound on the size of an HTML rendering.
     *
     * <p>The bound is on the rendering as it is written, in bytes of UTF-8, and it holds
     * while the page is produced: a rendering that reaches it is stopped there, with a
     * {@link EsjLimitException} that names the bound {@code maxHtmlBytes}, and nothing of it
     * is returned. The PDF rendering is bounded by its pages and ignores this.
     *
     * @param value how many bytes an HTML rendering may have
     * @return the options
     * @throws IllegalArgumentException if {@code value} is less than one
     */
    public RenderOptions withMaxHtmlBytes(long value) {
        if (value < 1) {
            throw new IllegalArgumentException(
                    "an HTML rendering has at least one byte, and maxHtmlBytes is " + value);
        }
        return new RenderOptions(language, pageSize, template, maxPages, layout, paymentCode,
                value);
    }

    /**
     * Tells whether another object is options with the same members. A template is equal
     * only to itself.
     *
     * @param other the object to compare with
     * @return {@code true} if every member is equal
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof RenderOptions that
                && language == that.language
                && pageSize == that.pageSize
                && Objects.equals(template, that.template)
                && maxPages == that.maxPages
                && layout == that.layout
                && paymentCode == that.paymentCode
                && maxHtmlBytes == that.maxHtmlBytes;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(language, pageSize, template, maxPages, layout, paymentCode,
                maxHtmlBytes);
    }

    /**
     * Returns the options as one line.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return "RenderOptions[language=" + language + ", pageSize=" + pageSize
                + ", template=" + (template == null ? "none" : template.name())
                + ", maxPages=" + maxPages + ", layout=" + (layout == null ? "none" : layout)
                + ", paymentCode=" + paymentCode + ", maxHtmlBytes=" + maxHtmlBytes + "]";
    }
}
