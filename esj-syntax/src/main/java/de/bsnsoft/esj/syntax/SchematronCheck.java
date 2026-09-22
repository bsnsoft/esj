package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XmlFrontDoor;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.trans.XPathException;

/**
 * The third block: the document against the compiled Schematron rule sets of the pack.
 *
 * <p>The stylesheets are the ones their publishers released, run as data. Nothing here
 * reads the expressions inside them, and no rule of theirs is restated in Java: the
 * rules are corrected and re-released by the bodies that own them, and a second opinion
 * with its own release cycle would be wrong on the day one of those corrections ships.
 *
 * <p>Each stylesheet is compiled once per process and the compiled form is shared, so a
 * long-lived process pays for it on its first document. What it is shared under is where
 * the file came from and what is in it — {@link Pack#cacheKey(String)} — and never the
 * identity a pack declares about itself, so no supplied pack can answer for a packaged
 * one. The processor is the one of
 * {@link XmlFrontDoor}: no extension function, no {@code xsl:evaluate}, no protocol that
 * may be dereferenced, and a resolver that answers nothing, because these artefacts
 * include no file and must not begin to.
 *
 * <p>What a run produces is a Schematron validation report in the SVRL vocabulary. Every
 * failed assertion and every successful report in it becomes a finding with the rule
 * identifier, the flag, the location and the text the artefact produced.
 *
 * <p>The two ways a stylesheet can fail are not the same thing and are not reported the
 * same way. One that will not <em>compile</em> is a fault of the installation: the pack
 * is not the pack that was reviewed, and no document is to blame, so it is a
 * {@link PackException}. One that raises a dynamic error while <em>running</em> over a
 * document is a property of that document — an amount no arithmetic of the artefact can
 * carry is the plain case — and it becomes a fatal finding of that component under
 * {@link #STOPPED}, which names the rule set as the source of the message and leaves the
 * verdict where it belongs. A rule set that did not finish can never make a document
 * valid.
 */
final class SchematronCheck {

    /** The namespace of the Schematron validation report language. */
    private static final String SVRL = "http://purl.oclc.org/dsdl/svrl";

    /** The code of a finding an artefact made without naming the rule it belongs to. */
    static final String UNIDENTIFIED = "UNIDENTIFIED";

    /**
     * The code of a finding made because a rule set stopped over the document instead of
     * reaching the end of its own report.
     */
    static final String STOPPED = "ARTEFACT-STOPPED";

    private static final Map<String, XsltExecutable> COMPILED = new ConcurrentHashMap<>();

    private SchematronCheck() {
        throw new AssertionError("no instances");
    }

    /**
     * The outcome of one rule set.
     *
     * @param findings    what the rule set said, at the level the profile gives it
     * @param duration    how long the run over the document took
     * @param compilation how long compiling the stylesheet took, zero where it was
     *                    already compiled in this process
     * @param stopped     whether the rule set stopped over the document instead of
     *                    reaching a report of its own
     */
    record Result(List<SyntaxFinding> findings, Duration duration, Duration compilation,
                  boolean stopped) {
    }

    /**
     * Runs one rule set over a document.
     *
     * @param document  the parsed document
     * @param pack      the pack the component belongs to
     * @param component the component
     * @param entry     the stylesheet, as a path inside the pack
     * @param levels    the levels the profile of the document gives rules, empty where it
     *                  gives none and the flags of the artefact stand
     * @param budget    the time the whole validation was given
     * @return the outcome
     * @throws SyntaxLimitException if the run was still going at the deadline
     */
    static Result run(XdmNode document, Pack pack, PackComponent component, String entry,
                      Optional<PackLevels> levels, Budget budget) {
        long compileStart = System.nanoTime();
        boolean[] compiled = {false};
        XsltExecutable executable = COMPILED.computeIfAbsent(pack.cacheKey(entry),
                key -> {
                    compiled[0] = true;
                    return budget.call("compiling the rule set " + entry,
                            () -> compile(pack, entry));
                });
        Duration compilation = compiled[0]
                ? Duration.ofNanos(System.nanoTime() - compileStart) : Duration.ZERO;

        long start = System.nanoTime();
        XdmNode svrl;
        try {
            svrl = budget.call("the rule set " + entry,
                    () -> transform(document, executable));
        } catch (ArtefactStopped stopped) {
            return new Result(List.of(stopped(pack, component, entry, stopped)),
                    Duration.ofNanos(System.nanoTime() - start), compilation, true);
        }
        List<SyntaxFinding> findings = new ArrayList<>();
        collect(svrl, pack, component, levels, findings);
        return new Result(List.copyOf(findings), Duration.ofNanos(System.nanoTime() - start),
                compilation, false);
    }

    /**
     * Turns a rule set that stopped over the document into a finding of that component.
     *
     * <p>It is fatal because the alternative is worse in both directions: a document that
     * made an official rule set stop was not checked by it, and neither a verdict of valid
     * nor a message about a broken installation would say so. The message names the rule
     * set and the pack it belongs to as the source of the words that follow, which are the
     * processor's own.
     */
    private static SyntaxFinding stopped(Pack pack, PackComponent component, String entry,
                                         ArtefactStopped failure) {
        return new SyntaxFinding(Engine.SCHEMATRON,
                Categories.of(STOPPED),
                Severity.FATAL,
                Severity.FATAL,
                STOPPED,
                Text.normalize("the rule set " + entry + " of the pack " + pack.directory()
                        + " stopped over this document and reached no result of its own: "
                        + failure.getMessage()),
                "",
                -1,
                -1,
                pack.id(),
                pack.version(),
                pack.release(),
                component.name());
    }

    private static XsltExecutable compile(Pack pack, String entry) {
        XsltCompiler compiler = XmlFrontDoor.processor().newXsltCompiler();
        compiler.setResourceResolver(SchematronCheck::resolveNothing);
        Source source = new StreamSource(new ByteArrayInputStream(pack.read(entry)),
                pack.uri(entry));
        try {
            return compiler.compile(source);
        } catch (SaxonApiException e) {
            throw new PackException("the rule set " + entry + " of the pack "
                    + pack.directory() + " could not be compiled", e);
        }
    }

    /**
     * Answers no reference at all. The vendored artefacts are single files by
     * construction — the pack carries what the engine executes and nothing beside it — so
     * a reference out of one is a pack that is not the pack that was reviewed.
     */
    private static Source resolveNothing(net.sf.saxon.lib.ResourceRequest request)
            throws XPathException {
        throw new XPathException("a validation artefact of a pack reads no other file, and"
                + " this one asked for " + request.uri);
    }

    private static XdmNode transform(XdmNode document, XsltExecutable executable) {
        try {
            Xslt30Transformer transformer = executable.load30();
            // A compiled rule set declares global variables that read the document — the
            // currency of the invoice, the tolerance of the arithmetic rules — so the
            // document is the global context item as well as the node templates are
            // applied to. Without it those variables raise "the context item is absent"
            // and no rule runs at all.
            transformer.setGlobalContextItem(document);
            transformer.setMessageHandler(message -> {
                // A stylesheet's xsl:message is diagnostic output of a third party; the
                // findings are what it says about the document.
            });
            transformer.setResultDocumentHandler(uri -> {
                throw new IllegalStateException("a validation artefact writes no"
                        + " xsl:result-document");
            });
            XdmDestination result = new XdmDestination();
            transformer.applyTemplates(document, result);
            return result.getXdmNode();
        } catch (SaxonApiException | IllegalStateException e) {
            throw new ArtefactStopped(e);
        }
    }

    /**
     * A rule set that stopped while running over a document.
     *
     * <p>It exists to keep that case apart from {@link PackException}, which is a fault of
     * the installation. It never leaves this class: {@link #run} turns it into a finding.
     */
    private static final class ArtefactStopped extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ArtefactStopped(Throwable cause) {
            super(cause.getMessage() == null ? cause.toString() : cause.getMessage(),
                    cause);
        }
    }

    /** Walks the report and turns every assertion it failed into a finding. */
    private static void collect(XdmNode node, Pack pack, PackComponent component,
                                Optional<PackLevels> levels,
                                List<SyntaxFinding> findings) {
        if (node.getNodeKind() == XdmNodeKind.ELEMENT && SVRL.equals(namespace(node))) {
            String name = node.getNodeName().getLocalName();
            if (name.equals("failed-assert") || name.equals("successful-report")) {
                findings.add(finding(node, pack, component, levels));
                return;
            }
        }
        for (XdmNode child : node.children()) {
            collect(child, pack, component, levels, findings);
        }
    }

    /**
     * Turns one assertion into a finding, at the two levels a finding carries: the flag
     * the artefact set, and the level the profile of the document gives that rule, which
     * is the flag wherever the profile says nothing about it.
     */
    private static SyntaxFinding finding(XdmNode assertion, Pack pack,
                                         PackComponent component,
                                         Optional<PackLevels> levels) {
        String code = attribute(assertion, "id");
        if (code.isEmpty()) {
            code = UNIDENTIFIED;
        }
        Severity flag = Severity.ofFlag(attribute(assertion, "flag"));
        String rule = code;
        Severity severity = levels.flatMap(table -> table.level(rule)).orElse(flag);
        return new SyntaxFinding(Engine.SCHEMATRON,
                Categories.of(code),
                severity,
                flag,
                code,
                text(assertion),
                attribute(assertion, "location"),
                -1,
                -1,
                pack.id(),
                pack.version(),
                pack.release(),
                component.name());
    }

    /** Returns the text the artefact wrote for the assertion, as it wrote it. */
    private static String text(XdmNode assertion) {
        StringBuilder text = new StringBuilder();
        for (XdmNode child : assertion.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT && SVRL.equals(namespace(child))
                    && child.getNodeName().getLocalName().equals("text")) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(child.getStringValue());
            }
        }
        return Text.normalize(text.length() > 0 ? text.toString()
                : assertion.getStringValue());
    }

    private static String attribute(XdmNode element, String name) {
        String value = element.attribute(name);
        return value == null ? "" : Text.normalize(value);
    }

    private static String namespace(XdmNode element) {
        return element.getNodeName().getNamespace();
    }
}
