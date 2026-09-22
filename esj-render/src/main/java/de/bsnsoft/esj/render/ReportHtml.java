package de.bsnsoft.esj.render;

import de.bsnsoft.esj.report.Text;
import de.bsnsoft.esj.report.ValidationOutcome;
import java.util.List;
import java.util.Locale;

/**
 * The validation report as one HTML page.
 *
 * <p>One file and nothing beside it. The style sheet is inline, there is no script, no
 * font, no image and no link to anything that would have to be fetched, so the page opens
 * from a directory, from an attachment and from a cold archive years from now. That is
 * what a proof about an invoice has to survive.
 *
 * <h2>The invoice stands in a frame of its own</h2>
 *
 * <p>The rendered invoice is the page {@link HtmlRenderer} produces, and that page is a
 * whole HTML document with a style sheet of its own — one that styles {@code html},
 * {@code body} and {@code *}. Pasting its body into this one would let it decide what the
 * report around it looks like, and a newer release of the vendored stylesheet would decide
 * it differently. So it goes into an {@code <iframe srcdoc="…">}: the file stays one file,
 * the two style sheets cannot reach each other, and nothing is fetched when the page is
 * opened.
 *
 * <p>The frame is sandboxed with {@code allow-scripts} and without
 * {@code allow-same-origin}. The vendored visualization needs its script to show four of
 * its five tabs, so a frame that ran nothing would hide most of the invoice; a frame
 * without {@code allow-same-origin} runs it in an origin of its own, where it can reach
 * neither this page nor anything the reader is signed in to. The two scripts in there are
 * the ones the stylesheet inlines and no others, whatever the invoice says — which is what
 * {@code UntrustedContentTest} is about.
 *
 * <p>What a frame costs is printing: a browser prints the box the frame occupies and not
 * the document inside it, so a printed page carries the report in full and the invoice
 * only as far as the frame shows it. The PDF form of the report is the one that prints the
 * invoice, as pages. The sentence under the heading of the invoice says so on the page
 * itself, so that the reader who would be cut short is the reader who is told;
 * {@code docs/cli.md}, "The report", is where it is written down.
 *
 * <h2>Untrusted content</h2>
 *
 * <p>Everything that came out of the run — the name of the input, a message of a rule set,
 * the place it names — is content of a document somebody else wrote or of a command line
 * somebody else typed. Every one of them goes through {@link #escape(String)} before it
 * reaches the page, so a {@code <script>} inside an invoice text is five characters a
 * reader sees. {@link ReportContent} takes the characters that direct the reading order
 * out first, so a line cannot be made to read differently from the finding it stands for.
 */
final class ReportHtml {

    /** The style sheet of the report, inline because the page carries no second file. */
    private static final String STYLE = """
            :root {
              --ink: #16191d; --soft: #5b6470; --rule: #d7dce2; --panel: #f6f8fa;
              --ok: #1f7a4d; --bad: #b3261e; --warn: #8a5a00; --none: #5b6470;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0; padding: 2rem 1rem; background: #fff; color: var(--ink);
              font: 15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
            }
            main { max-width: 60rem; margin: 0 auto; }
            h1 { font-size: 1.4rem; margin: 0 0 1.2rem; }
            h2 { font-size: 1.05rem; margin: 2rem 0 .6rem; }
            h3 { font-size: .95rem; margin: 1.2rem 0 .4rem; color: var(--soft); }
            .card { border: 1px solid var(--rule); border-radius: 6px; padding: 1rem 1.2rem; }
            .verdict { display: flex; align-items: baseline; gap: .8rem; flex-wrap: wrap; }
            .verdict b { font-size: 1.5rem; letter-spacing: .04em; }
            .verdict-VALID b { color: var(--ok); }
            .verdict-INVALID b { color: var(--bad); }
            .verdict-INDETERMINATE b { color: var(--warn); }
            .verdict-NONE b { color: var(--none); }
            .verdict span { color: var(--soft); }
            .subjects { margin: .4rem 0 0; }
            .subjects span { color: var(--soft); }
            dl { display: grid; grid-template-columns: max-content 1fr; gap: .25rem .9rem;
                 margin: 1rem 0 0; }
            dt { color: var(--soft); }
            dd { margin: 0; overflow-wrap: anywhere; }
            table { border-collapse: collapse; width: 100%; }
            th, td { text-align: left; padding: .3rem .6rem .3rem 0;
                     border-bottom: 1px solid var(--rule); vertical-align: top; }
            tr.block th { background: var(--panel); padding-left: .6rem; }
            td.status { width: 40%; }
            .finding { border-left: 3px solid var(--rule); padding: .1rem 0 .1rem .8rem;
                       margin: .8rem 0; break-inside: avoid; }
            .finding-error { border-left-color: var(--bad); }
            .finding-warning { border-left-color: var(--warn); }
            .finding-head { font-weight: 600; margin: 0; }
            .finding-where, .mono { font-family: ui-monospace, SFMono-Regular, Menlo,
                                    Consolas, monospace; font-size: .85em; }
            .finding-where { color: var(--soft); margin: .15rem 0; overflow-wrap: anywhere; }
            .finding-message { margin: .15rem 0; overflow-wrap: anywhere; }
            .note { color: var(--soft); margin: .2rem 0 .8rem; }
            ul { margin: .2rem 0; padding-left: 1.2rem; }
            iframe { width: 100%; height: 1400px; border: 1px solid var(--rule);
                     border-radius: 6px; background: #fff; }
            @media print {
              body { padding: 0; font-size: 11pt; }
              .card, .finding, section { break-inside: avoid; }
              iframe { height: 24cm; page-break-before: always; }
            }
            """;

    private final ValidationOutcome outcome;
    private final ReportOptions options;
    private final RenderLanguage language;
    private final StringBuilder out = new StringBuilder();

    private ReportHtml(ValidationOutcome outcome, ReportOptions options) {
        this.outcome = outcome;
        this.options = options;
        this.language = options.language();
    }

    /**
     * Writes a report as one HTML page.
     *
     * @param outcome what the run came to
     * @param options the language, and the moment the caller passed
     * @param invoice what this report says about the invoice: the rendering for the
     *                frame, or why there is none
     * @return the page
     */
    static String of(ValidationOutcome outcome, ReportOptions options,
                     ReportInvoice invoice) {
        ReportHtml page = new ReportHtml(outcome, options);
        page.write(invoice);
        return page.out.toString();
    }

    private void write(ReportInvoice invoice) {
        String title = ReportWord.TITLE.in(language) + " — "
                + ReportContent.cut(ReportContent.plain(outcome.identity().input()), language);
        out.append("<!DOCTYPE html>\n<html lang=\"").append(language.code())
                .append("\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width,")
                .append(" initial-scale=1\">\n<title>").append(escape(title))
                .append("</title>\n<style>\n").append(STYLE).append("</style>\n</head>\n")
                .append("<body>\n<main>\n<h1>").append(escape(ReportWord.TITLE.in(language)))
                .append("</h1>\n");
        verdict();
        checks();
        findings();
        provenance();
        invoice(invoice);
        out.append("</main>\n</body>\n</html>\n");
    }

    /** Writes the card a reader looks at first: the verdict, and what was judged. */
    private void verdict() {
        String word = ReportContent.verdict(outcome);
        String kind = outcome.verdict().map(Enum::name).orElse("NONE");
        out.append("<section class=\"card\">\n<p class=\"verdict verdict-").append(kind)
                .append("\"><span>").append(escape(ReportWord.VERDICT.in(language)))
                .append("</span> <b>").append(escape(word)).append("</b>");
        outcome.detail().ifPresent(detail -> out.append(" <span>")
                .append(escape(ReportContent.resolve(detail, language))).append("</span>"));
        out.append("</p>\n");
        subjects();
        out.append("<dl>\n");
        for (ReportContent.Line line : ReportContent.identity(outcome, options)) {
            out.append("<dt>").append(escape(line.label())).append("</dt><dd class=\"mono\">")
                    .append(escape(line.value())).append("</dd>\n");
        }
        out.append("</dl>\n</section>\n");
    }

    /**
     * Writes the verdicts of the subjects a run judged apart, under the one word.
     *
     * <p>A container and the invoice inside it are two questions and the run answered
     * both. The headline is the answer a pipeline branched on; these two lines are what
     * keep a reader from booking a conformant container as a bad invoice, or a sound
     * invoice as the reason a file was rejected.
     */
    private void subjects() {
        List<ReportContent.Line> lines = ReportContent.subjects(outcome, language);
        if (lines.isEmpty()) {
            return;
        }
        out.append("<p class=\"subjects\">");
        boolean first = true;
        for (ReportContent.Line line : lines) {
            if (!first) {
                out.append("<br>");
            }
            out.append("<span>").append(escape(line.label())).append(":</span> ")
                    .append(escape(line.value()));
            first = false;
        }
        out.append("</p>\n");
    }

    /** Writes the check table: one group of rows per engine that had something to say. */
    private void checks() {
        out.append("<section>\n<h2>").append(escape(ReportWord.CHECKS.in(language)))
                .append("</h2>\n<table>\n");
        for (ValidationOutcome.Block block : outcome.blocks()) {
            out.append("<tr class=\"block\"><th colspan=\"2\">")
                    .append(escape(ReportContent.heading(block.kind(), language)))
                    .append("</th></tr>\n");
            for (ValidationOutcome.Row row : block.rows()) {
                out.append("<tr><td>")
                        .append(escape(ReportContent.resolve(row.label(), language)))
                        .append("</td><td class=\"status\">")
                        .append(escape(ReportContent.status(row, language)))
                        .append("</td></tr>\n");
            }
        }
        out.append("</table>\n</section>\n");
    }

    /** Writes the findings, under the heading of the engine that produced each. */
    private void findings() {
        out.append("<section>\n<h2>").append(escape(ReportWord.FINDINGS.in(language)))
                .append("</h2>\n");
        if (outcome.findings().isEmpty()) {
            out.append("<p class=\"note\">")
                    .append(escape(ReportWord.NOTHING_FOUND.in(language)))
                    .append("</p>\n</section>\n");
            return;
        }
        for (ValidationOutcome.Block block : outcome.blocks()) {
            if (block.findings().isEmpty()) {
                continue;
            }
            out.append("<h3>").append(escape(ReportContent.heading(block.kind(), language)))
                    .append("</h3>\n");
            for (ValidationOutcome.Finding finding : ReportContent.findings(block)) {
                finding(finding);
            }
            for (String note : ReportContent.notes(block, language)) {
                out.append("<p class=\"note\">").append(escape(note)).append("</p>\n");
            }
        }
        out.append("</section>\n");
    }

    private void finding(ValidationOutcome.Finding finding) {
        out.append("<article class=\"finding finding-")
                .append(finding.severity().token().toLowerCase(Locale.ROOT))
                .append("\">\n<p class=\"finding-head\">")
                .append(escape(ReportContent.head(finding, language))).append("</p>\n");
        String where = ReportContent.where(finding, language);
        if (!where.isEmpty()) {
            out.append("<p class=\"finding-where\">").append(escape(where)).append("</p>\n");
        }
        out.append("<p class=\"finding-message\">")
                .append(escape(ReportContent.message(finding, language)))
                .append("</p>\n</article>\n");
    }

    /** Writes what was done to the bytes before they were read, where anything was. */
    private void provenance() {
        List<Text> notes = outcome.provenance();
        if (notes.isEmpty()) {
            return;
        }
        out.append("<section>\n<h2>").append(escape(ReportWord.PROVENANCE.in(language)))
                .append("</h2>\n<ul>\n");
        for (Text note : notes) {
            out.append("<li>").append(escape(ReportContent.resolve(note, language)))
                    .append("</li>\n");
        }
        out.append("</ul>\n</section>\n");
    }

    /** Writes the rendered invoice, or the sentence that says why it is not here. */
    private void invoice(ReportInvoice invoice) {
        if (!invoice.present()) {
            return;
        }
        out.append("<section>\n<h2>").append(escape(ReportWord.INVOICE.in(language)))
                .append("</h2>\n");
        if (invoice.refusal().isPresent()) {
            note(ReportWord.INVOICE_REFUSED.in(language) + " "
                    + ReportContent.plain(invoice.refusal().orElseThrow()));
            out.append("</section>\n");
            return;
        }
        if (invoice.oversize().isPresent()) {
            note(ReportWord.INVOICE_TOO_LARGE.in(language, List.of(
                    Integer.toString(invoice.oversize().orElseThrow()))));
            out.append("</section>\n");
            return;
        }
        if (invoice.oversizeValues().isPresent()) {
            note(ReportWord.INVOICE_TOO_LARGE_DOCUMENT.in(language, List.of(
                    Integer.toString(invoice.oversizeValues().orElseThrow()))));
            out.append("</section>\n");
            return;
        }
        note(ReportWord.INVOICE_FRAME.in(language));
        exportNotes(invoice.notes());
        out.append("<iframe sandbox=\"allow-scripts\" title=\"")
                .append(escape(ReportWord.INVOICE.in(language))).append("\" srcdoc=\"")
                .append(escape(invoice.rendering().orElseThrow()))
                .append("\"></iframe>\n</section>\n");
    }

    /**
     * Writes what did not reach the rendering below, where anything did not.
     *
     * <p>The rendering goes through a representation that carries the business terms of
     * the semantic model and nothing else, so a value it has no place for is a value the
     * frame does not show. A report whose invoice is incomplete is still a report; one
     * that shows an incomplete invoice under the digest of the whole document, and says
     * nothing, is not.
     */
    private void exportNotes(List<String> notes) {
        if (notes.isEmpty()) {
            return;
        }
        note((notes.size() == 1 ? ReportWord.EXPORT_NOTE : ReportWord.EXPORT_NOTES)
                .in(language, List.of(Integer.toString(notes.size()))));
        out.append("<ul>\n");
        for (String line : ReportContent.exportNotes(notes, language)) {
            out.append("<li>").append(escape(line)).append("</li>\n");
        }
        out.append("</ul>\n");
    }

    /** Writes one sentence of this report's own, in the grey of a remark. */
    private void note(String sentence) {
        out.append("<p class=\"note\">").append(escape(sentence)).append("</p>\n");
    }

    /**
     * Returns a text in the form the page may carry it, in an element and in an attribute
     * alike.
     *
     * <p>The five characters are escaped together and always, rather than one set for text
     * and another for an attribute: the whole of the rendered invoice is written into the
     * {@code srcdoc} attribute of the frame, so an escaping that was right in one place and
     * short in the other would be a defect that only a crafted invoice finds.
     *
     * @param text the text
     * @return the text with {@code & < > " '} replaced by their references
     */
    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
