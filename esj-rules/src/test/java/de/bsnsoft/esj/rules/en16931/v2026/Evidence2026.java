package de.bsnsoft.esj.rules.en16931.v2026;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.en16931.En16931;
import de.bsnsoft.esj.upgrade.EditionUpgrade;
import de.bsnsoft.esj.upgrade.UpgradeOptions;
import de.bsnsoft.esj.upgrade.UpgradeResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the tests of {@code conformance/rules-2026/} have in hand: the two engines, the
 * documents of the conformance corpus written up to this edition, and the files of the
 * repository the build copies onto the test class path.
 *
 * <p>Everything about this edition is measured over documents that were written for the
 * earlier one and moved with {@code esj upgrade}, because no corpus of invoices exists for
 * an edition no syntax binds yet. Where a measurement needs a document this edition would
 * accept and that one would not, the change is data and stands in
 * {@code cases/cases.json} beside the rule it is for.
 */
final class Evidence2026 {

    /** The pack of this edition, compiled against the registry of this edition. */
    static final RuleEngine ENGINE = new En16931V2026().engine(Registry.forEdition("2026"));

    /** The pack of the default edition, for the comparison the corpus measurement makes. */
    static final RuleEngine DEFAULT_PACK = En16931.engine(Registry.en16931());

    /** The options every upgrade of these measurements is made with. */
    static final UpgradeOptions OPTIONS = UpgradeOptions.builder()
            .extension(Registry.xrechnungExtension())
            .build();

    private Evidence2026() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the documents the measurements are taken over.
     *
     * <p>The list is the one the default pack was measured over, read from its own ledger
     * rather than from a directory: the two packs are held against the same documents, and
     * a document that is added to one measurement and not to the other cannot pass
     * unnoticed.
     *
     * @return the paths of the documents, relative to the repository
     */
    static List<String> corpus() {
        String text = text("/conformance/rules/corpus.json");
        int from = text.indexOf("\"documents\": [") + "\"documents\": [".length();
        int to = text.indexOf(']', from);
        List<String> paths = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(text.substring(from, to));
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        return List.copyOf(paths);
    }

    /**
     * Returns a document of the corpus as it reads in the repository.
     *
     * @param name its path, relative to the repository
     * @return the document, of the edition it was written for
     */
    static SemanticDocument document(String name) {
        return EsjReader.strict().read(bytes("/" + name));
    }

    /**
     * Returns a document of the corpus written up to this edition.
     *
     * @param name its path, relative to the repository
     * @return the upgraded document
     * @throws IllegalStateException if the upgrade refuses it
     */
    static SemanticDocument upgraded(String name) {
        UpgradeResult result = upgrade(name);
        if (!result.isUpgraded()) {
            throw new IllegalStateException(name + " cannot be written to this edition");
        }
        return result.require();
    }

    /**
     * Writes a document of the corpus up to this edition, with the report of the run.
     *
     * @param name its path, relative to the repository
     * @return what the upgrade produced and what it reported
     */
    static UpgradeResult upgrade(String name) {
        return EditionUpgrade.apply(document(name), "2026", OPTIONS);
    }

    /**
     * Returns the rule identifiers a pack reports about a document, without repetition.
     *
     * @param engine   the pack
     * @param document the document
     * @return the identifiers, in order
     */
    static List<String> codes(RuleEngine engine, SemanticDocument document) {
        TreeSet<String> codes = new TreeSet<>();
        for (RuleFinding finding : engine.evaluate(document)) {
            codes.add(finding.code());
        }
        return List.copyOf(codes);
    }

    /**
     * Applies the changes of a case to a document.
     *
     * @param document the document
     * @param changes  the changes, in order, or {@code null} for none
     * @return the document with the changes made
     */
    static SemanticDocument apply(SemanticDocument document, List<?> changes) {
        SemanticDocument.Builder builder = document.toBuilder();
        if (changes == null) {
            return builder.build();
        }
        for (Object element : changes) {
            Map<?, ?> change = (Map<?, ?>) element;
            String path = (String) change.get("path");
            if (Boolean.TRUE.equals(change.get("delete"))) {
                builder.remove(SemanticPath.of(path));
                continue;
            }
            if (Boolean.TRUE.equals(change.get("deleteUnder"))) {
                builder.removeUnder(SemanticPath.group(path));
                continue;
            }
            String scheme = (String) change.get("scheme");
            String content = (String) change.get("value");
            builder.set(SemanticPath.of(path), scheme == null
                    ? SemanticValue.of(content)
                    : SemanticValue.identifier(content, scheme));
        }
        return builder.build();
    }

    /** Returns a file of the repository that the build copies onto the test class path. */
    static byte[] bytes(String resource) {
        try (InputStream in = Evidence2026.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("this build carries no " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a file of the repository as text. */
    static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }

    /**
     * Reads JSON into maps, lists, strings and booleans, which is all these files hold.
     *
     * @param resource the resource path, absolute
     * @return the tree
     */
    static Object json(String resource) {
        try (JsonParser parser = new JsonFactory().createParser(bytes(resource))) {
            parser.nextToken();
            return value(parser);
        } catch (IOException e) {
            throw new IllegalStateException(resource + " is not JSON", e);
        }
    }

    /** Returns a member of an object. */
    static Object get(Object object, String member) {
        return ((Map<?, ?>) object).get(member);
    }

    /** Returns a member of an object as a number. */
    static int number(Object object, String member) {
        return Integer.parseInt(String.valueOf(get(object, member)));
    }

    private static Object value(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            Map<String, Object> members = new LinkedHashMap<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.currentName();
                parser.nextToken();
                members.put(name, value(parser));
            }
            return members;
        }
        if (token == JsonToken.START_ARRAY) {
            List<Object> elements = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                elements.add(value(parser));
            }
            return elements;
        }
        if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) {
            return parser.getBooleanValue();
        }
        return parser.getValueAsString();
    }
}
