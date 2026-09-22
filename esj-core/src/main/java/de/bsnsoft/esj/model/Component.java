package de.bsnsoft.esj.model;

import de.bsnsoft.esj.EsjFormatException;
import java.util.Objects;
import java.util.Optional;

/**
 * A supplementary component of the semantic data type of a business term: the scheme and
 * scheme version of an identifier, and the media type and file name of a binary object
 * (EN 16931-1, 6.5.6 and 6.5.11).
 *
 * <p>A component is not a term of its own. Whether it may or must be used is decided per
 * business term by the registry (specification, section 6.6).
 *
 * <p>Where the standard or the CEN validation artefacts fix the code list a scheme is
 * taken from, the registry records its name in {@code schemeList}. It is documentation:
 * it reaches the Javadoc of the generated accessors and nothing else, because membership
 * of a code list is a business rule and belongs to a rule pack, not to the structural
 * layers (specification, section 10).
 *
 * @param id          the component identifier the registry gives it
 * @param role        the member of the value object that carries this component
 * @param name        the name of the component in the described model
 * @param cardinality the cardinality the registry declares for it
 * @param schemeList  the name of the code list the scheme is taken from, where the model
 *                    fixes one
 */
public record Component(String id,
                        Role role,
                        String name,
                        Cardinality cardinality,
                        Optional<String> schemeList) {

    /**
     * Checks that every part is present and that only a scheme names a code list.
     *
     * @param id          the component identifier the registry gives it
     * @param role        the member of the value object that carries this component
     * @param name        the name of the component in the described model
     * @param cardinality the cardinality the registry declares for it
     * @param schemeList  the name of the code list the scheme is taken from, where the model
     *                    fixes one
     * @throws EsjFormatException   if a component that is not a scheme names a code list
     * @throws NullPointerException if a part is {@code null}
     */
    public Component {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(schemeList, "schemeList");
        if (schemeList.isPresent() && role != Role.SCHEME) {
            throw new EsjFormatException(
                    "only an identification scheme is taken from a code list, and " + id
                            + " carries " + role.jsonMember());
        }
    }

    /**
     * Tells whether the registry declares this component as mandatory for its term.
     *
     * @return {@code true} if the minimum cardinality is at least 1
     */
    public boolean isMandatory() {
        return cardinality.isMandatory();
    }

    /** The member of a value object a supplementary component is carried in. */
    public enum Role {

        /** The {@code scheme} member of an identifier. */
        SCHEME("scheme"),

        /** The {@code schemeVersion} member of an identifier. */
        SCHEME_VERSION("schemeVersion"),

        /** The {@code mimeCode} member of a binary object. */
        MIME_CODE("mimeCode"),

        /** The {@code filename} member of a binary object. */
        FILENAME("filename");

        private final String jsonMember;

        Role(String jsonMember) {
            this.jsonMember = jsonMember;
        }

        /**
         * Returns the name of the value object member this role is carried in.
         *
         * @return the member name, for example {@code schemeVersion}
         */
        public String jsonMember() {
            return jsonMember;
        }

        /**
         * Looks up the role a value object member carries.
         *
         * @param jsonMember the member name
         * @return the role, or an empty optional if no component is carried in that member
         * @throws NullPointerException if {@code jsonMember} is {@code null}
         */
        public static Optional<Role> findByMember(String jsonMember) {
            Objects.requireNonNull(jsonMember, "jsonMember");
            for (Role role : values()) {
                if (role.jsonMember.equals(jsonMember)) {
                    return Optional.of(role);
                }
            }
            return Optional.empty();
        }
    }
}
