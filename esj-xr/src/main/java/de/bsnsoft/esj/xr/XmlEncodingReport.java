package de.bsnsoft.esj.xr;

import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the bytes of an XML document say about their encoding, and what they are.
 *
 * <p>An XML document names its encoding twice over: a byte order mark is a fact about
 * the bytes, and the {@code encoding} pseudo-attribute of the XML declaration is a claim
 * the document makes about itself. The two usually agree, and where they do not it is
 * the claim that is wrong — a document written by a tool that encoded in one charset and
 * declared another. This report holds both, together with the charset the bytes actually
 * spell.
 *
 * <p>A report is a description and not a judgement. {@link #consistent()} says whether
 * the bytes and the claim agree; what to do about a disagreement is decided by the
 * {@link XrEncodingMode} the importer runs in. {@link #decodes()} says whether there is
 * anything to do at all: bytes that spell no character of the charset the document names,
 * and none of the charsets this module recodes from, are bytes nothing can be read from
 * without replacing some of them, and an importer refuses them whatever its mode.
 *
 * @param byteOrderMark the charset a byte order mark names, absent if there is none
 * @param declaration   the charset the XML declaration names, absent if the document
 *                      carries no declaration or a declaration without an
 *                      {@code encoding} pseudo-attribute
 * @param assumed       the canonical name of the charset the bytes are read in: the one
 *                      the document names where the bytes agree with it, the one the bytes
 *                      spell where they do not, and the one the document names again where
 *                      they spell none this module knows
 * @param consistent    whether the bytes agree with what the document says about them
 * @param decodes       whether the bytes decode in {@code assumed} without a single byte
 *                      sequence that is no character of it; {@code true} where this runtime
 *                      has no decoder for {@code assumed}, which {@link #unreadable()}
 *                      reports instead
 */
public record XmlEncodingReport(Optional<String> byteOrderMark,
                                Optional<String> declaration,
                                String assumed,
                                boolean consistent,
                                boolean decodes) {

    /**
     * The charsets this module recodes from. Nothing outside this set is guessed at and
     * nothing outside it is repaired; a document in any other charset is handed to the
     * XML parser as it stands where its bytes decode in that charset, and is not
     * {@link #decodes() decodable} where they do not.
     */
    private static final Set<String> RECODED = Set.of(
            "UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "ISO-8859-1", "windows-1252");

    /**
     * Checks that no member is {@code null} and that the bytes of a consistent report decode.
     *
     * @param byteOrderMark the charset a byte order mark names, absent if there is none
     * @param declaration   the charset the XML declaration names, absent if the document
     *                      carries no declaration or a declaration without an
     *                      {@code encoding} pseudo-attribute
     * @param assumed       the canonical name of the charset the bytes are read in: the one
     *                      the document names where the bytes agree with it, the one the
     *                      bytes spell where they do not, and the one the document names
     *                      again where they spell none this module knows
     * @param consistent    whether the bytes agree with what the document says about them
     * @param decodes       whether the bytes decode in {@code assumed}
     * @throws NullPointerException     if a member is {@code null}
     * @throws IllegalArgumentException if the report is consistent and the bytes do not
     *                                  decode: bytes that spell no character of the
     *                                  charset they claim do not agree with the claim
     */
    public XmlEncodingReport {
        Objects.requireNonNull(byteOrderMark, "byteOrderMark");
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(assumed, "assumed");
        if (consistent && !decodes) {
            throw new IllegalArgumentException("bytes that do not decode in " + assumed
                    + " do not agree with a document that says they are " + assumed);
        }
    }

    /**
     * Creates a report of bytes that decode in the charset they are read in, which is every
     * report this module made before it asked that question.
     *
     * @param byteOrderMark the charset a byte order mark names, absent if there is none
     * @param declaration   the charset the XML declaration names, absent if there is none
     * @param assumed       the canonical name of the charset the bytes are read in
     * @param consistent    whether the bytes agree with what the document says about them
     * @throws NullPointerException if a member is {@code null}
     */
    public XmlEncodingReport(Optional<String> byteOrderMark,
                             Optional<String> declaration,
                             String assumed,
                             boolean consistent) {
        this(byteOrderMark, declaration, assumed, consistent, true);
    }

    /**
     * Returns the charset the document says it is written in: the one a byte order mark
     * names, otherwise the one the XML declaration names, otherwise UTF-8, which is what
     * an XML document without either is.
     *
     * @return the canonical charset name
     */
    public String documented() {
        return byteOrderMark.or(() -> declaration).orElse("UTF-8");
    }

    /**
     * Tells whether {@link XmlBytes#repair(byte[], XmlEncodingReport)} can turn these
     * bytes into UTF-8.
     *
     * <p>It can where the bytes disagree with the document and the charset they spell is
     * one of the four this module knows: UTF-8, UTF-16, ISO-8859-1 and Windows-1252.
     * Where they disagree and spell none of those, nothing is guessed: a guess outside that
     * set would be a second defect on top of the first, and such bytes do not
     * {@link #decodes() decode}.
     *
     * @return {@code true} if a repair is possible and needed
     */
    public boolean repairable() {
        return !consistent && decodes && RECODED.contains(assumed);
    }

    /**
     * Returns the encoding these bytes announce where it is one that will not be read,
     * and an empty optional where it will.
     *
     * <p>Two encodings arrive here. UTF-32 is allowed by XML and is not read by the parser
     * of this platform, in either byte order and with or without a byte order mark. A
     * declaration that names a charset this runtime has never heard of is the other: its
     * bytes cannot be decoded at all. In both cases a parser handed the bytes reports that
     * they are not well-formed XML, which is true of no part of the document and sends the
     * reader of the message to look at markup that may be perfectly good; what is wanted
     * instead is the name of the encoding.
     *
     * @return the encoding, or an empty optional
     */
    public Optional<String> unreadable() {
        String charset = assumed();
        if (charset.startsWith("UTF-32")) {
            return Optional.of(charset);
        }
        try {
            return Charset.isSupported(charset) ? Optional.empty() : Optional.of(charset);
        } catch (IllegalArgumentException e) {
            // A name no charset carries is a name no charset of this runtime carries.
            return Optional.of(charset);
        }
    }

    /**
     * Returns the two facts a message about this document needs, in one phrase:
     * what the document said and what the bytes are.
     *
     * @return a description in English, such as {@code declared UTF-8, read as
     *         ISO-8859-1}
     */
    public String describe() {
        String documented = documented();
        String source = byteOrderMark.isPresent() ? "byte order mark " : "declared ";
        if (consistent) {
            return source + documented + ", and the bytes agree";
        }
        return decodes
                ? source + documented + ", read as " + assumed
                : source + documented + ", and the bytes do not decode in " + assumed;
    }
}
