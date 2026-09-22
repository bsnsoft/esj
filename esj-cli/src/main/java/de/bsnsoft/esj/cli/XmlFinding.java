package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.xr.XrEncodingException;
import java.util.Objects;

/**
 * Something about the bytes of an XML input that stands between them and any semantic
 * document at all.
 *
 * <p>It is not a finding of the specification and it carries no {@code ESJ-} code: the
 * three layers of {@code SPEC.md} section 9 describe an ESJ document, and this describes
 * the file that was supposed to become one. The category is the layer it belongs to —
 * {@code XML} — and the code names the kind of problem, so that a program branching on a
 * report can tell "this document breaks a rule of the format" from "these bytes are not
 * the document they say they are".
 *
 * <p>Every finding of this kind is fatal. There is no severity to choose from, because
 * there is no such thing as a document that was read despite one: the parser was handed
 * bytes it could not be handed, and what follows is that no layer ran.
 *
 * @param category the part of the input the finding is about, currently always
 *                 {@link #XML}
 * @param code     a stable, machine-readable identifier of the kind of problem
 * @param message  human-readable English text
 */
record XmlFinding(String category, String code, String message) {

    /** The category of everything that is about the XML bytes rather than the document. */
    static final String XML = "XML";

    /** The bytes of a document are not written in the encoding the document declares. */
    static final String XML_ENCODING = "XML-ENCODING";

    /** The severity every finding of this kind has. */
    static final String SEVERITY = "error";

    /**
     * Checks that no member is {@code null}.
     *
     * @param category the part of the input the finding is about, currently always
     *                 {@link #XML}
     * @param code     a stable, machine-readable identifier of the kind of problem
     * @param message  human-readable English text
     * @throws NullPointerException if a member is {@code null}
     */
    XmlFinding {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Returns the finding an encoding that does not match the bytes draws.
     *
     * <p>The message names what the document declared and what its bytes are, because the
     * two are what the writer of the document has to reconcile, and a reader who is told
     * only that "the encoding is wrong" has to guess which of the two to change.
     *
     * @param refused what the front door of the importer refused
     * @return the finding
     * @throws NullPointerException if {@code refused} is {@code null}
     */
    static XmlFinding encoding(XrEncodingException refused) {
        Objects.requireNonNull(refused, "refused");
        return new XmlFinding(XML, XML_ENCODING,
                "the bytes of this document are not written in the encoding it declares:"
                        + " declared " + refused.declared().orElse("nothing")
                        + ", and the bytes are " + refused.assumed()
                        + "; the document is invalid as it stands, whatever it would say"
                        + " once recoded");
    }

    /**
     * Returns the finding as one line, in the form the layers of a report use.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return code + " [" + SEVERITY + "] " + message;
    }
}
