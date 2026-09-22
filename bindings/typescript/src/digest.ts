import type { SemanticDocument } from './document.ts';
import { canonicalSemanticContent, canonicalize } from './canonical.ts';

/**
 * The two digests of the specification, section 8.
 *
 * Both are SHA-256 over canonical bytes and both identify content rather than a file: two
 * files that carry the same content in two layouts have the same two digests. They are
 * computed with the Web Crypto API, which every runtime this package targets carries, so
 * nothing here implements a hash of its own and the functions are asynchronous.
 */

const UTF8 = new TextEncoder();

async function sha256(text: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', UTF8.encode(text));
  let out = '';
  for (const byte of new Uint8Array(digest)) {
    out += byte.toString(16).padStart(2, '0');
  }
  return out;
}

/**
 * Returns the semantic digest: SHA-256 over the canonical serialization of the edition and
 * the values (section 8.2).
 *
 * It answers *did the invoice change*. It does not move when the format version, the
 * `source` member or the `extensions` subtree changes, and it does move when an extension
 * term does, because such a term is content of the invoice and lives in `values`.
 *
 * @param document the document
 * @return the digest, as 64 lowercase hexadecimal digits
 */
export function semanticDigest(document: SemanticDocument): Promise<string> {
  return sha256(canonicalSemanticContent(document));
}

/**
 * Returns the document digest: SHA-256 over the whole canonical document (section 8.3).
 *
 * It answers *did anything around the invoice change* — the format version, the
 * `extensions` member, the recorded provenance — and identifies canonical content, never a
 * file.
 *
 * @param document the document
 * @return the digest, as 64 lowercase hexadecimal digits
 */
export function documentDigest(document: SemanticDocument): Promise<string> {
  return sha256(canonicalize(document));
}

/**
 * Returns the SHA-256 of a byte sequence, which is what `source.sha256` records of the
 * document an ESJ document was derived from (section 4.7).
 *
 * @param bytes the bytes
 * @return the digest, as 64 lowercase hexadecimal digits
 */
export async function digestOfBytes(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  let out = '';
  for (const byte of new Uint8Array(digest)) {
    out += byte.toString(16).padStart(2, '0');
  }
  return out;
}
