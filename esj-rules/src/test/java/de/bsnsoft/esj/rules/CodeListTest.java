package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The code list snapshots, the loader and the membership operator. */
class CodeListTest {

    private static final String RESOURCE = "/codelists/untdid-5305/2026-09-19.json";

    private static CodeList fixture() {
        try (InputStream in = CodeListTest.class.getResourceAsStream(RESOURCE)) {
            return CodeLists.read(in, "the test fixture");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CodeList read(String json) {
        try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return CodeLists.read(in, "a snapshot the test wrote");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<RuleFinding> run(SemanticDocument document, String assertion) {
        return RuleEngine.compile(
                Packs.read(Packs.file(Packs.rule("BR-CL-01", "/BG-23/*", assertion))),
                Packs.REGISTRY, CodeLists.of(List.of(fixture())), JavaRules.none())
                .evaluate(document);
    }

    @Test
    void aSnapshotCarriesItsCodesItsDescriptionsAndItsProvenance() {
        CodeList list = fixture();

        assertEquals("untdid-5305", list.listId());
        assertEquals("2026-09-19", list.retrieved());
        assertEquals(4, list.size());
        assertTrue(list.contains("S"));
        assertEquals(Optional.of("Standard rate"), list.describe("S"));
        assertEquals(Optional.of(""), list.describe("AE"));
        assertEquals(Optional.empty(), list.describe("X"));
        assertEquals(Optional.of(list), CodeLists.of(List.of(list)).list("untdid-5305"));
        assertEquals(Optional.empty(), CodeLists.of(List.of(list)).list("iso-4217"));
    }

    @Test
    void membershipIsExactAndDoesNotRepairASpelling() {
        CodeList list = fixture();

        assertTrue(list.contains("S"));
        assertFalse(list.contains("s"));
        assertFalse(list.contains(" S"));
    }

    @Test
    void inListDecidesAgainstTheSnapshotThePackNames() {
        assertEquals(List.of(), run(Documents.minimal().build(),
                "{\"inList\": [{\"value\": \"/BT-118\"}, \"untdid-5305\"]}"));
    }

    @Test
    void aCodeThatIsNotOnTheListFailsTheRule() {
        SemanticDocument document = Documents.set(Documents.minimal(), "/BG-23/0/BT-118", "QQ").build();

        List<RuleFinding> findings = run(document,
                "{\"inList\": [{\"value\": \"/BT-118\"}, \"untdid-5305\"]}");

        assertEquals(1, findings.size());
        assertEquals(RuleCategory.EN_CL, findings.get(0).category());
    }

    @Test
    void aPackThatNamesAListWithNoSnapshotDoesNotCompile() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.run(Documents.minimal().build(),
                        "{\"inList\": [{\"value\": \"/BT-5\"}, \"iso-4217\"]}"));

        assertTrue(refused.getMessage().contains("no snapshot of the code list iso-4217"));
    }

    @Test
    void aManifestThatNamesASnapshotNobodyLoadedDoesNotCompile() {
        String pack = "{\"id\": \"test\", \"version\": \"1\", \"verifiedAgainst\": \"nothing\","
                + " \"description\": \"x\", \"codeLists\": {\"iso-4217\": \"2026-09-19\"},"
                + " \"rules\": []}";

        RulePackException refused = assertThrows(RulePackException.class, () -> RuleEngine
                .compile(Packs.read(pack), Packs.REGISTRY, CodeLists.empty(), JavaRules.none()));

        assertTrue(refused.getMessage().contains("iso-4217"));
    }

    @Test
    void aSnapshotWithACodeTwiceIsRefused() {
        RulePackException refused = assertThrows(RulePackException.class, () -> read(
                "{\"listId\": \"x\", \"name\": \"x\", \"publisher\": \"x\", \"source\": \"x\","
                        + " \"retrieved\": \"2026-09-19\","
                        + " \"entries\": [{\"value\": \"S\"}, {\"value\": \"S\"}]}"));

        assertTrue(refused.getMessage().contains("twice"));
    }

    @Test
    void aSnapshotWithoutItsProvenanceIsRefused() {
        RulePackException refused = assertThrows(RulePackException.class, () -> read(
                "{\"listId\": \"x\", \"name\": \"x\", \"retrieved\": \"2026-09-19\","
                        + " \"entries\": []}"));

        assertTrue(refused.getMessage().contains("publisher"));
    }

    @Test
    void aSnapshotWithAMemberTheFormatDoesNotDefineIsRefused() {
        RulePackException refused = assertThrows(RulePackException.class, () -> read(
                "{\"listId\": \"x\", \"name\": \"x\", \"publisher\": \"x\", \"source\": \"x\","
                        + " \"retrieved\": \"2026-09-19\", \"entries\": [], \"version\": \"3\"}"));

        assertTrue(refused.getMessage().contains("version"));
    }
}
