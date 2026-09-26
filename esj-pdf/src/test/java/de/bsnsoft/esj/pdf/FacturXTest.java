package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.xr.XrImporter;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.junit.jupiter.api.Test;

/**
 * Writing the invoice into the PDF that shows it, and the files this module will not
 * write it into.
 *
 * <p>The input here is a small PDF/A-3 file that this class builds; the pair with the
 * real renderer, the PDF/A oracle and the whole conformance corpus is in {@code
 * esj-render}, which is the module that has both. What is asked here is what the
 * operation writes, what it gives back, and where it stops.
 */
class FacturXTest {

    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";

    /** The specification identifier of an invoice of the profile XRECHNUNG, as the corpus writes it. */
    private static final EmbedOptions XRECHNUNG = EmbedOptions.of(FacturXProfile.XRECHNUNG);

    @Test
    void theInvoiceGoesInAndTheSameDocumentComesBackOut() {
        SemanticDocument document = document();
        byte[] hybrid = FacturX.embed(Pdfs.pdfa3(), document, XRECHNUNG);

        byte[] expected = EsjWriter.canonical()
                .toBytes(new XrImporter().importXml(CiiWriter.write(document)));
        assertArrayEquals(expected,
                EsjWriter.canonical().toBytes(PdfInvoiceImporter.importPdf(hybrid).document()),
                "the container gives back the invoice that was put into it");
    }

    @Test
    void theAttachmentIsAnAssociatedAlternativeUnderTheNameTheMetadataGives() {
        byte[] hybrid = FacturX.embed(Pdfs.pdfa3(), document(),
                XRECHNUNG.withEsj(false));

        try (PdfContainer container = PdfContainer.open(hybrid)) {
            List<EmbeddedFile> files = container.embeddedFiles();
            assertEquals(1, files.size(), "one attachment");
            EmbeddedFile file = files.get(0);
            assertEquals(HybridFlavour.FACTUR_X_1_0.attachmentName(), file.name());
            assertEquals(Optional.of(Pdfs.XML), file.declaredMediaType());
            assertEquals(Optional.of(Pdfs.ALTERNATIVE), file.associatedRelationship());
            assertTrue(file.associated(), "the catalog's /AF array names it");
            assertArrayEquals(CiiWriter.write(document()), file.content().bytes(),
                    "and it carries the cross industry invoice of this document");

            FacturXMetadata metadata = container.facturX().orElseThrow();
            assertEquals(Optional.of("INVOICE"), metadata.documentType());
            assertEquals(Optional.of(HybridFlavour.FACTUR_X_1_0.attachmentName()), metadata.documentFileName());
            assertEquals(Optional.of("1.0"), metadata.version());
            assertEquals(Optional.of(FacturXProfile.XRECHNUNG), metadata.profile());
            assertEquals("PDF/A-3B", container.pdfaIdentification().orElseThrow().describe(),
                    "and the file still declares what it declared");
        }
    }

    /**
     * The same rendering and the same invoice give the same file. The input is built once
     * here because a PDF the library writes carries an identifier of the moment it was
     * written, which two calls of the builder do not share; what is asked of the
     * embedding is that it adds nothing of its own to that.
     */
    @Test
    void embeddingTheSameInvoiceTwiceGivesTheSameBytes() {
        byte[] input = Pdfs.pdfa3();
        SemanticDocument document = document();

        assertArrayEquals(FacturX.embed(input, document, XRECHNUNG),
                FacturX.embed(input, document, XRECHNUNG));
    }

    @Test
    void refusesAnInputThatDeclaresAnotherPartOfTheStandard() {
        for (int part : new int[] {1, 2}) {
            byte[] pdf = Pdfs.builder().xmp(Pdfs.pdfaXmp(part, "B")).build();

            EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                    () -> FacturX.embed(pdf, document(), XRECHNUNG));

            assertTrue(refused.getMessage().contains("PDF/A-" + part + "B"),
                    refused.getMessage());
            assertTrue(refused.getMessage().contains("converts"), refused.getMessage());
        }
    }

    @Test
    void refusesAnInputThatDeclaresNoConformanceAtAll() {
        byte[] pdf = Pdfs.builder().build();

        EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                () -> FacturX.embed(pdf, document(), XRECHNUNG));

        assertTrue(refused.getMessage().contains("declares no PDF/A"), refused.getMessage());
    }

    @Test
    void refusesAnInputThatAlreadyCarriesAnInvoice() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));

        EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                () -> FacturX.embed(pdf, document(), XRECHNUNG));

        assertTrue(refused.getMessage().contains("could be the electronic invoice"),
                refused.getMessage());
    }

    @Test
    void refusesAnInputWhosePacketAlreadyClaimsToBeAHybridInvoice() {
        // The packet of a hybrid file without the attachment it promises. The claim is
        // still there, and writing a second set of the same properties over it would give
        // the file two answers to every question a consumer asks it.
        byte[] pdf = Pdfs.builder()
                .xmp(Pdfs.xmp(HybridFlavour.FACTUR_X_1_0.attachmentName(), "EN 16931"))
                .build();

        EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                () -> FacturX.embed(pdf, document(), XRECHNUNG));

        assertTrue(refused.getMessage().contains("already declares"), refused.getMessage());
    }

    @Test
    void refusesADocumentWhoseProfileIsNotTheOneTheContainerWouldDeclare() {
        EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                () -> FacturX.embed(Pdfs.pdfa3(), document(), EmbedOptions.defaults()));

        assertTrue(refused.getMessage().contains("XRECHNUNG"), refused.getMessage());
        assertTrue(refused.getMessage().contains("EN 16931"), refused.getMessage());
    }

    @Test
    void refusesADocumentThatNamesNoSpecificationAtAll() {
        SemanticDocument without = document().toBuilder().remove("/BG-2/BT-24").build();

        EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                () -> FacturX.embed(Pdfs.pdfa3(), without, XRECHNUNG));

        assertTrue(refused.getMessage().contains("BT-24"), refused.getMessage());
    }

    /**
     * The declaration in the packet is a claim of the file about itself, and a caller that
     * wants more than a claim lends a validator. The one here refuses everything, which is
     * what a real one does to a file that declares PDF/A-3 without being it.
     */
    @Test
    void aValidatorTheCallerLendsCanRefuseAFileThatOnlyDeclaresIt() {
        EmbedOptions checked = XRECHNUNG.checkedWith(
                pdf -> Optional.of("the page draws in a colour space the file does not carry"));

        EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                () -> FacturX.embed(Pdfs.pdfa3(), document(), checked));

        assertTrue(refused.getMessage().contains("does not agree"), refused.getMessage());
        assertTrue(refused.getMessage().contains("colour space"), refused.getMessage());
    }

    /** A validator that passes changes nothing about the result. */
    @Test
    void aValidatorThatPassesLeavesTheResultAsItWas() {
        byte[] input = Pdfs.pdfa3();
        EmbedOptions checked = XRECHNUNG.checkedWith(pdf -> Optional.empty());

        assertArrayEquals(FacturX.embed(input, document(), XRECHNUNG),
                FacturX.embed(input, document(), checked));
    }

    /** Embedding reads the input, so the input is refused where reading it is. */
    @Test
    void refusesAnEncryptedInput() {
        byte[] pdf = Pdfs.builder().xmp(Pdfs.pdfaXmp(3, "B")).userPassword("").build();

        assertThrows(PdfAccessException.class,
                () -> FacturX.embed(pdf, document(), XRECHNUNG));
    }

    @Test
    void refusesAProfileThatIsNoEn16931Invoice() {
        for (FacturXProfile profile : List.of(FacturXProfile.MINIMUM, FacturXProfile.BASIC_WL)) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> EmbedOptions.of(profile));

            assertTrue(refused.getMessage().contains("EN 16931 invoice"),
                    refused.getMessage());
        }
    }

    /**
     * The other flavour writes its own name, its own namespace, its own prefix and its
     * own version. The four travel together: a file that took the name of ZUGFeRD 2.0 and
     * the schema of Factur-X 1.0 would be a file neither specification defines.
     */
    @Test
    void theOtherFlavourWritesItsOwnNameNamespaceAndVersion() {
        byte[] hybrid = FacturX.embed(Pdfs.pdfa3(), document(),
                XRECHNUNG.withFlavour(HybridFlavour.ZUGFERD_2_0).withEsj(false));

        try (PdfContainer container = PdfContainer.open(hybrid)) {
            assertEquals("zugferd-invoice.xml", container.embeddedFiles().get(0).name());
            FacturXMetadata metadata = container.facturX().orElseThrow();
            assertEquals(FacturXMetadata.ZUGFERD_2_NAMESPACE, metadata.namespace());
            assertEquals(Optional.of("zugferd-invoice.xml"), metadata.documentFileName());
            assertEquals(Optional.of("2p0"), metadata.version());
        }
    }

    /**
     * Each flavour binds its namespace under the prefix its own specification gives it —
     * {@code fx} for Factur-X 1.0, {@code zf} for ZUGFeRD 2.0 — both as the declared
     * {@code pdfaSchema:prefix} and as the element prefix of the four properties. The
     * namespace is what binds in RDF, but a consumer that looks for the property by the
     * name the specification spells looks for that prefix.
     */
    @Test
    void eachFlavourBindsItsNamespaceUnderItsOwnPrefix() {
        for (HybridFlavour flavour : HybridFlavour.values()) {
            String packet = packetOf(FacturX.embed(Pdfs.pdfa3(), document(),
                    XRECHNUNG.withFlavour(flavour)));
            String prefix = flavour.prefix();

            assertTrue(packet.contains("<pdfaSchema:prefix>" + prefix
                    + "</pdfaSchema:prefix>"), flavour + ": declared prefix");
            assertTrue(packet.contains("xmlns:" + prefix + "=\"" + flavour.namespace()
                    + "\""), flavour + ": the namespace is bound under it");
            for (String property : List.of("DocumentType", "DocumentFileName", "Version",
                    "ConformanceLevel")) {
                assertTrue(packet.contains("<" + prefix + ":" + property + ">"),
                        flavour + ": " + prefix + ":" + property);
            }
        }
        assertNotEquals(HybridFlavour.FACTUR_X_1_0.prefix(),
                HybridFlavour.ZUGFERD_2_0.prefix());
    }

    /** Returns the XMP packet of a file as text. */
    private static String packetOf(byte[] pdf) {
        try (PdfContainer container = PdfContainer.open(pdf)) {
            return new String(container.xmpPacket().orElseThrow(),
                    StandardCharsets.UTF_8);
        }
    }

    @Test
    void refusesAnInputThatAlreadyCarriesAnAttachmentOfThatName() {
        // Not an invoice — nothing here would mistake a text file for one — but the name
        // the invoice would be attached under is taken, and a file with two attachments
        // of one name is a file a consumer has to choose from.
        byte[] pdf = Pdfs.builder()
                .xmp(Pdfs.pdfaXmp(3, "B"))
                .attach(supplement(HybridFlavour.FACTUR_X_1_0.attachmentName()))
                .nameTreeKeysAreNames()
                .build();

        EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                () -> FacturX.embed(pdf, document(), XRECHNUNG));

        assertTrue(refused.getMessage().contains("under the name"), refused.getMessage());
    }

    @Test
    void refusesAnInputWhoseNameTreeHasChildren() {
        byte[] pdf = Pdfs.builder()
                .xmp(Pdfs.pdfaXmp(3, "B"))
                .attach(supplement("terms.txt"))
                .nestedNameTree()
                .build();

        EmbedRefusedException refused = assertThrows(EmbedRefusedException.class,
                () -> FacturX.embed(pdf, document(), XRECHNUNG));

        assertTrue(refused.getMessage().contains("has children"), refused.getMessage());
    }

    /** An attachment that is not the invoice keeps its place beside the one that is. */
    @Test
    void keepsAnAttachmentTheFileAlreadyCarried() {
        byte[] pdf = Pdfs.builder()
                .xmp(Pdfs.pdfaXmp(3, "B"))
                .attach(supplement("terms.txt"))
                .build();

        byte[] hybrid = FacturX.embed(pdf, document(), XRECHNUNG);

        try (PdfContainer container = PdfContainer.open(hybrid)) {
            assertEquals(List.of(HybridFlavour.FACTUR_X_1_0.attachmentName(),
                            EsjAttachment.NAME, "terms.txt"),
                    container.embeddedFiles().stream().map(EmbeddedFile::name)
                            .sorted().toList(),
                    "what the file carried, the invoice, and the ESJ document beside it");
        }
    }

    /**
     * What the cross industry invoice had no place for leaves with the file. A hybrid
     * invoice is the archived record, and a term that did not reach the attachment is one
     * nobody notices again unless the operation says so.
     */
    @Test
    void theReportOfTheWriterComesOutWithTheFile() {
        SemanticDocument document = withATermThisSyntaxCannotCarry();

        byte[] pages = Pdfs.pdfa3();
        EmbedResult result = FacturX.embedWithReport(pages, document,
                EmbedOptions.defaults());

        assertTrue(result.report().dropped() > 0, "the value was not written: "
                + result.report());
        assertEquals(List.of("/BT-B2C-010"),
                result.report().notes(WriteNote.Kind.TERM_UNKNOWN).stream()
                        .map(WriteNote::path).toList(),
                "and the note names it");
        assertArrayEquals(FacturX.embed(pages, document, EmbedOptions.defaults()),
                result.pdf(), "the short form writes the same file");
    }

    /**
     * Handed the registry that defines it, the writer knows a term that belongs to no
     * transport syntax by design: the report names it with that registry and counts it as
     * no loss, and the file is the one written without the registry.
     */
    @Test
    void aTermItsRegistryKeepsOutOfEverySyntaxIsNoLoss() {
        SemanticDocument document = withATermThisSyntaxCannotCarry();
        List<Registry> b2c = List.of(Registry.b2cExtension());
        byte[] pages = Pdfs.pdfa3();

        EmbedResult result = FacturX.embedWithReport(pages, document,
                EmbedOptions.defaults().withExtensions(b2c));

        assertTrue(result.report().isComplete(), result.report().toString());
        assertEquals(0, result.report().dropped(), result.report().toString());
        assertEquals(List.of("/BT-B2C-010"),
                result.report().notes(WriteNote.Kind.TERM_BY_DESIGN).stream()
                        .map(WriteNote::path).toList(),
                "the note names the term");
        assertEquals(List.of("ESJ-B2C 0.1"),
                result.report().notes(WriteNote.Kind.TERM_BY_DESIGN).stream()
                        .map(WriteNote::registry).toList(),
                "and the registry that keeps it out of every syntax");
        assertFalse(result.report().notes().stream()
                        .anyMatch(note -> note.kind() == WriteNote.Kind.TERM_UNKNOWN),
                result.report().toString());
        assertArrayEquals(FacturX.embed(pages, document, EmbedOptions.defaults()),
                result.pdf(), "the registries change the report and not the file");
    }

    /** The registries stay with the options whatever else is set after them. */
    @Test
    void theRegistriesSurviveEveryOtherSetting() {
        List<Registry> b2c = List.of(Registry.b2cExtension());

        EmbedOptions options = EmbedOptions.defaults().withExtensions(b2c)
                .withProfile(FacturXProfile.XRECHNUNG)
                .withFlavour(HybridFlavour.ZUGFERD_2_0)
                .withLimits(PdfLimits.defaults())
                .withEsj(false)
                .checkedWith(pdf -> Optional.empty());

        assertEquals(b2c, options.extensions());
        assertEquals(List.of(), EmbedOptions.defaults().extensions(),
                "the defaults hand the writer no registry");
        assertEquals(List.of(), new EmbedOptions(FacturXProfile.EN_16931,
                        HybridFlavour.FACTUR_X_1_0, PdfLimits.defaults(), Optional.empty(),
                        true).extensions(),
                "and neither do the five members without them");
    }

    /** A document whose every value reached the syntax says exactly that. */
    @Test
    void aDocumentThatTravelsWholeReportsNothing() {
        EmbedResult result = FacturX.embedWithReport(Pdfs.pdfa3(), document(), XRECHNUNG);

        assertTrue(result.report().isComplete(), result.report().toString());
    }

    /**
     * The embedded file stream carries the issue date of the invoice as its modification
     * date: the container specification asks the stream for one, and a date of the
     * document rather than of the run keeps "the same invoice gives the same bytes".
     */
    @Test
    void theAttachmentCarriesTheIssueDateOfTheInvoice() {
        assertEquals(Optional.of("2016-04-04"),
                document().value(SemanticPath.of("/BT-2")).map(SemanticValue::content),
                "the invoice states the date this is taken from");

        byte[] hybrid = FacturX.embed(Pdfs.pdfa3(), document(), XRECHNUNG);

        Calendar written = modificationDateOfTheAttachment(hybrid);
        assertEquals(2016, written.get(Calendar.YEAR));
        assertEquals(Calendar.APRIL, written.get(Calendar.MONTH));
        assertEquals(4, written.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, written.get(Calendar.HOUR_OF_DAY), "at midnight");
    }

    /** Returns the {@code /Params /ModDate} of the one attachment of a hybrid file. */
    private static Calendar modificationDateOfTheAttachment(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDEmbeddedFilesNameTreeNode tree = document.getDocumentCatalog().getNames()
                    .getEmbeddedFiles();
            PDComplexFileSpecification specification =
                    tree.getNames().values().iterator().next();
            Calendar written = specification.getEmbeddedFile().getModDate();
            assertNotNull(written, "the embedded file stream carries a modification date");
            written.setTimeZone(TimeZone.getTimeZone("UTC"));
            return written;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * The file identifier says what ISO 32000-1, 14.4 gives it to say: the first half
     * stays with the pages that came in, the second changes because the file did.
     */
    @Test
    void theFileKeepsTheFirstIdentifierAndGetsASecondOfItsOwn() {
        byte[] pages = Pdfs.pdfa3();
        byte[] hybrid = FacturX.embed(pages, document(), XRECHNUNG);

        assertEquals(identifier(pages, 0), identifier(hybrid, 0),
                "the pages are the pages that came in");
        assertNotEquals(identifier(pages, 1), identifier(hybrid, 1),
                "and the file is not the file that came in");
        assertArrayEquals(hybrid, FacturX.embed(pages, document(), XRECHNUNG),
                "both halves are still a function of the inputs");
    }

    /** Returns one half of the file identifier of a PDF, as hexadecimal. */
    private static String identifier(byte[] pdf, int half) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSArray array = document.getDocument().getTrailer().getCOSArray(COSName.ID);
            return ((COSString) array.get(half)).toHexString();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * Returns a document carrying a term of no published model, which the binding table
     * of the syntax has no entry for. What the writer does with it is the writer's
     * business; what is asked here is that the caller of {@code embed} hears about it.
     */
    private static SemanticDocument withATermThisSyntaxCannotCarry() {
        String json = """
                {
                  "format": "EN16931-Semantic-JSON",
                  "version": "0.1",
                  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
                  "values": {
                    "/BT-1": "RE-2026-0001",
                    "/BT-2": "2026-01-15",
                    "/BT-3": "380",
                    "/BT-5": "EUR",
                    "/BG-2/BT-24": "urn:cen.eu:en16931:2017",
                    "/BT-B2C-010": "119.00"
                  }
                }
                """;
        return EsjReader.strict().read(json.getBytes(StandardCharsets.UTF_8));
    }

    /** A file that travels with the document rather than being it. */
    private static Pdfs.Attachment supplement(String name) {
        byte[] content = "delivery terms".getBytes(StandardCharsets.UTF_8);
        return new Pdfs.Attachment(name, content, "text/plain", "Supplement", true,
                content.length, false, null);
    }

    /** The invoice of the corpus this class embeds, as an ESJ document. */
    private static SemanticDocument document() {
        return new XrImporter().importXml(Conformance.instance(CII));
    }
}
