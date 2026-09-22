package de.bsnsoft.esj.b2c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleSeverity;
import de.bsnsoft.esj.typed.En16931;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The invoices the policy tests are run over, written through the typed editor of the core
 * model: everything EN 16931-1 asks of an invoice except the amounts, which are what a gross
 * authoring policy derives.
 */
final class Invoices {

    private static final Set<ValidationLayer> STRUCTURE =
            EnumSet.of(ValidationLayer.L2, ValidationLayer.L3);

    /** The rules of EN 16931-1, over the core registry with the B2C extension loaded. */
    private static final RuleEngine RULES =
            de.bsnsoft.esj.rules.en16931.En16931.engine(B2c.registry());

    private Invoices() {
    }

    /**
     * Returns a consumer invoice with everything but its amounts: the header, the seller, the
     * buyer and the payment instructions of {@code examples/b2c-gross.esj.json}.
     *
     * @return the editor, to write the lines and the gross figures into
     */
    static InvoiceEditor consumer() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceNumber("RE-2026-0731")
                .issueDate(LocalDate.of(2026, 4, 14))
                .typeCode("380")
                .currencyCode("EUR")
                .paymentDueDate(LocalDate.of(2026, 4, 28))
                .paymentTerms("Payable within 14 days without deduction.");
        invoice.processControl().specificationIdentifier("urn:cen.eu:en16931:2017");

        invoice.seller()
                .name("Beispiel Handels GmbH")
                .vatIdentifier("DE123456789")
                .electronicAddress("shop@beispiel.invalid", "EM")
                .postalAddress(address -> address
                        .addressLine1("Werkstrasse 8")
                        .city("Beispielstadt")
                        .postCode("10117")
                        .countryCode("DE"));

        invoice.buyer()
                .name("Jana Muster")
                .postalAddress(address -> address
                        .addressLine1("Lindenweg 4")
                        .city("Musterstadt")
                        .postCode("20095")
                        .countryCode("DE"));

        invoice.paymentInstructions(payment -> {
            payment.paymentMeansTypeCode("58").remittanceInformation("RE-2026-0731");
            payment.creditTransfers().add(transfer -> transfer
                    .accountIdentifier("DE89370400440532013000")
                    .accountName("Beispiel Handels GmbH"));
        });
        return invoice;
    }

    /**
     * Appends one invoice line without a price and without a net amount.
     *
     * @param invoice  the editor
     * @param quantity the invoiced quantity (BT-129)
     * @param category the invoiced item VAT category code (BT-151)
     * @param rate     the invoiced item VAT rate (BT-152), or {@code null} for a category
     *                 that is levied at none
     * @param name     the item name (BT-153)
     */
    static void line(InvoiceEditor invoice, String quantity, String category, String rate,
                     String name) {
        int number = invoice.invoiceLines().size() + 1;
        invoice.invoiceLines().add(line -> {
            line.identifier(String.valueOf(number))
                    .quantity(new BigDecimal(quantity), "H87")
                    .vat(vat -> {
                        vat.vatCategoryCode(category);
                        if (rate != null) {
                            vat.vatRate(new BigDecimal(rate));
                        }
                    })
                    .item(item -> item.name(name));
        });
    }

    /**
     * Appends one invoice line at the standard rate of 19 per cent.
     *
     * @param invoice  the editor
     * @param quantity the invoiced quantity (BT-129)
     * @param name     the item name (BT-153)
     */
    static void line(InvoiceEditor invoice, String quantity, String name) {
        line(invoice, quantity, "S", "19", name);
    }

    /**
     * Returns the value at a path of a document, as its plain decimal spelling.
     *
     * @param document the document
     * @param path     the semantic path
     * @return the value, or {@code null} where the document carries none
     */
    static String at(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path)).map(SemanticValue::asString).orElse(null);
    }

    /**
     * Asserts that a document is a conformant ESJ document and that the business rules of
     * EN 16931-1 find nothing fatal in it.
     *
     * @param document the document a policy wrote
     */
    static void isAConformantInvoice(SemanticDocument document) {
        byte[] canonical = EsjWriter.canonical().toBytes(document);
        assertEquals(document.values(), EsjReader.strict().read(canonical).values(),
                "layer L1: the document is written and read back unchanged");
        assertEquals(List.of(),
                StructuralValidator.validate(document, B2c.registry(), STRUCTURE).findings(),
                "layers L2 and L3 against the core registry with the extension loaded");
        assertEquals(List.of(), fatal(document),
                "the business rules of EN 16931-1, pack en16931/1.3.16");
    }

    /**
     * Returns the fatal findings of the EN 16931-1 rule pack, as one line each.
     *
     * @param document the document
     * @return the lines, empty where the pack found nothing fatal
     */
    static List<String> fatal(SemanticDocument document) {
        List<String> lines = new ArrayList<>();
        for (RuleFinding finding : RULES.evaluate(document)) {
            if (finding.severity() == RuleSeverity.FATAL) {
                lines.add(finding.toString());
            }
        }
        return lines;
    }

    /**
     * Asserts that the arithmetic rules of EN 16931-1 were among the rules that ran, so that
     * a green invariant is not a green nothing.
     *
     * @param document the document
     */
    static void arithmeticWasChecked(SemanticDocument document) {
        assertTrue(RULES.ruleIds().containsAll(List.of("BR-CO-15", "BR-CO-16", "BR-CO-17")),
                "the pack carries the arithmetic rules of the document totals");
        List<String> conditions = new ArrayList<>();
        for (RuleFinding finding : RULES.evaluate(document)) {
            if (finding.code().startsWith("BR-CO-")) {
                conditions.add(finding.toString());
            }
        }
        assertEquals(List.of(), conditions,
                "no condition of EN 16931-1 had anything to say about this invoice");
    }
}
