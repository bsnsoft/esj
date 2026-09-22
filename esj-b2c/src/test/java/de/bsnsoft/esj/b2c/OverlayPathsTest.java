package de.bsnsoft.esj.b2c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.runtime.TermPaths;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The overlay of an extension and the typed view of the core model have to address one and
 * the same value, or a figure written through the one would be invisible to the other.
 *
 * <p>The overlay carried its own copy of the path arithmetic while the runtime of the typed
 * view was a package nobody outside it could call. It is a published package now, the
 * overlay calls it, and this test is what says the move changed no path: the arithmetic
 * over the shapes the overlay uses, and the paths the generated editor actually writes at.
 */
class OverlayPathsTest {

    /** The one core group the B2C extension hangs terms under. */
    private static final String GROUP = "BG-25";

    @Test
    void theArithmeticBuildsThePathsTheSpecificationWrites() {
        SemanticPath line = TermPaths.indexed(TermPaths.group(SemanticPath.root(), GROUP), 0);

        assertEquals("/BG-25/0", line.toString(), "one occurrence of a repeatable group");
        assertEquals("/BG-25/0/BT-B2C-001",
                TermPaths.value(line, "BT-B2C-001").toString(), "a term inside that instance");
        assertEquals("/BT-B2C-010",
                TermPaths.value(SemanticPath.root(), "BT-B2C-010").toString(),
                "a term at the root of the document");
        assertEquals("/BG-25/7",
                TermPaths.indexed(TermPaths.group(SemanticPath.root(), GROUP), 7).toString(),
                "the eighth occurrence, and the index is zero-based");
    }

    @Test
    void theOverlayWritesWhereTheTypedViewOfTheCoreModelReads() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        Gross.on(invoice).line(0).displayedGrossUnitPrice(new BigDecimal("99.99"));
        Gross.on(invoice).displayedGrossTotal(new BigDecimal("99.99"));
        SemanticDocument document = invoice.document();

        SemanticPath line = TermPaths.indexed(TermPaths.group(SemanticPath.root(), GROUP), 0);
        assertEquals(List.of("99.99", "99.99"),
                List.of(value(document, TermPaths.value(line, "BT-B2C-001")),
                        value(document, TermPaths.value(SemanticPath.root(), "BT-B2C-010"))),
                "the overlay wrote at the paths the arithmetic of the typed runtime builds");
        assertEquals(new BigDecimal("99.99"),
                B2c.of(document).lines().get(0).displayedGrossUnitPrice().orElseThrow(),
                "and reads them back through the overlay");
        assertEquals("1", value(document, TermPaths.value(line, "BT-129")),
                "the typed editor of the core model wrote the invoiced quantity of that line"
                        + " under the very same group instance path");
    }

    /** Returns what the document carries at a path, as the document spells it. */
    private static String value(SemanticDocument document, SemanticPath path) {
        return document.value(path).orElseThrow().asString();
    }
}
