package de.bsnsoft.esj.render.internal;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.internal.report.Text;
import de.bsnsoft.esj.internal.report.ValidationOutcome;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import de.bsnsoft.esj.render.RenderLanguage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The page {@code esj view} writes: one HTML file that shows an invoice as it came, as this
 * tool read it, and, where a run validated it, the report about it.
 *
 * <p>The page is the overview of a directory of files and carries none of them. The received
 * PDF and the letter rendered from the data stand beside it and are shown in frames, by the
 * viewer of the browser; the report is written by the code of the report, as sections of the
 * page; the HTML rendering is linked and never framed. What a reader looks for first is text
 * in the header — what the file is, what the invoice says, what the verdict is — so that it
 * is read without scrolling and without entering a frame, and can be selected and pasted.
 *
 * <h2>Inert</h2>
 *
 * <p>The page fetches nothing and runs nothing. Every reference is relative and names a
 * file of its own directory; the head carries a content security policy that allows no
 * script, no base and no form, so the page says what it does not do and the browser holds
 * it to it. Every text of the run and of the document goes through
 * {@link ReportContent#plain(String)}, which takes out the characters that direct the
 * reading order, and through {@link ReportHtml#escape(String)}; every file name in a
 * reference is percent-encoded. The same content gives the same string.
 *
 * <h2>One style sheet</h2>
 *
 * <p>{@link ReportHtml#STYLE} comes first and unchanged — the colours, the type, the card,
 * the table and the finding of the report — and the rules of the page after it, which add
 * what only the page has: its header, the band of views, the frames, the card of a dead end,
 * the footer and the printout. The page adds no colour of its own.
 */
public final class ViewPage {

    /** The heading level of the sections of the report on the page, under its h2. */
    private static final int REPORT_LEVEL = 3;

    /** The most candidates or notes shown open; more are folded under their count. */
    private static final int OPEN_ITEMS = 3;

    /** The rules of the page, after the report's. */
    private static final String STYLE = """
            :root { color-scheme: light; }
            body { padding: 0; }
            main { max-width: none; margin: 0; }
            a { color: var(--ink); }
            a:focus-visible, summary:focus-visible { outline: 2px solid var(--ink);
                                                     outline-offset: 2px; }
            .view { max-width: 110rem; margin: 0 auto; padding: 1.5rem; }
            .head { display: flex; flex-wrap: wrap; align-items: baseline;
                    justify-content: space-between; gap: .3rem 1.5rem; }
            .head h1 { margin: 0; overflow-wrap: anywhere; }
            .path { margin: .3rem 0 1.2rem; color: var(--soft); font-size: .85rem;
                    overflow-wrap: anywhere; }
            .path span { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
            .summary { display: grid; grid-template-columns: minmax(0, 1fr) minmax(20rem, 28rem);
                       gap: 1.5rem; align-items: start; }
            .facts p { margin: 0 0 .3rem; overflow-wrap: anywhere; }
            .facts .meta { color: var(--soft); }
            .facts .title { font-size: 1.1rem; font-weight: 600; margin: .8rem 0 .4rem; }
            .facts dl { grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr));
                        gap: .6rem 1.2rem; margin: .4rem 0 0; }
            .facts dl div { min-width: 0; }
            .facts dt { font-size: .8rem; }
            .facts .due dd { font-weight: 700; font-size: 1.25rem;
                             font-variant-numeric: tabular-nums; }
            .summary .card p { margin: .4rem 0 0; }
            .summary .card .note { font-size: .85rem; margin-bottom: 0; }
            .card.state { max-width: 60rem; border-left: 3px solid var(--warn); }
            .card.state.limit { border-left-color: var(--none); }
            .card.state p { margin: .4rem 0 0; }
            .state-head { font-size: 1.05rem; }
            .label { color: var(--soft); font-size: .8rem; margin: .8rem 0 0; }
            pre.message { margin: .2rem 0 0; padding: .6rem .8rem; background: var(--panel);
                          border-radius: 6px; white-space: pre-wrap; overflow-wrap: anywhere;
                          font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                          font-size: .85rem; }
            table.candidates { margin: .6rem 0; }
            table.candidates td { overflow-wrap: anywhere; }
            table.candidates .size { text-align: right; font-variant-numeric: tabular-nums; }
            code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                   font-size: .85em; }
            code.command { user-select: all; }
            details summary { cursor: pointer; }
            .band { display: grid; margin-top: 1.5rem; column-gap: 1.5rem;
                    grid-template-columns: repeat(auto-fit, minmax(min(100%, 34rem), 1fr)); }
            .band.one { max-width: 60rem; }
            .pane { display: grid; grid-template-rows: subgrid; grid-row: span 3;
                    scroll-margin-top: 1rem; min-width: 0; }
            .pane-head { display: flex; flex-wrap: wrap; align-items: baseline;
                         justify-content: space-between; gap: .3rem 1rem; }
            .pane-head h2 { margin: 1rem 0 .3rem; }
            .links { margin: 0; }
            .links a + a { margin-left: 1rem; }
            .caption p { margin: 0 0 .6rem; color: var(--soft); }
            .caption ul { color: var(--soft); margin: 0 0 .6rem; }
            .pane iframe { height: calc(100vh - 6rem); min-height: 28rem; }
            .report { max-width: 60rem; margin-top: 1rem; }
            .report > .pane-head h2 { margin-top: 2rem; }
            h4 { font-size: .9rem; margin: 1rem 0 .3rem; color: var(--soft); }
            .print-note { display: none; }
            footer { margin-top: 2.5rem; padding-top: .8rem; border-top: 1px solid var(--rule);
                     color: var(--soft); font-size: .85rem; }
            footer p { margin: .2rem 0; max-width: 60rem; overflow-wrap: anywhere; }
            footer span { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
            @media (max-width: 64rem) {
              .summary { grid-template-columns: minmax(0, 1fr); }
            }
            @media (max-width: 40rem) {
              .view { padding: 1rem; }
            }
            @media print {
              @page { margin: 15mm; }
              body { font-size: 10.5pt; }
              .view { padding: 0; max-width: none; }
              nav, .links, .band { display: none; }
              .print-note { display: block; }
              .card, .finding, tr, .facts { break-inside: avoid; }
            }
            """;

    private final Content content;
    private final RenderLanguage language;
    private final StringBuilder out = new StringBuilder();

    private ViewPage(Content content) {
        this.content = content;
        this.language = content.language;
    }

    /**
     * Writes the page.
     *
     * @param content what the run came to and which files it wrote beside the page
     * @return the page, a whole HTML document
     */
    public static String html(Content content) {
        Objects.requireNonNull(content, "content");
        content.check();
        ViewPage page = new ViewPage(content);
        page.write();
        return page.out.toString();
    }

    // ------------------------------------------------------------------- the page

    private void write() {
        out.append("<!DOCTYPE html>\n<html lang=\"").append(language.code())
                .append("\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width,")
                .append(" initial-scale=1\">\n")
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"")
                .append("script-src 'none'; base-uri 'none'; form-action 'none'\">\n")
                .append("<title>").append(escape(title())).append("</title>\n<style>\n")
                .append(ReportHtml.STYLE).append(STYLE).append("</style>\n</head>\n")
                .append("<body>\n<div class=\"view\">\n");
        header();
        out.append("<main>\n");
        band();
        printNote();
        report();
        out.append("</main>\n");
        footer();
        out.append("</div>\n</body>\n</html>\n");
    }

    /** Returns the title: the file, then what became of it, then the tool. */
    private String title() {
        return text(content.fileName) + " · " + stateWord() + " · esj";
    }

    /** Returns the verdict word, {@link ViewWord#NOT_CHECKED}, or the head of a dead end. */
    private String stateWord() {
        if (content.deadEnd != null) {
            String head = deadEndHead();
            return head.endsWith(".") ? head.substring(0, head.length() - 1) : head;
        }
        return content.outcome == null ? ViewWord.NOT_CHECKED.in(language)
                : ReportContent.verdict(content.outcome);
    }

    // ------------------------------------------------------------------- the header

    private void header() {
        out.append("<header>\n<div class=\"head\">\n<h1>").append(escape(text(content.fileName)))
                .append("</h1>\n");
        nav();
        out.append("</div>\n<p class=\"path\">").append(escape(ReportWord.INPUT.in(language)))
                .append(" <span>").append(escape(text(content.input))).append("</span></p>\n");
        if (content.deadEnd != null) {
            deadEnd();
        } else {
            out.append("<div class=\"summary\">\n");
            facts();
            verdict();
            out.append("</div>\n");
        }
        out.append("</header>\n");
    }

    /** Writes the list of the views the page has, as anchors; none where it has none. */
    private void nav() {
        List<String> links = new ArrayList<>();
        if (content.received != null) {
            links.add(anchor("#received", ViewWord.RECEIVED.in(language)));
        }
        if (hasRead()) {
            links.add(anchor("#read", ViewWord.READ.in(language)));
        }
        if (content.outcome != null) {
            links.add(anchor("#report", ReportWord.TITLE.in(language)));
        }
        if (links.isEmpty()) {
            return;
        }
        out.append("<nav aria-label=\"").append(escape(ViewWord.NAV.in(language))).append("\">")
                .append(String.join("<span aria-hidden=\"true\"> · </span>", links))
                .append("</nav>\n");
    }

    /** Writes what the file is and what the invoice says: T1 and T2. */
    private void facts() {
        out.append("<div class=\"facts\">\n");
        if (content.provenance != null) {
            out.append("<p><b>").append(escape(ViewWord.HEAD_EMBEDDED.in(language)))
                    .append("</b></p>\n<p>").append(escape(provenance())).append("</p>\n");
        }
        List<String> meta = new ArrayList<>();
        if (content.syntax != null) {
            meta.add(escape(ReportWord.SYNTAX.in(language)) + " "
                    + escape(ReportContent.resolve(content.syntax, language)));
        }
        if (content.profile != null) {
            meta.add(escape(ReportWord.PROFILE.in(language)) + " " + escape(text(content.profile)));
        }
        if (!meta.isEmpty()) {
            out.append("<p class=\"meta\">").append(String.join(" · ", meta)).append("</p>\n");
        }
        if (content.recoded != null) {
            out.append("<p class=\"meta\">").append(escape(ReportWord.PROVENANCE_RECODED.in(
                    language, List.of(text(content.recoded))))).append("</p>\n");
        }
        if (content.notEn16931 != null) {
            out.append("<p>").append(escape(ReportWord.DETAIL_NOT_EN16931.in(language,
                    List.of(text(content.notEn16931))))).append("</p>\n");
        }
        if (content.document != null) {
            Facts facts = new Facts(content.document, content.registry, language);
            out.append("<p class=\"title\">").append(escape(facts.title())).append("</p>\n")
                    .append("<dl>\n");
            fact(Word.SELLER.in(language), facts.seller(), false);
            fact(Word.BUYER.in(language), facts.buyer(), false);
            fact(facts.creditNote() ? Word.AMOUNT_CREDITED.in(language)
                    : ViewWord.DUE.in(language), facts.due(), true);
            out.append("</dl>\n");
        }
        out.append("</div>\n");
    }

    private void fact(String label, String value, boolean due) {
        out.append(due ? "<div class=\"due\">" : "<div>").append("<dt>").append(escape(label))
                .append("</dt><dd>").append(escape(value)).append("</dd></div>\n");
    }

    /** Returns the sentence that says which attachment the invoice was taken out of. */
    private String provenance() {
        String sentence = ReportWord.PROVENANCE_ATTACHMENT.in(language, List.of(
                Integer.toString(content.provenance.position()),
                text(content.provenance.attachment())));
        return sentence.substring(0, 1).toUpperCase(Locale.ROOT) + sentence.substring(1) + ".";
    }

    /** Writes the verdict card: the report's where a run validated, else "not checked". */
    private void verdict() {
        if (content.outcome != null) {
            ReportHtml.card(out, content.outcome, reportOptions(),
                    content.received != null ? ViewWord.PICTURE_VERDICT.in(language) : null);
            return;
        }
        out.append("<section class=\"card\">\n<p class=\"verdict verdict-NONE\"><span>")
                .append(escape(ReportWord.VERDICT.in(language))).append("</span> <b>")
                .append(escape(ViewWord.NOT_CHECKED.in(language))).append("</b></p>\n")
                .append("<p class=\"note\"><code>esj view --report</code>")
                .append(escape(ViewWord.HINT.in(language))).append("</p>\n");
        if (content.received != null) {
            out.append("<p>").append(escape(ViewWord.PICTURE_ALONE.in(language)))
                    .append("</p>\n");
        }
        out.append("</section>\n");
    }

    // ------------------------------------------------------------------- dead ends

    /** Returns the head of the dead end, the words a reader sees first. */
    private String deadEndHead() {
        return switch (content.deadEnd.kind()) {
            case NO_INVOICE -> ViewWord.NO_INVOICE_HEAD.in(language);
            case NOT_ESTABLISHED -> ViewWord.UNESTABLISHED_HEAD.in(language);
            case SEVERAL -> ViewWord.SEVERAL_HEAD.in(language);
            case UNSUPPORTED -> ViewWord.UNSUPPORTED_HEAD.in(language);
            case ENCRYPTED -> ViewWord.ENCRYPTED_HEAD.in(language);
            case NOT_READ -> ViewWord.NOT_READ_HEAD.in(language);
            case LIMIT -> limitHead();
        };
    }

    /** Returns the head of a limit: the report's line about a subject, as a sentence. */
    private String limitHead() {
        String line = ReportWord.SUBJECT_LIMIT.in(language);
        return line.substring(0, 1).toUpperCase(Locale.ROOT) + line.substring(1) + ".";
    }

    private void deadEnd() {
        DeadEnd end = content.deadEnd;
        out.append("<section class=\"card state")
                .append(end.kind() == DeadEnd.Kind.LIMIT ? " limit" : "").append("\">\n")
                .append("<p class=\"state-head\"><b>").append(escape(deadEndHead()))
                .append("</b></p>\n");
        switch (end.kind()) {
            case NO_INVOICE -> {
                sentence(ViewWord.NO_INVOICE_TEXT.in(language));
                sentence(ViewWord.NO_INVOICE_NEXT.in(language));
            }
            case SEVERAL -> {
                candidates(end.candidates());
                sentence(ViewWord.SEVERAL_ASK.in(language));
            }
            case UNSUPPORTED -> sentence(ViewWord.UNSUPPORTED_TEXT.in(language));
            case ENCRYPTED -> sentence(ViewWord.ENCRYPTED_TEXT.in(language));
            case NOT_READ -> sentence(ViewWord.NOT_READ_TEXT.in(language));
            case LIMIT -> sentence(ViewWord.LIMIT_TEXT.in(language));
            case NOT_ESTABLISHED -> {
                // The message says why: an attachment that was never classified.
            }
        }
        message(end.message());
        if (content.reportAsked && content.outcome == null) {
            out.append("<p class=\"note\">").append(escape(ViewWord.NO_REPORT.in(language)))
                    .append("</p>\n");
        }
        out.append("</section>\n");
    }

    private void sentence(String text) {
        out.append("<p>").append(escape(text)).append("</p>\n");
    }

    /** Writes the error text of the run, as it stands. */
    private void message(String message) {
        out.append("<p class=\"label\">").append(escape(ViewWord.MESSAGE.in(language)))
                .append("</p>\n<pre class=\"message\">")
                .append(escape(ReportContent.cut(ReportContent.plain(message), language)))
                .append("</pre>\n");
    }

    /**
     * Writes the attachments that could each be the invoice, with the command that reads
     * each of them: the count first, and the table folded under it where it is long.
     */
    private void candidates(List<Candidate> candidates) {
        String count = ViewWord.SEVERAL_COUNT.in(language,
                List.of(Integer.toString(candidates.size())));
        boolean folded = candidates.size() > OPEN_ITEMS;
        if (folded) {
            out.append("<details>\n<summary>").append(escape(count)).append("</summary>\n");
        } else {
            sentence(count);
        }
        out.append("<table class=\"candidates\">\n<tr><th>")
                .append(escape(ViewWord.COLUMN_POSITION.in(language))).append("</th><th>")
                .append(escape(ViewWord.COLUMN_NAME.in(language))).append("</th><th>")
                .append(escape(ViewWord.COLUMN_CONTENT.in(language)))
                .append("</th><th class=\"size\">")
                .append(escape(ViewWord.COLUMN_SIZE.in(language))).append("</th><th>")
                .append(escape(ViewWord.COLUMN_RELATIONSHIP.in(language))).append("</th><th>")
                .append(escape(ViewWord.COLUMN_READ_WITH.in(language))).append("</th></tr>\n");
        for (Candidate candidate : candidates) {
            out.append("<tr><td>").append(candidate.position()).append("</td><td>")
                    .append(escape(text(candidate.name()))).append("</td><td>")
                    .append(escape(kind(candidate))).append("</td><td class=\"size\">")
                    .append(escape(candidate.declaredSize().isPresent()
                            ? Formats.bytes(candidate.declaredSize().getAsLong(), language)
                            : ViewWord.ABSENT.in(language)))
                    .append("</td><td>")
                    .append(escape(candidate.relationship().map(ViewPage::text)
                            .orElse(ViewWord.ABSENT.in(language))))
                    .append("</td><td><code class=\"command\">")
                    .append(escape(text(candidate.command()))).append("</code></td></tr>\n");
        }
        out.append("</table>\n");
        if (folded) {
            out.append("</details>\n");
        }
    }

    private String kind(Candidate candidate) {
        return switch (candidate.kind()) {
            case CII_INVOICE -> ViewWord.KIND_CII.in(language);
            case UBL_INVOICE -> ViewWord.KIND_UBL_INVOICE.in(language);
            case UBL_CREDIT_NOTE -> ViewWord.KIND_UBL_CREDIT_NOTE.in(language);
            case UNDETERMINED -> ViewWord.KIND_UNDETERMINED.in(language);
            case UNREADABLE -> ViewWord.KIND_UNREADABLE.in(language);
            case OTHER -> text(candidate.described());
        };
    }

    // ------------------------------------------------------------------- the views

    /** Tells whether the page has the view of the invoice as read. */
    private boolean hasRead() {
        return content.deadEnd == null && content.letter != null;
    }

    private void band() {
        boolean received = content.received != null;
        boolean read = hasRead();
        if (!received && !read) {
            return;
        }
        out.append(received && read ? "<div class=\"band\">\n" : "<div class=\"band one\">\n");
        if (received) {
            receivedPane();
        }
        if (read) {
            readPane();
        }
        out.append("</div>\n");
    }

    private void receivedPane() {
        String file = "received/" + content.received;
        paneHead("received", ViewWord.RECEIVED.in(language),
                List.of(anchor(href(file), ViewWord.OPEN_RECEIVED.in(language))));
        out.append("<div class=\"caption\"><p>")
                .append(escape(ViewWord.CAPTION_RECEIVED.in(language))).append("</p></div>\n")
                .append("<iframe src=\"").append(href(file)).append("\" title=\"")
                .append(escape(ViewWord.FRAME_RECEIVED.in(language,
                        List.of(text(content.received)))))
                .append("\"></iframe>\n</section>\n");
    }

    private void readPane() {
        List<String> links = new ArrayList<>();
        if (content.kosit != null) {
            links.add(anchor(href(content.kosit), ViewWord.OPEN_HTML.in(language)));
        }
        Letter letter = content.letter;
        if (letter.file().isPresent()) {
            links.add(anchor(href(letter.file().get()), ViewWord.OPEN_READ.in(language)));
        }
        paneHead("read", ViewWord.READ.in(language), links);
        out.append("<div class=\"caption\"><p>").append(escape(ViewWord.CAPTION_READ.in(language)))
                .append("</p>\n");
        if (content.kositRefusal != null) {
            out.append("<p>").append(escape(ViewWord.HTML_REFUSED.in(language) + " "
                    + ReportContent.cut(ReportContent.plain(content.kositRefusal), language)))
                    .append("</p>\n");
        } else {
            exportNotes(content.exportNotes);
        }
        out.append("</div>\n");
        if (letter.file().isPresent()) {
            out.append("<iframe src=\"").append(href(letter.file().get())).append("\" title=\"")
                    .append(escape(ViewWord.FRAME_READ.in(language))).append("\"></iframe>\n");
        } else if (letter.limit().isPresent()) {
            out.append("<div>\n<p class=\"state-head\"><b>").append(escape(limitHead()))
                    .append("</b></p>\n");
            message(letter.limit().get());
            out.append("</div>\n");
        } else {
            out.append("<div>\n<p>").append(escape(ReportWord.INVOICE_REFUSED.in(language) + " "
                    + ReportContent.resolve(letter.refusal().orElseThrow(), language)))
                    .append("</p>\n</div>\n");
        }
        out.append("</section>\n");
    }

    /** Writes the values that did not reach the HTML rendering, folded where they are many. */
    private void exportNotes(List<String> notes) {
        if (notes.isEmpty()) {
            return;
        }
        String count = (notes.size() == 1 ? ViewWord.EXPORT_NOTE : ViewWord.EXPORT_NOTES)
                .in(language, List.of(Integer.toString(notes.size())));
        boolean folded = notes.size() > OPEN_ITEMS;
        out.append(folded ? "<details>\n<summary>" : "<p>").append(escape(count))
                .append(folded ? "</summary>\n" : "</p>\n").append("<ul>\n");
        for (String line : ReportContent.exportNotes(notes, language)) {
            out.append("<li>").append(escape(line)).append("</li>\n");
        }
        out.append("</ul>\n").append(folded ? "</details>\n" : "");
    }

    /** Opens a view and writes its heading row: the heading, and its links at the right. */
    private void paneHead(String id, String heading, List<String> links) {
        out.append("<section class=\"pane\" id=\"").append(id).append("\" aria-labelledby=\"")
                .append(id).append("-heading\">\n<div class=\"pane-head\"><h2 id=\"").append(id)
                .append("-heading\">").append(escape(heading)).append("</h2>");
        if (!links.isEmpty()) {
            out.append("<p class=\"links\">").append(String.join(" ", links)).append("</p>");
        }
        out.append("</div>\n");
    }

    /** Writes, for the printout, what it is and where the invoice is printed from. */
    private void printNote() {
        List<String> files = new ArrayList<>();
        if (content.received != null) {
            files.add(text(content.received));
        }
        if (hasRead()) {
            content.letter.file().ifPresent(file -> files.add(text(file)));
        }
        if (files.isEmpty()) {
            return;
        }
        String first = (content.outcome != null ? ViewWord.PRINT_WITH_REPORT
                : ViewWord.PRINT_WITHOUT_REPORT).in(language);
        out.append("<p class=\"print-note\">").append(escape(first + " "
                + ViewWord.PRINT_FILES.in(language, List.of(String.join(", ", files)))))
                .append("</p>\n");
    }

    /** Writes the report, as sections of the page, under the links to its two files. */
    private void report() {
        if (content.outcome == null) {
            return;
        }
        List<String> links = new ArrayList<>();
        if (content.reportPdf != null) {
            links.add(anchor(href(content.reportPdf), ViewWord.OPEN_REPORT_PDF.in(language)));
        }
        if (content.reportHtml != null) {
            links.add(anchor(href(content.reportHtml), ViewWord.OPEN_REPORT_HTML.in(language)));
        }
        out.append("<section class=\"report\" id=\"report\" aria-labelledby=\"report-heading\">\n")
                .append("<div class=\"pane-head\"><h2 id=\"report-heading\">")
                .append(escape(ReportWord.TITLE.in(language))).append("</h2>");
        if (!links.isEmpty()) {
            out.append("<p class=\"links\">").append(String.join(" ", links)).append("</p>");
        }
        out.append("</div>\n");
        ReportHtml.sections(out, content.outcome, reportOptions(), REPORT_LEVEL);
        out.append("</section>\n");
    }

    private void footer() {
        String sentence = ViewWord.FOOTER_DIRECTORY.in(language);
        int at = sentence.indexOf("%1$s");
        out.append("<footer>\n<p>").append(escape(sentence.substring(0, at))).append("<span>")
                .append(escape(text(content.directory))).append("</span>")
                .append(escape(sentence.substring(at + "%1$s".length()))).append("</p>\n<p>")
                .append(escape(ViewWord.FOOTER_OFFLINE.in(language))).append("</p>\n<p>")
                .append(escape(ReportWord.TOOL.in(language))).append(' ')
                .append(escape(text(content.tool))).append("</p>\n</footer>\n");
    }

    // ------------------------------------------------------------------- helpers

    private ReportOptions reportOptions() {
        return ReportOptions.defaults().withLanguage(language);
    }

    private static String anchor(String href, String text) {
        return "<a href=\"" + href + "\">" + escape(text) + "</a>";
    }

    /** Returns a text of the run or of the document as the page may show it. */
    private static String text(String value) {
        return ReportContent.plain(value);
    }

    private static String escape(String text) {
        return ReportHtml.escape(text);
    }

    /**
     * Returns a relative reference to a file of the page's directory: each segment of the
     * path percent-encoded in UTF-8, every byte but the unreserved characters of RFC 3986,
     * so that a name with a space, a {@code #}, a {@code %} or an umlaut names that file and
     * nothing else.
     *
     * @param path the path relative to the page, segments separated by {@code /}
     * @return the reference, safe in an attribute
     */
    static String href(String path) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            boolean unreserved = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9' || c == '-' || c == '.' || c == '_' || c == '~'
                    || c == '/';
            if (unreserved) {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return encoded.toString();
    }

    // ------------------------------------------------------------------- the facts

    /** The facts of the header, read from the document and formatted as the letter writes them. */
    private static final class Facts {

        private final SemanticDocument document;
        private final Registry registry;
        private final RenderLanguage language;

        Facts(SemanticDocument document, Registry registry, RenderLanguage language) {
            this.document = document;
            this.registry = registry;
            this.language = language;
        }

        /** Returns the title: the type by name, the number, and the date. */
        String title() {
            String code = content("/BT-3");
            String type = code.isEmpty() ? ViewWord.ABSENT.in(language)
                    : codeList("BT-3").flatMap(DisplayNames.CodeList::of)
                            .flatMap(list -> DisplayNames.of(list, code, language))
                            .orElse(code);
            return type + " " + orAbsent(content("/BT-1")) + " "
                    + Word.PRECEDING_OF.in(language) + " "
                    + orAbsent(formatted("/BT-2", SemanticType.DATE));
        }

        String seller() {
            return orAbsent(content("/BG-4/BT-27"));
        }

        String buyer() {
            return orAbsent(content("/BG-7/BT-44"));
        }

        /** Returns the amount the invoice closes with, the currency of the document behind it. */
        String due() {
            String amount = formatted("/BG-22/BT-115", SemanticType.AMOUNT);
            if (amount.isEmpty()) {
                return ViewWord.ABSENT.in(language);
            }
            String currency = content("/BT-5");
            return currency.isEmpty() ? amount : amount + " " + currency;
        }

        /** Tells whether the document is a credit note, as the letter tells it. */
        boolean creditNote() {
            return LetterLayout.CREDIT_NOTES.contains(content("/BT-3"));
        }

        private String content(String path) {
            return document.value(SemanticPath.of(path)).map(SemanticValue::content)
                    .map(ReportContent::plain).map(Characters::oneLine).orElse("");
        }

        private String formatted(String path, SemanticType type) {
            String value = content(path);
            return value.isEmpty() ? "" : Formats.value(type, value, language);
        }

        private Optional<String> codeList(String termId) {
            return registry == null ? Optional.empty()
                    : registry.term(termId).flatMap(Term::codeList);
        }

        private String orAbsent(String value) {
            return value.isEmpty() ? ViewWord.ABSENT.in(language) : value;
        }
    }

    // ------------------------------------------------------------------- the content

    /**
     * Why a page shows no invoice as read: the run reached a dead end before a document was
     * built, and the page says which, with the message of the run.
     *
     * @param kind       which dead end
     * @param message    the error text of the run, as it stands
     * @param candidates the attachments that could each be the invoice, for
     *                   {@link Kind#SEVERAL}; empty for every other kind
     */
    public record DeadEnd(Kind kind, String message, List<Candidate> candidates) {

        /**
         * Checks the parts and copies the list.
         *
         * @param kind       which dead end
         * @param message    the error text of the run
         * @param candidates the attachments, for {@link Kind#SEVERAL}
         */
        public DeadEnd {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(message, "message");
            candidates = List.copyOf(candidates);
        }

        /** The dead ends of a run. */
        public enum Kind {

            /** A PDF without an electronic invoice. */
            NO_INVOICE,

            /** A PDF whose attachments were not classified, so nothing was established. */
            NOT_ESTABLISHED,

            /** A PDF with several attachments that could each be the invoice. */
            SEVERAL,

            /** A ZUGFeRD 1.0 invoice. */
            UNSUPPORTED,

            /** An encrypted PDF. */
            ENCRYPTED,

            /** A file that was not read. */
            NOT_READ,

            /** A limit of the run. */
            LIMIT
        }
    }

    /**
     * One attachment of a PDF that could be the invoice.
     *
     * @param position     its position among the attachments, counted from one
     * @param name         its name, as the container names it
     * @param kind         what its bytes were classified as
     * @param described    what the reader calls it, for {@link Kind#OTHER}
     * @param declaredSize the size the container declares for it, where it declares one
     * @param relationship its {@code AFRelationship}, where it has one
     * @param command      the command line that reads it
     */
    public record Candidate(int position, String name, Kind kind, String described,
                            OptionalLong declaredSize, Optional<String> relationship,
                            String command) {

        /**
         * Checks the parts.
         *
         * @param position     its position among the attachments
         * @param name         its name
         * @param kind         what its bytes were classified as
         * @param described    what the reader calls it
         * @param declaredSize the size the container declares
         * @param relationship its {@code AFRelationship}
         * @param command      the command line that reads it
         */
        public Candidate {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(described, "described");
            Objects.requireNonNull(declaredSize, "declaredSize");
            Objects.requireNonNull(relationship, "relationship");
            Objects.requireNonNull(command, "command");
        }

        /** What the bytes of an attachment were classified as. */
        public enum Kind {

            /** A cross industry invoice. */
            CII_INVOICE,

            /** A UBL invoice. */
            UBL_INVOICE,

            /** A UBL credit note. */
            UBL_CREDIT_NOTE,

            /** XML whose root element lies beyond the bytes it was classified by. */
            UNDETERMINED,

            /** Filtered with something the reader does not decode. */
            UNREADABLE,

            /** Anything else, named as the reader names it. */
            OTHER
        }
    }

    /**
     * The view of the invoice as read: the file of the letter, why there is none, or the
     * limit the rendering met. Exactly one of the three is present.
     *
     * @param file    the file name of the letter, relative to the page
     * @param refusal why the invoice could not be rendered: the message of the run, or a
     *                sentence of this project's own
     * @param limit   the message of a bound the rendering outgrew
     */
    public record Letter(Optional<String> file, Optional<Text> refusal,
                         Optional<String> limit) {

        /**
         * Checks that exactly one of the three is present.
         *
         * @param file    the file name of the letter
         * @param refusal why the invoice could not be rendered
         * @param limit   the message of a bound
         */
        public Letter {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(refusal, "refusal");
            Objects.requireNonNull(limit, "limit");
            int present = (file.isPresent() ? 1 : 0) + (refusal.isPresent() ? 1 : 0)
                    + (limit.isPresent() ? 1 : 0);
            if (present != 1) {
                throw new IllegalArgumentException("a letter is a file, a refusal or a limit");
            }
        }

        /**
         * Returns the letter that was written.
         *
         * @param file its file name, relative to the page
         * @return the view
         */
        public static Letter written(String file) {
            return new Letter(Optional.of(file), Optional.empty(), Optional.empty());
        }

        /**
         * Returns the letter that was refused.
         *
         * @param reason why
         * @return the view
         */
        public static Letter refused(Text reason) {
            return new Letter(Optional.empty(), Optional.of(reason), Optional.empty());
        }

        /**
         * Returns the letter that met a bound.
         *
         * @param message the message of the bound
         * @return the view
         */
        public static Letter limited(String message) {
            return new Letter(Optional.empty(), Optional.empty(), Optional.of(message));
        }
    }

    /**
     * The attachment an invoice was taken out of.
     *
     * @param position   its position among the attachments, counted from one
     * @param attachment its name
     */
    public record Provenance(int position, String attachment) {

        /**
         * Checks the name.
         *
         * @param position   its position
         * @param attachment its name
         */
        public Provenance {
            Objects.requireNonNull(attachment, "attachment");
        }
    }

    /**
     * What a run of {@code esj view} came to, and the files it wrote beside the page: the
     * one input of {@link #html(Content)}. Every file name is relative to the page; what is
     * left unset is not on the page.
     */
    public static final class Content {

        private final RenderLanguage language;
        private final String fileName;
        private final String input;
        private final String directory;
        private final String tool;
        private String received;
        private Provenance provenance;
        private Text syntax;
        private String profile;
        private String recoded;
        private String notEn16931;
        private SemanticDocument document;
        private Registry registry;
        private ValidationOutcome outcome;
        private boolean reportAsked;
        private String reportHtml;
        private String reportPdf;
        private Letter letter;
        private String kosit;
        private String kositRefusal;
        private List<String> exportNotes = List.of();
        private DeadEnd deadEnd;

        /**
         * Starts the content of a page.
         *
         * @param language  the language of the page, of its words and of the values
         * @param fileName  the name of the input file, the heading of the page
         * @param input     the input as the command line named it
         * @param directory the absolute directory the page and its files lie in
         * @param tool      the tool that wrote it, {@code esj} and its version
         */
        public Content(RenderLanguage language, String fileName, String input, String directory,
                       String tool) {
            this.language = Objects.requireNonNull(language, "language");
            this.fileName = Objects.requireNonNull(fileName, "fileName");
            this.input = Objects.requireNonNull(input, "input");
            this.directory = Objects.requireNonNull(directory, "directory");
            this.tool = Objects.requireNonNull(tool, "tool");
        }

        /**
         * Names the copy of the received PDF, in {@code received/} beside the page.
         *
         * @param name its file name
         * @return this content
         */
        public Content received(String name) {
            this.received = Objects.requireNonNull(name, "name");
            return this;
        }

        /**
         * Names the attachment the invoice was taken out of.
         *
         * @param position its position, counted from one
         * @param name     its name
         * @return this content
         */
        public Content provenance(int position, String name) {
            this.provenance = new Provenance(position, name);
            return this;
        }

        /**
         * Names the syntax the invoice was read as, in the words of the report.
         *
         * @param text the syntax
         * @return this content
         */
        public Content syntax(Text text) {
            this.syntax = Objects.requireNonNull(text, "text");
            return this;
        }

        /**
         * Names the profile the document claims.
         *
         * @param identifier its identifier, or its conformance level
         * @return this content
         */
        public Content profile(String identifier) {
            this.profile = Objects.requireNonNull(identifier, "identifier");
            return this;
        }

        /**
         * Says how the bytes of the invoice were recoded before they were read.
         *
         * @param description what the repair did
         * @return this content
         */
        public Content recoded(String description) {
            this.recoded = Objects.requireNonNull(description, "description");
            return this;
        }

        /**
         * Says that the profile of the container is not an EN 16931 invoice.
         *
         * @param conformanceLevel the level the container names
         * @return this content
         */
        public Content notEn16931(String conformanceLevel) {
            this.notEn16931 = Objects.requireNonNull(conformanceLevel, "conformanceLevel");
            return this;
        }

        /**
         * Gives the document the facts of the header are read from.
         *
         * @param value    the document
         * @param terms    the registry of its edition, which names its codes; {@code null}
         *                 where this build carries none
         * @return this content
         */
        public Content document(SemanticDocument value, Registry terms) {
            this.document = Objects.requireNonNull(value, "value");
            this.registry = terms;
            return this;
        }

        /**
         * Gives the report of the run that validated the invoice, and the files it was
         * written to.
         *
         * @param value the outcome
         * @param html  the file name of its HTML form, or {@code null} where none was written
         * @param pdf   the file name of its PDF form, or {@code null} where none was written
         * @return this content
         */
        public Content report(ValidationOutcome value, String html, String pdf) {
            this.outcome = Objects.requireNonNull(value, "value");
            this.reportHtml = html;
            this.reportPdf = pdf;
            return this;
        }

        /**
         * Records that the run was asked for a report, so that a dead end says there is none.
         *
         * @return this content
         */
        public Content reportAsked() {
            this.reportAsked = true;
            return this;
        }

        /**
         * Gives the view of the invoice as read.
         *
         * @param value the letter, its refusal or its limit
         * @return this content
         */
        public Content letter(Letter value) {
            this.letter = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Names the HTML rendering, with what did not reach it.
         *
         * @param file  its file name
         * @param notes the values of the document it has no place for, as the export wrote them
         * @return this content
         */
        public Content kosit(String file, List<String> notes) {
            this.kosit = Objects.requireNonNull(file, "file");
            this.exportNotes = List.copyOf(notes);
            return this;
        }

        /**
         * Says why the HTML rendering was not written.
         *
         * @param reason the reason, or the message of the bound
         * @return this content
         */
        public Content kositRefused(String reason) {
            this.kositRefusal = Objects.requireNonNull(reason, "reason");
            return this;
        }

        /**
         * Makes the page a dead end.
         *
         * @param value which, and the message of the run
         * @return this content
         */
        public Content deadEnd(DeadEnd value) {
            this.deadEnd = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Refuses a content that names no view where it has to name one. */
        private void check() {
            if (deadEnd == null && letter == null) {
                throw new IllegalStateException("a page that is no dead end shows the invoice"
                        + " as read, or why it could not");
            }
        }
    }
}
