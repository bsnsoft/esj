package de.bsnsoft.esj.invoice;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of the VAT breakdown (BG-23): what is taxed under one VAT category and rate,
 * and the VAT that comes to.
 *
 * @param vat            the VAT category and rate (BT-118, BT-119), with the exemption
 *                       reason where the breakdown states one (BT-120, BT-121)
 * @param taxableAmount  the VAT category taxable amount (BT-116)
 * @param taxAmount      the VAT category tax amount (BT-117)
 */
public record VatAmount(Vat vat, BigDecimal taxableAmount, BigDecimal taxAmount) {

    /**
     * Checks that every part is present.
     *
     * @param vat            the VAT category and rate (BT-118, BT-119), with the exemption
     *                       reason where the breakdown states one (BT-120, BT-121)
     * @param taxableAmount  the VAT category taxable amount (BT-116)
     * @param taxAmount      the VAT category tax amount (BT-117)
     * @throws NullPointerException if a part is {@code null}
     */
    public VatAmount {
        Objects.requireNonNull(vat, "vat");
        Objects.requireNonNull(taxableAmount, "taxableAmount");
        Objects.requireNonNull(taxAmount, "taxAmount");
    }
}
