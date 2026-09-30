package de.bsnsoft.esj.syntax;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

/**
 * Packs and recipes for the tests of pack directories and of {@code esj packs fetch}, made
 * of this project's own files and nothing fetched.
 *
 * <p>The recipe is a real recipe in every respect but where it fetches from: it names one
 * Schematron schema written for these tests, pins its digest, copies the UBL schema
 * component out of the bundled pack and is fetched through a download that answers from
 * memory. The pack it makes recognizes {@link #PROFILE}, which no bundled pack knows.
 */
final class ExamplePacks {

    /** The customization identifier of the example profile. */
    static final String PROFILE = "urn:cen.eu:en16931:2017#compliant#urn:esj.example:test:1.0";

    /** The customization identifier of the XRechnung 3.0 profile the corpus invoice names. */
    static final String XRECHNUNG =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    /** Where the example recipe fetches below; nothing answers there but the test. */
    static final String BASE = "https://files.example.test/rules/v1/";

    /** The path of the example schema in the "repository" the recipe fetches from. */
    static final String FILE = "sch/example-ubl.sch";

    /** The first invoice of the corpus, in UBL. */
    private static final String INVOICE =
            "/conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml";

    /** The day the tests fetch on. */
    static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    private ExamplePacks() {
        throw new AssertionError("no instances");
    }

    /** Returns the example schema. */
    static byte[] schematron() {
        return Corpus.bytes("/de/bsnsoft/esj/syntax/schematron/example-ubl.sch");
    }

    /** Returns the first invoice of the corpus, naming a profile of the caller's choice. */
    static byte[] invoice(String profile) {
        String xml = Corpus.text(INVOICE);
        if (!xml.contains(XRECHNUNG)) {
            throw new IllegalStateException(INVOICE + " names the XRechnung profile");
        }
        return xml.replace(XRECHNUNG, profile).getBytes(StandardCharsets.UTF_8);
    }

    /** Returns the example recipe, pinning the given schema. */
    static PackRecipe recipe(byte[] schematron) {
        return recipe(schematron, "1.0", "2026-09-30");
    }

    /** Returns the example recipe for one release, pinning the given schema. */
    static PackRecipe recipe(byte[] schematron, String version, String release) {
        String json = """
                {
                  "format": "esj-pack-recipe",
                  "formatVersion": "1",
                  "name": "example-%2$s",
                  "title": "Example rules, release %2$s",
                  "note": "Written for the tests of esj-syntax.",
                  "pack": { "id": "example", "version": "%1$s", "release": "%2$s" },
                  "source": {
                    "publisher": "BSNSoft Solutions GmbH",
                    "repository": "https://example.test/rules",
                    "tag": "v1",
                    "commit": "0000000000000000000000000000000000000000",
                    "release": "Example release",
                    "published": "2026-09-30",
                    "base": "%3$s"
                  },
                  "baseProfiles": [ "urn:cen.eu:en16931:2017" ],
                  "copy": [ { "pack": "xrechnung/3.0.2/2026-08-31", "component": "ubl-2.1-xsd" } ],
                  "schematron": [
                    {
                      "name": "example-ubl-schematron",
                      "file": "%4$s",
                      "bytes": %5$d,
                      "sha256": "%6$s",
                      "syntax": [ "ubl-invoice", "ubl-creditnote" ],
                      "profile": [ "%7$s" ],
                      "license": "Apache-2.0",
                      "about": "the rules of the example profile",
                      "directory": "example/%2$s"
                    }
                  ]
                }
                """.formatted(version, release, BASE, FILE, schematron.length,
                PackFetcher.sha256(schematron), PROFILE);
        return PackRecipe.read(json.getBytes(StandardCharsets.UTF_8), "example recipe");
    }

    /** Returns a download that answers the example files from memory and nothing else. */
    static PackFetcher.Download serving(Map<String, byte[]> files) {
        return (url, maxBytes) -> {
            byte[] bytes = files.get(url.toString());
            if (bytes == null) {
                throw new IOException(url + " answered with HTTP status 404");
            }
            if (bytes.length > maxBytes) {
                throw new IOException(url + " is longer than the " + maxBytes
                        + " bytes the recipe gives it");
            }
            return bytes;
        };
    }

    /** Returns the URL of the example schema. */
    static String url() {
        return URI.create(BASE).resolve(FILE).toString();
    }

    /** Fetches the example recipe into a pack directory and returns the result. */
    static PackFetcher.Result fetch(Path into) {
        byte[] schematron = schematron();
        return PackFetcher.fetch(recipe(schematron), into, false,
                serving(Map.of(url(), schematron)), TODAY, "esj test");
    }
}
