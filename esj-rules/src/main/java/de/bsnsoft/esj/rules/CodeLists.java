package de.bsnsoft.esj.rules;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The code list snapshots one pack was compiled with, keyed by the identifier a rule names
 * a list by.
 *
 * <p>Which snapshot that is, is decided by the pack manifest and by nothing else: a rule
 * writes {@code {"inList": [ {"value": "/BT-5"}, "iso-4217" ]}} and the manifest says that
 * {@code iso-4217} in this pack means the file of one particular day. Two runs of the same
 * pack version over the same document therefore give the same verdict however far apart
 * they are, which is the only way a validation report keeps its meaning in an archive.
 *
 * <p>A pack that names a list it has no snapshot for does not compile. That is deliberate
 * and it is the reason this class refuses rather than returns an empty list: a membership
 * test against a list nobody loaded would pass every code, and a rule that silently passes
 * everything is worse than a pack that will not start.
 */
public final class CodeLists {

    /** Where a bundled pack keeps its snapshots, below the resource directory of a pack. */
    private static final String CODELISTS = "codelists/";

    private final Map<String, CodeList> byId;

    private CodeLists(Map<String, CodeList> byId) {
        this.byId = byId;
    }

    /**
     * Collects snapshots that are already in hand.
     *
     * @param lists the snapshots; two of the same identifier are a mistake and are refused
     * @return the collection
     * @throws RulePackException    if two snapshots carry the same identifier
     * @throws NullPointerException if {@code lists} or an element is {@code null}
     */
    public static CodeLists of(Collection<CodeList> lists) {
        Objects.requireNonNull(lists, "lists");
        Map<String, CodeList> byId = new LinkedHashMap<>();
        for (CodeList list : lists) {
            if (byId.put(list.listId(), list) != null) {
                throw new RulePackException("two snapshots carry the identifier " + list.listId());
            }
        }
        return new CodeLists(Collections.unmodifiableMap(byId));
    }

    /**
     * Collects no snapshots at all, for a pack whose rules ask about none.
     *
     * @return the empty collection
     */
    public static CodeLists empty() {
        return new CodeLists(Map.of());
    }

    /**
     * Loads the snapshots a pack manifest names, from the rule directory that travels in
     * this module's jar.
     *
     * @param pack the pack whose manifest names the snapshots
     * @return the snapshots, keyed by list identifier
     * @throws RulePackException    if a snapshot the manifest names is not in the jar or is
     *                              not a snapshot
     * @throws NullPointerException if {@code pack} is {@code null}
     */
    public static CodeLists bundled(RulePack pack) {
        Objects.requireNonNull(pack, "pack");
        List<CodeList> lists = new ArrayList<>();
        for (Map.Entry<String, String> named : pack.codeLists().entrySet()) {
            String resource = RulePacks.resourceDirectory(pack.id(), pack.version())
                    + CODELISTS + named.getKey() + "/" + named.getValue() + ".json";
            try (InputStream in = CodeLists.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new RulePackException("the pack " + pack.id() + " " + pack.version()
                            + " names the snapshot " + named.getKey() + " of " + named.getValue()
                            + ", which is not in this build");
                }
                lists.add(read(in, named.getKey() + " of " + named.getValue()));
            } catch (IOException e) {
                throw new RulePackException("the snapshot " + resource + " could not be read: "
                        + e.getMessage(), e);
            }
        }
        return of(lists);
    }

    /**
     * Reads one snapshot.
     *
     * @param in   the bytes of the snapshot file, UTF-8; the caller owns the stream
     * @param what what is being read, for the message of a failure
     * @return the snapshot
     * @throws RulePackException if the bytes are not a snapshot
     */
    public static CodeList read(InputStream in, String what) {
        Json json = Json.read(in, "the code list snapshot " + what);
        Map<String, Json> members = json.asObject("the code list snapshot " + what);
        Set<String> known = Set.of("listId", "name", "publisher", "source", "retrieved", "terms", "entries");
        for (String member : members.keySet()) {
            if (!known.contains(member)) {
                throw new RulePackException("the code list snapshot " + what
                        + " carries the member " + member + ", which this format does not define");
            }
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (Json entry : required(members, "entries", what).asArray("entries of " + what)) {
            Map<String, Json> fields = entry.asObject("an entry of " + what);
            String value = required(fields, "value", what).asString("the value of an entry of " + what);
            String name = fields.containsKey("name")
                    ? fields.get("name").asString("the name of an entry of " + what)
                    : "";
            if (entries.put(value, name) != null) {
                throw new RulePackException("the code list snapshot " + what
                        + " carries the code " + value + " twice");
            }
        }
        return new CodeList(
                required(members, "listId", what).asString("listId of " + what),
                required(members, "name", what).asString("name of " + what),
                required(members, "publisher", what).asString("publisher of " + what),
                required(members, "source", what).asString("source of " + what),
                required(members, "retrieved", what).asString("retrieved of " + what),
                entries);
    }

    private static Json required(Map<String, Json> members, String name, String what) {
        Json value = members.get(name);
        if (value == null) {
            throw new RulePackException("the code list snapshot " + what + " has no " + name);
        }
        return value;
    }

    /**
     * Returns the snapshot a rule names, if this collection has it.
     *
     * @param listId the identifier a rule names the list by
     * @return the snapshot, or an empty optional
     */
    public Optional<CodeList> list(String listId) {
        return Optional.ofNullable(byId.get(listId));
    }

    /**
     * Returns the snapshot a rule names, or refuses.
     *
     * @param listId the identifier a rule names the list by
     * @return the snapshot
     * @throws RulePackException if this collection has no snapshot of that identifier
     */
    public CodeList require(String listId) {
        CodeList list = byId.get(listId);
        if (list == null) {
            throw new RulePackException("no snapshot of the code list " + listId
                    + " was loaded; a membership test against a list nobody loaded would pass"
                    + " every code, so the pack does not compile");
        }
        return list;
    }

    /**
     * Returns the identifiers of the snapshots in this collection.
     *
     * @return the identifiers, in the order the snapshots were collected
     */
    public Set<String> listIds() {
        return byId.keySet();
    }
}
