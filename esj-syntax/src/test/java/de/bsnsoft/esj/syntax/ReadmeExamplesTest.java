package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The snippets of the section "Validating against the official artefacts" of
 * {@code docs/java-api.md}, one test per snippet, so that the page cannot describe an API
 * this module does not have. The lines between the comment naming the page and its section
 * and the assertions are the snippet as the page prints it; a change to one is a change to
 * the other. The {@code ReadmeExamplesTest} of {@code esj-xr} does the same for the importer.
 *
 * <p>The file the snippets read is an instance of the conformance corpus, written into a
 * temporary directory so that a snippet can call {@code Files.readAllBytes} the way a
 * caller would.
 */
class ReadmeExamplesTest {

    private static final String INSTANCE = "business-cases/standard/01.01a-INVOICE_ubl.xml";

    @TempDir
    Path directory;

    private Path invoice() throws IOException {
        Path file = directory.resolve("invoice.xml");
        Files.write(file, Corpus.instance(INSTANCE));
        return file;
    }

    @Test
    void validateADocumentAgainstItsProfile() throws IOException {
        Path invoice = invoice();

        // docs/java-api.md: Validating against the official artefacts
        SyntaxReport report = SyntaxValidator.validate(Files.readAllBytes(invoice));

        Verdict verdict = report.verdict();
        List<SyntaxFinding> fatal = report.fatal();
        List<SyntaxFinding> warnings = report.warnings();

        assertEquals(Verdict.VALID, verdict);
        assertEquals(List.of(), fatal);
        assertEquals(List.of("BR-DE-TMP-32"),
                warnings.stream().map(SyntaxFinding::code).toList(),
                "the artefacts of this instance report one thing, for information");
    }

    @Test
    void sayWhatRanAndWhatDidNot() throws IOException {
        Path invoice = invoice();

        // docs/java-api.md: Validating against the official artefacts
        SyntaxOptions options = SyntaxOptions.defaults()
                .withMaxInputBytes(16L * 1024 * 1024)
                .withMaxRuntime(Duration.ofSeconds(30));
        SyntaxReport report = SyntaxValidator.validate(Files.readAllBytes(invoice), options);

        List<ComponentRun> ran = report.ran();
        List<SkippedComponent> skipped = report.skipped();
        Optional<String> profileNote = report.profileNote();

        assertEquals(3, ran.size(), "the schema and both rule sets ran");
        assertEquals(3, skipped.size(), "the components of the other syntax did not");
        assertEquals(Optional.empty(), profileNote,
                "the pack recognizes the profile this instance names");
    }

    @Test
    void sayWhichPacksAreAvailable() {
        // docs/java-api.md: Validating against the official artefacts
        for (Pack pack : Packs.bundled()) {
            String identity = pack.directory();
            SortedSet<String> licenses = pack.licenses();

            assertFalse(identity.isEmpty(), "a pack names itself");
            assertFalse(licenses.isEmpty(), "a pack names the licences of its components");
        }

        assertTrue(Packs.bundled().size() >= 1, "at least one pack is packaged");
    }
}
