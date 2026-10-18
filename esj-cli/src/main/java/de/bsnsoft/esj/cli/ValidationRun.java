package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.BindingEditionException;
import de.bsnsoft.esj.bindings.BindingFormatException;
import de.bsnsoft.esj.bindings.BindingSyntaxException;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.UblWriter;
import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.bindings.WriteReport;
import de.bsnsoft.esj.bindings.WriteResult;
import de.bsnsoft.esj.bindings.WriterOptions;
import de.bsnsoft.esj.internal.report.ValidationOutcome;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.render.internal.ReportOptions;
import de.bsnsoft.esj.render.internal.ReportRenderer;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One validation of {@code esj validate}: the strict read of an input, the two engines over
 * it, and the report drawn from what they found.
 *
 * <p>{@code esj validate} runs it once and prints what it came to; {@code esj view --report}
 * runs the same validation, so that the verdict on the page, its exit code and the report
 * beside it are the ones {@code esj validate} reaches for the same file, byte for byte. The
 * options are the ones a run of {@code esj validate} was given; the page takes the defaults.
 */
final class ValidationRun {

    private final Console console;
    private final String from;
    private final Extensions extensions;
    private final String via;
    private final boolean noSyntax;
    private final boolean businessRules;
    private final ValidationLayer layer;

    /**
     * What one validation came to.
     *
     * @param loaded the document, read strictly
     * @param syntax what the syntax engine did with the XML
     * @param report what every engine found
     */
    record Result(Loaded loaded, SyntaxCheck syntax, Validation.Report report) {
    }

    /**
     * Prepares a validation with the options of a run.
     *
     * @param console       the streams of this run
     * @param from          the {@code --from} token, or {@code null}
     * @param extensions    the extension registries this run loaded
     * @param via           the {@code --via} token
     * @param noSyntax      whether {@code --no-syntax} leaves the official artefacts out
     * @param businessRules whether the native rules run
     * @param layer         the highest structural layer to run
     */
    ValidationRun(Console console, String from, Extensions extensions, String via,
                  boolean noSyntax, boolean businessRules, ValidationLayer layer) {
        this.console = console;
        this.from = from;
        this.extensions = extensions;
        this.via = via;
        this.noSyntax = noSyntax;
        this.businessRules = businessRules;
        this.layer = layer;
    }

    /**
     * Reads an input into the semantic model the way a validation reads it.
     *
     * <p>The verdict is about the bytes that were handed over, so the front door does not
     * repair them: a document that is not written in the encoding it declares is reported as
     * such and not quietly recoded into a passing one.
     *
     * @param input    the input
     * @param deadline what is left of the time the command was given
     * @return the document, read strictly
     */
    Loaded load(Input input, Deadline deadline) {
        return deadline.within(input.name() + ": reading the document into the semantic model",
                () -> Loaded.validate(input, Options.from(from), extensions, console));
    }

    /**
     * Runs the engines over a document that was read.
     *
     * @param input    the input
     * @param loaded   the document, read by {@link #load}
     * @param chosen   the pack the run was given, or the packs the document chooses among
     * @param pdfa     the PDF/A validation of the container, run after the syntax engine
     * @param deadline what is left of the time the command was given
     * @return what every engine found
     */
    Result check(Input input,
                 Loaded loaded,
                 PackChoice chosen,
                 Supplier<Optional<Verapdf.Result>> pdfa,
                 Deadline deadline) {
        SyntaxCheck syntax = noSyntax
                ? SyntaxCheck.notRun(SyntaxCheck.BY_OPTION)
                : SyntaxCheck.run(loaded.xml(input), loaded.syntax(), chosen, deadline,
                        console);
        Optional<Verapdf.Result> container = pdfa.get();
        Validation.WrittenRequest written = written(loaded, chosen, extensions, deadline);
        Validation.RuleRequest request = businessRules
                ? Validation.RuleRequest.pack(chosen)
                : Validation.RuleRequest.notRun(RuleCheck.BY_OPTION);
        Validation.Report report = deadline.within(
                input.name() + ": the structural layers and the business rules",
                () -> Validation.run(loaded, syntax, written, request, layer, extensions,
                        container));
        return new Result(loaded, syntax, report);
    }

    /**
     * Draws the report of a validation, in the form a person keeps.
     *
     * <p>A path is an address relative to an edition, so the pages of the invoice are drawn
     * with the registry of the edition the document itself names. Where this build carries
     * none, the report is the check table and the findings alone: the identity row and the
     * reason of the verdict already name the edition.
     *
     * @param pdf        whether the report is a PDF rather than an HTML page
     * @param input      the input
     * @param loaded     the document, as the validation read it
     * @param validation what every engine found
     * @param extensions the extension registries this run loaded
     * @param deadline   what is left of the time the command was given
     * @param options    the language of the report, whether the invoice is in it, the
     *                   moment and the paper
     * @return the bytes of the report
     * @throws CliException with {@link ExitCode#LIMIT} where the deadline passed while it was
     *                      being drawn, or where a PDF report reached its bound on pages
     */
    static byte[] report(boolean pdf,
                         Input input,
                         Loaded loaded,
                         Validation.Report validation,
                         Extensions extensions,
                         Deadline deadline,
                         ReportOptions options) {
        Optional<SemanticDocument> built = loaded.document();
        Registry registry = built.flatMap(judged -> Editions.forDocument(judged, extensions))
                .orElseGet(() -> Validation.registry(extensions));
        ReportRenderer renderer = new ReportRenderer(registry);
        SemanticDocument document = built
                .filter(judged -> registry.describes(judged.semanticModel()))
                .orElse(null);
        try {
            return deadline.within(input.name() + ": writing the report", () -> {
                ValidationOutcome outcome = Outcome.of(input, loaded, validation);
                return pdf
                        ? renderer.pdf(outcome, document, options)
                        : renderer.html(outcome, document, options)
                                .getBytes(StandardCharsets.UTF_8);
            });
        } catch (EsjLimitException e) {
            throw ValidateCommand.reportPagesReached(e, options.rendering().maxPages());
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
     * @param pack       the pack the run was given, or the packs the document chooses among
     * @param extensions the extension registries this run loaded, which say whether a term
     *                   the writer leaves behind was meant to travel at all
     * @param deadline   what is left of the time the command was given
     * @return the request the structural layers answer
     */
    private Validation.WrittenRequest written(Loaded loaded,
                                              PackChoice pack,
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
                                   PackChoice pack,
                                   Extensions extensions,
                                   Deadline deadline) {
        WriteResult result;
        try {
            result = write(document, extensions);
        } catch (BindingEditionException e) {
            // A binding table is written against one edition, so a document of another has
            // no XML form this build can produce and the artefacts have nothing to read.
            // For the 2026 edition that is not a gap of this build: no syntax binding of
            // the terms it adds is published, and no artefact release is written against
            // it either. The document keeps every answer the layers before this row gave
            // it, and the run ends in the third state with the cause that says so.
            return WrittenCheck.notRun(Options.via(via), Coverage.Cause.NO_ARTEFACTS_FOR_EDITION,
                    "not run (no syntax binding is published for this edition)");
        } catch (EsjLimitException e) {
            // Not a refusal of the command: the document written by this run was longer
            // than the run allows, so the row of the official artefacts did not run.
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
            if (ValidateCommand.overBound(e)) {
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
     * Writes the document in the syntax {@code --via} names, within the bounds of this run.
     *
     * @throws EsjLimitException     if the written document is longer than
     *                               {@code --max-output-bytes} allows this run, which the
     *                               caller reports as a row that did not run rather than as
     *                               the end of the command
     */
    private WriteResult write(SemanticDocument document, Extensions extensions) {
        String target = Options.via(via);
        WriterOptions options = WriterOptions.defaults()
                .withMaxOutputBytes(console.options().bounds().maxOutputBytes())
                .withExtensions(extensions.registries());
        try {
            return Options.VIA_UBL.equals(target)
                    ? UblWriter.writeWithReport(document, options)
                    : CiiWriter.writeWithReport(document, options);
        } catch (BindingFormatException | BindingSyntaxException e) {
            throw CliException.input("cannot write the document as " + target + ": "
                    + e.getMessage(), e);
        }
    }

}
