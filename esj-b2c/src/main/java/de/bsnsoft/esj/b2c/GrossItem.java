package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.invoice.Line;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One invoice line of a shop that prices with VAT included: a line of the domain API, and
 * beside it the figure the customer was shown.
 *
 * <pre>{@code
 * GrossItem.perUnit(
 *         Line.of("Shower fitting SF-20").quantity(1, Unit.PIECE).vat(Vat.standard(19)),
 *         "99.99");
 * }</pre>
 *
 * <p>The line states what it always states — the item, the quantity, the unit, the VAT
 * category and rate — and no net unit price, because that is what a gross authoring policy
 * derives. A line that does state one is written as it stands and the policy replaces the
 * price it carries.
 *
 * @param line                    the line in the words of the domain API
 * @param displayedGrossUnitPrice the displayed gross unit price (BT-B2C-001), where one was
 *                                shown per unit
 * @param displayedGrossLineTotal the displayed gross line total (BT-B2C-002), where one was
 *                                shown for the line
 * @param displayedLineVatAmount  the displayed line VAT amount (BT-B2C-003), where one was
 *                                shown at line level
 */
public record GrossItem(Line line,
                        Optional<BigDecimal> displayedGrossUnitPrice,
                        Optional<BigDecimal> displayedGrossLineTotal,
                        Optional<BigDecimal> displayedLineVatAmount) {

    /**
     * Checks the arguments.
     *
     * @param line                    the line in the words of the domain API
     * @param displayedGrossUnitPrice the displayed gross unit price (BT-B2C-001), where one was
     *                                shown per unit
     * @param displayedGrossLineTotal the displayed gross line total (BT-B2C-002), where one was
     *                                shown for the line
     * @param displayedLineVatAmount  the displayed line VAT amount (BT-B2C-003), where one was
     *                                shown at line level
     * @throws NullPointerException if an argument is {@code null}
     */
    public GrossItem {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(displayedGrossUnitPrice, "displayedGrossUnitPrice");
        Objects.requireNonNull(displayedGrossLineTotal, "displayedGrossLineTotal");
        Objects.requireNonNull(displayedLineVatAmount, "displayedLineVatAmount");
    }

    /**
     * Returns a line whose unit price with VAT was shown to the customer, which
     * {@link GrossAuthoring#GROSS_UNIT_AUTHORING} derives the net line from.
     *
     * @param line  the line
     * @param gross the displayed gross unit price, at the scale it was shown with
     * @return the item
     * @throws NullPointerException if an argument is {@code null}
     */
    public static GrossItem perUnit(Line line, BigDecimal gross) {
        return new GrossItem(line, Optional.of(Objects.requireNonNull(gross, "gross")),
                Optional.empty(), Optional.empty());
    }

    /**
     * Returns a line whose unit price with VAT was shown, from its decimal spelling.
     *
     * @param line  the line
     * @param gross the displayed gross unit price
     * @return the item
     * @throws NumberFormatException if the text is no decimal number
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static GrossItem perUnit(Line line, String gross) {
        return perUnit(line, new BigDecimal(Objects.requireNonNull(gross, "gross")));
    }

    /**
     * Returns a line whose total with VAT was shown to the customer, which
     * {@link GrossAuthoring#GROSS_LINE_AUTHORING} derives the net line from.
     *
     * @param line  the line
     * @param gross the displayed gross line total
     * @return the item
     * @throws NullPointerException if an argument is {@code null}
     */
    public static GrossItem perLine(Line line, BigDecimal gross) {
        return new GrossItem(line, Optional.empty(),
                Optional.of(Objects.requireNonNull(gross, "gross")), Optional.empty());
    }

    /**
     * Returns a line whose total with VAT was shown, from its decimal spelling.
     *
     * @param line  the line
     * @param gross the displayed gross line total
     * @return the item
     * @throws NumberFormatException if the text is no decimal number
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static GrossItem perLine(Line line, String gross) {
        return perLine(line, new BigDecimal(Objects.requireNonNull(gross, "gross")));
    }

    /**
     * Returns a line no gross figure of its own was shown for, which
     * {@link GrossAuthoring#GROSS_TOTAL_AUTHORING} takes a share of the agreed total.
     *
     * @param line the line
     * @return the item
     * @throws NullPointerException if {@code line} is {@code null}
     */
    public static GrossItem of(Line line) {
        return new GrossItem(line, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Returns this item with the VAT amount the customer was shown at line level.
     *
     * @param value the displayed line VAT amount (BT-B2C-003)
     * @return the item
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public GrossItem displayedLineVatAmount(BigDecimal value) {
        return new GrossItem(line, displayedGrossUnitPrice, displayedGrossLineTotal,
                Optional.of(Objects.requireNonNull(value, "value")));
    }

    /**
     * Returns this item with the VAT amount shown at line level, from its decimal spelling.
     *
     * @param value the displayed line VAT amount (BT-B2C-003)
     * @return the item
     * @throws NumberFormatException if the text is no decimal number
     * @throws NullPointerException  if {@code value} is {@code null}
     */
    public GrossItem displayedLineVatAmount(String value) {
        return displayedLineVatAmount(new BigDecimal(Objects.requireNonNull(value, "value")));
    }
}
