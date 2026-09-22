package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The payment code on the page: that it is there, that it reads, and that it reads what
 * the document says.
 *
 * <p>A code is only worth the scan it survives, so the claim here is made the way a payer
 * makes it: the page is rasterised and handed to a decoder, and what comes back is
 * compared with the payload byte for byte ({@link Pdf#codes(byte[])}). Everything else —
 * which layout draws it, what turns it off, what the closing heading says about it — is
 * read out of the text of the pages.
 */
class PaymentCodeRenderingTest {

    /** The document most of the cases render: it states a SEPA credit transfer. */
    private static SemanticDocument invoice() {
        return Corpus.example("standard-invoice");
    }

    /** Returns the payload the code of a document carries. */
    private static String payload(SemanticDocument document) {
        return PaymentCode.of(document)
                .orElseThrow(() -> new AssertionError("the document states no credit transfer"))
                .payload();
    }

    // ---------------------------------------------------------------- it reads

    /**
     * The lead example, rendered as a letter and scanned off the page: one code, and its
     * text is the payload the guideline asks for, to the byte.
     */
    @Test
    void theCodeOnThePageDecodesToThePayloadOfTheDocument() {
        for (RenderLanguage language : RenderLanguage.values()) {
            byte[] letter = new PdfRenderer().render(invoice(),
                    RenderOptions.in(language).layout(Layout.LETTER));

            assertEquals(List.of(payload(invoice())), Pdf.codes(letter),
                    "the code of the letter in " + language + ", read back off the page");
        }
    }

    /** And on the branded template of the repository, where a letterhead is under it. */
    @Test
    void theCodeReadsOnTheExampleTemplateToo() {
        byte[] letter = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().with(Templates.example("letter.json")));

        assertEquals(List.of(payload(invoice())), Pdf.codes(letter),
                "a letterhead under the page does not stop the code being read");
    }

    /**
     * Ten instances of the conformance corpus that state a credit transfer, rendered as
     * letters and scanned. These are invoices this project did not write: their names,
     * their accounts and their remittance texts are what they are, and the code has to
     * carry them.
     */
    @Test
    void tenInvoicesOfTheCorpusCarryACodeThatReads() {
        List<String> instances = withACreditTransfer();

        assertEquals(10, instances.size(), "ten instances of the corpus state one");
        for (String instance : instances) {
            SemanticDocument document = Corpus.instance(instance);
            byte[] letter = new PdfRenderer().render(document,
                    RenderOptions.defaults().layout(Layout.LETTER));

            assertEquals(List.of(payload(document)), Pdf.codes(letter),
                    instance + ": the code of the letter reads as the payload");
        }
    }

    /** Returns the first ten instances of the corpus whose letter carries a code. */
    private static List<String> withACreditTransfer() {
        List<String> found = new ArrayList<>();
        for (String instance : Corpus.instances()) {
            if (PaymentCode.of(Corpus.instance(instance)).isPresent()) {
                found.add(instance);
            }
            if (found.size() == 10) {
                return found;
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- where it is drawn

    /** The letter draws it; the generic layout, which is the shape of the model, does not. */
    @Test
    void theLetterDrawsItAndTheGenericLayoutNever() {
        assertEquals(List.of(), Pdf.codes(new PdfRenderer().render(invoice())),
                "the generic layout draws none");
        assertEquals(1, Pdf.codes(new PdfRenderer().render(invoice(),
                        RenderOptions.defaults().layout(Layout.LETTER))).size(),
                "the letter draws exactly one");
    }

    /** It stands in the payment block, beside the account data and not under the totals. */
    @Test
    void itStandsInThePaymentBlock() {
        byte[] letter = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().layout(Layout.LETTER));
        int page = pageOfTheCode(letter);

        String text = Pdf.textOfPage(letter, page);
        assertTrue(text.contains(Word.PAYMENT.in(RenderLanguage.GERMAN)),
                "the code is on the page the payment block opens on");
        assertTrue(Pdf.flat(text).contains(Word.PAYMENT_CODE.in(RenderLanguage.GERMAN)),
                "and its name stands under it: " + Pdf.flat(text));
    }

    /** Returns the page the code stands on, counted from one. */
    private static int pageOfTheCode(byte[] letter) {
        for (int page = 1; page <= Pdf.pages(letter); page++) {
            if (Pdf.textOfPage(letter, page).contains(
                    Word.PAYMENT_CODE.in(RenderLanguage.GERMAN))) {
                return page;
            }
        }
        throw new AssertionError("no page carries the name of the code");
    }

    /** The caption is the word each language knows the code by. */
    @Test
    void theCaptionIsTheNameTheLanguageKnowsItBy() {
        assertEquals("GiroCode", Word.PAYMENT_CODE.in(RenderLanguage.GERMAN));
        assertEquals("EPC QR code", Word.PAYMENT_CODE.in(RenderLanguage.ENGLISH));
        assertTrue(Pdf.flat(new PdfRenderer().render(invoice(),
                        RenderOptions.in(RenderLanguage.ENGLISH).layout(Layout.LETTER)))
                        .contains("EPC QR code"),
                "the English letter names it as the guideline does");
    }

    /** The section under the payment block begins below the code and not over it. */
    @Test
    void theSectionAfterThePaymentBlockClearsTheCode() {
        byte[] letter = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().layout(Layout.LETTER));
        int page = pageOfTheCode(letter);
        float caption = Float.MAX_VALUE;
        float heading = 0;
        for (Pdf.Run run : Pdf.runs(letter, page)) {
            if (run.text().equals(Word.PAYMENT_CODE.in(RenderLanguage.GERMAN))) {
                caption = run.baseline();
            } else if (run.text().equals(Word.FURTHER_DETAILS.in(RenderLanguage.GERMAN))) {
                heading = run.top();
            }
        }

        assertTrue(heading > 0, "the closing heading is on the page of the code");
        assertTrue(heading < caption,
                "and it begins below the code, whose caption sits at " + caption
                        + " and whose heading tops at " + heading);
    }

    // ---------------------------------------------------------------- which account it pays

    /**
     * Where the document states more than one account, the caption says which of them the
     * code pays into, in the words the block beside it numbers them in.
     *
     * <p>The corpus carries the case: an instance with two credit transfer accounts. A
     * reader who scans a code under a block of three IBANs and is told nothing cannot
     * tell what they are paying.
     */
    @Test
    void theCaptionNamesTheAccountWhereTheDocumentStatesMoreThanOne() {
        SemanticDocument document = Corpus.instance(
                "technical-cases/cius/01.01_comprehensive_test_ubl.xml");

        byte[] letter = new PdfRenderer().render(document,
                RenderOptions.defaults().layout(Layout.LETTER));

        String flat = Pdf.flat(Pdf.text(letter));
        assertTrue(flat.contains("Überweisung 1 · GiroCode"),
                "the caption names the account the code pays into: " + flat);
        assertTrue(flat.contains("Überweisung 2 · IBAN"),
                "and the block numbers the accounts the same way");
        assertTrue(payload(document).contains("DE79000000001234567890"),
                "the code carries the first of them");
    }

    /**
     * Where the document states two payment instructions of one account each, the caption
     * names the instruction the code pays into, and so do the rows of the block.
     *
     * <p>Nothing repeats inside either instruction, so nothing inside one is numbered, and
     * the page carried two rows called <i>IBAN</i> under a code that said which of them it
     * paid into by saying nothing. The block is told apart against the whole of itself
     * now, which is what a reader sees.
     *
     * <p>This is the defensive case and is meant as one: BG-16 is 0..1 in every registry
     * of this build, so the specimen is a document the model refuses and no command of this
     * tool produces or accepts. A layout draws what it is handed — a caller of the library
     * hands it documents this tool never saw — and what it draws for this one is pinned
     * here rather than left to chance.
     */
    @Test
    void theCaptionNamesTheInstructionWhereTheDocumentStatesTwo() {
        SemanticDocument document = Documents.withTwoCreditTransferInstructions();

        for (RenderLanguage language : RenderLanguage.values()) {
            byte[] letter = new PdfRenderer().render(document, RenderOptions.in(language)
                    .layout(Layout.LETTER));

            String flat = Pdf.flat(Pdf.text(letter));
            String instruction = Word.PAYMENT_INSTRUCTION.in(language);
            assertTrue(flat.contains(instruction + " 1 · " + Word.PAYMENT_CODE.in(language)),
                    "the caption names the instruction the code pays into: " + flat);
            for (int number = 1; number <= 2; number++) {
                assertTrue(flat.contains(instruction + " " + number + " · "
                                + Word.ACCOUNT_IBAN.in(language)),
                        "and the block numbers the accounts the same way: " + flat);
            }
            assertTrue(payload(document).contains("DE89370400440532013000"),
                    "and the code carries the account of the instruction it names");
        }
    }

    /** With one account there is no number to give, and the caption is the name alone. */
    @Test
    void theCaptionIsTheNameAloneWhereThereIsOneAccount() {
        byte[] letter = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().layout(Layout.LETTER));

        List<String> captions = new ArrayList<>();
        for (Pdf.Run run : Pdf.runs(letter, pageOfTheCode(letter))) {
            if (run.text().contains(Word.PAYMENT_CODE.in(RenderLanguage.GERMAN))) {
                captions.add(run.text());
            }
        }
        assertEquals(List.of("GiroCode"), captions,
                "one caption, and it is the name of the code");
    }

    // ---------------------------------------------------------------- the width beside it

    /**
     * The code narrows the rows that stand beside it and nothing else: a value under it
     * runs on to the right margin, as it does in a letter that draws no code at all.
     *
     * <p>A width held back for a symbol that ended thirty millimetres higher up is a name
     * wrapped onto a second line for no reason, on every row of the block and on every
     * page after it. The column the values begin in does not move between the two, so the
     * block still reads as one block.
     */
    @Test
    void theRowsUnderTheCodeRunOnToTheRightMargin() {
        SemanticDocument document = Documents.withSeveralCreditTransferAccounts(6);
        RenderOptions options = RenderOptions.defaults().layout(Layout.LETTER);

        byte[] with = new PdfRenderer().render(document, options);
        byte[] without = new PdfRenderer().render(document, options.withPaymentCode(false));

        List<Pdf.Run> holders = holderLines(with);
        assertEquals(6, holders.size(), "one first line per account: " + holders);
        float beside = Float.MAX_VALUE;
        float under = 0;
        for (Pdf.Run run : holders) {
            beside = Math.min(beside, run.right());
            under = Math.max(under, run.right());
        }
        assertTrue(under > beside + 50f,
                "a value under the code runs further right than one beside it: " + beside
                        + " against " + under);
        assertEquals(1, lefts(holders).size(),
                "and every value begins in the same column: " + lefts(holders));
        assertEquals(1, lefts(holderLines(without)).size(),
                "as it does where no code is drawn");
    }

    /** Returns the first line of every account holder of a rendering, with its box. */
    private static List<Pdf.Run> holderLines(byte[] letter) {
        List<Pdf.Run> found = new ArrayList<>();
        for (int page = 1; page <= Pdf.pages(letter); page++) {
            for (Pdf.Run run : Pdf.runs(letter, page)) {
                if (run.text().startsWith("Kontoinhaber Beispiel")) {
                    found.add(run);
                }
            }
        }
        return found;
    }

    /** Returns the left edges these runs begin at, to a tenth of a point. */
    private static Set<Long> lefts(List<Pdf.Run> runs) {
        Set<Long> found = new TreeSet<>();
        for (Pdf.Run run : runs) {
            found.add((long) Math.round(run.left() * 10));
        }
        return found;
    }

    // ---------------------------------------------------------------- what turns it off

    /** A caller may leave it out, and a caller who asks for it gets the letter back. */
    @Test
    void aCallerCanLeaveItOut() {
        byte[] without = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().layout(Layout.LETTER).withPaymentCode(false));

        assertEquals(List.of(), Pdf.codes(without), "no code was drawn");
        assertFalse(Pdf.flat(Pdf.text(without))
                        .contains(Word.PAYMENT_CODE.in(RenderLanguage.GERMAN)),
                "and nothing names one");
        assertTrue(Pdf.shows(without, "DE89 3704 0044 0532 0130 00"),
                "the account is printed either way, which is what a reader pays from");
    }

    /** A template may leave it out, and the caller's own word wins over the template. */
    @Test
    void aTemplateMayLeaveItOutAndTheCallerOverrulesIt() {
        RenderTemplate silent = Templates.of("{\"template\": \"esj-render-template/0.1\","
                + " \"layout\": \"letter\", \"letter\": {\"paymentCode\": false}}");

        assertEquals(List.of(), Pdf.codes(new PdfRenderer().render(invoice(),
                        RenderOptions.defaults().with(silent))),
                "the template asked for no code");
        assertEquals(1, Pdf.codes(new PdfRenderer().render(invoice(),
                        RenderOptions.defaults().with(silent).withPaymentCode(true))).size(),
                "and the caller asked for one anyway");
    }

    /** A document that states no credit transfer gets no code and no word about it. */
    @Test
    void aDocumentWithoutACreditTransferGetsNothingAndSaysNothing() {
        SemanticDocument document = Corpus.example("minimal");

        byte[] letter = new PdfRenderer().render(document,
                RenderOptions.defaults().layout(Layout.LETTER));

        assertEquals(Optional.empty(), PaymentCode.of(document), "there is no code to draw");
        assertEquals(List.of(), Pdf.codes(letter), "so none is drawn");
        assertFalse(Pdf.flat(Pdf.text(letter))
                        .contains(Word.PAYMENT_CODE.in(RenderLanguage.GERMAN)),
                "and the letter says nothing about its absence");
    }

    // ---------------------------------------------------------------- what it says of itself

    /** Where the code carries something other than the page does, the letter says so. */
    @Test
    void anElementTheCodeCouldNotCarryIsSaidUnderTheClosingHeading() {
        SemanticDocument document = Documents.withARemittanceTextLongerThanACodeCarries();

        byte[] letter = new PdfRenderer().render(document,
                RenderOptions.defaults().layout(Layout.LETTER));

        assertEquals(List.of(PaymentCode.Remark.REMITTANCE_REPLACED),
                PaymentCode.of(document).orElseThrow().remarks(),
                "the invoice number took the place of the text");
        assertTrue(Pdf.flat(Pdf.text(letter)).contains(Pdf.flat(
                        Word.PAYMENT_CODE_REMITTANCE_REPLACED.in(RenderLanguage.GERMAN))),
                "and the letter says so under its closing heading");
        assertEquals(List.of(payload(document)), Pdf.codes(letter),
                "the code carries what the payload says it does");
    }

    // ---------------------------------------------------------------- the file it is in

    /** Two renderings of one document are the same bytes, code and all. */
    @Test
    void theRenderingWithACodeIsTheSameBytesTwice() {
        RenderOptions options = RenderOptions.defaults().layout(Layout.LETTER);

        assertArrayEquals(new PdfRenderer().render(invoice(), options),
                new PdfRenderer().render(invoice(), options),
                "a code is drawn from the document and from nothing else");
    }

    /**
     * The code is drawn and not placed: the page carries no image object, which is what
     * keeps the PDF/A claim of this module the claim {@link PdfaTest} makes about a
     * rendering without one.
     */
    @Test
    void theCodeIsDrawnRatherThanPlaced() {
        byte[] letter = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().layout(Layout.LETTER));

        assertFalse(new String(letter, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .contains("/Subtype /Image"),
                "no image object entered the file");
    }
}
