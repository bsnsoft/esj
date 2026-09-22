package de.bsnsoft.esj.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.Fixtures;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the events a document produces and the document they produce back. */
class ReplayTest {

    @Test
    void nestedRepeatingGroupsProduceTheRightEventSequence() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", SemanticValue.of("RE-1"))
                .put("/BG-25/0/BT-126", SemanticValue.of("1"))
                .put("/BG-25/0/BG-31/BT-153", SemanticValue.of("Service"))
                .put("/BG-25/0/BG-31/BG-32/0/BT-160", SemanticValue.of("Colour"))
                .put("/BG-25/0/BG-31/BG-32/1/BT-160", SemanticValue.of("Size"))
                .put("/BG-25/1/BT-126", SemanticValue.of("2"))
                .build();

        assertEquals(List.of(
                        "value /BT-1",
                        "begin /BG-25/0",
                        "value /BG-25/0/BT-126",
                        "begin /BG-25/0/BG-31",
                        "value /BG-25/0/BG-31/BT-153",
                        "begin /BG-25/0/BG-31/BG-32/0",
                        "value /BG-25/0/BG-31/BG-32/0/BT-160",
                        "end /BG-25/0/BG-31/BG-32/0",
                        "begin /BG-25/0/BG-31/BG-32/1",
                        "value /BG-25/0/BG-31/BG-32/1/BT-160",
                        "end /BG-25/0/BG-31/BG-32/1",
                        "end /BG-25/0/BG-31",
                        "end /BG-25/0",
                        "begin /BG-25/1",
                        "value /BG-25/1/BT-126",
                        "end /BG-25/1"),
                record(document));
    }

    @Test
    void aDocumentWithoutGroupsProducesValuesOnly() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", SemanticValue.of("RE-1"))
                .put("/BT-2", SemanticValue.ofDate(java.time.LocalDate.of(2026, 1, 15)))
                .build();

        assertEquals(List.of("value /BT-1", "value /BT-2"), record(document));
    }

    @Test
    void anEmptyDocumentProducesNoEvents() {
        assertEquals(List.of(), record(SemanticDocument.builder().build()));
    }

    @Test
    void theCollectorReproducesTheDocument() {
        SemanticDocument document = Fixtures.addLine(Fixtures.minimalInvoice(), 1)
                .put("/BG-25/0/BG-31/BG-32/0/BT-160", SemanticValue.of("Colour"))
                .put("/BG-25/0/BG-31/BG-32/0/BT-161", SemanticValue.of("Blue"))
                .put("/BG-24/0/BT-122", SemanticValue.of("DOC-1"))
                .put("/BG-24/0/BT-125",
                        SemanticValue.binary(new byte[] {1, 2, 3}, "application/pdf", "a.pdf"))
                .build();

        DocumentCollector collector = new DocumentCollector();
        Replay.replay(document, collector);

        assertEquals(document, collector.document());
    }

    @Test
    void theCollectorKeepsTheEnvelopeItWasSeededWith() {
        SemanticDocument document = Fixtures.minimalInvoice()
                .source("UBL", "c".repeat(64))
                .build();

        DocumentCollector collector = new DocumentCollector(SemanticDocument.builder()
                .source(document.source().orElseThrow()));
        Replay.replay(document, collector);

        assertEquals(document, collector.document());
    }

    @Test
    void theCollectorRejectsEventsThatDoNotNest() {
        DocumentCollector collector = new DocumentCollector();
        collector.beginGroup(SemanticPath.group("/BG-25/0"));

        assertThrows(IllegalStateException.class,
                () -> collector.value(SemanticPath.of("/BT-1"), SemanticValue.of("RE-1")));
        assertThrows(IllegalStateException.class,
                () -> collector.endGroup(SemanticPath.group("/BG-25/1")));
        assertThrows(IllegalStateException.class,
                () -> collector.beginGroup(SemanticPath.group("/BG-4/BG-5")));
        assertThrows(IllegalStateException.class, collector::document);
    }

    @Test
    void theCollectorTakesTheEventsOfAGroupInstanceInOrder() {
        DocumentCollector collector = new DocumentCollector();

        collector.beginGroup(SemanticPath.group("/BG-25/0"));
        collector.value(SemanticPath.of("/BG-25/0/BT-131"),
                SemanticValue.ofDecimal(new BigDecimal("100")));
        collector.endGroup(SemanticPath.group("/BG-25/0"));

        SemanticDocument document = collector.document();
        assertEquals(1, document.values().size());
        assertTrue(document.values().containsKey(SemanticPath.of("/BG-25/0/BT-131")));
    }

    private static List<String> record(SemanticDocument document) {
        List<String> events = new ArrayList<>();
        Replay.replay(document, new SemanticHandler() {

            @Override
            public void beginGroup(SemanticPath groupPath) {
                events.add("begin " + groupPath);
            }

            @Override
            public void value(SemanticPath path, SemanticValue value) {
                events.add("value " + path);
            }

            @Override
            public void endGroup(SemanticPath groupPath) {
                events.add("end " + groupPath);
            }
        });
        return events;
    }
}
