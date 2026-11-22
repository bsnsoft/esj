# Security

## Supported version

The latest release. Nothing older is patched: a fix goes into the next release. A report against
an earlier release is first checked against the latest one.

## Reporting

Report a vulnerability through the repository's *Security* tab, *Report a vulnerability*
(GitHub private vulnerability reporting). Please do not open a public issue for it, and do not
send it by e-mail: this project publishes no address for that. Include the input that shows it,
the command, and the version from `esj --version`. One author runs this project. Expect an
answer within days, not hours, and a fix in the next release rather than in a dated window.

## Checking a release

Check where a release came from and that nobody changed it, with the commands of
[`docs/install.md`](docs/install.md#checking-a-download):

- The archives and the container image from 0.9.5 on, and the Linux packages from 0.9.8 on,
  carry a build provenance attestation. `gh attestation verify <file> --repo bsnsoft/esj` checks it.
- The artefacts on Maven Central, the indices of the apt and dnf repositories at
  `https://apt.bsnsoft.de` and their rpm packages are signed with the OpenPGP key of BSNSoft
  Solutions GmbH, fingerprint `39BA1E760ADE6940558B6EEC25FD2A2F5B520EB8`.
- The macOS executable is signed ad hoc and not notarised.

## What counts

| In scope | Not in scope |
|---|---|
| A document that exhausts memory or time out of proportion to its size, past the bounds `--limits` documents | A run that reaches a configured bound and leaves with exit code 7, having reported it |
| A verdict of `VALID` on an invoice that the official artefacts of its profile reject | A finding of this project's own rules you disagree with, and a difference from the oracle that `docs/validation.md` already names |
| Anything that reads or writes outside the files the command was given: an entity, a schema, a stylesheet or a host reached from a document, a path escaped from | A file the command was told to write, and a temporary file under the directory the runtime was given |
| A rendering that executes script or fetches a resource when it is opened | The warning a rendering carries about its own content |
| A packaged artefact that carries something the sources do not | A dependency's advisory with no path to it from this code; report it, it is welcome, but it is not this project's finding |

## What the project does about untrusted input

Every document is input somebody else wrote. The library parses it behind one XML front door,
which resolves no entity, fetches no schema, processes no XInclude and executes no stylesheet it
was not given. It reads within the bounds of `--limits` (specification, section 12.2). For input
you do not control, run the packaged command line as a separate process with a heap ceiling and a
timeout: a document that exhausts a resource then costs one process
([`docs/deployment.md`](docs/deployment.md), [`docs/install.md`](docs/install.md)). ESJ opens a
PDF as a container only. It reads nothing off the page, and an attachment is what the container
declares it to be.

A verdict of `VALID` speaks about EN 16931 and the packs the report names. It does not say that
the document is safe to act on.
