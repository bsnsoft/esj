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
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The ESJ document a hybrid invoice carries beside its invoice XML, from the command line:
 * what is written, what is shown, and what {@code esj validate} makes of a file whose two
 * machine-readable representations do not say the same thing.
 *
 * <p>The files this tool writes itself are made by the tool; the ones a stranger might send
 * are built here, because what they are is the point of each case and a fixture that says so
 * in its own source is worth more than a binary.
 */
class EsjInPdfCommandsTest {

    /** A document of this repository's own examples, in the format's own syntax. */
    private static final String EXAMPLE = "examples/standard-invoice.esj.json";

    /** A second, different invoice of the same examples. */
    private static final String OTHER = "examples/minimal.esj.json";

    /** An invoice carrying terms of a model extension, which no transport syntax binds. */
    private static final String EXTENDED = "examples/b2c-gross.esj.json";

    /** The name the ESJ document goes in under. */
    private static final String NAME = "invoice.esj.json";

    /** The media type its embedded file stream declares. */
    private static final String JSON = "application/json";

    /** The relationship it is declared with: an enclosure, not the document. */
    private static final String SUPPLEMENT = "Supplement";

    /** The line the report ends the container's verdict with where the file is sound. */
    private static final String CONTAINER_OK = "Container:        OK";

    /** The line it ends with where the container is wrong about itself. */
    private static final String CONTAINER_INVALID = "Container:        INVALID";

    /** The row the container block gains for a file that carries an ESJ document. */
    private static final String ROW = "ESJ document attached";

    /** The line the embedding writes when the ESJ document went in. */
    private static final String ATTACHED =
            "the ESJ document of this invoice is attached beside it as \"invoice.esj.json\"";

    @TempDir
    private Path directory;

    /** The lead use case, with the second attachment in it and checked on the way back. */
    @Test
    void theHybridThisToolWritesCarriesTheEsjDocumentAndValidates() {
        String hybrid = hybrid();

        Cli.Run validated = Cli.run("validate", hybrid);

        assertEquals(ExitCode.SUCCESS, validated.exitCode(),
                validated.text() + validated.err());
        assertTrue(validated.text().contains(ROW), validated.text());
        assertTrue(validated.text().contains("\"invoice.esj.json\", checked against the"
                + " invoice"), validated.text());
        assertTrue(validated.text().contains(CONTAINER_OK), validated.text());
    }

    /**
     * What the second attachment is for: the gross figures a consumer was shown are terms of
     * the B2C extension, the cross industry invoice has no place for them, and they stand in
     * the ESJ document of the same file. The container is sound all the same, because the
     * rule asks of what the ESJ document states beyond the XML only that no binding table
     * binds it.
     */
    @Test
    void theEsjDocumentCarriesTheExtensionTermsTheXmlCannot() {
        String invoice = Fixtures.file(directory, EXTENDED);
        String hybrid = directory.resolve("gross.pdf").toString();
        Cli.Run rendered = Cli.run("render", invoice, "--extension", "b2c",
                "--embed", "cii", "--out", hybrid);
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());
        assertTrue(rendered.err().contains(ATTACHED), rendered.err());

        Cli.Run extracted = Cli.run("extract", hybrid, "--attachment", NAME, "--out", "-");
        assertEquals(ExitCode.SUCCESS, extracted.exitCode(), extracted.err());
        assertTrue(extracted.text().contains("/BT-B2C-010"), extracted.text());

        Cli.Run validated = Cli.run("validate", hybrid, "--extension", "b2c");

        assertEquals(ExitCode.SUCCESS, validated.exitCode(),
                validated.text() + validated.err());
        assertTrue(validated.text().contains(ROW), validated.text());
        assertTrue(validated.text().contains(CONTAINER_OK), validated.text());
        // And the row says what it did not do: those extension terms are exactly the paths
        // the invoice had nothing to say about, so a row reporting the comparison without
        // naming them would claim more than the run established.
        assertTrue(validated.text().contains("paths were not checked"), validated.text());
    }

    /**
     * The form a program reads says the same as the line a person reads.
     *
     * <p>A caller that branches on {@code container.esj.agrees} is entitled to the same
     * reservation the text row makes: where the enclosure carried paths the invoice syntax
     * binds nothing of, {@code agrees} is an answer about the rest of them, and
     * {@code pathsNotChecked} says how many the rest leaves out. Where nothing was left
     * unmeasured the member is absent, so the ordinary hybrid reads as it always did.
     */
    @Test
    void theJsonFormCountsTheUncheckedPathsAsTheTextRowDoes() {
        String invoice = Fixtures.file(directory, EXTENDED);
        String hybrid = directory.resolve("gross-json.pdf").toString();
        Cli.Run rendered = Cli.run("render", invoice, "--extension", "b2c",
                "--embed", "cii", "--out", hybrid);
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());

        Cli.Run reported = Cli.run("validate", hybrid, "--extension", "b2c",
                "--output", "json");
        Cli.Run ordinary = Cli.run("validate", hybrid(), "--output", "json");

        assertEquals(ExitCode.SUCCESS, reported.exitCode(), reported.err());
        assertTrue(reported.text().contains("\"pathsNotChecked\""), reported.text());
        assertEquals(ExitCode.SUCCESS, ordinary.exitCode(), ordinary.err());
        assertFalse(ordinary.text().contains("\"pathsNotChecked\""), ordinary.text());
    }

    /**
     * The row of an enclosure the invoice could have contradicted everywhere claims the
     * whole comparison and counts nothing as unchecked.
     */
    @Test
    void theRowCountsNothingUncheckedWhereTheInvoiceBindsEveryPath() {
        Cli.Run validated = Cli.run("validate", hybrid());

        assertEquals(ExitCode.SUCCESS, validated.exitCode(),
                validated.text() + validated.err());
        assertTrue(validated.text().contains("checked against the invoice"),
                validated.text());
        assertFalse(validated.text().contains("paths were not checked"), validated.text());
    }

    /**
     * Classification is by content, so an attachment that wears the name and the
     * relationship of this project's ESJ document and holds something else is not one, and
     * every row about one is silent about it. A consumer that picks an attachment by name
     * or by relationship, as another vendor's reader may, gets those bytes all the same, so
     * the label is reported with what the content turned out to be.
     */
    @Test
    void anAttachmentWearingTheLabelAndHoldingSomethingElseIsReported() {
        String hybrid = Fixtures.write(directory, "labelled.pdf",
                container(invoiceXml(), "this is not a document at all"
                        .getBytes(StandardCharsets.UTF_8)));

        Cli.Run validated = Cli.run("validate", hybrid);

        assertTrue(validated.text().contains("PDF-EMBEDDED-ESJ-LABEL"), validated.text());
        assertTrue(validated.text().contains("carries the name this project gives the ESJ"
                + " document beside an invoice"), validated.text());
        // An ESJ document is JSON, so the bytes not being XML is no ground for the
        // conclusion; what the line names is the classification that did rule it out.
        assertTrue(validated.text().contains("its content is neither an ESJ document this"
                + " reader reads nor XML"), validated.text());
        assertFalse(validated.text().contains("is not XML, so it is no ESJ document"),
                validated.text());
        assertFalse(validated.text().contains(ROW),
                "there is no ESJ document, so there is no row about one: "
                        + validated.text());
        assertEquals(ExitCode.SUCCESS, validated.exitCode(),
                "and the invoice of the container is its XML, which nothing an enclosure"
                        + " says touches: " + validated.text() + validated.err());
    }

    /** Both commands that embed say in one line that the document went in beside it. */
    @Test
    void bothCommandsSayThatTheEsjDocumentWentIn() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        assertEquals(ExitCode.SUCCESS,
                Cli.run("render", invoice, "--out", pages).exitCode());

        Cli.Run embedded = Cli.run("embed", pages, invoice,
                "--out", directory.resolve("embedded.pdf").toString());
        Cli.Run rendered = Cli.run("render", invoice, "--embed", "cii",
                "--out", directory.resolve("rendered.pdf").toString());

        assertTrue(embedded.err().contains(ATTACHED), embedded.err());
        assertTrue(rendered.err().contains(ATTACHED), rendered.err());
    }

    /** The switch leaves it out, on both commands, and says nothing was attached. */
    @Test
    void theSwitchLeavesTheEsjDocumentOut() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        Cli.run("render", invoice, "--out", pages);
        String plain = directory.resolve("plain.pdf").toString();

        Cli.Run embedded = Cli.run("embed", pages, invoice, "--no-esj", "--out", plain);

        assertEquals(ExitCode.SUCCESS, embedded.exitCode(), embedded.err());
        assertTrue(embedded.err().contains("turned it off"), embedded.err());
        Cli.Run listed = Cli.run("extract", "--list", plain);
        assertFalse(listed.text().contains(NAME), listed.text());
        assertTrue(listed.text().contains("Attachments:  1"), listed.text());
    }

    /** The two routes to a file without it are the same file, byte for byte. */
    @Test
    void theSwitchGivesTheSameFileFromBothCommands() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        String twoSteps = directory.resolve("two-steps.pdf").toString();
        String oneStep = directory.resolve("one-step.pdf").toString();
        Cli.run("render", invoice, "--out", pages);
        Cli.run("embed", pages, invoice, "--no-esj", "--out", twoSteps);
        Cli.run("render", invoice, "--embed", "cii", "--no-esj", "--out", oneStep);

        assertArrayEquals(read(twoSteps), read(oneStep));
    }

    /** Writing the same invoice twice gives the same file, attachment and all. */
    @Test
    void theFileIsStillAFunctionOfTheInvoice() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String first = directory.resolve("first.pdf").toString();
        String second = directory.resolve("second.pdf").toString();
        Cli.run("render", invoice, "--embed", "cii", "--out", first);
        Cli.run("render", invoice, "--embed", "cii", "--out", second);

        assertArrayEquals(read(first), read(second));
    }

    /** The reading commands show it with its role, and hand it out when asked by name. */
    @Test
    void theReadingCommandsShowItAndHandItOut() {
        String hybrid = hybrid();

        Cli.Run listed = Cli.run("extract", "--list", hybrid);
        Cli.Run inspected = Cli.run("inspect", hybrid);
        Cli.Run extracted = Cli.run("extract", "--attachment", NAME, hybrid);

        assertTrue(listed.text().contains("\"invoice.esj.json\" (ESJ document, not the"
                + " invoice,"), listed.text());
        assertTrue(listed.text().contains("AFRelationship \"Supplement\""), listed.text());
        assertTrue(inspected.text().contains("ESJ document, not the invoice"),
                inspected.text());
        assertTrue(inspected.text().contains("Embedded invoice:           \"factur-x.xml\""),
                "the invoice of the file is still the XML: " + inspected.text());
        assertEquals(ExitCode.SUCCESS, extracted.exitCode(), extracted.err());
        assertTrue(extracted.text().startsWith("{"),
                "it hands out the canonical bytes of the document itself");
        assertTrue(extracted.text().contains("\"format\":\"EN16931-Semantic-JSON\""),
                extracted.text());
    }

    /** An attachment somebody changed one amount in is a container that is wrong. */
    @Test
    void aTamperedEsjDocumentMakesTheContainerInvalid() {
        String hybrid = Fixtures.write(directory, "tampered.pdf",
                container(invoiceXml(), tampered()));

        Cli.Run validated = Cli.run("validate", hybrid);

        assertEquals(ExitCode.VALIDATION, validated.exitCode(), validated.text());
        assertTrue(validated.text().contains(CONTAINER_INVALID), validated.text());
        assertTrue(validated.text().contains("PDF-ESJ-DISAGREES"), validated.text());
        assertTrue(validated.text().contains("/BG-22/BT-112"), validated.text());
        assertTrue(validated.text().contains("Invoice:          VALID"),
                "and the invoice of the file is unmoved by it: " + validated.text());
    }

    /** An ESJ document of another invoice is a container that is wrong about itself. */
    @Test
    void anEsjDocumentOfAnotherInvoiceMakesTheContainerInvalid() {
        String hybrid = Fixtures.write(directory, "foreign.pdf",
                container(invoiceXml(), Fixtures.bytes(OTHER)));

        Cli.Run validated = Cli.run("validate", hybrid);

        assertEquals(ExitCode.VALIDATION, validated.exitCode(), validated.text());
        assertTrue(validated.text().contains("PDF-ESJ-DISAGREES"), validated.text());
    }

    /** Bytes that are no ESJ document at all are a container that is wrong about itself. */
    @Test
    void anAttachmentThatIsNoEsjDocumentMakesTheContainerInvalid() {
        String hybrid = Fixtures.write(directory, "garbage.pdf",
                container(invoiceXml(),
                        "{ \"format\": 7 }".getBytes(StandardCharsets.UTF_8)));

        Cli.Run validated = Cli.run("validate", hybrid);

        assertEquals(ExitCode.VALIDATION, validated.exitCode(), validated.text());
        assertTrue(validated.text().contains("PDF-ESJ-UNREADABLE"), validated.text());
    }

    /**
     * A term of no published model is what the attachment is for: the cross industry
     * invoice has no place for it, so carrying it is no disagreement.
     */
    @Test
    void anExtensionTermTheSyntaxDoesNotBindIsNoDisagreement() {
        String invoice = Fixtures.write(directory, "b2c.esj.json",
                CONSUMER_INVOICE.getBytes(StandardCharsets.UTF_8));
        String hybrid = directory.resolve("b2c.pdf").toString();
        Cli.Run rendered = Cli.run("render", invoice, "--embed", "cii", "--out", hybrid);
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());
        assertTrue(rendered.err().contains(ATTACHED), rendered.err());

        Cli.Run validated = Cli.run("validate", hybrid);

        assertTrue(validated.text().contains(ROW), validated.text());
        assertTrue(validated.text().contains(CONTAINER_OK), validated.text());
        Cli.Run extracted = Cli.run("extract", "--attachment", NAME, hybrid);
        assertTrue(extracted.text().contains("/BT-B2C-010"),
                "and the term the XML had no place for is in it: " + extracted.text());
    }

    /**
     * An attachment that outgrows a bound of the run is a reader that stopped, never a
     * verdict: exit code 7 and no verdict at all.
     */
    @Test
    void anOversizedEsjDocumentIsALimitAndNoVerdict() {
        byte[] padded = padded(Fixtures.bytes(EXAMPLE), 200_000);
        String hybrid = Fixtures.write(directory, "large.pdf",
                container(invoiceXml(), padded));

        Cli.Run validated = Cli.run("validate", hybrid, "--max-input-bytes", "100000");

        assertEquals(ExitCode.LIMIT, validated.exitCode(), validated.text() + validated.err());
        assertTrue(validated.err().contains("invoice.esj.json"), validated.err());
        assertFalse(validated.text().contains("VALID"), validated.text());
    }

    /**
     * The attachment is read under the reader bounds of this run and not under the hard
     * defaults, and a bound met inside it is a reader that stopped: exit code 7, no
     * verdict, and never the verdict {@code INVALID} on a container nobody looked into.
     */
    @Test
    void theAttachmentIsReadUnderTheReaderBoundsOfThisRun() {
        String hybrid = Fixtures.write(directory, "loose.pdf",
                container(invoiceXml(), loose(Fixtures.bytes(EXAMPLE), 200_000)));

        Cli.Run wide = Cli.run("validate", hybrid);
        Cli.Run narrow = Cli.run("validate", hybrid, "--max-document-bytes", "100000");

        assertEquals(ExitCode.SUCCESS, wide.exitCode(), wide.text() + wide.err());
        assertEquals(ExitCode.LIMIT, narrow.exitCode(), narrow.text() + narrow.err());
        assertTrue(narrow.err().contains(NAME), narrow.err());
        assertTrue(narrow.err().contains("--max-document-bytes"), narrow.err());
        assertFalse(narrow.text().contains("INVALID"),
                "a bound is never invalidity: " + narrow.text());
    }

    /**
     * An ESJ document of another edition is not another account of this invoice. Every
     * path and every value may be identical and the two still mean different things,
     * because the edition names the registry a consumer reads the document under.
     */
    @Test
    void anEsjDocumentOfAnotherEditionMakesTheContainerInvalid() {
        String hybrid = Fixtures.write(directory, "edition.pdf",
                container(invoiceXml(), otherEdition()));

        Cli.Run validated = Cli.run("validate", hybrid);

        assertEquals(ExitCode.VALIDATION, validated.exitCode(), validated.text());
        assertTrue(validated.text().contains("PDF-ESJ-DISAGREES"), validated.text());
        assertTrue(validated.text().contains("EN16931-1:2026"), validated.text());
        assertTrue(validated.text().contains("Invoice:          VALID"),
                "and the invoice of the file is unmoved by it: " + validated.text());
    }

    /**
     * The edition is a fact about the document and not about its header. An attachment
     * that names this edition and states a term of another one would pass the value
     * comparison — the syntax binds no such term, so the room the rule leaves for
     * extension terms would take it in — and this tool would certify a supplement it
     * rejects when the same bytes are handed to it as a file.
     */
    @Test
    void anEsjDocumentThatIsNoDocumentOfTheModelItNamesMakesTheContainerInvalid() {
        String forged = Fixtures.write(directory, "forged.esj.json", withTerm("/BT-170"));
        String hybrid = Fixtures.write(directory, "forged.pdf",
                container(invoiceXml(), withTerm("/BT-170")));
        String invented = Fixtures.write(directory, "invented.pdf",
                container(invoiceXml(), withTerm("/BT-999")));

        Cli.Run alone = Cli.run("validate", forged);
        Cli.Run validated = Cli.run("validate", hybrid);
        Cli.Run made = Cli.run("validate", invented);

        assertEquals(ExitCode.VALIDATION, alone.exitCode(), alone.text());
        assertEquals(ExitCode.VALIDATION, validated.exitCode(), validated.text());
        assertTrue(validated.text().contains("PDF-ESJ-UNSOUND"), validated.text());
        assertTrue(validated.text().contains("/BT-170"), validated.text());
        assertTrue(validated.text().contains(CONTAINER_INVALID), validated.text());
        assertTrue(validated.text().contains("Invoice:          VALID"),
                "and the invoice of the file is unmoved by it: " + validated.text());
        assertEquals(ExitCode.VALIDATION, made.exitCode(), made.text());
        assertTrue(made.text().contains("PDF-ESJ-UNSOUND"), made.text());
    }

    /**
     * A bound met inside the attachment is a bound wherever the reader met it. The reader
     * collects what it finds and reads on, so a document that is malformed somewhere and
     * too large as well must not come out as a defect of the file through one door and as
     * a bound through the other.
     */
    @Test
    void aBoundIsALimitEvenBehindAnotherFinding() {
        byte[] document = loose(malformed(), 200_000);
        String file = Fixtures.write(directory, "mixed.esj.json", document);
        String hybrid = Fixtures.write(directory, "mixed.pdf",
                container(invoiceXml(), document));

        Cli.Run alone = Cli.run("validate", file, "--max-document-bytes", "100000");
        Cli.Run inside = Cli.run("validate", hybrid, "--max-document-bytes", "100000");

        assertEquals(ExitCode.LIMIT, alone.exitCode(), alone.text() + alone.err());
        assertEquals(ExitCode.LIMIT, inside.exitCode(), inside.text() + inside.err());
        assertTrue(inside.err().contains(NAME), inside.err());
        assertFalse(inside.text().contains("INVALID"),
                "a bound is never invalidity: " + inside.text());
    }

    /**
     * The command whose job is to say what an unknown file carries is not the one an
     * appended file can switch off. It reports the bound on the row and describes the
     * container; only the command that pronounces a verdict over the container has to
     * refuse, because a verdict may not be given over a supplement nobody read.
     */
    @Test
    void aBoundInsideTheSupplementIsARowForInspectAndARefusalForValidate() {
        String hybrid = Fixtures.write(directory, "supplement-bound.pdf",
                container(invoiceXml(), padded(Fixtures.bytes(EXAMPLE), 200_000)));

        Cli.Run inspected = Cli.run("inspect", hybrid, "--max-input-bytes", "100000");
        Cli.Run validated = Cli.run("validate", hybrid, "--max-input-bytes", "100000");

        assertEquals(ExitCode.INDETERMINATE, inspected.exitCode(),
                inspected.text() + inspected.err());
        assertTrue(inspected.text().contains(ROW), inspected.text());
        assertTrue(inspected.text().contains("PDF-ESJ-UNCHECKED"), inspected.text());
        assertTrue(inspected.text().contains("a bound of this run was reached"),
                inspected.text());
        assertTrue(inspected.text().contains(CONTAINER_OK),
                "and no bound of this run became a verdict: " + inspected.text());
        assertEquals(ExitCode.LIMIT, validated.exitCode(), validated.err());
    }

    /**
     * The row that says {@code Embedded file params OK} is a statement about every
     * attachment of the file, so the checks that are about decoded content have to have
     * been made over every attachment it was read for. {@code esj inspect} decoded the
     * supplement after those checks had run, so a supplement that declared a size it does
     * not have was passed over in silence under an affirmative row, while the same lie on
     * the invoice attachment was reported.
     */
    @Test
    void inspectChecksTheDeclaredSizeOfTheSupplementItReports() {
        byte[] esj = Fixtures.bytes(EXAMPLE);
        String hybrid = Fixtures.write(directory, "supplement-size.pdf",
                TestPdfs.builder().xmp(TestPdfs.xmp(TestPdfs.FACTUR_X, "EN 16931"))
                        .attach(TestPdfs.FACTUR_X, invoiceXml())
                        .attach(esjAttachment(esj).declaring(100))
                        .build());

        Cli.Run inspected = Cli.run("inspect", hybrid);
        Cli.Run validated = Cli.run("validate", hybrid);

        assertTrue(inspected.text().contains("PDF-EMBEDDED-SIZE"),
                "inspect reports what it read: " + inspected.text());
        assertTrue(inspected.text().contains("declares a size of 100 bytes and decodes to "
                + esj.length), inspected.text());
        assertFalse(inspected.text().contains("Embedded file params  OK"),
                "and the row no longer says OK about a check it did not make: "
                        + inspected.text());
        assertTrue(validated.text().contains("PDF-EMBEDDED-SIZE"), validated.text());
    }

    /**
     * The property the ordering of those checks was there for holds all the same: a bound
     * of this run met inside an attachment somebody appended is not a defect of the file,
     * so it stays off the row that judges the file and stands on the row the supplement
     * has of its own.
     */
    @Test
    void aBoundInsideTheSupplementStaysOffTheRowThatJudgesTheFile() {
        String hybrid = Fixtures.write(directory, "supplement-bound-row.pdf",
                container(invoiceXml(), padded(Fixtures.bytes(EXAMPLE), 200_000)));

        Cli.Run inspected = Cli.run("inspect", hybrid, "--max-input-bytes", "100000");

        assertFalse(inspected.text().contains("PDF-EMBEDDED-TRUNCATED"),
                "a bound of this run is not a finding about the file: " + inspected.text());
        assertTrue(inspected.text().contains("PDF-ESJ-UNCHECKED"),
                "it stands on the row of the ESJ document: " + inspected.text());
        assertTrue(inspected.text().contains(CONTAINER_OK), inspected.text());
    }

    /**
     * A supplement nobody asked for does not end a run that never reads it. Anyone who
     * can append one file to somebody else's hybrid invoice would otherwise deny its
     * recipient the invoice and its rendering.
     */
    @Test
    void anOversizedSupplementDoesNotStopACommandThatNeverReadsIt() {
        String hybrid = Fixtures.write(directory, "supplement.pdf",
                container(invoiceXml(), padded(Fixtures.bytes(EXAMPLE), 200_000)));
        String pages = directory.resolve("pages-of-hybrid.pdf").toString();

        Cli.Run extracted = Cli.run("extract", "--attachment", "factur-x.xml", hybrid,
                "--max-input-bytes", "100000");
        Cli.Run rendered = Cli.run("render", hybrid, "--out", pages,
                "--max-input-bytes", "100000");

        assertEquals(ExitCode.SUCCESS, extracted.exitCode(), extracted.err());
        assertTrue(extracted.text().contains("CrossIndustryInvoice"),
                "the attachment the caller named: " + extracted.text());
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());
    }

    /** A run that was told to leave it out did what it was told, which is no warning. */
    @Test
    void turningTheAttachmentOffIsNoWarning() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        Cli.run("render", invoice, "--out", pages);

        Cli.Run embedded = Cli.run("embed", pages, invoice, "--no-esj",
                "--out", directory.resolve("quiet.pdf").toString());

        assertEquals(ExitCode.SUCCESS, embedded.exitCode(), embedded.err());
        assertTrue(embedded.err().contains("turned it off"), embedded.err());
        assertFalse(embedded.err().contains("warning:"), embedded.err());
    }

    /**
     * Where the invoice this run read is in no syntax this version has a binding table
     * for, there is nothing to compare the ESJ document against, and the row says so
     * instead of claiming an agreement nobody established. An attachment whose root
     * element lies beyond the classification window is such an invoice: it is read
     * because the caller named it, and it was never classified.
     */
    @Test
    void anInvoiceThisReaderNeverClassifiedLeavesTheEsjDocumentUnchecked() {
        String hybrid = Fixtures.write(directory, "hidden.pdf",
                container(beyondTheWindow(invoiceXml()), Fixtures.bytes(EXAMPLE)));

        Cli.Run validated = Cli.run("validate", hybrid, "--attachment", "factur-x.xml");

        assertTrue(validated.text().contains("PDF-ESJ-UNCHECKED"),
                validated.text() + validated.err());
        assertTrue(validated.text().contains(ROW), validated.text());
    }

    /** A PDF carrying the ESJ document and no invoice XML carries no invoice. */
    @Test
    void aFileCarryingOnlyTheEsjDocumentCarriesNoInvoice() {
        String hybrid = Fixtures.write(directory, "only-esj.pdf",
                container(null, Fixtures.bytes(EXAMPLE)));

        Cli.Run validated = Cli.run("validate", hybrid);

        assertEquals(ExitCode.INPUT, validated.exitCode(), validated.err());
        assertTrue(validated.err().contains("no structured invoice representation"),
                validated.err());
        assertTrue(validated.err().contains("reads no invoice out of an ESJ attachment"),
                validated.err());
    }

    /**
     * Two ESJ documents are a container whose producer left the choice to the reader.
     * Neither is checked against the invoice, and the file is wrong about itself.
     */
    @Test
    void severalEsjDocumentsAreRefusedRatherThanChosenBetween() {
        byte[] invoice = invoiceXml();
        String hybrid = Fixtures.write(directory, "two.pdf", TestPdfs.builder()
                .xmp(TestPdfs.xmp(TestPdfs.FACTUR_X, "EN 16931"))
                .attach(TestPdfs.FACTUR_X, invoice)
                .attach(esjAttachment(Fixtures.bytes(EXAMPLE)))
                .attach(esjAttachment(tampered()))
                .build());

        Cli.Run validated = Cli.run("validate", hybrid);

        assertEquals(ExitCode.VALIDATION, validated.exitCode(), validated.text());
        assertTrue(validated.text().contains("PDF-EMBEDDED-SEVERAL-ESJ"), validated.text());
        assertFalse(validated.text().contains(ROW),
                "and none of them is shown as checked: " + validated.text());
        assertTrue(validated.text().contains(CONTAINER_INVALID), validated.text());
        Cli.Run json = Cli.run("validate", "--output", "json", hybrid);
        assertTrue(json.text().contains("\"status\": \"several\""),
                "the JSON form tells several from none: " + json.text());
        assertTrue(json.text().contains("\"count\": 2"), json.text());
        assertTrue(json.text().contains("\"agrees\": null"),
                "nothing was checked, so nothing is claimed: " + json.text());
    }

    /** The JSON form carries the same answer as the row. */
    @Test
    void theJsonReportCarriesTheAnswer() {
        Cli.Run sound = Cli.run("validate", "--output", "json", hybrid());
        Cli.Run tampered = Cli.run("validate", "--output", "json",
                Fixtures.write(directory, "tampered.pdf",
                        container(invoiceXml(), tampered())));

        assertTrue(sound.text().contains("\"esj\": {"), sound.text());
        assertTrue(sound.text().contains("\"status\": \"one\""), sound.text());
        assertTrue(sound.text().contains("\"attachment\": \"invoice.esj.json\""),
                sound.text());
        assertTrue(sound.text().contains("\"agrees\": true"), sound.text());
        assertTrue(tampered.text().contains("\"agrees\": false"), tampered.text());
        assertTrue(Cli.run("validate", "--output", "json",
                        Fixtures.file(directory, "conformance/pdf/factur-x.pdf"))
                        .text().contains("\"esj\": null"),
                "and a file that carries none says so");
    }

    /** The report file carries the row, in either language it is written in. */
    @Test
    void theReportFileCarriesTheRowInBothLanguages() {
        String hybrid = hybrid();
        String english = directory.resolve("proof-en.html").toString();
        String german = directory.resolve("proof-de.html").toString();

        assertEquals(ExitCode.SUCCESS, Cli.run("validate", hybrid, "--report", english,
                "--report-lang", "en").exitCode());
        assertEquals(ExitCode.SUCCESS, Cli.run("validate", hybrid, "--report", german,
                "--report-lang", "de").exitCode());

        String reportEn = new String(read(english), StandardCharsets.UTF_8);
        String reportDe = new String(read(german), StandardCharsets.UTF_8);
        assertTrue(reportEn.contains("ESJ document attached"), "English report");
        assertTrue(reportEn.contains("invoice.esj.json"), "with the name it carries");
        assertTrue(reportDe.contains("ESJ-Dokument beigelegt"), "German report");
    }

    /** Renders and embeds the example, and returns the path of the hybrid file. */
    private String hybrid() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String hybrid = directory.resolve("hybrid.pdf").toString();
        Cli.Run rendered = Cli.run("render", invoice, "--embed", "cii", "--out", hybrid);
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());
        return hybrid;
    }

    /** Returns the cross industry invoice of the example, as the tool writes it. */
    private byte[] invoiceXml() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String xml = directory.resolve("invoice.xml").toString();
        Cli.run("convert", invoice, "--to", "cii", "--out", xml);
        return read(xml);
    }

    /** Returns the example with one total changed, which the invoice still states. */
    private static byte[] tampered() {
        String document = new String(Fixtures.bytes(EXAMPLE), StandardCharsets.UTF_8);
        String changed = document.replace("\"/BG-22/BT-112\": \"2915.5\"",
                "\"/BG-22/BT-112\": \"2915.51\"");
        assertFalse(changed.equals(document), "the example states BT-112 as 2915.5");
        return changed.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns an XML document whose root element lies beyond the window an attachment is
     * classified by, so that the container never established what it is.
     */
    private static byte[] beyondTheWindow(byte[] xml) {
        String text = new String(xml, StandardCharsets.UTF_8);
        int prolog = text.indexOf("?>") + 2;
        return (text.substring(0, prolog) + "<!--" + " ".repeat(9000) + "-->"
                + text.substring(prolog)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns the example with one more value at a path of no term of the edition it
     * names, which is the shape a forged supplement takes once the edition in the header
     * is left alone.
     */
    private static byte[] withTerm(String path) {
        String document = new String(Fixtures.bytes(EXAMPLE), StandardCharsets.UTF_8);
        String changed = document.replace("\"values\": {",
                "\"values\": {\n    \"" + path + "\": \"a term of another model\",");
        assertFalse(changed.equals(document), "the example carries a values object");
        return changed.getBytes(StandardCharsets.UTF_8);
    }

    /** Returns the example with one path that is no semantic path. */
    private static byte[] malformed() {
        String document = new String(Fixtures.bytes(EXAMPLE), StandardCharsets.UTF_8);
        String changed = document.replace("\"values\": {",
                "\"values\": {\n    \"/BG-1/x/BT-22\": \"no index\",");
        assertFalse(changed.equals(document), "the example carries a values object");
        return changed.getBytes(StandardCharsets.UTF_8);
    }

    /** Returns the example under the name of the 2026 edition, values unchanged. */
    private static byte[] otherEdition() {
        String document = new String(Fixtures.bytes(EXAMPLE), StandardCharsets.UTF_8);
        String changed = document.replace("\"EN16931-1:2017+A1:2019/AC:2020\"",
                "\"EN16931-1:2026\"");
        assertFalse(changed.equals(document), "the example names the 2017 edition");
        return changed.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns a document padded to at least this size with whitespace the reader has to
     * take in and that changes nothing the document says.
     */
    private static byte[] loose(byte[] document, int size) {
        String text = new String(document, StandardCharsets.UTF_8);
        int after = text.indexOf('{') + 1;
        StringBuilder padding = new StringBuilder();
        while (text.length() + padding.length() < size) {
            padding.append(' ');
        }
        return (text.substring(0, after) + padding + text.substring(after))
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Returns a document padded with insignificant whitespace to at least this size. */
    private static byte[] padded(byte[] document, int size) {
        StringBuilder text = new StringBuilder(new String(document, StandardCharsets.UTF_8));
        while (text.length() < size) {
            text.append(' ');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns a hybrid container with the ESJ document beside the invoice, in the shape
     * this project writes: the name, the media type and the relationship of an enclosure.
     *
     * @param invoice the cross industry invoice, or {@code null} for a file that carries
     *                none
     * @param esj     the bytes of the attachment
     */
    private static byte[] container(byte[] invoice, byte[] esj) {
        TestPdfs.Builder builder = TestPdfs.builder()
                .xmp(TestPdfs.xmp(TestPdfs.FACTUR_X, "EN 16931"));
        if (invoice != null) {
            builder.attach(TestPdfs.FACTUR_X, invoice);
        }
        return builder.attach(esjAttachment(esj)).build();
    }

    /** Returns an attachment of the shape this project writes the ESJ document as. */
    private static TestPdfs.Attachment esjAttachment(byte[] esj) {
        return new TestPdfs.Attachment(NAME, esj, JSON, SUPPLEMENT, true, (COSName) null);
    }

    /** An invoice carrying a term of no published model, as ESJ. */
    private static final String CONSUMER_INVOICE = """
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
                "/BG-4/BT-27": "Example GmbH",
                "/BG-4/BG-5/BT-40": "DE",
                "/BG-7/BT-44": "Muster AG",
                "/BG-7/BG-8/BT-55": "DE",
                "/BG-22/BT-106": "100",
                "/BG-22/BT-109": "100",
                "/BG-22/BT-112": "119",
                "/BG-22/BT-115": "119",
                "/BG-23/0/BT-116": "100",
                "/BG-23/0/BT-117": "19",
                "/BG-23/0/BT-118": "S",
                "/BG-23/0/BT-119": "19",
                "/BG-25/0/BT-126": "1",
                "/BG-25/0/BT-129": "1",
                "/BG-25/0/BT-130": "C62",
                "/BG-25/0/BT-131": "100",
                "/BG-25/0/BG-29/BT-146": "100",
                "/BG-25/0/BG-30/BT-151": "S",
                "/BG-25/0/BG-30/BT-152": "19",
                "/BG-25/0/BG-31/BT-153": "Shower fitting",
                "/BT-B2C-010": "119.00"
              }
            }
            """;

    /** Returns the bytes of a file the tool wrote. */
    private static byte[] read(String file) {
        try {
            return Files.readAllBytes(Path.of(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
