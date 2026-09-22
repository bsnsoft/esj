package de.bsnsoft.esj;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Shared checks and conversions for {@link SemanticValue} and for the provenance metadata
 * of a document: the string rules of the specification, section 6.1, the line ending
 * normalization of section 6.8 and the canonical decimal form of section 6.4.
 */
final class ValueSupport {

    private ValueSupport() {
        throw new AssertionError("no instances");
    }

    /**
     * Normalizes line endings and requires a non-empty result.
     *
     * @param value the string to check
     * @param what  the name of the member, for the message
     * @return the normalized string
     * @throws EsjFormatException   if the string is empty
     * @throws NullPointerException if {@code value} is {@code null}
     */
    static String required(String value, String what) {
        Objects.requireNonNull(value, what);
        String normalized = Esj.normalizeLineEndings(value);
        if (normalized.isEmpty()) {
            throw new EsjFormatException(what + " is a non-empty string");
        }
        return normalized;
    }

    /**
     * Normalizes line endings of a member that may be absent, and requires a non-empty
     * result when it is present.
     *
     * @param value the string to check, or {@code null}
     * @param what  the name of the member, for the message
     * @return the normalized string, or {@code null}
     * @throws EsjFormatException if the string is present and empty
     */
    static String optional(String value, String what) {
        return value == null ? null : required(value, what);
    }

    /**
     * Returns the decimal in the one form the specification, section 6.4 gives its value:
     * trailing fraction zeros removed, and a scale that is never negative, so that the
     * plain notation of the result is the canonical string and nothing else is.
     *
     * <p>Normalizing here is what makes {@code 100.00} and {@code 100} one value: a
     * writer handed either of them writes the same content, so the two have the same
     * canonical bytes and the same digests (specification, section 3.3). It is the only
     * place in this implementation where a spelling is chosen; nothing downstream of a
     * written document ever changes one (section 6.4).
     *
     * @param value the decimal
     * @param what  the name of the value, for the message
     * @return the normalized decimal
     * @throws EsjFormatException   if the canonical form is longer than 64 characters
     * @throws NullPointerException if {@code value} is {@code null}
     */
    static BigDecimal canonicalNumber(BigDecimal value, String what) {
        Objects.requireNonNull(value, what);
        BigDecimal stripped = value.stripTrailingZeros();
        long length = plainLength(stripped);
        if (length > Grammars.MAX_DECIMAL_LENGTH) {
            throw new EsjFormatException(
                    what + " has a canonical decimal form of " + length
                            + " characters, which exceeds the bound of " + Grammars.MAX_DECIMAL_LENGTH);
        }
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /**
     * Writes a normalized decimal in the canonical form of the specification,
     * section 6.4: plain notation, no exponent, no leading zeros in the integer part, no
     * trailing zeros in the fraction part and no negative zero, which a
     * {@link BigDecimal} cannot hold in the first place.
     *
     * @param value a decimal {@link #canonicalNumber(BigDecimal, String)} returned
     * @return the canonical decimal string
     */
    static String canonicalDecimal(BigDecimal value) {
        return value.toPlainString();
    }

    /**
     * Returns the length the plain notation of a decimal will have, without writing it
     * out. A decimal with a large exponent would otherwise be expanded to a string of
     * millions of digits only to be rejected for being too long.
     *
     * @param value the decimal, with its trailing fraction zeros already removed
     * @return the number of characters the plain notation takes, including the sign
     */
    private static long plainLength(BigDecimal value) {
        int precision = value.precision();
        int scale = value.scale();
        long length;
        if (scale <= 0) {
            length = (long) precision - scale;
        } else if (precision > scale) {
            length = precision + 1L;
        } else {
            length = scale + 2L;
        }
        return value.signum() < 0 ? length + 1 : length;
    }
}
