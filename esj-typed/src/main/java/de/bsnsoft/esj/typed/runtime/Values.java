package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.typed.BinaryObject;
import de.bsnsoft.esj.typed.Identifier;
import de.bsnsoft.esj.typed.ValueTypeException;
import de.bsnsoft.esj.validate.FindingCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * How the value of a business term becomes a Java value: one reader per semantic data
 * type of the registry (specification, section 6.2).
 *
 * <p>A document carries no type. The registry records the semantic data type of a term
 * once, the generator reads that record, and the accessor it emits names the reader for
 * it here. So the mapping from a semantic data type to a Java type is written down in one
 * place and every generated accessor uses it: {@code BigDecimal} for the four decimal
 * types, {@code LocalDate} for a date, {@code OffsetTime} for a time of day,
 * {@code String} for the three that are plain text, {@link Identifier} and
 * {@link BinaryObject} for the two that carry supplementary components.
 *
 * <p>A reader parses the content when it is called and refuses what does not satisfy the
 * grammar of its type, which is the layer L2 check of the specification, section 6.2 met
 * on the way to a Java value. {@link Views} turns that refusal into a
 * {@link ValueTypeException} naming the path.
 */
public final class Values {

    /** Free text, as the document carries it. */
    public static final Reader<String> TEXT =
            new Reader<>(SemanticType.TEXT, SemanticValue::asString);

    /** An identifier with the supplementary components the term carries. */
    public static final Reader<Identifier> IDENTIFIER =
            new Reader<>(SemanticType.IDENTIFIER, Values::identifier);

    /** A code of the list the semantic model fixes for the term. */
    public static final Reader<String> CODE =
            new Reader<>(SemanticType.CODE, SemanticValue::asString);

    /** A calendar date. */
    public static final Reader<LocalDate> DATE =
            new Reader<>(SemanticType.DATE, SemanticValue::asDate);

    /** A time of day with the offset from UTC it is stated in. */
    public static final Reader<OffsetTime> TIME =
            new Reader<>(SemanticType.TIME, SemanticValue::asTime);

    /** A monetary amount. */
    public static final Reader<BigDecimal> AMOUNT =
            new Reader<>(SemanticType.AMOUNT, SemanticValue::asDecimal);

    /** A unit price. */
    public static final Reader<BigDecimal> UNIT_PRICE_AMOUNT =
            new Reader<>(SemanticType.UNIT_PRICE_AMOUNT, SemanticValue::asDecimal);

    /** A quantity. */
    public static final Reader<BigDecimal> QUANTITY =
            new Reader<>(SemanticType.QUANTITY, SemanticValue::asDecimal);

    /** A percentage. */
    public static final Reader<BigDecimal> PERCENTAGE =
            new Reader<>(SemanticType.PERCENTAGE, SemanticValue::asDecimal);

    /** A reference to another document. */
    public static final Reader<String> DOCUMENT_REFERENCE =
            new Reader<>(SemanticType.DOCUMENT_REFERENCE, SemanticValue::asString);

    /** An embedded file, decoded. */
    public static final Reader<BinaryObject> BINARY_OBJECT =
            new Reader<>(SemanticType.BINARY_OBJECT, Values::binary);

    private Values() {
        throw new AssertionError("no instances");
    }

    private static Identifier identifier(SemanticValue value) {
        return new Identifier(value.asString(),
                Optional.ofNullable(value.scheme()),
                Optional.ofNullable(value.schemeVersion()));
    }

    private static BinaryObject binary(SemanticValue value) {
        if (value.mimeCode() == null || value.filename() == null) {
            throw new EsjFormatException(
                    "a binary object carries a mime code and a file name, and this value carries "
                            + (value.mimeCode() == null ? "no mime code" : "no file name"),
                    FindingCode.ESJ_L2_COMPONENT_MISSING, null);
        }
        return new BinaryObject(value.asBytes(), value.mimeCode(), value.filename());
    }

    /**
     * One reader: the semantic data type it belongs to, and how a value of that type is
     * read.
     *
     * @param type the semantic data type of the registry
     * @param read how the content and the components become a Java value
     * @param <T>  the Java type of the value
     */
    public record Reader<T>(SemanticType type, Function<SemanticValue, T> read) {

        /**
         * Checks that both parts are present.
         *
         * @param type the semantic data type of the registry
         * @param read how the content and the components become a Java value
         * @throws NullPointerException if a part is {@code null}
         */
        public Reader {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(read, "read");
        }
    }
}
