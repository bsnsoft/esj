package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.BindingEditionException;
import de.bsnsoft.esj.bindings.BindingTable;
import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.pdf.EmbedOptions;
import de.bsnsoft.esj.pdf.EmbedRefusedException;
import de.bsnsoft.esj.pdf.EmbedResult;
import de.bsnsoft.esj.pdf.EsjAttachment;
import de.bsnsoft.esj.pdf.FacturX;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.pdf.HybridFlavour;
import de.bsnsoft.esj.pdf.PdfAccessException;
import de.bsnsoft.esj.pdf.PdfaCheck;
import de.bsnsoft.esj.pdf.PdfException;
import de.bsnsoft.esj.pdf.PdfLimitException;
import java.util.Optional;

/**
 * Writing the invoice into the PDF that shows it, for the two commands that do it.
 *
 * <p>{@code esj embed} takes a PDF and an invoice; {@code esj render --embed cii} renders
 * the pages first and embeds into its own rendering. Everything after that point is the
 * same operation with the same refusals, so it is here and not in either command: the
 * profile the container declares, the name the attachment gets, the bounds the input PDF
 * is opened under, and the translation of a refusal of {@code esj-pdf} into an exit code.
 *
 * <p>Every refusal is a statement about the two inputs — the file is not a PDF/A-3, it
 * already carries an invoice, the document names another profile than the container would
 * declare — so every one of them is {@link ExitCode#INPUT}. A bound of this run that the
 * input PDF outgrew is the exception and stays {@link ExitCode#LIMIT}, because a reader
 * that stopped has said nothing about what it was reading.
 *
 * <p>The report of the writer is carried out with the file rather than dropped: the
 * hybrid invoice is the archived record, and a term the cross industry invoice had no
 * place for is a term the archive does not carry. The writer is handed the extension
 * registries {@code --extension} loaded, and both commands print its report the way
 * {@code esj convert --to cii} prints it: a term its registry keeps out of every syntax on
 * one information line, anything else the syntax had no place for as a warning.
 */
final class Embedding {

    /** The semantic path of BT-24, which says which specification the invoice is written to. */
    private static final SemanticPath SPECIFICATION_IDENTIFIER = SemanticPath.of("/BG-2/BT-24");

    /** The conventional name of an XRechnung in a PDF, which this tool does not write. */
    private static final String XRECHNUNG_XML = "xrechnung.xml";

    private Embedding() {
        throw new AssertionError("no instances");
    }

    /**
     * Writes the invoice into the file and says what the attachment had no place for.
     *
     * @param pdf      the bytes of the PDF/A-3 file
     * @param document the invoice
     * @param options  the profile, the flavour, the bounds and an optional validator
     * @param console  the streams of this run, which the report goes to
     * @return the hybrid invoice
     * @throws CliException if the file or the document is not one that can be embedded,
     *                      or if a bound of this run was reached
     */
    static byte[] into(byte[] pdf, SemanticDocument document, EmbedOptions options,
                       Console console) {
        try {
            EmbedResult result = FacturX.embedWithReport(pdf, document, options);
            Reports.notPlaced(console, result.report());
            say(console, result);
            return result.pdf();
        } catch (BindingEditionException refused) {
            throw editionRefusal(document);
        } catch (PdfLimitException e) {
            throw CliException.limit(e.getMessage(), e);
        } catch (EmbedRefusedException e) {
            throw CliException.input("this invoice was not written into the file: "
                    + e.getMessage(), e);
        } catch (PdfAccessException e) {
            throw CliException.input("cannot open the file the invoice goes into: "
                    + e.getMessage(), e);
        } catch (PdfException e) {
            throw CliException.input("cannot read the file the invoice goes into as a PDF: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Says in one line what became of the ESJ document that goes in beside the invoice.
     *
     * <p>Both ways round. A file that carries it says so, because a consumer that has to
     * open the container to find out is a consumer nobody told; a file that does not says
     * why, because an attachment that is quietly absent is the one a sender believes is
     * there. The line goes to the error stream, where everything this command says about
     * the file it wrote goes: the standard output is the file.
     *
     * <p>Only one of the three is a warning. A run that left the attachment out because
     * {@code --no-esj} said to did what it was told, and a tool that warns about obeying
     * its caller teaches that caller to ignore its warnings; a run that left it out
     * although it was wanted is the one nobody must miss.
     */
    private static void say(Console console, EmbedResult result) {
        switch (result.esj()) {
            case ATTACHED -> console.diagnostic("the ESJ document of this invoice is"
                    + " attached beside it as " + ValueText.quoted(EsjAttachment.NAME));
            case TURNED_OFF -> console.diagnostic(result.esjOmitted().orElseThrow());
            case UNPROVEN -> console.warning(result.esjOmitted().orElseThrow());
            default -> throw new IllegalStateException("unknown outcome: " + result.esj());
        }
    }

    /**
     * Returns what the container will declare, out of the options and the document.
     *
     * @param profile    what {@code --profile} named, or {@code null} to take the
     *                   document's own
     * @param name       what {@code --name} named, or {@code null} for {@code factur-x.xml}
     * @param verapdf    what {@code --verapdf} named, or {@code null} to go by what the
     *                   input PDF declares about itself
     * @param esj        whether the ESJ document of the invoice is attached beside the XML
     * @param extensions the extension registries {@code --extension} loaded, which the
     *                   writer of the attachment is handed
     * @param document   the invoice, whose BT-24 names the profile it is written to
     * @param console    the streams and the bounds of this run
     * @param deadline   what is left of the time this run was given, which the validator
     *                   runs inside
     * @return the options
     * @throws CliException if a token names something this version does not write, or if
     *                      the profile has to be taken from a document that names none
     */
    static EmbedOptions options(String profile,
                                String name,
                                String verapdf,
                                boolean esj,
                                Extensions extensions,
                                SemanticDocument document,
                                Console console,
                                Deadline deadline) {
        // The edition first: the attachment is a cross industry invoice, so a document of
        // an edition the CII binding table was not written against cannot be embedded at
        // all, and the answer is that refusal with its way out rather than a sentence
        // about the profile its BT-24 names.
        requireBindableEdition(document);
        EmbedOptions options = EmbedOptions.of(profile(profile, document))
                .withLimits(console.options().bounds().pdfLimits())
                .withEsj(esj)
                .withExtensions(extensions.registries());
        if (name != null) {
            options = options.withFlavour(flavour(name));
        }
        return verapdf == null ? options
                : options.checkedWith(validator(verapdf, console, deadline));
    }

    /**
     * Refuses a document of an edition the attachment cannot be written in.
     *
     * <p>The attachment is a cross industry invoice, so the edition the CII binding table
     * was written against decides what may be embedded, exactly as it decides what
     * {@code esj convert --to cii} writes. The refusal is the whole document, both
     * editions and the way out, and never a container carrying the terms the two editions
     * happen to share.
     *
     * @param document the invoice that was to be embedded
     * @throws CliException always
     */
    private static void requireBindableEdition(SemanticDocument document) {
        if (!BindingTable.of(BindingSyntax.CII).describes(document.semanticModel())) {
            throw editionRefusal(document);
        }
    }

    /** Returns the refusal both the check above and the writer below leave with. */
    private static CliException editionRefusal(SemanticDocument document) {
        return Editions.refuse(document, "the CII binding table of the attachment binds "
                + BindingTable.of(BindingSyntax.CII).semanticModel());
    }

    /**
     * Returns a validator over the veraPDF the caller installed.
     *
     * <p>Without it the claim the hybrid file makes rests on the claim the input made
     * about itself: a letterhead that says PDF/A-3 and is not one becomes a hybrid invoice
     * that declares a conformance it does not have, written by this tool. The validator
     * runs as a process of its own inside what is left of {@code --max-runtime}, as it
     * does under {@code esj validate}.
     */
    private static PdfaCheck validator(String installation, Console console,
                                       Deadline deadline) {
        return pdf -> {
            Verapdf.Result result = Verapdf.validate(installation, pdf,
                    deadline.remaining("validating the PDF/A conformance of the file the"
                            + " invoice goes into"), console);
            console.verbose(result.describe());
            return result.compliant() ? Optional.empty() : Optional.of(result.describe());
        };
    }

    /**
     * Returns the profile the container declares.
     *
     * <p>Without {@code --profile} it is the document's own: BT-24 names the
     * specification the invoice is written to, and the container declares that and
     * nothing else. The option exists for a caller who wants the claim written out, and a
     * document that names another profile is then refused by {@code esj-pdf} rather than
     * relabelled.
     */
    private static FacturXProfile profile(String token, SemanticDocument document) {
        if (token != null) {
            return FacturXProfile.ofConformanceLevel(token)
                    .filter(FacturXProfile::isEn16931Invoice)
                    .orElseThrow(() -> CliException.input("--profile takes " + profiles()
                            + ", not '" + token + "'"));
        }
        Optional<String> identifier = document.value(SPECIFICATION_IDENTIFIER)
                .map(SemanticValue::content);
        if (identifier.isEmpty()) {
            throw CliException.input("this document carries no BT-24, so nothing in it says"
                    + " which specification it is written to and the container has nothing"
                    + " to declare; --profile writes the claim explicitly");
        }
        return FacturXProfile.ofSpecificationIdentifier(identifier.get())
                .filter(FacturXProfile::isEn16931Invoice)
                .orElseThrow(() -> CliException.input("BT-24 of this document is "
                        + ValueText.quoted(identifier.get()) + ", which names no profile"
                        + " this tool writes into a container; --profile takes "
                        + profiles()));
    }

    /**
     * Returns the container flavour the attachment name belongs to.
     *
     * <p>The name is not free of the rest: each published flavour gives the attachment a
     * name, an XMP extension schema and a version of it, and a file that mixed them would
     * be one no specification defines. {@code xrechnung.xml} is a conventional name with
     * no container specification behind it, so there is no declaration this tool could
     * honestly write beside it; an XRechnung goes in under {@code --profile XRECHNUNG}.
     */
    private static HybridFlavour flavour(String token) {
        Optional<HybridFlavour> flavour = HybridFlavour.ofAttachmentName(token);
        if (flavour.isPresent()) {
            return flavour.get();
        }
        throw CliException.input("--name takes " + HybridFlavour.attachmentNames()
                + ", not '" + token + "'; "
                + (XRECHNUNG_XML.equals(token)
                        ? "xrechnung.xml is a conventional name for an XRechnung inside a"
                                + " PDF and no container specification says which XMP"
                                + " declaration belongs beside it; an XRechnung is embedded"
                                + " under --profile XRECHNUNG"
                        : "a made-up name is found by no consumer"));
    }

    /** Returns the profiles this tool writes, in the order they are offered. */
    private static String profiles() {
        StringBuilder profiles = new StringBuilder();
        for (FacturXProfile profile : FacturXProfile.values()) {
            if (profile.isEn16931Invoice()) {
                profiles.append(profiles.length() == 0 ? "" : ", ")
                        .append(profile.conformanceLevel().replace(" ", ""));
            }
        }
        return profiles.toString();
    }
}
