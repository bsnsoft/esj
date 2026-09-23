package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.pdf.EmbedOptions;
import de.bsnsoft.esj.pdf.EmbedResult;
import de.bsnsoft.esj.pdf.FacturX;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.pdf.PdfInvoiceImporter;
import de.bsnsoft.esj.xr.ExportNote;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rendering snippets of {@code docs/java-api.md}, one test per snippet, so that the page
 * cannot describe an API this module does not have. The lines between the comment naming the
 * page and its section and the assertions are the snippet as the page prints it; a change to
 * one is a change to the other. The first case below is no page's snippet: it is an import
 * and both renderings in one, which is the shape {@code esj render} runs them in.
 */
class ReadmeExamplesTest {

    /** An instance the conformance corpus carries, written out so a snippet can read a file. */
    private static final String INSTANCE = "business-cases/standard/02.05a-INVOICE_ubl.xml";

    /** The hybrid PDF of the corpus, a Factur-X file carrying a cross industry invoice. */
    private static final String HYBRID = "/conformance/pdf/factur-x.pdf";

    /** The example the README's Java reads: its BT-24 is EN 16931 and no CIUS of it. */
    private static final String EXAMPLE = "standard-invoice";

    @TempDir
    Path directory;

    @Test
    void renderAnInvoiceAsHtml() throws IOException {
        Path invoice = directory.resolve("invoice.xml");
        Files.write(invoice, Corpus.bytes("/conformance/kosit/" + INSTANCE));

        // An import and both renderings, which no page prints as one snippet.
        SemanticDocument document = new XrImporter().importXml(Files.readAllBytes(invoice));

        String html = new HtmlRenderer().render(document, RenderOptions.in(RenderLanguage.ENGLISH));
        byte[] pdf = new PdfRenderer().render(document, RenderOptions.defaults());

        assertTrue(html.startsWith("<!DOCTYPE HTML>"), "the snippet gives a whole page");
        assertTrue(html.contains("lang=\"en\""), "in the language it asked for");
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1),
                "and the other snippet gives a PDF");
    }

    /**
     * The Java of <b>Quick start</b> in {@code README.md}: an XML invoice and a hybrid PDF read
     * into one kind of document, one value addressed by its business term, and that same
     * document rendered, embedded as Factur-X and written out with the ESJ document beside it.
     * The lines between the comment and the assertions are the snippet as the page prints it; a
     * change to one is a change to the other. {@code examples/java/HybridInvoice.java} is the
     * program the page links, and a test of {@code esj-cli} compiles and runs it.
     */
    @Test
    void readAnInvoiceAndGenerateAHybridPdfFromIt() throws IOException {
        Path out = directory;
        Path xmlFile = directory.resolve("source.xml");
        Path pdfFile = directory.resolve("source.pdf");
        Files.write(xmlFile, CiiWriter.write(Corpus.example(EXAMPLE)));
        Files.write(pdfFile, Corpus.bytes(HYBRID));

        // README.md: Quick start
        SemanticDocument invoice = new StreamingReader().read(Files.readAllBytes(xmlFile)).document();
        SemanticDocument fromPdf = PdfInvoiceImporter.importPdf(Files.readAllBytes(pdfFile)).document();
        String invoiceNumber = invoice.value(SemanticPath.of("/BT-1"))
                .map(SemanticValue::asString).orElseThrow();

        byte[] pages = new PdfRenderer().render(invoice, RenderOptions.in(RenderLanguage.ENGLISH));
        EmbedResult hybrid = FacturX.embedWithReport(pages, invoice,
                EmbedOptions.of(FacturXProfile.EN_16931));
        hybrid.report().notes().forEach(note -> System.out.println("write note: " + note));
        Files.write(out.resolve("invoice.pdf"), hybrid.pdf());
        Files.write(out.resolve("invoice.esj.json"), EsjWriter.pretty().toBytes(invoice));
        // end

        assertEquals("RE-2026-0042", invoiceNumber, "the value the snippet reads out of the XML");
        assertEquals("CII", fromPdf.source().orElseThrow().syntax().orElseThrow(),
                "and the hybrid PDF gives a document of its own");
        assertEquals(List.of(), hybrid.report().notes(),
                "this invoice reaches the cross industry invoice whole");
        assertEquals("%PDF-",
                new String(Files.readAllBytes(out.resolve("invoice.pdf")), 0, 5,
                        StandardCharsets.ISO_8859_1),
                "the snippet writes a PDF");
        assertTrue(Files.readString(out.resolve("invoice.esj.json"), StandardCharsets.UTF_8)
                        .contains("\"/BT-1\": \"RE-2026-0042\""),
                "and the ESJ document beside it");
    }

    /** The snippet of {@code docs/templates.md}: a rendering on a branded template. */
    @Test
    void renderOnABrandedTemplate() {
        Path templateFile = Templates.directory().resolve("letterhead.json");
        SemanticDocument document = Corpus.example("standard-invoice");

        // docs/templates.md: Branded render templates
        RenderTemplate template = RenderTemplate.read(templateFile);
        byte[] pdf = new PdfRenderer().render(document, RenderOptions.defaults().with(template));

        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1),
                "the snippet gives a PDF");
        assertTrue(Pdf.flat(pdf).contains(Artwork.SENDER), "on the letterhead of the template");
    }

    @Test
    void renderInBothLanguagesAndOnTwoPapers() {
        SemanticDocument document = Corpus.example("standard-invoice");

        // docs/java-api.md: Rendering an invoice for a reader
        HtmlRenderer html = new HtmlRenderer();
        String german = html.render(document);
        String english = html.render(document, RenderOptions.in(RenderLanguage.ENGLISH));

        RenderResult result = html.renderWithReport(document, RenderOptions.defaults());
        List<ExportNote> notLeftBehind = result.report().notes();

        PdfRenderer pdf = new PdfRenderer();
        byte[] a4 = pdf.render(document);
        byte[] usLetter = pdf.render(document,
                RenderOptions.in(RenderLanguage.ENGLISH).on(PageSize.LETTER));
        byte[] generic = pdf.render(document,
                RenderOptions.defaults().layout(Layout.GENERIC));

        assertNotEquals(german, english, "the two languages are two renderings");
        assertEquals(german, result.html(), "the default options are the German ones");
        assertEquals(List.of(), notLeftBehind, "that example reached the stylesheet whole");
        assertEquals(PageSize.A4.width(), Pdf.pageSize(a4, 1)[0], 0.01f,
                "the default is A4");
        assertArrayEquals(pdf.render(document, RenderOptions.defaults().layout(Layout.LETTER)),
                a4, "and the letter layout");
        assertEquals(PageSize.LETTER.width(), Pdf.pageSize(usLetter, 1)[0], 0.01f,
                "and the snippet asked for Letter");
        assertTrue(Pdf.flat(generic).contains(Word.SELLER.in(RenderLanguage.GERMAN) + " "),
                "and the last one is the generic layout, with its block of parties");
    }
}
