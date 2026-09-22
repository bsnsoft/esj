package de.bsnsoft.esj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One ESJ document: the semantic model it refers to, the values addressed by their
 * semantic paths, the extension subtrees and the provenance metadata
 * (specification, section 4).
 *
 * <p>Instances are immutable and are built with {@link #builder()}. The values are held
 * in canonical path order (specification, section 7.4), so iterating them is iterating
 * the document in the order its canonical form writes it.
 *
 * <p>The builder checks what can be checked without a registry: the path grammar and the
 * uniqueness of paths. Whether a term exists, whether its type matches and whether the
 * mandatory terms are present is the business of
 * {@code de.bsnsoft.esj.validate.StructuralValidator}.
 */
public final class SemanticDocument {

    private final String semanticModel;
    private final SortedMap<SemanticPath, SemanticValue> values;
    private final Map<String, ExtensionValue> extensions;
    private final Source source;

    private SemanticDocument(String semanticModel,
                             SortedMap<SemanticPath, SemanticValue> values,
                             Map<String, ExtensionValue> extensions,
                             Source source) {
        this.semanticModel = semanticModel;
        this.values = values;
        this.extensions = extensions;
        this.source = source;
    }

    /**
     * Returns a builder for a document of the edition the bundled registry describes
     * ({@link Esj#SEMANTIC_MODEL}).
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the edition of the semantic model the paths of this document refer to.
     *
     * @return the {@code semanticModel} member, for example
     *         {@code EN16931-1:2017+A1:2019/AC:2020}
     */
    public String semanticModel() {
        return semanticModel;
    }

    /**
     * Returns the values of this document, keyed by their semantic paths and ordered by
     * the canonical path order of the specification, section 7.4.
     *
     * @return an unmodifiable sorted map of values
     */
    public SortedMap<SemanticPath, SemanticValue> values() {
        return values;
    }

    /**
     * Returns the value at a path.
     *
     * @param path the semantic path
     * @return the value, or an empty optional if the document carries none there
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public Optional<SemanticValue> value(SemanticPath path) {
        Objects.requireNonNull(path, "path");
        return Optional.ofNullable(values.get(path));
    }

    /**
     * Returns the extension subtrees of this document, keyed by their owner tokens in
     * Unicode code point order.
     *
     * @return an unmodifiable map, empty when the document carries no {@code extensions}
     *         member
     */
    public Map<String, ExtensionValue> extensions() {
        return extensions;
    }

    /**
     * Returns the provenance metadata of this document.
     *
     * @return the {@code source} member, or an empty optional
     */
    public Optional<Source> source() {
        return Optional.ofNullable(source);
    }

    /**
     * Returns a builder holding the content of this document, for making a changed copy.
     *
     * @return a builder seeded with this document
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.semanticModel = semanticModel;
        builder.values.putAll(values);
        builder.extensions.putAll(extensions);
        builder.source = source;
        return builder;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SemanticDocument other
                && semanticModel.equals(other.semanticModel)
                && values.equals(other.values)
                && extensions.equals(other.extensions)
                && Objects.equals(source, other.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(semanticModel, values, extensions, source);
    }

    /**
     * Returns a description naming the edition and the number of values, and not the
     * content of the invoice.
     *
     * @return a short description of the document
     */
    @Override
    public String toString() {
        return "SemanticDocument[semanticModel=" + semanticModel + ", values=" + values.size()
                + ", extensions=" + extensions.size() + ", source=" + (source == null ? "absent" : "present") + "]";
    }

    /**
     * The provenance metadata of a document: where its content was derived from. It is
     * never part of the semantic identity of the document, and the semantic digest does
     * not cover it (specification, section 4.7).
     *
     * <p>{@code sha256} is the digest of <strong>the source document this ESJ document was
     * derived from</strong>, and never of the ESJ file that carries it, which could not
     * contain a digest of itself. That source document is usually an instance of another
     * syntax, a UBL or CII instance; where an ESJ document is derived from an earlier ESJ
     * document, {@code syntax} is {@code ESJ} and {@code sha256} is the digest of that
     * earlier file's bytes, so a transformation inside the format records its provenance
     * like any other. It is the only digest in ESJ that identifies a file at all: the two
     * digests of section 8 identify content.
     *
     * @param syntax the syntax the content was derived from, for example {@code UBL},
     *               {@code CII} or {@code ESJ}, or an empty optional
     * @param sha256 the SHA-256 digest of the bytes of that source document, as 64
     *               lowercase hexadecimal digits, or an empty optional
     */
    public record Source(Optional<String> syntax, Optional<String> sha256) {

        /**
         * Checks both members and requires at least one of them.
         *
         * @param syntax the syntax the content was derived from, for example {@code UBL},
         *               {@code CII} or {@code ESJ}, or an empty optional
         * @param sha256 the SHA-256 digest of the bytes of that source document, as 64
         *               lowercase hexadecimal digits, or an empty optional
         * @throws EsjFormatException if both members are absent, the syntax is empty or
         *                            the digest is not 64 lowercase hexadecimal digits
         */
        public Source {
            Objects.requireNonNull(syntax, "syntax");
            Objects.requireNonNull(sha256, "sha256");
            if (syntax.isEmpty() && sha256.isEmpty()) {
                throw new EsjFormatException("source carries at least one of syntax and sha256");
            }
            syntax.ifPresent(value -> ValueSupport.required(value, "syntax"));
            sha256.ifPresent(Source::checkDigest);
        }

        /**
         * Creates provenance metadata with both members.
         *
         * @param syntax the syntax the content was derived from
         * @param sha256 the SHA-256 digest of the bytes of the source document
         * @return the metadata
         */
        public static Source of(String syntax, String sha256) {
            return new Source(Optional.of(syntax), Optional.of(sha256));
        }

        /**
         * Creates provenance metadata that names only the syntax.
         *
         * @param syntax the syntax the content was derived from
         * @return the metadata
         */
        public static Source ofSyntax(String syntax) {
            return new Source(Optional.of(syntax), Optional.empty());
        }

        /**
         * Creates provenance metadata that carries only the digest of the source bytes.
         *
         * @param sha256 the SHA-256 digest of the bytes of the source document
         * @return the metadata
         */
        public static Source ofDigest(String sha256) {
            return new Source(Optional.empty(), Optional.of(sha256));
        }

        private static void checkDigest(String value) {
            if (value.length() != 64) {
                throw new EsjFormatException("sha256 is 64 hexadecimal digits, not " + value.length());
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
                if (!hex) {
                    throw new EsjFormatException("sha256 is lowercase hexadecimal: " + value);
                }
            }
        }
    }

    /** Builds a {@link SemanticDocument}. A builder is not thread safe and is reusable. */
    public static final class Builder {

        private static final int MAX_OWNER_TOKEN_LENGTH = 128;

        /** The longest fragment of a rejected string a message reproduces. */
        private static final int MESSAGE_EXCERPT = 80;

        private final TreeMap<SemanticPath, SemanticValue> values =
                new TreeMap<>(SemanticPath.canonicalOrder());
        private final TreeMap<String, ExtensionValue> extensions =
                new TreeMap<>(ExtensionValue.memberOrder());
        private String semanticModel = Esj.SEMANTIC_MODEL;
        private Source source;

        private Builder() {
        }

        /**
         * Sets the edition of the semantic model the paths refer to. The default is
         * {@link Esj#SEMANTIC_MODEL}, the edition the bundled registry describes.
         *
         * <p>Any string that matches the edition grammar of the specification,
         * section 4.4 is accepted here. Whether a registry for that edition exists is a
         * different question: a reader refuses a document whose edition it has none for,
         * and a validator picks the registry whose edition matches this member
         * (sections 4.4 and 10).
         *
         * @param semanticModel the edition string, for example
         *                      {@code EN16931-1:2017+A1:2019/AC:2020}
         * @return this builder
         * @throws EsjFormatException   if the string is not an edition
         * @throws NullPointerException if {@code semanticModel} is {@code null}
         */
        public Builder semanticModel(String semanticModel) {
            Objects.requireNonNull(semanticModel, "semanticModel");
            if (!Esj.isEdition(semanticModel)) {
                throw new EsjFormatException("not an edition of the semantic model: "
                        + Esj.forMessage(semanticModel, MESSAGE_EXCERPT));
            }
            this.semanticModel = semanticModel;
            return this;
        }

        /**
         * Returns the edition of the semantic model the paths of this builder refer to.
         *
         * <p>A policy that writes into a builder reads it to know which edition's terms it
         * is allowed to write: a path is an address relative to an edition (specification,
         * sections 4.4 and 10).
         *
         * @return the {@code semanticModel} member the document will carry
         */
        public String semanticModel() {
            return semanticModel;
        }

        /**
         * Adds a value at a path.
         *
         * @param path  the semantic path of a business term occurrence
         * @param value the value
         * @return this builder
         * @throws EsjFormatException   if the path does not address a business term, or
         *                              the document already carries a value there
         * @throws NullPointerException if an argument is {@code null}
         */
        public Builder put(SemanticPath path, SemanticValue value) {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(value, "value");
            if (!path.isValuePath()) {
                throw new EsjFormatException("a value lies at a business term, not at " + path);
            }
            if (values.putIfAbsent(path, value) != null) {
                throw new EsjFormatException("the document already carries a value at " + path);
            }
            return this;
        }

        /**
         * Adds a value at a path.
         *
         * @param path  the semantic path of a business term occurrence
         * @param value the value
         * @return this builder
         * @throws EsjFormatException   if the path does not match the path grammar or does
         *                              not address a business term, or the document
         *                              already carries a value there
         * @throws NullPointerException if an argument is {@code null}
         */
        public Builder put(String path, SemanticValue value) {
            return put(SemanticPath.of(path), value);
        }

        /**
         * Adds a value that carries content and no supplementary component, which is the
         * shape of most values (specification, section 6.1).
         *
         * @param path    the semantic path of a business term occurrence
         * @param content the content of the business term
         * @return this builder
         * @throws EsjFormatException   if the path does not address a business term, the
         *                              content is empty, or the document already carries a
         *                              value there
         * @throws NullPointerException if an argument is {@code null}
         */
        public Builder put(SemanticPath path, String content) {
            return put(path, SemanticValue.of(content));
        }

        /**
         * Adds a value that carries content and no supplementary component, which is the
         * shape of most values (specification, section 6.1).
         *
         * @param path    the semantic path of a business term occurrence
         * @param content the content of the business term
         * @return this builder
         * @throws EsjFormatException   if the path does not match the path grammar or does
         *                              not address a business term, the content is empty,
         *                              or the document already carries a value there
         * @throws NullPointerException if an argument is {@code null}
         */
        public Builder put(String path, String content) {
            return put(SemanticPath.of(path), SemanticValue.of(content));
        }

        /**
         * Removes the value at a path, if there is one.
         *
         * @param path the semantic path
         * @return this builder
         * @throws NullPointerException if {@code path} is {@code null}
         */
        public Builder remove(SemanticPath path) {
            Objects.requireNonNull(path, "path");
            values.remove(path);
            return this;
        }

        /**
         * Removes the value at a path, if there is one.
         *
         * @param path the semantic path
         * @return this builder
         * @throws EsjFormatException   if the path does not match the path grammar
         * @throws NullPointerException if {@code path} is {@code null}
         */
        public Builder remove(String path) {
            return remove(SemanticPath.of(path));
        }

        /**
         * Adds a value at a path, replacing the one the builder already holds there.
         *
         * <p>It is what {@link #put(SemanticPath, SemanticValue)} is not: a caller that
         * means to overwrite says so. The typed editor of {@code esj-typed} writes
         * through this method, because setting the same business term twice is an
         * ordinary thing to do while an invoice is being assembled.
         *
         * @param path  the semantic path of a business term occurrence
         * @param value the value
         * @return this builder
         * @throws EsjFormatException   if the path does not address a business term
         * @throws NullPointerException if an argument is {@code null}
         */
        public Builder set(SemanticPath path, SemanticValue value) {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(value, "value");
            if (!path.isValuePath()) {
                throw new EsjFormatException("a value lies at a business term, not at " + path);
            }
            values.put(path, value);
            return this;
        }

        /**
         * Removes every value at or below a path. A group path removes one group
         * instance with everything inside it; a path that ends at a repeatable term
         * without an occurrence index removes every occurrence of that term.
         *
         * @param path the semantic path
         * @return this builder
         * @throws NullPointerException if {@code path} is {@code null}
         */
        public Builder removeUnder(SemanticPath path) {
            Objects.requireNonNull(path, "path");
            values.keySet().removeIf(key -> key.startsWith(path));
            return this;
        }

        /**
         * Removes one occurrence of a repeatable business term or business group and
         * moves every later occurrence of it one index down, so that the occurrence
         * indices stay dense as the specification, section 5.4 requires.
         *
         * <p>Only the index of the removed step changes. The occurrences nested inside a
         * moved instance keep their own indices, and the occurrences of the same term in
         * a different group instance are not touched: removing {@code /BG-25/1} renames
         * {@code /BG-25/2/BG-27/0/BT-136} to {@code /BG-25/1/BG-27/0/BT-136} and leaves
         * {@code /BG-25/0/...} where it is.
         *
         * <p>This is the one place where occurrences are renumbered. A value path whose
         * index is the last segment removes one occurrence of a repeatable term
         * ({@code /BG-4/BT-29/1}); a group path removes one group instance with
         * everything inside it ({@code /BG-25/1}).
         *
         * @param path the path of the occurrence, ending in an occurrence index
         * @return this builder
         * @throws EsjFormatException   if the path does not end in an occurrence index
         * @throws NullPointerException if {@code path} is {@code null}
         */
        public Builder removeOccurrence(SemanticPath path) {
            Objects.requireNonNull(path, "path");
            PathSegment.Index removed = path.index().orElseThrow(() -> new EsjFormatException(
                    "an occurrence is removed at a path that ends in an occurrence index, not at "
                            + path));
            int position = path.segments().size() - 1;
            int index = removed.value();
            SemanticPath step = path.prefix(position);
            List<SemanticPath> paths = new ArrayList<>();
            List<SemanticValue> moved = new ArrayList<>();
            for (SemanticPath key : List.copyOf(values.keySet())) {
                if (!(occurrenceOf(key, step, position) instanceof PathSegment.Index at)
                        || at.value() < index) {
                    continue;
                }
                SemanticValue value = values.remove(key);
                if (at.value() > index) {
                    paths.add(withIndex(key, position, at.value() - 1));
                    moved.add(value);
                }
            }
            for (int i = 0; i < paths.size(); i++) {
                values.put(paths.get(i), moved.get(i));
            }
            return this;
        }

        /**
         * Returns how many occurrences of a repeatable business term or business group
         * lie under a path. The occurrences are counted from index zero upwards and the
         * count stops at the first index the builder holds nothing under, which is the
         * rule the typed view reads them by (specification, section 5.4).
         *
         * @param step the path of the repeatable term or group, without an occurrence
         *             index
         * @return the number of occurrences, zero where there is none
         * @throws NullPointerException if {@code step} is {@code null}
         */
        public int occurrences(SemanticPath step) {
            Objects.requireNonNull(step, "step");
            int count = 0;
            while (holdsUnder(withIndex(step, count))) {
                count++;
            }
            return count;
        }

        /**
         * Returns the values the builder holds, in canonical path order.
         *
         * @return an unmodifiable view of the values put into this builder so far
         */
        public SortedMap<SemanticPath, SemanticValue> values() {
            return Collections.unmodifiableSortedMap(values);
        }

        /**
         * Returns the value the builder holds at a path.
         *
         * @param path the semantic path
         * @return the value, or an empty optional if the builder holds none there
         * @throws NullPointerException if {@code path} is {@code null}
         */
        public Optional<SemanticValue> value(SemanticPath path) {
            Objects.requireNonNull(path, "path");
            return Optional.ofNullable(values.get(path));
        }

        private boolean holdsUnder(SemanticPath path) {
            SortedMap<SemanticPath, SemanticValue> below = values.tailMap(path);
            return !below.isEmpty() && below.firstKey().startsWith(path);
        }

        private static PathSegment occurrenceOf(SemanticPath path, SemanticPath step, int position) {
            if (path.segments().size() <= position || !path.startsWith(step)) {
                return null;
            }
            return path.segments().get(position);
        }

        private static SemanticPath withIndex(SemanticPath path, int position, int index) {
            List<PathSegment> segments = new ArrayList<>(path.segments());
            segments.set(position, PathSegment.index(index));
            return SemanticPath.ofSegments(segments);
        }

        private static SemanticPath withIndex(SemanticPath step, int index) {
            List<PathSegment> segments = new ArrayList<>(step.segments());
            segments.add(PathSegment.index(index));
            return SemanticPath.ofSegments(segments);
        }

        /**
         * Adds an extension subtree under an owner token.
         *
         * @param owner the owner token, as the specification, section 4.6 defines it
         * @param value the subtree
         * @return this builder
         * @throws EsjFormatException   if the owner token does not match the grammar of
         *                              the specification, section 4.6
         * @throws NullPointerException if an argument is {@code null}
         */
        public Builder extension(String owner, ExtensionValue value) {
            checkOwnerToken(owner);
            Objects.requireNonNull(value, "value");
            extensions.put(owner, value);
            return this;
        }

        /**
         * Sets the provenance metadata.
         *
         * @param source the metadata, or {@code null} to leave the member out
         * @return this builder
         */
        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the provenance metadata from its two members.
         *
         * @param syntax the syntax the content was derived from
         * @param sha256 the SHA-256 digest of the bytes of the source document
         * @return this builder
         * @throws EsjFormatException if a member is not well formed
         */
        public Builder source(String syntax, String sha256) {
            return source(Source.of(syntax, sha256));
        }

        /**
         * Builds the document.
         *
         * @return an immutable document holding what was put into this builder
         */
        public SemanticDocument build() {
            TreeMap<SemanticPath, SemanticValue> copiedValues =
                    new TreeMap<>(SemanticPath.canonicalOrder());
            copiedValues.putAll(values);
            Map<String, ExtensionValue> copiedExtensions =
                    Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
            return new SemanticDocument(semanticModel,
                    Collections.unmodifiableSortedMap(copiedValues),
                    copiedExtensions,
                    source);
        }

        private static void checkOwnerToken(String owner) {
            Objects.requireNonNull(owner, "owner");
            if (owner.isEmpty() || owner.length() > MAX_OWNER_TOKEN_LENGTH) {
                throw new EsjFormatException(
                        "an owner token has 1 to " + MAX_OWNER_TOKEN_LENGTH + " characters: " + owner);
            }
            if (owner.startsWith("BT-") || owner.startsWith("BG-")) {
                throw new EsjFormatException("an owner token does not begin with BT- or BG-: " + owner);
            }
            for (int i = 0; i < owner.length(); i++) {
                char c = owner.charAt(i);
                boolean alnum = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
                boolean inner = alnum || c == '.' || c == '_' || c == '-';
                boolean edge = i == 0 || i == owner.length() - 1;
                if (!(edge ? alnum : inner)) {
                    throw new EsjFormatException(
                            "an owner token is ASCII, starts and ends with a letter or a digit"
                                    + " and otherwise carries only letters, digits, '.', '_' and '-': " + owner);
                }
            }
        }
    }
}
