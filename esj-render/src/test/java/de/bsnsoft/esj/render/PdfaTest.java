package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * Every rendering this module writes is a PDF/A-3b file, and the one that says so is not
 * this project.
 *
 * <p>The oracle is veraPDF 1.30.2 — the reference implementation of the PDF/A validation
 * model, the one the PDF Association maintains — run in this module's tests against the
 * flavour {@code 3b}, ISO 19005-3 conformance level B. It is a test-scope dependency under
 * GPLv3+ / MPLv2+, it is in no artefact this project publishes, and what it validates is
 * the bytes the renderer produced rather than a description of them. A claim of
 * conformance checked by the code that makes it would be worth nothing.
 *
 * <p>Every instance of the conformance corpus and every example of the repository is
 * rendered and validated, in both languages and on both papers for the examples, together
 * with the documents this module builds for the cases no real invoice covers: a value at
 * every term of the model, an invoice long enough to break over pages, and one with no
 * invoice number at all. Zero failed rules, or the test names the ones that failed.
 *
 * <p>The rest of the class is about what the file says of itself — the part and the level,
 * the output intent, the profile inside it, and the agreement between the XMP packet and the
 * information dictionary that PDF/A requires. Those are checked separately because a
 * validator that one day stops looking at one of them should not be able to take the claim
 * with it quietly.
 */
class PdfaTest {

    /** The flavour of PDF/A this module claims: part 3, conformance level B. */
    private static final PDFAFlavour FLAVOUR = PDFAFlavour.PDFA_3_B;

    /** The vendored ICC profile, as the output intent has to carry it. */
    private static final String PROFILE =
            "/de/bsnsoft/esj/render/icc/sRGB2014.icc";

    /** The namespace of the PDF/A identification schema. */
    private static final String PDFAID = "http://www.aiim.org/pdfa/ns/id/";

    /** The namespace of the Dublin Core schema, which carries the title. */
    private static final String DC = "http://purl.org/dc/elements/1.1/";

    /** The namespace of the XMP basic schema, which carries the tool. */
    private static final String XMP = "http://ns.adobe.com/xap/1.0/";

    /** The namespace of the PDF schema, which carries the producer. */
    private static final String PDF = "http://ns.adobe.com/pdf/1.3/";

    @BeforeAll
    static void loadTheValidator() {
        VeraGreenfieldFoundryProvider.initialise();
    }

    static List<String> instances() {
        return Corpus.instances();
    }

    static List<String> examples() {
        return Corpus.EXAMPLES;
    }

    @ParameterizedTest
    @MethodSource("instances")
    void everyRenderingOfTheCorpusIsPdfA3b(String instance) {
        assertPdfa(instance, new PdfRenderer().render(Corpus.instance(instance),
                RenderOptions.defaults().layout(Layout.GENERIC)));
    }

    @ParameterizedTest
    @MethodSource("examples")
    void everyExampleIsPdfA3bInBothLanguagesAndOnBothPapers(String example) {
        SemanticDocument document = Corpus.example(example);
        for (RenderLanguage language : RenderLanguage.values()) {
            for (PageSize size : PageSize.values()) {
                assertPdfa(example + " in " + language + " on " + size,
                        new PdfRenderer().render(document,
                                RenderOptions.in(language).on(size).layout(Layout.GENERIC)));
            }
        }
    }

    /**
     * The built documents, which is where the cases a real invoice does not have live: a
     * value at every term of the model, a document that breaks over several pages, and one
     * that carries no invoice number and therefore no title. In both layouts.
     *
     * @param layout the layout
     */
    @ParameterizedTest
    @EnumSource(Layout.class)
    void theBuiltDocumentsArePdfA3bToo(Layout layout) {
        RenderOptions options = RenderOptions.defaults().layout(layout);
        assertPdfa("every term", new PdfRenderer().render(Documents.everyTerm(), options));
        assertPdfa("sub invoice lines",
                new PdfRenderer().render(Documents.withSubLines(), options));
        assertPdfa("many pages",
                new PdfRenderer().render(Documents.withDetailedLines(60), options));
        assertPdfa("no invoice number",
                new PdfRenderer().render(withoutInvoiceNumber(), options));
    }

    /**
     * Every letter is an archival file too. It is the layout a caller gets without asking
     * and the claim is the same claim, so the oracle is asked about every instance of the
     * corpus in that layout as well as in the generic one.
     */
    @ParameterizedTest
    @MethodSource("instances")
    void everyLetterOfTheCorpusIsPdfA3b(String instance) {
        assertPdfa(instance + " as a letter", new PdfRenderer().render(
                Corpus.instance(instance), RenderOptions.defaults().layout(Layout.LETTER)));
    }

    /** And so is every example, in both languages and on both papers. */
    @ParameterizedTest
    @MethodSource("examples")
    void everyExampleIsPdfA3bAsALetterInBothLanguagesAndOnBothPapers(String example) {
        SemanticDocument document = Corpus.example(example);
        for (RenderLanguage language : RenderLanguage.values()) {
            for (PageSize size : PageSize.values()) {
                assertPdfa(example + " as a letter in " + language + " on " + size,
                        new PdfRenderer().render(document,
                                RenderOptions.in(language).on(size).layout(Layout.LETTER)));
            }
        }
    }

    /**
     * A branded rendering is an archival file too. The letterhead comes from somewhere
     * else — a form imported with its own font, or an image — and that is exactly why the
     * oracle is asked about it: what PDF/A requires of a file is required of everything in
     * it, including what a template brought.
     */
    @ParameterizedTest
    @MethodSource("examples")
    void everyExampleIsPdfA3bOnEveryTemplate(String example) {
        SemanticDocument document = Corpus.example(example);
        for (String file : List.of("letterhead.json", "image.json", "gross.json",
                "letter.json")) {
            RenderTemplate template = Templates.example(file);
            for (RenderLanguage language : RenderLanguage.values()) {
                assertPdfa(example + " on " + file + " in " + language,
                        new PdfRenderer().render(document,
                                RenderOptions.in(language).with(template)));
            }
        }
    }

    /** And so is the consumer invoice on the template that gives its figures a place. */
    @Test
    void theGrossLayoutIsPdfA3bToo() {
        for (RenderLanguage language : RenderLanguage.values()) {
            assertPdfa("a consumer invoice in " + language,
                    new PdfRenderer(Templates.withB2c()).render(Documents.grossB2cInvoice(),
                            RenderOptions.in(language).with(Templates.example("gross.json"))));
        }
    }

    @Test
    void theFileDeclaresPartThreeAtLevelB() {
        byte[] rendered = new PdfRenderer().render(Corpus.example("minimal"));

        assertEquals("3", Xmp.property(rendered, PDFAID, "part"), "the part of ISO 19005");
        assertEquals("B", Xmp.property(rendered, PDFAID, "conformance"), "the level");
    }

    /**
     * The version of PDF that part 3 of ISO 19005 profiles is 1.7, and the file says so.
     * PDFBox writes the header of a compressed file as 1.6, because that is the version
     * cross-reference streams were introduced in; the catalog carries the version of the
     * document, which is what a reader takes.
     */
    @Test
    void theFileIsPdfSeventeen() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(new PdfRenderer().render(Corpus.example("minimal")))) {
            assertEquals("1.7", pdf.getDocumentCatalog().getVersion(), "the catalog version");
            assertEquals(1.7f, pdf.getVersion(), 0.001f, "and so the version of the document");
        }
    }

    /**
     * The output intent carries the vendored profile whole and unmodified — a PDF/A file
     * says which colour space its device-dependent greys are to be read in, and it says it
     * by carrying the profile rather than by naming a condition the reader is expected to
     * know.
     */
    @Test
    void theOutputIntentCarriesTheVendoredProfile() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(new PdfRenderer().render(Corpus.example("minimal")))) {
            List<PDOutputIntent> intents = pdf.getDocumentCatalog().getOutputIntents();
            assertEquals(1, intents.size(), "one output intent");
            PDOutputIntent intent = intents.get(0);
            assertEquals("GTS_PDFA1",
                    intent.getCOSObject().getNameAsString(COSName.S),
                    "the subtype PDF/A asks for");
            assertEquals("sRGB IEC61966-2.1", intent.getOutputConditionIdentifier(),
                    "the output condition");
            COSStream profile = intent.getDestOutputIntent();
            try (InputStream in = profile.createInputStream()) {
                assertArrayEquals(Corpus.bytes(PROFILE), in.readAllBytes(),
                        "the destination profile is the vendored file, byte for byte");
            }
            assertEquals(3, profile.getInt(COSName.N),
                    "and it is declared as three components");
        }
    }

    /**
     * PDF/A requires the information dictionary and the XMP packet to say the same thing.
     * They cannot disagree here, because the packet is written out of that dictionary — this
     * test is what says so out loud, and what would notice if the packet were ever written
     * from somewhere else.
     */
    @Test
    void theXmpPacketRepeatsTheInformationDictionary() throws IOException {
        byte[] rendered = new PdfRenderer().render(Corpus.example("standard-invoice"));

        try (PDDocument pdf = Loader.loadPDF(rendered)) {
            var information = pdf.getDocumentInformation();
            assertEquals(information.getTitle(), Xmp.property(rendered, DC, "title"),
                    "the title");
            assertEquals(information.getCreator(), Xmp.property(rendered, XMP, "CreatorTool"),
                    "the creator");
            assertEquals(information.getProducer(), Xmp.property(rendered, PDF, "Producer"),
                    "the producer");
        }
        assertEquals("application/pdf", Xmp.property(rendered, DC, "format"), "the media type");
    }

    @Test
    void theTitleOfTheFileIsTheInvoiceNumber() throws IOException {
        byte[] rendered = new PdfRenderer().render(Corpus.example("standard-invoice"));

        try (PDDocument pdf = Loader.loadPDF(rendered)) {
            assertEquals("RE-2026-0042", pdf.getDocumentInformation().getTitle(),
                    "the title of the file is BT-1 and nothing else");
        }
        assertEquals("RE-2026-0042", Xmp.property(rendered, DC, "title"), "in the packet too");
    }

    /**
     * A document without an invoice number gives the file no title, in neither place: an
     * empty title in one of them and none in the other is exactly the inconsistency PDF/A
     * forbids, and a made-up one would be a value this renderer invented.
     */
    @Test
    void aDocumentWithoutAnInvoiceNumberCarriesNoTitle() throws IOException {
        byte[] rendered = new PdfRenderer().render(withoutInvoiceNumber());

        try (PDDocument pdf = Loader.loadPDF(rendered)) {
            assertNull(pdf.getDocumentInformation().getTitle(), "no title in the dictionary");
        }
        assertNull(Xmp.property(rendered, DC, "title"), "and none in the packet");
    }

    /**
     * The invoice number is a text of a document from a stranger, and the packet is XML. A
     * number with markup in it is escaped rather than written through, the packet stays
     * well-formed, and what comes back out of it is the text the document stores.
     */
    @Test
    void anInvoiceNumberWithMarkupIsEscaped() throws IOException {
        String number = "<RE & \"2026\" 'x'>";
        byte[] rendered = new PdfRenderer().render(withInvoiceNumber(number));

        assertEquals(number, Xmp.property(rendered, DC, "title"),
                "the packet parses and gives the number back");
        assertFalse(Xmp.of(rendered).contains("<RE"), "the markup is escaped in the text");
        try (PDDocument pdf = Loader.loadPDF(rendered)) {
            assertEquals(number, pdf.getDocumentInformation().getTitle(),
                    "and the dictionary carries the same string");
        }
        assertPdfa("a number with markup", rendered);
    }

    /**
     * A control character, a character that directs the reading order and half of a
     * surrogate pair that lost its other half are the three things a title cannot carry:
     * XML has no way to write the first two, and the third is not a character at all.
     */
    @Test
    void anInvoiceNumberWithCharactersXmlCannotCarryIsCleaned() {
        String number = "RE" + (char) 0x07 + "2026" + (char) 0x202e + "-1"
                + (char) 0xd800;
        byte[] rendered = new PdfRenderer().render(withInvoiceNumber(number));

        assertEquals("RE 2026 -1?", Xmp.property(rendered, DC, "title"),
                "the control becomes a space, and so does the override; the lone "
                        + "surrogate becomes a question mark");
        assertPdfa("a number of unwritable characters", rendered);
    }

    /**
     * The XMP stream is not compressed. PDF/A asks for that so that a reader which knows
     * nothing about PDF — an indexer, an archive — can find the metadata in the file with
     * no filter to undo first.
     */
    @Test
    void theXmpStreamCarriesNoFilter() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(new PdfRenderer().render(Corpus.example("minimal")))) {
            PDDocumentCatalog catalog = pdf.getDocumentCatalog();
            PDMetadata metadata = catalog.getMetadata();
            assertNotNull(metadata, "the file carries a packet");
            assertTrue(metadata.getFilters().isEmpty(), "and the stream of it is plain");
        }
    }

    /**
     * Level B and not level A: nothing here writes a structure tree or claims one. A file
     * that declared itself tagged and carried no reading order would be a worse document
     * than an untagged one, because a reader would trust it.
     */
    @Test
    void theFileIsNotTagged() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(new PdfRenderer().render(Corpus.example("minimal")))) {
            assertNull(pdf.getDocumentCatalog().getStructureTreeRoot(), "no structure tree");
            assertNull(pdf.getDocumentCatalog().getMarkInfo(), "and no MarkInfo");
        }
    }

    /** Renders a document whose invoice number is the given text. */
    private static SemanticDocument withInvoiceNumber(String number) {
        return Documents.oneLineAtNineteenPerCent().toBuilder()
                .remove("/BT-1").put("/BT-1", number).build();
    }

    /** Renders a document that has no invoice number at all. */
    private static SemanticDocument withoutInvoiceNumber() {
        return Documents.oneLineAtNineteenPerCent().toBuilder().remove("/BT-1").build();
    }

    /** Validates a rendering against the flavour, and names every rule that failed. */
    private static void assertPdfa(String what, byte[] rendered) {
        ValidationResult result = validate(rendered);

        assertEquals(FLAVOUR, result.getPDFAFlavour(), "the flavour that was checked");
        assertTrue(result.isCompliant(),
                () -> what + " is not PDF/A-3b: " + failures(result));
    }

    private static ValidationResult validate(byte[] rendered) {
        try (PDFAParser parser = Foundries.defaultInstance()
                        .createParser(new ByteArrayInputStream(rendered), FLAVOUR);
                PDFAValidator validator =
                        Foundries.defaultInstance().createValidator(FLAVOUR, false)) {
            return validator.validate(parser);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ModelParsingException | EncryptedPdfException | ValidationException e) {
            throw new IllegalStateException("the rendering could not be validated", e);
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
