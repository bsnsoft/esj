# Examples

Twelve synthetic ESJ documents in pretty form (SPEC.md section 7.7): canonical member order,
canonical path order, two-space indentation, LF line endings, UTF-8. Eleven name the 2017 edition of
the semantic model, `edition-2026.esj.json` the 2026 one. Beside them,
[`templates/`](templates/README.md) holds four branded render templates and
[`invalid/`](invalid/README.md) documents that must be rejected, one per kind of error.

Every document here is structurally complete under validation layers L1 to L3 of SPEC.md,
`b2c-gross.esj.json` with `model/b2c/0.1.json` loaded beside the core registry. The CEN business
rules of EN 16931-1, clause 6.4 are a separate layer (SPEC.md section 9.4), so the verdict below is
what `esj validate <file>` answers once that layer and the official artefacts over the written CII
have run as well. A test of `esj-cli` runs the command over every file of this directory and holds
it to this table.

| File | Verdict | What it shows |
|---|---|---|
| `smallest-valid.esj.json` | `VALID` | the smallest invoice this tool accepts: 27 terms, and dropping any one of them ends the verdict |
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

`minimal.esj.json`, `extended.esj.json` and `extension-depth.esj.json` are examples of the *shape*
of a document, not of an invoice anybody would send. Each carries the mandatory terms of the
registry and nothing else, which is what makes it useful and what makes the business rules reject
it: the rules ask for terms the model leaves optional. `esj validate` reports that and leaves with
exit code 1, which is the honest answer about a document the rules do not accept.

[`../conformance/rules/corpus.md`](../conformance/rules/corpus.md) records rule by rule what the EN
16931 pack of this repository says about them, and a test holds it to the figures. All three fall to
the same five rules, because `extended` and `extension-depth` are the `values` of `minimal` with a
subtree added: the single VAT breakdown states no BT-119 although `BR-48` asks for a VAT category
rate in every breakdown unless the invoice is not subject to VAT; the zero-rated line would need a
seller VAT or tax registration identifier and a line VAT rate of 0 (`BR-Z-02` and `BR-Z-05`); no
seller identifier of any kind is stated (`BR-CO-26`); and an amount due for payment is stated with
neither a payment due date nor payment terms (`BR-CO-25`).

Every term those rules ask for is optional in the model, so adding any of them would cost exactly
the thing each file is an example of. `smallest-valid.esj.json` is where that trade is made instead:
it is `minimal.esj.json` plus the four terms the rules need — a payment due date, the seller VAT
identifier, the VAT category rate of the breakdown and the VAT rate of the line — and it is minimal
in its own right, because removing any of its 27 terms ends the verdict. A transcript in `README.md`
or `docs/cli.md` uses `standard-invoice.esj.json` or a corpus instance, which every engine accepts.

The two `INDETERMINATE` rows are not faults either. No syntax binds an extension term, so the
official artefacts are not run over `b2c-gross.esj.json`, and no artefacts are published for the
2026 edition at all; in both cases nothing fatal was found and part of the complete check did not
run, which is what exit code 9 says ([`../docs/validation.md`](../docs/validation.md)).

## What holds for all twelve

All mandatory business terms of the registry are present in every group instance, the codes come
from the lists the registry names for their terms, the index rules of SPEC.md section 5.3 are
obeyed, and the arithmetic adds up wherever the terms it relates are present: line net amounts sum
to BT-106, allowances and charges to BT-107 and BT-108, BT-109 = BT-106 − BT-107 + BT-108, every VAT
breakdown that states a rate has a tax amount that follows from its taxable amount and that rate,
BT-112 = BT-109 + BT-110 where BT-110 is present, and BT-115 = BT-112 − BT-113 + BT-114.

Both value shapes of SPEC.md section 6.1 occur here. Most values are plain JSON strings, because
most business terms carry nothing but their content. The value objects are the ones that carry a
supplementary component: the seller and buyer electronic addresses and party identifiers with their
`scheme` (`standard-invoice.esj.json`, `self-billed.esj.json`, `multiple-lines.esj.json`), the item
classification identifier with `scheme` and `schemeVersion`, and the one embedded attachment with
`mimeCode` and `filename` (`multiple-lines.esj.json`). No document writes an object without a
component, which SPEC.md section 6.1, rule 3 forbids; and no document writes a semantic data type
anywhere, because the registry records it once per term and a document repeats nothing of it.

Beside every document lies its `*.canonical.esj.json`: the same content in canonical form (SPEC.md
section 7) — one line, UTF-8, no insignificant whitespace, no trailing newline. These are golden
files. Canonicalizing the pretty document MUST reproduce the bytes of its canonical twin, and the
two digests of SPEC.md section 8 are taken over those bytes.

## The invented and the real

The parties are invented: `Example GmbH`, `Muster AG`, `Beispiel Handels GmbH`, the consumer `Jana
Muster` of `b2c-gross.esj.json`, streets that do not exist, and VAT identifiers that are
syntactically plausible but fictitious. The account identifier `DE89370400440532013000` is the IBAN
example value that is published for exactly this purpose. Mail addresses use the reserved `.invalid`
top level domain. The payment card number of `charges.esj.json` is masked to its last four digits,
which is what BT-87 may show and all the official artefacts accept.

The identification schemes are not invented. A `scheme` here is a real code of the list the model
draws it from, and the value beside it has the structure that code's register gives it: `0088` is
the GS1 Global Location Number, so the identifiers carrying it are thirteen digits with a valid
check digit, and `EM` is the CEF EAS code for an electronic mail address. Where no register fits,
the example writes no scheme rather than a plausible-looking one — the legal registration identifier
`HRB 12345` of `standard-invoice.esj.json` is a German commercial register number, for which the ISO
6523 ICD list holds nothing that says so, and the registry declares the component optional. A scheme
code that does not fit its value is a business rule error no layer of SPEC.md catches (sections 6.3
and 10), which is exactly why the examples must not carry one.

## What three of them are really for

`extended.esj.json` is the one document with both `extensions` and `source`, and it exists for the
part of the canonical form that nothing else here reaches: SPEC.md section 7.6, the rules for the
free JSON inside `extensions`. Its subtree is a trap on purpose. It contains two member names whose
relative order differs between Unicode code point order (which SPEC.md requires) and UTF-16 code
unit order (which RFC 8785 requires and which most JCS libraries implement), so an implementation
that reaches for a JCS library without checking produces different bytes and a different document
digest. It contains numbers written in spellings that SPEC.md section 7.6, rule 2 has to rewrite
from their lexical form — `1e21` becomes `1000000000000000000000`, `1e-6` and `1e-7` become
`0.000001` and `0.0000001`, and a negative zero written once as an integer and once as a fraction
becomes `0` both times — beside two numbers it has to copy digit for digit although no double holds
them: `12345678901234567890` and `1.0000000000000001`. An implementation that canonicalizes numbers
the way RFC 8785 does writes `1e+21`, `12345678901234567000` and `1` for three of those and produces
a different document digest. It contains an array whose element order must survive, and the three
literals. Its `source` carries both members; the `sha256` there is the SHA-256 of a fictitious
source file and corresponds to nothing in this repository. The numbers keep their input spelling in
this pretty file: SPEC.md section 7.7 fixes the member order inside `extensions` — the pretty files
here are sorted exactly as their canonical twins are — but not the spelling of a number, and this
file is written to be canonicalized rather than to be compared byte for byte with another writer's
output. Because it has exactly the `values` of `minimal.esj.json` and names the same edition, the
two have the same semantic digest and different document digests — SPEC.md section 8.4 in one pair
of files. The semantic digest is taken over the edition and the values together (SPEC.md section
8.2), so the pair holds only as long as both name the same edition.

`extension-depth.esj.json` exists for one number: SPEC.md section 12.2 bounds the nesting inside
`extensions` at 32 levels, and says that the value of an owner-token member is level 1. Its subtree
is 32 nested arrays with a string at the bottom, so a reader with the reference configuration
accepts it and a reader that starts counting one level earlier or later does not.
`invalid/extension-depth-33.esj.json` is the same document with one array more and must be rejected
with `ESJ-L1-LIMIT`. The pair is there so that the counting base is checked by a document rather
than read out of a sentence.

`b2c-gross.esj.json` is the one document with terms of an extension registry in `values` (SPEC.md
sections 5.6 and 11.1): `/BT-B2C-010` and, per line, `/BG-25/<n>/BT-B2C-001` to `BT-B2C-003`, the
gross figures a consumer was shown. They stand beside the net core terms and replace none of them,
and the registry `model/b2c/0.1.json` defines no arithmetic relation between them and anything else
— that the displayed totals of this invoice happen to agree with BT-112 is a property of this
invoice, not a rule. Loading that registry beside the core one is what makes the four terms
checkable: with `esj validate --extension b2c` the layers measure them and find nothing, without the
option the ten paths are `ESJ-L2-NOT-CHECKED` ([`../docs/b2c.md`](../docs/b2c.md)).

`edition-2026.esj.json` is the one document of the 2026 edition. It carries the seven business
groups and the semantic data type that edition adds, at the paths that edition gives them — BT-20
inside BG-33, the invoice line period inside BG-37 — and its `BT-166` is a time with the offset it
is stated in. Its arithmetic follows that edition: BR-CO-16 adds the charges collected on behalf of
a third party, so BT-115 is BT-112 plus the BT-179 of BG-34. No official specification identifier is
published for the 2026 edition; its BT-24 continues the 2017 one. The files of that edition are
separable — `model/en16931/2026.paths` lists them and the Maven profile `without-edition-2026`
builds without them — so this document and its canonical twin are absent from such a build, and the
rule pack of this repository, which is written for the 2017 edition, does not run over it.

## java

[`java/HybridInvoice.java`](java/HybridInvoice.java): an invoice built, rendered, embedded as
Factur-X, read back, rules checked in three places. A test of `esj-cli` compiles and runs it.

```sh
java -cp "esj-cli/target/esj.jar:esj-invoice/target/classes" examples/java/HybridInvoice.java .
```
