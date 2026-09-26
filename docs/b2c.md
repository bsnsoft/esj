# The B2C extension

*Part of [EN16931 Semantic JSON](../README.md).*

An EN 16931 invoice is a net invoice. This extension records the gross figures a consumer was
shown; the invoice stays net. Module `esj-b2c`, package `de.bsnsoft.esj.b2c`.

## The four terms

| Path | Term | Semantic data type |
|---|---|---|
| `/BG-25/<n>/BT-B2C-001` | Displayed gross unit price | Unit Price Amount |
| `/BG-25/<n>/BT-B2C-002` | Displayed gross line total | Amount |
| `/BG-25/<n>/BT-B2C-003` | Displayed line VAT amount | Amount |
| `/BT-B2C-010` | Displayed invoice gross total | Amount |

Registry [`../model/b2c/0.1.json`](../model/b2c/0.1.json), namespace `B2C`, edition
`ESJ-B2C 0.1`, which imports the core edition. A term present means its figure was shown to or
agreed with the customer. The registry defines no arithmetic relation between these terms, and
none between one of them and a core term: BT-146, BT-131, BG-23, BT-112, BT-114 and BT-115 keep
their EN 16931 meaning, BT-114 as the invoice rounding amount. What computes is a policy below.

## Writing the figures

`Gross` writes into the builder the typed editor of the core model writes into, so one document
comes out of both. The core editor writes the invoice lines; the overlay writes gross figures
into the lines it finds and refuses an index it finds none at. `B2c.of(document)` reads them
back, `B2c.edit(...)` is the overlay, `B2c.registry()` the core registry with the extension.

```java
Gross gross = Gross.on(invoice);
gross.line(0).displayedGrossUnitPrice("99.99");
gross.displayedGrossTotal("99.99");
B2cInvoice shown = B2c.of(invoice.document());
shown.displayedGrossTotal();                     // Optional[99.99]
shown.lines().get(0).displayedGrossUnitPrice();  // Optional[99.99]
```

## Deriving the net invoice

| Policy | Reads | Derives |
|---|---|---|
| `GROSS_UNIT_AUTHORING` | BT-B2C-001 per line | BT-146 from the gross unit price at the line's rate |
| `GROSS_LINE_AUTHORING` | BT-B2C-002 per line | the line net amount, and BT-146 from it |
| `GROSS_TOTAL_AUTHORING` | BT-B2C-010 | a share of the agreed total per line, in proportion to what the lines come to, remainder on the last |

```java
AuthoringReport report = gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
```

Each then runs `Totals.STANDARD` for the line net amounts, the VAT breakdown and BT-106 to BT-115
([java-api.md](java-api.md#deriving-the-totals)), and adds one step: where the invoice states
BT-B2C-010, BT-114 takes the difference that carries BT-112 to it, and BT-115 becomes BT-B2C-010
less the paid amount (BT-113). 99.99 at 19 per cent is `84.02521` a unit at the default scale,
`84.03` a line, VAT `15.97`, BT-112 `100`, BT-114 `-0.01`; where the difference is zero, no BT-114
is written and the report carries no rounding step.

No policy writes a term of the extension; `GROSS_TOTAL_AUTHORING` puts its per-line shares in the
report and in no BT-B2C-002, and refuses a line that carries one. `AuthoringReport` carries the
policy, every value written, every price cut, the BT-114 step, notes and the `DerivationReport`.

## Options

| Option | Default | Without it |
|---|---|---|
| `withNetPriceScale(int)` | 6 | — (values below 6 are refused) |
| `withLineAllowancesAndCharges(boolean)` | false | a line with BG-27 or BG-28 is refused |
| `withBaseQuantity(boolean)` | false | a line with BT-149 other than 1 is refused |
| `withMaxRoundingAmount(BigDecimal)` | a cent per line and per VAT breakdown | a larger difference is refused, not written into BT-114 |

```java
GrossAuthoring policy = GrossAuthoring.GROSS_UNIT_AUTHORING.with(
        AuthoringOptions.standard().withBaseQuantity(true));
```

With `withLineAllowancesAndCharges(true)` a displayed unit price is the price before the line
allowances and charges, and a displayed line total, or a share of an agreed total, the figure
after them; with `withBaseQuantity(true)` a unit price is the price of one base quantity. A policy
refuses rather than guesses: `PolicyPreconditionException` names the precondition, the term and
the group instance.

## What a policy guarantees

`B2cConsistency.check(document, policy)` returns `List<RuleFinding>` in the shape of the rule
engine — category `B2C`, engine `native`, pack `b2c/0.1`.

| Code | Policies | Checks |
|---|---|---|
| `B2C-01` | all | BT-115 and BT-113 come to BT-B2C-010 |
| `B2C-02` | `GROSS_UNIT_AUTHORING` | BT-146 is BT-B2C-001 at the line's rate, cut to the policy's scale |
| `B2C-03` | all | BT-131 is what the line's price, quantity, charges and allowances come to |
| `B2C-04` | `GROSS_LINE_AUTHORING`, `GROSS_TOTAL_AUTHORING` | BT-131 is BT-B2C-002 at the line's rate |

These are the guarantees of a policy and no relations the extension states, which is why the
policy is an argument; a check the document carries no figure for is an information finding.

## Decimals

Unit Price Amount is of unlimited scale (EN 16931-1, 6.5, AC 8). A price the caller gives is used
as it stands; a price a policy computes is kept to `netPriceScale` fraction digits, the cut in
`AuthoringReport.cuts()`. Every intermediate is exact, an Amount is rounded half up to two
decimals once at the result, the totals are sums of rounded amounts, and a line net amount a
policy rounds itself is named in a note.

## The domain API

`GrossInvoice` wraps `Invoice.draft(profile)` and returns the same `InvoiceResult` and
`BuildReport`, with the `AuthoringReport` beside them. `esj-invoice` does not depend on `esj-b2c`.

```java
GrossResult result = GrossInvoice.draft(Profile.EN16931)
        .edit(draft -> draft
                .number("RE-2026-0731")
                .issued(LocalDate.of(2026, 4, 14))
                .currency(CurrencyCode.EUR)
                .seller(Party.named("Beispiel Handels GmbH").vatId("DE123456789")
                        .address("Werkstrasse 8", "10117", "Beispielstadt", Country.DE))
                .buyer(Party.named("Jana Muster")
                        .address("Lindenweg 4", "20095", "Musterstadt", Country.DE))
                .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                        PaymentTerms.days(14, "Payable within 14 days without deduction.")))
        .line(GrossItem.perUnit(Line.of("Shower fitting SF-20")
                .quantity(1, Unit.PIECE).vat(Vat.standard(19)), "99.99"))
        .displayedGrossTotal("99.99")
        .build(GrossAuthoring.GROSS_UNIT_AUTHORING);
```

`GrossItem.perUnit`, `GrossItem.perLine` and `GrossItem.of` say which figure was shown;
`displayedLineVatAmount(...)` adds BT-B2C-003. `draft()` and `edit(...)` are the draft itself.

## On the command line

```console
$ esj validate --extension b2c examples/b2c-gross.esj.json
...
  official artefacts over the written CII:
    4 terms of ESJ-B2C 0.1 stay in the ESJ document by design: BT-B2C-010, BT-B2C-001, BT-B2C-002, BT-B2C-003
...
VALID
$ esj convert --to cii --extension b2c --fail-on-loss --out invoice.cii.xml \
    examples/b2c-gross.esj.json
info: 4 terms of ESJ-B2C 0.1 stay in the ESJ document by design: BT-B2C-010, BT-B2C-001, BT-B2C-002, BT-B2C-003
$ echo $?
0
$ esj render examples/b2c-gross.esj.json --extension b2c \
    --template examples/templates/gross.json --out invoice.pdf
```

`--extension b2c` loads the registry, so every command checks, lists and renders the four terms;
`--extension xrechnung,b2c` loads both. Without it the four paths are `ESJ-L2-NOT-CHECKED`, the
reason is `extension-registry-missing` and the verdict is `INDETERMINATE`.

`model/b2c/0.1.json` declares `"transport": "none"`: these terms are bound by no transport syntax by
design, so the XML written from such a document is the whole invoice. `validate` runs the official
artefacts over it, counts the row and names the terms left behind ([validation.md](validation.md#verdict-and-exit-code));
`convert`, `embed` and `render --embed cii` name them on one `info:` line and count them as no
loss, and `--fail-on-loss` lets the conversion through ([cli.md](cli.md#writing-ubl-and-cii)). In
Java the writer knows the declaration when it is handed the registry:
`WriterOptions.builder().extensions(List.of(Registry.b2cExtension()))`.

## In a syntax and in a rendering

The extension travels in ESJ. A hybrid PDF loses nothing: the ESJ document it carries beside the
invoice XML holds them, and the container still agrees with itself ([pdf-output.md](pdf-output.md#the-esj-document-beside-the-invoice)).
The baseline PDF rendering prints the figures marked `(B2C)` and never inside a core totals row; the
baseline HTML rendering, built from the XR representation, has no element for them and names every
one in its report; a branded template gives them places of their own
([templates.md](templates.md), `examples/templates/gross.json`).
