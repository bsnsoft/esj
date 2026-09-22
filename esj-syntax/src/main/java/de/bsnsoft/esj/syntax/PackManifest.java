package de.bsnsoft.esj.syntax;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Turns the {@code pack.json} of a validation pack into a {@link Pack}.
 *
 * <p>The manifest is read strictly. A member that is missing, a member of the wrong kind
 * and a format version this module does not know are each a {@link PackException} rather
 * than a pack that runs half of its artefacts: a pack decides which official rules were
 * applied to an invoice, and a report that names a pack has to mean the whole of it.
 */
final class PackManifest {

    /** The name of the manifest inside a pack directory. */
    static final String FILE = "pack.json";

    /** The format the manifests of this repository are written in. */
    private static final String FORMAT = "esj-validation-pack";

    /** The version of that format this module reads. */
    private static final String FORMAT_VERSION = "1";

    private PackManifest() {
        throw new AssertionError("no instances");
    }

    /**
     * Reads a manifest.
     *
     * @param origin   where the files of this pack come from, as a name that is the
     *                 caller's and never the pack's own account of itself: the class path,
     *                 or the canonical directory a caller pointed at. Two packs that call
     *                 themselves the same thing are two packs, and the compiled artefacts
     *                 of one must not be handed to the other
     * @param source   whether the files were carried with this build or supplied by the
     *                 caller, which a report says because the identity in the manifest
     *                 cannot distinguish the two
     * @param label    where the manifest was read from, for a message
     * @param manifest the bytes of {@code pack.json}
     * @param files    where the other files of the pack are read from
     * @return the pack
     * @throws PackException if the manifest is not one this module reads
     */
    static Pack read(String origin, PackSource source, String label, byte[] manifest,
                     PackFiles files) {
        String where = label + "/" + FILE;
        Map<?, ?> root = asObject(PackJson.read(manifest, where), where);
        String format = string(root, "format", where);
        String formatVersion = string(root, "formatVersion", where);
        if (!FORMAT.equals(format) || !FORMAT_VERSION.equals(formatVersion)) {
            throw new PackException(where + " is written in " + format + " version "
                    + formatVersion + ", and this module reads " + FORMAT + " version "
                    + FORMAT_VERSION);
        }
        List<PackComponent> components = new ArrayList<>();
        for (Object element : array(root, "components", where)) {
            components.add(component(asObject(element, where), where));
        }
        List<PackLevels> levels = new ArrayList<>();
        for (Object element : optionalArray(root, "levels", where)) {
            levels.add(levels(asObject(element, where), where));
        }
        Set<String> baseProfiles = new LinkedHashSet<>();
        for (Object element : optionalArray(root, "baseProfiles", where)) {
            baseProfiles.add(asString(element, where));
        }
        Map<String, String> inventory = new TreeMap<>();
        for (Object element : array(root, "files", where)) {
            Map<?, ?> file = asObject(element, where);
            inventory.put(string(file, "path", where), string(file, "sha256", where));
        }
        String id = string(root, "id", where);
        String version = string(root, "version", where);
        String release = string(root, "release", where);
        return new Pack(origin,
                source,
                id + "/" + version + "/" + release,
                id,
                version,
                release,
                string(root, "title", where),
                string(root, "retrieved", where),
                components,
                levels,
                baseProfiles,
                inventory,
                files);
    }

    /**
     * Reads one level table.
     *
     * <p>A level that is not one of the three a report knows is refused rather than
     * passed over: a table that says something this module cannot act on would otherwise
     * change a verdict without anybody being able to see how.
     */
    private static PackLevels levels(Map<?, ?> table, String where) {
        Map<?, ?> appliesTo = asObject(table.get("appliesTo"), where);
        Set<String> syntaxes = new LinkedHashSet<>();
        for (Object syntax : array(appliesTo, "syntax", where)) {
            syntaxes.add(asString(syntax, where));
        }
        List<String> profiles = new ArrayList<>();
        for (Object profile : array(appliesTo, "profile", where)) {
            profiles.add(asString(profile, where));
        }
        SortedMap<String, Severity> levels = new TreeMap<>();
        Map<?, ?> given = asObject(table.get("level"), where);
        for (Map.Entry<?, ?> member : given.entrySet()) {
            String code = asString(member.getKey(), where);
            String level = asString(member.getValue(), where);
            Severity severity = Severity.ofFlag(level);
            if (!severity.token().equals(level)) {
                throw new PackException(where + " levels " + code + " as " + level
                        + ", and a level is one of fatal, warning and information");
            }
            levels.put(code, severity);
        }
        return new PackLevels(string(table, "name", where), syntaxes, profiles, levels,
                string(table, "source", where));
    }

    private static PackComponent component(Map<?, ?> component, String where) {
        Map<?, ?> appliesTo = asObject(component.get("appliesTo"), where);
        Set<String> syntaxes = new LinkedHashSet<>();
        for (Object syntax : array(appliesTo, "syntax", where)) {
            syntaxes.add(asString(syntax, where));
        }
        List<String> profiles = new ArrayList<>();
        for (Object profile : array(appliesTo, "profile", where)) {
            profiles.add(asString(profile, where));
        }
        Map<String, String> entries = new LinkedHashMap<>();
        Map<?, ?> entry = asObject(component.get("entry"), where);
        for (Map.Entry<?, ?> member : entry.entrySet()) {
            entries.put(asString(member.getKey(), where), asString(member.getValue(), where));
        }
        List<String> files = new ArrayList<>();
        for (Object file : array(component, "files", where)) {
            files.add(asString(file, where));
        }
        return new PackComponent(string(component, "name", where),
                ComponentRole.of(string(component, "role", where)),
                syntaxes,
                profiles,
                entries,
                files,
                string(component, "license", where),
                string(component, "licenseFile", where),
                string(component, "source", where),
                string(component, "obtainedFrom", where),
                Boolean.TRUE.equals(component.get("unmodified")));
    }

    private static Map<?, ?> asObject(Object value, String where) {
        if (value instanceof Map<?, ?> object) {
            return object;
        }
        throw new PackException(where + " has a member that is not a JSON object");
    }

    private static String asString(Object value, String where) {
        if (value instanceof String text) {
            return text;
        }
        throw new PackException(where + " has a member that is not a JSON string");
    }

    private static String string(Map<?, ?> object, String name, String where) {
        Object value = object.get(name);
        if (value == null) {
            throw new PackException(where + " has no member " + name);
        }
        return asString(value, where);
    }

    /**
     * Returns an array member that a manifest need not have.
     *
     * <p>A pack without a level table is a pack whose profiles level nothing beyond what
     * the artefacts flag, which is the ordinary case and not an incomplete manifest, and a
     * pack without {@code baseProfiles} is one that names no specification identifier as
     * carrying no core invoice usage specification. A member that is there and is not an
     * array is still a manifest this module refuses.
     */
    private static List<?> optionalArray(Map<?, ?> object, String name, String where) {
        return object.containsKey(name) ? array(object, name, where) : List.of();
    }

    private static List<?> array(Map<?, ?> object, String name, String where) {
        Object value = object.get(name);
        if (value instanceof List<?> elements) {
            return elements;
        }
        throw new PackException(where + " has no member " + name + " that is a JSON array");
    }
}
