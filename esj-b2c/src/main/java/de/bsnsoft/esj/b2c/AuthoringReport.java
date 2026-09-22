package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.typed.DerivationReport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a gross authoring policy did: the core terms it wrote itself, where it cut a price
 * that does not terminate, the rounding amount it arrived at, and the derivation of the
 * totals it ran afterwards.
 *
 * <p>The document carries values and no trace of how they came about. This is that trace,
 * handed to the caller instead, so that an application can show the arithmetic, log it or
 * assert on it. It is the record of one run and is no part of the document.
 *
 * @param policy     the name of the policy that ran, for instance
 *                   {@code GROSS_UNIT_AUTHORING}
 * @param values     every value the policy wrote itself, in the order it wrote them; the
 *                   amounts the derivation of the totals wrote are in {@link #derivation()}
 * @param cuts       every price whose exact quotient did not terminate at the scale the
 *                   options name and was cut half up to get there
 * @param rounding   the rounding amount (BT-114) step, or an empty optional where the policy
 *                   took none — where the invoice states no displayed invoice gross total, and
 *                   where the invoice total with VAT comes to that total without a rounding,
 *                   which the document says by carrying no BT-114 and the notes explain
 * @param notes      what the run has to say beyond the values, one English sentence each
 * @param derivation what {@code Totals} wrote after the policy had written the prices
 */
public record AuthoringReport(String policy,
                              List<Written> values,
                              List<Cut> cuts,
                              Optional<Rounding> rounding,
                              List<String> notes,
                              DerivationReport derivation) {

    /**
     * Copies the lists.
     *
     * @param policy     the name of the policy that ran, for instance
     *                   {@code GROSS_UNIT_AUTHORING}
     * @param values     every value the policy wrote itself, in the order it wrote them; the
     *                   amounts the derivation of the totals wrote are in {@link #derivation()}
     * @param cuts       every price whose exact quotient did not terminate at the scale the
     *                   options name and was cut half up to get there
     * @param rounding   the rounding amount (BT-114) step, or an empty optional where the policy
     *                   took none — where the invoice states no displayed invoice gross total, and
     *                   where the invoice total with VAT comes to that total without a rounding,
     *                   which the document says by carrying no BT-114 and the notes explain
     * @param notes      what the run has to say beyond the values, one English sentence each
     * @param derivation what {@code Totals} wrote after the policy had written the prices
     * @throws NullPointerException if an argument is {@code null}
     */
    public AuthoringReport {
        Objects.requireNonNull(policy, "policy");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        cuts = List.copyOf(Objects.requireNonNull(cuts, "cuts"));
        Objects.requireNonNull(rounding, "rounding");
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
        Objects.requireNonNull(derivation, "derivation");
    }

    /**
     * Returns the rounding amount the policy wrote into BT-114.
     *
     * @return the amount, or an empty optional where the policy took no rounding step
     */
    public Optional<BigDecimal> roundingAmount() {
        return rounding.map(Rounding::amount);
    }

    /**
     * Returns what the policy wrote at a path.
     *
     * @param path the semantic path, for instance {@code /BG-25/0/BG-29/BT-146}
     * @return the value written there, or an empty optional where the policy wrote none
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public Optional<Written> at(String path) {
        SemanticPath wanted = SemanticPath.of(Objects.requireNonNull(path, "path"));
        for (Written value : values) {
            if (value.path().equals(wanted)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the report as one line per thing the run did, in the order of the four kinds.
     *
     * @return the lines
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(policy);
        for (Written value : values) {
            lines.add(value.toString());
        }
        for (Cut cut : cuts) {
            lines.add(cut.toString());
        }
        rounding.ifPresent(step -> lines.add(step.toString()));
        lines.addAll(notes);
        return List.copyOf(lines);
    }

    /**
     * One value a gross authoring policy wrote.
     *
     * @param path  where it was written
     * @param term  the identifier of the business term, for instance {@code BT-146}
     * @param value the amount that was written
     * @param how   how the value was arrived at, as one English phrase naming the terms it
     *              came from
     */
    public record Written(SemanticPath path, String term, BigDecimal value, String how) {

        /**
         * Checks the arguments.
         *
         * @param path  where it was written
         * @param term  the identifier of the business term, for instance {@code BT-146}
         * @param value the amount that was written
         * @param how   how the value was arrived at, as one English phrase naming the terms it
         *              came from
         * @throws NullPointerException if an argument is {@code null}
         */
        public Written {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(how, "how");
        }

        @Override
        public String toString() {
            return path + " = " + value.toPlainString() + " (" + how + ")";
        }
    }

    /**
     * One price whose exact value has more fraction digits than the run kept.
     *
     * <p>Unit Price Amount is of unlimited scale (EN 16931-1, 6.5, AC 8) and nothing in this
     * library rounds, normalises or shortens a price the caller gave. A price the run
     * computes itself is another matter: the quotient of a gross price and a VAT rate rarely
     * terminates, so the run keeps it to the scale the options name and says here that it
     * did.
     *
     * @param path  where the price was written
     * @param term  the identifier of the business term, for instance {@code BT-146}
     * @param value the price as it was written
     * @param scale the number of fraction digits it was cut to, half up
     * @param how   how the exact quotient was arrived at, as one English phrase
     */
    public record Cut(SemanticPath path, String term, BigDecimal value, int scale, String how) {

        /**
         * Checks the arguments.
         *
         * @param path  where the price was written
         * @param term  the identifier of the business term, for instance {@code BT-146}
         * @param value the price as it was written
         * @param scale the number of fraction digits it was cut to, half up
         * @param how   how the exact quotient was arrived at, as one English phrase
         * @throws NullPointerException if an argument is {@code null}
         */
        public Cut {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(term, "term");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(how, "how");
        }

        @Override
        public String toString() {
            return path + " = " + value.toPlainString() + " (" + how + ", cut half up to "
                    + scale + " fraction digits)";
        }
    }

    /**
     * The rounding step a policy took so that the invoice total with VAT comes to the gross
     * total the customer agreed to.
     *
     * @param amount      the rounding amount (BT-114) the policy wrote, which is not zero:
     *                    where the difference is zero the policy writes no BT-114 and takes
     *                    no step
     * @param displayed   the displayed invoice gross total (BT-B2C-010) the step aims at
     * @param withVat     the invoice total with VAT (BT-112) the derivation arrived at
     * @param paid        the paid amount (BT-113) the invoice carries, zero where it carries
     *                    none
     * @param why         why the step was taken, as one English sentence
     */
    public record Rounding(BigDecimal amount, BigDecimal displayed, BigDecimal withVat,
                           BigDecimal paid, String why) {

        /**
         * Checks the arguments.
         *
         * @param amount      the rounding amount (BT-114) the policy wrote, which is not zero:
         *                    where the difference is zero the policy writes no BT-114 and takes
         *                    no step
         * @param displayed   the displayed invoice gross total (BT-B2C-010) the step aims at
         * @param withVat     the invoice total with VAT (BT-112) the derivation arrived at
         * @param paid        the paid amount (BT-113) the invoice carries, zero where it carries
         *                    none
         * @param why         why the step was taken, as one English sentence
         * @throws NullPointerException if an argument is {@code null}
         */
        public Rounding {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(displayed, "displayed");
            Objects.requireNonNull(withVat, "withVat");
            Objects.requireNonNull(paid, "paid");
            Objects.requireNonNull(why, "why");
        }

        @Override
        public String toString() {
            return "BT-114 = " + amount.toPlainString() + " (" + why + ")";
        }
    }
}
