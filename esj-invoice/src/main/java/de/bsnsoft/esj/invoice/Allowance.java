package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.AllowanceReason;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A deduction from what is invoiced: a document level allowance (BG-20) or an allowance
 * on one invoice line (BG-27).
 *
 * <p>An allowance states an amount (BT-92, BT-136) or a percentage (BT-94, BT-138) of a
 * base amount (BT-93, BT-137), and a reason in words (BT-97, BT-139), as a code (BT-98,
 * BT-140) or as both. A document level allowance is taxed and therefore carries a
 * {@link Vat}; a line allowance is taxed with its line and refuses one.
 *
 * <p>Where the allowance is a percentage, the amount is that percentage of the base
 * amount, exact in {@code BigDecimal} and rounded half up to two decimals once, at the
 * amount. A line allowance that states no base amount takes the line's own net base —
 * the invoiced quantity times the item net price over the item price base quantity — and
 * writes it as BT-137, so that the invoice says what the percentage was taken of. A
 * document level allowance has no such base and refuses a percentage without one.
 *
 * <p>The allowance is a value: every method returns a new one and the original is
 * unchanged.
 */
public final class Allowance {

    private final BigDecimal amount;

    private final BigDecimal percentage;

    private final BigDecimal base;

    private final String reasonText;

    private final Coded reasonCode;

    private final Vat vat;

    private Allowance(BigDecimal amount, BigDecimal percentage, BigDecimal base, String reasonText,
                      Coded reasonCode, Vat vat) {
        this.amount = amount;
        this.percentage = percentage;
        this.base = base;
        this.reasonText = reasonText;
        this.reasonCode = reasonCode;
        this.vat = vat;
    }

    /**
     * An allowance of a fixed amount, with the reason in words.
     *
     * @param amount the amount deducted, in the currency of the invoice
     * @param reason the reason (BT-97, BT-139)
     * @return the allowance
     * @throws IllegalArgumentException if the reason is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Allowance amount(BigDecimal amount, String reason) {
        return new Allowance(Objects.requireNonNull(amount, "amount"), null, null,
                Amounts.text(reason, "reason"), null, null);
    }

    /**
     * An allowance of a fixed amount, with the reason as a code.
     *
     * @param amount the amount deducted, in the currency of the invoice
     * @param reason the reason code (BT-98, BT-140), a code of UNTDID 5189
     * @return the allowance
     * @throws NullPointerException if a part is {@code null}
     */
    public static Allowance amount(BigDecimal amount, Coded reason) {
        return new Allowance(Objects.requireNonNull(amount, "amount"), null, null, null,
                Objects.requireNonNull(reason, "reason"), null);
    }

    /**
     * An allowance of a fixed amount, with the reason as a code of the generated list.
     *
     * @param amount the amount deducted, in the currency of the invoice
     * @param reason the reason code (BT-98, BT-140)
     * @return the allowance
     * @throws NullPointerException if a part is {@code null}
     */
    public static Allowance amount(BigDecimal amount, AllowanceReason reason) {
        return amount(amount, (Coded) reason);
    }

    /**
     * An allowance of a fixed amount, with the reason in words.
     *
     * @param amount the amount as text, for example {@code "25"}
     * @param reason the reason (BT-97, BT-139)
     * @return the allowance
     * @throws IllegalArgumentException if the amount is not a decimal or the reason is
     *                                  blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Allowance amount(String amount, String reason) {
        return amount(Amounts.decimal(amount, "amount"), reason);
    }

    /**
     * An allowance of a fixed amount, with the reason as a code.
     *
     * @param amount the amount as text, for example {@code "25"}
     * @param reason the reason code (BT-98, BT-140), a code of UNTDID 5189
     * @return the allowance
     * @throws IllegalArgumentException if the amount is not a decimal
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Allowance amount(String amount, Coded reason) {
        return amount(Amounts.decimal(amount, "amount"), reason);
    }

    /**
     * An allowance of a fixed amount, with the reason as a code of the generated list.
     *
     * @param amount the amount as text, for example {@code "25"}
     * @param reason the reason code (BT-98, BT-140)
     * @return the allowance
     * @throws IllegalArgumentException if the amount is not a decimal
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Allowance amount(String amount, AllowanceReason reason) {
        return amount(amount, (Coded) reason);
    }

    /**
     * An allowance of a percentage of a base amount, with the reason in words.
     *
     * @param percentage the percentage (BT-94, BT-138), at the scale the caller writes it
     * @param reason     the reason (BT-97, BT-139)
     * @return the allowance
     * @throws IllegalArgumentException if the reason is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Allowance percent(BigDecimal percentage, String reason) {
        return new Allowance(null, Objects.requireNonNull(percentage, "percentage"), null,
                Amounts.text(reason, "reason"), null, null);
    }

    /**
     * An allowance of a percentage of a base amount, with the reason as a code.
     *
     * @param percentage the percentage (BT-94, BT-138), at the scale the caller writes it
     * @param reason     the reason code (BT-98, BT-140), a code of UNTDID 5189
     * @return the allowance
     * @throws NullPointerException if a part is {@code null}
     */
    public static Allowance percent(BigDecimal percentage, Coded reason) {
        return new Allowance(null, Objects.requireNonNull(percentage, "percentage"), null, null,
                Objects.requireNonNull(reason, "reason"), null);
    }

    /**
     * An allowance of a percentage of a base amount, with the reason as a code of the generated
     * list.
     *
     * @param percentage the percentage (BT-94, BT-138), at the scale the caller writes it
     * @param reason     the reason code (BT-98, BT-140)
     * @return the allowance
     * @throws NullPointerException if a part is {@code null}
     */
    public static Allowance percent(BigDecimal percentage, AllowanceReason reason) {
        return percent(percentage, (Coded) reason);
    }

    /**
     * An allowance of a percentage of a base amount, with the reason in words.
     *
     * @param percentage the percentage, for example {@code 10}
     * @param reason     the reason (BT-97, BT-139)
     * @return the allowance
     * @throws IllegalArgumentException if the reason is blank
     * @throws NullPointerException     if {@code reason} is {@code null}
     */
    public static Allowance percent(long percentage, String reason) {
        return percent(BigDecimal.valueOf(percentage), reason);
    }

    /**
     * An allowance of a percentage of a base amount, with the reason as a code.
     *
     * @param percentage the percentage, for example {@code 10}
     * @param reason     the reason code (BT-98, BT-140), a code of UNTDID 5189
     * @return the allowance
     * @throws NullPointerException if {@code reason} is {@code null}
     */
    public static Allowance percent(long percentage, Coded reason) {
        return percent(BigDecimal.valueOf(percentage), reason);
    }

    /**
     * An allowance of a percentage of a base amount, with the reason as a code of the generated
     * list.
     *
     * @param percentage the percentage, for example {@code 10}
     * @param reason     the reason code (BT-98, BT-140)
     * @return the allowance
     * @throws NullPointerException if {@code reason} is {@code null}
     */
    public static Allowance percent(long percentage, AllowanceReason reason) {
        return percent(percentage, (Coded) reason);
    }

    /**
     * Returns this allowance with the reason in words (BT-97, BT-139).
     *
     * @param value the reason
     * @return a new allowance
     * @throws IllegalArgumentException if the reason is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Allowance reason(String value) {
        return new Allowance(amount, percentage, base, Amounts.text(value, "value"), reasonCode,
                vat);
    }

    /**
     * Returns this allowance with the reason as a code (BT-98, BT-140).
     *
     * @param value the reason code, a code of UNTDID 5189
     * @return a new allowance
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Allowance reason(Coded value) {
        return new Allowance(amount, percentage, base, reasonText,
                Objects.requireNonNull(value, "value"), vat);
    }

    /**
     * Returns this allowance with the reason as a code of the generated list (BT-98, BT-140).
     *
     * @param value the reason code
     * @return a new allowance
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Allowance reason(AllowanceReason value) {
        return reason((Coded) value);
    }

    /**
     * Returns this allowance with the base amount its percentage is taken of (BT-93,
     * BT-137).
     *
     * @param value the base amount
     * @return a new allowance
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Allowance base(BigDecimal value) {
        return new Allowance(amount, percentage, Objects.requireNonNull(value, "value"), reasonText,
                reasonCode, vat);
    }

    /**
     * Returns this allowance with the base amount its percentage is taken of (BT-93,
     * BT-137).
     *
     * @param value the base amount as text
     * @return a new allowance
     * @throws IllegalArgumentException if the text is not a decimal number
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Allowance base(String value) {
        return base(Amounts.decimal(value, "value"));
    }

    /**
     * Returns this allowance with the VAT it is taxed under (BT-95, BT-96). Only a
     * document level allowance carries one; a line allowance is taxed with its line.
     *
     * @param value the VAT category and rate
     * @return a new allowance
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Allowance vat(Vat value) {
        return new Allowance(amount, percentage, base, reasonText, reasonCode,
                Objects.requireNonNull(value, "value"));
    }

    /**
     * Rebuilds a allowance read from a document, with whatever it states and nothing added.
     *
     * @param amount     the amount (BT-92, BT-136), or {@code null}
     * @param percentage the percentage (BT-94, BT-138), or {@code null}
     * @param base       the base amount (BT-93, BT-137), or {@code null}
     * @param reasonText the reason in words (BT-97, BT-139), or {@code null}
     * @param reasonCode the reason code (BT-98, BT-140), or {@code null}
     * @param vat        the VAT of a document level allowance, or {@code null}
     * @return the allowance
     */
    static Allowance read(BigDecimal amount, BigDecimal percentage, BigDecimal base,
                            String reasonText, Coded reasonCode, Vat vat) {
        return new Allowance(amount, percentage, base, reasonText, reasonCode, vat);
    }

    /**
     * Returns the amount (BT-92, BT-136), where the allowance states one.
     *
     * @return the amount, or an empty optional where the allowance is a percentage
     */
    public Optional<BigDecimal> amount() {
        return Optional.ofNullable(amount);
    }

    /**
     * Returns the percentage (BT-94, BT-138).
     *
     * @return the percentage, or an empty optional
     */
    public Optional<BigDecimal> percentage() {
        return Optional.ofNullable(percentage);
    }

    /**
     * Returns the base amount the percentage is taken of (BT-93, BT-137).
     *
     * @return the base amount, or an empty optional
     */
    public Optional<BigDecimal> base() {
        return Optional.ofNullable(base);
    }

    /**
     * Returns the reason in words (BT-97, BT-139).
     *
     * @return the reason, or an empty optional
     */
    public Optional<String> reason() {
        return Optional.ofNullable(reasonText);
    }

    /**
     * Returns the reason code (BT-98, BT-140).
     *
     * @return the code, or an empty optional
     */
    public Optional<Coded> reasonCode() {
        return Optional.ofNullable(reasonCode);
    }

    /**
     * Returns the VAT of a document level allowance (BT-95, BT-96).
     *
     * @return the VAT, or an empty optional
     */
    public Optional<Vat> vat() {
        return Optional.ofNullable(vat);
    }

    /**
     * Returns what the allowance deducts and why.
     *
     * @return for example {@code 10% (Quantity discount)}
     */
    @Override
    public String toString() {
        String what = amount != null ? amount.toPlainString()
                : percentage.toPlainString() + "%";
        String why = reasonText != null ? reasonText
                : reasonCode != null ? reasonCode.code() : null;
        return why == null ? what : what + " (" + why + ")";
    }
}
