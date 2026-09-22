package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The rule that decides whether an ESJ document and an invoice are two accounts of one
 * invoice.
 *
 * <p>One function decides it for the producer and for the consumer, so this is where the
 * rule itself is pinned: what counts as the same value, what a document may carry beyond
 * the invoice, and what it may not.
 */
class EsjAgreementTest {

    /** An invoice of the corpus, read through the importer. */
    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";

    /** A path of the invoice both documents carry, and the one the tests move. */
    private static final SemanticPath LINE_AMOUNT = SemanticPath.of("/BG-25/0/BT-131");

    /**
     * The whole rule, which is the one both sides apply: a document agreeing with itself
     * disagrees with nothing.
     */
    @Test
    void theWholeRuleHoldsOfADocumentAgainstItself() {
        SemanticDocument document = document();

        assertEquals(Optional.empty(),
                EsjAgreement.disagreement(document, document, BindingSyntax.CII));
    }

    /**
     * Two documents may state every path and every value identically and still be two
     * invoices: the semantic model names the registry they are read under, and two
     * editions need not agree on what a value means.
     */
    @Test
    void aDocumentOfAnotherEditionIsNoAccountOfTheSameInvoice() {
        SemanticDocument invoice = document();
        SemanticDocument other = invoice.toBuilder()
                .semanticModel("EN16931-1:2026").build();

        Optional<EsjAgreement.Disagreement> reason =
                EsjAgreement.disagreement(other, invoice, BindingSyntax.CII);

        assertEquals(invoice.values(), other.values(), "every value is the same");
        assertEquals(EsjAgreement.Ground.MODEL, reason.orElseThrow().ground());
        assertTrue(reason.orElseThrow().message().contains("EN16931-1:2026"),
                reason.orElseThrow().message());
        assertTrue(reason.orElseThrow().message().contains(invoice.semanticModel()),
                reason.orElseThrow().message());
    }

    /**
     * A term of another edition, in a document that names this one, is not a difference of
     * values: the syntax binds no such term, so the room the rule leaves for extension
     * terms would take it in. The document is measured against the registry it names
     * instead, which is the measurement {@code esj validate} makes of the same bytes.
     */
    @Test
    void aTermOfAnotherEditionUnderThisEditionIsNoAccountOfTheInvoice() {
        SemanticDocument invoice = document();
        SemanticDocument forged = invoice.toBuilder()
                .set(SemanticPath.of("/BT-170"), SemanticValue.of("a 2026 term")).build();

        Optional<EsjAgreement.Disagreement> reason =
                EsjAgreement.disagreement(forged, invoice, BindingSyntax.CII);

        assertEquals(EsjAgreement.Ground.UNSOUND, reason.orElseThrow().ground());
        assertTrue(reason.orElseThrow().message().contains("BT-170"),
                reason.orElseThrow().message());
    }

    /** A term of no edition at all is the same answer. */
    @Test
    void aTermOfNoEditionIsNoAccountOfTheInvoice() {
        SemanticDocument invoice = document();
        SemanticDocument forged = invoice.toBuilder()
                .set(SemanticPath.of("/BT-999"), SemanticValue.of("invented")).build();

        assertEquals(EsjAgreement.Ground.UNSOUND,
                EsjAgreement.disagreement(forged, invoice, BindingSyntax.CII)
                        .orElseThrow().ground());
    }

    /**
     * A path only an extension registry defines is what the convention exists for, and the
     * model layer reports it as not checked rather than as wrong, so it stays what the
     * last condition says it is.
     */
    @Test
    void anExtensionTermIsNoDisagreement() {
        SemanticDocument invoice = document();
        SemanticDocument extended = invoice.toBuilder()
                .set(SemanticPath.of("/BT-B2C-001"), SemanticValue.of("119.00")).build();

        assertEquals(Optional.empty(),
                EsjAgreement.disagreement(extended, invoice, BindingSyntax.CII));
    }

    /** A difference of values is carried by the whole rule as well. */
    @Test
    void theWholeRuleCarriesADifferenceOfValues() {
        SemanticDocument invoice = document();
        SemanticDocument tampered = invoice.toBuilder()
                .set(LINE_AMOUNT, SemanticValue.of("999999")).build();

        Optional<EsjAgreement.Disagreement> reason =
                EsjAgreement.disagreement(tampered, invoice, BindingSyntax.CII);

        assertEquals(EsjAgreement.Ground.VALUES, reason.orElseThrow().ground());
        assertTrue(reason.orElseThrow().message().contains(LINE_AMOUNT.toString()),
                reason.orElseThrow().message());
    }

    @Test
    void aDocumentAgreesWithItself() {
        SemanticDocument document = document();

        assertEquals(List.of(),
                EsjAgreement.differences(document, document, BindingSyntax.CII));
    }

    @Test
    void aValueStatedDifferentlyIsADifference() {
        SemanticDocument invoice = document();
        SemanticDocument tampered = invoice.toBuilder()
                .set(LINE_AMOUNT, SemanticValue.of("999999")).build();

        List<EsjAgreement.Difference> differences =
                EsjAgreement.differences(tampered, invoice, BindingSyntax.CII);

        assertEquals(List.of(new EsjAgreement.Difference(LINE_AMOUNT,
                EsjAgreement.Kind.DIFFERENT)), differences);
        assertTrue(EsjAgreement.describe(differences).contains(LINE_AMOUNT.toString()),
                EsjAgreement.describe(differences));
    }

    @Test
    void aValueOfTheInvoiceTheDocumentDoesNotCarryIsADifference() {
        SemanticDocument invoice = document();
        SemanticDocument shortened = invoice.toBuilder().remove(LINE_AMOUNT).build();

        assertEquals(List.of(new EsjAgreement.Difference(LINE_AMOUNT,
                        EsjAgreement.Kind.ABSENT)),
                EsjAgreement.differences(shortened, invoice, BindingSyntax.CII));
    }

    /** A term the syntax binds is a term the invoice would have carried. */
    @Test
    void aBoundTermOnlyTheDocumentCarriesIsADifference() {
        SemanticDocument invoice = document();
        SemanticDocument richer = invoice.toBuilder().put("/BT-22", "a note").build();

        assertEquals(List.of(new EsjAgreement.Difference(SemanticPath.of("/BT-22"),
                        EsjAgreement.Kind.UNSTATED)),
                EsjAgreement.differences(richer, invoice, BindingSyntax.CII));
    }

    /** A term the syntax does not bind is what the attachment is for. */
    @Test
    void anUnboundTermOnlyTheDocumentCarriesIsNoDifference() {
        SemanticDocument invoice = document();
        SemanticDocument richer = invoice.toBuilder().put("/BT-B2C-010", "119.00").build();

        assertEquals(List.of(),
                EsjAgreement.differences(richer, invoice, BindingSyntax.CII));
    }

    /** A supplementary component the invoice states has to be stated the same way. */
    @Test
    void aComponentThatDiffersIsADifference() {
        SemanticDocument invoice = document().toBuilder()
                .put(SemanticPath.of("/BG-4/BT-29/0"),
                        SemanticValue.identifier("0088123456785", "0088"))
                .build();
        SemanticDocument other = invoice.toBuilder()
                .set(SemanticPath.of("/BG-4/BT-29/0"),
                        SemanticValue.identifier("0088123456785", "0060"))
                .build();

        assertEquals(List.of(new EsjAgreement.Difference(SemanticPath.of("/BG-4/BT-29/0"),
                        EsjAgreement.Kind.DIFFERENT)),
                EsjAgreement.differences(other, invoice, BindingSyntax.CII));
    }

    /** The message names the first paths and counts the rest. */
    @Test
    void theDescriptionNamesTheFirstPathsAndCountsTheRest() {
        SemanticDocument invoice = document();
        SemanticDocument.Builder builder = invoice.toBuilder();
        for (SemanticPath path : invoice.values().keySet().stream().limit(8).toList()) {
            builder.remove(path);
        }

        String described = EsjAgreement.describe(
                EsjAgreement.differences(builder.build(), invoice, BindingSyntax.CII));

        assertTrue(described.endsWith("and 3 more"), described);
    }

    @Test
    void everyArgumentIsRequired() {
        SemanticDocument document = document();

        assertThrows(NullPointerException.class,
                () -> EsjAgreement.differences(null, document, BindingSyntax.CII));
        assertThrows(NullPointerException.class,
                () -> EsjAgreement.differences(document, null, BindingSyntax.CII));
        assertThrows(NullPointerException.class,
                () -> EsjAgreement.differences(document, document, null));
        assertThrows(NullPointerException.class,
                () -> EsjAgreement.disagreement(null, document, BindingSyntax.CII));
        assertThrows(NullPointerException.class,
                () -> EsjAgreement.disagreement(document, null, BindingSyntax.CII));
        assertThrows(NullPointerException.class,
                () -> EsjAgreement.disagreement(document, document, null));
    }

    private static SemanticDocument document() {
        return new XrImporter().importXml(Conformance.instance(CII));
    }
}
