package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.bindings.WriteReport;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the official artefacts had to say about an ESJ document once it had been written
 * out as XML, or why they were not asked.
 *
 * <p>An ESJ document never was XML, so no artefact of a validation pack will look at it as
 * it stands: the schema modules of a syntax and the Schematron of EN 16931 and of a core
 * invoice usage specification are written over element trees. Until this row existed, an
 * ESJ input was therefore checked by the native engine alone and could not reach
 * {@code VALID} at all, because a component of the complete check had no way to run.
 *
 * <p>It runs here by writing the document into a syntax binding first — a cross industry
 * invoice by default, an OASIS UBL document with {@code --via ubl} — and handing the
 * result to the same engine, the same pack and the same artefacts that judge an XML input.
 * Nothing is written to disk and nothing of the written XML reaches the report: the
 * document under test is the ESJ document, and the XML is the form the artefacts need in
 * order to read it.
 *
 * <p>That only holds while the two say the same thing. A syntax has no place for
 * everything the semantic model admits, and where the writer had to leave something out —
 * a term the binding does not carry, a group the syntax admits fewer times than the
 * document uses it, a character XML cannot hold — the XML is a different document from
 * the one that was handed over, and a verdict over it would be a verdict about something
 * else. The row is then not applicable, its cause is
 * {@link Coverage.Cause#TERM_NOT_IN_SYNTAX}, and the run ends {@code INDETERMINATE} with
 * that reason rather than passing off one document's verdict as another's.
 *
 * <p>One thing left behind is not of that kind. An extension registry may declare
 * {@code "transport": "none"}, which says that its terms belong to no transport syntax by
 * design: the invoice travels in the core terms and these record something about it that
 * UBL and CII have nothing to say about (specification, section 10). The XML written from
 * such a document is the whole invoice, so the artefacts judge the invoice, the row counts
 * as run, and {@link ByDesign} names the terms that stayed behind beside it.
 * {@link Transport} is where the two are told apart.
 *
 * <p>An element the syntax requires and no business term of the document states is the
 * other half of the same thing, and it is withheld for the same reason. Nothing of the
 * document was lost by it — the syntax asked for something the semantic model does not
 * carry, so the writer left the element out or wrote it empty and named it — but the
 * result is an XML document its own schema refuses, and the finding an artefact then
 * makes points at an element the writer supplied rather than at anything the invoice
 * states. The row is not applicable and the document keeps the answers the reader, the
 * structural layers and the native rules gave it. Two causes say which shape it was:
 * {@link Coverage.Cause#TERM_NOT_STATED} where a business term of the model carries that
 * element and this document does not state it, and
 * {@link Coverage.Cause#ELEMENT_NOT_IN_MODEL} where no term names it at all.
 *
 * <p>An element the binding table states a value for is neither. The writer writes the
 * value, says so in its report, and the document is complete: the artefacts run over it
 * and answer for the invoice. {@code conformance/writers/ubl-roundtrip.md} names the three
 * of this release.
 *
 * @param target   the syntax the document was written in, for the report
 * @param check    what the artefacts found, empty where they did not run
 * @param cause    why they did not run, empty where they did
 * @param reason   the same in English, for a person reading the report
 * @param byDesign the terms the writer left behind that a registry declares untransported,
 *                 one entry per declaring registry, empty where there were none
 */
record WrittenCheck(String target,
                    Optional<SyntaxCheck> check,
                    Optional<Coverage.Cause> cause,
                    Optional<String> reason,
                    List<ByDesign> byDesign) {

    /** The input arrived as XML, so the artefacts ran over the bytes themselves. */
    static final String NOT_ESJ = "not applicable (the input is XML)";

    /** The caller asked for the structural layers alone. */
    static final String BY_OPTION = "skipped (--no-syntax)";

    /** No document was built, so there was nothing to write. */
    static final String NO_DOCUMENT = "not run (no document was built)";

    /**
     * The model layer measured nothing, so nothing says where the values of this document
     * belong in a syntax. Nothing about the document was found wrong.
     */
    static final String NOT_MEASURED = "not run (the model layer measured nothing)";

    /** A structural layer rejected the document, so writing it would render a defect. */
    static final String AFTER_MODEL =
            "not run (a structural layer rejected the document)";

    /** {@code esj inspect} summarizes a document rather than validating it. */
    static final String NOT_INSPECTED =
            "not run (esj validate runs the official artefacts over the written document)";

    /**
     * Creates a check, refusing the two states that are not states.
     *
     * @param target   the syntax the document was written in, for the report
     * @param check    what the artefacts found, empty where they did not run
     * @param cause    why they did not run, empty where they did
     * @param reason   the same in English, for a person reading the report
     * @param byDesign the terms the writer left behind that a registry declares
     *                 untransported, one entry per declaring registry
     * @throws IllegalArgumentException if a report and a reason are both present or both
     *                                  absent, or a cause stands without its words
     * @throws NullPointerException     if a part is {@code null}
     */
    WrittenCheck {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(reason, "reason");
        byDesign = List.copyOf(byDesign);
        if (check.isPresent() == cause.isPresent()) {
            throw new IllegalArgumentException(
                    "a written check has either a report or a reason it has none");
        }
        if (cause.isPresent() != reason.isPresent()) {
            throw new IllegalArgumentException("a cause is reported in words as well");
        }
    }

    /**
     * Returns the check of a run that wrote the document and ran the artefacts over it.
     *
     * @param target what the document was written as
     * @param check  what the artefacts found
     * @return the check
     */
    static WrittenCheck ran(String target, SyntaxCheck check) {
        return ran(target, check, List.of());
    }

    /**
     * Returns the check of a run that wrote the document, left behind only terms a
     * registry declares untransported, and ran the artefacts over the result.
     *
     * @param target   what the document was written as
     * @param check    what the artefacts found
     * @param byDesign the terms that stayed in the ESJ document, per declaring registry
     * @return the check
     */
    static WrittenCheck ran(String target, SyntaxCheck check, List<ByDesign> byDesign) {
        return new WrittenCheck(target, Optional.of(check), Optional.empty(),
                Optional.empty(), byDesign);
    }

    /**
     * Returns the check of a run that did not get that far.
     *
     * @param target what the document would have been written as
     * @param cause  the closed-vocabulary cause for a program
     * @param reason the same in English
     * @return the check
     */
    static WrittenCheck notRun(String target, Coverage.Cause cause, String reason) {
        return new WrittenCheck(target, Optional.empty(), Optional.of(cause),
                Optional.of(reason), List.of());
    }

    /**
     * What one registry that declares its terms untransported left in the ESJ document.
     *
     * <p>It is the honest half of a row that ran: the artefacts judged the written XML,
     * and these terms were never in it, because the registry that defines them says they
     * belong to no transport syntax. A report names them so that nobody has to infer from
     * a passing verdict that the XML carries everything the document does.
     *
     * @param registry the edition of the registry that made the declaration, as it names
     *                 itself
     * @param terms    the identifiers of its terms the document uses, without repetition,
     *                 in the canonical path order of the values they stood at
     */
    record ByDesign(String registry, List<String> terms) {

        /**
         * Copies the terms.
         *
         * @param registry the edition of the registry that made the declaration
         * @param terms    the identifiers of its terms the document uses
         * @throws NullPointerException if a part is {@code null}
         */
        ByDesign {
            Objects.requireNonNull(registry, "registry");
            terms = List.copyOf(terms);
        }
    }

    /**
     * Returns what a report calls the syntax a document was written in.
     *
     * @param syntax the syntax the writer chose
     * @return {@code CII}, {@code UBL invoice} or {@code UBL credit note}
     * @throws NullPointerException if {@code syntax} is {@code null}
     */
    static String target(BindingSyntax syntax) {
        return switch (Objects.requireNonNull(syntax, "syntax")) {
            case CII -> "CII";
            case UBL_INVOICE -> "UBL invoice";
            case UBL_CREDIT_NOTE -> "UBL credit note";
        };
    }

    /**
     * Returns the reason a writer's report gives for the row not applying.
     *
     * <p>It names the counts rather than the terms. The terms themselves are on the error
     * stream already, one line each, because {@code esj validate} reports what the
     * importer and the writer left behind wherever they leave something behind; repeating
     * them inside the verdict would say the same thing twice and push the findings off the
     * screen for the documents that use an extension, where there are hundreds.
     *
     * @param report what the writer had to leave out
     * @return the reason, ready for the report
     * @throws NullPointerException if {@code report} is {@code null}
     */
    static String incomplete(WriteReport report) {
        Objects.requireNonNull(report, "report");
        int losses = report.losses().size();
        String what = report.dropped() == 0
                ? losses + (losses == 1
                        ? " part of the document has" : " parts of the document have")
                : report.dropped() + (report.dropped() == 1
                        ? " value of the document has" : " values of the document have");
        return "not applicable (" + what + " no place in this syntax, so the written"
                + " document is not the document that was handed over)";
    }

    /**
     * Returns the reason a writer's report gives for the row not applying, where what the
     * syntax asked for is more than the semantic model carries.
     *
     * <p>It names how many elements and attributes that was rather than which, for the
     * reason {@link #incomplete} names the counts: the writer has already put one line per
     * element on the error stream.
     *
     * @param report what the writer had to leave out or write empty
     * @return the reason, ready for the report
     * @throws NullPointerException if {@code report} is {@code null}
     */
    static String unstated(WriteReport report) {
        Objects.requireNonNull(report, "report");
        int elements = report.notes(WriteNote.Kind.ELEMENT_NOT_STATED).size();
        return "not applicable (the schema of this syntax requires " + elements
                + (elements == 1 ? " element or attribute" : " elements or attributes")
                + " no business term of the semantic model carries, so the written document"
                + " is a rendition that schema does not accept)";
    }

    /**
     * Returns the reason a writer's report gives for the row not applying, where the
     * syntax requires an element a business term of the model carries and this document
     * does not state.
     *
     * <p>It names how many elements that was rather than which, for the reason
     * {@link #incomplete} names the counts.
     *
     * @param report what the writer had to leave out
     * @return the reason, ready for the report
     * @throws NullPointerException if {@code report} is {@code null}
     */
    static String notStated(WriteReport report) {
        Objects.requireNonNull(report, "report");
        int elements = report.notes(WriteNote.Kind.TERM_NOT_STATED).size();
        return "not applicable (the schema of this syntax requires " + elements
                + (elements == 1 ? " element" : " elements")
                + " whose content a business term of the semantic model carries and this"
                + " document does not state, so the written document is a rendition that"
                + " schema does not accept)";
    }

    /**
     * Returns the line a text report writes beside a row that ran with terms of one
     * registry left in the ESJ document.
     *
     * <p>It names the terms rather than their count. There are four of them in the one
     * extension of this release, a document that uses an extension uses few of its terms,
     * and the point of the line is that a reader sees which figures the written XML does
     * not carry.
     *
     * @param entry what one declaring registry left behind
     * @return the line, ready for the report
     * @throws NullPointerException if {@code entry} is {@code null}
     */
    static String stayed(ByDesign entry) {
        Objects.requireNonNull(entry, "entry");
        int count = entry.terms().size();
        return count + (count == 1 ? " term of " : " terms of ") + entry.registry()
                + (count == 1 ? " stays" : " stay") + " in the ESJ document by design: "
                + String.join(", ", entry.terms());
    }

    /**
     * Returns the name of this row, the same in every form the command reports in.
     *
     * @return {@code "official artefacts over the written CII"}
     */
    String label() {
        return "official artefacts over the written " + target;
    }

    /**
     * Tells whether the artefacts ran.
     *
     * @return whether there is a report
     */
    boolean checked() {
        return check.isPresent();
    }

    /**
     * Tells whether an artefact rejected the written document.
     *
     * <p>A fatal finding here is a finding about the ESJ document: the writer puts the
     * values of the document where the binding table says they go, so a rule that fires on
     * the result fired on what the document states. A row that did not run answers no, the
     * way a structural layer that did not run does.
     *
     * @return whether a fatal finding was made
     */
    boolean failed() {
        return check.map(SyntaxCheck::failed).orElse(false);
    }
}
