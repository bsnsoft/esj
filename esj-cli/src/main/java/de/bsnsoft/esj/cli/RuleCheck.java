package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RulePackException;
import de.bsnsoft.esj.rules.RuleSeverity;
import de.bsnsoft.esj.rules.en16931.En16931;
import de.bsnsoft.esj.syntax.ProfileLevels;
import de.bsnsoft.esj.syntax.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * What the native rule engine had to say about one document, or why it had nothing to say.
 *
 * <p>The engine runs the rules of EN 16931-1, clause 6.4 as rules of this project, written
 * over the business terms of the semantic model rather than over the XPath of a syntax. That
 * is what makes it the third thing {@code esj validate} does, beside the official artefacts
 * and the structural layers, and the reason it exists is the case neither of the other two
 * covers: a document that was never XML has no artefact that will look at it, and until this
 * engine ran, nothing checked whether its sums added up.
 *
 * <p>It runs over an XML input too, and then the same rule can be reported twice — once by the
 * official Schematron over the XML and once here over the imported document. Neither report is
 * merged into the other and neither is suppressed. They are two independent pieces of evidence
 * about two forms of one invoice: the artefact sees the document as it arrived, this engine
 * sees what the import carried into the model, and a disagreement between them is a fact worth
 * seeing rather than a duplicate worth hiding. {@link Reports} therefore prints both, under the
 * engine that produced each, and the JSON report keeps both lists apart.
 *
 * <p>A finding of this engine is a finding about the business rules of a standard. It is a
 * layer of its own, category {@code EN-BR}, {@code EN-DEC} or {@code EN-CL} with the engine
 * {@code native} and the pack identifier on every one, and it is never presented as conformance
 * to the ESJ format, which layers L1 to L3 of the specification, section 9.4 define alone.
 *
 * @param found  what the engine found, empty where it did not run
 * @param reason why it did not run, empty where it did
 */
record RuleCheck(Optional<Found> found, Optional<String> reason) {

    /** The pack this build carries, as it is written in a report. */
    static final String PACK = En16931.PACK_ID + "/" + En16931.VERSION;

    /** The label of the row this check fills in the semantic block. */
    static final String LABEL = "EN 16931 business rules (native, pack " + PACK + ")";

    /** The caller asked for the structural layers alone. */
    static final String BY_OPTION = "skipped (--rules none)";

    /** {@code esj inspect} summarizes a document rather than checking the rules of a standard. */
    static final String NOT_INSPECTED = "not run (esj validate runs the business rules)";

    /** No document was built, so there is nothing for a rule to read. */
    static final String NO_DOCUMENT = "not run (no document was built)";

    /**
     * A structural layer rejected the document, so a rule would be reading paths and values
     * that are not the ones it is written for.
     *
     * <p>It is the rule the layers already follow among themselves. A term the registry does
     * not know, a value that does not spell what its data type requires, an occurrence index
     * on a term that may occur once: a rule that summed over those would report an arithmetic
     * failure whose real cause is one line above it in the same report, and sending an author
     * after the wrong one of two defects is worse than saying nothing about the second.
     *
     * <p>One model finding is not of that kind and does not stop the rules: a supplementary
     * component the registry declares mandatory and the value does not carry. The value is
     * there and is readable, and four rules of the standard are statements about exactly that
     * absence, so both engines name the one defect — the structural layer by its code and the
     * rule pack by the identifier the official artefacts use. {@code Validation} decides it.
     */
    static final String AFTER_MODEL = "not run (a structural layer rejected the document)";

    /**
     * The model layer measured nothing, so the rules have nothing to resolve their paths
     * against: no registry describes the edition the document names, or a path is one only
     * an extension registry defines and none was loaded.
     *
     * <p>It is not {@link #AFTER_MODEL}. Nothing about the document was found wrong; this
     * build simply held no registry to read it by, which is a gap in the coverage of the
     * run (specification, sections 4.4 and 9.2) and leaves the verdict
     * {@code INDETERMINATE} rather than {@code INVALID}.
     */
    static final String NOT_MEASURED = "not run (the model layer measured nothing)";

    /**
     * The document names an edition of the semantic model this build carries no rule pack
     * for.
     *
     * <p>A pack is written against one edition and compiled against its registry: its
     * paths are that edition's addresses, and the rules of EN 16931-1 are renumbered,
     * added to and withdrawn between editions. Running the pack of one edition over a
     * document of another would report arithmetic about terms the document does not have,
     * so it does not run, and the business rules are a component of the check that is
     * missing rather than one that passed — the verdict is {@code INDETERMINATE} with the
     * cause {@link Coverage.Cause#NO_PACK_FOR_EDITION}.
     */
    static final String NO_PACK_FOR_EDITION = "not run (no pack for this edition)";

    /** The engines of this process, one per registry, compiled on first use. */
    private static final ConcurrentMap<Extensions, RuleEngine> ENGINES =
            new ConcurrentHashMap<>();

    /**
     * Creates a check, refusing the two states that are not states.
     *
     * @param found  what the engine found, empty where it did not run
     * @param reason why it did not run, empty where it did
     */
    RuleCheck {
        Objects.requireNonNull(found, "found");
        Objects.requireNonNull(reason, "reason");
        if (found.isPresent() == reason.isPresent()) {
            throw new IllegalArgumentException(
                    "a rule check has either findings or a reason it has none");
        }
    }

    /**
     * Returns a check that did not run.
     *
     * @param reason why not, in English, for the report
     * @return the check
     */
    static RuleCheck notRun(String reason) {
        return new RuleCheck(Optional.empty(), Optional.of(reason));
    }

    /**
     * Runs the pack this build carries over a document.
     *
     * <p>The engine is compiled once per process and per registry rather than once per
     * document. Compiling is where a pack is refused — the paths are resolved, the operators
     * are type-checked, the code list snapshots are read — and none of that depends on the
     * invoice; a command line that validates one document pays it once, and a caller that
     * keeps the process alive pays it once too. An engine holds no mutable state, so sharing
     * one is safe.
     *
     * <p>A pack that cannot be compiled is a condition of the input the tool was given and
     * not a defect of the tool, so it leaves with {@link ExitCode#INPUT} and a sentence
     * naming the pack, the way {@link SyntaxPacks#resolve(String)} answers a broken syntax
     * pack. Only a rebuilt or tampered jar reaches this today, because the pack is bundled;
     * it is written this way because {@code RulePacks.read} is public API and a pack may
     * arrive from a directory a caller was handed.
     *
     * <p>A document of another edition than the one the pack was written against is not
     * run over at all, and {@link #NO_PACK_FOR_EDITION} says so. The edition is a fact of
     * the document and the pack is a fact of this build; neither is a defect of the
     * invoice, so the row of the check is a gap and the verdict is the third state.
     *
     * @param document  the document to check; it is not changed
     * @param extension the extension registries this run loads, which decide the registry the
     *                  paths of the pack are resolved against
     * @param levels    the levels the profile the document names gives rules of the
     *                  validation pack, which are the levels the verdict is made on
     * @return what the rules found
     * @throws NullPointerException if an argument is {@code null}
     * @throws CliException if the rule pack this build carries cannot be read or compiled
     */
    static RuleCheck run(SemanticDocument document, Extensions extension,
                         ProfileLevels levels) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(levels, "levels");
        if (!Validation.registry(extension).describes(document.semanticModel())) {
            return notRun(NO_PACK_FOR_EDITION);
        }
        RuleEngine engine;
        try {
            engine = ENGINES.computeIfAbsent(extension,
                    loaded -> En16931.engine(Validation.registry(loaded)));
        } catch (RulePackException e) {
            throw CliException.input("--rules " + En16931.PACK_ID + ": " + e.getMessage(), e);
        }
        List<Levelled> findings = new ArrayList<>();
        for (RuleFinding finding : engine.evaluate(document)) {
            findings.add(Levelled.of(finding, levels));
        }
        return new RuleCheck(Optional.of(new Found(engine.pack().id(), engine.pack().version(),
                List.copyOf(findings))), Optional.empty());
    }

    /** Tells whether the engine ran at all. */
    boolean checked() {
        return found.isPresent();
    }

    /**
     * Tells whether a rule rejected the document.
     *
     * <p>A check that did not run answers no, the way a structural layer that did not run
     * does: {@link #checked()} is the question that separates "found nothing" from "was not
     * asked".
     *
     * @return whether a fatal finding was made
     */
    boolean failed() {
        return found.map(Found::hasFatal).orElse(false);
    }

    /** Returns what the rules found, empty where the engine did not run. */
    List<Levelled> findings() {
        return found.map(Found::findings).orElse(List.of());
    }

    /**
     * What the engine found over one document.
     *
     * @param packId      the identifier of the pack that was run
     * @param packVersion its version, which is the release of the artefacts it was verified
     *                    against
     * @param findings    what the rules had to say, in the order the engine reports them
     */
    record Found(String packId, String packVersion, List<Levelled> findings) {

        /**
         * Copies the findings.
         *
         * @param packId      the identifier of the pack that was run
         * @param packVersion its version, which is the release of the artefacts it was verified
         *                    against
         * @param findings    what the rules had to say, in the order the engine reports them
         */
        Found {
            Objects.requireNonNull(packId, "packId");
            Objects.requireNonNull(packVersion, "packVersion");
            findings = List.copyOf(findings);
        }

        /** Tells whether any finding decides the verdict. */
        boolean hasFatal() {
            return findings.stream().anyMatch(Levelled::fatal);
        }

        /** Returns how many findings of one severity were made. */
        long count(RuleSeverity severity) {
            return findings.stream().filter(finding -> finding.severity() == severity).count();
        }
    }

    /**
     * One finding of the native pack with the level the verdict is made on.
     *
     * <p>The engine is profile-agnostic: it states what the standard states, at the level
     * the rule declares, and knows nothing about the core invoice usage specifications.
     * The verdict is not. A specification a document names is entitled to say what a rule
     * means for documents of its own profile, and the validation pack carries those
     * levels as data of the profile rather than of the artefact that happens to raise the
     * rule ({@link ProfileLevels}). So the level a finding is counted at is the profile's
     * where the profile states one, and the rule's own everywhere else, and the report
     * prints both the way the syntax block prints an artefact's flag beside the level.
     *
     * <p>The two engines therefore end at the same verdict on the same document, which is
     * the point: the same rule identifier raised by the official artefact and by this pack
     * over one invoice weighs the same, whichever of them raised it.
     *
     * @param finding  what the rule reported, untouched
     * @param severity the level this finding is counted at
     * @param profile  the customization identifier of the profile that levelled it, empty
     *                 where the level is the rule's own
     */
    record Levelled(RuleFinding finding, RuleSeverity severity, String profile) {

        /** Creates a levelled finding. */
        Levelled {
            Objects.requireNonNull(finding, "finding");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(profile, "profile");
        }

        /**
         * Returns a finding at the level the profile of the document gives its rule.
         *
         * @param finding the finding of the engine
         * @param levels  the levels of that profile
         * @return the finding with the level the verdict is made on
         */
        static Levelled of(RuleFinding finding, ProfileLevels levels) {
            Optional<RuleSeverity> levelled = levels.level(finding.code())
                    .map(Levelled::severityOf);
            return levelled.filter(severity -> severity != finding.severity())
                    .map(severity -> new Levelled(finding, severity, levels.profile()))
                    .orElseGet(() -> new Levelled(finding, finding.severity(), ""));
        }

        /** Returns the level a validation pack writes as the level of a rule. */
        private static RuleSeverity severityOf(Severity level) {
            return switch (level) {
                case FATAL -> RuleSeverity.FATAL;
                case WARNING -> RuleSeverity.WARNING;
                case INFORMATION -> RuleSeverity.INFO;
            };
        }

        /** Tells whether the profile levels this rule other than the standard does. */
        boolean levelled() {
            return !profile.isEmpty();
        }

        /** Returns the level the rule declares, which is the level of the standard. */
        RuleSeverity standard() {
            return finding.severity();
        }

        /** Tells whether this finding decides the verdict. */
        boolean fatal() {
            return severity == RuleSeverity.FATAL;
        }
    }
}
