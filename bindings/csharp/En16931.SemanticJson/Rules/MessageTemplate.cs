using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace En16931.SemanticJson.Rules;

/// <summary>
/// What a finding says, with the four placeholders of <c>rules/README.md</c> resolved when
/// the pack is compiled rather than when a rule fails.
/// </summary>
/// <remarks>
/// A finding that says only that a rule failed makes the reader open the invoice and do the
/// arithmetic. <c>{/BG-22/BT-106}</c> shows the value at that path, <c>{@/BG-22/BT-106}</c>
/// the path itself, <c>{$name}</c> an expression the rule bound under that name and
/// <c>{.}</c> the business group instance the rule is looking at. A literal brace is written
/// twice, and a message that names a term nobody has is a defect of the pack, found once.
/// </remarks>
internal sealed class MessageTemplate
{
    private delegate string Part(Evaluation evaluation, SemanticPath basePath);

    private readonly List<Part> _parts;

    private MessageTemplate(List<Part> parts)
    {
        _parts = parts;
    }

    internal static MessageTemplate Compile(
        string template,
        Compiler compiler,
        Scope scope,
        IReadOnlyDictionary<string, Compiled> bindings,
        string where)
    {
        List<Part> parts = new();
        StringBuilder literal = new();
        int at = 0;
        while (at < template.Length)
        {
            char character = template[at];
            if (character == '}' && at + 1 < template.Length && template[at + 1] == '}')
            {
                literal.Append('}');
                at += 2;
                continue;
            }

            if (character != '{')
            {
                literal.Append(character);
                at++;
                continue;
            }

            if (at + 1 < template.Length && template[at + 1] == '{')
            {
                literal.Append('{');
                at += 2;
                continue;
            }

            int end = template.IndexOf('}', at + 1);
            if (end < 0)
            {
                throw new RulePackException(where + ": the message opens a placeholder at "
                    + at.ToString(CultureInfo.InvariantCulture) + " and never closes it");
            }

            if (literal.Length > 0)
            {
                string text = literal.ToString();
                parts.Add((evaluation, basePath) => text);
                literal.Clear();
            }

            parts.Add(Placeholder(template.Substring(at + 1, end - at - 1), compiler, scope, bindings, where));
            at = end + 1;
        }

        if (literal.Length > 0)
        {
            string text = literal.ToString();
            parts.Add((evaluation, basePath) => text);
        }

        return new MessageTemplate(parts);
    }

    private static Part Placeholder(
        string name,
        Compiler compiler,
        Scope scope,
        IReadOnlyDictionary<string, Compiled> bindings,
        string where)
    {
        if (name == ".")
        {
            return (evaluation, basePath) => basePath.IsRoot ? "/" : basePath.Text;
        }

        if (name.StartsWith("$", StringComparison.Ordinal))
        {
            if (!bindings.TryGetValue(name.Substring(1), out Compiled? bound))
            {
                throw new RulePackException(
                    where + ": the message names " + name + ", which the rule does not bind");
            }

            return (evaluation, basePath) => bound.Expression(evaluation, basePath).Display();
        }

        if (name.StartsWith("@", StringComparison.Ordinal))
        {
            PathPattern pattern = compiler.SingleValuePath(name.Substring(1), scope, where);
            return (evaluation, basePath) => pattern.AbsoluteText(basePath);
        }

        if (name.StartsWith("/", StringComparison.Ordinal))
        {
            PathPattern pattern = compiler.SingleValuePath(name, scope, where);
            SemanticType type = compiler.DatatypeOf(pattern, where);
            return (evaluation, basePath) => evaluation.Read(pattern, basePath, type).Display();
        }

        throw new RulePackException(where + ": {" + name + "} is not a placeholder; a placeholder"
            + " is a path, a path after @, a bound name after $, or a dot");
    }

    /// <summary>Returns the message a rule instance produces.</summary>
    /// <param name="evaluation">the run</param>
    /// <param name="basePath">the instance the rule is evaluated at</param>
    /// <returns>the message</returns>
    internal string Expand(Evaluation evaluation, SemanticPath basePath)
    {
        StringBuilder message = new();
        foreach (Part part in _parts)
        {
            message.Append(part(evaluation, basePath));
        }

        return message.ToString();
    }
}
