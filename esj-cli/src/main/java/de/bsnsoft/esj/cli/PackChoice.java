package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.PackCatalog;
import de.bsnsoft.esj.syntax.PackException;
import de.bsnsoft.esj.syntax.PackSelection;
import de.bsnsoft.esj.syntax.Packs;
import de.bsnsoft.esj.syntax.ProfileLevels;
import de.bsnsoft.esj.syntax.SyntaxOptions;
import de.bsnsoft.esj.xr.XrSyntax;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Which validation packs a run of {@code esj validate} or {@code esj inspect} takes: the one
 * {@code --pack} named, or the choice by profile among the bundled packs and the ones of the
 * pack directories named by {@code ESJ_PACKS} and {@code --packs}.
 *
 * <p>The two are one value because every place that needs a pack needs the same answer:
 * the syntax engine over the input, the same engine over the XML an ESJ document is written
 * to, and the level tables the native rules are counted with. A run that asked one of them
 * with the pack directories and another without would judge one invoice by two sets of
 * rules.
 *
 * @param named   the pack {@code --pack} named, or {@code null} where the document chooses
 * @param catalog the packs the document chooses among
 */
record PackChoice(Pack named, PackCatalog catalog) {

    /** The environment variable that names pack directories. */
    static final String ENVIRONMENT = "ESJ_PACKS";

    /**
     * Refuses a missing catalog.
     *
     * @param named   the pack {@code --pack} named, or {@code null} where the document chooses
     * @param catalog the packs the document chooses among
     */
    PackChoice {
        Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * Reads what the command line and the environment say about packs.
     *
     * <p>The pack directories are the entries of {@code ESJ_PACKS}, separated as the
     * platform separates a path list, followed by every {@code --packs}; an empty entry is
     * passed over. {@code --pack} is read afterwards: a directory holding a
     * {@code pack.json} is that pack, anything else is the identity of a pack of the
     * catalog — bundled, or found in one of those directories.
     *
     * @param pack    the value of {@code --pack}, or {@code null}
     * @param packs   the values of {@code --packs}, in order
     * @param console the streams and the environment of the run
     * @return the choice
     * @throws CliException if a directory or a pack cannot be read
     */
    static PackChoice of(String pack, List<String> packs, Console console) {
        PackCatalog catalog = catalog(packs, console);
        if (pack == null) {
            return new PackChoice(null, catalog);
        }
        try {
            Path directory = Path.of(pack);
            return new PackChoice(Files.isDirectory(directory)
                    ? Packs.fromDirectory(directory)
                    : catalog.find(pack), catalog);
        } catch (InvalidPathException | PackException e) {
            throw CliException.input("--pack " + pack + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads the pack directories of {@code ESJ_PACKS} and of {@code --packs} into a
     * catalog beside the bundled packs.
     *
     * @param packs   the values of {@code --packs}, in order
     * @param console the streams and the environment of the run
     * @return the catalog
     * @throws CliException if a directory cannot be read
     */
    static PackCatalog catalog(List<String> packs, Console console) {
        List<Path> directories = new ArrayList<>();
        String named = console.options().environment(ENVIRONMENT).orElse("");
        for (String entry : named.split(File.pathSeparator, -1)) {
            if (!entry.isBlank()) {
                directories.add(path(entry, ENVIRONMENT));
            }
        }
        for (String entry : packs == null ? List.<String>of() : packs) {
            directories.add(path(entry, "--packs"));
        }
        try {
            return PackCatalog.withDirectories(directories);
        } catch (PackException e) {
            throw CliException.input("the validation packs cannot be read: " + e.getMessage()
                    + (named.isBlank() ? "" : " (" + ENVIRONMENT + " is " + named + ")"), e);
        }
    }

    /**
     * Returns the pack {@code --pack} named.
     *
     * @return the pack, or an empty optional where the document chooses
     */
    Optional<Pack> pack() {
        return Optional.ofNullable(named);
    }

    /**
     * Returns the options of the syntax engine with this choice in them.
     *
     * @param options the options to start from
     * @return the options
     */
    SyntaxOptions apply(SyntaxOptions options) {
        SyntaxOptions withPacks = options.withPacks(catalog);
        return named == null ? withPacks : withPacks.withPack(named);
    }

    /**
     * Returns what applies to a document of a syntax and a profile.
     *
     * @param syntax  the syntax of the document
     * @param profile the customization identifier it names
     * @return the selection
     * @throws PackException if the pack cannot be read, or the choice is ambiguous
     */
    PackSelection select(XrSyntax syntax, String profile) {
        return named == null ? catalog.select(syntax, profile) : named.select(syntax, profile);
    }

    /**
     * Returns what the profile a document names makes of the rules of the packs.
     *
     * <p>The verdict of {@code esj validate} is made on those levels whichever engine
     * raised a rule, so the lookup is here rather than inside the syntax engine: a
     * specification that levels {@code BR-CL-13} down for its own profile has levelled the
     * rule, not the artefact that reports it, and the native pack reports the same
     * identifier over the same invoice.
     *
     * <p>Where the input is XML the table of its syntax answers, which is the table the
     * syntax engine ran with; where the document was never XML there is no syntax to
     * choose one by, and only what every table of the profile agrees on is taken
     * ({@link ProfileLevels}).
     *
     * @param syntax  what the input was written in
     * @param profile the customization identifier the document names in BT-24
     * @return the levels, which level nothing where the document names no profile or the
     *         pack has no table for it
     * @throws CliException if the pack cannot be read
     */
    ProfileLevels levels(InputSyntax syntax, String profile) {
        if (profile.isEmpty()) {
            return ProfileLevels.none();
        }
        try {
            return syntax.xrSyntax()
                    .map(xr -> named == null
                            ? catalog.levels(xr, profile)
                            : ProfileLevels.of(named, xr, profile))
                    .orElseGet(() -> named == null
                            ? catalog.levels(profile)
                            : ProfileLevels.of(named, profile));
        } catch (PackException e) {
            throw CliException.input("the validation pack cannot be read: "
                    + e.getMessage(), e);
        }
    }

    private static Path path(String entry, String source) {
        try {
            return Path.of(entry);
        } catch (InvalidPathException e) {
            throw CliException.input(source + " names " + entry + ", which is not a path", e);
        }
    }
}
