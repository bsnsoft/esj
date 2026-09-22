package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.report.Phrase;

/**
 * The two ways this tool reads an XML invoice into the semantic model.
 *
 * <p>Both produce the same document for 80 of the 86 instances of the conformance corpus
 * and differ at one business term in the other six, which
 * {@code conformance/readers.md} records with the reason and the counts. They share no
 * code, which is the point of having two: the second is the oracle the first is measured
 * against on every build.
 *
 * <p>{@link #STREAMING} is the default. It pulls the document one element at a time and
 * matches each element against the binding tables of {@code model/bindings}, so it holds
 * one element and the values it has produced rather than three trees of the document; an
 * invoice of tens of megabytes costs seconds instead of minutes. {@link #XSLT} runs the
 * vendored XRechnung visualization stylesheets and maps their output, which is how this
 * project read XML before the tables existed and what its results are still checked
 * against.
 */
enum Importer {

    /** The table-driven streaming reader of {@code esj-bindings}. */
    STREAMING("streaming", "the streaming reader of the binding tables",
            Phrase.READER_STREAMING),

    /** The XSLT bootstrap of {@code esj-xr}. */
    XSLT("xslt", "the XSLT path of the vendored visualization stylesheets",
            Phrase.READER_XSLT);

    /** The token this tool ships as the default of {@code --importer}. */
    static final Importer DEFAULT = STREAMING;

    private final String token;
    private final String label;
    private final Phrase description;

    Importer(String token, String label, Phrase description) {
        this.token = token;
        this.label = label;
        this.description = description;
    }

    /**
     * Returns the reader a token names.
     *
     * @param token the token, or {@code null} where the option was not given
     * @return the reader, or {@link #DEFAULT} where the option was not given
     * @throws CliException if the token names no reader this version ships
     */
    static Importer ofToken(String token) {
        if (token == null) {
            return DEFAULT;
        }
        for (Importer importer : values()) {
            if (importer.token.equals(token)) {
                return importer;
            }
        }
        throw CliException.input("--importer takes " + STREAMING.token + " or " + XSLT.token
                + ", not '" + token + "'");
    }

    /** Returns the token this reader is named by on the command line. */
    String token() {
        return token;
    }

    /** Returns the one-line description of this reader, in English, for the text form. */
    String label() {
        return label;
    }

    /**
     * Returns the sentence a report writes about this reader, in its own language.
     *
     * <p>The line of a report names the reader twice over: the token, which is what a
     * caller writes on the command line and therefore stands as it is, and the sentence
     * saying what that token reads with, which is this project's own and is written in the
     * language the report is written in.
     *
     * @return the phrase, which takes the token as its one argument
     */
    Phrase description() {
        return description;
    }
}
