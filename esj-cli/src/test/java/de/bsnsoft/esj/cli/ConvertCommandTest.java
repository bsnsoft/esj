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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code esj convert} over the three syntaxes it reads and the two it writes.
 *
 * <p>The assertions against the conformance corpus are the load-bearing ones: the ESJ
 * files under {@code conformance/esj/} are golden files for the whole path from XML to
 * canonical bytes, and a conversion run through the command line has to arrive at exactly
 * those bytes. If it does not, either the command line added something of its own to the
 * pipeline or the pipeline changed, and both are things to find out about here.
 */
class ConvertCommandTest {

    private static final String MINIMAL = "examples/minimal.esj.json";
    private static final String UBL = "conformance/kosit/business-cases/standard/"
            + "01.01a-INVOICE_ubl.xml";
    private static final String CII = "conformance/kosit/business-cases/standard/"
            + "01.01a-INVOICE_uncefact.xml";
    private static final String EXTENSION = "conformance/kosit/business-cases/extension/"
            + "04.01a-INVOICE_ubl.xml";

    /** The line of the standard invoice example a seller tax registration is added after. */
    private static final String SELLER_VAT_IDENTIFIER = "\"/BG-4/BT-31\": \"DE123456789\",";

    @TempDir
    private Path directory;

    @Test
    void writesThePrettyFormOfAnEsjDocumentByDefault() {
        // An example of the repository is stored in pretty form, so converting it to ESJ
        // has to reproduce the file it was read from, byte for byte.
        Cli.Run run = Cli.run("convert", Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertArrayEquals(Fixtures.bytes(MINIMAL), run.out());
        assertEquals("", run.err());
    }

    /**
     * A conversion that could not carry the fraction digits of an amount says so without
     * being asked.
     *
     * <p>An amount written with a third fraction digit that is a zero is the same number
     * one notation shorter, and the semantic document carries the number. What it no longer
     * carries is the notation, which is what the decimal rules of EN 16931 are about: after
     * this conversion no engine reading the result can see that the source spelled three
     * digits, and the artefacts fault exactly that. So the note is a warning and is on the
     * error stream of the ordinary run, not behind {@code --verbose}.
     *
     * <p>The reader is named because only the stylesheet path reports the reduction today;
     * {@code conformance/readers.md} records that as a difference between the two readers.
     */
    @Test
    void saysOnTheErrorStreamThatItCouldNotCarryTheFractionDigitsOfAnAmount() {
        String source = new String(Fixtures.bytes(CII), StandardCharsets.UTF_8)
                .replace("<ram:LineTotalAmount>314.86</ram:LineTotalAmount>",
                        "<ram:LineTotalAmount>314.860</ram:LineTotalAmount>");
        String file = Fixtures.write(directory, "over-scale.xml",
                source.getBytes(StandardCharsets.UTF_8));

        Cli.Run run = Cli.run("convert", "--importer", "xslt", file);

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("SCALE_REDUCED at /BG-22/BT-106"), run.err());
        assertTrue(run.err().contains("did not reach the document"), run.err());
    }

    @Test
    void writesTheCanonicalBytesWithNoTrailingNewline() {
        Cli.Run run = Cli.run("convert", "--canonical", Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertArrayEquals(Fixtures.bytes("examples/minimal.canonical.esj.json"), run.out());
        assertFalse(run.text().endsWith("\n"), "the canonical form ends without a newline");
    }

    @Test
    void readsTheStandardInputForAFileNameOfOneDash() {
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "convert", "-", "--canonical");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertArrayEquals(Fixtures.bytes("examples/minimal.canonical.esj.json"), run.out());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {UBL, CII})
    void reproducesTheGoldenFileOfTheConformanceCorpus(String instance) {
        Cli.Run run = Cli.run("convert", Fixtures.file(directory, instance));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String stem = instance.substring("conformance/kosit/".length());
        assertArrayEquals(Fixtures.bytes("conformance/esj/" + stem + ".esj.json"), run.out());
        assertEquals("", run.err(),
                "the default reader reads a component only where the standard has one, so"
                        + " it has nothing to report about these two instances");
    }

    /**
     * The same two instances through the second reader. It arrives at the same document —
     * {@code conformance/readers.md} records where the two do not — and it reports the one
     * kind of note the whole corpus produces on that path: a supplementary component the
     * standard does not give the term, dropped while the value itself is kept.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {UBL, CII})
    void arrivesAtTheSameDocumentThroughTheXsltReader(String instance) {
        Cli.Run run = Cli.run("--importer", "xslt", "convert",
                Fixtures.file(directory, instance));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String stem = instance.substring("conformance/kosit/".length());
        assertArrayEquals(Fixtures.bytes("conformance/esj/" + stem + ".esj.json"), run.out());
        for (String note : run.err().split("\n")) {
            assertTrue(note.isEmpty() || note.contains("COMPONENT_DROPPED"), note);
        }
    }

    @Test
    void refusesAReaderItDoesNotKnow() {
        Cli.Run run = Cli.run("--importer", "saxon", "convert",
                Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--importer takes streaming or xslt"), run.err());
    }

    @Test
    void importsExtensionTermsOnlyWhenTheExtensionRegistryIsAskedFor() {
        String file = Fixtures.file(directory, EXTENSION);
        String golden = "conformance/esj/business-cases/extension/"
                + "04.01a-INVOICE_ubl.xml.esj.json";

        Cli.Run withExtension = Cli.run("convert", file, "--extension", "xrechnung");
        assertEquals(ExitCode.SUCCESS, withExtension.exitCode(), withExtension.err());
        assertArrayEquals(Fixtures.bytes(golden), withExtension.out());
        assertEquals("", withExtension.err());

        Cli.Run core = Cli.run("convert", file);
        assertEquals(ExitCode.SUCCESS, core.exitCode(), core.err());
        assertFalse(core.text().contains("BG-DEX-01"),
                "without the extension registry the extension terms are not written");
        assertTrue(core.err().contains("warning: "),
                "and what was left out is said on the error stream");
        assertTrue(core.err().contains("UNKNOWN_TERM"), core.err());
    }

    @Test
    void saysHowLargeTheSkippedSubtreeWasAndWhereItsTermsAreDefined() {
        Cli.Run run = Cli.run("convert", Fixtures.file(directory, EXTENSION));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("term identifiers it encloses"),
                "one note stands for a subtree, and says how large it was: " + run.err());
        assertTrue(run.err().contains("the registry of the XRechnung extension defines it"),
                "\"no loaded registry knows it\" must not read as \"this tool cannot do"
                        + " that\": " + run.err());
        assertTrue(run.err().contains("--extension xrechnung loads it"),
                "the remedy is named once, by the side that owns the option: " + run.err());
    }

    @Test
    void doesNotOfferTheExtensionRegistryToARunThatAlreadyLoadedIt() {
        Cli.Run run = Cli.run("convert", Fixtures.file(directory, EXTENSION),
                "--extension", "xrechnung");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(run.err().contains("--extension xrechnung loads it"), run.err());
    }

    /**
     * The invoice type code decides which of the two UBL documents the tool writes, and the
     * run says which one it wrote rather than leaving the caller to read the root element.
     */
    @Test
    void writesTheUblDocumentTheInvoiceTypeCodeNames() {
        Path written = directory.resolve("converted.ubl.xml");
        Cli.Run invoice = Cli.run("convert", "--to", "ubl", "--out", written.toString(),
                "--verbose", Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.SUCCESS, invoice.exitCode(), invoice.err());
        assertTrue(read(written).contains("<Invoice "), read(written));
        assertTrue(invoice.err().contains("to a UBL invoice"), invoice.err());

        Cli.Run creditNote = Cli.run("convert", "--to", "ubl", "--out", written.toString(),
                "--verbose", Fixtures.file(directory, "examples/credit-note.esj.json"));
        assertEquals(ExitCode.SUCCESS, creditNote.exitCode(), creditNote.err());
        assertTrue(read(written).contains("<CreditNote "), read(written));
        assertTrue(creditNote.err().contains("to a UBL credit note"), creditNote.err());
    }

    /**
     * A UBL document the tool writes from either syntax of the corpus is one the official
     * artefacts accept, and reading it back gives the values it was written from.
     * {@code conformance/writers/ubl-roundtrip.md} measures the whole corpus; this is the
     * same path through the command line.
     */
    @Test
    void everyCorpusInstanceBecomesAUblDocumentTheArtefactsAccept() {
        Path written = directory.resolve("converted.ubl.xml");
        Path back = directory.resolve("converted.ubl.esj.json");
        for (String instance : List.of("business-cases/standard/01.01a-INVOICE_ubl.xml",
                "business-cases/standard/01.01a-INVOICE_uncefact.xml")) {
            Cli.Run converted = Cli.run("convert", "--to", "ubl", "--extension", "xrechnung",
                    "--out", written.toString(),
                    Fixtures.file(directory, "conformance/kosit/" + instance));
            assertEquals(ExitCode.SUCCESS, converted.exitCode(), instance + converted.err());
            assertEquals(ExitCode.SUCCESS,
                    Cli.run("validate", "--rules", "none", written.toString()).exitCode(),
                    instance + " converts to a UBL document the artefacts accept");
            Cli.run("convert", "--extension", "xrechnung", "--out", back.toString(),
                    written.toString());
            Cli.Run difference = Cli.run("diff",
                    Fixtures.file(directory, "conformance/esj/" + instance + ".esj.json"),
                    back.toString());
            assertEquals(ExitCode.SUCCESS, difference.exitCode(),
                    instance + ": and back unchanged: " + difference.text());
        }
    }

    /**
     * {@code --ubl-document} names one of the two documents instead of reading BT-3.
     *
     * <p>A receiver may expect one of them whatever the code says, and a run that writes
     * what it was told to write is not thereby writing a conformant document — the type
     * code goes over unchanged, so the artefacts of the target syntax have something to
     * say about the pair. That is the caller's business; what this tool owes is that the
     * option does what it says and that the run reports which document it wrote.
     */
    @Test
    void writesTheUblDocumentTheOptionNames() {
        Path written = directory.resolve("named.ubl.xml");
        Cli.Run invoice = Cli.run("convert", "--to", "ubl", "--ubl-document", "invoice",
                "--out", written.toString(), "--verbose",
                Fixtures.file(directory, "examples/credit-note.esj.json"));
        assertEquals(ExitCode.SUCCESS, invoice.exitCode(), invoice.err());
        assertTrue(read(written).contains("<Invoice "), read(written));
        assertTrue(invoice.err().contains("to a UBL invoice"), invoice.err());

        Cli.Run creditNote = Cli.run("convert", "--to", "ubl", "--ubl-document", "creditnote",
                "--out", written.toString(), "--verbose",
                Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.SUCCESS, creditNote.exitCode(), creditNote.err());
        assertTrue(read(written).contains("<CreditNote "), read(written));

        Cli.Run automatic = Cli.run("convert", "--to", "ubl", "--ubl-document", "auto",
                "--out", written.toString(), "--verbose",
                Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.SUCCESS, automatic.exitCode(), automatic.err());
        assertTrue(read(written).contains("<Invoice "), read(written));
    }

    @Test
    void refusesToChooseAUblDocumentForASyntaxThatHasNone() {
        Cli.Run run = Cli.run("convert", "--to", "cii", "--ubl-document", "invoice",
                Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--ubl-document chooses between the two UBL documents"),
                run.err());
    }

    @Test
    void refusesAUblDocumentItDoesNotKnow() {
        Cli.Run run = Cli.run("convert", "--to", "ubl", "--ubl-document", "note",
                Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--ubl-document takes invoice, creditnote or auto"),
                run.err());
    }

    @Test
    void refusesATargetSyntaxItDoesNotKnow() {
        Cli.Run run = Cli.run("convert", Fixtures.file(directory, MINIMAL), "--to", "json");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--to takes esj, cii or ubl"), run.err());
    }

    @Test
    void writesACrossIndustryInvoiceTheSyntaxEngineAccepts() {
        Cli.Run written = Cli.run("convert", "--to", "cii", Fixtures.file(directory, UBL));
        assertEquals(ExitCode.SUCCESS, written.exitCode(), written.err());
        assertEquals("", written.err(), "this invoice loses nothing on the way over");
        assertTrue(written.text().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                written.text().substring(0, Math.min(80, written.text().length())));
        assertTrue(written.text().contains("rsm:CrossIndustryInvoice"), "a cross industry"
                + " invoice is what came out");

        Cli.Run checked = Cli.run(written.out(), "validate", "-");
        assertEquals(ExitCode.SUCCESS, checked.exitCode(), checked.text());
        assertTrue(checked.text().contains("VALID"), checked.text());
    }

    @Test
    void writesTheCrossIndustryInvoiceToTheFileNamedByOut() {
        Path target = directory.resolve("invoice.cii.xml");
        Cli.Run run = Cli.run("convert", "--to", "cii", "--out", target.toString(),
                Fixtures.file(directory, CII));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals(0, run.out().length, "the document went to the file, not to the output");
        assertTrue(Files.exists(target), "the file was written");
        assertTrue(read(target).contains("rsm:CrossIndustryInvoice"), "and it is the invoice");
    }

    @Test
    void namesEveryValueTheTargetSyntaxHasNoPlaceFor() {
        Cli.Run run = Cli.run("convert", "--to", "cii", "--extension", "xrechnung",
                Fixtures.file(directory, EXTENSION));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("no place in this syntax"), run.err());
        assertTrue(run.err().contains("TERM_NOT_BOUND"), run.err());
        assertTrue(run.err().contains(" times)"),
                "notes that say the same thing are collapsed to one line with a count: "
                        + run.err());
        assertTrue(Cli.run("--verbose", "convert", "--to", "cii", "--extension", "xrechnung",
                        Fixtures.file(directory, EXTENSION)).err().contains("/BG-25/0/BG-DEX-01"),
                "and --verbose names every path");
    }

    @Test
    void carriesTheSameListAsDataUnderOutputJson() {
        Path target = directory.resolve("invoice.cii.xml");
        Cli.Run run = Cli.run("convert", "--to", "cii", "--extension", "xrechnung",
                "--out", target.toString(), "--output", "json",
                Fixtures.file(directory, EXTENSION));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"to\": \"cii\""), run.text());
        assertTrue(run.text().contains("\"importer\": \"streaming\""), run.text());
        assertTrue(run.text().contains("\"complete\": false"), run.text());
        assertTrue(run.text().contains("\"kind\": \"TERM_NOT_BOUND\""), run.text());
        assertTrue(run.text().contains("\"path\": \"/BG-25/0/BG-DEX-01/0/BT-126\""),
                run.text());
        assertEquals("", run.err(), "the report is the output, so nothing is said twice");
    }

    /**
     * A conversion that wrote a value the invoice did not state says so without being
     * asked.
     *
     * <p>UBL requires the tax scheme of every party tax scheme, and EN 16931-1 has no
     * term for the scheme of a registration that is not for value added tax, so the
     * binding table says what is written there. Nothing of the document was lost and the
     * run ends with 0 — but the file the caller now holds says something the invoice did
     * not, so the line is on the error stream of the ordinary run, as information rather
     * than as a warning and without being counted among the shortfalls.
     */
    @Test
    void saysOnTheErrorStreamWhatTheSyntaxAskedForAndTheInvoiceDidNotState() {
        String file = withSellerTaxRegistration();
        Path written = directory.resolve("convention.ubl.xml");

        Cli.Run run = Cli.run("convert", "--to", "ubl", "--out", written.toString(), file);

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().startsWith("info: CONVENTION_APPLIED: "), run.err());
        assertTrue(run.err().contains("/Invoice/cac:AccountingSupplierParty/cac:Party"
                        + "/cac:PartyTaxScheme/cac:TaxScheme/cbc:ID is required by"
                        + " UBL-SR-53"), run.err());
        assertTrue(run.err().contains("no business term of this document states it, so it"
                + " was written as FC"), run.err());
        assertTrue(run.err().contains("UNTDID 1153"),
                "and the line says where the value comes from: " + run.err());
        assertFalse(run.err().contains("warning:"),
                "nothing fell short, so nothing warns: " + run.err());
        assertTrue(read(written).contains("<cbc:ID>FC</cbc:ID>"), read(written));
    }

    /** A document the syntax asks nothing of beyond what it states says nothing either. */
    @Test
    void saysNothingWhereTheSyntaxAskedForNothingTheInvoiceDidNotState() {
        Path written = directory.resolve("plain.ubl.xml");

        Cli.Run run = Cli.run("convert", "--to", "ubl", "--out", written.toString(),
                Fixtures.file(directory, "examples/standard-invoice.esj.json"));

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("", run.err(), "this invoice states everything the syntax asks for");
    }

    /** The JSON form carries the convention as data and says it once, not twice. */
    @Test
    void carriesTheConventionAsDataUnderOutputJson() {
        String file = withSellerTaxRegistration();
        Path written = directory.resolve("convention.ubl.xml");

        Cli.Run run = Cli.run("convert", "--to", "ubl", "--out", written.toString(),
                "--output", "json", file);

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"kind\": \"CONVENTION_APPLIED\""), run.text());
        assertTrue(run.text().contains("\"complete\": true"),
                "a document that needed only a convention is complete: " + run.text());
        assertEquals("", run.err(), "the report is the output, so nothing is said twice");
    }

    /**
     * Returns the standard invoice example with a seller tax registration identifier
     * beside its value added tax identifier, which is the document UBL asks a tax scheme
     * of that no business term names.
     */
    private String withSellerTaxRegistration() {
        String source = Fixtures.text("examples/standard-invoice.esj.json")
                .replace(SELLER_VAT_IDENTIFIER,
                        SELLER_VAT_IDENTIFIER + "\n    \"/BG-4/BT-32\": \"12/345/67890\",");
        assertTrue(source.contains("/BG-4/BT-32"),
                "the example still carries the line the registration is added after");
        return Fixtures.write(directory, "seller-tax-registration.esj.json",
                source.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void refusesTheJsonReportWithNowhereToPutTheDocument() {
        Cli.Run run = Cli.run("convert", "--output", "json", Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("add --out"), run.err());
    }

    @Test
    void refusesAnEsjSerializationForATargetThatIsNotEsj() {
        Cli.Run run = Cli.run("convert", "--to", "cii", "--canonical",
                Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("says nothing about an XML syntax"), run.err());
    }

    /**
     * Every UBL instance of the conformance corpus, converted to a cross industry invoice
     * and put through {@code esj validate} — the official schema modules and the
     * Schematron of the profile, run by the tool itself rather than by a library call.
     *
     * <p>Two instances are expected to be refused, and each is named here with the rule it
     * fires. Neither is a value in the wrong element:
     * {@code conformance/writers/cii-roundtrip.md} explains both, and a third refusal, or
     * one of these two disappearing, fails here.
     *
     * <p>The question is what the artefacts say, so the native rule pack is left out. It
     * carries no core invoice usage specification's levels and reports a rule of the
     * standard that a profile levels down as the fatal rule it is;
     * {@code conformance/rules/corpus.md} names every document that happens to.
     */
    @Test
    void everyUblInstanceOfTheCorpusBecomesAnInvoiceTheArtefactsAccept() {
        Map<String, String> refused = Map.of(
                "business-cases/extension/05.01a-INVOICE_ubl.xml", "BR-CO-16");
        Path written = directory.resolve("converted.cii.xml");
        for (String instance : Fixtures.corpus()) {
            if (!instance.endsWith("_ubl.xml")) {
                continue;
            }
            Cli.Run converted = Cli.run("convert", "--to", "cii", "--extension", "xrechnung",
                    "--out", written.toString(),
                    Fixtures.file(directory, "conformance/kosit/" + instance));
            assertEquals(ExitCode.SUCCESS, converted.exitCode(), instance + converted.err());

            Cli.Run checked = Cli.run("validate", "--rules", "none", written.toString());
            String rule = refused.get(instance);
            if (rule == null) {
                assertEquals(ExitCode.SUCCESS, checked.exitCode(),
                        instance + " converts to an invoice the artefacts accept: "
                                + checked.text());
                assertTrue(checked.text().contains("VALID"), instance + checked.text());
            } else {
                assertEquals(ExitCode.VALIDATION, checked.exitCode(),
                        instance + " is the one the report names: " + checked.text());
                assertTrue(checked.text().contains(rule),
                        instance + " fires " + rule + ": " + checked.text());
            }
        }
    }

    /** Returns the text of a file the tool wrote. */
    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void refusesAnInputItCannotRecognize() {
        Cli.Run run = Cli.run("hello, world".getBytes(StandardCharsets.UTF_8), "convert", "-");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("neither an ESJ document"), run.err());
    }

    @Test
    void refusesAFileThatIsNotThere() {
        Cli.Run run = Cli.run("convert", directory.resolve("absent.esj.json").toString());
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("no such file"), run.err());
    }

    @Test
    void refusesAnEsjDocumentThatFailsLayerOne() {
        String file = Fixtures.file(directory, "examples/invalid/object-without-component.esj.json");
        Cli.Run run = Cli.run("convert", file);
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("ESJ-L1-VALUE-SHAPE"), run.err());
    }

    @Test
    void readsAnInputAsTheSyntaxThatWasNamedRatherThanTheOneItLooksLike() {
        String file = Fixtures.file(directory, UBL);
        Cli.Run run = Cli.run("convert", file, "--from", "esj");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("ESJ-L1-JSON"), run.err());
    }

    @Test
    void refusesAnInputOfNoBytes() {
        Cli.Run run = Cli.run("convert", Fixtures.write(directory, "empty.esj.json", new byte[0]));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("is empty"), run.err());
    }

    @Test
    void refusesADirectory() {
        Cli.Run run = Cli.run("convert", directory.toString());
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("not a file"), run.err());
    }

    @Test
    void refusesAnXmlInputAtTheBoundTheImporterReads() {
        byte[] oversize = paddedInvoice(
                (int) de.bsnsoft.esj.xr.XrImporter.DEFAULT_MAX_INPUT_BYTES + 1024);
        Cli.Run run = Cli.run("convert", Fixtures.write(directory, "big.xml", oversize));
        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        assertTrue(run.err().contains("larger than the"), run.err());
        assertTrue(run.err().contains("bytes of XML"), run.err());
    }

    @Test
    void refusesADocumentTypeDefinitionWithoutAStackTrace() {
        byte[] dtd = ("<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE Invoice [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>\n"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
                + "<ID>&xxe;</ID></Invoice>").getBytes(StandardCharsets.UTF_8);
        Cli.Run run = Cli.run("convert", Fixtures.write(directory, "dtd.xml", dtd));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertFalse(run.err().contains("\tat "), "no stack trace without --debug: " + run.err());
        assertFalse(run.err().contains("root:"), "no entity was resolved: " + run.err());
    }

    /** Returns a UBL invoice padded with a comment to at least the given number of bytes. */
    private static byte[] paddedInvoice(int bytes) {
        String head = "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
                + "<!--";
        String tail = "--></Invoice>";
        StringBuilder padded = new StringBuilder(bytes + tail.length());
        padded.append(head);
        while (padded.length() < bytes) {
            padded.append('x');
        }
        return padded.append(tail).toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void explainsWhatItDidUnderVerbose() {
        Cli.Run run = Cli.run("convert", "--verbose", Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("info: "), run.err());
        assertTrue(run.err().contains("ESJ (detected)"), run.err());
        assertTrue(run.err().contains(" ms"), run.err());
    }
}
