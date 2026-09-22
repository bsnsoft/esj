package de.bsnsoft.esj.syntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The levels one profile gives rules of the artefacts that run for it.
 *
 * <p>A compiled Schematron artefact flags each of its rules, and that flag is what the
 * artefact says about the rule in general. A core invoice usage specification may say
 * something else about a rule for documents of its own profile, and it is entitled to:
 * it is the body that decides what makes a document of its profile unacceptable. The
 * XRechnung specification does this in both directions. It levels
 * {@code BR-CL-13} down for its CVD profile, because that profile uses an item
 * classification scheme the EN 16931 code list does not carry and rejecting every such
 * invoice would be wrong; and it levels {@code UBL-CR-646} up for its standard profile,
 * because it wants a document that uses that part of the syntax refused rather than
 * noted.
 *
 * <p>So a finding has two levels and a report carries both. {@link SyntaxFinding#flag()}
 * is the artefact's, unchanged, which is what a comparison against any other tool running
 * the same artefact is made on. {@link SyntaxFinding#severity()} is the one the profile
 * asks for, which is what the verdict is made on. Where a table says nothing about a
 * rule, the two are the same, which is the case for almost every rule and every profile.
 *
 * <p>A table is data of the pack rather than a decision of this module; nothing here
 * levels anything. {@code packs/SOURCES.md} records for each table where its entries were
 * published.
 *
 * @param name     the name of the table inside its pack
 * @param syntaxes the syntax tokens it applies to
 * @param profiles the profile patterns it applies to
 * @param levels   the level each rule identifier is given, sorted by identifier
 * @param source   where the publisher states these levels
 */
public record PackLevels(String name,
                         Set<String> syntaxes,
                         List<String> profiles,
                         SortedMap<String, Severity> levels,
                         String source) {

    /**
     * Creates a level table, copying every collection it is given.
     *
     * @param name     the name of the table inside its pack
     * @param syntaxes the syntax tokens it applies to
     * @param profiles the profile patterns it applies to
     * @param levels   the level each rule identifier is given, sorted by identifier
     * @param source   where the publisher states these levels
     * @throws NullPointerException if an argument is {@code null}
     */
    public PackLevels {
        Objects.requireNonNull(name, "name");
        syntaxes = Set.copyOf(Objects.requireNonNull(syntaxes, "syntaxes"));
        profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        levels = new TreeMap<>(Objects.requireNonNull(levels, "levels"));
        Objects.requireNonNull(source, "source");
    }

    /**
     * Tells whether this table applies to a document.
     *
     * @param syntaxToken     the syntax token of the document
     * @param customizationId the customization identifier the document names in BT-24
     * @return whether the table names that syntax and one of its patterns matches
     */
    public boolean appliesTo(String syntaxToken, String customizationId) {
        return syntaxes.contains(syntaxToken)
                && Patterns.matchProfile(profiles, customizationId);
    }

    /**
     * Returns the level this profile gives a rule.
     *
     * @param code the rule identifier
     * @return the level, or an empty optional where the table says nothing about the
     *         rule and the artefact's own flag therefore stands
     */
    public Optional<Severity> level(String code) {
        return Optional.ofNullable(levels.get(code));
    }

    /**
     * Returns the table as a sorted map, which is the form a report prints.
     *
     * @return the level each rule identifier is given, sorted by identifier
     */
    @Override
    public SortedMap<String, Severity> levels() {
        return new TreeMap<>(levels);
    }
}
