#!/bin/sh
#
# Builds the Linux packages of the esj command line from the native executables
# of a release: a .deb and an .rpm for each processor, with nfpm (dist/nfpm.sh),
# which packs files and needs no packaging toolchain of either distribution, so
# one runner builds the packages of both processors.
#
#   dist/packages.sh <native-archive-dir> <version> <out-dir>
#   dist/packages.sh --versions <version>
#
#   <native-archive-dir>  holds esj-<version>-native-linux-x64 and
#                         esj-<version>-native-linux-arm64, each as the release
#                         archive (.zip, checked against the .sha256 beside it
#                         when there is one) or unpacked, as dist/package.sh
#                         leaves it under dist/out; a processor without one is
#                         left out, and none at all is a failure
#   <version>             the version of the tool, as esj --version prints it:
#                         1.2.3, 1.2.3-rc.1, 1.2.3-SNAPSHOT
#   <out-dir>             where esj_<deb version>_<amd64|arm64>.deb and
#                         esj-<rpm version>-1.<x86_64|aarch64>.rpm are written,
#                         each with its .sha256 beside it in the form of the
#                         release archives
#   --versions            prints the versions the packages of <version> carry
#                         and the suite of the repositories they go to, one per
#                         line: "deb 1.2.3~rc.1", "rpm 1.2.3~rc.1-1", "suite testing"
#
# The version of a release is the version of both packages; a pre-release
# 1.2.3-rc.1 becomes 1.2.3~rc.1, which apt and dnf both sort before 1.2.3, and
# goes to the suite testing, a release to stable (dist/repositories.sh). The rpm
# carries the release number 1, the deb no revision.
#
# What is installed: the executable, its shared libraries and the licences of
# the Java runtime parts in it under /usr/lib/esj, /usr/bin/esj as a relative
# symbolic link to it — the executable finds its libraries beside its own
# resolved path, which a run through the link and a PDF rendered through it
# prove (dist/packages-smoke.sh) — and the copyright file, LICENSE and NOTICE
# under /usr/share/doc/esj (dist/copyright). The packages require the C library of
# $ESJ_GLIBC_FLOOR (dist/checks.sh), which the executable is checked against
# here, and zlib, which it links; nothing else, measured by
# dist/packages-smoke.sh on minimal images: libawt_xawt and libfontmanager name
# X11 and FreeType, and no run of dist/cases.sh loads either of them, because
# the executable renders headless and draws its fonts itself.
#
# nfpm is $ESJ_NFPM, or the pinned release dist/nfpm.sh fetches into a
# temporary directory. The files carry project.build.outputTimestamp of the POM
# as their time, or $SOURCE_DATE_EPOCH when it is set, so the same inputs make
# the same packages.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

# The header of this file is its manual page.
usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(dirname -- "$here")
. "$here/checks.sh"

fail() { echo "packages: $*" >&2; exit 1; }

# The version both packages carry for a version of the tool: the release as it
# is, a pre-release with ~ in place of its hyphen. Anything else is refused.
package_version() {
  if ! printf '%s\n' "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$'; then
    echo "packages: $1 is no version of the form 1.2.3 or 1.2.3-rc.1" >&2
    return 1
  fi
  printf '%s\n' "$1" | sed 's/-/~/'
}

# The suite of the repositories a version goes to.
suite_of() {
  case $1 in
    *-*) echo testing ;;
    *) echo stable ;;
  esac
}

case ${1-} in
  -h|--help) usage; exit 0 ;;
  --versions)
    [ $# -eq 2 ] || fail "usage: dist/packages.sh --versions <version>"
    v=$(package_version "$2") || exit 2
    printf 'deb %s\nrpm %s-1\nsuite %s\n' "$v" "$v" "$(suite_of "$2")"
    exit 0 ;;
esac
[ $# -eq 3 ] || { echo "usage: dist/packages.sh <native-archive-dir> <version> <out-dir>" >&2; exit 2; }
inputs=$1
version=$2
out=$3
[ -d "$inputs" ] || fail "$inputs is not a directory"
pkgver=$(package_version "$version") || exit 2
mkdir -p "$out"
out=$(CDPATH='' cd -- "$out" && pwd)

# The time every file of the packages carries.
if [ -n "${SOURCE_DATE_EPOCH-}" ]; then
  mtime=$(date -u -d "@$SOURCE_DATE_EPOCH" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null ||
    date -u -r "$SOURCE_DATE_EPOCH" +%Y-%m-%dT%H:%M:%SZ)
else
  mtime=$(sed -n 's:.*<project\.build\.outputTimestamp>\(.*\)</project\.build\.outputTimestamp>.*:\1:p' \
    "$root/pom.xml" | head -1)
fi
case $mtime in
  [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z) ;;
  *) fail "no time for the files: project.build.outputTimestamp in pom.xml is '$mtime'" ;;
esac

work=$(mktemp -d)
cleanup() { rm -rf "${work:?}"; }
trap cleanup EXIT INT TERM

nfpm=${ESJ_NFPM-}
[ -n "$nfpm" ] || nfpm=$(sh "$here/nfpm.sh" "$work/nfpm")
[ -x "$nfpm" ] || fail "no nfpm at $nfpm"

# The SHA-256 of a file, and the checksum file beside it in the form of the
# release archives: the digest, two spaces, the name.
sha256_of() {
  if command -v sha256sum > /dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi |
    awk '{print $1}'
}
checksum() {
  ( cd "$(dirname -- "$1")" && printf '%s  %s\n' "$(sha256_of "$(basename -- "$1")")" \
      "$(basename -- "$1")" ) > "$1.sha256"
}

# The unpacked native executable of a processor: the directory itself, or the
# archive unpacked into the work directory after its checksum.
native_of() {
  name=esj-$version-native-linux-$1
  if [ -f "$inputs/$name/esj" ]; then
    echo "$inputs/$name"
  elif [ -f "$inputs/$name.zip" ]; then
    if [ -f "$inputs/$name.zip.sha256" ]; then
      [ "$(sha256_of "$inputs/$name.zip")" = "$(awk '{print $1; exit}' "$inputs/$name.zip.sha256")" ] ||
        { echo "packages: $name.zip does not match $name.zip.sha256" >&2; return 1; }
    fi
    mkdir -p "$work/unpacked-$1"
    unzip -q "$inputs/$name.zip" -d "$work/unpacked-$1"
    [ -f "$work/unpacked-$1/$name/esj" ] ||
      { echo "packages: $name.zip holds no $name/esj" >&2; return 1; }
    echo "$work/unpacked-$1/$name"
  fi
}

# One YAML string, quoted.
quoted() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"; }

# The nfpm configuration of one package, for a processor as Go and Debian name it
# (amd64, arm64; nfpm writes x86_64 and aarch64 into the rpm): the tree staged
# under $stage, every directory under /usr/lib/esj and /usr/share/doc/esj owned
# by the package, every file with its mode and the one time, and the link in
# /usr/bin.
configuration() {
  c_packager=$1
  c_arch=$2
  c_stage=$3
  cat <<YAML
name: esj
arch: $c_arch
platform: linux
version: $(quoted "$pkgver")
version_schema: none
YAML
  if [ "$c_packager" = rpm ]; then echo "release: '1'"; fi
  cat <<YAML
section: utils
priority: optional
maintainer: 'BSNSoft Solutions GmbH <info@bsnsoft.de>'
vendor: 'BSNSoft Solutions GmbH'
homepage: 'https://github.com/bsnsoft/esj'
license: Apache-2.0
mtime: '$mtime'
description: |-
  command line tool of EN16931 Semantic JSON (ESJ)
  ESJ is an application format for EN 16931, the European standard for
  electronic invoices: a flat JSON map keyed by the business terms of the
  standard. UBL and CII stay transport bindings; the same invoice gives the
  same map whichever of them it arrived in. esj reads UBL, CII and hybrid PDF
  invoices, validates them against EN 16931 and the official artefacts of
  their profile, converts between ESJ, UBL and CII, renders an invoice as
  HTML or as a PDF/A-3 with the invoice embedded, and serves the same tools
  over HTTP and MCP.
YAML
  if [ "$c_packager" = deb ]; then
    cat <<YAML
depends:
  - 'libc6 (>= $ESJ_GLIBC_FLOOR)'
  - zlib1g
deb:
  compression: xz
YAML
  else
    cat <<YAML
depends:
  - 'glibc >= $ESJ_GLIBC_FLOOR'
  - 'libz.so.1()(64bit)'
rpm:
  summary: 'command line tool of EN16931 Semantic JSON (ESJ)'
  compression: xz
  buildhost: localhost
  packager: 'BSNSoft Solutions GmbH <info@bsnsoft.de>'
YAML
  fi
  echo "contents:"
  ( cd "$c_stage" && find . -type d | LC_ALL=C sort ) | while IFS= read -r c_path; do
    c_path=${c_path#.}
    case $c_path in
      /usr/lib/esj|/usr/lib/esj/*|/usr/share/doc/esj) ;;
      *) continue ;;
    esac
    printf '  - dst: %s\n    type: dir\n    file_info: { mode: 0755, mtime: %s }\n' \
      "$(quoted "$c_path")" "'$mtime'"
  done
  ( cd "$c_stage" && find . -type f | LC_ALL=C sort ) | while IFS= read -r c_path; do
    c_path=${c_path#.}
    c_mode=0644
    [ "$c_path" = /usr/lib/esj/esj ] && c_mode=0755
    c_type=
    if [ "$c_packager" = rpm ]; then
      case $c_path in
        /usr/share/doc/esj/LICENSE|/usr/share/doc/esj/NOTICE) c_type=license ;;
        /usr/share/doc/esj/*) c_type=doc ;;
      esac
    fi
    printf '  - src: %s\n    dst: %s\n' "$(quoted "$c_stage$c_path")" "$(quoted "$c_path")"
    [ -z "$c_type" ] || printf '    type: %s\n' "$c_type"
    printf '    file_info: { mode: %s, mtime: %s }\n' "$c_mode" "'$mtime'"
  done
  printf '  - src: ../lib/esj/esj\n    dst: /usr/bin/esj\n    type: symlink\n'
}

built=0
for arch in x64 arm64; do
  native=$(native_of "$arch") || exit 1
  [ -n "$native" ] || continue
  case $arch in
    x64) deb_arch=amd64; rpm_arch=x86_64 ;;
    arm64) deb_arch=arm64; rpm_arch=aarch64 ;;
  esac
  echo "packages: esj $version for linux-$arch from $native"
  esj_glibc_floor "$native"
  # The tree of the package, with the modes it installs with: the archive keeps
  # the licences read-only, and a package has no reason to.
  stage=$work/stage-$arch
  mkdir -p "$stage/usr/lib/esj" "$stage/usr/share/doc/esj"
  cp "$native/esj" "$stage/usr/lib/esj/esj"
  libraries=0
  for library in "$native"/lib*.so; do
    [ -f "$library" ] || continue
    cp "$library" "$stage/usr/lib/esj/"
    libraries=$((libraries + 1))
  done
  [ "$libraries" -gt 0 ] || fail "no shared libraries beside $native/esj"
  [ -d "$native/legal" ] || fail "no legal/ beside $native/esj"
  cp -R "$native/legal" "$stage/usr/lib/esj/legal"
  for file in LICENSE NOTICE; do
    [ -f "$native/$file" ] || fail "no $file beside $native/esj"
    cp "$native/$file" "$stage/usr/share/doc/esj/$file"
  done
  cp "$here/copyright" "$stage/usr/share/doc/esj/copyright"
  find "$stage" -type d -exec chmod 0755 {} +
  find "$stage" -type f -exec chmod 0644 {} +
  chmod 0755 "$stage/usr/lib/esj/esj"
  for packager in deb rpm; do
    configuration "$packager" "$deb_arch" "$stage" > "$work/$packager-$arch.yaml"
    case $packager in
      deb) target=$out/esj_${pkgver}_$deb_arch.deb ;;
      rpm) target=$out/esj-$pkgver-1.$rpm_arch.rpm ;;
    esac
    "$nfpm" package --config "$work/$packager-$arch.yaml" --packager "$packager" \
      --target "$target" > "$work/nfpm.log" 2>&1 ||
      { cat "$work/nfpm.log" >&2; fail "nfpm did not write $target"; }
    checksum "$target"
    echo "packages: $(basename -- "$target") ($(du -h "$target" | awk '{print $1}'))"
    built=$((built + 1))
  done
done
[ "$built" -gt 0 ] ||
  fail "no esj-$version-native-linux-x64 or -arm64, unpacked or as .zip, in $inputs"
echo "packages: $built packages in $out"
