# What validation costs

*Part of [EN16931 Semantic JSON](../README.md).*

Numbers, not adjectives. `esj validate` on an XML input runs the official validation
artefacts of the document's profile — an XML Schema and two compiled Schematron rule sets —
and running a Schematron over a document is the expensive part of this tool by a wide margin.

Everything below was measured on one machine. A number here is an order of magnitude and a
ratio between the parts, not a service level: the machine, the JDK and the heap all move it.
The way to get a number for your own deployment is to run the same commands there.

## Method

| | |
|---|---|
| Measured on | Apple Silicon laptop, macOS 15, JDK 21, warm page cache |
| Build | `esj-cli/target/esj.jar`, the self-contained jar `bin/esj` runs |
| Pack | `xrechnung/3.0.2/2026-08-31` (CEN artefacts 1.3.16, XRechnung Schematron 2.6.0) |
| Repeats | three runs of each command, the middle one reported |
| Wall clock | `/usr/bin/time -p`, whole process: JVM start, class loading, artefact compilation, the run |
| Peak memory | `/usr/bin/time -l`, maximum resident set size of the process |

A measurement of one process includes what that process pays once and a long-lived one pays
once for all its documents: starting a virtual machine, loading classes, and compiling the
artefacts of the pack. `esj validate --verbose` reports the compilation of each artefact apart
from its run.

## One ordinary invoice

`conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml`, 7 kB, two invoice lines.

| Command | Wall clock |
|---|---|
| `esj validate <invoice>` | **1.00 s** |
| `esj validate --no-syntax <invoice>` | **0.51 s** |

So the official artefacts cost about half a second on a small invoice, and almost all of that
half second is paid once per process rather than once per document. Inside it, from
`--verbose`:

| Part | Compiled in | Ran in |
|---|---|---|
| `ubl-2.1-xsd` | 102 ms | 7 ms |
| `en16931-ubl-schematron` | 247 ms | 28 ms |
| `xrechnung-ubl-schematron` | 64 ms | 12 ms |
| the whole syntax engine | | 473 ms |

A process that validates one invoice and exits pays 413 ms of compilation for 47 ms of work. A
service that keeps a virtual machine alive pays the compilation on its first document and
nothing after that, so its marginal cost per ordinary invoice is tens of milliseconds. That
difference is the argument for a long-running process over a command per invoice, and it is the
reason the report separates the two numbers instead of adding them together.

## Large instances

The two documents below are synthetic: a corpus invoice whose invoice line is repeated until
the document reaches the size wanted. They are generated rather than checked in — a hundred
megabytes of repeated XML has no place in a repository — and the repetition makes the totals of
the document wrong, so the arithmetic rules of EN 16931 report on it. That is deliberate: this
measures the cost of a document the rule sets have something to say about, not of one they walk
past.

Both were run at `-Xmx1g`, through the `esj-syntax` API rather than through the command line;
see the caveat below.

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

Neither run came close to ten minutes, and neither needed more than about one and a half
gigabytes of resident memory.

The striking figure is the last column of the first row. The smaller document took three times
as long as the larger one, because the two rows differ in what is *in* a line and not only in
how many there are: the fat corpus line carries a note, an invoice period, an order line
reference and a commodity classification, and several XRechnung rules have something to look at
in each of them. **The cost of the Schematron path follows the content of the lines, not the
size of the file.** A ceiling derived from megabytes alone will be wrong in both directions.

## The semantic path on a very large ESJ document

The table above is the syntax engine. This one is the other half: `esj validate --no-syntax`
over an ESJ document that was never XML, which is the reader, the three structural layers and
the 216 native business rules of the pack and nothing else. It is the combination a very large
invoice is validated with, and it is the one an ESJ document has at all.

The documents are synthetic and are the ones `ScaleValidationTest` of `esj-cli` generates: one
invoice line repeated, the totals following from the count, and every term the business rules
ask for present — so the run ends `VALID` with nothing to report, and what is measured is the
full cost of checking rather than the cost of printing findings. The times are of
`java -Xmx… -jar esj.jar validate --no-syntax --limits large`, started cold on the document as a
file, best of three runs at the smaller size and of six at the larger; the peak resident set is
that process's.

| Invoice lines | Bytes | `-Xmx` | `--rules none` | `--rules en16931` | Peak RSS |
|---|---|---|---|---|---|
| 100,000 | 25,289,507 | 1g | **3.8 s** | **6.6 s** | 1.22 GB |
| 300,000 | 78,089,507 | 3g | **11.8 s** | **18.0 s** | 3.49 GB |

Three things follow, and all three are the point of the numbers.

**The rules are linear in the lines.** Three times the lines cost 3.1 times the time without
the pack and 2.7 times with it. The engine indexes the document once and computes each document-wide
aggregate once for the whole run; a scan per line would show here as a curve rather than a
line, and the ratio is checked on every build by the linearity test of `esj-rules`.

**And linear in the VAT breakdowns, which is a second count.** The documents in the table carry
one VAT breakdown, so they do not measure the other axis: the rules that compare a breakdown
with the invoice lines of its category have two counts to be linear in, and an invoice may
carry many of both. The linearity test therefore crosses the two — 2,000 and 50,000 lines
against 2,000 and 20,000 breakdowns, over the pack this build ships rather than over a
synthetic one — and asserts that what a breakdown costs does not grow with the number of lines.
Measured there over three runs: 18,000 more breakdowns cost 0.5 to 0.7 s over 2,000 lines and
0.5 to 0.8 s over 50,000 — the same on both, within the noise, which is the whole claim. With
the scan of the lines per breakdown put back, the two are 0.9 s and 3.6 s and the test fails.

**The rules are a third to a half of it, and they are optional.** The pack is 42 per cent of
the wall clock at 100,000 lines and 34 per cent at 300,000. A caller who wants only the
structural layers of a document that size asks for `--rules none` and pays the first column.

**Memory, not time, is the ceiling.** A document of 300,000 lines did not fit a 1 GiB heap: the
bytes, the parsed values and the indexed document are all resident at once, and the run ended
with the out-of-memory diagnostic and no verdict rather than with a wrong one. A caller sizing
a process should budget roughly 40 MiB of heap per megabyte of ESJ document, and should set
`-XX:+ExitOnOutOfMemoryError` — [`deployment.md`](deployment.md) is why, and
[`cli.md`](cli.md) is what the resulting exit code means.

## Caveats, and what could not be measured here

The figures of the first table were taken through the `esj-syntax` API and not through
`esj validate`, so they are what the syntax engine costs. The command reaches documents of that
size: `--limits large` raises the XML input bound to 256 MiB, `--max-input-bytes` raises it
alone. On the 83,886,439-byte instance of the first table, 55,884 invoice lines, the default
profile refuses it against its 4 MiB bound and
`java -Xmx3g -jar esj.jar validate --no-syntax --rules none --limits large` reads it to a
verdict in 5.2 s with a peak footprint of 1.28 GB.

The second table starts from an ESJ document, so it covers the reader, the structural layers and
the business rules and not a reader of XML. The two tables therefore cannot be added together to
predict `esj validate` on a very large XML invoice.

## Reading these numbers in a pipeline

- **Per-invoice cost is dominated by process start and artefact compilation** for anything of
  ordinary size, which makes where the process boundary goes the decision that matters.
  [`deployment.md`](deployment.md#batch-mode-is-not-isolation) states that boundary and names the
  input it is for; [`deployment-measurements.md`](deployment-measurements.md) measures what one
  process costs and what AppCDS and the packaged runtime take off the start-up cost.
- **`--max-runtime` bounds the whole command** — taking the bytes in, reading the document into
  the semantic model and running the official artefacts, in that order — and a run that reaches
  it leaves with exit code 7 and no report, which is deliberately not the code for an invalid
  document; see [`cli.md`](cli.md). The read is inside the bound because a bounded number of
  bytes is not a bounded wait: a pipe whose writer stops without closing it never ends.
- **`--no-syntax` is the switch for a document too large to pay the Schematron for**, and for a
  pipeline that has validated the XML elsewhere. The report of such a run says in as many words
  that nothing official ran.
- **`--rules none` is the switch below that**, and on a very large document it is a third to a
  half of the remaining cost. Ask for it only where the business rules are checked elsewhere: for an
  ESJ document that was never XML, they are checked here or nowhere.
- **Size the heap from the document, not from the timeout.** The semantic path holds the whole
  invoice in memory; a run that outgrows `-Xmx` ends with a diagnostic and no verdict, which is
  the right answer and not a usable one.
