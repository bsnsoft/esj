package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.xr.XrSyntax;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a pack says about itself, and which of its components a document gets.
 */
class PacksTest {

    /** The customization identifier of the XRechnung 3.0 core invoice usage specification. */
    private static final String XRECHNUNG =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    /** The customization identifier of the XRechnung CVD profile. */
    private static final String XRECHNUNG_CVD = XRECHNUNG
            + "#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_0.9";

    private final Pack pack = Packs.bundled("xrechnung/3.0.2/2026-08-31");

    @Test
    void namesItselfByProfileVersionAndRelease() {
        assertEquals("xrechnung", pack.id());
        assertEquals("3.0.2", pack.version());
        assertEquals("2026-08-31", pack.release());
        assertEquals("xrechnung/3.0.2/2026-08-31", pack.directory());
        assertEquals(27, pack.files().size(), "the inventory lists every file of the pack"
                + " but the manifest itself");
    }

    @Test
    void namesTheLicenceOfEveryComponent() {
        assertEquals(Set.of("Apache-2.0", "EUPL-1.2", "LicenseRef-OASIS-UBL-2.1",
                        "LicenseRef-UN-CEFACT-D16B"),
                Set.copyOf(pack.licenses()),
                "a report can name the licence of everything that ran");
        assertTrue(pack.components().stream().allMatch(PackComponent::unmodified),
                "every component is the published file, unmodified");
    }

    @Test
    void appliesTheSchemaAndBothRuleSetsToAnXRechnungInvoice() {
        PackSelection selection = pack.select(XrSyntax.UBL_INVOICE, XRECHNUNG);

        assertEquals(List.of("ubl-2.1-xsd", "en16931-ubl-schematron",
                        "xrechnung-ubl-schematron"),
                selection.applied().stream().map(PackComponent::name).toList());
        assertEquals(Optional.empty(), selection.profileNote());
        assertEquals(3, selection.skipped().size(), "the components of the other syntax"
                + " are skipped, and the report says why");
    }

    @Test
    void appliesTheComponentsOfTheSyntaxTheDocumentIsWrittenIn() {
        assertEquals(List.of("cii-d16b-xsd", "en16931-cii-schematron",
                        "xrechnung-cii-schematron"),
                pack.select(XrSyntax.CII, XRECHNUNG).applied().stream()
                        .map(PackComponent::name).toList());
        assertEquals(List.of("ubl-2.1-xsd", "en16931-ubl-schematron",
                        "xrechnung-ubl-schematron"),
                pack.select(XrSyntax.UBL_CREDIT_NOTE, XRECHNUNG).applied().stream()
                        .map(PackComponent::name).toList());
    }

    @Test
    void appliesTheCenArtefactsToAProfileWhoseCiusItDoesNotKnow() {
        PackSelection selection = pack.select(XrSyntax.UBL_INVOICE,
                "urn:cen.eu:en16931:2017#compliant#urn:example.org:cius:1.0");

        assertEquals(List.of("ubl-2.1-xsd", "en16931-ubl-schematron"),
                selection.applied().stream().map(PackComponent::name).toList());
        assertEquals(Optional.of("no core invoice usage specification of this pack applies"
                        + " to this profile: the EN 16931 artefacts ran, no CIUS rules did"),
                selection.profileNote());
        assertTrue(selection.profileRulesSkipped(),
                "a rule set was left out for the profile, and a program is entitled to"
                        + " know it without reading the sentence");
    }

    @Test
    void appliesOnlyTheSchemaToAProfileOfNoSpecificationItKnows() {
        PackSelection selection = pack.select(XrSyntax.UBL_INVOICE, "urn:example.org:own:1");

        assertEquals(List.of("ubl-2.1-xsd"),
                selection.applied().stream().map(PackComponent::name).toList());
        assertEquals(Optional.of("no rule set of this pack applies to this profile: schema"
                        + " validation only"),
                selection.profileNote());
        assertTrue(selection.profileRulesSkipped());
        assertFalse(pack.select(XrSyntax.UBL_INVOICE, XRECHNUNG).profileRulesSkipped(),
                "and a document of a profile the pack knows had nothing left out for it");
    }

    @Test
    void choosesTheBundledPackThatRecognizesTheProfile() {
        assertEquals(pack.directory(),
                Packs.select(XrSyntax.UBL_INVOICE, XRECHNUNG).pack().directory());
    }

    @Test
    void readsOnlyTheFilesItsManifestLists() {
        assertTrue(pack.read("cen/1.3.16/LICENSE").length > 0,
                "a file the inventory lists is read");

        assertThrows(PackException.class, () -> pack.read("pack.json"));
        assertThrows(PackException.class, () -> pack.read("cen/1.3.16/LICENSE.txt"));
        assertThrows(PackException.class, () -> pack.read("../SOURCES.md"));
        assertThrows(PackException.class, () -> Packs.open("/etc/hosts"));
        assertThrows(PackException.class, () -> Packs.open("xrechnung/../../secret"));
    }

    @Test
    void namesTheBundledPacksItHasNot() {
        assertThrows(PackException.class, () -> Packs.bundled("xrechnung/9.9.9/1970-01-01"));
    }

    /**
     * A pack a caller points at is read the same way a bundled one is. The directory of
     * this test is the bundled pack written out file by file, so the two answers have to
     * be the same answer.
     */
    @Test
    void readsAPackFromADirectory(@TempDir Path directory) throws IOException {
        Files.write(directory.resolve("pack.json"), manifest());
        for (String file : pack.files().keySet()) {
            Path target = directory.resolve(file);
            Files.createDirectories(target.getParent());
            Files.write(target, pack.read(file));
        }

        Pack fromDirectory = Packs.fromDirectory(directory);

        assertEquals(pack.directory(), fromDirectory.directory());
        assertEquals(pack.files(), fromDirectory.files());
        assertEquals(pack.components(), fromDirectory.components());
        assertEquals(List.of("ubl-2.1-xsd", "en16931-ubl-schematron",
                        "xrechnung-ubl-schematron"),
                fromDirectory.select(XrSyntax.UBL_INVOICE, XRECHNUNG).applied().stream()
                        .map(PackComponent::name).toList());
    }

    @Test
    void refusesADirectoryThatHoldsNoManifest(@TempDir Path directory) {
        assertThrows(PackException.class, () -> Packs.fromDirectory(directory));
    }

    /**
     * The inventory of a manifest is a check rather than documentation. Nothing else
     * establishes that what a run executed is what {@code packs/SOURCES.md} describes,
     * and for a directory a caller points at nothing else establishes it at all.
     */
    @Test
    void refusesAFileThatIsNotTheOneItsManifestRecords(@TempDir Path directory)
            throws IOException {
        Files.write(directory.resolve("pack.json"), manifest());
        for (String file : pack.files().keySet()) {
            Path target = directory.resolve(file);
            Files.createDirectories(target.getParent());
            Files.write(target, pack.read(file));
        }
        Path altered = directory.resolve("cen/1.3.16/LICENSE");
        Files.write(altered, "a line that was not in the released file\n".getBytes(
                StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);

        Pack supplied = Packs.fromDirectory(directory);
        PackException refused = assertThrows(PackException.class,
                () -> supplied.read("cen/1.3.16/LICENSE"));

        assertTrue(refused.getMessage().contains("is not the file its manifest records"),
                refused.getMessage());
    }

    /**
     * A directory pack is a pack of its own even where it calls itself by the identity of
     * a packaged one, and what one of them compiles is never handed to the other.
     *
     * <p>The identity in a {@code pack.json} is the pack's own account of itself, and a
     * directory is free to write anything there. A process that had cached an artefact
     * under that account would run a stranger's stylesheet under the name of the packaged
     * one for the rest of its life — and report the packaged pack as the source of what it
     * found.
     */
    @Test
    void keepsASuppliedPackApartFromThePackagedOneItCallsItself(@TempDir Path directory)
            throws IOException {
        Files.write(directory.resolve("pack.json"), manifest());
        for (String file : pack.files().keySet()) {
            Path target = directory.resolve(file);
            Files.createDirectories(target.getParent());
            Files.write(target, pack.read(file));
        }
        Pack supplied = Packs.fromDirectory(directory);

        assertEquals(pack.directory(), supplied.directory(),
                "the directory calls itself what the packaged pack is called");
        String entry = "cen/1.3.16/EN16931-UBL-validation.xslt";
        assertNotEquals(pack.cacheKey(entry), supplied.cacheKey(entry),
                "and the two are nonetheless cached apart");
    }

    /**
     * A link inside a pack directory that points out of it names a file outside it, and
     * the file a manifest names is read from below the directory or not at all.
     */
    @Test
    void refusesAFileALinkLeadsOutOfTheDirectory(@TempDir Path directory)
            throws IOException {
        Path outside = Files.createTempDirectory("esj-outside");
        outside.toFile().deleteOnExit();
        Path target = Files.write(outside.resolve("elsewhere.xslt"),
                "<not-a-stylesheet/>".getBytes(StandardCharsets.UTF_8));
        target.toFile().deleteOnExit();
        Files.write(directory.resolve("pack.json"), manifest());
        for (String file : pack.files().keySet()) {
            Path inside = directory.resolve(file);
            Files.createDirectories(inside.getParent());
            Files.write(inside, pack.read(file));
        }
        Path linked = directory.resolve("cen/1.3.16/EN16931-UBL-validation.xslt");
        Files.delete(linked);
        try {
            Files.createSymbolicLink(linked, target);
        } catch (UnsupportedOperationException | IOException e) {
            return; // A file system without links cannot be asked this question.
        }

        try {
            Pack supplied = Packs.fromDirectory(directory);
            PackException refused = assertThrows(PackException.class,
                    () -> supplied.read("cen/1.3.16/EN16931-UBL-validation.xslt"));

            assertTrue(refused.getMessage().contains("stays inside the pack directory"),
                    refused.getMessage());
        } finally {
            // The link goes before the temporary directory is cleaned up, so that the
            // cleanup has no link out of its own tree to reason about.
            Files.deleteIfExists(linked);
            Files.deleteIfExists(target);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void carriesTheLevelsEachProfileGivesRules() {
        assertEquals(List.of("xrechnung-ubl-invoice", "xrechnung-extension-ubl-invoice",
                        "xrechnung-cvd-ubl-invoice", "xrechnung-ubl-creditnote",
                        "xrechnung-cvd-ubl-creditnote", "xrechnung-cii",
                        "xrechnung-extension-cii", "xrechnung-cvd-cii"),
                pack.levels().stream().map(PackLevels::name).toList(),
                "one table per profile of the specification and syntax it is written in");
        assertTrue(pack.levels().stream().noneMatch(table -> table.levels().isEmpty()),
                "a table that levels nothing is not a table");
    }

    @Test
    void givesADocumentTheLevelsOfItsOwnProfile() {
        PackLevels standard = pack.select(XrSyntax.UBL_INVOICE, XRECHNUNG).levels()
                .orElseThrow();
        PackLevels cvd = pack.select(XrSyntax.UBL_INVOICE, XRECHNUNG_CVD).levels()
                .orElseThrow();

        assertEquals("xrechnung-ubl-invoice", standard.name());
        assertEquals("xrechnung-cvd-ubl-invoice", cvd.name());
        assertEquals(Optional.empty(), standard.level("BR-CL-13"),
                "the standard profile says nothing about the rule, so its artefact's flag"
                        + " stands");
        assertEquals(Optional.of(Severity.INFORMATION), cvd.level("BR-CL-13"),
                "and the CVD profile, whose item classification scheme the EN 16931 code"
                        + " list does not carry, levels it down for its own documents");
        assertEquals(Optional.of(Severity.FATAL), standard.level("UBL-CR-646"),
                "the levels go the other way too: a rule the artefact flags as a warning"
                        + " is one the standard profile refuses a document over");
    }

    @Test
    void givesNoLevelsToAProfileNoTableNames() {
        assertEquals(Optional.empty(),
                pack.select(XrSyntax.UBL_INVOICE, "urn:cen.eu:en16931:2017").levels(),
                "a document that names EN 16931 and no specification beyond it is judged"
                        + " on the flags of the artefacts alone");
        assertEquals(Optional.empty(),
                pack.select(XrSyntax.CII, "urn:example.org:own:1").levels());
    }

    @Test
    void refusesAManifestWithALevelItCannotAct(@TempDir Path directory)
            throws IOException {
        Files.write(directory.resolve("pack.json"),
                new String(manifest(), StandardCharsets.UTF_8)
                        .replace("\"BR-CL-13\": \"information\"",
                                "\"BR-CL-13\": \"advisory\"")
                        .getBytes(StandardCharsets.UTF_8));

        PackException refused = assertThrows(PackException.class,
                () -> Packs.fromDirectory(directory));

        assertTrue(refused.getMessage().contains("advisory"),
                "a level this module cannot act on would change a verdict without anybody"
                        + " being able to see how, and the message says which one it was:"
                        + " " + refused.getMessage());
    }

    /** The manifest of the bundled pack, which its own inventory does not list. */
    private byte[] manifest() throws IOException {
        try (java.io.InputStream in = Packs.open(pack.directory() + "/pack.json")) {
            return in.readAllBytes();
        }
    }
}
