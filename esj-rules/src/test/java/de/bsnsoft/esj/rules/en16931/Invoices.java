package de.bsnsoft.esj.rules.en16931;

/**
 * The documents the rule cases are written against.
 *
 * <p>One of them, {@link #standard()}, is a complete invoice on which no rule of the pack has
 * anything to say, and a test asserts exactly that: it is the anchor of the whole table, because
 * a positive case is only worth something if the document it is taken from is sound. The others
 * put one more part of the model into it — an allowance, a charge, a payment instruction, a
 * delivery — or state it under another VAT category, so that a rule about that part has a
 * document to be about.
 *
 * <p>The bases other than {@code standard()} are not required to be sound throughout. A rule
 * case asserts one thing about one rule: that the rule is silent on the first document and
 * speaks on the second. Making every base satisfy all two hundred and ten rules would mean
 * recomputing the document totals for every one of them and would test the arithmetic of the
 * test rather than the rule.
 */
final class Invoices {

    private Invoices() {
    }

    /** A complete standard-rated invoice of one line on which no rule of the pack fires. */
    static Invoice standard() {
        return Invoice.empty()
                .put("/BT-1", "RE-2026-0001")
                .put("/BT-2", "2026-01-15")
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BT-9", "2026-02-15")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2017")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-4/BT-31", "DE123456789")
                .put("/BG-4/BG-5/BT-37", "Musterstadt")
                .put("/BG-4/BG-5/BT-40", "DE")
                .put("/BG-7/BT-44", "Muster AG")
                .put("/BG-7/BG-8/BT-52", "Beispielstadt")
                .put("/BG-7/BG-8/BT-55", "DE")
                .put("/BG-22/BT-106", "100")
                .put("/BG-22/BT-109", "100")
                .put("/BG-22/BT-110", "19")
                .put("/BG-22/BT-112", "119")
                .put("/BG-22/BT-115", "119")
                .put("/BG-23/0/BT-116", "100")
                .put("/BG-23/0/BT-117", "19")
                .put("/BG-23/0/BT-118", "S")
                .put("/BG-23/0/BT-119", "19")
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "100")
                .put("/BG-25/0/BG-29/BT-146", "100")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", "Consulting service");
    }

    /** The standard invoice with a document level allowance, and the totals it changes. */
    static Invoice allowance() {
        return standard()
                .put("/BG-20/0/BT-92", "10")
                .put("/BG-20/0/BT-95", "S")
                .put("/BG-20/0/BT-96", "19")
                .put("/BG-20/0/BT-97", "Loyalty discount")
                .put("/BG-22/BT-107", "10")
                .put("/BG-22/BT-109", "90")
                .put("/BG-22/BT-110", "17.1")
                .put("/BG-22/BT-112", "107.1")
                .put("/BG-22/BT-115", "107.1")
                .put("/BG-23/0/BT-116", "90")
                .put("/BG-23/0/BT-117", "17.1");
    }

    /** The standard invoice with a document level charge, and the totals it changes. */
    static Invoice charge() {
        return standard()
                .put("/BG-21/0/BT-99", "10")
                .put("/BG-21/0/BT-102", "S")
                .put("/BG-21/0/BT-103", "19")
                .put("/BG-21/0/BT-104", "Freight")
                .put("/BG-22/BT-108", "10")
                .put("/BG-22/BT-109", "110")
                .put("/BG-22/BT-110", "20.9")
                .put("/BG-22/BT-112", "130.9")
                .put("/BG-22/BT-115", "130.9")
                .put("/BG-23/0/BT-116", "110")
                .put("/BG-23/0/BT-117", "20.9");
    }

    /** The standard invoice whose line carries an allowance of ten on a price of a hundred and ten. */
    static Invoice lineAllowance() {
        return standard()
                .put("/BG-25/0/BG-29/BT-146", "110")
                .put("/BG-25/0/BG-27/0/BT-136", "10")
                .put("/BG-25/0/BG-27/0/BT-139", "Loyalty discount");
    }

    /** The standard invoice whose line carries a charge of ten on a price of ninety. */
    static Invoice lineCharge() {
        return standard()
                .put("/BG-25/0/BG-29/BT-146", "90")
                .put("/BG-25/0/BG-28/0/BT-141", "10")
                .put("/BG-25/0/BG-28/0/BT-144", "Packing");
    }

    /** The standard invoice with a credit transfer payment instruction. */
    static Invoice payment() {
        return standard()
                .put("/BG-16/BT-81", "30")
                .put("/BG-16/BG-17/0/BT-84", "DE89370400440532013000")
                .put("/BG-16/BG-17/0/BT-85", "Example GmbH");
    }

    /** The standard invoice paid by card, with the card number masked to its last four digits. */
    static Invoice card() {
        return standard()
                .put("/BG-16/BT-81", "48")
                .put("/BG-16/BG-18/BT-87", "************1234")
                .put("/BG-16/BG-18/BT-88", "Muster AG");
    }

    /** The standard invoice with a seller tax representative party. */
    static Invoice taxRepresentative() {
        return standard()
                .put("/BG-11/BT-62", "Steuervertretung Beispiel GmbH")
                .put("/BG-11/BT-63", "DE111222333")
                .put("/BG-11/BG-12/BT-66", "Beispielstadt")
                .put("/BG-11/BG-12/BT-69", "DE");
    }

    /** The standard invoice with delivery information, an invoicing period and a deliver to address. */
    static Invoice delivery() {
        return standard()
                .put("/BG-13/BT-72", "2026-01-14")
                .put("/BG-13/BG-14/BT-73", "2026-01-01")
                .put("/BG-13/BG-14/BT-74", "2026-01-14")
                .put("/BG-13/BG-15/BT-77", "Lieferstadt")
                .put("/BG-13/BG-15/BT-80", "DE");
    }

    /** The standard invoice whose line carries a period. */
    static Invoice linePeriod() {
        return standard()
                .put("/BG-25/0/BG-26/BT-134", "2026-01-01")
                .put("/BG-25/0/BG-26/BT-135", "2026-01-14");
    }

    /** The standard invoice with an invoice note that states its subject. */
    static Invoice note() {
        return standard()
                .put("/BG-1/0/BT-21", "AAI")
                .put("/BG-1/0/BT-22", "Delivered as agreed.");
    }

    /** The standard invoice with a payee. */
    static Invoice payee() {
        return standard()
                .put("/BG-10/BT-59", "Factoring Beispiel GmbH")
                .put("/BG-10/BT-60", "FAC-1");
    }

    /** The standard invoice with an additional supporting document. */
    static Invoice attachment() {
        return standard()
                .put("/BG-24/0/BT-122", "TIMESHEET-1")
                .put("/BG-24/0/BT-123", "Time sheet of January");
    }

    /**
     * The standard invoice whose supporting document is embedded, with the media type it
     * states.
     *
     * @param mimeCode the media type of the attachment
     * @return the invoice
     */
    static Invoice attachment(String mimeCode) {
        return attachment()
                .binary("/BG-24/0/BT-125", "date,hours\n2026-01-02,8\n", mimeCode, "january.csv");
    }

    /** The standard invoice with a preceding invoice reference. */
    static Invoice preceding() {
        return standard()
                .put("/BG-3/0/BT-25", "RE-2025-0912")
                .put("/BG-3/0/BT-26", "2025-12-01");
    }

    /** The standard invoice whose item carries an attribute. */
    static Invoice attributes() {
        return standard()
                .put("/BG-25/0/BG-31/BG-32/0/BT-160", "Colour")
                .put("/BG-25/0/BG-31/BG-32/0/BT-161", "Blue");
    }

    /**
     * The standard invoice restated under a VAT category in which no VAT is levied.
     *
     * @param code the VAT category code of UNTDID 5305
     * @return the invoice, with the rate zero, the tax amount zero and an exemption reason
     */
    static Invoice zeroRated(String code) {
        return standard()
                .put("/BG-22/BT-110", "0")
                .put("/BG-22/BT-112", "100")
                .put("/BG-22/BT-115", "100")
                .put("/BG-23/0/BT-117", "0")
                .put("/BG-23/0/BT-118", code)
                .put("/BG-23/0/BT-119", "0")
                .put("/BG-23/0/BT-120", "No VAT is levied on this invoice.")
                .put("/BG-25/0/BG-30/BT-151", code)
                .put("/BG-25/0/BG-30/BT-152", "0");
    }

    /** The zero rated invoice, which states no exemption reason because its category forbids one. */
    static Invoice zeroRatedWithoutReason(String code) {
        return zeroRated(code).drop("/BG-23/0/BT-120");
    }

    /** The reverse charge invoice, which needs the buyer's VAT identifier as well. */
    static Invoice reverseCharge() {
        return zeroRated("AE").put("/BG-7/BT-48", "DE987654321");
    }

    /** The intra-community supply invoice, which needs the buyer, a delivery date and a country. */
    static Invoice intraCommunity() {
        return zeroRated("K")
                .put("/BG-7/BT-48", "FR12345678901")
                .put("/BG-13/BT-72", "2026-01-14")
                .put("/BG-13/BG-15/BT-77", "Lieferstadt")
                .put("/BG-13/BG-15/BT-80", "FR");
    }

    /** The invoice that is not subject to VAT, which carries no VAT identifier of any party. */
    static Invoice notSubject() {
        return zeroRated("O")
                .drop("/BG-4/BT-31", "/BG-23/0/BT-119", "/BG-25/0/BG-30/BT-152")
                .put("/BG-4/BT-30", "HRB 12345");
    }

    /**
     * The standard invoice restated under one of the two regional taxes, which are levied at a
     * rate of their own.
     *
     * @param code the VAT category code of UNTDID 5305
     * @return the invoice, at a rate of seven per cent
     */
    static Invoice regional(String code) {
        return standard()
                .put("/BG-22/BT-110", "7")
                .put("/BG-22/BT-112", "107")
                .put("/BG-22/BT-115", "107")
                .put("/BG-23/0/BT-117", "7")
                .put("/BG-23/0/BT-118", code)
                .put("/BG-23/0/BT-119", "7")
                .put("/BG-25/0/BG-30/BT-151", code)
                .put("/BG-25/0/BG-30/BT-152", "7");
    }

    /**
     * Adds a document level allowance of one VAT category to an invoice.
     *
     * @param invoice the invoice
     * @param code    the VAT category code
     * @param rate    the VAT rate of the allowance, or {@code null} for none
     * @return the same invoice
     */
    static Invoice withAllowance(Invoice invoice, String code, String rate) {
        invoice.put("/BG-20/0/BT-92", "10")
                .put("/BG-20/0/BT-95", code)
                .put("/BG-20/0/BT-97", "Loyalty discount");
        return rate == null ? invoice : invoice.put("/BG-20/0/BT-96", rate);
    }

    /**
     * Adds a document level charge of one VAT category to an invoice.
     *
     * @param invoice the invoice
     * @param code    the VAT category code
     * @param rate    the VAT rate of the charge, or {@code null} for none
     * @return the same invoice
     */
    static Invoice withCharge(Invoice invoice, String code, String rate) {
        invoice.put("/BG-21/0/BT-99", "10")
                .put("/BG-21/0/BT-102", code)
                .put("/BG-21/0/BT-104", "Freight");
        return rate == null ? invoice : invoice.put("/BG-21/0/BT-103", rate);
    }
}
