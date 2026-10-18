package de.bsnsoft.esj.render.internal;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.internal.report.ValidationOutcome;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import de.bsnsoft.esj.render.RenderLanguage;
import de.bsnsoft.esj.validate.Severity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The page {@code esj view} writes: one HTML file that shows an invoice to the person who
 * received it.
 *
 * <p>At the top the header says what the invoice is — seller and buyer, number, date, the
 * amount due — and, at the right, the short verdict: one word and the findings counted by
 * severity. Under it, one row of tabs, each a view of its own: the PDF as it came, the
 * invoice rendered from its data, the KoSIT visualization, and the validation report. A tab
 * is there only where its content is. A file that leads nowhere — a PDF without an invoice,
 * one with several candidates, a file that is not one — replaces the verdict with what it is
 * and one sentence that says what to ask the sender for.
 *
 * <h2>Inert</h2>
 *
 * <p>The page fetches nothing and runs nothing. The tabs are radio buttons and labels, shown
 * and hidden by the style sheet; the head carries a content security policy that allows no
 * script, no base and no form. Every reference is relative and names a file of the page's
 * own directory, percent-encoded. The KoSIT visualization runs its own script for its own
 * tabs, in a frame of its own, sandboxed without the origin of the page: the policy of the
 * page does not reach into a document a frame loads, and the frame cannot reach the page.
 * Every text of the run and of the document goes through {@link ReportContent#plain(String)},
 * which takes out the characters that direct the reading order, and through
 * {@link ReportHtml#escape(String)}. The same content gives the same string.
 *
 * <h2>One style sheet</h2>
 *
 * <p>{@link ReportHtml#STYLE} comes first and unchanged — the colours, the type, the card,
 * the table and the finding of the report — and the rules of the page after it, which add
 * the header, the tabs, the frames and the printout. The page adds no colour of its own.
 */
public final class ViewPage {

    /** The heading level of the sections of the report in its tab. */
    private static final int REPORT_LEVEL = 2;

    /** The rules of the page, after the report's. */
    private static final String STYLE = """
            :root { color-scheme: light; }
            body { padding: 0; }
            main { max-width: none; margin: 0; }
            a { color: var(--ink); }
            a:focus-visible { outline: 2px solid var(--ink); outline-offset: 2px; }
            .view { max-width: 110rem; margin: 0 auto; padding: 1.5rem; }
            h1 { margin: 0 0 1rem; overflow-wrap: anywhere; }
            .summary { display: grid; grid-template-columns: minmax(0, 1fr) minmax(16rem, 24rem);
                       gap: 1.5rem; align-items: start; }
            .facts p { margin: 0 0 .4rem; overflow-wrap: anywhere; }
            .facts .kind { font-weight: 600; }
            .facts .parties { font-size: 1.05rem; }
            .facts .meta { color: var(--soft); }
            .facts dl { grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr));
                        gap: .6rem 1.2rem; margin: .6rem 0 0; }
            .facts dl div { min-width: 0; }
            .facts dt { font-size: .8rem; }
            .facts .due dd { font-weight: 700; font-size: 1.25rem;
                             font-variant-numeric: tabular-nums; }
            .result p { margin: 0; }
            .result .counts { margin-top: .3rem; color: var(--soft); }
            .card.state { max-width: 60rem; border-left: 3px solid var(--warn); }
            .card.state.limit { border-left-color: var(--none); }
            .card.state p { margin: .5rem 0 0; }
            .card.state .state-head { margin: 0; font-size: 1.15rem; font-weight: 600; }
            code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                   font-size: .85em; }
            code.command { user-select: all; }
            table.candidates { margin: .8rem 0 .2rem; }
            table.candidates td { overflow-wrap: anywhere; }
            table.candidates .size { text-align: right; font-variant-numeric: tabular-nums; }
            .tabs { display: flex; flex-wrap: wrap; margin-top: 1.5rem; }
            .tabs > input { position: absolute; opacity: 0; width: 1px; height: 1px; margin: 0;
                            pointer-events: none; }
            .tabs > label { padding: .45rem 1.1rem; cursor: pointer; color: var(--soft);
                            border-bottom: 2px solid transparent; }
            .tabs > input:checked + label { color: var(--ink); font-weight: 600;
                                            border-bottom-color: var(--ink); }
            .tabs > input:focus-visible + label { outline: 2px solid var(--ink);
                                                  outline-offset: -2px; }
            .pane { display: none; order: 1; flex: 0 0 100%; min-width: 0; padding-top: 1rem;
                    border-top: 1px solid var(--rule); }
            #tab-pdf:checked ~ #pane-pdf, #tab-invoice:checked ~ #pane-invoice,
            #tab-kosit:checked ~ #pane-kosit, #tab-report:checked ~ #pane-report {
              display: block; }
            .pane iframe { height: calc(100vh - 4rem); min-height: 30rem; }
            .pane > p { margin: 0 0 .6rem; }
            .pane .caption { color: var(--soft); font-size: .85rem; }
            .report { max-width: 60rem; max-height: calc(100vh - 4rem); overflow: auto; }
            .report .save { margin: 0 0 1rem; }
            footer { margin-top: 2rem; padding-top: .8rem; border-top: 1px solid var(--rule);
                     color: var(--soft); font-size: .85rem; overflow-wrap: anywhere; }
            footer p { margin: 0; }
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
              .tabs > label { display: none; }
              .pane { border-top: 0; padding-top: 0; }
              .report { max-height: none; overflow: visible; }
              .pane iframe { height: 24cm; }
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

    /**
     * Returns what a dead end says in one line: what the file is, and what to ask the
     * sender for — or, for a limit, the bound that was met in the words of a reader. It is
     * the line the page writes at its head, for a terminal that has no page.
     *
     * @param end      the dead end
     * @param language the language
     * @param check    whether the run was asked to check the invoice
     * @return the line
     */
    public static String state(DeadEnd end, RenderLanguage language, boolean check) {
        Objects.requireNonNull(end, "end");
        String head = head(end, language, check);
        Optional<ViewWord> ask = ask(end.kind());
        return ask.isEmpty() ? head : head + " " + ask.get().in(language);
    }

    /**
     * Returns the sentence that says an invoice cannot be shown, for a terminal that has no
     * page to say it on.
     *
     * @param language the language
     * @return the sentence
     */
    public static String notRendered(RenderLanguage language) {
        return ViewWord.NOT_RENDERED.in(language);
    }

    /**
     * Returns a verdict word as the page writes it: the words of the reader's language for
     * the three states of a verdict, for a subject that was not checked and for one a limit
     * stopped, and any other word — {@code OK} — as it stands.
     *
     * @param token    the word, in the spelling every form of a run uses for it
     * @param language the language of the page
     * @return the word on the page
     */
    static String word(String token, RenderLanguage language) {
        return switch (token) {
            case "VALID" -> ViewWord.VALID.in(language);
            case "INVALID" -> ViewWord.INVALID.in(language);
            case "INDETERMINATE" -> ViewWord.UNDECIDED.in(language);
            case ValidationOutcome.NOT_CHECKED -> ViewWord.NOT_CHECKED.in(language);
            case ValidationOutcome.NO_VERDICT -> ViewWord.NO_VERDICT.in(language);
            default -> token;
        };
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
        tabs();
        footer();
        out.append("</div>\n</body>\n</html>\n");
    }

    /** Returns the title: the file, then what became of it, then the tool. */
    private String title() {
        String state;
        if (content.deadEnd != null) {
            String head = head(content.deadEnd, language, content.reportAsked);
            state = head.endsWith(".") ? head.substring(0, head.length() - 1) : head;
        } else {
            state = verdictWord();
        }
        return text(content.fileName) + " · " + state + " · esj";
    }

    /** Returns the verdict as the page writes it, or "not checked" without a report. */
    private String verdictWord() {
        return content.outcome == null ? ViewWord.NOT_CHECKED.in(language)
                : word(ReportContent.verdict(content.outcome), language);
    }

    // ------------------------------------------------------------------- the header

    private void header() {
        out.append("<header>\n<h1>").append(escape(text(content.fileName))).append("</h1>\n");
        if (content.deadEnd != null) {
            deadEnd();
        } else {
            out.append("<div class=\"summary\">\n");
            facts();
            result();
            out.append("</div>\n");
        }
        out.append("</header>\n");
    }

    /** Writes what the invoice says: who to whom, its type, number, date and amount due. */
    private void facts() {
        out.append("<div class=\"facts\">\n");
        if (content.document != null) {
            Facts facts = new Facts(content.document, content.registry, language);
            facts.kind().ifPresent(kind -> out.append("<p class=\"kind\">")
                    .append(escape(kind)).append("</p>\n"));
            out.append("<p class=\"parties\">").append(escape(facts.parties()))
                    .append("</p>\n<dl>\n");
            boolean credit = facts.creditNote();
            fact(credit ? Word.CREDIT_NOTE_NUMBER.in(language) : ViewWord.NUMBER.in(language),
                    facts.number(), false);
            fact(credit ? Word.CREDIT_NOTE_DATE.in(language) : ViewWord.DATE.in(language),
                    facts.date(), false);
            fact(credit ? Word.AMOUNT_CREDITED.in(language) : ViewWord.DUE.in(language),
                    facts.due(), true);
            out.append("</dl>\n");
        }
        if (content.notEn16931 != null) {
            out.append("<p class=\"meta\">").append(escape(ReportWord.DETAIL_NOT_EN16931.in(
                    language, List.of(text(content.notEn16931))))).append("</p>\n");
        }
        out.append("</div>\n");
    }

    private void fact(String label, String value, boolean due) {
        if (value.isEmpty()) {
            return;
        }
        out.append(due ? "<div class=\"due\">" : "<div>").append("<dt>").append(escape(label))
                .append("</dt><dd>").append(escape(value)).append("</dd></div>\n");
    }

    /** Writes the short verdict: one word, and the findings counted by severity. */
    private void result() {
        String kind = content.outcome == null ? "NONE"
                : content.outcome.verdict().map(Enum::name).orElse("NONE");
        out.append("<section class=\"card result\" aria-label=\"")
                .append(escape(ReportWord.VERDICT.in(language))).append("\">\n")
                .append("<p class=\"verdict verdict-").append(kind).append("\"><b>")
                .append(escape(verdictWord())).append("</b></p>\n");
        if (content.outcome != null) {
            out.append("<p class=\"counts\">").append(escape(counts(content.outcome)))
                    .append("</p>\n");
        }
        out.append("</section>\n");
    }

    /** Returns the findings counted by severity, the severities with none left out. */
    private String counts(ValidationOutcome outcome) {
        int errors = 0;
        int warnings = 0;
        int notes = 0;
        for (ValidationOutcome.Finding finding : outcome.findings()) {
            if (finding.severity() == Severity.ERROR) {
                errors++;
            } else if (finding.severity() == Severity.WARNING) {
                warnings++;
            } else {
                notes++;
            }
        }
        List<String> parts = new ArrayList<>();
        count(parts, errors, ViewWord.ERROR, ViewWord.ERRORS);
        count(parts, warnings, ViewWord.WARNING, ViewWord.WARNINGS);
        count(parts, notes, ViewWord.NOTE, ViewWord.NOTES);
        return parts.isEmpty() ? ViewWord.NO_FINDINGS.in(language) : String.join(" · ", parts);
    }

    private void count(List<String> parts, int count, ViewWord one, ViewWord several) {
        if (count > 0) {
            parts.add((count == 1 ? one : several).in(language,
                    List.of(Integer.toString(count))));
        }
    }

    // ------------------------------------------------------------------- dead ends

    /** Returns the line a dead end opens with: what the file is, or the bound that was met. */
    private static String head(DeadEnd end, RenderLanguage language, boolean check) {
        return switch (end.kind()) {
            case NO_INVOICE -> ViewWord.NO_INVOICE.in(language);
            case NOT_ESTABLISHED -> ViewWord.NOT_ESTABLISHED.in(language);
            case SEVERAL -> ViewWord.SEVERAL.in(language);
            case UNSUPPORTED -> ViewWord.UNSUPPORTED.in(language);
            case ENCRYPTED -> ViewWord.ENCRYPTED.in(language);
            case NOT_READ -> ViewWord.NOT_READ.in(language);
            case LIMIT -> limit(end.limit(), language, check);
        };
    }

    /** Returns the one sentence that says what to do about a dead end, where there is one. */
    private static Optional<ViewWord> ask(DeadEnd.Kind kind) {
        return Optional.ofNullable(switch (kind) {
            case NO_INVOICE -> ViewWord.NO_INVOICE_ASK;
            case NOT_ESTABLISHED -> ViewWord.NOT_ESTABLISHED_ASK;
            case SEVERAL -> ViewWord.SEVERAL_ASK;
            case UNSUPPORTED -> ViewWord.UNSUPPORTED_ASK;
            case ENCRYPTED -> ViewWord.ENCRYPTED_ASK;
            case NOT_READ -> ViewWord.NOT_READ_ASK;
            case LIMIT -> null;
        });
    }

    /** Returns a bound that was met, as the fact a reader understands. */
    private static String limit(Limit limit, RenderLanguage language, boolean check) {
        return switch (limit.kind()) {
            case SIZE -> (check ? ViewWord.LIMIT_SIZE_CHECK : ViewWord.LIMIT_SIZE_VIEW)
                    .in(language);
            case TIME -> (check ? ViewWord.LIMIT_TIME_CHECK : ViewWord.LIMIT_TIME_VIEW)
                    .in(language);
            case PAGES -> ViewWord.LIMIT_PAGES.in(language);
        };
    }

    private void deadEnd() {
        DeadEnd end = content.deadEnd;
        out.append("<section class=\"card state")
                .append(end.kind() == DeadEnd.Kind.LIMIT ? " limit" : "").append("\">\n")
                .append("<p class=\"state-head\">")
                .append(escape(head(end, language, content.reportAsked))).append("</p>\n");
        if (end.kind() == DeadEnd.Kind.SEVERAL) {
            candidates(end.candidates());
        }
        ask(end.kind()).ifPresent(sentence -> out.append("<p>")
                .append(escape(sentence.in(language))).append("</p>\n"));
        if (end.kind() == DeadEnd.Kind.LIMIT) {
            option(end.limit());
        }
        out.append("</section>\n");
    }

    /** Writes the option that raises a bound, as code on a line of its own. */
    private void option(Limit limit) {
        if (limit.option() != null) {
            out.append("<p><code>").append(escape(text(limit.option())))
                    .append("</code></p>\n");
        }
    }

    /** Writes the attachments that could each be the invoice, with the command that opens each. */
    private void candidates(List<Candidate> candidates) {
        out.append("<table class=\"candidates\">\n<tr><th>")
                .append(escape(ViewWord.COLUMN_POSITION.in(language))).append("</th><th>")
                .append(escape(ViewWord.COLUMN_NAME.in(language))).append("</th><th>")
                .append(escape(ViewWord.COLUMN_CONTENT.in(language)))
                .append("</th><th class=\"size\">")
                .append(escape(ViewWord.COLUMN_SIZE.in(language))).append("</th><th>")
                .append(escape(ViewWord.COLUMN_RELATIONSHIP.in(language))).append("</th><th>")
                .append(escape(ViewWord.COLUMN_OPEN_WITH.in(language))).append("</th></tr>\n");
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

    // ------------------------------------------------------------------- the tabs

    /** The tabs of the page, in the order they stand in. */
    private enum Tab {

        PDF("pdf"), INVOICE("invoice"), KOSIT("kosit"), REPORT("report");

        private final String id;

        Tab(String id) {
            this.id = id;
        }
    }

    /** Returns the tabs that have content, in their order. */
    private List<Tab> present() {
        List<Tab> tabs = new ArrayList<>();
        if (content.received != null) {
            tabs.add(Tab.PDF);
        }
        if (content.deadEnd == null && content.document != null && content.letter != null) {
            tabs.add(Tab.INVOICE);
        }
        if (content.deadEnd == null && content.kosit != null) {
            tabs.add(Tab.KOSIT);
        }
        if (content.outcome != null) {
            tabs.add(Tab.REPORT);
        }
        return tabs;
    }

    private String label(Tab tab) {
        return switch (tab) {
            case PDF -> ViewWord.TAB_PDF.in(language);
            case INVOICE -> ViewWord.TAB_INVOICE.in(language);
            case KOSIT -> ViewWord.TAB_KOSIT.in(language);
            case REPORT -> ReportWord.TITLE.in(language);
        };
    }

    /**
     * Writes the tabs: one radio button and its label per tab, the first checked, and a
     * pane per tab that the style sheet shows while its button is checked.
     */
    private void tabs() {
        List<Tab> tabs = present();
        if (tabs.isEmpty()) {
            return;
        }
        out.append("<main class=\"tabs\">\n");
        boolean first = true;
        for (Tab tab : tabs) {
            out.append("<input type=\"radio\" name=\"tab\" id=\"tab-").append(tab.id)
                    .append('"').append(first ? " checked" : "").append("><label for=\"tab-")
                    .append(tab.id).append("\">").append(escape(label(tab)))
                    .append("</label>\n");
            first = false;
        }
        for (Tab tab : tabs) {
            out.append("<section class=\"pane\" id=\"pane-").append(tab.id)
                    .append("\" aria-label=\"").append(escape(label(tab))).append("\">\n");
            switch (tab) {
                case PDF -> frame("received/" + content.received, label(tab), false);
                case INVOICE -> invoice();
                case KOSIT -> kosit();
                case REPORT -> report();
            }
            out.append("</section>\n");
        }
        out.append("</main>\n");
    }

    private void frame(String file, String title, boolean sandboxed) {
        out.append("<iframe").append(sandboxed ? " sandbox=\"allow-scripts\"" : "")
                .append(" src=\"").append(href(file)).append("\" title=\"")
                .append(escape(title)).append("\"></iframe>\n");
    }

    /** Writes the letter, or why there is none. */
    private void invoice() {
        Letter letter = content.letter;
        if (letter.file().isPresent()) {
            out.append("<p class=\"caption\">").append(escape(ViewWord.CAPTION_INVOICE
                    .in(language))).append("</p>\n");
            frame(letter.file().get(), label(Tab.INVOICE), false);
        } else if (letter.limit().isPresent()) {
            out.append("<p>").append(escape(limit(letter.limit().get(), language,
                    content.reportAsked))).append("</p>\n");
            option(letter.limit().get());
        } else {
            out.append("<p>").append(escape(ViewWord.NOT_RENDERED.in(language)))
                    .append("</p>\n");
        }
    }

    /**
     * Writes the KoSIT visualization in a sandboxed frame: its script runs for its own tabs,
     * in an origin of its own, and reaches nothing of the page.
     */
    private void kosit() {
        if (!content.exportNotes.isEmpty()) {
            out.append("<p class=\"caption\">").append(escape(ViewWord.KOSIT_INCOMPLETE
                    .in(language))).append("</p>\n");
        }
        frame(content.kosit, label(Tab.KOSIT), true);
    }

    /** Writes the report: its one link, the verdict in the page's words, and its sections. */
    private void report() {
        out.append("<div class=\"report\">\n");
        if (content.reportPdf != null) {
            out.append("<p class=\"save\"><a href=\"").append(href(content.reportPdf))
                    .append("\" download>").append(escape(ViewWord.SAVE_PDF.in(language)))
                    .append("</a></p>\n");
        }
        ReportOptions options = ReportOptions.defaults().withLanguage(language);
        ReportHtml.card(out, content.outcome, options, token -> word(token, language));
        ReportHtml.sections(out, content.outcome, options, REPORT_LEVEL);
        out.append("</div>\n");
    }

    private void footer() {
        out.append("<footer>\n<p>").append(escape(ViewWord.FOLDER.in(language)))
                .append(" <span>").append(escape(text(content.directory))).append("</span> · ")
                .append(escape(text(content.tool))).append("</p>\n</footer>\n");
    }

    // ------------------------------------------------------------------- helpers

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

    /** The facts of the header, read from the document and written as the letter writes them. */
    private static final class Facts {

        private final SemanticDocument document;
        private final Registry registry;
        private final RenderLanguage language;

        Facts(SemanticDocument document, Registry registry, RenderLanguage language) {
            this.document = document;
            this.registry = registry;
            this.language = language;
        }

        /** Returns the type of the document by name, where it is not a plain invoice. */
        Optional<String> kind() {
            String code = content("/BT-3");
            if (code.isEmpty() || "380".equals(code)) {
                return Optional.empty();
            }
            return Optional.of(codeList("BT-3").flatMap(DisplayNames.CodeList::of)
                    .flatMap(list -> DisplayNames.of(list, code, language))
                    .orElse(code));
        }

        /** Returns who the invoice is from and to whom it goes. */
        String parties() {
            return orAbsent(content("/BG-4/BT-27")) + " → " + orAbsent(content("/BG-7/BT-44"));
        }

        String number() {
            return content("/BT-1");
        }

        String date() {
            return formatted("/BT-2", SemanticType.DATE);
        }

        /** Returns the amount the invoice closes with, the currency of the document behind it. */
        String due() {
            String amount = formatted("/BG-22/BT-115", SemanticType.AMOUNT);
            if (amount.isEmpty()) {
                return "";
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
     * Why a page shows no invoice: the run reached a dead end before a document was built.
     *
     * @param kind       which dead end
     * @param candidates the attachments that could each be the invoice, for
     *                   {@link Kind#SEVERAL}; empty for every other kind
     * @param limit      the bound that was met, for {@link Kind#LIMIT}; {@code null} for
     *                   every other kind
     */
    public record DeadEnd(Kind kind, List<Candidate> candidates, Limit limit) {

        /**
         * Checks the parts and copies the list.
         *
         * @param kind       which dead end
         * @param candidates the attachments, for {@link Kind#SEVERAL}
         * @param limit      the bound, for {@link Kind#LIMIT}
         */
        public DeadEnd {
            Objects.requireNonNull(kind, "kind");
            candidates = List.copyOf(candidates);
            if ((kind == Kind.LIMIT) != (limit != null)) {
                throw new IllegalArgumentException("a limit, and only a limit, names its bound");
            }
        }

        /**
         * Returns a dead end that says no more than which it is.
         *
         * @param kind which dead end, neither {@link Kind#SEVERAL} nor {@link Kind#LIMIT}
         * @return the dead end
         */
        public static DeadEnd of(Kind kind) {
            return new DeadEnd(kind, List.of(), null);
        }

        /**
         * Returns the dead end of several candidates.
         *
         * @param candidates the attachments that could each be the invoice
         * @return the dead end
         */
        public static DeadEnd several(List<Candidate> candidates) {
            return new DeadEnd(Kind.SEVERAL, candidates, null);
        }

        /**
         * Returns the dead end of a bound that was met.
         *
         * @param limit the bound
         * @return the dead end
         */
        public static DeadEnd limit(Limit limit) {
            return new DeadEnd(Kind.LIMIT, List.of(), limit);
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
     * A bound of the run that was met: what kind of bound, in the terms a reader has for it,
     * and the option that raises it.
     *
     * @param kind   what the bound is about
     * @param option the option that raises it, or {@code null} where none does
     */
    public record Limit(Kind kind, String option) {

        /**
         * Checks the kind.
         *
         * @param kind   what the bound is about
         * @param option the option that raises it, or {@code null}
         */
        public Limit {
            Objects.requireNonNull(kind, "kind");
        }

        /** What a bound is about. */
        public enum Kind {

            /** The size of the file or of what was read out of it. */
            SIZE,

            /** The time the run was given. */
            TIME,

            /** The pages of the letter. */
            PAGES
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
     * @param command      the command line that opens it
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
         * @param command      the command line that opens it
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
     * The letter of the invoice tab: the file that was written, the bound the rendering met,
     * or neither, where the invoice could not be rendered.
     *
     * @param file  the file name of the letter, relative to the page
     * @param limit the bound the rendering met
     */
    public record Letter(Optional<String> file, Optional<Limit> limit) {

        /**
         * Checks that at most one of the two is present.
         *
         * @param file  the file name of the letter
         * @param limit the bound
         */
        public Letter {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(limit, "limit");
            if (file.isPresent() && limit.isPresent()) {
                throw new IllegalArgumentException("a letter is a file or a limit, not both");
            }
        }

        /**
         * Returns the letter that was written.
         *
         * @param file its file name, relative to the page
         * @return the letter
         */
        public static Letter written(String file) {
            return new Letter(Optional.of(file), Optional.empty());
        }

        /**
         * Returns the letter that could not be rendered.
         *
         * @return the letter
         */
        public static Letter refused() {
            return new Letter(Optional.empty(), Optional.empty());
        }

        /**
         * Returns the letter that met a bound.
         *
         * @param limit the bound
         * @return the letter
         */
        public static Letter limited(Limit limit) {
            return new Letter(Optional.empty(), Optional.of(limit));
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
        private final String directory;
        private final String tool;
        private String received;
        private String notEn16931;
        private SemanticDocument document;
        private Registry registry;
        private ValidationOutcome outcome;
        private boolean reportAsked;
        private String reportPdf;
        private Letter letter;
        private String kosit;
        private List<String> exportNotes = List.of();
        private DeadEnd deadEnd;

        /**
         * Starts the content of a page.
         *
         * @param language  the language of the page, of its words and of the values
         * @param fileName  the name of the input file, the heading of the page
         * @param directory the absolute directory the page and its files lie in
         * @param tool      the tool that wrote it, {@code esj} and its version
         */
        public Content(RenderLanguage language, String fileName, String directory,
                       String tool) {
            this.language = Objects.requireNonNull(language, "language");
            this.fileName = Objects.requireNonNull(fileName, "fileName");
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
         * @param value the document
         * @param terms the registry of its edition, which names its codes; {@code null}
         *              where this build carries none
         * @return this content
         */
        public Content document(SemanticDocument value, Registry terms) {
            this.document = Objects.requireNonNull(value, "value");
            this.registry = terms;
            return this;
        }

        /**
         * Gives the report of the run that validated the invoice, and the PDF it was
         * written to.
         *
         * @param value the outcome
         * @param pdf   the file name of its PDF form, or {@code null} where none was written
         * @return this content
         */
        public Content report(ValidationOutcome value, String pdf) {
            this.outcome = Objects.requireNonNull(value, "value");
            this.reportPdf = pdf;
            return this;
        }

        /**
         * Records that the run was asked to check the invoice, which is what a bound that was
         * met is a bound of.
         *
         * @return this content
         */
        public Content reportAsked() {
            this.reportAsked = true;
            return this;
        }

        /**
         * Gives the letter of the invoice tab.
         *
         * @param value the letter, its limit, or that there is none
         * @return this content
         */
        public Content letter(Letter value) {
            this.letter = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Names the KoSIT visualization, with what did not reach it.
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
         * Makes the page a dead end.
         *
         * @param value which
         * @return this content
         */
        public Content deadEnd(DeadEnd value) {
            this.deadEnd = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Refuses a content that has a document and no letter for it. */
        private void check() {
            if (deadEnd == null && document != null && letter == null) {
                throw new IllegalStateException("a page with an invoice shows its letter, or"
                        + " says why there is none");
            }
        }
    }
}
