package de.bsnsoft.esj.cli;

import com.fasterxml.jackson.core.JsonGenerator;
import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.bindings.WriteReport;
import de.bsnsoft.esj.pdf.AttachmentKind;
import de.bsnsoft.esj.pdf.ContainerFinding;
import de.bsnsoft.esj.pdf.EmbeddedFile;
import de.bsnsoft.esj.pdf.FacturXMetadata;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.pdf.LocatedAttachment;
import de.bsnsoft.esj.pdf.PdfaIdentification;
import de.bsnsoft.esj.syntax.ComponentRun;
import de.bsnsoft.esj.syntax.Engine;
import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.PackComponent;
import de.bsnsoft.esj.syntax.Severity;
import de.bsnsoft.esj.syntax.SkippedComponent;
import de.bsnsoft.esj.syntax.SyntaxFinding;
import de.bsnsoft.esj.syntax.SyntaxReport;
import de.bsnsoft.esj.report.ValidationOutcome;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleSeverity;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.ValidationStatus;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.xr.XrSyntax;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns a validation report into the two forms the tool writes it in: lines for a person
 * and an object for a program.
 *
 * <p>Both say the same thing, and both say what was not checked. The text form is what
 * {@code esj validate} and {@code esj inspect} print; the JSON form is what
 * {@code --output json} writes, and its shape is part of the interface of the tool.
 *
 * <h2>Two blocks, because there are two engines</h2>
 *
 * <p>The report is printed in two blocks. <b>Syntax</b> is what the official artefacts of
 * the document's profile said about the XML it arrived as: the parser, the XML Schema
 * modules of its syntax, and the compiled Schematron of EN 16931 and of the CIUS it
 * names. For an ESJ input, which arrived as no XML at all, the same artefacts read the
 * XML the document was written to, under a heading that names that binding
 * ({@link WrittenCheck}). <b>Semantic</b> is what the structural layers of the
 * specification, section 9 and the native rule engine said about the document itself.
 *
 * <h2>The same rule, reported twice</h2>
 *
 * <p>Over an XML input the official Schematron and the native rule pack both check the
 * business rules of EN 16931, and the same rule identifier can therefore appear in both
 * blocks. Neither report is merged into the other and neither is suppressed. They are two
 * measurements of two things: the artefact reads the document as it arrived, the native
 * engine reads what the import carried into the semantic model, and the case where they
 * disagree — a value the binding placed somewhere the importer does not read, a tolerance
 * one of them allows — is the case a reader most needs to see. A report that printed one
 * of them would be choosing which of two independent witnesses to believe, which is not a
 * decision a validator is entitled to make on a reader's behalf. The text form names the
 * overlap under the native findings so that nobody reads it as an error made twice; the
 * JSON form keeps {@code syntax.findings} and {@code rules.findings} apart and puts the
 * engine on every finding of both.
 *
 * <p>They are not merged, and the reason is not tidiness. A finding of a rule set is not
 * a statement about ESJ conformance — the specification, section 9.4 is normative about
 * that — and a reader who has to act on a report needs to know which of the two bodies
 * said what: a fatal finding of the EN 16931 Schematron sends its author to the standard,
 * a finding of layer L2 sends them to this project. Every finding carries the pack and
 * the component it came from for the same reason: a report has to survive the day a pack
 * is replaced by a newer release.
 *
 * <p>A layer or a component that did not run is never printed as one that passed, and
 * neither is one that ran and measured nothing. That is why a layer carries two members
 * in the JSON form rather than one: {@code ok} for an unrun layer would tell a program
 * that reads {@code layers.l1.ok} that the document was rejected, while the text form
 * beside it says "not checked" and the process leaves with 0. So {@code checked} says
 * whether the layer ran, and {@code ok} is {@code null} where it did not run and again
 * where it ran without measuring what it was asked to measure — the third answer the
 * text form writes as "no verdict". The syntax block carries the same pair.
 *
 * <p>For the same reason both forms carry what the importer dropped. Content of an XML
 * source that did not reach the semantic document is not a validation finding — the
 * document that exists is sound — but a caller who runs {@code esj validate x.xml &&
 * deploy} is entitled to learn from the report that the invoice was cut down on the way
 * in, rather than only from a stream they may not be reading.
 *
 * <p>What neither form does is claim completeness. The importer reports what it noticed,
 * so an empty note list is the absence of an observation and not the presence of a
 * guarantee; a report that turned it into "everything reached the document" would be
 * asserting something nothing measured. Where there is nothing to say, the text form says
 * nothing and the arrays of the JSON form are empty.
 *
 * <p>Neither form carries a duration. A machine report of this tool is compared, diffed
 * and checked into repositories, so it says what the document decides and never what the
 * machine was doing at the time; the cost of each artefact is on the error stream under
 * {@code --verbose}.
 */
final class Reports {

    /** How many distinct warning lines the error stream carries without {@code --verbose}. */
    private static final int NOTE_LINES = 20;

    /** The width a row label is padded to, below the two spaces that indent it. */
    private static final int LABEL_WIDTH = 27;

    /** The column the status of a row of the container block begins in. */
    private static final int ROW_WIDTH = 29;

    /**
     * The column a header line above the blocks begins its value in.
     *
     * <p>It is narrower than {@link #LABEL_WIDTH} because the two kinds of line are not
     * in one column: a header line stands at the left margin beside {@code Input:}, and a
     * row of a block is indented under its heading. Padding both to one width would leave
     * the header lines hanging two spaces to the right of the line above them.
     */
    private static final int HEADER_WIDTH = 18;

    /** How many findings of one check the text report prints without {@code --verbose}. */
    private static final int FINDING_LINES = 20;

    /** The code of a finding that says the agreement rule was not applied at all. */
    private static final String UNCHECKED = "PDF-ESJ-UNCHECKED";

    /** The heading of the block the official artefacts fill. */
    private static final String SYNTAX_HEADING = "Syntax";

    /** The heading of the block the structural layers fill. */
    private static final String SEMANTIC_HEADING = "Semantic";

    /** The row of the syntax block that the XML parser fills. */
    private static final String XML_ROW = "XML";

    /**
     * The sentence under the native findings that names the rules an official artefact
     * reported as well.
     *
     * <p>It is there so that a reader who sees a rule identifier twice in one report
     * knows why, without having to infer it. The two engines are independent and the
     * report keeps them that way.
     */
    static final String ALSO_REPORTED = "the same rules were reported above by an official"
            + " artefact over the XML; the two engines are independent and neither report"
            + " is merged into the other: ";

    /**
     * What a report says where a limit stopped the run, in place of a verdict.
     *
     * <p>It is not one of the three states and is not meant to read like one. A bound of
     * this run was met, the document was never judged, and the command leaves with
     * {@link ExitCode#LIMIT}; the answer is a larger bound, never a rejection of the
     * invoice (specification, sections 3.1 and 12.2).
     *
     * <p>The spelling is {@link ValidationOutcome#NO_VERDICT} rather than a literal of this
     * class, so that the lines this command prints and the report file a reader keeps say
     * it the same way.
     */
    static final String NO_VERDICT = ValidationOutcome.NO_VERDICT;

    private Reports() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the edition of the semantic model the document names, as an identity line
     * writes it.
     *
     * <p>Every path of the document is an address relative to it (specification, section
     * 4.4), so it decides what the layers below measured the document against and, for a
     * document of an edition no registry of this build describes, that they measured
     * nothing. The line says which of the two happened rather than leaving a reader to
     * infer it from a row further down.
     *
     * @param report what the engines found
     * @return the line, empty where no document was built
     */
    static Optional<String> semanticModel(Validation.Report report) {
        boolean unknown = report.l2().notEvaluatedCause()
                .filter(cause -> cause == Coverage.Cause.EDITION_UNKNOWN).isPresent();
        return report.semanticModel()
                .map(model -> model + (unknown ? " (no registry in this build)" : ""));
    }

    /**
     * Writes what the container had to say, before what the invoice inside it had to say.
     *
     * <p>Nothing is written for an input that was a file of XML or ESJ: there is no
     * container, and a block of five lines saying so would be noise on every run.
     *
     * @param console the streams of the process
     * @param report  what this run found
     */
    static void container(Console console, Validation.Report report) {
        Optional<Container> container = report.container();
        if (container.isEmpty()) {
            return;
        }
        Container pdf = container.orElseThrow();
        console.line(header("Detected:") + Container.LABEL);
        pdf.invoice().ifPresent(invoice -> console.line(header("Embedded invoice:")
                + Container.describe(invoice)));
        console.line(header("Profile:") + profileText(report));
        notApplicable(console, report);
        console.line();
        containerChecks(console, report);
        console.line();
    }

    /**
     * Writes the checks of the container, each with what it found.
     *
     * <p>They are structural and they say so: the name tree, the associated files array,
     * the embedded file dictionary and the XMP packet are read, and conformance to the
     * PDF/A part the file declares is not validated at all.
     *
     * @param console the streams of the process
     * @param report  what this run found
     */
    static void containerChecks(Console console, Validation.Report report) {
        Optional<Container> container = report.container();
        if (container.isEmpty()) {
            return;
        }
        Container pdf = container.orElseThrow();
        console.line("Container");
        row(console, "PDF structure",
                pdf.findings(ContainerFinding.Category.PDF_STRUCTURE), null);
        console.line(row("PDF/A-3 declared")
                + pdfaText(pdf.pdfa(), report.pdfaValidation()));
        row(console, "Associated file (/AF)", pdf.findings(ContainerFinding.Category.PDF_AF),
                relationship(pdf));
        row(console, "Factur-X XMP metadata", pdf.findings(ContainerFinding.Category.PDF_XMP),
                null);
        row(console, "Embedded file params",
                pdf.findings(ContainerFinding.Category.PDF_EMBEDDED), null);
        // Only for a file that carries one: a container without an ESJ document beside
        // its invoice is the ordinary hybrid invoice and has nothing to answer for.
        pdf.esj().ifPresent(esj -> row(console, "ESJ document attached",
                pdf.findings(ContainerFinding.Category.PDF_ESJ),
                ValueText.quoted(esj.name()) + checkedAgainst(esj)));
    }

    /**
     * Returns what the row of the ESJ document says beside its name.
     *
     * <p>Where the enclosure carried paths of terms the invoice syntax binds nothing of,
     * the row says how many. Those are the paths the comparison had nothing to measure
     * them against — the room a model extension needs, which is what the enclosure exists
     * for — and a row that reported the comparison without naming them would be claiming
     * more than the run established.
     */
    private static String checkedAgainst(Container.Esj esj) {
        if (!esj.read()) {
            return "";
        }
        Integer unchecked = esj.unchecked();
        return ", checked against the invoice"
                + (unchecked == null || unchecked == 0 ? ""
                        : "; " + unchecked + " paths were not checked");
    }

    /**
     * Writes the two verdicts of a run over a container, which are two and not one.
     *
     * <p>An invoice may be valid inside a container that is wrong about it, and a
     * container may be immaculate around an invoice that is not. One exit code is all a
     * caller gets, so the report is where the two are told apart.
     *
     * <p>The container line has two words where the invoice line has four. Its checks
     * either ran over the file or did not run at all: a bound of the reader met inside the
     * container — the file itself, one attachment, a structural stream over the pre-flight
     * bound — stops the command with {@link ExitCode#LIMIT} and no report, which is the
     * third state reached the only way a container can reach it.
     *
     * @param console the streams of the process
     * @param report  what this run found
     */
    static void verdicts(Console console, Validation.Report report) {
        if (report.container().isEmpty()) {
            return;
        }
        console.line();
        console.line(header("Container:") + (report.containerFailed() ? "INVALID" : "OK"));
        console.line(header("Invoice:") + invoiceVerdict(report));
    }

    /**
     * Returns the word the invoice line carries.
     *
     * <p>There are four of them and not two, and the line follows the rule of the last
     * line of the report for its own scope. "I formed no judgement" and "this document is
     * defective" are different statements, and the two ways of forming no judgement are
     * different again. A profile that carries no invoice line is {@code NOT CHECKED}: a
     * conformant MINIMUM file booked as a bad invoice is the mistake that word exists to
     * prevent, and the exit code stays 1 because the question this tool was asked was
     * answered rather than left open. A run that found nothing fatal and did not perform
     * the whole of the check for this input is {@code INDETERMINATE}, with the components
     * that did not run named, and {@code VALID} is what is left.
     *
     * <p>Whether it is {@code INVALID} is {@link Validation.Report#invoiceFailure()} and
     * nothing else, so that this line, the {@code invoice} member of the JSON form and
     * the exit code are one answer. A fatal finding of an official artefact is one of the
     * reasons that answer can be no, and a line that weighed only the structural layers
     * would print {@code VALID} under a report whose last verdict is {@code INVALID}.
     *
     * <p>The PDF/A conformance of the container takes no part in it. This tool reports
     * what the file declares and validates no PDF/A, which the container block says in as
     * many words; it is a scope limit stated in every report and not a component that
     * failed to run.
     */
    private static String invoiceVerdict(Validation.Report report) {
        if (!report.en16931Invoice()) {
            return "NOT CHECKED (profile "
                    + report.profile().orElseThrow().conformanceLevel() + ")";
        }
        if (report.limit().isPresent()) {
            return NO_VERDICT + " (a limit of this run was reached)";
        }
        if (report.invoiceFailure().isPresent()) {
            return "INVALID";
        }
        return report.coverage().complete()
                ? "VALID"
                : "INDETERMINATE (missing from the check: "
                        + report.coverage().describe() + ")";
    }

    /**
     * Writes the sentence that says the rules of EN 16931 are not the question to ask of
     * this document, where they are not.
     *
     * @param console the streams of the process
     * @param report  what this run found
     */
    static void notApplicable(Console console, Validation.Report report) {
        if (report.en16931Invoice()) {
            return;
        }
        console.line("profile " + report.profile().orElseThrow().conformanceLevel()
                + ": not an EN 16931 invoice — EN 16931 validation not applicable");
    }

    /** Returns the profile of the document and what the container said about it. */
    private static String profileText(Validation.Report report) {
        String resolved = report.profile()
                .map(FacturXProfile::conformanceLevel)
                .orElse("not stated");
        Optional<String> declared = report.container()
                .flatMap(Container::facturX)
                .flatMap(FacturXMetadata::conformanceLevel);
        return resolved + declared
                .map(level -> " (the XMP packet declares " + ValueText.quoted(level) + ")")
                .orElse(" (the XMP packet declares none)");
    }

    /**
     * Returns the PDF/A conformance of a file: what it declares, and what a validator
     * said about that claim where the caller lent one.
     *
     * <p>Without {@code --verapdf} the row says "declared, not validated" and means it:
     * the claim is read out of the XMP packet and nothing checked it. With one, the row
     * carries the validator, its version, the profile it ran and its verdict, so that a
     * reader of the report can see whose answer it is.
     */
    private static String pdfaText(Optional<PdfaIdentification> pdfa,
                                   Optional<Verapdf.Result> validation) {
        String declared = pdfa.map(identification -> "yes, " + identification.describe())
                .orElse("no");
        return declared + validation.map(result -> " — " + result.describe())
                .orElse(pdfa.isEmpty() ? "" : " — declared, not validated");
    }

    /** Returns what the container says the invoice attachment is to the document. */
    private static String relationship(Container container) {
        return container.invoice()
                .flatMap(invoice -> invoice.attachment().file().associatedRelationship())
                .map(value -> "AFRelationship " + ValueText.quoted(value))
                .orElse(null);
    }

    /** Writes one row of the container block, with the findings that belong to it. */
    private static void row(Console console,
                            String label,
                            List<ContainerFinding> findings,
                            String detail) {
        List<ContainerFinding> spoken = findings.stream()
                .filter(finding -> finding.severity() != ContainerFinding.Severity.INFO)
                .toList();
        console.line(row(label) + status(spoken, detail));
        for (ContainerFinding finding : spoken) {
            console.line("    " + finding.code() + " [" + severity(finding) + "] "
                    + finding.message());
        }
        if (console.options().verbose()) {
            for (ContainerFinding finding : findings) {
                if (finding.severity() == ContainerFinding.Severity.INFO) {
                    console.verbose(finding.code() + ": " + finding.message());
                }
            }
        }
    }

    /** Returns the status of one row: what it found, or that it found nothing. */
    private static String status(List<ContainerFinding> spoken, String detail) {
        if (spoken.isEmpty()) {
            return detail == null ? "OK" : "OK (" + detail + ")";
        }
        long errors = spoken.stream()
                .filter(finding -> finding.severity() == ContainerFinding.Severity.ERROR)
                .count();
        return errors > 0
                ? errors + (errors == 1 ? " error" : " errors")
                : spoken.size() + (spoken.size() == 1 ? " warning" : " warnings");
    }

    /** Returns the lower-case token a report writes for the severity of a finding. */
    private static String severity(ContainerFinding finding) {
        return finding.severity().name().toLowerCase(Locale.ROOT);
    }

    /** Returns the label of a row of the container block, padded to the value column. */
    private static String row(String label) {
        StringBuilder padded = new StringBuilder("  ").append(label);
        while (padded.length() < ROW_WIDTH) {
            padded.append(' ');
        }
        return padded.toString();
    }

    /**
     * Writes the two blocks, their findings and the verdict.
     *
     * @param console the streams of the process
     * @param report  what both engines found
     */
    static void text(Console console, Validation.Report report) {
        console.line(SYNTAX_HEADING);
        bytes(console, report);
        syntax(console, report.syntaxCheck());
        written(console, report);
        console.line();
        console.line(SEMANTIC_HEADING);
        semantic(console, report);
        console.line();
        console.line(verdict(report));
    }

    /**
     * Returns the last line of a report.
     *
     * <p>Three words and not two, because a check that does not end in yes ends in one of
     * two ways — the document is wrong, and the check did not all run — and a reader who
     * takes away the last line must not have to reconstruct which. {@code VALID} is a claim
     * about coverage as well as about findings and is earned only where
     * {@link Validation.Report#coverage()} is complete; the third state names the
     * components that did not run and why, so that the sentence stands without the rows
     * above it.
     *
     * <p>The wording is the same for every command that ends in a verdict. A command that
     * runs less than {@code esj validate} runs does not get a weaker word for the same
     * answer: it gets the third state, with the shortfall spelled out.
     *
     * <p>"Missing from the check" and not "not run", because a component can fall short
     * in two ways and the line has to cover both: one never started, and one ran over a
     * document it could not measure — a cardinality layer that ran beside a path no
     * loaded registry describes counted what it could and is still a gap in the check.
     *
     * <p>A run a limit stopped carries none of the three words, because it reached none
     * of the three states: this reader stopped where it was configured to stop, which is
     * a fact about the configuration and not about the invoice (specification, sections
     * 3.1 and 12.2). The line says so in those terms and the command leaves with
     * {@link ExitCode#LIMIT}, whose single meaning is that a bound was met; the answer to
     * it is to read the document again against a larger bound. Printing
     * {@code INDETERMINATE} there would offer a word a pipeline could branch on for a run
     * that judged nothing, and the exit code already says what happened.
     *
     * @param report what ran and what it found
     * @return the line
     */
    private static String verdict(Validation.Report report) {
        if (report.limit().isPresent()) {
            return NO_VERDICT + " — a limit of this run was reached before the document"
                    + " had been read; see the message on the error stream";
        }
        ValidationStatus status = report.status();
        if (status != ValidationStatus.INDETERMINATE) {
            return status.name();
        }
        return "INDETERMINATE — nothing fatal found; missing from the check: "
                + report.coverage().describe();
    }

    /**
     * Writes what stands between the bytes and any document at all, where anything does.
     *
     * <p>It is the first row of the syntax block because it is about the bytes rather
     * than about the document: a file that is not written in the encoding it declares
     * never became one, and the rows below it are about a document that does not exist.
     * Where there is nothing to say the row is absent, so that a report of a sound file
     * does not carry a line saying that its bytes were readable.
     */
    private static void bytes(Console console, Validation.Report report) {
        List<XmlFinding> findings = report.syntaxFindings();
        if (findings.isEmpty()) {
            return;
        }
        row(console, "XML bytes",
                findings.size() + (findings.size() == 1 ? " error" : " errors"));
        for (XmlFinding finding : findings) {
            console.line("    " + finding);
        }
    }

    /**
     * Writes the syntax block: one row per check the official artefacts made, with the
     * findings of each below it.
     *
     * <p>Only the components that could apply to a document of this syntax get a row. A
     * pack carries the artefacts of every syntax it covers, so validating a UBL invoice
     * always leaves the CII schema and the two CII rule sets unused — which is not
     * information about the document but the definition of it, and three lines of it at
     * the top of every report would push the findings off the screen. {@code --verbose}
     * prints them anyway, and {@code --output json} carries every one of them, because a
     * program that reconstructs what a pack did needs the whole list.
     */
    private static void syntax(Console console, SyntaxCheck check) {
        Optional<SyntaxReport> found = check.report();
        if (found.isEmpty()) {
            console.line("  " + check.reason().orElseThrow());
            return;
        }
        SyntaxReport report = found.orElseThrow();
        List<SyntaxFinding> parser = report.findings(Engine.PARSER);
        row(console, XML_ROW, status(parser));
        findings(console, parser);
        report.pack().ifPresent(pack ->
                components(console, check, report, pack, console.options().verbose()));
        report.profileNote().ifPresent(note -> console.line("  " + note));
    }

    /**
     * Writes what the official artefacts said about the XML an ESJ document was written
     * to, under the line that says there was no XML to begin with.
     *
     * <p>It is inside the syntax block and under its own heading rather than beside the
     * rows above, because the bytes are not the same bytes: everything above is about what
     * was handed over, and this is about a rendering of it this run made so that artefacts
     * written over element trees could read a document that is not one. The heading names
     * the syntax, so that a reader who is surprised by a finding knows which binding it
     * came through, and {@code --via} chooses the other one.
     *
     * <p>An XML input gets none of this: its artefacts read the bytes themselves and a
     * second block about a rendering nobody made would be noise.
     *
     * <p>Where a registry declared its terms untransported, the terms the writer left
     * behind stand under the heading before the components. The row ran and the artefacts
     * answered for the invoice; the line is there so that nobody reads the result as a
     * statement that the XML carries everything the document does.
     */
    private static void written(Console console, Validation.Report report) {
        if (report.syntax() != InputSyntax.ESJ) {
            return;
        }
        WrittenCheck check = report.written();
        console.line("  " + check.label() + ":");
        Optional<SyntaxCheck> ran = check.check();
        if (ran.isEmpty()) {
            console.line("    " + check.reason().orElseThrow());
            return;
        }
        for (WrittenCheck.ByDesign entry : check.byDesign()) {
            console.line("    " + WrittenCheck.stayed(entry));
        }
        Optional<SyntaxReport> found = ran.orElseThrow().report();
        if (found.isEmpty()) {
            console.line("    " + ran.orElseThrow().reason().orElseThrow());
            return;
        }
        SyntaxReport syntax = found.orElseThrow();
        syntax.pack().ifPresent(pack -> components(console, ran.orElseThrow(), syntax, pack,
                console.options().verbose()));
        syntax.profileNote().ifPresent(note -> console.line("    " + note));
    }

    /** Writes one row per component of the pack, in the order the manifest lists them. */
    private static void components(Console console,
                                   SyntaxCheck check,
                                   SyntaxReport report,
                                   Pack pack,
                                   boolean all) {
        Set<String> ran = new LinkedHashSet<>();
        for (ComponentRun run : report.ran()) {
            ran.add(run.component());
        }
        Map<String, String> skipped = new LinkedHashMap<>();
        for (SkippedComponent component : report.skipped()) {
            skipped.put(component.component(), component.message());
        }
        Optional<XrSyntax> syntax = report.syntax();
        for (PackComponent component : pack.components()) {
            boolean relevant = all || syntax.isEmpty()
                    || component.appliesToSyntax(syntax.orElseThrow());
            if (!relevant) {
                continue;
            }
            String label = SyntaxPacks.label(component.name());
            if (ran.contains(component.name())) {
                List<SyntaxFinding> reported = check.findings(component.name());
                row(console, label, status(reported));
                findings(console, reported);
            } else if (skipped.containsKey(component.name())) {
                row(console, label, "skipped: " + skipped.get(component.name()));
            }
        }
    }

    /**
     * Writes the semantic block: the three structural layers, what the importer had to
     * say, and the sentence about the business rules.
     */
    private static void semantic(Console console, Validation.Report report) {
        layer(console, "ESJ format (L1)", report.l1(), covered(report, Coverage.FORMAT_L1));
        layer(console, "Model (L2)", report.l2(), covered(report, Coverage.MODEL_L2));
        layer(console, "Cardinality (L3)", report.l3(), covered(report, Coverage.CARDINALITY_L3));
        conversion(report, console.options().verbose())
                .ifPresent(line -> row(console, "Conversion", line));
        rules(console, report);
    }

    /**
     * Writes the row of the native rule engine, its findings, and the overlap with the
     * official artefacts where there is one.
     *
     * <p>The row names the pack on the line itself rather than only in the JSON, because
     * a business rule is a rule of a release: the arithmetic of EN 16931 is corrected
     * between releases of the artefacts this pack was verified against, and a report that
     * named no version would be unreadable the day the next one lands.
     */
    private static void rules(Console console, Validation.Report report) {
        RuleCheck check = report.ruleCheck();
        console.line("  " + RuleCheck.LABEL + ": " + status(check));
        listed(console, check.findings(), levelled -> {
            RuleFinding finding = levelled.finding();
            return finding.code() + " [" + level(levelled) + "]" + paths(finding) + ": "
                    + ValueText.oneLine(finding.message());
        });
        List<String> both = alsoReported(report);
        if (!both.isEmpty()) {
            console.line("    " + ALSO_REPORTED + String.join(", ", both));
        }
    }

    /**
     * Returns the rule identifiers this run reported twice, once from each engine.
     *
     * <p>The list is of identifiers rather than of findings: an artefact and a native rule
     * can report the same rule about different places of one invoice, and a sentence that
     * tried to pair them up would be claiming a correspondence nothing established.
     */
    private static List<String> alsoReported(Validation.Report report) {
        Set<String> official = new LinkedHashSet<>();
        report.syntaxCheck().report().ifPresent(found -> {
            for (SyntaxFinding finding : found.findings()) {
                official.add(finding.code());
            }
        });
        Set<String> both = new LinkedHashSet<>();
        for (RuleCheck.Levelled levelled : report.ruleCheck().findings()) {
            if (official.contains(levelled.finding().code())) {
                both.add(levelled.finding().code());
            }
        }
        return List.copyOf(both);
    }

    /**
     * Returns the levels a finding of the native pack is written with.
     *
     * <p>Where the profile the document names levels the rule itself, the line says all
     * three things: the level the verdict is made on, the level the standard gives the
     * rule, and the profile that levelled it. It is the form the syntax block writes for a
     * finding of an artefact, for the same reason — nobody reading a report should have to
     * guess which body decided what a rule costs — and the profile is quoted from the
     * document, so it is escaped like any other content of it.
     */
    private static String level(RuleCheck.Levelled levelled) {
        if (!levelled.levelled()) {
            return levelled.severity().token();
        }
        return levelled.severity().token() + ", " + levelled.standard().token()
                + " by the standard, levelled by the profile "
                + ValueText.oneLine(levelled.profile());
    }

    /**
     * Returns the paths a rule read, as the finding line shows them.
     *
     * <p>A path is content this tool wrote from a registry and not content of the
     * document, but it is written beside a message that quotes the document, so it is
     * escaped the same way rather than being the one place a reader has to wonder about.
     */
    private static String paths(RuleFinding finding) {
        return finding.paths().isEmpty() ? ""
                : " " + ValueText.oneLine(String.join(", ", finding.paths()));
    }

    /**
     * Returns what the native rule engine came to: the counts, or the reason it did not
     * run.
     *
     * <p>The three severities are counted apart for the reason the syntax block counts
     * them apart: only the first decides the verdict, and a row that added them together
     * would make a document with two remarks look like a document with two defects. The
     * third is the engine's own level for a rule it could not decide, which is never a
     * statement about the invoice.
     */
    private static String status(RuleCheck check) {
        Optional<RuleCheck.Found> found = check.found();
        if (found.isEmpty()) {
            return check.reason().orElseThrow();
        }
        StringBuilder status = new StringBuilder();
        count(status, found.orElseThrow(), RuleSeverity.FATAL, "error", "errors");
        count(status, found.orElseThrow(), RuleSeverity.WARNING, "warning", "warnings");
        count(status, found.orElseThrow(), RuleSeverity.INFO, "note", "notes");
        return status.length() == 0 ? "OK" : status.toString();
    }

    /** Appends "n things" for one severity, or nothing where there is none of it. */
    private static void count(StringBuilder status,
                              RuleCheck.Found found,
                              RuleSeverity severity,
                              String one,
                              String many) {
        long made = found.count(severity);
        if (made == 0) {
            return;
        }
        if (status.length() > 0) {
            status.append(", ");
        }
        status.append(made).append(' ').append(made == 1 ? one : many);
    }

    /**
     * Says what the importer had to say, or nothing.
     *
     * <p>Two things are counted here and neither of them is values. A note can stand for
     * a whole subtree the importer abandoned, so the number of notes is a lower bound on
     * the loss and never its size; only the importer, which walks what it skips, counts
     * what is in that subtree, and where it does the note itself says so.
     *
     * <p>The line is absent where there is nothing to say. Silence is not a claim, and a
     * claim is what this line must never make: the importer reports what it noticed, so
     * "everything reached the document" is a sentence no report can support, least of all
     * inside {@code esj validate}. Nor is the negative claim available in the second
     * case. An information-level note is one that fires on every document of its syntax,
     * which is not the same as one that cost this document nothing — the note that a term
     * has no scheme component in EN 16931 is written exactly where the scheme the source
     * carried was dropped — so the line counts those notes, names what they are about and
     * says where to read them, and asserts nothing about what did or did not survive.
     *
     * @param report  what the layers found
     * @param verbose whether the notes are already on the error stream, in which case
     *                there is no point in saying how to put them there
     */
    private static Optional<String> conversion(Validation.Report report, boolean verbose) {
        if (!report.syntax().isXml()) {
            return Optional.empty();
        }
        int dropped = report.conversion().stream()
                .filter(note -> note.kind() != ImportNote.Kind.ENCODING_REPAIRED)
                .toList()
                .size();
        if (dropped > 0) {
            return Optional.of(dropped
                    + (dropped == 1 ? " observation about" : " observations about")
                    + " content of the source that did not reach the document"
                    + " (see the standard error stream)");
        }
        int information = report.information().size();
        if (information > 0) {
            return Optional.of(information
                    + (information == 1 ? " note" : " notes")
                    + " on the distance between this syntax and EN 16931"
                    + (verbose ? " (see the standard error stream)" : " (run with --verbose)"));
        }
        return Optional.empty();
    }

    /**
     * Writes the validation report as a JSON object.
     *
     * @param console the streams of the process
     * @param report  what both engines found
     */
    static void json(Console console, Validation.Report report) {
        Json.write(console, generator -> {
            generator.writeStartObject();
            generator.writeStringField("input", report.input());
            generator.writeStringField("detected", report.syntax().token());
            writeOrNull(generator, "semanticModel", report.semanticModel());
            containerJson(generator, report);
            invoiceJson(generator, report);
            xmlJson(generator, report);
            syntaxJson(generator, "syntax", report.syntaxCheck());
            writtenJson(generator, report);
            generator.writeObjectFieldStart("layers");
            layerJson(generator, "l1", report.l1(), covered(report, Coverage.FORMAT_L1));
            layerJson(generator, "l2", report.l2(), covered(report, Coverage.MODEL_L2));
            layerJson(generator, "l3", report.l3(), covered(report, Coverage.CARDINALITY_L3));
            generator.writeEndObject();
            rulesJson(generator, report.ruleCheck());
            generator.writeArrayFieldStart("notChecked");
            for (String token : report.notChecked()) {
                generator.writeString(token);
            }
            generator.writeEndArray();
            reasonsJson(generator, report);
            notesJson(generator, "warnings", report.conversion());
            notesJson(generator, "information", report.information());
            // A run a limit stopped reached none of the three states, so the member is
            // null rather than a word a program could branch on; the exit code is 7 and
            // the reasons name the limit under every component it cut short.
            if (report.limit().isPresent()) {
                generator.writeNullField("verdict");
            } else {
                generator.writeStringField("verdict", report.status().name());
            }
            generator.writeEndObject();
        });
    }

    /**
     * Writes to the error stream what a writer had no place for.
     *
     * <p>Every command that writes a document into a syntax says this the same way, and
     * {@code esj embed} says it about the invoice inside the container it writes, because
     * that file is the archived record of the invoice and a term it lost is a term nobody
     * will notice again.
     *
     * <p>The notes are collapsed the way the importer's are: one line per distinct
     * sentence with a count, cut off after {@link #NOTE_LINES}, and shown whole under
     * {@code --verbose}. A document that uses the XRechnung extension produces hundreds of
     * them — every business term inside every sub invoice line — and a screenful of
     * near-identical text is a channel a reader stops reading.
     *
     * @param console the streams of this run
     * @param report  what the writer had to say
     */
    static void notPlaced(Console console, WriteReport report) {
        if (report.isComplete()) {
            return;
        }
        console.warning(notPlacedHeadline(report));
        Map<String, Integer> collapsed = new LinkedHashMap<>();
        for (WriteNote note : report.notes()) {
            collapsed.merge(console.options().verbose() ? note.toString()
                    : note.kind() + ": " + note.message(), 1, Integer::sum);
        }
        int printed = 0;
        for (Map.Entry<String, Integer> entry : collapsed.entrySet()) {
            if (!console.options().verbose() && printed == NOTE_LINES) {
                console.diagnostic("  ... and " + (collapsed.size() - printed)
                        + " more; run with --verbose for all of them");
                return;
            }
            console.diagnostic("  " + entry.getKey()
                    + (entry.getValue() == 1 ? "" : " (" + entry.getValue() + " times)"));
            printed++;
        }
    }

    /**
     * Returns the first line of that warning: how many values had no place in the syntax,
     * or, where every value was written, how many observations the writer made all the
     * same. Not every note is a value lost — a supplementary component, a part of
     * {@code extensions} and a character the syntax cannot carry each leave the value
     * itself in the document — and a line that counted those as values would say zero and
     * then list them.
     */
    private static String notPlacedHeadline(WriteReport report) {
        if (report.dropped() == 0) {
            return report.notes().size()
                    + (report.notes().size() == 1
                            ? " observation about what the syntax has no place for"
                            : " observations about what the syntax has no place for");
        }
        return report.dropped()
                + (report.dropped() == 1 ? " value of the document has no place in this"
                        + " syntax and was not written"
                        : " values of the document have no place in this"
                                + " syntax and were not written");
    }

    /**
     * Writes what a conversion carried and what it left behind.
     *
     * <p>The three members are written whether or not the target syntax has a writer that
     * reports anything, so that a program never has to ask whether a member is there
     * before asking what it says. A conversion to ESJ carries everything by definition —
     * the semantic document is what it was given — so it reports nothing dropped and an
     * empty list.
     *
     * @param generator the generator to write into
     * @param report    what the writer had to say, or {@code null} where no writer ran
     * @throws IOException if the generator fails
     */
    static void conversionJson(JsonGenerator generator, WriteReport report)
            throws IOException {
        generator.writeBooleanField("complete", report == null || report.isComplete());
        generator.writeNumberField("dropped", report == null ? 0 : report.dropped());
        generator.writeArrayFieldStart("notPlaced");
        if (report != null) {
            for (WriteNote note : report.notes()) {
                generator.writeStartObject();
                generator.writeStringField("kind", note.kind().name());
                generator.writeStringField("path", note.path());
                generator.writeStringField("message", note.message());
                if (!note.registry().isEmpty()) {
                    generator.writeStringField("registry", note.registry());
                }
                generator.writeEndObject();
            }
        }
        generator.writeEndArray();
    }

    /**
     * Writes the components of the complete check that did not run, each with its cause.
     *
     * <p>It is the machine-readable half of the last line of the text report, and it is
     * what makes {@code "verdict": "INDETERMINATE"} actionable: a caller that meant to run
     * a reduced check compares the causes against what it gave up, and a caller that
     * expected the whole check sees which component to supply.
     *
     * <p>It is not {@code notChecked}. That list is everything this run did not look at or
     * could not measure, including what there was nothing to look at — layer L1 over a
     * document that arrived as XML is in it, and it is no gap. Every component named here
     * is in that list too, and the list is the longer of the two. This array carries only
     * the rows of the complete check for this kind of input, and it is non-empty where the
     * verdict is
     * {@code INDETERMINATE}: a run that found something fatal stopped layers below the
     * failure, and listing those as components that did not run would send a reader after
     * a coverage problem the document does not have. The {@code cause} of each row comes
     * from a closed vocabulary that {@code docs/validation.md} lists in full.
     */
    private static void reasonsJson(JsonGenerator generator, Validation.Report report)
            throws IOException {
        generator.writeArrayFieldStart("reasons");
        if (report.status() == ValidationStatus.INDETERMINATE) {
            for (Coverage.Row row : report.coverage().gaps()) {
                generator.writeStartObject();
                generator.writeStringField("component", row.component());
                generator.writeStringField("cause", row.cause().orElseThrow().token());
                generator.writeEndObject();
            }
        }
        generator.writeEndArray();
    }

    /**
     * Writes what the native rule engine did and found.
     *
     * <p>The members are the ones the syntax block carries, and for the same reason: a
     * program never has to ask whether a member is there before asking what it says, so
     * {@code ok} is {@code null} and {@code findings} is empty where the engine did not
     * run, and {@code reason} carries the sentence the text form prints instead.
     *
     * <p>Both levels are on every finding, the way the syntax block writes them:
     * {@code severity} is the level the verdict is made on and {@code flag} the level the
     * rule declares, which is the level of the standard. They differ where the profile the
     * document names levels that rule itself.
     *
     * <p>The findings are their own array and are never mixed into {@code syntax.findings}
     * even where the two engines report the same rule identifier. Each finding names the
     * engine that produced it and the pack it belongs to, so a program that wants one
     * engine's answer filters on {@code engine}, and one that wants to compare them has
     * both.
     */
    private static void rulesJson(JsonGenerator generator, RuleCheck check)
            throws IOException {
        generator.writeObjectFieldStart("rules");
        generator.writeBooleanField("checked", check.checked());
        Optional<RuleCheck.Found> found = check.found();
        if (found.isEmpty()) {
            generator.writeNullField("ok");
            generator.writeStringField("reason", check.reason().orElseThrow());
            generator.writeNullField("pack");
            generator.writeArrayFieldStart("findings");
            generator.writeEndArray();
            generator.writeEndObject();
            return;
        }
        RuleCheck.Found ran = found.orElseThrow();
        generator.writeBooleanField("ok", !ran.hasFatal());
        generator.writeNullField("reason");
        generator.writeObjectFieldStart("pack");
        generator.writeStringField("id", ran.packId());
        generator.writeStringField("version", ran.packVersion());
        generator.writeEndObject();
        generator.writeArrayFieldStart("findings");
        for (RuleCheck.Levelled levelled : ran.findings()) {
            RuleFinding finding = levelled.finding();
            generator.writeStartObject();
            generator.writeStringField("engine", finding.engine());
            generator.writeStringField("category", finding.category().token());
            generator.writeStringField("severity", levelled.severity().token());
            generator.writeStringField("flag", levelled.standard().token());
            generator.writeStringField("code", finding.code());
            generator.writeStringField("message", finding.message());
            generator.writeArrayFieldStart("paths");
            for (String path : finding.paths()) {
                generator.writeString(path);
            }
            generator.writeEndArray();
            generator.writeStringField("packId", finding.packId());
            generator.writeStringField("packVersion", finding.packVersion());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    /**
     * Writes what the official artefacts did and found.
     *
     * <p>The members are the same whether the engine ran or not, so that a program never
     * has to ask whether a member is there before asking what it says; the ones that have
     * no answer are {@code null} and the lists are empty. Unlike the text form, the two
     * component lists are complete: a program that reconstructs which artefacts of a pack
     * were applied to an invoice, and which were not and why, needs all of them.
     *
     * <p>{@code profileNote} is a sentence for a person and {@code profileRulesSkipped} is
     * the fact behind it for a program: where it is true, a rule set of the pack was left
     * out because of the profile the document names, and {@code ok} is the answer of the
     * schema and of whatever else did run. Which rule sets those were is in {@code ran}
     * and {@code skipped}. The same fact is in {@code notChecked} as
     * {@code profile-rules}.
     *
     * <p>{@code pack.source} is where the artefacts came from — {@code bundled} for the
     * pack this build carries, whose digests and licences are recorded with the source,
     * and {@code supplied} for a directory the caller pointed {@code --pack} at. It is the
     * one thing about a pack that the manifest does not get to say, and a report that is
     * diffed or checked into a repository is read by people who did not run it.
     *
     * <p>An entry of {@code ran} carries {@code stopped}: an artefact can be applied to a
     * document and reach no result of its own, and its presence in the list is therefore
     * not a statement that its rules were checked. Where it is true, the failure is in
     * {@code findings} under {@code ARTEFACT-STOPPED} and is fatal.
     */
    private static void syntaxJson(JsonGenerator generator, String field, SyntaxCheck check)
            throws IOException {
        generator.writeObjectFieldStart(field);
        generator.writeBooleanField("checked", check.checked());
        Optional<SyntaxReport> found = check.report();
        if (found.isEmpty()) {
            generator.writeNullField("ok");
            generator.writeStringField("reason", check.reason().orElseThrow());
            generator.writeNullField("customizationId");
            generator.writeNullField("pack");
            generator.writeNullField("profileNote");
            generator.writeBooleanField("profileRulesSkipped", false);
            generator.writeArrayFieldStart("ran");
            generator.writeEndArray();
            generator.writeArrayFieldStart("skipped");
            generator.writeEndArray();
            generator.writeArrayFieldStart("findings");
            generator.writeEndArray();
            generator.writeEndObject();
            return;
        }
        SyntaxReport report = found.orElseThrow();
        generator.writeBooleanField("ok", report.fatal().isEmpty());
        generator.writeNullField("reason");
        generator.writeStringField("customizationId", report.customizationId());
        Optional<Pack> pack = report.pack();
        if (pack.isEmpty()) {
            generator.writeNullField("pack");
        } else {
            generator.writeObjectFieldStart("pack");
            generator.writeStringField("directory", pack.orElseThrow().directory());
            generator.writeStringField("id", pack.orElseThrow().id());
            generator.writeStringField("version", pack.orElseThrow().version());
            generator.writeStringField("release", pack.orElseThrow().release());
            generator.writeStringField("source", pack.orElseThrow().source().token());
            generator.writeEndObject();
        }
        if (report.profileNote().isEmpty()) {
            generator.writeNullField("profileNote");
        } else {
            generator.writeStringField("profileNote", report.profileNote().orElseThrow());
        }
        generator.writeBooleanField("profileRulesSkipped", report.profileRulesSkipped());
        generator.writeArrayFieldStart("ran");
        for (ComponentRun run : report.ran()) {
            generator.writeStartObject();
            generator.writeStringField("component", run.component());
            generator.writeStringField("engine", run.engine().token());
            generator.writeBooleanField("stopped", run.stopped());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart("skipped");
        for (SkippedComponent component : report.skipped()) {
            generator.writeStartObject();
            generator.writeStringField("component", component.component());
            generator.writeStringField("reason", component.message());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart("findings");
        for (SyntaxFinding finding : report.findings()) {
            findingJson(generator, finding);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    /**
     * Writes what the official artefacts said about the XML an ESJ document was written to.
     *
     * <p>The member is present in every report, so that a program reads one shape whatever
     * the input was, and {@code required} says whether this input needed the row at all: an
     * XML input was judged as it arrived, and for it {@code target} is {@code null} and the
     * nested object carries the reason instead of a verdict. The nested object is the same
     * shape as {@code syntax}, because it is the same engine over the same pack — what
     * differs is only which bytes it read, and {@code target} names the binding they were
     * written through.
     *
     * <p>{@code byDesign} is the array of the terms that stayed in the ESJ document
     * because the registry that defines them declares that they belong to no transport
     * syntax, one entry per declaring registry. It is written in every report, empty where
     * there were none, so that a program reads one shape.
     */
    private static void writtenJson(JsonGenerator generator, Validation.Report report)
            throws IOException {
        WrittenCheck check = report.written();
        boolean required = report.syntax() == InputSyntax.ESJ;
        generator.writeObjectFieldStart("written");
        generator.writeBooleanField("required", required);
        generator.writeBooleanField("checked", check.checked());
        if (required) {
            generator.writeStringField("target", check.target());
        } else {
            generator.writeNullField("target");
        }
        if (check.reason().isEmpty()) {
            generator.writeNullField("reason");
        } else {
            generator.writeStringField("reason", check.reason().orElseThrow());
        }
        generator.writeArrayFieldStart("byDesign");
        for (WrittenCheck.ByDesign entry : check.byDesign()) {
            generator.writeStartObject();
            generator.writeStringField("registry", entry.registry());
            generator.writeArrayFieldStart("terms");
            for (String term : entry.terms()) {
                generator.writeString(term);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
        generator.writeEndArray();
        syntaxJson(generator, "syntax", check.check().orElseGet(() ->
                SyntaxCheck.notRun(check.reason().orElse(WrittenCheck.NOT_ESJ))));
        generator.writeEndObject();
    }

    /**
     * Writes one finding of an official artefact, with every field it carries.
     *
     * <p>The message and the location are the artefact's own text, not this project's:
     * the bodies that own the rules own their wording. A line or column the artefact did
     * not report is {@code null} rather than a negative number, because a program that
     * adds one to it should not be able to point at a place in the file that does not
     * exist.
     *
     * <p>Both levels are written. {@code flag} is the one the artefact set on the rule,
     * and is what a comparison against another tool running the same artefact is made on;
     * {@code severity} is the one the profile of the document gives that rule, and is
     * what the verdict is made on. They differ only where the specification the document
     * names levels a rule itself.
     */
    private static void findingJson(JsonGenerator generator, SyntaxFinding finding)
            throws IOException {
        generator.writeStartObject();
        generator.writeStringField("engine", finding.engine().token());
        generator.writeStringField("category", finding.category().label());
        generator.writeStringField("severity", finding.severity().token());
        generator.writeStringField("flag", finding.flag().token());
        generator.writeStringField("code", finding.code());
        generator.writeStringField("message", finding.message());
        generator.writeStringField("location", finding.location());
        writeNumberOrNull(generator, "line", finding.line());
        writeNumberOrNull(generator, "column", finding.column());
        generator.writeStringField("packId", finding.packId());
        generator.writeStringField("packVersion", finding.packVersion());
        generator.writeStringField("packRelease", finding.packRelease());
        generator.writeStringField("component", finding.component());
        generator.writeEndObject();
    }

    private static void writeNumberOrNull(JsonGenerator generator, String field, int value)
            throws IOException {
        if (value < 0) {
            generator.writeNullField(field);
        } else {
            generator.writeNumberField(field, value);
        }
    }

    /**
     * Writes what the container had to say, or {@code null} where the input was no
     * container.
     *
     * <p>The field is written either way, because a program that branches on it is
     * entitled to find it: a missing member and a null one are the same question asked
     * twice.
     */
    private static void containerJson(JsonGenerator generator, Validation.Report report)
            throws IOException {
        Optional<Container> container = report.container();
        if (container.isEmpty()) {
            generator.writeNullField("container");
            return;
        }
        Container pdf = container.orElseThrow();
        generator.writeObjectFieldStart("container");
        generator.writeBooleanField("ok", !report.containerFailed());
        Optional<Container.Invoice> invoice = pdf.invoice();
        if (invoice.isPresent()) {
            generator.writeStringField("attachment", invoice.orElseThrow().name());
            // The position and not only the name: two attachments of one container may
            // carry one name and one declared size, and a report that named neither
            // could not be tied back to the document this run judged.
            generator.writeNumberField("attachmentIndex", invoice.orElseThrow().position());
            generator.writeStringField("kind",
                    invoice.orElseThrow().attachment().kind().describe());
        } else {
            generator.writeNullField("attachment");
            generator.writeNullField("attachmentIndex");
            generator.writeNullField("kind");
        }
        writeOrNull(generator, "profile",
                report.profile().map(FacturXProfile::conformanceLevel));
        generator.writeBooleanField("en16931Invoice", report.en16931Invoice());
        esjJson(generator, pdf);
        pdfaJson(generator, pdf.pdfa(), report.pdfaValidation());
        facturXJson(generator, pdf.facturX());
        generator.writeArrayFieldStart("findings");
        for (ContainerFinding finding : pdf.findings()) {
            generator.writeStartObject();
            generator.writeStringField("category", finding.category().id());
            generator.writeStringField("code", finding.code());
            generator.writeStringField("severity", severity(finding));
            generator.writeStringField("message", finding.message());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart("attachments");
        int position = 1;
        int selected = invoice.map(Container.Invoice::position).orElse(0);
        for (LocatedAttachment attachment : pdf.attachments()) {
            attachmentJson(generator, attachment, position, position == selected);
            position++;
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    /**
     * Writes what became of the ESJ document a container carries beside its invoice.
     *
     * <p>The member is written either way, because a program that branches on it is
     * entitled to find it. It has three states and not two: a file that carries no ESJ
     * document is {@code null}; a file that carries one says whether it and the invoice
     * are two accounts of one invoice; and a file that carries several is a file whose
     * producer left the choice to the reader, so none of them was checked and neither
     * {@code null} nor an answer about one of them would be true. {@code status} tells
     * the three apart and {@code agrees} is {@code null} where nothing was checked. What
     * is wrong stands among the findings, under {@code PDF-ESJ} or {@code PDF-EMBEDDED},
     * so this member stays the one question and the findings stay the one list.
     *
     * <p>{@code pathsNotChecked} says how many paths the comparison had nothing to
     * measure against, and is absent where it had something for every one of them. A
     * program that reads {@code agrees} without it would be told that the whole document
     * was held against the invoice, which is exactly what the row in the lines is careful
     * not to claim.
     */
    private static void esjJson(JsonGenerator generator, Container pdf) throws IOException {
        Optional<Container.Esj> esj = pdf.esj();
        long carried = pdf.attachments().stream()
                .filter(attachment -> attachment.kind() == AttachmentKind.ESJ_DOCUMENT)
                .count();
        if (esj.isEmpty() && carried < 2) {
            generator.writeNullField("esj");
            return;
        }
        generator.writeObjectFieldStart("esj");
        generator.writeStringField("status", esj.isPresent() ? "one" : "several");
        generator.writeNumberField("count", carried);
        writeOrNull(generator, "attachment", esj.map(Container.Esj::name));
        if (esj.isEmpty()) {
            generator.writeNullField("attachmentIndex");
            generator.writeNullField("agrees");
        } else {
            generator.writeNumberField("attachmentIndex", esj.orElseThrow().position());
            // What the comparison had nothing to measure — the paths of terms the syntax
            // of the invoice binds nothing of, which is the room an enclosure exists for.
            // The member is written only where there were such paths, so a program that
            // does not know about it reads the whole comparison where it was whole; a
            // program that reads it learns that "agrees" is an answer about the rest.
            Integer notChecked = esj.orElseThrow().unchecked();
            if (notChecked != null && notChecked > 0) {
                generator.writeNumberField("pathsNotChecked", notChecked);
            }
            // Null where the rule was not applied at all — the invoice is in a syntax this
            // version has no binding table for, or a bound was reached inside the
            // attachment. "No error was found" is not "the two agree" when nothing looked.
            boolean unchecked = pdf.findings(ContainerFinding.Category.PDF_ESJ).stream()
                    .anyMatch(finding -> UNCHECKED.equals(finding.code()));
            if (unchecked) {
                generator.writeNullField("agrees");
            } else {
                generator.writeBooleanField("agrees",
                        pdf.findings(ContainerFinding.Category.PDF_ESJ).stream()
                                .noneMatch(finding ->
                                        finding.severity() == ContainerFinding.Severity.ERROR));
            }
        }
        generator.writeEndObject();
    }

    /**
     * Writes the verdict on the invoice, which is not the verdict on the container.
     *
     * <p>The object carries the same answers the {@code Invoice:} line carries, and it
     * carries them the way the layers do: {@code checked} says whether the rules of
     * EN 16931 were the question asked of this document, and {@code ok} is {@code null}
     * where they were not. A profile that carries no invoice line is not a defective
     * invoice, and {@code ok: false} for it would tell a program running
     * {@code if not report["invoice"]["ok"]} that a conformant MINIMUM file is a bad one.
     * {@code reason} names which of the ways the answer can be no it was.
     *
     * <p>{@code ok} is {@code null} for the third state too. A run that found nothing fatal
     * and did not perform the whole of the check for this input has formed no judgement on
     * the invoice either, and the top-level {@code reasons} names the components that did
     * not run. {@code ok: true} therefore means what it says: completely checked, nothing
     * fatal found.
     */
    private static void invoiceJson(JsonGenerator generator, Validation.Report report)
            throws IOException {
        Optional<String> failure = report.invoiceFailure();
        boolean checked = report.en16931Invoice();
        boolean complete = report.coverage().complete();
        generator.writeObjectFieldStart("invoice");
        generator.writeBooleanField("checked", checked);
        if (!checked || (failure.isEmpty() && !complete)) {
            generator.writeNullField("ok");
        } else {
            generator.writeBooleanField("ok", failure.isEmpty());
        }
        writeOrNull(generator, "reason", failure);
        generator.writeEndObject();
    }

    /**
     * Writes one attachment of a container: what the file says about it, and what its
     * bytes turned out to be.
     *
     * @param generator  the generator to write into
     * @param attachment the attachment
     * @param position   where it stands in the enumeration, counted from one
     * @param selected   whether this is the attachment the verdict is about
     * @throws IOException if writing fails
     */
    static void attachmentJson(JsonGenerator generator,
                               LocatedAttachment attachment,
                               int position,
                               boolean selected) throws IOException {
        EmbeddedFile file = attachment.file();
        generator.writeStartObject();
        generator.writeNumberField("position", position);
        generator.writeBooleanField("selected", selected);
        generator.writeStringField("name", file.name());
        generator.writeStringField("kind", attachment.kind().describe());
        generator.writeBooleanField("invoice", attachment.kind().isInvoice());
        writeOrNull(generator, "root", attachment.root());
        writeOrNull(generator, "mediaType", file.declaredMediaType());
        OptionalLong declared = file.declaredSize();
        if (declared.isPresent()) {
            generator.writeNumberField("declaredSize", declared.getAsLong());
        } else {
            generator.writeNullField("declaredSize");
        }
        writeOrNull(generator, "relationship", file.associatedRelationship());
        generator.writeBooleanField("associated", file.associated());
        generator.writeEndObject();
    }

    /**
     * Writes the PDF/A conformance of the file: what it declares, and what the validator
     * of {@code --verapdf} said about that claim where one ran.
     *
     * <p>{@code validated} is the member a program branches on, and it is {@code false}
     * for every run that asked no validator — including one over a file that declares
     * nothing, where {@code pdfa} itself is null. Where a validator ran, {@code validator}
     * carries its name, its version, the profile it ran and its verdict, because a report
     * that said only "compliant" would not survive the next release of the validator.
     */
    private static void pdfaJson(JsonGenerator generator,
                                 Optional<PdfaIdentification> pdfa,
                                 Optional<Verapdf.Result> validation) throws IOException {
        if (pdfa.isEmpty()) {
            generator.writeNullField("pdfa");
            validatorJson(generator, validation);
            return;
        }
        PdfaIdentification declared = pdfa.orElseThrow();
        generator.writeObjectFieldStart("pdfa");
        generator.writeStringField("declared", declared.describe());
        generator.writeNumberField("part", declared.part());
        writeOrNull(generator, "conformance", declared.conformance());
        generator.writeBooleanField("validated", validation.isPresent());
        generator.writeEndObject();
        validatorJson(generator, validation);
    }

    /** Writes what the external PDF/A validator said, or that none was asked. */
    private static void validatorJson(JsonGenerator generator,
                                      Optional<Verapdf.Result> validation)
            throws IOException {
        if (validation.isEmpty()) {
            generator.writeNullField("pdfaValidator");
            return;
        }
        Verapdf.Result result = validation.orElseThrow();
        generator.writeObjectFieldStart("pdfaValidator");
        generator.writeStringField("name", Verapdf.Result.VALIDATOR);
        generator.writeStringField("version", result.version());
        generator.writeStringField("profile", result.profile());
        generator.writeBooleanField("compliant", result.compliant());
        generator.writeNumberField("failedRules", result.failedRules());
        generator.writeNumberField("failedChecks", result.failedChecks());
        generator.writeEndObject();
    }

    /** Writes the Factur-X properties of the XMP packet, where the file carries them. */
    private static void facturXJson(JsonGenerator generator,
                                    Optional<FacturXMetadata> metadata) throws IOException {
        if (metadata.isEmpty()) {
            generator.writeNullField("facturX");
            return;
        }
        FacturXMetadata properties = metadata.orElseThrow();
        generator.writeObjectFieldStart("facturX");
        generator.writeStringField("namespace", properties.namespace());
        writeOrNull(generator, "documentType", properties.documentType());
        writeOrNull(generator, "documentFileName", properties.documentFileName());
        writeOrNull(generator, "version", properties.version());
        writeOrNull(generator, "conformanceLevel", properties.conformanceLevel());
        generator.writeEndObject();
    }

    /**
     * Writes what stands between the bytes and any document at all, or {@code null} where
     * the input was read as ESJ and there were no bytes of XML to have an opinion about.
     */
    private static void xmlJson(JsonGenerator generator, Validation.Report report)
            throws IOException {
        if (!report.syntax().isXml()) {
            generator.writeNullField("xml");
            return;
        }
        generator.writeObjectFieldStart("xml");
        generator.writeBooleanField("ok", report.syntaxFindings().isEmpty());
        generator.writeArrayFieldStart("findings");
        for (XmlFinding finding : report.syntaxFindings()) {
            generator.writeStartObject();
            generator.writeStringField("category", finding.category());
            generator.writeStringField("code", finding.code());
            generator.writeStringField("severity", XmlFinding.SEVERITY);
            generator.writeStringField("message", finding.message());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    /** Writes a string field, or a null one where the value is absent. */
    private static void writeOrNull(JsonGenerator generator,
                                    String field,
                                    Optional<String> value) throws IOException {
        if (value.isPresent()) {
            generator.writeStringField(field, value.orElseThrow());
        } else {
            generator.writeNullField(field);
        }
    }

    /**
     * Writes one array of import notes, collapsed the way the error stream collapses
     * them: one entry per distinct note with the number of times it was made.
     *
     * <p>The two levels are two arrays rather than one array with a level member, so that
     * neither has to be filtered to be used. {@code warnings} is content of the source
     * that did not reach the document, which is what a pipeline running {@code esj
     * validate x.xml && deploy} needs to see; {@code information} is the distance between
     * the source syntax and EN 16931, which is the same for every document of that syntax
     * and says nothing about this invoice. A program that treats the second as the first
     * learns to ignore both.
     */
    private static void notesJson(JsonGenerator generator, String field, List<ImportNote> notes)
            throws IOException {
        Map<ImportNote, Integer> counts = new LinkedHashMap<>();
        for (ImportNote note : notes) {
            counts.merge(note, 1, Integer::sum);
        }
        generator.writeArrayFieldStart(field);
        for (Map.Entry<ImportNote, Integer> entry : counts.entrySet()) {
            ImportNote note = entry.getKey();
            generator.writeStartObject();
            generator.writeStringField("kind", note.kind().name());
            generator.writeStringField("location", note.location());
            generator.writeStringField("message", note.message());
            generator.writeNumberField("count", entry.getValue());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    /**
     * Tells whether one component of the complete check covered the whole document, which
     * is the same question the report's {@code reasons} array answers for that component.
     *
     * <p>A layer can run cleanly over less than the document: where a path is one only an
     * extension registry describes, the model layer says so and the cardinality layer
     * cannot count that path either, so the cardinality row is a gap of the coverage while
     * carrying no finding of its own. Reading the coverage rather than the layer's own
     * findings is what keeps {@code layers.l3.ok} and {@code notChecked} from saying two
     * different things about the same layer.
     *
     * @param report    what both engines found
     * @param component one of the component tokens of {@link Coverage}
     * @return {@code true} where the component is not among the gaps of the coverage
     */
    private static boolean covered(Validation.Report report, String component) {
        return report.coverage().gaps().stream()
                .noneMatch(row -> row.component().equals(component));
    }

    /**
     * Writes one structural layer.
     *
     * <p>{@code ok} has three answers and not two, for the reason the text form's row has
     * four words, and it answers them in the order the text row answers them: <b>an error
     * of the layer first</b>, then the coverage. A layer that found an error is
     * {@code false} whatever else the run could not measure — the defect was found, and a
     * program branching on {@code layers.l3.ok === false} must not miss a layer that
     * definitely failed while the row beside it reads {@code 1 error}. Where the layer
     * found no error, {@code ok} is {@code null} if it did not run, and {@code null} again
     * if it ran over less than the whole document — an edition no registry of this build
     * describes, a path only an extension registry defines, a limit that stopped the
     * reader — whether the layer itself carries the finding that says so or the layer
     * before it does. A layer that measured less than the document is not a layer that
     * passed, and a program reading {@code layers.l2.ok} must not be told otherwise; the
     * question is the one {@code reasons} answers, and {@link #covered} asks it there.
     *
     * <p>Every finding carries its {@code subject} beside its {@code path}: the member
     * access the reader met the problem at, or the term or group a cardinality finding is
     * about (specification, section 9.5). It is {@code null} where the path says it all.
     */
    private static void layerJson(JsonGenerator generator,
                                  String name,
                                  Validation.Layer layer,
                                  boolean covered) throws IOException {
        generator.writeObjectFieldStart(name);
        generator.writeBooleanField("checked", layer.checked());
        boolean measured = covered && layer.findings().stream()
                .noneMatch(finding -> finding.code().recordsSomethingNotEvaluated());
        if (layer.checked() && (!layer.ok() || measured)) {
            generator.writeBooleanField("ok", layer.ok());
        } else {
            generator.writeNullField("ok");
        }
        generator.writeArrayFieldStart("findings");
        for (Finding finding : layer.findings()) {
            generator.writeStartObject();
            generator.writeStringField("path", finding.path().toString());
            if (finding.subject().isEmpty()) {
                generator.writeNullField("subject");
            } else {
                generator.writeStringField("subject", finding.subject());
            }
            generator.writeStringField("code", finding.code().code());
            generator.writeStringField("severity", finding.severity().token());
            generator.writeStringField("message", finding.message());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private static void layer(Console console,
                              String label,
                              Validation.Layer layer,
                              boolean covered) {
        row(console, label, status(layer, covered));
        listed(console, layer.findings(), Finding::toString);
    }

    /**
     * Returns what one structural layer came to.
     *
     * <p>A finding that records something the layer could not evaluate is neither an error
     * nor a note beside an {@code OK} (specification, section 9.5): the layer did not
     * finish, so the row says so rather than counting a defect the document does not have
     * or an acceptance the layer never reached. A limit is the case that matters; an
     * extension registry nobody loaded and an edition nobody holds a registry for are the
     * others, and each of them is a row of {@link Coverage} as well. {@code covered} is
     * that row read back, so a layer the coverage names as a gap reads {@code no verdict}
     * even where the finding that explains the gap sits on the layer before it.
     */
    private static String status(Validation.Layer layer, boolean covered) {
        if (!layer.checked()) {
            return "not checked";
        }
        List<Finding> errors = layer.errors();
        if (!errors.isEmpty()) {
            return errors.size() + (errors.size() == 1 ? " error" : " errors");
        }
        if (!covered || layer.findings().stream()
                .anyMatch(finding -> finding.code().recordsSomethingNotEvaluated())) {
            return "no verdict";
        }
        int notes = layer.findings().size();
        return notes == 0 ? "OK" : "OK (" + notes + (notes == 1 ? " note)" : " notes)");
    }

    /**
     * Returns what one check of the syntax engine came to.
     *
     * <p>The three levels are counted apart and each is named, because only the first of
     * them decides the verdict: a row that added them together would make a document with
     * three notes look like one with three errors. The level of a Schematron finding is
     * the flag its publisher set on the rule and never a judgement of this tool, so a rule
     * they levelled for information is reported as a note even where it reads like an
     * error.
     */
    private static String status(List<SyntaxFinding> findings) {
        StringBuilder status = new StringBuilder();
        count(status, findings, Severity.FATAL, "error", "errors");
        count(status, findings, Severity.WARNING, "warning", "warnings");
        count(status, findings, Severity.INFORMATION, "note", "notes");
        return status.length() == 0 ? "OK" : status.toString();
    }

    /** Appends "n things" for one severity, or nothing where there is none of it. */
    private static void count(StringBuilder status,
                              List<SyntaxFinding> findings,
                              Severity severity,
                              String one,
                              String many) {
        long found = findings.stream()
                .filter(finding -> finding.severity() == severity).count();
        if (found == 0) {
            return;
        }
        if (status.length() > 0) {
            status.append(", ");
        }
        status.append(found).append(' ').append(found == 1 ? one : many);
    }

    /**
     * Writes the findings of one check below its row.
     *
     * <p>The message and the location come from an artefact that quotes the document it
     * is about, so both are escaped the way the specification, section 12.6 requires of a
     * quoted fragment before they reach a terminal: an invoice is content a stranger
     * wrote, and a control sequence inside a seller name must not be able to rewrite the
     * line a reader sees.
     */
    private static void findings(Console console, List<SyntaxFinding> findings) {
        listed(console, findings, finding -> {
            String location = finding.location().isEmpty()
                    ? "" : " " + ValueText.oneLine(finding.location());
            String level = finding.severity().token()
                    + (finding.releveled() ? ", flagged " + finding.flag().token()
                            + " by the artefact" : "");
            return finding.code() + " [" + level + "]"
                    + location + ": " + ValueText.oneLine(finding.message());
        });
    }

    /**
     * Writes the findings of one check, one line each, and says how many were left out.
     *
     * <p>The row above the list already carries the count, and the list below it is what a
     * reader acts on. An invoice is content a stranger wrote, and nothing in a document
     * bounds how many findings it can produce: a hundred thousand lines each with a
     * negative unit price are a hundred thousand findings, and printing them turns a report
     * into a file nobody scrolls and a pipe nobody drains. So a check prints at most
     * {@link #FINDING_LINES} of them and then names the remainder, and {@code --verbose}
     * prints them all for the caller who wants the whole list. The count in the row is
     * never cut, so the number a reader takes away is always the true one.
     *
     * @param <T>      what a finding of this check is
     * @param console  the streams of the process, which carry {@code --verbose}
     * @param findings the findings, in the order the check reports them
     * @param line     how one of them is written
     */
    private static <T> void listed(Console console, List<T> findings, Function<T, String> line) {
        int printed = console.options().verbose()
                ? findings.size() : Math.min(findings.size(), FINDING_LINES);
        for (int at = 0; at < printed; at++) {
            console.line("    " + line.apply(findings.get(at)));
        }
        if (printed < findings.size()) {
            console.line("    ... and " + (findings.size() - printed)
                    + " more; run with --verbose for all of them");
        }
    }

    /** Writes one row of a block: two spaces, the padded label, then what it came to. */
    private static void row(Console console, String label, String status) {
        console.line("  " + pad(label + ":") + status);
    }

    private static String pad(String label) {
        StringBuilder padded = new StringBuilder(label);
        do {
            padded.append(' ');
        } while (padded.length() < LABEL_WIDTH);
        return padded.toString();
    }

    /** Returns the label of a header line, padded to the column its value begins in. */
    private static String header(String label) {
        StringBuilder padded = new StringBuilder(label);
        do {
            padded.append(' ');
        } while (padded.length() < HEADER_WIDTH);
        return padded.toString();
    }
}
