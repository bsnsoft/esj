package de.bsnsoft.esj.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks validation layer L1 as the reader enforces it (specification, sections 3.2 and 9.1). */
class EsjReaderTest {

    private static final String HEAD = """
            {"format":"EN16931-Semantic-JSON","version":"0.1",\
            "semanticModel":"EN16931-1:2017+A1:2019/AC:2020",""";

    private final EsjReader reader = EsjReader.strict();

    private static byte[] envelope(String tail) {
        return (HEAD + tail + "}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] oneValue(String path, String value) {
        return envelope("\"values\":{\"" + path + "\":" + value + "}");
    }

    private String codeOf(byte[] document) {
        EsjFormatException thrown =
                assertThrows(EsjFormatException.class, () -> reader.read(document));
        return thrown.code().orElseThrow().code();
    }

    /** Returns the codes a collecting read reports, sorted, so that order is no claim. */
    private List<String> codesOf(byte[] document) {
        return reader.readWithFindings(document).findings().stream()
                .map(finding -> finding.code().code())
                .sorted()
                .toList();
    }

    @Test
    void aValueObjectWithAComponentIsRead() {
        SemanticDocument document = reader.read(oneValue("/BG-4/BT-29/0",
                "{\"value\":\"0088123456785\",\"scheme\":\"0088\"}"));

        assertEquals(SemanticValue.identifier("0088123456785", "0088"),
                document.value(SemanticPath.of("/BG-4/BT-29/0")).orElseThrow());
    }

    @Test
    void aValueObjectWithTwoComponentsIsRead() {
        SemanticDocument document = reader.read(oneValue("/BG-24/0/BT-125",
                "{\"value\":\"QUJDRQ==\",\"mimeCode\":\"application/pdf\","
                        + "\"filename\":\"note.pdf\"}"));
        SemanticValue value = document.value(SemanticPath.of("/BG-24/0/BT-125")).orElseThrow();

        assertEquals(SemanticValue.binary("ABCE".getBytes(StandardCharsets.UTF_8),
                "application/pdf", "note.pdf"), value);
        assertTrue(value.hasComponents());
    }

    @Test
    void aMinimalEnvelopeIsRead() {
        SemanticDocument document = reader.read(oneValue("/BT-1", "\"RE-1\""));

        assertEquals("EN16931-1:2017+A1:2019/AC:2020", document.semanticModel());
        assertEquals(SemanticValue.of("RE-1"),
                document.value(SemanticPath.of("/BT-1")).orElseThrow());
        assertTrue(document.extensions().isEmpty());
        assertTrue(document.source().isEmpty());
    }

    @Test
    void aStreamIsReadLikeAByteArray() {
        byte[] bytes = oneValue("/BT-1", "\"RE-1\"");
        assertEquals(reader.read(bytes), reader.read(new ByteArrayInputStream(bytes)));
    }

    @Test
    void theDefaultsAreTheLimitsOfTheSpecification() {
        assertEquals(Limits.defaults(), reader.limits());
        assertEquals(64L * 1024 * 1024, Limits.defaults().maxDocumentBytes());
        assertEquals(100_000, Limits.defaults().maxValues());
        assertEquals(16, Limits.defaults().maxPathSegments());
        assertEquals(256, Limits.defaults().maxPathBytes());
        assertEquals(1024L * 1024, Limits.defaults().maxStringBytes());
        assertEquals(32L * 1024 * 1024, Limits.defaults().maxBinaryValueBytes());
        assertEquals(48L * 1024 * 1024, Limits.defaults().maxTotalBinaryBytes());
        assertEquals(32, Limits.defaults().maxExtensionDepth());
    }

    @Test
    void aByteOrderMarkIsRejected() {
        byte[] body = oneValue("/BT-1", "\"RE-1\"");
        byte[] withMark = new byte[body.length + 3];
        withMark[0] = (byte) 0xEF;
        withMark[1] = (byte) 0xBB;
        withMark[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withMark, 3, body.length);
        assertEquals("ESJ-L1-ENCODING", codeOf(withMark));
    }

    @Test
    void aByteSequenceThatIsNotUtf8IsRejected() {
        byte[] body = oneValue("/BG-4/BT-27", "\"aa\"");
        body[body.length - 4] = (byte) 0xC3;
        body[body.length - 3] = (byte) 0x28;
        assertEquals("ESJ-L1-ENCODING", codeOf(body));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "\"a string\"",
            "42",
            "null",
            "{\"format\":\"EN16931-Semantic-JSON\""})
    void aByteSequenceThatIsNotAnEsjObjectIsRejected(String text) {
        assertEquals("ESJ-L1-JSON", codeOf(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void contentAfterTheObjectThatClosesTheDocumentIsRejected() {
        String document = new String(oneValue("/BT-1", "\"a\""),
                StandardCharsets.UTF_8);
        assertEquals("ESJ-L1-JSON", codeOf((document + " {}").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aDuplicateMemberNameIsRejectedAtEveryDepth() {
        assertEquals("ESJ-L1-DUPLICATE-MEMBER", codeOf(envelope(
                "\"values\":{\"/BT-1\":\"a\","
                        + "\"/BT-1\":\"b\"}")));
        assertEquals("ESJ-L1-DUPLICATE-MEMBER", codeOf(envelope(
                "\"values\":{\"/BT-1\":{\"type\":\"identifier\",\"value\":\"a\",\"value\":\"b\"}}")));
        assertEquals("ESJ-L1-DUPLICATE-MEMBER", codeOf(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":{\"a\":1,\"a\":2}}")));
        assertEquals("ESJ-L1-DUPLICATE-MEMBER", codeOf(
                (HEAD + "\"values\":{},\"values\":{}}").getBytes(StandardCharsets.UTF_8)));
        assertEquals("ESJ-L1-DUPLICATE-MEMBER", codeOf(envelope(
                "\"values\":{},\"source\":{\"syntax\":\"UBL\",\"syntax\":\"CII\"}")));
        assertEquals("ESJ-L1-DUPLICATE-MEMBER", codeOf(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":1,"
                        + "\"de.example.vendor\":2}")));
        assertEquals("ESJ-L1-DUPLICATE-MEMBER", codeOf(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":"
                        + "{\"a\":[{\"b\":1,\"b\":2}]}}")));
    }

    @Test
    void aDuplicateMemberNameIsTheReadersOwnFindingAndNotTheParsersWording() {
        EsjFormatException thrown = assertThrows(EsjFormatException.class, () -> reader.read(
                envelope("\"values\":{\"/BT-1\":\"a\","
                        + "\"/BT-1\":\"b\"}")));

        assertEquals("ESJ-L1-DUPLICATE-MEMBER", thrown.code().orElseThrow().code());
        assertTrue(thrown.getMessage()
                        .startsWith("the member name /BT-1 occurs twice in one object"),
                thrown.getMessage());
        assertTrue(thrown.getCause() == null, "no parser exception is needed to classify it");
    }

    @ParameterizedTest
    @CsvSource({
            "'\"values\":{},\"profile\":\"x\"',                      ESJ-L1-ENVELOPE-MEMBER",
            "'\"values\":{},\"source\":{\"origin\":\"x\"}',          ESJ-L1-ENVELOPE-MEMBER",
            "'\"values\":[]',                                        ESJ-L1-ENVELOPE-VALUE",
            "'\"values\":{},\"extensions\":\"x\"',                   ESJ-L1-ENVELOPE-VALUE",
            "'\"values\":{},\"source\":5',                           ESJ-L1-ENVELOPE-VALUE",
            "'\"values\":{},\"extensions\":{}',                      ESJ-L1-ENVELOPE-VALUE",
            "'\"values\":{},\"source\":{}',                          ESJ-L1-ENVELOPE-VALUE",
            "'\"values\":{},\"source\":{\"syntax\":\"\"}',           ESJ-L1-ENVELOPE-VALUE",
            "'\"values\":{},\"source\":{\"sha256\":\"AB\"}',         ESJ-L1-ENVELOPE-VALUE"})
    void theEnvelopeIsExactlyTheOneTheSpecificationDefines(String tail, String code) {
        assertEquals(code, codeOf(envelope(tail)));
    }

    /**
     * The edition rule of the specification, section 4.4: layer L1 asks that
     * {@code semanticModel} satisfy the edition grammar and asks nothing else of it.
     * Whether a registry for that edition exists is an L2 question, so a reader written
     * today reads, canonicalizes and digests a document of an edition published after it
     * and claims about it only what it can see.
     */
    @Test
    void anEditionThisImplementationHasNoRegistryForIsReadLikeAnyOther() {
        byte[] document = ("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2026\",\"values\":{\"/BT-1\":\"RE-1\"}}")
                .getBytes(StandardCharsets.UTF_8);

        SemanticDocument later = reader.read(document);

        assertEquals("EN16931-1:2026", later.semanticModel());
        assertEquals("RE-1", later.value(SemanticPath.of("/BT-1")).orElseThrow().asString());
        assertTrue(reader.readWithFindings(document).isWellFormed());
        assertArrayEquals(document, Canonicalizer.canonicalize(document));
        assertEquals(64, Canonicalizer.documentDigest(later).length());
        assertEquals(64, Canonicalizer.semanticDigest(later).length());
        assertNotEquals(Canonicalizer.semanticDigest(later),
                Canonicalizer.semanticDigest(SemanticDocument.builder()
                        .put("/BT-1", "RE-1")
                        .build()),
                "the semantic digest covers the edition (specification, section 8.2)");
    }

    /**
     * A {@code semanticModel} that is no edition at all is the one thing L1 refuses here:
     * an empty string, a word, and the spelling with spaces that a registry file uses and
     * a document does not (specification, sections 4.4 and 10).
     */
    @ParameterizedTest
    @ValueSource(strings = {"whatever", "EN 16931-1:2017+A1:2019/AC:2020", "EN16931-1",
            "EN16931-1:20177", "en16931-1:2017+A1:2019/AC:2020 "})
    void aSemanticModelThatIsNotAnEditionIsRejected(String value) {
        EsjFormatException thrown = assertThrows(EsjFormatException.class, () -> reader.read(
                ("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                        + "\"semanticModel\":\"" + value + "\",\"values\":{}}")
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("ESJ-L1-ENVELOPE-VALUE", thrown.code().orElseThrow().code());
        assertEquals("semanticModel", thrown.location().orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({
            "'\"format\":\"ESJ\"',                                   format",
            "'\"version\":\"0.2\"',                                  version"})
    void aFixedEnvelopeValueMustBeTheOneTheSpecificationFixes(String member, String name) {
        String text = "{" + member + ",\"values\":{}}";
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> reader.read(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals(name, thrown.location().orElseThrow());
    }

    @Test
    void aMissingRequiredEnvelopeMemberIsRejected() {
        assertEquals("ESJ-L1-ENVELOPE-MEMBER", codeOf(
                "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\"}"
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "false", "null", "[]", "[\"a\"]"})
    void aNumberABooleanANullOrAnArrayInsideValuesIsRejected(String json) {
        assertEquals("ESJ-L1-JSON-TYPE", codeOf(oneValue("/BT-1", json)));
        assertEquals("ESJ-L1-JSON-TYPE", codeOf(
                oneValue("/BT-1", "{\"value\":" + json + ",\"scheme\":\"0088\"}")));
    }

    /**
     * The value shape of the specification, section 6.1: a value object always carries a
     * supplementary component, because a value that is nothing but content is written as a
     * JSON string and one content must not have two spellings.
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "{\"value\":\"RE-1\"}                                  ; ESJ-L1-VALUE-SHAPE",
            "{}                                                  ; ESJ-L1-VALUE-SHAPE",
            "{\"type\":\"identifier\",\"value\":\"RE-1\"}            ; ESJ-L1-VALUE-SHAPE",
            "{\"value\":{\"a\":\"b\"},\"scheme\":\"0088\"}          ; ESJ-L1-VALUE-SHAPE",
            "{\"scheme\":\"0088\"}                                 ; ESJ-L1-VALUE-MEMBER",
            "{\"value\":\"a\",\"scheme\":\"0088\",\"x\":\"y\"}      ; ESJ-L1-VALUE-MEMBER",
            "{\"value\":\"a\",\"schemeVersion\":\"2\"}             ; ESJ-L1-VALUE-MEMBER",
            "{\"value\":\"\",\"scheme\":\"0088\"}                  ; ESJ-L1-EMPTY-STRING",
            "{\"value\":\"a\",\"scheme\":\"\"}                     ; ESJ-L1-EMPTY-STRING",
            "\"\"                                                  ; ESJ-L1-EMPTY-STRING"})
    void theShapeAndTheMemberSetOfAValueAreTheOnesSectionSixOneDefines(String value, String code) {
        assertEquals(code, codeOf(oneValue("/BT-1", value)));
    }

    /**
     * The precedence of the specification, section 9.6: the shape of a value object is
     * decided before its member set is examined, so an object with an undefined member and
     * no component at all is a shape error and not a member error.
     */
    @Test
    void theShapeOfAValueObjectIsDecidedBeforeItsMemberSet() {
        assertEquals("ESJ-L1-VALUE-SHAPE",
                codeOf(oneValue("/BT-1", "{\"value\":\"a\",\"note\":\"x\"}")));
        assertEquals("ESJ-L1-VALUE-MEMBER",
                codeOf(oneValue("/BT-1", "{\"value\":\"a\",\"scheme\":\"s\",\"note\":\"x\"}")));
    }

    /**
     * The third precedence rule of the specification, section 9.6: a value object that is
     * already wrong as a member set is that error, whatever its members spell. Both inputs
     * below match the words of {@code ESJ-L1-EMPTY-STRING} as well, and the rule is what
     * keeps two conformant validators from answering differently.
     */
    @Test
    void theMemberSetOfAValueObjectIsDecidedBeforeTheContentOfItsMembers() {
        assertEquals("ESJ-L1-VALUE-MEMBER",
                codeOf(oneValue("/BT-1", "{\"scheme\":\"\"}")));
        assertEquals("ESJ-L1-VALUE-MEMBER",
                codeOf(oneValue("/BT-1", "{\"value\":\"X\",\"scheme\":\"0088\",\"foo\":\"\"}")));
        assertEquals("ESJ-L1-EMPTY-STRING",
                codeOf(oneValue("/BT-1", "{\"value\":\"0088123456785\",\"scheme\":\"\"}")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/BT-0", "BT-1", "/BG-4", "/BT-1/", "//BT-1", "/BG-4/BT-29/00", "/bt-1",
            "/BG-4/0/0/BT-27", "/BT-1/0/BT-2", "/BG-dex-1/BT-1"})
    void aMemberNameOfValuesThatIsNotAPathIsRejected(String path) {
        assertEquals("ESJ-L1-PATH-SYNTAX",
                codeOf(oneValue(path, "\"a\"")));
    }

    /**
     * What the reader does <em>not</em> check. Deciding that BT-106 carries a decimal and
     * BT-2 a date takes the registry, so those grammars are layer L2 (specification,
     * section 6.2). The content reaches the document as the sender spelled it, and nothing
     * here repairs it.
     */
    @ParameterizedTest
    @CsvSource({
            "/BG-22/BT-106, 100.00",
            "/BG-22/BT-106, 1E2",
            "/BG-22/BT-106, 42.015",
            "/BT-2,         2026-02-30",
            "/BT-2,         26-01-01"})
    void aContentTheRegistryWouldRefuseReachesTheDocumentUnchanged(String path, String content) {
        SemanticDocument document = reader.read(oneValue(path, "\"" + content + "\""));

        assertEquals(content,
                document.value(SemanticPath.of(path)).orElseThrow().canonicalContent());
    }

    @Test
    void aDecimalLongerThanSixtyFourCharactersIsNoConcernOfTheReader() {
        String tooLong = "1".repeat(65);

        assertEquals(tooLong, reader.read(oneValue("/BG-22/BT-106", "\"" + tooLong + "\""))
                .value(SemanticPath.of("/BG-22/BT-106")).orElseThrow().content());
    }

    @Test
    void aBinaryContentThatIsNotCanonicalBase64IsStillWellFormed() {
        SemanticDocument document = reader.read(oneValue("/BG-24/0/BT-125",
                "{\"value\":\"QUJDRR==\",\"mimeCode\":\"application/pdf\","
                        + "\"filename\":\"a.pdf\"}"));

        assertEquals("QUJDRR==",
                document.value(SemanticPath.of("/BG-24/0/BT-125")).orElseThrow().content());
    }

    @Test
    void aLoneSurrogateIsRejectedInValuesAndInExtensions() {
        assertEquals("ESJ-L1-SURROGATE", codeOf(oneValue("/BG-4/BT-27", "\"a\\ud800b\"")));
        assertEquals("ESJ-L1-SURROGATE", codeOf(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":\"a\\udc00\"}")));
    }

    /**
     * The lone-surrogate rule of the specification, section 6.8 is stated over every
     * string of the document, and section 9.6 gives {@code ESJ-L1-SURROGATE} precedence
     * over every code whose check reads the content of the same string. These are the
     * envelope strings that have a grammar or a fixed value of their own, plus
     * {@code source.syntax}, which has neither — it was the one string a reader could
     * accept and then be unable to encode.
     */
    @Test
    void aLoneSurrogateIsRejectedInEveryEnvelopeString() {
        assertEquals("ESJ-L1-SURROGATE",
                codeOf(envelope("\"values\":{},\"source\":{\"syntax\":\"\\ud800\"}")));
        assertEquals("ESJ-L1-SURROGATE",
                codeOf(envelope("\"values\":{},\"source\":{\"syntax\":\"A\\udc00B\"}")));
        assertEquals("ESJ-L1-SURROGATE",
                codeOf(envelope("\"values\":{},\"source\":{\"sha256\":\"\\ud800\"}")));
        assertEquals("ESJ-L1-SURROGATE", codeOf(("{\"format\":\"\\ud800\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{}}")
                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aLoneSurrogateInSourceCarriesTheLocationOfTheMember() {
        EsjFormatException thrown = assertThrows(EsjFormatException.class, () -> reader.read(
                envelope("\"values\":{},\"source\":{\"syntax\":\"UB\\ud800L\"}")));
        assertEquals("source.syntax", thrown.location().orElseThrow());
    }

    @Test
    void aPairedSurrogateInSourceIsNoDefect() {
        SemanticDocument document =
                reader.read(envelope("\"values\":{},\"source\":{\"syntax\":\"\\ud83d\\udcb6\"}"));
        assertEquals("\ud83d\udcb6", document.source().orElseThrow().syntax().orElseThrow());
    }

    @Test
    void aLoneSurrogateInAComponentCarriesTheLocationOfThatMember() {
        EsjFormatException thrown = assertThrows(EsjFormatException.class, () -> reader.read(
                oneValue("/BT-1", "{\"value\":\"RE-1\",\"scheme\":\"\\ud800\"}")));

        assertEquals("ESJ-L1-SURROGATE", thrown.code().orElseThrow().code());
        assertEquals("values[\"/BT-1\"].scheme", thrown.location().orElseThrow());
    }

    /**
     * A lone surrogate outranks every check that reads the content of the same string
     * (specification, section 9.6), the empty-string rule of section 6.1 included.
     */
    @Test
    void aLoneSurrogateOutranksTheRulesThatReadTheSameString() {
        assertEquals("ESJ-L1-SURROGATE", codeOf(oneValue("/BT-106", "\"1\\ud8000\"")));
        assertEquals("ESJ-L1-SURROGATE", codeOf(oneValue("/BT-2", "\"2026-01-\\ud800\"")));
        assertEquals("ESJ-L1-SURROGATE", codeOf(oneValue("/BG-24/0/BT-125",
                "{\"value\":\"QUJD\\ud800\",\"mimeCode\":\"text/plain\","
                        + "\"filename\":\"a.txt\"}")));
    }

    /**
     * The counter-example to the precedence rule: a check that reads a <em>different</em>
     * string is not outranked, so the two findings stand beside each other
     * (specification, section 9.6). Suppressing the surrogate would hide the one fact a
     * caller cannot work around, that the document has no UTF-8 encoding at all.
     */
    @Test
    void aSurrogateAndAnUndefinedMemberNameAreBothReported() {
        assertEquals(List.of("ESJ-L1-SURROGATE", "ESJ-L1-VALUE-MEMBER"),
                codesOf(oneValue("/BT-1",
                        "{\"value\":\"RE-1\",\"scheme\":\"0088\",\"note\":\"\\ud800\"}")));
        assertEquals(List.of("ESJ-L1-SURROGATE", "ESJ-L1-VALUE-MEMBER"),
                codesOf(oneValue("/BT-1", "{\"scheme\":\"a\\ud800b\",\"note\":\"y\"}")));
        assertEquals(List.of("ESJ-L1-SURROGATE"),
                codesOf(oneValue("/BT-1", "{\"value\":\"RE-1\",\"scheme\":\"a\\ud800b\"}")));
    }

    /**
     * A document the reader accepts is one the canonicalizer can encode (specification,
     * section 3.4). Every string of the fixture that carries a surrogate is refused, so
     * no accepted document can reach the canonicalizer without a UTF-8 encoding.
     */
    @Test
    void aDocumentCarryingASurrogateNeverReachesTheCanonicalizer() {
        assertEquals("ESJ-L1-SURROGATE", codeOf(Examples.invalid("source-lone-surrogate")));
        assertFalse(reader.readWithFindings(Examples.invalid("source-lone-surrogate"))
                .document().isPresent());
    }

    @Test
    void lineEndingsAreNormalizedAndNothingElseIsTouched() {
        SemanticDocument document =
                reader.read(oneValue("/BG-4/BT-27", "\"  a\\r\\nb\\rc\\nd  \""));

        assertEquals("  a\nb\nc\nd  ",
                document.value(SemanticPath.of("/BG-4/BT-27")).orElseThrow().content());
    }

    @ParameterizedTest
    @ValueSource(strings = {"urn:example:v/2", "de.exämple.vendor", ".vendor", "vendor-",
            "BT-1", "BG-1"})
    void aMemberNameOfExtensionsThatIsNotAnOwnerTokenIsRejected(String owner) {
        assertEquals("ESJ-L1-OWNER-TOKEN", codeOf(envelope(
                "\"values\":{},\"extensions\":{\"" + owner + "\":{\"a\":\"b\"}}")));
    }

    @Test
    void anExtensionSubtreeKeepsEveryJsonTypeAndEveryDigit() {
        SemanticDocument document = reader.read(envelope("""
                "values":{},"extensions":{"de.example.vendor":{\
                "n":[1e21,1e-7,-0.0,12345678901234567890,1.0000000000000001],\
                "t":true,"f":false,"z":null,"s":"x"}}"""));
        ExtensionValue.ObjectValue root =
                (ExtensionValue.ObjectValue) document.extensions().get("de.example.vendor");
        List<ExtensionValue> numbers =
                ((ExtensionValue.ArrayValue) root.members().get("n")).elements();
        assertEquals(new BigDecimal("1000000000000000000000"),
                ((ExtensionValue.NumberValue) numbers.get(0)).value());
        assertEquals(new BigDecimal("0.0000001"),
                ((ExtensionValue.NumberValue) numbers.get(1)).value());
        assertEquals(BigDecimal.ZERO, ((ExtensionValue.NumberValue) numbers.get(2)).value());
        assertEquals(new BigDecimal("12345678901234567890"),
                ((ExtensionValue.NumberValue) numbers.get(3)).value());
        assertEquals(new BigDecimal("1.0000000000000001"),
                ((ExtensionValue.NumberValue) numbers.get(4)).value());
        assertEquals(ExtensionValue.of(true), root.members().get("t"));
        assertEquals(ExtensionValue.of(false), root.members().get("f"));
        assertSame(ExtensionValue.nullValue(), root.members().get("z"));
        assertEquals(ExtensionValue.of("x"), root.members().get("s"));
    }

    @Test
    void aNumberInsideExtensionsWithATooLongCanonicalFormIsRejected() {
        assertEquals("ESJ-L1-EXT-NUMBER", codeOf(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":{\"n\":1e400}}")));
        assertEquals("ESJ-L1-EXT-NUMBER", codeOf(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":{\"n\":1e-400}}")));
    }

    @Test
    void aStringInsideExtensionsIsNotNormalized() {
        SemanticDocument document = reader.read(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":\"a\\r\\nb\"}"));
        assertEquals("a\r\nb",
                ((ExtensionValue.StringValue) document.extensions().get("de.example.vendor")).value());
    }

    @Test
    void provenanceIsReadWithBothMembersOrWithEither() {
        String digest = "228f74263f1a2795e21bc4a659c60afbe31a4ba71392e335e3e5e68953731be5";
        assertEquals(SemanticDocument.Source.of("UBL", digest),
                reader.read(envelope("\"values\":{},\"source\":{\"syntax\":\"UBL\",\"sha256\":\""
                        + digest + "\"}")).source().orElseThrow());
        assertEquals(SemanticDocument.Source.ofSyntax("CII"),
                reader.read(envelope("\"values\":{},\"source\":{\"syntax\":\"CII\"}"))
                        .source().orElseThrow());
        assertEquals(SemanticDocument.Source.ofDigest(digest),
                reader.read(envelope("\"values\":{},\"source\":{\"sha256\":\"" + digest + "\"}"))
                        .source().orElseThrow());
    }

    @Test
    void theDocumentSizeLimitIsEnforcedOnBytesAndOnStreams() {
        Limits tiny = Limits.builder().maxDocumentBytes(10).build();
        byte[] document = oneValue("/BT-1", "\"RE-1\"");
        assertThrows(EsjLimitException.class, () -> EsjReader.withLimits(tiny).read(document));
        assertThrows(EsjLimitException.class, () -> EsjReader.withLimits(tiny)
                .read(new ByteArrayInputStream(document)));
    }

    @Test
    void theNumberOfValuesIsLimited() {
        Limits one = Limits.builder().maxValues(1).build();
        byte[] document = envelope("\"values\":{"
                + "\"/BT-1\":\"a\","
                + "\"/BT-2\":{\"type\":\"date\",\"value\":\"2026-01-15\"}}");
        EsjLimitException thrown = assertThrows(EsjLimitException.class,
                () -> EsjReader.withLimits(one).read(document));
        assertEquals("ESJ-L1-LIMIT", thrown.code().code());
    }

    @Test
    void thePathLengthAndTheNumberOfSegmentsAreLimited() {
        byte[] document = oneValue("/BG-25/0/BG-31/BT-158/0",
                "{\"value\":\"a\",\"scheme\":\"s\"}");
        assertThrows(EsjLimitException.class,
                () -> EsjReader.withLimits(Limits.builder().maxPathBytes(8).build()).read(document));
        assertThrows(EsjLimitException.class, () -> EsjReader
                .withLimits(Limits.builder().maxPathSegments(2).build()).read(document));
    }

    @Test
    void theLengthOfAStringValueIsLimitedInUtf8Bytes() {
        Limits three = Limits.builder().maxStringBytes(3).build();
        byte[] ascii = oneValue("/BG-4/BT-27", "\"abc\"");
        byte[] wide = oneValue("/BG-4/BT-27", "\"中中\"");
        assertEquals(1, EsjReader.withLimits(three).read(ascii).values().size());
        assertThrows(EsjLimitException.class, () -> EsjReader.withLimits(three).read(wide));
    }

    @Test
    void theDecodedBinaryContentOfADocumentIsLimited() {
        Limits small = Limits.builder().maxTotalBinaryBytes(3).build();
        byte[] document = oneValue("/BG-24/0/BT-125",
                "{\"value\":\"QUJDRQ==\",\"mimeCode\":\"application/pdf\","
                        + "\"filename\":\"a.pdf\"}");
        assertThrows(EsjLimitException.class, () -> EsjReader.withLimits(small).read(document));
    }

    @Test
    void theNestingInsideExtensionsIsLimited() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            deep.append("[");
        }
        deep.append("1");
        for (int i = 0; i < 40; i++) {
            deep.append("]");
        }
        byte[] document = envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":" + deep + "}");
        assertThrows(EsjLimitException.class, () -> reader.read(document));
        assertEquals(1, EsjReader.withLimits(Limits.builder().maxExtensionDepth(40).build())
                .read(document).extensions().size());
    }

    @Test
    void aLoneSurrogateInAMemberNameIsASurrogateFindingAndNotAJsonOne() {
        assertEquals("ESJ-L1-SURROGATE", codeOf(oneValue("/BT-1\\ud800",
                "\"RE-1\"")));
        assertEquals("ESJ-L1-SURROGATE", codeOf(oneValue("/BT-1\\udc00",
                "\"RE-1\"")));
        assertEquals("ESJ-L1-SURROGATE", codeOf(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":{\"\\ud800\":\"x\"}}")));
        assertEquals("ESJ-L1-SURROGATE", codeOf(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":{\"\\udc00\":\"x\"}}")));
    }

    @Test
    void aPairedSurrogateInAMemberNameIsNoDefect() {
        SemanticDocument document = reader.read(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":"
                        + "{\"\\ud83d\\udcb6\":\"x\"}}"));
        assertEquals(1, document.extensions().size());
    }

    /**
     * A message is a log line and is bounded; the location is a field a program reads and
     * is whole (specification, sections 9.5 and 12.6).
     */
    @Test
    void aMessageDoesNotReproduceMoreThanAnExcerptOfTheDocument() {
        String huge = "x".repeat(200_000);
        EsjFormatException member = assertThrows(EsjFormatException.class, () -> reader.read(
                oneValue("/BT-1", "{\"" + huge + "\":\"y\",\"value\":\"a\","
                        + "\"scheme\":\"0088\"}")));

        assertEquals("ESJ-L1-VALUE-MEMBER", member.code().orElseThrow().code());
        assertTrue(member.getMessage().length() < 700,
                "message of " + member.getMessage().length());
        assertTrue(member.location().orElseThrow().contains(huge),
                "the location carries the member name whole");
    }

    /**
     * Two members alike in their first characters are two findings a program can tell
     * apart, which is what {@code subject} is for (specification, section 9.5). An
     * abbreviated subject made them one.
     */
    @Test
    void twoLongMemberNamesAlikeAtTheStartGiveTwoDistinctSubjects() {
        String stem = "/BG-25/0/BG-29/bt-" + "1".repeat(100);
        ReadResult result = reader.readWithFindings(envelope(
                "\"values\":{\"" + stem + "a\":\"1\",\"" + stem + "b\":\"2\"}"));

        List<String> subjects = result.validation().findings().stream()
                .filter(finding -> finding.code() == FindingCode.ESJ_L1_PATH_SYNTAX)
                .map(Finding::subject)
                .toList();
        assertEquals(2, subjects.size());
        assertNotEquals(subjects.get(0), subjects.get(1));
        assertTrue(subjects.get(0).contains(stem + "a"), subjects.get(0));
        assertTrue(subjects.get(1).contains(stem + "b"), subjects.get(1));
    }

    @Test
    void aTokenLongerThanTheStringBoundIsRefusedByTheParserAsALimit() {
        Limits small = Limits.builder().maxStringBytes(1024).build();
        String huge = "x".repeat(4096);
        assertThrows(EsjLimitException.class, () -> EsjReader.withLimits(small)
                .read(oneValue("/BT-1", "\"" + huge + "\"")));
        assertThrows(EsjLimitException.class, () -> EsjReader.withLimits(small).read(envelope(
                "\"values\":{},\"extensions\":{\"de.example.vendor\":1" + "0".repeat(4096) + "}")));
    }

    /**
     * A member name longer than what the parser assembles at all is refused before the
     * reader holds a character of it, so there is no name to write into {@code subject}
     * and the finding names the place by the byte offset the reader stopped at
     * (specification, section 9.5). Without it the finding named nowhere and sent its
     * reader through the whole file, which is the size a hostile document picks.
     */
    @Test
    void aMemberNameTooLongForTheParserIsLocatedByAByteOffset() {
        Limits small = Limits.builder().maxStringBytes(1024).build();
        byte[] document = envelope("\"values\":{\"" + "x".repeat(4096) + "\":\"1\"}");

        ReadResult result = EsjReader.withLimits(small).readWithFindings(document);

        Finding limit = result.findings().get(0);
        assertEquals(FindingCode.ESJ_L1_LIMIT, limit.code());
        assertEquals("", limit.subject(), "the parser never handed the name over");
        Matcher offset = Pattern.compile("stopped at byte (\\d+) of the document")
                .matcher(limit.message());
        assertTrue(offset.find(), limit.message());
        long at = Long.parseLong(offset.group(1));
        assertTrue(at > 1024 && at < document.length,
                "the offset is inside the name the parser refused: " + at);
    }

    @Test
    void readWithFindingsReportsALimitInsteadOfThrowing() {
        Limits two = Limits.builder().maxValues(2).build();
        byte[] document = envelope("""
                "values":{\
                "/BT-1":"RE-1",\
                "/BT-2":"2026-01-15",\
                "/BT-3":"380"}""");
        ReadResult result = EsjReader.withLimits(two).readWithFindings(document);

        assertTrue(result.document().isEmpty());
        assertFalse(result.isWellFormed());
        assertEquals(List.of("ESJ-L1-LIMIT"),
                result.findings().stream().map(finding -> finding.code().code()).toList());
        assertThrows(EsjLimitException.class, () -> EsjReader.withLimits(two).read(document));
    }

    @Test
    void readWithFindingsReportsALimitMetWhileTheStreamIsRead() {
        Limits tiny = Limits.builder().maxDocumentBytes(16).build();
        ReadResult result = EsjReader.withLimits(tiny).readWithFindings(
                new ByteArrayInputStream(oneValue("/BT-1",
                        "\"RE-1\"")));

        assertTrue(result.document().isEmpty());
        assertEquals(List.of("ESJ-L1-LIMIT"),
                result.findings().stream().map(finding -> finding.code().code()).toList());
    }

    @Test
    void readWithFindingsCollectsEveryDefectThatIsLocalToOneValue() {
        byte[] document = envelope("""
                "values":{\
                "/BT-1":"RE-1",\
                "/BT-2":{"value":"2026-01-15"},\
                "/BG-22/BT-106":{"value":"100","scheme":"s","currency":"EUR"},\
                "/BG-4/BT-27":"",\
                "/BT-999x":"a"}""");

        ReadResult result = reader.readWithFindings(document);

        assertFalse(result.isWellFormed());
        assertEquals(List.of("ESJ-L1-EMPTY-STRING", "ESJ-L1-PATH-SYNTAX",
                        "ESJ-L1-VALUE-MEMBER", "ESJ-L1-VALUE-SHAPE"),
                result.findings().stream().map(finding -> finding.code().code()).sorted().toList());
        assertEquals(1, result.orElseThrow().values().size());
    }

    @Test
    void readWithFindingsStopsWhereParsingCannotContinue() {
        ReadResult result = reader.readWithFindings(
                "{\"format\":\"ESJ\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(result.document().isEmpty());
        assertFalse(result.isWellFormed());
        assertEquals("ESJ-L1-ENVELOPE-VALUE", result.findings().get(0).code().code());
    }

    @Test
    void readWithFindingsReportsNothingForASoundDocument() {
        ReadResult result = reader.readWithFindings(
                oneValue("/BT-1", "\"RE-1\""));
        assertTrue(result.isWellFormed());
        assertEquals(List.<Finding>of(), result.findings());
    }

    @Test
    void aFindingCarriesThePathItIsAbout() {
        ReadResult result = reader.readWithFindings(oneValue("/BT-2", "{\"value\":\"a\"}"));

        assertEquals(SemanticPath.of("/BT-2"), result.findings().get(0).path());
    }

    @Test
    void anExceptionCarriesItsCodeAndTheLocationOfTheProblem() {
        EsjFormatException thrown = assertThrows(EsjFormatException.class, () -> reader.read(
                oneValue("/BG-4/BT-27",
                        "{\"value\":\"a\",\"scheme\":\"s\",\"currency\":\"EUR\"}")));

        assertEquals("ESJ-L1-VALUE-MEMBER", thrown.code().orElseThrow().code());
        assertEquals("values[\"/BG-4/BT-27\"].currency", thrown.location().orElseThrow());
        assertTrue(thrown.getMessage().contains("(at values[\"/BG-4/BT-27\"].currency)"));
    }

    /**
     * A mantissa longer than any constant the exponent could be clamped to. The canonical
     * form of the token is a nine-million-character decimal, so the specification,
     * section 7.6 rule 2 refuses it; an implementation that clamps or overflows the
     * exponent to a constant the mantissa can exceed computes the canonical form of a
     * different number and accepts it.
     */
    @Test
    @Timeout(value = 60)
    void aNumberInsideExtensionsIsJudgedByItsWholeExponent() {
        for (String exponent : List.of("e-10000000", "e10000000")) {
            String token = "1" + "0".repeat(999_999) + exponent;
            byte[] document = envelope("\"values\":{},\"extensions\":{\"de.example\":{\"n\":"
                    + token + "}}");
            assertEquals("ESJ-L1-EXT-NUMBER", codeOf(document), exponent);
        }
    }

    /**
     * The exponents that an accumulator overflowing a signed long reads as a small
     * number. Each of these tokens has a canonical decimal form of more than nine
     * quintillion characters, so section 7.6, rule 2 refuses it; a reader that wraps
     * instead accepts the document and takes both digests over digits nobody wrote, and
     * one that computes a garbage magnitude from the wrapped value allocates for it.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "1e106616204000961326445756421",
            "1e-106616204000961326445756421",
            "1e110680464442257309696",
            "-5e110680464442257309696",
            "1e9223372036854775808",
            "1e-9223372036854775809",
            "1e9999999999999999999"})
    @Timeout(value = 60)
    void anExponentThatWouldOverflowAnAccumulatorIsStillJudgedWhole(String token) {
        byte[] document = envelope("\"values\":{},\"extensions\":{\"de.example\":{\"n\":"
                + token + "}}");
        assertEquals("ESJ-L1-EXT-NUMBER", codeOf(document), token);
    }

    @Test
    @Timeout(value = 60)
    void aNumberTokenLongerThanTheStringBoundIsALimit() {
        long bound = Limits.defaults().maxStringBytes();
        String token = "1." + "0".repeat((int) bound);
        byte[] document = envelope("\"values\":{},\"extensions\":{\"de.example\":{\"n\":"
                + token + "}}");
        EsjLimitException thrown =
                assertThrows(EsjLimitException.class, () -> reader.read(document));
        assertEquals("ESJ-L1-LIMIT", thrown.code().code());
    }

    @Test
    void aNumberTokenAtTheStringBoundIsStillRead() {
        long bound = Limits.defaults().maxStringBytes();
        String token = "1." + "0".repeat((int) bound - 3) + "5";
        byte[] document = envelope("\"values\":{},\"extensions\":{\"de.example\":{\"n\":"
                + token + "}}");
        assertEquals((int) bound, token.length());
        assertEquals("ESJ-L1-EXT-NUMBER", codeOf(document));
    }

    @Test
    void aSourceSyntaxLongerThanTheStringBoundIsALimit() {
        EsjReader small = EsjReader.withLimits(Limits.builder().maxStringBytes(8).build());
        byte[] document = envelope("\"values\":{},\"source\":{\"syntax\":\"UBL-and-then-some\"}");
        EsjLimitException thrown =
                assertThrows(EsjLimitException.class, () -> small.read(document));
        assertEquals("ESJ-L1-LIMIT", thrown.code().code());
        assertTrue(thrown.getMessage().contains("source.syntax"));
    }

    @Test
    void extensionsWithMoreNodesThanTheBoundAllowsIsALimit() {
        EsjReader small = EsjReader.withLimits(Limits.builder().maxExtensionNodes(4).build());
        byte[] document = envelope(
                "\"values\":{},\"extensions\":{\"de.example\":[1,2,3,4,5]}");
        EsjLimitException thrown =
                assertThrows(EsjLimitException.class, () -> small.read(document));
        assertEquals("ESJ-L1-LIMIT", thrown.code().code());
        assertTrue(thrown.getMessage().contains("nodes"));
    }

    @Test
    void extensionsAtTheNodeBoundIsStillRead() {
        EsjReader small = EsjReader.withLimits(Limits.builder().maxExtensionNodes(6).build());
        byte[] document = envelope(
                "\"values\":{},\"extensions\":{\"de.example\":[1,2,3,4,5]}");
        assertEquals(1, small.read(document).extensions().size());
    }

    /**
     * A tree deeper than any stack would carry as recursion. The reader walks
     * {@code extensions} iteratively, so a bound a caller raised is answered with a
     * document and not with a {@link StackOverflowError}.
     */
    @Test
    @Timeout(value = 60)
    void aDeepExtensionTreeIsReadWithoutTheStack() {
        int depth = 20_000;
        EsjReader deep = EsjReader.withLimits(Limits.builder()
                .maxExtensionDepth(depth + 1)
                .maxExtensionNodes(depth + 1)
                .build());
        byte[] document = envelope("\"values\":{},\"extensions\":{\"de.example\":"
                + "[".repeat(depth) + "]".repeat(depth) + "}");
        assertEquals(1, deep.read(document).extensions().size());
    }

    /**
     * The location of a problem inside {@code extensions} is a chain of member names, and
     * a member name is document content. The copy of it a <em>message</em> carries is held
     * to a bound, so that a document cannot decide how long a log line is (specification,
     * section 12.6); the location itself is whole, because it is the field a program reads
     * (section 9.5).
     */
    @Test
    void aLocationInsideExtensionsIsExcerptedInTheMessageAndWholeInTheField() {
        String name = "n".repeat(4000);
        StringBuilder tree = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            tree.append("{\"").append(name).append("\":");
        }
        tree.append("1").append("}".repeat(30));
        byte[] document =
                envelope("\"values\":{},\"extensions\":{\"de.example\":" + tree + "}");
        EsjReader small = EsjReader.withLimits(Limits.builder().maxExtensionDepth(20).build());
        EsjLimitException thrown =
                assertThrows(EsjLimitException.class, () -> small.read(document));
        assertTrue(thrown.getMessage().length() < 1024,
                "message length " + thrown.getMessage().length());
        String location = thrown.location().orElseThrow();
        assertFalse(location.endsWith("..."), "the location is not cut");
        assertTrue(location.length() > 20 * name.length(), "location length " + location.length());
    }

    @Test
    void aSyntaxErrorBeforeALoneSurrogateIsNotASurrogate() {
        byte[] document = ("{\"format\":\"EN16931-Semantic-JSON\",,\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":"
                + "{\"/BT-1\":\"\\ud800\"}}")
                .getBytes(StandardCharsets.UTF_8);

        assertEquals("ESJ-L1-JSON", codeOf(document));
    }

    @Test
    void oneLoneSurrogateProducesOneSurrogateFinding() {
        byte[] document = (HEAD + "\"values\":{\"/BT-1\":\"\\ud800\"},}")
                .getBytes(StandardCharsets.UTF_8);

        ReadResult result = reader.readWithFindings(document);
        assertEquals(List.of("ESJ-L1-SURROGATE", "ESJ-L1-JSON"),
                result.findings().stream().map(finding -> finding.code().code()).toList());
    }

    @Test
    void everyExampleOfTheRepositoryIsRead() {
        for (String name : Examples.NAMES) {
            SemanticDocument document = reader.read(Examples.pretty(name));
            assertFalse(document.values().isEmpty(), name);
            assertEquals(document, reader.read(Examples.canonical(name)), name);
        }
    }

    @Test
    void theExtendedExampleCarriesTheExtensionsAndTheProvenanceItPromises() {
        SemanticDocument document = reader.read(Examples.pretty("extended"));
        assertEquals(2, document.extensions().size());
        assertEquals(List.of("de.example.archive", "de.example.vendor"),
                List.copyOf(document.extensions().keySet()));
        assertEquals("UBL", document.source().orElseThrow().syntax().orElseThrow());
        Map<String, ExtensionValue> vendor =
                ((ExtensionValue.ObjectValue) document.extensions().get("de.example.vendor"))
                        .members();
        assertEquals(new BigDecimal("1000000000000000000000"),
                ((ExtensionValue.NumberValue) ((ExtensionValue.ObjectValue) vendor.get("numbers"))
                        .members().get("exponentPositive")).value());
    }
}
