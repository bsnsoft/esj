package de.bsnsoft.esj.typed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.typed.build.InvoiceBuilder;
import de.bsnsoft.esj.typed.build.Profile;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The format version of what the editor and the builder write (specification, section 4.3): a
 * document opened for editing keeps the version it was read in, through every setter and the
 * derivation of its totals, and a document begun here carries the version of this
 * implementation.
 */
class FormatVersionTest {

    @Test
    void aDocumentBegunHereCarriesTheVersionOfThisImplementation() {
        assertEquals(Esj.formatVersion(), En16931.newInvoice().document().formatVersion());
        assertEquals(Esj.formatVersion(),
                InvoiceBuilder.draft(Profile.EN16931).document().formatVersion());
        assertEquals("1.0", Esj.formatVersion());
    }

    @Test
    void anEditedDocumentKeepsTheVersionItWasReadIn() {
        SemanticDocument earlier = Examples.document("standard-invoice").toBuilder()
                .formatVersion("0.1")
                .build();

        InvoiceEditor invoice = En16931.edit(earlier);
        invoice.buyerReference("KOST-4712");
        invoice.derive(Totals.STANDARD);
        SemanticDocument changed = invoice.document();

        assertEquals("0.1", changed.formatVersion());
        assertEquals("0.1", En16931.edit(earlier).document().formatVersion());
        assertEquals(Canonicalizer.semanticDigest(Examples.document("standard-invoice")),
                Canonicalizer.semanticDigest(En16931.edit(earlier).document()));
        assertArrayEquals(new String(Examples.canonical("standard-invoice"), StandardCharsets.UTF_8)
                        .replace("\"version\":\"1.0\"", "\"version\":\"0.1\"")
                        .getBytes(StandardCharsets.UTF_8),
                EsjWriter.canonical().toBytes(En16931.edit(earlier).document()),
                "an editor that changes nothing writes the 0.1 document it was given");
    }
}
