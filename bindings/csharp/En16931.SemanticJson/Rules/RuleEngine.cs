using System;
using System.Collections.Generic;
using System.Linq;
using En16931.SemanticJson.Model;

namespace En16931.SemanticJson.Rules;

/// <summary>
/// A rule pack compiled against a registry, ready to be run over documents.
/// </summary>
/// <remarks>
/// <para>Compiling is where a pack either becomes usable or is refused: the rules are parsed,
/// the paths are resolved against the registry, the operators and their operands are
/// type-checked, the code list snapshots the manifest names are fetched, and the rules written
/// in code that the manifest names are matched against the instances the caller handed over. A
/// pack that names a term nobody has, an operator nobody wrote or a snapshot nobody shipped
/// does not start. None of that is ever reported as a defect of an invoice.</para>
/// <para>The findings of this engine are findings about the business rules of a standard. They
/// are a layer of their own and are never presented as conformance to the ESJ format, which is
/// defined by layers L1 to L3 of the specification alone (<c>SPEC.md</c> section 9.4).</para>
/// </remarks>
public sealed class RuleEngine
{
    private readonly List<CompiledRule> _rules;

    private RuleEngine(RulePack pack, Registry registry, CodeLists codeLists, List<CompiledRule> rules)
    {
        Pack = pack;
        Registry = registry;
        CodeLists = codeLists;
        _rules = rules;
    }

    /// <summary>Returns the pack this engine was compiled from.</summary>
    public RulePack Pack { get; }

    /// <summary>Returns the registry the paths of the pack were resolved against.</summary>
    public Registry Registry { get; }

    /// <summary>Returns the code list snapshots this engine decides membership against.</summary>
    public CodeLists CodeLists { get; }

    /// <summary>Compiles a pack that carries no rules written in code.</summary>
    /// <param name="pack">the pack</param>
    /// <param name="registry">the registry of the edition the documents will name</param>
    /// <returns>the compiled engine</returns>
    public static RuleEngine Compile(RulePack pack, Registry registry)
    {
        ArgumentNullException.ThrowIfNull(pack);
        return Compile(pack, registry, CodeLists.Bundled(pack), NativeRules.None());
    }

    /// <summary>Compiles a pack.</summary>
    /// <param name="pack">the pack</param>
    /// <param name="registry">the registry of the edition the documents will name</param>
    /// <param name="codeLists">the snapshots the manifest names</param>
    /// <param name="nativeRules">the rules the manifest names, and no others</param>
    /// <returns>the compiled engine</returns>
    /// <exception cref="RulePackException">if the pack cannot be compiled</exception>
    public static RuleEngine Compile(
        RulePack pack, Registry registry, CodeLists codeLists, NativeRules nativeRules)
    {
        ArgumentNullException.ThrowIfNull(pack);
        ArgumentNullException.ThrowIfNull(registry);
        ArgumentNullException.ThrowIfNull(codeLists);
        ArgumentNullException.ThrowIfNull(nativeRules);
        CheckSnapshots(pack, codeLists);
        CheckNativeRules(pack, nativeRules);

        HashSet<string> rootValueKeys = new(StringComparer.Ordinal);
        HashSet<string> rootGroupKeys = new(StringComparer.Ordinal);
        Compiler compiler = new(registry, codeLists, rootValueKeys, rootGroupKeys);
        Dictionary<string, CompiledRule> byId = new(StringComparer.Ordinal);
        foreach (RuleDefinition definition in pack.Rules)
        {
            Add(byId, Language(definition, compiler, registry), pack);
        }

        foreach (string name in pack.NativeRules)
        {
            Add(byId, Native(nativeRules.Require(SimpleName(name)), compiler, registry, name), pack);
        }

        List<CompiledRule> ordered = byId.Values
            .OrderBy(rule => rule.Id, StringComparer.Ordinal)
            .ToList();
        return new RuleEngine(pack, registry, codeLists, ordered);
    }

    /// <summary>Returns the identifiers of the rules of this engine, sorted.</summary>
    /// <returns>the identifiers</returns>
    public IReadOnlyList<string> RuleIds() => _rules.Select(rule => rule.Id).ToList();

    /// <summary>Returns the terms a rule of this engine declares that it reads.</summary>
    /// <param name="ruleId">the identifier of the rule</param>
    /// <returns>the identifiers, or <c>null</c> where this engine has no such rule</returns>
    public IReadOnlyList<string>? TermsOf(string ruleId) =>
        _rules.FirstOrDefault(rule => rule.Id == ruleId)?.Terms;

    /// <summary>Returns the clause a rule of this engine states.</summary>
    /// <param name="ruleId">the identifier of the rule</param>
    /// <returns>the reference, or <c>null</c> where this engine has no such rule</returns>
    public string? SourceOf(string ruleId) =>
        _rules.FirstOrDefault(rule => rule.Id == ruleId)?.Source;

    /// <summary>
    /// Runs every rule of the pack over a document.
    /// </summary>
    /// <remarks>
    /// One signal of a rule is caught here and turned into a finding: the one an accessor
    /// raises when a value does not spell what its semantic data type requires. That is
    /// reported at <c>info</c> as a rule that was not decided, because the defect belongs to
    /// the value and a structural layer has already named it. Nothing else is caught: a rule
    /// that cannot run is a defect of the pack and never a statement about the invoice.
    /// <para>The document has to name the edition this engine was compiled for. A pack
    /// states rules about the business terms of one edition and a path is an address
    /// relative to an edition (specification, section 10), so a pack run over a document of
    /// another one would answer with rule identifiers that say nothing true about it. The
    /// engine refuses instead, and a caller with no pack for the edition it holds reports
    /// that nothing was checked rather than a verdict.</para>
    /// </remarks>
    /// <param name="document">the document; it is not changed</param>
    /// <returns>the findings, in a deterministic order</returns>
    /// <exception cref="ArgumentException">if the document names an edition other than the
    /// one this engine was compiled for</exception>
    public IReadOnlyList<RuleFinding> Evaluate(SemanticDocument document)
    {
        ArgumentNullException.ThrowIfNull(document);
        if (!Registry.Describes(document.SemanticModel))
        {
            throw new ArgumentException("this pack states rules about "
                + Registry.SemanticModel + " and the document names " + document.SemanticModel,
                nameof(document));
        }

        Evaluation evaluation = new(document, CodeLists);
        List<RuleFinding> findings = new();
        foreach (CompiledRule rule in _rules)
        {
            foreach (SemanticPath basePath in Bases(rule, evaluation))
            {
                evaluation.Begin();
                try
                {
                    Outcome? outcome = rule.Check(evaluation, basePath);
                    if (outcome is not null)
                    {
                        findings.Add(Finding(rule, outcome.Severity, outcome.Message, evaluation.Reads));
                    }
                }
                catch (UndecidedException undecided)
                {
                    findings.Add(Finding(
                        rule, RuleSeverity.Info, "not decided: " + undecided.Message, evaluation.Reads));
                }
            }
        }

        findings.Sort(RuleFinding.Order);
        return findings;
    }

    /// <inheritdoc />
    public override string ToString() =>
        "rule engine for " + Pack.Name + " with " + _rules.Count + " rules";

    private static IReadOnlyList<SemanticPath> Bases(CompiledRule rule, Evaluation evaluation) =>
        rule.Context is null
            ? new[] { SemanticPath.Root() }
            : evaluation.Instances(rule.Context, SemanticPath.Root());

    private RuleFinding Finding(
        CompiledRule rule, RuleSeverity severity, string message, IReadOnlyList<string> paths) =>
        new(rule.Id, RuleFinding.CategoryOf(rule.Id), severity, message, paths,
            Pack.Id, Pack.Version, RuleFinding.NativeEngine);

    private static void CheckSnapshots(RulePack pack, CodeLists codeLists)
    {
        foreach (string listId in pack.CodeLists.Keys)
        {
            codeLists.Require(listId);
        }
    }

    /// <summary>
    /// Returns the last segment of a name the manifest writes, which is what a binding
    /// declares its own implementation of that rule under.
    /// </summary>
    private static string SimpleName(string declared)
    {
        int dot = declared.LastIndexOf('.');
        return dot < 0 ? declared : declared.Substring(dot + 1);
    }

    private static void CheckNativeRules(RulePack pack, NativeRules nativeRules)
    {
        SortedSet<string> named = new(pack.NativeRules.Select(SimpleName), StringComparer.Ordinal);
        SortedSet<string> handed = new(nativeRules.Names, StringComparer.Ordinal);
        if (named.SetEquals(handed))
        {
            return;
        }

        IEnumerable<string> missing = named.Except(handed, StringComparer.Ordinal);
        IEnumerable<string> extra = handed.Except(named, StringComparer.Ordinal);
        throw new RulePackException("the pack " + pack.Name + " names the rules ["
            + string.Join(", ", named) + "] and the engine was handed ["
            + string.Join(", ", handed) + "]; missing [" + string.Join(", ", missing)
            + "]; not declared [" + string.Join(", ", extra) + "]");
    }

    private static void Add(Dictionary<string, CompiledRule> byId, CompiledRule rule, RulePack pack)
    {
        if (!byId.TryAdd(rule.Id, rule))
        {
            throw new RulePackException("the pack " + pack.Name + " carries the rule " + rule.Id + " twice");
        }
    }

    private static CompiledRule Language(RuleDefinition definition, Compiler compiler, Registry registry)
    {
        string where = "the rule " + definition.Id;
        PathPattern? context = Context(definition.Context, compiler, registry, where);
        Scope scope = ScopeOf(context);
        Dictionary<string, Compiled> bindings = new(StringComparer.Ordinal);
        foreach (KeyValuePair<string, System.Text.Json.JsonElement> bound in definition.Bindings)
        {
            bindings[bound.Key] = compiler.Compile(bound.Value, scope, where + ", bind " + bound.Key);
        }

        Expression assertion = Assertion(definition.Assertion, compiler, scope, where);
        MessageTemplate message = MessageTemplate.Compile(
            definition.Message, compiler, scope, bindings, where + ", message");
        Expression? second = definition.Warning is null
            ? null
            : Assertion(definition.Warning.Assertion, compiler, scope, where + ", warn");
        MessageTemplate? secondMessage = definition.Warning is null
            ? null
            : MessageTemplate.Compile(
                definition.Warning.Message, compiler, scope, bindings, where + ", warn message");

        return new CompiledRule(
            definition.Id, definition.Severity, context, definition.Terms, definition.Source,
            (evaluation, basePath) =>
            {
                if (!Holds(assertion, evaluation, basePath))
                {
                    return new Outcome(definition.Severity, message.Expand(evaluation, basePath));
                }

                if (second is not null && !Holds(second, evaluation, basePath))
                {
                    return new Outcome(RuleSeverity.Warning, secondMessage!.Expand(evaluation, basePath));
                }

                return null;
            });
    }

    private static CompiledRule Native(
        INativeRule rule, Compiler compiler, Registry registry, string name)
    {
        string where = "the rule " + name;
        if (rule.Severity == RuleSeverity.Info)
        {
            throw new RulePackException(where + " declares the severity info, which is the engine's");
        }

        PathPattern? context = Context(rule.Context, compiler, registry, where);
        Scope scope = ScopeOf(context);
        Resolver resolver = new(compiler, scope, where);
        if (scope.RootBased)
        {
            foreach (string root in rule.Roots)
            {
                compiler.Foresee(resolver.Pattern(root));
            }
        }

        return new CompiledRule(rule.Id, rule.Severity, context, rule.Terms, rule.Source,
            (evaluation, basePath) =>
            {
                RuleContext asked = new(evaluation, basePath, resolver);
                string? failed = rule.Check(asked);
                if (failed is not null)
                {
                    return new Outcome(rule.Severity, failed);
                }

                string? noted = rule.Warn(asked);
                return noted is null ? null : new Outcome(RuleSeverity.Warning, noted);
            });
    }

    private static Expression Assertion(
        System.Text.Json.JsonElement written, Compiler compiler, Scope scope, string where)
    {
        Compiled compiled = compiler.Compile(written, scope, where);
        if (compiled.Type != RuleType.Boolean)
        {
            throw new RulePackException(where + ": an assertion is a truth value");
        }

        return compiled.Expression;
    }

    /// <summary>Tells whether an assertion holds, which it does where it cannot be decided.</summary>
    private static bool Holds(Expression expression, Evaluation evaluation, SemanticPath basePath)
    {
        RuleValue truth = expression(evaluation, basePath);
        return truth.IsAbsent || truth.Truth == true;
    }

    private static Scope ScopeOf(PathPattern? context) => context is null
        ? new Scope(Array.Empty<string>(), true)
        : new Scope(context.Terms, false);

    private static PathPattern? Context(string written, Compiler compiler, Registry registry, string where)
    {
        if (written == "/")
        {
            return null;
        }

        PathPattern pattern = PathPattern.Compile(
            written, Array.Empty<string>(), registry, where + ", context");
        compiler.Foresee(pattern);
        if (!pattern.EndsAtGroup)
        {
            throw new RulePackException(where + ": the context " + written
                + " ends at a business term; a rule is a statement about the document or about a"
                + " business group instance");
        }

        return pattern;
    }
}

/// <summary>What a rule had to say about one business group instance.</summary>
/// <param name="Severity">how much it weighs</param>
/// <param name="Message">why</param>
internal sealed record Outcome(RuleSeverity Severity, string Message);

/// <summary>
/// One rule, ready to run: what it is called, how much it weighs, where it is evaluated, and
/// the one question it answers.
/// </summary>
/// <remarks>
/// Both kinds of rule end up here, and the engine cannot tell them apart afterwards. That is
/// what makes the promise of <see cref="INativeRule"/> true rather than merely intended.
/// </remarks>
internal sealed class CompiledRule
{
    private readonly Func<Evaluation, SemanticPath, Outcome?> _check;

    internal CompiledRule(
        string id,
        RuleSeverity severity,
        PathPattern? context,
        IReadOnlyList<string> terms,
        string source,
        Func<Evaluation, SemanticPath, Outcome?> check)
    {
        Id = id;
        Severity = severity;
        Context = context;
        Terms = terms;
        Source = source;
        _check = check;
    }

    internal string Id { get; }

    internal RuleSeverity Severity { get; }

    /// <summary>The context pattern, or <c>null</c> where the rule is about the document.</summary>
    internal PathPattern? Context { get; }

    internal IReadOnlyList<string> Terms { get; }

    internal string Source { get; }

    internal Outcome? Check(Evaluation evaluation, SemanticPath basePath) => _check(evaluation, basePath);
}
