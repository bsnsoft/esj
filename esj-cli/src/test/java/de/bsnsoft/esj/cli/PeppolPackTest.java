package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.bsnsoft.esj.syntax.PackFetcher;
import de.bsnsoft.esj.syntax.PackRecipes;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Peppol BIS Billing 3.0 pack end to end, through the command line: {@code esj packs
 * fetch} over the network, then {@code esj validate --packs} over OpenPeppol's own examples
 * and over documents made from one of them by one change each.
 *
 * <p>It runs only with {@code -Desj.network=true}: nothing of OpenPeppol is in this
 * repository, so the pack and the documents are fetched at test time, each document weighed
 * against the digest {@code conformance/peppol/README.md} records for it. Without the
 * property the test is skipped with a message naming it, and the build stays offline.
 */
class PeppolPackTest {

    private static final String PROPERTY = "esj.network";

    private static final String RECIPE = "peppol-bis-billing-3.0.20";

    private static final String IDENTITY = "peppol-bis-billing/3.0/3.0.20";

    private static final String PEPPOL =
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0";

    /** One line of the checksum block: a lowercase digest, two spaces, a relative path. */
    private static final Pattern CHECKSUM = Pattern.compile("^([0-9a-f]{64})  (\\S.*)$");

    /** A rule identifier as the text report prints a finding. */
    private static final Pattern FINDING = Pattern.compile("^ {4}(\\S+) \\[fatal\\]");

    @TempDir
    Path directory;

    @Test
    void fetchesThePackAndJudgesOpenPeppolsExamplesAndThreeBrokenDocuments() throws Exception {
        assumeTrue(Boolean.getBoolean(PROPERTY), "skipped: this test fetches the Peppol BIS"
                + " Billing 3.0 artefacts and OpenPeppol's examples at test time; run the"
                + " build with -D" + PROPERTY + "=true to include it");
        Path packs = directory.resolve("packs");
        StringBuilder ledger = new StringBuilder();

        long start = System.nanoTime();
        Cli.Run fetched = Cli.run("packs", "fetch", RECIPE, "--into", packs.toString());
        Duration fetchTime = Duration.ofNanos(System.nanoTime() - start);
        assertEquals(ExitCode.SUCCESS, fetched.exitCode(), fetched.text() + fetched.err());
        assertTrue(fetched.text().startsWith(IDENTITY + " written to "), fetched.text());
        ledger.append("esj packs fetch ").append(fetchTime.toMillis()).append(" ms\n");

        Cli.Run again = Cli.run("packs", "fetch", RECIPE, "--into", packs.toString());
        assertEquals(ExitCode.SUCCESS, again.exitCode(), again.err());
        assertTrue(again.text().startsWith(IDENTITY + " is already, unchanged, in "),
                again.text());

        Cli.Run listed = Cli.run("packs", "list", "--packs", packs.toString());
        assertTrue(listed.text().contains(IDENTITY + System.lineSeparator()), listed.text());
        assertTrue(listed.text().contains("peppol-ubl-schematron (schematron-xslt,"
                + " LicenseRef-OpenPeppol)"), listed.text());
        assertTrue(listed.text().contains("; in a pack directory above"), listed.text());

        Map<String, Integer> verdicts = new TreeMap<>();
        for (String example : files().keySet()) {
            if (!example.startsWith("rules/examples/")) {
                continue;
            }
            Path file = write(example, fetch(example));
            long run = System.nanoTime();
            Cli.Run validated = Cli.run("validate", "--packs", packs.toString(),
                    file.toString());
            ledger.append(example).append(": exit ").append(validated.exitCode()).append(", ")
                    .append((System.nanoTime() - run) / 1_000_000).append(" ms\n");
            assertTrue(validated.text().contains("(pack " + IDENTITY + ", from "),
                    example + " is judged by the Peppol pack: " + validated.text());
            verdicts.put(example, validated.exitCode());
            assertEquals(ExitCode.SUCCESS, validated.exitCode(),
                    example + " is valid by its publisher's rules and by this tool's:\n"
                            + validated.text() + validated.err());
        }
        assertEquals(9, verdicts.size(), "every example of the release was run");

        String base = new String(fetch("rules/examples/base-example.xml"),
                StandardCharsets.UTF_8);
        Map<String, String> mutations = new LinkedHashMap<>();
        mutations.put("no invoice number (BT-1)",
                once(base, "<cbc:ID>Snippet1</cbc:ID>", "<cbc:ID></cbc:ID>"));
        mutations.put("VAT category code X on the line",
                once(base, "<cac:ClassifiedTaxCategory>\n                <cbc:ID>S</cbc:ID>",
                        "<cac:ClassifiedTaxCategory>\n                <cbc:ID>X</cbc:ID>"));
        mutations.put("a business process no Peppol process names (BT-23)",
                once(base, "<cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
                        "<cbc:ProfileID>urn:example:process"));
        // What fires, from the official artefacts and the native rules together: an
        // identifier both engines report is listed once.
        Map<String, List<String>> expected = Map.of(
                "no invoice number (BT-1)", List.of("BR-02", "PEPPOL-EN16931-R008"),
                "VAT category code X on the line", List.of("BR-CL-18", "BR-S-08"),
                "a business process no Peppol process names (BT-23)",
                List.of("PEPPOL-EN16931-R007"));
        int index = 0;
        for (Map.Entry<String, String> mutation : mutations.entrySet()) {
            Path file = directory.resolve("mutation-" + ++index + ".xml");
            Files.writeString(file, mutation.getValue(), StandardCharsets.UTF_8);
            Cli.Run validated = Cli.run("validate", "--packs", packs.toString(),
                    file.toString());
            List<String> fatal = fatal(validated.text());
            ledger.append(mutation.getKey()).append(": exit ").append(validated.exitCode())
                    .append(", fatal ").append(fatal).append('\n');
            assertEquals(ExitCode.VALIDATION, validated.exitCode(), validated.text());
            assertEquals(expected.get(mutation.getKey()),
                    fatal.stream().distinct().sorted().toList(),
                    mutation.getKey() + ": " + validated.text());
        }

        String unknown = once(base, "<cbc:CustomizationID>" + PEPPOL,
                "<cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:example:cius:1.0");
        Path unknownFile = directory.resolve("mutation-unknown-cius.xml");
        Files.writeString(unknownFile, unknown, StandardCharsets.UTF_8);
        Cli.Run automatic = Cli.run("validate", "--packs", packs.toString(),
                unknownFile.toString());
        Cli.Run named = Cli.run("validate", "--packs", packs.toString(), "--pack", IDENTITY,
                unknownFile.toString());
        ledger.append("a specification identifier no pack knows (BT-24): exit ")
                .append(automatic.exitCode()).append(" (")
                .append(automatic.text().contains("(pack xrechnung/3.0.2/2026-08-31)")
                        ? "bundled pack" : "?")
                .append("), with --pack ").append(IDENTITY).append(": exit ")
                .append(named.exitCode()).append('\n');
        assertEquals(ExitCode.INDETERMINATE, automatic.exitCode(), automatic.text());
        assertEquals(ExitCode.INDETERMINATE, named.exitCode(), named.text());
        assertTrue(named.text().matches("(?s).*  Peppol UBL Schematron: +skipped: it"
                + " validates no document of the profile this one names\\R.*"), named.text());

        Path out = Path.of("target", "peppol-evidence");
        Files.createDirectories(out);
        Files.writeString(out.resolve("cli.txt"), ledger.toString(), StandardCharsets.UTF_8);
        System.out.println(ledger);
    }

    /** Returns the rule identifiers of the fatal findings a text report lists. */
    private static List<String> fatal(String text) {
        List<String> codes = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = FINDING.matcher(line);
            if (matcher.find()) {
                codes.add(matcher.group(1));
            }
        }
        return codes;
    }

    /** Replaces the one occurrence of a text, failing where there is not exactly one. */
    private static String once(String text, String find, String replace) {
        int first = text.indexOf(find);
        assertTrue(first >= 0 && text.indexOf(find, first + 1) < 0,
                "base-example.xml carries " + find + " once");
        return text.replace(find, replace);
    }

    private Path write(String path, byte[] bytes) throws IOException {
        Path file = directory.resolve(path.substring(path.lastIndexOf('/') + 1));
        Files.write(file, bytes);
        return file;
    }

    /** Returns the files {@code conformance/peppol/README.md} lists, path to SHA-256. */
    private static Map<String, String> files() {
        Map<String, String> files = new TreeMap<>();
        for (String line : Fixtures.text("conformance/peppol/README.md").split("\\R")) {
            Matcher matcher = CHECKSUM.matcher(line);
            if (matcher.matches()) {
                files.put(matcher.group(2), matcher.group(1));
            }
        }
        return files;
    }

    /** Fetches one listed file from the tag of the recipe and weighs it. */
    private static byte[] fetch(String path) throws IOException {
        String digest = files().get(path);
        assertNotNull(digest, "conformance/peppol/README.md lists " + path);
        URI url = PackRecipes.named(RECIPE).url(path);
        byte[] bytes = PackFetcher.https("esj-test", Duration.ofSeconds(60))
                .get(url, 8L * 1024 * 1024);
        assertEquals(digest, sha256(bytes), url + " is the file the README records");
        return bytes;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
