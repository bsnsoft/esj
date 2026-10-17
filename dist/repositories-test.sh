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
# markers <!-- repositories-test: apt stable --> and <!-- repositories-test:
# dnf stable -->, run as they are printed there: the repositories have one
# suite, stable, with every version in it, a release candidate as well.
#
# Checked besides: a second run over the same packages adds nothing and leaves
# every published file as it was, a CNAME file at the root of the site stays,
# no second suite is written, the rpm in the repository carries a signature
# and the header digest of the package it was made from, apt and dnf refuse
# the repository against another key, and a release candidate's package
# versions sort after the release before it and before its own release in dpkg
# and in rpm.
#
# And the life of a release candidate in the one suite, with stand-ins for esj
# that the container builds (dpkg-deb, rpmbuild) and whose esj prints its
# version, in a second site served beside the first: 0.9.8 and 1.0.0~rc.2 in
# the layout 1.0.0-rc.2 left, the candidate's rpm in the former suite testing,
# are migrated by the next run — the rpm moves to stable as the same file,
# dists/testing, rpm/testing and rpm/esj-testing.repo go, the CNAME file stays,
# and a second run finds nothing to move — after which apt and dnf, given the
# source line and the .repo file of the page for that site, install
# 1.0.0~rc.2, and upgrade to 1.0.0 once 1.0.0 is added.
#
# Every container runs under a time limit inside it, behind Docker's init
# process (the timeout of coreutils 9.10, Fedora 44, exits with 125 and does
# nothing when it is the first process of a container); a trap removes the two
# servers, the network and the temporary directory ($TMPDIR, which Docker has
# to be able to mount) when the script ends. Needs Docker.
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
# The version of the tool the packages carry.
package_version=${deb##*/esj_}
package_version=${package_version%_"$deb_arch".deb}
version=$(echo "$package_version" | sed 's/~/-/')
[ "$(sh "$here/packages.sh" --versions "$version" 2>/dev/null | sed -n 's/^deb //p')" = "$package_version" ] ||
  { echo "repositories-test: $package_version is no package version of a release" >&2; exit 2; }
echo "repositories-test: esj $version, $deb_arch"
# The site of the stand-ins, served beside the other one.
order=http://esj-repository-order:8080

work=$(mktemp -d)
network=esj-repositories-test-$$
server=esj-repository-$$
order_server=esj-repository-order-$$
cleanup() {
  docker rm -f "$server" "$order_server" > /dev/null 2>&1 || true
  docker network rm "$network" > /dev/null 2>&1 || true
  rm -rf "${work:?}"
}
trap cleanup EXIT INT TERM
mkdir -p "$work/site" "$work/order"
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
for name in "apt stable" "dnf stable"; do
  [ -n "$(block "$name")" ] ||
    { echo "repositories-test: docs/install.md has no console block after <!-- repositories-test: $name -->" >&2; exit 1; }
done
# The lines of the page that name the repository: the apt source line and the
# .repo file of dnf, which the stand-ins are installed with as well.
apt_source=$(block "apt stable" | grep ' tee /etc/apt/sources\.list\.d/' || true)
dnf_source=$(block "dnf stable" | grep ' -o /etc/yum\.repos\.d/' || true)
[ "$(printf '%s\n' "$apt_source" | grep -c .)" = 1 ] && [ "$(printf '%s\n' "$dnf_source" | grep -c .)" = 1 ] ||
  { echo "repositories-test: docs/install.md names no single apt source line and dnf .repo file" >&2; exit 1; }

say() { printf '\n== %s\n' "$*"; }

say "the sites, written with a throwaway key in $generator"
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
ESJ_REPOSITORY_URL=$SERVED sh /work/dist/repositories.sh /packages /site "$key"
# One suite: nothing of the former suite testing is written.
for gone in dists/testing rpm/testing rpm/esj-testing.repo; do
  [ ! -e "/site/$gone" ] || { echo "FAILED: the site has $gone" >&2; exit 1; }
done
# A second run over the same packages adds nothing and changes no package.
( cd /site && find . -name '*.deb' -o -name '*.rpm' | sort | xargs sha256sum ) > /tmp/before
ESJ_REPOSITORY_URL=$SERVED sh /work/dist/repositories.sh /packages /site "$key" > /tmp/second
grep -q '^repositories: 0 packages added$' /tmp/second ||
  { cat /tmp/second; echo "FAILED: the second run added packages" >&2; exit 1; }
( cd /site && find . -name '*.deb' -o -name '*.rpm' | sort | xargs sha256sum ) > /tmp/after
cmp -s /tmp/before /tmp/after || { echo "FAILED: the second run changed a package" >&2; exit 1; }
echo "second run: 0 packages added, $(wc -l < /tmp/after) packages unchanged"
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
# A release candidate sorts after the release before it and before its own
# release, in both package managers.
for kind in deb rpm; do
  earlier=$(sh /work/dist/packages.sh --versions 0.9.8 | sed -n "s/^$kind //p")
  candidate=$(sh /work/dist/packages.sh --versions 1.0.0-rc.1 | sed -n "s/^$kind //p")
  release=$(sh /work/dist/packages.sh --versions 1.0.0 | sed -n "s/^$kind //p")
  for pair in "$earlier $candidate" "$candidate $release"; do
    set -- $pair
    case $kind in
      deb) dpkg --compare-versions "$1" lt "$2" ;;
      rpm) [ "$(rpm --eval "%{lua: print(rpm.vercmp('$1', '$2'))}")" = -1 ] ;;
    esac || { echo "FAILED: $kind sorts $1 after $2" >&2; exit 1; }
  done
  echo "versions: $kind $earlier < $candidate < $release"
done

# Stand-ins for esj: the packages of a version, for this processor, whose esj
# prints that version.
stand_in() {
  tool=$1
  into=$2
  version=$(sh /work/dist/packages.sh --versions "$tool" | sed -n 's/^deb //p')
  tree=$(mktemp -d)
  mkdir -p "$tree/DEBIAN" "$tree/usr/bin"
  printf '#!/bin/sh\necho "esj %s"\n' "$tool" > "$tree/usr/bin/esj"
  chmod 0755 "$tree/usr/bin/esj"
  printf 'Package: esj\nVersion: %s\nArchitecture: %s\nMaintainer: esj repository test <test@esj.invalid>\nDescription: a stand-in for esj %s\n' \
    "$version" "$DEB_ARCH" "$tool" > "$tree/DEBIAN/control"
  dpkg-deb --build --root-owner-group "$tree" "$into/esj_${version}_$DEB_ARCH.deb" > /dev/null
  top=$(mktemp -d)
  mkdir -p "$top/SPECS"
  cat > "$top/SPECS/esj.spec" <<'SPEC'
Name: esj
Version: %{stand_in_version}
Release: 1
Summary: a stand-in for esj %{stand_in_tool}
License: Apache-2.0
AutoReqProv: no
%description
A stand-in for esj %{stand_in_tool}, which prints its version.
%install
mkdir -p %{buildroot}/usr/bin
printf '#!/bin/sh\necho "esj %{stand_in_tool}"\n' > %{buildroot}/usr/bin/esj
chmod 0755 %{buildroot}/usr/bin/esj
%files
/usr/bin/esj
SPEC
  rpmbuild -bb --quiet --define "_topdir $top" --define "stand_in_version $version" \
    --define "stand_in_tool $tool" --define 'debug_package %{nil}' \
    --define '__os_install_post %{nil}' --define '_build_id_links none' \
    "$top/SPECS/esj.spec" > /dev/null
  find "$top/RPMS" -name 'esj-*.rpm' -exec cp {} "$into/" \;
}
mkdir -p /tmp/earlier /tmp/release /tmp/none /order/before
stand_in 0.9.8 /tmp/earlier
stand_in 1.0.0-rc.2 /tmp/earlier
stand_in 1.0.0 /tmp/release
ls /tmp/earlier /tmp/release

# The site as 1.0.0-rc.2 left it: 0.9.8 in stable, the candidate's rpm and its
# indices in the suite testing beside it, its .deb in the shared pool, and the
# .repo file of testing.
printf 'apt.bsnsoft.de\n' > /order/before/CNAME
ESJ_REPOSITORY_URL=$ORDER/before sh /work/dist/repositories.sh /tmp/earlier /order/before "$key" \
  > /tmp/earlier.log
( cd /order/before
  find rpm/stable -name 'esj-1.0.0~rc.2-1.*.rpm' > /tmp/candidate.list
  while IFS= read -r package; do
    arch=${package#rpm/stable/}
    arch=${arch%%/*}
    mkdir -p "rpm/testing/$arch"
    mv "$package" "rpm/testing/$arch/"
  done < /tmp/candidate.list
  for arch in x86_64 aarch64; do
    mkdir -p "rpm/testing/$arch"
    createrepo_c --quiet --no-database --checksum sha256 "rpm/testing/$arch"
    createrepo_c --quiet --no-database --checksum sha256 --retain-old-md 2 "rpm/stable/$arch"
    gpg --batch --yes --pinentry-mode error --local-user "$key" --armor --detach-sign \
      --output "rpm/testing/$arch/repodata/repomd.xml.asc" "rpm/testing/$arch/repodata/repomd.xml"
  done
  cp -R dists/stable dists/testing
  sed -e 's/^\[esj\]$/[esj-testing]/' -e 's|/rpm/stable/|/rpm/testing/|' rpm/esj.repo > rpm/esj-testing.repo
  find rpm/testing -name '*.rpm' | xargs sha256sum > /tmp/candidate.sum )
[ "$(wc -l < /tmp/candidate.sum)" = 1 ] ||
  { cat /tmp/candidate.sum; echo "FAILED: the earlier layout holds no rpm of the candidate" >&2; exit 1; }
# Listed as the site looks apart from its indices: every path, and the digest
# of every package.
layout() {
  ( cd "$1" && find . -type f ! -path '*/repodata/*' ! -path '*/by-hash/*' | LC_ALL=C sort &&
    find . -name '*.deb' -o -name '*.rpm' | LC_ALL=C sort | xargs sha256sum )
}
ESJ_REPOSITORY_URL=$ORDER/before sh /work/dist/repositories.sh /tmp/none /order/before "$key" \
  > /tmp/migrated.log
grep 'moved to\|earlier layout' /tmp/migrated.log
[ "$(grep -c ' moved to rpm/stable/' /tmp/migrated.log)" = 1 ] ||
  { cat /tmp/migrated.log; echo "FAILED: the candidate's rpm was not moved to stable" >&2; exit 1; }
for gone in dists/testing rpm/testing rpm/esj-testing.repo; do
  [ ! -e "/order/before/$gone" ] || { echo "FAILED: $gone is still there" >&2; exit 1; }
done
( cd /order/before && sha256sum -c --quiet /tmp/candidate.sum > /dev/null 2>&1 ) && {
  echo "FAILED: the candidate's rpm is still under rpm/testing" >&2; exit 1; }
sed 's|rpm/testing/|rpm/stable/|' /tmp/candidate.sum > /tmp/moved.sum
( cd /order/before && sha256sum -c --quiet /tmp/moved.sum ) ||
  { echo "FAILED: the candidate's rpm is not the same file in stable" >&2; exit 1; }
[ "$(cat /order/before/CNAME)" = apt.bsnsoft.de ] || { echo "FAILED: the CNAME file was changed" >&2; exit 1; }
for index in dists/stable/main/binary-$DEB_ARCH/Packages; do
  [ "$(sed -n 's/^Version: //p' "/order/before/$index" | LC_ALL=C sort | tr '\n' ' ')" = '0.9.8 1.0.0~rc.2 ' ] ||
    { cat "/order/before/$index"; echo "FAILED: $index does not list 0.9.8 and 1.0.0~rc.2" >&2; exit 1; }
done
layout /order/before > /tmp/layout-before
ESJ_REPOSITORY_URL=$ORDER/before sh /work/dist/repositories.sh /tmp/none /order/before "$key" \
  > /tmp/again.log
if grep -q 'moved to\|earlier layout' /tmp/again.log; then
  cat /tmp/again.log; echo "FAILED: the second run migrated again" >&2; exit 1
fi
layout /order/before > /tmp/layout-again
cmp -s /tmp/layout-before /tmp/layout-again ||
  { diff /tmp/layout-before /tmp/layout-again; echo "FAILED: the second run changed the site" >&2; exit 1; }
echo "migration: the candidate's rpm moved to stable as the same file, the suite testing gone; a second run moves nothing"
# The same site once 1.0.0 is added.
cp -R /order/before /order/after
ESJ_REPOSITORY_URL=$ORDER/after sh /work/dist/repositories.sh /tmp/release /order/after "$key" \
  > /tmp/release.log
grep -q '^repositories: 2 packages added$' /tmp/release.log ||
  { cat /tmp/release.log; echo "FAILED: 1.0.0 was not added" >&2; exit 1; }

# Another key, for the clients to be refused with.
gpg --batch --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'another key <other@esj.invalid>' ed25519 cert,sign never 2>/dev/null
other_key=$(gpg --batch --with-colons --list-keys 'another key' | awk -F: '$1 == "fpr" { print $10; exit }')
gpg --batch --export "$other_key" > /site/test-other-key.gpg
gpg --batch --armor --export "$other_key" > /site/test-other-key.asc
gpgconf --kill gpg-agent
chown -R "$OWNER" /site /order 2>/dev/null || true
GENERATE
docker run --rm --init -e "SERVED=$served" -e "ORDER=$order" -e "DEB_ARCH=$deb_arch" \
  -e "OWNER=$(id -u):$(id -g)" \
  -v "$root:/work:ro" -v "$packages:/packages:ro" -v "$work/site:/site" -v "$work/order:/order" \
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
docker run -d --init --name "$order_server" --network "$network" --network-alias esj-repository-order \
  -v "$work/order:/site:ro" "$server_image" timeout 1800 httpd -f -p 8080 -h /site > /dev/null

# A client: the commands of the page, then esj, then the stand-ins of the
# other site, then the repository against another key.
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
baseurl=$served/rpm/stable/\$basearch/
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
    block "$kind stable" | while IFS= read -r line; do
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
    # The site of the stand-ins, given to the package manager with the line of
    # the page that names the repository: 0.9.8 and 1.0.0~rc.2 in the one suite
    # give 1.0.0~rc.2, and 1.0.0, once added, replaces it.
    case $kind in
      apt)
        source_line=$apt_source
        installed="dpkg-query -W -f='\${Version}' esj"
        install='apt-get install esj'
        upgrade='apt-get install --only-upgrade esj'
        candidate=1.0.0~rc.2 release=1.0.0
        refresh='apt-get update > /tmp/step.log 2>&1 || { cat /tmp/step.log; exit 1; }'
        echo 'apt-cache policy esj | sed -n "1,4p"'
        echo 'apt-get remove esj > /tmp/step.log 2>&1 || { cat /tmp/step.log; exit 1; }' ;;
      dnf)
        source_line=$dnf_source
        installed="rpm -q --qf '%{VERSION}-%{RELEASE}' esj"
        install='dnf install --refresh --repo esj esj'
        upgrade='dnf upgrade --refresh --repo esj esj'
        candidate=1.0.0~rc.2-1 release=1.0.0-1
        refresh=:
        echo "rpm -q --qf 'installed %{NAME}-%{VERSION}-%{RELEASE}.%{ARCH}, signed: %{DSAHEADER:pgpsig}\\n' esj"
        echo 'dnf remove esj > /tmp/step.log 2>&1 || { cat /tmp/step.log; exit 1; }' ;;
    esac
    cat <<CLIENT
( $(printf '%s' "$source_line" | sed "s|$served|$order/before|") ) > /tmp/step.log 2>&1 || { cat /tmp/step.log; exit 1; }
$refresh
$install > /tmp/step.log 2>&1 || { cat /tmp/step.log; exit 1; }
[ "\$($installed)" = $candidate ] || { echo "FAILED: $kind installed esj \$($installed) from 0.9.8 and 1.0.0~rc.2" >&2; exit 1; }
[ "\$(esj --version)" = "esj 1.0.0-rc.2" ] || { echo "FAILED: the stand-in says \$(esj --version)" >&2; exit 1; }
( $(printf '%s' "$source_line" | sed "s|$served|$order/after|") ) > /tmp/step.log 2>&1 || { cat /tmp/step.log; exit 1; }
$refresh
$upgrade > /tmp/step.log 2>&1 || { cat /tmp/step.log; exit 1; }
[ "\$($installed)" = $release ] || { echo "FAILED: $kind upgraded esj to \$($installed), not to $release" >&2; exit 1; }
echo "one suite: 0.9.8 and 1.0.0~rc.2 give $candidate, and 1.0.0 added replaces it with $release"
( $source_line ) > /tmp/step.log 2>&1 || { cat /tmp/step.log; exit 1; }
CLIENT
    # The keyring the page writes, which the refusal below replaces.
    keyring=$(block "apt stable" | sed -n 's/.*signed-by=\([^] ]*\).*/\1/p' | head -1)
    [ -n "$keyring" ] ||
      { echo "repositories-test: the apt block of docs/install.md names no signed-by keyring" >&2; exit 1; }
    case $kind in
      apt) cat <<CLIENT
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
say "repositories-test: passed (esj $version)"
