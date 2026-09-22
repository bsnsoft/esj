using System;
using System.Globalization;
using System.Security.Cryptography;
using System.Text;

namespace En16931.SemanticJson.Json;

/// <summary>
/// The canonical form of a document and the two digests taken over it (specification,
/// sections 7 and 8).
/// </summary>
/// <remarks>
/// A canonicalizer needs no registry and therefore cannot tell a conformant document from a
/// merely well-formed one; it orders members, escapes strings, normalizes line endings and
/// removes insignificant whitespace, and it rewrites no content. A document whose decimals
/// are spelled some other way is refused at layer L2 by whoever holds the registry, and is
/// never repaired on the way through.
/// </remarks>
public static class Canonicalizer
{
    /// <summary>Returns the canonical bytes of a document.</summary>
    /// <param name="document">the document</param>
    /// <returns>the bytes of section 7</returns>
    public static byte[] CanonicalBytes(SemanticDocument document) =>
        EsjWriter.Canonical().ToBytes(document);

    /// <summary>
    /// Returns the canonical bytes of the object the semantic digest is taken over
    /// (specification, section 8.2).
    /// </summary>
    /// <param name="document">the document</param>
    /// <returns>the bytes</returns>
    public static byte[] CanonicalSemanticBytes(SemanticDocument document) =>
        EsjWriter.Canonical().SemanticToBytes(document);

    /// <summary>Returns the semantic digest of a document.</summary>
    /// <param name="document">the document</param>
    /// <returns>SHA-256 over the bytes of section 8.2, in lowercase hexadecimal</returns>
    public static string SemanticDigest(SemanticDocument document) =>
        Sha256(CanonicalSemanticBytes(document));

    /// <summary>Returns the document digest of a document.</summary>
    /// <param name="document">the document</param>
    /// <returns>SHA-256 over the canonical bytes, in lowercase hexadecimal</returns>
    public static string DocumentDigest(SemanticDocument document) =>
        Sha256(CanonicalBytes(document));

    /// <summary>Reads a document and writes it back in canonical form.</summary>
    /// <param name="document">the bytes of a document</param>
    /// <param name="limits">the bounds to read it under, or <c>null</c> for the defaults</param>
    /// <returns>the canonical bytes</returns>
    public static byte[] Canonicalize(byte[] document, Limits? limits = null) =>
        CanonicalBytes(EsjReader.WithLimits(limits ?? Limits.Defaults).Read(document));

    /// <summary>Returns the SHA-256 of a byte sequence in lowercase hexadecimal.</summary>
    /// <param name="bytes">the bytes</param>
    /// <returns>64 hexadecimal digits</returns>
    public static string Sha256(byte[] bytes)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        byte[] hash = SHA256.HashData(bytes);
        StringBuilder text = new(hash.Length * 2);
        foreach (byte value in hash)
        {
            text.Append(value.ToString("x2", CultureInfo.InvariantCulture));
        }

        return text.ToString();
    }
}
