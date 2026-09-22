package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Aggregates and quantifiers over no instance, one instance and many, and the paths a finding
 * reports.
 */
class AggregateTest {

    private static SemanticDocument lines(int count) {
        SemanticDocument.Builder builder = Documents.minimal();
        for (int index = 1; index < count; index++) {
            Documents.line(builder, index, "1", "100");
        }
        String total = String.valueOf(100 * count);
        Documents.set(builder, "/BG-22/BT-106", total);
        Documents.set(builder, "/BG-22/BT-109", total);
        Documents.set(builder, "/BG-22/BT-112", total);
        Documents.set(builder, "/BG-22/BT-115", total);
        Documents.set(builder, "/BG-23/0/BT-116", total);
        return builder.build();
    }

    @Test
    void aSumOverNoInstanceIsZero() {
        assertFalse(Packs.fails(Documents.minimal().build(),
                "{\"eq\": [{\"sum\": \"/BG-25/*/BG-27/*/BT-136\"}, {\"const\": \"0\"}]}"));
    }

    @Test
    void aSumOverOneInstanceIsThatOne() {
        assertFalse(Packs.fails(lines(1),
                "{\"eq\": [{\"sum\": \"/BG-25/*/BT-131\"}, {\"const\": \"100\"}]}"));
    }

    @Test
    void aSumOverManyInstancesIsTheirTotal() {
        assertFalse(Packs.fails(lines(7),
                "{\"eq\": [{\"sum\": \"/BG-25/*/BT-131\"}, {\"const\": \"700\"}]}"));
        assertTrue(Packs.fails(lines(7),
                "{\"eq\": [{\"sum\": \"/BG-25/*/BT-131\"}, {\"const\": \"600\"}]}"));
    }

    @Test
    void aCountAnswersOverNoneOneAndMany() {
        assertFalse(Packs.fails(Documents.minimal().build(),
                "{\"eq\": [{\"count\": \"/BG-25/*/BG-27/*\"}, {\"const\": 0}]}"));
        assertFalse(Packs.fails(lines(1), "{\"eq\": [{\"count\": \"/BG-25/*\"}, {\"const\": 1}]}"));
        assertFalse(Packs.fails(lines(7), "{\"eq\": [{\"count\": \"/BG-25/*\"}, {\"const\": 7}]}"));
    }

    @Test
    void aMinimumOrMaximumOverNoInstanceIsUndecidedAndReportsNothing() {
        assertEquals(List.of(), Packs.run(Documents.minimal().build(),
                "{\"eq\": [{\"min\": \"/BG-25/*/BG-27/*/BT-136\"}, {\"const\": \"0\"}]}"));
        assertEquals(List.of(), Packs.run(Documents.minimal().build(),
                "{\"eq\": [{\"max\": \"/BG-25/*/BG-27/*/BT-136\"}, {\"const\": \"0\"}]}"));
    }

    @Test
    void aMinimumAndAMaximumOverManyInstances() {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.line(builder, 1, "1", "250");
        Documents.line(builder, 2, "1", "50");
        SemanticDocument document = builder.build();

        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"min\": \"/BG-25/*/BT-131\"}, {\"const\": \"50\"}]}"));
        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"max\": \"/BG-25/*/BT-131\"}, {\"const\": \"250\"}]}"));
    }

    @Test
    void anAggregateOfALineIsTakenInsideThatLine() {
        SemanticDocument document = Documents.minimal()
                .put("/BG-25/0/BG-27/0/BT-136", "10")
                .put("/BG-25/0/BG-27/1/BT-136", "5")
                .build();

        assertFalse(Packs.fails(document, "/BG-25/*",
                "{\"eq\": [{\"sum\": \"/BG-27/*/BT-136\"}, {\"const\": \"15\"}]}"));
    }

    @Test
    void aRuleWithAGroupContextRunsOncePerInstance() {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.line(builder, 1, "1", "100");
        Documents.line(builder, 2, "1", "100");

        List<RuleFinding> findings = Packs.run(builder.build(), "/BG-25/*",
                "{\"eq\": [{\"value\": \"/BT-131\"}, {\"const\": \"999\"}]}");

        assertEquals(3, findings.size());
        assertEquals(List.of("/BG-25/0/BT-131", "/BG-25/1/BT-131", "/BG-25/2/BT-131"),
                findings.stream().map(RuleFinding::firstPath).toList());
    }

    @Test
    void forEachHoldsWhenEveryInstanceDoesAndNamesTheFirstThatDoesNot() {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.line(builder, 1, "1", "100");
        Documents.line(builder, 2, "2", "100");
        SemanticDocument document = builder.build();

        assertFalse(Packs.fails(document,
                "{\"forEach\": {\"group\": \"/BG-25/*\","
                        + " \"assert\": {\"gt\": [{\"value\": \"/BT-131\"}, {\"const\": \"0\"}]}}}"));

        List<RuleFinding> findings = Packs.run(document,
                "{\"forEach\": {\"group\": \"/BG-25/*\","
                        + " \"assert\": {\"eq\": [{\"value\": \"/BT-131\"}, {\"const\": \"100\"}]}}}");

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).paths().contains("/BG-25/2"), findings.get(0).paths().toString());
    }

    @Test
    void allIsTheSameStatementWithoutNamingAnInstance() {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.line(builder, 1, "2", "100");
        SemanticDocument document = builder.build();

        List<RuleFinding> findings = Packs.run(document,
                "{\"all\": {\"group\": \"/BG-25/*\","
                        + " \"assert\": {\"eq\": [{\"value\": \"/BT-131\"}, {\"const\": \"100\"}]}}}");

        assertEquals(1, findings.size());
        assertFalse(findings.get(0).paths().contains("/BG-25/1"));
    }

    @Test
    void anyIsFalseOverNoInstanceWhereForEachIsTrue() {
        SemanticDocument document = Documents.minimal().build();

        assertTrue(Packs.fails(document,
                "{\"any\": {\"group\": \"/BG-25/*/BG-27/*\","
                        + " \"assert\": {\"exists\": \"/BT-136\"}}}"));
        assertFalse(Packs.fails(document,
                "{\"forEach\": {\"group\": \"/BG-25/*/BG-27/*\","
                        + " \"assert\": {\"exists\": \"/BT-136\"}}}"));
    }

    @Test
    void anyHoldsAsSoonAsOneInstanceDoes() {
        SemanticDocument document = Documents.minimal()
                .put("/BG-23/1/BT-116", "0")
                .put("/BG-23/1/BT-117", "0")
                .put("/BG-23/1/BT-118", "S")
                .put("/BG-23/1/BT-119", "19")
                .build();

        assertFalse(Packs.fails(document,
                "{\"any\": {\"group\": \"/BG-23/*\","
                        + " \"assert\": {\"eq\": [{\"value\": \"/BT-118\"}, {\"const\": \"S\"}]}}}"));
        assertTrue(Packs.fails(document,
                "{\"any\": {\"group\": \"/BG-23/*\","
                        + " \"assert\": {\"eq\": [{\"value\": \"/BT-118\"}, {\"const\": \"AE\"}]}}}"));
    }

    @Test
    void aNestedQuantifierWalksTheInnerGroupOfTheOuterInstance() {
        SemanticDocument document = Documents.minimal()
                .put("/BG-25/0/BG-27/0/BT-136", "10")
                .put("/BG-25/0/BG-27/1/BT-136", "-1")
                .build();

        assertTrue(Packs.fails(document,
                "{\"forEach\": {\"group\": \"/BG-25/*\", \"assert\":"
                        + " {\"all\": {\"group\": \"/BG-27/*\","
                        + " \"assert\": {\"ge\": [{\"value\": \"/BT-136\"}, {\"const\": \"0\"}]}}}}}"));
    }

    @Test
    void aDocumentAggregateIsTheSameAnswerForEveryRuleThatAsksForIt() {
        SemanticDocument document = lines(5);
        String sum = "{\"eq\": [{\"sum\": \"/BG-25/*/BT-131\"}, {\"const\": \"500\"}]}";

        List<RuleFinding> findings = Packs.engine(Packs.file(
                Packs.rule("BR-A", sum), Packs.rule("BR-B", sum), Packs.rule("BR-C", sum)))
                .evaluate(document);

        assertEquals(List.of(), findings);
    }
}
