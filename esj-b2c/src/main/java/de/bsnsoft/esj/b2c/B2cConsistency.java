package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.rules.RuleCategory;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Checks that the guarantee a gross authoring policy claims holds for a document.
 *
 * <pre>{@code
 * List<RuleFinding> findings =
 *         B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING);
 * }</pre>
 *
 * <p>Four checks, each the guarantee of a policy and none of them a relation the extension
 * states:
 *
 * <table class="striped">
 *   <caption>The checks and the policies they belong to</caption>
 *   <tr><th>Code</th><th>Policies</th><th>What it checks</th></tr>
 *   <tr><td>B2C-01</td><td>all</td>
 *       <td>the amount due for payment (BT-115) and the paid amount (BT-113) come to the
 *           displayed invoice gross total (BT-B2C-010)</td></tr>
 *   <tr><td>B2C-02</td><td>GROSS_UNIT_AUTHORING</td>
 *       <td>the item net price (BT-146) is the displayed gross unit price (BT-B2C-001) at the
 *           line's VAT rate, cut to the scale of the policy</td></tr>
 *   <tr><td>B2C-03</td><td>all</td>
 *       <td>the invoice line net amount (BT-131) is what the line's price, quantity, charges
 *           and allowances come to (Annex A.1.1)</td></tr>
 *   <tr><td>B2C-04</td><td>GROSS_LINE_AUTHORING, GROSS_TOTAL_AUTHORING</td>
 *       <td>the invoice line net amount (BT-131) is the displayed gross line total
 *           (BT-B2C-002) at the line's VAT rate</td></tr>
 * </table>
 *
 * <p>The specification of the extension ({@code model/b2c/0.1.json}) defines no arithmetic
 * relation between its terms, and none between one of them and a term of EN 16931-1. Nothing
 * here invents one. A check reads the guarantee of a <em>policy</em>, which is a statement
 * about how a document was authored and not about what the document means: a document that
 * carries the same terms and was priced another way may fail one of these and be right. Which
 * is why the policy is an argument and not a guess.
 *
 * <p>A check is skipped, with an information finding, where the document does not carry the
 * figure it is about. Where a value does not spell what its semantic data type requires — a
 * defect the structural validator reports at layer L2 — every check gives way to one
 * information finding rather than a second report of the same problem.
 */
public final class B2cConsistency {

    /** The identifier of the pack these findings carry. */
    public static final String PACK_ID = "b2c";

    /** The version of that pack, which is the version of the extension registry. */
    public static final String PACK_VERSION = "0.1";

    private static final SemanticPath DISPLAYED_TOTAL = SemanticPath.of("/BT-B2C-010");

    private static final SemanticPath AMOUNT_DUE = SemanticPath.of("/BG-22/BT-115");

    private static final SemanticPath PAID = SemanticPath.of("/BG-22/BT-113");

    private static final SemanticPath LINES = SemanticPath.group("/BG-25");

    private B2cConsistency() {
    }

    /**
     * Checks a document against the guarantee of a policy.
     *
     * @param document the invoice
     * @param policy   the policy that is said to have written it, with the options it ran
     *                 with; the scale of B2C-02 is
     *                 {@link AuthoringOptions#netPriceScale()} of those options
     * @return the findings, in the order of the codes and then of the paths, empty where the
     *         guarantee holds and every check could be made
     * @throws NullPointerException if an argument is {@code null}
     */
    public static List<RuleFinding> check(SemanticDocument document, GrossAuthoring policy) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(policy, "policy");
        List<RuleFinding> findings = new ArrayList<>();
        try {
            new Check(document.toBuilder(), policy, findings).run();
        } catch (EsjFormatException e) {
            return List.of(finding("B2C-00", RuleSeverity.INFO,
                    "a value this check reads does not spell what its semantic data type"
                            + " requires, so no guarantee of " + policy.name() + " could be"
                            + " checked; the structural validator reports the value itself at"
                            + " layer L2",
                    List.of()));
        }
        findings.sort(RuleFinding.ORDER);
        return List.copyOf(findings);
    }

    private static RuleFinding finding(String code, RuleSeverity severity, String message,
                                       List<String> paths) {
        return new RuleFinding(code, RuleCategory.of(code), severity, message, paths, PACK_ID,
                PACK_VERSION, RuleFinding.NATIVE_ENGINE);
    }

    /** One run of the check over one document. */
    private static final class Check {

        private final SemanticDocument.Builder builder;

        private final GrossAuthoring policy;

        private final List<RuleFinding> findings;

        Check(SemanticDocument.Builder builder, GrossAuthoring policy,
              List<RuleFinding> findings) {
            this.builder = builder;
            this.policy = policy;
            this.findings = findings;
        }

        void run() {
            agreedTotal();
            int lines = builder.occurrences(LINES);
            for (int index = 0; index < lines; index++) {
                line(AuthoringRun.line(index));
            }
        }

        /**
         * B2C-01: what the customer still has to pay and what was paid in advance come to the
         * gross total the customer agreed to. The paid amount (BT-113) is a term of
         * EN 16931-1 and no policy touches it, so the guarantee is about the rest.
         */
        private void agreedTotal() {
            Optional<BigDecimal> displayed = decimal(DISPLAYED_TOTAL);
            if (displayed.isEmpty()) {
                findings.add(finding("B2C-01", RuleSeverity.INFO,
                        "the invoice states no displayed invoice gross total (BT-B2C-010), so"
                                + " there is no agreed gross total for the amount due for"
                                + " payment (BT-115) to come to",
                        List.of(DISPLAYED_TOTAL.toString())));
                return;
            }
            BigDecimal due = decimal(AMOUNT_DUE).orElse(null);
            BigDecimal paid = decimal(PAID).orElse(BigDecimal.ZERO);
            BigDecimal target = displayed.orElseThrow();
            if (due == null || due.add(paid).compareTo(target) != 0) {
                findings.add(finding("B2C-01", RuleSeverity.FATAL,
                        "the customer agreed to " + target.toPlainString() + " with VAT"
                                + " (BT-B2C-010) and the amount due for payment (BT-115) is "
                                + (due == null ? "not stated" : due.toPlainString())
                                + " beside a paid amount (BT-113) of " + paid.toPlainString()
                                + "; " + policy.name() + " carries the invoice total with VAT"
                                + " (BT-112) to the agreed total through the invoice rounding"
                                + " amount (BT-114) and takes the paid amount off it",
                        List.of(AMOUNT_DUE.toString(), DISPLAYED_TOTAL.toString())));
            }
        }

        private void line(SemanticPath line) {
            BigDecimal rate = decimal(AuthoringRun.path(line, "BG-30/BT-152"))
                    .orElse(BigDecimal.ZERO);
            unitPrice(line, rate);
            lineNetAmount(line);
            grossLineTotal(line, rate);
        }

        /** B2C-02: the item net price is the displayed gross unit price at the rate. */
        private void unitPrice(SemanticPath line, BigDecimal rate) {
            if (!(policy.policy() instanceof GrossUnitAuthoring)) {
                return;
            }
            SemanticPath displayedPath = AuthoringRun.path(line, "BT-B2C-001");
            SemanticPath pricePath = AuthoringRun.path(line, "BG-29/BT-146");
            Optional<BigDecimal> displayed = decimal(displayedPath);
            if (displayed.isEmpty()) {
                findings.add(finding("B2C-02", RuleSeverity.INFO,
                        "the invoice line at " + line + " states no displayed gross unit price"
                                + " (BT-B2C-001), so its item net price (BT-146) follows from"
                                + " no figure of the extension",
                        List.of(displayedPath.toString())));
                return;
            }
            int scale = policy.options().netPriceScale();
            BigDecimal expected =
                    AuthoringRun.net(displayed.orElseThrow(), rate, scale).value();
            BigDecimal price = decimal(pricePath).orElse(null);
            if (price == null || price.compareTo(expected) != 0) {
                findings.add(finding("B2C-02", RuleSeverity.FATAL,
                        "the displayed gross unit price (BT-B2C-001) "
                                + displayed.orElseThrow().toPlainString() + " at "
                                + rate.toPlainString() + " per cent comes to "
                                + expected.toPlainString() + " over " + scale + " fraction"
                                + " digits, and the item net price (BT-146) is "
                                + (price == null ? "not stated" : price.toPlainString()),
                        List.of(pricePath.toString(), displayedPath.toString())));
            }
        }

        /** B2C-03: the line net amount is what price, quantity and adjustments come to. */
        private void lineNetAmount(SemanticPath line) {
            SemanticPath netPath = AuthoringRun.path(line, "BT-131");
            BigDecimal price = decimal(AuthoringRun.path(line, "BG-29/BT-146")).orElse(null);
            BigDecimal quantity = decimal(AuthoringRun.path(line, "BT-129")).orElse(null);
            if (price == null || quantity == null) {
                findings.add(finding("B2C-03", RuleSeverity.INFO,
                        "the invoice line at " + line + " states no item net price (BT-146) or"
                                + " no invoiced quantity (BT-129), so its invoice line net"
                                + " amount (BT-131) follows from nothing this check can read",
                        List.of(netPath.toString())));
                return;
            }
            BigDecimal base = decimal(AuthoringRun.path(line, "BG-29/BT-149"))
                    .orElse(BigDecimal.ONE);
            if (base.signum() == 0) {
                return;
            }
            BigDecimal adjustments = sum(line, "BG-28", "BT-141").subtract(
                    sum(line, "BG-27", "BT-136"));
            BigDecimal expected = AuthoringRun.quotient(
                    price.multiply(quantity).add(adjustments.multiply(base)), base,
                    AuthoringRun.AMOUNT_SCALE).value();
            BigDecimal net = decimal(netPath).orElse(null);
            if (net == null || net.compareTo(expected) != 0) {
                findings.add(finding("B2C-03", RuleSeverity.FATAL,
                        "the item net price (BT-146) " + price.toPlainString() + " over the"
                                + " invoiced quantity (BT-129) " + quantity.toPlainString()
                                + " comes to " + expected.toPlainString() + ", and the invoice"
                                + " line net amount (BT-131) is "
                                + (net == null ? "not stated" : net.toPlainString()),
                        List.of(netPath.toString())));
            }
        }

        /** B2C-04: the line net amount is the displayed gross line total at the rate. */
        private void grossLineTotal(SemanticPath line, BigDecimal rate) {
            if (policy.policy() instanceof GrossUnitAuthoring) {
                return;
            }
            SemanticPath displayedPath = AuthoringRun.path(line, "BT-B2C-002");
            Optional<BigDecimal> displayed = decimal(displayedPath);
            if (displayed.isEmpty()) {
                return;
            }
            SemanticPath netPath = AuthoringRun.path(line, "BT-131");
            BigDecimal expected = AuthoringRun.amount(
                    AuthoringRun.net(displayed.orElseThrow(), rate,
                            AuthoringRun.AMOUNT_SCALE).value());
            BigDecimal net = decimal(netPath).orElse(null);
            if (net == null || net.compareTo(expected) != 0) {
                findings.add(finding("B2C-04", RuleSeverity.FATAL,
                        "the displayed gross line total (BT-B2C-002) "
                                + displayed.orElseThrow().toPlainString() + " at "
                                + rate.toPlainString() + " per cent comes to "
                                + expected.toPlainString() + ", and the invoice line net amount"
                                + " (BT-131) is " + (net == null ? "not stated"
                                        : net.toPlainString()),
                        List.of(netPath.toString(), displayedPath.toString())));
            }
        }

        private BigDecimal sum(SemanticPath line, String groupId, String termId) {
            SemanticPath group = SemanticPath.group(line + "/" + groupId);
            BigDecimal sum = BigDecimal.ZERO;
            int count = builder.occurrences(group);
            for (int index = 0; index < count; index++) {
                SemanticPath instance = SemanticPath.group(group + "/" + index);
                sum = sum.add(decimal(AuthoringRun.path(instance, termId))
                        .orElse(BigDecimal.ZERO));
            }
            return sum;
        }

        private Optional<BigDecimal> decimal(SemanticPath path) {
            return builder.value(path).map(SemanticValue::asDecimal);
        }
    }
}
