package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * Reads the profile a document names, which is BT-24, the Specification identifier.
 *
 * <p>The profile decides which rule sets apply, so it is read from the document rather
 * than asked of the caller: a document that says it follows a core invoice usage
 * specification is checked against that specification's rules whether or not anybody
 * remembered to say so on a command line.
 *
 * <p>The element is found by walking the tree by local name rather than by an XPath
 * expression, because that is two steps in UBL and three in CII and because it needs no
 * namespace context, no compilation and no second copy of the syntax binding. The rest
 * of the binding is nobody's business here: this is the one term that decides which
 * artefacts run.
 */
final class Profiles {

    private Profiles() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the customization identifier of a document.
     *
     * @param root   the root element of the document
     * @param syntax the syntax of the document
     * @return the identifier, trimmed, or the empty string where the document carries none
     */
    static String customizationId(XdmNode root, XrSyntax syntax) {
        Optional<XdmNode> element = switch (syntax) {
            case UBL_INVOICE, UBL_CREDIT_NOTE -> child(root, "CustomizationID");
            case CII -> child(root, "ExchangedDocumentContext")
                    .flatMap(context -> child(context,
                            "GuidelineSpecifiedDocumentContextParameter"))
                    .flatMap(parameter -> child(parameter, "ID"));
        };
        return element.map(node -> node.getStringValue().strip()).orElse("");
    }

    private static Optional<XdmNode> child(XdmNode parent, String localName) {
        for (XdmNode child : parent.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT
                    && localName.equals(child.getNodeName().getLocalName())) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }
}
