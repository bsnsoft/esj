package de.bsnsoft.esj.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.EsjFormatException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Checks that the registry loader carries more than one edition of the core model and
 * keeps them apart: an edition is data the build ships, a path is an address relative to
 * one edition, and the loader hands out the registry of the edition that was asked for
 * and no other (specification, section 10).
 */
class EditionsTest {

    /**
     * Whether this build carries the registry of the 2026 edition. The tests that need it
     * are skipped where it does not, because the Maven profile
     * {@code without-edition-2026} builds a distribution without that edition's files and
     * the loader is expected to work there as well.
     */
    static boolean carries2026() {
        return Registry.editions().contains("2026");
    }

    @Test
    void theBuildCarriesAtLeastTheDefaultEdition() {
        assertTrue(Registry.editions().contains(Registry.DEFAULT_EDITION));
        assertEquals("2017", Registry.DEFAULT_EDITION);
    }

    @Test
    void theEditionsAreListedInTheOrderTheyWerePublished() {
        List<String> editions = Registry.editions();
        assertEquals(editions.stream().sorted().toList(), editions);
    }

    @Test
    void theCoreRegistryIsTheOneOfTheDefaultEdition() {
        assertSame(Registry.forEdition(Registry.DEFAULT_EDITION), Registry.en16931());
        assertEquals("EN 16931-1:2017+A1:2019/AC:2020", Registry.en16931().edition());
    }

    @Test
    void aRegistryIsReadOnceAndShared() {
        for (String edition : Registry.editions()) {
            assertSame(Registry.forEdition(edition), Registry.forEdition(edition));
        }
    }

    @Test
    void anEditionTheBuildDoesNotCarryIsSaidSoRatherThanGuessed() {
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> Registry.forEdition("1999"));
        assertTrue(thrown.getMessage().contains("1999"));
        assertTrue(thrown.getMessage().contains(Registry.DEFAULT_EDITION));
    }

    @Test
    void everyEditionOnTheClasspathIsReadable() {
        for (String edition : Registry.editions()) {
            Registry registry = Registry.forEdition(edition);
            assertEquals("EN16931-1", registry.model());
            assertFalse(registry.terms().isEmpty());
            assertEquals(List.of(), registry.imports(), edition);
        }
    }

    @Test
    void aDocumentIsMeasuredAgainstTheRegistryOfItsOwnEdition() {
        for (String edition : Registry.editions()) {
            Registry registry = Registry.forEdition(edition);
            String semanticModel = registry.edition().replace(" ", "");
            assertEquals(Optional.of(registry), Registry.forSemanticModel(semanticModel));
        }
    }

    @Test
    void anEditionNoRegistryDescribesIsAnsweredWithNothing() {
        assertEquals(Optional.empty(), Registry.forSemanticModel("EN16931-1:1999"));
        assertEquals(Optional.empty(),
                Registry.forSemanticModel("EN 16931-1:2017+A1:2019/AC:2020"));
    }

    @Test
    @EnabledIf("carries2026")
    void theTwoEditionsAreTwoRegistries() {
        Registry older = Registry.forEdition("2017");
        Registry newer = Registry.forEdition("2026");
        assertNotSame(older, newer);
        assertEquals("EN 16931-1:2026", newer.edition());
        assertTrue(newer.terms().size() > older.terms().size());
    }

    @Test
    @EnabledIf("carries2026")
    void theSamePathIsTwoDifferentTermsUnderTheTwoEditions() {
        assertEquals(Optional.empty(), Registry.forEdition("2017").term("BG-33"));
        assertTrue(Registry.forEdition("2026").term("BG-33").isPresent());
        assertEquals(Optional.empty(), Registry.forEdition("2017").parentOf("BT-20"));
        assertEquals(Optional.of("BG-33"), Registry.forEdition("2026").parentOf("BT-20"));
    }

    @Test
    @EnabledIf("carries2026")
    void aRegistryDescribesItsOwnEditionAndNoOther() {
        Registry newer = Registry.forEdition("2026");
        assertTrue(newer.describes("EN16931-1:2026"));
        assertFalse(newer.describes("EN16931-1:2017+A1:2019/AC:2020"));
        assertFalse(Registry.en16931().describes("EN16931-1:2026"));
    }
}
