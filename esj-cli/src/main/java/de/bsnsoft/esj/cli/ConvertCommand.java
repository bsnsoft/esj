package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.BindingEditionException;
import de.bsnsoft.esj.bindings.BindingException;
import de.bsnsoft.esj.bindings.BindingLimitException;
import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.bindings.BindingTable;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.UblWriter;
import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.bindings.WriteReport;
import de.bsnsoft.esj.bindings.WriteResult;
import de.bsnsoft.esj.bindings.WriterOptions;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.xr.XrSyntax;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj convert}: read a document in any syntax this tool knows and write it as ESJ
 * or as a cross industry invoice.
 *
 * <p>Three targets are written. {@code --to esj} is the semantic document, in either of the
 * two serializations of the specification, section 7. {@code --to cii} is a UN/CEFACT CII
 * D16B invoice and {@code --to ubl} an OASIS UBL 2.1 Invoice or Credit Note, both built from
 * the same binding tables the reader matches documents against;
 * {@code conformance/writers/cii-roundtrip.md} and {@code ubl-roundtrip.md} measure what the
 * official validation artefacts say about every document those writers produce over the
 * conformance corpus and what survives reading the result back. Which of the two UBL
 * documents is written is a fact of the invoice type code BT-3, and the run says which it
 * wrote; {@code --ubl-document} names one of the two instead, for a caller whose receiver
 * expects one.
 *
 * <p>A syntax has no place for everything a semantic document may hold, and what it has no
 * place for is said rather than dropped in silence: every value the writer could not place
 * becomes a warning on the error stream, and {@code --output json} carries the same list as
 * data. That report is the reason {@code --out} exists — a caller that wants the report on
 * the standard output needs somewhere else for the document to go.
 *
 * <p>The other direction is said as well. A syntax may require an element that no business
 * term of this document states, the binding table says what this project writes there, and
 * every such value becomes an information line of its own: nothing was lost, the exit code
 * stays 0, and the caller learns what is in the file beyond what the invoice said.
 */
@Command(name = "convert",
        description = "Read a UBL, CII or ESJ document, or a PDF carrying one of them, and"
                + " write it as ESJ or as a cross industry invoice.",
        sortOptions = false)
final class ConvertCommand implements Callable<Integer> {

    /** How many distinct warning lines the error stream carries without {@code --verbose}. */
    private static final int NOTE_LINES = 20;

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<file|->",
            description = "The document to read, or - for the standard input.")
    private String file;

    @Option(order = 10, names = "--to", paramLabel = "<esj|cii|ubl>", defaultValue = "esj",
            description = "The syntax to write. ubl writes an Invoice or a Credit Note,"
                    + " whichever the invoice type code (BT-3) names.")
    private String to;

    @Option(order = 20, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read the input as this syntax instead of recognizing it.")
    private String from;

    @Option(order = 30, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are imported as"
                    + " values instead of being reported as unknown. Two names"
                    + " separated by a comma load both.")
    private String extension;

    @Option(order = 35, names = "--ubl-document", paramLabel = "<invoice|creditnote|auto>",
            defaultValue = "auto",
            description = "Which of the two UBL documents to write. auto, the default,"
                    + " reads the invoice type code (BT-3); the other two name the document"
                    + " and write it whatever BT-3 says. It has no meaning with --to cii or"
                    + " --to esj.")
    private String ublDocument;

    @Option(order = 40, names = "--out", paramLabel = "<file>",
            description = "Write the converted document to this file instead of to the"
                    + " standard output. An existing file is replaced.")
    private String out;

    @Option(order = 50, names = "--output", paramLabel = "<text|json>", defaultValue = "text",
            description = "How this run reports what it converted. text writes the warnings"
                    + " and what was written by convention to the error stream and nothing"
                    + " else; json writes a report of the conversion to the standard"
                    + " output and therefore needs --out.")
    private String output;

    @ArgGroup(exclusive = true)
    private Form form = new Form();

    ConvertCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        Target target = Target.ofToken(to);
        boolean json = json(output);
        if (json && out == null) {
            throw CliException.input("--output json writes the report to the standard output,"
                    + " so the converted document needs a file: add --out");
        }
        UblWriter.DocumentType document = Options.ublDocument(ublDocument);
        if (target != Target.UBL && document != UblWriter.DocumentType.AUTO) {
            throw CliException.input("--ubl-document chooses between the two UBL documents"
                    + " and says nothing about " + target.description(form) + "; leave it"
                    + " out with --to " + target.token());
        }
        if (target != Target.ESJ && form.named()) {
            throw CliException.input(form.option() + " chooses between the two ESJ"
                    + " serializations and says nothing about an XML syntax; leave it out"
                    + " with --to " + target.token());
        }

        Input input = Input.read(file, console);
        Loaded loaded = Loaded.read(input, Options.from(from), Options.extension(extension),
                console);
        if (!json) {
            loaded.reportNotes(console);
        }
        SemanticDocument semantic = loaded.require(console);

        WriteResult result = target == Target.ESJ ? null : write(target, semantic, document);
        byte[] converted = result == null ? form.writer().toBytes(semantic) : result.xml();
        WriteReport report = result == null ? null : result.report();

        deliver(converted);
        if (json) {
            Json.write(console, generator -> {
                generator.writeStartObject();
                generator.writeStringField("input", input.name());
                generator.writeStringField("detected", loaded.syntax().token());
                generator.writeStringField("importer",
                        loaded.importer().map(Importer::token).orElse("none"));
                generator.writeStringField("to", target.token());
                generator.writeStringField("wrote", report == null
                        ? target.description(form) : written(report));
                generator.writeStringField("out", out);
                generator.writeNumberField("bytes", converted.length);
                generator.writeNumberField("values", semantic.values().size());
                Reports.conversionJson(generator, report);
                generator.writeEndObject();
            });
        } else if (report != null) {
            conventions(report);
            levelShift(loaded, report, semantic);
            warn(report);
        }
        console.verbose("converted " + input.name() + " (" + loaded.syntax().label() + ", "
                + semantic.values().size() + " values) to "
                + (report == null ? target.description(form) : written(report)));
        return ExitCode.SUCCESS;
    }

    /**
     * Returns what the writer wrote, which for UBL is one of two documents.
     *
     * <p>A semantic document is one invoice; UBL is two document types and the invoice type
     * code BT-3 says which, so a caller that asked for {@code --to ubl} is told which it
     * received rather than left to look at the root element.
     */
    private static String written(WriteReport report) {
        return switch (report.syntax()) {
            case CII -> "a cross industry invoice";
            case UBL_INVOICE -> "a UBL invoice";
            case UBL_CREDIT_NOTE -> "a UBL credit note";
        };
    }

    /** Writes the document in the target syntax, within the bounds of this run. */
    private WriteResult write(Target target,
                              SemanticDocument document,
                              UblWriter.DocumentType type) {
        WriterOptions options = WriterOptions.builder()
                .maxOutputBytes(console.options().bounds().maxOutputBytes())
                .document(type)
                .build();
        try {
            return target == Target.CII ? CiiWriter.writeWithReport(document, options)
                    : UblWriter.writeWithReport(document, options);
        } catch (BindingLimitException e) {
            throw CliException.limit(console.options().bounds()
                    .refusal(target.description(form), e.getMessage()), e);
        } catch (BindingEditionException refused) {
            // A document of an edition the table does not bind is a request this version
            // does not serve rather than an input it could not read, and the way out is
            // the one esj upgrade offers. Both tables bind one edition, so both refuse.
            throw Editions.refuse(document, target == Target.CII
                    ? "the CII binding table binds "
                            + BindingTable.of(BindingSyntax.CII).semanticModel()
                    : "the UBL binding tables bind "
                            + BindingTable.of(BindingSyntax.UBL_INVOICE).semanticModel());
        } catch (BindingException e) {
            throw CliException.input("cannot write " + target.description(form) + ": "
                    + e.getMessage(), e);
        }
    }

    /** Writes the converted document where {@code --out} says, or to the standard output. */
    private void deliver(byte[] converted) {
        if (out == null) {
            console.bytes(converted);
            return;
        }
        try {
            Files.write(target(out), converted);
        } catch (IOException e) {
            throw CliException.output("cannot write " + out + ": " + e.getMessage(), e);
        }
        console.verbose("wrote " + converted.length + " bytes to " + out);
    }

    /** Returns the path {@code --out} names, refusing one the platform cannot hold. */
    private static Path target(String argument) {
        try {
            return Path.of(argument);
        } catch (InvalidPathException e) {
            throw CliException.input("not a path this platform accepts: " + argument, e);
        }
    }

    /**
     * Writes what the syntax had no place for to the error stream: a headline counting
     * what fell short, and the notes under it, collapsed by {@link #collapse} the way the
     * importer's are.
     */
    private void warn(WriteReport report) {
        if (report.isComplete()) {
            return;
        }
        List<WriteNote> shortfalls = report.notes().stream()
                .filter(note -> note.kind().isShortfall()).toList();
        console.warning(headline(report, shortfalls.size()));
        collapse(shortfalls, line -> console.diagnostic("  " + line));
    }

    /**
     * Says on the error stream what the writer put into the document that the invoice did
     * not state.
     *
     * <p>A syntax may require an element that no business term of the semantic model
     * names, or one whose term this document leaves out, and the binding table says what
     * this project writes there — {@code FC} at the tax scheme of a registration that is
     * not for value added tax, {@code NA} at the purchase order reference and at the card
     * network. Nothing of the document is lost by it, so the report is complete and the
     * run ends with 0; but the file the caller now holds says something the invoice did
     * not, and the caller hears that without asking for it. The line names the element,
     * the value, what asks for the element and where the value comes from, as the note
     * carries them.
     *
     * <p>It is an information line rather than a warning: it is not one of the shortfalls
     * the headline of {@link #warn(WriteReport)} counts, and it leaves the exit code where
     * it was.
     */
    private void conventions(WriteReport report) {
        collapse(report.notes(WriteNote.Kind.CONVENTION_APPLIED), console::information);
    }

    /**
     * Says where the profile of the document levels a rule of the standard more strictly
     * for the syntax that was written than for the syntax the document arrived in.
     *
     * <p>A conversion is the step after which the other table applies: whoever validates
     * the file this run wrote is entitled to the levels of its syntax, and those are not
     * obliged to be the levels the source was judged by ({@link LevelShift}). The line
     * names rules and says nothing about this invoice, because deciding whether it trips
     * one of them is a business rule check this command does not make.
     */
    private void levelShift(Loaded loaded, WriteReport report, SemanticDocument document) {
        XrSyntax target = targetSyntax(report);
        String profile = Validation.customizationId(document);
        List<String> codes = LevelShift.stricterInTarget(loaded.syntax().xrSyntax(), target,
                profile);
        if (!codes.isEmpty()) {
            console.information(LevelShift.line(codes,
                    loaded.syntax().xrSyntax().orElseThrow(), target));
        }
    }

    /** Returns the syntax a write report says was written. */
    private static XrSyntax targetSyntax(WriteReport report) {
        return switch (report.syntax()) {
            case CII -> XrSyntax.CII;
            case UBL_INVOICE -> XrSyntax.UBL_INVOICE;
            case UBL_CREDIT_NOTE -> XrSyntax.UBL_CREDIT_NOTE;
        };
    }

    /**
     * Collapses notes to one line per distinct sentence with a count and hands each line
     * to a writer.
     *
     * <p>One line per note was unreadable: a document that uses the XRechnung extension
     * produces hundreds of them — every business term inside every sub invoice line — and
     * a screenful of near-identical text is a channel a reader stops reading. So identical
     * sentences become one line with a count, the list is cut off after
     * {@link #NOTE_LINES}, and {@code --verbose} shows every note whole, with the semantic
     * path that distinguishes two notes of the same sentence.
     *
     * @param notes the notes to write, in the order the writer made them
     * @param write what to do with one finished line
     */
    private void collapse(List<WriteNote> notes, Consumer<String> write) {
        Map<String, Integer> collapsed = new LinkedHashMap<>();
        for (WriteNote note : notes) {
            collapsed.merge(console.options().verbose() ? note.toString()
                    : note.kind() + ": " + note.message(), 1, Integer::sum);
        }
        int printed = 0;
        for (Map.Entry<String, Integer> entry : collapsed.entrySet()) {
            if (!console.options().verbose() && printed == NOTE_LINES) {
                write.accept("... and " + (collapsed.size() - printed)
                        + " more; run with --verbose for all of them");
                return;
            }
            write.accept(entry.getKey()
                    + (entry.getValue() == 1 ? "" : " (" + entry.getValue() + " times)"));
            printed++;
        }
    }

    /**
     * Returns the first line of the warning: how many values had no place in the syntax,
     * or, where every value was written, how many observations the writer made all the
     * same. Not every note is a value lost — a supplementary component, a part of
     * {@code extensions} and a character the syntax cannot carry each leave the value
     * itself in the document — and a line that counted those as values would say zero and
     * then list them.
     */
    private static String headline(WriteReport report, int shortfalls) {
        if (report.dropped() == 0) {
            return shortfalls
                    + (shortfalls == 1
                            ? " observation about what the syntax has no place for"
                            : " observations about what the syntax has no place for");
        }
        return report.dropped()
                + (report.dropped() == 1 ? " value of the document has no place in this"
                        + " syntax and was not written"
                        : " values of the document have no place in this"
                                + " syntax and were not written");
    }

    /** Returns whether {@code --output} asked for the JSON report. */
    private static boolean json(String token) {
        if ("json".equals(token)) {
            return true;
        }
        if (token == null || "text".equals(token)) {
            return false;
        }
        throw CliException.input("--output takes text or json, not '" + token + "'");
    }

    /** The syntaxes {@code --to} names. */
    private enum Target {

        ESJ("esj"),
        CII("cii"),
        UBL("ubl");

        private final String token;

        Target(String token) {
            this.token = token;
        }

        static Target ofToken(String token) {
            for (Target target : values()) {
                if (target.token.equals(token)) {
                    return target;
                }
            }
            throw CliException.input("--to takes esj, cii or ubl, not '" + token + "'");
        }

        String token() {
            return token;
        }

        /** Returns what a message about this target calls the result. */
        String description(Form form) {
            return switch (this) {
                case CII -> "a cross industry invoice";
                case UBL -> "a UBL document";
                case ESJ -> "the " + form.name() + " ESJ form";
            };
        }
    }

    /** The two serializations of the specification, section 7; the pretty one is the default. */
    private static final class Form {

        @Option(order = 60, names = "--pretty",
                description = "Write the pretty ESJ form: two-space indentation, one member"
                        + " per line, a trailing line feed. This is the default.")
        private boolean pretty;

        @Option(order = 70, names = "--canonical",
                description = "Write the canonical ESJ bytes of the specification, section 7:"
                        + " no insignificant whitespace and no trailing line feed.")
        private boolean canonical;

        /** Returns the writer for the form that was asked for. */
        EsjWriter writer() {
            return canonical ? EsjWriter.canonical() : EsjWriter.pretty();
        }

        /** Tells whether either form was asked for by name. */
        boolean named() {
            return pretty || canonical;
        }

        /** Returns the option that was given, for a message about it. */
        String option() {
            return canonical ? "--canonical" : "--pretty";
        }

        /** Returns the name of that form, and whether it was chosen or fallen back to. */
        String name() {
            if (canonical) {
                return "canonical";
            }
            return pretty ? "pretty" : "pretty (the default)";
        }
    }
}
