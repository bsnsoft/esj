package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a rule written in Java needs in order to speak the same language as a rule written in
 * the rule language: paths resolved against the registry, and message templates with the same
 * placeholders.
 *
 * <p>A rule in the rule language has its paths resolved once, when the pack is compiled. A
 * rule in Java writes its paths as strings in its own source, where the compiler of the pack
 * cannot see them, so they are resolved the first time the rule asks for one and kept. The
 * checks are the same ones — the term exists, the nesting is one the registry records, the
 * index rule holds — so a Java rule cannot reach a path that a rule of the language could
 * not have written, and a typo in one fails as loudly as a typo in the other.
 *
 * <p>The two caches are concurrent because a compiled engine may be used from several
 * threads at once, and a resolved path is the same answer for all of them. One cache holds
 * the patterns, and the check that a path addresses a single value runs on every call rather
 * than only on a cache miss: a path is compiled once, and what a caller may do with it is
 * decided by the accessor it asked through, never by which accessor reached the path first.
 */
final class Resolver {

    private final Compiler compiler;
    private final Compiler.Scope scope;
    private final Map<String, PathPattern> patterns = new ConcurrentHashMap<>();
    private final Map<String, MessageTemplate> templates = new ConcurrentHashMap<>();
    private final String where;

    Resolver(Compiler compiler, Compiler.Scope scope, String where) {
        this.compiler = compiler;
        this.scope = scope;
        this.where = where;
    }

    /**
     * Returns the registry the paths are resolved against.
     *
     * @return the registry
     */
    Registry registry() {
        return compiler.registry();
    }

    /**
     * Resolves a path a Java rule wrote, which may address one value or many.
     *
     * @param written the path, relative to the context of the rule
     * @return the pattern
     * @throws RulePackException if the path is not one the registry admits
     */
    PathPattern pattern(String written) {
        return patterns.computeIfAbsent(written,
                text -> PathPattern.compile(text, scope.terms(), compiler.registry(), where));
    }

    /**
     * Resolves a path a Java rule wrote and checks that it addresses one value.
     *
     * @param written the path
     * @return the pattern
     * @throws RulePackException if the path addresses a group or more than one value
     */
    PathPattern singleValuePath(String written) {
        return compiler.requireSingleValue(pattern(written), written, where);
    }

    /**
     * Returns the semantic data type of the term a pattern ends at.
     *
     * @param pattern the pattern
     * @return the semantic data type
     */
    SemanticType datatypeOf(PathPattern pattern) {
        return compiler.datatypeOf(pattern, where);
    }

    /**
     * Compiles a message template a Java rule wrote, with the same placeholders a rule of
     * the language has, except the bound names, which belong to a rule file.
     *
     * @param template the template
     * @return the compiled template
     * @throws RulePackException if the template names something that cannot be resolved
     */
    MessageTemplate template(String template) {
        return templates.computeIfAbsent(template,
                text -> MessageTemplate.compile(text, compiler, scope, Map.of(), where));
    }
}
