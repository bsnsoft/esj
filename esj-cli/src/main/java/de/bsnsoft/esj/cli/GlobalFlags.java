package de.bsnsoft.esj.cli;

import java.time.Duration;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The picocli mixin that carries the options of a run rather than of a command —
 * {@code --verbose}, {@code --debug}, the resource limits and the deadline — added to the
 * top level command and to every subcommand so that either position works.
 *
 * <p>The options are annotated methods rather than fields: picocli calls a setter only
 * with a value it actually parsed or with the declared default, and a run of the parser
 * over a subcommand therefore cannot undo what the same option set on the command above
 * it.
 *
 * <p>The limits are a policy of the reading party and take no part in conformance
 * (specification, sections 3.1 and 12.2), which is why they are switches at all: the
 * defaults are written for input from strangers, and a caller who knows its senders raises
 * them. What each one may be, and what the two profiles hold, is in {@link Bounds}.
 */
@Command
final class GlobalFlags {

    private final GlobalOptions target;

    /**
     * Set by picocli when {@code --help} is given; picocli prints the usage text itself.
     * The field exists so that every command has the option, and nothing reads it.
     */
    @Option(order = 1000, names = {"-h", "--help"}, usageHelp = true,
            description = "Show this help text and exit.")
    private boolean helpRequested;

    GlobalFlags(GlobalOptions target) {
        this.target = target;
    }

    @Option(order = 1010, names = "--verbose",
            description = "Explain on the error stream what was detected, how long the run "
                    + "took and what each validation artefact cost, show the importer's "
                    + "information-level notes, list every warning instead of the first 20, "
                    + "and, for a PDF, say what the PDF library had to say about the file.")
    void verbose(boolean value) {
        if (value) {
            target.enableVerbose();
        }
    }

    @Option(order = 1020, names = "--debug",
            description = "Print a stack trace when a command fails. Without it, a failure is "
                    + "one line.")
    void debug(boolean value) {
        if (value) {
            target.enableDebug();
        }
    }

    @Option(order = 1030, names = "--importer", paramLabel = "<streaming|xslt>",
            description = "Which reader turns an XML invoice into the semantic model."
                    + " streaming, the default, matches the document against the binding"
                    + " tables one element at a time and costs what one invoice line costs."
                    + " xslt runs the vendored visualization stylesheets, builds three trees"
                    + " of the document and is kept as the second implementation this"
                    + " project checks the first against. The two agree on 80 of the 86"
                    + " instances of the conformance corpus; conformance/readers.md records"
                    + " the rest. An ESJ input is read by neither.")
    void importer(String value) {
        target.importer(value);
    }

    @Option(order = 1040, names = "--attachment", paramLabel = "<name>",
            description = "The attachment of a PDF to read, named as the container names it."
                    + " Without it a PDF that carries exactly one electronic invoice is read"
                    + " and one that carries several is refused with exit code 2 and the list."
                    + " It is ignored for an input that is not a PDF, so that esj diff can"
                    + " compare a container with a file; esj diff reads both of its inputs"
                    + " with it.")
    void attachment(String value) {
        target.attachment(value);
    }

    @Option(order = 1050, names = "--attachment-index", paramLabel = "<position>",
            description = "The attachment of a PDF to read, by the position the tool prints"
                    + " for it, counted from one. It is the way to name one of two"
                    + " attachments that carry the same name, which a PDF permits and"
                    + " --attachment therefore refuses.")
    void attachmentIndex(int value) {
        target.attachmentIndex(value);
    }

    @Option(order = 1060, names = "--strict",
            description = "Refuse a document whose bytes are not written in the encoding it"
                    + " declares instead of recoding them, in every command. esj validate is"
                    + " strict in any case: its verdict is about the bytes it was given.")
    void strict(boolean value) {
        if (value) {
            target.enableStrict();
        }
    }

    @Option(order = 1070, names = "--limits", paramLabel = "<default|large>",
            description = "The profile of resource bounds to read within: default is the"
                    + " reference configuration of the specification, section 12.2 and is"
                    + " read within -Xmx256m; large is sized for invoices of tens of"
                    + " megabytes and hundreds of thousands of lines and needs a heap to"
                    + " match — about -Xmx1g for a file argument at its 512 MiB bounds,"
                    + " -Xmx1536m from the standard input, and -Xmx3g for XML at its 256 MiB"
                    + " bound (docs/deployment.md). A limit is a policy of this run and no"
                    + " statement about the document: reaching one leaves with exit code 7.")
    void limits(String value) {
        target.profile(value);
    }

    @Option(order = 1080, names = "--max-input-bytes", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "The largest XML input to read. A plain number of bytes, or one with"
                    + " a k, M or G suffix for multiples of 1024.")
    void maxInputBytes(long value) {
        target.bound(Bound.INPUT_BYTES, value);
    }

    @Option(order = 1090, names = "--max-output-bytes", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "The largest XML output to write. A conversion produces a document"
                    + " of a different size from the one it read — a cross industry invoice"
                    + " runs to about three times the UBL invoice it came from — so this"
                    + " bound is its own and not --max-input-bytes.")
    void maxOutputBytes(long value) {
        target.bound(Bound.OUTPUT_BYTES, value);
    }

    @Option(order = 1100, names = "--max-pdf-bytes", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "The largest PDF to open, in bytes of the file. It also bounds"
                    + " what one container may decode: the streams the PDF library reads"
                    + " to find the objects of the file, and the streams this reader"
                    + " decodes out of it. It bounds in the same proportion how many"
                    + " objects the object streams of one container may declare together."
                    + " The invoice inside is bounded by --max-input-bytes like any other"
                    + " XML input.")
    void maxPdfBytes(long value) {
        target.bound(Bound.PDF_BYTES, value);
    }

    @Option(order = 1110, names = "--max-attachments", paramLabel = "<count>",
            converter = Numbers.ByteCount.class,
            description = "The largest number of attachments of a PDF to enumerate.")
    void maxAttachments(long value) {
        target.bound(Bound.ATTACHMENTS, value);
    }

    @Option(order = 1120, names = "--max-pages", paramLabel = "<count>",
            converter = Numbers.ByteCount.class,
            description = "How many pages one rendering may have. It is a bound on what this"
                    + " run writes rather than on what it reads: a document inside every"
                    + " other bound can still be hundreds of pages, because one long value in"
                    + " a narrow column is. Reaching it leaves with exit code 7 and nothing"
                    + " written.")
    void maxPages(long value) {
        target.bound(Bound.RENDER_PAGES, value);
    }

    @Option(order = 1130, names = "--max-document-bytes", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "The largest ESJ document to read.")
    void maxDocumentBytes(long value) {
        target.bound(Bound.DOCUMENT_BYTES, value);
    }

    @Option(order = 1140, names = "--max-values", paramLabel = "<count>",
            converter = Numbers.ByteCount.class,
            description = "The largest number of members of values.")
    void maxValues(long value) {
        target.bound(Bound.VALUES, value);
    }

    @Option(order = 1150, names = "--max-string-bytes", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "The largest string value, in bytes of its UTF-8 encoding.")
    void maxStringBytes(long value) {
        target.bound(Bound.STRING_BYTES, value);
    }

    @Option(order = 1160, names = "--max-binary-bytes", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "The largest binary value, in bytes of its base64 encoding; the"
                    + " bound on the binary content of a whole document is raised with it"
                    + " where it would be the smaller of the two.")
    void maxBinaryBytes(long value) {
        target.bound(Bound.BINARY_BYTES, value);
    }

    @Option(order = 1170, names = "--max-path-segments", paramLabel = "<count>",
            converter = Numbers.ByteCount.class,
            description = "The largest number of segments of one semantic path, occurrence"
                    + " indices counted.")
    void maxPathSegments(long value) {
        target.bound(Bound.PATH_SEGMENTS, value);
    }

    @Option(order = 1180, names = "--max-extension-nodes", paramLabel = "<count>",
            converter = Numbers.ByteCount.class,
            description = "The largest number of nodes inside extensions.")
    void maxExtensionNodes(long value) {
        target.bound(Bound.EXTENSION_NODES, value);
    }

    @Option(order = 1190, names = "--max-buffered-bytes", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "The characters the streaming reader holds of the one element it"
                    + " has to read whole before it can decide a condition over it, counted"
                    + " over that element and everything below it. An embedded attachment"
                    + " is written inside such an element, which is why the default leaves"
                    + " room for one.")
    void maxBufferedBytes(long value) {
        target.bound(Bound.BUFFERED_BYTES, value);
    }

    @Option(order = 1200, names = "--max-buffered-elements", paramLabel = "<count>",
            converter = Numbers.ByteCount.class,
            description = "The elements it holds of that same element.")
    void maxBufferedElements(long value) {
        target.bound(Bound.BUFFERED_ELEMENTS, value);
    }

    @Option(order = 1210, names = "--max-runtime", paramLabel = "<duration>",
            converter = Numbers.Runtime.class,
            description = "How long this run may take, as 90s, 5m, 500ms or a bare number of"
                    + " seconds: reading the input, importing it and checking it together."
                    + " A run that reaches it while it is still deciding leaves with exit"
                    + " code 7 and no verdict, never with a verdict of invalid; reached"
                    + " while esj validate is drawing a --report, it gives up the file and"
                    + " keeps the verdict. The clock starts when this option is read."
                    + " A timeout around the process is the stronger guard and stays the"
                    + " first one to reach for. Default: 5m for esj validate and for"
                    + " esj render, none elsewhere.")
    void maxRuntime(Duration value) {
        target.maxRuntime(value);
    }
}
