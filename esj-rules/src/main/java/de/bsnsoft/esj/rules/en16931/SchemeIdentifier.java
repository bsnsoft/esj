package de.bsnsoft.esj.rules.en16931;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An identifier that is only meaningful together with the scheme it was issued under carries
 * that scheme.
 *
 * <p>Four business terms of the standard say so, and none of them can say it in the rule
 * language. The language addresses business terms and a scheme is not one: it is a
 * supplementary component of a value, which the specification records beside the content
 * rather than as a term of its own, so no path reaches it and no operator asks about it.
 * Adding an operator for it would put a second addressing scheme into a language whose whole
 * claim is that it addresses the semantic model.
 *
 * <p>The rule is a statement about the document, because two of the four terms sit inside the
 * invoice line and one of those repeats; reading them as a pattern at the document context is
 * one pass either way.
 */
abstract class SchemeIdentifier implements JavaRule {

    private final String id;
    private final String pattern;
    private final String term;
    private final String name;
    private final String source;

    /**
     * Creates the rule.
     *
     * @param id      the identifier the standard gives it
     * @param pattern the pattern of the business term, from the document root
     * @param term    the identifier of that business term
     * @param name    the name of the business term, in English, for the message
     * @param source  the clause of the standard the rule states
     */
    SchemeIdentifier(String id, String pattern, String term, String name, String source) {
        this.id = id;
        this.pattern = pattern;
        this.term = term;
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
        return List.of(term);
    }

    @Override
    public final List<String> roots() {
        return List.of(pattern);
    }

    @Override
    public final String source() {
        return source;
    }

    @Override
    public final Optional<String> check(RuleContext context) {
        for (Map.Entry<SemanticPath, SemanticValue> value : context.values(pattern)) {
            if (value.getValue().scheme() == null) {
                return Optional.of("The " + name + " (" + term + ") at " + value.getKey() + " is "
                        + context.escape(value.getValue().asString()) + " and names no identification"
                        + " scheme; an identifier of this term is read together with its scheme.");
            }
        }
        return Optional.empty();
    }
}
