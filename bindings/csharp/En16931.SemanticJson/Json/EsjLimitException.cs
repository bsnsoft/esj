using System;

namespace En16931.SemanticJson.Json;

/// <summary>
/// A limit of the specification, section 12.2 was reached, so this implementation declines
/// to process the document under its configuration.
/// </summary>
/// <remarks>
/// This is not a statement about the document (specification, section 3.1). The same byte
/// sequence is a conformant document for a reader configured differently, and forwarding it
/// to a party with a larger bound is a sensible answer to this exception, which it would not
/// be to an <see cref="EsjFormatException"/>. The finding code is always <c>ESJ-L1-LIMIT</c>.
/// </remarks>
public sealed class EsjLimitException : EsjException
{
    /// <summary>Creates the exception.</summary>
    /// <param name="message">which bound was reached, in English</param>
    /// <param name="subject">the place in the document, written as a member access</param>
    public EsjLimitException(string message, string subject) : base(message)
    {
        Subject = subject;
    }

    /// <summary>Returns the finding code of the specification, section 9.6.</summary>
    public static string Code => "ESJ-L1-LIMIT";

    /// <summary>Returns the place in the document the reader had reached.</summary>
    public string Subject { get; }
}
