# Related work

*Part of [EN16931 Semantic JSON](../README.md).*

If you already use one of the projects below, this list shows where ESJ sits next to it.
Several of them give UBL and EN 16931 invoices a JSON form or a programming model. ESJ uses the
EN 16931 semantic identifiers themselves as the canonical address space, independent of either
XML binding. This project uses or measures itself against three of them. Every entry was read
before it was written down; a licence or a figure belongs to the project that states it.

- [AusDigital UBL JSON Syntax 1.0](https://ausdigital.org/specs/ausdigital-syn-json/1.0/):
  a 2016 draft of the Australian Digital Business Council. It defines "a JSON based syntax and
  processing model for UBL semantics", with lossless transformation between UBL 2.1 XML and
  JSON. Member names follow the UBL element names without their prefixes. The document
  currency is declared once instead of on every amount, as in ESJ. Its addresses are UBL's, so
  the same invoice in CII does not land on them; ESJ's addresses are the business terms.
- [OASIS UBL 2.1 JSON Alternative Representation](https://docs.oasis-open.org/ubl/UBL-2.1-JSON/v1.0/UBL-2.1-JSON-v1.0.html):
  a 2017 Committee Note Draft. It supplements UBL 2.1 with the sample documents in JSON and a
  JSON schema for each of the 65 XSD schemas. It serializes the UBL document tree a second time;
  ESJ serializes the semantic model that UBL and CII both bind.
- [@attestwire/en16931](https://www.npmjs.com/package/@attestwire/en16931): a TypeScript
  library (MIT). It generates and parses EN 16931 invoices in UBL 2.1 and CII D16B from one
  input model, extracts the embedded XML from Factur-X and ZUGFeRD PDFs, and checks business
  rules. It is a domain model inside one programming language. ESJ is a wire format with a
  canonical byte sequence and digests that compare across implementations.
- [B2Brouter's mapping guides](https://docs.b2brouter.net/en/developers/mapping-guides/json-invoice-fields-to-ubl-xml-en16931-peppol-bis-3/)
  and [InvoiceXML's ExtractJson](https://www.invoicexml.com/docs/api/extract/json): hosted
  services, each with a JSON invoice model of its own. B2Brouter documents field by field how
  its API maps to EN 16931 business terms and to UBL or CII XPaths. InvoiceXML parses a CII or
  UBL file, or the XML embedded in a PDF, into a JSON object whose field names follow the
  standard. Both are products with their own names and their own servers. ESJ is a
  specification whose names are the standard's, and you need no service to use it.
- [KoSIT SeMoX](https://projekte.kosit.org/xrechnung/xrechnung-model-semox): the
  machine-readable semantic model of XRechnung, written in SeMoX ("Simple Semantic Data
  Modeling in XML"). It holds business terms, data types, rules and code lists in one XML
  model. It is the closest relative of this project's registry. SeMoX models the standard,
  not an instance; ESJ adds the document format that carries the values.
- [schema.org/Invoice](https://schema.org/Invoice): a vocabulary type for a bill on the web,
  usually encoded as JSON-LD, with about 15 properties such as `totalPaymentDue`, `customer`
  and `provider`. It is semantic, but far coarser than EN 16931: no VAT breakdown, no
  line-level tax category, no cardinality model.
- [Mustangproject](https://www.mustangproject.org/): a Java library that reads, writes and
  validates ZUGFeRD and Factur-X invoices, that is, PDF plus embedded CII. ESJ reads the
  attachment out of such a container and writes one (`esj embed`, `esj render --embed cii`),
  and there the two touch. ESJ's document is the semantic model the attachment carries. This
  project cross-checked the facts about
  the container formats it relies on against Mustangproject; see [`pdf-input.md`](pdf-input.md).
- [ph-ubl](https://github.com/phax/ph-ubl) and
  [en16931-cii2ubl](https://github.com/phax/en16931-cii2ubl) by Philip Helger: mature Java
  bindings for the UBL and CII schemas and a converter between the two syntaxes. ESJ works one
  level up. It names the semantic model itself instead of mapping syntax to syntax.
- [KoSIT validator](https://github.com/itplr-kosit/validator),
  [xrechnung-visualization](https://github.com/itplr-kosit/xrechnung-visualization) and
  [xrechnung-testsuite](https://github.com/itplr-kosit/xrechnung-testsuite): the reference
  validation, rendering and test material for XRechnung. This project uses the second and the
  third and measures itself against the first.
  `esj-xr` reads UBL and CII through the visualization stylesheets, and `esj-render` renders
  the HTML page through them, vendored unmodified under their Apache-2.0 licence. The test
  suite is the conformance corpus. The validator is the oracle for this project's syntax
  engine. Both ran the 86 corpus instances and 63 documents broken on purpose, and they agree
  on every rule identifier and every flag of all 149.
  [`conformance/syntax/ledger.md`](../conformance/syntax/ledger.md) has the figures and the 3
  documents where the verdicts differ, with the reason. The rule engine of this project checks
  an ESJ document that was never XML. It is a layer beside them and is measured against the
  same artefacts: [`conformance/rules/ledger.md`](../conformance/rules/ledger.md).
