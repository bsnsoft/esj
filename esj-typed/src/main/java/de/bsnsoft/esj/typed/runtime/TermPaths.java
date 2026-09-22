package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.SemanticPath;

/**
 * The path arithmetic the typed package shares: how the path of a child of a group
 * instance is built, and how one occurrence of a repeatable term or group is addressed
 * (specification, section 5). The read views and the editors both go through it, so a
 * value is written at the path it is read from.
 */
public final class TermPaths {

    private TermPaths() {
    }

    /**
     * Returns the path of a business term inside a group instance.
     *
     * @param parent the path of the group instance
     * @param termId the identifier of the business term
     * @return the value path
     */
    public static SemanticPath value(SemanticPath parent, String termId) {
        return SemanticPath.of(parent + "/" + termId);
    }

    /**
     * Returns the path of a business group inside a group instance.
     *
     * @param parent  the path of the enclosing group instance
     * @param groupId the identifier of the business group
     * @return the group instance path
     */
    public static SemanticPath group(SemanticPath parent, String groupId) {
        return SemanticPath.group(parent + "/" + groupId);
    }

    /**
     * Returns the path of one occurrence of a repeatable business term or business group.
     *
     * @param step  the path of the repeatable term or group, without an occurrence index
     * @param index the zero-based occurrence index
     * @return the path of that occurrence
     */
    public static SemanticPath indexed(SemanticPath step, int index) {
        String text = step + "/" + index;
        return step.isGroupPath() ? SemanticPath.group(text) : SemanticPath.of(text);
    }
}
