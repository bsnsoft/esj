package de.bsnsoft.esj.generator;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Emits the model schema of one edition, for example
 * {@code schema/esj-en16931-2017.schema.json}: the format schema of the repository,
 * narrowed to the business terms of that edition's registry.
 *
 * <p>The format schema knows the shape of a document and nothing about EN 16931: a value
 * is a non-empty string there, or an object carrying content and at least one
 * supplementary component. This one adds what only the registry knows — one
 * {@code patternProperties} entry per term path, mapped to the content grammar and the
 * component set the semantic data type of that term requires (specification,
 * section 6.2) — and refuses every other member of {@code values}. It is where a tool
 * with a JSON Schema validator and no registry gets the per-term typing; it is not a
 * second definition of the format, and it does not replace validation layers L2 and L3.
 */
final class ModelSchema {

    private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";
    private static final String INDEX = "/(?:0|[1-9][0-9]*)";

    /**
     * The canonical decimal form of the specification, section 6.4: an optional sign, an
     * integer part without a leading zero, an optional fraction without a trailing zero,
     * and no sign on a zero, which is what the leading lookahead removes.
     */
    private static final String DECIMAL_PATTERN = "^(?!-0$)-?(?:0|[1-9][0-9]*)(?:\\.[0-9]*[1-9])?$";

    /** The bound section 6.4 puts on the canonical decimal form, in characters. */
    private static final int DECIMAL_MAX_LENGTH = 64;

    /** The calendar date of the specification, section 6.5. */
    private static final String DATE_PATTERN =
            "^[1-9][0-9]{3}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])$";

    /**
     * The time of day of the specification, section 6.5: two-digit hours, minutes and
     * seconds and an offset that is always present, where the leading lookahead removes the
     * numeric spelling of the zero offset, which is written {@code Z}.
     */
    private static final String TIME_PATTERN = "^(?![0-9:]+[+-]00:00$)"
            + "(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]"
            + "(?:Z|[+-](?:(?:0[0-9]|1[0-3]):[0-5][0-9]|14:00))$";

    /**
     * The canonical padded base64 of the specification, section 6.7, including the rule
     * that the pad bits of the last quantum are zero.
     */
    private static final String BASE64_PATTERN = "^(?:[A-Za-z0-9+/]{4})*"
            + "(?:[A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{2}[AEIMQUYcgkosw048]=|[A-Za-z0-9+/][AQgw]==)$";

    private final Registry registry;
    private final String fileName;
    private final String registryFile;
    private final String formatSchemaId;
    private final String comment;

    /**
     * Prepares the emitter.
     *
     * @param registry       the registry whose terms the schema names
     * @param fileName       the file name the schema is written under, which is also the
     *                       last segment of its {@code $id}
     * @param registryFile   the path of the registry file inside the repository, which the
     *                       schema names as its provenance
     * @param formatSchemaId the {@code $id} of the format schema this one extends
     * @param comment        the sentence that marks the file as generated
     */
    ModelSchema(Registry registry,
                String fileName,
                String registryFile,
                String formatSchemaId,
                String comment) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.registryFile = Objects.requireNonNull(registryFile, "registryFile");
        this.formatSchemaId = Objects.requireNonNull(formatSchemaId, "formatSchemaId");
        this.comment = Objects.requireNonNull(comment, "comment");
    }

    /**
     * Emits the schema.
     *
     * @return the schema, in the pretty layout of the repository, ending in a line break
     */
    String source() {
        List<Entry> entries = entries();
        Map<String, String> definitions = definitions(entries);
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  ").append(member("$schema", DIALECT)).append(",\n");
        out.append("  ").append(member("$id", identifier())).append(",\n");
        out.append("  ").append(member("$comment", comment + " " + provenance())).append(",\n");
        out.append("  ").append(member("title", title())).append(",\n");
        out.append("  ").append(member("description", description())).append(",\n");
        out.append("  \"allOf\": [\n");
        out.append("    {\n");
        out.append("      ").append(member("$comment",
                "Everything the format schema requires of a document: the envelope, the path"
                        + " grammar and the shape of a value. A validator therefore needs both"
                        + " files; the format schema is resolved by its identifier.")).append(",\n");
        out.append("      ").append(member("$ref", formatSchemaId)).append("\n");
        out.append("    },\n");
        out.append("    {\n");
        out.append("      \"properties\": {\n");
        out.append("        \"semanticModel\": {\n");
        out.append("          ").append(member("$comment", editionComment())).append(",\n");
        out.append("          ").append(member("const", editionToken())).append("\n");
        out.append("        },\n");
        out.append("        \"values\": {\n");
        out.append("          ").append(member("$comment", valuesComment())).append(",\n");
        out.append("          \"type\": \"object\",\n");
        out.append("          \"patternProperties\": {\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            out.append("            ").append(quote(entry.pattern())).append(": { \"$ref\": ")
                    .append(quote("#/$defs/" + entry.valueDefinition()))
                    .append(" }").append(i == entries.size() - 1 ? "" : ",").append("\n");
        }
        out.append("          },\n");
        out.append("          \"additionalProperties\": false\n");
        out.append("        }\n");
        out.append("      }\n");
        out.append("    }\n");
        out.append("  ],\n");
        out.append("  \"$defs\": {\n");
        int written = 0;
        for (Map.Entry<String, String> definition : definitions.entrySet()) {
            written++;
            out.append("    ").append(quote(definition.getKey())).append(": ")
                    .append(definition.getValue())
                    .append(written == definitions.size() ? "" : ",").append("\n");
        }
        out.append("  }\n");
        out.append("}\n");
        return out.toString();
    }

    private String identifier() {
        int cut = formatSchemaId.lastIndexOf('/');
        return cut < 0 ? fileName : formatSchemaId.substring(0, cut + 1) + fileName;
    }

    private String title() {
        return "EN16931 Semantic JSON 0.1 with the terms of " + registry.edition();
    }

    private String provenance() {
        return "It names every business term of " + registry.edition()
                + " as the registry " + registryFile + " records it.";
    }

    private String description() {
        return "The format schema of ESJ 0.1, narrowed to the business terms of the semantic model."
                + " Every member name of values must be the path of a term the registry knows, and"
                + " the value at it must satisfy the content grammar and carry the supplementary"
                + " components the semantic data type of that term allows. This is a convenience"
                + " for tools that have a JSON Schema validator and no registry; the normative"
                + " structural checks are validation layers L2 and L3 of SPEC.md, and they check"
                + " more than this file can.";
    }

    /**
     * Returns the edition string as a document writes it: the {@code edition} member of the
     * registry with every space removed (specification, section 10).
     */
    private String editionToken() {
        return registry.edition().replace(" ", "");
    }

    private String editionComment() {
        return "The edition this schema is generated for. A generated schema is scoped to the"
                + " terms of one registry, so it pins the edition the document names"
                + " (SPEC.md section 6.2); the format schema carries the edition grammar"
                + " instead and accepts every edition.";
    }

    private String valuesComment() {
        return "One entry per business term of the registry. The pattern spells the parent chain of"
                + " the term, with an occurrence index wherever the declared cardinality of a group"
                + " or of the term itself is greater than one, so a path that indexes a term the"
                + " model declares once, or omits the index of a repeatable one, matches no entry."
                + " additionalProperties is false, which is what turns an unknown term into an"
                + " error here; it also rejects extension terms, because this file is generated"
                + " from the core registry alone. What it still cannot say: whether a mandatory"
                + " term is present, whether occurrence indices are dense, whether a date that"
                + " matches the pattern exists in the calendar, and whether the document obeys the"
                + " business rules of EN 16931-1. Those are SPEC.md sections 9.2 to 9.4. The value"
                + " of each entry is the shape the semantic data type of that term requires, named"
                + " once in $defs so that the entries stay readable.";
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Term term : registry.terms()) {
            if (term.isGroup()) {
                continue;
            }
            Shape shape = shape(term);
            for (List<String> chain : registry.chains(term.id())) {
                entries.add(new Entry(pattern(chain), example(chain), shape.definitionName()));
            }
        }
        entries.sort((left, right) -> left.example().compareTo(right.example()));
        return entries;
    }

    /**
     * Returns the definitions the entries refer to, in the order of the semantic data
     * types and, within one of them, in the order the terms of the registry introduce
     * their component sets.
     */
    private Map<String, String> definitions(List<Entry> entries) {
        Map<String, Shape> shapes = new LinkedHashMap<>();
        for (SemanticType type : SemanticType.values()) {
            for (Term term : registry.terms()) {
                if (term.isGroup() || term.datatype().orElseThrow() != type) {
                    continue;
                }
                Shape shape = shape(term);
                if (used(entries, shape.definitionName())) {
                    shapes.putIfAbsent(shape.definitionName(), shape);
                }
            }
        }
        Map<String, String> definitions = new LinkedHashMap<>();
        shapes.forEach((name, shape) -> definitions.put(name, shape.source()));
        return definitions;
    }

    private static boolean used(List<Entry> entries, String definition) {
        for (Entry entry : entries) {
            if (entry.valueDefinition().equals(definition)) {
                return true;
            }
        }
        return false;
    }

    private static Shape shape(Term term) {
        SemanticType type = term.datatype().orElseThrow(() -> new IllegalStateException(
                "the registry gives " + term.id() + " no semantic data type"));
        return new Shape(type, term.components());
    }

    private String pattern(List<String> chain) {
        StringBuilder pattern = new StringBuilder("^");
        for (String id : chain) {
            pattern.append('/').append(id);
            if (repeatable(id)) {
                pattern.append(INDEX);
            }
        }
        return pattern.append('$').toString();
    }

    private SemanticPath example(List<String> chain) {
        StringBuilder path = new StringBuilder();
        for (String id : chain) {
            path.append('/').append(id);
            if (repeatable(id)) {
                path.append("/0");
            }
        }
        return SemanticPath.of(path.toString());
    }

    private boolean repeatable(String id) {
        return registry.term(id).orElseThrow(() ->
                new IllegalStateException("the registry does not know " + id)).isRepeatable();
    }

    private static String member(String name, String value) {
        return quote(name) + ": " + quote(value);
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String lowerCamel(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static String upperCamel(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** One {@code patternProperties} entry: the pattern, the path it sorts by, and the value shape. */
    private record Entry(String pattern, SemanticPath example, String valueDefinition) {
    }

    /**
     * The shape the value of a term must have: the semantic data type, which decides the
     * content grammar, and the supplementary components the registry lists for that term,
     * which decide whether the value is written as a string, as an object, or as either.
     *
     * @param type       the semantic data type
     * @param components the components the registry lists, in the order it lists them
     */
    private record Shape(SemanticType type, List<Component> components) {

        /**
         * Returns the name this shape is defined under. It names the semantic data type
         * and, where the term carries components, each of them with its cardinality, so
         * that two terms share a definition exactly when they share a shape.
         */
        String definitionName() {
            StringBuilder name = new StringBuilder(lowerCamel(type.registryDatatype())).append("Value");
            if (!components.isEmpty()) {
                name.append("With");
                for (Component component : components) {
                    name.append(component.isMandatory() ? "" : "Optional")
                            .append(upperCamel(component.role().jsonMember()));
                }
            }
            return name.toString();
        }

        /** Returns the definition, indented as a member of the {@code $defs} object. */
        String source() {
            if (components.isEmpty()) {
                return "{\n"
                        + "      " + member("$comment", comment()) + ",\n"
                        + "      " + contentMembers("      ") + "\n"
                        + "    }";
            }
            if (mandatory().isEmpty()) {
                return "{\n"
                        + "      " + member("$comment", comment()) + ",\n"
                        + "      " + quote("oneOf") + ": [\n"
                        + "        { " + contentMembers(null) + " },\n"
                        + "        " + object("        ") + "\n"
                        + "      ]\n"
                        + "    }";
            }
            return object("    ");
        }

        /** Returns the sentence that says which semantic data type this shape belongs to. */
        private String comment() {
            String content = switch (type) {
                case TEXT -> "Registry datatype Text: any non-empty string, line breaks included"
                        + " (SPEC.md section 6.8).";
                case IDENTIFIER -> "Registry datatype Identifier: any non-empty string"
                        + " (SPEC.md section 6.6).";
                case CODE -> "Registry datatype Code: the code as the list the semantic model fixes"
                        + " for the term spells it; membership in that list is a business rule and"
                        + " is not checked here (SPEC.md sections 6.3 and 9.4).";
                case DATE -> "Registry datatype Date: the date grammar of SPEC.md section 6.5."
                        + " Whether the day exists in the calendar is beyond a pattern and stays a"
                        + " check of validation layer L2.";
                case TIME -> "Registry datatype Time: the time grammar of SPEC.md section 6.5,"
                        + " which carries the offset from UTC and admits one spelling per"
                        + " value.";
                case AMOUNT, UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE ->
                        "Registry datatype " + type.registryDatatype() + ": the canonical decimal"
                                + " form of SPEC.md section 6.4, bounded at "
                                + DECIMAL_MAX_LENGTH + " characters.";
                case DOCUMENT_REFERENCE -> "Registry datatype DocumentReference: any non-empty"
                        + " string; the type is derived from Identifier and EN 16931-1 gives it"
                        + " content alone (SPEC.md section 6.3).";
                case BINARY_OBJECT -> "Registry datatype BinaryObject: the canonical padded base64"
                        + " of SPEC.md section 6.7.";
            };
            if (components.isEmpty()) {
                return content + " The registry lists no supplementary component for the terms of"
                        + " this shape, so such a value is always written as a string.";
            }
            if (mandatory().isEmpty()) {
                return content + " Every supplementary component of this shape is optional, so the"
                        + " value is a string where it carries none and an object where it carries"
                        + " one (SPEC.md section 6.1).";
            }
            List<Component> optional = new ArrayList<>(components);
            optional.removeAll(mandatory());
            return content + " The registry declares " + names(mandatory()) + " mandatory for the"
                    + " terms of this shape, so such a value is always written as an object"
                    + (optional.isEmpty() ? "." : ", and it may carry " + names(optional)
                            + " beside the mandatory ones.");
        }

        private static String names(List<Component> components) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < components.size(); i++) {
                names.append(i == 0 ? "" : i == components.size() - 1 ? " and " : ", ")
                        .append(components.get(i).role().jsonMember());
            }
            return names.toString();
        }

        /** Returns the members that constrain the content, on one line or over several. */
        private String contentMembers(String indent) {
            List<String> members = new ArrayList<>();
            members.add(quote("type") + ": " + quote("string"));
            switch (type) {
                case AMOUNT, UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE -> {
                    members.add(quote("maxLength") + ": " + DECIMAL_MAX_LENGTH);
                    members.add(quote("pattern") + ": " + quote(DECIMAL_PATTERN));
                }
                case DATE -> members.add(quote("pattern") + ": " + quote(DATE_PATTERN));
                case TIME -> members.add(quote("pattern") + ": " + quote(TIME_PATTERN));
                case BINARY_OBJECT -> members.add(quote("pattern") + ": " + quote(BASE64_PATTERN));
                case TEXT, IDENTIFIER, CODE, DOCUMENT_REFERENCE -> { }
            }
            return indent == null
                    ? String.join(", ", members)
                    : String.join(",\n" + indent, members);
        }

        /**
         * Returns the object form of the value. The opening brace follows whatever stands
         * before it; every line after the first carries the given indent.
         */
        private String object(String indent) {
            List<Component> alternatives = mandatory().isEmpty() && components.size() > 1
                    ? components
                    : List.of();
            List<Component> required = mandatory().isEmpty() && components.size() == 1
                    ? components
                    : mandatory();
            StringBuilder out = new StringBuilder("{\n");
            if (!mandatory().isEmpty()) {
                out.append(indent).append("  ").append(member("$comment", comment())).append(",\n");
            }
            out.append(indent).append("  ").append(quote("type")).append(": ")
                    .append(quote("object")).append(",\n");
            out.append(indent).append("  ").append(quote("required")).append(": [")
                    .append(quote("value"));
            for (Component component : required) {
                out.append(", ").append(quote(component.role().jsonMember()));
            }
            out.append("],\n");
            if (!alternatives.isEmpty()) {
                out.append(indent).append("  ").append(quote("$comment")).append(": ")
                        .append(quote("A value object carries at least one supplementary component;"
                                + " without a component the content is written as a string"
                                + " (SPEC.md section 6.1, rule 3).")).append(",\n");
                out.append(indent).append("  ").append(quote("anyOf")).append(": [\n");
                for (int i = 0; i < alternatives.size(); i++) {
                    out.append(indent).append("    { ").append(quote("required")).append(": [")
                            .append(quote(alternatives.get(i).role().jsonMember())).append("] }")
                            .append(i == alternatives.size() - 1 ? "" : ",").append("\n");
                }
                out.append(indent).append("  ],\n");
            }
            out.append(indent).append("  ").append(quote("additionalProperties")).append(": false,\n");
            out.append(indent).append("  ").append(quote("properties")).append(": {\n");
            out.append(indent).append("    ").append(quote("value")).append(": { ")
                    .append(contentMembers(null)).append(" }");
            for (Component component : components) {
                out.append(",\n").append(indent).append("    ")
                        .append(quote(component.role().jsonMember())).append(": { ")
                        .append(quote("type")).append(": ").append(quote("string")).append(" }");
            }
            out.append("\n").append(indent).append("  }\n");
            out.append(indent).append("}");
            return out.toString();
        }

        private List<Component> mandatory() {
            List<Component> mandatory = new ArrayList<>();
            for (Component component : components) {
                if (component.isMandatory()) {
                    mandatory.add(component);
                }
            }
            return mandatory;
        }
    }
}
