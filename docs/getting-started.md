# Getting started

*Part of [EN16931 Semantic JSON](../README.md).*

Read an invoice, write one, store it and check it from the shell. A test runs every snippet and
every transcript on this page; each section names its test.

## Build it

The modules are on Maven Central from 0.9.0 ([`java-api.md`](java-api.md)). To build them from
source, you need a JDK 17, 21 or 25 and Maven 3.9 or newer. The compiler targets release 17, so
the result runs on Java 17 and later.

```text
mvn -B verify
```

You get the self-contained `esj-cli/target/esj.jar`. `bin/esj` runs it (`bin\esj.cmd` on
Windows) and finds the jar relative to itself. The transcripts on this page write `esj`: that
script, with `bin/` on the `PATH`. Run `mvn -B install` to put the modules into your local
repository, where a build of your own picks them up. The coordinates and what each module adds
are in [`java-api.md`](java-api.md#the-reference-implementation).

```text
SPEC.md         the format, normative      packs/        the official validation artefacts
model/          the term registry          rules/        this project's semantic rule packs
schema/         JSON Schema 2020-12        conformance/  the KoSIT test suite and the ledgers
examples/       documents with canonical twins
esj-core/       paths, values, reader, writer, canonicalizer, structural validator
esj-typed/      the generated typed view, editor and builder; esj-generator/ generates them
esj-invoice/    the domain API and the code list enums generated from the rule pack
esj-bindings/   the default reader for UBL 2.1 and CII, without a tree, and the two writers
esj-xr/         the second reader, through the vendored KoSIT stylesheets, and the XR export
esj-syntax/     runs the official XSD and Schematron of a profile over an XML input
esj-rules/      evaluates the rule packs over business terms, whatever the input was
esj-pdf/        reads the embedded invoice out of a hybrid PDF, and reports the container
esj-render/     the HTML page and the PDF an invoice is read on
esj-cli/        the esj command line tool; bin/ runs its jar
```

Every directory of material carries a `README.md`: what is in it and where it came from.

## Read an invoice

You get one `SemanticDocument` from an XML invoice and from a hybrid PDF. Nothing after that
knows which syntax it was.

```java
SemanticDocument fromXml = new StreamingReader().read(Files.readAllBytes(xmlFile)).document();
SemanticDocument fromPdf = PdfInvoiceImporter.importPdf(Files.readAllBytes(pdfFile)).document();
String invoiceNumber = fromXml.value(SemanticPath.of("/BT-1"))
        .map(SemanticValue::asString).orElseThrow();
```

`read` recognizes the root element and refuses a syntax it does not know. `importPdf` takes the
electronic invoice out of the container and reads nothing off the page
([`pdf-input.md`](pdf-input.md)). The report beside the document says what did not reach it
([`java-api.md`](java-api.md#reading-ubl-and-cii-without-a-tree)). `XrImporter` of `esj-xr` reads
the same three types through the vendored KoSIT stylesheets.

Read an ESJ file with the strict reader. What fails layer L1 is not a document.

```java
SemanticDocument document = EsjReader.strict().read(Files.readAllBytes(file));
```

The typed accessors read a value on demand and refuse what the grammar does not allow:
`asDecimal` for the four decimal types, `asDate` for a date, `asBytes` for a binary object,
`asString` for the rest ([`java-api.md`](java-api.md#layers)). The registry says which one
belongs to a term. If you would rather not look it up, read the typed view.

*Verified by `esj-pdf/src/test/java/de/bsnsoft/esj/pdf/GettingStartedExamplesTest.java`
and `esj-typed/src/test/java/de/bsnsoft/esj/typed/ReadmeExamplesTest.java`.*

## Create an invoice, validate it, write it

`esj-invoice` lets you write an invoice in the words of the domain. You use enums instead of code
lists and parties and lines instead of business groups, and you type no total by hand.

```java
SemanticDocument document = Invoice.create(Profile.EN16931)
        .number("RE-2026-0211")
        .issued(LocalDate.of(2026, 5, 12))
        .currency(CurrencyCode.EUR)
        .seller(Party.named("Example GmbH").vatId("DE123456789")
                .address("Musterweg 12", "10117", "Beispielstadt", Country.DE))
        .buyer(Party.named("Muster AG").vatId("DE987654321")
                .address("Beispielallee 3", "20095", "Musterstadt", Country.DE))
        .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                PaymentTerms.days(30, "Payable within 30 days without deduction."))
        .line(Line.of("Sensor module SM-100").quantity(100, Unit.PIECE).unitPrice("12")
                .vat(Vat.standard(19))
                .allowance(Allowance.percent(10, AllowanceReason.DISCOUNT)
                        .reason("Quantity discount")))
        .line(Line.of("Printed documentation set").quantity(5, Unit.PIECE).unitPrice("200")
                .vat(Vat.standard(7))
                .allowance(Allowance.amount("25", AllowanceReason.SPECIAL_AGREEMENT)
                        .reason("Damaged packaging")))
        .allowance(Allowance.amount("100", AllowanceReason.SPECIAL_REBATE)
                .reason("Annual volume rebate").vat(Vat.standard(19)))
        .allowance(Allowance.amount("50", AllowanceReason.SPECIAL_REBATE)
                .reason("Annual volume rebate").vat(Vat.standard(7)))
        .build();
```

The chain asks for what EN 16931-1 asks of every invoice. You reach `build()` only once all of it
is there. `build()` derives the line net amounts, the VAT breakdown and the totals. It checks
layers L2 and L3 and the fatal findings of the EN 16931 rule pack. Then it returns the document,
or it refuses and names the term it missed. That document is `examples/allowances.esj.json`.
`Invoice.draft` takes the same members in any order, and `Invoice.read` reads one back.

```java
Files.write(file, EsjWriter.pretty().toBytes(document));
Files.write(canonicalFile, EsjWriter.canonical().toBytes(document));
```

Hash, compare and store the canonical form
([`java-api.md`](java-api.md#canonical-form-and-digests)). Under the domain API sit the constrained
builder and the typed editor. They write any term of the standard by its own name and leave
derivation and validation to you ([`java-api.md`](java-api.md#layers)).
[`examples/java/HybridInvoice.java`](../examples/java/HybridInvoice.java) carries this on. It
renders the invoice, embeds it as Factur-X, reads it back and checks the business rules in the
three places a sender can check them.

*Verified by `esj-invoice/src/test/java/de/bsnsoft/esj/invoice/DomainInvoiceTest.java`.*

## Put it into a database

Pass a document from outside through the reader and the validator before you store it. The row
gets the canonical form and the two digests.

```java
SemanticDocument document = EsjReader.strict().read(bytes);
ValidationResult result = StructuralValidator.validate(document, Registry.en16931());
if (result.status() == ValidationStatus.INVALID) {
    throw new IllegalArgumentException(result.findings().toString());
}

byte[] canonical = Canonicalizer.canonicalBytes(document);
String semanticDigest = Canonicalizer.semanticDigest(document);
String documentDigest = Canonicalizer.documentDigest(document);
```

Those four values make one `INSERT`: the canonical bytes as they are, the same bytes in a `jsonb`
column you query, and the digests for deduplication and change detection. The schema, the views,
the indexes and the key/value variant are in [`storage.md`](storage.md).

*Verified by `esj-xr/src/test/java/de/bsnsoft/esj/xr/StorageExamplesTest.java`.*

## The command line

Convert one invoice in any of the three syntaxes into an ESJ document:

```console
$ esj convert conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
{
  "format": "EN16931-Semantic-JSON",
  "version": "1.0",
  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
  "values": {
    "/BT-1": "123456XX",
...
```

Check a hybrid PDF against the official artefacts of its profile and the structural layers of this
specification. ESJ checks the container beside them:

```console
$ esj validate conformance/pdf/factur-x.pdf
Input:            conformance/pdf/factur-x.pdf
Detected:         PDF (hybrid invoice container)
Embedded invoice: "factur-x.xml" (CII, 9622 bytes declared), position 1
...
Container:        OK
Invoice:          VALID
$ echo $?
0
```

Print one value for a shell script, or a summary of a document:

```console
$ esj get examples/standard-invoice.esj.json /BG-22/BT-112
2915.5
```

```console
$ esj inspect examples/standard-invoice.esj.json
Input:                      examples/standard-invoice.esj.json
Detected syntax:            ESJ
...
INDETERMINATE — nothing fatal found; missing from the check: written-syntax (not-run-by-this-command), business-rules (not-run-by-this-command)
```

`convert`, `validate`, `render`, `inspect`, `extract`, `get`, `list`, `diff` and `canonicalize`
take XML, PDF and ESJ input. [`cli.md`](cli.md) is the complete reference: every option, every
output form and the exit codes. Run input from a stranger in a process of its own.
[`deployment.md`](deployment.md) has the contract, the switches and the measurements.

*Verified by `esj-cli/src/test/java/de/bsnsoft/esj/cli/ReadmeCliExamplesTest.java`.*
