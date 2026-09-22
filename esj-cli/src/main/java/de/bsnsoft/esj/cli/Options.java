package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.bindings.UblWriter;
import de.bsnsoft.esj.render.PageSize;
import de.bsnsoft.esj.validate.ValidationLayer;

/**
 * The option values several commands share, and what each of them means.
 *
 * <p>picocli checks that an option was given a value; what that value stands for is
 * decided here, once, so that {@code --from}, {@code --extension} and {@code --level}
 * mean the same thing in every command that offers them and are refused in the same
 * words where they do not.
 */
final class Options {

    /** The only rule pack this version ships. */
    static final String EN16931 = "en16931";

    /** What {@code --rules} is given to leave the business rules out. */
    static final String NONE = "none";

    /** The cross industry invoice, which {@code --via} writes an ESJ document to by default. */
    static final String VIA_CII = "CII";

    /** The OASIS UBL document {@code --via ubl} writes an ESJ document to instead. */
    static final String VIA_UBL = "UBL";

    private Options() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the syntax {@code --from} names.
     *
     * @param token the token, or {@code null} where the option was not given
     * @return the syntax, or {@code null} to recognize the input from its first bytes
     * @throws CliException if the token names no syntax
     */
    static InputSyntax from(String token) {
        return token == null ? null : InputSyntax.ofToken(token);
    }

    /**
     * Returns the extension registries {@code --extension} asked for.
     *
     * <p>The option takes a comma separated list rather than one name, because the
     * registries this version ships describe disjoint terms of the same core model and a
     * document may carry terms of both.
     *
     * @param token the token, or {@code null} where the option was not given
     * @return the extensions to load, empty where the option was not given
     * @throws CliException if the token names no extension this version ships
     */
    static Extensions extension(String token) {
        return Extensions.of(token);
    }

    /**
     * Tells whether {@code --rules} asked for the EN 16931 rule pack.
     *
     * <p>The option takes a pack name rather than being a switch, because this build carries
     * one pack and later builds will carry more — the core invoice usage specifications have
     * rules of their own — and a caller who writes {@code --rules en16931} today keeps meaning
     * that when there is a second name to write.
     *
     * @param token {@code en16931}, {@code none}, or {@code null} for the default
     * @return whether the business rules are checked
     * @throws CliException if the token names no rule pack this version carries
     */
    static boolean rules(String token) {
        if (token == null || EN16931.equals(token)) {
            return true;
        }
        if (NONE.equals(token)) {
            return false;
        }
        throw CliException.input("--rules takes " + NONE + " or " + EN16931 + ", not '"
                + token + "'");
    }

    /**
     * Returns the highest validation layer {@code --level} asks for.
     *
     * @param token {@code l2}, {@code l3}, or {@code null} for the default
     * @return the layer to stop after
     * @throws CliException if the token names no layer that can be asked for
     */
    static ValidationLayer level(String token) {
        if (token == null || "l3".equals(token)) {
            return ValidationLayer.L3;
        }
        if ("l2".equals(token)) {
            return ValidationLayer.L2;
        }
        throw CliException.input("--level takes l2 or l3, not '" + token + "'");
    }

    /**
     * Returns the syntax {@code --via} names an ESJ document to be written to.
     *
     * <p>The cross industry invoice is the default because it is the binding that carries
     * the semantic model most completely: over the conformance corpus it takes every
     * document the writer is given, where the UBL binding requires elements EN 16931-1
     * states no term for ({@code conformance/writers/ubl-roundtrip.md}). A caller whose
     * documents travel as UBL asks for UBL and is told where the difference falls.
     *
     * @param token the token, or {@code null} where the option was not given
     * @return {@link #VIA_CII} or {@link #VIA_UBL}
     * @throws CliException if the token names no syntax this version writes
     */
    static String via(String token) {
        if (token == null || "cii".equals(token)) {
            return VIA_CII;
        }
        if ("ubl".equals(token)) {
            return VIA_UBL;
        }
        throw CliException.input("--via takes cii or ubl, not '" + token + "'");
    }

    /**
     * Returns which of the two UBL documents {@code --ubl-document} names.
     *
     * <p>UBL writes an invoice and a credit note as two document types where the semantic
     * model has one, and which of the two a document is is a fact of the invoice type code
     * BT-3. Reading BT-3 is therefore the default and the right answer; the other two
     * values exist for a receiver that expects one of the two whatever the code says, and
     * a run that uses them writes a document whose type code the target syntax may not
     * admit, which its own validation artefacts will say.
     *
     * @param token the token, or {@code null} where the option was not given
     * @return the document type to write
     * @throws CliException if the token names no document of UBL 2.1
     */
    static UblWriter.DocumentType ublDocument(String token) {
        if (token == null || "auto".equals(token)) {
            return UblWriter.DocumentType.AUTO;
        }
        if ("invoice".equals(token)) {
            return UblWriter.DocumentType.INVOICE;
        }
        if ("creditnote".equals(token)) {
            return UblWriter.DocumentType.CREDIT_NOTE;
        }
        throw CliException.input("--ubl-document takes invoice, creditnote or auto, not '"
                + token + "'");
    }

    /**
     * Returns the paper a switch names.
     *
     * <p>It is one reader for the two switches that take a paper — the rendering's and the
     * report's — because a caller who has learnt what {@code --page} accepts has learnt
     * what the other one accepts, and a second spelling of the same list is a second thing
     * to keep right.
     *
     * @param option the switch the token arrived through, for the refusal
     * @param token  the token, or {@code null} where the option was not given
     * @return the paper, {@link PageSize#A4} where the option was not given
     * @throws CliException if the token names no paper this version lays out for
     */
    static PageSize pageSize(String option, String token) {
        if (token == null) {
            return PageSize.A4;
        }
        for (PageSize size : PageSize.values()) {
            if (size.name().equalsIgnoreCase(token)) {
                return size;
            }
        }
        throw CliException.input(option + " takes A4 or LETTER, not '" + token + "'");
    }
}
