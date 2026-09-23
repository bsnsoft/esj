# Releasing

*Part of [EN16931 Semantic JSON](../README.md).*

A release is a tag. `release.yml` builds the archives of the command line tool from it, attaches
them to the GitHub release and pushes the container image to the GitHub container registry;
publishing that release runs `publish.yml`, which deploys the libraries to Maven Central. Nothing
is published from a workstation.

## What each channel carries

| Channel | Artefacts |
|---|---|
| Maven Central, `de.bsnsoft.esj` | the eleven modules a build can depend on, `esj-generator` among them, each with its source and Javadoc jar; the aggregator POM they inherit from; `esj-bom` |
| GitHub release assets | the native executables, the runtime image, the release zip and their checksums ([`install.md`](install.md)) |
| GitHub container registry, `ghcr.io/bsnsoft/esj` | the container image of `dist/Dockerfile` for linux/amd64 and linux/arm64: the tags `<version>` and `latest` name both platforms, `<version>-linux-amd64` and `<version>-linux-arm64` one each ([`install.md`](install.md#container-image)) |

`esj-cli` is not on Central: it is the tool, not a library, and `excludeArtifacts` in the
`release` profile leaves it out of the deployment.

## Cutting a release

```sh
mvn -B versions:set -DnewVersion=0.9.0 -DgenerateBackupPoms=false
```

Then, in the same commit: `project.build.outputTimestamp` in `pom.xml` to the release date, the
heading of the version in [`CHANGELOG.md`](../CHANGELOG.md) from `unreleased` to that date, and the
artifact version in the identifier table of [`java-api.md`](java-api.md). `MavenCoordinatesTest`
holds every coordinate a page offers to the current version or to the last release the changelog dates.

```sh
mvn -B clean verify
git commit -am 'Release 0.9.0'
git tag v0.9.0
git push origin main v0.9.0
```

The tag runs `release.yml`, which builds the archives and publishes the GitHub release with them.
That release fires no `release` event — GitHub raises none for a release a workflow created with
its own token — so `publish.yml` is started by hand, on the tag, with the tag as its input
(`gh workflow run publish.yml --ref v0.9.0 -f tag=v0.9.0`): it
refuses a snapshot version and a tag that does not name the version of the POM, signs every
file, and hands the reactor to the Portal as one deployment that is validated and published or
rejected as a whole. Afterwards:

```sh
mvn -B versions:set -DnewVersion=0.9.1-SNAPSHOT -DgenerateBackupPoms=false
git commit -am 'Back to a snapshot version'
```

The same command repeats a deployment that failed; a version the Portal has published cannot be
deployed again.

Beside the archives, on one Linux runner per processor, `release.yml` builds the container image
natively, compares it with the jar and pushes it as `<version>-linux-amd64` and
`<version>-linux-arm64`; once both are there, it joins them under `<version>` and `latest` and
checks that both tags list both platforms. It pushes only from a `v*` tag that names the version
of the jar — started by hand on a branch, it builds and compares the image and pushes nothing —
and every run on a tag moves `latest` to that tag, so re-running the workflow of an older release
moves `latest` back.

## Set up once

1. An account on [central.sonatype.com](https://central.sonatype.com) for the publisher.
2. The namespace `de.bsnsoft`, registered there and verified by the `TXT` record the Portal
   names, on `bsnsoft.de`. It covers every `de.bsnsoft.*` group id.
3. A user token, generated in the Portal account, as the two repository secrets
   `CENTRAL_USER` and `CENTRAL_PASSWORD`. It is not the account password.
4. An OpenPGP key pair for signing, its public key on `keys.openpgp.org` — the Portal verifies
   a signature against a public keyserver — and as repository secrets the armoured private key
   in `GPG_PRIVATE_KEY` and its passphrase in `GPG_PASSPHRASE`. The workflow imports that one
   key and signs with it, so the key needs no name in the POM.

The four secrets are the whole configuration `publish.yml` reads. A deployment cannot be
withdrawn once it is published, and a version cannot be published twice.

The container image needs no secret: `release.yml` pushes it with the token of the workflow run.
Once, after the first release that pushes it, check the package `esj` among the organisation's
packages on GitHub. A package a workflow creates is private, whatever the visibility of the
repository, until it is made public in its *Package settings*, under *Danger Zone*, *Change
visibility*; a public package cannot be made private again. The same page names the repository
the package is linked to, which has to be `bsnsoft/esj`: the workflow links it by itself, and
the package takes its access from that repository.
