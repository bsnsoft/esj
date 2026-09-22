package de.bsnsoft.esj.upgrade;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.PathSegment;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.StructuralValidator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes a document of one edition of the semantic model as a document of another.
 *
 * <p>What changes between the two editions is read from a mapping file — which paths move,
 * which terms only one edition has, what an upgrade cannot decide and what a downgrade
 * refuses; see {@link UpgradeMapping}. Nothing about a particular pair of editions is
 * written in this class, so a later edition arrives as one more file and not as a release
 * of this library. The registry of the target edition decides two things the mapping does
 * not state: whether a segment carries an occurrence index, which is the rule of the
 * specification, section 5.3 applied to that edition, and whether the result satisfies the
 * model at all.
 *
 * <p>Two rules hold in both directions.
 *
 * <ul>
 *   <li><b>Nothing is repaired.</b> A value with more fraction digits than the target
 *       edition allows is reported and never rounded; a component the target edition
 *       requires is reported and never invented; the specification identifier BT-24 is
 *       reported and replaced only where the caller names the replacement.</li>
 *   <li><b>Nothing is lost in silence.</b> A value the target edition has no address for
 *       makes the run refuse and name every such path. A caller who accepts the loss names
 *       the paths that may be dropped, and every dropped value stands in the report.</li>
 * </ul>
 *
 * <p>The result carries the provenance of the specification, section 4.7 where the caller
 * hands over the bytes it derives from: {@code syntax} is {@code ESJ} and {@code sha256}
 * is the digest of those bytes. Where the caller hands over none, the {@code source} of
 * the input stands unchanged, so that a run of this class over a document and back gives
 * the canonical bytes it started from.
 */
public final class EditionUpgrade {

    /** The syntax an ESJ document derived from another ESJ document names as its source. */
    private static final String ESJ = "ESJ";

    private EditionUpgrade() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the editions this build can write a document of a given edition as.
     *
     * @param semanticModel the {@code semanticModel} member of a document
     * @return the edition keys, in the order {@link Registry#editions()} gives them
     */
    public static List<String> targets(String semanticModel) {
        Objects.requireNonNull(semanticModel, "semanticModel");
        List<String> targets = new ArrayList<>();
        for (String edition : Registry.editions()) {
            if (isAvailable(semanticModel, edition)) {
                targets.add(edition);
            }
        }
        return List.copyOf(targets);
    }

    /**
     * Tells whether this build can write a document of one edition as another.
     *
     * @param semanticModel the {@code semanticModel} member of a document
     * @param targetEdition the key of the target edition, as {@link Registry#editions()}
     *                      spells it
     * @return {@code true} where this build carries the registry of the target edition and
     *         a mapping between the two
     */
    public static boolean isAvailable(String semanticModel, String targetEdition) {
        Objects.requireNonNull(semanticModel, "semanticModel");
        Objects.requireNonNull(targetEdition, "targetEdition");
        if (!Registry.editions().contains(targetEdition)) {
            return false;
        }
        Registry target = Registry.forEdition(targetEdition);
        if (target.describes(semanticModel)) {
            return false;
        }
        return direction(semanticModel, target) != null;
    }

    /**
     * Writes a document as a document of another edition.
     *
     * @param document      the document to upgrade or downgrade
     * @param targetEdition the key of the target edition, as {@link Registry#editions()}
     *                      spells it
     * @param options       what the caller decides
     * @return the result: a document of the target edition and what the run has to say
     *         about it, or the reasons there is none
     * @throws EsjFormatException   if this build carries no mapping from the edition of
     *                              the document to that edition
     * @throws NullPointerException if an argument is {@code null}
     */
    public static UpgradeResult apply(SemanticDocument document,
                                      String targetEdition,
                                      UpgradeOptions options) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(targetEdition, "targetEdition");
        Objects.requireNonNull(options, "options");
        if (!Registry.editions().contains(targetEdition)) {
            throw new EsjFormatException("this build carries no registry of the edition "
                    + targetEdition + "; it carries " + Registry.editions());
        }
        Registry target = Registry.forEdition(targetEdition);
        if (target.describes(document.semanticModel())) {
            throw new EsjFormatException("the document already names the edition "
                    + target.edition());
        }
        Direction direction = direction(document.semanticModel(), target);
        if (direction == null) {
            throw new EsjFormatException("this build carries no mapping from "
                    + document.semanticModel() + " to " + target.edition());
        }
        return new Run(document, target, direction, options).call();
    }

    /**
     * Returns how a document of one edition reaches another, or {@code null} where this
     * build carries no mapping for the pair.
     */
    private static Direction direction(String semanticModel, Registry target) {
        for (UpgradeMapping mapping : UpgradeMapping.available()) {
            if (mapping.from().semanticModel().equals(semanticModel)
                    && mapping.to().edition().equals(target.edition())) {
                return new Direction(mapping, true);
            }
            if (mapping.to().semanticModel().equals(semanticModel)
                    && mapping.from().edition().equals(target.edition())) {
                return new Direction(mapping, false);
            }
        }
        return null;
    }

    /** A mapping and the way it is being read. */
    private record Direction(UpgradeMapping mapping, boolean forward) {
    }

    /** One step of a path: a term and the occurrence index of that term, where it has one. */
    private record Step(String term, String index) {
    }

    /** A chain of identifiers that is rewritten into another. */
    private record Rule(List<String> from, List<String> to) {
    }

    /** One upgrade of one document. */
    private static final class Run {

        private final SemanticDocument document;
        private final Registry target;
        private final Registry checked;
        private Registry inputRegistry;
        private final UpgradeMapping mapping;
        private final boolean forward;
        private final UpgradeOptions options;
        private final List<Rule> rules = new ArrayList<>();
        private final Set<String> absent = new LinkedHashSet<>();
        private final List<UpgradeNote> notes = new ArrayList<>();
        private final Map<String, String> statements = new LinkedHashMap<>();
        private final Map<SemanticPath, SemanticValue> values =
                new TreeMap<>(SemanticPath.canonicalOrder());
        private final Map<SemanticPath, SemanticPath> origins =
                new TreeMap<>(SemanticPath.canonicalOrder());
        private final Map<String, Integer> carried = new LinkedHashMap<>();

        Run(SemanticDocument document, Registry target, Direction direction,
            UpgradeOptions options) {
            this.document = document;
            this.target = target;
            this.mapping = direction.mapping();
            this.forward = direction.forward();
            this.options = options;
            this.checked = combined(target, options);
            for (UpgradeMapping.PathRewrite rewrite : mapping.pathRewrites()) {
                if (rewrite.change() == UpgradeMapping.Change.MOVED) {
                    rules.add(forward ? new Rule(rewrite.from(), rewrite.to())
                            : new Rule(rewrite.to(), rewrite.from()));
                }
            }
            absent.addAll(forward ? mapping.removedTerms() : mapping.addedTerms().keySet());
        }

        /**
         * Returns the registry the result is checked against: the target registry with
         * every extension the caller named that was written against this edition. An
         * extension names the edition it was written for, and one written for another
         * edition is left out rather than combined; its terms are then carried and
         * reported as unchecked.
         */
        private static Registry combined(Registry target, UpgradeOptions options) {
            Registry checked = target;
            for (Registry extension : options.extensions()) {
                try {
                    checked = checked.withExtension(extension);
                } catch (EsjFormatException e) {
                    // Written against another edition: carried, and reported as unchecked.
                }
            }
            return checked;
        }

        UpgradeResult call() {
            inputRegistry = sourceRegistry();
            carried.putAll(findings(document, inputRegistry));
            rewrite();
            if (!refused()) {
                report();
            }
            SemanticDocument result = build();
            if (!refused()) {
                check(result);
            }
            if (!refused() && options.strict()) {
                strict();
            }
            UpgradeReport report = new UpgradeReport(document.semanticModel(),
                    target.semanticModel(), notes, statements);
            return refused()
                    ? new UpgradeResult(UpgradeResult.Outcome.REFUSED, Optional.empty(), report)
                    : new UpgradeResult(UpgradeResult.Outcome.UPGRADED, Optional.of(result),
                            report);
        }

        /** Moves every value to the address the target edition gives it. */
        private void rewrite() {
            for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
                SemanticPath path = entry.getKey();
                if (options.allowsDropping(path)) {
                    note(UpgradeNote.about(UpgradeNote.Kind.VALUE_DROPPED, path,
                            "dropped: the caller named this path"));
                    continue;
                }
                SemanticPath moved = address(path);
                if (moved == null) {
                    continue;
                }
                SemanticPath taken = origins.get(moved);
                if (taken != null) {
                    occurrences(path, moved, taken);
                    continue;
                }
                origins.put(moved, path);
                values.put(moved, entry.getValue());
                if (!moved.equals(path)) {
                    note(UpgradeNote.about(UpgradeNote.Kind.PATH_REWRITTEN, path,
                            "moved to " + moved));
                }
            }
        }

        /**
         * Returns the address of a value in the target edition, or {@code null} where the
         * run refused it and said why.
         */
        private SemanticPath address(SemanticPath path) {
            List<Step> steps = steps(path);
            for (Step step : steps) {
                String wrong = indexOfItsOwnEdition(step);
                if (wrong != null) {
                    note(UpgradeNote.about(UpgradeNote.Kind.SOURCE_INDEX, path, wrong));
                    return null;
                }
            }
            List<String> chain = new ArrayList<>();
            for (Step step : steps) {
                chain.add(step.term());
            }
            Rule rule = longestRule(chain);
            List<String> to = rule == null ? chain : rewritten(rule, chain);
            for (String term : to) {
                if (absent.contains(term)) {
                    note(UpgradeNote.about(UpgradeNote.Kind.UNMAPPED, path,
                            term + " has no address in " + target.edition()));
                    return null;
                }
            }
            int replaced = rule == null ? 0 : rule.from().size();
            int inserted = rule == null ? 0 : rule.to().size();
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < to.size(); i++) {
                String term = to.get(i);
                Step source = i < inserted
                        ? inPrefix(steps, replaced, term)
                        : steps.get(i - inserted + replaced);
                String index = index(term, source);
                if (index == null && source != null && source.index() != null
                        && !"0".equals(source.index())) {
                    note(UpgradeNote.about(UpgradeNote.Kind.SEVERAL_OCCURRENCES, path,
                            term + " carries the occurrence " + source.index()
                                    + " and " + target.edition() + " addresses one",
                            refusal(term)));
                    return null;
                }
                text.append('/').append(term);
                if (index != null) {
                    text.append('/').append(index);
                }
            }
            for (int i = 0; i < replaced; i++) {
                Step dropped = steps.get(i);
                if (to.contains(dropped.term()) || dropped.index() == null
                        || "0".equals(dropped.index())) {
                    continue;
                }
                note(UpgradeNote.about(UpgradeNote.Kind.SEVERAL_OCCURRENCES, path,
                        dropped.term() + " carries the occurrence " + dropped.index()
                                + " and " + target.edition() + " has no group to hold it",
                        refusal(dropped.term())));
                return null;
            }
            return SemanticPath.of(text.toString());
        }

        /**
         * Names the way a segment of the input disagrees with the model of the edition the
         * document itself names, or returns {@code null} where it agrees with it.
         *
         * <p>Whether a segment carries an occurrence index follows from the declared
         * maximum cardinality of its term (specification, section 5.3), and the two
         * editions do not always answer alike. Writing the address the target edition
         * wants would therefore repair an address the document's own edition calls wrong,
         * and this class repairs nothing: the run refuses and {@code validate} answers
         * that question first. A term the source registry does not define — a term of an
         * extension, or any term at all where this build carries no registry of that
         * edition — is not measured here.
         */
        private String indexOfItsOwnEdition(Step step) {
            if (inputRegistry == null || inputRegistry.term(step.term()).isEmpty()) {
                return null;
            }
            boolean repeatable = inputRegistry.isRepeatable(step.term());
            if (repeatable && step.index() == null) {
                return step.term() + " is declared " + inputRegistry.cardinality(step.term())
                        + " in " + document.semanticModel() + " and its segment carries no"
                        + " occurrence index; this run writes addresses and repairs none";
            }
            if (!repeatable && step.index() != null) {
                return step.term() + " is declared " + inputRegistry.cardinality(step.term())
                        + " in " + document.semanticModel() + " and its segment carries the"
                        + " occurrence index " + step.index()
                        + "; this run writes addresses and repairs none";
            }
            return null;
        }

        /**
         * Returns the occurrence index the segment of a term carries in the target
         * edition, or {@code null} where it carries none.
         *
         * <p>Whether a segment carries an index is decided by the declared maximum
         * cardinality of the term in the edition the path belongs to (specification,
         * section 5.3), and that is a fact of the registry rather than of the mapping. A
         * term no core registry defines — a term of an extension — keeps what the source
         * path gave it.
         */
        private String index(String term, Step source) {
            Optional<Term> known = target.term(term);
            boolean indexed = known.isPresent()
                    ? known.orElseThrow().isRepeatable()
                    : source != null && source.index() != null;
            if (!indexed) {
                return null;
            }
            return source == null || source.index() == null ? "0" : source.index();
        }

        /** Returns the step of the replaced prefix that names a term, or {@code null}. */
        private static Step inPrefix(List<Step> steps, int replaced, String term) {
            for (int i = 0; i < replaced && i < steps.size(); i++) {
                if (steps.get(i).term().equals(term)) {
                    return steps.get(i);
                }
            }
            return null;
        }

        /** Returns the chain with the prefix the rule names replaced by the rule's target. */
        private static List<String> rewritten(Rule rule, List<String> chain) {
            List<String> to = new ArrayList<>(rule.to());
            to.addAll(chain.subList(rule.from().size(), chain.size()));
            return to;
        }

        /** Returns the rule with the longest chain that is a prefix of this one. */
        private Rule longestRule(List<String> chain) {
            Rule longest = null;
            for (Rule rule : rules) {
                if (rule.from().size() > chain.size()) {
                    continue;
                }
                if (!chain.subList(0, rule.from().size()).equals(rule.from())) {
                    continue;
                }
                if (longest == null || rule.from().size() > longest.from().size()) {
                    longest = rule;
                }
            }
            return longest;
        }

        /** Refuses two values that would arrive at one address. */
        private void occurrences(SemanticPath path, SemanticPath moved, SemanticPath taken) {
            note(UpgradeNote.about(UpgradeNote.Kind.SEVERAL_OCCURRENCES, path,
                    "would arrive at " + moved + ", where " + taken + " already stands",
                    refusal(moved.term())));
        }

        /**
         * Returns the identifier of the refusal the mapping states for a term, or the
         * general one for several occurrences.
         */
        private String refusal(String term) {
            return mapping.refusalFor(term)
                    .map(UpgradeMapping.Point::id)
                    .orElse(UpgradeNote.Kind.SEVERAL_OCCURRENCES.point());
        }

        /** Says what the run cannot decide on its own. */
        private void report() {
            schemes();
            specification();
            decimals();
        }

        /**
         * Reports every identifier whose scheme the target edition requires and the
         * document does not carry. The scheme is not derived from anything: an
         * identification scheme is a statement about where an identifier was issued.
         */
        private void schemes() {
            UpgradeMapping.Point point = mapping.openPoints().get("scheme-component-missing");
            if (point == null) {
                return;
            }
            for (Map.Entry<SemanticPath, SemanticValue> entry : values.entrySet()) {
                String term = entry.getKey().term();
                if (!point.terms().contains(term) || entry.getValue().scheme() != null) {
                    continue;
                }
                Optional<Component> component = target.term(term)
                        .flatMap(known -> known.component(Component.Role.SCHEME));
                if (component.isPresent() && component.orElseThrow().isMandatory()) {
                    note(UpgradeNote.about(UpgradeNote.Kind.SCHEME_MISSING, entry.getKey(),
                            term + " carries no scheme and " + target.edition()
                                    + " requires one"));
                }
            }
        }

        /** Reports the specification identifier, and replaces it where the caller said so. */
        private void specification() {
            UpgradeMapping.Point point = mapping.openPoints().get("specification-identifier");
            if (point == null) {
                return;
            }
            for (String term : point.terms()) {
                for (Map.Entry<SemanticPath, SemanticValue> entry
                        : new ArrayList<>(values.entrySet())) {
                    if (!entry.getKey().term().equals(term)) {
                        continue;
                    }
                    Optional<String> replacement = options.specification();
                    if (replacement.isEmpty()) {
                        note(UpgradeNote.about(UpgradeNote.Kind.SPECIFICATION_IDENTIFIER,
                                entry.getKey(), term + " states "
                                        + entry.getValue().content()
                                        + " and was left as it stands"));
                        continue;
                    }
                    values.put(entry.getKey(),
                            SemanticValue.of(replacement.orElseThrow()));
                    note(UpgradeNote.about(UpgradeNote.Kind.SPECIFICATION_REPLACED,
                            entry.getKey(), term + " was replaced by "
                                    + replacement.orElseThrow() + ", as the caller asked"));
                }
            }
        }

        /**
         * Reports every value with more fraction digits than the target edition allows it,
         * and says where a bound was not evaluated.
         *
         * <p>Nothing is rounded. A bound the registry states as a constant is checked
         * here; a bound the registry states as a rule over the currency in use is a fact
         * of a versioned rule pack, not of the registry, and this run says that it did not
         * evaluate it rather than guessing the currency's minor unit.
         */
        private void decimals() {
            int unevaluated = 0;
            Set<String> rules = new LinkedHashSet<>();
            for (Map.Entry<SemanticPath, SemanticValue> entry : values.entrySet()) {
                Optional<Term> term = target.term(entry.getKey().term());
                if (term.isEmpty() || term.orElseThrow().datatype()
                        .filter(datatype -> datatype.isDecimal()).isEmpty()) {
                    continue;
                }
                int scale = scale(entry.getValue());
                if (scale < 0) {
                    continue;
                }
                if (term.orElseThrow().maxDecimals().isPresent()) {
                    int bound = term.orElseThrow().maxDecimals().getAsInt();
                    if (scale > bound) {
                        note(UpgradeNote.about(UpgradeNote.Kind.DECIMALS_OUT_OF_BOUNDS,
                                entry.getKey(), "carries " + scale + " fraction digits and "
                                        + target.edition() + " allows " + bound
                                        + "; the value was not rounded"));
                    }
                    continue;
                }
                Optional<String> rule = term.orElseThrow().maxDecimalsRule();
                if (rule.isPresent()) {
                    unevaluated++;
                    rules.add(rule.orElseThrow());
                }
            }
            if (unevaluated > 0) {
                note(UpgradeNote.ofDocument(UpgradeNote.Kind.DECIMALS_NOT_EVALUATED,
                        unevaluated + " values stand at terms whose bound "
                                + target.edition() + " states as " + rules
                                + "; that bound follows the currency in use, it is a fact of"
                                + " a rule pack rather than of the registry, and this run did"
                                + " not evaluate it"));
            }
        }

        /** Returns the number of fraction digits of a value, or -1 where it is no decimal. */
        private static int scale(SemanticValue value) {
            try {
                BigDecimal decimal = value.asDecimal();
                return Math.max(decimal.scale(), 0);
            } catch (EsjFormatException e) {
                return -1;
            }
        }

        /** Builds the document of the target edition. */
        private SemanticDocument build() {
            SemanticDocument.Builder builder = SemanticDocument.builder()
                    .semanticModel(target.semanticModel());
            for (Map.Entry<SemanticPath, SemanticValue> entry : values.entrySet()) {
                builder.put(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, ExtensionValue> entry : document.extensions().entrySet()) {
                builder.extension(entry.getKey(), entry.getValue());
            }
            Optional<String> digest = options.sourceDigest();
            if (digest.isPresent()) {
                builder.source(ESJ, digest.orElseThrow());
            } else {
                document.source().ifPresent(builder::source);
            }
            return builder.build();
        }

        /**
         * Checks the result against the model of the target edition and says what the
         * mapping does not explain.
         */
        private void check(SemanticDocument result) {
            int unchecked = 0;
            int failing = 0;
            for (Finding finding : StructuralValidator.validate(result, checked).findings()) {
                if (finding.code() == FindingCode.ESJ_L2_NOT_CHECKED) {
                    unchecked++;
                    continue;
                }
                if (!finding.isError() || explained(finding)) {
                    continue;
                }
                if (finding.path().hasExtensionSegment()) {
                    failing++;
                    continue;
                }
                if (wasThereBefore(finding)) {
                    note(UpgradeNote.about(UpgradeNote.Kind.CARRIED_FINDING, finding.path(),
                            finding.code().code() + ": " + finding.message()
                                    + " \u2014 the document carries the same finding against "
                                    + document.semanticModel()));
                    continue;
                }
                note(UpgradeNote.at(UpgradeNote.Kind.MODEL_FINDING,
                        options.partial() ? UpgradeNote.Severity.OPEN_POINT
                                : UpgradeNote.Severity.REFUSAL,
                        finding.path(), finding.code().code() + ": " + finding.message()));
            }
            extensions(unchecked, failing);
        }

        /**
         * Says what became of the extension content, in one sentence for the document.
         *
         * <p>It is one note and not one per path: a sub invoice line tree carries hundreds
         * of values and the answer is the same for all of them. Which paths they are is a
         * question for a validation of the result, not for a report about the move.
         */
        private void extensions(int unchecked, int failing) {
            if (unchecked > 0) {
                note(UpgradeNote.ofDocument(UpgradeNote.Kind.EXTENSION_CARRIED, unchecked
                        + " values stand at extension terms and were carried unchanged; this"
                        + " build loaded no extension registry written against "
                        + target.edition() + ", so they were not checked"));
            }
            if (failing > 0) {
                note(UpgradeNote.ofDocument(UpgradeNote.Kind.EXTENSION_CARRIED, failing
                        + " findings of the loaded extension registry stand against the"
                        + " result; extension content was carried unchanged and is no reason"
                        + " for this run to refuse"));
            }
        }

        /**
         * Returns the registry of the edition the document names, or {@code null} where
         * this build carries none. A document of an edition this build does not know is
         * still upgraded — the mapping says how — and then nothing is known about what it
         * already failed.
         */
        private Registry sourceRegistry() {
            for (String edition : Registry.editions()) {
                Registry registry = Registry.forEdition(edition);
                if (registry.describes(document.semanticModel())) {
                    return combined(registry, options);
                }
            }
            return null;
        }

        /**
         * Returns how often each finding stands against a document, by code and message.
         *
         * <p>The paths of the two editions differ where a term moved, so the count is kept
         * by what the finding says rather than by where it stands; two instances that fail
         * the same way count twice and are matched twice.
         */
        private static Map<String, Integer> findings(SemanticDocument document,
                                                     Registry registry) {
            Map<String, Integer> counted = new LinkedHashMap<>();
            if (registry == null) {
                return counted;
            }
            for (Finding finding : StructuralValidator.validate(document, registry).findings()) {
                if (finding.isError()) {
                    counted.merge(finding.code().code() + '\u0000' + finding.message(), 1,
                            Integer::sum);
                }
            }
            return counted;
        }

        /**
         * Tells whether the document already failed the model of its own edition in this
         * way, and counts the match off, so that a second instance of the same failure is
         * matched only by a second one before it.
         */
        private boolean wasThereBefore(Finding finding) {
            String key = finding.code().code() + '\u0000' + finding.message();
            Integer left = carried.get(key);
            if (left == null || left == 0) {
                return false;
            }
            carried.put(key, left - 1);
            return true;
        }

        /** Tells whether a finding is one the mapping already states as an open point. */
        private boolean explained(Finding finding) {
            if (finding.code() != FindingCode.ESJ_L2_COMPONENT_MISSING) {
                return false;
            }
            UpgradeMapping.Point point = mapping.openPoints().get("scheme-component-missing");
            return point != null && point.terms().contains(finding.path().term());
        }

        /** Refuses a run that was asked for a transformation without an open point. */
        private void strict() {
            int open = 0;
            for (UpgradeNote note : notes) {
                if (note.severity() == UpgradeNote.Severity.OPEN_POINT) {
                    open++;
                }
            }
            if (open > 0) {
                note(UpgradeNote.ofDocument(UpgradeNote.Kind.STRICT,
                        "the run was asked for a document with no open point and left "
                                + open + " of them"));
            }
        }

        private void note(UpgradeNote note) {
            notes.add(note);
            note.point().flatMap(mapping::statement)
                    .ifPresent(statement ->
                            statements.putIfAbsent(note.point().orElseThrow(), statement));
        }

        private boolean refused() {
            for (UpgradeNote note : notes) {
                if (note.severity() == UpgradeNote.Severity.REFUSAL) {
                    return true;
                }
            }
            return false;
        }

        /** Returns the path as a list of terms and the occurrence index of each. */
        private static List<Step> steps(SemanticPath path) {
            List<Step> steps = new ArrayList<>();
            String term = null;
            for (PathSegment segment : path.segments()) {
                if (segment instanceof PathSegment.Term named) {
                    if (term != null) {
                        steps.add(new Step(term, null));
                    }
                    term = named.id();
                } else {
                    steps.add(new Step(term, segment.text()));
                    term = null;
                }
            }
            if (term != null) {
                steps.add(new Step(term, null));
            }
            return steps;
        }
    }
}
