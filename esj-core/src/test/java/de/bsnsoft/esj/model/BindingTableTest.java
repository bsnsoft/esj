package de.bsnsoft.esj.model;

import static de.bsnsoft.esj.model.JsonTree.list;
import static de.bsnsoft.esj.model.JsonTree.map;
import static de.bsnsoft.esj.model.JsonTree.number;
import static de.bsnsoft.esj.model.JsonTree.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the binding tables of {@code model/bindings/} against the registry files, the
 * schema beside them, the flag table of {@code model/bindings/README.md} and the
 * cross-check report the tables are measured by.
 *
 * <p>The tables are generated, so what is checked here is not that a human kept them
 * right but that the generated files, the two documents that describe them and the
 * registry they bind still say the same thing.
 */
class BindingTableTest {

    /** The three tables, in the order {@code model/bindings/README.md} lists them. */
    private static final List<String> TABLES =
            List.of("ubl-invoice.json", "ubl-creditnote.json", "cii.json");

    /** The column of the README flag table each file's counts stand in. */
    private static final Map<String, Integer> COLUMN =
            Map.of("ubl-invoice.json", 0, "ubl-creditnote.json", 1, "cii.json", 2);

    /** A prefixed name inside a path, so that the namespace map can be checked. */
    private static final Pattern PREFIX =
            Pattern.compile("(?<![\\w-])([A-Za-z_][\\w.-]*):(?=[A-Za-z_])");

    /** A row of the flag table of the README: flag, meaning, then three counts. */
    private static final Pattern FLAG_ROW = Pattern.compile(
            "^\\| `([^`]+)` \\| (.+?) \\| (\\d+) \\| (\\d+) \\| (\\d+) \\|$");

    /** The sentence of the README that counts the corrections of the three tables. */
    private static final Pattern CORRECTION_COUNT =
            Pattern.compile("^([\\w-]+) corrections stand today, and all but ([\\w-]+) of"
                    + " them");

    /**
     * The number words the correction sentence may use, indexed by the number. It reaches
     * as far as a release that had to depart from its source on every second term, which
     * is further than anything this project expects to write.
     */
    private static final List<String> NUMBERS = List.of(
            "No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen", "Twenty", "Twenty-one", "Twenty-two",
            "Twenty-three", "Twenty-four", "Twenty-five", "Twenty-six", "Twenty-seven",
            "Twenty-eight", "Twenty-nine", "Thirty", "Thirty-one", "Thirty-two",
            "Thirty-three", "Thirty-four", "Thirty-five", "Thirty-six", "Thirty-seven",
            "Thirty-eight", "Thirty-nine", "Forty");

    private static Schema schema;

    @BeforeAll
    static void loadTheSchema() {
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(resource("/model/bindings/binding.schema.json"), InputFormat.JSON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ubl-invoice.json", "ubl-creditnote.json", "cii.json"})
    void aTableValidatesAgainstTheBindingSchema(String name) {
        assertEquals(List.of(), schema.validate(source(name), InputFormat.JSON), name);
    }

    @Test
    void aTableThatLosesTheXPathOfABoundTermFailsTheSchema() {
        String broken = source("cii.json")
                .replaceFirst("\"bound\": true,\\s*\"xpath\": \"[^\"]*\",", "\"bound\": true,");
        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "a bound entry without a path is not a binding");
    }

    @Test
    void aTableThatGivesAnUnboundTermAPathFailsTheSchema() {
        String broken = source("ubl-invoice.json").replaceFirst(
                "\"bound\": false,", "\"bound\": false, \"xpath\": \"/Invoice/cbc:ID\",");
        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "an entry that says the syntax has no place for the term carries no path");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ubl-invoice.json", "ubl-creditnote.json", "cii.json"})
    void aTableBindsExactlyTheTermsOfTheRegistries(String name) {
        Object table = JsonTree.of(source(name));
        assertEquals(identifiers("/model/en16931/2017.json"), identifiers(table, "terms"),
                name + " binds the core registry, in its order");
        assertEquals(
                identifiers("/model/xrechnung/3.0.2.json"),
                identifiers(map(table, "extension"), "terms"),
                name + " binds the extension registry, in its order");
        assertEquals(reusedByTheExtension(),
                identifiers(map(table, "extension"), "reusedTerms"),
                name + " binds the core terms the extension groups carry, in their order");
    }

    /**
     * Returns the core terms the groups of the extension registry carry through their
     * {@code reusesTerms} member, in the order the registry writes them, each of them once.
     * A binding table binds every one of them a second time, inside the group that carries
     * it, and {@code extension.reusedTerms} is where it says so.
     */
    private static List<String> reusedByTheExtension() {
        Object registry = JsonTree.of(resource("/model/xrechnung/3.0.2.json"));
        Set<String> own = new LinkedHashSet<>(identifiers(registry, "terms"));
        List<String> reused = new ArrayList<>();
        for (Object term : list(registry, "terms")) {
            for (Object identifier : list(term, "reusesTerms")) {
                if (!own.contains((String) identifier) && !reused.contains(identifier)) {
                    reused.add((String) identifier);
                }
            }
        }
        assertFalse(reused.isEmpty(), "the extension registry states which terms it reuses");
        return reused;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ubl-invoice.json", "ubl-creditnote.json", "cii.json"})
    void theCountsOfATableDescribeThatTable(String name) {
        Object table = JsonTree.of(source(name));
        Object counts = JsonTree.get(table, "counts");
        List<Object> terms = list(table, "terms");
        List<Object> extension = list(map(table, "extension"), "terms");

        assertEquals(terms.size(), number(counts, "terms"), name + ": terms");
        assertEquals(bound(terms), number(counts, "bound"), name + ": bound");
        assertEquals(terms.size() - bound(terms), number(counts, "notRepresented"),
                name + ": notRepresented");
        assertEquals(extension.size(), number(counts, "extensionTerms"),
                name + ": extensionTerms");
        assertEquals(bound(extension), number(counts, "extensionBound"),
                name + ": extensionBound");
        List<Object> reused = list(map(table, "extension"), "reusedTerms");
        assertEquals(reused.size(), number(counts, "extensionReusedTerms"),
                name + ": extensionReusedTerms");
        assertEquals(bound(reused), number(counts, "extensionReusedBound"),
                name + ": extensionReusedBound");

        int components = 0;
        for (Object entry : terms) {
            components += list(entry, "components").size();
        }
        assertEquals(components, number(counts, "components"), name + ": components");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ubl-invoice.json", "ubl-creditnote.json", "cii.json"})
    void everyFlagOfATableIsDefinedInItAndEveryDefinitionIsUsed(String name) {
        Object table = JsonTree.of(source(name));
        assertEquals(
                new LinkedHashSet<>(map(table, "flagDefinitions").keySet()),
                flagsUsedIn(table),
                name + " defines exactly the flags it uses");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ubl-invoice.json", "ubl-creditnote.json", "cii.json"})
    void everyPrefixOfAPathIsDeclaredAndEveryPathStartsAtTheRootElement(String name) {
        Object table = JsonTree.of(source(name));
        Set<String> declared = map(table, "namespaces").keySet();
        String root = "/" + text(table, "rootElement");
        for (Object entry : entriesOf(table)) {
            for (String path : pathsOf(entry)) {
                assertTrue(path.startsWith(root + "/") || path.equals(root),
                        name + ": " + text(entry, "id") + " starts at " + root);
                Matcher matcher = PREFIX.matcher(path);
                while (matcher.find()) {
                    assertTrue(declared.contains(matcher.group(1)),
                            name + ": " + text(entry, "id") + " uses the undeclared prefix "
                                    + matcher.group(1));
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ubl-invoice.json", "ubl-creditnote.json", "cii.json"})
    void aComponentHangsOffAPathItsOwnEntryBinds(String name) {
        Object table = JsonTree.of(source(name));
        for (Object entry : entriesOf(table)) {
            List<String> paths = pathsOf(entry);
            for (Object component : list(entry, "components")) {
                assertTrue(paths.contains(text(component, "anchor")),
                        name + ": the " + text(component, "role") + " of "
                                + text(entry, "id") + " hangs off a path that entry does"
                                + " not bind");
            }
        }
    }

    @Test
    void theFlagTableOfTheReadmeMatchesTheFiles() {
        Map<String, int[]> counted = new TreeMap<>();
        for (String name : TABLES) {
            Object table = JsonTree.of(source(name));
            for (Object entry : entriesOf(table)) {
                for (Object flag : list(entry, "flags")) {
                    counted.computeIfAbsent((String) flag, key -> new int[3])[
                            COLUMN.get(name)]++;
                }
            }
        }

        Map<String, int[]> documented = new TreeMap<>();
        for (String line : resource("/model/bindings/README.md").split("\n")) {
            Matcher row = FLAG_ROW.matcher(line.strip());
            if (row.matches()) {
                documented.put(row.group(1), new int[] {
                    Integer.parseInt(row.group(3)),
                    Integer.parseInt(row.group(4)),
                    Integer.parseInt(row.group(5)),
                });
            }
        }

        assertEquals(counted.keySet(), documented.keySet(),
                "the flag table of model/bindings/README.md lists the flags of the files");
        for (Map.Entry<String, int[]> flag : counted.entrySet()) {
            for (String name : TABLES) {
                int column = COLUMN.get(name);
                assertEquals(flag.getValue()[column], documented.get(flag.getKey())[column],
                        "count of " + flag.getKey() + " in " + name);
            }
        }
    }

    @Test
    void everyFlagMeansTheSameInTheReadmeAndInTheFiles() {
        Map<String, String> documented = new LinkedHashMap<>();
        for (String line : resource("/model/bindings/README.md").split("\n")) {
            Matcher row = FLAG_ROW.matcher(line.strip());
            if (row.matches()) {
                documented.put(row.group(1), row.group(2).replace("`", ""));
            }
        }
        for (String name : TABLES) {
            Map<String, Object> definitions = map(JsonTree.of(source(name)),
                    "flagDefinitions");
            for (Map.Entry<String, Object> flag : definitions.entrySet()) {
                assertEquals(documented.get(flag.getKey()), flag.getValue(),
                        name + " and the README say the same about " + flag.getKey());
            }
        }
    }

    /**
     * A correction is a deliberate departure from the source, and how far the tables
     * depart from it is the first thing a consumer of them wants to know. The README
     * counts them in a sentence, and a sentence nobody counts drifts the moment a
     * correction is added; this is the count, taken from the files.
     */
    @Test
    void theCorrectionCountOfTheReadmeMatchesTheFiles() {
        int corrections = 0;
        for (String name : TABLES) {
            corrections += list(JsonTree.of(source(name)), "corrections").size();
        }
        assertTrue(corrections < NUMBERS.size(),
                "the README spells the count, and " + corrections + " is past the words"
                        + " this test knows");
        Matcher sentence = null;
        for (String line : resource("/model/bindings/README.md").split("\n")) {
            Matcher found = CORRECTION_COUNT.matcher(line.strip());
            if (found.lookingAt()) {
                sentence = found;
            }
        }
        assertFalse(sentence == null,
                "model/bindings/README.md counts the corrections in a sentence");
        assertEquals(NUMBERS.get(corrections), sentence.group(1),
                "model/bindings/README.md counts the corrections of the three tables");
        int exceptions = NUMBERS.indexOf(
                sentence.group(2).substring(0, 1).toUpperCase(Locale.ROOT)
                        + sentence.group(2).substring(1));
        assertTrue(exceptions > 0 && exceptions < corrections,
                "the corrections that are not the one shape are some of them, not all");
    }

    @Test
    void theCrossCheckReportLeavesNoDifferenceUnexplained() {
        String report = resource("/conformance/bindings/crosscheck.md");
        assertFalse(report.contains("**unexplained**"),
                "every difference of conformance/bindings/crosscheck.md is explained");
        assertTrue(report.contains("Total: "), "the report states how many it found");
    }

    private static int bound(List<Object> entries) {
        int count = 0;
        for (Object entry : entries) {
            if (Boolean.TRUE.equals(JsonTree.get(entry, "bound"))) {
                count++;
            }
        }
        return count;
    }

    private static List<Object> entriesOf(Object table) {
        List<Object> all = new ArrayList<>(list(table, "terms"));
        all.addAll(list(map(table, "extension"), "terms"));
        all.addAll(list(map(table, "extension"), "reusedTerms"));
        return all;
    }

    private static List<String> pathsOf(Object entry) {
        List<String> paths = new ArrayList<>();
        String own = text(entry, "xpath");
        if (own != null) {
            paths.add(own);
        }
        for (Object alternative : list(entry, "alternatives")) {
            paths.add(text(alternative, "xpath"));
        }
        return paths;
    }

    private static Set<String> flagsUsedIn(Object table) {
        Set<String> flags = new LinkedHashSet<>();
        for (Object entry : entriesOf(table)) {
            for (Object flag : list(entry, "flags")) {
                flags.add((String) flag);
            }
            for (Object alternative : list(entry, "alternatives")) {
                for (Object flag : list(alternative, "flags")) {
                    flags.add((String) flag);
                }
            }
        }
        return flags;
    }

    private static List<String> identifiers(String file) {
        return identifiers(JsonTree.of(resource(file)), "terms");
    }

    private static List<String> identifiers(Object container, String member) {
        List<String> ids = new ArrayList<>();
        for (Object entry : list(container, member)) {
            ids.add(text(entry, "id"));
        }
        return ids;
    }

    private static String source(String table) {
        return resource("/model/bindings/" + table);
    }

    private static String resource(String resource) {
        try (InputStream in = BindingTableTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
