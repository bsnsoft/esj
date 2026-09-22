package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.model.Registry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The document {@code README.md} shows is read by the reader and checked against the registry.
 *
 * <p>It is the first thing anyone sees of the format, and it is the one block of the page that
 * no other test touches: the transcripts are run by {@link ReadmeCliExamplesTest} and the Java
 * snippets by the {@code ReadmeExamplesTest} classes of {@code esj-typed} and {@code esj-xr}.
 * Read here through {@link EsjReader#strict()}, it cannot drift away from layer L1 or from the
 * value form, and every path it prints has to be a term of the edition the page names.
 */
class ReadmeJsonExampleTest {

    /** The page, on the test classpath of this module. */
    private static final String PAGE = "README.md";

    /** The fence that opens the example. */
    private static final String FENCE = "```json";

    @Test
    void readsTheDocumentTheReadmeShows() {
        List<String> blocks = fencedJson(Fixtures.text(PAGE));
        assertEquals(1, blocks.size(),
                PAGE + " shows one " + FENCE + " block, and this test reads it");

        SemanticDocument document =
                EsjReader.strict().read(blocks.get(0).getBytes(StandardCharsets.UTF_8));

        Registry registry = Registry.en16931();
        assertTrue(registry.describes(document.semanticModel()),
                "the page names the edition of the registry: " + document.semanticModel());
        assertFalse(document.values().isEmpty(), "the page shows values");
        for (SemanticPath path : document.values().keySet()) {
            for (String term : path.termIds()) {
                assertTrue(registry.term(term).isPresent(),
                        PAGE + " shows " + path + ", whose " + term
                                + " is no term of " + registry.edition());
            }
        }
    }

    /** Returns the content of every fenced {@code json} block of a page, in order. */
    private static List<String> fencedJson(String page) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = null;
        for (String line : page.split("\n", -1)) {
            if (current == null) {
                if (FENCE.equals(line.strip())) {
                    current = new StringBuilder();
                }
            } else if ("```".equals(line.strip())) {
                blocks.add(current.toString());
                current = null;
            } else {
                current.append(line).append('\n');
            }
        }
        return blocks;
    }
}
