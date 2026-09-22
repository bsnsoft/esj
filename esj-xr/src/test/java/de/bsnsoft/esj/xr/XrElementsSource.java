package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * Derives the element table of the XR representation from the vendored XRechnung semantic
 * model schema. It is the generator behind
 * {@code de/bsnsoft/esj/xr/xr-elements.tsv}, and it runs in the test source
 * set because generating that file is the only thing it is for: no invoice is written
 * through this class, and no XML schema is parsed while one is.
 *
 * <p>The schema gives four facts per element: the name of the element, the identifier of
 * the business term or business group it stands for, the name of its type, and its
 * position in the sequence of its enclosing type. The walk starts at the {@code invoice}
 * element and follows the types, so a type that two elements share — the invoice line and
 * the sub invoice line of the XRechnung extension share one — is walked once per
 * enclosing group and not once per type.
 *
 * <h2>The groups the schema does not name</h2>
 *
 * <p>Seven groups of the XRechnung extension carry no identifier of their own in the
 * schema. The sub invoice line reuses the type of the invoice line, so the groups inside
 * it are spelled with the identifiers of the invoice line groups — {@code BG-26} and its
 * siblings — while the extension registry and the stylesheets call the same positions
 * {@code BG-DEX-05} and its siblings. The generator resolves them rather than listing
 * them: where the registry records no parent chain that puts the identifier of the schema
 * under the group being walked, it looks for a group the registry does record there whose
 * reused business terms are exactly the business terms of the schema type. That
 * correspondence is unique for every one of the seven, and an ambiguous one ends the
 * generation instead of guessing.
 */
final class XrElementsSource {

    /** The namespace of XML Schema, the one namespace this generator reads elements of. */
    private static final String XS = "http://www.w3.org/2001/XMLSchema";

    /** The prefix the schema writes before its own type names. */
    private static final String XR_PREFIX = "xr:";

    /** The {@code name} attribute of a type or of an element. */
    private static final QName NAME = new QName("name");

    /** The {@code type} attribute of an element. */
    private static final QName TYPE = new QName("type");

    private final Registry registry;
    private final Map<String, List<SchemaElement>> types = new LinkedHashMap<>();
    private final Map<String, List<XrElements.Element>> containers = new LinkedHashMap<>();

    private XrElementsSource(Registry registry) {
        this.registry = registry;
    }

    /**
     * One element of the schema: what it is called, which term it stands for and which
     * type it carries.
     */
    private record SchemaElement(String id, String name, String type) {
    }

    /**
     * Derives the table.
     *
     * @param schema   the bytes of {@code xrechnung-semantic-model.xsd}
     * @param registry the registry the identifiers are resolved against, which is the core
     *                 model combined with the XRechnung extension
     * @return the content of {@code xr-elements.tsv}, ending in a line separator
     */
    static String derive(byte[] schema, Registry registry) {
        XrElementsSource source = new XrElementsSource(registry);
        XdmNode root = XmlFrontDoor.rootElement(XmlFrontDoor.parse(schema));
        for (XdmNode type : children(root, "complexType")) {
            String name = type.getAttributeValue(NAME);
            source.types.put(name, source.sequence(type));
        }
        XdmNode invoice = children(root, "element").stream()
                .filter(element -> XrElements.ROOT.equals(
                        element.getAttributeValue(NAME)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the schema carries no element named " + XrElements.ROOT));
        XdmNode inline = children(invoice, "complexType").get(0);
        source.container(XrElements.ROOT, List.of(), source.sequence(inline));
        return source.text(schema);
    }

    /** Reads the elements of the {@code xs:sequence} of one type, in schema order. */
    private List<SchemaElement> sequence(XdmNode type) {
        List<SchemaElement> elements = new ArrayList<>();
        for (XdmNode sequence : children(type, "sequence")) {
            for (XdmNode element : children(sequence, "element")) {
                elements.add(new SchemaElement(appinfo(element),
                        element.getAttributeValue(NAME),
                        local(element.getAttributeValue(TYPE))));
            }
        }
        return elements;
    }

    /**
     * Records one container and walks the groups inside it. A container that is already
     * recorded is left alone, which is what ends the walk at the sub invoice line: its
     * type is the type of the invoice line, and its own chain collapses onto itself.
     */
    private void container(String containerId, List<String> chain, List<SchemaElement> elements) {
        if (containers.containsKey(containerId)) {
            return;
        }
        List<XrElements.Element> resolved = new ArrayList<>();
        for (SchemaElement element : elements) {
            resolved.add(new XrElements.Element(id(containerId, chain, element),
                    element.name(), element.type()));
        }
        containers.put(containerId, List.copyOf(resolved));
        for (int i = 0; i < elements.size(); i++) {
            String id = resolved.get(i).id();
            if (registry.term(id).filter(Term::isGroup).isEmpty()) {
                continue;
            }
            List<String> continuation = XrPlacement.continuation(registry, chain, id)
                    .orElseThrow(() -> new IllegalStateException(
                            "the registry records no parent chain that puts " + id
                                    + " under " + containerId));
            container(id, XrPlacement.resolved(continuation),
                    types.getOrDefault(elements.get(i).type(), List.of()));
        }
    }

    /**
     * Returns the identifier the registry knows an element by inside one container: the
     * one the schema writes, or the corresponding group of the extension where the schema
     * reuses a type under another name.
     */
    private String id(String containerId, List<String> chain, SchemaElement element) {
        if (registry.term(element.id()).isPresent()
                && XrPlacement.continuation(registry, chain, element.id()).isPresent()) {
            return element.id();
        }
        Set<String> terms = schemaTerms(element.type());
        List<String> matches = new ArrayList<>();
        for (Term candidate : registry.children(containerId)) {
            if (candidate.isGroup() && reusedTerms(candidate).equals(terms) && !terms.isEmpty()) {
                matches.add(candidate.id());
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("the schema puts " + element.id() + " under "
                    + containerId + ", where the registry does not record it, and "
                    + (matches.isEmpty() ? "no group there reuses" : "the groups " + matches
                            + " there reuse") + " exactly the business terms of its type");
        }
        return matches.get(0);
    }

    /** Returns the business terms a schema type carries directly, without its groups. */
    private Set<String> schemaTerms(String type) {
        Set<String> terms = new LinkedHashSet<>();
        for (SchemaElement element : types.getOrDefault(type, List.of())) {
            if (isTerm(element.id())) {
                terms.add(element.id());
            }
        }
        return terms;
    }

    /** Returns the business terms a group of the registry carries through {@code reusesTerms}. */
    private Set<String> reusedTerms(Term group) {
        Set<String> terms = new LinkedHashSet<>();
        for (String reused : group.reusesTerms()) {
            if (isTerm(reused)) {
                terms.add(reused);
            }
        }
        return terms;
    }

    private boolean isTerm(String id) {
        return registry.term(id).map(term -> !term.isGroup()).orElse(false);
    }

    /** Writes the table: a header naming the source, then one line per element. */
    private String text(byte[] schema) {
        StringBuilder table = new StringBuilder();
        table.append("# The element names and the element order of the XR representation.\n")
                .append("#\n")
                .append("# Generated from src/xsd/xrechnung-semantic-model.xsd of the KoSIT\n")
                .append("# XRechnung visualization, tag v2026-08-31, SHA-256\n")
                .append("# ").append(sha256(schema)).append(",\n")
                .append("# by XrElementsSource of this module. Do not edit by hand; the test\n")
                .append("# XrElementsTest derives this file again on every build and compares it.\n")
                .append("#\n")
                .append("# One line per element of the schema, in schema order, with tabs between\n")
                .append("# the fields: the group the element sits in, the business term or business\n")
                .append("# group it stands for, the name of the element, and the name of its type.\n")
                .append("# The group of an element at the root of the document is written as the\n")
                .append("# name of the root element, ").append(XrElements.ROOT).append(".\n");
        containers.forEach((containerId, elements) -> {
            for (XrElements.Element element : elements) {
                table.append(containerId).append('\t')
                        .append(element.id()).append('\t')
                        .append(element.name()).append('\t')
                        .append(element.type()).append('\n');
            }
        });
        return table.toString();
    }

    private static String appinfo(XdmNode element) {
        for (XdmNode annotation : children(element, "annotation")) {
            for (XdmNode appinfo : children(annotation, "appinfo")) {
                return appinfo.getStringValue().strip();
            }
        }
        throw new IllegalStateException("an element of the schema carries no business term");
    }

    private static String local(String type) {
        return Optional.ofNullable(type)
                .map(name -> name.startsWith(XR_PREFIX) ? name.substring(XR_PREFIX.length()) : name)
                .orElseThrow(() -> new IllegalStateException("an element of the schema has no type"));
    }

    private static List<XdmNode> children(XdmNode parent, String localName) {
        List<XdmNode> found = new ArrayList<>();
        for (XdmNode child : parent.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT
                    && XS.equals(child.getNodeName().getNamespace())
                    && localName.equals(child.getNodeName().getLocalName())) {
                found.add(child);
            }
        }
        return found;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime implements SHA-256", e);
        }
    }
}
