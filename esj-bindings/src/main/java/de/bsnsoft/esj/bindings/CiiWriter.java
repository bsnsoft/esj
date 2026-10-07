package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.xml.InvoiceSyntax;
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
     * Returns the edition of the semantic model this writer writes, in the spelling a
     * document uses in its {@code semanticModel} member ({@code SPEC.md}, section 10).
     *
     * @return the edition, for example {@code EN16931-1:2017+A1:2019/AC:2020}
     */
    public static String semanticModel() {
        return BindingTable.of(InvoiceSyntax.CII).semanticModel();
    }

    /**
     * Tells whether this writer writes a document that names an edition of the semantic
     * model.
     *
     * <p>A path is an address relative to an edition, so a document of another edition is
     * refused with a {@link BindingEditionException} rather than written short of the terms
     * the binding table does not know. A caller asks here before it writes, to say so in
     * its own words.
     *
     * @param semanticModel the {@code semanticModel} member of a document
     * @return {@code true} if the CII binding table was written against that edition
     * @throws NullPointerException if {@code semanticModel} is {@code null}
     */
    public static boolean supports(String semanticModel) {
        return BindingTable.of(InvoiceSyntax.CII).describes(semanticModel);
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
     * @throws EsjLimitException     if the document is larger than
     *                               {@link WriterOptions#maxOutputBytes()}
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
     * @throws EsjLimitException     if the document is larger than
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
     * @throws EsjLimitException     if the document is larger than
     *                               {@link WriterOptions#maxOutputBytes()}
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static WriteResult writeWithReport(SemanticDocument document,
                                              WriterOptions options) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(options, "options");
        return SyntaxWriter.write(InvoiceSyntax.CII, document, options);
    }
}
