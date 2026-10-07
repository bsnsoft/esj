package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.validate.Severity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name tree that lists two files under one name.
 *
 * <p>A name tree maps a key to one file, and the library loads a node of it into a map, so
 * a second entry under a key replaces the first. A reader that walks the array instead takes
 * the first. The fixtures here are the placements a security review found: the second file
 * written in front of the first and left out of the associated files array — the one the
 * map hid completely — behind it, and inside the array. Each of them has to show both
 * files, refuse to choose between them, and report the name.
 */
class DuplicateNamesTest {

    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";

    /** A second invoice, which is a different document from the first. */
    private static final String OTHER_CII =
            "business-cases/standard/01.02a-INVOICE_uncefact.xml";

    private static final String DUPLICATE = "PDF-EMBEDDED-DUPLICATE-NAME";

    /** The name of the JSON enclosure a hybrid of 0.9.0 to 0.9.3 carried. */
    private static final String JSON = "invoice.esj.json";

    /** Bytes that are no XML: the file signature of a PNG image. */
    private static final byte[] IMAGE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Test
    void aShadowInFrontOfTheInvoiceAndOutsideTheArrayIsNoLongerHidden() {
        byte[] pdf = Pdfs.builder()
                .attach(shadow(OTHER_CII, false))
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .keys(Pdfs.FACTUR_X, Pdfs.FACTUR_X)
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        refusedAndReported(pdf, 2);
    }

    @Test
    void aShadowBehindTheInvoiceIsRefusedAndReported() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .attach(shadow(OTHER_CII, false))
                .keys(Pdfs.FACTUR_X, Pdfs.FACTUR_X)
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        refusedAndReported(pdf, 2);
    }

    @Test
    void aShadowInsideTheArrayIsRefusedAndReported() {
        byte[] pdf = Pdfs.builder()
                .attach(shadow(OTHER_CII, true))
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .keys(Pdfs.FACTUR_X, Pdfs.FACTUR_X)
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        refusedAndReported(pdf, 2);
    }

    @Test
    void aKeyListedInTwoNodesOfTheTreeIsADuplicateToo() {
        byte[] pdf = Pdfs.builder()
                .attach(shadow(OTHER_CII, false))
                .attach(new Pdfs.Attachment("terms.txt",
                        "delivery terms".getBytes(StandardCharsets.UTF_8), "text/plain",
                        "Supplement", true, 14, false, null))
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .keys(Pdfs.FACTUR_X, "terms.txt", Pdfs.FACTUR_X)
                .leaves(2, 1)
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        refusedAndReported(pdf, 3);
    }

    @Test
    void aShadowThatSpellsNoInvoiceIsStillAFileTheNameMayBeTakenFor() {
        // A reader that looks the invoice up by its name and takes the first entry is
        // handed the image; the key does not say which of the two the file means.
        byte[] pdf = Pdfs.builder()
                .attach(new Pdfs.Attachment(Pdfs.FACTUR_X, IMAGE, "image/png", null, false,
                        IMAGE.length, false, null))
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .keys(Pdfs.FACTUR_X, Pdfs.FACTUR_X)
                .build();

        InvoiceAttachments located = located(pdf);

        assertEquals(List.of(AttachmentKind.NOT_XML, AttachmentKind.CII_INVOICE),
                located.all().stream().map(LocatedAttachment::kind).toList());
        assertEquals(2, located.candidates().size(),
                "the image shares the name of the invoice, so it is a candidate");
        assertThrows(AmbiguousInvoiceAttachmentException.class, located::single);
    }

    @Test
    void aDuplicatedNameOfAnAttachmentThatIsNotTheInvoiceIsReportedAndCannotBeNamed() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .attach(json("{\"shadow\":true}"))
                .attach(json("{\"original\":true}"))
                .keys(Pdfs.FACTUR_X, JSON, JSON)
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);

            assertEquals(3, located.all().size(), "both JSON files are listed");
            assertEquals(List.of(AttachmentKind.CII_INVOICE, AttachmentKind.NOT_XML,
                            AttachmentKind.NOT_XML),
                    located.all().stream().map(LocatedAttachment::kind).toList());
            assertEquals(AttachmentKind.CII_INVOICE, located.single().kind(),
                    "the invoice itself is not in doubt");
            assertThrows(AmbiguousInvoiceAttachmentException.class,
                    () -> located.named(JSON));

            List<ContainerFinding> findings = ContainerChecks.run(container, located);
            ContainerFinding duplicate = finding(findings, DUPLICATE);
            assertEquals(Severity.ERROR, duplicate.severity());
            assertTrue(duplicate.message().contains("\"invoice.esj.json\""), duplicate.message());
            assertTrue(duplicate.message().contains(" 2 \"invoice.esj.json\" (not XML, "),
                    duplicate.message());
            assertTrue(duplicate.message().contains(" 3 \"invoice.esj.json\" (not XML, "),
                    duplicate.message());
            assertEquals(List.of(DUPLICATE), findings.stream()
                    .filter(finding -> finding.severity() == Severity.ERROR)
                    .map(ContainerFinding::code).toList());
        }
    }

    @Test
    void oneFileListedTwiceUnderOneKeyIsOneAttachment() {
        byte[] invoice = Conformance.instance(CII);
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, invoice))
                .build();
        // The same file specification written twice under the same key: a reader of
        // either kind is handed the same file, so nothing is in doubt.
        byte[] twice = PdfEdit.repeatNameTreeEntry(pdf, 1);

        try (PdfContainer container = PdfContainer.open(twice)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);
            assertEquals(1, located.all().size());
            assertTrue(located.duplicateNames().isEmpty());
            assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
            assertFalse(ContainerChecks.run(container, located).stream()
                    .anyMatch(finding -> finding.code().equals(DUPLICATE)));
        }
    }

    @Test
    void aNameTheTreeListsTwiceCannotBeUsedToPickOneOfTheTwo() {
        // The second file calls itself something else, so its file specification does not
        // carry the name; the key under which the tree lists it does.
        byte[] pdf = Pdfs.builder()
                .attach(new Pdfs.Attachment("other.xml", Conformance.instance(OTHER_CII),
                        Pdfs.XML, Pdfs.ALTERNATIVE, false,
                        Conformance.instance(OTHER_CII).length, false, null))
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .keys(Pdfs.FACTUR_X, Pdfs.FACTUR_X)
                .build();

        InvoiceAttachments located = located(pdf);

        AmbiguousInvoiceAttachmentException byName = assertThrows(
                AmbiguousInvoiceAttachmentException.class,
                () -> located.named(Pdfs.FACTUR_X));
        assertEquals(2, byName.candidates().size());
        AmbiguousInvoiceAttachmentException byItsOwnName = assertThrows(
                AmbiguousInvoiceAttachmentException.class, () -> located.named("other.xml"));
        assertEquals(2, byItsOwnName.candidates().size(),
                "its own name picks it, and the tree lists another file under its key");
        assertEquals(AttachmentKind.CII_INVOICE, located.at(2).orElseThrow().kind(),
                "the position is the selector that always works");
    }

    @Test
    void theEntriesOfTheTreeAreBoundedWhateverTheyNameAndHowOften() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .build();
        byte[] repeated = PdfEdit.repeatNameTreeEntry(pdf, 3);

        EsjLimitException refused = assertThrows(EsjLimitException.class,
                () -> PdfContainer.open(repeated,
                        PdfLimits.defaults().withMaxEmbeddedFiles(3)).close());

        assertTrue(refused.getMessage().contains("entries"), refused.getMessage());
    }

    @Test
    void embeddingRefusesATreeThatListsTwoFilesUnderOneName() {
        // The tree is written back as one node that maps a name to one file, so one of
        // the two would be gone afterwards.
        byte[] content = "terms".getBytes(StandardCharsets.UTF_8);
        byte[] pdf = Pdfs.builder()
                .xmp(Pdfs.pdfaXmp(3, "B"))
                .attach(new Pdfs.Attachment("terms.txt", content, "text/plain", "Supplement",
                        true, content.length, false, null))
                .attach(new Pdfs.Attachment("terms.txt", content, "text/plain", "Supplement",
                        true, content.length, false, null))
                .keys("terms.txt", "terms.txt")
                .build();

        EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                () -> FacturX.embed(pdf, new de.bsnsoft.esj.xr.XrImporter()
                                .read(Conformance.instance(CII)).document(),
                        EmbedOptions.defaults().withProfile(FacturXProfile.XRECHNUNG)));

        assertTrue(refused.getMessage().contains("\"terms.txt\""), refused.getMessage());
    }

    /**
     * Asserts what every placement of a second file under the invoice's name comes to: both
     * files listed, neither chosen, neither nameable by the name, and the name reported
     * with the position, the object number and the size of each entry.
     */
    private static void refusedAndReported(byte[] pdf, int files) {
        try (PdfContainer container = PdfContainer.open(pdf)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);

            assertEquals(files, located.all().size(), "every entry of the tree is listed");
            List<LocatedAttachment> sharing = key(located, Pdfs.FACTUR_X).attachments();
            assertEquals(2, sharing.size());
            assertEquals(2, located.invoices().size());
            AmbiguousInvoiceAttachmentException refused = assertThrows(
                    AmbiguousInvoiceAttachmentException.class, located::single);
            assertTrue(refused.candidates().containsAll(sharing));
            assertThrows(AmbiguousInvoiceAttachmentException.class,
                    () -> located.named(Pdfs.FACTUR_X));

            ContainerFinding duplicate = finding(ContainerChecks.run(container, located),
                    DUPLICATE);
            assertEquals(Severity.ERROR, duplicate.severity());
            assertEquals(ContainerFinding.Category.PDF_EMBEDDED, duplicate.category());
            String message = duplicate.message();
            assertTrue(message.contains("under the name \"factur-x.xml\""), message);
            for (LocatedAttachment attachment : sharing) {
                int position = located.all().indexOf(attachment) + 1;
                EmbeddedFile file = attachment.file();
                assertTrue(message.contains(" " + position + " \"factur-x.xml\" (CII, object "
                                + file.objectNumber().getAsLong() + ", "
                                + file.declaredSize().getAsLong() + " bytes declared)"),
                        message);
            }
        }
    }

    /** A second cross industry invoice, declared as the invoice of the document. */
    private static Pdfs.Attachment shadow(String instance, boolean associated) {
        byte[] content = Conformance.instance(instance);
        return new Pdfs.Attachment(Pdfs.FACTUR_X, content, Pdfs.XML, Pdfs.ALTERNATIVE,
                associated, content.length, false, null);
    }

    /**
     * A JSON enclosure under the name a hybrid of 0.9.0 to 0.9.3 gave the ESJ document it
     * carried beside the invoice.
     */
    private static Pdfs.Attachment json(String json) {
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        return new Pdfs.Attachment(JSON, content, "application/json", "Supplement", true,
                content.length, false, null);
    }

    /** Returns the name tree key the container gives more than one file. */
    private static InvoiceAttachments.DuplicateName key(InvoiceAttachments located,
                                                        String key) {
        return located.duplicateNames().stream()
                .filter(duplicate -> duplicate.source()
                        == InvoiceAttachments.DuplicateName.Source.NAME_TREE_KEY)
                .filter(duplicate -> duplicate.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the key " + key + " is not duplicated"));
    }

    private static InvoiceAttachments located(byte[] pdf) {
        PdfContainer container = PdfContainer.open(pdf);
        return InvoiceAttachments.locate(container);
    }

    private static ContainerFinding finding(List<ContainerFinding> findings, String code) {
        return findings.stream().filter(finding -> finding.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + code + " among " + findings));
    }
}
