package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.model.Registry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A rule pack compiled against a registry, ready to be run over documents.
 *
 * <p>Compiling is where a pack either becomes usable or is refused: the rules are parsed, the
 * paths are resolved against the registry, the operators and their operands are type-checked,
 * the code list snapshots the manifest names are fetched, and the Java rules the manifest
 * names are matched against the instances the caller handed over. A pack that names a term
 * nobody has, an operator nobody wrote or a snapshot nobody shipped does not start. None of
 * that is ever reported as a defect of an invoice.
 *
 * <p>Running is one pass. The rules of a pack fall into two kinds: those that are statements
 * about the document, evaluated once, and those that are statements about every instance of a
 * business group, evaluated once per instance. The instances come from one indexed pass over
 * the document and an aggregate over the whole document is computed once for the whole run,
 * so the cost of a run grows with the number of invoice lines and not with the square of it.
 * An invoice has a second count as well — a rule that compares a VAT breakdown with the parts
 * of the invoice in its category has the breakdowns and the lines to be linear in — and the
 * filtered sums those rules need are bucketed once per run rather than scanned per breakdown,
 * so the cost grows with the sum of the two counts and not with their product. An invoice of
 * three hundred thousand lines is a real input for this project, {@code rules/README.md}
 * carries the measurement, and the linearity test of this module measures both axes on every
 * build.
 *
 * <p>An engine holds no mutable state and may be used from several threads at once. Each call
 * of {@link #evaluate} makes its own working state, including its own index of the document
 * it was handed.
 *
 * <p>The findings of this engine are findings about the business rules of a standard. They
 * are a layer of their own and are never presented as conformance to the ESJ format, which is
 * defined by layers L1 to L3 of the specification alone ({@code SPEC.md} section 9.4).
 */
public final class RuleEngine {

    private final RulePack pack;
    private final Registry registry;
    private final CodeLists codeLists;
    private final List<CompiledRule> rules;
    private final Set<String> rootValueKeys;
    private final Set<String> rootGroupKeys;

    private RuleEngine(RulePack pack, Registry registry, CodeLists codeLists,
                       List<CompiledRule> rules, Set<String> rootValueKeys, Set<String> rootGroupKeys) {
        this.pack = pack;
        this.registry = registry;
        this.codeLists = codeLists;
        this.rules = rules;
        this.rootValueKeys = rootValueKeys;
        this.rootGroupKeys = rootGroupKeys;
    }

    /**
     * Compiles a pack that carries no Java rules, with the code list snapshots the manifest
     * names taken from this build.
     *
     * @param pack     the pack
     * @param registry the registry of the edition the documents will name
     * @return the compiled engine
     * @throws RulePackException    if the pack cannot be compiled, which includes a pack
     *                              that names Java rules, since those have to be handed over
     * @throws NullPointerException if an argument is {@code null}
     */
    public static RuleEngine compile(RulePack pack, Registry registry) {
        Objects.requireNonNull(pack, "pack");
        return compile(pack, registry, CodeLists.bundled(pack), JavaRules.none());
    }

    /**
     * Compiles a pack.
     *
     * @param pack      the pack
     * @param registry  the registry of the edition the documents will name
     * @param codeLists the snapshots the manifest names
     * @param javaRules the instances of the classes the manifest names, and no others
     * @return the compiled engine
     * @throws RulePackException    if the pack cannot be compiled
     * @throws NullPointerException if an argument is {@code null}
     */
    public static RuleEngine compile(RulePack pack, Registry registry, CodeLists codeLists,
                                     JavaRules javaRules) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(codeLists, "codeLists");
        Objects.requireNonNull(javaRules, "javaRules");
        checkSnapshots(pack, codeLists);
        checkJavaRules(pack, javaRules);
        Set<String> rootValueKeys = new LinkedHashSet<>();
        Set<String> rootGroupKeys = new LinkedHashSet<>();
        Compiler compiler = new Compiler(registry, codeLists, rootValueKeys, rootGroupKeys);
        Map<String, CompiledRule> byId = new LinkedHashMap<>();
        for (RuleDefinition definition : pack.rules()) {
            add(byId, language(definition, compiler, registry), pack);
        }
        for (String className : pack.javaRules()) {
            add(byId, java(javaRules.require(className), compiler, registry, className), pack);
        }
        List<CompiledRule> ordered = new ArrayList<>(byId.values());
        ordered.sort(Comparator.comparing(CompiledRule::id));
        return new RuleEngine(pack, registry, codeLists, List.copyOf(ordered),
                Set.copyOf(rootValueKeys), Set.copyOf(rootGroupKeys));
    }

    private static void checkSnapshots(RulePack pack, CodeLists codeLists) {
        for (String listId : pack.codeLists().keySet()) {
            codeLists.require(listId);
        }
    }

    private static void checkJavaRules(RulePack pack, JavaRules javaRules) {
        Set<String> named = new TreeSet<>(pack.javaRules());
        Set<String> handed = new TreeSet<>(javaRules.classNames());
        if (!named.equals(handed)) {
            Set<String> missing = new TreeSet<>(named);
            missing.removeAll(handed);
            Set<String> extra = new TreeSet<>(handed);
            extra.removeAll(named);
            throw new RulePackException("the pack " + pack.name() + " names the Java rules "
                    + named + " and the engine was handed " + handed
                    + (missing.isEmpty() ? "" : "; missing " + missing)
                    + (extra.isEmpty() ? "" : "; not declared " + extra));
        }
    }

    private static void add(Map<String, CompiledRule> byId, CompiledRule rule, RulePack pack) {
        if (byId.put(rule.id(), rule) != null) {
            throw new RulePackException("the pack " + pack.name() + " carries the rule "
                    + rule.id() + " twice");
        }
    }

    private static CompiledRule language(RuleDefinition definition, Compiler compiler, Registry registry) {
        String where = "the rule " + definition.id();
        PathPattern context = context(definition.context(), compiler, registry, where);
        Compiler.Scope scope = scopeOf(context);
        Map<String, Compiler.Compiled> bindings = new LinkedHashMap<>();
        for (Map.Entry<String, Json> bound : definition.bindings().entrySet()) {
            bindings.put(bound.getKey(),
                    compiler.compile(bound.getValue(), scope, where + ", bind " + bound.getKey()));
        }
        Expression expression = assertion(definition.assertion(), compiler, scope, where);
        MessageTemplate message = MessageTemplate.compile(definition.message(), compiler, scope,
                bindings, where + ", message");
        Expression second = definition.warning()
                .map(warning -> assertion(warning.assertion(), compiler, scope,
                        where + ", warn"))
                .orElse(null);
        MessageTemplate secondMessage = definition.warning()
                .map(warning -> MessageTemplate.compile(warning.message(), compiler, scope,
                        bindings, where + ", warn message"))
                .orElse(null);
        return new CompiledRule(definition.id(), definition.severity(), context, definition.terms(),
                definition.source(), (evaluation, base) -> {
                    if (!holds(expression, evaluation, base)) {
                        return Optional.of(new CompiledRule.Outcome(definition.severity(),
                                message.expand(evaluation, base)));
                    }
                    if (second != null && !holds(second, evaluation, base)) {
                        return Optional.of(new CompiledRule.Outcome(RuleSeverity.WARNING,
                                secondMessage.expand(evaluation, base)));
                    }
                    return Optional.empty();
                });
    }

    private static Expression assertion(Json written, Compiler compiler, Compiler.Scope scope,
                                        String where) {
        Compiler.Compiled compiled = compiler.compile(written, scope, where);
        if (compiled.type() != RuleType.BOOLEAN) {
            throw new RulePackException(where + ": an assertion is a truth value, and this one is "
                    + compiled.type().token());
        }
        return compiled.expression();
    }

    /** Tells whether an assertion holds, which it does where it cannot be decided. */
    private static boolean holds(Expression expression, Evaluation evaluation, SemanticPath base) {
        RuleValue truth = expression.evaluate(evaluation, base);
        return truth.isAbsent() || Boolean.TRUE.equals(truth.truth());
    }

    private static CompiledRule java(JavaRule rule, Compiler compiler, Registry registry, String className) {
        String where = "the Java rule " + className;
        if (rule.severity() == RuleSeverity.INFO) {
            throw new RulePackException(where + " declares the severity info, which is the engine's");
        }
        PathPattern context = context(rule.context(), compiler, registry, where);
        Compiler.Scope scope = scopeOf(context);
        Resolver resolver = new Resolver(compiler, scope, where);
        if (scope.rootBased()) {
            for (String root : rule.roots()) {
                compiler.foresee(resolver.pattern(root));
            }
        }
        return new CompiledRule(rule.id(), rule.severity(), context, rule.terms(), rule.source(),
                (evaluation, base) -> {
                    RuleContext ruleContext = new RuleContext(evaluation, base, resolver);
                    Optional<String> failed = rule.check(ruleContext);
                    if (failed.isPresent()) {
                        return failed.map(text ->
                                new CompiledRule.Outcome(rule.severity(), text));
                    }
                    return rule.warn(ruleContext).map(text ->
                            new CompiledRule.Outcome(RuleSeverity.WARNING, text));
                });
    }

    private static Compiler.Scope scopeOf(PathPattern context) {
        return context == null
                ? new Compiler.Scope(List.of(), true)
                : new Compiler.Scope(context.terms(), false);
    }

    private static PathPattern context(String written, Compiler compiler, Registry registry,
                                      String where) {
        if (written.equals("/")) {
            return null;
        }
        PathPattern pattern = PathPattern.compile(written, List.of(), registry, where + ", context");
        compiler.foresee(pattern);
        if (!pattern.endsAtGroup()) {
            throw new RulePackException(where + ": the context " + written
                    + " ends at a business term; a rule is a statement about the document or"
                    + " about a business group instance");
        }
        return pattern;
    }

    /**
     * Returns the pack this engine was compiled from.
     *
     * @return the pack
     */
    public RulePack pack() {
        return pack;
    }

    /**
     * Returns the registry the paths of the pack were resolved against.
     *
     * @return the registry
     */
    public Registry registry() {
        return registry;
    }

    /**
     * Returns the code list snapshots this engine decides membership against.
     *
     * @return the snapshots
     */
    public CodeLists codeLists() {
        return codeLists;
    }

    /**
     * Returns the identifiers of the rules of this engine, in the order they are evaluated.
     *
     * @return the identifiers, sorted
     */
    public List<String> ruleIds() {
        List<String> ids = new ArrayList<>(rules.size());
        for (CompiledRule rule : rules) {
            ids.add(rule.id());
        }
        return List.copyOf(ids);
    }

    /**
     * Returns the business terms and groups a rule of this engine declares that it reads.
     *
     * <p>It is what a coverage table is built from: which terms of the model the pack has
     * something to say about, and which it does not.
     *
     * @param ruleId the identifier of the rule
     * @return the identifiers the rule declares, or an empty optional if this engine has no
     *         such rule
     */
    public Optional<List<String>> termsOf(String ruleId) {
        for (CompiledRule rule : rules) {
            if (rule.id().equals(ruleId)) {
                return Optional.of(rule.terms());
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the clause a rule of this engine states.
     *
     * @param ruleId the identifier of the rule
     * @return the reference, or an empty optional if this engine has no such rule
     */
    public Optional<String> sourceOf(String ruleId) {
        for (CompiledRule rule : rules) {
            if (rule.id().equals(ruleId)) {
                return Optional.of(rule.source());
            }
        }
        return Optional.empty();
    }

    /**
     * Runs every rule of the pack over a document.
     *
     * <p>One exception of a rule is caught here and turned into a finding: the internal
     * signal a context accessor raises when a value does not spell what its semantic data
     * type requires. That is reported at {@link RuleSeverity#INFO} as a rule that was not
     * decided, because the defect belongs to the value and a structural layer has already
     * named it.
     *
     * <p>Nothing else is caught. A {@link RulePackException} from a rule written in Java —
     * which is what a path that rule wrote and the registry does not admit raises, on the
     * first document that reaches it — and any other runtime exception leave this method
     * unchanged, because a rule that cannot run is a defect of the pack and never a
     * statement about the invoice. A caller that runs a pack it was handed treats that the
     * way it treats a pack that would not compile.
     *
     * <p>The document has to name the edition this engine was compiled for. A pack states
     * rules about the business terms of one edition, and a path is an address relative to
     * an edition (specification, section 10), so a pack run over a document of another one
     * would answer with rule identifiers that say nothing true about that document. The
     * engine refuses instead, and a caller that holds a document of an edition it has no
     * pack for reports that nothing was checked rather than a verdict.
     *
     * @param document the document; it is not changed
     * @return the findings, in a deterministic order: the rule identifier, then the first
     *         path the rule read
     * @throws NullPointerException if {@code document} is {@code null}
     * @throws IllegalArgumentException if the document names an edition other than the one
     *                                  this engine was compiled for
     * @throws RulePackException if a rule written in Java addresses a path the registry does
     *                           not admit
     */
    public List<RuleFinding> evaluate(SemanticDocument document) {
        Objects.requireNonNull(document, "document");
        if (!registry.describes(document.semanticModel())) {
            throw new IllegalArgumentException("this pack states rules about "
                    + registry.semanticModel() + " and the document names "
                    + document.semanticModel());
        }
        Evaluation evaluation = new Evaluation(document, codeLists,
                new DocumentIndex(document, rootValueKeys, rootGroupKeys));
        List<RuleFinding> findings = new ArrayList<>();
        for (CompiledRule rule : rules) {
            for (SemanticPath base : bases(rule, evaluation)) {
                evaluation.begin();
                try {
                    rule.check(evaluation, base).ifPresent(outcome -> findings.add(
                            finding(rule, outcome.severity(), outcome.message(), evaluation.reads())));
                } catch (Undecided undecided) {
                    findings.add(finding(rule, RuleSeverity.INFO,
                            "not decided: " + undecided.getMessage(), evaluation.reads()));
                }
            }
        }
        findings.sort(RuleFinding.ORDER);
        return List.copyOf(findings);
    }

    private static List<SemanticPath> bases(CompiledRule rule, Evaluation evaluation) {
        return rule.context() == null
                ? List.of(SemanticPath.root())
                : evaluation.instances(rule.context(), SemanticPath.root());
    }

    private RuleFinding finding(CompiledRule rule, RuleSeverity severity, String message,
                                List<String> paths) {
        return new RuleFinding(rule.id(), RuleCategory.of(rule.id()), severity, message, paths,
                pack.id(), pack.version(), RuleFinding.NATIVE_ENGINE);
    }

    /**
     * Returns the pack and how many rules it carries.
     *
     * @return a short description of the engine
     */
    @Override
    public String toString() {
        return "rule engine for " + pack.name() + " with " + rules.size() + " rules";
    }
}
