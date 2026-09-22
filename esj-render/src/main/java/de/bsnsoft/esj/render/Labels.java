package de.bsnsoft.esj.render;

import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import de.bsnsoft.esj.xr.XmlFrontDoor;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

/**
 * What the PDF rendering calls a business term, in the language it is rendered in.
 *
 * <h2>Where the words come from</h2>
 *
 * <p><b>English</b> is the term registry. Every business term and every business group of
 * EN 16931-1 has a name there, and so does every supplementary component; those names are
 * facts of the semantic model, they are in the registry already, and inventing a second
 * set of English words for the same terms would only be a second opinion to keep in step.
 *
 * <p><b>German</b> is the localization of the KoSIT XRechnung visualization that this
 * module vendors for its HTML rendering, {@code kosit/l10n/de.xml}. Each entry there may
 * carry an {@code id} attribute naming the business term it labels, and that attribute is
 * the whole of the mapping: no name matching, no guessing. Where an entry names a term,
 * its text is the German label, so that the two renderings of this module call a term the
 * same thing. Where no entry names it, the English name of the registry stands in its
 * place.
 *
 * <h2>The gaps, and why they are gaps</h2>
 *
 * <p>The localization labels the fields of a layout, not the terms of a model, so it
 * carries no entry for a term the layout of the visualization never prints on its own. Of
 * the 208 terms of the core registry and the XRechnung extension, 185 are named by an
 * entry and 23 are not: the groups BG-2, BG-5, BG-6, BG-8, BG-9, BG-12, BG-14, BG-15,
 * BG-16, BG-24, BG-25, BG-30 and the eight {@code BG-DEX-*} groups of the extension, and
 * the three business terms BT-128, BT-160 and BT-161. Those show their English registry
 * name in a German rendering, and a reader sees that as the exception it is. Most of them
 * are groups, and a group's name is a heading rather than a label: the headings of this
 * layout are written in both languages by the renderer itself and do not come from here.
 *
 * <p>Two entries of the localization are read although they name no term: the words for a
 * figure without VAT and for one with it, under the keys {@code _net} and {@code _gross}.
 * The layout of the visualization keeps its three {@code Gesamtsumme} apart by the table
 * each of them stands in; a block of definitions cannot, and those two words are what that
 * layout itself writes beside them. {@link #netOrGross(String)} hands them out.
 *
 * <p>Three entries of the localization name a term that another entry already names —
 * BG-4 three times and BG-7 twice, once as a heading and once as the caption of a
 * sub-table. The first entry in document order wins, deterministically, so a rendering
 * does not depend on which of them was read last.
 *
 * <p>Instances are immutable and safe to share.
 */
final class Labels {

    /** The vendored localization of the visualization, the German side. */
    private static final String GERMAN_RESOURCE =
            "/de/bsnsoft/esj/render/kosit/l10n/de.xml";

    /** The attribute of a localization entry that names the business term it labels. */
    private static final QName ID = new QName("id");

    /** The attribute every localization entry is keyed by. */
    private static final QName KEY = new QName("key");

    /** The key of the localization entry that says "without VAT". */
    private static final String NET_KEY = "_net";

    /** The key of the localization entry that says "with VAT". */
    private static final String GROSS_KEY = "_gross";

    /**
     * The terms whose figure carries VAT and the ones whose figure does not, for the
     * layout to say so where two of them would otherwise be called the same thing. Only
     * the totals are here: they are the figures the localization of the visualization
     * gives one and the same name, because its own layout keeps them apart by the table
     * they stand in and a block of definitions cannot.
     */
    private static final Map<String, Boolean> GROSS = Map.of(
            "BT-109", Boolean.FALSE,
            "BT-116", Boolean.FALSE,
            "BT-112", Boolean.TRUE);

    /** The suffix the localization appends to a term identifier for each component. */
    private static final Map<Component.Role, String> COMPONENT_SUFFIX = Map.of(
            Component.Role.SCHEME, "_scheme",
            Component.Role.SCHEME_VERSION, "_scheme_version",
            Component.Role.MIME_CODE, "_mime_code",
            Component.Role.FILENAME, "_filename");

    /** The English words for a component of a term the registry does not know. */
    private static final Map<Component.Role, String> ENGLISH_COMPONENT = Map.of(
            Component.Role.SCHEME, "identification scheme",
            Component.Role.SCHEME_VERSION, "identification scheme version",
            Component.Role.MIME_CODE, "media type",
            Component.Role.FILENAME, "file name");

    private final Registry registry;
    private final Map<String, String> german;

    private Labels(Registry registry, Map<String, String> german) {
        this.registry = registry;
        this.german = german;
    }

    /**
     * Returns the labels of a language.
     *
     * @param registry the registry the English names come from
     * @param language the language
     * @return the labels
     * @throws NullPointerException if an argument is {@code null}
     */
    static Labels of(Registry registry, RenderLanguage language) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(language, "language");
        return new Labels(registry,
                language == RenderLanguage.GERMAN ? Localization.GERMAN : Map.of());
    }

    /**
     * Returns the label of a business term or business group.
     *
     * @param termId the identifier, for example {@code BT-131}
     * @return the label, or the identifier itself where nothing names the term
     */
    String of(String termId) {
        Objects.requireNonNull(termId, "termId");
        String label = german.get(termId);
        if (label != null) {
            return label;
        }
        return registry.term(termId).map(Term::name).orElse(termId);
    }

    /**
     * Returns the word the localization uses for a figure without VAT or with it, where
     * the term is one of the totals and the rendering has a word for it.
     *
     * <p>The localization of the visualization calls BT-109, BT-112 and BT-116 all the
     * same thing, and its own layout keeps them apart by the table each of them stands
     * in. A block that lists two of them under one another cannot, and the identifier of
     * the term is a poor answer for a reader who does not know the numbering. The file
     * already carries the two words that settle it, under the keys {@code _net} and
     * {@code _gross}, so they are the words used.
     *
     * @param termId the identifier of the term
     * @return the word, or empty where the language has none or the term is not a total
     */
    Optional<String> netOrGross(String termId) {
        Objects.requireNonNull(termId, "termId");
        Boolean gross = GROSS.get(termId);
        if (gross == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(german.get(gross ? GROSS_KEY : NET_KEY));
    }

    /**
     * Returns the label of a supplementary component of a term.
     *
     * @param termId the identifier of the term the component belongs to
     * @param role   the component
     * @return the label
     * @throws NullPointerException if an argument is {@code null}
     */
    String ofComponent(String termId, Component.Role role) {
        Objects.requireNonNull(termId, "termId");
        Objects.requireNonNull(role, "role");
        String label = german.get(termId + COMPONENT_SUFFIX.get(role));
        if (label != null) {
            return label;
        }
        Optional<Component> component = registry.term(termId)
                .flatMap(term -> term.component(role));
        return component.map(Component::name).orElseGet(() -> ENGLISH_COMPONENT.get(role));
    }

    /**
     * The German localization, read once out of the vendored file. It is a fixed resource
     * of this module and never changes while the module runs, so reading it a second time
     * would only be work.
     */
    private static final class Localization {

        private static final Map<String, String> GERMAN = read();

        private Localization() {
            throw new AssertionError("no instances");
        }

        private static Map<String, String> read() {
            byte[] bytes = bytes();
            Map<String, String> labels = new LinkedHashMap<>();
            try {
                XdmNode root = XmlFrontDoor.parse(bytes);
                XdmValue entries = XmlFrontDoor.processor().newXPathCompiler()
                        .evaluate("/*/*", root);
                for (XdmItem item : entries) {
                    XdmNode entry = (XdmNode) item;
                    String id = entry.getAttributeValue(ID);
                    if (id != null && !labels.containsKey(id)) {
                        labels.put(id, collapse(entry.getStringValue()));
                    }
                    String key = entry.getAttributeValue(KEY);
                    if ((NET_KEY.equals(key) || GROSS_KEY.equals(key))
                            && !labels.containsKey(key)) {
                        labels.put(key, collapse(entry.getStringValue()));
                    }
                }
            } catch (SaxonApiException e) {
                throw new RenderException(
                        "the vendored localization of the visualization could not be read", e);
            }
            if (labels.isEmpty()) {
                throw new RenderException(
                        "the vendored localization of the visualization names no term");
            }
            return Map.copyOf(labels);
        }

        private static byte[] bytes() {
            try (InputStream in = Labels.class.getResourceAsStream(GERMAN_RESOURCE)) {
                if (in == null) {
                    throw new RenderException(
                            "the resource " + GERMAN_RESOURCE + " is not on the classpath");
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** Collapses the whitespace an entry wrapped over two lines carries. */
        private static String collapse(String text) {
            return text.replaceAll("\\s+", " ").strip();
        }
    }
}
