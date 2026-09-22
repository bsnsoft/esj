package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.EsjException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj get}: one value, by its semantic path.
 *
 * <p>The plain form writes the canonical value string and nothing else, which is what a
 * shell wants: {@code total=$(esj get invoice.xml /BG-22/BT-112)}. The supplementary
 * components of a value — the scheme of an identifier, the file name of an attachment —
 * are not in that string, so {@code --json} writes the whole value object instead, in
 * the canonical form of the specification, section 7.
 *
 * <p>A path the document does not carry is not an error of the input: the document is
 * fine and the answer is that there is nothing there. It leaves with
 * {@link ExitCode#VALIDATION}, which is the code for a question that was understood and
 * answered with no — the same shape {@code grep} gives it. That third meaning of the code
 * is written down in {@code esj get --help}, in {@code esj --help} and in the exit code
 * table of {@code README.md}, because a code a script branches on has to be documented
 * everywhere the others are.
 *
 * <p>"Nothing there" has three causes, and the tool tells them apart rather than giving
 * one sentence to all of them: the term may not exist in EN 16931 at all, the path may
 * break the index rule of the specification, section 5.3 and address nothing that could
 * ever be written, or the document may simply not carry that term. The registry is loaded
 * and knows the difference, and the check that draws it is the L2 validator itself rather
 * than a second reading of the rules here.
 */
@Command(name = "get",
        description = "Print the value at one semantic path.",
        footerHeading = "%nExit codes:%n",
        footer = {
            "  0  the document carries a value at that path, which is on the standard output",
            "  1  the document carries no value at that path",
            "  2  the input could not be read, recognized or parsed",
            "  7  a resource or time limit of this run was reached; no verdict on the",
            "     document"},
        sortOptions = false)
final class GetCommand implements Callable<Integer> {

    /**
     * The model layer findings that are about the path itself rather than about the value
     * standing at it.
     */
    private static final Set<FindingCode> ABOUT_THE_PATH = EnumSet.of(
            FindingCode.ESJ_L2_UNKNOWN_TERM,
            FindingCode.ESJ_L2_PARENT_CHAIN,
            FindingCode.ESJ_L2_INDEX_REQUIRED,
            FindingCode.ESJ_L2_INDEX_FORBIDDEN);

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<file|->",
            description = "The document to read, or - for the standard input.")
    private String file;

    @Parameters(index = "1", paramLabel = "<path>",
            description = "The semantic path, for example /BT-1 or /BG-25/0/BT-129.")
    private String path;

    @Option(order = 10, names = "--json",
            description = "Print the whole value object in canonical form instead of the"
                    + " value string.")
    private boolean json;

    @Option(order = 20, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read the input as this syntax instead of recognizing it.")
    private String from;

    @Option(order = 30, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are imported as"
                    + " values instead of being reported as unknown. Two names"
                    + " separated by a comma load both.")
    private String extension;

    GetCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        SemanticPath wanted = parse(path);
        Input input = Input.read(file, console);
        Loaded loaded = Loaded.read(input, Options.from(from), Options.extension(extension),
                console);
        loaded.reportNotes(console);
        SemanticDocument document = loaded.require(console);

        Optional<SemanticValue> value = document.value(wanted);
        if (value.isEmpty()) {
            console.diagnostic(input.name() + " carries no value at " + wanted
                    + reason(wanted, document.semanticModel(), Options.extension(extension)));
            return ExitCode.VALIDATION;
        }
        if (json) {
            console.bytes(ValueText.canonicalObject(wanted, value.get()));
            console.print("\n");
        } else {
            console.bytes(value.get().canonicalContent().getBytes(StandardCharsets.UTF_8));
            console.print("\n");
        }
        return ExitCode.SUCCESS;
    }

    /**
     * Says why a path addresses nothing, where the reason is a property of the path and
     * not of this document.
     *
     * <p>The answer comes from the model layer of the validator, run over a document that
     * holds this path alone: the message a whole document would get for an unknown term or
     * for a broken index rule is the message one path gets here, in the same words and
     * from the same code. Only the findings about the path are kept — the placeholder
     * value put there to make a document has a type of its own, and what the validator has
     * to say about that type is about the placeholder and not about the question.
     *
     * <p>The question is asked of the edition the document names, because a path is an
     * address relative to one: a term the 2017 edition knows and a later one has moved is
     * absent from a document of that later edition for a reason the reader has to be
     * told. Where this build holds no registry of that edition, the validator says that
     * instead, in the same sentence it would give any other run.
     *
     * @param path          the path that addressed nothing
     * @param semanticModel the edition the document names
     * @param extension     the extension registries this run loads
     * @return a sentence to append, or the empty string where the path is sound and the
     *         document simply does not carry the term
     */
    private static String reason(SemanticPath path, String semanticModel,
                                 Extensions extension) {
        List<Finding> findings = StructuralValidator.validate(
                SemanticDocument.builder()
                        .semanticModel(semanticModel)
                        .put(path, SemanticValue.of("?"))
                        .build(),
                Editions.registries(extension),
                EnumSet.of(ValidationLayer.L2)).findings();
        return findings.stream()
                .filter(finding -> ABOUT_THE_PATH.contains(finding.code()))
                .findFirst()
                .map(finding -> ": " + finding.message())
                .orElse("");
    }

    private static SemanticPath parse(String text) {
        try {
            return SemanticPath.of(text);
        } catch (EsjException e) {
            throw CliException.input("not a semantic path: " + text + " (" + e.getMessage() + ")",
                    e);
        }
    }
}
