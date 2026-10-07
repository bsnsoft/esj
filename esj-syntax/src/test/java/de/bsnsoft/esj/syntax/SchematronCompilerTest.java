package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.xr.internal.XmlFrontDoor;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.streams.Steps;
import net.sf.saxon.trans.XPathException;
import org.junit.jupiter.api.Test;

/**
 * The Schematron compilation of {@code esj packs fetch}, with the bundled skeleton, over a
 * schema written for this test.
 */
class SchematronCompilerTest {

    private static final String SVRL = "http://purl.oclc.org/dsdl/svrl";

    @Test
    void compilesASchemaIntoAStylesheetThatReportsInSvrl() throws Exception {
        byte[] xslt = SchematronCompiler.compile(ExamplePacks.schematron(), "example-ubl.sch");

        assertEquals(List.of(), failed(xslt, ExamplePacks.invoice(ExamplePacks.PROFILE)),
                "the corpus invoice meets every rule of the example");
        String dollars = new String(ExamplePacks.invoice(ExamplePacks.PROFILE),
                StandardCharsets.UTF_8)
                .replace(">EUR</cbc:DocumentCurrencyCode>", ">USD</cbc:DocumentCurrencyCode>");
        assertEquals(List.of("EXAMPLE-02 warning"),
                failed(xslt, dollars.getBytes(StandardCharsets.UTF_8)),
                "a variable of the schema reaches the rule, with the flag the schema set");
    }

    @Test
    void writesTheSameBytesForTheSameSchema() {
        assertArrayEquals(
                SchematronCompiler.compile(ExamplePacks.schematron(), "example-ubl.sch"),
                SchematronCompiler.compile(ExamplePacks.schematron(), "example-ubl.sch"));
    }

    @Test
    void refusesASchemaThatIncludesAnotherFile() {
        PackException refused = assertThrows(PackException.class,
                () -> SchematronCompiler.compile(Corpus.bytes(
                        "/de/bsnsoft/esj/syntax/schematron/includes-another.sch"),
                        "includes-another.sch"));
        assertTrue(refused.getMessage().contains("includes-another.sch"),
                refused.getMessage());
    }

    @Test
    void refusesASchemaWithADocumentTypeDeclaration() {
        byte[] schema = ("<?xml version=\"1.0\"?><!DOCTYPE schema [<!ENTITY x \"y\">]>"
                + "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\"/>")
                .getBytes(StandardCharsets.UTF_8);

        PackException refused = assertThrows(PackException.class,
                () -> SchematronCompiler.compile(schema, "doctype.sch"));
        assertTrue(refused.getMessage().contains("not well-formed XML this project accepts"),
                refused.getMessage());
    }

    @Test
    void refusesAStylesheetTheEngineCannotRun() {
        assertThrows(PackException.class, () -> SchematronCompiler.check(
                "<not-a-stylesheet/>".getBytes(StandardCharsets.UTF_8), "broken.xslt"));
    }

    /** Runs a compiled stylesheet over a document and returns its failed assertions. */
    private static List<String> failed(byte[] xslt, byte[] document) throws Exception {
        XsltCompiler compiler = XmlFrontDoor.processor().newXsltCompiler();
        compiler.setResourceResolver(request -> {
            throw new XPathException("no " + request.uri);
        });
        XsltExecutable executable = compiler.compile(
                new StreamSource(new ByteArrayInputStream(xslt), "esj-test:/compiled.xslt"));
        XdmNode input = XmlFrontDoor.parse(document);
        Xslt30Transformer transformer = executable.load30();
        transformer.setGlobalContextItem(input);
        XdmDestination result = new XdmDestination();
        transformer.applyTemplates(input, result);
        List<String> failed = new ArrayList<>();
        for (XdmNode assertion : result.getXdmNode()
                .select(Steps.descendant(SVRL, "failed-assert")).toList()) {
            failed.add(assertion.attribute("id") + " " + assertion.attribute("flag"));
        }
        return failed;
    }
}
