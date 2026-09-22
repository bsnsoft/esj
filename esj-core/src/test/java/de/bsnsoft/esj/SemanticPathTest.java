package de.bsnsoft.esj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks the path grammar, the structure of a path and the canonical path order. */
class SemanticPathTest {

    @Test
    void aPathAtTheRootHasOneTermAndNoGroups() {
        SemanticPath path = SemanticPath.of("/BT-1");

        assertEquals("/BT-1", path.toString());
        assertEquals("BT-1", path.term());
        assertEquals(List.of(), path.groupPaths());
        assertEquals(SemanticPath.root(), path.parent());
        assertTrue(path.isValuePath());
        assertFalse(path.isGroupPath());
        assertFalse(path.isExtension());
    }

    @Test
    void aNestedPathKnowsItsGroupInstances() {
        SemanticPath path = SemanticPath.of("/BG-25/0/BG-29/BT-146");

        assertEquals(List.of("BG-25", "BG-29", "BT-146"), path.termIds());
        assertEquals(List.of(SemanticPath.group("/BG-25/0"), SemanticPath.group("/BG-25/0/BG-29")),
                path.groupPaths());
        assertEquals(SemanticPath.group("/BG-25/0/BG-29"), path.parent());
        assertEquals("BT-146", path.term());
        assertEquals(4, path.segments().size());
    }

    @Test
    void aRepeatedTermCarriesItsIndexInTheLastStep() {
        SemanticPath path = SemanticPath.of("/BG-4/BT-29/0");

        assertEquals("BT-29", path.term());
        assertEquals("0", path.index().orElseThrow().digits());
        assertEquals(0, path.index().orElseThrow().value());
        assertEquals(SemanticPath.group("/BG-4"), path.parent());
    }

    @Test
    void extensionSegmentsCarryANamespace() {
        SemanticPath extensionTerm = SemanticPath.of("/BG-DEX-09/0/BT-DEX-001");
        assertTrue(extensionTerm.isExtension());
        assertTrue(extensionTerm.hasExtensionSegment());
        assertEquals("BT-DEX-001", extensionTerm.term());

        SemanticPath reusedCoreTerm = SemanticPath.of("/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146");
        assertFalse(reusedCoreTerm.isExtension());
        assertTrue(reusedCoreTerm.hasExtensionSegment());
    }

    @Test
    void theRootIsThePathWithNoSegments() {
        SemanticPath root = SemanticPath.root();

        assertEquals("", root.toString());
        assertTrue(root.isRoot());
        assertFalse(root.isValuePath());
        assertEquals(List.of(), root.segments());
        assertThrows(IllegalStateException.class, root::parent);
        assertThrows(IllegalStateException.class, root::term);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "BT-1",
        "/",
        "/BT-1/",
        "//BT-1",
        "/BT-01",
        "/BT-",
        "/BT-1/BT-2",
        "/bt-1",
        "/XX-1",
        "/BT-dex-1",
        "/BT-DEX-",
        "/BG-25/00/BT-129",
        "/BG-25/0/0/BT-129",
        "/0/BT-1",
        "/BG-4",
        "/BT-1 ",
        "/BT-1.0"
    })
    void aPathThatDoesNotMatchTheGrammarIsRejected(String text) {
        assertThrows(EsjFormatException.class, () -> SemanticPath.of(text));
    }

    @Test
    void aGroupPathEndsAtAGroupAndAValuePathAtATerm() {
        assertEquals("/BG-25/0", SemanticPath.group("/BG-25/0").toString());
        assertTrue(SemanticPath.group("/BG-4/BG-5").isGroupPath());
        assertThrows(EsjFormatException.class, () -> SemanticPath.group("/BT-1"));
        assertThrows(EsjFormatException.class, () -> SemanticPath.of("/BG-25/0"));
    }

    @Test
    void theCanonicalOrderIsTheOrderOfTheWorkedExample() {
        List<SemanticPath> paths = new ArrayList<>(List.of(
                SemanticPath.of("/BG-25/1/BT-129"),
                SemanticPath.of("/BG-4/BG-5/BT-40"),
                SemanticPath.of("/BT-2"),
                SemanticPath.of("/BG-25/0/BT-129"),
                SemanticPath.of("/BT-1"),
                SemanticPath.of("/BG-4/BT-27")));

        paths.sort(SemanticPath.canonicalOrder());

        assertEquals(List.of("/BT-1", "/BT-2", "/BG-4/BT-27", "/BG-4/BG-5/BT-40",
                        "/BG-25/0/BT-129", "/BG-25/1/BT-129"),
                paths.stream().map(SemanticPath::toString).toList());
    }

    @Test
    void theAddressLineAddedByTheAmendmentSortsAfterTheOtherAddressTerms() {
        SemanticPath city = SemanticPath.of("/BG-4/BG-5/BT-37");
        SemanticPath thirdLine = SemanticPath.of("/BG-4/BG-5/BT-162");

        assertTrue(city.compareTo(thirdLine) < 0);
        assertTrue(thirdLine.compareTo(city) > 0);
    }

    @Test
    void numbersAreComparedNumericallyAndNotAsText() {
        assertTrue(SemanticPath.of("/BG-25/2/BT-129").compareTo(SemanticPath.of("/BG-25/10/BT-129")) < 0);
        assertTrue(SemanticPath.of("/BG-4/BT-27").compareTo(SemanticPath.of("/BG-25/0/BT-129")) < 0);
    }

    @Test
    void coreSegmentsSortBeforeExtensionSegmentsAndNamespacesByCodePoint() {
        SemanticPath core = SemanticPath.of("/BG-25/0/BG-29/BT-146");
        SemanticPath dex = SemanticPath.of("/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146");
        SemanticPath dey = SemanticPath.of("/BG-25/0/BG-DEY-01/0/BG-DEX-07/BT-146");

        assertTrue(core.compareTo(dex) < 0);
        assertTrue(dex.compareTo(dey) < 0);
    }

    @Test
    void anExtensionNumberWithLeadingZerosTiesByItsSpelling() {
        SemanticPath padded = SemanticPath.of("/BG-DEX-09/0/BT-DEX-001");
        SemanticPath plain = SemanticPath.of("/BG-DEX-09/0/BT-DEX-1");

        assertTrue(padded.compareTo(plain) < 0);
        assertNotEquals(padded, plain);
    }

    @Test
    void aTermSegmentSortsBeforeAnIndexSegmentAtTheSamePosition() {
        SemanticPath withoutIndex = SemanticPath.of("/BG-25/BT-129");
        SemanticPath withIndex = SemanticPath.of("/BG-25/0/BT-129");

        assertTrue(withoutIndex.compareTo(withIndex) < 0);
    }

    @Test
    void aShorterPathSortsFirstWhenItIsAPrefix() {
        assertTrue(SemanticPath.group("/BG-25/0").compareTo(SemanticPath.of("/BG-25/0/BT-126")) < 0);
        assertTrue(SemanticPath.root().compareTo(SemanticPath.of("/BT-1")) < 0);
    }

    @Test
    void equalityIsEqualityOfTheText() {
        SemanticPath one = SemanticPath.of("/BG-25/0/BT-126");
        SemanticPath other = SemanticPath.of("/BG-25/0/BT-126");

        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
        assertEquals(0, one.compareTo(other));
        assertNotEquals(one, SemanticPath.of("/BG-25/1/BT-126"));
    }

    @Test
    void aPathCanBeBuiltFromSegments() {
        SemanticPath path = SemanticPath.ofSegments(List.of(
                PathSegment.core(TermKind.BG, "25"),
                PathSegment.index(3),
                PathSegment.core(TermKind.BT, "126")));

        assertEquals("/BG-25/3/BT-126", path.toString());
        assertEquals(SemanticPath.of("/BG-25/3/BT-126"), path);
        assertTrue(path.startsWith(SemanticPath.group("/BG-25/3")));
        assertFalse(path.startsWith(SemanticPath.group("/BG-25/4")));
    }

    @Test
    void segmentsAreValidatedOnTheirOwn() {
        assertThrows(EsjFormatException.class, () -> PathSegment.core(TermKind.BT, "01"));
        assertThrows(EsjFormatException.class, () -> PathSegment.extension(TermKind.BT, "dex", "1"));
        assertThrows(EsjFormatException.class, () -> new PathSegment.Index("00"));
        assertThrows(IllegalArgumentException.class, () -> PathSegment.index(-1));
        assertEquals("BT-DEX-001", PathSegment.extension(TermKind.BT, "DEX", "001").id());
    }

    @Test
    void anIndexTooLargeForAnIntIsARejectedLimit() {
        PathSegment.Index index = new PathSegment.Index("99999999999999999999");

        assertThrows(EsjLimitException.class, index::value);
        assertEquals("99999999999999999999", index.digits());
    }
}
