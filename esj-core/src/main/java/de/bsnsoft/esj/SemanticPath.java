package de.bsnsoft.esj;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The absolute address of one business term occurrence, or of one business group
 * instance, inside a document (specification, section 5).
 *
 * <p>A <em>value path</em> ends at a business term and is what the member names of
 * {@code values} are: {@code /BG-25/0/BG-29/BT-146}. A <em>group path</em> ends at a
 * business group and names one group instance: {@code /BG-25/0}. The root of the document
 * is the path with no segments; its text is the empty string.
 *
 * <p>Instances are immutable. Two paths are equal exactly when their texts are equal,
 * code point by code point; there is no normalization and no case folding
 * (specification, section 5.5). The natural order is the canonical path order of the
 * specification, section 7.4, which needs no registry.
 */
public final class SemanticPath implements Comparable<SemanticPath> {

    private static final SemanticPath ROOT = new SemanticPath("", List.of());

    private static final Comparator<SemanticPath> CANONICAL_ORDER = SemanticPath::compareCanonically;

    private final String text;
    private final List<PathSegment> segments;

    private SemanticPath(String text, List<PathSegment> segments) {
        this.text = text;
        this.segments = segments;
    }

    /**
     * Returns the root of the document: the path with no segments, whose text is the
     * empty string. It is the parent of every path that has exactly one term step, and it
     * is the path of a finding that is about the document as a whole.
     *
     * @return the root path
     */
    public static SemanticPath root() {
        return ROOT;
    }

    /**
     * Parses a value path: a path that ends at a business term, which is what a member
     * name of {@code values} is.
     *
     * @param text the path text, starting with a solidus
     * @return the parsed path
     * @throws EsjFormatException   if the text does not match the path grammar of the
     *                              specification, section 5.1, or does not end at a
     *                              business term
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static SemanticPath of(String text) {
        return parse(text, TermKind.BT);
    }

    /**
     * Parses a group path: a path that ends at a business group and therefore names one
     * group instance.
     *
     * @param text the path text, starting with a solidus
     * @return the parsed path
     * @throws EsjFormatException   if the text does not match the path grammar of the
     *                              specification, section 5.1, or does not end at a
     *                              business group
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static SemanticPath group(String text) {
        return parse(text, TermKind.BG);
    }

    /**
     * Builds a path from segments. The segments must form the grammar of the
     * specification, section 5.1: term steps, each optionally followed by one index
     * segment, with a business term only in the last step.
     *
     * @param segments the segments, from the root down
     * @return the path
     * @throws EsjFormatException   if the segments do not form a path
     * @throws NullPointerException if {@code segments} or an element is {@code null}
     */
    public static SemanticPath ofSegments(List<PathSegment> segments) {
        Objects.requireNonNull(segments, "segments");
        List<PathSegment> copy = List.copyOf(segments);
        if (copy.isEmpty()) {
            return ROOT;
        }
        checkShape(copy);
        return new SemanticPath(join(copy), copy);
    }

    /**
     * Returns the comparator that implements the canonical path order of the
     * specification, section 7.4. It compares segment sequences rather than strings, it
     * needs no registry, and it is a total order on every path this class can hold.
     *
     * @return the canonical path order
     */
    public static Comparator<SemanticPath> canonicalOrder() {
        return CANONICAL_ORDER;
    }

    /**
     * Returns the segments of this path, from the root down.
     *
     * @return an unmodifiable list of segments, empty for the root
     */
    public List<PathSegment> segments() {
        return segments;
    }

    /**
     * Tells whether this path is the root of the document.
     *
     * @return {@code true} if the path has no segments
     */
    public boolean isRoot() {
        return segments.isEmpty();
    }

    /**
     * Tells whether this path addresses a business term and can therefore carry a value.
     *
     * @return {@code true} if the last term segment is a business term
     */
    public boolean isValuePath() {
        return !isRoot() && lastTerm().kind() == TermKind.BT;
    }

    /**
     * Tells whether this path addresses a business group instance.
     *
     * @return {@code true} if the last term segment is a business group
     */
    public boolean isGroupPath() {
        return !isRoot() && lastTerm().kind() == TermKind.BG;
    }

    /**
     * Returns the enclosing group instance, or the root when this path has a single term
     * step.
     *
     * @return the parent path
     * @throws IllegalStateException if this path is the root, which has no parent
     */
    public SemanticPath parent() {
        if (isRoot()) {
            throw new IllegalStateException("the root path has no parent");
        }
        int cut = segments.size() - (lastSegmentIsIndex() ? 2 : 1);
        return prefix(cut);
    }

    /**
     * Returns the group instances this path lies in, from the outermost to the innermost.
     * For {@code /BG-25/0/BG-29/BT-146} these are {@code /BG-25/0} and
     * {@code /BG-25/0/BG-29}.
     *
     * @return an unmodifiable list of group paths, empty for a path that lies at the root
     */
    public List<SemanticPath> groupPaths() {
        List<SemanticPath> result = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i) instanceof PathSegment.Term term && term.kind() == TermKind.BG) {
                int end = i + 1;
                if (end < segments.size() && segments.get(end) instanceof PathSegment.Index) {
                    end++;
                }
                result.add(prefix(end));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns the identifier of the last term of this path, for example {@code BT-146}.
     *
     * @return the term identifier
     * @throws IllegalStateException if this path is the root, which names no term
     */
    public String term() {
        if (isRoot()) {
            throw new IllegalStateException("the root path names no term");
        }
        return lastTerm().id();
    }

    /**
     * Returns the term segment of the last step of this path.
     *
     * @return the last term segment
     * @throws IllegalStateException if this path is the root, which names no term
     */
    public PathSegment.Term termSegment() {
        if (isRoot()) {
            throw new IllegalStateException("the root path names no term");
        }
        return lastTerm();
    }

    /**
     * Returns the occurrence index of the last step of this path, if it carries one.
     *
     * @return the index segment of the last step, or an empty optional
     */
    public Optional<PathSegment.Index> index() {
        if (lastSegmentIsIndex()) {
            return Optional.of((PathSegment.Index) segments.get(segments.size() - 1));
        }
        return Optional.empty();
    }

    /**
     * Tells whether the last term of this path belongs to an extension rather than to the
     * core model.
     *
     * @return {@code true} if the last term segment carries a namespace
     * @throws IllegalStateException if this path is the root, which names no term
     */
    public boolean isExtension() {
        if (isRoot()) {
            throw new IllegalStateException("the root path names no term");
        }
        return lastTerm().isExtension();
    }

    /**
     * Tells whether any term of this path belongs to an extension.
     *
     * @return {@code true} if at least one term segment carries a namespace
     */
    public boolean hasExtensionSegment() {
        for (PathSegment segment : segments) {
            if (segment instanceof PathSegment.Term term && term.isExtension()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the identifiers of the terms of this path, from the root down.
     *
     * @return an unmodifiable list of term identifiers, empty for the root
     */
    public List<String> termIds() {
        List<String> ids = new ArrayList<>(segments.size());
        for (PathSegment segment : segments) {
            if (segment instanceof PathSegment.Term term) {
                ids.add(term.id());
            }
        }
        return List.copyOf(ids);
    }

    /**
     * Tells whether this path lies at or below another path.
     *
     * @param other the candidate prefix
     * @return {@code true} if the segments of {@code other} are a prefix of the segments
     *         of this path
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public boolean startsWith(SemanticPath other) {
        Objects.requireNonNull(other, "other");
        if (other.segments.size() > segments.size()) {
            return false;
        }
        return segments.subList(0, other.segments.size()).equals(other.segments);
    }

    /**
     * Returns the path formed by the first {@code count} segments of this path.
     *
     * @param count the number of segments to keep
     * @return the prefix path
     * @throws IllegalArgumentException if {@code count} is negative or greater than the
     *                                  number of segments
     */
    public SemanticPath prefix(int count) {
        if (count < 0 || count > segments.size()) {
            throw new IllegalArgumentException("segment count out of range: " + count);
        }
        if (count == segments.size()) {
            return this;
        }
        if (count == 0) {
            return ROOT;
        }
        List<PathSegment> head = List.copyOf(segments.subList(0, count));
        return new SemanticPath(join(head), head);
    }

    @Override
    public int compareTo(SemanticPath other) {
        return compareCanonically(this, other);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SemanticPath other && text.equals(other.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * Returns the path text, which is the empty string for the root.
     *
     * @return the path as it appears in a document
     */
    @Override
    public String toString() {
        return text;
    }

    private PathSegment.Term lastTerm() {
        PathSegment last = segments.get(segments.size() - 1);
        if (last instanceof PathSegment.Term term) {
            return term;
        }
        return (PathSegment.Term) segments.get(segments.size() - 2);
    }

    private boolean lastSegmentIsIndex() {
        return !segments.isEmpty() && segments.get(segments.size() - 1) instanceof PathSegment.Index;
    }

    private static SemanticPath parse(String text, TermKind expectedLastKind) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty() || text.charAt(0) != '/') {
            throw new EsjFormatException("a semantic path starts with a solidus: " + text);
        }
        List<PathSegment> segments = new ArrayList<>();
        int position = 1;
        TermKind lastKind = null;
        while (position <= text.length()) {
            int end = text.indexOf('/', position);
            if (end < 0) {
                end = text.length();
            }
            String token = text.substring(position, end);
            if (token.isEmpty()) {
                throw new EsjFormatException("empty segment in semantic path: " + text);
            }
            if (PathSegment.isDigits(token)) {
                if (segments.isEmpty() || segments.get(segments.size() - 1) instanceof PathSegment.Index) {
                    throw new EsjFormatException(
                            "an occurrence index follows a term segment: " + text);
                }
                segments.add(new PathSegment.Index(token));
            } else {
                if (lastKind == TermKind.BT) {
                    throw new EsjFormatException(
                            "only the last step of a semantic path names a business term: " + text);
                }
                PathSegment.Term term = parseTerm(token, text);
                lastKind = term.kind();
                segments.add(term);
            }
            position = end + 1;
        }
        if (lastKind != expectedLastKind) {
            throw new EsjFormatException(expectedLastKind == TermKind.BT
                    ? "a value path ends at a business term: " + text
                    : "a group path ends at a business group: " + text);
        }
        return new SemanticPath(text, List.copyOf(segments));
    }

    private static PathSegment.Term parseTerm(String token, String path) {
        if (token.length() < 4 || token.charAt(2) != '-') {
            throw new EsjFormatException("not a term segment: " + token + " in " + path);
        }
        TermKind kind;
        try {
            kind = TermKind.fromPrefix(token.substring(0, 2));
        } catch (EsjFormatException e) {
            throw new EsjFormatException("not a term segment: " + token + " in " + path, e);
        }
        String rest = token.substring(3);
        int separator = rest.indexOf('-');
        try {
            if (separator < 0) {
                return new PathSegment.Term(kind, null, rest);
            }
            return new PathSegment.Term(kind, rest.substring(0, separator), rest.substring(separator + 1));
        } catch (EsjFormatException e) {
            throw new EsjFormatException("not a term segment: " + token + " in " + path, e);
        }
    }

    private static void checkShape(List<PathSegment> segments) {
        TermKind lastKind = null;
        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = Objects.requireNonNull(segments.get(i), "segment");
            if (segment instanceof PathSegment.Term term) {
                if (lastKind == TermKind.BT) {
                    throw new EsjFormatException(
                            "only the last step of a semantic path names a business term");
                }
                lastKind = term.kind();
            } else if (i == 0 || segments.get(i - 1) instanceof PathSegment.Index) {
                throw new EsjFormatException("an occurrence index follows a term segment");
            }
        }
    }

    private static String join(List<PathSegment> segments) {
        StringBuilder builder = new StringBuilder();
        for (PathSegment segment : segments) {
            builder.append('/').append(segment.text());
        }
        return builder.toString();
    }

    private static int compareCanonically(SemanticPath left, SemanticPath right) {
        List<PathSegment> a = left.segments;
        List<PathSegment> b = right.segments;
        int common = Math.min(a.size(), b.size());
        for (int i = 0; i < common; i++) {
            int result = compareSegments(a.get(i), b.get(i));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private static int compareSegments(PathSegment left, PathSegment right) {
        if (left instanceof PathSegment.Term a) {
            if (!(right instanceof PathSegment.Term b)) {
                return -1;
            }
            int result = Integer.compare(a.kind().canonicalRank(), b.kind().canonicalRank());
            if (result != 0) {
                return result;
            }
            result = compareNamespaces(a.namespace(), b.namespace());
            if (result != 0) {
                return result;
            }
            result = compareDigits(a.number(), b.number());
            if (result != 0) {
                return result;
            }
            return CodePoints.compare(a.number(), b.number());
        }
        if (right instanceof PathSegment.Term) {
            return 1;
        }
        return compareDigits(((PathSegment.Index) left).digits(), ((PathSegment.Index) right).digits());
    }

    private static int compareNamespaces(String left, String right) {
        if (left == null) {
            return right == null ? 0 : -1;
        }
        if (right == null) {
            return 1;
        }
        return CodePoints.compare(left, right);
    }

    private static int compareDigits(String left, String right) {
        String a = stripLeadingZeros(left);
        String b = stripLeadingZeros(right);
        if (a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        return a.compareTo(b);
    }

    private static String stripLeadingZeros(String digits) {
        int i = 0;
        while (i < digits.length() - 1 && digits.charAt(i) == '0') {
            i++;
        }
        return digits.substring(i);
    }
}
