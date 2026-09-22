/**
 * The domain API: an invoice written and read in the words of the domain rather than in
 * the identifiers of EN 16931-1.
 *
 * <p>It is the fourth of four layers over one registry, and the only one written by hand:
 *
 * <ul>
 *   <li>{@code SemanticDocument} of {@code esj-core} — free, path based.</li>
 *   <li>{@code InvoiceEditor} of {@code esj-typed} — one accessor and one setter per
 *       business term, generated, complete.</li>
 *   <li>{@code InvoiceBuilder} of {@code esj-typed.build} — the same, with the structure
 *       of the model checked by the compiler, generated.</li>
 *   <li>{@link de.bsnsoft.esj.invoice.Invoice} — enums instead of codes,
 *       {@link de.bsnsoft.esj.invoice.Party},
 *       {@link de.bsnsoft.esj.invoice.Line} and
 *       {@link de.bsnsoft.esj.invoice.Vat} instead of groups, defaults instead
 *       of boilerplate, and {@code build()} instead of typing the totals.</li>
 * </ul>
 *
 * <p>Each layer is usable on its own and every effect of this one is expressible through
 * the one below it; {@link de.bsnsoft.esj.invoice.Draft#edit} hands the editor
 * out for the terms this layer has no word for. Nothing here decides whether a code is
 * admissible or whether a business rule holds: that is the validator's, and
 * {@link de.bsnsoft.esj.invoice.Rules} is how a build asks it.
 *
 * <p>Decimals follow the typing of the standard. Unit prices, quantities and percentages
 * are of unlimited scale and are passed through exactly as the caller wrote them; an
 * amount this layer computes — the amount a percentage comes to — is rounded half up to
 * two decimals once, at the result. The line net amounts, the VAT breakdown and the
 * totals are not computed here at all: they are derived by {@code Totals.STANDARD} when
 * the invoice is built.
 */
package de.bsnsoft.esj.invoice;
