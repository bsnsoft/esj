package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.pdf.ContainerFinding;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.pdf.PdfaIdentification;
import de.bsnsoft.esj.report.Phrase;
import de.bsnsoft.esj.report.Text;
import de.bsnsoft.esj.report.ValidationOutcome;
import de.bsnsoft.esj.report.ValidationOutcome.Block;
import de.bsnsoft.esj.report.ValidationOutcome.Judged;
import de.bsnsoft.esj.report.ValidationOutcome.Row;
import de.bsnsoft.esj.report.ValidationOutcome.Subject;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleSeverity;
import de.bsnsoft.esj.syntax.ComponentRun;
import de.bsnsoft.esj.syntax.Engine;
import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.PackComponent;
import de.bsnsoft.esj.syntax.PackSource;
import de.bsnsoft.esj.syntax.Severity;
import de.bsnsoft.esj.syntax.SkippedComponent;
import de.bsnsoft.esj.syntax.SyntaxFinding;
import de.bsnsoft.esj.syntax.SyntaxReport;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.ValidationStatus;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.xr.XrSyntax;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns what a run of {@code esj validate} came to into the neutral shape a report is
 * written from.
 *
 * <p>It is the third form of one answer. {@link Reports} writes the lines a person reads
 * and the object a program reads; this class writes the same run as a
 * {@link ValidationOutcome}, which {@code esj-render} puts on a page. All three are made
 * from the same {@link Validation.Report} and none of them decides anything of its own: a
 * row here carries the status that row carried in the text form, the verdict is
 * {@link Validation.Report#status()}, the two subject verdicts of a container are the two
 * the text form prints, and a finding keeps the words its engine wrote.
 *
 * <p>Nothing is added that the run did not establish. The PDF/A conformance of a container
 * is what the file declares and is said to be a declaration, the syntax block of a
 * document that was never XML is not applicable rather than absent, a component that did
 * not run carries the reason it did not, what the import could not carry is reported
 * rather than passed over, and every pack is named with its version.
 *
 * <p>Every text of the outcome says whether it is this project's sentence, which a report
 * writes in the language it is written in, or words of the run — a rule message, a pack, a
 * switch of the command line — which stand as they are.
 */
final class Outcome {

    /** The engine a finding of the importer is reported under. */
    private static final String IMPORTER_ENGINE = "importer";

    /** The family a finding of the importer belongs to. */
    private static final String IMPORT_CATEGORY = "import";

    /** How many distinct import observations the provenance of a report carries. */
    private static final int PROVENANCE_LINES = 20;

    /** The role a syntax pack has in the identity of a report. */
    private static final String SYNTAX_ROLE = "syntax";

    /** The role the native rule pack has in the identity of a report. */
    private static final String RULES_ROLE = "rules";

    private Outcome() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns what this run came to, in the form a report is written from.
     *
     * @param input  the bytes that were handed over, with the name the caller wrote
     * @param loaded the input, read
     * @param report what the engines found
     * @return the outcome
     */
    static ValidationOutcome of(Input input, Loaded loaded, Validation.Report report) {
        List<Block> blocks = new ArrayList<>();
        container(report).ifPresent(blocks::add);
        blocks.add(syntax(report));
        blocks.add(semantic(report, loaded));
        return new ValidationOutcome(identity(input, loaded, report), blocks,
                verdict(report), detail(report), subjects(report), provenance(loaded));
    }

    /** Returns what was judged and what judged it. */
    private static ValidationOutcome.Identity identity(Input input,
                                                       Loaded loaded,
                                                       Validation.Report report) {
        Optional<SemanticDocument> document = loaded.document();
        return new ValidationOutcome.Identity(report.input(), syntaxLabel(report),
                loaded.importer()
                        .map(importer -> Text.of(importer.description(), importer.token())),
                Reports.semanticModel(report),
                profile(report), Optional.of(sha256(input.bytes())),
                document.map(Canonicalizer::semanticDigest),
                document.map(Canonicalizer::documentDigest),
                document.flatMap(SemanticDocument::source)
                        .flatMap(SemanticDocument.Source::sha256),
                packs(report), VersionProvider.tool());
    }

    /** Returns the syntax of the input, saying where it was found for a container. */
    private static Text syntaxLabel(Validation.Report report) {
        return report.container().isEmpty()
                ? Text.words(report.syntax().label())
                : Text.of(Phrase.SYNTAX_FROM_ATTACHMENT, report.syntax().label());
    }

    /**
     * Returns the specification this document is judged against, as it is known.
     *
     * <p>The identifier the document names in BT-24 is the first answer, because it is the
     * document's own statement and the pack was chosen for it. Where no artefact read one
     * — an ESJ input, a run without the syntax engine — the conformance level the
     * container resolved stands in its place, and where neither is known the member is
     * absent rather than guessed at.
     */
    private static Optional<String> profile(Validation.Report report) {
        Optional<String> named = report.syntaxCheck().report()
                .map(SyntaxReport::customizationId)
                .filter(id -> !id.isEmpty());
        return named.isPresent()
                ? named
                : report.profile().map(FacturXProfile::conformanceLevel);
    }

    /** Returns the released rule material this run executed, in report order. */
    private static List<ValidationOutcome.Pack> packs(Validation.Report report) {
        List<ValidationOutcome.Pack> packs = new ArrayList<>();
        report.syntaxCheck().report().flatMap(SyntaxReport::pack).ifPresent(pack ->
                packs.add(new ValidationOutcome.Pack(SYNTAX_ROLE, pack.id(), pack.version(),
                        Optional.of(pack.release()),
                        Optional.of(pack.source().token()))));
        report.ruleCheck().found().ifPresent(found ->
                packs.add(new ValidationOutcome.Pack(RULES_ROLE, found.packId(),
                        found.packVersion(), Optional.empty(),
                        Optional.of(PackSource.BUNDLED.token()))));
        return List.copyOf(packs);
    }

    /**
     * Returns the block of the file an invoice was carried in, where there was one.
     *
     * <p>The rows are the ones the text form prints, and the PDF/A row says what the
     * run says: without {@code --verapdf} the file declares a conformance and nothing here
     * validated it, so the status is that declaration and never {@code OK}; with one, the
     * row carries the validator, its version, the profile it ran and its verdict, and it
     * is a check like any other. The sixth row is there only for a file that carries an
     * ESJ document beside its invoice.
     */
    private static Optional<Block> container(Validation.Report report) {
        Optional<Container> found = report.container();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Container pdf = found.orElseThrow();
        List<Row> rows = new ArrayList<>(List.of(
                containerRow(pdf, Phrase.ROW_PDF_STRUCTURE,
                        ContainerFinding.Category.PDF_STRUCTURE, null),
                pdfaRow(pdf, report.pdfaValidation()),
                containerRow(pdf, Phrase.ROW_PDF_AF, ContainerFinding.Category.PDF_AF,
                        relationship(pdf)),
                containerRow(pdf, Phrase.ROW_PDF_XMP, ContainerFinding.Category.PDF_XMP,
                        null),
                containerRow(pdf, Phrase.ROW_PDF_EMBEDDED,
                        ContainerFinding.Category.PDF_EMBEDDED, null)));
        // Only where the file carries one. A container without an ESJ document beside its
        // invoice is the ordinary hybrid invoice and has nothing to answer for, and a row
        // saying so on every run would be a check nobody asked for.
        pdf.esj().ifPresent(esj -> rows.add(containerRow(pdf, Phrase.ROW_PDF_ESJ,
                ContainerFinding.Category.PDF_ESJ,
                esjDetail(esj))));
        List<ValidationOutcome.Finding> findings = new ArrayList<>();
        for (ContainerFinding finding : spoken(pdf.findings())) {
            findings.add(new ValidationOutcome.Finding(finding.category().id(),
                    finding.code(), severity(finding), "container", Optional.empty(),
                    List.of(), finding.message()));
        }
        return Optional.of(new Block(Block.Kind.CONTAINER, List.copyOf(rows), findings));
    }

    /**
     * Returns what the row of the ESJ document says beside its name.
     *
     * <p>Where the enclosure carried paths of terms the invoice syntax binds nothing of,
     * the row says how many: those are the paths the comparison had nothing to measure
     * them against, and a row that reported the comparison without naming them would be
     * claiming more than the run established.
     */
    private static Text esjDetail(Container.Esj esj) {
        String name = ValueText.quoted(esj.name());
        Integer unchecked = esj.unchecked();
        return unchecked == null || unchecked == 0
                ? Text.of(Phrase.ESJ_ATTACHMENT, name)
                : Text.of(Phrase.ESJ_ATTACHMENT_PARTLY, name, Integer.toString(unchecked));
    }

    /** Returns one row of the container block, counted by the findings of its category. */
    private static Row containerRow(Container pdf,
                                    Phrase label,
                                    ContainerFinding.Category category,
                                    Text detail) {
        List<ContainerFinding> findings = spoken(pdf.findings(category));
        if (findings.isEmpty()) {
            return new Row(Text.of(label), Row.Status.OK, 0, 0, Optional.ofNullable(detail));
        }
        int errors = (int) findings.stream()
                .filter(finding -> finding.severity() == ContainerFinding.Severity.ERROR)
                .count();
        return new Row(Text.of(label), Row.Status.FOUND, errors, findings.size() - errors,
                Optional.ofNullable(detail));
    }

    /** Returns the findings a report speaks: everything the container did not note. */
    private static List<ContainerFinding> spoken(List<ContainerFinding> findings) {
        return findings.stream()
                .filter(finding -> finding.severity() != ContainerFinding.Severity.INFO)
                .toList();
    }

    /**
     * Returns the PDF/A row: what the file declares, and what a validator said about that
     * claim where the caller lent one.
     *
     * <p>A run without {@code --verapdf} checked nothing about PDF/A, so the row is the
     * declaration and its status says so. A run with one asked the question and has an
     * answer, so the row is {@code ok} or carries the one error the validator's refusal
     * is — which is also what makes the container of such a run {@code INVALID} in the
     * verdicts above, exactly as it does in the printed lines.
     *
     * <p>The refusal is not listed a second time among the findings. The validator reports
     * how many of its rules and checks failed and not which, so a finding written from it
     * would carry an identifier this project invented; the row says everything the
     * validator said, in the validator's own sentence.
     */
    private static Row pdfaRow(Container pdf, Optional<Verapdf.Result> validation) {
        if (validation.isEmpty()) {
            return new Row(Text.of(Phrase.ROW_PDFA), Row.Status.DECLARED, 0, 0,
                    Optional.of(pdfa(pdf.pdfa())));
        }
        Verapdf.Result result = validation.orElseThrow();
        Text detail = pdf.pdfa()
                .map(declared -> Text.of(Phrase.PDFA_DECLARED_VALIDATED,
                        declared.describe(), result.describe()))
                .orElseGet(() -> Text.of(Phrase.PDFA_NONE_VALIDATED, result.describe()));
        return result.compliant()
                ? new Row(Text.of(Phrase.ROW_PDFA), Row.Status.OK, 0, 0, Optional.of(detail))
                : new Row(Text.of(Phrase.ROW_PDFA), Row.Status.FOUND, 1, 0,
                        Optional.of(detail));
    }

    /** Returns the PDF/A conformance a file declares, said to be a declaration. */
    private static Text pdfa(Optional<PdfaIdentification> declared) {
        return declared.map(pdfa -> Text.of(Phrase.PDFA_DECLARED, pdfa.describe()))
                .orElse(Text.of(Phrase.PDFA_NONE));
    }

    /** Returns what the container says the invoice attachment is to the document. */
    private static Text relationship(Container container) {
        return container.invoice()
                .flatMap(invoice -> invoice.attachment().file().associatedRelationship())
                .map(value -> Text.of(Phrase.AF_RELATIONSHIP, ValueText.quoted(value)))
                .orElse(null);
    }

    /** Returns the severity of a container finding in the vocabulary of a report. */
    private static de.bsnsoft.esj.validate.Severity severity(
            ContainerFinding finding) {
        return finding.severity() == ContainerFinding.Severity.ERROR
                ? de.bsnsoft.esj.validate.Severity.ERROR
                : de.bsnsoft.esj.validate.Severity.WARNING;
    }

    /**
     * Returns the block of the official artefacts.
     *
     * <p>A document that was never XML has no binding to check, so the block carries one
     * row that says so rather than being left out: an absent block reads as a check that
     * was forgotten. The artefacts still answer for such a document, over the XML the run
     * wrote it to, and {@link #written} puts that row into the same block.
     */
    private static Block syntax(Validation.Report report) {
        List<Row> rows = new ArrayList<>();
        List<XmlFinding> bytes = report.syntaxFindings();
        List<ValidationOutcome.Finding> findings = new ArrayList<>();
        if (!bytes.isEmpty()) {
            rows.add(Row.found(Text.of(Phrase.ROW_XML_BYTES), bytes.size(), 0));
            for (XmlFinding finding : bytes) {
                findings.add(new ValidationOutcome.Finding(finding.category(),
                        finding.code(), de.bsnsoft.esj.validate.Severity.ERROR,
                        "parser", Optional.empty(), List.of(), finding.message()));
            }
        }
        SyntaxCheck check = report.syntaxCheck();
        Optional<SyntaxReport> found = check.report();
        if (found.isEmpty()) {
            String reason = check.reason().orElseThrow();
            rows.add(new Row(Text.of(Phrase.ROW_PACK), SyntaxCheck.NO_XML.equals(reason)
                    ? Row.Status.NOT_APPLICABLE : Row.Status.SKIPPED, 0, 0,
                    Optional.of(SyntaxCheck.NO_XML.equals(reason)
                            ? Text.of(Phrase.REASON_NO_XML) : Text.words(bare(reason)))));
        } else {
            SyntaxReport ran = found.orElseThrow();
            rows.add(componentRow(Text.of(Phrase.ROW_XML), ran.findings(Engine.PARSER)));
            ran.pack().ifPresent(pack -> rows.addAll(components(check, ran, pack)));
            for (SyntaxFinding finding : ran.findings()) {
                findings.add(finding(finding));
            }
        }
        written(report, rows, findings);
        return new Block(Block.Kind.SYNTAX, rows, findings);
    }

    /**
     * Adds the rows of the official artefacts over the XML an ESJ document was written to.
     *
     * <p>An input that arrived as XML gets none of them: its artefacts read the bytes
     * themselves, and a second set of rows about a rendition nobody made would be noise.
     * An ESJ input gets them all, because they are the components of the complete check
     * for that kind of input and a report that left them out would say less than the lines
     * the same run printed.
     *
     * <p>The first row names the syntax the document was written in, and carries what the
     * artefacts found over it or the reason they were not asked. Where they ran, the
     * components follow it under their own names, exactly as they do for an XML input, so
     * that a reader meets one check table and not two shapes of one.
     *
     * <p>Between the two stands one row per registry that declares its terms to have no
     * transport binding, naming the terms the written document does not carry. It declares
     * rather than checks: the artefacts above it answered for the invoice, and this says
     * what was never theirs to read.
     */
    private static void written(Validation.Report report,
                                List<Row> rows,
                                List<ValidationOutcome.Finding> findings) {
        if (report.syntax() != InputSyntax.ESJ) {
            return;
        }
        WrittenCheck written = report.written();
        Text label = Text.of(Phrase.ROW_WRITTEN_SYNTAX, written.target());
        Optional<SyntaxCheck> asked = written.check();
        if (asked.isEmpty()) {
            rows.add(new Row(label, status(written.cause().orElseThrow()), 0, 0,
                    Optional.of(Text.words(bare(written.reason().orElseThrow())))));
            return;
        }
        SyntaxCheck check = asked.orElseThrow();
        Optional<SyntaxReport> found = check.report();
        if (found.isEmpty()) {
            rows.add(Row.skipped(label, Text.words(bare(check.reason().orElseThrow()))));
            return;
        }
        SyntaxReport ran = found.orElseThrow();
        rows.add(componentRow(label, ran.findings()));
        for (WrittenCheck.ByDesign entry : written.byDesign()) {
            rows.add(new Row(Text.of(Phrase.ROW_TERMS_WITHOUT_TRANSPORT, entry.registry()),
                    Row.Status.DECLARED, 0, 0,
                    Optional.of(Text.of(Phrase.DETAIL_ONLY_IN_ESJ,
                            String.join(", ", entry.terms())))));
        }
        ran.pack().ifPresent(pack -> rows.addAll(components(check, ran, pack)));
        for (SyntaxFinding finding : ran.findings()) {
            findings.add(finding(finding));
        }
    }

    /**
     * Returns the status of a written row the artefacts were not asked for.
     *
     * <p>A caller who took the artefacts out left a gap, and a run that could not write
     * the document into a syntax the artefacts read had nothing for them to run on. The
     * two are different answers and the check table has a status for each.
     */
    private static Row.Status status(Coverage.Cause cause) {
        return cause == Coverage.Cause.SKIPPED_BY_CALLER
                ? Row.Status.SKIPPED : Row.Status.NOT_APPLICABLE;
    }

    /** Returns one finding of an official artefact, in the shape a report carries. */
    private static ValidationOutcome.Finding finding(SyntaxFinding finding) {
        return new ValidationOutcome.Finding(finding.category().label(),
                finding.code(), severity(finding.severity()), flag(finding),
                finding.engine().token(),
                Optional.of(finding.packId() + "/" + finding.packVersion()),
                location(finding), finding.message());
    }

    /**
     * Returns the level the artefact itself flagged, where the profile asked for another.
     *
     * <p>The level that decides the verdict is the one the core invoice usage
     * specification of the document asks for, and a report that printed it alone would
     * hide that the two publishers of the finding disagree about how much it weighs.
     */
    private static Optional<String> flag(SyntaxFinding finding) {
        return finding.releveled() ? Optional.of(finding.flag().token()) : Optional.empty();
    }

    /**
     * Returns one row per component of the pack that could apply to this document.
     *
     * <p>A pack carries the artefacts of every syntax it covers, so a UBL invoice leaves
     * the CII components unused; those rows are the definition of the pack rather than
     * information about the document, and a report that carried them would push the
     * findings off the page.
     */
    private static List<Row> components(SyntaxCheck check, SyntaxReport report, Pack pack) {
        Set<String> ran = new LinkedHashSet<>();
        for (ComponentRun run : report.ran()) {
            ran.add(run.component());
        }
        Map<String, SkippedComponent.Reason> skipped = new LinkedHashMap<>();
        for (SkippedComponent component : report.skipped()) {
            skipped.put(component.component(), component.reason());
        }
        Optional<XrSyntax> syntax = report.syntax();
        List<Row> rows = new ArrayList<>();
        for (PackComponent component : pack.components()) {
            if (syntax.isPresent() && !component.appliesToSyntax(syntax.orElseThrow())) {
                continue;
            }
            Text label = Text.words(SyntaxPacks.label(component.name()));
            if (ran.contains(component.name())) {
                rows.add(componentRow(label, check.findings(component.name())));
            } else if (skipped.containsKey(component.name())) {
                rows.add(Row.skipped(label, reason(skipped.get(component.name()))));
            }
        }
        return rows;
    }

    /**
     * Returns why a component of the pack did not run, as a sentence of this project.
     *
     * <p>The pack says which of the reasons it is and not how to write it down: the
     * sentence belongs to the report and is written in the language the report is written
     * in, which is what keeps a German check table from carrying an English row.
     */
    private static Text reason(SkippedComponent.Reason reason) {
        return Text.of(switch (reason) {
            case OTHER_SYNTAX -> Phrase.SKIPPED_OTHER_SYNTAX;
            case OTHER_PROFILE -> Phrase.SKIPPED_OTHER_PROFILE;
            case SCHEMA_INVALID -> Phrase.SKIPPED_SCHEMA_INVALID;
        });
    }

    /** Returns the row of one artefact: what it found, counted by the level it found it at. */
    private static Row componentRow(Text label, List<SyntaxFinding> findings) {
        int errors = count(findings, Severity.FATAL);
        int warnings = count(findings, Severity.WARNING);
        int notes = count(findings, Severity.INFORMATION);
        if (errors == 0 && warnings == 0) {
            return notes == 0 ? Row.ok(label)
                    : new Row(label, Row.Status.OK, 0, 0, Optional.of(notes(notes)));
        }
        return new Row(label, Row.Status.FOUND, errors, warnings,
                notes == 0 ? Optional.empty() : Optional.of(notes(notes)));
    }

    /** Counts the findings of one level. */
    private static int count(List<SyntaxFinding> findings, Severity severity) {
        return (int) findings.stream()
                .filter(finding -> finding.severity() == severity).count();
    }

    /** Returns how many notes a row carries beside its counted findings. */
    private static Text notes(int notes) {
        return notes == 1 ? Text.of(Phrase.NOTE, "1")
                : Text.of(Phrase.NOTES, Integer.toString(notes));
    }

    /** Returns the level a finding of an official artefact is reported at. */
    private static de.bsnsoft.esj.validate.Severity severity(Severity level) {
        return switch (level) {
            case FATAL -> de.bsnsoft.esj.validate.Severity.ERROR;
            case WARNING -> de.bsnsoft.esj.validate.Severity.WARNING;
            case INFORMATION -> de.bsnsoft.esj.validate.Severity.INFO;
        };
    }

    /** Returns where an artefact found something, in the terms the artefact used. */
    private static List<String> location(SyntaxFinding finding) {
        return finding.location().isEmpty() ? List.of() : List.of(finding.location());
    }

    /**
     * Returns the block of the import, the structural layers and the native rules.
     *
     * <p>A layer that did not run carries the reason it did not, and a layer that ran over
     * less than the document reaches no verdict rather than passing: the coverage of the
     * run decides that, exactly as it decides the row of the text form.
     *
     * <p>The import comes first, because the layers below it can only decide what it
     * carried. What it could not carry is a finding of this block like any other, under
     * the engine that made the observation, so that it is counted in a row and cannot be
     * read past.
     */
    private static Block semantic(Validation.Report report, Loaded loaded) {
        List<Row> rows = new ArrayList<>();
        List<ImportNote> lost = Loaded.lostContent(loaded.report());
        loaded.importer().ifPresent(importer -> rows.add(lost.isEmpty()
                ? Row.ok(Text.of(Phrase.ROW_IMPORT))
                : Row.found(Text.of(Phrase.ROW_IMPORT), 0, lost.size())));
        rows.add(layer(Phrase.ROW_FORMAT_L1, report.l1(), report, Coverage.FORMAT_L1,
                formatReason(report)));
        rows.add(layer(Phrase.ROW_MODEL_L2, report.l2(), report, Coverage.MODEL_L2,
                modelReason(report, loaded)));
        rows.add(layer(Phrase.ROW_CARDINALITY_L3, report.l3(), report,
                Coverage.CARDINALITY_L3, cardinalityReason(report, loaded)));
        rows.add(rules(report.ruleCheck()));
        List<ValidationOutcome.Finding> findings = new ArrayList<>();
        for (ImportNote note : lost) {
            findings.add(new ValidationOutcome.Finding(IMPORT_CATEGORY, note.kind().name(),
                    de.bsnsoft.esj.validate.Severity.WARNING, IMPORTER_ENGINE,
                    Optional.empty(), place(note), note.message()));
        }
        structural(findings, report.l1());
        structural(findings, report.l2());
        structural(findings, report.l3());
        for (RuleCheck.Levelled levelled : report.ruleCheck().findings()) {
            RuleFinding finding = levelled.finding();
            findings.add(new ValidationOutcome.Finding(finding.category().token(),
                    finding.code(), severity(levelled.severity()),
                    levelled.levelled() ? Optional.of(levelled.standard().token())
                            : Optional.<String>empty(),
                    levelled.levelled() ? Optional.of(levelled.profile())
                            : Optional.<String>empty(),
                    finding.engine(),
                    Optional.of(finding.packId() + "/" + finding.packVersion()),
                    finding.paths(), finding.message()));
        }
        return new Block(Block.Kind.SEMANTIC, rows, findings, alsoReported(report));
    }

    /** Returns where the import made an observation, where it named a place. */
    private static List<String> place(ImportNote note) {
        return note.location().isEmpty() ? List.of() : List.of(note.location());
    }

    /**
     * Returns the rule identifiers this run reported twice, once from each engine.
     *
     * <p>The list is of identifiers rather than of findings, for the reason the text form
     * gives: an artefact and a native rule can report the same rule about different places
     * of one invoice, and a sentence that paired them up would claim a correspondence
     * nothing established.
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

    /** Adds the findings of one structural layer, in the order the layer met them. */
    private static void structural(List<ValidationOutcome.Finding> findings,
                                   Validation.Layer layer) {
        for (Finding finding : layer.findings()) {
            findings.add(new ValidationOutcome.Finding("ESJ", finding.code().code(),
                    finding.severity(), "structural", Optional.empty(),
                    place(finding), finding.message()));
        }
    }

    /** Returns where a structural finding is: the path, and what the path cannot name. */
    private static List<String> place(Finding finding) {
        String path = finding.path().toString();
        if (finding.subject().isEmpty()) {
            return path.isEmpty() ? List.of() : List.of(path);
        }
        return path.isEmpty() ? List.of(finding.subject())
                : List.of(path, finding.subject());
    }

    /**
     * Returns the row of one structural layer.
     *
     * <p>The four answers are the four the text form writes. An error outranks everything
     * else; a layer the coverage names as a gap reaches no verdict even where the finding
     * that explains the gap sits on the layer above it; and a layer that never ran says
     * why, and says whether there was anything for it to run on at all.
     */
    private static Row layer(Phrase label,
                             Validation.Layer layer,
                             Validation.Report report,
                             String component,
                             NotRun reason) {
        if (!layer.checked()) {
            return new Row(Text.of(label), reason.applicable() ? Row.Status.SKIPPED
                    : Row.Status.NOT_APPLICABLE, 0, 0, Optional.of(reason.text()));
        }
        List<Finding> errors = layer.errors();
        if (!errors.isEmpty()) {
            int warnings = layer.findings().size() - errors.size();
            return Row.found(Text.of(label), errors.size(), warnings);
        }
        boolean covered = report.coverage().gaps().stream()
                .noneMatch(row -> row.component().equals(component));
        if (!covered || layer.findings().stream()
                .anyMatch(finding -> finding.code().recordsSomethingNotEvaluated())) {
            return new Row(Text.of(label), Row.Status.NO_VERDICT, 0, 0,
                    Optional.of(Text.of(Phrase.REASON_MEASURED_LESS)));
        }
        int notes = layer.findings().size();
        return notes == 0 ? Row.ok(Text.of(label))
                : new Row(Text.of(label), Row.Status.OK, 0, 0, Optional.of(notes(notes)));
    }

    /** Returns why the format layer did not run, where it did not. */
    private static NotRun formatReason(Validation.Report report) {
        return report.syntax() == InputSyntax.ESJ
                ? new NotRun(Text.of(Phrase.REASON_NOT_RUN), true)
                : new NotRun(Text.of(Phrase.REASON_INPUT_XML), false);
    }

    /** Returns why the model layer did not run, where it did not. */
    private static NotRun modelReason(Validation.Report report, Loaded loaded) {
        if (!report.en16931Invoice()) {
            return new NotRun(Text.of(Phrase.REASON_NO_INVOICE_LINE), false);
        }
        if (loaded.document().isEmpty()) {
            return new NotRun(Text.of(Phrase.REASON_NO_DOCUMENT), true);
        }
        return new NotRun(Text.of(Phrase.REASON_FORMAT_REJECTED), true);
    }

    /** Returns why the cardinality layer did not run, where it did not. */
    private static NotRun cardinalityReason(Validation.Report report, Loaded loaded) {
        if (!report.l2().checked()) {
            return modelReason(report, loaded);
        }
        if (!report.l2().ok()) {
            return new NotRun(Text.of(Phrase.REASON_MODEL_REJECTED), true);
        }
        if (report.l2().notEvaluatedCause().isPresent()) {
            return new NotRun(Text.of(Phrase.REASON_MODEL_MEASURED_NOTHING), true);
        }
        return new NotRun(Text.words("--level l2"), true);
    }

    /**
     * Why a row of the semantic block did not run, and whether its absence is a gap.
     *
     * @param text       the reason, in the words the report prints
     * @param applicable whether there was something for it to run on, which separates a
     *                   check that was left out from one that could never have applied
     */
    private record NotRun(Text text, boolean applicable) {
    }

    /** Returns the row of the native rule engine, named with the pack that decided it. */
    private static Row rules(RuleCheck check) {
        Text label = Text.of(Phrase.ROW_RULES, RuleCheck.PACK);
        Optional<RuleCheck.Found> found = check.found();
        if (found.isEmpty()) {
            return Row.skipped(label, Text.words(bare(check.reason().orElseThrow())));
        }
        RuleCheck.Found ran = found.orElseThrow();
        int errors = (int) ran.count(RuleSeverity.FATAL);
        int warnings = (int) ran.count(RuleSeverity.WARNING);
        int notes = (int) ran.count(RuleSeverity.INFO);
        Optional<Text> detail = notes == 0 ? Optional.empty() : Optional.of(notes(notes));
        if (errors == 0 && warnings == 0) {
            return new Row(label, Row.Status.OK, 0, 0, detail);
        }
        return new Row(label, Row.Status.FOUND, errors, warnings, detail);
    }

    /**
     * Returns the reason a check gives, without the status it repeats.
     *
     * <p>The text form writes a reason as a whole row — {@code skipped (--no-syntax)} — and
     * the report has a column for the status already, so the parenthesis is what belongs in
     * the other column. A reason written in any other shape is passed through unchanged.
     *
     * @param reason the reason as the check states it
     * @return what is inside the parenthesis, or the reason itself
     */
    private static String bare(String reason) {
        int open = reason.indexOf(" (");
        return open > 0 && reason.endsWith(")")
                ? reason.substring(open + 2, reason.length() - 1) : reason;
    }

    /** Returns the level a native rule finding is reported at. */
    private static de.bsnsoft.esj.validate.Severity severity(
            RuleSeverity severity) {
        return switch (severity) {
            case FATAL -> de.bsnsoft.esj.validate.Severity.ERROR;
            case WARNING -> de.bsnsoft.esj.validate.Severity.WARNING;
            case INFO -> de.bsnsoft.esj.validate.Severity.INFO;
        };
    }

    /**
     * Returns the verdict of the run, or nothing where it reached none.
     *
     * <p>A run a bound of its own stopped judged nothing, which is not one of the three
     * states of the specification, section 9.5 and is not written as a word.
     */
    private static Optional<ValidationStatus> verdict(Validation.Report report) {
        return report.limit().isPresent()
                ? Optional.empty() : Optional.of(report.status());
    }

    /** Returns what the verdict line says beyond the word, where it says anything. */
    private static Optional<Text> detail(Validation.Report report) {
        if (report.limit().isPresent()) {
            return Optional.of(Text.of(Phrase.DETAIL_LIMIT));
        }
        if (report.status() == ValidationStatus.INDETERMINATE) {
            return Optional.of(Text.of(Phrase.DETAIL_MISSING, report.coverage().describe()));
        }
        if (!report.en16931Invoice()) {
            return Optional.of(Text.of(Phrase.DETAIL_NOT_EN16931,
                    report.profile().orElseThrow().conformanceLevel()));
        }
        return report.invoiceFailure().isEmpty() && report.containerFailed()
                ? Optional.of(Text.of(Phrase.DETAIL_CONTAINER_WRONG)) : Optional.empty();
    }

    /**
     * Returns the verdicts of the subjects this run judged apart.
     *
     * <p>A run over a container answered two questions and the text form prints both. The
     * report carries the same two, because the one word above them cannot say that the
     * invoice of a conformant MINIMUM container was never checked, or that a sound invoice
     * sits inside a file that is wrong about it — and a reader of the file alone has
     * nothing else to go on.
     */
    private static List<Subject> subjects(Validation.Report report) {
        if (report.container().isEmpty()) {
            return List.of();
        }
        return List.of(new Subject(Judged.CONTAINER, report.containerFailed()
                        ? ValidationStatus.INVALID.name() : ValidationOutcome.OK,
                        Optional.empty()),
                invoiceSubject(report));
    }

    /**
     * Returns the verdict of the invoice inside a container.
     *
     * <p>The four answers are the four the text form writes, and for the same reasons:
     * a profile that carries no invoice line was not checked rather than rejected, a run a
     * bound stopped reached no verdict, a fatal finding of any engine is
     * {@code INVALID}, and a check that did not run in full is {@code INDETERMINATE} with
     * what it was missing.
     */
    private static Subject invoiceSubject(Validation.Report report) {
        if (!report.en16931Invoice()) {
            return new Subject(Judged.INVOICE, ValidationOutcome.NOT_CHECKED,
                    Optional.of(Text.of(Phrase.SUBJECT_PROFILE,
                            report.profile().orElseThrow().conformanceLevel())));
        }
        if (report.limit().isPresent()) {
            return new Subject(Judged.INVOICE, ValidationOutcome.NO_VERDICT,
                    Optional.of(Text.of(Phrase.SUBJECT_LIMIT)));
        }
        if (report.invoiceFailure().isPresent()) {
            return new Subject(Judged.INVOICE, ValidationStatus.INVALID.name(),
                    Optional.empty());
        }
        return report.coverage().complete()
                ? new Subject(Judged.INVOICE, ValidationStatus.VALID.name(),
                        Optional.empty())
                : new Subject(Judged.INVOICE, ValidationStatus.INDETERMINATE.name(),
                        Optional.of(Text.of(Phrase.DETAIL_MISSING,
                                report.coverage().describe())));
    }

    /**
     * Returns what was done to the bytes before they were read, one sentence each.
     *
     * <p>The observations of the import that lost nothing are here as well. They describe
     * the distance between the source syntax and the semantic model rather than this
     * document, which is why the error stream shows them only under {@code --verbose} —
     * but a report is read by somebody who cannot run the command again, so the record
     * belongs in it. Identical observations are collapsed with a count and the list is cut
     * off, for the reason the error stream collapses them: one invoice makes the same
     * observation dozens of times, and a section nobody reads is a section that hides the
     * one line that mattered.
     */
    private static List<Text> provenance(Loaded loaded) {
        List<Text> sentences = new ArrayList<>();
        loaded.container().flatMap(Container::invoice).ifPresent(invoice ->
                sentences.add(Text.of(Phrase.PROVENANCE_ATTACHMENT,
                        Integer.toString(invoice.position()), invoice.name())));
        loaded.encoding().ifPresent(recoded ->
                sentences.add(Text.of(Phrase.PROVENANCE_RECODED, recoded.describe())));
        sentences.addAll(observations(loaded));
        return List.copyOf(sentences);
    }

    /** Returns the observations of the import that lost nothing, collapsed and cut off. */
    private static List<Text> observations(Loaded loaded) {
        Map<String, Integer> collapsed = new LinkedHashMap<>();
        for (ImportNote note : loaded.report().notes(ImportNote.Level.INFORMATION)) {
            collapsed.merge(note.toString(), 1, Integer::sum);
        }
        List<Text> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : collapsed.entrySet()) {
            if (lines.size() == PROVENANCE_LINES) {
                lines.add(Text.of(Phrase.MORE_NOTES,
                        Integer.toString(collapsed.size() - PROVENANCE_LINES)));
                break;
            }
            lines.add(entry.getValue() == 1 ? Text.words(entry.getKey())
                    : Text.of(Phrase.TIMES, entry.getKey(),
                            Integer.toString(entry.getValue())));
        }
        return lines;
    }

    /** Returns the SHA-256 of the bytes that were handed over, as lowercase hexadecimal. */
    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }
}
