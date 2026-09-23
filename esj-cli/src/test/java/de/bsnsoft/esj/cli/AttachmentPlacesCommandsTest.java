package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A second invoice a page refers to rather than the name tree.
 *
 * <p>A file attachment annotation and the associated files array of a page are places a
 * viewer and a reader that collects attachments take a file from, so an invoice there is a
 * second candidate beside the one the tree lists: refused without a choice, with its place
 * named, and listed by {@code extract --list}.
 */
class AttachmentPlacesCommandsTest {

    private static final String CII =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml";

    private static final String OTHER_CII =
            "conformance/kosit/business-cases/standard/01.02a-INVOICE_uncefact.xml";

    private static final String NAME = TestPdfs.FACTUR_X;

    @Test
    void aFileAttachmentAnnotationCarryingASecondInvoiceIsRefused() {
        byte[] pdf = TestPdfs.builder()
                .attach(NAME, Fixtures.bytes(CII))
                .annotate(second())
                .xmp(TestPdfs.xmp(NAME, TestPdfs.XRECHNUNG))
                .build();

        refusedAndPlaced(pdf, "not in the name tree: a file attachment annotation on page 1");
    }

    @Test
    void thePageAssociatedFilesArrayCarryingASecondInvoiceIsRefused() {
        byte[] pdf = TestPdfs.builder()
                .attach(NAME, Fixtures.bytes(CII))
                .associateWithThePage(second())
                .xmp(TestPdfs.xmp(NAME, TestPdfs.XRECHNUNG))
                .build();

        refusedAndPlaced(pdf, "not in the name tree: the associated files array of page 1");
    }

    private static void refusedAndPlaced(byte[] pdf, String place) {
        Cli.Run validate = Cli.run(pdf, "validate", "-");
        assertEquals(ExitCode.INPUT, validate.exitCode(), validate.text() + validate.err());
        String err = validate.err();
        assertTrue(err.contains("2 attachments that could be the electronic invoice"), err);
        assertTrue(err.contains("\n  1  \"factur-x.xml\" (CII,"), err);
        assertTrue(err.contains("\n  2  \"factur-x.xml\" (CII,"), err);
        assertTrue(err.contains(place), err);

        Cli.Run extract = Cli.run(pdf, "extract", "-");
        assertEquals(ExitCode.INPUT, extract.exitCode(), extract.err());

        Cli.Run list = Cli.run(pdf, "extract", "-", "--list");
        assertEquals(ExitCode.SUCCESS, list.exitCode(), list.err());
        assertTrue(list.text().contains("Attachments:  2"), list.text());
        assertTrue(list.text().contains(place), list.text());

        Cli.Run named = Cli.run(pdf, "validate", "-", "--attachment", NAME);
        assertEquals(ExitCode.INPUT, named.exitCode(), named.err());

        Cli.Run first = Cli.run(pdf, "validate", "-", "--attachment-index", "1");
        assertEquals(ExitCode.SUCCESS, first.exitCode(), first.text() + first.err());
        assertTrue(first.text().contains("PDF-EMBEDDED-NOT-IN-TREE [warning] the attachment 2"
                + " \"factur-x.xml\" (CII, object "), first.text());
        assertTrue(first.text().contains("PDF-EMBEDDED-SEVERAL [warning]"), first.text());
    }

    /** A second invoice under the invoice's own name, declared as the document. */
    private static TestPdfs.Attachment second() {
        return new TestPdfs.Attachment(NAME, Fixtures.bytes(OTHER_CII), TestPdfs.XML,
                TestPdfs.ALTERNATIVE, false);
    }
}
