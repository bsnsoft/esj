package de.bsnsoft.esj.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.bindings.UblWriter;
import de.bsnsoft.esj.bindings.WriteResult;
import de.bsnsoft.esj.bindings.WriterOptions;
import de.bsnsoft.esj.invoice.BillingPeriod;
import de.bsnsoft.esj.invoice.Invoice;
import de.bsnsoft.esj.invoice.ItemAttribute;
import de.bsnsoft.esj.invoice.Line;
import de.bsnsoft.esj.invoice.Party;
import de.bsnsoft.esj.invoice.PaymentMeans;
import de.bsnsoft.esj.invoice.PaymentTerms;
import de.bsnsoft.esj.invoice.Vat;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.invoice.code.ElectronicAddressScheme;
import de.bsnsoft.esj.invoice.code.NoteSubject;
import de.bsnsoft.esj.invoice.code.Unit;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.syntax.PackCatalog;
import de.bsnsoft.esj.syntax.SyntaxOptions;
import de.bsnsoft.esj.syntax.SyntaxReport;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import de.bsnsoft.esj.typed.build.Profile;
import de.bsnsoft.esj.validate.ValidationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * An invoice the domain API writes with item attributes (BG-32) and a note under a subject
 * code (BT-21), through both syntaxes and back: the writer binds every value, the reader
 * returns the document that was written, and the XSD and the Schematron of the XRechnung
 * pack accept the bytes that are sent.
 */
class NoteSubjectAndItemAttributeTest {

    /** The validation pack the bytes are checked with, named so that a newer default keeps it. */
    private static final String PACK = "xrechnung/3.0.2/2026-10-12";

    private static final String LABOUR = "Arbeitskosten netto (§ 35a EStG)";

    private static final String NOTE =
            "Enthaltene Arbeitskosten nach § 35a EStG: 412,50 EUR netto.";

    @Test
    void theCrossIndustryInvoiceCarriesBothAndReadsBackTheSame() {
        SemanticDocument invoice = invoice();
        WriteResult written = CiiWriter.writeWithReport(invoice, WriterOptions.defaults());
        String xml = compact(written.xml());

        assertTrue(written.report().isFaithful(), written.report().toString());
        assertTrue(xml.contains("<ram:ApplicableProductCharacteristic><ram:Description>" + LABOUR
                + "</ram:Description><ram:Value>412.50</ram:Value>"
                + "</ram:ApplicableProductCharacteristic>"), xml);
        assertTrue(xml.contains("<ram:IncludedNote><ram:Content>" + NOTE + "</ram:Content>"
                + "<ram:SubjectCode>AAI</ram:SubjectCode></ram:IncludedNote>"), xml);
        assertReadsBack(invoice, written.xml());
        assertAccepted(written.xml());
    }

    @Test
    void theUblInvoiceCarriesBothAndReadsBackTheSame() {
        SemanticDocument invoice = invoice();
        WriteResult written = UblWriter.writeWithReport(invoice, WriterOptions.defaults());
        String xml = compact(written.xml());

        assertTrue(written.report().isFaithful(), written.report().toString());
        assertTrue(xml.contains("<cac:AdditionalItemProperty><cbc:Name>" + LABOUR
                + "</cbc:Name><cbc:Value>412.50</cbc:Value></cac:AdditionalItemProperty>"), xml);
        assertTrue(xml.contains("<cbc:Note>#AAI#" + NOTE + "</cbc:Note>"), xml);
        assertReadsBack(invoice, written.xml());
        assertAccepted(written.xml());
    }

    private static void assertReadsBack(SemanticDocument invoice, byte[] xml) {
        SemanticDocument read = new StreamingReader().read(xml).document();

        assertEquals(Canonicalizer.semanticDigest(invoice), Canonicalizer.semanticDigest(read),
                "the document read back is the one that was written");
        assertEquals(SemanticValue.of("AAI"), read.value(SemanticPath.of("/BG-1/1/BT-21"))
                .orElseThrow());
        assertEquals(List.of(new ItemAttribute(LABOUR, "412.50"),
                        new ItemAttribute("Leistungsort", "Musterstadt")),
                Invoice.read(read).lines().get(0).attributes());
        assertEquals(List.of(), Invoice.read(read).lines().get(1).attributes());
    }

    private static void assertAccepted(byte[] xml) {
        SyntaxReport report = SyntaxValidator.validate(xml,
                SyntaxOptions.defaults().withPack(PackCatalog.bundled().find(PACK)));
        String findings = report.findings().stream().map(Object::toString)
                .collect(Collectors.joining("\n"));

        assertEquals(PACK, report.pack().orElseThrow().directory(), findings);
        assertEquals(ValidationStatus.VALID, report.verdict(), findings);
        assertTrue(report.findings().isEmpty(), findings);
        assertEquals(3, report.ran().size(), report.ran().toString());
        assertTrue(report.ran().stream().anyMatch(run -> run.component().startsWith("en16931-")),
                "the EN 16931 Schematron ran: " + report.ran());
        assertTrue(report.ran().stream().anyMatch(run -> run.component().startsWith("xrechnung-")),
                "the XRechnung Schematron ran: " + report.ran());
    }

    /** The written XML without the indentation between elements. */
    private static String compact(byte[] xml) {
        return new String(xml, UTF_8).replaceAll(">\\s+<", "><");
    }

    private static SemanticDocument invoice() {
        return Invoice.create(Profile.XRECHNUNG_3_0)
                .number("RE-2026-0350")
                .issued(LocalDate.of(2026, 11, 6))
                .currency(CurrencyCode.EUR)
                .seller(Party.named("Beispiel Garten GmbH").vatId("DE123456789")
                        .address("Musterweg 12", "10117", "Beispielstadt", Country.DE)
                        .contact("Büro", "+49 30 000000", "buero@example.invalid")
                        .electronicAddress("rechnung@example.invalid",
                                ElectronicAddressScheme.ELECTRONIC_MAIL))
                .buyer(Party.named("Erika Mustermann")
                        .address("Beispielallee 3", "20095", "Musterstadt", Country.DE)
                        .electronicAddress("erika@example.invalid",
                                ElectronicAddressScheme.ELECTRONIC_MAIL))
                .line(Line.of("Heckenschnitt").quantity(5, Unit.HOUR).unitPrice("82.50")
                        .vat(Vat.standard(19))
                        .attribute(LABOUR, "412.50")
                        .attribute("Leistungsort", "Musterstadt"))
                .line(Line.of("Grünschnitt-Entsorgung").quantity(1, Unit.PIECE).unitPrice("35")
                        .vat(Vat.standard(19)))
                .buyerReference("RE-2026-0350")
                .period(BillingPeriod.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                        PaymentTerms.days(14, "Zahlbar innerhalb von 14 Tagen ohne Abzug."))
                .note("Vielen Dank für Ihren Auftrag.")
                .note(NoteSubject.GENERAL_INFORMATION, NOTE)
                .build();
    }
}
