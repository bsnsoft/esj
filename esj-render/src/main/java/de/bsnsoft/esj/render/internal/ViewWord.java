package de.bsnsoft.esj.render.internal;

import de.bsnsoft.esj.render.RenderLanguage;
import java.util.List;
import java.util.Locale;

/**
 * The words of the page {@code esj view} writes that are its own.
 *
 * <p>The page reuses the words of the validation report and of the invoice layout wherever
 * it says what they say — the verdict, the input, the seller, the scope of a verdict — and
 * takes them from {@link ReportWord} and {@link Word}, so that the same thing is called the
 * same in the report, in the letter and on the page. What is here is what only the page has:
 * the names of its views, the sentences at its dead ends, the lines of its footer. A message
 * of the run is not among them: it is printed as the run wrote it, in English, as it is in a
 * German report.
 */
enum ViewWord {

    /** The view of the file as it came. */
    RECEIVED("As received", "Wie empfangen"),

    /** The view of the invoice as this tool read it, rendered from the data. */
    READ("As read", "Wie gelesen"),

    /** The label of the list of views. */
    NAV("Views", "Ansichten"),

    /** The line under the heading of the received file. */
    CAPTION_RECEIVED("The file as it came, unchanged.", "Die Datei, wie sie vorliegt, unverändert."),

    /** The line under the heading of the rendering. */
    CAPTION_READ("Rendered from the data of the electronic invoice.",
            "Aus den Daten der E-Rechnung erzeugt."),

    /** What a verdict beside a picture is about, and what it is not. */
    PICTURE_VERDICT("The verdict is about the data of the electronic invoice; esj reads nothing"
            + " off the page image. Whether picture and data agree is yours to see.",
            "Das Urteil gilt den Daten der E-Rechnung; das Seitenbild liest esj nicht. Ob Bild"
                    + " und Daten übereinstimmen, sehen nur Sie."),

    /** The same, where there is no verdict. */
    PICTURE_ALONE("esj reads nothing off the page image. Whether picture and data agree is yours"
            + " to see.",
            "Das Seitenbild liest esj nicht. Ob Bild und Daten übereinstimmen, sehen nur Sie."),

    /** The link to the received file. */
    OPEN_RECEIVED("Open the received file on its own", "Empfangene Datei für sich öffnen"),

    /** The link to the rendering. */
    OPEN_READ("Open the rendering on its own", "Darstellung für sich öffnen"),

    /** The link to the HTML rendering, the accessible and searchable form. */
    OPEN_HTML("Open as HTML on its own (KoSIT visualization)",
            "Als HTML für sich öffnen (KoSIT-Visualisierung)"),

    /** The link to the PDF form of the report. */
    OPEN_REPORT_PDF("The validation report as a PDF, to keep and to print",
            "Prüfbericht als PDF, zum Aufbewahren und Drucken"),

    /** The link to the HTML form of the report. */
    OPEN_REPORT_HTML("The validation report as an HTML file", "Prüfbericht als HTML-Datei"),

    /** The head of the facts of a PDF that carries an invoice. */
    HEAD_EMBEDDED("Electronic invoice embedded", "E-Rechnung eingebettet"),

    /**
     * The amount an invoice closes with. It is the label the vendored localization of the
     * visualization gives BT-115 in either language; the letter writes the German one too.
     */
    DUE("Amount due", "Fälliger Betrag"),

    /** A fact the document does not state. */
    ABSENT("(absent)", "(fehlt)"),

    /** The verdict word where no validation ran. */
    NOT_CHECKED("not checked", "nicht geprüft"),

    /** What the command that checks the invoice does, after the command itself. */
    HINT(" checks the invoice.", " prüft die Rechnung."),

    /** The dead end of a PDF without an electronic invoice: its head. */
    NO_INVOICE_HEAD("No electronic invoice embedded.", "Keine E-Rechnung eingebettet."),

    /** Its sentence. */
    NO_INVOICE_TEXT("This PDF carries no structured invoice data, and esj reads nothing off its"
            + " pages.",
            "Dieses PDF trägt keine strukturierten Rechnungsdaten; was auf seinen Seiten steht,"
                    + " liest esj nicht."),

    /** Its next step. */
    NO_INVOICE_NEXT("Ask the sender for the electronic invoice.",
            "Bitten Sie den Absender um die E-Rechnung."),

    /** The dead end of a PDF whose attachments were not classified. */
    UNESTABLISHED_HEAD("Not established whether an electronic invoice is embedded.",
            "Nicht festgestellt, ob eine E-Rechnung eingebettet ist."),

    /** The dead end of a PDF with several candidates: its head. */
    SEVERAL_HEAD("Several possible electronic invoices", "Mehrere mögliche E-Rechnungen"),

    /** Its sentence, with the count. */
    SEVERAL_COUNT("The file carries %1$s attachments that could be the electronic invoice and"
            + " does not say which one it is; esj does not choose.",
            "Die Datei trägt %1$s Anhänge, die die E-Rechnung sein könnten, und sagt nicht,"
                    + " welcher es ist; esj wählt nicht."),

    /** Its next step. */
    SEVERAL_ASK("Ask the sender which one counts.", "Fragen Sie den Absender, welcher gilt."),

    /** A column of the table of candidates. */
    COLUMN_POSITION("Position", "Position"),

    /** A column of the table of candidates. */
    COLUMN_NAME("Name", "Name"),

    /** A column of the table of candidates. */
    COLUMN_CONTENT("Content", "Inhalt"),

    /** A column of the table of candidates. */
    COLUMN_SIZE("Size (declared)", "Größe (deklariert)"),

    /** A column of the table of candidates. */
    COLUMN_RELATIONSHIP("Relationship", "Beziehung"),

    /** A column of the table of candidates. */
    COLUMN_READ_WITH("Read with", "Lesen mit"),

    /** What a candidate holds. */
    KIND_CII("CII invoice", "CII-Rechnung"),

    /** What a candidate holds. */
    KIND_UBL_INVOICE("UBL invoice", "UBL-Rechnung"),

    /** What a candidate holds. */
    KIND_UBL_CREDIT_NOTE("UBL credit note", "UBL-Gutschrift"),

    /** What a candidate holds. */
    KIND_UNDETERMINED("undetermined", "nicht bestimmt"),

    /** What a candidate holds. */
    KIND_UNREADABLE("unreadable", "nicht lesbar"),

    /** The dead end of a ZUGFeRD 1.0 invoice: its head. */
    UNSUPPORTED_HEAD("ZUGFeRD 1.0 is not read.", "ZUGFeRD 1.0 wird nicht gelesen."),

    /** Its sentence. */
    UNSUPPORTED_TEXT("The embedded invoice is ZUGFeRD 1.0, which is no syntax binding of"
            + " EN 16931.",
            "Die eingebettete Rechnung ist ZUGFeRD 1.0; das ist keine Syntaxbindung der"
                    + " EN 16931."),

    /** The dead end of an encrypted PDF: its head. */
    ENCRYPTED_HEAD("Encrypted PDF.", "Verschlüsseltes PDF."),

    /** Its sentence. */
    ENCRYPTED_TEXT("esj opens no encrypted file and decrypts nothing; a hybrid electronic"
            + " invoice is never encrypted, as PDF/A forbids it.",
            "esj öffnet keine verschlüsselte Datei und entschlüsselt nichts; eine hybride"
                    + " E-Rechnung ist nie verschlüsselt, PDF/A verbietet es."),

    /** The dead end of a file that was not read: its head. */
    NOT_READ_HEAD("File not read.", "Datei nicht gelesen."),

    /** Its sentence. */
    NOT_READ_TEXT("esj reads UBL, CII and ESJ, and PDF files with an embedded electronic"
            + " invoice.",
            "esj liest UBL, CII und ESJ, und PDF-Dateien mit eingebetteter E-Rechnung."),

    /** The sentence of a limit, after its head, which is the report's. */
    LIMIT_TEXT("The invoice was not judged; the message names the switch that raises the"
            + " bound.",
            "Die Rechnung ist nicht beurteilt; die Meldung nennt die Option, die die Grenze"
                    + " hebt."),

    /** The label over the error text of the run. */
    MESSAGE("Message from esj", "Meldung von esj"),

    /** The HTML rendering that was not written, before why. */
    HTML_REFUSED("The HTML rendering was not written:",
            "Die HTML-Darstellung wurde nicht geschrieben:"),

    /** One value that did not reach the HTML rendering. */
    EXPORT_NOTE("%1$s value of the document did not reach this rendering:",
            "%1$s Wert des Dokuments hat diese Darstellung nicht erreicht:"),

    /** Several of them. */
    EXPORT_NOTES("%1$s values of the document did not reach this rendering:",
            "%1$s Werte des Dokuments haben diese Darstellung nicht erreicht:"),

    /** A run with {@code --report} that had no invoice to check. */
    NO_REPORT("No validation report: there was no invoice to check.",
            "Kein Prüfbericht: es gab keine Rechnung zu prüfen."),

    /** Where the page lies, and that nothing removes it. */
    FOOTER_DIRECTORY("This page and its files are in %1$s; esj does not delete them.",
            "Diese Seite und ihre Dateien liegen in %1$s; esj löscht sie nicht."),

    /** What the page does not do. */
    FOOTER_OFFLINE("Nothing was sent or fetched: the page reads only files of this directory"
            + " and runs no script.",
            "Nichts wurde gesendet oder geladen: die Seite liest nur Dateien dieses"
                    + " Verzeichnisses und führt kein Skript aus."),

    /** What a printout of a page with a report is. */
    PRINT_WITH_REPORT("This printout is the overview with the validation report.",
            "Gedruckt wird diese Übersicht mit dem Prüfbericht."),

    /** What a printout of a page without one is. */
    PRINT_WITHOUT_REPORT("This printout is the overview.", "Gedruckt wird diese Übersicht."),

    /** Where the invoice is printed from instead. */
    PRINT_FILES("Print the invoice from its own files: %1$s.",
            "Die Rechnung drucken Sie aus ihren eigenen Dateien: %1$s."),

    /** The title of the frame of the received file. */
    FRAME_RECEIVED("As received: %1$s", "Wie empfangen: %1$s"),

    /** The title of the frame of the rendering. */
    FRAME_READ("As read: rendering from the data", "Wie gelesen: Darstellung aus den Daten");

    private final String english;
    private final String german;

    ViewWord(String english, String german) {
        this.english = english;
        this.german = german;
    }

    /**
     * Returns this word in a language.
     *
     * @param language the language of the page
     * @return the word
     */
    String in(RenderLanguage language) {
        return language == RenderLanguage.GERMAN ? german : english;
    }

    /**
     * Returns this word in a language, with what the run said filled into it, placed as it
     * stands and in no locale of the machine.
     *
     * @param language  the language of the page
     * @param arguments what the sentence says about, in the order it takes them
     * @return the sentence
     */
    String in(RenderLanguage language, List<String> arguments) {
        return arguments.isEmpty() ? in(language)
                : String.format(Locale.ROOT, in(language), arguments.toArray());
    }
}
