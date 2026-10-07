package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.Preview;
import de.bsnsoft.esj.xml.InvoiceSyntax;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * How a validation pack is made on the machine that runs it: which files of one release of
 * a publisher to fetch, with which digests, and how to turn them into a pack.
 *
 * <p>A recipe exists for artefacts that may be used but not redistributed. What this
 * project ships of such a pack is its own work only — the recipe, the command that follows
 * it and the manifest that command writes — and the publisher's files travel from the
 * publisher to the user and nowhere else. The digest of every file is pinned in the
 * recipe, so a file that changed upstream after the recipe was written is refused rather
 * than compiled.
 *
 * <p>A recipe names four kinds of fact: the identity of the pack it makes; the release of
 * the publisher it takes the files from, by repository, tag or commit and base URL; the
 * Schematron
 * files, each with its size, its SHA-256, the syntaxes and profiles it applies to and its
 * licence; and the components it copies out of a bundled pack — the XML Schema modules of
 * the syntaxes, whose licences permit that — so that the pack it makes is complete.
 *
 * <p>Instances are immutable.
 *
 * <p>The class is a preview, like the recipes it reads: it may change in any minor release.
 */
@Preview
public final class PackRecipe {

    /** The format a recipe of this repository is written in. */
    private static final String FORMAT = "esj-pack-recipe";

    /** The version of that format this module reads. */
    private static final String FORMAT_VERSION = "1";

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** A full Git commit identifier. */
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    /** A path of a pack or of a publisher's repository: relative, no {@code ..}, no {@code \}. */
    private static final Pattern RELATIVE_PATH = Pattern.compile(
            "[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");

    private final String name;
    private final String title;
    private final String note;
    private final String id;
    private final String version;
    private final String release;
    private final String publisher;
    private final String repository;
    private final String tag;
    private final String branch;
    private final String commit;
    private final String releaseName;
    private final String published;
    private final URI base;
    private final List<String> baseProfiles;
    private final List<Copy> copies;
    private final List<Schematron> schematron;

    private PackRecipe(Map<?, ?> root, String where) {
        Map<?, ?> pack = object(root, "pack", where);
        Map<?, ?> source = object(root, "source", where);
        this.name = string(root, "name", where);
        this.title = string(root, "title", where);
        this.note = string(root, "note", where);
        this.id = segment(pack, "id", where);
        this.version = segment(pack, "version", where);
        this.release = segment(pack, "release", where);
        this.publisher = string(source, "publisher", where);
        this.repository = string(source, "repository", where);
        this.tag = optionalString(source, "tag", where);
        this.branch = optionalString(source, "branch", where);
        this.commit = string(source, "commit", where);
        if (!COMMIT.matcher(commit).matches()) {
            throw new PackException(where + " names the commit " + commit + ", which is not a"
                    + " full commit identifier in lower case hexadecimal");
        }
        this.releaseName = string(source, "release", where);
        this.published = string(source, "published", where);
        this.base = base(string(source, "base", where), where);
        // The base is what the files are fetched below, so it is what pins them: below the
        // tag where the release has one, and below the commit itself where it has none. A
        // branch is never a base, because a branch moves.
        String pinned = "/" + (tag != null ? tag : commit) + "/";
        if (!base.toString().contains(pinned)) {
            throw new PackException(where + " fetches below " + base + ", which is not below "
                    + (tag != null ? "the tag " + tag : "the commit " + commit)
                    + "; a recipe without a tag fetches below the commit it names");
        }
        this.baseProfiles = strings(root, "baseProfiles", where);
        List<Copy> copied = new ArrayList<>();
        for (Object element : array(root, "copy", where)) {
            Map<?, ?> copy = asObject(element, where);
            copied.add(new Copy(string(copy, "pack", where), string(copy, "component", where)));
        }
        this.copies = List.copyOf(copied);
        List<Schematron> rules = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (Object element : array(root, "schematron", where)) {
            Schematron rule = schematron(asObject(element, where), where);
            if (!names.add(rule.name())) {
                throw new PackException(where + " names the component " + rule.name()
                        + " twice");
            }
            rules.add(rule);
        }
        if (rules.isEmpty()) {
            throw new PackException(where + " names no Schematron file");
        }
        this.schematron = List.copyOf(rules);
    }

    /**
     * Reads a recipe.
     *
     * <p>A recipe is read strictly, as a manifest is: a member that is missing or of the
     * wrong kind, a digest that is not a SHA-256, a path that leaves the directory it names
     * and a base URL that is not {@code https} are each a {@link PackException}, because a
     * recipe decides what is downloaded and executed.
     *
     * @param json  the bytes of the recipe
     * @param where what messages call the recipe
     * @return the recipe
     * @throws PackException        if the bytes are not a recipe this module reads
     * @throws NullPointerException if an argument is {@code null}
     */
    public static PackRecipe read(byte[] json, String where) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(where, "where");
        Map<?, ?> root = asObject(PackJson.read(json, where), where);
        String format = string(root, "format", where);
        String formatVersion = string(root, "formatVersion", where);
        if (!FORMAT.equals(format) || !FORMAT_VERSION.equals(formatVersion)) {
            throw new PackException(where + " is written in " + format + " version "
                    + formatVersion + ", and this module reads " + FORMAT + " version "
                    + FORMAT_VERSION);
        }
        return new PackRecipe(root, where);
    }

    /**
     * One Schematron file of a recipe, and the component of the pack it becomes.
     *
     * @param name      the name of the component in the pack
     * @param file      the path of the file in the publisher's repository
     * @param bytes     its size
     * @param sha256    its SHA-256, lower case hexadecimal
     * @param syntaxes  the syntax tokens the component applies to
     * @param profiles  the profile patterns the component applies to
     * @param license   the SPDX identifier of its licence, or a {@code LicenseRef-} name
     * @param about     what the file is, in a phrase, for the notice of the pack
     * @param directory the directory of the pack the file and its compiled form are put in
     */
    public record Schematron(String name,
                             String file,
                             long bytes,
                             String sha256,
                             List<String> syntaxes,
                             List<String> profiles,
                             String license,
                             String about,
                             String directory) {

        /**
         * Creates the entry, copying the lists.
         *
         * @param name      the name of the component in the pack
         * @param file      the path of the file in the publisher's repository
         * @param bytes     its size
         * @param sha256    its SHA-256, lower case hexadecimal
         * @param syntaxes  the syntax tokens the component applies to
         * @param profiles  the profile patterns the component applies to
         * @param license   the SPDX identifier of its licence, or a {@code LicenseRef-} name
         * @param about     what the file is, in a phrase, for the notice of the pack
         * @param directory the directory of the pack the file and its compiled form are put
         *                  in
         */
        public Schematron {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(sha256, "sha256");
            syntaxes = List.copyOf(syntaxes);
            profiles = List.copyOf(profiles);
            Objects.requireNonNull(license, "license");
            Objects.requireNonNull(about, "about");
            Objects.requireNonNull(directory, "directory");
        }

        /**
         * Returns the name of the file, without the directories above it.
         *
         * @return the file name
         */
        public String fileName() {
            return file.substring(file.lastIndexOf('/') + 1);
        }

        /**
         * Returns the path in the pack the fetched file is written to.
         *
         * @return the path
         */
        public String source() {
            return directory + "/" + fileName();
        }

        /**
         * Returns the path in the pack the compiled stylesheet is written to: the name of the
         * file with {@code .xslt} for the extension it had.
         *
         * @return the path
         */
        public String compiled() {
            String fileName = fileName();
            int dot = fileName.lastIndexOf('.');
            return directory + "/" + (dot < 0 ? fileName : fileName.substring(0, dot))
                    + ".xslt";
        }
    }

    /**
     * A component a recipe copies out of a pack bundled with this module.
     *
     * @param pack      the identity of the bundled pack
     * @param component the name of the component in it
     */
    public record Copy(String pack, String component) {

        /**
         * Refuses a missing member.
         *
         * @param pack      the identity of the bundled pack
         * @param component the name of the component in it
         */
        public Copy {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(component, "component");
        }
    }

    /**
     * Returns the name of the recipe, which is what {@code esj packs fetch} is given.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the title the pack is given.
     *
     * @return the title, in English
     */
    public String title() {
        return title;
    }

    /**
     * Returns the one-line note the pack carries about the terms of its files.
     *
     * @return the note, in English
     */
    public String note() {
        return note;
    }

    /**
     * Returns the identity of the pack the recipe makes, {@code id/version/release}.
     *
     * @return the identity
     */
    public String identity() {
        return id + "/" + version + "/" + release;
    }

    /**
     * Returns the identifier of the pack.
     *
     * @return the identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the version of the profile.
     *
     * @return the version
     */
    public String version() {
        return version;
    }

    /**
     * Returns the release of the artefacts.
     *
     * @return the release
     */
    public String release() {
        return release;
    }

    /**
     * Returns who publishes the files.
     *
     * @return the publisher
     */
    public String publisher() {
        return publisher;
    }

    /**
     * Returns the repository the files are published in.
     *
     * @return the URL of the repository
     */
    public String repository() {
        return repository;
    }

    /**
     * Returns the tag of the release the files are taken from, where the publisher tagged
     * the release.
     *
     * @return the tag, or an empty optional where the release has none and the files are
     *         fetched below the {@link #commit() commit}
     */
    public Optional<String> tag() {
        return Optional.ofNullable(tag);
    }

    /**
     * Returns the branch the commit was found on when the recipe was written, where the
     * release has no tag. It is recorded for a reader; the files are never fetched by it.
     *
     * @return the branch, or an empty optional
     */
    public Optional<String> branch() {
        return Optional.ofNullable(branch);
    }

    /**
     * Returns the commit of the release. Where the release has a tag, it is the commit the
     * tag named when the recipe was written: the files are fetched by the tag and weighed
     * against their digests, and the commit is recorded so that a reader can find the same
     * tree if the tag is ever moved. Where it has none, the files are fetched below the
     * commit itself.
     *
     * @return the commit, forty lower case hexadecimal digits
     */
    public String commit() {
        return commit;
    }

    /**
     * Returns what the files are fetched by, in words: {@code tag v3.0.20}, or
     * {@code commit <forty digits> (branch <name>)} for a release without a tag.
     *
     * @return the revision
     */
    public String revision() {
        if (tag != null) {
            return "tag " + tag;
        }
        return "commit " + commit + (branch == null ? "" : " (branch " + branch + ")");
    }

    /**
     * Returns the tag, or for a release without one the commit, as a path segment of the
     * publisher's repository.
     *
     * @return the tag or the commit
     */
    public String treeish() {
        return tag != null ? tag : commit;
    }

    /**
     * Returns the name the publisher gives the release.
     *
     * @return the name
     */
    public String releaseName() {
        return releaseName;
    }

    /**
     * Returns the day the publisher published the release, as {@code YYYY-MM-DD}.
     *
     * @return the date
     */
    public String published() {
        return published;
    }

    /**
     * Returns the URL the files are fetched below.
     *
     * @return the base URL, which ends in {@code /}
     */
    public URI base() {
        return base;
    }

    /**
     * Returns the URL one file of the recipe is fetched from.
     *
     * @param file the path of the file in the publisher's repository
     * @return the URL
     */
    public URI url(String file) {
        return base.resolve(file);
    }

    /**
     * Returns the specification identifiers that name no core invoice usage specification,
     * which the pack records as its {@code baseProfiles}.
     *
     * @return the identifiers
     */
    public List<String> baseProfiles() {
        return baseProfiles;
    }

    /**
     * Returns the components the recipe copies out of bundled packs.
     *
     * @return the components
     */
    public List<Copy> copies() {
        return copies;
    }

    /**
     * Returns the Schematron files the recipe fetches and compiles.
     *
     * @return the files, in the order the pack lists their components
     */
    public List<Schematron> schematron() {
        return schematron;
    }

    /**
     * Tells whether the pack this recipe makes brings the rules of a core invoice usage
     * specification for a document: a rule set of it applies to the syntax and the profile
     * of the document and applies to no identifier of its {@code baseProfiles}. It is what
     * lets a report that found no rules for a profile name the recipe that would bring them.
     *
     * @param syntax          the syntax of the document
     * @param customizationId the customization identifier the document names in BT-24
     * @return whether the pack would carry rules for that profile
     * @throws NullPointerException if an argument is {@code null}
     */
    public boolean bringsRulesFor(InvoiceSyntax syntax, String customizationId) {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        String token = Syntaxes.token(syntax);
        for (Schematron rule : schematron) {
            if (rule.syntaxes().contains(token)
                    && Patterns.matchProfile(rule.profiles(), customizationId)
                    && baseProfiles.stream().noneMatch(
                            base -> Patterns.matchProfile(rule.profiles(), base))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }

    private static Schematron schematron(Map<?, ?> entry, String where) {
        String file = path(entry, "file", where);
        String sha256 = string(entry, "sha256", where);
        if (!SHA256.matcher(sha256).matches()) {
            throw new PackException(where + " pins " + file + " with " + sha256
                    + ", which is not a SHA-256 in lower case hexadecimal");
        }
        long bytes;
        try {
            bytes = Long.parseLong(string(entry, "bytes", where));
        } catch (NumberFormatException e) {
            throw new PackException(where + " gives " + file + " a size that is not a"
                    + " whole number", e);
        }
        if (bytes <= 0) {
            throw new PackException(where + " gives " + file + " a size that is not positive");
        }
        List<String> syntaxes = strings(entry, "syntax", where);
        for (String syntax : syntaxes) {
            if (!List.of(Syntaxes.UBL_INVOICE, Syntaxes.UBL_CREDIT_NOTE, Syntaxes.CII)
                    .contains(syntax)) {
                throw new PackException(where + " names the syntax " + syntax
                        + ", which is not one a pack knows");
            }
        }
        return new Schematron(segment(entry, "name", where), file, bytes, sha256, syntaxes,
                strings(entry, "profile", where), string(entry, "license", where),
                string(entry, "about", where), path(entry, "directory", where));
    }

    private static URI base(String text, String where) {
        try {
            URI uri = new URI(text);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                    || !text.endsWith("/") || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new PackException(where + " fetches below " + text + ", and a recipe"
                        + " fetches over https below a URL that ends in /");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new PackException(where + " fetches below " + text
                    + ", which is not a URL", e);
        }
    }

    private static String path(Map<?, ?> object, String name, String where) {
        String path = string(object, name, where);
        if (!RELATIVE_PATH.matcher(path).matches() || path.contains("..")) {
            throw new PackException(where + " names the path " + path + ", and a path of a"
                    + " recipe is relative and stays inside its directory");
        }
        return path;
    }

    private static String segment(Map<?, ?> object, String name, String where) {
        String segment = string(object, name, where);
        if (!segment.matches("[A-Za-z0-9._-]+") || segment.startsWith(".")) {
            throw new PackException(where + " names " + name + " " + segment
                    + ", which is not one directory name");
        }
        return segment;
    }

    private static Map<?, ?> object(Map<?, ?> object, String name, String where) {
        return asObject(object.get(name), where);
    }

    private static Map<?, ?> asObject(Object value, String where) {
        if (value instanceof Map<?, ?> object) {
            return object;
        }
        throw new PackException(where + " has a member that is not a JSON object");
    }

    private static String string(Map<?, ?> object, String name, String where) {
        Object value = object.get(name);
        if (value instanceof String text) {
            return text;
        }
        throw new PackException(where + " has no member " + name + " that is a JSON string");
    }

    private static String optionalString(Map<?, ?> object, String name, String where) {
        Object value = object.get(name);
        if (value == null && !object.containsKey(name)) {
            return null;
        }
        if (value instanceof String text && !text.isEmpty()) {
            return text;
        }
        throw new PackException(where + " has a member " + name + " that is not a non-empty"
                + " JSON string");
    }

    private static List<?> array(Map<?, ?> object, String name, String where) {
        Object value = object.get(name);
        if (value instanceof List<?> elements) {
            return elements;
        }
        throw new PackException(where + " has no member " + name + " that is a JSON array");
    }

    private static List<String> strings(Map<?, ?> object, String name, String where) {
        List<String> strings = new ArrayList<>();
        for (Object element : array(object, name, where)) {
            if (!(element instanceof String text)) {
                throw new PackException(where + " has a member " + name
                        + " with an element that is not a JSON string");
            }
            strings.add(text);
        }
        return List.copyOf(strings);
    }
}
