package de.bsnsoft.esj.xr;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The shape of the XR representation: which element name stands for which business term or
 * business group, which group it sits in, and in which order the elements of one group are
 * written.
 *
 * <p>The facts come from the XML schema of the KoSIT XRechnung visualization, and they come
 * from it once: {@code XrElementsSource} derives this table from the schema and a test of
 * this module derives it again on every build and compares it, so nothing here can drift
 * away from the schema, and no schema is parsed while an invoice is written. The table is
 * read from the classpath once and shared.
 *
 * <p>Seven groups of the XRechnung extension are recorded under the identifiers the
 * registry and the stylesheets use rather than the ones the schema writes; the generator
 * resolves them, and the reason is written down there.
 */
final class XrElements {

    /**
     * The name of the root element of the XR representation, and the name this table
     * records the elements at the root of the document under. It is no business term
     * identifier, so it cannot collide with one.
     */
    static final String ROOT = "invoice";

    /** The table, read from the classpath of this module. */
    private static final String RESOURCE = "xr-elements.tsv";

    private final Map<String, List<Element>> byContainer;

    private XrElements(Map<String, List<Element>> byContainer) {
        this.byContainer = byContainer;
    }

    /**
     * One element of the XR representation.
     *
     * @param id   the business term or business group the element stands for, as the
     *             registry knows it
     * @param name the name of the element
     * @param type the name of its type in the schema, which is the semantic data type in
     *            the spelling of the schema for a business term, and the name of a group
     *            type for a business group
     */
    record Element(String id, String name, String type) {

        /**
         * Checks that no member is {@code null}.
         *
         * @param id   the business term or business group the element stands for, as the
         *             registry knows it
         * @param name the name of the element
         * @param type the name of its type in the schema, which is the semantic data type in
         *            the spelling of the schema for a business term, and the name of a group
         *            type for a business group
         * @throws NullPointerException if a member is {@code null}
         */
        Element {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }

    /**
     * Returns the table, read once and shared. It ships in the same artefact as this class
     * and is checked on every build, so a failure to read it is a broken artefact rather
     * than a document a caller handed in.
     *
     * @return the shape of the XR representation
     */
    static XrElements table() {
        return Holder.TABLE;
    }

    /**
     * Returns the elements one group carries, in the order the schema writes them.
     *
     * @param containerId the business group, or {@link #ROOT} for the root of the document
     * @return the elements in schema order, empty for a group this table does not record
     * @throws NullPointerException if {@code containerId} is {@code null}
     */
    List<Element> children(String containerId) {
        Objects.requireNonNull(containerId, "containerId");
        return byContainer.getOrDefault(containerId, List.of());
    }

    /**
     * Returns every group this table records, in the order the walk of the schema reached
     * them, the root first.
     *
     * @return the identifiers of the groups, and {@link #ROOT}
     */
    List<String> containers() {
        return List.copyOf(byContainer.keySet());
    }

    private static XrElements load() {
        Map<String, List<Element>> byContainer = new LinkedHashMap<>();
        for (String line : read().split("\n")) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length != 4) {
                throw new IllegalStateException(
                        "a line of " + RESOURCE + " carries four fields, and one carries "
                                + fields.length);
            }
            byContainer.computeIfAbsent(fields[0], key -> new ArrayList<>())
                    .add(new Element(fields[1], fields[2], fields[3]));
        }
        Map<String, List<Element>> copy = new LinkedHashMap<>();
        byContainer.forEach((container, elements) -> copy.put(container, List.copyOf(elements)));
        return new XrElements(Collections.unmodifiableMap(copy));
    }

    private static String read() {
        try (InputStream in = XrElements.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Holds the table, which is read on first use and shared. */
    private static final class Holder {

        static final XrElements TABLE = load();

        private Holder() {
        }
    }
}
