package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The files of the Peppol BIS Billing 3.0 repository a network test fetches, as
 * {@code conformance/peppol/README.md} lists them, and the fetch itself.
 *
 * <p>No file of OpenPeppol is in this repository. A test that needs one asks for it here,
 * which skips the test unless {@code -Desj.network=true} was given, fetches the file from the
 * tag the recipe pins, and weighs it against the digest the README records.
 */
final class PeppolEvidence {

    /** The system property that lets a test reach the network. */
    static final String PROPERTY = "esj.network";

    /** The recipe the pack is made from. */
    static final String RECIPE = "peppol-bis-billing-3.0.20";

    /** One line of the checksum block: a lowercase digest, two spaces, a relative path. */
    private static final Pattern CHECKSUM = Pattern.compile("^([0-9a-f]{64})  (\\S.*)$");

    private static final Map<String, byte[]> FETCHED = new ConcurrentHashMap<>();

    private static final PackFetcher.Download DOWNLOAD =
            PackFetcher.https("esj-test", Duration.ofSeconds(60));

    private PeppolEvidence() {
        throw new AssertionError("no instances");
    }

    /** Skips the calling test unless the network was allowed. */
    static void assumeNetwork() {
        assumeTrue(Boolean.getBoolean(PROPERTY), "skipped: this test fetches the Peppol BIS"
                + " Billing 3.0 artefacts and OpenPeppol's own tests at test time; run the"
                + " build with -D" + PROPERTY + "=true to include it");
    }

    /** Returns the download a test fetches over. */
    static PackFetcher.Download download() {
        return DOWNLOAD;
    }

    /** Returns the files the README lists, path to SHA-256, in path order. */
    static Map<String, String> files() {
        Map<String, String> files = new TreeMap<>();
        for (String line : Corpus.text("/conformance/peppol/README.md").split("\\R")) {
            Matcher matcher = CHECKSUM.matcher(line);
            if (matcher.matches()) {
                files.put(matcher.group(2), matcher.group(1));
            }
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("conformance/peppol/README.md lists no file");
        }
        return files;
    }

    /** Fetches one listed file and weighs it against its digest. */
    static byte[] fetch(String path) {
        String digest = files().get(path);
        assertNotNull(digest, "conformance/peppol/README.md lists " + path);
        return FETCHED.computeIfAbsent(path, key -> {
            PackRecipe recipe = PackRecipes.named(RECIPE);
            URI url = recipe.url(key);
            try {
                byte[] bytes = DOWNLOAD.get(url, 8L * 1024 * 1024);
                assertEquals(digest, PackFetcher.sha256(bytes),
                        url + " is the file conformance/peppol/README.md records");
                return bytes;
            } catch (IOException e) {
                throw new UncheckedIOException("could not fetch " + url, e);
            }
        });
    }
}
