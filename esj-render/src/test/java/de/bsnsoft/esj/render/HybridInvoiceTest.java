package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.pdf.ContainerFinding;
import de.bsnsoft.esj.pdf.EmbedOptions;
import de.bsnsoft.esj.pdf.EmbedResult;
import de.bsnsoft.esj.pdf.EsjAgreement;
import de.bsnsoft.esj.pdf.EsjAttachment;
import de.bsnsoft.esj.pdf.FacturX;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.pdf.HybridFlavour;
import de.bsnsoft.esj.pdf.PdfContainer;
import de.bsnsoft.esj.pdf.EmbeddedFile;
import de.bsnsoft.esj.pdf.PdfImportResult;
import de.bsnsoft.esj.pdf.PdfInvoiceImporter;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.verapdf.core.EncryptedPdfException;
import org.verapdf.core.ModelParsingException;
import org.verapdf.core.ValidationException;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

/**
 * The lead use case, end to end: a document is rendered, the invoice is embedded into its
 * own rendering, and what comes back out of the file is the invoice that went in.
 *
 * <p>This is the one test of the build in which the renderer and the container meet, so
 * it asks the three questions that only the pair can answer. Is the result still a
 * PDF/A-3b file — veraPDF says so, not this project, and it says it for every instance of
 * the conformance corpus embedded into its own rendering. Does the invoice survive the
 * container — the document read back out of the PDF is compared with the document read
 * out of the same cross industry invoice on its own, so that a difference would be the
 * container's and nothing else's. And does the file describe itself — the structural
 * checks of {@code esj-pdf} run over the result and have nothing to report but the
 * declaration the file makes about its own conformance.
 */
class HybridInvoiceTest {

    /** The flavour a hybrid invoice claims: part 3, conformance level B. */
    private static final PDFAFlavour FLAVOUR = PDFAFlavour.PDFA_3_B;

    /** The finding every well-formed container draws: what the file declares itself to be. */
    private static final String DECLARATION = "PDF-STRUCTURE-PDFA";

    /**
     * The file {@code --no-esj} writes over the generic rendering of
     * {@code examples/standard-invoice.esj.json}, by its digest: the hybrid invoice as this
     * project wrote it before the ESJ attachment existed, when that layout was the default.
     * It changes when the rendering, the invoice or the container changes, which is when
     * somebody should look.
     */
    private static final String WITHOUT_ESJ =
            "b53cbb53128a0043a50997f08b4f1148f00672ec6bfa40191ed0a044c3bc1ef3";

    /** The body of the embedding snippet of {@code docs/pdf-output.md}, as the page prints it. */
    private static final String SNIPPET = """
            byte[] rendering = new PdfRenderer().render(document);
            byte[] hybrid = FacturX.embed(rendering, document,
                    EmbedOptions.of(FacturXProfile.EN_16931));""";

    @BeforeAll
    static void loadTheValidator() {
        VeraGreenfieldFoundryProvider.initialise();
    }

    static List<String> instances() {
        return Corpus.instances();
    }

    /**
     * Every instance of the corpus, embedded into its own rendering: still PDF/A-3b, and
     * the same invoice on the way out.
     */
    @ParameterizedTest
    @MethodSource("instances")
    void everyCorpusInstanceEmbeddedIntoItsOwnRenderingIsStillPdfA3b(String instance) {
        SemanticDocument document = Corpus.instance(instance);
        byte[] hybrid = embedded(document);

        assertPdfa(instance, hybrid);
        assertReadsBack(instance, document, hybrid);
        assertEsjAttachment(instance, document, hybrid);
    }

    /**
     * The ESJ document is in the file exactly where the rule it is written under holds, and
     * what is in it is the document: the canonical bytes, and no disagreement with the
     * cross industry invoice beside it. Two instances of the corpus carry a second
     * preceding invoice reference, which the cross industry invoice has one place for, and
     * those two get no attachment and say so.
     */
    private static void assertEsjAttachment(String what, SemanticDocument document,
                                            byte[] hybrid) {
        List<EsjAgreement.Difference> expected = EsjAgreement.differences(document,
                new StreamingReader().read(CiiWriter.write(document)).document(),
                BindingSyntax.CII);
        try (PdfContainer container = PdfContainer.open(hybrid)) {
            Optional<EmbeddedFile> attached = container.embeddedFiles().stream()
                    .filter(file -> EsjAttachment.NAME.equals(file.name()))
                    .findFirst();
            if (!expected.isEmpty()) {
                assertEquals(Optional.empty(), attached.map(EmbeddedFile::name),
                        what + ": the rule does not hold here, so nothing is attached");
                return;
            }
            assertArrayEquals(Canonicalizer.canonicalBytes(document),
                    attached.orElseThrow(() -> new AssertionError(
                            what + ": no ESJ document beside the invoice")).content().bytes(),
                    what + ": the canonical bytes of the document");
            assertEquals(List.of(), EsjAgreement.differences(
                            EsjReader.strict().read(attached.orElseThrow().content().bytes()),
                            new StreamingReader().read(container.embeddedFiles().stream()
                                    .filter(file -> HybridFlavour.FACTUR_X_1_0.attachmentName()
                                            .equals(file.name()))
                                    .findFirst().orElseThrow().content().bytes()).document(),
                            BindingSyntax.CII),
                    what + ": the two files of this container agree");
        }
    }

    /**
     * The letter layout, with the EPC QR code of its credit transfer drawn on the page and
     * a letterhead under it, carries both attachments and is still an archival file. The
     * code is vector art and the letterhead is an imported form, so each brings resources
     * of its own into a file the profile has to hold for.
     */
    @Test
    void aLetterWithItsPaymentCodeIsStillAnArchivalHybrid() {
        SemanticDocument document = Corpus.example("standard-invoice");
        byte[] letter = new PdfRenderer().render(document,
                RenderOptions.defaults().with(Templates.example("letter.json")));
        assertFalse(Pdf.codes(letter).isEmpty(), "the letter carries the EPC QR code");

        byte[] hybrid = FacturX.embed(letter, document,
                EmbedOptions.of(FacturXProfile.EN_16931));

        assertPdfa("a letter carrying its payment code", hybrid);
        try (PdfContainer container = PdfContainer.open(hybrid)) {
            List<String> names = container.embeddedFiles().stream()
                    .map(EmbeddedFile::name).sorted().toList();

            assertEquals(List.of(HybridFlavour.FACTUR_X_1_0.attachmentName(),
                            EsjAttachment.NAME).stream().sorted().toList(), names,
                    "the invoice, and the ESJ document beside it");
        }
    }

    /**
     * {@code --no-esj} writes the file this project wrote before the second attachment
     * existed, byte for byte. The digest is the golden: the rendering and the embedding are
     * each a function of the document, so the file is, and the switch has to change what is
     * in the container and nothing else.
     */
    @Test
    void theSwitchWritesTheFileThisProjectWroteBefore() {
        SemanticDocument document = Corpus.example("standard-invoice");
        byte[] rendering = new PdfRenderer().render(document,
                RenderOptions.defaults().layout(Layout.GENERIC));

        byte[] without = FacturX.embed(rendering, document,
                EmbedOptions.of(FacturXProfile.EN_16931).withEsj(false));

        assertEquals(WITHOUT_ESJ, sha256(without),
                "the file --no-esj writes is the one this project wrote before");
        try (PdfContainer container = PdfContainer.open(without)) {
            assertEquals(1, container.embeddedFiles().size(), "the invoice alone");
        }
    }

    /** What is attached, and what is said where nothing is. */
    @Test
    void theResultSaysWhetherTheEsjDocumentWentIn() {
        SemanticDocument document = Corpus.example("standard-invoice");
        byte[] rendering = new PdfRenderer().render(document);

        EmbedResult attached = FacturX.embedWithReport(rendering, document,
                EmbedOptions.of(FacturXProfile.EN_16931));
        EmbedResult omitted = FacturX.embedWithReport(rendering, document,
                EmbedOptions.of(FacturXProfile.EN_16931).withEsj(false));

        assertTrue(attached.esjAttached(), attached.esjOmitted().orElse(""));
        assertTrue(omitted.esjOmitted().orElseThrow().contains("turned it off"),
                omitted.esjOmitted().orElseThrow());
    }

    /** Returns the SHA-256 of some bytes, lower case hexadecimal. */
    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
        }
    }

    /**
     * The structural checks over a file this project wrote itself. A producer that had to
     * explain away a finding of its own reader would be a producer or a reader that is
     * wrong, and the only finding here is the file saying what it is.
     */
    @Test
    void theContainerChecksHaveNothingToSayAboutWhatThisProjectWrote() {
        SemanticDocument document = Corpus.example("standard-invoice");
        byte[] hybrid = embedded(document);

        PdfImportResult result = PdfInvoiceImporter.importPdf(hybrid);
        List<String> unexpected = new ArrayList<>();
        for (ContainerFinding finding : result.pdf().findings()) {
            if (!DECLARATION.equals(finding.code())) {
                unexpected.add(finding.code() + ": " + finding.message());
            }
        }
        assertEquals(List.of(), unexpected, "findings about a container this project wrote");
        assertEquals("PDF/A-3B", result.pdf().pdfa().orElseThrow().describe(),
                "and the one finding there is says this");
    }

    /**
     * The rendering and the embedding are each a function of the document, so the pair is
     * one too: the same invoice gives the same file, byte for byte, on every run and every
     * machine.
     */
    @Test
    void embeddingTheSameInvoiceTwiceGivesTheSameBytes() {
        SemanticDocument document = Corpus.example("allowances");

        assertArrayEquals(embedded(document), embedded(document));
    }

    /**
     * The snippet of {@code docs/pdf-output.md}, run here and compared with the page, so
     * that the page cannot show a call this project does not make.
     */
    @Test
    void theSnippetOfThePageIsTheCallThisBuildMakes() {
        SemanticDocument document = Corpus.example("standard-invoice");

        // docs/pdf-output.md: Embedding the invoice
        byte[] rendering = new PdfRenderer().render(document);
        byte[] hybrid = FacturX.embed(rendering, document,
                EmbedOptions.of(FacturXProfile.EN_16931));

        assertPdfa("the snippet of docs/pdf-output.md", hybrid);
        assertTrue(Corpus.text("/docs/pdf-output.md").contains(SNIPPET),
                "docs/pdf-output.md shows the embedding as this version writes it");
    }

    /**
     * The four properties a consumer that only reads the container goes by, written where
     * that consumer looks for them.
     */
    @Test
    void theFileSaysInItsMetadataWhichAttachmentIsTheInvoice() {
        SemanticDocument document = Corpus.example("minimal");
        byte[] hybrid = embedded(document);

        String schema = "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#";
        assertEquals("INVOICE", Xmp.property(hybrid, schema, "DocumentType"));
        assertEquals(HybridFlavour.FACTUR_X_1_0.attachmentName(),
                Xmp.property(hybrid, schema, "DocumentFileName"));
        assertEquals("1.0", Xmp.property(hybrid, schema, "Version"));
        assertEquals("EN 16931", Xmp.property(hybrid, schema, "ConformanceLevel"));
        assertEquals("3", Xmp.property(hybrid, "http://www.aiim.org/pdfa/ns/id/", "part"),
                "and the file is still what it was");
    }

    /**
     * Renders a document and embeds its invoice into that rendering.
     *
     * <p>The profile the container declares is the one the invoice writes in BT-24: the
     * two have to agree and {@code FacturX} refuses a pair that does not, so a caller
     * that has the document reads the profile off it rather than guessing. Every instance
     * of the corpus is an XRechnung and every example of the repository is a plain
     * EN 16931 invoice, and the same three lines cover both.
     */
    private static byte[] embedded(SemanticDocument document) {
        FacturXProfile profile = FacturXProfile.ofSpecificationIdentifier(
                        document.value(SemanticPath.of("/BG-2/BT-24"))
                                .map(SemanticValue::content).orElse(null))
                .orElseThrow(() -> new IllegalStateException(
                        "the document names no profile in BT-24"));
        return FacturX.embed(new PdfRenderer().render(document), document,
                EmbedOptions.of(profile));
    }

    /**
     * The document read out of the PDF against the document read out of the same cross
     * industry invoice on its own. Both go through the same importer, so what is compared
     * is the container and not the mapping: a difference here would mean that putting the
     * invoice into a PDF changed it.
     */
    private static void assertReadsBack(String what, SemanticDocument document, byte[] hybrid) {
        byte[] invoice = CiiWriter.write(document);
        byte[] expected = EsjWriter.canonical()
                .toBytes(new XrImporter().importXml(invoice));
        byte[] read = EsjWriter.canonical()
                .toBytes(PdfInvoiceImporter.importPdf(hybrid).document());

        assertArrayEquals(expected, read, what + ": the invoice the container gives back");
    }

    /** Validates a file against the flavour, and names every rule that failed. */
    private static void assertPdfa(String what, byte[] hybrid) {
        ValidationResult result = validate(hybrid);

        assertEquals(FLAVOUR, result.getPDFAFlavour(), "the flavour that was checked");
        assertTrue(result.isCompliant(),
                () -> what + " is not PDF/A-3b once the invoice is in it: "
                        + failures(result));
    }

    private static ValidationResult validate(byte[] hybrid) {
        try (PDFAParser parser = Foundries.defaultInstance()
                        .createParser(new ByteArrayInputStream(hybrid), FLAVOUR);
                PDFAValidator validator =
                        Foundries.defaultInstance().createValidator(FLAVOUR, false)) {
            return validator.validate(parser);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ModelParsingException | EncryptedPdfException | ValidationException e) {
            throw new IllegalStateException("the hybrid invoice could not be validated", e);
        }
    }

    private static String failures(ValidationResult result) {
        List<String> failed = new ArrayList<>();
        for (TestAssertion assertion : result.getTestAssertions()) {
            if (assertion.getStatus() == TestAssertion.Status.FAILED) {
                failed.add(assertion.getRuleId().getClause() + "-"
                        + assertion.getRuleId().getTestNumber() + " "
                        + assertion.getMessage());
            }
        }
        return String.join("; ", failed);
    }
}
