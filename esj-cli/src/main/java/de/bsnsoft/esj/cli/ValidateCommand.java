package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.BindingEditionException;
import de.bsnsoft.esj.bindings.BindingFormatException;
import de.bsnsoft.esj.bindings.BindingLimitException;
import de.bsnsoft.esj.bindings.BindingSyntaxException;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.UblWriter;
import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.bindings.WriteReport;
import de.bsnsoft.esj.bindings.WriteResult;
import de.bsnsoft.esj.bindings.WriterOptions;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.render.RenderLanguage;
import de.bsnsoft.esj.render.ReportOptions;
import de.bsnsoft.esj.render.ReportRenderer;
import de.bsnsoft.esj.report.ValidationOutcome;
import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.PackSource;
import de.bsnsoft.esj.syntax.SyntaxLimitException;
import de.bsnsoft.esj.syntax.SyntaxReport;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj validate}: run everything this version can run over a document and say what
 * each of them found — and what none of them looked at.
 *
 * <p>Two engines run. The <b>syntax engine</b> takes an XML input as it arrived and runs
 * the official validation artefacts of its profile over it: the XML Schema modules of its
 * syntax, the EN 16931 Schematron of CEN/TC 434 and the Schematron of the core invoice
 * usage specification the document names in BT-24. Those artefacts are carried in the jar
 * as data and executed, never reimplemented, so the rules that decide are the rules their
 * publishers released. The <b>semantic engine</b> runs the structural layers of the
 * specification, section 9 over the document the importer built — L1 for an ESJ input,
 * which was read rather than built, and L2 and L3 for both — and then the business rules
 * of EN 16931-1, clause 6.4 as rules of this project, written over the business terms
 * rather than over the XPath of a syntax.
 *
 * <p>An ESJ input has no XML and therefore no binding to check; the syntax block says so
 * rather than being left out. For such an input the native rules are the only check the
 * arithmetic of the standard gets at all, which is why {@code --rules} defaults to
 * running them. The verdict is one word over every engine that ran, because a caller who
 * runs {@code esj validate x.xml && deploy} asked one question.
 *
 * <p>Over an XML input a rule of the standard can be reported twice, once by the official
 * Schematron and once by the native pack. Both are printed and both are in the JSON
 * report under the engine that produced them; {@link Reports} carries why neither is
 * suppressed.
 *
 * <p>{@code --max-runtime} is one number over the whole command rather than over the
 * syntax engine alone. Taking the bytes in and reading a large or deliberately awkward
 * document into the semantic model are the other expensive parts, and a bound that left
 * them out would be no bound at all for a caller who set one because the document came
 * from a stranger — least of all the read, where a bounded number of bytes says nothing
 * about how long a stream takes to produce them. A {@link Deadline} carries it: the clock
 * starts before anything is opened, each step runs inside what is left, and what the
 * steps before it did not use is what the official artefacts get.
 *
 * <p>{@code --verapdf} is the one thing this command does not do by itself. The PDF/A
 * conformance of a container is reported as the declaration it is, because nothing in this
 * build validates it and the reference validator is under a licence that keeps it out of
 * everything this project publishes. The switch names an installation of the caller's, which
 * runs as a process of its own inside the same deadline, and its version, its profile and its
 * verdict go into the container block; a run that asked for it has asked the question, so a
 * file the validator rejects is a container that is wrong about itself. See {@link Verapdf}.
 *
 * <p>{@code --report} writes the same run as one file a person keeps: the identity of what
 * was judged, the check table, the findings and the rendered invoice. It changes neither the
 * lines this command prints nor the code it leaves with, because a report is a second form of
 * one answer and never a second answer — not even when {@code --max-runtime} runs out
 * while it is being drawn, in which case the file is what is given up, and not the
 * verdict; {@code docs/cli.md} says what is in it.
 *
 * <p>One finding is not a verdict about the invoice: a limit of this run that the document
 * outgrew. It is printed in the report like every other finding, and the command leaves
 * with {@link ExitCode#LIMIT} rather than with {@link ExitCode#VALIDATION}, because a
 * reader that stopped has said nothing about the document it stopped reading
 * (specification, sections 3.1 and 12.2).
 */
@Command(name = "validate",
        description = "Check a document: the official artefacts of its profile, the"
                + " structural layers of the ESJ specification, and the business rules of"
                + " EN 16931 over the semantic document. A PDF is opened first and the"
                + " invoice inside it is checked, with what the container had to say"
                + " reported beside it. VALID and exit code 0 are given only where the"
                + " complete check for that kind of input ran.",
        sortOptions = false)
final class ValidateCommand implements Callable<Integer> {

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<file|->",
            description = "The document to check, or - for the standard input.")
    private String file;

    @Option(order = 10, names = "--level", paramLabel = "<l2|l3>", defaultValue = "l3",
            description = "The highest structural layer to run: l2 stops after the model"
                    + " layer, l3 runs the cardinality layer too.")
    private String level;

    @Option(order = 20, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read the input as this syntax instead of recognizing it.")
    private String from;

    @Option(order = 30, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are checked instead"
                    + " of being reported as not checked. Two"
                    + " names separated by a comma load both.")
    private String extension;

    @Option(order = 40, names = "--pack", paramLabel = "<directory|id>",
            description = "Run this validation pack instead of the bundled one that"
                    + " recognizes the profile: a directory holding a pack.json, or the"
                    + " identity of a bundled pack. esj --list-packs shows what is bundled.")
    private String pack;

    @Option(order = 45, names = "--via", paramLabel = "<cii|ubl>", defaultValue = "cii",
            description = "The syntax an ESJ document is written to, in memory, so that the"
                    + " official artefacts of its profile can be run over it. It changes"
                    + " nothing about an input that is already XML.")
    private String via;

    @Option(order = 50, names = "--rules", paramLabel = "<none|en16931>", defaultValue = "en16931",
            description = "The business rules to check natively over the semantic document."
                    + " none leaves them out; together with --no-syntax it is the fast"
                    + " semantic-only check for a very large input.")
    private String rules;

    @Option(order = 60, names = "--no-syntax",
            description = "Leave the official artefacts out and run the structural layers"
                    + " alone. It is the way to check a very large document structurally"
                    + " without paying for the Schematron, and it is never the default: a"
                    + " run without it is the complete check.")
    private boolean noSyntax;

    @Option(order = 70, names = "--output", paramLabel = "<text|json>", defaultValue = "text",
            description = "The form of the report: lines for a person, or a stable object for"
                    + " a program.")
    private String output;

    @Option(order = 80, names = "--verapdf", paramLabel = "<path>",
            description = "Validate the PDF/A conformance of a container with a veraPDF"
                    + " installation of your own: the directory it was installed into, or"
                    + " its executable. It runs as a process of its own, inside"
                    + " --max-runtime, and the container block reports its version, the"
                    + " profile it ran and its verdict. Without it this tool reports what"
                    + " the file declares about itself and says so.")
    private String verapdf;

    @Option(order = 90, names = "--report", paramLabel = "<file|->",
            description = "Also write the whole run as one self-contained file for a person"
                    + " to read: .html or .pdf by the name given, or - for the standard"
                    + " output with --report-format. The printed lines are the same with and"
                    + " without it; the exit code is not, where the report was not"
                    + " delivered: a destination that cannot be written and a report given"
                    + " up on the deadline both leave with exit code 6, unless the verdict"
                    + " is invalid and keeps code 1.")
    private String report;

    @Option(order = 100, names = "--report-format", paramLabel = "<html|pdf>",
            description = "The form of the report, where the name does not say it: required"
                    + " with --report - and with a name that ends in neither .html nor"
                    + " .pdf. Where the name says a form, this must say the same one or"
                    + " nothing: a file called .html never holds a PDF.")
    private String reportFormat;

    @Option(order = 110, names = "--report-lang", paramLabel = "<de|en>", defaultValue = "de",
            description = "The language of this report's own words and of the invoice in"
                    + " it; what a check, a pack or a rule is called is printed as the run"
                    + " reports it. Default: de.")
    private String reportLang;

    @Option(order = 120, names = "--report-time", paramLabel = "<moment>",
            description = "Print this moment in the report, exactly as written. Without it"
                    + " the report names none, so the same input gives the same bytes.")
    private String reportTime;

    @Option(order = 125, names = "--report-page", paramLabel = "<A4|LETTER>",
            description = "The paper the PDF report is laid out for, its own pages and the"
                    + " invoice pages after them alike. The HTML report has no paper and"
                    + " ignores it. Default: A4.")
    private String reportPage;

    @Option(order = 130, names = "--no-invoice",
            description = "Leave the rendered invoice out of the report.")
    private boolean noInvoice;

    @Option(order = 140, names = "--after-repair",
            description = "After a document was refused because its bytes are not written in"
                    + " the encoding it declares, run the checks again on the recoded bytes"
                    + " and print what they say. It is information and not a verdict: the"
                    + " document stays invalid. Text output only.")
    private boolean afterRepair;

    ValidateCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        boolean json = json(output);
        ReportForm form = reportForm();
        // Where the report has the standard output, the lines a person reads go to the
        // error stream: two things cannot share one stream, and the report is the one the
        // caller asked to receive there. Two machine forms on it are refused outright.
        boolean reportOnStandardOutput = form != null && Input.STDIN_ARGUMENT.equals(report);
        if (reportOnStandardOutput && json) {
            throw CliException.input("--report - and --output json both write to the standard"
                    + " output; name a file for one of them");
        }
        Console printer = reportOnStandardOutput ? console.toErrorStream() : console;
        ValidationLayer layer = Options.level(level);
        Extensions extensions = Options.extension(extension);
        boolean businessRules = Options.rules(rules);
        Deadline deadline = Deadline.of(console.options().maxRuntime());
        Pack chosen = deadline.within("reading the validation pack",
                () -> SyntaxPacks.resolve(pack));

        Input input = deadline.within(Input.label(file) + ": reading the input",
                () -> Input.read(file, console));
        // The verdict is about the bytes that were handed over, so the front door does
        // not repair them: a document that is not written in the encoding it declares is
        // reported as such and not quietly recoded into a passing one.
        Loaded loaded = deadline.within(input.name() + ": reading the document into the"
                        + " semantic model",
                () -> Loaded.validate(input, Options.from(from), extensions, console));
        loaded.reportNotes(console);
        SyntaxCheck syntax = noSyntax
                ? SyntaxCheck.notRun(SyntaxCheck.BY_OPTION)
                : SyntaxCheck.run(loaded.xml(input), loaded.syntax(), chosen, deadline,
                        console);
        Optional<Verapdf.Result> pdfa = pdfa(input, loaded, deadline);
        Validation.WrittenRequest written = written(loaded, chosen, extensions, deadline);
        Validation.RuleRequest request = businessRules
                ? Validation.RuleRequest.pack(chosen)
                : Validation.RuleRequest.notRun(RuleCheck.BY_OPTION);
        Validation.Report validation = deadline.within(
                input.name() + ": the structural layers and the business rules",
                () -> Validation.run(loaded, syntax, written, request, layer, extensions,
                        pdfa));

        if (json) {
            Reports.json(console, validation);
        } else {
            printer.line("Input:            " + validation.input());
            Reports.container(printer, validation);
            printer.line("Detected syntax:  " + validation.syntax().label());
            Reports.semanticModel(validation)
                    .ifPresent(model -> printer.line("Semantic model:   " + model));
            profile(printer, syntax);
            printer.line();
            Reports.text(printer, validation);
            Reports.verdicts(printer, validation);
            afterRepair(printer, input, request, layer, extensions, validation);
        }
        boolean delivered = form == null
                || writeReport(form, input, loaded, validation, extensions, deadline);
        Optional<Finding> limit = validation.limit();
        if (limit.isPresent()) {
            console.error(console.options().bounds()
                    .refusal(validation.input(), limit.orElseThrow().message()));
            return ExitCode.LIMIT;
        }
        int verdict = Validation.exitCode(validation);
        // A report the caller asked for and did not get is an output that was not written
        // in full, and code 0 is a claim that everything the caller asked for was
        // delivered. A verdict of invalid outranks it: it is the answer the caller came
        // for and it was printed, so it keeps its own code.
        return delivered || verdict == ExitCode.VALIDATION ? verdict : ExitCode.OUTPUT;
    }

    /**
     * Names the profile the document claims and the pack that was run against it.
     *
     * <p>The two belong on one line because neither is useful alone. The customization
     * identifier is what the document says about itself, and the pack is what this tool
     * did with that claim; a reader who is surprised by a finding needs to see both to
     * know whether the surprise is in the invoice or in the artefact that judged it.
     *
     * <p>The line is absent where the syntax engine did not run, because then no pack was
     * chosen, and a line that named one would be describing something that did not happen.
     */
    private static void profile(Console console, SyntaxCheck check) {
        check.report().ifPresent(report -> console.line("Profile:          "
                + (report.customizationId().isEmpty() ? "(none named)" : report.customizationId())
                + pack(report)));
    }

    /**
     * Returns the pack that judged the document, with where it came from.
     *
     * <p>The provenance is on the line because the identity alone cannot carry it. A
     * directory handed to {@code --pack} writes its own manifest, so it can call itself
     * the release this build carries; the caller who pointed at it knows what is in it,
     * and the person reading the report afterwards has nothing else to go on.
     */
    private static String pack(SyntaxReport report) {
        return report.pack().map(pack -> " (pack " + pack.directory()
                + (pack.source() == PackSource.BUNDLED ? "" : ", supplied with --pack")
                + ")").orElse("");
    }

    /**
     * Runs the checks a second time on the recoded bytes and prints what they say.
     *
     * <p>It answers a question the verdict does not: whether the document would pass if
     * its encoding were put right. It is the same run as the first — the same layers and
     * the same rule request — over the recoded bytes. The heading says that this is
     * information, the verdict line after it says that the document as it stands is
     * invalid, and the exit code is the one the first report reached.
     */
    private void afterRepair(Console console,
                             Input input,
                             Validation.RuleRequest request,
                             ValidationLayer layer,
                             Extensions extensions,
                             Validation.Report report) {
        if (!afterRepair || report.syntaxFindings().isEmpty()) {
            return;
        }
        console.line();
        console.line("after repair (informational)");
        try {
            Loaded recoded = Loaded.repaired(input, Options.from(from), extensions, console);
            SyntaxCheck none = SyntaxCheck.notRun(SyntaxCheck.AFTER_REPAIR);
            Validation.WrittenRequest unwritten = Validation.WrittenRequest.notRun(
                    Options.via(via), Coverage.Cause.NOT_RUN_BY_THIS_COMMAND,
                    WrittenCheck.NOT_ESJ);
            Validation.Report second =
                    Validation.run(recoded, none, unwritten, request, layer, extensions);
            Reports.text(console, second);
        } catch (CliException e) {
            console.line("  the recoded bytes could not be read either: " + e.getMessage());
        }
        console.line();
        console.line("Verdict:          INVALID (the bytes as they stand are not written in"
                + " the encoding they declare)");
    }

    /**
     * Runs the PDF/A validator the caller lent, where they lent one and the input is a
     * container.
     *
     * <p>It validates the bytes that were handed over, as everything else in this command
     * does: the file as it arrived, not the attachment and not a repaired form of it. A
     * run over an input that is no container has nothing to validate, and is told so
     * rather than left to wonder why the report carries no verdict.
     *
     * <p>The validator runs inside what is left of {@code --max-runtime}, like every other
     * step, so a caller who bounded this run bounded the process it starts as well.
     */
    private Optional<Verapdf.Result> pdfa(Input input, Loaded loaded, Deadline deadline) {
        if (verapdf == null) {
            return Optional.empty();
        }
        if (loaded.container().isEmpty()) {
            console.warning("--verapdf validates the PDF/A conformance of a container, and "
                    + input.name() + " is not one; nothing was validated");
            return Optional.empty();
        }
        String step = input.name() + ": validating the PDF/A conformance";
        return Optional.of(Verapdf.validate(verapdf, input.bytes(),
                deadline.remaining(step), console));
    }

    /**
     * Writes the report.
     *
     * <p>The report is written after the lines, so that a caller who is watching the run
     * has the verdict before the file lands, and it is written from the same
     * {@link Validation.Report} the lines were written from: {@link Outcome} carries it
     * over, and nothing is judged a second time.
     *
     * <p>It is the last thing that may be given up. {@code --max-runtime} covers this step
     * like every other, because drawing a report about a document built to be expensive
     * costs real time; but a bound met <em>here</em> is met after the verdict, and a
     * verdict already reached is not unreached by the file about it running out of time.
     * So this step alone answers a deadline by leaving the file out, with one line saying
     * so, while the printed lines stay what they were. A bound met before the verdict is
     * the other thing and is still {@link ExitCode#LIMIT} with no verdict.
     *
     * <p>What the exit code does is the one thing that is not unchanged, because a caller
     * who asked for a file and received none has not had what it asked for: the answer is
     * {@link ExitCode#OUTPUT} unless the verdict is {@link ExitCode#VALIDATION}, which is
     * the answer the caller came for and was printed. The caller that watches the lines
     * reads the verdict; the caller that branches on the code is told that something is
     * missing.
     *
     * <p>A destination that cannot be written is the other way the file does not arrive,
     * and it is answered by the same rule rather than by the exit code of the write: the
     * write is tried after the verdict was printed, so the run has an answer about the
     * document either way, and the code says which of the two is missing.
     *
     * @return whether the report reached its destination
     */
    private boolean writeReport(ReportForm form,
                             Input input,
                             Loaded loaded,
                             Validation.Report validation,
                             Extensions extensions,
                             Deadline deadline) {
        ReportOptions options = new ReportOptions(language(reportLang), !noInvoice,
                Optional.ofNullable(reportTime),
                Options.pageSize("--report-page", reportPage));
        // A path is an address relative to an edition, so the pages of the invoice are
        // drawn with the registry of the edition the document itself names. Where this
        // build carries none, the report is the check table and the findings alone: the
        // identity row and the reason of the verdict already name the edition.
        Optional<SemanticDocument> built = loaded.document();
        Registry registry = built.flatMap(judged -> Editions.forDocument(judged, extensions))
                .orElseGet(() -> Validation.registry(extensions));
        ReportRenderer renderer = new ReportRenderer(registry);
        SemanticDocument document = built
                .filter(judged -> registry.describes(judged.semanticModel()))
                .orElse(null);
        byte[] bytes;
        try {
            bytes = deadline.within(input.name() + ": writing the report", () -> {
                ValidationOutcome outcome = Outcome.of(input, loaded, validation);
                return form == ReportForm.PDF
                        ? renderer.pdf(outcome, document, options)
                        : renderer.html(outcome, document, options)
                                .getBytes(StandardCharsets.UTF_8);
            });
        } catch (CliException e) {
            if (e.exitCode() != ExitCode.LIMIT) {
                throw e;
            }
            console.error("no report was written: the " + deadline.total().toMillis()
                    + " ms this run was given had gone by while it was being drawn. The"
                    + " verdict was reached before that and stands and is printed above;"
                    + " this run leaves with exit code " + ExitCode.OUTPUT + " because the"
                    + " report the caller asked for was not delivered, unless the verdict"
                    + " is invalid and keeps its own code. --max-runtime gives the report"
                    + " more time.");
            return false;
        }
        try {
            Output.write(report, bytes, console);
        } catch (CliException e) {
            if (e.exitCode() != ExitCode.OUTPUT) {
                throw e;
            }
            // The destination is the other way the file does not reach the caller, and it
            // is answered the same way: the verdict was reached and printed before the
            // write was tried, so a run that found the document invalid keeps the code
            // that says so, and every other run says that an output is missing.
            console.error(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Returns the form the report is written in, or {@code null} where none was asked for.
     *
     * <p>The name decides where the name says it, because a caller who wrote
     * {@code report.pdf} has said which form they want. {@code --report-format} says it
     * where no name can — the standard output, or a name of any other ending — and a
     * {@code --report-format} that contradicts the name is refused rather than obeyed: a
     * file called {@code report.html} that holds a PDF is a file whose reader is misled by
     * this tool, and the caller wrote two answers to one question.
     */
    private ReportForm reportForm() {
        if (report == null) {
            if (reportFormat != null || reportTime != null || reportPage != null
                    || noInvoice) {
                console.warning("--report-format, --report-page, --report-time and"
                        + " --no-invoice describe a report; without --report none is"
                        + " written");
            }
            return null;
        }
        ReportForm named = named(report);
        if (reportFormat == null) {
            if (named == null) {
                throw CliException.input("cannot tell what form '" + report + "' is: name it"
                        + " .html or .pdf, or write --report-format html or"
                        + " --report-format pdf");
            }
            return named;
        }
        ReportForm asked = form(reportFormat);
        if (named != null && named != asked) {
            throw CliException.input("--report " + report + " names a "
                    + named.token() + " report and --report-format says " + reportFormat
                    + "; the name decides what a file holds, so write one of the two");
        }
        return asked;
    }

    /** Returns the form the name of a file says, or {@code null} where it says none. */
    private static ReportForm named(String destination) {
        String name = destination.toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) {
            return ReportForm.HTML;
        }
        return name.endsWith(".pdf") ? ReportForm.PDF : null;
    }

    /** Returns the form a {@code --report-format} token names. */
    private static ReportForm form(String token) {
        if ("html".equals(token)) {
            return ReportForm.HTML;
        }
        if ("pdf".equals(token)) {
            return ReportForm.PDF;
        }
        throw CliException.input("--report-format takes html or pdf, not '" + token + "'");
    }

    /** Returns the language a {@code --report-lang} token names. */
    private static RenderLanguage language(String token) {
        if ("de".equals(token)) {
            return RenderLanguage.GERMAN;
        }
        if ("en".equals(token)) {
            return RenderLanguage.ENGLISH;
        }
        throw CliException.input("--report-lang takes de or en, not '" + token + "'");
    }

    /** The two forms a report is written in. */
    private enum ReportForm {

        /** One self-contained HTML page. */
        HTML("html"),

        /** A PDF, with the invoice after the report in the same file. */
        PDF("pdf");

        private final String token;

        ReportForm(String token) {
            this.token = token;
        }

        /** Returns the token this form is named by on the command line. */
        String token() {
            return token;
        }
    }

    /**
     * Returns whether the official artefacts run over the XML an ESJ document is written
     * to, which is the only way they can read a document that never was XML.
     *
     * <p>Two things the caller decides are settled here: an input that arrived as XML was
     * judged as it arrived and needs no rendering of itself, and {@code --no-syntax} takes
     * the artefacts out wherever they would have run. Everything else waits for the
     * structural layers, because a document they could not measure is a document nothing
     * says how to write.
     *
     * @param loaded     the document, read
     * @param pack       the pack the run was given, or {@code null} for the bundled ones
     * @param extensions the extension registries this run loaded, which say whether a term
     *                   the writer leaves behind was meant to travel at all
     * @param deadline   what is left of the time the command was given
     * @return the request the structural layers answer
     */
    private Validation.WrittenRequest written(Loaded loaded,
                                              Pack pack,
                                              Extensions extensions,
                                              Deadline deadline) {
        String target = Options.via(via);
        if (loaded.syntax() != InputSyntax.ESJ) {
            return Validation.WrittenRequest.notRun(target,
                    Coverage.Cause.NOT_RUN_BY_THIS_COMMAND, WrittenCheck.NOT_ESJ);
        }
        if (noSyntax) {
            return Validation.WrittenRequest.notRun(target,
                    Coverage.Cause.SKIPPED_BY_CALLER, WrittenCheck.BY_OPTION);
        }
        return (document, gap, blocked) -> {
            if (document.isEmpty()) {
                return WrittenCheck.notRun(target, Coverage.Cause.NOT_RUN_BY_THIS_COMMAND,
                        WrittenCheck.NO_DOCUMENT);
            }
            if (gap.isPresent()) {
                return WrittenCheck.notRun(target, gap.orElseThrow(),
                        WrittenCheck.NOT_MEASURED);
            }
            if (blocked.isPresent()) {
                return WrittenCheck.notRun(target, Coverage.Cause.NOT_RUN_BY_THIS_COMMAND,
                        WrittenCheck.AFTER_MODEL);
            }
            return artefacts(loaded, document.orElseThrow(), pack, extensions, deadline);
        };
    }

    /**
     * Writes the document and runs the official artefacts of its profile over the result.
     *
     * <p>Four things stop it short of a verdict, and none of them is a defect of the
     * invoice. The writer may have found no place in the syntax for something the document
     * states, in which case the XML is a different document and an artefact's verdict over
     * it would be about something else; the syntax may require an element or an attribute
     * the document does not fill and the binding table states no value for, in which case
     * the writer had to leave it out or write it empty and what an artefact then says is
     * about the rendition and not about the invoice; the pack may carry no artefact for the
     * profile the
     * document names, which is a property of this build; and the written XML may be larger
     * than a bound of this run allows — the bytes the writer may produce, or the bytes the
     * syntax engine accepts — which is a property of the bound and of the syntax rather
     * than of the document, because a written document is several times the size of the
     * ESJ it comes from. All four leave the row not applicable and the command
     * {@code INDETERMINATE}, with the reason in the report — the last of them rather than
     * ending the whole command at {@link ExitCode#LIMIT} and throwing away the verdict the
     * reader, the structural layers and the native rules had already reached.
     *
     * <p>A limit of the clock is not among them. A run that ran out of time reached no
     * verdict at all, so it keeps {@link ExitCode#LIMIT}, which is the one exit code of
     * this tool that means a limit and nothing about the invoice.
     *
     * <p>The first of the four has one exception, which the writer names in its report,
     * handed the extension registries of this run, and {@link Transport} reads: a term
     * whose registry declares {@code "transport": "none"} was never meant to reach a
     * syntax, so the XML written without it is the whole invoice and the artefacts answer
     * for the invoice. The row then runs and names those terms beside its result.
     */
    private WrittenCheck artefacts(Loaded loaded,
                                   SemanticDocument document,
                                   Pack pack,
                                   Extensions extensions,
                                   Deadline deadline) {
        WriteResult result;
        try {
            result = write(document, extensions);
        } catch (BindingEditionException e) {
            // A binding table is written against one edition, so a document of another has
            // no XML form this build can produce and the artefacts have nothing to read.
            // It is a property of this build, like the rule pack of another edition, and
            // the document keeps every answer the layers before this row gave it.
            return WrittenCheck.notRun(Options.via(via), Coverage.Cause.NO_PACK_FOR_EDITION,
                    "not run (no binding table for this edition)");
        } catch (BindingLimitException e) {
            return WrittenCheck.notRun(Options.via(via), Coverage.Cause.WRITTEN_OVER_BOUND,
                    "not run (" + console.options().bounds()
                            .refusal("the document written as " + Options.via(via),
                                    e.getMessage()) + ")");
        }
        WriteReport wrote = result.report();
        String written = WrittenCheck.target(wrote.syntax());
        Optional<List<WrittenCheck.ByDesign>> byDesign = Transport.byDesign(wrote);
        if (byDesign.isEmpty()) {
            return WrittenCheck.notRun(written, Coverage.Cause.TERM_NOT_IN_SYNTAX,
                    WrittenCheck.incomplete(wrote));
        }
        if (!wrote.notes(WriteNote.Kind.TERM_NOT_STATED).isEmpty()) {
            return WrittenCheck.notRun(written, Coverage.Cause.TERM_NOT_STATED,
                    WrittenCheck.notStated(wrote));
        }
        if (!wrote.notes(WriteNote.Kind.ELEMENT_NOT_STATED).isEmpty()) {
            return WrittenCheck.notRun(written, Coverage.Cause.ELEMENT_NOT_IN_MODEL,
                    WrittenCheck.unstated(wrote));
        }
        String name = loaded.name() + " written as " + written;
        try {
            return WrittenCheck.ran(written,
                    SyntaxCheck.run(result.xml(), name, pack, deadline, console),
                    byDesign.orElseThrow());
        } catch (CliException e) {
            if (overBound(e)) {
                return WrittenCheck.notRun(written, Coverage.Cause.WRITTEN_OVER_BOUND,
                        "not run (" + e.getMessage() + ")");
            }
            if (e.exitCode() != ExitCode.UNSUPPORTED) {
                throw e;
            }
            return WrittenCheck.notRun(written, Coverage.Cause.NO_RULES_FOR_PROFILE,
                    "not applicable (" + e.getMessage() + ")");
        }
    }

    /**
     * Tells whether the syntax engine stood down because the written XML was larger than
     * this run accepts, rather than for any other reason it reaches a limit for.
     *
     * <p>The engine reaches {@link ExitCode#LIMIT} for two bounds, and only one of them is
     * a bound on the written document: the other is the clock, which the whole command
     * shares and which leaves no verdict for any row to keep.
     * {@link de.bsnsoft.esj.syntax.SyntaxLimitException#budget()} is what tells
     * the two apart, and a limit the deadline itself raised carries no such exception at
     * all.
     *
     * <p>It is package-private so that a test can hold the two exceptions side by side
     * without having to make a document outlast a clock in the step this is asked in,
     * which is not a thing a test can time.
     */
    static boolean overBound(CliException e) {
        return e.exitCode() == ExitCode.LIMIT
                && e.getCause() instanceof SyntaxLimitException limit
                && limit.budget().isEmpty();
    }

    /**
     * Writes the document in the syntax {@code --via} names, within the bounds of this run.
     *
     * @throws BindingLimitException if the written document is longer than
     *                               {@code --max-output-bytes} allows this run, which the
     *                               caller reports as a row that did not run rather than as
     *                               the end of the command
     */
    private WriteResult write(SemanticDocument document, Extensions extensions) {
        String target = Options.via(via);
        WriterOptions options = WriterOptions.builder()
                .maxOutputBytes(console.options().bounds().maxOutputBytes())
                .extensions(extensions.registries())
                .build();
        try {
            return Options.VIA_UBL.equals(target)
                    ? UblWriter.writeWithReport(document, options)
                    : CiiWriter.writeWithReport(document, options);
        } catch (BindingFormatException | BindingSyntaxException e) {
            throw CliException.input("cannot write the document as " + target + ": "
                    + e.getMessage(), e);
        }
    }

    private static boolean json(String token) {
        if ("json".equals(token)) {
            return true;
        }
        if ("text".equals(token)) {
            return false;
        }
        throw CliException.input("--output takes text or json, not '" + token + "'");
    }
}
