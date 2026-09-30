package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.syntax.PackException;
import de.bsnsoft.esj.syntax.PackFetcher;
import de.bsnsoft.esj.syntax.PackRecipe;
import de.bsnsoft.esj.syntax.PackRecipes;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code esj packs}: the validation packs themselves, rather than a document.
 *
 * <p>Two subcommands. {@code list} is {@code esj --list-packs} with the one option that
 * cannot live on the top level, {@code --packs}. {@code fetch} makes a pack from a recipe
 * this build carries: it is for artefacts whose publisher permits their use and not their
 * redistribution, so what this project ships is the recipe, and the files travel from the
 * publisher to the machine that runs them and nowhere else. It is the one command of this
 * tool that opens a network connection, and only to the base URL of the recipe it was
 * given.
 */
@Command(name = "packs",
        description = "List the validation packs a run can choose among, or make one from a"
                + " recipe of this build: esj packs list, esj packs fetch.",
        synopsisSubcommandLabel = "<command>",
        commandListHeading = "%nCommands:%n",
        sortOptions = false)
final class PacksCommand implements Callable<Integer> {

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Spec
    private CommandSpec spec;

    private PacksCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    /**
     * Returns the command with its two subcommands, for the top level to add.
     *
     * @param console the streams of the process
     * @return the command line of {@code esj packs}
     */
    static CommandLine tree(Console console) {
        return new CommandLine(new PacksCommand(console))
                .addSubcommand(new ListPacks(console))
                .addSubcommand(new FetchPack(console));
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(console.errWriter());
        return ExitCode.INPUT;
    }

    /** {@code esj packs list}. */
    @Command(name = "list",
            description = "List the validation packs this build carries and the ones of the"
                    + " pack directories ESJ_PACKS and --packs name — their origin, their"
                    + " components, which documents each applies to and the licence each is"
                    + " distributed under — the recipes esj packs fetch follows, and the"
                    + " native rule packs with their edition.",
            sortOptions = false)
    static final class ListPacks implements Callable<Integer> {

        @Mixin
        private final GlobalFlags flags;

        private final Console console;

        @Option(order = 10, names = "--packs", paramLabel = "<directory>",
                description = "Add a pack directory to the ones ESJ_PACKS names."
                        + " Repeatable.")
        private List<String> packs = new ArrayList<>();

        ListPacks(Console console) {
            this.console = console;
            this.flags = new GlobalFlags(console.options());
        }

        @Override
        public Integer call() {
            SyntaxPacks.list(console, PackChoice.catalog(packs, console));
            console.line();
            RuleCheck.list(console);
            return ExitCode.SUCCESS;
        }
    }

    /** {@code esj packs fetch}. */
    @Command(name = "fetch",
            description = "Make a validation pack from a recipe of this build: fetch the files"
                    + " it names from their publisher over https, refuse any whose SHA-256 is"
                    + " not the one the recipe pins, compile the Schematron to XSLT on this"
                    + " machine, copy the schema modules from a bundled pack, and write the"
                    + " pack to <directory>/<id>/<version>/<release>. An identical pack there"
                    + " is left alone; a different one is refused unless --replace is given."
                    + " esj packs list names the recipes.",
            sortOptions = false)
    static final class FetchPack implements Callable<Integer> {

        /** How long one file may take to arrive. */
        private static final Duration TIMEOUT = Duration.ofSeconds(60);

        @Mixin
        private final GlobalFlags flags;

        private final Console console;

        @Parameters(index = "0", paramLabel = "<recipe>",
                description = "The name of the recipe, for example peppol-bis-billing-3.0.20.")
        private String recipe;

        @Option(order = 10, names = "--into", paramLabel = "<directory>", required = true,
                description = "The pack directory to write the pack into; it is made where it"
                        + " is not there. Name it with --packs or ESJ_PACKS afterwards.")
        private String into;

        @Option(order = 20, names = "--replace",
                description = "Replace a different pack of the same identity in that"
                        + " directory. Only files its manifest lists are removed.")
        private boolean replace;

        FetchPack(Console console) {
            this.console = console;
            this.flags = new GlobalFlags(console.options());
        }

        @Override
        public Integer call() {
            PackRecipe chosen;
            Path directory;
            try {
                chosen = PackRecipes.named(recipe);
                directory = Path.of(into);
            } catch (PackException | InvalidPathException e) {
                throw CliException.input(e.getMessage(), e);
            }
            PackFetcher.Result result;
            try {
                result = PackFetcher.fetch(chosen, directory, replace,
                        PackFetcher.https(VersionProvider.tool().replace(' ', '/'), TIMEOUT),
                        LocalDate.now(ZoneOffset.UTC), VersionProvider.tool());
            } catch (PackException e) {
                throw CliException.input(chosen.name() + ": " + e.getMessage(), e);
            }
            String where = result.pack().location().map(Path::toString).orElseThrow();
            console.line(chosen.identity() + switch (result.outcome()) {
                case WRITTEN -> " written to ";
                case REPLACED -> " replaced in ";
                case UNCHANGED -> " is already, unchanged, in ";
            } + where);
            console.line("  " + result.fetched() + " files fetched from " + chosen.base()
                    + ", each with the SHA-256 the recipe pins");
            console.line("  " + chosen.schematron().size()
                    + " Schematron files compiled to XSLT on this machine");
            for (String copied : result.copied()) {
                console.line("  copied from the bundled pack " + copied);
            }
            console.line("  " + result.pack().note().orElse(chosen.note()));
            console.line("esj validate <file> --packs " + directory.toAbsolutePath().normalize()
                    + " runs it, as does " + PackChoice.ENVIRONMENT + "="
                    + directory.toAbsolutePath().normalize());
            console.verbose(chosen.name() + ": fetched in " + result.fetchTime().toMillis()
                    + " ms, compiled in " + result.compileTime().toMillis() + " ms");
            return ExitCode.SUCCESS;
        }
    }
}
