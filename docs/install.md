# Installing `esj`

*Part of [EN16931 Semantic JSON](../README.md).*

Run `brew install bsnsoft/tap/esj` on macOS (Apple silicon) or Linux and you get the native
executable. From 0.9.8 on, `apt-get install esj` and `dnf install esj` install it from the [package
repositories](#debian-and-ubuntu-apt) of this project. Every
[release](https://github.com/bsnsoft/esj/releases) carries the archives below with their SHA-256.
From 0.9.2 on, the container image is published at `ghcr.io/bsnsoft/esj`. Each of the four
artefacts answers exactly as the self-contained jar does, which [`dist/smoke.sh`](#the-smoke-test)
checks.

| Artefact | Needs | Size | `validate` of a 7 kB invoice | Resident set |
|---|---|---|---|---|
| **Native executable** (recommended) | nothing | 68 MB | **0.12 s** | 110 MiB |
| Runtime image | nothing | 119 MB | 0.50 s | 203 MiB |
| Self-contained jar | a JRE 17 or newer | 13 MB | 1.41 s | 296 MiB |
| Container image | a container runtime | 579 MB | 1.04 s | — |

Measured as in [`deployment-measurements.md`](deployment-measurements.md#start-up), each artefact
with its own defaults, at a load average of 8 to 40. Read the figures as an order of magnitude, not
as a ratio. The container row is a whole `docker run` on a host where that starts a virtual
machine. Inside the container, the tool costs what the runtime image row says.

## Debian and Ubuntu (apt)

<!-- repositories-test: apt stable -->
```console
$ sudo apt-get update && sudo apt-get install ca-certificates curl
$ sudo install -d -m 0755 /etc/apt/keyrings
$ curl -fsSL https://apt.bsnsoft.de/bsnsoft-esj.gpg | sudo tee /etc/apt/keyrings/bsnsoft-esj.gpg > /dev/null
$ echo 'deb [signed-by=/etc/apt/keyrings/bsnsoft-esj.gpg] https://apt.bsnsoft.de stable main' | sudo tee /etc/apt/sources.list.d/esj.list
$ sudo apt-get update && sudo apt-get install esj
```

You get the native executable as a package for amd64 and arm64, from 0.9.8 on. `/usr/bin/esj` links
to `/usr/lib/esj/esj`, which lies beside its shared libraries. `apt-get upgrade` follows every
version published after that. GitHub Pages serves the repository and its key from the branch
`gh-pages` of this repository. The key is the one the libraries on Maven Central are signed with:
BSNSoft Solutions GmbH, `39BA1E760ADE6940558B6EEC25FD2A2F5B520EB8`.
`gpg --show-keys /etc/apt/keyrings/bsnsoft-esj.gpg` prints it.

A release candidate goes into the same suite as a release, as the version `1.2.3~rc.1`. apt and dnf
sort it before `1.2.3`, so the release replaces it.

## Fedora and RHEL-family (dnf)

<!-- repositories-test: dnf stable -->
```console
$ sudo curl -fsSL -o /etc/yum.repos.d/esj.repo https://apt.bsnsoft.de/rpm/esj.repo
$ sudo dnf install esj
```

The same host serves the rpm repository under `/rpm/`, with the same key. dnf prints its
fingerprint before it imports it. It then checks every package and the metadata against it
(`gpgcheck=1`, `repo_gpgcheck=1`). `dnf upgrade` follows every version. Instead of the `curl` line
you can run `sudo dnf config-manager addrepo --from-repofile=https://apt.bsnsoft.de/rpm/esj.repo`
(dnf5, Fedora 41 and later) or
`sudo dnf config-manager --add-repo https://apt.bsnsoft.de/rpm/esj.repo` (dnf4 with
`dnf-plugins-core`, RHEL 9).

### Which systems

The executable needs the GNU C library 2.34 or newer and zlib, nothing else. That covers Debian 12
and later, Ubuntu 22.04 and later, Fedora, and RHEL, Rocky Linux and AlmaLinux 9 and later, on
x86_64 and arm64. Debian 11 and Ubuntu 20.04 (glibc 2.31) are below that; run the container image
there. [`dist/packages-smoke.sh`](../dist/packages-smoke.sh) installs the packages in
`debian:12-slim`, `ubuntu:22.04`, `ubuntu:24.04`, `fedora:latest` and `rockylinux:9`. It then runs
a validation, a rendered letter, a PDF report and a request to `esj serve`.
[`dist/repositories-test.sh`](../dist/repositories-test.sh) runs the commands above against the
repositories. You need no X11 and no FreeType: the executable renders headless and draws its fonts
itself.

### A package without the repository

```console
$ sudo apt-get install ./esj_<version>_amd64.deb
$ sudo dnf install ./esj-<version>-1.x86_64.rpm
```

Every release from 0.9.8 on carries the four packages, `esj_<version>_<amd64|arm64>.deb` and
`esj-<version>-1.<x86_64|aarch64>.rpm`, with their checksums and attestations
([below](#checking-a-download)). A package installed this way is not upgraded. The rpm of the
repository is the same package, with a signature added when it is published.
`rpm -qp --qf '%{SHA256HEADER}\n'` prints the same header digest for both.

GitHub stores a `~` in an asset name as a dot. So a release candidate's packages carry a dot where
the package version has the `~`. The packages of `1.2.3~rc.1` are
`esj_1.2.3.rc.1_<amd64|arm64>.deb` and `esj-1.2.3.rc.1-1.<x86_64|aarch64>.rpm`, and their checksum
files name them so. Installed, the package is `1.2.3~rc.1`, as from the repository. The checksum
files of 1.0.0-rc.2 still name the `~` names, which `sha256sum -c` does not find. For those,
compare the first field of the file with `sha256sum <package>`.

## Native executable

```console
$ unzip esj-0.9.0-native-linux-arm64.zip
$ ./esj-0.9.0-native-linux-arm64/esj validate invoice.xml
```

You get one executable and, beside it, the shared libraries it loads for the fonts and images of a
rendering. Keep them together. You need no Java, no `JAVA_HOME`, nothing to install. It starts in
milliseconds and holds a third of the memory of a virtual machine. That makes one process per
document cheap ([`deployment.md`](deployment.md)). It is built with GraalVM Community Edition 25.
The defaults of the process boundary are built in: a heap ceiling of 512 MiB and an abort on heap
exhaustion, exit code 3 ([`deployment.md`](deployment.md)).

Its limits: `-Xmx` replaces the ceiling at run time, as does the container. Most other options of a
virtual machine cannot be set. And a compiler that never warms up is slower on a large document.
50 MB of UBL costs it 6.1 s against the runtime image's 2.4 s, in half the memory
([`deployment-measurements.md`](deployment-measurements.md#start-up)).

## `esj view` from the file manager

Open an XML or a PDF file from your file manager with [`esj view`](view.md). You get one page with
a tab each for the PDF as it came, the invoice rendered from its data and the validation report.
Each system offers it under *Open with*, never as the default program of a type.

- **Linux**: the deb and the rpm install `/usr/share/applications/esj.desktop`, *ESJ Viewer*,
  for `application/xml`, `text/xml` and `application/pdf`, and its icon
  `/usr/share/icons/hicolor/scalable/apps/esj.svg`; the entry runs `esj view --report %f`.
- **macOS**: the native archive carries `ESJ Viewer.app` beside `esj`. Move both together, for
  instance into `~/Applications/esj/`. The bundle runs the `esj` beside it, else the one Homebrew
  installed in `/opt/homebrew/bin` or `/usr/local/bin`. It is signed ad hoc and not notarised.
  After a download with a browser, run `xattr -dr com.apple.quarantine` on its directory, as for
  the executable ([below](#checking-a-download)). Finder lists it under *Open With* after you have
  opened it once.
- **Windows**: no artefact is built for Windows ([below](#what-a-release-carries)). Put `esj` on
  the `PATH` (the jar's `bin\esj.cmd`, for one) and write a file `esj-view.cmd` of one line,

  ```bat
  @esj view --report "%~1"
  ```

  Choose it once under *Open with → Choose another app*, and it opens the file. Nothing is written
  to the registry.

## Runtime image

```console
$ unzip esj-0.9.0-linux-arm64.zip
$ ./esj-0.9.0-linux-arm64/bin/esj validate invoice.xml
```

You get a Java 25 runtime with the jar and an ahead-of-time cache in it. Use it on a machine
without a JDK, and for the large documents where the native executable is slower. `bin/esj` passes
the defaults of the process boundary (`-Xms32m -Xmx512m -XX:+ExitOnOutOfMemoryError`) and the
cache. `ESJ_JAVA_OPTS` replaces the defaults, `ESJ_AOT=0` leaves the cache out.

## Self-contained jar

```console
$ java -jar esj.jar validate invoice.xml
$ ESJ_JAVA_OPTS='-Xmx2g' bin/esj convert --limits large big.xml
```

Every other artefact is built from this jar. It runs on any JRE from 17, the release the library is
compiled for. Embed it in an application that already has a virtual machine. It carries Saxon-HE
13.0 unrelocated, so an application with Saxon 12.x on the class path loses the HTML pages
([`java-api.md`](java-api.md)). `bin/esj` in the archive finds the jar beside it. It passes the
same options of the process boundary, and reads the same `ESJ_JAVA_OPTS`, as the one in a checkout.

## Container image

```console
$ docker pull ghcr.io/bsnsoft/esj:latest
$ docker run --rm -i --memory 1g --network none -v "$PWD:/work:ro" -w /work \
      ghcr.io/bsnsoft/esj:latest validate invoice.xml
$ docker run --rm -p 8080:8080 ghcr.io/bsnsoft/esj:latest serve
```

Every release from 0.9.2 on publishes the image at `ghcr.io/bsnsoft/esj`, for linux/amd64 and
linux/arm64. `latest` names the version published last, a release candidate as well. In a
deployment, pin the version you tested with: `ghcr.io/bsnsoft/esj:<version>`.

- Java 25 under an unprivileged account, with the ahead-of-time cache recorded on the architecture
  it runs on. `ESJ_MAX_HEAP` sets the ceiling (512 MiB by default). The image carries the security
  updates its distribution had on its build day, and not pebble, the service manager of the base
  image.
- The Java runtime starts with `-XX:-UseBASE64Intrinsics`, and so does every child of `esj serve`.
  The base64 routine of the Java releases of October 2026 writes past its array on malformed input
  (CVE-2026-114938). esj itself hands the decoder only whole groups. `ESJ_JAVA_OPTS` with
  `-XX:+UseBASE64Intrinsics` switches the routine on again.
- `esj serve` listens on port 8080 of every interface of the container (`ESJ_SERVE_BIND`) and
  serves the tools over HTTP ([`serve.md`](serve.md)). Its own process gets `ESJ_SERVE_HEAP`
  (256 MiB), every child it starts `ESJ_MAX_HEAP`.

Fetch a pack with `esj packs fetch` ([`validation.md`](validation.md#packs-made-on-this-machine))
once, in a run that has the network and writes as the owner of the directory. Then mount it
read-only into every run that validates:

```console
$ docker run --rm --user "$(id -u):$(id -g)" -v "$HOME/esj-packs:/packs" \
      ghcr.io/bsnsoft/esj:latest packs fetch peppol-bis-billing --into /packs
$ docker run --rm -i --memory 1g --network none -v "$PWD:/work:ro" -w /work \
      -v "$HOME/esj-packs:/packs:ro" -e ESJ_PACKS=/packs \
      ghcr.io/bsnsoft/esj:latest validate invoice.xml
```

```console
$ dist/package.sh docker              # the image of a checkout: esj:<version> and esj:latest
```

This builds the same image from the checkout the script runs in, for the platform of the Docker
host. `ESJ_IMAGE` gives it another name. [`dist/compose.yaml`](../dist/compose.yaml) runs it as one
container per document, and as `esj serve` (`esj-api`).

## Homebrew

[`docs/homebrew/esj.rb`](homebrew/esj.rb) is the template of the formula in the tap
[bsnsoft/homebrew-tap](https://github.com/bsnsoft/homebrew-tap). Each release fills in the
checksums of its archives there.

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
| `native` | the executable of this machine; on macOS with `ESJ Viewer.app` beside it | GraalVM 25 (`ESJ_GRAALVM_HOME`; `dist/graalvm.sh <dir>` fetches the pinned build the releases use) |
| `linux-native` | the Linux executable | Docker |
| `docker` | the container image, for the platform of the Docker host | Docker |
| `zip` | the archives of a release | `zip` |
| `smoke` | nothing; compares every artefact with the jar, its servers included | — |

Almost nothing is listed by hand. `jdeps` over the jar picks the modules of the runtime image, plus
`jdk.localedata` and `jdk.charsets`, which no class refers to by name. The GraalVM agent records
what the native executable must reach, while it runs the cases of
[`dist/cases.sh`](../dist/cases.sh). Every character set is added (`-H:+AddAllCharsets`), since the
agent does not record them. The resources (validation packs, registry, rule packs, fonts,
stylesheets) are read out of the jar. A command you add to the tool needs a case there and nothing
else. `dist/smoke.sh` fails when a command of `--help` has none.

One training run at package time writes the ahead-of-time cache. The cache belongs to the exact
virtual machine that wrote it, so the runtime image carries one and the jar does not.

## The smoke test

```console
$ dist/smoke.sh dist/out/esj-0.9.0-native-macos-arm64/esj \
      --small-heap 'dist/out/esj-0.9.0-native-macos-arm64/esj -Xmx8m'
```

The smoke test runs every case of [`dist/cases.sh`](../dist/cases.sh) twice: once with the
artefact, once with the self-contained jar. The first difference in what they write, in the verdict
or in the exit code fails the run. `--small-heap` adds the one run that must end with exit code 3,
the abort of the process boundary. `--full` takes the whole corpus instead of the subset.
`dist/package.sh smoke` runs it against every artefact that has been built.

## What a release carries

| Archive | Contents |
|---|---|
| `esj-<version>-native-<os>-<arch>.zip` | the executable, its shared libraries, `LICENSE`, `NOTICE`, the licences of the Java runtime parts in it (`legal/`); on macOS `ESJ Viewer.app` ([above](#esj-view-from-the-file-manager)) |
| `esj-<version>-<os>-<arch>.zip` | the runtime image, `bin/esj`, the jar, the cache, `LICENSE`, `NOTICE` |
| `esj-<version>.zip` | the jar, `bin/esj`, `LICENSE`, `NOTICE`, the documentation of the command line |
| `esj_<version>_<arch>.deb`, `esj-<version>-1.<arch>.rpm` | the native Linux executable as a package for apt and dnf, for x64 and arm64, with the desktop entry of `esj view`; a release candidate with a dot for the `~` of its version ([above](#a-package-without-the-repository)) |

Beside each archive and package lies a `.sha256` file with its checksum, written by
`dist/package.sh zip` and `dist/packages.sh`. A tag builds the archives on Linux (x64 and arm64)
and macOS (arm64). It packs the two Linux executables into the four packages and attaches all of
them, with their checksums, to its release. No Windows artefact is built. `jpackage` and
`native-image` would produce one from the same jar on a Windows runner, and no one has run it.

## Checking a download

```console
$ sha256sum -c <archive>.sha256            # shasum -a 256 -c on macOS
$ gh attestation verify <archive> --repo bsnsoft/esj
$ gh attestation verify oci://ghcr.io/bsnsoft/esj:<version> --repo bsnsoft/esj
```

The checksum catches a damaged download. The attestation tells you where an artefact came from.
From 0.9.5 on, the archives and the image carry a build provenance attestation, signed through
Sigstore and kept by GitHub. It names the workflow, the commit and the tag they were built from.
The packages carry one from 0.9.8 on. The key `39BA1E760ADE6940558B6EEC25FD2A2F5B520EB8` (BSNSoft
Solutions GmbH) signs the artefacts on Maven Central, the indices of the package repositories and
the rpm packages in them. The macOS executable is signed ad hoc and not notarised. Downloaded with
a browser, Gatekeeper blocks it until you run `xattr -dr com.apple.quarantine` on its directory.
Homebrew sets no quarantine flag.
