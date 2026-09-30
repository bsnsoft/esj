package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The vendored Schematron skeleton is the file its README records, byte for byte, and
 * nothing the compiler resolves is missing from it.
 */
class SchematronSkeletonTest {

    private static final String DIRECTORY = "/de/bsnsoft/esj/syntax/schematron/";

    /** One row of the digest table: a file name in backticks and its digest. */
    private static final Pattern ROW = Pattern.compile(
            "^\\| `([^`]+)` \\| `([0-9a-f]{64})` \\|$", Pattern.MULTILINE);

    @Test
    void everyFileHasTheDigestItsReadmeRecords() {
        Map<String, String> recorded = new TreeMap<>();
        Matcher row = ROW.matcher(Corpus.text(DIRECTORY + "README.md"));
        while (row.find()) {
            recorded.put(row.group(1), row.group(2));
        }
        TreeSet<String> expected = new TreeSet<>(SchematronCompiler.FILES);
        expected.add("LICENSE");
        assertEquals(expected, recorded.keySet(),
                "the README records the four stylesheets and the licence");
        recorded.forEach((file, digest) -> assertEquals(digest,
                PackFetcher.sha256(Corpus.bytes(DIRECTORY + file)), file));
    }

    @Test
    void everyFileCarriesTheMitNotice() {
        for (String file : SchematronCompiler.FILES) {
            String text = Corpus.text(DIRECTORY + file);
            assertTrue(text.contains("The MIT License")
                    && text.contains("Permission is hereby granted, free of charge"), file);
        }
    }
}
