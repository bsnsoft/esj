# Synthetic instances for measuring

*Part of [EN16931 Semantic JSON](../../README.md).*

The conformance corpus answers whether a document is read *correctly*. It says nothing about
what reading a large one costs: its largest instance is under half a mebibyte, while invoices
of tens of megabytes with hundreds of thousands of lines exist in the field. This directory
holds the generator that makes instances of that class, so that the limits of `SPEC.md`
section 12.2, the `--limits large` profile of the command line and the deployment tables are
measured rather than guessed.

`generate.py` takes one corpus invoice and grows it: the first invoice line is replicated with
distinct line identifiers, and every total is recomputed from the replicated lines, so the
result stays arithmetically consistent — BT-131 = BT-129 × BT-146 per line, BT-106 the sum of
them, BT-109 = BT-106, one VAT breakdown with BT-116 = BT-109 and BT-117 = BT-116 × rate,
BT-110 = BT-117, BT-112 = BT-109 + BT-110 and BT-115 = BT-112. A source with more than one VAT
rate, or with an allowance or a charge at document or line level, is refused by name rather
than grown into a document whose totals are wrong.

It is plain Python 3 with no dependencies, it parses nothing into a tree, and it writes the
file one invoice line at a time, so an instance of three hundred thousand lines costs a
constant amount of memory to produce.

## The two sources

The instances of `01.01a` carry one VAT rate, no allowances and no charges, in both syntaxes,
and they are therefore the two sources these sizes are generated from:

| Syntax | Source |
|---|---|
| UBL | `conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml` |
| CII | `conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml` |

Both grow into the same semantic document: 50 lines of either syntax import to the same 791
values and the same BT-106, which is the litmus test of the project at a size no corpus
instance reaches.

## The three reference sizes

The numbers below are the sizes the measurements of this project are taken at — what a run
costs in wall clock and peak resident set at a given heap ceiling. Two of them are stated as a
number of lines and one as a file size, because they answer two different questions: how many
values a document carries, and how many bytes have to pass the front door before anyone
knows.

| Reference | What it stands for |
|---|---|
| 50 MB / 100 000 lines | a large invoice that a service still meets in ordinary work |
| 80 MB / 300 000 lines | the largest class reported from the field |
| 150 MB | past that class: what a bound, not a heap, should stop |

One invoice line of this source is about 1.5 kB of UBL and about 2.6 kB of CII, so a line count
and a file size are not interchangeable between the two syntaxes; `--lines` fixes the semantic
size and `--target-bytes` fixes the file size, and each measurement names which of the two it
was taken at.

## The two classes of line

A corpus line is a rich line: an item with a description, a classification, attributes, a
period, a gross and a net price. Real invoices of the size this project sizes for are not made
of those. `--dense` replicates the smallest line EN 16931 admits instead — the line identifier,
the invoiced quantity with its unit, the line net amount, the item name, the classified VAT
category and the item net price, and nothing else — with the same totals arithmetic on top.

| Line | UBL | CII |
|---|---:|---:|
| the line of `01.01a` | 1 508 bytes | 2 516 bytes |
| `--dense` | 472 bytes | 952 bytes |

80 MB of dense UBL is therefore 177 716 invoice lines against 55 884 of the corpus line, which
is the class "eighty megabytes with several hundred thousand lines" actually describes. The two
classes answer different questions — how many bytes have to pass the front door, and how many
values the document carries — and `docs/deployment-measurements.md` measures both and names
which is which.

## Regenerating them

```text
python3 conformance/scale/generate.py \
    --source conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml \
    --lines 100000 --out conformance/scale/out/ubl-100k-lines.xml

python3 conformance/scale/generate.py \
    --source conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml \
    --lines 300000 --out conformance/scale/out/ubl-300k-lines.xml

python3 conformance/scale/generate.py \
    --source conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml \
    --target-bytes 150M --out conformance/scale/out/ubl-150mb.xml
```

```text
python3 conformance/scale/generate.py \
    --source conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml \
    --dense --target-bytes 80M --out conformance/scale/out/ubl-80mb-dense.xml
```

`--syntax cii` is not needed: the syntax is recognized from the root element, and the same
three lines with the `uncefact` source write the CII instances. `--target-bytes` takes a plain
number of bytes or one with a `k`, `M` or `G` suffix for multiples of 1024, and grows the
document until the file is at least that large.

## A fourth shape: wide values rather than many of them

A reader costs what a *rendering* costs, and that is pages rather than values: one item name of
a megabyte is hundreds of pages of paper in a narrow column, while a document of the same size
spread over a hundred thousand ordinary lines is far fewer. `--text-bytes` makes the first
shape — it pads the item name of every generated line with that many bytes of filler words,
with the same `k`/`M`/`G` suffixes:

```text
python3 conformance/scale/generate.py \
    --source conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml \
    --lines 20 --text-bytes 1000k --out conformance/scale/out/ubl-20-wide.xml
```

Twenty lines of a megabyte each are a 21 MB document that every bound of `--limits default`
lets through — the per-string bound is 1 MiB — and several thousand pages of PDF. That is the
document the *Rendering* section of
[`docs/deployment-measurements.md`](../../docs/deployment-measurements.md) is measured on, and
the reason the section exists. Keep the padding under the per-string bound of the profile the
measurement uses, or the importer leaves the value out and says so.

Reading one of these through the command line needs the large profile, and a heap to match:

```text
java -Xmx1g -jar esj-cli/target/esj.jar convert --limits large \
    conformance/scale/out/ubl-100k-lines.xml > /dev/null
```

Without `--limits large` the tool refuses the file at the front door and leaves with exit code
7, naming the bound and the switch that raises it. That is the intended answer: the default
profile is written for input from strangers.

## Why they are not checked in

They are derived material: a hundred and fifty megabytes that `generate.py` and one corpus file
reproduce byte for byte at any time. `conformance/scale/out/` is in `.gitignore`, the build
does not copy it onto any classpath, and nothing in the repository depends on a generated file
existing. What is checked in is the generator, this page, and one test that runs the generator
for fifty lines and imports the result, so that a change which breaks the arithmetic is caught
by the ordinary build rather than by a measurement nobody repeated.
