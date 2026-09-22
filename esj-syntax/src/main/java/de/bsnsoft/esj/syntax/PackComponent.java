package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One artefact of a validation pack: a set of XML Schema modules, or one compiled
 * Schematron rule set.
 *
 * <p>A component says for itself which documents it applies to. Two facts decide, and
 * both are read from the document rather than from a command line: the syntax, which is
 * the document type of the root element, and the profile, which is the customization
 * identifier the document names in BT-24. A profile pattern is an exact identifier, an
 * identifier followed by {@code *}, which matches any suffix, or {@code *} alone, which
 * matches every profile and is what a schema component carries — an XML Schema does not
 * depend on the profile.
 *
 * @param name         the name of the component inside its pack
 * @param role         what the component is, and therefore how it is run
 * @param syntaxes     the syntax tokens the component applies to
 * @param profiles     the profile patterns the component applies to
 * @param entries      the file to load per syntax token, relative to the pack
 * @param files        every file of the component, relative to the pack
 * @param license      the SPDX identifier the component is distributed under, or a
 *                     {@code LicenseRef-} name where the terms have none
 * @param licenseFile  the licence or notice file beside the component, relative to the
 *                     pack
 * @param source       where the publisher publishes the component
 * @param obtainedFrom the archive the files were taken from
 * @param unmodified   whether the files are byte for byte the published ones, which they
 *                     are
 */
public record PackComponent(String name,
                            ComponentRole role,
                            Set<String> syntaxes,
                            List<String> profiles,
                            Map<String, String> entries,
                            List<String> files,
                            String license,
                            String licenseFile,
                            String source,
                            String obtainedFrom,
                            boolean unmodified) {

    /**
     * Creates a component, copying every collection it is given.
     *
     * @param name         the name of the component inside its pack
     * @param role         what the component is, and therefore how it is run
     * @param syntaxes     the syntax tokens the component applies to
     * @param profiles     the profile patterns the component applies to
     * @param entries      the file to load per syntax token, relative to the pack
     * @param files        every file of the component, relative to the pack
     * @param license      the SPDX identifier the component is distributed under, or a
     *                     {@code LicenseRef-} name where the terms have none
     * @param licenseFile  the licence or notice file beside the component, relative to the
     *                     pack
     * @param source       where the publisher publishes the component
     * @param obtainedFrom the archive the files were taken from
     * @param unmodified   whether the files are byte for byte the published ones, which they
     *                     are
     * @throws NullPointerException if an argument is {@code null}
     */
    public PackComponent {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        syntaxes = Set.copyOf(Objects.requireNonNull(syntaxes, "syntaxes"));
        profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        entries = Map.copyOf(Objects.requireNonNull(entries, "entries"));
        files = List.copyOf(Objects.requireNonNull(files, "files"));
        Objects.requireNonNull(license, "license");
        Objects.requireNonNull(licenseFile, "licenseFile");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(obtainedFrom, "obtainedFrom");
    }

    /**
     * Tells whether this component applies to a document of a syntax.
     *
     * @param syntaxToken the syntax token of the document
     * @return whether the component names that syntax and has an entry file for it
     */
    public boolean appliesToSyntax(String syntaxToken) {
        return syntaxes.contains(syntaxToken) && entries.containsKey(syntaxToken);
    }

    /**
     * Tells whether this component applies to a document of a syntax.
     *
     * <p>It is the same question as {@link #appliesToSyntax(String)} asked in the
     * vocabulary of the importer rather than in that of a manifest, so that a caller who
     * has a document in hand — a report that lists what a pack had to say about it, for
     * one — does not have to learn the words a pack is written in.
     *
     * @param syntax the syntax of the document
     * @return whether the component names that syntax and has an entry file for it
     * @throws NullPointerException if {@code syntax} is {@code null}
     */
    public boolean appliesToSyntax(XrSyntax syntax) {
        return appliesToSyntax(Syntaxes.token(Objects.requireNonNull(syntax, "syntax")));
    }

    /**
     * Tells whether this component applies to a document that names a profile.
     *
     * @param customizationId the customization identifier of the document, empty where
     *                        the document names none
     * @return whether one of the patterns of the component matches
     */
    public boolean appliesToProfile(String customizationId) {
        return Patterns.matchProfile(profiles, customizationId);
    }

    /**
     * Returns the file to load for a document of a syntax.
     *
     * @param syntaxToken the syntax token of the document
     * @return the path of the entry file inside the pack, or an empty optional if the
     *         component has none for that syntax
     */
    public Optional<String> entry(String syntaxToken) {
        return Optional.ofNullable(entries.get(syntaxToken));
    }
}
