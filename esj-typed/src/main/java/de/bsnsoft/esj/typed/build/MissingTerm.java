package de.bsnsoft.esj.typed.build;

import de.bsnsoft.esj.SemanticPath;
import java.util.Objects;

/**
 * One member an invoice has to carry and does not.
 *
 * @param term the identifier of the business term or business group
 * @param name the name of that term or group
 * @param in   the path of the group instance it is missing from; the root path, whose
 *             text is the empty string, stands for the document itself
 */
public record MissingTerm(String term, String name, SemanticPath in) {

    /**
     * Checks that every part is present.
     *
     * @param term the identifier of the business term or business group
     * @param name the name of that term or group
     * @param in   the path of the group instance it is missing from
     * @throws NullPointerException if a part is {@code null}
     */
    public MissingTerm {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(in, "in");
    }

    /**
     * Returns the missing member as one line: the identifier, the name and where it is
     * missing.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return term + " " + name + " is missing "
                + (in.isRoot() ? "at the root of the document" : "in " + in);
    }
}
