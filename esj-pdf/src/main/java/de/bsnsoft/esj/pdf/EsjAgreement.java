package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.bindings.BindingTable;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Whether an ESJ document and an invoice XML are two accounts of one invoice.
 *
 * <p>A hybrid invoice that carries both has to be right about itself, and this is the one
 * definition of what that means. It is used twice, on purpose: the embedding checks it
 * against the XML it has just written and attaches nothing where it does not hold, and
 * {@code esj validate} checks it again over a file somebody else wrote. One function, so
 * that a producer and a consumer of the same file cannot be applying two rules.
 *
 * <p>The rule, over the two documents:
 *
 * <ol>
 *   <li>both name the same semantic model;</li>
 *   <li>the ESJ document holds together under the registry of that model, which is layer
 *       L2 of the specification: every business term it states is a term of that edition,
 *       and every value satisfies the datatype, the supplementary components and the
 *       group chain the registry records for it;</li>
 *   <li>every value the invoice XML states stands in the ESJ document unchanged — same
 *       path, same content, same supplementary components;</li>
 *   <li>every value the ESJ document states and the invoice does not stands at a path at
 *       least one of whose terms the binding table of that syntax does not bind.</li>
 * </ol>
 *
 * <p>The first condition is not a formality. The edition names the registry a consumer
 * reads the document under, and two editions do not agree about what a value means — the
 * scale an amount is bounded by, which components are mandatory, which path a term stands
 * at. An ESJ document of one edition beside an invoice of another is two invoices.
 *
 * <p>The second condition is what keeps the first from being a claim about a header. A
 * document may name this edition and state a term of another one, or a term of no edition
 * at all, and the value comparison would let it through: such a term is a term the binding
 * table does not bind, so the fourth condition — the room the attachment needs for model
 * extensions — would swallow it. Measuring the document against the registry it names
 * closes that door, and it is the same measurement {@code esj validate} makes of the
 * attachment handed over as a file of its own. Only errors count; a path only an extension
 * registry defines is reported by that layer as not checked rather than as wrong, so an
 * extension term stays what the fourth condition says it is.
 *
 * <p>The fourth condition is what makes the attachment worth carrying: a term of a model
 * extension has no place in the XML, so an ESJ document that carries it is not thereby
 * disagreeing with the invoice. A term the syntax does bind is another matter — the XML
 * would have carried it, so a document that has it and an XML that does not are two
 * different invoices, whichever way round the difference came about.
 *
 * <p>The {@code extensions} object of a document takes no part: it holds data of no
 * business term at all, so there is no term for the table to bind or not to bind and
 * nothing for the XML to have stated.
 */
public final class EsjAgreement {

    /** How many differing paths a message names before it says how many are left. */
    private static final int NAMED_PATHS = 5;

    private EsjAgreement() {
        throw new AssertionError("no instances");
    }

    /** Which condition of the rule a file broke. */
    public enum Ground {

        /** The two documents name different semantic models. */
        MODEL,

        /**
         * The ESJ document does not hold together under the registry of the model it
         * names, so nothing it states has been established to mean anything.
         */
        UNSOUND,

        /** The values of the two documents are not the values of one invoice. */
        VALUES
    }

    /**
     * Why an ESJ document and an invoice XML are not two accounts of one invoice.
     *
     * @param ground  which condition of the rule was broken
     * @param message what is wrong, as one line fit to be shown to a person
     */
    public record Disagreement(Ground ground, String message) {

        /**
         * Checks that no member is {@code null}.
         *
         * @param ground  which condition of the rule was broken
         * @param message what is wrong, as one line fit to be shown to a person
         * @throws NullPointerException if a member is {@code null}
         */
        public Disagreement {
            Objects.requireNonNull(ground, "ground");
            Objects.requireNonNull(message, "message");
        }

        /**
         * Returns the message, which is what a report shows.
         *
         * @return the one-line description
         */
        @Override
        public String toString() {
            return message;
        }
    }

    /** Which half of the value comparison a difference broke. */
    public enum Kind {

        /** The XML states a value the ESJ document does not carry at all. */
        ABSENT,

        /** Both state the path and they state different things. */
        DIFFERENT,

        /**
         * The ESJ document states a value of a term the syntax binds, and the XML does
         * not state it.
         */
        UNSTATED
    }

    /**
     * One place where the two documents are not two accounts of one invoice.
     *
     * @param path the semantic path the difference is at
     * @param kind which half of the rule it broke
     */
    public record Difference(SemanticPath path, Kind kind) {

        /**
         * Checks that no member is {@code null}.
         *
         * @param path the semantic path the difference is at
         * @param kind which half of the rule it broke
         * @throws NullPointerException if a member is {@code null}
         */
        public Difference {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(kind, "kind");
        }

        /**
         * Returns the difference as one line: the path and what is wrong with it.
         *
         * @return a one-line description
         */
        @Override
        public String toString() {
            return path + switch (kind) {
                case ABSENT -> " (in the invoice, absent from the ESJ document)";
                case DIFFERENT -> " (stated differently)";
                case UNSTATED -> " (in the ESJ document, not in the invoice)";
            };
        }
    }

    /**
     * Returns why an ESJ document and an invoice XML are not two accounts of one invoice.
     *
     * <p>This is the whole rule and the one entry point to it: the embedding asks it of
     * the XML it has just written, {@code esj validate} asks it of a file somebody else
     * wrote, and neither can end up applying half of it.
     *
     * @param esj    the ESJ document
     * @param xml    the document the invoice XML states, read back with the reader of the
     *               binding tables
     * @param syntax the syntax the XML is written in, whose binding table decides which
     *               terms it could have carried
     * @return which condition was broken and what is wrong, as one line naming the model,
     *         the model findings or the first differing paths, or an empty optional where
     *         the two agree
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Optional<Disagreement> disagreement(SemanticDocument esj,
                                                      SemanticDocument xml,
                                                      BindingSyntax syntax) {
        Objects.requireNonNull(esj, "esj");
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(syntax, "syntax");
        if (!esj.semanticModel().equals(xml.semanticModel())) {
            return Optional.of(new Disagreement(Ground.MODEL,
                    "the ESJ document names the semantic model " + esj.semanticModel()
                            + " and the invoice names " + xml.semanticModel()
                            + ", so the two are read under two registries and the same"
                            + " values do not mean the same thing"));
        }
        Optional<Disagreement> unsound = unsound(esj);
        if (unsound.isPresent()) {
            return unsound;
        }
        List<Difference> differences = differences(esj, xml, syntax);
        return differences.isEmpty() ? Optional.empty()
                : Optional.of(new Disagreement(Ground.VALUES, describe(differences)));
    }

    /**
     * Returns why the ESJ document is no document of the model it names, where it is not.
     *
     * <p>The layer is L2 and the registry is the one the document's own
     * {@code semanticModel} selects, so the answer is the answer {@code esj validate}
     * gives the same bytes handed over as a file. Only errors of that layer count: a path
     * only an extension registry defines is reported there as not checked, and an
     * enclosure carrying extension terms is the case this whole convention exists for.
     * Where this build carries no registry for the model, the layer reports that and
     * measures nothing, and nothing is claimed here either.
     */
    private static Optional<Disagreement> unsound(SemanticDocument esj) {
        List<Registry> registries = Registry.editions().stream()
                .map(Registry::forEdition)
                .toList();
        List<Finding> errors = StructuralValidator
                .validate(esj, registries, EnumSet.of(ValidationLayer.L2))
                .findings().stream()
                .filter(Finding::isError)
                .toList();
        if (errors.isEmpty()) {
            return Optional.empty();
        }
        String named = errors.stream().limit(NAMED_PATHS)
                .map(Finding::toString)
                .collect(Collectors.joining(", "));
        int rest = errors.size() - Math.min(NAMED_PATHS, errors.size());
        return Optional.of(new Disagreement(Ground.UNSOUND,
                "the ESJ document does not hold together under the semantic model it"
                        + " names, " + esj.semanticModel() + ": " + named
                        + (rest == 0 ? "" : ", and " + rest + " more")));
    }

    /**
     * Returns where the values of the two documents disagree, which is conditions 3 and 4
     * of the rule; {@link #disagreement} is the whole of it.
     *
     * @param esj    the ESJ document
     * @param xml    the document the invoice XML states, read back with the reader of the
     *               binding tables
     * @param syntax the syntax the XML is written in, whose binding table decides which
     *               terms it could have carried
     * @return the differences in path order, empty where the two agree
     * @throws NullPointerException if an argument is {@code null}
     */
    public static List<Difference> differences(SemanticDocument esj,
                                               SemanticDocument xml,
                                               BindingSyntax syntax) {
        Objects.requireNonNull(esj, "esj");
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(syntax, "syntax");
        BindingTable table = BindingTable.of(syntax);
        Map<SemanticPath, SemanticValue> stated = esj.values();
        List<Difference> differences = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : xml.values().entrySet()) {
            SemanticValue carried = stated.get(entry.getKey());
            if (carried == null) {
                differences.add(new Difference(entry.getKey(), Kind.ABSENT));
            } else if (!carried.equals(entry.getValue())) {
                differences.add(new Difference(entry.getKey(), Kind.DIFFERENT));
            }
        }
        Map<SemanticPath, SemanticValue> written = xml.values();
        for (SemanticPath path : stated.keySet()) {
            if (!written.containsKey(path) && bound(table, path)) {
                differences.add(new Difference(path, Kind.UNSTATED));
            }
        }
        differences.sort((one, other) -> one.path().compareTo(other.path()));
        return List.copyOf(differences);
    }

    /**
     * Returns how many paths of the ESJ document this comparison did not check.
     *
     * <p>They are the paths condition 4 lets through: a value the invoice does not state
     * at a path the binding table of its syntax does not bind. That is what the enclosure
     * exists for — a term of a model extension has no place in the XML — and it is also
     * the whole of what a sound enclosure can carry without the XML having anything to say
     * about it. A row that reported the comparison without naming them would be claiming
     * more than the run established.
     *
     * @param esj    the ESJ document
     * @param xml    the document the invoice XML states
     * @param syntax the syntax the XML is written in
     * @return the number of paths, zero where every value of the enclosure was measured
     *         against the invoice
     * @throws NullPointerException if an argument is {@code null}
     */
    public static int unchecked(SemanticDocument esj,
                                SemanticDocument xml,
                                BindingSyntax syntax) {
        Objects.requireNonNull(esj, "esj");
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(syntax, "syntax");
        BindingTable table = BindingTable.of(syntax);
        Map<SemanticPath, SemanticValue> written = xml.values();
        int unchecked = 0;
        for (SemanticPath path : esj.values().keySet()) {
            if (!written.containsKey(path) && !bound(table, path)) {
                unchecked++;
            }
        }
        return unchecked;
    }

    /**
     * Returns the first differing paths as one line, for a message that has to name them.
     *
     * @param differences what {@link #differences} found, which is not empty
     * @return the line, naming at most five paths and counting the rest
     * @throws NullPointerException if {@code differences} is {@code null}
     */
    public static String describe(List<Difference> differences) {
        Objects.requireNonNull(differences, "differences");
        String named = differences.stream().limit(NAMED_PATHS)
                .map(Difference::toString)
                .collect(Collectors.joining(", "));
        int rest = differences.size() - Math.min(NAMED_PATHS, differences.size());
        return rest == 0 ? named : named + ", and " + rest + " more";
    }

    /**
     * Tells whether the table binds every term of a path, which is what makes a value of
     * it one the XML would have carried.
     *
     * <p>Every term, and not only the last: a value that sits inside a group the syntax
     * has no place for could not have reached the XML however well its own term is bound.
     */
    private static boolean bound(BindingTable table, SemanticPath path) {
        return path.termIds().stream().allMatch(table::isBound);
    }
}
