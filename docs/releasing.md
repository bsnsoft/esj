# Releasing

*Part of [EN16931 Semantic JSON](../README.md).*

A release is a tag. `release.yml` builds the archives and the Linux packages of the command line
tool from it, attaches them to the GitHub release, pushes the container image to the GitHub
container registry and the packages to the apt and dnf repositories at `https://apt.bsnsoft.de`;
publishing that release runs `publish.yml`, which deploys the libraries to Maven Central. Nothing
is published from a workstation.

## What each channel carries

| Channel | Artefacts |
|---|---|
| Maven Central, `de.bsnsoft.esj` | the ten modules a build can depend on, each with its source and Javadoc jar; the aggregator POM they inherit from; `esj-bom`, flattened (no parent, only these modules managed) |
| GitHub release assets | the native executables, the runtime image, the release zip, the four Linux packages and their checksums ([`install.md`](install.md)) |
| Package repositories, `https://apt.bsnsoft.de` | the `.deb` and `.rpm` packages in an apt repository at the root and a dnf repository under `rpm/`, in the suite `stable` for a release and `testing` for a pre-release, served by GitHub Pages from the branch `gh-pages` ([`install.md`](install.md#debian-and-ubuntu-apt)) |
| GitHub container registry, `ghcr.io/bsnsoft/esj` | the container image of `dist/Dockerfile` for linux/amd64 and linux/arm64: the tags `<version>` and `latest` name both platforms, `<version>-linux-amd64` and `<version>-linux-arm64` one each ([`install.md`](install.md#container-image)) |

`esj-cli` and `esj-generator` are not on Central: one is the tool, the other the build tool of
this repository, and `excludeArtifacts` in the `release` profile leaves both out of the deployment.

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
Its first job compares the tag with the version of the POM and stops a snapshot, a tag of another
version and a tag not of the form `v1.2.3` or `v1.2.3-rc.1` before anything is built.
That release fires no `release` event — GitHub raises none for a release a workflow created with
its own token — so `publish.yml` is started by hand, on the tag, with the tag as its input
(`gh workflow run publish.yml --ref v0.9.0 -f tag=v0.9.0`; the workflow file comes from `--ref`,
the code from the tag, so a workflow fixed on `main` after the tag was cut runs with `--ref main`): it
refuses a snapshot version and a tag that does not name the version of the POM, signs every
file, and hands the reactor to the Portal as one deployment that is validated and published or
rejected as a whole. Afterwards:

```sh
mvn -B versions:set -DnewVersion=0.9.1-SNAPSHOT -DgenerateBackupPoms=false
git commit -am 'Back to a snapshot version'
```

The same commit sets `esj.api-baseline` in `pom.xml` to the version just released.

The same command repeats a deployment that failed; a version the Portal has published cannot be
deployed again. The deployment runs on Maven 3.9.16, which the workflow downloads and checks:
with the Maven 3.10.0 of newer runner images, central-publishing-maven-plugin 0.11.0 bundles the
repository metadata, and the Portal refuses the bundle.

`mvn -B -Papi-check verify -DskipTests` compares the API of every library module with the release
`esj.api-baseline` names (`pom.xml`) and writes `target/japicmp/api-check.html` per module; the CI
job *API compared with the last release* keeps it as the artifact `api-check`. A class listed as
removed or modified incompatibly is a break, except where an element of the baseline became
internal or `@Preview`. Before 1.0.0 the check reports; from 1.0.0 `esj.api-check.break` is `true`
and the baseline is the last release.

Each of the three packaging jobs of `release.yml` delivers the archives of its platform, and the
`linux-x64` job also the platform-independent `esj-<version>.zip`; the other two build one as
well and leave it out of their upload. Before anything is attached, the release job checks every
`.sha256` against the archive beside it: a checksum that does not match, a name two jobs
delivered with different contents, or, on a tag, no `esj-<version>.zip` of the version the tag
names stops the release. Then it attests the provenance of every archive and attaches them.
Releases are immutable once published: an archive that turns out broken is fixed by the next
patch release, never by replacing the asset.

Beside the archives, on one Linux runner per processor, `release.yml` builds the container image
natively, compares it with the jar and pushes it as `<version>-linux-amd64` and
`<version>-linux-arm64`; once both are there, it joins them under `<version>` and `latest` and
checks that both tags list both platforms, and attests the provenance of the joined image. It
pushes only from a `v*` tag that names the version of the jar — started by hand on a branch, it
builds and compares the image and pushes nothing — and every run on a tag moves `latest` to that
tag, so re-running the workflow of an older release moves `latest` back.

## The Linux packages and their repositories

The job *Linux packages* packs the native executables of the two Linux jobs into
`esj_<version>_<amd64|arm64>.deb` and `esj-<version>-1.<x86_64|aarch64>.rpm` with nfpm
(`dist/packages.sh`, nfpm pinned by version and SHA-256 in `dist/nfpm.sh`): a pre-release
`1.2.3-rc.1` becomes `1.2.3~rc.1`, which apt and dnf sort before `1.2.3`. Every executable is
checked first against the oldest C library the packages require, `ESJ_GLIBC_FLOOR` in
`dist/checks.sh` (glibc 2.34), as the native build checks it. On a runner of each processor the
job *Linux packages on …* then installs them in Debian, Ubuntu, Fedora and Rocky Linux and runs
the tool there (`dist/packages-smoke.sh`), and publishes them into apt and dnf repositories signed
with a throwaway key and installs them from there with the commands of
[`install.md`](install.md#debian-and-ubuntu-apt) (`dist/repositories-test.sh`). The release job
attaches them with the archives, checked and attested the same way, and only after both tests.

The job *Package repositories* runs on a `v*` tag, after the release job, one tag at a time. It
checks out the branch `gh-pages` (or starts it as an orphan where there is none), imports the
release key from the secrets `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` and makes one signature with
it before anything else, adds the four packages to the suite of the tag (`stable` for a release,
`testing` for a pre-release), regenerates every index from the packages on the branch
(`dist/repositories.sh`: `apt-ftparchive`, `createrepo_c`), signs the indices and every rpm that
has no signature yet, and pushes the branch as `github-actions[bot]`; GitHub Pages then publishes
it. Only this job reads the release key. The rpm in the repository is the attached package with a
signature added (the header digest is the same); the `.deb` is the same file. A published package
is never replaced: a package of the same name with other contents stops the job.

The branch is the whole state of the repositories: every version stays on it, and nothing else is
kept. GitHub Pages publishes at most 1 GB, and every release adds about 120 MB, so the job warns
above 750 MB and stops above 1 GB. Pruning is by hand: delete the old packages from
`pool/main/e/esj/` and `rpm/<suite>/<arch>/` on `gh-pages`, push, and at once re-run the job
*Package repositories* of the latest release, which regenerates every index from what is left and
signs it — until it has, the indices name packages that are gone:

```sh
gh run list --workflow release.yml --limit 5          # the run of the latest tag
gh run view <run-id> --json jobs --jq '.jobs[] | select(.name == "Package repositories") | .databaseId'
gh run rerun <run-id> --job <that id>
```

The same re-run repeats a publication that failed, for as long as the run keeps its artifacts
(90 days); a package already on the branch is left as it is. The history of `gh-pages` keeps the
pruned files; to shrink the branch itself, replace it by a single commit of its current tree. The
site has the custom domain `apt.bsnsoft.de`, which GitHub keeps in the file `CNAME` at the root of
the branch; the job leaves that file alone.

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

The four secrets are the whole configuration `publish.yml` reads; they are organisation secrets
that only this repository may read. A deployment cannot be withdrawn once it is published, and a
version cannot be published twice.

The package repositories are signed with the same key, read from `GPG_PRIVATE_KEY` and
`GPG_PASSPHRASE` by the job *Package repositories*; `release.yml` names its fingerprint and stops
when the secret holds another key. The container image and the attestations need no secret: `release.yml` pushes the image with the
token of the workflow run and signs the attestations with a certificate issued to that run.
Once, after the first release that pushes it, check the package `esj` among the organisation's
packages on GitHub. A package a workflow creates is private, whatever the visibility of the
repository, until it is made public in its *Package settings*, under *Danger Zone*, *Change
visibility*; a public package cannot be made private again. The same page names the repository
the package is linked to, which has to be `bsnsoft/esj`: the workflow links it by itself, and
the package takes its access from that repository.
