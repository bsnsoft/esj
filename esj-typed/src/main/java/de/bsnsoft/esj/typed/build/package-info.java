/**
 * The constrained builder: a chain of step interfaces, generated from the term registry,
 * on which the terminal step is reachable only once every member the model declares
 * mandatory has been written.
 *
 * <p>It is a layer over the typed editor of
 * {@code de.bsnsoft.esj.typed} and writes through it; everything it does is
 * expressible with the editor alone. What it adds is that the compiler checks the
 * structure: the mandatory members of the document and of every business group, their
 * cardinalities, the group each member belongs to, and its semantic data type.
 *
 * <p>What it does not add is judgement about content. Business rules, arithmetic, code
 * list membership, VAT logic and the rules of a profile beyond its cardinalities are the
 * validator's, and the terminal step is where a caller asks for them:
 * {@code validate()} reports, {@code validateOrThrow()} refuses, {@code build()} derives
 * the amounts, refuses and returns the document.
 *
 * <pre>{@code
 * SemanticDocument document = InvoiceBuilder.create(Profile.EN16931)
 *     .invoiceNumber("RE-2026-0211")
 *     .issueDate(LocalDate.of(2026, 5, 12))
 *     .typeCode("380")
 *     .currencyCode("EUR")
 *     .seller(s -> s.name("Example GmbH").postalAddress(a -> a.countryCode("DE")))
 *     .buyer(b -> b.name("Muster AG").postalAddress(a -> a.countryCode("DE")))
 *     .invoiceLine(l -> l.identifier("1")
 *         .quantity(new BigDecimal("100"), "H87")
 *         .price(p -> p.netPrice(new BigDecimal("12")))
 *         .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19")))
 *         .item(i -> i.name("Sensor module SM-100")))
 *     .build();
 * }</pre>
 */
package de.bsnsoft.esj.typed.build;
