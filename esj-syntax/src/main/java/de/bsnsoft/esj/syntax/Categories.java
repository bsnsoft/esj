package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The table from a rule identifier to a {@link FindingCategory}.
 *
 * <p>It is built from the identifiers the vendored artefacts actually carry rather than
 * from the families the standards documents describe, and the build enumerates those
 * identifiers out of the artefacts and asks this table about every one of them. A family
 * that appears in a later release and is not here lands in {@link FindingCategory#OTHER}
 * with its identifier intact, which is the one outcome that cannot mislead.
 *
 * <p>The longest matching prefix wins, which is what keeps the three families that start
 * with {@code BR-DE} apart: {@code BR-DEC-} is a decimal rule of EN 16931,
 * {@code BR-DEX-} a rule of the XRechnung extension and {@code BR-DE-} a rule of the
 * XRechnung CIUS.
 */
final class Categories {

    private static final Map<String, FindingCategory> TABLE = table();

    private Categories() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the category of a rule identifier.
     *
     * @param code the identifier the artefact reports
     * @return the category, {@link FindingCategory#OTHER} for an identifier of no family
     *         in the table
     */
    static FindingCategory of(String code) {
        FindingCategory category = FindingCategory.OTHER;
        int longest = -1;
        for (Map.Entry<String, FindingCategory> entry : TABLE.entrySet()) {
            String prefix = entry.getKey();
            if (code.startsWith(prefix) && prefix.length() > longest) {
                longest = prefix.length();
                category = entry.getValue();
            }
        }
        return category;
    }

    /**
     * Returns the category of a schema finding, which depends on the schema that was run
     * rather than on an identifier.
     *
     * @param syntax the syntax of the document
     * @return the category
     */
    static FindingCategory ofSchema(XrSyntax syntax) {
        return switch (syntax) {
            case UBL_INVOICE, UBL_CREDIT_NOTE -> FindingCategory.UBL_XSD;
            case CII -> FindingCategory.CII_XSD;
        };
    }

    /** Builds the table: a prefix of a rule identifier, and what it belongs to. */
    private static Map<String, FindingCategory> table() {
        Map<String, FindingCategory> table = new LinkedHashMap<>();
        // EN 16931, as the CEN artefacts identify its rules. BR- alone carries the
        // business rules numbered BR-01 and the ones of the VAT categories, BR-S-, BR-Z-,
        // BR-E-, BR-AE-, BR-G-, BR-O-, BR-IC-, BR-AF-, BR-AG- and BR-B-.
        table.put("BR-", FindingCategory.EN_BR);
        table.put("BR-CO-", FindingCategory.EN_BR);
        table.put("BR-DEC-", FindingCategory.EN_DEC);
        table.put("BR-CL-", FindingCategory.EN_CL);
        // How EN 16931 is bound to each syntax: the syntax rules, the conformance rules
        // and the data type rules of the CEN artefacts.
        table.put("UBL-SR-", FindingCategory.UBL_BINDING);
        table.put("UBL-CR-", FindingCategory.UBL_BINDING);
        table.put("UBL-DT-", FindingCategory.UBL_BINDING);
        table.put("CII-SR-", FindingCategory.CII_BINDING);
        table.put("CII-DT-", FindingCategory.CII_BINDING);
        // The XRechnung artefact. BR-DE- covers BR-DE-CVD- and BR-DE-TMP- as well;
        // BR-TMP- are the temporary rules a release of that artefact carries while it
        // corrects something; and the identifiers of the Peppol rule set are there
        // because the XRechnung Schematron carries those rules, so a finding with one of
        // them is a finding of the CIUS component that ran.
        table.put("BR-DE-", FindingCategory.XR_BR);
        table.put("BR-TMP-", FindingCategory.XR_BR);
        table.put("PEPPOL-EN16931-", FindingCategory.XR_BR);
        table.put("BR-DEX-", FindingCategory.XR_EXT);
        return table;
    }
}
