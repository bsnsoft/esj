package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.imports.ImportNote;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

/**
 * Source elements a syntax binding cannot classify, and therefore does not carry.
 *
 * <p>The transformation into the XR representation selects the elements of a business group
 * by a value inside them: a document level allowance is an allowance charge whose indicator
 * says {@code false}, the payment instructions are grouped by the payment means type code,
 * and the invoice total VAT amount is the tax total whose currency is the currency of the
 * invoice. Each of those selections is silent about an element that states neither value —
 * an allowance charge with no indicator, a payment means with no type code, a tax total in
 * no currency the invoice names — and every such shape parses against the schema of its
 * syntax, so they arrive, are selected by nothing, and the business terms below them reach
 * no semantic document.
 *
 * <p>That is a loss like any other and belongs in the report. The mapper writes a note for
 * every element of the XR representation it cannot place; this class is the same promise one
 * step earlier, for an element that never became XR at all. It does not repair anything and
 * does not guess which group the element belonged to: it says that the source carried one,
 * which business terms are consequently absent, and why.
 *
 * <p>The table is per syntax and per selection, because the stylesheet of one syntax does
 * not make every selection the binding of that syntax states: it reads the tax
 * registration of a CII tax representative whatever scheme the registration is of, so
 * BT-63 reaches the document from an element that is not a value added tax registration
 * and there is nothing here to report. {@code conformance/readers.md} records that as a
 * limitation of this path against the streaming reader, which selects the element as the
 * CEN syntax binding does.
 *
 * <p>The check descends the declared paths and no others, so it costs the elements on the way
 * to a group and not a walk of the document.
 */
final class XrCoverage {

    /**
     * One way the binding tells an element it carries from an element it does not.
     *
     * <p>A selection may offer several: the tax totals of an invoice are read twice, once
     * against the invoice currency and once against the accounting currency, and an element
     * either key admits is carried.
     */
    private interface Key {

        /**
         * Tells whether this key classifies an element.
         *
         * @param element the element the binding selects from
         * @param root    the root element of the source document
         * @return whether the binding carries it
         */
        boolean classifies(XdmNode element, XdmNode root);
    }

    /**
     * A selection made on the content of an element below the one selected.
     *
     * @param names    the chain of element local names, below the selected element, of the
     *                 value the selection is made on
     * @param admitted the contents the binding classifies, or empty where any non-empty
     *                 content is classified
     */
    private record Content(List<String> names, Set<String> admitted) implements Key {

        @Override
        public boolean classifies(XdmNode element, XdmNode root) {
            List<XdmNode> keys = along(List.of(element), names);
            if (keys.isEmpty()) {
                return false;
            }
            String value = keys.get(0).getStringValue().strip();
            if (value.isEmpty()) {
                return false;
            }
            return admitted.isEmpty() || admitted.contains(value);
        }
    }

    /**
     * A selection made on an attribute of the element, against a value the document states
     * somewhere else.
     *
     * @param name    the local name of the attribute
     * @param against the chain of element local names, from the root of the source document,
     *                of the value the attribute is compared with
     */
    private record Attribute(String name, List<String> against) implements Key {

        @Override
        public boolean classifies(XdmNode element, XdmNode root) {
            String value = element.getAttributeValue(new QName(name));
            if (value == null || value.strip().isEmpty()) {
                return false;
            }
            List<XdmNode> stated = along(List.of(root), against);
            return !stated.isEmpty() && stated.get(0).getStringValue().strip().equals(value.strip());
        }
    }

    /**
     * A selection made on an attribute of the element, against a fixed set of values.
     *
     * @param name     the local name of the attribute
     * @param admitted the contents the binding classifies, or empty where the binding
     *                 classifies an element that carries the attribute at all
     */
    private record AttributeIn(String name, Set<String> admitted) implements Key {

        @Override
        public boolean classifies(XdmNode element, XdmNode root) {
            String value = element.getAttributeValue(new QName(name));
            if (value == null || value.strip().isEmpty()) {
                return false;
            }
            return admitted.isEmpty() || admitted.contains(value.strip());
        }
    }

    /**
     * One selection of the binding, and what an element it does not select costs.
     *
     * @param path    the chain of element local names from the root of the source document
     *                down to the element the binding selects from
     * @param keys    the ways the binding classifies such an element; one of them suffices
     * @param group   the business group the element would have become, or the one it belongs
     *                to
     * @param terms   the business terms that consequently did not reach the semantic
     *                document, in English
     * @param because what the element states instead, in English
     */
    private record Selection(List<String> path,
                             List<Key> keys,
                             String group,
                             String terms,
                             String because) {
    }

    private static final String NO_INDICATOR =
            "the allowance charge states no indicator of whether it is an allowance or a"
                    + " charge, and the binding of this syntax selects the two by that value";

    private static final String NO_TYPE_CODE =
            "the payment means states no type code, and the binding of this syntax groups the"
                    + " payment instructions by that value";

    private static final String NO_CURRENCY =
            "the tax total states no currency that the invoice names, and the binding of this"
                    + " syntax tells the total in the invoice currency from the total in the"
                    + " accounting currency by that value";

    private static final String NO_TAX_SCHEME =
            "the tax registration states no identification scheme that the binding of this"
                    + " syntax selects a value added tax registration by";

    private static final String NO_GLOBAL_SCHEME =
            "the global identifier states no identification scheme, and the binding of this"
                    + " syntax selects that element by the presence of one";

    private static final Set<String> VAT_SCHEME = Set.of("VA");

    private static final Set<String> VAT_OR_FISCAL = Set.of("VA", "FC");

    private static final String AGREEMENT = "ApplicableHeaderTradeAgreement";

    private static final String TOTALS =
            "BT-110, or BT-111 where the invoice states a VAT accounting currency,";

    private static final Set<String> INDICATORS = Set.of("true", "false", "1", "0");

    private static final List<Selection> CII = List.of(
            new Selection(List.of("SupplyChainTradeTransaction", "ApplicableHeaderTradeSettlement",
                    "SpecifiedTradeSettlementPaymentMeans"),
                    List.of(new Content(List.of("TypeCode"), Set.of())),
                    "BG-16", "BT-81, BT-82, BT-83 and the groups BG-17, BG-18 and BG-19 below it",
                    NO_TYPE_CODE),
            new Selection(List.of("SupplyChainTradeTransaction", "ApplicableHeaderTradeSettlement",
                    "SpecifiedTradeAllowanceCharge"),
                    List.of(new Content(List.of("ChargeIndicator", "Indicator"), INDICATORS)),
                    "BG-20 or BG-21", "BT-92 to BT-98, or BT-99 to BT-105",
                    NO_INDICATOR),
            new Selection(List.of("SupplyChainTradeTransaction", "IncludedSupplyChainTradeLineItem",
                    "SpecifiedLineTradeSettlement", "SpecifiedTradeAllowanceCharge"),
                    List.of(new Content(List.of("ChargeIndicator", "Indicator"), INDICATORS)),
                    "BG-27 or BG-28", "BT-136 to BT-140, or BT-141 to BT-145", NO_INDICATOR),
            new Selection(List.of("SupplyChainTradeTransaction", "ApplicableHeaderTradeSettlement",
                    "SpecifiedTradeSettlementHeaderMonetarySummation", "TaxTotalAmount"),
                    List.of(new Attribute("currencyID", List.of("SupplyChainTradeTransaction",
                                    "ApplicableHeaderTradeSettlement", "InvoiceCurrencyCode")),
                            new Attribute("currencyID", List.of("SupplyChainTradeTransaction",
                                    "ApplicableHeaderTradeSettlement", "TaxCurrencyCode"))),
                    "BG-22", TOTALS, NO_CURRENCY),
            registration(AGREEMENT, "SellerTradeParty", VAT_OR_FISCAL, "BG-4",
                    "BT-31, and BT-32 where the registration is the fiscal one,"),
            registration(AGREEMENT, "BuyerTradeParty", VAT_SCHEME, "BG-7", "BT-48"),
            globalIdentifier(AGREEMENT, "SellerTradeParty", "BG-4", "BT-29"),
            globalIdentifier(AGREEMENT, "BuyerTradeParty", "BG-7", "BT-46"),
            globalIdentifier("ApplicableHeaderTradeSettlement", "PayeeTradeParty",
                    "BG-10", "BT-60"),
            globalIdentifier("ApplicableHeaderTradeDelivery", "ShipToTradeParty",
                    "BG-13", "BT-71"));

    /** Returns the CII selection of a party tax registration by the scheme it states. */
    private static Selection registration(String section,
                                          String party,
                                          Set<String> admitted,
                                          String group,
                                          String terms) {
        return new Selection(List.of("SupplyChainTradeTransaction", section, party,
                "SpecifiedTaxRegistration", "ID"),
                List.of(new AttributeIn("schemeID", admitted)), group, terms, NO_TAX_SCHEME);
    }

    /** Returns the CII selection of a party global identifier by the scheme it states. */
    private static Selection globalIdentifier(String section,
                                              String party,
                                              String group,
                                              String term) {
        return new Selection(List.of("SupplyChainTradeTransaction", section, party,
                "GlobalID"), List.of(new AttributeIn("schemeID", Set.of())), group, term,
                NO_GLOBAL_SCHEME);
    }

    private static final List<Selection> UBL = List.of(
            new Selection(List.of("PaymentMeans"),
                    List.of(new Content(List.of("PaymentMeansCode"), Set.of())),
                    "BG-16", "BT-81, BT-82, BT-83 and the groups BG-17, BG-18 and BG-19 below it",
                    NO_TYPE_CODE),
            new Selection(List.of("AllowanceCharge"),
                    List.of(new Content(List.of("ChargeIndicator"), INDICATORS)),
                    "BG-20 or BG-21", "BT-92 to BT-98, or BT-99 to BT-105", NO_INDICATOR),
            new Selection(List.of("InvoiceLine", "AllowanceCharge"),
                    List.of(new Content(List.of("ChargeIndicator"), INDICATORS)),
                    "BG-27 or BG-28", "BT-136 to BT-140, or BT-141 to BT-145", NO_INDICATOR),
            new Selection(List.of("CreditNoteLine", "AllowanceCharge"),
                    List.of(new Content(List.of("ChargeIndicator"), INDICATORS)),
                    "BG-27 or BG-28", "BT-136 to BT-140, or BT-141 to BT-145", NO_INDICATOR),
            new Selection(List.of("InvoiceLine", "Price", "AllowanceCharge"),
                    List.of(new Content(List.of("ChargeIndicator"), INDICATORS)),
                    "BG-29", "BT-147 and BT-148", NO_INDICATOR),
            new Selection(List.of("CreditNoteLine", "Price", "AllowanceCharge"),
                    List.of(new Content(List.of("ChargeIndicator"), INDICATORS)),
                    "BG-29", "BT-147 and BT-148", NO_INDICATOR),
            new Selection(List.of("TaxTotal", "TaxAmount"),
                    List.of(new Attribute("currencyID", List.of("DocumentCurrencyCode")),
                            new Attribute("currencyID", List.of("TaxCurrencyCode"))),
                    "BG-22", TOTALS, NO_CURRENCY),
            new Selection(List.of("AccountingCustomerParty", "Party", "PartyTaxScheme"),
                    List.of(new Content(List.of("TaxScheme", "ID"), Set.of("VAT"))),
                    "BG-7", "BT-48", NO_TAX_SCHEME),
            new Selection(List.of("TaxRepresentativeParty", "PartyTaxScheme"),
                    List.of(new Content(List.of("TaxScheme", "ID"), Set.of("VAT"))),
                    "BG-11", "BT-63", NO_TAX_SCHEME));

    private XrCoverage() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns one note for every element of the source document that the binding of its
     * syntax selects into no business group.
     *
     * @param root   the root element of the source document
     * @param syntax the syntax it was read as
     * @return the notes, in document order, empty where the binding carried everything
     */
    static List<ImportNote> notes(XdmNode root, XrSyntax syntax) {
        List<ImportNote> notes = new ArrayList<>();
        for (Selection selection : syntax == XrSyntax.CII ? CII : UBL) {
            for (XdmNode element : along(List.of(root), selection.path())) {
                if (!classified(element, root, selection)) {
                    notes.add(new ImportNote(ImportNote.Kind.UNPLACEABLE, selection.group(),
                            "the source document carries an element of " + selection.group()
                                    + " that did not reach the semantic document: "
                                    + selection.because() + ", so " + selection.terms()
                                    + " did not reach it"));
                }
            }
        }
        return List.copyOf(notes);
    }

    private static boolean classified(XdmNode element, XdmNode root, Selection selection) {
        for (Key key : selection.keys()) {
            if (key.classifies(element, root)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the elements reached from a set of elements along a chain of local names. */
    private static List<XdmNode> along(List<XdmNode> from, List<String> names) {
        List<XdmNode> reached = from;
        for (String name : names) {
            List<XdmNode> next = new ArrayList<>();
            for (XdmNode node : reached) {
                children(node, name, next);
            }
            reached = next;
            if (reached.isEmpty()) {
                return List.of();
            }
        }
        return reached;
    }

    private static void children(XdmNode node, String name, List<XdmNode> into) {
        XdmSequenceIterator<XdmNode> children = node.axisIterator(Axis.CHILD);
        while (children.hasNext()) {
            XdmNode child = children.next();
            if (child.getNodeName() != null && name.equals(child.getNodeName().getLocalName())) {
                into.add(child);
            }
        }
    }
}
