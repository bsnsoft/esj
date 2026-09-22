package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the tool does with a PDF, which is what it does with the invoice inside it.
 *
 * <p>The first test is the one that matters: the same invoice, once as a file of XML and
 * once inside a container, has to produce the same document byte for byte. Everything else
 * here is about the answers a container can force that a file cannot — no invoice, several
 * invoices, one this project does not read — and that each of them leaves with its own exit
 * code, because a caller branches on the code and never on the English.
 */
class PdfInputTest {

    private static final String CII =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml";

    /** The hybrid invoice of the repository, which the pages that show the tool use. */
    private static final String EXAMPLE = "conformance/pdf/factur-x.pdf";

    private static final String UBL =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml";

    @TempDir
    private Path directory;

    @Test
    void readsTheInvoiceOutOfAContainerAsIfItHadBeenTheFile() {
        Cli.Run direct = Cli.run(Fixtures.bytes(CII), "convert", "-");
        Cli.Run hybrid = Cli.run(TestPdfs.facturX(Fixtures.bytes(CII)), "convert", "-");
        assertEquals(ExitCode.SUCCESS, direct.exitCode());
        assertEquals(ExitCode.SUCCESS, hybrid.exitCode(), hybrid.err());
        assertArrayEquals(direct.out(), hybrid.out(),
                "a container is a wrapper and changes nothing about the document inside it");
    }

    @Test
    void readsAUblInvoiceOutOfAContainerToo() {
        Cli.Run direct = Cli.run(Fixtures.bytes(UBL), "convert", "-");
        Cli.Run hybrid = Cli.run(TestPdfs.facturX("xrechnung.xml", Fixtures.bytes(UBL),
                "XRECHNUNG"), "convert", "-");
        assertEquals(ExitCode.SUCCESS, hybrid.exitCode(), hybrid.err());
        assertArrayEquals(direct.out(), hybrid.out());
    }

    @Test
    void rendersTheInvoiceOutOfAContainerAsIfItHadBeenTheFile() {
        String target = directory.resolve("out.pdf").toString();
        Cli.Run direct = Cli.run(Fixtures.bytes(CII), "render", "-", "--out", target);
        assertEquals(ExitCode.SUCCESS, direct.exitCode(), direct.err());
        byte[] fromXml = read(Path.of(target));
        Cli.Run hybrid = Cli.run(TestPdfs.facturX(Fixtures.bytes(CII)), "render", "-",
                "--out", target);
        assertEquals(ExitCode.SUCCESS, hybrid.exitCode(), hybrid.err());
        assertArrayEquals(fromXml, read(Path.of(target)),
                "a container is a wrapper and changes nothing about the rendering");
    }

    @Test
    void validateReportsTheContainerBesideTheInvoice() {
        String path = pdf("invoice.pdf", TestPdfs.facturX(Fixtures.bytes(CII)));
        Cli.Run run = Cli.run("validate", path);
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err() + run.text());
        String text = run.text();
        assertTrue(text.contains("Detected:         " + Container.LABEL), text);
        assertTrue(text.contains("Embedded invoice: \"factur-x.xml\" (CII,"), text);
        assertTrue(text.contains("Profile:          XRECHNUNG"), text);
        assertTrue(text.contains("Container\n"), text);
        assertTrue(text.contains("PDF structure"), text);
        assertTrue(text.contains("PDF/A-3 declared"), text);
        assertTrue(text.contains("PDF/A-3B — declared, not validated"), text);
        assertTrue(text.contains("AFRelationship \"Alternative\""), text);
        assertTrue(text.contains("Container:        OK"), text);
        assertTrue(text.contains("Invoice:          VALID"), text);
        assertTrue(text.contains("Detected syntax:  CII"), text);
    }

    @Test
    void refusesAContainerWithSeveralInvoicesAndListsThem() {
        byte[] both = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach("xrechnung.xml", Fixtures.bytes(UBL))
                .xmp(TestPdfs.xmp("factur-x.xml", TestPdfs.XRECHNUNG))
                .build();
        Cli.Run run = Cli.run("validate", pdf("two.pdf", both));
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("2 attachments that could be the electronic invoice"),
                run.err());
        assertTrue(run.err().contains("1  \"factur-x.xml\" (CII,"), run.err());
        assertTrue(run.err().contains("2  \"xrechnung.xml\" (UBL invoice,"), run.err());
        assertTrue(run.err().contains("use --attachment <name>"), run.err());
    }

    @Test
    void namingTheAttachmentSettlesIt() {
        byte[] both = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach("xrechnung.xml", Fixtures.bytes(UBL))
                .build();
        Cli.Run run = Cli.run(both, "validate", "-", "--attachment", "xrechnung.xml");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains("Detected syntax:  UBL Invoice"), run.text());
    }

    @Test
    void refusesANameNoAttachmentCarries() {
        Cli.Run run = Cli.run(TestPdfs.facturX(Fixtures.bytes(CII)), "validate", "-",
                "--attachment", "nothing.xml");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("carries no attachment named \"nothing.xml\""),
                run.err());
    }

    @Test
    void refusesAnAttachmentThatCarriesNoInvoice() {
        byte[] mixed = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach(new TestPdfs.Attachment("logo.png", new byte[] {1, 2, 3},
                        "image/png", null, false))
                .build();
        Cli.Run run = Cli.run(mixed, "convert", "-", "--attachment", "logo.png");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("carries no electronic invoice"), run.err());
    }

    @Test
    void saysSoWhenThereIsNoStructuredInvoiceAtAll() {
        Cli.Run run = Cli.run(TestPdfs.builder().build(), "validate", "-");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("the PDF contains no structured invoice representation"),
                run.err());
        assertTrue(run.err().contains("nothing is read off the page"), run.err());
    }

    @Test
    void saysSoWhenNoAttachmentIsAnInvoice() {
        byte[] neither = TestPdfs.builder()
                .attach(new TestPdfs.Attachment("notes.txt",
                        "an invoice is not printed here".getBytes(StandardCharsets.UTF_8),
                        "text/plain", null, false))
                .build();
        Cli.Run run = Cli.run(neither, "inspect", "-");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("the PDF contains no structured invoice representation"),
                run.err());
        assertTrue(run.err().contains("\"notes.txt\" (not XML"), run.err());
    }

    @Test
    void refusesZugferd1AsUnsupportedRatherThanAsBroken() {
        byte[] old = TestPdfs.facturX(TestPdfs.ZUGFERD_1, TestPdfs.ZUGFERD_1_INVOICE, "BASIC");
        Cli.Run run = Cli.run(old, "validate", "-");
        assertEquals(ExitCode.UNSUPPORTED, run.exitCode(), run.err());
        assertTrue(run.err().contains("ZUGFeRD 1.0"), run.err());
    }

    @Test
    void refusesAnEncryptedContainer() {
        byte[] locked = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .userPassword("secret")
                .build();
        Cli.Run run = Cli.run(locked, "validate", "-");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("encrypted"), run.err());
    }

    @Test
    void refusesBytesThatAreNoPdfThisReaderOpens() {
        byte[] broken = Arrays.copyOf(TestPdfs.facturX(Fixtures.bytes(CII)), 600);
        Cli.Run run = Cli.run(broken, "validate", "-");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("as a PDF"), run.err());
    }

    @Test
    void aBoundOnThePdfIsNoVerdictOnTheInvoice() {
        Cli.Run run = Cli.run(TestPdfs.facturX(Fixtures.bytes(CII)), "validate", "-",
                "--max-pdf-bytes", "2k");
        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        assertTrue(run.err().contains("--max-pdf-bytes"), run.err());
    }

    @Test
    void aBoundOnTheNumberOfAttachmentsIsNoVerdictEither() {
        byte[] many = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach(new TestPdfs.Attachment("a.txt", new byte[] {1}, "text/plain",
                        null, false))
                .attach(new TestPdfs.Attachment("b.txt", new byte[] {2}, "text/plain",
                        null, false))
                .build();
        Cli.Run run = Cli.run(many, "validate", "-", "--max-attachments", "2");
        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        assertTrue(run.err().contains("--max-attachments"), run.err());
    }

    @Test
    void theSmallestProfilesAreNotEn16931Invoices() {
        byte[] minimum = TestPdfs.withSpecificationIdentifier(Fixtures.bytes(CII),
                "urn:factur-x.eu:1p0:minimum");
        Cli.Run run = Cli.run(TestPdfs.facturX("factur-x.xml", minimum, "MINIMUM"),
                "validate", "-");
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains(
                "profile MINIMUM: not an EN 16931 invoice — EN 16931 validation not applicable"),
                run.text());
        assertTrue(run.text().contains("Model (L2):                not checked"), run.text());
        assertTrue(run.text().contains("Invoice:          NOT CHECKED (profile MINIMUM)"),
                run.text());
    }

    @Test
    void inspectShowsWhatTheContainerCarriesAndWhatItDeclares() {
        Cli.Run run = Cli.run(TestPdfs.facturX(Fixtures.bytes(CII)), "inspect", "-");
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err() + run.text());
        String text = run.text();
        assertTrue(text.contains("Detected:                   " + Container.LABEL), text);
        assertTrue(text.contains("PDF/A (declared):           PDF/A-3B — declared,"
                + " not validated"), text);
        assertTrue(text.contains("Factur-X profile:           XRECHNUNG"), text);
        assertTrue(text.contains("Attachments:                1"), text);
        assertTrue(text.contains("media type \"text/xml\""), text);
        assertTrue(text.contains("AFRelationship \"Alternative\", in /AF"), text);
        assertTrue(text.contains("Container\n"), text);
        assertTrue(text.contains("Invoice:          INDETERMINATE (missing from the check:"
                + " syntax-binding (not-run-by-this-command))"),
                "this command runs no official artefact, so the invoice line of a container"
                        + " says no more than the last line does: " + text);
    }

    @Test
    void jsonCarriesTheContainerBesideTheLayers() {
        Cli.Run run = Cli.run(TestPdfs.facturX(Fixtures.bytes(CII)), "validate", "-",
                "--output", "json");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String json = run.text();
        assertTrue(json.contains("\"container\": {"), json);
        assertTrue(json.contains("\"attachment\": \"factur-x.xml\""), json);
        assertTrue(json.contains("\"kind\": \"CII\""), json);
        assertTrue(json.contains("\"profile\": \"XRECHNUNG\""), json);
        assertTrue(json.contains("\"en16931Invoice\": true"), json);
        assertTrue(json.contains("\"declared\": \"PDF/A-3B\""), json);
        assertTrue(json.contains("\"validated\": false"), json);
        assertTrue(json.contains("\"conformanceLevel\": \"XRECHNUNG\""), json);
        assertTrue(json.contains("\"relationship\": \"Alternative\""), json);
    }

    @Test
    void jsonSaysThereIsNoContainerWhereTheInputWasAFile() {
        Cli.Run run = Cli.run(Fixtures.bytes(CII), "validate", "-", "--output", "json");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"container\": null"), run.text());
        assertTrue(run.text().contains("\"xml\": {"), run.text());
    }

    @Test
    void everyCommandThatTakesAnInputTakesAPdf() {
        byte[] pdf = TestPdfs.facturX(Fixtures.bytes(CII));
        assertEquals(ExitCode.SUCCESS, Cli.run(pdf, "get", "-", "/BT-1").exitCode());
        assertEquals(ExitCode.SUCCESS, Cli.run(pdf, "list", "-").exitCode());
        assertEquals(ExitCode.SUCCESS, Cli.run(pdf, "canonicalize", "-").exitCode());
        String path = pdf("a.pdf", pdf);
        Cli.Run diff = Cli.run("diff", path, path);
        assertEquals(ExitCode.SUCCESS, diff.exitCode(), diff.err());
    }

    @Test
    void theExampleContainerCarriesTheCorpusInvoice() {
        Cli.Run run = Cli.run("extract", Fixtures.file(directory, EXAMPLE));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertArrayEquals(Fixtures.bytes(CII), run.out(),
                "the checked-in container carries the corpus invoice and cannot drift from it");
    }

    @Test
    void extractWritesTheAttachmentUnchanged() {
        Cli.Run run = Cli.run(TestPdfs.facturX(Fixtures.bytes(CII)), "extract", "-");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertArrayEquals(Fixtures.bytes(CII), run.out(),
                "the attachment leaves as it arrived, so the next tool reads what was sent");
    }

    @Test
    void extractWritesToAFileWhenAskedTo() {
        String target = directory.resolve("out.xml").toString();
        Cli.Run run = Cli.run(TestPdfs.facturX(Fixtures.bytes(CII)), "extract", "-",
                "--out", target);
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals(0, run.out().length, "the document went to the file, not to the output");
        assertArrayEquals(Fixtures.bytes(CII), read(Path.of(target)));
    }

    @Test
    void extractListsWhatTheContainerCarries() {
        byte[] mixed = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach(new TestPdfs.Attachment("logo.png", new byte[] {1, 2, 3},
                        "image/png", null, false))
                .build();
        Cli.Run run = Cli.run(mixed, "extract", "-", "--list");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String text = run.text();
        assertTrue(text.contains("Attachments:  2"), text);
        assertTrue(text.contains("1  \"factur-x.xml\" (CII,"), text);
        assertTrue(text.contains("2  \"logo.png\" (not XML,"), text);
        assertTrue(text.contains("no AFRelationship"), text);
    }

    @Test
    void extractRefusesAnInputThatIsNoContainer() {
        Cli.Run run = Cli.run(Fixtures.bytes(CII), "extract", "-");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("is not a PDF"), run.err());
    }

    @Test
    void refusesAContainerWhoseSecondInvoiceHidesBehindALongProlog() {
        // The window an attachment is classified by is not a promise the file makes; a
        // producer that puts a long comment in front of the root element decides what is
        // inside it. The second invoice here is invalid on its own, so a container read as
        // if it carried one would report VALID for a file carrying an invalid invoice.
        byte[] both = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach("xrechnung.xml", padded(Fixtures.bytes(CII)))
                .xmp(TestPdfs.xmp("factur-x.xml", "EN 16931"))
                .build();

        Cli.Run run = Cli.run(both, "validate", "-");

        assertEquals(ExitCode.INPUT, run.exitCode(), run.text() + run.err());
        assertTrue(run.err().contains("2 attachments that could be the electronic invoice"),
                run.err());
        assertTrue(run.err().contains("root element beyond the window"), run.err());
    }

    @Test
    void refusesANameThatTwoAttachmentsCarryAndOffersThePosition() {
        byte[] twice = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach("factur-x.xml", Fixtures.bytes(UBL))
                .build();

        Cli.Run without = Cli.run(twice, "validate", "-");
        assertEquals(ExitCode.INPUT, without.exitCode(), without.text());
        assertTrue(without.err().contains("--attachment-index"), without.err());

        Cli.Run named = Cli.run(twice, "validate", "-", "--attachment", "factur-x.xml");
        assertEquals(ExitCode.INPUT, named.exitCode(), named.text());
        assertTrue(named.err().contains("2 attachments named \"factur-x.xml\""), named.err());

        Cli.Run second = Cli.run(twice, "validate", "-", "--attachment-index", "2");
        assertEquals(ExitCode.SUCCESS, second.exitCode(), second.err() + second.text());
        assertTrue(second.text().contains("Detected syntax:  UBL Invoice"), second.text());

        Cli.Run extract = Cli.run(twice, "extract", "-", "--attachment", "factur-x.xml");
        assertEquals(ExitCode.INPUT, extract.exitCode(), extract.err());

        String file = pdf("twice.pdf", twice);
        Cli.Run diff = Cli.run("diff", file, file, "--attachment", "factur-x.xml");
        assertEquals(ExitCode.INPUT, diff.exitCode(), diff.err());
        assertTrue(diff.err().contains("does not say which of them is meant"), diff.err());

        Cli.Run beyond = Cli.run(twice, "validate", "-", "--attachment-index", "3");
        assertEquals(ExitCode.INPUT, beyond.exitCode(), beyond.err());
        assertTrue(beyond.err().contains("has no attachment at position 3"), beyond.err());
    }

    @Test
    void refusesTwoSelectorsThatNameTwoDifferentAttachments() {
        // The ambiguity message offers both switches on one line, so a caller passing
        // both is the expected mistake rather than an exotic one. A run answered about
        // the indexed attachment while the name it was given points somewhere else is a
        // verdict about a document the caller did not select.
        byte[] two = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach(new TestPdfs.Attachment("logo.png", new byte[] {1, 2, 3},
                        "image/png", null, false))
                .build();

        Cli.Run mismatch = Cli.run(two, "validate", "-", "--attachment", "logo.png",
                "--attachment-index", "1");
        assertEquals(ExitCode.INPUT, mismatch.exitCode(), mismatch.text());
        assertTrue(mismatch.err().contains("name two different attachments"), mismatch.err());

        Cli.Run absent = Cli.run(two, "validate", "-", "--attachment", "nosuch.xml",
                "--attachment-index", "1");
        assertEquals(ExitCode.INPUT, absent.exitCode(), absent.text());

        // The two agreeing is the documented way through a container with two
        // attachments of one name, and it stays open.
        Cli.Run agreeing = Cli.run(two, "validate", "-", "--attachment", "factur-x.xml",
                "--attachment-index", "1");
        assertEquals(ExitCode.SUCCESS, agreeing.exitCode(), agreeing.err() + agreeing.text());
    }

    @Test
    void namesTheAttachmentTheVerdictIsAboutByItsPosition() {
        // Two attachments of one name and one declared size: the name in the report
        // describes either of them, and an archived report has to say which one was read.
        byte[] twice = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach("factur-x.xml", Fixtures.bytes(UBL))
                .build();

        Cli.Run second = Cli.run(twice, "validate", "-", "--attachment-index", "2");
        assertEquals(ExitCode.SUCCESS, second.exitCode(), second.err() + second.text());
        assertTrue(second.text().contains("position 2"), second.text());

        Cli.Run json = Cli.run(twice, "validate", "-", "--attachment-index", "2",
                "--output", "json");
        assertEquals(ExitCode.SUCCESS, json.exitCode(), json.err() + json.text());
        assertTrue(json.text().contains("\"attachmentIndex\": 2"), json.text());
        assertTrue(json.text().contains("\"position\": 2,\n        \"selected\": true"),
                json.text());
        assertTrue(json.text().contains("\"position\": 1,\n        \"selected\": false"),
                json.text());
    }

    @Test
    void saysThatAContainerCarriesSeveralCandidatesEvenWhenTheCallerResolvedIt() {
        // A container declares one invoice attachment. The caller took responsibility for
        // which of the two is read; what the file carries beside it is still part of the
        // answer, and the list of attachments is a listing rather than a verdict.
        byte[] both = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach("xrechnung.xml", Fixtures.bytes(UBL))
                .build();

        Cli.Run run = Cli.run(both, "validate", "-", "--attachment-index", "1");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains("PDF-EMBEDDED-SEVERAL"), run.text());
        assertTrue(run.text().contains("xrechnung.xml"), run.text());
    }

    @Test
    void doesNotSayThatAnUnclassifiedAttachmentIsNoInvoice() {
        // The one attachment is a valid invoice behind a long comment. "The PDF contains
        // no structured invoice representation" would be a statement this tool never
        // established, and an auditor quoting it back to a supplier would be quoting a
        // falsehood.
        byte[] only = TestPdfs.builder()
                .attach("factur-x.xml", padded(Fixtures.bytes(CII)))
                .build();

        Cli.Run run = Cli.run(only, "validate", "-");
        assertEquals(ExitCode.INPUT, run.exitCode(), run.text());
        assertTrue(run.err().contains("was not established"), run.err());
        assertTrue(run.err().contains("--attachment"), run.err());

        // And the way out the refusal offers is open: the caller names the attachment and
        // the bytes decide, with the container report saying what was not established.
        Cli.Run named = Cli.run(only, "validate", "-", "--attachment", "factur-x.xml");
        assertEquals(ExitCode.SUCCESS, named.exitCode(), named.err() + named.text());
        assertTrue(named.text().contains("PDF-EMBEDDED-UNDETERMINED"), named.text());
    }

    @Test
    void refusesAContainerWhoseObjectStreamInflatesWithoutEnd() {
        // The library decodes an object stream while it opens the file, before any
        // attachment has been looked at, so a bound applied to the attachments is no
        // bound at all against this. Reaching it is a limit of the run and not a defect
        // of any invoice: exit 7, and no verdict.
        byte[] pdf = TestPdfs.objectStreamBomb(512L * 1024 * 1024);

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err() + run.text());
        assertTrue(run.err().contains("object stream"), run.err());
        assertTrue(run.err().contains("--max-pdf-bytes"),
                "a refusal by a bound names the switch that raises it: " + run.err());
        assertFalse(run.text().contains("INVALID"),
                "a bound of the run says nothing about the invoice inside the file");
    }

    @Test
    void refusesAContainerWhoseObjectStreamDeclaresAPredictorRowNoInvoiceNeeds() {
        // The row is allocated twice from the dictionary before either decoder writes a
        // byte, so a bound on what the decode produces never sees it. No switch of this
        // version moves that ceiling, and the refusal says so rather than sending its
        // reader to run the same file again.
        byte[] pdf = TestPdfs.objectStreamWith("/Type /ObjStm /N 1 /First 8"
                + " /Filter /FlateDecode /DecodeParms << /Predictor 12 /Colors 1"
                + " /BitsPerComponent 8 /Columns 268435455 >> /Length 8", new byte[8]);

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err() + run.text());
        assertTrue(run.err().contains("predictor row"), run.err());
        assertTrue(run.err().contains("no profile and no switch"), run.err());
        assertFalse(run.text().contains("INVALID"),
                "a bound of the run says nothing about the invoice inside the file");
    }

    @Test
    void refusesAContainerWhoseObjectStreamIsFilteredWithAChainThisReaderDoesNotRun() {
        byte[] raster = {
            (byte) 0xFF, (byte) 0xD8,
            (byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08, 0x4E, 0x20, 0x4E, 0x20,
            0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
            (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01};
        byte[] pdf = TestPdfs.objectStreamWith("/Type /ObjStm /N 1 /First 8"
                + " /Filter /DCTDecode /Length " + raster.length, raster);

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err() + run.text());
        assertTrue(run.err().contains("a chain this reader does not decode"), run.err());
    }

    @Test
    void refusesAContainerWhoseObjectStreamDeclaresMoreObjectsThanTheBound() {
        byte[] pdf = TestPdfs.objectStreamWith("/Type /ObjStm /N 3000000 /First 8"
                + " /Filter /FlateDecode /Length 8", new byte[8]);

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err() + run.text());
        assertTrue(run.err().contains("3000000 objects"), run.err());
        assertTrue(run.err().contains("--max-pdf-bytes"),
                "the bound follows the bound on the file: " + run.err());
    }

    @Test
    void refusesAnEncryptedContainerThatWouldOpenWithTheEmptyPassword() {
        byte[] locked = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .userPassword("")
                .build();

        Cli.Run run = Cli.run(locked, "validate", "-");

        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("PDF/A forbids encryption"), run.err());
    }

    @Test
    void refusesAContainerThatDeclaresEncryptionInATrailerTheParserRebuilt() {
        // A broken startxref and no /Info: the rebuilt trailer loses /Encrypt, so the
        // question "is this file encrypted" cannot be asked of the trailer alone. The
        // file declares encryption, PDF/A forbids it, and no verdict is printed.
        byte[] pdf = TestPdfs.declaredEncryptionWithoutAUsableTrailer();

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.INPUT, run.exitCode(), run.err() + run.text());
        assertTrue(run.err().contains("PDF/A forbids encryption"), run.err());
        assertFalse(run.text().contains("VALID"),
                "a file that declares encryption gets no verdict: " + run.text());
    }

    @Test
    void refusesAContainerWhoseObjectStreamDeclaresAPredictorThisFormatDoesNotDefine() {
        // /Colors 8 by /BitsPerComponent 15790321 by /Columns -17 is a negative row in
        // sixty-four bits and 268 435 455 in the thirty-two the library computes in. The
        // stage is malformed rather than large, so it is exit 2 and not exit 7.
        byte[] pdf = TestPdfs.objectStreamWith("/Type /ObjStm /N 1 /First 8"
                + " /Filter /FlateDecode /DecodeParms << /Predictor 12 /Colors 8"
                + " /BitsPerComponent 15790321 /Columns -17 >> /Length 8", new byte[8]);

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.INPUT, run.exitCode(), run.err() + run.text());
        assertTrue(run.err().contains("no predictor stage this format defines"), run.err());
    }

    @Test
    void refusesAContainerWhoseDocumentAttachmentCannotBeDecoded() {
        // Two attachments the container declares to be what the document is, one of them
        // filtered with something this reader does not decode. Answering about the other
        // one would let a changed stream filter turn an ambiguity into a verdict, and an
        // unattended caller reading the exit code would see nothing wrong.
        byte[] pdf = TestPdfs.builder()
                .attach(new TestPdfs.Attachment("factur-x.xml",
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0},
                        "image/jpeg", TestPdfs.ALTERNATIVE, true, COSName.DCT_DECODE))
                .attach("other.xml", Fixtures.bytes(CII))
                .build();

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.INPUT, run.exitCode(), run.err() + run.text());
        assertTrue(run.err().contains("could be the electronic invoice"), run.err());
    }

    @Test
    void readsAnInvoiceThatDeclaresNoRelationshipBesideAnEnclosure() {
        // The invoice carries no /AFRelationship at all, which this tool reports on and
        // still reads, and the enclosure beside it has a prolog longer than the window.
        // The rule that sets a supplement aside is about the supplement: what the invoice
        // declares about itself does not decide whether the container is ambiguous.
        byte[] prolog = ("<!--" + "x".repeat(9000) + "-->").getBytes(StandardCharsets.UTF_8);
        byte[] terms = new byte[prolog.length + Fixtures.bytes(CII).length];
        System.arraycopy(prolog, 0, terms, 0, prolog.length);
        System.arraycopy(Fixtures.bytes(CII), 0, terms, prolog.length,
                Fixtures.bytes(CII).length);
        byte[] pdf = TestPdfs.builder()
                .attach(new TestPdfs.Attachment("factur-x.xml", Fixtures.bytes(CII),
                        TestPdfs.XML, null, true))
                .attach(new TestPdfs.Attachment("terms.xml", terms, TestPdfs.XML,
                        "Supplement", false))
                .build();

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains("Invoice:          VALID"), run.text());
    }

    @Test
    void readsAnInvoiceBesideAnAttachmentItCannotDecode() {
        // The stream of the second attachment declares a filter whose decoder allocates
        // from the numbers in the file, and this reader does not run those. Refusing the
        // whole container for it would hand anyone who can attach a picture the power to
        // deny the recipient a verdict.
        byte[] pdf = TestPdfs.builder()
                .attach("factur-x.xml", Fixtures.bytes(CII))
                .attach(TestPdfs.Attachment.filtered("logo.jpg",
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0}))
                .xmp(TestPdfs.xmp("factur-x.xml", TestPdfs.XRECHNUNG))
                .build();

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains("Invoice:          VALID"), run.text());
        assertTrue(run.text().contains("PDF-EMBEDDED-UNREADABLE"), run.text());
    }

    @Test
    void readsAnInvoiceInsideAContainerThatIsWrittenInUtf16() {
        // The container classified the attachment on a decoder that reads every encoding
        // XML allows. Detecting the syntax again on the extracted bytes would replace that
        // answer with one that cannot read a wide encoding.
        byte[] utf16 = new String(Fixtures.bytes(CII), StandardCharsets.UTF_8)
                .replace("encoding=\"UTF-8\"", "encoding=\"UTF-16\"")
                .getBytes(StandardCharsets.UTF_16);

        Cli.Run run = Cli.run(TestPdfs.facturX("factur-x.xml", utf16, "XRECHNUNG"),
                "validate", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains("Detected syntax:  CII"), run.text());
        assertTrue(run.text().contains("Invoice:          VALID"), run.text());
    }

    @Test
    void theJsonOfASmallestProfileSaysWhatTheExitCodeSays() {
        byte[] minimum = TestPdfs.withSpecificationIdentifier(Fixtures.bytes(CII),
                "urn:factur-x.eu:1p0:minimum");

        Cli.Run run = Cli.run(TestPdfs.facturX("factur-x.xml", minimum, "MINIMUM"),
                "validate", "-", "--output", "json");

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err() + run.text());
        // The headline of the object is not "ok": a profile the rules do not apply to is
        // not a defective invoice, and a script reading invoice.ok alone would book a
        // conformant MINIMUM file as a bad one. The layers say it the same way.
        assertTrue(run.text().contains("\"invoice\": {\n    \"checked\": false,\n"
                + "    \"ok\": null,\n"
                + "    \"reason\": \"profile-not-en16931\"\n  }"), run.text());
    }

    @Test
    void theInvoiceLineSaysWhatTheVerdictAndTheExitCodeSay() {
        // The two engines are reported apart, and the invoice line is one answer over
        // both of them. An invoice the structural layers accept and an official artefact
        // rejects is the case that separates them: the report's own verdict, the exit
        // code and the JSON all say no, and the line under Container: has to as well.
        byte[] pdf = TestPdfs.facturX(unbalanced(Fixtures.bytes(CII)));

        Cli.Run run = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains("Model (L2):                OK"), run.text());
        assertTrue(run.text().contains("BR-CO-15"), run.text());
        assertTrue(run.text().contains("Container:        OK"), run.text());
        assertTrue(run.text().contains("Invoice:          INVALID"), run.text());

        Cli.Run json = Cli.run(pdf, "validate", "-", "--output", "json");

        assertEquals(ExitCode.VALIDATION, json.exitCode(), json.err() + json.text());
        assertTrue(json.text().contains("\"invoice\": {\n    \"checked\": true,\n"
                + "    \"ok\": false,\n"
                + "    \"reason\": \"syntax-finding\"\n  }"), json.text());
    }

    @Test
    void inspectSaysWhyItLeavesWithTheCodeItLeavesWith() {
        byte[] minimum = TestPdfs.withSpecificationIdentifier(Fixtures.bytes(CII),
                "urn:factur-x.eu:1p0:minimum");

        Cli.Run run = Cli.run(TestPdfs.facturX("factur-x.xml", minimum, "MINIMUM"),
                "inspect", "-");

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains(
                "profile MINIMUM: not an EN 16931 invoice — EN 16931 validation not"
                        + " applicable"), run.text());
        assertTrue(run.text().contains("Invoice:          NOT CHECKED (profile MINIMUM)"),
                run.text());
    }

    @Test
    void namesThePdfBoundWhenAPdfIsLargerThanItMayBe() {
        Cli.Run run = Cli.run(TestPdfs.facturX(Fixtures.bytes(CII)), "validate", "-",
                "--max-pdf-bytes", "1024");

        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        assertTrue(run.err().contains("bytes a PDF may have in this run"), run.err());
        assertTrue(run.err().contains("--max-pdf-bytes"), run.err());
    }

    /**
     * Returns an invoice whose totals do not add up, which BR-CO-15 of the official
     * Schematron rejects and no structural layer has anything to say about.
     */
    private static byte[] unbalanced(byte[] invoice) {
        return new String(invoice, StandardCharsets.UTF_8)
                .replace("<ram:GrandTotalAmount>336.9</ram:GrandTotalAmount>",
                        "<ram:GrandTotalAmount>999.99</ram:GrandTotalAmount>")
                .replace("<ram:DuePayableAmount>336.9</ram:DuePayableAmount>",
                        "<ram:DuePayableAmount>999.99</ram:DuePayableAmount>")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Returns a document with a comment longer than the classification window in front. */
    private static byte[] padded(byte[] invoice) {
        String text = new String(invoice, StandardCharsets.UTF_8);
        int root = text.indexOf("<rsm:");
        return (text.substring(0, root) + "<!--" + "p".repeat(20000) + "-->"
                + text.substring(root)).getBytes(StandardCharsets.UTF_8);
    }

    /** Writes a PDF into the temporary directory and returns its path. */
    private String pdf(String name, byte[] content) {
        return Fixtures.write(directory, name, content);
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
