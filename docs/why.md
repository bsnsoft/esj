# Why this exists

*Part of [EN16931 Semantic JSON](../README.md).*

EN 16931 fixed the semantics of an invoice. Applications still reach them through two transport
syntaxes, and each one builds an internal model of its own on the way:

```text
standard -> two bindings -> parser -> mapping -> internal model -> database -> API
```

Electronic invoicing standardized the semantics, but applications are still forced to work
through transport syntaxes. ESJ closes that gap: the business terms are the addresses, so the
same code reads an invoice whichever syntax it arrived in — a claim the corpus measures
([`conformance.md`](conformance.md)) — and the layer between the parser and the database is one
nobody writes a second time ([`storage.md`](storage.md)).

**The everyday tools are missing.** The public validators say whether a file is valid, and
the visualisation renders it. Converting, inspecting, pulling out one value, diffing two
renderings of one invoice, canonicalizing and hashing: none of that has a common command line
tool. `esj` is that tool ([`cli.md`](cli.md)).

## Designed so that agents can produce and validate invoices without reasoning about XML

The registry is machine-readable, the keys of a document are the standard's own identifiers,
and every finding carries a code. That is a loop an automated agent can close without anyone
reading XML: write the values, validate, correct, write the invoice out. Over a document it
built itself it gets the structural answer of layers L1 to L3 and the business rules of the
native pack; the official validation artefacts run over an imported XML or PDF
([`validation.md`](validation.md)). The way out is a writer for either syntax: of the 86 corpus
instances written back out, 85 as a cross industry invoice and 40 as a UBL document are accepted
by the official artefacts, and 80 and 86 read back as the document they were written from, each
difference named in
[`../conformance/writers/cii-roundtrip.md`](../conformance/writers/cii-roundtrip.md) and
[`ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md). Taken through the core into the
other syntax, the 40 business cases the corpus carries twice agree with the file already there
wherever the two files agree with each other
([`matrix.md`](../conformance/writers/matrix.md)).
