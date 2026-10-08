package de.bsnsoft.esj.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.validate.Finding;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Probes of layer L1 that the readers of this project answered differently from each other,
 * or differently from the specification, until release 0.9.6, each with the answer the
 * specification fixes: the code, the path and the subject of every finding (sections 9.5 and
 * 9.6), and the limits of section 12.2 on every token of a document.
 *
 * <p>A subject is a member access from the root of the document: a name the specification
 * defines at that place in dotted form, any other name in brackets as a JSON string with the
 * escapes of section 9.5, an array index in brackets. Where the reader stops at a limit, the
 * subject names the member whose name or value reached it.
 */
class ReaderProbesTest {

    private static final String FORMAT = "\"format\":\"EN16931-Semantic-JSON\"";
    private static final String VERSION = "\"version\":\"0.1\"";
    private static final String MODEL = "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\"";
    private static final String HEAD = "{" + FORMAT + "," + VERSION + "," + MODEL + ",";

    /** A string bound the envelope's own strings and names fit in: the edition is 30 bytes. */
    private static final Limits SMALL = Limits.defaults().withMaxStringBytes(64);

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] document(String tail) {
        return utf8(HEAD + tail + "}");
    }

    private static byte[] values(String members) {
        return document("\"values\":{" + members + "}");
    }

    private static List<Finding> findings(byte[] bytes) {
        return EsjReader.strict().readWithFindings(bytes).findings();
    }

    private static List<Finding> findings(Limits limits, byte[] bytes) {
        return EsjReader.withLimits(limits).readWithFindings(bytes).findings();
    }

    private static Finding only(List<Finding> findings) {
        assertEquals(1, findings.size(), () -> findings.toString());
        return findings.get(0);
    }

    private static void assertFinding(Finding finding, String code, String path, String subject) {
        assertEquals(code, finding.code().code(), finding::toString);
        assertEquals(path, finding.path().toString(), finding::toString);
        assertEquals(subject, finding.subject(), finding::toString);
    }

    private static long byteOffset(String message) {
        Matcher matcher = Pattern.compile("at byte (\\d+)").matcher(message);
        assertTrue(matcher.find(), message);
        return Long.parseLong(matcher.group(1));
    }

    // ------------------------------------------------------------------ the encoding

    /**
     * A reader reads UTF-8 and guesses no other encoding: a document written in UTF-16 or
     * UTF-32 without a byte order mark is valid UTF-8 — every character is a byte below
     * U+0080 or a NUL — and no JSON text in it (specification, section 4.2).
     */
    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE"})
    void aDocumentInAnotherEncodingWithoutAByteOrderMarkIsNoJsonText(String encoding) {
        byte[] bytes = (HEAD + "\"values\":{\"/BT-1\":\"RE-1\"}}").getBytes(
                Charset.forName(encoding));

        Finding finding = only(findings(bytes));

        assertFinding(finding, "ESJ-L1-JSON", "", "");
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> Canonicalizer.canonicalize(bytes));
        assertEquals("ESJ-L1-JSON", thrown.code().orElseThrow().code());
    }

    @ParameterizedTest
    @CsvSource({"UTF-16LE, FFFE", "UTF-16BE, FEFF", "UTF-32LE, FFFE0000",
                "UTF-32BE, 0000FEFF", "UTF-8, EFBBBF"})
    void aDocumentWithAByteOrderMarkIsAnEncodingFinding(String encoding, String mark) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < mark.length(); i += 2) {
            bytes.write(Integer.parseInt(mark.substring(i, i + 2), 16));
        }
        bytes.writeBytes((HEAD + "\"values\":{\"/BT-1\":\"RE-1\"}}").getBytes(
                Charset.forName(encoding)));

        assertFinding(only(findings(bytes.toByteArray())), "ESJ-L1-ENCODING", "", "");
    }

    // ------------------------------------------------------------------ the string bound

    /**
     * Every string of the envelope is held to the string bound and draws a limit, not the
     * code of its grammar, where it is longer (specification, section 12.2).
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"format\":\"XXXX\"                                     | format",
            "{\"version\":\"XXXX\"                                    | version",
            "{\"semanticModel\":\"XXXX\"                              | semanticModel",
            "{\"source\":{\"syntax\":\"XXXX\"}                        | source.syntax",
            "{\"source\":{\"sha256\":\"XXXX\"}                        | source.sha256"})
    void everyStringOfTheEnvelopeIsHeldToTheStringBound(String start, String subject) {
        byte[] bytes = utf8(start.trim().replace("XXXX", "a".repeat(65)) + "}");

        assertFinding(only(findings(SMALL, bytes)), "ESJ-L1-LIMIT", "", subject);
        EsjLimitException thrown = assertThrows(EsjLimitException.class,
                () -> EsjReader.withLimits(SMALL).read(bytes));
        assertEquals(subject, thrown.subject());
        assertTrue(thrown.path().isRoot());
    }

    @Test
    void aStringOfTheEnvelopeAtTheStringBoundIsJudgedByItsGrammar() {
        byte[] bytes = utf8("{\"semanticModel\":\"" + "a".repeat(64) + "\"}");

        assertFinding(only(findings(SMALL, bytes)), "ESJ-L1-ENVELOPE-VALUE", "",
                "semanticModel");
    }

    /**
     * Every member name is held to the string bound, counted in UTF-8 bytes, and the
     * finding's subject is the member access with the name in it, whole (specification,
     * sections 9.5 and 12.2).
     */
    @Test
    void everyMemberNameIsHeldToTheStringBoundInUtf8Bytes() {
        String wide = "ä".repeat(33);
        String subject = wide;

        assertFinding(only(findings(SMALL, values("\"" + wide + "\":\"x\""))),
                "ESJ-L1-LIMIT", "", "values[\"" + subject + "\"]");
        assertFinding(only(findings(SMALL, utf8("{\"" + wide + "\":1}"))),
                "ESJ-L1-LIMIT", "", "[\"" + subject + "\"]");
        assertFinding(only(findings(SMALL, utf8("{\"source\":{\"" + wide + "\":1}}"))),
                "ESJ-L1-LIMIT", "", "source[\"" + subject + "\"]");
        assertFinding(only(findings(SMALL, document(
                        "\"values\":{},\"extensions\":{\"" + wide + "\":1}"))),
                "ESJ-L1-LIMIT", "", "extensions[\"" + subject + "\"]");
        assertFinding(only(findings(SMALL, document(
                        "\"values\":{},\"extensions\":{\"de.example\":[{\"" + wide + "\":1}]}"))),
                "ESJ-L1-LIMIT", "", "extensions[\"de.example\"][0][\"" + subject + "\"]");
        assertFinding(only(findings(SMALL, values(
                        "\"/BT-1\":{\"value\":\"a\",\"" + wide + "\":\"b\"}"))),
                "ESJ-L1-LIMIT", "/BT-1", "values[\"/BT-1\"][\"" + subject + "\"]");
    }

    @Test
    void aMemberNameAtTheStringBoundIsJudgedByItsGrammar() {
        String wide = "ä".repeat(32);

        assertFinding(only(findings(SMALL, values("\"" + wide + "\":\"x\""))),
                "ESJ-L1-PATH-SYNTAX", "", "values[\"" + wide + "\"]");
        assertFinding(only(findings(SMALL, document(
                        "\"values\":{},\"extensions\":{\"" + wide + "\":1}"))),
                "ESJ-L1-OWNER-TOKEN", "", "extensions[\"" + wide + "\"]");
    }

    /**
     * Inside the string bound the bounds of a grammar apply with the grammar's code: an owner
     * token of 129 characters is no owner token (specification, section 4.6), and one past
     * the string bound is a limit.
     */
    @Test
    void anOwnerTokenIsJudgedByItsGrammarInsideTheStringBoundAndIsALimitPastIt() {
        String owner = "a".repeat(129);

        assertFinding(only(findings(document(
                        "\"values\":{},\"extensions\":{\"" + owner + "\":1}"))),
                "ESJ-L1-OWNER-TOKEN", "", "extensions[\"" + owner + "\"]");
        String longer = "a".repeat(65);
        assertFinding(only(findings(SMALL, document(
                        "\"values\":{},\"extensions\":{\"" + longer + "\":1}"))),
                "ESJ-L1-LIMIT", "", "extensions[\"" + longer + "\"]");
    }

    /**
     * A number token longer than the string bound is a limit wherever it stands, measured on
     * its spelling before anything is said about its type (specification, section 12.2).
     */
    @Test
    void aNumberTokenLongerThanTheStringBoundIsALimitWhereverItStands() {
        String integer = "1".repeat(65);
        String fraction = "1." + "0".repeat(63);

        assertFinding(only(findings(SMALL, values("\"/BT-1\":" + integer))),
                "ESJ-L1-LIMIT", "/BT-1", "values[\"/BT-1\"]");
        assertFinding(only(findings(SMALL, values("\"/BT-1\":" + fraction))),
                "ESJ-L1-LIMIT", "/BT-1", "values[\"/BT-1\"]");
        assertFinding(only(findings(SMALL, values(
                        "\"/BT-1\":{\"value\":" + fraction + ",\"scheme\":\"s\"}"))),
                "ESJ-L1-LIMIT", "/BT-1", "values[\"/BT-1\"].value");
        assertFinding(only(findings(SMALL, values("\"/BT-1\":[[" + integer + "]]"))),
                "ESJ-L1-LIMIT", "/BT-1", "values[\"/BT-1\"]");
        assertFinding(only(findings(SMALL, utf8("{\"format\":" + integer + "}"))),
                "ESJ-L1-LIMIT", "", "format");
        assertFinding(only(findings(SMALL, document(
                        "\"values\":{},\"extensions\":{\"de.example\":[1," + fraction + "]}"))),
                "ESJ-L1-LIMIT", "", "extensions[\"de.example\"][1]");
    }

    @Test
    void aNumberTokenAtTheStringBoundInValuesIsAJsonTypeFinding() {
        assertFinding(only(findings(SMALL, values("\"/BT-1\":" + "1".repeat(64)))),
                "ESJ-L1-JSON-TYPE", "/BT-1", "values[\"/BT-1\"]");
    }

    /**
     * A bound met while the reader walks past a structure inside {@code values} names the
     * member walked past and its path: that member is what the reader judged (specification,
     * sections 9.5 and 12.2).
     */
    @Test
    void aBoundMetInAStructureWalkedPastNamesTheMemberWalkedPast() {
        String deep = "[".repeat(33) + "]".repeat(33);

        assertFinding(only(findings(values("\"/BT-1\":" + deep))),
                "ESJ-L1-LIMIT", "/BT-1", "values[\"/BT-1\"]");
        assertFinding(only(findings(values(
                        "\"/BT-1\":{\"value\":\"a\",\"scheme\":\"s\",\"note\":" + deep + "}"))),
                "ESJ-L1-LIMIT", "/BT-1", "values[\"/BT-1\"][\"note\"]");
        assertFinding(only(findings(SMALL, values(
                        "\"/BT-1\":[{\"" + "n".repeat(65) + "\":1}]"))),
                "ESJ-L1-LIMIT", "/BT-1", "values[\"/BT-1\"]");
    }

    /**
     * A string in a structure the reader walks past is checked to be JSON and never built,
     * so no string bound reaches it; names, numbers and the depth are bounded there
     * (specification, section 12.2).
     */
    @Test
    void aStringWalkedPastIsHeldToNoStringBound() {
        Limits tight = SMALL.withMaxBinaryValueBytes(64);

        assertFinding(only(findings(tight, values("\"/BT-1\":[\"" + "s".repeat(1000) + "\"]"))),
                "ESJ-L1-JSON-TYPE", "/BT-1", "values[\"/BT-1\"]");
        assertFinding(only(findings(tight, values(
                        "\"/BT-1\":{\"value\":\"a\",\"scheme\":\"s\",\"note\":[\""
                                + "s".repeat(1000) + "\"]}"))),
                "ESJ-L1-JSON-TYPE", "/BT-1", "values[\"/BT-1\"][\"note\"]");
    }

    /** A name past the string bound is a limit before a surrogate in it is anything. */
    @Test
    void theBoundOnANameIsAskedBeforeItsSurrogates() {
        assertFinding(only(findings(SMALL, values("\"\\ud800" + "x".repeat(64) + "\":\"a\""))),
                "ESJ-L1-LIMIT", "", "values[\"\\ud800" + "x".repeat(64) + "\"]");
    }



    /**
     * The offset a message names is the byte offset at which the token that met the bound
     * begins, counted in UTF-8 bytes from zero (specification, section 9.5).
     */
    @Test
    void theOffsetOfALimitIsTheByteTheTokenBeginsAt() {
        String text = HEAD + "\"values\":{\"/BT-1\":\"ääää\",\"" + "x".repeat(65) + "\":\"y\"}}";
        byte[] bytes = utf8(text);
        int begins = new String(bytes, StandardCharsets.UTF_8).indexOf("\"xxx");
        int inBytes = utf8(text.substring(0, begins)).length;

        Finding finding = only(EsjReader.withLimits(SMALL).readWithFindings(bytes).findings()
                .stream().filter(Finding::isError).toList());

        assertEquals("ESJ-L1-LIMIT", finding.code().code());
        assertEquals(inBytes, byteOffset(finding.message()));
        assertTrue(inBytes > begins, "four characters before it take two bytes each");
    }

    /**
     * The decoded size of a base64 value is {@code floor(L / 4) * 3} less the padding that
     * ends it, of which at most two count, and never negative (specification, section 12.2):
     * {@code ========} decodes to four bytes, not to minus two.
     */
    @Test
    void theDecodedSizeOfABinaryValueCountsAtMostTwoPaddingCharacters() {
        assertEquals(4, Texts.decodedBase64Length("========"));
        assertEquals(1, Texts.decodedBase64Length("===="));
        assertEquals(1, Texts.decodedBase64Length("QQ=="));
        assertEquals(2, Texts.decodedBase64Length("QUI="));
        assertEquals(3, Texts.decodedBase64Length("QUJD"));
        assertEquals(0, Texts.decodedBase64Length("="));
        assertEquals(0, Texts.decodedBase64Length(""));

        byte[] bytes = values("\"/BG-24/0/BT-125\":{\"value\":\"========\","
                + "\"mimeCode\":\"application/pdf\",\"filename\":\"a.pdf\"}");
        Limits three = Limits.defaults().withMaxTotalBinaryBytes(3);
        assertFinding(only(findings(three, bytes)), "ESJ-L1-LIMIT", "/BG-24/0/BT-125",
                "values[\"/BG-24/0/BT-125\"]");
        assertTrue(findings(Limits.defaults().withMaxTotalBinaryBytes(4), bytes).isEmpty());
    }

    // ------------------------------------------------------------------ subjects

    @Test
    void aNameTheDocumentChoseIsWrittenInBracketsAndADefinedNameDotted() {
        assertFinding(only(findings(document("\"values\":{},\"profile\":\"x\""))),
                "ESJ-L1-ENVELOPE-MEMBER", "", "[\"profile\"]");
        assertFinding(only(findings(document("\"values\":{},\"source\":{\"origin\":\"x\"}"))),
                "ESJ-L1-ENVELOPE-MEMBER", "", "source[\"origin\"]");
        assertFinding(only(findings(values(
                        "\"/BT-1\":{\"value\":\"a\",\"scheme\":\"s\",\"note\":\"x\"}"))),
                "ESJ-L1-VALUE-MEMBER", "/BT-1", "values[\"/BT-1\"][\"note\"]");
        assertFinding(only(findings(values("\"/BT-1\":{\"value\":\"a\",\"scheme\":5}"))),
                "ESJ-L1-JSON-TYPE", "/BT-1", "values[\"/BT-1\"].scheme");
        assertFinding(only(findings(values(
                        "\"/BT-1\":{\"value\":\"a\",\"schemeVersion\":\"1\"}"))),
                "ESJ-L1-VALUE-MEMBER", "/BT-1", "values[\"/BT-1\"].schemeVersion");
        assertFinding(only(findings(values(
                        "\"/BT-1\":{\"value\":\"a\",\"scheme\":\"s\",\"value\\u0041\":{}}"))),
                "ESJ-L1-VALUE-SHAPE", "/BT-1", "values[\"/BT-1\"][\"valueA\"]");
    }

    /**
     * A duplicate member name is named by the member access of its second occurrence, in
     * every object the reader judges but a value object, whose findings name the value
     * (specification, section 9.5).
     */
    @Test
    void aDuplicateIsNamedByItsOwnMemberAccess() {
        assertFinding(only(findings(values("\"/BT-1\":\"a\",\"/BT-1\":\"b\""))),
                "ESJ-L1-DUPLICATE-MEMBER", "", "values[\"/BT-1\"]");
        assertFinding(only(findings(values("\"/BT-1\":\"a\",\"/BT-\\u0031\":\"b\""))),
                "ESJ-L1-DUPLICATE-MEMBER", "", "values[\"/BT-1\"]");
        assertFinding(only(findings(utf8(HEAD + FORMAT + ",\"values\":{}}"))),
                "ESJ-L1-DUPLICATE-MEMBER", "", "format");
        assertFinding(only(findings(document(
                        "\"values\":{},\"source\":{\"syntax\":\"UBL\",\"syntax\":\"CII\"}"))),
                "ESJ-L1-DUPLICATE-MEMBER", "", "source.syntax");
        assertFinding(only(findings(document(
                        "\"values\":{},\"extensions\":{\"a.b\":1,\"a.b\":2}"))),
                "ESJ-L1-DUPLICATE-MEMBER", "", "extensions[\"a.b\"]");
        assertFinding(only(findings(document(
                        "\"values\":{},\"extensions\":{\"a.b\":{\"x\":1,\"x\":2}}"))),
                "ESJ-L1-DUPLICATE-MEMBER", "", "extensions[\"a.b\"][\"x\"]");
        assertFinding(only(findings(document(
                        "\"values\":{},\"extensions\":{\"a.b\":[{\"x\":1,\"x\":2}]}"))),
                "ESJ-L1-DUPLICATE-MEMBER", "", "extensions[\"a.b\"][0][\"x\"]");
        assertFinding(only(findings(values(
                        "\"/BT-1\":{\"value\":\"a\",\"scheme\":\"s\",\"value\":\"b\"}"))),
                "ESJ-L1-DUPLICATE-MEMBER", "/BT-1", "values[\"/BT-1\"]");
    }

    /**
     * Every missing required envelope member is a finding of its own, named by the member,
     * in the order {@code format}, {@code version}, {@code semanticModel}, {@code values}
     * (specification, section 9.5).
     */
    @Test
    void everyMissingEnvelopeMemberIsAFindingOfItsOwn() {
        List<Finding> all = findings(utf8("{}"));
        assertEquals(List.of("format", "version", "semanticModel", "values"),
                all.stream().map(Finding::subject).toList());
        all.forEach(finding -> assertFinding(finding, "ESJ-L1-ENVELOPE-MEMBER", "",
                finding.subject()));

        List<Finding> two = findings(utf8("{" + VERSION + "," + MODEL + "}"));
        assertEquals(List.of("format", "values"), two.stream().map(Finding::subject).toList());

        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> EsjReader.strict().read(utf8("{" + VERSION + "}")));
        assertEquals("format", thrown.subject());
    }

    /** A subject is never shortened; the message is (specification, sections 9.5 and 12.6). */
    @Test
    void theSubjectOfAnUndefinedEnvelopeMemberIsWhole() {
        String name = "profile-" + "x".repeat(600);

        Finding finding = only(findings(document("\"values\":{},\"" + name + "\":1")));

        assertFinding(finding, "ESJ-L1-ENVELOPE-MEMBER", "", "[\"" + name + "\"]");
        assertFalse(finding.message().contains(name), "the message carries an excerpt");
    }

    /**
     * A member name that carries a lone surrogate is named by its member access, the
     * surrogate escaped as {@code \}{@code udXXX} in lowercase (specification, section 9.5).
     */
    @Test
    void aNameWithALoneSurrogateIsNamedWithTheSurrogateEscaped() {
        assertFinding(only(findings(values("\"/BT-1\\uD800\":\"x\""))),
                "ESJ-L1-SURROGATE", "", "values[\"/BT-1\\ud800\"]");
        assertFinding(only(findings(utf8("{\"\\ud800\":1}"))),
                "ESJ-L1-SURROGATE", "", "[\"\\ud800\"]");
        assertFinding(only(findings(document("\"values\":{},\"source\":{\"\\udc00\":\"x\"}"))),
                "ESJ-L1-SURROGATE", "", "source[\"\\udc00\"]");
        assertFinding(only(findings(values(
                        "\"/BT-1\":{\"value\":\"a\",\"\\ud800\":\"b\",\"scheme\":\"s\"}"))),
                "ESJ-L1-SURROGATE", "/BT-1", "values[\"/BT-1\"]");
        assertFinding(only(findings(document("\"values\":{},\"extensions\":{\"\\ud800x\":1}"))),
                "ESJ-L1-SURROGATE", "", "extensions[\"\\ud800x\"]");
        assertFinding(only(findings(document(
                        "\"values\":{},\"extensions\":{\"o\":{\"a\\udfff\":1}}"))),
                "ESJ-L1-SURROGATE", "", "extensions[\"o\"][\"a\\udfff\"]");
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> EsjReader.strict().read(values("\"\\ud800\":\"x\"")));
        assertEquals("values[\"\\ud800\"]", thrown.subject());
    }

    // ------------------------------------------------------------------ the JSON text

    /**
     * A byte sequence that is no JSON text is a finding about the document: an empty path,
     * an empty subject, and the byte offset of the token in the message. A token that stands
     * where a value belongs and is no complete JSON value is such a defect, before anything
     * is said about its type (specification, section 9.6).
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"format\":tru}            | 10",
            "{\"format\":01}             | 10",
            "{\"format\":1.}             | 10",
            "{\"format\":\"a\\x\"}       | 10",
            "{\"values\":-}              | 10",
            "{\"values\":truex}          | 10",
            "{\"values\":true_}          | 10",
            "{\"format\":nul}            | 10",
            "{\"format\":1e}             | 10",
            "''                          | 0"})
    void aTokenThatIsNoCompleteJsonValueIsADocumentFinding(String text, long offset) {
        Finding finding = only(findings(utf8(text)));

        assertFinding(finding, "ESJ-L1-JSON", "", "");
        assertEquals(offset, byteOffset(finding.message()), finding.message());
    }

    /** A complete JSON value of the wrong type is judged by its type. */
    @Test
    void aCompleteValueOfTheWrongTypeIsJudgedByItsType() {
        assertFinding(only(findings(utf8("{\"values\":5 }"))),
                "ESJ-L1-ENVELOPE-VALUE", "", "values");
        assertFinding(only(findings(utf8("{\"format\":true}"))),
                "ESJ-L1-ENVELOPE-VALUE", "", "format");
        assertFinding(only(findings(values("\"/BT-1\":-0.5e+3"))),
                "ESJ-L1-JSON-TYPE", "/BT-1", "values[\"/BT-1\"]");
        // A number is complete where its grammar ends; what follows is the next token.
        assertFinding(only(findings(utf8("{\"format\":1.5x}"))),
                "ESJ-L1-ENVELOPE-VALUE", "", "format");
        List<Finding> both = findings(values("\"/BT-1\":0x"));
        assertEquals(2, both.size(), both::toString);
        assertFinding(both.get(0), "ESJ-L1-JSON-TYPE", "/BT-1", "values[\"/BT-1\"]");
        assertFinding(both.get(1), "ESJ-L1-JSON", "", "");
    }

    /**
     * The offset of a defect of the JSON text is the byte offset of the token it was met in,
     * and the finding is about the document even where the defect sits inside a member of
     * {@code values} (specification, sections 9.5 and 9.6).
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"values\":{\"/BT-1\":01}}                                  | 01",
            "\"values\":{\"/BT-1\":\"a\" \"/BT-2\":\"b\"}}             | \"/BT-2",
            "\"values\":{\"/BT-1\":\"a\",}}                              | }}",
            "\"values\":{\"/BT-1\":{\"value\":\"a\",\"scheme\":tru}}}  | tru",
            "\"values\":{\"/BT-1\":[1,]}}                                | ]}",
            "\"values\":{}} x                                             | x",
            "\"values\":{}                                                | END"})
    void aDefectOfTheJsonTextIsLocatedByTheByteItsTokenBeginsAt(String tail, String token) {
        String text = HEAD + tail;
        Finding finding = only(findings(utf8(text)).stream()
                .filter(each -> each.code().code().equals("ESJ-L1-JSON")).toList());

        assertFinding(finding, "ESJ-L1-JSON", "", "");
        long expected = token.equals("END") ? text.length() : text.lastIndexOf(token);
        assertEquals(expected, byteOffset(finding.message()), finding.message());
    }

    /**
     * A defect of a member name is found before a defect of its value, so that the first
     * defect in the text is the first finding (specification, section 9.6).
     */
    @Test
    void aMemberNameIsJudgedBeforeTheValueWrittenUnderIt() {
        assertFinding(only(findings(utf8("{\"foo\":tru}"))),
                "ESJ-L1-ENVELOPE-MEMBER", "", "[\"foo\"]");
        assertFinding(only(findings(utf8("{\"foo\" 1}"))),
                "ESJ-L1-ENVELOPE-MEMBER", "", "[\"foo\"]");
        assertFinding(only(findings(utf8("{" + FORMAT + ",\"format\":tru}"))),
                "ESJ-L1-DUPLICATE-MEMBER", "", "format");
        assertFinding(only(findings(values("\"/BT-1\":\"a\",\"/BT-1\":01"))),
                "ESJ-L1-DUPLICATE-MEMBER", "", "values[\"/BT-1\"]");
        assertFinding(only(findings(document("\"values\":{},\"extensions\":{\"x/y\":tru}"))),
                "ESJ-L1-OWNER-TOKEN", "", "extensions[\"x/y\"]");
        assertFinding(only(findings(values("\"\\ud800\":01"))),
                "ESJ-L1-SURROGATE", "", "values[\"\\ud800\"]");

        List<Finding> both = findings(values("\"/BT 1\":tru"));
        assertEquals(2, both.size(), both::toString);
        assertFinding(both.get(0), "ESJ-L1-PATH-SYNTAX", "", "values[\"/BT 1\"]");
        assertFinding(both.get(1), "ESJ-L1-JSON", "", "");
    }

    /**
     * A structure the reader walks past is checked for JSON and for the limits and for
     * nothing else: a lone surrogate or a repeated name inside it is no finding, and the
     * reader reads on behind it (specification, section 9.6).
     */
    @Test
    void aStructureWalkedPastIsCheckedForJsonAndTheLimitsAlone() {
        List<Finding> broken = findings(values("\"/BT-1\":[\"\\ud800\"],\"/BT-2\":01"));
        assertEquals(2, broken.size(), broken::toString);
        assertFinding(broken.get(0), "ESJ-L1-JSON-TYPE", "/BT-1", "values[\"/BT-1\"]");
        assertFinding(broken.get(1), "ESJ-L1-JSON", "", "");

        ReadResult read = EsjReader.strict().readWithFindings(values(
                "\"/BT-1\":[{\"\\ud800\":1,\"a\":2,\"a\":3}],\"/BT-2\":\"2026-01-15\""));
        assertFinding(only(read.findings()), "ESJ-L1-JSON-TYPE", "/BT-1", "values[\"/BT-1\"]");
        SemanticDocument document = read.document().orElseThrow();
        assertEquals("2026-01-15",
                document.value(SemanticPath.of("/BT-2")).orElseThrow().asString());

        assertFinding(only(findings(values("\"/BT-1\":{\"value\":\"a\",\"scheme\":\"s\","
                        + "\"note\":{\"\\udc00\":1,\"x\":1,\"x\":2}}"))),
                "ESJ-L1-VALUE-SHAPE", "/BT-1", "values[\"/BT-1\"][\"note\"]");
    }

    @Test
    void anUndefinedEnvelopeMemberIsThatAloneWhateverItsValueCarries() {
        assertFinding(only(findings(document("\"values\":{},\"profile\":\"\\ud800\""))),
                "ESJ-L1-ENVELOPE-MEMBER", "", "[\"profile\"]");
        assertFinding(only(findings(document("\"values\":{},\"profile\":[01]"))),
                "ESJ-L1-ENVELOPE-MEMBER", "", "[\"profile\"]");
    }

    // ------------------------------------------------------------------ exceptions

    /** An exception carries the code, the path and the subject of its finding. */
    @Test
    void anExceptionCarriesTheCodeThePathAndTheSubjectOfItsFinding() {
        EsjFormatException member = assertThrows(EsjFormatException.class,
                () -> EsjReader.strict().read(values(
                        "\"/BT-1\":{\"value\":\"a\",\"scheme\":\"s\",\"x\":\"y\"}")));
        assertEquals("ESJ-L1-VALUE-MEMBER", member.code().orElseThrow().code());
        assertEquals(SemanticPath.of("/BT-1"), member.path());
        assertEquals("values[\"/BT-1\"][\"x\"]", member.subject());

        EsjFormatException json = assertThrows(EsjFormatException.class,
                () -> EsjReader.strict().read(values("\"/BT-1\":01")));
        assertEquals("ESJ-L1-JSON", json.code().orElseThrow().code());
        assertTrue(json.path().isRoot());
        assertEquals("", json.subject());

        EsjLimitException limit = assertThrows(EsjLimitException.class,
                () -> EsjReader.withLimits(SMALL).read(values(
                        "\"/BT-1\":\"" + "a".repeat(65) + "\"")));
        assertEquals(SemanticPath.of("/BT-1"), limit.path());
        assertEquals("values[\"/BT-1\"]", limit.subject());
        assertEquals("maxStringBytes", limit.bound().orElseThrow().name());
    }
}
