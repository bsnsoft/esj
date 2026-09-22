package de.bsnsoft.esj.typed;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * What a caller may vary about the derivation of the document totals.
 *
 * <p>The arithmetic of {@link Totals} is fixed by EN 16931-1 and is not an option. These two
 * are: whether an invoice line net amount (BT-131) the caller wrote by hand may be replaced,
 * and the exchange rate that carries the invoice total VAT amount (BT-110) into the VAT
 * accounting currency (BT-6) as the invoice total VAT amount in accounting currency (BT-111).
 *
 * @param overwriteLines whether a line that already carries an invoice line net amount
 *                       (BT-131) is recomputed from its price, quantity, charges and
 *                       allowances. With the default {@code false} such a line keeps the
 *                       amount it was given, and a derivation that computes a different one
 *                       refuses rather than overwrite it
 * @param vatAccountingCurrencyRate the rate that multiplies the invoice total VAT amount
 *                       (BT-110) into the VAT accounting currency (BT-6), or an empty
 *                       optional to leave the invoice total VAT amount in accounting currency
 *                       (BT-111) untouched. The rate is a plain multiplier: one unit of the
 *                       invoice currency (BT-5) in units of the accounting currency
 */
public record TotalsOptions(boolean overwriteLines, Optional<BigDecimal> vatAccountingCurrencyRate) {

    private static final TotalsOptions STANDARD = new TotalsOptions(false, Optional.empty());

    /**
     * Checks the arguments.
     *
     * @param overwriteLines whether a line that already carries an invoice line net amount
     *                       (BT-131) is recomputed from its price, quantity, charges and
     *                       allowances. With the default {@code false} such a line keeps the
     *                       amount it was given, and a derivation that computes a different one
     *                       refuses rather than overwrite it
     * @param vatAccountingCurrencyRate the rate that multiplies the invoice total VAT amount
     *                       (BT-110) into the VAT accounting currency (BT-6), or an empty
     *                       optional to leave the invoice total VAT amount in accounting currency
     *                       (BT-111) untouched. The rate is a plain multiplier: one unit of the
     *                       invoice currency (BT-5) in units of the accounting currency
     * @throws IllegalArgumentException if the exchange rate is not positive
     * @throws NullPointerException     if {@code vatAccountingCurrencyRate} is {@code null}
     */
    public TotalsOptions {
        Objects.requireNonNull(vatAccountingCurrencyRate, "vatAccountingCurrencyRate");
        if (vatAccountingCurrencyRate.isPresent()
                && vatAccountingCurrencyRate.get().signum() <= 0) {
            throw new IllegalArgumentException("an exchange rate is positive, not "
                    + vatAccountingCurrencyRate.get().toPlainString());
        }
    }

    /**
     * Returns the options the standard policy runs with: hand-written line net amounts are
     * kept, and nothing is written into BT-111.
     *
     * @return the default options
     */
    public static TotalsOptions standard() {
        return STANDARD;
    }

    /**
     * Returns these options with the recomputation of hand-written line net amounts switched
     * on or off.
     *
     * @param overwrite whether a line that already carries BT-131 is recomputed
     * @return the options
     */
    public TotalsOptions withOverwriteLines(boolean overwrite) {
        return new TotalsOptions(overwrite, vatAccountingCurrencyRate);
    }

    /**
     * Returns these options with an exchange rate into the VAT accounting currency.
     *
     * @param rate the rate, positive, or {@code null} to leave BT-111 untouched
     * @return the options
     * @throws IllegalArgumentException if the rate is not positive
     */
    public TotalsOptions withVatAccountingCurrencyRate(BigDecimal rate) {
        return new TotalsOptions(overwriteLines, Optional.ofNullable(rate));
    }
}
