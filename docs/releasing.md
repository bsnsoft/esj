# Releasing

*Part of [EN16931 Semantic JSON](../README.md).*

A release is a tag. `release.yml` builds the archives of the command line tool from it and
attaches them to the GitHub release; publishing that release runs `publish.yml`, which deploys
the libraries to Maven Central. Nothing is published from a workstation.

## What each channel carries

| Channel | Artefacts |
|---|---|
| Maven Central, `de.bsnsoft.esj` | the eleven modules a build can depend on, `esj-generator` among them, each with its source and Javadoc jar; the aggregator POM they inherit from; `esj-bom` |
| GitHub release assets | the native executables, the runtime image, the release zip and their checksums ([`install.md`](install.md)) |

`esj-cli` is not on Central: it is the tool, not a library, and `excludeArtifacts` in the
`release` profile leaves it out of the deployment.

## Cutting a release

```sh
mvn -B versions:set -DnewVersion=0.9.0 -DgenerateBackupPoms=false
```

Then, in the same commit: `project.build.outputTimestamp` in `pom.xml` to the release date, and
the heading of the version in [`CHANGELOG.md`](../CHANGELOG.md) from `unreleased` to that date.

```sh
mvn -B clean verify
git commit -am 'Release 0.9.0'
git tag v0.9.0
git push origin main v0.9.0
```

The tag runs `release.yml`. Publish the draft release it fills, which runs `publish.yml`: it
refuses a snapshot version and a tag that does not name the version of the POM, signs every
file, and hands the reactor to the Portal as one deployment that is validated and published or
rejected as a whole. Afterwards:

```sh
mvn -B versions:set -DnewVersion=0.9.1-SNAPSHOT -DgenerateBackupPoms=false
git commit -am 'Back to a snapshot version'
```

`publish.yml` can also be started by hand with the tag as its input, for a release whose
deployment failed after the assets were attached.

## Set up once

1. An account on [central.sonatype.com](https://central.sonatype.com) for the publisher.
2. The namespace `de.bsnsoft`, registered there and verified by the `TXT` record the Portal
   names, on `bsnsoft.de`. It covers every `de.bsnsoft.*` group id.
3. A user token, generated in the Portal account, as the two repository secrets
   `CENTRAL_USERNAME` and `CENTRAL_PASSWORD`. It is not the account password.
4. An OpenPGP key pair for signing, its public key on `keys.openpgp.org` — the Portal verifies
   a signature against a public keyserver — and as repository secrets the armoured private key
   in `GPG_PRIVATE_KEY` and its passphrase in `GPG_PASSPHRASE`. The workflow imports that one
   key and signs with it, so the key needs no name in the POM.

The four secrets are the whole configuration `publish.yml` reads. A deployment cannot be
withdrawn once it is published, and a version cannot be published twice.
