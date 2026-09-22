package de.bsnsoft.esj.rules.en16931;

import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.util.List;
import java.util.Optional;

/**
 * An invoice that categorises anything under one of the VAT categories in which no VAT is
 * levied carries exactly one VAT breakdown group for that category.
 *
 * <p>Six of the nine VAT categories say <em>exactly</em> one and three say <em>at least</em>
 * one; the three are the categories in which VAT is levied at a rate, where one breakdown per
 * rate is the point. The six are here because the rule language can ask whether a business
 * group instance of a given shape exists and cannot ask how many instances of a filtered set
 * there are — an operator that counted a filtered set would be a query language, and the set
 * of operators is closed on purpose.
 *
 * <p>The rule is a statement about the document and is evaluated once. It reads the VAT
 * category of every invoice line, document level allowance, document level charge and VAT
 * breakdown, which is four passes over the document whatever the number of lines.
 */
abstract class ExactlyOneBreakdown implements JavaRule {

    private final String id;
    private final String code;
    private final String name;
    private final String source;

    /**
     * Creates the rule.
     *
     * @param id     the identifier the standard gives it
     * @param code   the VAT category code of UNTDID 5305 the rule is about
     * @param name   what that code means, in English, for the message
     * @param source the clause of the standard the rule states
     */
    ExactlyOneBreakdown(String id, String code, String name, String source) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.source = source;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final RuleSeverity severity() {
        return RuleSeverity.FATAL;
    }

    @Override
    public final String context() {
        return "/";
    }

    @Override
    public final List<String> terms() {
        return List.of("BT-95", "BT-102", "BT-118", "BT-151");
    }

    @Override
    public final List<String> roots() {
        return Vat.ROOTS;
    }

    @Override
    public final String source() {
        return source;
    }

    @Override
    public final Optional<String> check(RuleContext context) {
        if (!Vat.used(context, code)) {
            return Optional.empty();
        }
        int breakdowns = Vat.count(Vat.breakdowns(context), code);
        if (breakdowns == 1) {
            return Optional.empty();
        }
        return Optional.of("An invoice line, a document level allowance or a document level charge is"
                + " categorised \"" + code + "\" (" + name + "), and the invoice carries " + breakdowns
                + " VAT breakdown groups (BG-23) with that VAT category code (BT-118) where exactly"
                + " one belongs.");
    }
}
