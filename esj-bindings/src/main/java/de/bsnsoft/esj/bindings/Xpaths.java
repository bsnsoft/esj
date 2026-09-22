package de.bsnsoft.esj.bindings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the XPaths of a binding table into steps the reader can match a document against.
 *
 * <p>This is not an XPath engine and does not try to be one. The tables of
 * {@code model/bindings} write one shape of expression — an absolute path of child steps,
 * with a descendant step where a group nests inside itself, any number of predicates on a
 * step and an optional attribute as the last step — and this class reads exactly that
 * shape and refuses everything else by name. A binding table that grew an expression this
 * reader does not understand is a defect of the table or of this reader, and it is better
 * found when the table is compiled than when a document happens to walk into it.
 */
final class Xpaths {

    private Xpaths() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the element steps of an XPath, with the prefixes resolved.
     *
     * @param xpath      the expression, which begins with a slash
     * @param namespaces what each prefix of the table stands for
     * @return the steps, in document order
     * @throws BindingFormatException if the expression is not one this reader understands
     */
    static List<BindingTable.Step> steps(String xpath, Map<String, String> namespaces) {
        List<BindingTable.Step> steps = new ArrayList<>();
        boolean descendant = false;
        for (String part : split(xpath)) {
            if (part.isEmpty()) {
                if (descendant) {
                    throw new BindingFormatException("a binding XPath has an empty step: "
                            + xpath);
                }
                descendant = true;
                continue;
            }
            if (part.startsWith("@")) {
                throw new BindingFormatException("an attribute step stands at the end of an"
                        + " XPath and nowhere else: " + xpath);
            }
            int bracket = part.indexOf('[');
            String name = bracket < 0 ? part : part.substring(0, bracket);
            List<Predicate> predicates = new ArrayList<>(1);
            for (String body : brackets(part, bracket, xpath)) {
                predicates.add(predicate(body, namespaces, xpath));
            }
            Name expanded = name(name, namespaces, xpath);
            steps.add(new BindingTable.Step(expanded.namespace(), expanded.localName(),
                    predicates, descendant));
            descendant = false;
        }
        if (descendant || steps.isEmpty()) {
            throw new BindingFormatException("a binding XPath ends in an axis without a"
                    + " step: " + xpath);
        }
        return steps;
    }

    /**
     * Returns the bodies of the predicates a step carries, in the order they are written.
     *
     * <p>A step carries none, one, or more than one: the conditions of a step hold
     * together, and a term whose element has to be told from two others needs two of
     * them. The supporting document group of a UBL credit note is the case — the syntax
     * writes the project reference, the invoiced object identifier and the supporting
     * document into one element and tells them apart by a document type code — and it is
     * the only one the tables of this release state.
     *
     * @param part    the step as written, name and predicates
     * @param bracket where the first predicate opens, or {@code -1} for a step without one
     * @param xpath   the whole expression, for the message of a refusal
     */
    private static List<String> brackets(String part, int bracket, String xpath) {
        if (bracket < 0) {
            return List.of();
        }
        List<String> bodies = new ArrayList<>(1);
        int depth = 0;
        boolean quoted = false;
        int start = -1;
        for (int i = bracket; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '[') {
                if (depth == 0) {
                    if (start >= 0 || i > bracket && part.charAt(i - 1) != ']') {
                        throw new BindingFormatException("a step of a binding XPath writes"
                                + " its predicates one after another: " + xpath);
                    }
                    start = i + 1;
                }
                depth++;
            } else if (!quoted && c == ']') {
                depth--;
                if (depth == 0) {
                    bodies.add(part.substring(start, i));
                    start = -1;
                }
            }
        }
        if (depth != 0 || quoted || start >= 0
                || part.charAt(part.length() - 1) != ']') {
            throw new BindingFormatException("a step of a binding XPath has an unclosed"
                    + " predicate: " + xpath);
        }
        return bodies;
    }

    /**
     * Returns the local name of the attribute an XPath ends in, or {@code null} where it
     * ends in an element.
     *
     * @param xpath the expression
     * @return the attribute name, or {@code null}
     */
    static String attribute(String xpath) {
        List<String> parts = split(xpath);
        String last = parts.isEmpty() ? "" : parts.get(parts.size() - 1);
        return last.startsWith("@") ? last.substring(1) : null;
    }

    /**
     * Returns the XPath without the attribute it ends in, or the expression itself where
     * it ends in an element.
     *
     * @param xpath the expression
     * @return the expression down to its last element step
     */
    static String withoutAttribute(String xpath) {
        String attribute = attribute(xpath);
        return attribute == null ? xpath
                : xpath.substring(0, xpath.length() - attribute.length() - 2);
    }

    /**
     * Splits an expression into its steps at the slashes that separate them, leaving the
     * slashes inside a predicate alone. Two slashes in a row leave an empty part between
     * them, which is how the descendant axis reaches {@link #steps}.
     */
    private static List<String> split(String xpath) {
        if (!xpath.startsWith("/")) {
            throw new BindingFormatException("a binding XPath is absolute: " + xpath);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int i = 1; i < xpath.length(); i++) {
            char c = xpath.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '[') {
                depth++;
            } else if (!quoted && c == ']') {
                depth--;
            } else if (!quoted && depth == 0 && c == '/') {
                parts.add(part.toString());
                part.setLength(0);
                continue;
            }
            part.append(c);
        }
        if (depth != 0 || quoted) {
            throw new BindingFormatException("a binding XPath has an unclosed predicate or"
                    + " string: " + xpath);
        }
        parts.add(part.toString());
        return parts;
    }

    /** Returns the expanded name of one step, which may carry a prefix. */
    private static Name name(String step, Map<String, String> namespaces, String xpath) {
        int colon = step.indexOf(':');
        String prefix = colon < 0 ? "" : step.substring(0, colon);
        String local = colon < 0 ? step : step.substring(colon + 1);
        String namespace = namespaces.get(prefix);
        if (namespace == null) {
            throw new BindingFormatException("the binding table gives the prefix "
                    + (prefix.isEmpty() ? "of the default namespace" : prefix)
                    + " no namespace, and " + xpath + " uses it");
        }
        if (local.isEmpty()) {
            throw new BindingFormatException("a binding XPath has a step without a name: "
                    + xpath);
        }
        return new Name(namespace, local);
    }

    /** Returns the predicate of one step. */
    private static Predicate predicate(String text,
                                       Map<String, String> namespaces,
                                       String xpath) {
        String body = text.strip();
        if (body.startsWith("not(") && body.endsWith(")")) {
            String negated = body.substring(4, body.length() - 1).strip();
            if (!negated.startsWith("@")) {
                throw new BindingFormatException("a negated predicate reads an attribute of"
                        + " the element, and this one does not: " + xpath);
            }
            int equals = negated.indexOf('=');
            if (equals < 0) {
                throw new BindingFormatException("a negated predicate compares an attribute"
                        + " with a string, and this one does not: " + xpath);
            }
            return new Predicate(Predicate.Kind.ATTRIBUTE_NOT_EQUALS,
                    negated.substring(1, equals).strip(), List.of(),
                    literal(negated.substring(equals + 1), xpath));
        }
        if (body.startsWith("@")) {
            return attributePredicate(body.substring(1), namespaces, xpath);
        }
        int negated = body.indexOf("!=");
        if (negated >= 0) {
            return new Predicate(Predicate.Kind.CHILD_NOT_EQUALS, null,
                    relative(body.substring(0, negated), namespaces, xpath),
                    literal(body.substring(negated + 2), xpath));
        }
        int equals = body.indexOf('=');
        if (equals < 0) {
            throw new BindingFormatException("a predicate on an element step compares it"
                    + " with a string, and this one does not: " + xpath);
        }
        return new Predicate(Predicate.Kind.CHILD_EQUALS, null,
                relative(body.substring(0, equals), namespaces, xpath),
                literal(body.substring(equals + 1), xpath));
    }

    /** Returns a predicate that reads an attribute of the element. */
    private static Predicate attributePredicate(String body,
                                                Map<String, String> namespaces,
                                                String xpath) {
        int equals = body.indexOf('=');
        if (equals < 0) {
            return new Predicate(Predicate.Kind.ATTRIBUTE_PRESENT, body.strip(), List.of(),
                    null);
        }
        String attribute = body.substring(0, equals).strip();
        String right = body.substring(equals + 1).strip();
        if (right.startsWith("/")) {
            List<Name> reference = new ArrayList<>();
            for (BindingTable.Step step : steps(right, namespaces)) {
                if (!step.predicates().isEmpty() || step.descendant()) {
                    throw new BindingFormatException("the element a predicate refers to is"
                            + " named by a path of plain child steps: " + xpath);
                }
                reference.add(new Name(step.namespace(), step.localName()));
            }
            return new Predicate(Predicate.Kind.ATTRIBUTE_REFERENCE, attribute, reference,
                    null);
        }
        return new Predicate(Predicate.Kind.ATTRIBUTE_EQUALS, attribute, List.of(),
                literal(right, xpath));
    }

    /** Returns the names of a path relative to the element a predicate stands on. */
    private static List<Name> relative(String text,
                                       Map<String, String> namespaces,
                                       String xpath) {
        List<Name> names = new ArrayList<>();
        for (String step : text.strip().split("/")) {
            names.add(name(step.strip(), namespaces, xpath));
        }
        return names;
    }

    /** Returns the content of a string in single quotes. */
    private static String literal(String text, String xpath) {
        String body = text.strip();
        if (body.length() < 2 || body.charAt(0) != '\''
                || body.charAt(body.length() - 1) != '\'') {
            throw new BindingFormatException("a predicate compares with a string in single"
                    + " quotes, and this one does not: " + xpath);
        }
        return body.substring(1, body.length() - 1);
    }
}
