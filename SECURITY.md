# Security

## Supported version

The latest release. Nothing older is patched: a fix goes into the next release, and a report
against an earlier one is checked against the latest release first.

## Reporting

Report a vulnerability through the repository's *Security* tab, *Report a vulnerability*
(GitHub private vulnerability reporting). Please do not open a public issue for one, and do
not send it by e-mail; this project publishes no address for it. Include the input that shows
it, the command, and the version from `esj --version`.

This is a project of one author: expect an answer in days rather than hours, and
a fix in the next release rather than in a dated window.

## What counts

| In scope | Not in scope |
|---|---|
| A document that exhausts memory or time out of proportion to its size, past the bounds `--limits` documents | A run that reaches a configured bound and leaves with exit code 7, having reported it |
| A verdict of `VALID` on an invoice that the official artefacts of its profile reject | A finding of this project's own rules you disagree with, and a difference from the oracle that `docs/validation.md` already names |
| Anything that reads or writes outside the files the command was given: an entity, a schema, a stylesheet or a host reached from a document, a path escaped from | A file the command was told to write, and a temporary file under the directory the runtime was given |
| A rendering that executes script or fetches a resource when it is opened | The warning a rendering carries about its own content |
| A packaged artefact that carries something the sources do not | A dependency's advisory with no path to it from this code; report it, it is welcome, but it is not this project's finding |

## What the project does about untrusted input

Every document is input somebody else wrote. The library parses it behind one XML front door
that resolves no entity, fetches no schema, processes no XInclude and executes no stylesheet it
was not given, and reads it within the bounds of `--limits` (specification, section 12.2). The
recommended way to run it over input you do not control is the packaged command line as a
separate process with a heap ceiling and a timeout, so that a document that exhausts a resource
costs one process ([`docs/deployment.md`](docs/deployment.md), [`docs/install.md`](docs/install.md)).
PDF input is opened as a container only: nothing is read off the page of a PDF, and an
attachment is what the container declares it to be.

Validation says what an invoice is, not what it is worth: a verdict of `VALID` is a statement
about EN 16931 and the packs named in the report, not a statement that the document is safe to
act on.
