# The Java API

*Part of [EN16931 Semantic JSON](../README.md).*

Every snippet `README.md` and [`getting-started.md`](getting-started.md) show, and every one
they do not. `SPEC.md` is the normative specification of the format.

## The reference implementation

The format needs no library: `SPEC.md` is normative and complete, `schema/esj.schema.json` covers
most of layer L1 for any JSON Schema 2020-12 validator, `model/en16931/2017.json` carries the
registry L2 and L3 check against, and every document in `examples/` has a canonical twin. Every
module is built for Java 17 and depends on `esj-core`. From 0.9.0 they are on Maven Central;
**Building from source** below builds the tree itself.

| Artefact | What it adds | What it pulls in |
|---|---|---|
| `esj-core` | paths, values, reader, writer, canonicalizer, structural validator | `jackson-core` |
| `esj-typed` | the generated typed view and the typed editor | — |
| `esj-bindings` | the table-driven streaming reader and the two writers | `esj-xr` |
| `esj-xr` | the XSLT path for UBL 2.1 and CII D16B, and the XR export | Saxon-HE |
| `esj-syntax` | the official XSD and Schematron of a profile, run over an XML input | `esj-xr` |
| `esj-rules` | the business rules of the standard over the semantic model | — |
| `esj-invoice` | the domain API: invoice objects, code list enums, one `build()` | `esj-typed`, `esj-rules` |
| `esj-b2c` | the B2C extension: the gross figures a consumer was shown, and the policies that derive the net invoice from them ([`b2c.md`](b2c.md)) | `esj-invoice` |
| `esj-pdf` | hybrid PDFs: the embedded invoice read, and an invoice written into a PDF/A-3 file | `esj-xr`, `esj-bindings`, PDFBox |
| `esj-render` | the XR export, the HTML page and the PDF/A-3b rendering, plain or on a template | `esj-xr`, PDFBox, ZXing |
| `esj-generator` | generates the sources of `esj-typed` and the per-term schema, under `-Pgenerate` | — |
| `esj-cli` | the `esj` command line tool | every module above but `esj-invoice`, picocli |
| `esj-bom` | the version of every module above but `esj-cli`, which is not published | — |

Import the bill of materials once, then name a module of the table as a dependency without a
version of its own; `esj-cli` is the tool and is on no repository ([`releasing.md`](releasing.md)).

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>de.bsnsoft.esj</groupId>
      <artifactId>esj-bom</artifactId>
      <version>0.9.1</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

The public API lives in eight packages of `esj-core` — `de.bsnsoft.esj` for paths,
values and documents, `.json` for reader, writer, canonicalizer and limits, `.model` for the
registry, `.validate` for the structural validator, `.imports` for what a reader has to say about
the document it read, `.upgrade` for moving a document between editions
([`editions.md`](editions.md)), `.handler` for the event view, `.report` for what a run came to —
plus one package per module, named after it, and `.invoice.code` for the code list enums.

Every snippet below is a test, in the `ReadmeExamplesTest.java` of the module it shows.

## Reading a document

A reader is strict: what fails layer L1 is not a document. `read` throws `EsjFormatException`
with the finding code of `SPEC.md` section 9.6 and the place of the problem, or
`EsjLimitException` for a limit of section 12.2.

```java
SemanticDocument document = EsjReader.strict().read(Files.readAllBytes(file));
```

To see every defect at once, `readWithFindings` returns the document it could build with the
findings beside it.

```java
ReadResult result = EsjReader.strict().readWithFindings(bytes);
List<String> problems = result.findings().stream().map(Finding::toString).toList();
```

The limits of `SPEC.md` section 12.2 are the defaults, and every one is the caller's to set.

```java
EsjReader reader = EsjReader.withLimits(
        Limits.defaults().toBuilder().maxValues(5_000).build());
```

## Layers

Four layers over one registry, each usable on its own, each expressible through the one below it;
only the top one is written by hand ([`design-decisions.md`](design-decisions.md)).

| Layer | Type | Module | For |
|---|---|---|---|
| document | `SemanticDocument` | `esj-core` | paths and values: storage, mapping, tooling |
| typed view and editor | `En16931.view`, `InvoiceEditor` | `esj-typed` | one accessor and one setter per business term, generated |
| constrained builder | `InvoiceBuilder` | `esj-typed` | the same, with the structure of the model checked by the compiler |
| domain API | `Invoice`, `Party`, `Line`, `Vat` | `esj-invoice` | enums, profile defaults, one `build()` |

**The document.** A value is addressed by the identifier the standard gives it.
`canonicalContent` returns the string it carries; the typed accessors — `asDecimal`, `asDate`,
`asBytes`, `asString` — read it on demand and refuse what the grammar does not allow, and which
of them belongs to a term is the registry's answer, not the document's.

```java
String invoiceNumber = document.value(SemanticPath.of("/BT-1"))
        .map(SemanticValue::asString)
        .orElseThrow();
BigDecimal firstLineNet = document.value(SemanticPath.of("/BG-25/0/BT-131"))
        .map(SemanticValue::asDecimal)
        .orElseThrow();
```

`values()` is the whole document as a sorted map, in the canonical path order of `SPEC.md`
section 7.4.

```java
List<String> lines = new ArrayList<>();
for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
    SemanticPath path = entry.getKey();
    SemanticValue value = entry.getValue();
    lines.add(path + " " + value.canonicalContent());
}
```

**The typed view and editor.** Names where the identifiers stood, one view with its own `En16931`
per edition, generated from that edition's registry; `edit` refuses a document of another edition.
A mandatory term comes back directly, an optional one as an `Optional`, a repeatable term or group
as a `List`, the business term stands in the Javadoc, nothing is copied. The view neither validates
nor hides a defect: an absent mandatory term throws `MissingValueException`, content outside the
grammar of its semantic data type `ValueTypeException`. The `Invoice` below is
`…esj.typed.Invoice`; six simple names — `Invoice`, `Address`, `Allowance`, `Charge`, `Delivery`
and `Vat` — exist in that package and in `…esj.invoice`, so a class using both qualifies one of
them.

```java
Invoice invoice = En16931.view(document);
LocalDate issueDate = invoice.issueDate();
String sellerName = invoice.seller().name();
BigDecimal amountDue = invoice.documentTotals().amountDueForPayment();
List<String> items = new ArrayList<>();
for (InvoiceLine line : invoice.invoiceLines()) {
    items.add(line.item().name() + " " + line.netAmount());
}
```

**The constrained builder.** The same editor behind step interfaces generated from the registry:
one step per mandatory member, in the order of the model, so that the terminal step — the only
one carrying `build()` — is reachable once all of them are written and not before.

```java
SemanticDocument built = InvoiceBuilder.create(Profile.EN16931)
        .invoiceNumber("RE-2026-0211")
        .issueDate(LocalDate.of(2026, 5, 12))
        .typeCode("380")
        .currencyCode("EUR")
        .seller(seller -> seller
                .name("Example GmbH")
                .postalAddress(address -> address.countryCode("DE"))
                .vatIdentifier("DE123456789"))
        .buyer(buyer -> buyer
                .name("Muster AG")
                .postalAddress(address -> address.countryCode("DE")))
        .invoiceLine(line -> line
                .identifier("1")
                .quantity(new BigDecimal("2"), "H87")
                .price(price -> price.netPrice(new BigDecimal("12.50")))
                .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                .item(item -> item.name("Sensor module SM-100")))
        .paymentDueDate(LocalDate.of(2026, 6, 11))
        .paymentTerms("Payable within 30 days.")
        .build();
```

`build()` names what is missing, derives with `Totals.STANDARD`, checks layers L2 and L3 and
hands over the document; `derive`, `validate`, `validateOrThrow` and `document` are that chain
step by step, and `build(InvoiceRules)` adds the business rules a caller supplies.
`InvoiceBuilder.draft(profile)` takes the same members in any order through
`InvoiceDraft.edit(...)`, checks the same completeness and writes the same document.

A `Profile` is an overlay of facts under `model/profiles/`: the values it fixes, written when the
builder is opened, and the cardinalities it narrows, which become steps of its own chain.
`Profile.EN16931` fixes BT-24; `Profile.XRECHNUNG_3_0` fixes BT-23 and BT-24 and narrows nineteen
members to mandatory, seventeen of which become steps, among them BT-10, BT-34, BT-49 and BG-16.
A term a derivation policy writes is no step of either chain (`model/derivable-terms.json`);
`model/README.md` reads both files.

**The domain API.** The same invoice in the words of the domain, byte for byte the document
above; the section of that name below is the rest of it.

```java
SemanticDocument written = Invoice.create(Profile.EN16931)
        .number("RE-2026-0211")
        .issued(LocalDate.of(2026, 5, 12))
        .currency(CurrencyCode.EUR)
        .seller(Party.named("Example GmbH").vatId("DE123456789")
                .address(Address.in(Country.DE)))
        .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
        .line(Line.of("Sensor module SM-100").quantity(2, Unit.PIECE)
                .unitPrice("12.50").vat(Vat.standard(19)))
        .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."))
        .build();
```

## Validating

Layers L2 and L3 are checked against the registry and are individually selectable. A finding is
a value, not an exception: fifty problems produce fifty findings in one pass.

```java
Registry registry = Registry.en16931();
ValidationResult result = StructuralValidator.validate(document, registry);
ValidationStatus status = result.status();

ValidationResult cardinality = StructuralValidator.validate(
        document, registry, EnumSet.of(ValidationLayer.L3));
```

### The three states

`ValidationResult` carries the verdict, the findings and the coverage, and derives the verdict
from the other two: an empty finding list is not conformance, because a validator that
evaluated nothing also reports nothing.

| `status()` | when |
|---|---|
| `VALID` | all three layers were evaluated, no finding is an error, and no finding records something not evaluated |
| `INVALID` | some finding is an error that judges the document |
| `INDETERMINATE` | nothing was found to be wrong and something was not evaluated |

Three finding codes record something that was **not** evaluated rather than something that is
wrong — `ESJ-L1-LIMIT`, `ESJ-L2-NOT-CHECKED` and `ESJ-L2-EDITION-UNKNOWN`, together
`FindingCode.recordsSomethingNotEvaluated()`. Severity alone does not decide the state:
`ESJ-L1-LIMIT` is an error and yields `INDETERMINATE`, because a limit is this reader's
configuration and not a defect of the document.

`evaluated()` and `notEvaluated()` partition the three layers, so every result states what it
did not do; the reason comes from the closed vocabulary `NotEvaluatedReason` — `LIMIT`,
`PRECEDING-LAYER-FAILED`, `EDITION-UNKNOWN`, `NOT-REQUESTED`, in the order of precedence
`SPEC.md` 9.5 fixes, which also decides which survives a `merge`. `registries()` names what the
run measured against. A structural validator never evaluates L1 and a reader evaluates nothing
else, so neither alone may say `VALID`; `merge` composes them. A document that never was a byte
sequence has no L1, and the tool that composes a verdict for such an input decides whether L1 is
part of its complete check (`SPEC.md` 3.5).

```java
ReadResult read = EsjReader.strict().readWithFindings(bytes);
ValidationResult verdict = read.isWellFormed()
        ? read.validation().merge(
                StructuralValidator.validate(read.orElseThrow(), registry))
        : read.validation();
```

Layer L1 is the reader's: it is decided by the bytes, so the validator refuses to be asked
for it. An extension registry is loaded beside the core one, and the paths it describes are
then checked rather than reported as unchecked.

```java
Registry registry = Registry.en16931().withExtension(Registry.xrechnungExtension());
ValidationResult result = StructuralValidator.validate(
        document, registry, EnumSet.of(ValidationLayer.L2));
```

A validator given a set of registries picks the one that describes the edition the document
names. Where none does it evaluates nothing rather than guessing: the result carries
`ESJ-L2-EDITION-UNKNOWN` once at document level, names both model layers as not evaluated for
the reason `EDITION-UNKNOWN`, and is `INDETERMINATE`. Which editions an implementation holds a
registry for is a property of the implementation, not of the document.

## Canonical form and digests

The canonical form is one byte sequence per document content, and the two digests of `SPEC.md`
section 8 are taken over it: the semantic digest over the semantic model and the `values`
object, the document digest over the whole document.

The document has to be one that was read whole: a document `readWithFindings` handed back after
layer L1 found an error is missing members, and its digest then identifies content the sender
never sent. `EsjReader.strict().read(bytes)` throws instead; a caller that read with findings
asks `isWellFormed()` first.

```java
byte[] canonical = Canonicalizer.canonicalBytes(document);
String semanticDigest = Canonicalizer.semanticDigest(document);
String documentDigest = Canonicalizer.documentDigest(document);
```

A canonicalizer needs no registry and works from bytes; `EsjWriter` writes the same document
pretty, in the same order and with another layout.

```java
byte[] fromBytes = Canonicalizer.canonicalize(bytes);

Files.write(file, EsjWriter.pretty().toBytes(document));
byte[] canonical = EsjWriter.canonical().toBytes(document);
```

## Building a document

The builder checks the path grammar and nothing else; whether what it produces is conformant is
the validator's question.

```java
SemanticDocument document = SemanticDocument.builder()
        .put("/BT-1", "RE-2026-0001")
        .put("/BT-2", SemanticValue.ofDate(LocalDate.of(2026, 1, 15)))
        .put("/BT-3", "380")
        .put("/BT-5", "EUR")
        .put("/BG-4/BT-27", "Example GmbH")
        .put("/BG-4/BT-29/0", SemanticValue.identifier("0088123456785", "0088"))
        .put("/BG-25/0/BT-129", SemanticValue.ofDecimal(new BigDecimal("1")))
        .put("/BG-25/0/BT-131", SemanticValue.ofDecimal(new BigDecimal("100")))
        .source(SemanticDocument.Source.ofSyntax("UBL"))
        .build();
```

`toBuilder` amends a document without changing it; documents are immutable, and `put` refuses
an address that is already taken, so replacing a value is a removal and an insertion:

```java
SemanticDocument amended = document.toBuilder()
        .put("/BT-11", "PRJ-2026-7")
        .remove("/BT-19")
        .remove("/BT-10")
        .put("/BT-10", "04011000-12345-34")
        .build();
```

## Building an invoice

`En16931.newInvoice()` opens an editor whose accessors carry the same generated names as the
view. A group is reached through a handle typed by its cardinality — a `1..1` or `0..1` group
has one, a repeatable group has a list handle — and nested content is written in a block.

```java
InvoiceEditor invoice = En16931.newInvoice();
invoice.invoiceNumber("RE-2026-0211")
        .issueDate(LocalDate.of(2026, 5, 12))
        .typeCode("380")
        .currencyCode("EUR")
        .paymentDueDate(LocalDate.of(2026, 6, 11))
        .paymentTerms("Payable within 30 days without deduction.")
        .processControl(c -> c.specificationIdentifier("urn:cen.eu:en16931:2017"));

invoice.seller()
        .name("Example GmbH")
        .vatIdentifier("DE123456789")
        .postalAddress(a -> a.addressLine1("Musterweg 12").city("Beispielstadt")
                .postCode("10117").countryCode("DE"));

invoice.buyer()
        .name("Muster AG")
        .vatIdentifier("DE987654321")
        .postalAddress(a -> a.addressLine1("Beispielallee 3").city("Musterstadt")
                .postCode("20095").countryCode("DE"));

invoice.paymentInstructions(p -> p
        .paymentMeansTypeCode("58")
        .creditTransfer(t -> t.accountIdentifier("DE89370400440532013000")));

invoice.documentLevelAllowance(a -> a
                .amount(new BigDecimal("100"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("19"))
                .reason("Annual volume rebate")
                .reasonCode("100"))
        .documentLevelAllowance(a -> a
                .amount(new BigDecimal("50"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("7"))
                .reason("Annual volume rebate")
                .reasonCode("100"));

invoice.invoiceLine(line -> line
                .identifier("1")
                .quantity(new BigDecimal("100"), "H87")
                .allowance(a -> a
                        .amount(new BigDecimal("120"))
                        .baseAmount(new BigDecimal("1200"))
                        .percentage(new BigDecimal("10"))
                        .reason("Quantity discount")
                        .reasonCode("95"))
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                .item(i -> i.name("Sensor module SM-100")))
        .invoiceLine(line -> line
                .identifier("2")
                .quantity(new BigDecimal("5"), "H87")
                .allowance(a -> a
                        .amount(new BigDecimal("25"))
                        .reason("Damaged packaging")
                        .reasonCode("64"))
                .price(p -> p.netPrice(new BigDecimal("200")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("7")))
                .item(i -> i.name("Printed documentation set")));

invoice.derive(Totals.STANDARD);

SemanticDocument document = invoice.document();
```

That is `examples/allowances.esj.json`, term for term: the same snippet in
`ReadmeExamplesTest` canonicalizes the document it builds and compares the bytes against
`examples/allowances.canonical.esj.json`. No amount the invoice itself implies is typed; the
last line before `document()` derives them. A repeatable group is written either through its
list handle — `invoiceLines()`, which also reads, counts and removes — or through the singular
block that appends one instance and hands the parent editor back.

A group comes into being when a value is written into it (`SPEC.md` 4.5): there is no `create`
step. Setting `null` or an empty string removes a value, with one wrinkle: a term of the
semantic data type Identifier has both a `String` and an `Identifier` setter of one argument, so
the literal `null` needs the cast `(String) null` or `(Identifier) null`. Nothing is validated
on the way in beyond the grammar of the semantic data type.

`En16931.edit(document)` opens an existing document for changes; the document itself is
immutable. A list handle appends with `add`, reads with `get`, counts with `size`, empties with
`clear` and removes with `remove`, which moves the occurrences that follow one index down.

```java
InvoiceEditor amended = En16931.edit(document);
amended.invoiceLines().remove(0);
amended.buyerReference("KOST-4711");
// an extension term has no generated setter in 0.1; the builder is the way to it
amended.builder().put("/BG-DEX-09/0/BT-DEX-001", "retention");
SemanticDocument changed = amended.document();
```

## Deriving the totals

`Totals` is the policy that computes the amounts the invoice implies:

```java
DerivationReport report = invoice.derive(Totals.STANDARD);

List<Derived> written = report.values();
List<Derived> rounded = report.roundings();
List<Removed> removed = report.removals();
Optional<Derived> lineNet = report.at("/BG-25/0/BT-131");
```

`removals()` is what the run took out with nothing in its place: BT-107 or BT-108 with no
document level allowance or charge behind it, and a breakdown for a category and rate the invoice
does not use. A stated value written over is a `replacements()` entry, matched by category and
rate, not by index.

It writes the line net amount (BT-131) of every line, one VAT breakdown (BG-23) per pair of VAT
category code and VAT rate, and the document totals (BG-22), in that order:

| Written | From |
|---|---|
| BT-131 | BT-129 × BT-146 ÷ BT-149 + Σ BT-141 − Σ BT-136, rounded half up to two decimals |
| BT-116 | the line net amounts of the pair, − Σ BT-92 + Σ BT-99 of the same pair |
| BT-117 | BT-116 × BT-119 ÷ 100, rounded half up to two decimals |
| BT-106, BT-107, BT-108 | Σ BT-131, Σ BT-92, Σ BT-99 |
| BT-109, BT-110, BT-112 | BT-106 − BT-107 + BT-108, Σ BT-117, BT-109 + BT-110 |
| BT-115 | BT-112 − BT-113 + BT-114, taking BT-113 and BT-114 from the invoice |

Base quantity is one where the invoice does not state it, a category keeps the exemption reason
(BT-120, BT-121) its breakdown carried, and BT-111 is written only with the exchange rate in
`TotalsOptions`. Decimals follow 6.5: item net price, invoiced quantity, item price base
quantity and VAT rate are unlimited and used at the scale the caller gave; every intermediate
result is an exact `BigDecimal`; rounding is half up to two decimals, once per amount, and the
totals are sums of amounts already rounded (6.5.13).

The derivation runs where it is asked to and never inside `document()`. Where the invoice does
not say enough — a line with no VAT category, a category levied at a rate with no rate, a base
quantity with no price, an allowance with no category, no line at all — it throws
`DerivationException` naming the term and the group instance. A line that already carries a net
amount is checked against the formula, and a mismatch is a refusal unless
`TotalsOptions.standard().withOverwriteLines(true)` replaces it.

## The domain API

`Invoice.create(profile)` opens one step chain per profile, `Invoice.draft(profile)` takes the
same members in any order, and `Invoice.creditNote(profile)` is the chain with BT-3 381, where
`precedingInvoice(number, issued)` names the invoice credited (BG-3). `build()` runs
`derive(Totals.STANDARD)`, L2 and L3, the members the profile adds, and the fatal findings of
`Rules.en16931()`; a warning or an information goes into the report and refuses nothing.
`buildReport()` returns the document and the findings instead of throwing, `validate()` reports
without deriving, `document()` hands over what was written, and `Draft.edit(e -> ...)` is the
typed editor for a term this layer has no word for. A part a group has no term for is refused by
name, and a group the model allows at most once — BG-4, BG-7, BG-10, BG-13, BG-14, BG-16 — is
refused the second time rather than merged into the first.

The read side gives the same view of a document that arrived from anywhere:

```java
Invoice invoice = Invoice.read(document);
BigDecimal due = invoice.totals().orElseThrow().amountDue();
Coded unit = invoice.lines().get(0).unit().orElseThrow();
```

Ten enums are generated from the code list snapshots the rule pack `en16931/1.3.16` carries
([`../rules/en16931/1.3.16/codelists/SOURCES.md`](../rules/en16931/1.3.16/codelists/SOURCES.md)):
`Unit`, `InvoiceType`, `PaymentMeansCode`, `VatCategory`, `AllowanceReason`, `ChargeReason`,
`CurrencyCode`, `Country`, `ElectronicAddressScheme`, `VatExemptionReason`, each constant carrying
its code and its publisher's name. A slot that takes a code takes the enum of its list or the
interface `Coded`, so `Unit.custom("QQQ")` stays reachable and `Unit.resolve(code)` reads an
unknown code back as a `CustomCode`; whether a code is admissible is the rule pack's answer.
Unit prices, quantities and percentages keep the scale the caller gave; the one amount this
layer computes — a percentage of a base amount — is rounded half up to two decimals at the
result. `Contact.named(n).telephone(t).email(e)` names the three parts that
`Contact.of(n, t, e)` tells apart only by position.

## Replaying a document

A document can be consumed as a stream of events instead of a map. `Replay` produces them in
canonical path order with the group instances nested.

```java
List<String> events = new ArrayList<>();
Replay.replay(document, new SemanticHandler() {

    @Override
    public void beginGroup(SemanticPath groupPath) {
        events.add("begin " + groupPath);
    }

    @Override
    public void value(SemanticPath path, SemanticValue value) {
        events.add("value " + path);
    }

    @Override
    public void endGroup(SemanticPath groupPath) {
        events.add("end " + groupPath);
    }
});

// DocumentCollector builds a document back from those events
DocumentCollector collector = new DocumentCollector();
Replay.replay(document, collector);
SemanticDocument copy = collector.document();
```

## Reading UBL and CII

`esj-xr` reads an invoice written in UBL 2.1 (invoice or credit note) or in UN/CEFACT CII D16B.
It holds no syntax knowledge of its own: the stylesheets of the KoSIT XRechnung visualization
transform the document into the semantic XR representation and the importer asks the registry
what each identifier is. This is the second of the two readers, the one the command line runs
under `--importer xslt`; where the syntax is not named it is read off the root element.

```java
XrImporter importer = new XrImporter();
SemanticDocument fromUbl = importer.importUbl(Files.readAllBytes(ublFile));
SemanticDocument fromCii = importer.importCii(Files.readAllBytes(ciiFile));
SemanticDocument either = importer.importXml(Files.readAllBytes(someFile));
```

What comes back is an ordinary `SemanticDocument`; its `source` member records the syntax and the
SHA-256 of the bytes it was read from, and the variant with a report adds what did not reach it.

```java
ImportResult result = importer.importXmlWithReport(Files.readAllBytes(invoice));

SemanticDocument document = result.document();
String syntax = document.source().orElseThrow().syntax().orElseThrow();
List<ImportNote> notes = result.report().notes();
```

An element the registry cannot place, content that spells no value of its semantic data type
and a supplementary component the standard does not give that term are left out and described in
the report. It does not cover content the stylesheets never emitted — a CII date with a format
qualifier other than `102` is the known case.

**What the importer refuses.** A document type declaration, outright, with an
`XrFormatException` — and with it external entities, parameter entities and entity expansion.
Nothing external is fetched: no entity, no DTD, no schema, no XInclude. The input is bounded in
bytes before it is parsed, four mebibytes by default, and the XR element nesting as the mapper
walks it; both raise `XrLimitException`. The byte bound is low because the cost of the
transformation grows faster than the input
([`deployment-measurements.md`](deployment-measurements.md)); `XrImporter.maxInputBytes()`
raises it.

**What the importer writes within.** The `Limits` of the reader its documents are written for,
measured as it writes: path length, number of values, string and binary length, binary content,
canonical size. What does not fit is left out and named in the report — a path too deep as
`PATH_TOO_LONG`, everything else as `LIMIT_REACHED`. Sub invoice lines are the readable case,
each level spending two of the sixteen segments a path may carry;
`XrImporter.readerLimits()` takes a wider profile.

**What the importer corrects.** The named normalizations of `XrNormalization` it was given, all
of them by default and each switchable. The one of this release splits the note subject code
BT-21 out of the `#AAC#` prefix the UBL binding writes into the note BT-22, at document level
and for UBL only, the scope of the CEN rule `BR-CL-08`; `conformance/ledger/pairs.md` carries
the rule and its source.

`XrImporter.registry()` is the core model combined with the XRechnung extension, so the
extension terms of an XRechnung invoice are checked rather than reported as unchecked. Layer L2
accepts every instance of the corpus; layer L3 does not, and
`conformance/ledger/l3-findings.md` names the two instances and the reason.

```java
ValidationResult model = StructuralValidator.validate(
        document, importer.registry(), EnumSet.of(ValidationLayer.L2));
ValidationStatus modelStatus = model.status();
List<Finding> modelFindings = model.findings();
```

One layer checked is not three, so `modelStatus` is `INDETERMINATE` here whatever the findings
say; the verdict over the imported document is the merge of all three layers.

Canonicalizing is what makes two syntaxes comparable. The semantic digest covers `values` and
nothing else, so two documents built from the same invoice in different syntaxes meet on it
while their `source` members, and therefore their document digests, differ.

```java
byte[] canonical = Canonicalizer.canonicalBytes(document);
boolean sameInvoice = Canonicalizer.semanticDigest(fromUbl)
        .equals(Canonicalizer.semanticDigest(fromCii));
```

The litmus test of [`conformance.md`](conformance.md) is what that comparison says over the
whole corpus. This module depends on Saxon-HE (Mozilla Public License 2.0), because the
stylesheets are written in XSLT 2.0.

## Reading UBL and CII without a tree

`esj-bindings` reads the same three document types a second way, and the difference is the cost
rather than the result: it pulls the document one element at a time and matches it against the
binding tables of `model/bindings/`, holding one element and the values it has produced, so
eighty megabytes take seconds ([`deployment-measurements.md`](deployment-measurements.md)).

```java
StreamingReader reader = new StreamingReader();
ImportResult result;
try (InputStream in = Files.newInputStream(invoice)) {
    result = reader.read(in);
}

SemanticDocument document = result.document();
List<ImportNote> notes = result.report().notes();
```

The result is the same `ImportResult` the importer returns, `source` member included; a root
element of no syntax it reads ends in a `BindingSyntaxException`. `ReaderOptions` says what it
may do: the registry, the `Limits` the documents are written for, and three bounds — the input
in bytes (64 MiB by default), the element nesting, and the largest element it holds whole.

```java
StreamingReader large = new StreamingReader(ReaderOptions.builder()
        .limits(Limits.builder().maxValues(5_000_000).build())
        .maxInputBytes(256L * 1024 * 1024)
        .build());
```

`ReaderMode` says what happens where the document and the model disagree: `REPAIR`, the
default, applies the named corrections the tables declare and turns a value it cannot build
into a note; `STRICT` ends the read with a `BindingFormatException` naming the path.
`encodingMode` is answered alike by both readers. This reader needs no XSLT processor.

Every instance of the conformance corpus is read both ways on every build;
[`conformance/readers.md`](../conformance/readers.md) records the result — 74 of 86 identical to
the byte — and every differing path with its cause. This reader writes `conformance/esj/`.

## Writing XML

The same tables that say where a business term is read say how to write it, so `esj-bindings`
also turns a semantic document back into XML: the element of every term and its conditions come
from `model/bindings/`, the order of two siblings from the schema modules of the pack.

```java
WriteResult cii = CiiWriter.writeWithReport(document, WriterOptions.defaults());
WriteResult ubl = UblWriter.writeWithReport(document, WriterOptions.defaults());

byte[] xml = ubl.xml();
WriteReport report = ubl.report();
```

`write(document)` returns the bytes alone. `WriterOptions` decides whether the output is
indented and `maxOutputBytes`, which the command line sets with `--max-output-bytes`; the
output is UTF-8 and deterministic.

UBL is two document types where the semantic model has one. `WriterOptions.document()` chooses:
`AUTO`, the default, writes a credit note where BT-3 is one of the codes the artefacts admit on
a credit note and not on an invoice; `INVOICE` and `CREDIT_NOTE` name it, `report.syntax()` says
which was written.

`WriteReport.isComplete()` says whether this document lost anything, and every note names the
semantic path or the element and the reason: a term of an extension the syntax binding does not
cover, a group the schema admits fewer of than the document carries, the `extensions` member, an
element the syntax requires that the document does not state, and a character XML 1.0 has no
place for — a text value may hold any Unicode scalar value (`SPEC.md` 12.6), so the writer
leaves it out, writes the rest and reports `CHARACTER_NOT_REPRESENTABLE`.

```java
List<String> lost = new ArrayList<>();
if (!report.isComplete()) {
    for (WriteNote note : report.notes()) {
        lost.add(note.toString());   // kind, semantic path and reason
    }
}
```

What the two writers produce over the corpus and the examples, measured against the artefacts
and against reading the result back, is
[`cii-roundtrip.md`](../conformance/writers/cii-roundtrip.md) and
[`ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md).

## Writing the XR representation

`XrExporter` writes an ESJ document back as an XR document — the same semantic XML the
stylesheets produce, and the input `XrImporter.fromXr` reads. It is not a syntax writer.

```java
XrExporter exporter = new XrExporter();
byte[] xr = exporter.toXr(document);

ExportResult exported = exporter.toXrWithReport(document);
List<ExportNote> left = exported.report().notes();
```

The element names, the attributes the supplementary components are written as and the order of
sibling elements come from the XRechnung semantic model schema of the same KoSIT project:
`esj-xr/src/main/resources/de/bsnsoft/esj/xr/xr-elements.tsv` is derived from it,
checked in and derived again on every build to be compared. The same document written twice
gives the same bytes; every instance of the corpus is imported, written and read again.

The report names what the XR representation has no place for: a value at a path it has no
element for (`NO_ELEMENT`), content carrying a character XML 1.0 cannot hold
(`NOT_REPRESENTABLE`), a supplementary component the element cannot carry (`COMPONENT_DROPPED`)
and the `extensions` subtree (`EXTENSIONS_DROPPED`). `source` is dropped without a note: the
importer writes a new one when it reads the result back.

## Rendering an invoice for a reader

`esj-render` has two renderers. Both take a `RenderOptions` — the language, and the page size,
layout, template and page bound only the PDF one uses — and neither changes the document.

```java
HtmlRenderer html = new HtmlRenderer();
String german = html.render(document);
String english = html.render(document, RenderOptions.in(RenderLanguage.ENGLISH));

RenderResult result = html.renderWithReport(document, RenderOptions.defaults());
List<ExportNote> notLeftBehind = result.report().notes();

PdfRenderer pdf = new PdfRenderer();
byte[] a4 = pdf.render(document);
byte[] usLetter = pdf.render(document,
        RenderOptions.in(RenderLanguage.ENGLISH).on(PageSize.LETTER));
byte[] businessLetter = pdf.render(document, RenderOptions.defaults().layout(Layout.LETTER));
```

The HTML rendering is the document written as the XR representation and handed to
`xrechnung-html.xsl` of the KoSIT visualization; its report is `XrExporter`'s and the
`extensions` subtree never reaches it. The PDF is drawn with PDFBox as PDF/A-3b, in one of two
layouts: `Layout.GENERIC`, the shape of the semantic model, or `Layout.LETTER`, a business
letter. `RenderOptions.with(template)` brings a letterhead, a logo, colours, fonts, margins,
places for the terms of a model extension and a layout of its own, which an explicit
`layout(…)` overrules ([`templates.md`](templates.md)); `withMaxPages(int)` bounds a rendering
at 2 000 pages and throws `RenderLimitException` beyond it. Two runs give the same bytes, an
edition the registry does not describe is refused with `IllegalArgumentException`, and
[`rendering.md`](rendering.md) is what each shows.

## Validating against the official artefacts

`esj-syntax` asks whether an XML invoice is what the rules of its profile say it should be: its
XML Schema, the EN 16931 Schematron of CEN/TC 434 and the Schematron of the core invoice usage
specification the document names in BT-24. Those artefacts travel with the repository as
validation packs under `packs/` and are executed as data, never reimplemented.

```java
SyntaxReport report = SyntaxValidator.validate(Files.readAllBytes(invoice));

Verdict verdict = report.verdict();
List<SyntaxFinding> fatal = report.fatal();
List<SyntaxFinding> warnings = report.warnings();
```

A finding names the rule identifier as the artefact reports it, its category, the level the
artefact set, the message and location it wrote, and the pack and component that produced it.
The verdict is `INVALID` when any finding is fatal. The order is engine, category, identifier,
location, so two runs over the same bytes produce the same report.

```java
SyntaxOptions options = SyntaxOptions.defaults()
        .withMaxInputBytes(16L * 1024 * 1024)
        .withMaxRuntime(Duration.ofSeconds(30));
SyntaxReport report = SyntaxValidator.validate(Files.readAllBytes(invoice), options);

List<ComponentRun> ran = report.ran();
List<SkippedComponent> skipped = report.skipped();
Optional<String> profileNote = report.profileNote();
boolean profileRulesSkipped = report.profileRulesSkipped();
```

`profileNote` is set where a component of the pack was left out because of the profile the
document names, and `profileRulesSkipped` is the same fact without the sentence; `ran` and
`skipped` say which, and [`validation.md`](validation.md) has the two cases.

The message of a schema finding is the platform validator's, in the default locale of the
virtual machine; the rule identifier in `code` is read off the front of it and is the same
everywhere. An application whose reports travel fixes its locale.

A rule set that stops over a document is a fatal finding of that component with the code
`ARTEFACT-STOPPED`, not a `PackException`; its `ComponentRun` stays in `ran()` and carries
`stopped() == true`. The exception is kept for a pack that cannot be read or an artefact that
will not compile. Reaching either limit ends the run in a `SyntaxLimitException` rather than in
a verdict — `budget()` names the budget where the limit was the time — and a document of a
syntax no pack binds raises `SyntaxNotSupportedException`.

Which packs are available, and under which licences:

```java
for (Pack pack : Packs.bundled()) {
    String identity = pack.directory();
    SortedSet<String> licenses = pack.licenses();
}
Pack fromDisk = Packs.fromDirectory(directory);
PackSource source = fromDisk.source();          // SUPPLIED
```

`source()` is the one thing about a pack that the pack does not get to say: everything else a
report prints is read out of its manifest. Compiling a schema library and a rule set does not
depend on the document, so each artefact is compiled once per process and shared; a
`ComponentRun` reports the run and the compilation apart.

## Checking the business rules

`esj-rules` answers the third question, and the only one that can be put to a document that was
never XML: do the business rules of EN 16931-1, clause 6.4 hold? They are written over business
terms rather than over the XPath of a syntax, so one rule serves UBL, CII and a native document.

```java
RuleEngine engine = En16931.engine(Registry.en16931());

List<RuleFinding> findings = engine.evaluate(document);
boolean rejected = findings.stream().anyMatch(RuleFinding::fatal);
```

A finding carries the rule identifier as its `code`, the category (`EN_BR`, `EN_DEC` or
`EN_CL`), the severity the rule declares, a message naming the terms and the values seen, the
semantic paths the rule read, and the pack identifier and version together with the engine name
`native`. `RuleFinding.ORDER` sorts a report so that two runs over one document produce the same
list. Compiling a pack is the expensive part and does not depend on the document, so an engine
is compiled once, evaluated many times, and is immutable and safe to share between threads.
`En16931.engine` is the pack this build carries, brought together from three things:

```java
RulePack pack = RulePacks.bundled(En16931.PACK_ID, En16931.VERSION);
RuleEngine other = RuleEngine.compile(pack, Registry.en16931(),
        CodeLists.bundled(pack), En16931.javaRules());
```

The third argument is what a caller supplies for a pack it did not write. **The manifest names
the classes of the rules written in Java; it does not load them**, so `compile` checks that
exactly the named instances arrived. A pack that names a code list it has no snapshot for does
not compile either, because a membership test against a list nobody loaded would pass every
code. Either way the refusal is a `RulePackException` — a fault of the installation, never a
statement about an invoice. A document the engine cannot decide a rule on produces an
informational finding.

What the pack covers, what it does not and what it comes to beside the official artefacts are
[`../conformance/rules/coverage.md`](../conformance/rules/coverage.md),
[`../conformance/rules/not-applicable.md`](../conformance/rules/not-applicable.md) and
[`../conformance/rules/ledger.md`](../conformance/rules/ledger.md), and
[`../rules/README.md`](../rules/README.md) is the rule language. These findings are a layer of
their own and never ESJ conformance (`SPEC.md` 9.4); CEN/TC 434 and the core invoice usage
specifications remain the authority on the rules.

## Building from source

The build is Maven and needs a JDK 17, 21 or 25; the compiler targets release 17.

```text
mvn -B verify
```

That compiles the eleven modules, runs every test, builds the source and Javadoc jars, and writes
the self-contained `esj-cli/target/esj.jar` that `bin/esj` runs. Warnings are errors: `-Xlint:all`
with `failOnWarning`, and Javadoc with `doclint` on every group and `failOnWarnings`. The
generated artefacts — the sources under `esj-typed/src`, the code list enums under
`esj-invoice/src` and the model schema of each edition under `schema/` — are checked in and
regenerated from the registries with

```text
mvn -B -Pgenerate -pl esj-generator -am process-classes
```

which must leave the working tree unchanged; `.github/workflows/ci.yml` runs both and fails
when a generated file differs.

## The three version numbers

| Number | Where it is written | Value today |
|---|---|---|
| format version | the `version` member of every document, `SPEC.md` | `0.1` |
| semantic model edition | the `semanticModel` member of every document, the `edition` member of the registry | `EN16931-1:2017+A1:2019/AC:2020`, `EN16931-1:2026` |
| artifact version | `pom.xml` | `0.9.2-SNAPSHOT` |

The **format version** is the version of `SPEC.md`, not of the semantic model. It follows
`MAJOR.MINOR`, and until 1.0 any version may change the format in incompatible ways. A reader
that does not implement the version it finds rejects the document rather than guessing.

The **semantic model edition** is the edition of EN 16931-1 the paths refer to:
`EN16931-1:2017+A1:2019/AC:2020` by default, `EN16931-1:2026` where the build carries its registry.
A reader needs none, so a document of an edition without one is read, canonicalized and hashed, and
the model layers report `ESJ-L2-EDITION-UNKNOWN` (`SPEC.md` 9.2). The extension registry is
versioned by the specification it describes, `XRechnung 3.0.2`.

The **artifact version** is the Maven version every module shares; it says nothing about the
format. From 0.9.0 the libraries are published under `de.bsnsoft.esj` on Maven Central
([`releasing.md`](releasing.md)). All three are constants in `de.bsnsoft.esj.Esj` where a program
needs them: `FORMAT`, `VERSION`, `SEMANTIC_MODEL`, `MEDIA_TYPE` and `FILE_EXTENSION`.
