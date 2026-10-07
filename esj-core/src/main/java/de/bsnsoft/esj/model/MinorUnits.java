package de.bsnsoft.esj.model;

import de.bsnsoft.esj.Preview;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The minor unit of each currency, as one dated snapshot of the currency list states it, and
 * the number of fraction digits a term allows that follows from it.
 *
 * <p>A registry may state the fraction digit bound of a term as a rule over the currency in
 * use rather than as a constant ({@link Term#maxDecimalsRule()}). Which minor unit a currency
 * has is not a fact of the registry: the currency list changes, and a rule pack dates the
 * snapshot it decides against. This class carries no list of its own. It holds the numbers a
 * caller hands it — a rule pack's snapshot, read by whoever owns it — together with a
 * description of where they came from, and it answers the one question a policy that rounds
 * an amount has to ask: how many fraction digits this term allows in this currency.
 *
 * <p>A currency the snapshot gives no minor unit is answered with nothing. Nothing here
 * guesses a number: a caller that needs one refuses by the name of the currency.
 *
 * <p>The class is a preview: it serves the edition whose fraction digit bounds follow the
 * currency, and it may change in any minor release.
 */
@Preview
public final class MinorUnits {

    /** The rule that bounds a term at the minor unit of the currency in use. */
    public static final String MINOR_UNIT = "iso4217-minor-unit";

    /** The rule that bounds a term at the minor unit of the currency in use plus two. */
    public static final String MINOR_UNIT_PLUS_2 = "iso4217-minor-unit-plus-2";

    private final Map<String, Integer> byCurrency;

    private final String source;

    private MinorUnits(Map<String, Integer> byCurrency, String source) {
        this.byCurrency = byCurrency;
        this.source = source;
    }

    /**
     * Collects minor units that are already in hand.
     *
     * @param byCurrency the minor unit of each currency, keyed by the alphabetic code the
     *                   currency list gives it; a currency the list gives no minor unit is
     *                   left out
     * @param source     where the numbers come from, as a short phrase naming the list and
     *                   its day, for the message of a refusal
     * @return the minor units
     * @throws IllegalArgumentException if a minor unit is negative or the source is blank
     * @throws NullPointerException     if an argument, a key or a value is {@code null}
     */
    public static MinorUnits of(Map<String, Integer> byCurrency, String source) {
        Objects.requireNonNull(byCurrency, "byCurrency");
        Objects.requireNonNull(source, "source");
        if (source.isBlank()) {
            throw new IllegalArgumentException("the source of the minor units is blank");
        }
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : byCurrency.entrySet()) {
            String currency = Objects.requireNonNull(entry.getKey(), "currency");
            int digits = Objects.requireNonNull(entry.getValue(), "minor unit of " + currency);
            if (digits < 0) {
                throw new IllegalArgumentException("the minor unit of " + currency
                        + " is " + digits + ", and a number of fraction digits is not negative");
            }
            copy.put(currency, digits);
        }
        return new MinorUnits(Map.copyOf(copy), source);
    }

    /**
     * Returns where these minor units come from.
     *
     * @return the phrase the caller gave, naming the list and its day
     */
    public String source() {
        return source;
    }

    /**
     * Returns the minor unit of a currency.
     *
     * @param currency the alphabetic currency code as the document spells it; the
     *                 comparison is exact
     * @return the number of fraction digits of the currency, or an empty optional where the
     *         snapshot gives it none
     * @throws NullPointerException if {@code currency} is {@code null}
     */
    public OptionalInt minorUnit(String currency) {
        Integer digits = byCurrency.get(Objects.requireNonNull(currency, "currency"));
        return digits == null ? OptionalInt.empty() : OptionalInt.of(digits);
    }

    /**
     * Returns how many fraction digits a term allows for a value in a currency.
     *
     * @param term     the term, as its registry records it
     * @param currency the alphabetic code of the currency the value is written in
     * @return the constant the registry records, or the number its rule gives in that
     *         currency; an empty optional where the registry states no bound for the term or
     *         where the bound follows a currency the snapshot gives no minor unit
     * @throws IllegalArgumentException if the registry states a rule this class does not know
     * @throws NullPointerException     if an argument is {@code null}
     */
    public OptionalInt fractionDigits(Term term, String currency) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(currency, "currency");
        if (term.maxDecimals().isPresent()) {
            return term.maxDecimals();
        }
        Optional<String> rule = term.maxDecimalsRule();
        if (rule.isEmpty()) {
            return OptionalInt.empty();
        }
        OptionalInt minor = minorUnit(currency);
        if (minor.isEmpty()) {
            return minor;
        }
        return switch (rule.get()) {
            case MINOR_UNIT -> minor;
            case MINOR_UNIT_PLUS_2 -> OptionalInt.of(minor.getAsInt() + 2);
            default -> throw new IllegalArgumentException("the registry bounds " + term.id()
                    + " by the rule " + rule.get() + ", which is not one of " + MINOR_UNIT
                    + " and " + MINOR_UNIT_PLUS_2);
        };
    }

    /**
     * Returns the source and the number of currencies.
     *
     * @return a short description of these minor units
     */
    @Override
    public String toString() {
        return "minor units of " + byCurrency.size() + " currencies (" + source + ")";
    }
}
