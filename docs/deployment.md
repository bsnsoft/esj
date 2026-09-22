# Running esj as a process boundary

*Part of [EN16931 Semantic JSON](../README.md).*

Use ESJ from a server application through the command line tool **in a process of its own**,
with a heap ceiling on its command line, a cap on how many of those processes run at once, and
a timeout held by the caller: an invoice from a stranger costs memory and time before anything
is known about it, and a library that reads it inside the service shares that service's heap.
The Java library is for applications that own their input.

Run a **packaged artefact**, not a jar on whatever virtual machine the host carries: the native
executable is the cheapest process of the four and needs no Java, and it, the runtime image and
the container image carry the heap ceiling and the abort of this contract by themselves
([`install.md`](install.md)). Nothing here is a conformance statement: limits are policy of the
reading party (`SPEC.md`, sections 3.1 and 12.2), and so is every recommendation below.

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

Four layers, and each one does something the others cannot:

1. **A process of its own.** A failure inside it is a dead process, not a dead service.
2. **A hard `-Xmx`.** It is the ceiling the tool cannot talk its way past, whatever the input
   turns out to be, and it is what makes a large limit profile safe to configure.
3. **A cap on concurrent processes.** `N × -Xmx` plus the resident overhead has to be a number
   the host can afford, or the kernel picks the victim instead of the operator.
4. **ESJ's own limits**, the inner layer: input bytes, document bytes, values, string and
   binary sizes, path depth, extension nodes (`docs/cli.md`, *Limits*). They refuse a document
   before anything is parsed, and name the switch that raises them.

The order is the point: the outer layers hold when the inner ones were configured wrong.

## The watchdog is external

**A run is stopped from outside.** A process burning CPU inside a transformation is not
checking a flag, and a process that has exhausted its heap may not manage to run the code that
would have reported it. Killing it from outside always works, and it takes the heap, the
threads, the state of the XSLT processor and the temporary files with it.

Every platform this runs on has the mechanism built in:

| Where the caller sits | What stops the run | What caps the memory |
|---|---|---|
| a shell | `timeout 30 …` (`gtimeout` from GNU coreutils on macOS, or the `perl` line below) | `-Xmx` on the command line |
| Java | `Process.waitFor(timeout, unit)`, then `destroy()` and `destroyForcibly()` | `-Xmx` on the command line |
| systemd (a unit or `systemd-run`) | `RuntimeMaxSec=` | `MemoryMax=` |
| Docker or Podman | `timeout 30 …` inside the container, or a `docker stop` from the caller (`--stop-timeout` only sets the grace period between `SIGTERM` and `SIGKILL` once somebody has stopped it) | `--memory` |
| a plain cgroup | the freezer and a kill, from the supervisor | `memory.max` |

Inside a container the memory limit can replace `-Xmx` rather than duplicate it: the JVM reads
the cgroup limit and sizes its heap from it, so `-XX:MaxRAMPercentage=60` on a container limited
with `--memory=768m` gives a heap of about 460 MiB and leaves the rest for everything that lives
outside the heap. Set one of the two, not two that disagree.

The tool also has `--max-runtime <duration>` — `500ms`, `90s`, `5m` or a bare number of
seconds — which leaves with exit code 7 when the deadline passes. `esj validate` spends the
time step by step and stops itself, naming the step it ran out in; behind that, and for the
commands that hold no deadline of their own, a watchdog ends the process half a second later
whatever state it is in. It is a fallback for callers that cannot kill — a cron line, a shell
without a timeout — and not the model.

## Why a process and not a cancelled task

Cancelling a `Future` interrupts a thread, and an interrupt is a request: Saxon does not poll
for it while it evaluates an expression, the reader does not while it fills a collection, and
`Thread.stop` is gone. A cancelled task that keeps running still holds its memory, still burns
its core and still counts against the thread pool.

A killed process is over in the sense the operating system uses: its address space is returned,
its threads are gone, its file descriptors are closed. That is the reason to pay a start per
document — and the native executable is why that start costs milliseconds.

## The contract

One process reads **one document** and writes **one result**.

- **Input.** A file name, or `-` for the standard input. The standard input is the form to use
  from a service: there is no temporary file to name, to clean up or to leak. `esj diff` is the
  one command that takes two inputs and therefore needs at least one of them as a file.
- **Standard output** carries the result and nothing else: the ESJ document, the canonical
  bytes, the value, the report.
- **Standard error** carries diagnostics, and it is for a person or a log. **Never parse it.**
  Its wording is not an interface and it changes between versions; the exit code and
  `--output json` are the interface.
- **Exit code** says what happened, and the meanings do not change between versions:

| Code | Meaning |
|---|---|
| 0 | success |
| 1 | a validation found an error, the profile of a container puts the rules of EN 16931 out of scope, two documents differ, or `esj get` found no value |
| 2 | the input could not be read, recognized or parsed, or the command line could not be parsed |
| 3 | not this tool's: the out-of-memory abort under `-XX:+ExitOnOutOfMemoryError`; its notice goes to the standard output from a JVM and to the error stream from the native executable, so it is a crash and no verdict |
| 4 | a feature this version does not implement, such as a ZUGFeRD 1.0 attachment |
| 5 | an internal error |
| 6 | the output could not be written in full: a full disk, or a `--report` the run could not deliver |
| 7 | a resource or time limit of this run was reached; no verdict on the document |
| 8 | the conversion cannot be completed as constrained (reserved) |
| 9 | nothing fatal was found and a component of the complete check did not run or did not complete: no verdict, and the report names which and why |

**Code 0 is a claim about coverage.** `esj validate` returns it only where the complete check
for that kind of input ran and nothing fatal was found; what that check is, is decided by the
tool per input kind and is the table in [`validation.md`](validation.md#the-complete-check).
A run that left part of it out — `--no-syntax`, `--rules none`, `--level l2`, or a document
whose profile brought no rules with it — leaves with `9`, and the `reasons` array of the JSON
report names each component and its cause from a closed vocabulary. A pipeline that means to
run a reduced check writes itself against `9`.

**Codes 1 and 7 are kept apart on purpose.** A document this run was configured not to read,
or that was killed after thirty seconds, is not thereby an invalid invoice: no verdict was
reached, and the answers are more resources, a larger profile or another reader. The same
holds for `3` and for a code the table does not list: `3` for a JVM that aborted on a heap
exhaustion, `137` for a process killed with `SIGKILL`, `143` for `SIGTERM`. `3` is in the
table only so that nobody reads it as a verdict; the tool itself never returns it, and the
reserved code for a conversion that cannot be completed as constrained is `8`.

- **`--output json`** is the machine-readable form of the validation report. Its shape is
  documented field by field in [`cli.md`](cli.md), *validate*: `layers.l1`, `layers.l2` and
  `layers.l3`, each with `checked`, `ok` and `findings`; `notChecked` as stable tokens;
  `warnings` and `information`; and `reasons`, the components of the complete check that did
  not run or did not complete, each with its cause. A layer that found an error is `ok: false`;
  otherwise a layer that did not run, and a layer that ran over less than the whole document —
  one `reasons` names — are both `ok: null`, so `ok: true` means covered and clean, and a
  program reading `ok` without `checked` calls a layer that never ran a failure.
- **Exit 7 is a bound of this run, never a verdict — and whether a report exists turns on when
  the bound was met.** Met *after* the semantic document exists, which is an ESJ input under a
  reader bound of `SPEC.md` section 12.2, the command writes a full report: layer L1 carries
  `checked: true`, `ok: null` and an `ESJ-L1-LIMIT` finding at `severity: "error"`, section 9.5
  having a limit be a finding that records something not evaluated rather than a defect. Met
  *while the document is still being built* — every importer path, so every XML and every PDF
  input, as well as `--max-runtime` and the container bounds — the command ends before any
  report exists: exit 7, one line on the error stream, nothing on the standard output even with
  `--output json`. Read the exit code first: on 7 a consumer stores no verdict and retries.
- **`-XX:+ExitOnOutOfMemoryError` belongs on every command line.** Without it, a JVM that has
  exhausted its heap keeps trying: threads die one by one, the tool's own `Throwable` handler
  may itself fail to allocate, and what the caller gets is a process that is alive and useless.
  With it, the JVM leaves at once. Two consequences for the caller: the code is `3`, which is
  the JVM's and not the tool's and means a crash, and its notice —
  `Terminating due to java.lang.OutOfMemoryError`, then what ran out — lands on a stream that
  differs per artefact: the **standard output** from the jar, the runtime image and the
  container image, the **error stream** from the native executable. Branch on the code: bytes
  read from a run that did not leave with 0 or 1 are not a result.
- **Without it, the tool answers `7` where it still can.** A heap or a stack that ran out is a
  resource of this run — the ceiling was chosen by whoever started the process — so where the
  tool survives long enough to say so it writes one line naming the ceiling and leaves with
  `7`, not with `5`, which would claim a defect it does not have. That recovery is unreliable
  by nature, so it is a fallback for the run that was started without the switch and not a
  reason to omit it. `bin/esj` therefore runs with `-Xms32m -Xmx512m
  -XX:+ExitOnOutOfMemoryError` unless `ESJ_JAVA_OPTS` replaces them, the container entry point
  passes the same list, and the native executable carries the ceiling and the abort built in
  ([`install.md`](install.md)), so every packaged artefact answers a heap that ran out with `3`.

## The caller's checklist

1. **Put a timeout on every run**, then `destroy()` and, after a short grace, `destroyForcibly()`.
2. **Cap the bytes written to the standard input.** The process has a bound of its own, but the
   caller knows sooner and pays less.
3. **Cap the bytes read from the standard output and the standard error stream.** A pipe read
   into an unbounded buffer moves the memory problem into the service it was to be kept out of.
4. **Drain both pipes while the process runs**, or redirect them to files: a process whose pipe
   buffer is full is blocked, and the timeout then measures the caller's own deadlock.
5. **Cap the number of concurrent processes**, with a semaphore, and size the host for
   `N × (-Xmx + about 200 MiB)`.
6. **Treat any exit code outside the table as a crash**, including 137 and 143.
7. **Treat 7 and a kill as "no verdict"**: retry with more resources or give up, never record
   them as "invalid".
8. **Never parse the standard error stream.** Branch on the exit code, and read `--output json`
   when the detail is needed.
9. **Never use the standard output of a run that did not leave with 0 or 1.**

## Sizing

[`deployment-measurements.md`](deployment-measurements.md) is the measured version of this
section: wall clock, peak resident set and the heap ceiling at which each class of document
stops working, on one machine that is named there. The short form:

| Input class | `-Xmx` | `--limits` | What to expect |
|---|---|---|---|
| the conformance corpus, up to a few hundred kilobytes | `256m` | default | well under a second |
| up to a few megabytes of XML | `512m` | `large` past 4 MiB | well under a second; a few seconds under `--importer xslt` |
| tens of megabytes of XML | `512m` to `1g`, by the number of values | `large` | two to four seconds; minutes under `--importer xslt` |
| an ESJ document already converted | `512m` | `large` past 64 MiB | about a second for half a million values |
| a PDF carrying one of those, up to 64 MiB | `512m` | `large` past 64 MiB | the file, then the attachment, then the row above it |
| **writing** a cross industry invoice from tens of megabytes of XML | `2g` | `large` | five to nine seconds; `convert --to cii` builds the whole output tree, so it costs about three times what reading the same document costs |

A PDF is read within two bounds of its own. `--max-pdf-bytes` (64 MiB by default, 512 MiB under
`--limits large`) is the length of the file, refused before it is parsed; `--max-attachments`
(64) is how many attachments are enumerated; and the attachment that is read is then held to
`--max-input-bytes` like any other XML input. The decoding of an attachment and of the XMP
packet stops **at** its bound rather than after it, so a stream written to inflate without end
costs the bound and not what it would have produced. Two streams are the library's to decode
and not this reader's — the object streams that hold a PDF's objects and the cross-reference
streams that find them — and the library decodes them while it opens the file. Each of them is
measured at the moment the library reaches it, against one budget of decoded bytes for the
whole container that also counts what this reader decodes out of it; a container that spends
that budget ends the run with exit code 7 and no verdict. A structural stream whose filter chain
this reader does not run is refused the same way rather than decoded unmeasured; an encrypted file
is refused with exit code 2 before anything is decrypted, because PDF/A forbids encryption; and the
objects an object stream declares are counted as well as its bytes. The library's own object
model in general is not, and for that **the memory limit set from outside the process — the
`-Xmx` of this page — remains the last line**. Two consequences for sizing: PDFBox's stream
cache in this tool is **memory only** — nothing is written to a temporary directory, so the
file and its object structure are heap — and a container is therefore about the file plus the
largest attachment read, on top of the row above for the invoice itself.

**A profile needs the heap its bounds imply.** A bound is a promise that an input of that size is
read rather than refused, and reading it costs at least the bytes: the tool holds the whole input
before it parses it, because every command needs the bytes twice and the digest of the source
belongs to the document. A file argument is read into one array of the file's length; the standard
input has no length to ask for, so it is collected into a buffer that grows and then copied out,
which costs about twice the input at the moment it is largest. Measured, on the machine of
[`deployment-measurements.md`](deployment-measurements.md), as the smallest `-Xmx` at which an
input **at** the bound is read and judged:

| Profile | Bound | File argument | Standard input | Resident set at those ceilings |
|---|---|---|---|---|
| default | 4 MiB of XML | `64m` | `64m` | 215 MiB / 210 MiB |
| default | 64 MiB of ESJ | `128m` | `160m` | 291 MiB / 257 MiB |
| default | 64 MiB of PDF | `96m` | `160m` | 271 MiB / 285 MiB |
| `large` | 256 MiB of XML | `3g` | not measured | 3 477 MiB |
| `large` | 512 MiB of ESJ | `640m` | `1280m` | 1 259 MiB / 1 308 MiB |
| `large` | 512 MiB of PDF | `640m` | `1280m` | 1 240 MiB / 1 230 MiB |

**These figures are measured on documents and not on adversaries.** The bounds count bytes and
streams, not the objects the library builds out of them, so a file inside the byte bound that
spends its bytes on objects can cost more than the table says; the answer to that is the memory
limit this page is about, which ends the process with exit code 3 and no verdict.

**A container is sized on the last column and not on `-Xmx`.** The flag bounds the Java heap;
the input, the library's buffers, the class metadata and the runtime itself stand outside it,
and at `-Xmx640m` the process occupies about 1.7 times the number on the flag. A service that
gives a container the `-Xmx` figure is killed by the operating system, with no exit code of
this tool at all — which is the one outcome this page exists to prevent.

So **the default profile is read within `-Xmx256m`** with room to spare, which is why
`bin/esj` runs `-Xmx512m` and does not have to think about it, and **the large profile needs
about `-Xmx1g` from a file and `-Xmx1536m` from the standard input** at its 512 MiB bounds.
The XML row is the outlier, and it was measured under `--importer xslt`, which builds a tree
of the document and holds the source beside it. What the default reader needs at that size
follows the number of values instead;
[`deployment-measurements.md`](deployment-measurements.md) has the figures.

**A profile is only as good as the heap that can refuse at its bounds — and on the standard
input that is a real heap.** The rows above are documents that fit; what decides the answer to a
document that does not is whether the process can still reach the refusal, and that depends on
the form the input arrived in. A **file argument** is refused on the length the file system
reports, before a byte has been collected: a constant cost of about 65 MiB, at any bound and at
any heap. The **standard input** has no length to ask, so it is collected until one byte past
the bound, and a refusal there costs roughly twice the bound in resident set — about 845 MiB for
the large profile's 512 MiB document bound, which a `-Xmx512m` process does not reach: it
exhausts the heap first and leaves with exit 3 and an empty error stream. The measured table,
both bounds in both forms with the input each figure was taken against, is
[`deployment-measurements.md`](deployment-measurements.md), *The front door*.

So a `512m` process with `--limits large` reads its few megabytes of XML happily, answers a
hostile **file** in a tenth of a second, and answers a hostile **stream** of that size with a
crash — and the standard input is the form this page recommends. Where the heap cannot be raised
with the profile, raise the single bound the documents need — `--max-input-bytes` or
`--max-document-bytes` — and leave the rest of the profile alone.

Two numbers matter more than the table. **A process runs up to about 200 MiB above its heap
ceiling** — the jar, the metaspace, the compiled code and Saxon's own structures live outside
the heap — so a container sized at `-Xmx` and nothing more is a container the kernel kills
instead of the JVM. Size it by the measured resident set, not by `-Xmx`. And **the heap a large XML document needs follows the number of its values, not the number of
its bytes**: the default reader holds one element — within `--max-buffered-bytes` and
`--max-buffered-elements` — and the values it has produced, so 80 MB of
CII with half a million values fits in `256m` while 80 MB of dense UBL with 1.4 million values
needs a gibibyte. `--importer xslt` is the other reader, kept as this project's oracle; it
builds three trees of the document and costs minutes at that size, so a run that asks for it
asks for a different class of resources.

## From a shell

`timeout(1)` is the obvious form where it exists. It is GNU coreutils, so it is on every Linux
and, as `gtimeout`, on a macOS with coreutils installed:

```text
timeout 30 java -Xmx512m -XX:+ExitOnOutOfMemoryError -jar esj.jar \
    validate - --output json < invoice.xml
```

Where it does not exist, `perl` does, on both systems, and an alarm is the same thing spelled
differently — the signal arrives on the schedule the shell set, whatever the child is doing:

```text
perl -e 'alarm shift @ARGV; exec @ARGV or die $!' 30 \
    java -Xmx512m -XX:+ExitOnOutOfMemoryError -jar esj.jar \
    validate - --output json < invoice.xml
```

Either way the exit code is what the script branches on: `0` completely checked and nothing
fatal, `1` findings, `9` nothing fatal and part of the check did not run or did not complete,
`7` or a signal code no verdict, anything else a crash.

## From Java

The snippet below is the whole of it: bound the input, redirect the streams, wait with a
timeout, kill on the way out. It is compiled and run against the built jar by
`esj-cli/src/test/java/de/bsnsoft/esj/cli/DeploymentExampleIT.java`, so it is
code that works rather than code that reads well.

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

Both streams are redirected to files, which is the cheapest way to have them bounded and drained
at once: the operating system writes them, the caller reads them afterwards and can refuse a
file that grew past what it is willing to read. A caller that wants them as pipes has to read
both of them on threads of their own for as long as the process lives.

The bytes are written to the standard input and the stream is closed, which is what tells the
tool the document is complete. The write can fail, and that is not an error of the caller: a
process that met a bound in the first megabyte is already writing its refusal and will not read
the rest. The exit code is the answer in either case.

## From other runtimes

The contract is the same everywhere, and only the spelling of "spawn a process and read its
pipes without letting them grow" changes.

**.NET.** `System.Diagnostics.Process` with `RedirectStandardInput`, `RedirectStandardOutput`
and `RedirectStandardError`. Read both output streams asynchronously — `OutputDataReceived` and
`ErrorDataReceived`, or `ReadToEndAsync` on both at once — because reading one to the end while
the other fills its buffer is the classic deadlock. `WaitForExitAsync` with a
`CancellationToken` for the timeout, `Process.Kill(entireProcessTree: true)` when it fires, and
`ExitCode` afterwards.

**Node.** `child_process.spawn` with `stdio: ['pipe', 'pipe', 'pipe']`, and a counter on each
`data` event so that a runaway output is cut rather than concatenated: `Buffer.concat` over an
unbounded array is how a service inherits the memory problem it spawned a process to avoid.
`AbortSignal.timeout(30_000)` as the `signal` option, or a `setTimeout` that calls
`child.kill('SIGKILL')`, and the `close` event carries the code and the signal — a `null` code
with a signal is the kill, not a verdict.

**Python.** `subprocess.run(..., input=invoice, capture_output=True, timeout=30)` does most of
it, raising `TimeoutExpired` after killing the child, and `completed.returncode` is the answer.
`capture_output` reads without a bound, so for input from strangers use `Popen` with
`stdout=tempfile` and `stderr=tempfile` and check the sizes afterwards, or read the pipes in
fixed-size chunks and stop at a ceiling of your own.

## Batch mode is not isolation

Running many documents through one process is faster — the JVM starts once and the stylesheets
are compiled once — and it is not this model. The documents share one heap, so the one that
exhausts it takes the others with it, and they share one timeout, so a single pathological
instance spends the budget of the whole batch. A process per document is what makes one
document's failure one document's failure.

One statement covers it, and it names the input it is for: a long-lived process, or a batch,
for input you own or that is already isolated behind another boundary — your own archive, a
regression run, a migration; one process per document, with a heap ceiling on its command line
and a timeout from outside, for input from strangers. For the second, pay the start-up cost:
[`deployment-measurements.md`](deployment-measurements.md) measures it and says what an AppCDS
archive and `-XX:TieredStopAtLevel=1` take off it, and
[`validation-measurements.md`](validation-measurements.md) is what the check itself costs once
a process is running.
