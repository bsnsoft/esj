package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Checks that the vendored stylesheets are the files the README beside them describes.
 * The claim that they are unmodified copies of a tagged release is only worth something
 * while a digest backs it.
 */
class VendoredStylesheetsTest {

    private static final String DIRECTORY = "/de/bsnsoft/esj/xr/kosit/";

    private static final Map<String, String> DIGESTS = digests();

    private static Map<String, String> digests() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("cii-xr.xsl",
                "216c591ef7bd887e2f89d5623bd20fbf73a9b48ae51f93fdbb0e46d1645f63e3");
        expected.put("common-xr.xsl",
                "3155e48fe19dd23ff74e3f191721e28c7a51a5e76a093fb1e2ee60bfe08edc41");
        expected.put("functions.xsl",
                "1e8e2c414a19d63007408856649037c8654f5c7391618c48010865dac60db3ed");
        expected.put("ubl-creditnote-xr.xsl",
                "8cf629bd45887447d605b442201bad5f14dff118133b358adb4468c5fd31cf89");
        expected.put("ubl-invoice-xr.xsl",
                "ef2c2af5efbc5e130a4fe4b67c62080ff179a67df08522dbf9cfcf305093c899");
        expected.put("LICENSE",
                "e0d7665e91531aebb79e4feaa415796f076b0f93e9eddd6d1d05efe9d93808ac");
        return Map.copyOf(expected);
    }

    @Test
    void shipsTheFilesTheReadmeDescribes() {
        DIGESTS.forEach((name, digest) ->
                assertEquals(digest, sha256(Instances.bytes(DIRECTORY + name)),
                        "the shipped " + name + " is the one the README records"));
    }

    @Test
    void recordsEveryDigestInTheReadme() {
        String readme = Instances.text(DIRECTORY + "README.md");

        DIGESTS.forEach((name, digest) -> {
            assertTrue(readme.contains(name), "the README names " + name);
            assertTrue(readme.contains(digest), "the README records the digest of " + name);
        });
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
