package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.Test;

/** What the bytes of an attachment decide, and what a name is not allowed to decide. */
class InvoiceAttachmentsTest {

    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";
    private static final String UBL = "business-cases/standard/01.01a-INVOICE_ubl.xml";

    /** A ZUGFeRD 1.0 invoice, whose root element belongs to CII D14B. */
    private static final byte[] ZUGFERD_1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CrossIndustryDocument
                xmlns:rsm="urn:ferd:CrossIndustryDocument:invoice:1p0">
              <rsm:SpecifiedExchangedDocumentContext/>
            </rsm:CrossIndustryDocument>
            """.getBytes(StandardCharsets.UTF_8);

    /** An XML document that is no invoice. */
    private static final byte[] ORDER =
            "<order xmlns=\"urn:example:orders\"><id>1</id></order>"
                    .getBytes(StandardCharsets.UTF_8);

    /** Bytes that are no XML: the file signature of a PNG image. */
    private static final byte[] IMAGE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Test
    void classifiesByTheRootElementAndNotByTheName() {
        // The conventional name of a cross industry invoice, holding a UBL invoice.
        byte[] pdf = Pdfs.facturX(Pdfs.FACTUR_X, Conformance.instance(UBL), "EN 16931");

        LocatedAttachment attachment = located(pdf).single();

        assertEquals(AttachmentKind.UBL_INVOICE, attachment.kind());
        assertEquals(Pdfs.FACTUR_X, attachment.name());
        assertTrue(attachment.root().orElseThrow().endsWith("}Invoice"), attachment.root().get());
    }

    @Test
    void findsAnInvoiceUnderAnUnconventionalName() {
        byte[] pdf = Pdfs.facturX("rechnungsdaten.xml", Conformance.instance(CII), "EN 16931");

        LocatedAttachment attachment = located(pdf).single();

        assertEquals(AttachmentKind.CII_INVOICE, attachment.kind());
        assertEquals("rechnungsdaten.xml", attachment.name());
    }

    @Test
    void leavesEveryAttachmentThatIsNoInvoiceAlone() {
        byte[] pdf = Pdfs.builder()
                .attach("order.xml", ORDER)
                .attach("logo.png", IMAGE)
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .build();

        InvoiceAttachments located = located(pdf);

        assertEquals(3, located.all().size());
        assertEquals(List.of(AttachmentKind.OTHER_XML, AttachmentKind.NOT_XML,
                        AttachmentKind.CII_INVOICE),
                located.all().stream().map(LocatedAttachment::kind).toList());
        assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
    }

    @Test
    void neverChoosesBetweenTwoInvoices() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .attach("xrechnung.xml", Conformance.instance(UBL))
                .build();

        InvoiceAttachments located = located(pdf);
        AmbiguousInvoiceAttachmentException thrown =
                assertThrows(AmbiguousInvoiceAttachmentException.class, located::single);

        assertEquals(2, thrown.candidates().size());
        assertTrue(thrown.getMessage().contains(Pdfs.FACTUR_X), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("xrechnung.xml"), thrown.getMessage());
        assertEquals(List.of(Pdfs.FACTUR_X, "xrechnung.xml"),
                located.invoices().stream().map(LocatedAttachment::name).toList());
    }

    @Test
    void doesNotLetAnEnclosureWithALongPrologHideTheInvoiceItTravelsWith() {
        // The window is a bound of the reading party, and an ordinary enclosure — terms
        // of delivery, a note, a timesheet — is written by whoever writes the invoice.
        // Counting one as a candidate because its prolog is long makes a container that
        // says exactly what it carries unreadable, and lets anyone who can add a file to
        // a hybrid invoice deny its recipient a verdict.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .attach(new Pdfs.Attachment("terms.xml", padded(Conformance.instance(CII)),
                        Pdfs.XML, "Supplement", false, 0, false, null))
                .build();

        InvoiceAttachments located = located(pdf);

        assertEquals(AttachmentKind.UNDETERMINED, located.all().get(1).kind());
        assertEquals(1, located.candidates().size());
        assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
    }

    @Test
    void setsTheSupplementAsideWhateverTheInvoiceDeclaresAboutItself() {
        // The rule is about the supplement and not about the invoice. A hybrid invoice
        // whose XML carries no relationship at all is a file this tool reports on and
        // still reads, so an enclosure beside it may not turn it into an ambiguity.
        byte[] pdf = Pdfs.builder()
                .attach(new Pdfs.Attachment(Pdfs.FACTUR_X, Conformance.instance(CII),
                        Pdfs.XML, null, true, 0, false, null))
                .attach(new Pdfs.Attachment("terms.xml", padded(Conformance.instance(CII)),
                        Pdfs.XML, "Supplement", false, 0, false, null))
                .build();

        InvoiceAttachments located = located(pdf);

        assertEquals(AttachmentKind.UNDETERMINED, located.all().get(1).kind());
        assertEquals(1, located.candidates().size());
        assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
    }

    @Test
    void anUnreadableAttachmentTheContainerCallsTheDocumentIsACandidate() {
        // Two attachments the container declares to be what the document is, one of them
        // filtered with something this reader does not decode. Reading the other one and
        // printing a verdict would let a changed stream filter turn a refusal into an
        // answer about the attachment the container did not single out.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.filtered(Pdfs.FACTUR_X,
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0},
                        COSName.DCT_DECODE, Pdfs.ALTERNATIVE))
                .attach(new Pdfs.Attachment("other.xml", Conformance.instance(CII),
                        Pdfs.XML, Pdfs.ALTERNATIVE, true, 0, false, null))
                .build();

        InvoiceAttachments located = located(pdf);

        assertEquals(AttachmentKind.UNREADABLE, located.all().get(0).kind());
        assertEquals(2, located.candidates().size());
        assertThrows(AmbiguousInvoiceAttachmentException.class, located::single);
    }

    @Test
    void anUnreadableSupplementIsNotACandidate() {
        // The case the classification was introduced for: a picture beside the invoice.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .attach(Pdfs.Attachment.filtered("logo.jpg",
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0},
                        COSName.DCT_DECODE, "Supplement"))
                .build();

        InvoiceAttachments located = located(pdf);

        assertEquals(AttachmentKind.UNREADABLE, located.all().get(1).kind());
        assertEquals(1, located.candidates().size());
        assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
    }

    @Test
    void stillRefusesASecondCandidateTheContainerCallsTheDocument() {
        // The same file, with the relationship that says this attachment is what the
        // document is. That one is a candidate whatever its prolog looks like.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .attach(new Pdfs.Attachment("second.xml", padded(Conformance.instance(CII)),
                        Pdfs.XML, Pdfs.ALTERNATIVE, true, 0, false, null))
                .build();

        InvoiceAttachments located = located(pdf);

        assertEquals(2, located.candidates().size());
        assertThrows(AmbiguousInvoiceAttachmentException.class, located::single);
    }

    @Test
    void saysThatAnUnclassifiedAttachmentWasNotEstablishedRatherThanRefused() {
        // "This is not an invoice" is a statement about the file; "what this holds was
        // not established" is a statement about the reader, and the one may not be
        // printed for the other.
        byte[] pdf = Pdfs.builder()
                .attach(new Pdfs.Attachment("terms.xml", padded(Conformance.instance(CII)),
                        Pdfs.XML, "Supplement", false, 0, false, null))
                .build();

        NoInvoiceAttachmentException thrown =
                assertThrows(NoInvoiceAttachmentException.class, () -> located(pdf).single());

        assertEquals(1, thrown.unestablished().size());
        assertTrue(thrown.getMessage().contains("was not established"), thrown.getMessage());
    }

    @Test
    void saysWhenThereIsNoInvoiceAtAll() {
        byte[] pdf = Pdfs.builder().attach("logo.png", IMAGE).build();

        NoInvoiceAttachmentException thrown =
                assertThrows(NoInvoiceAttachmentException.class, () -> located(pdf).single());

        assertEquals(1, thrown.attachments().size());
        assertEquals(AttachmentKind.NOT_XML, thrown.attachments().get(0).kind());
        assertTrue(thrown.unestablished().isEmpty());
    }

    @Test
    void recognizesZugferd1AndDoesNotTakeItForNothing() {
        byte[] pdf = Pdfs.builder().attach(Pdfs.ZUGFERD_1, ZUGFERD_1).build();

        LocatedAttachment attachment = located(pdf).single();

        assertEquals(AttachmentKind.ZUGFERD_1, attachment.kind());
        assertTrue(attachment.kind().isInvoice());
        assertFalse(attachment.kind().isSupported());
    }

    @Test
    void findsAnAttachmentByName() {
        byte[] pdf = Pdfs.builder()
                .attach("order.xml", ORDER)
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .build();

        InvoiceAttachments located = located(pdf);

        assertEquals(AttachmentKind.CII_INVOICE,
                located.named(Pdfs.FACTUR_X).orElseThrow().kind());
        assertTrue(located.named("nothing.xml").isEmpty());
    }

    @Test
    void doesNotCallAnAttachmentWhoseRootElementIsOutOfReachSomethingElse() {
        // The prolog of an XML document has no length limit, so whoever writes the file
        // decides whether the root element is inside the window this reader classifies by.
        // An attachment that is filed as "XML of some other kind" on that basis would let
        // a second invoice hide behind a comment.
        byte[] padded = padded(Conformance.instance(CII));
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .attach("xrechnung.xml", padded)
                .build();

        InvoiceAttachments located = located(pdf);

        assertEquals(AttachmentKind.UNDETERMINED, located.all().get(1).kind());
        assertEquals(2, located.candidates().size());
        AmbiguousInvoiceAttachmentException refused =
                assertThrows(AmbiguousInvoiceAttachmentException.class, located::single);
        assertEquals(2, refused.candidates().size());
    }

    @Test
    void saysSoWhenAnAttachmentWasNotClassified() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .attach("xrechnung.xml", padded(Conformance.instance(CII)))
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            List<String> codes = ContainerChecks.run(container,
                            InvoiceAttachments.locate(container)).stream()
                    .map(ContainerFinding::code)
                    .toList();

            assertTrue(codes.contains("PDF-EMBEDDED-UNDETERMINED"), codes.toString());
        }
    }

    @Test
    void refusesANameThatTwoAttachmentsCarry() {
        // A PDF permits two attachments of one name, so a name is not a selector: the
        // first match is what an attacker who orders the name tree would pick.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .attach(Pdfs.FACTUR_X, Conformance.instance(UBL))
                .build();

        InvoiceAttachments located = located(pdf);

        AmbiguousInvoiceAttachmentException refused = assertThrows(
                AmbiguousInvoiceAttachmentException.class,
                () -> located.named(Pdfs.FACTUR_X));
        assertEquals(2, refused.candidates().size());
        assertEquals(AttachmentKind.CII_INVOICE, located.at(1).orElseThrow().kind());
        assertEquals(AttachmentKind.UBL_INVOICE, located.at(2).orElseThrow().kind());
        assertTrue(located.at(3).isEmpty());
        assertTrue(located.at(0).isEmpty());
    }

    /** Returns a document with a comment longer than the window in front of its root. */
    private static byte[] padded(byte[] invoice) {
        String text = new String(invoice, StandardCharsets.UTF_8);
        int root = text.indexOf("<rsm:");
        return (text.substring(0, root) + "<!--" + "p".repeat(2 * InvoiceAttachments.WINDOW)
                + "-->" + text.substring(root)).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void readsAnAttachmentWrittenInUtf16() {
        byte[] utf16 = new String(Conformance.instance(CII), StandardCharsets.UTF_8)
                .replace("encoding=\"UTF-8\"", "encoding=\"UTF-16\"")
                .getBytes(java.nio.charset.StandardCharsets.UTF_16);
        byte[] pdf = Pdfs.builder().attach("invoice.xml", utf16).build();

        assertEquals(AttachmentKind.CII_INVOICE, located(pdf).single().kind());
    }

    @Test
    void writesNothingToTheErrorStreamWhileLookingAtHostileAttachments() {
        // A library does not get to write to the error stream of the process, and a
        // parser handed arbitrary bytes does exactly that unless it is given characters.
        byte[] pdf = Pdfs.builder()
                .attach("image.png", IMAGE)
                .attach("half.xml", "<Invoice xmlns=\"urn:oasis".getBytes(StandardCharsets.UTF_8))
                .attach("tag-then-rubbish.xml", new byte[] {'<', (byte) 0x89, (byte) 0xFF, 'a'})
                .build();

        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            assertEquals(3, located(pdf).all().size());
        } finally {
            System.setErr(original);
        }

        assertEquals("", captured.toString(StandardCharsets.UTF_8));
    }

    private static InvoiceAttachments located(byte[] pdf) {
        try (PdfContainer container = PdfContainer.open(pdf, PdfLimits.defaults())) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);
            // The classification is decided before the container is closed, and the
            // result is what the tests look at.
            return located;
        }
    }
}
