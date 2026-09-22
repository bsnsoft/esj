package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;

/**
 * The tokens a pack manifest names the document syntaxes with.
 *
 * <p>They are the manifest's vocabulary, not the importer's, so the translation lives
 * here rather than in {@link XrSyntax}: a pack is data this module reads, and the
 * importer has no reason to learn the words a pack is written in.
 */
final class Syntaxes {

    /** The token of a UBL 2.1 invoice. */
    static final String UBL_INVOICE = "ubl-invoice";

    /** The token of a UBL 2.1 credit note. */
    static final String UBL_CREDIT_NOTE = "ubl-creditnote";

    /** The token of a UN/CEFACT cross industry invoice. */
    static final String CII = "cii";

    private Syntaxes() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the manifest token of a syntax.
     *
     * @param syntax the syntax
     * @return the token
     */
    static String token(XrSyntax syntax) {
        return switch (syntax) {
            case UBL_INVOICE -> UBL_INVOICE;
            case UBL_CREDIT_NOTE -> UBL_CREDIT_NOTE;
            case CII -> CII;
        };
    }

    /**
     * Returns the name of a syntax as a report writes it.
     *
     * @param syntax the syntax
     * @return the name in English
     */
    static String title(XrSyntax syntax) {
        return switch (syntax) {
            case UBL_INVOICE -> "UBL 2.1 Invoice";
            case UBL_CREDIT_NOTE -> "UBL 2.1 CreditNote";
            case CII -> "UN/CEFACT CII D16B CrossIndustryInvoice";
        };
    }
}
