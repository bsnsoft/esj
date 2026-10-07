package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.Preview;
import de.bsnsoft.esj.xml.InvoiceSyntax;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The recipes this module carries: how to make, on the machine that runs it, a validation
 * pack whose files may be used there but not redistributed.
 *
 * <p>A recipe is this project's own work and lives in the repository under
 * {@code packs/recipes/}. Like the bundled packs, the recipes are named in an index beside
 * this class rather than discovered, because a class path inside a jar is not a directory
 * that can be listed; a test checks that the index and the packaged recipes are the same
 * list.
 *
 * <p>The class is a preview, like the recipes it lists: it may change in any minor release.
 */
@Preview
public final class PackRecipes {

    /** The directory of the recipes below {@link Packs#ROOT}. */
    static final String DIRECTORY = "recipes";

    /** The index of the bundled recipes, as a class path resource name. */
    private static final String INDEX = "de/bsnsoft/esj/syntax/bundled-recipes.json";

    private PackRecipes() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns every recipe this module carries, in the order of the index.
     *
     * @return the recipes
     * @throws PackException if a recipe cannot be read
     */
    public static List<PackRecipe> bundled() {
        return Holder.RECIPES;
    }

    /**
     * Returns one recipe by its name, or by the identifier of the pack it makes.
     *
     * <p>A recipe's own name, for example {@code peppol-bis-billing-3.0.20}, names that
     * recipe. The identifier of a pack, for example {@code peppol-bis-billing}, names the
     * recipe of its newest release among the recipes this build carries, by the order of
     * releases ({@code 3.0.9} before {@code 3.0.10}); a build that adds a newer release
     * moves it there, and the recipe's own name keeps naming the older one.
     *
     * @param name the name of a recipe, or the identifier of a pack
     * @return the recipe
     * @throws PackException        if no recipe of that name, and none of a pack of that
     *                              identifier, is carried
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static PackRecipe named(String name) {
        Objects.requireNonNull(name, "name");
        for (PackRecipe recipe : bundled()) {
            if (recipe.name().equals(name)) {
                return recipe;
            }
        }
        Optional<PackRecipe> newest = bundled().stream()
                .filter(recipe -> recipe.id().equals(name))
                .max(Comparator.comparing(PackRecipe::version, Releases.ORDER)
                        .thenComparing(PackRecipe::release, Releases.ORDER));
        if (newest.isPresent()) {
            return newest.get();
        }
        throw new PackException("no recipe " + name + " is carried by this build; there "
                + (bundled().size() == 1 ? "is " : "are ") + String.join(", ",
                        bundled().stream().map(PackRecipe::name).toList()));
    }

    /**
     * Tells whether a recipe is the one the identifier of its pack names: the newest release
     * of that pack this build carries.
     *
     * @param recipe a recipe of this build
     * @return whether {@code esj packs fetch <id>} follows it
     * @throws NullPointerException if {@code recipe} is {@code null}
     */
    public static boolean isNewest(PackRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        return named(recipe.id()).name().equals(recipe.name());
    }

    /**
     * Returns the recipe whose pack brings the rules of the core invoice usage
     * specification a document names, where one does: of several releases of one pack, the
     * newest.
     *
     * @param syntax          the syntax of the document
     * @param customizationId the customization identifier the document names in BT-24
     * @return the recipe, or an empty optional
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Optional<PackRecipe> bringingRulesFor(InvoiceSyntax syntax,
                                                        String customizationId) {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        List<PackRecipe> bringing = bundled().stream()
                .filter(recipe -> recipe.bringsRulesFor(syntax, customizationId))
                .toList();
        return bringing.stream().findFirst().flatMap(first -> bringing.stream()
                .filter(recipe -> recipe.id().equals(first.id()))
                .max(Comparator.comparing(PackRecipe::version, Releases.ORDER)
                        .thenComparing(PackRecipe::release, Releases.ORDER)));
    }

    /** Returns the names the index lists. */
    static List<String> index() {
        byte[] json;
        try (InputStream in = PackRecipes.class.getClassLoader().getResourceAsStream(INDEX)) {
            if (in == null) {
                throw new PackException("the index of the bundled recipes is not on the class"
                        + " path");
            }
            json = in.readAllBytes();
        } catch (IOException e) {
            throw new PackException("the index of the bundled recipes could not be read", e);
        }
        if (PackJson.read(json, "bundled-recipes.json") instanceof Map<?, ?> root
                && root.get("recipes") instanceof List<?> listed) {
            List<String> names = new ArrayList<>();
            for (Object element : listed) {
                if (!(element instanceof String name)) {
                    throw new PackException("bundled-recipes.json names a recipe that is not a"
                            + " JSON string");
                }
                names.add(name);
            }
            return List.copyOf(names);
        }
        throw new PackException("bundled-recipes.json has no member recipes that is a JSON"
                + " array");
    }

    /** Reads the recipes on first use. */
    private static final class Holder {

        private static final List<PackRecipe> RECIPES = load();

        private Holder() {
            throw new AssertionError("no instances");
        }

        private static List<PackRecipe> load() {
            List<PackRecipe> recipes = new ArrayList<>();
            for (String name : index()) {
                String path = DIRECTORY + "/" + name + ".json";
                byte[] json;
                try (InputStream in = Packs.open(path)) {
                    json = in.readAllBytes();
                } catch (IOException e) {
                    throw new PackException("the recipe " + name + " could not be read", e);
                }
                PackRecipe recipe = PackRecipe.read(json, "packs/" + path);
                if (!recipe.name().equals(name)) {
                    throw new PackException("the recipe packaged as " + name
                            + " calls itself " + recipe.name());
                }
                recipes.add(recipe);
            }
            return List.copyOf(recipes);
        }
    }
}
