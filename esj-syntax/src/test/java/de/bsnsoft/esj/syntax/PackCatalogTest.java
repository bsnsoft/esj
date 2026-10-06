package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.xr.XrSyntax;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pack directories: what joins the bundled packs, and what is refused rather than ordered.
 */
class PackCatalogTest {

    private static final String BUNDLED = "xrechnung/3.0.2/2026-08-31";

    @TempDir
    Path directory;

    @Test
    void withoutDirectoriesItIsTheBundledCatalog() {
        assertSame(PackCatalog.bundled(), PackCatalog.withDirectories(List.of()));
        assertEquals(Packs.bundled(), PackCatalog.bundled().packs());
    }

    @Test
    void anEmptyDirectoryAddsNothingAndChangesNoChoice() {
        PackCatalog catalog = PackCatalog.withDirectories(List.of(directory));

        assertEquals(Packs.bundled(), catalog.packs());
        assertEquals(BUNDLED, catalog.select(XrSyntax.UBL_INVOICE, ExamplePacks.PROFILE)
                .pack().directory(), "a profile nobody knows falls back as it always did");
    }

    @Test
    void refusesADirectoryThatIsNotThere() {
        PackException refused = assertThrows(PackException.class,
                () -> PackCatalog.withDirectories(List.of(directory.resolve("absent"))));
        assertTrue(refused.getMessage().contains("does not exist"), refused.getMessage());
    }

    @Test
    void offersAPackOfADirectoryForItsProfileAndTheBundledOneForTheRest() {
        ExamplePacks.fetch(directory);
        PackCatalog catalog = PackCatalog.withDirectories(List.of(directory, directory));

        assertEquals(List.of(BUNDLED, "example/1.0/2026-09-30"),
                catalog.packs().stream().map(Pack::directory).toList(),
                "the bundled packs come first, and a directory named twice is read once");
        assertEquals("example/1.0/2026-09-30",
                catalog.select(XrSyntax.UBL_INVOICE, ExamplePacks.PROFILE).pack().directory());
        assertEquals(BUNDLED, catalog.select(XrSyntax.UBL_INVOICE, ExamplePacks.XRECHNUNG)
                .pack().directory());
        assertEquals(BUNDLED, catalog.select(XrSyntax.UBL_INVOICE, "urn:cen.eu:en16931:2017")
                .pack().directory(), "a document of no CIUS keeps the pack it had");
        assertEquals(BUNDLED, catalog.select(XrSyntax.CII, ExamplePacks.PROFILE)
                .pack().directory(), "the example pack has no rules for CII");
        assertEquals("example/1.0/2026-09-30",
                catalog.find("example/1.0/2026-09-30").directory());
        assertEquals(BUNDLED, catalog.find(BUNDLED).directory());
    }

    @Test
    void passesOverTheWorkingDirectoriesOfAFetch() throws IOException {
        Files.createDirectories(directory.resolve(".esj-fetch-example-1/example/1.0/x"));
        ExamplePacks.fetch(directory);

        assertEquals(2, PackCatalog.withDirectories(List.of(directory)).packs().size());
    }

    @Test
    void refusesAPackThatTakesTheIdentityOfABundledOne() throws IOException {
        ExamplePacks.fetch(directory);
        Path pack = directory.resolve("example/1.0/2026-09-30");
        Path bundled = Files.createDirectories(directory.resolve("xrechnung/3.0.2"));
        Files.move(pack, bundled.resolve("2026-08-31"));
        rewrite(bundled.resolve("2026-08-31/pack.json"), Map.of(
                "\"id\": \"example\"", "\"id\": \"xrechnung\"",
                "\"version\": \"1.0\"", "\"version\": \"3.0.2\"",
                "\"release\": \"2026-09-30\"", "\"release\": \"2026-08-31\""));

        PackException refused = assertThrows(PackException.class,
                () -> PackCatalog.withDirectories(List.of(directory)));
        assertTrue(refused.getMessage().contains("the identity of a pack this build carries"),
                refused.getMessage());
    }

    @Test
    void refusesAPackThatSitsUnderAnotherIdentity() throws IOException {
        ExamplePacks.fetch(directory);
        Files.move(directory.resolve("example/1.0/2026-09-30"),
                directory.resolve("example/1.0/2026-10-01"));

        PackException refused = assertThrows(PackException.class,
                () -> PackCatalog.withDirectories(List.of(directory)));
        assertTrue(refused.getMessage().contains("calls itself example/1.0/2026-09-30"),
                refused.getMessage());
    }

    @Test
    void refusesOneIdentityInTwoDirectories() throws IOException {
        Path first = Files.createDirectory(directory.resolve("first"));
        Path second = Files.createDirectory(directory.resolve("second"));
        ExamplePacks.fetch(first);
        ExamplePacks.fetch(second);

        PackException refused = assertThrows(PackException.class,
                () -> PackCatalog.withDirectories(List.of(first, second)));
        assertTrue(refused.getMessage().contains("is in two pack directories"),
                refused.getMessage());
    }

    @Test
    void takesTheNewestOfTwoReleasesOfOnePack() {
        byte[] schematron = ExamplePacks.schematron();
        PackFetcher.Download download =
                ExamplePacks.serving(Map.of(ExamplePacks.url(), schematron));
        // 1.0.10 is newer than 1.0.9, which a comparison of characters would get wrong.
        PackFetcher.fetch(ExamplePacks.recipe(schematron, "1.0", "1.0.10"), directory,
                false, download, ExamplePacks.TODAY, "esj test");
        PackFetcher.fetch(ExamplePacks.recipe(schematron, "1.0", "1.0.9"), directory,
                false, download, ExamplePacks.TODAY, "esj test");
        PackCatalog catalog = PackCatalog.withDirectories(List.of(directory));

        assertEquals("example/1.0/1.0.10", catalog.select(XrSyntax.UBL_INVOICE,
                ExamplePacks.PROFILE).pack().directory());
        assertEquals("example/1.0/1.0.9", catalog.find("example/1.0/1.0.9").directory(),
                "the older release is still there for --pack");
        assertEquals(BUNDLED, catalog.select(XrSyntax.UBL_INVOICE, ExamplePacks.XRECHNUNG)
                .pack().directory(), "a profile only one pack knows is still decided");
    }

    @Test
    void refusesToChooseBetweenTwoPacksOfOneProfile() {
        byte[] schematron = ExamplePacks.schematron();
        PackFetcher.Download download =
                ExamplePacks.serving(Map.of(ExamplePacks.url(), schematron));
        PackFetcher.fetch(ExamplePacks.recipe(schematron, "1.0", "2026-09-30"), directory,
                false, download, ExamplePacks.TODAY, "esj test");
        PackFetcher.fetch(ExamplePacks.recipe(schematron, "2.0", "2026-10-15"), directory,
                false, download, ExamplePacks.TODAY, "esj test");
        PackCatalog catalog = PackCatalog.withDirectories(List.of(directory));

        PackException refused = assertThrows(PackException.class,
                () -> catalog.select(XrSyntax.UBL_INVOICE, ExamplePacks.PROFILE));
        assertTrue(refused.getMessage().startsWith("2 validation packs apply to the profile "
                + ExamplePacks.PROFILE), refused.getMessage());
        assertTrue(refused.getMessage().contains("--pack"), refused.getMessage());
        assertEquals(BUNDLED, catalog.select(XrSyntax.UBL_INVOICE, ExamplePacks.XRECHNUNG)
                .pack().directory(), "a profile only one pack knows is still decided");
    }

    /** Replaces text in a file. */
    private static void rewrite(Path file, Map<String, String> replacements)
            throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            assertTrue(text.contains(replacement.getKey()), replacement.getKey());
            text = text.replace(replacement.getKey(), replacement.getValue());
        }
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
