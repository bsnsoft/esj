# Releasing

*Part of [EN16931 Semantic JSON](../README.md).*

A release is a tag. From it, `release.yml` builds the archives and the Linux packages of the
command line tool and attaches them to the GitHub release. It pushes the container image to the
GitHub container registry and the packages to the apt and dnf repositories at
`https://apt.bsnsoft.de`. `publish.yml` deploys the libraries to Maven Central. Nothing is
published from a workstation.

## What each channel carries

| Channel | Artefacts |
|---|---|
| Maven Central, `de.bsnsoft.esj` | the ten modules a build can depend on, each with its source and Javadoc jar; the aggregator POM they inherit from; `esj-bom`, flattened (no parent, only these modules managed) |
| GitHub release assets | the native executables, the runtime image, the release zip, the four Linux packages and their checksums ([`install.md`](install.md)) |
| Package repositories, `https://apt.bsnsoft.de` | the `.deb` and `.rpm` packages in an apt repository at the root and a dnf repository under `rpm/`, in one suite, `stable`, with every version, served by GitHub Pages from the branch `gh-pages` ([`install.md`](install.md#debian-and-ubuntu-apt)) |
| GitHub container registry, `ghcr.io/bsnsoft/esj` | the container image of `dist/Dockerfile` for linux/amd64 and linux/arm64: the tags `<version>` and `latest` name both platforms, `<version>-linux-amd64` and `<version>-linux-arm64` one each ([`install.md`](install.md#container-image)) |

`esj-cli` and `esj-generator` are not on Central. One is the tool, the other the build tool of this
repository. `excludeArtifacts` in the `release` profile leaves both out of the deployment.

## Cutting a release

```sh
mvn -B versions:set -DnewVersion=0.9.0 -DgenerateBackupPoms=false
```

Then, in the same commit, set `project.build.outputTimestamp` in `pom.xml` to the release date.
Change the heading of the version in [`CHANGELOG.md`](../CHANGELOG.md) from `unreleased` to that
date, and set the artifact version in the identifier table of [`java-api.md`](java-api.md).
`MavenCoordinatesTest` holds every coordinate a page offers to the current version or to the last
release the changelog dates.

```sh
mvn -B clean verify
git commit -am 'Release 0.9.0'
git tag v0.9.0
git push origin main v0.9.0
```

The tag runs `release.yml`, which builds the archives and publishes the GitHub release with them.
Its first job compares the tag with the version of the POM. Before anything is built, it stops a
snapshot, a tag of another version and a tag not of the form `v1.2.3` or `v1.2.3-rc.1`. That
release fires no `release` event: GitHub raises none for a release a workflow created with its own
token. So start `publish.yml` by hand, on the tag, with the tag as its input:
`gh workflow run publish.yml --ref v0.9.0 -f tag=v0.9.0`. The workflow file comes from `--ref`, the
code from the tag. A workflow fixed on `main` after the tag was cut runs with `--ref main`.
`publish.yml` refuses a snapshot version and a tag that does not name the version of the POM. It
signs every file and hands the reactor to the Portal as one deployment, validated and published or
rejected as a whole. Afterwards:

```sh
mvn -B versions:set -DnewVersion=0.9.1-SNAPSHOT -DgenerateBackupPoms=false
git commit -am 'Back to a snapshot version'
```

In the same commit, set `esj.api-baseline` in `pom.xml` to the version just released. The same
`gh workflow run` command repeats a deployment that failed. A version the Portal has published
cannot be deployed again. The deployment runs on Maven 3.9.16, which the workflow downloads and
checks. With the Maven 3.10.0 of newer runner images, central-publishing-maven-plugin 0.11.0
bundles the repository metadata, and the Portal refuses the bundle.

`mvn -B -Papi-check verify -DskipTests` compares the API of every library module with the release
`esj.api-baseline` names (`pom.xml`). It writes `target/japicmp/api-check.html` per module. The CI
job *API compared with the last release* keeps it as the artifact `api-check`. A class listed as
removed or modified incompatibly is a break, except where an element of the baseline became
internal or `@Preview`. From 1.0.0 `esj.api-check.break` is `true`: an incompatible change fails
the build, and the baseline is the last release.

Each of the three packaging jobs of `release.yml` delivers the archives of its platform. The
`linux-x64` job also delivers the platform-independent `esj-<version>.zip`; the other two build one
as well and leave it out of their upload. Before anything is attached, the release job checks every
`.sha256` against the archive beside it. Three things stop the release: a checksum that does not
match, a name two jobs delivered with different contents, and, on a tag, no `esj-<version>.zip` of
the version the tag names. Then the job attests the provenance of every archive and attaches them.
Releases are immutable once published. Fix an archive that turns out broken with the next patch
release, never by replacing the asset.

Beside the archives, on one Linux runner per processor, `release.yml` builds the container image
natively. It compares the image with the jar and pushes it as `<version>-linux-amd64` and
`<version>-linux-arm64`. Once both are there, it joins them under `<version>` and `latest`, checks
that both tags list both platforms, and attests the provenance of the joined image. It pushes only
from a `v*` tag that names the version of the jar. Started by hand on a branch, it builds and
compares the image and pushes nothing. Every run on a tag, a release candidate's as well, moves
`latest` to that tag. So re-running the workflow of an older tag moves `latest` back; [publishing a
version again](#publishing-a-version-again) moves it without a build.

## Pre-releases

Cut a release candidate like a release, with the version `1.0.0-rc.1` and the tag `v1.0.0-rc.1`.
Its changelog heading is `[1.0.0-rc.1]`, and the tree is `1.0.0-SNAPSHOT` before and after it. It
is published like a release, on every channel: the container image under `1.0.0-rc.1` and `latest`,
the Linux packages in the suite `stable` as `1.0.0~rc.1`, which apt and dnf sort before `1.0.0`
([`install.md`](install.md#debian-and-ubuntu-apt)), and the libraries on Maven Central under that
version (`publish.yml`). The Homebrew formula and the coordinates the pages offer name it, as they
name a release. Only GitHub's mark differs. `release.yml` creates the GitHub release as a draft,
attaches the assets and then publishes it as a pre-release, which GitHub does not show as the
latest release. Releases are immutable once published, so the assets have to be there first;
`v1.0.0-rc.1` was published before its assets and is a tag without a release. The packages are
attached as `esj_1.0.0.rc.1_<arch>.deb` and `esj-1.0.0.rc.1-1.<arch>.rpm`, the names GitHub stores
a `~` under, with checksum files that name them so (`dist/packages.sh --release-names`; the
checksum files of `v1.0.0-rc.2` still name the `~`). Afterwards `esj.api-baseline` names the
candidate, and the API check compares with it.

Up to 1.0.0-rc.2, a candidate left `latest` on the last release and went to a second suite,
`testing`. The first run of `dist/repositories.sh` over that site moves the rpm packages of
`testing` into `stable`, the same files. It removes `dists/testing`, `rpm/testing` and
`rpm/esj-testing.repo`.

## Publishing a version again

```sh
gh workflow run release.yml --ref main -f publish_version=1.0.0-rc.2
```

Started with `publish_version`, `release.yml` builds, attaches and deploys nothing. The job
*Version to publish* checks that the version has the form of a release tag's and that its GitHub
release is published. *Image tag latest* then points `latest` at the multi-platform image of that
version. It copies the manifest list unchanged, so `latest` has the digest, and with it the
attestation, of the release. *Package repositories* regenerates and signs every index from the
packages on `gh-pages`, adds none, checks that the four packages of the version are there, and
pushes the branch. The workflow and the scripts come from `--ref`, which is `main`: the
`release.yml` of an older tag knows no `publish_version`. Use it to bring a version tagged before
candidates were published on every channel to `latest` and `stable` (that is how 1.0.0-rc.2 got
there), to move `latest` back to an earlier version, and to put the indices right after old
packages are pruned.

## The Linux packages and their repositories

The job *Linux packages* packs the native executables of the two Linux jobs into
`esj_<version>_<amd64|arm64>.deb` and `esj-<version>-1.<x86_64|aarch64>.rpm` with nfpm
(`dist/packages.sh`, nfpm pinned by version and SHA-256 in `dist/nfpm.sh`). A pre-release
`1.2.3-rc.1` becomes `1.2.3~rc.1`, which apt and dnf sort before `1.2.3`. First, every executable
is checked against the oldest C library the packages require, `ESJ_GLIBC_FLOOR` in `dist/checks.sh`
(glibc 2.34), as the native build checks it. Then, on a runner of each processor, the job *Linux
packages on …* installs them in Debian, Ubuntu, Fedora and Rocky Linux and runs the tool there
(`dist/packages-smoke.sh`). It publishes them into apt and dnf repositories signed with a throwaway
key and installs them from there with the commands of
[`install.md`](install.md#debian-and-ubuntu-apt) (`dist/repositories-test.sh`). Only after both
tests does the release job attach them with the archives, checked and attested the same way. A
pre-release's carry a dot for the `~` ([Pre-releases](#pre-releases)). The repositories get the
packages from the artifact, with the `~`.

The job *Package repositories* runs on a `v*` tag after the release job, or when a version is
[published again](#publishing-a-version-again), one run at a time. It checks out the branch
`gh-pages`, or starts it as an orphan where there is none. It imports the release key from the
secrets `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` and makes one signature with it before anything
else. It adds the four packages of the tag to the suite `stable` and regenerates every index from
the packages on the branch (`dist/repositories.sh`: `apt-ftparchive`, `createrepo_c`). It signs the
indices and every rpm that has no signature yet, checks that the four packages of the version are
there, and pushes the branch as `github-actions[bot]`. GitHub Pages then publishes it. Only this
job reads the release key. The rpm in the repository is the attached package with a signature added
(the header digest is the same). The `.deb` is the same file. A published package is never
replaced: a package of the same name with other contents stops the job.

The branch is the whole state of the repositories. Every version stays on it, and nothing else is
kept. GitHub Pages publishes at most 1 GB, and every release adds about 120 MB. So the job warns
above 750 MB and stops above 1 GB. Prune by hand: delete the old packages from `pool/main/e/esj/`
and `rpm/stable/<arch>/` on `gh-pages`, push, and at once [publish the version `latest` names
again](#publishing-a-version-again). That regenerates every index from what is left and signs it.
Until it has, the indices name packages that are gone. To repeat a publication that failed, re-run
the job *Package repositories* of its run, for as long as the run keeps its artifacts (90 days). A
package already on the branch is left as it is:

```sh
gh run list --workflow release.yml --limit 5          # the run of the tag
gh run view <run-id> --json jobs --jq '.jobs[] | select(.name == "Package repositories") | .databaseId'
gh run rerun <run-id> --job <that id>
```

The history of `gh-pages` keeps the pruned files. To shrink the branch itself, replace it by a
single commit of its current tree. The site has the custom domain `apt.bsnsoft.de`. GitHub keeps it
in the file `CNAME` at the root of the branch, and the job leaves that file alone.

## Set up once

1. An account on [central.sonatype.com](https://central.sonatype.com) for the publisher.
2. The namespace `de.bsnsoft`, registered there and verified by the `TXT` record the Portal
   names, on `bsnsoft.de`. It covers every `de.bsnsoft.*` group id.
3. A user token, generated in the Portal account, as the two repository secrets
   `CENTRAL_USER` and `CENTRAL_PASSWORD`. It is not the account password.
4. An OpenPGP key pair for signing. Put its public key on `keys.openpgp.org`, since the Portal
   verifies a signature against a public keyserver. Store the armoured private key in the
   repository secret `GPG_PRIVATE_KEY` and its passphrase in `GPG_PASSPHRASE`. The workflow imports
   that one key and signs with it, so the key needs no name in the POM.

The four secrets are the whole configuration `publish.yml` reads. They are organisation secrets
that only this repository may read. A deployment cannot be withdrawn once it is published, and a
version cannot be published twice.

The job *Package repositories* signs the package repositories with the same key, read from
`GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`. `release.yml` names its fingerprint and stops when the
secret holds another key. The container image and the attestations need no secret. `release.yml`
pushes the image with the token of the workflow run and signs the attestations with a certificate
issued to that run. Once, after the first release that pushes it, check the package `esj` among the
organisation's packages on GitHub. A package a workflow creates is private, whatever the visibility
of the repository. Make it public in its *Package settings*, under *Danger Zone*, *Change
visibility*; a public package cannot be made private again. The same page names the repository the
package is linked to, which has to be `bsnsoft/esj`. The workflow links it by itself, and the
package takes its access from that repository.
