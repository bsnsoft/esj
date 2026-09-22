package de.bsnsoft.esj.json;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * The canonical bytes of a document and the two digests taken over them (specification,
 * sections 7 and 8).
 *
 * <p>The canonical form gives one document content exactly one byte representation, so two
 * documents with the same content have the same digests whichever implementation wrote
 * them, in whatever order and with whatever whitespace. Nothing here needs the term
 * registry: the member order of {@code values} follows from the paths alone
 * (specification, section 7.4), which is what makes the canonical form reproducible where
 * no registry is loaded.
 *
 * <p>The <strong>semantic digest</strong> covers the two-member object of the
 * specification, section 8.2 — the edition and the values, in that order — serialized as a
 * standalone JSON object. It identifies the semantic content of the invoice and does not
 * move when the format version, the provenance, an extension or the layout changes; it
 * does move when the edition changes, because a path is an address relative to an edition.
 * The <strong>document digest</strong> covers the whole canonical document and identifies
 * its content together with everything the envelope says about it.
 *
 * <p>Neither digest identifies a <em>file</em>: two files that carry the same content in
 * two layouts have the same two digests, which is what the canonical form is for. Neither
 * is a signature either: anyone who can change the document can change the digest.
 *
 * <p><strong>The document has to be one that was read whole.</strong> A digest is a claim
 * about content (specification, section 8.1), and these three entry points take a
 * document object and cannot see where it came from. A document a reader handed back
 * after layer L1 found an error is missing the members the reader had to leave out
 * ({@link ReadResult}), so its canonical bytes and its digests are those of content the
 * sender never sent, and nothing downstream can tell. A caller that read with
 * {@link EsjReader#readWithFindings(byte[])} therefore asks {@link ReadResult#isWellFormed()}
 * before it canonicalizes or digests; a caller that read with {@link EsjReader#read(byte[])}
 * has that answer already, because that reader hands back no document unless layer L1
 * passed.
 */
public final class Canonicalizer {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Canonicalizer() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the canonical bytes of a document (specification, section 7).
     *
     * <p>The document must be one that passed layer L1, for the reason the class
     * documentation gives.
     *
     * @param document the document, read whole
     * @return the canonical serialization: UTF-8, no byte order mark, no insignificant
     *         whitespace, no trailing newline
     * @throws EsjFormatException   if a string of the document carries an unpaired
     *                              surrogate, or a number inside {@code extensions} has no
     *                              canonical decimal form of at most 64 characters
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static byte[] canonicalBytes(SemanticDocument document) {
        return EsjWriter.canonical().toBytes(document);
    }

    /**
     * Returns the canonical serialization of the semantic identity of a document: the
     * standalone object {@code {"semanticModel": …, "values": …}}, with the values in
     * canonical path order. This is the byte sequence the semantic digest is taken over
     * (specification, section 8.2).
     *
     * <p>The document must be one that passed layer L1, for the reason the class
     * documentation gives.
     *
     * @param document the document, read whole
     * @return the canonical bytes of the semantic identity object
     * @throws EsjFormatException   if a string of the document carries an unpaired
     *                              surrogate
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static byte[] canonicalSemanticBytes(SemanticDocument document) {
        return EsjWriter.canonical().semanticToBytes(document);
    }

    /**
     * Returns the semantic digest of a document: SHA-256 over the canonical serialization
     * of the edition and the values (specification, section 8.2).
     *
     * <p>The document must be one that passed layer L1: a digest of a document the reader
     * could not read whole identifies content that was never sent, for the reason the
     * class documentation gives.
     *
     * @param document the document, read whole
     * @return 64 lowercase hexadecimal digits
     * @throws EsjFormatException   if a string of the document carries an unpaired
     *                              surrogate
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static String semanticDigest(SemanticDocument document) {
        return sha256(canonicalSemanticBytes(document));
    }

    /**
     * Returns the document digest: SHA-256 over the whole canonical document
     * (specification, section 8.3).
     *
     * <p>The document must be one that passed layer L1: a digest of a document the reader
     * could not read whole identifies content that was never sent, for the reason the
     * class documentation gives.
     *
     * @param document the document, read whole
     * @return 64 lowercase hexadecimal digits
     * @throws EsjFormatException   if a string of the document carries an unpaired
     *                              surrogate, or a number inside {@code extensions} has no
     *                              canonical decimal form of at most 64 characters
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static String documentDigest(SemanticDocument document) {
        return sha256(canonicalBytes(document));
    }

    /**
     * Reads a document in any layout and writes it back in canonical form, with the limits
     * of the specification, section 12.2.
     *
     * @param document the document, in any layout
     * @return the canonical bytes
     * @throws EsjFormatException   if the byte sequence fails validation layer L1
     * @throws de.bsnsoft.esj.EsjLimitException if a limit is exceeded
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static byte[] canonicalize(byte[] document) {
        return canonicalize(document, Limits.defaults());
    }

    /**
     * Reads a document in any layout and writes it back in canonical form.
     *
     * @param document the document, in any layout
     * @param limits   the resource bounds the reader enforces
     * @return the canonical bytes
     * @throws EsjFormatException   if the byte sequence fails validation layer L1
     * @throws de.bsnsoft.esj.EsjLimitException if a limit is exceeded
     * @throws NullPointerException if an argument is {@code null}
     */
    public static byte[] canonicalize(byte[] document, Limits limits) {
        return canonicalBytes(EsjReader.withLimits(limits).read(document));
    }

    /**
     * Reads a document from a stream and writes its canonical form to another. Neither
     * stream is closed.
     *
     * @param in     the document, in any layout
     * @param out    the stream the canonical bytes are written to
     * @param limits the resource bounds the reader enforces
     * @throws EsjFormatException   if the byte sequence fails validation layer L1
     * @throws de.bsnsoft.esj.EsjLimitException if a limit is exceeded
     * @throws UncheckedIOException if a stream fails
     * @throws NullPointerException if an argument is {@code null}
     */
    public static void canonicalize(InputStream in, OutputStream out, Limits limits) {
        Objects.requireNonNull(out, "out");
        byte[] canonical = canonicalBytes(EsjReader.withLimits(limits).read(in));
        try {
            out.write(canonical);
        } catch (IOException e) {
            throw new UncheckedIOException("writing the canonical form failed", e);
        }
    }

    private static String sha256(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
        byte[] hash = digest.digest(bytes);
        StringBuilder text = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            text.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return text.toString();
    }
}
