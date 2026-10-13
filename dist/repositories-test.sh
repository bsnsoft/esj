#!/bin/sh
#
# Puts the apt and dnf repositories of dist/repositories.sh through what a user
# does with them, end to end and with nothing of the real site: a throwaway key
# of the kind the release key is (ed25519 for signing, cv25519 beside it, with a
# passphrase) is made inside the container that writes the site, the site is
# written into a temporary directory from the packages given and served over
# HTTP on a Docker network of its own, and in a Debian and a Fedora container
# the commands of docs/install.md are run as they are printed there — the
# address of the site is the only thing exchanged — before esj is run once.
#
#   dist/repositories-test.sh <packages-dir> [--apt-image <image>]... [--dnf-image <image>]...
#
#   <packages-dir>  the .deb and .rpm files of dist/packages.sh; those for the
#                   processor of the Docker host are installed, all of them
#                   are published
#   --apt-image     an image to install the .deb from the repository in;
#                   debian:12-slim when none is named
#   --dnf-image     an image to install the .rpm from the repository in;
#                   fedora:latest when none is named
#
# The commands are the console blocks of docs/install.md that follow the
# markers <!-- repositories-test: apt stable -->, <!-- repositories-test: apt
# testing --> and the same two for dnf. A release goes to stable and its block
# is run as it is; a pre-release goes to testing, and the testing block runs
# before the last line of the stable one, as the page tells a reader to add it.
#
# Checked besides: a second run over the same packages adds nothing and leaves
# every published file as it was, a CNAME file at the root of the site stays,
# a package of the other suite is refused, the rpm in the repository carries a
# signature and the header digest of the package it was made from, apt and dnf
# refuse the repository against another key, and a release candidate's package
# versions sort before the release's in dpkg and in rpm.
#
# Every container runs under a time limit inside it, behind Docker's init
# process (the timeout of coreutils 9.10, Fedora 44, exits with 125 and does
# nothing when it is the first process of a container); a trap removes the
# server, the network and the temporary directory ($TMPDIR, which Docker has to
# be able to mount) when the script ends. Needs Docker.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

# The header of this file is its manual page.
usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(dirname -- "$here")
# The address the published site has, which the test serves under another.
public=https://apt.bsnsoft.de
served=http://esj-repository:8080
generator=ubuntu:24.04
server_image=busybox:1.37

packages=
apt_images=
dnf_images=
while [ $# -gt 0 ]; do
  case $1 in
    --apt-image) apt_images="$apt_images $2"; shift 2 ;;
    --dnf-image) dnf_images="$dnf_images $2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "repositories-test: unknown option $1" >&2; exit 2 ;;
    *) packages=$1; shift ;;
  esac
done
[ -n "$packages" ] || { echo "usage: dist/repositories-test.sh <packages-dir> [--apt-image <image>]... [--dnf-image <image>]..." >&2; exit 2; }
[ -n "$apt_images" ] || apt_images=debian:12-slim
[ -n "$dnf_images" ] || dnf_images=fedora:latest
command -v docker > /dev/null || { echo "repositories-test: needs docker" >&2; exit 2; }
packages=$(CDPATH='' cd -- "$packages" && pwd)

case $(docker version --format '{{.Server.Arch}}') in
  amd64|x86_64) deb_arch=amd64; rpm_arch=x86_64 ;;
  arm64|aarch64) deb_arch=arm64; rpm_arch=aarch64 ;;
  *) echo "repositories-test: no packages for the Docker host's processor" >&2; exit 2 ;;
esac
deb=$(find "$packages" -maxdepth 1 -name "esj_*_$deb_arch.deb" | head -1)
rpm=$(find "$packages" -maxdepth 1 -name "esj-*.$rpm_arch.rpm" | head -1)
[ -n "$deb" ] && [ -n "$rpm" ] ||
  { echo "repositories-test: no esj_*_$deb_arch.deb and esj-*.$rpm_arch.rpm in $packages" >&2; exit 2; }
# The version of the tool the packages carry, and the suite it goes to.
package_version=${deb##*/esj_}
package_version=${package_version%_"$deb_arch".deb}
version=$(echo "$package_version" | sed 's/~/-/')
suite=$(sh "$here/packages.sh" --versions "$version" | sed -n 's/^suite //p')
[ -n "$suite" ] || { echo "repositories-test: $version is no version of a release" >&2; exit 2; }
echo "repositories-test: esj $version, suite $suite, $deb_arch"

work=$(mktemp -d)
network=esj-repositories-test-$$
server=esj-repository-$$
cleanup() {
  docker rm -f "$server" > /dev/null 2>&1 || true
  docker network rm "$network" > /dev/null 2>&1 || true
  rm -rf "${work:?}"
}
trap cleanup EXIT INT TERM
mkdir -p "$work/site"
# GitHub Pages keeps the custom domain in this file; the script must leave it.
printf 'apt.bsnsoft.de\n' > "$work/site/CNAME"

# The lines of the console block that follows a marker of docs/install.md, the
# prompt removed and the address of the site exchanged for the test's.
block() {
  awk -v marker="<!-- repositories-test: $1 -->" '
    $0 == marker { found = 1; next }
    found && /^```console$/ { inside = 1; next }
    inside && /^```$/ { exit }
    inside && /^\$ / { print substr($0, 3) }
  ' "$root/docs/install.md" | sed "s|$public|$served|g"
}
for name in "apt stable" "apt testing" "dnf stable" "dnf testing"; do
  [ -n "$(block "$name")" ] ||
    { echo "repositories-test: docs/install.md has no console block after <!-- repositories-test: $name -->" >&2; exit 1; }
done

# The commands of a reader of the page, in the order the suite asks for.
commands() {
  block "$1 stable" > "$work/stable"
  if [ "$suite" = testing ]; then
    sed '$d' "$work/stable"
    block "$1 testing"
    sed -n '$p' "$work/stable"
  else
    cat "$work/stable"
  fi
}

say() { printf '\n== %s\n' "$*"; }

say "the site, written twice with a throwaway key in $generator"
cat > "$work/generate.sh" <<'GENERATE'
set -eu
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq > /dev/null
apt-get install -y -qq --no-install-recommends apt-utils createrepo-c rpm gpg gpg-agent \
  > /dev/null 2>&1
GNUPGHOME=$(mktemp -d)
export GNUPGHOME
passphrase=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
gpg --batch --pinentry-mode loopback --passphrase "$passphrase" \
  --quick-generate-key 'esj repository test <test@esj.invalid>' ed25519 cert,sign never 2>/dev/null
key=$(gpg --batch --with-colons --list-secret-keys | awk -F: '$1 == "fpr" { print $10; exit }')
gpg --batch --pinentry-mode loopback --passphrase "$passphrase" \
  --quick-add-key "$key" cv25519 encr never 2>/dev/null
echo "throwaway key $key"
printf '%s\n' "$passphrase" | sh /work/dist/repositories.sh --unlock "$key"
export ESJ_REPOSITORY_URL=$SERVED
sh /work/dist/repositories.sh /packages /site "$SUITE" "$key"
# A second run over the same packages adds nothing and changes no package.
( cd /site && find . -name '*.deb' -o -name '*.rpm' | sort | xargs sha256sum ) > /tmp/before
sh /work/dist/repositories.sh /packages /site "$SUITE" "$key" > /tmp/second
grep -q '^repositories: 0 packages added$' /tmp/second ||
  { cat /tmp/second; echo "FAILED: the second run added packages" >&2; exit 1; }
( cd /site && find . -name '*.deb' -o -name '*.rpm' | sort | xargs sha256sum ) > /tmp/after
cmp -s /tmp/before /tmp/after || { echo "FAILED: the second run changed a package" >&2; exit 1; }
echo "second run: 0 packages added, $(wc -l < /tmp/after) packages unchanged"
# A package of the other suite is refused, and nothing is written.
case $SUITE in stable) other=testing ;; *) other=stable ;; esac
if sh /work/dist/repositories.sh /packages /site "$other" "$key" > /tmp/other 2>&1; then
  echo "FAILED: packages of $SUITE went to $other" >&2; exit 1
fi
grep -q "does not go to $other" /tmp/other || { cat /tmp/other; exit 1; }
echo "refused for $other: $(grep -m1 'does not go to' /tmp/other)"
# The rpm in the repository is the package given, with a signature added.
for package in /packages/*.rpm; do
  published=$(find /site/rpm -name "${package##*/}")
  [ "$(rpm -qp --nosignature --qf '%{SHA256HEADER}' "$package")" = \
    "$(rpm -qp --nosignature --qf '%{SHA256HEADER}' "$published")" ] ||
    { echo "FAILED: ${package##*/} has another header in the repository" >&2; exit 1; }
  signature=$(rpm -qp --nosignature --qf '%{DSAHEADER:pgpsig}' "$published" 2>/dev/null)
  case $signature in
    *"Key ID $(echo "$key" | cut -c25- | tr 'A-F' 'a-f')"*) ;;
    *) echo "FAILED: ${package##*/} in the repository carries the signature $signature" >&2; exit 1 ;;
  esac
  echo "${package##*/}: header digest kept, signature $signature"
done
# A pre-release sorts before its release in both package managers.
deb_rc=$(sh /work/dist/packages.sh --versions 1.0.0-rc.1 | sed -n 's/^deb //p')
deb_release=$(sh /work/dist/packages.sh --versions 1.0.0 | sed -n 's/^deb //p')
dpkg --compare-versions "$deb_rc" lt "$deb_release" ||
  { echo "FAILED: dpkg sorts $deb_rc after $deb_release" >&2; exit 1; }
rpm_rc=$(sh /work/dist/packages.sh --versions 1.0.0-rc.1 | sed -n 's/^rpm //p')
rpm_release=$(sh /work/dist/packages.sh --versions 1.0.0 | sed -n 's/^rpm //p')
[ "$(rpm --eval "%{lua: print(rpm.vercmp('$rpm_rc', '$rpm_release'))}")" = -1 ] ||
  { echo "FAILED: rpm sorts $rpm_rc after $rpm_release" >&2; exit 1; }
echo "versions: dpkg $deb_rc < $deb_release, rpm $rpm_rc < $rpm_release"
# Another key, for the clients to be refused with.
gpg --batch --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'another key <other@esj.invalid>' ed25519 cert,sign never 2>/dev/null
other_key=$(gpg --batch --with-colons --list-keys 'another key' | awk -F: '$1 == "fpr" { print $10; exit }')
gpg --batch --export "$other_key" > /site/test-other-key.gpg
gpg --batch --armor --export "$other_key" > /site/test-other-key.asc
gpgconf --kill gpg-agent
chown -R "$OWNER" /site 2>/dev/null || true
GENERATE
docker run --rm --init -e "SUITE=$suite" -e "SERVED=$served" -e "OWNER=$(id -u):$(id -g)" \
  -v "$root:/work:ro" -v "$packages:/packages:ro" -v "$work/site:/site" \
  -v "$work/generate.sh:/generate.sh:ro" "$generator" timeout 900 sh /generate.sh
[ "$(cat "$work/site/CNAME")" = apt.bsnsoft.de ] ||
  { echo "repositories-test: the CNAME file was changed" >&2; exit 1; }
echo "CNAME file left as it was"
( cd "$work/site" && find . -path ./.git -prune -o -type f -print | grep -v '/pool/\|/by-hash/\|\.rpm$' |
  LC_ALL=C sort | sed 's/^/  /' )

say "the site, served on the network $network"
docker network create "$network" > /dev/null
docker run -d --init --name "$server" --network "$network" --network-alias esj-repository \
  -v "$work/site:/site:ro" "$server_image" timeout 1800 httpd -f -p 8080 -h /site > /dev/null

# A client: the commands of the page, then esj, then the repository against
# another key.
client() {
  kind=$1
  image=$2
  {
    echo 'set -eu'
    echo 'sudo() { "$@"; }'
    case $kind in
      apt)
        echo 'export DEBIAN_FRONTEND=noninteractive'
        echo 'printf '"'"'APT::Get::Assume-Yes "true";\n'"'"' > /etc/apt/apt.conf.d/90-esj-test' ;;
      dnf)
        echo 'printf '"'"'assumeyes=1\n'"'"' >> /etc/dnf/dnf.conf' ;;
    esac
    if [ "$kind" = dnf ]; then
      # Before the right key is known to rpm: dnf checks the metadata against
      # the keys rpm holds as well as against gpgkey=, so a repository of the
      # same site read with another key is refused only by a system that has
      # not imported the right one. Fedora skips a repository it cannot read
      # and goes on (skip_if_unavailable), which the test turns off.
      cat <<CLIENT
cat > /etc/yum.repos.d/other.repo <<'REPO'
[other]
name=the repository read with another key
baseurl=$served/rpm/$suite/\$basearch/
gpgcheck=1
repo_gpgcheck=1
gpgkey=$served/test-other-key.asc
skip_if_unavailable=0
REPO
if dnf makecache --repo other > /tmp/refused.log 2>&1; then
  cat /tmp/refused.log; echo "FAILED: dnf read the repository with another key" >&2; exit 1
fi
grep -qi 'signature' /tmp/refused.log || { cat /tmp/refused.log; exit 1; }
echo "another key: refused (\$(grep -i -m1 'signature' /tmp/refused.log | tr -s ' ' | cut -c1-110))"
rm -f /etc/yum.repos.d/other.repo
dnf clean all > /dev/null 2>&1
CLIENT
    fi
    echo 'echo "the commands of docs/install.md:"'
    commands "$kind" | while IFS= read -r line; do
      printf 'echo %s\n' "'  \$ $(printf '%s' "$line" | sed "s/'/'\\\\''/g")'"
      printf '( %s ) > /tmp/step.log 2>&1 || { cat /tmp/step.log; exit 1; }\n' "$line"
    done
    cat <<CLIENT
first=\$(esj --version | head -1)
[ "\$first" = "esj $version" ] || { echo "FAILED: esj --version says \$first" >&2; exit 1; }
echo "\$first from \$(command -v esj)"
esj validate /work/conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml > /tmp/validate.log 2>&1 ||
  { cat /tmp/validate.log; exit 1; }
echo "validate: \$(tail -1 /tmp/validate.log)"
CLIENT
    # The keyring the page writes, which the refusal below replaces.
    keyring=$(block "apt stable" | sed -n 's/.*signed-by=\([^] ]*\).*/\1/p' | head -1)
    [ -n "$keyring" ] ||
      { echo "repositories-test: the apt block of docs/install.md names no signed-by keyring" >&2; exit 1; }
    case $kind in
      apt) cat <<CLIENT
apt-cache policy esj | sed -n '1,4p'
curl -fsSL $served/test-other-key.gpg > $keyring
# Without the indices apt holds, which it would not check again.
rm -f /var/lib/apt/lists/esj-repository*
if apt-get update > /tmp/refused.log 2>&1; then
  echo "FAILED: apt read the repository with another key" >&2; exit 1
fi
grep -q 'NO_PUBKEY\|not signed\|signatures were invalid\|could not be verified' /tmp/refused.log ||
  { cat /tmp/refused.log; exit 1; }
echo "another key: refused (\$(grep -m1 -o 'NO_PUBKEY [0-9A-F]*\|is not signed' /tmp/refused.log))"
CLIENT
        ;;
      dnf) cat <<CLIENT
rpm -q --qf 'installed %{NAME}-%{VERSION}-%{RELEASE}.%{ARCH}, signed: %{DSAHEADER:pgpsig}\n' esj
CLIENT
        ;;
    esac
  } > "$work/client-$kind.sh"
  say "$kind in $image"
  start=$(date +%s)
  docker run --rm --init --network "$network" -v "$root:/work:ro" \
    -v "$work/client-$kind.sh:/client.sh:ro" "$image" timeout 900 sh /client.sh
  echo "ok in $(( $(date +%s) - start )) s"
}
for image in $apt_images; do client apt "$image"; done
for image in $dnf_images; do client dnf "$image"; done
say "repositories-test: passed (esj $version, suite $suite)"
