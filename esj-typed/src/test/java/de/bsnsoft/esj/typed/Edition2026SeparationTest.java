package de.bsnsoft.esj.typed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.model.Registry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Holds the two halves of the separation of EN 16931-1:2026 together: a build that
 * carries the registry of that edition carries its typed view and its example too, and a
 * build that carries neither is a build in which nothing of the module refers to them.
 *
 * <p>{@code model/en16931/2026.paths} lists the files, the Maven profile
 * {@code without-edition-2026} leaves them out of the sources that are compiled and out
 * of the resources that are copied, and this test is what makes the claim checkable
 * rather than asserted: it passes in both configurations and says something different in
 * each.
 */
class Edition2026SeparationTest {

    private static final String VIEW = "de.bsnsoft.esj.typed.v2026.En16931";

    private static final List<String> FILES = List.of(
            "/examples/edition-2026.esj.json",
            "/examples/edition-2026.canonical.esj.json",
            "/schema/esj-en16931-2026.schema.json");

    @Test
    void theViewAndTheFilesOfTheEditionAreThereExactlyWhereTheRegistryIs() {
        boolean carried = Registry.editions().contains("2026");
        for (String file : FILES) {
            assertEquals(carried, Edition2026SeparationTest.class.getResource(file) != null,
                    file + " belongs to the 2026 edition and follows its registry");
        }
        if (carried) {
            assertTrue(loads(VIEW), "the view of an edition the build carries is compiled");
        } else {
            assertFalse(loads(VIEW), "and is not compiled where the build carries no registry");
        }
    }

    /**
     * The machinery is not separable and is not meant to be: the loader, the view of the
     * default edition and the types every view shares stand whatever editions a build
     * carries.
     */
    @Test
    void whatIsSeparableIsTheEditionAndNotTheMachinery() {
        assertTrue(loads("de.bsnsoft.esj.typed.En16931"));
        assertTrue(loads("de.bsnsoft.esj.typed.runtime.Views"));
        assertTrue(Registry.editions().contains(Registry.DEFAULT_EDITION));
        assertThrows(Exception.class, () -> Registry.forEdition("1999"));
    }

    private static boolean loads(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
