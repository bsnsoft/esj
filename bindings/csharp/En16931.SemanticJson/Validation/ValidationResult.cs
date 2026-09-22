using System;
using System.Collections.Generic;
using System.Linq;

namespace En16931.SemanticJson.Validation;

/// <summary>
/// One thing a validator has to say about a document (specification, section 9.5).
/// </summary>
/// <param name="Path">the semantic path the finding is about, the root for a document-level one</param>
/// <param name="Subject">what the finding is about where the path cannot name it</param>
/// <param name="Code">the finding code of section 9.6</param>
/// <param name="Severity">how much the finding weighs</param>
/// <param name="Message">the finding in English, with quoted document content escaped</param>
public sealed record Finding(
    SemanticPath Path,
    string Subject,
    FindingCode Code,
    Severity Severity,
    string Message)
{
    /// <summary>Returns a finding about a path.</summary>
    /// <param name="path">the path</param>
    /// <param name="code">the finding code</param>
    /// <param name="message">the finding in English</param>
    /// <returns>the finding</returns>
    public static Finding Of(SemanticPath path, FindingCode code, string message) =>
        About(path, string.Empty, code, message);

    /// <summary>Returns a finding about a path, naming what it is about.</summary>
    /// <param name="path">the path</param>
    /// <param name="subject">the term, the group or the place in the document</param>
    /// <param name="code">the finding code</param>
    /// <param name="message">the finding in English</param>
    /// <returns>the finding</returns>
    public static Finding About(SemanticPath path, string subject, FindingCode code, string message)
    {
        ArgumentNullException.ThrowIfNull(path);
        ArgumentNullException.ThrowIfNull(subject);
        ArgumentNullException.ThrowIfNull(code);
        ArgumentNullException.ThrowIfNull(message);
        return new Finding(path, subject, code, code.DefaultSeverity, message);
    }

    /// <summary>Returns a finding about the document itself.</summary>
    /// <param name="code">the finding code</param>
    /// <param name="message">the finding in English</param>
    /// <returns>the finding</returns>
    public static Finding OfDocument(FindingCode code, string message) =>
        Of(SemanticPath.Root(), code, message);

    /// <summary>Tells whether the document fails the layer this finding belongs to.</summary>
    public bool IsError => Severity == Severity.Error;

    /// <inheritdoc />
    public override string ToString() => Code + " [" + Severity + "] " + Path + ": " + Message;
}

/// <summary>
/// What a validator answers: a status, the findings, and a statement of what was covered
/// (specification, section 9.5).
/// </summary>
/// <remarks>
/// An empty finding list is not conformance. A validator that evaluated nothing also
/// reports nothing, so a caller reads the status and not the length of the list.
/// </remarks>
public sealed class ValidationResult
{
    private readonly List<Finding> _findings;
    private readonly SortedDictionary<ValidationLayer, NotEvaluatedReason> _notEvaluated;
    private readonly List<string> _registries;

    private ValidationResult(
        List<Finding> findings,
        SortedDictionary<ValidationLayer, NotEvaluatedReason> notEvaluated,
        List<string> registries)
    {
        _findings = findings;
        _notEvaluated = notEvaluated;
        _registries = registries;
        Status = StatusOf(findings, notEvaluated);
    }

    /// <summary>Returns a result.</summary>
    /// <param name="findings">the findings, in the order the validator produced them</param>
    /// <param name="notEvaluated">the layers that were not evaluated, each with the reason</param>
    /// <param name="registries">the editions the run measured the document against</param>
    /// <returns>the result</returns>
    public static ValidationResult Of(
        IEnumerable<Finding> findings,
        IReadOnlyDictionary<ValidationLayer, NotEvaluatedReason> notEvaluated,
        IEnumerable<string>? registries = null)
    {
        ArgumentNullException.ThrowIfNull(findings);
        ArgumentNullException.ThrowIfNull(notEvaluated);
        SortedDictionary<ValidationLayer, NotEvaluatedReason> reasons = new();
        foreach (KeyValuePair<ValidationLayer, NotEvaluatedReason> reason in notEvaluated)
        {
            reasons[reason.Key] = reason.Value;
        }

        return new ValidationResult(
            new List<Finding>(findings),
            reasons,
            registries is null ? new List<string>() : registries.Distinct(StringComparer.Ordinal).ToList());
    }

    /// <summary>Returns the status of the run.</summary>
    public ValidationStatus Status { get; }

    /// <summary>Returns the findings, in the order they were produced.</summary>
    public IReadOnlyList<Finding> Findings => _findings;

    /// <summary>Returns the layers that were evaluated.</summary>
    public IReadOnlyCollection<ValidationLayer> Evaluated =>
        Enum.GetValues<ValidationLayer>().Where(layer => !_notEvaluated.ContainsKey(layer)).ToList();

    /// <summary>Returns the layers that were not evaluated, each with the reason.</summary>
    public IReadOnlyDictionary<ValidationLayer, NotEvaluatedReason> NotEvaluated => _notEvaluated;

    /// <summary>Returns the editions this run measured the document against.</summary>
    public IReadOnlyList<string> Registries => _registries;

    /// <summary>
    /// Composes this result with another, which is how a caller that ran the reader and the
    /// structural validator reads one verdict off the two. A layer is evaluated where
    /// either run evaluated it, and where neither did, the reason of higher precedence
    /// survives (specification, section 9.5).
    /// </summary>
    /// <param name="other">the other result</param>
    /// <returns>the composed result</returns>
    public ValidationResult Merge(ValidationResult other)
    {
        ArgumentNullException.ThrowIfNull(other);
        List<Finding> together = new(_findings);
        together.AddRange(other._findings);
        Dictionary<ValidationLayer, NotEvaluatedReason> reasons = new();
        foreach (ValidationLayer layer in Enum.GetValues<ValidationLayer>())
        {
            if (_notEvaluated.TryGetValue(layer, out NotEvaluatedReason mine)
                && other._notEvaluated.TryGetValue(layer, out NotEvaluatedReason theirs))
            {
                reasons[layer] = mine <= theirs ? mine : theirs;
            }
        }

        return Of(together, reasons, _registries.Concat(other._registries));
    }

    /// <inheritdoc />
    public override string ToString() =>
        "ValidationResult[" + Status + ", findings=" + _findings.Count
        + ", evaluated=" + string.Join(", ", Evaluated) + "]";

    private static ValidationStatus StatusOf(
        List<Finding> findings,
        SortedDictionary<ValidationLayer, NotEvaluatedReason> notEvaluated)
    {
        bool unresolved = notEvaluated.Count > 0;
        foreach (Finding finding in findings)
        {
            if (finding.Code.RecordsSomethingNotEvaluated)
            {
                unresolved = true;
            }
            else if (finding.IsError)
            {
                return ValidationStatus.Invalid;
            }
        }

        return unresolved ? ValidationStatus.Indeterminate : ValidationStatus.Valid;
    }
}
