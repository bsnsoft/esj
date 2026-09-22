package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.upgrade.EditionUpgrade;
import de.bsnsoft.esj.upgrade.UpgradeNote;
import de.bsnsoft.esj.upgrade.UpgradeOptions;
import de.bsnsoft.esj.upgrade.UpgradeReport;
import de.bsnsoft.esj.upgrade.UpgradeResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj upgrade}: write an ESJ document as a document of another edition of the
 * semantic model.
 *
 * <p>What changes between two editions is data — {@code model/en16931/upgrade-2017-2026.json}
 * — and the engine is {@code EditionUpgrade}. This command is the process boundary around
 * it: it reads one document, hands the decisions the run must not take on its own to the
 * engine as options, writes the result where {@code --out} says, and turns the report into
 * text on the error stream or into JSON on the standard output.
 *
 * <p>Only an ESJ document is read. An importer produces the edition its syntax binds, so a
 * UBL or CII instance is converted first and upgraded afterwards, and the two steps stand
 * in the report of each.
 *
 * <p>The exit codes are those of {@link ExitCode}: 0 where a document was written, 1 where
 * the run refused because of what the document holds, 2 where the input is not an ESJ
 * document or already names the edition that was asked for, and 4 where this build carries
 * no registry of that edition or no mapping between the two.
 */
@Command(name = "upgrade",
        description = "Write an ESJ document as a document of another edition of the"
                + " semantic model.",
        sortOptions = false)
final class UpgradeCommand implements Callable<Integer> {

    /** How many distinct note lines the error stream carries without {@code --verbose}. */
    private static final int NOTE_LINES = 20;

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<file|->",
            description = "The ESJ document to write as another edition, or - for the"
                    + " standard input.")
    private String file;

    @Option(order = 10, names = "--to", paramLabel = "<edition>", required = true,
            description = "The edition to write: 2017 or 2026.")
    private String to;

    @Option(order = 20, names = "--out", paramLabel = "<file>",
            description = "Write the document to this file instead of to the standard"
                    + " output. An existing file is replaced.")
    private String out;

    @Option(order = 30, names = "--output", paramLabel = "<text|json>", defaultValue = "text",
            description = "How this run reports what it did. text writes the report to the"
                    + " error stream; json writes it to the standard output and therefore"
                    + " needs --out.")
    private String output;

    @Option(order = 40, names = "--specification", paramLabel = "<identifier>",
            description = "Write this identifier at BT-24. Without it the specification"
                    + " identifier of the document stands and is reported: which"
                    + " specification an invoice claims is not this tool's to decide.")
    private String specification;

    @Option(order = 50, names = "--drop", paramLabel = "<path>",
            description = "Allow the run to drop the values at this path and under it. A"
                    + " value the target edition has no address for makes the run refuse"
                    + " unless its path is named here; every dropped value stands in the"
                    + " report. Repeatable.")
    private List<String> drop = new ArrayList<>();

    @Option(order = 60, names = "--refuse-open-points",
            description = "Refuse where the run would leave an open point — a component"
                    + " the target edition requires and the document has not, a value"
                    + " dropped, a specification identifier left as it stands.")
    private boolean refuseOpenPoints;

    @Option(order = 70, names = "--partial",
            description = "Write the result although it does not satisfy the model of the"
                    + " target edition for a reason the mapping does not explain. Without"
                    + " it such a result is refused.")
    private boolean partial;

    @Option(order = 80, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that the result is checked with"
                    + " it where it was written against the target edition. Two names"
                    + " separated by a comma load both.")
    private String extension;

    @ArgGroup(exclusive = true)
    private Form form = new Form();

    UpgradeCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        boolean json = json(output);
        if (json && out == null) {
            throw CliException.input("--output json writes the report to the standard output,"
                    + " so the document needs a file: add --out");
        }
        // Before anything is read: an option the parser accepted and this command cannot
        // make sense of is a mistake in the command line, and it is answered as one
        // whatever the document and whatever editions this build carries.
        List<SemanticPath> droppable = new ArrayList<>();
        for (String path : drop) {
            droppable.add(dropped(path));
        }
        Input input = Input.read(file, console);
        if (input.isPdf() || InputDetector.detect(input.bytes())
                .filter(syntax -> syntax != InputSyntax.ESJ).isPresent()) {
            throw CliException.input(input.name() + " is not an ESJ document, and esj upgrade"
                    + " writes one edition of the semantic model as another; read it with"
                    + " esj convert --to esj first, then upgrade the result");
        }
        Loaded loaded = Loaded.read(input, InputSyntax.ESJ, Options.extension(extension),
                console);
        SemanticDocument document = loaded.require(console);
        UpgradeResult result = upgrade(document, input, droppable);

        UpgradeReport report = result.report();
        if (result.isUpgraded()) {
            byte[] written = form.writer().toBytes(result.require());
            deliver(written);
            if (json) {
                json(input, report, result, written.length);
            } else {
                warn(report);
            }
            console.verbose("wrote " + input.name() + " (" + document.values().size()
                    + " values) as " + report.to());
            return ExitCode.SUCCESS;
        }
        if (json) {
            json(input, report, result, 0);
        } else {
            refuse(report);
        }
        return ExitCode.VALIDATION;
    }

    /** Runs the engine, turning what this build cannot do into an exit code. */
    private UpgradeResult upgrade(SemanticDocument document, Input input,
                                  List<SemanticPath> droppable) {
        if (!EditionUpgrade.isAvailable(document.semanticModel(), to)) {
            throw refusalOf(document, input);
        }
        UpgradeOptions.Builder options = UpgradeOptions.builder()
                .source(input.bytes())
                .strict(refuseOpenPoints)
                .partial(partial);
        if (specification != null) {
            options.specification(specification);
        }
        for (SemanticPath path : droppable) {
            options.drop(path);
        }
        for (Registry registry : Options.extension(extension).registries()) {
            options.extension(registry);
        }
        return EditionUpgrade.apply(document, to, options.build());
    }

    /** Says why this build cannot write that document as that edition. */
    private CliException refusalOf(SemanticDocument document, Input input) {
        if (!Registry.editions().contains(to)) {
            return CliException.unsupported("this build carries no registry of the edition "
                    + to + "; --to takes " + Registry.editions());
        }
        if (Registry.forEdition(to).describes(document.semanticModel())) {
            return CliException.input(input.name() + " already names the edition "
                    + Registry.forEdition(to).edition());
        }
        List<String> targets = EditionUpgrade.targets(document.semanticModel());
        return CliException.unsupported("this build carries no mapping from "
                + document.semanticModel() + " to the edition " + to
                + (targets.isEmpty() ? "" : "; it can write that document as " + targets));
    }

    /** Returns the path {@code --drop} names, which may be a value path or a group path. */
    private static SemanticPath dropped(String argument) {
        try {
            return SemanticPath.of(argument);
        } catch (EsjFormatException e) {
            try {
                return SemanticPath.group(argument);
            } catch (EsjFormatException group) {
                throw CliException.input("--drop takes a semantic path, not '" + argument
                        + "': " + group.getMessage());
            }
        }
    }

    /** Writes the document where {@code --out} says, or to the standard output. */
    private void deliver(byte[] written) {
        if (out == null) {
            console.bytes(written);
            return;
        }
        try {
            Files.write(target(out), written);
        } catch (IOException e) {
            throw CliException.output("cannot write " + out + ": " + e.getMessage(), e);
        }
        console.verbose("wrote " + written.length + " bytes to " + out);
    }

    /** Returns the path {@code --out} names, refusing one the platform cannot hold. */
    private static Path target(String argument) {
        try {
            return Path.of(argument);
        } catch (InvalidPathException e) {
            throw CliException.input("not a path this platform accepts: " + argument, e);
        }
    }

    /** Writes what the run left to the caller to the error stream. */
    private void warn(UpgradeReport report) {
        List<UpgradeNote> open = report.openPoints();
        console.verbose(report.from() + " -> " + report.to() + ": " + report.rewritten()
                + " values moved to another address");
        if (open.isEmpty()) {
            return;
        }
        console.warning(open.size() + (open.size() == 1 ? " open point" : " open points")
                + " this upgrade leaves to you");
        lines(open);
    }

    /** Writes why nothing was written to the error stream. */
    private void refuse(UpgradeReport report) {
        List<UpgradeNote> refusals = report.refusals();
        console.error("the upgrade to " + report.to() + " was refused: " + refusals.size()
                + (refusals.size() == 1 ? " reason" : " reasons"));
        lines(refusals);
        if (refusals.stream().anyMatch(note -> note.kind() == UpgradeNote.Kind.UNMAPPED)) {
            console.diagnostic("  name the paths that may be dropped with --drop <path> to"
                    + " write the rest, and each of them will stand in the report");
        }
        if (refusals.stream().anyMatch(note -> note.kind() == UpgradeNote.Kind.SOURCE_INDEX)) {
            console.diagnostic("  esj validate names the same paths against the edition the"
                    + " document itself carries; this command writes addresses and repairs"
                    + " none");
        }
    }

    /**
     * Writes the notes, and the sentence the mapping states each point in once above the
     * paths it is about.
     */
    private void lines(List<UpgradeNote> notes) {
        List<String> said = new ArrayList<>();
        int printed = 0;
        for (UpgradeNote note : notes) {
            Optional<String> point = note.point();
            if (point.isPresent() && !said.contains(point.orElseThrow())) {
                said.add(point.orElseThrow());
            }
            if (!console.options().verbose() && printed == NOTE_LINES) {
                console.diagnostic("  ... and " + (notes.size() - printed)
                        + " more; run with --verbose for all of them");
                break;
            }
            console.diagnostic("  " + note);
            printed++;
        }
    }

    /** Writes the report as JSON on the standard output. */
    private void json(Input input, UpgradeReport report, UpgradeResult result, int bytes) {
        Json.write(console, generator -> {
            generator.writeStartObject();
            generator.writeStringField("input", input.name());
            generator.writeStringField("from", report.from());
            generator.writeStringField("to", report.to());
            generator.writeStringField("outcome",
                    result.isUpgraded() ? "upgraded" : "refused");
            if (result.isUpgraded()) {
                generator.writeStringField("out", out);
                generator.writeNumberField("bytes", bytes);
                generator.writeNumberField("values", result.require().values().size());
            }
            generator.writeNumberField("rewritten", report.rewritten());
            generator.writeNumberField("dropped", report.dropped());
            generator.writeNumberField("openPoints", report.openPoints().size());
            generator.writeObjectFieldStart("statements");
            for (Map.Entry<String, String> statement : report.statements().entrySet()) {
                generator.writeStringField(statement.getKey(), statement.getValue());
            }
            generator.writeEndObject();
            generator.writeArrayFieldStart("notes");
            for (UpgradeNote note : report.notes()) {
                generator.writeStartObject();
                generator.writeStringField("kind", note.kind().token());
                generator.writeStringField("severity",
                        note.severity().name().toLowerCase(java.util.Locale.ROOT)
                                .replace('_', '-'));
                if (note.path().isPresent()) {
                    generator.writeStringField("path", note.path().orElseThrow().toString());
                }
                if (note.point().isPresent()) {
                    generator.writeStringField("point", note.point().orElseThrow());
                }
                generator.writeStringField("message", note.message());
                generator.writeEndObject();
            }
            generator.writeEndArray();
            generator.writeEndObject();
        });
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

    /** The two serializations of the specification, section 7; the pretty one is the default. */
    private static final class Form {

        @Option(order = 90, names = "--pretty",
                description = "Write the pretty ESJ form: two-space indentation, one member"
                        + " per line, a trailing line feed. This is the default.")
        private boolean pretty;

        @Option(order = 100, names = "--canonical",
                description = "Write the canonical ESJ bytes of the specification,"
                        + " section 7: no insignificant whitespace and no trailing line"
                        + " feed.")
        private boolean canonical;

        /** Returns the writer for the form that was asked for. */
        EsjWriter writer() {
            return canonical ? EsjWriter.canonical() : EsjWriter.pretty();
        }
    }
}
