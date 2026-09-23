# Installing `esj`

*Part of [EN16931 Semantic JSON](../README.md).*

`brew install bsnsoft/tap/esj` installs the native executable on macOS (Apple silicon) and Linux;
the archives below are attached to the [release](https://github.com/bsnsoft/esj/releases) with
their SHA-256, and from 0.9.2 on the container image is published at `ghcr.io/bsnsoft/esj`. Four
artefacts, one tool: each of them answers exactly as the self-contained jar does, which
`dist/smoke.sh` checks by running every command of the tool with both and comparing what they
write, the verdict they reach and the code they leave with.

| Artefact | Needs | Size | `validate` of a 7 kB invoice | Resident set |
|---|---|---|---|---|
| **Native executable** (recommended) | nothing | 68 MB | **0.12 s** | 110 MiB |
| Runtime image | nothing | 119 MB | 0.50 s | 203 MiB |
| Self-contained jar | a JRE 17 or newer | 13 MB | 1.41 s | 296 MiB |
| Container image | a container runtime | 579 MB | 1.04 s | — |

Measured on the machine and with the method of
[`deployment-measurements.md`](deployment-measurements.md#start-up), at a one-minute load
average between 8 and 40, which is the condition that costs a just-in-time compiler most and an
executable that never warms up least: read the column as an order of magnitude and not as a
ratio. The rows are the artefacts as they ship, with their own defaults. The container row is a
whole `docker run` on a host where that starts a virtual machine, and the resident set of the
container is not what the measurement sees; what the tool costs inside it is the runtime image
row.

## Native executable

```console
$ unzip esj-0.9.0-native-linux-arm64.zip
$ ./esj-0.9.0-native-linux-arm64/esj validate invoice.xml
```

One executable and, beside it, the shared libraries it loads for the fonts and images of a
rendering; keep them together. No Java, no `JAVA_HOME`, nothing to install. It starts in
milliseconds and holds a third of the memory of a virtual machine, which is what makes one
process per document cheap ([`deployment.md`](deployment.md)). Built with GraalVM Community
Edition 25.

It carries the defaults of the process boundary built in — a heap ceiling of 512 MiB and an
abort on heap exhaustion, the same two the other artefacts pass on their command line — so a
heap that runs out ends the process with exit code 3 and not a process that is alive and
useless ([`deployment.md`](deployment.md)).

Its limits: `-Xmx` replaces the ceiling at run time, as does the container, but most of the
options of a virtual machine are not there to be set; and a compiler that never warms up is the
slower one on a large document — 50 MB of UBL costs it 6.1 s against the runtime image's 2.4 s,
in half the memory ([`deployment-measurements.md`](deployment-measurements.md#start-up)).

## Runtime image

```console
$ unzip esj-0.9.0-linux-arm64.zip
$ ./esj-0.9.0-linux-arm64/bin/esj validate invoice.xml
```

A Java 25 runtime with the jar and an ahead-of-time cache in it, for a machine without a JDK
and for the documents the native executable is not the better answer for. `bin/esj` passes the
defaults of the process boundary — `-Xms32m -Xmx512m -XX:+ExitOnOutOfMemoryError` — and the
cache; `ESJ_JAVA_OPTS` replaces the first, `ESJ_AOT=0` leaves the cache out.

## Self-contained jar

```console
$ java -jar esj.jar validate invoice.xml
$ ESJ_JAVA_OPTS='-Xmx2g' bin/esj convert --limits large big.xml
```

The jar every other artefact is built from. It runs on any JRE from 17, which is the release
the library is compiled for, and it is what to embed in an application that already has a
virtual machine. `bin/esj` in the archive resolves the jar beside it and passes the same
options of the process boundary, and the same `ESJ_JAVA_OPTS`, as the one in a checkout.

## Container image

```console
$ docker pull ghcr.io/bsnsoft/esj:latest
$ docker run --rm -i --memory 1g --network none -v "$PWD:/work:ro" -w /work \
      ghcr.io/bsnsoft/esj:latest validate invoice.xml
```

Every release from 0.9.2 on publishes the image at `ghcr.io/bsnsoft/esj`, for linux/amd64 and
linux/arm64. `latest` follows the newest release; a deployment pins the version it was tested
with, `ghcr.io/bsnsoft/esj:<version>`. Java 25, an unprivileged account, the ahead-of-time cache
recorded on the architecture it runs on, and `ESJ_MAX_HEAP` for the ceiling (512 MiB by default).

```console
$ dist/package.sh docker              # the image of a checkout: esj:<version> and esj:latest
```

The same image, built from the checkout the script runs in and for the platform of the Docker
host; `ESJ_IMAGE` gives it another name. [`dist/compose.yaml`](../dist/compose.yaml) runs it as a
service: one container run per document, a memory limit above the heap, a read-only mount and no
network.

## Homebrew

A draft formula for a tap is [`docs/homebrew/esj.rb`](homebrew/esj.rb). It is not submitted
anywhere and names the two values a release fills in.

## Building the artefacts

```console
$ mvn -B verify                       # the tests, on JDK 17, 21 or 25
$ dist/package.sh --all               # every artefact this host can build
$ dist/package.sh native smoke        # one of them, and the comparison with the jar
```

| Target | What it writes | Needs |
|---|---|---|
| `jar` | `esj-cli/target/esj.jar` | JDK 17 or newer |
| `runtime-image` | a runtime image with the cache | JDK 25 (`ESJ_JDK25_HOME`) |
| `native` | the executable of this machine | GraalVM 25 (`ESJ_GRAALVM_HOME`; `dist/graalvm.sh <dir>` fetches the pinned build the releases use) |
| `linux-native` | the Linux executable | Docker |
| `docker` | the container image, for the platform of the Docker host | Docker |
| `zip` | the archives of a release | `zip` |
| `smoke` | nothing; compares every artefact with the jar | — |

Nothing is listed by hand: the modules of the runtime image come from `jdeps` over the jar,
what the native executable must reach is recorded by running the cases of
[`dist/cases.sh`](../dist/cases.sh) under the GraalVM agent, and its resources — the validation
packs, the registry, the rule packs, the fonts and the stylesheets — are read out of the jar. A
command added to the tool needs a case there and nothing else, and `dist/smoke.sh` fails when
a command of `--help` has none.

The ahead-of-time cache is written by one training run at package time and belongs to the exact
virtual machine that wrote it; it is why the runtime image carries one and the jar does not.

## The smoke test

```console
$ dist/smoke.sh dist/out/esj-0.9.0-native-macos-arm64/esj \
      --small-heap 'dist/out/esj-0.9.0-native-macos-arm64/esj -Xmx8m'
```

Every case of [`dist/cases.sh`](../dist/cases.sh) run twice — once with the artefact, once with
the self-contained jar — and the first difference in what they write, in the verdict they reach
or in the code they leave with fails the run. `--small-heap` adds the one run that must end
with exit code 3, the abort of the process boundary. `--full` takes the whole corpus instead of
the subset, and `dist/package.sh smoke` runs it against every artefact that has been built.

## What a release carries

| Archive | Contents |
|---|---|
| `esj-<version>-native-<os>-<arch>.zip` | the executable, its shared libraries, `LICENSE`, `NOTICE`, the licences of the Java runtime parts in it (`legal/`) |
| `esj-<version>-<os>-<arch>.zip` | the runtime image, `bin/esj`, the jar, the cache, `LICENSE`, `NOTICE` |
| `esj-<version>.zip` | the jar, `bin/esj`, `LICENSE`, `NOTICE`, the documentation of the command line |

Beside each archive a `.sha256` file with its checksum, written by `dist/package.sh zip`. A
tag builds all three on Linux (x64 and arm64) and macOS (arm64) and attaches them, with their
checksums, to its release; from 0.9.2 on it also pushes the container image, for linux/amd64
and linux/arm64, to `ghcr.io/bsnsoft/esj`. A Windows artefact is not built: `jpackage` and
`native-image` produce one from the same jar on a Windows runner, and no one has run it.
