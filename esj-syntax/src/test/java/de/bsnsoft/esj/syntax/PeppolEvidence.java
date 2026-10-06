package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
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
 * tag or commit the recipe pins, and weighs it against the digest the README records for
 * that recipe: the README lists the files of each release under a heading that names its
 * recipe.
 */
final class PeppolEvidence {

    /** The system property that lets a test reach the network. */
    static final String PROPERTY = "esj.network";

    /** The recipes whose packs are measured, oldest release first. */
    static final List<String> RECIPES = List.of("peppol-bis-billing-3.0.20",
            "peppol-bis-billing-3.0.21");

    /** The heading of the README under which the files of one recipe are listed. */
    private static final Pattern HEADING = Pattern.compile("^### `([a-z0-9.-]+)`$");

    /** One line of the checksum block: a lowercase digest, two spaces, a relative path. */
    private static final Pattern CHECKSUM = Pattern.compile("^([0-9a-f]{64})  (\\S.*)$");

    /** What was fetched, by recipe and path. */
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

    /**
     * Returns the files the README lists for one recipe, path to SHA-256, in path order.
     */
    static Map<String, String> files(String recipe) {
        Map<String, String> files = new TreeMap<>();
        String current = null;
        for (String line : Corpus.text("/conformance/peppol/README.md").split("\\R")) {
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                current = heading.group(1);
                continue;
            }
            Matcher matcher = CHECKSUM.matcher(line);
            if (matcher.matches() && recipe.equals(current)) {
                files.put(matcher.group(2), matcher.group(1));
            }
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("conformance/peppol/README.md lists no file for "
                    + recipe);
        }
        return files;
    }

    /** Fetches one listed file of a recipe's release and weighs it against its digest. */
    static byte[] fetch(String recipe, String path) {
        String digest = files(recipe).get(path);
        assertNotNull(digest, "conformance/peppol/README.md lists " + path + " for " + recipe);
        String key = recipe + " " + path;
        byte[] cached = FETCHED.get(key);
        if (cached != null) {
            return cached;
        }
        // Fetched outside the map, so that requests for different files run side by side.
        URI url = PackRecipes.named(recipe).url(path);
        byte[] bytes;
        try {
            bytes = DOWNLOAD.get(url, 8L * 1024 * 1024);
        } catch (IOException e) {
            throw new UncheckedIOException("could not fetch " + url, e);
        }
        assertEquals(digest, PackFetcher.sha256(bytes),
                url + " is the file conformance/peppol/README.md records");
        FETCHED.putIfAbsent(key, bytes);
        return FETCHED.get(key);
    }
}
