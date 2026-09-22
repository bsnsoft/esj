package de.bsnsoft.esj.bindings;

import java.nio.charset.StandardCharsets;

/** Small documents written for one question each, so that a failure names one thing. */
final class Documents {

    /** The namespace declarations of a UBL 2.1 invoice. */
    private static final String UBL_NAMESPACES =
            " xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                    + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:"
                    + "CommonAggregateComponents-2\""
                    + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:"
                    + "CommonBasicComponents-2\"";

    /** The namespace declarations of a UN/CEFACT CII D16B invoice. */
    private static final String CII_NAMESPACES =
            " xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\""
                    + " xmlns:ram=\"urn:un:unece:uncefact:data:standard:"
                    + "ReusableAggregateBusinessInformationEntity:100\""
                    + " xmlns:udt=\"urn:un:unece:uncefact:data:standard:"
                    + "UnqualifiedDataType:100\"";

    private Documents() {
        throw new AssertionError("no instances");
    }

    /** Returns a UBL invoice whose root element holds the given content. */
    static byte[] ubl(String content) {
        return utf8("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Invoice" + UBL_NAMESPACES
                + ">" + content + "</Invoice>");
    }

    /** Returns a UBL credit note whose root element holds the given content. */
    static byte[] creditNote(String content) {
        return utf8("<?xml version=\"1.0\" encoding=\"UTF-8\"?><CreditNote xmlns=\""
                + "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:"
                + "CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:"
                + "CommonBasicComponents-2\">" + content + "</CreditNote>");
    }

    /** Returns a cross industry invoice whose root element holds the given content. */
    static byte[] cii(String content) {
        return utf8("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rsm:CrossIndustryInvoice"
                + CII_NAMESPACES + ">" + content + "</rsm:CrossIndustryInvoice>");
    }

    /** Returns a cross industry invoice whose trade transaction holds the given content. */
    static byte[] ciiTransaction(String content) {
        return cii("<rsm:SupplyChainTradeTransaction>" + content
                + "</rsm:SupplyChainTradeTransaction>");
    }

    /** Returns a cross industry invoice whose header trade settlement holds the content. */
    static byte[] ciiSettlement(String content) {
        return ciiTransaction("<ram:ApplicableHeaderTradeSettlement>" + content
                + "</ram:ApplicableHeaderTradeSettlement>");
    }

    /** Returns a cross industry invoice whose header trade agreement holds the content. */
    static byte[] ciiAgreement(String content) {
        return ciiTransaction("<ram:ApplicableHeaderTradeAgreement>" + content
                + "</ram:ApplicableHeaderTradeAgreement>");
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
