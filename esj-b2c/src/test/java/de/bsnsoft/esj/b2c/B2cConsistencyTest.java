package de.bsnsoft.esj.b2c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.rules.RuleCategory;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleSeverity;
import de.bsnsoft.esj.typed.InvoiceEditor;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The consistency checks: what a policy guarantees, and what happens when it does not hold. */
class B2cConsistencyTest {

    @Test
    void aDerivedInvoiceSatisfiesTheGuaranteeOfThePolicyThatWroteIt() {
        SemanticDocument document = grossUnitInvoice();

        assertEquals(List.of(),
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING));
    }

    @Test
    void aFindingCarriesTheShapeOfTheRuleEngineAndTheNameOfThisPack() {
        SemanticDocument document = grossUnitInvoice().toBuilder()
                .set(SemanticPath.of("/BG-22/BT-115"), SemanticValue.of("99.98"))
                .build();

        List<RuleFinding> findings =
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING);

        assertEquals(1, findings.size(), findings.toString());
        RuleFinding finding = findings.get(0);
        assertEquals("B2C-01", finding.code());
        assertEquals(RuleCategory.B2C, finding.category());
        assertEquals(RuleSeverity.FATAL, finding.severity());
        assertEquals("b2c", finding.packId());
        assertEquals("0.1", finding.packVersion());
        assertEquals("native", finding.engine());
        assertEquals(List.of("/BG-22/BT-115", "/BT-B2C-010"), finding.paths());
        assertTrue(finding.message().contains("99.99"), finding.message());
    }

    @Test
    void anItemNetPriceThatIsNotTheDisplayedGrossUnitPriceAtTheRateIsAFinding() {
        SemanticDocument document = grossUnitInvoice().toBuilder()
                .set(SemanticPath.of("/BG-25/0/BG-29/BT-146"), SemanticValue.of("84.03"))
                .build();

        List<RuleFinding> findings =
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING);

        assertEquals(List.of("B2C-02"),
                findings.stream().map(RuleFinding::code).toList(), findings.toString());
        assertTrue(findings.get(0).message().contains("84.02521"), findings.get(0).message());
    }

    @Test
    void theScaleOfTheCheckIsTheScaleThePolicyRanWith() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99");
        gross.displayedGrossTotal("99.99");
        GrossAuthoring policy = GrossAuthoring.GROSS_UNIT_AUTHORING.with(
                AuthoringOptions.standard().withNetPriceScale(10));
        gross.derive(policy);
        SemanticDocument document = invoice.document();

        assertEquals("84.025210084", Invoices.at(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals(List.of(), B2cConsistency.check(document, policy));
        assertEquals(List.of("B2C-02"),
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING).stream()
                        .map(RuleFinding::code).toList(),
                "the same document read against the default scale is another claim");
    }

    @Test
    void aLinePricedGrossIsCheckedAgainstItsDisplayedGrossLineTotal() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Repair kit, complete");
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossLineTotal("29.99");
        gross.derive(GrossAuthoring.GROSS_LINE_AUTHORING);
        SemanticDocument tampered = invoice.document().toBuilder()
                .set(SemanticPath.of("/BG-25/0/BT-131"), SemanticValue.of("25.21"))
                .build();

        List<RuleFinding> findings =
                B2cConsistency.check(tampered, GrossAuthoring.GROSS_LINE_AUTHORING);

        assertEquals(List.of("B2C-01", "B2C-03", "B2C-04"),
                findings.stream().map(RuleFinding::code).toList(), findings.toString());
        assertEquals(RuleSeverity.INFO, findings.get(0).severity(),
                "the invoice states no agreed gross total, so B2C-01 is not decided");
        assertTrue(findings.get(2).message().contains("25.2"), findings.get(2).message());
    }

    @Test
    void aDocumentWithoutAnAgreedGrossTotalIsNotJudgedOnOne() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        Gross.on(invoice).line(0).displayedGrossUnitPrice("99.99");
        Gross.on(invoice).derive(GrossAuthoring.GROSS_UNIT_AUTHORING);

        List<RuleFinding> findings = B2cConsistency.check(invoice.document(),
                GrossAuthoring.GROSS_UNIT_AUTHORING);

        assertEquals(1, findings.size(), findings.toString());
        assertEquals("B2C-01", findings.get(0).code());
        assertEquals(RuleSeverity.INFO, findings.get(0).severity());
        assertEquals(List.of(), findings.stream().filter(RuleFinding::fatal).toList());
    }

    @Test
    void aValueThatDoesNotSpellItsTypeGivesOneInformationAndNoJudgement() {
        SemanticDocument document = grossUnitInvoice().toBuilder()
                .set(SemanticPath.of("/BG-22/BT-115"), SemanticValue.of("ninety nine"))
                .build();

        List<RuleFinding> findings =
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING);

        assertEquals(1, findings.size(), findings.toString());
        assertEquals("B2C-00", findings.get(0).code());
        assertEquals(RuleSeverity.INFO, findings.get(0).severity());
        assertTrue(findings.get(0).message().contains("layer L2"));
    }

    @Test
    void theCategoryOfTheChecksIsDerivedFromTheirIdentifiers() {
        assertEquals(RuleCategory.B2C, RuleCategory.of("B2C-01"));
        assertEquals("B2C", RuleCategory.B2C.token());
        assertEquals(RuleCategory.EN_BR, RuleCategory.of("BR-CO-16"));
    }

    private static SemanticDocument grossUnitInvoice() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice(new BigDecimal("99.99"));
        gross.displayedGrossTotal(new BigDecimal("99.99"));
        gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
        return invoice.document();
    }
}
