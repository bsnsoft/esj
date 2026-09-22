package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * How the vendored stylesheet writes a value down, so that a test can look for it.
 *
 * <p>A rendering is for a human reader, and a human reader is not shown a canonical
 * decimal. The stylesheet formats amounts, quantities and percentages with a picture out
 * of its localization file and dates with a date picture out of the same file, and the two
 * languages it carries have different pictures. This class states, per semantic data type,
 * the forms a value may take in the rendering; a coverage test then asks whether one of
 * them is there.
 *
 * <p>The forms are stated here and not read out of the stylesheet, deliberately: if the
 * stylesheet is ever vendored again at a newer tag and changes how it writes a number, the
 * coverage test has to fail rather than adapt. What the test would then be saying is that
 * the rendering has changed and that this file has not been looked at.
 *
 * <h2>The pictures</h2>
 *
 * <ul>
 *   <li>a value of at most two decimal places is written with exactly two
 *       ({@code amount-format}: {@code ###.##0,00} in German, {@code ###,##0.00} in
 *       English), and one with more is written with all of them and at least none
 *       ({@code at-least-two-format});</li>
 *   <li>a date is {@code [D].[M].[Y,4]} in German and {@code [Y,4]-[M,2]-[D,2]} in
 *       English, which is the canonical spelling of the value itself;</li>
 *   <li>BT-20, the payment terms, is split on the semicolon and the parts are put on
 *       separate lines, so the semicolons themselves are not in the rendering.</li>
 * </ul>
 */
final class Presentation {

    private Presentation() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the forms the value of a term may take in a rendering. The content itself is
     * always among them.
     *
     * @param term     the business term identifier the value sits at
     * @param type     the semantic data type of that term
     * @param content  the content of the value, as the document spells it
     * @param language the language the rendering is in
     * @return the forms to look for, already flattened for comparison
     */
    static Set<String> of(String term, SemanticType type, String content,
                          RenderLanguage language) {
        Set<String> forms = new LinkedHashSet<>();
        forms.add(content);
        boolean german = language == RenderLanguage.GERMAN;
        switch (type) {
            case DATE -> date(content, german).ifPresent(forms::add);
            case AMOUNT, UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE ->
                    number(content, german).ifPresent(forms::add);
            default -> {
                // Every other type is written down as it stands.
            }
        }
        if ("BT-20".equals(term)) {
            forms.add(content.replace(";", " "));
        }
        Set<String> flattened = new LinkedHashSet<>();
        forms.forEach(form -> flattened.add(flatten(form)));
        return flattened;
    }

    /** Collapses every run of whitespace into one space, which is what HTML does too. */
    static String flatten(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private static java.util.Optional<String> date(String content, boolean german) {
        try {
            LocalDate date = LocalDate.parse(content);
            return java.util.Optional.of(german
                    ? date.getDayOfMonth() + "." + date.getMonthValue() + "." + date.getYear()
                    : String.format(Locale.ROOT, "%04d-%02d-%02d",
                            date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<String> number(String content, boolean german) {
        try {
            BigDecimal value = new BigDecimal(content);
            int dot = content.indexOf('.');
            int decimals = dot < 0 ? 0 : content.length() - dot - 1;
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
            symbols.setDecimalSeparator(german ? ',' : '.');
            symbols.setGroupingSeparator(german ? '.' : ',');
            DecimalFormat format = new DecimalFormat(
                    decimals > 2 ? "###,##0.#################" : "###,##0.00", symbols);
            format.setRoundingMode(RoundingMode.HALF_EVEN);
            return java.util.Optional.of(format.format(value.doubleValue()));
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
    }
}
