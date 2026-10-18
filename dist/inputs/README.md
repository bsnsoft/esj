# Inputs of the packaging smoke test

*Part of [EN16931 Semantic JSON](../../README.md).*

Documents a reader refuses or reads the hard way, for the cases `input-*` of
[`../cases.sh`](../cases.sh). They are not examples of anything a sender should write — the
example invoices are under [`examples/`](../../examples/) — but the inputs a tool meets anyway,
each in a form that takes its own way through the readers:

| File | What it is | What the jar answers |
|---|---|---|
| `truncated.xml` | the UBL invoice cut short after 1500 bytes | exit 2, not well-formed XML |
| `mismatched-tag.xml` | an end tag that does not match its start tag | exit 2, not well-formed XML |
| `undeclared-entity.xml` | a reference to an entity no declaration defines | exit 2, not well-formed XML |
| `control-character.xml` | a control character XML does not allow, in BT-1 | exit 2, not well-formed XML |
| `after-root.xml` | an element after the root element | `validate` INVALID, exit 1, with the parser's sentence; the streaming reader reads the invoice, the XSLT importer refuses it, exit 2 |
| `doctype.xml` | a document type declaration with an internal entity | exit 2, the declaration is refused |
| `unknown-encoding.xml` | declares an encoding no runtime has | exit 2, the encoding is not read |
| `latin1-declared-utf8.xml` | declares UTF-8, written in ISO-8859-1 | `validate` INVALID, exit 1; the other commands repair it, with a warning |
| `cp1252-declared-utf8.xml` | declares UTF-8, written in Windows-1252 | the same, read as Windows-1252; `--strict` refuses it, exit 2 |
| `windows-1252.xml`, `iso-8859-15.xml`, `shift-jis.xml` | declared and written in that encoding, the seller's name in its script | VALID, exit 0 |
| `utf-16.xml` | declared and written in UTF-16, with a byte order mark | VALID, exit 0 |
| `schema-invalid.xml` | an element the UBL schema does not define | INVALID, exit 1, with the schema validator's sentence |
| `datatype-invalid.xml` | an issue date that is no date | INVALID, exit 1, with the schema validator's sentences |
| `truncated.esj.json` | the ESJ example cut short | `validate` INVALID, exit 1; the other commands exit 2 |
| `syntax-error.esj.json` | two commas in a row | INVALID, exit 1 |
| `not-utf8.esj.json` | two bytes that are not UTF-8, in a string | INVALID, exit 1 |
| `truncated.pdf` | the example hybrid invoice cut short | exit 2, not a PDF that can be read |
| `garbage.pdf` | a PDF header and no PDF behind it | exit 2, not a PDF that can be read |
| `attachment-truncated.pdf` | a sound container whose attachment is the CII invoice cut short | exit 2, not well-formed XML; `extract` writes the attachment as it is |
| `attachment-doctype.pdf` | a sound container whose attachment carries a document type declaration | exit 2, the declaration is refused |
| `xmp-malformed.pdf` | a sound container whose XMP packet is cut short | the invoice is read; the container says the packet carries none of the Factur-X properties |
| `empty.xml` | no byte at all | exit 2, the file is empty |

A sound invoice takes the paths through the Java runtime that the other cases of `../cases.sh`
record. These take others: the XML parser of the runtime formats the message of each error it
finds from a resource bundle — `XMLMessages` for a document that is not well-formed,
`XMLSchemaMessages` for one its schema refuses — even where the tool then prints a sentence of
its own, and an encoding other than UTF-8, UTF-16 and ISO-8859-1 is decoded by a charset the
runtime may not carry. A native executable carries a resource bundle only where the GraalVM
agent saw a run load it ([`../native-image.sh`](../native-image.sh)), and a charset only where
it is told to; a runtime image carries the charsets of the modules it was linked with. These
cases are those runs, and [`../smoke.sh`](../smoke.sh) compares what a packaged artefact
answers to each of them with the jar's answer, byte for byte. Without them a native executable
ended on the first document that was not well-formed with an internal error. They also showed
that it refused an invoice in Windows-1252 that the jar reads, and a runtime image one in
Shift_JIS: the native build now carries every charset (`-H:+AddAllCharsets`), and the runtime
image the module `jdk.charsets` ([`../package.sh`](../package.sh)).

The inputs are made by [`Make.java`](Make.java), from the root of the repository:

```console
$ java dist/inputs/Make.java
```

Each one is a document this repository already carries with one change that the source names:
the corpus invoice `01.01a` in UBL and in CII
([`conformance/kosit/`](../../conformance/kosit/README.md), Apache License, Version 2.0), the
example [`standard-invoice.esj.json`](../../examples/standard-invoice.esj.json) and the example
hybrid invoice [`conformance/pdf/factur-x.pdf`](../../conformance/pdf/README.md), whose XMP
packet the PDFs here carry. A character an encoding has no byte for is written as a character
reference, so that the invoice says the same in every encoding it is written in. JDK 17, 21 and
25 wrote the same bytes.
