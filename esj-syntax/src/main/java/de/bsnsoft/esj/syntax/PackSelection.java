package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a pack applies to one document: the components that run, the components that do
 * not and why, and a note where the document names a profile the pack does not know.
 *
 * @param pack            the pack the components belong to
 * @param syntax          the syntax of the document
 * @param customizationId the customization identifier the document names in BT-24, empty
 *                        where it names none
 * @param applied         the components that apply, in the order the manifest lists them
 * @param skipped         the components that do not apply, each with its reason
 * @param levels          the levels the profile gives rules of those components, empty
 *                        where it levels none and the flags of the artefacts stand
 * @param profileNote     a note in English where the document names a profile no
 *                        component of the pack recognizes
 * @param profileRulesSkipped whether a rule set of the pack was left out because of the
 *                        profile the document names, which is the fact behind the note
 *                        and the one a program can branch on: a verdict reached after it
 *                        is a verdict of the schema and of whatever else did run
 * @param profileRulesMissing whether the rule set that was left out was left out for a
 *                        specification the document names and this pack carries no rules
 *                        for. It is the narrower half of {@code profileRulesSkipped} and
 *                        the one a verdict turns on: a document that names EN 16931 and no
 *                        core invoice usage specification also leaves the CIUS rule sets
 *                        unused, and that is a complete check rather than a gap, while a
 *                        document that asked to be judged by rules nobody here holds was
 *                        checked less thoroughly than it asked to be
 */
public record PackSelection(Pack pack,
                            XrSyntax syntax,
                            String customizationId,
                            List<PackComponent> applied,
                            List<SkippedComponent> skipped,
                            Optional<PackLevels> levels,
                            Optional<String> profileNote,
                            boolean profileRulesSkipped,
                            boolean profileRulesMissing) {

    /**
     * Creates a selection, copying the lists it is given.
     *
     * @param pack            the pack the components belong to
     * @param syntax          the syntax of the document
     * @param customizationId the customization identifier the document names in BT-24, empty
     *                        where it names none
     * @param applied         the components that apply, in the order the manifest lists them
     * @param skipped         the components that do not apply, each with its reason
     * @param levels          the levels the profile gives rules of those components, empty
     *                        where it levels none and the flags of the artefacts stand
     * @param profileNote     a note in English where the document names a profile no
     *                        component of the pack recognizes
     * @param profileRulesSkipped whether a rule set of the pack was left out because of the
     *                        profile the document names, which is the fact behind the note
     *                        and the one a program can branch on: a verdict reached after it
     *                        is a verdict of the schema and of whatever else did run
     * @param profileRulesMissing whether the rule set that was left out was left out for a
     *                        specification the document names and this pack carries no rules
     *                        for. It is the narrower half of {@code profileRulesSkipped} and
     *                        the one a verdict turns on: a document that names EN 16931 and no
     *                        core invoice usage specification also leaves the CIUS rule sets
     *                        unused, and that is a complete check rather than a gap, while a
     *                        document that asked to be judged by rules nobody here holds was
     *                        checked less thoroughly than it asked to be
     * @throws NullPointerException if an argument is {@code null}
     */
    public PackSelection {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        applied = List.copyOf(Objects.requireNonNull(applied, "applied"));
        skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(profileNote, "profileNote");
    }

    /**
     * Returns the components of a role that apply, in manifest order.
     *
     * @param role the role
     * @return the components
     */
    public List<PackComponent> applied(ComponentRole role) {
        return applied.stream().filter(component -> component.role() == role).toList();
    }
}
