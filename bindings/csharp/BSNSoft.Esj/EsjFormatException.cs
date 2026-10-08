using System;

namespace BSNSoft.Esj;

/// <summary>
/// A document that does not satisfy the format, with the finding code of the specification,
/// section 9.6 that names the defect, and the path and the subject of the finding it stands for.
/// </summary>
/// <remarks>
/// A reader that is asked to reject throws this; a reader that is asked to report carries
/// the same code, the same path and the same subject in a finding. Both are conformant, and a
/// caller that must handle either catches the exception and branches on its code, exactly as it
/// would branch on a finding's (specification, section 9.5).
/// </remarks>
public sealed class EsjFormatException : EsjException
{
    /// <summary>Creates the exception.</summary>
    /// <param name="code">the finding code, or <c>null</c> where none applies</param>
    /// <param name="message">what the defect is, in English</param>
    /// <param name="subject">the place in the document, written as a member access</param>
    /// <param name="path">the semantic path the defect is about, or <c>null</c> for the root</param>
    public EsjFormatException(string? code, string message, string subject = "", SemanticPath? path = null)
        : base(message)
    {
        Code = code;
        Subject = subject ?? throw new ArgumentNullException(nameof(subject));
        Path = path ?? SemanticPath.Root();
    }

    /// <summary>Returns the finding code of the specification, section 9.6.</summary>
    public string? Code { get; }

    /// <summary>
    /// Returns the semantic path the defect is about: the member of <c>values</c> it was met in,
    /// or the root of the document where the defect is not confined to one such member or its
    /// name is no semantic path (specification, section 9.5).
    /// </summary>
    public SemanticPath Path { get; }

    /// <summary>
    /// Returns the place in the document, written as the member access of the specification,
    /// section 9.5 — <c>values["/BG-4/BT-29"].scheme</c> — whole and never shortened, or the empty
    /// string where the finding names none.
    /// </summary>
    public string Subject { get; }
}
