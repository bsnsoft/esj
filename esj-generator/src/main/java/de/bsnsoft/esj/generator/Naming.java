package de.bsnsoft.esj.generator;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the registry members {@code slug} and {@code id} into the Java names of the typed
 * view. The rules are fixed here rather than in the emitters so that every generated file
 * spells a name the same way.
 *
 * <p>Three rules decide a type name:
 *
 * <ol>
 *   <li>the base name is the upper camel case form of the group's slug;</li>
 *   <li>a repeatable group's slug is turned into its singular, because the slug names the
 *       list of its instances while the type names one instance: {@code invoiceLines}
 *       becomes {@code InvoiceLine} and {@code latePaymentPenalties} becomes
 *       {@code LatePaymentPenalty};</li>
 *   <li>a base name that several groups share, or that the typed view already uses for
 *       something else, is qualified with the name of the enclosing group, as often as it
 *       takes to make it unique: the three groups whose slug is {@code postalAddress}
 *       become {@code SellerPostalAddress}, {@code BuyerPostalAddress} and
 *       {@code SellerTaxRepresentativePostalAddress}.</li>
 * </ol>
 *
 * <p>Rule 3 qualifies every group that shares a base name, not only the newcomer. A view
 * generated with an extension registry can therefore name a core group differently from a
 * view generated without it: with {@code model/xrechnung/3.0.2.json} loaded, the core
 * group BG-31 and the extension group BG-DEX-02 both carry the slug {@code item} and
 * become {@code InvoiceLineItem} and {@code SubInvoiceLineItem}. A project that ships a
 * view generates it once, with the registries it means to support.
 *
 * <p>Each group yields four types: the view interface, its implementation, the editor
 * interface ({@code SellerEditor}) and its implementation ({@code SellerEdit}). The three
 * derived names come from the same base name, so a group is named once and read and write
 * never drift apart.
 *
 * <p>A member name is the slug itself. A slug that Java cannot spell as a method name, or
 * that would hide {@code path()}, {@code document()} or {@code builder()}, is a defect of
 * the registry, and this class refuses it instead of inventing a spelling for it.
 */
final class Naming {

    /** The type of the view over the whole document. */
    static final String ROOT_TYPE = "Invoice";

    /** The suffix that turns the name of a view type into the name of its implementation. */
    static final String VIEW_SUFFIX = "View";

    /** The suffix that turns the name of a group into the name of its editor interface. */
    static final String EDITOR_SUFFIX = "Editor";

    /** The suffix that turns the name of a group into the name of its editor class. */
    static final String EDIT_SUFFIX = "Edit";

    private static final Set<String> RESERVED_TYPES = Set.of(
            "BinaryObject", "Editors", "EditorFactory", "EditorList", "En16931",
            "GroupEditorList", "Identifier", "IdentifierList", "MissingValueException",
            "TermIdentifierList", "TermPaths", "TermValueList", "ValueList",
            "ValueTypeException", "ViewFactory", "Values", "Views", "Writers");

    private static final Set<String> RESERVED_MEMBERS = Set.of(
            "builder", "clone", "document", "equals", "finalize", "getClass", "hashCode",
            "notify", "notifyAll", "path", "toString", "wait");

    private static final Set<String> JAVA_RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "false", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
            "throws", "transient", "true", "try", "void", "volatile", "while", "_");

    private final Map<String, String> typeNames;

    /**
     * Computes the type name of every business group of a registry.
     *
     * @param registry the registry the typed view is generated from
     * @throws IllegalStateException if a name cannot be made unique or a slug cannot be a
     *                               Java member name
     */
    Naming(Registry registry) {
        Objects.requireNonNull(registry, "registry");
        this.typeNames = resolveTypeNames(registry);
        for (Term term : registry.terms()) {
            checkMemberName(term);
        }
        checkSingularNames(registry);
    }

    private void checkSingularNames(Registry registry) {
        for (Term term : registry.terms()) {
            if (!term.isGroup() || !term.isRepeatable()) {
                continue;
            }
            String singular = singularMemberName(term);
            List<Term> siblings = term.parent().isPresent()
                    ? registry.children(term.parent().get())
                    : registry.rootTerms();
            for (Term sibling : siblings) {
                if (sibling.slug().equals(singular)) {
                    throw new IllegalStateException("the singular of the slug of " + term.id()
                            + " is the slug of " + sibling.id() + ": " + singular);
                }
            }
        }
    }

    /**
     * Returns the name of the interface that views a business group.
     *
     * @param groupId the identifier of the group
     * @return the interface name
     * @throws IllegalArgumentException if the registry does not know the group
     */
    String typeName(String groupId) {
        String name = typeNames.get(Objects.requireNonNull(groupId, "groupId"));
        if (name == null) {
            throw new IllegalArgumentException("not a business group of the registry: " + groupId);
        }
        return name;
    }

    /**
     * Returns the name of the package-private class that implements a view type.
     *
     * @param typeName the name of the interface
     * @return the name of the implementation class
     */
    static String viewName(String typeName) {
        return typeName + VIEW_SUFFIX;
    }

    /**
     * Returns the name of the editor interface of a business group.
     *
     * @param typeName the name of the view interface of the group
     * @return the name of the editor interface
     */
    static String editorName(String typeName) {
        return typeName + EDITOR_SUFFIX;
    }

    /**
     * Returns the name of the package-private class that implements an editor type.
     *
     * @param typeName the name of the view interface of the group
     * @return the name of the implementation class
     */
    static String editName(String typeName) {
        return typeName + EDIT_SUFFIX;
    }

    /**
     * Returns the name of the accessor of a business term or business group.
     *
     * @param term the child term or group
     * @return the member name, which is the slug of the registry
     */
    String memberName(Term term) {
        return Objects.requireNonNull(term, "term").slug();
    }

    /**
     * Returns the name of the member that appends one instance of a repeatable business
     * group. The slug of such a group names the list of its instances, so the singular of
     * it names one: {@code invoiceLines} yields {@code invoiceLine}.
     *
     * @param term the repeatable group
     * @return the member name of the singular block overload
     * @throws IllegalStateException if the slug has no singular form this rule can take,
     *                               or the singular is a name the typed view reserves
     */
    String singularMemberName(Term term) {
        String singular = singular(Objects.requireNonNull(term, "term").slug());
        if (singular == null) {
            throw new IllegalStateException("the slug of the repeatable group " + term.id()
                    + " has no singular form: " + term.slug());
        }
        if (JAVA_RESERVED.contains(singular) || RESERVED_MEMBERS.contains(singular)) {
            throw new IllegalStateException("the singular of the slug of " + term.id()
                    + " cannot be a member name of the typed view: " + singular);
        }
        return singular;
    }

    /**
     * Returns the Java type an accessor of a business term of a given semantic data type
     * returns. The mapping is the one {@code Values} implements, and the two are read
     * together: this one names the type, that one reads the value.
     *
     * <p>A time is handed out as an {@code OffsetTime}: the semantic data type carries
     * the offset from UTC, so the Java type has to carry it as well, and the two types of
     * {@code java.time} that drop it would turn one instant into another.
     *
     * @param type the semantic data type
     * @return the simple name of the Java type, for example {@code BigDecimal}
     */
    static String valueType(SemanticType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case TEXT, CODE, DOCUMENT_REFERENCE -> "String";
            case IDENTIFIER -> "Identifier";
            case DATE -> "LocalDate";
            case TIME -> "OffsetTime";
            case AMOUNT, UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE -> "BigDecimal";
            case BINARY_OBJECT -> "BinaryObject";
        };
    }

    /**
     * Returns the type a generated file has to import to name {@link #valueType}. The two
     * types of the typed view's own package need none, and neither does
     * {@code java.lang.String}.
     *
     * @param type the semantic data type
     * @return the qualified name to import, or an empty optional
     */
    static Optional<String> valueImport(SemanticType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case DATE -> Optional.of("java.time.LocalDate");
            case TIME -> Optional.of("java.time.OffsetTime");
            case AMOUNT, UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE ->
                    Optional.of("java.math.BigDecimal");
            case TEXT, CODE, DOCUMENT_REFERENCE, IDENTIFIER, BINARY_OBJECT ->
                    Optional.empty();
        };
    }

    /**
     * Returns the reader of {@code Values} an accessor passes to read a value of a given
     * semantic data type.
     *
     * @param type the semantic data type
     * @return the name of the constant, for example {@code Values.UNIT_PRICE_AMOUNT}
     */
    static String valueReader(SemanticType type) {
        return "Values." + Objects.requireNonNull(type, "type").name();
    }

    /**
     * Returns the writer of {@code Writers} a setter passes to write a value of a given
     * semantic data type.
     *
     * @param type the semantic data type
     * @return the name of the constant, for example {@code Writers.UNIT_PRICE_AMOUNT}
     */
    static String valueWriter(SemanticType type) {
        return "Writers." + Objects.requireNonNull(type, "type").name();
    }

    /**
     * Returns the Java type a setter of a business term of a given semantic data type
     * takes. It is the natural type a person writing an invoice has at hand, which for an
     * identifier is the identifier itself, with its scheme in a further parameter rather
     * than in a record the caller has to build first.
     *
     * @param type the semantic data type
     * @return the simple name of the Java type, for example {@code BigDecimal}
     * @throws IllegalArgumentException if the type is Binary Object, whose setter takes
     *                                  the bytes and both components instead of one value
     */
    static String setterType(SemanticType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case TEXT, CODE, DOCUMENT_REFERENCE, IDENTIFIER -> "String";
            case DATE -> "LocalDate";
            case TIME -> "OffsetTime";
            case AMOUNT, UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE -> "BigDecimal";
            case BINARY_OBJECT -> throw new IllegalArgumentException(
                    "the setter of a binary object takes the bytes and both components");
        };
    }

    /**
     * Returns the member name an overlay gives a child of a group: the slug of the child,
     * shortened by the name of the enclosing group where the slug repeats it. It is the
     * rule the registry itself follows when it derives a slug from a business term name,
     * applied one level further out, and it is what makes {@code invoiceLines} read
     * {@code lines} in the overlay of the document.
     *
     * <p>The slug is kept as it stands unless it begins with the name of the enclosing
     * group at a word boundary and what remains is a name a member can have. The typed view
     * of the core model does not use this rule: there the slug is the member name, and a
     * generated accessor spells the term the registry spells.
     *
     * @param enclosingTypeName the type name of the enclosing group, {@link #ROOT_TYPE} for
     *                          the root of the document
     * @param slug              the slug of the child
     * @return the member name of the overlay
     */
    static String overlayMemberName(String enclosingTypeName, String slug) {
        String stem = lowerCamel(Objects.requireNonNull(enclosingTypeName, "enclosingTypeName"));
        Objects.requireNonNull(slug, "slug");
        if (slug.length() <= stem.length()
                || !slug.startsWith(stem)
                || !Character.isUpperCase(slug.charAt(stem.length()))) {
            return slug;
        }
        String rest = lowerCamel(slug.substring(stem.length()));
        return isUsableMemberName(rest) ? rest : slug;
    }

    /**
     * Tells whether a name can be the member name of a generated type: a Java identifier
     * that is neither a keyword of the language nor one of the members every view and
     * editor carries.
     *
     * @param name the candidate name
     * @return {@code true} if a generated accessor may be called that
     */
    static boolean isUsableMemberName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty() || JAVA_RESERVED.contains(name) || RESERVED_MEMBERS.contains(name)
                || !Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the lower camel case form of an upper camel case name.
     *
     * @param name the name
     * @return the name with its first character in lower case
     */
    static String lowerCamel(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalStateException("a type name is not empty");
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Returns the upper camel case form of a lower camel case name.
     *
     * @param name the name
     * @return the name with its first character in upper case
     */
    static String upperCamel(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalStateException("a registry slug is not empty");
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private Map<String, String> resolveTypeNames(Registry source) {
        List<Term> groups = new ArrayList<>();
        for (Term term : source.terms()) {
            if (term.isGroup()) {
                groups.add(term);
            }
        }
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (Term group : groups) {
            levels.put(group.id(), 0);
        }
        for (int round = 0; round <= maxDepth(groups); round++) {
            Map<String, String> candidates = candidates(source, groups, levels);
            Set<String> clashing = clashing(candidates);
            if (clashing.isEmpty()) {
                return candidates;
            }
            boolean raised = false;
            for (Map.Entry<String, String> entry : candidates.entrySet()) {
                if (clashing.contains(entry.getValue())
                        && levels.get(entry.getKey()) < ancestors(source, entry.getKey()).size()) {
                    levels.merge(entry.getKey(), 1, Integer::sum);
                    raised = true;
                }
            }
            if (!raised) {
                throw new IllegalStateException(
                        "the registry slugs do not yield unique type names: " + clashing);
            }
        }
        throw new IllegalStateException("the registry slugs do not yield unique type names");
    }

    private static int maxDepth(List<Term> groups) {
        int depth = 0;
        for (Term group : groups) {
            depth = Math.max(depth, group.depth() + 1);
        }
        return depth;
    }

    private static Map<String, String> candidates(Registry source, List<Term> groups, Map<String, Integer> levels) {
        Map<String, String> names = new LinkedHashMap<>();
        for (Term group : groups) {
            List<Term> ancestors = ancestors(source, group.id());
            int level = Math.min(levels.get(group.id()), ancestors.size());
            StringBuilder name = new StringBuilder();
            for (int i = level - 1; i >= 0; i--) {
                name.append(baseName(ancestors.get(i)));
            }
            name.append(baseName(group));
            names.put(group.id(), name.toString());
        }
        return names;
    }

    private static Set<String> clashing(Map<String, String> candidates) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> clashing = new LinkedHashSet<>();
        for (String name : candidates.values()) {
            if (!seen.add(name)) {
                clashing.add(name);
            }
        }
        for (String name : candidates.values()) {
            if (RESERVED_TYPES.contains(name) || ROOT_TYPE.equals(name)
                    || name.endsWith(VIEW_SUFFIX) || name.endsWith(EDITOR_SUFFIX)
                    || name.endsWith(EDIT_SUFFIX)) {
                clashing.add(name);
            }
        }
        return clashing;
    }

    private static List<Term> ancestors(Registry source, String groupId) {
        List<Term> ancestors = new ArrayList<>();
        String current = groupId;
        while (true) {
            Term term = source.term(current).orElseThrow();
            if (term.parent().isEmpty()) {
                return ancestors;
            }
            current = term.parent().get();
            ancestors.add(source.term(current).orElseThrow());
        }
    }

    private static String baseName(Term group) {
        String slug = group.slug();
        if (group.isRepeatable()) {
            String singular = singular(slug);
            if (singular != null) {
                slug = singular;
            }
        }
        return upperCamel(slug);
    }

    /**
     * Returns the singular of the slug of a repeatable term or group, which the registry
     * writes in the plural because it names the list of the occurrences.
     *
     * <p>Two forms are taken, and they are the two English builds a plural this way:
     * {@code invoiceLines} yields {@code invoiceLine}, and {@code latePaymentPenalties}
     * yields {@code latePaymentPenalty}. Anything else is a slug this convention cannot
     * read, and the caller says so rather than inventing a spelling for it.
     *
     * @param slug the slug of the registry
     * @return the singular, or {@code null} where the slug has none of these two forms
     */
    private static String singular(String slug) {
        if (slug.length() > 3 && slug.endsWith("ies")) {
            return slug.substring(0, slug.length() - 3) + "y";
        }
        if (slug.length() > 1 && slug.endsWith("s")) {
            return slug.substring(0, slug.length() - 1);
        }
        return null;
    }

    private void checkMemberName(Term term) {
        String slug = term.slug();
        if (JAVA_RESERVED.contains(slug) || RESERVED_MEMBERS.contains(slug)) {
            throw new IllegalStateException(
                    "the slug of " + term.id() + " cannot be a member name of the typed view: " + slug);
        }
        if (!Character.isJavaIdentifierStart(slug.charAt(0))) {
            throw new IllegalStateException("the slug of " + term.id() + " does not start a Java name: " + slug);
        }
        for (int i = 1; i < slug.length(); i++) {
            if (!Character.isJavaIdentifierPart(slug.charAt(i))) {
                throw new IllegalStateException("the slug of " + term.id() + " is not a Java name: " + slug);
            }
        }
    }
}
