package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The validation packs this module can run: the ones packaged into it, and the ones a
 * caller points at.
 *
 * <p>A bundled pack is read from the class path and from nowhere else — a pack is part
 * of the artefact, so no file system path, no working directory and no download takes
 * part in finding one. Which packs are bundled is written in a small index beside this
 * class rather than discovered by listing a directory, because a class path is not a
 * directory once the module is inside a jar; the build checks that the index and the
 * packaged packs are the same list.
 *
 * <p>{@link #fromDirectory(Path)} reads a pack a caller supplies. It is how a release
 * that is newer than the one in the artefact reaches the engine without a new build, and
 * it is the caller's business, not this module's, to know where that directory came
 * from: the files are executed, so a pack from a stranger is a stranger's code.
 *
 * <p>The provenance, licences and digests of everything the bundled packs carry are
 * recorded in {@code packs/SOURCES.md} of the repository, and every digest is checked by
 * the build.
 */
public final class Packs {

    /**
     * The directory the bundled packs are packaged in, as a class path resource name:
     * the path of a pack file below this prefix is the path it has under {@code packs/}.
     */
    public static final String ROOT = "de/bsnsoft/esj/syntax/packs";

    /** The index of the bundled packs, as a class path resource name. */
    private static final String INDEX = "de/bsnsoft/esj/syntax/bundled-packs.json";

    private static final List<Pack> BUNDLED = loadBundled();

    private Packs() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the packs packaged into this module, newest release first.
     *
     * @return the bundled packs
     * @throws PackException if a packaged pack cannot be read
     */
    public static List<Pack> bundled() {
        return BUNDLED;
    }

    /**
     * Returns one bundled pack by its identity.
     *
     * @param directory the identity of the pack, {@code id/version/release}
     * @return the pack
     * @throws PackException        if no pack of that identity is packaged
     * @throws NullPointerException if {@code directory} is {@code null}
     */
    public static Pack bundled(String directory) {
        Objects.requireNonNull(directory, "directory");
        for (Pack pack : BUNDLED) {
            if (pack.directory().equals(directory)) {
                return pack;
            }
        }
        throw new PackException("no validation pack " + directory + " is packaged; this"
                + " module carries " + BUNDLED);
    }

    /**
     * Reads a pack from a directory of the file system.
     *
     * <p>The directory is the one that holds the {@code pack.json}. Only the files the
     * manifest lists are read, and each of them is read from below that directory: a
     * manifest that named a path outside it, and one that named a link pointing out of
     * it, both name a file this method does not open. Every file is weighed against the
     * digest the manifest records for it before it is used.
     *
     * @param directory the directory of the pack
     * @return the pack
     * @throws PackException        if the directory holds no manifest this module reads
     * @throws NullPointerException if {@code directory} is {@code null}
     */
    public static Pack fromDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        Path named = directory.toAbsolutePath().normalize();
        if (!Files.isRegularFile(named.resolve(PackManifest.FILE))) {
            throw new PackException("the directory " + named.getFileName()
                    + " holds no " + PackManifest.FILE);
        }
        Path root = real(named);
        return PackManifest.read("directory:" + root, PackSource.SUPPLIED,
                root.getFileName().toString(),
                read(root.resolve(PackManifest.FILE)),
                path -> read(inside(root, path)));
    }

    /**
     * Chooses the components of a bundled pack that apply to a document.
     *
     * <p>Where several packs are bundled, the one that recognizes the profile of the
     * document is taken; where none does, the newest release is, and its selection then
     * carries the note that says so.
     *
     * @param syntax          the syntax of the document
     * @param customizationId the customization identifier the document names in BT-24,
     *                        empty where it names none
     * @return what applies, what does not, and why
     * @throws PackException        if no pack is packaged
     * @throws NullPointerException if an argument is {@code null}
     */
    public static PackSelection select(XrSyntax syntax, String customizationId) {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        if (BUNDLED.isEmpty()) {
            throw new PackException("no validation pack is packaged into this module");
        }
        PackSelection fallback = null;
        for (Pack pack : BUNDLED) {
            PackSelection selection = pack.select(syntax, customizationId);
            if (selection.profileNote().isEmpty()) {
                return selection;
            }
            if (fallback == null) {
                fallback = selection;
            }
        }
        return fallback;
    }

    /**
     * Returns the levels a bundled pack gives a document of one syntax and one profile.
     *
     * <p>It is the lookup {@link ProfileLevels} describes, over the packs this module
     * carries: the first bundled pack that levels anything for the profile answers, and
     * where none does the answer levels nothing. A pack a caller supplied is asked
     * directly, through {@link ProfileLevels#of(Pack, XrSyntax, String)}.
     *
     * @param syntax          the syntax of the document
     * @param customizationId the customization identifier the document names in BT-24,
     *                        empty where it names none
     * @return the lookup
     * @throws NullPointerException if an argument is {@code null}
     */
    public static ProfileLevels levels(XrSyntax syntax, String customizationId) {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        return first(pack -> ProfileLevels.of(pack, syntax, customizationId));
    }

    /**
     * Returns the levels a bundled pack gives a document of one profile whose syntax is
     * not known, which is a document that was never XML.
     *
     * @param customizationId the customization identifier the document names in BT-24,
     *                        empty where it names none
     * @return the lookup
     * @throws NullPointerException if {@code customizationId} is {@code null}
     */
    public static ProfileLevels levels(String customizationId) {
        Objects.requireNonNull(customizationId, "customizationId");
        return first(pack -> ProfileLevels.of(pack, customizationId));
    }

    /** Returns the first answer that levels a rule, or the one that levels none. */
    private static ProfileLevels first(Function<Pack, ProfileLevels> lookup) {
        for (Pack pack : BUNDLED) {
            ProfileLevels levels = lookup.apply(pack);
            if (levels.any()) {
                return levels;
            }
        }
        return ProfileLevels.none();
    }

    /**
     * Opens a file of a bundled pack.
     *
     * @param path the path of the file inside {@code packs/}, with {@code /} as the
     *             separator, neither absolute nor containing a {@code ..} segment
     * @return a stream over the bytes of that file, to be closed by the caller
     * @throws PackException        if no such file is packaged, or the path leaves the
     *                              packs directory
     * @throws NullPointerException if the path is {@code null}
     */
    static InputStream open(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty() || path.startsWith("/")) {
            throw new PackException("a pack file is addressed by a relative path: " + path);
        }
        for (String segment : path.split("/", -1)) {
            if (segment.equals("..")) {
                throw new PackException(
                        "a pack file path stays inside the packs directory: " + path);
            }
        }
        InputStream in = Packs.class.getClassLoader().getResourceAsStream(ROOT + "/" + path);
        if (in == null) {
            throw new PackException("no such file in the validation packs: " + path);
        }
        return in;
    }

    private static List<Pack> loadBundled() {
        List<Pack> packs = new ArrayList<>();
        for (String directory : index()) {
            Pack pack = PackManifest.read("classpath:" + ROOT, PackSource.BUNDLED, directory,
                    resource(directory + "/" + PackManifest.FILE),
                    path -> resource(directory + "/" + path));
            if (!pack.directory().equals(directory)) {
                throw new PackException("the pack packaged under " + directory
                        + " calls itself " + pack.directory());
            }
            packs.add(pack);
        }
        packs.sort(Comparator.comparing(Pack::release).reversed()
                .thenComparing(Pack::directory));
        return List.copyOf(packs);
    }

    private static List<String> index() {
        byte[] json;
        try (InputStream in = Packs.class.getClassLoader().getResourceAsStream(INDEX)) {
            if (in == null) {
                throw new PackException("the index of the bundled validation packs is not"
                        + " on the class path");
            }
            json = in.readAllBytes();
        } catch (IOException e) {
            throw new PackException("the index of the bundled validation packs could not be"
                    + " read", e);
        }
        Object root = PackJson.read(json, "bundled-packs.json");
        List<String> directories = new ArrayList<>();
        if (root instanceof Map<?, ?> object && object.get("packs") instanceof List<?> listed) {
            for (Object element : listed) {
                if (element instanceof String directory) {
                    directories.add(directory);
                } else {
                    throw new PackException("bundled-packs.json names a pack that is not a"
                            + " JSON string");
                }
            }
            return directories;
        }
        throw new PackException("bundled-packs.json has no member packs that is a JSON array");
    }

    private static byte[] resource(String path) {
        try (InputStream in = open(path)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new PackException("the packaged file " + path + " could not be read", e);
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("the file " + file.getFileName()
                    + " of a validation pack could not be read", e);
        }
    }

    /**
     * Resolves a path of a manifest below the directory of the pack, and refuses one that
     * would leave it.
     *
     * <p>Both halves of that are needed. A {@code ..} segment is caught by the name, and
     * a symbolic link is caught by resolving the path the way the file system will: a
     * link inside the directory that points outside it names a file outside it, whatever
     * its name reads like.
     */
    private static Path inside(Path root, String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root) || !real(resolved).startsWith(root)) {
            throw new PackException("a pack file path stays inside the pack directory: "
                    + path);
        }
        return resolved;
    }

    /**
     * Returns a path with every link on it resolved, so that what is compared is what
     * would be opened.
     */
    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            // A path that cannot be resolved is a path that cannot be read, and the
            // caller learns which file it was from the message of the refusal.
            throw new PackException("the file " + path.getFileName()
                    + " of a validation pack could not be read", e);
        }
    }
}
