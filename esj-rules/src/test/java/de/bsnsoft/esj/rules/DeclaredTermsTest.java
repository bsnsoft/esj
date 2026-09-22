package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.bsnsoft.esj.rules.en16931.En16931;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The {@code terms} a rule declares against the terms its statement actually addresses.
 *
 * <p>{@code terms} is documentation that leaves the rule: it is what a coverage table is built
 * from and what {@link RuleEngine#termsOf(String)} hands a caller, so a rule that declares a
 * business term it never reads makes a report say something untrue about itself. The list is
 * written by hand in the rule file, which means it can be copied from a neighbouring rule whose
 * statement is not the same — and that is exactly how twenty-four rules of the VAT category
 * families came to name two terms their statements never mention.
 *
 * <p>The check is mechanical and runs over every rule written in the rule language: collect the
 * business term identifiers that appear in the paths of the rule's assertion, of its message
 * bindings and of its message placeholders, and require the declared list to be exactly the
 * business terms among them. Business groups are left to the rule file, because a path names the
 * groups on the way to a term whether or not the rule is a statement about them; only the terms
 * a rule declares have to be terms it reads, and every term it reads has to be declared.
 *
 * <p>Rules written in Java declare their own terms in {@link JavaRule#terms()} and carry no
 * expression to compare them with, so they are outside this check by construction.
 */
class DeclaredTermsTest {

    /** A business term or business group identifier, as a path segment spells it. */
    private static final Pattern IDENTIFIER = Pattern.compile("B[TG]-[A-Z0-9]+(?:-[A-Z0-9]+)*");

    /** A path placeholder of a message: {@code {/BT-5}}, {@code {@/BT-5}} or {@code {$name}}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[@$]?(/[^}]*)}");

    @Test
    void everyRuleDeclaresTheTermsItReadsAndNoOthers() {
        List<String> wrong = new ArrayList<>();
        for (RuleDefinition rule : En16931.pack().rules()) {
            Set<String> addressed = addressed(rule);
            Set<String> declared = new TreeSet<>(rule.terms());
            Set<String> phantom = new TreeSet<>(declared);
            phantom.removeAll(addressed);
            Set<String> missing = new TreeSet<>();
            for (String identifier : addressed) {
                if (identifier.startsWith("BT-") && !declared.contains(identifier)) {
                    missing.add(identifier);
                }
            }
            if (!phantom.isEmpty() || !missing.isEmpty()) {
                wrong.add(rule.id() + " declares " + phantom + " without reading them, and reads "
                        + missing + " without declaring them");
            }
        }

        assertEquals(List.of(), wrong,
                "a rule's terms list is what a coverage table and a report quote");
    }

    /** Returns every business term and group the rule's statement and message address. */
    private static Set<String> addressed(RuleDefinition rule) {
        Set<String> identifiers = new TreeSet<>();
        collect(rule.assertion(), identifiers);
        for (Json binding : rule.bindings().values()) {
            collect(binding, identifiers);
        }
        Matcher placeholders = PLACEHOLDER.matcher(rule.message());
        while (placeholders.find()) {
            identifiers(placeholders.group(1), identifiers);
        }
        return identifiers;
    }

    /** Walks a rule expression and collects the identifiers of every path string in it. */
    private static void collect(Json value, Set<String> into) {
        if (value instanceof Json.Str str) {
            if (str.value().startsWith("/")) {
                identifiers(str.value(), into);
            }
        } else if (value instanceof Json.Arr arr) {
            arr.items().forEach(item -> collect(item, into));
        } else if (value instanceof Json.Obj obj) {
            for (Map.Entry<String, Json> member : obj.members().entrySet()) {
                collect(member.getValue(), into);
            }
        }
        // an integer or a boolean addresses nothing
    }

    private static void identifiers(String path, Set<String> into) {
        Matcher matcher = IDENTIFIER.matcher(path);
        while (matcher.find()) {
            into.add(matcher.group());
        }
    }
}
