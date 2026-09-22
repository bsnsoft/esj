package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.ChargeReason;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * An addition to what is invoiced: a document level charge (BG-21) or a charge on one
 * invoice line (BG-28).
 *
 * <p>A charge states an amount (BT-99, BT-141) or a percentage (BT-101, BT-143) of a base
 * amount (BT-100, BT-142), and a reason in words (BT-104, BT-144), as a code (BT-105,
 * BT-145) or as both. A document level charge is taxed and therefore carries a
 * {@link Vat}; a line charge is taxed with its line and refuses one.
 *
 * <p>Where the charge is a percentage, the amount is that percentage of the base amount,
 * exact in {@code BigDecimal} and rounded half up to two decimals once, at the amount. A
 * line charge that states no base amount takes the line's own net base — the invoiced
 * quantity times the item net price over the item price base quantity — and writes it as
 * BT-142, so that the invoice says what the percentage was taken of. A document level
 * charge has no such base and refuses a percentage without one.
 *
 * <p>The charge is a value: every method returns a new one and the original is
 * unchanged.
 */
public final class Charge {

    private final BigDecimal amount;

    private final BigDecimal percentage;

    private final BigDecimal base;

    private final String reasonText;

    private final Coded reasonCode;

    private final Vat vat;

    private Charge(BigDecimal amount, BigDecimal percentage, BigDecimal base, String reasonText,
                      Coded reasonCode, Vat vat) {
        this.amount = amount;
        this.percentage = percentage;
        this.base = base;
        this.reasonText = reasonText;
        this.reasonCode = reasonCode;
        this.vat = vat;
    }

    /**
     * A charge of a fixed amount, with the reason in words.
     *
     * @param amount the amount added, in the currency of the invoice
     * @param reason the reason (BT-104, BT-144)
     * @return the charge
     * @throws IllegalArgumentException if the reason is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Charge amount(BigDecimal amount, String reason) {
        return new Charge(Objects.requireNonNull(amount, "amount"), null, null,
                Amounts.text(reason, "reason"), null, null);
    }

    /**
     * A charge of a fixed amount, with the reason as a code.
     *
     * @param amount the amount added, in the currency of the invoice
     * @param reason the reason code (BT-105, BT-145), a code of UNTDID 7161
     * @return the charge
     * @throws NullPointerException if a part is {@code null}
     */
    public static Charge amount(BigDecimal amount, Coded reason) {
        return new Charge(Objects.requireNonNull(amount, "amount"), null, null, null,
                Objects.requireNonNull(reason, "reason"), null);
    }

    /**
     * A charge of a fixed amount, with the reason as a code of the generated list.
     *
     * @param amount the amount added, in the currency of the invoice
     * @param reason the reason code (BT-105, BT-145)
     * @return the charge
     * @throws NullPointerException if a part is {@code null}
     */
    public static Charge amount(BigDecimal amount, ChargeReason reason) {
        return amount(amount, (Coded) reason);
    }

    /**
     * A charge of a fixed amount, with the reason in words.
     *
     * @param amount the amount as text, for example {@code "12.50"}
     * @param reason the reason (BT-104, BT-144)
     * @return the charge
     * @throws IllegalArgumentException if the amount is not a decimal or the reason is
     *                                  blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Charge amount(String amount, String reason) {
        return amount(Amounts.decimal(amount, "amount"), reason);
    }

    /**
     * A charge of a fixed amount, with the reason as a code.
     *
     * @param amount the amount as text, for example {@code "12.50"}
     * @param reason the reason code (BT-105, BT-145), a code of UNTDID 7161
     * @return the charge
     * @throws IllegalArgumentException if the amount is not a decimal
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Charge amount(String amount, Coded reason) {
        return amount(Amounts.decimal(amount, "amount"), reason);
    }

    /**
     * A charge of a fixed amount, with the reason as a code of the generated list.
     *
     * @param amount the amount as text, for example {@code "25"}
     * @param reason the reason code (BT-105, BT-145)
     * @return the charge
     * @throws IllegalArgumentException if the amount is not a decimal
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Charge amount(String amount, ChargeReason reason) {
        return amount(amount, (Coded) reason);
    }

    /**
     * A charge of a percentage of a base amount, with the reason in words.
     *
     * @param percentage the percentage (BT-101, BT-143), at the scale the caller writes it
     * @param reason     the reason (BT-104, BT-144)
     * @return the charge
     * @throws IllegalArgumentException if the reason is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Charge percent(BigDecimal percentage, String reason) {
        return new Charge(null, Objects.requireNonNull(percentage, "percentage"), null,
                Amounts.text(reason, "reason"), null, null);
    }

    /**
     * A charge of a percentage of a base amount, with the reason as a code.
     *
     * @param percentage the percentage (BT-101, BT-143), at the scale the caller writes it
     * @param reason     the reason code (BT-105, BT-145), a code of UNTDID 7161
     * @return the charge
     * @throws NullPointerException if a part is {@code null}
     */
    public static Charge percent(BigDecimal percentage, Coded reason) {
        return new Charge(null, Objects.requireNonNull(percentage, "percentage"), null, null,
                Objects.requireNonNull(reason, "reason"), null);
    }

    /**
     * A charge of a percentage of a base amount, with the reason as a code of the generated
     * list.
     *
     * @param percentage the percentage (BT-101, BT-143), at the scale the caller writes it
     * @param reason     the reason code (BT-105, BT-145)
     * @return the charge
     * @throws NullPointerException if a part is {@code null}
     */
    public static Charge percent(BigDecimal percentage, ChargeReason reason) {
        return percent(percentage, (Coded) reason);
    }

    /**
     * A charge of a percentage of a base amount, with the reason in words.
     *
     * @param percentage the percentage, for example {@code 10}
     * @param reason     the reason (BT-104, BT-144)
     * @return the charge
     * @throws IllegalArgumentException if the reason is blank
     * @throws NullPointerException     if {@code reason} is {@code null}
     */
    public static Charge percent(long percentage, String reason) {
        return percent(BigDecimal.valueOf(percentage), reason);
    }

    /**
     * A charge of a percentage of a base amount, with the reason as a code.
     *
     * @param percentage the percentage, for example {@code 10}
     * @param reason     the reason code (BT-105, BT-145), a code of UNTDID 7161
     * @return the charge
     * @throws NullPointerException if {@code reason} is {@code null}
     */
    public static Charge percent(long percentage, Coded reason) {
        return percent(BigDecimal.valueOf(percentage), reason);
    }

    /**
     * A charge of a percentage of a base amount, with the reason as a code of the generated
     * list.
     *
     * @param percentage the percentage, for example {@code 10}
     * @param reason     the reason code (BT-105, BT-145)
     * @return the charge
     * @throws NullPointerException if {@code reason} is {@code null}
     */
    public static Charge percent(long percentage, ChargeReason reason) {
        return percent(percentage, (Coded) reason);
    }

    /**
     * Returns this charge with the reason in words (BT-104, BT-144).
     *
     * @param value the reason
     * @return a new charge
     * @throws IllegalArgumentException if the reason is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Charge reason(String value) {
        return new Charge(amount, percentage, base, Amounts.text(value, "value"), reasonCode,
                vat);
    }

    /**
     * Returns this charge with the reason as a code (BT-105, BT-145).
     *
     * @param value the reason code, a code of UNTDID 7161
     * @return a new charge
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Charge reason(Coded value) {
        return new Charge(amount, percentage, base, reasonText,
                Objects.requireNonNull(value, "value"), vat);
    }

    /**
     * Returns this charge with the reason as a code of the generated list (BT-105, BT-145).
     *
     * @param value the reason code
     * @return a new charge
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Charge reason(ChargeReason value) {
        return reason((Coded) value);
    }

    /**
     * Returns this charge with the base amount its percentage is taken of (BT-100,
     * BT-142).
     *
     * @param value the base amount
     * @return a new charge
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Charge base(BigDecimal value) {
        return new Charge(amount, percentage, Objects.requireNonNull(value, "value"), reasonText,
                reasonCode, vat);
    }

    /**
     * Returns this charge with the base amount its percentage is taken of (BT-100,
     * BT-142).
     *
     * @param value the base amount as text
     * @return a new charge
     * @throws IllegalArgumentException if the text is not a decimal number
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Charge base(String value) {
        return base(Amounts.decimal(value, "value"));
    }

    /**
     * Returns this charge with the VAT it is taxed under (BT-102, BT-103). Only a
     * document level charge carries one; a line charge is taxed with its line.
     *
     * @param value the VAT category and rate
     * @return a new charge
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Charge vat(Vat value) {
        return new Charge(amount, percentage, base, reasonText, reasonCode,
                Objects.requireNonNull(value, "value"));
    }

    /**
     * Rebuilds a charge read from a document, with whatever it states and nothing added.
     *
     * @param amount     the amount (BT-99, BT-141), or {@code null}
     * @param percentage the percentage (BT-101, BT-143), or {@code null}
     * @param base       the base amount (BT-100, BT-142), or {@code null}
     * @param reasonText the reason in words (BT-104, BT-144), or {@code null}
     * @param reasonCode the reason code (BT-105, BT-145), or {@code null}
     * @param vat        the VAT of a document level charge, or {@code null}
     * @return the charge
     */
    static Charge read(BigDecimal amount, BigDecimal percentage, BigDecimal base,
                            String reasonText, Coded reasonCode, Vat vat) {
        return new Charge(amount, percentage, base, reasonText, reasonCode, vat);
    }

    /**
     * Returns the amount (BT-99, BT-141), where the charge states one.
     *
     * @return the amount, or an empty optional where the charge is a percentage
     */
    public Optional<BigDecimal> amount() {
        return Optional.ofNullable(amount);
    }

    /**
     * Returns the percentage (BT-101, BT-143).
     *
     * @return the percentage, or an empty optional
     */
    public Optional<BigDecimal> percentage() {
        return Optional.ofNullable(percentage);
    }

    /**
     * Returns the base amount the percentage is taken of (BT-100, BT-142).
     *
     * @return the base amount, or an empty optional
     */
    public Optional<BigDecimal> base() {
        return Optional.ofNullable(base);
    }

    /**
     * Returns the reason in words (BT-104, BT-144).
     *
     * @return the reason, or an empty optional
     */
    public Optional<String> reason() {
        return Optional.ofNullable(reasonText);
    }

    /**
     * Returns the reason code (BT-105, BT-145).
     *
     * @return the code, or an empty optional
     */
    public Optional<Coded> reasonCode() {
        return Optional.ofNullable(reasonCode);
    }

    /**
     * Returns the VAT of a document level charge (BT-102, BT-103).
     *
     * @return the VAT, or an empty optional
     */
    public Optional<Vat> vat() {
        return Optional.ofNullable(vat);
    }

    /**
     * Returns what the charge adds and why.
     *
     * @return for example {@code 12.50 (Freight service)}
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
