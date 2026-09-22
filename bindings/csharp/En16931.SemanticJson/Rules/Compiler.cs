using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using En16931.SemanticJson.Model;

namespace En16931.SemanticJson.Rules;

/// <summary>A compiled expression of the rule language.</summary>
/// <param name="evaluation">the run: the document, the index and what has been read so far</param>
/// <param name="basePath">the business group instance the rule is evaluated at, or the root</param>
/// <returns>the value, which may be absent</returns>
public delegate RuleValue Expression(Evaluation evaluation, SemanticPath basePath);

/// <summary>What kind of thing an expression produces, as far as the compiler can tell.</summary>
internal enum RuleType
{
    /// <summary>An exact decimal number.</summary>
    Decimal,

    /// <summary>A calendar date.</summary>
    Date,

    /// <summary>Text: the content of a value whose semantic data type is none of the numeric ones.</summary>
    Text,

    /// <summary>A truth value, or the absence of one.</summary>
    Boolean,

    /// <summary>A literal whose type the compiler takes from what it stands beside.</summary>
    Any,
}

/// <summary>Where an expression stands.</summary>
/// <param name="Terms">the business terms of the enclosing context</param>
/// <param name="RootBased">whether that context is the document itself</param>
internal sealed record Scope(IReadOnlyList<string> Terms, bool RootBased);

/// <summary>What an expression evaluates to, and what the compiler knows about it.</summary>
internal sealed record Compiled(Expression Expression, RuleType Type, string? Literal);

/// <summary>
/// Turns the expression of a rule into something that can be evaluated, and refuses
/// everything it cannot turn.
/// </summary>
/// <remarks>
/// The operator set is closed. There are thirty-one operators and there is no way to add
/// another without changing this class and <c>rules/rule.schema.json</c> together, which is
/// the point: a rule file is data, and data that could name an arbitrary function would be
/// code. A closed set is also what lets the same rule file be read by an implementation in
/// another language, which is what this one is. Everything that does not depend on the
/// document is settled here: a path becomes a resolved pattern with the semantic data type
/// the registry gives its term, a literal beside a decimal becomes that decimal, a code list
/// identifier becomes the snapshot the pack manifest names, and a regular expression is
/// compiled once.
/// </remarks>
internal sealed class Compiler
{
    /// <summary>
    /// The fraction digits a division whose quotient does not terminate is computed to. Exact
    /// arithmetic is the rule and division is the one operation that cannot always keep it, so
    /// the engine names a working precision far beyond any figure an invoice carries; every
    /// amount a rule compares is rounded to two decimals by the rule that computes it, so the
    /// choice can change nothing a rule decides.
    /// </summary>
    private const int DivisionScale = 34;

    /// <summary>The largest scale <c>round</c> and <c>decimals</c> accept.</summary>
    private const int MaxScale = 20;

    private readonly CodeLists _codeLists;
    private readonly HashSet<string> _rootValueKeys;
    private readonly HashSet<string> _rootGroupKeys;

    internal Compiler(
        Registry registry, CodeLists codeLists, HashSet<string> rootValueKeys, HashSet<string> rootGroupKeys)
    {
        Registry = registry;
        _codeLists = codeLists;
        _rootValueKeys = rootValueKeys;
        _rootGroupKeys = rootGroupKeys;
    }

    /// <summary>Returns the registry the paths of this pack are resolved against.</summary>
    internal Registry Registry { get; }

    /// <summary>Compiles one expression.</summary>
    /// <param name="json">the expression as the rule file writes it</param>
    /// <param name="scope">where it stands</param>
    /// <param name="where">what is being compiled, for the message of a failure</param>
    /// <returns>the compiled expression</returns>
    internal Compiled Compile(JsonElement json, Scope scope, string where)
    {
        if (json.ValueKind != JsonValueKind.Object)
        {
            throw new RulePackException(where + ": an expression is a JSON object");
        }

        List<JsonProperty> members = json.EnumerateObject().ToList();
        if (members.Count != 1)
        {
            throw new RulePackException(where + ": an expression is an object with exactly one"
                + " operator, and this one names " + members.Count.ToString(CultureInfo.InvariantCulture));
        }

        string operatorName = members[0].Name;
        JsonElement argument = members[0].Value;
        string at = where + ", " + operatorName;
        return operatorName switch
        {
            "value" => Value(argument, scope, at),
            "const" => Literal(argument, at),
            "exists" => Presence(argument, scope, at, true),
            "absent" => Presence(argument, scope, at, false),
            "eq" or "ne" or "lt" or "le" or "gt" or "ge" => Comparison(operatorName, argument, scope, at),
            "add" or "sub" or "mul" or "div" => Arithmetic(operatorName, argument, scope, at),
            "sum" or "min" or "max" => Aggregate(operatorName, argument, scope, at),
            "count" => Count(argument, scope, at),
            "round" => Round(argument, scope, at),
            "decimals" => DecimalsOf(argument, scope, at),
            "abs" => Abs(argument, scope, at),
            "inList" => InList(argument, scope, at),
            "matches" => Matches(argument, scope, at),
            "len" => Len(argument, scope, at),
            "and" or "or" => Junction(operatorName, argument, scope, at),
            "not" => Negation(argument, scope, at),
            "if" => Conditional(argument, scope, at),
            "forEach" or "all" or "any" => Quantifier(operatorName, argument, scope, at),
            _ => throw new RulePackException(
                where + ": " + operatorName + " is not an operator of this language"),
        };
    }

    /// <summary>Compiles a path and checks that it addresses one value of a business term.</summary>
    /// <param name="written">the path</param>
    /// <param name="scope">where it stands</param>
    /// <param name="where">what is being compiled, for the message of a failure</param>
    /// <returns>the pattern</returns>
    internal PathPattern SingleValuePath(string written, Scope scope, string where) =>
        RequireSingleValue(PathPattern.Compile(written, scope.Terms, Registry, where), written, where);

    /// <summary>Checks that a pattern already compiled addresses one value of a business term.</summary>
    /// <param name="pattern">the compiled pattern</param>
    /// <param name="written">the path as it was written</param>
    /// <param name="where">what is being compiled, for the message of a failure</param>
    /// <returns>the same pattern</returns>
    internal static PathPattern RequireSingleValue(PathPattern pattern, string written, string where)
    {
        if (pattern.EndsAtGroup)
        {
            throw new RulePackException(where + ": " + written
                + " addresses a business group, and only a business term carries a value");
        }

        if (pattern.HasWildcard)
        {
            throw new RulePackException(where + ": " + written
                + " addresses more than one value; an aggregate or forEach takes those");
        }

        return pattern;
    }

    /// <summary>Returns the semantic data type of the term a pattern ends at.</summary>
    /// <param name="pattern">the pattern</param>
    /// <param name="where">what is being compiled, for the message of a failure</param>
    /// <returns>the semantic data type</returns>
    internal SemanticType DatatypeOf(PathPattern pattern, string where) =>
        Registry.Datatype(pattern.LastTerm)
        ?? throw new RulePackException(
            where + ": the registry gives " + pattern.LastTerm + " no semantic data type");

    /// <summary>
    /// Records that a pattern will be asked about from the document root. It is a saving and
    /// not a requirement: a pattern nobody foresaw is answered correctly all the same.
    /// </summary>
    /// <param name="pattern">the pattern</param>
    internal void Foresee(PathPattern pattern)
    {
        if (pattern.HasWildcard)
        {
            (pattern.EndsAtGroup ? _rootGroupKeys : _rootValueKeys).Add(pattern.Text);
        }
    }

    private Compiled Value(JsonElement argument, Scope scope, string where)
    {
        PathPattern pattern = SingleValuePath(AsString(argument, where), scope, where);
        SemanticType type = DatatypeOf(pattern, where);
        return new Compiled(
            (evaluation, basePath) => evaluation.Read(pattern, basePath, type), TypeOf(type), null);
    }

    private static Compiled Literal(JsonElement argument, string where)
    {
        switch (argument.ValueKind)
        {
            case JsonValueKind.String:
                string text = argument.GetString()!;
                RuleValue asText = RuleValue.Of(text);
                return new Compiled((evaluation, basePath) => asText, RuleType.Any, text);
            case JsonValueKind.Number:
                if (!argument.TryGetInt64(out long whole))
                {
                    throw new RulePackException(where + ": a literal number is a whole number");
                }

                RuleValue number = RuleValue.Of(whole);
                return new Compiled((evaluation, basePath) => number, RuleType.Decimal, null);
            case JsonValueKind.True:
            case JsonValueKind.False:
                RuleValue truth = RuleValue.Of(argument.ValueKind == JsonValueKind.True);
                return new Compiled((evaluation, basePath) => truth, RuleType.Boolean, null);
            default:
                throw new RulePackException(
                    where + ": a literal is a string, a whole number or a truth value");
        }
    }

    private Compiled Presence(JsonElement argument, Scope scope, string where, bool wanted)
    {
        PathPattern pattern = PathPattern.Compile(AsString(argument, where), scope.Terms, Registry, where);
        Note(pattern, scope);
        bool group = pattern.EndsAtGroup;
        return new Compiled(
            (evaluation, basePath) => RuleValue.Of((group
                ? evaluation.Instances(pattern, basePath).Count > 0
                : evaluation.Matches(pattern, basePath).Count > 0) == wanted),
            RuleType.Boolean,
            null);
    }

    private Compiled Comparison(string operatorName, JsonElement argument, Scope scope, string where)
    {
        List<Compiled> operands = Operands(argument, scope, where, 2, 2);
        RuleType type = Unify(operands[0].Type, operands[1].Type, where);
        if (type == RuleType.Boolean && operatorName != "eq" && operatorName != "ne")
        {
            throw new RulePackException(where + ": a truth value is compared with eq or ne only");
        }

        Expression left = Coerce(operands[0], type, where).Expression;
        Expression right = Coerce(operands[1], type, where).Expression;
        return new Compiled(
            (evaluation, basePath) =>
            {
                RuleValue first = left(evaluation, basePath);
                RuleValue second = right(evaluation, basePath);
                if (first.IsAbsent || second.IsAbsent)
                {
                    return RuleValue.Absent;
                }

                int order = Order(first, second);
                return RuleValue.Of(operatorName switch
                {
                    "eq" => order == 0,
                    "ne" => order != 0,
                    "lt" => order < 0,
                    "le" => order <= 0,
                    "gt" => order > 0,
                    _ => order >= 0,
                });
            },
            RuleType.Boolean,
            null);
    }

    private static int Order(RuleValue left, RuleValue right)
    {
        if (left.IsDecimal)
        {
            return left.Decimal.CompareTo(right.Decimal);
        }

        if (left.IsDate)
        {
            return left.Date.CompareTo(right.Date);
        }

        if (left.IsBoolean)
        {
            return left.Truth!.Value.CompareTo(right.Truth!.Value);
        }

        return Json.Texts.CompareByCodePoint(left.Text, right.Text);
    }

    private Compiled Arithmetic(string operatorName, JsonElement argument, Scope scope, string where)
    {
        bool binary = operatorName is "sub" or "div";
        List<Compiled> operands = Operands(argument, scope, where, 2, binary ? 2 : int.MaxValue);
        List<Expression> parts = operands
            .Select(operand => Coerce(operand, RuleType.Decimal, where).Expression)
            .ToList();
        return new Compiled(
            (evaluation, basePath) =>
            {
                BigDecimal? total = null;
                foreach (Expression part in parts)
                {
                    RuleValue next = part(evaluation, basePath);
                    if (next.IsAbsent)
                    {
                        return RuleValue.Absent;
                    }

                    if (total is null)
                    {
                        total = next.Decimal;
                        continue;
                    }

                    switch (operatorName)
                    {
                        case "add":
                            total = total.Value.Add(next.Decimal);
                            break;
                        case "sub":
                            total = total.Value.Subtract(next.Decimal);
                            break;
                        case "mul":
                            total = total.Value.Multiply(next.Decimal);
                            break;
                        default:
                            if (!total.Value.TryDivide(next.Decimal, DivisionScale, out BigDecimal quotient))
                            {
                                return RuleValue.Absent;
                            }

                            total = quotient;
                            break;
                    }
                }

                return RuleValue.Of(total!.Value);
            },
            RuleType.Decimal,
            null);
    }

    private Compiled Aggregate(string operatorName, JsonElement argument, Scope scope, string where)
    {
        if (argument.ValueKind == JsonValueKind.String)
        {
            return OverPattern(operatorName, argument.GetString()!, scope, where);
        }

        List<Expression> parts = Operands(argument, scope, where, 1, int.MaxValue)
            .Select(operand => Coerce(operand, RuleType.Decimal, where).Expression)
            .ToList();
        return new Compiled(
            (evaluation, basePath) =>
            {
                List<BigDecimal> numbers = new(parts.Count);
                foreach (Expression part in parts)
                {
                    RuleValue next = part(evaluation, basePath);
                    if (next.IsAbsent)
                    {
                        return RuleValue.Absent;
                    }

                    numbers.Add(next.Decimal);
                }

                return Fold(operatorName, numbers);
            },
            RuleType.Decimal,
            null);
    }

    private Compiled OverPattern(string operatorName, string written, Scope scope, string where)
    {
        PathPattern pattern = PathPattern.Compile(written, scope.Terms, Registry, where);
        if (pattern.EndsAtGroup)
        {
            throw new RulePackException(where + ": " + written
                + " addresses business group instances, and " + operatorName + " takes values");
        }

        SemanticType type = DatatypeOf(pattern, where);
        if (!type.IsDecimal())
        {
            throw new RulePackException(where + ": " + pattern.LastTerm + " is "
                + type.RegistryDatatype() + ", and " + operatorName + " takes a numeric term");
        }

        Note(pattern, scope);
        bool remember = scope.RootBased;
        return new Compiled(
            (evaluation, basePath) =>
            {
                string key = operatorName + " " + pattern.AbsoluteText(basePath);
                evaluation.Record(pattern.AbsoluteText(basePath));
                return evaluation.Aggregate(key, remember, () =>
                {
                    List<BigDecimal> numbers = new();
                    foreach (KeyValuePair<SemanticPath, SemanticValue> match in evaluation.Matches(pattern, basePath))
                    {
                        numbers.Add(Evaluation.Convert(match.Value, type, match.Key).Decimal);
                    }

                    return Fold(operatorName, numbers);
                });
            },
            RuleType.Decimal,
            null);
    }

    private static RuleValue Fold(string operatorName, List<BigDecimal> numbers)
    {
        if (numbers.Count == 0)
        {
            return operatorName == "sum" ? RuleValue.Of(BigDecimal.Zero) : RuleValue.Absent;
        }

        BigDecimal result = numbers[0];
        for (int at = 1; at < numbers.Count; at++)
        {
            BigDecimal next = numbers[at];
            result = operatorName switch
            {
                "sum" => result.Add(next),
                "min" => result.CompareTo(next) <= 0 ? result : next,
                _ => result.CompareTo(next) >= 0 ? result : next,
            };
        }

        return RuleValue.Of(result);
    }

    private Compiled Count(JsonElement argument, Scope scope, string where)
    {
        PathPattern pattern = PathPattern.Compile(AsString(argument, where), scope.Terms, Registry, where);
        Note(pattern, scope);
        bool group = pattern.EndsAtGroup;
        bool remember = scope.RootBased;
        return new Compiled(
            (evaluation, basePath) =>
            {
                string key = "count " + pattern.AbsoluteText(basePath);
                evaluation.Record(pattern.AbsoluteText(basePath));
                return evaluation.Aggregate(key, remember, () => RuleValue.Of(group
                    ? evaluation.Instances(pattern, basePath).Count
                    : evaluation.Matches(pattern, basePath).Count));
            },
            RuleType.Decimal,
            null);
    }

    private Compiled Round(JsonElement argument, Scope scope, string where)
    {
        List<JsonElement> parts = AsArray(argument, where);
        if (parts.Count != 2)
        {
            throw new RulePackException(where + ": round takes an expression and a scale");
        }

        Expression value = Coerce(Compile(parts[0], scope, where), RuleType.Decimal, where).Expression;
        int scale = AsInt(parts[1], where + ": the scale of round", 0, MaxScale);
        return new Compiled(
            (evaluation, basePath) =>
            {
                RuleValue number = value(evaluation, basePath);
                return number.IsAbsent ? RuleValue.Absent : RuleValue.Of(number.Decimal.SetScale(scale));
            },
            RuleType.Decimal,
            null);
    }

    private Compiled DecimalsOf(JsonElement argument, Scope scope, string where)
    {
        List<JsonElement> parts = AsArray(argument, where);
        if (parts.Count != 2)
        {
            throw new RulePackException(where + ": decimals takes a path and a maximum scale");
        }

        PathPattern pattern = SingleValuePath(AsString(parts[0], where), scope, where);
        SemanticType type = DatatypeOf(pattern, where);
        if (type != SemanticType.Amount)
        {
            throw new RulePackException(where + ": " + pattern.LastTerm + " is "
                + type.RegistryDatatype() + ", and decimals applies to Amount alone. Unit Price"
                + " Amount, Quantity and Percentage have no fixed scale in EN 16931-1, 6.5, and a"
                + " rule that capped one of them would refuse a net price this standard expects"
                + " to carry many fraction digits");
        }

        int maxScale = AsInt(parts[1], where + ": the maximum scale of decimals", 0, MaxScale);
        return new Compiled(
            (evaluation, basePath) =>
            {
                RuleValue number = evaluation.Read(pattern, basePath, type);
                return number.IsAbsent
                    ? RuleValue.Absent
                    : RuleValue.Of(Math.Max(0, number.Decimal.StripTrailingZeros().Scale) <= maxScale);
            },
            RuleType.Boolean,
            null);
    }

    private Compiled Abs(JsonElement argument, Scope scope, string where)
    {
        Expression value = Coerce(Compile(argument, scope, where), RuleType.Decimal, where).Expression;
        return new Compiled(
            (evaluation, basePath) =>
            {
                RuleValue number = value(evaluation, basePath);
                return number.IsAbsent ? RuleValue.Absent : RuleValue.Of(number.Decimal.Abs());
            },
            RuleType.Decimal,
            null);
    }

    private Compiled InList(JsonElement argument, Scope scope, string where)
    {
        List<JsonElement> parts = AsArray(argument, where);
        if (parts.Count != 2)
        {
            throw new RulePackException(where + ": inList takes an expression and a list identifier");
        }

        Expression value = Coerce(Compile(parts[0], scope, where), RuleType.Text, where).Expression;
        CodeList list = _codeLists.Require(AsString(parts[1], where + ": the list identifier"));
        return new Compiled(
            (evaluation, basePath) =>
            {
                RuleValue code = value(evaluation, basePath);
                return code.IsAbsent ? RuleValue.Absent : RuleValue.Of(list.Contains(code.Text));
            },
            RuleType.Boolean,
            null);
    }

    private Compiled Matches(JsonElement argument, Scope scope, string where)
    {
        List<JsonElement> parts = AsArray(argument, where);
        if (parts.Count != 2)
        {
            throw new RulePackException(where + ": matches takes an expression and a pattern");
        }

        Expression value = Coerce(Compile(parts[0], scope, where), RuleType.Text, where).Expression;
        string written = AsString(parts[1], where + ": the pattern");
        Regex regex;
        try
        {
            regex = new Regex(@"\A(?:" + written + @")\z", RegexOptions.CultureInvariant);
        }
        catch (ArgumentException broken)
        {
            throw new RulePackException(
                where + ": " + written + " is not a pattern: " + broken.Message, broken);
        }

        return new Compiled(
            (evaluation, basePath) =>
            {
                RuleValue text = value(evaluation, basePath);
                return text.IsAbsent ? RuleValue.Absent : RuleValue.Of(regex.IsMatch(text.Text));
            },
            RuleType.Boolean,
            null);
    }

    private Compiled Len(JsonElement argument, Scope scope, string where)
    {
        Expression value = Coerce(Compile(argument, scope, where), RuleType.Text, where).Expression;
        return new Compiled(
            (evaluation, basePath) =>
            {
                RuleValue text = value(evaluation, basePath);
                return text.IsAbsent ? RuleValue.Absent : RuleValue.Of(CodePoints(text.Text));
            },
            RuleType.Decimal,
            null);
    }

    private static long CodePoints(string text)
    {
        long count = 0;
        for (int at = 0; at < text.Length; at++)
        {
            if (char.IsHighSurrogate(text[at]) && at + 1 < text.Length && char.IsLowSurrogate(text[at + 1]))
            {
                at++;
            }

            count++;
        }

        return count;
    }

    private Compiled Junction(string operatorName, JsonElement argument, Scope scope, string where)
    {
        List<Expression> parts = Operands(argument, scope, where, 2, int.MaxValue)
            .Select(operand => Coerce(operand, RuleType.Boolean, where).Expression)
            .ToList();
        bool conjunction = operatorName == "and";
        return new Compiled(
            (evaluation, basePath) =>
            {
                bool undecided = false;
                foreach (Expression part in parts)
                {
                    RuleValue next = part(evaluation, basePath);
                    if (next.IsAbsent)
                    {
                        undecided = true;
                        continue;
                    }

                    if (next.Truth!.Value != conjunction)
                    {
                        return RuleValue.Of(!conjunction);
                    }
                }

                return undecided ? RuleValue.Absent : RuleValue.Of(conjunction);
            },
            RuleType.Boolean,
            null);
    }

    private Compiled Negation(JsonElement argument, Scope scope, string where)
    {
        Expression value = Coerce(Compile(argument, scope, where), RuleType.Boolean, where).Expression;
        return new Compiled(
            (evaluation, basePath) =>
            {
                RuleValue truth = value(evaluation, basePath);
                return truth.IsAbsent ? RuleValue.Absent : RuleValue.Of(!truth.Truth!.Value);
            },
            RuleType.Boolean,
            null);
    }

    private Compiled Conditional(JsonElement argument, Scope scope, string where)
    {
        Dictionary<string, JsonElement> members = AsObject(argument, where);
        Reject(members.Keys, new[] { "condition", "then", "else" }, where, "if");
        if (!members.TryGetValue("condition", out JsonElement condition)
            || !members.TryGetValue("then", out JsonElement then))
        {
            throw new RulePackException(where + ": if takes a condition and a then");
        }

        Expression test = Coerce(Compile(condition, scope, where), RuleType.Boolean, where).Expression;
        Compiled consequent = Compile(then, scope, where);
        Compiled alternative = members.TryGetValue("else", out JsonElement otherwise)
            ? Compile(otherwise, scope, where)
            : new Compiled((evaluation, basePath) => RuleValue.True, RuleType.Boolean, null);
        RuleType type = Unify(consequent.Type, alternative.Type, where);
        Expression yes = Coerce(consequent, type, where).Expression;
        Expression no = Coerce(alternative, type, where).Expression;
        return new Compiled(
            (evaluation, basePath) =>
            {
                RuleValue truth = test(evaluation, basePath);
                if (truth.IsAbsent)
                {
                    return RuleValue.Absent;
                }

                return truth.Truth!.Value ? yes(evaluation, basePath) : no(evaluation, basePath);
            },
            type,
            null);
    }

    private Compiled Quantifier(string operatorName, JsonElement argument, Scope scope, string where)
    {
        Dictionary<string, JsonElement> members = AsObject(argument, where);
        Reject(members.Keys, new[] { "group", "assert" }, where, operatorName);
        if (!members.TryGetValue("group", out JsonElement group)
            || !members.TryGetValue("assert", out JsonElement assertion))
        {
            throw new RulePackException(where + ": " + operatorName + " takes a group and an assert");
        }

        PathPattern pattern = PathPattern.Compile(AsString(group, where), scope.Terms, Registry, where);
        if (!pattern.EndsAtGroup)
        {
            throw new RulePackException(where + ": " + operatorName
                + " walks business group instances, and " + pattern.LastTerm + " is a business term");
        }

        Note(pattern, scope);
        List<string> inner = new(scope.Terms);
        inner.AddRange(pattern.Terms);
        Expression body = Coerce(
            Compile(assertion, new Scope(inner, false), where), RuleType.Boolean, where).Expression;
        bool existential = operatorName == "any";
        bool report = operatorName == "forEach";
        return new Compiled(
            (evaluation, basePath) =>
            {
                bool undecided = false;
                foreach (SemanticPath instance in evaluation.Instances(pattern, basePath))
                {
                    RuleValue truth = body(evaluation, instance);
                    if (truth.IsAbsent)
                    {
                        undecided = true;
                        continue;
                    }

                    if (truth.Truth!.Value == existential)
                    {
                        if (report)
                        {
                            evaluation.Record(instance.Text);
                        }

                        return RuleValue.Of(existential);
                    }
                }

                return undecided ? RuleValue.Absent : RuleValue.Of(!existential);
            },
            RuleType.Boolean,
            null);
    }

    private List<Compiled> Operands(JsonElement argument, Scope scope, string where, int min, int max)
    {
        List<JsonElement> parts = AsArray(argument, where);
        if (parts.Count < min || parts.Count > max)
        {
            throw new RulePackException(where + ": "
                + parts.Count.ToString(CultureInfo.InvariantCulture) + " operands, where "
                + (min == max ? "exactly " + min : "at least " + min) + " belong");
        }

        return parts.Select(part => Compile(part, scope, where)).ToList();
    }

    private void Note(PathPattern pattern, Scope scope)
    {
        if (scope.RootBased)
        {
            Foresee(pattern);
        }
    }

    private static RuleType TypeOf(SemanticType type)
    {
        if (type.IsDecimal())
        {
            return RuleType.Decimal;
        }

        return type == SemanticType.Date ? RuleType.Date : RuleType.Text;
    }

    private static RuleType Unify(RuleType left, RuleType right, string where)
    {
        if (left == right)
        {
            return left == RuleType.Any ? RuleType.Text : left;
        }

        if (left == RuleType.Any)
        {
            return right;
        }

        if (right == RuleType.Any)
        {
            return left;
        }

        throw new RulePackException(where + ": " + Token(left) + " and " + Token(right)
            + " are not compared or combined in this language");
    }

    private static Compiled Coerce(Compiled compiled, RuleType target, string where)
    {
        if (compiled.Type == target)
        {
            return compiled;
        }

        if (compiled.Type != RuleType.Any || compiled.Literal is null)
        {
            throw new RulePackException(where + ": " + Token(compiled.Type)
                + " stands where " + Token(target) + " belongs");
        }

        string text = compiled.Literal;
        RuleValue value = target switch
        {
            RuleType.Decimal => RuleValue.Of(DecimalLiteral(text, where)),
            RuleType.Date => RuleValue.Of(DateLiteral(text, where)),
            RuleType.Text => RuleValue.Of(text),
            _ => throw new RulePackException(
                where + ": the literal " + text + " stands where " + Token(target) + " belongs"),
        };
        return new Compiled((evaluation, basePath) => value, target, null);
    }

    private static string Token(RuleType type) => type switch
    {
        RuleType.Decimal => "a decimal",
        RuleType.Date => "a date",
        RuleType.Text => "text",
        RuleType.Boolean => "a truth value",
        _ => "a literal",
    };

    private static BigDecimal DecimalLiteral(string text, string where) =>
        BigDecimal.TryParse(text, out BigDecimal value)
            ? value
            : throw new RulePackException(
                where + ": the literal " + text + " stands beside a decimal and is not one");

    private static DateOnly DateLiteral(string text, string where) =>
        DateOnly.TryParseExact(text, "yyyy-MM-dd", CultureInfo.InvariantCulture,
            DateTimeStyles.None, out DateOnly value)
            ? value
            : throw new RulePackException(
                where + ": the literal " + text + " stands beside a date and is not one");

    private static void Reject(IEnumerable<string> present, IEnumerable<string> known, string where, string operatorName)
    {
        HashSet<string> defined = new(known, StringComparer.Ordinal);
        foreach (string member in present)
        {
            if (!defined.Contains(member))
            {
                throw new RulePackException(where + ": " + operatorName + " has no member " + member);
            }
        }
    }

    internal static string AsString(JsonElement json, string where) =>
        json.ValueKind == JsonValueKind.String
            ? json.GetString()!
            : throw new RulePackException(where + ": a string belongs here");

    internal static List<JsonElement> AsArray(JsonElement json, string where) =>
        json.ValueKind == JsonValueKind.Array
            ? json.EnumerateArray().ToList()
            : throw new RulePackException(where + ": an array belongs here");

    internal static Dictionary<string, JsonElement> AsObject(JsonElement json, string where) =>
        json.ValueKind == JsonValueKind.Object
            ? json.EnumerateObject().ToDictionary(member => member.Name, member => member.Value, StringComparer.Ordinal)
            : throw new RulePackException(where + ": an object belongs here");

    internal static int AsInt(JsonElement json, string where, int min, int max)
    {
        if (json.ValueKind != JsonValueKind.Number || !json.TryGetInt32(out int value))
        {
            throw new RulePackException(where + ": a whole number belongs here");
        }

        return value >= min && value <= max
            ? value
            : throw new RulePackException(where + ": " + value.ToString(CultureInfo.InvariantCulture)
                + " lies outside " + min + " to " + max);
    }
}
