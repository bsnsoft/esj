package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.xml.InvoiceSyntax;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The recipes this build carries: that the index and the packaged files agree, and that
 * every recipe is one {@code esj packs fetch} can follow — without fetching anything.
 */
class PackRecipesTest {

    private static final String PEPPOL = "peppol-bis-billing-3.0.20";

    private static final String PEPPOL_NEWEST = "peppol-bis-billing-3.0.21";

    private static final String PEPPOL_PROFILE =
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0";

    @Test
    void theIndexNamesEveryPackagedRecipe() throws IOException, URISyntaxException {
        URL url = PackRecipesTest.class.getResource("/" + Packs.ROOT + "/"
                + PackRecipes.DIRECTORY);
        Set<String> packaged = new TreeSet<>();
        URI uri = url.toURI();
        if ("jar".equals(uri.getScheme())) {
            try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                packaged.addAll(names(jar.getPath("/" + Packs.ROOT + "/"
                        + PackRecipes.DIRECTORY)));
            }
        } else {
            packaged.addAll(names(Path.of(uri)));
        }

        assertEquals(packaged, new TreeSet<>(PackRecipes.index()));
    }

    @Test
    void carriesThePeppolRecipePinnedToItsTag() {
        PackRecipe recipe = PackRecipes.named(PEPPOL);

        assertEquals("peppol-bis-billing/3.0/3.0.20", recipe.identity());
        assertEquals("v3.0.20", recipe.tag().orElseThrow());
        assertEquals("tag v3.0.20", recipe.revision());
        assertEquals(URI.create("https://raw.githubusercontent.com/OpenPEPPOL/"
                + "peppol-bis-invoice-3/v3.0.20/rules/sch/PEPPOL-EN16931-UBL.sch"),
                recipe.url("rules/sch/PEPPOL-EN16931-UBL.sch"));
        assertEquals(List.of("rules/sch/CEN-EN16931-UBL.sch", "rules/sch/CEN-EN16931-CII.sch",
                        "rules/sch/PEPPOL-EN16931-UBL.sch", "rules/sch/PEPPOL-EN16931-CII.sch"),
                recipe.schematron().stream().map(PackRecipe.Schematron::file).toList());
        assertTrue(recipe.schematron().stream()
                        .filter(rule -> rule.name().startsWith("peppol-"))
                        .allMatch(rule -> rule.license().equals("LicenseRef-OpenPeppol")),
                "the Peppol rule sets are named as what they are: no open-source licence");
    }

    @Test
    void carriesThePeppolRecipeOfAReleaseWithoutATagPinnedToItsCommit() {
        PackRecipe recipe = PackRecipes.named(PEPPOL_NEWEST);

        assertEquals("peppol-bis-billing/3.0/3.0.21", recipe.identity());
        assertTrue(recipe.tag().isEmpty(), "OpenPeppol tagged no 3.0.21");
        assertEquals("806866bd2bd91d7e9623b68f08164e8fbe9e67a0", recipe.commit());
        assertEquals("commit 806866bd2bd91d7e9623b68f08164e8fbe9e67a0 (branch 2026-Q2-QA2)",
                recipe.revision());
        assertEquals(URI.create("https://raw.githubusercontent.com/OpenPEPPOL/"
                + "peppol-bis-invoice-3/806866bd2bd91d7e9623b68f08164e8fbe9e67a0/rules/sch/"
                + "PEPPOL-EN16931-CII.sch"), recipe.url("rules/sch/PEPPOL-EN16931-CII.sch"));
        assertEquals(List.of("cen/1.3.16", "cen/1.3.16", "peppol/3.0.21", "peppol/3.0.21"),
                recipe.schematron().stream().map(PackRecipe.Schematron::directory).toList());
    }

    @Test
    void theIdentifierOfThePackNamesTheNewestRelease() {
        assertEquals(PEPPOL_NEWEST, PackRecipes.named("peppol-bis-billing").name());
        assertTrue(PackRecipes.isNewest(PackRecipes.named(PEPPOL_NEWEST)));
        assertFalse(PackRecipes.isNewest(PackRecipes.named(PEPPOL)),
                "the older release stays fetchable by its own name");
        assertEquals(PEPPOL, PackRecipes.named(PEPPOL).name());
    }

    @Test
    void ordersReleasesByTheirParts() {
        List<String> releases = new ArrayList<>(List.of("3.0.21", "3.0.9", "3.0.10",
                "3.0.20", "2026-10-15", "2026-08-31", "3.0"));
        releases.sort(Releases.ORDER);
        assertEquals(List.of("3.0", "3.0.9", "3.0.10", "3.0.20", "3.0.21", "2026-08-31",
                "2026-10-15"), releases);
    }

    @Test
    void refusesARecipeItDoesNotCarry() {
        PackException refused = assertThrows(PackException.class,
                () -> PackRecipes.named("peppol"));
        assertTrue(refused.getMessage().contains(PEPPOL), refused.getMessage());
    }

    @Test
    void everyRecipeMakesAPackThisBuildCanComplete() {
        for (PackRecipe recipe : PackRecipes.bundled()) {
            assertFalse(Packs.bundled().stream()
                            .anyMatch(pack -> pack.directory().equals(recipe.identity())),
                    recipe + " makes a pack that is not also bundled");
            for (PackRecipe.Copy copy : recipe.copies()) {
                Pack pack = Packs.bundled(copy.pack());
                assertTrue(pack.components().stream()
                                .anyMatch(component -> component.name().equals(copy.component())),
                        recipe + " copies " + copy + ", which is bundled");
            }
            Set<String> paths = new HashSet<>();
            for (PackRecipe.Schematron rule : recipe.schematron()) {
                assertTrue(paths.add(rule.source()), rule.source() + " is written once");
                assertTrue(paths.add(rule.compiled()), rule.compiled() + " is written once");
                assertTrue(rule.bytes() > 0 && rule.sha256().matches("[0-9a-f]{64}"),
                        rule.file() + " is pinned");
                assertTrue(recipe.url(rule.file()).toString().startsWith(
                        recipe.base().toString()), rule.file() + " stays below the base");
            }
            assertTrue(recipe.base().toString().contains("/" + recipe.treeish() + "/"),
                    recipe + " fetches by its tag, or without one by its commit");
            assertFalse(recipe.branch().isPresent() && recipe.base().toString()
                            .contains("/" + recipe.branch().orElseThrow() + "/"),
                    recipe + " never fetches by a branch");
        }
    }

    @Test
    void thePeppolRecipeBringsRulesForThePeppolProfileOnly() {
        PackRecipe recipe = PackRecipes.named(PEPPOL);

        assertTrue(recipe.bringsRulesFor(InvoiceSyntax.UBL_INVOICE, PEPPOL_PROFILE));
        assertTrue(recipe.bringsRulesFor(InvoiceSyntax.UBL_CREDIT_NOTE, PEPPOL_PROFILE));
        assertTrue(recipe.bringsRulesFor(InvoiceSyntax.CII, PEPPOL_PROFILE));
        assertFalse(recipe.bringsRulesFor(InvoiceSyntax.UBL_INVOICE, "urn:cen.eu:en16931:2017"),
                "the EN 16931 rules alone are what a bundled pack already brings");
        assertFalse(recipe.bringsRulesFor(InvoiceSyntax.UBL_INVOICE, ExamplePacks.XRECHNUNG));
        assertEquals(PEPPOL_NEWEST, PackRecipes.bringingRulesFor(InvoiceSyntax.UBL_INVOICE,
                PEPPOL_PROFILE).orElseThrow().name(), "the newest release is the one named");
    }

    @Test
    void refusesARecipeThatFetchesOverPlainHttpOrOutOfItsBase() {
        String recipe = new String(Corpus.bytes("/" + Packs.ROOT + "/recipes/" + PEPPOL
                + ".json"), StandardCharsets.UTF_8);

        assertThrows(PackException.class, () -> PackRecipe.read(recipe.replace(
                "\"base\": \"https://", "\"base\": \"http://")
                .getBytes(StandardCharsets.UTF_8), "plain http"));
        assertThrows(PackException.class, () -> PackRecipe.read(recipe.replace(
                "\"file\": \"rules/sch/CEN-EN16931-UBL.sch\"",
                "\"file\": \"../rules/sch/CEN-EN16931-UBL.sch\"")
                .getBytes(StandardCharsets.UTF_8), "out of the base"));
        assertThrows(PackException.class, () -> PackRecipe.read(recipe.replace(
                "bdcbb7b702cce55c7f8c789bef0cb9bebf6d376140c1776e683bd6d9bc0ad331", "abc")
                .getBytes(StandardCharsets.UTF_8), "no digest"));
    }

    @Test
    void refusesARecipeWithoutATagThatFetchesByABranch() {
        String recipe = new String(Corpus.bytes("/" + Packs.ROOT + "/recipes/" + PEPPOL_NEWEST
                + ".json"), StandardCharsets.UTF_8);
        PackRecipe.read(recipe.getBytes(StandardCharsets.UTF_8), "as carried");

        PackException refused = assertThrows(PackException.class, () -> PackRecipe.read(
                recipe.replace("peppol-bis-invoice-3/806866bd2bd91d7e9623b68f08164e8fbe9e67a0/",
                        "peppol-bis-invoice-3/2026-Q2-QA2/")
                        .getBytes(StandardCharsets.UTF_8), "by branch"));
        assertTrue(refused.getMessage().contains("below the commit"), refused.getMessage());
        assertThrows(PackException.class, () -> PackRecipe.read(recipe.replace(
                "\"commit\": \"806866bd2bd91d7e9623b68f08164e8fbe9e67a0\"",
                "\"commit\": \"806866bd\"").getBytes(StandardCharsets.UTF_8),
                "short commit"));
    }

    private static List<String> names(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .toList();
        }
    }
}
