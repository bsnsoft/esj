package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XsltExecutable;
import org.junit.jupiter.api.Test;

/**
 * What the importer does with an XML document that was written to make a parser fetch,
 * expand or execute something. Every one of these is refused by the parser configuration
 * of this module rather than by a check further in, and a change that loosened that
 * configuration would show up here.
 *
 * <p>The addresses in these documents are unreachable by construction: a port on the
 * loopback interface that nothing listens on. A test that fetched one would fail slowly
 * rather than quietly succeed.
 */
class XmlHardeningTest {

    /** A loopback address in the range IANA keeps for dynamic ports, with no listener. */
    private static final String UNREACHABLE = "http://127.0.0.1:1/";

    private final XrImporter importer = new XrImporter();

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** A UBL invoice with the given text as the name of the seller. */
    private static String invoice(String sellerName, String rootAttributes) {
        return "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\""
                + rootAttributes + ">"
                + "<cbc:ID>RE-1</cbc:ID>"
                + "<cbc:IssueDate>2026-01-01</cbc:IssueDate>"
                + "<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>"
                + "<cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>"
                + "<cac:AccountingSupplierParty><cac:Party><cac:PartyLegalEntity>"
                + "<cbc:RegistrationName>" + sellerName + "</cbc:RegistrationName>"
                + "</cac:PartyLegalEntity></cac:Party></cac:AccountingSupplierParty>"
                + "</Invoice>";
    }

    @Test
    void refusesAnExternalParameterEntity() {
        byte[] document = utf8("<?xml version=\"1.0\"?>"
                + "<!DOCTYPE Invoice [<!ENTITY % remote SYSTEM \"" + UNREACHABLE + "e.dtd\">%remote;]>"
                + invoice("Example GmbH", ""));

        assertThrows(XrFormatException.class, () -> importer.importXml(document));
    }

    @Test
    void refusesAnExternalDocumentTypeDeclaration() {
        byte[] document = utf8("<?xml version=\"1.0\"?>"
                + "<!DOCTYPE Invoice SYSTEM \"" + UNREACHABLE + "invoice.dtd\">"
                + invoice("Example GmbH", ""));

        assertThrows(XrFormatException.class, () -> importer.importXml(document));
    }

    /**
     * A document type declaration is refused for what it is, not for what it contains:
     * this one declares nothing at all and is refused just the same, because a parser
     * that read it would be a parser that could read the others.
     */
    @Test
    void refusesADocumentTypeDeclarationThatDeclaresNothing() {
        byte[] document = utf8("<?xml version=\"1.0\"?><!DOCTYPE Invoice>"
                + invoice("Example GmbH", ""));

        assertThrows(XrFormatException.class, () -> importer.importXml(document));
    }

    /**
     * The entity expansion of the billion laughs attack needs a document type
     * declaration, so refusing the declaration ends it before any expansion begins. The
     * bound on the time is what tells the two apart: a parser that expanded these
     * entities and failed afterwards would pass an assertion on the exception alone.
     */
    @Test
    void refusesEntityExpansionQuickly() {
        StringBuilder subset = new StringBuilder("<!ENTITY a0 \"aaaaaaaaaa\">");
        for (int level = 1; level <= 12; level++) {
            subset.append("<!ENTITY a").append(level).append(" \"");
            subset.append(("&a" + (level - 1) + ";").repeat(10));
            subset.append("\">");
        }
        byte[] document = utf8("<?xml version=\"1.0\"?><!DOCTYPE Invoice [" + subset + "]>"
                + invoice("&a12;", ""));

        long start = System.nanoTime();
        assertThrows(XrFormatException.class, () -> importer.importXml(document));
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertTrue(took.toSeconds() < 5, "the refusal took " + took);
    }

    /**
     * XInclude is a way to pull a file into a document without a document type
     * declaration, so refusing the declaration does not cover it. The parser is asked not
     * to process it, and an unprocessed {@code xi:include} is an element like any other:
     * the stylesheets do not know it, so the seller name arrives empty rather than
     * carrying the file.
     */
    @Test
    void processesNoXInclude() {
        byte[] document = utf8("<?xml version=\"1.0\"?>"
                + invoice("<xi:include href=\"" + UNREACHABLE + "secret\" parse=\"text\"/>",
                        " xmlns:xi=\"http://www.w3.org/2001/XInclude\""));

        SemanticDocument imported = importer.importXml(document);

        assertFalse(imported.values().containsKey(SemanticPath.of("/BG-4/BT-27")),
                "an unprocessed xi:include carries no text, so the seller name has no value");
    }

    /**
     * The mapper walks the XR tree recursively, so a document that nests without end used
     * to end the call in a {@code StackOverflowError} — an error no caller of this module
     * agreed to and none of its documentation names. It is now an {@link XrLimitException}
     * like any other bound. The entry point that reads the XR representation directly is
     * the one that matters: it hands the caller's bytes to the mapper without a
     * transformation in between.
     */
    @Test
    void refusesAnXrDocumentThatNestsDeeperThanTheMapperWalks() {
        int depth = 5_000;
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?>")
                .append("<xr:invoice xmlns:xr=\"").append(XrTransformer.XR_NAMESPACE)
                .append("\">");
        xml.append("<nest>".repeat(depth));
        xml.append("</nest>".repeat(depth));
        xml.append("</xr:invoice>");
        byte[] document = utf8(xml.toString());

        assertThrows(XrLimitException.class, () -> importer.fromXr(document));
    }

    /**
     * The transformation dereferences no protocol, and this asks the processor the module
     * actually uses rather than a copy of it. The setting behind this is one whose values
     * Saxon has read differently from release to release — an empty value allowed every
     * protocol in 12.10 and allows none in 13.0 — so a stylesheet that reads a file is the
     * only evidence that says what the configured value does.
     */
    @Test
    void dereferencesNoProtocolFromAStylesheet() throws Exception {
        Path canary = Files.createTempFile("esj-hardening", ".txt");
        try {
            Files.writeString(canary, "the contents of a file the transformation may not read");
            String uri = canary.toUri().toString();
            String outcome = runProbeStylesheet(
                    "<xsl:template match=\'/\'><out><xsl:value-of"
                    + " select=\"unparsed-text(\'" + uri + "\')\"/></out></xsl:template>");
            assertFalse(outcome.contains("may not read"), outcome);
        } finally {
            Files.deleteIfExists(canary);
        }
    }

    /** A stylesheet cannot compile an XPath expression at run time. */
    @Test
    void evaluatesNoXPathExpressionAtRunTime() throws Exception {
        String outcome = runProbeStylesheet(
                "<xsl:template match=\'/\'><out><xsl:evaluate xpath=\"\'1+1\'\"/></out>"
                + "</xsl:template>");
        assertTrue(outcome.startsWith("refused"), outcome);
    }

    /**
     * Compiles and runs a stylesheet on the processor this module configures, and reports
     * what came of it. The processor is reached by reflection on purpose: a test that built
     * its own would assert the settings it had just made rather than the ones that ship.
     *
     * @param body the templates of the stylesheet
     * @return the serialized result, or {@code refused} and the reason
     */
    private static String runProbeStylesheet(String body) throws Exception {
        Field field = XrTransformer.class.getDeclaredField("PROCESSOR");
        field.setAccessible(true);
        Processor processor = (Processor) field.get(null);
        String stylesheet = "<xsl:stylesheet xmlns:xsl=\'http://www.w3.org/1999/XSL/Transform\'"
                + " version=\'3.0\'>" + body + "</xsl:stylesheet>";
        try {
            XsltExecutable executable = processor.newXsltCompiler().compile(new StreamSource(
                    new StringReader(stylesheet), "esj-kosit:/kosit/probe.xsl"));
            XdmDestination destination = new XdmDestination();
            executable.load30().applyTemplates(processor.newDocumentBuilder().build(
                    new StreamSource(new StringReader("<a/>"), "esj-kosit:/kosit/probe.xml")),
                    destination);
            return processor.newSerializer().serializeNodeToString(destination.getXdmNode());
        } catch (SaxonApiException e) {
            return "refused: " + e.getMessage();
        }
    }

    /**
     * A schema location is a URL in an attribute, and a validating parser would fetch it.
     * This one does not validate and does not fetch: the document is read, and the
     * unreachable address it names is never asked for.
     */
    @Test
    void fetchesNoSchemaLocation() {
        byte[] document = utf8("<?xml version=\"1.0\"?>"
                + invoice("Example GmbH",
                        " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                                + " xsi:schemaLocation=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2 "
                                + UNREACHABLE + "UBL-Invoice-2.1.xsd\""));

        SemanticDocument imported = importer.importXml(document);

        SemanticValue name = imported.values().get(SemanticPath.of("/BG-4/BT-27"));

        assertEquals(SemanticValue.of("Example GmbH"), name);
    }
}
