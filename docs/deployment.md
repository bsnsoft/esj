# Running esj as a process boundary

*Part of [EN16931 Semantic JSON](../README.md).*

From a server application, run the command line tool **in a process of its own** for each document,
with a heap ceiling on its command line, a cap on how many such processes run at once, and a
timeout you hold. An invoice from a stranger costs memory and time before anything is known about
it. A library that reads it inside your service shares your service's heap. Use the Java library in
applications that own their input.

Run a **packaged artefact**, not a jar on whatever virtual machine the host carries. The native
executable is the cheapest process of the four and needs no Java. It, the runtime image and the
container image carry the heap ceiling and the abort of this contract by themselves
([`install.md`](install.md)). The image pulled from `ghcr.io/bsnsoft/esj` is that container image,
with the same entry point and the same defaults. Nothing here is a conformance statement. Limits
are policy of the reading party (`SPEC.md`, sections 3.1 and 12.2), and so is every recommendation
below.

## The model

```text
service (Java, .NET, PHP, Node, Python, Go, …)
   │  spawn, bounded by a semaphore — say four concurrent processes
   ▼
java -Xms32m -Xmx512m -XX:+ExitOnOutOfMemoryError -jar esj.jar validate - --output json
   stdin  = the document (XML or ESJ)   ← the caller caps the bytes it writes
   stdout = the result                  ← the caller caps the bytes it reads
   stderr = diagnostics                 ← the caller caps the bytes it reads
   exit   = the code table below        ← anything outside the table is a crash
   │
   ▼ on timeout: the caller kills the process — heap, threads, transformer state, all gone
```

You get four layers, and each one does something the others cannot:

1. **A process of its own.** A failure inside it kills the process, not your service.
2. **A hard `-Xmx`.** The tool cannot talk its way past this ceiling, whatever the input turns out
   to be. It makes a large limit profile safe to configure.
3. **A cap on concurrent processes.** `N × -Xmx` plus the resident overhead must be a number the
   host can afford. Otherwise the kernel picks the victim instead of the operator.
4. **ESJ's own limits**, the inner layer: input bytes, document bytes, values, string and binary
   sizes, path depth, extension nodes (`docs/cli.md`, *Limits*). They refuse a document before
   anything is parsed, and name the switch that raises them.

The outer layers hold when the inner ones were configured wrong.

## The watchdog is external

**Stop a run from outside.** A process burning CPU inside a transformation checks no flag. A
process that has exhausted its heap may not manage to run the code that would report it. A kill
from outside always works. It takes the heap, the threads, the state of the XSLT processor and the
temporary files with it.

Every platform has the mechanism built in:

| Where the caller sits | What stops the run | What caps the memory |
|---|---|---|
| a shell | `timeout 30 …` (`gtimeout` from GNU coreutils on macOS, or the `perl` line below) | `-Xmx` on the command line |
| Java | `Process.waitFor(timeout, unit)`, then `destroy()` and `destroyForcibly()` | `-Xmx` on the command line |
| systemd (a unit or `systemd-run`) | `RuntimeMaxSec=` | `MemoryMax=` |
| Docker or Podman | `timeout 30 …` inside the container, or a `docker stop` from the caller (`--stop-timeout` only sets the grace period between `SIGTERM` and `SIGKILL` once somebody has stopped it) | `--memory` |
| a plain cgroup | the freezer and a kill, from the supervisor | `memory.max` |

Inside a container, the memory limit can replace `-Xmx`. The JVM reads the cgroup limit and sizes
its heap from it. `-XX:MaxRAMPercentage=60` on a container limited with `--memory=768m` gives a
heap of about 460 MiB and leaves the rest for everything outside the heap. Set one of the two,
never two that disagree.

The tool also has `--max-runtime <duration>`: `500ms`, `90s`, `5m` or a bare number of seconds. A
command that reads a document gets five minutes where you give none. When the deadline passes, the
tool leaves with exit code 7. `esj validate` spends the time step by step and stops itself, naming
the step it ran out in. Behind that, and for every other command, a watchdog ends the process half
a second later, whatever state it is in. Use it as a fallback where you cannot kill, in a cron line
or a shell without a timeout. It is not the model.

## Why a process and not a cancelled task

Cancelling a `Future` interrupts a thread, and an interrupt is only a request. Saxon does not poll
for it while it evaluates an expression. The reader does not while it fills a collection. And
`Thread.stop` is gone. A cancelled task that keeps running still holds its memory, burns its core
and counts against the thread pool.

A killed process is over in the sense of the operating system. Its address space is returned, its
threads are gone, its file descriptors are closed. That is why you pay a start per document. The
native executable makes that start cost milliseconds.

## The contract

One process reads **one document** and writes **one result**.

- **Input.** A file name, or `-` for the standard input. Use the standard input from a service: you
  get no temporary file to name, clean up or leak. `esj diff` is the one command that takes two
  inputs, so at least one of them must be a file.
- **Standard output** carries the result and nothing else: the ESJ document, the canonical bytes,
  the value, the report.
- **Standard error** carries diagnostics for a person or a log. **Never parse it.** Its wording is
  not an interface and changes between versions. The exit code and `--output json` are the
  interface.
- **Exit code** says what happened. The meanings do not change between versions:

| Code | Meaning |
|---|---|
| 0 | success |
| 1 | a validation found an error, the profile of a container puts the rules of EN 16931 out of scope, two documents differ, or `esj get` found no value |
| 2 | the input could not be read, recognized or parsed, a validation pack could not be read or fetched, or the command line could not be parsed |
| 3 | not this tool's: the out-of-memory abort under `-XX:+ExitOnOutOfMemoryError`; its notice goes to the standard output from a JVM and to the error stream from the native executable, so it is a crash and no verdict |
| 4 | a feature this version does not implement, such as a ZUGFeRD 1.0 attachment |
| 5 | an internal error |
| 6 | the output could not be written in full: a full disk, or a `--report` the run could not deliver |
| 7 | a resource or time limit of this run was reached; no verdict on the document |
| 8 | the conversion cannot be completed as constrained: `esj convert --fail-on-loss` found part of the document the target syntax has no place for, and nothing was written |
| 9 | nothing fatal was found and a component of the complete check did not run or did not complete: no verdict, and the report names which and why |

**Code 0 is a claim about coverage.** `esj validate` returns it only where the complete check for
that kind of input ran and found nothing fatal. The tool decides per input kind what that check is;
[`validation.md`](validation.md#the-complete-check) has the table. A run that left part of it out
leaves with `9`: `--no-syntax`, `--rules none`, `--level l2`, or a document whose profile brought
no rules with it. The `reasons` array of the JSON report names each component and its cause from a
closed vocabulary. If your pipeline runs a reduced check, write it against `9`.

**Codes 1 and 7 are kept apart on purpose.** A document this run was configured not to read, or one
killed after 30 seconds, is not an invalid invoice. No verdict was reached. The answers are more
resources, a larger profile or another reader. The same holds for `3` and for a code the table does
not list: `3` for a JVM that aborted on a heap exhaustion, `137` for a process killed with
`SIGKILL`, `143` for `SIGTERM`. `3` is in the table only so that nobody reads it as a verdict. The
tool itself never returns it. `8` means the conversion cannot be completed as constrained. If you
must not lose a value, pass `esj convert --fail-on-loss`. Where the target syntax has no place for
part of the document, nothing is written and the run leaves with `8`.

- **`--output json`** gives you the validation report in machine-readable form. [`cli.md`](cli.md),
  *validate*, documents it field by field: `layers.l1`, `layers.l2` and `layers.l3`, each with
  `checked`, `ok` and `findings`; `notChecked` as stable tokens; `warnings` and `information`; and
  `reasons`, the components of the complete check that did not run or did not complete, each with
  its cause. A layer that found an error is `ok: false`. A layer that did not run is `ok: null`,
  and so is a layer that ran over less than the whole document (a `reasons` entry names it). So
  `ok: true` means covered and clean. A program that reads `ok` without `checked` calls a layer
  that never ran a failure.
- **Exit 7 is a bound of this run, never a verdict.** Whether a report exists depends on when the
  bound was met. *After* the semantic document exists (an ESJ input under a reader bound of
  `SPEC.md` section 12.2), the command writes a full report. Layer L1 then carries `checked: true`,
  `ok: null` and an `ESJ-L1-LIMIT` finding at `severity: "error"`. Section 9.5 makes a limit a
  finding that records something not evaluated, not a defect. *While the document is still being
  built*, the command ends before any report exists. That covers every importer path, so every XML
  and every PDF input, as well as `--max-runtime` and the container bounds. You get exit 7, one
  line on the error stream and nothing on the standard output, even with `--output json`. Read the
  exit code first: on 7, store no verdict and retry.
- **Put `-XX:+ExitOnOutOfMemoryError` on every command line.** Without it, a JVM that has exhausted
  its heap keeps trying. Threads die one by one, the tool's own `Throwable` handler may itself fail
  to allocate, and you get a process that is alive and useless. With it, the JVM leaves at once.
  You then see two things. The code is `3`, which is the JVM's, not the tool's, and means a crash.
  Its notice (`Terminating due to java.lang.OutOfMemoryError`, then what ran out) lands on a stream
  that differs per artefact: the **standard output** from the jar, the runtime image and the
  container image, the **error stream** from the native executable. Branch on the code: bytes read
  from a run that did not leave with 0 or 1 are not a result.
- **Without it, the tool answers `7` where it still can.** A heap or a stack that ran out is a
  resource of this run, because whoever started the process chose the ceiling. Where the tool
  survives long enough, it writes one line naming the ceiling and leaves with `7`. It does not
  leave with `5`, which would claim a defect it does not have. That recovery is unreliable by
  nature. It is a fallback for a run started without the switch, never a reason to omit it.
  `bin/esj` therefore runs with `-Xms32m -Xmx512m -XX:+ExitOnOutOfMemoryError` unless
  `ESJ_JAVA_OPTS` replaces them. The container entry point passes the same list. The native
  executable carries the ceiling and the abort built in ([`install.md`](install.md)). So every
  packaged artefact answers a heap that ran out with `3`.

## The caller's checklist

1. **Put a timeout on every run**, then `destroy()` and, after a short grace, `destroyForcibly()`.
2. **Cap the bytes written to the standard input.** The process has a bound of its own, but you
   know sooner and pay less.
3. **Cap the bytes read from the standard output and the standard error stream.** A pipe read into
   an unbounded buffer moves the memory problem into your service.
4. **Drain both pipes while the process runs**, or redirect them to files. A process whose pipe
   buffer is full is blocked, and the timeout then measures your own deadlock.
5. **Cap the number of concurrent processes**, with a semaphore, and size the host for
   `N × (-Xmx + about 200 MiB)`.
6. **Treat any exit code outside the table as a crash**, including 137 and 143.
7. **Treat 7 and a kill as "no verdict"**: retry with more resources or give up, never record
   them as "invalid".
8. **Never parse the standard error stream.** Branch on the exit code, and read `--output json`
   when the detail is needed.
9. **Never use the standard output of a run that did not leave with 0 or 1.**

## Sizing

[`deployment-measurements.md`](deployment-measurements.md) measures what this section says: wall
clock, peak resident set and the heap ceiling at which each class of document stops working, on one
machine named there. The short form:

| Input class | `-Xmx` | `--limits` | What to expect |
|---|---|---|---|
| the conformance corpus, up to a few hundred kilobytes | `256m` | default | well under a second |
| up to a few megabytes of XML | `512m` | `large` past 4 MiB | well under a second; a few seconds under `--importer xslt` |
| tens of megabytes of XML | `512m` to `1g`, by the number of values | `large` | two to four seconds; minutes under `--importer xslt`, past the five-minute default of `--max-runtime` from about 40 MB |
| an ESJ document already converted | `512m` | `large` past 64 MiB | about a second for half a million values |
| a PDF carrying one of those, up to 64 MiB | `512m` | `large` past 64 MiB | the file, then the attachment, then the row above it |
| **writing** a cross industry invoice from tens of megabytes of XML | `2g` | `large` | five to nine seconds; `convert --to cii` builds the whole output tree, so it costs about three times what reading the same document costs |
| **rendering** ten thousand lines as HTML | `1g` | `large` | three seconds and about 130 MB of HTML; the page is held to `--max-output-bytes` while it is written, so one past it leaves with 7 rather than with a heap that ran out |

A PDF is read within bounds of its own:

- `--max-pdf-bytes` (64 MiB by default, 512 MiB under `--limits large`) bounds the length of the
  file. A longer file is refused before it is parsed.
- `--max-attachments` (64) bounds how many attachments are enumerated.
- The attachment that is read is then held to `--max-input-bytes` like any other XML input.
- The decoding of an attachment and of the XMP packet stops **at** its bound, not after it. A
  stream written to inflate without end costs the bound, not what it would have produced.
- The library, not this reader, decodes two kinds of stream while it opens the file: the object
  streams that hold a PDF's objects and the cross-reference streams that find them. ESJ measures
  each one when the library reaches it, against one budget of decoded bytes for the whole
  container. The budget also counts what this reader decodes out of it. A container that spends the
  budget ends the run with exit code 7 and no verdict.
- A structural stream whose filter chain this reader does not run is refused the same way, never
  decoded unmeasured. An encrypted file is refused with exit code 2 before anything is decrypted,
  because PDF/A forbids encryption. The objects an object stream declares are counted as well as
  its bytes.

The library's own object model in general is not counted. For that, **the memory limit set from
outside the process, the `-Xmx` of this page, remains the last line**. Two consequences for sizing.
PDFBox's stream cache in this tool is **memory only**: nothing is written to a temporary directory,
so the file and its object structure are heap. A container therefore costs about the file plus the
largest attachment read, on top of the row above for the invoice itself.

**A profile needs the heap its bounds imply.** A bound promises that an input of that size is read,
not refused. Reading it costs at least the bytes. The tool holds the whole input before it parses
it, because every command needs the bytes twice and the digest of the source belongs to the
document. A file argument is read into one array of the file's length. The standard input has no
length to ask for. It is collected into a buffer that grows and is then copied out, which costs
about twice the input at its largest. The table gives the smallest `-Xmx` at which an input **at**
the bound is read and judged, measured on the machine of
[`deployment-measurements.md`](deployment-measurements.md):

| Profile | Bound | File argument | Standard input | Resident set at those ceilings |
|---|---|---|---|---|
| default | 4 MiB of XML | `64m` | `64m` | 215 MiB / 210 MiB |
| default | 64 MiB of ESJ | `160m` | `160m` | 276 MiB / 251 MiB |
| default | 64 MiB of PDF | `96m` | `160m` | 271 MiB / 285 MiB |
| `large` | 256 MiB of XML | `3g` | not measured | 3 477 MiB |
| `large` | 512 MiB of ESJ | `640m` | `1280m` | 1 259 MiB / 1 308 MiB |
| `large` | 512 MiB of PDF | `640m` | `1280m` | 1 240 MiB / 1 230 MiB |

**These figures are measured on documents, not on adversaries.** The bounds count bytes and
streams, not the objects the library builds out of them. A file inside the byte bound that spends
its bytes on objects can cost more than the table says. The answer to that is the memory limit this
page is about: it ends the process with exit code 3 and no verdict.

**Size a container on the last column, not on `-Xmx`.** The flag bounds the Java heap. The input,
the library's buffers, the class metadata and the runtime itself stand outside it. At `-Xmx640m`
the process occupies about 1.7 times the number on the flag. If your service gives a container the
`-Xmx` figure, the operating system kills it, with no exit code of this tool at all. That is the
one outcome this page exists to prevent.

So **the default profile is read within `-Xmx256m`** with room to spare. That is why `bin/esj` runs
`-Xmx512m` and need not think about it. **The large profile needs about `-Xmx1g` from a file and
`-Xmx1536m` from the standard input** at its 512 MiB bounds. The XML row is the outlier. It was
measured under `--importer xslt`, which builds a tree of the document and holds the source beside
it. What the default reader needs at that size follows the number of values instead;
[`deployment-measurements.md`](deployment-measurements.md) has the figures.

**A profile is only as good as the heap that can refuse at its bounds, and on the standard input
that is a real heap.** The rows above are documents that fit. For a document that does not fit, the
answer depends on whether the process can still reach the refusal, and so on the form the input
arrived in. A **file argument** is refused on the length the file system reports, before a byte has
been collected. That costs about 65 MiB, at any bound and at any heap. The **standard input** has
no length to ask, so it is collected until one byte past the bound. A refusal there costs roughly
twice the bound in resident set: about 845 MiB for the large profile's 512 MiB document bound. A
`-Xmx512m` process does not reach it. It exhausts the heap first and leaves with exit 3 and an
empty error stream. [`deployment-measurements.md`](deployment-measurements.md), *The front door*,
has the measured table: both bounds in both forms, with the input each figure was taken against.

So a `512m` process with `--limits large` reads its few megabytes of XML, answers a hostile
**file** in a tenth of a second, and answers a hostile **stream** of that size with a crash. And
this page recommends the standard input. Where you cannot raise the heap with the profile, raise
the single bound your documents need (`--max-input-bytes` or `--max-document-bytes`) and leave the
rest of the profile alone.

Two numbers matter more than the table. **A process runs up to about 200 MiB above its heap
ceiling**: the jar, the metaspace, the compiled code and Saxon's own structures live outside the
heap. The kernel kills a container sized at `-Xmx` and nothing more, before the JVM can. Size it by
the measured resident set, not by `-Xmx`. And **the heap a large XML document needs follows the
number of its values, not the number of its bytes**. The default reader holds one element (within
`--max-buffered-bytes` and `--max-buffered-elements`) and the values it has produced. 80 MB of CII
with half a million values fits in `256m`, while 80 MB of dense UBL with 1.4 million values needs a
gibibyte. `--importer xslt` is the other reader, kept as this project's oracle. It builds three
trees of the document and costs minutes at that size. A run that asks for it needs a different
class of resources.

## From a shell

`timeout(1)` is GNU coreutils: on every Linux, and as `gtimeout` on a macOS with coreutils:

```text
timeout 30 java -Xmx512m -XX:+ExitOnOutOfMemoryError -jar esj.jar \
    validate - --output json < invoice.xml
```

Where it does not exist, `perl` does. Its alarm arrives whatever the child is doing:

```text
perl -e 'alarm shift @ARGV; exec @ARGV or die $!' 30 \
    java -Xmx512m -XX:+ExitOnOutOfMemoryError -jar esj.jar \
    validate - --output json < invoice.xml
```

Either way, branch in your script on the exit code of [the table above](#the-contract).

## From Java

Bound the input, redirect the streams, wait with a timeout, kill on the way out. That is the whole
of it. `esj-cli/src/test/java/de/bsnsoft/esj/cli/DeploymentExampleIT.java` compiles the snippet and
runs it against the built jar.

```java
static int validate(Path jar, byte[] invoice, Path report, Path diagnostics)
        throws IOException, InterruptedException {
    Process process = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Xms32m", "-Xmx512m", "-XX:+ExitOnOutOfMemoryError",
            "-jar", jar.toString(), "validate", "-", "--output", "json")
            .redirectOutput(report.toFile())
            .redirectError(diagnostics.toFile())
            .start();
    try (OutputStream in = process.getOutputStream()) {
        in.write(invoice);
    } catch (IOException e) {
        // The process left before it had read everything: a bound, or a crash. Its exit
        // code says which, and it is about to be read.
    }
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
        process.destroy();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
        }
        return -1;   // no verdict: the caller retries with more, or gives up
    }
    return process.exitValue();
}
```

Redirecting both streams to files is the cheapest way to have them bounded and drained at once. The
operating system writes them. You read them afterwards and can refuse a file that grew past what
you are willing to read. If you want pipes instead, read both on threads of their own for as long
as the process lives.

Write the bytes to the standard input and close the stream. Closing tells the tool the document is
complete. The write can fail, and that is not your error. A process that met a bound in the first
megabyte is already writing its refusal and will not read the rest. The exit code is the answer in
either case.

## From other runtimes

The contract is the same everywhere. Only the spelling of "spawn a process and read its pipes
without letting them grow" changes.

**.NET.** Use `System.Diagnostics.Process` with `RedirectStandardInput`, `RedirectStandardOutput`
and `RedirectStandardError`. Read both output streams asynchronously (`OutputDataReceived` and
`ErrorDataReceived`, or `ReadToEndAsync` on both at once). Reading one to the end while the other
fills its buffer is the classic deadlock. Use `WaitForExitAsync` with a `CancellationToken` for the
timeout, `Process.Kill(entireProcessTree: true)` when it fires, and `ExitCode` afterwards.

**Node.** Use `child_process.spawn` with `stdio: ['pipe', 'pipe', 'pipe']`, and a counter on each
`data` event, so that a runaway output is cut, not concatenated. `Buffer.concat` over an unbounded
array is how a service inherits the memory problem it spawned a process to avoid. Pass
`AbortSignal.timeout(30_000)` as the `signal` option, or use a `setTimeout` that calls
`child.kill('SIGKILL')`. The `close` event carries the code and the signal. A `null` code with a
signal is the kill, not a verdict.

**Python.** `subprocess.run(..., input=invoice, capture_output=True, timeout=30)` does most of it.
It raises `TimeoutExpired` after killing the child, and `completed.returncode` is the answer.
`capture_output` reads without a bound. For input from strangers, use `Popen` with
`stdout=tempfile` and `stderr=tempfile` and check the sizes afterwards. Or read the pipes in
fixed-size chunks and stop at a ceiling of your own.

## Batch mode is not isolation

Running many documents through one process is faster: the JVM starts once and the stylesheets are
compiled once. It is not this model. The documents share one heap, so the one that exhausts it
takes the others with it. They share one timeout, so a single pathological instance spends the
budget of the whole batch. A process per document keeps one document's failure to that document.

- **Input you own** or that is already isolated behind another boundary (your own archive, a
  regression run, a migration): use a long-lived process or a batch.
- **Input from strangers**: use one process per document, with a heap ceiling on its command line
  and a timeout from outside. Pay the start-up cost.
  [`deployment-measurements.md`](deployment-measurements.md) measures it and says what an AppCDS
  archive and `-XX:TieredStopAtLevel=1` take off it.
  [`validation-measurements.md`](validation-measurements.md) gives what the check itself costs once
  a process is running.

`esj serve` and `esj mcp` ([`serve.md`](serve.md)) are that model as a server. Every call is a
process of its own, with `--job-heap` as its `-Xmx`, `--job-timeout` as its deadline and a kill
from outside, at most `--max-jobs` at once. A document that exhausts a child costs that call, never
the server. The server holds no document and no answer of a child whole. `--max-jobs`, not the
documents, bounds its own heap, as `--max-disk` bounds its temporary directory.
