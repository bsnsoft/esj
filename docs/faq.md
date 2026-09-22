# FAQ

*Part of [EN16931 Semantic JSON](../README.md).* German: [`faq-de.md`](faq-de.md).

One question, one command or one snippet. `invoice.xml` stands for a UBL or CII invoice,
`invoice.pdf` for a Factur-X or ZUGFeRD file, `invoice.esj.json` for an ESJ document,
`letterhead.json` for a render template and `pages.pdf` for a PDF/A-3 file without an invoice. A
test runs every `esj` line but the one that needs an installed veraPDF over files of the
repository, and every other block is taken as it stands from the page it links to, where a
test runs it.

## Reading

**How do I read an e-invoice, whichever syntax it came in?**
```sh
esj convert invoice.xml                     # UBL, CII or invoice.pdf in; the ESJ document out
```

**How do I get one value, such as the invoice number or the total?**
```sh
esj get invoice.xml /BT-1                   # the invoice number
esj get invoice.pdf /BG-22/BT-112           # the total with VAT, straight from the hybrid PDF
```

**How do I get one invoice line?**
```sh
esj get invoice.xml /BG-25/0/BT-131         # the net amount of the first line
```

**How do I see every value at once?**
```sh
esj list invoice.xml                        # one line per term: path, type, value
```

**How do I see at a glance what a file is?**
```sh
esj inspect invoice.pdf                     # syntax, profile, parties, totals, digests; exit 9, never a verdict
```

**How do I get the XML out of a ZUGFeRD PDF?**
```sh
esj extract invoice.pdf --list
esj extract invoice.pdf --out factur-x.xml
```

**How do I make an e-invoice readable?**
```sh
esj render invoice.xml --html --out invoice.html   # one self-contained page
esj render invoice.xml --out invoice.pdf           # a PDF/A-3b file
```

## Checking

**How do I validate an e-invoice?**
```sh
esj validate invoice.xml                    # XML Schema, Schematron and business rules; VALID, exit 0
```

**How do I check an XRechnung against the KoSIT rules?**
```sh
esj validate invoice.xml                    # the pack follows BT-24: xrechnung/3.0.2/2026-08-31
esj validate --extension xrechnung invoice.xml   # with the extension terms of XRechnung checked too
```

**How do I check a Factur-X or ZUGFeRD PDF, the container and the invoice?**
```sh
esj validate invoice.pdf                    # Container: OK / Invoice: VALID
```

**How do I prove compliance in one file?**
```sh
esj validate invoice.pdf --report proof.pdf # or proof.html: digests, packs, findings, the invoice
```

**How do I validate PDF/A instead of believing the declaration?**
```sh
esj validate invoice.pdf --verapdf /opt/verapdf   # a veraPDF of your own, never bundled
```

**How do I check the business rules on the data, without XML?**
```sh
esj validate invoice.esj.json               # the rule pack over the terms; the artefacts over the CII written in memory
```

**How do I tell whether two files are the same invoice, in UBL, CII or PDF?**
```sh
esj diff invoice.xml invoice.pdf            # the same semantic digest: no difference, exit 0
```

**How do I get a result a program can read?**
```sh
esj validate invoice.xml --output json      # invoice.ok, syntax.findings, rules.findings, reasons
```

**How do I tell "invalid" from "could not be checked"?**
By the exit code: 0 `VALID`, 1 `INVALID`, 9 `INDETERMINATE` with `reasons`, 7 a limit and no
verdict ([`cli.md`](cli.md#exit-codes)).

**Which official artefacts run, in which version?**
```sh
esj --list-packs                            # XML Schema, EN 16931 Schematron, XRechnung Schematron, licences
```

## Storing and querying

**How do I store an e-invoice?**
```sql
CREATE TABLE invoice (
    id              BIGSERIAL PRIMARY KEY,
    document        JSONB     NOT NULL,
    semantic_digest CHAR(64)  NOT NULL
);
```

**How do I query invoices in the database?**
```sql
SELECT document -> 'values' ->> '/BT-1' AS invoice_number
  FROM invoice
 WHERE document -> 'values' ->> '/BG-4/BT-27' = ?
```

**How do I index the invoice number?**
```sql
CREATE INDEX invoice_number_idx ON invoice ((document -> 'values' ->> '/BT-1'));
```

**How do I recognize a duplicate, whichever syntax it came in?**
```sh
esj canonicalize invoice.xml --digest       # semantic: the same for the UBL and the CII of one invoice
```

**How do I tell a changed invoice from a changed file?**
```sh
esj canonicalize invoice.esj.json --digest  # semantic: the content; document: the whole file
```

**How do I keep the link to the bytes that arrived?**
```sh
esj convert invoice.xml                     # ends with "source": the syntax and the SHA-256 of the input
```

## Producing

**How do I produce an e-invoice from my own data without knowing UBL or CII?**
```sh
esj convert invoice.esj.json --to ubl --out invoice.ubl.xml   # or --to cii
```

**How do I convert UBL to CII, or back?**
```sh
esj convert invoice.xml --to cii --out invoice.cii.xml
```

**How do I get a good-looking PDF on my own letterhead?**
```sh
esj render invoice.esj.json --template letterhead.json --out invoice.pdf   # the letter layout, DIN 5008
```

**How do I produce a Factur-X or ZUGFeRD PDF?**
```sh
esj render invoice.esj.json --template letterhead.json --embed cii --out invoice.pdf
```

**How do I attach an e-invoice to a PDF I already have?**
```sh
esj embed pages.pdf invoice.esj.json --out invoice.pdf   # XML and ESJ attached; PDF/A-3 stays what it was
```

**How does a GiroCode get onto the invoice?**
```sh
esj render invoice.esj.json --layout letter --out invoice.pdf   # in the payment block, where the invoice states a credit transfer
```

**How do I show a consumer the gross prices (B2C)?**
```sh
esj render examples/b2c-gross.esj.json --extension b2c --template examples/templates/gross.json --out invoice.pdf
```

**How do I lift an invoice to EN 16931-1:2026?**
```sh
esj upgrade invoice.esj.json --to 2026 --out invoice-2026.esj.json   # names every open point, never rounds
```

## Integrating

**How do I call it from a service, for input I do not trust?**
```sh
java -Xmx512m -XX:+ExitOnOutOfMemoryError -jar esj.jar validate - --output json --max-runtime 30s < invoice.xml
```

**How do I process very large invoices, 80 MB and 300 000 lines?**
```sh
esj validate --limits large --max-runtime 10m invoice.xml   # streaming reader; rules linear in the lines
```

**How do I get it onto a machine without Java?**
```sh
dist/package.sh native                      # a native executable; also runtime-image, docker, zip
```

**How do I map an e-invoice into my own data model?**
```sh
esj list invoice.xml --format json          # one object per term: path, datatype, value
```

**How do I use it from TypeScript?** ([`bindings-ts.md`](bindings-ts.md))
```ts
const document = readDocumentOrThrow(await readFile('invoice.esj.json'));
document.values.get('/BG-25/0/BT-131');           // { value: '1080' }
```

**How do I use it from C#?** ([`bindings-csharp.md`](bindings-csharp.md))
```csharp
SemanticDocument invoice = EsjReader.Strict().Read(File.ReadAllBytes("invoice.esj.json"));

string digest = Canonicalizer.SemanticDigest(invoice);
```

## Java

The lines are those of [`getting-started.md`](getting-started.md), [`java-api.md`](java-api.md),
[`b2c.md`](b2c.md) and the README.

**How do I read an e-invoice in Java?**
```java
SemanticDocument fromXml = new StreamingReader().read(Files.readAllBytes(xmlFile)).document();
SemanticDocument fromPdf = PdfInvoiceImporter.importPdf(Files.readAllBytes(pdfFile)).document();
```

**How do I read by name instead of by term number?**
```java
Invoice invoice = En16931.view(document);
LocalDate issueDate = invoice.issueDate();
String sellerName = invoice.seller().name();
BigDecimal amountDue = invoice.documentTotals().amountDueForPayment();
```

**How do I write an invoice in the words of the domain?** The chain goes on with seller, buyer,
payment and lines; `build()` derives the totals, checks layers L2 and L3 and the fatal rules.
```java
SemanticDocument document = Invoice.create(Profile.EN16931)
        .number("RE-2026-0211")
        .issued(LocalDate.of(2026, 5, 12))
        .currency(CurrencyCode.EUR)
```

**How do I change an existing invoice?**
```java
InvoiceEditor amended = En16931.edit(document);
amended.invoiceLines().remove(0);
amended.buyerReference("KOST-4711");
```

**How do I derive the line amounts, the VAT breakdown and the totals?**
```java
DerivationReport report = invoice.derive(Totals.STANDARD);
```

**How do I check the business rules in Java?**
```java
RuleEngine engine = En16931.engine(Registry.en16931());

List<RuleFinding> findings = engine.evaluate(document);
boolean rejected = findings.stream().anyMatch(RuleFinding::fatal);
```

**How do I run the official artefacts in Java?**
```java
SyntaxReport report = SyntaxValidator.validate(Files.readAllBytes(invoice));
```

**How do I write UBL or CII from Java?**
```java
WriteResult cii = CiiWriter.writeWithReport(document, WriterOptions.defaults());
WriteResult ubl = UblWriter.writeWithReport(document, WriterOptions.defaults());
```

**How do I produce the hybrid PDF from Java?**
```java
byte[] pages = new PdfRenderer().render(invoice, RenderOptions.in(RenderLanguage.ENGLISH));
EmbedResult hybrid = FacturX.embedWithReport(pages, invoice,
        EmbedOptions.of(FacturXProfile.EN_16931));
```

**How do I store from Java: the canonical bytes and the digests?**
```java
byte[] canonical = Canonicalizer.canonicalBytes(document);
String semanticDigest = Canonicalizer.semanticDigest(document);
String documentDigest = Canonicalizer.documentDigest(document);
```

**How do I keep the gross figures a consumer was shown?**
```java
Gross gross = Gross.on(invoice);
gross.line(0).displayedGrossUnitPrice("99.99");
gross.displayedGrossTotal("99.99");
```
