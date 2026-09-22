package de.bsnsoft.esj.rules.en16931;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.rules.CodeList;
import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code BR-CL-25}: the identification scheme of an electronic address is one of the
 * electronic address scheme identifiers the European Commission publishes.
 *
 * <p>Written in Java for the same reason as the rules that ask whether a scheme is there at
 * all: a scheme is a supplementary component of a value rather than a business term, and no
 * path of the rule language reaches it.
 *
 * <p>The identifier of this rule is the official validation artefacts' and not the
 * standard's, which names the code list of the term in the semantic model table and gives the
 * restriction no rule identifier of its own.
 */
public final class BrCl25 implements JavaRule {

    private static final Map<String, String> ADDRESSES = Map.of(
            "/BG-4/BT-34", "seller electronic address (BT-34)",
            "/BG-7/BT-49", "buyer electronic address (BT-49)");

    /** Creates the rule. */
    public BrCl25() {
    }

    @Override
    public String id() {
        return "BR-CL-25";
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
        return List.of("BT-34", "BT-49");
    }

    @Override
    public String source() {
        return "EN 16931-1, 6.3, Table 2, BT-34 and BT-49";
    }

    @Override
    public Optional<String> check(RuleContext context) {
        CodeList schemes = context.codeList("eas");
        for (String path : List.of("/BG-4/BT-34", "/BG-7/BT-49")) {
            for (Map.Entry<SemanticPath, SemanticValue> value : context.values(path)) {
                String scheme = value.getValue().scheme();
                if (scheme == null || schemes.contains(scheme)) {
                    continue;
                }
                return Optional.of("The " + ADDRESSES.get(path) + " at " + value.getKey()
                        + " names the identification scheme " + context.escape(scheme)
                        + ", which is not on the eas snapshot this pack decides against.");
            }
        }
        return Optional.empty();
    }
}
