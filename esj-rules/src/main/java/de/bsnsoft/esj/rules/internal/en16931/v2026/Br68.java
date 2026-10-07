package de.bsnsoft.esj.rules.internal.en16931.v2026;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.validate.Severity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code BR-68}: one exemption reason and specification code is always explained by the same
 * exemption reason text.
 *
 * <p>The edition states the code and the text in four places — the VAT breakdown, the invoice
 * line, the document level allowance and the document level charge or tax — and asks that the
 * text be the same wherever a code repeats. Written in Java because the statement compares
 * values of different group instances with each other, which no operator of the rule language
 * reaches: an aggregate takes a sum, not a partition.
 *
 * <p>A place that states a code and no text says nothing about what the code means and is
 * passed over; the rule faults two places that state one code and two texts. The comparison
 * is exact, character for character: whether two differently worded texts mean the same is
 * not a question an engine answers, and a rule that normalised the texts would decide by its
 * own normalisation rather than by the document.
 *
 * <p>No official validation artefact exists for this edition, so nothing here is compared
 * with one. The rule is weighed by the hand-computed cases of {@code conformance/rules-2026}.
 */
public final class Br68 implements JavaRule {

    private static final List<String> CODES = List.of(
            "/BG-23/*/BT-121", "/BG-25/*/BG-30/BT-195", "/BG-20/*/BT-174", "/BG-21/*/BT-176");

    private static final List<String> TEXTS = List.of(
            "/BG-23/*/BT-120", "/BG-25/*/BG-30/BT-194", "/BG-20/*/BT-173", "/BG-21/*/BT-175");

    /** Creates the rule. */
    public Br68() {
    }

    @Override
    public String id() {
        return "BR-68";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String context() {
        return "/";
    }

    @Override
    public List<String> terms() {
        return List.of("BT-120", "BT-121", "BT-173", "BT-174", "BT-175", "BT-176", "BT-194",
                "BT-195");
    }

    @Override
    public List<String> roots() {
        List<String> roots = new ArrayList<>(CODES);
        roots.addAll(TEXTS);
        return List.copyOf(roots);
    }

    @Override
    public String source() {
        return "EN 16931-1:2026, 6.4.1, Table 3, BR-68";
    }

    @Override
    public Optional<String> check(RuleContext context) {
        Map<String, Map.Entry<SemanticPath, String>> seen = new LinkedHashMap<>();
        for (int i = 0; i < CODES.size(); i++) {
            Map<SemanticPath, String> texts = new LinkedHashMap<>();
            for (Map.Entry<SemanticPath, String> read : context.texts(TEXTS.get(i))) {
                texts.put(read.getKey().parent(), read.getValue());
            }
            for (Map.Entry<SemanticPath, String> read : context.texts(CODES.get(i))) {
                String text = texts.get(read.getKey().parent());
                if (text == null) {
                    continue;
                }
                Map.Entry<SemanticPath, String> first = seen.get(read.getValue());
                if (first == null) {
                    seen.put(read.getValue(), Map.entry(read.getKey(), text));
                    continue;
                }
                if (!first.getValue().equals(text)) {
                    return Optional.of("The exemption reason and specification code "
                            + context.escape(read.getValue()) + " is explained at " + first.getKey()
                            + " by one exemption reason text and at " + read.getKey()
                            + " by another; one code carries one text throughout an invoice.");
                }
            }
        }
        return Optional.empty();
    }
}
