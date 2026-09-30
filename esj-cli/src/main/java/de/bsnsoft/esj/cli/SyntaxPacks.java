package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.PackCatalog;
import de.bsnsoft.esj.syntax.PackComponent;
import de.bsnsoft.esj.syntax.PackRecipe;
import de.bsnsoft.esj.syntax.PackRecipes;
import de.bsnsoft.esj.syntax.PackSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * What the tool says about validation packs: the ones a run can choose among, where each
 * came from, and how a component of one is called in a report.
 *
 * <p>A pack is the official XML Schema and Schematron artefacts of a profile, carried in
 * the jar as data or made on this machine from a recipe, and executed rather than
 * reimplemented. Which one applies to a document follows from the document — its syntax
 * and the customization identifier it names in BT-24 — and {@link PackChoice} is where
 * {@code --pack}, {@code --packs} and {@code ESJ_PACKS} are read.
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
            "xrechnung", "XRechnung",
            "peppol", "Peppol");

    private SyntaxPacks() {
        throw new AssertionError("no instances");
    }

    /**
     * Writes what this run can choose among: every bundled pack and every pack of the pack
     * directories, its origin, its components, which documents each applies to and the
     * licence each is distributed under, and then the recipes this build carries.
     *
     * <p>The digests of a bundled pack are not printed. They are recorded in
     * {@code packs/SOURCES.md} of the repository, they are checked by the build rather than
     * by the reader, and a page of hashes on a terminal is a page nobody reads; the line
     * that says where they are is more use than the hashes themselves. A pack of a pack
     * directory has no such page, so a rule set that was compiled on this machine is
     * listed with the file it was compiled from and the SHA-256 of what came out, which is
     * what its manifest records and what the engine weighs before it runs it.
     *
     * @param console the streams of the process
     * @param catalog the packs to list
     */
    static void list(Console console, PackCatalog catalog) {
        for (Pack pack : catalog.packs()) {
            console.line(pack.directory());
            console.line("  " + pack.title());
            console.line("  origin " + origin(pack));
            pack.note().ifPresent(note -> console.line("  " + note));
            console.line("  retrieved " + pack.retrieved() + ", " + pack.files().size()
                    + " files besides the manifest; "
                    + (pack.source() == PackSource.BUNDLED
                            ? "provenance, licences and digests in packs/SOURCES.md"
                            : "each weighed against the SHA-256 its pack.json records"));
            for (PackComponent component : pack.components()) {
                console.line("  " + component.name() + " (" + component.role().token()
                        + ", " + component.license() + ")");
                console.line("    syntax " + String.join(", ",
                                component.syntaxes().stream().sorted().toList())
                        + "; " + profiles(component));
                if (!component.unmodified()) {
                    component.entries().values().stream().distinct().sorted()
                            .forEach(entry -> console.line("    compiled here from "
                                    + component.obtainedFrom() + "; " + entry + " sha256 "
                                    + pack.files().get(entry)));
                }
            }
            levels(console, pack);
            console.line();
        }
        console.line("A pack is run as data, never reimplemented. --pack <directory|id>"
                + " runs another one; " + PackChoice.ENVIRONMENT + " and --packs <directory>"
                + " add pack directories.");
        List<PackRecipe> recipes = PackRecipes.bundled();
        if (recipes.isEmpty()) {
            return;
        }
        console.line();
        console.line("Recipes, for artefacts that may be used but not redistributed"
                + " (esj packs fetch <recipe> --into <directory>):");
        for (PackRecipe recipe : recipes) {
            console.line(recipe.name());
            console.line("  " + recipe.title());
            boolean present = catalog.packs().stream()
                    .anyMatch(pack -> pack.directory().equals(recipe.identity()));
            console.line("  makes " + recipe.identity() + " from " + recipe.repository()
                    + " at tag " + recipe.tag() + (present ? "; in a pack directory above"
                            : "; not fetched into any pack directory of this run"));
        }
    }

    /**
     * Returns where a pack came from, as a report writes it: {@code bundled}, or the
     * directory its manifest was read from.
     *
     * @param pack the pack
     * @return the origin
     */
    static String origin(Pack pack) {
        return pack.location().map(Path::toString).orElse(PackSource.BUNDLED.token());
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
