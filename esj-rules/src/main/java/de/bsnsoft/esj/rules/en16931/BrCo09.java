package de.bsnsoft.esj.rules.en16931;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.rules.CodeList;
import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code BR-CO-09}: a VAT identifier begins with the country code of the state that issued
 * it, so that the country can be read off the identifier.
 *
 * <p>Written in Java for one reason: the question is about the first two characters of a
 * value, and the rule language has no operator that takes a part of a value. It could be
 * written as a pattern of two hundred and fifty alternatives, which is how the official
 * validation artefacts write it, and a pattern that has to be regenerated whenever a country
 * is added is a copy of a code list in the wrong place. Here the prefix is looked up in the
 * country code snapshot the pack decides against, which is the list the statement names.
 *
 * <p>Greece is the exception the standard states: its VAT identifiers carry the prefix
 * {@code EL} where ISO 3166-1 gives the country {@code GR}.
 */
public final class BrCo09 implements JavaRule {

    /** The prefix Greece uses on a VAT identifier, which ISO 3166-1 does not have. */
    private static final String GREECE = "EL";

    /** How many characters of a VAT identifier are the country prefix. */
    private static final int PREFIX = 2;

    private static final List<String> PATHS = List.of("/BG-4/BT-31", "/BG-7/BT-48", "/BG-11/BT-63");

    private static final Map<String, String> NAMES = Map.of(
            "/BG-4/BT-31", "seller VAT identifier (BT-31)",
            "/BG-7/BT-48", "buyer VAT identifier (BT-48)",
            "/BG-11/BT-63", "seller tax representative VAT identifier (BT-63)");

    /** Creates the rule. */
    public BrCo09() {
    }

    @Override
    public String id() {
        return "BR-CO-09";
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
        return List.of("BT-31", "BT-48", "BT-63");
    }

    @Override
    public String source() {
        return "EN 16931-1, 6.4.2, Table 4, BR-CO-9";
    }

    @Override
    public Optional<String> check(RuleContext context) {
        CodeList countries = context.codeList("iso-3166-1");
        for (String path : PATHS) {
            for (Map.Entry<SemanticPath, String> value : context.texts(path)) {
                Optional<String> failure = examine(context, countries, path, value);
                if (failure.isPresent()) {
                    return failure;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> examine(RuleContext context, CodeList countries, String path,
                                     Map.Entry<SemanticPath, String> value) {
        String identifier = value.getValue();
        String prefix = identifier.length() < PREFIX ? identifier : identifier.substring(0, PREFIX);
        if (prefix.equals(GREECE) || countries.contains(prefix)) {
            return Optional.empty();
        }
        return Optional.of("The " + NAMES.get(path) + " at " + value.getKey() + " is "
                + context.escape(identifier) + ", and it begins with " + context.escape(prefix)
                + ", which is not a country of the iso-3166-1 snapshot this pack decides against"
                + " and is not the prefix EL that Greece uses.");
    }
}
