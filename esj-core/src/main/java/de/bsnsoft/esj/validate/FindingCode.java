package de.bsnsoft.esj.validate;

/**
 * The stable, machine-readable identifiers of the kinds of problem a validator reports
 * (specification, section 9.6). Each code names its layer and the severity a validator
 * must use when it reports that code at the layer it was asked for.
 *
 * <p>The prefix {@code ESJ-} is reserved for the specification. A validator that reports
 * problems of its own uses a different prefix.
 */
public enum FindingCode {

    /** The byte sequence is not UTF-8, or it starts with a byte order mark. */
    ESJ_L1_ENCODING("ESJ-L1-ENCODING", ValidationLayer.L1, Severity.ERROR),

    /** The byte sequence is not a JSON text, or its top level is not an object. */
    ESJ_L1_JSON("ESJ-L1-JSON", ValidationLayer.L1, Severity.ERROR),

    /** A member name occurs twice in one object, at any depth. */
    ESJ_L1_DUPLICATE_MEMBER("ESJ-L1-DUPLICATE-MEMBER", ValidationLayer.L1, Severity.ERROR),

    /** A required envelope member is missing, or an undefined one is present. */
    ESJ_L1_ENVELOPE_MEMBER("ESJ-L1-ENVELOPE-MEMBER", ValidationLayer.L1, Severity.ERROR),

    /** An envelope member does not carry the value or the JSON type it must have. */
    ESJ_L1_ENVELOPE_VALUE("ESJ-L1-ENVELOPE-VALUE", ValidationLayer.L1, Severity.ERROR),

    /** A member name of {@code extensions} is not an owner token. */
    ESJ_L1_OWNER_TOKEN("ESJ-L1-OWNER-TOKEN", ValidationLayer.L1, Severity.ERROR),

    /** A member name of {@code values} does not match the path grammar. */
    ESJ_L1_PATH_SYNTAX("ESJ-L1-PATH-SYNTAX", ValidationLayer.L1, Severity.ERROR),

    /** A JSON number, {@code true}, {@code false}, {@code null} or array appears inside {@code values}. */
    ESJ_L1_JSON_TYPE("ESJ-L1-JSON-TYPE", ValidationLayer.L1, Severity.ERROR),

    /** A number inside {@code extensions} has a canonical decimal form of more than 64 characters. */
    ESJ_L1_EXT_NUMBER("ESJ-L1-EXT-NUMBER", ValidationLayer.L1, Severity.ERROR),

    /**
     * A member of {@code values} is an object that carries no supplementary component, or
     * a member of a value object is itself a JSON object (specification, section 6.1).
     */
    ESJ_L1_VALUE_SHAPE("ESJ-L1-VALUE-SHAPE", ValidationLayer.L1, Severity.ERROR),

    /**
     * A value object lacks {@code value}, carries a member outside the five of the
     * specification, section 6.1, or carries {@code schemeVersion} without {@code scheme}.
     */
    ESJ_L1_VALUE_MEMBER("ESJ-L1-VALUE-MEMBER", ValidationLayer.L1, Severity.ERROR),

    /** A value written as a string, or a member of a value object, is the empty string. */
    ESJ_L1_EMPTY_STRING("ESJ-L1-EMPTY-STRING", ValidationLayer.L1, Severity.ERROR),

    /** A string contains a lone surrogate. */
    ESJ_L1_SURROGATE("ESJ-L1-SURROGATE", ValidationLayer.L1, Severity.ERROR),

    /**
     * A limit of the specification, section 12.2 is exceeded, so this implementation
     * declines to process the document under its configuration.
     *
     * <p>This is the one code that is not a statement about the document. A limit is the
     * reading party's policy and takes no part in conformance (specification,
     * section 3.1): the same byte sequence is a conformant document for a reader
     * configured differently, and forwarding it to a party with a larger bound is a
     * sensible answer to this finding. The prefix says L1 because that is where a reader
     * meets it, not that the document failed a layer.
     */
    ESJ_L1_LIMIT("ESJ-L1-LIMIT", ValidationLayer.L1, Severity.ERROR),

    /** A core segment names a term the registry does not contain. */
    ESJ_L2_UNKNOWN_TERM("ESJ-L2-UNKNOWN-TERM", ValidationLayer.L2, Severity.ERROR),

    /** The group segments are not a parent chain a loaded registry records for the term. */
    ESJ_L2_PARENT_CHAIN("ESJ-L2-PARENT-CHAIN", ValidationLayer.L2, Severity.ERROR),

    /**
     * The content of a term whose registry datatype is a decimal one does not match the
     * canonical decimal form, or exceeds 64 characters (specification, sections 6.2
     * and 6.4).
     */
    ESJ_L2_DECIMAL("ESJ-L2-DECIMAL", ValidationLayer.L2, Severity.ERROR),

    /**
     * The content of a {@code Date} term does not match the date grammar, or names a day
     * that does not exist (specification, sections 6.2 and 6.5).
     */
    ESJ_L2_DATE("ESJ-L2-DATE", ValidationLayer.L2, Severity.ERROR),

    /**
     * The content of a {@code Time} term does not match the time grammar, which carries the
     * offset and admits one spelling per value (specification, sections 6.2 and 6.5).
     */
    ESJ_L2_TIME("ESJ-L2-TIME", ValidationLayer.L2, Severity.ERROR),

    /**
     * The content of a {@code BinaryObject} term is not canonical padded base64
     * (specification, sections 6.2 and 6.7).
     */
    ESJ_L2_BASE64("ESJ-L2-BASE64", ValidationLayer.L2, Severity.ERROR),

    /** A supplementary component is present where the registry lists none for the term. */
    ESJ_L2_COMPONENT_NOT_ALLOWED("ESJ-L2-COMPONENT-NOT-ALLOWED", ValidationLayer.L2, Severity.ERROR),

    /**
     * A component the registry declares mandatory is absent, which for a
     * {@code BinaryObject} term means {@code mimeCode} or {@code filename}.
     */
    ESJ_L2_COMPONENT_MISSING("ESJ-L2-COMPONENT-MISSING", ValidationLayer.L2, Severity.ERROR),

    /** A repeatable term or group carries no index segment. */
    ESJ_L2_INDEX_REQUIRED("ESJ-L2-INDEX-REQUIRED", ValidationLayer.L2, Severity.ERROR),

    /** A term or group with maximum cardinality 1 carries an index segment. */
    ESJ_L2_INDEX_FORBIDDEN("ESJ-L2-INDEX-FORBIDDEN", ValidationLayer.L2, Severity.ERROR),

    /**
     * An extension segment, or a core term re-rooted by an extension, could not be checked
     * because that registry is not loaded (specification, section 5.6).
     */
    ESJ_L2_NOT_CHECKED("ESJ-L2-NOT-CHECKED", ValidationLayer.L2, Severity.INFO),

    /**
     * No registry is available for the edition the document names, so neither layer L2
     * nor layer L3 was evaluated (specification, sections 4.4 and 9.2).
     *
     * <p>Which editions an implementation holds a registry for is a property of the
     * implementation and not of the document, so this is not a defect of the document and
     * must not be presented as one: a validator reports this code once, at document
     * level, and returns the status {@link ValidationStatus#INDETERMINATE}. It never
     * stands beside {@link #ESJ_L2_NOT_CHECKED}, because a validator that checked no path
     * has no path to report that one about.
     */
    ESJ_L2_EDITION_UNKNOWN("ESJ-L2-EDITION-UNKNOWN", ValidationLayer.L2, Severity.INFO),

    /** Indices under one parent instance are not dense and zero-based. */
    ESJ_L3_INDEX_GAP("ESJ-L3-INDEX-GAP", ValidationLayer.L3, Severity.ERROR),

    /** A term with minimum cardinality 1 is absent from a group instance or from the root. */
    ESJ_L3_MISSING_TERM("ESJ-L3-MISSING-TERM", ValidationLayer.L3, Severity.ERROR),

    /** A group with minimum cardinality 1 has no instance in a group instance or at the root. */
    ESJ_L3_MISSING_GROUP("ESJ-L3-MISSING-GROUP", ValidationLayer.L3, Severity.ERROR),

    /** A term or group occurs more often than its maximum cardinality allows. */
    ESJ_L3_MAX_CARDINALITY("ESJ-L3-MAX-CARDINALITY", ValidationLayer.L3, Severity.ERROR);

    private final String code;
    private final ValidationLayer layer;
    private final Severity defaultSeverity;

    FindingCode(String code, ValidationLayer layer, Severity defaultSeverity) {
        this.code = code;
        this.layer = layer;
        this.defaultSeverity = defaultSeverity;
    }

    /**
     * Returns the code as the specification writes it.
     *
     * @return the code, for example {@code ESJ-L3-MISSING-TERM}
     */
    public String code() {
        return code;
    }

    /**
     * Returns the layer this code belongs to.
     *
     * @return the validation layer
     */
    public ValidationLayer layer() {
        return layer;
    }

    /**
     * Returns the severity a validator must use when it reports this code at the layer it
     * was asked for.
     *
     * @return the severity
     */
    public Severity defaultSeverity() {
        return defaultSeverity;
    }

    /**
     * Tells whether this code reports something that was <strong>not evaluated</strong>
     * rather than something that is wrong with the document (specification, section 9.5).
     *
     * <p>Three codes do: {@link #ESJ_L1_LIMIT}, because a limit is the reading party's
     * policy and stops the run rather than judging the document; and
     * {@link #ESJ_L2_NOT_CHECKED} and {@link #ESJ_L2_EDITION_UNKNOWN}, because a registry
     * that is not loaded leaves a path, or the whole model layer, unmeasured. A result
     * carrying one of them is {@link ValidationStatus#INDETERMINATE} and never
     * {@link ValidationStatus#VALID}; none of them alone makes it
     * {@link ValidationStatus#INVALID}, whatever severity the finding carries.
     *
     * @return {@code true} if this code records something that could not be evaluated
     */
    public boolean recordsSomethingNotEvaluated() {
        return this == ESJ_L1_LIMIT
                || this == ESJ_L2_NOT_CHECKED
                || this == ESJ_L2_EDITION_UNKNOWN;
    }

    /**
     * Returns the code as the specification writes it.
     *
     * @return the code
     */
    @Override
    public String toString() {
        return code;
    }
}
