# Legal note (Germany)

*Part of [EN16931 Semantic JSON](../README.md).*

Since 1 January 2025 an electronic invoice under German VAT law must use a structured
electronic format. § 14 Abs. 1 Satz 6 UStG offers two routes: a format conforming to
EN 16931 and to the syntaxes of Directive 2014/55/EU, or a format agreed between issuer and
recipient that allows the correct and complete extraction of the information the UStG
requires into a format conforming to EN 16931 or interoperable with it. Section 14.1
paragraph 15 of the Umsatzsteuer-Anwendungserlass, as amended by the BMF letter of 15 October
2025, defines interoperable for that purpose: the information must be processable from the
original format without loss, and there is a loss when the content or the meaning of an item
changes or is no longer recognisable.

ESJ is built to make that extraction mechanical: values are keyed by EN 16931 business terms,
the registry records the semantic data type of each term, and the canonical form makes the
result of an extraction byte-comparable. Whether a concrete use of ESJ meets those
requirements is for the parties to assess, and an agreement between them is a precondition.
B2G invoicing and the Peppol network are outside the scope of this project.

This page states the legal texts it cites; it is not legal advice.
