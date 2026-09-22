using System;
using System.Collections.Generic;

namespace En16931.SemanticJson.Validation;

/// <summary>The three layers of the specification, section 9.</summary>
public enum ValidationLayer
{
    /// <summary>Format: what the bytes of the document decide.</summary>
    L1,

    /// <summary>Model: what the registry of the edition decides about one path at a time.</summary>
    L2,

    /// <summary>Cardinality: what the document as a whole decides.</summary>
    L3,
}

/// <summary>How much a finding weighs (specification, section 9.5).</summary>
public enum Severity
{
    /// <summary>The document fails the layer the finding belongs to.</summary>
    Error,

    /// <summary>Worth saying, and the document does not fail the layer for it.</summary>
    Warning,

    /// <summary>No judgement about the document at all.</summary>
    Info,
}

/// <summary>The three states a validation ends in (specification, section 9.5).</summary>
public enum ValidationStatus
{
    /// <summary>All three layers were evaluated and nothing was found.</summary>
    Valid,

    /// <summary>A defect was found.</summary>
    Invalid,

    /// <summary>Nothing was found and something was not evaluated.</summary>
    Indeterminate,
}

/// <summary>
/// Why a layer was not evaluated, from the closed vocabulary of the specification,
/// section 9.5. The order of the members is the order of precedence that section fixes.
/// </summary>
public enum NotEvaluatedReason
{
    /// <summary>A limit of section 12.2 stopped the run before the layer was complete.</summary>
    Limit,

    /// <summary>An earlier layer did not produce what this one needs.</summary>
    PrecedingLayerFailed,

    /// <summary>No registry is available for the edition the document names.</summary>
    EditionUnknown,

    /// <summary>The caller asked for a subset of the layers, or the layer could not be asked.</summary>
    NotRequested,
}

/// <summary>
/// A finding code of the specification, section 9.6: what kind of problem was met, which
/// layer it belongs to and the severity a validator reports it with.
/// </summary>
public sealed class FindingCode
{
    private static readonly Dictionary<string, FindingCode> ByCode = new(StringComparer.Ordinal);

    private FindingCode(string code, ValidationLayer layer, Severity severity)
    {
        Code = code;
        Layer = layer;
        DefaultSeverity = severity;
        ByCode.Add(code, this);
    }

    /// <summary>The byte sequence is not UTF-8, or it starts with a byte order mark.</summary>
    public static FindingCode EncodingCode { get; } = new("ESJ-L1-ENCODING", ValidationLayer.L1, Severity.Error);

    /// <summary>The byte sequence is not a JSON text, or its top level is not an object.</summary>
    public static FindingCode JsonCode { get; } = new("ESJ-L1-JSON", ValidationLayer.L1, Severity.Error);

    /// <summary>A member name occurs twice in one object, at any depth.</summary>
    public static FindingCode DuplicateMember { get; } = new("ESJ-L1-DUPLICATE-MEMBER", ValidationLayer.L1, Severity.Error);

    /// <summary>A required envelope member is missing, or an undefined one is present.</summary>
    public static FindingCode EnvelopeMember { get; } = new("ESJ-L1-ENVELOPE-MEMBER", ValidationLayer.L1, Severity.Error);

    /// <summary>An envelope member does not carry what section 4 gives it.</summary>
    public static FindingCode EnvelopeValue { get; } = new("ESJ-L1-ENVELOPE-VALUE", ValidationLayer.L1, Severity.Error);

    /// <summary>A member name of <c>extensions</c> is not an owner token.</summary>
    public static FindingCode OwnerToken { get; } = new("ESJ-L1-OWNER-TOKEN", ValidationLayer.L1, Severity.Error);

    /// <summary>A member name of <c>values</c> does not match the path grammar.</summary>
    public static FindingCode PathSyntax { get; } = new("ESJ-L1-PATH-SYNTAX", ValidationLayer.L1, Severity.Error);

    /// <summary>A JSON number, boolean, <c>null</c> or array appears inside <c>values</c>.</summary>
    public static FindingCode JsonType { get; } = new("ESJ-L1-JSON-TYPE", ValidationLayer.L1, Severity.Error);

    /// <summary>A number inside <c>extensions</c> is longer than 64 characters canonically.</summary>
    public static FindingCode ExtensionNumber { get; } = new("ESJ-L1-EXT-NUMBER", ValidationLayer.L1, Severity.Error);

    /// <summary>A value has the wrong shape (specification, section 6.1).</summary>
    public static FindingCode ValueShape { get; } = new("ESJ-L1-VALUE-SHAPE", ValidationLayer.L1, Severity.Error);

    /// <summary>A value object carries a member set this specification does not define.</summary>
    public static FindingCode ValueMember { get; } = new("ESJ-L1-VALUE-MEMBER", ValidationLayer.L1, Severity.Error);

    /// <summary>A string inside <c>values</c> is empty.</summary>
    public static FindingCode EmptyString { get; } = new("ESJ-L1-EMPTY-STRING", ValidationLayer.L1, Severity.Error);

    /// <summary>A string carries a lone surrogate.</summary>
    public static FindingCode Surrogate { get; } = new("ESJ-L1-SURROGATE", ValidationLayer.L1, Severity.Error);

    /// <summary>A limit of section 12.2 was exceeded, which is no verdict on the document.</summary>
    public static FindingCode Limit { get; } = new("ESJ-L1-LIMIT", ValidationLayer.L1, Severity.Error);

    /// <summary>A core segment names a term the registry does not contain.</summary>
    public static FindingCode UnknownTerm { get; } = new("ESJ-L2-UNKNOWN-TERM", ValidationLayer.L2, Severity.Error);

    /// <summary>The group segments are not a parent chain a registry records for the term.</summary>
    public static FindingCode ParentChain { get; } = new("ESJ-L2-PARENT-CHAIN", ValidationLayer.L2, Severity.Error);

    /// <summary>The content of a decimal term is not the canonical decimal form.</summary>
    public static FindingCode Decimal { get; } = new("ESJ-L2-DECIMAL", ValidationLayer.L2, Severity.Error);

    /// <summary>The content of a <c>Date</c> term is not a date of section 6.5.</summary>
    public static FindingCode Date { get; } = new("ESJ-L2-DATE", ValidationLayer.L2, Severity.Error);

    /// <summary>The content of a <c>Time</c> term is not a time of section 6.5.</summary>
    public static FindingCode Time { get; } = new("ESJ-L2-TIME", ValidationLayer.L2, Severity.Error);

    /// <summary>The content of a <c>BinaryObject</c> term is not canonical base64.</summary>
    public static FindingCode Base64 { get; } = new("ESJ-L2-BASE64", ValidationLayer.L2, Severity.Error);

    /// <summary>A component is present where the registry lists none for the term.</summary>
    public static FindingCode ComponentNotAllowed { get; } = new("ESJ-L2-COMPONENT-NOT-ALLOWED", ValidationLayer.L2, Severity.Error);

    /// <summary>A component the registry declares mandatory is absent.</summary>
    public static FindingCode ComponentMissing { get; } = new("ESJ-L2-COMPONENT-MISSING", ValidationLayer.L2, Severity.Error);

    /// <summary>A repeatable term or group carries no index segment.</summary>
    public static FindingCode IndexRequired { get; } = new("ESJ-L2-INDEX-REQUIRED", ValidationLayer.L2, Severity.Error);

    /// <summary>A term or group of maximum cardinality one carries an index segment.</summary>
    public static FindingCode IndexForbidden { get; } = new("ESJ-L2-INDEX-FORBIDDEN", ValidationLayer.L2, Severity.Error);

    /// <summary>An extension term could not be checked, because its registry is not loaded.</summary>
    public static FindingCode NotChecked { get; } = new("ESJ-L2-NOT-CHECKED", ValidationLayer.L2, Severity.Info);

    /// <summary>No registry is available for the edition the document names.</summary>
    public static FindingCode EditionUnknown { get; } = new("ESJ-L2-EDITION-UNKNOWN", ValidationLayer.L2, Severity.Info);

    /// <summary>Indices under one parent instance are not dense and zero-based.</summary>
    public static FindingCode IndexGap { get; } = new("ESJ-L3-INDEX-GAP", ValidationLayer.L3, Severity.Error);

    /// <summary>A mandatory term is absent from a group instance or from the root.</summary>
    public static FindingCode MissingTerm { get; } = new("ESJ-L3-MISSING-TERM", ValidationLayer.L3, Severity.Error);

    /// <summary>A mandatory group has no instance in a group instance or at the root.</summary>
    public static FindingCode MissingGroup { get; } = new("ESJ-L3-MISSING-GROUP", ValidationLayer.L3, Severity.Error);

    /// <summary>A term or group occurs more often than its maximum cardinality allows.</summary>
    public static FindingCode MaxCardinality { get; } = new("ESJ-L3-MAX-CARDINALITY", ValidationLayer.L3, Severity.Error);

    /// <summary>Returns the code as the specification writes it.</summary>
    public string Code { get; }

    /// <summary>Returns the layer this code belongs to.</summary>
    public ValidationLayer Layer { get; }

    /// <summary>Returns the severity a validator reports this code with.</summary>
    public Severity DefaultSeverity { get; }

    /// <summary>
    /// Tells whether this code records something that was not evaluated, which makes a
    /// result <c>INDETERMINATE</c> rather than <c>VALID</c> (specification, section 9.5).
    /// </summary>
    public bool RecordsSomethingNotEvaluated =>
        ReferenceEquals(this, Limit) || ReferenceEquals(this, NotChecked) || ReferenceEquals(this, EditionUnknown);

    /// <summary>Returns the code with that spelling.</summary>
    /// <param name="code">the code, for example <c>ESJ-L2-DATE</c></param>
    /// <returns>the finding code</returns>
    /// <exception cref="ArgumentException">if this specification defines no such code</exception>
    public static FindingCode Of(string code)
    {
        ArgumentNullException.ThrowIfNull(code);
        return ByCode.TryGetValue(code, out FindingCode? found)
            ? found
            : throw new ArgumentException("no finding code of this specification is spelled " + code, nameof(code));
    }

    /// <inheritdoc />
    public override string ToString() => Code;
}
