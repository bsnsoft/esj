package de.bsnsoft.esj.render.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.render.RenderLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The invoice as text: what of a document reaches it, in how wide a line, and in which
 * characters.
 *
 * <p>The claim is the one {@code LetterCoverageTest} makes for the letter, made for the text
 * it is written from: every value of the document is in the text, in the form the letter
 * writes it — over the conformance corpus, the examples and a document of every term, in
 * both languages, and at three widths, none of whose lines is longer than its width. A value
 * may be wrapped over lines, and in a table the lines of a wrapped cell stand beside the
 * other cells of its row, so a value is looked for in the text and in the text with every
 * row of a table read cell by cell ({@link #unwrapped(String)}).
 */
class InvoiceTextTest {

    private static final Registry REGISTRY = Registry.en16931WithXrechnung();

    private static final List<Integer> WIDTHS = List.of(60, 100, 200);

    /** The rule under the header of a table, and over its foot: runs of dashes, two apart. */
    private static final Pattern RULE = Pattern.compile("-+(  -+)+");

    static List<String> instances() {
        return Corpus.instances();
    }

    static List<String> examples() {
        return Corpus.EXAMPLES;
    }

    @ParameterizedTest
    @MethodSource("instances")
    void everyValueOfAnInstanceIsInItsTextAtEveryWidth(String instance) {
        assertWhole(REGISTRY, Corpus.instance(instance), instance);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void everyValueOfAnExampleIsInItsTextAtEveryWidth(String example) {
        assertWhole(REGISTRY, Corpus.example(example), "examples/" + example);
    }

    /** A document with a value at every path of the registry, which no invoice has. */
    @Test
    void everyTermOfTheRegistryIsInTheText() {
        assertWhole(REGISTRY, Documents.everyTerm(), "a document of every term");
    }

    /** A document of the 2026 edition is written by the registry of that edition, whole. */
    @Test
    @EnabledIf("carries2026")
    void aDocumentOfTheOtherEditionIsInItsTextWhole() {
        Registry registry = Registry.forEdition("2026");
        SemanticDocument document = Corpus.example("edition-2026");
        assertWhole(registry, document, "the 2026 example");
        String text = InvoiceText.render(document, registry, RenderLanguage.ENGLISH, 100);
        assertTrue(text.contains("Invoice issue time (/BT-166)"), text);
    }

    static boolean carries2026() {
        return Registry.editionKeys().contains("2026");
    }

    @Test
    void aCreditNoteIsCalledOneAndClosesWithTheAmountCredited() {
        String text = InvoiceText.render(Corpus.example("credit-note"), REGISTRY,
                RenderLanguage.GERMAN, 100);
        assertTrue(text.startsWith("Verkäufer\n---------\n"), text);
        assertTrue(text.contains("\nGutschrift GU-2026-0004\n=======================\n"
                + "zur Rechnung RE-2026-0042 vom 03.02.2026\n"), text);
        assertTrue(text.contains("Gutschriftsnummer  GU-2026-0004"), text);
        assertTrue(text.matches("(?s).*=+\n +Gutschriftsbetrag +355,81 EUR\n.*"), text);
    }

    @Test
    void allowancesAndChargesAreTablesOfTheirOwn() {
        String allowances = InvoiceText.render(Corpus.example("allowances"), REGISTRY,
                RenderLanguage.ENGLISH, 100);
        assertTrue(allowances.contains("\nDocument level allowances\n-------------------------\n"),
                allowances);
        assertTrue(allowances.contains("Annual volume rebate"), allowances);
        String charges = InvoiceText.render(Corpus.example("charges"), REGISTRY,
                RenderLanguage.ENGLISH, 100);
        assertTrue(charges.contains("\nDocument level charges\n----------------------\n"),
                charges);
    }

    /** The letter draws a payment code; the text has none, and says nothing about one. */
    @Test
    void theTextNamesNoPaymentCode() {
        for (RenderLanguage language : RenderLanguage.values()) {
            String text = InvoiceText.render(Corpus.example("standard-invoice"), REGISTRY,
                    language, 100);
            assertFalse(text.contains(Word.PAYMENT_CODE.in(language)), text);
            assertTrue(text.contains(language == RenderLanguage.GERMAN
                    ? "IBAN                 DE89 3704 0044 0532 0130 00"
                    : "IBAN                    DE89 3704 0044 0532 0130 00"), text);
        }
    }

    /** Plain text: no colour, no box drawing, nothing a terminal reads as a command. */
    @Test
    void aTerminalIsHandedTextAndNoCommand() {
        SemanticDocument document = Corpus.example("standard-invoice").toBuilder()
                .set(SemanticPath.of("/BG-7/BT-44"),
                        SemanticValue.of("\u001b[2J\u001b]0;owned\u0007Mus\u202eter\tAG"))
                .build();
        String text = InvoiceText.render(document, REGISTRY, RenderLanguage.ENGLISH, 100);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            assertTrue(c == '\n' || c >= 0x20 && c != 0x7f && !(c >= 0x80 && c < 0xa0),
                    "no control but the line feed, at " + i + ": " + text);
            assertFalse(c >= 0x2500 && c <= 0x257f, "no box drawing: " + text);
            assertFalse(Characters.directional(c), "no reading-order control: " + text);
        }
        assertTrue(text.contains("[2J ]0;owned Mus ter AG"), text);
    }

    @Test
    void theSameDocumentAndWidthGiveTheSameText() {
        SemanticDocument document = Corpus.example("multiple-lines");
        assertEquals(InvoiceText.render(document, REGISTRY, RenderLanguage.GERMAN, 80),
                InvoiceText.render(document, REGISTRY, RenderLanguage.GERMAN, 80));
    }

    @Test
    void aWidthOutOfBoundsIsRefused() {
        SemanticDocument document = Corpus.example("minimal");
        assertThrows(IllegalArgumentException.class, () ->
                InvoiceText.render(document, REGISTRY, RenderLanguage.GERMAN, 39));
        assertThrows(IllegalArgumentException.class, () ->
                InvoiceText.render(document, REGISTRY, RenderLanguage.GERMAN, 1001));
    }

    /** A line is broken at a space, a word too long for it inside the word, and nothing is lost. */
    @Test
    void aTextIsWrappedWithoutLosingACharacter() {
        assertEquals(List.of("one two", "three"), InvoiceText.wrap("one two three", 7));
        assertEquals(List.of("abcde", "fghij", "k"), InvoiceText.wrap("abcdefghijk", 5));
        assertEquals(List.of("ab", "cdefg", "hi"), InvoiceText.wrap("ab cdefghi", 5));
        assertEquals(List.of("äöüß", "x"), InvoiceText.wrap("äöüß x", 4));
        assertEquals(List.of("first", "", "second"), InvoiceText.wrap("first\n\nsecond", 10));
        assertEquals(List.of(), InvoiceText.wrap("", 10));
    }

    // ------------------------------------------------------------------- helpers

    private static void assertWhole(Registry registry, SemanticDocument document,
                                    String what) {
        for (RenderLanguage language : RenderLanguage.values()) {
            for (int width : WIDTHS) {
                String text = InvoiceText.render(document, registry, language, width);
                for (String line : text.split("\n", -1)) {
                    assertTrue(InvoiceText.length(line) <= width, what + " in " + language
                            + " at " + width + ": a line of " + InvoiceText.length(line)
                            + " characters: " + line);
                }
                assertEquals(List.of(), missing(registry, document, text, language),
                        what + " in " + language + " at " + width + ": every value is in it");
            }
        }
    }

    /** Returns the values of a document the text does not carry, as the letter writes them. */
    private static List<String> missing(Registry registry, SemanticDocument document,
                                        String text, RenderLanguage language) {
        String rows = unwrapped(text);
        List<String> missing = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            SemanticPath path = entry.getKey();
            SemanticValue value = entry.getValue();
            SemanticType type = registry.datatype(path.term()).orElse(SemanticType.TEXT);
            String shown = Formats.asWritten(type, value.content(), language);
            if (type != SemanticType.BINARY_OBJECT && !shows(text, rows, shown)) {
                missing.add(path + " = " + value.content());
            }
            for (String component : new String[] {value.scheme(), value.schemeVersion(),
                                                  value.mimeCode(), value.filename()}) {
                if (component != null && !shows(text, rows, component)) {
                    missing.add(path + " component = " + component);
                }
            }
        }
        return missing;
    }

    private static boolean shows(String text, String rows, String value) {
        String plain = Characters.plain(value).replace('\t', ' ');
        return Pdf.shows(text, plain) || Pdf.shows(rows, plain);
    }

    /**
     * Returns every row of every table of a text read cell by cell: per column, what the
     * first line of the row has in it, followed by every further line of the row. The cell
     * that wraps continues on those lines, so its value is whole in one of them. Rows of more
     * than one line are parted by a blank line; a table without one has rows of one line, or
     * a single row.
     */
    static String unwrapped(String text) {
        List<String> lines = List.of(text.split("\n", -1));
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (!RULE.matcher(lines.get(i)).matches()) {
                continue;
            }
            List<int[]> columns = columns(lines.get(i));
            int end = i + 1;
            while (end < lines.size() && !RULE.matcher(lines.get(end)).matches()) {
                end++;
            }
            List<String> region = lines.subList(i + 1, Math.min(end, lines.size()));
            List<List<String>> blocks = new ArrayList<>();
            if (region.contains("")) {
                List<String> block = new ArrayList<>();
                for (String line : region) {
                    if (line.isEmpty()) {
                        blocks.add(block);
                        block = new ArrayList<>();
                    } else {
                        block.add(line);
                    }
                }
                blocks.add(block);
            } else {
                // Rows of one line each, or a table of one row, which has no row to part
                // from another: both readings are taken.
                region.forEach(line -> blocks.add(List.of(line)));
                blocks.add(region);
            }
            for (List<String> block : blocks) {
                if (block.isEmpty()) {
                    continue;
                }
                int[] first = block.get(0).codePoints().toArray();
                StringBuilder rest = new StringBuilder();
                for (String line : block.subList(1, block.size())) {
                    rest.append(' ').append(line.strip());
                }
                for (int[] column : columns) {
                    int from = Math.min(column[0], first.length);
                    int to = Math.min(column[1], first.length);
                    rows.append(new String(first, from, to - from).strip()).append(rest)
                            .append('\n');
                }
            }
            i = end;
        }
        return rows.toString();
    }

    /** Returns the columns a rule marks out, as the start and end of each. */
    private static List<int[]> columns(String rule) {
        List<int[]> columns = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= rule.length(); i++) {
            boolean dash = i < rule.length() && rule.charAt(i) == '-';
            if (dash && start < 0) {
                start = i;
            } else if (!dash && start >= 0) {
                columns.add(new int[] {start, i});
                start = -1;
            }
        }
        return columns;
    }
}
