using System;

namespace En16931.SemanticJson;

/// <summary>
/// A document that does not satisfy the format, with the finding code of the specification,
/// section 9.6 that names the defect and the place it sits in.
/// </summary>
/// <remarks>
/// A reader that is asked to reject throws this; a reader that is asked to report carries
/// the same code and the same place in a finding. Both are conformant, and a caller that
/// must handle either catches the exception and branches on its code, exactly as it would
/// branch on a finding's (specification, section 9.5).
/// </remarks>
public sealed class EsjFormatException : EsjException
{
    /// <summary>Creates the exception.</summary>
    /// <param name="code">the finding code, or <c>null</c> where none applies</param>
    /// <param name="message">what the defect is, in English</param>
    /// <param name="subject">the place in the document, written as a member access</param>
    public EsjFormatException(string? code, string message, string subject = "") : base(message)
    {
        Code = code;
        Subject = subject;
    }

    /// <summary>Returns the finding code of the specification, section 9.6.</summary>
    public string? Code { get; }

    /// <summary>
    /// Returns the place in the document, written the way a program would address it —
    /// <c>values["/BG-4/BT-29"].scheme</c> — or the empty string where the reader knows none.
    /// </summary>
    public string Subject { get; }
}
