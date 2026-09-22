/**
 * The JSON layer: the reader, the writer and the canonicalizer of the EN16931 Semantic
 * JSON format, together with the reader limits of the specification, section 12.2.
 *
 * <p>{@link de.bsnsoft.esj.json.EsjReader} turns bytes into a
 * {@link de.bsnsoft.esj.SemanticDocument} and enforces validation layer L1 on
 * the way; {@link de.bsnsoft.esj.json.EsjWriter} turns a document back into
 * bytes, in canonical or in pretty form;
 * {@link de.bsnsoft.esj.json.Canonicalizer} produces the canonical bytes and
 * the two digests of the specification, section 8.
 *
 * <p>Nothing here needs the term registry. Layers L2 and L3 are the business of
 * {@code de.bsnsoft.esj.validate.StructuralValidator}, which a caller runs on
 * the document the reader returned.
 */
package de.bsnsoft.esj.json;
