package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The levels one profile gives rules, as a lookup a caller can ask outside the syntax
 * engine.
 *
 * <p>{@link PackLevels} is one table of a pack; this is the answer to the question a
 * report has to ask of a document: the specification the document names in BT-24 levels
 * these rule identifiers this way, and says nothing about the rest. The syntax engine
 * asks it through {@link PackSelection} while it runs the artefacts of the pack. A caller
 * that reports findings of another engine about the same document asks it here, because
 * the levels are a fact of the profile rather than of the artefact that reported a rule
 * or of the syntax the document arrived in: what a core invoice usage specification says
 * about {@code BR-CL-13} for documents of its profile is the same statement whoever
 * raises the rule.
 *
 * <p>Which table answers depends on how much is known about the document. Where the
 * syntax is known — the document arrived as XML — it is the table of that syntax and that
 * profile, which is the one the syntax engine ran with, so the two agree by construction.
 * Where it is not known, because the document was never XML, every table of the pack that
 * matches the profile is consulted and a rule is levelled only where all of them level it
 * the same way. That is the conservative reading: a level one scenario of a profile states
 * and another does not is not a statement of the profile about a document of no syntax.
 *
 * <p>Nothing here levels anything. A table is data of the pack, transcribed from what its
 * publisher released, and {@code packs/SOURCES.md} records for each of them where.
 */
public final class ProfileLevels {

    private static final ProfileLevels NONE = new ProfileLevels("", new TreeMap<>());

    private final String profile;
    private final SortedMap<String, Severity> levels;

    private ProfileLevels(String profile, SortedMap<String, Severity> levels) {
        this.profile = profile;
        this.levels = levels;
    }

    /**
     * Returns the lookup of a profile that levels nothing.
     *
     * <p>It is the answer for a document that names no profile and for one whose profile
     * the pack has no table for; the level of a rule is then the one the rule was given
     * where it was raised.
     *
     * @return the empty lookup
     */
    public static ProfileLevels none() {
        return NONE;
    }

    /**
     * Returns the levels a pack gives a document of one syntax and one profile.
     *
     * @param pack            the pack
     * @param syntax          the syntax of the document
     * @param customizationId the customization identifier the document names in BT-24
     * @return the lookup, which levels nothing where no table of the pack applies
     * @throws NullPointerException if an argument is {@code null}
     */
    public static ProfileLevels of(Pack pack, XrSyntax syntax, String customizationId) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        String token = Syntaxes.token(syntax);
        return pack.levels().stream()
                .filter(table -> table.appliesTo(token, customizationId))
                .findFirst()
                .map(table -> new ProfileLevels(customizationId, table.levels()))
                .orElse(NONE);
    }

    /**
     * Returns the levels a pack gives a document of one profile whose syntax is not known,
     * which is a document that was never XML.
     *
     * @param pack            the pack
     * @param customizationId the customization identifier the document names in BT-24
     * @return the lookup, carrying the rules every table of that profile levels the same
     *         way
     * @throws NullPointerException if an argument is {@code null}
     */
    public static ProfileLevels of(Pack pack, String customizationId) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(customizationId, "customizationId");
        List<PackLevels> tables = new ArrayList<>();
        for (PackLevels table : pack.levels()) {
            if (Patterns.matchProfile(table.profiles(), customizationId)) {
                tables.add(table);
            }
        }
        return tables.isEmpty() ? NONE : new ProfileLevels(customizationId, agreed(tables));
    }

    /**
     * Returns the rules every table levels, at the level all of them give it.
     *
     * <p>A rule one table levels and another does not mention is left out, because the
     * table that does not mention it lets the level of the artefact stand and the two
     * together therefore say nothing about a document of no syntax.
     */
    private static SortedMap<String, Severity> agreed(List<PackLevels> tables) {
        SortedMap<String, Severity> agreed = new TreeMap<>();
        for (Map.Entry<String, Severity> entry : tables.get(0).levels().entrySet()) {
            boolean everywhere = tables.stream().allMatch(table -> table.level(entry.getKey())
                    .filter(level -> level == entry.getValue()).isPresent());
            if (everywhere) {
                agreed.put(entry.getKey(), entry.getValue());
            }
        }
        return agreed;
    }

    /**
     * Returns the level this profile gives a rule.
     *
     * @param code the rule identifier
     * @return the level, or an empty optional where the profile says nothing about the
     *         rule and the level it was raised at therefore stands
     * @throws NullPointerException if {@code code} is {@code null}
     */
    public Optional<Severity> level(String code) {
        Objects.requireNonNull(code, "code");
        return Optional.ofNullable(levels.get(code));
    }

    /**
     * Returns the rules this profile levels at all.
     *
     * <p>It is what a caller needs in order to compare two of these lookups, which is a
     * question the levels of one profile for two syntaxes raise: the same rule of the
     * standard may be levelled for one syntax of a profile and not for the other.
     *
     * @return the rule identifiers, in order, empty where nothing is levelled
     */
    public Set<String> codes() {
        return Collections.unmodifiableSet(levels.keySet());
    }

    /**
     * Returns the profile these levels are those of.
     *
     * @return the customization identifier, empty where nothing is levelled
     */
    public String profile() {
        return profile;
    }

    /**
     * Tells whether this lookup levels any rule at all.
     *
     * @return whether a table applied and carries at least one rule
     */
    public boolean any() {
        return !levels.isEmpty();
    }
}
