# Examples

Twelve synthetic ESJ documents in pretty form (SPEC.md section 7.7): canonical member order,
canonical path order, two-space indentation, LF line endings, UTF-8. Eleven name the 2017 edition of
the semantic model, `edition-2026.esj.json` the 2026 one. [`templates/`](templates/README.md) holds
four branded render templates. [`invalid/`](invalid/README.md) holds documents a reader must reject,
one per kind of error (their variants:
[`conformance/fixtures/`](../conformance/fixtures/README.md)).

Every document here is structurally complete under validation layers L1 to L3 of SPEC.md,
`b2c-gross.esj.json` with `model/b2c/0.1.json` loaded beside the core registry. The CEN business
rules of EN 16931-1, clause 6.4 are a separate layer (SPEC.md section 9.4). The verdict below is
what `esj validate <file>` answers once that layer and the official artefacts over the written CII
have run as well. A test of `esj-cli` runs it over every file here and holds it to this table.

| File | Verdict | What it shows |
|---|---|---|
| `smallest-valid.esj.json` | `VALID` | the smallest invoice this tool accepts through both syntaxes: 28 terms, and dropping any one of them ends the verdict through one of the two |
| `standard-invoice.esj.json` | `VALID` | a typical business invoice: seller and buyer with addresses and contacts, payment instructions with one credit transfer, one VAT breakdown, three lines |
| `multiple-lines.esj.json` | `VALID` | ten lines with periods, item attributes and classifications, delivery information, an embedded attachment, two VAT rates |
| `allowances.esj.json` | `VALID` | allowances on the document level and on a line, two VAT rates |
| `charges.esj.json` | `VALID` | charges on the document level and on a line, a seller tax representative, a payment card |
| `self-billed.esj.json` | `VALID` | invoice type code 389, a payee, a direct debit mandate, one reverse charge line |
| `credit-note.esj.json` | `VALID` | invoice type code 381 with a preceding invoice reference |
| `minimal.esj.json` | `INVALID` | only the mandatory terms of the model, one line, one VAT breakdown — a document about structure, see below |
| `extended.esj.json` | `INVALID` | the `values` of `minimal.esj.json` plus `extensions` and `source`, built to trap the canonical-form mistakes of SPEC.md section 7.6 |
| `extension-depth.esj.json` | `INVALID` | the `values` of `minimal.esj.json` plus an `extensions` subtree nested exactly 32 levels deep, the deepest the reader limits of SPEC.md section 12.2 accept |
| `b2c-gross.esj.json` | `INDETERMINATE` | a three-line consumer invoice carrying the four terms of the B2C extension beside the net core terms |
| `edition-2026.esj.json` | `INDETERMINATE` | EN 16931-1:2026: payment terms with an early payment discount and a late payment penalty, a charge collected on behalf of a third party, line-level delivery information and preceding invoice reference, and an invoice issue time |

## The three documents about structure

`minimal.esj.json`, `extended.esj.json` and `extension-depth.esj.json` show the *shape* of a
document, not an invoice anybody would send. Each carries the mandatory terms of the registry and
nothing else. The business rules reject it, because they ask for terms the model leaves optional.
`esj validate` reports that and leaves with exit code 1.

[`../conformance/rules/corpus.md`](../conformance/rules/corpus.md) records rule by rule what this
repository's EN 16931 pack says about them, and a test holds it to the figures. All three fall to
the same five rules, because `extended` and `extension-depth` are the `values` of `minimal` with a
subtree added. `BR-48`: the single VAT breakdown states no BT-119, and the rule asks for a VAT
category rate in every breakdown unless the invoice is not subject to VAT. `BR-Z-02` and `BR-Z-05`:
the zero-rated line would need a seller VAT or tax registration identifier and a line VAT rate of 0.
`BR-CO-26`: no seller identifier of any kind is stated. `BR-CO-25`: an amount due for payment is
stated with neither a payment due date nor payment terms.

Every term those rules ask for is optional in the model. Adding any of them would cost exactly what
each file is an example of. `smallest-valid.esj.json` makes that trade: `minimal.esj.json` plus the
four terms the rules need (payment due date, seller VAT identifier, VAT category rate, line VAT
rate) and BT-110, which UBL requires beside a VAT breakdown. It is `VALID` by default and under
`--via ubl`, and removing any of its 28 terms ends the verdict under one of the two. Transcripts in
`README.md` and `docs/cli.md` use `standard-invoice.esj.json` or a corpus instance.

The two `INDETERMINATE` rows are no faults either. Nothing fatal is found, and part of the complete
check does not run, which is what exit code 9 says
([`../docs/validation.md`](../docs/validation.md)). For `b2c-gross.esj.json` no registry of the
namespace B2C is loaded without `--extension b2c` (`extension-registry-missing`). For the 2026
edition, no official artefacts are published at all (`no-artefacts-for-edition`).

## What holds for all twelve

All mandatory business terms of the registry are present in every group instance. The codes come
from the lists the registry names for their terms. The index rules of SPEC.md section 5.3 hold. The
arithmetic adds up wherever the terms it relates are present: line net amounts sum to BT-106,
allowances and charges to BT-107 and BT-108, BT-109 = BT-106 − BT-107 + BT-108, every VAT breakdown
that states a rate has a tax amount that follows from its taxable amount and that rate, BT-112 =
BT-109 + BT-110 where BT-110 is present, and BT-115 = BT-112 − BT-113 + BT-114.

Both value shapes of SPEC.md section 6.1 occur here. Most values are plain JSON strings, because
most business terms carry nothing but their content. A value object carries a supplementary
component: the seller and buyer electronic addresses and party identifiers with their `scheme`
(`standard-invoice.esj.json`, `self-billed.esj.json`, `multiple-lines.esj.json`), the item
classification identifier with `scheme` and `schemeVersion`, and the one embedded attachment with
`mimeCode` and `filename` (`multiple-lines.esj.json`). No document writes an object without a
component, which SPEC.md section 6.1, rule 3 forbids. No document writes a semantic data type
anywhere: the registry records it once per term, and a document repeats nothing of it.

Beside every document lies its `*.canonical.esj.json`, the same content in canonical form (SPEC.md
section 7): one line, UTF-8, no insignificant whitespace, no trailing newline. These are golden
files. Canonicalizing the pretty document MUST reproduce the bytes of its canonical twin. The two
digests of SPEC.md section 8 are taken over those bytes.

## The invented and the real

The parties are invented: `Example GmbH`, `Muster AG`, `Beispiel Handels GmbH`, the consumer
`Jana Muster` of `b2c-gross.esj.json`, streets that do not exist, and VAT identifiers that are
syntactically plausible but fictitious. The account identifier `DE89370400440532013000` is the IBAN
example value published for exactly this purpose. Mail addresses use the reserved `.invalid` top
level domain. The payment card number of `charges.esj.json` is masked to its last four digits, which
is what BT-87 may show and all the official artefacts accept.

The identification schemes are real. A `scheme` here is a real code of the list the model draws it
from, and its value has the structure that code's register gives it. `0088` is the GS1 Global
Location Number, so the identifiers carrying it are 13 digits with a valid check digit. `EM` is the
CEF EAS code for an electronic mail address. Where no register fits, the example writes no scheme
rather than a plausible-looking one. The legal registration identifier `HRB 12345` of
`standard-invoice.esj.json` is a German commercial register number, the ISO 6523 ICD list holds
nothing that says so, and the registry declares the component optional. No layer of SPEC.md catches
a scheme code that does not fit its value: that is a business rule error (sections 6.3 and 10). So
the examples must not carry one.

## What three of them are really for

`extended.esj.json` is the one document with both `extensions` and `source`. It reaches the part of
the canonical form nothing else here reaches: SPEC.md section 7.6, the rules for the free JSON
inside `extensions`. Its subtree is a trap on purpose. Two member names sort differently in Unicode
code point order (which SPEC.md requires) and in UTF-16 code unit order (which RFC 8785 requires and
most JCS libraries implement). An implementation that reaches for a JCS library without checking
produces different bytes and a different document digest. SPEC.md section 7.6, rule 2 has to rewrite
numbers from their lexical form: `1e21` becomes `1000000000000000000000`, `1e-6` and `1e-7` become
`0.000001` and `0.0000001`, and a negative zero written once as an integer and once as a fraction
becomes `0` both times. Two numbers no double holds must be copied digit for digit:
`12345678901234567890` and `1.0000000000000001`. An implementation that canonicalizes numbers the
way RFC 8785 does writes `1e+21`, `12345678901234567000` and `1` for three of those, and a different
document digest. The subtree also holds an array whose element order must survive, and the three
literals. Its `source` carries both members. The `sha256` there is the SHA-256 of a fictitious
source file and corresponds to nothing in this repository. The numbers keep their input spelling in
this pretty file. SPEC.md section 7.7 fixes the member order inside `extensions` (the pretty files
here are sorted exactly as their canonical twins), but not the spelling of a number. This file is
written to be canonicalized, not to be compared byte for byte with another writer's output. It has
exactly the `values` of `minimal.esj.json` and names the same edition, so the two have the same
semantic digest and different document digests: SPEC.md section 8.4 in one pair of files. The
semantic digest is taken over the edition and the values together (SPEC.md section 8.2), so the pair
holds only while both name the same edition.

`extension-depth.esj.json` exists for one number. SPEC.md section 12.2 bounds the nesting inside
`extensions` at 32 levels and counts the value of an owner-token member as level 1. Its subtree is
32 nested arrays with a string at the bottom. A reader with the reference configuration accepts it;
a reader that starts counting one level earlier or later does not.
`invalid/extension-depth-33.esj.json` is the same document with one array more, and a reader must
reject it with `ESJ-L1-LIMIT`. The pair checks the counting base.

`b2c-gross.esj.json` is the one document with terms of an extension registry in `values` (SPEC.md
sections 5.6 and 11.1): `/BT-B2C-010` and, per line, `/BG-25/<n>/BT-B2C-001` to `BT-B2C-003`, the
gross figures a consumer was shown. They stand beside the net core terms and replace none of them.
The registry `model/b2c/0.1.json` defines no arithmetic relation between them and anything else:
that the displayed totals of this invoice agree with BT-112 is a property of this invoice, not a
rule. Load that registry beside the core one to check the four terms. With
`esj validate --extension b2c` the layers measure them and find nothing. Without the option, the
ten paths are `ESJ-L2-NOT-CHECKED` ([`../docs/b2c.md`](../docs/b2c.md)).

`edition-2026.esj.json` is the one document of the 2026 edition. It carries the seven business
groups and the semantic data type that edition adds, at the paths that edition gives them (BT-20
inside BG-33, the invoice line period inside BG-37), and its `BT-166` is a time with the offset it
is stated in. Its arithmetic follows that edition: BR-CO-16 adds the charges collected on behalf of
a third party, so BT-115 is BT-112 plus the BT-179 of BG-34. No official specification identifier is
published for the 2026 edition; its BT-24 continues the 2017 one. The files of that edition are
separable: `model/en16931/2026.paths` lists them, and the Maven profile `without-edition-2026`
builds without them, this document and its canonical twin included. The rule pack of the 2017
edition does not run over it; the pack `en16931-2026/0.1` does, and no official artefact
corroborates it ([`../docs/editions.md`](../docs/editions.md)).

## java

[`java/HybridInvoice.java`](java/HybridInvoice.java) builds, renders and embeds an invoice as
Factur-X, reads it back and checks the rules in three places. `esj-cli` tests compile and run it.

```sh
java -cp "esj-cli/target/esj.jar:esj-invoice/target/classes" examples/java/HybridInvoice.java .
```
