package de.bsnsoft.esj.typed.build;

import de.bsnsoft.esj.SemanticDocument;
import java.util.List;

/**
 * The business rules a build runs, supplied by the caller.
 *
 * <p>This module carries no rules. Whether an invoice satisfies the business rules of a
 * standard or of a profile is decided by a versioned rule pack, which lives in
 * {@code esj-rules} and is a layer of its own; the builder takes one as an argument so
 * that a caller who wants the rules checked when the invoice is built says so in one
 * place:
 *
 * <pre>{@code
 * RulePack pack = RulePacks.bundled("en16931", "1.3.16");
 * RuleEngine engine = RuleEngine.compile(pack, Registry.en16931());
 * InvoiceRules rules = document -> engine.evaluate(document).stream()
 *         .map(finding -> new RuleViolation(finding.code(), finding.message(),
 *                 finding.paths(), finding.fatal()))
 *         .toList();
 * }</pre>
 *
 * <p>An adapter reports what the rules said and marks which of it is fatal; the builder
 * refuses on a fatal violation alone.
 */
@FunctionalInterface
public interface InvoiceRules {

    /**
     * Runs the rules over a document.
     *
     * @param document the document to check
     * @return what the rules had to say about it, empty where they had nothing
     */
    List<RuleViolation> check(SemanticDocument document);
}
