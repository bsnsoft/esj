package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticPath;
import java.math.BigDecimal;
import java.util.Map;

/**
 * The merchant priced per unit with VAT included: three shower hoses at 19.99 each.
 *
 * <p>Input per invoice line: the displayed gross unit price (BT-B2C-001), the invoiced
 * quantity (BT-129) and the VAT category and rate of the line (BT-151, BT-152). Output: the
 * item net price (BT-146), from which the derivation of EN 16931-1 computes the line net
 * amount and everything above it.
 *
 * <p>BT-146 is the exact quotient of the gross price and the rate, kept to the scale the
 * options name — Unit Price Amount is of unlimited scale (6.5, AC 8) and a quotient like
 * 99.99 ÷ 1.19 does not terminate — and every cut is in the report. The line net amount is
 * rounded once, half up to two decimals, by the derivation.
 */
final class GrossUnitAuthoring implements GrossPolicy {

    @Override
    public String name() {
        return "GROSS_UNIT_AUTHORING";
    }

    @Override
    public Map<Integer, BigDecimal> prices(AuthoringRun run) {
        int lines = run.lineCount();
        for (int index = 0; index < lines; index++) {
            SemanticPath line = AuthoringRun.line(index);
            SemanticPath displayed = AuthoringRun.path(line, "BT-B2C-001");
            BigDecimal gross = run.decimal(displayed).orElseThrow(() -> run.refusal(
                    "a displayed gross unit price on every line",
                    "the invoice line at " + line + " states no displayed gross unit price"
                            + " (BT-B2C-001), and this policy derives the item net price"
                            + " (BT-146) from one",
                    line, "BT-B2C-001"));
            run.quantity(line);
            run.checkLineAdjustments(line);
            run.baseQuantity(line);
            BigDecimal rate = run.rate(line);
            AuthoringRun.Quotient price =
                    AuthoringRun.net(gross, rate, run.options().netPriceScale());
            run.writePrice(AuthoringRun.path(line, "BG-29/BT-146"), "BT-146", price,
                    "the displayed gross unit price (BT-B2C-001) " + gross.toPlainString()
                            + " at the invoiced item VAT rate (BT-152) " + rate.toPlainString()
                            + " per cent");
        }
        return Map.of();
    }
}
