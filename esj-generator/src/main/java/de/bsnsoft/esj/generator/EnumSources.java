package de.bsnsoft.esj.generator;

import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Emits one Java enum per code list a business term of the registry draws from, from the
 * dated snapshots of a rule pack.
 *
 * <p>A constant carries three facts and nothing else: the code, the name its publisher
 * gives the code and the identifier of the list it came from. Whether a code is admissible
 * is not one of them — that is a business rule, decided by a named rule of a named pack
 * version against the same snapshot, and an enum that refused a code would be a second
 * validator with a release cycle of its own.
 *
 * <p>How a constant is named is written out in {@code model/enums.json} and implemented by
 * {@link #constantName(String)}: the code or the publisher's name, decomposed to its base
 * letters, upper cased and joined with underscores; a name longer than sixty characters cut
 * at an underscore; a name that is empty or starts with a digit prefixed; and a name two
 * codes would share given to the first of them in list order, the later one suffixed with
 * its own code. The configuration may name a constant instead, which is what the VAT
 * category codes need: their published names are sentences.
 */
final class EnumSources {

    /** The longest derived constant name; a longer one is cut at an underscore. */
    private static final int MAX_NAME = 60;

    private final EnumFacts.Configuration configuration;

    private final Map<String, EnumFacts.Snapshot> snapshots;

    private final Registry registry;

    /**
     * Prepares the emitter.
     *
     * @param configuration what {@code model/enums.json} says
     * @param snapshots     the snapshots of the pack, by list identifier
     * @param registry      the registry the business terms using a list are read from
     * @throws NullPointerException if an argument is {@code null}
     */
    EnumSources(EnumFacts.Configuration configuration,
                Map<String, EnumFacts.Snapshot> snapshots,
                Registry registry) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.snapshots = Map.copyOf(snapshots);
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Emits every enum of the configuration.
     *
     * @return the sources, by file name, in the order of the configuration
     * @throws IllegalStateException if a snapshot the configuration names was not loaded,
     *                               if no term of the registry uses a code list, or if two
     *                               constants of one enum cannot be told apart
     */
    Map<String, String> sources() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (EnumFacts.Spec spec : configuration.enums()) {
            sources.put(spec.type() + ".java", source(spec));
        }
        return sources;
    }

    private String source(EnumFacts.Spec spec) {
        List<EnumFacts.Snapshot> lists = new ArrayList<>();
        for (String listId : spec.lists()) {
            EnumFacts.Snapshot snapshot = snapshots.get(listId);
            if (snapshot == null) {
                throw new IllegalStateException("the enum " + spec.type() + " is made of the code"
                        + " list " + listId + ", which the rule pack does not carry");
            }
            lists.add(snapshot);
        }
        List<Constant> constants = constants(spec, lists);
        List<String> users = users(spec);

        StringBuilder out = new StringBuilder();
        out.append(header(lists));
        out.append("package ").append(configuration.packageName()).append(";\n\n");
        out.append("import java.util.LinkedHashMap;\n");
        out.append("import java.util.Map;\n");
        out.append("import java.util.Objects;\n\n");
        out.append(typeComment(spec, lists, users));
        out.append("public enum ").append(spec.type()).append(" implements Coded {\n\n");
        for (int i = 0; i < constants.size(); i++) {
            Constant constant = constants.get(i);
            out.append(constantComment(constant, lists.size() > 1));
            out.append("    ").append(constant.name()).append('(')
                    .append(literal(constant.code())).append(", ")
                    .append(literal(constant.publishedName())).append(", ")
                    .append(literal(constant.listId())).append(')')
                    .append(i == constants.size() - 1 ? ";" : ",").append('\n');
        }
        out.append('\n');
        out.append(members(spec));
        out.append("}\n");
        return out.toString();
    }

    private String header(List<EnumFacts.Snapshot> lists) {
        List<String> named = new ArrayList<>();
        for (EnumFacts.Snapshot list : lists) {
            named.add(list.listId() + " of " + list.retrieved());
        }
        return JavaText.codeListHeader(configuration.packId(), configuration.packVersion(), named);
    }

    private String typeComment(EnumFacts.Spec spec,
                               List<EnumFacts.Snapshot> lists,
                               List<String> users) {
        StringBuilder out = new StringBuilder("/**\n");
        out.append(JavaText.wrap("", " * ", JavaText.escape(spec.summary())));
        for (EnumFacts.Snapshot list : lists) {
            out.append(" *\n");
            out.append(JavaText.wrap("", " * <p>", JavaText.escape(list.name() + ". " + list.publisher()
                    + ", snapshot of " + list.retrieved() + ", " + list.entries().size() + " codes.")));
        }
        out.append(" *\n");
        out.append(JavaText.wrap("", " * <p>", "The registry names this list at "
                + String.join(", ", users) + "."));
        out.append(" *\n");
        out.append(JavaText.wrap("", " * <p>", "A code the snapshot does not carry is representable:"
                + " {@code " + spec.type() + ".custom(code)} yields a {@link CustomCode}, and"
                + " {@code resolve(code)} yields the constant where there is one and a"
                + " {@code CustomCode} where there is none. Whether a code is admissible is decided"
                + " by the rule pack and not here."));
        out.append(" */\n");
        return out.toString();
    }

    private String constantComment(Constant constant, boolean namedList) {
        String text = "{@code " + JavaText.escape(constant.code()) + "}";
        if (!constant.publishedName().isEmpty()) {
            text += ", " + JavaText.escape(constant.publishedName());
        }
        if (namedList) {
            text += " (" + constant.listId() + ")";
        }
        String comment = "    /** " + text + ". */\n";
        if (comment.length() <= 100) {
            return comment;
        }
        return "    /**\n" + JavaText.wrap("    ", " * ", text + ".") + "     */\n";
    }

    private String members(EnumFacts.Spec spec) {
        String type = spec.type();
        return "    private static final Map<String, " + type + "> BY_CODE = byCode();\n"
                + "\n"
                + "    private final String code;\n"
                + "\n"
                + "    private final String publishedName;\n"
                + "\n"
                + "    private final String listId;\n"
                + "\n"
                + "    " + type + "(String code, String publishedName, String listId) {\n"
                + "        this.code = code;\n"
                + "        this.publishedName = publishedName;\n"
                + "        this.listId = listId;\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * Returns the constant carrying a code.\n"
                + "     *\n"
                + "     * <p>The comparison is exact, code point by code point: a code list is a list of\n"
                + "     * codes and not of their spellings.\n"
                + "     *\n"
                + "     * @param code the code\n"
                + "     * @return the constant\n"
                + "     * @throws IllegalArgumentException if the snapshot does not carry the code\n"
                + "     * @throws NullPointerException     if {@code code} is {@code null}\n"
                + "     */\n"
                + "    public static " + type + " of(String code) {\n"
                + "        " + type + " value = BY_CODE.get(Objects.requireNonNull(code, \"code\"));\n"
                + "        if (value == null) {\n"
                + "            throw new IllegalArgumentException(\"the snapshot of " + type
                + " does not carry the code \"\n"
                + "                    + code + \"; " + type
                + ".custom(code) represents a code it does not have\");\n"
                + "        }\n"
                + "        return value;\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * Returns the constant carrying a code, or a custom code where there is none.\n"
                + "     *\n"
                + "     * @param code the code as a document carries it\n"
                + "     * @return the constant or a {@link CustomCode}\n"
                + "     * @throws NullPointerException     if {@code code} is {@code null}\n"
                + "     * @throws IllegalArgumentException if the code is blank\n"
                + "     */\n"
                + "    public static Coded resolve(String code) {\n"
                + "        " + type + " value = BY_CODE.get(Objects.requireNonNull(code, \"code\"));\n"
                + "        return value != null ? value : new CustomCode(\"" + type + "\", code);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * Returns a code the snapshot does not carry.\n"
                + "     *\n"
                + "     * @param code the code\n"
                + "     * @return the custom code\n"
                + "     * @throws IllegalArgumentException if the snapshot carries the code, which has a\n"
                + "     *                                  constant and must not have a second"
                + " representation,\n"
                + "     *                                  or if the code is blank\n"
                + "     * @throws NullPointerException     if {@code code} is {@code null}\n"
                + "     */\n"
                + "    public static Coded custom(String code) {\n"
                + "        " + type + " value = BY_CODE.get(Objects.requireNonNull(code, \"code\"));\n"
                + "        if (value != null) {\n"
                + "            throw new IllegalArgumentException(\"the snapshot of " + type
                + " carries the code \"\n"
                + "                    + code + \" as \" + value.name());\n"
                + "        }\n"
                + "        return new CustomCode(\"" + type + "\", code);\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * Returns the code as it is written into the document.\n"
                + "     *\n"
                + "     * @return the code\n"
                + "     */\n"
                + "    @Override\n"
                + "    public String code() {\n"
                + "        return code;\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * Returns the name the publisher of the list gives the code.\n"
                + "     *\n"
                + "     * @return the name, empty where the publisher gives none\n"
                + "     */\n"
                + "    @Override\n"
                + "    public String publishedName() {\n"
                + "        return publishedName;\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * Returns the identifier of the code list the constant came from.\n"
                + "     *\n"
                + "     * @return the list identifier, for example {@code " + spec.lists().get(0) + "}\n"
                + "     */\n"
                + "    @Override\n"
                + "    public String listId() {\n"
                + "        return listId;\n"
                + "    }\n"
                + "\n"
                + "    private static Map<String, " + type + "> byCode() {\n"
                + "        Map<String, " + type + "> byCode = new LinkedHashMap<>();\n"
                + "        for (" + type + " value : values()) {\n"
                + "            byCode.put(value.code, value);\n"
                + "        }\n"
                + "        return Map.copyOf(byCode);\n"
                + "    }\n";
    }

    private List<Constant> constants(EnumFacts.Spec spec, List<EnumFacts.Snapshot> lists) {
        List<Constant> constants = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        for (EnumFacts.Snapshot list : lists) {
            for (EnumFacts.Entry entry : list.entries()) {
                if (!codes.add(entry.value())) {
                    throw new IllegalStateException("the enum " + spec.type()
                            + " is made of lists that both carry the code " + entry.value());
                }
                String name = name(spec, entry, names);
                if (!names.add(name)) {
                    throw new IllegalStateException("the enum " + spec.type()
                            + " would carry the constant " + name + " twice");
                }
                constants.add(new Constant(name, entry.value(), entry.name(), list.listId()));
            }
        }
        return constants;
    }

    private String name(EnumFacts.Spec spec, EnumFacts.Entry entry, Set<String> taken) {
        String named = spec.constants().get(entry.value());
        if (named != null) {
            return named;
        }
        String derived = constantName(spec.fromCode() ? entry.value() : entry.name());
        if (derived.isEmpty()) {
            derived = constantName(entry.value());
        }
        if (derived.isEmpty() || Character.isDigit(derived.charAt(0))) {
            derived = "C_" + derived;
        }
        if (taken.contains(derived)) {
            derived = derived + "_" + constantName(entry.value());
        }
        return derived;
    }

    /**
     * Derives a constant name from a code or from the name a publisher gives a code.
     *
     * @param source the code or the name
     * @return the name with its base letters upper cased and every other run of characters
     *         replaced by one underscore, cut at an underscore where it would be longer
     *         than sixty characters
     */
    static String constantName(String source) {
        String decomposed = Normalizer.normalize(Objects.requireNonNull(source, "source"),
                Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder();
        boolean separated = false;
        for (int i = 0; i < decomposed.length(); i++) {
            char character = Character.toUpperCase(decomposed.charAt(i));
            if (character >= 'A' && character <= 'Z' || character >= '0' && character <= '9') {
                out.append(character);
                separated = false;
            } else if (Character.getType(decomposed.charAt(i)) == Character.NON_SPACING_MARK) {
                continue;
            } else if (out.length() > 0 && !separated) {
                out.append('_');
                separated = true;
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '_') {
            out.setLength(out.length() - 1);
        }
        if (out.length() <= MAX_NAME) {
            return out.toString();
        }
        String cut = out.substring(0, MAX_NAME);
        int underscore = cut.lastIndexOf('_');
        return underscore > 0 ? cut.substring(0, underscore) : cut;
    }

    private List<String> users(EnumFacts.Spec spec) {
        List<String> users = new ArrayList<>();
        for (Term term : registry.terms()) {
            if (term.codeList().filter(spec.registryList()::equals).isPresent()) {
                users.add(term.id());
            }
            for (Component component : term.components()) {
                if (component.schemeList().filter(spec.registryList()::equals).isPresent()) {
                    users.add(component.id());
                }
            }
        }
        if (users.isEmpty()) {
            throw new IllegalStateException("no term of the registry names the code list "
                    + spec.registryList() + ", which the enum " + spec.type() + " is made of");
        }
        return users;
    }

    private static String literal(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private record Constant(String name, String code, String publishedName, String listId) {
    }
}
