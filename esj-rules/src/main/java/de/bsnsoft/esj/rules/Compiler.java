package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns the expression of a rule into something that can be evaluated, and refuses
 * everything it cannot turn.
 *
 * <p>The operator set is closed. There are thirty-one operators and there is no way to add
 * another without changing this class and {@code rules/rule.schema.json} together, which
 * is the point: a rule file is data, and data that could name an arbitrary function would be
 * code. A closed set is also what lets the same rule file be read by an implementation in
 * another language — the reason the rules of this project are JSON and not a script.
 *
 * <p>Everything that does not depend on the document is settled here. A path becomes a
 * resolved pattern with the semantic data type the registry gives its term; a literal beside
 * a decimal becomes that decimal; a code list identifier becomes the snapshot the pack
 * manifest names; a regular expression is compiled once. What is left is a tree of small
 * operations over values, which is what gets evaluated once per invoice line.
 *
 * <p>The compiler also collects what the document index will be asked for: the patterns that
 * are evaluated from the document root and therefore have to be answered out of one pass.
 * A pattern inside a business group instance is not collected, because its answer lies in
 * one subtree and the sorted map hands that out directly.
 */
final class Compiler {

    /**
     * The number of fraction digits a division falls back to where the exact quotient does
     * not terminate.
     *
     * <p>Exact arithmetic is the rule of this project and division is the one operation
     * that cannot always keep it: one divided by three has no decimal expansion, and an
     * engine must either refuse the rule or name a working precision. It names one, far
     * beyond any figure an invoice carries, and says so here and in
     * {@code rules/README.md}. Every amount a rule compares is rounded to two decimals by
     * the rule that computes it, so the choice can change nothing a rule decides; it exists
     * so that a rule which divides by a base quantity of three has a number to go on rather
     * than an exception.
     */
    private static final int DIVISION_SCALE = 34;

    /** The largest scale {@code round} and {@code decimals} accept. */
    private static final int MAX_SCALE = 20;

    private final Registry registry;
    private final CodeLists codeLists;
    private final Set<String> rootValueKeys;
    private final Set<String> rootGroupKeys;

    Compiler(Registry registry, CodeLists codeLists,
             Set<String> rootValueKeys, Set<String> rootGroupKeys) {
        this.registry = registry;
        this.codeLists = codeLists;
        this.rootValueKeys = rootValueKeys;
        this.rootGroupKeys = rootGroupKeys;
    }

    /** What an expression evaluates to, and what the compiler knows about it. */
    record Compiled(Expression expression, RuleType type, String literal) {
    }

    /**
     * Where an expression stands: the business terms of the enclosing context, and whether
     * that context is the document itself.
     */
    record Scope(List<String> terms, boolean rootBased) {
    }

    /**
     * Returns the registry the paths of this pack are resolved against.
     *
     * @return the registry
     */
    Registry registry() {
        return registry;
    }

    /**
     * Compiles one expression.
     *
     * @param json  the expression as the rule file writes it
     * @param scope where it stands
     * @param where what is being compiled, for the message of a failure
     * @return the compiled expression
     * @throws RulePackException if the expression is not one this language has
     */
    Compiled compile(Json json, Scope scope, String where) {
        Map<String, Json> members = json.asObject(where + ": an expression");
        if (members.size() != 1) {
            throw new RulePackException(where + ": an expression is an object with exactly one"
                    + " operator, and this one names " + members.size());
        }
        Map.Entry<String, Json> only = members.entrySet().iterator().next();
        String operator = only.getKey();
        Json argument = only.getValue();
        String at = where + ", " + operator;
        return switch (operator) {
            case "value" -> value(argument, scope, at);
            case "const" -> literal(argument, at);
            case "exists" -> presence(argument, scope, at, true);
            case "absent" -> presence(argument, scope, at, false);
            case "eq", "ne", "lt", "le", "gt", "ge" -> comparison(operator, argument, scope, at);
            case "add", "sub", "mul", "div" -> arithmetic(operator, argument, scope, at);
            case "sum", "min", "max" -> aggregate(operator, argument, scope, at);
            case "count" -> count(argument, scope, at);
            case "round" -> round(argument, scope, at);
            case "decimals" -> decimals(argument, scope, at);
            case "abs" -> abs(argument, scope, at);
            case "inList" -> inList(argument, scope, at);
            case "matches" -> matches(argument, scope, at);
            case "len" -> len(argument, scope, at);
            case "and", "or" -> junction(operator, argument, scope, at);
            case "not" -> negation(argument, scope, at);
            case "if" -> conditional(argument, scope, at);
            case "forEach", "all", "any" -> quantifier(operator, argument, scope, at);
            default -> throw new RulePackException(where + ": " + operator
                    + " is not an operator of this language");
        };
    }

    /**
     * Compiles a path a message or a rule writes, and checks that it addresses one value.
     *
     * @param written the path
     * @param scope   where it stands
     * @param where   what is being compiled, for the message of a failure
     * @return the pattern
     * @throws RulePackException if the path is not one value of a business term
     */
    PathPattern singleValuePath(String written, Scope scope, String where) {
        return requireSingleValue(PathPattern.compile(written, scope.terms(), registry, where),
                written, where);
    }

    /**
     * Checks that a pattern already compiled addresses one value of a business term.
     *
     * <p>The two questions are about the pattern and not about the act of compiling it, which
     * is why they are asked here rather than inside a cache's mapping function: a caller that
     * hands over a pattern someone else compiled gets the same refusal as the caller that
     * compiled it.
     *
     * @param pattern the compiled pattern
     * @param written the path as it was written, for the message of a failure
     * @param where   what is being compiled, for the message of a failure
     * @return the same pattern
     * @throws RulePackException if the path is not one value of a business term
     */
    PathPattern requireSingleValue(PathPattern pattern, String written, String where) {
        if (pattern.endsAtGroup()) {
            throw new RulePackException(where + ": " + written
                    + " addresses a business group, and only a business term carries a value");
        }
        if (pattern.hasWildcard()) {
            throw new RulePackException(where + ": " + written
                    + " addresses more than one value; an aggregate or forEach takes those");
        }
        return pattern;
    }

    /**
     * Returns the semantic data type of the term a pattern ends at.
     *
     * @param pattern the pattern
     * @param where   what is being compiled, for the message of a failure
     * @return the semantic data type
     */
    SemanticType datatypeOf(PathPattern pattern, String where) {
        return registry.datatype(pattern.lastTerm()).orElseThrow(() -> new RulePackException(
                where + ": the registry gives " + pattern.lastTerm() + " no semantic data type"));
    }

    private Compiled value(Json argument, Scope scope, String where) {
        PathPattern pattern = singleValuePath(argument.asString(where), scope, where);
        SemanticType type = datatypeOf(pattern, where);
        return new Compiled((evaluation, base) -> evaluation.read(pattern, base, type),
                ruleType(type), null);
    }

    private Compiled literal(Json argument, String where) {
        if (argument instanceof Json.Str text) {
            RuleValue value = RuleValue.of(text.value());
            return new Compiled((evaluation, base) -> value, RuleType.ANY, text.value());
        }
        if (argument instanceof Json.Int number) {
            RuleValue value = RuleValue.of(number.value());
            return new Compiled((evaluation, base) -> value, RuleType.DECIMAL, null);
        }
        if (argument instanceof Json.Bool truth) {
            RuleValue value = RuleValue.of(truth.value());
            return new Compiled((evaluation, base) -> value, RuleType.BOOLEAN, null);
        }
        throw new RulePackException(where + ": a literal is a string, a whole number or a"
                + " truth value");
    }

    private Compiled presence(Json argument, Scope scope, String where, boolean wanted) {
        PathPattern pattern = PathPattern.compile(argument.asString(where), scope.terms(), registry, where);
        note(pattern, scope);
        boolean group = pattern.endsAtGroup();
        return new Compiled((evaluation, base) -> {
            boolean there = group
                    ? !evaluation.instances(pattern, base).isEmpty()
                    : !evaluation.matches(pattern, base).isEmpty();
            return RuleValue.of(there == wanted);
        }, RuleType.BOOLEAN, null);
    }

    private Compiled comparison(String operator, Json argument, Scope scope, String where) {
        List<Compiled> operands = operands(argument, scope, where, 2, 2);
        RuleType type = unify(operands.get(0).type(), operands.get(1).type(), where);
        if (type == RuleType.BOOLEAN && !operator.equals("eq") && !operator.equals("ne")) {
            throw new RulePackException(where + ": a truth value is compared with eq or ne only");
        }
        Expression left = coerce(operands.get(0), type, where).expression();
        Expression right = coerce(operands.get(1), type, where).expression();
        return new Compiled((evaluation, base) -> {
            RuleValue a = left.evaluate(evaluation, base);
            RuleValue b = right.evaluate(evaluation, base);
            if (a.isAbsent() || b.isAbsent()) {
                return RuleValue.ABSENT;
            }
            int order = order(a, b);
            return RuleValue.of(switch (operator) {
                case "eq" -> order == 0;
                case "ne" -> order != 0;
                case "lt" -> order < 0;
                case "le" -> order <= 0;
                case "gt" -> order > 0;
                default -> order >= 0;
            });
        }, RuleType.BOOLEAN, null);
    }

    private static int order(RuleValue a, RuleValue b) {
        if (a.isDecimal()) {
            return a.decimal().compareTo(b.decimal());
        }
        if (a.isDate()) {
            return a.date().compareTo(b.date());
        }
        if (a.isBoolean()) {
            return Boolean.compare(a.truth(), b.truth());
        }
        return codePointOrder(a.text(), b.text());
    }

    private static int codePointOrder(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int left = a.codePointAt(i);
            int right = b.codePointAt(j);
            if (left != right) {
                return Integer.compare(left, right);
            }
            i += Character.charCount(left);
            j += Character.charCount(right);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private Compiled arithmetic(String operator, Json argument, Scope scope, String where) {
        boolean binary = operator.equals("sub") || operator.equals("div");
        List<Compiled> operands = operands(argument, scope, where, 2, binary ? 2 : Integer.MAX_VALUE);
        List<Expression> parts = new ArrayList<>();
        for (Compiled operand : operands) {
            parts.add(coerce(operand, RuleType.DECIMAL, where).expression());
        }
        return new Compiled((evaluation, base) -> {
            BigDecimal total = null;
            for (Expression part : parts) {
                RuleValue next = part.evaluate(evaluation, base);
                if (next.isAbsent()) {
                    return RuleValue.ABSENT;
                }
                if (total == null) {
                    total = next.decimal();
                    continue;
                }
                total = switch (operator) {
                    case "add" -> total.add(next.decimal());
                    case "sub" -> total.subtract(next.decimal());
                    case "mul" -> total.multiply(next.decimal());
                    default -> divide(total, next.decimal());
                };
                if (total == null) {
                    return RuleValue.ABSENT;
                }
            }
            return RuleValue.of(total);
        }, RuleType.DECIMAL, null);
    }

    private static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            return null;
        }
        try {
            return dividend.divide(divisor);
        } catch (ArithmeticException nonTerminating) {
            return dividend.divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP);
        }
    }

    private Compiled aggregate(String operator, Json argument, Scope scope, String where) {
        if (argument instanceof Json.Str written) {
            return overPattern(operator, written.value(), scope, where);
        }
        List<Compiled> operands = operands(argument, scope, where, 1, Integer.MAX_VALUE);
        List<Expression> parts = new ArrayList<>();
        for (Compiled operand : operands) {
            parts.add(coerce(operand, RuleType.DECIMAL, where).expression());
        }
        return new Compiled((evaluation, base) -> {
            List<BigDecimal> numbers = new ArrayList<>(parts.size());
            for (Expression part : parts) {
                RuleValue next = part.evaluate(evaluation, base);
                if (next.isAbsent()) {
                    return RuleValue.ABSENT;
                }
                numbers.add(next.decimal());
            }
            return fold(operator, numbers);
        }, RuleType.DECIMAL, null);
    }

    private Compiled overPattern(String operator, String written, Scope scope, String where) {
        PathPattern pattern = PathPattern.compile(written, scope.terms(), registry, where);
        if (pattern.endsAtGroup()) {
            throw new RulePackException(where + ": " + written
                    + " addresses business group instances, and " + operator + " takes values");
        }
        SemanticType type = datatypeOf(pattern, where);
        if (!type.isDecimal()) {
            throw new RulePackException(where + ": " + pattern.lastTerm() + " is "
                    + type.registryDatatype() + ", and " + operator + " takes a numeric term");
        }
        note(pattern, scope);
        boolean remember = scope.rootBased();
        return new Compiled((evaluation, base) -> {
            String key = operator + " " + pattern.absoluteText(base);
            evaluation.record(pattern.absoluteText(base));
            return evaluation.aggregate(key, remember, () -> {
                List<BigDecimal> numbers = new ArrayList<>();
                for (Map.Entry<SemanticPath, SemanticValue> match : evaluation.matches(pattern, base)) {
                    numbers.add(Evaluation.convert(match.getValue(), type, match.getKey()).decimal());
                }
                return fold(operator, numbers);
            });
        }, RuleType.DECIMAL, null);
    }

    private static RuleValue fold(String operator, List<BigDecimal> numbers) {
        if (numbers.isEmpty()) {
            return operator.equals("sum") ? RuleValue.of(BigDecimal.ZERO) : RuleValue.ABSENT;
        }
        BigDecimal result = numbers.get(0);
        for (int i = 1; i < numbers.size(); i++) {
            BigDecimal next = numbers.get(i);
            result = switch (operator) {
                case "sum" -> result.add(next);
                case "min" -> result.compareTo(next) <= 0 ? result : next;
                default -> result.compareTo(next) >= 0 ? result : next;
            };
        }
        return RuleValue.of(result);
    }

    private Compiled count(Json argument, Scope scope, String where) {
        PathPattern pattern = PathPattern.compile(argument.asString(where), scope.terms(), registry, where);
        note(pattern, scope);
        boolean group = pattern.endsAtGroup();
        boolean remember = scope.rootBased();
        return new Compiled((evaluation, base) -> {
            String key = "count " + pattern.absoluteText(base);
            evaluation.record(pattern.absoluteText(base));
            return evaluation.aggregate(key, remember, () -> RuleValue.of(group
                    ? evaluation.instances(pattern, base).size()
                    : evaluation.matches(pattern, base).size()));
        }, RuleType.DECIMAL, null);
    }

    private Compiled round(Json argument, Scope scope, String where) {
        List<Json> parts = argument.asArray(where);
        if (parts.size() != 2) {
            throw new RulePackException(where + ": round takes an expression and a scale");
        }
        Expression value = coerce(compile(parts.get(0), scope, where), RuleType.DECIMAL, where).expression();
        int scale = parts.get(1).asInt(where + ": the scale of round", 0, MAX_SCALE);
        return new Compiled((evaluation, base) -> {
            RuleValue number = value.evaluate(evaluation, base);
            return number.isAbsent()
                    ? RuleValue.ABSENT
                    : RuleValue.of(number.decimal().setScale(scale, RoundingMode.HALF_UP));
        }, RuleType.DECIMAL, null);
    }

    private Compiled decimals(Json argument, Scope scope, String where) {
        List<Json> parts = argument.asArray(where);
        if (parts.size() != 2) {
            throw new RulePackException(where + ": decimals takes a path and a maximum scale");
        }
        PathPattern pattern = singleValuePath(parts.get(0).asString(where), scope, where);
        SemanticType type = datatypeOf(pattern, where);
        if (type != SemanticType.AMOUNT) {
            throw new RulePackException(where + ": " + pattern.lastTerm() + " is "
                    + type.registryDatatype() + ", and decimals applies to Amount alone."
                    + " Unit Price Amount, Quantity and Percentage have no fixed scale in"
                    + " EN 16931-1, 6.5, and a rule that capped one of them would refuse a"
                    + " net price this standard expects to carry many fraction digits");
        }
        int maxScale = parts.get(1).asInt(where + ": the maximum scale of decimals", 0, MAX_SCALE);
        return new Compiled((evaluation, base) -> {
            RuleValue number = evaluation.read(pattern, base, type);
            if (number.isAbsent()) {
                return RuleValue.ABSENT;
            }
            return RuleValue.of(Math.max(0, number.decimal().stripTrailingZeros().scale()) <= maxScale);
        }, RuleType.BOOLEAN, null);
    }

    private Compiled abs(Json argument, Scope scope, String where) {
        Expression value = coerce(compile(argument, scope, where), RuleType.DECIMAL, where).expression();
        return new Compiled((evaluation, base) -> {
            RuleValue number = value.evaluate(evaluation, base);
            return number.isAbsent() ? RuleValue.ABSENT : RuleValue.of(number.decimal().abs());
        }, RuleType.DECIMAL, null);
    }

    private Compiled inList(Json argument, Scope scope, String where) {
        List<Json> parts = argument.asArray(where);
        if (parts.size() != 2) {
            throw new RulePackException(where + ": inList takes an expression and a list identifier");
        }
        Expression value = coerce(compile(parts.get(0), scope, where), RuleType.TEXT, where).expression();
        CodeList list = codeLists.require(parts.get(1).asString(where + ": the list identifier"));
        return new Compiled((evaluation, base) -> {
            RuleValue code = value.evaluate(evaluation, base);
            return code.isAbsent() ? RuleValue.ABSENT : RuleValue.of(list.contains(code.text()));
        }, RuleType.BOOLEAN, null);
    }

    private Compiled matches(Json argument, Scope scope, String where) {
        List<Json> parts = argument.asArray(where);
        if (parts.size() != 2) {
            throw new RulePackException(where + ": matches takes an expression and a pattern");
        }
        Expression value = coerce(compile(parts.get(0), scope, where), RuleType.TEXT, where).expression();
        String written = parts.get(1).asString(where + ": the pattern");
        Pattern regex;
        try {
            regex = Pattern.compile(written);
        } catch (PatternSyntaxException e) {
            throw new RulePackException(where + ": " + written + " is not a pattern: " + e.getMessage(), e);
        }
        return new Compiled((evaluation, base) -> {
            RuleValue text = value.evaluate(evaluation, base);
            return text.isAbsent() ? RuleValue.ABSENT : RuleValue.of(regex.matcher(text.text()).matches());
        }, RuleType.BOOLEAN, null);
    }

    private Compiled len(Json argument, Scope scope, String where) {
        Expression value = coerce(compile(argument, scope, where), RuleType.TEXT, where).expression();
        return new Compiled((evaluation, base) -> {
            RuleValue text = value.evaluate(evaluation, base);
            return text.isAbsent()
                    ? RuleValue.ABSENT
                    : RuleValue.of(text.text().codePointCount(0, text.text().length()));
        }, RuleType.DECIMAL, null);
    }

    private Compiled junction(String operator, Json argument, Scope scope, String where) {
        List<Compiled> operands = operands(argument, scope, where, 2, Integer.MAX_VALUE);
        List<Expression> parts = new ArrayList<>();
        for (Compiled operand : operands) {
            parts.add(coerce(operand, RuleType.BOOLEAN, where).expression());
        }
        boolean conjunction = operator.equals("and");
        return new Compiled((evaluation, base) -> {
            boolean undecided = false;
            for (Expression part : parts) {
                RuleValue next = part.evaluate(evaluation, base);
                if (next.isAbsent()) {
                    undecided = true;
                    continue;
                }
                if (next.truth() != conjunction) {
                    return RuleValue.of(!conjunction);
                }
            }
            return undecided ? RuleValue.ABSENT : RuleValue.of(conjunction);
        }, RuleType.BOOLEAN, null);
    }

    private Compiled negation(Json argument, Scope scope, String where) {
        Expression value = coerce(compile(argument, scope, where), RuleType.BOOLEAN, where).expression();
        return new Compiled((evaluation, base) -> {
            RuleValue truth = value.evaluate(evaluation, base);
            return truth.isAbsent() ? RuleValue.ABSENT : RuleValue.of(!truth.truth());
        }, RuleType.BOOLEAN, null);
    }

    private Compiled conditional(Json argument, Scope scope, String where) {
        Map<String, Json> members = argument.asObject(where);
        reject(members.keySet(), Set.of("condition", "then", "else"), where, "if");
        Json condition = members.get("condition");
        Json then = members.get("then");
        if (condition == null || then == null) {
            throw new RulePackException(where + ": if takes a condition and a then");
        }
        Expression test = coerce(compile(condition, scope, where), RuleType.BOOLEAN, where).expression();
        Compiled consequent = compile(then, scope, where);
        Compiled alternative = members.containsKey("else")
                ? compile(members.get("else"), scope, where)
                : new Compiled((evaluation, base) -> RuleValue.TRUE, RuleType.BOOLEAN, null);
        RuleType type = unify(consequent.type(), alternative.type(), where);
        Expression yes = coerce(consequent, type, where).expression();
        Expression no = coerce(alternative, type, where).expression();
        return new Compiled((evaluation, base) -> {
            RuleValue truth = test.evaluate(evaluation, base);
            if (truth.isAbsent()) {
                return RuleValue.ABSENT;
            }
            return truth.truth() ? yes.evaluate(evaluation, base) : no.evaluate(evaluation, base);
        }, type, null);
    }

    private Compiled quantifier(String operator, Json argument, Scope scope, String where) {
        Map<String, Json> members = argument.asObject(where);
        reject(members.keySet(), Set.of("group", "assert"), where, operator);
        Json group = members.get("group");
        Json assertion = members.get("assert");
        if (group == null || assertion == null) {
            throw new RulePackException(where + ": " + operator + " takes a group and an assert");
        }
        PathPattern pattern = PathPattern.compile(group.asString(where), scope.terms(), registry, where);
        if (!pattern.endsAtGroup()) {
            throw new RulePackException(where + ": " + operator
                    + " walks business group instances, and " + pattern.lastTerm()
                    + " is a business term");
        }
        note(pattern, scope);
        List<String> inner = new ArrayList<>(scope.terms());
        inner.addAll(pattern.terms());
        Expression body = coerce(compile(assertion, new Scope(List.copyOf(inner), false), where),
                RuleType.BOOLEAN, where).expression();
        boolean existential = operator.equals("any");
        boolean report = operator.equals("forEach");
        return new Compiled((evaluation, base) -> {
            boolean undecided = false;
            for (SemanticPath instance : evaluation.instances(pattern, base)) {
                RuleValue truth = body.evaluate(evaluation, instance);
                if (truth.isAbsent()) {
                    undecided = true;
                    continue;
                }
                if (truth.truth() == existential) {
                    if (report) {
                        evaluation.record(instance.toString());
                    }
                    return RuleValue.of(existential);
                }
            }
            return undecided ? RuleValue.ABSENT : RuleValue.of(!existential);
        }, RuleType.BOOLEAN, null);
    }

    private List<Compiled> operands(Json argument, Scope scope, String where, int min, int max) {
        List<Json> parts = argument.asArray(where);
        if (parts.size() < min || parts.size() > max) {
            throw new RulePackException(where + ": " + parts.size() + " operands, where "
                    + (min == max ? "exactly " + min : "at least " + min) + " belong");
        }
        List<Compiled> compiled = new ArrayList<>(parts.size());
        for (Json part : parts) {
            compiled.add(compile(part, scope, where));
        }
        return compiled;
    }

    private void note(PathPattern pattern, Scope scope) {
        if (scope.rootBased()) {
            foresee(pattern);
        }
    }

    /**
     * Records that a pattern will be asked about from the document root, so that the index of
     * a document is built for it in the one pass rather than rebuilt when it is first asked.
     *
     * <p>It is a saving and not a requirement. A pattern nobody foresaw is still answered
     * correctly ({@link DocumentIndex}); it costs one more pass over the document, which is
     * why the contexts of the rules and the root patterns a Java rule declares are registered
     * here while the pack is compiled.
     *
     * @param pattern a pattern that will be evaluated from the document root
     */
    void foresee(PathPattern pattern) {
        if (pattern.hasWildcard()) {
            (pattern.endsAtGroup() ? rootGroupKeys : rootValueKeys).add(pattern.text());
        }
    }

    private static RuleType ruleType(SemanticType type) {
        if (type.isDecimal()) {
            return RuleType.DECIMAL;
        }
        return type == SemanticType.DATE ? RuleType.DATE : RuleType.TEXT;
    }

    private static RuleType unify(RuleType left, RuleType right, String where) {
        if (left == right) {
            return left == RuleType.ANY ? RuleType.TEXT : left;
        }
        if (left == RuleType.ANY) {
            return right;
        }
        if (right == RuleType.ANY) {
            return left;
        }
        throw new RulePackException(where + ": " + left.token() + " and " + right.token()
                + " are not compared or combined in this language");
    }

    private static Compiled coerce(Compiled compiled, RuleType target, String where) {
        if (compiled.type() == target) {
            return compiled;
        }
        if (compiled.type() != RuleType.ANY || compiled.literal() == null) {
            throw new RulePackException(where + ": " + compiled.type().token()
                    + " stands where " + target.token() + " belongs");
        }
        String text = compiled.literal();
        RuleValue value = switch (target) {
            case DECIMAL -> RuleValue.of(decimalLiteral(text, where));
            case DATE -> RuleValue.of(dateLiteral(text, where));
            case TEXT -> RuleValue.of(text);
            default -> throw new RulePackException(where + ": the literal " + text
                    + " stands where " + target.token() + " belongs");
        };
        return new Compiled((evaluation, base) -> value, target, null);
    }

    private static BigDecimal decimalLiteral(String text, String where) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new RulePackException(where + ": the literal " + text
                    + " stands beside a decimal and is not one", e);
        }
    }

    private static LocalDate dateLiteral(String text, String where) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new RulePackException(where + ": the literal " + text
                    + " stands beside a date and is not one", e);
        }
    }

    private static void reject(Set<String> present, Set<String> known, String where, String operator) {
        for (String member : present) {
            if (!known.contains(member)) {
                throw new RulePackException(where + ": " + operator + " has no member " + member);
            }
        }
    }
}
