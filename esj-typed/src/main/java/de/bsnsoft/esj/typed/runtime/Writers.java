package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.typed.BinaryObject;
import de.bsnsoft.esj.typed.Identifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.util.Objects;
import java.util.function.Function;

/**
 * How a Java value becomes the value of a business term: one writer per semantic data
 * type of the registry, the mirror image of {@link Values} (specification, section 6.2).
 *
 * <p>A writer produces the canonical spelling of what it is handed — the decimal form of
 * the specification, section 6.4, the date and time forms of section 6.5, the padded
 * base64 of section 6.7 — so a document an editor builds is already in canonical form
 * and no later stage has to repair it.
 */
public final class Writers {

    /** Free text, written as it stands. */
    public static final Writer<String> TEXT = new Writer<>(SemanticType.TEXT, SemanticValue::of);

    /** An identifier with the supplementary components it carries. */
    public static final Writer<Identifier> IDENTIFIER =
            new Writer<>(SemanticType.IDENTIFIER, Writers::identifier);

    /** A code of the list the semantic model fixes for the term. */
    public static final Writer<String> CODE = new Writer<>(SemanticType.CODE, SemanticValue::of);

    /** A calendar date. */
    public static final Writer<LocalDate> DATE =
            new Writer<>(SemanticType.DATE, SemanticValue::ofDate);

    /** A time of day with the offset from UTC it is stated in. */
    public static final Writer<OffsetTime> TIME =
            new Writer<>(SemanticType.TIME, SemanticValue::ofTime);

    /** A monetary amount. */
    public static final Writer<BigDecimal> AMOUNT =
            new Writer<>(SemanticType.AMOUNT, SemanticValue::ofDecimal);

    /** A unit price. */
    public static final Writer<BigDecimal> UNIT_PRICE_AMOUNT =
            new Writer<>(SemanticType.UNIT_PRICE_AMOUNT, SemanticValue::ofDecimal);

    /** A quantity. */
    public static final Writer<BigDecimal> QUANTITY =
            new Writer<>(SemanticType.QUANTITY, SemanticValue::ofDecimal);

    /** A percentage. */
    public static final Writer<BigDecimal> PERCENTAGE =
            new Writer<>(SemanticType.PERCENTAGE, SemanticValue::ofDecimal);

    /** A reference to another document. */
    public static final Writer<String> DOCUMENT_REFERENCE =
            new Writer<>(SemanticType.DOCUMENT_REFERENCE, SemanticValue::of);

    /** An embedded file, encoded. */
    public static final Writer<BinaryObject> BINARY_OBJECT =
            new Writer<>(SemanticType.BINARY_OBJECT, Writers::binary);

    private Writers() {
        throw new AssertionError("no instances");
    }

    private static SemanticValue identifier(Identifier identifier) {
        return SemanticValue.identifier(identifier.value(),
                identifier.scheme().orElse(null),
                identifier.schemeVersion().orElse(null));
    }

    private static SemanticValue binary(BinaryObject file) {
        return SemanticValue.binary(file.bytes(), file.mimeCode(), file.filename());
    }

    /**
     * One writer: the semantic data type it belongs to, and how a Java value of that type
     * becomes the value of a business term.
     *
     * @param type  the semantic data type of the registry
     * @param write how the Java value becomes content and components
     * @param <T>   the Java type of the value
     */
    public record Writer<T>(SemanticType type, Function<T, SemanticValue> write) {

        /**
         * Checks that both parts are present.
         *
         * @param type  the semantic data type of the registry
         * @param write how the Java value becomes content and components
         * @throws NullPointerException if a part is {@code null}
         */
        public Writer {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(write, "write");
        }
    }
}
