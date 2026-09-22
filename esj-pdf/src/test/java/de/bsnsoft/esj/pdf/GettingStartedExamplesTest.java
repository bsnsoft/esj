package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.StreamingReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The snippet of <b>Read an invoice</b> in {@code docs/getting-started.md}: an XML invoice and
 * a hybrid PDF read into one kind of document, and one value addressed by its business term.
 * The lines between the comment and the assertions are the snippet as the page prints it; a
 * change to one is a change to the other. Both files are of the conformance corpus, written
 * into a temporary directory so that the snippet can call {@code Files.readAllBytes} the way
 * a caller would. The README shows reading and generating together, which
 * {@code esj-render} pins.
 */
class GettingStartedExamplesTest {

    /** A UBL instance of the corpus. */
    private static final String UBL = "business-cases/standard/01.01a-INVOICE_ubl.xml";

    /** The hybrid PDF of the corpus, a Factur-X file carrying a CII invoice. */
    private static final String PDF = "/conformance/pdf/factur-x.pdf";

    @TempDir
    Path directory;

    @Test
    void readAnInvoiceFromXmlAndFromAPdf() throws IOException {
        Path xmlFile = write("invoice.xml", Conformance.instance(UBL));
        Path pdfFile = write("invoice.pdf", bytes(PDF));

        // docs/getting-started.md: Read an invoice
        SemanticDocument fromXml = new StreamingReader().read(Files.readAllBytes(xmlFile)).document();
        SemanticDocument fromPdf = PdfInvoiceImporter.importPdf(Files.readAllBytes(pdfFile)).document();
        String invoiceNumber = fromXml.value(SemanticPath.of("/BT-1"))
                .map(SemanticValue::asString).orElseThrow();

        assertEquals("UBL", fromXml.source().orElseThrow().syntax().orElseThrow());
        assertEquals("CII", fromPdf.source().orElseThrow().syntax().orElseThrow());
        assertEquals("123456XX", invoiceNumber);
        assertTrue(fromPdf.value(SemanticPath.of("/BT-1")).isPresent());
    }

    private Path write(String name, byte[] content) throws IOException {
        Path file = directory.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static byte[] bytes(String resource) {
        try (InputStream in = GettingStartedExamplesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "the resource " + resource + " is on the test classpath");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
