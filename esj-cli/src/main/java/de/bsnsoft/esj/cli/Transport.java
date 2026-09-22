package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.bindings.WriteReport;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Which of the values a writer left behind were never meant to reach a syntax.
 *
 * <p>An extension registry may declare {@code "transport": "none"}: its terms record
 * something about the document that no transport syntax carries, and the XML written from
 * a document that uses them is the whole invoice rather than a lossy copy of it
 * (specification, section 10). The B2C extension of this release is the case. A
 * gross-priced invoice travels in UBL and in CII as the net values of the core terms with
 * the difference in BT-114, which is what the writer produces; the terms of that registry
 * say what the customer was shown, and a syntax has nothing to say about that.
 *
 * <p>So the two things a writer can leave behind are told apart here.
 * {@link Coverage.Cause#TERM_NOT_IN_SYNTAX} is the answer where a term of a registry whose
 * terms do travel found no place — the XRechnung extension bound for UBL alone is that
 * case, and the official artefacts would then judge a document other than the one handed
 * over. Where everything left behind belongs to a registry that declared it untransported,
 * the written XML says everything the syntax has a place for, the artefacts judge the
 * invoice, and the row counts as run with those terms named beside it.
 *
 * <p>The question is asked of the extension registries this run loaded and never of the
 * combination: a declaration belongs to the file that defines the terms, and a run that
 * did not load that file has no term of it to ask about, because the reader reported the
 * paths as unmeasured long before a writer saw them.
 */
final class Transport {

    private Transport() {
    }

    /**
     * Returns what the writer left behind because a registry declared it untransported,
     * or nothing at all where it left behind anything else.
     *
     * <p>Two things have to hold for the answer to be present: every note the report calls
     * a loss names a term of a registry that declared {@code "transport": "none"}, and the
     * number of values the writer counted as dropped is exactly the number of those notes.
     * The second is what keeps the answer honest if a writer ever drops a value without a
     * note of its own: a count that does not add up is not a document this run may pass
     * off as completely written.
     *
     * @param report     what the writer had to leave out
     * @param extensions the extension registries this run loaded
     * @return one entry per declaring registry, in the order the extensions are declared
     *         in, empty where the document lost nothing at all, and
     *         {@link Optional#empty()} where something else was left behind
     * @throws NullPointerException if an argument is {@code null}
     */
    static Optional<List<WrittenCheck.ByDesign>> byDesign(WriteReport report,
                                                          Extensions extensions) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(extensions, "extensions");
        List<WriteNote> losses = report.losses();
        if (report.dropped() != losses.size()) {
            return Optional.empty();
        }
        Map<String, String> declared = untransported(extensions);
        Map<String, Set<String>> byRegistry = new LinkedHashMap<>();
        for (WriteNote note : losses) {
            String registry = declared.get(term(note.path()));
            if (registry == null) {
                return Optional.empty();
            }
            byRegistry.computeIfAbsent(registry, key -> new LinkedHashSet<>())
                    .add(term(note.path()));
        }
        List<WrittenCheck.ByDesign> entries = new ArrayList<>();
        byRegistry.forEach((registry, terms) ->
                entries.add(new WrittenCheck.ByDesign(registry, List.copyOf(terms))));
        return Optional.of(List.copyOf(entries));
    }

    /**
     * Returns every term of an extension registry that declared its terms untransported,
     * mapped to the edition of the registry that declared it.
     *
     * <p>The edition string is what a report names, because it is what the registry calls
     * itself and is unique among the registries a run can load.
     */
    private static Map<String, String> untransported(Extensions extensions) {
        Map<String, String> declared = new LinkedHashMap<>();
        for (Registry registry : extensions.registries()) {
            if (!registry.withoutTransport()) {
                continue;
            }
            for (Term term : registry.terms()) {
                declared.put(term.id(), registry.edition());
            }
        }
        return declared;
    }

    /**
     * Returns the term a semantic path names: its last segment.
     *
     * <p>A note about the document as a whole carries no path, and its empty term matches
     * no registry, which is the answer that leaves the row not applicable.
     */
    private static String term(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
