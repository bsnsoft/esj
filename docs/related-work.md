# Related work

*Part of [EN16931 Semantic JSON](../README.md).*

Several projects provide JSON representations or programming models for UBL and EN 16931
invoices, and three of them are material this project uses or measures itself against rather
than only compares itself with. ESJ differs from all of them by using the EN 16931 semantic
identifiers themselves as the canonical address space, independently of either XML binding. Everything below was read before
it was described here; where a project states a licence or a figure, it is that project's own.

- [AusDigital UBL JSON Syntax 1.0](https://ausdigital.org/specs/ausdigital-syn-json/1.0/) —
  a 2016 draft of the Australian Digital Business Council defining "a JSON based syntax and
  processing model for UBL semantics", with lossless transformation between UBL 2.1 XML and
  JSON; member names follow the UBL element names without their prefixes, and the document
  currency is declared once rather than repeated on every amount, as in ESJ. Its addresses
  are UBL's, so the same invoice in CII does not land on them; ESJ's are the business terms.
- [OASIS UBL 2.1 JSON Alternative Representation](https://docs.oasis-open.org/ubl/UBL-2.1-JSON/v1.0/UBL-2.1-JSON-v1.0.html)
  — a 2017 Committee Note Draft that supplements UBL 2.1 with the sample documents expressed
  in JSON and a JSON schema for each of the 65 XSD schemas. It is a second serialization of
  the UBL document tree; ESJ serializes the semantic model that UBL and CII both bind.
- [@attestwire/en16931](https://www.npmjs.com/package/@attestwire/en16931) — a TypeScript
  library (MIT) that generates and parses EN 16931 invoices in both UBL 2.1 and CII D16B from
  one input model, extracts the embedded XML from Factur-X and ZUGFeRD PDFs, and checks
  business rules. It is a domain model inside one programming language; ESJ is a wire format
  with a canonical byte sequence and digests, comparable across implementations.
- [B2Brouter's mapping guides](https://docs.b2brouter.net/en/developers/mapping-guides/json-invoice-fields-to-ubl-xml-en16931-peppol-bis-3/)
  and [InvoiceXML's ExtractJson](https://www.invoicexml.com/docs/api/extract/json) — hosted
  services with a JSON invoice model of their own: the first documents field by field how its
  API maps to EN 16931 business terms and UBL or CII XPaths, the second parses a CII or UBL
  file, or the XML embedded in a PDF, into a JSON object whose field names follow the standard.
  Both are products with their own names and their own servers; ESJ is a specification whose
  names are the standard's and which needs no service.
- [KoSIT SeMoX](https://projekte.kosit.org/xrechnung/xrechnung-model-semox) — the
  machine-readable semantic model of XRechnung, written in SeMoX ("Simple Semantic Data
  Modeling in XML"): business terms, data types, rules and code lists in one XML model. It is
  the closest relative of this project's registry, and it models the standard rather than an
  instance; ESJ adds the document format that carries the values.
- [schema.org/Invoice](https://schema.org/Invoice) — a vocabulary type for describing a bill
  on the web, usually encoded as JSON-LD, with about fifteen properties such as
  `totalPaymentDue`, `customer` and `provider`. Semantic, but far coarser than EN 16931: no
  VAT breakdown, no line-level tax category, no cardinality model.
- [Mustangproject](https://www.mustangproject.org/) — a Java library for reading, writing and
  validating ZUGFeRD and Factur-X invoices, that is, PDF plus embedded CII. ESJ reads the
  attachment out of such a container as of this version, and there the two touch; what
  Mustangproject does beyond that, ESJ does not. It writes containers and it checks the
  business rules, while ESJ produces no transport format at all, says nothing about the
  printed page, and stops at the semantic model the attachment carries. The facts about the
  container formats this project relies on were cross-checked against it; see
  [`pdf-input.md`](pdf-input.md).
- [ph-ubl](https://github.com/phax/ph-ubl) and
  [en16931-cii2ubl](https://github.com/phax/en16931-cii2ubl) by Philip Helger — mature Java
  bindings for the UBL and CII schemas and a converter between the two syntaxes. ESJ works
  one level up: instead of mapping syntax to syntax, it names the semantic model itself and
  leaves the syntax bindings to libraries like these.
- [KoSIT validator](https://github.com/itplr-kosit/validator),
  [xrechnung-visualization](https://github.com/itplr-kosit/xrechnung-visualization) and
  [xrechnung-testsuite](https://github.com/itplr-kosit/xrechnung-testsuite) — the reference
  validation, rendering and test material for XRechnung. Two of the three are related work
  this project uses rather than only compares itself with: `esj-xr` reads UBL and CII through
  the visualization stylesheets and `esj-render` renders the HTML page through them, vendored
  unmodified under their Apache-2.0 licence, and the conformance corpus is the test suite. The
  validator itself is the oracle this project's syntax engine is measured against: the 86
  corpus instances and 63 documents broken on purpose were put through both, and the two agree
  on every rule identifier and every flag of all 149 —
  [`conformance/syntax/ledger.md`](../conformance/syntax/ledger.md) has the figures and the three
  documents where the verdicts differ, with the reason. The rule engine of this project, which
  serves an ESJ document that was never XML, is a layer beside them and is measured against the
  same artefacts — [`conformance/rules/ledger.md`](../conformance/rules/ledger.md).
