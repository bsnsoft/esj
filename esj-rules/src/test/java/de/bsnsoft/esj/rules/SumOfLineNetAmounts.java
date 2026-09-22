package de.bsnsoft.esj.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * BR-CO-10 written in Java, so that a test can put it beside the same rule written in the
 * rule language and compare the two findings member for member.
 *
 * <p>It is not a rule the language cannot express — the point is that it can, and that the
 * two ways of writing it are interchangeable. A rule that really needs Java would make a poor
 * test of that, because there would be nothing to compare it with.
 */
final class SumOfLineNetAmounts implements JavaRule {

    static final String MESSAGE =
            "The invoice line net amounts come to {$never}, and BT-106 at {@/BG-22/BT-106}"
                    + " carries {/BG-22/BT-106}.";

    /** The same message without the placeholder a Java rule has no binding for. */
    static final String JAVA_MESSAGE =
            "The invoice line net amounts come to %s, and BT-106 at {@/BG-22/BT-106}"
                    + " carries {/BG-22/BT-106}.";

    @Override
    public String id() {
        return "BR-CO-10";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.FATAL;
    }

    @Override
    public String context() {
        return "/";
    }

    @Override
    public List<String> terms() {
        return List.of("BT-106", "BT-131");
    }

    @Override
    public String source() {
        return "EN 16931-1, 6.4.2, BR-CO-10";
    }

    @Override
    public Optional<String> check(RuleContext context) {
        Optional<BigDecimal> stated = context.decimal("/BG-22/BT-106");
        BigDecimal lines = context.sum("/BG-25/*/BT-131");
        if (stated.isEmpty() || stated.get().compareTo(lines) == 0) {
            return Optional.empty();
        }
        return Optional.of(context.format(String.format(JAVA_MESSAGE, lines.toPlainString())));
    }
}
