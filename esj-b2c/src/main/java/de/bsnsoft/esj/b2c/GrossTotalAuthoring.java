package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Only the total was agreed: the job costs 1 500 including VAT, and the lines say what it was
 * for.
 *
 * <p>Input: the displayed invoice gross total (BT-B2C-010), and on every line a basis to
 * spread it over — the item net price (BT-146) and the invoiced quantity, or an invoice line
 * net amount (BT-131) the line already carries. The agreed total is allocated over the lines
 * in proportion to what they would have come to gross, each share rounded half up to two
 * decimals; the remainder rule is that the last line takes what the rounded shares left, so
 * the shares add up to the agreed total exactly. Each line is then derived from its share the
 * way {@link GrossLineAuthoring} derives one from a displayed gross line total.
 *
 * <p>The shares are in the report and in no term of the extension. A share is not a figure
 * the customer was shown — nothing at line level was — and BT-B2C-002 says that a figure was
 * shown. For the same reason the policy refuses a line that states a gross figure of its own:
 * where one was shown, {@link GrossUnitAuthoring} or {@link GrossLineAuthoring} derives the
 * line from it exactly instead of spreading a total over it.
 *
 * <p>The policy refuses a document level allowance or charge: the agreed total covers them
 * too, and how much of it each line then carries is not something a proportion can answer.
 */
final class GrossTotalAuthoring implements GrossPolicy {

    private static final SemanticPath DISPLAYED_TOTAL = SemanticPath.of("/BT-B2C-010");

    private static final SemanticPath DOCUMENT_ALLOWANCES = SemanticPath.group("/BG-20");

    private static final SemanticPath DOCUMENT_CHARGES = SemanticPath.group("/BG-21");

    /**
     * The scale the basis of the allocation is computed at. It decides a proportion and no
     * figure of the invoice, and the shares it leads to are rounded to two decimals after it.
     */
    private static final int BASIS_SCALE = 8;

    @Override
    public String name() {
        return "GROSS_TOTAL_AUTHORING";
    }

    @Override
    public Map<Integer, BigDecimal> prices(AuthoringRun run) {
        int lines = run.lineCount();
        BigDecimal total = run.decimal(DISPLAYED_TOTAL).orElseThrow(() -> run.refusal(
                "a displayed invoice gross total",
                "the invoice states no displayed invoice gross total (BT-B2C-010), and this"
                        + " policy has nothing to spread over its lines",
                SemanticPath.root(), "BT-B2C-010"));
        refuseDocumentAdjustments(run);
        List<BigDecimal> bases = new ArrayList<>(lines);
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = 0; index < lines; index++) {
            BigDecimal basis = grossBasis(run, AuthoringRun.line(index));
            bases.add(basis);
            sum = sum.add(basis);
        }
        if (sum.signum() == 0) {
            throw run.refusal("a basis to spread the agreed total over",
                    "the invoice lines come to nothing at the prices and quantities they carry,"
                            + " so there is no proportion to spread the displayed invoice gross"
                            + " total (BT-B2C-010) in",
                    SemanticPath.root(), "BG-25");
        }
        return derive(run, total, bases, sum);
    }

    private Map<Integer, BigDecimal> derive(AuthoringRun run, BigDecimal total,
                                            List<BigDecimal> bases, BigDecimal sum) {
        Map<Integer, BigDecimal> expected = new LinkedHashMap<>();
        BigDecimal allocated = BigDecimal.ZERO;
        int lines = bases.size();
        for (int index = 0; index < lines; index++) {
            SemanticPath line = AuthoringRun.line(index);
            BigDecimal share;
            String how;
            if (index < lines - 1) {
                share = AuthoringRun.quotient(total.multiply(bases.get(index)), sum,
                        AuthoringRun.AMOUNT_SCALE).value();
                how = "the share " + share.toPlainString() + " of the displayed invoice gross"
                        + " total (BT-B2C-010) " + total.toPlainString() + " this line's"
                        + " proportion comes to";
            } else {
                share = total.subtract(allocated);
                how = "the share " + share.toPlainString() + " of the displayed invoice gross"
                        + " total (BT-B2C-010) " + total.toPlainString() + " the rounded shares"
                        + " of the lines before it left";
            }
            if (share.signum() != total.signum() && share.signum() != 0) {
                throw run.refusal("a share of the agreed total for every line",
                        "spreading the displayed invoice gross total (BT-B2C-010) "
                                + total.toPlainString() + " over the lines leaves "
                                + share.toPlainString() + " for the line at " + line + ", which"
                                + " is no share of it",
                        line, "BT-B2C-010");
            }
            run.note("the line at " + line + " carries " + share.toPlainString()
                    + " of the displayed invoice gross total (BT-B2C-010)");
            expected.put(index, run.fromGrossLineTotal(line, share, how));
            allocated = allocated.add(share);
        }
        return expected;
    }

    /** What a line would come to with VAT at the price and quantity it carries. */
    private BigDecimal grossBasis(AuthoringRun run, SemanticPath line) {
        refuseDisplayedFigure(run, line, "BT-B2C-001", "a displayed gross unit price",
                "GROSS_UNIT_AUTHORING");
        refuseDisplayedFigure(run, line, "BT-B2C-002", "a displayed gross line total",
                "GROSS_LINE_AUTHORING");
        BigDecimal net = run.decimal(AuthoringRun.path(line, "BG-29/BT-146"))
                .map(price -> AuthoringRun.quotient(price.multiply(run.quantity(line)),
                        run.baseQuantity(line), BASIS_SCALE).value())
                .or(() -> run.decimal(AuthoringRun.path(line, "BT-131")))
                .orElseThrow(() -> run.refusal(
                        "a price or a net amount on every line",
                        "the invoice line at " + line + " states neither an item net price"
                                + " (BT-146) nor an invoice line net amount (BT-131), so its"
                                + " share of the agreed total does not follow from anything",
                        line, "BT-146"));
        return net.multiply(AuthoringRun.HUNDRED.add(run.rate(line)))
                .divide(AuthoringRun.HUNDRED);
    }

    private void refuseDisplayedFigure(AuthoringRun run, SemanticPath line, String term,
                                       String what, String instead) {
        if (run.decimal(AuthoringRun.path(line, term)).isPresent()) {
            throw run.refusal("no gross figure on a line",
                    "the invoice line at " + line + " states " + what + " (" + term + "), a"
                            + " figure the customer was shown at line level; spreading an agreed"
                            + " total over it would contradict it, and " + instead + " derives"
                            + " the line from it exactly",
                    line, term);
        }
    }

    private void refuseDocumentAdjustments(AuthoringRun run) {
        refuseGroup(run, DOCUMENT_ALLOWANCES, "BG-20", "a document level allowance");
        refuseGroup(run, DOCUMENT_CHARGES, "BG-21", "a document level charge");
    }

    private void refuseGroup(AuthoringRun run, SemanticPath group, String groupId, String what) {
        if (run.builder().occurrences(group) > 0) {
            throw run.refusal("no document level allowance and no document level charge",
                    "the invoice carries " + what + " (" + groupId + "), and the displayed"
                            + " invoice gross total (BT-B2C-010) covers it too; how much of the"
                            + " agreed total each line then carries is not a proportion of the"
                            + " lines",
                    group, groupId);
        }
    }
}
