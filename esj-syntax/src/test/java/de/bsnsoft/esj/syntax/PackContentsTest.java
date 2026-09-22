package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Checks that the validation packs of the repository are packaged into this module whole
 * and unaltered.
 *
 * <p>The packs are third-party validation artefacts that this project redistributes; the
 * claim that they are the released files is worth exactly as much as the digests that back
 * it. So every byte that reaches the class path is weighed here: each file the manifest
 * lists must be present with the digest it records, every file that is present must be one
 * the manifest lists, and every path a component names must be one of them. A file added
 * to a pack without an entry in {@code pack.json}, a file removed from it, and a file
 * changed by so much as a line ending are each a failing build rather than a quiet
 * divergence from {@code packs/SOURCES.md}.
 */
class PackContentsTest {

    /** Where the packs sit on the class path; the pom copies {@code packs/} here. */
    private static final String PACKS = "/" + Packs.ROOT;

    private static final String MANIFEST = "pack.json";

    /**
     * How {@code packs/SOURCES.md} states the size of a pack: the heading that names it,
     * and the sentence with the two figures somewhere below it.
     */
    private static final Pattern STATED = Pattern.compile(
            "## Pack `([^`]+)`.*?(\\d+) files, ([\\d,]+) bytes", Pattern.DOTALL);

    @Test
    void packagesAtLeastTheXRechnungPack() {
        List<Manifest> packs = packs();

        assertFalse(packs.isEmpty(), "the packs of the repository reach the class path");
        assertTrue(packs.stream().anyMatch(pack -> "xrechnung/3.0.2/2026-08-31".equals(
                        pack.directory())),
                "the XRechnung 3.0.2 pack of release 2026-08-31 is packaged");
    }

    @Test
    void everyFileHasTheDigestTheManifestRecords() {
        forEachPack((root, pack) -> {
            for (ManifestFile file : pack.files()) {
                String packed = pack.directory() + "/" + file.path();
                assertTrue(Files.isRegularFile(root.resolve(file.path())),
                        pack.directory() + " ships " + file.path());
                assertEquals(file.sha256(), sha256(packed),
                        packed + " is the file the manifest records");
            }
        });
    }

    @Test
    void shipsNoFileTheManifestDoesNotList() {
        forEachPack((root, pack) -> {
            Set<String> listed = new TreeSet<>();
            listed.add(MANIFEST);
            pack.files().forEach(file -> listed.add(file.path()));

            assertEquals(listed, present(root),
                    pack.directory() + " ships exactly the files its manifest lists");
        });
    }

    @Test
    void everyComponentNamesFilesOfItsOwnPack() {
        forEachPack((root, pack) -> {
            Set<String> listed = new TreeSet<>();
            pack.files().forEach(file -> listed.add(file.path()));

            assertFalse(pack.components().isEmpty(),
                    pack.directory() + " declares its components");
            for (ManifestComponent component : pack.components()) {
                String where = pack.directory() + " component " + component.name();
                assertTrue(List.of("xsd", "schematron-xslt").contains(component.role()),
                        where + " has a role the engine knows: " + component.role());
                assertFalse(component.entries().isEmpty(), where + " names an entry file");
                Stream.concat(
                                Stream.concat(component.entries().stream(),
                                        component.files().stream()),
                                Stream.of(component.licenseFile()))
                        .forEach(path -> assertTrue(listed.contains(path),
                                where + " names " + path + ", which the inventory lists"));
                component.entries().forEach(entry ->
                        assertTrue(component.files().contains(entry),
                                where + " counts its entry " + entry
                                        + " among its own files"));
            }
        });
    }

    /**
     * What {@code packs/SOURCES.md} says each pack weighs is what it weighs.
     *
     * <p>That sentence is the first thing a reviewer of the vendored artefacts
     * recomputes, and it is the one line of the file no digest backs: every hash can be
     * right while the total has gone stale under an edit to the manifest beside them. So
     * the tree is counted and weighed here against the two figures the page states, the
     * way the digests are checked against the ones it lists.
     */
    @Test
    void weighsAsMuchAsSourcesSaysItDoes() {
        String sources = sources();
        forEachPack((root, pack) -> {
            Matcher stated = STATED.matcher(sources);
            String recorded = null;
            while (stated.find()) {
                if (stated.group(1).equals(pack.directory())) {
                    recorded = stated.group(2) + " files, " + stated.group(3) + " bytes";
                }
            }
            assertNotNull(recorded,
                    "packs/SOURCES.md states the size of the pack " + pack.directory());
            assertEquals(recorded, weigh(root),
                    "packs/SOURCES.md states what the pack " + pack.directory()
                            + " actually weighs");
        });
    }

    /** Returns the file count and the byte total of a pack, in the words SOURCES.md uses. */
    private static String weigh(Path root) {
        try (Stream<Path> tree = Files.walk(root)) {
            List<Path> files = tree.filter(Files::isRegularFile).toList();
            long bytes = 0;
            for (Path file : files) {
                bytes += Files.size(file);
            }
            // The grouping separator is the page's, not the machine's: a German virtual
            // machine writes 2.813.955 for the same number.
            return files.size() + " files, "
                    + String.format(java.util.Locale.ROOT, "%,d", bytes) + " bytes";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads {@code packs/SOURCES.md}, which the build puts on the test class path. */
    private static String sources() {
        try (InputStream in = PackContentsTest.class.getResourceAsStream("/packs/SOURCES.md")) {
            assertNotNull(in, "packs/SOURCES.md is on the test class path");
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The engine finds the bundled packs through an index beside its classes, because a
     * class path inside a jar is not a directory that can be listed. An index and a tree
     * are two lists of the same thing, so they are compared here: a pack added to the
     * repository and left out of the index would be a pack that never runs, and an index
     * entry with no pack behind it would be a module that does not start.
     */
    @Test
    void theIndexOfBundledPacksNamesEveryPackagedPack() {
        Set<String> packaged = new TreeSet<>();
        packs().forEach(manifest -> packaged.add(manifest.directory()));
        Set<String> indexed = new TreeSet<>();
        Packs.bundled().forEach(pack -> indexed.add(pack.directory()));

        assertEquals(packaged, indexed,
                "bundled-packs.json names the packs the build packages");
    }

    @Test
    void identifiesItselfByTheDirectoryItSitsIn() {
        forEachPack((root, pack) ->
                assertEquals(pack.id() + "/" + pack.version() + "/" + pack.release(),
                        pack.directory(),
                        "a pack is identified by the directory it sits in"));
    }

    // ----------------------------------------------------------------------
    // reading the packs off the class path
    // ----------------------------------------------------------------------

    /** What a test does with one pack: its directory on the class path and its manifest. */
    private interface ManifestCheck {
        void check(Path root, Manifest pack);
    }

    private void forEachPack(ManifestCheck check) {
        withPacksRoot(packsRoot -> {
            for (Path manifest : manifests(packsRoot)) {
                Path root = manifest.getParent();
                check.check(root, read(relative(packsRoot, root)));
            }
        });
    }

    private List<Manifest> packs() {
        List<Manifest> packs = new ArrayList<>();
        withPacksRoot(packsRoot -> {
            for (Path manifest : manifests(packsRoot)) {
                packs.add(read(relative(packsRoot, manifest.getParent())));
            }
        });
        return packs;
    }

    /**
     * Runs an action on the packs directory of the class path. The directory is a plain
     * directory while the module is built and an entry of a jar once it is packaged, so
     * both are opened here and the action sees a {@link Path} either way.
     */
    private void withPacksRoot(java.util.function.Consumer<Path> action) {
        URL url = PackContentsTest.class.getResource(PACKS);
        assertNotNull(url, "the packs directory is on the class path");
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                    action.accept(jar.getPath(PACKS));
                }
            } else {
                action.accept(Path.of(uri));
            }
        } catch (URISyntaxException | IOException e) {
            throw new AssertionError("the packs directory could not be opened", e);
        }
    }

    private static List<Path> manifests(Path packsRoot) {
        try (Stream<Path> tree = Files.walk(packsRoot)) {
            List<Path> found = tree.filter(Files::isRegularFile)
                    .filter(path -> MANIFEST.equals(path.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            assertFalse(found.isEmpty(), "at least one pack manifest is packaged");
            return found;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> present(Path root) {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile)
                    .map(path -> relative(root, path))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relative(Path root, Path path) {
        StringBuilder text = new StringBuilder();
        for (Path segment : root.relativize(path)) {
            if (text.length() > 0) {
                text.append('/');
            }
            text.append(segment);
        }
        return text.toString();
    }

    private static String sha256(String packPath) {
        try (InputStream in = Packs.open(packPath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    // ----------------------------------------------------------------------
    // the manifest
    // ----------------------------------------------------------------------

    /** One file of a pack, as the inventory of the manifest records it. */
    private record ManifestFile(String path, String sha256) {}

    /** One artefact of a pack: what it is, which files it owns and under which licence. */
    private record ManifestComponent(String name, String role, List<String> entries,
            List<String> files, String licenseFile) {}

    /** A manifest, together with the directory it was read from. */
    private record Manifest(String directory, String id, String version, String release,
            List<ManifestFile> files, List<ManifestComponent> components) {}

    private static Manifest read(String directory) {
        try (InputStream in = Packs.open(directory + "/" + MANIFEST);
                JsonParser parser = new JsonFactory().createParser(in)) {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken(),
                    directory + "/" + MANIFEST + " is a JSON object");

            String id = null;
            String version = null;
            String release = null;
            List<ManifestFile> files = List.of();
            List<ManifestComponent> components = List.of();

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                switch (field) {
                    case "id" -> id = parser.nextTextValue();
                    case "version" -> version = parser.nextTextValue();
                    case "release" -> release = parser.nextTextValue();
                    case "files" -> files = readFiles(parser);
                    case "components" -> components = readComponents(parser);
                    default -> {
                        parser.nextToken();
                        parser.skipChildren();
                    }
                }
            }
            assertNotNull(id, directory + "/" + MANIFEST + " names its pack");
            assertNotNull(version, directory + "/" + MANIFEST + " names its version");
            assertNotNull(release, directory + "/" + MANIFEST + " names its release");
            assertFalse(files.isEmpty(), directory + "/" + MANIFEST + " has an inventory");
            return new Manifest(directory, id, version, release, files, components);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<ManifestFile> readFiles(JsonParser parser) throws IOException {
        List<ManifestFile> files = new ArrayList<>();
        parser.nextToken();
        while (parser.nextToken() == JsonToken.START_OBJECT) {
            String path = null;
            String sha256 = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                switch (parser.currentName()) {
                    case "path" -> path = parser.nextTextValue();
                    case "sha256" -> sha256 = parser.nextTextValue();
                    default -> {
                        parser.nextToken();
                        parser.skipChildren();
                    }
                }
            }
            assertNotNull(path, "an inventory entry names a file");
            assertNotNull(sha256, "an inventory entry carries a digest");
            files.add(new ManifestFile(path, sha256));
        }
        return List.copyOf(files);
    }

    private static List<ManifestComponent> readComponents(JsonParser parser) throws IOException {
        List<ManifestComponent> components = new ArrayList<>();
        parser.nextToken();
        while (parser.nextToken() == JsonToken.START_OBJECT) {
            String name = null;
            String role = null;
            String licenseFile = null;
            Set<String> entries = new LinkedHashSet<>();
            List<String> files = List.of();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                switch (parser.currentName()) {
                    case "name" -> name = parser.nextTextValue();
                    case "role" -> role = parser.nextTextValue();
                    case "licenseFile" -> licenseFile = parser.nextTextValue();
                    case "entry" -> entries.addAll(readEntries(parser));
                    case "files" -> files = readStrings(parser);
                    default -> {
                        parser.nextToken();
                        parser.skipChildren();
                    }
                }
            }
            assertNotNull(name, "a component has a name");
            assertNotNull(role, "component " + name + " has a role");
            assertNotNull(licenseFile, "component " + name + " names its licence file");
            components.add(new ManifestComponent(name, role, List.copyOf(entries), files,
                    licenseFile));
        }
        return List.copyOf(components);
    }

    private static Set<String> readEntries(JsonParser parser) throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        parser.nextToken();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            entries.add(parser.nextTextValue());
        }
        return entries;
    }

    private static List<String> readStrings(JsonParser parser) throws IOException {
        List<String> values = new ArrayList<>();
        parser.nextToken();
        while (parser.nextToken() == JsonToken.VALUE_STRING) {
            values.add(parser.getText());
        }
        return List.copyOf(values);
    }
}
