using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using En16931.SemanticJson.Model;

namespace En16931.SemanticJson.Rules;

/// <summary>
/// A path as a rule writes it: a chain of business term identifiers in which a repeatable one
/// is followed by <c>*</c> instead of by an occurrence index.
/// </summary>
/// <remarks>
/// A rule never writes an occurrence index. It cannot: a rule is a statement about every
/// invoice, and the number of lines an invoice has is not known when the rule is written. The
/// asterisk is therefore not a convenience but the only form a repeatable step has, and the
/// index rule of the specification, section 5.3 decides where one stands — checked against the
/// registry when the pack is compiled rather than once per invoice.
/// </remarks>
public sealed class PathPattern
{
    private readonly List<string> _terms;
    private readonly List<bool> _wildcards;

    private PathPattern(List<string> terms, List<bool> wildcards, string text, bool endsAtGroup)
    {
        _terms = terms;
        _wildcards = wildcards;
        Text = text;
        HasWildcard = wildcards.Contains(true);
        EndsAtGroup = endsAtGroup;
    }

    /// <summary>Returns the pattern as the rule wrote it.</summary>
    public string Text { get; }

    /// <summary>Tells whether the pattern can match more than one path inside one instance.</summary>
    public bool HasWildcard { get; }

    /// <summary>Tells whether the pattern names business group instances rather than values.</summary>
    public bool EndsAtGroup { get; }

    /// <summary>Returns the term identifiers of the pattern, outermost first.</summary>
    public IReadOnlyList<string> Terms => _terms;

    /// <summary>Returns the identifier of the term the pattern ends at.</summary>
    public string LastTerm => _terms[_terms.Count - 1];

    /// <summary>
    /// Compiles a pattern written in a rule, with the chain of the enclosing context before
    /// it.
    /// </summary>
    /// <param name="written">the pattern as the rule writes it, starting with a solidus</param>
    /// <param name="contextTerms">the term identifiers of the enclosing context</param>
    /// <param name="registry">the registry the terms are resolved against</param>
    /// <param name="what">what the pattern is, for the message of a failure</param>
    /// <returns>the compiled pattern</returns>
    /// <exception cref="RulePackException">
    /// if the pattern is not a path, names a term the registry does not know, nests in a way
    /// the registry does not record, or breaks the index rule
    /// </exception>
    public static PathPattern Compile(
        string written, IReadOnlyList<string> contextTerms, Registry registry, string what)
    {
        ArgumentNullException.ThrowIfNull(written);
        ArgumentNullException.ThrowIfNull(contextTerms);
        ArgumentNullException.ThrowIfNull(registry);
        if (written.Length == 0 || written[0] != '/')
        {
            throw new RulePackException(what + ": a path starts with a solidus, " + written + " does not");
        }

        List<string> steps = new();
        List<bool> stars = new();
        foreach (string token in written.Substring(1).Split('/'))
        {
            if (token == "*")
            {
                if (stars.Count == 0 || stars[stars.Count - 1])
                {
                    throw new RulePackException(
                        what + ": an asterisk follows a term, " + written + " does not");
                }

                stars[stars.Count - 1] = true;
                continue;
            }

            if (token.Length == 0)
            {
                throw new RulePackException(what + ": empty step in " + written);
            }

            steps.Add(token);
            stars.Add(false);
        }

        if (steps.Count == 0)
        {
            throw new RulePackException(what + ": " + written + " names no term");
        }

        Check(steps, stars, contextTerms, registry, what, written);
        Term last = registry.TermOf(steps[steps.Count - 1])!;
        return new PathPattern(steps, stars, written, last.IsGroup);
    }

    private static void Check(
        List<string> steps,
        List<bool> stars,
        IReadOnlyList<string> contextTerms,
        Registry registry,
        string what,
        string written)
    {
        List<string> chain = new(contextTerms);
        chain.AddRange(steps);
        for (int at = 0; at < steps.Count; at++)
        {
            string id = steps[at];
            Term term = registry.TermOf(id) ?? throw new RulePackException(
                what + ": " + id + " is not a term of " + registry.SemanticModel);
            if (at < steps.Count - 1 && !term.IsGroup)
            {
                throw new RulePackException(what + ": only the last step of " + written
                    + " may name a business term, " + id + " is one");
            }

            if (term.IsRepeatable != stars[at])
            {
                throw new RulePackException(what + ": " + id + (term.IsRepeatable
                    ? " is repeatable and is written " + id + "/* in a path"
                    : " occurs at most once and is written without an asterisk")
                    + ", " + written + " does the other");
            }
        }

        IReadOnlyList<IReadOnlyList<string>> recorded = registry.Chains(chain[chain.Count - 1]);
        if (!recorded.Any(known => known.SequenceEqual(chain, StringComparer.Ordinal)))
        {
            throw new RulePackException(what + ": " + string.Join("/", chain)
                + " is not a nesting the registry records");
        }
    }

    /// <summary>
    /// Returns how many path segments the pattern adds to the context: one per step, and one
    /// more for each step that carries an asterisk.
    /// </summary>
    public int SegmentCount => _terms.Count + _wildcards.Count(star => star);

    /// <summary>Returns the pattern as it addresses the document from an instance.</summary>
    /// <param name="basePath">the context instance, the root for a document rule</param>
    /// <returns>the absolute pattern text</returns>
    public string AbsoluteText(SemanticPath basePath)
    {
        ArgumentNullException.ThrowIfNull(basePath);
        return basePath.Text + Text;
    }

    /// <summary>Returns the one path this pattern addresses inside an instance.</summary>
    /// <param name="basePath">the context instance</param>
    /// <returns>the path</returns>
    /// <exception cref="InvalidOperationException">if the pattern carries an asterisk</exception>
    public SemanticPath Concrete(SemanticPath basePath)
    {
        if (HasWildcard)
        {
            throw new InvalidOperationException(Text + " addresses more than one path");
        }

        return SemanticPath.Parse(AbsoluteText(basePath));
    }

    /// <summary>
    /// Returns the longest prefix of the absolute pattern that carries no asterisk, which is
    /// the subtree every match of the pattern lies in.
    /// </summary>
    /// <param name="basePath">the context instance</param>
    /// <returns>the prefix path</returns>
    public SemanticPath ConcretePrefix(SemanticPath basePath)
    {
        StringBuilder prefix = new(basePath.Text);
        for (int at = 0; at < _terms.Count; at++)
        {
            if (_wildcards[at])
            {
                break;
            }

            prefix.Append('/').Append(_terms[at]);
        }

        string text = prefix.ToString();
        return text.Length == 0 ? SemanticPath.Root() : SemanticPath.Parse(text);
    }

    /// <summary>
    /// Returns the key a path has in the index of a document: the path with every occurrence
    /// index replaced by an asterisk.
    /// </summary>
    /// <param name="path">the path</param>
    /// <returns>the key</returns>
    public static string Key(SemanticPath path)
    {
        ArgumentNullException.ThrowIfNull(path);
        StringBuilder key = new(path.Text.Length);
        foreach (PathSegment segment in path.Segments)
        {
            key.Append('/').Append(segment.IsIndex ? "*" : segment.Text);
        }

        return key.ToString();
    }

    /// <inheritdoc />
    public override string ToString() => Text;
}
