package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * What the tool does with a hybrid invoice whose name tree lists two files under one name.
 *
 * <p>A reader that loads the tree into a map keeps one of the two and a reader that walks
 * it takes the first, so a file this tool judged may be booked by another reader as the
 * other file. The three placements a security review found — the second file in front of
 * the first and outside the associated files array, behind it, inside the array — and a
 * key split across two nodes of the tree are refused with the code of a container that
 * names more than one invoice; a caller who picks one by position gets a verdict about that
 * one and a container that is reported as unsound. A duplicated ESJ document leaves the
 * invoice alone and makes the container unsound.
 */
class DuplicateNamesCommandsTest {

    private static final String CII =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml";

    /** A second invoice of the corpus, which is a different document from the first. */
    private static final String OTHER_CII =
            "conformance/kosit/business-cases/standard/01.02a-INVOICE_uncefact.xml";

    private static final String NAME = TestPdfs.FACTUR_X;

    private static final String CODE = "PDF-EMBEDDED-DUPLICATE-NAME";

    @Test
    void aShadowInFrontOfTheInvoiceAndOutsideTheArrayIsRefused() {
        byte[] pdf = TestPdfs.builder()
                .attach(shadow(false))
                .attach(NAME, Fixtures.bytes(CII))
                .keys(NAME, NAME)
                .xmp(TestPdfs.xmp(NAME, TestPdfs.XRECHNUNG))
                .build();

        refusedAndNamed(pdf, "1", "2");
    }

    @Test
    void aShadowBehindTheInvoiceIsRefused() {
        byte[] pdf = TestPdfs.builder()
                .attach(NAME, Fixtures.bytes(CII))
                .attach(shadow(false))
                .keys(NAME, NAME)
                .xmp(TestPdfs.xmp(NAME, TestPdfs.XRECHNUNG))
                .build();

        refusedAndNamed(pdf, "1", "2");
    }

    @Test
    void aShadowInsideTheArrayIsRefused() {
        byte[] pdf = TestPdfs.builder()
                .attach(shadow(true))
                .attach(NAME, Fixtures.bytes(CII))
                .keys(NAME, NAME)
                .xmp(TestPdfs.xmp(NAME, TestPdfs.XRECHNUNG))
                .build();

        refusedAndNamed(pdf, "1", "2");
    }

    @Test
    void aKeyListedInTwoNodesIsRefused() {
        byte[] terms = "delivery terms".getBytes(StandardCharsets.UTF_8);
        byte[] pdf = TestPdfs.builder()
                .attach(shadow(false))
                .attach(new TestPdfs.Attachment("terms.txt", terms, "text/plain",
                        "Supplement", true))
                .attach(NAME, Fixtures.bytes(CII))
                .keys(NAME, "terms.txt", NAME)
                .leaves(2, 1)
                .xmp(TestPdfs.xmp(NAME, TestPdfs.XRECHNUNG))
                .build();

        refusedAndNamed(pdf, "1", "3");
    }

    @Test
    void aDuplicatedEsjDocumentMakesTheContainerUnsoundAndCannotBeExtractedByName() {
        byte[] pdf = TestPdfs.builder()
                .attach(NAME, Fixtures.bytes(CII))
                .attach(esj("{\"shadow\":true}"))
                .attach(esj("{\"original\":true}"))
                .keys(NAME, "invoice.esj.json", "invoice.esj.json")
                .xmp(TestPdfs.xmp(NAME, TestPdfs.XRECHNUNG))
                .build();

        Cli.Run validate = Cli.run(pdf, "validate", "-");
        assertEquals(ExitCode.VALIDATION, validate.exitCode(), validate.text() + validate.err());
        String text = validate.text();
        assertTrue(text.contains(CODE + " [error]"), text);
        assertTrue(text.contains("PDF-EMBEDDED-SEVERAL-ESJ [error]"), text);
        assertTrue(text.contains(" 2 \"invoice.esj.json\" (ESJ document, not the invoice, object "),
                text);
        assertTrue(text.contains(" 3 \"invoice.esj.json\" (ESJ document, not the invoice, object "),
                text);
        assertTrue(text.contains("Container:        INVALID"), text);
        assertTrue(text.contains("Invoice:          VALID"), text);

        Cli.Run list = Cli.run(pdf, "extract", "-", "--list");
        assertEquals(ExitCode.SUCCESS, list.exitCode(), list.err());
        assertTrue(list.text().contains("Attachments:  3"), list.text());
        assertTrue(list.text().contains("one of 2 files the name tree lists under"
                + " \"invoice.esj.json\""), list.text());

        Cli.Run named = Cli.run(pdf, "extract", "-", "--attachment", "invoice.esj.json");
        assertEquals(ExitCode.INPUT, named.exitCode(), named.err());
        assertTrue(named.err().contains("does not say which of them is meant"), named.err());

        Cli.Run invoice = Cli.run(pdf, "extract", "-");
        assertEquals(ExitCode.SUCCESS, invoice.exitCode(), invoice.err());
    }

    /**
     * Asserts what every placement comes to on the command line: {@code validate},
     * {@code extract} and {@code inspect} refuse without a choice and list both entries,
     * the name cannot pick one, {@code extract --list} shows both and says that the tree
     * gives them one name, and a position picks one, with the container reported unsound.
     */
    private static void refusedAndNamed(byte[] pdf, String first, String second) {
        Cli.Run validate = Cli.run(pdf, "validate", "-");
        assertEquals(ExitCode.INPUT, validate.exitCode(), validate.text() + validate.err());
        String err = validate.err();
        assertTrue(err.contains("attachments that could be the electronic invoice"), err);
        assertTrue(err.contains("\n  " + first + "  \"factur-x.xml\" (CII,"), err);
        assertTrue(err.contains("\n  " + second + "  \"factur-x.xml\" (CII,"), err);
        assertTrue(err.contains("the embedded files name tree lists under one name,"
                + " \"factur-x.xml\", the attachments at positions " + first + " and " + second
                + ", so that name cannot say which of them is meant"), err);

        Cli.Run extract = Cli.run(pdf, "extract", "-");
        assertEquals(ExitCode.INPUT, extract.exitCode(), extract.err());

        Cli.Run inspect = Cli.run(pdf, "inspect", "-");
        assertEquals(ExitCode.INPUT, inspect.exitCode(), inspect.err());
        assertTrue(inspect.text().contains("one of 2 files the name tree lists under"
                + " \"factur-x.xml\""), inspect.text());

        Cli.Run named = Cli.run(pdf, "validate", "-", "--attachment", NAME);
        assertEquals(ExitCode.INPUT, named.exitCode(), named.err());
        assertTrue(named.err().contains("does not say which of them is meant"), named.err());

        Cli.Run list = Cli.run(pdf, "extract", "-", "--list");
        assertEquals(ExitCode.SUCCESS, list.exitCode(), list.err());
        String listing = list.text();
        assertTrue(listing.contains("\n  " + first + "  \"factur-x.xml\" (CII,"), listing);
        assertTrue(listing.contains("\n  " + second + "  \"factur-x.xml\" (CII,"), listing);
        assertEquals(2, count(listing, "one of 2 files the name tree lists under"
                + " \"factur-x.xml\""), listing);

        Cli.Run picked = Cli.run(pdf, "validate", "-", "--attachment-index", second);
        assertEquals(ExitCode.VALIDATION, picked.exitCode(), picked.text() + picked.err());
        String text = picked.text();
        assertTrue(text.contains(CODE + " [error] the embedded files name tree lists 2"
                + " different files under the name \"factur-x.xml\""), text);
        assertTrue(text.contains(" " + first + " \"factur-x.xml\" (CII, object "), text);
        assertTrue(text.contains(" " + second + " \"factur-x.xml\" (CII, object "), text);
        assertTrue(text.contains("Container:        INVALID"), text);
    }

    private static int count(String text, String fragment) {
        int count = 0;
        for (int at = text.indexOf(fragment); at >= 0; at = text.indexOf(fragment, at + 1)) {
            count++;
        }
        return count;
    }

    /** A second invoice under the invoice's name, declared as the invoice of the file. */
    private static TestPdfs.Attachment shadow(boolean associated) {
        return new TestPdfs.Attachment(NAME, Fixtures.bytes(OTHER_CII), TestPdfs.XML,
                TestPdfs.ALTERNATIVE, associated);
    }

    /** An attachment wearing the label of the ESJ document. */
    private static TestPdfs.Attachment esj(String json) {
        return new TestPdfs.Attachment("invoice.esj.json", json.getBytes(StandardCharsets.UTF_8),
                "application/json", "Supplement", true);
    }
}
