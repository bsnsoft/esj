package de.bsnsoft.esj.rules.en16931;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.rules.RuleFinding;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * What the pack says about documents that are known to be good.
 *
 * <p>A rule pack is easy to write and hard to trust, and the first thing it owes a reader is
 * that it is quiet about invoices nobody faults. The 86 instances of the conformance corpus are
 * invoices the official validator accepts, and the eleven examples of the 2017 edition are
 * documents of the format; what this pack says about all ninety-seven is written down in
 * {@code conformance/rules/corpus.json}, and this test runs the pack and compares.
 *
 * <p>The comparison is exact and the file is a golden one. A finding that appears, disappears or
 * moves to another path is a change in what this pack says about a document nobody changed,
 * which is either a rule that was improved or a rule that was broken; either way the build stops
 * until somebody has written down which.
 * {@code conformance/rules/corpus.md} explains every row that is there.
 */
class En16931CorpusTest {

    /** One finding of the ledger: the document, the rule, the severity and where it looked. */
    private record Row(String document, String code, String severity, List<String> paths) {

        @Override
        public String toString() {
            return document + " " + code + " [" + severity + "] " + paths;
        }
    }

    private static final Pattern MEMBER = Pattern.compile("\"(\\w+)\": \"([^\"]*)\"");

    private static String ledger() {
        return Pack.text("/conformance/rules/corpus.json");
    }

    /**
     * Returns the documents the ledger was taken over.
     *
     * <p>The list is read from the ledger rather than from a directory, so that a document
     * that was measured and a document that is checked in cannot drift apart without the
     * count below noticing.
     */
    private static List<String> documents() {
        String text = ledger();
        String array = between(text, "\"documents\": [", "]");
        List<String> paths = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(array);
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        return paths;
    }

    private static List<Row> recorded() {
        String array = between(ledger(), "\"findings\": [", "\n  ]");
        List<Row> rows = new ArrayList<>();
        for (String object : array.split("\\},")) {
            if (!object.contains("\"document\"")) {
                continue;
            }
            String document = null;
            String code = null;
            String severity = null;
            Matcher matcher = MEMBER.matcher(object);
            while (matcher.find()) {
                switch (matcher.group(1)) {
                    case "document" -> document = matcher.group(2);
                    case "code" -> code = matcher.group(2);
                    case "severity" -> severity = matcher.group(2);
                    default -> { }
                }
            }
            List<String> paths = new ArrayList<>();
            Matcher path = Pattern.compile("\"(/[^\"]*)\"").matcher(between(object, "\"paths\": [", "]"));
            while (path.find()) {
                paths.add(path.group(1));
            }
            rows.add(new Row(document, code, severity, List.copyOf(paths)));
        }
        return rows;
    }

    private static String between(String text, String open, String close) {
        int from = text.indexOf(open);
        if (from < 0) {
            throw new IllegalStateException("the ledger has no " + open);
        }
        from += open.length();
        int to = text.indexOf(close, from);
        return text.substring(from, to < 0 ? text.length() : to);
    }

    private static List<Row> run(List<String> documents) {
        List<Row> rows = new ArrayList<>();
        for (String document : documents) {
            SemanticDocument invoice = EsjReader.strict().read(Pack.bytes("/" + document));
            for (RuleFinding finding : Pack.ENGINE.evaluate(invoice)) {
                rows.add(new Row(document, finding.code(), finding.severity().token(),
                        finding.paths()));
            }
        }
        return rows;
    }

    @Test
    void thePackSaysAboutTheCorpusAndTheExamplesWhatTheLedgerRecords() {
        List<String> documents = documents();

        assertEquals(recorded(), run(documents), "conformance/rules/corpus.json is a golden file:"
                + " a finding that appears, disappears or moves is a change in what this pack says"
                + " about a document nobody changed");
    }

    @Test
    void theLedgerWasTakenOverTheWholeCorpusAndAllTheExamples() {
        List<String> documents = documents();

        assertEquals(86, documents.stream().filter(path -> path.startsWith("conformance/")).count());
        assertEquals(11, documents.stream().filter(path -> path.startsWith("examples/")).count());
    }

    /**
     * Every rule this pack reports on a corpus instance is a rule the official artefacts report
     * on the same instance.
     *
     * <p>The corpus is 86 invoices the official validator accepts, and it accepts them while
     * reporting on four of them: the core invoice usage specification those documents name
     * levels a fatal finding of the artefacts down to information, which decides the verdict
     * and does not make the finding go away.
     * {@code conformance/syntax/ledger.json} records that instance by instance, and
     * {@code corpus.md} puts the two side by side. A finding of this pack carries the severity
     * its rule declares; what a profile makes of that is a decision of whoever runs it.
     */
    @Test
    void everyRuleThatFiresOnACorpusInstanceIsOneTheOfficialArtefactsAlsoRaise() {
        Set<String> codes = new LinkedHashSet<>();
        for (Row row : run(documents())) {
            if (row.document().startsWith("conformance/")) {
                codes.add(row.code());
            }
        }

        assertEquals(Set.of("BR-CL-10", "BR-CL-13", "BR-CL-21", "BR-CO-16"), new TreeSet<>(codes),
                "BR-CO-16 on 05.01a-INVOICE_ubl, the two scheme identifier rules on"
                        + " 04.05a-INVOICE_uncefact and BR-CL-13 on the two cvd instances are what"
                        + " this pack reports about the corpus, and the EN 16931 Schematron of"
                        + " release 1.3.16 reports every one of them on the same instance");
    }

    @Test
    void theEightExamplesThatAreCompleteInvoicesCarryNoFinding() {
        List<String> complete = List.of(
                "examples/smallest-valid.esj.json", "examples/standard-invoice.esj.json",
                "examples/multiple-lines.esj.json", "examples/allowances.esj.json",
                "examples/charges.esj.json", "examples/self-billed.esj.json",
                "examples/credit-note.esj.json", "examples/b2c-gross.esj.json");

        assertEquals(List.of(), run(complete));
    }

    @Test
    void everyDocumentTheLedgerNamesIsInThisBuild() {
        for (String document : documents()) {
            assertTrue(Pack.bytes("/" + document).length > 0, document);
        }
    }
}
