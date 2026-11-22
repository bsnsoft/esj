# What validation costs

*Part of [EN16931 Semantic JSON](../README.md).*

On an XML input, `esj validate` runs the official validation artefacts of the document's
profile: an XML Schema and two compiled Schematron rule sets. Running a Schematron over a
document is by a wide margin the expensive part of this tool.

All figures below come from one machine. Read each as an order of magnitude and a ratio between
the parts, not as a service level: the machine, the JDK and the heap all move it. To get a number
for your own deployment, run the same commands there.

## Method

| | |
|---|---|
| Measured on | Apple Silicon laptop, macOS 15, JDK 21, warm page cache |
| Build | `esj-cli/target/esj.jar`, the self-contained jar `bin/esj` runs |
| Pack | `xrechnung/3.0.2/2026-08-31` (CEN artefacts 1.3.16, XRechnung Schematron 2.6.0) |
| Repeats | three runs of each command, the middle one reported |
| Wall clock | `/usr/bin/time -p`, whole process: JVM start, class loading, artefact compilation, the run |
| Peak memory | `/usr/bin/time -l`, maximum resident set size of the process |

A measurement of one process includes what that process pays once: starting a virtual machine,
loading classes and compiling the artefacts of the pack. A long-lived process pays this once for
all its documents. `esj validate --verbose` reports the compilation of each artefact apart from
its run.

## One ordinary invoice

`conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml`, 7 kB, two invoice lines.

| Command | Wall clock |
|---|---|
| `esj validate <invoice>` | **1.00 s** |
| `esj validate --no-syntax <invoice>` | **0.51 s** |

On a small invoice the official artefacts cost about half a second. Almost all of it is paid
once per process, not once per document. Inside it, from `--verbose`:

| Part | Compiled in | Ran in |
|---|---|---|
| `ubl-2.1-xsd` | 102 ms | 7 ms |
| `en16931-ubl-schematron` | 247 ms | 28 ms |
| `xrechnung-ubl-schematron` | 64 ms | 12 ms |
| the whole syntax engine | | 473 ms |

A process that validates one invoice and exits pays 413 ms of compilation for 47 ms of work. A
service that keeps a virtual machine alive pays the compilation on its first document and nothing
after that. Its marginal cost per ordinary invoice is tens of milliseconds. That difference
argues for a long-running process over a command per invoice, and it is why the report gives the
two numbers apart instead of adding them.

## Large instances

The two documents below are synthetic: a corpus invoice whose invoice line repeats until the
document reaches the size wanted. They are generated, not checked in, because 100 MB of repeated
XML has no place in a repository. The repetition makes the totals wrong, so the arithmetic rules
of EN 16931 report on the document. That is deliberate: it measures a document the rule sets have
something to say about, not one they walk past.

Both ran at `-Xmx1g`, through the `esj-syntax` API and not the command line (see the caveats
below).

| Instance | Bytes | Invoice lines | Wall clock | Peak RSS |
|---|---|---|---|---|
| corpus line repeated | 83,887,126 | 37,717 | **75.4 s** | 1.03 GB |
| compact line repeated | 140,704,518 | 300,000 | **25.0 s** | 1.42 GB |

Where the time went:

| Part | 80 MB / 37.7k lines | 140 MB / 300k lines |
|---|---|---|
| `ubl-2.1-xsd` | 0.6 s | 1.4 s |
| `en16931-ubl-schematron` | 5.7 s | 15.3 s |
| `xrechnung-ubl-schematron` | 67.9 s | 6.6 s |

The smaller document took 3 times as long as the larger one. The corpus line carries a note, an
invoice period, an order line reference and a commodity classification, and several XRechnung
rules look at each. **The cost of the Schematron path follows the content of the lines, not the
size of the file.** A ceiling derived from megabytes alone is wrong in both directions.

**A rule that fails on every line makes the official Schematron superlinear.** The Schematron
names the place of each failed assertion by counting the elements before it. Measured for
`en16931-cii-schematron` with `esj --verbose --limits large validate --rules none` over dense CII
lines, the line VAT rate set to 0 so that `BR-S-05` fails on each, at `-Xmx2g` on a shared machine
(load 13):

| Lines | Bytes | valid | `BR-S-05` on every line |
|---|---|---|---|
| 5,000 | 4,765,727 | 1.1 s | 2.9 s |
| 10,000 | 9,525,732 | 2.0 s | 8.9 s |
| 20,000 | 19,045,734 | 5.8 s | 31.6 s |

The artefacts run as published. Along this curve, the 5-minute default of `--max-runtime` ends
such a run with exit code 7 from about 60,000 failing lines.

## The semantic path on a very large ESJ document

Here `esj validate --no-syntax` runs over an ESJ document that was never XML: the reader, the 3
structural layers and the 216 native business rules of the pack, nothing else.

`ScaleValidationTest` of `esj-cli` generates the documents. One invoice line repeats, the totals
follow from the count and every term the rules ask for is present, so the run ends `VALID` with
nothing to report. The times are of `java -Xmx… -jar esj.jar validate --no-syntax --limits large`,
started cold on the document as a file: best of 3 runs at the smaller size, of 6 at the larger.
The peak resident set is that process's.

| Invoice lines | Bytes | `-Xmx` | `--rules none` | `--rules en16931` | Peak RSS |
|---|---|---|---|---|---|
| 100,000 | 25,289,507 | 1g | **3.8 s** | **6.6 s** | 1.22 GB |
| 300,000 | 78,089,507 | 3g | **11.8 s** | **18.0 s** | 3.49 GB |

**The rules are linear in the lines.** 3 times the lines cost 3.1 times the time without the
pack and 2.7 times with it. The engine indexes the document once and computes each document-wide
aggregate once for the whole run. A scan per line would show here as a curve, not a line. The
linearity test of `esj-rules` checks the ratio on every build.

**And linear in the VAT breakdowns.** The table carries one breakdown. The linearity test crosses
2,000 and 50,000 lines with 2,000 and 20,000 breakdowns over the pack this build ships. It asserts
that a breakdown costs the same at both line counts. Measured there over 3 runs, 18,000 more
breakdowns cost 0.5 to 0.7 s over 2,000 lines and 0.5 to 0.8 s over 50,000. With a scan of the
lines per breakdown put back, they cost 0.9 s and 3.6 s, and the test fails.

**The rules are a third to a half of it, and they are optional.** The pack takes 42 per cent of
the wall clock at 100,000 lines and 34 per cent at 300,000. If you want only the structural layers
of a document that size, pass `--rules none` and pay the first column.

**Memory, not time, is the ceiling.** A document of 300,000 lines did not fit a 1 GiB heap: the
bytes, the parsed values and the indexed document are all resident at once. The run ended with
the out-of-memory diagnostic and no verdict, not with a wrong one. When you size a process,
budget roughly 40 MiB of heap per megabyte of ESJ document and set `-XX:+ExitOnOutOfMemoryError`.
[`deployment.md`](deployment.md) says why, and [`cli.md`](cli.md) what the resulting exit code
means.

## Caveats, and what could not be measured here

The figures of the first table were taken through the `esj-syntax` API, not through
`esj validate`, so they are what the syntax engine costs. The command reaches documents of that
size: `--limits large` raises the XML input bound to 256 MiB, and `--max-input-bytes` raises it
alone. Take the 83,886,439-byte instance of the first table, 55,884 invoice lines. The default
profile refuses it against its 4 MiB bound.
`java -Xmx3g -jar esj.jar validate --no-syntax --rules none --limits large` reads it to a verdict
in 5.2 s with a peak footprint of 1.28 GB.

The second table starts from an ESJ document. It covers the reader, the structural layers and the
business rules, but no reader of XML. You therefore cannot add the two tables to predict
`esj validate` on a very large XML invoice.

## Reading these numbers in a pipeline

- **Process start and artefact compilation dominate the per-invoice cost** of anything of
  ordinary size. Where you put the process boundary is the decision that matters.
  [`deployment.md`](deployment.md#batch-mode-is-not-isolation) states that boundary and names the
  input it is for. [`deployment-measurements.md`](deployment-measurements.md) measures what one
  process costs and what AppCDS and the packaged runtime take off the start-up cost.
- **`--max-runtime` bounds the whole command**: taking the bytes in, reading the document into the
  semantic model and running the official artefacts, in that order. A run that reaches it leaves
  with exit code 7 and no report, deliberately not the code for an invalid document
  ([`cli.md`](cli.md)). The read is inside the bound because a bounded number of bytes is not a
  bounded wait: a pipe whose writer stops without closing it never ends.
- **`--no-syntax` is the switch for a document too large to pay the Schematron for**, and for a
  pipeline that validated the XML elsewhere. The report of such a run says in as many words that
  nothing official ran.
- **`--rules none` is the switch below that.** On a very large document it saves a third to a half
  of the remaining cost. Use it only where the business rules are checked elsewhere: an ESJ
  document that was never XML is checked here or nowhere.
- **Size the heap from the document, not from the timeout.** The semantic path holds the whole
  invoice in memory. A run that outgrows `-Xmx` ends with a diagnostic and no verdict: the right
  answer, but not a usable one.
