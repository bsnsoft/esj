package de.bsnsoft.esj.model;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.TermKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * One entry of the registry: a business term or a business group, with the structural
 * facts the validation layers are defined against and the documentation members the
 * registry carries beside them (specification, section 10).
 *
 * <p>The members {@code slug}, {@code order}, {@code codeList}, {@code maxDecimals},
 * {@code maxDecimalsRule}, {@code reqIds}, {@code description} and {@code notes} are
 * documentation and code generation aids and never affect validation or the canonical
 * form.
 *
 * @param id          the identifier, for example {@code BT-131} or {@code BG-DEX-01}
 * @param kind        business term or business group
 * @param name        the name of the term in the described model
 * @param slug        a lower camel case member name for code generation
 * @param parent      the identifier of the enclosing group, or an empty optional for a
 *                    term directly at the root
 * @param path        the identifiers from the root down to and including this term
 * @param depth       the number of enclosing groups
 * @param cardinality the declared cardinality inside one instance of the parent
 * @param datatype    the semantic data type, or an empty optional for a group
 * @param maxDecimals the maximum number of fraction digits, where the edition fixes a
 *                    constant
 * @param maxDecimalsRule how the edition derives the maximum number of fraction digits
 *                    from the document, where it fixes no constant; stated in place of
 *                    {@code maxDecimals} and never beside it
 * @param codeList    the name of the code list a code is taken from, where there is one
 * @param components  the supplementary components of the semantic data type
 * @param reusesTerms the identifiers of terms of the base model that this extension group
 *                    carries one level deeper (specification, section 5.6)
 * @param order       the one-based position of the term in the table of the described model
 * @param reqIds      the requirement identifiers the described edition's term table gives
 *                    for this entry, empty where it gives none
 * @param description one sentence explaining the term, written for the registry
 * @param notes       further remarks written for the registry
 */
public record Term(String id,
                   TermKind kind,
                   String name,
                   String slug,
                   Optional<String> parent,
                   List<String> path,
                   int depth,
                   Cardinality cardinality,
                   Optional<SemanticType> datatype,
                   OptionalInt maxDecimals,
                   Optional<String> maxDecimalsRule,
                   Optional<String> codeList,
                   List<Component> components,
                   List<String> reusesTerms,
                   int order,
                   List<String> reqIds,
                   String description,
                   List<String> notes) {

    /** The components an Identifier has (EN 16931-1, 6.5.6). */
    private static final Set<Component.Role> IDENTIFIER_ROLES =
            EnumSet.of(Component.Role.SCHEME, Component.Role.SCHEME_VERSION);

    /** The components a Binary Object has, both of them mandatory (EN 16931-1, 6.5.11). */
    private static final Set<Component.Role> BINARY_OBJECT_ROLES =
            EnumSet.of(Component.Role.MIME_CODE, Component.Role.FILENAME);

    /**
     * Copies the collections, checks that every part is present, and checks the
     * components against the semantic data type: a component role belongs to exactly one
     * data type, a Binary Object carries both of its components and both are mandatory
     * whatever its component list says, and a scheme version stands only beside a scheme
     * and is mandatory only beside a mandatory one (EN 16931-1, 6.5.6 and 6.5.11;
     * specification, section 6.2). The registry is the single source of typing, so a
     * combination the model does not have is refused here rather than carried into the
     * validator, the generated schema and the typed view.
     *
     * @param id          the identifier, for example {@code BT-131} or {@code BG-DEX-01}
     * @param kind        business term or business group
     * @param name        the name of the term in the described model
     * @param slug        a lower camel case member name for code generation
     * @param parent      the identifier of the enclosing group, or an empty optional for a
     *                    term directly at the root
     * @param path        the identifiers from the root down to and including this term
     * @param depth       the number of enclosing groups
     * @param cardinality the declared cardinality inside one instance of the parent
     * @param datatype    the semantic data type, or an empty optional for a group
     * @param maxDecimals the maximum number of fraction digits, where the edition fixes a
     *                    constant
     * @param maxDecimalsRule how the edition derives the maximum number of fraction digits
     *                    from the document, where it fixes no constant; stated in place of
     *                    {@code maxDecimals} and never beside it
     * @param codeList    the name of the code list a code is taken from, where there is one
     * @param components  the supplementary components of the semantic data type
     * @param reusesTerms the identifiers of terms of the base model that this extension group
     *                    carries one level deeper (specification, section 5.6)
     * @param order       the one-based position of the term in the table of the described model
     * @param reqIds      the requirement identifiers the described edition's term table gives
     *                    for this entry, empty where it gives none
     * @param description one sentence explaining the term, written for the registry
     * @param notes       further remarks written for the registry
     * @throws NullPointerException     if a part is {@code null}
     * @throws IllegalArgumentException if the path is empty or does not end at this term
     * @throws EsjFormatException       if the components do not fit the semantic data type
     */
    public Term {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(datatype, "datatype");
        Objects.requireNonNull(maxDecimals, "maxDecimals");
        Objects.requireNonNull(maxDecimalsRule, "maxDecimalsRule");
        Objects.requireNonNull(codeList, "codeList");
        Objects.requireNonNull(description, "description");
        path = List.copyOf(path);
        components = List.copyOf(components);
        reusesTerms = List.copyOf(reusesTerms);
        reqIds = List.copyOf(reqIds);
        notes = List.copyOf(notes);
        if (maxDecimals.isPresent() && maxDecimalsRule.isPresent()) {
            throw new EsjFormatException(id + " states its decimal limit as a number and"
                    + " as a rule, and two limits are two answers to one question");
        }
        if (path.isEmpty() || !path.get(path.size() - 1).equals(id)) {
            throw new IllegalArgumentException("the path of " + id + " ends at " + id);
        }
        checkComponents(id, datatype, components);
    }

    private static void checkComponents(String id,
                                        Optional<SemanticType> datatype,
                                        List<Component> components) {
        Set<Component.Role> roles = EnumSet.noneOf(Component.Role.class);
        for (Component component : components) {
            if (!roles.add(component.role())) {
                throw new EsjFormatException(id + " lists the component "
                        + component.role().jsonMember() + " twice");
            }
        }
        if (datatype.orElse(null) == SemanticType.BINARY_OBJECT) {
            checkBinaryObject(id, roles, components);
            return;
        }
        if (components.isEmpty()) {
            return;
        }
        SemanticType type = datatype.orElseThrow(() -> new EsjFormatException(
                id + " carries supplementary components but no semantic data type"));
        Set<Component.Role> allowed = switch (type) {
            case IDENTIFIER -> IDENTIFIER_ROLES;
            case BINARY_OBJECT -> BINARY_OBJECT_ROLES;
            default -> Set.of();
        };
        for (Component.Role role : roles) {
            if (!allowed.contains(role)) {
                throw new EsjFormatException("the semantic data type " + type.registryDatatype()
                        + " of " + id + " has no component " + role.jsonMember());
            }
        }
        if (roles.contains(Component.Role.SCHEME_VERSION)
                && !roles.contains(Component.Role.SCHEME)) {
            throw new EsjFormatException(
                    id + " carries a scheme version without a scheme");
        }
        if (mandatory(components, Component.Role.SCHEME_VERSION)
                && !mandatory(components, Component.Role.SCHEME)) {
            throw new EsjFormatException(id + " declares its scheme version mandatory and"
                    + " its scheme optional, and no value can satisfy both");
        }
    }

    /**
     * Checks the components of a Binary Object, whose two components are a property of
     * the semantic data type rather than of the term: both are present and both are
     * mandatory (EN 16931-1, 6.5.11; specification, section 6.2). An empty component list
     * is therefore the defect itself and not the absence of one, which is why this runs
     * before the shortcut that lets a term without components pass.
     */
    private static void checkBinaryObject(String id,
                                          Set<Component.Role> roles,
                                          List<Component> components) {
        if (!roles.equals(BINARY_OBJECT_ROLES)) {
            throw new EsjFormatException("the Binary Object " + id
                    + " carries the components mimeCode and filename");
        }
        for (Component component : components) {
            if (!component.isMandatory()) {
                throw new EsjFormatException("the component "
                        + component.role().jsonMember() + " of the Binary Object " + id
                        + " is mandatory");
            }
        }
    }

    /** Tells whether the component in that role is present and declared mandatory. */
    private static boolean mandatory(List<Component> components, Component.Role role) {
        for (Component component : components) {
            if (component.role() == role) {
                return component.isMandatory();
            }
        }
        return false;
    }

    /**
     * Tells whether this entry is a business group.
     *
     * @return {@code true} for a group
     */
    public boolean isGroup() {
        return kind == TermKind.BG;
    }

    /**
     * Tells whether this term may occur more than once inside one instance of its parent
     * and therefore carries an occurrence index in every path.
     *
     * @return {@code true} if the declared maximum is greater than 1
     */
    public boolean isRepeatable() {
        return cardinality.isRepeatable();
    }

    /**
     * Tells whether this term must be present in every instance of its parent.
     *
     * @return {@code true} if the declared minimum is at least 1
     */
    public boolean isMandatory() {
        return cardinality.isMandatory();
    }

    /**
     * Returns the supplementary component carried in a given value object member.
     *
     * @param role the member of the value object
     * @return the component, or an empty optional if the registry lists none for this term
     * @throws NullPointerException if {@code role} is {@code null}
     */
    public Optional<Component> component(Component.Role role) {
        Objects.requireNonNull(role, "role");
        for (Component component : components) {
            if (component.role() == role) {
                return Optional.of(component);
            }
        }
        return Optional.empty();
    }
}
