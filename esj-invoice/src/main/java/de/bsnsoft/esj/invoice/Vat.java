package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.VatCategory;
import de.bsnsoft.esj.invoice.code.VatExemptionReason;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * How a line, a document level allowance or a document level charge is taxed: the VAT
 * category code (BT-151, BT-95, BT-102) and the VAT rate (BT-152, BT-96, BT-103).
 *
 * <p>One factory per category of UNTDID 5305, so that a caller states the case and not
 * the code. Every category but "not subject to VAT" is written at a rate, and the six
 * categories that levy no VAT are written at the rate zero, which is what EN 16931-1 asks
 * of them (BR-E-5, BR-Z-5, BR-AE-5, BR-IC-5, BR-G-5); the categories whose VAT breakdown
 * has to say why no VAT was charged carry the matching VATEX code, which
 * {@link #exemptionReason(Coded)} replaces and {@link #exemptionReason(String)} explains
 * in words.
 *
 * <p>The rate is of the semantic data type Percentage, which has no scale limit: it is
 * written exactly as the caller gave it and is never rounded or normalised.
 *
 * <p>The VAT is a value: every method returns a new one and the original is
 * unchanged.
 */
public final class Vat {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final Coded category;

    private final BigDecimal rate;

    private final Coded exemptionReasonCode;

    private final String exemptionReasonText;

    private Vat(Coded category, BigDecimal rate, Coded exemptionReasonCode,
                String exemptionReasonText) {
        this.category = category;
        this.rate = rate;
        this.exemptionReasonCode = exemptionReasonCode;
        this.exemptionReasonText = exemptionReasonText;
    }

    /**
     * The standard rate, category {@code S}.
     *
     * @param rate the rate in percent, at the scale the caller writes it at
     * @return the VAT
     * @throws NullPointerException if {@code rate} is {@code null}
     */
    public static Vat standard(BigDecimal rate) {
        return new Vat(VatCategory.STANDARD, Objects.requireNonNull(rate, "rate"), null, null);
    }

    /**
     * The standard rate, category {@code S}.
     *
     * @param rate the rate in percent, for example {@code 19}
     * @return the VAT
     */
    public static Vat standard(long rate) {
        return standard(BigDecimal.valueOf(rate));
    }

    /**
     * The standard rate, category {@code S}.
     *
     * @param rate the rate in percent as text, for example {@code "19.5"}
     * @return the VAT
     * @throws IllegalArgumentException if the text is not a decimal number
     * @throws NullPointerException     if {@code rate} is {@code null}
     */
    public static Vat standard(String rate) {
        return standard(Amounts.decimal(rate, "rate"));
    }

    /**
     * Zero rated goods, category {@code Z}, at the rate zero.
     *
     * @return the VAT
     */
    public static Vat zero() {
        return new Vat(VatCategory.ZERO_RATED, ZERO, null, null);
    }

    /**
     * Exempt from VAT, category {@code E}, at the rate zero, with the reason the VAT
     * breakdown has to carry (BT-121).
     *
     * @param reason the exemption reason, a code of the VATEX list
     * @return the VAT
     * @throws NullPointerException if {@code reason} is {@code null}
     */
    public static Vat exempt(Coded reason) {
        return new Vat(VatCategory.EXEMPT, ZERO, Objects.requireNonNull(reason, "reason"), null);
    }

    /**
     * Exempt from VAT, category {@code E}, at the rate zero, with a reason of the
     * generated VATEX list (BT-121).
     *
     * @param reason the exemption reason
     * @return the VAT
     * @throws NullPointerException if {@code reason} is {@code null}
     */
    public static Vat exempt(VatExemptionReason reason) {
        return exempt((Coded) reason);
    }

    /**
     * Exempt from VAT, category {@code E}, at the rate zero, with the reason in words
     * (BT-120).
     *
     * @param reason the exemption reason as text
     * @return the VAT
     * @throws IllegalArgumentException if the text is blank
     * @throws NullPointerException     if {@code reason} is {@code null}
     */
    public static Vat exempt(String reason) {
        return new Vat(VatCategory.EXEMPT, ZERO, null, Amounts.text(reason, "reason"));
    }

    /**
     * VAT reverse charge, category {@code AE}, at the rate zero, with the VATEX code for
     * the reverse charge as the reason (BT-121, BR-AE-10).
     *
     * @return the VAT
     */
    public static Vat reverseCharge() {
        return new Vat(VatCategory.REVERSE_CHARGE, ZERO, VatExemptionReason.VATEX_EU_AE, null);
    }

    /**
     * Intra-community supply, category {@code K}, at the rate zero, with the VATEX code
     * for the intra-community supply as the reason (BT-121, BR-IC-10).
     *
     * @return the VAT
     */
    public static Vat intraCommunity() {
        return new Vat(VatCategory.INTRA_COMMUNITY, ZERO, VatExemptionReason.VATEX_EU_IC, null);
    }

    /**
     * Free export item, category {@code G}, at the rate zero, with the VATEX code for the
     * export outside the EU as the reason (BT-121, BR-G-10).
     *
     * @return the VAT
     */
    public static Vat export() {
        return new Vat(VatCategory.EXPORT, ZERO, VatExemptionReason.VATEX_EU_G, null);
    }

    /**
     * Outside the scope of VAT, category {@code O}, which states no rate at all (BR-O-5),
     * with the VATEX code for the case as the reason (BT-121, BR-O-10).
     *
     * @return the VAT
     */
    public static Vat notSubject() {
        return new Vat(VatCategory.OUTSIDE_SCOPE, null, VatExemptionReason.VATEX_EU_O, null);
    }

    /**
     * Any VAT category, for a case the factories above do not name — a code of a list
     * update, or one of the two regional categories.
     *
     * @param category the VAT category code
     * @param rate     the rate in percent, or {@code null} where the category states none
     * @return the VAT
     * @throws NullPointerException if {@code category} is {@code null}
     */
    public static Vat of(Coded category, BigDecimal rate) {
        return new Vat(Objects.requireNonNull(category, "category"), rate, null, null);
    }

    /**
     * Any VAT category of the generated list, for a case the factories above do not name.
     *
     * @param category the VAT category code
     * @param rate     the rate in percent, or {@code null} where the category states none
     * @return the VAT
     * @throws NullPointerException if {@code category} is {@code null}
     */
    public static Vat of(VatCategory category, BigDecimal rate) {
        return of((Coded) category, rate);
    }

    /**
     * Returns this VAT with the exemption reason code its VAT breakdown states (BT-121).
     *
     * @param value the reason, a code of the VATEX list
     * @return a new VAT
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Vat exemptionReason(Coded value) {
        return new Vat(category, rate, Objects.requireNonNull(value, "value"), exemptionReasonText);
    }

    /**
     * Returns this VAT with an exemption reason code of the generated VATEX list (BT-121).
     *
     * @param value the reason
     * @return a new VAT
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Vat exemptionReason(VatExemptionReason value) {
        return exemptionReason((Coded) value);
    }

    /**
     * Returns this VAT with the exemption reason its VAT breakdown states in words
     * (BT-120).
     *
     * @param value the reason as text
     * @return a new VAT
     * @throws IllegalArgumentException if the text is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Vat exemptionReason(String value) {
        return new Vat(category, rate, exemptionReasonCode, Amounts.text(value, "value"));
    }

    /**
     * Returns the VAT category code (BT-151, BT-95, BT-102, BT-118).
     *
     * @return the category, a constant of {@code VatCategory} or a custom code
     */
    public Coded category() {
        return category;
    }

    /**
     * Returns the VAT rate (BT-152, BT-96, BT-103, BT-119).
     *
     * @return the rate in percent, or an empty optional where the category states none
     */
    public Optional<BigDecimal> rate() {
        return Optional.ofNullable(rate);
    }

    /**
     * Returns the VAT exemption reason code of the breakdown (BT-121).
     *
     * @return the code, or an empty optional
     */
    public Optional<Coded> exemptionReasonCode() {
        return Optional.ofNullable(exemptionReasonCode);
    }

    /**
     * Returns the VAT exemption reason of the breakdown in words (BT-120).
     *
     * @return the text, or an empty optional
     */
    public Optional<String> exemptionReasonText() {
        return Optional.ofNullable(exemptionReasonText);
    }

    /**
     * Returns the category and the rate.
     *
     * @return for example {@code S at 19}
     */
    @Override
    public String toString() {
        return rate == null ? category.code() : category.code() + " at " + rate.toPlainString();
    }

    /** The key a VAT breakdown is kept under: one breakdown per category and rate. */
    String key() {
        return category.code() + "@"
                + (rate == null ? "" : rate.stripTrailingZeros().toPlainString());
    }
}
