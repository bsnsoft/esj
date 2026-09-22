package de.bsnsoft.esj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks the immutable document and what its builder does and does not check. */
class SemanticDocumentTest {

    @Test
    void theValuesAreHeldInCanonicalPathOrder() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/1/BT-129", SemanticValue.ofDecimal(BigDecimal.ONE))
                .put("/BT-2", SemanticValue.ofDate(java.time.LocalDate.of(2026, 1, 15)))
                .put("/BG-25/0/BT-129", SemanticValue.ofDecimal(BigDecimal.ONE))
                .put("/BT-1", SemanticValue.of("RE-1"))
                .build();

        assertEquals(List.of("/BT-1", "/BT-2", "/BG-25/0/BT-129", "/BG-25/1/BT-129"),
                document.values().keySet().stream().map(SemanticPath::toString).toList());
    }

    @Test
    void anEmptyDocumentCarriesTheFixedEnvelopeOnly() {
        SemanticDocument document = SemanticDocument.builder().build();

        assertEquals(Esj.SEMANTIC_MODEL, document.semanticModel());
        assertTrue(document.values().isEmpty());
        assertTrue(document.extensions().isEmpty());
        assertEquals(Optional.empty(), document.source());
    }

    @Test
    void theValuesAndExtensionsHandedOutCannotBeChanged() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", SemanticValue.of("RE-1"))
                .extension("de.example.vendor", ExtensionValue.of("x"))
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> document.values().remove(SemanticPath.of("/BT-1")));
        assertThrows(UnsupportedOperationException.class,
                () -> document.extensions().remove("de.example.vendor"));
    }

    @Test
    void aValueLiesAtABusinessTermAndOnlyOnce() {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .put("/BT-1", SemanticValue.of("RE-1"));

        assertThrows(EsjFormatException.class,
                () -> builder.put("/BT-1", SemanticValue.of("RE-2")));
        assertThrows(EsjFormatException.class,
                () -> builder.put(SemanticPath.group("/BG-4"), SemanticValue.of("x")));
        assertThrows(EsjFormatException.class,
                () -> builder.put("/BT-01", SemanticValue.of("x")));
    }

    @Test
    void aValueCanBeRemovedAndPutAgain() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", SemanticValue.of("RE-1"))
                .remove("/BT-1")
                .put("/BT-1", SemanticValue.of("RE-2"))
                .remove("/BT-2")
                .build();

        assertEquals(SemanticValue.of("RE-2"),
                document.value(SemanticPath.of("/BT-1")).orElseThrow());
        assertEquals(Optional.empty(), document.value(SemanticPath.of("/BT-3")));
    }

    @Test
    void theBuilderDoesNotCheckTheRegistry() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-9999", SemanticValue.of("no such term"))
                .put("/BT-1/0", SemanticValue.of("wrong type and a forbidden index"))
                .build();

        assertEquals(2, document.values().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"de.example.vendor", "example-vendor_2", "a", "A0"})
    void anOwnerTokenOfTheGrammarIsAccepted(String owner) {
        SemanticDocument document = SemanticDocument.builder()
                .extension(owner, ExtensionValue.of("x"))
                .build();

        assertEquals(Map.of(owner, ExtensionValue.of("x")), document.extensions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "urn:example:v/2", "de.exämple.vendor", ".leading", "trailing-",
        "BT-1", "BG-25", "with space"})
    void anOwnerTokenOutsideTheGrammarIsRejected(String owner) {
        assertThrows(EsjFormatException.class,
                () -> SemanticDocument.builder().extension(owner, ExtensionValue.of("x")));
    }

    @Test
    void anOwnerTokenLongerThanTheBoundIsRejected() {
        String tooLong = "a".repeat(129);

        assertThrows(EsjFormatException.class,
                () -> SemanticDocument.builder().extension(tooLong, ExtensionValue.of("x")));
        assertEquals(1, SemanticDocument.builder()
                .extension("a".repeat(128), ExtensionValue.of("x"))
                .build().extensions().size());
    }

    @Test
    void theOwnerTokensAreHeldInOrder() {
        SemanticDocument document = SemanticDocument.builder()
                .extension("z.example", ExtensionValue.of("z"))
                .extension("a.example", ExtensionValue.of("a"))
                .build();

        assertEquals(List.of("a.example", "z.example"), List.copyOf(document.extensions().keySet()));
    }

    @Test
    void provenanceMetadataCarriesAtLeastOneMember() {
        String digest = "a".repeat(64);
        SemanticDocument document = SemanticDocument.builder().source("UBL", digest).build();

        assertEquals("UBL", document.source().orElseThrow().syntax().orElseThrow());
        assertEquals(digest, document.source().orElseThrow().sha256().orElseThrow());
        assertEquals(Optional.empty(), SemanticDocument.Source.ofSyntax("CII").sha256());
        assertThrows(EsjFormatException.class,
                () -> new SemanticDocument.Source(Optional.empty(), Optional.empty()));
    }

    @Test
    void aDigestIs64LowercaseHexadecimalDigits() {
        assertThrows(EsjFormatException.class, () -> SemanticDocument.Source.ofDigest("abc"));
        assertThrows(EsjFormatException.class,
                () -> SemanticDocument.Source.ofDigest("A".repeat(64)));
        assertThrows(EsjFormatException.class,
                () -> SemanticDocument.Source.ofDigest("z".repeat(64)));
    }

    /**
     * The builder is edition-ready: it takes any string of the edition grammar of the
     * specification, section 4.4 and defaults to the edition the bundled registry
     * describes. Whether a registry for that edition exists is the reader's and the
     * validator's question, not the builder's.
     */
    @Test
    void theEditionDefaultsToTheBundledOneAndIsHeldToItsGrammar() {
        assertEquals(Esj.SEMANTIC_MODEL, SemanticDocument.builder().build().semanticModel());
        assertEquals(Esj.SEMANTIC_MODEL, SemanticDocument.builder()
                .semanticModel(Esj.SEMANTIC_MODEL).build().semanticModel());
        assertEquals("EN16931-1:2026", SemanticDocument.builder()
                .semanticModel("EN16931-1:2026").build().semanticModel());
        assertThrows(EsjFormatException.class,
                () -> SemanticDocument.builder().semanticModel("EN 16931-1:2017"));
        assertThrows(EsjFormatException.class,
                () -> SemanticDocument.builder().semanticModel("whatever"));
    }

    /**
     * The shorter form of {@code put}, for the shape most values have: content and no
     * supplementary component (specification, section 6.1).
     */
    @Test
    void aValueOfNothingButContentIsPutAsAString() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", "RE-1")
                .put(SemanticPath.of("/BT-3"), "380")
                .build();

        assertEquals(SemanticValue.of("RE-1"),
                document.value(SemanticPath.of("/BT-1")).orElseThrow());
        assertEquals(SemanticValue.of("380"),
                document.value(SemanticPath.of("/BT-3")).orElseThrow());
        assertThrows(EsjFormatException.class,
                () -> SemanticDocument.builder().put("/BT-1", ""));
    }

    @Test
    void twoDocumentsWithTheSameContentAreEqual() {
        SemanticDocument one = Fixtures.minimalInvoice().build();
        SemanticDocument other = Fixtures.minimalInvoice().build();
        SemanticDocument changed = Fixtures.minimalInvoice()
                .remove("/BT-1")
                .put("/BT-1", SemanticValue.of("RE-2026-0002"))
                .build();

        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
        assertNotEquals(one, changed);
    }

    @Test
    void aDocumentCanBeChangedThroughItsBuilder() {
        SemanticDocument document = Fixtures.minimalInvoice().build();

        SemanticDocument changed = document.toBuilder()
                .put("/BT-6", SemanticValue.of("EUR"))
                .source("CII", "b".repeat(64))
                .build();

        assertEquals(document.values().size() + 1, changed.values().size());
        assertTrue(changed.source().isPresent());
        assertFalse(document.source().isPresent());
    }

    @Test
    void theDescriptionOfADocumentDoesNotCarryItsContent() {
        SemanticDocument document = Fixtures.minimalInvoice().build();

        assertFalse(document.toString().contains("Example GmbH"));
        assertTrue(document.toString().contains("values=23"));
    }
}
