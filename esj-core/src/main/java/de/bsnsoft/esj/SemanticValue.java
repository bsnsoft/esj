package de.bsnsoft.esj;

import de.bsnsoft.esj.validate.FindingCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.util.Base64;
import java.util.Objects;

/**
 * One value of a business term: its content, and the supplementary components it carries
 * (specification, section 6).
 *
 * <p>A value has one shape in Java because it has one shape in a document. The content is
 * always a string — the text, the decimal, the date or the base64 of an attachment — and
 * beside it stand the components the semantic data type of the term allows: {@code scheme}
 * and {@code schemeVersion} for an identifier (section 6.6), {@code mimeCode} and
 * {@code filename} for a binary object (section 6.7). A value that carries no component is
 * written as a JSON string and one that carries at least one is written as a JSON object;
 * the content decides the shape, so a value has exactly one spelling (section 6.1).
 *
 * <p><strong>The content is kept as it stands.</strong> Which grammar it has to satisfy is
 * decided by the registry datatype of the term it sits at, and that is a layer L2 question
 * (section 6.2). This record therefore neither checks nor repairs a decimal, a date or a
 * base64 string: a document that spells a decimal {@code 100.00} keeps that spelling
 * through the reader, the writer and the canonicalizer, and a validator with a registry
 * reports it as {@code ESJ-L2-DECIMAL} (section 6.4). What the record does check is what
 * is decided without a registry: the strings are non-empty, their line endings are
 * normalized (section 6.8), and a scheme version never stands without a scheme.
 *
 * <p>The typed accessors read the content on demand: {@link #asDecimal()},
 * {@link #asDate()}, {@link #asTime()} and {@link #asBytes()} apply the grammar their
 * name implies and throw {@link EsjFormatException} with the finding code of section 9.6
 * when the content does not satisfy it. A writer that starts from a Java value goes the
 * other way with {@link #ofDecimal(BigDecimal)}, {@link #ofDate(LocalDate)},
 * {@link #ofTime(OffsetTime)} and {@link #binary(byte[], String, String)}, which produce
 * the canonical spelling of that value.
 *
 * <p>A component that is absent is {@code null}. Instances are immutable, and two values a
 * document would write down alike are equal.
 *
 * @param content       the content of the business term, never empty
 * @param scheme        the identification scheme of an identifier, or {@code null}
 * @param schemeVersion the version of that scheme, or {@code null}
 * @param mimeCode      the media type of a binary object, or {@code null}
 * @param filename      the file name of a binary object, or {@code null}
 */
public record SemanticValue(String content,
                            String scheme,
                            String schemeVersion,
                            String mimeCode,
                            String filename) {

    /** The longest fragment of content the description of a value reproduces. */
    private static final int DESCRIPTION_EXCERPT = 40;

    /**
     * Normalizes line endings in every string, rejects an empty one and rejects a scheme
     * version that stands without a scheme (specification, section 6.6, rule 4).
     *
     * @param content       the content of the business term, never empty
     * @param scheme        the identification scheme of an identifier, or {@code null}
     * @param schemeVersion the version of that scheme, or {@code null}
     * @param mimeCode      the media type of a binary object, or {@code null}
     * @param filename      the file name of a binary object, or {@code null}
     * @throws EsjFormatException   if a string is empty, or a scheme version is present
     *                              without a scheme
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public SemanticValue {
        content = ValueSupport.required(content, "value");
        scheme = ValueSupport.optional(scheme, "scheme");
        schemeVersion = ValueSupport.optional(schemeVersion, "schemeVersion");
        mimeCode = ValueSupport.optional(mimeCode, "mimeCode");
        filename = ValueSupport.optional(filename, "filename");
        if (schemeVersion != null && scheme == null) {
            throw new EsjFormatException("schemeVersion is present only beside scheme");
        }
    }

    /**
     * Creates a value that carries content and no supplementary component. It is written
     * as a JSON string.
     *
     * @param content the content of the business term
     * @return the value
     * @throws EsjFormatException   if the content is empty
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public static SemanticValue of(String content) {
        return new SemanticValue(content, null, null, null, null);
    }

    /**
     * Creates an identifier qualified by a scheme.
     *
     * @param content the identifier itself
     * @param scheme  the identification scheme
     * @return the value
     * @throws EsjFormatException   if a string is empty
     * @throws NullPointerException if an argument is {@code null}
     */
    public static SemanticValue identifier(String content, String scheme) {
        return identifier(content, Objects.requireNonNull(scheme, "scheme"), null);
    }

    /**
     * Creates an identifier qualified by a scheme and, where the term allows one, by the
     * version of that scheme.
     *
     * @param content       the identifier itself
     * @param scheme        the identification scheme
     * @param schemeVersion the version of that scheme, or {@code null}
     * @return the value
     * @throws EsjFormatException   if a string is empty, or a scheme version is given
     *                              without a scheme
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public static SemanticValue identifier(String content, String scheme, String schemeVersion) {
        return new SemanticValue(content, scheme, schemeVersion, null, null);
    }

    /**
     * Creates a binary object from the bytes of a file, encoding them as the canonical
     * padded base64 of the specification, section 6.7. Both components are mandatory in
     * the standard and therefore here.
     *
     * @param bytes    the content of the file
     * @param mimeCode the media type of the file
     * @param filename the file name of the file
     * @return the value
     * @throws EsjFormatException   if the file is empty or a component is empty
     * @throws NullPointerException if an argument is {@code null}
     */
    public static SemanticValue binary(byte[] bytes, String mimeCode, String filename) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mimeCode, "mimeCode");
        Objects.requireNonNull(filename, "filename");
        if (bytes.length == 0) {
            throw new EsjFormatException("value is a non-empty string");
        }
        return new SemanticValue(Base64.getEncoder().encodeToString(bytes),
                null, null, mimeCode, filename);
    }

    /**
     * Creates a value whose content is the canonical decimal form of a number
     * (specification, section 6.4): trailing fraction zeros removed, no exponent, no sign
     * on a zero. A writer handed the exact value {@code 100.00} therefore writes
     * {@code 100}, and no later stage repeats that step for it (section 3.3).
     *
     * @param value the number
     * @return the value
     * @throws EsjFormatException   if the canonical decimal form is longer than 64
     *                              characters
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static SemanticValue ofDecimal(BigDecimal value) {
        return of(ValueSupport.canonicalDecimal(ValueSupport.canonicalNumber(value, "value")));
    }

    /**
     * Creates a value whose content is a calendar date in the form of the specification,
     * section 6.5.
     *
     * @param value the date, from {@code 1000-01-01} to {@code 9999-12-31}
     * @return the value
     * @throws EsjFormatException   if the year lies outside the representable range
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static SemanticValue ofDate(LocalDate value) {
        Objects.requireNonNull(value, "value");
        if (value.getYear() < 1000 || value.getYear() > 9999) {
            throw new EsjFormatException(
                    "a date carries a year from 1000 to 9999, not " + value.getYear());
        }
        return of(value.toString());
    }

    /**
     * Creates a value whose content is a time of day with its offset from UTC, in the
     * form of the specification, section 6.5. It is the writing side of the semantic data
     * type Time, which the 2026 edition of the model introduces.
     *
     * <p>The grammar admits one spelling per time, so this is where a time is brought
     * into it: UTC is written {@code Z}, an offset is written to the minute, and a time
     * carrying fractional seconds is refused rather than truncated, because dropping
     * digits would change the value.
     *
     * @param value the time of day, whose second-of-minute is the last field it carries
     * @return the value
     * @throws EsjFormatException   if the time carries fractional seconds, or an offset
     *                              that is not a whole number of minutes or lies outside
     *                              the range from {@code -14:00} to {@code +14:00}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static SemanticValue ofTime(OffsetTime value) {
        Objects.requireNonNull(value, "value");
        if (value.getNano() != 0) {
            throw new EsjFormatException(
                    "a time carries no fraction of a second, and this one carries "
                            + value.getNano() + " nanoseconds");
        }
        int offset = value.getOffset().getTotalSeconds();
        if (offset % 60 != 0) {
            throw new EsjFormatException("an offset is a whole number of minutes, and "
                    + value.getOffset().getId() + " is not");
        }
        String violation = Grammars.offsetViolation(value.getOffset());
        if (violation != null) {
            throw new EsjFormatException("a time " + violation);
        }
        return of(Grammars.printTime(value));
    }

    /**
     * Tells whether this value carries at least one supplementary component and is
     * therefore written as a JSON object rather than as a JSON string (specification,
     * section 6.1).
     *
     * @return {@code true} if a scheme, a scheme version, a media type or a file name is
     *         present
     */
    public boolean hasComponents() {
        return scheme != null || schemeVersion != null || mimeCode != null || filename != null;
    }

    /**
     * Returns the content as a document carries it: the string itself for a value written
     * as a JSON string, and the {@code value} member for one written as an object. The
     * content is never rewritten, so this is the record component under another name and
     * the name a serializer reads it by.
     *
     * @return the content, never empty
     */
    public String canonicalContent() {
        return content;
    }

    /**
     * Returns the content as text. It is the accessor of the semantic data types whose
     * content has no grammar beyond being a non-empty string: Text, Identifier, Code and
     * Document Reference (specification, section 6.2).
     *
     * @return the content, never empty
     */
    public String asString() {
        return content;
    }

    /**
     * Reads the content as an exact decimal (specification, section 6.4). It is the
     * accessor of the semantic data types Amount, Unit Price Amount, Quantity and
     * Percentage.
     *
     * <p>No binary floating point type takes part, and nothing is rounded or repaired: a
     * content outside the canonical decimal form is refused rather than read as the
     * number it resembles.
     *
     * @return the number the content spells
     * @throws EsjFormatException if the content is not a canonical decimal of at most 64
     *                            characters; the exception carries
     *                            {@link FindingCode#ESJ_L2_DECIMAL}
     */
    public BigDecimal asDecimal() {
        if (content.length() > Grammars.MAX_DECIMAL_LENGTH || !Grammars.isDecimal(content)) {
            throw new EsjFormatException(
                    Grammars.excerpt(content) + " " + Grammars.decimalViolation(content),
                    FindingCode.ESJ_L2_DECIMAL, null);
        }
        return new BigDecimal(content);
    }

    /**
     * Reads the content as a calendar date (specification, section 6.5). It is the
     * accessor of the semantic data type Date.
     *
     * @return the date the content spells
     * @throws EsjFormatException if the content is outside the date grammar or names a day
     *                            that does not exist; the exception carries
     *                            {@link FindingCode#ESJ_L2_DATE}
     */
    public LocalDate asDate() {
        LocalDate parsed = Grammars.parseDate(content);
        if (parsed == null) {
            throw new EsjFormatException(
                    Grammars.excerpt(content) + " " + Grammars.dateViolation(content),
                    FindingCode.ESJ_L2_DATE, null);
        }
        return parsed;
    }

    /**
     * Reads the content as a time of day with its offset from UTC (specification,
     * section 6.5). It is the accessor of the semantic data type Time, which the 2026
     * edition of the model introduces.
     *
     * @return the time the content spells
     * @throws EsjFormatException if the content is outside the time grammar; the exception
     *                            carries {@link FindingCode#ESJ_L2_TIME}
     */
    public OffsetTime asTime() {
        OffsetTime parsed = Grammars.parseTime(content);
        if (parsed == null) {
            throw new EsjFormatException(
                    Grammars.excerpt(content) + " " + Grammars.timeViolation(content),
                    FindingCode.ESJ_L2_TIME, null);
        }
        return parsed;
    }

    /**
     * Decodes the content as the canonical padded base64 of the specification,
     * section 6.7. It is the accessor of the semantic data type Binary Object.
     *
     * <p>The caller receives a fresh array and may do what it likes with it, except open,
     * render or execute what it holds: an attachment is a file a stranger sent
     * (specification, section 12.5).
     *
     * @return the decoded bytes
     * @throws EsjFormatException if the content is not canonical padded base64; the
     *                            exception carries {@link FindingCode#ESJ_L2_BASE64}
     */
    public byte[] asBytes() {
        if (!Grammars.isBase64(content)) {
            throw new EsjFormatException(
                    "a binary object value " + Grammars.base64Violation(content),
                    FindingCode.ESJ_L2_BASE64, null);
        }
        return Base64.getDecoder().decode(content);
    }

    /**
     * Returns a description that names the components and only an excerpt of the content:
     * a value may be an attachment of thirty megabytes, and a description of it reaches a
     * log line (specification, section 12.6).
     *
     * @return a short description of the value
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("SemanticValue[value=\"")
                .append(Esj.forMessage(content, DESCRIPTION_EXCERPT))
                .append("\" (").append(content.length()).append(" characters)");
        append(text, "scheme", scheme);
        append(text, "schemeVersion", schemeVersion);
        append(text, "mimeCode", mimeCode);
        append(text, "filename", filename);
        return text.append(']').toString();
    }

    private static void append(StringBuilder text, String name, String value) {
        if (value != null) {
            text.append(", ").append(name).append("=\"")
                    .append(Esj.forMessage(value, DESCRIPTION_EXCERPT)).append('"');
        }
    }
}
