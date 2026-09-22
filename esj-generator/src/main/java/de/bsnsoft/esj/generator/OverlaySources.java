package de.bsnsoft.esj.generator;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Cardinality;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Emits the sources of an overlay: a second, small view and editor that show the terms of
 * one extension registry and nothing else, addressed through the paths of the core model.
 *
 * <p>The typed view of {@code esj-typed} is generated from the core registry alone, and it
 * stays that way: a project that adopts an extension does not get a different core API.
 * The overlay is the other half of that decision. It is a separate package with a separate
 * entry point, and every one of its accessors lies on a path the core model already
 * defines, so one document is read and written by both at once and neither knows about the
 * other. {@code B2c.of(document).lines().get(0)} and
 * {@code En16931.view(document).invoiceLines().get(0)} address the same invoice line,
 * because both build the path {@code /BG-25/0} with the same arithmetic.
 *
 * <p>A type is emitted for the root of the document, for every group of the extension, and
 * for every core group that has a term of the extension somewhere below it — the last so
 * that the overlay has a way down to it. A type carries a member for each child of its
 * group that the extension defines, and for each child group that leads to one. It carries
 * nothing else: the core terms of a group are the business of the typed view, and the
 * overlay never repeats an accessor for one.
 *
 * <p>The emitter refuses three shapes rather than emitting something the overlay runtime
 * cannot carry out: a repeatable business term of the extension, and a term whose semantic
 * data type is Identifier or Binary Object, both of which carry supplementary components.
 * No extension registry of this repository has one, and a registry that does needs the
 * overlay runtime extended before it can be emitted.
 *
 * <p>The emitted text depends on the registries alone. It carries no timestamp, no host
 * name and no path, and the order of everything in it is the order of the registry, so two
 * runs over the same registries produce the same bytes.
 */
final class OverlaySources {

    private static final String DOCUMENT_TYPE = "de.bsnsoft.esj.SemanticDocument";
    private static final String PATH_TYPE = "de.bsnsoft.esj.SemanticPath";
    private static final String REGISTRY_TYPE = "de.bsnsoft.esj.model.Registry";
    private static final String MISSING_TYPE = TypedSources.BASE_PACKAGE + ".MissingValueException";
    private static final String VALUE_TYPE_EXCEPTION = TypedSources.BASE_PACKAGE + ".ValueTypeException";
    private static final String RUNTIME_PACKAGE = TypedSources.BASE_PACKAGE + ".runtime";

    private final Registry registry;
    private final Registry extension;
    private final Set<String> extensionTerms;
    private final Set<String> leadsToExtension;
    private final String packageName;
    private final String prefix;
    private final String header;
    private final Naming naming;

    /**
     * Prepares the emitter.
     *
     * @param registry    the core registry with the extension loaded, which is the registry
     *                    the paths of the overlay are built from
     * @param extension   the extension registry alone, which decides which terms the
     *                    overlay shows
     * @param packageName the package the overlay is emitted into
     * @param prefix      the name of the entry point class, which also prefixes every type
     *                    of the overlay, for example {@code B2c}
     * @param header      the three-line header every generated file carries
     * @throws IllegalStateException if the extension registry carries no term
     */
    OverlaySources(Registry registry,
                   Registry extension,
                   String packageName,
                   String prefix,
                   String header) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.extension = Objects.requireNonNull(extension, "extension");
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.header = Objects.requireNonNull(header, "header");
        this.naming = new Naming(registry);
        Set<String> ids = new LinkedHashSet<>();
        for (Term term : extension.terms()) {
            ids.add(term.id());
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("the extension registry carries no term");
        }
        this.extensionTerms = ids;
        this.leadsToExtension = leadsToExtension();
    }

    /**
     * Returns the package an overlay with a given entry point name is emitted into: the
     * package of the typed view, with its last segment replaced by the entry point name in
     * lower case.
     *
     * @param prefix the name of the entry point class, for example {@code B2c}
     * @return the package name, for example {@code de.bsnsoft.esj.b2c}
     */
    static String packageOf(String prefix) {
        String typed = TypedSources.DEFAULT_PACKAGE;
        return typed.substring(0, typed.lastIndexOf('.') + 1)
                + Objects.requireNonNull(prefix, "prefix").toLowerCase(Locale.ROOT);
    }

    /**
     * Emits every source file of the overlay.
     *
     * @return the file names and their content, the entry point first and the types after
     *         it in the order of the registry
     * @throws IllegalStateException if the extension places no term inside a document, if a
     *                               member name cannot be made unique, or if the extension
     *                               carries a shape the overlay runtime has no way to read
     *                               or write
     */
    Map<String, String> sources() {
        List<Group> groups = groups();
        Map<String, String> files = new LinkedHashMap<>();
        files.put(prefix + ".java", entryPointSource());
        for (Group group : groups) {
            String type = typeName(group);
            files.put(type + ".java", interfaceSource(group));
            files.put(Naming.viewName(type) + ".java", viewSource(group));
            files.put(Naming.editorName(type) + ".java", editorSource(group));
            files.put(Naming.editName(type) + ".java", editSource(group));
        }
        return files;
    }

    /**
     * Returns the identifiers of the groups that have a term of the extension somewhere
     * below them. The relation is computed as a fixed point rather than by walking down
     * from each group, because a group of an extension may carry itself (specification,
     * section 5.6) and a walk down such a group does not end.
     */
    private Set<String> leadsToExtension() {
        List<Term> groups = new ArrayList<>();
        for (Term term : registry.terms()) {
            if (term.isGroup()) {
                groups.add(term);
            }
        }
        Set<String> marked = new LinkedHashSet<>();
        for (Term group : groups) {
            for (Term child : registry.children(group.id())) {
                if (extensionTerms.contains(child.id())) {
                    marked.add(group.id());
                    break;
                }
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Term group : groups) {
                if (marked.contains(group.id())) {
                    continue;
                }
                for (Term child : registry.children(group.id())) {
                    if (child.isGroup() && marked.contains(child.id())) {
                        marked.add(group.id());
                        changed = true;
                        break;
                    }
                }
            }
        }
        return marked;
    }

    private List<Group> groups() {
        List<Child> rootChildren = children(Naming.ROOT_TYPE, registry.rootTerms());
        if (rootChildren.isEmpty()) {
            throw new IllegalStateException(
                    "the extension places no term anywhere inside a document");
        }
        List<Group> groups = new ArrayList<>();
        groups.add(new Group(null, Naming.ROOT_TYPE, rootChildren));
        for (Term term : registry.terms()) {
            if (!term.isGroup() || !shown(term)) {
                continue;
            }
            String core = naming.typeName(term.id());
            groups.add(new Group(term, core, children(core, registry.children(term.id()))));
        }
        return groups;
    }

    /** Tells whether a group is one the overlay emits a type for. */
    private boolean shown(Term group) {
        return extensionTerms.contains(group.id()) || leadsToExtension.contains(group.id());
    }

    /**
     * Returns the children of a group the overlay shows, with the member name each one
     * gets. The name is the slug shortened by the name of the enclosing group where the
     * slug repeats it; where that would make two members of one group share a name, every
     * member of that group keeps its slug instead, so the choice never depends on the order
     * the registry lists them in.
     */
    private List<Child> children(String coreTypeName, List<Term> all) {
        List<Term> exposed = new ArrayList<>();
        for (Term child : all) {
            if (extensionTerms.contains(child.id())
                    || (child.isGroup() && leadsToExtension.contains(child.id()))) {
                exposed.add(child);
            }
        }
        List<String> names = new ArrayList<>();
        for (Term child : exposed) {
            names.add(Naming.overlayMemberName(coreTypeName, child.slug()));
        }
        if (new LinkedHashSet<>(names).size() != names.size()) {
            names.clear();
            for (Term child : exposed) {
                names.add(child.slug());
            }
        }
        if (new LinkedHashSet<>(names).size() != names.size()) {
            throw new IllegalStateException("the children of " + coreTypeName
                    + " do not yield unique overlay member names: " + names);
        }
        List<Child> children = new ArrayList<>();
        for (int i = 0; i < exposed.size(); i++) {
            Term child = exposed.get(i);
            children.add(new Child(child, names.get(i), singular(child, names, i)));
        }
        return children;
    }

    private static String singular(Term child, List<String> names, int index) {
        if (!child.isGroup() || !child.cardinality().isRepeatable()) {
            return null;
        }
        String member = names.get(index);
        if (member.length() < 2 || !member.endsWith("s")) {
            throw new IllegalStateException("the overlay member of the repeatable group "
                    + child.id() + " has no singular form: " + member);
        }
        String singular = member.substring(0, member.length() - 1);
        if (!Naming.isUsableMemberName(singular) || names.contains(singular)) {
            throw new IllegalStateException("the singular of the overlay member of " + child.id()
                    + " is not a member name it can have: " + singular);
        }
        return singular;
    }

    private String typeName(Group group) {
        return prefix + group.coreTypeName();
    }

    private String entryPointSource() {
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);
        imports.add(REGISTRY_TYPE);
        imports.add(TypedSources.DEFAULT_PACKAGE + "." + Naming.ROOT_TYPE);
        imports.add(TypedSources.DEFAULT_PACKAGE + "." + Naming.editorName(Naming.ROOT_TYPE));
        imports.add("java.util.Objects");

        String view = prefix + Naming.ROOT_TYPE;
        String editor = Naming.editorName(view);
        String accessor = Naming.lowerCamel(prefix) + "Extension";

        StringBuilder body = new StringBuilder("/**\n");
        body.append(JavaText.wrap("", " * ", JavaText.escape(
                "The entry point of the " + extension.edition() + " overlay: it turns a semantic"
                        + " document into a {@link " + view + "} for reading, and opens a {@link "
                        + editor + "} over a document builder for writing.")));
        body.append(" *\n");
        body.append(JavaText.wrap("", " * <p>", JavaText.escape(
                "The overlay shows the terms of that extension and nothing else. It reads and"
                        + " writes the very document the typed view of the core model reads and"
                        + " writes, at the paths the core model gives the groups the extension"
                        + " hangs its terms under, so the two are used side by side on one"
                        + " document and neither copies it.")));
        body.append(" *\n");
        body.append(JavaText.wrap("", " * <p>", JavaText.escape(
                "Neither the view nor the editor validates. {@link #registry()} hands over the"
                        + " registry a structural validator needs to check the paths of this"
                        + " extension, which without it are reported as not checked"
                        + " (specification, section 5.6).")));
        body.append(" */\n");
        body.append("public final class ").append(prefix).append(" {\n");
        body.append("\n");
        body.append(JavaText.call("    ",
                "private static final Registry REGISTRY = Registry.en16931().withExtension(",
                List.of("Registry." + accessor + "()"), ";"));
        body.append("\n");
        body.append("    private ").append(prefix).append("() {\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Returns the registry of the core model with this extension loaded."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * @return ",
                "the combined registry, which is what the structural validator has to be handed"
                        + " for the paths of this extension to be checked rather than reported as"
                        + " not checked"));
        body.append("     */\n");
        body.append("    public static Registry registry() {\n");
        body.append("        return REGISTRY;\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Returns an overlay over a document. Nothing is copied: every accessor reads the"
                        + " document when it is called."));
        body.append("     *\n");
        body.append("     * @param document the document to read\n");
        body.append("     * @return an overlay over the root of the document\n");
        body.append("     * @throws NullPointerException if {@code document} is {@code null}\n");
        body.append("     */\n");
        body.append("    public static ").append(view).append(" of(SemanticDocument document) {\n");
        body.append(JavaText.call("        ", "return new " + Naming.viewName(view) + "(",
                List.of("Objects.requireNonNull(document, \"document\")", "SemanticPath.root()"),
                ";"));
        body.append("    }\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Returns an overlay over the document a typed view reads, so that a caller"
                        + " holding the core view reaches the extension without naming the"
                        + " document again."));
        body.append("     *\n");
        body.append("     * @param invoice the typed view of the core model\n");
        body.append("     * @return an overlay over the document that view reads\n");
        body.append("     * @throws NullPointerException if {@code invoice} is {@code null}\n");
        body.append("     */\n");
        body.append("    public static ").append(view).append(" of(").append(Naming.ROOT_TYPE)
                .append(" invoice) {\n");
        body.append("        return of(Objects.requireNonNull(invoice, \"invoice\")")
                .append(".document());\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Opens an overlay editor over a document builder. Nothing is written until a"
                        + " setter is called, so an overlay that is only opened leaves the"
                        + " document as it was."));
        body.append("     *\n");
        body.append("     * @param builder the builder to write into\n");
        body.append("     * @return an editor over the root of that builder\n");
        body.append("     * @throws NullPointerException if {@code builder} is {@code null}\n");
        body.append("     */\n");
        body.append("    public static ").append(editor)
                .append(" edit(SemanticDocument.Builder builder) {\n");
        body.append(JavaText.call("        ", "return new " + Naming.editName(view) + "(",
                List.of("Objects.requireNonNull(builder, \"builder\")", "SemanticPath.root()"),
                ";"));
        body.append("    }\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Opens an overlay editor over the builder a typed editor writes into. Both"
                        + " editors then write into one builder, and one document comes out of"
                        + " it."));
        body.append("     *\n");
        body.append("     * @param editor the typed editor of the core model\n");
        body.append("     * @return an editor over the builder that editor writes into\n");
        body.append("     * @throws NullPointerException if {@code editor} is {@code null}\n");
        body.append("     */\n");
        body.append("    public static ").append(editor).append(" edit(")
                .append(Naming.editorName(Naming.ROOT_TYPE)).append(" editor) {\n");
        body.append("        return edit(Objects.requireNonNull(editor, \"editor\").builder());\n");
        body.append("    }\n");
        body.append("}\n");
        return file(imports, body.toString());
    }

    private String interfaceSource(Group group) {
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);
        String type = typeName(group);

        StringBuilder body = new StringBuilder(typeJavadoc(group, false));
        body.append("public interface ").append(type).append(" {\n");
        body.append("\n");
        body.append("    /**\n");
        body.append("     * Returns the document this overlay reads.\n");
        body.append("     *\n");
        body.append("     * @return the document the overlay was built over\n");
        body.append("     */\n");
        body.append("    SemanticDocument document();\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ", group.isRoot()
                ? "Returns the path of the root of the document, which has no segments."
                : "Returns the path of the group instance this overlay reads, which is the path"
                        + " the core model gives that instance."));
        body.append("     *\n");
        body.append("     * @return the path every value of this overlay lies under\n");
        body.append("     */\n");
        body.append("    SemanticPath path();\n");
        if (group.isRoot()) {
            imports.add(REGISTRY_TYPE);
            body.append(registryMethod());
        }
        for (Child child : group.children()) {
            if (!child.term().isGroup()) {
                imports.add(VALUE_TYPE_EXCEPTION);
                if (child.term().cardinality().isMandatory()) {
                    imports.add(MISSING_TYPE);
                }
            }
            body.append("\n");
            body.append(viewMemberJavadoc(group, child));
            body.append("    ").append(viewReturnType(child, imports)).append(" ")
                    .append(child.memberName()).append("();\n");
        }
        body.append("}\n");
        return file(imports, body.toString());
    }

    private String viewSource(Group group) {
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);
        imports.add("java.util.Objects");
        String type = typeName(group);
        String view = Naming.viewName(type);

        StringBuilder body = new StringBuilder();
        body.append("/** Reads {@link ").append(type).append("} from a semantic document. */\n");
        body.append("final class ").append(view).append(" implements ").append(type).append(" {\n");
        body.append("\n");
        body.append("    private final SemanticDocument document;\n");
        body.append("    private final SemanticPath path;\n");
        body.append("\n");
        body.append("    ").append(view)
                .append("(SemanticDocument document, SemanticPath path) {\n");
        body.append("        this.document = Objects.requireNonNull(document, \"document\");\n");
        body.append("        this.path = Objects.requireNonNull(path, \"path\");\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    @Override\n");
        body.append("    public SemanticDocument document() {\n");
        body.append("        return document;\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    @Override\n");
        body.append("    public SemanticPath path() {\n");
        body.append("        return path;\n");
        body.append("    }\n");
        for (Child child : group.children()) {
            body.append("\n");
            body.append("    @Override\n");
            body.append("    public ").append(viewReturnType(child, imports)).append(" ")
                    .append(child.memberName()).append("() {\n");
            body.append(accessorBody(child, imports));
            body.append("    }\n");
        }
        body.append("\n");
        body.append("    @Override\n");
        body.append("    public String toString() {\n");
        body.append("        return \"").append(type).append("[\" + path + \"]\";\n");
        body.append("    }\n");
        body.append("}\n");
        return file(imports, body.toString());
    }

    private String accessorBody(Child child, TreeSet<String> imports) {
        Term term = child.term();
        String id = "\"" + term.id() + "\"";
        Cardinality cardinality = term.cardinality();
        if (term.isGroup()) {
            String view = Naming.viewName(prefix + naming.typeName(term.id()));
            if (cardinality.isRepeatable()) {
                return JavaText.call("        ", "return " + runtime(imports, "Views") + ".groups(",
                        List.of("document", "path", id, view + "::new"), ";");
            }
            if (cardinality.isMandatory()) {
                return JavaText.call("        ", "return new " + view + "(",
                        List.of("document",
                                runtime(imports, "TermPaths") + ".group(path, " + id + ")"), ";");
            }
            return JavaText.call("        ",
                    "return " + runtime(imports, "Views") + ".optionalGroup(",
                    List.of("document", "path", id, view + "::new"), ";");
        }
        SemanticType type = datatype(term);
        runtime(imports, "Values");
        String views = runtime(imports, "Views");
        String paths = runtime(imports, "TermPaths");
        if (!cardinality.isMandatory()) {
            return JavaText.call("        ", "return " + views + ".optional(",
                    List.of("document", paths + ".value(path, " + id + ")",
                            Naming.valueReader(type)), ";");
        }
        return JavaText.call("        ", "return " + views + ".required(",
                List.of("document", paths + ".value(path, " + id + ")",
                        Naming.valueReader(type)), ";");
    }

    private String editorSource(Group group) {
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);
        String type = Naming.editorName(typeName(group));

        StringBuilder body = new StringBuilder(typeJavadoc(group, true));
        body.append("public interface ").append(type).append(" {\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Returns the builder this overlay writes into, which is the builder the typed"
                        + " editor of the core model writes into as well."));
        body.append("     *\n");
        body.append("     * @return the builder the overlay was opened over\n");
        body.append("     */\n");
        body.append("    SemanticDocument.Builder builder();\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ", group.isRoot()
                ? "Returns the path of the root of the document, which has no segments."
                : "Returns the path of the group instance this overlay writes into, which is the"
                        + " path the core model gives that instance."));
        body.append("     *\n");
        body.append("     * @return the path every value of this overlay lies under\n");
        body.append("     */\n");
        body.append("    SemanticPath path();\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Returns the document as it has been written so far."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * @return ",
                "an immutable document holding what was written into this overlay and into every"
                        + " other editor of the same builder, in canonical path order"));
        body.append("     */\n");
        body.append("    SemanticDocument document();\n");
        if (group.isRoot()) {
            imports.add(REGISTRY_TYPE);
            body.append(registryMethod());
        }
        for (Member member : members(group, imports, false)) {
            body.append("\n");
            body.append(member.javadoc());
            body.append("    ").append(member.signature()).append(";\n");
        }
        body.append("}\n");
        return file(imports, body.toString());
    }

    private String editSource(Group group) {
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);
        imports.add("java.util.Objects");
        String type = Naming.editorName(typeName(group));
        String edit = Naming.editName(typeName(group));
        List<Member> members = members(group, imports, true);

        StringBuilder body = new StringBuilder();
        body.append("/** Writes {@link ").append(type).append("} into a document builder. */\n");
        body.append("final class ").append(edit).append(" implements ").append(type).append(" {\n");
        body.append("\n");
        body.append("    private final SemanticDocument.Builder builder;\n");
        body.append("    private final SemanticPath path;\n");
        body.append("\n");
        body.append("    ").append(edit)
                .append("(SemanticDocument.Builder builder, SemanticPath path) {\n");
        body.append("        this.builder = Objects.requireNonNull(builder, \"builder\");\n");
        body.append("        this.path = Objects.requireNonNull(path, \"path\");\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    @Override\n");
        body.append("    public SemanticDocument.Builder builder() {\n");
        body.append("        return builder;\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    @Override\n");
        body.append("    public SemanticPath path() {\n");
        body.append("        return path;\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    @Override\n");
        body.append("    public SemanticDocument document() {\n");
        body.append("        return builder.build();\n");
        body.append("    }\n");
        for (Member member : members) {
            body.append("\n");
            body.append("    @Override\n");
            body.append("    public ").append(member.signature()).append(" {\n");
            body.append(member.body());
            body.append("    }\n");
        }
        body.append("\n");
        body.append("    @Override\n");
        body.append("    public String toString() {\n");
        body.append("        return \"").append(type).append("[\" + path + \"]\";\n");
        body.append("    }\n");
        body.append("}\n");
        return file(imports, body.toString());
    }

    private List<Member> members(Group group, TreeSet<String> imports, boolean implementation) {
        List<Member> members = new ArrayList<>();
        String editor = Naming.editorName(typeName(group));
        for (Child child : group.children()) {
            Term term = child.term();
            String id = "\"" + term.id() + "\"";
            if (term.isGroup()) {
                groupMember(members, group, child, editor, id, imports, implementation);
            } else {
                valueMember(members, group, child, editor, id, imports, implementation);
            }
        }
        return members;
    }

    private void groupMember(List<Member> members,
                             Group group,
                             Child child,
                             String editor,
                             String id,
                             TreeSet<String> imports,
                             boolean implementation) {
        Term term = child.term();
        String childEditor = Naming.editorName(prefix + naming.typeName(term.id()));
        String childEdit = Naming.editName(prefix + naming.typeName(term.id()));
        imports.add("java.util.function.Consumer");
        if (term.cardinality().isRepeatable()) {
            imports.add("java.util.List");
            members.add(new Member(
                    memberJavadoc(group, child, List.of(JavaText.wrap("    ", " * @return ",
                            "one editor per instance of " + term.id() + " " + where(group)
                                    + ", in the order of their occurrence indices, empty where the"
                                    + " document carries none")),
                            appendsNothing(term)),
                    "List<" + childEditor + ">", child.memberName(), "",
                    JavaText.call("        ", "return Overlays.editors(",
                            List.of("builder", "path", id, childEdit + "::new"), ";")));
            members.add(new Member(
                    memberJavadoc(group, child, List.of(
                            JavaText.wrap("    ", " * @param index ",
                                    "the zero-based occurrence index of the instance of "
                                            + term.id() + " to write into"),
                            JavaText.wrap("    ", " * @param block ",
                                    "what to write into that instance"),
                            JavaText.wrap("    ", " * @return ", "this editor"),
                            JavaText.wrap("    ", " * @throws IndexOutOfBoundsException ",
                                    "if the document carries no instance of " + term.id()
                                            + " at that index"),
                            JavaText.wrap("    ", " * @throws NullPointerException ",
                                    "if {@code block} is {@code null}")),
                            appendsNothing(term)),
                    editor, child.singularMemberName(),
                    "int index, Consumer<" + childEditor + "> block",
                    "        Objects.requireNonNull(block, \"block\").accept(Overlays.at("
                            + child.memberName() + "(), index, " + id + "));\n"
                            + "        return this;\n"));
            return;
        }
        members.add(new Member(
                memberJavadoc(group, child, List.of(JavaText.wrap("    ", " * @return ",
                        "the editor of the instance of " + term.id() + " " + where(group)
                                + "; the group comes into being when a value is written into it,"
                                + " so an editor that is not written into leaves nothing"
                                + " behind"))),
                childEditor, child.memberName(), "",
                JavaText.call("        ", "return new " + childEdit + "(",
                        List.of("builder",
                                paths(imports, implementation) + ".group(path, " + id + ")"), ";")));
        members.add(new Member(
                memberJavadoc(group, child, List.of(
                        JavaText.wrap("    ", " * @param block ",
                                "what to write into the instance of " + term.id()),
                        JavaText.wrap("    ", " * @return ", "this editor"),
                        JavaText.wrap("    ", " * @throws NullPointerException ",
                                "if {@code block} is {@code null}"))),
                editor, child.memberName(), "Consumer<" + childEditor + "> block",
                "        Objects.requireNonNull(block, \"block\").accept("
                        + child.memberName() + "());\n"
                        + "        return this;\n"));
    }

    private void valueMember(List<Member> members,
                             Group group,
                             Child child,
                             String editor,
                             String id,
                             TreeSet<String> imports,
                             boolean implementation) {
        SemanticType type = datatype(child.term());
        String setterType = Naming.setterType(type);
        boolean string = "String".equals(setterType);
        Naming.valueImport(type).ifPresent(imports::add);
        String editors = implementation ? runtime(imports, "Editors") : "Editors";
        String writers = implementation ? runtime(imports, "Writers") : "Writers";
        members.add(new Member(
                memberJavadoc(group, child, List.of(
                        JavaText.wrap("    ", " * @param value ",
                                "the value to write, or {@code null}"
                                        + (string ? " or the empty string" : "")
                                        + " to remove the value"),
                        JavaText.wrap("    ", " * @return ", "this editor"))),
                editor, child.memberName(), setterType + " value",
                JavaText.call("        ", editors + ".set(",
                        List.of("builder",
                                paths(imports, implementation) + ".value(path, " + id + ")",
                                "value", writers + "." + type.name()), ";")
                        + "        return this;\n"));
    }

    /**
     * Returns the name of one of the types the emitted accessors and setters call, the
     * runtime of the typed view of the core model, and adds its import.
     *
     * @param imports    the imports of the file being emitted
     * @param simpleName the name of the runtime type
     * @return the name as the file writes it
     */
    private static String runtime(TreeSet<String> imports, String simpleName) {
        imports.add(RUNTIME_PACKAGE + "." + simpleName);
        return simpleName;
    }

    /**
     * Returns the name of the path arithmetic, and imports it where the file being emitted
     * is an implementation. An interface carries the signatures alone, so an import for
     * something only a body names would be an unused one.
     *
     * @param imports        the imports of the file being emitted
     * @param implementation whether the file being emitted carries the bodies
     * @return the name as the file writes it
     */
    private static String paths(TreeSet<String> imports, boolean implementation) {
        return implementation ? runtime(imports, "TermPaths") : "TermPaths";
    }

    private String registryMethod() {
        StringBuilder body = new StringBuilder("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Returns the registry of the core model with this extension loaded, so that a"
                        + " caller holding an overlay has what a structural validator needs to"
                        + " check the paths of the extension."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * @return ",
                "the combined registry, the one {@link " + prefix + "#registry()} hands over"));
        body.append("     */\n");
        body.append("    default Registry registry() {\n");
        body.append("        return ").append(prefix).append(".registry();\n");
        body.append("    }\n");
        return body.toString();
    }

    private String typeJavadoc(Group group, boolean editor) {
        String verb = editor ? "Writes" : "Reads";
        StringBuilder doc = new StringBuilder("/**\n");
        if (group.isRoot()) {
            doc.append(JavaText.wrap("", " * ", JavaText.escape(verb + " the terms of "
                    + extension.edition() + " that lie at the root of a document.")));
        } else {
            Term term = group.term();
            doc.append(JavaText.wrap("", " * ", JavaText.escape(verb + " the terms of "
                    + extension.edition() + " that lie inside " + term.id() + " " + term.name()
                    + ".")));
        }
        doc.append(" *\n");
        doc.append(JavaText.wrap("", " * <p>", JavaText.escape(editor
                ? "An overlay over the same document builder the typed editor of the core model"
                        + " writes into, carrying the terms of the extension and nothing else."
                        + " Setting null or an empty string removes the value, exactly as in the"
                        + " core editor. A group comes into being when a value is written into it"
                        + " (specification, section 4.5); this overlay appends no instance of a"
                        + " core group and removes none."
                : "An overlay over the same document the typed view of the core model reads,"
                        + " carrying the terms of the extension and nothing else. Nothing is"
                        + " copied and nothing is resolved before it is asked for.")));
        doc.append(" */\n");
        return doc.toString();
    }

    private String viewMemberJavadoc(Group group, Child child) {
        Term term = child.term();
        Cardinality cardinality = term.cardinality();
        List<String> tags = new ArrayList<>();
        if (term.isGroup()) {
            if (cardinality.isRepeatable()) {
                tags.add(JavaText.wrap("    ", " * @return ",
                        "one overlay per instance of " + term.id() + " " + where(group)
                                + ", in the order of their occurrence indices and aligned with"
                                + " the instances the typed view of the core model shows, empty"
                                + " where the document has none"));
            } else if (cardinality.isMandatory()) {
                tags.add(JavaText.wrap("    ", " * @return ",
                        "the overlay over the instance of " + term.id() + " " + where(group)
                                + "; the model declares it mandatory, so the overlay is returned"
                                + " even where the document carries no value under it"));
            } else {
                tags.add(JavaText.wrap("    ", " * @return ",
                        "the overlay over the instance of " + term.id() + " " + where(group)
                                + ", or an empty optional where the document carries no value"
                                + " under it"));
            }
            return memberJavadoc(group, child, tags);
        }
        if (cardinality.isMandatory()) {
            tags.add(JavaText.wrap("    ", " * @return ",
                    "the value of " + term.id() + " " + where(group)));
            tags.add(JavaText.wrap("    ", " * @throws MissingValueException ",
                    "if the document carries no value there; the extension declares the term"
                            + " mandatory, which validation layer L3 checks and this overlay does"
                            + " not"));
        } else {
            tags.add(JavaText.wrap("    ", " * @return ",
                    "the value of " + term.id() + " " + where(group) + ", or an empty optional"
                            + " where the document carries none"));
        }
        tags.add(JavaText.wrap("    ", " * @throws ValueTypeException ",
                "if the value there is no value of that semantic data type"));
        return memberJavadoc(group, child, tags);
    }

    private String memberJavadoc(Group group, Child child, List<String> tags) {
        return memberJavadoc(group, child, tags, "");
    }

    private String memberJavadoc(Group group, Child child, List<String> tags, String note) {
        Term term = child.term();
        StringBuilder doc = new StringBuilder("    /**\n");
        if (extensionTerms.contains(term.id())) {
            doc.append(JavaText.wrap("    ", " * ", JavaText.escape(
                    term.id() + " " + term.name() + ". " + term.description())));
            doc.append("     *\n");
            doc.append(JavaText.wrap("    ", " * <p>", JavaText.escape(
                    "Declared cardinality " + term.cardinality() + TypedSources.termFacts(term)
                            + ". A term of " + extension.edition()
                            + ", which is no part of EN 16931-1.")));
        } else {
            doc.append(JavaText.wrap("    ", " * ", JavaText.escape(
                    "The way down to the terms of " + extension.edition() + " that lie inside "
                            + term.id() + " " + term.name() + ", a business group of the core"
                            + " model this overlay shows nothing else of.")));
            doc.append("     *\n");
            doc.append(JavaText.wrap("    ", " * <p>", JavaText.escape(
                    "Declared cardinality " + term.cardinality() + ". The core terms of the group"
                            + " are read and written through the typed view of the core model, at"
                            + " the same path.")));
        }
        if (!note.isEmpty()) {
            doc.append("     *\n");
            doc.append(JavaText.wrap("    ", " * <p>", JavaText.escape(note)));
        }
        doc.append("     *\n");
        for (String tag : tags) {
            doc.append(tag);
        }
        doc.append("     */\n");
        return doc.toString();
    }

    private static String appendsNothing(Term term) {
        return "The overlay writes into the instances of " + term.id() + " the document already"
                + " carries and appends none: how many there are is decided by the core model,"
                + " and the typed editor of the core model is what appends one.";
    }

    private String viewReturnType(Child child, TreeSet<String> imports) {
        Term term = child.term();
        String type;
        if (term.isGroup()) {
            type = prefix + naming.typeName(term.id());
        } else {
            SemanticType datatype = datatype(term);
            Naming.valueImport(datatype).ifPresent(imports::add);
            type = Naming.valueType(datatype);
        }
        Cardinality cardinality = term.cardinality();
        if (cardinality.isRepeatable()) {
            imports.add("java.util.List");
            return "List<" + type + ">";
        }
        if (cardinality.isMandatory()) {
            return type;
        }
        imports.add("java.util.Optional");
        return "Optional<" + type + ">";
    }

    /**
     * Returns the semantic data type of a business term, and refuses the shapes the overlay
     * runtime has no way to read or write: a repeatable term, and the two semantic data
     * types whose values carry supplementary components.
     */
    private static SemanticType datatype(Term term) {
        if (term.cardinality().isRepeatable()) {
            throw new IllegalStateException(
                    "the overlay has no way to carry the repeatable business term " + term.id());
        }
        SemanticType type = term.datatype().orElseThrow(() -> new IllegalStateException(
                "the registry gives " + term.id() + " no semantic data type"));
        if (type == SemanticType.IDENTIFIER || type == SemanticType.BINARY_OBJECT) {
            throw new IllegalStateException("the overlay has no way to carry " + term.id()
                    + ", whose semantic data type " + type.registryDatatype()
                    + " has supplementary components");
        }
        return type;
    }

    private static String where(Group group) {
        return group.isRoot() ? "at the root of the document" : "in this group instance";
    }

    private String file(TreeSet<String> imports, String body) {
        StringBuilder out = new StringBuilder(header);
        out.append("\n");
        out.append("package ").append(packageName).append(";\n");
        out.append("\n");
        for (String type : imports) {
            out.append("import ").append(type).append(";\n");
        }
        out.append("\n");
        out.append(body);
        return out.toString();
    }

    /** One group of the overlay: a business group, or the root of the document. */
    private record Group(Term term, String coreTypeName, List<Child> children) {

        boolean isRoot() {
            return term == null;
        }
    }

    /**
     * One child of a group the overlay shows.
     *
     * @param term               the business term or business group
     * @param memberName         the name the overlay gives its accessor
     * @param singularMemberName the name of the member that writes into one instance of a
     *                           repeatable group, {@code null} for everything else
     */
    private record Child(Term term, String memberName, String singularMemberName) {
    }

    /**
     * One member of an overlay editor: what it looks like in the interface, and what it does
     * in the implementation.
     *
     * @param javadoc    the comment, ending in a line break
     * @param returnType the type the member returns
     * @param name       the member name
     * @param parameters the parameter list, without its parentheses
     * @param body       the statements of the implementation, each ending in a line break
     */
    private record Member(String javadoc,
                          String returnType,
                          String name,
                          String parameters,
                          String body) {

        String signature() {
            return returnType + " " + name + "(" + parameters + ")";
        }
    }
}
