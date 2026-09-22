package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One validation pack: the official validation artefacts of one invoice profile, in the
 * form a machine can execute.
 *
 * <p>A pack is named by three parts, and a report needs all three to say which rules
 * ran: the profile ({@code xrechnung}), the version of that profile ({@code 3.0.2}) and
 * the release date of the artefact bundle ({@code 2026-08-31}). Two releases of the same
 * profile version differ — a bugfix release adds, removes or re-levels rules — so the
 * release is part of the identity.
 *
 * <p>Nothing a pack carries is this project's work. Every component keeps the licence it
 * came with, and {@code packs/SOURCES.md} of the repository records for each file where
 * it came from, under which licence and with which digest.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class Pack {

    private final String origin;
    private final PackSource source;
    private final String directory;
    private final String id;
    private final String version;
    private final String release;
    private final String title;
    private final String retrieved;
    private final List<PackComponent> components;
    private final List<PackLevels> levels;
    private final SortedSet<String> baseProfiles;
    private final SortedMap<String, String> files;
    private final PackFiles bytes;

    Pack(String origin,
         PackSource source,
         String directory,
         String id,
         String version,
         String release,
         String title,
         String retrieved,
         List<PackComponent> components,
         List<PackLevels> levels,
         Collection<String> baseProfiles,
         Map<String, String> files,
         PackFiles bytes) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.source = Objects.requireNonNull(source, "source");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.id = Objects.requireNonNull(id, "id");
        this.version = Objects.requireNonNull(version, "version");
        this.release = Objects.requireNonNull(release, "release");
        this.title = Objects.requireNonNull(title, "title");
        this.retrieved = Objects.requireNonNull(retrieved, "retrieved");
        this.components = List.copyOf(Objects.requireNonNull(components, "components"));
        this.levels = List.copyOf(Objects.requireNonNull(levels, "levels"));
        this.baseProfiles = Collections.unmodifiableSortedSet(
                new TreeSet<>(Objects.requireNonNull(baseProfiles, "baseProfiles")));
        this.files = new TreeMap<>(Objects.requireNonNull(files, "files"));
        this.bytes = Objects.requireNonNull(bytes, "bytes");
    }

    /**
     * Returns where the artefacts of this pack came from.
     *
     * <p>It is the one thing about a pack that the pack does not get to say. Everything
     * else a report prints about it — the identity, the title, the release — is read out
     * of a manifest, and a directory a caller points {@code --pack} at is free to write
     * the identity of the reviewed release into its own. A reader of the report, or the
     * repository it was checked into, has no other way to tell the two apart.
     *
     * @return {@link PackSource#BUNDLED} for a pack of this build, and
     *         {@link PackSource#SUPPLIED} for one read from a directory the caller named
     */
    public PackSource source() {
        return source;
    }

    /**
     * Returns the identifier of the profile this pack validates, for example
     * {@code xrechnung}.
     *
     * @return the pack identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the version of the profile, for example {@code 3.0.2}.
     *
     * @return the profile version
     */
    public String version() {
        return version;
    }

    /**
     * Returns the release date of the artefact bundle, for example {@code 2026-08-31}.
     *
     * @return the release
     */
    public String release() {
        return release;
    }

    /**
     * Returns the three parts of the identity as one path, {@code id/version/release}.
     * It is the directory the pack has under {@code packs/} and the name a report prints.
     *
     * @return the pack identity
     */
    public String directory() {
        return directory;
    }

    /**
     * Returns the title the manifest gives the pack, in English.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * Returns the day the files were fetched from their publishers, as
     * {@code YYYY-MM-DD}.
     *
     * @return the retrieval date
     */
    public String retrieved() {
        return retrieved;
    }

    /**
     * Returns the components of the pack, in the order the manifest lists them.
     *
     * @return the components
     */
    public List<PackComponent> components() {
        return components;
    }

    /**
     * Returns the level tables of the pack, in the order the manifest lists them.
     *
     * @return the tables, empty where no profile of this pack levels a rule of its own
     */
    public List<PackLevels> levels() {
        return levels;
    }

    /**
     * Returns the specification identifiers that name no core invoice usage specification.
     *
     * <p>A document that carries one of them is judged by the rules that apply to it and
     * by nothing else, and that is the whole of what this pack has to say about it. The
     * distinction cannot be read off the components: a CIUS rule set is skipped for such a
     * document exactly as it is for a document naming a specification nobody here holds
     * rules for, and only one of the two is a gap.
     *
     * @return the identifiers, in ascending order, empty where the manifest names none
     */
    public SortedSet<String> baseProfiles() {
        return baseProfiles;
    }

    /**
     * Returns the licences the components of this pack are distributed under, as SPDX
     * identifiers where the terms have one and as {@code LicenseRef-} names where they do
     * not.
     *
     * @return the licences, sorted, without repetition
     */
    public SortedSet<String> licenses() {
        SortedSet<String> licenses = new TreeSet<>();
        components.forEach(component -> licenses.add(component.license()));
        return licenses;
    }

    /**
     * Returns every file of the pack with the SHA-256 the manifest records for it.
     *
     * @return the inventory, keyed by the path of the file inside the pack
     */
    public SortedMap<String, String> files() {
        return new TreeMap<>(files);
    }

    /**
     * Chooses the components that apply to a document.
     *
     * <p>A component applies when the document's syntax is one it names and one of its
     * profile patterns matches the customization identifier of the document. Everything
     * else is skipped with the reason, and a profile no component of the pack recognizes
     * leaves a note on the selection: the rules that do not depend on a profile still
     * ran, and the ones that do did not. Where a rule set was one of those left out,
     * {@link PackSelection#profileRulesSkipped()} says so as a fact a program can branch
     * on, because the note is a sentence and a sentence is not an interface.
     *
     * <p>A rule set left out for the profile is not by itself a gap, and the selection
     * says which of the two it is. The manifest's {@code baseProfiles} are the
     * specification identifiers that name no core invoice usage specification: a document
     * that carries one of them asked for the rules that did run and for nothing else, so
     * the check is complete. A document that names anything else and leaves a rule set
     * unused asked to be judged by rules this pack does not carry, and
     * {@link PackSelection#profileRulesMissing()} says so.
     *
     * <p>The level table of the selection is the first one of the manifest that names
     * the syntax and matches the profile, and there is none where no table does.
     *
     * @param syntax          the syntax of the document
     * @param customizationId the customization identifier the document names in BT-24,
     *                        empty where it names none
     * @return what applies, what does not, at which levels, and why
     * @throws NullPointerException if an argument is {@code null}
     */
    public PackSelection select(XrSyntax syntax, String customizationId) {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        String token = Syntaxes.token(syntax);
        List<PackComponent> applied = new ArrayList<>();
        List<SkippedComponent> skipped = new ArrayList<>();
        boolean profileSkipped = false;
        boolean profileRulesSkipped = false;
        for (PackComponent component : components) {
            if (!component.appliesToSyntax(token)) {
                skipped.add(new SkippedComponent(component.name(),
                        SkippedComponent.Reason.OTHER_SYNTAX,
                        "it validates no " + Syntaxes.title(syntax)));
                continue;
            }
            if (!component.appliesToProfile(customizationId)) {
                skipped.add(new SkippedComponent(component.name(),
                        SkippedComponent.Reason.OTHER_PROFILE,
                        "it validates no document of the profile this one names"));
                profileSkipped = true;
                profileRulesSkipped |= component.role() == ComponentRole.SCHEMATRON_XSLT;
                continue;
            }
            applied.add(component);
        }
        return new PackSelection(this, syntax, customizationId, applied, skipped,
                levels.stream().filter(table -> table.appliesTo(token, customizationId))
                        .findFirst(),
                profileNote(profileSkipped, applied),
                profileRulesSkipped,
                profileRulesSkipped && !baseProfiles.contains(customizationId));
    }

    /**
     * Reads a file of the pack, and weighs it against the digest the manifest records for
     * it.
     *
     * <p>The inventory is checked here rather than trusted, because a run that does not
     * weigh what it executes cannot say which official rules decided a verdict: the claim
     * that a component is the released file is worth exactly as much as the digest behind
     * it, and for a pack a caller points at nothing else establishes it at all. It costs
     * one pass over the file, once per process, at the moment the artefact is compiled.
     *
     * @param path the path of the file inside the pack, with {@code /} as the separator
     * @return the bytes of the file
     * @throws PackException        if no such file belongs to the pack, it cannot be read,
     *                              or it is not the file the manifest records
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public byte[] read(String path) {
        Objects.requireNonNull(path, "path");
        String recorded = files.get(path);
        if (recorded == null) {
            throw new PackException("the pack " + directory + " lists no file " + path);
        }
        byte[] content = bytes.read(path);
        String weighed = sha256(content);
        if (!weighed.equalsIgnoreCase(recorded)) {
            throw new PackException("the file " + path + " of the pack " + directory
                    + " is not the file its manifest records: the manifest has sha256 "
                    + recorded + " and the file weighs " + weighed);
        }
        return content;
    }

    /**
     * Returns the key one artefact of this pack is cached under while a process lives.
     *
     * <p>It names where the file came from and what is in it — the class path or the
     * canonical directory a caller pointed at, the path inside the pack, and the digest
     * the manifest records, which {@link #read(String)} has established by the time the
     * artefact is compiled. What it deliberately does not name is the identity a pack
     * declares about itself: a directory is free to call itself by the identity of a
     * packaged pack, and a cache keyed on that account would let it answer for the
     * packaged one for the rest of the process.
     *
     * @param path the path of the file inside the pack
     * @return the cache key
     */
    String cacheKey(String path) {
        return origin + "|" + path + "|" + files.getOrDefault(path, "");
    }

    /** Returns the SHA-256 of some bytes, as lower case hexadecimal. */
    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("this platform has no SHA-256", e);
        }
    }

    /**
     * Returns the URI a file of this pack is loaded under.
     *
     * <p>The scheme is one nothing can dereference, so a reference inside an artefact
     * resolves against it and reaches the resolver of this module rather than a file or a
     * host.
     *
     * @param path the path of the file inside the pack
     * @return the URI
     */
    String uri(String path) {
        return PackFiles.SCHEME + ":/" + path;
    }

    @Override
    public String toString() {
        return directory;
    }

    /**
     * Returns the note a selection carries when a rule set of the pack was left out
     * because of the profile the document names. It is empty when every component that
     * was skipped was skipped for the syntax rather than for the profile.
     *
     * <p>The note says what did not run rather than judging the profile, because the two
     * cases it covers are not the same thing. A document that names EN 16931 and no core
     * invoice usage specification beyond it is a document this pack understands perfectly
     * well: the CEN artefacts apply to it and no CIUS does. A document that names a
     * specification the pack carries no rules for is a document that was checked less
     * thoroughly than it asked to be. Both need the same sentence — these rule sets ran,
     * those did not — and a reader who wants to know which case it is has the profile in
     * the line above.
     */
    private static Optional<String> profileNote(boolean profileSkipped,
                                                List<PackComponent> applied) {
        if (!profileSkipped) {
            return Optional.empty();
        }
        boolean schematronApplied = applied.stream()
                .anyMatch(component -> component.role() == ComponentRole.SCHEMATRON_XSLT);
        return Optional.of(schematronApplied
                ? "no core invoice usage specification of this pack applies to this"
                        + " profile: the EN 16931 artefacts ran, no CIUS rules did"
                : "no rule set of this pack applies to this profile: schema validation"
                        + " only");
    }
}
