using System;

namespace BSNSoft.Esj.Json;

/// <summary>
/// A limit of the specification, section 12.2 was reached, so this implementation declines
/// to process the document under its configuration.
/// </summary>
/// <remarks>
/// This is not a statement about the document (specification, section 3.1). The same byte
/// sequence is a conformant document for a reader configured differently, and forwarding it
/// to a party with a larger bound is a sensible answer to this exception, which it would not
/// be to an <see cref="EsjFormatException"/>. The finding code is always <c>ESJ-L1-LIMIT</c>, and
/// the exception carries the path and the subject the finding of the same limit carries.
/// </remarks>
public sealed class EsjLimitException : EsjException
{
    /// <summary>The finding code every limit is reported with.</summary>
    public const string LimitCode = "ESJ-L1-LIMIT";

    /// <summary>Creates the exception.</summary>
    /// <param name="message">which bound was reached, in English</param>
    /// <param name="subject">the place in the document, written as a member access</param>
    /// <param name="path">the semantic path of the member of <c>values</c> the bound was reached in, or <c>null</c> for the root</param>
    public EsjLimitException(string message, string subject, SemanticPath? path = null) : base(message)
    {
        Subject = subject ?? throw new ArgumentNullException(nameof(subject));
        Path = path ?? SemanticPath.Root();
    }

    /// <summary>Returns the finding code of the specification, section 9.6: <c>ESJ-L1-LIMIT</c>.</summary>
    public string Code => LimitCode;

    /// <summary>
    /// Returns the semantic path of the member of <c>values</c> the bound was reached in, or the
    /// root of the document where it was reached elsewhere (specification, section 9.5).
    /// </summary>
    public SemanticPath Path { get; }

    /// <summary>
    /// Returns the member access of the member whose name or value reached the bound, or the
    /// empty string for a bound of the whole document.
    /// </summary>
    public string Subject { get; }
}
