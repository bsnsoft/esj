package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Two promises this module makes about text: how much of a document it quotes back, and whose
 * vocabulary a broken pack is refused in.
 *
 * <p>Both are about a report that has to stay readable. A rule message quotes the content that
 * failed, and a business term may carry a megabyte, so a message that quoted the whole of one
 * would turn a large invoice into a report the size of the invoice; {@code esj-core} cuts such
 * a fragment at eighty characters and this module cuts it at the same eighty. A pack that
 * cannot be parsed is a condition of the input and not a defect of the tool, so what the caller
 * is told names the pack rather than the internals of the parser this module happens to use.
 */
class QuotingAndRefusalTest {

    @Test
    void aMessageQuotesAtMostTheExcerptTheSpecificationAllows() {
        String long_ = "x".repeat(5_000);
        SemanticDocument document = Documents.set(Documents.minimal(), "/BT-1", long_).build();

        String pack = Packs.file("{\"id\": \"BR-TEST\", \"severity\": \"fatal\","
                + " \"context\": \"/\", \"terms\": [\"BT-1\"],"
                + " \"assert\": {\"eq\": [{\"value\": \"/BT-1\"}, {\"const\": \"nothing\"}]},"
                + " \"message\": \"The invoice number (BT-1) is {/BT-1}.\","
                + " \"source\": \"EN 16931-1, 6.4\"}");
        List<RuleFinding> findings = Packs.engine(pack).evaluate(document);

        assertEquals(1, findings.size());
        String message = findings.get(0).message();
        assertTrue(message.contains("x".repeat(Texts.MESSAGE_EXCERPT) + "..."),
                "the fragment is cut at the excerpt of the specification and marked");
        assertFalse(message.contains("x".repeat(Texts.MESSAGE_EXCERPT + 1)),
                "and nothing beyond it is reproduced: " + message.length() + " characters");
    }

    @Test
    void aBrokenPackIsRefusedWithoutTheParsersVocabulary() {
        for (String broken : List.of("{\"id\": \"p\", \"rules\": [",
                "this is not JSON at all",
                "{\"id\": \"p\", \"rules\": {}} trailing")) {
            RulePackException thrown = assertThrows(RulePackException.class,
                    () -> RulePacks.read(new ByteArrayInputStream(
                            broken.getBytes(StandardCharsets.UTF_8)), "a pack of a caller"));

            String message = thrown.getMessage();
            assertTrue(message.contains("a pack of a caller"), message);
            for (String internal : List.of("StreamRead", "[Source", "com.fasterxml", "getMax")) {
                assertFalse(message.contains(internal),
                        "the refusal names " + internal + ", which belongs to this module: "
                                + message);
            }
        }
    }
}
