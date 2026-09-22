package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.model.Registry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code esj upgrade} over the editions this build carries.
 *
 * <p>The tests that need the 2026 edition are skipped where the build carries no registry
 * of it, and one of them checks what the command says there instead: an edition a build
 * does not carry is answered with the code for a feature this version does not implement,
 * not with a wrong document.
 */
class UpgradeCommandTest {

    private static final String MINIMAL = "examples/minimal.esj.json";

    private static final String EDITION_2026 = "examples/edition-2026.esj.json";

    private static final String UBL = "conformance/kosit/business-cases/standard/"
            + "01.01a-INVOICE_ubl.xml";

    @TempDir
    private Path directory;

    static boolean carries2026() {
        return Registry.editions().contains("2026");
    }

    private Path out(String name) {
        return directory.resolve(name);
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @EnabledIf("carries2026")
    void aDocumentIsWrittenAsTheOtherEditionAndTheReportSaysWhatWasLeftOpen() {
        Path written = out("upgraded.esj.json");
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "upgrade", "-",
                "--to", "2026", "--out", written.toString());
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("1 open point"), run.err());
        assertTrue(run.err().contains("BT-24"), run.err());
        assertEquals("EN16931-1:2026",
                EsjReader.strict().read(read(written)).semanticModel());
    }

    @Test
    @EnabledIf("carries2026")
    void theProvenanceOfTheResultIsTheDigestOfTheBytesItWasDerivedFrom() {
        Path written = out("provenance.esj.json");
        byte[] input = Fixtures.bytes(MINIMAL);
        assertEquals(ExitCode.SUCCESS, Cli.run(input, "upgrade", "-", "--to", "2026",
                "--out", written.toString()).exitCode());
        String text = new String(read(written), StandardCharsets.UTF_8);
        assertTrue(text.contains("\"syntax\": \"ESJ\""), text);
        assertTrue(text.contains(digest(input)), text);
    }

    @Test
    @EnabledIf("carries2026")
    void aRoundTripThroughTheToolChangesNothingButTheProvenance() {
        Path up = out("up.esj.json");
        Path down = out("down.esj.json");
        byte[] input = Fixtures.bytes(MINIMAL);
        assertEquals(ExitCode.SUCCESS, Cli.run(input, "upgrade", "-", "--to", "2026",
                "--out", up.toString()).exitCode());
        assertEquals(ExitCode.SUCCESS, Cli.run(read(up), "upgrade", "-", "--to", "2017",
                "--out", down.toString()).exitCode());
        SemanticDocument before = EsjReader.strict().read(input);
        SemanticDocument after = EsjReader.strict().read(read(down));
        assertEquals(before.semanticModel(), after.semanticModel());
        assertEquals(before.values(), after.values());
        assertEquals(before.extensions(), after.extensions());
        assertEquals(java.util.Optional.of("ESJ"),
                after.source().orElseThrow().syntax());
        assertEquals(java.util.Optional.of(digest(read(up))),
                after.source().orElseThrow().sha256());
    }

    @Test
    @EnabledIf("carries2026")
    void contentTheOlderEditionHasNoAddressForIsRefusedAndTheReportNamesEveryPath() {
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026), "upgrade", "-", "--to", "2017",
                "--out", out("never.esj.json").toString());
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.err().contains("13 reasons"), run.err());
        assertTrue(run.err().contains("/BT-166"), run.err());
        assertTrue(run.err().contains("--drop"), run.err());
        assertFalse(Files.exists(out("never.esj.json")));
    }

    @Test
    @EnabledIf("carries2026")
    void thePathsTheCallerNamesAreDroppedAndEachStandsInTheReport() {
        Path written = out("downgraded.esj.json");
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026), "upgrade", "-", "--to", "2017",
                "--out", written.toString(),
                "--drop", "/BT-166",
                "--drop", "/BG-22/BG-34",
                "--drop", "/BG-25/0/BG-37/BT-185",
                "--drop", "/BG-25/0/BG-37/BT-187",
                "--drop", "/BG-25/0/BG-37/BG-38",
                "--drop", "/BG-25/0/BG-39",
                "--drop", "/BG-33/0/BG-35",
                "--drop", "/BG-33/0/BG-36");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("value-dropped /BT-166"), run.err());
        String text = new String(read(written), StandardCharsets.UTF_8);
        assertTrue(text.contains("\"/BT-20\""), text);
        assertTrue(text.contains("\"/BG-25/0/BG-26/BT-134\""), text);
        assertFalse(text.contains("BT-166"), text);
    }

    @Test
    @EnabledIf("carries2026")
    void theJsonReportCarriesTheNotesAndTheSentenceTheMappingStatesEachPointIn() {
        Path written = out("json.esj.json");
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "upgrade", "-", "--to", "2026",
                "--out", written.toString(), "--output", "json");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String report = run.text();
        assertTrue(report.contains("\"outcome\": \"upgraded\""), report);
        assertTrue(report.contains("\"from\": \"EN16931-1:2017+A1:2019/AC:2020\""), report);
        assertTrue(report.contains("\"to\": \"EN16931-1:2026\""), report);
        assertTrue(report.contains("\"point\": \"specification-identifier\""), report);
        assertTrue(report.contains("\"statements\""), report);
    }

    @Test
    @EnabledIf("carries2026")
    void theSpecificationIdentifierIsWrittenOnlyWhereTheCallerNamesIt() {
        Path written = out("specification.esj.json");
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "upgrade", "-", "--to", "2026",
                "--out", written.toString(), "--specification", "urn:cen.eu:en16931:2026");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(new String(read(written), StandardCharsets.UTF_8)
                .contains("urn:cen.eu:en16931:2026"));
    }

    @Test
    @EnabledIf("carries2026")
    void anUpgradeAskedToLeaveNoOpenPointRefusesWhereItWouldLeaveOne() {
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "upgrade", "-", "--to", "2026",
                "--out", out("strict.esj.json").toString(), "--refuse-open-points");
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.err().contains("open point"), run.err());
    }

    @Test
    @EnabledIf("carries2026")
    void aDocumentThatAlreadyNamesTheEditionIsNotUpgraded() {
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "upgrade", "-", "--to", "2017");
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("already names the edition"), run.err());
    }

    @Test
    void anXmlInputIsSentThroughTheImporterFirst() {
        Cli.Run run = Cli.run(Fixtures.bytes(UBL), "upgrade", "-", "--to", "2026");
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("esj convert --to esj"), run.err());
    }

    @Test
    void anEditionThisBuildCarriesNoRegistryOfIsNotImplementedRatherThanGuessed() {
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "upgrade", "-", "--to", "1999");
        assertEquals(ExitCode.UNSUPPORTED, run.exitCode(), run.err());
        assertTrue(run.err().contains("1999"), run.err());
    }

    @Test
    void theEditionOfATargetThisBuildDoesNotCarryIsAnsweredTheSameWay() {
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "upgrade", "-", "--to", "2026",
                "--out", out("either.esj.json").toString());
        assertEquals(carries2026() ? ExitCode.SUCCESS : ExitCode.UNSUPPORTED,
                run.exitCode(), run.err());
    }

    @Test
    void theJsonReportNeedsSomewhereForTheDocumentToGo() {
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "upgrade", "-", "--to", "2026",
                "--output", "json");
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("--out"), run.err());
    }

    @Test
    void aDropThatIsNoSemanticPathIsRefused() {
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "upgrade", "-", "--to", "2026",
                "--out", out("bad.esj.json").toString(), "--drop", "BT-1");
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("--drop"), run.err());
    }

    private static String digest(byte[] bytes) {
        try {
            java.security.MessageDigest sha =
                    java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder text = new StringBuilder();
            for (byte b : sha.digest(bytes)) {
                text.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
