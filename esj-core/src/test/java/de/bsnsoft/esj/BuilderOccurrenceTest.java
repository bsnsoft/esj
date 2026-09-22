package de.bsnsoft.esj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Checks the part of the builder a writing API needs: overwriting a value, removing a
 * subtree, counting occurrences, and removing one occurrence so that the indices that
 * follow it move down. The renumbering lives here and nowhere else, which is why it is
 * tested here and not through the API that calls it.
 */
class BuilderOccurrenceTest {

    private static SemanticDocument.Builder lines(int count) {
        SemanticDocument.Builder builder = SemanticDocument.builder();
        for (int index = 0; index < count; index++) {
            builder.put("/BG-25/" + index + "/BT-126", SemanticValue.of(Integer.toString(index)));
        }
        return builder;
    }

    private static List<String> paths(SemanticDocument.Builder builder) {
        return builder.values().keySet().stream().map(SemanticPath::toString).toList();
    }

    @Test
    void setWritesOverAValueThatPutRefuses() {
        SemanticDocument.Builder builder = SemanticDocument.builder().put("/BT-1", "RE-1");

        assertThrows(EsjFormatException.class, () -> builder.put("/BT-1", "RE-2"));
        builder.set(SemanticPath.of("/BT-1"), SemanticValue.of("RE-2"));

        assertEquals(Optional.of(SemanticValue.of("RE-2")), builder.value(SemanticPath.of("/BT-1")));
    }

    @Test
    void setRefusesAPathThatDoesNotAddressABusinessTerm() {
        assertThrows(EsjFormatException.class, () -> SemanticDocument.builder()
                .set(SemanticPath.group("/BG-25/0"), SemanticValue.of("x")));
    }

    @Test
    void theValuesOfABuilderAreReadableAndCannotBeChangedThroughThatView() {
        SemanticDocument.Builder builder = lines(2);

        assertEquals(List.of("/BG-25/0/BT-126", "/BG-25/1/BT-126"), paths(builder));
        assertThrows(UnsupportedOperationException.class,
                () -> builder.values().remove(SemanticPath.of("/BG-25/0/BT-126")));
        assertTrue(builder.value(SemanticPath.of("/BG-25/2/BT-126")).isEmpty());
    }

    @Test
    void removeUnderTakesAwayEverythingBelowAPath() {
        SemanticDocument.Builder builder = lines(3).put("/BT-1", "RE-1");

        builder.removeUnder(SemanticPath.group("/BG-25/1"));

        assertEquals(List.of("/BT-1", "/BG-25/0/BT-126", "/BG-25/2/BT-126"), paths(builder));

        builder.removeUnder(SemanticPath.group("/BG-25"));
        assertEquals(List.of("/BT-1"), paths(builder));
    }

    @Test
    void occurrencesCountsFromZeroAndStopsAtTheFirstGap() {
        SemanticDocument.Builder builder = lines(3);

        assertEquals(3, builder.occurrences(SemanticPath.group("/BG-25")));

        builder.removeUnder(SemanticPath.group("/BG-25/1"));
        assertEquals(1, builder.occurrences(SemanticPath.group("/BG-25")));
        assertEquals(0, builder.occurrences(SemanticPath.group("/BG-23")));
    }

    @Test
    void removingAnOccurrenceMovesEveryLaterOneDownOneIndex() {
        SemanticDocument.Builder builder = lines(4);

        builder.removeOccurrence(SemanticPath.group("/BG-25/1"));

        assertEquals(List.of("/BG-25/0/BT-126", "/BG-25/1/BT-126", "/BG-25/2/BT-126"),
                paths(builder));
        assertEquals(Optional.of(SemanticValue.of("2")),
                builder.value(SemanticPath.of("/BG-25/1/BT-126")));
        assertEquals(Optional.of(SemanticValue.of("3")),
                builder.value(SemanticPath.of("/BG-25/2/BT-126")));
    }

    @Test
    void removingAnOccurrenceMovesWhatLiesInsideTheLaterOnesWithThem() {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .put("/BG-25/0/BG-27/0/BT-136", "1")
                .put("/BG-25/1/BG-27/0/BT-136", "2")
                .put("/BG-25/1/BG-27/1/BT-136", "3")
                .put("/BG-25/2/BG-27/0/BT-136", "4");

        builder.removeOccurrence(SemanticPath.group("/BG-25/0"));

        assertEquals(List.of("/BG-25/0/BG-27/0/BT-136", "/BG-25/0/BG-27/1/BT-136",
                "/BG-25/1/BG-27/0/BT-136"), paths(builder));
        assertEquals(Optional.of(SemanticValue.of("3")),
                builder.value(SemanticPath.of("/BG-25/0/BG-27/1/BT-136")));
    }

    @Test
    void removingAnInnerOccurrenceLeavesTheOccurrencesOfOtherInstancesAlone() {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .put("/BG-25/0/BG-27/0/BT-136", "1")
                .put("/BG-25/0/BG-27/1/BT-136", "2")
                .put("/BG-25/1/BG-27/0/BT-136", "3");

        builder.removeOccurrence(SemanticPath.group("/BG-25/0/BG-27/0"));

        assertEquals(List.of("/BG-25/0/BG-27/0/BT-136", "/BG-25/1/BG-27/0/BT-136"), paths(builder));
        assertEquals(Optional.of(SemanticValue.of("2")),
                builder.value(SemanticPath.of("/BG-25/0/BG-27/0/BT-136")));
    }

    @Test
    void anOccurrenceOfARepeatableTermIsRemovedTheSameWay() {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .put("/BG-4/BT-29/0", "first")
                .put("/BG-4/BT-29/1", "second")
                .put("/BG-4/BT-29/2", "third");

        assertEquals(3, builder.occurrences(SemanticPath.of("/BG-4/BT-29")));
        builder.removeOccurrence(SemanticPath.of("/BG-4/BT-29/0"));

        assertEquals(List.of("/BG-4/BT-29/0", "/BG-4/BT-29/1"), paths(builder));
        assertEquals(Optional.of(SemanticValue.of("second")),
                builder.value(SemanticPath.of("/BG-4/BT-29/0")));
    }

    @Test
    void removingAnOccurrenceThatIsNotThereLeavesTheRestWhereItIs() {
        SemanticDocument.Builder builder = lines(2);

        builder.removeOccurrence(SemanticPath.group("/BG-25/5"));

        assertEquals(List.of("/BG-25/0/BT-126", "/BG-25/1/BT-126"), paths(builder));
    }

    @Test
    void anOccurrenceIsRemovedAtAPathThatEndsInAnIndex() {
        assertThrows(EsjFormatException.class,
                () -> SemanticDocument.builder().removeOccurrence(SemanticPath.of("/BT-1")));
        assertThrows(EsjFormatException.class,
                () -> SemanticDocument.builder().removeOccurrence(SemanticPath.group("/BG-25")));
    }
}
