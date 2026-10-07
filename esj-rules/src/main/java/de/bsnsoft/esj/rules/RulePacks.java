package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.validate.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            "$schema", "id", "version", "edition", "verifiedAgainst", "description",
            "codeLists", "javaRules", "files", "shares", "rules");

    /** The members one entry of {@code javaRules} may carry. */
    private static final Set<String> JAVA_RULE_MEMBERS = Set.of("class", "oracle");

    /** The members one entry of {@code shares} may carry. */
    private static final Set<String> SHARE_MEMBERS = Set.of("pack", "version", "file", "oracle", "rules");

    /** The members one entry of {@code codeLists} may carry where it is written out. */
    private static final Set<String> SNAPSHOT_MEMBERS = Set.of("day", "from");

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
            "id", "severity", "oracle", "context", "terms", "assert", "bind", "message",
            "source", "note", "warn", "undecided");

    /** What the reference to another pack in {@code from} and in {@code shares} looks like. */
    private static final Pattern PACK_REFERENCE = Pattern.compile(
            "[a-z][a-z0-9-]*/[0-9A-Za-z][0-9A-Za-z.-]*");

    /** What the second assertion of a rule is written with. */
    private static final Set<String> WARNING_MEMBERS = Set.of("assert", "message");

    /** What the case in which a rule is not decided is written with. */
    private static final Set<String> NOT_DECIDED_MEMBERS = Set.of("when", "message");

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
        Map<String, String> days = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        readCodeLists(members, what, id + "/" + version, days, sources);
        Json verified = members.get("verifiedAgainst");
        return new RulePack(id, version,
                text(members, "edition", what),
                verified == null
                        ? Optional.empty()
                        : Optional.of(verified.asString("verifiedAgainst of " + what)),
                text(members, "description", what),
                days, sources,
                readJavaRules(members, what),
                readFiles(members, what),
                readShares(members, what),
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

    private static void readCodeLists(Map<String, Json> members, String what, String self,
                                      Map<String, String> days, Map<String, String> sources) {
        Json lists = members.get("codeLists");
        if (lists == null) {
            return;
        }
        for (Map.Entry<String, Json> entry : lists.asObject("codeLists of " + what).entrySet()) {
            String listId = entry.getKey();
            String where = "the snapshot of " + listId + " in " + what;
            if (entry.getValue() instanceof Json.Obj written) {
                Map<String, Json> snapshot = written.asObject(where);
                reject(snapshot.keySet(), SNAPSHOT_MEMBERS, where, "a code list snapshot reference");
                days.put(listId, text(snapshot, "day", where));
                sources.put(listId, check(text(snapshot, "from", where), PACK_REFERENCE, where,
                        "the pack a snapshot is read from"));
                continue;
            }
            days.put(listId, entry.getValue().asString("the snapshot day of " + listId + " in " + what));
            sources.put(listId, self);
        }
    }

    private static List<RulePack.Share> readShares(Map<String, Json> members, String what) {
        Json shares = members.get("shares");
        if (shares == null) {
            return List.of();
        }
        List<RulePack.Share> read = new ArrayList<>();
        for (Json share : shares.asArray("shares of " + what)) {
            Map<String, Json> fields = share.asObject("a share of " + what);
            String where = "a share of " + what;
            reject(fields.keySet(), SHARE_MEMBERS, where, "a share");
            String file = text(fields, "file", where);
            if (!FILE_PATH.matcher(file).matches()) {
                throw new RulePackException(where + " names the rule file " + file
                        + ", which is not a path of the form rules/<name>.json below a manifest");
            }
            Set<String> ids = new LinkedHashSet<>();
            Json named = fields.get("rules");
            if (named == null) {
                throw new RulePackException(where + " names no rule; a share that took a whole"
                        + " file would take whatever a later release of that file carries");
            }
            for (Json rule : named.asArray("the rules of " + where)) {
                if (!ids.add(rule.asString("a rule identifier of " + where))) {
                    throw new RulePackException(where + " names a rule twice");
                }
            }
            read.add(new RulePack.Share(
                    check(text(fields, "pack", where), PACK_ID, where, "the identifier of a pack"),
                    check(text(fields, "version", where), PACK_VERSION, where, "the version of a pack"),
                    file, oracle(text(fields, "oracle", where), where), List.copyOf(ids)));
        }
        return List.copyOf(read);
    }

    private static RuleOracle oracle(String token, String where) {
        return RuleOracle.declared(token).orElseThrow(() -> new RulePackException(
                where + " declares the oracle " + token
                        + "; a rule rests on an artefact, on a downgrade or on cases"));
    }

    private static List<RulePack.JavaRuleRef> readJavaRules(Map<String, Json> members, String what) {
        Json classes = members.get("javaRules");
        if (classes == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        List<RulePack.JavaRuleRef> read = new ArrayList<>();
        for (Json entry : classes.asArray("javaRules of " + what)) {
            String where = "a Java rule of " + what;
            Map<String, Json> fields = entry.asObject(where);
            reject(fields.keySet(), JAVA_RULE_MEMBERS, where, "a Java rule");
            String className = text(fields, "class", where);
            if (!names.add(className)) {
                throw new RulePackException(what + " names the Java rule " + className + " twice");
            }
            read.add(new RulePack.JavaRuleRef(className, oracle(text(fields, "oracle", where), where)));
        }
        return List.copyOf(read);
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
        Severity severity = RuleLevel.declared(severityToken).orElseThrow(
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
        return new RuleDefinition(id, severity, oracle(text(members, "oracle", where), where),
                text(members, "context", where), terms,
                assertion, bindings, text(members, "message", where), text(members, "source", where),
                note == null ? null : note.asString("note of " + where),
                readWarning(members.get("warn"), where),
                readNotDecided(members.get("undecided"), where));
    }

    private static RuleDefinition.NotDecided readNotDecided(Json undecided, String where) {
        if (undecided == null) {
            return null;
        }
        String what = "the case in which " + where + " is not decided";
        Map<String, Json> members = undecided.asObject(what);
        reject(members.keySet(), NOT_DECIDED_MEMBERS, where, "the case in which a rule is not decided");
        Json condition = members.get("when");
        if (condition == null) {
            throw new RulePackException(what + " names no condition");
        }
        return new RuleDefinition.NotDecided(condition, text(members, "message", what));
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
        if (manifest.files().isEmpty() && manifest.shares().isEmpty()) {
            return manifest;
        }
        Set<String> ids = new LinkedHashSet<>();
        List<RuleDefinition> rules = new ArrayList<>();
        for (RuleDefinition declared : manifest.rules()) {
            ids.add(declared.id());
            rules.add(declared);
        }
        for (RulePack.Share share : manifest.shares()) {
            rules.addAll(shared(manifest, share, ids));
        }
        String directory = resourceDirectory(manifest.id(), manifest.version());
        for (String file : manifest.files()) {
            String what = "the rule file " + file + " of " + manifest.name();
            rules.addAll(readRules(fileOf(directory + file, manifest.name(), file, what), what, ids));
        }
        return new RulePack(manifest.id(), manifest.version(), manifest.edition(),
                manifest.verifiedAgainst(), manifest.description(), manifest.codeLists(),
                manifest.codeListSources(), manifest.javaRules(), manifest.files(),
                manifest.shares(), rules);
    }

    /**
     * Reads the rules one share names out of the rule file of the pack it names, and gives
     * each of them the oracle of the share rather than the one it carries in its own pack.
     */
    private static List<RuleDefinition> shared(RulePack manifest, RulePack.Share share,
                                               Set<String> ids) {
        String what = "the shared rule file " + share.file() + " of " + share.name()
                + ", taken over by " + manifest.name();
        String resource = resourceDirectory(share.pack(), share.version()) + share.file();
        Map<String, RuleDefinition> byId = new LinkedHashMap<>();
        for (RuleDefinition rule : readRules(fileOf(resource, share.name(), share.file(), what),
                what, new LinkedHashSet<>())) {
            byId.put(rule.id(), rule);
        }
        List<RuleDefinition> taken = new ArrayList<>();
        for (String id : share.rules()) {
            RuleDefinition rule = byId.get(id);
            if (rule == null) {
                throw new RulePackException(manifest.name() + " takes the rule " + id
                        + " over from " + share.name() + ", and " + share.file()
                        + " of that pack does not carry it");
            }
            if (!ids.add(id)) {
                throw new RulePackException(manifest.name() + " carries the rule " + id + " twice");
            }
            taken.add(rule.withOracle(share.oracle()));
        }
        return taken;
    }

    private static Json fileOf(String resource, String packName, String file, String what) {
        try (InputStream in = RulePacks.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new RulePackException("the pack " + packName + " names " + file
                        + ", which is not in this build");
            }
            return Json.read(in, what);
        } catch (IOException e) {
            throw new RulePackException(what + " could not be read: " + e.getMessage(), e);
        }
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
