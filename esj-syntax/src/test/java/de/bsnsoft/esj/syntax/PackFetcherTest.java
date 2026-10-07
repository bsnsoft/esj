package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.xml.InvoiceSyntax;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code esj packs fetch} without the network: the example recipe of {@link ExamplePacks},
 * answered from memory, through everything a fetch does to a pack directory.
 */
class PackFetcherTest {

    @TempDir
    Path into;

    @Test
    void writesAPackThatAPackDirectoryOffersForItsProfile() throws IOException {
        PackFetcher.Result result = ExamplePacks.fetch(into);

        assertEquals(PackFetcher.Outcome.WRITTEN, result.outcome());
        assertEquals(1, result.fetched());
        assertEquals(List.of("xrechnung/3.0.2/2026-08-31 ubl-2.1-xsd"), result.copied());
        Pack pack = result.pack();
        assertEquals("example/1.0/2026-09-30", pack.directory());
        assertEquals(PackSource.DIRECTORY, pack.source());
        assertEquals(Optional.of(into.toRealPath().resolve("example/1.0/2026-09-30")),
                pack.location());
        assertEquals("2026-09-30", pack.retrieved());
        assertEquals(Optional.of("Written for the tests of esj-syntax."), pack.note());
        assertTrue(pack.files().containsKey("example/2026-09-30/example-ubl.sch"),
                "the fetched file is kept beside what was compiled from it");
        assertTrue(pack.files().containsKey("example/2026-09-30/example-ubl.xslt"));
        assertTrue(pack.files().containsKey("xsd/ubl-2.1/NOTICE"),
                "a copied component travels with its licence file");
        assertTrue(pack.files().containsKey(PackFetcher.NOTICE));

        PackCatalog catalog = PackCatalog.withDirectories(List.of(into));
        PackSelection selection = catalog.select(InvoiceSyntax.UBL_INVOICE, ExamplePacks.PROFILE);
        assertEquals("example/1.0/2026-09-30", selection.pack().directory());
        assertEquals(List.of("ubl-2.1-xsd", "example-ubl-schematron"),
                selection.applied().stream().map(PackComponent::name).toList());
        assertEquals("xrechnung/3.0.2/2026-08-31",
                catalog.select(InvoiceSyntax.UBL_INVOICE, ExamplePacks.XRECHNUNG).pack().directory(),
                "a document of another profile keeps the pack it had");
    }

    @Test
    void theRulesItCompiledDecideAnInvoiceOfItsProfile() {
        ExamplePacks.fetch(into);
        SyntaxOptions options = SyntaxOptions.defaults()
                .withPacks(PackCatalog.withDirectories(List.of(into)));

        SyntaxReport valid = SyntaxValidator.validate(
                ExamplePacks.invoice(ExamplePacks.PROFILE), options);
        assertEquals(Verdict.VALID, valid.verdict(), valid.findings().toString());
        assertEquals("example/1.0/2026-09-30", valid.pack().orElseThrow().directory());

        byte[] longNumber = new String(ExamplePacks.invoice(ExamplePacks.PROFILE),
                StandardCharsets.UTF_8)
                .replace("<cbc:ID>123456XX</cbc:ID>",
                        "<cbc:ID>123456XX-123456XX-123456XX</cbc:ID>")
                .getBytes(StandardCharsets.UTF_8);
        SyntaxReport invalid = SyntaxValidator.validate(longNumber, options);
        assertEquals(Verdict.INVALID, invalid.verdict());
        assertEquals(List.of("EXAMPLE-01"),
                invalid.fatal().stream().map(SyntaxFinding::code).toList());
    }

    @Test
    void writesTheSameBytesTwiceAndLeavesAnIdenticalPackAlone() throws IOException {
        ExamplePacks.fetch(into);
        Path manifest = into.resolve("example/1.0/2026-09-30/pack.json");
        byte[] first = Files.readAllBytes(manifest);

        PackFetcher.Result again = ExamplePacks.fetch(into);

        assertEquals(PackFetcher.Outcome.UNCHANGED, again.outcome());
        assertArrayEquals(first, Files.readAllBytes(manifest));
        assertEquals(List.of("example"), names(into),
                "no working directory is left behind");

        Path other = Files.createDirectory(into.resolve("second"));
        ExamplePacks.fetch(other);
        assertArrayEquals(first, Files.readAllBytes(
                other.resolve("example/1.0/2026-09-30/pack.json")),
                "a fetch writes the same manifest wherever it writes it");
    }

    @Test
    void refusesAFileThatIsNotTheOneTheRecipePinsAndWritesNothing() {
        byte[] schematron = ExamplePacks.schematron();
        byte[] changed = new String(schematron, StandardCharsets.UTF_8)
                .replace("at most 20", "at most 21").getBytes(StandardCharsets.UTF_8);

        PackException refused = assertThrows(PackException.class, () -> PackFetcher.fetch(
                ExamplePacks.recipe(schematron), into, false,
                ExamplePacks.serving(Map.of(ExamplePacks.url(), changed)),
                ExamplePacks.TODAY, "esj test"));

        assertTrue(refused.getMessage().contains(ExamplePacks.FILE), refused.getMessage());
        assertTrue(refused.getMessage().contains("nothing was written"), refused.getMessage());
        assertEquals(List.of(), names(into));
    }

    @Test
    void refusesAFileItCannotFetchAndWritesNothing() {
        PackException refused = assertThrows(PackException.class, () -> PackFetcher.fetch(
                ExamplePacks.recipe(ExamplePacks.schematron()), into, false,
                ExamplePacks.serving(Map.of()), ExamplePacks.TODAY, "esj test"));

        assertTrue(refused.getMessage().startsWith("could not fetch " + ExamplePacks.url()),
                refused.getMessage());
        assertEquals(List.of(), names(into));
    }

    @Test
    void refusesADifferentPackInTheTargetUnlessAskedToReplaceIt() throws IOException {
        byte[] schematron = ExamplePacks.schematron();
        ExamplePacks.fetch(into);
        byte[] revised = new String(schematron, StandardCharsets.UTF_8)
                .replace("at most 20", "at most 25").getBytes(StandardCharsets.UTF_8);
        PackRecipe recipe = ExamplePacks.recipe(revised);
        PackFetcher.Download download =
                ExamplePacks.serving(Map.of(ExamplePacks.url(), revised));

        PackException refused = assertThrows(PackException.class, () -> PackFetcher.fetch(
                recipe, into, false, download, ExamplePacks.TODAY, "esj test"));
        assertTrue(refused.getMessage().contains("--replace"), refused.getMessage());
        Path xslt = into.resolve("example/1.0/2026-09-30/example/2026-09-30/example-ubl.xslt");
        assertTrue(Files.readString(xslt).contains("at most 20"), "the old pack stands");

        PackFetcher.Result replaced = PackFetcher.fetch(recipe, into, true, download,
                ExamplePacks.TODAY, "esj test");
        assertEquals(PackFetcher.Outcome.REPLACED, replaced.outcome());
        assertTrue(Files.readString(xslt).contains("at most 25"), "the new pack stands");
        assertEquals(List.of("example"), names(into));
    }

    @Test
    void replacesNoPackThatHoldsAFileItsManifestDoesNotList() throws IOException {
        byte[] schematron = ExamplePacks.schematron();
        ExamplePacks.fetch(into);
        Path mine = into.resolve("example/1.0/2026-09-30/my-notes.txt");
        Files.writeString(mine, "mine");
        byte[] revised = new String(schematron, StandardCharsets.UTF_8)
                .replace("at most 20", "at most 25").getBytes(StandardCharsets.UTF_8);

        PackException refused = assertThrows(PackException.class, () -> PackFetcher.fetch(
                ExamplePacks.recipe(revised), into, true,
                ExamplePacks.serving(Map.of(ExamplePacks.url(), revised)),
                ExamplePacks.TODAY, "esj test"));

        assertTrue(refused.getMessage().contains("my-notes.txt"), refused.getMessage());
        assertEquals("mine", Files.readString(mine));
        assertEquals(PackFetcher.Outcome.UNCHANGED, ExamplePacks.fetch(into).outcome(),
                "a file beside the same pack does not make it another pack");
    }

    @Test
    void writesIntoNoDirectoryThatHoldsSomethingElse() throws IOException {
        Path target = Files.createDirectories(into.resolve("example/1.0/2026-09-30"));
        Files.writeString(target.resolve("unrelated.txt"), "unrelated");

        PackException refused = assertThrows(PackException.class,
                () -> ExamplePacks.fetch(into));

        assertTrue(refused.getMessage().contains("holds no pack"), refused.getMessage());
        assertEquals(List.of("unrelated.txt"), names(target));
    }

    @Test
    void writesAManifestThisModuleReadsAndANoticeWithoutADate() throws IOException {
        Pack pack = ExamplePacks.fetch(into).pack();
        PackComponent rules = pack.components().stream()
                .filter(component -> component.name().equals("example-ubl-schematron"))
                .findFirst().orElseThrow();

        assertEquals(ComponentRole.SCHEMATRON_XSLT, rules.role());
        assertFalse(rules.unmodified(), "a compiled rule set is not the published file");
        assertEquals(ExamplePacks.url(), rules.obtainedFrom());
        assertEquals("Apache-2.0", rules.license());
        assertEquals(PackFetcher.NOTICE, rules.licenseFile());
        String notice = Files.readString(pack.location().orElseThrow().resolve("NOTICE"));
        assertTrue(notice.contains(PackFetcher.sha256(ExamplePacks.schematron())), notice);
        assertFalse(notice.contains("2026-09-30T") || notice.contains("retrieved"), notice);
        assertTrue(Files.readString(pack.location().orElseThrow().resolve("pack.json"))
                .contains("\"compiledWith\": \"" + SchematronCompiler.SKELETON));
    }

    /** Returns the names of the entries of a directory, sorted. */
    private static List<String> names(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
