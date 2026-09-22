package de.bsnsoft.esj.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The rules written in Java that a caller hands to the engine.
 *
 * <p>A pack manifest names the classes of its Java rules; it does not load them. The
 * difference is the whole point. A file that named a class the engine then instantiated
 * would be a file that decides what code runs, and a validation pack is a file that may
 * arrive from a directory the caller was given. So the manifest is a statement of what the
 * pack contains, the caller passes the instances, and {@link RuleEngine#compile} checks that
 * exactly the named classes arrived: one missing is a pack that would silently check less
 * than it claims, and one too many is a rule nobody declared.
 *
 * <p>For the packs this repository publishes, both halves are in the same jar and the caller
 * is the command line, which passes the instances it was built with.
 */
public final class JavaRules {

    private final Map<String, JavaRule> byClassName;

    private JavaRules(Map<String, JavaRule> byClassName) {
        this.byClassName = byClassName;
    }

    /**
     * Collects rules.
     *
     * @param rules the instances
     * @return the collection
     * @throws RulePackException    if two instances are of the same class
     * @throws NullPointerException if {@code rules} or an element is {@code null}
     */
    public static JavaRules of(JavaRule... rules) {
        Objects.requireNonNull(rules, "rules");
        return of(List.of(rules));
    }

    /**
     * Collects rules.
     *
     * @param rules the instances
     * @return the collection
     * @throws RulePackException    if two instances are of the same class
     * @throws NullPointerException if {@code rules} or an element is {@code null}
     */
    public static JavaRules of(List<JavaRule> rules) {
        Objects.requireNonNull(rules, "rules");
        Map<String, JavaRule> byClassName = new LinkedHashMap<>();
        for (JavaRule rule : rules) {
            String name = rule.getClass().getName();
            if (byClassName.put(name, rule) != null) {
                throw new RulePackException("two instances of " + name + " were handed to the engine");
            }
        }
        return new JavaRules(Map.copyOf(byClassName));
    }

    /**
     * Collects no rules, for a pack that names none.
     *
     * @return the empty collection
     */
    public static JavaRules none() {
        return new JavaRules(Map.of());
    }

    /**
     * Returns the instance of a class the manifest names.
     *
     * @param className the class name, as the manifest writes it
     * @return the instance
     * @throws RulePackException if no instance of that class was handed to the engine
     */
    JavaRule require(String className) {
        JavaRule rule = byClassName.get(className);
        if (rule == null) {
            throw new RulePackException("the pack names the Java rule " + className
                    + ", and no instance of it was handed to the engine");
        }
        return rule;
    }

    /**
     * Returns the class names of the instances in this collection.
     *
     * @return the class names
     */
    Set<String> classNames() {
        return byClassName.keySet();
    }
}
