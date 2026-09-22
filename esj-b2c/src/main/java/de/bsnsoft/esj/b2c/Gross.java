package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.typed.InvoiceEditor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Writing the gross figures a consumer was shown, and deriving the net invoice from them.
 *
 * <pre>{@code
 * Gross gross = Gross.on(invoice);
 * gross.line(0).displayedGrossUnitPrice("99.99");
 * gross.displayedGrossTotal("153.47");
 * AuthoringReport report = gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
 * }</pre>
 *
 * <p>It is the overlay editor {@link B2c#edit(InvoiceEditor)} hands out, with the figures
 * taken as strings as well as decimals and with the policies of {@link GrossAuthoring} one
 * call away. {@link #b2c()} is that overlay editor itself, for everything this class does not
 * shorten; the typed editor of the core model stays what writes the core terms.
 *
 * <p>A figure is stored as it is written. Displayed Gross Unit Price is of the unlimited
 * semantic data type Unit Price Amount (EN 16931-1, 6.5, AC 8) and nothing here rounds,
 * normalises or shortens it.
 */
public final class Gross {

    private final B2cInvoiceEditor overlay;

    private Gross(B2cInvoiceEditor overlay) {
        this.overlay = overlay;
    }

    /**
     * Opens the gross figures over the builder a typed editor writes into, so that the core
     * terms and the figures of the extension go into one document.
     *
     * @param invoice the typed editor of the core model
     * @return the gross figures of that invoice
     * @throws NullPointerException if {@code invoice} is {@code null}
     */
    public static Gross on(InvoiceEditor invoice) {
        return new Gross(B2c.edit(Objects.requireNonNull(invoice, "invoice")));
    }

    /**
     * Opens the gross figures over a document builder.
     *
     * @param builder the builder to write into
     * @return the gross figures of that invoice
     * @throws NullPointerException if {@code builder} is {@code null}
     */
    public static Gross on(SemanticDocument.Builder builder) {
        return new Gross(B2c.edit(Objects.requireNonNull(builder, "builder")));
    }

    /**
     * Opens the gross figures over an overlay editor that is already open.
     *
     * @param overlay the overlay editor
     * @return the gross figures it writes
     * @throws NullPointerException if {@code overlay} is {@code null}
     */
    public static Gross on(B2cInvoiceEditor overlay) {
        return new Gross(Objects.requireNonNull(overlay, "overlay"));
    }

    /**
     * Returns the overlay editor underneath, which writes the four terms of the extension and
     * nothing else.
     *
     * @return the overlay editor
     */
    public B2cInvoiceEditor b2c() {
        return overlay;
    }

    /**
     * Returns the gross figures of one invoice line.
     *
     * @param index the zero-based position of the line, which is the position it has in the
     *              typed view of the core model
     * @return the figures of that line
     * @throws IndexOutOfBoundsException if the invoice carries no line at that position; an
     *                                   overlay appends none, because how many lines an
     *                                   invoice has is decided by the core model
     */
    public Line line(int index) {
        List<B2cInvoiceLineEditor> lines = overlay.lines();
        if (index < 0 || index >= lines.size()) {
            throw new IndexOutOfBoundsException("the invoice carries no invoice line (BG-25) at"
                    + " index " + index + ", it carries " + lines.size() + "; the core editor"
                    + " writes the lines, the overlay only their gross figures");
        }
        return new Line(lines.get(index));
    }

    /**
     * Writes the displayed invoice gross total (BT-B2C-010): the total with VAT the customer
     * was shown or agreed to.
     *
     * @param value the figure, or {@code null} to take it away again
     * @return this
     */
    public Gross displayedGrossTotal(BigDecimal value) {
        overlay.displayedGrossTotal(value);
        return this;
    }

    /**
     * Writes the displayed invoice gross total (BT-B2C-010) from its decimal spelling.
     *
     * @param value the figure, or {@code null} to take it away again
     * @return this
     * @throws NumberFormatException if the text is no decimal number
     */
    public Gross displayedGrossTotal(String value) {
        return displayedGrossTotal(decimal(value));
    }

    /**
     * Derives the net invoice from the gross figures with one of the policies of
     * {@link GrossAuthoring}.
     *
     * @param policy the policy
     * @return what the policy wrote, where it cut a price and what it set BT-114 to
     * @throws PolicyPreconditionException if the invoice does not state what the policy needs
     * @throws de.bsnsoft.esj.typed.DerivationException if the totals cannot be
     *                                                             derived from what the
     *                                                             policy wrote
     * @throws NullPointerException        if {@code policy} is {@code null}
     */
    public AuthoringReport derive(GrossAuthoring policy) {
        return Objects.requireNonNull(policy, "policy").apply(overlay.builder());
    }

    /**
     * Returns the invoice as it stands, gross figures and core terms together.
     *
     * @return an immutable document in canonical path order
     */
    public SemanticDocument document() {
        return overlay.document();
    }

    @Override
    public String toString() {
        return "Gross[" + overlay.path() + "]";
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isEmpty() ? null : new BigDecimal(value);
    }

    /** The gross figures of one invoice line. */
    public static final class Line {

        private final B2cInvoiceLineEditor editor;

        private Line(B2cInvoiceLineEditor editor) {
            this.editor = editor;
        }

        /**
         * Writes the displayed gross unit price (BT-B2C-001).
         *
         * @param value the figure, at the scale it was shown with, or {@code null} to take it
         *              away again
         * @return this
         */
        public Line displayedGrossUnitPrice(BigDecimal value) {
            editor.displayedGrossUnitPrice(value);
            return this;
        }

        /**
         * Writes the displayed gross unit price (BT-B2C-001) from its decimal spelling.
         *
         * @param value the figure, or {@code null} to take it away again
         * @return this
         * @throws NumberFormatException if the text is no decimal number
         */
        public Line displayedGrossUnitPrice(String value) {
            return displayedGrossUnitPrice(decimal(value));
        }

        /**
         * Writes the displayed gross line total (BT-B2C-002).
         *
         * @param value the figure, or {@code null} to take it away again
         * @return this
         */
        public Line displayedGrossLineTotal(BigDecimal value) {
            editor.displayedGrossLineTotal(value);
            return this;
        }

        /**
         * Writes the displayed gross line total (BT-B2C-002) from its decimal spelling.
         *
         * @param value the figure, or {@code null} to take it away again
         * @return this
         * @throws NumberFormatException if the text is no decimal number
         */
        public Line displayedGrossLineTotal(String value) {
            return displayedGrossLineTotal(decimal(value));
        }

        /**
         * Writes the displayed line VAT amount (BT-B2C-003), where one was shown at line
         * level.
         *
         * @param value the figure, or {@code null} to take it away again
         * @return this
         */
        public Line displayedLineVatAmount(BigDecimal value) {
            editor.displayedLineVatAmount(value);
            return this;
        }

        /**
         * Writes the displayed line VAT amount (BT-B2C-003) from its decimal spelling.
         *
         * @param value the figure, or {@code null} to take it away again
         * @return this
         * @throws NumberFormatException if the text is no decimal number
         */
        public Line displayedLineVatAmount(String value) {
            return displayedLineVatAmount(decimal(value));
        }

        @Override
        public String toString() {
            return "Gross.Line[" + editor.path() + "]";
        }
    }
}
