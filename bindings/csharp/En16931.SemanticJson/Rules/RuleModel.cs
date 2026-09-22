using System;
using System.Collections.Generic;
using System.Globalization;

namespace En16931.SemanticJson.Rules;

/// <summary>A pack this engine refuses, which is never a statement about an invoice.</summary>
public sealed class RulePackException : EsjException
{
    /// <summary>Creates the exception.</summary>
    /// <param name="message">what is wrong with the pack, in English</param>
    public RulePackException(string message) : base(message)
    {
    }

    /// <summary>Creates the exception.</summary>
    /// <param name="message">what is wrong with the pack, in English</param>
    /// <param name="cause">what raised it</param>
    public RulePackException(string message, Exception cause) : base(message, cause)
    {
    }
}

/// <summary>
/// A rule that cannot be decided on this document, because a value it reads does not spell
/// what its semantic data type requires.
/// </summary>
/// <remarks>
/// An amount that reads <c>1.000,00</c> is not a number this engine will guess at. The
/// structural validator reports it at layer L2 with its path, and a rule that reads it stops
/// there: it produces one <c>info</c> finding saying which rule was not decided and why, and
/// no verdict. Reporting the same defect a second time as a failed business rule would make
/// one problem look like two.
/// </remarks>
public sealed class UndecidedException : EsjException
{
    /// <summary>Creates the signal.</summary>
    /// <param name="message">what could not be read, in English, with the text escaped</param>
    public UndecidedException(string message) : base(message)
    {
    }
}

/// <summary>How much a rule finding weighs.</summary>
public enum RuleSeverity
{
    /// <summary>The document fails the rule; a fatal finding decides the verdict.</summary>
    Fatal,

    /// <summary>The document passes the rule, and something about it is worth saying.</summary>
    Warning,

    /// <summary>No judgement: the rule could not be decided on this document.</summary>
    Info,
}

/// <summary>Which family of the validation artefacts a rule belongs to.</summary>
public enum RuleCategory
{
    /// <summary>An integrity constraint or a condition of clause 6.4.</summary>
    EnBr,

    /// <summary>A decimal restriction the artefacts derive from clause 6.5.12.</summary>
    EnDec,

    /// <summary>A code list restriction.</summary>
    EnCl,
}

/// <summary>One thing a rule pack has to say about a document.</summary>
/// <param name="Code">the identifier the standard gives the rule</param>
/// <param name="Category">the family the rule belongs to</param>
/// <param name="Severity">how much the finding weighs</param>
/// <param name="Message">what the finding says, in English</param>
/// <param name="Paths">the paths and patterns the rule read, in the order it read them</param>
/// <param name="PackId">the pack that reported it</param>
/// <param name="PackVersion">the version of that pack</param>
/// <param name="Engine">the engine that produced it</param>
public sealed record RuleFinding(
    string Code,
    RuleCategory Category,
    RuleSeverity Severity,
    string Message,
    IReadOnlyList<string> Paths,
    string PackId,
    string PackVersion,
    string Engine)
{
    /// <summary>The name of the engine of this implementation.</summary>
    public const string NativeEngine = "native";

    /// <summary>Tells whether the finding decides the verdict.</summary>
    public bool IsFatal => Severity == RuleSeverity.Fatal;

    /// <summary>Returns the first path the rule read, or the empty string.</summary>
    public string FirstPath => Paths.Count == 0 ? string.Empty : Paths[0];

    /// <summary>
    /// The order findings are reported in: the rule identifier, then the first path the rule
    /// read, then the severity and the message, so that two runs over one document report in
    /// the same order.
    /// </summary>
    public static IComparer<RuleFinding> Order { get; } = Comparer<RuleFinding>.Create((left, right) =>
    {
        int codes = string.CompareOrdinal(left.Code, right.Code);
        if (codes != 0)
        {
            return codes;
        }

        int paths = string.CompareOrdinal(left.FirstPath, right.FirstPath);
        if (paths != 0)
        {
            return paths;
        }

        int severities = left.Severity.CompareTo(right.Severity);
        return severities != 0 ? severities : string.CompareOrdinal(left.Message, right.Message);
    });

    /// <summary>Returns the family a rule identifier belongs to.</summary>
    /// <param name="ruleId">the identifier</param>
    /// <returns>the category</returns>
    public static RuleCategory CategoryOf(string ruleId)
    {
        ArgumentNullException.ThrowIfNull(ruleId);
        if (ruleId.StartsWith("BR-DEC-", StringComparison.Ordinal))
        {
            return RuleCategory.EnDec;
        }

        return ruleId.StartsWith("BR-CL-", StringComparison.Ordinal) ? RuleCategory.EnCl : RuleCategory.EnBr;
    }

    /// <inheritdoc />
    public override string ToString() =>
        Category + " " + Code + " [" + Severity.ToString().ToLower(CultureInfo.InvariantCulture) + "] " + Message;
}
