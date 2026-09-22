package de.bsnsoft.esj.render;

import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.SemanticType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * How the PDF rendering writes a value down for a reader.
 *
 * <p>A document spells its values canonically — {@code 1234.5} and {@code 2026-12-31} —
 * and a reader is not shown a canonical decimal. This class is the one place that turns a
 * canonical value into the form the rendering carries, and it is deliberately small and
 * deliberately its own: it uses no locale of the machine and no shared formatter, because
 * a rendering that depended on either would differ between two machines that ran the same
 * document.
 *
 * <h2>The pictures</h2>
 *
 * <ul>
 *   <li>a decimal is grouped in threes: {@code 1.234,56} in German, {@code 1,234.56} in
 *       English;</li>
 *   <li>an amount, a unit price and a percentage carry at least two decimal places, and
 *       all of them where the document writes more; a quantity carries exactly the
 *       decimal places the document writes;</li>
 *   <li>a percentage is followed by a per-cent sign;</li>
 *   <li>a percentage of the letter layout carries the decimal places the document wrote
 *       and no others — {@link #asWritten(SemanticType, String, RenderLanguage)};</li>
 *   <li>a date is {@code 31.12.2026} in German and {@code 2026-12-31} — the canonical
 *       spelling itself — in English.</li>
 * </ul>
 *
 * <p>A value whose content is not of the shape its semantic data type asks for is written
 * as it stands. A renderer is not a validator: an invoice with a date that is not a date
 * still has to be readable, and this project has a validator that says what is wrong with
 * it. A decimal whose exponent asks for more digits than {@link #MAXIMUM_DIGITS} is
 * treated the same way, because writing it out is work no rendering is worth.
 */
final class Formats {

    /** The smallest number of decimal places an amount, a unit price or a rate is shown with. */
    private static final int MINIMUM_DECIMALS = 2;

    /**
     * The most digits a decimal is written out with before it is printed as it stands.
     *
     * <p>A decimal of the semantic model carries an exponent in its lexical form, and
     * {@code 1E+2000000000} is a decimal by that grammar: it parses, and writing it out
     * would ask for two thousand million digits. No reader reads such a number and no
     * invoice states one, so beyond this many digits the content is written the way every
     * other content that is not of the shape its type asks for is written — as it stands.
     * The bound is far above any figure of an invoice and far below what costs a page,
     * a second or a heap.
     */
    private static final int MAXIMUM_DIGITS = 2000;

    private Formats() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the form a value of a semantic data type takes in the rendering.
     *
     * @param type     the semantic data type of the term, as the registry gives it
     * @param content  the content of the value, as the document spells it
     * @param language the language the rendering is in
     * @return the text to show
     */
    static String value(SemanticType type, String content, RenderLanguage language) {
        return switch (type) {
            case DATE -> date(content, language);
            case AMOUNT, UNIT_PRICE_AMOUNT -> decimal(content, MINIMUM_DECIMALS, language);
            case PERCENTAGE -> percentage(content, language);
            case QUANTITY -> decimal(content, 0, language);
            default -> text(content);
        };
    }

    /**
     * Returns the form a value takes in the letter layout, which differs from
     * {@link #value(SemanticType, String, RenderLanguage)} in one type: a percentage
     * carries the decimal places the document wrote it with and no others.
     *
     * @param type     the semantic data type of the term, as the registry gives it
     * @param content  the content of the value, as the document spells it
     * @param language the language the rendering is in
     * @return the text to show
     */
    static String asWritten(SemanticType type, String content, RenderLanguage language) {
        return type == SemanticType.PERCENTAGE
                ? percentageAsWritten(content, language)
                : value(type, content, language);
    }

    /**
     * Returns a decimal with grouped thousands and at least as many decimal places as
     * asked for, or the content as it stands where it is not a decimal.
     *
     * @param content     the content of the value
     * @param minDecimals the smallest number of decimal places to show
     * @param language    the language the rendering is in
     * @return the text to show
     */
    static String decimal(String content, int minDecimals, RenderLanguage language) {
        BigDecimal number = decimalOf(content);
        if (number == null) {
            return text(content);
        }
        return written(number, Math.max(number.scale(), minDecimals), content, language);
    }

    /**
     * Returns a decimal written out at a scale, grouped in threes, or the content as it
     * stands where writing it out is work no rendering is worth.
     *
     * @param number   the number
     * @param scale    how many decimal places to write it with
     * @param content  the content of the value, which stands in where it cannot be written
     * @param language the language the rendering is in
     * @return the text to show
     */
    private static String written(BigDecimal number, int scale, String content,
                                  RenderLanguage language) {
        if (!writable(number, scale)) {
            return text(content);
        }
        String digits;
        try {
            digits = number.setScale(scale, RoundingMode.UNNECESSARY).abs().toPlainString();
        } catch (ArithmeticException e) {
            return text(content);
        }
        int point = digits.indexOf('.');
        String whole = point < 0 ? digits : digits.substring(0, point);
        String fraction = point < 0 ? "" : digits.substring(point + 1);
        boolean german = language == RenderLanguage.GERMAN;
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) {
                grouped.append(german ? '.' : ',');
            }
            grouped.append(whole.charAt(i));
        }
        StringBuilder shown = new StringBuilder();
        if (number.signum() < 0) {
            shown.append('-');
        }
        shown.append(grouped);
        if (!fraction.isEmpty()) {
            shown.append(german ? ',' : '.').append(fraction);
        }
        return shown.toString();
    }

    /**
     * Returns a percentage, which is a decimal followed by a per-cent sign.
     *
     * @param content  the content of the value
     * @param language the language the rendering is in
     * @return the text to show
     */
    static String percentage(String content, RenderLanguage language) {
        if (decimalOf(content) == null) {
            return text(content);
        }
        return decimal(content, MINIMUM_DECIMALS, language) + " %";
    }

    /**
     * Returns a percentage with the decimal places the document wrote it with: 19.00 is
     * {@code 19 %}, 19.50 is {@code 19,5 %} and 10.7 is {@code 10,7 %}.
     *
     * <p>What is dropped are the trailing zeros of the fraction, which are the digits the
     * figure does not carry. Nothing is rounded and nothing is cut: a rate of 19.125 per
     * cent is on the page with all of its digits, because the rate of an invoice is a
     * fact of the invoice and not a figure this module decides the precision of.
     *
     * @param content  the content of the value
     * @param language the language the rendering is in
     * @return the text to show
     */
    static String percentageAsWritten(String content, RenderLanguage language) {
        BigDecimal number = decimalOf(content);
        if (number == null) {
            return text(content);
        }
        // The stripping is asked of BigDecimal rather than of the digits: it moves the
        // exponent and never writes the number out, so a decimal whose exponent is absurd
        // is still refused below rather than expanded here.
        int scale = Math.max(number.stripTrailingZeros().scale(), 0);
        return written(number, scale, content, language) + " %";
    }

    /**
     * Returns a date in the picture of the language, or the content as it stands where it
     * is not a date.
     *
     * @param content  the content of the value
     * @param language the language the rendering is in
     * @return the text to show
     */
    static String date(String content, RenderLanguage language) {
        if (language == RenderLanguage.ENGLISH) {
            return text(content);
        }
        try {
            LocalDate date = LocalDate.parse(content);
            return String.format(Locale.ROOT, "%02d.%02d.%04d",
                    date.getDayOfMonth(), date.getMonthValue(), date.getYear());
        } catch (DateTimeParseException e) {
            return text(content);
        }
    }

    /**
     * Returns a text with the line endings of the specification: a line break is one
     * newline, whatever the document wrote.
     *
     * @param content the content of the value
     * @return the text to show
     */
    static String text(String content) {
        return Esj.normalizeLineEndings(content);
    }

    /**
     * Returns the size of an attachment in bytes, as a grouped number followed by the
     * unit. The content of an attachment is never shown; its size is what tells a reader
     * that there is something to open and roughly how large it is.
     *
     * @param bytes    the number of bytes
     * @param language the language the rendering is in
     * @return the text to show
     */
    static String bytes(long bytes, RenderLanguage language) {
        return decimal(Long.toString(bytes), 0, language)
                + (language == RenderLanguage.GERMAN ? " Bytes" : " bytes");
    }

    /**
     * Tells whether a decimal can be written out at a scale without asking for more
     * digits than any reader would read.
     *
     * <p>The count is the length of the plain form, worked out without producing it: the
     * digits before the point are the precision less the scale the number carries, at
     * least one, and the digits after it are the scale it is written at. A scale that is
     * large makes the second half large, and a scale that is negative — which is what an
     * exponent above the precision leaves — makes the first half large.
     */
    private static boolean writable(BigDecimal number, int scale) {
        long whole = Math.max((long) number.precision() - number.scale(), 1);
        return whole + scale <= MAXIMUM_DIGITS;
    }

    /** Returns the content as a decimal, or {@code null} where it is not one. */
    private static BigDecimal decimalOf(String content) {
        try {
            return new BigDecimal(content);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
