package de.bsnsoft.esj.invoice;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * What the invoice adds up to: the document totals (BG-22).
 *
 * <p>Every one of them is of the semantic data type Amount and carries at most two
 * fraction digits. They are read from the document as it stands; nothing here computes.
 *
 * @param lineNetTotal    the sum of the invoice line net amounts (BT-106)
 * @param allowanceTotal  the sum of the document level allowances (BT-107), absent where
 *                        the invoice has none
 * @param chargeTotal     the sum of the document level charges (BT-108), absent where the
 *                        invoice has none
 * @param totalWithoutVat the invoice total amount without VAT (BT-109)
 * @param vatTotal        the invoice total VAT amount (BT-110)
 * @param totalWithVat    the invoice total amount with VAT (BT-112)
 * @param paidAmount      the amount already paid (BT-113)
 * @param roundingAmount  the amount rounded on the total (BT-114)
 * @param amountDue       the amount due for payment (BT-115)
 */
public record InvoiceTotals(BigDecimal lineNetTotal,
                            Optional<BigDecimal> allowanceTotal,
                            Optional<BigDecimal> chargeTotal,
                            BigDecimal totalWithoutVat,
                            Optional<BigDecimal> vatTotal,
                            BigDecimal totalWithVat,
                            Optional<BigDecimal> paidAmount,
                            Optional<BigDecimal> roundingAmount,
                            BigDecimal amountDue) {

    /**
     * Checks that every part is present.
     *
     * @param lineNetTotal    the sum of the invoice line net amounts (BT-106)
     * @param allowanceTotal  the sum of the document level allowances (BT-107), absent where
     *                        the invoice has none
     * @param chargeTotal     the sum of the document level charges (BT-108), absent where the
     *                        invoice has none
     * @param totalWithoutVat the invoice total amount without VAT (BT-109)
     * @param vatTotal        the invoice total VAT amount (BT-110)
     * @param totalWithVat    the invoice total amount with VAT (BT-112)
     * @param paidAmount      the amount already paid (BT-113)
     * @param roundingAmount  the amount rounded on the total (BT-114)
     * @param amountDue       the amount due for payment (BT-115)
     * @throws NullPointerException if a part is {@code null}
     */
    public InvoiceTotals {
        Objects.requireNonNull(lineNetTotal, "lineNetTotal");
        Objects.requireNonNull(allowanceTotal, "allowanceTotal");
        Objects.requireNonNull(chargeTotal, "chargeTotal");
        Objects.requireNonNull(totalWithoutVat, "totalWithoutVat");
        Objects.requireNonNull(vatTotal, "vatTotal");
        Objects.requireNonNull(totalWithVat, "totalWithVat");
        Objects.requireNonNull(paidAmount, "paidAmount");
        Objects.requireNonNull(roundingAmount, "roundingAmount");
        Objects.requireNonNull(amountDue, "amountDue");
    }
}
