package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The message of a rule, with the places the values go.
 *
 * <p>A finding that says only that BR-CO-10 failed makes the reader open the invoice and do
 * the arithmetic. A finding that says which figure was expected, which was there and where
 * both live makes the reader fix the invoice. So a message is a template, and the values it
 * shows are read out of the document when the rule fires.
 *
 * <p>Four placeholders, and each of them is resolved when the pack is compiled, so a message
 * that names a business term nobody has is a defect of the pack rather than a surprise in a
 * report:
 *
 * <ul>
 *   <li>{@code {/BG-22/BT-106}} — the value at that path, relative to the rule's context,
 *       escaped as {@code SPEC.md} section 9.5 requires and cut to the excerpt length of
 *       {@link Texts}, so that a message quotes enough of a value to be recognised and
 *       never the whole of a large one. A value that is not there reads
 *       {@code (absent)}.</li>
 *   <li>{@code {@/BG-22/BT-106}} — the path itself, as the document addresses it, so that a
 *       reader can go to it.</li>
 *   <li>{@code {$name}} — an expression the rule bound under that name, which is how a sum
 *       or a difference gets into a message.</li>
 *   <li>{@code {.}} — the business group instance the rule is looking at, {@code /} for the
 *       document.</li>
 * </ul>
 *
 * <p>A literal brace is written twice.
 */
final class MessageTemplate {

    /** One piece of a message: a constant, or something read when the rule fires. */
    @FunctionalInterface
    private interface Part {
        String render(Evaluation evaluation, SemanticPath base);
    }

    private final List<Part> parts;

    private MessageTemplate(List<Part> parts) {
        this.parts = parts;
    }

    /**
     * Compiles a template.
     *
     * @param template the template as the rule file writes it
     * @param compiler the compiler, for the paths in the template
     * @param scope    the context of the rule
     * @param bindings the expressions the rule bound, already compiled
     * @param where    what is being compiled, for the message of a failure
     * @return the compiled template
     * @throws RulePackException if the template names something that cannot be resolved
     */
    static MessageTemplate compile(String template, Compiler compiler, Compiler.Scope scope,
                                   Map<String, Compiler.Compiled> bindings, String where) {
        List<Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '}' && i + 1 < template.length() && template.charAt(i + 1) == '}') {
                literal.append('}');
                i += 2;
                continue;
            }
            if (c != '{') {
                literal.append(c);
                i++;
                continue;
            }
            if (i + 1 < template.length() && template.charAt(i + 1) == '{') {
                literal.append('{');
                i += 2;
                continue;
            }
            int end = template.indexOf('}', i + 1);
            if (end < 0) {
                throw new RulePackException(where + ": the message opens a placeholder at "
                        + i + " and never closes it");
            }
            if (literal.length() > 0) {
                String text = literal.toString();
                parts.add((evaluation, base) -> text);
                literal.setLength(0);
            }
            parts.add(placeholder(template.substring(i + 1, end), compiler, scope, bindings, where));
            i = end + 1;
        }
        if (literal.length() > 0) {
            String text = literal.toString();
            parts.add((evaluation, base) -> text);
        }
        return new MessageTemplate(List.copyOf(parts));
    }

    private static Part placeholder(String name, Compiler compiler, Compiler.Scope scope,
                                    Map<String, Compiler.Compiled> bindings, String where) {
        if (name.equals(".")) {
            return (evaluation, base) -> base.isRoot() ? "/" : base.toString();
        }
        if (name.startsWith("$")) {
            Compiler.Compiled bound = bindings.get(name.substring(1));
            if (bound == null) {
                throw new RulePackException(where + ": the message names " + name
                        + ", which the rule does not bind");
            }
            return (evaluation, base) -> bound.expression().evaluate(evaluation, base).display();
        }
        if (name.startsWith("@")) {
            PathPattern pattern = compiler.singleValuePath(name.substring(1), scope, where);
            return (evaluation, base) -> pattern.absoluteText(base);
        }
        if (name.startsWith("/")) {
            PathPattern pattern = compiler.singleValuePath(name, scope, where);
            SemanticType type = compiler.datatypeOf(pattern, where);
            return (evaluation, base) -> evaluation.read(pattern, base, type).display();
        }
        throw new RulePackException(where + ": {" + name + "} is not a placeholder;"
                + " a placeholder is a path, a path after @, a bound name after $, or a dot");
    }

    /**
     * Renders the message.
     *
     * @param evaluation the run
     * @param base       the business group instance the rule is evaluated at, or the root
     * @return the message
     * @throws Undecided if a value the message reads does not spell what its semantic data
     *                   type requires
     */
    String expand(Evaluation evaluation, SemanticPath base) {
        StringBuilder message = new StringBuilder();
        for (Part part : parts) {
            message.append(part.render(evaluation, base));
        }
        return message.toString();
    }
}
