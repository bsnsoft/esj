package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What the reader makes of one document, one family of terms at a time.
 *
 * <p>The corpus comparison in {@link ReaderCorpusTest} asks whether the whole of two
 * readers agree over 86 real invoices. It cannot say which rule broke when they stop
 * agreeing, and it says nothing at all about the cases no invoice of that corpus happens
 * to carry. These tests ask one question each.
 */
class StreamingReaderTest {

    /** The default namespace of a UBL 2.1 invoice. */
    private static final String UBL_INVOICE_NAMESPACE =
            "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";

    /** The namespace of the aggregate components both UBL document types are built from. */
    private static final String UBL_AGGREGATE_NAMESPACE =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    private final StreamingReader reader = new StreamingReader();

    @Test
    void readsATextACodeAndADate() {
        SemanticDocument document = document(Documents.ubl(
                "<cbc:ID>RE-4711</cbc:ID>"
                        + "<cbc:IssueDate>2026-09-19</cbc:IssueDate>"
                        + "<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>"));
        assertEquals("RE-4711", content(document, "/BT-1"));
        assertEquals("2026-09-19", content(document, "/BT-2"));
        assertEquals("380", content(document, "/BT-3"));
        assertEquals(Optional.of("UBL"), document.source().orElseThrow().syntax());
    }

    /**
     * The conventional value at an element a business term is bound to is not that term.
     * UBL requires {@code cac:OrderReference/cbc:ID} as soon as the sales order reference
     * BT-14 is written, and {@code model/bindings/ubl-invoice.json} says what stands there
     * where a document states no purchase order reference.
     */
    @Test
    void doesNotReadTheConventionalValueAsTheTermItStandsFor() {
        ImportResult result = reader.read(Documents.ubl("<cac:OrderReference>"
                + "<cbc:ID>NA</cbc:ID><cbc:SalesOrderID>SO-1</cbc:SalesOrderID>"
                + "</cac:OrderReference>"));
        assertTrue(result.document().value(SemanticPath.of("/BT-13")).isEmpty(),
                "the conventional value states no purchase order reference");
        assertEquals("SO-1", content(result.document(), "/BT-14"));
        assertEquals(List.of(ImportNote.Kind.CONVENTION_NOT_READ),
                result.report().notes().stream().map(ImportNote::kind).toList());
        assertEquals(ImportNote.Level.INFORMATION,
                result.report().notes().get(0).level(),
                "nothing the semantic model can hold was lost");
    }

    /**
     * Without the sales order reference the condition of that convention does not hold,
     * and the same two letters are an ordinary purchase order reference.
     */
    @Test
    void readsTheSameValueAsTheTermWhereTheConventionDoesNotApply() {
        ImportResult result = reader.read(Documents.ubl(
                "<cac:OrderReference><cbc:ID>NA</cbc:ID></cac:OrderReference>"));
        assertEquals("NA", content(result.document(), "/BT-13"));
        assertEquals(List.of(), result.report().notes());
    }

    /** Any other content at that element is the term, whatever stands beside it. */
    @Test
    void readsAnOrdinaryPurchaseOrderReferenceBesideASalesOrderReference() {
        SemanticDocument document = document(Documents.ubl("<cac:OrderReference>"
                + "<cbc:ID>PO-1</cbc:ID><cbc:SalesOrderID>SO-1</cbc:SalesOrderID>"
                + "</cac:OrderReference>"));
        assertEquals("PO-1", content(document, "/BT-13"));
        assertEquals("SO-1", content(document, "/BT-14"));
    }

    @Test
    void readsTheEightDigitDateOfCii() {
        SemanticDocument document = document(Documents.cii(
                "<rsm:ExchangedDocument><ram:ID>RE-4711</ram:ID><ram:IssueDateTime>"
                        + "<udt:DateTimeString format=\"102\">20260919</udt:DateTimeString>"
                        + "</ram:IssueDateTime></rsm:ExchangedDocument>"));
        assertEquals("2026-09-19", content(document, "/BT-2"));
    }

    @Test
    void reportsADateWhoseFormatQualifierIsNot102() {
        ImportResult result = reader.read(Documents.cii(
                "<rsm:ExchangedDocument><ram:IssueDateTime>"
                        + "<udt:DateTimeString format=\"203\">202609191200</udt:DateTimeString>"
                        + "</ram:IssueDateTime></rsm:ExchangedDocument>"));
        assertTrue(result.document().value(SemanticPath.of("/BT-2")).isEmpty(),
                "a date the binding does not name is no value");
        assertEquals(List.of(ImportNote.Kind.UNPLACEABLE),
                result.report().notes().stream().map(ImportNote::kind).toList(),
                "and the date the source states is a loss the reader describes");
        assertEquals("BT-2", result.report().notes().get(0).location());
    }

    @Test
    void keepsACiiGlobalIdentifierThatStatesNoScheme() {
        ImportResult result = reader.read(Documents.ciiAgreement(
                "<ram:SellerTradeParty><ram:GlobalID>987654321</ram:GlobalID>"
                        + "</ram:SellerTradeParty>"));

        assertEquals(Optional.of("987654321"),
                result.document().value(SemanticPath.of("/BG-4/BT-29/0"))
                        .map(SemanticValue::content),
                "the scheme is optional in the schema of the syntax, so the identifier"
                        + " arrives without one and the term holds it");
        assertTrue(result.report().isEmpty(), "and nothing was lost to report");
    }

    @Test
    void reportsACiiTaxRegistrationOfAnotherScheme() {
        ImportResult result = reader.read(Documents.ciiAgreement(
                "<ram:SellerTradeParty><ram:SpecifiedTaxRegistration>"
                        + "<ram:ID schemeID=\"XX\">ATU123456789</ram:ID>"
                        + "</ram:SpecifiedTaxRegistration></ram:SellerTradeParty>"));

        assertTrue(result.document().value(SemanticPath.of("/BG-4/BT-31")).isEmpty(),
                "a registration of another scheme is neither the VAT identifier");
        assertTrue(result.document().value(SemanticPath.of("/BG-4/BT-32")).isEmpty(),
                "nor the fiscal registration identifier");
        assertEquals(List.of(ImportNote.Kind.UNPLACEABLE),
                result.report().notes().stream().map(ImportNote::kind).toList(),
                "and the element the source carried is reported rather than dropped");
        assertEquals("BT-31, BT-32", result.report().notes().get(0).location());
    }

    @Test
    void reportsACiiTaxRepresentativeRegistrationOfAnotherScheme() {
        ImportResult result = reader.read(Documents.ciiAgreement(
                "<ram:SellerTaxRepresentativeTradeParty><ram:SpecifiedTaxRegistration>"
                        + "<ram:ID schemeID=\"FC\">123/456/789</ram:ID>"
                        + "</ram:SpecifiedTaxRegistration>"
                        + "</ram:SellerTaxRepresentativeTradeParty>"));

        assertTrue(result.document().value(SemanticPath.of("/BG-11/BT-63")).isEmpty(),
                "BT-63 is the VAT identifier of the tax representative and this is not one");
        assertEquals("BT-63", result.report().notes().get(0).location());
    }

    @Test
    void reportsAUblPartyTaxSchemeThatIsNotValueAddedTax() {
        ImportResult result = reader.read(Documents.ubl(
                "<cac:AccountingCustomerParty><cac:Party><cac:PartyTaxScheme>"
                        + "<cbc:CompanyID>DE123456789</cbc:CompanyID>"
                        + "<cac:TaxScheme><cbc:ID>FC</cbc:ID></cac:TaxScheme>"
                        + "</cac:PartyTaxScheme></cac:Party></cac:AccountingCustomerParty>"));

        assertTrue(result.document().value(SemanticPath.of("/BG-7/BT-48")).isEmpty(),
                "BT-48 is the buyer VAT identifier and this registration is of another tax");
        assertEquals(List.of(ImportNote.Kind.UNPLACEABLE),
                result.report().notes().stream().map(ImportNote::kind).toList());
        assertEquals("BT-48", result.report().notes().get(0).location());
    }

    @Test
    void reportsAnEightDigitDateThatNamesNoDay() {
        ImportResult result = reader.read(Documents.cii(
                "<rsm:ExchangedDocument><ram:IssueDateTime>"
                        + "<udt:DateTimeString format=\"102\">20260230</udt:DateTimeString>"
                        + "</ram:IssueDateTime></rsm:ExchangedDocument>"));
        assertEquals(List.of(ImportNote.Kind.MALFORMED),
                result.report().notes().stream().map(ImportNote::kind).toList());
    }

    @Test
    void readsADecimalWithoutTouchingIt() {
        SemanticDocument document = document(Documents.ubl(
                "<cac:LegalMonetaryTotal><cbc:PayableAmount currencyID=\"EUR\">84.0300"
                        + "</cbc:PayableAmount></cac:LegalMonetaryTotal>"));
        assertEquals("84.03", content(document, "/BG-22/BT-115"),
                "the canonical decimal grammar of the specification, section 6.4");
    }

    @Test
    void readsAnIdentifierWithItsScheme() {
        SemanticDocument document = document(Documents.ubl(
                "<cac:AccountingSupplierParty><cac:Party><cbc:EndpointID schemeID=\"0088\">"
                        + "4004561000005</cbc:EndpointID></cac:Party>"
                        + "</cac:AccountingSupplierParty>"));
        SemanticValue value = document.value(SemanticPath.of("/BG-4/BT-34")).orElseThrow();
        assertEquals("4004561000005", value.canonicalContent());
        assertEquals("0088", value.scheme());
    }

    @Test
    void readsAnIdentifierWhoseSchemeIsASiblingElement() {
        SemanticDocument document = document(Documents.ciiAgreement(
                "<ram:AdditionalReferencedDocument><ram:IssuerAssignedID>ANG987"
                        + "</ram:IssuerAssignedID><ram:TypeCode>130</ram:TypeCode>"
                        + "<ram:ReferenceTypeCode>ABZ</ram:ReferenceTypeCode>"
                        + "</ram:AdditionalReferencedDocument>"));
        SemanticValue value = document.value(SemanticPath.of("/BT-18")).orElseThrow();
        assertEquals("ANG987", value.canonicalContent());
        assertEquals("ABZ", value.scheme(),
                "the scheme of a CII document identifier is the element beside it");
    }

    @Test
    void readsAnIdentifierWithASchemeAndAVersion() {
        SemanticDocument document = document(Documents.ubl(
                "<cac:InvoiceLine><cac:Item><cac:CommodityClassification>"
                        + "<cbc:ItemClassificationCode listID=\"TST\" listVersionID=\"19.05.01\">"
                        + "9873242</cbc:ItemClassificationCode>"
                        + "</cac:CommodityClassification></cac:Item></cac:InvoiceLine>"));
        SemanticValue value =
                document.value(SemanticPath.of("/BG-25/0/BG-31/BT-158/0")).orElseThrow();
        assertEquals("TST", value.scheme());
        assertEquals("19.05.01", value.schemeVersion());
    }

    @Test
    void readsAnAttachmentWithBothOfItsComponents() {
        SemanticDocument document = document(Documents.ubl(
                "<cac:AdditionalDocumentReference><cbc:ID>doc-1</cbc:ID><cac:Attachment>"
                        + "<cbc:EmbeddedDocumentBinaryObject mimeCode=\"application/pdf\""
                        + " filename=\"note.pdf\">JVBERi0xLjQK"
                        + "</cbc:EmbeddedDocumentBinaryObject></cac:Attachment>"
                        + "</cac:AdditionalDocumentReference>"));
        SemanticValue value =
                document.value(SemanticPath.of("/BG-24/0/BT-125")).orElseThrow();
        assertEquals("JVBERi0xLjQK", value.canonicalContent());
        assertEquals("application/pdf", value.mimeCode());
        assertEquals("note.pdf", value.filename());
    }

    @Test
    void reportsAnAttachmentWithoutItsComponents() {
        ImportResult result = reader.read(Documents.ubl(
                "<cac:AdditionalDocumentReference><cbc:ID>doc-1</cbc:ID><cac:Attachment>"
                        + "<cbc:EmbeddedDocumentBinaryObject>JVBERi0xLjQK"
                        + "</cbc:EmbeddedDocumentBinaryObject></cac:Attachment>"
                        + "</cac:AdditionalDocumentReference>"));
        assertEquals(List.of(ImportNote.Kind.COMPONENT_MISSING),
                result.report().notes().stream().map(ImportNote::kind).toList());
    }

    @Test
    void readsATermBoundToAnAttribute() {
        SemanticDocument document = document(Documents.ubl(
                "<cac:InvoiceLine><cac:Price><cbc:BaseQuantity unitCode=\"MTR\">2"
                        + "</cbc:BaseQuantity></cac:Price></cac:InvoiceLine>"));
        assertEquals("2", content(document, "/BG-25/0/BG-29/BT-149"));
        assertEquals("MTR", content(document, "/BG-25/0/BG-29/BT-150"));
    }

    @Test
    void tellsAnAllowanceFromAChargeByItsIndicator() {
        SemanticDocument document = document(Documents.ubl(
                "<cac:AllowanceCharge><cbc:ChargeIndicator>false</cbc:ChargeIndicator>"
                        + "<cbc:Amount currencyID=\"EUR\">10.00</cbc:Amount>"
                        + "</cac:AllowanceCharge>"
                        + "<cac:AllowanceCharge><cbc:ChargeIndicator>true</cbc:ChargeIndicator>"
                        + "<cbc:Amount currencyID=\"EUR\">5.00</cbc:Amount>"
                        + "</cac:AllowanceCharge>"));
        assertEquals("10", content(document, "/BG-20/0/BT-92"));
        assertEquals("5", content(document, "/BG-21/0/BT-99"));
    }

    @Test
    void tellsTheTwoTaxTotalsApartByTheCurrencyOfTheDocument() {
        SemanticDocument document = document(Documents.ubl(
                "<cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>"
                        + "<cbc:TaxCurrencyCode>SEK</cbc:TaxCurrencyCode>"
                        + "<cac:TaxTotal><cbc:TaxAmount currencyID=\"EUR\">19.00"
                        + "</cbc:TaxAmount></cac:TaxTotal>"
                        + "<cac:TaxTotal><cbc:TaxAmount currencyID=\"SEK\">200.00"
                        + "</cbc:TaxAmount></cac:TaxTotal>"));
        assertEquals("19", content(document, "/BG-22/BT-110"));
        assertEquals("200", content(document, "/BG-22/BT-111"));
    }

    @Test
    void countsTheInstancesOfARepeatedGroup() {
        SemanticDocument document = document(Documents.ubl(
                "<cac:InvoiceLine><cbc:ID>1</cbc:ID></cac:InvoiceLine>"
                        + "<cac:InvoiceLine><cbc:ID>2</cbc:ID></cac:InvoiceLine>"
                        + "<cac:InvoiceLine><cbc:ID>3</cbc:ID></cac:InvoiceLine>"));
        assertEquals("1", content(document, "/BG-25/0/BT-126"));
        assertEquals("2", content(document, "/BG-25/1/BT-126"));
        assertEquals("3", content(document, "/BG-25/2/BT-126"));
    }

    @Test
    void leavesNoGapWhereAGroupInstanceProducedNothing() {
        SemanticDocument document = document(Documents.ubl(
                "<cac:InvoiceLine><cbc:ID>1</cbc:ID></cac:InvoiceLine>"
                        + "<cac:InvoiceLine><cbc:Unknown>x</cbc:Unknown></cac:InvoiceLine>"
                        + "<cac:InvoiceLine><cbc:ID>3</cbc:ID></cac:InvoiceLine>"));
        assertEquals("3", content(document, "/BG-25/1/BT-126"),
                "an instance that produced nothing spends no index");
    }

    @Test
    void opensAGroupTheSyntaxDoesNotRepresentOncePerElement() {
        SemanticDocument document = document(Documents.ubl(
                "<cbc:Note>first</cbc:Note><cbc:Note>second</cbc:Note>"));
        assertEquals("first", content(document, "/BG-1/0/BT-22"));
        assertEquals("second", content(document, "/BG-1/1/BT-22"));
    }

    @Test
    void splitsTheSubjectCodeOutOfAUblNote() {
        SemanticDocument document =
                document(Documents.ubl("<cbc:Note>#AAC#Delivery on Monday</cbc:Note>"));
        assertEquals("AAC", content(document, "/BG-1/0/BT-21"));
        assertEquals("Delivery on Monday", content(document, "/BG-1/0/BT-22"));
    }

    @Test
    void keepsANoteThatIsNothingButAPrefix() {
        SemanticDocument document = document(Documents.ubl("<cbc:Note>#AAC#</cbc:Note>"));
        assertEquals("#AAC#", content(document, "/BG-1/0/BT-22"));
        assertTrue(document.value(SemanticPath.of("/BG-1/0/BT-21")).isEmpty());
    }

    @Test
    void leavesTheNoteAloneWhenNoCorrectionIsAskedFor() {
        StreamingReader strict = new StreamingReader(ReaderOptions.builder()
                .mode(ReaderMode.STRICT).build());
        SemanticDocument document = strict
                .read(Documents.ubl("<cbc:Note>#AAC#Delivery on Monday</cbc:Note>"))
                .document();
        assertEquals("#AAC#Delivery on Monday", content(document, "/BG-1/0/BT-22"));
        assertTrue(document.value(SemanticPath.of("/BG-1/0/BT-21")).isEmpty());
    }

    @Test
    void readsTheSubjectCodeOfCiiFromItsOwnElement() {
        SemanticDocument document = document(Documents.cii(
                "<rsm:ExchangedDocument><ram:IncludedNote><ram:Content>Delivery"
                        + "</ram:Content><ram:SubjectCode>AAC</ram:SubjectCode>"
                        + "</ram:IncludedNote></rsm:ExchangedDocument>"));
        assertEquals("AAC", content(document, "/BG-1/0/BT-21"));
        assertEquals("Delivery", content(document, "/BG-1/0/BT-22"));
    }

    @Test
    void readsACreditNote() {
        SemanticDocument document = document(Documents.creditNote(
                "<cbc:ID>CN-1</cbc:ID><cbc:IssueDate>2026-09-19</cbc:IssueDate>"));
        assertEquals("CN-1", content(document, "/BT-1"));
        assertEquals(Optional.of("UBL"), document.source().orElseThrow().syntax());
    }

    @Test
    void placesATermOutsideTheElementOfItsGroup() {
        SemanticDocument document = document(Documents.ciiSettlement(
                "<ram:SpecifiedTradeSettlementPaymentMeans><ram:TypeCode>58</ram:TypeCode>"
                        + "<ram:PayeePartyCreditorFinancialAccount><ram:IBANID>DE02"
                        + "</ram:IBANID></ram:PayeePartyCreditorFinancialAccount>"
                        + "<ram:PayeeSpecifiedCreditorFinancialInstitution><ram:BICID>"
                        + "PBNKDEFF</ram:BICID>"
                        + "</ram:PayeeSpecifiedCreditorFinancialInstitution>"
                        + "</ram:SpecifiedTradeSettlementPaymentMeans>"));
        assertEquals("DE02", content(document, "/BG-16/BG-17/0/BT-84"));
        assertEquals("PBNKDEFF", content(document, "/BG-16/BG-17/0/BT-86"),
                "the bank of a credit transfer stands beside the account, not inside it");
    }

    /**
     * The sub invoice line of the XRechnung extension nests inside itself, and the table
     * binds it over the descendant axis, so the reader reaches the inner one as it reaches
     * the outer one. It writes no value there, because the table binds the groups of the
     * extension and not the core terms that are reused inside them; that gap is the
     * largest difference between the two readers and {@code conformance/readers.md}
     * records it. What is checked here is the axis: the inner element is matched, which is
     * what the group instance below shows, and nothing below an unmatched element could
     * be.
     */
    @Test
    void reachesASubInvoiceLineInsideASubInvoiceLine() {
        MatchTrie.Node invoice = MatchTrie.of(BindingSyntax.UBL_INVOICE).root()
                .childrenNamed(Name.key(UBL_INVOICE_NAMESPACE, "Invoice")).get(0);
        MatchTrie.Node line = invoice
                .childrenNamed(Name.key(UBL_AGGREGATE_NAMESPACE, "InvoiceLine")).get(0);
        List<MatchTrie.Node> reachable = line.reachable();
        assertEquals(1, reachable.size(), "one element is reached over the descendant axis");
        assertEquals(Name.key(UBL_AGGREGATE_NAMESPACE, "SubInvoiceLine"),
                reachable.get(0).key());
        assertTrue(reachable.get(0).groups().contains("BG-DEX-01"));
        SemanticDocument document = document(Documents.ubl(
                "<cac:InvoiceLine><cbc:ID>1</cbc:ID>"
                        + "<cac:SubInvoiceLine><cac:SubInvoiceLine/></cac:SubInvoiceLine>"
                        + "</cac:InvoiceLine>"));
        assertEquals("1", content(document, "/BG-25/0/BT-126"));
    }

    @Test
    void takesOneOfTwoSpellingsOfOneValue() {
        SemanticDocument document = document(Documents.ciiTransaction(
                "<ram:IncludedSupplyChainTradeLineItem><ram:SpecifiedLineTradeAgreement>"
                        + "<ram:GrossPriceProductTradePrice><ram:BasisQuantity unitCode=\"C62\">"
                        + "1</ram:BasisQuantity></ram:GrossPriceProductTradePrice>"
                        + "<ram:NetPriceProductTradePrice><ram:BasisQuantity unitCode=\"C62\">"
                        + "1</ram:BasisQuantity></ram:NetPriceProductTradePrice>"
                        + "</ram:SpecifiedLineTradeAgreement>"
                        + "</ram:IncludedSupplyChainTradeLineItem>"));
        assertEquals("1", content(document, "/BG-25/0/BG-29/BT-149"));
        assertTrue(document.values().keySet().stream()
                        .noneMatch(path -> path.toString().endsWith("BT-149/1")),
                "two spellings of one value are one value");
    }

    @Test
    void reportsAnElementThatSaysNothing() {
        ImportResult result = reader.read(Documents.ubl("<cbc:ID></cbc:ID>"));
        assertEquals(List.of(ImportNote.Kind.EMPTY),
                result.report().notes().stream().map(ImportNote::kind).toList());
        assertTrue(result.document().values().isEmpty());
    }

    @Test
    void reportsAValueThatSpellsNoValueOfItsType() {
        ImportResult result = reader.read(Documents.ubl(
                "<cac:LegalMonetaryTotal><cbc:PayableAmount currencyID=\"EUR\">ten"
                        + "</cbc:PayableAmount></cac:LegalMonetaryTotal>"));
        assertEquals(List.of(ImportNote.Kind.MALFORMED),
                result.report().notes().stream().map(ImportNote::kind).toList());
    }

    @Test
    void endsTheReadOnSuchAValueWhenAskedToBeStrict() {
        StreamingReader strict = new StreamingReader(ReaderOptions.builder()
                .mode(ReaderMode.STRICT).build());
        byte[] document = Documents.ubl(
                "<cac:LegalMonetaryTotal><cbc:PayableAmount currencyID=\"EUR\">ten"
                        + "</cbc:PayableAmount></cac:LegalMonetaryTotal>");
        BindingFormatException thrown =
                assertThrows(BindingFormatException.class, () -> strict.read(document));
        assertTrue(thrown.getMessage().contains("/BG-22/BT-115"),
                "the message names the path: " + thrown.getMessage());
    }

    @Test
    void turnsAnExtensionTermIntoANoteWithoutTheExtensionRegistry() {
        StreamingReader core = new StreamingReader(ReaderOptions.builder()
                .registry(Registry.en16931()).build());
        ImportResult result = core.read(Documents.ubl(
                "<cac:InvoiceLine><cbc:ID>1</cbc:ID><cac:SubInvoiceLine>"
                        + "<cac:Item><cbc:Description>x</cbc:Description></cac:Item>"
                        + "</cac:SubInvoiceLine></cac:InvoiceLine>"));
        assertEquals("1", content(result.document(), "/BG-25/0/BT-126"));
        assertTrue(result.report().notes().stream()
                        .anyMatch(note -> note.kind() == ImportNote.Kind.UNKNOWN_TERM),
                "a term no loaded registry knows is a note");
    }

    @Test
    void readsTheSameDocumentTheSameWayEveryTime() {
        byte[] source = Documents.ubl(
                "<cbc:ID>RE-4711</cbc:ID><cbc:Note>#AAC#one</cbc:Note>"
                        + "<cbc:Note>two</cbc:Note>"
                        + "<cac:InvoiceLine><cbc:ID>1</cbc:ID></cac:InvoiceLine>"
                        + "<cac:InvoiceLine><cbc:ID>2</cbc:ID></cac:InvoiceLine>");
        SemanticDocument first = document(source);
        for (int run = 0; run < 5; run++) {
            assertEquals(first, document(source));
        }
    }

    @Test
    void refusesARootElementItDoesNotKnow() {
        BindingSyntaxException thrown = assertThrows(BindingSyntaxException.class,
                () -> reader.read(Documents.utf8("<order xmlns=\"urn:x\"/>")));
        assertEquals("order", thrown.localName());
        assertEquals("urn:x", thrown.namespace());
    }

    @Test
    void refusesADocumentTypeDeclaration() {
        assertThrows(BindingFormatException.class, () -> reader.read(Documents.utf8(
                "<!DOCTYPE Invoice><Invoice xmlns=\"urn:oasis:names:specification:ubl:"
                        + "schema:xsd:Invoice-2\"/>")));
    }

    @Test
    void refusesAnExternalEntity() {
        assertThrows(BindingFormatException.class, () -> reader.read(Documents.utf8(
                "<!DOCTYPE Invoice [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                        + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:"
                        + "Invoice-2\"><cbc:ID xmlns:cbc=\"urn:oasis:names:specification:"
                        + "ubl:schema:xsd:CommonBasicComponents-2\">&x;</cbc:ID></Invoice>")));
    }

    @Test
    void refusesAnInputLargerThanItReads() {
        StreamingReader small = new StreamingReader(ReaderOptions.builder()
                .maxInputBytes(64).build());
        assertThrows(BindingLimitException.class,
                () -> small.read(Documents.ubl("<cbc:ID>RE-4711</cbc:ID>")));
    }

    @Test
    void refusesElementsNestedDeeperThanItWalks() {
        StreamingReader shallow = new StreamingReader(ReaderOptions.builder()
                .maxElementDepth(3).build());
        assertThrows(BindingLimitException.class, () -> shallow.read(Documents.ubl(
                "<cac:InvoiceLine><cac:Item><cac:ClassifiedTaxCategory><cbc:ID>S</cbc:ID>"
                        + "</cac:ClassifiedTaxCategory></cac:Item></cac:InvoiceLine>")));
    }

    @Test
    void refusesAnElementLargerThanItHolds() {
        StreamingReader small = new StreamingReader(ReaderOptions.builder()
                .maxBufferedBytes(64).build());
        assertThrows(BindingLimitException.class, () -> small.read(Documents.ubl(
                "<cac:AllowanceCharge><cbc:ChargeIndicator>false</cbc:ChargeIndicator>"
                        + "<cbc:AllowanceChargeReason>" + "x".repeat(200)
                        + "</cbc:AllowanceChargeReason></cac:AllowanceCharge>")));
    }

    /**
     * The characters are counted over the held element and everything below it, not over
     * one element at a time: a document reference with a great many small children costs
     * the same heap as one with a great deal of text, and a bound that counted per element
     * would let it through.
     */
    @Test
    void refusesASubtreeLargerThanItHolds() {
        StreamingReader small = new StreamingReader(ReaderOptions.builder()
                .maxBufferedBytes(64).build());
        assertThrows(BindingLimitException.class, () -> small.read(Documents.ubl(
                "<cac:AllowanceCharge><cbc:ChargeIndicator>false</cbc:ChargeIndicator>"
                        + "<cbc:AllowanceChargeReason>x</cbc:AllowanceChargeReason>"
                                .repeat(100)
                        + "</cac:AllowanceCharge>")));
    }

    /**
     * And the elements are counted as well, because empty ones cost heap and no
     * characters at all. The message names this bound rather than the other, so that a
     * caller who reads it knows whether the content or the structure was too large.
     */
    @Test
    void refusesASubtreeWithMoreElementsThanItHolds() {
        StreamingReader small = new StreamingReader(ReaderOptions.builder()
                .maxBufferedElements(16).build());
        BindingLimitException refused = assertThrows(BindingLimitException.class,
                () -> small.read(Documents.ubl(
                        "<cac:AllowanceCharge><cbc:ChargeIndicator>false"
                                + "</cbc:ChargeIndicator>" + "<q/>".repeat(100)
                                + "</cac:AllowanceCharge>")));
        assertTrue(refused.getMessage().contains("16 elements it holds"),
                refused.getMessage());
    }

    /**
     * The embedded attachment sits inside a document reference, which is an element the
     * reader holds whole, so the buffer has to leave room for a value of the size the
     * limits admit. A binary value of a megabyte and a half is ordinary and is read.
     */
    @Test
    void readsAnAttachmentInsideAnElementItHoldsWhole() {
        String base64 = "QUJD".repeat(400_000);
        ImportResult result = new StreamingReader().read(Documents.ubl(
                "<cac:AdditionalDocumentReference><cbc:ID>ATT-1</cbc:ID>"
                        + "<cbc:DocumentTypeCode>916</cbc:DocumentTypeCode>"
                        + "<cac:Attachment><cbc:EmbeddedDocumentBinaryObject"
                        + " mimeCode=\"application/pdf\" filename=\"big.pdf\">" + base64
                        + "</cbc:EmbeddedDocumentBinaryObject></cac:Attachment>"
                        + "</cac:AdditionalDocumentReference>"));
        assertEquals(base64.length(),
                content(result.document(), "/BG-24/0/BT-125").length());
    }

    /**
     * A value longer than a value may be is reported as that one term and does not refuse
     * the document — and the reader stops collecting where the bound is, so that deciding
     * it costs the bound rather than the document. Both paths do it: the streamed one
     * here, the held one below.
     */
    @Test
    void reportsAValueTooLongInsteadOfBuildingIt() {
        StreamingReader bounded = new StreamingReader(ReaderOptions.builder()
                .limits(Limits.builder().maxStringBytes(64).build()).build());
        ImportResult result = bounded.read(Documents.ubl(
                "<cbc:Note>" + "x".repeat(4096) + "</cbc:Note>"));
        assertTrue(result.document().value(SemanticPath.of("/BG-1/0/BT-22")).isEmpty());
        assertTrue(result.report().notes().stream()
                .anyMatch(note -> note.kind() == ImportNote.Kind.LIMIT_REACHED
                        && note.message().contains("without being read whole")),
                result.report().notes().toString());
    }

    @Test
    void reportsAValueTooLongInsideAnElementItHoldsWhole() {
        StreamingReader bounded = new StreamingReader(ReaderOptions.builder()
                .limits(Limits.builder().maxStringBytes(64).maxBinaryValueBytes(64).build())
                .build());
        ImportResult result = bounded.read(Documents.ubl(
                "<cac:AdditionalDocumentReference><cbc:ID>ATT-1</cbc:ID>"
                        + "<cbc:DocumentTypeCode>916</cbc:DocumentTypeCode>"
                        + "<cac:Attachment><cbc:EmbeddedDocumentBinaryObject"
                        + " mimeCode=\"application/pdf\" filename=\"big.pdf\">"
                        + "QUJD".repeat(200)
                        + "</cbc:EmbeddedDocumentBinaryObject></cac:Attachment>"
                        + "</cac:AdditionalDocumentReference>"));
        assertEquals("ATT-1", content(result.document(), "/BG-24/0/BT-122"),
                "the rest of the document reference is read");
        assertTrue(result.report().notes().stream()
                .anyMatch(note -> note.kind() == ImportNote.Kind.LIMIT_REACHED
                        && note.message().contains("BT-125")),
                result.report().notes().toString());
    }

    /**
     * The bound that refuses a value refuses that value and no other. An element can carry
     * one term in its content and another on an attribute — a base quantity states its
     * number and its unit of measure that way — and the attribute arrived with the start
     * tag, bounded by the parser. Losing it along with the content would be a value gone
     * with neither a value nor a note.
     */
    @Test
    void keepsTheTermOnAnAttributeOfAnElementWhoseContentIsTooLong() {
        StreamingReader bounded = new StreamingReader(ReaderOptions.builder()
                .limits(Limits.builder().maxStringBytes(64).build()).build());
        ImportResult result = bounded.read(Documents.ubl(
                "<cac:InvoiceLine><cbc:ID>1</cbc:ID><cac:Price>"
                        + "<cbc:BaseQuantity unitCode=\"H87\">" + "1".repeat(4096)
                        + "</cbc:BaseQuantity></cac:Price></cac:InvoiceLine>"));
        assertTrue(result.document().value(SemanticPath.of("/BG-25/0/BG-29/BT-149")).isEmpty(),
                "the content passed the bound");
        assertEquals("H87", content(result.document(), "/BG-25/0/BG-29/BT-150"),
                "the unit of measure did not");
        assertTrue(result.report().notes().stream()
                .anyMatch(note -> note.kind() == ImportNote.Kind.LIMIT_REACHED
                        && note.message().contains("BT-149")),
                result.report().notes().toString());
    }

    /**
     * The cross industry invoice writes four different references into one element and
     * tells them apart by its type code. Only the type 916 one is a supporting document,
     * so only that one opens BG-24 — and the occurrence index of the supporting document
     * does not move because a tender reference stands before it in the file. The index is
     * part of the semantic path, so a group opened for the wrong element is a wrong
     * document rather than a spare one.
     */
    @Test
    void opensTheSupportingDocumentGroupOnlyForTheReferenceThatIsOne() {
        SemanticDocument document = new StreamingReader().read(Documents.ciiAgreement(
                "<ram:AdditionalReferencedDocument>"
                        + "<ram:IssuerAssignedID>TENDER-7</ram:IssuerAssignedID>"
                        + "<ram:TypeCode>50</ram:TypeCode>"
                        + "</ram:AdditionalReferencedDocument>"
                        + "<ram:AdditionalReferencedDocument>"
                        + "<ram:IssuerAssignedID>DOC-1</ram:IssuerAssignedID>"
                        + "<ram:TypeCode>916</ram:TypeCode>"
                        + "<ram:Name>Timesheet</ram:Name>"
                        + "</ram:AdditionalReferencedDocument>")).document();
        assertEquals("TENDER-7", content(document, "/BT-17"));
        assertEquals("DOC-1", content(document, "/BG-24/0/BT-122"));
        assertEquals("Timesheet", content(document, "/BG-24/0/BT-123"));
        assertTrue(document.value(SemanticPath.of("/BG-24/1/BT-122")).isEmpty(),
                "the tender reference is not a supporting document");
    }

    @Test
    void holdsNothingWhereNoPredicateStands() {
        StreamingReader small = new StreamingReader(ReaderOptions.builder()
                .maxBufferedBytes(64).build());
        SemanticDocument document = small.read(Documents.ubl(
                "<cbc:Note>" + "x".repeat(4096) + "</cbc:Note>")).document();
        assertEquals(4096, content(document, "/BG-1/0/BT-22").length(),
                "a long value is not a held element");
    }

    @Test
    void stopsWhereTheDocumentReachesItsBound() {
        StreamingReader bounded = new StreamingReader(ReaderOptions.builder()
                .limits(Limits.builder().maxValues(2).build()).build());
        ImportResult result = bounded.read(Documents.ubl(
                "<cac:InvoiceLine><cbc:ID>1</cbc:ID></cac:InvoiceLine>"
                        + "<cac:InvoiceLine><cbc:ID>2</cbc:ID></cac:InvoiceLine>"
                        + "<cac:InvoiceLine><cbc:ID>3</cbc:ID></cac:InvoiceLine>"));
        assertEquals(2, result.document().values().size());
        assertTrue(result.report().notes().stream()
                .anyMatch(note -> note.kind() == ImportNote.Kind.LIMIT_REACHED));
    }

    @Test
    void leavesOutAPathLongerThanItWrites() {
        StreamingReader bounded = new StreamingReader(ReaderOptions.builder()
                .limits(Limits.builder().maxPathSegments(3).build()).build());
        ImportResult result = bounded.read(Documents.ubl(
                "<cbc:ID>RE-4711</cbc:ID>"
                        + "<cac:InvoiceLine><cbc:ID>1</cbc:ID><cac:Item><cbc:Name>x"
                        + "</cbc:Name></cac:Item></cac:InvoiceLine>"));
        assertEquals("RE-4711", content(result.document(), "/BT-1"));
        assertTrue(result.report().notes().stream()
                .anyMatch(note -> note.kind() == ImportNote.Kind.PATH_TOO_LONG));
    }

    @Test
    void readsFromAStreamAsItReadsFromBytes() {
        byte[] source = Documents.ubl("<cbc:ID>RE-4711</cbc:ID>");
        assertEquals(document(source),
                reader.read(new java.io.ByteArrayInputStream(source)).document());
    }

    @Test
    void digestsTheSourceItWasGiven() {
        byte[] source = Documents.ubl("<cbc:ID>RE-4711</cbc:ID>");
        SemanticDocument.Source origin = document(source).source().orElseThrow();
        assertEquals(Optional.of(sha256(source)), origin.sha256());
    }

    @Test
    void sharesItsOptions() {
        assertEquals(ReaderMode.REPAIR, reader.options().mode());
        assertFalse(ReaderOptions.defaults().toString().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> ReaderOptions.builder().maxInputBytes(0));
    }

    private SemanticDocument document(byte[] source) {
        return reader.read(source).document();
    }

    private static String content(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path)).orElseThrow(
                () -> new AssertionError("no value at " + path + " in " + document.values()))
                .canonicalContent();
    }

    private static String sha256(byte[] source) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(source));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
