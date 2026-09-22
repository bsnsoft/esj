package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj list}: every value of a document, one per line, in canonical path order.
 *
 * <p>This is the form that makes a semantic invoice usable from a shell. The three
 * columns of the tab-separated form are the path, the semantic data type the registry
 * records for the term, and the content of the value; a value that carries a line break is
 * written on one line with the escapes of {@link ValueText#oneLine(String)}, because a
 * line-oriented format that a value can break is not a format. The JSON form carries the
 * same three fields and needs no escaping of its own.
 *
 * <p>The datatype is not in the document. A document carries the content alone, and which
 * semantic data type it belongs to is what the registry says about the term the path names
 * (specification, section 6.2), so a term no loaded registry knows gets a dash in that
 * column rather than a guess.
 *
 * <p>Neither form carries the supplementary components of a value — the scheme of an
 * identifier, the file name of an attachment. {@code esj get --json} gives the whole
 * value for one path.
 */
@Command(name = "list",
        description = "List every semantic path of a document with the semantic data type of"
                + " its term and its content.",
        sortOptions = false)
final class ListCommand implements Callable<Integer> {

    /** What stands in the datatype column for a term no loaded registry describes. */
    private static final String UNKNOWN_DATATYPE = "-";

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<file|->",
            description = "The document to read, or - for the standard input.")
    private String file;

    @Option(order = 10, names = "--format", paramLabel = "<tsv|json>", defaultValue = "tsv",
            description = "tsv writes path, datatype and value separated by tabs; json writes"
                    + " an array of objects with those three members.")
    private String format;

    @Option(order = 20, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read the input as this syntax instead of recognizing it.")
    private String from;

    @Option(order = 30, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are imported as"
                    + " values instead of being reported as unknown. Two names"
                    + " separated by a comma load both.")
    private String extension;

    ListCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        boolean json = json(format);
        Input input = Input.read(file, console);
        Loaded loaded = Loaded.read(input, Options.from(from), Options.extension(extension),
                console);
        loaded.reportNotes(console);
        SemanticDocument document = loaded.require(console);
        Optional<Registry> registry =
                Editions.forDocument(document, Options.extension(extension));

        if (json) {
            Json.write(console, generator -> {
                generator.writeStartArray();
                for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
                    generator.writeStartObject();
                    generator.writeStringField("path", entry.getKey().toString());
                    generator.writeStringField("datatype", datatype(registry, entry.getKey()));
                    generator.writeStringField("value", entry.getValue().canonicalContent());
                    generator.writeEndObject();
                }
                generator.writeEndArray();
            });
        } else {
            for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
                console.line(entry.getKey() + "\t" + datatype(registry, entry.getKey()) + "\t"
                        + ValueText.oneLine(entry.getValue().canonicalContent()));
            }
        }
        return ExitCode.SUCCESS;
    }

    /**
     * Returns the semantic data type the registry records for the term a path names, or
     * {@link #UNKNOWN_DATATYPE} where no loaded registry knows that term.
     *
     * <p>The registry is the one of the edition the document names, and there may be none:
     * a path is an address relative to an edition, and this command lists the paths of a
     * document whichever edition it names. Where the edition is one this build carries no
     * registry of, every row carries the dash and the paths and values are still listed —
     * which is what a reader of a document from the future needs.
     */
    private static String datatype(Optional<Registry> registry, SemanticPath path) {
        return registry.flatMap(loaded -> loaded.term(path.term()))
                .flatMap(Term::datatype)
                .map(SemanticType::registryDatatype)
                .orElse(UNKNOWN_DATATYPE);
    }

    private static boolean json(String token) {
        if ("json".equals(token)) {
            return true;
        }
        if ("tsv".equals(token)) {
            return false;
        }
        throw CliException.input("--format takes tsv or json, not '" + token + "'");
    }
}
