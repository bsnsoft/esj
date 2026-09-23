package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.SemanticDocument;
import java.util.Objects;

/**
 * Writes a semantic document as a UN/CEFACT Cross Industry Invoice D16B.
 *
 * <p>The document is built from {@code model/bindings/cii.json} and the element order of
 * the schema modules of the validation pack; {@link SyntaxWriter} is the engine and says
 * how. What did not reach the syntax is named in a {@link WriteReport} rather than dropped
 * quietly, and {@code conformance/writers/cii-roundtrip.md} measures the writer over the
 * conformance corpus and over {@code examples/}.
 *
 * <p>The output is UTF-8 and deterministic: two runs over the same document produce the
 * same bytes.
 */
public final class CiiWriter {

    private CiiWriter() {
        throw new AssertionError("no instances");
    }

    /**
     * Writes a document with the default options.
     *
     * <p>The report of what did not reach the syntax is discarded; a caller who has to
     * know whether a value was left behind uses {@link #writeWithReport}.
     *
     * @param document the semantic document
     * @return the cross industry invoice, encoded in UTF-8
     * @throws BindingEditionException if the document names an edition of the semantic
     *                               model other than the one the CII binding table was
     *                               written against
     * @throws BindingLimitException if the document is larger than
     *                               {@link WriterOptions#DEFAULT_MAX_OUTPUT_BYTES}
     * @throws NullPointerException  if {@code document} is {@code null}
     */
    public static byte[] write(SemanticDocument document) {
        return write(document, WriterOptions.defaults());
    }

    /**
     * Writes a document.
     *
     * <p>The report of what did not reach the syntax is discarded; a caller who has to
     * know whether a value was left behind uses {@link #writeWithReport}.
     *
     * @param document the semantic document
     * @param options  what this run may do and how it shapes its output
     * @return the cross industry invoice, encoded in UTF-8
     * @throws BindingEditionException if the document names an edition of the semantic
     *                               model other than the one the CII binding table was
     *                               written against
     * @throws BindingLimitException if the document is larger than
     *                               {@link WriterOptions#maxOutputBytes()}
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static byte[] write(SemanticDocument document, WriterOptions options) {
        return writeWithReport(document, options).xml();
    }

    /**
     * Writes a document and reports what did not reach the syntax.
     *
     * @param document the semantic document
     * @param options  what this run may do and how it shapes its output
     * @return the document and the report
     * @throws BindingEditionException if the document names an edition of the semantic
     *                               model other than the one the CII binding table was
     *                               written against
     * @throws BindingLimitException if the document is larger than
     *                               {@link WriterOptions#maxOutputBytes()}
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static WriteResult writeWithReport(SemanticDocument document,
                                              WriterOptions options) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(options, "options");
        return SyntaxWriter.write(BindingSyntax.CII, document, options);
    }
}
