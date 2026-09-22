package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.PackComponent;
import de.bsnsoft.esj.syntax.PackException;
import de.bsnsoft.esj.syntax.Packs;
import de.bsnsoft.esj.syntax.ProfileLevels;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * What the tool does with validation packs: choose one, name one, and list the ones it
 * carries.
 *
 * <p>A pack is the official XML Schema and Schematron artefacts of a profile, carried in
 * the jar as data and executed rather than reimplemented. Which one applies to a document
 * follows from the document — its syntax and the customization identifier it names in
 * BT-24 — so {@code --pack} exists for the one case the document cannot decide: a release
 * that is newer than the one this version was built with. It takes the identity of a
 * bundled pack or a directory holding a {@code pack.json}, and a directory is a directory
 * of files this tool will execute, so it is the caller's business where it came from.
 *
 * <p>The component labels here are presentation and nothing else. A pack names its
 * components in its own vocabulary — {@code en16931-ubl-schematron} — which is the name a
 * report has to carry so that it still means something when the pack is replaced, and
 * which is what {@code --output json} writes. The text form spells the same name for a
 * reader, by casing the words it recognizes and passing on the ones it does not, so a
 * pack this version has never seen still prints a label rather than nothing.
 */
final class SyntaxPacks {

    /**
     * The words a component name is built from, and how each is spelled for a reader. A
     * token that is not here travels unchanged, so the table is a courtesy rather than a
     * list of the packs this tool knows.
     */
    private static final Map<String, String> WORDS = Map.of(
            "ubl", "UBL",
            "cii", "CII",
            "xsd", "XSD",
            "d16b", "D16B",
            "creditnote", "CreditNote",
            "schematron", "Schematron",
            "en16931", "EN 16931",
            "xrechnung", "XRechnung");

    private SyntaxPacks() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the pack {@code --pack} names.
     *
     * <p>A token that is an existing directory is read as a pack directory; anything else
     * is the identity of a bundled pack, {@code id/version/release}. The two cannot be
     * confused: an identity carries slashes but names no directory that exists, and a
     * directory that holds no manifest is refused by name rather than looked up as an
     * identity.
     *
     * @param token the value of {@code --pack}, or {@code null} where it was not given
     * @return the pack, or {@code null} to let the document choose among the bundled ones
     * @throws CliException if the token names no pack this tool can read
     */
    static Pack resolve(String token) {
        if (token == null) {
            return null;
        }
        try {
            Path directory = Path.of(token);
            return Files.isDirectory(directory)
                    ? Packs.fromDirectory(directory)
                    : Packs.bundled(token);
        } catch (InvalidPathException | PackException e) {
            throw CliException.input("--pack " + token + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns what the profile a document names makes of the rules of a pack.
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
     * @param chosen  the pack {@code --pack} named, or {@code null} for the bundled ones
     * @param syntax  what the input was written in
     * @param profile the customization identifier the document names in BT-24
     * @return the levels, which level nothing where the document names no profile or the
     *         pack has no table for it
     * @throws CliException if the pack cannot be read
     */
    static ProfileLevels levels(Pack chosen, InputSyntax syntax, String profile) {
        if (profile.isEmpty()) {
            return ProfileLevels.none();
        }
        try {
            return syntax.xrSyntax()
                    .map(xr -> chosen == null
                            ? Packs.levels(xr, profile)
                            : ProfileLevels.of(chosen, xr, profile))
                    .orElseGet(() -> chosen == null
                            ? Packs.levels(profile)
                            : ProfileLevels.of(chosen, profile));
        } catch (PackException e) {
            throw CliException.input("the validation pack cannot be read: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Writes what this build carries: every bundled pack, its components, which documents
     * each applies to and the licence each is distributed under.
     *
     * <p>The digests and the upstream releases are not printed. They are recorded in
     * {@code packs/SOURCES.md} of the repository, they are checked by the build rather
     * than by the reader, and a page of hashes on a terminal is a page nobody reads; the
     * line that says where they are is more use than the hashes themselves.
     *
     * @param console the streams of the process
     */
    static void list(Console console) {
        for (Pack pack : Packs.bundled()) {
            console.line(pack.directory());
            console.line("  " + pack.title());
            console.line("  retrieved " + pack.retrieved() + ", " + pack.files().size()
                    + " files besides the manifest; provenance, licences and digests"
                    + " in packs/SOURCES.md");
            for (PackComponent component : pack.components()) {
                console.line("  " + component.name() + " (" + component.role().token()
                        + ", " + component.license() + ")");
                console.line("    syntax " + String.join(", ",
                                component.syntaxes().stream().sorted().toList())
                        + "; " + profiles(component));
            }
            levels(console, pack);
            console.line();
        }
        console.line("A pack is run as data, never reimplemented. --pack <directory|id>"
                + " runs another one.");
    }

    /**
     * Writes one line about the levels the profiles of a pack give rules of its
     * artefacts.
     *
     * <p>The tables themselves are not printed. They are dozens of rule identifiers, they
     * are in {@code packs/SOURCES.md} where a reader can look one up, and what matters on
     * a terminal is that they exist and are somebody else's decision rather than this
     * tool's.
     */
    private static void levels(Console console, Pack pack) {
        if (pack.levels().isEmpty()) {
            return;
        }
        int rules = pack.levels().stream().mapToInt(table -> table.levels().size()).sum();
        console.line("  " + pack.levels().size() + " level tables, " + rules + " rules in"
                + " all: what the profiles of this specification say a rule of the"
                + " artefacts above means for a document of their own profile, listed in"
                + " packs/SOURCES.md");
    }

    /** Returns the profiles a component applies to, as a phrase. */
    private static String profiles(PackComponent component) {
        if (component.profiles().equals(List.of("*"))) {
            return "any profile";
        }
        return "profile " + String.join(", ", component.profiles());
    }

    /**
     * Returns the name of a pack component as a line of a report spells it.
     *
     * @param component the name the pack manifest gives the component
     * @return the same name, cased for a reader
     */
    static String label(String component) {
        StringBuilder label = new StringBuilder();
        for (String word : component.split("-")) {
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(WORDS.getOrDefault(word, word));
        }
        return label.toString();
    }
}
