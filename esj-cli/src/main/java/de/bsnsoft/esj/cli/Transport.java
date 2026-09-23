package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.bindings.WriteReport;
import de.bsnsoft.esj.bindings.WriterOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * <p>The writer tells the two things it can leave behind apart itself, because the report
 * is where the knowledge belongs: handed the extension registries of a run, it names a
 * value of a term whose registry makes the declaration as
 * {@link WriteNote.Kind#TERM_BY_DESIGN} rather than as a loss, and counts it neither as
 * written nor as dropped. A command hands the writer the registries through
 * {@link WriterOptions.Builder#extensions}, and this class reads its answer.
 * {@link Coverage.Cause#TERM_NOT_IN_SYNTAX} is the answer where anything else was left
 * behind — a term of a registry whose terms do travel and that found no place, the
 * XRechnung extension bound for UBL alone being that case — and the official artefacts
 * would then judge a document other than the one handed over. Where nothing but terms
 * untransported by design stayed behind, the written XML says everything the syntax has a
 * place for, the artefacts judge the invoice, and the row counts as run with those terms
 * named beside it.
 *
 * <p>The registries handed over are the extension registries this run loaded and never
 * the combination: a declaration belongs to the file that defines the terms, and a run that
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
     * <p>The answer is present exactly where the report is faithful: no value was dropped
     * and no note says that something the document states did not travel. A value of a
     * term untransported by design is neither, so a document that left behind those alone
     * is answered with the terms, per declaring registry.
     *
     * @param report what the writer had to leave out
     * @return one entry per declaring registry, in the order the report met them, empty
     *         where the document lost nothing at all, and {@link Optional#empty()} where
     *         something else was left behind
     * @throws NullPointerException if {@code report} is {@code null}
     */
    static Optional<List<WrittenCheck.ByDesign>> byDesign(WriteReport report) {
        Objects.requireNonNull(report, "report");
        if (!report.isFaithful()) {
            return Optional.empty();
        }
        return Optional.of(entries(report));
    }

    /**
     * Returns the terms a report says stayed in the semantic document by design, one entry
     * per declaring registry.
     *
     * @param report what the writer had to say
     * @return the entries, empty where nothing stayed behind by design
     * @throws NullPointerException if {@code report} is {@code null}
     */
    static List<WrittenCheck.ByDesign> entries(WriteReport report) {
        Objects.requireNonNull(report, "report");
        List<WrittenCheck.ByDesign> entries = new ArrayList<>();
        report.byDesign().forEach((registry, terms) ->
                entries.add(new WrittenCheck.ByDesign(registry, terms)));
        return List.copyOf(entries);
    }
}
