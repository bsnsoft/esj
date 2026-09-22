package de.bsnsoft.esj.generator;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Emits the constrained builder: per business group, and once more for the root of the
 * document, a chain of step interfaces that carries one mandatory member per step, and a
 * package-private class that implements every step of that chain by writing through the
 * typed editor of the group.
 *
 * <p>What the chain enforces is structure, and structure is what the registry carries:
 * which members a group must have, how often each may occur, and which group a member
 * belongs to. A member the model declares mandatory is a step, so the terminal step —
 * the only one that can end the chain — is reachable only once every mandatory member has
 * been written. Business rules, arithmetic, code list membership and VAT logic are not
 * here and are not in the type system: they are the validator's, and the terminal step is
 * where a caller asks for them.
 *
 * <p>Three inputs decide which members are steps. The registry decides what is mandatory.
 * {@code model/derivable-terms.json} names the members a derivation policy of this SDK
 * writes, which are left to {@code derive()} and are settable on the terminal step
 * instead. A profile overlay under {@code model/profiles/} names the members a profile
 * narrows to mandatory and the values it fixes; a fixed value is written when the builder
 * is opened and is therefore not a step either. A profile whose chain for a group differs
 * from the base profile's gets its own types for that group and for every group that
 * refers to it; where nothing differs, the base profile's types are used, so a profile
 * adds only what it changes.
 *
 * <p>The emitted text depends on the registry and those two files alone. It carries no
 * timestamp, no host name and no path, and the order of everything in it is the order of
 * the registry, so two runs over the same inputs produce the same bytes.
 */
final class BuilderSources {

    /** The package the constrained builder is emitted into. */
    static final String PACKAGE = "de.bsnsoft.esj.typed.build";

    /** The package of the typed view and the typed editor the builder writes through. */
    private static final String TYPED = "de.bsnsoft.esj.typed";

    /** The key of the root of the document, which is no business group. */
    private static final String ROOT = "";

    /** The suffix of the interface that holds one group's chain. */
    private static final String STEPS_SUFFIX = "Steps";

    /** The suffix of the class that implements one group's chain. */
    private static final String BUILD_SUFFIX = "Build";

    /** The width the generated sources keep to. */
    private static final int WIDTH = 100;

    /** A blank line of a javadoc comment of a nested interface member. */
    private static final String GAP = "         *\n";

    /** The names the terminal step of the root chain uses for itself. */
    private static final Set<String> RESERVED_MEMBERS = Set.of(
            "build", "derivationReport", "derive", "editor", "validate", "validateOrThrow");

    private final Registry registry;
    private final Naming naming;
    private final Set<String> derivable;
    private final List<BuildFacts.Profile> profiles;
    private final String header;
    private final List<String> keys;
    private final Map<String, List<Term>> children = new LinkedHashMap<>();
    private final List<Map<String, List<Step>>> steps = new ArrayList<>();
    private final List<Map<String, Integer>> owners = new ArrayList<>();

    /**
     * Prepares the emitter.
     *
     * @param registry  the registry the builder is generated from
     * @param derivable the identifiers a derivation policy writes
     * @param profiles  the profiles, the base profile first
     * @param header    the three-line header every generated file carries
     * @throws IllegalStateException if a registry slug would hide a member of the
     *                               terminal step
     */
    BuilderSources(Registry registry,
                   Set<String> derivable,
                   List<BuildFacts.Profile> profiles,
                   String header) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.naming = new Naming(registry);
        this.derivable = Set.copyOf(derivable);
        this.profiles = List.copyOf(profiles);
        this.header = Objects.requireNonNull(header, "header");

        List<String> groupKeys = new ArrayList<>();
        groupKeys.add(ROOT);
        children.put(ROOT, registry.rootTerms());
        for (Term term : registry.terms()) {
            if (term.isGroup()) {
                groupKeys.add(term.id());
                children.put(term.id(), registry.children(term.id()));
            }
        }
        this.keys = List.copyOf(groupKeys);
        for (Term term : registry.terms()) {
            if (RESERVED_MEMBERS.contains(naming.memberName(term))) {
                throw new IllegalStateException("the slug of " + term.id()
                        + " cannot be a member name of the constrained builder: " + term.slug());
            }
        }
        for (BuildFacts.Profile profile : profiles) {
            steps.add(chains(profile));
        }
        assignOwners();
    }

    /**
     * Emits every source file of the constrained builder.
     *
     * @return the file names and their content, in the order of the registry
     */
    Map<String, String> sources() {
        Map<String, String> files = new LinkedHashMap<>();
        for (int profile = 0; profile < profiles.size(); profile++) {
            for (String key : keys) {
                if (owners.get(profile).get(key) != profile) {
                    continue;
                }
                files.put(stepsType(key, profile) + ".java", stepsSource(key, profile));
                files.put(buildType(key, profile) + ".java", buildSource(key, profile));
            }
        }
        files.put("Profile.java", profileSource());
        return files;
    }

    // ---------------------------------------------------------------- step computation

    private Map<String, List<Step>> chains(BuildFacts.Profile profile) {
        Map<String, List<Step>> chains = new LinkedHashMap<>();
        Map<String, Boolean> asked = new LinkedHashMap<>();
        for (String key : keys) {
            chain(key, profile, chains, asked);
        }
        return chains;
    }

    private List<Step> chain(String key,
                             BuildFacts.Profile profile,
                             Map<String, List<Step>> chains,
                             Map<String, Boolean> asked) {
        List<Step> known = chains.get(key);
        if (known != null) {
            return known;
        }
        if (asked.putIfAbsent(key, Boolean.TRUE) != null) {
            // A group that reaches itself. Its chain is being computed one frame up; the
            // answer that frame needs is only whether this group asks for anything, and a
            // group that carries itself does so through an optional member, which is not
            // a step. Returning the empty chain here therefore ends the recursion without
            // changing the result (specification, section 5.6).
            return List.of();
        }
        List<Step> chain = new ArrayList<>();
        Set<String> folded = new LinkedHashSet<>();
        for (Term child : children.get(key)) {
            if (folded.contains(child.id()) || !mandatory(child, profile)
                    || derivable.contains(child.id())
                    || profile.fixed().containsKey(child.id())) {
                continue;
            }
            if (child.isGroup()) {
                if (!chain(child.id(), profile, chains, asked).isEmpty()) {
                    chain.add(new Step(child, null));
                }
                continue;
            }
            Term unit = unitCodeSibling(key, child);
            if (unit != null && mandatory(unit, profile)) {
                folded.add(unit.id());
                chain.add(new Step(child, unit));
            } else {
                chain.add(new Step(child, null));
            }
        }
        List<Step> result = List.copyOf(chain);
        chains.put(key, result);
        return result;
    }

    private boolean mandatory(Term child, BuildFacts.Profile profile) {
        return child.isMandatory() || profile.requires(child.id());
    }

    /**
     * Returns the sibling that carries the unit of measure of a quantity term, which the
     * registry pairs with it by naming its slug the slug of the quantity followed by
     * {@code UnitCode} (EN 16931-1, 6.5), or {@code null} where the term has none.
     */
    private Term unitCodeSibling(String key, Term child) {
        if (TypedSources.datatype(child) != SemanticType.QUANTITY) {
            return null;
        }
        String expected = naming.memberName(child) + "UnitCode";
        for (Term sibling : children.get(key)) {
            if (!sibling.isGroup()
                    && sibling.datatype().orElse(null) == SemanticType.CODE
                    && naming.memberName(sibling).equals(expected)
                    && !sibling.isRepeatable()) {
                return sibling;
            }
        }
        return null;
    }

    private void assignOwners() {
        List<Set<String>> differing = new ArrayList<>();
        for (int profile = 0; profile < profiles.size(); profile++) {
            differing.add(profile == 0 ? Set.of() : ownDifferences(profile));
        }
        for (int profile = 1; profile < profiles.size(); profile++) {
            Set<String> differs = new LinkedHashSet<>(differing.get(profile));
            boolean grew = true;
            while (grew) {
                grew = false;
                for (String key : keys) {
                    if (differs.contains(key)) {
                        continue;
                    }
                    for (Term child : children.get(key)) {
                        if (child.isGroup() && differs.contains(child.id())) {
                            differs.add(key);
                            grew = true;
                            break;
                        }
                    }
                }
            }
            differing.set(profile, differs);
        }
        for (int profile = 0; profile < profiles.size(); profile++) {
            Map<String, Integer> owner = new LinkedHashMap<>();
            for (String key : keys) {
                owner.put(key, differing.get(profile).contains(key) ? profile : 0);
            }
            owners.add(Map.copyOf(owner));
        }
    }

    private Set<String> ownDifferences(int profile) {
        Set<String> differs = new LinkedHashSet<>();
        for (String key : keys) {
            if (!signature(steps.get(0).get(key)).equals(signature(steps.get(profile).get(key)))) {
                differs.add(key);
            }
        }
        return differs;
    }

    private static String signature(List<Step> chain) {
        StringBuilder text = new StringBuilder();
        for (Step step : chain) {
            text.append(step.term().id());
            if (step.unit() != null) {
                text.append('+').append(step.unit().id());
            }
            text.append(' ');
        }
        return text.toString();
    }

    // ------------------------------------------------------------------------- naming

    private String baseType(String key) {
        return ROOT.equals(key) ? Naming.ROOT_TYPE : naming.typeName(key);
    }

    private String editorType(String key) {
        return Naming.editorName(baseType(key));
    }

    private String stepsType(String key, int profile) {
        return baseType(key) + STEPS_SUFFIX + profiles.get(profile).typeSuffix();
    }

    private String buildType(String key, int profile) {
        return baseType(key) + BUILD_SUFFIX + profiles.get(profile).typeSuffix();
    }

    /** Returns the chain interface a group is reached through under a profile. */
    private String stepsOf(String key, int profile) {
        return stepsType(key, owners.get(profile).get(key));
    }

    /** Returns the chain implementation of a group under a profile. */
    private String buildOf(String key, int profile) {
        return buildType(key, owners.get(profile).get(key));
    }

    private String stepName(String key, int profile, int index) {
        List<Step> chain = steps.get(profile).get(key);
        if (index >= chain.size()) {
            return "Buildable";
        }
        if (index == 0) {
            return "Start";
        }
        return "With" + Naming.upperCamel(naming.memberName(chain.get(index - 1).term()));
    }

    // ------------------------------------------------------------------ the interfaces

    private String stepsSource(String key, int profile) {
        List<Step> chain = steps.get(profile).get(key);
        TreeSet<String> imports = new TreeSet<>();
        imports.add(TYPED + "." + editorType(key));
        StringBuilder body = new StringBuilder();

        body.append(chainJavadoc(key, profile, chain));
        body.append("public final class ").append(stepsType(key, profile)).append(" {\n");
        body.append("\n");
        body.append("    private ").append(stepsType(key, profile)).append("() {\n");
        body.append("    }\n");

        if (chain.isEmpty()) {
            body.append("\n");
            body.append("    /**\n");
            body.append(JavaText.wrap("    ", " * ", "The first step of " + article(key)
                    + ", which is its terminal step as well: the model declares nothing in it"
                    + " mandatory that a caller has to state."));
            body.append("     */\n");
            body.append("    public interface Start extends Buildable {\n");
            body.append("    }\n");
        }
        for (int index = 0; index <= chain.size(); index++) {
            body.append("\n");
            body.append(stepInterface(key, profile, chain, index, imports));
        }
        body.append("}\n");
        return file(imports, body.toString());
    }

    private String stepInterface(String key,
                                 int profile,
                                 List<Step> chain,
                                 int index,
                                 TreeSet<String> imports) {
        String name = stepName(key, profile, index);
        boolean terminal = index == chain.size();
        StringBuilder body = new StringBuilder();
        body.append("    /**\n");
        if (terminal) {
            body.append(JavaText.wrap("    ", " * ", "The terminal step of " + article(key)
                    + ": every member the model declares mandatory in it has been written, so what"
                    + " is left are the optional members"
                    + (ROOT.equals(key) ? ", the derivation and the validation." : ".")));
        } else if (index == 0) {
            body.append(JavaText.wrap("    ", " * ", "The first step of " + article(key)
                    + ": nothing has been written into it yet."));
        } else {
            Term written = chain.get(index - 1).term();
            body.append(JavaText.wrap("    ", " * ", JavaText.escape("The step of " + article(key)
                    + " that follows " + written.id() + " " + written.name() + ".")));
        }
        body.append("     */\n");
        body.append("    public interface ").append(name).append(" {\n");

        List<Member> members = terminal
                ? terminalMembers(key, profile, imports)
                : stepMembers(key, profile, chain, index, imports);
        boolean first = true;
        for (Member member : members) {
            if (!first) {
                body.append("\n");
            }
            first = false;
            body.append(member.javadoc());
            body.append(declaration("        ", "", member.signature(), ";"));
        }
        body.append("    }\n");
        return body.toString();
    }

    private List<Member> stepMembers(String key,
                                     int profile,
                                     List<Step> chain,
                                     int index,
                                     TreeSet<String> imports) {
        Step step = chain.get(index);
        String returned = stepsType(key, profile) + "." + stepName(key, profile, index + 1);
        return members(key, profile, step.term(), step.unit(), returned, imports);
    }

    private List<Member> terminalMembers(String key, int profile, TreeSet<String> imports) {
        String returned = stepsType(key, profile) + ".Buildable";
        List<Step> chain = steps.get(profile).get(key);
        Set<String> stepped = new LinkedHashSet<>();
        for (Step step : chain) {
            stepped.add(step.term().id());
            if (step.unit() != null) {
                stepped.add(step.unit().id());
            }
        }
        // A repeatable member that is a step can be written again on the terminal step,
        // where it appends a further occurrence. The two declarations are the same
        // method, so that is possible only where the step is the last one and therefore
        // already returns the terminal step; with a second repeatable member ahead of it
        // the further occurrences go through editor().
        Step last = chain.isEmpty() ? null : chain.get(chain.size() - 1);
        String appendable = last != null && last.term().isRepeatable() ? last.term().id() : null;
        List<Member> members = new ArrayList<>();
        for (Term child : children.get(key)) {
            if (stepped.contains(child.id()) && !child.id().equals(appendable)) {
                continue;
            }
            Term unit = child.isGroup() ? null : unitCodeSibling(key, child);
            if (unit != null && !stepped.contains(unit.id())) {
                members.addAll(members(key, profile, child, unit, returned, imports));
            }
            members.addAll(members(key, profile, child, null, returned, imports));
        }
        members.addAll(escapeHatch(key, imports));
        if (ROOT.equals(key)) {
            members.addAll(documentMembers(returned, imports));
        }
        return members;
    }

    private List<Member> escapeHatch(String key, TreeSet<String> imports) {
        imports.add(TYPED + "." + editorType(key));
        String what = ROOT.equals(key) ? "the document" : "this instance of " + key;
        return List.of(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Returns the typed editor of " + what + ". It is"
                        + " the way to anything this builder does not offer, an extension term"
                        + " above all, and it writes into the same document."),
                GAP,
                JavaText.wrap("        ", " * @return ",
                        "the editor this builder writes through"))),
                editorType(key) + " editor()", "        return editor;\n"));
    }

    private List<Member> documentMembers(String returned, TreeSet<String> imports) {
        imports.add("de.bsnsoft.esj.SemanticDocument");
        imports.add(TYPED + ".DerivationReport");
        imports.add(TYPED + ".Totals");
        imports.add("java.util.Optional");
        String derivation = "de.bsnsoft.esj.typed.DerivationException";
        List<Member> members = new ArrayList<>();
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Derives the amounts this invoice adds up to with"
                        + " {@code Totals.STANDARD} and writes them into it."),
                GAP,
                JavaText.wrap("        ", " * @return ", "this step"),
                JavaText.wrap("        ", " * @throws " + derivation + " ", "if the invoice does"
                        + " not state what the policy needs, or contradicts it"))),
                returned + " derive()",
                "        building.derive(Totals.STANDARD);\n        return this;\n"));
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Derives the amounts this invoice adds up to and"
                        + " writes them into it. What a policy computes, where it rounds and where"
                        + " it refuses is written down in {@link Totals}."),
                GAP,
                JavaText.wrap("        ", " * @param policy ", "the derivation policy"),
                JavaText.wrap("        ", " * @return ", "this step"),
                JavaText.wrap("        ", " * @throws " + derivation + " ", "if the invoice does"
                        + " not state what the policy needs, or contradicts it"),
                JavaText.wrap("        ", " * @throws NullPointerException ",
                        "if {@code policy} is {@code null}"))),
                returned + " derive(Totals policy)",
                "        building.derive(policy);\n        return this;\n"));
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ",
                        "Returns what the last derivation wrote and where it rounded."),
                GAP,
                JavaText.wrap("        ", " * @return ", "the report of the last derivation, or an"
                        + " empty optional where none has run"))),
                "Optional<DerivationReport> derivationReport()",
                "        return building.derivationReport();\n"));
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Checks the invoice as it stands against the"
                        + " structural layers L2 and L3 of the specification and against the"
                        + " profile the builder was opened with."),
                GAP,
                JavaText.wrap("        ", " * @return ", "what the check found"))),
                "BuildReport validate()", "        return building.validate(null);\n"));
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Checks the invoice as it stands against the"
                        + " structural layers L2 and L3 of the specification, against the profile"
                        + " the builder was opened with, and against a set of business rules."),
                GAP,
                JavaText.wrap("        ", " * @param rules ", "the business rules to run, for"
                        + " instance the EN 16931 rule pack of {@code esj-rules}"),
                JavaText.wrap("        ", " * @return ", "what the check found"),
                JavaText.wrap("        ", " * @throws NullPointerException ",
                        "if {@code rules} is {@code null}"))),
                "BuildReport validate(InvoiceRules rules)",
                "        return building.validate(Objects.requireNonNull(rules, \"rules\"));\n"));
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Checks the invoice as {@code validate()} does and"
                        + " refuses where the check found anything."),
                GAP,
                JavaText.wrap("        ", " * @return ", "this step"),
                JavaText.wrap("        ", " * @throws BuildException ",
                        "if the check found anything, carrying the report"))),
                returned + " validateOrThrow()",
                "        building.validateOrThrow(null);\n        return this;\n"));
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Checks the invoice as"
                        + " {@code validate(InvoiceRules)} does and refuses where the check found"
                        + " anything."),
                GAP,
                JavaText.wrap("        ", " * @param rules ", "the business rules to run"),
                JavaText.wrap("        ", " * @return ", "this step"),
                JavaText.wrap("        ", " * @throws BuildException ",
                        "if the check found anything, carrying the report"),
                JavaText.wrap("        ", " * @throws NullPointerException ",
                        "if {@code rules} is {@code null}"))),
                returned + " validateOrThrow(InvoiceRules rules)",
                "        building.validateOrThrow(Objects.requireNonNull(rules, \"rules\"));\n"
                        + "        return this;\n"));
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Returns the invoice as it has been written so"
                        + " far, without deriving and without checking anything."),
                GAP,
                JavaText.wrap("        ", " * @return ",
                        "an immutable document in canonical path order"))),
                "SemanticDocument document()", "        return building.document();\n"));
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Derives, checks and returns the invoice: the"
                        + " three calls {@code derive()}, {@code validateOrThrow()} and"
                        + " {@code document()} in that order."),
                GAP,
                JavaText.wrap("        ", " * @return ",
                        "an immutable document in canonical path order"),
                JavaText.wrap("        ", " * @throws " + derivation + " ",
                        "if the amounts cannot be derived"),
                JavaText.wrap("        ", " * @throws BuildException ",
                        "if the check found anything, carrying the report"))),
                "SemanticDocument build()", "        return building.build(null);\n"));
        members.add(new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", "Derives, checks and returns the invoice, running"
                        + " a set of business rules as part of the check."),
                GAP,
                JavaText.wrap("        ", " * @param rules ", "the business rules to run"),
                JavaText.wrap("        ", " * @return ",
                        "an immutable document in canonical path order"),
                JavaText.wrap("        ", " * @throws " + derivation + " ",
                        "if the amounts cannot be derived"),
                JavaText.wrap("        ", " * @throws BuildException ",
                        "if the check found anything, carrying the report"),
                JavaText.wrap("        ", " * @throws NullPointerException ",
                        "if {@code rules} is {@code null}"))),
                "SemanticDocument build(InvoiceRules rules)",
                "        return building.build(Objects.requireNonNull(rules, \"rules\"));\n"));
        return members;
    }

    // ---------------------------------------------------------------------- the members

    private List<Member> members(String key,
                                 int profile,
                                 Term child,
                                 Term unit,
                                 String returned,
                                 TreeSet<String> imports) {
        if (child.isGroup()) {
            return List.of(groupMember(key, profile, child, returned, imports));
        }
        if (child.isRepeatable()) {
            return repeatedMembers(profile, child, returned, imports);
        }
        return valueMembers(profile, child, unit, returned, imports);
    }

    private Member groupMember(String key,
                               int profile,
                               Term child,
                               String returned,
                               TreeSet<String> imports) {
        imports.add("java.util.function.Function");
        String chainType = stepsOf(child.id(), profile);
        String parameter = "Function<" + chainType + ".Start, " + chainType + ".Buildable> block";
        String member = child.isRepeatable()
                ? naming.singularMemberName(child)
                : naming.memberName(child);
        String what = child.isRepeatable()
                ? "Appends one instance of " + child.id() + " and writes into it."
                : "Writes into the instance of " + child.id() + " of " + article(key) + ".";
        return new Member(javadoc(List.of(
                JavaText.wrap("        ", " * ", JavaText.escape(
                        child.id() + " " + child.name() + ". " + child.description())),
                GAP,
                JavaText.wrap("        ", " * <p>", JavaText.escape(what
                        + " Declared cardinality " + child.cardinality() + "."
                        + narrowed(profile, child) + derived(child))),
                GAP,
                JavaText.wrap("        ", " * @param block ", "what to write into that instance;"
                        + " it ends on the terminal step of the group, so every member the model"
                        + " declares mandatory in it has been written when it returns"),
                JavaText.wrap("        ", " * @return ", returnText(returned)),
                JavaText.wrap("        ", " * @throws NullPointerException ",
                        "if {@code block} is {@code null}"))),
                returned + " " + member + "(" + parameter + ")",
                blockBody(child, chainType, profile, returned));
    }

    private String blockBody(Term child, String chainType, int profile, String returned) {
        String build = buildOf(child.id(), profile);
        String editor = child.isRepeatable()
                ? "editor." + naming.memberName(child) + "().add()"
                : "editor." + naming.memberName(child) + "()";
        StringBuilder body = new StringBuilder();
        if (!child.isRepeatable()) {
            body.append("        once(\"").append(child.id()).append("\", \"")
                    .append(JavaText.escape(child.name()).replace("\"", "\\\"")).append("\");\n");
        }
        String call = "Objects.requireNonNull(block, \"block\").apply(new "
                + build + "(" + editor + "));";
        if (call.length() + 8 <= WIDTH) {
            body.append("        ").append(call).append("\n");
        } else {
            body.append("        Objects.requireNonNull(block, \"block\").apply(\n");
            body.append("                new ").append(build).append("(").append(editor)
                    .append("));\n");
        }
        body.append("        return this;\n");
        return body.toString();
    }

    private List<Member> repeatedMembers(int profile,
                                         Term child,
                                         String returned,
                                         TreeSet<String> imports) {
        SemanticType type = TypedSources.datatype(child);
        String member = naming.singularMemberName(child);
        String list = "editor." + naming.memberName(child) + "()";
        List<Member> members = new ArrayList<>();
        if (type == SemanticType.IDENTIFIER) {
            imports.add(TYPED + ".Identifier");
            List<String> names = new ArrayList<>(List.of("value"));
            if (child.component(Component.Role.SCHEME).isPresent()) {
                names.add("scheme");
            }
            if (child.component(Component.Role.SCHEME_VERSION).isPresent()) {
                names.add("schemeVersion");
            }
            int smallest = TypedSources.schemeIsMandatory(child) ? 2 : 1;
            for (int arity = smallest; arity <= names.size(); arity++) {
                List<String> parameters = new ArrayList<>();
                for (int i = 0; i < arity; i++) {
                    parameters.add("String " + names.get(i));
                }
                members.add(appender(profile, child, returned, member, parameters,
                        list + ".add(" + String.join(", ", names.subList(0, arity)) + ");"));
            }
            members.add(appender(profile, child, returned, member, List.of("Identifier value"),
                    list + ".add(value);"));
            return members;
        }
        Naming.valueImport(type).ifPresent(imports::add);
        members.add(appender(profile, child, returned, member,
                List.of(Naming.setterType(type) + " value"), list + ".add(value);"));
        return members;
    }

    private Member appender(int profile,
                            Term child,
                            String returned,
                            String member,
                            List<String> parameters,
                            String call) {
        List<String> tags = new ArrayList<>();
        tags.add(JavaText.wrap("        ", " * @param value ",
                "the value of the occurrence this call appends"));
        for (String parameter : parameters) {
            if (parameter.endsWith(" scheme")) {
                tags.add(JavaText.wrap("        ", " * @param scheme ",
                        "the identification scheme of that identifier"));
            } else if (parameter.endsWith(" schemeVersion")) {
                tags.add(JavaText.wrap("        ", " * @param schemeVersion ",
                        "the version of that identification scheme"));
            }
        }
        tags.add(JavaText.wrap("        ", " * @return ", returnText(returned)));
        return new Member(memberJavadoc(profile, child, tags),
                returned + " " + member + "(" + String.join(", ", parameters) + ")",
                "        " + call + "\n        return this;\n");
    }

    private List<Member> valueMembers(int profile,
                                      Term child,
                                      Term unit,
                                      String returned,
                                      TreeSet<String> imports) {
        SemanticType type = TypedSources.datatype(child);
        String member = naming.memberName(child);
        String guard = "        once(\"" + child.id() + "\", \""
                + JavaText.escape(child.name()).replace("\"", "\\\"") + "\");\n";
        List<Member> members = new ArrayList<>();
        if (type == SemanticType.BINARY_OBJECT) {
            imports.add(TYPED + ".BinaryObject");
            members.add(new Member(memberJavadoc(profile, child, List.of(
                    JavaText.wrap("        ", " * @param bytes ", "the content of the file"),
                    JavaText.wrap("        ", " * @param mimeCode ", "the media type of the file"),
                    JavaText.wrap("        ", " * @param filename ", "the file name of the file"),
                    JavaText.wrap("        ", " * @return ", returnText(returned)))),
                    returned + " " + member + "(byte[] bytes, String mimeCode, String filename)",
                    guard + "        editor." + member + "(bytes, mimeCode, filename);\n"
                            + "        return this;\n"));
            members.add(new Member(memberJavadoc(profile, child, List.of(
                    JavaText.wrap("        ", " * @param value ",
                            "the file as the view of another document hands it over"),
                    JavaText.wrap("        ", " * @return ", returnText(returned)))),
                    returned + " " + member + "(BinaryObject value)",
                    guard + "        editor." + member + "(value);\n        return this;\n"));
            return members;
        }
        if (type == SemanticType.IDENTIFIER) {
            imports.add(TYPED + ".Identifier");
            List<String> names = new ArrayList<>(List.of("value"));
            if (child.component(Component.Role.SCHEME).isPresent()) {
                names.add("scheme");
            }
            if (child.component(Component.Role.SCHEME_VERSION).isPresent()) {
                names.add("schemeVersion");
            }
            int smallest = TypedSources.schemeIsMandatory(child) ? 2 : 1;
            for (int arity = smallest; arity <= names.size(); arity++) {
                List<String> parameters = new ArrayList<>();
                List<String> tags = new ArrayList<>();
                for (int i = 0; i < arity; i++) {
                    parameters.add("String " + names.get(i));
                }
                tags.add(JavaText.wrap("        ", " * @param value ", "the identifier"
                        + (arity == 1 ? "; where the argument is the literal {@code null}, Java"
                                + " cannot choose between this overload and the one taking an"
                                + " {@code Identifier}, so write {@code (String) null} instead"
                                : "")));
                if (arity > 1) {
                    tags.add(JavaText.wrap("        ", " * @param scheme ",
                            "the identification scheme"));
                }
                if (arity > 2) {
                    tags.add(JavaText.wrap("        ", " * @param schemeVersion ",
                            "the version of that identification scheme"));
                }
                tags.add(JavaText.wrap("        ", " * @return ", returnText(returned)));
                members.add(new Member(memberJavadoc(profile, child, tags),
                        returned + " " + member + "(" + String.join(", ", parameters) + ")",
                        guard + "        editor." + member + "("
                                + String.join(", ", names.subList(0, arity))
                                + ");\n        return this;\n"));
            }
            members.add(new Member(memberJavadoc(profile, child, List.of(
                    JavaText.wrap("        ", " * @param value ",
                            "the identifier with whatever components it carries"),
                    JavaText.wrap("        ", " * @return ", returnText(returned)))),
                    returned + " " + member + "(Identifier value)",
                    guard + "        editor." + member + "(value);\n        return this;\n"));
            return members;
        }
        if (unit != null) {
            imports.add("java.math.BigDecimal");
            String unitGuard = "        once(\"" + unit.id() + "\", \""
                    + JavaText.escape(unit.name()).replace("\"", "\\\"") + "\");\n";
            members.add(new Member(memberJavadoc(profile, child, List.of(
                    JavaText.wrap("        ", " * @param value ", "the quantity, at whatever scale"
                            + " the caller states it; it is stored as it stands"),
                    JavaText.wrap("        ", " * @param unitCode ", "the unit of measure of that"
                            + " quantity, written to " + unit.id()),
                    JavaText.wrap("        ", " * @return ", returnText(returned)))),
                    returned + " " + member + "(BigDecimal value, String unitCode)",
                    guard + unitGuard + "        editor." + member + "(value, unitCode);\n"
                            + "        return this;\n"));
            return members;
        }
        String setterType = Naming.setterType(type);
        Naming.valueImport(type).ifPresent(imports::add);
        members.add(new Member(memberJavadoc(profile, child, List.of(
                JavaText.wrap("        ", " * @param value ", valueText(type)),
                JavaText.wrap("        ", " * @return ", returnText(returned)))),
                returned + " " + member + "(" + setterType + " value)",
                guard + "        editor." + member + "(value);\n        return this;\n"));
        return members;
    }

    private static String valueText(SemanticType type) {
        return switch (type) {
            case UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE -> "the value, at whatever scale the"
                    + " caller states it; the semantic data type is unlimited in EN 16931-1, and"
                    + " the value is stored as it stands";
            case AMOUNT -> "the amount, of at most two fraction digits";
            default -> "the value to write";
        };
    }

    private static String returnText(String returned) {
        return returned.endsWith(".Buildable") ? "this step" : "the next step of the chain";
    }

    // ------------------------------------------------------------- the implementations

    private String buildSource(String key, int profile) {
        List<Step> chain = steps.get(profile).get(key);
        TreeSet<String> imports = new TreeSet<>();
        imports.add("java.util.Objects");
        imports.add(TYPED + "." + editorType(key));

        String stepsName = stepsType(key, profile);
        List<String> implemented = new ArrayList<>();
        implemented.add(stepsName + ".Start");
        for (int index = 1; index < chain.size(); index++) {
            implemented.add(stepsName + "." + stepName(key, profile, index));
        }
        implemented.add(stepsName + ".Buildable");

        List<Member> members = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>();
        for (int index = 0; index < chain.size(); index++) {
            for (Member member : stepMembers(key, profile, chain, index, imports)) {
                if (signatures.add(member.signature())) {
                    members.add(member);
                }
            }
        }
        for (Member member : terminalMembers(key, profile, imports)) {
            if (signatures.add(member.signature())) {
                members.add(member);
            }
        }

        StringBuilder body = new StringBuilder();
        body.append("/** Writes {@link ").append(stepsName)
                .append("} through the typed editor. */\n");
        body.append("final class ").append(buildType(key, profile))
                .append(" extends BuildSupport\n");
        for (int index = 0; index < implemented.size(); index++) {
            body.append("        ").append(index == 0 ? "implements " : "        ")
                    .append(implemented.get(index))
                    .append(index == implemented.size() - 1 ? " {" : ",").append("\n");
        }
        body.append("\n    private final ").append(editorType(key)).append(" editor;\n");
        if (ROOT.equals(key)) {
            imports.add("de.bsnsoft.esj.SemanticDocument");
            imports.add(TYPED + ".DerivationReport");
            imports.add(TYPED + ".Totals");
            imports.add("java.util.Optional");
            body.append("\n    private final Building building;\n");
            body.append("\n    ").append(buildType(key, profile)).append("(")
                    .append(editorType(key)).append(" editor, Profile<?> profile) {\n");
            body.append("        this.editor = Objects.requireNonNull(editor, \"editor\");\n");
            body.append("        this.building = new Building(editor, profile);\n");
            body.append("    }\n");
        } else {
            body.append("\n    ").append(buildType(key, profile)).append("(")
                    .append(editorType(key)).append(" editor) {\n");
            body.append("        this.editor = Objects.requireNonNull(editor, \"editor\");\n");
            body.append("    }\n");
        }
        for (Member member : members) {
            body.append("\n    @Override\n");
            body.append(declaration("    ", "public ", member.signature(), " {"));
            body.append(member.body());
            body.append("    }\n");
        }
        body.append("\n    @Override\n    public String toString() {\n");
        body.append("        return \"").append(stepsName).append("[\" + editor.path() + \"]\";\n");
        body.append("    }\n");
        body.append("}\n");
        return file(imports, body.toString());
    }

    // ------------------------------------------------------------------------ the profiles

    private String profileSource() {
        TreeSet<String> imports = new TreeSet<>();
        imports.add("de.bsnsoft.esj.SemanticPath");
        imports.add("de.bsnsoft.esj.SemanticValue");
        imports.add(TYPED + ".InvoiceEditor");
        imports.add("java.util.List");
        imports.add("java.util.Objects");
        imports.add("java.util.function.BiFunction");

        StringBuilder body = new StringBuilder();
        body.append("/**\n");
        body.append(JavaText.wrap("", " * ", "One profile the constrained builder can be opened"
                + " with: the values it fixes in every invoice, the cardinalities it narrows, and"
                + " the chain its first step belongs to."));
        body.append(" *\n");
        body.append(JavaText.wrap("", " * <p>", "A profile narrows and fixes; it never widens. What"
                + " it narrows is a fact taken from the profile's own model and recorded under"
                + " {@code model/profiles/}, and it reaches the caller as a step of the chain"
                + " rather than as a rule. Everything else a profile asks of an invoice is a"
                + " business rule and belongs to a rule pack."));
        body.append(" *\n");
        body.append(JavaText.wrap("", " * @param ", "<S> the first step of the chain this profile"
                + " opens"));
        body.append(" */\n");
        body.append("public final class Profile<S> {\n");
        body.append("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ", "The members a derivation policy of this SDK"
                + " writes, which are therefore no steps of any chain and are not asked for"
                + " before {@code derive()} has run. The list is"
                + " {@code model/derivable-terms.json}."));
        body.append("     */\n");
        body.append(JavaText.call("    ", "private static final List<String> DERIVED = List.of(",
                derivedList(), ";"));

        for (int index = 0; index < profiles.size(); index++) {
            BuildFacts.Profile profile = profiles.get(index);
            body.append("\n    /** ").append(JavaText.escape(profile.name())).append(". */\n");
            body.append("    public static final Profile<").append(stepsType(ROOT, index))
                    .append(".Start> ").append(profile.constant()).append(" = new Profile<>(\n");
            body.append("            \"").append(profile.id()).append("\",\n");
            body.append("            \"").append(JavaText.escape(profile.name())).append("\",\n");
            body.append("            List.of(").append(fixedList(profile)).append("),\n");
            body.append("            List.of(").append(narrowingList(profile)).append("),\n");
            body.append("            DERIVED,\n");
            body.append("            (editor, profile) -> new ").append(buildType(ROOT, index))
                    .append("(editor, profile));\n");
        }

        body.append("\n    private final String id;\n");
        body.append("\n    private final String name;\n");
        body.append("\n    private final List<Fixed> fixed;\n");
        body.append("\n    private final List<Narrowing> narrowings;\n");
        body.append("\n    private final List<String> derivable;\n");
        body.append("\n    private final BiFunction<InvoiceEditor, Profile<S>, S> start;\n");
        body.append("\n    private Profile(String id,\n");
        body.append("                    String name,\n");
        body.append("                    List<Fixed> fixed,\n");
        body.append("                    List<Narrowing> narrowings,\n");
        body.append("                    List<String> derivable,\n");
        body.append("                    BiFunction<InvoiceEditor, Profile<S>, S> start) {\n");
        body.append("        this.id = id;\n");
        body.append("        this.name = name;\n");
        body.append("        this.fixed = fixed;\n");
        body.append("        this.narrowings = narrowings;\n");
        body.append("        this.derivable = derivable;\n");
        body.append("        this.start = start;\n");
        body.append("    }\n");

        body.append("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ", "Returns the identifier of the profile."));
        body.append("     *\n");
        body.append("     * @return the identifier\n");
        body.append("     */\n");
        body.append("    public String id() {\n        return id;\n    }\n");

        body.append("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ", "Returns the name a message prints."));
        body.append("     *\n");
        body.append("     * @return the name of the profile\n");
        body.append("     */\n");
        body.append("    public String name() {\n        return name;\n    }\n");

        body.append("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ", "Returns the values this profile writes into every"
                + " invoice it opens."));
        body.append("     *\n");
        body.append("     * @return the fixed values, in the order of the overlay file\n");
        body.append("     */\n");
        body.append("    public List<Fixed> fixed() {\n        return fixed;\n    }\n");

        body.append("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ", "Returns the cardinalities this profile narrows"
                + " against the registry."));
        body.append("     *\n");
        body.append("     * @return the narrowings, in the order of the overlay file\n");
        body.append("     */\n");
        body.append("    public List<Narrowing> narrowings() {\n");
        body.append("        return narrowings;\n    }\n");

        body.append("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ", "Returns the members a derivation policy writes,"
                + " which a builder does not ask for before it has derived."));
        body.append("     *\n");
        body.append("     * @return the identifiers of those terms and groups\n");
        body.append("     */\n");
        body.append("    public List<String> derivable() {\n        return derivable;\n    }\n");

        body.append("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ", "Tells whether this profile asks for at least one"
                + " occurrence of a term or group that the registry leaves optional."));
        body.append("     *\n");
        body.append("     * @param term the identifier of the term or group\n");
        body.append("     * @return whether the profile makes it mandatory\n");
        body.append("     */\n");
        body.append("    public boolean requires(String term) {\n");
        body.append("        for (Narrowing narrowing : narrowings) {\n");
        body.append("            if (narrowing.term().equals(term)) {\n");
        body.append("                return narrowing.min() >= 1;\n");
        body.append("            }\n");
        body.append("        }\n");
        body.append("        return false;\n");
        body.append("    }\n");

        body.append("\n    @Override\n    public String toString() {\n");
        body.append("        return name;\n    }\n");

        body.append("\n    void fix(InvoiceEditor editor) {\n");
        body.append("        for (Fixed value : fixed) {\n");
        body.append("            editor.builder().set(SemanticPath.of(value.path()),\n");
        body.append("                    SemanticValue.of(value.value()));\n");
        body.append("        }\n");
        body.append("    }\n");
        body.append("\n    S start(InvoiceEditor editor) {\n");
        body.append("        fix(editor);\n");
        body.append("        return start.apply(editor, this);\n");
        body.append("    }\n");

        body.append("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ", "One value a profile fixes in every invoice it"
                + " opens. It is written when the builder is opened and can be written again"
                + " afterwards; because it is written, it is no step of the chain."));
        body.append("     *\n");
        body.append("     * @param term  the identifier of the business term\n");
        body.append("     * @param path  the semantic path the value is written to\n");
        body.append("     * @param value the content\n");
        body.append("     */\n");
        body.append("    public record Fixed(String term, String path, String value) {\n");
        body.append("\n        /**\n");
        body.append("         * Checks that every part is present.\n");
        body.append("         *\n");
        body.append("         * @param term  the identifier of the business term\n");
        body.append("         * @param path  the semantic path the value is written to\n");
        body.append("         * @param value the content\n");
        body.append("         * @throws NullPointerException if a part is {@code null}\n");
        body.append("         */\n");
        body.append("        public Fixed {\n");
        body.append("            Objects.requireNonNull(term, \"term\");\n");
        body.append("            Objects.requireNonNull(path, \"path\");\n");
        body.append("            Objects.requireNonNull(value, \"value\");\n");
        body.append("        }\n");
        body.append("    }\n");

        body.append("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ", "One cardinality a profile narrows against the"
                + " registry."));
        body.append("     *\n");
        body.append("     * @param term the identifier of the term or group\n");
        body.append("     * @param name the name of that term or group\n");
        body.append("     * @param min  the minimum number of occurrences the profile allows\n");
        body.append("     * @param max  the maximum number of occurrences the profile allows\n");
        body.append("     */\n");
        body.append("    public record Narrowing(String term, String name, int min, int max) {\n");
        body.append("\n        /**\n");
        body.append("         * Checks that every part is present.\n");
        body.append("         *\n");
        body.append("         * @param term the identifier of the term or group\n");
        body.append("         * @param name the name of that term or group\n");
        body.append("         * @param min  the minimum number of occurrences the profile allows\n");
        body.append("         * @param max  the maximum number of occurrences the profile allows\n");
        body.append("         * @throws NullPointerException if a part is {@code null}\n");
        body.append("         */\n");
        body.append("        public Narrowing {\n");
        body.append("            Objects.requireNonNull(term, \"term\");\n");
        body.append("            Objects.requireNonNull(name, \"name\");\n");
        body.append("        }\n");
        body.append("    }\n");
        body.append("}\n");
        return file(imports, body.toString());
    }

    private List<String> derivedList() {
        List<String> entries = new ArrayList<>();
        for (Term term : registry.terms()) {
            if (derivable.contains(term.id())) {
                entries.add("\"" + term.id() + "\"");
            }
        }
        return entries;
    }

    private String fixedList(BuildFacts.Profile profile) {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String> fixed : sorted(profile.fixed()).entrySet()) {
            Term term = registry.term(fixed.getKey()).orElseThrow(() -> new IllegalStateException(
                    "the profile fixes " + fixed.getKey() + ", which the registry does not know"));
            entries.add("\n                    new Fixed(\"" + term.id() + "\", \"/"
                    + String.join("/", term.path()) + "\", \""
                    + fixed.getValue().replace("\\", "\\\\").replace("\"", "\\\"") + "\")");
        }
        return String.join(",", entries) + (entries.isEmpty() ? "" : "\n            ");
    }

    private String narrowingList(BuildFacts.Profile profile) {
        List<String> entries = new ArrayList<>();
        for (BuildFacts.Narrowing narrowing : sortedNarrowings(profile)) {
            Term term = registry.term(narrowing.id()).orElseThrow(() -> new IllegalStateException(
                    "the profile narrows " + narrowing.id()
                            + ", which the registry does not know"));
            entries.add("\n                    new Narrowing(\"" + term.id() + "\", \""
                    + JavaText.escape(term.name()).replace("\"", "\\\"") + "\", "
                    + narrowing.min() + ", " + narrowing.max() + ")");
        }
        return String.join(",", entries) + (entries.isEmpty() ? "" : "\n            ");
    }

    private Map<String, String> sorted(Map<String, String> source) {
        Map<String, String> ordered = new LinkedHashMap<>();
        for (Term term : registry.terms()) {
            if (source.containsKey(term.id())) {
                ordered.put(term.id(), source.get(term.id()));
            }
        }
        return ordered;
    }

    private List<BuildFacts.Narrowing> sortedNarrowings(BuildFacts.Profile profile) {
        List<BuildFacts.Narrowing> ordered = new ArrayList<>();
        for (Term term : registry.terms()) {
            BuildFacts.Narrowing narrowing = profile.narrows().get(term.id());
            if (narrowing != null) {
                ordered.add(narrowing);
            }
        }
        return ordered;
    }

    // ------------------------------------------------------------------------- text

    private String chainJavadoc(String key, int profile, List<Step> chain) {
        StringBuilder doc = new StringBuilder("/**\n");
        if (ROOT.equals(key)) {
            doc.append(JavaText.wrap("", " * ", "The chain of the invoice as a whole under the "
                    + JavaText.escape(profiles.get(profile).name()) + " profile."));
        } else {
            Term term = registry.term(key).orElseThrow();
            doc.append(JavaText.wrap("", " * ", JavaText.escape(term.id() + " " + term.name()
                    + ". " + term.description())));
        }
        doc.append(" *\n");
        StringBuilder mandatory = new StringBuilder();
        for (Step step : chain) {
            mandatory.append(mandatory.length() == 0 ? "" : ", ").append(step.term().id());
            if (step.unit() != null) {
                mandatory.append(" with ").append(step.unit().id());
            }
        }
        doc.append(JavaText.wrap("", " * <p>", chain.isEmpty()
                ? "Nothing in this group is a step: the model declares nothing in it mandatory that"
                        + " a caller has to state, so its first step is already its terminal one."
                : "One step per mandatory member, in the order of the model: "
                        + mandatory + ". The terminal step carries the optional members."));
        doc.append(" */\n");
        return doc.toString();
    }

    /**
     * Returns what a member's javadoc says about a profile that narrows it, which is why
     * a member the registry declares optional can be a step of that profile's chain.
     */
    private String narrowed(int profile, Term term) {
        BuildFacts.Narrowing narrowing = profiles.get(profile).narrows().get(term.id());
        if (narrowing == null) {
            return "";
        }
        return " The " + profiles.get(profile).name() + " profile narrows it to "
                + narrowing.min() + ".." + narrowing.max() + ".";
    }

    /**
     * Returns what a member's javadoc says about a derivation policy that writes it,
     * which is why a member the model declares mandatory is no step of any chain.
     */
    private String derived(Term term) {
        return derivable.contains(term.id())
                ? " A derivation policy writes it, so it is no step of the chain and"
                        + " {@code derive()} fills it in; this setter states it by hand instead."
                : "";
    }

    private String article(String key) {
        return ROOT.equals(key) ? "the invoice" : "the group " + key;
    }

    private String memberJavadoc(int profile, Term term, List<String> tags) {
        List<String> lines = new ArrayList<>();
        lines.add(JavaText.wrap("        ", " * ", JavaText.escape(
                term.id() + " " + term.name() + ". " + term.description())));
        lines.add(GAP);
        lines.add(JavaText.wrap("        ", " * <p>", JavaText.escape(
                "Declared cardinality " + term.cardinality() + TypedSources.termFacts(term) + "."
                        + narrowed(profile, term) + derived(term))));
        lines.add(GAP);
        lines.addAll(tags);
        return javadoc(lines);
    }

    /**
     * Writes a member declaration, breaking after the opening parenthesis where the line
     * would otherwise run past the width the generated sources keep to.
     */
    private static String declaration(String indent, String prefix, String signature, String tail) {
        String line = indent + prefix + signature + tail;
        if (line.length() <= WIDTH) {
            return line + "\n";
        }
        int open = signature.indexOf('(');
        String head = indent + prefix + signature.substring(0, open + 1);
        String parameters = signature.substring(open + 1);
        String continuation = indent + "        ";
        String second = continuation + parameters + tail;
        int comma = parameters.indexOf(", ");
        if (second.length() <= WIDTH || comma < 0) {
            return head + "\n" + second + "\n";
        }
        return head + "\n"
                + continuation + parameters.substring(0, comma + 1) + "\n"
                + continuation + "        " + parameters.substring(comma + 2) + tail + "\n";
    }

    private static String javadoc(List<String> lines) {
        StringBuilder doc = new StringBuilder("        /**\n");
        for (String line : lines) {
            doc.append(line);
        }
        doc.append("         */\n");
        return doc.toString();
    }

    private String file(TreeSet<String> imports, String body) {
        StringBuilder out = new StringBuilder(header);
        out.append("\n");
        out.append("package ").append(PACKAGE).append(";\n");
        out.append("\n");
        for (String type : imports) {
            out.append("import ").append(type).append(";\n");
        }
        out.append("\n");
        out.append(body);
        return out.toString();
    }

    /**
     * One step of a chain: the member it writes, and the unit code the model pairs with
     * it where the member is a quantity whose unit is mandatory too.
     *
     * @param term the mandatory member
     * @param unit the unit code written in the same call, or {@code null}
     */
    private record Step(Term term, Term unit) {

        /**
         * Checks that the member is present.
         *
         * @throws NullPointerException if {@code term} is {@code null}
         */
        private Step {
            Objects.requireNonNull(term, "term");
        }
    }

    /**
     * One member of a chain: what it looks like on the interface, and what it does in the
     * implementation.
     *
     * @param javadoc   the comment, ending in a line break
     * @param signature the return type, the name and the parameter list
     * @param body      the statements of the implementation, each ending in a line break
     */
    private record Member(String javadoc, String signature, String body) {
    }
}
