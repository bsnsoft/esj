package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportResult;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.validate.ValidationResult;
import de.bsnsoft.esj.validate.ValidationStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The importer snippets of {@code docs/java-api.md}, one test per snippet, so that the page
 * cannot describe an API this module does not have. The lines between the comment naming a
 * page and its section and the assertions are the snippet as that page prints it; a change to
 * one is a change to the other. The {@code ReadmeExamplesTest} of {@code esj-typed} does the
 * same for the snippets of the other modules, and the {@code GettingStartedExamplesTest} of
 * {@code esj-pdf} for the two-line snippet {@code README.md} opens with.
 *
 * <p>The files the snippets read are the two syntaxes of one business case of the
 * conformance corpus, written into a temporary directory so that a snippet can call
 * {@code Files.readAllBytes} the way a caller would. That business case is one of those
 * whose two syntaxes arrive at the same semantic digest, which is what the last snippet of
 * the section claims and what the section around it is about.
 */
class ReadmeExamplesTest {

    /** A business case the suite carries in both syntaxes, and whose pair is identical. */
    private static final String STEM = "business-cases/standard/02.05a-INVOICE";

    @TempDir
    Path directory;

    private Path ubl() throws IOException {
        return write("invoice-ubl.xml", Conformance.instance(STEM + "_ubl.xml"));
    }

    private Path cii() throws IOException {
        return write("invoice-cii.xml", Conformance.instance(STEM + "_uncefact.xml"));
    }

    private Path write(String name, byte[] content) throws IOException {
        Path file = directory.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static String syntaxOf(SemanticDocument document) {
        return document.source().orElseThrow().syntax().orElseThrow();
    }

    @Test
    void importAUblOrACiiDocument() throws IOException {
        Path ublFile = ubl();
        Path ciiFile = cii();
        Path someFile = ciiFile;

        // docs/java-api.md: Reading UBL and CII
        XrImporter importer = new XrImporter();
        SemanticDocument fromUbl = importer.importUbl(Files.readAllBytes(ublFile));
        SemanticDocument fromCii = importer.importCii(Files.readAllBytes(ciiFile));
        SemanticDocument either = importer.importXml(Files.readAllBytes(someFile));

        assertEquals("UBL", syntaxOf(fromUbl));
        assertEquals("CII", syntaxOf(fromCii));
        assertEquals(fromCii, either, "importXml reads the syntax off the root element");
    }

    @Test
    void importADocumentWithItsReport() throws IOException {
        XrImporter importer = new XrImporter();
        Path invoice = ubl();

        // docs/java-api.md: Reading UBL and CII
        ImportResult result = importer.importXmlWithReport(Files.readAllBytes(invoice));

        SemanticDocument document = result.document();
        String syntax = document.source().orElseThrow().syntax().orElseThrow();
        List<ImportNote> notes = result.report().notes();

        assertEquals("UBL", syntax);
        assertEquals(Conformance.sha256(Files.readAllBytes(invoice)),
                document.source().orElseThrow().sha256().orElseThrow());
        assertEquals(List.of(), notes, "this instance reaches the document whole");
    }

    @Test
    void validateAnImportedDocument() throws IOException {
        XrImporter importer = new XrImporter();
        SemanticDocument document = importer.importUbl(Files.readAllBytes(ubl()));

        // docs/java-api.md: Reading UBL and CII
        ValidationResult model = StructuralValidator.validate(
                document, importer.registry(), EnumSet.of(ValidationLayer.L2));
        ValidationStatus modelStatus = model.status();
        List<Finding> modelFindings = model.findings();

        assertEquals(List.of(), modelFindings);
        assertEquals(ValidationStatus.INDETERMINATE, modelStatus,
                "one layer checked is not three, so this result alone is never VALID");
    }

    @Test
    void writeADocumentBackAsAnXrDocument() throws IOException {
        SemanticDocument document = new XrImporter().importUbl(Files.readAllBytes(ubl()));

        // docs/java-api.md: Writing the XR representation
        XrExporter exporter = new XrExporter();
        byte[] xr = exporter.toXr(document);

        ExportResult exported = exporter.toXrWithReport(document);
        List<ExportNote> left = exported.report().notes();

        assertEquals(List.of(), left, "this instance reaches the XR representation whole");
        assertTrue(new String(xr, StandardCharsets.UTF_8).contains("<xr:invoice"));
        assertEquals(document.values(), new XrImporter().fromXr(xr).values());
    }

    @Test
    void canonicalizeAnImportedDocumentAndCompareTheTwoSyntaxes() throws IOException {
        XrImporter importer = new XrImporter();
        SemanticDocument fromUbl = importer.importUbl(Files.readAllBytes(ubl()));
        SemanticDocument fromCii = importer.importCii(Files.readAllBytes(cii()));
        SemanticDocument document = fromUbl;

        // docs/java-api.md: Reading UBL and CII
        byte[] canonical = Canonicalizer.canonicalBytes(document);
        boolean sameInvoice = Canonicalizer.semanticDigest(fromUbl)
                .equals(Canonicalizer.semanticDigest(fromCii));

        assertTrue(new String(canonical, StandardCharsets.UTF_8)
                        .startsWith("{\"format\":\"EN16931-Semantic-JSON\""),
                "the canonical form begins with the envelope in canonical member order");
        assertTrue(sameInvoice, "the two syntaxes of " + STEM + " carry the same invoice");
        assertNotEquals(Canonicalizer.documentDigest(fromUbl),
                Canonicalizer.documentDigest(fromCii),
                "the document digest covers the provenance the semantic digest leaves out");
    }
}
