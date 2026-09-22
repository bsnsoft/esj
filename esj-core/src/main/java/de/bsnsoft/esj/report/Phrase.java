package de.bsnsoft.esj.report;

/**
 * A sentence of a report that this project writes itself.
 *
 * <p>A report carries two kinds of text side by side. One kind came out of the run and
 * belongs to somebody else — the message of a Schematron rule, the name of a pack, the
 * identifier of a business term, the label a pack gives one of its components — and is
 * printed exactly as it stands, because rewording another publisher's rule would make the
 * report say something the rule does not. The other kind is this project's own: the label
 * of a check row it invented, the reason a layer did not run, the sentence under the
 * verdict. Only the second kind may be translated, and this enum is the list of it.
 *
 * <p>The words themselves are not here. A run says <em>which</em> sentence it means and
 * with which arguments, and the renderer of the report holds the wording in every language
 * it writes; that way a run has no language and a report has one.
 *
 * <p>An argument is text of the run — a pack, a profile, a count — and is never
 * translated.
 */
public enum Phrase {

    /** The row of the XML parser. No argument. */
    ROW_XML,

    /** The row of what stands between the bytes and any document at all. No argument. */
    ROW_XML_BYTES,

    /** The row a syntax engine that did not run leaves behind. No argument. */
    ROW_PACK,

    /**
     * The row of the official artefacts over the XML a document that never was XML was
     * written to. One argument: the syntax it was written in.
     */
    ROW_WRITTEN_SYNTAX,

    /**
     * The row of the terms a registry declares to have no binding in any transport syntax,
     * beside the row of the artefacts that judged the document written without them. One
     * argument: the registry that declared it, as it names itself.
     */
    ROW_TERMS_WITHOUT_TRANSPORT,

    /** The row of the object structure of a PDF. No argument. */
    ROW_PDF_STRUCTURE,

    /** The row of the PDF/A conformance a file declares. No argument. */
    ROW_PDFA,

    /** The row of the associated files array of a PDF. No argument. */
    ROW_PDF_AF,

    /** The row of the Factur-X XMP packet. No argument. */
    ROW_PDF_XMP,

    /** The row of the parameters of an embedded file. No argument. */
    ROW_PDF_EMBEDDED,

    /**
     * The row of the ESJ document a container carries beside the invoice. No argument.
     */
    ROW_PDF_ESJ,

    /** The row of the import, where one built the document. No argument. */
    ROW_IMPORT,

    /**
     * The name a container gives the ESJ document beside its invoice. One argument: the
     * name, as the container spells it.
     */
    ESJ_ATTACHMENT,

    /**
     * The same, where the enclosure carried paths the comparison had nothing to measure
     * them against. Two arguments: the name, and how many such paths there are.
     */
    ESJ_ATTACHMENT_PARTLY,

    /**
     * The syntax of an invoice that was taken out of a container. One argument: the
     * syntax, in the words the run uses for it.
     */
    SYNTAX_FROM_ATTACHMENT,

    /**
     * The streaming reader of the binding tables. One argument: the token it is named by
     * on the command line.
     */
    READER_STREAMING,

    /**
     * The XSLT path of the vendored visualization stylesheets. One argument: the token it
     * is named by on the command line.
     */
    READER_XSLT,

    /** A component of a pack that validates another syntax than this document. No argument. */
    SKIPPED_OTHER_SYNTAX,

    /** A component of a pack that validates another profile than this document. No argument. */
    SKIPPED_OTHER_PROFILE,

    /** A rule set left out because the document failed the schema of its syntax. No argument. */
    SKIPPED_SCHEMA_INVALID,

    /** The row of the format layer of the specification. No argument. */
    ROW_FORMAT_L1,

    /** The row of the model layer of the specification. No argument. */
    ROW_MODEL_L2,

    /** The row of the cardinality layer of the specification. No argument. */
    ROW_CARDINALITY_L3,

    /** The row of the native rule engine. One argument: the pack, with its version. */
    ROW_RULES,

    /** One finding of a row that weighs nothing for the verdict. One argument: {@code 1}. */
    NOTE,

    /** Several of them. One argument: how many. */
    NOTES,

    /**
     * What a file declares about its PDF/A conformance, always said to be a declaration.
     * One argument: the conformance as the file writes it.
     */
    PDFA_DECLARED,

    /** A file that declares no PDF/A conformance at all. No argument. */
    PDFA_NONE,

    /**
     * What a file declares about its PDF/A conformance, and what a validator the caller
     * lent said about that claim. Two arguments: the conformance as the file writes it,
     * and the answer of the validator as it gave it.
     */
    PDFA_DECLARED_VALIDATED,

    /**
     * What a validator said about a file that declares no PDF/A conformance at all. One
     * argument: the answer of the validator as it gave it.
     */
    PDFA_NONE_VALIDATED,

    /**
     * What a container says the invoice attachment is to the document. One argument: the
     * relationship, quoted.
     */
    AF_RELATIONSHIP,

    /** A check that did not run and has nothing further to say. No argument. */
    REASON_NOT_RUN,

    /** A layer that is about an ESJ document, over an input that arrived as XML. No argument. */
    REASON_INPUT_XML,

    /** A container profile that carries no invoice line at all. No argument. */
    REASON_NO_INVOICE_LINE,

    /** A layer that had no document to measure. No argument. */
    REASON_NO_DOCUMENT,

    /** A layer the format layer stopped. No argument. */
    REASON_FORMAT_REJECTED,

    /** A layer the model layer stopped. No argument. */
    REASON_MODEL_REJECTED,

    /** A layer whose predecessor measured nothing. No argument. */
    REASON_MODEL_MEASURED_NOTHING,

    /** A layer that ran over less than the document. No argument. */
    REASON_MEASURED_LESS,

    /** An input that was never XML, so no binding was checked. No argument. */
    REASON_NO_XML,

    /**
     * What became of the terms of such a registry: they stayed where they were written.
     * One argument: their identifiers, as the run lists them.
     */
    DETAIL_ONLY_IN_ESJ,

    /** The verdict line of a run a bound of its own stopped. No argument. */
    DETAIL_LIMIT,

    /** What the complete check was missing. One argument: the components, as the run names them. */
    DETAIL_MISSING,

    /** A container whose profile is not an EN 16931 invoice. One argument: the profile. */
    DETAIL_NOT_EN16931,

    /** A sound invoice inside a container that is wrong about it. No argument. */
    DETAIL_CONTAINER_WRONG,

    /** Which profile a container names, beside a verdict about it. One argument: the profile. */
    SUBJECT_PROFILE,

    /** A subject no verdict was reached about, because a bound was met. No argument. */
    SUBJECT_LIMIT,

    /**
     * Where an invoice was taken from. Two arguments: the position of the attachment and
     * its name.
     */
    PROVENANCE_ATTACHMENT,

    /** Bytes that were not written in the encoding they declare. One argument: what was done. */
    PROVENANCE_RECODED,

    /** How many observations of the same kind were left out. One argument: how many. */
    MORE_NOTES,

    /**
     * One observation that was made more than once, where identical ones are collapsed.
     * Two arguments: the observation, and how many times it was made.
     */
    TIMES,

    /**
     * What is left of a text that is longer than a report prints. One argument: how many
     * characters are not shown.
     */
    CUT,

    /** How many findings of a block were left out. One argument: how many. */
    MORE_FINDINGS,

    /**
     * The same rules, reported by an official artefact as well. One argument: the
     * identifiers, joined.
     */
    ALSO_REPORTED,

    /**
     * One value of the document that did not reach the rendering of the invoice below.
     * One argument: {@code 1}.
     */
    EXPORT_NOTE,

    /**
     * Values of the document that did not reach the rendering of the invoice below. One
     * argument: how many.
     */
    EXPORT_NOTES,

    /**
     * An invoice too large for this form of the report. One argument: how large the
     * rendering is, in characters.
     */
    INVOICE_TOO_LARGE,

    /**
     * A document larger than this form of the report renders, so that no rendering of it
     * was made at all. One argument: how many values the document carries.
     */
    INVOICE_TOO_LARGE_DOCUMENT
}
