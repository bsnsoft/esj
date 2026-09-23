package de.bsnsoft.esj.render;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The one code this project prints: the QR code a payer's banking application reads to
 * fill in a credit transfer of this invoice.
 *
 * <p>Its content is the payload of EPC069-12, the guideline of the European Payments
 * Council, known in Germany under the name <i>GiroCode</i>. Every element of that payload
 * is a value this document states — the account, the beneficiary, the amount due, the
 * remittance information — written out again in the order the guideline gives them.
 * Nothing here computes anything: a figure the code carries is the figure the letter
 * prints beside it, and a document that does not state one gets no code.
 *
 * <p>The code is drawn rather than photographed: {@link #cells()} is the matrix of modules
 * and the layout fills them as squares, so the rendering carries no image object and the
 * PDF/A claim of this module is untouched. The error correction level is the one the
 * guideline names, and the version is bounded by it as well.
 *
 * <p>Where a condition of the guideline is not met — a means of payment that is not a
 * credit transfer, an invoice in another currency, an account identifier that is not a
 * well-formed IBAN, an amount outside the range, a payload past its bound — this returns
 * nothing and the letter says nothing about it. A code that a banking application refuses,
 * or one that carries a figure the guideline did not mean, would be worse than no code:
 * the invoice itself is on the page either way.
 *
 * <p>Two elements of the payload the guideline can do without: it allows an empty bank
 * identifier in this version, and it allows a payment with no reference at all. So an
 * identifier that is not a BIC, and a reference the payload cannot carry, cost the code
 * that element rather than the whole code — and the letter says which element it was
 * ({@link #remarks()}), because a reader who compares the page with what the code carries
 * is owed the difference.
 */
final class PaymentCode {

    /** The service tag every payload of the guideline begins with. */
    private static final String SERVICE_TAG = "BCD";

    /** The version of the payload this project writes. */
    private static final String VERSION = "002";

    /** The character set of the payload: 1 is UTF-8, which is what the elements are read as. */
    private static final String CHARACTER_SET = "1";

    /** The identification of the payload: a SEPA credit transfer. */
    private static final String IDENTIFICATION = "SCT";

    /** The code of the currency the guideline allows an amount in. */
    private static final String EURO = "EUR";

    /** The means of payment the guideline is about: credit transfer, and the SEPA one. */
    private static final List<String> CREDIT_TRANSFER = List.of("30", "58");

    /** How many characters of the name of the beneficiary the payload carries. */
    static final int NAME_LIMIT = 70;

    /** How many characters of the unstructured remittance information it carries. */
    static final int REMITTANCE_LIMIT = 140;

    /** How many bytes the whole payload may be. */
    static final int PAYLOAD_LIMIT = 331;

    /** The highest version of the symbol the guideline allows. */
    static final int MAX_VERSION = 13;

    /** How many modules of white surround the symbol, as ISO/IEC 18004 asks. */
    static final int QUIET_ZONE = 4;

    /** The smallest amount the guideline allows. */
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    /** The largest one. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    /** The shape of an amount the payload carries: digits, and at most two decimal places. */
    private static final Pattern AMOUNT = Pattern.compile("[0-9]{1,9}(\\.[0-9]{1,2})?");

    /** The shape of an IBAN: two letters, two digits, and up to thirty more of either. */
    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}[0-9]{2}[0-9A-Z]{1,30}");

    /**
     * The shape of a BIC after ISO 9362: four letters for the institution, two for the
     * country, two characters for the place, and three more for a branch or none.
     */
    private static final Pattern BIC = Pattern.compile("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");

    /** What is subtracted from a letter to get its value in the check of ISO 13616. */
    private static final int LETTER_OFFSET = 'A' - 10;

    /** The modulus of that check. */
    private static final int MODULUS = 97;

    /** Where what the code carries is not what the letter prints beside it. */
    enum Remark {

        /**
         * The name of the beneficiary stands in the code with the 70 characters the
         * guideline holds it to. A name is read by a person and a person reads the first
         * seventy characters of it as the name.
         */
        BENEFICIARY_CUT,

        /**
         * The remittance information is longer than the code carries, so the code names
         * the invoice instead. A reference written short is a reference the seller cannot
         * reconcile a payment on, and the invoice number is what they reconcile on.
         */
        REMITTANCE_REPLACED,

        /**
         * The document states a bank identifier that is not a BIC, so the code carries
         * none. The guideline allows the element to be empty in this version, and a
         * payment into a well-formed IBAN needs nothing else.
         */
        BIC_LEFT_OUT
    }

    private final String payload;
    private final boolean[][] cells;
    private final int version;
    private final List<Remark> remarks;
    private final SemanticPath instruction;
    private final SemanticPath account;

    private PaymentCode(String payload, boolean[][] cells, int version,
                        List<Remark> remarks, SemanticPath instruction,
                        SemanticPath account) {
        this.payload = payload;
        this.cells = cells;
        this.version = version;
        this.remarks = remarks;
        this.instruction = instruction;
        this.account = account;
    }

    /**
     * Returns the code of a document, where the document states a credit transfer the
     * guideline covers.
     *
     * <p>What is read, in the order the guideline puts the elements in: the BIC BT-86 and
     * the account identifier BT-84 of the first credit transfer account of the first
     * payment instruction whose means BT-81 is a credit transfer; the beneficiary, which
     * is the account name BT-85, or the payee BT-59, or the seller BT-27; the amount due
     * BT-115 in the currency BT-5; and the remittance information BT-83, or the invoice
     * number BT-1 where the document states none — or where BT-83 is longer than the
     * payload carries.
     *
     * <p>The type of the document is not read. Whether a page asks its reader to pay is a
     * decision of the layout that draws the page, and the letter layout draws no code where
     * its reader is not the one who pays: on a credit note, and on a self-billed invoice.
     *
     * @param document the document
     * @return the code, or nothing where the document does not meet the guideline
     */
    static Optional<PaymentCode> of(SemanticDocument document) {
        if (!EURO.equals(value(document, SemanticPath.of("/BT-5")))) {
            return Optional.empty();
        }
        for (SemanticPath instruction
                : InvoiceLayout.instances(document, SemanticPath.root(), "BG-16")) {
            if (!CREDIT_TRANSFER.contains(
                    value(document, InvoiceLayout.path(instruction, "BT-81")))) {
                continue;
            }
            List<SemanticPath> accounts =
                    InvoiceLayout.instances(document, instruction, "BG-17");
            if (accounts.isEmpty()) {
                continue;
            }
            return of(document, instruction, accounts.get(0));
        }
        return Optional.empty();
    }

    /** Returns the code of one credit transfer account, or nothing where it is not one. */
    private static Optional<PaymentCode> of(SemanticDocument document,
                                            SemanticPath instruction, SemanticPath account) {
        String iban = value(document, InvoiceLayout.path(account, "BT-84"))
                .replace(" ", "");
        if (!isIban(iban)) {
            return Optional.empty();
        }
        String amount = value(document, SemanticPath.of("/BG-22/BT-115"));
        if (!isAmount(amount)) {
            return Optional.empty();
        }
        String beneficiary = first(value(document, InvoiceLayout.path(account, "BT-85")),
                value(document, SemanticPath.of("/BG-10/BT-59")),
                value(document, SemanticPath.of("/BG-4/BT-27")));
        if (beneficiary.isEmpty()) {
            return Optional.empty();
        }
        String number = value(document, SemanticPath.of("/BT-1"));
        String remittance = first(value(document, InvoiceLayout.path(instruction, "BT-83")),
                number);
        String bic = value(document, InvoiceLayout.path(account, "BT-86"));
        List<Remark> remarks = new ArrayList<>();
        if (!bic.isEmpty() && !BIC.matcher(bic).matches()) {
            // Being a BIC is a condition of the guideline like the others, and a payload
            // whose fifth element is not one is a payload a banking application refuses.
            // The element may be empty in this version, so the code loses it and keeps
            // the rest: the IBAN is what the payment is made into.
            bic = "";
            remarks.add(Remark.BIC_LEFT_OUT);
        }
        if (codePoints(beneficiary) > NAME_LIMIT) {
            beneficiary = cut(beneficiary, NAME_LIMIT);
            remarks.add(Remark.BENEFICIARY_CUT);
        }
        if (codePoints(remittance) > REMITTANCE_LIMIT) {
            // A reference cut in the middle of a word is the reference the payer's bank
            // transmits and the seller cannot reconcile on. The invoice number is the
            // one they do reconcile on, and it is what the code carries instead.
            if (number.isEmpty() || codePoints(number) > REMITTANCE_LIMIT) {
                return Optional.empty();
            }
            remittance = number;
            remarks.add(Remark.REMITTANCE_REPLACED);
        }
        return payload(bic, beneficiary, iban, amount, remittance)
                .flatMap(payload -> encoded(payload, List.copyOf(remarks), instruction,
                        account));
    }

    /**
     * Returns the payload: the elements of the guideline, one per line, with the trailing
     * empty ones left off. An element that carries a line break of its own would be two
     * elements to whoever reads the code back, so it is no payload at all.
     *
     * <p>The purpose and the structured creditor reference are empty here. The guideline
     * allows a structured reference or an unstructured text and never both, and what this
     * document states is BT-83, which is a text.
     */
    private static Optional<String> payload(String bic, String beneficiary, String iban,
                                            String amount, String remittance) {
        List<String> elements = new ArrayList<>(List.of(SERVICE_TAG, VERSION, CHARACTER_SET,
                IDENTIFICATION, bic, beneficiary, iban, EURO + amount, "", "", remittance));
        for (String element : elements) {
            if (element.indexOf('\n') >= 0 || element.indexOf('\r') >= 0) {
                return Optional.empty();
            }
        }
        while (!elements.isEmpty() && elements.get(elements.size() - 1).isEmpty()) {
            elements.remove(elements.size() - 1);
        }
        return Optional.of(String.join("\n", elements));
    }

    /**
     * Encodes a payload, or returns nothing where the guideline does not allow it: a
     * payload of more bytes than it permits, or a symbol past the version it bounds the
     * code at.
     */
    private static Optional<PaymentCode> encoded(String payload, List<Remark> remarks,
                                                 SemanticPath instruction,
                                                 SemanticPath account) {
        if (payload.getBytes(StandardCharsets.UTF_8).length > PAYLOAD_LIMIT) {
            return Optional.empty();
        }
        QRCode code;
        try {
            code = Encoder.encode(payload, ErrorCorrectionLevel.M, hints(payload));
        } catch (WriterException e) {
            return Optional.empty();
        }
        int version = code.getVersion().getVersionNumber();
        if (version > MAX_VERSION) {
            return Optional.empty();
        }
        ByteMatrix matrix = code.getMatrix();
        boolean[][] cells = new boolean[matrix.getHeight()][matrix.getWidth()];
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int column = 0; column < matrix.getWidth(); column++) {
                cells[row][column] = matrix.get(column, row) == 1;
            }
        }
        return Optional.of(
                new PaymentCode(payload, cells, version, remarks, instruction, account));
    }

    /**
     * Returns what the encoder is told about the character set of a payload.
     *
     * <p>A payload of nothing but ASCII is told nothing: its bytes are the same under the
     * encoder's own default and under UTF-8, and a symbol that carries no indication of a
     * character set is the symbol every reader of these codes was built for. A payload
     * with a character above that — an umlaut in the name of a beneficiary — is told
     * UTF-8, which the encoder then writes into the symbol as the extended channel the
     * standard has for it, and which is the character set the payload declares of itself
     * in its third element either way.
     */
    private static Map<EncodeHintType, Object> hints(String payload) {
        for (int i = 0; i < payload.length(); i++) {
            if (payload.charAt(i) > 0x7f) {
                Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
                hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
                return hints;
            }
        }
        return Map.of();
    }

    /** Returns the text of the payload, which is what a banking application reads. */
    String payload() {
        return payload;
    }

    /**
     * Returns the modules of the symbol, row zero at the top, without the quiet zone.
     *
     * @return the matrix, a copy the caller may keep
     */
    boolean[][] cells() {
        boolean[][] copy = new boolean[cells.length][];
        for (int row = 0; row < cells.length; row++) {
            copy[row] = cells[row].clone();
        }
        return copy;
    }

    /** Returns how many modules the symbol is wide, without the quiet zone. */
    int modules() {
        return cells.length;
    }

    /** Returns the version of the symbol, which the guideline bounds at 13. */
    int version() {
        return version;
    }

    /** Returns where what this code carries differs from what the letter prints. */
    List<Remark> remarks() {
        return remarks;
    }

    /** Returns the payment instruction this code was read from. */
    SemanticPath instruction() {
        return instruction;
    }

    /** Returns the account of that instruction this code pays into. */
    SemanticPath account() {
        return account;
    }

    /** Returns the content of a value of the document, or nothing where there is none. */
    private static String value(SemanticDocument document, SemanticPath path) {
        return document.value(path).map(SemanticValue::content).orElse("");
    }

    /** Returns the first of these that says something, or nothing where none does. */
    private static String first(String... candidates) {
        for (String candidate : candidates) {
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return "";
    }

    /** Returns how many characters a text is, counting a pair of surrogates as one. */
    private static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    /** Returns the first characters of a text, never cutting a pair of surrogates in two. */
    private static String cut(String text, int limit) {
        return text.substring(0, text.offsetByCodePoints(0, limit));
    }

    /**
     * Tells whether an amount is one the guideline carries: written with a full stop and
     * at most two decimal places, and between one cent and the bound it names.
     */
    private static boolean isAmount(String amount) {
        if (!AMOUNT.matcher(amount).matches()) {
            return false;
        }
        BigDecimal value = new BigDecimal(amount);
        return value.compareTo(MIN_AMOUNT) >= 0 && value.compareTo(MAX_AMOUNT) <= 0;
    }

    /**
     * Tells whether an account identifier is a well-formed IBAN: the shape of ISO 13616
     * and the check digits of it, which are the remainder of the rearranged number on
     * division by 97.
     *
     * <p>The check is here rather than in a validation rule because the question it
     * answers is about this code and not about the invoice: an identifier that fails it
     * is one a banking application would refuse to pay into, and the letter prints the
     * account either way.
     *
     * @param iban the identifier, without the spaces a printed one is grouped by
     * @return whether it is well formed
     */
    static boolean isIban(String iban) {
        if (!IBAN.matcher(iban).matches()) {
            return false;
        }
        int remainder = 0;
        for (int i = 0; i < iban.length(); i++) {
            char c = iban.charAt((i + 4) % iban.length());
            int digit = c >= 'A' ? c - LETTER_OFFSET : c - '0';
            remainder = (remainder * (digit > 9 ? 100 : 10) + digit) % MODULUS;
        }
        return remainder == 1;
    }
}
