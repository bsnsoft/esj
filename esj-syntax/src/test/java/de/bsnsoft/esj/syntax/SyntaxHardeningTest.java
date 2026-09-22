package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What the engine does with bytes that were written to make a validator fetch, expand or
 * execute something, and with bytes that do not say what they are.
 *
 * <p>These are the documents the hardening tests of {@code esj-xr} use, met by the same
 * front door one module further on. The point of repeating them here is the order: a
 * hostile document has to be refused by the parser <em>before</em> the schema sees it, and
 * a schema validator is a second parser with a second set of settings. A finding from the
 * schema on one of these would mean the bytes had already been read by something that was
 * not the front door.
 *
 * <p>The addresses in these documents are unreachable by construction: a port on the
 * loopback interface that nothing listens on. A run that fetched one would fail slowly
 * rather than quietly succeed.
 */
class SyntaxHardeningTest {

    /** A loopback address in the range IANA keeps for dynamic ports, with no listener. */
    private static final String UNREACHABLE = "http://127.0.0.1:1/";

    private static final String XRECHNUNG =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    /**
     * A document that makes an official rule set stop is a finding about that document,
     * not a broken installation.
     *
     * <p>An amount of one followed by three hundred and twenty zeros is well formed, is
     * valid against the UBL schema — {@code xs:decimal} bounds no digit count — and
     * therefore reaches the CEN artefact, which converts it with {@code number()}, gets an
     * infinity and stops. The pack is the source of the message and not the culprit: it is
     * the released file, it compiled, and it ran over every other document of the corpus.
     * So the finding is fatal and names the rule set, and the run still reaches a verdict.
     */
    @Test
    void reportsARuleSetThatStoppedOverTheDocumentAsAFindingOfThatDocument() {
        byte[] overflowing = new String(
                Corpus.instance("business-cases/standard/01.01a-INVOICE_ubl.xml"),
                StandardCharsets.UTF_8)
                .replace(">314.86<", ">1" + "0".repeat(320) + "<")
                .getBytes(StandardCharsets.UTF_8);

        SyntaxReport report = SyntaxValidator.validate(overflowing);

        List<SyntaxFinding> stopped = report.findings().stream()
                .filter(finding -> finding.code().equals("ARTEFACT-STOPPED")).toList();
        assertEquals(1, stopped.size(),
                "the rule set that stopped is reported once: " + report.findings());
        assertEquals(Severity.FATAL, stopped.get(0).severity(),
                "a rule set that did not finish has checked nothing, so the document is"
                        + " not one this tool calls valid");
        assertEquals("en16931-ubl-schematron", stopped.get(0).component());
        assertTrue(stopped.get(0).message().contains("EN16931-UBL-validation.xslt"),
                "the message names the rule set: " + stopped.get(0).message());
        assertEquals(Verdict.INVALID, report.verdict());

        ComponentRun run = report.ran().stream()
                .filter(each -> each.component().equals("en16931-ubl-schematron"))
                .findFirst().orElseThrow();
        assertTrue(run.stopped(),
                "the artefact was applied and cost what it cost, so it stays in the list of"
                        + " what ran — marked, because being there is not a statement that"
                        + " its rules were checked");
        assertTrue(report.ran().stream()
                        .filter(each -> each.component().equals("xrechnung-ubl-schematron"))
                        .noneMatch(ComponentRun::stopped),
                "the rule set that did finish is not marked");
    }

    /**
     * A limit that was a time carries the time it was, and one that was a size carries no
     * time at all.
     *
     * <p>A caller that spends one bound of its own across several steps hands this module
     * what is left rather than what its user asked for, so the message it composes needs
     * both numbers; the one this module can give it is the budget this validation had. A
     * size limit has no such number, and a sentence about a clock under a message about
     * bytes would send a reader after the wrong option.
     */
    @Test
    void namesTheBudgetOfARunThatOutlastedIt() {
        SyntaxLimitException outOfTime = assertThrows(SyntaxLimitException.class,
                () -> SyntaxValidator.validate(
                        Corpus.instance("business-cases/standard/01.01a-INVOICE_ubl.xml"),
                        SyntaxOptions.defaults().withMaxRuntime(Duration.ofMillis(1))));
        assertEquals(Optional.of(Duration.ofMillis(1)), outOfTime.budget());

        SyntaxLimitException tooLarge = assertThrows(SyntaxLimitException.class,
                () -> SyntaxValidator.validate(
                        Corpus.instance("business-cases/standard/01.01a-INVOICE_ubl.xml"),
                        SyntaxOptions.defaults().withMaxInputBytes(16)));
        assertEquals(Optional.empty(), tooLarge.budget(),
                "a limit that was not a time names no time: " + tooLarge.getMessage());
    }

    /**
     * A schema validator quotes the value it refused, so a value of the document travels
     * into a message. Whatever steers a terminal rather than saying something is collapsed
     * out of it: Java's own definition of whitespace covers neither the Unicode line
     * separator nor the next-line control, and a sender who could put either into a report
     * could write a line that reads like a result of this tool.
     */
    @Test
    void keepsNoLineSeparatorOfTheDocumentInAMessage() {
        byte[] steering = new String(
                Corpus.instance("business-cases/standard/01.01a-INVOICE_ubl.xml"),
                StandardCharsets.UTF_8)
                .replace(">314.86<", ">1.00\u202e\u2028\u0085 UBL 2.1 XSD: OK<")
                .getBytes(StandardCharsets.UTF_8);

        SyntaxReport report = SyntaxValidator.validate(steering);

        assertFalse(report.findings().isEmpty(), "the schema refuses the value");
        for (SyntaxFinding finding : report.findings()) {
            for (int i = 0; i < finding.message().length(); i++) {
                char c = finding.message().charAt(i);
                assertFalse(Character.getType(c) == Character.CONTROL
                                || Character.getType(c) == Character.FORMAT
                                || Character.getType(c) == Character.LINE_SEPARATOR
                                || Character.getType(c) == Character.PARAGRAPH_SEPARATOR,
                        "the message carries nothing that steers a terminal: "
                                + finding.message());
            }
        }
    }

    @Test
    void refusesAnExternalParameterEntityBeforeTheSchema() {
        refusedByTheParser("<?xml version=\"1.0\"?>"
                + "<!DOCTYPE Invoice [<!ENTITY % remote SYSTEM \"" + UNREACHABLE
                + "e.dtd\">%remote;]>" + invoice("Example GmbH", ""));
    }

    @Test
    void refusesAnExternalDocumentTypeDeclarationBeforeTheSchema() {
        refusedByTheParser("<?xml version=\"1.0\"?><!DOCTYPE Invoice SYSTEM \""
                + UNREACHABLE + "invoice.dtd\">" + invoice("Example GmbH", ""));
    }

    @Test
    void refusesADocumentTypeDeclarationThatDeclaresNothing() {
        refusedByTheParser("<?xml version=\"1.0\"?><!DOCTYPE Invoice>"
                + invoice("Example GmbH", ""));
    }

    /**
     * The entity expansion of the billion laughs attack needs a document type
     * declaration, so refusing the declaration ends it before any expansion begins. The
     * bound on the time is what tells the two apart: a validator that expanded these
     * entities and failed afterwards would pass an assertion on the finding alone.
     */
    @Test
    void refusesEntityExpansionQuickly() {
        StringBuilder subset = new StringBuilder("<!ENTITY a0 \"aaaaaaaaaa\">");
        for (int level = 1; level <= 12; level++) {
            subset.append("<!ENTITY a").append(level).append(" \"");
            subset.append(("&a" + (level - 1) + ";").repeat(10));
            subset.append("\">");
        }

        long start = System.nanoTime();
        refusedByTheParser("<?xml version=\"1.0\"?><!DOCTYPE Invoice [" + subset + "]>"
                + invoice("&a12;", ""));
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertTrue(took.toSeconds() < 5, "the refusal took " + took);
    }

    /**
     * XInclude is a way to pull a file into a document without a document type
     * declaration, so refusing the declaration does not cover it. It is not processed, and
     * an unprocessed {@code xi:include} is an element like any other: the schema meets it
     * where it does not belong and says so, which is the evidence that it was never
     * expanded.
     */
    @Test
    void processesNoXInclude() {
        SyntaxReport report = validate("<?xml version=\"1.0\"?>"
                + invoice("<xi:include href=\"" + UNREACHABLE + "secret\" parse=\"text\"/>",
                        " xmlns:xi=\"http://www.w3.org/2001/XInclude\""));

        assertEquals(Verdict.INVALID, report.verdict());
        assertFalse(report.findings(Engine.XSD).isEmpty(),
                "the element reaches the schema as an element");
        assertTrue(report.findings().stream().noneMatch(finding ->
                        finding.message().contains("127.0.0.1")),
                "nothing was fetched from the address the document names");
    }

    /**
     * A schema location is a URL in an attribute, and a validator that honoured it would
     * fetch it. This one validates against the schema of the pack and asks no host.
     */
    @Test
    void fetchesNoSchemaLocation() {
        SyntaxReport report = validate("<?xml version=\"1.0\"?>"
                + invoice("Example GmbH",
                        " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                        + " xsi:schemaLocation=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2 "
                        + UNREACHABLE + "UBL-Invoice-2.1.xsd\""));

        assertTrue(report.findings().stream().noneMatch(finding ->
                        finding.message().contains("127.0.0.1")),
                "the address the document names was never asked for: " + report.findings());
    }

    @Test
    void refusesADocumentThatDoesNotSpellWhatItDeclares() {
        byte[] latinInUtf8 = concatenate(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes(StandardCharsets.UTF_8),
                invoice("Müller GmbH", "").getBytes(StandardCharsets.ISO_8859_1));

        SyntaxReport report = SyntaxValidator.validate(latinInUtf8);

        assertEquals(Verdict.INVALID, report.verdict());
        assertEquals(List.of(XmlCheck.ENCODING),
                report.findings().stream().map(SyntaxFinding::code).toList());
        assertEquals(Engine.PARSER, report.findings().get(0).engine());
        assertEquals(List.of(), report.ran(), "nothing was run over bytes whose text"
                + " nobody can read");
    }

    @Test
    void readsADocumentThatSpellsWhatItDeclares() {
        byte[] latin = concatenate(
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                        .getBytes(StandardCharsets.ISO_8859_1),
                invoice("Müller GmbH", "").getBytes(StandardCharsets.ISO_8859_1));

        SyntaxReport report = SyntaxValidator.validate(latin);

        assertTrue(report.findings(Engine.PARSER).isEmpty(),
                "a document in the encoding it declares is read: "
                        + report.findings(Engine.PARSER));
    }

    @Test
    void readsADocumentWithAByteOrderMark() {
        byte[] marked = concatenate(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + invoice("Example GmbH", ""))
                        .getBytes(StandardCharsets.UTF_8));

        SyntaxReport report = SyntaxValidator.validate(marked);

        assertTrue(report.findings(Engine.PARSER).isEmpty(),
                "a byte order mark in front of a UTF-8 document is a byte order mark: "
                        + report.findings(Engine.PARSER));
    }

    @Test
    void refusesADocumentLargerThanTheRunWasGiven() {
        byte[] document = Corpus.instance("business-cases/standard/01.01a-INVOICE_ubl.xml");

        SyntaxLimitException refused = assertThrows(SyntaxLimitException.class,
                () -> SyntaxValidator.validate(document,
                        SyntaxOptions.defaults().withMaxInputBytes(1024)));

        assertTrue(refused.getMessage().contains("no verdict"),
                "a limit is not a verdict: " + refused.getMessage());
    }

    @Test
    void refusesToAnswerWhenTheTimeRanOut() {
        byte[] document = Corpus.instance("business-cases/standard/01.01a-INVOICE_ubl.xml");

        SyntaxLimitException refused = assertThrows(SyntaxLimitException.class,
                () -> SyntaxValidator.validate(document,
                        SyntaxOptions.defaults().withMaxRuntime(Duration.ofNanos(1))));

        assertTrue(refused.getMessage().contains("no verdict"),
                "a limit is not a verdict: " + refused.getMessage());
    }

    @Test
    void refusesADocumentOfASyntaxNoPackBinds() {
        assertThrows(SyntaxNotSupportedException.class,
                () -> SyntaxValidator.validate("<order/>".getBytes(StandardCharsets.UTF_8)));
    }

    /** Asserts that the parser refused the document and that nothing else ran. */
    private static void refusedByTheParser(String document) {
        SyntaxReport report = validate(document);

        assertEquals(Verdict.INVALID, report.verdict());
        assertEquals(List.of(), report.ran(), "nothing ran over a document the parser"
                + " refused");
        assertTrue(report.syntax().isEmpty(), "a document that was not parsed has no syntax");
        List<SyntaxFinding> findings = report.findings();
        assertEquals(1, findings.size(), "the parser said one thing: " + findings);
        assertEquals(Engine.PARSER, findings.get(0).engine());
        assertEquals(FindingCategory.XML, findings.get(0).category());
        assertEquals(Severity.FATAL, findings.get(0).severity());
    }

    private static SyntaxReport validate(String document) {
        return SyntaxValidator.validate(document.getBytes(StandardCharsets.UTF_8));
    }

    /** A UBL invoice with the given text as the name of the seller. */
    private static String invoice(String sellerName, String rootAttributes) {
        return "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\""
                + rootAttributes + ">"
                + "<cbc:CustomizationID>" + XRECHNUNG + "</cbc:CustomizationID>"
                + "<cbc:ID>RE-1</cbc:ID>"
                + "<cbc:IssueDate>2026-01-01</cbc:IssueDate>"
                + "<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>"
                + "<cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>"
                + "<cac:AccountingSupplierParty><cac:Party><cac:PartyLegalEntity>"
                + "<cbc:RegistrationName>" + sellerName + "</cbc:RegistrationName>"
                + "</cac:PartyLegalEntity></cac:Party></cac:AccountingSupplierParty>"
                + "</Invoice>";
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] both = new byte[first.length + second.length];
        System.arraycopy(first, 0, both, 0, first.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }
}
