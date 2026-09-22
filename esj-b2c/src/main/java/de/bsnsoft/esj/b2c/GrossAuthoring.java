package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.typed.DerivationReport;
import de.bsnsoft.esj.typed.Totals;
import de.bsnsoft.esj.typed.TotalsOptions;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Derives a net EN 16931 invoice from the gross figures a consumer was shown.
 *
 * <p>Three policies, one per way a merchant prices, chosen by the caller and never by the
 * library:
 *
 * <ul>
 *   <li>{@link #GROSS_UNIT_AUTHORING} — priced per unit with VAT included, three hoses at
 *       19.99 each. Reads BT-B2C-001 and writes the item net price (BT-146) of every line.</li>
 *   <li>{@link #GROSS_LINE_AUTHORING} — priced per line, the package costs 29.99. Reads
 *       BT-B2C-002 and writes the line's net amount and, from it, the unit price.</li>
 *   <li>{@link #GROSS_TOTAL_AUTHORING} — only the total was agreed. Reads BT-B2C-010 and
 *       spreads it over the lines in proportion to what they come to, with the remainder on
 *       the last line.</li>
 * </ul>
 *
 * <pre>{@code
 * Gross.on(invoice).line(0).displayedGrossUnitPrice("99.99");
 * AuthoringReport report = Gross.on(invoice).derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
 * }</pre>
 *
 * <p>Every policy then runs the derivation of EN 16931-1
 * ({@code de.bsnsoft.esj.typed.Totals}) for the line net amounts, the VAT
 * breakdown (BG-23) and the totals (BT-106 to BT-115), and adds one step of its own: where the
 * invoice states a displayed invoice gross total (BT-B2C-010), the invoice rounding amount
 * (BT-114) is set to the difference that carries the invoice total with VAT (BT-112) to it
 * exactly, and the amount due for payment (BT-115) to that total less what was paid in advance
 * (BT-113). That step is the whole reason BT-114 appears here; it stays the rounding amount of
 * EN 16931-1 and is no balancing account of the extension, which is why the difference is
 * bounded by what the half-up steps of the run can add up to
 * ({@link AuthoringOptions#maxRoundingAmount()}) and a larger one is refused, and why no BT-114
 * is written where the difference is zero.
 *
 * <p>No policy writes a term of the extension. A B2C term records a figure that was shown to
 * or agreed with the customer, which the caller states and a policy reads. What a policy
 * computes are the core terms and the report.
 *
 * <p>Decimals follow the typing of EN 16931-1, 6.5. The item net price is of unlimited scale
 * (AC 8): a price the caller gave is used as it stands, and a price the policy computes is
 * kept to {@link AuthoringOptions#netPriceScale()} fraction digits, six by default, with the
 * cut in {@link AuthoringReport#cuts()}. Every intermediate result is an exact
 * {@link BigDecimal}; an Amount is rounded half up to two decimals once, at the result; the
 * totals are sums of amounts already rounded.
 *
 * <p>A policy refuses rather than guesses. Where the invoice does not state the gross figure
 * the policy reads, where a line carries a line allowance or charge or a price base quantity
 * other than one and the options do not say how they are meant, or where the scale of the item
 * net price is too small for the quantity to come back to the figure that was shown, it throws
 * {@link PolicyPreconditionException} naming the precondition, the term and the group
 * instance.
 */
public final class GrossAuthoring {

    /** The merchant priced per unit with VAT included. */
    public static final GrossAuthoring GROSS_UNIT_AUTHORING =
            new GrossAuthoring(new GrossUnitAuthoring(), AuthoringOptions.standard());

    /** The merchant priced the line with VAT included. */
    public static final GrossAuthoring GROSS_LINE_AUTHORING =
            new GrossAuthoring(new GrossLineAuthoring(), AuthoringOptions.standard());

    /** Only the total with VAT was agreed. */
    public static final GrossAuthoring GROSS_TOTAL_AUTHORING =
            new GrossAuthoring(new GrossTotalAuthoring(), AuthoringOptions.standard());

    private static final SemanticPath DISPLAYED_TOTAL = SemanticPath.of("/BT-B2C-010");

    private static final SemanticPath TOTALS = SemanticPath.group("/BG-22");

    private static final SemanticPath ROUNDING_AMOUNT = SemanticPath.of("/BG-22/BT-114");

    private static final SemanticPath AMOUNT_DUE = SemanticPath.of("/BG-22/BT-115");

    /** One cent: what one half-up step to two fraction digits can move an amount by. */
    private static final BigDecimal CENT = new BigDecimal("0.01");

    /**
     * The derivation of EN 16931-1 as a policy runs it: the policy has just written the
     * prices the line amounts follow from, so a line amount the invoice carried from before
     * is replaced rather than checked against them.
     */
    private static final Totals TOTALS_POLICY =
            Totals.of(TotalsOptions.standard().withOverwriteLines(true));

    private final GrossPolicy policy;

    private final AuthoringOptions options;

    private GrossAuthoring(GrossPolicy policy, AuthoringOptions options) {
        this.policy = policy;
        this.options = options;
    }

    /**
     * Returns the name of the policy.
     *
     * @return the name, for instance {@code GROSS_UNIT_AUTHORING}
     */
    public String name() {
        return policy.name();
    }

    /**
     * Returns the options this policy runs with.
     *
     * @return the options
     */
    public AuthoringOptions options() {
        return options;
    }

    /**
     * Returns the same policy with other options.
     *
     * @param newOptions what to vary
     * @return the policy
     * @throws NullPointerException if {@code newOptions} is {@code null}
     */
    public GrossAuthoring with(AuthoringOptions newOptions) {
        return new GrossAuthoring(policy, Objects.requireNonNull(newOptions, "newOptions"));
    }

    /**
     * Derives the invoice into a document builder.
     *
     * @param builder the builder holding the invoice; its gross figures, quantities and VAT
     *                categories are read, and its prices, line amounts, VAT breakdown and
     *                totals are written
     * @return what the policy wrote, where it cut a price, what it set BT-114 to and what the
     *         derivation of the totals wrote
     * @throws PolicyPreconditionException if the invoice does not state what the policy needs
     * @throws de.bsnsoft.esj.typed.DerivationException if the totals cannot be
     *                                                             derived from what the
     *                                                             policy wrote
     * @throws NullPointerException        if {@code builder} is {@code null}
     */
    public AuthoringReport apply(SemanticDocument.Builder builder) {
        Objects.requireNonNull(builder, "builder");
        AuthoringRun run = new AuthoringRun(builder, policy.name(), options);
        Map<Integer, BigDecimal> expected = policy.prices(run);
        DerivationReport derivation = TOTALS_POLICY.apply(builder);
        checkLines(run, expected);
        Optional<AuthoringReport.Rounding> rounding = round(run);
        return new AuthoringReport(policy.name(), run.written(), run.cuts(), rounding,
                run.notes(), derivation);
    }

    @Override
    public String toString() {
        return name() + "[netPriceScale=" + options.netPriceScale()
                + ", lineAllowancesAndCharges=" + options.lineAllowancesAndCharges()
                + ", baseQuantity=" + options.baseQuantity() + "]";
    }

    /** The policy behind this object, for the consistency check that names it. */
    GrossPolicy policy() {
        return policy;
    }

    /**
     * Checks that the derivation arrived at the line net amounts the policy derived from the
     * gross figures, which it does unless the scale of the item net price is too small for
     * the quantity to carry them back.
     */
    private static void checkLines(AuthoringRun run, Map<Integer, BigDecimal> expected) {
        for (Map.Entry<Integer, BigDecimal> entry : expected.entrySet()) {
            SemanticPath line = AuthoringRun.line(entry.getKey());
            SemanticPath path = AuthoringRun.path(line, "BT-131");
            BigDecimal derived = run.decimal(path).orElseThrow();
            if (derived.compareTo(entry.getValue()) != 0) {
                throw run.refusal("an item net price scale the invoiced quantity carries back",
                        "the gross figure of the invoice line at " + line + " is the net amount "
                                + entry.getValue().toPlainString() + ", and the item net price"
                                + " (BT-146) at " + run.options().netPriceScale() + " fraction"
                                + " digits comes to " + derived.toPlainString() + " over the"
                                + " invoiced quantity;"
                                + " AuthoringOptions.withNetPriceScale(int) keeps more of it",
                        path, "BT-146");
            }
        }
    }

    /**
     * Sets the invoice rounding amount (BT-114) so that the invoice total with VAT comes to
     * the gross total the customer agreed to, and the amount due for payment (BT-115) to that
     * total less what was paid in advance (BT-113).
     */
    private Optional<AuthoringReport.Rounding> round(AuthoringRun run) {
        Optional<BigDecimal> displayed = run.decimal(DISPLAYED_TOTAL);
        if (displayed.isEmpty()) {
            run.note("the invoice states no displayed invoice gross total (BT-B2C-010), so no"
                    + " rounding amount (BT-114) was derived and the amount due for payment"
                    + " (BT-115) is the one the derivation arrived at");
            return Optional.empty();
        }
        BigDecimal target = displayed.orElseThrow();
        BigDecimal withVat = run.decimal(AuthoringRun.path(TOTALS, "BT-112")).orElseThrow();
        BigDecimal paid = run.decimal(AuthoringRun.path(TOTALS, "BT-113")).orElse(BigDecimal.ZERO);
        BigDecimal required = target.subtract(withVat);
        BigDecimal limit = limit(run);
        if (required.abs().compareTo(limit) > 0) {
            throw run.refusal("a rounding amount within the limit of this run",
                    "carrying the invoice total with VAT (BT-112) " + withVat.toPlainString()
                            + " to the displayed invoice gross total (BT-B2C-010) "
                            + target.toPlainString() + " asks for a rounding amount (BT-114) of "
                            + required.toPlainString() + ", beyond the " + limit.toPlainString()
                            + " this run allows; a difference that size is not a rounding, and"
                            + " BT-114 is the rounding amount of EN 16931-1 and no balancing"
                            + " account;"
                            + " AuthoringOptions.withMaxRoundingAmount(BigDecimal) names another"
                            + " limit",
                    ROUNDING_AMOUNT, "BT-114");
        }
        BigDecimal due = target.subtract(paid);
        String why = "the invoice total with VAT (BT-112) is " + withVat.toPlainString()
                + " and the customer agreed to " + target.toPlainString();
        if (required.signum() == 0) {
            if (run.decimal(ROUNDING_AMOUNT).isPresent()) {
                run.builder().remove(ROUNDING_AMOUNT);
                run.note("the invoice rounding amount (BT-114) the invoice carried was removed:"
                        + " the invoice total with VAT comes to the agreed total without one");
            } else {
                run.note("the invoice total with VAT (BT-112) comes to the displayed invoice"
                        + " gross total (BT-B2C-010) " + target.toPlainString() + " exactly, so"
                        + " no invoice rounding amount (BT-114) was written");
            }
        } else {
            run.write(ROUNDING_AMOUNT, "BT-114", required, why);
        }
        run.builder().set(AMOUNT_DUE, SemanticValue.ofDecimal(due));
        run.written().add(new AuthoringReport.Written(AMOUNT_DUE, "BT-115", due,
                "the displayed invoice gross total (BT-B2C-010) " + target.toPlainString()
                        + " less the paid amount (BT-113) " + paid.toPlainString()
                        + ", which is the invoice total with VAT (BT-112) less that amount and"
                        + " plus the rounding amount (BT-114)"));
        return required.signum() == 0
                ? Optional.empty()
                : Optional.of(new AuthoringReport.Rounding(required, target, withVat, paid, why));
    }

    /**
     * Returns the largest rounding amount this run may write: the one the options name, or
     * the bound the half-up steps of the run add up to — a cent per invoice line and a cent
     * per VAT breakdown, each of which is rounded once to two decimals.
     */
    private BigDecimal limit(AuthoringRun run) {
        return options.maxRoundingAmount().orElseGet(() -> CENT.multiply(BigDecimal.valueOf(
                run.builder().occurrences(SemanticPath.group("/BG-25"))
                        + run.builder().occurrences(SemanticPath.group("/BG-23")))));
    }
}
