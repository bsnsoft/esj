# Why this exists

*Part of [EN16931 Semantic JSON](../README.md).*

EN 16931 fixed the semantics of an invoice. Your application still reaches them through two
transport syntaxes, and it builds a model of its own on the way:

```text
standard -> two bindings -> parser -> mapping -> internal model -> database -> API
```

ESJ takes the parser, the mapping and the internal model out of that chain. The business terms
are the addresses. The same code reads an invoice whichever syntax it arrived in, and the corpus
measures that ([`conformance.md`](conformance.md)). You write the layer between parser and
database once, as ESJ ([`storage.md`](storage.md)).

**The everyday tools are missing.** The public validators tell you whether a file is valid, and
the visualisation renders it. No common command line tool converts, inspects, pulls out one
value, diffs two renderings of one invoice, canonicalizes or hashes. `esj` does all of that
([`cli.md`](cli.md)).

## Designed so that agents can produce and validate invoices without reasoning about XML

An automated agent can write an invoice without anyone reading XML. The registry is
machine-readable, the keys of a document are the standard's own identifiers, and every finding
carries a code. So the agent closes the loop itself: write the values, validate, correct, write
the invoice out. On a document it built itself, it gets the structural answer of layers L1 to L3
and the business rules of the native pack. On an imported XML or PDF, the official validation
artefacts run as well ([`validation.md`](validation.md)).

- A writer for either syntax takes the invoice out. Of the 86 corpus instances written back out,
  the official artefacts accept 85 as a cross industry invoice and 85 as a UBL document. 80 (CII)
  and 86 (UBL) read back as the document they were written from. Each difference is named in
  [`../conformance/writers/cii-roundtrip.md`](../conformance/writers/cii-roundtrip.md) and
  [`ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md).
- The corpus carries 40 business cases twice. Taken through the core into the other syntax, they
  agree with the file already there wherever the two files agree with each other
  ([`matrix.md`](../conformance/writers/matrix.md)).
