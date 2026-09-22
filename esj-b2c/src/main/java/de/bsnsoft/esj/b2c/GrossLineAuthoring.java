package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticPath;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The merchant priced the line with VAT included: the package costs 29.99, whatever is in it.
 *
 * <p>Input per invoice line: the displayed gross line total (BT-B2C-002), the invoiced
 * quantity (BT-129) and the VAT category and rate of the line. The line is derived first and
 * the unit price from it, not the other way round: the invoice line net amount is the gross
 * line total at the rate, rounded once half up to two decimals, and the item net price
 * (BT-146) is what that amount comes to per unit, cut at the scale the options name.
 */
final class GrossLineAuthoring implements GrossPolicy {

    @Override
    public String name() {
        return "GROSS_LINE_AUTHORING";
    }

    @Override
    public Map<Integer, BigDecimal> prices(AuthoringRun run) {
        int lines = run.lineCount();
        Map<Integer, BigDecimal> expected = new LinkedHashMap<>();
        for (int index = 0; index < lines; index++) {
            SemanticPath line = AuthoringRun.line(index);
            BigDecimal gross = run.decimal(AuthoringRun.path(line, "BT-B2C-002"))
                    .orElseThrow(() -> run.refusal(
                            "a displayed gross line total on every line",
                            "the invoice line at " + line + " states no displayed gross line"
                                    + " total (BT-B2C-002), and this policy derives the line"
                                    + " from one",
                            line, "BT-B2C-002"));
            expected.put(index, run.fromGrossLineTotal(line, gross,
                    "the displayed gross line total (BT-B2C-002) " + gross.toPlainString()));
        }
        return expected;
    }
}
