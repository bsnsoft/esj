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
 * <p>Instances are made by {@link Registry} and are immutable.
 */
public final class Term {

    /** The components an Identifier has (EN 16931-1, 6.5.6). */
    private static final Set<Component.Role> IDENTIFIER_ROLES =
            EnumSet.of(Component.Role.SCHEME, Component.Role.SCHEME_VERSION);

    /** The components a Binary Object has, both of them mandatory (EN 16931-1, 6.5.11). */
    private static final Set<Component.Role> BINARY_OBJECT_ROLES =
            EnumSet.of(Component.Role.MIME_CODE, Component.Role.FILENAME);

    private final String id;
    private final TermKind kind;
    private final String name;
    private final String slug;
    private final Optional<String> parent;
    private final List<String> path;
    private final int depth;
    private final Cardinality cardinality;
    private final Optional<SemanticType> datatype;
    private final OptionalInt maxDecimals;
    private final Optional<String> maxDecimalsRule;
    private final Optional<String> codeList;
    private final List<Component> components;
    private final List<String> reusesTerms;
    private final int order;
    private final List<String> reqIds;
    private final String description;
    private final List<String> notes;

    Term(String id,
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
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.slug = slug;
        this.parent = parent;
        this.path = path;
        this.depth = depth;
        this.cardinality = cardinality;
        this.datatype = datatype;
        this.maxDecimals = maxDecimals;
        this.maxDecimalsRule = maxDecimalsRule;
        this.codeList = codeList;
        this.components = components;
        this.reusesTerms = reusesTerms;
        this.order = order;
        this.reqIds = reqIds;
        this.description = description;
        this.notes = notes;
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

    /**
     * Returns the identifier, for example {@code BT-131} or {@code BG-DEX-01}.
     *
     * @return the identifier, for example {@code BT-131} or {@code BG-DEX-01}
     */
    public String id() {
        return id;
    }

    /**
     * Returns whether this entry is a business term or a business group.
     *
     * @return the kind
     */
    public TermKind kind() {
        return kind;
    }

    /**
     * Returns the name of the term in the described model.
     *
     * @return the name of the term in the described model
     */
    public String name() {
        return name;
    }

    /**
     * Returns a lower camel case member name for code generation.
     *
     * @return a lower camel case member name for code generation
     */
    public String slug() {
        return slug;
    }

    /**
     * Returns the identifier of the enclosing group, or an empty optional for a term
     * directly at the root.
     *
     * @return the identifier of the enclosing group
     */
    public Optional<String> parent() {
        return parent;
    }

    /**
     * Returns the identifiers from the root down to and including this term.
     *
     * @return the identifiers from the root down to and including this term
     */
    public List<String> path() {
        return path;
    }

    /**
     * Returns the number of enclosing groups.
     *
     * @return the number of enclosing groups
     */
    public int depth() {
        return depth;
    }

    /**
     * Returns the declared cardinality inside one instance of the parent.
     *
     * @return the declared cardinality inside one instance of the parent
     */
    public Cardinality cardinality() {
        return cardinality;
    }

    /**
     * Returns the semantic data type, or an empty optional for a group.
     *
     * @return the semantic data type
     */
    public Optional<SemanticType> datatype() {
        return datatype;
    }

    /**
     * Returns the maximum number of fraction digits, where the edition fixes a constant.
     *
     * @return the maximum number of fraction digits, where the edition fixes a constant
     */
    public OptionalInt maxDecimals() {
        return maxDecimals;
    }

    /**
     * Returns how the edition derives the maximum number of fraction digits from the
     * document, where it fixes no constant; stated in place of {@code maxDecimals} and
     * never beside it.
     *
     * @return how the edition derives the maximum number of fraction digits from
     */
    public Optional<String> maxDecimalsRule() {
        return maxDecimalsRule;
    }

    /**
     * Returns the name of the code list a code is taken from, where there is one.
     *
     * @return the name of the code list a code is taken from, where there is one
     */
    public Optional<String> codeList() {
        return codeList;
    }

    /**
     * Returns the supplementary components of the semantic data type.
     *
     * @return the supplementary components of the semantic data type
     */
    public List<Component> components() {
        return components;
    }

    /**
     * Returns the identifiers of terms of the base model that this extension group carries
     * one level deeper (specification, section 5.6).
     *
     * @return the identifiers of terms of the base model that this extension group carries
     */
    public List<String> reusesTerms() {
        return reusesTerms;
    }

    /**
     * Returns the one-based position of the term in the table of the described model.
     *
     * @return the one-based position of the term in the table of the described model
     */
    public int order() {
        return order;
    }

    /**
     * Returns the requirement identifiers the described edition's term table gives for
     * this entry, empty where it gives none.
     *
     * @return the requirement identifiers the described edition's term table gives for this
     */
    public List<String> reqIds() {
        return reqIds;
    }

    /**
     * Returns one sentence explaining the term, written for the registry.
     *
     * @return one sentence explaining the term, written for the registry
     */
    public String description() {
        return description;
    }

    /**
     * Returns further remarks written for the registry.
     *
     * @return further remarks written for the registry
     */
    public List<String> notes() {
        return notes;
    }

    /**
     * Tells whether another object is of this class and has equal components.
     *
     * @param other the object to compare with
     * @return {@code true} if every component is equal
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Term that
                && Objects.equals(id, that.id)
                && Objects.equals(kind, that.kind)
                && Objects.equals(name, that.name)
                && Objects.equals(slug, that.slug)
                && Objects.equals(parent, that.parent)
                && Objects.equals(path, that.path)
                && depth == that.depth
                && Objects.equals(cardinality, that.cardinality)
                && Objects.equals(datatype, that.datatype)
                && Objects.equals(maxDecimals, that.maxDecimals)
                && Objects.equals(maxDecimalsRule, that.maxDecimalsRule)
                && Objects.equals(codeList, that.codeList)
                && Objects.equals(components, that.components)
                && Objects.equals(reusesTerms, that.reusesTerms)
                && order == that.order
                && Objects.equals(reqIds, that.reqIds)
                && Objects.equals(description, that.description)
                && Objects.equals(notes, that.notes);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}, combined as a record
     * combines the hash codes of its components.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(id);
        hash = 31 * hash + Objects.hashCode(kind);
        hash = 31 * hash + Objects.hashCode(name);
        hash = 31 * hash + Objects.hashCode(slug);
        hash = 31 * hash + Objects.hashCode(parent);
        hash = 31 * hash + Objects.hashCode(path);
        hash = 31 * hash + Integer.hashCode(depth);
        hash = 31 * hash + Objects.hashCode(cardinality);
        hash = 31 * hash + Objects.hashCode(datatype);
        hash = 31 * hash + Objects.hashCode(maxDecimals);
        hash = 31 * hash + Objects.hashCode(maxDecimalsRule);
        hash = 31 * hash + Objects.hashCode(codeList);
        hash = 31 * hash + Objects.hashCode(components);
        hash = 31 * hash + Objects.hashCode(reusesTerms);
        hash = 31 * hash + Integer.hashCode(order);
        hash = 31 * hash + Objects.hashCode(reqIds);
        hash = 31 * hash + Objects.hashCode(description);
        hash = 31 * hash + Objects.hashCode(notes);
        return hash;
    }

    /**
     * Returns the components as one line, in the form a record writes itself.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return "Term[id=" + id
                + ", kind=" + kind
                + ", name=" + name
                + ", slug=" + slug
                + ", parent=" + parent
                + ", path=" + path
                + ", depth=" + depth
                + ", cardinality=" + cardinality
                + ", datatype=" + datatype
                + ", maxDecimals=" + maxDecimals
                + ", maxDecimalsRule=" + maxDecimalsRule
                + ", codeList=" + codeList
                + ", components=" + components
                + ", reusesTerms=" + reusesTerms
                + ", order=" + order
                + ", reqIds=" + reqIds
                + ", description=" + description
                + ", notes=" + notes
                + "]";
    }
}
