package de.bsnsoft.esj.invoice;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The decimal rules of EN 16931-1 section 6.5, in the one place this module rounds.
 *
 * <p>The semantic data type Amount carries at most two fraction digits (Table 26,
 * BR-DEC-*); Unit Price Amount, Quantity and Percentage are of unlimited scale (AC 8 to
 * AC 10), because a net price cut out of a gross price needs more digits than an amount
 * may have. So this module passes a price, a quantity and a percentage through at the
 * scale the caller wrote them at, and rounds half up to {@link #AMOUNT_SCALE} digits at an
 * amount it computes itself — once, at the result, never on an intermediate value.
 */
final class Amounts {

    /** The scale of the semantic data type Amount: two fraction digits (Table 26). */
    static final int AMOUNT_SCALE = 2;

    private Amounts() {
    }

    /**
     * Reads a decimal a caller wrote as text, at the scale the caller wrote it at.
     *
     * @param value what the caller wrote
     * @param what  the name of the argument, for the message of a refusal
     * @return the decimal
     * @throws IllegalArgumentException if the text is not a decimal
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    static BigDecimal decimal(String value, String what) {
        Objects.requireNonNull(value, what);
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " is a decimal number, not " + value, e);
        }
    }

    /**
     * Checks that a string a caller gave is present and not blank.
     *
     * @param value the string
     * @param what  the name of the argument, for the message of a refusal
     * @return the string
     * @throws IllegalArgumentException if it is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    static String text(String value, String what) {
        Objects.requireNonNull(value, what);
        if (value.isBlank()) {
            throw new IllegalArgumentException(what + " is not blank");
        }
        return value;
    }
}
