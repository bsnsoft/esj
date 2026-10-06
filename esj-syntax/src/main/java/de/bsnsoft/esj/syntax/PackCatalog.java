package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The validation packs a run chooses among: the ones bundled with this module and the ones
 * found in the pack directories a caller named.
 *
 * <p>A <em>pack directory</em> is a directory that holds packs the way {@code packs/} of the
 * repository does: every {@code <directory>/<id>/<version>/<release>/pack.json} below it is
 * a pack, and it is identified by those three parts. {@code esj packs fetch} writes packs in
 * that layout, and the environment variable {@code ESJ_PACKS} and the option
 * {@code --packs} of the command line name such directories. What is found there joins the
 * bundled packs for the automatic choice by profile; nothing is fetched while a document is
 * checked.
 *
 * <p>Two rules keep the choice unambiguous rather than ordered. A pack in a directory with
 * the identity of a bundled pack is refused by name, and so is one identity found in two
 * directories: an identity is how a report names the rules that ran, and a name that could
 * mean either of two sets of files names neither. And where more than one pack recognizes
 * the profile of a document and one of them came from a directory, the run is refused and
 * {@code --pack} names the one to take; a precedence rule would decide silently which rules
 * judge an invoice. The one order the catalog does apply is the one a publisher gives:
 * where every pack that recognizes the profile is a release of one pack — the same
 * {@code id} and {@code version}, as two fetched releases of Peppol BIS Billing 3.0 are —
 * the newest release is taken, its release compared part by part (3.0.9 before 3.0.10), as among
 * bundled packs, and the report names it.
 *
 * <p>Where no pack recognizes the profile, the bundled packs are asked first, so a pack
 * directory changes nothing for a document of a profile none of its packs is written for.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class PackCatalog {

    private static final PackCatalog BUNDLED = new PackCatalog(Packs.bundled(), List.of());

    private final List<Pack> packs;
    private final List<Path> directories;

    private PackCatalog(List<Pack> packs, List<Path> directories) {
        this.packs = List.copyOf(packs);
        this.directories = List.copyOf(directories);
    }

    /**
     * Returns the catalog of the packs bundled with this module and no others.
     *
     * @return the catalog
     */
    public static PackCatalog bundled() {
        return BUNDLED;
    }

    /**
     * Returns the bundled packs together with every pack found in some pack directories.
     *
     * <p>Only the manifests are read here. The files of a pack are read, and weighed
     * against the digests of its manifest, when a document first needs them.
     *
     * @param directories the pack directories, in the order the caller named them; a
     *                    directory named twice is read once
     * @return the catalog
     * @throws PackException        if a directory does not exist, a manifest found in one
     *                              cannot be read, a pack sits under a directory that is
     *                              not its identity, or an identity is found twice or is
     *                              one of a bundled pack
     * @throws NullPointerException if {@code directories} is or contains {@code null}
     */
    public static PackCatalog withDirectories(List<Path> directories) {
        Objects.requireNonNull(directories, "directories");
        if (directories.isEmpty()) {
            return BUNDLED;
        }
        Map<String, Pack> found = new LinkedHashMap<>();
        List<Path> read = new ArrayList<>();
        for (Path directory : directories) {
            Path root = root(Objects.requireNonNull(directory, "directory"));
            if (read.contains(root)) {
                continue;
            }
            read.add(root);
            for (Pack pack : scan(root)) {
                refuseBundled(pack);
                Pack earlier = found.putIfAbsent(pack.directory(), pack);
                if (earlier != null && !earlier.location().equals(pack.location())) {
                    throw new PackException("the pack " + pack.directory() + " is in two pack"
                            + " directories, " + earlier.location().orElseThrow() + " and "
                            + pack.location().orElseThrow() + "; an identity names one set of"
                            + " files, so remove one of them");
                }
            }
        }
        List<Pack> all = new ArrayList<>(Packs.bundled());
        all.addAll(found.values());
        return new PackCatalog(all, read);
    }

    /**
     * Returns every pack of the catalog: the bundled ones first, newest release first, then
     * the ones of the pack directories in the order the directories were named and, within
     * one directory, by identity.
     *
     * @return the packs
     */
    public List<Pack> packs() {
        return packs;
    }

    /**
     * Returns the pack directories this catalog was read from, as canonical paths, in the
     * order they were named.
     *
     * @return the directories, empty for the catalog of the bundled packs
     */
    public List<Path> directories() {
        return directories;
    }

    /**
     * Returns one pack of the catalog by its identity.
     *
     * @param identity the identity of the pack, {@code id/version/release}
     * @return the pack
     * @throws PackException        if the catalog holds no pack of that identity
     * @throws NullPointerException if {@code identity} is {@code null}
     */
    public Pack find(String identity) {
        Objects.requireNonNull(identity, "identity");
        for (Pack pack : packs) {
            if (pack.directory().equals(identity)) {
                return pack;
            }
        }
        throw new PackException("no validation pack " + identity + " is bundled"
                + (directories.isEmpty() ? "" : " or in a pack directory") + "; there are "
                + packs.stream().map(Pack::directory).collect(Collectors.joining(", ")));
    }

    /**
     * Chooses the components that apply to a document.
     *
     * <p>A pack <em>recognizes</em> a profile where its selection for the document carries
     * no profile note and applies something: every rule set it holds for the syntax
     * applies, and it holds one. Exactly one pack
     * that recognizes the profile is taken. Several bundled ones are a release of the same
     * profile beside another, and the newest release is taken, as it always was; so are
     * several that are all releases of one pack, {@code id} and {@code version} alike,
     * wherever they came from. Several of which one came from a pack directory and which
     * are not releases of one pack are refused, naming them, because which of two sets of
     * rules judges an invoice is the caller's decision and {@code --pack} is how it is made.
     * Where none recognizes the profile, the first bundled pack is taken and its selection
     * carries the note that says what did not run.
     *
     * @param syntax          the syntax of the document
     * @param customizationId the customization identifier the document names in BT-24,
     *                        empty where it names none
     * @return what applies, what does not, and why
     * @throws PackException        if no pack is available, or more than one pack from which
     *                              a pack directory contributes recognizes the profile and
     *                              they are not all releases of one pack
     * @throws NullPointerException if an argument is {@code null}
     */
    public PackSelection select(XrSyntax syntax, String customizationId) {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        if (packs.isEmpty()) {
            throw new PackException("no validation pack is packaged into this module");
        }
        List<PackSelection> recognizing = new ArrayList<>();
        PackSelection fallback = null;
        for (Pack pack : packs) {
            PackSelection selection = pack.select(syntax, customizationId);
            if (selection.profileNote().isEmpty() && !selection.applied().isEmpty()) {
                recognizing.add(selection);
            }
            if (fallback == null) {
                fallback = selection;
            }
        }
        if (recognizing.isEmpty()) {
            return fallback;
        }
        boolean fromDirectory = recognizing.stream()
                .anyMatch(selection -> selection.pack().source() != PackSource.BUNDLED);
        boolean releasesOfOne = recognizing.stream()
                .map(selection -> selection.pack().id() + "/" + selection.pack().version())
                .distinct().count() == 1;
        if (recognizing.size() > 1 && releasesOfOne) {
            return recognizing.stream()
                    .max(Comparator.comparing((PackSelection selection) ->
                            selection.pack().release(), Releases.ORDER))
                    .orElseThrow();
        }
        if (recognizing.size() > 1 && fromDirectory) {
            throw new PackException(recognizing.size() + " validation packs apply to "
                    + (customizationId.isEmpty() ? "a document that names no profile"
                            : "the profile " + customizationId) + ": "
                    + recognizing.stream().map(PackCatalog::describe)
                            .collect(Collectors.joining(", "))
                    + "; --pack <id> chooses one of them");
        }
        return recognizing.get(0);
    }

    /**
     * Returns the levels a pack of this catalog gives a document of one syntax and one
     * profile: the first pack that levels anything for the profile answers, and where none
     * does the answer levels nothing.
     *
     * @param syntax          the syntax of the document
     * @param customizationId the customization identifier the document names in BT-24,
     *                        empty where it names none
     * @return the lookup
     * @throws NullPointerException if an argument is {@code null}
     */
    public ProfileLevels levels(XrSyntax syntax, String customizationId) {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        return first(pack -> ProfileLevels.of(pack, syntax, customizationId));
    }

    /**
     * Returns the levels a pack of this catalog gives a document of one profile whose
     * syntax is not known, which is a document that was never XML.
     *
     * @param customizationId the customization identifier the document names in BT-24,
     *                        empty where it names none
     * @return the lookup
     * @throws NullPointerException if {@code customizationId} is {@code null}
     */
    public ProfileLevels levels(String customizationId) {
        Objects.requireNonNull(customizationId, "customizationId");
        return first(pack -> ProfileLevels.of(pack, customizationId));
    }

    @Override
    public String toString() {
        return packs.stream().map(PackCatalog::describe).collect(Collectors.joining(", "));
    }

    /** Returns the first answer that levels a rule, or the one that levels none. */
    private ProfileLevels first(Function<Pack, ProfileLevels> lookup) {
        for (Pack pack : packs) {
            ProfileLevels levels = lookup.apply(pack);
            if (levels.any()) {
                return levels;
            }
        }
        return ProfileLevels.none();
    }

    /** Names a selection's pack with where it came from, for a message. */
    private static String describe(PackSelection selection) {
        return describe(selection.pack());
    }

    /** Names a pack with where it came from, for a message. */
    private static String describe(Pack pack) {
        return pack.directory() + pack.location().map(path -> " (" + path + ")")
                .orElse(" (bundled)");
    }

    /** Refuses a pack in a directory that has the identity of a bundled one. */
    private static void refuseBundled(Pack pack) {
        for (Pack bundled : Packs.bundled()) {
            if (bundled.directory().equals(pack.directory())) {
                throw new PackException("the pack directory " + pack.location().orElseThrow()
                        + " holds a pack with the identity " + pack.directory() + ", which is"
                        + " the identity of a pack this build carries; an identity names one"
                        + " set of files, so a pack directory may not reuse it");
            }
        }
    }

    /** Returns the canonical form of a pack directory, refusing one that is not there. */
    private static Path root(Path directory) {
        Path named = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(named)) {
            throw new PackException("the pack directory " + named + " does not exist or is"
                    + " not a directory");
        }
        return Packs.real(named);
    }

    /**
     * Reads every pack of one pack directory: each {@code <id>/<version>/<release>} that
     * holds a {@code pack.json}. Names that begin with a dot are passed over, which is where
     * a fetch in progress keeps its files and where file managers keep theirs.
     */
    private static List<Pack> scan(Path root) {
        List<Pack> packs = new ArrayList<>();
        for (Path id : children(root)) {
            for (Path version : children(id)) {
                for (Path release : children(version)) {
                    if (!Files.isRegularFile(release.resolve(PackManifest.FILE))) {
                        continue;
                    }
                    Pack pack = Packs.fromDirectory(release, PackSource.DIRECTORY);
                    String sits = id.getFileName() + "/" + version.getFileName() + "/"
                            + release.getFileName();
                    if (!pack.directory().equals(sits)) {
                        throw new PackException("the pack under " + release + " calls itself "
                                + pack.directory() + "; a pack sits in the directory of its"
                                + " identity, id/version/release");
                    }
                    packs.add(pack);
                }
            }
        }
        return packs;
    }

    /** Returns the subdirectories of a directory whose name does not begin with a dot. */
    private static List<Path> children(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new PackException("the pack directory " + directory
                    + " could not be listed", e);
        }
    }
}
