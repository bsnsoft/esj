package de.bsnsoft.esj.rules.en16931;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A document under construction, for the rule cases.
 *
 * <p>A rule case is a pair: a document the rule holds on and the same document with one thing
 * changed. Writing the pair means putting a value, taking one away, or taking a whole business
 * group away, which the builder of the core is deliberately not for — it refuses to replace a
 * value it already carries, because a document is written once. So the cases are assembled in a
 * map first and turned into a document at the end.
 */
final class Invoice {

    private final Map<String, SemanticValue> values;

    private Invoice(Map<String, SemanticValue> values) {
        this.values = values;
    }

    /** Returns an empty document under construction. */
    static Invoice empty() {
        return new Invoice(new LinkedHashMap<>());
    }

    /** Returns an independent copy, so that a mutation cannot reach the document it came from. */
    Invoice copy() {
        return new Invoice(new LinkedHashMap<>(values));
    }

    /**
     * Puts a value, replacing what is there.
     *
     * @param path    the semantic path
     * @param content the content, in the canonical form its semantic data type requires
     * @return this document
     */
    Invoice put(String path, String content) {
        values.put(path, SemanticValue.of(content));
        return this;
    }

    /**
     * Puts an identifier with the scheme it was issued under.
     *
     * @param path    the semantic path
     * @param content the identifier
     * @param scheme  the identification scheme
     * @return this document
     */
    Invoice scheme(String path, String content, String scheme) {
        values.put(path, SemanticValue.identifier(content, scheme));
        return this;
    }

    /**
     * Puts a binary object with the media type and the file name it was attached under.
     *
     * @param path     the semantic path
     * @param content  the bytes of the file
     * @param mimeCode the media type
     * @param filename the file name
     * @return this document
     */
    Invoice binary(String path, String content, String mimeCode, String filename) {
        values.put(path, SemanticValue.binary(content.getBytes(StandardCharsets.UTF_8), mimeCode,
                filename));
        return this;
    }

    /**
     * Takes values away.
     *
     * @param paths the semantic paths; a path the document does not carry is ignored
     * @return this document
     */
    Invoice drop(String... paths) {
        for (String path : paths) {
            values.remove(path);
        }
        return this;
    }

    /**
     * Takes a whole business group away, with everything below it.
     *
     * @param prefix the path of the group, without a trailing solidus
     * @return this document
     */
    Invoice dropUnder(String prefix) {
        List<String> gone = new ArrayList<>();
        for (String path : values.keySet()) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                gone.add(path);
            }
        }
        gone.forEach(values::remove);
        return this;
    }

    /**
     * Builds the document.
     *
     * @return the semantic document
     */
    SemanticDocument build() {
        SemanticDocument.Builder builder = SemanticDocument.builder();
        for (Map.Entry<String, SemanticValue> value : values.entrySet()) {
            builder.set(SemanticPath.of(value.getKey()), value.getValue());
        }
        return builder.build();
    }
}
