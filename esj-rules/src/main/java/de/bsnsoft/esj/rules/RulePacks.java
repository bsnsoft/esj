package de.bsnsoft.esj.rules;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a rule pack file.
 *
 * <p>The reader is strict in the way the rest of this project is strict: a member the
 * format does not define is refused rather than ignored, because a rule file is operator
 * material and a member nobody reads is either a mistake or a rule that will never run.
 * The same goes for a duplicate rule identifier and for a severity the language does not
 * have.
 *
 * <p>What the reader does not do is resolve anything. It produces a {@link RulePack}, which
 * is the file; the paths, the operators and the code lists are resolved by
 * {@link RuleEngine#compile}, because that needs a registry and the file does not carry one.
 * The separation is what lets a test read a pack and count its rules without a registry, and
 * what lets one pack be compiled against two registry editions when there are two.
 */
public final class RulePacks {

    /** The members a rule file may carry. */
    private static final Set<String> PACK_MEMBERS = Set.of(
            "$schema", "id", "version", "verifiedAgainst", "description",
            "codeLists", "javaRules", "files", "rules");

    /** What a rule identifier looks like: the shape the standards give their rules. */
    private static final Pattern RULE_ID = Pattern.compile("[A-Z][A-Z0-9-]*");

    /** What a pack identifier looks like. */
    private static final Pattern PACK_ID = Pattern.compile("[a-z][a-z0-9-]*");

    /** What a pack version looks like. */
    private static final Pattern PACK_VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.-]*");

    /** What a rule file of a pack is called, relative to the directory of the manifest. */
    private static final Pattern FILE_PATH = Pattern.compile("rules/[a-z0-9-]+\\.json");

    /** The members a rule may carry. */
    private static final Set<String> RULE_MEMBERS = Set.of(
            "id", "severity", "context", "terms", "assert", "bind", "message", "source", "note",
            "warn");

    /** What the second assertion of a rule is written with. */
    private static final Set<String> WARNING_MEMBERS = Set.of("assert", "message");

    private RulePacks() {
        throw new AssertionError("no instances");
    }

    /**
     * Reads a rule pack from a stream.
     *
     * @param in   the bytes of the rule file, UTF-8; the caller owns the stream
     * @param what what is being read, for the message of a failure
     * @return the pack
     * @throws RulePackException    if the bytes are not a rule file
     * @throws NullPointerException if an argument is {@code null}
     */
    public static RulePack read(InputStream in, String what) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(what, "what");
        Json json = Json.read(in, what);
        Map<String, Json> members = json.asObject(what);
        reject(members.keySet(), PACK_MEMBERS, what, "the rule pack format");
        String id = check(text(members, "id", what), PACK_ID, what, "the identifier of a pack");
        String version = check(text(members, "version", what), PACK_VERSION, what,
                "the version of a pack");
        if (!members.containsKey("rules") && !members.containsKey("files")) {
            throw new RulePackException(what + " has no rules member and names no rule file;"
                    + " a pack that carries nothing would report nothing and claim to have checked");
        }
        return new RulePack(id, version,
                text(members, "verifiedAgainst", what),
                text(members, "description", what),
                readCodeLists(members, what),
                readJavaRules(members, what),
                readFiles(members, what),
                readRules(members.get("rules"), what, new LinkedHashSet<>()));
    }

    /**
     * Reads a rule pack that travels in this module's jar.
     *
     * @param packId  the identifier of the pack, for example {@code en16931}
     * @param version the version of the pack, for example {@code 1.3.16}
     * @return the pack
     * @throws RulePackException    if this build carries no such pack, or carries one that
     *                              is not a rule file
     * @throws NullPointerException if an argument is {@code null}
     */
    public static RulePack bundled(String packId, String version) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(version, "version");
        String resource = resourceDirectory(packId, version) + "pack.json";
        try (InputStream in = RulePacks.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new RulePackException("this build carries no rule pack " + packId + "/" + version);
            }
            RulePack manifest = read(in, "the rule pack " + packId + "/" + version);
            if (!manifest.id().equals(packId) || !manifest.version().equals(version)) {
                throw new RulePackException("the rule pack under " + packId + "/" + version
                        + " calls itself " + manifest.name());
            }
            return withFiles(manifest);
        } catch (IOException e) {
            throw new RulePackException("the rule pack " + packId + "/" + version
                    + " could not be read: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the resource directory of a bundled pack, relative to the package of this
     * class.
     *
     * @param packId  the identifier of the pack
     * @param version the version of the pack
     * @return the directory, with a trailing solidus
     */
    static String resourceDirectory(String packId, String version) {
        return "packs/" + packId + "/" + version + "/";
    }

    private static Map<String, String> readCodeLists(Map<String, Json> members, String what) {
        Json lists = members.get("codeLists");
        if (lists == null) {
            return Map.of();
        }
        Map<String, String> byId = new LinkedHashMap<>();
        for (Map.Entry<String, Json> entry : lists.asObject("codeLists of " + what).entrySet()) {
            byId.put(entry.getKey(),
                    entry.getValue().asString("the snapshot day of " + entry.getKey() + " in " + what));
        }
        return byId;
    }

    private static List<String> readJavaRules(Map<String, Json> members, String what) {
        Json classes = members.get("javaRules");
        if (classes == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Json name : classes.asArray("javaRules of " + what)) {
            String className = name.asString("a class name in javaRules of " + what);
            if (!names.add(className)) {
                throw new RulePackException(what + " names the Java rule " + className + " twice");
            }
        }
        return List.copyOf(names);
    }

    private static List<String> readFiles(Map<String, Json> members, String what) {
        Json files = members.get("files");
        if (files == null) {
            return List.of();
        }
        Set<String> paths = new LinkedHashSet<>();
        for (Json file : files.asArray("files of " + what)) {
            String path = file.asString("a rule file of " + what);
            if (!FILE_PATH.matcher(path).matches()) {
                throw new RulePackException(what + " names the rule file " + path
                        + ", which is not a path of the form rules/<name>.json below the manifest");
            }
            if (!paths.add(path)) {
                throw new RulePackException(what + " names the rule file " + path + " twice");
            }
        }
        return List.copyOf(paths);
    }

    private static List<RuleDefinition> readRules(Json rules, String what, Set<String> ids) {
        if (rules == null) {
            return List.of();
        }
        List<RuleDefinition> read = new ArrayList<>();
        for (Json rule : rules.asArray("the rules of " + what)) {
            RuleDefinition definition = readRule(rule, what);
            if (!ids.add(definition.id())) {
                throw new RulePackException(what + " carries the rule " + definition.id() + " twice");
            }
            read.add(definition);
        }
        return List.copyOf(read);
    }

    private static RuleDefinition readRule(Json rule, String what) {
        Map<String, Json> members = rule.asObject("a rule of " + what);
        String id = check(text(members, "id", "a rule of " + what), RULE_ID, what,
                "the identifier of a rule");
        String where = "the rule " + id + " of " + what;
        reject(members.keySet(), RULE_MEMBERS, where, "a rule");
        String severityToken = text(members, "severity", where);
        RuleSeverity severity = RuleSeverity.declared(severityToken).orElseThrow(
                () -> new RulePackException(where + " declares the severity " + severityToken
                        + "; a rule declares fatal or warning"));
        List<String> terms = new ArrayList<>();
        Json declared = members.get("terms");
        if (declared == null) {
            throw new RulePackException(where + " lists no terms");
        }
        for (Json term : declared.asArray("terms of " + where)) {
            terms.add(term.asString("a term of " + where));
        }
        Map<String, Json> bindings = new LinkedHashMap<>();
        Json bind = members.get("bind");
        if (bind != null) {
            bindings.putAll(bind.asObject("bind of " + where));
        }
        Json assertion = members.get("assert");
        if (assertion == null) {
            throw new RulePackException(where + " asserts nothing");
        }
        Json note = members.get("note");
        return new RuleDefinition(id, severity, text(members, "context", where), terms,
                assertion, bindings, text(members, "message", where), text(members, "source", where),
                note == null ? null : note.asString("note of " + where),
                readWarning(members.get("warn"), where));
    }

    private static RuleDefinition.Warning readWarning(Json warn, String where) {
        if (warn == null) {
            return null;
        }
        Map<String, Json> members = warn.asObject("the second assertion of " + where);
        reject(members.keySet(), WARNING_MEMBERS, where, "the second assertion of a rule");
        Json assertion = members.get("assert");
        if (assertion == null) {
            throw new RulePackException(where + " carries a second assertion that asserts nothing");
        }
        return new RuleDefinition.Warning(assertion,
                text(members, "message", "the second assertion of " + where));
    }

    /**
     * Reads the rule files a bundled manifest names and returns the pack with their rules
     * beside its own.
     *
     * @param manifest the manifest as its file writes it
     * @return the whole pack
     * @throws RulePackException if a file the manifest names is not in this build, or is
     *                           not an array of rules, or repeats a rule identifier
     */
    private static RulePack withFiles(RulePack manifest) {
        if (manifest.files().isEmpty()) {
            return manifest;
        }
        Set<String> ids = new LinkedHashSet<>();
        List<RuleDefinition> rules = new ArrayList<>();
        for (RuleDefinition declared : manifest.rules()) {
            ids.add(declared.id());
            rules.add(declared);
        }
        String directory = resourceDirectory(manifest.id(), manifest.version());
        for (String file : manifest.files()) {
            String what = "the rule file " + file + " of " + manifest.name();
            try (InputStream in = RulePacks.class.getResourceAsStream(directory + file)) {
                if (in == null) {
                    throw new RulePackException("the pack " + manifest.name() + " names "
                            + file + ", which is not in this build");
                }
                rules.addAll(readRules(Json.read(in, what), what, ids));
            } catch (IOException e) {
                throw new RulePackException(what + " could not be read: " + e.getMessage(), e);
            }
        }
        return new RulePack(manifest.id(), manifest.version(), manifest.verifiedAgainst(),
                manifest.description(), manifest.codeLists(), manifest.javaRules(),
                manifest.files(), rules);
    }

    private static void reject(Set<String> present, Set<String> known, String what, String format) {
        for (String member : present) {
            if (!known.contains(member)) {
                throw new RulePackException(what + " carries the member " + member
                        + ", which " + format + " does not define");
            }
        }
    }

    private static String check(String value, Pattern shape, String what, String which) {
        if (!shape.matcher(value).matches()) {
            throw new RulePackException(what + ": " + value + " is not " + which
                    + ", which is written " + shape.pattern());
        }
        return value;
    }

    private static String text(Map<String, Json> members, String name, String what) {
        Json value = members.get(name);
        if (value == null) {
            throw new RulePackException(what + " has no " + name);
        }
        return value.asString(name + " of " + what);
    }
}
