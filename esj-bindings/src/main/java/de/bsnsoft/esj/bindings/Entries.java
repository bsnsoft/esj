package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.model.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the {@code terms} array of a binding table into the entries the reader compiles.
 *
 * <p>The table writes one term as one object: its identifier, its kind, whether the
 * syntax represents it at all, the XPath it is written at, the alternatives that are
 * written instead of it in some documents, the XPaths of its supplementary components
 * with the XPath each of them belongs to, and its flags. Nothing here interprets those
 * facts; it only reads them into the shape the trie is built from, and it refuses a
 * table that says something this reader cannot represent.
 */
final class Entries {

    private Entries() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the entries of one {@code terms} array.
     *
     * @param terms      the array, as {@link Json} read it
     * @param namespaces what each prefix of the table stands for
     * @param syntax     the syntax of the table, for the messages
     * @return the entries, in the order the table writes them
     * @throws BindingFormatException if the table says something this reader cannot
     *                                represent
     */
    static List<BindingTable.Entry> parse(Object terms,
                                          Map<String, String> namespaces,
                                          BindingSyntax syntax) {
        List<BindingTable.Entry> entries = new ArrayList<>();
        if (!(terms instanceof List<?> items)) {
            return entries;
        }
        for (Object item : items) {
            entries.add(entry(item, namespaces, syntax));
        }
        return entries;
    }

    private static BindingTable.Entry entry(Object term,
                                            Map<String, String> namespaces,
                                            BindingSyntax syntax) {
        String id = Json.text(term, "id");
        String kind = Json.text(term, "kind");
        if (id == null || kind == null) {
            throw new BindingFormatException("a term of the binding table of " + syntax
                    + " carries no identifier or no kind");
        }
        Set<String> flags = flags(term);
        List<BindingTable.Path> paths = new ArrayList<>();
        String main = Json.text(term, "xpath");
        if (main != null) {
            paths.add(path(main, flags, namespaces));
        }
        for (Object alternative : Json.list(term, "alternatives")) {
            String xpath = Json.text(alternative, "xpath");
            if (xpath == null) {
                throw new BindingFormatException("an alternative of " + id
                        + " carries no XPath");
            }
            paths.add(path(xpath, flags(alternative), namespaces));
        }
        boolean bound = Json.flag(term, "bound");
        if (bound == paths.isEmpty()) {
            throw new BindingFormatException("the binding table of " + syntax + " says that "
                    + id + (bound ? " is bound and gives it no XPath"
                    : " is not bound and gives it an XPath"));
        }
        return new BindingTable.Entry(id, kind, bound, components(term, paths, id),
                Json.text(term, "instanceElement"), flags);
    }

    private static BindingTable.Path path(String xpath,
                                          Set<String> flags,
                                          Map<String, String> namespaces) {
        String elements = Xpaths.withoutAttribute(xpath);
        return new BindingTable.Path(xpath, Xpaths.steps(elements, namespaces),
                Xpaths.attribute(xpath), List.of(), flags);
    }

    /**
     * Returns the paths with every supplementary component attached to the one it belongs
     * to. A component names its anchor, which is the XPath of the value it is a component
     * of, so a term whose scheme is written differently in its alternative keeps the two
     * apart.
     */
    private static List<BindingTable.Path> components(Object term,
                                                      List<BindingTable.Path> paths,
                                                      String id) {
        List<List<BindingTable.ComponentBinding>> perPath = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            perPath.add(new ArrayList<>());
        }
        for (Object component : Json.list(term, "components")) {
            String role = Json.text(component, "role");
            String xpath = Json.text(component, "xpath");
            String anchor = Json.text(component, "anchor");
            if (role == null || xpath == null || anchor == null) {
                throw new BindingFormatException("a supplementary component of " + id
                        + " carries no role, no XPath or no anchor");
            }
            Component.Role member = Component.Role.findByMember(role).orElseThrow(
                    () -> new BindingFormatException("a supplementary component of " + id
                            + " names the member " + role + ", which no value object has"));
            int at = -1;
            for (int i = 0; i < paths.size(); i++) {
                if (paths.get(i).xpath().equals(anchor)) {
                    at = i;
                }
            }
            if (at < 0) {
                throw new BindingFormatException("a supplementary component of " + id
                        + " is anchored at an XPath the term is not bound to");
            }
            perPath.get(at).add(new BindingTable.ComponentBinding(member, xpath));
        }
        List<BindingTable.Path> completed = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            BindingTable.Path path = paths.get(i);
            completed.add(new BindingTable.Path(path.xpath(), path.steps(), path.attribute(),
                    perPath.get(i), path.flags()));
        }
        return completed;
    }

    private static Set<String> flags(Object owner) {
        Set<String> flags = new LinkedHashSet<>();
        for (Object flag : Json.list(owner, "flags")) {
            if (flag instanceof String text) {
                flags.add(text);
            }
        }
        return flags;
    }
}
