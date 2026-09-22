package de.bsnsoft.esj.render;

import de.bsnsoft.esj.report.Phrase;
import java.util.List;
import java.util.Locale;

/**
 * The words of a validation report that are this module's and not the run's.
 *
 * <p>Both forms of the report write the same words, so they are written once here rather
 * than twice in two layouts. What is <em>not</em> here is anything a run produced: a
 * verdict, a rule identifier, a message of an artefact, a pack name and the label a pack
 * gives one of its components travel in the
 * {@link de.bsnsoft.esj.report.ValidationOutcome} as
 * {@link de.bsnsoft.esj.report.Text.Words} and are printed as they stand. A
 * report that translated a finding of a rule set would be rewording somebody else's rule,
 * and a report that translated the verdict would give a pipeline a second spelling of a
 * word it branches on.
 *
 * <p>What a run writes in this project's own words — the label of a check row it invented,
 * the reason a layer did not run, the line under the verdict — is a
 * {@link de.bsnsoft.esj.report.Text.Phrased} text, and {@link #of(Phrase)}
 * is where each of those sentences is written in every language this module knows. A
 * report half in one language and half in another is the thing that split keeps out.
 *
 * <p>{@link Word} is the neighbouring enum and belongs to the invoice layout; a heading of
 * the report is not a heading of an invoice, so the two lists stay apart.
 */
enum ReportWord {

    /** The title of the whole page. */
    TITLE("Validation report", "Prüfbericht"),

    /** The label of the verdict. */
    VERDICT("Verdict", "Urteil"),

    /** The heading over what was judged. */
    IDENTITY("Identity", "Identität"),

    /** The input as the caller named it. */
    INPUT("Input", "Eingabe"),

    /** The syntax the bytes turned out to be. */
    SYNTAX("Detected syntax", "Erkannte Syntax"),

    /** The reader that built the semantic document the digests are of. */
    READER("Reader", "Leser"),

    /** The edition of the semantic model the document names, the paths being relative to it. */
    SEMANTIC_MODEL("Semantic model", "Semantisches Modell"),

    /** The specification the document names, or the conformance level of a container. */
    PROFILE("Profile", "Profil"),

    /** The digest of the bytes that were handed over. */
    INPUT_DIGEST("SHA-256 of the input", "SHA-256 der Eingabe"),

    /** The semantic digest of the document (specification, section 8.2). */
    SEMANTIC_DIGEST("Semantic digest", "Semantischer Digest"),

    /** The document digest (specification, section 8.3). */
    DOCUMENT_DIGEST("Document digest", "Dokument-Digest"),

    /** The digest the document records for the source it was imported from. */
    SOURCE_DIGEST("SHA-256 of the source", "SHA-256 der Quelle"),

    /** The rule material that was executed. */
    PACKS("Packs", "Pakete"),

    /** What produced the report. */
    TOOL("Tool", "Werkzeug"),

    /** The moment the caller passed, printed as given. */
    TIME("Time", "Zeitpunkt"),

    /** The verdict about the file an invoice was carried in. */
    SUBJECT_CONTAINER("Container", "Container"),

    /** The verdict about the invoice inside it. */
    SUBJECT_INVOICE("Invoice", "Rechnung"),

    /** The heading over the check table. */
    CHECKS("Checks", "Prüfungen"),

    /** The block of the file an invoice was carried in. */
    BLOCK_CONTAINER("Container", "Container"),

    /** The block of the official artefacts of the profile. */
    BLOCK_SYNTAX("Syntax", "Syntax"),

    /** The block of the structural layers and the business rules. */
    BLOCK_SEMANTIC("Semantic", "Semantik"),

    /** A row that ran and found nothing. */
    STATUS_OK("OK", "OK"),

    /** A row that did not run. */
    STATUS_SKIPPED("skipped", "übersprungen"),

    /** A row that had nothing to run on. */
    STATUS_NOT_APPLICABLE("not applicable", "nicht anwendbar"),

    /** A row that ran over less than it was asked about. */
    STATUS_NO_VERDICT("no verdict", "kein Urteil"),

    /** A row that checked nothing and reports what the document declares. */
    STATUS_DECLARED("declared", "deklariert"),

    /** One finding that decides the verdict. */
    ERROR("error", "Fehler"),

    /** Several of them. */
    ERRORS("errors", "Fehler"),

    /** One finding that does not decide the verdict. */
    WARNING("warning", "Warnung"),

    /** Several of them. */
    WARNINGS("warnings", "Warnungen"),

    /** What the line of a re-levelled finding says about the level the artefact wrote. */
    FLAGGED("flagged %1$s by the artefact", "vom Artefakt als %1$s markiert"),

    /**
     * What the line of a native finding says where the specification of the document gave
     * the rule another level than the standard it comes from does.
     */
    LEVELLED("%1$s by the standard, levelled by the profile %2$s",
            "%1$s nach der Norm, vom Profil %2$s eingestuft"),

    /** The heading over the findings. */
    FINDINGS("Findings", "Befunde"),

    /** What stands where no engine found anything. */
    NOTHING_FOUND("Nothing was found.", "Nichts gefunden."),

    /** The heading over what was done to the bytes before they were read. */
    PROVENANCE("Before the check", "Vor der Prüfung"),

    /** The heading over the rendered invoice. */
    INVOICE("The invoice", "Die Rechnung"),

    /** What the line under that heading says about the frame the invoice stands in. */
    INVOICE_FRAME("Rendered from the document this report is about, in a frame of its own"
            + " so that the page below cannot reach this one. Printing this page cuts the"
            + " invoice off at the frame; the PDF form of the report carries it as pages.",
            "Aus dem Dokument erzeugt, um das es in diesem Bericht geht, in einem eigenen"
                    + " Rahmen, damit die Seite darin diese hier nicht erreichen kann. Beim"
                    + " Drucken dieser Seite wird die Rechnung am Rahmen abgeschnitten; die"
                    + " PDF-Form des Berichts trägt sie als Seiten."),

    /** What stands in place of an invoice that could not be rendered. */
    INVOICE_REFUSED("The invoice could not be rendered:",
            "Die Rechnung konnte nicht dargestellt werden:"),

    /** The footer of the PDF form: the page number of the page count. */
    PAGE_OF("Page %1$d of %2$d", "Seite %1$d von %2$d"),

    /** The row of the XML parser. */
    ROW_XML("XML", "XML"),

    /** The row of what stands between the bytes and any document at all. */
    ROW_XML_BYTES("XML bytes", "XML-Bytes"),

    /** The row a syntax engine that did not run leaves behind. */
    ROW_PACK("Validation pack", "Prüfpaket"),

    /** The row of the official artefacts over the document written as a syntax. */
    ROW_WRITTEN_SYNTAX("Official artefacts over the written document (%1$s)",
            "Amtliche Artefakte über dem geschriebenen Dokument (%1$s)"),

    /** The row of the terms a registry declares to have no transport binding. */
    ROW_TERMS_WITHOUT_TRANSPORT("Terms of %1$s without a transport binding",
            "Begriffe aus %1$s ohne Transportbindung"),

    /** The row of the object structure of a PDF. */
    ROW_PDF_STRUCTURE("PDF structure", "PDF-Struktur"),

    /** The row of the PDF/A conformance a file declares. */
    ROW_PDFA("PDF/A-3 declared", "PDF/A-3 deklariert"),

    /** The row of the associated files array of a PDF. */
    ROW_PDF_AF("Associated file (/AF)", "Zugeordnete Datei (/AF)"),

    /** The row of the Factur-X XMP packet. */
    ROW_PDF_XMP("Factur-X XMP metadata", "Factur-X-XMP-Metadaten"),

    /** The row of the parameters of an embedded file. */
    ROW_PDF_EMBEDDED("Embedded file params", "Parameter der eingebetteten Datei"),

    /** The row of the ESJ document a container carries beside the invoice. */
    ROW_PDF_ESJ("ESJ document attached", "ESJ-Dokument beigelegt"),

    /** The name a container gives the ESJ document beside its invoice. */
    ESJ_ATTACHMENT("%1$s, checked against the invoice",
            "%1$s, gegen die Rechnung geprüft"),

    /**
     * The same, where the enclosure carried paths of terms the invoice syntax binds
     * nothing of, so that the invoice had nothing to measure them against.
     */
    ESJ_ATTACHMENT_PARTLY("%1$s, checked against the invoice; %2$s paths were not checked",
            "%1$s, gegen die Rechnung geprüft; %2$s Pfade wurden nicht geprüft"),

    /** The row of the import that built the document. */
    ROW_IMPORT("Import", "Import"),

    /** The syntax of an invoice that was taken out of a container. */
    SYNTAX_FROM_ATTACHMENT("%1$s (from a PDF attachment)", "%1$s (aus einem PDF-Anhang)"),

    /** The streaming reader of the binding tables, named by the token of the option. */
    READER_STREAMING("%1$s — the streaming reader of the binding tables",
            "%1$s — der Streaming-Leser der Bindungstabellen"),

    /** The XSLT path of the vendored stylesheets, named by the token of the option. */
    READER_XSLT("%1$s — the XSLT path of the vendored visualization stylesheets",
            "%1$s — der XSLT-Weg der mitgelieferten Visualisierungs-Stylesheets"),

    /** A component of the pack that validates another syntax than this document. */
    SKIPPED_OTHER_SYNTAX("it validates no document of this syntax",
            "es prüft kein Dokument dieser Syntax"),

    /** A component of the pack that validates another profile than this document. */
    SKIPPED_OTHER_PROFILE("it validates no document of the profile this one names",
            "es prüft kein Dokument des Profils, das dieses nennt"),

    /** A rule set left out because the document failed the schema of its syntax. */
    SKIPPED_SCHEMA_INVALID("the document is not valid against the schema of its syntax, and"
            + " a rule set run over it would describe a document nobody sent",
            "das Dokument ist gegen das Schema seiner Syntax nicht gültig, und ein"
                    + " Regelsatz darüber würde ein Dokument beschreiben, das niemand"
                    + " gesendet hat"),

    /** The row of the format layer of the specification. */
    ROW_FORMAT_L1("ESJ format (L1)", "ESJ-Format (L1)"),

    /** The row of the model layer of the specification. */
    ROW_MODEL_L2("Model (L2)", "Modell (L2)"),

    /** The row of the cardinality layer of the specification. */
    ROW_CARDINALITY_L3("Cardinality (L3)", "Kardinalität (L3)"),

    /** The row of the native rule engine, with the pack that decided it. */
    ROW_RULES("EN 16931 business rules (native, pack %1$s)",
            "EN-16931-Geschäftsregeln (nativ, Paket %1$s)"),

    /** One finding of a row that weighs nothing for the verdict. */
    NOTE("%1$s note", "%1$s Hinweis"),

    /** Several of them. */
    NOTES("%1$s notes", "%1$s Hinweise"),

    /** What a file declares about its PDF/A conformance, said to be a declaration. */
    PDFA_DECLARED("%1$s — declared, not validated", "%1$s — deklariert, nicht validiert"),

    /** A file that declares no PDF/A conformance at all. */
    PDFA_NONE("none declared", "keine deklariert"),

    /**
     * What a file declares about its PDF/A conformance, with the answer of the validator
     * the caller lent. The answer is the validator's own sentence and is printed as it
     * stands, like every other thing a run reports.
     */
    PDFA_DECLARED_VALIDATED("%1$s — %2$s", "%1$s — %2$s"),

    /** The same where the file declares no conformance and a validator ran anyway. */
    PDFA_NONE_VALIDATED("none declared — %1$s", "keine deklariert — %1$s"),

    /** What a container says the invoice attachment is to the document. */
    AF_RELATIONSHIP("AFRelationship %1$s", "AFRelationship %1$s"),

    /** A check that did not run and has nothing further to say. */
    REASON_NOT_RUN("it did not run", "sie lief nicht"),

    /** A layer about an ESJ document, over an input that arrived as XML. */
    REASON_INPUT_XML("the input arrived as XML", "die Eingabe kam als XML"),

    /** A container profile that carries no invoice line at all. */
    REASON_NO_INVOICE_LINE("the profile carries no invoice line",
            "das Profil trägt keine Rechnungsposition"),

    /** A layer that had no document to measure. */
    REASON_NO_DOCUMENT("no document was built", "es wurde kein Dokument gebaut"),

    /** A layer the format layer stopped. */
    REASON_FORMAT_REJECTED("the format layer rejected the document",
            "die Formatschicht hat das Dokument abgelehnt"),

    /** A layer the model layer stopped. */
    REASON_MODEL_REJECTED("the model layer rejected the document",
            "die Modellschicht hat das Dokument abgelehnt"),

    /** A layer whose predecessor measured nothing. */
    REASON_MODEL_MEASURED_NOTHING("the model layer measured nothing",
            "die Modellschicht hat nichts gemessen"),

    /** A layer that ran over less than the document. */
    REASON_MEASURED_LESS("it measured less than the document",
            "sie hat weniger gemessen als das Dokument"),

    /** An input that was never XML, so no binding was checked. */
    REASON_NO_XML("no XML", "kein XML"),

    /** Where the terms of such a registry stayed. */
    DETAIL_ONLY_IN_ESJ("by design in the ESJ document only: %1$s",
            "planmäßig nur im ESJ-Dokument: %1$s"),

    /** The verdict line of a run a bound of its own stopped. */
    DETAIL_LIMIT("a limit of this run was reached before the document had been read",
            "eine Grenze dieses Laufs wurde erreicht, bevor das Dokument gelesen war"),

    /** What the complete check was missing. */
    DETAIL_MISSING("missing from the check: %1$s", "fehlt in der Prüfung: %1$s"),

    /** A container whose profile is not an EN 16931 invoice. */
    DETAIL_NOT_EN16931("profile %1$s: not an EN 16931 invoice",
            "Profil %1$s: keine EN-16931-Rechnung"),

    /** A sound invoice inside a container that is wrong about it. */
    DETAIL_CONTAINER_WRONG("the container is wrong about the invoice it carries",
            "der Container macht falsche Angaben über die Rechnung, die er trägt"),

    /** Which profile a container names, beside a verdict about it. */
    SUBJECT_PROFILE("profile %1$s", "Profil %1$s"),

    /** A subject no verdict was reached about, because a bound was met. */
    SUBJECT_LIMIT("a limit of this run was reached",
            "eine Grenze dieses Laufs wurde erreicht"),

    /** Where an invoice was taken from. */
    PROVENANCE_ATTACHMENT("the invoice was taken out of attachment %1$s of the file, %2$s",
            "die Rechnung stammt aus Anhang %1$s der Datei, %2$s"),

    /** Bytes that were not written in the encoding they declare. */
    PROVENANCE_RECODED("the bytes were recoded: %1$s",
            "die Bytes wurden umkodiert: %1$s"),

    /** How many observations of the same kind were left out. */
    MORE_NOTES("… and %1$s more", "… und %1$s weitere"),

    /** One observation that was made more than once. */
    TIMES("%1$s (%2$s times)", "%1$s (%2$s-mal)"),

    /** What is left of a text longer than a report prints. */
    CUT("… (%1$s characters not shown)", "… (%1$s Zeichen nicht angezeigt)"),

    /** How many findings of a block were left out, and where every one of them is. */
    MORE_FINDINGS("… and %1$s more findings of this block; the ones printed are the"
            + " heaviest of it, and --output json carries every one of them",
            "… und %1$s weitere Befunde dieses Blocks; gedruckt sind die schwersten, und"
                    + " --output json trägt sie alle"),

    /** The same rules, reported by an official artefact as well. */
    ALSO_REPORTED("The same rules were reported by an official artefact over the XML; the"
            + " two engines are independent and neither report is merged into the other:"
            + " %1$s",
            "Dieselben Regeln hat ein offizielles Artefakt über dem XML gemeldet; die"
                    + " beiden Engines sind unabhängig, und kein Bericht wird in den"
                    + " anderen eingerechnet: %1$s"),

    /** One value of the document that did not reach the rendering of the invoice. */
    EXPORT_NOTE("%1$s value of the document did not reach this rendering of the invoice;"
            + " the PDF form of this report carries the document as pages.",
            "%1$s Wert des Dokuments hat diese Darstellung der Rechnung nicht erreicht;"
                    + " die PDF-Form dieses Berichts trägt das Dokument als Seiten."),

    /** Several of them. */
    EXPORT_NOTES("%1$s values of the document did not reach this rendering of the"
            + " invoice; the PDF form of this report carries the document as pages.",
            "%1$s Werte des Dokuments haben diese Darstellung der Rechnung nicht"
                    + " erreicht; die PDF-Form dieses Berichts trägt das Dokument als"
                    + " Seiten."),

    /**
     * An invoice whose document is larger than this form of the report renders, measured
     * before anything was rendered.
     */
    INVOICE_TOO_LARGE_DOCUMENT("The invoice is not in this file: the document carries %1$s"
            + " values, and its rendering would be larger than this form of the report"
            + " carries. It was therefore not rendered. The PDF form of the report carries"
            + " the invoice as pages, and esj render writes it on its own.",
            "Die Rechnung steht nicht in dieser Datei: das Dokument trägt %1$s Werte, und"
                    + " ihre Darstellung wäre größer, als diese Form des Berichts trägt."
                    + " Sie wurde deshalb nicht erzeugt. Die PDF-Form des Berichts trägt"
                    + " die Rechnung als Seiten, und esj render schreibt sie für sich."),

    /** An invoice whose rendering is larger than this form of the report carries. */
    INVOICE_TOO_LARGE("The invoice is not in this file: its rendering is %1$s characters,"
            + " more than this form of the report carries. The PDF form of the report"
            + " carries it as pages, and esj render writes it on its own.",
            "Die Rechnung steht nicht in dieser Datei: ihre Darstellung hat %1$s Zeichen"
                    + " und ist damit größer, als diese Form des Berichts trägt. Die"
                    + " PDF-Form des Berichts trägt sie als Seiten, und esj render"
                    + " schreibt sie für sich.");

    private final String english;
    private final String german;

    ReportWord(String english, String german) {
        this.english = english;
        this.german = german;
    }

    /**
     * Returns the words of one sentence a run wrote in this project's own voice.
     *
     * @param phrase which sentence
     * @return the word of this module that carries it
     */
    static ReportWord of(Phrase phrase) {
        return switch (phrase) {
            case ROW_XML -> ROW_XML;
            case ROW_XML_BYTES -> ROW_XML_BYTES;
            case ROW_PACK -> ROW_PACK;
            case ROW_WRITTEN_SYNTAX -> ROW_WRITTEN_SYNTAX;
            case ROW_TERMS_WITHOUT_TRANSPORT -> ROW_TERMS_WITHOUT_TRANSPORT;
            case ROW_PDF_STRUCTURE -> ROW_PDF_STRUCTURE;
            case ROW_PDFA -> ROW_PDFA;
            case ROW_PDF_AF -> ROW_PDF_AF;
            case ROW_PDF_XMP -> ROW_PDF_XMP;
            case ROW_PDF_EMBEDDED -> ROW_PDF_EMBEDDED;
            case ROW_PDF_ESJ -> ROW_PDF_ESJ;
            case ESJ_ATTACHMENT -> ESJ_ATTACHMENT;
            case ESJ_ATTACHMENT_PARTLY -> ESJ_ATTACHMENT_PARTLY;
            case ROW_IMPORT -> ROW_IMPORT;
            case SYNTAX_FROM_ATTACHMENT -> SYNTAX_FROM_ATTACHMENT;
            case READER_STREAMING -> READER_STREAMING;
            case READER_XSLT -> READER_XSLT;
            case SKIPPED_OTHER_SYNTAX -> SKIPPED_OTHER_SYNTAX;
            case SKIPPED_OTHER_PROFILE -> SKIPPED_OTHER_PROFILE;
            case SKIPPED_SCHEMA_INVALID -> SKIPPED_SCHEMA_INVALID;
            case ROW_FORMAT_L1 -> ROW_FORMAT_L1;
            case ROW_MODEL_L2 -> ROW_MODEL_L2;
            case ROW_CARDINALITY_L3 -> ROW_CARDINALITY_L3;
            case ROW_RULES -> ROW_RULES;
            case NOTE -> NOTE;
            case NOTES -> NOTES;
            case PDFA_DECLARED -> PDFA_DECLARED;
            case PDFA_NONE -> PDFA_NONE;
            case PDFA_DECLARED_VALIDATED -> PDFA_DECLARED_VALIDATED;
            case PDFA_NONE_VALIDATED -> PDFA_NONE_VALIDATED;
            case AF_RELATIONSHIP -> AF_RELATIONSHIP;
            case REASON_NOT_RUN -> REASON_NOT_RUN;
            case REASON_INPUT_XML -> REASON_INPUT_XML;
            case REASON_NO_INVOICE_LINE -> REASON_NO_INVOICE_LINE;
            case REASON_NO_DOCUMENT -> REASON_NO_DOCUMENT;
            case REASON_FORMAT_REJECTED -> REASON_FORMAT_REJECTED;
            case REASON_MODEL_REJECTED -> REASON_MODEL_REJECTED;
            case REASON_MODEL_MEASURED_NOTHING -> REASON_MODEL_MEASURED_NOTHING;
            case REASON_MEASURED_LESS -> REASON_MEASURED_LESS;
            case REASON_NO_XML -> REASON_NO_XML;
            case DETAIL_ONLY_IN_ESJ -> DETAIL_ONLY_IN_ESJ;
            case DETAIL_LIMIT -> DETAIL_LIMIT;
            case DETAIL_MISSING -> DETAIL_MISSING;
            case DETAIL_NOT_EN16931 -> DETAIL_NOT_EN16931;
            case DETAIL_CONTAINER_WRONG -> DETAIL_CONTAINER_WRONG;
            case SUBJECT_PROFILE -> SUBJECT_PROFILE;
            case SUBJECT_LIMIT -> SUBJECT_LIMIT;
            case PROVENANCE_ATTACHMENT -> PROVENANCE_ATTACHMENT;
            case PROVENANCE_RECODED -> PROVENANCE_RECODED;
            case MORE_NOTES -> MORE_NOTES;
            case TIMES -> TIMES;
            case CUT -> CUT;
            case MORE_FINDINGS -> MORE_FINDINGS;
            case ALSO_REPORTED -> ALSO_REPORTED;
            case EXPORT_NOTE -> EXPORT_NOTE;
            case EXPORT_NOTES -> EXPORT_NOTES;
            case INVOICE_TOO_LARGE -> INVOICE_TOO_LARGE;
            case INVOICE_TOO_LARGE_DOCUMENT -> INVOICE_TOO_LARGE_DOCUMENT;
        };
    }

    /**
     * Returns this word in a language.
     *
     * @param language the language of the report
     * @return the word
     */
    String in(RenderLanguage language) {
        return language == RenderLanguage.GERMAN ? german : english;
    }

    /**
     * Returns this word in a language, with what a run said filled into it.
     *
     * <p>The arguments are texts of the run — a pack, a profile, a count — and are placed
     * as they stand. The locale takes no part in it: a report is the same file on every
     * machine, so a number that a formatter wrote in the digits of the machine's locale
     * would be the one thing in it that is not.
     *
     * @param language  the language of the report
     * @param arguments what the sentence says about, in the order it takes them
     * @return the sentence
     */
    String in(RenderLanguage language, List<String> arguments) {
        return arguments.isEmpty() ? in(language)
                : String.format(Locale.ROOT, in(language), arguments.toArray());
    }
}
