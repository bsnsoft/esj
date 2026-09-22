package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.xr.XrSyntax;
import java.util.Optional;

/**
 * What the tool found at the front of its input, and what it does with it.
 *
 * <p>The three XML constants are the three root elements {@code esj-xr} reads; the
 * token of each is what {@code --output json} writes and what {@code --from} accepts,
 * and the two UBL document types share the token {@code ubl} because the option names a
 * syntax and not a document type.
 */
enum InputSyntax {

    /** An ESJ document: a JSON object. */
    ESJ("esj", "ESJ", null),

    /** A UBL 2.1 invoice. */
    UBL_INVOICE("ubl", "UBL Invoice", XrSyntax.UBL_INVOICE),

    /** A UBL 2.1 credit note. */
    UBL_CREDIT_NOTE("ubl", "UBL CreditNote", XrSyntax.UBL_CREDIT_NOTE),

    /** A UN/CEFACT cross industry invoice, CII D16B. */
    CII("cii", "CII", XrSyntax.CII);

    private final String token;
    private final String label;
    private final XrSyntax xr;

    InputSyntax(String token, String label, XrSyntax xr) {
        this.token = token;
        this.label = label;
        this.xr = xr;
    }

    /** Returns the token {@code --from} accepts for this syntax. */
    String token() {
        return token;
    }

    /** Returns the name to print for a human reader. */
    String label() {
        return label;
    }

    /** Tells whether reading this syntax means going through the XML importer. */
    boolean isXml() {
        return xr != null;
    }

    /** Returns the syntax of {@code esj-xr} this constant stands for, where there is one. */
    Optional<XrSyntax> xrSyntax() {
        return Optional.ofNullable(xr);
    }

    /**
     * Returns the constant that stands for a syntax of {@code esj-xr}.
     *
     * @param xr the syntax of the importer
     * @return the constant, or an empty optional where this tool has none for it
     */
    static Optional<InputSyntax> ofXrSyntax(XrSyntax xr) {
        for (InputSyntax syntax : values()) {
            if (syntax.xr == xr) {
                return Optional.of(syntax);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the syntax a root element belongs to.
     *
     * @param namespace the namespace URI of the root element, possibly empty
     * @param localName the local name of the root element
     * @return the syntax, or an empty optional for a root element of no syntax this tool
     *         reads
     */
    static Optional<InputSyntax> ofRootElement(String namespace, String localName) {
        for (InputSyntax syntax : values()) {
            if (syntax.xr != null && syntax.xr.matches(namespace, localName)) {
                return Optional.of(syntax);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the syntaxes a {@code --from} token names.
     *
     * @param token the token, one of {@code esj}, {@code ubl} and {@code cii}
     * @return the syntax to read the input as, which for {@code ubl} is the invoice
     *         constant because the importer recognizes the document type itself
     * @throws CliException if the token names no syntax
     */
    static InputSyntax ofToken(String token) {
        for (InputSyntax syntax : values()) {
            if (syntax.token.equals(token)) {
                return syntax;
            }
        }
        throw CliException.input("--from takes esj, ubl or cii, not '" + token + "'");
    }
}
