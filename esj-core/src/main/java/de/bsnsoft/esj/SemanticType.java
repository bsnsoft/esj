package de.bsnsoft.esj;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The semantic data types of EN 16931-1, clause 6.5, as the registry names them in the
 * {@code datatype} member of a term (specification, section 6.2). The set is the union
 * over the editions ESJ knows: the 2017 edition defines ten of them and the 2026 edition
 * adds {@link #TIME}, and a registry uses the ones its own edition has.
 *
 * <p>A document carries no type: the registry records the semantic data type of a
 * business term once, and that record decides which grammar the content of a value at
 * that term must satisfy and which supplementary components it may carry (section 6.1).
 * This enumeration is therefore a fact about the model and not about the serialization,
 * and it is used by the validator, by the code generator and by the typed view.
 */
public enum SemanticType {

    /** Free text; line breaks are preserved. */
    TEXT("Text"),

    /** An identifier, optionally qualified by a scheme and a scheme version. */
    IDENTIFIER("Identifier"),

    /** A code taken from the list the semantic model fixes for the term. */
    CODE("Code"),

    /** A calendar date without a time of day and without a time zone. */
    DATE("Date"),

    /**
     * A time of day carrying the offset from UTC it is stated in. Introduced by the 2026
     * edition of the model; a registry of an earlier edition names no term of this type.
     */
    TIME("Time"),

    /** A monetary amount; the currency is a business term of its own. */
    AMOUNT("Amount"),

    /** A unit price; the currency is a business term of its own. */
    UNIT_PRICE_AMOUNT("UnitPriceAmount"),

    /** A quantity; the unit of measure is a business term of its own. */
    QUANTITY("Quantity"),

    /** A percentage, where {@code 34.78} means 34,78 per cent. */
    PERCENTAGE("Percentage"),

    /** A reference to another document; it never carries a scheme. */
    DOCUMENT_REFERENCE("DocumentReference"),

    /** An embedded file, with its media type and its file name. */
    BINARY_OBJECT("BinaryObject");

    private static final Map<String, SemanticType> BY_DATATYPE = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(SemanticType::registryDatatype, Function.identity()));

    private final String registryDatatype;

    SemanticType(String registryDatatype) {
        this.registryDatatype = registryDatatype;
    }

    /**
     * Returns the name the registry uses for this semantic data type.
     *
     * @return the registry datatype name, for example {@code UnitPriceAmount}
     */
    public String registryDatatype() {
        return registryDatatype;
    }

    /**
     * Tells whether the content of a value of this type is a decimal number
     * (specification, section 6.4).
     *
     * @return {@code true} for amount, unit price amount, quantity and percentage
     */
    public boolean isDecimal() {
        return this == AMOUNT || this == UNIT_PRICE_AMOUNT || this == QUANTITY || this == PERCENTAGE;
    }

    /**
     * Returns the type a registry datatype name denotes.
     *
     * @param datatype the {@code datatype} member of a registry term
     * @return the type
     * @throws EsjFormatException   if the name is not one of the registry datatypes
     * @throws NullPointerException if {@code datatype} is {@code null}
     */
    public static SemanticType fromRegistryDatatype(String datatype) {
        return findByRegistryDatatype(datatype)
                .orElseThrow(() -> new EsjFormatException("not a registry datatype: " + datatype));
    }

    /**
     * Looks up the type a registry datatype name denotes.
     *
     * @param datatype the {@code datatype} member of a registry term
     * @return the type, or an empty optional if the name is not a registry datatype
     * @throws NullPointerException if {@code datatype} is {@code null}
     */
    public static Optional<SemanticType> findByRegistryDatatype(String datatype) {
        Objects.requireNonNull(datatype, "datatype");
        return Optional.ofNullable(BY_DATATYPE.get(datatype));
    }
}
