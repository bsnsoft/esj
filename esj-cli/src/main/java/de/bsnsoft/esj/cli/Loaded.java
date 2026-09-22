package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.BindingException;
import de.bsnsoft.esj.bindings.BindingLimitException;
import de.bsnsoft.esj.bindings.BindingSyntaxException;
import de.bsnsoft.esj.bindings.ReaderOptions;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.ReadResult;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.pdf.AttachmentKind;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportReport;
import de.bsnsoft.esj.imports.ImportResult;
import de.bsnsoft.esj.xr.XmlBytes;
import de.bsnsoft.esj.xr.XmlEncodingReport;
import de.bsnsoft.esj.xr.XrEncodingException;
import de.bsnsoft.esj.xr.XrEncodingMode;
import de.bsnsoft.esj.xr.XrException;
import de.bsnsoft.esj.xr.XrImporter;
import de.bsnsoft.esj.xr.XrLimitException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One input after it has been recognized and read into a semantic document.
 *
 * <p>Every command starts here, and the six steps are the same for all of them: read the
 * bytes, recognize the syntax, hand them to the reader or to the importer, keep what the
 * importer had to say, keep what the reader found, and pass the document on. The
 * commands differ only in what they do with the result.
 *
 * <p>An ESJ input is read with {@link EsjReader#readWithFindings(byte[])} rather than
 * with the reader that throws, because {@code esj validate} has to report layer L1 as
 * findings and the other commands can turn the same findings into a refusal. That way
 * one path through the reader serves both, and a document that fails L1 fails it in the
 * same words whichever command met it.
 *
 * <p>A PDF is one more shape of the same thing. It is opened first, the electronic invoice
 * is taken out of it, and everything after that is the run this would have been if that XML
 * had been the file: the container travels along as {@link #container()} so that a report
 * can say what it found without the invoice and the container sharing one verdict.
 *
 * @param name            what to call the input in a message, which stays the name of the
 *                        file the user named even when the document came out of a container
 * @param syntax          the syntax the bytes were written in
 * @param document        the document, absent where parsing could not be completed
 * @param formatFindings  what layer L1 found, empty for an input that was not ESJ
 * @param report          what the importer had to say, empty for an ESJ input
 * @param extension       the extension registries this run loaded, which decide whether
 *                        it is worth telling the reader of a note that one can be
 * @param importer        the reader that built the document from XML, absent for an ESJ
 *                        input, which no importer touches
 * @param container       the PDF the document came out of, absent for a file that was one
 * @param encoding        what the bytes declared and what they turned out to be, present
 *                        only where the two disagreed and the importer recoded them
 * @param syntaxFindings  what stands between the bytes and any document at all, which is
 *                        a finding rather than a refusal only for {@code esj validate}
 */
record Loaded(String name,
              InputSyntax syntax,
              Optional<SemanticDocument> document,
              List<Finding> formatFindings,
              ImportReport report,
              Extensions extension,
              Optional<Importer> importer,
              Optional<Container> container,
              Optional<XmlEncodingReport> encoding,
              List<XmlFinding> syntaxFindings) {

    /** How many distinct note lines the error stream carries without {@code --verbose}. */
    private static final int NOTE_LINES = 20;

    /**
     * What to do about a term no loaded registry defines, said once per run.
     *
     * <p>A note that says an extension registry defines a term reads, without this, as
     * "this tool cannot do that", and the difference matters: the registry in question
     * ships in the same jar, and loading it is the difference between an XRechnung
     * invoice arriving whole and arriving without most of its lines.
     */
    private static final String EXTENSION_HINT =
            "where a note says an extension registry defines a term,"
                    + " --extension xrechnung loads it";

    /**
     * Reads one input.
     *
     * @param input     the bytes and their name
     * @param from      the syntax the caller named with {@code --from}, or {@code null}
     *                  to recognize it
     * @param extension the extension registries this run loads
     * @param console   the streams of the process, for {@code --verbose}
     * @return the input, read
     * @throws CliException if the syntax is not one this tool reads, or an XML input
     *                      cannot be imported
     */
    static Loaded read(Input input, InputSyntax from, Extensions extension, Console console) {
        return load(input, from, extension, console,
                console.options().strict() ? XrEncodingMode.STRICT : XrEncodingMode.REPAIR,
                false, Container.Supplement.SKIPPED);
    }

    /**
     * Reads one input for {@code esj inspect}, which reports on the container it came out
     * of and therefore reads the ESJ document beside the invoice as well.
     *
     * @param input     the bytes and their name
     * @param from      the syntax the caller named with {@code --from}, or {@code null}
     * @param extension the extension registries this run loads
     * @param console   the streams of the process
     * @return the input, read
     * @throws CliException if the syntax is not one this tool reads, or an XML input
     *                      cannot be imported
     */
    static Loaded inspect(Input input, InputSyntax from, Extensions extension,
                          Console console) {
        return load(input, from, extension, console,
                console.options().strict() ? XrEncodingMode.STRICT : XrEncodingMode.REPAIR,
                false, Container.Supplement.OPTIONAL);
    }

    /**
     * Reads one input for {@code esj validate}, whose verdict is about the bytes it was
     * given.
     *
     * <p>Two things follow from that and from nothing else. The front door is strict, so
     * bytes that are not written in the encoding they declare are not quietly recoded
     * before the answer is formed; and the refusal that follows is a finding rather than
     * an error, because a document that cannot be read is an answer about the document and
     * belongs in the report beside the layers that were not reached.
     *
     * @param input     the bytes and their name
     * @param from      the syntax the caller named with {@code --from}, or {@code null}
     * @param extension the extension registries this run loads
     * @param console   the streams of the process
     * @return the input, read, or read as far as it goes
     * @throws CliException if the syntax is not one this tool reads
     */
    static Loaded validate(Input input, InputSyntax from, Extensions extension,
                           Console console) {
        return load(input, from, extension, console, XrEncodingMode.STRICT, true,
                Container.Supplement.REQUIRED);
    }

    /**
     * Reads one input with the front door repairing, whatever this run was configured
     * with.
     *
     * <p>It serves the one caller that has already formed its verdict on the bytes as they
     * are and now shows, for information, what the same document says once the encoding is
     * put right.
     *
     * @param input     the bytes and their name
     * @param from      the syntax the caller named with {@code --from}, or {@code null}
     * @param extension the extension registries this run loads
     * @param console   the streams of the process
     * @return the input, read from the recoded bytes
     * @throws CliException if the syntax is not one this tool reads, or the import failed
     *                      for a reason the encoding was not
     */
    static Loaded repaired(Input input, InputSyntax from, Extensions extension,
                           Console console) {
        return load(input, from, extension, console, XrEncodingMode.REPAIR, false,
                Container.Supplement.REQUIRED);
    }

    private static Loaded load(Input input,
                               InputSyntax from,
                               Extensions extension,
                               Console console,
                               XrEncodingMode mode,
                               boolean encodingIsAFinding,
                               Container.Supplement supplement) {
        Bounds bounds = console.options().bounds();
        if (input.isPdf()) {
            // Only a command that reports on the container reads the ESJ document beside
            // the invoice: see Container.read.
            Container container = supplement == Container.Supplement.SKIPPED
                    ? Container.read(input, console)
                    : Container.readWithSupplement(input, console, supplement);
            Container.Invoice invoice = container.requireInvoice();
            // An attachment whose root element lies beyond the classification window was
            // never established to be anything, and a caller who names it is asking for it
            // to be read rather than classified. The bytes decide, as always: the detector
            // below reads the whole attachment, and the container report carries
            // PDF-EMBEDDED-UNDETERMINED so that what happened is on the record.
            if (!invoice.attachment().kind().isInvoice()
                    && invoice.attachment().kind() != AttachmentKind.UNDETERMINED) {
                throw CliException.input(input.name() + ": the attachment "
                        + Container.describe(invoice.attachment()) + " carries no electronic"
                        + " invoice; esj extract --list shows what the file carries");
            }
            Input inner = new Input(input.name(), invoice.bytes());
            // The container already classified the attachment, on a decoder that reads
            // every encoding XML allows. Running the byte detector over the same bytes
            // again would replace that answer with a weaker one — it reads no wide
            // encoding — and a UTF-16 invoice inside a PDF would be reported as no
            // invoice at all.
            Optional<InputSyntax> classified = invoice.attachment().kind().syntax()
                    .flatMap(InputSyntax::ofXrSyntax);
            InputSyntax syntax = from != null || classified.isEmpty()
                    ? resolve(inner, from)
                    : classified.orElseThrow();
            Loaded loaded = read(inner, syntax, origin(from, classified.isPresent()),
                    extension, console, bounds, mode, encodingIsAFinding,
                    Optional.of(container));
            return loaded.document().isPresent()
                    ? loaded.inside(container.against(loaded.document().orElseThrow(),
                            input, console))
                    : loaded;
        }
        return read(input, resolve(input, from), origin(from, false), extension, console,
                bounds, mode, encodingIsAFinding, Optional.empty());
    }

    /**
     * Returns the XML the document arrived as, under the name of the input.
     *
     * <p>For a PDF that is the attachment and not the file. The official artefacts read
     * bytes rather than a document, and the bytes they are about are the invoice's: a
     * container is not XML, and an engine handed one would report the file as unreadable
     * and say so about the invoice inside it.
     *
     * @param input the input as it was handed over
     * @return the bytes an XML engine is to be run over
     */
    Input xml(Input input) {
        return container.flatMap(Container::invoice)
                .map(invoice -> new Input(input.name(), invoice.bytes()))
                .orElse(input);
    }

    /** Says where the syntax of an input came from, for the line {@code --verbose} writes. */
    private static String origin(InputSyntax from, boolean fromContainer) {
        if (from != null) {
            return " (named by --from)";
        }
        return fromContainer ? " (the attachment of the container)" : " (detected)";
    }

    private static Loaded read(Input input,
                               InputSyntax syntax,
                               String origin,
                               Extensions extension,
                               Console console,
                               Bounds bounds,
                               XrEncodingMode mode,
                               boolean encodingIsAFinding,
                               Optional<Container> container) {
        if (console.options().verbose()) {
            // The detector parses the root element again for this; it is only worth the
            // work when somebody asked to be told what the tool saw.
            console.verbose(input.name() + ": " + InputDetector.describe(input.bytes()));
        }
        console.verbose(input.name() + ": " + syntax.label() + origin);
        if (syntax.isXml()) {
            Importer importer = console.options().importer();
            console.verbose(input.name() + ": read with " + importer.token() + ", "
                    + importer.label());
            Loaded loaded = importXml(input, syntax, extension, bounds, importer, mode,
                    encodingIsAFinding, container);
            loaded.refuseWhereABoundEditedTheDocument(console);
            return loaded;
        }
        ReadResult result = EsjReader.withLimits(bounds.readerLimits())
                .readWithFindings(input.bytes());
        return new Loaded(input.name(), syntax, result.document(), result.findings(),
                ImportReport.empty(), extension, Optional.empty(), container,
                Optional.empty(), List.of());
    }

    /** Returns this result with the container it came out of replaced by a later one. */
    private Loaded inside(Container replaced) {
        return new Loaded(name, syntax, document, formatFindings, report, extension, importer,
                Optional.of(replaced), encoding, syntaxFindings);
    }

    /**
     * Returns the reader this input was read with, as one line for a report a person
     * reads. An ESJ input was read by the reader of the format itself and by no importer,
     * and the line says so rather than naming a reader that never ran.
     *
     * @return the description
     */
    String importerLabel() {
        return importer.map(chosen -> chosen.token() + " — " + chosen.label())
                .orElse("(none — the input is already ESJ)");
    }

    /**
     * Returns the document, or refuses the input with the first error layer L1 found.
     *
     * <p>A limit is not such an error. The reader reports it as a finding like any other,
     * because the specification, section 9.5 has it be one, but it says that this run was
     * configured to read less than the document holds rather than that the document is
     * malformed — so it is refused with {@link ExitCode#LIMIT} and the switch that raises
     * the bound, and the caller can tell the two apart without reading English.
     *
     * @param console the streams of the process, which carry the bounds of this run
     * @return the document
     * @throws CliException if the input is not a well-formed document, or reached a bound
     *                      of this run
     */
    SemanticDocument require(Console console) {
        // The bound is looked for among all the findings and not only the first error.
        // The reader collects what it meets and reads on, so a document that is malformed
        // somewhere and too large as well would otherwise be refused as malformed, and a
        // caller told to branch on the exit code would read a defect of the document where
        // this run had merely stopped reading.
        Optional<Finding> limit = formatFindings.stream()
                .filter(finding -> finding.code() == FindingCode.ESJ_L1_LIMIT)
                .findFirst();
        if (limit.isPresent()) {
            throw CliException.limit(console.options().bounds()
                    .refusal(name, limit.orElseThrow().message()));
        }
        Optional<Finding> error = formatFindings.stream().filter(Finding::isError).findFirst();
        if (error.isPresent()) {
            throw CliException.input(name + " is not a well-formed ESJ document: "
                    + error.orElseThrow());
        }
        return document.orElseThrow(() -> CliException.input(
                name + " could not be read as an ESJ document"));
    }

    /**
     * Refuses an import that a bound of this run cut down, after saying what was lost.
     *
     * <p>The importer leaves out what does not fit and records it, so a run that ignored
     * those notes would write a document that is not the invoice it was given and leave
     * with a successful exit code — and the contract of this tool tells a caller to branch
     * on the exit code and never to read the error stream. A bound of this run may
     * therefore decline to give a verdict, which is {@link ExitCode#LIMIT}, and it may not
     * edit the invoice and call the result a success.
     *
     * <p>The notes are printed first, because the refusal names one bound and the import
     * may have met several, and the caller wants the list rather than the first line of
     * it. The command this returns to never prints them a second time: it is not reached.
     *
     * @param console the streams of the process, which carry the bounds of this run
     * @throws CliException if a bound of this run kept content of the source out of the
     *                      document
     */
    private void refuseWhereABoundEditedTheDocument(Console console) {
        List<ImportNote> reached = report.notes(ImportNote.Kind.LIMIT_REACHED);
        if (reached.isEmpty()) {
            return;
        }
        reportNotes(console);
        throw CliException.limit(console.options().bounds()
                .refusal(name, reached.get(0).message()));
    }

    /**
     * Writes what the importer had to say to the error stream.
     *
     * <p>One line per note was unreadable: the same four sentences repeat dozens of times
     * over one invoice, each of them carrying the file name again, and a screenful of
     * near-identical text is a channel a user stops reading — which is exactly the channel
     * the report of dropped content depends on. So identical notes are collapsed to one
     * line with a count, the file name is said once, and the list is cut off after
     * {@link #NOTE_LINES}.
     *
     * <p>Notes at {@link ImportNote.Level#INFORMATION} describe the distance between the
     * source syntax and the semantic model rather than a loss in this document — a
     * component EN 16931 does not define at that term, an attribute that was empty to
     * begin with — so they are shown only under {@code --verbose}, which also shows the
     * warnings uncollapsed and uncut. A note about something a downstream engine can no
     * longer see is a warning and is shown here, whether or not the number it is about is
     * still the same number.
     *
     * <p>The header counts notes and says so. One note can stand for a whole subtree the
     * importer abandoned, so the number of notes is not the number of values that were
     * lost: saying "four observations" is true at any ratio, while saying "four values"
     * understates a skipped group by as much as the group holds. The size of such a
     * subtree is in the note itself, put there by the importer, which is the one side
     * that walks what it skips.
     *
     * @param console the streams of the process
     */
    void reportNotes(Console console) {
        encoding.ifPresent(recoded -> console.warning(name + ": encoding repaired: "
                + recoded.describe() + "; esj validate is strict about this and --strict"
                + " makes every command so"));
        for (ImportNote note : report.notes(ImportNote.Level.INFORMATION)) {
            console.verbose(name + ": " + note);
        }
        List<ImportNote> warnings = lostContent(report);
        if (warnings.isEmpty()) {
            return;
        }
        if (console.options().verbose()) {
            for (ImportNote note : warnings) {
                console.warning(name + ": " + note);
            }
        } else {
            collapse(console, warnings);
        }
        if (extension.isEmpty() && warnings.stream()
                .anyMatch(note -> note.kind() == ImportNote.Kind.UNKNOWN_TERM)) {
            console.diagnostic("  " + EXTENSION_HINT);
        }
    }

    /**
     * Returns the notes that say content of the source did not reach the document.
     *
     * <p>A recoded encoding is a warning like them and is not one of them: nothing was
     * lost, the bytes were read as what they are, and the reader is told so in a line of
     * its own. Counting it among the losses would say that an invoice arrived smaller than
     * it was sent, which is the one thing that line is watched for.
     *
     * @param report what the importer had to say
     * @return its warnings about content that did not arrive
     */
    static List<ImportNote> lostContent(ImportReport report) {
        return report.notes(ImportNote.Level.WARNING).stream()
                .filter(note -> note.kind() != ImportNote.Kind.ENCODING_REPAIRED)
                .toList();
    }

    /** Writes the warnings as one line each, collapsed by text and cut off. */
    private void collapse(Console console, List<ImportNote> warnings) {
        console.warning(name + ": " + warnings.size()
                + (warnings.size() == 1 ? " observation about" : " observations about")
                + " content of the source that did not reach the document");
        Map<String, Integer> collapsed = new LinkedHashMap<>();
        for (ImportNote note : warnings) {
            collapsed.merge(note.toString(), 1, Integer::sum);
        }
        int printed = 0;
        for (Map.Entry<String, Integer> entry : collapsed.entrySet()) {
            if (printed == NOTE_LINES) {
                console.diagnostic("  ... and " + (collapsed.size() - printed)
                        + " more; run with --verbose for all of them");
                return;
            }
            console.diagnostic("  " + entry.getKey()
                    + (entry.getValue() == 1 ? "" : " (" + entry.getValue() + " times)"));
            printed++;
        }
    }

    /**
     * Returns the syntax to read the input as. {@code --from ubl} names a syntax and not
     * a document type, and the importer reads both UBL document types either way, so the
     * root element is still asked which of the two it is — only to have the right name in
     * a message and in a report.
     */
    private static InputSyntax resolve(Input input, InputSyntax from) {
        if (from == null) {
            return detect(input);
        }
        if (from == InputSyntax.UBL_INVOICE) {
            return InputDetector.detect(input.bytes())
                    .filter(detected -> detected == InputSyntax.UBL_CREDIT_NOTE)
                    .orElse(from);
        }
        return from;
    }

    private static InputSyntax detect(Input input) {
        Optional<InputSyntax> syntax = InputDetector.detect(input.bytes());
        if (syntax.isPresent()) {
            return syntax.orElseThrow();
        }
        throw CliException.input(
                input.name() + " is neither an ESJ document nor a UBL invoice, a UBL credit"
                        + " note or a cross industry invoice; name the syntax with --from if"
                        + " it is one of them" + why(input));
    }

    /**
     * Says why nothing was recognized, where the bytes themselves say so.
     *
     * <p>An input whose encoding this tool does not read is not an input of an
     * unrecognized syntax, and a message that named only the syntaxes would send its
     * reader to look at the markup of a document whose markup may be perfectly good. The
     * same question is asked of the same bytes by the importer, and both ask it of
     * {@link XmlEncodingReport#unreadable()}.
     */
    private static String why(Input input) {
        Optional<String> unreadable = XmlBytes.inspect(input.bytes()).unreadable();
        if (unreadable.isPresent()) {
            return " (it is written in, or declares, the encoding "
                    + ValueText.quoted(unreadable.orElseThrow())
                    + ", which this tool does not read)";
        }
        return InputDetector.isWide(input.bytes())
                ? " (its first bytes are the byte order mark of a wide encoding, and no"
                        + " root element was read behind it)"
                : "";
    }

    /**
     * Imports an XML input with the reader this run was asked for. The registry either
     * reader is given is the one {@code --extension} asks for: with the core model alone,
     * an extension element becomes a note on the error stream rather than a value, which
     * is what a tool whose subject is EN 16931 should do unless it is told otherwise.
     *
     * <p>Both readers check the bytes against the encoding the document declares before
     * they parse, and both are given the mode of this run, so that {@code --strict} and
     * the repair that is reported mean the same thing whichever reader ran.
     */
    private static Loaded importXml(Input input,
                                    InputSyntax syntax,
                                    Extensions extension,
                                    Bounds bounds,
                                    Importer importer,
                                    XrEncodingMode mode,
                                    boolean encodingIsAFinding,
                                    Optional<Container> container) {
        try {
            ImportResult result = importer == Importer.STREAMING
                    ? stream(input, bounds, registry(extension), mode)
                    : transform(input, syntax, bounds, registry(extension), mode);
            return new Loaded(input.name(), syntax, Optional.of(result.document()), List.of(),
                    result.report(), extension, Optional.of(importer), container,
                    recoded(input, result.report()), List.of());
        } catch (XrEncodingException e) {
            if (encodingIsAFinding) {
                return new Loaded(input.name(), syntax, Optional.empty(), List.of(),
                        ImportReport.empty(), extension, Optional.of(importer), container,
                        Optional.empty(), List.of(XmlFinding.encoding(e)));
            }
            throw CliException.input("cannot read " + input.name() + ": " + e.getMessage()
                    + "; this run is strict, and without --strict the bytes are recoded and"
                    + " the repair is reported", e);
        }
    }

    /**
     * Returns the registry the readers are given for this run: the default edition, with
     * every extension registry {@code --extension} named combined into it.
     *
     * <p>An importer produces a document of the edition the source syntax binds, always.
     * UBL 2.1 and CII D16B bind the 2017 edition; producing a document of a later edition
     * from one of them would be asserting a mapping nobody published. A caller who wants
     * another edition converts and then runs {@code esj upgrade}, and the {@code source}
     * member plus the upgrade report say that both happened.
     */
    private static Registry registry(Extensions extension) {
        return extension.registry();
    }

    /**
     * Reads an XML input with the streaming reader.
     *
     * <p>The reader takes the syntax from the root element of the document rather than
     * from {@code --from}, because it has to know the element before it can match
     * anything against a table and the root element is the first thing it sees. Where it
     * does not recognize that element, the refusal names the other reader, which is the
     * one a caller who has to name the syntax by hand wants.
     */
    private static ImportResult stream(Input input, Bounds bounds, Registry registry,
                                       XrEncodingMode mode) {
        StreamingReader reader = new StreamingReader(ReaderOptions.builder()
                .registry(registry)
                .limits(bounds.readerLimits())
                .encodingMode(mode)
                .maxInputBytes(bounds.maxInputBytes())
                .maxBufferedBytes(bounds.maxBufferedBytes())
                .maxBufferedElements(bounds.maxBufferedElements())
                .build());
        try {
            return reader.read(input.bytes());
        } catch (BindingLimitException e) {
            throw CliException.limit(bounds.refusal(input.name(), e.getMessage()), e);
        } catch (BindingSyntaxException e) {
            throw CliException.input("cannot read " + input.name() + ": " + e.getMessage()
                    + "; --importer xslt reads a document whose syntax is named with --from",
                    e);
        } catch (BindingException e) {
            throw CliException.input("cannot read " + input.name() + ": " + e.getMessage(), e);
        }
    }

    /** Reads an XML input with the XSLT path. */
    private static ImportResult transform(Input input, InputSyntax syntax, Bounds bounds,
                                          Registry registry, XrEncodingMode mode) {
        XrImporter importer =
                new XrImporter(registry, bounds.maxInputBytes(), bounds.readerLimits())
                        .withEncodingMode(mode);
        try {
            return syntax == InputSyntax.CII
                    ? importer.importCiiWithReport(input.bytes())
                    : importer.importUblWithReport(input.bytes());
        } catch (XrEncodingException e) {
            // The front door of both readers. What it means for the run is decided one
            // level up, so it must not be folded into the sentence about the syntax below.
            throw e;
        } catch (XrLimitException e) {
            throw CliException.limit(bounds.refusal(input.name(), e.getMessage()), e);
        } catch (XrException e) {
            throw CliException.input("cannot read " + input.name() + " as "
                    + syntax.label() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns what the bytes declared and what they turned out to be, where the importer
     * recoded them.
     *
     * <p>The note the importer wrote says the same in English. This is the same fact in
     * the shape a report writes, and it is read from the bytes a second time rather than
     * parsed back out of that sentence.
     */
    private static Optional<XmlEncodingReport> recoded(Input input, ImportReport report) {
        return report.notes(ImportNote.Kind.ENCODING_REPAIRED).isEmpty()
                ? Optional.empty()
                : Optional.of(XmlBytes.inspect(input.bytes()));
    }
}
