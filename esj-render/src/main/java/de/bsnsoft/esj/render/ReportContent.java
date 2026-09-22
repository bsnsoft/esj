package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.report.Text;
import de.bsnsoft.esj.report.ValidationOutcome;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What a validation report says, once and for both forms it is written in.
 *
 * <p>The HTML page and the PDF are two layouts of one report, and the one thing that must
 * not differ between them is the content: a reader who is handed the PDF and a program
 * that is handed the page have to be looking at the same run. So every line of text that
 * is neither markup nor a coordinate is decided here — the labels, the identity rows, the
 * status of a check row, the head of a finding — and the two layouts only place them.
 *
 * <p>What came out of the run in somebody else's words travels unchanged. A verdict, a
 * rule identifier, a message of an artefact and the label a pack gives one of its
 * components are printed as the run wrote them; what the run wrote in this project's own
 * voice is a {@link Text.Phrased} sentence and is written in the language of the report,
 * which is what keeps a German report from being half English. What this class does do to
 * a text of the run is {@link Characters#plain(String)}: a control character that only
 * directs the reading order, or that a terminal reads as a command, would make a line of
 * the report read differently from the finding it stands for, which is the one thing a
 * report must not allow (the same rule both renderings of this module follow).
 *
 * <h2>A report is bounded</h2>
 *
 * <p>Nothing in a document bounds what it can produce: one invoice can carry a hundred
 * thousand findings, one finding can carry a message of ten megabytes, and the rendering
 * of an invoice of a few megabytes is larger again. A report is written after the verdict
 * is known and is the file somebody has to open, so it is bounded here rather than left to
 * the document: a finding list is cut with the remainder named, a text longer than
 * {@link #MAX_TEXT_CHARACTERS} is cut with the number of characters named, and an invoice
 * whose rendering would be larger than {@link #MAX_INVOICE_CHARACTERS} is left out of the
 * page with the sentence that says so and what to run instead — measured by
 * {@link #renderingCharacters(SemanticDocument)} before the rendering is made, because a
 * rendering that does not fit in the page does not fit in the heap either. Everything a bound cut is still
 * in {@code --output json}, which is the form that carries all of it.
 */
final class ReportContent {

    /**
     * How many findings of one block a report prints before it names the remainder.
     *
     * <p>The rows above the list carry the true counts, which are never cut, so a reader
     * of a report that reached the bound still has the number; what the bound saves is the
     * reader of a report about an invoice with ten thousand defects, for whom the
     * ten-thousandth article is of no use at all.
     */
    static final int MAX_FINDINGS = 200;

    /** How many characters of one message or one place a report prints. */
    static final int MAX_TEXT_CHARACTERS = 2_000;

    /** How many observations about a rendering of the invoice a report prints. */
    static final int MAX_EXPORT_NOTES = 20;

    /**
     * How large the rendering of an invoice may be for the page to carry it.
     *
     * <p>The HTML form writes the whole rendering into one attribute, escaped character by
     * character, so a rendering of a few megabytes becomes a file no browser opens
     * comfortably. A report about a large invoice is more useful without the invoice than
     * unopenable with it, and the sentence that stands in its place says where the invoice
     * is to be had.
     */
    static final int MAX_INVOICE_CHARACTERS = 2_000_000;

    /**
     * What the vendored visualization writes for one value of the document, beside the
     * characters of the value itself.
     *
     * <p>It is a measurement and not a guess: the visualization was run over generated
     * documents of 820, 4 020 and 8 020 values, whose own texts are a few characters each,
     * and wrote 1 483, 1 448 and 1 443 characters per value. The number here is rounded up
     * from those, so that the estimate the bound is applied to is never the smaller of the
     * two.
     */
    static final int CHARACTERS_PER_VALUE = 1_500;

    /**
     * Returns about how many characters the vendored visualization writes for a document,
     * without running it.
     *
     * <p>It is what {@link #MAX_INVOICE_CHARACTERS} is applied to, and it is applied
     * before the rendering rather than to it, because the rendering is the thing that does
     * not fit: sixteen thousand invoice lines are about a hundred and eighty megabytes of
     * characters, which is three hundred and seventy megabytes of heap, and a report that
     * measured them after making them would take the run down with it and lose a verdict
     * that had already been reached.
     *
     * <p>Two terms, because a document is large in two ways that do not follow from one
     * another: how many values it has — one invoice line is eight of them and costs about
     * {@link #CHARACTERS_PER_VALUE} characters apiece — and how long the values are, since
     * a value's own text is written into the page as it stands. A single value of a
     * megabyte is a page of a megabyte, in a document of twenty values.
     *
     * @param document the document the invoice section is about
     * @return the estimate, saturated at {@link Integer#MAX_VALUE}
     */
    static int renderingCharacters(SemanticDocument document) {
        long characters = 0;
        for (SemanticValue value : document.values().values()) {
            characters += CHARACTERS_PER_VALUE + value.content().length();
            if (characters >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) characters;
    }

    private ReportContent() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the verdict of a run as the report writes it.
     *
     * <p>Neither the three states nor the word that stands for none of them is translated:
     * they are the words the tool writes in every one of its forms, and a second spelling
     * of one of them would be a second thing for a reader to look up.
     *
     * @param outcome what the run came to
     * @return the word, or {@link ValidationOutcome#NO_VERDICT} where the run reached none
     */
    static String verdict(ValidationOutcome outcome) {
        return outcome.verdict().map(Enum::name).orElse(ValidationOutcome.NO_VERDICT);
    }

    /**
     * Returns the verdicts of the subjects a run judged apart, as labelled lines.
     *
     * <p>A run over a container judged the file and the invoice inside it separately, and
     * the one word above them cannot say that a conformant container carries no EN 16931
     * invoice, or that a sound invoice sits in a file that is wrong about it. Both lines
     * are printed under the word for that reason, in both forms.
     *
     * @param outcome  what the run came to
     * @param language the language of the report
     * @return the lines, in report order, empty for a run with one subject
     */
    static List<Line> subjects(ValidationOutcome outcome, RenderLanguage language) {
        List<Line> lines = new ArrayList<>();
        for (ValidationOutcome.Subject subject : outcome.subjects()) {
            ReportWord label = switch (subject.judged()) {
                case CONTAINER -> ReportWord.SUBJECT_CONTAINER;
                case INVOICE -> ReportWord.SUBJECT_INVOICE;
            };
            String detail = subject.detail()
                    .map(text -> " (" + resolve(text, language) + ")").orElse("");
            lines.add(new Line(label.in(language), plain(subject.verdict()) + detail));
        }
        return List.copyOf(lines);
    }

    /**
     * Returns the identity of the run, as the label and value pairs the report prints.
     *
     * <p>A row whose value the run never had is left out rather than printed empty: there
     * is no semantic digest without a semantic document, and a line saying so would be a
     * line about this tool rather than about the invoice.
     *
     * <p>Three of the values come out of the document — the profile it names in BT-24, the
     * name the caller gave it, the syntax it turned out to be — so they are cut at
     * {@link #MAX_TEXT_CHARACTERS} like every other text of the run. The identity is the
     * block a reader trusts most and the one the PDF form repeats, and an element of a
     * stranger's invoice must not decide how long it is.
     *
     * @param outcome what the run came to
     * @param options the language, and the moment the caller passed
     * @return the rows, in report order
     */
    static List<Line> identity(ValidationOutcome outcome, ReportOptions options) {
        RenderLanguage language = options.language();
        ValidationOutcome.Identity identity = outcome.identity();
        List<Line> lines = new ArrayList<>();
        add(lines, ReportWord.INPUT, language, Optional.of(identity.input()));
        say(lines, ReportWord.SYNTAX, language, Optional.of(identity.syntax()));
        say(lines, ReportWord.READER, language, identity.reader());
        add(lines, ReportWord.SEMANTIC_MODEL, language, identity.semanticModel());
        add(lines, ReportWord.PROFILE, language, identity.profile());
        add(lines, ReportWord.INPUT_DIGEST, language, identity.inputSha256());
        add(lines, ReportWord.SEMANTIC_DIGEST, language, identity.semanticDigest());
        add(lines, ReportWord.DOCUMENT_DIGEST, language, identity.documentDigest());
        add(lines, ReportWord.SOURCE_DIGEST, language, identity.sourceSha256());
        boolean labelled = false;
        for (String pack : packs(identity)) {
            lines.add(new Line(labelled ? "" : ReportWord.PACKS.in(language),
                    cut(pack, language)));
            labelled = true;
        }
        add(lines, ReportWord.TOOL, language, Optional.of(identity.tool()));
        add(lines, ReportWord.TIME, language, options.time());
        return List.copyOf(lines);
    }

    /**
     * Returns one line per pack the run executed: what it was to the run, which pack it
     * was, and where it came from.
     *
     * @param identity the identity of the run
     * @return the lines, in the order the run lists the packs
     */
    static List<String> packs(ValidationOutcome.Identity identity) {
        List<String> lines = new ArrayList<>();
        for (ValidationOutcome.Pack pack : identity.packs()) {
            StringBuilder line = new StringBuilder(plain(pack.role())).append(": ")
                    .append(plain(pack.id())).append('/').append(plain(pack.version()));
            pack.release().ifPresent(release ->
                    line.append(", release ").append(plain(release)));
            pack.source().ifPresent(source -> line.append(" (").append(plain(source))
                    .append(')'));
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    /**
     * Returns the heading of one block.
     *
     * @param kind     which engine the block belongs to
     * @param language the language of the report
     * @return the heading
     */
    static String heading(ValidationOutcome.Block.Kind kind, RenderLanguage language) {
        return switch (kind) {
            case CONTAINER -> ReportWord.BLOCK_CONTAINER.in(language);
            case SYNTAX -> ReportWord.BLOCK_SYNTAX.in(language);
            case SEMANTIC -> ReportWord.BLOCK_SEMANTIC.in(language);
        };
    }

    /**
     * Returns what one row of the check table came to, as the report prints it.
     *
     * <p>Errors and warnings are counted apart, because only the first of them decides the
     * verdict and a row that added them together would make a document with two remarks
     * look like one with two defects.
     *
     * <p>A row that declares rather than checks carries its declaration alone. The status
     * column is what a reader skims, and a row that says {@code OK} where nothing was
     * checked reads as a check that passed.
     *
     * @param row      the row
     * @param language the language of the report
     * @return the status, with the reason a row that did not run carries
     */
    static String status(ValidationOutcome.Row row, RenderLanguage language) {
        Optional<String> detail = row.detail().map(text -> resolve(text, language));
        String tail = detail.map(text -> ": " + text).orElse("");
        return switch (row.status()) {
            case OK -> ReportWord.STATUS_OK.in(language) + tail;
            case SKIPPED -> ReportWord.STATUS_SKIPPED.in(language) + tail;
            case NOT_APPLICABLE -> ReportWord.STATUS_NOT_APPLICABLE.in(language) + tail;
            case NO_VERDICT -> ReportWord.STATUS_NO_VERDICT.in(language) + tail;
            case FOUND -> counted(row, language) + tail;
            case DECLARED -> detail.orElseGet(() -> ReportWord.STATUS_DECLARED.in(language));
        };
    }

    /** Returns "n errors, m warnings", leaving out the half that is zero. */
    private static String counted(ValidationOutcome.Row row, RenderLanguage language) {
        StringBuilder found = new StringBuilder();
        if (row.errors() > 0) {
            found.append(row.errors()).append(' ').append(row.errors() == 1
                    ? ReportWord.ERROR.in(language) : ReportWord.ERRORS.in(language));
        }
        if (row.warnings() > 0) {
            if (found.length() > 0) {
                found.append(", ");
            }
            found.append(row.warnings()).append(' ').append(row.warnings() == 1
                    ? ReportWord.WARNING.in(language) : ReportWord.WARNINGS.in(language));
        }
        return found.length() == 0 ? ReportWord.STATUS_OK.in(language) : found.toString();
    }

    /**
     * Returns the findings of one block that a report prints.
     *
     * <p>Where a block carries more than a report prints, what is printed is decided by
     * what a finding weighs and not by where it stands. One block can hold the
     * observations of two engines — the importer's warnings about what it could not carry
     * stand in front of the rules that decided the verdict — and a cut that took the first
     * two hundred of them would hand a reader a report that says {@code INVALID} and names
     * no error. So the heaviest are kept, ties going to the earlier finding, and they are
     * printed in the order the block has them: the order is the engines' and only the
     * choice is the report's.
     *
     * @param block the block
     * @return the findings, in the order the engines report them, cut at
     *         {@link #MAX_FINDINGS} by severity
     */
    static List<ValidationOutcome.Finding> findings(ValidationOutcome.Block block) {
        List<ValidationOutcome.Finding> findings = block.findings();
        if (findings.size() <= MAX_FINDINGS) {
            return findings;
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < findings.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator
                .comparingInt((Integer index) -> findings.get(index).severity().ordinal())
                .thenComparingInt(index -> index));
        List<Integer> kept = new ArrayList<>(order.subList(0, MAX_FINDINGS));
        Collections.sort(kept);
        List<ValidationOutcome.Finding> printed = new ArrayList<>();
        for (int index : kept) {
            printed.add(findings.get(index));
        }
        return List.copyOf(printed);
    }

    /**
     * Returns what a block says under its findings: how many were left out, and which of
     * them another engine of the same run reported as well.
     *
     * <p>The second sentence is what keeps a two-engine list readable. Over an XML input
     * the official artefacts and the native rules check the same rules of the standard and
     * both report what they find, so one defect can stand twice on one page; naming the
     * identifiers says which of the articles are one defect seen twice, without either
     * report being merged into the other.
     *
     * @param block    the block
     * @param language the language of the report
     * @return the sentences, in report order, empty where a block has nothing to add
     */
    static List<String> notes(ValidationOutcome.Block block, RenderLanguage language) {
        List<String> notes = new ArrayList<>();
        int left = block.findings().size() - MAX_FINDINGS;
        if (left > 0) {
            notes.add(ReportWord.MORE_FINDINGS.in(language,
                    List.of(Integer.toString(left))));
        }
        if (!block.alsoReported().isEmpty()) {
            notes.add(ReportWord.ALSO_REPORTED.in(language,
                    List.of(plain(String.join(", ", block.alsoReported())))));
        }
        return List.copyOf(notes);
    }

    /**
     * Returns what did not reach a rendering of the invoice, one line each.
     *
     * <p>The lines are the exporter's own, because they name a path of the document and a
     * reason of the representation, and the list is cut for the reason every list of a
     * report is cut: one document can leave a thousand values behind, and the thousandth
     * line helps nobody. {@code esj render} over the same document prints all of them.
     *
     * @param notes    the lines, as the exporter wrote them
     * @param language the language of the report
     * @return the lines a report prints, with the remainder named where there is one
     */
    static List<String> exportNotes(List<String> notes, RenderLanguage language) {
        List<String> lines = new ArrayList<>();
        for (String note : notes) {
            if (lines.size() == MAX_EXPORT_NOTES) {
                lines.add(ReportWord.MORE_NOTES.in(language, List.of(
                        Integer.toString(notes.size() - MAX_EXPORT_NOTES))));
                break;
            }
            lines.add(cut(plain(note), language));
        }
        return List.copyOf(lines);
    }

    /**
     * Returns the head of a finding: what it is, how much it weighs, and who said it.
     *
     * <p>Where a core invoice usage specification asked for another level than the finding
     * carried, both are on the line. The first decides the verdict and the second is the
     * level the finding had before — the one the artefact wrote, or, for a rule of the
     * native pack, the one the standard gives it — and a reader who sees only one of them
     * cannot see that the two publishers of the rule disagree. The line names the profile
     * that levelled it wherever the other level is the standard's, exactly as the printed
     * lines of the run do.
     *
     * @param finding  the finding
     * @param language the language of the report
     * @return {@code "BR-CO-10 [error] — native, pack en16931/1.3.16"}
     */
    static String head(ValidationOutcome.Finding finding, RenderLanguage language) {
        StringBuilder head = new StringBuilder(plain(finding.code()))
                .append(" [").append(finding.severity().token());
        finding.flag().ifPresent(flag -> head.append(", ").append(finding.levelledBy()
                .map(profile -> ReportWord.LEVELLED.in(language,
                        List.of(plain(flag), plain(profile))))
                .orElseGet(() -> ReportWord.FLAGGED.in(language, List.of(plain(flag))))));
        head.append("] — ").append(plain(finding.category())).append(", ")
                .append(plain(finding.engine()));
        finding.pack().ifPresent(pack -> head.append(", pack ").append(plain(pack)));
        return head.toString();
    }

    /**
     * Returns where a finding is, in the terms of the engine that produced it.
     *
     * @param finding  the finding
     * @param language the language of the report
     * @return the places, joined, or an empty string where the engine named none
     */
    static String where(ValidationOutcome.Finding finding, RenderLanguage language) {
        return cut(plain(String.join("  ·  ", finding.locations())), language);
    }

    /**
     * Returns the sentence a finding carries, as the engine wrote it.
     *
     * @param finding  the finding
     * @param language the language of the report
     * @return the message
     */
    static String message(ValidationOutcome.Finding finding, RenderLanguage language) {
        return cut(plain(finding.message()), language);
    }

    /**
     * Returns a text of the run, cut where it is longer than a report prints.
     *
     * @param text     the text
     * @param language the language of the report
     * @return the text, with what was left out counted where anything was
     */
    static String cut(String text, RenderLanguage language) {
        return text.length() <= MAX_TEXT_CHARACTERS ? text
                : text.substring(0, MAX_TEXT_CHARACTERS) + " "
                        + ReportWord.CUT.in(language, List.of(
                                Integer.toString(text.length() - MAX_TEXT_CHARACTERS)));
    }

    /**
     * Returns one text of a report in the language it is written in.
     *
     * @param text     the text
     * @param language the language of the report
     * @return this project's sentence in that language, or the words of the run, either of
     *         them cut at {@link #MAX_TEXT_CHARACTERS}
     */
    static String resolve(Text text, RenderLanguage language) {
        if (text instanceof Text.Phrased phrased) {
            return ReportWord.of(phrased.phrase()).in(language, phrased.arguments().stream()
                    .map(argument -> cut(plain(argument), language)).toList());
        }
        return cut(plain(((Text.Words) text).words()), language);
    }

    /**
     * Returns a text of the run in the form a report may carry it.
     *
     * @param text the text
     * @return the text without the characters that direct the reading order or address a
     *         terminal
     */
    static String plain(String text) {
        return Characters.plain(text);
    }

    private static void add(List<Line> lines, ReportWord label, RenderLanguage language,
                            Optional<String> value) {
        value.ifPresent(text ->
                lines.add(new Line(label.in(language), cut(plain(text), language))));
    }

    private static void say(List<Line> lines, ReportWord label, RenderLanguage language,
                            Optional<Text> value) {
        value.ifPresent(text ->
                lines.add(new Line(label.in(language), resolve(text, language))));
    }

    /**
     * One labelled line of the identity.
     *
     * @param label what it is called, empty where it continues the line above it
     * @param value what it says
     */
    record Line(String label, String value) {
    }
}
