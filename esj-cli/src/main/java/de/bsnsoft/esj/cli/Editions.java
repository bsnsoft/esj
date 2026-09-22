package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.upgrade.EditionUpgrade;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The editions of the semantic model this build carries, and what a command does with a
 * document of one of them.
 *
 * <p>A path is an address relative to an edition (specification, sections 4.4 and 10), so
 * every command that resolves a path has to resolve it against the registry of the edition
 * the document names and against no other. This class is where that choice is made, once,
 * for all of them: {@link #registries(Extensions)} hands the whole set to an engine that
 * selects for itself, {@link #forDocument(SemanticDocument, Extensions)} makes the choice for
 * an engine that takes one registry, and {@link #refuse(SemanticDocument, String)} writes
 * the refusal for a component that cannot serve the edition it was handed.
 *
 * <p>Which editions are there is a property of the build and not of this class. A registry
 * is data, a distribution may leave one out — {@code model/en16931/2026.paths} lists the
 * files of the 2026 edition and the Maven profile {@code without-edition-2026} builds
 * without them — so the set is read from {@link Registry#editions()} and nothing here
 * names an edition key.
 *
 * <p>An extension registry belongs to one edition too. The XRechnung extension and the
 * B2C extension are both written against the 2017 edition and say so in their
 * {@code imports} member; combining one of them with the registry of another edition
 * would measure its parents and cardinalities against a different list of terms, so each
 * is combined where it fits and left out where it does not, and a path of it in a
 * document of another edition is then reported as not measured rather than as wrong.
 */
final class Editions {

    private Editions() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the registry of every edition this build carries, each with the extension
     * registry combined into it where the extension was written against that edition.
     *
     * <p>This is the form an engine gets that selects the registry itself, which is what
     * {@code StructuralValidator} does with a set: the document names the edition and the
     * engine measures it against the one registry that describes it, or reports that it
     * has none.
     *
     * @param extension the extension registries this run loads
     * @return the registries, in the order the editions were published
     */
    static List<Registry> registries(Extensions extension) {
        Objects.requireNonNull(extension, "extension");
        List<Registry> registries = new ArrayList<>();
        for (String edition : Registry.editions()) {
            registries.add(withExtensions(Registry.forEdition(edition), extension));
        }
        return List.copyOf(registries);
    }

    /**
     * Returns the registry a document is read and written by, where this build carries
     * one for the edition it names.
     *
     * @param document  the document
     * @param extension the extension registries this run loads
     * @return the registry, or empty where no registry of this build describes that
     *         edition
     */
    static Optional<Registry> forDocument(SemanticDocument document, Extensions extension) {
        Objects.requireNonNull(document, "document");
        for (Registry registry : registries(extension)) {
            if (registry.describes(document.semanticModel())) {
                return Optional.of(registry);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the registry a document is read and written by, refusing the run where this
     * build carries none for the edition it names.
     *
     * @param document  the document
     * @param extension the extension registries this run loads
     * @return the registry
     * @throws CliException if no registry of this build describes that edition
     */
    static Registry require(SemanticDocument document, Extensions extension) {
        return forDocument(document, extension).orElseThrow(() ->
                CliException.unsupported("this build carries no registry of the edition "
                        + document.semanticModel() + "; it carries "
                        + String.join(", ", Registry.editions())));
    }

    /**
     * Returns the {@code semanticModel} of a document as a report line writes it: the
     * edition it names, and where this build holds no registry of that edition, that fact
     * beside it.
     *
     * <p>A reader of a report has to be able to tell the two situations apart without
     * knowing which editions this build was compiled with. Everything the report says
     * below the line — which layers measured anything, which rows of the check ran —
     * follows from it.
     *
     * @param document  the document
     * @param extension the extension registries this run loads
     * @return the line, for example {@code EN16931-1:2026 (no registry in this build)}
     */
    static String describe(SemanticDocument document, Extensions extension) {
        return document.semanticModel()
                + (forDocument(document, extension).isPresent()
                        ? "" : " (no registry in this build)");
    }

    /**
     * Returns the refusal of a component that cannot carry a document of the edition it
     * names.
     *
     * <p>A writer, a stylesheet or a binding table is written against one edition. Handing
     * it a document of another one has two possible answers, and only one of them is
     * honest: write what happens to fit and drop the rest, or refuse. Dropping business
     * content on the way into a syntax is the failure this project is built against, so
     * the answer is the refusal, it names the edition on both sides, and it names the way
     * out where there is one.
     *
     * @param document what was handed over
     * @param what     what cannot carry it, and the edition it is written against, as one
     *                 phrase — for example {@code the CII binding table, which binds
     *                 EN16931-1:2017+A1:2019/AC:2020}
     * @return the exception to throw, which leaves with {@link ExitCode#UNSUPPORTED}
     */
    static CliException refuse(SemanticDocument document, String what) {
        List<String> targets = EditionUpgrade.targets(document.semanticModel());
        return CliException.unsupported("the document names the edition "
                + document.semanticModel() + " and " + what
                + "; nothing of it is dropped to make it fit"
                + (targets.isEmpty()
                        ? ", and this build carries no edition to write it as"
                        : " \u2014 write it as an edition that can carry it, with esj upgrade"
                                + " --to " + String.join(" or ", targets)
                                + ", and run this command on the result"));
    }

    /**
     * Combines every loaded extension registry into a core registry, leaving out each one
     * that was written against another edition.
     */
    private static Registry withExtensions(Registry core, Extensions extensions) {
        Registry combined = core;
        for (Registry extension : extensions.registries()) {
            if (fits(core, extension)) {
                combined = combined.withExtension(extension);
            }
        }
        return combined;
    }

    /**
     * Tells whether an extension registry was written against the edition of a core
     * registry.
     */
    private static boolean fits(Registry core, Registry extension) {
        for (Registry.Import imported : extension.imports()) {
            if (imported.model().equals(core.model())
                    && !imported.edition().equals(core.edition())) {
                return false;
            }
        }
        return true;
    }
}
