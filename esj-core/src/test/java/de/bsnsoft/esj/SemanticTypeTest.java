package de.bsnsoft.esj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Checks the mapping between the registry datatypes and the semantic data types. */
class SemanticTypeTest {

    @ParameterizedTest
    @EnumSource(SemanticType.class)
    void everyTypeIsFoundByItsRegistryDatatype(SemanticType type) {
        assertEquals(type, SemanticType.fromRegistryDatatype(type.registryDatatype()));
    }

    @Test
    void theDatatypeNamesAreTheOnesTheRegistryUses() {
        assertEquals("UnitPriceAmount", SemanticType.UNIT_PRICE_AMOUNT.registryDatatype());
        assertEquals("DocumentReference", SemanticType.DOCUMENT_REFERENCE.registryDatatype());
        assertEquals("BinaryObject", SemanticType.BINARY_OBJECT.registryDatatype());
        assertEquals("Time", SemanticType.TIME.registryDatatype());
        assertEquals(11, SemanticType.values().length);
    }

    @Test
    void theTypeSetIsTheUnionOverTheEditionsTheBuildCarries() {
        Set<String> declared = Stream.of(SemanticType.values())
                .map(SemanticType::registryDatatype)
                .collect(Collectors.toSet());
        for (String edition : Registry.editions()) {
            for (Term term : Registry.forEdition(edition).terms()) {
                term.datatype().ifPresent(type ->
                        assertTrue(declared.contains(type.registryDatatype())));
            }
        }
        assertTrue(declared.contains("Time"),
                "the 2026 edition adds Time and the enumeration is the union over editions");
    }

    @Test
    void anUnknownDatatypeNameIsNotAType() {
        assertEquals(Optional.empty(), SemanticType.findByRegistryDatatype("text"));
        assertEquals(Optional.empty(), SemanticType.findByRegistryDatatype("identifier"));
        assertThrows(EsjFormatException.class, () -> SemanticType.fromRegistryDatatype("Number"));
    }

    @Test
    void theFourDecimalTypesKnowThatTheyAreDecimals() {
        assertTrue(SemanticType.AMOUNT.isDecimal());
        assertTrue(SemanticType.UNIT_PRICE_AMOUNT.isDecimal());
        assertTrue(SemanticType.QUANTITY.isDecimal());
        assertTrue(SemanticType.PERCENTAGE.isDecimal());
        assertFalse(SemanticType.TEXT.isDecimal());
        assertFalse(SemanticType.CODE.isDecimal());
        assertFalse(SemanticType.BINARY_OBJECT.isDecimal());
    }
}
