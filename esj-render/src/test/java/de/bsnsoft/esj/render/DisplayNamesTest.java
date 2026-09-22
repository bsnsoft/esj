package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.model.Registry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The tables of display names, and where each half of them comes from.
 *
 * <p>Two things are checked, and the first is the one that matters. <b>The English names of
 * the four code lists are the names of the dated code list snapshots this repository
 * already carries</b>, code for code — so a table that was edited by hand, or a snapshot
 * that was fetched again at a later date and says something else, fails here rather than
 * putting a name of this project's own invention on a page under the appearance of a fact.
 * The two exceptions are stated in the files and asserted below: the document type 380 reads
 * <i>Invoice</i> rather than <i>Commercial invoice</i>, because there it is the title of a
 * page and not a row of a code list, and the unit XPP reads <i>Packaging piece</i> rather
 * than <i>Piece</i>, because <i>piece</i> is what Recommendation 20 calls H87 and a quantity
 * column has to tell the two apart.
 *
 * <p>The third thing checked is that no two codes of one table read alike in one language,
 * in either number. A page that wrote one word for two units would hide the difference the
 * document states, and the closing section that lists the codes could not give it back.
 *
 * <p>The German half is this project's wording and there is nothing to compare it against.
 * What is checked of it is that it is there: every table names every code it carries in
 * both languages, and a code no table names has no name rather than a made-up one.
 */
class DisplayNamesTest {

    /** Where the tables sit on the test classpath. */
    private static final String NAMES = "/de/bsnsoft/esj/render/names/";

    /** Where the dated code list snapshots sit. */
    private static final String SNAPSHOT = "/rules/en16931/1.3.16/codelists/";

    /** The date the snapshots this module read were taken. */
    private static final String TAKEN = "2026-09-19";

    /** Which snapshot each table takes its English names from. */
    private static final Map<String, List<String>> SOURCES = new LinkedHashMap<>(Map.of(
            "invoice-type", List.of("untdid-1001"),
            "unit", List.of("unece-rec20", "unece-rec21"),
            "payment-means", List.of("untdid-4461"),
            "vat-category", List.of("untdid-5305")));

    static List<String> tables() {
        return SOURCES.keySet().stream().sorted().toList();
    }

    /**
     * The English name of every code is the name of the snapshot it came from. The two
     * documented exceptions are the document type 380 and the unit XPP.
     */
    @ParameterizedTest
    @MethodSource("tables")
    void theEnglishNamesAreTheOnesOfTheCodeListSnapshots(String table) {
        Map<String, String> snapshot = snapshot(SOURCES.get(table));
        List<String> wrong = new ArrayList<>();

        for (Map.Entry<String, Map<String, String>> entry : names(table).entrySet()) {
            String code = entry.getKey();
            String english = entry.getValue().get("en");
            String fact = snapshot.get(code);
            if (fact == null) {
                wrong.add(code + " is in no snapshot of " + SOURCES.get(table));
            } else if (!fact.equals(english) && !exception(table, code, english)) {
                wrong.add(code + ": the snapshot says '" + fact + "' and the table says '"
                        + english + "'");
            }
        }

        assertEquals(List.of(), wrong, table + ".json against its snapshot");
    }

    /**
     * Tells whether an English name that differs from its snapshot is one of the two the
     * files state a reason for.
     *
     * @param table   the table the code stands in
     * @param code    the code
     * @param english the name the table gives it
     * @return whether that name is a documented exception
     */
    private static boolean exception(String table, String code, String english) {
        return "invoice-type".equals(table) && "380".equals(code)
                        && "Invoice".equals(english)
                || "unit".equals(table) && "XPP".equals(code)
                        && "Packaging piece".equals(english);
    }

    /**
     * No two codes of one table read alike in one language, counting the name of one and
     * the name of more than one alike: a quantity column that writes <i>pieces</i> for two
     * different units says less than the code it replaced.
     */
    @ParameterizedTest
    @EnumSource(DisplayNames.CodeList.class)
    void noTwoCodesOfOneTableReadAlike(DisplayNames.CodeList list) {
        List<String> wrong = new ArrayList<>();

        for (RenderLanguage language : RenderLanguage.values()) {
            Map<String, String> seen = new LinkedHashMap<>();
            for (String code : names(file(list)).keySet()) {
                for (Optional<String> name : List.of(DisplayNames.of(list, code, language),
                        DisplayNames.ofSeveral(list, code, language))) {
                    String written = name.orElse("");
                    String other = seen.putIfAbsent(written, code);
                    if (other != null && !other.equals(code)) {
                        wrong.add(language + ": " + code + " and " + other + " both read '"
                                + written + "'");
                    }
                }
            }
        }

        assertEquals(List.of(), wrong, list + " names every code differently");
    }

    /** Every table names every code in both languages, and carries at least one. */
    @ParameterizedTest
    @EnumSource(DisplayNames.CodeList.class)
    void everyTableIsCompleteInBothLanguages(DisplayNames.CodeList list) {
        Map<String, Map<String, String>> table = names(file(list));
        List<String> wrong = new ArrayList<>();

        for (String code : table.keySet()) {
            for (RenderLanguage language : RenderLanguage.values()) {
                Optional<String> name = DisplayNames.of(list, code, language);
                if (name.isEmpty() || name.get().isBlank()) {
                    wrong.add(code + " has no name in " + language);
                }
            }
        }

        assertFalse(table.isEmpty(), list + " names at least one code");
        assertEquals(List.of(), wrong, list + " is complete");
    }

    /** A code no table names has no name, rather than a made-up one. */
    @Test
    void aCodeNoTableNamesHasNoName() {
        assertTrue(DisplayNames.of(DisplayNames.CodeList.UNIT, "XZZ",
                RenderLanguage.GERMAN).isEmpty(), "an unlisted unit");
        assertEquals(Optional.of("Stück"),
                DisplayNames.of(DisplayNames.CodeList.UNIT, "H87", RenderLanguage.GERMAN),
                "and a listed one");
    }

    /**
     * The lists this module has names for are the lists the registry writes the terms of
     * the model against. A list the registry names and this module does not simply leaves
     * its codes as codes, which is a decision; this writes down which lists those are, so
     * that the decision fails here when it changes rather than changing a page quietly.
     */
    @Test
    void theListsAreTheOnesTheRegistryNames() {
        List<String> named = new ArrayList<>();
        List<String> left = new ArrayList<>();
        Registry.en16931().terms().forEach(term -> term.codeList().ifPresent(list -> {
            List<String> into = DisplayNames.CodeList.of(list).isPresent() ? named : left;
            if (!into.contains(list)) {
                into.add(list);
            }
        }));

        assertEquals(List.of("UNTDID 1001", "UN/ECE Recommendation 20 with Rec 21 extension",
                        "UNTDID 4461", "UNTDID 5305", "ISO 3166-1 alpha-2"),
                named.stream().sorted(Comparator.comparing(
                        list -> DisplayNames.CodeList.of(list).orElseThrow().ordinal())).toList(),
                "the five lists the letter writes under their names");
        assertEquals(List.of("ISO 4217 alpha-3", "UNTDID 2005", "UNTDID 4451", "UNTDID 5189",
                        "UNTDID 7161", "VATEX"),
                left.stream().sorted().toList(),
                "and the ones whose codes stay codes");
    }

    /** The header of every table says where its English names came from. */
    @ParameterizedTest
    @MethodSource("tables")
    void theHeaderNamesTheSnapshotTheNamesCameFrom(String table) {
        Object header = TemplateJson.read(Corpus.bytes(NAMES + table + ".json"));
        String where = table + ".json";

        assertEquals("esj-render-display-names/0.1",
                TemplateJson.text(header, "displayNames", where), "the format marker");
        assertEquals(table, TemplateJson.text(header, "list", where), "the list it is");
        for (String source : SOURCES.get(table)) {
            assertTrue(TemplateJson.text(header, "english", where).contains(
                            source + "/" + TAKEN + ".json"),
                    where + " names the snapshot " + source + "/" + TAKEN);
        }
        assertFalse(TemplateJson.text(header, "german", where).isBlank(),
                "and says where the German wording comes from");
    }

    /** Returns the name of the file a list is carried in. */
    private static String file(DisplayNames.CodeList list) {
        return list.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Reads one table of names as codes and the two languages of each. */
    private static Map<String, Map<String, String>> names(String table) {
        Object tree = TemplateJson.read(Corpus.bytes(NAMES + table + ".json"));
        Object names = TemplateJson.member(tree, "names");
        Map<String, Map<String, String>> read = new LinkedHashMap<>();
        ((Map<?, ?>) names).forEach((code, entry) -> {
            Map<String, String> languages = new LinkedHashMap<>();
            for (RenderLanguage language : RenderLanguage.values()) {
                languages.put(language.code(),
                        TemplateJson.text(entry, language.code(), table));
            }
            read.put(String.valueOf(code), languages);
        });
        return read;
    }

    /** Returns the codes and the English names of one or more snapshots, merged. */
    private static Map<String, String> snapshot(List<String> lists) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String list : lists) {
            Object tree = TemplateJson.read(
                    Corpus.bytes(SNAPSHOT + list + "/" + TAKEN + ".json"));
            for (Object entry : TemplateJson.list(tree, "entries", list)) {
                names.putIfAbsent(TemplateJson.text(entry, "value", list),
                        TemplateJson.text(entry, "name", list));
            }
        }
        assertFalse(names.isEmpty(), "the snapshots " + lists + " carry entries");
        return names;
    }
}
