package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Writes a semantic document as an OASIS UBL 2.1 Invoice or Credit Note.
 *
 * <p>The document is built from {@code model/bindings/ubl-invoice.json} or
 * {@code model/bindings/ubl-creditnote.json} and the element order of the UBL 2.1 schema
 * modules of the validation pack; {@link SyntaxWriter} is the engine and says how. What
 * did not reach the syntax is named in a {@link WriteReport} rather than dropped quietly,
 * and {@code conformance/writers/ubl-roundtrip.md} measures the writer over the
 * conformance corpus and over {@code examples/}.
 *
 * <p>UBL writes an invoice and a credit note as two different documents where the semantic
 * model has one, and which of the two a semantic document is is a fact of BT-3, the
 * invoice type code. {@link WriterOptions#document()} decides: {@link DocumentType#INVOICE}
 * and {@link DocumentType#CREDIT_NOTE} name the document to write, and
 * {@link DocumentType#AUTO}, the default, reads BT-3. The choice is in the report, as
 * {@link WriteReport#syntax()}.
 *
 * <p>The wrappers a UBL document carries above its business content are business terms and
 * are written as such: the specification identifier BT-24 is {@code cbc:CustomizationID} and
 * the business process type BT-23 is {@code cbc:ProfileID}. {@code cbc:UBLVersionID} is not a
 * business term, the schema of UBL 2.1 leaves it optional and no artefact of the validation
 * pack asks for it, so the writer states no version of a syntax the document does not state.
 *
 * <p>The output is UTF-8 and deterministic: two runs over the same document produce the
 * same bytes.
 */
public final class UblWriter {

    /** Where the invoice type code stands in a semantic document. */
    private static final SemanticPath TYPE_CODE = SemanticPath.of("/BT-3");

    /**
     * The document name codes that make a UBL document a credit note.
     *
     * <p>UBL 2.1 has two document elements for what EN 16931-1 calls an invoice, and the
     * standard's validation artefacts say which code belongs on which: the artefact of
     * {@code packs/} admits one set of UNTDID 1001 codes on {@code cbc:InvoiceTypeCode} and
     * another on {@code cbc:CreditNoteTypeCode}. These are the codes it admits on a credit
     * note and not on an invoice; 81, which it admits on both, is therefore written as an
     * invoice. {@code UblWriterTest} reads both sets out of that artefact and holds this
     * list to their difference, so a later release of the artefact shows up as a failing
     * test rather than as a document of the wrong kind.
     */
    private static final Set<String> CREDIT_NOTE_CODES = Set.of(
            "83", "261", "262", "296", "308", "381", "396", "420", "458", "502", "503",
            "532");

    private UblWriter() {
        throw new AssertionError("no instances");
    }

    /**
     * Writes a document with the default options, which choose the document type from
     * BT-3.
     *
     * <p>The report of what did not reach the syntax is discarded; a caller who has to
     * know whether a value was left behind uses {@link #writeWithReport}.
     *
     * @param document the semantic document
     * @return the UBL document, encoded in UTF-8
     * @throws BindingEditionException if the document names an edition of the semantic
     *                               model other than the one the UBL binding table was
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
     * @param options  which document to write, what this run may do and how it shapes its
     *                 output
     * @return the UBL document, encoded in UTF-8
     * @throws BindingEditionException if the document names an edition of the semantic
     *                               model other than the one the UBL binding table was
     *                               written against
     * @throws BindingLimitException if the document is larger than
     *                               {@link WriterOptions#maxOutputBytes()}
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static byte[] write(SemanticDocument document, WriterOptions options) {
        return writeWithReport(document, options).xml();
    }

    /**
     * Writes a document and reports which of the two UBL documents it wrote and what did
     * not reach the syntax.
     *
     * @param document the semantic document
     * @param options  which document to write, what this run may do and how it shapes its
     *                 output
     * @return the document and the report
     * @throws BindingEditionException if the document names an edition of the semantic
     *                               model other than the one the UBL binding table was
     *                               written against
     * @throws BindingLimitException if the document is larger than
     *                               {@link WriterOptions#maxOutputBytes()}
     * @throws NullPointerException  if an argument is {@code null}
     */
    public static WriteResult writeWithReport(SemanticDocument document,
                                              WriterOptions options) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(options, "options");
        return SyntaxWriter.write(syntaxOf(document, options.document()), document, options);
    }

    /**
     * Returns which of the two UBL documents a semantic document is written as.
     *
     * @param document the semantic document
     * @param choice   what the caller asked for
     * @return the syntax and document type
     * @throws NullPointerException if an argument is {@code null}
     */
    public static BindingSyntax syntaxOf(SemanticDocument document, DocumentType choice) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(choice, "choice");
        return switch (choice) {
            case INVOICE -> BindingSyntax.UBL_INVOICE;
            case CREDIT_NOTE -> BindingSyntax.UBL_CREDIT_NOTE;
            case AUTO -> document.value(TYPE_CODE)
                    .filter(code -> CREDIT_NOTE_CODES.contains(code.canonicalContent()))
                    .map(code -> BindingSyntax.UBL_CREDIT_NOTE)
                    .orElse(BindingSyntax.UBL_INVOICE);
        };
    }

    /**
     * Returns the document name codes that make {@link DocumentType#AUTO} write a credit
     * note, in the order the code list writes them.
     *
     * @return the codes
     */
    public static List<String> creditNoteCodes() {
        return CREDIT_NOTE_CODES.stream().sorted(UblWriter::byNumber).toList();
    }

    /** Orders two document name codes by the number they are. */
    private static int byNumber(String left, String right) {
        return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
    }

    /** Which of the two UBL documents to write. */
    public enum DocumentType {

        /** A UBL 2.1 Invoice, whatever BT-3 says. */
        INVOICE,

        /** A UBL 2.1 Credit Note, whatever BT-3 says. */
        CREDIT_NOTE,

        /**
         * The one the invoice type code names: a credit note where BT-3 is a code the
         * standard's validation artefacts admit on a credit note and not on an invoice, an
         * invoice otherwise. A document with no BT-3 is an invoice.
         */
        AUTO
    }
}
