package de.bsnsoft.esj.render.internal;

import de.bsnsoft.esj.render.RenderLanguage;
import java.util.List;
import java.util.Locale;

/**
 * The words of the page {@code esj view} writes that are its own, written for the person
 * who opens an invoice.
 *
 * <p>A label names a thing; a sentence is there only where the reader has something to do,
 * and then it says what. Nothing here explains what the tool did. The page takes the words
 * of the report and of the letter wherever it says what they say — the heading of the
 * report, the name of a credit note's amount — from {@link ReportWord} and {@link Word}.
 *
 * <p>The verdict words are the page's own. The report writes the verdict in the spelling
 * every form of a run uses for it, in both languages, because a pipeline branches on it; a
 * page is read by a person, who reads the verdict in their own language.
 */
enum ViewWord {

    /** The tab of the received PDF. */
    TAB_PDF("PDF", "PDF"),

    /** The tab of the letter rendered from the data. */
    TAB_INVOICE("Invoice", "Rechnung"),

    /** The tab of the KoSIT visualization. */
    TAB_KOSIT("KoSIT", "KoSIT"),

    /** The one quiet line over the letter. */
    CAPTION_INVOICE("Rendered from the invoice data", "Aus den Rechnungsdaten erzeugt"),

    /** The one quiet line over a KoSIT visualization that does not show every value. */
    KOSIT_INCOMPLETE("This view does not show every detail of the invoice.",
            "Diese Ansicht zeigt nicht alle Angaben der Rechnung."),

    /** The one link of the report tab. */
    SAVE_PDF("Save as PDF", "Als PDF speichern"),

    /** The folder the page and its files are in, before its path. */
    FOLDER("Folder:", "Ablage:"),

    /** The number of an invoice. */
    NUMBER("Invoice number", "Rechnungsnummer"),

    /** The date of an invoice. */
    DATE("Invoice date", "Rechnungsdatum"),

    /** The amount an invoice closes with. */
    DUE("Amount due", "Fälliger Betrag"),

    /** The verdict of an invoice that passed every check. */
    VALID("VALID", "GÜLTIG"),

    /** The verdict of an invoice that failed one. */
    INVALID("INVALID", "UNGÜLTIG"),

    /** The verdict where a check that belongs to the verdict did not run. */
    UNDECIDED("UNDECIDED", "NICHT ENTSCHIEDEN"),

    /** The verdict where nothing was checked. */
    NOT_CHECKED("NOT CHECKED", "NICHT GEPRÜFT"),

    /** The verdict of a subject a limit stopped before it was judged. */
    NO_VERDICT("NO VERDICT", "KEIN ERGEBNIS"),

    /** No finding at all. */
    NO_FINDINGS("No findings", "Keine Befunde"),

    /** One error. */
    ERROR("%1$s error", "%1$s Fehler"),

    /** Several errors. */
    ERRORS("%1$s errors", "%1$s Fehler"),

    /** One warning. */
    WARNING("%1$s warning", "%1$s Warnung"),

    /** Several warnings. */
    WARNINGS("%1$s warnings", "%1$s Warnungen"),

    /** One note. */
    NOTE("%1$s note", "%1$s Hinweis"),

    /** Several notes. */
    NOTES("%1$s notes", "%1$s Hinweise"),

    /** A PDF without an electronic invoice. */
    NO_INVOICE("This PDF contains no e-invoice.", "Dieses PDF enthält keine E-Rechnung."),

    /** What to do about it. */
    NO_INVOICE_ASK("Ask the sender for the e-invoice.",
            "Bitten Sie den Absender um die E-Rechnung."),

    /** A PDF whose attachments could not be told apart. */
    NOT_ESTABLISHED("Whether this PDF contains an e-invoice cannot be determined.",
            "Ob dieses PDF eine E-Rechnung enthält, lässt sich nicht feststellen."),

    /** What to do about it. */
    NOT_ESTABLISHED_ASK("Ask the sender for the e-invoice as an XML file.",
            "Bitten Sie den Absender um die E-Rechnung als XML-Datei."),

    /** A PDF with several attachments that could each be the invoice. */
    SEVERAL("This PDF contains more than one possible e-invoice.",
            "Dieses PDF enthält mehrere mögliche E-Rechnungen."),

    /** What to do about it. */
    SEVERAL_ASK("Ask the sender which one applies.", "Fragen Sie den Absender, welche gilt."),

    /** A column of the table of candidates. */
    COLUMN_POSITION("No.", "Nr."),

    /** A column of the table of candidates. */
    COLUMN_NAME("File name", "Dateiname"),

    /** A column of the table of candidates. */
    COLUMN_CONTENT("Content", "Inhalt"),

    /** A column of the table of candidates. */
    COLUMN_SIZE("Size", "Größe"),

    /** A column of the table of candidates. */
    COLUMN_RELATIONSHIP("Relationship", "Beziehung"),

    /** A column of the table of candidates. */
    COLUMN_OPEN_WITH("Open with", "Öffnen mit"),

    /** What a candidate holds. */
    KIND_CII("CII invoice", "CII-Rechnung"),

    /** What a candidate holds. */
    KIND_UBL_INVOICE("UBL invoice", "UBL-Rechnung"),

    /** What a candidate holds. */
    KIND_UBL_CREDIT_NOTE("UBL credit note", "UBL-Gutschrift"),

    /** What a candidate holds. */
    KIND_UNDETERMINED("unknown", "unbekannt"),

    /** What a candidate holds. */
    KIND_UNREADABLE("unreadable", "nicht lesbar"),

    /** A cell of the table of candidates with nothing in it. */
    ABSENT("–", "–"),

    /** An invoice in ZUGFeRD 1.0. */
    UNSUPPORTED("The e-invoice is in ZUGFeRD 1.0, which does not conform to EN 16931.",
            "Die E-Rechnung hat das Format ZUGFeRD 1.0, das nicht der EN 16931 entspricht."),

    /** What to do about it. */
    UNSUPPORTED_ASK("Ask the sender for an EN 16931 e-invoice.",
            "Bitten Sie den Absender um eine E-Rechnung nach EN 16931."),

    /** An encrypted PDF. */
    ENCRYPTED("This PDF is encrypted.", "Dieses PDF ist verschlüsselt."),

    /** What to do about it. */
    ENCRYPTED_ASK("Ask the sender for an unencrypted copy.",
            "Bitten Sie den Absender um eine unverschlüsselte Fassung."),

    /** A file that could not be read as an invoice. */
    NOT_READ("This file is not a readable e-invoice.",
            "Diese Datei ist keine lesbare E-Rechnung."),

    /** What to do about it. */
    NOT_READ_ASK("Ask the sender to send the e-invoice again.",
            "Bitten Sie den Absender, die E-Rechnung erneut zu senden."),

    /** A file larger than a check allows. */
    LIMIT_SIZE_CHECK("The file is larger than this check allows.",
            "Die Datei ist größer, als diese Prüfung zulässt."),

    /** A file larger than a view allows. */
    LIMIT_SIZE_VIEW("The file is larger than this view allows.",
            "Die Datei ist größer, als diese Ansicht zulässt."),

    /** A check that took longer than allowed. */
    LIMIT_TIME_CHECK("The check took longer than allowed.",
            "Die Prüfung hat länger gedauert, als zulässig ist."),

    /** A view that took longer than allowed. */
    LIMIT_TIME_VIEW("Preparing the view took longer than allowed.",
            "Die Ansicht hat länger gedauert, als zulässig ist."),

    /** A letter of more pages than allowed. */
    LIMIT_PAGES("The invoice runs to more pages than this view allows.",
            "Die Rechnung hat mehr Seiten, als diese Ansicht zulässt."),

    /** An invoice that cannot be shown as a letter. */
    NOT_RENDERED("This invoice cannot be shown here.",
            "Diese Rechnung lässt sich hier nicht darstellen.");

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
