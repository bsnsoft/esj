package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.Packs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pack directories on the command line: {@code --packs}, {@code ESJ_PACKS}, the listing,
 * and {@code esj packs} — all of it without the network.
 *
 * <p>The pack of a directory here is made by the test out of the bundled one: its UBL
 * schema and its EN 16931 UBL rule set, under an identity of its own, for a profile no
 * bundled pack knows. It is a pack in every respect the engine looks at, which is what a
 * pack directory offers.
 */
class PackDirectoriesTest {

    private static final String UBL =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml";

    private static final String XRECHNUNG =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    private static final String PROFILE =
            "urn:cen.eu:en16931:2017#compliant#urn:esj.example:directory:1.0";

    private static final String PEPPOL =
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0";

    private static final String IDENTITY = "example/1.0/2026-09-30";

    @TempDir
    Path directory;

    @Test
    void listsTheOriginOfEveryPackAndTheRecipes() {
        Cli.Run run = Cli.run("--list-packs");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("xrechnung/3.0.2/2026-08-31" + System.lineSeparator()
                + "  XRechnung 3.0.2 validation pack, release 2026-08-31"
                + System.lineSeparator() + "  origin bundled"), run.text());
        assertTrue(run.text().contains("peppol-bis-billing-3.0.20" + System.lineSeparator()
                + "  Peppol BIS Billing 3.0 validation artefacts, as published by OpenPeppol,"
                + " release 3.0.20"), run.text());
        assertTrue(run.text().contains("peppol-bis-billing-3.0.21 (newest release; esj packs"
                + " fetch peppol-bis-billing follows it)" + System.lineSeparator()), run.text());
        assertTrue(run.text().contains("at commit 806866bd2bd91d7e9623b68f08164e8fbe9e67a0"
                + " (branch 2026-Q2-QA2)"), run.text());
        assertTrue(run.text().contains("at tag v3.0.20"), run.text());
        assertTrue(run.text().contains("not fetched into any pack directory of this run"),
                run.text());
    }

    @Test
    void listsThePacksOfADirectoryWithTheirOrigin() throws IOException {
        Path pack = pack(directory);

        Cli.Run listed = Cli.run("packs", "list", "--packs", directory.toString());
        assertEquals(ExitCode.SUCCESS, listed.exitCode(), listed.err());
        assertTrue(listed.text().contains(IDENTITY + System.lineSeparator()
                + "  Example rules" + System.lineSeparator() + "  origin "
                + pack.toRealPath()), listed.text());
        assertTrue(listed.text().contains("each weighed against the SHA-256 its pack.json"
                + " records"), listed.text());

        Cli.Run fromEnvironment = Cli.runWith(Map.of(PackChoice.ENVIRONMENT,
                directory.toString()), "--list-packs");
        assertEquals(listed.text(), fromEnvironment.text(),
                "ESJ_PACKS and --packs name the same directory the same way");
    }

    @Test
    void anEmptyPackDirectoryChangesNothing() throws IOException {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        Cli.Run plain = Cli.run("validate", Fixtures.file(directory, UBL));

        Cli.Run withPacks = Cli.run("validate", "--packs", empty.toString(),
                Fixtures.file(directory, UBL));

        assertEquals(ExitCode.SUCCESS, withPacks.exitCode(), withPacks.err());
        assertEquals(plain.text(), withPacks.text());
    }

    @Test
    void refusesAPackDirectoryThatIsNotThere() {
        Cli.Run run = Cli.run("validate", "--packs", directory.resolve("absent").toString(),
                Fixtures.file(directory, UBL));

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("does not exist"), run.err());
    }

    @Test
    void runsThePackOfADirectoryForItsProfile() throws IOException {
        Path pack = pack(directory.resolve("packs"));
        String invoice = invoice(PROFILE);

        Cli.Run run = Cli.run("validate", "--packs", directory.resolve("packs").toString(),
                invoice);

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains("Profile:          " + PROFILE + " (pack " + IDENTITY
                + ", from " + pack.toRealPath() + ")"), run.text());
        assertTrue(run.text().matches("(?s).*  example UBL Schematron: +OK\\R.*"), run.text());

        Cli.Run json = Cli.runWith(Map.of(PackChoice.ENVIRONMENT,
                directory.resolve("packs").toString()), "validate", "--output", "json", invoice);
        assertEquals(ExitCode.SUCCESS, json.exitCode(), json.err());
        assertTrue(json.text().contains("\"source\": \"directory\","), json.text());
        assertTrue(json.text().contains("\"location\": \"" + pack.toRealPath() + "\""),
                json.text());

        Cli.Run other = Cli.run("validate", "--packs", directory.resolve("packs").toString(),
                Fixtures.file(directory, UBL));
        assertTrue(other.text().contains("(pack xrechnung/3.0.2/2026-08-31)"),
                "a document of another profile keeps the bundled pack: " + other.text());
    }

    @Test
    void namesThePackOfADirectoryInTheReportFile() throws IOException {
        Path pack = pack(directory.resolve("packs"));

        Cli.Run run = Cli.run("validate", "--packs", directory.resolve("packs").toString(),
                "--report", "-", "--report-format", "html", "--report-lang", "en",
                "--no-invoice", invoice(PROFILE));

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("(from " + pack.toRealPath() + ")"), run.text());
    }

    @Test
    void choosesThePackOfADirectoryByItsIdentityAndInspectNamesIt() throws IOException {
        Path pack = pack(directory.resolve("packs"));
        String invoice = invoice(XRECHNUNG);

        Cli.Run chosen = Cli.run("validate", "--packs", directory.resolve("packs").toString(),
                "--pack", IDENTITY, invoice);
        assertTrue(chosen.text().contains("(pack " + IDENTITY + ", from "), chosen.text());

        Cli.Run inspected = Cli.run("inspect", "--packs", directory.resolve("packs").toString(),
                invoice(PROFILE));
        assertTrue(inspected.text().contains("Validation pack:            " + IDENTITY
                + " (from " + pack.toRealPath() + ")"), inspected.text());
    }

    @Test
    void refusesAPackDirectoryThatReusesTheIdentityOfABundledPack() throws IOException {
        Path pack = pack(directory);
        Path bundled = Files.createDirectories(directory.resolve("xrechnung/3.0.2"));
        Files.move(pack, bundled.resolve("2026-08-31"));
        Path manifest = bundled.resolve("2026-08-31/pack.json");
        Files.writeString(manifest, Files.readString(manifest)
                .replace("\"id\": \"example\"", "\"id\": \"xrechnung\"")
                .replace("\"version\": \"1.0\"", "\"version\": \"3.0.2\"")
                .replace("\"release\": \"2026-09-30\"", "\"release\": \"2026-08-31\""));

        Cli.Run run = Cli.run("validate", "--packs", directory.toString(),
                Fixtures.file(directory, UBL));

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("the identity of a pack this build carries"), run.err());
    }

    @Test
    void namesTheRecipeThatBringsTheRulesOfAProfileItFoundNoneFor() {
        Cli.Run run = Cli.run("validate", invoice(PEPPOL));

        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains("esj packs fetch peppol-bis-billing-3.0.21 --into"
                + " <directory>"), run.text());
        assertFalse(Cli.run("validate", invoice(PROFILE)).text().contains("esj packs fetch"),
                "a profile no recipe is written for gets no such line");
    }

    @Test
    void fetchRefusesARecipeThisBuildDoesNotCarryAndWritesNothing() throws IOException {
        Path into = directory.resolve("into");

        Cli.Run run = Cli.run("packs", "fetch", "peppol", "--into", into.toString());

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("peppol-bis-billing-3.0.21, peppol-bis-billing-3.0.20"),
                run.err());
        assertFalse(Files.exists(into));
    }

    @Test
    void packsAloneShowsItsUsage() {
        Cli.Run run = Cli.run("packs");

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("fetch") && run.err().contains("list"), run.err());
    }

    /** Writes the first corpus invoice, naming the given profile, and returns its path. */
    private String invoice(String profile) {
        Path file = directory.resolve("invoice-" + Math.abs(profile.hashCode()) + ".xml");
        try {
            Files.writeString(file, Fixtures.text(UBL).replace(XRECHNUNG, profile),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return file.toString();
    }

    /**
     * Writes a pack into a pack directory: the UBL schema and the EN 16931 UBL rule set of
     * the bundled pack, for {@link #PROFILE} alone, and returns its directory.
     */
    static Path pack(Path packs) throws IOException {
        Pack bundled = Packs.bundled("xrechnung/3.0.2/2026-08-31");
        Path root = Files.createDirectories(packs.resolve(IDENTITY));
        List<String> files = bundled.files().keySet().stream()
                .filter(path -> path.startsWith("xsd/ubl-2.1/")
                        || path.equals("cen/1.3.16/EN16931-UBL-validation.xslt")
                        || path.equals("cen/1.3.16/LICENSE"))
                .toList();
        StringBuilder inventory = new StringBuilder();
        for (String path : files) {
            Path target = root.resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, bundled.read(path));
            inventory.append(inventory.length() == 0 ? "" : ",\n").append("    { \"path\": \"")
                    .append(path).append("\", \"sha256\": \"").append(bundled.files().get(path))
                    .append("\" }");
        }
        String schema = bundled.components().get(0).files().stream()
                .map(path -> "\"" + path + "\"").reduce((a, b) -> a + ", " + b).orElseThrow();
        String manifest = """
                {
                  "format": "esj-validation-pack",
                  "formatVersion": "1",
                  "id": "example",
                  "version": "1.0",
                  "release": "2026-09-30",
                  "title": "Example rules",
                  "retrieved": "2026-09-30",
                  "components": [
                    {
                      "name": "ubl-2.1-xsd",
                      "role": "xsd",
                      "appliesTo": { "syntax": [ "ubl-invoice", "ubl-creditnote" ], "profile": [ "*" ] },
                      "entry": {
                        "ubl-invoice": "xsd/ubl-2.1/maindoc/UBL-Invoice-2.1.xsd",
                        "ubl-creditnote": "xsd/ubl-2.1/maindoc/UBL-CreditNote-2.1.xsd"
                      },
                      "files": [ %1$s ],
                      "license": "LicenseRef-OASIS-UBL-2.1",
                      "licenseFile": "xsd/ubl-2.1/NOTICE",
                      "source": "http://docs.oasis-open.org/ubl/os-UBL-2.1/",
                      "obtainedFrom": "the bundled pack xrechnung/3.0.2/2026-08-31",
                      "unmodified": true
                    },
                    {
                      "name": "example-ubl-schematron",
                      "role": "schematron-xslt",
                      "appliesTo": { "syntax": [ "ubl-invoice", "ubl-creditnote" ], "profile": [ "%2$s" ] },
                      "entry": {
                        "ubl-invoice": "cen/1.3.16/EN16931-UBL-validation.xslt",
                        "ubl-creditnote": "cen/1.3.16/EN16931-UBL-validation.xslt"
                      },
                      "files": [ "cen/1.3.16/EN16931-UBL-validation.xslt" ],
                      "license": "EUPL-1.2",
                      "licenseFile": "cen/1.3.16/LICENSE",
                      "source": "https://github.com/ConnectingEurope/eInvoicing-EN16931",
                      "obtainedFrom": "the bundled pack xrechnung/3.0.2/2026-08-31",
                      "unmodified": true
                    }
                  ],
                  "files": [
                %3$s
                  ]
                }
                """.formatted(schema, PROFILE, inventory);
        Files.writeString(root.resolve("pack.json"), manifest, StandardCharsets.UTF_8);
        return root;
    }
}
