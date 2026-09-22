/**
 * The semantic rule engine: business rules evaluated over semantic paths.
 *
 * <p>A rule of this package reads {@code /BG-25/*}{@code /BT-131} and never a UBL or a CII
 * XPath. That is the whole reason it exists. The business rules of EN 16931 are statements
 * about business terms, and the validation artefacts that check them are written once per
 * syntax, so the same arithmetic is expressed twice and a document that was never XML has no
 * artefact at all. Written over the semantic model, one rule serves a UBL invoice, a CII
 * invoice and a document written directly in this format alike, and a finding lands on the
 * business term the caller wrote the value at rather than on an element of a syntax the
 * caller may never have seen.
 *
 * <p>It does not replace the syntax engine and is not meant to. Semantic validity does not
 * prove that a document is correctly bound to its syntax, and
 * {@code docs/validation.md} says why both keep running over an XML input. Nor is this an
 * authority on the rules: the artefacts of CEN/TC 434 and of the core invoice usage
 * specifications are, a pack names the release it was verified against, and no claim of
 * agreement is made anywhere in this repository without the ledger that measured it.
 *
 * <h2>Where to start</h2>
 *
 * <p>{@link de.bsnsoft.esj.rules.RulePacks} reads a rule file into a
 * {@link de.bsnsoft.esj.rules.RulePack}.
 * {@link de.bsnsoft.esj.rules.RuleEngine#compile} resolves it against a registry
 * and answers with an engine; {@link de.bsnsoft.esj.rules.RuleEngine#evaluate}
 * runs it over a document and answers with
 * {@link de.bsnsoft.esj.rules.RuleFinding}s.
 * {@link de.bsnsoft.esj.rules.JavaRule} is the rule that the rule language cannot
 * express, and {@link de.bsnsoft.esj.rules.RuleContext} is what it reads the
 * invoice through. {@link de.bsnsoft.esj.rules.CodeList} is a dated snapshot of a
 * published code list, which is how a membership test keeps its answer over the years.
 *
 * <p>The rule language itself — the operators, what each of them means and what each of them
 * does with a value that is not there — is described in {@code rules/README.md} at the root
 * of the repository, and its schema is {@code rules/rule.schema.json} beside it.
 *
 * <h2>What a finding of this package is not</h2>
 *
 * <p>It is not a statement about conformance to the ESJ format. Conformance is defined by
 * layers L1 to L3 of {@code SPEC.md} section 9 and by nothing else; section 9.4 requires an
 * implementation that checks business rules to report them as a separate layer and forbids
 * presenting them as ESJ conformance. Every finding of this package carries its category, its
 * pack and the engine name {@code native} so that a report can keep them apart.
 */
package de.bsnsoft.esj.rules;
