import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.invoice.Invoice;
import de.bsnsoft.esj.invoice.Line;
import de.bsnsoft.esj.invoice.Party;
import de.bsnsoft.esj.invoice.PaymentMeans;
import de.bsnsoft.esj.invoice.PaymentTerms;
import de.bsnsoft.esj.invoice.Vat;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.invoice.code.Unit;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.pdf.EmbedOptions;
import de.bsnsoft.esj.pdf.EmbedResult;
import de.bsnsoft.esj.pdf.EmbeddedFile;
import de.bsnsoft.esj.pdf.FacturX;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.pdf.PdfContainer;
import de.bsnsoft.esj.pdf.PdfInvoiceImporter;
import de.bsnsoft.esj.render.PdfRenderer;
import de.bsnsoft.esj.render.RenderLanguage;
import de.bsnsoft.esj.render.RenderOptions;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.en16931.En16931;
import de.bsnsoft.esj.syntax.SyntaxReport;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import de.bsnsoft.esj.typed.build.BuildException;
import de.bsnsoft.esj.typed.build.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Invoice data to a hybrid PDF and back, and the three places the business rules are checked.
 *
 * <p>The program writes {@code invoice.pdf} and {@code invoice.esj.json} into the directory
 * named as its first argument, or into the working directory when it is given none.
 * {@code examples/README.md} has the command line that runs it against the built modules.
 */
public final class HybridInvoice {

    private HybridInvoice() {
    }

    /**
     * Runs the five steps and prints what each of them found.
     *
     * @param args the directory the two files are written into, optional
     * @throws IOException if one of the two files cannot be written
     */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : ".");

        // 1. The invoice in the words of the domain. build() derives the line amounts, the
        // VAT breakdown and the totals, and refuses what EN 16931 does not accept.
        SemanticDocument invoice = Invoice.create(Profile.EN16931)
                .number("RE-2026-0042")
                .issued(LocalDate.of(2026, 5, 12))
                .currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address("Musterweg 12", "10117", "Beispielstadt", Country.DE))
                .buyer(Party.named("Muster AG").vatId("DE987654321")
                        .address("Beispielallee 3", "20095", "Musterstadt", Country.DE))
                .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                        PaymentTerms.days(30, "Payable within 30 days without deduction."))
                .line(Line.of("Sensor module SM-100").quantity(100, Unit.PIECE)
                        .unitPrice("12").vat(Vat.standard(19)))
                .line(Line.of("Mounting kit MK-12").quantity(20, Unit.PIECE)
                        .unitPrice("4.50").vat(Vat.standard(19)))
                .build();

        // 2. The pages a person reads: a PDF/A-3b file, drawn from the document alone.
        byte[] pages = new PdfRenderer().render(invoice, RenderOptions.in(RenderLanguage.ENGLISH));

        // 3. The invoice embedded as Factur-X, and beside it the same invoice as an ESJ
        // document, so that the file carries two attachments. The notes of the write report
        // name every term the cross industry invoice has no place for.
        EmbedResult hybrid = FacturX.embedWithReport(pages, invoice,
                EmbedOptions.of(FacturXProfile.EN_16931));
        hybrid.report().notes().forEach(note -> System.out.println("write note: " + note));
        Files.write(out.resolve("invoice.pdf"), hybrid.pdf());
        Files.write(out.resolve("invoice.esj.json"), EsjWriter.pretty().toBytes(invoice));

        // 4. Read the file back. The container says what it carries, and the semantic digest
        // of the document that comes out of it is the digest of the document that went in.
        try (PdfContainer container = PdfContainer.open(hybrid.pdf())) {
            for (EmbeddedFile file : container.embeddedFiles()) {
                System.out.println("attachment: " + file.name()
                        + " (" + file.declaredMediaType().orElse("no media type")
                        + ", " + file.associatedRelationship().orElse("not associated") + ")");
            }
        }
        SemanticDocument read = PdfInvoiceImporter.importPdf(hybrid.pdf()).document();
        System.out.println("digest written: " + Canonicalizer.semanticDigest(invoice));
        System.out.println("digest read:    " + Canonicalizer.semanticDigest(read));

        // 5a. The first of the three places the business rules are checked: build(), which
        // refuses. This seller states no VAT identifier and the line is standard rated.
        try {
            Invoice.create(Profile.EN16931)
                    .number("RE-2026-0043")
                    .issued(LocalDate.of(2026, 5, 12))
                    .currency(CurrencyCode.EUR)
                    .seller(Party.named("Example GmbH")
                            .address("Musterweg 12", "10117", "Beispielstadt", Country.DE))
                    .buyer(Party.named("Muster AG").vatId("DE987654321")
                            .address("Beispielallee 3", "20095", "Musterstadt", Country.DE))
                    .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                            PaymentTerms.days(30, "Payable within 30 days without deduction."))
                    .line(Line.of("Sensor module SM-100").quantity(100, Unit.PIECE)
                            .unitPrice("12").vat(Vat.standard(19)))
                    .build();
        } catch (BuildException refused) {
            refused.report().lines().forEach(line -> System.out.println("refused: " + line));
        }

        // 5b. The second: the rule pack over any document, whatever syntax it arrived in.
        List<RuleFinding> findings = En16931.engine(Registry.en16931()).evaluate(invoice);
        System.out.println("rule findings: " + findings.size());

        // 5c. The third: the official XSD and Schematron over the very bytes that are sent.
        SyntaxReport official = SyntaxValidator.validate(CiiWriter.write(invoice));
        official.ran().forEach(run -> System.out.println("ran: " + run.component()));
        System.out.println("official verdict: " + official.verdict());

        // The record of all this for an audit is one command over the file just written:
        //   esj validate invoice.pdf --report proof.pdf
    }
}
