package de.bsnsoft.esj;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZoneOffset;

/**
 * The grammars the registry data types require of the content of a value: the canonical
 * decimal form of the specification, section 6.4, the calendar date and the time of day of
 * section 6.5, and the canonical base64 of section 6.7.
 *
 * <p>Which of them applies at a business term is decided by the registry, so a document
 * is held to them at layer L2 and not at L1 (specification, section 6.2). They live here,
 * beside the value, rather than in the validator, so that the typed accessors of
 * {@link SemanticValue} and the validator ask one question and give one answer.
 *
 * <p>Each grammar comes with a phrase naming the one clause a string breaks. The phrase
 * follows the offending content in a message and says what is wrong rather than restating
 * the rule.
 */
final class Grammars {

    /** Longest decimal content the specification, section 6.4 allows, sign and point included. */
    static final int MAX_DECIMAL_LENGTH = 64;

    /** The length of the clock part of a time, {@code hh:mm:ss} (specification, section 6.5). */
    private static final int CLOCK_LENGTH = 8;

    /** The largest offset from UTC the specification, section 6.5 admits, in hours. */
    private static final int MAX_OFFSET_HOURS = 14;

    /** The longest fragment of content a message reproduces (specification, section 12.6). */
    private static final int MESSAGE_EXCERPT = 80;

    private Grammars() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns a fragment of content in the form a message may carry it.
     *
     * @param value the content
     * @return the escaped, truncated fragment
     */
    static String excerpt(String value) {
        return Esj.forMessage(value, MESSAGE_EXCERPT);
    }

    /**
     * Tells whether a string is a decimal in the canonical form of the specification,
     * section 6.4: an optional minus sign, an integer part without leading zeros, an
     * optional fraction part without trailing zeros, no exponent and no sign on a zero.
     * The length bound is a separate clause and is not checked here.
     *
     * @param value the string to test
     * @return {@code true} if the string matches the canonical decimal grammar
     */
    static boolean isDecimal(String value) {
        int i = 0;
        int length = value.length();
        boolean negative = i < length && value.charAt(i) == '-';
        if (negative) {
            i++;
        }
        int intStart = i;
        if (i >= length || !isDigit(value.charAt(i))) {
            return false;
        }
        if (value.charAt(i) == '0') {
            i++;
        } else {
            while (i < length && isDigit(value.charAt(i))) {
                i++;
            }
        }
        boolean intIsZero = i - intStart == 1 && value.charAt(intStart) == '0';
        boolean fractionIsZero = true;
        if (i < length) {
            if (value.charAt(i) != '.') {
                return false;
            }
            i++;
            int fractionStart = i;
            while (i < length && isDigit(value.charAt(i))) {
                if (value.charAt(i) != '0') {
                    fractionIsZero = false;
                }
                i++;
            }
            if (i != length || i == fractionStart || value.charAt(length - 1) == '0') {
                return false;
            }
        }
        return !(negative && intIsZero && fractionIsZero);
    }

    /**
     * Names the one clause of the decimal grammar a string breaks, written to follow the
     * offending content.
     *
     * @param value a string that is not a decimal of an acceptable length
     * @return the phrase, or {@code null} if the string is a canonical decimal after all
     */
    static String decimalViolation(String value) {
        if (value.length() > MAX_DECIMAL_LENGTH) {
            return "is " + value.length() + " characters long; a decimal has at most "
                    + MAX_DECIMAL_LENGTH;
        }
        if (isDecimal(value)) {
            return null;
        }
        if (value.isEmpty()) {
            return "is empty; a decimal carries at least one digit";
        }
        if (value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
            return "is written with an exponent, which a decimal never carries";
        }
        if (value.charAt(0) == '+') {
            return "has a leading plus sign, which a decimal never carries";
        }
        if (value.charAt(value.length() - 1) == '.') {
            return "ends in a decimal point, and a fraction part carries at least one digit";
        }
        if (isSignedZero(value)) {
            return "signs a zero, which is written 0";
        }
        if (hasLeadingZero(value)) {
            return "has a leading zero in its integer part";
        }
        if (value.indexOf('.') >= 0 && value.charAt(value.length() - 1) == '0') {
            return "has trailing zeros in its fraction, which carry no numeric information";
        }
        return "is not written in the canonical decimal form of a decimal";
    }

    /**
     * Returns the date a string spells, if it spells one of the calendar.
     *
     * @param value the string to read
     * @return the date, or {@code null} if the string is outside the grammar of the
     *         specification, section 6.5 or names a day that does not exist
     */
    static LocalDate parseDate(String value) {
        if (!hasDateShape(value) || value.charAt(0) == '0') {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(value, 0, 4, 10),
                    Integer.parseInt(value, 5, 7, 10),
                    Integer.parseInt(value, 8, 10, 10));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /**
     * Names the one clause of the date grammar a string breaks, written to follow the
     * offending content.
     *
     * @param value a string that {@link #parseDate(String)} does not read
     * @return the phrase
     */
    static String dateViolation(String value) {
        if (!hasDateShape(value)) {
            return "is not a date written YYYY-MM-DD";
        }
        if (value.charAt(0) == '0') {
            return "has a year below 1000; a date carries a year from 1000 to 9999";
        }
        return "names a day that does not exist in the calendar";
    }

    /**
     * Returns the time of day a string spells, if it spells one of the grammar of the
     * specification, section 6.5.
     *
     * <p>The grammar is narrower than ISO 8601-1:2019 on purpose, so that one time has one
     * spelling: the offset is always present, UTC is written {@code Z}, and there are no
     * fractional seconds, no {@code 24:00:00} and no leap second.
     *
     * @param value the string to read
     * @return the time, or {@code null} if the string is outside the grammar
     */
    static OffsetTime parseTime(String value) {
        if (!hasTimeShape(value)) {
            return null;
        }
        try {
            return OffsetTime.of(field(value, 0), field(value, 3), field(value, 6), 0,
                    offset(value));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /**
     * Returns the one spelling the grammar of the specification, section 6.5 has for a
     * time of day: two digits per field, UTC written {@code Z}, and every other offset
     * written to the minute.
     *
     * @param value a time whose second is its last field and whose offset is a whole
     *              number of minutes
     * @return the content of a value of the semantic data type Time
     */
    static String printTime(OffsetTime value) {
        StringBuilder out = new StringBuilder(14);
        two(out, value.getHour()).append(':');
        two(out, value.getMinute()).append(':');
        two(out, value.getSecond());
        int seconds = value.getOffset().getTotalSeconds();
        if (seconds == 0) {
            return out.append('Z').toString();
        }
        int minutes = Math.abs(seconds) / 60;
        out.append(seconds < 0 ? '-' : '+');
        two(out, minutes / 60).append(':');
        return two(out, minutes % 60).toString();
    }

    /** Appends a field of the clock or of the offset, padded to two digits. */
    private static StringBuilder two(StringBuilder out, int field) {
        if (field < 10) {
            out.append('0');
        }
        return out.append(field);
    }

    /**
     * Names the one clause of the time grammar a string breaks, written to follow the
     * offending content.
     *
     * @param value a string that {@link #parseTime(String)} does not read
     * @return the phrase
     */
    static String timeViolation(String value) {
        if (!hasClockShape(value)) {
            return "is not a time of day written hh:mm:ss";
        }
        if (field(value, 0) > 23) {
            return "names the hour " + value.substring(0, 2)
                    + "; the last time of a day is 23:59:59";
        }
        if (field(value, 3) > 59) {
            return "names the minute " + value.substring(3, 5);
        }
        if (field(value, 6) == 60) {
            return "names a leap second, which a time never carries";
        }
        if (field(value, 6) > 59) {
            return "names the second " + value.substring(6, 8);
        }
        if (value.length() == CLOCK_LENGTH) {
            return "carries no offset; a time is written Z or with an offset such as +02:00";
        }
        if (value.charAt(CLOCK_LENGTH) == '.' || value.charAt(CLOCK_LENGTH) == ',') {
            return "carries fractional seconds, which a time never does";
        }
        if (hasOffsetShape(value)) {
            if (isZeroOffset(value)) {
                return "writes UTC as an offset of zero, which is written Z";
            }
            return "has the offset " + value.substring(CLOCK_LENGTH)
                    + ", and an offset lies between -" + MAX_OFFSET_HOURS + ":00 and +"
                    + MAX_OFFSET_HOURS + ":00";
        }
        return "carries no offset written Z, +hh:mm or -hh:mm";
    }

    /**
     * Names the clause of the time grammar an offset from UTC breaks, written to follow the
     * offending value, or {@code null} where the offset is one section 6.5 admits.
     *
     * <p>{@link ZoneOffset} reaches eighteen hours and the grammar stops at fourteen, so a
     * writer that takes an offset from the caller asks this before it prints one.
     *
     * @param offset the offset of a time a writer was handed
     * @return the phrase, or {@code null}
     */
    static String offsetViolation(ZoneOffset offset) {
        if (Math.abs(offset.getTotalSeconds()) <= MAX_OFFSET_HOURS * 3600) {
            return null;
        }
        return "has the offset " + offset.getId() + ", and an offset lies between -"
                + MAX_OFFSET_HOURS + ":00 and +" + MAX_OFFSET_HOURS + ":00";
    }

    /** Tells whether a string begins with two-digit hours, minutes and seconds. */
    private static boolean hasClockShape(String value) {
        if (value.length() < CLOCK_LENGTH || value.charAt(2) != ':' || value.charAt(5) != ':') {
            return false;
        }
        for (int i = 0; i < CLOCK_LENGTH; i++) {
            if (i != 2 && i != 5 && !isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Tells whether what follows the clock is an offset written {@code +hh:mm} or {@code -hh:mm}. */
    private static boolean hasOffsetShape(String value) {
        if (value.length() != CLOCK_LENGTH + 6
                || (value.charAt(CLOCK_LENGTH) != '+' && value.charAt(CLOCK_LENGTH) != '-')
                || value.charAt(CLOCK_LENGTH + 3) != ':') {
            return false;
        }
        for (int i = CLOCK_LENGTH + 1; i < value.length(); i++) {
            if (i != CLOCK_LENGTH + 3 && !isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Tells whether the offset of a string is written as a number and is zero. */
    private static boolean isZeroOffset(String value) {
        return hasOffsetShape(value) && field(value, CLOCK_LENGTH + 1) == 0
                && field(value, CLOCK_LENGTH + 4) == 0;
    }

    private static boolean hasTimeShape(String value) {
        if (!hasClockShape(value) || field(value, 0) > 23 || field(value, 3) > 59
                || field(value, 6) > 59) {
            return false;
        }
        if (value.length() == CLOCK_LENGTH + 1 && value.charAt(CLOCK_LENGTH) == 'Z') {
            return true;
        }
        return hasOffsetShape(value) && !isZeroOffset(value) && isCivilOffset(value);
    }

    /**
     * Tells whether the offset of a string of offset shape lies between {@code -14:00} and
     * {@code +14:00}, which is the rule XML Schema uses for a time zone.
     */
    private static boolean isCivilOffset(String value) {
        int hours = field(value, CLOCK_LENGTH + 1);
        int minutes = field(value, CLOCK_LENGTH + 4);
        return hours < MAX_OFFSET_HOURS ? minutes <= 59 : hours == MAX_OFFSET_HOURS && minutes == 0;
    }

    /** Reads the two digits at an index as a number. */
    private static int field(String value, int at) {
        return Integer.parseInt(value, at, at + 2, 10);
    }

    private static ZoneOffset offset(String value) {
        if (value.length() == CLOCK_LENGTH + 1) {
            return ZoneOffset.UTC;
        }
        int seconds = field(value, CLOCK_LENGTH + 1) * 3600 + field(value, CLOCK_LENGTH + 4) * 60;
        return ZoneOffset.ofTotalSeconds(value.charAt(CLOCK_LENGTH) == '-' ? -seconds : seconds);
    }

    /**
     * Tells whether a string is a canonical, padded base64 encoding in the standard
     * alphabet of RFC 4648, section 4, whose last quantum has zero pad bits
     * (specification, section 6.7).
     *
     * @param value the string to test
     * @return {@code true} if the string is canonical base64
     */
    static boolean isBase64(String value) {
        int length = value.length();
        if (length == 0 || length % 4 != 0) {
            return false;
        }
        int end = length;
        int padding = 0;
        while (end > 0 && value.charAt(end - 1) == '=') {
            end--;
            padding++;
        }
        if (padding > 2) {
            return false;
        }
        for (int i = 0; i < end; i++) {
            if (base64Value(value.charAt(i)) < 0) {
                return false;
            }
        }
        if (padding == 0) {
            return true;
        }
        int tail = base64Value(value.charAt(end - 1));
        int padBits = padding == 1 ? 2 : 4;
        return (tail & ((1 << padBits) - 1)) == 0;
    }

    /**
     * Names the one clause of the base64 grammar a string breaks.
     *
     * <p>The phrase never reproduces the string: an attachment is the one content of a
     * document that is both long and, by construction, not readable text (specification,
     * section 12.6).
     *
     * @param value a string that {@link #isBase64(String)} rejects
     * @return the phrase, or {@code null} if the string is canonical base64 after all
     */
    static String base64Violation(String value) {
        int length = value.length();
        if (length == 0) {
            return "is empty";
        }
        if (length % 4 != 0) {
            return "is " + length + " characters long, which is not a multiple of four:"
                    + " padded base64 is written in quanta of four characters";
        }
        int end = length;
        int padding = 0;
        while (end > 0 && value.charAt(end - 1) == '=') {
            end--;
            padding++;
        }
        if (padding > 2) {
            return "ends with " + padding + " padding characters, and base64 padding is"
                    + " one or two";
        }
        for (int i = 0; i < end; i++) {
            char c = value.charAt(i);
            if (base64Value(c) < 0) {
                return Character.isWhitespace(c)
                        ? "carries whitespace, and base64 is written without line breaks"
                                + " and without whitespace"
                        : "carries a character that is not in the standard base64 alphabet"
                                + " of RFC 4648, section 4";
            }
        }
        if (padding > 0) {
            int tail = base64Value(value.charAt(end - 1));
            int padBits = padding == 1 ? 2 : 4;
            if ((tail & ((1 << padBits) - 1)) != 0) {
                return "sets pad bits in its last quantum, which a canonical encoding"
                        + " leaves at zero";
            }
        }
        return null;
    }

    /** Tells whether a string is ten characters in the shape {@code NNNN-NN-NN}. */
    private static boolean hasDateShape(String value) {
        if (value.length() != 10 || value.charAt(4) != '-' || value.charAt(7) != '-') {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (i == 4 || i == 7) {
                continue;
            }
            if (!isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Tells whether a string spells a zero and carries a minus sign. */
    private static boolean isSignedZero(String value) {
        if (value.charAt(0) != '-') {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '0' && c != '.') {
                return false;
            }
        }
        return value.length() > 1;
    }

    private static boolean hasLeadingZero(String value) {
        int i = value.charAt(0) == '-' ? 1 : 0;
        return i + 1 < value.length() && value.charAt(i) == '0' && isDigit(value.charAt(i + 1));
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int base64Value(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 26;
        }
        if (c >= '0' && c <= '9') {
            return c - '0' + 52;
        }
        if (c == '+') {
            return 62;
        }
        if (c == '/') {
            return 63;
        }
        return -1;
    }
}
