package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The snippets of {@code docs/java-api.md} that use this module, one test per snippet, so
 * that the page cannot describe an API this module does not have. The lines between the
 * comment naming the page and its section and the assertions are the snippet as the page
 * prints it; a change to one is a change to the other.
 */
class ReadmeExamplesTest {

    /** A business case the suite carries in both syntaxes. */
    private static final String INSTANCE =
            "business-cases/standard/02.05a-INVOICE_ubl.xml";

    /**
     * A business case both writers carry whole. Most of the corpus states the seller's other
     * tax registration BT-32, which UBL writes in an element whose tax scheme identifier the
     * semantic model has no term for; `conformance/writers/ubl-roundtrip.md` says what the
     * writer does with it, and a snippet about a complete report needs a document without it.
     */
    private static final String COMPLETE =
            "business-cases/standard/01.01a-INVOICE_ubl.xml";

    @TempDir
    Path directory;

    @Test
    void readsADocumentFromAStream() throws IOException {
        Path invoice = write();
        // docs/java-api.md: Reading UBL and CII without a tree
        StreamingReader reader = new StreamingReader();
        ImportResult result;
        try (InputStream in = Files.newInputStream(invoice)) {
            result = reader.read(in);
        }

        SemanticDocument document = result.document();
        List<ImportNote> notes = result.report().notes();
        // end

        assertEquals("UBL", document.source().orElseThrow().syntax().orElseThrow());
        assertTrue(notes.isEmpty(), "this instance costs the reader no observation");
        assertTrue(document.values().size() > 100);
    }

    @Test
    void readsWithBoundsOfItsCallersChoosing() throws IOException {
        // docs/java-api.md: Reading UBL and CII without a tree
        StreamingReader large = new StreamingReader(ReaderOptions.builder()
                .limits(Limits.builder().maxValues(5_000_000).build())
                .maxInputBytes(256L * 1024 * 1024)
                .build());
        // end

        assertEquals(5_000_000, large.options().limits().maxValues());
        assertEquals(256L * 1024 * 1024, large.options().maxInputBytes());
        assertTrue(large.read(Files.readAllBytes(write())).document().values().size() > 100);
    }

    @Test
    void writesTheTwoSyntaxes() {
        SemanticDocument document = new StreamingReader()
                .read(Corpus.instance(COMPLETE)).document();

        // docs/java-api.md: Writing XML
        WriteResult cii = CiiWriter.writeWithReport(document, WriterOptions.defaults());
        WriteResult ubl = UblWriter.writeWithReport(document, WriterOptions.defaults());

        byte[] xml = ubl.xml();
        WriteReport report = ubl.report();
        // end

        assertTrue(new String(cii.xml(), StandardCharsets.UTF_8)
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(new String(xml, StandardCharsets.UTF_8).contains("<Invoice "));
        assertEquals(BindingSyntax.UBL_INVOICE, report.syntax());
        assertTrue(report.isComplete(), report.notes().toString());

        // docs/java-api.md: Writing XML
        List<String> lost = new ArrayList<>();
        if (!report.isComplete()) {
            for (WriteNote note : report.notes()) {
                lost.add(note.toString());   // kind, semantic path and reason
            }
        }
        // end

        assertTrue(lost.isEmpty(), lost.toString());
    }

    @Test
    void refusesARootElementOfNoSyntaxItReads() {
        assertThrows(BindingSyntaxException.class,
                () -> new StreamingReader().read(Documents.utf8("<order xmlns=\"urn:x\"/>")));
    }

    private Path write() throws IOException {
        Path file = directory.resolve("invoice-ubl.xml");
        Files.write(file, Corpus.instance(INSTANCE));
        return file;
    }
}
