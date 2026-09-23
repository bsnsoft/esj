package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.report.ValidationOutcome;
import de.bsnsoft.esj.validate.Severity;
import de.bsnsoft.esj.validate.ValidationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What a validation report has to carry, in both of its forms.
 *
 * <p>Three questions are asked of every report here. Does it say everything the run said —
 * every finding code, the verdict, the packs that judged the document? Does it say the same
 * thing in both forms, so that the reader of the PDF and the reader of the page are looking
 * at the same run? And is it inert: an invoice is a document a stranger wrote, and a report
 * about one is opened by somebody who trusts the report.
 *
 * <p>{@link ReportDeterminismTest} asks the fourth question, which is whether two runs of
 * the same report give the same bytes.
 */
class ReportRendererTest {

    private final ReportRenderer renderer = new ReportRenderer();

    /** Every run the tests are written against, with the document each is about. */
    private static List<Run> runs() {
        return List.of(new Run("valid", Outcomes.valid(), Outcomes.instance()),
                new Run("mutated", Outcomes.mutated(), Outcomes.instance()),
                new Run("esj", Outcomes.esj(), Corpus.example("standard-invoice")),
                new Run("pdf", Outcomes.pdf(), Corpus.example("minimal")),
                new Run("minimum", Outcomes.minimum(), Corpus.example("minimal")));
    }

    /**
     * A report that left a finding out would be worse than no report: it is the file a
     * reader keeps instead of the run.
     */
    @Test
    void bothFormsCarryEveryFindingAndTheVerdict() {
        for (Run run : runs()) {
            String page = renderer.html(run.outcome(), run.document(), ReportOptions.defaults());
            String pdf = Pdf.text(renderer.pdf(run.outcome(), run.document(),
                    ReportOptions.defaults()));
            for (ValidationOutcome.Finding finding : run.outcome().findings()) {
                assertTrue(page.contains(finding.code()),
                        run.name() + ": the page carries " + finding.code());
                assertTrue(Pdf.shows(pdf, finding.code()),
                        run.name() + ": the PDF carries " + finding.code());
                assertTrue(Pdf.shows(pdf, finding.message()),
                        run.name() + ": the PDF carries the message of " + finding.code());
            }
            String verdict = ReportContent.verdict(run.outcome());
            assertTrue(page.contains(verdict), run.name() + ": the page carries the verdict");
            assertTrue(Pdf.shows(pdf, verdict), run.name() + ": the PDF carries the verdict");
        }
    }

    /**
     * A report names the released rules that judged the document, because a rule is a rule
     * of a release and a report is read after the release was replaced.
     */
    @Test
    void bothFormsNameEveryPackWithItsVersion() {
        Run run = runs().get(3);
        String page = renderer.html(run.outcome(), run.document(), ReportOptions.defaults());
        String pdf = Pdf.text(renderer.pdf(run.outcome(), run.document(),
                ReportOptions.defaults()));

        for (String pack : ReportContent.packs(run.outcome().identity())) {
            assertTrue(page.contains(ReportHtml.escape(pack)), "the page names " + pack);
            assertTrue(Pdf.shows(pdf, pack), "the PDF names " + pack);
        }
    }

    /**
     * Every row of the check table is in both forms with what it came to, so that a check
     * that did not run is never read as one that passed.
     */
    @Test
    void bothFormsCarryEveryRowOfTheCheckTable() {
        Run run = runs().get(3);
        String page = renderer.html(run.outcome(), run.document(), ReportOptions.in(
                RenderLanguage.ENGLISH));
        String pdf = Pdf.text(renderer.pdf(run.outcome(), run.document(), ReportOptions.in(
                RenderLanguage.ENGLISH)));

        for (ValidationOutcome.Block block : run.outcome().blocks()) {
            for (ValidationOutcome.Row row : block.rows()) {
                String label = ReportContent.resolve(row.label(), RenderLanguage.ENGLISH);
                assertTrue(page.contains(ReportHtml.escape(label)),
                        "the page carries the row " + label);
                assertTrue(Pdf.shows(pdf, label), "the PDF carries the row " + label);
            }
        }
        assertTrue(page.contains("declared, not validated"),
                "the PDF/A row says what it says everywhere: " + page);
        assertTrue(Pdf.shows(pdf, "declared, not validated"), pdf);
    }

    /**
     * The invoice reaches the page as data and never as markup.
     *
     * <p>The whole rendered invoice is written into one attribute, so a page that escaped
     * it short would hand a reader the invoice's markup as markup. The assertion is the
     * blunt one: a report carries no {@code <script} at all, whatever the invoice says and
     * whatever the vendored stylesheet inlines, because everything that came from the
     * document went through one escape.
     */
    @Test
    void theInvoiceIsWrittenIntoThePageAsTextAndNotAsMarkup() {
        SemanticDocument hostile = Corpus.example("standard-invoice").toBuilder()
                .set(SemanticPath.of("/BG-25/0/BG-31/BT-153"), SemanticValue.of(
                        "<script>alert('x')</script> & \"quoted\" </iframe>"))
                .build();

        String page = renderer.html(Outcomes.valid(), hostile, ReportOptions.defaults());

        assertFalse(page.contains("<script"),
                "neither the invoice's own scripts nor one of its values is markup here");
        assertEquals(1, Html.count(page, "<iframe"), "one frame, and the invoice is in it");
        assertTrue(page.contains("sandbox=\"allow-scripts\""),
                "the frame runs the visualization in an origin of its own");
        String framed = unescaped(srcdoc(page));
        assertEquals(new HtmlRenderer().render(hostile, RenderOptions.defaults()), framed,
                "the attribute carries the rendering unchanged, once it is decoded");
        assertTrue(framed.contains("&lt;script&gt;alert("),
                "and inside the frame the value is text as well");
        assertEquals(2, Html.count(framed, "<script"),
                "the only scripts in there are the two the vendored stylesheet inlines");
    }

    /**
     * Returns the {@code srcdoc} attribute of the one frame of a page.
     *
     * <p>The value is read to the first quotation mark after it, which is the closing one
     * precisely because every quotation mark of the rendering inside it was escaped. A page
     * that escaped them short would end the attribute early and this would read a fragment.
     */
    private static String srcdoc(String page) {
        int at = page.indexOf("srcdoc=\"");
        assertTrue(at > 0, "the page carries a frame");
        int from = at + "srcdoc=\"".length();
        return page.substring(from, page.indexOf('"', from));
    }

    /** Resolves the five character references {@link ReportHtml#escape(String)} writes. */
    private static String unescaped(String attribute) {
        return attribute.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&");
    }

    /**
     * A report about a document this build cannot render is still a report. It is the case
     * a report is most needed in, so the verdict and the findings stand and the sentence
     * that says what refused the invoice stands in its place.
     */
    @Test
    void aDocumentNoRendererWillTakeStillGetsAReport() {
        SemanticDocument future = SemanticDocument.builder()
                .semanticModel("EN16931-1:2099")
                .put("/BT-1", "RE-2099-0001")
                .build();

        String page = renderer.html(Outcomes.mutated(), future, ReportOptions.in(
                RenderLanguage.ENGLISH));
        String pdf = Pdf.text(renderer.pdf(Outcomes.mutated(), future, ReportOptions.in(
                RenderLanguage.ENGLISH)));

        assertTrue(page.contains("The invoice could not be rendered:"), page);
        assertTrue(page.contains("EN16931-1:2099"), page);
        assertTrue(Pdf.shows(pdf, "The invoice could not be rendered:"), pdf);
        assertTrue(page.contains("BR-CO-10") && Pdf.shows(pdf, "BR-CO-10"),
                "the findings are unaffected: they were decided before the renderer ran");
        assertFalse(page.contains("<iframe"), "there is no invoice to frame");
    }

    /** A caller that keeps the invoice elsewhere asks for the report alone. */
    @Test
    void withoutTheInvoiceNeitherFormCarriesIt() {
        ReportOptions alone = ReportOptions.defaults().withInvoice(false);

        String page = renderer.html(Outcomes.valid(), Outcomes.instance(), alone);
        byte[] pdf = renderer.pdf(Outcomes.valid(), Outcomes.instance(), alone);

        assertFalse(page.contains("<iframe"), page);
        assertFalse(Pdf.shows(pdf, "Rechnungspositionen"), Pdf.text(pdf));
        assertTrue(Pdf.shows(pdf, "VALID"), Pdf.text(pdf));
    }

    /** A run that built no document at all is a run a report can still be written from. */
    @Test
    void aRunWithoutADocumentGetsAReportWithoutAnInvoice() {
        String page = renderer.html(Outcomes.valid(), null, ReportOptions.defaults());
        byte[] pdf = renderer.pdf(Outcomes.valid(), null, ReportOptions.defaults());

        assertFalse(page.contains("<iframe"), page);
        assertTrue(page.contains("VALID"), page);
        assertTrue(Pdf.shows(pdf, "VALID"), Pdf.text(pdf));
    }

    /**
     * The PDF carries the report first and the invoice after it, as pages of one file, so
     * that a printed report is the whole of both.
     */
    @Test
    void theInvoiceFollowsTheReportInThePdfAsPages() {
        byte[] alone = renderer.pdf(Outcomes.valid(), Outcomes.instance(),
                ReportOptions.defaults().withInvoice(false));
        byte[] whole = renderer.pdf(Outcomes.valid(), Outcomes.instance(),
                ReportOptions.defaults());

        assertTrue(Pdf.pages(whole) > Pdf.pages(alone),
                "the invoice adds pages: " + Pdf.pages(alone) + " and " + Pdf.pages(whole));
        assertTrue(Pdf.shows(Pdf.textOfPage(whole, 1), "Prüfbericht"),
                "the report is first: " + Pdf.textOfPage(whole, 1));
        assertTrue(Pdf.shows(whole, "Rechnungspositionen"),
                "and the invoice is in the same file");
    }

    /**
     * The invoice of a report is drawn in the generic layout, and not in the letter a
     * rendering is drawn in without asking: a report is a proof about the document, and the
     * shape of the semantic model is the picture of one. The options the invoice is rendered
     * with name that layout, and its pages carry the block of parties the letter has none of.
     */
    @Test
    void theInvoiceOfAReportIsDrawnInTheGenericLayout() {
        String parties = Word.SELLER.in(RenderLanguage.GERMAN) + " ";
        String alone = Pdf.flat(renderer.pdf(Outcomes.valid(), Outcomes.instance(),
                ReportOptions.defaults().withInvoice(false)));
        String whole = Pdf.flat(renderer.pdf(Outcomes.valid(), Outcomes.instance(),
                ReportOptions.defaults()));

        assertEquals(Optional.of(Layout.GENERIC), ReportOptions.defaults().rendering().layout(),
                "the invoice of a report is rendered with the generic layout named");
        assertFalse(alone.contains(parties), "the report itself has no block of parties");
        assertTrue(whole.contains(parties),
                "and the invoice after it is the generic layout, which has one");
    }

    /** The moment on the page is the caller's string, printed as it was given. */
    @Test
    void theMomentIsPrintedExactlyAsTheCallerWroteIt() {
        ReportOptions dated = ReportOptions.defaults().at("2026-09-21T08:15:00+02:00");

        String page = renderer.html(Outcomes.valid(), null, dated);

        assertTrue(page.contains("2026-09-21T08:15:00+02:00"), page);
        assertFalse(renderer.html(Outcomes.valid(), null, ReportOptions.defaults())
                .contains("2026-09-21"), "a report that names no moment carries none");
    }

    /**
     * A run over a container answered two questions, and the file a reader keeps carries
     * both answers.
     *
     * <p>The headline of a conformant MINIMUM container is {@code INVALID}, because the
     * tool was asked about an EN 16931 invoice and the file is not one. A reader holding
     * that word alone, over a check table in which every row is {@code OK} and nothing was
     * found, would book a file nothing is wrong with as a bad invoice.
     */
    @Test
    void bothFormsCarryTheVerdictOfEachSubject() {
        ValidationOutcome outcome = Outcomes.minimum();

        String page = renderer.html(outcome, null, ReportOptions.in(RenderLanguage.ENGLISH));
        String pdf = Pdf.text(renderer.pdf(outcome, null,
                ReportOptions.in(RenderLanguage.ENGLISH)));

        for (String line : new String[] {"Container", "OK", "Invoice", "NOT CHECKED",
                "profile MINIMUM"}) {
            assertTrue(page.contains(ReportHtml.escape(line)),
                    "the page says " + line + ": " + page);
            assertTrue(Pdf.shows(pdf, line), "the PDF says " + line + ": " + pdf);
        }
    }

    /**
     * Where the specification of a document levelled a finding of an artefact down, both
     * levels are on the line: the one that decided the verdict and the one the artefact
     * itself wrote. One level alone hides that the two publishers of the rule disagree.
     */
    @Test
    void aRelevelledFindingCarriesBothLevels() {
        ValidationOutcome outcome = Outcomes.mutated();

        String page = renderer.html(outcome, null, ReportOptions.in(RenderLanguage.ENGLISH));
        String pdf = Pdf.text(renderer.pdf(outcome, null,
                ReportOptions.in(RenderLanguage.ENGLISH)));

        assertTrue(page.contains("BR-CL-13 [info, flagged fatal by the artefact]"), page);
        assertTrue(Pdf.shows(pdf, "BR-CL-13 [info, flagged fatal by the artefact]"), pdf);
    }

    /**
     * Two engines that reported one rule are two findings and stay two, and the page says
     * which identifiers those are — otherwise a reader counting articles counts a defect
     * twice.
     */
    @Test
    void theRulesBothEnginesReportedAreNamed() {
        String page = renderer.html(Outcomes.mutated(), null,
                ReportOptions.in(RenderLanguage.ENGLISH));
        String pdf = Pdf.text(renderer.pdf(Outcomes.mutated(), null,
                ReportOptions.in(RenderLanguage.ENGLISH)));

        assertTrue(page.contains("neither report is merged into the other: BR-CO-10,"
                + " BR-CL-13"), page);
        assertTrue(Pdf.shows(pdf, "BR-CO-10, BR-CL-13"), pdf);
    }

    /**
     * A German report is German. What a rule, a pack or an artefact of somebody else's
     * calls itself travels from the run and stands as it is; every sentence this project
     * writes — the label of a check row, the reason a layer did not run, the line under
     * the verdict — is written in the language the report is written in.
     */
    @Test
    void theGermanReportIsWrittenInGerman() {
        String page = renderer.html(Outcomes.mutated(), null, ReportOptions.defaults());

        for (String german : new String[] {"ESJ-Format (L1)", "Modell (L2)",
                "Kardinalität (L3)", "nicht anwendbar: die Eingabe kam als XML",
                "EN-16931-Geschäftsregeln (nativ, Paket en16931/1.3.16)",
                "vom Artefakt als fatal markiert"}) {
            assertTrue(page.contains(ReportHtml.escape(german)),
                    "the page says " + german + ": " + page);
        }
        assertFalse(page.contains("the input arrived as XML"),
                "and says nothing this project wrote in English: " + page);
        assertTrue(page.contains("[BR-CO-10]-Sum of Invoice line net amount"),
                "the message of an artefact is the artefact's and is not translated");
    }

    /**
     * A rendering the export could not carry whole is said to be one. A report that showed
     * an incomplete invoice under the digest of the whole document, and said nothing,
     * would be a proof about a document other than the one on the page.
     */
    @Test
    void thePageSaysWhatDidNotReachTheRenderedInvoice() {
        SemanticDocument document = Corpus.example("standard-invoice").toBuilder()
                .set(SemanticPath.of("/BG-4/BT-27"), SemanticValue.of("Seller\u0001Name"))
                .build();

        String page = renderer.html(Outcomes.valid(), document,
                ReportOptions.in(RenderLanguage.ENGLISH));
        String pdf = Pdf.text(renderer.pdf(Outcomes.valid(), document,
                ReportOptions.in(RenderLanguage.ENGLISH)));

        assertTrue(page.contains("1 value of the document did not reach this rendering"),
                "the page says that a value is missing from the frame: " + page);
        assertTrue(page.contains("BT-27"), "and where: " + page);
        assertTrue(Pdf.shows(pdf, "Name"),
                "the PDF form draws the document itself and shows it: " + pdf);
    }

    /**
     * A report is bounded. Nothing in a document bounds what it can produce, and the
     * report is written after the verdict is known: a finding list is cut with the
     * remainder named, and a message longer than the bound is cut with the characters
     * counted.
     */
    @Test
    void theFindingsAndTheirMessagesAreBounded() {
        List<ValidationOutcome.Finding> many = new ArrayList<>();
        for (int i = 0; i <= ReportContent.MAX_FINDINGS; i++) {
            many.add(new ValidationOutcome.Finding("EN-BR", "BR-TEST-" + i, Severity.ERROR,
                    "native", Optional.of("en16931/1.3.16"), List.of("/BT-1"),
                    "x".repeat(i == 0 ? ReportContent.MAX_TEXT_CHARACTERS + 500 : 10)));
        }
        ValidationOutcome outcome = new ValidationOutcome(Outcomes.valid().identity(),
                List.of(new ValidationOutcome.Block(
                        ValidationOutcome.Block.Kind.SEMANTIC, List.of(), many)),
                Optional.of(ValidationStatus.INVALID), Optional.empty(), List.of(),
                List.of());

        String page = renderer.html(outcome, null, ReportOptions.in(RenderLanguage.ENGLISH));

        assertTrue(page.contains("… and 1 more findings of this block"), page);
        assertFalse(page.contains("BR-TEST-" + ReportContent.MAX_FINDINGS),
                "the last finding is the one the bound cut");
        assertTrue(page.contains("(500 characters not shown)"),
                "and a message longer than the bound is cut, with the count");
    }

    /**
     * An invoice whose rendering would be larger than the page carries is left out of it,
     * with the sentence that says so and where to have it. The page writes the whole
     * rendering into one attribute, escaped character by character, so a report about a very
     * large invoice would otherwise be a file no reader opens.
     *
     * <p>The bound is applied to the document and not to the rendering, so the sentence
     * names what was measured: the values of the document. A document of forty lines
     * carrying sixty thousand characters apiece is large in the second of the two ways
     * {@link ReportContent#renderingCharacters} counts.
     */
    @Test
    void anInvoiceTooLargeForThePageIsLeftOutWithTheReasonNamed() {
        SemanticDocument.Builder builder = Corpus.example("standard-invoice").toBuilder();
        for (int line = 1; line <= 40; line++) {
            builder.set(SemanticPath.of("/BG-25/" + line + "/BT-126"),
                    SemanticValue.of(Integer.toString(line + 1)));
            builder.set(SemanticPath.of("/BG-25/" + line + "/BT-127"),
                    SemanticValue.of("y".repeat(60_000)));
            builder.set(SemanticPath.of("/BG-25/" + line + "/BT-131"),
                    SemanticValue.of("10.00"));
        }

        String page = renderer.html(Outcomes.valid(), builder.build(),
                ReportOptions.in(RenderLanguage.ENGLISH));

        assertFalse(page.contains("<iframe"), "the frame is not in the page");
        assertTrue(page.contains("The invoice is not in this file: the document carries"),
                page);
        assertTrue(page.length() < ReportContent.MAX_INVOICE_CHARACTERS,
                "and the report is a file a reader opens: " + page.length());
    }

    /**
     * The same bound over a document that is large in the other way: many values, each of
     * them short. Sixteen thousand invoice lines are about a hundred and eighty megabytes
     * of rendering, and a report that made that rendering in order to measure it exhausted
     * the heap the wrapper of this project ships and took a verdict down that had already
     * been reached. So the document is measured and nothing is rendered, and the sentence
     * names the number of values, which is the thing that was measured.
     */
    @Test
    void aDocumentOfManyValuesIsMeasuredRatherThanRendered() {
        SemanticDocument.Builder builder = Corpus.example("standard-invoice").toBuilder();
        for (int line = 1; line <= 2_000; line++) {
            builder.set(SemanticPath.of("/BG-25/" + line + "/BT-126"),
                    SemanticValue.of(Integer.toString(line + 1)));
            builder.set(SemanticPath.of("/BG-25/" + line + "/BT-131"),
                    SemanticValue.of("10.00"));
        }
        SemanticDocument document = builder.build();

        String page = renderer.html(Outcomes.valid(), document,
                ReportOptions.in(RenderLanguage.ENGLISH));

        assertFalse(page.contains("<iframe"), "the frame is not in the page");
        assertTrue(page.contains("the document carries " + document.values().size()
                + " values"), page);
        assertTrue(page.length() < ReportContent.MAX_INVOICE_CHARACTERS,
                "and the report is a file a reader opens: " + page.length());
    }

    /**
     * The PDF form has pages and no frame, so the same document is drawn rather than left
     * out: the bound of the HTML form is the one attribute it writes the whole rendering
     * into, and a bound that refused the PDF form as well would be refusing something that
     * works.
     */
    @Test
    void thePdfFormCarriesADocumentTheHtmlFormLeavesOut() {
        SemanticDocument.Builder builder = Corpus.example("standard-invoice").toBuilder();
        for (int line = 1; line <= 2_000; line++) {
            builder.set(SemanticPath.of("/BG-25/" + line + "/BT-126"),
                    SemanticValue.of(Integer.toString(line + 1)));
            builder.set(SemanticPath.of("/BG-25/" + line + "/BT-131"),
                    SemanticValue.of("10.00"));
        }

        byte[] pdf = renderer.pdf(Outcomes.valid(), builder.build(),
                ReportOptions.in(RenderLanguage.ENGLISH));

        assertTrue(pdf.length > 0, "the PDF report is written");
    }

    /**
     * What a bound cuts is decided by what a finding weighs and not by where it stands.
     * One block carries two engines: the importer's warnings about what it could not carry
     * stand in front of the rules that decided the verdict, and one ordinary invoice can
     * carry hundreds of them. A cut that took the first two hundred would hand a reader a
     * report saying {@code INVALID} in which no error is named at all.
     */
    @Test
    void theHeaviestFindingsAreTheOnesPrinted() {
        List<ValidationOutcome.Finding> many = new ArrayList<>();
        for (int line = 1; line <= 300; line++) {
            many.add(new ValidationOutcome.Finding("import", "COMPONENT_MISSING",
                    Severity.WARNING, "importer", Optional.empty(),
                    List.of("/BG-25/" + line + "/BG-31/BT-158"),
                    "the identifier carries no scheme the binding requires"));
        }
        for (String code : new String[] {"BR-CL-23", "BR-CO-10", "BR-S-08"}) {
            many.add(new ValidationOutcome.Finding("EN-BR", code, Severity.ERROR, "native",
                    Optional.of("en16931/1.3.16"), List.of("/BG-22/BT-106"),
                    "the sum does not add up"));
        }
        ValidationOutcome outcome = new ValidationOutcome(Outcomes.valid().identity(),
                List.of(new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SEMANTIC,
                        List.of(), many, List.of("BR-CO-10"))),
                Optional.of(ValidationStatus.INVALID), Optional.empty(), List.of(),
                List.of());

        String page = renderer.html(outcome, null, ReportOptions.in(RenderLanguage.ENGLISH));
        String pdf = Pdf.flat(renderer.pdf(outcome, null,
                ReportOptions.in(RenderLanguage.ENGLISH)));

        for (String code : new String[] {"BR-CL-23", "BR-CO-10", "BR-S-08"}) {
            assertTrue(page.contains(code), "the page names " + code);
            assertTrue(pdf.contains(code), "and so does the PDF form");
        }
        assertTrue(page.contains("… and 103 more findings of this block"), page);
        assertTrue(page.contains("The same rules were reported by an official artefact"),
                "the overlap sentence stands even where the cut took findings it names");
    }

    /**
     * The identity is bounded like everything else. Its values come out of the document —
     * the specification it names in BT-24, the name it was handed under — it is the block
     * a reader trusts most, and the PDF form repeats it; an element of a stranger's invoice
     * must not decide how long the report is or how long it takes to draw.
     */
    @Test
    void aValueOfTheDocumentCannotDecideHowLongTheIdentityIs() {
        String huge = "Z".repeat(400_000);
        ValidationOutcome.Identity identity = Outcomes.valid().identity();
        ValidationOutcome outcome = new ValidationOutcome(
                new ValidationOutcome.Identity(huge, identity.syntax(),
                        identity.reader(), identity.semanticModel(),
                        Optional.of(huge), identity.inputSha256(),
                        identity.semanticDigest(), identity.documentDigest(),
                        identity.sourceSha256(), identity.packs(), identity.tool()),
                List.of(), Optional.of(ValidationStatus.VALID), Optional.empty(), List.of(),
                List.of());

        String page = renderer.html(outcome, null, ReportOptions.in(RenderLanguage.ENGLISH));
        String pdf = Pdf.flat(renderer.pdf(outcome, null,
                ReportOptions.in(RenderLanguage.ENGLISH)));

        String cut = "(398000 characters not shown)";
        assertTrue(page.contains(cut), "the page cuts the profile and counts what it cut");
        assertTrue(pdf.contains(cut), "and so does the PDF form: " + pdf);
        assertFalse(page.contains("Z".repeat(ReportContent.MAX_TEXT_CHARACTERS + 1)),
                "and neither form carries more of it than the bound");
        assertTrue(page.indexOf("</title>") < 3 * ReportContent.MAX_TEXT_CHARACTERS,
                "the title of the page is the name of the input and is cut with it");
    }

    /**
     * The footer of every page of the PDF form names the input, and a path too long for the
     * footer is shortened in its middle instead of being written over the page count at
     * the right.
     *
     * <p>A caller names the input by a path, and a path is as long as a directory tree is
     * deep: one of a hundred and thirty characters was written over <i>Seite 1 von 3</i>.
     * The beginning of the path stays and so does the file name, an ellipsis stands for the
     * middle, and every page carries the same footer. The identity block of the report and
     * the HTML form name the path whole; a short one is written as it is.
     */
    @Test
    void aLongInputPathIsShortenedInTheFooterAndNeverReachesThePageCount() {
        String file = "/rechnung-RE-2026-0042_ubl.xml";
        StringBuilder directory = new StringBuilder("/srv/eingang");
        for (int level = 1; directory.length() < 200 - file.length(); level++) {
            directory.append("/ablage-ebene-").append(level);
        }
        String path = directory.substring(0, 200 - file.length()) + file;
        assertEquals(200, path.length(), "the path of the case is 200 characters long");
        ValidationOutcome outcome = withInput(Outcomes.valid(), path);

        byte[] pdf = renderer.pdf(outcome, Outcomes.instance(), ReportOptions.defaults());

        int pages = Pdf.pages(pdf);
        assertTrue(pages > 1, "the report and the invoice take more than one page");
        List<String> footers = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            Pdf.Run footer = null;
            Pdf.Run count = null;
            for (Pdf.Run run : Pdf.runs(pdf, page)) {
                if (run.text().startsWith("Prüfbericht — ")) {
                    footer = run;
                } else if (run.text().equals("Seite " + page + " von " + pages)) {
                    count = run;
                }
            }
            assertTrue(footer != null && count != null,
                    "page " + page + " carries the footer and the page count");
            assertTrue(footer.right() + Sheet.FOOTER_GAP - 0.5f <= count.left(),
                    "page " + page + ": the footer ends at " + footer.right()
                            + " and the page count begins at " + count.left());
            assertEquals(count.baseline(), footer.baseline(), 0.1f,
                    "and the two stand on one line of page " + page);
            footers.add(footer.text());
        }
        String footer = footers.get(0);
        assertTrue(footer.startsWith("Prüfbericht — /srv/eingang/ablage-ebene-1"),
                "the path keeps its beginning: " + footer);
        assertTrue(footer.endsWith(file), "and its file name: " + footer);
        assertTrue(footer.contains("\u2026"), "an ellipsis stands for the middle: " + footer);
        assertEquals(List.of(footer), footers.stream().distinct().toList(),
                "and every page carries the same footer");
        assertTrue(Pdf.shows(pdf, path), "the identity block names the path whole");
        assertTrue(renderer.html(outcome, Outcomes.instance(), ReportOptions.defaults())
                .contains(path), "and so does the HTML form");

        byte[] shortPath = renderer.pdf(Outcomes.valid(), null, ReportOptions.defaults());
        assertTrue(Pdf.flat(shortPath).contains("Prüfbericht — "
                        + Outcomes.valid().identity().input() + " Seite 1 von 1"),
                "a path that fits is written as it is: " + Pdf.flat(shortPath));
    }

    /** Returns a run's outcome under another name of its input. */
    private static ValidationOutcome withInput(ValidationOutcome outcome, String input) {
        ValidationOutcome.Identity identity = outcome.identity();
        return new ValidationOutcome(
                new ValidationOutcome.Identity(input, identity.syntax(), identity.reader(),
                        identity.semanticModel(), identity.profile(), identity.inputSha256(),
                        identity.semanticDigest(), identity.documentDigest(),
                        identity.sourceSha256(), identity.packs(), identity.tool()),
                outcome.blocks(), outcome.verdict(), outcome.detail(), outcome.subjects(),
                outcome.provenance());
    }

    /** One run, and the document it is about. */
    private record Run(String name, ValidationOutcome outcome, SemanticDocument document) {
    }
}
