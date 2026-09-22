package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The CII writer on documents written for one question each, so that a failure names one
 * thing.
 *
 * <p>The corpus measures whether the writer produces documents the official artefacts
 * accept; these tests say what it does and why, one rule of the binding at a time.
 */
class CiiWriterTest {

    @Test
    void writesTheSectionsTheSchemaRequiresEvenWhenNothingFallsIntoThem() {
        String xml = write(SemanticDocument.builder().put("/BT-1", "RE-1").build());
        assertContains(xml, "<rsm:ExchangedDocumentContext/>");
        assertContains(xml, "<ram:ApplicableHeaderTradeAgreement/>");
        assertContains(xml, "<ram:ApplicableHeaderTradeDelivery/>");
        assertContains(xml, "<ram:ApplicableHeaderTradeSettlement/>");
        assertContains(xml, "<ram:ID>RE-1</ram:ID>");
    }

    @Test
    void declaresEveryNamespaceOfTheSyntaxOnTheDocumentElement() {
        String xml = write(SemanticDocument.builder().put("/BT-1", "RE-1").build());
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<rsm:CrossIndustryInvoice "), xml.substring(0, 120));
        for (String prefix : List.of("qdt", "ram", "rsm", "udt")) {
            assertContains(xml, "xmlns:" + prefix + "=\"urn:un:unece:uncefact:data:standard:");
        }
    }

    /** A date is written in the eight digit form the format qualifier of the element says. */
    @Test
    void writesADateInTheFormTheQualifierNames() {
        String xml = write(SemanticDocument.builder().put("/BT-2", "2026-09-19").build());
        assertContains(xml, "<ram:IssueDateTime>");
        assertContains(xml, "<udt:DateTimeString format=\"102\">20260919</udt:DateTimeString>");
    }

    /** An element stands where the schema declares it, not where the value arrived. */
    @Test
    void putsSiblingsInTheOrderTheSchemaDeclaresThem() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-22/BT-115", "119.00")
                .put("/BG-22/BT-106", "100.00")
                .put("/BG-22/BT-112", "119.00")
                .build());
        assertOrder(xml, "<ram:LineTotalAmount>", "<ram:GrandTotalAmount>",
                "<ram:DuePayableAmount>");
    }

    /**
     * The two invoice totals that differ only in the currency they are stated in are two
     * elements of one name, told apart by an attribute whose value is an element elsewhere
     * in the document.
     */
    @Test
    void copiesACurrencyCodeIntoTheAttributeThatNamesIt() {
        String xml = write(SemanticDocument.builder()
                .put("/BT-5", "EUR")
                .put("/BT-6", "GBP")
                .put("/BG-22/BT-110", "19.00")
                .put("/BG-22/BT-111", "16.50")
                .build());
        assertContains(xml, "<ram:TaxTotalAmount currencyID=\"EUR\">19.00</ram:TaxTotalAmount>");
        assertContains(xml, "<ram:TaxTotalAmount currencyID=\"GBP\">16.50</ram:TaxTotalAmount>");
    }

    /** An identifier with a scheme goes to the element that has somewhere to put it. */
    @Test
    void choosesTheSpellingThatHasRoomForTheScheme() {
        String plain = write(SemanticDocument.builder()
                .put("/BG-4/BT-29/0", "123456").build());
        assertContains(plain, "<ram:ID>123456</ram:ID>");
        assertFalse(plain.contains("GlobalID"), plain);

        String scheme = write(SemanticDocument.builder()
                .put("/BG-4/BT-29/0", SemanticValue.identifier("123456", "0088")).build());
        assertContains(scheme, "<ram:GlobalID schemeID=\"0088\">123456</ram:GlobalID>");
    }

    /** Two occurrences of one term are two elements, not one element written over. */
    @Test
    void writesEachOccurrenceOfATermAsAnElementOfItsOwn() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-4/BT-29/0", "first")
                .put("/BG-4/BT-29/1", "second")
                .build());
        assertContains(xml, "<ram:ID>first</ram:ID>");
        assertContains(xml, "<ram:ID>second</ram:ID>");
    }

    /**
     * The seller VAT identifier and the seller tax registration identifier are one element
     * each in a tax registration of their own, because the schema gives a tax registration
     * one identifier.
     */
    @Test
    void repeatsTheElementAboveWhereTheOneBelowMayNotRepeat() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-4/BT-31", "DE123456789")
                .put("/BG-4/BT-32", "123/456/789")
                .build());
        assertContains(xml, "<ram:SpecifiedTaxRegistration>\n"
                + "          <ram:ID schemeID=\"VA\">DE123456789</ram:ID>\n"
                + "        </ram:SpecifiedTaxRegistration>");
        assertContains(xml, "<ram:SpecifiedTaxRegistration>\n"
                + "          <ram:ID schemeID=\"FC\">123/456/789</ram:ID>\n"
                + "        </ram:SpecifiedTaxRegistration>");
    }

    /** An allowance and a charge are the same element, told apart by an indicator. */
    @Test
    void saysOfEveryAllowanceOrChargeWhichOfTheTwoItIs() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-20/0/BT-92", "10.00")
                .put("/BG-21/0/BT-99", "5.00")
                .build());
        assertContains(xml, "<ram:ChargeIndicator>\n"
                + "          <udt:Indicator>false</udt:Indicator>\n"
                + "        </ram:ChargeIndicator>\n"
                + "        <ram:ActualAmount>10.00</ram:ActualAmount>");
        assertContains(xml, "<ram:ChargeIndicator>\n"
                + "          <udt:Indicator>true</udt:Indicator>\n"
                + "        </ram:ChargeIndicator>\n"
                + "        <ram:ActualAmount>5.00</ram:ActualAmount>");
    }

    /**
     * The item price discount is bound to an allowance or charge element without a group,
     * so nothing in the table says which of the two it is. It is a discount, so the writer
     * says it is an allowance.
     */
    @Test
    void saysThatAnItemPriceDiscountIsAnAllowance() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-25/0/BG-29/BT-147", "2.00").build());
        assertContains(xml, "<ram:AppliedTradeAllowanceCharge>\n"
                + "            <ram:ChargeIndicator>\n"
                + "              <udt:Indicator>false</udt:Indicator>\n"
                + "            </ram:ChargeIndicator>\n"
                + "            <ram:ActualAmount>2.00</ram:ActualAmount>");
    }

    /** Each instance of a repeating group is an element of its own, in its own order. */
    @Test
    void writesOneElementPerGroupInstance() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/1/BT-126", "2")
                .put("/BG-25/2/BT-126", "3")
                .build());
        assertOrder(xml, "<ram:LineID>1</ram:LineID>", "<ram:LineID>2</ram:LineID>",
                "<ram:LineID>3</ram:LineID>");
        assertEquals(3, count(xml, "<ram:IncludedSupplyChainTradeLineItem>"));
    }

    /**
     * The model gives an invoice one payment instruction with many credit transfers; the
     * syntax gives it many payment means with one account each. The index of the credit
     * transfer therefore lands on the payment means, and what the payment instruction says
     * is written into every one of them, because the schema asks each of them for it.
     */
    @Test
    void writesWhatTheGroupAboveSaysIntoEveryInstanceOfASharedElement() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-16/BT-81", "58")
                .put("/BG-16/BG-17/0/BT-84", "DE02120300000000202051")
                .put("/BG-16/BG-17/1/BT-84", "DE02500105170137075030")
                .build());
        assertEquals(2, count(xml, "<ram:SpecifiedTradeSettlementPaymentMeans>"));
        assertEquals(2, count(xml, "<ram:TypeCode>58</ram:TypeCode>"));
        assertContains(xml, "<ram:IBANID>DE02120300000000202051</ram:IBANID>");
        assertContains(xml, "<ram:IBANID>DE02500105170137075030</ram:IBANID>");
    }

    /** A supplementary component the syntax writes beside the value, not on it. */
    @Test
    void writesASchemeOnTheSiblingElementThatCarriesIt() {
        String xml = write(SemanticDocument.builder()
                .put("/BT-18", SemanticValue.identifier("OK-1", "ABZ")).build());
        assertContains(xml, "<ram:IssuerAssignedID>OK-1</ram:IssuerAssignedID>");
        assertContains(xml, "<ram:TypeCode>130</ram:TypeCode>");
        assertContains(xml, "<ram:ReferenceTypeCode>ABZ</ram:ReferenceTypeCode>");
    }

    /** An attachment carries both of its components on the element of the value. */
    @Test
    void writesTheComponentsOfABinaryObjectAsAttributes() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-24/0/BT-125", SemanticValue.binary(new byte[] {1, 2, 3},
                        "application/pdf", "note.pdf"))
                .build());
        assertContains(xml, "<ram:AttachmentBinaryObject mimeCode=\"application/pdf\""
                + " filename=\"note.pdf\">AQID</ram:AttachmentBinaryObject>");
    }

    /** A quantity carries its unit on an attribute, which is a term of its own. */
    @Test
    void writesAUnitOfMeasureOnTheAttributeThatCarriesIt() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-25/0/BT-129", "3")
                .put("/BG-25/0/BT-130", "C62")
                .build());
        assertContains(xml, "<ram:BilledQuantity unitCode=\"C62\">3</ram:BilledQuantity>");
    }

    /** Two documents the syntax cannot hold, each named in the report rather than dropped. */
    @Test
    void reportsWhatTheSyntaxHasNoPlaceFor() {
        WriteResult result = CiiWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-3/0/BT-25", "RE-1")
                .put("/BG-3/1/BT-25", "RE-2")
                .build(), WriterOptions.defaults());
        assertEquals(1, result.report().dropped());
        assertEquals(1, result.report().written());
        assertFalse(result.report().isComplete());
        List<WriteNote> notes = result.report().notes(WriteNote.Kind.GROUP_NOT_REPEATABLE);
        assertEquals(1, notes.size());
        assertEquals("/BG-3/1/BT-25", notes.get(0).path());
        assertContains(new String(result.xml(), StandardCharsets.UTF_8),
                "<ram:IssuerAssignedID>RE-1</ram:IssuerAssignedID>");
    }

    @Test
    void reportsATermTheSyntaxBindingDoesNotBind() {
        WriteResult result = CiiWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-DEX-09/0/BT-DEX-001", "MobilesBezahlen")
                .build(), WriterOptions.defaults());
        List<WriteNote> notes = result.report().notes(WriteNote.Kind.TERM_NOT_BOUND);
        assertEquals(1, notes.size());
        assertEquals("/BG-DEX-09/0/BT-DEX-001", notes.get(0).path());
        assertContains(notes.get(0).message(), "no place in this syntax");
    }

    /**
     * A document of an edition the table was not written against is refused whole, and the
     * refusal names both editions.
     *
     * <p>The document below is of the default edition in every respect but its
     * {@code semanticModel}, so every one of its paths is one the table binds. It is
     * refused all the same: the declared edition is what decides, because a caller that
     * received a conversion of one such document and a refusal for the next has been told
     * nothing it can act on.
     */
    @Test
    void refusesADocumentOfAnEditionTheTableDoesNotBind() {
        SemanticDocument document = SemanticDocument.builder()
                .semanticModel("EN16931-1:2026")
                .put("/BT-1", "RE-1")
                .build();

        BindingEditionException refused = assertThrows(BindingEditionException.class,
                () -> CiiWriter.write(document));

        assertContains(refused.getMessage(), "EN16931-1:2026");
        assertContains(refused.getMessage(),
                BindingTable.of(BindingSyntax.CII).semanticModel());
        assertContains(refused.getMessage(), "drops");
    }

    @Test
    void reportsATermNoRegistryOfTheTableKnows() {
        WriteResult result = CiiWriter.writeWithReport(SemanticDocument.builder()
                .put("/BT-XYZ-1", "something").build(), WriterOptions.defaults());
        List<WriteNote> notes = result.report().notes(WriteNote.Kind.TERM_UNKNOWN);
        assertEquals(1, notes.size());
        assertEquals("/BT-XYZ-1", notes.get(0).path());
        assertEquals(1, result.report().dropped());
    }

    /**
     * {@code examples/b2c-gross.esj.json} carries the four terms of {@code model/b2c/0.1.json}.
     * No binding table of this release covers that registry, so the writer names every one of
     * the ten paths and writes the core invoice around them.
     */
    @Test
    void reportsEveryTermOfTheB2cExampleTheBindingTablesDoNotCover() {
        SemanticDocument document = EsjReader.strict().read(Examples.bytes("b2c-gross"));

        WriteResult result = CiiWriter.writeWithReport(document, WriterOptions.defaults());

        assertEquals(List.of("/BT-B2C-010", "/BG-25/0/BT-B2C-001", "/BG-25/0/BT-B2C-002",
                        "/BG-25/0/BT-B2C-003", "/BG-25/1/BT-B2C-001", "/BG-25/1/BT-B2C-002",
                        "/BG-25/1/BT-B2C-003", "/BG-25/2/BT-B2C-001", "/BG-25/2/BT-B2C-002",
                        "/BG-25/2/BT-B2C-003"),
                result.report().notes(WriteNote.Kind.TERM_UNKNOWN).stream()
                        .map(WriteNote::path).toList());
        assertEquals(10, result.report().dropped());
        assertContains(new String(result.xml(), StandardCharsets.UTF_8),
                "<ram:LineTotalAmount>84.03</ram:LineTotalAmount>");
    }

    @Test
    void reportsAComponentTheSyntaxHasNoPlaceFor() {
        WriteResult result = CiiWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-4/BT-27", SemanticValue.identifier("Example GmbH", "0088"))
                .build(), WriterOptions.defaults());
        List<WriteNote> notes = result.report().notes(WriteNote.Kind.COMPONENT_DROPPED);
        assertEquals(1, notes.size());
        assertContains(notes.get(0).message(), "no place for the scheme of BT-27");
        assertEquals(1, result.report().written());
        assertContains(new String(result.xml(), StandardCharsets.UTF_8),
                "<ram:Name>Example GmbH</ram:Name>");
    }

    @Test
    void reportsDataThatNoBusinessTermNames() {
        WriteResult result = CiiWriter.writeWithReport(SemanticDocument.builder()
                .put("/BT-1", "RE-1")
                .extension("example.esj-test", ExtensionValue.of("a token"))
                .build(), WriterOptions.defaults());
        assertEquals(1, result.report().notes(WriteNote.Kind.EXTENSIONS_DROPPED).size());
        assertEquals(0, result.report().dropped());
    }

    /**
     * The semantic model admits characters XML 1.0 does not, and the writer decides that
     * once: the character is left out, the rest of the value is written and the report
     * names the term and the code point. A control character is a value an ESJ reader
     * accepts (specification, section 12.6) and no XML document can carry, escaped or not.
     */
    @Test
    void leavesOutACharacterNoXmlDocumentCanCarryAndNamesIt() {
        WriteResult result = CiiWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-4/BT-27", "xy")
                .put("/BT-1", "RE-￾1")
                .build(), WriterOptions.defaults());
        List<WriteNote> notes =
                result.report().notes(WriteNote.Kind.CHARACTER_NOT_REPRESENTABLE);
        assertEquals(2, notes.size(), notes.toString());
        assertEquals("/BT-1", notes.get(0).path());
        assertContains(notes.get(0).message(), "U+FFFE");
        assertEquals("/BG-4/BT-27", notes.get(1).path());
        assertContains(notes.get(1).message(), "U+0007");
        String xml = new String(result.xml(), StandardCharsets.UTF_8);
        assertContains(xml, "<ram:Name>xy</ram:Name>");
        assertContains(xml, "<ram:ID>RE-1</ram:ID>");
        assertEquals(2, result.report().written(), "both values reached the syntax");
        assertEquals(0, result.report().dropped());
        for (byte b : result.xml()) {
            assertTrue(b != 0x07, "no control character stands in the output");
        }
    }

    /**
     * A surrogate that is not half of a pair is no scalar value, so the same rule takes
     * it: nothing between {@code SemanticValue.of} and the writer refuses it, and the
     * bytes that come out have to be UTF-8 all the same.
     */
    @Test
    void leavesOutASurrogateThatIsNotHalfOfAPair() {
        WriteResult result = CiiWriter.writeWithReport(SemanticDocument.builder()
                .put("/BT-1", "RE-\uD800-X")
                .build(), WriterOptions.defaults());
        List<WriteNote> notes =
                result.report().notes(WriteNote.Kind.CHARACTER_NOT_REPRESENTABLE);
        assertEquals(1, notes.size(), notes.toString());
        assertContains(notes.get(0).message(), "U+D800");
        String xml = new String(result.xml(), StandardCharsets.UTF_8);
        assertContains(xml, "<ram:ID>RE--X</ram:ID>");
        assertFalse(xml.contains("�"), "nothing was substituted for it");
        assertArrayEquals(result.xml(), xml.getBytes(StandardCharsets.UTF_8),
                "the bytes are UTF-8 and decode back to themselves");
    }

    /** A well-formed pair is a character like any other and is written as it stands. */
    @Test
    void writesASurrogatePairAsTheCharacterItSpells() {
        SemanticDocument document =
                SemanticDocument.builder().put("/BT-1", "RE-📄").build();
        assertContains(write(document), "<ram:ID>RE-📄</ram:ID>");
        assertEquals(0, CiiWriter.writeWithReport(document, WriterOptions.defaults())
                .report().notes(WriteNote.Kind.CHARACTER_NOT_REPRESENTABLE).size());
    }

    /** A supplementary component is a value too, and the note says which one it was. */
    @Test
    void leavesOutSuchACharacterInASupplementaryComponentAsWell() {
        WriteResult result = CiiWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-24/0/BT-125", SemanticValue.binary(new byte[] {1, 2, 3},
                        "application/pdf", "note.pdf"))
                .build(), WriterOptions.defaults());
        List<WriteNote> notes =
                result.report().notes(WriteNote.Kind.CHARACTER_NOT_REPRESENTABLE);
        assertEquals(1, notes.size(), notes.toString());
        assertContains(notes.get(0).message(), "the filename of the value carries U+000B");
        assertContains(new String(result.xml(), StandardCharsets.UTF_8),
                "filename=\"note.pdf\"");
    }

    /** Tab, line feed and carriage return are characters XML has, and they survive. */
    @Test
    void keepsTheThreeControlCharactersXmlDoesHave() {
        WriteResult result = CiiWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-1/0/BT-22", "one\ttwo\nthree\r")
                .build(), WriterOptions.defaults());
        assertEquals(0,
                result.report().notes(WriteNote.Kind.CHARACTER_NOT_REPRESENTABLE).size());
        assertContains(new String(result.xml(), StandardCharsets.UTF_8),
                "one\ttwo\nthree");
    }

    @Test
    void writesTheSameBytesEveryTime() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", "RE-1")
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/1/BT-126", "2")
                .put("/BG-23/0/BT-116", "100.00")
                .put("/BG-23/1/BT-116", "200.00")
                .build();
        assertArrayEquals(CiiWriter.write(document), CiiWriter.write(document));
    }

    @Test
    void writesOneLineWhereTheOptionsAskForIt() {
        SemanticDocument document = SemanticDocument.builder().put("/BT-1", "RE-1").build();
        String xml = new String(CiiWriter.write(document,
                WriterOptions.builder().indent(false).build()), StandardCharsets.UTF_8);
        assertEquals(1, xml.lines().count(), xml);
        assertContains(xml, "<ram:ID>RE-1</ram:ID>");
    }

    /**
     * Where the syntax offers two elements for one term and nothing in the semantic
     * document says which, the writer has a rule and the rule is written down. BT-84 is
     * the one term where the choice changes what the document asserts: an identifier
     * shaped like an international bank account number is one, and any other is not.
     */
    @Test
    void writesTheAccountIdentifierAtTheElementItsFormNames() {
        String iban = write(SemanticDocument.builder()
                .put("/BG-16/BG-17/0/BT-84", "DE12500105170648489890").build());
        assertContains(iban, "<ram:IBANID>DE12500105170648489890</ram:IBANID>");
        String proprietary = write(SemanticDocument.builder()
                .put("/BG-16/BG-17/0/BT-84", "0012345678").build());
        assertContains(proprietary, "<ram:ProprietaryID>0012345678</ram:ProprietaryID>");
        assertFalse(proprietary.contains("IBANID"), proprietary);
    }

    /**
     * The other two places the syntax draws a distinction the model does not. Both keep
     * the value; `conformance/writers/cii-roundtrip.md` records what they change. The
     * price base quantity goes under the gross price where there is one and under the net
     * price where there is not, which is the case a table order alone would get wrong.
     */
    @Test
    void writesThePriceBaseQuantityUnderThePriceThatIsThere() {
        String gross = write(SemanticDocument.builder()
                .put("/BG-25/0/BG-29/BT-148", "12.00")
                .put("/BG-25/0/BG-29/BT-149", "2")
                .put("/BG-25/0/BG-29/BT-150", "H87")
                .build());
        assertContains(gross, "<ram:GrossPriceProductTradePrice>");
        assertContains(gross, "<ram:BasisQuantity unitCode=\"H87\">2</ram:BasisQuantity>");
        assertFalse(gross.contains("<ram:NetPriceProductTradePrice>"), gross);

        String net = write(SemanticDocument.builder()
                .put("/BG-25/0/BG-29/BT-146", "10.00")
                .put("/BG-25/0/BG-29/BT-149", "2")
                .put("/BG-25/0/BG-29/BT-150", "H87")
                .build());
        assertContains(net, "<ram:NetPriceProductTradePrice>");
        assertContains(net, "<ram:BasisQuantity unitCode=\"H87\">2</ram:BasisQuantity>");
    }

    @Test
    void refusesToWriteMoreThanTheOptionsAllow() {
        SemanticDocument document = SemanticDocument.builder().put("/BT-1", "RE-1").build();
        BindingLimitException refused = assertThrows(BindingLimitException.class, () ->
                CiiWriter.write(document, WriterOptions.builder().maxOutputBytes(64).build()));
        assertContains(refused.getMessage(), "the 64 bytes this run was given");
    }

    @Test
    void carriesOneSettingOverIntoAnother() {
        WriterOptions options = WriterOptions.defaults().toBuilder().indent(false).build();
        assertFalse(options.indent());
        assertEquals(WriterOptions.DEFAULT_MAX_OUTPUT_BYTES, options.maxOutputBytes());
        assertTrue(options.toBuilder().maxOutputBytes(1024).build().maxOutputBytes() == 1024);
        assertFalse(options.toBuilder().build().indent());
    }

    @Test
    void describesItsOptionsAndItsResult() {
        assertEquals("WriterOptions[indent=true, maxOutputBytes="
                + WriterOptions.DEFAULT_MAX_OUTPUT_BYTES + ", document=AUTO,"
                + " taxRegistrationScheme="
                + WriterOptions.DEFAULT_TAX_REGISTRATION_SCHEME + "]",
                WriterOptions.defaults().toString());
        WriteResult result = CiiWriter.writeWithReport(
                SemanticDocument.builder().put("/BT-1", "RE-1").build(),
                WriterOptions.defaults());
        assertContains(result.toString(), "1 values written, 0 dropped, 0 notes");
        assertEquals(BindingSyntax.CII, result.report().syntax());
    }

    private static String write(SemanticDocument document) {
        return new String(CiiWriter.write(document), StandardCharsets.UTF_8);
    }

    private static void assertContains(String haystack, String needle) {
        assertTrue(haystack.contains(needle), needle + "\nis not in\n" + haystack);
    }

    private static void assertOrder(String xml, String... fragments) {
        int at = -1;
        for (String fragment : fragments) {
            int found = xml.indexOf(fragment);
            assertTrue(found > at, fragment + " does not follow what goes before it in\n" + xml);
            at = found;
        }
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        for (int at = haystack.indexOf(needle); at >= 0;
                at = haystack.indexOf(needle, at + needle.length())) {
            found++;
        }
        return found;
    }
}
