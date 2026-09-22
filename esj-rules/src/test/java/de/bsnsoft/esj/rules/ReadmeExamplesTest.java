package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.en16931.En16931;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The snippets of the section "Checking the business rules" of {@code docs/java-api.md}, one
 * test per snippet, so that the page cannot describe an API this module does not have. The
 * lines between the comment naming the page and its section and the assertions are the
 * snippet as the page prints it; a change to one is a change to the other.
 *
 * <p>The document the snippets read is an example of the repository, which the build copies
 * onto the test class path.
 */
class ReadmeExamplesTest {

    private static SemanticDocument example(String name) {
        try (InputStream in = ReadmeExamplesTest.class
                .getResourceAsStream("/examples/" + name + ".esj.json")) {
            if (in == null) {
                throw new IllegalStateException("this build carries no example " + name);
            }
            return EsjReader.strict().read(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void evaluateThePackOverADocument() {
        SemanticDocument document = example("standard-invoice");

        // docs/java-api.md: Checking the business rules
        RuleEngine engine = En16931.engine(Registry.en16931());

        List<RuleFinding> findings = engine.evaluate(document);
        boolean rejected = findings.stream().anyMatch(RuleFinding::fatal);

        assertEquals(List.of(), findings, "the example is a sound invoice");
        assertFalse(rejected);
    }

    @Test
    void compileThePackFromItsThreeParts() {
        // docs/java-api.md: Checking the business rules
        RulePack pack = RulePacks.bundled(En16931.PACK_ID, En16931.VERSION);
        RuleEngine other = RuleEngine.compile(pack, Registry.en16931(),
                CodeLists.bundled(pack), En16931.javaRules());

        assertEquals("en16931", other.pack().id());
        assertEquals("1.3.16", other.pack().version());
        assertEquals(217, other.ruleIds().size());
        assertTrue(other.ruleIds().contains("BR-CO-10"));
    }
}
