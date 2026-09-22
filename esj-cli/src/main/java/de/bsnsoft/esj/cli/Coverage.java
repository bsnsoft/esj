package de.bsnsoft.esj.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the complete check for one kind of input is, and how much of it this run performed.
 *
 * <p>A verdict is a claim about coverage as well as about findings. A tool that reported
 * {@code VALID} after running one component of six would be read by the next program along
 * as the answer to the question the command is named after, and that program would be
 * wrong in a way nothing in the report can correct: an exit code has no footnotes. So the
 * word {@code VALID} and the exit code {@link ExitCode#SUCCESS} are given only when every
 * component the complete check for this input consists of actually ran.
 *
 * <p>What that complete check is, is decided here and never by the caller. The rows are
 * per input kind and are the same table as the one in {@code docs/validation.md}:
 *
 * <ul>
 *   <li><b>an XML input</b> — also the XML taken out of a PDF — needs the official
 *       artefacts of its profile ({@link #SYNTAX_BINDING}: the front door, the XML Schema
 *       of its syntax and the EN 16931 Schematron), every rule set the specification it
 *       names asks for ({@link #PROFILE_RULES}), and the structural layers
 *       {@link #MODEL_L2} and {@link #CARDINALITY_L3} of the document the importer built.
 *       Layer L1 is not among them: the bytes were XML, not ESJ, so there is nothing for
 *       it to decide.</li>
 *   <li><b>an ESJ input</b> needs {@link #FORMAT_L1}, {@link #MODEL_L2},
 *       {@link #CARDINALITY_L3}, the business rules of EN 16931 ({@link #BUSINESS_RULES}),
 *       which the native engine runs over the semantic document, and the official
 *       artefacts of its profile over the XML the document is written to
 *       ({@link #WRITTEN_SYNTAX}). No artefact will look at a document that was never XML,
 *       so the document is written into a syntax binding first and the artefacts read
 *       that; {@link WrittenCheck} says when the two are the same document and when they
 *       are not. {@code --rules none} and {@code --no-syntax} each take one of the two
 *       rows out, and the row is then a gap like any other a caller asked for.</li>
 *   <li><b>a PDF input</b> needs the container checks of this tool, which always run once
 *       the file was opened, and the rows of the XML inside it. The PDF/A conformance of
 *       the file is outside the verdict by definition — this tool reports what the file
 *       declares and validates no PDF/A — and every report says so. That is a documented
 *       scope limit and not a row that failed to run.</li>
 * </ul>
 *
 * <p>The native rules are not a required row for an XML input. What defines {@code VALID}
 * there is the official artefacts of the document's profile, which check the same rules
 * over the form the document arrived in; a second engine over the imported document adds
 * evidence rather than coverage, and a run whose artefacts all applied is complete without
 * it. A fatal finding of the native engine is {@code INVALID} for either kind of input —
 * coverage decides what {@code VALID} needs, never what a defect counts for.
 *
 * @param rows the required components of the complete check for this input, in report
 *             order, each either satisfied or carrying the reason it was not
 */
record Coverage(List<Row> rows) {

    /** The bytes are an ESJ document: layer L1 of the specification, section 9.1. */
    static final String FORMAT_L1 = Validation.NOT_CHECKED_L1;

    /** Every path and value is one the registry knows: layer L2, section 9.2. */
    static final String MODEL_L2 = Validation.NOT_CHECKED_L2;

    /** The cardinalities of the registry hold: layer L3, section 9.3. */
    static final String CARDINALITY_L3 = Validation.NOT_CHECKED_L3;

    /** The business rules of EN 16931-1, clause 6.4 over the semantic document. */
    static final String BUSINESS_RULES = Validation.NOT_CHECKED_BUSINESS_RULES;

    /** The official artefacts of the document's profile over the XML it arrived as. */
    static final String SYNTAX_BINDING = Validation.NOT_CHECKED_SYNTAX_BINDING;

    /** The official artefacts of the profile over the XML an ESJ document was written to. */
    static final String WRITTEN_SYNTAX = Validation.NOT_CHECKED_WRITTEN_SYNTAX;

    /** Every rule set of the pack that the specification the document names asks for. */
    static final String PROFILE_RULES = Validation.NOT_CHECKED_PROFILE_RULES;

    /**
     * Copies the rows.
     *
     * @param rows the required components of the complete check for this input, in report
     *             order, each either satisfied or carrying the reason it was not
     */
    Coverage {
        rows = List.copyOf(rows);
    }

    /**
     * Works out what the complete check for an input was and how much of it ran.
     *
     * @param report what both engines found
     * @return the coverage of the run
     */
    static Coverage of(Validation.Report report) {
        List<Row> rows = new ArrayList<>();
        if (report.syntax() == InputSyntax.ESJ) {
            rows.add(Row.of(WRITTEN_SYNTAX, writtenSyntaxCause(report)));
            rows.add(Row.of(FORMAT_L1, formatLayerCause(report)));
            rows.add(layer(MODEL_L2, report));
            rows.add(layer(CARDINALITY_L3, report));
            rows.add(Row.of(BUSINESS_RULES, businessRulesCause(report)));
        } else {
            SyntaxCheck check = report.syntaxCheck();
            rows.add(Row.of(SYNTAX_BINDING, check.checked() ? null : cause(check)));
            if (check.checked()) {
                rows.add(Row.of(PROFILE_RULES,
                        check.profileRulesMissing() ? Cause.NO_RULES_FOR_PROFILE : null));
            }
            rows.add(layer(MODEL_L2, report));
            rows.add(layer(CARDINALITY_L3, report));
        }
        return new Coverage(rows);
    }

    /**
     * Returns why the official artefacts did not cover an ESJ input, where they did not.
     *
     * <p>A limit comes first, as it does for every other row: a reader that stopped wrote
     * out part of a document, and an artefact over that part judges a fragment. Otherwise
     * the reason is the one {@link WrittenCheck} already carries — the caller asked for
     * less, the writer had no place in the syntax for something the document states, or
     * the pack holds no artefact for the profile the document names.
     */
    private static Cause writtenSyntaxCause(Validation.Report report) {
        if (report.limit().isPresent()) {
            return Cause.LIMIT_REACHED;
        }
        WrittenCheck written = report.written();
        return written.checked() ? null
                : written.cause().orElse(Cause.NOT_RUN_BY_THIS_COMMAND);
    }

    /**
     * Returns why the business rules did not cover an ESJ input, where they did not.
     *
     * <p>A caller that passed {@code --rules none} asked for less and gets the third state
     * and that reason. The other reasons the engine gives for standing down — no document,
     * or a structural layer that rejected one — accompany a fatal finding, so the verdict
     * is {@code INVALID} and this cause is never read.
     */
    private static Cause businessRulesCause(Validation.Report report) {
        if (report.limit().isPresent()) {
            return Cause.LIMIT_REACHED;
        }
        if (report.ruleCheck().checked()) {
            return null;
        }
        Optional<Cause> model = report.l2().notEvaluatedCause();
        if (model.isPresent()) {
            return model.orElseThrow();
        }
        String reason = report.ruleCheck().reason().orElse("");
        if (RuleCheck.NO_PACK_FOR_EDITION.equals(reason)) {
            return Cause.NO_PACK_FOR_EDITION;
        }
        return RuleCheck.BY_OPTION.equals(reason)
                ? Cause.SKIPPED_BY_CALLER
                : Cause.NOT_RUN_BY_THIS_COMMAND;
    }

    /**
     * Returns why layer L1 did not cover the bytes of an ESJ input, where it did not.
     *
     * <p>A limit is the case a run meets most often: the reader stopped where it was
     * configured to stop, so the layer ran but judged only what it had read by then, and
     * the row is a gap like any other. A command that does not run the layer at all is
     * the other.
     */
    private static Cause formatLayerCause(Validation.Report report) {
        if (report.limit().isPresent()) {
            return Cause.LIMIT_REACHED;
        }
        return report.l1().checked() ? null : Cause.NOT_RUN_BY_THIS_COMMAND;
    }

    /**
     * Returns the row of one structural layer.
     *
     * <p>A layer that ran and reported that it could not measure something did not cover
     * what it was asked to cover, which is why the finding codes of the specification,
     * section 9.5 that record something not evaluated are read here rather than counted as
     * findings: an extension path nobody held the registry for, and a document of an
     * edition this build has no registry for, are gaps and not defects.
     *
     * <p>A limit comes before either: a run the reader stopped read part of the document,
     * and neither model layer measured what it did not see. The cause is then the limit
     * for every one of them, and never the caller, who asked for the whole check.
     *
     * <p>Both of the causes below are reported at the model layer and both leave the
     * cardinality layer short as well: an unmeasured path is a path the cardinality layer
     * cannot count, and an unknown edition leaves it with no cardinalities at all. So the cardinality row carries
     * the model layer's cause where there is one. Where there is none, a cardinality layer
     * that did not run did not run because the caller asked for less.
     */
    private static Row layer(String component, Validation.Report report) {
        if (report.limit().isPresent()) {
            return Row.of(component, Cause.LIMIT_REACHED);
        }
        Optional<Cause> model = report.l2().notEvaluatedCause();
        if (MODEL_L2.equals(component)) {
            return Row.of(component,
                    report.l2().checked() ? model.orElse(null) : Cause.SKIPPED_BY_CALLER);
        }
        if (report.l3().checked()) {
            return Row.of(component, model.orElse(null));
        }
        return Row.of(component, model.filter(cause -> cause == Cause.EDITION_UNKNOWN)
                .orElse(Cause.SKIPPED_BY_CALLER));
    }

    /** Returns the cause behind a syntax check that did not run. */
    private static Cause cause(SyntaxCheck check) {
        return SyntaxCheck.BY_OPTION.equals(check.reason().orElse(""))
                ? Cause.SKIPPED_BY_CALLER
                : Cause.NOT_RUN_BY_THIS_COMMAND;
    }

    /**
     * Tells whether every required component ran.
     *
     * @return {@code true} if nothing was left out
     */
    boolean complete() {
        return rows.stream().allMatch(row -> row.cause().isEmpty());
    }

    /**
     * Returns the required components that did not run, in report order.
     *
     * @return the rows with a cause, possibly empty
     */
    List<Row> gaps() {
        return rows.stream().filter(row -> row.cause().isPresent()).toList();
    }

    /**
     * Returns the gaps as one clause for the last line of a text report.
     *
     * @return {@code "model-l2 (edition-unknown), …"}, empty where there is no gap
     */
    String describe() {
        StringBuilder text = new StringBuilder();
        for (Row row : gaps()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(row.component()).append(" (")
                    .append(row.cause().orElseThrow().token()).append(')');
        }
        return text.toString();
    }

    /**
     * One required component of the complete check.
     *
     * @param component the stable token of the component, one of the constants of
     *                  {@link Coverage}
     * @param cause     why it did not run, empty where it ran
     */
    record Row(String component, Optional<Cause> cause) {

        /**
         * Refuses a row without a component.
         *
         * @param component the stable token of the component, one of the constants of
         *                  {@link Coverage}
         * @param cause     why it did not run, empty where it ran
         */
        Row {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(cause, "cause");
        }

        /** Returns a row, satisfied where {@code cause} is {@code null}. */
        static Row of(String component, Cause cause) {
            return new Row(component, Optional.ofNullable(cause));
        }
    }

    /**
     * Why a required component did not run.
     *
     * <p>The vocabulary is closed and documented, so that a pipeline can branch on it
     * without reading English. A cause is never a statement that the document is wrong: a
     * run that ends here found nothing fatal, and every one of these says something about
     * this build, this command line or the rules that exist, not about the invoice.
     */
    enum Cause {

        /** The caller left the component out: {@code --no-syntax}, or {@code --level l2}. */
        SKIPPED_BY_CALLER("skipped-by-caller"),

        /**
         * A limit of this run stopped the reader before the component could cover the
         * document (specification, section 12.2). It is the one cause that ends the
         * command with {@link ExitCode#LIMIT} rather than with
         * {@link ExitCode#INDETERMINATE}, because the answer is not "supply the missing
         * component" but "read the document again against a larger bound".
         */
        LIMIT_REACHED("limit-reached"),

        /**
         * This command does not run that component at all, whatever the command line says.
         * {@code esj inspect} names the pack it would run and runs none of it, and the
         * informational second pass of {@code --after-repair} judges bytes this run made
         * rather than the bytes that were handed over.
         */
        NOT_RUN_BY_THIS_COMMAND("not-run-by-this-command"),

        /**
         * The document names a specification the pack carries no rules for, so a rule set
         * it asked to be judged by did not run. A document that names EN 16931 and no core
         * invoice usage specification is not this case.
         */
        NO_RULES_FOR_PROFILE("no-rules-for-profile"),

        /**
         * The component is not part of this build. No required row carries it today; it is
         * the token a component that arrives with a later version uses while it is
         * announced and not yet built, and it is in the vocabulary so that a caller can
         * branch on it before that happens.
         */
        NOT_IN_THIS_VERSION("not-in-this-version"),

        /**
         * The document carries a path an extension registry defines, and no such registry
         * was loaded, so that path was not measured against anything;
         * {@code --extension xrechnung} loads the one this build carries.
         */
        EXTENSION_REGISTRY_MISSING("extension-registry-missing"),

        /**
         * The writer had no place in the target syntax for something the document states,
         * so the XML the official artefacts would have judged is not the document that was
         * handed over. It is a property of the syntax binding and not a defect of the
         * invoice, and {@code --via} chooses the other syntax, which may carry it.
         */
        TERM_NOT_IN_SYNTAX("term-not-in-syntax"),

        /**
         * The schema of the target syntax requires an element or an attribute that no
         * business term of the semantic model carries and the binding table of that syntax
         * states no value for, so the writer had to leave it out or write it empty and the
         * written XML is a rendition the schema of that syntax does not accept. What an
         * artefact says about such an element is a statement about the rendition and not
         * about the invoice, so the artefacts stand down rather than answer for something
         * the writer supplied. {@code --via} chooses the other syntax, which may ask for
         * less. Where the binding table does state what such an element is written with,
         * the writer writes it and this row runs; no document of the conformance corpus
         * and no example reaches this cause in this release.
         */
        ELEMENT_NOT_IN_MODEL("element-not-in-model"),

        /**
         * The schema of the target syntax requires an element whose content a business
         * term of the semantic model does carry, and this document does not state that
         * term, so the writer left the element out and the written XML is a rendition that
         * schema does not accept. The total value added tax amount BT-110 is the case of
         * this release: an ESJ document may leave it out, UBL requires it of every tax
         * total, and a business rule of the standard faults it as well — which the native
         * rules of this run have already said. The artefacts stand down rather than repeat
         * that as a finding about an element the writer could not write.
         */
        TERM_NOT_STATED("term-not-stated"),

        /**
         * No registry is available for the edition the document names, so the model layers
         * had nothing to measure it against. It is a property of this build and not of the
         * document (specification, sections 4.4 and 9.2).
         */
        EDITION_UNKNOWN("edition-unknown"),

        /**
         * This build carries a registry for the edition the document names and no
         * artefact written against it, so the structural layers measured the document and
         * a component that is written for one edition did not run over it. A rule pack is
         * written against one edition — the rules are renumbered, added to and withdrawn
         * between them — and running another edition's pack would report about terms this
         * document does not have. A binding table is written against one edition for the
         * same reason, so an ESJ document of an edition no table binds reaches no written
         * form for the official artefacts to read either.
         */
        NO_PACK_FOR_EDITION("no-pack-for-edition"),

        /**
         * The XML an ESJ document was written to is larger than a bound of this run
         * allows — the bytes the writer may produce, or the bytes the syntax engine
         * accepts — so the official artefacts had nothing to read. It is a bound on the
         * written document and not on the document that was handed over — a cross industry
         * invoice is about four times the size of the ESJ it comes from — and every other
         * component of the check ran, so the run reaches a report rather than
         * {@link ExitCode#LIMIT}. {@code --max-output-bytes} and {@code --max-input-bytes}
         * raise the two bounds and {@code --via} chooses the syntax that writes the
         * smaller document. A limit of the clock is not this cause: a run that ran out of
         * time reached no verdict at all and keeps {@link ExitCode#LIMIT}.
         */
        WRITTEN_OVER_BOUND("written-over-bound");

        private final String token;

        Cause(String token) {
            this.token = token;
        }

        /**
         * Returns the token a report writes.
         *
         * @return the token, for example {@code edition-unknown}
         */
        String token() {
            return token;
        }
    }
}
