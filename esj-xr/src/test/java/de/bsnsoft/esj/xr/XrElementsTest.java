package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Checks the element table of the XR representation against the schema it is derived from.
 *
 * <p>The table is generated, and a generated file that is checked in is a claim about a
 * source. This test is what backs the claim: the vendored schema is the file its README
 * records, the table is what the generator makes of that file, and every line of it says
 * the same thing about a term as the registry does.
 */
class XrElementsTest {

    /** The vendored schema on the test classpath. */
    private static final String DIRECTORY = "/kosit-model/";

    private static final String SCHEMA = DIRECTORY + "xrechnung-semantic-model.xsd";

    /** The table as it is checked in and shipped, on the classpath of the main sources. */
    private static final String TABLE = "/de/bsnsoft/esj/xr/xr-elements.tsv";

    /** Where the table is checked in, for the message of a test that found it out of date. */
    private static final String SOURCE = "esj-xr/src/main/resources" + TABLE;

    private static final Map<String, String> DIGESTS = Map.of(
            "xrechnung-semantic-model.xsd",
            "dc2193877adf0f5b31dcf0e86a24f38773bb748b0f0ec001341e4a2892121aef",
            "LICENSE",
            "e0d7665e91531aebb79e4feaa415796f076b0f93e9eddd6d1d05efe9d93808ac");

    @Test
    void shipsTheSchemaTheReadmeDescribes() {
        String readme = Instances.text(DIRECTORY + "README.md");

        DIGESTS.forEach((name, digest) -> {
            assertEquals(digest, sha256(Instances.bytes(DIRECTORY + name)),
                    "the vendored " + name + " is the one the README records");
            assertTrue(readme.contains(name), "the README names " + name);
            assertTrue(readme.contains(digest), "the README records the digest of " + name);
        });
    }

    @Test
    void theCheckedInTableIsTheOneTheSchemaYields() throws IOException {
        String derived = XrElementsSource.derive(Instances.bytes(SCHEMA), registry());

        Path written = Path.of("target", "xr-elements.tsv");
        Files.createDirectories(written.getParent());
        Files.writeString(written, derived, StandardCharsets.UTF_8);

        assertEquals(Instances.text(TABLE).strip(), derived.strip(),
                "the checked-in " + SOURCE + " is out of date; the table the schema yields"
                        + " has just been written to " + written.toAbsolutePath());
    }

    @Test
    void twoRunsOverOneSchemaProduceTheSameBytes() {
        byte[] schema = Instances.bytes(SCHEMA);

        assertEquals(XrElementsSource.derive(schema, registry()),
                XrElementsSource.derive(schema, registry()));
    }

    @Test
    void recordsEveryTermOfTheRegistryExactlyWhereTheRegistryPutsIt() {
        Registry registry = registry();
        XrElements table = XrElements.table();
        Set<String> recorded = new TreeSet<>();
        Set<String> positions = new HashSet<>();

        for (String container : table.containers()) {
            for (XrElements.Element element : table.children(container)) {
                Term term = registry.term(element.id()).orElseThrow(() ->
                        new AssertionError("the table records " + element.id()
                                + ", which no registry of this module knows"));
                assertTrue(positions.add(container + "/" + element.id()),
                        "the table records " + element.id() + " twice under " + container);
                assertTrue(XrPlacement.continuation(registry, chain(registry, container), term.id())
                                .isPresent(),
                        "the registry places " + element.id() + " under " + container);
                recorded.add(element.id());
            }
        }

        Set<String> known = new TreeSet<>();
        registry.terms().forEach(term -> known.add(term.id()));
        assertEquals(known, recorded,
                "the XR representation carries every term of the core model and of the"
                        + " XRechnung extension, and no other");
    }

    @Test
    void namesTheTypeOfEveryTermTheRegistryGivesIt() {
        Registry registry = registry();
        XrElements table = XrElements.table();

        for (String container : table.containers()) {
            for (XrElements.Element element : table.children(container)) {
                Term term = registry.term(element.id()).orElseThrow();
                if (term.isGroup()) {
                    assertTrue(element.type().endsWith("Type"),
                            "the type of the group " + element.id() + " is a group type");
                    continue;
                }
                SemanticType datatype = term.datatype().orElseThrow();
                assertEquals(datatype.name().toLowerCase(Locale.ROOT), element.type(),
                        "the schema type of " + element.id() + " is its semantic data type");
            }
        }
    }

    @Test
    void recordsOneContainerPerGroup() {
        Registry registry = registry();
        XrElements table = XrElements.table();
        Set<String> groups = new LinkedHashSet<>();
        registry.terms().stream().filter(Term::isGroup).forEach(term -> groups.add(term.id()));
        Set<String> containers = new LinkedHashSet<>(table.containers());

        assertTrue(containers.remove(XrElements.ROOT), "the table records the root");
        assertEquals(groups, containers,
                "every business group of the registry is a group of the XR representation");
        assertFalse(table.children(XrElements.ROOT).isEmpty(), "the root carries elements");
    }

    /** Returns the chain the children of a container resolve against, empty at the root. */
    private static List<String> chain(Registry registry, String container) {
        if (XrElements.ROOT.equals(container)) {
            return List.of();
        }
        return XrPlacement.resolved(registry.term(container).orElseThrow().path());
    }

    private static Registry registry() {
        return XrImporter.defaultRegistry();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
