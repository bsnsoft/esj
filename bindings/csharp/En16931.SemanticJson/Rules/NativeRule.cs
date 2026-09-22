using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using En16931.SemanticJson.Model;

namespace En16931.SemanticJson.Rules;

/// <summary>
/// A rule the closed operator set of the rule language cannot express, written in code under
/// the same rule identifier, in the same pack, producing the same finding.
/// </summary>
/// <remarks>
/// <para>Which side of that line a rule fell on is an implementation detail and not a fact
/// about the invoice: the severity, the category, the pack, the engine name, the read list and
/// the ordering of a finding are decided in one place, so a rule expressed in the rule
/// language and the same rule expressed in code produce the same finding by construction.</para>
/// <para>The manifest names these rules; it does not load them. A file that could name a class
/// the engine then instantiates would be a file that decides what code runs, and a pack may
/// arrive from a directory a caller was handed. The caller passes the instances to
/// <see cref="RuleEngine.Compile(RulePack, Model.Registry, CodeLists, NativeRules)"/>, which checks that exactly the named rules arrived: one
/// missing is a pack that would silently check less than it claims, and one too many is a rule
/// nobody declared. <see cref="DeclaredAs"/> is the name the manifest writes, which is how a
/// binding in another language carries its own implementation of a rule the pack declares.</para>
/// </remarks>
public interface INativeRule
{
    /// <summary>Returns the identifier the standard gives the rule.</summary>
    string Id { get; }

    /// <summary>
    /// Returns the name the pack manifest declares this rule under, without the namespace the
    /// manifest may write before it: <c>Br62</c> for a rule a manifest names
    /// <c>…rules.en16931.Br62</c>. A pack is data and names its rules in the words of whoever
    /// wrote it; a binding carries its own implementation of each of them and is matched to
    /// the pack by the last segment of that name.
    /// </summary>
    string DeclaredAs { get; }

    /// <summary>Returns how much a finding of this rule weighs.</summary>
    RuleSeverity Severity { get; }

    /// <summary>Returns what the rule is a statement about: the document, or a group.</summary>
    string Context { get; }

    /// <summary>Returns the business terms and groups the rule reads.</summary>
    IReadOnlyList<string> Terms { get; }

    /// <summary>Returns the clause of the standard the rule states.</summary>
    string Source { get; }

    /// <summary>
    /// Returns the patterns this rule evaluates from the document root, as a hint for the
    /// index of a document. It is a saving and not a duty: a pattern nobody declared is
    /// answered all the same.
    /// </summary>
    IReadOnlyList<string> Roots => Array.Empty<string>();

    /// <summary>Returns what is wrong, or <c>null</c> where the rule holds.</summary>
    /// <param name="context">the document, as the rule may ask it</param>
    /// <returns>the message of the finding, or <c>null</c></returns>
    string? Check(RuleContext context);

    /// <summary>
    /// Returns what is worth saying although the rule holds, or <c>null</c>. It is weighed
    /// only where <see cref="Check"/> found nothing, and its finding decides no verdict.
    /// </summary>
    /// <param name="context">the document, as the rule may ask it</param>
    /// <returns>the message of the warning, or <c>null</c></returns>
    string? Warn(RuleContext context) => null;
}

/// <summary>The instances of the rules a pack manifest names, and no others.</summary>
public sealed class NativeRules
{
    private readonly Dictionary<string, INativeRule> _byName;

    private NativeRules(Dictionary<string, INativeRule> byName)
    {
        _byName = byName;
    }

    /// <summary>Returns no rules at all.</summary>
    /// <returns>the empty set</returns>
    public static NativeRules None() => new(new Dictionary<string, INativeRule>(StringComparer.Ordinal));

    /// <summary>Returns a set of rules.</summary>
    /// <param name="rules">the instances</param>
    /// <returns>the set</returns>
    public static NativeRules Of(IEnumerable<INativeRule> rules)
    {
        ArgumentNullException.ThrowIfNull(rules);
        Dictionary<string, INativeRule> byName = new(StringComparer.Ordinal);
        foreach (INativeRule rule in rules)
        {
            if (!byName.TryAdd(rule.DeclaredAs, rule))
            {
                throw new RulePackException("two rules are declared as " + rule.DeclaredAs);
            }
        }

        return new NativeRules(byName);
    }

    /// <summary>Returns the names the rules of this set are declared under.</summary>
    public IReadOnlyCollection<string> Names => _byName.Keys.ToList();

    /// <summary>Returns the rule a pack declares under a name.</summary>
    /// <param name="name">the name the manifest writes</param>
    /// <returns>the rule</returns>
    /// <exception cref="RulePackException">if this set carries no such rule</exception>
    public INativeRule Require(string name)
    {
        ArgumentNullException.ThrowIfNull(name);
        return _byName.TryGetValue(name, out INativeRule? rule)
            ? rule
            : throw new RulePackException("the pack names the rule " + name
                + ", and the engine was handed no implementation of it");
    }
}

/// <summary>
/// The document as a rule written in code may ask it: values, sums, counts, instances and the
/// code list snapshots, all resolved against the registry exactly as a rule file's paths are.
/// </summary>
/// <remarks>
/// Every read is recorded in the order it was made, which is the list a finding reports. A
/// path this context is asked for that the registry does not admit is a defect of the pack and
/// raises rather than reporting anything about the invoice.
/// </remarks>
public sealed class RuleContext
{
    private readonly Evaluation _evaluation;
    private readonly Resolver _resolver;

    internal RuleContext(Evaluation evaluation, SemanticPath basePath, Resolver resolver)
    {
        _evaluation = evaluation;
        Base = basePath;
        _resolver = resolver;
    }

    /// <summary>Returns the business group instance the rule is looking at.</summary>
    public SemanticPath Base { get; }

    /// <summary>Returns the registry the paths are resolved against.</summary>
    public Registry Registry => _resolver.Registry;

    /// <summary>Returns the path a pattern addresses inside this instance.</summary>
    /// <param name="path">the path, as a rule writes it</param>
    /// <returns>the path in the document</returns>
    public SemanticPath Resolve(string path)
    {
        ArgumentNullException.ThrowIfNull(path);
        return _resolver.SingleValuePath(path).Concrete(Base);
    }

    /// <summary>Tells whether the document carries anything at a pattern.</summary>
    /// <param name="path">the path or pattern</param>
    /// <returns>whether anything is there</returns>
    public bool Exists(string path)
    {
        ArgumentNullException.ThrowIfNull(path);
        PathPattern pattern = _resolver.Pattern(path);
        return pattern.EndsAtGroup
            ? _evaluation.Instances(pattern, Base).Count > 0
            : _evaluation.Matches(pattern, Base).Count > 0;
    }

    /// <summary>Returns the value at a path, or <c>null</c>.</summary>
    /// <param name="path">the path</param>
    /// <returns>the value, or <c>null</c> where the document carries none</returns>
    public SemanticValue? Value(string path)
    {
        ArgumentNullException.ThrowIfNull(path);
        return _evaluation.Raw(_resolver.SingleValuePath(path), Base);
    }

    /// <summary>Returns the content at a path, or <c>null</c>.</summary>
    /// <param name="path">the path</param>
    /// <returns>the content, or <c>null</c></returns>
    public string? Text(string path) => Value(path)?.Content;

    /// <summary>Returns the number at a numeric path, or <c>null</c>.</summary>
    /// <param name="path">the path</param>
    /// <returns>the number, or <c>null</c></returns>
    public BigDecimal? Decimal(string path)
    {
        RuleValue value = Typed(path, type => type.IsDecimal(), "numeric");
        return value.IsAbsent ? null : value.Decimal;
    }

    /// <summary>Returns the date at a date path, or <c>null</c>.</summary>
    /// <param name="path">the path</param>
    /// <returns>the date, or <c>null</c></returns>
    public DateOnly? Date(string path)
    {
        RuleValue value = Typed(path, type => type == SemanticType.Date, "a date");
        return value.IsAbsent ? null : value.Date;
    }

    /// <summary>Returns the sum over every match of a pattern, zero over none.</summary>
    /// <param name="pattern">the pattern</param>
    /// <returns>the total</returns>
    public BigDecimal Sum(string pattern)
    {
        ArgumentNullException.ThrowIfNull(pattern);
        PathPattern compiled = _resolver.Pattern(pattern);
        SemanticType type = _resolver.DatatypeOf(compiled);
        if (!type.IsDecimal())
        {
            throw new RulePackException(compiled.LastTerm + " is " + type.RegistryDatatype()
                + ", and a sum is taken over a numeric term");
        }

        _evaluation.Record(compiled.AbsoluteText(Base));
        return _evaluation.Aggregate("sum " + compiled.AbsoluteText(Base), Base.IsRoot, () =>
        {
            BigDecimal total = BigDecimal.Zero;
            foreach (KeyValuePair<SemanticPath, SemanticValue> match in _evaluation.Matches(compiled, Base))
            {
                total = total.Add(Evaluation.Convert(match.Value, type, match.Key).Decimal);
            }

            return RuleValue.Of(total);
        }).Decimal;
    }

    /// <summary>Returns how many values or instances a pattern addresses.</summary>
    /// <param name="pattern">the pattern</param>
    /// <returns>the count</returns>
    public int Count(string pattern)
    {
        ArgumentNullException.ThrowIfNull(pattern);
        PathPattern compiled = _resolver.Pattern(pattern);
        _evaluation.Record(compiled.AbsoluteText(Base));
        return compiled.EndsAtGroup
            ? _evaluation.Instances(compiled, Base).Count
            : _evaluation.Matches(compiled, Base).Count;
    }

    /// <summary>Returns the business group instances a pattern addresses.</summary>
    /// <param name="pattern">the pattern, which ends at a business group</param>
    /// <returns>the instances, in canonical order</returns>
    public IReadOnlyList<SemanticPath> Instances(string pattern)
    {
        ArgumentNullException.ThrowIfNull(pattern);
        PathPattern compiled = _resolver.Pattern(pattern);
        if (!compiled.EndsAtGroup)
        {
            throw new RulePackException(compiled.LastTerm
                + " is a business term, and instances are of a business group");
        }

        return _evaluation.Instances(compiled, Base);
    }

    /// <summary>Returns the values a pattern addresses, with their paths.</summary>
    /// <param name="pattern">the pattern</param>
    /// <returns>the matches, in canonical order</returns>
    public IReadOnlyList<KeyValuePair<SemanticPath, SemanticValue>> Values(string pattern)
    {
        ArgumentNullException.ThrowIfNull(pattern);
        PathPattern compiled = _resolver.Pattern(pattern);
        if (compiled.EndsAtGroup)
        {
            throw new RulePackException(compiled.LastTerm
                + " is a business group, and only a business term carries a value");
        }

        return _evaluation.Matches(compiled, Base);
    }

    /// <summary>Returns the numbers a numeric pattern addresses, with their paths.</summary>
    /// <param name="pattern">the pattern</param>
    /// <returns>the matches, in canonical order</returns>
    public IReadOnlyList<KeyValuePair<SemanticPath, BigDecimal>> Decimals(string pattern) =>
        Converted(pattern, type => type.IsDecimal(), "numeric", value => value.Decimal);

    /// <summary>Returns the contents a pattern addresses, with their paths.</summary>
    /// <param name="pattern">the pattern</param>
    /// <returns>the matches, in canonical order</returns>
    public IReadOnlyList<KeyValuePair<SemanticPath, string>> Texts(string pattern) =>
        Converted(pattern, type => true, "any", value => value.Text);

    /// <summary>
    /// Returns a remembered answer about the document, or computes and remembers it. It is
    /// the aggregate memory of the rule language, opened to the rules the language cannot
    /// express, so that fifteen rules asking for one join of the document make one pass.
    /// </summary>
    /// <typeparam name="T">what the answer is</typeparam>
    /// <param name="key">what identifies the answer</param>
    /// <param name="compute">how to compute it</param>
    /// <returns>the answer</returns>
    public T Shared<T>(string key, Func<T> compute)
    {
        ArgumentNullException.ThrowIfNull(key);
        ArgumentNullException.ThrowIfNull(compute);
        return _evaluation.Shared(Base + " " + key, Base.IsRoot, compute);
    }

    /// <summary>Returns a code list snapshot the pack decides membership against.</summary>
    /// <param name="listId">the identifier of the list</param>
    /// <returns>the snapshot</returns>
    public CodeList CodeList(string listId)
    {
        ArgumentNullException.ThrowIfNull(listId);
        return _evaluation.CodeLists.Require(listId);
    }

    /// <summary>Returns a message with the placeholders of the rule language resolved.</summary>
    /// <param name="template">the message</param>
    /// <returns>the expanded message</returns>
    public string Format(string template)
    {
        ArgumentNullException.ThrowIfNull(template);
        return _resolver.Template(template).Expand(_evaluation, Base);
    }

    /// <summary>Returns a fragment of document content fit for a message.</summary>
    /// <param name="text">the content</param>
    /// <returns>the fragment, escaped and cut</returns>
    public string Escape(string text)
    {
        ArgumentNullException.ThrowIfNull(text);
        return Esj.ForMessage(text);
    }

    /// <inheritdoc />
    public override string ToString() => Base.IsRoot ? "/" : Base.Text;

    private IReadOnlyList<KeyValuePair<SemanticPath, T>> Converted<T>(
        string pattern, Func<SemanticType, bool> admits, string what, Func<RuleValue, T> read)
    {
        ArgumentNullException.ThrowIfNull(pattern);
        PathPattern compiled = _resolver.Pattern(pattern);
        if (compiled.EndsAtGroup)
        {
            throw new RulePackException(compiled.LastTerm
                + " is a business group, and only a business term carries a value");
        }

        SemanticType type = _resolver.DatatypeOf(compiled);
        if (!admits(type))
        {
            throw new RulePackException(compiled.LastTerm + " is " + type.RegistryDatatype()
                + ", and this accessor reads " + what);
        }

        List<KeyValuePair<SemanticPath, T>> values = new();
        foreach (KeyValuePair<SemanticPath, SemanticValue> match in _evaluation.Matches(compiled, Base))
        {
            values.Add(new KeyValuePair<SemanticPath, T>(
                match.Key, read(Evaluation.Convert(match.Value, type, match.Key))));
        }

        return values;
    }

    private RuleValue Typed(string path, Func<SemanticType, bool> admits, string what)
    {
        ArgumentNullException.ThrowIfNull(path);
        PathPattern pattern = _resolver.SingleValuePath(path);
        SemanticType type = _resolver.DatatypeOf(pattern);
        if (!admits(type))
        {
            throw new RulePackException(pattern.LastTerm + " is " + type.RegistryDatatype()
                + ", and this accessor reads " + what);
        }

        return _evaluation.Read(pattern, Base, type);
    }
}

/// <summary>
/// Compiles the paths and the messages a rule written in code writes, once each, and remembers
/// them for the life of the engine.
/// </summary>
internal sealed class Resolver
{
    private readonly Compiler _compiler;
    private readonly Scope _scope;
    private readonly string _where;
    private readonly ConcurrentDictionary<string, PathPattern> _patterns = new(StringComparer.Ordinal);
    private readonly ConcurrentDictionary<string, MessageTemplate> _templates = new(StringComparer.Ordinal);

    internal Resolver(Compiler compiler, Scope scope, string where)
    {
        _compiler = compiler;
        _scope = scope;
        _where = where;
    }

    internal Registry Registry => _compiler.Registry;

    internal PathPattern Pattern(string written) => _patterns.GetOrAdd(
        written, text => PathPattern.Compile(text, _scope.Terms, _compiler.Registry, _where));

    internal PathPattern SingleValuePath(string written) =>
        Compiler.RequireSingleValue(Pattern(written), written, _where);

    internal SemanticType DatatypeOf(PathPattern pattern) => _compiler.DatatypeOf(pattern, _where);

    internal MessageTemplate Template(string template) => _templates.GetOrAdd(
        template,
        text => MessageTemplate.Compile(
            text, _compiler, _scope, new Dictionary<string, Compiled>(StringComparer.Ordinal), _where));
}
