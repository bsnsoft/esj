package de.bsnsoft.esj.typed;

import de.bsnsoft.esj.SemanticPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a derivation policy wrote into an invoice, what it took out of it, and where it
 * rounded.
 *
 * <p>A derived invoice carries no trace of how its amounts came about: the document holds
 * values and nothing else. The report is that trace, handed back to the caller instead, so
 * that an application can show the arithmetic, log it, or assert on it in a test. It is a
 * record of one run and is not part of the document.
 *
 * @param values   every value the policy wrote, in the order it wrote them
 * @param removals every value the policy took out of the document and put nothing in the
 *                 place of, in the order it met them
 */
public record DerivationReport(List<Derived> values, List<Removed> removals) {

    /**
     * Copies the lists.
     *
     * @param values   every value the policy wrote, in the order it wrote them
     * @param removals every value the policy took out of the document and put nothing in
     *                 the place of, in the order it met them
     * @throws NullPointerException if an argument is {@code null}
     */
    public DerivationReport {
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        removals = List.copyOf(Objects.requireNonNull(removals, "removals"));
    }

    /**
     * A report of a run that removed nothing.
     *
     * @param values every value the policy wrote, in the order it wrote them
     * @throws NullPointerException if {@code values} is {@code null}
     */
    public DerivationReport(List<Derived> values) {
        this(values, List.of());
    }

    /**
     * Returns the values the policy arrived at by rounding.
     *
     * <p>EN 16931-1, 6.5.13 rounds on the final result and not on an intermediate one, so a
     * derivation rounds at the amounts the 2017 edition of the standard fixes to two
     * fraction digits — the bound is that edition's and {@link Totals} computes no other —
     * and nowhere else. These are those amounts, of which the exact result had more than two fraction
     * digits and was therefore cut half up; an amount that came out exact is not listed here
     * although it passed the same step.
     *
     * @return the rounded values, in the order they were written
     */
    public List<Derived> roundings() {
        List<Derived> rounded = new ArrayList<>();
        for (Derived value : values) {
            if (value.rounded()) {
                rounded.add(value);
            }
        }
        return List.copyOf(rounded);
    }

    /**
     * Returns the values the policy wrote over a value the invoice already carried.
     *
     * <p>The document totals (BG-22) and the VAT breakdown (BG-23) are rewritten from the
     * lines on every run, so an amount a caller stated there by hand is replaced rather
     * than checked; a line net amount is checked against the formula instead, and is
     * replaced only where {@code TotalsOptions.withOverwriteLines(true)} says so. These are
     * the paths where that happened and the value the run put there; what stood there
     * before is gone from the document, which is why the run reports it at all.
     *
     * @return the replaced values, in the order they were written
     */
    public List<Derived> replacements() {
        List<Derived> replaced = new ArrayList<>();
        for (Derived value : values) {
            if (value.replaced()) {
                replaced.add(value);
            }
        }
        return List.copyOf(replaced);
    }

    /**
     * Returns what the policy took out of the document.
     *
     * <p>A derivation writes the document totals (BG-22) and the VAT breakdown (BG-23) from
     * the lines, so a term the invoice stated by hand can end up with nothing in its place:
     * BT-107 and BT-108 are removed where no document level allowance or charge stands behind
     * them, and a breakdown for a VAT category and rate that no line, allowance or charge uses
     * is removed whole. These are those values, with the path they stood at and the content
     * they had; a value the run wrote over is a {@link #replacements() replacement} and is not
     * listed here.
     *
     * @return the removed values, in the order the run met them
     */
    public List<Removed> removals() {
        return removals;
    }

    /**
     * Returns what the policy wrote at a path.
     *
     * @param path the semantic path, for instance {@code /BG-22/BT-112}
     * @return the value written there, or an empty optional if the policy wrote none
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public Optional<Derived> at(String path) {
        SemanticPath wanted = SemanticPath.of(Objects.requireNonNull(path, "path"));
        for (Derived value : values) {
            if (value.path().equals(wanted)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * One value a derivation policy wrote.
     *
     * @param path     where it was written
     * @param term     the identifier of the business term, for instance {@code BT-112}
     * @param value    the amount that was written
     * @param rounded  whether the exact result carried more than two fraction digits and
     *                 was rounded half up to get here
     * @param replaced whether the document already carried a different value at this path,
     *                 which this one took the place of
     * @param how      how the value was arrived at, as one English phrase naming the terms
     *                 it came from
     */
    public record Derived(SemanticPath path, String term, BigDecimal value, boolean rounded,
                          boolean replaced, String how) {

        /**
         * Checks the arguments.
         *
         * @param path    where it was written
         * @param term    the identifier of the business term, for instance {@code BT-112}
         * @param value   the amount that was written
         * @param rounded  whether the exact result carried more than two fraction digits and
         *                 was rounded half up to get here
         * @param replaced whether the document already carried a different value at this path
         * @param how      how the value was arrived at, as one English phrase naming the terms
         *                 it came from
         * @throws NullPointerException if an argument is {@code null}
         */
        public Derived {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(how, "how");
        }

        @Override
        public String toString() {
            return path + " = " + value.toPlainString() + " (" + how
                    + (rounded ? ", rounded half up to two decimals" : "")
                    + (replaced ? ", replacing the value the invoice stated" : "") + ")";
        }
    }

    /**
     * One value a derivation policy took out of the document.
     *
     * @param path  where it stood
     * @param term  the identifier of the business term, for instance {@code BT-107}
     * @param value the content it had, as the document carried it
     * @param why   why nothing took its place, as one English phrase
     */
    public record Removed(SemanticPath path, String term, String value, String why) {

        /**
         * Checks the arguments.
         *
         * @param path  where it stood
         * @param term  the identifier of the business term, for instance {@code BT-107}
         * @param value the content it had, as the document carried it
         * @param why   why nothing took its place, as one English phrase
         * @throws NullPointerException if an argument is {@code null}
         */
        public Removed {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(why, "why");
        }

        @Override
        public String toString() {
            return path + " removed, it stated " + value + " (" + why + ")";
        }
    }
}
