package de.bsnsoft.esj.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * The upgrade engine on documents built for the purpose: what it moves, what it reports,
 * what it refuses and what it never does on its own.
 *
 * <p>Every test is skipped where this build carries no registry of the 2026 edition: the
 * files of that edition are separable and the profile {@code without-edition-2026} leaves
 * them out, and the engine then has no second edition to reach.
 */
class EditionUpgradeTest {

    private static final String E2017 = "EN16931-1:2017+A1:2019/AC:2020";

    private static final String E2026 = "EN16931-1:2026";

    static boolean carries2026() {
        return Registry.editions().contains("2026");
    }

    /** A document of the 2017 edition that is complete enough to satisfy the model. */
    private static SemanticDocument.Builder invoice() {
        return SemanticDocument.builder()
                .semanticModel(E2017)
                .put("/BT-1", "RE-1")
                .put("/BT-2", "2026-01-31")
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2017")
                .put("/BG-4/BT-27", "Seller")
                .put("/BG-4/BG-5/BT-40", "DE")
                .put("/BG-7/BT-44", "Buyer")
                .put("/BG-7/BG-8/BT-55", "DE")
                .put("/BG-22/BT-106", "100")
                .put("/BG-22/BT-109", "100")
                .put("/BG-22/BT-112", "119")
                .put("/BG-22/BT-115", "119")
                .put("/BG-23/0/BT-116", "100")
                .put("/BG-23/0/BT-117", "19")
                .put("/BG-23/0/BT-118", "S")
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "H87")
                .put("/BG-25/0/BT-131", "100")
                .put("/BG-25/0/BG-29/BT-146", "100")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-31/BT-153", "Item");
    }

    private static UpgradeResult to2026(SemanticDocument document) {
        return EditionUpgrade.apply(document, "2026", UpgradeOptions.defaults());
    }

    private static List<UpgradeNote> notes(UpgradeResult result, UpgradeNote.Kind kind) {
        List<UpgradeNote> found = new ArrayList<>();
        for (UpgradeNote note : result.report().notes()) {
            if (note.kind() == kind) {
                found.add(note);
            }
        }
        return found;
    }

    @Test
    @EnabledIf("carries2026")
    void theEditionsThisBuildCanWriteADocumentAsAreTheOnesItHasAMappingFor() {
        assertEquals(List.of("2026"), EditionUpgrade.targets(E2017));
        assertEquals(List.of("2017"), EditionUpgrade.targets(E2026));
        assertFalse(EditionUpgrade.isAvailable(E2017, "2017"));
        assertFalse(EditionUpgrade.isAvailable(E2017, "1999"));
        assertEquals(List.of(), EditionUpgrade.targets("EN16931-1:1999"));
    }

    @Test
    @EnabledIf("carries2026")
    void everyPathTheMappingListsIsAPathTheEngineMoves() {
        UpgradeMapping mapping = UpgradeMapping
                .between(Registry.forEdition("2017").edition(),
                        Registry.forEdition("2026").edition())
                .orElseThrow();
        SemanticDocument.Builder builder = invoice()
                .put("/BT-10", "KOST-1")
                .put("/BT-20", "Thirty days")
                .put("/BG-7/BT-46", "4711")
                .put("/BG-25/0/BG-26/BT-134", "2026-01-01")
                .put("/BG-25/0/BG-26/BT-135", "2026-01-31");
        UpgradeResult result = to2026(builder.build());
        assertTrue(result.isUpgraded(), result.report().notes().toString());
        assertEquals(new java.util.TreeSet<>(List.of("/BT-10/0", "/BG-33/0/BT-20",
                        "/BG-7/BT-46/0", "/BG-25/0/BG-37/BG-26/BT-134",
                        "/BG-25/0/BG-37/BG-26/BT-135")),
                new java.util.TreeSet<>(notes(result, UpgradeNote.Kind.PATH_REWRITTEN).stream()
                        .map(note -> note.message().substring("moved to ".length()))
                        .toList()));
        for (UpgradeMapping.PathRewrite rewrite : mapping.pathRewrites()) {
            if (rewrite.term().startsWith("BG-")) {
                continue;
            }
            assertTrue(result.require().values().keySet().stream()
                            .anyMatch(path -> path.termIds().equals(rewrite.to())),
                    rewrite.term() + " stands at the chain the mapping gives it");
        }
    }

    /**
     * An address the document's own edition calls wrong is not quietly put right on the way
     * to the other one. Both directions of the defect are refused: an occurrence index on a
     * term that has one occurrence, and none on a term that repeats.
     */
    @Test
    @EnabledIf("carries2026")
    void anOccurrenceIndexTheDocumentsOwnEditionDoesNotGiveIsRefusedRatherThanRepaired() {
        SemanticDocument.Builder indexed = invoice();
        indexed.remove(SemanticPath.of("/BT-1"));
        indexed.put("/BT-1/0", "RE-1");
        SemanticDocument.Builder unindexed = invoice();
        unindexed.remove(SemanticPath.of("/BG-25/0/BT-126"));
        unindexed.put("/BG-25/BT-126", "1");

        for (SemanticDocument.Builder wrong : List.of(indexed, unindexed)) {
            UpgradeResult result = to2026(wrong.build());

            assertFalse(result.isUpgraded(), "an invalid address is not upgraded");
            List<UpgradeNote> refused = notes(result, UpgradeNote.Kind.SOURCE_INDEX);
            assertEquals(1, refused.size(), result.report().notes().toString());
            assertEquals(UpgradeNote.Severity.REFUSAL, refused.get(0).severity());
            assertTrue(refused.get(0).message().contains(E2017),
                    "the refusal names the edition the document itself carries: "
                            + refused.get(0).message());
        }
    }

    @Test
    @EnabledIf("carries2026")
    void theModelOfTheDocumentIsTheOneOfTheTargetEdition() {
        UpgradeResult result = to2026(invoice().build());
        assertEquals(E2026, result.require().semanticModel());
        assertEquals(Registry.forEdition("2026").semanticModel(), result.report().to());
        assertEquals(E2017, result.report().from());
    }

    @Test
    @EnabledIf("carries2026")
    void anIdentifierWithoutTheSchemeTheEditionRequiresIsReportedAndNeverInvented() {
        UpgradeResult result = to2026(invoice().put("/BG-4/BT-29/0", "4711").build());
        List<UpgradeNote> reported = notes(result, UpgradeNote.Kind.SCHEME_MISSING);
        assertEquals(1, reported.size());
        assertEquals(Optional.of(SemanticPath.of("/BG-4/BT-29/0")), reported.get(0).path());
        assertEquals(UpgradeNote.Severity.OPEN_POINT, reported.get(0).severity());
        assertEquals(Optional.of("scheme-component-missing"), reported.get(0).point());
        assertTrue(result.report().statements().containsKey("scheme-component-missing"));
        assertEquals(SemanticValue.of("4711"),
                result.require().value(SemanticPath.of("/BG-4/BT-29/0")).orElseThrow());
    }

    @Test
    @EnabledIf("carries2026")
    void theSpecificationIdentifierIsLeftAsItStandsUnlessTheCallerNamesAnother() {
        UpgradeResult left = to2026(invoice().build());
        assertEquals(1, notes(left, UpgradeNote.Kind.SPECIFICATION_IDENTIFIER).size());
        assertEquals("urn:cen.eu:en16931:2017",
                left.require().value(SemanticPath.of("/BG-2/BT-24")).orElseThrow().content());

        UpgradeResult named = EditionUpgrade.apply(invoice().build(), "2026",
                UpgradeOptions.builder().specification("urn:cen.eu:en16931:2026").build());
        assertEquals(List.of(), notes(named, UpgradeNote.Kind.SPECIFICATION_IDENTIFIER));
        assertEquals(1, notes(named, UpgradeNote.Kind.SPECIFICATION_REPLACED).size());
        assertEquals("urn:cen.eu:en16931:2026",
                named.require().value(SemanticPath.of("/BG-2/BT-24")).orElseThrow().content());
    }

    @Test
    @EnabledIf("carries2026")
    void aValueOutsideTheBoundOfTheTargetEditionIsReportedAndNeverRounded() {
        UpgradeResult result = to2026(invoice()
                .put("/BG-20/0/BT-94", "1.234")
                .put("/BG-20/0/BT-92", "10")
                .put("/BG-20/0/BT-95", "S")
                .build());
        List<UpgradeNote> reported = notes(result, UpgradeNote.Kind.DECIMALS_OUT_OF_BOUNDS);
        assertEquals(1, reported.size());
        assertEquals(Optional.of(SemanticPath.of("/BG-20/0/BT-94")), reported.get(0).path());
        assertTrue(reported.get(0).message().contains("not rounded"));
        assertEquals("1.234",
                result.require().value(SemanticPath.of("/BG-20/0/BT-94")).orElseThrow()
                        .content());
    }

    @Test
    @EnabledIf("carries2026")
    void aBoundThatFollowsTheCurrencyIsNamedAsOneThisRunDidNotEvaluate() {
        UpgradeResult result = to2026(invoice().build());
        List<UpgradeNote> reported = notes(result, UpgradeNote.Kind.DECIMALS_NOT_EVALUATED);
        assertEquals(1, reported.size());
        assertEquals(UpgradeNote.Severity.INFORMATION, reported.get(0).severity());
        assertTrue(reported.get(0).message().contains("iso4217-minor-unit"));
    }

    @Test
    @EnabledIf("carries2026")
    void contentTheOlderEditionHasNoAddressForIsRefusedAndEveryPathIsNamed() {
        SemanticDocument document = to2026(invoice().build()).require().toBuilder()
                .put("/BT-166", "09:15:00+02:00")
                .put("/BG-33/0/BT-20", "Thirty days")
                .put("/BG-33/0/BG-35/0/BT-170", "2026-02-10")
                .build();
        UpgradeResult refused = EditionUpgrade.apply(document, "2017",
                UpgradeOptions.defaults());
        assertFalse(refused.isUpgraded());
        assertEquals(UpgradeResult.Outcome.REFUSED, refused.outcome());
        assertEquals(List.of("/BT-166", "/BG-33/0/BG-35/0/BT-170"),
                notes(refused, UpgradeNote.Kind.UNMAPPED).stream()
                        .map(note -> note.path().orElseThrow().toString()).toList());
        assertTrue(refused.report().statements().containsKey("unmapped-term"));
        assertThrows(java.util.NoSuchElementException.class, refused::require);

        UpgradeResult dropped = EditionUpgrade.apply(document, "2017",
                UpgradeOptions.builder()
                        .drop(SemanticPath.of("/BT-166"))
                        .drop(SemanticPath.group("/BG-33/0/BG-35"))
                        .build());
        assertTrue(dropped.isUpgraded(), dropped.report().notes().toString());
        assertEquals(2, dropped.report().dropped());
        assertEquals(List.of("/BT-166", "/BG-33/0/BG-35/0/BT-170"),
                notes(dropped, UpgradeNote.Kind.VALUE_DROPPED).stream()
                        .map(note -> note.path().orElseThrow().toString()).toList());
        assertEquals("Thirty days",
                dropped.require().value(SemanticPath.of("/BT-20")).orElseThrow().content());
    }

    @Test
    @EnabledIf("carries2026")
    void anOccurrenceTheOlderEditionCannotAddressIsRefusedAndNotTakenFirst() {
        SemanticDocument twice = to2026(invoice().build()).require().toBuilder()
                .put("/BG-33/0/BT-20", "Thirty days")
                .put("/BG-33/1/BT-20", "Or forty")
                .build();
        UpgradeResult refused = EditionUpgrade.apply(twice, "2017", UpgradeOptions.defaults());
        assertFalse(refused.isUpgraded());
        List<UpgradeNote> several = notes(refused, UpgradeNote.Kind.SEVERAL_OCCURRENCES);
        assertEquals(1, several.size());
        assertEquals(Optional.of("several-payment-terms"), several.get(0).point());

        SemanticDocument two = to2026(invoice().build()).require().toBuilder()
                .put("/BT-10/0", "KOST-1")
                .put("/BT-10/1", "KOST-2")
                .build();
        UpgradeResult second = EditionUpgrade.apply(two, "2017", UpgradeOptions.defaults());
        assertFalse(second.isUpgraded());
        assertEquals(Optional.of("several-occurrences"),
                notes(second, UpgradeNote.Kind.SEVERAL_OCCURRENCES).get(0).point());
    }

    @Test
    @EnabledIf("carries2026")
    void aResultThatDoesNotSatisfyTheTargetModelIsRefusedUnlessAPartialOneWasAskedFor() {
        SemanticDocument relaxed = to2026(invoice().build()).require().toBuilder()
                .remove("/BG-23/0/BT-117")
                .build();
        UpgradeResult refused = EditionUpgrade.apply(relaxed, "2017",
                UpgradeOptions.defaults());
        assertFalse(refused.isUpgraded());
        List<UpgradeNote> findings = notes(refused, UpgradeNote.Kind.MODEL_FINDING);
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).message().contains("BT-117"));

        UpgradeResult partial = EditionUpgrade.apply(relaxed, "2017",
                UpgradeOptions.builder().partial(true).build());
        assertTrue(partial.isUpgraded());
        assertEquals(UpgradeNote.Severity.OPEN_POINT,
                notes(partial, UpgradeNote.Kind.MODEL_FINDING).get(0).severity());
    }

    @Test
    @EnabledIf("carries2026")
    void aFindingTheDocumentAlreadyCarriesIsNotTheUpgradesDoing() {
        SemanticDocument incomplete = invoice().remove("/BG-25/0/BT-129").build();
        UpgradeResult result = to2026(incomplete);
        assertTrue(result.isUpgraded(), result.report().notes().toString());
        List<UpgradeNote> carried = notes(result, UpgradeNote.Kind.CARRIED_FINDING);
        assertEquals(1, carried.size());
        assertEquals(UpgradeNote.Severity.OPEN_POINT, carried.get(0).severity());
        assertTrue(carried.get(0).message().contains("BT-129"));
        assertTrue(carried.get(0).message().contains(E2017));
    }

    @Test
    @EnabledIf("carries2026")
    void aRunAskedForADocumentWithoutAnOpenPointRefusesWhereItLeavesOne() {
        UpgradeResult refused = EditionUpgrade.apply(invoice().build(), "2026",
                UpgradeOptions.builder().strict(true).build());
        assertFalse(refused.isUpgraded());
        assertEquals(1, notes(refused, UpgradeNote.Kind.STRICT).size());

        UpgradeResult clean = EditionUpgrade.apply(invoice().build(), "2026",
                UpgradeOptions.builder().strict(true)
                        .specification("urn:cen.eu:en16931:2026").build());
        assertTrue(clean.isUpgraded(), clean.report().notes().toString());
        assertTrue(clean.report().isClean(), clean.report().notes().toString());
    }

    @Test
    @EnabledIf("carries2026")
    void provenanceIsRecordedWhereTheCallerHandsOverTheBytesAndNotOtherwise() {
        SemanticDocument document = invoice()
                .source(SemanticDocument.Source.of("UBL", "a".repeat(64)))
                .build();
        UpgradeResult kept = to2026(document);
        assertEquals(Optional.of("UBL"), kept.require().source().orElseThrow().syntax());

        byte[] bytes = "the bytes the result derives from".getBytes(StandardCharsets.UTF_8);
        UpgradeResult recorded = EditionUpgrade.apply(document, "2026",
                UpgradeOptions.builder().source(bytes).build());
        SemanticDocument.Source source = recorded.require().source().orElseThrow();
        assertEquals(Optional.of("ESJ"), source.syntax());
        assertEquals(Optional.of(sha256(bytes)), source.sha256());
    }

    @Test
    @EnabledIf("carries2026")
    void extensionsAndTheirValuesAreCarriedUnchanged() {
        SemanticDocument document = invoice()
                .put("/BG-25/0/BG-DEX-01/0/BT-DEX-001", "sub line")
                .build();
        UpgradeResult result = to2026(document);
        assertTrue(result.isUpgraded(), result.report().notes().toString());
        assertEquals("sub line", result.require()
                .value(SemanticPath.of("/BG-25/0/BG-DEX-01/0/BT-DEX-001")).orElseThrow()
                .content());
        assertEquals(1, notes(result, UpgradeNote.Kind.EXTENSION_CARRIED).size());
    }

    @Test
    @EnabledIf("carries2026")
    void aDocumentOfTheEditionThatWasAskedForIsNotUpgraded() {
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> to2026(to2026(invoice().build()).require()));
        assertTrue(thrown.getMessage().contains("already"));
    }

    @Test
    void anEditionThisBuildDoesNotCarryIsSaidSoRatherThanGuessed() {
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> EditionUpgrade.apply(invoice().build(), "1999",
                        UpgradeOptions.defaults()));
        assertTrue(thrown.getMessage().contains("1999"));
        assertFalse(EditionUpgrade.isAvailable(E2017, "1999"));
    }

    private static String sha256(byte[] bytes) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder text = new StringBuilder();
            for (byte b : digest.digest(bytes)) {
                text.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
