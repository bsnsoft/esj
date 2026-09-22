package de.bsnsoft.esj.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.Fixtures;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticValue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the canonical bytes and the two digests of the specification, sections 7 and 8. */
class CanonicalizerTest {

    /** The worked example of the specification, Appendix B, in pretty form. */
    private static final String APPENDIX_B = """
            {
              "format": "EN16931-Semantic-JSON",
              "version": "0.1",
              "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
              "values": {
                "/BT-1": "RE-2026-0001",
                "/BT-2": "2026-01-15",
                "/BG-4/BT-29/0": { "value": "0088123456785", "scheme": "0088" },
                "/BG-25/0/BT-131": "100"
              }
            }
            """;

    private static final String CANONICAL =
            "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                    + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{"
                    + "\"/BT-1\":\"RE-2026-0001\","
                    + "\"/BT-2\":\"2026-01-15\","
                    + "\"/BG-4/BT-29/0\":{\"value\":\"0088123456785\",\"scheme\":\"0088\"},"
                    + "\"/BG-25/0/BT-131\":\"100\"}}";

    private static final String SEMANTIC =
            "{\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{"
                    + "\"/BT-1\":\"RE-2026-0001\","
                    + "\"/BT-2\":\"2026-01-15\","
                    + "\"/BG-4/BT-29/0\":{\"value\":\"0088123456785\",\"scheme\":\"0088\"},"
                    + "\"/BG-25/0/BT-131\":\"100\"}}";

    /** The second worked example of the specification, Appendix B, in pretty form. */
    private static final String APPENDIX_B_2026 = """
            {
              "format": "EN16931-Semantic-JSON",
              "version": "0.1",
              "semanticModel": "EN16931-1:2026",
              "values": {
                "/BT-1": "RE-2026-0001",
                "/BT-2": "2026-01-15",
                "/BT-166": "09:30:00+02:00",
                "/BG-4/BT-29/0": { "value": "0088123456785", "scheme": "0088" },
                "/BG-25/0/BT-131": "100"
              }
            }
            """;

    private static final String CANONICAL_2026 =
            "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                    + "\"semanticModel\":\"EN16931-1:2026\",\"values\":{"
                    + "\"/BT-1\":\"RE-2026-0001\","
                    + "\"/BT-2\":\"2026-01-15\","
                    + "\"/BT-166\":\"09:30:00+02:00\","
                    + "\"/BG-4/BT-29/0\":{\"value\":\"0088123456785\",\"scheme\":\"0088\"},"
                    + "\"/BG-25/0/BT-131\":\"100\"}}";

    private static SemanticDocument appendixB2026() {
        return appendixB().toBuilder()
                .semanticModel("EN16931-1:2026")
                .put("/BT-166", "09:30:00+02:00")
                .build();
    }

    private static SemanticDocument appendixB() {
        return SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0001")
                .put("/BT-2", "2026-01-15")
                .put("/BG-4/BT-29/0", SemanticValue.identifier("0088123456785", "0088"))
                .put("/BG-25/0/BT-131", "100")
                .build();
    }

    @Test
    void theWorkedExampleOfTheSpecificationIsReproducedByteForByte() {
        byte[] canonical = Canonicalizer.canonicalBytes(appendixB());

        assertEquals(CANONICAL, new String(canonical, StandardCharsets.UTF_8));
        assertEquals(236, canonical.length);
        assertArrayEquals(canonical,
                Canonicalizer.canonicalize(APPENDIX_B.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theDigestsOfTheWorkedExampleAreTheOnesTheSpecificationPrints() {
        assertEquals("27d43ab507a929218e8278be315a736467d9a5f881bf232fe1843b06bd6105b8",
                Canonicalizer.semanticDigest(appendixB()));
        assertEquals("fdc75e42190b6aec5cf5454fe07e615af71f33fc275e297cb6aeefec0525e42a",
                Canonicalizer.documentDigest(appendixB()));
    }

    @Test
    void theSemanticDigestCoversTheEditionAndTheValues() {
        byte[] semantic = Canonicalizer.canonicalSemanticBytes(appendixB());

        assertEquals(SEMANTIC, new String(semantic, StandardCharsets.UTF_8));
        assertEquals(187, semantic.length);
    }

    @Test
    void theSemanticDigestDoesNotMoveWhenTheProvenanceOrAnExtensionChanges() {
        SemanticDocument plain = appendixB();
        SemanticDocument annotated = plain.toBuilder()
                .source("CII", "b".repeat(64))
                .extension("de.example.vendor", ExtensionValue.of("x"))
                .build();

        assertEquals(Canonicalizer.semanticDigest(plain), Canonicalizer.semanticDigest(annotated));
        assertNotEquals(Canonicalizer.documentDigest(plain),
                Canonicalizer.documentDigest(annotated));
    }

    /**
     * The reason the edition is inside the semantic digest (specification, section 8.2): a
     * path is an address relative to an edition, so the same strings under two editions are
     * not the same invoice and must not share a deduplication key.
     */
    @Test
    void theSemanticDigestMovesWhenTheEditionChanges() {
        SemanticDocument later = appendixB().toBuilder()
                .semanticModel("EN16931-1:2026")
                .build();

        assertNotEquals(Canonicalizer.semanticDigest(appendixB()),
                Canonicalizer.semanticDigest(later));
    }

    /**
     * The second worked example of Appendix B: the same four values under the 2026 edition
     * and the invoice issue time that edition adds. Nothing here needs a registry — a
     * canonicalizer is edition-blind (specification, section 3.4) — so the bytes and the
     * digests the text prints are reproduced whether or not this build carries the registry
     * of that edition.
     */
    @Test
    void theSecondWorkedExampleOfTheSpecificationIsReproducedByteForByte() {
        byte[] canonical = Canonicalizer.canonicalBytes(appendixB2026());

        assertEquals(CANONICAL_2026, new String(canonical, StandardCharsets.UTF_8));
        assertEquals(247, canonical.length);
        assertArrayEquals(canonical,
                Canonicalizer.canonicalize(APPENDIX_B_2026.getBytes(StandardCharsets.UTF_8)));
        assertEquals(198, Canonicalizer.canonicalSemanticBytes(appendixB2026()).length);
        assertEquals("8f12f3ea4a038fa8877b9b8084793c0ff4d90190dfe6897b4a44b04d464f2d1c",
                Canonicalizer.semanticDigest(appendixB2026()));
        assertEquals("38b3abe5cfa1d8f705844b7169b56d8aecd35ffbd9ae65ec0aaa83d4c8b7b10f",
                Canonicalizer.documentDigest(appendixB2026()));
        assertNotEquals(Canonicalizer.semanticDigest(appendixB()),
                Canonicalizer.semanticDigest(appendixB2026()));
    }

    @Test
    void theTwoExamplesThatShareTheirValuesShareTheirSemanticDigest() {
        EsjReader reader = EsjReader.strict();
        SemanticDocument minimal = reader.read(Examples.pretty("minimal"));
        SemanticDocument extended = reader.read(Examples.pretty("extended"));

        assertEquals(Canonicalizer.semanticDigest(minimal), Canonicalizer.semanticDigest(extended));
        assertNotEquals(Canonicalizer.documentDigest(minimal),
                Canonicalizer.documentDigest(extended));
    }

    @Test
    void canonicalizingIsStableAcrossRuns() {
        SemanticDocument document = Fixtures.minimalInvoice().build();

        assertArrayEquals(Canonicalizer.canonicalBytes(document),
                Canonicalizer.canonicalBytes(document));
        assertEquals(Canonicalizer.documentDigest(document),
                Canonicalizer.documentDigest(document));
    }

    /**
     * The canonicalizer has no registry and never rewrites content (specification,
     * section 3.4): a decimal spelled some other way passes through it unchanged and is
     * refused by the validator, not repaired here.
     */
    @Test
    void aDecimalSpelledSomeOtherWayPassesThroughUnchanged() {
        String document = "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\","
                + "\"values\":{\"/BG-22/BT-106\":\"100.00\"}}";

        assertEquals(document, new String(
                Canonicalizer.canonicalize(document.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8));
    }

    @Test
    void neitherTheMemberOrderNorTheWhitespaceOfTheInputReachesTheCanonicalBytes() {
        String shuffled = """
                {
                  "values" : {
                    "/BG-25/0/BT-131" : "100",
                    "/BG-4/BT-29/0" : { "scheme" : "0088", "value" : "0088123456785" },
                    "/BT-2" : "2026-01-15",
                    "/BT-1" : "RE-2026-0001"
                  },
                  "semanticModel" : "EN16931-1:2017+A1:2019/AC:2020",
                  "version" : "0.1",
                  "format" : "EN16931-Semantic-JSON"
                }
                """;

        assertArrayEquals(Canonicalizer.canonicalBytes(appendixB()),
                Canonicalizer.canonicalize(shuffled.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void anExtensionTreeBuiltInCodeIsWrittenWithoutOverflowingTheStack() {
        int levels = 50_000;
        ExtensionValue deep = ExtensionValue.of("leaf");
        for (int i = 0; i < levels - 1; i++) {
            deep = ExtensionValue.array(List.of(deep));
        }
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0001")
                .extension("de.example.vendor", ExtensionValue.array(List.of(deep)))
                .build();

        byte[] canonical = Canonicalizer.canonicalBytes(document);
        String text = new String(canonical, StandardCharsets.UTF_8);
        assertEquals(levels, text.chars().filter(c -> c == '[').count());
        assertEquals(64, Canonicalizer.documentDigest(document).length());
    }

    @Test
    void theStreamFormCanonicalizesLikeTheByteForm() {
        byte[] pretty = Examples.pretty("standard-invoice");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Canonicalizer.canonicalize(new ByteArrayInputStream(pretty), out, Limits.defaults());

        assertArrayEquals(Canonicalizer.canonicalize(pretty), out.toByteArray());
    }
}
