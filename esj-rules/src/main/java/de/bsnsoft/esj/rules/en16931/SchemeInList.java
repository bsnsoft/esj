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
 * An identification scheme, where one is stated, is one the code list its business term
 * names carries.
 *
 * <p>Six rules of the official validation artefacts say that, of eleven business terms
 * between them, and none of the six can be written in the rule language for the reason
 * {@link SchemeIdentifier} gives: the language addresses business terms, and a scheme is a
 * supplementary component of a value rather than a term of its own. The membership test an
 * {@code inList} would have made is therefore made here, against the snapshot a rule file
 * would have named, and produces a finding of the same shape.
 *
 * <p>The scheme is optional on most of these terms, and a value that states none is not a
 * value these rules are about: the standard says which list a scheme is chosen from, not
 * that one has to be chosen. Where it does require one, a rule of its own says so.
 */
abstract class SchemeInList implements JavaRule {

    /**
     * One business term the rule is about.
     *
     * @param pattern the pattern of the term, from the document root
     * @param term    the identifier of the term
     * @param name    the name of the term, in English, for the message
     */
    record Scheme(String pattern, String term, String name) {
    }

    private final String id;
    private final String listId;
    private final String source;
    private final List<Scheme> schemes;

    /**
     * Creates the rule.
     *
     * @param id      the identifier the official validation artefacts give it
     * @param listId  the code list snapshot the scheme is decided against
     * @param source  the clause of the standard the rule states
     * @param schemes the business terms it is about
     */
    SchemeInList(String id, String listId, String source, List<Scheme> schemes) {
        this.id = id;
        this.listId = listId;
        this.source = source;
        this.schemes = List.copyOf(schemes);
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
        return schemes.stream().map(Scheme::term).toList();
    }

    @Override
    public final List<String> roots() {
        return schemes.stream().map(Scheme::pattern).toList();
    }

    @Override
    public final String source() {
        return source;
    }

    @Override
    public final Optional<String> check(RuleContext context) {
        CodeList list = context.codeList(listId);
        for (Scheme scheme : schemes) {
            for (Map.Entry<SemanticPath, SemanticValue> value : context.values(scheme.pattern())) {
                String stated = value.getValue().scheme();
                if (stated == null || list.contains(stated)) {
                    continue;
                }
                return Optional.of("The " + scheme.name() + " (" + scheme.term() + ") at "
                        + value.getKey() + " names the identification scheme "
                        + context.escape(stated) + ", which is not on the " + listId
                        + " snapshot this pack decides against.");
            }
        }
        return Optional.empty();
    }
}
