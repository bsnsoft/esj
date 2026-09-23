package de.bsnsoft.esj.render;

/**
 * The few words of the PDF rendering that are not labels of business terms: the headings
 * of its sections, the headers of its tables and the two words that join two values into
 * one cell.
 *
 * <p>They are written here in both languages rather than taken from the registry or from
 * the vendored localization, for two reasons. The registry names a group the way the
 * tables of the standard do — {@code DOCUMENT TOTALS}, in capitals — and a heading in
 * capitals shouts at a reader. And the localization of the visualization labels the fields
 * of that project's layout, so it has no entry at all for a third of the groups and none
 * for a column header of a table this project laid out itself. A heading is part of a
 * layout, and this layout is this project's.
 *
 * <p>Labels of business terms are a different matter and do not belong here: they are
 * facts of the model and come from {@link Labels}.
 */
enum Word {

    /** The party that issues the invoice. */
    SELLER("Seller", "Verkäufer"),

    /** The party the invoice is addressed to. */
    BUYER("Buyer", "Käufer"),

    /** The heading over the identification of the document itself. */
    INVOICE_DATA("Invoice data", "Rechnungsdaten"),

    /** The heading over the references to other documents and processes. */
    REFERENCES("References", "Referenzen"),

    /** The heading over the notes of the invoice. */
    NOTES("Invoice notes", "Bemerkungen zur Rechnung"),

    /** The heading over the delivery information. */
    DELIVERY("Delivery", "Lieferung"),

    /** The heading over the invoice lines. */
    LINES("Invoice lines", "Rechnungspositionen"),

    /** The heading over the allowances of the document. */
    ALLOWANCES("Document level allowances", "Nachlässe auf Dokumentenebene"),

    /** The heading over the charges of the document. */
    CHARGES("Document level charges", "Zuschläge auf Dokumentenebene"),

    /** The heading over the VAT breakdown. */
    VAT_BREAKDOWN("VAT breakdown", "Aufschlüsselung der Umsatzsteuer"),

    /** The heading over the totals. */
    TOTALS("Document totals", "Gesamtbeträge"),

    /** The heading over the payment instructions. */
    PAYMENT("Payment instructions", "Zahlungsanweisungen"),

    /**
     * What one of several payment instructions is called where a label or a caption has to
     * say which of them it belongs to.
     *
     * <p>The registry names BG-16 in English and the localization of the visualization
     * does not name it at all, and a German letter that numbered its accounts in English
     * would be the register speaking rather than the letter.
     */
    PAYMENT_INSTRUCTION("Payment instruction", "Zahlungsanweisung"),

    /**
     * The caption under the QR code of a credit transfer, which is the name a reader of
     * the letter knows the code by: the guideline calls it the EPC QR code, and in Germany
     * it is printed under the name a payer looks for.
     */
    PAYMENT_CODE("EPC QR code", "GiroCode"),

    /** Says that the name of the beneficiary was written to the length the code allows. */
    PAYMENT_CODE_NAME_CUT(
            "the name of the beneficiary is written to the 70 characters the code carries",
            "Der Name des Zahlungsempfängers steht mit den 70 Zeichen im Code, die er"
                    + " aufnimmt"),

    /**
     * Says that the remittance information is longer than the code carries and that the
     * code names the invoice in its place.
     */
    PAYMENT_CODE_REMITTANCE_REPLACED(
            "the remittance information is longer than the 140 characters the code carries,"
                    + " so the code names the invoice instead",
            "Der Verwendungszweck ist länger als die 140 Zeichen, die der Code aufnimmt;"
                    + " der Code nennt stattdessen die Rechnungsnummer"),

    /** Says that the bank identifier of the account is not a BIC and is not in the code. */
    PAYMENT_CODE_BIC_LEFT_OUT(
            "the bank identifier of the account is not a BIC, so the code carries none",
            "Die Bankkennung des Kontos ist kein BIC; der Code führt keine"),

    /** The heading over the payee. */
    PAYEE("Payee", "Zahlungsempfänger"),

    /** The heading over the tax representative of the seller. */
    TAX_REPRESENTATIVE("Seller tax representative", "Steuervertreter des Verkäufers"),

    /** The heading over the supporting documents. */
    SUPPORTING_DOCUMENTS("Supporting documents", "Anlagen und Belege"),

    /** The heading over everything the layout above has no place of its own for. */
    OTHER_TERMS("Other terms", "Weitere Angaben"),

    /** The closing heading of the letter layout, which does the same work. */
    FURTHER_DETAILS("Further details", "Weitere Angaben"),

    /** Names the code of the document type where the page shows its name instead. */
    CODE_DOCUMENT_TYPE("Document type", "Dokumentenart"),

    /** Names a unit code where the page shows the name of the unit instead. */
    CODE_UNIT("Unit", "Einheit"),

    /** Names a means of payment code where the page shows its name instead. */
    CODE_PAYMENT_MEANS("Payment means", "Zahlungsart"),

    /** Names a VAT category code where the page shows its name instead. */
    CODE_VAT_CATEGORY("VAT category", "Umsatzsteuerkategorie"),

    /** Names a country code where the page shows the name of the country instead. */
    CODE_COUNTRY("Country", "Land"),

    /** What the block beside the address field calls the buyer identifier BT-46. */
    CUSTOMER_NUMBER("Customer number", "Kundennummer"),

    /** What that block calls the seller's VAT identifier BT-31. */
    VAT_IDENTIFIER("VAT ID", "USt-IdNr."),

    /** What it calls the seller's tax registration identifier BT-32. */
    TAX_NUMBER("Tax number", "Steuernummer"),

    /** What the head data of a credit note calls its number, BT-1. */
    CREDIT_NOTE_NUMBER("Credit note number", "Gutschriftsnummer"),

    /** What the head data of a credit note calls its issue date, BT-2. */
    CREDIT_NOTE_DATE("Credit note date", "Gutschriftsdatum"),

    /** What the totals of a credit note call the figure they close with, BT-115. */
    AMOUNT_CREDITED("Amount credited", "Gutschriftsbetrag"),

    /** Introduces the one preceding invoice (BG-3) the line under the title names. */
    PRECEDING_INVOICE("to invoice", "zur Rechnung"),

    /** Introduces the two or three preceding invoices that line names. */
    PRECEDING_INVOICES("to invoices", "zu den Rechnungen"),

    /** Joins the number of a preceding invoice and its issue date in that line. */
    PRECEDING_OF("of", "vom"),

    /**
     * What the foot of the letter writes behind the seller's legal registration
     * identifier BT-30 where the document states no scheme for it.
     */
    FOOT_REGISTRATION("Registration number", "Registernummer"),

    /**
     * What the foot writes behind a seller identifier BT-29 where the document states no
     * scheme for it.
     */
    FOOT_IDENTIFIER("Identifier", "Kennung"),

    /** What the payment block of the letter calls the payment terms BT-20. */
    PAYMENT_TERMS("Payment terms", "Zahlungsbedingungen"),

    /** What the payment block calls an account identifier that is written as an IBAN. */
    ACCOUNT_IBAN("IBAN", "IBAN"),

    /** What the payment block calls the name the account is held under. */
    ACCOUNT_HOLDER("Account holder", "Kontoinhaber"),

    /** What the payment block calls the identifier of the payment service provider. */
    ACCOUNT_BIC("BIC", "BIC"),

    /** The column of the line identifier. */
    COLUMN_LINE("No.", "Nr."),

    /** The column of the item. */
    COLUMN_ITEM("Item", "Bezeichnung"),

    /** The column of the invoiced quantity. */
    COLUMN_QUANTITY("Quantity", "Menge"),

    /** The column of the net unit price. */
    COLUMN_UNIT_PRICE("Unit price, net", "Einzelpreis, netto"),

    /** The column of the VAT category and rate of a line. */
    COLUMN_VAT("VAT", "USt."),

    /** The column of the net line amount. */
    COLUMN_NET_AMOUNT("Net amount", "Nettobetrag"),

    /** The column of the reason of an allowance or a charge. */
    COLUMN_REASON("Reason", "Grund"),

    /** The column of the base amount of an allowance or a charge. */
    COLUMN_BASE("Base amount", "Grundbetrag"),

    /** The column of the percentage of an allowance or a charge. */
    COLUMN_PERCENTAGE("Percentage", "Prozentsatz"),

    /** The column of an amount. */
    COLUMN_AMOUNT("Amount", "Betrag"),

    /** The column of the VAT category. */
    COLUMN_CATEGORY("Category", "Kategorie"),

    /** The column of the VAT rate. */
    COLUMN_RATE("Rate", "Satz"),

    /** The column of the amount a VAT category is calculated on. */
    COLUMN_TAXABLE("Taxable amount", "Steuerbasisbetrag"),

    /** The column of the VAT of a category. */
    COLUMN_VAT_AMOUNT("VAT amount", "Umsatzsteuerbetrag"),

    /** What a sub invoice line of the XRechnung extension is called. */
    SUB_LINE("Sub line", "Unterposition"),

    /** What an allowance on a line is called where it hangs under that line. */
    LINE_ALLOWANCE("Allowance", "Nachlass"),

    /** What a charge on a line is called where it hangs under that line. */
    LINE_CHARGE("Charge", "Zuschlag"),

    /** What an item attribute is called where it hangs under its line. */
    ITEM_ATTRIBUTE("Attribute", "Merkmal"),

    /** Joins a unit price and the quantity it is a price for. */
    PER("per", "je"),

    /** Introduces the size of an attachment. */
    SIZE("size", "Größe"),

    /** Says that the content of an attachment is not printed. */
    ATTACHMENT_NOT_SHOWN("content not printed", "Inhalt nicht abgedruckt"),

    /** Says that an extension subtree carries data this rendering does not print. */
    EXTENSION_DATA("extension data, not printed", "Erweiterungsdaten, nicht abgedruckt"),

    /** Marks a figure a template placed: one that was shown, not one of the standard. */
    DISPLAYED("displayed", "angezeigt"),

    /** Says, once under the block that carries them, what a displayed figure is. */
    DISPLAYED_NOTE(
            "Figures marked as displayed are figures the buyer was shown, recorded in a model"
                    + " extension. The EN 16931 figures of this invoice are the net amounts,"
                    + " the VAT breakdown and the totals beside them.",
            "Als angezeigt gekennzeichnete Beträge sind dem Käufer angezeigte Beträge aus einer"
                    + " Modellerweiterung. Maßgeblich sind die Nettobeträge, die"
                    + " Umsatzsteueraufschlüsselung und die Gesamtbeträge nach EN 16931"
                    + " daneben."),

    /** Says that the detail lines under the repeated header belong to a row of the page before. */
    CONTINUED("continued", "Fortsetzung"),

    /** The footer: the page number of the page count. */
    PAGE_OF("Page %1$d of %2$d", "Seite %1$d von %2$d");

    private final String english;
    private final String german;

    Word(String english, String german) {
        this.english = english;
        this.german = german;
    }

    /**
     * Returns this word in a language.
     *
     * @param language the language of the rendering
     * @return the word
     */
    String in(RenderLanguage language) {
        return language == RenderLanguage.GERMAN ? german : english;
    }
}
