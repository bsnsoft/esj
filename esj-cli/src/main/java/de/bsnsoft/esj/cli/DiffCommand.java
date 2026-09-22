package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.Canonicalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj diff}: where two invoices differ, in the semantic model rather than in a
 * syntax.
 *
 * <p>The two inputs may be written in different syntaxes — that is the point of the
 * command. A UBL invoice and the CII invoice that is supposed to carry the same content
 * produce the same semantic paths and the same canonical values where the two agree, so
 * what is left after this command is exactly what one of the two files says and the other
 * does not.
 *
 * <p>The two semantic digests go to the error stream, because they are about the
 * comparison rather than part of its result: a pipeline that reads the differences should
 * not have to skip two lines first. The two file names do not: {@code ---} and
 * {@code +++} head the differences on the standard output the way {@code diff(1)} and
 * {@code git diff} head theirs, because a report redirected to a file or paged with
 * {@code less} has to say by itself which side is which.
 */
@Command(name = "diff",
        description = "Compare two documents in the semantic model and print the paths at"
                + " which they differ.",
        sortOptions = false)
final class DiffCommand implements Callable<Integer> {

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<a>",
            description = "The first document, or - for the standard input.")
    private String first;

    @Parameters(index = "1", paramLabel = "<b>",
            description = "The second document, or - for the standard input.")
    private String second;

    @Option(order = 10, names = "--summary",
            description = "Print how many paths differ instead of which ones.")
    private boolean summary;

    @Option(order = 20, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read both inputs as this syntax instead of recognizing them.")
    private String from;

    @Option(order = 30, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are imported as"
                    + " values instead of being reported as unknown. Two names"
                    + " separated by a comma load both.")
    private String extension;

    DiffCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        Extensions extensions = Options.extension(extension);
        InputSyntax syntax = Options.from(from);
        if (Input.STDIN_ARGUMENT.equals(first) && Input.STDIN_ARGUMENT.equals(second)) {
            // Both arguments would drain the same stream: the first takes everything and
            // the second finds it at its end. Said here, it is one sentence; left to the
            // reader, it is a command that waits for a terminal that will never answer, or
            // a complaint that the second document is of an unrecognized syntax.
            throw CliException.input("the standard input can be read only once");
        }
        SemanticDocument left = load(first, syntax, extensions);
        SemanticDocument right = load(second, syntax, extensions);

        console.diagnostic("semantic digest a: " + Canonicalizer.semanticDigest(left));
        console.diagnostic("semantic digest b: " + Canonicalizer.semanticDigest(right));
        editions(left, right);

        List<SemanticPath> differing = differingPaths(left, right);
        if (summary) {
            int onlyLeft = 0;
            int onlyRight = 0;
            int changed = 0;
            for (SemanticPath path : differing) {
                boolean inLeft = left.value(path).isPresent();
                boolean inRight = right.value(path).isPresent();
                if (!inRight) {
                    onlyLeft++;
                } else if (!inLeft) {
                    onlyRight++;
                } else {
                    changed++;
                }
            }
            console.line(differing.size() + " differing "
                    + (differing.size() == 1 ? "path" : "paths")
                    + ": " + onlyLeft + " only in a, " + onlyRight + " only in b, "
                    + changed + " with a different value");
        } else {
            if (!differing.isEmpty()) {
                console.line("--- " + label(first));
                console.line("+++ " + label(second));
            }
            for (SemanticPath path : differing) {
                left.value(path).ifPresent(value -> console.line("-" + path + " = "
                        + ValueText.oneLine(value.canonicalContent())));
                right.value(path).ifPresent(value -> console.line("+" + path + " = "
                        + ValueText.oneLine(value.canonicalContent())));
            }
        }
        return differing.isEmpty() ? ExitCode.SUCCESS : ExitCode.VALIDATION;
    }

    /**
     * Says, where the two documents name different editions of the semantic model, that
     * they do.
     *
     * <p>The comparison itself is right either way: it is over paths and canonical values,
     * and a canonicalizer is blind to the edition by construction. What is not right is
     * reading the result without knowing — a path the two editions place differently is
     * then reported as a value only one side carries, and the cause is the edition rather
     * than the invoice. The command compares them anyway and leaves the reading to its
     * caller, because comparing an invoice with its own upgrade is a reason to run it.
     */
    private void editions(SemanticDocument left, SemanticDocument right) {
        if (!left.semanticModel().equals(right.semanticModel())) {
            console.warning("a names the edition " + left.semanticModel() + " and b names "
                    + right.semanticModel() + "; a path that the two editions place"
                    + " differently is reported below as a difference");
        }
    }

    /** Returns what to call one side of the comparison in the header of the diff. */
    private static String label(String argument) {
        return Input.STDIN_ARGUMENT.equals(argument) ? Input.STDIN_NAME : argument;
    }

    private SemanticDocument load(String argument, InputSyntax syntax,
                                  Extensions extensions) {
        Loaded loaded = Loaded.read(Input.read(argument, console), syntax, extensions, console);
        loaded.reportNotes(console);
        return loaded.require(console);
    }

    /**
     * Returns the paths at which the two documents disagree, in canonical path order: a
     * path only one of them carries, or one at which the two values are not equal.
     */
    private static List<SemanticPath> differingPaths(SemanticDocument left,
                                                     SemanticDocument right) {
        SortedSet<SemanticPath> all = new TreeSet<>(SemanticPath.canonicalOrder());
        all.addAll(left.values().keySet());
        all.addAll(right.values().keySet());

        List<SemanticPath> differing = new ArrayList<>();
        for (SemanticPath path : all) {
            SemanticValue a = left.values().get(path);
            SemanticValue b = right.values().get(path);
            if (!Objects.equals(a, b)) {
                differing.add(path);
            }
        }
        return List.copyOf(differing);
    }
}
