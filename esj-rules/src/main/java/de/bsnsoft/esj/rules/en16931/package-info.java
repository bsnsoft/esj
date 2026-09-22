/**
 * The rules of the EN 16931 pack that the rule language cannot express.
 *
 * <p>Most of the pack is JSON. What is here is what a closed operator set does not reach,
 * and it is three kinds of statement. One asks how many VAT breakdown groups of a given VAT
 * category an invoice has, which is a count over a filtered set and the language has no
 * filter. One adds up the invoice lines, the document level allowances and the document
 * level charges of one VAT category, and for two categories of one rate as well, which is
 * the same filter under a sum. One reads a supplementary component of a value — the
 * identification scheme of an identifier — which no operator of the language addresses,
 * because the language addresses business terms and a scheme is not one.
 *
 * <p>Every class here carries the identifier the standard or the official validation
 * artefacts give the rule, and produces a finding of the same shape a rule of the language
 * produces. A report cannot tell the two apart and should not be able to: which side of that
 * line a rule fell on is an implementation detail of this project and not a fact about the
 * invoice.
 */
package de.bsnsoft.esj.rules.en16931;
