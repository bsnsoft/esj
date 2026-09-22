package de.bsnsoft.esj.json;

/**
 * The canonical decimal form of a JSON number inside {@code extensions} (specification,
 * section 7.6, rule 2), obtained from the lexical form of that number by string processing
 * alone.
 *
 * <p>No numeric type takes part: the digits the sender wrote are the digits that come out,
 * shifted by the exponent and freed of the zeros that carry no information. That is why
 * ESJ never converts a number to binary floating point, here no more than anywhere else.
 *
 * <p>The form is the decimal grammar of section 6.4, and the 64-character bound is the
 * same one; inside {@code values} that grammar is a property of the semantic data type a
 * term carries and is checked at layer L2, which is why the check lives with the value and
 * only the canonicalization lives here.
 */
final class Decimals {

    /** Longest canonical decimal form the specification, section 6.4 allows. */
    static final int MAX_LENGTH = 64;

    /**
     * The magnitude an exponent is saturated at while it is accumulated. It is far below
     * {@code Long.MAX_VALUE}, so that the whole arithmetic of {@link #plain} — shifting
     * the decimal point by the exponent, and sizing the result from that position and
     * from the number of significant digits — stays exact even though every other term
     * of it is a document length and therefore bounded only by the {@code int} range. It
     * is at the same time far above the number of digits any token can carry, so that a
     * saturated exponent always pushes the canonical form past {@link #MAX_LENGTH}
     * whatever the mantissa is: a mantissa long enough to cancel it would need more than
     * {@code Long.MAX_VALUE / 8} digits, which no token holds.
     */
    private static final long EXPONENT_SATURATION = Long.MAX_VALUE / 8;

    /**
     * The largest accumulator value another digit may still be appended to. Testing this
     * before the multiplication rather than testing {@link #EXPONENT_SATURATION} after it
     * is what keeps the accumulation from stepping over the saturation and wrapping: a
     * value above it is already saturated, and a saturated accumulator stays saturated.
     */
    private static final long EXPONENT_LAST_SAFE = (EXPONENT_SATURATION - 9) / 10;

    private Decimals() {
        throw new AssertionError("no instances");
    }

    /**
     * Writes the canonical decimal form of a number given in its lexical form: the
     * spelling of a JSON number, or the spelling {@code BigDecimal.toString} produces.
     *
     * <p>The decimal point is shifted by the exponent, the leading zeros of the integer
     * part and the trailing zeros of the fraction part are removed, and the result is
     * written in plain notation without an exponent, without a plus sign, without an empty
     * fraction and without a sign on a zero.
     *
     * @param lexical the number as it is written, for example {@code 1e21} or
     *                {@code -0.0}
     * @return the canonical decimal form, or {@code null} if it would be longer than
     *         {@link #MAX_LENGTH} characters
     * @throws IllegalArgumentException if the string is not the lexical form of a number
     */
    static String canonicalize(String lexical) {
        int length = lexical.length();
        int i = 0;
        boolean negative = i < length && lexical.charAt(i) == '-';
        if (negative || (i < length && lexical.charAt(i) == '+')) {
            i++;
        }
        StringBuilder digits = new StringBuilder(length);
        int pointAfter = -1;
        while (i < length) {
            char c = lexical.charAt(i);
            if (isDigit(c)) {
                digits.append(c);
                i++;
            } else if (c == '.' && pointAfter < 0) {
                pointAfter = digits.length();
                i++;
            } else {
                break;
            }
        }
        if (digits.length() == 0) {
            throw new IllegalArgumentException("not a number: " + lexical);
        }
        long exponent = 0;
        if (i < length) {
            char c = lexical.charAt(i);
            if (c != 'e' && c != 'E') {
                throw new IllegalArgumentException("not a number: " + lexical);
            }
            exponent = parseExponent(lexical, i + 1);
        }
        if (pointAfter < 0) {
            pointAfter = digits.length();
        }
        return plain(digits, pointAfter + exponent, negative);
    }

    /**
     * Accumulates the exponent, saturating at {@link #EXPONENT_SATURATION} rather than
     * overflowing or clamping to a value a long mantissa could cancel. The saturation is
     * applied before each multiplication, so the accumulator never leaves the range in
     * which the product is exact, and it is monotone: an exponent of a million digits
     * reads as the saturation value, never as the small residue the digits would leave
     * behind if the accumulator were allowed to wrap. The digits are read to the end so
     * that a malformed exponent is still refused.
     */
    private static long parseExponent(String lexical, int from) {
        int length = lexical.length();
        int i = from;
        boolean negative = i < length && lexical.charAt(i) == '-';
        if (negative || (i < length && lexical.charAt(i) == '+')) {
            i++;
        }
        if (i >= length) {
            throw new IllegalArgumentException("not a number: " + lexical);
        }
        long value = 0;
        while (i < length) {
            char c = lexical.charAt(i);
            if (!isDigit(c)) {
                throw new IllegalArgumentException("not a number: " + lexical);
            }
            if (value <= EXPONENT_LAST_SAFE) {
                value = value * 10 + (c - '0');
            } else {
                value = EXPONENT_SATURATION;
            }
            i++;
        }
        return negative ? -value : value;
    }

    private static String plain(StringBuilder digits, long pointAfter, boolean negative) {
        int first = 0;
        while (first < digits.length() && digits.charAt(first) == '0') {
            first++;
        }
        int last = digits.length();
        while (last > first && digits.charAt(last - 1) == '0') {
            last--;
        }
        if (first == last) {
            return "0";
        }
        long point = pointAfter - first;
        int significant = last - first;
        long fractionDigits = significant - point;
        long size;
        if (fractionDigits <= 0) {
            size = point;
        } else if (fractionDigits < significant) {
            size = significant + 1L;
        } else {
            size = fractionDigits + 2L;
        }
        if (negative) {
            size++;
        }
        if (size > MAX_LENGTH) {
            return null;
        }
        StringBuilder out = new StringBuilder((int) size);
        if (negative) {
            out.append('-');
        }
        if (fractionDigits <= 0) {
            out.append(digits, first, last);
            for (long k = 0; k < -fractionDigits; k++) {
                out.append('0');
            }
        } else if (fractionDigits < significant) {
            int split = first + (int) point;
            out.append(digits, first, split).append('.').append(digits, split, last);
        } else {
            out.append("0.");
            for (long k = 0; k < fractionDigits - significant; k++) {
                out.append('0');
            }
            out.append(digits, first, last);
        }
        return out.toString();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
