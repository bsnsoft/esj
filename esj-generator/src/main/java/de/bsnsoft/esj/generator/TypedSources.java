package de.bsnsoft.esj.generator;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Cardinality;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Emits the sources of the typed view: per business group, and once more for the root of
 * the document, a view interface with a package-private implementation and an editor
 * interface with a package-private implementation.
 *
 * <p>The view implementations hold the document and the path of their group instance and
 * nothing else; every accessor reads the document when it is called, so nothing is copied
 * and nothing is resolved before it is asked for. The editor implementations hold a
 * document builder and the same path, and every setter writes into that one builder.
 *
 * <p>A group that carries itself — an extension group that lists its own identifier in
 * {@code reusesTerms} — is emitted as a type that refers to itself, never unrolled: the
 * emitter walks the flat list of terms once and each group yields exactly one view type
 * and one editor type, whatever the depth a document may reach (specification,
 * section 5.6).
 *
 * <p>One view belongs to one edition of the semantic model, so the package it is emitted
 * into is an argument: a path is an address relative to an edition, and a union of two
 * editions would offer accessors for terms the document's own edition does not have. The
 * types the views of every edition share — the value records, the handles, the two
 * exceptions and the runtime the accessors call — are not emitted; they lie in
 * {@code de.bsnsoft.esj.typed} and its {@code runtime} package and are
 * imported where the view is emitted somewhere else.
 *
 * <p>The emitted text depends on the registry and on that package alone. It carries no
 * timestamp, no host name and no path, and the order of everything in it is the order of
 * the registry, so two runs over the same registry produce the same bytes.
 */
final class TypedSources {

    /**
     * The package that holds the types every edition's view shares, and the one the
     * overlay emitter derives its own from.
     */
    static final String BASE_PACKAGE = "de.bsnsoft.esj.typed";

    /** The package the view of the edition the repository defaults to is emitted into. */
    static final String DEFAULT_PACKAGE = BASE_PACKAGE;

    private static final String RUNTIME_PACKAGE = BASE_PACKAGE + ".runtime";

    /** The type that opens a view and an editor over a document of one edition. */
    private static final String ENTRY_POINT = "En16931";

    private static final String DOCUMENT_TYPE = "de.bsnsoft.esj.SemanticDocument";
    private static final String PATH_TYPE = "de.bsnsoft.esj.SemanticPath";

    private final Registry registry;
    private final Naming naming;
    private final String header;
    private final String typedPackage;

    /**
     * Prepares the emitter for a registry.
     *
     * @param registry     the registry the typed view describes
     * @param header       the three-line header every generated file carries
     * @param typedPackage the package the view is emitted into, which is
     *                     {@link #DEFAULT_PACKAGE} for the edition the repository
     *                     defaults to and a package of its own for every other one
     */
    TypedSources(Registry registry, String header, String typedPackage) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.naming = new Naming(registry);
        this.header = Objects.requireNonNull(header, "header");
        this.typedPackage = Objects.requireNonNull(typedPackage, "typedPackage");
    }

    /**
     * Returns the import a generated file needs to name one of the types the views of
     * every edition share, and adds it where the view does not lie beside them.
     *
     * @param imports    the imports of the file being emitted
     * @param simpleName the name of the shared type
     * @return the name as the file writes it
     */
    private String shared(TreeSet<String> imports, String simpleName) {
        if (!typedPackage.equals(BASE_PACKAGE)) {
            imports.add(BASE_PACKAGE + "." + simpleName);
        }
        return simpleName;
    }

    /**
     * Returns the name of one of the types the generated accessors and setters call, and
     * adds its import. They lie in a package of their own, so every view imports them.
     *
     * @param imports    the imports of the file being emitted
     * @param simpleName the name of the runtime type
     * @return the name as the file writes it
     */
    private String runtime(TreeSet<String> imports, String simpleName) {
        imports.add(RUNTIME_PACKAGE + "." + simpleName);
        return simpleName;
    }

    /**
     * Emits every source file of the typed view.
     *
     * @return the file names and their content, in the order of the registry
     */
    Map<String, String> sources() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(ENTRY_POINT + ".java", entryPointSource());
        List<Group> groups = groups();
        for (Group group : groups) {
            files.put(group.typeName() + ".java", interfaceSource(group));
            files.put(Naming.viewName(group.typeName()) + ".java", viewSource(group));
            files.put(Naming.editorName(group.typeName()) + ".java", editorSource(group));
            files.put(Naming.editName(group.typeName()) + ".java", editSource(group));
        }
        return files;
    }

    /**
     * Emits the entry point of one edition's view: the two lines a caller writes before
     * anything else, and the only place in the package where a view or an editor is
     * built.
     *
     * <p>It is emitted rather than hand-written because it names the root type of the
     * view, which is generated, and because every edition needs one of its own. What it
     * says about the edition comes from the registry.
     *
     * @return the source of the entry point, javadoc included
     */
    private String entryPointSource() {
        StringBuilder body = new StringBuilder();
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);
        imports.add("java.util.Objects");
        String root = Naming.ROOT_TYPE;
        String view = Naming.viewName(root);
        String editor = Naming.editorName(root);
        String edit = Naming.editName(root);
        String missing = shared(imports, "MissingValueException");
        String wrongType = shared(imports, "ValueTypeException");

        body.append("/**\n");
        body.append(JavaText.wrap("", " * ", "The entry point of the typed view of "
                + JavaText.escape(registry.edition()) + ": it turns a semantic document of"
                + " that edition into an {@link " + root + "} for reading, and opens an"
                + " {@link " + editor + "} for writing."));
        body.append(" *\n");
        body.append(JavaText.wrap("", " * <p>", "A view of one edition reads a document of"
                + " that edition. A path is an address relative to an edition"
                + " (specification, section 10), so the same path may be two different"
                + " terms under two editions, and a document whose {@code semanticModel}"
                + " names another one is read through the entry point of that edition's"
                + " package instead. Nothing here looks at {@code semanticModel}: this"
                + " view is what a caller reaches for once they know which edition they"
                + " are holding."));
        body.append(" *\n");
        body.append(JavaText.wrap("", " * <p>", "The view reads the document; it neither"
                + " copies nor validates it. A document that fails validation layer L2 or"
                + " L3 can still be viewed, and the view reports what it finds: a"
                + " mandatory term that is absent throws {@link " + missing + "} when it"
                + " is asked for, and a content that is no value of the semantic data type"
                + " the registry records for its term throws {@link " + wrongType + "}."
                + " Use {@code de.bsnsoft.esj.validate.StructuralValidator} to"
                + " learn about those defects before reading."));
        body.append(" */\n");
        body.append("public final class ").append(ENTRY_POINT).append(" {\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ", "The {@code semanticModel} a document of"
                + " this edition carries (specification, section 10): the edition of the"
                + " registry this view was generated from, with the spaces taken out."));
        body.append("     */\n");
        body.append("    public static final String SEMANTIC_MODEL = \"")
                .append(registry.edition().replace(" ", "")).append("\";\n");
        body.append("\n");
        body.append("    private ").append(ENTRY_POINT).append("() {\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ", "Returns a typed view over a document."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * <p>", "A view is a reader, and a reader is"
                + " blind to the edition by construction: it hands out what the document"
                + " carries at the paths this edition gives its terms, and it looks at"
                + " {@code semanticModel} no more than a canonicalizer does. Handing it a"
                + " document of another edition is therefore not refused here and not"
                + " sensible either — the terms that moved between the two editions are"
                + " read as absent — so the edition is the caller's to know, and"
                + " {@link #SEMANTIC_MODEL} is what they compare against."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * @param document ", "the document to read"));
        body.append(JavaText.wrap("    ", " * @return ", "a view over the root of the document"));
        body.append(JavaText.wrap("    ", " * @throws NullPointerException ",
                "if {@code document} is {@code null}"));
        body.append("     */\n");
        body.append("    public static ").append(root).append(" view(SemanticDocument document) {\n");
        body.append("        return new ").append(view)
                .append("(Objects.requireNonNull(document, \"document\"), SemanticPath.root());\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ", "Opens an editor over an empty invoice of "
                + JavaText.escape(registry.edition()) + ". The document it builds names that"
                + " edition, because the paths this editor writes are that edition's paths."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * <p>", "Nothing exists until something is"
                + " written: a business group comes into being the moment a value is"
                + " written into it (specification, section 4.5), so an editor that is"
                + " only looked at produces an empty document. {@link " + editor
                + "#document()} returns what has been written so far, in canonical path"
                + " order."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * @return ", "an editor over a new document"));
        body.append("     */\n");
        body.append("    public static ").append(editor).append(" newInvoice() {\n");
        body.append("        return new ").append(edit)
                .append("(SemanticDocument.builder().semanticModel(SEMANTIC_MODEL),\n");
        body.append("                SemanticPath.root());\n");
        body.append("    }\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ", "Opens an editor over a copy of an"
                + " existing document. The document itself is immutable and is not"
                + " touched; the editor writes into a builder seeded with its content, and"
                + " {@link " + editor + "#document()} returns the changed copy."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * <p>", "An editor is a writer, and a writer"
                + " knows its edition: a document naming another one is refused rather"
                + " than written into, because every setter here writes a path of this"
                + " edition, and a document whose paths and whose {@code semanticModel}"
                + " disagree is one no validator can make sense of. Use"
                + " {@code esj upgrade} or the entry point of the document's own edition."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * @param document ", "the document to change"));
        body.append(JavaText.wrap("    ", " * @return ", "an editor over a copy of it"));
        body.append(JavaText.wrap("    ", " * @throws IllegalArgumentException ",
                "if the document names another edition than {@link #SEMANTIC_MODEL}"));
        body.append(JavaText.wrap("    ", " * @throws NullPointerException ",
                "if {@code document} is {@code null}"));
        body.append("     */\n");
        body.append("    public static ").append(editor).append(" edit(SemanticDocument document) {\n");
        body.append("        Objects.requireNonNull(document, \"document\");\n");
        body.append("        if (!SEMANTIC_MODEL.equals(document.semanticModel())) {\n");
        body.append("            throw new IllegalArgumentException(\"this editor writes \"\n");
        body.append("                    + SEMANTIC_MODEL + \" and the document names \"\n");
        body.append("                    + document.semanticModel());\n");
        body.append("        }\n");
        body.append("        return new ").append(edit)
                .append("(document.toBuilder(), SemanticPath.root());\n");
        body.append("    }\n");
        body.append("}\n");
        return file(imports, body.toString());
    }

    private List<Group> groups() {
        List<Group> groups = new ArrayList<>();
        groups.add(new Group(null, Naming.ROOT_TYPE, registry.rootTerms()));
        for (Term term : registry.terms()) {
            if (term.isGroup()) {
                groups.add(new Group(term, naming.typeName(term.id()), registry.children(term.id())));
            }
        }
        return groups;
    }

    private String interfaceSource(Group group) {
        StringBuilder body = new StringBuilder();
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);

        body.append(typeJavadoc(group));
        body.append("public interface ").append(group.typeName()).append(" {\n");
        body.append("\n");
        body.append("    /**\n");
        body.append("     * Returns the document this view reads.\n");
        body.append("     *\n");
        body.append("     * @return the document the view was built over\n");
        body.append("     */\n");
        body.append("    SemanticDocument document();\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ", group.isRoot()
                ? "Returns the path of the root of the document, which has no segments."
                : "Returns the path of the group instance this view reads."));
        body.append("     *\n");
        body.append("     * @return the path every value of this view lies under\n");
        body.append("     */\n");
        body.append("    SemanticPath path();\n");

        for (Term child : group.children()) {
            if (!child.isGroup()) {
                shared(imports, "ValueTypeException");
                if (child.cardinality().isMandatory() && !child.cardinality().isRepeatable()) {
                    shared(imports, "MissingValueException");
                }
            }
            body.append("\n");
            body.append(memberJavadoc(child, group.isRoot()));
            body.append("    ").append(returnType(child, imports)).append(" ")
                    .append(naming.memberName(child)).append("();\n");
        }
        body.append("}\n");
        return file(imports, body.toString());
    }

    private String viewSource(Group group) {
        StringBuilder body = new StringBuilder();
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);
        imports.add("java.util.Objects");

        String type = group.typeName();
        String view = Naming.viewName(type);

        body.append("/** Reads {@link ").append(type).append("} from a semantic document. */\n");
        body.append("final class ").append(view).append(" implements ").append(type).append(" {\n");
        body.append("\n");
        body.append("    private final SemanticDocument document;\n");
        body.append("    private final SemanticPath path;\n");
        body.append("\n");
        body.append("    ").append(view).append("(SemanticDocument document, SemanticPath path) {\n");
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

        for (Term child : group.children()) {
            body.append("\n");
            body.append("    @Override\n");
            body.append("    public ").append(returnType(child, imports)).append(" ")
                    .append(naming.memberName(child)).append("() {\n");
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

    private String accessorBody(Term child, TreeSet<String> imports) {
        String id = "\"" + child.id() + "\"";
        String views = runtime(imports, "Views");
        String paths = runtime(imports, "TermPaths");
        Cardinality cardinality = child.cardinality();
        if (child.isGroup()) {
            String view = Naming.viewName(naming.typeName(child.id()));
            if (cardinality.isRepeatable()) {
                return JavaText.call("        ", "return " + views + ".groups(",
                        List.of("document", "path", id, view + "::new"), ";");
            }
            if (cardinality.isMandatory()) {
                return JavaText.call("        ", "return new " + view + "(",
                        List.of("document", paths + ".group(path, " + id + ")"), ";");
            }
            return JavaText.call("        ", "return " + views + ".optionalGroup(",
                    List.of("document", "path", id, view + "::new"), ";");
        }
        runtime(imports, "Values");
        String reader = Naming.valueReader(datatype(child));
        if (cardinality.isRepeatable()) {
            return JavaText.call("        ", "return " + views + ".repeated(",
                    List.of("document", "path", id, reader), ";");
        }
        String method = cardinality.isMandatory() ? "required" : "optional";
        return JavaText.call("        ", "return " + views + "." + method + "(",
                List.of("document", paths + ".value(path, " + id + ")", reader), ";");
    }

    private String returnType(Term child, TreeSet<String> imports) {
        String type;
        if (child.isGroup()) {
            type = naming.typeName(child.id());
        } else {
            SemanticType datatype = datatype(child);
            Naming.valueImport(datatype).ifPresent(imports::add);
            type = Naming.valueType(datatype);
            if (datatype == SemanticType.IDENTIFIER || datatype == SemanticType.BINARY_OBJECT) {
                shared(imports, type);
            }
        }
        Cardinality cardinality = child.cardinality();
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

    static SemanticType datatype(Term term) {
        return term.datatype().orElseThrow(() ->
                new IllegalStateException("the registry gives " + term.id() + " no semantic data type"));
    }

    private String typeJavadoc(Group group) {
        StringBuilder doc = new StringBuilder("/**\n");
        if (group.isRoot()) {
            doc.append(JavaText.wrap("", " * ",
                    "The invoice as a whole: the business terms and business groups the semantic model"
                            + " places at the root of a document."));
        } else {
            Term term = group.term();
            doc.append(JavaText.wrap("", " * ",
                    JavaText.escape(term.id() + " " + term.name() + ". " + term.description())));
        }
        doc.append(" *\n");
        doc.append(JavaText.wrap("", " * <p>", group.isRoot()
                ? "A view over a semantic document. Nothing is copied and nothing is resolved before it"
                        + " is asked for: every accessor reads the document the view was built over."
                : "A view over one instance of this group inside a semantic document. Nothing is copied"
                        + " and nothing is resolved before it is asked for: every accessor reads the"
                        + " document the view was built over."));
        doc.append(" */\n");
        return doc.toString();
    }

    private String editorJavadoc(Group group, String type) {
        StringBuilder doc = new StringBuilder("/**\n");
        if (group.isRoot()) {
            doc.append(JavaText.wrap("", " * ",
                    "Writes the invoice as a whole: the business terms and business groups the"
                            + " semantic model places at the root of a document."));
        } else {
            Term term = group.term();
            doc.append(JavaText.wrap("", " * ",
                    JavaText.escape(term.id() + " " + term.name() + ". " + term.description())));
        }
        doc.append(" *\n");
        doc.append(JavaText.wrap("", " * <p>", group.isRoot()
                ? "An editor over a document builder. Every setter writes into that one builder and"
                        + " returns this editor, so an invoice is written as one chain of names"
                        + " rather than as a list of paths."
                : "An editor over one instance of this group inside a document builder. The group"
                        + " comes into being when a value is written into it (specification,"
                        + " section 4.5), so an editor that is not written into leaves nothing"
                        + " behind."));
        doc.append(" *\n");
        doc.append(JavaText.wrap("", " * <p>", "Setting {@code null}, an empty string or an empty"
                + " file removes the value. Whether the values a document ends up with are"
                + " complete and consistent is the question of the validation layers, not of"
                + " this editor: {@code " + type + "} checks a value against the grammar of its"
                + " semantic data type and nothing else."));
        doc.append(" */\n");
        return doc.toString();
    }

    private String memberJavadoc(Term term, boolean root) {
        StringBuilder doc = new StringBuilder("    /**\n");
        doc.append(JavaText.wrap("    ", " * ",
                JavaText.escape(term.id() + " " + term.name() + ". " + term.description())));
        doc.append("     *\n");
        String facts = "Declared cardinality " + term.cardinality() + termFacts(term) + ".";
        doc.append(JavaText.wrap("    ", " * <p>", JavaText.escape(facts)));
        doc.append("     *\n");
        String where = root ? "at the root of the document" : "in this group instance";
        Cardinality cardinality = term.cardinality();
        if (term.isGroup()) {
            if (cardinality.isRepeatable()) {
                doc.append(JavaText.wrap("    ", " * @return ",
                        "one view per instance of " + term.id() + " " + where + ", in the order of"
                                + " their occurrence indices, empty where the document has none"));
            } else if (cardinality.isMandatory()) {
                doc.append(JavaText.wrap("    ", " * @return ",
                        "the view over the instance of " + term.id() + " " + where + "; the model"
                                + " declares it mandatory, so the view is returned even where the"
                                + " document carries no value under it"));
            } else {
                doc.append(JavaText.wrap("    ", " * @return ",
                        "the view over the instance of " + term.id() + " " + where + ", or an empty"
                                + " optional where the document carries no value under it"));
            }
            doc.append("     */\n");
            return doc.toString();
        }
        if (cardinality.isRepeatable()) {
            doc.append(JavaText.wrap("    ", " * @return ",
                    "the values of " + term.id() + " " + where + ", in the order of their occurrence"
                            + " indices, empty where the document carries none"));
            doc.append(JavaText.wrap("    ", " * @throws ValueTypeException ",
                    "if a value there is no value of that semantic data type"));
        } else if (cardinality.isMandatory()) {
            doc.append(JavaText.wrap("    ", " * @return ",
                    "the value of " + term.id() + " " + where));
            doc.append(JavaText.wrap("    ", " * @throws MissingValueException ",
                    "if the document carries no value there; the model declares the term mandatory,"
                            + " which validation layer L3 checks and this view does not"));
            doc.append(JavaText.wrap("    ", " * @throws ValueTypeException ",
                    "if the value there is no value of that semantic data type"));
        } else {
            doc.append(JavaText.wrap("    ", " * @return ",
                    "the value of " + term.id() + " " + where + ", or an empty optional where the"
                            + " document carries none"));
            doc.append(JavaText.wrap("    ", " * @throws ValueTypeException ",
                    "if the value there is no value of that semantic data type"));
        }
        doc.append("     */\n");
        return doc.toString();
    }

    private String editorSource(Group group) {
        StringBuilder body = new StringBuilder();
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);

        String type = Naming.editorName(group.typeName());
        body.append(editorJavadoc(group, type));
        body.append("public interface ").append(type).append(" {\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Returns the builder every editor of this document writes into. It is the way to"
                        + " an extension term, which the typed editor of the core model has no"
                        + " setter for."));
        body.append("     *\n");
        body.append("     * @return the builder the editor was opened over\n");
        body.append("     */\n");
        body.append("    SemanticDocument.Builder builder();\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ", group.isRoot()
                ? "Returns the path of the root of the document, which has no segments."
                : "Returns the path of the group instance this editor writes into."));
        body.append("     *\n");
        body.append("     * @return the path every value of this editor lies under\n");
        body.append("     */\n");
        body.append("    SemanticPath path();\n");
        body.append("\n");
        body.append("    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Returns the document as it has been written so far."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * @return ",
                "an immutable document holding what was written into this editor and every other"
                        + " editor of the same document, in canonical path order"));
        body.append("     */\n");
        body.append("    SemanticDocument document();\n");
        if (group.isRoot() && typedPackage.equals(BASE_PACKAGE)) {
            imports.add("java.util.Objects");
            body.append(derivation());
        }

        for (Member member : members(group, imports)) {
            body.append("\n");
            body.append(member.javadoc());
            body.append("    ").append(member.signature()).append(";\n");
        }
        body.append("}\n");
        return file(imports, body.toString());
    }

    /**
     * Emits the one method of the root editor that is not a setter: the hand-written
     * derivation policies of the package, reached from the editor a caller already holds.
     *
     * <p>It is emitted rather than hand-written because the interface it belongs on is
     * emitted. The body is a single call; everything the derivation does is in
     * {@code Totals}, which no generator produced.
     *
     * <p>It is emitted for the view of one edition only, the one {@code Totals} is
     * written against. The arithmetic of the standard is stated per edition — which terms
     * enter a sum, and how many fraction digits an amount may carry — so a derivation for
     * another edition is another policy, and a view of another edition is generated
     * without the hook rather than with one that would compute the wrong invoice.
     *
     * @return the source of the method, javadoc included
     */
    private static String derivation() {
        StringBuilder body = new StringBuilder("\n    /**\n");
        body.append(JavaText.wrap("    ", " * ",
                "Derives the amounts this invoice adds up to and writes them into it: the invoice"
                        + " line net amounts, the VAT breakdown and the document totals, computed"
                        + " from the prices, quantities, allowances and charges the invoice"
                        + " carries."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * <p>", "The derivation is a policy of this SDK and"
                + " not part of the format: it happens here, where a caller asks for it, and"
                + " never inside {@code document()}. What it computes, where it rounds and"
                + " where it refuses is written down in {@link Totals}."));
        body.append("     *\n");
        body.append(JavaText.wrap("    ", " * @param policy ",
                "the derivation policy, for instance {@code Totals.STANDARD}"));
        body.append(JavaText.wrap("    ", " * @return ",
                "what the policy wrote and where it rounded"));
        body.append(JavaText.wrap("    ", " * @throws DerivationException ",
                "if the invoice does not state what the policy needs, or contradicts it"));
        body.append(JavaText.wrap("    ", " * @throws NullPointerException ",
                "if {@code policy} is {@code null}"));
        body.append("     */\n");
        body.append("    default DerivationReport derive(Totals policy) {\n");
        body.append("        return Objects.requireNonNull(policy, \"policy\").apply(builder());\n");
        body.append("    }\n");
        return body.toString();
    }

    private String editSource(Group group) {
        StringBuilder body = new StringBuilder();
        TreeSet<String> imports = new TreeSet<>();
        imports.add(DOCUMENT_TYPE);
        imports.add(PATH_TYPE);
        imports.add("java.util.Objects");

        String type = Naming.editorName(group.typeName());
        String edit = Naming.editName(group.typeName());
        List<Member> members = members(group, imports);

        body.append("/** Writes {@link ").append(type).append("} into a document builder. */\n");
        body.append("final class ").append(edit).append(" implements ").append(type).append(" {\n");
        body.append("\n");
        body.append("    private final SemanticDocument.Builder builder;\n");
        body.append("    private final SemanticPath path;\n");
        body.append("\n");
        body.append("    ").append(edit).append("(SemanticDocument.Builder builder, SemanticPath path) {\n");
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

    private List<Member> members(Group group, TreeSet<String> imports) {
        List<Member> members = new ArrayList<>();
        String editor = Naming.editorName(group.typeName());
        for (Term child : group.children()) {
            if (child.isGroup()) {
                groupMembers(members, group, child, editor, imports);
            } else {
                termMembers(members, group, child, editor, imports);
            }
        }
        return members;
    }

    private void groupMembers(List<Member> members,
                              Group group,
                              Term child,
                              String editor,
                              TreeSet<String> imports) {
        String id = "\"" + child.id() + "\"";
        String childEditor = Naming.editorName(naming.typeName(child.id()));
        String childEdit = Naming.editName(naming.typeName(child.id()));
        String member = naming.memberName(child);
        if (child.cardinality().isRepeatable()) {
            members.add(new Member(
                    memberJavadoc(child, group.isRoot(), List.of(JavaText.wrap("    ", " * @return ",
                            "the handle of the instances of " + child.id() + " "
                                    + where(group) + ", which appends, reads, removes and empties"
                                    + " them"))),
                    shared(imports, "EditorList") + "<" + childEditor + ">", member, "",
                    JavaText.call("        ", "return " + runtime(imports, "Editors") + ".groups(",
                            List.of("builder", "path", id, childEdit + "::new"), ";")));
            imports.add("java.util.function.Consumer");
            members.add(new Member(
                    memberJavadoc(child, group.isRoot(), List.of(
                            JavaText.wrap("    ", " * @param block ",
                                    "what to write into the appended instance of " + child.id()),
                            JavaText.wrap("    ", " * @return ", "this editor"),
                            JavaText.wrap("    ", " * @throws NullPointerException ",
                                    "if {@code block} is {@code null}"))),
                    editor, naming.singularMemberName(child), "Consumer<" + childEditor + "> block",
                    "        " + member + "().add(block);\n"
                            + "        return this;\n"));
            return;
        }
        members.add(new Member(
                memberJavadoc(child, group.isRoot(), List.of(JavaText.wrap("    ", " * @return ",
                        "the editor of the instance of " + child.id() + " " + where(group)
                                + "; the group comes into being when a value is written into it, so"
                                + " an editor that is not written into leaves nothing behind"))),
                childEditor, member, "",
                JavaText.call("        ", "return new " + childEdit + "(",
                        List.of("builder",
                                runtime(imports, "TermPaths") + ".group(path, " + id + ")"), ";")));
        imports.add("java.util.function.Consumer");
        members.add(new Member(
                memberJavadoc(child, group.isRoot(), List.of(
                        JavaText.wrap("    ", " * @param block ",
                                "what to write into the instance of " + child.id()),
                        JavaText.wrap("    ", " * @return ", "this editor"),
                        JavaText.wrap("    ", " * @throws NullPointerException ",
                                "if {@code block} is {@code null}"))),
                editor, member, "Consumer<" + childEditor + "> block",
                "        Objects.requireNonNull(block, \"block\").accept(" + member + "());\n"
                        + "        return this;\n"));
    }

    private void termMembers(List<Member> members,
                             Group group,
                             Term child,
                             String editor,
                             TreeSet<String> imports) {
        String id = "\"" + child.id() + "\"";
        String member = naming.memberName(child);
        SemanticType type = datatype(child);
        runtime(imports, "Editors");
        runtime(imports, "TermPaths");
        runtime(imports, "Writers");
        if (child.cardinality().isRepeatable()) {
            boolean schemed = schemeIsMandatory(child);
            String identifierList = shared(imports,
                    schemed ? "SchemedIdentifierList" : "IdentifierList");
            String identifierFactory = schemed
                    ? "return Editors.schemedIdentifiers("
                    : "return Editors.identifiers(";
            String returned = type == SemanticType.IDENTIFIER
                    ? identifierList
                    : shared(imports, "ValueList") + "<" + Naming.valueType(type) + ">";
            if (type != SemanticType.IDENTIFIER) {
                Naming.valueImport(type).ifPresent(imports::add);
                runtime(imports, "Values");
                if (type == SemanticType.BINARY_OBJECT) {
                    shared(imports, "BinaryObject");
                }
            }
            String call = type == SemanticType.IDENTIFIER
                    ? JavaText.call("        ", identifierFactory,
                            List.of("builder", "path", id), ";")
                    : JavaText.call("        ", "return Editors.values(",
                            List.of("builder", "path", id,
                                    Naming.valueReader(type), Naming.valueWriter(type)), ";");
            members.add(new Member(
                    memberJavadoc(child, group.isRoot(), List.of(JavaText.wrap("    ", " * @return ",
                            "the handle of the occurrences of " + child.id() + " " + where(group)
                                    + ", which appends, reads, removes and empties them"))),
                    returned, member, "", call));
            return;
        }
        if (type == SemanticType.BINARY_OBJECT) {
            members.add(new Member(
                    memberJavadoc(child, group.isRoot(), List.of(
                            JavaText.wrap("    ", " * @param bytes ",
                                    "the content of the file, or {@code null} or an empty array to"
                                            + " remove the value"),
                            JavaText.wrap("    ", " * @param mimeCode ", "the media type of the file"),
                            JavaText.wrap("    ", " * @param filename ", "the file name of the file"),
                            JavaText.wrap("    ", " * @return ", "this editor"))),
                    editor, member, "byte[] bytes, String mimeCode, String filename",
                    JavaText.call("        ", "Editors.set(",
                            List.of("builder", "TermPaths.value(path, " + id + ")",
                                    "Editors.binary(bytes, mimeCode, filename)",
                                    Naming.valueWriter(type)), ";")
                            + "        return this;\n"));
            members.add(new Member(
                    memberJavadoc(child, group.isRoot(), List.of(
                            JavaText.wrap("    ", " * @param value ",
                                    "the file as the view of another document hands it over, or"
                                            + " {@code null} or an empty file to remove the value"),
                            JavaText.wrap("    ", " * @return ", "this editor"))),
                    editor, member, shared(imports, "BinaryObject") + " value",
                    JavaText.call("        ", "Editors.set(",
                            List.of("builder", "TermPaths.value(path, " + id + ")",
                                    "Editors.binary(value)", Naming.valueWriter(type)), ";")
                            + "        return this;\n"));
            return;
        }
        String setterType = Naming.setterType(type);
        if (type == SemanticType.IDENTIFIER) {
            List<String> names = new ArrayList<>(List.of("value"));
            if (child.component(Component.Role.SCHEME).isPresent()) {
                names.add("scheme");
            }
            if (child.component(Component.Role.SCHEME_VERSION).isPresent()) {
                names.add("schemeVersion");
            }
            int smallest = schemeIsMandatory(child) ? 2 : 1;
            for (int arity = smallest; arity <= names.size(); arity++) {
                members.add(identifierMember(group, child, editor, member, id, names, arity,
                        smallest));
            }
            members.add(new Member(
                    memberJavadoc(child, group.isRoot(), List.of(
                            JavaText.wrap("    ", " * @param value ",
                                    "the identifier as the view of another document hands it over,"
                                            + " with whatever components it carries, or"
                                            + " {@code null} to remove the value"
                                            + wholeIdentifierNote(child, smallest)),
                            JavaText.wrap("    ", " * @return ", "this editor"))),
                    editor, member, shared(imports, "Identifier") + " value",
                    JavaText.call("        ", "Editors.set(",
                            List.of("builder", "TermPaths.value(path, " + id + ")",
                                    "Editors.identifier(value)", "Writers.IDENTIFIER"), ";")
                            + "        return this;\n"));
            return;
        }
        if (type == SemanticType.QUANTITY) {
            unitCodeSibling(group, child).ifPresent(unit -> {
                imports.add("java.math.BigDecimal");
                members.add(new Member(
                        memberJavadoc(child, group.isRoot(), List.of(
                                JavaText.wrap("    ", " * @param value ",
                                        "the quantity, or {@code null} to remove it"),
                                JavaText.wrap("    ", " * @param unitCode ",
                                        "the unit of measure of that quantity, written to "
                                                + unit.id() + ", or {@code null} or the empty"
                                                + " string to remove it"),
                                JavaText.wrap("    ", " * @return ", "this editor"))),
                        editor, member, "BigDecimal value, String unitCode",
                        JavaText.call("        ", "Editors.set(",
                                List.of("builder", "TermPaths.value(path, " + id + ")", "value",
                                        Naming.valueWriter(type)), ";")
                                + JavaText.call("        ", "Editors.set(",
                                        List.of("builder",
                                                "TermPaths.value(path, \"" + unit.id() + "\")",
                                                "unitCode", Naming.valueWriter(datatype(unit))), ";")
                                + "        return this;\n"));
            });
        }
        Naming.valueImport(type).ifPresent(imports::add);
        members.add(new Member(
                memberJavadoc(child, group.isRoot(), List.of(
                        JavaText.wrap("    ", " * @param value ",
                                "the value to write, or {@code null}" + removalHint(setterType)
                                        + " to remove the value"),
                        JavaText.wrap("    ", " * @return ", "this editor"))),
                editor, member, setterType + " value",
                JavaText.call("        ", "Editors.set(",
                        List.of("builder", "TermPaths.value(path, " + id + ")", "value",
                                Naming.valueWriter(type)), ";")
                        + "        return this;\n"));
    }

    private Member identifierMember(Group group,
                                    Term child,
                                    String editor,
                                    String member,
                                    String id,
                                    List<String> names,
                                    int arity,
                                    int smallest) {
        List<String> parameters = new ArrayList<>();
        List<String> arguments = new ArrayList<>(List.of("null", "null", "null"));
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < arity; i++) {
            parameters.add("String " + names.get(i));
            arguments.set(i, names.get(i));
        }
        tags.add(JavaText.wrap("    ", " * @param value ",
                "the identifier, or {@code null} or the empty string to remove the value"
                        + (arity == 1
                                ? "; where the argument is the literal {@code null}, Java cannot"
                                        + " choose between this overload and the one taking an"
                                        + " {@code Identifier}, so write {@code (String) null} or"
                                        + " the empty string instead"
                                : "")));
        if (arity > 1) {
            tags.add(JavaText.wrap("    ", " * @param scheme ", schemeText(child)));
        }
        if (arity > 2) {
            tags.add(JavaText.wrap("    ", " * @param schemeVersion ",
                    "the version of that identification scheme"));
        }
        tags.add(JavaText.wrap("    ", " * @return ", "this editor"));
        return new Member(memberJavadoc(child, group.isRoot(), tags),
                editor, member, String.join(", ", parameters),
                JavaText.call("        ", "Editors.set(",
                        List.of("builder", "TermPaths.value(path, " + id + ")",
                                "Editors.identifier(" + String.join(", ", arguments) + ")",
                                "Writers.IDENTIFIER"), ";")
                        + "        return this;\n");
    }

    /**
     * Returns the sibling that carries the unit of measure of a quantity term, which the
     * registry pairs with it by naming its slug the slug of the quantity followed by
     * {@code UnitCode}. ESJ stores a quantity without its unit (EN 16931-1, 6.5), so the
     * two are separate terms that are nevertheless always written together.
     */
    private Optional<Term> unitCodeSibling(Group group, Term child) {
        String expected = naming.memberName(child) + "UnitCode";
        for (Term sibling : group.children()) {
            if (!sibling.isGroup()
                    && sibling.datatype().orElse(null) == SemanticType.CODE
                    && naming.memberName(sibling).equals(expected)
                    && !sibling.cardinality().isRepeatable()) {
                return Optional.of(sibling);
            }
        }
        return Optional.empty();
    }

    /**
     * Tells whether the registry declares the scheme component of a term mandatory, which
     * is what decides that the term has no one-argument way of being written.
     */
    static boolean schemeIsMandatory(Term child) {
        return child.component(Component.Role.SCHEME).filter(Component::isMandatory).isPresent();
    }

    /**
     * Returns what the overload taking a whole {@code Identifier} has to say beyond the
     * value it writes: on a term that also has a one-argument string overload, that the
     * literal {@code null} is ambiguous between the two; on a term whose scheme the
     * registry declares mandatory, where that overload does not exist, that this one
     * writes what it is handed and leaves the scheme to validation layer L2.
     */
    private static String wholeIdentifierNote(Term child, int smallest) {
        if (smallest == 1) {
            return "; where the argument is the literal {@code null}, Java cannot choose between"
                    + " this overload and the one taking a string, so write"
                    + " {@code (Identifier) null} or the empty string instead";
        }
        return ". The registry declares the scheme of " + child.id() + " mandatory, and this"
                + " overload writes the identifier as it stands: one that carries no scheme is"
                + " written and reported at validation layer L2, not refused here";
    }

    private static String schemeText(Term child) {
        String list = child.component(Component.Role.SCHEME).flatMap(Component::schemeList)
                .orElse(null);
        return list == null
                ? "the identification scheme"
                : "the identification scheme, a code of the " + JavaText.escape(list) + " list";
    }

    private static String removalHint(String setterType) {
        return "String".equals(setterType) ? " or the empty string" : "";
    }

    private static String where(Group group) {
        return group.isRoot() ? "at the root of the document" : "in this group instance";
    }

    static String termFacts(Term term) {
        if (term.isGroup()) {
            return "";
        }
        String facts = ", semantic data type " + datatype(term).registryDatatype();
        String list = term.component(Component.Role.SCHEME).flatMap(Component::schemeList)
                .orElse(null);
        return list == null ? facts : facts + ", scheme from the " + list + " list";
    }

    private String memberJavadoc(Term term, boolean root, List<String> tags) {
        StringBuilder doc = new StringBuilder("    /**\n");
        doc.append(JavaText.wrap("    ", " * ",
                JavaText.escape(term.id() + " " + term.name() + ". " + term.description())));
        doc.append("     *\n");
        doc.append(JavaText.wrap("    ", " * <p>", JavaText.escape(
                "Declared cardinality " + term.cardinality() + termFacts(term) + ".")));
        doc.append("     *\n");
        for (String tag : tags) {
            doc.append(tag);
        }
        doc.append("     */\n");
        return doc.toString();
    }

    /**
     * One member of an editor: what it looks like in the interface, and what it does in
     * the implementation.
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

    private String file(TreeSet<String> imports, String body) {
        StringBuilder out = new StringBuilder(header);
        out.append("\n");
        out.append("package ").append(typedPackage).append(";\n");
        out.append("\n");
        for (String type : imports) {
            out.append("import ").append(type).append(";\n");
        }
        out.append("\n");
        out.append(body);
        return out.toString();
    }

    /** One group of the typed view: a business group, or the root of the document. */
    private record Group(Term term, String typeName, List<Term> children) {

        boolean isRoot() {
            return term == null;
        }
    }
}
