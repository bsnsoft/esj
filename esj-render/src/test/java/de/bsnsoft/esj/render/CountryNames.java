package de.bsnsoft.esj.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes the checked-in table of country names, once, out of the locale data of the Java
 * runtime.
 *
 * <p>A rendering has to write the same bytes on every machine, so the renderer never asks
 * the runtime what a country is called: it reads a file of this repository. That file has
 * to come from somewhere, and the Unicode CLDR that every Java runtime since 9 carries is
 * the best source there is for the name of a country in two languages. So it is read here,
 * in a generator, at the moment somebody runs it — and the answer is checked in.
 *
 * <p>{@link CountryNamesTest} is the other half: it re-runs this generator on the runtime
 * whose version the file records and compares, so that the file cannot quietly stop being
 * what this code writes. On any other runtime it only reads the file, because the CLDR of
 * another release names a country differently and that is not a failure of this project.
 *
 * <p>To write the file again: run {@code main} with the path of the resource directory,
 * on a runtime whose names are the ones the project wants to carry.
 */
final class CountryNames {

    /** What the file calls itself, as the other tables of names do. */
    static final String FORMAT = "esj-render-display-names/0.1";

    /** The name of the file inside the resource directory. */
    static final String FILE = "country.json";

    private CountryNames() {
        throw new AssertionError("no instances");
    }

    /**
     * Writes the table into the directory named by the first argument.
     *
     * @param args the resource directory the file is written into
     * @throws IOException if the file could not be written
     */
    public static void main(String... args) throws IOException {
        Path directory = Path.of(args[0]);
        Files.writeString(directory.resolve(FILE), generate(), StandardCharsets.UTF_8);
    }

    /** Returns the runtime version the file records and this generator runs on. */
    static String runtimeVersion() {
        return System.getProperty("java.version");
    }

    /**
     * Returns the table as the file carries it: every ISO 3166-1 alpha-2 code the runtime
     * knows a name for in both languages, in the order of the code.
     *
     * @return the JSON text, ending in a line feed
     */
    static String generate() {
        List<String> codes = new ArrayList<>(List.of(Locale.getISOCountries()));
        codes.sort(String::compareTo);
        StringBuilder text = new StringBuilder(24_000);
        text.append("{\n")
                .append("  \"displayNames\": \"").append(FORMAT).append("\",\n")
                .append("  \"list\": \"country\",\n")
                .append("  \"english\": \"The name of a country in the Unicode CLDR, read"
                        + " once out of the locale data of the Java runtime named below and"
                        + " checked in. A rendering never reads it from a runtime.\",\n")
                .append("  \"german\": \"The same, in German.\",\n")
                .append("  \"generator\": \"CountryNames, a test-scope class of"
                        + " esj-render\",\n")
                .append("  \"javaRuntimeVersion\": \"").append(runtimeVersion())
                .append("\",\n")
                .append("  \"languages\": [\"en\", \"de\"],\n")
                .append("  \"names\": {\n");
        boolean first = true;
        for (String code : codes) {
            Locale country = new Locale.Builder().setRegion(code).build();
            String english = country.getDisplayCountry(Locale.ENGLISH);
            String german = country.getDisplayCountry(Locale.GERMAN);
            if (english.equals(code) || german.equals(code)) {
                continue;
            }
            if (!first) {
                text.append(",\n");
            }
            first = false;
            text.append("    \"").append(code).append("\": { \"en\": \"").append(escape(english))
                    .append("\", \"de\": \"").append(escape(german)).append("\" }");
        }
        return text.append("\n  }\n}\n").toString();
    }

    /** Escapes the two characters a JSON string may not carry as they stand. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
