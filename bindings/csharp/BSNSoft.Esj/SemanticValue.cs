using System;
using BSNSoft.Esj.Json;

namespace BSNSoft.Esj;

/// <summary>
/// One value of a document: its content, and the supplementary components it carries
/// (specification, section 6.1).
/// </summary>
public sealed class SemanticValue
{
    /// <summary>
    /// Creates a value. Every string is normalized as the specification, section 6.8 asks of
    /// every string in <c>values</c>: CR LF and a lone CR become LF, so a value built here and the
    /// same value read from a document are one value with one canonical form and one digest.
    /// </summary>
    /// <param name="content">the content, which is never empty</param>
    /// <param name="scheme">the identification scheme, or <c>null</c></param>
    /// <param name="schemeVersion">the version of that scheme, or <c>null</c></param>
    /// <param name="mimeCode">the media type of an attachment, or <c>null</c></param>
    /// <param name="filename">the file name of an attachment, or <c>null</c></param>
    public SemanticValue(
        string content,
        string? scheme = null,
        string? schemeVersion = null,
        string? mimeCode = null,
        string? filename = null)
    {
        Content = Texts.NormalizeLineEndings(content ?? throw new ArgumentNullException(nameof(content)));
        Scheme = Normalized(scheme);
        SchemeVersion = Normalized(schemeVersion);
        MimeCode = Normalized(mimeCode);
        Filename = Normalized(filename);
    }

    /// <summary>Returns the content of the value.</summary>
    public string Content { get; }

    /// <summary>Returns the identification scheme, or <c>null</c>.</summary>
    public string? Scheme { get; }

    /// <summary>Returns the version of the identification scheme, or <c>null</c>.</summary>
    public string? SchemeVersion { get; }

    /// <summary>Returns the media type of an attachment, or <c>null</c>.</summary>
    public string? MimeCode { get; }

    /// <summary>Returns the file name of an attachment, or <c>null</c>.</summary>
    public string? Filename { get; }

    /// <summary>Tells whether the value carries any supplementary component.</summary>
    public bool HasComponent =>
        Scheme is not null || SchemeVersion is not null || MimeCode is not null || Filename is not null;

    /// <summary>Returns the content as an exact decimal.</summary>
    /// <returns>the number</returns>
    /// <exception cref="EsjFormatException">if the content is not the canonical decimal form</exception>
    public BigDecimal AsDecimal()
    {
        if (!Grammars.IsDecimal(Content))
        {
            throw new EsjFormatException("ESJ-L2-DECIMAL",
                "is not the canonical decimal form of section 6.4: " + Esj.ForMessage(Content));
        }

        return BigDecimal.Parse(Content);
    }

    /// <summary>Returns the content as a calendar date.</summary>
    /// <returns>the date</returns>
    /// <exception cref="EsjFormatException">if the content is not a date of section 6.5</exception>
    public DateOnly AsDate()
    {
        if (!Grammars.TryReadDate(Content, out DateOnly date))
        {
            throw new EsjFormatException("ESJ-L2-DATE",
                "is not a date of section 6.5: " + Esj.ForMessage(Content));
        }

        return date;
    }

    /// <summary>Checks the content against the time grammar of section 6.5.</summary>
    /// <returns>the content</returns>
    /// <exception cref="EsjFormatException">if the content is not a time of section 6.5</exception>
    public string AsTime()
    {
        if (!Grammars.IsTime(Content))
        {
            throw new EsjFormatException("ESJ-L2-TIME",
                "is not a time of section 6.5: " + Esj.ForMessage(Content));
        }

        return Content;
    }

    /// <summary>Returns the decoded bytes of an attachment.</summary>
    /// <returns>the bytes</returns>
    /// <exception cref="EsjFormatException">if the content is not canonical base64</exception>
    public byte[] AsBytes()
    {
        if (!Grammars.IsCanonicalBase64(Content))
        {
            throw new EsjFormatException("ESJ-L2-BASE64",
                "is not the canonical base64 of section 6.7: " + Esj.ForMessage(Content));
        }

        return Convert.FromBase64String(Content);
    }

    /// <inheritdoc />
    public override string ToString() => Content;

    private static string? Normalized(string? component) =>
        component is null ? null : Texts.NormalizeLineEndings(component);
}
