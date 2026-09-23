package de.bsnsoft.esj.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * The top level of the command tree: {@code esj}.
 *
 * <p>It carries almost no behaviour of its own. Run without a subcommand it prints its
 * usage text and leaves with {@link ExitCode#INPUT}, because a command line that names
 * nothing to do is a command line that was written wrong.
 *
 * <p>The exception is {@code --list-packs}, which belongs here rather than under a
 * subcommand because it is a question about the tool and not about a document: it takes
 * no input and answers what this build carries. It sits beside {@code --version} for the
 * same reason — both say what you have got.
 */
@Command(name = "esj",
        header = "Convert, validate, render and inspect EN 16931 invoices through the"
                + " semantic model.",
        description = {
            "",
            "esj reads a UBL 2.1 invoice or credit note, a UN/CEFACT CII D16B invoice, an"
                    + " ESJ document, or a PDF carrying one of the three, brings it into the"
                    + " EN 16931 semantic model and writes, checks, renders or summarizes it"
                    + " from there. Nothing is read off the page of a PDF: a file that carries"
                    + " no structured invoice is reported as carrying none.",
            "",
            "A file name of - reads the standard input. The document goes to the standard"
                    + " output; every diagnostic, warning and error goes to the standard error"
                    + " stream.",
            "",
            "esj validate runs two engines. The official validation artefacts of a"
                    + " document's profile — the XML Schema of its syntax and the compiled"
                    + " Schematron of EN 16931 and of its CIUS — are carried in this jar as"
                    + " data and executed over an XML input, never reimplemented; the"
                    + " structural layers L1 to L3 of the ESJ specification are checked"
                    + " against the semantic model. The business rules of EN 16931 are then"
                    + " checked a second time by a rule engine of this project's own, written"
                    + " over the business terms rather than over the XPath of a syntax, so"
                    + " that it runs on an ESJ input too; --rules=<none|en16931> chooses it,"
                    + " and what it finds is reported as its own layer and never as ESJ"
                    + " conformance.",
            "",
            "esj --list-packs shows the validation packs this build carries, with their"
                    + " components and licences.",
            ""},
        versionProvider = VersionProvider.class,
        synopsisSubcommandLabel = "<command>",
        commandListHeading = "%nCommands:%n",
        footerHeading = "%nExit codes:%n",
        footer = {
            "  0  success",
            "  1  a validation found an error, the profile of a container puts the rules",
            "     of EN 16931 out of scope, two documents differ, or esj get found no",
            "     value at the path it was asked for",
            "  2  the input could not be read, recognized or parsed, or the command line",
            "     could not be parsed",
            "  3  not this tool's: the virtual machine aborts with it under",
            "     -XX:+ExitOnOutOfMemoryError and writes its notice to the standard",
            "     output, so it is a crash and no verdict",
            "  4  a feature this version does not implement, such as a ZUGFeRD 1.0",
            "     attachment",
            "  5  an internal error",
            "  6  the output could not be written in full",
            "  7  a resource or time limit of this run was reached; no verdict on the",
            "     document",
            "  8  the conversion cannot be completed as constrained: esj convert",
            "     --fail-on-loss found part of the document the target syntax has no",
            "     place for, and nothing was written",
            "  9  nothing fatal was found and a component of the complete check did not",
            "     run or did not complete: no verdict, and the report names which and why"},
        sortOptions = false)
final class EsjCommand implements Callable<Integer> {

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Spec
    private CommandSpec spec;

    /**
     * Set by picocli when {@code --version} is given; picocli prints the version itself.
     * The field exists so that the option exists, and nothing reads it.
     */
    @Option(order = 10, names = {"-V", "--version"}, versionHelp = true,
            description = "Print the version of the tool, of the format and of the semantic"
                    + " model, and exit.")
    private boolean versionRequested;

    @Option(order = 20, names = "--list-packs",
            description = "List the validation packs this build carries — their components,"
                    + " which documents each applies to and the licence each is distributed"
                    + " under — and exit.")
    private boolean listPacks;

    EsjCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        if (listPacks) {
            SyntaxPacks.list(console);
            return ExitCode.SUCCESS;
        }
        spec.commandLine().usage(console.errWriter());
        return ExitCode.INPUT;
    }
}
