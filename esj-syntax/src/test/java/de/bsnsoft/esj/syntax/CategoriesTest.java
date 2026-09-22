package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.xr.XmlFrontDoor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import org.junit.jupiter.api.Test;

/**
 * The table from a rule identifier to a category, checked against the identifiers the
 * vendored artefacts actually carry.
 *
 * <p>The identifiers are read out of the stylesheets rather than written down here: the
 * stylesheets are XML, the assertions in them are elements, and the identifier of an
 * assertion is on the element that reports it. A table written from a standards document
 * would describe the families somebody meant to ship; this one describes the families
 * that are in the artefact that runs.
 */
class CategoriesTest {

    /** The namespace of the Schematron validation report language. */
    private static final String SVRL = "http://purl.oclc.org/dsdl/svrl";

    /** The namespace of XSLT, whose {@code xsl:attribute} sets an identifier at run time. */
    private static final String XSLT = "http://www.w3.org/1999/XSL/Transform";

    private final Pack pack = Packs.bundled("xrechnung/3.0.2/2026-08-31");

    @Test
    void everyIdentifierOfTheArtefactsHasACategory() {
        Map<FindingCategory, Set<String>> byCategory = new TreeMap<>();
        Set<String> identifiers = identifiers();

        assertTrue(identifiers.size() > 1500, "the artefacts carry the rules of EN 16931,"
                + " of both syntax bindings and of the XRechnung specification, and this"
                + " run found " + identifiers.size() + " identifiers");
        for (String identifier : identifiers) {
            byCategory.computeIfAbsent(Categories.of(identifier), key -> new TreeSet<>())
                    .add(identifier);
        }

        assertEquals(Set.of(), byCategory.getOrDefault(FindingCategory.OTHER, Set.of()),
                "every identifier the artefacts report belongs to a family of the table");
        assertEquals(Set.of(FindingCategory.EN_BR, FindingCategory.EN_DEC,
                        FindingCategory.EN_CL, FindingCategory.UBL_BINDING,
                        FindingCategory.CII_BINDING, FindingCategory.XR_BR,
                        FindingCategory.XR_EXT),
                byCategory.keySet(),
                "the artefacts of this pack produce findings of these categories and no"
                        + " others");
    }

    @Test
    void sortsTheFamiliesTheTableNames() {
        Map<String, FindingCategory> expected = new LinkedHashMap<>();
        expected.put("BR-01", FindingCategory.EN_BR);
        expected.put("BR-S-08", FindingCategory.EN_BR);
        expected.put("BR-IC-11", FindingCategory.EN_BR);
        expected.put("BR-CO-10", FindingCategory.EN_BR);
        expected.put("BR-DEC-12", FindingCategory.EN_DEC);
        expected.put("BR-CL-10", FindingCategory.EN_CL);
        expected.put("UBL-SR-01", FindingCategory.UBL_BINDING);
        expected.put("UBL-CR-646", FindingCategory.UBL_BINDING);
        expected.put("UBL-DT-01", FindingCategory.UBL_BINDING);
        expected.put("CII-SR-475", FindingCategory.CII_BINDING);
        expected.put("CII-DT-01", FindingCategory.CII_BINDING);
        expected.put("BR-DE-15", FindingCategory.XR_BR);
        expected.put("BR-DE-CVD-01", FindingCategory.XR_BR);
        expected.put("BR-DE-TMP-32", FindingCategory.XR_BR);
        expected.put("BR-TMP-2", FindingCategory.XR_BR);
        expected.put("PEPPOL-EN16931-R001", FindingCategory.XR_BR);
        expected.put("BR-DEX-01", FindingCategory.XR_EXT);

        expected.forEach((code, category) ->
                assertEquals(category, Categories.of(code), code));
    }

    /**
     * The three families whose identifiers begin with the same five characters are the
     * reason the table matches the longest prefix rather than the first one.
     */
    @Test
    void keepsTheThreeFamiliesThatBeginWithBrDeApart() {
        assertEquals(FindingCategory.XR_BR, Categories.of("BR-DE-1"));
        assertEquals(FindingCategory.EN_DEC, Categories.of("BR-DEC-1"));
        assertEquals(FindingCategory.XR_EXT, Categories.of("BR-DEX-1"));
    }

    @Test
    void keepsAnIdentifierItDoesNotKnow() {
        assertEquals(FindingCategory.OTHER, Categories.of("SOMETHING-NEW-1"));
        assertEquals(FindingCategory.OTHER, Categories.of(""));
    }

    @Test
    void namesTheSchemaCategoryAfterTheSyntax() {
        assertEquals(FindingCategory.UBL_XSD,
                Categories.ofSchema(de.bsnsoft.esj.xr.XrSyntax.UBL_INVOICE));
        assertEquals(FindingCategory.UBL_XSD,
                Categories.ofSchema(de.bsnsoft.esj.xr.XrSyntax.UBL_CREDIT_NOTE));
        assertEquals(FindingCategory.CII_XSD,
                Categories.ofSchema(de.bsnsoft.esj.xr.XrSyntax.CII));
    }

    /** Every rule identifier an assertion of the four vendored stylesheets reports. */
    private Set<String> identifiers() {
        Set<String> identifiers = new TreeSet<>();
        for (PackComponent component : pack.components()) {
            if (component.role() != ComponentRole.SCHEMATRON_XSLT) {
                continue;
            }
            for (String file : component.files()) {
                collect(XmlFrontDoor.parse(pack.read(file)), identifiers);
            }
        }
        assertFalse(identifiers.isEmpty(), "the stylesheets report rule identifiers");
        return identifiers;
    }

    private static void collect(XdmNode node, Set<String> identifiers) {
        if (node.getNodeKind() == XdmNodeKind.ELEMENT && SVRL.equals(namespace(node))
                && isAssertion(node.getNodeName().getLocalName())) {
            identifier(node).ifPresent(identifiers::add);
            return;
        }
        for (XdmNode child : node.children()) {
            collect(child, identifiers);
        }
    }

    private static boolean isAssertion(String localName) {
        return localName.equals("failed-assert") || localName.equals("successful-report");
    }

    /**
     * Returns the identifier of an assertion. One compiler writes it as an attribute of
     * the element, the other as an {@code xsl:attribute} instruction inside it.
     */
    private static java.util.Optional<String> identifier(XdmNode assertion) {
        String literal = assertion.attribute("id");
        if (literal != null && !literal.isBlank() && !literal.contains("{")) {
            return java.util.Optional.of(literal.strip());
        }
        for (XdmNode child : assertion.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT
                    && XSLT.equals(namespace(child))
                    && "attribute".equals(child.getNodeName().getLocalName())
                    && "id".equals(child.attribute("name"))) {
                return java.util.Optional.of(child.getStringValue().strip());
            }
        }
        return java.util.Optional.empty();
    }

    private static String namespace(XdmNode element) {
        return element.getNodeName().getNamespace();
    }
}
