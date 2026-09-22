package de.bsnsoft.esj.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a code of an invoice is called, in the language the page is written in.
 *
 * <p>A reader does not read {@code 380}, {@code H87} or {@code AE}. The letter layout
 * therefore writes the name of a code where a name is the thing a reader needs — the
 * document type in the title, the unit beside a quantity, the means of payment, the VAT
 * category, the country of an address — and the code itself is then listed once under the
 * closing heading, so that the page never hides what the document says. The generic layout
 * prints every code as it stands and asks nothing of this class.
 *
 * <p>The names are <b>data of this repository</b>, five small files beside this class, and
 * that is the whole point: the same document has to render to the same bytes on every
 * machine, and a name looked up in the locale data of whatever runtime happens to be
 * running would not do that. The English names of the four code lists come from the dated
 * code list snapshots this repository already carries; the German names are this project's
 * wording. The country names were generated once out of the Unicode CLDR of a Java runtime
 * and checked in, and the file records which runtime that was.
 *
 * <p>A code no file names is shown as the code. That is not a gap to be filled quietly: the
 * lists here cover what invoices use, and a page that printed a guess would be worse than a
 * page that printed the code.
 */
final class DisplayNames {

    /** What a file of names calls itself. */
    private static final String FORMAT = "esj-render-display-names/0.1";

    /** Where the files sit on the classpath. */
    private static final String DIRECTORY = "/de/bsnsoft/esj/render/names/";

    private DisplayNames() {
        throw new AssertionError("no instances");
    }

    /** One list of codes the rendering knows the names of. */
    enum CodeList {

        /** The type of the document, BT-3, which is the title of the page. */
        INVOICE_TYPE("invoice-type", "UNTDID 1001", Word.CODE_DOCUMENT_TYPE),

        /** The unit a quantity is stated in, BT-130 and BT-150. */
        UNIT("unit", "UN/ECE Recommendation 20 with Rec 21 extension", Word.CODE_UNIT),

        /** The means of payment, BT-81. */
        PAYMENT_MEANS("payment-means", "UNTDID 4461", Word.CODE_PAYMENT_MEANS),

        /** The VAT category of a line, an allowance, a charge or a breakdown. */
        VAT_CATEGORY("vat-category", "UNTDID 5305", Word.CODE_VAT_CATEGORY),

        /** The country of an address. */
        COUNTRY("country", "ISO 3166-1 alpha-2", Word.CODE_COUNTRY);

        private final String file;
        private final String registryName;
        private final Word word;

        CodeList(String file, String registryName, Word word) {
            this.file = file;
            this.registryName = registryName;
            this.word = word;
        }

        /** Returns what a line under the closing heading calls this list. */
        Word word() {
            return word;
        }

        /**
         * Returns the list a term is written against, as the registry names it.
         *
         * @param registryName the {@code codeList} of the term, or {@code null}
         * @return the list, or empty where this rendering has no names for it
         */
        static Optional<CodeList> of(String registryName) {
            if (registryName == null) {
                return Optional.empty();
            }
            for (CodeList list : values()) {
                if (list.registryName.equals(registryName)) {
                    return Optional.of(list);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Returns what a code is called.
     *
     * @param list     the code list the code belongs to
     * @param code     the code, as the document spells it
     * @param language the language of the rendering
     * @return the name, or empty where no file names that code
     * @throws NullPointerException if an argument is {@code null}
     */
    static Optional<String> of(CodeList list, String code, RenderLanguage language) {
        return read(Loaded.NAMES, list, code, language);
    }

    /**
     * Returns what more than one of a code is called, and what one of it is called where
     * the table gives no second name.
     *
     * <p>Three of a unit are not one of it. <i>3 Tag</i> is not German and <i>3 day</i> is
     * not English, and a letter that writes the name of a unit rather than its code has to
     * write the name a reader would. The second name is a name and not a rule: a rule that
     * appended a letter would be right in two languages by accident and wrong in the third
     * the day this module learns one.
     *
     * @param list     the code list the code belongs to
     * @param code     the code, as the document spells it
     * @param language the language of the rendering
     * @return the name of more than one, or the one name the table carries, or empty
     * @throws NullPointerException if an argument is {@code null}
     */
    static Optional<String> ofSeveral(CodeList list, String code, RenderLanguage language) {
        Optional<String> several = read(Loaded.PLURALS, list, code, language);
        return several.isPresent() ? several : of(list, code, language);
    }

    private static Optional<String> read(
            Map<CodeList, Map<String, Map<RenderLanguage, String>>> tables, CodeList list,
            String code, RenderLanguage language) {
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(language, "language");
        Map<RenderLanguage, String> names = tables.get(list).get(code);
        return names == null ? Optional.empty() : Optional.ofNullable(names.get(language));
    }

    /**
     * The five files, read once. They are fixed resources of this module and never change
     * while it runs, so reading one a second time would only be work.
     */
    private static final class Loaded {

        /** What one of a code is called, under the member {@code en} and {@code de}. */
        private static final Map<CodeList, Map<String, Map<RenderLanguage, String>>> NAMES =
                readAll("");

        /** What more than one is called, under {@code enPlural} and {@code dePlural}. */
        private static final Map<CodeList, Map<String, Map<RenderLanguage, String>>> PLURALS =
                readAll("Plural");

        private Loaded() {
            throw new AssertionError("no instances");
        }

        private static Map<CodeList, Map<String, Map<RenderLanguage, String>>> readAll(
                String suffix) {
            Map<CodeList, Map<String, Map<RenderLanguage, String>>> all =
                    new EnumMap<>(CodeList.class);
            for (CodeList list : CodeList.values()) {
                all.put(list, read(list, suffix));
            }
            return all;
        }

        private static Map<String, Map<RenderLanguage, String>> read(CodeList list,
                                                                     String suffix) {
            String resource = DIRECTORY + list.file + ".json";
            Object tree = parse(bytes(resource), resource);
            if (!FORMAT.equals(TemplateJson.text(tree, "displayNames", resource))) {
                throw new RenderException("the resource " + resource + " is not a table of"
                        + " display names of this version");
            }
            Object names = TemplateJson.member(tree, "names");
            if (!(names instanceof Map<?, ?> entries) || entries.isEmpty()) {
                throw new RenderException("the resource " + resource + " names no code");
            }
            Map<String, Map<RenderLanguage, String>> table = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : entries.entrySet()) {
                Map<RenderLanguage, String> languages = new EnumMap<>(RenderLanguage.class);
                for (RenderLanguage language : RenderLanguage.values()) {
                    String name = TemplateJson.text(entry.getValue(),
                            language.code() + suffix, resource);
                    if (name != null && !name.isBlank()) {
                        languages.put(language, name);
                    }
                }
                table.put(String.valueOf(entry.getKey()), Map.copyOf(languages));
            }
            return Map.copyOf(table);
        }

        private static Object parse(byte[] bytes, String resource) {
            try {
                return TemplateJson.read(bytes);
            } catch (TemplateException e) {
                throw new RenderException("the resource " + resource + " could not be read: "
                        + e.getMessage(), e);
            }
        }

        private static byte[] bytes(String resource) {
            try (InputStream in = DisplayNames.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new RenderException(
                            "the resource " + resource + " is not on the classpath");
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
